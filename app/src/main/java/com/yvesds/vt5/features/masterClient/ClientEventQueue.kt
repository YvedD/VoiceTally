package com.yvesds.vt5.features.masterClient

import android.util.Log
import com.yvesds.vt5.features.masterClient.protocol.ObservationEvent
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
class ClientEventQueue {

    companion object {
        private const val TAG = "ClientEventQueue"
    }

    // Events die wachten op verwerking (nog niet verstuurd of nog geen ACK)
    private val pendingQueue = ConcurrentLinkedQueue<ObservationEvent>()

    // Events verstuurd maar nog niet ge-ack't: clientEventId → event
    private val inFlight = ConcurrentHashMap<String, ObservationEvent>()

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
        opmerkingen: String = ""
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
            isUpdate      = isUpdate
        )
        pendingQueue.add(event)
        Log.d(TAG, "Event in wachtrij gezet: $clientEventId ($soortid ×$aantal) update=$isUpdate")
    }

    // ─── Lezen voor verzending ─────────────────────────────────────────────────

    /**
     * Geeft het volgende event terug dat verstuurd moet worden, of null als de wachtrij leeg is.
     * Het event wordt verplaatst naar [inFlight] totdat een ACK ontvangen wordt.
     */
    fun getNextPending(): ObservationEvent? {
        val event = pendingQueue.poll() ?: return null
        inFlight[event.clientEventId] = event
        return event
    }

    /** Geeft alle pending events terug (voor bulk-export). Markeert ze als inFlight. */
    fun getAllPending(): List<ObservationEvent> {
        val events = mutableListOf<ObservationEvent>()
        while (true) {
            val ev = pendingQueue.poll() ?: break
            inFlight[ev.clientEventId] = ev
            events.add(ev)
        }
        return events
    }

    // ─── ACK verwerking ───────────────────────────────────────────────────────

    /** Verwijder een event uit [inFlight] nadat de master een ACK heeft teruggestuurd. */
    fun acknowledge(clientEventId: String) {
        inFlight.remove(clientEventId)
        Log.d(TAG, "ACK verwerkt: $clientEventId")
    }

    /**
     * Zet alle inFlight-events terug in de pendingQueue (bij verbindingsbreuk).
     * Zodat ze opnieuw verstuurd worden na reconnect.
     */
    fun requeueInFlight() {
        val toRequeue = inFlight.values.toList()
        inFlight.clear()
        toRequeue.forEach { pendingQueue.add(it) }
        if (toRequeue.isNotEmpty()) {
            Log.d(TAG, "${toRequeue.size} inFlight-events teruggezet in wachtrij")
        }
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
    }
}
