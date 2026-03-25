package com.yvesds.vt5.features.telling

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * Beheert gegroepeerde single/double taps op soorttegels.
 *
 * Belangrijke keuzes:
 * - Elke soort heeft zijn eigen pending state + flush job, dus meerdere soorten kunnen tegelijk pending zijn.
 * - Pending taps bestaan alleen in UI-state; pas bij flush wordt een echte waarneming aangemaakt.
 * - De commit callback krijgt het exacte finalisatiemoment zodat records/upload exact dat tijdstip kunnen gebruiken.
 */
class TileTapAggregationManager(
    private val scope: CoroutineScope,
    private val groupWindowMs: Long = DEFAULT_GROUP_WINDOW_MS,
    private val onPendingCountChanged: (speciesId: String, pendingMainCount: Int) -> Unit,
    private val onPendingRowUpsert: (TellingScherm.SpeechLogRow) -> Unit,
    private val onAggregateFinalized: suspend (PendingAggregate, Long) -> Unit
) {
    companion object {
        const val DEFAULT_GROUP_WINDOW_MS = 5_000L
    }

    data class PendingAggregate(
        val pendingKey: String,
        val speciesId: String,
        val displayName: String,
        var totalMainCount: Int,
        var createdAtMs: Long,
        var lastInputAtMs: Long,
        var flushJob: Job? = null,
        var countdownJob: Job? = null
    ) {
        fun toPendingRow(remainingSeconds: Int): TellingScherm.SpeechLogRow {
            return TellingScherm.SpeechLogRow(
                ts = createdAtMs / 1000L,
                tekst = "$displayName -> +$totalMainCount (${remainingSeconds}s)",
                bron = "final",
                isPending = true,
                recordLocalId = null,
                rowKey = pendingKey
            )
        }
    }

    private val pendingBySpecies = linkedMapOf<String, PendingAggregate>()
    private var nextPendingSequence = 1L

    fun registerTap(speciesId: String, displayName: String, delta: Int) {
        if (delta <= 0) return

        val now = System.currentTimeMillis()
        val existing = pendingBySpecies[speciesId]
        val aggregate = if (existing != null && now - existing.lastInputAtMs <= groupWindowMs) {
            existing.totalMainCount += delta
            existing.lastInputAtMs = now
            existing
        } else {
            PendingAggregate(
                pendingKey = buildPendingKey(speciesId),
                speciesId = speciesId,
                displayName = displayName,
                totalMainCount = delta,
                createdAtMs = now,
                lastInputAtMs = now
            ).also { pendingBySpecies[speciesId] = it }
        }

        onPendingCountChanged(speciesId, aggregate.totalMainCount)
        onPendingRowUpsert(aggregate.toPendingRow(calculateRemainingSeconds(aggregate)))
        scheduleCountdown(aggregate)
        scheduleFlush(aggregate)
    }

    suspend fun flushSpeciesAndAwait(speciesId: String) {
        finalizeSpecies(speciesId)
    }

    suspend fun flushAllAndAwait() {
        pendingBySpecies.keys.toList().forEach { speciesId ->
            finalizeSpecies(speciesId)
        }
    }

    fun getPendingMainCount(speciesId: String): Int {
        return pendingBySpecies[speciesId]?.totalMainCount ?: 0
    }


    private fun scheduleFlush(aggregate: PendingAggregate) {
        aggregate.flushJob?.cancel()
        aggregate.flushJob = scope.launch {
            delay(groupWindowMs)
            finalizeSpecies(aggregate.speciesId, aggregate.pendingKey)
        }
    }

    private fun scheduleCountdown(aggregate: PendingAggregate) {
        aggregate.countdownJob?.cancel()
        aggregate.countdownJob = scope.launch {
            while (true) {
                val current = pendingBySpecies[aggregate.speciesId]
                if (current == null || current.pendingKey != aggregate.pendingKey) return@launch

                val remainingSeconds = calculateRemainingSeconds(current)
                onPendingRowUpsert(current.toPendingRow(remainingSeconds))

                val remainingMs = current.lastInputAtMs + groupWindowMs - System.currentTimeMillis()
                if (remainingMs <= 0L) return@launch

                val delayMs = (remainingMs % 1000L).let { remainder ->
                    if (remainder == 0L) 1000L else remainder
                }
                delay(delayMs)
            }
        }
    }

    private suspend fun finalizeSpecies(speciesId: String, expectedPendingKey: String? = null) {
        val aggregate = pendingBySpecies.remove(speciesId) ?: return
        if (expectedPendingKey != null && aggregate.pendingKey != expectedPendingKey) {
            pendingBySpecies[speciesId] = aggregate
            return
        }

        val currentJob = currentCoroutineContext()[Job]
        if (aggregate.flushJob != null && aggregate.flushJob !== currentJob) {
            aggregate.flushJob?.cancel()
        }
        if (aggregate.countdownJob != null && aggregate.countdownJob !== currentJob) {
            aggregate.countdownJob?.cancel()
        }
        aggregate.flushJob = null
        aggregate.countdownJob = null
        onPendingCountChanged(speciesId, 0)

        val finalizedAtEpochSeconds = System.currentTimeMillis() / 1000L
        onAggregateFinalized(aggregate, finalizedAtEpochSeconds)
    }

    private fun calculateRemainingSeconds(aggregate: PendingAggregate): Int {
        val remainingMs = aggregate.lastInputAtMs + groupWindowMs - System.currentTimeMillis()
        return ceil(remainingMs.coerceAtLeast(1L) / 1000.0).toInt().coerceAtLeast(1)
    }

    private fun buildPendingKey(speciesId: String): String {
        return "tiletap_${speciesId}_${nextPendingSequence++}"
    }
}

