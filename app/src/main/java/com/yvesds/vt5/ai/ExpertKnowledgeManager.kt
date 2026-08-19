package com.yvesds.vt5.ai

import android.content.Context
import android.util.Log
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.dao.PeakDayRow
import com.yvesds.vt5.utils.weather.WeatherManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * ExpertKnowledgeManager - Voert de retroactieve analyse uit van vogelpieken.
 * Scant de database, haalt historisch weer op voor 21 locaties en bouwt vingerafdrukken.
 */
class ExpertKnowledgeManager(private val context: Context) {
    private val TAG = "ExpertKnowledge"
    private val db = VoiceTallyDatabase.getDatabase(context)
    private val modelStore = ModelStore(context)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * Start de analyse van historische pieken en bouwt de Knowledge Base.
     */
    suspend fun analyzeHistoricalPeaks(onProgress: (String, Int, Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress("Historische pieken identificeren...", 10, 100)
            val guildSignatures = mutableListOf<GuildPeakSignature>()

            // We analyseren de belangrijkste wetenschappelijke gilden
            val guildsToAnalyze = listOf(
                SpeciesGuildMapper.Guild.RAPTORS_ACTIVE,
                SpeciesGuildMapper.Guild.HERONS,
                SpeciesGuildMapper.Guild.STORKS,
                SpeciesGuildMapper.Guild.PASSERINES,
                SpeciesGuildMapper.Guild.SHOREBIRDS
            )

            for ((gIdx, guild) in guildsToAnalyze.withIndex()) {
                val speciesIds = getSpeciesIdsForGuild(guild)
                if (speciesIds.isEmpty()) continue

                onProgress("Topdagen analyseren voor ${guild.displayName}...", 20 + gIdx * 20, 100)
                
                // Zoek de 3 absolute top-dagen in de Room DB voor deze gilde
                val peakDays = db.tellingDao().getPeakDaysForSpecies(speciesIds, limit = 3)
                
                if (peakDays.isNotEmpty()) {
                    val snapshots = mutableListOf<RegionalWeatherSnapshot>()
                    
                    for (peak in peakDays) {
                        // Voor elke piekdag kijken we naar het weer op de 5 meest relevante noordelijke bronlocaties
                        for (ref in AiConfig.REFERENCE_POINTS.take(5)) {
                            fetchRetroWeather(ref, peak.dayEpoch)?.let { snapshots.addAll(it) }
                        }
                    }
                    
                    // Bereken gemiddelde vingerafdruk voor deze gilde
                    if (snapshots.isNotEmpty()) {
                        guildSignatures.add(GuildPeakSignature(
                            guildName = guild.displayName,
                            month = 0, 
                            averageConditions = snapshots
                        ))
                    }
                }
            }

            // 7. Zelf-ontdekkende Krenten-motor (Gefilterd op VisMig relevantie)
            onProgress("Zeldzaamheden identificeren uit database...", 90, 100)
            
            // Haal de dynamische drempelwaarde op uit de instellingen
            val threshold = com.yvesds.vt5.core.opslag.AppDataStore.getKrentenThreshold(context)
            
            // Laad serverdata voor filters
            val snapshot = try { com.yvesds.vt5.features.serverdata.model.ServerDataCache.getOrLoad(context) } catch (_: Exception) { null }
            
            val globalMassa = db.tellingDao().getGlobalSpeciesMassa()
            val discoveredKrenten = globalMassa
                .filter { it.observationCount in 1..threshold }
                .filter { p ->
                    val species = snapshot?.speciesById?.get(p.soortid)
                    val name = species?.soortnaam?.lowercase() ?: ""
                    val latin = species?.latin
                    
                    // Filter 1: Geen ruis in namen (spec, /, onbekend)
                    val isCleanName = !name.contains("spec.") && 
                                     !name.contains("/") && 
                                     !name.contains("onbekend")
                    
                    // Filter 2: Alleen vogels (Gilde mag niet OTHER zijn)
                    val guild = SpeciesGuildMapper.getGuildByLatin(latin)
                    val isBird = guild != SpeciesGuildMapper.Guild.OTHER && 
                                guild != SpeciesGuildMapper.Guild.UNCLASSIFIED_BIRDS
                    
                    isCleanName && isBird
                }
                .map { it.soortid }
            
            Log.i(TAG, "AI heeft ${discoveredKrenten.size} zuivere vogel-krenten ontdekt.")

            // Sla de kennis op in SAF (beide formaten via ModelStore)
            val kb = ExpertKnowledgeBase(guildSignatures, discoveredKrenten, System.currentTimeMillis())
            modelStore.saveExpertKnowledge(kb)

            onProgress("Expert Knowledge Base bijgewerkt!", 100, 100)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Peak analysis failed: ${e.message}", e)
            false
        }
    }

