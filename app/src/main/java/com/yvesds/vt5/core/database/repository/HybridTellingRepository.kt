package com.yvesds.vt5.core.database.repository

import android.content.Context
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.entities.TellingHeader
import com.yvesds.vt5.core.database.entities.Waarneming
import com.yvesds.vt5.core.opslag.FileLogger
import com.yvesds.vt5.features.telling.TellingStatus
import com.yvesds.vt5.hoofd.InstellingenScherm
import com.yvesds.vt5.net.ServerTellingDataItem
import com.yvesds.vt5.net.ServerTellingEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * HybridTellingRepository: Beheert de opslag-logica tussen JSON en ROOM.
 * Luistert naar de instelling 'pref_storage_mode'.
 */
class HybridTellingRepository(private val context: Context) {
    
    private val database by lazy { VoiceTallyDatabase.getDatabase(context) }
    private val tellingDao by lazy { database.tellingDao() }
    private val fileLogger by lazy { FileLogger(context) }

    /**
     * Slaat een waarneming op in Room als de modus ROOM of Parallel is.
     */
    suspend fun saveWaarnemingToRoom(item: ServerTellingDataItem, force: Boolean = false) = withContext(Dispatchers.IO) {
        val mode = InstellingenScherm.getStorageMode(context)
        fileLogger.info("ROOM: saveWaarnemingToRoom aangeroepen (Modus: $mode, Telling: ${item.tellingid}, ID: ${item.idLocal})")
        
        if (force || mode == InstellingenScherm.STORAGE_MODE_ROOM || mode == InstellingenScherm.STORAGE_MODE_PARALLEL) {
            try {
                if (item.tellingid.isBlank()) {
                    fileLogger.warn("ROOM: Waarneming genegeerd - tellingid is leeg")
                    return@withContext
                }

                val entity = Waarneming(
                    idLocal = item.idLocal,
                    tellingid = item.tellingid,
                    soortid = item.soortid,
                    aantal = item.aantal,
                    richting = item.richting,
                    aantalterug = item.aantalterug,
                    richtingterug = item.richtingterug,
                    sightingdirection = item.sightingdirection,
                    lokaal = item.lokaal,
                    aantal_plus = item.aantal_plus,
                    aantalterug_plus = item.aantalterug_plus,
                    lokaal_plus = item.lokaal_plus,
                    markeren = item.markeren,
                    markerenlokaal = item.markerenlokaal,
                    geslacht = item.geslacht,
                    leeftijd = item.leeftijd,
                    kleed = item.kleed,
                    opmerkingen = item.opmerkingen,
                    trektype = item.trektype,
                    teltype = item.teltype,
                    location = item.location,
                    height = item.height,
                    tijdstip = item.tijdstip,
                    groupid = item.groupid,
                    uploadtijdstip = item.uploadtijdstip,
                    totaalaantal = item.totaalaantal
                )
                
                // Check of de header bestaat (optioneel, voor debugging)
                val header = tellingDao.getHeader(item.tellingid)
                if (header == null) {
                    fileLogger.warn("ROOM: Waarschuwing - Waarneming ingevoegd voor onbekende telling [${item.tellingid}]")
                }
                
                tellingDao.insertWaarneming(entity)
                fileLogger.info("ROOM: Waarneming [${item.idLocal}] voor telling [${item.tellingid}] succesvol opgeslagen")
            } catch (e: Exception) {
                fileLogger.error("ROOM: Fout bij opslaan waarneming [${item.idLocal}]: ${e.message}")
                android.util.Log.e("HybridRepo", "DB Insert error", e)
            }
        } else {
            fileLogger.info("ROOM: Waarneming overgeslagen wegens modus $mode")
        }
    }

