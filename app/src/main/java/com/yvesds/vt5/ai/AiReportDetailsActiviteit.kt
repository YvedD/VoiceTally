package com.yvesds.vt5.ai

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.dao.DayCountRowClean
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import com.yvesds.vt5.utils.weather.WeatherManager
import com.yvesds.vt5.core.ui.ProgressDialogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * [STABLE_GOLDEN_STATE_V2 - 23 AUG 2026]
 * AiReportDetailsActiviteit - Gedetailleerd overzicht met exacte piekperiodes en Sparklines.
 * NIET WIJZIGEN ZONDER MANIFEST CHECK.
 */
class AiReportDetailsActiviteit : AppCompatActivity() {

    private var dateMillis: Long = 0
    private lateinit var database: VoiceTallyDatabase
    private lateinit var etRemarks: TextInputEditText
    private var reportClusterIds: List<String> = emptyList() 
    private var reportAnchorSiteId: String = "default" // De Hoofdtelpost ID

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
            val progress = ProgressDialogHelper.show(this@AiReportDetailsActiviteit, "Archief raadplegen...")
            
            val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
            val sdf = SimpleDateFormat("EEEE d MMMM yyyy", Locale("nl", "BE"))
            findViewById<TextView>(R.id.tvReportTitle).text = sdf.format(cal.time).replaceFirstChar { it.uppercase() }

            val dao = database.tellingDao()
            val startDay = dateMillis / 1000
            
            val analysis = withContext(Dispatchers.IO) { dao.getDailyAnalysis(startDay) }
            if (analysis == null) {
                progress.dismiss(); return@launch
            }

            val snapshot = withContext(Dispatchers.IO) { try { ServerDataCache.getOrLoad(this@AiReportDetailsActiviteit) } catch(_: Exception) { null } }

            // EENMALIGE BEPALING VAN DE VOLLEDIGE REGIONALE CLUSTER (GEANKERD AAN DE HOOFDTELPOST)
            val ej = JSONObject(analysis.effortJson)
            val firstSiteIdInReport = if (ej.keys().hasNext()) ej.keys().next() else ""
            
            reportClusterIds = withContext(Dispatchers.IO) {
                val saf = SaFStorageHelper(this@AiReportDetailsActiviteit)
                val jsonStr = saf.readServerDataFile("telpost_locaties.json") ?: "{}"
                val root = com.yvesds.vt5.VT5App.json.decodeFromString<com.yvesds.vt5.core.database.entities.TelpostLocatiesRoot>(jsonStr)
                
                // BEPAAL HOOFDTELPOST (De allereerste in het JSON bestand)
                val primarySite = root.locaties.firstOrNull()
                reportAnchorSiteId = primarySite?.telpostid ?: firstSiteIdInReport
                
                if (primarySite != null) {
                    // Haal ALLE telposten op binnen 35km van de HOOFDTELPOST
                    getLocalSiteClusterIds(saf, primarySite.latitude, primarySite.longitude) ?: listOf(firstSiteIdInReport)
                } else {
                    // Fallback naar de post uit het rapport zelf als er geen locaties bekend zijn
                    val siteLoc = root.locaties.find { it.telpostid == firstSiteIdInReport }
                    getLocalSiteClusterIds(saf, siteLoc?.latitude ?: 51.2, siteLoc?.longitude ?: 3.0) ?: listOf(firstSiteIdInReport)
                }
            }

            // 1. Weer & Inspanning
            val wj = JSONObject(analysis.weatherJson)
            findViewById<TextView>(R.id.tvWeatherInfo).text = 
                "Wind: ${wj.optString("wind").lowercase()} ${wj.optString("bft")}bft | Temp: ${wj.optString("temp")}°C | Druk: ${wj.optString("hpa")}hPa"
            
            var totalDaySeconds = 0L
            val effortText = ej.keys().asSequence().joinToString(" | ") { id ->
                val name = snapshot?.sitesById?.get(id)?.telpostnaam ?: id
                val sec = ej.getLong(id); totalDaySeconds += sec
                val h = sec / 3600; val m = (sec % 3600) / 60
                "$name: ${h}u ${"%02d".format(m)}m"
            }
            val dayHours = totalDaySeconds / 3600.0
            findViewById<TextView>(R.id.tvEffortInfo).text = "INSPANNING:\n$effortText"

            Toast.makeText(this@AiReportDetailsActiviteit, "Seizoens-curves renderen...", Toast.LENGTH_SHORT).show()

