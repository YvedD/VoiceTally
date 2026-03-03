package com.yvesds.vt5.features.telling

import android.content.Context

/**
 * Tracks whether a telling was successfully uploaded.
 * Uses a per-telling key based on tellingId + onlineId.
 */
object TellingUploadFlags {
    private const val PREFS_NAME = "vt5_prefs"
    private const val KEY_PREFIX = "telling_uploaded_"

    fun markSent(context: Context, tellingId: String?, onlineId: String?) {
        val key = buildKey(tellingId, onlineId) ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(key, true).apply()
    }

    fun markNotSent(context: Context, tellingId: String?, onlineId: String?) {
        val key = buildKey(tellingId, onlineId) ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(key, false).apply()
    }

    fun isSent(context: Context, tellingId: String?, onlineId: String?): Boolean {
        val key = buildKey(tellingId, onlineId) ?: return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(key, false)
    }

    fun clearFlag(context: Context, tellingId: String?, onlineId: String?) {
        val key = buildKey(tellingId, onlineId) ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(key).apply()
    }

    private fun buildKey(tellingId: String?, onlineId: String?): String? {
        val tid = tellingId?.trim().orEmpty()
        val oid = onlineId?.trim().orEmpty()
        if (tid.isEmpty() && oid.isEmpty()) return null
        return KEY_PREFIX + tid + "_" + oid
    }
}

