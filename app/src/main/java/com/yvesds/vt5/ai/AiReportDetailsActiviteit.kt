package com.yvesds.vt5.ai

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * AiReportDetailsActiviteit - Gedetailleerd overzicht met secties en opmerkingen.
 */
class AiReportDetailsActiviteit : AppCompatActivity() {

    private var dateMillis: Long = 0
    private lateinit var database: VoiceTallyDatabase
    private lateinit var etRemarks: TextInputEditText

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
            
            val analysis = withContext(Dispatchers.IO) { dao.getDailyAnalysis(startDay) }
            if (analysis == null) {
                findViewById<TextView>(R.id.tvWeatherInfo).text = "Geen analyse gevonden."
                return@launch
            }

            val snapshot = withContext(Dispatchers.IO) { try { ServerDataCache.getOrLoad(this@AiReportDetailsActiviteit) } catch(_: Exception) { null } }

            // 1. Weer & Inspanning
            val wj = JSONObject(analysis.weatherJson)
            findViewById<TextView>(R.id.tvWeatherInfo).text = 
                "Wind: ${wj.optString("wind").lowercase()} ${wj.optString("bft")}bft | Temp: ${wj.optString("temp")}°C | Druk: ${wj.optString("hpa")}hPa"
            
            val ej = JSONObject(analysis.effortJson)
            val effortText = ej.keys().asSequence().joinToString(" | ") { id ->
                val name = snapshot?.sitesById?.get(id)?.telpostnaam ?: id
                val sec = ej.getLong(id)
                val h = sec / 3600; val m = (sec % 3600) / 60
                "$name: ${h}u ${"%02d".format(m)}m"
            }
            findViewById<TextView>(R.id.tvEffortInfo).text = "INSPANNING:\n$effortText"

            // 2. Soorten verwerken
            val speciesContainer = findViewById<LinearLayout>(R.id.speciesContainer)
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
                seenList.forEach { addSpeciesCard(speciesContainer, it, snapshot) }
            }

            // SECTIE 2: NIET WAARGENOMEN (PROGNOSE)
            if (notSeenList.isNotEmpty()) {
                if (seenList.isNotEmpty()) {
                    val divider = View(this@AiReportDetailsActiviteit)
                    divider.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply { setMargins(0, 32, 0, 32) }
                    divider.setBackgroundColor(Color.parseColor("#444444"))
                    speciesContainer.addView(divider)
                }
                addSectionHeader(speciesContainer, "NIET WAARGENOMEN (PROGNOSE)", Color.GRAY)
                notSeenList.forEach { addSpeciesCard(speciesContainer, it, snapshot) }
            }

            // 3. Opmerkingen
            addRemarksSection(speciesContainer, analysis.remarks, startDay)
        }
    }

    private fun addSectionHeader(container: LinearLayout, title: String, color: Int) {
        val tv = TextView(this)
        tv.text = title
        tv.setTextColor(color)
        tv.textSize = 14f
        tv.setPadding(16, 32, 16, 8)
        tv.typeface = android.graphics.Typeface.DEFAULT_BOLD
        container.addView(tv)
    }

    private fun addSpeciesCard(container: LinearLayout, item: JSONObject, snapshot: com.yvesds.vt5.features.serverdata.model.DataSnapshot?) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_report_species, container, false)
        val sid = item.getString("id")
        val name = item.getString("name")
        val totalSeen = item.getLong("count")
        val bph = item.getDouble("bph").toFloat()
        val prob = item.getInt("prob")
        val guild = item.getString("guild")
        val latin = item.optString("latin")
        val stars = item.optInt("stars", 0)

        view.findViewById<TextView>(R.id.tvSpeciesName).text = name
        
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
        
        view.findViewById<TextView>(R.id.tvSiteBreakdown).text = breakdown
        view.findViewById<TextView>(R.id.tvStars).text = "⭐".repeat(stars).ifEmpty { "☁️" }
        
        val totalCountTv = view.findViewById<TextView>(R.id.tvTotalCount)
        totalCountTv.text = if (totalSeen > 0) "$totalSeen ex." else ""
        
        val scientific = "BSI Kans: $prob% | Norm: ${"%.2f".format(bph)} ex/h"
        view.findViewById<TextView>(R.id.tvScientificInfo).text = scientific
        
        view.findViewById<MaterialCardView>(R.id.cardSpecies).strokeColor = getGuildColor(guild)
        
        val iv = view.findViewById<ImageView>(R.id.ivSpecies)
        if (latin.isNotEmpty()) {
            lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) { SpeciesImageHelper.getThumbnail(latin) }
                if (bitmap != null) iv.setImageBitmap(bitmap)
            }
        }
        container.addView(view)
    }

    private fun addRemarksSection(container: LinearLayout, existingRemarks: String, dayEpoch: Long) {
        val layout = LayoutInflater.from(this).inflate(R.layout.view_report_remarks, container, false)
        etRemarks = layout.findViewById(R.id.etRemarks)
        etRemarks.setText(existingRemarks)
        
        layout.findViewById<Button>(R.id.btnSaveRemarks).setOnClickListener {
            val newRemarks = etRemarks.text.toString()
            lifecycleScope.launch(Dispatchers.IO) {
                database.tellingDao().updateDailyAnalysisRemarks(dayEpoch, newRemarks)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AiReportDetailsActiviteit, "Opmerkingen opgeslagen", Toast.LENGTH_SHORT).show()
                }
            }
        }
        container.addView(layout)
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
}
