package com.yvesds.vt5.ai

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
// ...existing imports...
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.yvesds.vt5.ai.AiConfig
import com.yvesds.vt5.ai.AiWeatherService
import org.json.JSONArray
import org.json.JSONObject

/**
 * TrainingDataPreparer - exports Room data + weather context into CSV features for training.
 * Current implementation creates a simple CSV with header and returns the created DocumentFile path.
 */
class TrainingDataPreparer(private val context: Context) {

    // Helper to escape CSV fields according to RFC4180: wrap in double quotes and double any existing quotes.
    private fun escapeCsvField(v: String?): String {
        if (v == null) return "\"\""
        val s = v.replace("\"", "\"\"")
        return "\"$s\""
    }

    suspend fun generateLabelsJson(exportDir: DocumentFile?): List<String> {
        return withContext(Dispatchers.IO) {
            if (exportDir == null) return@withContext emptyList()

            val db = VoiceTallyDatabase.getDatabase(context)
            val speciesIds = db.tellingDao().getAllUniqueSpeciesIds()

            val json = JSONObject()
            val classes = JSONArray()
            speciesIds.forEach { classes.put(it) }
            json.put("classes", classes)
            json.put("generatedAt", System.currentTimeMillis())

            val filename = "personal_migration_model.labels.json"
            // Overwrite existing labels if any
            val existing = exportDir.findFile(filename)
            val file = existing ?: exportDir.createFile("application/json", filename) ?: return@withContext emptyList()

            context.contentResolver.openOutputStream(file.uri)?.use { out ->
                out.write(json.toString(2).toByteArray(Charsets.UTF_8))
            }

            return@withContext speciesIds
        }
    }

