package com.yvesds.vt5.ai

import android.content.Context
import android.util.Log
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.entities.AiLog
import com.yvesds.vt5.core.database.ui.SpeciesNameResolver
import com.yvesds.vt5.utils.weather.Current
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.*

/**
 * AiInferenceEngine - Powering the AI Information Dialog.
 */
object AiInferenceEngine {
    private const val TAG = "AiInferenceEngine"
    private var interpreter: Interpreter? = null
    private var loadedLabels: List<String> = emptyList()

    suspend fun getSuggesties(context: Context, cur: Current): AiInformatieDialoog.AiSuggesties = withContext(Dispatchers.IO) {
        ensureModelLoaded(context)

        if (interpreter != null && loadedLabels.isNotEmpty()) {
            return@withContext getTflitePredicties(context, cur)
        }

        // Fallback: Real Database Statistics
        val db = VoiceTallyDatabase.getDatabase(context)
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        
        val tijdstipList = getTopSpeciesByHour(context, db, currentHour, currentMonth)
        val windLabel = com.yvesds.vt5.utils.weather.WeatherManager.degTo16WindLabel(cur.windDirection10m)
        val bft = com.yvesds.vt5.utils.weather.WeatherManager.msToBeaufort(cur.windSpeed10m)
        val weerList = getTopSpeciesByWeather(context, db, windLabel, currentMonth)
        val periodeList = getTopSpeciesByMonth(context, db, currentMonth)

        val result = AiInformatieDialoog.AiSuggesties(
            tijdstipSuggesties = tijdstipList,
            weerBeschrijving = "$windLabel-wind / ${bft}bft",
            weerSuggesties = weerList,
            periodeSuggesties = periodeList
        )
        
        // Log the forecast for later evaluation
        logForecast(context, db, cur, result)

        return@withContext result
    }

    private suspend fun logForecast(context: Context, db: VoiceTallyDatabase, cur: Current, result: AiInformatieDialoog.AiSuggesties) {
        val tellingId = context.getSharedPreferences("vt5_prefs", Context.MODE_PRIVATE).getString("pref_telling_id", "unknown") ?: "unknown"
        val conditionJson = org.json.JSONObject().apply {
            put("temp", cur.temperature2m)
            put("wind", cur.windSpeed10m)
            put("wind_deg", cur.windDirection10m)
        }.toString()
        
        val suggestionsJson = org.json.JSONObject().apply {
            val list = org.json.JSONArray()
            (result.tijdstipSuggesties + result.weerSuggesties + result.periodeSuggesties).distinctBy { it.soortnaam }.forEach {
                val item = org.json.JSONObject()
                item.put("name", it.soortnaam)
                item.put("prob", it.kans)
                list.put(item)
            }
            put("items", list)
        }.toString()

        db.tellingDao().insertAiLog(AiLog(
            tellingid = tellingId,
            type = "metadata",
            requestContext = conditionJson,
            suggestions = suggestionsJson
        ))
    }

    private fun ensureModelLoaded(context: Context) {
        if (interpreter != null) return
        try {
            val modelStore = ModelStore(context)
            val modelDir = modelStore.getModelDir() ?: return
            val modelFile = modelDir.findFile("personal_migration_model.tflite") ?: modelDir.findFile("training_model.tflite") ?: return
            val labelsFile = modelDir.findFile("personal_migration_model.labels.json") ?: modelDir.findFile("training_model.labels.json") ?: return

            context.contentResolver.openFileDescriptor(modelFile.uri, "r")?.use { pfd ->
                val fileChannel = FileInputStream(pfd.fileDescriptor).channel
                val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
                interpreter = Interpreter(modelBuffer)
            }

            context.contentResolver.openInputStream(labelsFile.uri)?.use { input ->
                val jsonStr = input.bufferedReader().readText()
                val json = org.json.JSONObject(jsonStr)
                val arr = json.optJSONArray("classes")
                val list = mutableListOf<String>()
                if (arr != null) {
                    for (i in 0 until arr.length()) list.add(arr.getString(i))
                }
                loadedLabels = list
            }
        } catch (_: Exception) {}
    }

