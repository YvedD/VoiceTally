package com.yvesds.vt5.ai

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import android.util.Log
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

    /**
     * Bouwt dezelfde 21-features vector als gebruikt tijdens training, maar dan voor een
     * arbitraire context (bijvoorbeeld tijdens een prognose-aanvraag).
     * Alle waarden zijn optioneel en vallen terug op veilige defaults.
     */
    fun buildFeatureVectorForContext(
        epoch: Long,
        telpostId: String?,
        temperature: Double?,
        windDeg: Double?,
        windForce: Double?,
        cloudCover: Double?,
        hpa: Double?,
        precipitationFlag: Boolean?
    ): FloatArray {
        val features = FloatArray(21)
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault())
        val dayOfYear = zdt.dayOfYear.toDouble()
        val hourOfDay = zdt.hour.toDouble()

        features[0] = sin(2.0 * PI * dayOfYear / 365.25).toFloat()
        features[1] = cos(2.0 * PI * dayOfYear / 365.25).toFloat()
        features[2] = sin(2.0 * PI * hourOfDay / 24.0).toFloat()
        features[3] = cos(2.0 * PI * hourOfDay / 24.0).toFloat()

        features[4] = temperature?.toFloat() ?: 15f
        val wdeg = windDeg ?: 0.0
        features[5] = sin(Math.toRadians(wdeg)).toFloat()
        features[6] = cos(Math.toRadians(wdeg)).toFloat()
        features[7] = windForce?.toFloat() ?: 0f
        features[8] = (cloudCover?.toFloat() ?: 0f) / 8.0f

        features[9] = hpa?.toFloat() ?: 1013f
        features[10] = 0f

        // Geen betrouwbare gisteren-factor beschikbaar in live-call -> 0
        features[11] = 0f

        features[12] = calculateMoonPhase(epoch).toFloat()
        features[13] = if (precipitationFlag == true) 1f else 0f

        val hash = telpostId?.hashCode()?.let { kotlin.math.abs(it) % 1000 } ?: 0
        features[14] = hash / 1000f

        for (i in 15..20) features[i] = 0f
        return features
    }

    /**
     * Compute per-species sample weights from 'daily_analysis' (teldag verslagen).
     * Returns a map speciesId -> weight (>= 1.0). The heuristic used here counts how many
     * of the recent analysed days the species was actually seen and normalizes that count
     * to the most-observed species in the window. This produces a multiplicative weight in
     * range [1.0, AiConfig.DAILY_ANALYSIS_WEIGHT_MAX].
     */
    suspend fun computeSpeciesWeightsFromDailyAnalysis(lookbackDays: Int = AiConfig.DAILY_ANALYSIS_LOOKBACK_DAYS): Map<String, Float> = withContext(Dispatchers.IO) {
        val db = VoiceTallyDatabase.getDatabase(context)
        val dao = db.tellingDao()
        try {
            val allDays = dao.getAllAnalyzedDays()
            if (allDays.isEmpty()) return@withContext emptyMap()

            val now = System.currentTimeMillis() / 1000L
            val startOfToday = (now / 86400L) * 86400L
            val startEpoch = startOfToday - lookbackDays.toLong() * 86400L

            val recent = allDays.filter { it.dayEpoch >= startEpoch }
            if (recent.isEmpty()) return@withContext emptyMap()

            val seenCounts = mutableMapOf<String, Int>()
            for (d in recent) {
                val analysis = dao.getDailyAnalysis(d.dayEpoch) ?: continue
                try {
                    val arr = JSONArray(analysis.resultsJson)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val sid = obj.optString("id", "")
                        if (sid.isBlank()) continue
                        val isSeen = obj.optBoolean("isSeen", obj.optInt("count", 0) > 0)
                        if (isSeen) seenCounts[sid] = (seenCounts[sid] ?: 0) + 1
                    }
                } catch (je: Exception) {
                    Log.w("TrainingDataPreparer", "Invalid daily_analysis JSON for day ${d.dayEpoch}")
                }
            }

            val maxSeen = seenCounts.values.maxOrNull() ?: 0
            if (maxSeen <= 0) return@withContext emptyMap()

            val weights = mutableMapOf<String, Float>()
            val span = AiConfig.DAILY_ANALYSIS_WEIGHT_MAX - 1.0f
            for ((sid, cnt) in seenCounts) {
                val norm = cnt.toFloat() / maxSeen.toFloat()
                val w = 1.0f + norm * span
                weights[sid] = w.coerceAtLeast(1.0f)
            }
            try {
                val top = weights.entries.sortedByDescending { it.value }.take(6).joinToString { "${it.key}:${String.format(Locale.US, "%.2f", it.value)}" }
                Log.i("TrainingDataPreparer", "Computed species weights from ${recent.size} days; top=$top")
            } catch (_: Exception) { }
            return@withContext weights
        } catch (e: Exception) {
            Log.w("TrainingDataPreparer", "Failed to compute species weights: ${e.message}")
            return@withContext emptyMap()
        }
    }
}
