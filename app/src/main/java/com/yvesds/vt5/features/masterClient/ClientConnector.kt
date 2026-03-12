package com.yvesds.vt5.features.masterClient

import android.content.Context
import android.util.Log
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.features.masterClient.protocol.AckMessage
import com.yvesds.vt5.features.masterClient.protocol.HeartbeatMessage
import com.yvesds.vt5.features.masterClient.protocol.LeaveMessage
import com.yvesds.vt5.features.masterClient.protocol.MasterHandoverMessage
import com.yvesds.vt5.features.masterClient.protocol.MC_MSG_ACK
import com.yvesds.vt5.features.masterClient.protocol.MC_MSG_HEARTBEAT
import com.yvesds.vt5.features.masterClient.protocol.MC_MSG_LEAVE
import com.yvesds.vt5.features.masterClient.protocol.MC_MSG_MASTER_HANDOVER
import com.yvesds.vt5.features.masterClient.protocol.MC_MSG_OBSERVATION
import com.yvesds.vt5.features.masterClient.protocol.MC_MSG_PAIRING_REQ
import com.yvesds.vt5.features.masterClient.protocol.MC_MSG_PAIRING_RESP
import com.yvesds.vt5.features.masterClient.protocol.MC_MSG_SESSION_END
import com.yvesds.vt5.features.masterClient.protocol.McEnvelope
import com.yvesds.vt5.features.masterClient.protocol.ObservationEvent
import com.yvesds.vt5.features.masterClient.protocol.PairingRequest
import com.yvesds.vt5.features.masterClient.protocol.PairingResponse
import com.yvesds.vt5.features.masterClient.protocol.SessionEndMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

/**
 * ClientConnector – verbindt als client met de MasterServer.
 *
 * Verbindingsflow:
 *  1. Maak verbinding met master op [ip]:[port].
 *  2. Stuur PairingRequest met PIN.
 *  3. Ontvang PairingResponse (sessietoken of fout).
 *  4. Stuur verzamelde ObservationEvents; wacht op ACK per event.
 *  5. Bewaar tijdelijk niet-ge-ack'te events in [ClientEventQueue].
 *
 * Bij verbrekingen herverbindt de connector automatisch (met exponentieel backoff).
 */
