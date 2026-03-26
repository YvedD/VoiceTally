package com.yvesds.vt5.features.masterClient

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object McRuntimePermissions {

    fun requiredStartupPermissions(): Array<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }.toTypedArray()

    fun missingStartupPermissions(context: Context): Array<String> {
        return requiredStartupPermissions()
            .filter { permission ->
                ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
            }
            .toTypedArray()
    }

    fun hasAllStartupPermissions(context: Context): Boolean =
        missingStartupPermissions(context).isEmpty()
}

