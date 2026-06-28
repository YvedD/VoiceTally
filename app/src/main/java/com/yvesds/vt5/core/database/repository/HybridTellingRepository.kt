package com.yvesds.vt5.core.database.repository

import android.content.Context
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.entities.TellingHeader
import com.yvesds.vt5.core.database.entities.Waarneming
import com.yvesds.vt5.core.opslag.FileLogger
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.hoofd.InstellingenScherm
import com.yvesds.vt5.net.ServerTellingDataItem
import com.yvesds.vt5.net.ServerTellingEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.Closeable

/**
 * HybridTellingRepository: Beheert de opslag-logica tussen JSON en ROOM.
 * Luistert naar de instelling 'pref_storage_mode'.
 */
class HybridTellingRepository(private val context: Context) {
    
    private val database by lazy { VoiceTallyDatabase.getDatabase(context) }
    private val tellingDao by lazy { database.tellingDao() }
    private val fileLogger by lazy { FileLogger(context) }
    private val safHelper by lazy { SaFStorageHelper(context) }

    /**
     * Slaat een waarneming op in Room als de modus ROOM of Parallel is.
     */
    suspend fun saveWaarnemingToRoom(item: ServerTellingDataItem) = withContext(Dispatchers.IO) {
        val mode = InstellingenScherm.getStorageMode(context)
        fileLogger.info("ROOM: saveWaarnemingToRoom aangeroepen (Modus: $mode, Telling: ${item.tellingid}, ID: ${item.idLocal})")
        
        if (mode == InstellingenScherm.STORAGE_MODE_ROOM || mode == InstellingenScherm.STORAGE_MODE_PARALLEL) {
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
                
                // exportDatabaseToSaf() // UITGESCHAKELD voor snelheid in Hybride modus
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
    suspend fun saveHeaderToRoom(envelope: ServerTellingEnvelope, status: String = "actief") = withContext(Dispatchers.IO) {
        val mode = InstellingenScherm.getStorageMode(context)
        if (mode == InstellingenScherm.STORAGE_MODE_ROOM || mode == InstellingenScherm.STORAGE_MODE_PARALLEL) {
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
                    status = status
                )
                tellingDao.insertHeader(header)
                fileLogger.info("ROOM: Header voor telling [${envelope.tellingid}] opgeslagen (Status: $status)")
                
                // exportDatabaseToSaf() // UITGESCHAKELD voor snelheid in Hybride modus
            } catch (e: Exception) {
                fileLogger.error("ROOM: Fout bij opslaan header [${envelope.tellingid}]: ${e.message}")
            }
        }
    }

    /**
     * Handmatige backup: Kopieert het interne SQLite databasebestand naar de zichtbare SAF map (/VT5/database/).
     */
    suspend fun forceDatabaseBackup() = withContext(Dispatchers.IO) {
        try {
            // 1. Forceer een checkpoint om data van WAL naar DB file te pushen
            try {
                // Gebruik rawQuery voor PRAGMAs die resultaten teruggeven om "unknown error (code 0 SQLITE_OK)" te vermijden
                database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
                    // Cursor consumeren/sluiten triggert de actie
                    if (cursor.moveToFirst()) {
                        val busy = cursor.getInt(0)
                        val log = cursor.getInt(1)
                        val checkpointed = cursor.getInt(2)
                        fileLogger.info("Database checkpoint resultaat: busy=$busy, log=$log, checkpointed=$checkpointed")
                    }
                }
            } catch (e: Exception) {
                fileLogger.warn("Database checkpoint mislukt of waarschuwing: ${e.message}")
            }

            // 2. Definieer bronpaden
            val internalRoot = context.getExternalFilesDir(null) ?: context.filesDir
            val dbPath = File(internalRoot, "VT5/database/voicetally.db")
            val walPath = File(internalRoot, "VT5/database/voicetally.db-wal")
            val shmPath = File(internalRoot, "VT5/database/voicetally.db-shm")

            // 3. Toegang tot doelmap op SAF
            val vt5Dir = safHelper.getVt5DirIfExists() ?: return@withContext
            val dbDirSaf = safHelper.findOrCreateDirectory(vt5Dir, "database") ?: return@withContext
            
            // 4. Kopieer alle drie de bestanden voor volledige integriteit
            if (dbPath.exists()) copyToSafWithOverwrite(dbPath, dbDirSaf, "voicetally.db")
            if (walPath.exists()) copyToSafWithOverwrite(walPath, dbDirSaf, "voicetally.db-wal")
            if (shmPath.exists()) copyToSafWithOverwrite(shmPath, dbDirSaf, "voicetally.db-shm")
            
            fileLogger.info("SAF: Database export voltooid (inclusief WAL/SHM)")

        } catch (e: Exception) {
            fileLogger.error("SAF: Fout bij exporteren database: ${e.message}")
        }
    }

    private fun copyToSafWithOverwrite(source: File, targetDir: androidx.documentfile.provider.DocumentFile, fileName: String) {
        try {
            var targetFile = targetDir.findFile(fileName)
            if (targetFile == null) {
                val mime = if (fileName.endsWith(".db")) "application/x-sqlite3" else "application/octet-stream"
                targetFile = targetDir.createFile(mime, fileName)
            }
            
            targetFile?.let { file ->
                val os = context.contentResolver.openOutputStream(file.uri, "wt")
                val isSource = source.inputStream()
                
                os?.use { output ->
                    isSource.use { input ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HybridRepo", "Fout bij kopiëren $fileName: ${e.message}")
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
