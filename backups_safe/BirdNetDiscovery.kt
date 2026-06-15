package com.yvesds.vt5.features.birdnet

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Auto-discovery van BirdNET-GO op het lokale netwerk.
 *
 * Strategie (in volgorde):
 *   Stap 1 – mDNS: probeer bekende Pi-hostnamen (.local) rechtstreeks.
 *             Als de Pi hostnaam 'birdnet' heeft, lost Android birdnet.local
 *             automatisch op via mDNS/Zeroconf — geen scan nodig.
 *
 *   Stap 2 – Subnet scan: pikt het /24-netwerk van de huidige WiFi-verbinding
 *             en probeert parallel alle .1–.254 adressen:
 *               a) snelle TCP-connectie op poort 8080 (350 ms timeout)
 *               b) alleen bij open poort: GET /api/v2/ping verifieert BirdNET-GO
 *
 * Totale doorlooptijd: typisch < 1 s via mDNS, < 2 s via subnetwerk scan.
 *
 * Rate-limit van BirdNET-GO (10 req/min per IP) wordt NIET geraakt:
 * de /ping call per host is één request, alleen op open poorten.
 */
object BirdNetDiscovery {

    private const val TAG = "BirdNetDiscovery"

    /** Bekende mDNS-hostnamen in volgorde van meest waarschijnlijk naar minst. */
    val MDNS_CANDIDATES = listOf(
        "birdnet.local",
        "birdnet-go.local",
        "raspberrypi.local",
    )

    const val DEFAULT_PORT = 8080

    /** TCP-verbindingstimeout voor de subnetwerk scan (ms). */
    private const val TCP_TIMEOUT_MS = 350

    /** HTTP-timeout voor /api/v2/ping verificatie (seconds). */
    private const val PING_TIMEOUT_S = 3L

