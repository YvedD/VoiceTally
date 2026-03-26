package com.yvesds.vt5.features.masterClient

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit
import androidx.core.content.ContextCompat

object McRuntimePermissions {

    private const val PREFS_NAME = "vt5_permission_cache"
    private const val KEY_PREFIX_GRANTED = "perm_granted_"

    fun requiredStartupPermissions(): Array<String> = arrayOf(
        Manifest.permission.RECORD_AUDIO
    )

    fun missingStartupPermissions(context: Context): Array<String> {
        return requiredStartupPermissions()
            .filter { permission ->
                ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
            }
            .toTypedArray()
    }

    fun hasAllStartupPermissions(context: Context): Boolean =
        missingStartupPermissions(context).isEmpty()

    fun refreshCachedPermissionStates(context: Context) {
        val snapshot = requiredStartupPermissions().associateWith { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        cachePermissionResults(context, snapshot)
    }

    fun cachePermissionResult(context: Context, permission: String, granted: Boolean) {
        prefs(context).edit {
            putBoolean(KEY_PREFIX_GRANTED + permission, granted)
        }
    }

    fun cachePermissionResults(context: Context, grants: Map<String, Boolean>) {
        if (grants.isEmpty()) return
        prefs(context).edit {
            grants.forEach { (permission, granted) ->
                putBoolean(KEY_PREFIX_GRANTED + permission, granted)
            }
        }
    }

    fun wasPermissionPreviouslyGranted(context: Context, permission: String): Boolean? {
        val prefs = prefs(context)
        val key = KEY_PREFIX_GRANTED + permission
        return if (prefs.contains(key)) prefs.getBoolean(key, false) else null
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

