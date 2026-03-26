package com.yvesds.vt5.features.masterClient

import android.content.Context
import android.util.Log
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.features.masterClient.protocol.ObservationEvent
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * ClientEventQueue – lokale wachtrij met retry-logica voor client-side events.
 *
 * Elk [ObservationEvent] wordt in de wachtrij gezet met een unieke [clientEventId].
 * De queue houdt bij welke events nog wachten op een ACK van de master.
 * Bij verbinding-herstel worden alle nog openstaande events opnieuw aangeboden.
 *
 * Thread-safe: alle methoden mogen vanuit meerdere coroutines aangeroepen worden.
 */
class ClientEventQueue(
    context: Context
) {

    companion object {
        private const val TAG = "ClientEventQueue"
        private const val PREFS_NAME = "vt5_mc_queue"
        private const val KEY_PENDING = "pending_events"
        private const val KEY_IN_FLIGHT = "inflight_events"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = VT5App.json

    // Events die wachten op verwerking (nog niet verstuurd of nog geen ACK)
    private val pendingQueue = ConcurrentLinkedQueue<ObservationEvent>()

    // Events verstuurd maar nog niet ge-ack't: clientEventId → event
    private val inFlight = ConcurrentHashMap<String, ObservationEvent>()

    // Tijdstip waarop een event laatst als inFlight gemarkeerd werd.
    private val inFlightSinceMs = ConcurrentHashMap<String, Long>()

    init {
        restoreState()
    }

    // ─── Toevoegen ────────────────────────────────────────────────────────────

    /**
     * Voeg een nieuw event toe aan de wachtrij.
     * [clientId] en [sessionToken] worden meegegeven vanuit de [ClientConnector].
     * @return het gegenereerde [clientEventId]
     */
    fun enqueue(
        clientId: String,
        sessionToken: String,
        soortid: String,
        aantal: Int,
        aantalterug: Int = 0,
        tijdstip: Long = System.currentTimeMillis() / 1000L,
        geslacht: String = "",
        leeftijd: String = "",
        kleed: String = "",
        opmerkingen: String = "",
        recordPayload: String = ""
    ): String {
        val eventId = UUID.randomUUID().toString().replace("-", "")
        enqueueWithId(
            clientEventId = eventId,
            clientId = clientId,
            sessionToken = sessionToken,
            soortid = soortid,
            aantal = aantal,
            aantalterug = aantalterug,
            tijdstip = tijdstip,
            geslacht = geslacht,
            leeftijd = leeftijd,
            kleed = kleed,
            opmerkingen = opmerkingen,
            recordPayload = recordPayload,
            isUpdate = false
        )
        return eventId
    }

    fun enqueueWithId(
        clientEventId: String,
        clientId: String,
        sessionToken: String,
        soortid: String,
        aantal: Int,
        aantalterug: Int = 0,
        tijdstip: Long = System.currentTimeMillis() / 1000L,
        geslacht: String = "",
        leeftijd: String = "",
        kleed: String = "",
        opmerkingen: String = "",
        recordPayload: String = "",
        isUpdate: Boolean = false
    ) {
        val event = ObservationEvent(
            clientId      = clientId,
            clientEventId = clientEventId,
            sessionToken  = sessionToken,
            soortid       = soortid,
            aantal        = aantal,
            aantalterug   = aantalterug,
            tijdstip      = tijdstip,
            geslacht      = geslacht,
            leeftijd      = leeftijd,
            kleed         = kleed,
            opmerkingen   = opmerkingen,
            recordPayload = recordPayload,
            isUpdate      = isUpdate
        )
        pendingQueue.add(event)
        Log.d(TAG, "Event in wachtrij gezet: $clientEventId ($soortid ×$aantal) update=$isUpdate")
        persistState()
    }

    // ─── Lezen voor verzending ─────────────────────────────────────────────────

    /**
     * Geeft het volgende event terug dat verstuurd moet worden, of null als de wachtrij leeg is.
     * Het event wordt verplaatst naar [inFlight] totdat een ACK ontvangen wordt.
     */
    fun getNextPending(): ObservationEvent? {
        val event = pendingQueue.poll() ?: return null
        inFlight[event.clientEventId] = event
        inFlightSinceMs[event.clientEventId] = System.currentTimeMillis()
        persistState()
        return event
    }

    /** Geeft alle pending events terug (voor bulk-export). Markeert ze als inFlight. */
    fun getAllPending(): List<ObservationEvent> {
        val events = mutableListOf<ObservationEvent>()
        while (true) {
            val ev = pendingQueue.poll() ?: break
            inFlight[ev.clientEventId] = ev
            inFlightSinceMs[ev.clientEventId] = System.currentTimeMillis()
            events.add(ev)
        }
        if (events.isNotEmpty()) {
            persistState()
        }
        return events
    }

    // ─── ACK verwerking ───────────────────────────────────────────────────────

    /** Verwijder een event uit [inFlight] nadat de master een ACK heeft teruggestuurd. */
    fun acknowledge(clientEventId: String) {
        inFlight.remove(clientEventId)
        inFlightSinceMs.remove(clientEventId)
        Log.d(TAG, "ACK verwerkt: $clientEventId")
        persistState()
    }

    /**
     * Zet één specifiek inFlight-event terug in de wachtrij (bij NACK of gedeeltelijke fout).
     * @return true als een event teruggeplaatst werd.
     */
    fun requeue(clientEventId: String): Boolean {
        val event = inFlight.remove(clientEventId) ?: return false
        inFlightSinceMs.remove(clientEventId)
        pendingQueue.add(event)
        Log.w(TAG, "Event terug in wachtrij gezet na NACK: $clientEventId")
        persistState()
        return true
    }

    /**
     * Zet verlopen inFlight-events terug in de wachtrij zodat de connector ze opnieuw kan sturen.
     * @return de clientEventIds die opnieuw ingepland werden.
     */
    fun requeueExpiredInFlight(maxAgeMs: Long): List<String> {
        if (maxAgeMs <= 0L) return emptyList()

        val now = System.currentTimeMillis()
        val expiredIds = mutableListOf<String>()

        inFlight.entries.forEach { entry ->
            val eventId = entry.key
            val startedAt = inFlightSinceMs[eventId] ?: 0L
            if (now - startedAt < maxAgeMs) return@forEach

            if (inFlight.remove(eventId, entry.value)) {
                inFlightSinceMs.remove(eventId)
                pendingQueue.add(entry.value)
                expiredIds.add(eventId)
            }
        }

        if (expiredIds.isNotEmpty()) {
            Log.w(TAG, "${expiredIds.size} inFlight-event(s) verlopen; opnieuw ingepland voor retry")
            persistState()
        }

        return expiredIds
    }

    /**
     * Zet alle inFlight-events terug in de pendingQueue (bij verbindingsbreuk).
     * Zodat ze opnieuw verstuurd worden na reconnect.
     */
    fun requeueInFlight() {
        val toRequeue = inFlight.values.toList()
        inFlight.clear()
        inFlightSinceMs.clear()
        toRequeue.forEach { pendingQueue.add(it) }
        if (toRequeue.isNotEmpty()) {
            Log.d(TAG, "${toRequeue.size} inFlight-events teruggezet in wachtrij")
            persistState()
        }
    }

    fun replaceSessionToken(sessionToken: String, clientId: String) {
        if (sessionToken.isBlank()) return

        val pendingSnapshot = pendingQueue.toList().map {
            it.copy(sessionToken = sessionToken, clientId = clientId)
        }
        val inFlightSnapshot = inFlight.values.toList().map {
            it.copy(sessionToken = sessionToken, clientId = clientId)
        }

        pendingQueue.clear()
        pendingSnapshot.forEach { pendingQueue.add(it) }

        inFlight.clear()
        inFlightSinceMs.clear()
        inFlightSnapshot.forEach { inFlight[it.clientEventId] = it }
        inFlightSnapshot.forEach { inFlightSinceMs[it.clientEventId] = System.currentTimeMillis() }

        persistState()
    }

    // ─── Status ───────────────────────────────────────────────────────────────

    /** Totaal aantal events dat nog niet ge-ack't is (pending + inFlight). */
    fun totalUnacknowledged(): Int = pendingQueue.size + inFlight.size

    /** Alle pending events als snapshot (voor export-dialog). */
    fun pendingSnapshot(): List<ObservationEvent> = pendingQueue.toList()

    /** Wis alle events (bij einde sessie). */
    fun clear() {
        pendingQueue.clear()
        inFlight.clear()
        inFlightSinceMs.clear()
        persistState()
    }

    private fun restoreState() {
        try {
            val pendingRaw = prefs.getString(KEY_PENDING, null)
            val inFlightRaw = prefs.getString(KEY_IN_FLIGHT, null)

            if (!pendingRaw.isNullOrBlank()) {
                val pendingItems = json.decodeFromString(
                    ListSerializer(ObservationEvent.serializer()),
                    pendingRaw
                )
                pendingItems.forEach { pendingQueue.add(it) }
            }

            if (!inFlightRaw.isNullOrBlank()) {
                val inFlightItems = json.decodeFromString(
                    ListSerializer(ObservationEvent.serializer()),
                    inFlightRaw
                )
                // Een herstelde app weet niet meer zeker of deze events de master ooit bereikt hebben.
                // Zet ze daarom meteen terug naar pending zodat resend direct opnieuw kan starten.
                inFlightItems.forEach { pendingQueue.add(it) }
            }

            if (!inFlightRaw.isNullOrBlank()) {
                persistState()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Kon eventqueue niet herstellen: ${e.message}", e)
            pendingQueue.clear()
            inFlight.clear()
            inFlightSinceMs.clear()
            persistState()
        }
    }

    private fun persistState() {
        try {
            prefs.edit()
                .putString(
                    KEY_PENDING,
                    json.encodeToString(ListSerializer(ObservationEvent.serializer()), pendingQueue.toList())
                )
                .putString(
                    KEY_IN_FLIGHT,
                    json.encodeToString(ListSerializer(ObservationEvent.serializer()), inFlight.values.toList())
                )
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Kon eventqueue niet bewaren: ${e.message}", e)
        }
    }
}
