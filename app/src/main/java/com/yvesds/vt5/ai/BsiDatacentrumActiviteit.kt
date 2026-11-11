package com.yvesds.vt5.ai

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.batch.ExcelImportManager
import com.yvesds.vt5.core.database.ui.DatabaseBeheerScherm
import com.yvesds.vt5.core.database.weather.WeatherEnrichmentManager
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.core.ui.ProgressDialogHelper
import com.yvesds.vt5.features.export.ui.TrektellenExportTestActiviteit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BsiDatacentrumActiviteit : AppCompatActivity() {

    private lateinit var tvBsiStatus: TextView
    private lateinit var tvDbStats: TextView
    private lateinit var btnTerug: MaterialButton
    private lateinit var btnStartAutoPipeline: MaterialButton

    private lateinit var btnStep1Export: MaterialButton
    private lateinit var btnStep2Import: MaterialButton
    private lateinit var btnStep3Weather: MaterialButton
    private lateinit var btnStep4Train: MaterialButton

    private lateinit var btnAiForecast3Days: MaterialButton
    private lateinit var btnKrentenMonitor: MaterialButton
    private lateinit var btnAiDailyReports: MaterialButton
    private lateinit var btnDatabaseBeheer: MaterialButton

    private lateinit var safStorageHelper: SaFStorageHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_bsi_datacentrum)

        safStorageHelper = SaFStorageHelper(this)

        // Bind UI Elementen
        btnTerug = findViewById(R.id.btnTerug)
        tvBsiStatus = findViewById(R.id.tvBsiStatus)
        tvDbStats = findViewById(R.id.tvDbStats)
        btnStartAutoPipeline = findViewById(R.id.btnStartAutoPipeline)

        btnStep1Export = findViewById(R.id.btnStep1Export)
        btnStep2Import = findViewById(R.id.btnStep2Import)
        btnStep3Weather = findViewById(R.id.btnStep3Weather)
        btnStep4Train = findViewById(R.id.btnStep4Train)

        btnAiForecast3Days = findViewById(R.id.btnAiForecast3Days)
        btnKrentenMonitor = findViewById(R.id.btnKrentenMonitor)
        btnAiDailyReports = findViewById(R.id.btnAiDailyReports)
        btnDatabaseBeheer = findViewById(R.id.btnDatabaseBeheer)

        // TERUG KNOP
        btnTerug.setOnClickListener {
            finish()
        }

        // INDIVIDUELE STAP KNOPPEN
        btnStep1Export.setOnClickListener {
            startActivity(Intent(this, TrektellenExportTestActiviteit::class.java))
        }

        btnStep2Import.setOnClickListener {
            runExcelBatchImport()
        }

        btnStep3Weather.setOnClickListener {
            runWeatherEnrichment()
        }

        btnStep4Train.setOnClickListener {
            runAiTrainingFlow()
        }

        // AUTOMATISCHE 1-KLIK BSI PIJPLIJN
        btnStartAutoPipeline.setOnClickListener {
            runAutomatedBsiPipeline()
        }

        // RESULTATEN & AI OUTPUT KNOPPEN
        btnAiForecast3Days.setOnClickListener {
            startActivity(Intent(this, AiForecastScherm::class.java))
        }

        btnKrentenMonitor.setOnClickListener {
            startActivity(Intent(this, KrentenMonitorActiviteit::class.java))
        }

        btnAiDailyReports.setOnClickListener {
            startActivity(Intent(this, AiReportListActiviteit::class.java))
        }

        btnDatabaseBeheer.setOnClickListener {
            startActivity(Intent(this, DatabaseBeheerScherm::class.java))
        }

        // Update BSI status en tellingen
        updateBsiStatus()
    }

    override fun onResume() {
        super.onResume()
        updateBsiStatus()
    }

    /**
     * Controleert de status van de Room DB en BSI AI-bestanden om de live statusbalk (🔴/🟡/🟢) in te stellen.
     */
    private fun updateBsiStatus() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = VoiceTallyDatabase.getDatabase(this@BsiDatacentrumActiviteit)
            val count = db.tellingDao().getAllHeaders().size

            // Controleer of getrainde BSI bestanden aanwezig zijn
            val hasTrainedModel = safStorageHelper.readModelFile("bsi_weights.json") != null ||
                    safStorageHelper.readModelFile("species_weights.json") != null

            withContext(Dispatchers.Main) {
                tvDbStats.text = getString(R.string.bsi_datacentrum_db_count_format, count)

                when {
                    count == 0 -> {
                        tvBsiStatus.text = getString(R.string.bsi_status_red)
                    }
                    !hasTrainedModel -> {
                        tvBsiStatus.text = getString(R.string.bsi_status_orange)
                    }
                    else -> {
                        tvBsiStatus.text = getString(R.string.bsi_status_green)
                    }
                }
            }
        }
    }

    /**
     * Start de Geautomatiseerde BSI Pijplijn met visuele voortgangsindicator en duidelijke keuzes.
     */
    private fun runAutomatedBsiPipeline() {
        lifecycleScope.launch {
            val db = VoiceTallyDatabase.getDatabase(this@BsiDatacentrumActiviteit)
            val count = withContext(Dispatchers.IO) { db.tellingDao().getAllHeaders().size }
            
            val importsDir = safStorageHelper.getImportsDirSuspend()
            val hasImportFiles = importsDir?.listFiles()?.any { 
                it.name?.startsWith("Trektellen_headerdata_") == true 
            } == true

            if (count == 0 && !hasImportFiles) {
                // HET SYSTEEM IS LEEG: Geef de gebruiker de keuze tussen Trektellen.nl import OF puur eigen invoer!
                AlertDialog.Builder(this@BsiDatacentrumActiviteit)
                    .setTitle(R.string.bsi_datacentrum_titel)
                    .setMessage("De database bevat momenteel nog geen tellingen.\n\n" +
                            "Bio Statistic Intelligence (BSI) werkt het meest nauwkeurig met historische data van Trektellen.nl, maar kan ook puur op je eigen ingevoerde tellingen draaien.\n\n" +
                            "Hoe wil je starten?")
                    .setPositiveButton("Trektellen Data Importeren") { _, _ ->
                        startActivity(Intent(this@BsiDatacentrumActiviteit, TrektellenExportTestActiviteit::class.java))
                    }
                    .setNeutralButton("Alleen Eigen Invoer Gebruiken") { _, _ ->
                        AlertDialog.Builder(this@BsiDatacentrumActiviteit)
                            .setTitle("Alleen Eigen Invoer Gebruiken")
                            .setMessage("Je hebt gekozen om alleen met je eigen handmatige tellingen te werken.\n\n" +
                                    "⚠️ LET OP: In het begin zal BSI minder nauwkeurige voorspellingen en grafieken tonen omdat er nog weinig historie is. Naarmate je meer tellingen invoert via de app, wordt BSI automatisch slimmer op jouw telpost.\n\n" +
                                    "Er worden op dit moment géén bestanden gedownload van Trektellen.nl.")
                            .setPositiveButton("Begrepen") { _, _ ->
                                writeLogToVt5Logs("PIPELINE_MODE", "Gebruiker kiest voor alleen eigen invoer (geen Trektellen import).")
                                executePipelineFlowWithProgress(skipImportStep = true)
                            }
                            .setNegativeButton("Annuleren", null)
                            .show()
                    }
                    .setNegativeButton("Annuleren", null)
                    .show()
                return@launch
            }

            AlertDialog.Builder(this@BsiDatacentrumActiviteit)
                .setTitle(R.string.bsi_datacentrum_titel)
                .setMessage("De BSI Pijplijn zal nu de volgende stappen automatisch verwerken:\n\n" +
                        "1. Excel-bestanden uit Documents/VT5/imports/ importeren in DB\n" +
                        "2. Historische weerdata koppelen via Yr.no\n" +
                        "3. BSI Neuraal Netwerk trainen op jouw telpost\n\n" +
                        "Wil je de verwerking nu starten?")
                .setPositiveButton("Start BSI Pijplijn") { _, _ ->
                    executePipelineFlowWithProgress(skipImportStep = false)
                }
                .setNegativeButton("Annuleren", null)
                .show()
        }
    }

    /**
     * Voert de Pijplijn uit met een live visuele ProgressDialog voor directe feedback.
     */
    private fun executePipelineFlowWithProgress(skipImportStep: Boolean = false) {
        val progressDialog = ProgressDialogHelper.show(
            this,
            "BSI Pijplijn gestart...\nEen ogenblik geduld A.U.B."
        )

        lifecycleScope.launch {
            val importManager = ExcelImportManager(this@BsiDatacentrumActiviteit)
            val weatherManager = WeatherEnrichmentManager(this@BsiDatacentrumActiviteit)
            val trainer = Trainer(this@BsiDatacentrumActiviteit, ModelStore(this@BsiDatacentrumActiviteit))

            var importedPairsCount = 0

            try {
                // 1. STAP 1: Batch Import uit Documents/VT5/imports/
                if (!skipImportStep) {
                    ProgressDialogHelper.updateMessage(
                        progressDialog,
                        "[Stap 1/3] Excel bestanden scannen in Documents/VT5/imports/..."
                    )

                    val importsDir = safStorageHelper.getImportsDirSuspend()

                    if (importsDir != null) {
                        val files = importsDir.listFiles()
                        val headerFiles = files.filter { it.name?.startsWith("Trektellen_headerdata_") == true && it.name?.endsWith(".xlsx") == true }
                        val totalHeaders = headerFiles.size

                        var idx = 0
                        for (headerFile in headerFiles) {
                            idx++
                            val hName = headerFile.name ?: continue
                            val dataName = hName.replace("Trektellen_headerdata_", "Trektellen_data_")
                            val dataFile = files.find { it.name == dataName }

                            ProgressDialogHelper.updateMessage(
                                progressDialog,
                                "[Stap 1/3] Importeren in Database ($idx/$totalHeaders):\n$hName"
                            )

                            if (dataFile != null) {
                                withContext(Dispatchers.IO) {
                                    val ok = importManager.importPair(headerFile.uri, dataFile.uri) { msg, c, t ->
                                        Log.d("BsiPipeline", "Import ($c/$t): $msg")
                                    }
                                    if (ok) importedPairsCount++
                                }
                            }
                        }
                    }
                    writeLogToVt5Logs("PIPELINE_STEP1", "Geïmporteerde koppel-bestanden: $importedPairsCount")
                } else {
                    writeLogToVt5Logs("PIPELINE_STEP1", "Stap 1 overgeslagen op verzoek van gebruiker (Alleen eigen invoer).")
                }

                // 2. STAP 2: Weerverrijking via Yr.no
                ProgressDialogHelper.updateMessage(
                    progressDialog,
                    "[Stap 2/3] Historische weerdata koppelen via Yr.no..."
                )

                val weatherOk = withContext(Dispatchers.IO) {
                    weatherManager.performEnrichment { msg, curr, total ->
                        lifecycleScope.launch(Dispatchers.Main) {
                            ProgressDialogHelper.updateMessage(
                                progressDialog,
                                "[Stap 2/3] Yr.no Weerdata ($curr/$total):\n$msg"
                            )
                        }
                    }
                }

                writeLogToVt5Logs("PIPELINE_STEP2", "Weerverrijking voltooid: $weatherOk")

                // 3. STAP 3: BSI Model Trainen
                ProgressDialogHelper.updateMessage(
                    progressDialog,
                    "[Stap 3/3] BSI Neuraal Netwerk trainen..."
                )

                withContext(Dispatchers.IO) {
                    trainer.runOnDeviceTraining { msg, curr, total ->
                        lifecycleScope.launch(Dispatchers.Main) {
                            ProgressDialogHelper.updateMessage(
                                progressDialog,
                                "[Stap 3/3] BSI Training ($curr/$total%):\n$msg"
                            )
                        }
                    }
                }

                writeLogToVt5Logs("PIPELINE_STEP3", "BSI Neuraal Netwerk succesvol getraind.")

                updateBsiStatus()

            } catch (e: Exception) {
                Log.e("BsiDatacentrum", "Fout in BSI Pijplijn: ${e.message}", e)
                writeLogToVt5Logs("PIPELINE_ERROR", "Fout in BSI Pijplijn: ${e.message}")
            } finally {
                // Verberg de visuele progress dialoog
                try { progressDialog.dismiss() } catch (_: Exception) {}
            }

            // Eindrapportage tonen
            val successMessage = if (skipImportStep) {
                "BSI Pijplijn voltooid op basis van je eigen ingevoerde tellingen.\n\n" +
                "⚠️ Denk eraan: BSI zal in het begin minder nauwkeurig zijn omdat er nog weinig historie is. Elke nieuwe telling die je invoert via de app maakt BSI automatisch slimmer!"
            } else {
                "Gegevens verwerkt ($importedPairsCount koppel-bestanden geïmporteerd), weerdata gekoppeld en BSI Neuraal Netwerk getraind.\n\nDe 3-daagse trekprognoses, KrentenMonitor en dagrapporten zijn nu 100% operationeel!"
            }

            AlertDialog.Builder(this@BsiDatacentrumActiviteit)
                .setTitle("BSI Pijplijn Voltooid! 🎉")
                .setMessage(successMessage)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun runExcelBatchImport() {
        val progress = ProgressDialogHelper.show(this, "Batch import starten uit Documents/VT5/imports/...")
        lifecycleScope.launch {
            val importManager = ExcelImportManager(this@BsiDatacentrumActiviteit)
            val importsDir = safStorageHelper.getImportsDirSuspend()
            var count = 0

            if (importsDir != null) {
                val files = importsDir.listFiles()
                val headerFiles = files.filter { it.name?.startsWith("Trektellen_headerdata_") == true && it.name?.endsWith(".xlsx") == true }

                for (headerFile in headerFiles) {
                    val hName = headerFile.name ?: continue
                    val dataName = hName.replace("Trektellen_headerdata_", "Trektellen_data_")
                    val dataFile = files.find { it.name == dataName }

                    if (dataFile != null) {
                        ProgressDialogHelper.updateMessage(progress, "Importeren:\n$hName")
                        val ok = withContext(Dispatchers.IO) {
                            importManager.importPair(headerFile.uri, dataFile.uri) { msg, _, _ ->
                                Log.d("BsiImport", msg)
                            }
                        }
                        if (ok) count++
                    }
                }
            }

            try { progress.dismiss() } catch (_: Exception) {}
            updateBsiStatus()
            Toast.makeText(this@BsiDatacentrumActiviteit, "Import voltooid: $count koppel-bestanden verwerkt.", Toast.LENGTH_LONG).show()
        }
    }

    private fun runWeatherEnrichment() {
        val progress = ProgressDialogHelper.show(this, "Weerdata ophalen via Yr.no...")
        lifecycleScope.launch {
            val weatherManager = WeatherEnrichmentManager(this@BsiDatacentrumActiviteit)
            val ok = withContext(Dispatchers.IO) {
                weatherManager.performEnrichment { msg, curr, total ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        ProgressDialogHelper.updateMessage(progress, "Yr.no Weerdata ($curr/$total):\n$msg")
                    }
                }
            }
            try { progress.dismiss() } catch (_: Exception) {}
            Toast.makeText(this@BsiDatacentrumActiviteit, if (ok) "Weerdata succesvol bijgewerkt!" else "Weerdata bijwerken afgerond.", Toast.LENGTH_LONG).show()
        }
    }

    private fun runAiTrainingFlow() {
        val progress = ProgressDialogHelper.show(this, "BSI Neuraal Netwerk Trainen...")
        lifecycleScope.launch {
            val trainer = Trainer(this@BsiDatacentrumActiviteit, ModelStore(this@BsiDatacentrumActiviteit))
            withContext(Dispatchers.IO) {
                trainer.runOnDeviceTraining { msg, curr, total ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        ProgressDialogHelper.updateMessage(progress, "BSI Training ($curr/$total%):\n$msg")
                    }
                }
            }
            try { progress.dismiss() } catch (_: Exception) {}
            updateBsiStatus()
            Toast.makeText(this@BsiDatacentrumActiviteit, "BSI Training succesvol afgerond!", Toast.LENGTH_LONG).show()
        }
    }

    private fun writeLogToVt5Logs(eventTag: String, details: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val now = Date()
                val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)
                val timeLogStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(now)

                val filename = "import_test_log_$timestampStr.txt"
                val logLine = "[$timeLogStr] [$eventTag]\n$details\n----------------------------------------\n"

                val vt5Dir = safStorageHelper.getVt5DirIfExistsSuspend() ?: return@launch
                val logsDir = safStorageHelper.findOrCreateDirectorySuspend(vt5Dir, "logs") ?: return@launch

                val logFile = logsDir.findFile(filename) ?: logsDir.createFile("text/plain", filename)
                if (logFile != null) {
                    contentResolver.openOutputStream(logFile.uri, "wa")?.use { out ->
                        out.write(logLine.toByteArray(Charsets.UTF_8))
                    }
                }
            } catch (e: Exception) {
                Log.e("BsiDatacentrum", "Fout bij schrijven naar Documents/VT5/logs: ${e.message}")
            }
        }
    }
}