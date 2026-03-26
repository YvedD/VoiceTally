package com.yvesds.vt5.features.masterClient

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.SoftApConfiguration
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

object McLocalHotspotManager {
    private const val TAG = "McLocalHotspotManager"

    data class HotspotInfo(
        val ssid: String,
        val passphrase: String,
        val security: String = "WPA"
    )

    @Volatile
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    @Volatile
    private var activeInfo: HotspotInfo? = null

    fun getActiveHotspotInfo(@Suppress("UNUSED_PARAMETER") context: Context): HotspotInfo? =
        if (reservation != null) activeInfo else null

    fun isActive(@Suppress("UNUSED_PARAMETER") context: Context): Boolean = reservation != null && activeInfo != null

    @SuppressLint("MissingPermission")
    fun start(
        context: Context,
        onStarted: (HotspotInfo) -> Unit,
        onFailed: (String) -> Unit
    ) {
        val appContext = context.applicationContext
        activeInfo?.let {
            onStarted(it)
            return
        }

        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager == null) {
            onFailed("Wi-Fi manager niet beschikbaar")
            return
        }

        if (!hasNearbyWifiPermission(appContext)) {
            onFailed("Toestemming ‘Nabije wifi-apparaten’ ontbreekt")
            return
        }

        try {
            wifiManager.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                        this@McLocalHotspotManager.reservation?.close()
                        this@McLocalHotspotManager.reservation = reservation

                        val config = reservation.softApConfiguration
                        val ssid = config.resolveSsid().trim()
                        val pass = config.resolvePassphrase().trim()
                        val security = if (pass.isBlank()) "NOPASS" else "WPA"

                        if (ssid.isBlank()) {
                            Log.w(TAG, "LocalOnlyHotspot started without SSID")
                            stop(appContext)
                            onFailed("Geen hotspotnaam ontvangen")
                            return
                        }

                        val info = HotspotInfo(ssid = ssid, passphrase = pass, security = security)
                        activeInfo = info
                        MasterClientPrefs.setHotspotSsid(appContext, info.ssid)
                        MasterClientPrefs.setHotspotPassword(appContext, info.passphrase)
                        MasterClientPrefs.setHotspotSecurity(appContext, info.security)
                        Log.i(TAG, "LocalOnlyHotspot actief: ${info.ssid}")
                        onStarted(info)
                    }

                    override fun onStopped() {
                        Log.i(TAG, "LocalOnlyHotspot gestopt")
                        clear(appContext, closeReservation = false)
                    }

                    override fun onFailed(reason: Int) {
                        Log.w(TAG, "LocalOnlyHotspot start mislukt: $reason")
                        clear(appContext, closeReservation = false)
                        onFailed(reasonToMessage(reason))
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (se: SecurityException) {
            Log.e(TAG, "Geen permissie voor LocalOnlyHotspot: ${se.message}", se)
            onFailed("Toestemming ‘Nabije wifi-apparaten’ ontbreekt of is geweigerd")
        } catch (e: Exception) {
            Log.e(TAG, "LocalOnlyHotspot start fout: ${e.message}", e)
            onFailed(e.message ?: "Onbekende fout")
        }
    }

    fun stop(context: Context) {
        clear(context.applicationContext, closeReservation = true)
    }

    private fun clear(context: Context, closeReservation: Boolean) {
        if (closeReservation) {
            try {
                reservation?.close()
            } catch (_: Exception) {
            }
        }
        reservation = null
        activeInfo = null
        MasterClientPrefs.clearHotspotCredentials(context)
    }

    private fun reasonToMessage(reason: Int): String {
        return when (reason) {
            WifiManager.LocalOnlyHotspotCallback.ERROR_GENERIC -> "Lokaal netwerk kon niet gestart worden"
            WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL -> "Geen vrij Wi-Fi kanaal beschikbaar"
            WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE -> "Toestel kan lokaal netwerk nu niet starten"
            else -> "Lokaal netwerk start mislukt ($reason)"
        }
    }

    private fun hasNearbyWifiPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun SoftApConfiguration.resolveSsid(): String = try {
        wifiSsid?.toString()?.trim()?.trim('"').orEmpty()
    } catch (_: Exception) {
        ""
    }

    private fun SoftApConfiguration.resolvePassphrase(): String = try {
        passphrase ?: ""
    } catch (_: Exception) {
        ""
    }
}

