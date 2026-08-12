package com.yvesds.vt5.ai

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * TrainingDataPreparer - Bereidt data uit Room DB voor voor de AI trainer.
 * Gebruikt 21 features voor het On-Device Training model.
 */
class TrainingDataPreparer(private val context: Context) {

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
            val existing = exportDir.findFile(filename)
            val file = existing ?: exportDir.createFile("application/json", filename) ?: return@withContext emptyList()

            context.contentResolver.openOutputStream(file.uri)?.use { out ->
                out.write(json.toString(2).toByteArray(Charsets.UTF_8))
            }

            return@withContext speciesIds
        }
    }

    /**
     * Haalt alle trainingsdata op uit Room en converteert deze naar een lijst van TrainingSamples.
     * Nu met caching voor razendsnelle berekening van de gisteren-factor.
     */
    suspend fun getTrainingDataFromRoom(onProgress: (String, Int, Int) -> Unit): List<Trainer.TrainingSample> = withContext(Dispatchers.IO) {
        val db = VoiceTallyDatabase.getDatabase(context)
        val dao = db.tellingDao()
        
        onProgress("Database scannen...", 5, 100)
        val rawRows = dao.getRawTrainingData()
        val allSpecies = dao.getAllSpeciesIds().sorted()
        
        onProgress("Dagtotalen berekenen voor gisteren-factor...", 7, 100)
        val dailyTotals = dao.getAllDailyTotals().associate { it.dayEpoch to it.count }
        
        val samples = mutableListOf<Trainer.TrainingSample>()
        val total = rawRows.size

        rawRows.forEachIndexed { index, row ->
            if (index % 1000 == 0) {
                val perc = (7 + (index.toFloat() / total * 8)).toInt()
                onProgress("Data voorbereiden... ($index van $total)", perc, 100)
            }

            val labelIndex = allSpecies.indexOf(row.soortid)
            if (labelIndex == -1) return@forEachIndexed

            val features = FloatArray(21)
            val epoch = row.observationTime.toLongOrNull() ?: row.sessionStart.toLongOrNull() ?: 0L
            val zdt = ZonedDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault())
            
            // 1-4: Tijd
            val dayOfYear = zdt.dayOfYear.toDouble()
            val hourOfDay = zdt.hour.toDouble()
            features[0] = sin(2.0 * PI * dayOfYear / 365.25).toFloat()
            features[1] = cos(2.0 * PI * dayOfYear / 365.25).toFloat()
            features[2] = sin(2.0 * PI * hourOfDay / 24.0).toFloat()
            features[3] = cos(2.0 * PI * hourOfDay / 24.0).toFloat()

            // 5-9: Basis Weer
            features[4] = row.temperatuur.replace(',', '.').replace(Regex("[^0-9.\\-]"), "").toFloatOrNull() ?: 15f
            val windDeg = parseWindDirectionToDegrees(row.windrichting) ?: 0.0
            features[5] = sin(Math.toRadians(windDeg)).toFloat()
            features[6] = cos(Math.toRadians(windDeg)).toFloat()
            features[7] = row.windkracht.replace(',', '.').replace(Regex("[^0-9.\\-]"), "").toFloatOrNull() ?: 0f
            features[8] = (row.bewolking.toFloatOrNull() ?: 0f) / 8.0f

            // 10-11: Druk & Trend
            features[9] = row.hpa.toFloatOrNull() ?: 1013f
            features[10] = 0f 

            // 12: Gisteren-Factor (Nu via snelle Cache!)
            val startOfToday = (epoch / 86400) * 86400
            val startOfYesterday = startOfToday - 86400
            val yesterdayCount = dailyTotals[startOfYesterday] ?: 0L
            features[11] = Math.log1p(yesterdayCount.toDouble()).toFloat()

            // 13: Maanfase
            features[12] = calculateMoonPhase(epoch).toFloat()
            // 14: Neerslag
            features[13] = if (row.neerslag.lowercase().contains("regen")) 1f else 0f
            // 15: Locatie Hash
            features[14] = (row.telpostid.hashCode() % 1000) / 1000f

            for (i in 15..20) features[i] = 0f

            samples.add(Trainer.TrainingSample(features, labelIndex))
        }
        
        return@withContext samples
    }

    private fun calculateMoonPhase(epoch: Long): Double {
        val knownNewMoonEpoch = 1704974760L
        val synodicMonthSeconds = 29.530588 * 24 * 3600
        val delta = epoch - knownNewMoonEpoch
        val phase = (delta % synodicMonthSeconds) / synodicMonthSeconds
        return if (phase < 0) phase + 1.0 else phase
    }

    private fun parseWindDirectionToDegrees(s: String?): Double? {
        if (s == null) return null
        val t = s.trim().uppercase(Locale.getDefault())
        if (t.isEmpty()) return null
        t.replace("°", "").toDoubleOrNull()?.let { return it }
        val labels = arrayOf("N","NNO","NO","ONO","O","OZO","ZO","ZZO","Z","ZZW","ZW","WZW","W","WNW","NW","NNW")
        val idx = labels.indexOf(t)
        if (idx >= 0) return idx * 22.5
        val eng = mapOf("N" to 0.0, "NNE" to 22.5, "NE" to 45.0, "ENE" to 67.5, "E" to 90.0, "ESE" to 112.5, "SE" to 135.0, "SSE" to 157.5, "S" to 180.0, "SSW" to 202.5, "SW" to 225.0, "WSW" to 247.5, "W" to 270.0, "WNW" to 292.5, "NW" to 315.0, "NNW" to 337.5)
        eng[t]?.let { return it }
        return null
    }
}
