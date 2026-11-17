package com.yvesds.vt5.hoofd

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.google.android.material.button.MaterialButton
import com.yvesds.vt5.R
import com.yvesds.vt5.core.app.AppShutdown
import com.yvesds.vt5.core.app.HourlyAlarmManager
import com.yvesds.vt5.core.opslag.FileLogger
import com.yvesds.vt5.core.opslag.AppDataStore
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.features.metadata.ui.MetadataScherm
import com.yvesds.vt5.features.masterClient.MasterClientPrefs
import com.yvesds.vt5.features.masterClient.MasterClientRuntimeStore
import com.yvesds.vt5.features.masterClient.McQrPayloadCodec
import com.yvesds.vt5.features.masterClient.McRuntimePermissions
import com.yvesds.vt5.features.opstart.ui.InstallatieScherm
import com.yvesds.vt5.features.telling.TellingEnvelopePersistence
import com.yvesds.vt5.features.telling.RoomTellingReopenLauncher
import com.yvesds.vt5.features.telling.TellingScherm
import com.yvesds.vt5.features.telling.TellingSessionManager
import com.yvesds.vt5.features.telling.TellingStatus
import com.yvesds.vt5.features.telling.TellingUploadFlags
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.entities.TellingHeader
import com.yvesds.vt5.core.database.entities.Waarneming
import com.yvesds.vt5.core.database.ui.DatabaseBeheerScherm
import com.yvesds.vt5.core.database.ui.DatabaseBeheerScherm.Companion.PREF_BATCH_IMPORT_ACTIVE
import com.yvesds.vt5.core.database.ui.DatabaseTellingLijstActiviteit
import com.yvesds.vt5.core.ui.DialogStyler
import com.yvesds.vt5.core.ui.ProgressDialogHelper
import androidx.activity.OnBackPressedCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.ExperimentalSerializationApi
import androidx.activity.result.ActivityResultLauncher
import android.net.Uri
import com.yvesds.vt5.ai.ModelStore
import com.yvesds.vt5.ai.TrainingDataPreparer
import com.yvesds.vt5.features.update.AppUpdateManager
import android.view.View

/**
 * HoofdActiviteit - Hoofdscherm van VT5 app
 * 
 * Biedt drie opties:
 * 1. (Her)Installatie → InstallatieScherm
 * 2. Invoeren telpostgegevens → MetadataScherm
 * 3. Invoegen als client in de master-client modus
 * 4. Afsluiten → Veilige app shutdown met cleanup
 * ------------------------------------------------
 * 5. Toggle uurlijks alarm aan/uit
 * 6. Bewerk tellingen → Room sessielijst
 * 7. Opkuis exports → Verwijder oude bestanden uit exports map
 * 8. Instellingen bewerken
 * 9. Databasebeheer
 */
class HoofdActiviteit : AppCompatActivity() {
    private val TAG = "HoofdActiviteit"

    companion object {
        private const val PREFS_UPLOADS = "vt5_uploads"
        private const val KEY_PENDING_UPLOADS = "pending_uploads"
        private const val KEY_UPLOAD_ON_EXIT = "upload_on_exit"
    }

    @Volatile
    private var isExiting = false

    private lateinit var safHelper: SaFStorageHelper
    private lateinit var fileLogger: FileLogger
    private var pendingTellingDialogShown = false
    private var pendingAiUpdateAfterSaF: Boolean = false
    private lateinit var openTreeLauncher: ActivityResultLauncher<Uri?>
    
    private var isDataEnriched = false
    private var isAiTrained = false

    private val clientQrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        val raw = result.contents?.trim().orEmpty()
        if (raw.isBlank()) return@registerForActivityResult

        val payload = McQrPayloadCodec.decode(raw)
        if (payload == null) {
            Toast.makeText(this, getString(R.string.mc_pairing_qr_invalid), Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        promptForClientIdentity {
            TellingSessionManager.clear()
            MasterClientRuntimeStore.clearAll()
            MasterClientPrefs.clearSession(this)
            MasterClientPrefs.setMode(this, MasterClientPrefs.MODE_CLIENT)
            Toast.makeText(this, getString(R.string.mc_client_starting_telling), Toast.LENGTH_SHORT).show()
            startActivity(
                Intent(this, TellingScherm::class.java).apply {
                    putExtra(TellingScherm.EXTRA_CLIENT_QR_PAYLOAD, raw)
                }
            )
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        McRuntimePermissions.cachePermissionResult(this, Manifest.permission.CAMERA, granted)
        if (granted) {
            launchClientQrScanner()
        } else {
            Toast.makeText(this, getString(R.string.mc_pairing_qr_invalid), Toast.LENGTH_SHORT).show()
        }
    }

    private val requestStartupPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        McRuntimePermissions.cachePermissionResults(this, grants)
        val allGranted = grants.isNotEmpty() && grants.values.all { it }
        if (!allGranted && !McRuntimePermissions.hasAllStartupPermissions(this)) {
            Toast.makeText(this, getString(R.string.mc_permissions_startup_required), Toast.LENGTH_LONG).show()
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_hoofd)
        
        safHelper = SaFStorageHelper(this)
        fileLogger = FileLogger(this)
        
        // Check for active batch import to redirect
        val batchPrefs = getSharedPreferences("batch_import", MODE_PRIVATE)
        if (batchPrefs.getBoolean(DatabaseBeheerScherm.PREF_BATCH_IMPORT_ACTIVE, false)) {
            Log.i(TAG, "Active batch import detected, redirecting to DatabaseBeheerScherm")
            startActivity(Intent(this, DatabaseBeheerScherm::class.java))
            // We wissen het vlaggetje hier om een oneindige loop te voorkomen bij crashes
            batchPrefs.edit { putBoolean(DatabaseBeheerScherm.PREF_BATCH_IMPORT_ACTIVE, false) }
            finish()
            return
        }

        openTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                try {
                    safHelper.takePersistablePermission(uri)
                    safHelper.saveRootUri(uri)
                } catch (_: Exception) {}
                // If there was a pending ai update, run it now
                if (pendingAiUpdateAfterSaF) {
                    pendingAiUpdateAfterSaF = false
                    startActivity(Intent(this, com.yvesds.vt5.ai.BsiHubActiviteit::class.java))
                }
            }
        }
        requestStartupPermissionsIfNeeded()

