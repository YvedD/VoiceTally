package com.yvesds.vt5.ai

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.entities.TelpostLocatiesRoot
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.utils.weather.WeatherManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Calendar
import java.util.Date

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
     * @param dateMillis De start van de dag in milliseconden. Indien null wordt 'vandaag' gebruikt.
     */
    fun showEndOfDayReport(context: Context, dateMillis: Long? = null) {
        val prefs = context.getSharedPreferences("vt5_prefs", Context.MODE_PRIVATE)
        val telpostId = prefs.getString("pref_telpost_id", "") ?: return
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val db = VoiceTallyDatabase.getDatabase(context)
                val dao = db.tellingDao()
                
                val cal = Calendar.getInstance()
                if (dateMillis != null) cal.timeInMillis = dateMillis
                
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                val startDay = cal.timeInMillis / 1000
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
                val endDay = cal.timeInMillis / 1000
                
                val totalSeconds = withContext(Dispatchers.IO) { dao.getUserDailyEffort(telpostId, startDay, endDay) } ?: 0L
                if (totalSeconds < 300) {
                    if (dateMillis != null) {
                         AlertDialog.Builder(context).setTitle("Geen data").setMessage("Geen telsessies gevonden voor deze dag.").setPositiveButton("OK", null).show()
                    }
                    return@launch
                }
                
                val durationHours = totalSeconds / 3600.0
                val userYieldRows = withContext(Dispatchers.IO) { dao.getUserDailyYield(telpostId, startDay, endDay) }
                val userYield = userYieldRows.associateBy { it.soortid }
                
                val targetSpecies = mutableMapOf<String, AiInformatieDialoog.GuildSuggestie>()
                
                // 3a. Haal soorten uit de bestaande logs van deze specifieke dag
                val logs = withContext(Dispatchers.IO) { dao.getAllAiLogsFlow() }.first().filter { it.timestamp >= startDay * 1000 && it.timestamp <= endDay * 1000 }
                logs.forEach { log ->
                    try {
                        val json = JSONObject(log.suggestions)
                        val items = json.getJSONArray("items")
                        for (i in 0 until items.length()) {
                            val obj = items.getJSONObject(i)
                            val id = obj.getString("id")
                            if (!targetSpecies.containsKey(id)) {
                                targetSpecies[id] = AiInformatieDialoog.GuildSuggestie(
                                    guildName = obj.optString("guild"),
                                    soortnaam = obj.getString("name"),
                                    kans = obj.getInt("prob"),
                                    soortid = id
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }

                // 3b. "Stille Prognose": Reconstrueer doelsoorten op basis van sessie-weer
                if (targetSpecies.size < 5) {
                    val todaysHeaders = withContext(Dispatchers.IO) { 
                        dao.getAllHeaders().filter { 
                            val bt = it.begintijd.toLongOrNull() ?: 0L
                            bt >= startDay && bt <= endDay 
                        } 
                    }
                    
                    val loc = WeatherManager.getLastKnownLocation(context)
                    val lat = loc?.latitude ?: 51.0
                    val lon = loc?.longitude ?: 3.0
                    val dateStrYmd = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
                    val dayWeather = WeatherManager.fetchHistoricalWeather(lat, lon, dateStrYmd)

                    todaysHeaders.forEach { header ->
                        val bt = header.begintijd.toLongOrNull() ?: 0L
                        val hour = Calendar.getInstance().apply { timeInMillis = bt * 1000 }.get(Calendar.HOUR_OF_DAY)
                        
                        val headerWindBft = header.windkracht.toDoubleOrNull() ?: 0.0
                        val windDeg = parseWindLabelToDegrees(header.windrichting) ?: dayWeather?.getOrNull(hour)?.windDeg ?: 0.0
                        val windMs = if (headerWindBft > 0) headerWindBft * 0.8 else dayWeather?.getOrNull(hour)?.windSpeed ?: 0.0
                        val temp = header.temperatuur.toDoubleOrNull() ?: dayWeather?.getOrNull(hour)?.temp ?: 15.0
                        
                        val pseudoCurrent = com.yvesds.vt5.utils.weather.Current(
                            temperature2m = temp,
                            windSpeed10m = windMs,
                            windDirection10m = windDeg
                        )

                        val sessionSuggesties = AiInferenceEngine.getSuggesties(context, pseudoCurrent, hourOverride = hour)
                        sessionSuggesties.guildResults.forEach { s ->
                            if (!targetSpecies.containsKey(s.soortid)) targetSpecies[s.soortid] = s
                        }
                    }
                }

                val reportItems = mutableListOf<String>()
                val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
                val loc = WeatherManager.getLastKnownLocation(context)
                val saf = SaFStorageHelper(context)
                
                val clusterIds = withContext(Dispatchers.IO) { 
                    getLocalSiteClusterIds(saf, loc?.latitude ?: 51.0, loc?.longitude ?: 3.0) 
                } ?: listOf(telpostId)
                
                val indices = withContext(Dispatchers.IO) {
                    dao.getSpeciesClusterIndex(dayOfYear - 10, dayOfYear + 10, clusterIds).associateBy { it.soortid }
                }

                val dayStarsList = mutableListOf<Int>()
                targetSpecies.values.sortedByDescending { it.kans }.forEach { target ->
                    val count = userYield[target.soortid]?.count ?: 0
                    val index = indices[target.soortid]?.clusterIndex
                    val starsCount = calculateStars(count, durationHours, index)
                    
                    if (count > 0 || starsCount > 0) {
                        val stars = "⭐".repeat(starsCount).ifEmpty { "☁️" }
                        reportItems.add("${target.soortnaam}: $stars ($count ex)")
                        dayStarsList.add(starsCount)
                        
                        // ZELFLERENDE LUS: Sla de score op in het brein (JSON)
                        // EXCLUSIE: VoiceTally Testsite (5177) nooit meenemen in feedback
                        if (telpostId != "5177") {
                            val windForFeedback = logs.lastOrNull()?.let { 
                                try { JSONObject(it.requestContext).optString("wind") } catch(_: Exception) { null }
                            } ?: "Onbekend"
                            AiFeedbackManager.saveRating(context, target.soortid, starsCount.toFloat(), windForFeedback)
                        }
                    }
                }

                // 5. Update de database logs met de gemiddelde dag-score
                if (dayStarsList.isNotEmpty() && telpostId != "5177") {
                    val avgDayRating = dayStarsList.average().toInt()
                    withContext(Dispatchers.IO) {
                        logs.forEach { log ->
                            log.rating = avgDayRating
                            dao.updateAiLog(log)
                        }
                    }
                }

                if (reportItems.isNotEmpty()) {
                    val sdfDisplay = java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale("nl", "BE"))
                    val dateStr = sdfDisplay.format(cal.time)
                    val durationText = "%du %02dm".format(totalSeconds / 3600, (totalSeconds % 3600) / 60)
                    AlertDialog.Builder(context)
                        .setTitle("AI Evaluatie: $dateStr")
                        .setMessage("Totaal geteld op deze post: $durationText\n\nResultaten t.o.v. de cluster:\n\n" + reportItems.joinToString("\n"))
                        .setPositiveButton("Mooi!") { d, _ -> d.dismiss() }
                        .show()
                } else if (dateMillis != null) {
                    AlertDialog.Builder(context).setTitle("Geen evaluatie").setMessage("Kon geen relevante AI-evaluatie genereren voor deze dag.").setPositiveButton("OK", null).show()
                }
            } catch (e: Exception) {
                Log.e("AiEvaluator", "Error generating report: ${e.message}", e)
            }
        }
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
