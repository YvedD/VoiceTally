package com.yvesds.vt5.features.birdnet

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Modernized BirdNET-GO Auto-discovery & Subnet IP Scanner.
 *
 * Strategies:
 * 1. Active Subnet TCP/HTTP Scanner: Scans current Wi-Fi IPv4 subnet (e.g. 192.168.x.1..254, 10.x.x.x)
 *    using raw TCP socket port checks (super fast) followed by HTTP /api/v2/ping verification.
 * 2. Passive Discovery (NSD): Listens for _http._tcp services on the network.
 * 3. Active Discovery (mDNS): Attempts to resolve known hostnames if supported by network.
 */
object BirdNetDiscovery {

    private const val TAG = "BirdNetDiscovery"
    private const val SERVICE_TYPE = "_http._tcp."

    val MDNS_CANDIDATES = listOf(
        "birdnet.local",
        "birdnet-go.local",
        "raspberrypi.local",
    )

    const val DEFAULT_PORT = 8080
    private const val PING_TIMEOUT_S = 3L

    private val discoveryHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(PING_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(PING_TIMEOUT_S, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
            .build()
    }

    data class DiscoveryResult(
        val host: String,
        val resolvedIp: String?,
        val port: Int = DEFAULT_PORT,
        val protocol: String = "http",
        val source: String = "NSD"
    ) {
        fun toConfig() = BirdNetConfig(protocol = protocol, host = host, port = port)
        val displayLabel: String get() = if (resolvedIp != null && resolvedIp != host) "$host ($resolvedIp) [$source]" else "$host [$source]"
    }

    /**
     * Discover BirdNET-GO instances on the local network.
     * Uses Subnet IP Scanning, NSD, and mDNS in parallel/sequence.
     */
    suspend fun discoverAll(
        context: Context,
        port: Int = DEFAULT_PORT,
        onProgress: ((String) -> Unit)? = null
    ): List<DiscoveryResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiscoveryResult>()
        
        Log.i(TAG, "Start uitgebreide discovery voor BirdNET-GO op poort $port...")

        // 1. Primair: Snelle IP Subnet Scan (zoekt actieve apparaten op lokaal netwerk)
        onProgress?.invoke("Lokale IP-adressen (subnet) scannen op BirdNET-GO...")
        val subnetResults = scanSubnet(context, port, onProgress)
        subnetResults.forEach { ipHit ->
            if (results.none { it.resolvedIp == ipHit.resolvedIp }) {
                results.add(ipHit)
            }
        }

        // 2. NSD (Network Service Discovery)
        onProgress?.invoke("Netwerk scannen via NSD/mDNS...")
        val nsdResults = tryNsdDiscovery(context)
        nsdResults.forEach { nsd ->
            if (results.none { it.resolvedIp == nsd.resolvedIp }) {
                results.add(nsd)
            }
        }

        // 3. Direct mDNS hostnames
        coroutineScope {
            val mdnsJobs = MDNS_CANDIDATES.map { hostname ->
                async { tryMdnsHost(hostname, port) }
            }
            val mdnsResults = mdnsJobs.awaitAll().filterNotNull()
            mdnsResults.forEach { mdns ->
                if (results.none { it.resolvedIp == mdns.resolvedIp || it.host == mdns.host }) {
                    results.add(mdns)
                }
            }
        }

        Log.i(TAG, "Discovery afgerond. Totaal aantal unieke opties: ${results.size}")
        results
    }