    suspend fun exportTrainingCsv(exportDir: DocumentFile?, onProgressUpdate: (String) -> Unit = {}): String {
        return withContext(Dispatchers.IO) {
            if (exportDir == null) return@withContext ""

            val db = VoiceTallyDatabase.getDatabase(context)
            val headers = db.tellingDao().getAllHeaders()
            if (headers.isEmpty()) return@withContext ""

            val modelStore = ModelStore(context)
            val archiveManager = WeatherArchiveManager(context)
            val dataSnapshot = com.yvesds.vt5.features.serverdata.model.ServerDataCache.getOrLoad(context)

            // 1. Bouw Weer-Archief op (éénmalig per jaar/telpost)
            onProgressUpdate("Weer-archief controleren...")
            val usedSiteIds = db.tellingDao().getAllUsedSiteIds()
            val allEpochs = headers.mapNotNull { it.begintijd.toLongOrNull() }
            val years = allEpochs.map { Instant.ofEpochSecond(if (it > 9999999999L) it/1000 else it).atZone(ZoneId.of("UTC")).year }.distinct().sorted()

            // A. Telposten
            for (siteId in usedSiteIds) {
                val site = dataSnapshot.sitesById[siteId] ?: continue
                val lat = site.r1?.toDoubleOrNull() ?: continue
                val lon = site.r2?.toDoubleOrNull() ?: continue
                
                for (year in years) {
                    archiveManager.ensureYearArchive(year, lat, lon, "site_$siteId") { msg ->
                        onProgressUpdate("Weer-archief ($year): $msg")
                    }
                }
            }

            // B. Referentiepunten (France/Germany + Belgian Coast)
            val allRefs = mutableListOf<Pair<String, List<Pair<Double, Double>>>>(
                "ref_spring" to AiConfig.SPRING_SOUTH_REFS,
                "ref_autumn" to AiConfig.AUTUMN_NORTH_REFS,
                "ref_coast" to AiConfig.COAST_REFS
            )

            for (year in years) {
                for ((prefix, refList) in allRefs) {
                    for ((idx, pair) in refList.withIndex()) {
                        archiveManager.ensureYearArchive(year, pair.first, pair.second, "${prefix}_$idx") { msg ->
                            onProgressUpdate("Weer-archief ($year): $msg")
                        }
                    }
                }
            }

            // 2. Start CSV Export
            onProgressUpdate("Database exporteren naar CSV...")
            val headerLine = "tellingid,epoch,siteid,temp,temp_numeric,wind_ms,wind_ms_numeric,wind_deg,wind_dir_sin,wind_dir_cos,cloud_pct,visibility,precip,ref_avg_wind_ms,ref_avg_pressure,ref_coast_wind_ms,ref_coast_pressure,day_sin,day_cos,hour_sin,hour_cos,moon_phase,wind_chill,pressure_trend,yesterday_count,is_rare,sample_weight,label_species_id,label_count\n"

            // Fixed filename to avoid spamming multiple CSV files. 
            // We overwrite the existing one to keep only the latest training data.
            val filename = "training_data_current.csv"
            val existing = exportDir.findFile(filename)
            
            // Cache for species counts to determine rarity
            val speciesTotals = db.tellingDao().getAllSpeciesIds().associateWith { id ->
                db.tellingDao().countObservationsForSpecies(id)
            }
            val totalObservations = speciesTotals.values.sum().toDouble()

            // Log for debugging
            if (existing != null) android.util.Log.d("TrainingDataPreparer", "Overwriting existing CSV: ${existing.uri}")
            else android.util.Log.d("TrainingDataPreparer", "Creating new CSV: $filename in ${exportDir.uri}")

            val file = existing ?: exportDir.createFile("text/csv", filename) ?: return@withContext ""
            
            val outStream = context.contentResolver.openOutputStream(file.uri, "wt") 
            if (outStream == null) {
                android.util.Log.e("TrainingDataPreparer", "Failed to open output stream for ${file.uri}")
                return@withContext ""
            }

            val writer = outStream.bufferedWriter(Charsets.UTF_8)
            writer.write(headerLine)

            for (h in headers) {
                val waarnemingen = db.tellingDao().getWaarnemingenList(h.tellingid)
                
                val epochForHeader = try {
                    val raw = h.begintijd.toLongOrNull() ?: 0L
                    if (raw > 9999999999L) raw / 1000L else raw
                } catch (ex: Exception) { 0L }

                // Haal weer uit Room database (archief)
                val localWeather = archiveManager.getWeatherFromDb(epochForHeader, "site_${h.telpostid}")

                val zdt = if (epochForHeader > 0L) {
                    val tz = try { ZoneId.of(h.timezoneid) } catch (_: Exception) { ZoneId.of("Europe/Brussels") }
                    ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochForHeader), tz)
                } else {
                    ZonedDateTime.now()
                }

                // Referentie-weer uit archief (gemiddelde van 3 punten)
                val nowMonth = zdt.monthValue
                val isSpring = nowMonth <= 6
                val refPrefix = if (isSpring) "ref_spring_" else "ref_autumn_"
                val refCount = if (isSpring) AiConfig.SPRING_SOUTH_REFS.size else AiConfig.AUTUMN_NORTH_REFS.size
                
                val refWeathers = (0 until refCount).mapNotNull { idx ->
                    archiveManager.getWeatherFromDb(epochForHeader, "$refPrefix$idx")
                }
                
                val refAvgWind = if (refWeathers.isNotEmpty()) refWeathers.mapNotNull { it.windSpeed10m }.average() else 5.0
                val refAvgPressure = if (refWeathers.isNotEmpty()) refWeathers.mapNotNull { it.pressureMsl }.average() else 1013.0
                
                // Kust-referentie (Bredene & Noord)
                val coastWeathers = (0 until AiConfig.COAST_REFS.size).mapNotNull { idx ->
                    archiveManager.getWeatherFromDb(epochForHeader, "ref_coast_$idx")
                }
                val coastAvgWind = if (coastWeathers.isNotEmpty()) coastWeathers.mapNotNull { it.windSpeed10m }.average() else 5.0
                val coastAvgPressure = if (coastWeathers.isNotEmpty()) coastWeathers.mapNotNull { it.pressureMsl }.average() else 1013.0
                
                // Extra features uit Room archive
                val tempVal = localWeather?.temp ?: h.temperatuur.replace(',', '.').replace(Regex("[^0-9.\\-]"), "").toDoubleOrNull() ?: 15.0
                val windMsVal = localWeather?.windSpeed10m ?: h.windkracht.replace(',', '.').replace(Regex("[^0-9.\\-]"), "").toDoubleOrNull() ?: 5.0
                val cloudVal = localWeather?.cloudCover ?: 50.0
                val precipVal = localWeather?.precip ?: 0.0
                val pressureVal = localWeather?.pressureMsl ?: 1013.0
                val wind100m = localWeather?.windSpeed100m ?: windMsVal
                val gusts = localWeather?.windGusts10m ?: windMsVal
                
                val dayOfYear = zdt.dayOfYear.toDouble()
                val hourOfDay = zdt.hour.toDouble()
                val daySin = sin(2.0 * PI * dayOfYear / 365.25)
                val dayCos = cos(2.0 * PI * dayOfYear / 365.25)
                val hourSin = sin(2.0 * PI * hourOfDay / 24.0)
                val hourCos = cos(2.0 * PI * hourOfDay / 24.0)
                
                val moonPhase = calculateMoonPhase(epochForHeader)
                val windChill = calculateWindChill(tempVal, windMsVal)
                
                // Yesterday's count
                val yesterdayCount = if (epochForHeader > 0) {
                    val start = (epochForHeader - 86400).toString()
                    val end = epochForHeader.toString()
                    db.tellingDao().sumCountsInPeriod(start, end) ?: 0
                } else 0

                for (w in waarnemingen) {
                    val windDeg = parseWindDirectionToDegrees(h.windrichting) ?: localWeather?.windDir10m ?: 180.0
                    val windRad = Math.toRadians(windDeg)
                    val windDirSin = sin(windRad)
                    val windDirCos = cos(windRad)

                    val speciesCount = speciesTotals[w.soortid] ?: 0
                    val isRare = if (totalObservations > 0 && (speciesCount / totalObservations) < 0.001) 1 else 0
                    val sampleWeight = AiConfig.getSampleWeightForSpecies(w.soortid)

                    val fields = listOf(
                        h.tellingid,
                        epochForHeader.toString(),
                        h.telpostid,
                        tempVal.toString(),
                        tempVal.toString(),
                        windMsVal.toString(),
                        windMsVal.toString(),
                        windDeg.toString(),
                        windDirSin.toString(),
                        windDirCos.toString(),
                        cloudVal.toString(),
                        "10000", // visibility (niet in archive API)
                        precipVal.toString(),
                        refAvgWind.toString(),
                        refAvgPressure.toString(),
                        coastAvgWind.toString(),
                        coastAvgPressure.toString(),
                        daySin.toString(),
                        dayCos.toString(),
                        hourSin.toString(),
                        hourCos.toString(),
                        moonPhase.toString(),
                        windChill.toString(),
                        pressureVal.toString(), // Pressure as trend source
                        yesterdayCount.toString(),
                        isRare.toString(),
                        sampleWeight.toString(),
                        w.soortid,
                        w.aantal
                    )
                    val q = fields.map { escapeCsvField(it.toString()) }
                    writer.write(q.joinToString(","))
                    writer.newLine()
                }
            }
            // flush and close writer
            writer.flush()
            writer.close()

