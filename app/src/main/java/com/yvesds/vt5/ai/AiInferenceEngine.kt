package com.yvesds.vt5.ai

import android.content.Context
import android.util.Log
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.entities.AiLog
import com.yvesds.vt5.core.database.entities.TelpostLocatiesRoot
import com.yvesds.vt5.core.database.ui.SpeciesNameResolver
import com.yvesds.vt5.core.opslag.EffortManager
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import com.yvesds.vt5.utils.weather.Current
import com.yvesds.vt5.utils.weather.WeatherManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.math.*

/**
 * AiInferenceEngine - Expert Deep Diagnostic & Live Corridor Edition.
 * Nu met Zelf-ontdekkende Krenten op basis van de ExpertKnowledgeBase.
 */
object AiInferenceEngine {
    private const val TAG = "AiInference"

    suspend fun getSuggesties(
        context: Context, 
        cur: Current, 
        hourOverride: Int? = null,
        providedRegBoost: Double? = null
    ): AiInformatieDialoog.AiSuggesties = withContext(Dispatchers.IO) {
        Log.i(TAG, "=========================================================")
        Log.i(TAG, "START SCIENTIFIC AI ANALYSIS")
        
        val db = VoiceTallyDatabase.getDatabase(context)
        val dao = db.tellingDao()
        val saf = SaFStorageHelper(context)
        
        // Laad alle AI Kennis (JSON of Binair met Auto-Sync)
        val snapshot = try { ServerDataCache.getOrLoad(context) } catch (_: Exception) { null }
        val modelStore = ModelStore(context)
        val expertKB = loadExpertKnowledge(modelStore)

        val cal = Calendar.getInstance()
        val currentHour = hourOverride ?: cal.get(Calendar.HOUR_OF_DAY)
        cal.set(Calendar.HOUR_OF_DAY, currentHour); cal.set(Calendar.MINUTE, 0)
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        
        val loc = WeatherManager.getLastKnownLocation(context)
        val lat = loc?.latitude ?: 51.0
        val lon = loc?.longitude ?: 3.0
        
        // BEPAAL CLUSTER GEANKERD AAN DE HOOFDTELPOST (Eerste in JSON)
        val cluster = withContext(Dispatchers.IO) {
            val jsonStr = saf.readServerDataFile("telpost_locaties.json") ?: "{}"
            val root = VT5App.json.decodeFromString<TelpostLocatiesRoot>(jsonStr)
            val primarySite = root.locaties.firstOrNull()
            if (primarySite != null) {
                root.locaties.filter { calculateDistance(primarySite.latitude, primarySite.longitude, it.latitude, it.longitude) <= 35.0 }
                    .map { it.telpostid }
            } else {
                getLocalSiteCluster(saf, lat, lon)
            }
        }
        
        val phase = SolarTimeEngine.getSolarPhase(lat, lon, cal)

        // 1. LIVE REGIONALE CORRIDOR CHECK (Tenzij boost al gegeven is)
        val regBoost = providedRegBoost ?: getLiveCorridorBoost(month)

        // 2. Data ophalen (Nu met de persistente Giga-Baseline)
        val effortHours = EffortManager.getClusterEffortHours(context, cluster ?: emptyList())
        val profiles = dao.getSpeciesPhenologyProfile(dayOfYear - 10, dayOfYear + 10, cluster ?: emptyList(), if (cluster == null) 0 else 1)
        
        val clusterIndices = if (cluster != null) {
            dao.getSpeciesGigaBaseline(cluster, effortHours).associateBy { it.soortid }
        } else emptyMap()

        val currentWindDeg = cur.windDirection10m ?: 0.0
        val currentWindLabel = WeatherManager.degTo16WindLabel(currentWindDeg)
        val currentTemp = (cur.temperature2m ?: 15.0).toFloat()

        // 3. Score berekening
        val scoredList = profiles.mapNotNull { p ->
            val speciesData = snapshot?.speciesById?.get(p.soortid)
            val latin = speciesData?.latin
            val name = speciesData?.soortnaam ?: SpeciesNameResolver.getName(context, p.soortid)
            
            if (name.lowercase().contains("spec.") || 
                name.lowercase().contains("onbekend") || 
                name.contains("/")) return@mapNotNull null
            
            val guild = SpeciesGuildMapper.getGuildByLatin(latin)
            if (guild == SpeciesGuildMapper.Guild.OTHER) return@mapNotNull null
            val strategy = guild.strategy
            
            // F1: Massa (Log)
            val fMassaRaw = log10(p.count.toDouble().coerceAtLeast(1.0))
            val fMassa = 1.0 + (fMassaRaw * 0.4)
            
            // F2: Harde Wind-DNA Match (Kwadratische afstraffing bij mismatch)
            val histWindDeg = parseWindLabelToDegrees(p.mainWind) ?: currentWindDeg
            val diffRad = Math.toRadians(currentWindDeg - histWindDeg)
            val fWind = (cos(diffRad / 2.0).pow(4.0) * 1.8) + 0.1
            
            // F3: Special / Remarkable / Discovery
            var fSpecial = 1.0
            if (p.isRemarkable == 1) fSpecial = 4.0
            else if (expertKB?.discoveredKrenten?.contains(p.soortid) == true) fSpecial = 2.5
            
            // F4: Tijd & Strategie
            var fTime = 1.0
            val bft = WeatherManager.msToBeaufort(cur.windSpeed10m)
            
            when (strategy) {
                SpeciesGuildMapper.FlightStrategy.THERMAL -> {
                    if (currentHour < 9 || currentHour > 18 || phase == SolarTimeEngine.SolarPhase.NIGHT || bft >= 6) fTime = 0.0001
                    else fTime = 0.5 + ((currentTemp - 10.0).coerceIn(0.1, 10.0) / 10.0)
                }
                SpeciesGuildMapper.FlightStrategy.ACTIVE -> {
                    if (phase == SolarTimeEngine.SolarPhase.NIGHT && guild != SpeciesGuildMapper.Guild.PELAGICS) fTime = 0.01 
                    else fTime = exp(-(abs(currentHour - (p.avgHour ?: 10f)) * abs(currentHour - (p.avgHour ?: 10f))) / 40.0)
                }
                SpeciesGuildMapper.FlightStrategy.VISMIG -> {
                    if (phase == SolarTimeEngine.SolarPhase.NIGHT) fTime = 0.0001
                    else fTime = exp(-(abs(currentHour - (p.avgHour ?: 08f)) * abs(currentHour - (p.avgHour ?: 08f))) / 25.0)
                }
            }

            // F5: Local Wind Gatekeeper (The Veto)
            var fGatekeeper = 1.0
            val isOffShore = currentWindLabel in listOf("O","OZO","ZO","ZZO","Z")
            val isOnShore = currentWindLabel in listOf("NW","WNW","W","ZW","NNW")
            
            if (guild == SpeciesGuildMapper.Guild.PELAGICS) {
                if (isOffShore) fGatekeeper = 0.001 
                else if (isOnShore) fGatekeeper = 2.0 
            } else if (guild == SpeciesGuildMapper.Guild.RAPTORS_THERMAL || guild == SpeciesGuildMapper.Guild.RAPTORS_ACTIVE) {
                if (currentWindLabel in listOf("O", "ONO", "NO")) fGatekeeper = 1.5
                else if (isOnShore && bft >= 5) fGatekeeper = 0.1 
            }

            val total = fMassa * fWind * fSpecial * fTime * fGatekeeper * (1.0 + regBoost)
            
            Log.d(TAG, "RAW: %-20s | S:%5.2f | M:%d | W:%.2f | T:%.2f | G:%.2f | Krent:%s".format(
                name, total, p.count, fWind, fTime, fGatekeeper, if (fSpecial > 1.0) "JA" else "NEE"))

            ScoredSpecies(p.soortid, name, total, guild, clusterIndices[p.soortid]?.clusterIndex)
        }

        // 4. Gilde selectie & Krenten-Highlights
        val finalResults = mutableListOf<AiInformatieDialoog.GuildSuggestie>()
        val rareHighlights = mutableListOf<AiInformatieDialoog.GuildSuggestie>()
        val idealScore = 5.0 

        SpeciesGuildMapper.Guild.entries.filter { it != SpeciesGuildMapper.Guild.OTHER }.forEach { guild ->
            val winners = scoredList.filter { it.guild == guild }.sortedByDescending { it.score }.take(3)
            winners.forEach { w ->
                val probRaw = (min(0.98, w.score / idealScore) * 100).toInt()
                
                // FEEDBACK CORRECTIE via JSON
                val avgRating = AiFeedbackManager.getCorrectionFactor(context, w.soortid, currentWindLabel)
                val prob = (probRaw * (0.5f + avgRating * 0.5f)).toInt() 

                if (prob >= 10) {
                    val latin = snapshot?.speciesById?.get(w.soortid)?.latin
                    val suggestion = AiInformatieDialoog.GuildSuggestie(
                        guildName = guild.displayName, 
                        soortnaam = w.soortnaam, 
                        kans = prob, 
                        soortid = w.soortid,
                        latinName = latin,
                        expectedIndex = w.expectedIndex
                    )
                    finalResults.add(suggestion)
                    
                    // EXTRA: KRENTEN HIGHLIGHTS ("Uitkijken voor")
                    if (guild.isSpecial || expertKB?.discoveredKrenten?.contains(w.soortid) == true) {
                        if (prob >= 12) { // Lagere drempel voor kwaliteits-waarschuwingen
                            Log.i(TAG, "HIGHLIGHT FOUND: ${w.soortnaam} (${prob}%)")
                            rareHighlights.add(suggestion)
                        }
                    }
                }
            }
        }

        Log.i(TAG, "FINISH: ${finalResults.size} suggesties, ${rareHighlights.size} highlights.")
        Log.i(TAG, "=========================================================")

        logForecast(context, db, cur, currentHour, phase, finalResults)

        return@withContext AiInformatieDialoog.AiSuggesties(
            guildResults = finalResults.sortedByDescending { it.kans },
            rareHighlights = rareHighlights.sortedByDescending { it.kans }.take(3),
            weerBeschrijving = "$currentWindLabel-wind / ${WeatherManager.msToBeaufort(cur.windSpeed10m)}bft"
        )
    }

