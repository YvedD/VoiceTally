package com.yvesds.vt5.ai

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.dao.DayCountRowClean
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.core.ui.ProgressDialogHelper
import com.yvesds.vt5.utils.weather.WeatherManager
import com.yvesds.vt5.utils.weather.Current
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * [STABLE_AI_FORECAST_V2 - 24 AUG 2026]
 * Scherm voor het tonen van de 3-daagse AI-prognose.
 * Gebruikt de wetenschappelijke standaard (BSI Kans, Norm, Pieken & Sparklines).
 */
class AiForecastScherm : AppCompatActivity() {

    private lateinit var database: VoiceTallyDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_ai_forecast)

        database = VoiceTallyDatabase.getDatabase(this)

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnClose).setOnClickListener {
            finish()
        }

        loadForecast()
    }

    private fun loadForecast() {
        lifecycleScope.launch {
            val progress = ProgressDialogHelper.show(this@AiForecastScherm, "AI berekent 3-daagse prognose...")
            
            try {
                val loc = WeatherManager.getLastKnownLocation(this@AiForecastScherm)
                if (loc == null) {
                    showError("Geen locatie beschikbaar"); progress.dismiss(); return@launch
                }

                // 1. HAAL WEERSVERWACHTING OP
                val hourlyData = WeatherManager.fetch72HourForecast(loc.latitude, loc.longitude)
                if (hourlyData == null || hourlyData.isEmpty()) {
                    showError("Kon weersverwachting niet ophalen"); progress.dismiss(); return@launch
                }

                // BEPAAL CLUSTER ÉÉN KEER (Analoog aan verslagen)
                val saf = SaFStorageHelper(this@AiForecastScherm)
                val clusterIds = withContext(Dispatchers.IO) {
                    try {
                        val jsonStr = saf.readServerDataFile("telpost_locaties.json") ?: "{}"
                        val root = com.yvesds.vt5.VT5App.json.decodeFromString<com.yvesds.vt5.core.database.entities.TelpostLocatiesRoot>(jsonStr)
                        val primarySite = root.locaties.firstOrNull()
                        if (primarySite != null) {
                            root.locaties.filter { 
                                val r = 6371.0
                                val dLat = Math.toRadians(it.latitude - primarySite.latitude)
                                val dLon = Math.toRadians(it.longitude - primarySite.longitude)
                                val a = sin(dLat/2).pow(2) + cos(Math.toRadians(primarySite.latitude)) * cos(Math.toRadians(it.latitude)) * sin(dLon/2).pow(2)
                                r * 2 * atan2(sqrt(a), sqrt(1 - a)) <= 35.0 
                            }.map { it.telpostid }
                        } else emptyList()
                    } catch(_: Exception) { emptyList() }
                }

                val container = findViewById<ViewGroup>(R.id.forecastContainer)
                container.removeAllViews()

                val sdf = SimpleDateFormat("EEEE d MMMM", Locale("nl", "BE"))
                val dailySnapshots = hourlyData.filter { it.time.endsWith("T10:00") }

                // 2. CORRIDOR VOORSPELLING OPHALEN
                val calNow = Calendar.getInstance()
                val isAutumn = (calNow.get(Calendar.MONTH) + 1) in 7..11
                val points = if (isAutumn) AiConfig.REFERENCE_POINTS.take(6) else AiConfig.REFERENCE_POINTS.takeLast(6)
                val corridorForecast = withContext(Dispatchers.IO) { WeatherManager.fetchCorridorForecast(points) }

                for (snapshot in dailySnapshots) {
                    val dayView = LayoutInflater.from(this@AiForecastScherm).inflate(R.layout.item_ai_forecast_day, container, false)
                    
                    // TABLET OPTIMALISATIE: Breedte verdelen in het raster
                    if (container is android.widget.GridLayout) {
                        val gridParams = android.widget.GridLayout.LayoutParams()
                        gridParams.width = 0
                        gridParams.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                        gridParams.setMargins(8, 8, 8, 8)
                        dayView.layoutParams = gridParams
                    }
                    
                    val dateParts = snapshot.time.split("T")[0].split("-")
                    val snapshotCal = Calendar.getInstance().apply {
                        set(dateParts[0].toInt(), dateParts[1].toInt() - 1, dateParts[2].toInt())
                    }
                    
                    dayView.findViewById<TextView>(R.id.tvDayTitle).text = sdf.format(snapshotCal.time).replaceFirstChar { it.uppercase() }
                    val bft = WeatherManager.msToBeaufort(snapshot.windSpeed)
                    val windLabel = WeatherManager.degTo16WindLabel(snapshot.windDeg)
                    val temp = snapshot.temp?.roundToInt() ?: "?"
                    dayView.findViewById<TextView>(R.id.tvWeatherSummary).text = "Verwachting 10:00u | Wind: $windLabel ${bft}bft | Temp: ${temp}°C"
                    
                    // Bepaal corridor boost voor deze specifieke toekomstige dag
                    val regBoost = calculateCorridorBoostAtTime(snapshot.time, corridorForecast, isAutumn)

                    // Voer AI Inference uit voor deze dag
                    val pseudoCurrent = Current(temperature2m = snapshot.temp, windSpeed10m = snapshot.windSpeed, windDirection10m = snapshot.windDeg)
                    val aiData = AiInferenceEngine.getSuggesties(this@AiForecastScherm, pseudoCurrent, 10, providedRegBoost = regBoost)
                    
                    val speciesGrid = dayView.findViewById<GridLayout>(R.id.speciesGrid)
                    val isTablet = resources.configuration.smallestScreenWidthDp >= 600
                    if (speciesGrid != null) {
                        speciesGrid.columnCount = if (isTablet) 2 else 1
                        
                        // NIEUW: Filter op > 25% kans en neem de Top-12
                        val topBirds = aiData.guildResults
                            .filter { it.kans > 25 }
                            .sortedByDescending { it.kans }
                            .take(12)

                        for (vogel in topBirds) {
                            val specView = createSpeciesCard(vogel, snapshotCal, clusterIds)
                            
                            // Voor GridLayout op tablet: verdeel de ruimte over 2 kolommen
                            if (isTablet) {
                                val gridParams = GridLayout.LayoutParams()
                                gridParams.width = 0
                                gridParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                                specView.layoutParams = gridParams
                            }
                            
                            speciesGrid.addView(specView)
                        }
                    }
                    
                    container.addView(dayView)
                }

            } catch (e: Exception) {
                showError("Fout bij berekenen prognose: ${e.message}")
            } finally {
                progress.dismiss()
            }
        }
    }

    private suspend fun createSpeciesCard(item: VogelSuggestie, targetDate: Calendar, clusterIds: List<String>): View {
        val view = LayoutInflater.from(this).inflate(R.layout.item_forecast_species, null)
        
        val tvName = view.findViewById<TextView>(R.id.tvSpeciesName)
        val tvScientific = view.findViewById<TextView>(R.id.tvScientificInfo)
        val tvHistoric = view.findViewById<TextView>(R.id.tvHistoric)
        val ivIcon = view.findViewById<ImageView>(R.id.ivSpecies)
        val chartView = view.findViewById<com.patrykandpatrick.vico.views.cartesian.CartesianChartView>(R.id.sparklinePhenology)
        val viewIndicator = view.findViewById<View>(R.id.viewDateIndicator)
        val clGraph = view.findViewById<View>(R.id.clGraphContainer)
        
        view.findViewById<TextView>(R.id.tvGuild).text = item.guildName
        tvName.text = item.soortnaam
        
        // BSI Kans & Norm
        val norm = item.expectedIndex ?: 0f
        view.findViewById<TextView>(R.id.tvProbability).text = "${item.kans}%"
        tvScientific.text = "BSI Kans: ${item.kans}% | Norm: ${"%.5f".format(norm)} ex/h"
        
        // Foto
        if (!item.latinName.isNullOrBlank()) {
            val bitmap = withContext(Dispatchers.IO) { SpeciesImageHelper.getThumbnail(item.latinName) }
            if (bitmap != null) ivIcon.setImageBitmap(bitmap)
        }

        // Data & Grafiek (52-weken distributie)
        val distWeeks = withContext(Dispatchers.IO) { database.tellingDao().getSpeciesWeeklyDistribution(item.soortid, clusterIds) }
        val distDays = withContext(Dispatchers.IO) { database.tellingDao().getSpeciesDailyDistribution(item.soortid, clusterIds) }

        if (distWeeks.isNotEmpty()) {
            clGraph.visibility = View.VISIBLE
            PhenologySparklineHelper.setupWeekly(chartView, distWeeks)
            
            // Indicator op de voorspelde week
            val weekOfYear = targetDate.get(Calendar.WEEK_OF_YEAR)
            chartView.post {
                viewIndicator.x = chartView.left + (chartView.width * (weekOfYear / 53f))
                viewIndicator.visibility = View.VISIBLE; viewIndicator.bringToFront()
            }

            // Pieken
            val spring = calculatePeak(distDays.filter { it.day <= 166 })
            val autumn = calculatePeak(distDays.filter { it.day > 166 })
            tvHistoric.text = "Historische Pieken: [$spring] [$autumn]"
            tvHistoric.visibility = View.VISIBLE
        } else {
            clGraph.visibility = View.GONE; tvHistoric.visibility = View.GONE
        }
        
        view.findViewById<MaterialCardView>(R.id.cardSpecies).strokeColor = getGuildColor(item.guildName)
        return view
    }

    private fun calculatePeak(dayCounts: List<DayCountRowClean>): String {
        if (dayCounts.isEmpty()) return "onbekend"
        val maxRow = dayCounts.maxByOrNull { it.count } ?: return "onbekend"
        if (maxRow.count <= 0L) return "onbekend"
        val threshold = maxRow.count * 0.5
        val window = dayCounts.filter { it.count >= threshold }
        val sdf = SimpleDateFormat("d MMM", Locale("nl", "BE"))
        val calS = Calendar.getInstance().apply { set(Calendar.DAY_OF_YEAR, window.first().day) }
        val calE = Calendar.getInstance().apply { set(Calendar.DAY_OF_YEAR, window.last().day) }
        return if (window.first().day == window.last().day) sdf.format(calS.time) else "${sdf.format(calS.time)} - ${sdf.format(calE.time)}"
    }

    private fun getGuildColor(guildName: String): Int {
        return when {
            guildName.contains("Zang") -> Color.CYAN
            guildName.contains("Roof") -> Color.YELLOW
            guildName.contains("Reiger") -> Color.GREEN
            guildName.contains("Zee") -> Color.MAGENTA
            guildName.contains("Stelt") -> Color.parseColor("#FF9800")
            guildName.contains("Water") -> Color.parseColor("#4FC3F7")
            guildName.contains("Kust") -> Color.parseColor("#009688")
            else -> Color.parseColor("#333333")
        }
    }

    private fun calculateCorridorBoostAtTime(targetTime: String, forecast: Map<String, List<WeatherManager.HourlyForecast>>, isAutumn: Boolean): Double {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        val targetDt = java.time.LocalDateTime.parse(targetTime, formatter)
        
        var totalMaxScore = 0.0
        forecast.forEach { (_, hourly) ->
            val windowHours = hourly.filter { 
                try {
                    val dt = java.time.LocalDateTime.parse(it.time, formatter)
                    dt.isAfter(targetDt.minusHours(6)) && dt.isBefore(targetDt.plusHours(1))
                } catch (_: Exception) { false }
            }
            val bestInWindow = windowHours.maxOfOrNull { entry ->
                val cur = Current(temperature2m = entry.temp, windSpeed10m = entry.windSpeed, windDirection10m = entry.windDeg)
                AiInferenceEngine.calculateSinglePointScore(cur, isAutumn)
            } ?: 0.0
            totalMaxScore += bestInWindow
        }
        return if (forecast.isEmpty()) 0.0 else totalMaxScore / forecast.size
    }

    private fun showError(msg: String) {
        val container = findViewById<LinearLayout>(R.id.forecastContainer)
        val tv = TextView(this)
        tv.text = msg; tv.setTextColor(getColor(R.color.vt5_red))
        container.addView(tv)
    }
}
