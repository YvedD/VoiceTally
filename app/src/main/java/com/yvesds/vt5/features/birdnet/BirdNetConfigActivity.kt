package com.yvesds.vt5.features.birdnet

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.yvesds.vt5.R
import com.yvesds.vt5.core.ui.DialogStyler
import kotlinx.coroutines.launch

/**
 * BirdNetConfigActivity - Scherm voor configuratie van BirdNET-GO server.
 *
 * Functies:
 * - Auto-discovery & Subnet IP Scan: Zoekt BirdNET-GO servers en IP-adressen op het lokale netwerk/hotspot
 * - Quick IP Presets & Historie: Snelkoppelingen en historie voor eerder gebruikte IP-adressen
 * - Minimale Betrouwbaarheid Filter (%): In te stellen drempelwaarde (standaard 40%)
 * - Handmatige invoer: Host / IP, poort, protocol en filter instelbaar
 * - Test verbinding: Controleert of de server bereikbaar is op het gekozen IP
 */
class BirdNetConfigActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BirdNetConfigActivity"
    }

    private lateinit var tvStatus: TextView
    private lateinit var rgProtocol: RadioGroup
    private lateinit var etHost: EditText
    private lateinit var npPort: NumberPicker
    private lateinit var npMinConfidence: NumberPicker
    private lateinit var btnAutoDiscover: MaterialButton
    private lateinit var btnTestConnection: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var btnClear: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvDiscoveryResults: TextView
    private lateinit var btnApplyDiscoveryResult: MaterialButton
    private lateinit var llDiscoveryList: LinearLayout

    private var currentDiscoveryResults = listOf<BirdNetDiscovery.DiscoveryResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_birdnet_config)

        // UI vinden
        tvStatus = findViewById(R.id.tvBirdNetStatus)
        rgProtocol = findViewById(R.id.rgBirdNetProtocol)
        etHost = findViewById(R.id.etBirdNetHost)
        npPort = findViewById(R.id.npBirdNetPort)
        npMinConfidence = findViewById(R.id.npBirdNetMinConfidence)
        btnAutoDiscover = findViewById(R.id.btnBirdNetAutoDiscover)
        btnTestConnection = findViewById(R.id.btnBirdNetTestConnection)
        btnSave = findViewById(R.id.btnBirdNetSave)
        btnClear = findViewById(R.id.btnBirdNetClear)
        progressBar = findViewById(R.id.progressBirdNetDiscovery)
        tvDiscoveryResults = findViewById(R.id.tvBirdNetDiscoveryResults)
        btnApplyDiscoveryResult = findViewById(R.id.btnApplyDiscoveryResult)
        llDiscoveryList = findViewById(R.id.llDiscoveryList)

        // NumberPicker poort instellen
        npPort.minValue = 1
        npPort.maxValue = 65535
        npPort.value = 8080

        // NumberPicker betrouwbaarheid filter instellen (10% - 95%)
        npMinConfidence.minValue = 10
        npMinConfidence.maxValue = 99
        npMinConfidence.value = 40

        // Terug knop
        findViewById<MaterialButton>(R.id.btnTerug)?.setOnClickListener { finish() }

        // Quick IP Presets koppelen
        setupPresetButtons()

        // Laad huidige config & historie
        loadCurrentConfig()

        // Event listeners
        btnAutoDiscover.setOnClickListener { performAutoDiscovery() }
        btnTestConnection.setOnClickListener { testConnection() }
        btnSave.setOnClickListener { saveConfiguration() }
        btnClear.setOnClickListener { clearConfiguration() }
        btnApplyDiscoveryResult.setOnClickListener { applyDiscoveryResult() }

        updateStatusDisplay()
    }

    private fun setupPresetButtons() {
        findViewById<MaterialButton>(R.id.btnPresetBirdNetLocal)?.setOnClickListener {
            applyPresetHost("birdnet.local")
        }
        findViewById<MaterialButton>(R.id.btnPresetHotspot)?.setOnClickListener {
            applyPresetHost("192.168.43.1")
        }
        findViewById<MaterialButton>(R.id.btnPresetPiAp)?.setOnClickListener {
            applyPresetHost("10.42.0.1")
        }
        findViewById<MaterialButton>(R.id.btnPresetLan)?.setOnClickListener {
            applyPresetHost("192.168.1.100")
        }
    }

    private fun applyPresetHost(host: String) {
        etHost.setText(host)
        Toast.makeText(this, "Host ingesteld op: $host", Toast.LENGTH_SHORT).show()
        testConnection()
    }

    private fun loadCurrentConfig() {
        val config = BirdNetConfig.load()

        // Protocol
        val protocolId = if (config.protocol == "https") R.id.rbHttps else R.id.rbHttp
        rgProtocol.check(protocolId)

        // Host
        etHost.setText(config.host)

        // Port
        npPort.value = config.port.coerceIn(1, 65535)

        // Minimale Betrouwbaarheid Filter (%)
        npMinConfidence.value = config.minConfidencePct.coerceIn(10, 95)

        // Historie bijwerken
        updateHistoryDisplay()
    }

    private fun updateHistoryDisplay() {
        val history = BirdNetConfig.getHistory()
        val tvHeader = findViewById<TextView>(R.id.tvHistoryHeader)
        val container = findViewById<LinearLayout>(R.id.llHistoryContainer) ?: return

        container.removeAllViews()

        if (history.isEmpty()) {
            tvHeader?.visibility = View.GONE
            return
        }

        tvHeader?.visibility = View.VISIBLE

        history.forEach { savedHost ->
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                text = "⚡ $savedHost"
                setTextColor(Color.parseColor("#64B5F6"))
                setOnClickListener {
                    applyPresetHost(savedHost)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 2
                }
            }
            container.addView(btn)
        }
    }

    private fun updateStatusDisplay() {
        val config = BirdNetConfig.load()
        val activeIp = BirdNetDiscovery.getLocalWifiIpAddress(this)
        val ipInfo = if (activeIp != null) "\n📱 Tablet Wi-Fi IP: $activeIp" else ""

        val displayText = if (config.isConfigured) {
            "✓ Geconfigureerd: ${config.displayLabel}\nURL: ${config.baseUrl}\nFilter: ≥ ${config.minConfidencePct}% betrouwbaarheid$ipInfo"
        } else {
            "✗ Niet geconfigureerd - Voer een IP-adres in of gebruik Scan Netwerk$ipInfo"
        }
        tvStatus.text = displayText
    }

    private fun performAutoDiscovery() {
        if (progressBar.visibility == View.VISIBLE) return

        progressBar.visibility = View.VISIBLE
        btnAutoDiscover.isEnabled = false
        val tabletIp = BirdNetDiscovery.getLocalWifiIpAddress(this) ?: "onbekend"
        tvDiscoveryResults.text = "Tablet IP is $tabletIp. Scannen op BirdNET-GO servers op lokaal netwerk..."
        llDiscoveryList.removeAllViews()

        lifecycleScope.launch {
            try {
                val selectedPort = npPort.value
                val results = BirdNetDiscovery.discoverAll(
                    context = this@BirdNetConfigActivity,
                    port = selectedPort,
                    onProgress = { message ->
                        tvDiscoveryResults.text = "Tablet IP: $tabletIp\n$message"
                    }
                )

                currentDiscoveryResults = results

                if (results.isEmpty()) {
                    tvDiscoveryResults.text = "Geen servers gevonden via IP Scan (Tablet IP: $tabletIp).\n\nTip: Als je het IP-adres van de Raspberry Pi kent (bijv. via je router of laptop), voer het dan onderaan handmatig in bij Host/IP-adres en klik op 'Verbinding Testen'."
                    btnApplyDiscoveryResult.isEnabled = false
                } else {
                    val summaryText = "✓ Gevonden: ${results.size} server(s) op poort $selectedPort (Tablet IP: $tabletIp). Klik op een knop hieronder:"
                    tvDiscoveryResults.text = summaryText
                    btnApplyDiscoveryResult.isEnabled = true

                    renderDiscoveredIpButtons(results)
                }
            } catch (e: Exception) {
                tvDiscoveryResults.text = "Discovery fout: ${e.message}"
                btnApplyDiscoveryResult.isEnabled = false
            } finally {
                progressBar.visibility = View.GONE
                btnAutoDiscover.isEnabled = true
            }
        }
    }

    private fun renderDiscoveredIpButtons(results: List<BirdNetDiscovery.DiscoveryResult>) {
        llDiscoveryList.removeAllViews()

        results.forEach { result ->
            val btn = MaterialButton(this).apply {
                text = "✓ Verbinden met: ${result.displayLabel}"
                setBackgroundColor(Color.parseColor("#2E7D32")) // Material Green
                setTextColor(Color.WHITE)
                setOnClickListener {
                    applyConfig(result.toConfig())
                    testConnection()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 8
                }
            }
            llDiscoveryList.addView(btn)
        }
    }

    private fun applyDiscoveryResult() {
        if (currentDiscoveryResults.isEmpty()) {
            Toast.makeText(this, "Geen resultaten om toe te passen", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentDiscoveryResults.size == 1) {
            applyConfig(currentDiscoveryResults[0].toConfig())
            testConnection()
            return
        }

        val items = currentDiscoveryResults.map { it.displayLabel }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Kies BirdNET-GO Server")
            .setItems(items) { _, which ->
                applyConfig(currentDiscoveryResults[which].toConfig())
                testConnection()
            }
            .show()
            .also { DialogStyler.apply(it) }
    }

    private fun applyConfig(config: BirdNetConfig) {
        rgProtocol.check(if (config.protocol == "https") R.id.rbHttps else R.id.rbHttp)
        etHost.setText(config.host)
        npPort.value = config.port
        npMinConfidence.value = config.minConfidencePct.coerceIn(10, 95)

        Toast.makeText(this, "Toegepast: ${config.displayLabel}", Toast.LENGTH_SHORT).show()
        updateStatusDisplay()
    }

    private fun testConnection() {
        val host = etHost.text.toString().trim()
        if (host.isEmpty()) {
            Toast.makeText(this, "Voer a.u.b. een IP-adres of host in", Toast.LENGTH_SHORT).show()
            return
        }

        val protocol = if (rgProtocol.checkedRadioButtonId == R.id.rbHttps) "https" else "http"
        val port = npPort.value
        val minConfidencePct = npMinConfidence.value
        val config = BirdNetConfig(protocol = protocol, host = host, port = port, minConfidencePct = minConfidencePct)

        btnTestConnection.isEnabled = false
        Toast.makeText(this, "Verbinding testen met ${config.displayLabel}...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val (isOk, detailReason) = BirdNetDiscovery.pingHostDetailed(config)
                if (isOk) {
                    Toast.makeText(this@BirdNetConfigActivity, "✓ Verbinding succesvol!", Toast.LENGTH_SHORT).show()
                    tvStatus.text = "✓ Server is bereikbaar op ${config.displayLabel}\nURL: ${config.baseUrl}"
                    BirdNetConfig.save(config)
                    updateHistoryDisplay()
                } else {
                    val isLocalDomain = host.endsWith(".local")
                    val extraAdvice = if (isLocalDomain) {
                        "\n\n⚠ Let op: Domeinnamen zoals 'birdnet.local' werken op veel Android-toestellen niet in apps. Gebruik het IP-adres (bijv. 192.168.1.x of 10.209.161.x via Scan Netwerk)."
                    } else ""

                    Toast.makeText(this@BirdNetConfigActivity, "✗ Kan server niet bereiken", Toast.LENGTH_SHORT).show()
                    tvStatus.text = "✗ Kan server niet bereiken op ${config.displayLabel}\nFoutmelding: $detailReason$extraAdvice"
                }
            } catch (e: Exception) {
                Toast.makeText(this@BirdNetConfigActivity, "✗ Fout: ${e.message}", Toast.LENGTH_SHORT).show()
                tvStatus.text = "✗ Fout: ${e.message}"
            } finally {
                btnTestConnection.isEnabled = true
            }
        }
    }

    private fun saveConfiguration() {
        val host = etHost.text.toString().trim()
        if (host.isEmpty()) {
            Toast.makeText(this, "Voer a.u.b. een IP-adres of host in", Toast.LENGTH_SHORT).show()
            return
        }

        val protocol = if (rgProtocol.checkedRadioButtonId == R.id.rbHttps) "https" else "http"
        val port = npPort.value
        val minConfidencePct = npMinConfidence.value
        val config = BirdNetConfig(protocol = protocol, host = host, port = port, minConfidencePct = minConfidencePct)

        BirdNetConfig.save(config)
        updateStatusDisplay()
        updateHistoryDisplay()
        Toast.makeText(this, "Configuratie opgeslagen: ${config.displayLabel}", Toast.LENGTH_SHORT).show()
    }

    private fun clearConfiguration() {
        AlertDialog.Builder(this)
            .setTitle("Configuratie Wissen?")
            .setMessage("Dit wist de opgeslagen BirdNET-GO instellingen en de historie.")
            .setPositiveButton("Wissen") { _, _ ->
                BirdNetConfig.clear()
                etHost.setText("")
                npPort.value = 8080
                npMinConfidence.value = 40
                rgProtocol.check(R.id.rbHttp)
                llDiscoveryList.removeAllViews()
                updateStatusDisplay()
                updateHistoryDisplay()
                tvDiscoveryResults.text = ""
                Toast.makeText(this@BirdNetConfigActivity, "Configuratie gewist", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annuleren", null)
            .show()
            .also { DialogStyler.apply(it) }
    }
}