        lifecycleScope.launch {
            val storageMode = InstellingenScherm.getStorageMode(this@HoofdActiviteit)
            fileLogger.info("HoofdActiviteit gestart - VT5 App actief")
            fileLogger.info("Geselecteerde opslagmodus: $storageMode")
        }

        // Controleer op eventueel aanwezig onverzonden fatale crashlog
        checkAndPromptUnsentFatalCrashLogs()

        // Check if the splash screen passed AI-load status and inform the user.
        try {
            val aiLoaded = intent.getBooleanExtra("ai_model_loaded", false)
            val aiError = intent.getStringExtra("ai_model_error")
            if (aiLoaded) {
                Toast.makeText(this, "AI-model geladen", Toast.LENGTH_SHORT).show()
            } else if (!aiError.isNullOrBlank()) {
                val dlg = AlertDialog.Builder(this)
                    .setTitle("AI-model fout")
                    .setMessage(aiError)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                DialogStyler.apply(dlg)
            }
        } catch (_: Exception) {}

        val btnInstall   = findViewById<MaterialButton>(R.id.btnInstall)
        val btnVerder    = findViewById<MaterialButton>(R.id.btnVerder)
        val btnJoinAsClient = findViewById<MaterialButton>(R.id.btnJoinAsClient)
        val btnAfsluiten = findViewById<MaterialButton>(R.id.btnAfsluiten)
        val btnBewerkTellingen = findViewById<MaterialButton>(R.id.btnBewerkTellingen)
        val btnSettings = findViewById<MaterialButton>(R.id.btnSettings)
        val btnTelpostBeheer = findViewById<MaterialButton>(R.id.btnTelpostBeheer)
        val btnDatabaseBeheer = findViewById<MaterialButton>(R.id.btnDatabaseBeheer)
        val btnOptiesMenu = findViewById<MaterialButton>(R.id.btnOptiesMenu)
        val layoutOptiesMenu = findViewById<View>(R.id.layoutOptiesMenu)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isExiting) return
                shutdownAndExit()
            }
        })

        btnOptiesMenu?.setOnClickListener {
            if (layoutOptiesMenu != null) {
                val isVisible = layoutOptiesMenu.visibility == View.VISIBLE
                layoutOptiesMenu.visibility = if (isVisible) View.GONE else View.VISIBLE
                btnOptiesMenu.text = if (isVisible) "⚙️ BEHEER & OPTIES" else "✖ SLUIT OPTIES MENU"
            }
        }

        // Alarm sectie - altijd zichtbaar
        setupAlarmSection()

        btnInstall.setOnClickListener {
            it.isEnabled = false
            startActivity(Intent(this, InstallatieScherm::class.java))
            it.isEnabled = true
        }

        btnVerder.setOnClickListener {
            handleStartOrResumeTellingClick(it)
        }

        btnJoinAsClient.setOnClickListener {
            it.isEnabled = false
            startClientQrJoinFlow()
            it.postDelayed({ it.isEnabled = true }, 600)
        }

        btnAfsluiten.setOnClickListener {
            if (isExiting) return@setOnClickListener
            it.isEnabled = false
            shutdownAndExit()
        }
        
        // Bewerk tellingen knop
        btnBewerkTellingen.setOnClickListener {
            it.isEnabled = false
            startActivity(Intent(this, DatabaseTellingLijstActiviteit::class.java))
            it.isEnabled = true
        }
        
        // Settings knop rechtsboven
        btnSettings?.setOnClickListener {
            it.isEnabled = false
            startActivity(Intent(this, InstellingenScherm::class.java))
            it.isEnabled = true
        }

        // BSI Header knop (BSI Hub)
        findViewById<MaterialButton>(R.id.btnBsiHeader)?.setOnClickListener {
            it.isEnabled = false
            startActivity(Intent(this, com.yvesds.vt5.ai.BsiHubActiviteit::class.java))
            it.isEnabled = true
        }

        updateBsiHeaderVisibility()
        showOnboardingInfoScreen(forceShow = false)

        // Info Icoon onderaan (Snelcursus / Onboarding Floating Info Screen)
        findViewById<View>(R.id.btnInfoOnboarding)?.setOnClickListener {
            showOnboardingInfoScreen(forceShow = true)
        }

        // Database Beheer knop
        btnDatabaseBeheer.setOnClickListener {
            it.isEnabled = false
            startActivity(Intent(this, DatabaseBeheerScherm::class.java))
            it.isEnabled = true
        }

        // Telpost Beheer knop
        btnTelpostBeheer.setOnClickListener {
            it.isEnabled = false
            startActivity(Intent(this, com.yvesds.vt5.core.database.ui.TelpostBeheerActiviteit::class.java))
            it.isEnabled = true
        }



        // Luister continu naar update-status in DataStore voor real-time badge-updates op de instellingenknop
        lifecycleScope.launch {
            AppDataStore.getUpdateSnapshotFlow(this@HoofdActiviteit).collect { snapshot ->
                findViewById<TextView>(R.id.badgeAppUpdate)?.visibility = if (snapshot.updateAvailable) View.VISIBLE else View.GONE
            }
        }

        refreshAppUpdateBadge(showPrompt = true)
    }

    private fun checkAndPromptUnsentFatalCrashLogs() {
        lifecycleScope.launch {
            try {
                val enabled = com.yvesds.vt5.core.opslag.AppDataStore.isErrorReportingEmailEnabled(this@HoofdActiviteit)
                if (!enabled) return@launch

                val targetEmail = com.yvesds.vt5.core.opslag.AppDataStore.getErrorReportingEmail(this@HoofdActiviteit)
                val saf = com.yvesds.vt5.core.opslag.SaFStorageHelper(this@HoofdActiviteit)
                val errorLogsDir = withContext(Dispatchers.IO) { saf.getErrorLogsDir() }

                var unsentDocFile: androidx.documentfile.provider.DocumentFile? = null
                var unsentRawFile: java.io.File? = null

                withContext(Dispatchers.IO) {
                    if (errorLogsDir != null) {
                        unsentDocFile = errorLogsDir.listFiles().firstOrNull { file ->
                            val name = file.name ?: ""
                            name.startsWith("fatal_crash_") && name.endsWith(".log") && !name.contains(".sent.")
                        }
                    }
                    if (unsentDocFile == null) {
                        val root = java.io.File(filesDir, "VT5")
                        val dir = java.io.File(root, "errorlogs")
                        if (dir.exists()) {
                            unsentRawFile = dir.listFiles()?.firstOrNull { file ->
                                file.name.startsWith("fatal_crash_") && file.name.endsWith(".log") && !file.name.contains(".sent.")
                            }
                        }
                    }
                }

                val filename = unsentDocFile?.name ?: unsentRawFile?.name ?: return@launch

                val dlg = AlertDialog.Builder(this@HoofdActiviteit)
                    .setTitle("Onverwachte crash gedetecteerd")
                    .setMessage("VT5 is de vorige keer onverwacht afgesloten.\n\nWil je het foutdiagnose-logbestand ($filename) per e-mail verzenden naar ${if (targetEmail.isBlank()) "het gekozen e-mailadres" else targetEmail}?")
                    .setPositiveButton("E-mailen") { _, _ ->
                        val sent = com.yvesds.vt5.core.opslag.ErrorLogEmailSender.sendErrorLogByEmail(
                            context = this@HoofdActiviteit,
                            targetEmail = targetEmail,
                            docFile = unsentDocFile,
                            rawFile = unsentRawFile
                        )
                        if (sent) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                com.yvesds.vt5.core.opslag.ErrorLogger.markAsSent(this@HoofdActiviteit, filename)
                            }
                        }
                    }
                    .setNegativeButton("Nee", null)
                    .show()
                DialogStyler.apply(dlg)
            } catch (ex: Exception) {
                Log.w(TAG, "checkAndPromptUnsentFatalCrashLogs mislukt: ${ex.message}")
            }
        }
    }

    private fun handleStartOrResumeTellingClick(button: View) {
        button.isEnabled = false
        lifecycleScope.launch {
            var dialogShown = false
            try {
                val db = VoiceTallyDatabase.getDatabase(this@HoofdActiviteit)
                val unfinishedPairs = withContext(Dispatchers.IO) {
                    val dao = db.tellingDao()
                    val allHeaders = dao.getAllHeaders()
                    val result = mutableListOf<Pair<TellingHeader, List<Waarneming>>>()
                    for (header in allHeaders) {
                        val statusNorm = TellingStatus.normalize(header.status)
                        val isSent = TellingUploadFlags.isSent(this@HoofdActiviteit, header.tellingid, header.onlineid)
                        if (statusNorm != TellingStatus.GEUPLOAD && statusNorm != TellingStatus.GEARCHIVEERD && !isSent) {
                            val records = dao.getWaarnemingenList(header.tellingid, header.onlineid)
                            result.add(header to records)
                        }
                    }
                    result.sortedByDescending { it.first.begintijd }
                }

                if (unfinishedPairs.isNotEmpty()) {
                    dialogShown = true
                    showUnfinishedTellingenFloatingDialog(unfinishedPairs, button)
                    return@launch
                }
            } catch (e: Exception) {
                Log.e(TAG, "Check for unfinished telling failed: ${e.message}", e)
                withContext(Dispatchers.IO) {
                    com.yvesds.vt5.core.opslag.ErrorLogger.logError(
                        this@HoofdActiviteit,
                        "HoofdActiviteit",
                        "Fout bij 'Telling starten / hervatten' knop: ${e.message}",
                        e
                    )
                }
            } finally {
                if (!dialogShown) {
                    button.isEnabled = true
                }
            }

            // Scenario A: Geen onafgewerkte telling -> direct naar metadatascherm
            startNewTellingFlow(button)
        }
    }

    private fun showUnfinishedTellingenFloatingDialog(
        unfinishedPairs: List<Pair<TellingHeader, List<Waarneming>>>,
        button: View
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_unfinished_tellingen_floating, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val container = dialogView.findViewById<LinearLayout>(R.id.containerFloatingCards)
        val btnMarkAllAndStartNew = dialogView.findViewById<MaterialButton>(R.id.btnMarkAllCompletedAndStartNew)

        val currentPairs = unfinishedPairs.toMutableList()

        fun renderCards() {
            container.removeAllViews()
            if (currentPairs.isEmpty()) {
                dialog.dismiss()
                button.isEnabled = true
                startNewTellingFlow(button)
                return
            }

            refreshUnfinishedTellingenBadge()

            for (pair in currentPairs) {
                val header = pair.first
                val records = pair.second
                val cardView = layoutInflater.inflate(R.layout.item_unfinished_telling_card, container, false)

                val tvTelpost = cardView.findViewById<TextView>(R.id.tvCardTelpost)
                val tvDatumTijd = cardView.findViewById<TextView>(R.id.tvCardDatumTijd)
                val tvAantallen = cardView.findViewById<TextView>(R.id.tvCardAantallen)
                val btnHervatten = cardView.findViewById<MaterialButton>(R.id.btnCardHervatten)
                val btnAfgewerkt = cardView.findViewById<MaterialButton>(R.id.btnCardAfgewerktMarkeren)

                val countRecords = records.size
                val countBirds = records.sumOf { (it.aantal.toIntOrNull() ?: 0) + (it.aantalterug.toIntOrNull() ?: 0) }
                val rawDate = header.begintijd.takeIf { it.isNotBlank() } ?: "Onbekende datum"
                val formattedDate = com.yvesds.vt5.core.database.ui.SpeciesNameResolver.formatTimestamp(rawDate)
                val dateText = if (formattedDate.isNotBlank()) formattedDate else rawDate
                val telpostText = header.telpostid.ifBlank { "Telpost" }

                tvTelpost.text = "📍 $telpostText"
                tvDatumTijd.text = "📅 $dateText"
                tvAantallen.text = "📊 $countRecords records ($countBirds vogels)"

                btnHervatten.setOnClickListener {
                    dialog.dismiss()
                    button.isEnabled = true
                    lifecycleScope.launch {
                        try {
                            var freshRecords = withContext(Dispatchers.IO) {
                                val db = VoiceTallyDatabase.getDatabase(this@HoofdActiviteit)
                                db.tellingDao().getWaarnemingenList(header.tellingid, header.onlineid)
                            }
                            if (freshRecords.isEmpty()) {
                                val recovered = withContext(Dispatchers.IO) {
                                    tryRecoverRecordsFromExportsInHoofd(header.tellingid, header.onlineid)
                                }
                                if (recovered.isNotEmpty()) {
                                    freshRecords = withContext(Dispatchers.IO) {
                                        val db = VoiceTallyDatabase.getDatabase(this@HoofdActiviteit)
                                        db.tellingDao().getWaarnemingenList(header.tellingid, header.onlineid)
                                    }
                                }
                            }
                            RoomTellingReopenLauncher.launch(
                                activity = this@HoofdActiviteit,
                                header = header,
                                records = freshRecords,
                                finishCurrent = false
                            )
                        } catch (ex: Exception) {
                            Log.e(TAG, "Hervatten mislukt: ${ex.message}", ex)
                            withContext(Dispatchers.IO) {
                                com.yvesds.vt5.core.opslag.ErrorLogger.logError(
                                    this@HoofdActiviteit,
                                    "HoofdActiviteit",
                                    "Fout bij hervatten van telling [${header.tellingid}]: ${ex.message}",
                                    ex
                                )
                            }
                        }
                    }
                }

                btnAfgewerkt.setOnClickListener {
                    lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val dao = VoiceTallyDatabase.getDatabase(this@HoofdActiviteit).tellingDao()
                                val current = dao.getHeader(header.tellingid)
                                if (current != null) {
                                    dao.updateHeader(current.copy(status = TellingStatus.GEUPLOAD))
                                    TellingUploadFlags.markSent(this@HoofdActiviteit, current.tellingid, current.onlineid)
                                }
                            }
                            currentPairs.removeAll { it.first.tellingid == header.tellingid }
                            renderCards()
                        } catch (ex: Exception) {
                            Log.e(TAG, "Marking header completed failed: ${ex.message}", ex)
                        }
                    }
                }

                container.addView(cardView)
            }
        }

        btnMarkAllAndStartNew.setOnClickListener {
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val dao = VoiceTallyDatabase.getDatabase(this@HoofdActiviteit).tellingDao()
                        for (pair in currentPairs) {
                            val header = pair.first
                            val current = dao.getHeader(header.tellingid)
                            if (current != null) {
                                dao.updateHeader(current.copy(status = TellingStatus.GEUPLOAD))
                                TellingUploadFlags.markSent(this@HoofdActiviteit, current.tellingid, current.onlineid)
                            }
                        }
                    }
                    dialog.dismiss()
                    button.isEnabled = true
                    startNewTellingFlow(button)
                } catch (ex: Exception) {
                    Log.e(TAG, "Marking all completed failed: ${ex.message}", ex)
                }
            }
        }

        dialog.setOnDismissListener {
            button.isEnabled = true
        }

        renderCards()
        dialog.show()
        DialogStyler.apply(dialog)
    }

    private suspend fun tryRecoverRecordsFromExportsInHoofd(targetTellingId: String, targetOnlineId: String): List<Waarneming> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Waarneming>()
        try {
            val safHelper = com.yvesds.vt5.core.opslag.SaFStorageHelper(this@HoofdActiviteit)
            val exportsDir = safHelper.getExportsDirSuspend() ?: return@withContext emptyList()
            val files = exportsDir.listFiles().filter { it.isFile }

            val tidClean = targetTellingId.trim()
            val oidClean = targetOnlineId.trim()
            val dao = VoiceTallyDatabase.getDatabase(this@HoofdActiviteit).tellingDao()

            for (file in files) {
                val name = file.name ?: continue
                if (!name.endsWith(".txt") && !name.endsWith(".json")) continue

                val text = contentResolver.openInputStream(file.uri)?.use {
                    it.bufferedReader(Charsets.UTF_8).readText()
                } ?: continue

                if (text.isBlank()) continue

                var matchFound = false
                if (tidClean.isNotBlank() && (text.contains("TellingId: $tidClean", ignoreCase = true) || text.contains("tellingid=$tidClean", ignoreCase = true))) {
                    matchFound = true
                }
                if (!matchFound && oidClean.isNotBlank() && (text.contains("Onlineid: $oidClean", ignoreCase = true) || text.contains("onlineid=$oidClean", ignoreCase = true))) {
                    matchFound = true
                }

                if (!matchFound) continue

                val recordItem = parseTxtBackupRecordInHoofd(text) ?: continue
                val tid = recordItem.tellingid.ifBlank { tidClean }
                val oid = oidClean

                var header = dao.getHeader(tid)
                if (header == null && oid.isNotBlank()) header = dao.getHeader(oid)
                if (header == null) {
                    header = TellingHeader(
                        tellingid = tid,
                        onlineid = oid,
                        telpostid = "Restored",
                        begintijd = recordItem.tijdstip,
                        status = TellingStatus.ACTIEF
                    )
                    dao.insertHeader(header)
                }

                val entity = Waarneming(
                    idLocal = recordItem.idLocal,
                    tellingid = tid,
                    onlineid = oid,
                    soortid = recordItem.soortid,
                    aantal = recordItem.aantal,
                    richting = recordItem.richting,
                    aantalterug = recordItem.aantalterug,
                    richtingterug = recordItem.richtingterug,
                    sightingdirection = recordItem.sightingdirection,
                    lokaal = recordItem.lokaal,
                    geslacht = recordItem.geslacht,
                    leeftijd = recordItem.leeftijd,
                    kleed = recordItem.kleed,
                    opmerkingen = recordItem.opmerkingen,
                    tijdstip = recordItem.tijdstip
                )
                dao.insertWaarneming(entity)
                result.add(entity)
            }
        } catch (e: Exception) {
            Log.w(TAG, "tryRecoverRecordsFromExportsInHoofd error: ${e.message}", e)
        }
        return@withContext result
    }

    private fun parseTxtBackupRecordInHoofd(text: String): com.yvesds.vt5.net.ServerTellingDataItem? {
        if (text.isBlank()) return null
        val jsonParsed = runCatching {
            com.yvesds.vt5.VT5App.json.decodeFromString(com.yvesds.vt5.net.ServerTellingDataItem.serializer(), text)
        }.getOrNull()
        if (jsonParsed != null && jsonParsed.idLocal.isNotBlank()) return jsonParsed

        try {
            var tellingId = ""
            var onlineId = ""
            var idLocal = ""
            var soortId = ""
            var aantal = "0"
            var aantalterug = "0"
            var timestamp = ""
            var groupId = ""

            val lines = text.lines()
            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("TellingId:", ignoreCase = true) -> tellingId = trimmed.substringAfter(":").trim()
                    trimmed.startsWith("Onlineid:", ignoreCase = true) -> onlineId = trimmed.substringAfter(":").trim()
                    trimmed.startsWith("IdLocal:", ignoreCase = true) -> idLocal = trimmed.substringAfter(":").trim()
                    trimmed.startsWith("SoortId:", ignoreCase = true) -> soortId = trimmed.substringAfter(":").trim()
                    trimmed.startsWith("Aantal:", ignoreCase = true) -> aantal = trimmed.substringAfter(":").trim()
                    trimmed.startsWith("Aantal terug:", ignoreCase = true) -> aantalterug = trimmed.substringAfter(":").trim()
                    trimmed.startsWith("Timestamp:", ignoreCase = true) -> timestamp = trimmed.substringAfter(":").trim()
                    trimmed.startsWith("GroupId:", ignoreCase = true) -> groupId = trimmed.substringAfter(":").trim()
                }
            }

            if (idLocal.isBlank() && text.contains("idLocal=")) {
                idLocal = text.substringAfter("idLocal=").substringBefore(",").trim()
            }
            if (tellingId.isBlank() && text.contains("tellingid=")) {
                tellingId = text.substringAfter("tellingid=").substringBefore(",").trim()
            }
            if (soortId.isBlank() && text.contains("soortid=")) {
                soortId = text.substringAfter("soortid=").substringBefore(",").trim()
            }
            if (timestamp.isBlank() && text.contains("tijdstip=")) {
                timestamp = text.substringAfter("tijdstip=").substringBefore(",").trim()
            }
            if (aantalterug.isBlank() && text.contains("aantalterug=")) {
                aantalterug = text.substringAfter("aantalterug=").substringBefore(",").trim()
            }

            if (idLocal.isNotBlank() && (tellingId.isNotBlank() || onlineId.isNotBlank())) {
                return com.yvesds.vt5.net.ServerTellingDataItem(
                    idLocal = idLocal,
                    tellingid = tellingId,
                    soortid = soortId,
                    aantal = aantal,
                    aantalterug = aantalterug,
                    tijdstip = timestamp,
                    groupid = groupId.ifBlank { idLocal }
                )
            }
        } catch (_: Exception) {}
        return null
    }

    private fun startNewTellingFlow(button: View) {
        Toast.makeText(this, getString(R.string.hoofd_metadata_loading), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                com.yvesds.vt5.features.serverdata.model.ServerDataCache.getOrLoad(this@HoofdActiviteit)
            } catch (e: Exception) {
                Log.w(TAG, "Background preload failed (non-critical): ${e.message}")
            }
        }

        startActivity(Intent(this, MetadataScherm::class.java))
        button.isEnabled = true
    }

    private fun maybeShowPendingTellingDialog() {
        if (pendingTellingDialogShown) return
        lifecycleScope.launch {
            try {
                val mode = InstellingenScherm.getStorageMode(this@HoofdActiviteit)
                val isHybrid = true
                
                val tellingId: String
                val onlineId: String
                val hasPending: Boolean

                if (isHybrid) {
                    val prefs = getSharedPreferences("vt5_prefs", MODE_PRIVATE)
                    tellingId = prefs.getString("pref_telling_id", "") ?: ""
                    onlineId = prefs.getString("pref_online_id", "") ?: ""
                    
                    if (tellingId.isBlank()) {
                        hasPending = false
                    } else {
                        val header = withContext(Dispatchers.IO) {
                            VoiceTallyDatabase.getDatabase(this@HoofdActiviteit).tellingDao().getHeader(tellingId) 
                        }
                        // Sessie is pending als status nog niet geupload is en er records zijn
                        if (header != null && TellingStatus.isPendingUpload(header.status)) {
                            val count = withContext(Dispatchers.IO) {
                                VoiceTallyDatabase.getDatabase(this@HoofdActiviteit).tellingDao().getWaarnemingenList(tellingId).size 
                            }
                            hasPending = count > 0
                        } else {
                            hasPending = false
                        }
                    }
                } else {
                    val envelopePersistence = TellingEnvelopePersistence(this@HoofdActiviteit, safHelper)
                    val savedEnvelope = if (envelopePersistence.hasSavedEnvelope()) envelopePersistence.loadSavedEnvelope() else null
                    tellingId = savedEnvelope?.tellingid ?: ""
                    onlineId = savedEnvelope?.onlineid ?: ""
                    hasPending = savedEnvelope != null && savedEnvelope.data.isNotEmpty()
                }

                if (!hasPending) return@launch

                if (TellingUploadFlags.isSent(this@HoofdActiviteit, tellingId, onlineId)) {
                    Log.i(TAG, "Pending session already marked as sent; skipping prompt")
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    pendingTellingDialogShown = true
                    val dlg = AlertDialog.Builder(this@HoofdActiviteit)
                        .setTitle(getString(R.string.pending_telling_title))
                        .setMessage(getString(R.string.pending_telling_message))
                        .setPositiveButton(getString(R.string.pending_telling_open)) { _, _ ->
                            val intent = Intent(this@HoofdActiviteit, TellingScherm::class.java)
                            intent.putExtra(TellingScherm.EXTRA_RESTORE_PENDING_TELLING, true)
                            startActivity(intent)
                        }
                        .setNegativeButton(getString(R.string.pending_telling_delete)) { _, _ ->
                            lifecycleScope.launch {
                                val envelopePersistence = TellingEnvelopePersistence(this@HoofdActiviteit, safHelper)
                                deletePendingTelling(envelopePersistence, tellingId, onlineId)
                            }
                        }
                        .setOnDismissListener { pendingTellingDialogShown = false }
                        .show()
                    DialogStyler.apply(dlg)
                }
            } catch (e: Exception) {
                Log.w(TAG, "maybeShowPendingTellingDialog failed: ${e.message}", e)
            }
        }
    }

    private suspend fun deletePendingTelling(
        envelopePersistence: TellingEnvelopePersistence,
        tellingId: String?,
        onlineId: String?
    ) {
        try {
            envelopePersistence.clearSavedEnvelope()
            TellingUploadFlags.clearFlag(this@HoofdActiviteit, tellingId, onlineId)

            // Database status bijwerken naar 'gearchiveerd'
            if (!tellingId.isNullOrBlank()) {
                withContext(Dispatchers.IO) {
                    val db = VoiceTallyDatabase.getDatabase(this@HoofdActiviteit)
                    val header = db.tellingDao().getHeader(tellingId)
                    if (header != null) {
                        db.tellingDao().updateHeader(header.copy(status = "gearchiveerd"))
                    }
                }
            }

            val prefs = getSharedPreferences("vt5_prefs", MODE_PRIVATE)
            prefs.edit {
                remove("pref_saved_envelope_json")
                remove("pref_online_id")
                remove("pref_telling_id")
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@HoofdActiviteit, getString(R.string.pending_telling_deleted), Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.w(TAG, "deletePendingTelling failed: ${e.message}", e)
        }
    }

    private fun startClientQrJoinFlow() {
        val hasCameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasCameraPermission) {
            launchClientQrScanner()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun requestStartupPermissionsIfNeeded() {
        McRuntimePermissions.refreshCachedPermissionStates(this)
        val missingPermissions = McRuntimePermissions.autoPromptStartupPermissions(this)
        if (missingPermissions.isNotEmpty()) {
            requestStartupPermissionsLauncher.launch(missingPermissions)
        }
    }

    private fun launchClientQrScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setBeepEnabled(false)
            setOrientationLocked(true)
            setPrompt(getString(R.string.mc_scan_client_qr_prompt))
        }
        clientQrScanLauncher.launch(options)
    }

    private fun promptForClientIdentity(onConfirmed: () -> Unit) {
        val input = EditText(this).apply {
            hint = getString(R.string.mc_client_identity_hint)
            setSingleLine(true)
            setText(MasterClientPrefs.getClientAlias(this@HoofdActiviteit))
            setSelection(text?.length ?: 0)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.mc_client_identity_title)
            .setMessage(R.string.mc_client_identity_message)
            .setView(input)
            .setPositiveButton(R.string.mc_client_identity_continue) { _, _ ->
                MasterClientPrefs.setClientAlias(this, input.text?.toString().orEmpty())
                onConfirmed()
            }
            .setNegativeButton(R.string.annuleer, null)
            .create()
        DialogStyler.apply(dialog)
        dialog.show()
    }

    /**
     * Voert een veilige shutdown uit bij het afsluiten:
     * - Toont toast 'Tijdelijke bestanden verwijderen...'
     * - Blokkeert afsluiten gedurende opkuis
     * - Verwijder alle export bestanden ouder dan de laatste 5 TELDAGEN
     * - Toont toast 'Tijdelijke bestanden verwijderd'
     * - Roept AppShutdown.shutdownApp() voor cleanup
     * - Verwijdert de app uit 'recente apps' (finishAndRemoveTask)
     */
    private fun shutdownAndExit() {
        if (isExiting) return
        isExiting = true

        val prefs = getSharedPreferences(PREFS_UPLOADS, MODE_PRIVATE)
        val pendingUploads = prefs.getBoolean(KEY_PENDING_UPLOADS, false)
        val uploadOnExit = prefs.getBoolean(KEY_UPLOAD_ON_EXIT, false)
        if (pendingUploads && uploadOnExit) {
            isExiting = false
            startActivity(Intent(this, DatabaseTellingLijstActiviteit::class.java))
            return
        }

        Log.i(TAG, "User initiated app shutdown with automated cleanup across exports, logs, errorlogs and counts (keep last 5 counting days)")

        Toast.makeText(this, getString(R.string.hoofd_toast_deleting_exports), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                // Automatische opkuis van 'exports', 'logs', 'errorlogs' en 'counts' ouder dan de laatste 5 teldagen
                val (deleted, failed) = withContext(Dispatchers.IO) {
                    safHelper.cleanupAllTemporaryFoldersOnExit(keepDaysCount = 5)
                }
                Log.i(TAG, "Exit cleanup across exports, logs, errorlogs and counts completed: $deleted deleted, $failed failed")

                // Voer overige app-cleanup uit (netwerk clients, logs, etc.)
                withContext(Dispatchers.IO) {
                    AppShutdown.shutdownApp(this@HoofdActiviteit)
                }

                Toast.makeText(this@HoofdActiviteit, getString(R.string.hoofd_toast_deleted_exports), Toast.LENGTH_SHORT).show()
                delay(1500.milliseconds)
                Toast.makeText(this@HoofdActiviteit, getString(R.string.hoofd_toast_app_closing), Toast.LENGTH_SHORT).show()
                // Geef de laatste Toast tijd om zichtbaar te zijn voordat de app sluit.
                delay(1000.milliseconds)

            } catch (e: Exception) {
                Log.e(TAG, "Error during shutdown cleanup: ${e.message}", e)
            } finally {
                // Verwijder de app uit recente apps en sluit af
                finishAndRemoveTaskCompat()
            }
        }
    }

    /**
     * Compatibility wrapper voor finishAndRemoveTask()
     * - API 21+: finishAndRemoveTask() verwijdert app uit recente apps
     * - API < 21: Fallback naar finish()
     */
    private fun finishAndRemoveTaskCompat() {
        finishAndRemoveTask()
    }
    
    /**
     * Setup alarm sectie
     */
    private fun setupAlarmSection() {
        val btnToggleAlarm = findViewById<MaterialButton>(R.id.btnToggleAlarm)
        
        // Update alarm status
        updateAlarmStatus()
        
        // Toggle alarm button
        btnToggleAlarm?.setOnClickListener {
            it.isEnabled = false
            val currentlyEnabled = HourlyAlarmManager.isEnabled(this)
            HourlyAlarmManager.setEnabled(this, !currentlyEnabled)
            updateAlarmStatus()
            Toast.makeText(
                this,
                if (!currentlyEnabled) "Alarm ingeschakeld" else "Alarm uitgeschakeld",
                Toast.LENGTH_SHORT
            ).show()
            it.postDelayed({ it.isEnabled = true }, 500)
        }
    }
    
    /**
     * Update de alarm status tekst
     * Toont eenvoudig "Uurlijks alarm is ingeschakeld/uitgeschakeld"
     */
    private fun updateAlarmStatus() {
        val tvAlarmStatus = findViewById<TextView>(R.id.tvAlarmStatus)
        val enabled = HourlyAlarmManager.isEnabled(this)
        
        tvAlarmStatus?.text = if (enabled) {
            getString(R.string.hoofd_alarm_enabled)
        } else {
            getString(R.string.hoofd_alarm_disabled)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Update status wanneer we terugkomen naar dit scherm
        updateAlarmStatus()
        refreshUnfinishedTellingenBadge()
        // Ververs de UI badges direct bij terugkeer
        checkAiStateAndShowBadges()
        updateBsiHeaderVisibility()
        refreshAppUpdateBadge(showPrompt = false)
    }

    private fun updateBsiHeaderVisibility() {
        lifecycleScope.launch {
            try {
                val isAiEnabled = AppDataStore.isAiEnabled(this@HoofdActiviteit)
                val btnBsi = findViewById<View>(R.id.btnBsiHeader)
                btnBsi?.visibility = if (isAiEnabled) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update BSI header visibility: ${e.message}")
            }
        }
    }

    private fun showOnboardingInfoScreen(forceShow: Boolean = false) {
        lifecycleScope.launch {
            try {
                val shown = AppDataStore.isFirstLaunchInfoShown(this@HoofdActiviteit)
                if (forceShow || !shown) {
                    com.yvesds.vt5.core.ui.FloatingInfoDialog.show(
                        context = this@HoofdActiviteit,
                        title = getString(R.string.onboarding_welcome_title),
                        bodyText = getString(R.string.onboarding_welcome_body),
                        headerDrawableResId = R.drawable.kraanvogels_bckgrnd,
                        closeButtonText = getString(R.string.sluiten),
                        showDontShowAgainOption = true,
                        onDontShowAgainChecked = {
                            lifecycleScope.launch {
                                AppDataStore.setFirstLaunchInfoShown(this@HoofdActiviteit, true)
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to show onboarding info screen: ${e.message}")
            }
        }
    }

    private fun refreshUnfinishedTellingenBadge() {
        lifecycleScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val db = VoiceTallyDatabase.getDatabase(this@HoofdActiviteit)
                    val allHeaders = db.tellingDao().getAllHeaders()
                    allHeaders.count { header ->
                        val statusNorm = TellingStatus.normalize(header.status)
                        val isSent = TellingUploadFlags.isSent(this@HoofdActiviteit, header.tellingid, header.onlineid)
                        statusNorm != TellingStatus.GEUPLOAD && statusNorm != TellingStatus.GEARCHIVEERD && !isSent
                    }
                }
                val badge = findViewById<TextView>(R.id.badgeUnfinishedTellingen)
                if (count > 0) {
                    badge?.text = count.toString()
                    badge?.visibility = View.VISIBLE
                } else {
                    badge?.visibility = View.GONE
                }
            } catch (ex: Exception) {
                Log.w(TAG, "refreshUnfinishedTellingenBadge failed: ${ex.message}")
            }
        }
    }

    private fun refreshAppUpdateBadge(showPrompt: Boolean) {
        lifecycleScope.launch {
            val cached = AppUpdateManager.getCachedState(this@HoofdActiviteit)
            findViewById<TextView>(R.id.badgeAppUpdate)?.visibility = if (cached.updateAvailable) View.VISIBLE else View.GONE

            // Dwing bij opstart altijd een grondige verse controle bij GitHub af (allowCache = false)
            val result = AppUpdateManager.refreshFromGithub(this@HoofdActiviteit, allowCache = false)
            result.onSuccess { state ->
                findViewById<TextView>(R.id.badgeAppUpdate)?.visibility = if (state.updateAvailable) View.VISIBLE else View.GONE
                if (showPrompt && state.updateAvailable && !state.latestVersion.isNullOrBlank()) {
                    val prompted = AppDataStore.getLastPromptedUpdateVersion(this@HoofdActiviteit)
                    if (prompted != state.latestVersion) {
                        AppDataStore.setLastPromptedUpdateVersion(this@HoofdActiviteit, state.latestVersion)
                        val dlg = AlertDialog.Builder(this@HoofdActiviteit)
                            .setTitle(R.string.update_prompt_title)
                            .setMessage(getString(R.string.update_prompt_message, state.latestVersion, state.localVersion))
                            .setPositiveButton(R.string.update_prompt_now) { _, _ ->
                                startActivity(Intent(this@HoofdActiviteit, InstellingenScherm::class.java))
                            }
                            .setNegativeButton(R.string.update_prompt_later, null)
                            .show()
                        DialogStyler.apply(dlg)
                    }
                }
            }.onFailure {
                // Netwerkfouten blokkeren het hoofdscherm niet; we laten de laatst gekende badge staan.
            }
        }
    }

    /**
     * Controleert of data verrijkt is en AI getraind is, en toont badges indien nodig.
     */
    private fun checkAiStateAndShowBadges() {
        lifecycleScope.launch {
            val db = VoiceTallyDatabase.getDatabase(this@HoofdActiviteit)
            val modelStore = com.yvesds.vt5.ai.ModelStore(this@HoofdActiviteit)

            isDataEnriched = withContext(Dispatchers.IO) { db.tellingDao().countWeatherArchive() > 0 }
            isAiTrained = withContext(Dispatchers.IO) { 
                val modelDir = modelStore.getModelDir()
                modelDir?.findFile("neural_engine.bin") != null && modelDir.findFile("expert_knowledge.json") != null
            }
        }
    }

    private fun showAiPrerequisiteWarning() {
        val msg = if (!isDataEnriched && !isAiTrained) {
            "De teldag verslagen kunnen nog niet geraadpleegd worden. U moet eerst de gegevens verrijken en de BSI trainen."
        } else if (!isDataEnriched) {
            "De teldag verslagen kunnen nog niet geraadpleegd worden. U moet eerst de gegevens verrijken."
        } else {
            "De teldag verslagen kunnen nog niet geraadpleegd worden. U moet eerst de BSI trainen."
        }

        val dlg = AlertDialog.Builder(this)
            .setTitle("AI Actie Vereist")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
        DialogStyler.apply(dlg)
    }
    

}