    private suspend fun logForecast(
        context: Context, 
        db: VoiceTallyDatabase, 
        cur: Current, 
        hour: Int, 
        phase: SolarTimeEngine.SolarPhase,
        results: List<AiInformatieDialoog.GuildSuggestie>
    ) {
        try {
            val conditionJson = org.json.JSONObject().apply {
                put("temp", cur.temperature2m)
                put("wind", WeatherManager.degTo16WindLabel(cur.windDirection10m))
                put("h", hour)
                put("phase", phase.name)
            }.toString()

            val suggestionsJson = org.json.JSONObject().apply {
                val list = org.json.JSONArray()
                results.forEach {
                    val item = org.json.JSONObject()
                    item.put("id", it.soortid)
                    item.put("name", it.soortnaam)
                    item.put("prob", it.kans)
                    item.put("guild", it.guildName)
                    list.put(item)
                }
                put("items", list)
            }.toString()

            val prefs = context.getSharedPreferences("vt5_prefs", Context.MODE_PRIVATE)
            val currentTellingId = prefs.getString("pref_telling_id", "manual") ?: "manual"

            db.tellingDao().insertAiLog(com.yvesds.vt5.core.database.entities.AiLog(
                tellingid = currentTellingId,
                type = "scientific_v2",
                requestContext = conditionJson,
                suggestions = suggestionsJson
            ))
        } catch (_: Exception) {}
    }

