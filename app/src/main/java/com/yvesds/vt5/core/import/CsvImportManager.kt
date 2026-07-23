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
import com.yvesds.vt5.core.opslag.AppDataStore
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import java.io.PrintWriter
import java.io.StringWriter

data class CsvImportResult(
    val inserted: Int,
    val skipped: Int = 0,
    val warnings: List<String> = emptyList(),
    val insertedHeaders: Int = 0,
    val insertedWaarnemingen: Int = 0,
)

/**
 * Manager verantwoordelijk voor het importeren van Trektellen CSV bestanden.
 * Garandeert dat geïmporteerde data de status 'gearchiveerd' krijgt om uploads te voorkomen.
 */
class CsvImportManager(
    private val context: Context,
    private val tellingDao: TellingDao
) {
    // De SaF helper kan soms trage IO doen bij gebruik. Maak deze lazy zodat
    // de constructor van CsvImportManager geen onnodige synchrone IO triggert
    // wanneer de manager alleen wordt geïnstantieerd (bijv. in onCreate van een Activity).
    private val safHelper: SaFStorageHelper by lazy { SaFStorageHelper(context) }
    private companion object {
        private const val TAG = "CsvImportManager"
        private const val DEFAULT_TIMEZONE = "Europe/Brussels"
        private const val BATCH_SIZE = 500
    }

    private val defaultZoneId: ZoneId = ZoneId.of(DEFAULT_TIMEZONE)
    // Strict header parsing: headers will be provided in the exact format "yyyy-dd-MM HH:mm:ss" (year-day-month)
    // and optionally date-only "yyyy-dd-MM". We do NOT accept other textual header datetime formats.
    private val headerDateTimeFormatters: List<DateTimeFormatter> = listOf(
        DateTimeFormatter.ofPattern("yyyy-dd-MM HH:mm:ss")
    )

    // Only the exact date-only pattern is accepted for headers
    private val headerDateOnlyFormatters: List<DateTimeFormatter> = listOf(
        DateTimeFormatter.ofPattern("yyyy-dd-MM")
    )

    // Data CSV date format historically was 'dd/MM/yy'. Accept that and the new 'yyyy-dd-MM' format as requested.
    private val dataDateFormatters: List<DateTimeFormatter> = listOf(
        DateTimeFormatter.ofPattern("dd/MM/yy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy-dd-MM"),
    )

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    private data class ValidationSets(
        val validSiteIds: Set<String> = emptySet(),
        val validSpeciesIds: Set<String> = emptySet(),
    )

    /**
     * Importeert headers (metadata) uit een CSV bestand.
     * Gebruikt puntkomma (;) als scheidingsteken.
     */
    suspend fun importHeaders(inputStream: InputStream): Result<CsvImportResult> = withContext(Dispatchers.IO) {
        try {
            BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                val firstLine = readCsvRecord(reader) ?: return@withContext Result.failure(Exception("Leeg bestand"))
                val headers = parseCsvLine(firstLine)

                val idIdx = headerIndex(headers, "id")
                if (idIdx == -1) {
                    return@withContext Result.failure(Exception("Verplicht veld ontbreekt in header CSV: id"))
                }

                val startIdx = headerIndex(headers, "start")
                val stopIdx = headerIndex(headers, "stop")
                val dayIdx = headerIndex(headers, "day")
                val monthIdx = headerIndex(headers, "month")
                val yearIdx = headerIndex(headers, "year")
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
                val tellingIdCursor = TellingIdCursor()

                var inserted = 0
                var skipped = 0
                var rawRecord = readCsvRecord(reader)
                while (rawRecord != null) {
                    if (rawRecord.isBlank()) {
                        rawRecord = readCsvRecord(reader)
                        continue
                    }

                    val columns = parseCsvLine(rawRecord)
                    val onlineId = columnValue(columns, idIdx)
                    if (onlineId.isBlank()) {
                        skipped++
                        rawRecord = readCsvRecord(reader)
                        continue
                    }
                    val localTellingId = nextLocalTellingId(tellingIdCursor)

                    val siteId = columnValue(columns, siteIdx)
                    if (validation.validSiteIds.isNotEmpty() && siteId.isNotBlank() && siteId !in validation.validSiteIds) {
                        unknownSiteIds.add(siteId)
                    }

                    batch.add(
                        TellingHeader(
                            tellingid = localTellingId,
                            onlineid = onlineId,
                            bron = CsvImportPolicy.IMPORT_SOURCE,
                            timezoneid = DEFAULT_TIMEZONE,
                            telpostid = siteId,
                            begintijd = normalizeHeaderDateTime(
                                value = columnValue(columns, startIdx),
                                dayStr = columnValue(columns, dayIdx),
                                monthStr = columnValue(columns, monthIdx),
                                yearStr = columnValue(columns, yearIdx)
                            ),
                            eindtijd = normalizeHeaderDateTime(
                                value = columnValue(columns, stopIdx),
                                dayStr = columnValue(columns, dayIdx),
                                monthStr = columnValue(columns, monthIdx),
                                yearStr = columnValue(columns, yearIdx)
                            ),
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

                Result.success(
                    CsvImportResult(
                        inserted = inserted,
                        skipped = skipped,
                        warnings = warnings,
                        insertedHeaders = inserted,
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij header import", e)
            Result.failure(e)
        }
    }

    /**
     * Importeert waarnemingen uit een CSV bestand.
     * Gebruikt puntkomma (;) als scheidingsteken.
     * Verwacht kolom 'countid' met server-online-id; die wordt gebruikt om te linken naar lokale tellingid.
     * De waarde van 'countid' wordt opgeslagen in Waarneming.onlineid.
     */
    suspend fun importWaarnemingen(inputStream: InputStream): Result<CsvImportResult> = withContext(Dispatchers.IO) {
        try {
            BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                val headers = readCsvRecord(reader)?.let { parseCsvLine(it) }
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
                // build map onlineid -> lokale tellingid
                val onlineToLocal: Map<String, String> = tellingDao.getAllHeaders()
                    .filter { it.onlineid.isNotBlank() }
                    .associate { it.onlineid to it.tellingid }

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

                    val columns = parseCsvLine(rawRecord)
                    val countOnlineId = columnValue(columns, countIdIdx) // server online id
                    if (countOnlineId.isBlank()) {
                        skipped++
                        rawRecord = readCsvRecord(reader)
                        continue
                    }

                    // lookup lokale tellingid op basis van onlineid
                    val localTellingId = onlineToLocal[countOnlineId]
                    if (localTellingId == null) {
                        missingHeaderIds.add(countOnlineId)
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
                    val dataId = columnValue(columns, dataIdIdx)
                    val localRecordId = if (dataId.isBlank()) {
                        AppDataStore.nextRecordId(context, localTellingId)
                    } else {
                        dataId
                    }

                    batch.add(
                        Waarneming(
                            idLocal = localRecordId,
                            tellingid = localTellingId,          // gebruik lokale PK
                            onlineid = countOnlineId,            // bewaar server-id als onlineid
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
                        "Dataregels overgeslagen door ontbrekende header (countid / onlineid): ${limitValues(missingHeaderIds)}"
                    )
                }
                if (unknownSpeciesIds.isNotEmpty()) {
                    warnings.add(
                        "Onbekende soortid in data CSV (volgens serverdata): ${limitValues(unknownSpeciesIds)}"
                    )
                }

                // schrijf import-log met details (warnings / skipped) naar VT5/imports
                if (warnings.isNotEmpty() || skipped > 0) {
                    val summary = buildString {
                        appendLine("Data import summary")
                        appendLine("Inserted: $inserted")
                        appendLine("Skipped: $skipped")
                        appendLine("Missing headers (onlineids): ${limitValues(missingHeaderIds)}")
                        appendLine("Unknown species: ${limitValues(unknownSpeciesIds)}")
                        appendLine("Warnings:")
                        warnings.forEach { appendLine("- $it") }
                    }
                    writeImportLog("data_import", summary)
                }

                Result.success(
                    CsvImportResult(
                        inserted = inserted,
                        skipped = skipped,
                        warnings = warnings,
                        insertedWaarnemingen = inserted,
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij waarneming import", e)
            val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
            val content = "Data import failed: ${e.message}\n\n${sw.toString()}"
            writeImportLog("data_import_error", content)
            Result.failure(e)
        }
    }

    /**
     * Importeer headers en data gecombineerd.
     * Werking:
     *  - Lees eerst de volledige data CSV in en indexeer de regels op 'countid' (vierde veld in Trektellen data CSV header).
     *  - Lees daarna headers CSV regel-voor-regel; voor elke header haal de 'id' waarde (server-online-id)
     *    en insert de header. Vervolgens worden alle dataregels met countid == id uit de data-index ingeschreven
     *    als waarnemingen (met waarneming.onlineid = id).
     *
     * Vereisten: header CSV heeft kolom 'id'; data CSV heeft kolom 'countid'.
     */
    suspend fun importHeadersAndData(headerInput: InputStream, dataInput: InputStream): Result<CsvImportResult> = withContext(Dispatchers.IO) {
        try {
            // --- Stap A: Parse data CSV en indexeer op countid ---
            val dataReader = BufferedReader(InputStreamReader(dataInput, StandardCharsets.UTF_8))
            val dataHeaderLine = readCsvRecord(dataReader) ?: return@withContext Result.failure(Exception("Leeg data bestand"))
            // data files are expected to use ';'
            val dataHeaders = parseCsvLine(dataHeaderLine)
            val countIdIdx = headerIndex(dataHeaders, "countid")
            if (countIdIdx == -1) return@withContext Result.failure(Exception("Data CSV mist vereiste kolom: countid"))
            val dataIdIdx = headerIndex(dataHeaders, "dataid")
            val dateIdx = headerIndex(dataHeaders, "date")
            val timeIdx = headerIndex(dataHeaders, "timestamp")
            val speciesIdIdx = headerIndex(dataHeaders, "speciesid")
            val direction1Idx = headerIndex(dataHeaders, "direction1")
            val direction2Idx = headerIndex(dataHeaders, "direction2")
            val localIdx = headerIndex(dataHeaders, "local")
            val exactDirection1Idx = headerIndex(dataHeaders, "exactdirection1")
            val exactDirection2Idx = headerIndex(dataHeaders, "exactdirection2")
            val sightingDirectionIdx = headerIndex(dataHeaders, "sightingdirection")
            val remarkableIdx = headerIndex(dataHeaders, "remarkable")
            val remarkableLocalIdx = headerIndex(dataHeaders, "remarkablelocal")
            val ageIdx = headerIndex(dataHeaders, "age")
            val sexIdx = headerIndex(dataHeaders, "sex")
            val plumageIdx = headerIndex(dataHeaders, "plumage")
            val remarkIdx = headerIndex(dataHeaders, "remark")
            val heightIdx = headerIndex(dataHeaders, "height")
            val locationIdx = headerIndex(dataHeaders, "location")
            val migTypeIdx = headerIndex(dataHeaders, "migtype")
            val dataCountTypeIdx = headerIndex(dataHeaders, "counttype")
            val groupIdIdx = headerIndex(dataHeaders, "groupid")
            val submittedIdx = headerIndex(dataHeaders, "submitted")

            // Build index: map countid -> list of parsed columns
            val dataIndex = mutableMapOf<String, MutableList<List<String>>>()
            var raw = readCsvRecord(dataReader)
            while (raw != null) {
                if (raw.isNotBlank()) {
                    val cols = parseCsvLine(raw)
                    val cid = columnValue(cols, countIdIdx)
                    if (cid.isNotBlank()) {
                        dataIndex.getOrPut(cid) { ArrayList() }.add(cols)
                    }
                }
                raw = readCsvRecord(dataReader)
            }

            // Load validation sets once
            val validation = loadValidationSets()

            // --- Stap B: Parse header CSV en voor elk header record insert header + bijhorende data records ---
            val headerReader = BufferedReader(InputStreamReader(headerInput, StandardCharsets.UTF_8))
            val headerFirstLine = readCsvRecord(headerReader) ?: return@withContext Result.failure(Exception("Leeg header bestand"))
            val headerCols = parseCsvLine(headerFirstLine)
            val headerIdIdx = headerIndex(headerCols, "id")
            if (headerIdIdx == -1) return@withContext Result.failure(Exception("Header CSV mist vereiste kolom: id"))

            // other header column indices (optional)
            val startIdx = headerIndex(headerCols, "start")
            val stopIdx = headerIndex(headerCols, "stop")
            val dayIdx = headerIndex(headerCols, "day")
            val monthIdx = headerIndex(headerCols, "month")
            val yearIdx = headerIndex(headerCols, "year")
            val siteIdx = headerIndex(headerCols, "siteid")
            val observersIdx = headerIndex(headerCols, "observers")
            val weatherIdx = headerIndex(headerCols, "weather")
            val windDirIdx = headerIndex(headerCols, "winddirection")
            val windForceIdx = headerIndex(headerCols, "windspeed_bfr")
            val tempIdx = headerIndex(headerCols, "temperature")
            val cloudIdx = headerIndex(headerCols, "cloudcover")
            val precipitationIdx = headerIndex(headerCols, "precipitation")
            val percDurIdx = headerIndex(headerCols, "perc_duration")
            val visibilityIdx = headerIndex(headerCols, "visibility")
            val countTypeIdx = headerIndex(headerCols, "counttype")
            val remarksIdx = headerIndex(headerCols, "remarks")
            val observersActiveIdx = headerIndex(headerCols, "observersactive")
            val observersPresentIdx = headerIndex(headerCols, "observerspresent")

            val headerBatch = ArrayList<TellingHeader>(BATCH_SIZE)
            val waarnemingBatch = ArrayList<Waarneming>(BATCH_SIZE)
            val tellingIdCursor = TellingIdCursor()

            var insertedHeaders = 0
            var insertedWaarnemingen = 0
            val warnings = linkedSetOf<String>()
            var skippedDataRecords = 0

            var headerRaw = readCsvRecord(headerReader)
            while (headerRaw != null) {
                if (headerRaw.isBlank()) { headerRaw = readCsvRecord(headerReader); continue }
                val headerValues = parseCsvLine(headerRaw)
                val onlineId = columnValue(headerValues, headerIdIdx)
                if (onlineId.isBlank()) { headerRaw = readCsvRecord(headerReader); continue }

                val localTellingId = nextLocalTellingId(tellingIdCursor)

                // Calculate counts from the indexed data for this onlineId
                val related = dataIndex[onlineId]
                val nrecCount = related?.size ?: 0
                val uniqueSpeciesCount = related?.map { cols -> columnValue(cols, speciesIdIdx) }
                    ?.filter { it.isNotBlank() }?.toSet()?.size ?: 0

                // compose TellingHeader with nrec and nsoort
                    val headerEntity = TellingHeader(
                    tellingid = localTellingId,
                    onlineid = onlineId,
                    bron = CsvImportPolicy.IMPORT_SOURCE,
                    timezoneid = DEFAULT_TIMEZONE,
                    telpostid = columnValue(headerValues, siteIdx),
                    begintijd = normalizeHeaderDateTime(
                        value = columnValue(headerValues, startIdx),
                        dayStr = columnValue(headerValues, dayIdx),
                        monthStr = columnValue(headerValues, monthIdx),
                        yearStr = columnValue(headerValues, yearIdx)
                    ),
                    eindtijd = normalizeHeaderDateTime(
                        value = columnValue(headerValues, stopIdx),
                        dayStr = columnValue(headerValues, dayIdx),
                        monthStr = columnValue(headerValues, monthIdx),
                        yearStr = columnValue(headerValues, yearIdx)
                    ),
                    tellers = columnValue(headerValues, observersIdx),
                    tellersactief = columnValue(headerValues, observersActiveIdx),
                    tellersaanwezig = columnValue(headerValues, observersPresentIdx),
                    weer = columnValue(headerValues, weatherIdx),
                    windrichting = columnValue(headerValues, windDirIdx),
                    windkracht = columnValue(headerValues, windForceIdx),
                    temperatuur = columnValue(headerValues, tempIdx),
                    bewolking = columnValue(headerValues, cloudIdx),
                    neerslag = columnValue(headerValues, precipitationIdx),
                    duurneerslag = columnValue(headerValues, percDurIdx),
                    zicht = columnValue(headerValues, visibilityIdx),
                    typetelling = columnValue(headerValues, countTypeIdx),
                    opmerkingen = columnValue(headerValues, remarksIdx),
                    nrec = nrecCount.toString(),
                    nsoort = uniqueSpeciesCount.toString(),
                    status = CsvImportPolicy.IMPORT_STATUS
                )

                // Persist header immediately so FK references from waarnemingen are valid
                headerBatch.add(headerEntity)
                tellingDao.insertHeaders(headerBatch)
                insertedHeaders += headerBatch.size
                headerBatch.clear()

                // find related data records by onlineId
                if (related != null && related.isNotEmpty()) {
                    // prepare waarnemingen
                    for (cols in related) {
                        val dataId = columnValue(cols, dataIdIdx)
                        val species = columnValue(cols, speciesIdIdx)
                        if (species.isBlank()) { skippedDataRecords++; continue }
                        val localRecordId = if (dataId.isBlank()) {
                            AppDataStore.nextRecordId(context, localTellingId)
                        } else {
                            dataId
                        }

                        if (validation.validSpeciesIds.isNotEmpty() && species !in validation.validSpeciesIds) {
                            // unknown species - record a warning but still insert the row
                            warnings.add("Onbekende soort '$species' voor telling onlineId=$onlineId")
                        }

                        val waarneming = Waarneming(
                            idLocal = localRecordId,
                            tellingid = localTellingId,
                            onlineid = onlineId,
                            soortid = species,
                            aantal = normalizeCount(columnValue(cols, direction1Idx)),
                            richting = columnValue(cols, exactDirection1Idx),
                            aantalterug = normalizeCount(columnValue(cols, direction2Idx)),
                            richtingterug = columnValue(cols, exactDirection2Idx),
                            sightingdirection = columnValue(cols, sightingDirectionIdx),
                            lokaal = normalizeCount(columnValue(cols, localIdx)),
                            markeren = normalizeBoolean(columnValue(cols, remarkableIdx)),
                            markerenlokaal = normalizeBoolean(columnValue(cols, remarkableLocalIdx)),
                            leeftijd = columnValue(cols, ageIdx),
                            geslacht = columnValue(cols, sexIdx),
                            kleed = columnValue(cols, plumageIdx),
                            opmerkingen = columnValue(cols, remarkIdx),
                            trektype = columnValue(cols, migTypeIdx),
                            teltype = columnValue(cols, dataCountTypeIdx),
                            location = columnValue(cols, locationIdx),
                            height = columnValue(cols, heightIdx),
                            tijdstip = normalizeDataTimestamp(
                                dateValue = columnValue(cols, dateIdx),
                                timeValue = columnValue(cols, timeIdx)
                            ),
                            groupid = columnValue(cols, groupIdIdx),
                            uploadtijdstip = columnValue(cols, submittedIdx),
                            totaalaantal = ((normalizeCount(columnValue(cols, direction1Idx)).toIntOrNull() ?: 0)
                                + (normalizeCount(columnValue(cols, direction2Idx)).toIntOrNull() ?: 0)
                                + (normalizeCount(columnValue(cols, localIdx)).toIntOrNull() ?: 0)).toString()
                        )
                        waarnemingBatch.add(waarneming)
                        if (waarnemingBatch.size >= BATCH_SIZE) {
                            tellingDao.insertWaarnemingen(waarnemingBatch)
                            insertedWaarnemingen += waarnemingBatch.size
                            waarnemingBatch.clear()
                        }
                    }
                }

                headerRaw = readCsvRecord(headerReader)
            }

            if (headerBatch.isNotEmpty()) { tellingDao.insertHeaders(headerBatch); insertedHeaders += headerBatch.size; headerBatch.clear() }
            if (waarnemingBatch.isNotEmpty()) { tellingDao.insertWaarnemingen(waarnemingBatch); insertedWaarnemingen += waarnemingBatch.size; waarnemingBatch.clear() }

            // write summary log
            val summary = buildString {
                appendLine("Combined import summary")
                appendLine("Inserted headers: $insertedHeaders")
                appendLine("Inserted waarnemingen: $insertedWaarnemingen")
                appendLine("Skipped data records (invalid species/id): $skippedDataRecords")
                if (warnings.isNotEmpty()) {
                    appendLine("Warnings:")
                    warnings.forEach { appendLine("- $it") }
                }
            }
            if (insertedHeaders > 0 || insertedWaarnemingen > 0 || skippedDataRecords > 0 || warnings.isNotEmpty()) {
                writeImportLog("combined_import", summary)
            }

            Result.success(
                CsvImportResult(
                    inserted = insertedWaarnemingen + insertedHeaders,
                    skipped = skippedDataRecords,
                    warnings = warnings.toList(),
                    insertedHeaders = insertedHeaders,
                    insertedWaarnemingen = insertedWaarnemingen,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Combined import failed", e)
            val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
            writeImportLog("combined_import_error", "Combined import failed: ${e.message}\n\n${sw}")
            Result.failure(e)
        }
    }

    /**
     * Scant de imports folder en geeft een lijst van alle herkende (header + data) paren terug.
     */
    suspend fun getPendingImportPairs(): List<Pair<BatchFileInfo, BatchFileInfo>> = withContext(Dispatchers.IO) {
        try {
            val importsDir = safHelper.getImportsDirSuspend() ?: return@withContext emptyList()
            val files = importsDir.listFiles()
            val recognized = mutableListOf<BatchFileInfo>()

            for (file in files) {
                val name = file.name ?: continue
                if (!name.lowercase().endsWith(".csv")) continue
                
                val pattern = Regex("^Trektellen_(headerdata|data)_(\\d+)_(\\d{4})\\.csv$", RegexOption.IGNORE_CASE)
                val match = pattern.matchEntire(name) ?: continue
                
                val type = if (match.groupValues[1].lowercase() == "headerdata") CsvFileType.HEADER else CsvFileType.DATA
                recognized.add(BatchFileInfo(file, name, type, match.groupValues[2], match.groupValues[3]))
            }

            val headers = recognized.filter { it.type == CsvFileType.HEADER }
            val dataFiles = recognized.filter { it.type == CsvFileType.DATA }
            
            val pairs = mutableListOf<Pair<BatchFileInfo, BatchFileInfo>>()
            for (h in headers) {
                val d = dataFiles.find { it.telpostId == h.telpostId && it.year == h.year }
                if (d != null) {
                    pairs.add(h to d)
                }
            }
            // Sorteren voor consistente verwerking over herstarts heen
            pairs.sortBy { "${it.first.telpostId}_${it.first.year}" }
            pairs
        } catch (e: Exception) {
            Log.e(TAG, "Error listing pending pairs", e)
            emptyList()
        }
    }

    /**
     * Importeert één specifiek paar bestanden.
     */
    suspend fun importSinglePair(header: BatchFileInfo, data: BatchFileInfo): Result<CsvImportResult> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(header.file.uri)?.use { hs ->
                context.contentResolver.openInputStream(data.file.uri)?.use { ds ->
                    importHeadersAndData(hs, ds)
                }
            } ?: Result.failure(Exception("Kon header bestand niet openen"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    enum class CsvFileType { HEADER, DATA }
    data class BatchFileInfo(
        val file: androidx.documentfile.provider.DocumentFile,
        val name: String,
        val type: CsvFileType,
        val telpostId: String,
        val year: String
    )

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

    private fun normalizeHeaderDateTime(value: String, dayStr: String = "", monthStr: String = "", yearStr: String = ""): String {
        if (value.isBlank()) return ""

        // Strict policy for headers: ALWAYS parse the textual header 'start'/'stop' value
        // using the exact known pattern(s). Do NOT treat a numeric epoch string as priority
        // here even if an 'Epoch_start' column exists later in the CSV — the textual value
        // is authoritative for header timestamps.

        // Only accept the exact header datetime pattern(s). If parsing fails we return an empty string
        // If day/month/year columns are provided, construct LocalDateTime from them and the time portion
        if (dayStr.isNotBlank() && monthStr.isNotBlank() && yearStr.isNotBlank()) {
            val day = dayStr.trim().toIntOrNull()
            val month = monthStr.trim().toIntOrNull()
            val year = yearStr.trim().toIntOrNull()
            if (day != null && month != null && year != null) {
                // extract time from value (expecting 'YYYY-XX-XX HH:mm:ss' or similar), prefer the part after space
                val timePart = value.trim().split(' ').getOrNull(1) ?: "00:00:00"
                val parsedTime = runCatching { LocalTime.parse(timePart, timeFormatter) }.getOrNull()
                val time = parsedTime ?: LocalTime.MIDNIGHT
                try {
                    return LocalDateTime.of(year, month, day, time.hour, time.minute, time.second)
                        .atZone(defaultZoneId).toEpochSecond().toString()
                } catch (_: Exception) {
                    // invalid date numbers -> fall through to strict textual parsing below
                }
            }
        }

        for (fmt in headerDateTimeFormatters) {
            val parsed = runCatching { LocalDateTime.parse(value, fmt) }.getOrNull()
            if (parsed != null) {
                return parsed.atZone(defaultZoneId).toEpochSecond().toString()
            }
        }

        // Accept exact date-only header pattern and interpret as start of day
        for (fmt in headerDateOnlyFormatters) {
            val parsedDate = runCatching { LocalDate.parse(value, fmt) }.getOrNull()
            if (parsedDate != null) {
                val dt = parsedDate.atStartOfDay()
                return dt.atZone(defaultZoneId).toEpochSecond().toString()
            }
        }

        // Strict policy: do not accept other formats or numeric epoch here; return empty so caller can detect missing/invalid header time
        return ""
    }

    private fun normalizeDataTimestamp(dateValue: String, timeValue: String): String {
        normalizeEpoch(timeValue)?.let { return it }
        if (dateValue.isBlank() || timeValue.isBlank()) return timeValue.trim()
        // Try multiple date formats (historic and new). If none match, fall back to original timeValue.
        for (df in dataDateFormatters) {
            val date = runCatching { LocalDate.parse(dateValue, df) }.getOrNull()
            if (date != null) {
                val time = runCatching { LocalTime.parse(timeValue, timeFormatter) }.getOrNull()
                    ?: return timeValue.trim()
                return date.atTime(time).atZone(defaultZoneId).toEpochSecond().toString()
            }
        }

        return timeValue.trim()
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

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        val delimiter = ';'

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

    private data class TellingIdCursor(
        var next: Long = 0L,
        var endExclusive: Long = 0L,
    )

    private suspend fun nextLocalTellingId(cursor: TellingIdCursor): String {
        if (cursor.next >= cursor.endExclusive) {
            val block = AppDataStore.reserveTellingIds(context, BATCH_SIZE)
            cursor.next = block.first
            cursor.endExclusive = block.last + 1L
        }
        val current = cursor.next
        cursor.next += 1L
        return current.toString()
    }

    private suspend fun writeImportLog(namePrefix: String, content: String) {
        try {
            // ensure SAF folders (no-op if not configured)
            try { safHelper.ensureFoldersSuspend() } catch (_: Exception) {}
            val now = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            val filename = "${now}_${namePrefix}.log"
            val wroteSaf = runCatching {
                safHelper.writeImportsFileSuspend(filename, content)
            }.getOrDefault(false)

            if (!wroteSaf) {
                // fallback to internal file
                val root = java.io.File(context.filesDir, "VT5/imports")
                if (!root.exists()) root.mkdirs()
                val file = java.io.File(root, filename)
                file.writeText(content, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.w("CsvImportManager", "Failed to write import log: ${e.message}", e)
        }
    }
}