    /**
     * Haalt het actieve Wi-Fi IPv4-adres van de tablet op (bijv. "192.168.1.145").
     */
    fun getLocalWifiIpAddress(context: Context): String? {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val network = cm.activeNetwork
                val capabilities = cm.getNetworkCapabilities(network)
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    val linkProperties = cm.getLinkProperties(network)
                    if (linkProperties != null) {
                        for (linkAddress in linkProperties.linkAddresses) {
                            val address = linkAddress.address
                            if (address is Inet4Address && !address.isLoopbackAddress) {
                                val ip = address.hostAddress
                                if (!ip.isNullOrBlank() && !ip.startsWith("127.")) {
                                    return ip
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij ophalen Wi-Fi IP via ConnectivityManager: ${e.message}")
        }

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    java.util.Locale.US,
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij ophalen Wi-Fi IP via WifiManager: ${e.message}")
        }

        // Fallback: scannen van netwerkinterfaces
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces?.hasMoreElements() == true) {
                val intf = interfaces.nextElement()
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress
                        if (!ip.isNullOrBlank() && !ip.startsWith("127.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij ophalen IP via NetworkInterfaces: ${e.message}")
        }

        return null
    }

    /**
     * Detecteert actieve IPv4 subnet-prefixes van het toestel.
     * Het actieve subnet van de tablet staat ALTIJD bovenaan!
     */
    fun getActiveSubnetPrefixes(context: Context): List<String> {
        val prefixes = LinkedHashSet<String>()

        // 1. Actief Wi-Fi IP van tablet ophalen
        val activeIp = getLocalWifiIpAddress(context)
        if (!activeIp.isNullOrBlank()) {
            val parts = activeIp.split(".")
            if (parts.size == 4) {
                val primaryPrefix = "${parts[0]}.${parts[1]}.${parts[2]}."
                prefixes.add(primaryPrefix)
                Log.i(TAG, "Primair actief Wi-Fi subnet van tablet: $primaryPrefix (Tablet IP: $activeIp)")
            }
        }

        // 2. Bekende thuis- en hotspot-subnetten toevoegen
        prefixes.add("192.168.1.")   // Thuis LAN
        prefixes.add("192.168.0.")   // Thuis LAN
        prefixes.add("192.168.178.") // FritzBox
        prefixes.add("192.168.2.")   // KPN / Ziggo Box
        prefixes.add("192.168.50.")  // Asus / Mesh
        prefixes.add("192.168.68.")  // TP-Link Deco Mesh
        prefixes.add("10.0.0.")      // 10.x LAN
        prefixes.add("10.209.161.")  // Motorola G56 Hotspot
        prefixes.add("192.168.43.")  // Standaard Android Hotspot
        prefixes.add("10.42.0.")     // Raspberry Pi AP

        return prefixes.toList()
    }

    /**
     * Snelle TCP Socket poort-check (2ms - 800ms) om te zien of poort 8080 open is.
     */
    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int = 800): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Scans IPv4 addresses 1..254 across active subnet prefixes.
     */
    suspend fun scanSubnet(
        context: Context,
        port: Int = DEFAULT_PORT,
        onProgress: ((String) -> Unit)? = null
    ): List<DiscoveryResult> = withContext(Dispatchers.IO) {
        val found = mutableListOf<DiscoveryResult>()
        val prefixes = getActiveSubnetPrefixes(context)
        val activeTabletIp = getLocalWifiIpAddress(context)
        val semaphore = Semaphore(25) // Max 25 gelijktijdige pings om netwerk/router niet te overbelasten

        for (prefix in prefixes) {
            onProgress?.invoke("IP-reeks ${prefix}x scannen op poort $port...")
            Log.d(TAG, "Scanning subnet ${prefix}1..254 op poort $port")

            coroutineScope {
                val jobs = (1..254).map { hostNum ->
                    async {
                        val targetIp = "$prefix$hostNum"
                        if (targetIp == activeTabletIp) return@async

                        semaphore.acquire()
                        try {
                            // Stap 1: Ultrasnelle TCP poortcheck op poort 8080
                            if (isPortOpen(targetIp, port, 750)) {
                                Log.i(TAG, "✓ Poort $port is open op $targetIp! HTTP ping verifiëren...")
                                
                                // Stap 2: HTTP API Ping verifiëren
                                val pingUrl1 = "http://$targetIp:$port/api/v2/ping"
                                val pingUrl2 = "http://$targetIp:$port/ping"
                                
                                if (pingOk(pingUrl1) || pingOk(pingUrl2)) {
                                    Log.i(TAG, "✓ BirdNET-GO bevestigd via IP-scan: $targetIp:$port")
                                    synchronized(found) {
                                        found.add(
                                            DiscoveryResult(
                                                host = targetIp,
                                                resolvedIp = targetIp,
                                                port = port,
                                                source = "IP Subnet"
                                            )
                                        )
                                    }
                                }
                            }
                        } finally {
                            semaphore.release()
                        }
                    }
                }
                jobs.awaitAll()
            }

            if (found.isNotEmpty() && prefix == prefixes.first()) {
                Log.i(TAG, "BirdNET-GO gevonden in primair subnet van tablet ($prefix), scan succesvol afgerond!")
                break
            }
        }

        found
    }

    suspend fun pingHost(config: BirdNetConfig): Boolean =
        withContext(Dispatchers.IO) { 
            Log.d(TAG, "Ping naar: ${config.pingUrl}")
            pingOk(config.pingUrl) || pingOk("${config.baseUrl.removeSuffix("/api/v2")}/ping")
        }

    suspend fun pingHostDetailed(config: BirdNetConfig): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val url1 = config.pingUrl
            val url2 = "${config.baseUrl.removeSuffix("/api/v2")}/ping"
            
            val res1 = pingOkDetailed(url1)
            if (res1.first) return@withContext Pair(true, "OK")

            val res2 = pingOkDetailed(url2)
            if (res2.first) return@withContext Pair(true, "OK")

            Pair(false, res1.second)
        }

