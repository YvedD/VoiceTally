package com.yvesds.vt5.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yvesds.vt5.R
import com.yvesds.vt5.core.ui.ProgressDialogHelper
import com.yvesds.vt5.utils.weather.WeatherManager
import com.yvesds.vt5.utils.weather.Current
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * Scherm voor het tonen van de 3-daagse AI-prognose op basis van echte weerdata.
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
            val progress = ProgressDialogHelper.show(this@AiForecastScherm, "AI haalt weersverwachting op...")
            
            try {
                // 1. Get Location
                val loc = WeatherManager.getLastKnownLocation(this@AiForecastScherm)
                if (loc == null) {
                    showError("Geen locatie beschikbaar")
                    return@launch
                }

                // 2. Fetch 72-hour forecast
                val hourlyData = WeatherManager.fetch72HourForecast(loc.latitude, loc.longitude)
                if (hourlyData == null || hourlyData.isEmpty()) {
                    showError("Kon weersverwachting niet ophalen")
                    return@launch
                }

                // 3. Process and display daily snapshots (e.g., at 10:00 AM each day)
                val container = findViewById<LinearLayout>(R.id.forecastContainer)
                container.removeAllViews()

                val calendar = Calendar.getInstance()
                val sdf = SimpleDateFormat("EEEE d MMMM", Locale("nl", "BE"))

                // Filter for approx 10:00 AM each day for a representative migration snapshot
                val dailySnapshots = hourlyData.filter { it.time.endsWith("T10:00") }

                for (snapshot in dailySnapshots) {
                    val dayView = LayoutInflater.from(this@AiForecastScherm)
                        .inflate(R.layout.item_ai_forecast_day, container, false)
                    
                    // Parse date from snapshot time (format: 2024-07-22T10:00)
                    val dateParts = snapshot.time.split("T")[0].split("-")
                    val snapshotCal = Calendar.getInstance().apply {
                        set(dateParts[0].toInt(), dateParts[1].toInt() - 1, dateParts[2].toInt())
                    }
                    
                    dayView.findViewById<TextView>(R.id.tvDayTitle).text = sdf.format(snapshotCal.time)
                    
                    // Convert wind to Beaufort and Label
                    val bft = WeatherManager.msToBeaufort(snapshot.windSpeed)
                    val windLabel = WeatherManager.degTo16WindLabel(snapshot.windDeg)
                    val temp = snapshot.temp?.roundToInt() ?: "?"
                    
                    val weatherSummary = "Wind: $windLabel ${bft}bft | Temp: ${temp}°C"
                    dayView.findViewById<TextView>(R.id.tvWeatherSummary).text = weatherSummary
                    
                    // 4. Get AI Predictions for this weather snapshot
                    // Create a pseudo-Current object for the inference engine
                    val pseudoCurrent = Current(
                        temperature2m = snapshot.temp,
                        windSpeed10m = snapshot.windSpeed,
                        windDirection10m = snapshot.windDeg
                    )
                    
                    val suggestions = AiInferenceEngine.getSuggesties(this@AiForecastScherm, pseudoCurrent)
                    
                    // Log forecast for evaluation (type "forecast")
                    logForecast(pseudoCurrent, suggestions, snapshot.time)

                    val speciesText = buildSpeciesListText(suggestions)
                    
                    dayView.findViewById<TextView>(R.id.tvSpeciesList).text = speciesText
                    
                    container.addView(dayView)
                }

            } catch (e: Exception) {
                showError("Fout bij berekenen prognose: ${e.message}")
            } finally {
                progress.dismiss()
            }
        }
    }

    private fun logForecast(cur: Current, result: AiInformatieDialoog.AiSuggesties, time: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = com.yvesds.vt5.core.database.VoiceTallyDatabase.getDatabase(this@AiForecastScherm)
                val conditionJson = org.json.JSONObject().apply {
                    put("temp", cur.temperature2m)
                    put("wind", cur.windSpeed10m)
                    put("wind_deg", cur.windDirection10m)
                    put("forecast_time", time)
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

                db.tellingDao().insertAiLog(com.yvesds.vt5.core.database.entities.AiLog(
                    tellingid = "forecast_3d",
                    type = "forecast",
                    requestContext = conditionJson,
                    suggestions = suggestionsJson
                ))
            } catch (_: Exception) {}
        }
    }

    private fun buildSpeciesListText(suggestions: AiInformatieDialoog.AiSuggesties): String {
        val all = mutableListOf<AiInformatieDialoog.Suggestie>()
        all.addAll(suggestions.periodeSuggesties)
        all.addAll(suggestions.weerSuggesties)
        
        // De-duplicate by name and take top 5
        val uniqueTop = all.distinctBy { it.soortnaam }.take(5)
        
        if (uniqueTop.isEmpty()) return "Geen specifieke prognose beschikbaar"
        
        return uniqueTop.joinToString("\n") { "• ${it.soortnaam} (${it.kans}%)" }
    }

    private fun showError(msg: String) {
        val container = findViewById<LinearLayout>(R.id.forecastContainer)
        val tv = TextView(this)
        tv.text = msg
        tv.setTextColor(getColor(R.color.vt5_red))
        container.addView(tv)
    }
}