    /**
     * Slaat de metadata (header) op in Room.
     * @param status Optionele status update (bijv. "geupload")
     */
    suspend fun saveHeaderToRoom(
        envelope: ServerTellingEnvelope,
        status: String = "actief",
        force: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val mode = InstellingenScherm.getStorageMode(context)
        if (force || mode == InstellingenScherm.STORAGE_MODE_ROOM || mode == InstellingenScherm.STORAGE_MODE_PARALLEL) {
            try {
                val header = TellingHeader(
                    tellingid = envelope.tellingid,
                    onlineid = envelope.onlineid,
                    externid = envelope.externid,
                    timezoneid = envelope.timezoneid,
                    bron = envelope.bron,
                    telpostid = envelope.telpostid,
                    begintijd = envelope.begintijd,
                    eindtijd = envelope.eindtijd,
                    tellers = envelope.tellers,
                    weer = envelope.weer,
                    windrichting = envelope.windrichting,
                    windkracht = envelope.windkracht,
                    temperatuur = envelope.temperatuur,
                    bewolking = envelope.bewolking,
                    bewolkinghoogte = envelope.bewolkinghoogte,
                    neerslag = envelope.neerslag,
                    duurneerslag = envelope.duurneerslag,
                    zicht = envelope.zicht,
                    tellersactief = envelope.tellersactief,
                    tellersaanwezig = envelope.tellersaanwezig,
                    typetelling = envelope.typetelling,
                    metersnet = envelope.metersnet,
                    geluid = envelope.geluid,
                    opmerkingen = envelope.opmerkingen,
                    hydro = envelope.hydro,
                    hpa = envelope.hpa,
                    equipment = envelope.equipment,
                    uuid = envelope.uuid,
                    uploadtijdstip = envelope.uploadtijdstip,
                    nrec = envelope.nrec,
                    nsoort = envelope.nsoort,
                    status = TellingStatus.normalize(status)
                )
                tellingDao.insertHeader(header)
                fileLogger.info("ROOM: Header voor telling [${envelope.tellingid}] opgeslagen (Status: ${header.status})")
            } catch (e: Exception) {
                fileLogger.error("ROOM: Fout bij opslaan header [${envelope.tellingid}]: ${e.message}")
            }
        }
    }

    /**
     * Werkt enkel de status (en optioneel onlineId) van een bestaande header bij.
     */
    suspend fun updateHeaderStatus(
        tellingId: String,
        status: String,
        onlineId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (tellingId.isBlank()) return@withContext false
        return@withContext try {
            val current = tellingDao.getHeader(tellingId) ?: return@withContext false
            val updated = current.copy(
                status = TellingStatus.normalize(status),
                onlineid = onlineId?.trim()?.takeIf { it.isNotBlank() } ?: current.onlineid
            )
            tellingDao.updateHeader(updated)
            fileLogger.info("ROOM: Status van telling [$tellingId] bijgewerkt naar [${updated.status}]")
            true
        } catch (e: Exception) {
            fileLogger.error("ROOM: Fout bij status-update [$tellingId]: ${e.message}")
            false
        }
    }

    /**
     * Replaces one telling snapshot in Room with the provided envelope+records.
     * Used for deterministic historical reopen/edit flows.
     */
    suspend fun replaceTellingSnapshotInRoom(envelope: ServerTellingEnvelope, status: String = "actief") = withContext(Dispatchers.IO) {
        if (envelope.tellingid.isBlank()) return@withContext

        try {
            tellingDao.deleteWaarnemingenVoorTellingById(envelope.tellingid)
            saveHeaderToRoom(envelope, status = status, force = true)
            envelope.data.forEach { item ->
                val normalized = item.copy(
                    tellingid = envelope.tellingid
                )
                saveWaarnemingToRoom(normalized, force = true)
            }
            fileLogger.info("ROOM: Snapshot replaced for telling [${envelope.tellingid}] with ${envelope.data.size} records")
        } catch (e: Exception) {
            fileLogger.error("ROOM: Fout bij vervangen snapshot [${envelope.tellingid}]: ${e.message}")
        }
    }

    /**
     * Haalt waarnemingen uit Room als Flow.
     */
    fun getWaarnemingenFlow(tellingId: String): Flow<List<Waarneming>> {
        return tellingDao.getWaarnemingenFlow(tellingId)
    }

    /**
     * Haalt alle waarnemingen voor een telling op als lijst.
     */
    suspend fun getWaarnemingenList(tellingId: String): List<Waarneming> = withContext(Dispatchers.IO) {
        return@withContext tellingDao.getWaarnemingenList(tellingId)
    }

    /**
     * Verwijdert een waarneming uit Room.
     */
    suspend fun deleteWaarnemingFromRoom(item: ServerTellingDataItem) = withContext(Dispatchers.IO) {
        val mode = InstellingenScherm.getStorageMode(context)
        if (mode == InstellingenScherm.STORAGE_MODE_ROOM || mode == InstellingenScherm.STORAGE_MODE_PARALLEL) {
            try {
                // We maken een dummy entity met alleen het ID voor verwijdering
                val entity = Waarneming(idLocal = item.idLocal, tellingid = item.tellingid)
                tellingDao.deleteWaarneming(entity)
                fileLogger.info("ROOM: Waarneming [${item.idLocal}] verwijderd")
            } catch (e: Exception) {
                fileLogger.error("ROOM: Fout bij verwijderen waarneming [${item.idLocal}]: ${e.message}")
            }
        }
    }
}
