@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.yvesds.vt5.features.export.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.yvesds.vt5.R
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.core.secure.CredentialsStore
import com.yvesds.vt5.features.export.TrektellenDownloadResult
import com.yvesds.vt5.features.export.TrektellenExcelDownloader
import com.yvesds.vt5.features.serverdata.model.ServerDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Optie voor het Telpost Selectie Dropdown
 */
data class TelpostOption(val id: String, val name: String) {
    override fun toString(): String = if (id == "ALL") name else "$name ($id)"
}

class TrektellenExportTestActiviteit : AppCompatActivity() {

    private lateinit var downloader: TrektellenExcelDownloader
    private lateinit var credentialsStore: CredentialsStore
    private lateinit var safStorageHelper: SaFStorageHelper

    // Layout Cards & Containers
    private lateinit var cardLoginSection: CardView
    private lateinit var cardDownloadSection: CardView
    private lateinit var cardResultSection: CardView
    private lateinit var layoutResultContent: LinearLayout
    private lateinit var layoutResultHeader: LinearLayout

    // UI Elements
    private lateinit var btnTerug: MaterialButton
    private lateinit var tvCookieStatus: TextView
    private lateinit var spinnerTelpost: Spinner
    private lateinit var spinnerJaar: Spinner
    private lateinit var etStartYear: EditText
    private lateinit var chkValidatePk: CheckBox
    private lateinit var btnStartDownload: MaterialButton
    private lateinit var btnResetLogin: MaterialButton
    private lateinit var btnToggleResult: MaterialButton
    private lateinit var tvResultReport: TextView
    private lateinit var webView: WebView