    private suspend fun getTflitePredicties(context: Context, cur: Current): AiInformatieDialoog.AiSuggesties {
        val inter = interpreter ?: return getEmptySuggesties(cur)
        
        // 1. Calculate all 19 features in exact same order as training
        val cal = Calendar.getInstance()
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR).toDouble()
        val hourOfDay = cal.get(Calendar.HOUR_OF_DAY).toDouble()
        val month = cal.get(Calendar.MONTH) + 1
        
        val daySin = Math.sin(2.0 * Math.PI * dayOfYear / 365.25)
        val dayCos = Math.cos(2.0 * Math.PI * dayOfYear / 365.25)
        val hourSin = Math.sin(2.0 * Math.PI * hourOfDay / 24.0)
        val hourCos = Math.cos(2.0 * Math.PI * hourOfDay / 24.0)
        
        val moonPhase = calculateMoonPhase(System.currentTimeMillis() / 1000L)
        val windChill = calculateWindChill(cur.temperature2m ?: 15.0, cur.windSpeed10m ?: 5.0)
        
        // Fetch ref weather (same as training)
        val refs = if (month <= 6) AiConfig.SPRING_SOUTH_REFS else AiConfig.AUTUMN_NORTH_REFS
        var refAvgWind = 5.0
        var refAvgPressure = 1013.0
        var pressureTrend = 0.0
        try {
            val refWeathers = refs.mapNotNull { pair ->
                runCatching { AiWeatherService.fetchContextualWeather(context, pair.first, pair.second) }.getOrNull()
            }
            if (refWeathers.isNotEmpty()) {
                refAvgWind = refWeathers.mapNotNull { it.windSpeed }.average()
                refAvgPressure = refWeathers.mapNotNull { it.pressure }.average()
                pressureTrend = refWeathers.mapNotNull { it.pressureTrend }.average()
            }
        } catch (_: Exception) {}

        // Yesterday's count
        val db = VoiceTallyDatabase.getDatabase(context)
        val nowEpoch = System.currentTimeMillis() / 1000L
        val yesterdayCount = db.tellingDao().sumCountsInPeriod((nowEpoch - 86400).toString(), nowEpoch.toString()) ?: 0

        // Wind vectors
        val windRad = cur.windDirection10m?.let { Math.toRadians(it) }
        val windDirSin = windRad?.let { Math.sin(it) } ?: 0.0
        val windDirCos = windRad?.let { Math.cos(it) } ?: 0.0

        // Features list (Order MUST match train_model.py)
        val features = floatArrayOf(
            (cur.temperature2m ?: 15.0).toFloat(),
            (cur.windSpeed10m ?: 5.0).toFloat(),
            windDirSin.toFloat(),
            windDirCos.toFloat(),
            (cur.cloudCover ?: 50.0).toFloat(),
            (cur.visibility ?: 10000.0).toFloat(),
            (cur.precipitation ?: 0.0).toFloat(),
            refAvgWind.toFloat(),
            refAvgPressure.toFloat(),
            daySin.toFloat(),
            dayCos.toFloat(),
            hourSin.toFloat(),
            hourCos.toFloat(),
            moonPhase.toFloat(),
            windChill.toFloat(),
            pressureTrend.toFloat(),
            yesterdayCount.toFloat(),
            0.0f, // is_rare (default 0 during inference as we don't know yet)
            1.0f  // label_count (default 1)
        )

        val inputBuffer = ByteBuffer.allocateDirect(1 * features.size * 4).order(ByteOrder.nativeOrder())
        features.forEach { inputBuffer.putFloat(it) }
        
        val outputBuffer = ByteBuffer.allocateDirect(1 * loadedLabels.size * 4).order(ByteOrder.nativeOrder())
        inter.run(inputBuffer, outputBuffer)
        
