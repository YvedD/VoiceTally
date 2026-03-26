package com.yvesds.vt5.features.masterClient

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

object McNetworkUtils {

    private data class Ipv4Candidate(
        val interfaceName: String,
        val hostAddress: String,
        val score: Int
    )

    private val excludedInterfaceHints = listOf("rmnet", "ccmni", "radio", "tun", "ppp", "bt-pan", "aware", "lo")

    fun getConnectedWifiSsid(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        if (!wifiManager.isWifiEnabled) return null
        val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val rawSsid = try {
            val activeNetwork = connectivityManager?.activeNetwork
            val caps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                (caps.transportInfo as? WifiInfo)?.ssid
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
        return rawSsid
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }

    fun isWifiTransportActive(context: Context): Boolean {
        val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return try {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: Exception) {
            false
        }
    }

    fun getLocalIpv4(): String? {
        return try {
            val candidates = collectIpv4Candidates()
            if (candidates.isEmpty()) return null
            candidates.maxByOrNull { it.score }?.hostAddress
        } catch (_: Exception) {
            null
        }
    }

    private fun collectIpv4Candidates(): List<Ipv4Candidate> {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            buildList {
                for (intf in interfaces) {
                    val name = intf.name.orEmpty()
                    if (!intf.isUp || intf.isLoopback) continue
                    if (excludedInterfaceHints.any { name.contains(it, ignoreCase = true) }) continue

                    for (addr in intf.inetAddresses.toList()) {
                        if (addr !is Inet4Address) continue
                        if (addr.isLoopbackAddress || addr.isLinkLocalAddress) continue
                        val host = addr.hostAddress ?: continue
                        add(
                            Ipv4Candidate(
                                interfaceName = name,
                                hostAddress = host,
                                score = scoreCandidate(name, host)
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun scoreCandidate(interfaceName: String, hostAddress: String): Int {
        var score = 0
        if (interfaceName.startsWith("wlan", ignoreCase = true)) score += 20
        if (interfaceName.startsWith("wifi", ignoreCase = true)) score += 20
        if (interfaceName.startsWith("eth", ignoreCase = true)) score += 10
        if (hostAddress.startsWith("192.168.")) score += 10
        if (hostAddress.startsWith("172.")) score += 5
        return score
    }
}

