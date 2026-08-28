package com.yvesds.vt5.ai

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.dao.DayCountRowClean
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * [STABLE_AI_PROGNOSIS_ACTIVITY_V1 - 24 AUG 2026]
 * Nieuwe Activity voor de Live Migratie Prognose.
 * Gebruikt de bewezen seriële render-engine van de dagrapporten.
 */
class AiPrognoseActiviteit : AppCompatActivity() {

    private lateinit var database: VoiceTallyDatabase
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_ai_prognose)

        database = VoiceTallyDatabase.getDatabase(this)
        container = findViewById(R.id.prognoseContainer)

        val data = intent.getParcelableExtra<AiSuggestieData>("prognose_data")
        if (data == null) {
            finish(); return
        }

        findViewById<TextView>(R.id.tvCondities).text = "Condities: ${data.weerBeschrijving}"
        findViewById<View>(R.id.btnTerug).setOnClickListener { finish() }

        buildDashboard(data)
    }

    private fun buildDashboard(data: AiSuggestieData) {
        lifecycleScope.launch {
            // 1. BEPAAL CLUSTER (Cruciaal anker voor de grafieklijnen)
            val saf = SaFStorageHelper(this@AiPrognoseActiviteit)
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

            // 2. VOEG HIGHLIGHTS TOE (Serieel)
            if (data.rareHighlights.isNotEmpty()) {
                addSectionHeader("UITKIJKEN VOOR (KRENTEN):", getColor(R.color.vt5_orange))
                data.rareHighlights.forEach { item ->
                    val view = createSpeciesCard(item, isHighlight = true, clusterIds = clusterIds)
                    container.addView(view)
                }
                addDivider()
            }

            // 3. VOEG ALGEMENE PROGNOSE TOE (Serieel)
            if (data.guildResults.isNotEmpty()) {
                addSectionHeader("ALGEMENE PROGNOSE:", Color.WHITE)
                data.guildResults.forEach { item ->
                    val view = createSpeciesCard(item, isHighlight = false, clusterIds = clusterIds)
                    container.addView(view)
                }
            }
        }
    }

    private fun addSectionHeader(title: String, color: Int) {
        val tv = TextView(this)
        tv.text = title; tv.setTextColor(color); tv.textSize = 14f; tv.setPadding(24, 32, 24, 16); tv.typeface = Typeface.DEFAULT_BOLD
        container.addView(tv)
    }

    private fun addDivider() {
        val d = View(this)
        d.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply { setMargins(0, 32, 0, 16) }
        d.setBackgroundColor(Color.parseColor("#444444"))
        container.addView(d)
    }

    private suspend fun createSpeciesCard(item: VogelSuggestie, isHighlight: Boolean, clusterIds: List<String>): View {
        val view = LayoutInflater.from(this).inflate(R.layout.item_ai_suggestion, container, false)
        
        val tvName = view.findViewById<TextView>(R.id.tvSpeciesName)
        val tvExpected = view.findViewById<TextView>(R.id.tvExpectedIndex)
        val tvScientific = view.findViewById<TextView>(R.id.tvScientificInfo)
        val tvHistoric = view.findViewById<TextView>(R.id.tvHistoric)
        val ivIcon = view.findViewById<ImageView>(R.id.ivSpecies)
        val chartView = view.findViewById<com.patrykandpatrick.vico.views.cartesian.CartesianChartView>(R.id.sparklinePhenology)
        val viewIndicator = view.findViewById<View>(R.id.viewDateIndicator)
        val clGraph = view.findViewById<View>(R.id.clGraphContainer)
        
        view.findViewById<TextView>(R.id.tvGuild).text = item.guildName
        tvName.text = item.soortnaam
        tvName.setTextColor(if (isHighlight) getColor(R.color.vt5_orange) else getGuildColor(item.guildName))
        
        val norm = item.expectedIndex ?: 0f
        tvExpected.text = "BpH: ${"%.2f".format(norm)}"
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
            
            // Indicator
            val weekOfYear = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)
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
            guildName.contains("Zeevogels") -> Color.MAGENTA
            guildName.contains("Stelt") -> Color.parseColor("#FF9800")
            guildName.contains("Water") -> Color.parseColor("#4FC3F7")
            guildName.contains("Kust") -> Color.parseColor("#009688")
            else -> Color.LTGRAY
        }
    }
}
