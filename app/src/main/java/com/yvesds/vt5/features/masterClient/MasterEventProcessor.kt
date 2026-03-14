package com.yvesds.vt5.features.masterClient

import android.util.Log
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.features.masterClient.protocol.AckMessage
import com.yvesds.vt5.features.masterClient.protocol.ExportDataMessage
import com.yvesds.vt5.features.masterClient.protocol.ObservationEvent
import com.yvesds.vt5.net.ServerTellingDataItem
import kotlinx.serialization.builtins.ListSerializer

/**
 * MasterEventProcessor – verwerkt inkomende ObservationEvents van clients.
 *
 * Taken:
 *  - Deduplicatie op basis van [clientEventId]
 *  - Doorsturen van waarnemingen via [onObservationReceived]-callback naar de lopende telling
 *
 * De callback wordt ingesteld door [com.yvesds.vt5.features.telling.TellingScherm] en integreert
 * client-waarnemingen rechtstreeks in de live telling (dezelfde codepath als spraak-waarnemingen).
 * Zo verschijnen client-waarnemingen live in de tegels en het logscherm van de master.
 *
 * [onExportReceived] wordt aangeroepen bij een bulk-export aan het einde van een telling.
 */
class MasterEventProcessor {

    companion object {
        private const val TAG = "MasterEventProcessor"
        private const val MAX_SEEN_IDS = 10_000
    }

    private val json = VT5App.json

    // Geziene clientEventIds voor deduplicatie (FIFO-bounded set).
    // Alle toegangen verlopen via synchronized(seenEventIds), zodat ook de removeEldestEntry-
    // aanroep (die plaatsvindt tijdens put() binnen het gesynchroniseerde blok) thread-safe is.
    private val seenEventIds = object : LinkedHashMap<String, Unit>(256, 0.75f, false) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Unit>): Boolean = size > MAX_SEEN_IDS
    }

    /**
     * Callback ingesteld door TellingScherm.
     * Wordt aangeroepen voor elke unieke, gevalideerde client-waarneming.
     * Roept intern [speciesManager.updateSoortCountInternal] + [speciesManager.collectFinalAsRecord] aan.
     */
    var onObservationReceived: (suspend (
        clientId: String,
        clientEventId: String,
        isUpdate: Boolean,
        soortId: String,
        amount: Int,
        aantalterug: Int,
        tijdstip: Long,
        geslacht: String,
        leeftijd: String,
        kleed: String,
        opmerkingen: String,
        recordPayload: String
    ) -> Unit)? = null

    /**
     * Callback voor bulk-export (einde telling / offline herstel).
     * Wordt aangeroepen met de volledige lijst gedecodeerde records.
     */
    var onExportReceived: (suspend (List<ServerTellingDataItem>) -> Unit)? = null

    // ─── Observatie verwerken ─────────────────────────────────────────────────

    /**
     * Verwerk een ObservationEvent afkomstig van een client.
     * Deduplicatie: als [clientEventId] al eerder ontvangen is, wordt een ACK teruggestuurd
     * zonder de callback opnieuw aan te roepen.
     *
     * @return [AckMessage] om terug te sturen naar de client.
     */
    suspend fun processEvent(event: ObservationEvent): AckMessage {
        val key = "${event.clientId}::${event.clientEventId}"

        if (!event.isUpdate) {
            synchronized(seenEventIds) {
                if (seenEventIds.containsKey(key)) {
                    Log.d(TAG, "Duplicaat event genegeerd: $key")
                    return AckMessage(clientEventId = event.clientEventId, success = true)
                }
                seenEventIds[key] = Unit
            }
        }

        val cb = onObservationReceived
        if (cb == null) {
            Log.w(TAG, "Geen onObservationReceived-callback; event genegeerd: $key")
            return AckMessage(
                clientEventId = event.clientEventId,
                success       = false,
                error         = "Geen actieve telling op master"
            )
        }

        return try {
            cb(
                event.clientId,
                event.clientEventId,
                event.isUpdate,
                event.soortid,
                event.aantal,
                event.aantalterug,
                event.tijdstip,
                event.geslacht,
                event.leeftijd,
                event.kleed,
                event.opmerkingen,
                event.recordPayload
            )
            AckMessage(clientEventId = event.clientEventId, success = true)
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
     * Deduplicatie is niet volledig gegarandeerd bij bulk-export; de aanroeper kan
     * op [ServerTellingDataItem.idLocal] dedupliceren indien gewenst.
     */
    suspend fun processExport(export: ExportDataMessage) {
        val cb = onExportReceived
        Log.i(TAG, "Bulk-export van client ${export.clientId}: ${export.records.size} records")
        val allItems = mutableListOf<ServerTellingDataItem>()
        for (recordJson in export.records) {
            try {
                val items = json.decodeFromString(
                    ListSerializer(ServerTellingDataItem.serializer()),
                    recordJson
                )
                allItems.addAll(items)
            } catch (e: Exception) {
                Log.w(TAG, "processExport – kon record niet decoderen: ${e.message}")
            }
        }
        if (allItems.isNotEmpty() && cb != null) {
            cb(allItems)
        } else if (cb == null) {
            Log.w(TAG, "processExport – geen onExportReceived-callback; items genegeerd")
        }
    }
}
