package com.yvesds.vt5.features.telling

import android.content.Context
import androidx.core.content.edit
import com.yvesds.vt5.net.ServerTellingDataItem
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Keeps day totals per telpost across multiple sessions within the same calendar day.
 * Data is stored as a single JSON blob in SharedPreferences and updated idempotently per record key.
 */
object DailyDirectionTotalsStore {
    private const val PREFS = "vt5_prefs"
    private const val KEY_STATE = "pref_daily_direction_totals_state_json"
    private const val RETENTION_DAYS = 8L

    data class Totals(
        val mainTotal: Int,
        val returnTotal: Int
    )

    fun upsertRecord(context: Context, telpostId: String?, record: ServerTellingDataItem) {
        val state = loadState(context)
        cleanupBuckets(state)

        val dateKey = dayKeyForRecord(record)
        val bucketKey = bucketKey(dateKey, telpostId)
        val bucket = state.optJSONObject(bucketKey) ?: JSONObject().also { state.put(bucketKey, it) }
        bucket.put("dateKey", dateKey)
        bucket.put("telpostId", telpostId.orEmpty())

        val records = bucket.optJSONObject("records") ?: JSONObject().also { bucket.put("records", it) }
        records.put(recordKey(record), JSONObject().apply {
            put("main", record.aantal.toIntOrNull() ?: 0)
            put("return", record.aantalterug.toIntOrNull() ?: 0)
        })

        recomputeTotals(bucket)
        saveState(context, state)
    }

    fun getTodayTotalsForCurrentSession(context: Context): Totals {
        val telpostId = TellingSessionManager.preselectState.value.telpostId
        return getTotals(context, LocalDate.now(ZoneId.systemDefault()).toString(), telpostId)
    }

    private fun getTotals(context: Context, dateKey: String, telpostId: String?): Totals {
        val state = loadState(context)
        cleanupBuckets(state)
        val bucket = state.optJSONObject(bucketKey(dateKey, telpostId))
        return Totals(
            mainTotal = bucket?.optInt("mainTotal", 0) ?: 0,
            returnTotal = bucket?.optInt("returnTotal", 0) ?: 0
        )
    }

    private fun recomputeTotals(bucket: JSONObject) {
        val records = bucket.optJSONObject("records") ?: JSONObject()
        var main = 0
        var ret = 0
        val keys = records.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = records.optJSONObject(key) ?: continue
            main += item.optInt("main", 0)
            ret += item.optInt("return", 0)
        }
        bucket.put("mainTotal", main)
        bucket.put("returnTotal", ret)
    }

    private fun cleanupBuckets(state: JSONObject) {
        val today = LocalDate.now(ZoneId.systemDefault())
        val keysToRemove = mutableListOf<String>()
        val keys = state.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val bucket = state.optJSONObject(key) ?: continue
            val dateKey = bucket.optString("dateKey", "")
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
            else -> listOf(record.soortid, record.tijdstip, record.aantal, record.aantalterug).joinToString(":")
        }
    }

    private fun bucketKey(dateKey: String, telpostId: String?): String = "$dateKey|${telpostId.orEmpty()}"

    private fun loadState(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_STATE, null)
        return try {
            if (raw.isNullOrBlank()) JSONObject() else JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun saveState(context: Context, state: JSONObject) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_STATE, state.toString())
        }
    }
}

