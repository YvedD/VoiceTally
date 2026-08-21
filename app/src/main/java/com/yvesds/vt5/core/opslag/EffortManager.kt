package com.yvesds.vt5.core.opslag

import android.content.Context
import android.util.Log
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * EffortManager - Coördineert de berekening en synchronisatie van tel-inspanning.
 */
object EffortManager {
    private const val TAG = "EffortManager"

    /**
     * Voert een eenmalige scan uit van de database om de EffortStore te vullen indien nodig.
     */
    suspend fun syncIfRequired(context: Context) = withContext(Dispatchers.IO) {
        if (EffortStore.isInitialized(context)) return@withContext

        Log.i(TAG, "Start initiële scan van database voor tel-inspanning...")
        try {
            val db = VoiceTallyDatabase.getDatabase(context)
            val headers = db.tellingDao().getAllHeaders()
            
            val effortBySite = headers.groupBy { it.telpostid }.mapValues { (_, list) ->
                list.sumOf { (it.eindtijd.toLongOrNull() ?: 0L) - (it.begintijd.toLongOrNull() ?: 0L) }
            }

            EffortStore.resetAll(context)
            effortBySite.forEach { (siteId, seconds) ->
                EffortStore.addEffort(context, siteId, seconds)
            }
            
            EffortStore.setInitialized(context)
            Log.i(TAG, "Initiële inspanning-sync voltooid: ${effortBySite.size} telposten verwerkt.")
        } catch (e: Exception) {
            Log.e(TAG, "Fout tijdens inspanning-sync: ${e.message}")
        }
    }

    /**
     * Voegt de duur van een nieuwe sessie toe aan de persistente teller.
     */
    suspend fun addSessionEffort(context: Context, siteId: String, startEpoch: Long, endEpoch: Long) {
        val duration = (endEpoch - startEpoch).coerceAtLeast(0)
        if (duration > 0) {
            EffortStore.addEffort(context, siteId, duration)
            Log.d(TAG, "Effort toegevoegd voor $siteId: $duration seconden.")
        }
    }

    /**
     * Haalt de totale cluster-inspanning op in uren.
     */
    suspend fun getClusterEffortHours(context: Context, siteIds: List<String>): Double {
        syncIfRequired(context)
        val totalSeconds = EffortStore.getTotalSecondsForCluster(context, siteIds)
        return totalSeconds / 3600.0
    }
}