            // 2. Soorten verwerken (Ondersteunt zowel LinearLayout als GridLayout)
            val speciesContainer = findViewById<ViewGroup>(R.id.speciesContainer)
            if (speciesContainer == null) {
                Log.e("AiReportDetails", "CRITICAL: speciesContainer niet gevonden in layout!")
                progress.dismiss()
                return@launch
            }
            speciesContainer.removeAllViews()
            
            val results = org.json.JSONArray(analysis.resultsJson)
            val seenList = mutableListOf<JSONObject>()
            val notSeenList = mutableListOf<JSONObject>()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                if (item.optBoolean("isSeen", false)) seenList.add(item) else notSeenList.add(item)
            }

            // SECTIE 1: WAARGENOMEN
            if (seenList.isNotEmpty()) {
                addSectionHeader(speciesContainer, "WAARGENOMEN SOORTEN", Color.parseColor("#4CAF50"))
                seenList.forEachIndexed { idx, it ->
                    ProgressDialogHelper.updateMessage(progress, "Laden: ${it.getString("name")} (${idx + 1}/${seenList.size + notSeenList.size})")
                    addSpeciesCardSynchronous(speciesContainer, it, snapshot, dayHours)
                }
            }

            // SECTIE 2: NIET WAARGENOMEN
            if (notSeenList.isNotEmpty()) {
                if (seenList.isNotEmpty()) {
                    val divider = View(this@AiReportDetailsActiviteit)
                    divider.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply { setMargins(0, 32, 0, 32) }
                    divider.setBackgroundColor(Color.parseColor("#444444")); speciesContainer.addView(divider)
                }
                addSectionHeader(speciesContainer, "NIET WAARGENOMEN (PROGNOSE)", Color.GRAY)
                notSeenList.forEachIndexed { idx, it ->
                    ProgressDialogHelper.updateMessage(progress, "Laden: ${it.getString("name")} (${seenList.size + idx + 1}/${seenList.size + notSeenList.size})")
                    addSpeciesCardSynchronous(speciesContainer, it, snapshot, dayHours)
                }
            }

            addRemarksSection(speciesContainer, analysis.remarks, startDay)
            progress.dismiss()
        }
    }

    private fun addSectionHeader(container: ViewGroup, title: String, color: Int) {
        val tv = TextView(this)
        tv.text = title; tv.setTextColor(color); tv.textSize = 14f; tv.setPadding(16, 32, 16, 8); tv.typeface = android.graphics.Typeface.DEFAULT_BOLD
        container.addView(tv)
    }

    private suspend fun addSpeciesCardSynchronous(container: ViewGroup, item: JSONObject, snapshot: com.yvesds.vt5.features.serverdata.model.DataSnapshot?, dayHours: Double) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_report_species, container, false)
        val sid = item.getString("id")
        val name = item.getString("name")
        val totalSeen = item.getLong("count")
        val bph = item.optDouble("bph", 0.0).toFloat()
        val prob = item.getInt("prob")
        val guild = item.getString("guild")
        val latin = item.optString("latin")
        val stars = item.optInt("stars", 0)

        view.findViewById<TextView>(R.id.tvSpeciesName).text = name
        
        val sites = item.getJSONArray("sites")
        val sb = StringBuilder()
        for (j in 0 until sites.length()) {
            val s = sites.getJSONObject(j)
            val sName = snapshot?.sitesById?.get(s.getString("site"))?.telpostnaam ?: s.getString("site")
            if (sb.isNotEmpty()) sb.append(" | ")
            sb.append("$sName: ${s.getInt("c")}ex")
        }
        
        view.findViewById<TextView>(R.id.tvSiteBreakdown).text = if (sb.isNotEmpty()) sb.toString() else "Niet waargenomen"
        view.findViewById<TextView>(R.id.tvStars).text = "⭐".repeat(stars).ifEmpty { "☁️" }
        view.findViewById<TextView>(R.id.tvTotalCount).text = if (totalSeen > 0) "$totalSeen ex." else ""
        
        // BSI & NORM (GIGA-PRECISIE: 5 decimalen)
        val scientific = "BSI Kans: $prob% | Norm: ${"%.5f".format(bph)} ex/h"
        view.findViewById<TextView>(R.id.tvScientificInfo).text = scientific
        
        val tvHistoric = view.findViewById<TextView>(R.id.tvHistoric)
        val chartView = view.findViewById<com.patrykandpatrick.vico.views.cartesian.CartesianChartView>(R.id.sparklinePhenology)
        val viewIndicator = view.findViewById<View>(R.id.viewDateIndicator)
        val clGraph = view.findViewById<View>(R.id.clGraphContainer)

        // HAAL DATA OP UIT DE VOLLEDIGE REGIONALE CLUSTER (Week-aggregatie uit de cluster)
        val distWeeks = withContext(Dispatchers.IO) { database.tellingDao().getSpeciesWeeklyDistribution(sid, reportClusterIds) }
        val distDays = withContext(Dispatchers.IO) { database.tellingDao().getSpeciesDailyDistribution(sid, reportClusterIds) }
        
        if (distWeeks.isNotEmpty()) {
            val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
            
            // 3. Teken curve (52-weken basis met maand-as)
            clGraph.visibility = View.VISIBLE
            PhenologySparklineHelper.setupWeekly(chartView, distWeeks)
            
            // Marker positie op basis van week (1 op 53 schaal)
            val weekOfYear = cal.get(Calendar.WEEK_OF_YEAR)
            chartView.post {
                val graphWidth = chartView.width
                viewIndicator.x = chartView.left + (graphWidth * (weekOfYear / 53f))
                viewIndicator.visibility = View.VISIBLE
                viewIndicator.bringToFront()
            }

            // 4. Exacte Piekperiodes berekenen [Start - End]
            val springPeriod = calculatePeakPeriod(distDays.filter { it.day <= 166 })
            val autumnPeriod = calculatePeakPeriod(distDays.filter { it.day > 166 })
            tvHistoric.text = "Historische Pieken: [$springPeriod] [$autumnPeriod]"
            tvHistoric.visibility = View.VISIBLE
        } else {
            clGraph.visibility = View.GONE; tvHistoric.visibility = View.GONE
        }

        view.findViewById<MaterialCardView>(R.id.cardSpecies).strokeColor = getGuildColor(guild)
        
        if (!latin.isNullOrBlank()) {
            val bitmap = withContext(Dispatchers.IO) { SpeciesImageHelper.getThumbnail(latin) }
            if (bitmap != null) view.findViewById<ImageView>(R.id.ivSpecies).setImageBitmap(bitmap)
        }
        container.addView(view)
    }

    private fun calculatePeakPeriod(dayCounts: List<DayCountRowClean>): String {
        if (dayCounts.isEmpty()) return "onbekend"
        val maxRow = dayCounts.maxByOrNull { it.count } ?: return "onbekend"
        if (maxRow.count <= 0L) return "onbekend"
        
        val threshold = maxRow.count * 0.5
        val window = dayCounts.filter { it.count >= threshold }
        val startDay = window.first().day
        val endDay = window.last().day
        
        val sdf = SimpleDateFormat("d MMM", Locale("nl", "BE"))
        val calS = Calendar.getInstance().apply { set(Calendar.DAY_OF_YEAR, startDay) }
        val calE = Calendar.getInstance().apply { set(Calendar.DAY_OF_YEAR, endDay) }
        
        return if (startDay == endDay) sdf.format(calS.time) 
        else "${sdf.format(calS.time)} - ${sdf.format(calE.time)}"
    }

    private fun addRemarksSection(container: ViewGroup, existingRemarks: String, dayEpoch: Long) {
        val layout = LayoutInflater.from(this).inflate(R.layout.view_report_remarks, container, false)
        etRemarks = layout.findViewById(R.id.etRemarks); etRemarks.setText(existingRemarks)
        layout.findViewById<Button>(R.id.btnSaveRemarks).setOnClickListener {
            val newRemarks = etRemarks.text.toString()
            lifecycleScope.launch(Dispatchers.IO) {
                database.tellingDao().updateDailyAnalysisRemarks(dayEpoch, newRemarks)
                withContext(Dispatchers.Main) { Toast.makeText(this@AiReportDetailsActiviteit, "Opmerkingen opgeslagen", Toast.LENGTH_SHORT).show() }
            }
        }
        container.addView(layout)
    }

    private fun getGuildColor(guildName: String): Int {
        return when {
            guildName.contains("Zang") -> Color.CYAN
            guildName.contains("Roof") -> Color.YELLOW
            guildName.contains("Reiger") -> Color.GREEN
            guildName.contains("Zeevogels") -> Color.MAGENTA // Specifieker maken
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
                val a = sin(dLat/2).pow(2) + cos(Math.toRadians(lat)) * cos(Math.toRadians(it.latitude)) * sin(dLon/2).pow(2)
                r * 2 * atan2(sqrt(a), sqrt(1 - a)) <= 35.0 
            }.map { it.telpostid }
        } catch (_: Exception) { null }
    }
}
