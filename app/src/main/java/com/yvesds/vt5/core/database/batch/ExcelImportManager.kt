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
import java.io.InputStream
import java.time.LocalDateTime
import java.util.stream.Stream

/**
 * ExcelImportManager - Verwerkt batch-imports via FastExcel.
 * Berekent nrec/nsoort statistieken en gebruikt numerieke ID's.
 */
class ExcelImportManager(private val context: Context) {
    private val TAG = "ExcelImportManager"
    private val db = VoiceTallyDatabase.getDatabase(context)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    suspend fun importPair(
        headerUri: Uri,
        dataUri: Uri,
        onProgress: suspend (String, Int, Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Scan Data bestand voor statistieken
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

            // 2. Verwerk Headers met statistieken en numeriek ID
            val headersMap = mutableMapOf<String, HeaderInfo>()
            val headersToInsert = mutableListOf<TellingHeader>()
            
            context.contentResolver.openInputStream(headerUri)?.use { hIn ->
                val workbook = ReadableWorkbook(hIn)
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
                                val onlineId = row.getCellText(colMap["id"] ?: -1).split(".")[0]
                                if (onlineId.isNotEmpty() && onlineId != "null") {
                                    val existing = db.tellingDao().getHeaderByOnlineId(onlineId)
                                    val startTimeStr = row.getCellText(colMap["start"] ?: -1).let { if (it.contains(" ")) it.split(" ")[1] else "00:00:00" }
                                    
                                    if (existing != null) {
                                        headersMap[onlineId] = HeaderInfo(existing.tellingid, startTimeStr)
                                    } else {
                                        val stats = statsMap[onlineId] ?: SessionStats()
                                        val header = TellingHeader(
                                            tellingid = AppDataStore.nextTellingId(context),
                                            onlineid = onlineId,
                                            telpostid = row.getCellText(colMap["siteid"] ?: -1).split(".")[0],
                                            begintijd = parseTrektellenDate(row, colMap, "start"),
                                            eindtijd = parseTrektellenDate(row, colMap, "stop"),
                                            tellers = row.getCellText(colMap["observers"] ?: -1),
                                            windrichting = row.getCellText(colMap["winddirection"] ?: -1).lowercase(),
                                            windkracht = row.getCellText(colMap["windspeed_bfr"] ?: -1).split(".")[0],
                                            temperatuur = row.getCellText(colMap["temperature"] ?: -1).split(".")[0],
                                            bewolking = row.getCellText(colMap["cloudcover"] ?: -1).split(".")[0],
                                            zicht = row.getCellText(colMap["visibility"] ?: -1).split(".")[0],
                                            neerslag = row.getCellText(colMap["precipitation"] ?: -1),
                                            opmerkingen = row.getCellText(colMap["remarks"] ?: -1),
                                            nrec = stats.nrec.toString(),
                                            nsoort = stats.speciesIds.size.toString(),
                                            status = "gearchiveerd"
                                        )
                                        headersToInsert.add(header)
                                        headersMap[onlineId] = HeaderInfo(header.tellingid, startTimeStr)
                                    }
                                }
                            }
                            rowIdx++
                        }
                    }
                } finally { workbook.close() }
            }

            if (headersToInsert.isNotEmpty()) db.tellingDao().insertHeaders(headersToInsert)

            // 3. Waarnemingen met koppeling naar numeriek ID
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
                                    if (db.tellingDao().getWaarnemingenByOnlineId(dataId).isEmpty()) {
                                        val headerInfo = headersMap[countId]
                                        if (headerInfo != null) {
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
                                                db.tellingDao().insertWaarnemingen(waarnemingenToInsert)
                                                waarnemingenToInsert.clear()
                                                onProgress("Gegevens opslaan...", rowIdx, 0)
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
            if (waarnemingenToInsert.isNotEmpty()) db.tellingDao().insertWaarnemingen(waarnemingenToInsert)
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
            // De datum staat in de tweede kolom (index 1 of "date")
            val dateStr = row.getCellText(colMap["date"] ?: 1) 
            // Probeer tijdstip uit "timestamp" kolom
            var timeStr = row.getCellText(colMap["timestamp"] ?: -1).trim()
            if (timeStr.isEmpty() || timeStr == "null") {
                timeStr = startTimeFallback
            }

            // dateStr is meestal "yyyy-MM-dd" of "dd-MM-yyyy"
            // We proberen het jaar, maand, dag te extraheren
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

    private data class HeaderInfo(val tellingId: String, val startTimeFallback: String)
}