        outputBuffer.rewind()
        val results = FloatArray(loadedLabels.size)
        outputBuffer.asFloatBuffer().get(results)
        
        val sortedIndices = results.indices.sortedByDescending { results[it] }
        val topSuggesties = sortedIndices.take(5).map { index ->
            AiInformatieDialoog.Suggestie(SpeciesNameResolver.getName(context, loadedLabels.getOrNull(index) ?: "Unknown"), (results[index] * 100).toInt())
        }

        val windLabel = com.yvesds.vt5.utils.weather.WeatherManager.degTo16WindLabel(cur.windDirection10m)
        val bft = com.yvesds.vt5.utils.weather.WeatherManager.msToBeaufort(cur.windSpeed10m)

        return AiInformatieDialoog.AiSuggesties(
            tijdstipSuggesties = topSuggesties.take(3),
            weerBeschrijving = "$windLabel-wind / ${bft}bft",
            weerSuggesties = topSuggesties.drop(3).take(2),
            periodeSuggesties = topSuggesties.take(2)
        )
    }

    private fun calculateMoonPhase(epoch: Long): Double {
        val knownNewMoonEpoch = 1704974760L
        val synodicMonthSeconds = 29.530588 * 24 * 3600
        val delta = epoch - knownNewMoonEpoch
        val phase = (delta % synodicMonthSeconds) / synodicMonthSeconds
        return if (phase < 0) phase + 1.0 else phase
    }

    private fun calculateWindChill(temp: Double, windMs: Double): Double {
        val windKmh = windMs * 3.6
        if (temp > 10.0 || windKmh < 4.8) return temp
        return 13.12 + 0.6215 * temp - 11.37 * Math.pow(windKmh, 0.16) + 0.3965 * temp * Math.pow(windKmh, 0.16)
    }

    private fun getEmptySuggesties(cur: Current): AiInformatieDialoog.AiSuggesties {
        val windLabel = com.yvesds.vt5.utils.weather.WeatherManager.degTo16WindLabel(cur.windDirection10m)
        val bft = com.yvesds.vt5.utils.weather.WeatherManager.msToBeaufort(cur.windSpeed10m)
        return AiInformatieDialoog.AiSuggesties(emptyList(), "$windLabel-wind / ${bft}bft", emptyList(), emptyList())
    }

    private suspend fun getTopSpeciesByHour(context: Context, db: VoiceTallyDatabase, hour: Int, month: Int): List<AiInformatieDialoog.Suggestie> {
        return try {
            val results = db.tellingDao().getTopSpeciesByHour(hour, month)
            val total = results.sumOf { it.count }.toDouble()
            results.map { 
                val perc = if (total > 0) ((it.count / total) * 100).toInt() else 0
                AiInformatieDialoog.Suggestie(SpeciesNameResolver.getName(context, it.soortid), perc)
            }
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun getTopSpeciesByWeather(context: Context, db: VoiceTallyDatabase, wind: String, month: Int): List<AiInformatieDialoog.Suggestie> {
        return try {
            val results = db.tellingDao().getTopSpeciesByWind(wind, month)
            val total = results.sumOf { it.count }.toDouble()
            results.map { 
                val perc = if (total > 0) ((it.count / total) * 100).toInt() else 0
                AiInformatieDialoog.Suggestie(SpeciesNameResolver.getName(context, it.soortid), perc)
            }
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun getTopSpeciesByMonth(context: Context, db: VoiceTallyDatabase, month: Int): List<AiInformatieDialoog.Suggestie> {
        return try {
            val results = db.tellingDao().getTopSpeciesByMonth(month)
            val total = results.sumOf { it.count }.toDouble()
            results.map { 
                val perc = if (total > 0) ((it.count / total) * 100).toInt() else 0
                AiInformatieDialoog.Suggestie(SpeciesNameResolver.getName(context, it.soortid), perc)
            }
        } catch (_: Exception) { emptyList() }
    }
}
