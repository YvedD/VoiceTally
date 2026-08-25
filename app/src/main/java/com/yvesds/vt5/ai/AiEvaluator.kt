package com.yvesds.vt5.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.entities.DailyAnalysis
import com.yvesds.vt5.core.database.entities.TelpostLocatiesRoot
import com.yvesds.vt5.core.opslag.EffortManager
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.utils.weather.WeatherManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * AiEvaluator - Wetenschappelijke prestatie-analyse op basis van Catch Per Unit Effort (CPUE).
 */
object AiEvaluator {

    fun calculateStars(
        userCount: Int,
        durationHours: Double,
        clusterIndex: Float?,
        isRare: Boolean = false
    ): Int {
        if (userCount <= 0) return 0
        if (durationHours <= 0.0) return 1
        
        val userBph = userCount / durationHours
        if (clusterIndex == null || clusterIndex <= 0f) return if (isRare) 5 else 3

        val ratio = userBph / clusterIndex
        return when {
            ratio >= 2.0 -> 5
            ratio >= 1.2 -> 4
            ratio >= 0.8 -> 3
            ratio >= 0.4 -> 2
            else -> 1
        }
    }

    /**
     * Genereert en toont een eindrapport van de teldag.
     */
    fun showEndOfDayReport(context: Context, dateMillis: Long? = null) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val cal = Calendar.getInstance()
                if (dateMillis != null) cal.timeInMillis = dateMillis
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                
                reconstructAndSaveReport(context, cal.timeInMillis)
                
