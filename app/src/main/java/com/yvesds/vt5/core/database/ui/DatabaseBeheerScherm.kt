package com.yvesds.vt5.core.database.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.patrykandpatrick.vico.core.cartesian.CartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.views.cartesian.CartesianChartView
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.batch.ExcelImportManager
import com.yvesds.vt5.core.database.entities.TellingHeader
import com.yvesds.vt5.core.opslag.AppDataStore
import com.yvesds.vt5.core.opslag.FileLogger
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.core.ui.ProgressDialogHelper
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.*

/**
 * DatabaseBeheerScherm - Beheert database imports, resets en overzichten.
 * Nu met Site-filter en herstelde witte labels.
 */
class DatabaseBeheerScherm : AppCompatActivity() {

    companion object {
        const val PREF_BATCH_IMPORT_ACTIVE = "pref_batch_import_active"
    }

    private lateinit var database: VoiceTallyDatabase
    private lateinit var fileLogger: FileLogger
    private lateinit var container: LinearLayout
    private var chartView: CartesianChartView? = null
    private val modelProducer = CartesianChartModelProducer()
    private lateinit var excelManager: ExcelImportManager
    private lateinit var safHelper: SaFStorageHelper
    private val debugDisplayFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")

    private lateinit var cbShowAantallen: CheckBox
    private lateinit var cbShowTellingen: CheckBox
    private lateinit var spinnerSiteFilter: Spinner

    private var availableSiteIds = mutableListOf<String?>() // null represents "All Sites"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_database_beheer)

        database = VoiceTallyDatabase.getDatabase(this)
        excelManager = ExcelImportManager(this)
        safHelper = SaFStorageHelper(this)
        fileLogger = FileLogger(this)
        container = findViewById(R.id.containerTabellen)
        
        cbShowAantallen = findViewById(R.id.cbShowAantallen)
        cbShowTellingen = findViewById(R.id.cbShowTellingen)
        spinnerSiteFilter = findViewById(R.id.spinnerSiteFilter)

        setupChartView()
        loadSitesForFilter()

        val filterListener = { _: View -> setupChartData() }
        cbShowAantallen.setOnClickListener(filterListener)
        cbShowTellingen.setOnClickListener(filterListener)

        spinnerSiteFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                setupChartData()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        findViewById<MaterialButton>(R.id.btnTerug).setOnClickListener { finish() }

        findViewById<MaterialButton>(R.id.btnTellingenLijst).setOnClickListener {
            startActivity(Intent(this, DatabaseTellingLijstActiviteit::class.java))
        }

        findViewById<MaterialButton>(R.id.btnSoortZoeken).setOnClickListener {
            startActivity(Intent(this, DatabaseSoortOverzichtActiviteit::class.java))
        }

        findViewById<MaterialButton>(R.id.btnAiEvaluation).setOnClickListener {
            startActivity(Intent(this, com.yvesds.vt5.ai.AiLogActiviteit::class.java))
        }

        findViewById<MaterialButton>(R.id.btnBatchImport).setOnClickListener {
            startExcelBatchImport()
        }

        findViewById<MaterialButton>(R.id.btnResetDatabase).setOnClickListener {
            startDoubleResetConfirmation("DE VOLLEDIGE DATABASE", ::performFullDatabaseReset)
        }

