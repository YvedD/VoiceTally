package com.yvesds.vt5.features.telling

import android.content.Context
import androidx.core.content.edit
import com.yvesds.vt5.net.ServerTellingDataItem
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Keeps per-day observation counts per species across all sessions.
 *
 * Important:
 * - Counts are based on processed records, not specimen totals.
 * - One record == one observation for that species on that day.
 * - Updates are idempotent per record key and survive session restarts via SharedPreferences.
 * - A record that changes species/day is moved safely to the correct bucket.
 */
object DailySpeciesObservationRankStore {
    private const val PREFS = "vt5_prefs"
    private const val KEY_STATE = "pref_daily_species_observation_rank_state_json"
    private const val RETENTION_DAYS = 8L

    fun upsertRecord(context: Context, record: ServerTellingDataItem) {
        val state = loadState(context)
        cleanupBuckets(state)

        val key = recordKey(record)
        removeRecordKeyFromAllBuckets(state, key)

        val dateKey = dayKeyForRecord(record)
        val bucket = state.optJSONObject(dateKey) ?: JSONObject().also { state.put(dateKey, it) }
        bucket.put("dateKey", dateKey)

        val records = bucket.optJSONObject("records") ?: JSONObject().also { bucket.put("records", it) }
        records.put(key, JSONObject().apply {
            put("speciesId", record.soortid)
        })

        recomputeCounts(bucket)
        saveState(context, state)
    }

    fun getTodayCounts(context: Context): Map<String, Int> {
        val state = loadState(context)
        cleanupBuckets(state)
        val bucket = state.optJSONObject(LocalDate.now(ZoneId.systemDefault()).toString()) ?: return emptyMap()
        if (bucket.optJSONObject("counts") == null) {
            recomputeCounts(bucket)
            saveState(context, state)
        }
        return extractCounts(bucket)
    }

    private fun recomputeCounts(bucket: JSONObject) {
        val records = bucket.optJSONObject("records") ?: JSONObject()
        val counts = JSONObject()
        val keys = records.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = records.optJSONObject(key) ?: continue
            val speciesId = item.optString("speciesId", "").trim()
            if (speciesId.isBlank()) continue
            counts.put(speciesId, counts.optInt(speciesId, 0) + 1)
        }
        bucket.put("counts", counts)
    }

    private fun extractCounts(bucket: JSONObject): Map<String, Int> {
        val counts = bucket.optJSONObject("counts") ?: return emptyMap()
        val result = linkedMapOf<String, Int>()
        val keys = counts.keys()
        while (keys.hasNext()) {
            val speciesId = keys.next()
            val value = counts.optInt(speciesId, 0)
            if (speciesId.isNotBlank() && value > 0) {
                result[speciesId] = value
            }
        }
        return result
    }

    private fun removeRecordKeyFromAllBuckets(state: JSONObject, recordKey: String) {
        val keys = state.keys()
        while (keys.hasNext()) {
            val bucketKey = keys.next()
            val bucket = state.optJSONObject(bucketKey) ?: continue
            val records = bucket.optJSONObject("records") ?: continue
            if (records.has(recordKey)) {
                records.remove(recordKey)
                recomputeCounts(bucket)
            }
        }
    }

    private fun cleanupBuckets(state: JSONObject) {
        val today = LocalDate.now(ZoneId.systemDefault())
        val keysToRemove = mutableListOf<String>()
        val keys = state.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val bucket = state.optJSONObject(key) ?: continue
            val dateKey = bucket.optString("dateKey", key)
            val date = runCatching { LocalDate.parse(dateKey) }.getOrNull() ?: continue
            if (date.isBefore(today.minusDays(RETENTION_DAYS))) {
                keysToRemove += key
            }
        }
        keysToRemove.forEach(state::remove)
    }

    private fun dayKeyForRecord(record: ServerTellingDataItem): String {
        val epochSeconds = record.tijdstip.toLongOrNull()
        val date = if (epochSeconds != null && epochSeconds > 0L) {
            Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate()
        } else {
            LocalDate.now(ZoneId.systemDefault())
        }
        return date.toString()
    }

    private fun recordKey(record: ServerTellingDataItem): String {
        return when {
            record.tellingid.isNotBlank() && record.idLocal.isNotBlank() -> "${record.tellingid}:${record.idLocal}"
            record.tellingid.isNotBlank() && record.groupid.isNotBlank() -> "${record.tellingid}:${record.groupid}"
            record.groupid.isNotBlank() -> record.groupid
            else -> listOf(record.soortid, record.tijdstip, record.aantal, record.aantalterug, record.lokaal).joinToString(":")
        }
    }

    private fun loadState(context: Context): JSONObject {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_STATE, null)
        return try {
            if (raw.isNullOrBlank()) JSONObject() else JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun saveState(context: Context, state: JSONObject) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_STATE, state.toString())
        }
    }
}

