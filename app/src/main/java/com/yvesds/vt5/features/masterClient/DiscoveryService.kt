package com.yvesds.vt5.features.masterClient

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * DiscoveryService – NSD (mDNS / Bonjour) advertentie en ontdekking voor het master-client systeem.
 *
 * Gebruik:
 *  - Master roept [startAdvertising] aan na het starten van de server.
 *  - Client roept [startDiscovery] aan; gevonden masters worden via [discoveredMasters] gerapporteerd.
 *  - Beide kanten roepen [stop] aan bij onDestroy.
 */
class DiscoveryService(private val context: Context) {

    companion object {
        private const val TAG          = "DiscoveryService"
        const val SERVICE_TYPE         = "_vt5mc._tcp."
        const val SERVICE_NAME_PREFIX  = "VT5-master"
        private const val TXT_KEY_PORT = "port"
    }

    data class MasterInfo(
        val name: String,
        val host: String,
        val port: Int
    )

    private val _discoveredMasters = MutableStateFlow<List<MasterInfo>>(emptyList())
    val discoveredMasters: StateFlow<List<MasterInfo>> = _discoveredMasters

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener?        = null

    // ─── Master: adverteer service ────────────────────────────────────────────

    /**
     * Start NSD-advertentie zodat clients dit toestel kunnen vinden.
     * @param port   TCP-poort waarop de MasterServer luistert
     */
    fun startAdvertising(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "$SERVICE_NAME_PREFIX-${android.os.Build.MODEL}"
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "NSD geregistreerd: ${info.serviceName}")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD registratie mislukt: code $errorCode")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.i(TAG, "NSD uitgeschreven: ${info.serviceName}")
            }
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD uitschrijving mislukt: code $errorCode")
            }
        }

        registrationListener = listener
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "startAdvertising exception: ${e.message}", e)
        }
    }

    /** Stop NSD-advertentie. */
    fun stopAdvertising() {
        val listener = registrationListener ?: return
        try {
            nsdManager.unregisterService(listener)
        } catch (e: Exception) {
            Log.w(TAG, "stopAdvertising exception: ${e.message}", e)
        } finally {
            registrationListener = null
        }
    }

    // ─── Client: ontdek masters ───────────────────────────────────────────────

    /** Start NSD-ontdekking. Gevonden masters worden bijgehouden in [discoveredMasters]. */
    fun startDiscovery() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.i(TAG, "NSD ontdekking gestart voor $serviceType")
            }
            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "NSD ontdekking gestopt voor $serviceType")
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "NSD ontdekking start mislukt: code $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "NSD ontdekking stop mislukt: code $errorCode")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceName.startsWith(SERVICE_NAME_PREFIX)) return
                Log.d(TAG, "Service gevonden: ${serviceInfo.serviceName}")
                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service verloren: ${serviceInfo.serviceName}")
                val current = _discoveredMasters.value.toMutableList()
                current.removeAll { it.name == serviceInfo.serviceName }
                _discoveredMasters.value = current
            }
        }

        discoveryListener = listener
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "startDiscovery exception: ${e.message}", e)
        }
    }

    /** Stop NSD-ontdekking. */
    fun stopDiscovery() {
        val listener = discoveryListener ?: return
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (e: Exception) {
            Log.w(TAG, "stopDiscovery exception: ${e.message}", e)
        } finally {
            discoveryListener = null
        }
    }

    /** Wis de lijst met gevonden masters. */
    fun clearDiscovered() {
        _discoveredMasters.value = emptyList()
    }

    /** Stop advertentie én ontdekking. */
    fun stop() {
        stopAdvertising()
        stopDiscovery()
    }

    // ─── Intern: resolve service naar adres+poort ─────────────────────────────

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        try {
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "Resolve mislukt voor ${info.serviceName}: code $errorCode")
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = info.host?.hostAddress ?: return
                    val port = info.port
                    Log.i(TAG, "Resolved ${info.serviceName} → $host:$port")
                    val master = MasterInfo(
                        name = info.serviceName,
                        host = host,
                        port = port
                    )
                    val current = _discoveredMasters.value.toMutableList()
                    current.removeAll { it.name == master.name }
                    current.add(master)
                    _discoveredMasters.value = current
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "resolveService exception: ${e.message}", e)
        }
    }
}