    /**
     * Dedicated OkHttpClient voor discovery.
     * Kortere timeouts dan VT5App.http; hoeft geen SSE te ondersteunen.
     */
    private val discoveryHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(PING_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(PING_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Resultaat van een succesvolle discovery.
     *
     * @param host        hostname of IP-adres zoals gebruikt in de URL
     * @param resolvedIp  opgelost IP-adres (informatief, bv. voor weergave)
     * @param port        TCP-poort (standaard 8080)
     * @param protocol    "http" of "https"
     */
    data class DiscoveryResult(
        val host: String,
        val resolvedIp: String?,
        val port: Int = DEFAULT_PORT,
        val protocol: String = "http"
    ) {
        /** Converteer naar een BirdNetConfig die direct kan worden opgeslagen. */
        fun toConfig() = BirdNetConfig(protocol = protocol, host = host, port = port)

        /** Leesbaar label voor in de UI. */
        val displayLabel: String get() = if (resolvedIp != null && resolvedIp != host) {
            "$host ($resolvedIp)"
        } else {
            host
        }
    }

    // ─── Publieke API ────────────────────────────────────────────────────────

    /**
     * Voer auto-discovery uit. Geeft het eerste gevonden resultaat terug, of null.
     *
     * @param context   Android context (nodig voor WifiManager bij subnetwerk scan)
     * @param onProgress progresscallback, aangeroepen op de coroutine-thread
     */
    suspend fun discover(
        context: Context,
        onProgress: ((String) -> Unit)? = null
    ): DiscoveryResult? {

        // Stap 1: mDNS-kandidaten
        for (hostname in MDNS_CANDIDATES) {
            onProgress?.invoke("Zoeken via $hostname…")
            tryMdnsHost(hostname)?.let { result ->
                Log.i(TAG, "mDNS gevonden: $hostname → ${result.resolvedIp}")
                return result
            }
        }

        // Stap 2: subnetwerk scan
        val subnetBase = getWifiSubnetBase(context)
        if (subnetBase == null) {
            Log.w(TAG, "Geen WiFi-subnet beschikbaar voor scan.")
            return null
        }

        onProgress?.invoke("WiFi-netwerk $subnetBase.* scannen…")
        Log.i(TAG, "Subnetwerk scan gestart op $subnetBase.0/24")
        return scanSubnet(subnetBase)
    }

    /**
     * Quick ping-only check against an already-known host (for reconnect verification).
     * Returns the resolved IP address as well (useful for display).
     */
    suspend fun pingHost(config: BirdNetConfig): PingResult =
        withContext(Dispatchers.IO) {
            try {
                val addr = InetAddress.getByName(config.host)
                val resolvedIp = addr.hostAddress ?: config.host
                val ok = pingOk(config.pingUrl)
                PingResult(reachable = ok, resolvedIp = resolvedIp)
            } catch (_: Exception) {
                PingResult(reachable = false, resolvedIp = null)
            }
        }

    data class PingResult(val reachable: Boolean, val resolvedIp: String?)

    /**
     * Geeft de drie eerste octetten van het huidige WiFi-IP terug.
     * Bv. "192.168.1" voor 192.168.1.x/24.
     * Geeft null terug als het apparaat niet verbonden is met WiFi.
     */
    fun getWifiSubnetBase(context: Context): String? {
        return try {
            val cm = ContextCompat.getSystemService(context, ConnectivityManager::class.java)
                ?: return null
            val activeNetwork = cm.activeNetwork ?: return null
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return null

            // Controleer of we op WiFi zitten
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

            val lp = cm.getLinkProperties(activeNetwork) ?: return null
            val ipv4 = lp.linkAddresses.map { it.address }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress ?: return null

            // Pak de eerste drie octetten (bv. "192.168.1")
            val parts = ipv4.split(".")
            if (parts.size >= 3) {
                "${parts[0]}.${parts[1]}.${parts[2]}"
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "getWifiSubnetBase: ${e.message}")
            null
        }
    }

    // ─── Interne helpers ─────────────────────────────────────────────────────

    /** Probeer één mDNS-hostnaam: DNS-resolutie + /ping verificatie. */
    private suspend fun tryMdnsHost(hostname: String): DiscoveryResult? =
        withContext(Dispatchers.IO) {
            try {
                // InetAddress.getByName lost .local op via mDNS op Android 4.1+
                val addr = InetAddress.getByName(hostname)
                val ip = addr.hostAddress
                val pingUrl = "http://$hostname:$DEFAULT_PORT/api/v2/ping"
                if (pingOk(pingUrl)) {
                    DiscoveryResult(host = hostname, resolvedIp = ip)
                } else null
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Scan alle 254 hostadressen in het /24-subnet parallel.
     * Eerst TCP-open-check (snel), dan pas HTTP-ping (alleen op open poorten).
     */
    private suspend fun scanSubnet(subnetBase: String): DiscoveryResult? =
        withContext(Dispatchers.IO) {
            coroutineScope {
                (1..254)
                    .map { i ->
                        async {
                            val ip = "$subnetBase.$i"
                            try {
                                // Snelle poorttoegang controleren vóór HTTP overhead
                                if (isTcpOpen(ip, DEFAULT_PORT)) {
                                    val pingUrl = "http://$ip:$DEFAULT_PORT/api/v2/ping"
                                    if (pingOk(pingUrl)) {
                                        Log.d(TAG, "Gevonden via scan: $ip")
                                        DiscoveryResult(host = ip, resolvedIp = ip)
                                    } else null
                                } else null
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
                    .firstOrNull()
            }
        }

    /** Probeer TCP-verbinding op [port] met [TCP_TIMEOUT_MS] ms timeout. */
    private fun isTcpOpen(ip: String, port: Int): Boolean =
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), TCP_TIMEOUT_MS)
                true
            }
        } catch (_: Exception) {
            false
        }

    /** Voer GET /api/v2/ping uit en controleer of het antwoord 2xx is. */
    private fun pingOk(url: String): Boolean {
        val req = Request.Builder().url(url).build()
        return try {
            discoveryHttp.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }
}