        refreshTableList()
        setupChartData()
    }

    private fun setupChartView() {
        chartView = findViewById(R.id.chartActivity)
        chartView?.modelProducer = modelProducer
        
        val colorBirds = VicoLineChartHelper.getColorTrek(this)
        val colorSessions = VicoLineChartHelper.getColorWind(this)
        val thickness = VicoLineChartHelper.getLineThicknessDp(this)

        cbShowAantallen.buttonTintList = android.content.res.ColorStateList.valueOf(colorBirds)
        cbShowAantallen.setTextColor(colorBirds)
        cbShowTellingen.buttonTintList = android.content.res.ColorStateList.valueOf(colorSessions)
        cbShowTellingen.setTextColor(colorSessions)

        val birdFormatter = CartesianValueFormatter { _, value, _ ->
            when {
                value >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", value / 1_000_000)
                value >= 1000 -> String.format(Locale.getDefault(), "%.0fk", value / 1000)
                else -> String.format(Locale.getDefault(), "%.0f", value)
            }
        }

        val birdLayer = LineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.Line(
                    fill = LineCartesianLayer.LineFill.single(Fill(colorBirds)),
                    thicknessDp = thickness
                )
            ),
            verticalAxisPosition = Axis.Position.Vertical.Start
        )

        val sessionLayer = LineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.Line(
                    fill = LineCartesianLayer.LineFill.single(Fill(colorSessions)),
                    thicknessDp = thickness
                )
            ),
            verticalAxisPosition = Axis.Position.Vertical.End
        )

        chartView?.chart = CartesianChart(
            layers = arrayOf(birdLayer, sessionLayer),
            startAxis = VerticalAxis.start(
                label = TextComponent(color = Color.WHITE, textSizeSp = 9f),
                valueFormatter = birdFormatter,
                itemPlacer = VerticalAxis.ItemPlacer.count(count = { 6 })
            ),
            endAxis = VerticalAxis.end(
                label = TextComponent(color = Color.WHITE, textSizeSp = 9f),
                valueFormatter = CartesianValueFormatter { _, v, _ -> String.format(Locale.getDefault(), "%.0f", v) }
            ),
            bottomAxis = VicoLineChartHelper.createMonthLabelAxis(VicoLineChartHelper.whiteAxisLabel)
        )
    }

    private fun loadSitesForFilter() {
        lifecycleScope.launch {
            val snapshot = try {
                withContext(Dispatchers.IO) { ServerDataCache.getOrLoad(this@DatabaseBeheerScherm) }
            } catch (_: Exception) { null }

            val siteList = mutableListOf<String>()
            siteList.add("Alle Telposten")
            availableSiteIds.clear()
            availableSiteIds.add(null)

            snapshot?.sitesById?.values?.sortedBy { it.telpostnaam }?.forEach { site ->
                siteList.add("${site.telpostnaam} (${site.telpostid})")
                availableSiteIds.add(site.telpostid)
            }

            val adapter = ArrayAdapter(this@DatabaseBeheerScherm, android.R.layout.simple_spinner_item, siteList)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerSiteFilter.adapter = adapter
        }
    }

    private fun startExcelBatchImport() {
        lifecycleScope.launch {
            val progress = ProgressDialogHelper.show(this@DatabaseBeheerScherm, "Scannen VT5/imports...")
            try {
                val importsDir = safHelper.getImportsDirSuspend()
                if (importsDir == null) {
                    progress.dismiss()
                    Toast.makeText(this@DatabaseBeheerScherm, "Imports map niet gevonden.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val files = importsDir.listFiles()
                val pairs = findExcelPairs(files)
                if (pairs.isEmpty()) {
                    progress.dismiss()
                    AlertDialog.Builder(this@DatabaseBeheerScherm)
                        .setTitle("Geen imports gevonden")
                        .setMessage("Geen Excel-paren gevonden.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@launch
                }
                progress.dismiss()
                showPairsConfirmDialog(pairs)
            } catch (e: Exception) {
                progress.dismiss()
                Toast.makeText(this@DatabaseBeheerScherm, "Fout: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun findExcelPairs(files: Array<DocumentFile>): List<ExcelPair> {
        val headers = files.filter { it.name?.startsWith("Trektellen_headerdata_") == true && it.name?.endsWith(".xlsx") == true }
        val dataFiles = files.filter { it.name?.startsWith("Trektellen_data_") == true && it.name?.endsWith(".xlsx") == true }
        val pairs = mutableListOf<ExcelPair>()
        headers.forEach { hFile ->
            val name = hFile.name ?: return@forEach
            val parts = name.replace("Trektellen_headerdata_", "").replace(".xlsx", "").split("_")
            if (parts.size >= 2) {
                val site = parts[0]
                val year = parts[1]
                val dFile = dataFiles.find { it.name == "Trektellen_data_${site}_${year}.xlsx" }
                if (dFile != null) pairs.add(ExcelPair(hFile, dFile, site, year))
            }
        }
        return pairs
    }

    private fun showPairsConfirmDialog(pairs: List<ExcelPair>) {
        val msg = StringBuilder("Klaar voor import:\n\n")
        pairs.forEach { msg.append("• Site ${it.site}, Jaar ${it.year}\n") }
        AlertDialog.Builder(this)
            .setTitle("Excel Batch Import")
            .setMessage(msg.toString())
            .setPositiveButton("Start Import") { _, _ -> executeBatchImport(pairs) }
            .setNegativeButton("Annuleren", null)
            .show()
    }

    private fun executeBatchImport(pairs: List<ExcelPair>) {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        lifecycleScope.launch {
            val progress = ProgressDialogHelper.show(this@DatabaseBeheerScherm, "Batch import starten...")
            var successCount = 0
            try {
                pairs.forEachIndexed { index, pair ->
                    val ok = excelManager.importPair(pair.header.uri, pair.data.uri) { msg, curr, total ->
                        withContext(Dispatchers.Main) {
                            ProgressDialogHelper.updateMessage(progress, "Site ${pair.site} ${pair.year}\n$msg ($curr)")
                        }
                    }
                    if (ok) successCount++
                }
            } catch (e: Exception) {
                Log.e("DatabaseBeheerScherm", "Fout: ${e.message}")
            } finally {
                progress.dismiss()
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            AlertDialog.Builder(this@DatabaseBeheerScherm)
                .setTitle("Import Voltooid")
                .setMessage("$successCount jaren geimporteerd.")
                .setPositiveButton("OK") { _, _ -> 
                    refreshTableList()
                    setupChartData()
                    showDebugImportSummary()
                }
                .show()
        }
    }

    private fun showDebugImportSummary() {
        lifecycleScope.launch(Dispatchers.IO) {
            val headers = database.tellingDao().getAllHeaders()
            if (headers.isEmpty()) return@launch
            val lastHeader = headers.first()
            val waarnemingen = database.tellingDao().getWaarnemingenList(lastHeader.tellingid)
            val startEpoch = lastHeader.begintijd.toLongOrNull() ?: 0L
            val readableDate = if (startEpoch > 0) Instant.ofEpochSecond(startEpoch).atZone(ZoneId.systemDefault()).format(debugDisplayFormatter) else lastHeader.begintijd
            val sb = StringBuilder().apply {
                append("--- DATABASE VERIFICATIE ---\n\n")
                append("HEADER DATA:\n")
                append("• Telling ID (volgnummer): ${lastHeader.tellingid}\n")
                append("• id: ${lastHeader.onlineid}\n")
                append("• Site: ${lastHeader.telpostid}\n")
                append("• Start: $readableDate\n")
                append("• Week: ${getWeekIndex(lastHeader.begintijd)}\n")
                append("• nRec (Aantal records): ${lastHeader.nrec}\n")
                append("• nSoort (Aantal soorten): ${lastHeader.nsoort}\n\n")
                append("WAARNEMINGEN (${waarnemingen.size}):\n")
                waarnemingen.take(10).forEach { w ->
                    append("[${w.soortid}] Aantal: ${w.aantal}\n")
                }
            }
            withContext(Dispatchers.Main) {
                val scroll = android.widget.ScrollView(this@DatabaseBeheerScherm).apply {
                    addView(TextView(this@DatabaseBeheerScherm).apply {
                        text = sb.toString()
                        setPadding(32, 32, 32, 32)
                        textSize = 12f
                        typeface = android.graphics.Typeface.MONOSPACE
                        setTextColor(Color.WHITE)
                    })
                }
                AlertDialog.Builder(this@DatabaseBeheerScherm).setTitle("Debug Import").setView(scroll).setPositiveButton("OK", null).show()
            }
        }
    }

    private fun setupChartData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val weekBirdTotals = FloatArray(53)
            val weekSessionTotals = FloatArray(53)
            
            val showBirds = cbShowAantallen.isChecked
            val showSessions = cbShowTellingen.isChecked
            
            val selectedSiteIdx = withContext(Dispatchers.Main) { spinnerSiteFilter.selectedItemPosition }
            val selectedSiteId = if (selectedSiteIdx >= 0 && selectedSiteIdx < availableSiteIds.size) availableSiteIds[selectedSiteIdx] else null

            // 1. Haal vogel-aantallen op via SQL Aggregatie
            if (showBirds) {
                val birdRows = if (selectedSiteId == null) {
                    database.tellingDao().getBirdCountsByWeekGlobal()
                } else {
                    database.tellingDao().getBirdCountsByWeekForSite(selectedSiteId)
                }
                birdRows.forEach { row ->
                    if (row.week in 1..52) weekBirdTotals[row.week] = row.count.toFloat()
                }
            }

            // 2. Haal sessie-aantallen op via SQL Aggregatie
            if (showSessions) {
                val sessionRows = if (selectedSiteId == null) {
                    database.tellingDao().getSessionCountsByWeekGlobal()
                } else {
                    database.tellingDao().getSessionCountsByWeekForSite(selectedSiteId)
                }
                sessionRows.forEach { row ->
                    if (row.week in 1..52) weekSessionTotals[row.week] = row.count.toFloat()
                }
            }

            withContext(Dispatchers.Main) {
                modelProducer.runTransaction {
                    lineSeries { series(weekBirdTotals.toList()) }
                    lineSeries { series(weekSessionTotals.toList()) }
                }
            }
        }
    }

    private fun getWeekIndex(begintijd: String): Int {
        return try {
            val epoch = begintijd.toLongOrNull() ?: return 0
            val instant = if (epoch > 9999999999L) Instant.ofEpochMilli(epoch) else Instant.ofEpochSecond(epoch)
            val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
            val week = ((date.dayOfYear - 1) / 7) + 1
            if (week > 52) 52 else week
        } catch (e: Exception) { 0 }
    }

    private fun startDoubleResetConfirmation(targetName: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("$targetName Leegmaken?")
            .setPositiveButton("JA") { _, _ -> 
                AlertDialog.Builder(this).setTitle("Zeker?").setPositiveButton("WISSEN") { _, _ -> onConfirm() }.show()
            }.show()
    }

    private fun performFullDatabaseReset() {
        lifecycleScope.launch(Dispatchers.IO) {
            database.tellingDao().clearAllHeaders()
            database.tellingDao().clearAllWaarnemingen()
            database.tellingDao().clearAllAiLogs()
            database.tellingDao().clearWeatherArchive()
            database.tellingDao().clearDailyAnalysis()
            database.tellingDao().clearPhenologyVault()
            database.tellingDao().clearSpeciesImages()
            database.tellingDao().clearSyncLogs()
            AppDataStore.resetTellingId(this@DatabaseBeheerScherm) 
            com.yvesds.vt5.core.opslag.EffortStore.resetAll(this@DatabaseBeheerScherm) 
            withContext(Dispatchers.Main) { refreshTableList(); setupChartData() }
        }
    }

    private fun refreshTableList() {
        lifecycleScope.launch(Dispatchers.IO) {
            val hCount = database.tellingDao().countHeaders()
            val wCount = database.tellingDao().countWaarnemingen()
            val aCount = database.tellingDao().countAiLogs()
            val weCount = database.tellingDao().countWeatherArchive()
            val vCount = database.tellingDao().countPhenologyVault()
            val rCount = database.tellingDao().countDailyAnalysis()
            val iCount = database.tellingDao().countSpeciesImages()
            val sCount = database.tellingDao().countSyncLogs()

            withContext(Dispatchers.Main) {
                container.removeAllViews()
                addTableCard("Sessies", hCount) { 
                    lifecycleScope.launch(Dispatchers.IO) { 
                        database.tellingDao().clearAllHeaders()
                        AppDataStore.resetTellingId(this@DatabaseBeheerScherm) 
                        com.yvesds.vt5.core.opslag.EffortStore.resetAll(this@DatabaseBeheerScherm) 
                        withContext(Dispatchers.Main) { refreshTableList(); setupChartData() } 
                    } 
                }
                addTableCard("Waarnemingen", wCount) { lifecycleScope.launch(Dispatchers.IO) { database.tellingDao().clearAllWaarnemingen(); withContext(Dispatchers.Main) { refreshTableList(); setupChartData() } } }
                addTableCard("BSI Vault", vCount) { lifecycleScope.launch(Dispatchers.IO) { database.tellingDao().clearPhenologyVault(); withContext(Dispatchers.Main) { refreshTableList() } } }
                addTableCard("Dag Verslagen", rCount) { lifecycleScope.launch(Dispatchers.IO) { database.tellingDao().clearDailyAnalysis(); withContext(Dispatchers.Main) { refreshTableList() } } }
                addTableCard("AI Logs", aCount) { lifecycleScope.launch(Dispatchers.IO) { database.tellingDao().clearAllAiLogs(); withContext(Dispatchers.Main) { refreshTableList() } } }
                addTableCard("Weer", weCount) { lifecycleScope.launch(Dispatchers.IO) { database.tellingDao().clearWeatherArchive(); withContext(Dispatchers.Main) { refreshTableList() } } }
                addTableCard("Vogel Beelden", iCount) { lifecycleScope.launch(Dispatchers.IO) { database.tellingDao().clearSpeciesImages(); withContext(Dispatchers.Main) { refreshTableList() } } }
                addTableCard("Sync Logs", sCount) { lifecycleScope.launch(Dispatchers.IO) { database.tellingDao().clearSyncLogs(); withContext(Dispatchers.Main) { refreshTableList() } } }
            }
        }
    }

    private fun addTableCard(name: String, count: Int, onReset: () -> Unit) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_db_tabel, container, false)
        view.findViewById<TextView>(R.id.tvTabelNaam).text = name
        view.findViewById<TextView>(R.id.tvRecordsCount).text = "$count records"
        view.findViewById<MaterialButton>(R.id.btnWissenRecords).setOnClickListener { startDoubleResetConfirmation(name, onReset) }
        view.findViewById<View>(R.id.btnWissenTabel).visibility = View.GONE
        container.addView(view)
    }

    private data class ExcelPair(val header: DocumentFile, val data: DocumentFile, val site: String, val year: String)
}
