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
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.ui.SpeciesNameResolver
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import com.yvesds.vt5.utils.weather.WeatherManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * AiReportDetailsActiviteit - Gedetailleerd overzicht van een teldag met site-breakdown en wetenschappelijke context.
 */
class AiReportDetailsActiviteit : AppCompatActivity() {

    private var dateMillis: Long = 0
    private lateinit var database: VoiceTallyDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_ai_report_details)

        dateMillis = intent.getLongExtra("date_millis", 0)
        database = VoiceTallyDatabase.getDatabase(this)

        findViewById<View>(R.id.btnTerug).setOnClickListener { finish() }

        loadReport()
    }

    private fun loadReport() {
        lifecycleScope.launch {
            val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
            val sdf = SimpleDateFormat("EEEE d MMMM yyyy", Locale("nl", "BE"))
            findViewById<TextView>(R.id.tvReportTitle).text = sdf.format(cal.time).replaceFirstChar { it.uppercase() }

            val dao = database.tellingDao()
            val startDay = dateMillis / 1000
            
            // 1. Haal de analyse op uit de kluis
            val analysis = withContext(Dispatchers.IO) { dao.getDailyAnalysis(startDay) }
            if (analysis == null) {
                findViewById<TextView>(R.id.tvWeatherInfo).text = "Geen analyse gevonden voor deze dag. Start eerst een (batch) reconstructie."
                return@launch
            }

            val snapshot = withContext(Dispatchers.IO) { try { ServerDataCache.getOrLoad(this@AiReportDetailsActiviteit) } catch(_: Exception) { null } }

            // 2. Weer & Inspanning tonen vanuit de opgeslagen JSON
            val wj = JSONObject(analysis.weatherJson)
            findViewById<TextView>(R.id.tvWeatherInfo).text = 
                "Wind: ${wj.optString("wind").uppercase()} ${wj.optString("bft")}bft | Temp: ${wj.optString("temp")}°C | Luchtdruk: ${wj.optString("hpa")}hPa"
            
            val ej = JSONObject(analysis.effortJson)
            val effortText = ej.keys().asSequence().joinToString(" | ") { id ->
                val name = snapshot?.sitesById?.get(id)?.telpostnaam ?: id
                val sec = ej.getLong(id)
                val h = sec / 3600
                val m = (sec % 3600) / 60
                "$name: ${h}u ${"%02d".format(m)}m"
            }
            findViewById<TextView>(R.id.tvEffortInfo).text = "INSPANNING:\n$effortText"

            // 3. Soorten verwerken vanuit de opgeslagen resultaten-JSON
            val speciesContainer = findViewById<LinearLayout>(R.id.speciesContainer)
            val isTablet = resources.configuration.smallestScreenWidthDp >= 600
            val grid: GridLayout? = if (isTablet) findViewById(R.id.speciesGrid) else null
            
            val results = org.json.JSONArray(analysis.resultsJson)
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val sid = item.getString("id")
                val name = item.getString("name")
                val totalSeen = item.getLong("count")
                val bph = item.getDouble("bph").toFloat()
                val prob = item.getInt("prob")
                val guild = item.getString("guild")
                val latin = item.optString("latin")
                
                val speciesView = LayoutInflater.from(this@AiReportDetailsActiviteit).inflate(R.layout.item_report_species, if (isTablet) grid else speciesContainer, false)
                
                speciesView.findViewById<TextView>(R.id.tvSpeciesName).text = name
                
                // Site breakdown
                val sites = item.getJSONArray("sites")
                val breakdown = if (sites.length() > 0) {
                    val sb = StringBuilder()
                    for (j in 0 until sites.length()) {
                        val s = sites.getJSONObject(j)
                        val sName = snapshot?.sitesById?.get(s.getString("site"))?.telpostnaam ?: s.getString("site")
                        if (sb.isNotEmpty()) sb.append(" | ")
                        sb.append("$sName: ${s.getInt("c")}ex")
                    }
                    sb.toString()
                } else "Niet waargenomen"
                speciesView.findViewById<TextView>(R.id.tvSiteBreakdown).text = breakdown
                
                val stars = AiEvaluator.calculateStars(totalSeen.toInt(), 1.0, bph) // duration is al in bph verrekend
                speciesView.findViewById<TextView>(R.id.tvStars).text = "⭐".repeat(stars).ifEmpty { "☁️" }
                
                val totalCountTv = speciesView.findViewById<TextView>(R.id.tvTotalCount)
                totalCountTv.text = "$totalSeen ex."
                totalCountTv.visibility = if (totalSeen > 0) View.VISIBLE else View.INVISIBLE

                val scientific = "BSI Kans: $prob% | Norm: ${"%.2f".format(bph)} ex/h"
                speciesView.findViewById<TextView>(R.id.tvScientificInfo).text = scientific

                speciesView.findViewById<MaterialCardView>(R.id.cardSpecies).strokeColor = getGuildColor(guild)

                val iv = speciesView.findViewById<ImageView>(R.id.ivSpecies)
                if (!latin.isNullOrBlank()) {
                    lifecycleScope.launch {
                        val bitmap = withContext(Dispatchers.IO) { SpeciesImageHelper.getThumbnail(latin) }
                        if (bitmap != null) iv.setImageBitmap(bitmap)
                    }
                }

                if (isTablet) {
                    val params = GridLayout.LayoutParams()
                    params.width = 0; params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    speciesView.layoutParams = params; grid?.addView(speciesView)
                } else {
                    speciesContainer.addView(speciesView)
                }
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

    private fun getLocalSiteClusterIds(saf: SaFStorageHelper, lat: Double, lon: Double): List<String>? {
        return try {
            val jsonStr = saf.readServerDataFile("telpost_locaties.json") ?: return null
            val root = com.yvesds.vt5.VT5App.json.decodeFromString<com.yvesds.vt5.core.database.entities.TelpostLocatiesRoot>(jsonStr)
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
