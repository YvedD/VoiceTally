package com.yvesds.vt5.features.telling

import android.content.Context
import com.yvesds.vt5.core.database.entities.TellingHeader
import com.yvesds.vt5.core.import.CsvImportPolicy

/**
 * Tracks whether a telling was successfully uploaded.
 * Uses per-telling keys based on tellingId + onlineId.
 */
object TellingUploadFlags {
    private const val PREFS_NAME = "vt5_prefs"
    private const val KEY_PREFIX = "telling_uploaded_"

    fun markSent(context: Context, tellingId: String?, onlineId: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        val primaryKey = buildKey(tellingId, onlineId)
        if (primaryKey != null) {
            editor.putBoolean(primaryKey, true)
        }

        // Als tellingId bekend is, markeer ook fallback varianten (bijv. local)
        val tid = tellingId?.trim().orEmpty()
        if (tid.isNotEmpty()) {
            editor.putBoolean(KEY_PREFIX + tid + "_local", true)
            editor.putBoolean(KEY_PREFIX + tid + "_", true)
        }

        editor.apply()
    }

    fun markNotSent(context: Context, tellingId: String?, onlineId: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        val primaryKey = buildKey(tellingId, onlineId)
        if (primaryKey != null) {
            editor.putBoolean(primaryKey, false)
            editor.remove(primaryKey)
        }

        val tid = tellingId?.trim().orEmpty()
        if (tid.isNotEmpty()) {
            val keyLocal = KEY_PREFIX + tid + "_local"
            val keyBlank = KEY_PREFIX + tid + "_"
            editor.putBoolean(keyLocal, false)
            editor.putBoolean(keyBlank, false)
            editor.remove(keyLocal)
            editor.remove(keyBlank)
        }

        editor.apply()
    }

    /**
     * Razendsnelle, niet-blokkerende SharedPreferences controle (zonder runBlocking op Room DB).
     */
    fun isSent(context: Context, tellingId: String?, onlineId: String?): Boolean {
        val tid = tellingId?.trim().orEmpty()
        val oid = onlineId?.trim().orEmpty()
        if (tid.isEmpty() && oid.isEmpty()) return false

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Check specifieke sleutel
        val primaryKey = buildKey(tellingId, onlineId)
        if (primaryKey != null && prefs.getBoolean(primaryKey, false)) {
            return true
        }

        // 2. Check algemene tellingId sleutels als fallback
        if (tid.isNotEmpty()) {
            val prefixTid = KEY_PREFIX + tid + "_"
            val allEntries = prefs.all
            for ((key, value) in allEntries) {
                if (key.startsWith(prefixTid) && value == true) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Suspend variant die ook Room DB status controleert zonder threads te blokkeren.
     */
    suspend fun isSentSuspend(context: Context, header: TellingHeader): Boolean {
        if (CsvImportPolicy.isUploadBlocked(header.status, header.bron)) return true
        val statusNorm = TellingStatus.normalize(header.status)
        if (statusNorm == TellingStatus.GEUPLOAD || statusNorm == TellingStatus.GEARCHIVEERD) return true
        return isSent(context, header.tellingid, header.onlineid)
    }

    fun isEdited(context: Context, tellingId: String?): Boolean {
        val tid = tellingId?.trim().orEmpty()
        if (tid.isEmpty()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "telling_edited_$tid"
        return prefs.getBoolean(key, false)
    }

    fun clearFlag(context: Context, tellingId: String?, onlineId: String?) {
        val primaryKey = buildKey(tellingId, onlineId) ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(primaryKey).apply()
    }

    private fun buildKey(tellingId: String?, onlineId: String?): String? {
        val tid = tellingId?.trim().orEmpty()
        val oid = onlineId?.trim().orEmpty()
        if (tid.isEmpty() && oid.isEmpty()) return null
        return KEY_PREFIX + tid + "_" + oid
    }
}
