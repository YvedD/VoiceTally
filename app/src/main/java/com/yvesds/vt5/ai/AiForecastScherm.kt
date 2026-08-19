package com.yvesds.vt5.ai

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.yvesds.vt5.R
import com.yvesds.vt5.core.ui.ProgressDialogHelper
import com.yvesds.vt5.utils.weather.WeatherManager
import com.yvesds.vt5.utils.weather.Current
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * Scherm voor het tonen van de 3-daagse AI-prognose met een professionele top-15 lijst.
 */
class AiForecastScherm : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_ai_forecast)

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
                    showError("Geen locatie beschikbaar")
                    return@launch
                }

                val hourlyData = WeatherManager.fetch72HourForecast(loc.latitude, loc.longitude)
                if (hourlyData == null || hourlyData.isEmpty()) {
                    showError("Kon weersverwachting niet ophalen")
                    return@launch
                }

                val container = findViewById<LinearLayout>(R.id.forecastContainer)
                container.removeAllViews()

                val sdf = SimpleDateFormat("EEEE d MMMM", Locale("nl", "BE"))
                val dailySnapshots = hourlyData.filter { it.time.endsWith("T10:00") }

                // Bepaal of we op een tablet zitten voor de grid layout
                val isTablet = resources.configuration.smallestScreenWidthDp >= 600

                for (snapshot in dailySnapshots) {
                    val dayView = LayoutInflater.from(this@AiForecastScherm)
                        .inflate(R.layout.item_ai_forecast_day, container, false)
                    
                    val dateParts = snapshot.time.split("T")[0].split("-")
                    val snapshotCal = Calendar.getInstance().apply {
                        set(dateParts[0].toInt(), dateParts[1].toInt() - 1, dateParts[2].toInt())
                    }
                    
                    dayView.findViewById<TextView>(R.id.tvDayTitle).text = sdf.format(snapshotCal.time).replaceFirstChar { it.uppercase() }
                    
                    val bft = WeatherManager.msToBeaufort(snapshot.windSpeed)
                    val windLabel = WeatherManager.degTo16WindLabel(snapshot.windDeg)
                    val temp = snapshot.temp?.roundToInt() ?: "?"
                    val weatherSummary = "Verwachting 10:00u | Wind: $windLabel ${bft}bft | Temp: ${temp}°C"
                    dayView.findViewById<TextView>(R.id.tvWeatherSummary).text = weatherSummary
                    
                    // 1. Haal corridor boost op
                    val calNow = Calendar.getInstance()
                    val isAutumn = (calNow.get(Calendar.MONTH) + 1) in 7..11
                    val points = if (isAutumn) AiConfig.REFERENCE_POINTS.take(6) else AiConfig.REFERENCE_POINTS.takeLast(6)
                    val corridorForecast = withContext(Dispatchers.IO) { WeatherManager.fetchCorridorForecast(points) }
                    val regBoost = calculateCorridorBoostAtTime(snapshot.time, corridorForecast, isAutumn)

                    // 2. Voer AI Inference uit
                    val pseudoCurrent = Current(temperature2m = snapshot.temp, windSpeed10m = snapshot.windSpeed, windDirection10m = snapshot.windDeg)
                    val suggestions = AiInferenceEngine.getSuggesties(
                        context = this@AiForecastScherm, 
                        cur = pseudoCurrent, 
                        hourOverride = 10,
                        providedRegBoost = regBoost
                    )
                    
                    // 3. Vul de soorten-grid
                    val speciesGrid = dayView.findViewById<GridLayout>(R.id.speciesGrid)
                    speciesGrid.columnCount = if (isTablet) 2 else 1
                    
                    val top15 = suggestions.guildResults.sortedByDescending { it.kans }.take(15)
                    
                    for (item in top15) {
                        val specView = LayoutInflater.from(this@AiForecastScherm)
                            .inflate(R.layout.item_forecast_species, speciesGrid, false)
                        
                        val card = specView.findViewById<MaterialCardView>(R.id.cardSpecies)
                        val tvName = specView.findViewById<TextView>(R.id.tvSpeciesName)
                        val tvBph = specView.findViewById<TextView>(R.id.tvSpeciesBph)
                        val tvProb = specView.findViewById<TextView>(R.id.tvProbability)
                        val ivIcon = specView.findViewById<ImageView>(R.id.ivSpecies)
                        
                        tvName.text = item.soortnaam
                        tvProb.text = "${item.kans}%"
                        
                        if (item.expectedIndex != null && item.expectedIndex > 0) {
                            tvBph.text = "BpH index: %.2f ex/h".format(item.expectedIndex)
                            tvBph.visibility = View.VISIBLE
                        } else {
                            tvBph.visibility = View.GONE
                        }
                        
                        val guildColor = getGuildColor(item.guildName)
                        card.strokeColor = guildColor
                        
                        // Foto laden op de achtergrond
                        if (!item.latinName.isNullOrBlank()) {
                            val latin = item.latinName
                            ivIcon.tag = latin
                            lifecycleScope.launch {
                                val bitmap = withContext(Dispatchers.IO) { SpeciesImageHelper.getThumbnail(latin) }
                                if (bitmap != null && ivIcon.tag == latin) ivIcon.setImageBitmap(bitmap)
                            }
                        }
                        
                        // Layout params voor grid om breedte goed te verdelen
                        val params = GridLayout.LayoutParams()
                        params.width = 0
                        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        specView.layoutParams = params
                        
                        speciesGrid.addView(specView)
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
        tv.text = msg
        tv.setTextColor(getColor(R.color.vt5_red))
        container.addView(tv)
    }
}
