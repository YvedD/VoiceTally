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

            val filename = "training_model.labels.json"
            // Overwrite existing labels if any
            val existing = exportDir.findFile(filename)
            val file = existing ?: exportDir.createFile("application/json", filename) ?: return@withContext emptyList()

            context.contentResolver.openOutputStream(file.uri)?.use { out ->
                out.write(json.toString(2).toByteArray(Charsets.UTF_8))
            }

            return@withContext speciesIds
        }
    }

    suspend fun exportTrainingCsv(exportDir: DocumentFile?): String {
        return withContext(Dispatchers.IO) {
            if (exportDir == null) return@withContext ""

            val db = VoiceTallyDatabase.getDatabase(context)
            val headers = db.tellingDao().getAllHeaders() // assume exists

            val headerLine = "tellingid,epoch,siteid,temp,temp_numeric,wind_ms,wind_ms_numeric,wind_deg,wind_dir_sin,wind_dir_cos,cloud_pct,visibility,precip,ref_avg_wind_ms,ref_avg_pressure,day_sin,day_cos,hour_sin,hour_cos,sample_weight,label_species_id,label_count\n"

            // Fixed filename to avoid spamming multiple CSV files. 
            // We overwrite the existing one to keep only the latest training data.
            val filename = "training_data_current.csv"
            val existing = exportDir.findFile(filename)
            val file = existing ?: exportDir.createFile("text/csv", filename) ?: return@withContext ""
            
            val outStream = context.contentResolver.openOutputStream(file.uri, "wt") ?: return@withContext ""
            val writer = outStream.bufferedWriter(Charsets.UTF_8)
            writer.write(headerLine)

            for (h in headers) {
                val waarnemingen = db.tellingDao().getWaarnemingenList(h.tellingid)
                val epoch = Instant.now().epochSecond
                // Determine season window: voorjaar (Jan..15Jun) uses south refs, otherwise autumn uses north refs
                // For simplicity choose ref list based on current month
                val nowMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
                val refs = if (nowMonth <= 6) AiConfig.SPRING_SOUTH_REFS else AiConfig.AUTUMN_NORTH_REFS

                // Fetch simple aggregated ref weather (synchronous call via AiWeatherService may be suspend)
                var refAvgWind: Double? = null
                var refAvgPressure: Double? = null
                try {
                    val refWeathers = refs.mapNotNull { pair ->
                        runCatching { AiWeatherService.fetchContextualWeather(context, pair.first, pair.second) }.getOrNull()
                    }
                    if (refWeathers.isNotEmpty()) {
                        refAvgWind = refWeathers.mapNotNull { it.windSpeed }.average()
                        refAvgPressure = refWeathers.mapNotNull { it.pressure }.average()
                    }
                } catch (_: Exception) {
                    // ignore failures, keep nulls
                }

                // Compute time features based on header.begintijd (if available)
                val epochForHeader = try {
                    val raw = h.begintijd.toLongOrNull() ?: 0L
                    if (raw > 9999999999L) raw / 1000L else raw
                } catch (ex: Exception) { 0L }

                val zdt = if (epochForHeader > 0L) {
                    val tz = try { ZoneId.of(h.timezoneid) } catch (_: Exception) { ZoneId.of("Europe/Brussels") }
                    ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochForHeader), tz)
                } else {
                    ZonedDateTime.now()
                }

                val dayOfYear = zdt.dayOfYear.toDouble()
                val hourOfDay = zdt.hour.toDouble()
                val daySin = sin(2.0 * PI * dayOfYear / 365.25)
                val dayCos = cos(2.0 * PI * dayOfYear / 365.25)
                val hourSin = sin(2.0 * PI * hourOfDay / 24.0)
                val hourCos = cos(2.0 * PI * hourOfDay / 24.0)

                for (w in waarnemingen) {
                    val tempRaw = h.temperatuur.ifEmpty { "" }
                    val tempNumeric = tempRaw.replace(',', '.').replace(Regex("[^0-9.\\-]"), "").toDoubleOrNull()

                    val windRaw = h.windkracht.ifEmpty { "" }
                    val windNumeric = windRaw.replace(',', '.').replace(Regex("[^0-9.\\-]"), "").toDoubleOrNull()

                    val windDeg = parseWindDirectionToDegrees(h.windrichting)
                    val windRad = windDeg?.let { Math.toRadians(it) }
                    val windDirSin = windRad?.let { sin(it) } ?: 0.0
                    val windDirCos = windRad?.let { cos(it) } ?: 0.0

                    val sampleWeight = AiConfig.getSampleWeightForSpecies(w.soortid)

                    val fields = listOf(
                        h.tellingid,
                        epoch.toString(),
                        h.telpostid,
                        tempRaw,
                        (tempNumeric?.toString() ?: ""),
                        windRaw,
                        (windNumeric?.toString() ?: ""),
                        (windDeg?.toString() ?: ""),
                        windDirSin.toString(),
                        windDirCos.toString(),
                        h.bewolking.ifEmpty { "" },
                        h.zicht.ifEmpty { "" },
                        h.neerslag.ifEmpty { "" },
                        (refAvgWind?.toString() ?: ""),
                        (refAvgPressure?.toString() ?: ""),
                        daySin.toString(),
                        dayCos.toString(),
                        hourSin.toString(),
                        hourCos.toString(),
                        sampleWeight.toString(),
                        w.soortid,
                        w.aantal
                    )
                    // Ensure each field is properly escaped/quoted so no stray commas or locales break the CSV.
                    val q = fields.map { escapeCsvField(it?.toString()) }
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

