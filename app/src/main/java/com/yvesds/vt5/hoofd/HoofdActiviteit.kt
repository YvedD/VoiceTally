package com.yvesds.vt5.hoofd

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.EditText
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
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.features.metadata.ui.MetadataScherm
import com.yvesds.vt5.features.masterClient.MasterClientPrefs
import com.yvesds.vt5.features.masterClient.MasterClientRuntimeStore
import com.yvesds.vt5.features.masterClient.McQrPayloadCodec
import com.yvesds.vt5.features.masterClient.McRuntimePermissions
import com.yvesds.vt5.features.opstart.ui.InstallatieScherm
import com.yvesds.vt5.features.telling.TellingBeheerScherm
import com.yvesds.vt5.features.telling.TellingEnvelopePersistence
import com.yvesds.vt5.features.telling.TellingScherm
import com.yvesds.vt5.features.telling.TellingSessionManager
import com.yvesds.vt5.features.telling.TellingUploadFlags
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.ui.DatabaseBeheerScherm
import com.yvesds.vt5.core.database.ui.DatabaseBeheerScherm.Companion.PREF_BATCH_IMPORT_ACTIVE
import com.yvesds.vt5.core.ui.DialogStyler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import androidx.activity.result.ActivityResultLauncher
import android.net.Uri
import com.yvesds.vt5.ai.ModelStore
import com.yvesds.vt5.ai.TrainingDataPreparer

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
 * 6. Bewerk tellingen → TellingBeheerScherm
 * 7. Opkuis exports → Verwijder oude bestanden uit exports map
 * 8. Instellingen bewerken
 * 9. Databasebeheer
 */
class HoofdActiviteit : AppCompatActivity() {
    private val TAG = "HoofdActiviteit"

    companion object {
        private const val EXPORTS_KEEP_COUNT = 10
        private const val PREFS_UPLOADS = "vt5_uploads"
        private const val KEY_PENDING_UPLOADS = "pending_uploads"
        private const val KEY_UPLOAD_ON_EXIT = "upload_on_exit"
    }

    private lateinit var safHelper: SaFStorageHelper
    private lateinit var fileLogger: FileLogger
    private var pendingTellingDialogShown = false
    private var pendingExportAfterSaF: Boolean = false
    private var pendingAiUpdateAfterSaF: Boolean = false
    private lateinit var openTreeLauncher: ActivityResultLauncher<Uri?>

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
        if (batchPrefs.getBoolean(PREF_BATCH_IMPORT_ACTIVE, false)) {
            Log.i(TAG, "Active batch import detected, redirecting to DatabaseBeheerScherm")
            startActivity(Intent(this, DatabaseBeheerScherm::class.java))
            finish()
            return
        }

        openTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                try {
                    safHelper.takePersistablePermission(uri)
                    safHelper.saveRootUri(uri)
                } catch (_: Exception) {}
                // If there was a pending export or ai update, run them now
                if (pendingExportAfterSaF) {
                    pendingExportAfterSaF = false
                    lifecycleScope.launch { startTrainingExport() }
                }
                if (pendingAiUpdateAfterSaF) {
                    pendingAiUpdateAfterSaF = false
                    lifecycleScope.launch {
                        try {
                            com.yvesds.vt5.ai.AiManager.requestManualUpdate(this@HoofdActiviteit)
                            Toast.makeText(this@HoofdActiviteit, "AI update gestart (achtergrond)", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.w(TAG, "AI update request failed after SAF selection: ${e.message}")
                            Toast.makeText(this@HoofdActiviteit, "AI update kon niet gestart worden: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        requestStartupPermissionsIfNeeded()

        lifecycleScope.launch {
            val storageMode = InstellingenScherm.getStorageMode(this@HoofdActiviteit)
            fileLogger.info("HoofdActiviteit gestart - VT5 App actief")
            fileLogger.info("Geselecteerde opslagmodus: $storageMode")
        }

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
        val btnOpkuisExports = findViewById<MaterialButton>(R.id.btnOpkuisExports)
        val btnInstellingen = findViewById<MaterialButton>(R.id.btnInstellingen)
        val btnDatabaseBeheer = findViewById<MaterialButton>(R.id.btnDatabaseBeheer)
        val btnAiUpdate = findViewById<MaterialButton>(R.id.btnAiUpdate)
        val btnAiForecast = findViewById<MaterialButton>(R.id.btnAiForecast3Days)

        // Alarm sectie - altijd zichtbaar
        setupAlarmSection()

        btnInstall.setOnClickListener {
            it.isEnabled = false
            startActivity(Intent(this, InstallatieScherm::class.java))
            it.isEnabled = true
        }

        btnVerder.setOnClickListener {
            it.isEnabled = false
            // OPTIMIZATION: Trigger preload during toast display for faster MetadataScherm startup
            Toast.makeText(this, getString(R.string.hoofd_metadata_loading), Toast.LENGTH_SHORT).show()
            
            // Start preloading minimal data in background
            lifecycleScope.launch {
                try {
                    val repo = com.yvesds.vt5.features.serverdata.model.ServerDataRepository(this@HoofdActiviteit)
                    withContext(Dispatchers.IO) {
                        // Trigger background preload (non-blocking)
                        repo.loadMinimalData()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Background preload failed (non-critical): ${e.message}")
                }
            }
            
            startActivity(Intent(this, MetadataScherm::class.java))
            it.isEnabled = true
        }

        btnJoinAsClient.setOnClickListener {
            it.isEnabled = false
            startClientQrJoinFlow()
            it.postDelayed({ it.isEnabled = true }, 600)
        }

        btnAfsluiten.setOnClickListener {
            it.isEnabled = false
            shutdownAndExit()
        }
        
        // Bewerk tellingen knop
        btnBewerkTellingen.setOnClickListener {
            it.isEnabled = false
            startActivity(Intent(this, TellingBeheerScherm::class.java))
            it.isEnabled = true
        }
        
        // Opkuis exports knop
        btnOpkuisExports.setOnClickListener {
            it.isEnabled = false
            showExportsCleanupConfirmation()
            it.isEnabled = true
        }
        
        // Instellingen knop
        btnInstellingen.setOnClickListener {
            it.isEnabled = false
            startActivity(Intent(this, InstellingenScherm::class.java))
            it.isEnabled = true
        }

        // Database Beheer knop
        btnDatabaseBeheer.setOnClickListener {
            it.isEnabled = false
            startActivity(Intent(this, DatabaseBeheerScherm::class.java))
            it.isEnabled = true
        }

        btnAiUpdate.setOnClickListener {
            it.isEnabled = false
            lifecycleScope.launch {
                try {
                    val modelStore = ModelStore(this@HoofdActiviteit)
                    val ok = modelStore.ensureModelDir()
                    if (!ok) {
                        // request SAF root selection and mark pending
                        pendingAiUpdateAfterSaF = true
                        Toast.makeText(this@HoofdActiviteit, "Selecteer VT5 root om AI modelmap aan te maken", Toast.LENGTH_LONG).show()
                        openTreeLauncher.launch(null)
                    } else {
                        com.yvesds.vt5.ai.AiManager.requestManualUpdate(this@HoofdActiviteit)
                        Toast.makeText(this@HoofdActiviteit, "AI optimalisatie gestart (achtergrond). Laat de app open.", Toast.LENGTH_SHORT).show()
                        
                        // Observe the work state to show a final dialog
                        androidx.work.WorkManager.getInstance(this@HoofdActiviteit)
                            .getWorkInfosByTagLiveData("manual_update")
                            .observe(this@HoofdActiviteit) { infoList ->
                                val info = infoList.firstOrNull { it.state.isFinished }
                                if (info != null && info.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                                    AlertDialog.Builder(this@HoofdActiviteit)
                                        .setTitle("AI Optimalisatie Klaar")
                                        .setMessage("Het AI-model is succesvol bijgewerkt en opgeslagen in:\n\nVT5/AI-models/models/training_model.tflite")
                                        .setPositiveButton("OK", null)
                                        .show()
                                }
                            }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "AI update request failed: ${e.message}")
                    Toast.makeText(this@HoofdActiviteit, "AI optimalisatie kon niet gestart worden: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    it.postDelayed({ it.isEnabled = true }, 500)
                }
            }
        }

        btnAiForecast?.setOnClickListener {
            startActivity(Intent(this, com.yvesds.vt5.ai.AiForecastScherm::class.java))
        }

        maybeShowPendingUploadsDialog()
    }

    private fun maybeShowPendingTellingDialog() {
        if (pendingTellingDialogShown) return
        lifecycleScope.launch {
            try {
                val mode = InstellingenScherm.getStorageMode(this@HoofdActiviteit)
                val isHybrid = mode == InstellingenScherm.STORAGE_MODE_ROOM || mode == InstellingenScherm.STORAGE_MODE_PARALLEL
                
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
                        // Sessie is pending als status 'actief' is en er records zijn
                        if (header != null && header.status == "actief") {
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

    /**
     * Start a training CSV export. If SAF VT5 root or training_exports dir is missing,
     * request the user to select the VT5 root and retry after selection.
     */
    private suspend fun startTrainingExport() {
        try {
            val modelStore = ModelStore(this@HoofdActiviteit)
            val exportDir = modelStore.getTrainingExportDir()
            if (exportDir == null) {
                // need to prompt for SAF VT5 root
                pendingExportAfterSaF = true
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HoofdActiviteit, "Selecteer VT5 root om CSV op te slaan", Toast.LENGTH_LONG).show()
                    openTreeLauncher.launch(null)
                }
                return
            }

            // perform export on IO
            withContext(Dispatchers.IO) {
                val exported = TrainingDataPreparer(this@HoofdActiviteit).exportTrainingCsv(exportDir)
                withContext(Dispatchers.Main) {
                    if (exported.isNotBlank()) {
                        Toast.makeText(this@HoofdActiviteit, "CSV geëxporteerd: $exported", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@HoofdActiviteit, "CSV export mislukt", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "startTrainingExport failed: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@HoofdActiviteit, "Export fout: ${e.message}", Toast.LENGTH_LONG).show()
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

    private fun maybeShowPendingUploadsDialog() {
        val prefs = getSharedPreferences(PREFS_UPLOADS, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING_UPLOADS, false)) return

        val dlg = AlertDialog.Builder(this)
            .setTitle(getString(R.string.uploads_pending_title))
            .setMessage(getString(R.string.uploads_pending_message))
            .setPositiveButton(getString(R.string.uploads_pending_now)) { _, _ ->
                prefs.edit { putBoolean(KEY_UPLOAD_ON_EXIT, false) }
                startActivity(Intent(this, TellingBeheerScherm::class.java))
            }
            .setNegativeButton(getString(R.string.uploads_pending_on_exit)) { _, _ ->
                prefs.edit { putBoolean(KEY_UPLOAD_ON_EXIT, true) }
            }
            .setNeutralButton(getString(R.string.annuleer), null)
            .show()

        DialogStyler.apply(dlg)
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
     * Voert een veilige shutdown uit:
     * - Roept AppShutdown.shutdownApp() voor cleanup
     * - Verwijdert de app uit 'recente apps' (finishAndRemoveTask)
     * - Thread-safe en voorkomt geheugenlekken
     */
    private fun shutdownAndExit() {
        val prefs = getSharedPreferences(PREFS_UPLOADS, MODE_PRIVATE)
        val pendingUploads = prefs.getBoolean(KEY_PENDING_UPLOADS, false)
        val uploadOnExit = prefs.getBoolean(KEY_UPLOAD_ON_EXIT, false)
        if (pendingUploads && uploadOnExit) {
            startActivity(Intent(this, TellingBeheerScherm::class.java))
            return
        }

        Log.i(TAG, "User initiated app shutdown")
        
        try {
            // Voer alle cleanup uit (netwerk clients, logs, etc.)
            AppShutdown.shutdownApp(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown cleanup: ${e.message}", e)
        }
        
        // Verwijder de app uit recente apps en sluit af
        finishAndRemoveTaskCompat()
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
        maybeShowPendingTellingDialog()
    }
    
    /**
     * Show confirmation dialog for exports cleanup.
     * First checks how many files would be deleted, then asks for confirmation.
     */
    private fun showExportsCleanupConfirmation() {
        Toast.makeText(this, getString(R.string.hoofd_opkuis_laden), Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                val filesToDelete = withContext(Dispatchers.IO) {
                    safHelper.getExportsCleanupCount(EXPORTS_KEEP_COUNT)
                }
                
                if (filesToDelete == 0) {
                    Toast.makeText(
                        this@HoofdActiviteit,
                        getString(R.string.hoofd_opkuis_geen_bestanden),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                
                // Show confirmation dialog
                val dlg = AlertDialog.Builder(this@HoofdActiviteit)
                    .setTitle(getString(R.string.hoofd_opkuis_bevestig_titel))
                    .setMessage(getString(R.string.hoofd_opkuis_bevestig_msg, filesToDelete))
                    .setPositiveButton(getString(R.string.hoofd_opkuis_ja)) { _, _ ->
                        performExportsCleanup()
                    }
                    .setNegativeButton(getString(R.string.hoofd_opkuis_nee), null)
                    .show()

                DialogStyler.apply(dlg)

            } catch (e: Exception) {
                Log.e(TAG, "Error checking exports cleanup count: ${e.message}", e)
                Toast.makeText(
                    this@HoofdActiviteit,
                    getString(R.string.hoofd_opkuis_fout, e.message ?: "Onbekende fout"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * Perform the actual exports cleanup after user confirmation.
     */
    private fun performExportsCleanup() {
        lifecycleScope.launch {
            try {
                val (deleted, failed) = withContext(Dispatchers.IO) {
                    safHelper.cleanupExportsDir(EXPORTS_KEEP_COUNT)
                }
                
                if (failed > 0) {
                    Toast.makeText(
                        this@HoofdActiviteit,
                        getString(R.string.hoofd_opkuis_succes, deleted) + " ($failed mislukt)",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this@HoofdActiviteit,
                        getString(R.string.hoofd_opkuis_succes, deleted),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during exports cleanup: ${e.message}", e)
                Toast.makeText(
                    this@HoofdActiviteit,
                    getString(R.string.hoofd_opkuis_fout, e.message ?: "Onbekende fout"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