    private fun pingOkDetailed(url: String): Pair<Boolean, String> {
        val req = Request.Builder()
            .url(url)
            .addHeader("Connection", "close")
            .addHeader("User-Agent", "VoiceTally-Android")
            .build()
        return try {
            val response = discoveryHttp.newCall(req).execute()
            val ok = response.isSuccessful
            val code = response.code
            response.close()
            if (ok) Pair(true, "OK") else Pair(false, "HTTP $code")
        } catch (e: Exception) {
            val msg = e.message ?: e.toString()
            Log.w(TAG, "Ping mislukt voor $url: $msg")
            Pair(false, msg)
        }
    }

    // ─── Internal Helpers ─────────────────────────────────────────────────────

    private suspend fun tryNsdDiscovery(context: Context): List<DiscoveryResult> {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return emptyList()
        val foundItems = mutableListOf<DiscoveryResult>()
        val nsdTimeoutMs = 2000L

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "NSD Start failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "NSD Stop failed: $errorCode")
            }
            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d(TAG, "NSD Discovery gestart voor type: $serviceType")
            }
            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d(TAG, "NSD Discovery handmatig gestopt")
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "NSD Service gevonden: ${serviceInfo.serviceName}")
                val name = serviceInfo.serviceName.lowercase()
                if (name.contains("birdnet") || name.contains("raspberry") || name.contains("voicetally")) {
                    @Suppress("DEPRECATION")
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                            Log.w(TAG, "NSD Resolve mislukt voor ${serviceInfo?.serviceName} (Fout: $errorCode)")
                        }
                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val host = resolvedInfo.host.hostAddress
                            val port = resolvedInfo.port
                            Log.i(TAG, "NSD Service opgelost: $host:$port")
                            if (host != null && (pingOk("http://$host:$port/api/v2/ping") || pingOk("http://$host:$port/ping"))) {
                                synchronized(foundItems) {
                                    foundItems.add(DiscoveryResult(host = host, resolvedIp = host, port = port, source = "NSD"))
                                }
                            }
                        }
                    })
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
        }

        return try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            delay(nsdTimeoutMs)
            foundItems
        } catch (e: Exception) {
            Log.e(TAG, "Fout tijdens NSD scan: ${e.message}")
            emptyList()
        } finally {
            try { nsdManager.stopServiceDiscovery(listener) } catch (_: Exception) {}
        }
    }

    private suspend fun tryMdnsHost(hostname: String, port: Int): DiscoveryResult? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "mDNS resolutie poging voor: $hostname:$port")
            val addr = InetAddress.getByName(hostname)
            val ip = addr.hostAddress ?: return@withContext null
            Log.i(TAG, "mDNS succes: $hostname -> $ip")
            val pingUrl = "http://$hostname:$port/api/v2/ping"
            if (pingOk(pingUrl)) {
                DiscoveryResult(host = hostname, resolvedIp = ip, port = port, source = "mDNS")
            } else {
                Log.w(TAG, "mDNS host $hostname gevonden ($ip), maar /ping antwoordt niet op poort $port")
                null
            }
        } catch (e: Exception) {
            Log.v(TAG, "mDNS host $hostname niet gevonden: ${e.message}")
            null
        }
    }

    private fun pingOk(url: String): Boolean {
        return pingOkDetailed(url).first
    }
}
