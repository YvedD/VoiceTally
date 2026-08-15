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
 * AiInferenceEngine - Plan C: Lite-Neural + BSI Gilde Strategie.
 * Nu met strikte uur-controle en biologische ritmes.
 */
object AiInferenceEngine {
    private const val TAG = "AiInference"

    suspend fun getSuggesties(
        context: Context, 
        cur: Current, 
        hourOverride: Int? = null
    ): AiInformatieDialoog.AiSuggesties = withContext(Dispatchers.IO) {
        val db = VoiceTallyDatabase.getDatabase(context)
        val dao = db.tellingDao()

        val cal = Calendar.getInstance()
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val hourOfDay = hourOverride ?: cal.get(Calendar.HOUR_OF_DAY)
        
        val dayStart = dayOfYear - 7
        val dayEnd = dayOfYear + 7

        // 1. Haal historische profielen op (inclusief avgHour)
        val profiles = dao.getSpeciesPhenologyProfile(dayStart, dayEnd)
        if (profiles.isEmpty()) return@withContext getEmptySuggesties(cur)

        // 2. Laad de Neurale Motor
        val modelStore = ModelStore(context)
        val allSpecies = dao.getAllSpeciesIds().sorted()
        val engine = modelStore.loadNeuralEngine(allSpecies.size.coerceAtLeast(1))

        // 3. Bereken factoren
        val nowEpoch = System.currentTimeMillis() / 1000L
        val yesterdayCount = dao.getTotalCountInEpochRange(nowEpoch - 86400, nowEpoch) ?: 0L
        val gisterenFactor = ln(yesterdayCount.toDouble() + 1.0)
        
        val currentWind = WeatherManager.degTo16WindLabel(cur.windDirection10m)
        val currentTemp = (cur.temperature2m ?: 15.0).toFloat()
        val currentPress = (cur.pressureMsl ?: 1013.0).toFloat()

        // 4. Bouw Neurale Input Features
        val features = FloatArray(21)
        features[0] = sin(2.0 * PI * dayOfYear / 365.25).toFloat()
        features[1] = cos(2.0 * PI * dayOfYear / 365.25).toFloat()
        features[2] = sin(2.0 * PI * hourOfDay / 24.0).toFloat()
        features[3] = cos(2.0 * PI * hourOfDay / 24.0).toFloat()
        features[4] = currentTemp
        val windRad = Math.toRadians(cur.windDirection10m ?: 0.0)
        features[5] = sin(windRad).toFloat()
        features[6] = cos(windRad).toFloat()
        features[7] = WeatherManager.msToBeaufort(cur.windSpeed10m).toFloat()
        features[9] = currentPress
        features[11] = gisterenFactor.toFloat()
        
        val neuralProbs = engine.predict(features)

        // 5. Combineer alles per soort
        val scoredSpecies = profiles.map { profile ->
            var score = log10(profile.count.toDouble().coerceAtLeast(1.0))
            
            // A. Biologisch Ritme (DE TIJD CHECK)
            val avgHour = profile.avgHour ?: 10f
            val hourDiff = abs(hourOfDay - avgHour)
            
            // Strikte penalty voor nachtelijke uren bij dagtrekkers
            val isNight = hourOfDay < 5 || hourOfDay >= 22
            val guild = SpeciesGuildMapper.getGuild(profile.soortid)
            
            if (isNight && guild != SpeciesGuildMapper.Guild.PELAGICS && guild != SpeciesGuildMapper.Guild.OTHER) {
                 score *= 0.001 
            } else {
                 // Gaussian time match (breedte van 3 uur)
                 score *= exp(-(hourDiff * hourDiff) / 18.0)
            }

            // B. Weer & Wind
            if (profile.mainWind == currentWind) score *= 1.4
            val tempDiff = abs((profile.avgTemp ?: 15f) - currentTemp)
            score *= exp(-(tempDiff * tempDiff) / 50.0)

            // C. Neurale 'Second Opinion'
            val speciesIdx = allSpecies.indexOf(profile.soortid)
            if (speciesIdx != -1) {
                score *= (1.0 + neuralProbs[speciesIdx] * 12.0)
            }

            // D. De 'Krenten' boost
            if (profile.count < 1000) score *= 1.8

            ScoredSpecies(profile.soortid, score)
        }

        // 6. Gilde-selectie
        val guildWinners = scoredSpecies.groupBy { SpeciesGuildMapper.getGuild(it.soortid) }
            .mapValues { (_, list) -> list.maxByOrNull { it.score } }

        val maxGlobalScore = scoredSpecies.maxOfOrNull { it.score } ?: 1.0
        val finalResults = mutableListOf<AiInformatieDialoog.GuildSuggestie>()

        SpeciesGuildMapper.Guild.entries.forEach { guild ->
            val winner = guildWinners[guild]
            if (winner != null) {
                val prob = (min(0.98, winner.score / maxGlobalScore) * 100).toInt()
                if (prob >= 15) {
                    val name = SpeciesNameResolver.getName(context, winner.soortid)
                    finalResults.add(AiInformatieDialoog.GuildSuggestie(guild.displayName, name, prob))
                }
            }
        }

        val result = AiInformatieDialoog.AiSuggesties(
            guildResults = finalResults.sortedByDescending { it.kans },
            weerBeschrijving = "$currentWind-wind / ${WeatherManager.msToBeaufort(cur.windSpeed10m)}bft"
        )

        logForecast(context, db, cur, hourOfDay)
        return@withContext result
    }

    private fun getEmptySuggesties(cur: Current): AiInformatieDialoog.AiSuggesties {
        return AiInformatieDialoog.AiSuggesties(emptyList(), "Geen data")
    }

    private suspend fun logForecast(context: Context, db: VoiceTallyDatabase, cur: Current, hour: Int) {
        val conditionJson = org.json.JSONObject().apply {
            put("temp", cur.temperature2m)
            put("wind", cur.windSpeed10m)
            put("h", hour)
        }.toString()
        db.tellingDao().insertAiLog(AiLog(tellingid = "auto", type = "bsi_neural_guild", requestContext = conditionJson))
    }

    private data class ScoredSpecies(val soortid: String, val score: Double)
}
