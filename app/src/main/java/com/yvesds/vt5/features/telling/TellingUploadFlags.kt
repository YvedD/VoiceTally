package com.yvesds.vt5.features.telling

import android.content.Context
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
        }

        val tid = tellingId?.trim().orEmpty()
        if (tid.isNotEmpty()) {
            editor.putBoolean(KEY_PREFIX + tid + "_local", false)
        }

        editor.apply()
    }

    fun isSent(context: Context, tellingId: String?, onlineId: String?): Boolean {
        val tid = tellingId?.trim().orEmpty()

        // GEBRUIKERSWENS: Gearchiveerde sessies (imports) moeten ook als 'verzonden' (groen/geblokkeerd) getoond worden
        if (tid.isNotEmpty()) {
            val db = com.yvesds.vt5.core.database.VoiceTallyDatabase.getDatabase(context)
            val header = kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    db.tellingDao().getHeader(tid)
                }
            }
            if (header != null && CsvImportPolicy.isUploadBlocked(header.status, header.bron)) return true
        }

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
