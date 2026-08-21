package com.yvesds.vt5.core.database.batch

import android.content.Context
import android.net.Uri
import android.util.Log
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.entities.TellingHeader
import com.yvesds.vt5.core.database.entities.Waarneming
import com.yvesds.vt5.core.opslag.AppDataStore
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.dhatim.fastexcel.reader.Row
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.stream.Stream

/**
 * ExcelImportManager - Verwerkt batch-imports via FastExcel.
 * Geoptimaliseerd voor snelheid: bulk ID reservering en slimme duplicaat-checks.
 */
class ExcelImportManager(private val context: Context) {
    private val TAG = "ExcelImportManager"
    private val db = VoiceTallyDatabase.getDatabase(context)

    suspend fun importPair(
        headerUri: Uri,
        dataUri: Uri,
        onProgress: suspend (String, Int, Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Scan Data bestand voor statistieken (nrec/nsoort)
            onProgress("Data scannen voor statistieken...", 0, 0)
            val statsMap = mutableMapOf<String, SessionStats>()
            context.contentResolver.openInputStream(dataUri)?.use { dIn ->
                val workbook = ReadableWorkbook(dIn)
                try {
                    val sheet = workbook.getSheets().findFirst().orElse(null) ?: return@use
                    val colMap = mutableMapOf<String, Int>()
                    var rowIdx = 0
                    val rowStream: Stream<Row> = sheet.openStream()
                    rowStream.use { rows ->
                        val it = rows.iterator()
                        while (it.hasNext()) {
                            val row = it.next()
                            if (rowIdx == 0) {
                                for (i in 0 until row.cellCount) {
                                    val name = row.getCellText(i).trim().lowercase()
                                    if (name.isNotEmpty()) colMap[name] = i
                                }
                            } else {
                                val countId = row.getCellText(colMap["countid"] ?: -1).split(".")[0]
                                val speciesId = row.getCellText(colMap["speciesid"] ?: -1).split(".")[0]
                                if (countId.isNotEmpty() && countId != "null") {
                                    val stats = statsMap.getOrPut(countId) { SessionStats() }
                                    stats.nrec++
                                    if (speciesId.isNotEmpty()) stats.speciesIds.add(speciesId)
                                }
                            }
                            rowIdx++
                        }
                    }
                } finally { workbook.close() }
            }

            // 2. Verwerk Headers
            onProgress("Sessies voorbereiden...", 0, 0)
            val headersMap = mutableMapOf<String, HeaderInfo>()
            val headersToInsert = mutableListOf<TellingHeader>()
            
            // Haal alle bestaande onlineIds in één keer op om per-row DB hits te voorkomen
            val existingOnlineIds = db.tellingDao().getAllHeaders().mapNotNull { it.onlineid.ifEmpty { null } }.toSet()
            
            // Verzamel sessies uit Excel
            val sessionsInExcel = mutableListOf<Row>()
            val headerColMap = mutableMapOf<String, Int>()
            context.contentResolver.openInputStream(headerUri)?.use { hIn ->
                val workbook = ReadableWorkbook(hIn)
                try {
                    val sheet = workbook.getSheets().findFirst().orElse(null) ?: return@use
                    var rowIdx = 0
                    sheet.openStream().use { rows ->
                        rows.forEach { row ->
                            if (rowIdx == 0) {
                                for (i in 0 until row.cellCount) {
                                    val name = row.getCellText(i).trim().lowercase()
                                    if (name.isNotEmpty()) headerColMap[name] = i
                                }
                            } else {
                                val id = row.getCellText(headerColMap["id"] ?: -1).split(".")[0]
                                if (id.isNotEmpty() && id != "null") {
                                    sessionsInExcel.add(row)
                                }
                            }
                            rowIdx++
                        }
                    }
                } finally { workbook.close() }
            }

            // Reserveer IDs in bulk voor de nieuwe sessies
            val newSessionsCount = sessionsInExcel.count { row ->
                val onlineId = row.getCellText(headerColMap["id"] ?: -1).split(".")[0]
                !existingOnlineIds.contains(onlineId)
            }
            
            val nextIdIterator = if (newSessionsCount > 0) {
                AppDataStore.reserveTellingIds(context, newSessionsCount).iterator()
            } else null

            sessionsInExcel.forEach { row ->
                val onlineId = row.getCellText(headerColMap["id"] ?: -1).split(".")[0]
                val startTimeStr = row.getCellText(headerColMap["start"] ?: -1).let { if (it.contains(" ")) it.split(" ")[1] else "00:00:00" }
                
                if (existingOnlineIds.contains(onlineId)) {
                    // Reeds aanwezig: haal bestaande tellingid op
                    val existing = db.tellingDao().getHeaderByOnlineId(onlineId)
                    if (existing != null) {
                        headersMap[onlineId] = HeaderInfo(existing.tellingid, startTimeStr, true)
                    }
                } else {
                    // Nieuwe sessie: gebruik gereserveerd ID
                    val tellingId = nextIdIterator?.next()?.toString() ?: UUID.randomUUID().toString()
                    val stats = statsMap[onlineId] ?: SessionStats()
                    val header = TellingHeader(
                        tellingid = tellingId,
                        onlineid = onlineId,
                        telpostid = row.getCellText(headerColMap["siteid"] ?: -1).split(".")[0],
                        begintijd = parseTrektellenDate(row, headerColMap, "start"),
                        eindtijd = parseTrektellenDate(row, headerColMap, "stop"),
                        tellers = row.getCellText(headerColMap["observers"] ?: -1),
                        windrichting = row.getCellText(headerColMap["winddirection"] ?: -1).lowercase(),
                        windkracht = row.getCellText(headerColMap["windspeed_bfr"] ?: -1).split(".")[0],
                        temperatuur = row.getCellText(headerColMap["temperature"] ?: -1).split(".")[0],
                        bewolking = row.getCellText(headerColMap["cloudcover"] ?: -1).split(".")[0],
                        zicht = row.getCellText(headerColMap["visibility"] ?: -1).split(".")[0],
                        neerslag = row.getCellText(headerColMap["precipitation"] ?: -1),
                        hpa = (row.getCellText(headerColMap["hpa"] ?: -1).ifEmpty { row.getCellText(headerColMap["pressure"] ?: -1) }).split(".")[0],
                        opmerkingen = row.getCellText(headerColMap["remarks"] ?: -1),
                        nrec = stats.nrec.toString(),
                        nsoort = stats.speciesIds.size.toString(),
                        status = "gearchiveerd"
                    )
                    headersToInsert.add(header)
                    headersMap[onlineId] = HeaderInfo(tellingId, startTimeStr, false)
                }
            }

            if (headersToInsert.isNotEmpty()) {
                onProgress("Sessies opslaan...", 0, 0)
                db.tellingDao().insertHeaders(headersToInsert)
                
                // Update tel-inspanning teller in DataStore
                headersToInsert.forEach { h ->
                    val start = h.begintijd.toLongOrNull() ?: 0L
                    val end = h.eindtijd.toLongOrNull() ?: 0L
                    com.yvesds.vt5.core.opslag.EffortManager.addSessionEffort(context, h.telpostid, start, end)
                }
            }

            // 3. Waarnemingen
            val waarnemingenToInsert = mutableListOf<Waarneming>()
            context.contentResolver.openInputStream(dataUri)?.use { dIn ->
                val workbook = ReadableWorkbook(dIn)
                try {
                    val sheet = workbook.getSheets().findFirst().orElse(null) ?: return@use
                    val colMap = mutableMapOf<String, Int>()
                    var rowIdx = 0
                    val rowStream: Stream<Row> = sheet.openStream()
                    rowStream.use { rows ->
                        val it = rows.iterator()
                        while (it.hasNext()) {
                            val row = it.next()
                            if (rowIdx == 0) {
                                for (i in 0 until row.cellCount) {
                                    val name = row.getCellText(i).trim().lowercase()
                                    if (name.isNotEmpty()) colMap[name] = i
                                }
                            } else {
                                val dataId = row.getCellText(colMap["dataid"] ?: -1).split(".")[0]
                                val countId = row.getCellText(colMap["countid"] ?: -1).split(".")[0]
                                
                                if (dataId.isNotEmpty() && countId.isNotEmpty() && dataId != "null") {
                                    val headerInfo = headersMap[countId]
                                    if (headerInfo != null) {
                                        // SNELHEID-TRUC: Alleen controleren op dubbele waarnemingen als de sessie al bestond
                                        val isDuplicate = if (headerInfo.wasAlreadyInDb) {
                                            db.tellingDao().getWaarnemingenByOnlineId(dataId).isNotEmpty()
                                        } else false

                                        if (!isDuplicate) {
                                            val waarneming = Waarneming(
                                                idLocal = UUID.randomUUID().toString(),
                                                tellingid = headerInfo.tellingId,
                                                onlineid = dataId,
                                                soortid = row.getCellText(colMap["speciesid"] ?: -1).split(".")[0],
                                                aantal = row.getCellText(colMap["direction1"] ?: -1).split(".")[0].ifEmpty { "0" },
                                                richting = row.getCellText(colMap["direction1"] ?: -1),
                                                aantalterug = row.getCellText(colMap["direction2"] ?: -1).split(".")[0].ifEmpty { "0" },
                                                tijdstip = parseWaarnemingFullDate(row, colMap, headerInfo.startTimeFallback),
                                                opmerkingen = row.getCellText(colMap["remark"] ?: -1)
                                            )
                                            waarnemingenToInsert.add(waarneming)
                                            
                                            if (waarnemingenToInsert.size >= 1000) {
                                                onProgress("Waarnemingen opslaan...", rowIdx, 0)
                                                db.tellingDao().insertWaarnemingen(waarnemingenToInsert)
                                                waarnemingenToInsert.clear()
                                            }
                                        }
                                    }
                                }
                            }
                            rowIdx++
                        }
                    }
                } finally { workbook.close() }
            }
            if (waarnemingenToInsert.isNotEmpty()) {
                onProgress("Laatste waarnemingen opslaan...", 0, 0)
                db.tellingDao().insertWaarnemingen(waarnemingenToInsert)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Import fout: ${e.message}")
            false
        }
    }

    private fun parseTrektellenDate(row: Row, colMap: Map<String, Int>, fullColName: String): String {
        try {
            val d = row.getCellText(colMap["day"] ?: -1).split(".")[0].toIntOrNull()
            val m = row.getCellText(colMap["month"] ?: -1).split(".")[0].toIntOrNull()
            val y = row.getCellText(colMap["year"] ?: -1).split(".")[0].toIntOrNull()
            if (d != null && m != null && y != null) {
                val timeStr = row.getCellText(colMap[fullColName] ?: -1).let { if (it.contains(" ")) it.split(" ")[1] else "00:00:00" }
                val tParts = timeStr.split(":")
                val h = tParts[0].toIntOrNull() ?: 0; val min = if (tParts.size > 1) tParts[1].toIntOrNull() ?: 0 else 0
                return LocalDateTime.of(y, m, d, h, min, 0).atZone(ZoneId.systemDefault()).toEpochSecond().toString()
            }
        } catch (_: Exception) {}
        return "0"
    }

    private fun parseWaarnemingFullDate(row: Row, colMap: Map<String, Int>, startTimeFallback: String): String {
        try {
            val dateStr = row.getCellText(colMap["date"] ?: 1) 
            var timeStr = row.getCellText(colMap["timestamp"] ?: -1).trim()
            if (timeStr.isEmpty() || timeStr == "null") {
                timeStr = startTimeFallback
            }

            val dParts = if (dateStr.contains("-")) dateStr.split("-") else if (dateStr.contains("/")) dateStr.split("/") else emptyList()
            if (dParts.size < 3) return "0"

            val y: Int
            val m: Int
            val d: Int
            if (dParts[0].length == 4) { // yyyy-mm-dd
                y = dParts[0].toInt(); m = dParts[1].toInt(); d = dParts[2].toInt()
            } else if (dParts[2].length == 4) { // dd-mm-yyyy
                d = dParts[0].toInt(); m = dParts[1].toInt(); y = dParts[2].toInt()
            } else {
                return "0"
            }

            val tParts = timeStr.split(":")
            val hour = tParts.getOrNull(0)?.toIntOrNull() ?: 0
            val min = tParts.getOrNull(1)?.toIntOrNull() ?: 0
            val sec = tParts.getOrNull(2)?.toIntOrNull() ?: 0

            return LocalDateTime.of(y, m, d, hour, min, sec).atZone(ZoneId.systemDefault()).toEpochSecond().toString()
        } catch (e: Exception) {
            return "0"
        }
    }

    private class SessionStats {
        var nrec: Int = 0
        val speciesIds = mutableSetOf<String>()
    }

    private data class HeaderInfo(val tellingId: String, val startTimeFallback: String, val wasAlreadyInDb: Boolean)
}
