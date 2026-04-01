package com.yvesds.vt5.features.masterClient

import android.annotation.SuppressLint

/**
 * Houdt de actieve master/client runtime-objects vast buiten een enkele Activity-lifecycle.
 *
 * Hierdoor kunnen bevestigde clients verbonden blijven wanneer de master van telling wisselt
 * (bijv. via MetadataScherm → SoortSelectieScherm → nieuwe TellingScherm).
 */
@SuppressLint("StaticFieldLeak")
object MasterClientRuntimeStore {

    @Volatile
    private var preserveAcrossTellings = false

    @Volatile
    private var masterServer: MasterServer? = null
    @Volatile
    private var pairingManager: PairingManager? = null
    @Volatile
    private var eventProcessor: MasterEventProcessor? = null

    @Volatile
    private var clientConnector: ClientConnector? = null
    @Volatile
    private var clientEventQueue: ClientEventQueue? = null

    @Synchronized
    fun hasActiveRuntime(): Boolean = masterServer != null || clientConnector != null

    @Synchronized
    fun isPreservingAcrossTellings(): Boolean = preserveAcrossTellings

    @Synchronized
    fun markPreserveAcrossTellings() {
        preserveAcrossTellings = true
    }

    @Synchronized
    fun clearPreserveAcrossTellings() {
        preserveAcrossTellings = false
    }

    @Synchronized
    fun storeMasterRuntime(
        server: MasterServer,
        pairing: PairingManager,
        processor: MasterEventProcessor
    ) {
        masterServer = server
        pairingManager = pairing
        eventProcessor = processor
        preserveAcrossTellings = true
    }

    @Synchronized
    fun getMasterServer(): MasterServer? = masterServer

    @Synchronized
    fun getPairingManager(): PairingManager? = pairingManager

    @Synchronized
    fun getMasterEventProcessor(): MasterEventProcessor? = eventProcessor

    @Synchronized
    fun storeClientRuntime(connector: ClientConnector, queue: ClientEventQueue) {
        clientConnector = connector
        clientEventQueue = queue
        preserveAcrossTellings = true
    }

    @Synchronized
    fun getClientConnector(): ClientConnector? = clientConnector

    @Synchronized
    fun getClientEventQueue(): ClientEventQueue? = clientEventQueue

    @Synchronized
    fun clearMasterRuntime(stopServer: Boolean = true) {
        if (stopServer) {
            try {
                masterServer?.stop()
            } catch (_: Exception) {
            }
        }
        masterServer = null
        pairingManager = null
        eventProcessor = null
    }

    @Synchronized
    fun clearClientRuntime(stopClient: Boolean = true) {
        if (stopClient) {
            try {
                clientConnector?.stop()
            } catch (_: Exception) {
            }
        }
        clientConnector = null
        clientEventQueue = null
    }

    @Synchronized
    fun clearAll(stopServer: Boolean = true, stopClient: Boolean = true) {
        clearMasterRuntime(stopServer)
        clearClientRuntime(stopClient)
        preserveAcrossTellings = false
    }
}

