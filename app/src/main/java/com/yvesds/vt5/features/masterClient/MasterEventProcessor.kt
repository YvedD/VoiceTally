package com.yvesds.vt5.features.masterClient

import android.util.Log
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.features.masterClient.protocol.AckMessage
import com.yvesds.vt5.features.masterClient.protocol.ExportDataMessage
import com.yvesds.vt5.features.masterClient.protocol.ObservationEvent
import com.yvesds.vt5.features.telling.RecordsBeheer
import com.yvesds.vt5.net.ServerTellingDataItem
import kotlinx.serialization.builtins.ListSerializer

/**
 * MasterEventProcessor – verwerkt inkomende ObservationEvents van clients.
 *
 * Taken:
 *  - Deduplicatie op basis van [clientEventId]
 *  - Aanmaak van een [ServerTellingDataItem] via [RecordsBeheer.addExternalRecord]
 *  - Terugsturen van een [AckMessage] met het toegewezen idLocal
 *
 * Roep [setRecordsBeheer] aan nadat de telling gestart is.
 */
class MasterEventProcessor {

    companion object {
        private const val TAG = "MasterEventProcessor"
        private const val MAX_SEEN_IDS = 10_000
    }

    private val json = VT5App.json

    // RecordsBeheer wordt ingesteld vanuit TellingScherm na het starten van een telling
    @Volatile
    private var recordsBeheer: RecordsBeheer? = null

    // Geziene clientEventIds voor deduplicatie (FIFO-bounded set via gesynchroniseerde LinkedHashMap)
    private val seenEventIds = object : LinkedHashMap<String, Unit>(256, 0.75f, false) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Unit>): Boolean = size > MAX_SEEN_IDS
    }

    fun setRecordsBeheer(rb: RecordsBeheer) {
        recordsBeheer = rb
    }

    fun clearRecordsBeheer() {
        recordsBeheer = null
    }

    // ─── Observatie verwerken ─────────────────────────────────────────────────

    /**
     * Verwerk een ObservationEvent afkomstig van een client.
     * Deduplicatie: als [clientEventId] al eerder ontvangen is, wordt een ACK teruggestuurd
     * zonder het record opnieuw aan te maken.
     *
     * @return [AckMessage] om terug te sturen naar de client.
     */
    suspend fun processEvent(event: ObservationEvent): AckMessage {
        val key = "${event.clientId}::${event.clientEventId}"

        // Deduplicatie
        synchronized(seenEventIds) {
            if (seenEventIds.containsKey(key)) {
                Log.d(TAG, "Duplicaat event genegeerd: $key")
                return AckMessage(
                    clientEventId   = event.clientEventId,
                    assignedIdLocal = "",
                    success         = true   // idempotent ACK
                )
            }
            seenEventIds[key] = Unit
        }

        val rb = recordsBeheer
        if (rb == null) {
            Log.w(TAG, "RecordsBeheer niet beschikbaar; event genegeerd: $key")
            return AckMessage(
                clientEventId = event.clientEventId,
                success       = false,
                error         = "Geen actieve telling op master"
            )
        }

        // Voeg het record toe via RecordsBeheer
        return try {
            val result = rb.addExternalRecord(
                soortId           = event.soortid,
                amount            = event.aantal,
                aantalterug       = event.aantalterug,
                explicitTijdstip  = event.tijdstip,
                geslacht          = event.geslacht,
                leeftijd          = event.leeftijd,
                kleed             = event.kleed,
                opmerkingen       = event.opmerkingen
            )
            when (result) {
                is com.yvesds.vt5.features.telling.OperationResult.Success ->
                    AckMessage(
                        clientEventId   = event.clientEventId,
                        assignedIdLocal = result.item.idLocal,
                        success         = true
                    )
                is com.yvesds.vt5.features.telling.OperationResult.Failure ->
                    AckMessage(
                        clientEventId = event.clientEventId,
                        success       = false,
                        error         = result.reason
                    )
            }
        } catch (e: Exception) {
            Log.e(TAG, "processEvent fout: ${e.message}", e)
            AckMessage(
                clientEventId = event.clientEventId,
                success       = false,
                error         = e.message ?: "Onbekende fout"
            )
        }
    }

    // ─── Bulk-export verwerken ────────────────────────────────────────────────

    /**
     * Verwerk een bulk-export van een client (einde telling of offline herstel).
     * Records die al via event-verwerking binnenkwamen worden gededupliceerd
     * via de [seenEventIds]-set – hier niet beschikbaar, dus dubbels zijn mogelijk.
     * De aanroeper is verantwoordelijk voor deduplicatie op [idLocal] als dat gewenst is.
     */
    suspend fun processExport(export: ExportDataMessage) {
        val rb = recordsBeheer
        if (rb == null) {
            Log.w(TAG, "processExport: RecordsBeheer niet beschikbaar")
            return
        }
        Log.i(TAG, "Verwerken bulk-export van client ${export.clientId}: ${export.records.size} records")
        for (recordJson in export.records) {
            try {
                val items = json.decodeFromString(
                    ListSerializer(ServerTellingDataItem.serializer()),
                    recordJson
                )
                for (item in items) {
                    rb.addExternalRecord(
                        soortId          = item.soortid,
                        amount           = item.aantal.toIntOrNull() ?: 1,
                        aantalterug      = item.aantalterug.toIntOrNull() ?: 0,
                        explicitTijdstip = item.tijdstip.toLongOrNull(),
                        geslacht         = item.geslacht,
                        leeftijd         = item.leeftijd,
                        kleed            = item.kleed,
                        opmerkingen      = item.opmerkingen
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "processExport – kon record niet decoderen: ${e.message}")
            }
        }
    }
}
