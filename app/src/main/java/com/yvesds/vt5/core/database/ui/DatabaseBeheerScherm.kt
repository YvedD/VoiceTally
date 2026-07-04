package com.yvesds.vt5.core.database.ui

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

    // Kleuren komen uit colors.xml sectie "grafiekkleuren".
    // Aanpassen: verander de resource-naam hieronder als je een andere kleur wil.
    private val chartLineColor   get() = ContextCompat.getColor(this, R.color.grafiek_lijnkleur)
    private val chartBgColor     get() = ContextCompat.getColor(this, R.color.grafiek_achtergrondkleur)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_database_beheer)

        database = VoiceTallyDatabase.getDatabase(this)
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

        findViewById<MaterialButton>(R.id.btnResetDatabase).setOnClickListener {
            startDoubleResetConfirmation()
        }

        initChart()
        setupChart()
        refreshTableList()
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
}
