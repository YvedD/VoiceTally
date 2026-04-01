package com.yvesds.vt5.features.masterClient

import android.content.Context
import android.util.Log
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.features.masterClient.protocol.AckMessage
import com.yvesds.vt5.features.masterClient.protocol.ExportDataMessage
import com.yvesds.vt5.features.masterClient.protocol.HeartbeatMessage
import com.yvesds.vt5.features.masterClient.protocol.LeaveMessage
import com.yvesds.vt5.features.masterClient.protocol.MasterHandoverMessage
import com.yvesds.vt5.features.masterClient.protocol.MC_MSG_ACK
import com.yvesds.vt5.features.masterClient.protocol.MC_MSG_EXPORT_DATA
import com.yvesds.vt5.features.masterClient.protocol.MC_MSG_HEARTBEAT
import com.yvesds.vt5.features.masterClient.protocol.MC_MSG_LEAVE
import com.yvesds.vt5.features.masterClient.protocol.MC_MSG_MASTER_HANDOVER
import com.yvesds.vt5.features.masterClient.protocol.MC_MSG_OBSERVATION
import com.yvesds.vt5.features.masterClient.protocol.MC_MSG_PAIRING_REQ
import com.yvesds.vt5.features.masterClient.protocol.MC_MSG_PAIRING_RESP
import com.yvesds.vt5.features.masterClient.protocol.MC_MSG_SESSION_END
import com.yvesds.vt5.features.masterClient.protocol.MC_MSG_TILE_SYNC
import com.yvesds.vt5.features.masterClient.protocol.TileSyncMessage
import com.yvesds.vt5.features.masterClient.protocol.TileSyncItem
import com.yvesds.vt5.features.masterClient.protocol.McEnvelope
import com.yvesds.vt5.features.masterClient.protocol.ObservationEvent
import com.yvesds.vt5.features.masterClient.protocol.PairingRequest
import com.yvesds.vt5.features.masterClient.protocol.PairingResponse
import com.yvesds.vt5.features.masterClient.protocol.SessionEndMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale

/**
 * MasterServer – lokale TCP-server die draait op het master-toestel.
 *
 * Luistert op [port] (default 50234). Voor elke inkomende verbinding:
 *  1. Verwacht een PairingRequest om de client te authenticeren via de PIN.
 *  2. Stuurt een PairingResponse terug.
 *  3. Leest vervolgens ObservationEvent-berichten en verwerkt ze via [eventProcessor].
 *
 * Berichten worden uitgewisseld als newline-delimited JSON (McEnvelope).
 */
