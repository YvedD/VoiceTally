package com.yvesds.vt5.features.masterClient

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit

object McRuntimePermissions {

    private const val PREFS_NAME = "vt5_permission_cache"
    private const val KEY_PREFIX_GRANTED = "perm_granted_"
    private const val KEY_PREFIX_PROMPTED = "perm_prompted_"

    fun requiredStartupPermissions(): Array<String> = arrayOf(
        Manifest.permission.RECORD_AUDIO
    )

    fun trackedRuntimePermissions(): Array<String> = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    fun missingStartupPermissions(context: Context): Array<String> {
        return requiredStartupPermissions()
            .filter { permission ->
                !isGranted(context, permission)
            }
            .toTypedArray()
    }

    fun autoPromptStartupPermissions(context: Context): Array<String> {
        return requiredStartupPermissions()
            .filter { permission ->
                !isGranted(context, permission) && shouldAutoPrompt(context, permission)
            }
            .toTypedArray()
    }

    fun hasAllStartupPermissions(context: Context): Boolean =
        missingStartupPermissions(context).isEmpty()

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun shouldAutoPrompt(context: Context, permission: String): Boolean {
        if (isGranted(context, permission)) return false
        val previouslyGranted = wasPermissionPreviouslyGranted(context, permission)
        return previouslyGranted != false
    }


    fun refreshCachedPermissionStates(context: Context) {
        val snapshot = trackedRuntimePermissions().associateWith { permission ->
            isGranted(context, permission)
        }
        cachePermissionResults(context, snapshot)
    }

    fun cachePermissionResult(context: Context, permission: String, granted: Boolean) {
        prefs(context).edit {
            putBoolean(KEY_PREFIX_GRANTED + permission, granted)
            putBoolean(KEY_PREFIX_PROMPTED + permission, true)
        }
    }

    fun cachePermissionResults(context: Context, grants: Map<String, Boolean>) {
        if (grants.isEmpty()) return
        prefs(context).edit {
            grants.forEach { (permission, granted) ->
                putBoolean(KEY_PREFIX_GRANTED + permission, granted)
                putBoolean(KEY_PREFIX_PROMPTED + permission, true)
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