    private fun loadExpertKnowledge(modelStore: ModelStore): ExpertKnowledgeBase? {
        return modelStore.loadExpertKnowledge()
    }

    suspend fun getLiveCorridorBoost(month: Int): Double {
        val isAutumn = month in 7..11
        val points = if (isAutumn) AiConfig.REFERENCE_POINTS.take(6) else AiConfig.REFERENCE_POINTS.takeLast(6)
        
        // We vragen de laatste 24 uur aan voor een venster-analyse
        val corridorData = WeatherManager.fetchCorridorForecast(points)
        if (corridorData.isEmpty()) return 0.0

        val now = java.time.LocalDateTime.now()
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

        var totalMaxScore = 0.0
        corridorData.forEach { (name, hourly) ->
            // Zoek de meest gunstige condities in de afgelopen 72 uur (3 dagen) voor dit punt
            val recentHours = hourly.filter { 
                try {
                    val dt = java.time.LocalDateTime.parse(it.time, formatter)
                    dt.isAfter(now.minusHours(72)) && dt.isBefore(now.plusHours(1))
                } catch (_: Exception) { false }
            }
            
            val bestSnapshotScore = recentHours.maxOfOrNull { entry ->
                val cur = Current(temperature2m = entry.temp, windSpeed10m = entry.windSpeed, windDirection10m = entry.windDeg)
                calculateSinglePointScore(cur, isAutumn)
            } ?: 0.0
            totalMaxScore += bestSnapshotScore
        }
        
        return totalMaxScore / points.size
    }

