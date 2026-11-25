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
import com.yvesds.vt5.core.database.weather.WeatherEnrichmentManager
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.core.ui.ProgressDialogHelper
import com.yvesds.vt5.features.export.ui.TrektellenExportTestActiviteit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import com.yvesds.vt5.features.export.TrektellenExcelDownloader
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import org.json.JSONObject
import java.util.Calendar
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

    private lateinit var safStorageHelper: SaFStorageHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_bsi_datacentrum)

        lifecycleScope.launch {
            if (!com.yvesds.vt5.core.opslag.AppDataStore.isAiEnabled(this@BsiDatacentrumActiviteit)) {
                android.widget.Toast.makeText(this@BsiDatacentrumActiviteit, "BSI / AI is uitgeschakeld in de Instellingen.", android.widget.Toast.LENGTH_SHORT).show()
                finish()
            }
        }

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

        // TERUG KNOP
        btnTerug.setOnClickListener {
            finish()
        }

        // INDIVIDUELE STAP KNOPPEN & INFO-SCHERMEN
        btnStep1Export.setOnClickListener {
            startActivity(Intent(this, TrektellenExportTestActiviteit::class.java))
        }

        findViewById<android.widget.ImageView>(R.id.btnInfoStep1)?.setOnClickListener {
            com.yvesds.vt5.core.ui.FloatingInfoDialog.show(
                context = this,
                title = "1. Trektellen.nl Bestanden Ophalen",
                bodyText = "Waarom?\nTrektellen.nl bevat alle historische telgegevens. Door deze data te downloaden bouwt BSI een solide historische kennisbasis op.\n\n" +
                        "Hoe werkt het?\nVia de Trektellen Export Downloader log je eenmalig in om gegevens in koppels (.xlsx) automatisch binnen te halen.\n\n" +
                        "Resultaat:\nAlle benodigde tellingen en waarnemingen staan opgeslagen in Documents/VT5/imports/.",
                iconResId = R.drawable.ic_info_circle_exclamation,
                closeButtonText = "Sluiten"
            )
        }

        btnStep2Import.setOnClickListener {
            runExcelBatchImport()
        }

        findViewById<android.widget.ImageView>(R.id.btnInfoStep2)?.setOnClickListener {
            com.yvesds.vt5.core.ui.FloatingInfoDialog.show(
                context = this,
                title = "2. Importeren in Database",
                bodyText = "Waarom?\nOm de gedownloade Excel-bestanden (headerdata en waarnemingsdata) snel en doorzoekbaar beschikbaar te maken voor BSI.\n\n" +
                        "Hoe werkt het?\nDe FastExcel parser verwerkt alle koppelbestanden uit Documents/VT5/imports/ in bulk naar de lokale Room Database.\n\n" +
                        "Resultaat:\nVolledige integriteit en directe beschikbaarheid van alle historische tellingen op jouw apparaat.",
                iconResId = R.drawable.ic_info_circle_exclamation,
                closeButtonText = "Sluiten"
            )
        }

        btnStep3Weather.setOnClickListener {
            runWeatherEnrichment()
        }

        findViewById<android.widget.ImageView>(R.id.btnInfoStep3)?.setOnClickListener {
            com.yvesds.vt5.core.ui.FloatingInfoDialog.show(
                context = this,
                title = "3. Historische Weerdata Koppelen",
                bodyText = "Waarom?\nVogelmigratie is sterk afhankelijk van weeromstandigheden zoals windrichting, luchtdruk, temperatuur en neerslag.\n\n" +
                        "Hoe werkt het?\nVia de Yr.no weer-API wordt voor elke historische teldatum en -tijdstip het exacte lokale weerbeeld opgehaald.\n\n" +
                        "Resultaat:\nElke telling in de database wordt verrijkt met nauwkeurige meteorologische kenmerken.",
                iconResId = R.drawable.ic_info_circle_exclamation,
                closeButtonText = "Sluiten"
            )
        }

        btnStep4Train.setOnClickListener {
            runAiTrainingFlow()
        }

        findViewById<android.widget.ImageView>(R.id.btnInfoStep4)?.setOnClickListener {
            com.yvesds.vt5.core.ui.FloatingInfoDialog.show(
                context = this,
                title = "4. Bio Statistic Intelligence Trainen",
                bodyText = "Waarom?\nHet BSI Neuraal Netwerk leert de patronen tussen vogelmigratie en weersomstandigheden specifiek voor jouw telpost.\n\n" +
                        "Hoe werkt het?\nOn-device machine learning traint de gewichten van het neurale netwerk op alle verrijkte database-gegevens.\n\n" +
                        "Resultaat:\nNauwkeurige 3-daagse trekprognoses, fenologische inzichten en ecologische teldag-analyses.",
                iconResId = R.drawable.ic_info_circle_exclamation,
                closeButtonText = "Sluiten"
            )
        }

        // AUTOMATISCHE 1-KLIK BSI PIJPLIJN
        btnStartAutoPipeline.setOnClickListener {
            runAutomatedBsiPipeline()
        }

        // RESULTATEN & AI OUTPUT KNOPPEN
        // RESULTATEN & AI OUTPUT KNOPPEN & OPKLAPBAAR MENU
        val layoutHeaderResults = findViewById<android.view.View>(R.id.layoutHeaderResults)
        val layoutResultsContent = findViewById<android.view.View>(R.id.layoutResultsContent)
        val tvArrowResults = findViewById<TextView>(R.id.tvArrowResults)

        layoutHeaderResults?.setOnClickListener {
            val isVisible = layoutResultsContent?.visibility == android.view.View.VISIBLE
            layoutResultsContent?.visibility = if (isVisible) android.view.View.GONE else android.view.View.VISIBLE
            tvArrowResults?.text = if (isVisible) "▼" else "▲"
        }

        btnAiForecast3Days.setOnClickListener {
            startActivity(Intent(this, AiForecastScherm::class.java))
        }

        findViewById<android.widget.ImageView>(R.id.btnInfoForecast)?.setOnClickListener {
            com.yvesds.vt5.core.ui.FloatingInfoDialog.show(
                context = this,
                title = "3-Daagse Trekprognose",
                bodyText = "De 3-Daagse Trekprognose gebruikt het neuraal BSI-model gecombineerd met Yr.no weersverwachtingen (windrichting, luchtdruk, temperatuur).\n\n" +
                        "Hoe te interpreteren?\n" +
                        "• Verwachte Trekactiviteit: Geeft per vogelsoort de berekende kans op trekactiviteit over de komende 72 uur.\n" +
                        "• Gunstige Omstandigheden: De prognose houdt rekening met de optimale windrichting en weersomstandigheden voor jouw telpost.",
                iconResId = R.drawable.ic_info_circle_exclamation,
                closeButtonText = "Sluiten"
            )
        }

        btnKrentenMonitor.setOnClickListener {
            startActivity(Intent(this, KrentenMonitorActiviteit::class.java))
        }

        findViewById<android.widget.ImageView>(R.id.btnInfoKrenten)?.setOnClickListener {
            com.yvesds.vt5.core.ui.FloatingInfoDialog.show(
                context = this,
                title = "KrentenMonitor & Fenologie",
                bodyText = "De KrentenMonitor helpt bij het bewaken van zeldzame vogelsoorten (Birds of Interest) en fenologische verschijningsdata.\n\n" +
                        "Hoe te interpreteren?\n" +
                        "• Gepinde & Gedetecteerde Soorten: Soorten die hier ingeschakeld zijn, worden op het tellingscherm automatisch gemarkeerd en de 'Trek'-annotatie staat standaard aangevinkt.\n" +
                        "• Fenologie Vault: Vergelijkt historische verschijningsdata om te bepalen of een vogel vroeg of laat in het seizoen arriveert.",
                iconResId = R.drawable.ic_info_circle_exclamation,
                closeButtonText = "Sluiten"
            )
        }

        btnAiDailyReports.setOnClickListener {
            startActivity(Intent(this, AiReportListActiviteit::class.java))
        }

        findViewById<android.widget.ImageView>(R.id.btnInfoDailyReports)?.setOnClickListener {
            com.yvesds.vt5.core.ui.FloatingInfoDialog.show(
                context = this,
                title = "Teldag Verslagen & AI Analyses",
                bodyText = "Hier vind je automatisch gegenereerde teldagverslagen en AI-analyses op basis van voltooide tellingsessies.\n\n" +
                        "Hoe te interpreteren?\n" +
                        "• Dagtotalen & Soortenrijkdom: Overzicht van totale aantallen en diversiteit per teldag.\n" +
                        "• AI Samenvatting: Het neuraal netwerk analyseert de trekpieken in combinatie met het lokale weer en genereert een beknopt ecologisch dagverslag.",
                iconResId = R.drawable.ic_info_circle_exclamation,
                closeButtonText = "Sluiten"
            )
        }

        findViewById<MaterialButton>(R.id.btnSoortAnalyse)?.setOnClickListener {
            startActivity(Intent(this, com.yvesds.vt5.core.database.ui.DatabaseSoortOverzichtActiviteit::class.java))
        }

        findViewById<android.widget.ImageView>(R.id.btnInfoSoortAnalyse)?.setOnClickListener {
            com.yvesds.vt5.core.ui.FloatingInfoDialog.show(
                context = this,
                title = "Soortenanalyse & Windrozen",
                bodyText = "De Soortenanalyse geeft gedetailleerd inzicht in het vlieggedrag en de trekbanen van elke vogelsoort op jouw telpost.\n\n" +
                        "Hoe te interpreteren?\n" +
                        "• Globale Jaartrend: Bovenste grafiek toont het week-voor-week verloop van trek- en retouraantallen (week 1 t/m 52).\n" +
                        "• 16 Windroosgrafieken: Laten zien bij welke specifieke windrichting (N, NO, ZW, etc.) en windkracht de soort het meest intensief vliegt.",
                iconResId = R.drawable.ic_info_circle_exclamation,
                closeButtonText = "Sluiten"
            )
        }

        findViewById<MaterialButton>(R.id.btnAiEvaluation)?.setOnClickListener {
            startActivity(Intent(this, AiLogActiviteit::class.java))
        }

        findViewById<android.widget.ImageView>(R.id.btnInfoAiEvaluation)?.setOnClickListener {
            com.yvesds.vt5.core.ui.FloatingInfoDialog.show(
                context = this,
                title = "AI-Historiek & Auditlogs",
                bodyText = "De AI-Historiek biedt een transparant logboek van alle berekeningen, model-updates en inferenties die door het BSI-systeem zijn uitgevoerd.\n\n" +
                        "Hoe te interpreteren?\n" +
                        "• Inference Logs: Toont exact welke gewichten en neurale activaties gebruikt zijn bij het berekenen van prognoses.\n" +
                        "• Model Training History: Inzicht in het leerproces en de voortgang van de on-device trainingen.",
                iconResId = R.drawable.ic_info_circle_exclamation,
                closeButtonText = "Sluiten"
            )
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

    private data class SiteOption(val id: String, val name: String)

    private suspend fun loadImportManifest(): JSONObject {
        return withContext(Dispatchers.IO) {
            try {
                val importsDir = safStorageHelper.getImportsDirSuspend() ?: return@withContext JSONObject()
                val file = importsDir.findFile("bsi_import_history.json") ?: return@withContext JSONObject()
                val content = contentResolver.openInputStream(file.uri)?.use { it.bufferedReader().readText() }
                if (!content.isNullOrBlank()) JSONObject(content) else JSONObject()
            } catch (e: Exception) {
                Log.e("BsiDatacentrum", "Fout bij laden import manifest: ${e.message}")
                JSONObject()
            }
        }
    }

    private suspend fun saveImportManifest(manifest: JSONObject) {
        withContext(Dispatchers.IO) {
            try {
                val vt5Dir = safStorageHelper.getVt5DirIfExistsSuspend() ?: return@withContext
                val importsDir = safStorageHelper.findOrCreateDirectorySuspend(vt5Dir, "imports") ?: return@withContext
                val file = importsDir.findFile("bsi_import_history.json")
                    ?: importsDir.createFile("application/json", "bsi_import_history.json")
                    ?: return@withContext
                contentResolver.openOutputStream(file.uri, "wt")?.use { out ->
                    out.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                Log.e("BsiDatacentrum", "Fout bij opslaan import manifest: ${e.message}")
            }
        }
    }

    private fun checkHeaderFileHasData(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                org.dhatim.fastexcel.reader.ReadableWorkbook(inputStream).use { workbook ->
                    val sheet = workbook.sheets.findFirst().orElse(null) ?: return false
                    sheet.openStream().use { rowStream ->
                        val iterator = rowStream.iterator()
                        if (!iterator.hasNext()) return false // no row 1
                        iterator.next() // skip header row 1
                        if (!iterator.hasNext()) return false // no row 2
                        val row2 = iterator.next()
                        var cellFound = false
                        for (i in 0 until row2.cellCount) {
                            val text = row2.getCellText(i).trim()
                            if (text.isNotEmpty() && text != "null") {
                                cellFound = true
                                break
                            }
                        }
                        cellFound
                    }
                }
            } ?: false
        } catch (e: Exception) {
            Log.e("BsiDatacentrum", "Fout bij inspecteren header bestand: ${e.message}")
            false
        }
    }

    /**
     * Start de Geautomatiseerde BSI Pijplijn met visuele voortgangsindicator en duidelijke keuzes.
     */
    private fun runAutomatedBsiPipeline() {
        lifecycleScope.launch {
            val rawSiteOptions = mutableListOf<SiteOption>()
            try {
                val snapshot = withContext(Dispatchers.IO) { ServerDataCache.getOrLoad(this@BsiDatacentrumActiviteit) }
                val sites = snapshot.sitesById.values.sortedBy { it.telpostnaam.lowercase(Locale.getDefault()) }
                if (sites.isNotEmpty()) {
                    sites.forEach { site ->
                        if (site.telpostid.isNotBlank() && site.telpostnaam.isNotBlank() && site.telpostid != "5177") {
                            rawSiteOptions.add(SiteOption(site.telpostid, site.telpostnaam))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BsiDatacentrum", "Fout bij laden telposten uit ServerDataCache: ${e.message}")
            }

            if (rawSiteOptions.isEmpty()) {
                val db = VoiceTallyDatabase.getDatabase(this@BsiDatacentrumActiviteit)
                val dbSiteIds = withContext(Dispatchers.IO) { db.tellingDao().getUniqueTelpostIds() }
                dbSiteIds.filter { it != "5177" }.forEach { id ->
                    rawSiteOptions.add(SiteOption(id, "Telpost $id"))
                }
            }

            if (rawSiteOptions.isEmpty()) {
                rawSiteOptions.add(SiteOption("4310", "De Horde"))
                rawSiteOptions.add(SiteOption("5719", "Spanjaardduin"))
                rawSiteOptions.add(SiteOption("1", "Fonteintjes"))
            }

            val displayOptions = arrayOf("🌐 Alle Telposten (${rawSiteOptions.size} telposten)") +
                    rawSiteOptions.map { "📌 ${it.name} (${it.id})" }.toTypedArray()

            AlertDialog.Builder(this@BsiDatacentrumActiviteit)
                .setTitle("Selecteer Telpost voor BSI Pijplijn")
                .setItems(displayOptions) { _, index ->
                    val chosenSite = if (index == 0) null else rawSiteOptions[index - 1]
                    val targetSiteIds = if (chosenSite == null) rawSiteOptions.map { it.id }.filter { it != "5177" } else listOf(chosenSite.id)
                    val siteNameLabel = chosenSite?.let { "${it.name} (${it.id})" } ?: "Alle ${rawSiteOptions.size} telposten"

                    AlertDialog.Builder(this@BsiDatacentrumActiviteit)
                        .setTitle("Start BSI Pijplijn")
                        .setMessage("De BSI Pijplijn zal nu automatisch verwerken voor:\n📍 $siteNameLabel\n\n" +
                                "Stappen:\n" +
                                "1. Trektellen data ophalen (jaren 1950-${Calendar.getInstance().get(Calendar.YEAR)}) & slim verifiëren\n" +
                                "2. Excel-bestanden importeren in Room DB\n" +
                                "3. Historische weerdata koppelen via Yr.no\n" +
                                "4. BSI Neuraal Netwerk trainen\n\n" +
                                "Wil je de verwerking nu starten?")
                        .setPositiveButton("Starten") { _, _ ->
                            executeAutomatedPipeline(targetSiteIds, rawSiteOptions.associate { it.id to it.name })
                        }
                        .setNegativeButton("Annuleren", null)
                        .show()
                }
                .setNegativeButton("Annuleren", null)
                .show()
        }
    }

    /**
     * Voert de Pijplijn uit met slimme downloads, rij 2 controle en live voortgangsindicator.
     */
    private fun executeAutomatedPipeline(targetSiteIds: List<String>, siteNamesMap: Map<String, String>) {
        val progressDialog = ProgressDialogHelper.show(
            this,
            "BSI Pijplijn gestart...\nEen ogenblik geduld A.U.B."
        )

        lifecycleScope.launch {
            val importManager = ExcelImportManager(this@BsiDatacentrumActiviteit)
            val weatherManager = WeatherEnrichmentManager(this@BsiDatacentrumActiviteit)
            val trainer = Trainer(this@BsiDatacentrumActiviteit, ModelStore(this@BsiDatacentrumActiviteit))
            val downloader = TrektellenExcelDownloader(this@BsiDatacentrumActiviteit)

            var importedPairsCount = 0
            var totalDownloadedHeader = 0
            var totalDownloadedData = 0
            var skippedEmptyYears = 0
            var skippedExistingYears = 0

            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val manifest = loadImportManifest()
            val filteredTargetSiteIds = targetSiteIds.filter { it != "5177" }

            try {
                for ((siteIndex, siteId) in filteredTargetSiteIds.withIndex()) {
                    val siteName = siteNamesMap[siteId] ?: "Telpost $siteId"

                    for (year in currentYear downTo 1950) {
                        val manifestKey = "${siteId}_$year"

                        // Sla reeds verwerkte historische jaren over (< currentYear)
                        if (year < currentYear && manifest.has(manifestKey)) {
                            val obj = manifest.optJSONObject(manifestKey)
                            if (obj != null && obj.has("hasData")) {
                                val hasData = obj.getBoolean("hasData")
                                if (!hasData) {
                                    skippedEmptyYears++
                                } else {
                                    skippedExistingYears++
                                }
                                continue
                            }
                        }

                        ProgressDialogHelper.updateMessage(
                            progressDialog,
                            "[Stap 1/3] Telpost ${siteIndex + 1}/${targetSiteIds.size}: $siteName\n" +
                            "Controleer jaar $year op Trektellen.nl..."
                        )

                        // 1. Download Header-bestand
                        val headerResult = withContext(Dispatchers.IO) {
                            downloader.downloadSingleExport(
                                telpostNumber = siteId,
                                year = year,
                                exportType = 1,
                                validatePkHeader = true
                            )
                        }

                        if (headerResult.isFailure) {
                            val keyObj = JSONObject().apply {
                                put("hasData", false)
                                put("updated", System.currentTimeMillis())
                                put("reason", "download_failed_or_no_data")
                            }
                            manifest.put(manifestKey, keyObj)
                            saveImportManifest(manifest)
                            skippedEmptyYears++
                            continue
                        }

                        totalDownloadedHeader++
                        val headerFilename = "Trektellen_headerdata_${siteId}_${year}.xlsx"
                        val importsDir = safStorageHelper.getImportsDirSuspend()
                        val headerFile = importsDir?.findFile(headerFilename)

                        if (headerFile == null) {
                            val keyObj = JSONObject().apply {
                                put("hasData", false)
                                put("updated", System.currentTimeMillis())
                            }
                            manifest.put(manifestKey, keyObj)
                            saveImportManifest(manifest)
                            continue
                        }

                        // 2. Inspecteer Rij 2 van Header-bestand
                        val hasDataInRow2 = withContext(Dispatchers.IO) {
                            checkHeaderFileHasData(headerFile.uri)
                        }

                        if (!hasDataInRow2) {
                            Log.i("BsiPipeline", "Rij 2 is leeg voor site $siteId jaar $year. Header verwijderen, data niet downloaden.")
                            withContext(Dispatchers.IO) {
                                try { headerFile.delete() } catch (_: Exception) {}
                            }
                            val keyObj = JSONObject().apply {
                                put("hasData", false)
                                put("updated", System.currentTimeMillis())
                            }
                            manifest.put(manifestKey, keyObj)
                            saveImportManifest(manifest)
                            skippedEmptyYears++
                            continue
                        }

                        // 3. Rij 2 bevat data -> Download Data-bestand
                        ProgressDialogHelper.updateMessage(
                            progressDialog,
                            "[Stap 1/3] Telpost ${siteIndex + 1}/${targetSiteIds.size}: $siteName\n" +
                            "Jaar $year heeft tellingen! Data-bestand ophalen..."
                        )

                        val dataResult = withContext(Dispatchers.IO) {
                            downloader.downloadSingleExport(
                                telpostNumber = siteId,
                                year = year,
                                exportType = 2,
                                validatePkHeader = true
                            )
                        }

                        if (dataResult.isSuccess) {
                            totalDownloadedData++
                            val dataFilename = "Trektellen_data_${siteId}_${year}.xlsx"
                            val dataFile = importsDir.findFile(dataFilename)

                            if (dataFile != null) {
                                ProgressDialogHelper.updateMessage(
                                    progressDialog,
                                    "[Stap 1/3] Importeren in Database:\n$siteName - Jaar $year"
                                )
                                val importedOk = withContext(Dispatchers.IO) {
                                    importManager.importPair(headerFile.uri, dataFile.uri) { msg, c, t ->
                                        Log.d("BsiPipeline", "Import ($c/$t): $msg")
                                    }
                                }
                                if (importedOk) {
                                    importedPairsCount++
                                }
                            }
                        }

                        val keyObj = JSONObject().apply {
                            put("hasData", true)
                            put("updated", System.currentTimeMillis())
                        }
                        manifest.put(manifestKey, keyObj)
                        saveImportManifest(manifest)
                    }
                }

                writeLogToVt5Logs(
                    "PIPELINE_STEP1",
                    "Slimme download & import voltooid.\n" +
                    "Geïmporteerde koppels: $importedPairsCount\n" +
                    "Headers gedownload: $totalDownloadedHeader, Data gedownload: $totalDownloadedData\n" +
                    "Overgeslagen lege jaren: $skippedEmptyYears, Reeds verwerkte historische jaren: $skippedExistingYears"
                )

                // STAP 2: Weerverrijking via Yr.no
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

                // STAP 3: BSI Model Trainen
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
                try {
                    com.yvesds.vt5.core.opslag.ErrorLogger.logError(
                        this@BsiDatacentrumActiviteit,
                        "BsiDatacentrum",
                        "Fout in BSI Pijplijn: ${e.message}",
                        e
                    )
                } catch (_: Exception) {}
            } finally {
                try { progressDialog.dismiss() } catch (_: Exception) {}
            }

            val successMessage = "BSI Pijplijn Succesvol Voltooid! 🎉\n\n" +
                    "📊 Resultaten van de verwerking:\n" +
                    "• Geïmporteerde koppel-bestanden: $importedPairsCount\n" +
                    "• Nieuwe/Bijgewerkte data-bestanden: $totalDownloadedData\n" +
                    "• Slim overgeslagen lege/onbekende jaren: $skippedEmptyYears\n" +
                    "• Reeds bekende historische jaren overgeslagen: $skippedExistingYears\n\n" +
                    "De weerdata is bijgewerkt en het BSI Neuraal Netwerk is opnieuw getraind voor jouw telpost(en)."

            AlertDialog.Builder(this@BsiDatacentrumActiviteit)
                .setTitle("BSI Pijplijn Voltooid!")
                .setMessage(successMessage)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun runExcelBatchImport() {
        lifecycleScope.launch {
            val importsDir = safStorageHelper.getImportsDirSuspend()
            val files = importsDir?.listFiles() ?: emptyArray()
            val headerFilesAll = files.filter { it.name?.startsWith("Trektellen_headerdata_") == true && it.name?.endsWith(".xlsx") == true }
            val siteIdsInImports = headerFilesAll.mapNotNull { f ->
                val parts = f.name?.split("_") ?: return@mapNotNull null
                if (parts.size >= 3) parts[2] else null
            }.distinct().sorted()

            if (siteIdsInImports.isEmpty()) {
                Toast.makeText(this@BsiDatacentrumActiviteit, "Geen Excel bestanden gevonden in Documents/VT5/imports/", Toast.LENGTH_LONG).show()
                return@launch
            }

            val options = arrayOf("🌐 Alle Telposten importeren (${siteIdsInImports.size} telposten)") + siteIdsInImports.map { "📌 Alleen Telpost: $it" }.toTypedArray()

            AlertDialog.Builder(this@BsiDatacentrumActiviteit)
                .setTitle("Selecteer Telpost voor Batch Import")
                .setItems(options) { _, index ->
                    val chosenSite = if (index == 0) null else siteIdsInImports[index - 1]
                    executeExcelBatchImportForSite(chosenSite)
                }
                .setNegativeButton("Annuleren", null)
                .show()
        }
    }

    private fun executeExcelBatchImportForSite(targetSiteId: String?) {
        val progress = ProgressDialogHelper.show(this, "Batch import starten uit Documents/VT5/imports/...")
        lifecycleScope.launch {
            val importManager = ExcelImportManager(this@BsiDatacentrumActiviteit)
            val importsDir = safStorageHelper.getImportsDirSuspend()
            var count = 0

            if (importsDir != null) {
                val files = importsDir.listFiles()
                val headerFiles = files.filter { file ->
                    val name = file.name ?: ""
                    val isHeader = name.startsWith("Trektellen_headerdata_") && name.endsWith(".xlsx")
                    if (!isHeader) false
                    else if (targetSiteId != null) name.startsWith("Trektellen_headerdata_${targetSiteId}_")
                    else true
                }

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