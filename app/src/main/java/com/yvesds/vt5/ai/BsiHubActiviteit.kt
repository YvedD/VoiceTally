package com.yvesds.vt5.ai

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.opslag.AppDataStore
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * BsiHubActiviteit - Centrale Hub & Beheerscentrum voor Bio Statistic Intelligence (BSI).
 * Voldoet aan de Glassmorphic Huisstijl (VT5GlassTheme) voor smartphone & tablet.
 */
class BsiHubActiviteit : AppCompatActivity() {

    private lateinit var btnTerug: MaterialButton
    private lateinit var tvBsiStatus: TextView
    private lateinit var tvDbStats: TextView

    private lateinit var btnOpenDatacentrum: MaterialButton
    private lateinit var btnOpenForecast: MaterialButton
    private lateinit var btnOpenKrentenMonitor: MaterialButton
    private lateinit var btnOpenDailyReports: MaterialButton

    private lateinit var switchNeuralInference: SwitchMaterial
    private lateinit var switchDailyAnalysisWeights: SwitchMaterial

    private lateinit var safStorageHelper: SaFStorageHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_bsi_hub)

        safStorageHelper = SaFStorageHelper(this)

        // Bind UI Elementen
        btnTerug = findViewById(R.id.btnTerug)
        tvBsiStatus = findViewById(R.id.tvBsiStatus)
        tvDbStats = findViewById(R.id.tvDbStats)

        btnOpenDatacentrum = findViewById(R.id.btnOpenDatacentrum)
        btnOpenForecast = findViewById(R.id.btnOpenForecast)
        btnOpenKrentenMonitor = findViewById(R.id.btnOpenKrentenMonitor)
        btnOpenDailyReports = findViewById(R.id.btnOpenDailyReports)

        switchNeuralInference = findViewById(R.id.switchNeuralInference)
        switchDailyAnalysisWeights = findViewById(R.id.switchDailyAnalysisWeights)

        // TERUG KNOP
        btnTerug.setOnClickListener {
            finish()
        }

        // NAVIGATIE MODULES
        btnOpenDatacentrum.setOnClickListener {
            startActivity(Intent(this, BsiDatacentrumActiviteit::class.java))
        }

        btnOpenForecast.setOnClickListener {
            startActivity(Intent(this, AiForecastScherm::class.java))
        }

        btnOpenKrentenMonitor.setOnClickListener {
            startActivity(Intent(this, KrentenMonitorActiviteit::class.java))
        }

        btnOpenDailyReports.setOnClickListener {
            startActivity(Intent(this, AiReportListActiviteit::class.java))
        }

        // FLOATING INFO-SCREEN VOOR BSI DATACENTRUM
        findViewById<android.widget.ImageView>(R.id.btnInfoBsiDatacentrum)?.setOnClickListener {
            com.yvesds.vt5.core.ui.FloatingInfoDialog.show(
                context = this,
                title = getString(R.string.bsi_info_datacentrum_title),
                bodyText = getString(R.string.bsi_info_datacentrum_body),
                closeButtonText = "Sluiten"
            )
        }

        // SWITCHOVER INSTELLINGEN BINDING
        setupSwitchListeners()

        // STATUS UPDATEN
        updateBsiHubStatus()
    }

    override fun onResume() {
        super.onResume()
        updateBsiHubStatus()
    }

    private fun setupSwitchListeners() {
        lifecycleScope.launch {
            val useNeural = AppDataStore.isUseNeuralInference(this@BsiHubActiviteit)
            val useDaily = AppDataStore.isUseDailyAnalysisWeights(this@BsiHubActiviteit)

            switchNeuralInference.isChecked = useNeural
            switchDailyAnalysisWeights.isChecked = useDaily

            switchNeuralInference.setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch {
                    AppDataStore.setUseNeuralInference(this@BsiHubActiviteit, isChecked)
                }
            }

            switchDailyAnalysisWeights.setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch {
                    AppDataStore.setUseDailyAnalysisWeights(this@BsiHubActiviteit, isChecked)
                }
            }
        }
    }

    private fun updateBsiHubStatus() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = VoiceTallyDatabase.getDatabase(this@BsiHubActiviteit)
            val count = db.tellingDao().getAllHeaders().size

            val hasTrainedModel = safStorageHelper.readModelFile("bsi_weights.json") != null ||
                    safStorageHelper.readModelFile("species_weights.json") != null ||
                    safStorageHelper.readModelFile("neural_engine.bin") != null

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
}
