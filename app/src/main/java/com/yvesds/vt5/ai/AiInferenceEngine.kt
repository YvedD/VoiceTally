package com.yvesds.vt5.ai

import android.content.Context
import android.util.Log
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.entities.AiLog
import com.yvesds.vt5.core.database.ui.SpeciesNameResolver
import com.yvesds.vt5.utils.weather.Current
import com.yvesds.vt5.utils.weather.WeatherManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.*

/**
 * AiInferenceEngine - Plan B: Bio-Statistische Intelligentie (BSI).
 * Gebruikt gewogen patroonherkenning op basis van historische piektrek en weersomstandigheden.
 */
object AiInferenceEngine {
    private const val TAG = "BsiInference"

    suspend fun getSuggesties(context: Context, cur: Current): AiInformatieDialoog.AiSuggesties = withContext(Dispatchers.IO) {
        val db = VoiceTallyDatabase.getDatabase(context)
        val dao = db.tellingDao()

        // 1. Bepaal het 15-dagen venster (Vandaag +/- 7 dagen)
        val cal = Calendar.getInstance()
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val dayStart = dayOfYear - 7
        val dayEnd = dayOfYear + 7

        // 2. Haal historische piektrek-profielen op binnen dit venster (Deep Data Matching)
        val profiles = dao.getSpeciesPhenologyProfile(dayStart, dayEnd)
        if (profiles.isEmpty()) return@withContext getEmptySuggesties(cur)

        // 3. Bereken Gisteren-factor (Persistentie)
        val nowEpoch = System.currentTimeMillis() / 1000L
        val yesterdayCount = dao.getTotalCountInEpochRange(nowEpoch - 86400, nowEpoch) ?: 0L
        val gisterenFactor = ln(yesterdayCount.toDouble() + 1.0)

        // 4. Match profielen met de huidige omstandigheden
        val scoredSpecies = profiles.map { profile ->
            var score = log10(profile.count.toDouble().coerceAtLeast(1.0))

            // A. Wind Match
            val currentWind = WeatherManager.degTo16WindLabel(cur.windDirection10m)
            if (profile.mainWind == currentWind) score *= 1.4

            // B. Temperatuur Match (Gaussian similarity)
            val tempDiff = abs((profile.avgTemp ?: 15f) - (cur.temperature2m ?: 15.0).toFloat())
            score *= exp(-(tempDiff * tempDiff) / 50.0) // 50 is de variantie

            // C. Luchtdruk Match
            val pressDiff = abs((profile.avgPressure ?: 1013f) - (cur.pressureMsl ?: 1013.0).toFloat())
            score *= (1.0 / (1.0 + pressDiff * 0.02))

            // D. Gisteren Bonus
            if (gisterenFactor > 1.0) score *= 1.1

            ScoredSpecies(profile.soortid, score)
        }.sortedByDescending { it.score }

        // 5. Resultaat voorbereiden voor de UI
        val maxScore = scoredSpecies.firstOrNull()?.score ?: 1.0
        val topSuggesties = scoredSpecies.take(8).map {
            val name = SpeciesNameResolver.getName(context, it.soortid)
            val probability = (min(0.99, it.score / maxScore) * 100).toInt()
            AiInformatieDialoog.Suggestie(name, probability)
        }

        val windLabel = WeatherManager.degTo16WindLabel(cur.windDirection10m)
        val bft = WeatherManager.msToBeaufort(cur.windSpeed10m)

        val result = AiInformatieDialoog.AiSuggesties(
            tijdstipSuggesties = topSuggesties.take(3),
            weerBeschrijving = "$windLabel-wind / ${bft}bft",
            weerSuggesties = topSuggesties.drop(3).take(3),
            periodeSuggesties = topSuggesties.take(2)
        )

        logForecast(context, db, cur, result)
        return@withContext result
    }

    private fun getEmptySuggesties(cur: Current): AiInformatieDialoog.AiSuggesties {
        val windLabel = WeatherManager.degTo16WindLabel(cur.windDirection10m)
        val bft = WeatherManager.msToBeaufort(cur.windSpeed10m)
        return AiInformatieDialoog.AiSuggesties(emptyList(), "$windLabel-wind / ${bft}bft", emptyList(), emptyList())
    }

    private suspend fun logForecast(context: Context, db: VoiceTallyDatabase, cur: Current, result: AiInformatieDialoog.AiSuggesties) {
        val tellingId = context.getSharedPreferences("vt5_prefs", Context.MODE_PRIVATE).getString("pref_telling_id", "unknown") ?: "unknown"
        val conditionJson = org.json.JSONObject().apply {
            put("temp", cur.temperature2m)
            put("wind", cur.windSpeed10m)
            put("pressure", cur.pressureMsl)
        }.toString()
        
        db.tellingDao().insertAiLog(AiLog(
            tellingid = tellingId,
            type = "bsi_forecast",
            requestContext = conditionJson,
            suggestions = ""
        ))
    }

    private data class ScoredSpecies(val soortid: String, val score: Double)
}