            // Also write a local copy to app files dir for offline training with the Python tool (stream from created SAF file)
            try {
                val localDir = java.io.File(context.filesDir, "ai_training")
                if (!localDir.exists()) localDir.mkdirs()
                val localFile = java.io.File(localDir, filename)
                // copy via SAF input stream to local file to avoid keeping whole content in memory
                context.contentResolver.openInputStream(file.uri)?.use { input ->
                    localFile.outputStream().use { out -> input.copyTo(out) }
                }
            } catch (_: Exception) {}

            return@withContext file.name ?: filename
        }
    }

    /**
     * Approximate moon phase (0.0 = New Moon, 0.5 = Full Moon, 1.0 = New Moon again).
     * Simple calculation based on synodic month (29.53 days).
     */
    private fun calculateMoonPhase(epoch: Long): Double {
        val knownNewMoonEpoch = 1704974760L // Jan 11, 2024 (approx)
        val synodicMonthSeconds = 29.530588 * 24 * 3600
        val delta = epoch - knownNewMoonEpoch
        val phase = (delta % synodicMonthSeconds) / synodicMonthSeconds
        return if (phase < 0) phase + 1.0 else phase
    }

    /**
     * Steadman formula for Wind Chill (Celsius).
     */
    private fun calculateWindChill(temp: Double, windMs: Double): Double {
        val windKmh = windMs * 3.6
        if (temp > 10.0 || windKmh < 4.8) return temp
        return 13.12 + 0.6215 * temp - 11.37 * Math.pow(windKmh, 0.16) + 0.3965 * temp * Math.pow(windKmh, 0.16)
    }

    /**
     * Parse wind direction string to degrees. Accepts numeric degrees or common compass labels
     * (N, NNO, NO, ... O for East, Z for South (Dutch labels)). Returns null if unknown.
     */
    private fun parseWindDirectionToDegrees(s: String?): Double? {
        if (s == null) return null
        val t = s.trim().uppercase(Locale.getDefault())
        if (t.isEmpty()) return null
        // try numeric
        t.replace("°", "").toDoubleOrNull()?.let { return it }

        // mapping of 16-point compass used in WeatherManager
        val labels = arrayOf("N","NNO","NO","ONO","O","OZO","ZO","ZZO","Z","ZZW","ZW","WZW","W","WNW","NW","NNW")
        val idx = labels.indexOf(t)
        if (idx >= 0) return idx * 22.5

        // Accept common English abbreviations
        val eng = mapOf(
            "N" to 0.0, "NNE" to 22.5, "NE" to 45.0, "ENE" to 67.5,
            "E" to 90.0, "ESE" to 112.5, "SE" to 135.0, "SSE" to 157.5,
            "S" to 180.0, "SSW" to 202.5, "SW" to 225.0, "WSW" to 247.5,
            "W" to 270.0, "WNW" to 292.5, "NW" to 315.0, "NNW" to 337.5
        )
        eng[t]?.let { return it }

        return null
    }
}