class MasterServer(
    private val context: Context,
    private val pairingManager: PairingManager,
    private val eventProcessor: MasterEventProcessor,
    val port: Int = MasterClientPrefs.DEFAULT_PORT
) {
    companion object {
        private const val TAG = "MasterServer"
    }

    data class ClientIdentity(
        val clientId: String,
        val deviceName: String,
        val alias: String,
        val ordinal: Int,
        val sessionToken: String
    ) {
        val displayLabel: String
            get() = alias.ifBlank { String.format(Locale.US, "Cl%03d", ordinal) }

        val logPrefix: String
            get() = alias.takeIf { it.isNotBlank() }?.let { "CL:$it" }
                ?: String.format(Locale.US, "Cl%03d", ordinal)

        val approvalLabel: String
            get() = buildString {
                append(displayLabel)
                if (deviceName.isNotBlank() && !deviceName.equals(displayLabel, ignoreCase = true)) {
                    append(" • ")
                    append(deviceName)
                }
            }
    }

    private val json = VT5App.json

    // Verbonden clients: token → clientnaam
    private val _connectedClients = MutableStateFlow<Map<String, String>>(emptyMap())
    val connectedClients: StateFlow<Map<String, String>> = _connectedClients
    private val clientProfilesByToken = mutableMapOf<String, ClientIdentity>()
    private val knownClientProfilesById = mutableMapOf<String, ClientIdentity>()
    private val clientOrdinalsById = mutableMapOf<String, Int>()
    private var nextClientOrdinal = 1

    // Writers per sessietoken zodat de master broadcast-berichten kan sturen
    private val clientWriters = mutableMapOf<String, PrintWriter>()
    private val clientWritersLock = Any()

    /** Optionele callback: aangeroepen (op IO-thread) wanneer een client de telling verlaat. */
    var onClientLeft: ((clientName: String, reason: String) -> Unit)? = null

    /** Optionele callback: aangeroepen (op IO-thread) wanneer een client succesvol gekoppeld is. */
    var onClientConnected: ((clientName: String) -> Unit)? = null

    /** Optionele callback: vraag master-operator om toestemming voor nieuwe client. */
    var onPairingRequest: (suspend (clientId: String, clientName: String) -> Boolean)? = null

    /** Optionele callback: lever huidige tegelset voor tile-sync naar clients. */
    var onTilesSnapshot: (suspend () -> List<TileSyncItem>)? = null

    private var serverScope: CoroutineScope? = null
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var running = false

    // ─── Start / stop ─────────────────────────────────────────────────────────

    /**
     * Start de lokale TCP-server.
     * Roep aan vanuit een coroutine-context (of gebruik de interne scope).
     */
    fun start() {
        if (running) return
        running = true
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        serverScope = scope
        scope.launch {
            try {
                val ss = ServerSocket(port)
                serverSocket = ss
                Log.i(TAG, "MasterServer gestart op poort $port")
                while (isActive && !ss.isClosed) {
                    try {
                        val socket = ss.accept()
                        launch { handleClient(socket) }
                    } catch (e: Exception) {
                        if (isActive) Log.w(TAG, "accept() fout: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ServerSocket fout: ${e.message}", e)
            } finally {
                running = false
                Log.i(TAG, "MasterServer gestopt")
            }
        }
    }

    /** Stop de server en stuur eerst een SessionEnd-broadcast naar alle verbonden clients. */
    fun stop() {
        // Laat verbonden clients weten dat de sessie stopt vóór de socket gesloten wordt
        try { broadcastSessionEnd("server gestopt") } catch (_: Exception) {}
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverScope?.cancel()
        serverScope = null
        serverSocket = null
        _connectedClients.value = emptyMap()
        synchronized(clientWritersLock) {
            clientWriters.clear()
            clientProfilesByToken.clear()
            knownClientProfilesById.clear()
            clientOrdinalsById.clear()
            nextClientOrdinal = 1
        }
        pairingManager.revokeAll()
        Log.i(TAG, "MasterServer gestopt door aanroep stop()")
    }

    // ─── Client-afhandeling ───────────────────────────────────────────────────

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        val remote = socket.remoteSocketAddress.toString()
        Log.d(TAG, "Nieuwe verbinding van $remote")
        try {
            socket.use { s ->
                s.keepAlive = true
                s.tcpNoDelay = true
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val writer = PrintWriter(s.getOutputStream(), true, Charsets.UTF_8)

                // ── Stap 1: Pairing ──────────────────────────────────────────
                val firstLine = reader.readLine() ?: return@withContext
                val firstEnvelope = decodeEnvelope(firstLine) ?: return@withContext

                if (firstEnvelope.type != MC_MSG_PAIRING_REQ) {
                    Log.w(TAG, "Verwachtte PairingRequest, maar ontving: ${firstEnvelope.type}")
                    return@withContext
                }

                val pairingReq = decodePayload<PairingRequest>(firstEnvelope.payload) ?: return@withContext
                val (acceptedPin, sessionToken) = pairingManager.validateSession(
                    sessionId = pairingReq.session,
                    clientId = pairingReq.clientId,
                    reconnectToken = pairingReq.sessionToken
                )

                if (!acceptedPin) {
                    val pairingResp = PairingResponse(
                        accepted     = false,
                        sessionToken = "",
                        masterName   = android.os.Build.MODEL,
                        clientLabel  = "",
                        error        = "Ongeldige of verlopen sessie"
                    )
                    sendPairingResponse(writer, pairingResp)
                    Log.w(TAG, "Pairing geweigerd voor client ${pairingReq.clientId}")
                    return@withContext
                }

                val clientIdentity = registerClientIdentity(sessionToken, pairingReq)
                val approvedByMaster = onPairingRequest?.invoke(pairingReq.clientId, clientIdentity.approvalLabel) ?: true
                if (!approvedByMaster) {
                    pairingManager.revokeToken(sessionToken)
                    unregisterPendingClient(sessionToken)
                    val pairingResp = PairingResponse(
                        accepted     = false,
                        sessionToken = "",
                        masterName   = android.os.Build.MODEL,
                        clientOrdinal = clientIdentity.ordinal,
                        clientLabel  = clientIdentity.displayLabel,
                        error        = "Afgewezen door master"
                    )
                    sendPairingResponse(writer, pairingResp)
                    Log.w(TAG, "Pairing afgewezen door master voor client ${pairingReq.clientId}")
                    return@withContext
                }

                val pairingResp = PairingResponse(
                    accepted     = true,
                    sessionToken = sessionToken,
                    masterName   = android.os.Build.MODEL,
                    clientOrdinal = clientIdentity.ordinal,
                    clientLabel   = clientIdentity.displayLabel,
                    tellingId    = getActiveTellingId(),
                    error        = ""
                )
                sendPairingResponse(writer, pairingResp)

                // Voeg toe aan verbonden clients
                addConnectedClient(clientIdentity, writer)
                Log.i(TAG, "Client verbonden: ${clientIdentity.displayLabel} (${pairingReq.clientId})")
                onClientConnected?.invoke(clientIdentity.displayLabel)

                // Stuur huidige tegelset naar de client (indien beschikbaar)
                sendTileSyncIfAvailable(writer)

                // ── Stap 2: Event-loop ───────────────────────────────────────
                try {
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) continue
                        val envelope = decodeEnvelope(line) ?: continue
                        dispatchMessage(envelope, sessionToken, writer)
                    }
                } finally {
                    removeConnectedClient(sessionToken, writer, revokeToken = false)
                    Log.i(TAG, "Client verbroken: ${pairingReq.clientName}")
                }            }
        } catch (e: Exception) {
            Log.w(TAG, "Fout bij afhandeling client $remote: ${e.message}", e)
        }
    }

    private suspend fun dispatchMessage(
        envelope: McEnvelope,
        sessionToken: String,
        writer: PrintWriter
    ) {
        when (envelope.type) {
            MC_MSG_OBSERVATION -> {
                val event = decodePayload<ObservationEvent>(envelope.payload) ?: return
                if (event.sessionToken != sessionToken) {
                    Log.w(TAG, "Ongeldig sessietoken in ObservationEvent – genegeerd")
                    return
                }
                val ack = eventProcessor.processEvent(event)
                sendAck(writer, ack)
            }
            MC_MSG_EXPORT_DATA -> {
                val export = decodePayload<ExportDataMessage>(envelope.payload) ?: return
                if (export.sessionToken != sessionToken) {
                    Log.w(TAG, "Ongeldig sessietoken in ExportData – genegeerd")
                    return
                }
                eventProcessor.processExport(export)
            }
            MC_MSG_LEAVE -> {
                val leave = decodePayload<LeaveMessage>(envelope.payload) ?: return
                if (leave.sessionToken != sessionToken) {
                    Log.w(TAG, "Ongeldig sessietoken in LeaveMessage – genegeerd")
                    return
                }
                val clientName = _connectedClients.value[sessionToken] ?: "onbekend"
                Log.i(TAG, "Client $clientName heeft de telling verlaten. Reden: ${leave.reason}")
                onClientLeft?.invoke(clientName, leave.reason)
                // Verwijder de client; de TCP-verbinding wordt daarna netjes gesloten
                removeConnectedClient(sessionToken, writer, revokeToken = true)
            }
            MC_MSG_HEARTBEAT -> {
                // Pong terug sturen
                sendHeartbeat(writer)
            }
            else -> Log.w(TAG, "Onbekend bericht-type: ${envelope.type}")
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun decodeEnvelope(line: String): McEnvelope? =
        try { json.decodeFromString(McEnvelope.serializer(), line) }
        catch (e: Exception) { Log.w(TAG, "Decode envelope fout: ${e.message}"); null }

    private inline fun <reified T : Any> decodePayload(payload: String): T? =
        try { json.decodeFromString(kotlinx.serialization.serializer<T>(), payload) }
        catch (e: Exception) { Log.w(TAG, "Decode payload fout: ${e.message}"); null }

    /** Stuur een envelop met een reeds gecodeerde JSON-payload. */
    private fun sendEnvelopeRaw(writer: PrintWriter, type: String, payloadJson: String) {
        try {
            val envelope = McEnvelope(type = type, payload = payloadJson)
            synchronized(writer) {
                writer.println(json.encodeToString(McEnvelope.serializer(), envelope))
                writer.flush()
            }
        } catch (e: Exception) {
            Log.w(TAG, "sendEnvelopeRaw fout: ${e.message}", e)
        }
    }

    private fun sendAck(writer: PrintWriter, ack: AckMessage) =
        sendEnvelopeRaw(writer, MC_MSG_ACK, json.encodeToString(AckMessage.serializer(), ack))

    private fun sendHeartbeat(writer: PrintWriter) =
        sendEnvelopeRaw(writer, MC_MSG_HEARTBEAT,
            json.encodeToString(HeartbeatMessage.serializer(), HeartbeatMessage()))

    private fun sendPairingResponse(writer: PrintWriter, resp: PairingResponse) =
        sendEnvelopeRaw(writer, MC_MSG_PAIRING_RESP,
            json.encodeToString(PairingResponse.serializer(), resp))

    private fun getActiveTellingId(): String {
        val prefs = context.getSharedPreferences("vt5_prefs", Context.MODE_PRIVATE)
        return prefs.getString("pref_telling_id", "") ?: ""
    }

    /**
     * Stuur een [SessionEndMessage] naar alle verbonden clients zodat zij weten dat
     * de telling beëindigd is en zichzelf netjes kunnen afsluiten.
     *
     * @param reason Optionele reden (bijv. "telling afgerond" of "samenwerking gestopt").
     */
    fun broadcastSessionEnd(reason: String = "") {
        val msg = SessionEndMessage(
            masterName = android.os.Build.MODEL,
            reason     = reason
        )
        val payload = json.encodeToString(SessionEndMessage.serializer(), msg)
        val writers = synchronized(clientWritersLock) { clientWriters.values.toList() }
        var sent = 0
        for (w in writers) {
            try {
                sendEnvelopeRaw(w, MC_MSG_SESSION_END, payload)
                sent++
            } catch (e: Exception) {
                // Verbinding al verbroken – overige clients ontvangen de broadcast gewoon
                Log.w(TAG, "broadcastSessionEnd: kon niet schrijven naar één client: ${e.message}")
            }
        }
        Log.i(TAG, "SessionEnd broadcast naar $sent/${writers.size} client(s). Reden: $reason")
    }

    /**
     * Stuur een [MasterHandoverMessage] naar alle verbonden clients.
     *
     * Roep deze methode aan nadat alle pending records succesvol ge-upload zijn en
     * de master de telpost verlaat zónder zelf een vervolgtelling te starten.
     * Clients ontvangen het bericht en kunnen daarna de masterfunctie overnemen.
     *
     * @param eindtijdEpoch Eindtijd van de afgeronde telling (epoch-seconden als string),
     *                      zodat de nieuwe master die als begintijd kan invullen.
     * @param reason        Optionele beschrijving.
     */
    fun broadcastMasterHandover(eindtijdEpoch: String, reason: String = "") {
        val msg = MasterHandoverMessage(
            masterName    = android.os.Build.MODEL,
            eindtijdEpoch = eindtijdEpoch,
            reason        = reason
        )
        val payload = json.encodeToString(MasterHandoverMessage.serializer(), msg)
        val writers = synchronized(clientWritersLock) { clientWriters.values.toList() }
        var sent = 0
        for (w in writers) {
            try {
                sendEnvelopeRaw(w, MC_MSG_MASTER_HANDOVER, payload)
                sent++
            } catch (e: Exception) {
                Log.w(TAG, "broadcastMasterHandover: kon niet schrijven naar één client: ${e.message}")
            }
        }
        Log.i(TAG, "MasterHandover broadcast naar $sent/${writers.size} client(s). EindtijdEpoch=$eindtijdEpoch")
    }

    /**
     * Stuur een TileSyncMessage naar alle verbonden clients.
     * Wordt gebruikt om de tegelset (soorten + aantallen) te synchroniseren.
     */
    fun broadcastTileSync(tiles: List<TileSyncItem>) {
        val payload = json.encodeToString(
            TileSyncMessage.serializer(),
            TileSyncMessage(tiles = tiles, tellingId = getActiveTellingId())
        )
        val writers = synchronized(clientWritersLock) { clientWriters.values.toList() }
        var sent = 0
        for (w in writers) {
            try {
                sendEnvelopeRaw(w, MC_MSG_TILE_SYNC, payload)
                sent++
            } catch (e: Exception) {
                Log.w(TAG, "broadcastTileSync: kon niet schrijven naar één client: ${e.message}")
            }
        }
        Log.i(TAG, "TileSync broadcast naar $sent/${writers.size} client(s).")
    }

    fun getClientLogPrefix(clientId: String): String? = synchronized(clientWritersLock) {
        knownClientProfilesById[clientId]?.logPrefix
    }

    fun getClientDisplayLabel(clientId: String): String? = synchronized(clientWritersLock) {
        knownClientProfilesById[clientId]?.displayLabel
    }

    private fun addConnectedClient(identity: ClientIdentity, writer: PrintWriter) {
        synchronized(clientWritersLock) {
            val current = _connectedClients.value.toMutableMap()
            current[identity.sessionToken] = identity.displayLabel
            _connectedClients.value = current
            clientWriters[identity.sessionToken] = writer
            clientProfilesByToken[identity.sessionToken] = identity
            knownClientProfilesById[identity.clientId] = identity
        }
    }

    private fun removeConnectedClient(token: String, writer: PrintWriter? = null, revokeToken: Boolean = false) {
        synchronized(clientWritersLock) {
            val currentWriter = clientWriters[token]
            if (writer != null && currentWriter != null && currentWriter !== writer) {
                return
            }
            val current = _connectedClients.value.toMutableMap()
            current.remove(token)
            _connectedClients.value = current
            clientWriters.remove(token)
            clientProfilesByToken.remove(token)
        }
        if (revokeToken) {
            pairingManager.revokeToken(token)
        }
    }

    private fun unregisterPendingClient(token: String) {
        synchronized(clientWritersLock) {
            clientProfilesByToken.remove(token)
        }
    }

    private fun registerClientIdentity(sessionToken: String, request: PairingRequest): ClientIdentity {
        synchronized(clientWritersLock) {
            val existing = knownClientProfilesById[request.clientId]
            val ordinal = existing?.ordinal ?: clientOrdinalsById.getOrPut(request.clientId) { nextClientOrdinal++ }
            val alias = request.clientAlias.trim().ifBlank { existing?.alias.orEmpty() }
            val identity = ClientIdentity(
                clientId = request.clientId,
                deviceName = request.clientName.trim(),
                alias = alias,
                ordinal = ordinal,
                sessionToken = sessionToken
            )
            clientProfilesByToken[sessionToken] = identity
            knownClientProfilesById[request.clientId] = identity
            return identity
        }
    }

    private suspend fun sendTileSyncIfAvailable(writer: PrintWriter) {
        val tiles = onTilesSnapshot?.invoke().orEmpty()
        val payload = json.encodeToString(
            TileSyncMessage.serializer(),
            TileSyncMessage(tiles = tiles, tellingId = getActiveTellingId())
        )
        sendEnvelopeRaw(writer, MC_MSG_TILE_SYNC, payload)
        Log.i(TAG, "TileSync gestuurd naar client (${tiles.size} tegels)")
    }
}