                withContext(Dispatchers.Main) {
                    val sdf = SimpleDateFormat("dd MMMM yyyy", Locale("nl", "BE"))
                    AlertDialog.Builder(context)
                        .setTitle("Analyse Voltooid")
                        .setMessage("Het AI-verslag voor ${sdf.format(cal.time)} is opgeslagen in het archief.")
                        .setPositiveButton("BEKIJK") { _, _ ->
                            val intent = Intent(context, AiReportDetailsActiviteit::class.java)
                            intent.putExtra("date_millis", cal.timeInMillis)
                            context.startActivity(intent)
                        }
                        .setNegativeButton("SLUITEN", null)
                        .show()
                }
            } catch (e: Exception) {
                Log.e("AiEvaluator", "Error in showEndOfDayReport: ${e.message}")
            }
        }
    }

    /**
     * De "Tijdsmachine": Reconstrueert een complete teldag en slaat deze op in de DailyAnalysis kluis.
     */
    suspend fun reconstructAndSaveReport(context: Context, dateMillis: Long) = withContext(Dispatchers.Default) {
        try {
            val db = VoiceTallyDatabase.getDatabase(context)
            val dao = db.tellingDao()
            
            val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val startDay = cal.timeInMillis / 1000
            val endDay = startDay + 86399

            val dayHeaders = dao.getAllHeaders().filter { (it.begintijd.toLongOrNull() ?: 0L) in startDay..endDay }
            if (dayHeaders.isEmpty()) return@withContext

            val loc = WeatherManager.getLastKnownLocation(context)
            val lat = loc?.latitude ?: 51.0
            val lon = loc?.longitude ?: 3.0
            val dateStrYmd = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            
            // 1. Data-verwerving (Lokaal Archief)
            val month = cal.get(Calendar.MONTH) + 1
            val dayBoost = AiInferenceEngine.getHistoricalCorridorBoost(context, startDay, month)
            
            val isAutumn = month in 7..11
            val points = if (isAutumn) AiConfig.REFERENCE_POINTS.take(6) else AiConfig.REFERENCE_POINTS.takeLast(6)
            val corridorHistoryMap = mutableMapOf<String, List<com.yvesds.vt5.core.database.entities.WeatherArchive>>()
            
            points.forEach { point ->
                val list = mutableListOf<com.yvesds.vt5.core.database.entities.WeatherArchive>()
                for (h in 0 until 72) {
                    val checkEpoch = startDay - (72 * 3600) + (h * 3600)
                    dao.getWeather(point.name, checkEpoch)?.let { list.add(it) }
                }
                corridorHistoryMap[point.name] = list
            }

            // 2. Inspanning & Giga-Norm-berekening (15.800h Methode)
            val totalSeconds = dayHeaders.sumOf { (it.eindtijd.toLongOrNull() ?: 0L) - (it.begintijd.toLongOrNull() ?: 0L) }
            val durationHours = totalSeconds / 3600.0
            val saf = SaFStorageHelper(context)
            val clusterIds = getLocalSiteClusterIds(saf, lat, lon) ?: emptyList()
            
            val clusterEffortHours = EffortManager.getClusterEffortHours(context, clusterIds)
            val gigaIndices = dao.getSpeciesGigaBaseline(clusterIds, clusterEffortHours).associateBy { it.soortid }
            
            // 3. AI Inference Loop (Prognose)
            val predictedSpecies = mutableMapOf<String, VogelSuggestie>()
            dayHeaders.forEach { h ->
                val bt = h.begintijd.toLongOrNull() ?: 0L
                val hour = (((bt + 1800) / 3600) % 24).toInt()
                val cur = com.yvesds.vt5.utils.weather.Current(
                    temperature2m = h.temperatuur.toDoubleOrNull() ?: 15.0,
                    windSpeed10m = (h.windkracht.toDoubleOrNull() ?: 0.0) * 0.8,
                    windDirection10m = parseWindLabelToDegrees(h.windrichting) ?: 0.0
                )
                AiInferenceEngine.getSuggesties(context, cur, hour, dayBoost).guildResults.forEach { s ->
                    if (!predictedSpecies.containsKey(s.soortid)) predictedSpecies[s.soortid] = s
                }
            }

            // 4. Combineer Voorspeld + Werkelijk Gezien
            val seenSpeciesIds = dao.getSeenSpeciesIdsInRange(startDay, endDay)
            val allRelevantIds = (predictedSpecies.keys + seenSpeciesIds).distinct()
            val snapshot = try { com.yvesds.vt5.features.serverdata.model.ServerDataCache.getOrLoad(context) } catch(_: Exception) { null }

            val rawResults = allRelevantIds.map { sid ->
                val siteCounts = dao.getSpeciesCountPerSiteInRange(startDay, endDay, sid)
                val totalSeen = siteCounts.sumOf { it.count.toLong() }
                
                val giga = gigaIndices[sid]
                val bph = giga?.clusterIndex ?: 0f
                val pred = predictedSpecies[sid]
                val stars = calculateStars(totalSeen.toInt(), durationHours, bph)
                
                val item = JSONObject()
                item.put("id", sid)
                item.put("name", pred?.soortnaam ?: snapshot?.speciesById?.get(sid)?.soortnaam ?: sid)
                item.put("prob", pred?.kans ?: 0)
                item.put("guild", pred?.guildName ?: snapshot?.speciesById?.get(sid)?.let { SpeciesGuildMapper.getGuildByLatin(it.latin).displayName } ?: "Overige")
                item.put("latin", pred?.latinName ?: snapshot?.speciesById?.get(sid)?.latin ?: "")
                item.put("count", totalSeen)
                item.put("bph", bph)
                item.put("stars", stars)
                item.put("isSeen", totalSeen > 0)
                
                // Piekperiodes berekenen
                if (giga != null) {
                    val p1 = formatPeakRange(giga.peakDay1)
                    val p2 = formatPeakRange(giga.peakDay2)
                    item.put("peaks", "[$p1] & [$p2]")
                }
                
                val sites = JSONArray()
                siteCounts.forEach { sc ->
                    val sj = JSONObject(); sj.put("site", sc.soortid); sj.put("c", sc.count); sites.put(sj)
                }
                item.put("sites", sites)
                item
            }

            // 5. Sorteren
            val sortedResults = rawResults.sortedWith(compareByDescending<JSONObject> { it.getBoolean("isSeen") }
                .thenByDescending { it.getInt("stars") }
                .thenByDescending { it.getInt("prob") })

            val resultsArray = JSONArray()
            sortedResults.forEach { resultsArray.put(it) }

            val weatherJson = JSONObject().apply {
                val h = dayHeaders.first()
                put("wind", h.windrichting); put("bft", h.windkracht); put("temp", h.temperatuur); put("hpa", h.hpa)
            }.toString()

            val effortJson = JSONObject().apply {
                dayHeaders.groupBy { it.telpostid }.forEach { (id, list) ->
                    val sec = list.sumOf { (it.eindtijd.toLongOrNull() ?: 0L) - (it.begintijd.toLongOrNull() ?: 0L) }
                    put(id, sec)
                }
            }.toString()

            val corridorJson = JSONObject().apply {
                corridorHistoryMap.forEach { (name, data) ->
                    val arr = JSONArray()
                    data.forEach { d ->
                        val dj = JSONObject(); dj.put("t", d.timeEpoch); dj.put("w", d.windDir10m); dj.put("s", d.windSpeed10m); arr.put(dj)
                    }
                    put(name, arr)
                }
            }.toString()

            dao.insertDailyAnalysis(DailyAnalysis(
                dayEpoch = startDay,
                type = "RECONSTRUCTED",
                weatherJson = weatherJson,
                effortJson = effortJson,
                resultsJson = resultsArray.toString(),
                corridorJson = corridorJson
            ))

        } catch (e: Exception) {
            Log.e("AiEvaluator", "Reconstruction failed: ${e.message}", e)
        }
    }

    private fun formatPeakRange(dayOfYear: Int): String {
        if (dayOfYear <= 0) return "onbekend"
        val cal = Calendar.getInstance()
        // Centreer het venster rond de piekdagen: 7 dagen ervoor en 7 dagen erna
        cal.set(Calendar.DAY_OF_YEAR, dayOfYear)
        cal.add(Calendar.DAY_OF_YEAR, -7)
        val startFmt = SimpleDateFormat("d MMMM", Locale("nl", "BE"))
        val start = startFmt.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, 14)
        val end = startFmt.format(cal.time)
        return "$start / $end"
    }

    private fun parseWindLabelToDegrees(l: String?): Double? {
        val labels = arrayOf("N","NNO","NO","ONO","O","OZO","ZO","ZZO","Z","ZZW","ZW","WZW","W","WNW","NW","NNW")
        val i = labels.indexOf(l?.uppercase()); return if (i >= 0) i * 22.5 else null
    }

    private fun getLocalSiteClusterIds(saf: SaFStorageHelper, lat: Double, lon: Double): List<String>? {
        return try {
            val jsonStr = saf.readServerDataFile("telpost_locaties.json") ?: return null
            val root = VT5App.json.decodeFromString<TelpostLocatiesRoot>(jsonStr)
            root.locaties.filter { 
                val r = 6371.0
                val dLat = Math.toRadians(it.latitude - lat); val dLon = Math.toRadians(it.longitude - lon)
                val a = Math.sin(dLat/2) * Math.sin(dLat/2) + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(it.latitude)) * Math.sin(dLon/2) * Math.sin(dLon/2)
                val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
                r * c <= 35.0 
            }.map { it.telpostid }
        } catch (_: Exception) { null }
    }
}
