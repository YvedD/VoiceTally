package com.yvesds.vt5.features.masterClient

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

object McNetworkUtils {

    enum class MasterNetworkMode {
        WIFI_CLIENT,
        HOTSPOT_PROVIDER,
        LOCAL_NETWORK
    }

    data class MasterNetworkContext(
        val mode: MasterNetworkMode,
        val hostAddress: String?,
        val connectedWifiSsid: String?,
        val hotspotSsid: String,
        val hotspotPass: String,
        val hotspotSecurity: String,
        val interfaceName: String? = null
    )

    private data class Ipv4Candidate(
        val interfaceName: String,
        val hostAddress: String,
        val score: Int
    )

    private val hotspotInterfaceHints = listOf("ap", "softap", "swlan", "wlan", "wl", "rndis")
    private val excludedInterfaceHints = listOf("rmnet", "ccmni", "radio", "tun", "ppp", "bt-pan", "aware", "lo")
    private val hotspotSubnetPrefixes = listOf(
        "192.168.43.",
        "192.168.232.",
        "192.168.137.",
        "172.20.10."
    )

    fun resolveMasterNetworkContext(
        context: Context,
        fallbackHotspotSsid: String = "",
        fallbackHotspotPass: String = "",
        fallbackHotspotSecurity: String = "WPA"
    ): MasterNetworkContext {
        val connectedWifiSsid = getConnectedWifiSsid(context)
        val normalizedSecurity = fallbackHotspotSecurity.ifBlank { if (fallbackHotspotPass.isBlank()) "NOPASS" else "WPA" }

        if (!connectedWifiSsid.isNullOrBlank()) {
            return MasterNetworkContext(
                mode = MasterNetworkMode.WIFI_CLIENT,
                hostAddress = getLocalIpv4(preferHotspot = false),
                connectedWifiSsid = connectedWifiSsid,
                hotspotSsid = fallbackHotspotSsid,
                hotspotPass = fallbackHotspotPass,
                hotspotSecurity = normalizedSecurity
            )
        }

        val hotspotCandidate = getBestHotspotCandidate()
        if (hotspotCandidate != null) {
            return MasterNetworkContext(
                mode = MasterNetworkMode.HOTSPOT_PROVIDER,
                hostAddress = hotspotCandidate.hostAddress,
                connectedWifiSsid = null,
                hotspotSsid = fallbackHotspotSsid,
                hotspotPass = fallbackHotspotPass,
                hotspotSecurity = normalizedSecurity,
                interfaceName = hotspotCandidate.interfaceName
            )
        }

        return MasterNetworkContext(
            mode = MasterNetworkMode.LOCAL_NETWORK,
            hostAddress = getLocalIpv4(preferHotspot = false),
            connectedWifiSsid = null,
            hotspotSsid = fallbackHotspotSsid,
            hotspotPass = fallbackHotspotPass,
            hotspotSecurity = normalizedSecurity
        )
    }

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

    fun getLocalIpv4(preferHotspot: Boolean = false): String? {
        return try {
            val candidates = collectIpv4Candidates()
            if (candidates.isEmpty()) return null
            val hotspot = candidates.filter { isLikelyHotspotCandidate(it) }.maxByOrNull { it.score }
            when {
                preferHotspot && hotspot != null -> hotspot.hostAddress
                !preferHotspot -> candidates.maxByOrNull { it.score }?.hostAddress
                else -> candidates.maxByOrNull { it.score }?.hostAddress
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getBestHotspotCandidate(): Ipv4Candidate? {
        return collectIpv4Candidates()
            .filter { isLikelyHotspotCandidate(it) }
            .maxByOrNull { it.score }
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

    private fun isLikelyHotspotCandidate(candidate: Ipv4Candidate): Boolean {
        val name = candidate.interfaceName
        val host = candidate.hostAddress
        return hotspotSubnetPrefixes.any { host.startsWith(it) } ||
            hotspotInterfaceHints.any { name.contains(it, ignoreCase = true) }
    }

    private fun scoreCandidate(interfaceName: String, hostAddress: String): Int {
        var score = 0
        if (hotspotSubnetPrefixes.any { hostAddress.startsWith(it) }) score += 120
        if (hostAddress.endsWith(".1")) score += 30
        if (interfaceName.startsWith("wlan", ignoreCase = true)) score += 20
        if (interfaceName.startsWith("swlan", ignoreCase = true)) score += 40
        if (hotspotInterfaceHints.any { interfaceName.contains(it, ignoreCase = true) }) score += 35
        if (hostAddress.startsWith("192.168.")) score += 10
        if (hostAddress.startsWith("172.")) score += 5
        return score
    }
}

