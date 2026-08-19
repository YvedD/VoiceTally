package com.yvesds.vt5.ai

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * AiReportListActiviteit - Beheert het overzicht van teldag verslagen.
 * Geoptimaliseerd om ANR's te voorkomen door Toasts uit lussen te halen.
 */
class AiReportListActiviteit : AppCompatActivity() {

    private lateinit var database: VoiceTallyDatabase
    private lateinit var adapter: ReportListAdapter
    private val reportDays = mutableListOf<DayItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_ai_report_list)

        database = VoiceTallyDatabase.getDatabase(this)
        
        findViewById<View>(R.id.btnTerug).setOnClickListener { finish() }
        
        findViewById<View>(R.id.btnReconstructAll).setOnClickListener {
            showReconstructAllWarning()
        }

        val rv = findViewById<RecyclerView>(R.id.rvReports)
        rv.layoutManager = LinearLayoutManager(this)
        
        adapter = ReportListAdapter(reportDays, 
            onClick = { dayItem ->
                if (dayItem.status == "TODO") {
                    reconstructSingleDay(dayItem)
                } else {
                    openReportDetails(dayItem.dateMillis)
                }
            },
            onLongClick = { dayItem ->
                if (dayItem.status != "TODO") {
                    showDeleteConfirmation(dayItem)
                }
            }
        )
        rv.adapter = adapter

        loadDays()
    }

    private fun loadDays() {
        lifecycleScope.launch {
            val progress = com.yvesds.vt5.core.ui.ProgressDialogHelper.show(this@AiReportListActiviteit, "Archief scannen...")
            
            withContext(Dispatchers.IO) {
                // 1. Haal vederlichte metadata op
                val headers = database.tellingDao().getAllHeadersLight()
                val snapshot = try { ServerDataCache.getOrLoad(this@AiReportListActiviteit) } catch(_: Exception) { null }
                
                // 2. Haal ALLE geanalyseerde dagen in één keer op (Cruciaal tegen ANR!)
                val analyzedStatusMap = database.tellingDao().getAllAnalyzedDays().associateBy({ it.dayEpoch }, { it.type })
                
                val dayGroups = headers.groupBy { h ->
                    val seconds = h.begintijd.toLongOrNull() ?: 0L
                    val cal = Calendar.getInstance().apply { 
                        timeInMillis = seconds * 1000
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }
                    cal.timeInMillis
                }

                var currentYear = ""
                val items = dayGroups.mapNotNull { (millis, dayHeaders) ->
                    // Optioneel: Update voortgangsdialog met het jaar dat we verwerken
                    val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(millis))
                    if (year != currentYear) {
                        currentYear = year
                        withContext(Dispatchers.Main) {
                            com.yvesds.vt5.core.ui.ProgressDialogHelper.updateMessage(progress, "Verwerken jaar $currentYear...")
                        }
                    }

                    val totalEffort = dayHeaders.sumOf { (it.eindtijd.toLongOrNull() ?: 0L) - (it.begintijd.toLongOrNull() ?: 0L) }
                    if (totalEffort < 1800) return@mapNotNull null

                    val siteIds = dayHeaders.map { it.telpostid }.distinct()
                    val siteNames = siteIds.map { id -> snapshot?.sitesById?.get(id)?.telpostnaam ?: id }.joinToString(", ")

                    val type = analyzedStatusMap[millis / 1000]
                    val status = when (type) {
                        "LIVE", "scientific_v2" -> "AVAILABLE"
                        "RECONSTRUCTED" -> "RECONSTRUCTED"
                        else -> "TODO"
                    }

                    DayItem(millis, siteNames, status)
                }.sortedByDescending { it.dateMillis }

                withContext(Dispatchers.Main) {
                    reportDays.clear()
                    reportDays.addAll(items)
                    adapter.notifyDataSetChanged()
                    progress.dismiss()
                }
            }
        }
    }

    private fun showReconstructAllWarning() {
        AlertDialog.Builder(this)
            .setTitle("Batch Analyse")
            .setMessage("De app zal nu verslagen genereren voor alle openstaande dagen. Dit kan enige tijd duren.")
            .setPositiveButton("START") { _, _ -> startBatchReconstruction() }
            .setNegativeButton("ANNULEER", null)
            .show()
    }

    private fun showDeleteConfirmation(item: DayItem) {
        AlertDialog.Builder(this)
            .setTitle("Verslag Verwijderen")
            .setMessage("Wilt u deze analyse wissen uit het archief?")
            .setPositiveButton("WISSEN") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    database.tellingDao().deleteDailyAnalysis(item.dateMillis / 1000)
                    withContext(Dispatchers.Main) { loadDays() }
                }
            }
            .setNegativeButton("ANNULEER", null)
            .show()
    }

    private fun startBatchReconstruction() {
        val todoItems = reportDays.filter { it.status == "TODO" }
        if (todoItems.isEmpty()) return

        lifecycleScope.launch {
            val progress = com.yvesds.vt5.core.ui.ProgressDialogHelper.show(this@AiReportListActiviteit, "Batch Analyse...")
            withContext(Dispatchers.Default) {
                todoItems.forEachIndexed { index, item ->
                    val dateStr = SimpleDateFormat("d MMMM yyyy", Locale("nl", "BE")).format(Date(item.dateMillis))
                    withContext(Dispatchers.Main) {
                        com.yvesds.vt5.core.ui.ProgressDialogHelper.updateMessage(progress, "Bezig met (${index+1}/${todoItems.size}):\n$dateStr")
                    }
                    
                    AiEvaluator.reconstructAndSaveReport(this@AiReportListActiviteit, item.dateMillis)
                    
                    // Kleine delay om de UI te laten ademen en voortgang leesbaar te maken
                    delay(150.milliseconds) 
                }
            }
            progress.dismiss()
            loadDays()
        }
    }

    private fun reconstructSingleDay(item: DayItem) {
        lifecycleScope.launch {
            val progress = com.yvesds.vt5.core.ui.ProgressDialogHelper.show(this@AiReportListActiviteit, "Analyse uitvoeren...")
            withContext(Dispatchers.Default) {
                AiEvaluator.reconstructAndSaveReport(this@AiReportListActiviteit, item.dateMillis)
            }
            progress.dismiss()
            openReportDetails(item.dateMillis)
            loadDays()
        }
    }

    private fun openReportDetails(dateMillis: Long) {
        val intent = Intent(this, AiReportDetailsActiviteit::class.java)
        intent.putExtra("date_millis", dateMillis)
        startActivity(intent)
    }

    data class DayItem(val dateMillis: Long, val siteNames: String, val status: String)

    private inner class ReportListAdapter(
        private val items: List<DayItem>,
        private val onClick: (DayItem) -> Unit,
        private val onLongClick: (DayItem) -> Unit
    ) : RecyclerView.Adapter<ReportListAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_ai_report_list_card, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val sdf = SimpleDateFormat("EEEE d MMMM yyyy", Locale("nl", "BE"))
            holder.tvDate.text = sdf.format(Date(item.dateMillis)).replaceFirstChar { it.uppercase() }
            holder.tvSites.text = item.siteNames
            
            when (item.status) {
                "AVAILABLE" -> {
                    holder.tvStatus.text = "BESCHIKBAAR"; holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_available)
                }
                "RECONSTRUCTED" -> {
                    holder.tvStatus.text = "RECONSTRUCTIE"; holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_reconstructed)
                }
                else -> {
                    holder.tvStatus.text = "TO-DO"; holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_todo)
                }
            }
            holder.itemView.setOnClickListener { onClick(item) }
            holder.itemView.setOnLongClickListener { onLongClick(item); true }
        }

        override fun getItemCount() = items.size
        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvDate: TextView = v.findViewById(R.id.tvDate)
            val tvSites: TextView = v.findViewById(R.id.tvSites)
            val tvStatus: TextView = v.findViewById(R.id.tvStatusBadge)
        }
    }
}
