package com.yvesds.vt5.features.ai

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.yvesds.vt5.R
import com.yvesds.vt5.core.opslag.FileLogger
import com.yvesds.vt5.databinding.DialogAiPredictionBinding
import com.yvesds.vt5.utils.weather.WeatherManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * PredictionDialog: Shows AI migration prognosis for the next 8 hours.
 */
class PredictionDialog : DialogFragment() {

    private var _binding: DialogAiPredictionBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAiPredictionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.btnSluiten.setOnClickListener { dismiss() }
        
        runPrognosis()
    }

    private fun runPrognosis() {
        val fileLogger = FileLogger(requireContext())
        lifecycleScope.launch {
            try {
                val predictor = MigrationPredictor(requireContext())
                val location = withContext(Dispatchers.IO) {
                    WeatherManager.getLastKnownLocation(requireContext())
                }
                
                if (location == null) {
                    val msg = "Geen locatie beschikbaar. AI voorspelling niet mogelijk."
                    binding.tvSummary.text = msg
                    fileLogger.error(msg)
                    return@launch
                }

                val hourlyData = withContext(Dispatchers.IO) {
                    WeatherManager.fetchHourly(location.latitude, location.longitude, 24)
                }

                if (hourlyData == null || hourlyData.time.isNullOrEmpty()) {
                    val msg = "Geen weergegevens beschikbaar."
                    binding.tvSummary.text = msg
                    fileLogger.error(msg)
                    return@launch
                }

                val snapshots = mutableListOf<HourlyPrediction>()
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
                val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

                val now = System.currentTimeMillis()
                val currentCal = Calendar.getInstance()
                val currentMonth = currentCal.get(Calendar.MONTH) + 1
                
                // Determine upstream location
                val upstreamLoc = getUpstreamLocation(location.latitude, location.longitude, currentMonth)
                fileLogger.info("Determined upstream location: ${upstreamLoc.first}, ${upstreamLoc.second} for month $currentMonth")
                
                val upstreamData = withContext(Dispatchers.IO) {
                    WeatherManager.fetchHourly(upstreamLoc.first, upstreamLoc.second, 24)
                }

                var hoursCount = 0
                val alerts = mutableListOf<String>()
                var lastWindDir: Double? = null
                
                for (i in hourlyData.time.indices) {
                    val timeStr = hourlyData.time[i]
                    val date = sdf.parse(timeStr) ?: continue
                    
                    if (date.time < now - 3600000) continue // Skip past hours
                    if (hoursCount >= 8) break
                    
                    val windDir = hourlyData.windDirection10m?.get(i) ?: 0.0
                    val temp = hourlyData.temperature2m?.get(i) ?: 0.0
                    
                    // Sea breeze detection (Coast: Lat > 51.0, Lon < 4.0 approx)
                    if (location.latitude > 51.0 && location.longitude < 4.5) {
                        if (lastWindDir != null && isSeaBreezeShift(lastWindDir, windDir)) {
                            val alert = "Mogelijke Zeebries gedetecteerd rond ${timeFmt.format(date)}!"
                            alerts.add(alert)
                            fileLogger.warn("Alert: $alert")
                        }
                    }
                    lastWindDir = windDir

                    val input = MigrationPredictor.PredictionInput(
                        temp = temp,
                        windSpeed10m = hourlyData.windSpeed10m?.get(i) ?: 0.0,
                        windDirectionDeg = windDir,
                        pressureMsl = hourlyData.pressureMsl?.get(i) ?: 1013.0,
                        cloudCover = hourlyData.cloudCover?.get(i) ?: 0.0,
                        tempUpstream = upstreamData?.temperature2m?.get(i) ?: temp,
                        pressureUpstream = upstreamData?.pressureMsl?.get(i) ?: (hourlyData.pressureMsl?.get(i) ?: 1013.0),
                        timestamp = date.time
                    )
                    
                    val results = predictor.predict(input)
                    snapshots.add(HourlyPrediction(timeFmt.format(date), results))
                    hoursCount++
                }

                updateUI(snapshots, alerts)
                
            } catch (e: Exception) {
                Log.e("PredictionDialog", "Prognosis error", e)
                binding.tvSummary.text = "Fout bij berekening: ${e.message}"
                fileLogger.error("Prognosis Logic Failed: ${e.message}\n${Log.getStackTraceString(e)}")
            }
        }
    }

    private fun getUpstreamLocation(lat: Double, lon: Double, month: Int): Pair<Double, Double> {
        return if (month in 2..6) {
            // Spring: birds come from South
            Pair(lat - 2.0, lon - 0.5)
        } else {
            // Autumn: birds come from North/North-East
            Pair(lat + 2.0, lon + 1.5)
        }
    }

    private fun isSeaBreezeShift(oldDir: Double, newDir: Double): Boolean {
        // Simple heuristic: shift from land (S/E) to sea (N/W)
        // Sea directions for Belgian coast: 270 (W) to 360/0 (N)
        val isOldLand = oldDir in 90.0..220.0
        val isNewSea = newDir in 270.0..360.0 || newDir in 0.0..30.0
        return isOldLand && isNewSea
    }

    private fun updateUI(snapshots: List<HourlyPrediction>, alerts: List<String>) {
        if (snapshots.isEmpty()) {
            binding.tvSummary.text = "Geen voorspellingen gegenereerd."
            return
        }

        // Summary: overall trend
        val allResults = snapshots.flatMap { it.results }
        val topOverall = allResults.groupBy { it.speciesName }
            .mapValues { entry -> entry.value.sumOf { it.confidence } / snapshots.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        val summaryText = StringBuilder()
        if (alerts.isNotEmpty()) {
            summaryText.append("⚠️ WAARSCHUWING:\n")
            alerts.forEach { summaryText.append("- $it\n") }
            summaryText.append("\n")
        }
        
        summaryText.append("Verwachte top-soorten voor de komende 8 uur:\n")
        topOverall.forEachIndexed { index, (name, score) ->
            summaryText.append("${index + 1}. $name (${(score * 100).toInt()}%)\n")
        }
        binding.tvSummary.text = summaryText.toString()
        
        if (alerts.isNotEmpty()) {
            binding.tvSummary.setTextColor(requireContext().getColor(R.color.vt5_orange))
        } else {
            binding.tvSummary.setTextColor(requireContext().getColor(R.color.vt5_on_surface))
        }

        // Detailed snapshots
        binding.containerSnapshots.removeAllViews()
        snapshots.forEach { snap ->
            val snapshotView = layoutInflater.inflate(R.layout.item_ai_snapshot, binding.containerSnapshots, false)
            val tvTime = snapshotView.findViewById<android.widget.TextView>(R.id.tvTime)
            val tvSpecies = snapshotView.findViewById<android.widget.TextView>(R.id.tvSpeciesList)
            
            tvTime.text = snap.time
            val speciesText = snap.results.take(5).joinToString(", ") { "${it.speciesName} (${(it.confidence * 100).toInt()}%)" }
            tvSpecies.text = speciesText
            
            binding.containerSnapshots.addView(snapshotView)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class HourlyPrediction(val time: String, val results: List<MigrationPredictor.PredictionResult>)
}
