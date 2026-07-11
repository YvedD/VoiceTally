package com.yvesds.vt5.core.database.ui

import android.content.Intent
import android.graphics.Color  // benodigd voor secondDlg kleur-parsing
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.patrykandpatrick.vico.core.cartesian.CartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.views.cartesian.CartesianChartView
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.entities.TellingHeader
import com.yvesds.vt5.core.opslag.FileLogger
import com.yvesds.vt5.core.ui.DialogStyler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.system.exitProcess

import com.yvesds.vt5.core.import.CsvImportManager
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns

/**
 * DatabaseBeheerScherm: Grafische interface voor het beheren van de Room database.
 * Mogelijkheden: Overzicht tabellen, records tellen, records wissen, tabellen droppen.
 */
class DatabaseBeheerScherm : AppCompatActivity() {

    private lateinit var database: VoiceTallyDatabase
    private lateinit var fileLogger: FileLogger
    private lateinit var container: LinearLayout
    private var chartView: CartesianChartView? = null
    private val modelProducer = CartesianChartModelProducer()
    private lateinit var importManager: CsvImportManager

    private val selectCsvFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            handleCsvImport(uris)
        }
    }

    // Kleuren komen uit colors.xml sectie "grafiekkleuren".
    // Aanpassen: verander de resource-naam hieronder als je een andere kleur wil.
    private val chartLineColor   get() = ContextCompat.getColor(this, R.color.grafiek_lijnkleur)
    private val chartBgColor     get() = ContextCompat.getColor(this, R.color.grafiek_achtergrondkleur)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_database_beheer)

        database = VoiceTallyDatabase.getDatabase(this)
        importManager = CsvImportManager(this, database.tellingDao())
        fileLogger = FileLogger(this)
        container = findViewById(R.id.containerTabellen)
        chartView = findViewById(R.id.chartActivity)

        findViewById<MaterialButton>(R.id.btnTerug).setOnClickListener { finish() }

        findViewById<MaterialButton>(R.id.btnTellingenLijst).setOnClickListener {
            startActivity(android.content.Intent(this, DatabaseTellingLijstActiviteit::class.java))
        }

        findViewById<MaterialButton>(R.id.btnSoortZoeken).setOnClickListener {
            startActivity(android.content.Intent(this, DatabaseSoortOverzichtActiviteit::class.java))
        }

        findViewById<MaterialButton>(R.id.btnImportCsv).setOnClickListener {
            selectCsvFiles.launch(arrayOf("text/comma-separated-values", "text/csv"))
        }

        findViewById<MaterialButton>(R.id.btnResetDatabase).setOnClickListener {
            startDoubleResetConfirmation()
        }

        initChart()
        setupChart()
        refreshTableList()
    }

    private fun handleCsvImport(uris: List<Uri>) {
        lifecycleScope.launch {
            var headerInserted = 0
            var waarnemingInserted = 0
            var skippedRows = 0
            val warnings = mutableListOf<String>()
            val errors = mutableListOf<String>()

            withContext(Dispatchers.IO) {
                val recognizedFiles = mutableListOf<CsvImportFile>()

                for (uri in uris) {
                    val fileName = getFileName(uri)
                    val parsed = parseTrektellenFileName(fileName)
                    if (parsed == null) {
                        errors.add(
                            "Bestand overgeslagen ($fileName): naam moet 'Trektellen_data_<telpostid>_<jaar>.csv' of 'Trektellen_headerdata_<telpostid>_<jaar>.csv' zijn"
                        )
                        continue
                    }
                    recognizedFiles.add(CsvImportFile(uri, fileName, parsed.fileType, parsed.telpostId, parsed.year))
                }

                val headerFiles = recognizedFiles.filter { it.fileType == CsvFileType.HEADER }
                val dataFiles = recognizedFiles.filter { it.fileType == CsvFileType.DATA }

                if (headerFiles.size != 1 || dataFiles.size != 1) {
                    errors.add(
                        "Import vereist exact 1 headerbestand en 1 databestand voor hetzelfde jaar/telpost (multi-jaar import is uitgeschakeld)."
                    )
                }

                val headerFile = headerFiles.singleOrNull()
                val dataFile = dataFiles.singleOrNull()

                if (headerFile != null && dataFile != null) {
                    if (headerFile.year != dataFile.year) {
                        errors.add(
                            "Bestanden horen niet bij elkaar: jaartal verschilt (${headerFile.fileName} -> ${headerFile.year}, ${dataFile.fileName} -> ${dataFile.year})."
                        )
                    }
                    if (headerFile.telpostId != dataFile.telpostId) {
                        errors.add(
                            "Bestanden horen niet bij elkaar: telpostid verschilt (${headerFile.fileName} -> ${headerFile.telpostId}, ${dataFile.fileName} -> ${dataFile.telpostId})."
                        )
                    }
                }

                if (errors.isNotEmpty()) {
                    return@withContext
                }

                // Use combined importer which reads headers sequentially and links matching data rows by online id (countid)
                val headerStream = contentResolver.openInputStream(headerFile!!.uri)
                val dataStream = contentResolver.openInputStream(dataFile!!.uri)
                if (headerStream == null) {
                    errors.add("Bestand niet leesbaar (${headerFile.fileName})")
                } else if (dataStream == null) {
                    errors.add("Bestand niet leesbaar (${dataFile.fileName})")
                } else {
                    headerStream.use { hs ->
                        dataStream.use { ds ->
                            val result = importManager.importHeadersAndData(hs, ds)
                            result.onSuccess { stats ->
                                headerInserted += stats.insertedHeaders
                                waarnemingInserted += stats.insertedWaarnemingen
                                skippedRows += stats.skipped
                                warnings.addAll(stats.warnings.map { "${headerFile.fileName}/${dataFile.fileName}: $it" })
                            }.onFailure {
                                errors.add("Combined import fout (${headerFile.fileName}, ${dataFile.fileName}): ${it.message}")
                            }
                        }
                    }
                }
            }

            if (errors.isEmpty()) {
                val successMessage = buildString {
                    append("Import geslaagd!\n")
                    append("$headerInserted sessies en $waarnemingInserted waarnemingen toegevoegd.")
                    if (skippedRows > 0) {
                        append("\n$skippedRows rijen overgeslagen (onvolledig of zonder geldige koppeling).")
                    }
                    if (warnings.isNotEmpty()) {
                        append("\n\nWaarschuwingen:\n")
                        append(warnings.take(6).joinToString("\n"))
                        if (warnings.size > 6) {
                            append("\n... en nog ${warnings.size - 6} waarschuwingen")
                        }
                    }
                }
                showMandatoryRestartAfterImportDialog(successMessage)
            } else {
                val errorMessage = buildString {
                    append("Import voltooid met fouten:\n")
                    append(errors.joinToString("\n"))
                    if (headerInserted > 0 || waarnemingInserted > 0) {
                        append("\n\nReeds geimporteerd: $headerInserted sessies, $waarnemingInserted waarnemingen")
                    }
                    if (warnings.isNotEmpty()) {
                        append("\n\nWaarschuwingen:\n")
                        append(warnings.take(6).joinToString("\n"))
                        if (warnings.size > 6) {
                            append("\n... en nog ${warnings.size - 6} waarschuwingen")
                        }
                    }
                }

                AlertDialog.Builder(this@DatabaseBeheerScherm)
                    .setTitle("CSV Import Resultaat")
                    .setMessage(errorMessage)
                    .setPositiveButton("OK") { _, _ ->
                        refreshTableList()
                        setupChart()
                    }
                    .show()
            }
        }
    }

    private fun parseTrektellenFileName(fileName: String): ParsedTrektellenFileName? {
        val normalized = fileName.substringAfterLast('/')
        val pattern = Regex("^Trektellen_(headerdata|data)_(\\d+)_(\\d{4})\\.csv$", RegexOption.IGNORE_CASE)
        val match = pattern.matchEntire(normalized) ?: return null

        val type = when (match.groupValues[1].lowercase(Locale.ROOT)) {
            "headerdata" -> CsvFileType.HEADER
            "data" -> CsvFileType.DATA
            else -> return null
        }

        return ParsedTrektellenFileName(
            fileType = type,
            telpostId = match.groupValues[2],
            year = match.groupValues[3],
        )
    }

    private fun showMandatoryRestartAfterImportDialog(importMessage: String) {
        val finalMessage = buildString {
            append(importMessage)
            append("\n\nVT5 moet nu verplicht herstarten. De import heeft veel data verwerkt; een schone herstart reset geheugen en interne caches zodat de app niet vastloopt (ANR).")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("CSV Import Resultaat")
            .setMessage(finalMessage)
            .setPositiveButton("Nu herstarten") { _, _ ->
                restartAppProcess()
            }
            .setCancelable(false)
            .show()
        dialog.setCanceledOnTouchOutside(false)
    }

    private fun restartAppProcess() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

        if (launchIntent == null) {
            Toast.makeText(
                this,
                "Automatische herstart mislukt. Sluit VT5 af en start opnieuw.",
                Toast.LENGTH_LONG,
            ).show()
            finishAffinity()
            return
        }

        val restartIntent = Intent.makeRestartActivityTask(launchIntent.component)
        startActivity(restartIntent)

        finishAffinity()
        exitProcess(0)
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "unknown"
    }

    private fun initChart() {
        val view = chartView ?: return
        try {
            val lineLayer = VicoLineChartHelper.createLineLayer(chartLineColor)
            val bottomAxis = VicoLineChartHelper.createMonthLabelAxis()
            val topAxis = VicoLineChartHelper.createWeeklyTickAxis()

            view.chart = CartesianChart(
                lineLayer,
                bottomAxis = bottomAxis,
                topAxis = topAxis,
                startAxis = VicoLineChartHelper.createCountAxis(),
            )
            view.setBackgroundColor(chartBgColor)
            view.modelProducer = modelProducer
        } catch (e: Exception) {
            Log.e("DatabaseBeheer", "Vico initChart failed: ${e.message}", e)
        }
    }

    private fun setupChart() {
        val view = chartView ?: return
        lifecycleScope.launch {
            try {
                val allHeaders = withContext(Dispatchers.IO) {
                    database.tellingDao().getAllHeaders()
                }

                if (allHeaders.isNotEmpty()) {
                    val weeklySessionCounts = MutableList(52) { 0 }

                    for (header in allHeaders) {
                        val weekIndex = getWeekOfYear(header) - 1
                        if (weekIndex in 0..51) {
                            // Elke afzonderlijke sessie (header) telt als +1.
                            weeklySessionCounts[weekIndex] += 1
                        }
                    }

                    withContext(Dispatchers.Main) {
                        modelProducer.runTransaction {
                            lineSeries {
                                series(weeklySessionCounts.map { it.toFloat() })
                            }
                        }
                        view.invalidate()
                    }
                } else {
                    fileLogger.warn("ActivityChart: No headers found in database")
                }
            } catch (e: Exception) {
                Log.e("DatabaseBeheer", "setupChart failed: ${e.message}", e)
            }
        }
    }

    private fun getWeekOfYear(header: TellingHeader): Int {
        val epoch = header.begintijd.trim().toLongOrNull()
        if (epoch == null) {
            Log.w("DatabaseBeheer", "ActivityChart: ongeldige begintijd '${header.begintijd}' voor telling ${header.tellingid}")
            return 1
        }

        val epochSeconds = if (epoch > 9_999_999_999L) epoch / 1000 else epoch
        val zone = runCatching {
            val zoneId = header.timezoneid.ifBlank { "Europe/Brussels" }
            ZoneId.of(zoneId)
        }.getOrDefault(ZoneId.of("Europe/Brussels"))

        val localDate = Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate()
        val week = localDate.get(WeekFields.of(Locale.getDefault()).weekOfYear())
        return week.coerceIn(1, 52)
    }

    private fun startDoubleResetConfirmation() {
        val firstDlg = AlertDialog.Builder(this)
            .setTitle("Database Resetten?")
            .setMessage("Wil je echt alle gegevens uit de database verwijderen? Dit kan niet ongedaan worden gemaakt.")
            .setPositiveButton("Verwijderen") { _, _ ->
                showSecondResetConfirmation()
            }
            .setNegativeButton(R.string.annuleer, null)
            .show()
        DialogStyler.apply(firstDlg)
    }

    private fun showSecondResetConfirmation() {
        val secondDlg = AlertDialog.Builder(this)
            .setTitle("Laatste waarschuwing")
            .setMessage("Weet u het zeker?")
            .setPositiveButton("JA, RESET ALLES") { _, _ ->
                performFullDatabaseReset()
            }
            .setNegativeButton(R.string.annuleer, null)
            .show()

        DialogStyler.apply(secondDlg)

        // Specifieke styling voor de tweede waarschuwing (oranje kleur)
        secondDlg.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.parseColor("#FF9800"))
        secondDlg.findViewById<TextView>(android.R.id.message)?.setTextColor(android.graphics.Color.parseColor("#FF9800"))
    }

    private fun performFullDatabaseReset() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Wis alle tabellen
                database.tellingDao().clearAllHeaders()
                database.tellingDao().clearAllWaarnemingen()
                database.tellingDao().clearAllSyncLogs()

                // 2. Reset de sessie-teller in DataStore
                com.yvesds.vt5.core.opslag.AppDataStore.resetTellingId(this@DatabaseBeheerScherm)

                // 3. Log de actie
                fileLogger.warn("GEBRUIKER: Volledige database reset uitgevoerd (tabellen leeg + teller op 0)")

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DatabaseBeheerScherm, "Database volledig gereset", Toast.LENGTH_LONG).show()
                    refreshTableList()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DatabaseBeheerScherm, "Fout bij reset: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun refreshTableList() {
        container.removeAllViews()

        lifecycleScope.launch {
            val tables = listOf(
                TableInfo("telling_headers", "Headers"),
                TableInfo("waarnemingen", "Waarnemingen"),
                TableInfo("sync_logs", "Sync Logs")
            )

            for (table in tables) {
                val count = getCountForTable(table.id)
                addTableCard(table, count)
            }
        }
    }

    private suspend fun getCountForTable(tableId: String): Int = withContext(Dispatchers.IO) {
        try {
            when (tableId) {
                "telling_headers" -> database.tellingDao().countHeaders()
                "waarnemingen" -> database.tellingDao().countWaarnemingen()
                "sync_logs" -> database.tellingDao().countSyncLogs()
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun addTableCard(table: TableInfo, count: Int) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_db_tabel, container, false)

        view.findViewById<TextView>(R.id.tvTabelNaam).text = getString(R.string.db_beheer_tabel_naam, table.name)
        view.findViewById<TextView>(R.id.tvRecordsCount).text = getString(R.string.db_beheer_records_count, count)

        view.findViewById<MaterialButton>(R.id.btnWissenRecords).setOnClickListener {
            showConfirmDialog(
                getString(R.string.db_beheer_confirm_records_msg, table.name)
            ) { clearTableRecords(table.id, table.name) }
        }

        view.findViewById<MaterialButton>(R.id.btnWissenTabel).setOnClickListener {
            showConfirmDialog(
                getString(R.string.db_beheer_confirm_tabel_msg, table.name)
            ) { dropTable(table.id, table.name) }
        }

        container.addView(view)
    }

    private fun showConfirmDialog(message: String, onConfirm: () -> Unit) {
        val dlg = AlertDialog.Builder(this)
            .setTitle(R.string.db_beheer_confirm_titel)
            .setMessage(message)
            .setPositiveButton(R.string.dlg_ok) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.annuleer, null)
            .show()
        DialogStyler.apply(dlg)
    }

    private fun clearTableRecords(tableId: String, tableName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                when (tableId) {
                    "telling_headers" -> database.tellingDao().clearAllHeaders()
                    "waarnemingen" -> database.tellingDao().clearAllWaarnemingen()
                    "sync_logs" -> database.tellingDao().clearAllSyncLogs()
                }
                fileLogger.info(getString(R.string.db_beheer_log_records_gewist, tableName))

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DatabaseBeheerScherm, R.string.db_beheer_actie_gelukt, Toast.LENGTH_SHORT).show()
                    refreshTableList()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DatabaseBeheerScherm, getString(R.string.db_beheer_actie_fout, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun dropTable(tableId: String, tableName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Room supportSQLiteDatabase gebruiken voor ruwe SQL
                database.openHelper.writableDatabase.execSQL("DROP TABLE IF EXISTS $tableId")

                // Na een DROP moet de tabel meestal opnieuw aangemaakt worden.
                // In Room is het veiliger om de app te herstarten of destructieve migratie te triggeren.
                // Voor nu: we loggen het en waarschuwen dat dit een zware ingreep is.
                fileLogger.warn(getString(R.string.db_beheer_log_tabel_dropped, tableName))

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DatabaseBeheerScherm, "Tabel gedropt. Herstart app aanbevolen.", Toast.LENGTH_LONG).show()
                    refreshTableList()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DatabaseBeheerScherm, getString(R.string.db_beheer_actie_fout, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    data class TableInfo(val id: String, val name: String)

    private enum class CsvFileType { HEADER, DATA }

    private data class ParsedTrektellenFileName(
        val fileType: CsvFileType,
        val telpostId: String,
        val year: String,
    )

    private data class CsvImportFile(
        val uri: Uri,
        val fileName: String,
        val fileType: CsvFileType,
        val telpostId: String,
        val year: String,
    )
}
