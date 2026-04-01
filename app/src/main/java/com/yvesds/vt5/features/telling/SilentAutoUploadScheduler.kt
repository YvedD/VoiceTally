package com.yvesds.vt5.features.telling

import android.content.Context
import android.util.Log
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.features.masterClient.MasterClientPrefs
import com.yvesds.vt5.hoofd.InstellingenScherm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime

/**
 * In-app scheduler voor klokgebaseerde silent uploads.
 *
 * Eigenschappen:
 * - draait buiten een individuele Activity, zodat schermwissels de timer niet stoppen
 * - uploadt op echte klokgrenzen (bv. 05/10/15/... minuten), nooit op :00
 * - gebruikt enkel de persisted active envelope en dus alleen reeds verwerkte records
 * - slaat een tick over wanneer de centrale uploadkern al bezig is
 */
object SilentAutoUploadScheduler {
    private const val TAG = "SilentAutoUpload"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var runningJob: Job? = null
    @Volatile
    private var activeTellingId: String? = null
    @Volatile
    private var activeIntervalMinutes: Int = 0

    fun startOrUpdate(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("vt5_prefs", Context.MODE_PRIVATE)
        val tellingId = prefs.getString("pref_telling_id", null).orEmpty()
        val intervalMinutes = InstellingenScherm.getSilentAutoUploadIntervalMinutes(appContext)
        val mode = MasterClientPrefs.getMode(appContext)

        if (tellingId.isBlank() || intervalMinutes <= 0 || mode == MasterClientPrefs.MODE_CLIENT) {
            stop("geen actieve silent-upload context")
            return
        }

        val currentJob = runningJob
        if (currentJob != null && currentJob.isActive && activeTellingId == tellingId && activeIntervalMinutes == intervalMinutes) {
            return
        }

        stop("schema wijzigen")
        activeTellingId = tellingId
        activeIntervalMinutes = intervalMinutes
        runningJob = scope.launch {
            runLoop(appContext, tellingId, intervalMinutes)
        }
        Log.i(TAG, "Silent auto-upload gestart voor telling=$tellingId interval=${intervalMinutes}m")
    }

    fun stop(reason: String = "") {
        runningJob?.cancel()
        runningJob = null
        if (reason.isNotBlank()) {
            Log.i(TAG, "Silent auto-upload gestopt: $reason")
        }
        activeTellingId = null
        activeIntervalMinutes = 0
    }

    suspend fun stopAndJoin(reason: String = "") {
        val job = runningJob
        stop(reason)
        if (job != null) {
            runCatching { job.cancelAndJoin() }
        }
    }

    private suspend fun runLoop(context: Context, tellingId: String, intervalMinutes: Int) {
        val uploadCore = TellingUploadCore(context)
        val envelopePersistence = TellingEnvelopePersistence(context, SaFStorageHelper(context))

        while (scope.isActive) {
            val delayMs = millisUntilNextBoundary(intervalMinutes)
            delay(delayMs)

            if (!shouldKeepRunning(context, tellingId, intervalMinutes)) {
                break
            }

            val envelope = runCatching { envelopePersistence.loadSavedEnvelope() }
                .getOrNull()
                ?.takeIf { it.tellingid == tellingId }
                ?: continue

            if (envelope.data.isEmpty()) {
                Log.d(TAG, "Silent upload overgeslagen: geen verwerkte records voor telling=$tellingId")
                continue
            }

            val prepared = uploadCore.prepareEnvelopeForUpload(
                sourceEnvelope = envelope,
                useStoredOnlineIdWhenBlank = true
            )
            val result = uploadCore.uploadPrepared(
                TellingUploadCore.UploadRequest(
                    mode = TellingUploadCore.Mode.SILENT_SYNC,
                    preparedEnvelope = prepared,
                    persistReturnedOnlineId = true,
                    persistPreparedEnvelopeToPrefs = true,
                    markTellingSent = false
                )
            )

            if (result.success) {
                Log.i(
                    TAG,
                    "Silent upload geslaagd voor telling=${result.preparedEnvelope.tellingid} onlineId=${result.effectiveOnlineId.orEmpty()} records=${result.preparedEnvelope.data.size}"
                )
            } else if (!result.skipped) {
                Log.w(TAG, "Silent upload mislukt: ${result.errorMessage}")
            }
        }

        if (activeTellingId == tellingId && activeIntervalMinutes == intervalMinutes) {
            activeTellingId = null
            activeIntervalMinutes = 0
            runningJob = null
        }
    }

    private fun shouldKeepRunning(context: Context, tellingId: String, intervalMinutes: Int): Boolean {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("vt5_prefs", Context.MODE_PRIVATE)
        val currentTellingId = prefs.getString("pref_telling_id", null).orEmpty()
        val currentInterval = InstellingenScherm.getSilentAutoUploadIntervalMinutes(appContext)
        val mode = MasterClientPrefs.getMode(appContext)

        return currentTellingId == tellingId &&
            currentInterval == intervalMinutes &&
            intervalMinutes > 0 &&
            mode != MasterClientPrefs.MODE_CLIENT
    }

    private fun millisUntilNextBoundary(intervalMinutes: Int, now: ZonedDateTime = ZonedDateTime.now()): Long {
        val next = nextBoundary(intervalMinutes, now)
        return Duration.between(now, next).toMillis().coerceAtLeast(1_000L)
    }

    private fun nextBoundary(intervalMinutes: Int, now: ZonedDateTime): ZonedDateTime {
        val validMinutes = generateSequence(intervalMinutes) { it + intervalMinutes }
            .takeWhile { it < 60 }
            .toList()

        if (validMinutes.isEmpty()) {
            return now.plusMinutes(intervalMinutes.toLong()).withSecond(0).withNano(0)
        }

        val currentHour = now.withMinute(0).withSecond(0).withNano(0)
        for (hourOffset in 0..24) {
            val baseHour = currentHour.plusHours(hourOffset.toLong())
            for (minute in validMinutes) {
                val candidate = baseHour.withMinute(minute)
                if (candidate.isAfter(now)) {
                    return candidate
                }
            }
        }

        return currentHour.plusHours(1).withMinute(validMinutes.first())
    }
}

