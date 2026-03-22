package com.yvesds.vt5.features.telling

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class ClientObservationDeliveryStore(
    context: Context
) {
    companion object {
        private const val TAG = "ClientObservationDeliveryStore"
        private const val PREFS_NAME = "vt5_mc_delivery_status"
        private const val KEY_ENTRIES = "entries"
    }

    data class Entry(
        val recordId: String,
        val clientEventId: String,
        val status: TellingScherm.DeliveryStatus,
        val updatedAtMs: Long
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<Entry> {
        val raw = prefs.getString(KEY_ENTRIES, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val recordId = item.optString("recordId").trim()
                    val clientEventId = item.optString("clientEventId").trim()
                    val status = TellingScherm.DeliveryStatus.fromWireValue(item.optString("status"))
                    val updatedAtMs = item.optLong("updatedAtMs")
                    if (recordId.isNotBlank() && clientEventId.isNotBlank()) {
                        add(
                            Entry(
                                recordId = recordId,
                                clientEventId = clientEventId,
                                status = status,
                                updatedAtMs = updatedAtMs
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Kon delivery-statussen niet lezen: ${e.message}", e)
            emptyList()
        }
    }

    fun getByRecordId(recordId: String): Entry? =
        getAll().lastOrNull { it.recordId == recordId }

    fun getByEventId(clientEventId: String): Entry? =
        getAll().lastOrNull { it.clientEventId == clientEventId }

    fun upsert(recordId: String, clientEventId: String, status: TellingScherm.DeliveryStatus) {
        if (recordId.isBlank() || clientEventId.isBlank()) return
        val entries = getAll().toMutableList()
        val now = System.currentTimeMillis()
        val index = entries.indexOfLast { it.recordId == recordId || it.clientEventId == clientEventId }
        val updated = Entry(
            recordId = recordId,
            clientEventId = clientEventId,
            status = status,
            updatedAtMs = now
        )
        if (index >= 0) {
            entries[index] = updated
        } else {
            entries.add(updated)
        }
        persist(entries)
    }

    fun removeByRecordId(recordId: String) {
        if (recordId.isBlank()) return
        persist(getAll().filterNot { it.recordId == recordId })
    }

    fun clear() {
        prefs.edit { remove(KEY_ENTRIES) }
    }

    private fun persist(entries: List<Entry>) {
        try {
            val array = JSONArray()
            entries.forEach { entry ->
                array.put(
                    JSONObject()
                        .put("recordId", entry.recordId)
                        .put("clientEventId", entry.clientEventId)
                        .put("status", entry.status.wireValue)
                        .put("updatedAtMs", entry.updatedAtMs)
                )
            }
            prefs.edit { putString(KEY_ENTRIES, array.toString()) }
        } catch (e: Exception) {
            Log.w(TAG, "Kon delivery-statussen niet bewaren: ${e.message}", e)
        }
    }
}