    private suspend fun fetchRetroWeather(ref: AiConfig.RefPoint, peakEpoch: Long): List<RegionalWeatherSnapshot>? {
        val date = Instant.ofEpochSecond(peakEpoch).atZone(ZoneId.of("UTC")).toLocalDate()
        val startDate = date.minusDays(3).toString()
        val endDate = date.toString()
        
        val url = "https://archive-api.open-meteo.com/v1/archive" +
                "?latitude=${ref.lat}&longitude=${ref.lon}" +
                "&start_date=$startDate&end_date=$endDate" +
                "&hourly=temperature_2m,wind_speed_10m,wind_direction_10m,pressure_msl" +
                "&wind_speed_unit=ms&timezone=UTC"

        return try {
            val req = Request.Builder().url(url).build()
            VT5App.http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val data = json.decodeFromString<OpenMeteoArchiveResponse>(body)
                val hourly = data.hourly
                
                val results = mutableListOf<RegionalWeatherSnapshot>()
                val daysBack = listOf(-1, -2, -3)
                val daylightHours = listOf(6, 8, 10, 12, 14, 16, 18) // Elk even uur overdag
                
                for (d in daysBack) {
                    for (h in daylightHours) {
                        // Index berekening: (Aantal_dagen * 24) + target_hour + offset
                        // We hebben 4 dagen opgevraagd (3 dagen terug + piekdag zelf)
                        val idx = (3 + d) * 24 + h 
                        
                        if (idx in hourly.time.indices) {
                            results.add(RegionalWeatherSnapshot(
                                locationName = ref.name,
                                relativeHour = (d * 24) + h,
                                avgTemp = hourly.temperature_2m?.getOrNull(idx)?.toFloat() ?: 15f,
                                avgWindDir = hourly.wind_direction_10m?.getOrNull(idx) ?: 0.0,
                                avgWindSpeed = hourly.wind_speed_10m?.getOrNull(idx)?.toFloat() ?: 0f,
                                avgPressure = hourly.pressure_msl?.getOrNull(idx)?.toFloat() ?: 1013f
                            ))
                        }
                    }
                }
                results
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getSpeciesIdsForGuild(guild: SpeciesGuildMapper.Guild): List<String> {
        return when(guild) {
            SpeciesGuildMapper.Guild.RAPTORS_ACTIVE -> listOf("96", "90", "113", "100") 
            SpeciesGuildMapper.Guild.HERONS -> listOf("27", "31")
            SpeciesGuildMapper.Guild.STORKS -> listOf("33", "32")
            SpeciesGuildMapper.Guild.PASSERINES -> listOf("401", "307", "318", "279")
            SpeciesGuildMapper.Guild.SHOREBIRDS -> listOf("148", "156", "145", "174")
            else -> emptyList()
        }
    }

    @kotlinx.serialization.Serializable
    private data class OpenMeteoArchiveResponse(val hourly: OpenMeteoHourlyData)

    @kotlinx.serialization.Serializable
    private data class OpenMeteoHourlyData(
        val time: List<String>,
        val temperature_2m: List<Double?>,
        val wind_speed_10m: List<Double?>,
        val wind_direction_10m: List<Double?>,
        val pressure_msl: List<Double?>
    )
}
