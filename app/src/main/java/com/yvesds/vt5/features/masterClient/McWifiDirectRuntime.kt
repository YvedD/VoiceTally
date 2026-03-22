package com.yvesds.vt5.features.masterClient

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class McWifiDirectRuntime(context: Context) {

    companion object {
        private const val TAG = "McWifiDirectRuntime"
        private const val DEFAULT_GROUP_OWNER_ADDRESS = "192.168.49.1"
    }

    data class SessionInfo(
        val networkName: String,
        val passphrase: String,
        val security: String,
        val ownerAddress: String,
        val ownerDeviceAddress: String,
        val ownerDeviceName: String,
        val isGroupOwner: Boolean,
        val sessionId: String = "",
        val serviceTag: String = ""
    )

    private data class ServiceMetadata(
        val sessionId: String = "",
        val serviceTag: String = "",
        val networkName: String = "",
        val ownerAddress: String = "",
        val ownerDeviceName: String = ""
    )

    sealed class State {
        data object Idle : State()
        data class Starting(val message: String) : State()
        data class Discovering(val targetHint: String) : State()
        data class Connecting(val targetHint: String) : State()
        data class Ready(val sessionInfo: SessionInfo) : State()
        data class Lost(val message: String) : State()
        data class Error(val message: String) : State()
    }

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private val manager: WifiP2pManager? by lazy {
        appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }

    private var channel: WifiP2pManager.Channel? = null
    private var receiverRegistered = false
    private var pendingJoinTarget: McWifiDirectJoinResolver.JoinTarget? = null
    private var pendingSessionId: String = ""
    private var lastPeers: List<WifiP2pDevice> = emptyList()
    private var pendingConnectCallback: ((Result<SessionInfo>) -> Unit)? = null
    private var pendingGroupOwnerCallback: ((Result<SessionInfo>) -> Unit)? = null
    private var lastSessionInfo: SessionInfo? = null
    private val discoveredServices = mutableMapOf<String, ServiceMetadata>()
    private var serviceRequest: WifiP2pDnsSdServiceRequest? = null
    private var localServiceInfo: WifiP2pDnsSdServiceInfo? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val enabled = intent.getIntExtra(
                        WifiP2pManager.EXTRA_WIFI_STATE,
                        WifiP2pManager.WIFI_P2P_STATE_DISABLED
                    ) == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    if (!enabled) {
                        deliverError("Wi‑Fi Direct is uitgeschakeld op dit toestel")
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    refreshConnectionInfo()
                    maybeConnectToPendingPeer()
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    // Geen directe actie nodig in fase 2.
                }
            }
        }
    }

    fun isSupported(): Boolean = manager != null

    fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.NEARBY_WIFI_DEVICES) ==
            PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun ensureGroupOwner(
        sessionId: String = "",
        callback: (Result<SessionInfo>) -> Unit
    ) {
        val p2p = manager ?: run {
            callback(Result.failure(IllegalStateException("Wi‑Fi Direct niet ondersteund")))
            return
        }
        if (!hasRequiredPermissions()) {
            callback(Result.failure(SecurityException("NEARBY_WIFI_DEVICES permissie ontbreekt")))
            return
        }

        ensureInitialized()
        registerReceiverIfNeeded()
        pendingSessionId = sessionId
        pendingGroupOwnerCallback = callback

        val current = lastSessionInfo
        if (current?.isGroupOwner == true) {
            _state.value = State.Ready(current)
            pendingGroupOwnerCallback = null
            callback(Result.success(current))
            return
        }

        _state.value = State.Starting("Wi‑Fi Direct groep starten…")
        setupServiceDiscoveryListeners()
        p2p.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                refreshConnectionInfo()
            }

            override fun onFailure(reason: Int) {
                if (reason == WifiP2pManager.BUSY) {
                    refreshConnectionInfo()
                } else {
                    deliverError("Wi‑Fi Direct groep starten mislukt (${reasonToText(reason)})")
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connectToOwner(
        joinTarget: McWifiDirectJoinResolver.JoinTarget,
        callback: (Result<SessionInfo>) -> Unit
    ) {
        val p2p = manager ?: run {
            callback(Result.failure(IllegalStateException("Wi‑Fi Direct niet ondersteund")))
            return
        }
        if (!hasRequiredPermissions()) {
            callback(Result.failure(SecurityException("NEARBY_WIFI_DEVICES permissie ontbreekt")))
            return
        }
        if (joinTarget.ownerDeviceAddress.isBlank() &&
            joinTarget.ownerDeviceName.isBlank() &&
            joinTarget.serviceTag.isBlank() &&
            joinTarget.sessionId.isBlank()
        ) {
            callback(Result.failure(IllegalArgumentException("Geen Wi‑Fi Direct joinhints opgegeven")))
            return
        }

        ensureInitialized()
        registerReceiverIfNeeded()
        setupServiceDiscoveryListeners()
        pendingJoinTarget = joinTarget
        pendingSessionId = joinTarget.sessionId
        pendingConnectCallback = callback
        _state.value = State.Discovering(joinTarget.summary())

        startServiceDiscovery()

        p2p.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                requestPeers()
            }

            override fun onFailure(reason: Int) {
                if (reason == WifiP2pManager.BUSY) {
                    requestPeers()
                } else {
                    deliverError("Wi‑Fi Direct peers zoeken mislukt (${reasonToText(reason)})")
                }
            }
        })
    }

    fun reconnect(
        joinTarget: McWifiDirectJoinResolver.JoinTarget,
        callback: (Result<SessionInfo>) -> Unit
    ) {
        connectToOwner(joinTarget, callback)
    }

    fun stop() {
        val p2p = manager
        val ch = channel
        pendingJoinTarget = null
        pendingSessionId = ""
        pendingConnectCallback = null
        pendingGroupOwnerCallback = null
        lastPeers = emptyList()
        lastSessionInfo = null
        discoveredServices.clear()
        if (p2p != null && ch != null && hasRequiredPermissions()) {
            serviceRequest?.let { request ->
                runCatching {
                    p2p.removeServiceRequest(ch, request, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {}
                        override fun onFailure(reason: Int) {}
                    })
                }
            }
            serviceRequest = null
            localServiceInfo?.let { service ->
                runCatching {
                    p2p.removeLocalService(ch, service, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {}
                        override fun onFailure(reason: Int) {}
                    })
                }
            }
            localServiceInfo = null
        }
        if (p2p != null && ch != null && hasRequiredPermissions()) {
            runCatching {
                p2p.removeGroup(ch, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {}
                    override fun onFailure(reason: Int) {}
                })
            }
        }
        unregisterReceiverIfNeeded()
        _state.value = State.Idle
    }

    private fun ensureInitialized() {
        if (channel != null) return
        channel = manager?.initialize(appContext, Looper.getMainLooper(), null)
    }

    private fun registerReceiverIfNeeded() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    private fun unregisterReceiverIfNeeded() {
        if (!receiverRegistered) return
        runCatching { appContext.unregisterReceiver(receiver) }
        receiverRegistered = false
    }

    @SuppressLint("MissingPermission")
    private fun setupServiceDiscoveryListeners() {
        val p2p = manager ?: return
        val ch = channel ?: return
        if (!hasRequiredPermissions()) return
        runCatching {
            p2p.setDnsSdResponseListeners(
                ch,
                { instanceName, _, srcDevice ->
                    val current = discoveredServices[srcDevice.deviceAddress]
                    discoveredServices[srcDevice.deviceAddress] = (current ?: ServiceMetadata()).copy(
                        serviceTag = instanceName.orEmpty().substringAfter("VT5-", missingDelimiterValue = instanceName.orEmpty())
                    )
                    maybeConnectToPendingPeer()
                },
                { _, txtRecordMap, device ->
                    discoveredServices[device.deviceAddress] = ServiceMetadata(
                        sessionId = txtRecordMap["sessionId"].orEmpty(),
                        serviceTag = txtRecordMap["serviceTag"].orEmpty(),
                        networkName = txtRecordMap["networkName"].orEmpty(),
                        ownerAddress = txtRecordMap["ownerAddress"].orEmpty(),
                        ownerDeviceName = txtRecordMap["ownerDeviceName"].orEmpty()
                    )
                    maybeConnectToPendingPeer()
                }
            )
        }.onFailure { ex ->
            Log.w(TAG, "setDnsSdResponseListeners mislukt: ${ex.message}", ex)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startServiceDiscovery() {
        val p2p = manager ?: return
        val ch = channel ?: return
        if (!hasRequiredPermissions()) return
        discoveredServices.clear()
        val request = serviceRequest ?: WifiP2pDnsSdServiceRequest.newInstance().also { serviceRequest = it }
        runCatching {
            p2p.addServiceRequest(ch, request, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    p2p.discoverServices(ch, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {}
                        override fun onFailure(reason: Int) {
                            Log.w(TAG, "discoverServices mislukt: ${reasonToText(reason)}")
                        }
                    })
                }

                override fun onFailure(reason: Int) {
                    if (reason != WifiP2pManager.BUSY) {
                        Log.w(TAG, "addServiceRequest mislukt: ${reasonToText(reason)}")
                    }
                }
            })
        }.onFailure { ex ->
            Log.w(TAG, "startServiceDiscovery exception: ${ex.message}", ex)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestPeers() {
        val p2p = manager ?: return
        val ch = channel ?: return
        if (!hasRequiredPermissions()) return
        runCatching {
            p2p.requestPeers(ch) { peers ->
                lastPeers = peers.deviceList.toList()
                maybeConnectToPendingPeer()
            }
        }.onFailure { ex ->
            Log.w(TAG, "requestPeers mislukt: ${ex.message}", ex)
        }
    }

    @SuppressLint("MissingPermission")
    private fun maybeConnectToPendingPeer() {
        val joinTarget = pendingJoinTarget ?: return
        val p2p = manager ?: return
        val ch = channel ?: return
        val candidates = lastPeers.map { peer ->
            val service = discoveredServices[peer.deviceAddress]
            McWifiDirectJoinResolver.Candidate(
                deviceAddress = peer.deviceAddress.orEmpty(),
                deviceName = peer.deviceName.orEmpty(),
                networkName = service?.networkName.orEmpty(),
                ownerAddress = service?.ownerAddress.orEmpty(),
                advertisedSessionId = service?.sessionId.orEmpty(),
                advertisedServiceTag = service?.serviceTag.orEmpty(),
                advertisedOwnerDeviceName = service?.ownerDeviceName.orEmpty()
            )
        }
        val selected = McWifiDirectJoinResolver.resolve(joinTarget, candidates) ?: return
        _state.value = State.Connecting(joinTarget.summary())
        val config = WifiP2pConfig().apply {
            deviceAddress = selected.deviceAddress
            wps.setup = WpsInfo.PBC
            groupOwnerIntent = 0
        }
        p2p.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                refreshConnectionInfo()
            }

            override fun onFailure(reason: Int) {
                deliverError("Wi‑Fi Direct verbinden mislukt (${reasonToText(reason)})")
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun refreshConnectionInfo() {
        val p2p = manager ?: return
        val ch = channel ?: return
        if (!hasRequiredPermissions()) return
        try {
            p2p.requestConnectionInfo(ch) { info ->
                if (info == null || !info.groupFormed) {
                    if (lastSessionInfo != null) {
                        _state.value = State.Lost("Wi‑Fi Direct sessie verbroken")
                    }
                    return@requestConnectionInfo
                }
                requestGroupInfo(info)
            }
        } catch (se: SecurityException) {
            deliverError("Permissie ontbreekt voor Wi‑Fi Direct info")
        } catch (ex: Exception) {
            deliverError("Wi‑Fi Direct info ophalen mislukt: ${ex.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestGroupInfo(info: WifiP2pInfo) {
        val p2p = manager ?: return
        val ch = channel ?: return
        try {
            p2p.requestGroupInfo(ch) { group ->
                val ownerDeviceAddress = group?.owner?.deviceAddress.orEmpty()
                val serviceMetadata = discoveredServices[ownerDeviceAddress]
                val sessionId = pendingSessionId.ifBlank { serviceMetadata?.sessionId.orEmpty() }
                val serviceTag = serviceMetadata?.serviceTag.orEmpty()
                val ownerAddress = info.groupOwnerAddress?.hostAddress
                    ?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_GROUP_OWNER_ADDRESS
                val sessionInfo = SessionInfo(
                    networkName = group?.networkName.orEmpty(),
                    passphrase = group?.passphrase.orEmpty(),
                    security = if (group?.passphrase.isNullOrBlank()) "NOPASS" else "WPA",
                    ownerAddress = ownerAddress,
                    ownerDeviceAddress = ownerDeviceAddress,
                    ownerDeviceName = group?.owner?.deviceName.orEmpty().ifBlank {
                        serviceMetadata?.ownerDeviceName.orEmpty().ifBlank { Build.MODEL }
                    },
                    isGroupOwner = info.isGroupOwner,
                    sessionId = sessionId,
                    serviceTag = serviceTag.ifBlank {
                        if (sessionId.isBlank()) "" else McWifiDirectJoinResolver.buildServiceTag(sessionId)
                    }
                )
                lastSessionInfo = sessionInfo
                _state.value = State.Ready(sessionInfo)
                if (sessionInfo.isGroupOwner) {
                    advertiseLocalService(sessionInfo)
                }
                if (sessionInfo.isGroupOwner) {
                    pendingGroupOwnerCallback?.invoke(Result.success(sessionInfo))
                    pendingGroupOwnerCallback = null
                } else {
                    pendingConnectCallback?.invoke(Result.success(sessionInfo))
                    pendingConnectCallback = null
                    pendingJoinTarget = null
                }
            }
        } catch (se: SecurityException) {
            deliverError("Permissie ontbreekt voor Wi‑Fi Direct groepinfo")
        } catch (ex: Exception) {
            deliverError("Wi‑Fi Direct groepinfo ophalen mislukt: ${ex.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun advertiseLocalService(sessionInfo: SessionInfo) {
        val p2p = manager ?: return
        val ch = channel ?: return
        if (!hasRequiredPermissions()) return
        val serviceTag = sessionInfo.serviceTag.ifBlank {
            if (sessionInfo.sessionId.isBlank()) "VT5MC" else McWifiDirectJoinResolver.buildServiceTag(sessionInfo.sessionId)
        }
        val record = mapOf(
            "sessionId" to sessionInfo.sessionId,
            "serviceTag" to serviceTag,
            "networkName" to sessionInfo.networkName,
            "ownerAddress" to sessionInfo.ownerAddress,
            "ownerDeviceName" to sessionInfo.ownerDeviceName
        )
        val instanceName = "VT5-$serviceTag"
        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(instanceName, "_vt5mc._tcp", record)
        localServiceInfo?.let { previous ->
            runCatching {
                p2p.removeLocalService(ch, previous, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {}
                    override fun onFailure(reason: Int) {}
                })
            }
        }
        localServiceInfo = serviceInfo
        runCatching {
            p2p.addLocalService(ch, serviceInfo, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {}
                override fun onFailure(reason: Int) {
                    Log.w(TAG, "addLocalService mislukt: ${reasonToText(reason)}")
                }
            })
        }.onFailure { ex ->
            Log.w(TAG, "advertiseLocalService exception: ${ex.message}", ex)
        }
    }

    private fun deliverError(message: String) {
        Log.w(TAG, message)
        _state.value = State.Error(message)
        val ex = IllegalStateException(message)
        pendingGroupOwnerCallback?.invoke(Result.failure(ex))
        pendingConnectCallback?.invoke(Result.failure(ex))
        pendingGroupOwnerCallback = null
        pendingConnectCallback = null
        pendingJoinTarget = null
    }

    private fun reasonToText(reason: Int): String {
        return when (reason) {
            WifiP2pManager.ERROR -> "interne fout"
            WifiP2pManager.P2P_UNSUPPORTED -> "niet ondersteund"
            WifiP2pManager.BUSY -> "radio bezet"
            else -> "code $reason"
        }
    }
}