class ClientConnector(
    private val context: Context,
    private val eventQueue: ClientEventQueue
) {
    companion object {
        private const val TAG              = "ClientConnector"
        private const val RECONNECT_BASE_MS = 3_000L
        private const val RECONNECT_MAX_MS  = 60_000L
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
    }

    private val json = VT5App.json
    private val prefs = MasterClientPrefs

    enum class State { DISCONNECTED, CONNECTING, PAIRED, ERROR }

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state

    private val _lastError = MutableStateFlow("")
    val lastError: StateFlow<String> = _lastError

    /**
     * Optionele callback: aangeroepen op de IO-thread wanneer de master een
     * [SessionEndMessage] stuurt. De aanroeper moet UI-updates naar de Main-thread
     * dispatchen.
     */
    var onSessionEnded: ((reason: String) -> Unit)? = null

    /**
     * Optionele callback: aangeroepen op de IO-thread wanneer de master een
     * [MasterHandoverMessage] stuurt. Dit betekent dat de master alle pending records
     * heeft ge-upload en de telpost verlaat zónder een vervolgtelling te starten.
     * Eén van de clients kan nu de masterfunctie overnemen.
     *
     * Parameters: eindtijdEpoch (te gebruiken als begintijd vervolgtelling), masterName, reason.
     * De aanroeper moet UI-updates naar de Main-thread dispatchen.
     */
    var onMasterHandover: ((eindtijdEpoch: String, masterName: String, reason: String) -> Unit)? = null

    private var connectorScope: CoroutineScope? = null

    @Volatile private var running = false
    @Volatile private var socket: Socket? = null
    @Volatile private var writer: PrintWriter? = null
    /** Sessietoken ontvangen na succesvolle pairing. */
    @Volatile private var currentSessionToken: String = ""

    // ─── Start / stop ─────────────────────────────────────────────────────────

    /** Start de verbindingspoging (auto-reconnect). */
    fun start() {
        if (running) return
        running = true
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        connectorScope = scope
        scope.launch { connectionLoop() }
    }

    /**
     * Stuur een [LeaveMessage] naar de master en stop daarna de verbinding.
     * Mag aangeroepen worden vanuit elke thread; de stop() is thread-safe.
     *
     * @param reason Optionele reden (bijv. "gebruiker gestopt met tellen").
     */
    fun leaveSession(reason: String = "") {
        // Maak lokale kopieën zodat de referenties stabiel zijn gedurende de rest van de methode.
        // @Volatile garandeert zichtbaarheid maar geen atomiciteit; door de waarden onmiddellijk
        // lokaal vast te leggen werken we met een stabiele snapshot van het moment van de aanroep.
        // Een TOCTOU-race (bijv. writer wordt null ná de kopie) is theoretisch mogelijk maar heeft
        // in de praktijk geen gevolg: de PrintWriter-write gooit dan een IOException die we opvangen.
        val w = writer
        val token = currentSessionToken
        if (w != null && token.isNotBlank()) {
            try {
                val msg = LeaveMessage(
                    clientId     = prefs.getClientId(context),
                    sessionToken = token,
                    reason       = reason
                )
                val payload  = json.encodeToString(LeaveMessage.serializer(), msg)
                val envelope = McEnvelope(type = MC_MSG_LEAVE, payload = payload)
                w.println(json.encodeToString(McEnvelope.serializer(), envelope))
                Log.i(TAG, "LeaveMessage verstuurd naar master. Reden: $reason")
            } catch (e: Exception) {
                Log.w(TAG, "Kon LeaveMessage niet versturen: ${e.message}")
            }
        }
        stop()
    }

    /** Stop de verbinding en herstelpoging. */
    fun stop() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        connectorScope?.cancel()
        connectorScope = null
        socket = null
        writer = null
        _state.value = State.DISCONNECTED
    }

    /**
     * Stuur alle events in de wachtrij onmiddellijk (voor bulk-export bij einde telling).
     * Roep aan nadat een verbinding al tot stand is gebracht.
     */
    suspend fun flushQueue() {
        val w = writer ?: return
        val items = eventQueue.getAllPending()
        for (item in items) {
            if (!running) break
            sendObservation(w, item)
        }
    }

    // ─── Verbindingslus ───────────────────────────────────────────────────────

    private suspend fun connectionLoop() {
        var backoffMs = RECONNECT_BASE_MS
        while (running) {
            try {
                val ip   = prefs.getMasterIp(context)
                val port = prefs.getMasterPort(context)
                val pin  = requestPin()          // UI-laag moet PIN beschikbaar stellen

                if (ip.isBlank() || pin.isBlank()) {
                    Log.w(TAG, "Geen master-IP of PIN beschikbaar; wachten…")
                    delay(RECONNECT_BASE_MS)
                    continue
                }

                _state.value = State.CONNECTING
                Log.i(TAG, "Verbinding poging naar $ip:$port")

                val s = Socket(ip, port)
                socket = s
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val w      = PrintWriter(s.getOutputStream(), true, Charsets.UTF_8)
                writer = w

                // Pairing
                val clientId   = prefs.getClientId(context)
                val clientName = android.os.Build.MODEL
                sendPairingRequest(w, PairingRequest(pin, clientId, clientName))

                val respLine = reader.readLine() ?: throw Exception("Verbinding verbroken tijdens pairing")
                val respEnv  = decodeEnvelope(respLine)
                    ?: throw Exception("Ongeldig antwoord van master")
                if (respEnv.type != MC_MSG_PAIRING_RESP) throw Exception("Onverwacht bericht: ${respEnv.type}")

                val resp = decodePayload<PairingResponse>(respEnv.payload)
                    ?: throw Exception("Kon PairingResponse niet decoderen")

                if (!resp.accepted) {
                    _state.value = State.ERROR
                    _lastError.value = resp.error.ifBlank { "Pairing geweigerd" }
                    Log.w(TAG, "Pairing geweigerd: ${resp.error}")
                    delay(RECONNECT_MAX_MS)
                    continue
                }

                prefs.setSessionToken(context, resp.sessionToken)
                currentSessionToken = resp.sessionToken
                _state.value = State.PAIRED
                _lastError.value = ""
                backoffMs = RECONNECT_BASE_MS
                Log.i(TAG, "Verbonden met master: ${resp.masterName}")

                // Event-lus + heartbeat
                sessionLoop(reader, w, resp.sessionToken)

            } catch (e: Exception) {
                if (!running) break
                Log.w(TAG, "Verbindingsfout: ${e.message}")
                _state.value = State.ERROR
                _lastError.value = e.message ?: "Onbekende fout"
                try { socket?.close() } catch (_: Exception) {}
                socket = null
                writer = null
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(RECONNECT_MAX_MS)
            }
        }
        _state.value = State.DISCONNECTED
    }

    private suspend fun sessionLoop(
        reader: BufferedReader,
        writer: PrintWriter,
        sessionToken: String
    ) {
        val scope = connectorScope ?: return

        // Heartbeat job
        val heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                try {
                    sendHeartbeat(writer)
                } catch (_: Exception) {}
            }
        }

        // Queue-flush job: stuur in afwachting staande events
        val flushJob = scope.launch {
            while (isActive) {
                val pending = eventQueue.getNextPending()
                if (pending != null) {
                    sendObservation(writer, pending)
                } else {
                    delay(500L)
                }
            }
        }

        try {
            while (running) {
                val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                if (line.isBlank()) continue
                val env = decodeEnvelope(line) ?: continue
                when (env.type) {
                    MC_MSG_ACK -> {
                        val ack = decodePayload<AckMessage>(env.payload) ?: continue
                        if (ack.success) {
                            eventQueue.acknowledge(ack.clientEventId)
                            Log.d(TAG, "ACK ontvangen voor event ${ack.clientEventId}")
                        } else {
                            Log.w(TAG, "NACK voor event ${ack.clientEventId}: ${ack.error}")
                        }
                    }
                    MC_MSG_MASTER_HANDOVER -> {
                        val msg = decodePayload<MasterHandoverMessage>(env.payload)
                        val eindtijdEpoch = msg?.eindtijdEpoch ?: ""
                        val masterName    = msg?.masterName    ?: ""
                        val reason        = msg?.reason        ?: ""
                        Log.i(TAG, "MasterHandover ontvangen van master $masterName. EindtijdEpoch=$eindtijdEpoch")
                        onMasterHandover?.invoke(eindtijdEpoch, masterName, reason)
                        // Stop reconnect: de master-sessie is definitief beëindigd
                        running = false
                        break
                    }
                    MC_MSG_SESSION_END -> {
                        val msg = decodePayload<SessionEndMessage>(env.payload)
                        val reason = msg?.reason ?: ""
                        Log.i(TAG, "SessionEnd ontvangen van master. Reden: $reason")
                        onSessionEnded?.invoke(reason)
                        // Stop reconnect: de telling is beëindigd door de master
                        running = false
                        break
                    }
                    MC_MSG_HEARTBEAT -> { /* pong ontvangen, verbinding OK */ }
                    else -> Log.w(TAG, "Onbekend bericht-type ontvangen: ${env.type}")
                }
            }
        } finally {
            heartbeatJob.cancel()
            flushJob.cancel()
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun sendObservation(writer: PrintWriter, event: ObservationEvent) {
        try {
            val payload  = json.encodeToString(ObservationEvent.serializer(), event)
            val envelope = McEnvelope(type = MC_MSG_OBSERVATION, payload = payload)
            writer.println(json.encodeToString(McEnvelope.serializer(), envelope))
        } catch (e: Exception) {
            Log.w(TAG, "Kon event niet versturen: ${e.message}")
        }
    }

    private fun sendHeartbeat(writer: PrintWriter) {
        try {
            val payload  = json.encodeToString(HeartbeatMessage.serializer(), HeartbeatMessage())
            val envelope = McEnvelope(type = MC_MSG_HEARTBEAT, payload = payload)
            writer.println(json.encodeToString(McEnvelope.serializer(), envelope))
        } catch (e: Exception) {
            Log.w(TAG, "sendHeartbeat fout: ${e.message}")
        }
    }

    private fun sendPairingRequest(writer: PrintWriter, req: PairingRequest) {
        try {
            val payload  = json.encodeToString(PairingRequest.serializer(), req)
            val envelope = McEnvelope(type = MC_MSG_PAIRING_REQ, payload = payload)
            writer.println(json.encodeToString(McEnvelope.serializer(), envelope))
        } catch (e: Exception) {
            Log.w(TAG, "sendPairingRequest fout: ${e.message}")
        }
    }

    private fun decodeEnvelope(line: String): McEnvelope? =
        try { json.decodeFromString(McEnvelope.serializer(), line) }
        catch (e: Exception) { Log.w(TAG, "Decode envelope fout: ${e.message}"); null }

    private inline fun <reified T : Any> decodePayload(payload: String): T? =
        try { json.decodeFromString(kotlinx.serialization.serializer<T>(), payload) }
        catch (e: Exception) { Log.w(TAG, "Decode payload fout: ${e.message}"); null }

    /**
     * Haal de PIN op die door de UI is ingesteld via [setPendingPin].
     * Geeft een lege string terug als er nog geen PIN ingesteld is
     * → de verbindingslus wacht dan totdat de gebruiker een PIN invoert.
     */
    private fun requestPin(): String = pendingPin ?: ""

    // ─── PIN-invoer (vanuit UI) ───────────────────────────────────────────────

    @Volatile
    private var pendingPin: String? = null

    /** Stel de PIN in die werd ingevoerd door de gebruiker. */
    fun setPendingPin(pin: String) {
        pendingPin = pin
    }

    /** Wis de PIN na gebruik. */
    fun clearPendingPin() {
        pendingPin = null
    }
}