    private val availableTelpostOptions = mutableListOf<TelpostOption>()
    private val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    private var isResultExpanded = true

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_trektellen_export_test)

        downloader = TrektellenExcelDownloader(this)
        credentialsStore = CredentialsStore(this)
        safStorageHelper = SaFStorageHelper(this)

        // Bind UI elementen
        btnTerug = findViewById(R.id.btnTerug)
        cardLoginSection = findViewById(R.id.cardLoginSection)
        cardDownloadSection = findViewById(R.id.cardDownloadSection)
        cardResultSection = findViewById(R.id.cardResultSection)
        layoutResultContent = findViewById(R.id.layoutResultContent)
        layoutResultHeader = findViewById(R.id.layoutResultHeader)

        tvCookieStatus = findViewById(R.id.tvCookieStatus)
        spinnerTelpost = findViewById(R.id.spinnerTelpost)
        spinnerJaar = findViewById(R.id.spinnerJaar)
        etStartYear = findViewById(R.id.etStartYear)
        chkValidatePk = findViewById(R.id.chkValidatePk)
        btnStartDownload = findViewById(R.id.btnStartDownload)
        btnResetLogin = findViewById(R.id.btnResetLogin)
        btnToggleResult = findViewById(R.id.btnToggleResult)
        tvResultReport = findViewById(R.id.tvResultReport)
        webView = findViewById(R.id.wvLogin)

        // TERUG KNOP
        btnTerug.setOnClickListener {
            finish()
        }

        // JAARTAL DROPDOWN INSTELLEN (Standaard 1960 t/m huidig jaar + "Alle jaren")
        setupJaartallenSpinner()

        // Luister naar wijzigingen in het vroegste jaar invoerveld
        etStartYear.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                setupJaartallenSpinner()
            }
        })

        // TELPOST DROPDOWN DYNAMISCH LADEN MET ECHTE TELPOSTNAMEN & "ALLE TELPOSTEN" OPTIE
        setupTelpostenList()

        // RESET LOGIN KNOP
        btnResetLogin.setOnClickListener {
            credentialsStore.saveCiSessionCookie("")
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            webView.clearCache(true)
            webView.clearFormData()
            webView.clearHistory()
            android.webkit.WebStorage.getInstance().deleteAllData()

            writeLogToVt5Logs("SESSIE_RESET", "Gebruiker heeft opgeslagen ci_session cookie en WebView cache gewist.")
            updateUiStateBasedOnCookie()
            Toast.makeText(this, "Sessie en cache gewist. Log opnieuw in.", Toast.LENGTH_SHORT).show()
        }

        // BATCH DOWNLOAD STARTEN
        btnStartDownload.setOnClickListener {
            startBatchDownload()
        }

        // OPKLAPBAAR RESULTAAT VENSTER TOGGLE
        layoutResultHeader.setOnClickListener {
            toggleResultPanel()
        }
        btnToggleResult.setOnClickListener {
            toggleResultPanel()
        }

        // WEBVIEW CONFIGUREREN
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                checkAndPersistCiSessionCookie(url)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                checkAndPersistCiSessionCookie(request?.url?.toString())
                return super.shouldOverrideUrlLoading(view, request)
            }
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        // Log activiteit opstart
        writeLogToVt5Logs("APP_START", "TrektellenExportTestActiviteit gestart.")

        // Controleer opgeslagen cookie status & stel UI in
        updateUiStateBasedOnCookie()
    }

    private fun setupJaartallenSpinner() {
        val startYear = etStartYear.text.toString().toIntOrNull() ?: 1960
        val jaarOptions = mutableListOf("Alle jaren ($startYear - $currentYear)")
        for (y in currentYear downTo startYear) {
            jaarOptions.add(y.toString())
        }
        spinnerJaar.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            jaarOptions
        )
    }

    private fun toggleResultPanel() {
        isResultExpanded = !isResultExpanded
        if (isResultExpanded) {
            layoutResultContent.visibility = View.VISIBLE
            btnToggleResult.text = "▲"
        } else {
            layoutResultContent.visibility = View.GONE
            btnToggleResult.text = "▼"
        }
    }

    private fun updateUiStateBasedOnCookie() {
        val savedCookie = credentialsStore.getCiSessionCookie()
        val hasCookie = !savedCookie.isNullOrBlank()

        if (hasCookie) {
            tvCookieStatus.text = "✔ Actieve ci_session cookie geladen uit beveiligde opslag."
            webView.visibility = View.GONE
            btnResetLogin.visibility = View.VISIBLE
            cardDownloadSection.visibility = View.VISIBLE
        } else {
            tvCookieStatus.text = "⚠ Geen opgeslagen sessie cookie. Log hieronder eenmalig in op Trektellen.nl:"
            webView.visibility = View.VISIBLE
            btnResetLogin.visibility = View.GONE
            cardDownloadSection.visibility = View.GONE
            webView.loadUrl("https://www.trektellen.nl/auth/login")
        }
    }

    private fun checkAndPersistCiSessionCookie(url: String?) {
        val targetUrl = url ?: ""
        val isLoginPage = targetUrl.contains("/auth/login", ignoreCase = true) || 
                          targetUrl.contains("/login", ignoreCase = true)

        if (isLoginPage) {
            return
        }

        val rawCookieString = CookieManager.getInstance().getCookie(targetUrl) 
            ?: CookieManager.getInstance().getCookie("https://www.trektellen.nl")

        val essentialCookies = extractEssentialCookies(rawCookieString)

        if (!essentialCookies.isNullOrBlank() && essentialCookies.contains("ci_session")) {
            val existingCookie = credentialsStore.getCiSessionCookie()
            if (existingCookie != essentialCookies) {
                credentialsStore.saveCiSessionCookie(essentialCookies)
                writeLogToVt5Logs(
                    "COOKIE_CAPTURED", 
                    "Geauthenticeerde ci_session cookie afgevangen na succesvolle login!\nURL: $targetUrl\nOpgeslagen Cookie: $essentialCookies"
                )

                AlertDialog.Builder(this)
                    .setTitle("Inloggen Geslaagd! 🎉")
                    .setMessage("De geauthenticeerde ci_session cookie van Trektellen.nl is succesvol afgevangen en beveiligd opgeslagen op je toestel.\n\nJe kunt nu direct je telpost(en) en jaren selecteren en de batch-download starten.")
                    .setPositiveButton("OK", null)
                    .show()

                Toast.makeText(this, "Inloggen geslaagd! Sessie-cookie opgeslagen.", Toast.LENGTH_LONG).show()
                updateUiStateBasedOnCookie()
            }
        }
    }

    private fun extractEssentialCookies(cookieString: String?): String? {
        if (cookieString.isNullOrBlank()) return null
        val ciMatch = Regex("(ci_session=[^;]+)").find(cookieString)?.groupValues?.get(1)
        val cfMatch = Regex("(cf_clearance=[^;]+)").find(cookieString)?.groupValues?.get(1)

        return listOfNotNull(ciMatch, cfMatch).joinToString("; ").ifBlank { null }
    }

    /**
     * Laadt alle echte telpostnamen uit ServerDataRepository / serverdata en voegt "Alle telposten" toe.
     */
    private fun setupTelpostenList() {
        lifecycleScope.launch(Dispatchers.IO) {
            val rawSites = mutableListOf<TelpostOption>()
            try {
                val repository = ServerDataRepository(this@TrektellenExportTestActiviteit)
                val snapshot = repository.loadMinimalData()
                val sites = snapshot.sitesById.values.sortedBy { it.telpostnaam.lowercase(Locale.getDefault()) }

                if (sites.isNotEmpty()) {
                    sites.forEach { site ->
                        if (site.telpostid.isNotBlank() && site.telpostnaam.isNotBlank()) {
                            rawSites.add(TelpostOption(site.telpostid, site.telpostnaam))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TrektellenExportTest", "Fout bij laden telpostnamen: ${e.message}")
            }

            // Fallbacks indien geen serverdata geladen
            if (rawSites.isEmpty()) {
                rawSites.add(TelpostOption("4310", "De Horde"))
                rawSites.add(TelpostOption("5719", "Spanjaardduin"))
                rawSites.add(TelpostOption("1", "Fonteintjes"))
            }

            val finalOptions = mutableListOf<TelpostOption>()
            // OPTIE 0: ALLE TELPOSTEN
            finalOptions.add(TelpostOption("ALL", "Alle telposten (${rawSites.size})"))
            finalOptions.addAll(rawSites)

            withContext(Dispatchers.Main) {
                availableTelpostOptions.clear()
                availableTelpostOptions.addAll(finalOptions)

                val adapter = ArrayAdapter(
                    this@TrektellenExportTestActiviteit,
                    android.R.layout.simple_spinner_dropdown_item,
                    availableTelpostOptions
                )
                spinnerTelpost.adapter = adapter
            }
        }
    }

    private fun startBatchDownload() {
        if (availableTelpostOptions.isEmpty()) return

        btnStartDownload.isEnabled = false
        btnResetLogin.isEnabled = false

        val selectedOption = spinnerTelpost.selectedItem as? TelpostOption 
            ?: availableTelpostOptions.first()

        // Bepaal de lijst van te verwerken telposten
        val telpostenToProcess = if (selectedOption.id == "ALL") {
            availableTelpostOptions.filter { it.id != "ALL" }
        } else {
            listOf(selectedOption)
        }

        val startYear = etStartYear.text.toString().toIntOrNull() ?: 1960

        // Bepaal de lijst van te verwerken jaartallen
        val selectedJaarPos = spinnerJaar.selectedItemPosition
        val yearsToProcess = if (selectedJaarPos == 0) {
            (currentYear downTo startYear).toList()
        } else {
            val yr = spinnerJaar.selectedItem.toString().toIntOrNull() ?: currentYear
            listOf(yr)
        }

        val validatePk = chkValidatePk.isChecked
        val savedCookie = credentialsStore.getCiSessionCookie() 
            ?: extractEssentialCookies(CookieManager.getInstance().getCookie("https://www.trektellen.nl"))

        // Zorg dat het resultaatvenster zichtbaar wordt
        cardResultSection.visibility = View.VISIBLE
        layoutResultContent.visibility = View.VISIBLE
        isResultExpanded = true
        btnToggleResult.text = "▲"

        val totalTelpostCount = telpostenToProcess.size
        val totalYearsCount = yearsToProcess.size
        val totalCombinations = totalTelpostCount * totalYearsCount

        tvResultReport.text = "Batch download gestart:\n• Telposten: $totalTelpostCount\n• Jaren per telpost: $totalYearsCount\n• Totaal aantal koppels: $totalCombinations\n\n----------------------------------------\n"

        writeLogToVt5Logs(
            "BATCH_START",
            "Start batch download voor $totalTelpostCount telpost(en), Jaren per telpost: $totalYearsCount, PK-Validatie: $validatePk"
        )

        lifecycleScope.launch {
            val succeededResults = mutableListOf<TrektellenDownloadResult>()
            val failedResults = mutableListOf<String>()

            var pairProgressIndex = 0

            for (telpostOpt in telpostenToProcess) {
                val tNumber = telpostOpt.id
                val tName = telpostOpt.name

                for (yr in yearsToProcess) {
                    pairProgressIndex++

                    // 1. Download Headerdata (exporttype = 1) -> Trektellen_headerdata_[telpost]_[jaar].xlsx
                    tvResultReport.append("► [$pairProgressIndex/$totalCombinations] $tName ($tNumber) - $yr: Headerdata (1/2)...\n")
                    val headerResult = downloader.downloadSingleExport(
                        telpostNumber = tNumber,
                        year = yr,
                        exportType = 1,
                        customCookieHeader = savedCookie,
                        validatePkHeader = validatePk
                    )

                    var headerSuccess = false
                    headerResult.onSuccess { res ->
                        headerSuccess = true
                        succeededResults.add(res)
                        tvResultReport.append("  ✔ Opgeslagen: ${res.filename} (${res.bytesWritten / 1024} KB)\n")
                        writeLogToVt5Logs("DOWNLOAD_OK", "Headerdata $tName ($tNumber) $yr: ${res.filename} (${res.bytesWritten} bytes)")
                    }.onFailure { err ->
                        val errMsg = "$tName ($tNumber) - Jaar $yr [headerdata]: ${err.message}"
                        failedResults.add(errMsg)
                        tvResultReport.append("  ✘ Mislukt/Leeg: ${err.message} (Data overgeslagen)\n")
                        writeLogToVt5Logs("DOWNLOAD_FAIL", errMsg)
                    }

                    // 2. Download Data (exporttype = 2) alleen als headerdata succesvol was
                    if (headerSuccess) {
                        tvResultReport.append("► [$pairProgressIndex/$totalCombinations] $tName ($tNumber) - $yr: Data (2/2)...\n")
                        val dataResult = downloader.downloadSingleExport(
                            telpostNumber = tNumber,
                            year = yr,
                            exportType = 2,
                            customCookieHeader = savedCookie,
                            validatePkHeader = validatePk
                        )

                        dataResult.onSuccess { res ->
                            succeededResults.add(res)
                            tvResultReport.append("  ✔ Opgeslagen: ${res.filename} (${res.bytesWritten / 1024} KB)\n")
                            writeLogToVt5Logs("DOWNLOAD_OK", "Data $tName ($tNumber) $yr: ${res.filename} (${res.bytesWritten} bytes)")
                        }.onFailure { err ->
                            val errMsg = "$tName ($tNumber) - Jaar $yr [data]: ${err.message}"
                            failedResults.add(errMsg)
                            tvResultReport.append("  ✘ Mislukt/Leeg: ${err.message}\n")
                            writeLogToVt5Logs("DOWNLOAD_FAIL", errMsg)
                        }
                    }
                }
            }

            // ========================================================================
            // EINDRAPPORTAGE TONEN
            // ========================================================================
            val report = StringBuilder()
            report.append("\n========================================\n")
            report.append("=== BATCH DOWNLOAD COMPLEET ===\n")
            report.append("Aantal verwerkte telposten: $totalTelpostCount\n")
            report.append("Aantal jaren per telpost: $totalYearsCount\n")
            report.append("Succesvol gedownloade bestanden: ${succeededResults.size}\n")
            report.append("Mislukte / Lege items: ${failedResults.size}\n")
            report.append("Locatie: Documents/VT5/imports/\n")
            report.append("========================================\n")

            tvResultReport.append(report.toString())
            btnStartDownload.isEnabled = true
            btnResetLogin.isEnabled = true

            writeLogToVt5Logs("BATCH_COMPLETE", report.toString())

            Toast.makeText(
                this@TrektellenExportTestActiviteit,
                "Batch voltooid! ${succeededResults.size} bestanden opgeslagen in Documents/VT5/imports/",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Schrijft uitgebreide test- en proceslogs weg naar Documents/VT5/logs/
     */
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
                Log.e("TrektellenExportTest", "Fout bij schrijven naar Documents/VT5/logs: ${e.message}")
            }
        }
    }
}