    /**
     * NIEUW: Haalt de corridor boost op uit de lokale database voor een historisch moment.
     */
    suspend fun getHistoricalCorridorBoost(context: Context, dayEpoch: Long, month: Int): Double = withContext(Dispatchers.IO) {
        val isAutumn = month in 7..11
        val points = if (isAutumn) AiConfig.REFERENCE_POINTS.take(6) else AiConfig.REFERENCE_POINTS.takeLast(6)
        val db = VoiceTallyDatabase.getDatabase(context)
        val dao = db.tellingDao()

        var totalMaxScore = 0.0
        val windowStart = dayEpoch - (72 * 3600)
        
        points.forEach { point ->
            var bestPointScore = 0.0
            // Check elk uur in het 72-uurs venster in het archief
            for (hour in 0 until 72) {
                val checkEpoch = windowStart + (hour * 3600)
                val w = dao.getWeather(point.name, checkEpoch)
                if (w != null) {
                    val cur = Current(temperature2m = w.temp, windSpeed10m = w.windSpeed10m, windDirection10m = w.windDir10m, pressureMsl = w.pressureMsl)
                    val score = calculateSinglePointScore(cur, isAutumn)
                    if (score > bestPointScore) bestPointScore = score
                }
            }
            totalMaxScore += bestPointScore
        }
        
        return@withContext totalMaxScore / points.size
    }

    fun calculateSinglePointScore(cur: Current, isAutumn: Boolean): Double {
        val w = WeatherManager.degTo16WindLabel(cur.windDirection10m)
        val p = cur.pressureMsl ?: 1013.0
        return if (isAutumn) {
            if (w in listOf("N","NNO","NO","ONO","O") && p > 1014.0) 1.0 else 0.0
        } else {
            if (w in listOf("Z","ZZW","ZW","WZW") && p > 1010.0) 1.0 else 0.0
        }
    }

    /**
     * Berekent de corridor-score op basis van een lijst met weer-snapshots.
     */
    fun calculateCorridorScore(statuses: List<Pair<String, Current>>, isAutumn: Boolean): Double {
        if (statuses.isEmpty()) return 0.0
        var matchCount = 0
        statuses.forEach { (_, cur) ->
            if (calculateSinglePointScore(cur, isAutumn) > 0.5) matchCount++
        }
        return (matchCount.toDouble() / statuses.size)
    }

    private fun getLocalSiteCluster(saf: SaFStorageHelper, lat: Double, lon: Double): List<String>? {
        return try {
            val jsonStr = saf.readServerDataFile("telpost_locaties.json") ?: return null
            val root = VT5App.json.decodeFromString<TelpostLocatiesRoot>(jsonStr)
            val ids = root.locaties.filter { calculateDistance(lat, lon, it.latitude, it.longitude) <= 35.0 }.map { it.telpostid }
            ids.ifEmpty { null }
        } catch (_: Exception) { null }
    }

    private fun parseWindLabelToDegrees(l: String?): Double? {
        val labels = arrayOf("N","NNO","NO","ONO","O","OZO","ZO","ZZO","Z","ZZW","ZW","WZW","W","WNW","NW","NNW")
        val i = labels.indexOf(l?.uppercase()); return if (i >= 0) i * 22.5 else null
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat/2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private data class ScoredSpecies(
        val soortid: String, 
        val soortnaam: String, 
        val score: Double, 
        val guild: SpeciesGuildMapper.Guild,
        val expectedIndex: Float? = null
    )
}
