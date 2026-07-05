package com.yvesds.vt5.core.import

import android.content.Context
import android.util.Log
import com.yvesds.vt5.core.database.dao.TellingDao
import com.yvesds.vt5.core.database.entities.TellingHeader
import com.yvesds.vt5.core.database.entities.Waarneming
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class CsvImportResult(
    val inserted: Int,
    val skipped: Int = 0,
    val warnings: List<String> = emptyList(),
)

/**
 * Manager verantwoordelijk voor het importeren van Trektellen CSV bestanden.
 * Garandeert dat geïmporteerde data de status 'gearchiveerd' krijgt om uploads te voorkomen.
 */
class CsvImportManager(
    private val context: Context,
    private val tellingDao: TellingDao
) {
    private companion object {
        private const val TAG = "CsvImportManager"
        private const val DEFAULT_TIMEZONE = "Europe/Brussels"
        private const val BATCH_SIZE = 500
    }

    private val defaultZoneId: ZoneId = ZoneId.of(DEFAULT_TIMEZONE)
    private val headerDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val dataDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    private data class ValidationSets(
        val validSiteIds: Set<String> = emptySet(),
        val validSpeciesIds: Set<String> = emptySet(),
    )

    /**
     * Importeert headers (metadata) uit een CSV bestand.
     * Gebruikt komma (,) als scheidingsteken.
     */
    suspend fun importHeaders(inputStream: InputStream): Result<CsvImportResult> = withContext(Dispatchers.IO) {
        try {
            BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                val headers = readCsvRecord(reader)?.let { parseCsvLine(it, ',') }
                    ?: return@withContext Result.failure(Exception("Leeg bestand"))

                val idIdx = headerIndex(headers, "id")
                if (idIdx == -1) {
                    return@withContext Result.failure(Exception("Verplicht veld ontbreekt in header CSV: id"))
                }

                val startIdx = headerIndex(headers, "start")
                val stopIdx = headerIndex(headers, "stop")
                val weatherIdx = headerIndex(headers, "weather")
                val windDirIdx = headerIndex(headers, "winddirection")
                val windForceIdx = headerIndex(headers, "windspeed_bfr")
                val tempIdx = headerIndex(headers, "temperature")
                val cloudIdx = headerIndex(headers, "cloudcover")
                val cloudHeightIdx = headerIndex(headers, "cloudheight")
                val precipitationIdx = headerIndex(headers, "precipitation")
                val precipitationDurationIdx = headerIndex(headers, "perc_duration")
                val visibilityIdx = headerIndex(headers, "visibility")
                val siteIdx = headerIndex(headers, "siteid")
                val observersIdx = headerIndex(headers, "observers")
                val observersActiveIdx = headerIndex(headers, "observersactive")
                val observersPresentIdx = headerIndex(headers, "observerspresent")
                val countTypeIdx = headerIndex(headers, "counttype")
                val remarksIdx = headerIndex(headers, "remarks")

                val validation = loadValidationSets()
                val unknownSiteIds = linkedSetOf<String>()
                val warnings = mutableListOf<String>()
                val batch = ArrayList<TellingHeader>(BATCH_SIZE)

                var inserted = 0
                var skipped = 0
                var rawRecord = readCsvRecord(reader)
                while (rawRecord != null) {
                    if (rawRecord.isBlank()) {
                        rawRecord = readCsvRecord(reader)
                        continue
                    }

                    val columns = parseCsvLine(rawRecord, ',')
                    val tellingId = columnValue(columns, idIdx)
                    if (tellingId.isBlank()) {
                        skipped++
                        rawRecord = readCsvRecord(reader)
                        continue
                    }

                    val siteId = columnValue(columns, siteIdx)
                    if (validation.validSiteIds.isNotEmpty() && siteId.isNotBlank() && siteId !in validation.validSiteIds) {
                        unknownSiteIds.add(siteId)
                    }

                    batch.add(
                        TellingHeader(
                            tellingid = tellingId,
                            bron = CsvImportPolicy.IMPORT_SOURCE,
                            timezoneid = DEFAULT_TIMEZONE,
                            telpostid = siteId,
                            begintijd = normalizeHeaderDateTime(columnValue(columns, startIdx)),
                            eindtijd = normalizeHeaderDateTime(columnValue(columns, stopIdx)),
                            tellers = columnValue(columns, observersIdx),
                            tellersactief = columnValue(columns, observersActiveIdx),
                            tellersaanwezig = columnValue(columns, observersPresentIdx),
                            weer = columnValue(columns, weatherIdx),
                            windrichting = columnValue(columns, windDirIdx),
                            windkracht = columnValue(columns, windForceIdx),
                            temperatuur = columnValue(columns, tempIdx),
                            bewolking = columnValue(columns, cloudIdx),
                            bewolkinghoogte = columnValue(columns, cloudHeightIdx),
                            neerslag = columnValue(columns, precipitationIdx),
                            duurneerslag = columnValue(columns, precipitationDurationIdx),
                            zicht = columnValue(columns, visibilityIdx),
                            typetelling = columnValue(columns, countTypeIdx),
                            opmerkingen = columnValue(columns, remarksIdx),
                            status = CsvImportPolicy.IMPORT_STATUS,
                        )
                    )

                    if (batch.size >= BATCH_SIZE) {
                        tellingDao.insertHeaders(batch)
                        inserted += batch.size
                        batch.clear()
                    }

                    rawRecord = readCsvRecord(reader)
                }

                if (batch.isNotEmpty()) {
                    tellingDao.insertHeaders(batch)
                    inserted += batch.size
                }

                if (unknownSiteIds.isNotEmpty()) {
                    warnings.add(
                        "Onbekende siteid in header CSV (volgens serverdata): ${limitValues(unknownSiteIds)}"
                    )
                }

                Result.success(CsvImportResult(inserted = inserted, skipped = skipped, warnings = warnings))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij header import", e)
            Result.failure(e)
        }
    }

    /**
     * Importeert waarnemingen uit een CSV bestand.
     * Gebruikt puntkomma (;) als scheidingsteken.
     */
    suspend fun importWaarnemingen(inputStream: InputStream): Result<CsvImportResult> = withContext(Dispatchers.IO) {
        try {
            BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                val headers = readCsvRecord(reader)?.let { parseCsvLine(it, ';') }
                    ?: return@withContext Result.failure(Exception("Leeg bestand"))

                val countIdIdx = headerIndex(headers, "countid")
                if (countIdIdx == -1) {
                    return@withContext Result.failure(Exception("Verplicht veld ontbreekt in data CSV: countid"))
                }

                val dataIdIdx = headerIndex(headers, "dataid")
                val dateIdx = headerIndex(headers, "date")
                val timeIdx = headerIndex(headers, "timestamp")
                val speciesIdIdx = headerIndex(headers, "speciesid")
                val direction1Idx = headerIndex(headers, "direction1")
                val direction2Idx = headerIndex(headers, "direction2")
                val localIdx = headerIndex(headers, "local")
                val exactDirection1Idx = headerIndex(headers, "exactdirection1")
                val exactDirection2Idx = headerIndex(headers, "exactdirection2")
                val sightingDirectionIdx = headerIndex(headers, "sightingdirection")
                val remarkableIdx = headerIndex(headers, "remarkable")
                val remarkableLocalIdx = headerIndex(headers, "remarkablelocal")
                val ageIdx = headerIndex(headers, "age")
                val sexIdx = headerIndex(headers, "sex")
                val plumageIdx = headerIndex(headers, "plumage")
                val remarkIdx = headerIndex(headers, "remark")
                val heightIdx = headerIndex(headers, "height")
                val locationIdx = headerIndex(headers, "location")
                val migTypeIdx = headerIndex(headers, "migtype")
                val countTypeIdx = headerIndex(headers, "counttype")
                val groupIdIdx = headerIndex(headers, "groupid")
                val submittedIdx = headerIndex(headers, "submitted")

                val validation = loadValidationSets()
                val knownHeaderIds = tellingDao.getAllHeaderIds().toHashSet()
                val missingHeaderIds = linkedSetOf<String>()
                val unknownSpeciesIds = linkedSetOf<String>()
                val warnings = mutableListOf<String>()
                val batch = ArrayList<Waarneming>(BATCH_SIZE)

                var inserted = 0
                var skipped = 0
                var rawRecord = readCsvRecord(reader)
                while (rawRecord != null) {
                    if (rawRecord.isBlank()) {
                        rawRecord = readCsvRecord(reader)
                        continue
                    }

                    val columns = parseCsvLine(rawRecord, ';')
                    val tellingId = columnValue(columns, countIdIdx)
                    if (tellingId.isBlank()) {
                        skipped++
                        rawRecord = readCsvRecord(reader)
                        continue
                    }

                    if (tellingId !in knownHeaderIds) {
                        missingHeaderIds.add(tellingId)
                        skipped++
                        rawRecord = readCsvRecord(reader)
                        continue
                    }

                    val soortId = columnValue(columns, speciesIdIdx)
                    if (soortId.isBlank()) {
                        skipped++
                        rawRecord = readCsvRecord(reader)
                        continue
                    }

                    if (validation.validSpeciesIds.isNotEmpty() && soortId !in validation.validSpeciesIds) {
                        unknownSpeciesIds.add(soortId)
                    }

                    val aantal = normalizeCount(columnValue(columns, direction1Idx))
                    val aantalTerug = normalizeCount(columnValue(columns, direction2Idx))
                    val lokaal = normalizeCount(columnValue(columns, localIdx))

                    batch.add(
                        Waarneming(
                            idLocal = columnValue(columns, dataIdIdx).ifBlank { UUID.randomUUID().toString() },
                            tellingid = tellingId,
                            soortid = soortId,
                            aantal = aantal,
                            richting = columnValue(columns, exactDirection1Idx),
                            aantalterug = aantalTerug,
                            richtingterug = columnValue(columns, exactDirection2Idx),
                            sightingdirection = columnValue(columns, sightingDirectionIdx),
                            lokaal = lokaal,
                            markeren = normalizeBoolean(columnValue(columns, remarkableIdx)),
                            markerenlokaal = normalizeBoolean(columnValue(columns, remarkableLocalIdx)),
                            leeftijd = columnValue(columns, ageIdx),
                            geslacht = columnValue(columns, sexIdx),
                            kleed = columnValue(columns, plumageIdx),
                            opmerkingen = columnValue(columns, remarkIdx),
                            trektype = columnValue(columns, migTypeIdx),
                            teltype = columnValue(columns, countTypeIdx),
                            location = columnValue(columns, locationIdx),
                            height = columnValue(columns, heightIdx),
                            tijdstip = normalizeDataTimestamp(
                                dateValue = columnValue(columns, dateIdx),
                                timeValue = columnValue(columns, timeIdx),
                            ),
                            groupid = columnValue(columns, groupIdIdx),
                            uploadtijdstip = columnValue(columns, submittedIdx),
                            totaalaantal = (aantal.toIntOrNull() ?: 0)
                                .plus(aantalTerug.toIntOrNull() ?: 0)
                                .plus(lokaal.toIntOrNull() ?: 0)
                                .toString(),
                        )
                    )

                    if (batch.size >= BATCH_SIZE) {
                        tellingDao.insertWaarnemingen(batch)
                        inserted += batch.size
                        batch.clear()
                    }

                    rawRecord = readCsvRecord(reader)
                }

                if (batch.isNotEmpty()) {
                    tellingDao.insertWaarnemingen(batch)
                    inserted += batch.size
                }

                if (missingHeaderIds.isNotEmpty()) {
                    warnings.add(
                        "Dataregels overgeslagen door ontbrekende header (countid): ${limitValues(missingHeaderIds)}"
                    )
                }
                if (unknownSpeciesIds.isNotEmpty()) {
                    warnings.add(
                        "Onbekende soortid in data CSV (volgens serverdata): ${limitValues(unknownSpeciesIds)}"
                    )
                }

                Result.success(CsvImportResult(inserted = inserted, skipped = skipped, warnings = warnings))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij waarneming import", e)
            Result.failure(e)
        }
    }

    private suspend fun loadValidationSets(): ValidationSets {
        return runCatching {
            val snapshot = ServerDataCache.getOrLoad(context)
            ValidationSets(
                validSiteIds = snapshot.sitesById.keys,
                validSpeciesIds = snapshot.speciesById.keys,
            )
        }.getOrElse {
            Log.w(TAG, "Serverdata validatie niet beschikbaar tijdens CSV import: ${it.message}")
            ValidationSets()
        }
    }

    private fun headerIndex(headers: List<String>, key: String): Int {
        val normalizedKey = key.trim().lowercase()
        return headers.indexOfFirst { normalizeHeader(it) == normalizedKey }
    }

    private fun normalizeHeader(value: String): String {
        return value.trim().trimStart('\uFEFF').lowercase()
    }

    private fun columnValue(columns: List<String>, index: Int): String {
        if (index < 0 || index >= columns.size) return ""
        return columns[index].trim()
    }

    private fun normalizeCount(value: String): String {
        if (value.isBlank()) return "0"
        return value.toIntOrNull()?.toString() ?: "0"
    }

    private fun normalizeBoolean(value: String): String {
        return when (value.trim().lowercase()) {
            "1", "true", "waar", "ja", "yes" -> "1"
            else -> "0"
        }
    }

    private fun normalizeHeaderDateTime(value: String): String {
        if (value.isBlank()) return ""
        normalizeEpoch(value)?.let { return it }
        return runCatching {
            LocalDateTime.parse(value, headerDateTimeFormatter)
                .atZone(defaultZoneId)
                .toEpochSecond()
                .toString()
        }.getOrElse { value }
    }

    private fun normalizeDataTimestamp(dateValue: String, timeValue: String): String {
        normalizeEpoch(timeValue)?.let { return it }
        if (dateValue.isBlank() || timeValue.isBlank()) return timeValue.trim()

        return runCatching {
            val date = LocalDate.parse(dateValue, dataDateFormatter)
            val time = LocalTime.parse(timeValue, timeFormatter)
            date.atTime(time).atZone(defaultZoneId).toEpochSecond().toString()
        }.getOrElse { timeValue.trim() }
    }


    private fun normalizeEpoch(value: String): String? {
        val raw = value.trim().toLongOrNull() ?: return null
        return if (raw > 9_999_999_999L) {
            (raw / 1000L).toString()
        } else {
            raw.toString()
        }
    }

    private fun readCsvRecord(reader: BufferedReader): String? {
        val firstLine = reader.readLine() ?: return null
        val builder = StringBuilder(firstLine)

        while (hasUnclosedQuote(builder)) {
            val nextLine = reader.readLine() ?: break
            builder.append('\n').append(nextLine)
        }

        return builder.toString()
    }

    private fun hasUnclosedQuote(text: CharSequence): Boolean {
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch == '"') {
                if (inQuotes && i + 1 < text.length && text[i + 1] == '"') {
                    i += 2
                    continue
                }
                inQuotes = !inQuotes
            }
            i++
        }
        return inQuotes
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val ch = line[index]
            when {
                ch == '"' -> {
                    if (inQuotes && index + 1 < line.length && line[index + 1] == '"') {
                        current.append('"')
                        index++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == delimiter && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.setLength(0)
                }
                else -> current.append(ch)
            }
            index++
        }

        result.add(current.toString().trim())
        return result
    }

    private fun limitValues(values: Collection<String>, max: Int = 8): String {
        val shown = values.take(max)
        val suffix = if (values.size > max) " (+${values.size - max} meer)" else ""
        return shown.joinToString(", ") + suffix
    }
}
