@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.yvesds.vt5.features.telling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.core.app.HourlyAlarmManager
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.core.ui.ProgressDialogHelper
import com.yvesds.vt5.databinding.SchermTellingBinding
import com.yvesds.vt5.features.alias.AliasRepository
import com.yvesds.vt5.features.alias.AliasManager
import com.yvesds.vt5.features.recent.RecentSpeciesStore
import com.yvesds.vt5.features.recent.SpeciesUsageScoreStore
import com.yvesds.vt5.net.ServerTellingDataItem
import com.yvesds.vt5.net.ServerTellingEnvelope
import com.yvesds.vt5.features.metadata.ui.MetadataScherm
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.jvm.Volatile
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.yvesds.vt5.R
import com.yvesds.vt5.core.ui.DialogStyler
import com.yvesds.vt5.features.speech.Candidate
import com.yvesds.vt5.hoofd.InstellingenScherm
import com.yvesds.vt5.hoofd.HoofdActiviteit
import com.yvesds.vt5.features.network.DataUploader
import com.yvesds.vt5.features.masterClient.protocol.TileSyncItem

/**
 * TellingScherm.kt
 *
 * Preserves existing functionality (ASR parsing, tiles, logs, aliases) and adds:
 * - Optional ViewModel mirroring so UI state survives rotation
 * - Confirmation before Afronden
 * - Dialog text styling helper to force white text for readability in sunlight
 *
 * I did not remove any original logic; I only added mirroring calls to the ViewModel
 * and the dialog styling/confirmation.
 */
class TellingScherm : AppCompatActivity() {

    companion object {
        private const val TAG = "TellingScherm"

        // Preferences keys
        private const val PREFS_NAME = "vt5_prefs"

        private const val MAX_LOG_ROWS = 600
        
        // Auto-dismiss delay for success dialog (ms)
        private const val SUCCESS_DIALOG_DELAY_MS = 1000L
        
        // Intent extra key for hourly alarm trigger
        const val EXTRA_SHOW_HUIDIGE_STAND = "SHOW_HUIDIGE_STAND"
        const val EXTRA_RESTORE_PENDING_TELLING = "RESTORE_PENDING_TELLING"
        const val EXTRA_JOIN_AS_CLIENT = "JOIN_AS_CLIENT"
    }

    // UI & adapters
    private lateinit var binding: SchermTellingBinding
    private lateinit var tilesAdapter: SpeciesTileAdapter
    private lateinit var partialsAdapter: SpeechLogAdapter
    private lateinit var finalsAdapter: SpeechLogAdapter

    // ViewModel (optional mirror for rotation persistence) - ensure TellingViewModel.kt is present
    private lateinit var viewModel: TellingViewModel

    // Helper classes for refactored code
    private lateinit var logManager: TellingLogManager
    private lateinit var dialogHelper: TellingDialogHelper
    private lateinit var backupManager: TellingBackupManager
    private lateinit var dataProcessor: TellingDataProcessor
    private lateinit var uiManager: TellingUiManager
    private lateinit var afrondHandler: TellingAfrondHandler
    private lateinit var tegelBeheer: TegelBeheer
    private lateinit var speechHandler: TellingSpeechHandler
    private lateinit var matchResultHandler: TellingMatchResultHandler
    private lateinit var speciesManager: TellingSpeciesManager
    private lateinit var annotationHandler: TellingAnnotationHandler
    private lateinit var initializer: TellingInitializer
    private lateinit var alarmHandler: TellingAlarmHandler

    // Continuous envelope persistence - saves full envelope after each observation
    private lateinit var envelopePersistence: TellingEnvelopePersistence

    // Legacy helpers
    private lateinit var aliasEditor: AliasEditor
    private val safHelper by lazy { SaFStorageHelper(this) }

    // Prefs
    private lateinit var prefs: android.content.SharedPreferences

    // Partial UI debounce + only keep last partial
    private var lastPartialUiUpdateMs: Long = 0L
    private val PARTIAL_UI_DEBOUNCE_MS = 200L

    // Local pendingRecords (legacy) — we mirror to ViewModel for persistence but keep this for compatibility
    private val pendingRecords = mutableListOf<ServerTellingDataItem>()

    // Track backup files created per-record (DocumentFile or internal path strings)
    private val pendingBackupDocs = mutableListOf<DocumentFile>()
    private val pendingBackupInternalPaths = mutableListOf<String>()
    
    // Flag to show HuidigeStandScherm after tiles are loaded (triggered by hourly alarm)
    @Volatile
    private var pendingShowHuidigeStand = false

    // Restore guard to avoid applying restore multiple times
    private var restoredFromSavedEnvelope = false

    // ─── Master-client ────────────────────────────────────────────────────────
    private var mcPairingManager: com.yvesds.vt5.features.masterClient.PairingManager? = null
    private var mcEventProcessor: com.yvesds.vt5.features.masterClient.MasterEventProcessor? = null
    private var mcMasterServer: com.yvesds.vt5.features.masterClient.MasterServer? = null
    private var mcDiscoveryService: com.yvesds.vt5.features.masterClient.DiscoveryService? = null
    private var mcEventQueue: com.yvesds.vt5.features.masterClient.ClientEventQueue? = null
    private var mcClientConnector: com.yvesds.vt5.features.masterClient.ClientConnector? = null

    private val mcClientEventIdByRecordId = mutableMapOf<String, String>()
    private val mcClientRecordIdByEvent = mutableMapOf<String, String>()

    // BroadcastReceiver: listen for alias-reload events from AliasManager
    private val aliasReloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val success = intent?.getBooleanExtra(AliasRepository.EXTRA_RELOAD_SUCCESS, true) ?: true
            if (!success) {
                Log.w(TAG, "Received alias reload broadcast but success flag=false")
                return
            }

            lifecycleScope.launch(Dispatchers.Default) {
                try {
                    val tiles = tegelBeheer.getTiles().map { it.soortId }.toSet()
                    val mc = initializer.buildMatchContext(tiles)
                    speechHandler.updateCachedMatchContext(mc)
                } catch (ex: Exception) {
                    Log.w(TAG, "Failed rebuilding cachedMatchContext after alias reload: ${ex.message}", ex)
                }

                // Reload speech recognition aliases
                speechHandler.loadAliases()
            }
        }
    }

    // Data models
    data class SoortRow(
        val soortId: String, 
        val naam: String, 
        val countMain: Int = 0,
        val countReturn: Int = 0
    ) {
        // Backwards compatible total count property
        val count: Int get() = countMain + countReturn
    }
    data class SpeechLogRow(val ts: Long, val tekst: String, val bron: String)

    // Import records launcher
    private val importRecordsLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            importRecordsFromUri(uri)
        }
    }

    private var pendingJoinAsClient = false
    private var pendingOpenSoortSelectieOnPair = false

    private data class ClientPairingDialogRefs(
        val etIp: EditText,
        val etPin: EditText,
        val tvStatus: android.widget.TextView
    )

    private var activeClientPairingDialog: ClientPairingDialogRefs? = null

    private enum class QrScanMode {
        WIFI,
        PAIRING
    }

    private var pendingQrScanMode: QrScanMode = QrScanMode.WIFI

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        val raw = result.contents ?: return@registerForActivityResult

        when (pendingQrScanMode) {
            QrScanMode.WIFI -> {
                if (!raw.startsWith("WIFI:")) {
                    Toast.makeText(this, getString(R.string.mc_pairing_wifi_qr_invalid), Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }
                pendingQrScanMode = QrScanMode.PAIRING
                Toast.makeText(this, getString(R.string.mc_pairing_wifi_qr_ok), Toast.LENGTH_SHORT).show()
                launchQrScan(getString(R.string.mc_pairing_scan_pairing_qr))
            }
            QrScanMode.PAIRING -> {
                val payload = com.yvesds.vt5.features.masterClient.McQrPayloadCodec.decode(raw)
                if (payload == null) {
                    Toast.makeText(this, getString(R.string.mc_pairing_qr_invalid), Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }
                com.yvesds.vt5.features.masterClient.MasterClientPrefs.setMasterIp(this, payload.ip)
                com.yvesds.vt5.features.masterClient.MasterClientPrefs.setMasterPort(this, payload.port)
                if (payload.ssid.isNotBlank()) {
                    com.yvesds.vt5.features.masterClient.MasterClientPrefs.setHotspotSsid(this, payload.ssid)
                    com.yvesds.vt5.features.masterClient.MasterClientPrefs.setHotspotPassword(this, payload.pass)
                    com.yvesds.vt5.features.masterClient.MasterClientPrefs.setHotspotSecurity(this, payload.sec)
                    Toast.makeText(this, getString(R.string.mc_pairing_hotspot_from_qr), Toast.LENGTH_SHORT).show()
                }
                activeClientPairingDialog?.let { refs ->
                    refs.etIp.setText(payload.ip)
                    refs.etPin.setText(payload.pin)
                    refs.tvStatus.text = getString(R.string.mc_pairing_qr_filled)
                }
            }
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingQrScanMode = QrScanMode.WIFI
            launchQrScan(getString(R.string.mc_pairing_scan_wifi_qr))
        } else {
            Toast.makeText(this, getString(R.string.mc_pairing_qr_invalid), Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchQrScan(prompt: String) {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setBeepEnabled(false)
            setOrientationLocked(true)
            setPrompt(prompt)
        }
        qrScanLauncher.launch(options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SchermTellingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        pendingJoinAsClient = intent.getBooleanExtra(EXTRA_JOIN_AS_CLIENT, false)
        if (pendingJoinAsClient) {
            pendingOpenSoortSelectieOnPair = true
        }

        // Check if this is triggered by the hourly alarm (from HourlyAlarmManager)
        // Store the flag to show HuidigeStandScherm after tiles are loaded
        if (intent.getBooleanExtra(EXTRA_SHOW_HUIDIGE_STAND, false)) {
            pendingShowHuidigeStand = true
        }
        
        // Initialize helper classes BEFORE registering launchers
        // (partial initialization for those that need it)
        backupManager = TellingBackupManager(this, safHelper)
        
        // Initialize TegelBeheer early
        tegelBeheer = TegelBeheer(object : TegelUi {
            override fun submitTiles(list: List<SoortTile>) {
                val rows = list.map { SoortRow(it.soortId, it.naam, it.countMain, it.countReturn) }
                tilesAdapter.submitList(rows)
                if (::viewModel.isInitialized) {
                    viewModel.setTiles(rows)
                }
            }
            override fun onTileCountUpdated(soortId: String, newCount: Int) {}
        })
        
        // Initialize handlers that need launcher registration
        speciesManager = TellingSpeciesManager(this, this, safHelper, backupManager, tegelBeheer, PREFS_NAME)
        annotationHandler = TellingAnnotationHandler(this, backupManager, PREFS_NAME)
        
        // Register launchers before super.onCreate (required by ActivityResultContracts)
        speciesManager.registerLaunchers()
        annotationHandler.registerLauncher()
        
        // Initialize remaining helpers
        initializeHelpers()

        // Initialize legacy helpers
        aliasEditor = AliasEditor(this, safHelper)

        // Setup UI using UiManager
        setupUiWithManager()

        // Initialize ViewModel (if you have TellingViewModel in project)
        try {
            viewModel = ViewModelProvider(this).get(TellingViewModel::class.java)
            // Observe VM lists and keep adapters in sync (this ensures rotation preserves UI)
            viewModel.tiles.observe(this) { tiles ->
                // update adapter (observer will call submitList callback if configured)
                uiManager.updateTiles(tiles)

                // Rebuild cachedMatchContext asynchronously on Default to avoid blocking parse path
                lifecycleScope.launch(Dispatchers.Default) {
                    try {
                        val tilesIds = tiles.map { it.soortId }.toSet()
                        val mc = initializer.buildMatchContext(tilesIds)
                        speechHandler.updateCachedMatchContext(mc)
                    } catch (ex: Exception) {
                        Log.w(TAG, "Failed rebuilding cachedMatchContext after tiles change: ${ex.message}", ex)
                    }
                }
            }
            viewModel.partials.observe(this) { list ->
                uiManager.updatePartials(list)
            }
            viewModel.finals.observe(this) { list ->
                uiManager.updateFinals(list)
            }
        } catch (ex: Exception) {
            Log.w(TAG, "TellingViewModel not available or failed to init: ${ex.message}")
        }

        // Register receiver to keep cached context and ASR in sync when AliasManager reloads index
        try {
            val filter = IntentFilter(AliasRepository.ACTION_ALIAS_RELOAD_COMPLETED)
            // minSdk is Android 13+, so use the API-33 overload and explicitly mark NOT_EXPORTED
            registerReceiver(aliasReloadReceiver, filter, RECEIVER_NOT_EXPORTED)
        } catch (ex: Exception) {
            Log.w(TAG, "Failed to register aliasReloadReceiver: ${ex.message}", ex)
        }

        // Preload tiles (if preselected) then initialize ASR
        initializer.loadPreselection()

        // Ask user if a pending, non-uploaded telling should be restored
        lifecycleScope.launch {
            if (intent?.getBooleanExtra(EXTRA_RESTORE_PENDING_TELLING, false) == true) {
                restorePendingTellingIfAvailable()
            }
        }
    }

    /**
     * Initialize all helper classes for refactored code.
     */
    private fun initializeHelpers() {
        logManager = TellingLogManager(MAX_LOG_ROWS)
        dialogHelper = TellingDialogHelper(this, this, safHelper)
        // backupManager already initialized before super.onCreate()
        dataProcessor = TellingDataProcessor()
        uiManager = TellingUiManager(this, binding)
        
        // Initialize envelope persistence for continuous backup
        envelopePersistence = TellingEnvelopePersistence(this, safHelper)
        
        // Initialize afrondHandler with envelope persistence for cleanup on success
        afrondHandler = TellingAfrondHandler(this, backupManager, dataProcessor, envelopePersistence)
        
        // tegelBeheer already initialized before super.onCreate()

        // Initialize new helper classes
        speechHandler = TellingSpeechHandler(this, this, safHelper, prefs)
        matchResultHandler = TellingMatchResultHandler(this)
        // speciesManager and annotationHandler already initialized before super.onCreate()
        initializer = TellingInitializer(this)
        
        // Initialize alarm handler for hourly alarm at minute 59
        alarmHandler = TellingAlarmHandler(this, lifecycleScope)
        alarmHandler.onAlarmTriggered = {
            showHuidigeStandScherm()
        }

        // Setup callbacks for new helpers
        setupHelperCallbacks()
    }

    /**
     * Setup callbacks for helper classes.
     */
    private fun setupHelperCallbacks() {
        // Speech handler callbacks
        speechHandler.onHypothesesReceived = { hypotheses, partials ->
            handleSpeechHypotheses(hypotheses, partials)
        }
        speechHandler.onRawResult = { rawText ->
            lifecycleScope.launch(Dispatchers.Main) {
                addLog(rawText, "raw")
            }
        }
        speechHandler.onListeningStarted = {
            addLog("Luisteren...", "systeem")
        }

        // Match result handler callbacks
        matchResultHandler.onAutoAccept = { speciesId, displayName, amount ->
            recordSpeciesCount(speciesId, displayName, amount)
        }
        matchResultHandler.onAutoAcceptWithPopup = { speciesId, displayName, amount, isInTiles ->
            if (isInTiles) {
                recordSpeciesCount(speciesId, displayName, amount)
            } else {
                showAddSpeciesConfirmationDialog(speciesId, displayName, amount)
            }
        }
        matchResultHandler.onMultiMatch = { matches ->
            matches.forEach { match ->
                val sid = match.candidate.speciesId
                val cnt = match.amount
                val present = tegelBeheer.findIndexBySoortId(sid) >= 0
                if (present) {
                    recordSpeciesCount(sid, match.candidate.displayName, cnt)
                } else {
                    showAddSpeciesConfirmationDialog(sid, match.candidate.displayName, cnt)
                }
            }
        }
        matchResultHandler.onSuggestionList = { candidates, count ->
            showSuggestionBottomSheet(candidates, count)
        }
        matchResultHandler.onNoMatch = { hypothesis ->
            val now = System.currentTimeMillis()
            if (now - lastPartialUiUpdateMs >= PARTIAL_UI_DEBOUNCE_MS) {
                upsertPartialLog(hypothesis)
                lastPartialUiUpdateMs = now
            } else {
                upsertPartialLog(hypothesis)
            }
        }

        // Species manager callbacks
        speciesManager.onLogMessage = { msg, source -> addLog(msg, source) }
        speciesManager.onTilesUpdated = { updateSelectedSpeciesMap() }
        speciesManager.onRecordCollected = { item ->
            synchronized(pendingRecords) { pendingRecords.add(item) }
            mcEventQueue?.enqueue(
                clientId = com.yvesds.vt5.features.masterClient.MasterClientPrefs.getClientId(applicationContext),
                sessionToken = com.yvesds.vt5.features.masterClient.MasterClientPrefs.getSessionToken(applicationContext),
                soortid = item.soortid,
                aantal = item.aantal.toIntOrNull() ?: 0,
                aantalterug = item.aantalterug.toIntOrNull() ?: 0,
                tijdstip = item.tijdstip.toLongOrNull() ?: (System.currentTimeMillis() / 1000L),
                geslacht = item.geslacht,
                leeftijd = item.leeftijd,
                kleed = item.kleed,
                opmerkingen = item.opmerkingen
            )
            if (::viewModel.isInitialized) viewModel.addPendingRecord(item)

            if (mcMasterServer == null && mcClientConnector != null) {
                val clientEventId = mcClientConnector?.queueObservation(
                    soortid = item.soortid,
                    aantal = item.aantal.toIntOrNull() ?: 0,
                    aantalterug = item.aantalterug.toIntOrNull() ?: 0,
                    tijdstip = item.tijdstip.toLongOrNull() ?: (System.currentTimeMillis() / 1000L),
                    geslacht = item.geslacht,
                    leeftijd = item.leeftijd,
                    kleed = item.kleed,
                    opmerkingen = item.opmerkingen
                )
                if (!clientEventId.isNullOrBlank()) {
                    mcClientEventIdByRecordId[item.idLocal] = clientEventId
                }
            }

            // Save full envelope after each observation to prevent data loss on crash.
            // Runs async on IO dispatcher; failures are logged but don't interrupt the UI flow.
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val records = synchronized(pendingRecords) { pendingRecords.toList() }
                    envelopePersistence.saveEnvelopeWithRecords(records)
                } catch (ex: Exception) {
                    Log.w(TAG, "Envelope persistence failed after record collected: ${ex.message}", ex)
                }
            }
        }

        // Annotation handler callbacks
        annotationHandler.onAnnotationApplied = { summary -> addLog(summary, "annotatie") }
        annotationHandler.onGetFinalsList = {
            if (::viewModel.isInitialized) viewModel.finals.value.orEmpty() else finalsAdapter.currentList
        }
        annotationHandler.onGetPendingRecords = { synchronized(pendingRecords) { pendingRecords.toList() } }
        annotationHandler.onUpdatePendingRecord = { idx, updated ->
            synchronized(pendingRecords) {
                if (idx in pendingRecords.indices) {
                    pendingRecords[idx] = updated
                    if (::viewModel.isInitialized) viewModel.setPendingRecords(pendingRecords.toList())
                    
                    // Recalculate tile counts from all pending records
                    tegelBeheer.recalculateCountsFromRecords(pendingRecords.toList())
                }
            }

            if (mcMasterServer == null && mcClientConnector != null) {
                val eventId = mcClientEventIdByRecordId[updated.idLocal]
                if (!eventId.isNullOrBlank()) {
                    mcClientConnector?.queueObservationUpdate(
                        clientEventId = eventId,
                        soortid = updated.soortid,
                        aantal = updated.aantal.toIntOrNull() ?: 0,
                        aantalterug = updated.aantalterug.toIntOrNull() ?: 0,
                        tijdstip = updated.tijdstip.toLongOrNull() ?: (System.currentTimeMillis() / 1000L),
                        geslacht = updated.geslacht,
                        leeftijd = updated.leeftijd,
                        kleed = updated.kleed,
                        opmerkingen = updated.opmerkingen
                    )
                }
            }

            // Save full envelope after annotation update to preserve changes.
            // Runs async on IO dispatcher; failures are logged but don't interrupt the UI flow.
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val records = synchronized(pendingRecords) { pendingRecords.toList() }
                    envelopePersistence.saveEnvelopeWithRecords(records)
                } catch (ex: Exception) {
                    Log.w(TAG, "Envelope persistence failed after record update: ${ex.message}", ex)
                }
            }
        }
        annotationHandler.onGetTelpostId = {
            try {
                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                val savedEnvelopeJson = prefs.getString("pref_saved_envelope_json", null)
                if (savedEnvelopeJson != null) {
                    val envelopeList = VT5App.json.decodeFromString<List<ServerTellingEnvelope>>(savedEnvelopeJson)
                    envelopeList.firstOrNull()?.telpostid
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get telpostid: ${e.message}")
                null
            }
        }

        // Initializer callbacks
        initializer.onTilesLoaded = { tiles ->
            tegelBeheer.setTiles(tiles)
            
            // Check if we need to show HuidigeStandScherm (triggered by hourly alarm)
            if (pendingShowHuidigeStand) {
                pendingShowHuidigeStand = false
                showHuidigeStandScherm()
            }
        }
        initializer.onMatchContextBuilt = { context ->
            speechHandler.updateCachedMatchContext(context)
        }
        initializer.onPermissionsGranted = {
            initializeSpeechRecognition()
            speechHandler.initializeVolumeKeyHandler()
        }
        initializer.onLogMessage = { msg, source -> addLog(msg, source) }
    }

    /**
     * Setup UI using the UiManager helper.
     */
    private fun setupUiWithManager() {
        // Setup RecyclerViews
        uiManager.setupPartialsRecyclerView()
        uiManager.setupFinalsRecyclerView()
        uiManager.setupSpeciesTilesRecyclerView()

        // Store adapter references for backward compatibility
        partialsAdapter = uiManager.getCurrentPartials().let { SpeechLogAdapter().apply { submitList(it) } }
        finalsAdapter = uiManager.getCurrentFinals().let { SpeechLogAdapter().apply { submitList(it) } }

        // IMPORTANT: do NOT create a second tilesAdapter here.
        // UiManager already created + attached the SpeciesTileAdapter to the RecyclerView.
        // We reuse that adapter so 'Totalen' sees the same up-to-date list.
        tilesAdapter = uiManager.getTilesAdapter()

        // Setup callbacks for UI manager
        uiManager.onPartialTapCallback = { pos, row -> handlePartialTap(pos, row) }
        uiManager.onFinalTapCallback = { pos, row -> handleFinalTap(pos, row) }
        uiManager.onTileTapCallback = { pos -> showNumberInputDialog(pos) }
        uiManager.onAddSoortenCallback = { openSoortSelectieForAdd() }
        uiManager.onAfrondenCallback = { handleAfrondenWithConfirmation() }
        uiManager.onSaveCloseCallback = { tiles -> handleSaveClose(tiles) }
        uiManager.onOpenSettingsCallback = { openInstellingenScherm() }
        uiManager.onToggleAlarmCallback = { toggleHourlyAlarm() }

        // Setup buttons
        uiManager.setupButtons()

        // Initialiseer master-client statusbalk en knoppen
        setupMasterClientUi()

        if (pendingJoinAsClient) {
            pendingJoinAsClient = false
            if (mcClientConnector == null) {
                setupClientMode()
            }
            showClientPairingDialog()
        }
    }

    /**
     * Initialiseer de master-client UI.
     *
     * - "Add clients" knop (btnAddClients) is ALTIJD aanwezig in de header en start de
     *   master-server on-demand bij eerste klik (ongeacht de modus in InstellingenScherm).
     *   Zo kan de eigenaar van het toestel op elk moment tijdens een lopende telling clients
     *   toevoegen, zonder de telling te herstarten.
     *
     * - Client-modus (ingesteld in InstellingenScherm): auto-verbinding bij opstarten.
     *
     * - Solo-modus (default): geen server gestart, app werkt exact als voorheen.
     */
    private fun setupMasterClientUi() {
        // "Add clients" knop: altijd zichtbaar in de header, start server on-demand
        val btnAddClients = binding.root.findViewById<android.widget.ImageButton?>(R.id.btnAddClients)
        btnAddClients?.setOnClickListener { handleAddClientsPressed() }

        // Client-modus: auto-verbinding bij opstarten (toestel is geconfigureerd als client)
        val mode = com.yvesds.vt5.features.masterClient.MasterClientPrefs.getMode(this)
        if (mode == com.yvesds.vt5.features.masterClient.MasterClientPrefs.MODE_CLIENT) {
            setupClientMode()
        }
    }

    /**
     * Behandel de klik op de "Add clients" knop.
     * - Als de server nog niet draait: start de server en toon de pairing-dialog.
     * - Als de server al draait: toon meteen de pairing-dialog (om meer clients toe te voegen).
     */
    private fun handleAddClientsPressed() {
        val mode = com.yvesds.vt5.features.masterClient.MasterClientPrefs.getMode(this)
        when {
            mcMasterServer != null -> {
                // Server draait al: pairing-dialog tonen voor extra clients
                showMasterPairingDialog()
            }
            mode == com.yvesds.vt5.features.masterClient.MasterClientPrefs.MODE_CLIENT || mcClientConnector != null -> {
                // In client-modus: toon de client-pairing dialog
                if (mcClientConnector == null) {
                    setupClientMode()
                }
                showClientPairingDialog()
            }
            else -> {
                // Solo-modus: eerst bevestigen, daarna role-chooser
                val dlg = AlertDialog.Builder(this)
                    .setTitle(R.string.mc_add_clients_confirm_title)
                    .setMessage(R.string.mc_add_clients_confirm_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        showMasterClientRoleChooser()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .create()
                DialogStyler.apply(dlg)
                dlg.show()
            }
        }
    }

    private fun showMasterClientRoleChooser() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.mc_dialog_choose_role_title)
            .setMessage(R.string.mc_dialog_choose_role_message)
            .setPositiveButton(R.string.mc_dialog_choose_master) { _, _ ->
                startMasterServerOnDemand { showMasterPairingDialog() }
            }
            .setNegativeButton(R.string.mc_dialog_choose_client) { _, _ ->
                setupClientMode()
                showClientPairingDialog()
            }
            .setNeutralButton(R.string.mc_dialog_choose_cancel, null)
            .create()
        com.yvesds.vt5.core.ui.DialogStyler.apply(dialog)
        dialog.show()
    }

    // ─── Master: server starten on-demand ────────────────────────────────────

    /**
     * Start de master-server on-demand (bij eerste klik op "Add clients").
     * Nadat de server gestart is, wordt [onStarted] aangeroepen (op de Main-thread).
     *
     * Werking:
     *  1. PairingManager + MasterEventProcessor aanmaken
     *  2. MasterEventProcessor.onObservationReceived koppelen aan de live telling
     *  3. MasterServer starten + NSD-advertentie starten
     *  4. Live-update van de statusbalk bij verbindingswijzigingen
     */
    private fun startMasterServerOnDemand(onStarted: (() -> Unit)? = null) {
        if (mcMasterServer != null) {
            onStarted?.invoke()
            return
        }
        try {
            com.yvesds.vt5.features.masterClient.MasterClientPrefs.setMode(
                this,
                com.yvesds.vt5.features.masterClient.MasterClientPrefs.MODE_MASTER
            )
            val pm = com.yvesds.vt5.features.masterClient.PairingManager()
            val ep = com.yvesds.vt5.features.masterClient.MasterEventProcessor()

            // Koppel client-observaties aan de live telling via dezelfde codepath als
            // eigen spraakwaarnemingen (tile-update + log + record-opslag).
            ep.onObservationReceived = { clientId, clientEventId, isUpdate, soortId, amount, aantalterug, tijdstip, geslacht, leeftijd, kleed, opmerkingen ->
                withContext(Dispatchers.Main) {
                    handleLiveClientEvent(
                        clientId = clientId,
                        clientEventId = clientEventId,
                        isUpdate = isUpdate,
                        soortId = soortId,
                        amount = amount,
                        aantalterug = aantalterug,
                        tijdstip = tijdstip,
                        geslacht = geslacht,
                        leeftijd = leeftijd,
                        kleed = kleed,
                        opmerkingen = opmerkingen
                    )
                }
            }

            // Koppel bulk-export aan de live telling
            ep.onExportReceived = { items ->
                withContext(Dispatchers.Main) {
                    applyImportedRecords(items)
                }
            }

            val server = com.yvesds.vt5.features.masterClient.MasterServer(
                context        = applicationContext,
                pairingManager = pm,
                eventProcessor = ep
            )
            server.onPairingRequest = { _, clientName ->
                requestClientApproval(clientName)
            }
            server.onTilesSnapshot = {
                buildTileSyncItems()
            }
            server.start()

            // Wanneer een client actief de telling verlaat: toon een toast op de master
            server.onClientLeft = { clientName, reason ->
                runOnUiThread {
                    val msg = if (reason.isBlank()) "$clientName heeft de telling verlaten"
                              else "$clientName heeft de telling verlaten: $reason"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }

            // NSD-advertentie zodat clients dit toestel automatisch kunnen vinden
            val discovery = com.yvesds.vt5.features.masterClient.DiscoveryService(applicationContext)
            discovery.startAdvertising(server.port)

            mcPairingManager   = pm
            mcEventProcessor   = ep
            mcMasterServer     = server
            mcDiscoveryService = discovery

            updateMasterStatusBar(server.connectedClients.value.size)

            // Live-update van de statusbalk (zichtbaar zodra eerste client verbindt)
            lifecycleScope.launch {
                server.connectedClients.collect { clients ->
                    runOnUiThread { updateMasterStatusBar(clients.size) }
                }
            }

            Log.i(TAG, "MasterServer gestart on-demand op poort ${server.port}")
            onStarted?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "startMasterServerOnDemand fout: ${e.message}", e)
            Toast.makeText(this, getString(R.string.mc_error_server_start, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun requestClientApproval(clientName: String): Boolean =
        suspendCancellableCoroutine { cont ->
            runOnUiThread {
                val dlg = AlertDialog.Builder(this)
                    .setTitle(R.string.mc_pairing_permission_title)
                    .setMessage(getString(R.string.mc_pairing_permission_message, clientName))
                    .setPositiveButton(R.string.mc_pairing_permission_allow) { _, _ ->
                        if (cont.isActive) cont.resume(true)
                    }
                    .setNegativeButton(R.string.mc_pairing_permission_deny) { _, _ ->
                        if (cont.isActive) cont.resume(false)
                    }
                    .setOnCancelListener {
                        if (cont.isActive) cont.resume(false)
                    }
                    .create()
                DialogStyler.apply(dlg)
                dlg.show()
            }
        }

    /**
     * Verwerk een client-waarneming in de lopende telling.
     * Roept dezelfde codepath aan als een eigen spraakwaarneming:
     * tile-telling bijwerken, log-invoer toevoegen, record opslaan.
     *
     * Moet aangeroepen worden op de Main-thread.
     */
    private fun recordClientObservation(
        soortId: String,
        amount: Int,
        aantalterug: Int,
        geslacht: String,
        leeftijd: String,
        kleed: String,
        opmerkingen: String
    ) {
        val naam = tegelBeheer.findNaamBySoortId(soortId) ?: soortId
        val prefix = getString(R.string.mc_log_client_prefix)
        val logText = "$prefix $naam -> +$amount" +
            (if (aantalterug > 0) " / ↩ +$aantalterug" else "")
        addFinalLog(logText)
        lifecycleScope.launch {
            // Bijwerken tegeltelling (zichtbaar voor de master)
            speciesManager.updateSoortCountInternal(soortId, amount)
            // Record opslaan (dezelfde codepath als eigen spraakwaarnemingen)
            speciesManager.collectFinalAsRecord(soortId, amount, aantalterug)
        }
    }

    private fun handleLiveClientEvent(
        clientId: String,
        clientEventId: String,
        isUpdate: Boolean,
        soortId: String,
        amount: Int,
        aantalterug: Int,
        tijdstip: Long,
        geslacht: String,
        leeftijd: String,
        kleed: String,
        opmerkingen: String
    ) {
        val key = "$clientId::$clientEventId"
        val existingIdLocal = mcClientRecordIdByEvent[key]
        if (isUpdate && !existingIdLocal.isNullOrBlank()) {
            applyClientRecordUpdate(existingIdLocal, soortId, amount, aantalterug, tijdstip, geslacht, leeftijd, kleed, opmerkingen)
            return
        }

        val tellingId = prefs.getString("pref_telling_id", null) ?: return
        val idLocal = DataUploader.getAndIncrementRecordId(this, tellingId)
        val nowStamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", resources.configuration.locales[0])
            .format(java.util.Date())
        val item = ServerTellingDataItem(
            idLocal = idLocal,
            tellingid = tellingId,
            soortid = soortId,
            aantal = amount.toString(),
            richting = "",
            aantalterug = aantalterug.toString(),
            richtingterug = "",
            sightingdirection = "",
            lokaal = "0",
            aantal_plus = "0",
            aantalterug_plus = "0",
            lokaal_plus = "0",
            markeren = "0",
            markerenlokaal = "0",
            geslacht = geslacht,
            leeftijd = leeftijd,
            kleed = kleed,
            opmerkingen = opmerkingen,
            trektype = "",
            teltype = "",
            location = "",
            height = "",
            tijdstip = tijdstip.toString(),
            groupid = idLocal,
            uploadtijdstip = nowStamp,
            totaalaantal = (amount + aantalterug).toString()
        )

        synchronized(pendingRecords) { pendingRecords.add(item) }
        if (::viewModel.isInitialized) viewModel.setPendingRecords(synchronized(pendingRecords) { pendingRecords.toList() })

        mcClientRecordIdByEvent[key] = idLocal

        if (amount > 0) {
            speciesManager.updateSoortCountInternal(soortId, amount)
        }
        if (aantalterug > 0) {
            tegelBeheer.verhoogSoortAantalReturn(soortId, aantalterug)
        }

        val naam = tegelBeheer.findNaamBySoortId(soortId) ?: soortId
        val prefix = getString(R.string.mc_log_client_prefix)
        val logText = "$prefix $naam -> +$amount" + (if (aantalterug > 0) " / ↩ +$aantalterug" else "")
        addFinalLog(logText)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                backupManager.writeRecordBackupSaf(tellingId, item)
            } catch (_: Exception) {
            }
            try {
                val records = synchronized(pendingRecords) { pendingRecords.toList() }
                envelopePersistence.saveEnvelopeWithRecords(records)
            } catch (ex: Exception) {
                Log.w(TAG, "Envelope persistence failed after live client event: ${ex.message}", ex)
            }
        }
    }

    private fun applyClientRecordUpdate(
        idLocal: String,
        soortId: String,
        amount: Int,
        aantalterug: Int,
        tijdstip: Long,
        geslacht: String,
        leeftijd: String,
        kleed: String,
        opmerkingen: String
    ) {
        val idx = synchronized(pendingRecords) { pendingRecords.indexOfFirst { it.idLocal == idLocal } }
        if (idx < 0) return

        val existing = synchronized(pendingRecords) { pendingRecords[idx] }
        val updated = existing.copy(
            soortid = soortId,
            aantal = amount.toString(),
            aantalterug = aantalterug.toString(),
            geslacht = geslacht,
            leeftijd = leeftijd,
            kleed = kleed,
            opmerkingen = opmerkingen,
            tijdstip = tijdstip.toString(),
            totaalaantal = (amount + aantalterug).toString()
        )

        synchronized(pendingRecords) {
            pendingRecords[idx] = updated
        }
        if (::viewModel.isInitialized) viewModel.setPendingRecords(synchronized(pendingRecords) { pendingRecords.toList() })

        tegelBeheer.recalculateCountsFromRecords(synchronized(pendingRecords) { pendingRecords.toList() })

        val naam = tegelBeheer.findNaamBySoortId(soortId) ?: soortId
        val prefix = getString(R.string.mc_log_client_prefix)
        val oldMain = existing.aantal.toIntOrNull() ?: 0
        val oldRet = existing.aantalterug.toIntOrNull() ?: 0
        val changedCounts = oldMain != amount || oldRet != aantalterug
        val changedAnn = existing.geslacht != geslacht || existing.leeftijd != leeftijd || existing.kleed != kleed || existing.opmerkingen != opmerkingen
        if (changedCounts || changedAnn) {
            val logText = if (changedCounts) {
                "$prefix $naam bijgewerkt -> $oldMain/$oldRet → $amount/$aantalterug"
            } else {
                "$prefix $naam annotatie bijgewerkt"
            }
            addFinalLog(logText)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tellingId = prefs.getString("pref_telling_id", null)
                if (!tellingId.isNullOrBlank()) {
                    backupManager.writeRecordBackupSaf(tellingId, updated)
                }
            } catch (_: Exception) {
            }
            try {
                val records = synchronized(pendingRecords) { pendingRecords.toList() }
                envelopePersistence.saveEnvelopeWithRecords(records)
            } catch (ex: Exception) {
                Log.w(TAG, "Envelope persistence failed after client update: ${ex.message}", ex)
            }
        }
    }

    /* ---------- UI Callback Handlers ---------- */

    /**
     * Handle tap on partial log entry - show alias dialog.
     */
    private fun handlePartialTap(@Suppress("UNUSED_PARAMETER") _pos: Int, row: SpeechLogRow) {
        when (row.bron) {
            "partial", "raw" -> {
                val (nameOnly, cnt) = parseNameAndCountFromDisplay(row.tekst)
                ensureAvailableSpeciesFlat { flat ->
                    dialogHelper.showAddAliasDialog(nameOnly, cnt, flat, 
                        onAliasAdded = { speciesId, canonical, count ->
                            addLog("Alias toegevoegd: '$nameOnly' → $canonical", "alias")
                            Toast.makeText(this, getString(R.string.telling_alias_saved_buffer), Toast.LENGTH_SHORT).show()
                            
                            lifecycleScope.launch {
                                Toast.makeText(this@TellingScherm, getString(R.string.telling_index_updating), Toast.LENGTH_SHORT).show()
                                val ok = withContext(Dispatchers.IO) {
                                    try {
                                        AliasManager.forceRebuildCborNow(this@TellingScherm, safHelper)
                                        true
                                    } catch (ex: Exception) {
                                        Log.w(TAG, "forceRebuildCborNow failed: ${ex.message}", ex)
                                        false
                                    }
                                }
                                if (ok) {
                                    Toast.makeText(this@TellingScherm, getString(R.string.telling_alias_saved_index_updated), Toast.LENGTH_SHORT).show()
                                    speciesManager.refreshAliasesRuntimeAsync(speechHandler) { context ->
                                        speechHandler.updateCachedMatchContext(context)
                                    }
                                } else {
                                    Toast.makeText(this@TellingScherm, getString(R.string.telling_alias_saved_index_later), Toast.LENGTH_LONG).show()
                                }
                            }
                            
                            if (count > 0) {
                                lifecycleScope.launch {
                                    // Add species to tiles (creates tile if not present, or increases count)
                                    speciesManager.addSpeciesToTilesIfNeeded(speciesId, canonical, count)
                                    // Add to finals log (green text) and collect record for server upload
                                    addFinalLog("$canonical -> +$count")
                                    speciesManager.collectFinalAsRecord(speciesId, count)
                                }
                            }
                        },
                        fragmentManager = supportFragmentManager
                    )
                }
            }
        }
    }

    /**
     * Handle tap on final log entry - open annotation screen.
     */
    private fun handleFinalTap(pos: Int, row: SpeechLogRow) {
        if (row.bron == "final") {
            annotationHandler.launchAnnotatieScherm(row.tekst, row.ts, pos)
        }
    }

    /**
     * Handle Afronden button with confirmation dialog.
     * Shows a popup to allow editing begintijd, eindtijd, and opmerkingen.
     */
    private fun handleAfrondenWithConfirmation() {
        // Get current envelope data to show as defaults in dialog
        val envelopeData = getCurrentEnvelopeData()
        
        val dialog = AfrondConfirmDialog.newInstance(
            begintijdEpoch = envelopeData.begintijd,
            eindtijdEpoch = envelopeData.eindtijd,
            opmerkingen = envelopeData.opmerkingen
        )
        
        dialog.listener = object : AfrondConfirmDialog.AfrondConfirmListener {
            override fun onAfrondConfirmed(begintijdEpoch: String, eindtijdEpoch: String, opmerkingen: String) {
                lifecycleScope.launch {
                    val progressDialog = ProgressDialogHelper.show(this@TellingScherm, "Bezig met afronden upload...")
                    try { 
                        handleAfronden(
                            MetadataUpdates(
                                begintijd = begintijdEpoch,
                                eindtijd = eindtijdEpoch,
                                opmerkingen = opmerkingen
                            )
                        )
                    } finally { 
                        progressDialog.dismiss() 
                    }
                }
            }
            
            override fun onAfrondCancelled() {
                // User cancelled - do nothing
            }
        }
        
        dialog.show(supportFragmentManager, "afrondConfirm")
    }
    
    /**
     * Data class to hold current envelope metadata for the confirm dialog.
     */
    private data class EnvelopeData(
        val begintijd: String,
        val eindtijd: String,
        val opmerkingen: String
    )
    
    /**
     * Get current envelope data from saved preferences.
     */
    private fun getCurrentEnvelopeData(): EnvelopeData {
        return try {
            val savedEnvelopeJson = prefs.getString("pref_saved_envelope_json", null)
            if (savedEnvelopeJson != null) {
                val envelopeList = VT5App.json.decodeFromString(
                    ListSerializer(ServerTellingEnvelope.serializer()),
                    savedEnvelopeJson
                )

                if (envelopeList.isEmpty()) {
                    val nowEpoch = (System.currentTimeMillis() / 1000L).toString()
                    return EnvelopeData(nowEpoch, nowEpoch, "")
                }

                val envelope = envelopeList[0]
                EnvelopeData(
                    begintijd = envelope.begintijd,
                    eindtijd = envelope.eindtijd.ifBlank { (System.currentTimeMillis() / 1000L).toString() },
                    opmerkingen = envelope.opmerkingen
                )
            } else {
                // Return defaults with current time
                val nowEpoch = (System.currentTimeMillis() / 1000L).toString()
                return EnvelopeData(nowEpoch, nowEpoch, "")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get envelope data: ${e.message}")
            val nowEpoch = (System.currentTimeMillis() / 1000L).toString()
            EnvelopeData(nowEpoch, nowEpoch, "")
        }
    }

    /**
     * Handle Save/Close button - show current status screen.
     */
    private fun handleSaveClose(tiles: List<SoortRow>) {
        val ids = ArrayList<String>(tiles.size)
        val names = ArrayList<String>(tiles.size)
        val countsMain = ArrayList<String>(tiles.size)
        val countsReturn = ArrayList<String>(tiles.size)
        for (row in tiles) {
            ids.add(row.soortId)
            names.add(row.naam)
            countsMain.add(row.countMain.toString())
            countsReturn.add(row.countReturn.toString())
        }

        // Get the telling begintijd (epoch seconds) for correct season determination
        val begintijdEpoch = getTellingBegintijdEpoch()

        val intent = Intent(this, HuidigeStandScherm::class.java).apply {
            putStringArrayListExtra(HuidigeStandScherm.EXTRA_SOORT_IDS, ids)
            putStringArrayListExtra(HuidigeStandScherm.EXTRA_SOORT_NAMEN, names)
            putStringArrayListExtra(HuidigeStandScherm.EXTRA_SOORT_AANTALLEN_MAIN, countsMain)
            putStringArrayListExtra(HuidigeStandScherm.EXTRA_SOORT_AANTALLEN_RETURN, countsReturn)
            if (begintijdEpoch > 0) {
                putExtra(HuidigeStandScherm.EXTRA_TELLING_BEGINTIJD_EPOCH, begintijdEpoch)
            }
        }
        startActivity(intent)
    }
    
    /**
     * Get the telling begintijd (start time) as epoch seconds from the saved envelope.
     * This is needed to correctly determine the season for column mapping in HuidigeStandScherm.
     * 
     * @return The begintijd as epoch seconds, or -1L if not available
     */
    private fun getTellingBegintijdEpoch(): Long {
        return try {
            val savedEnvelopeJson = prefs.getString("pref_saved_envelope_json", null)
            if (savedEnvelopeJson.isNullOrBlank()) return -1L
            
            val envelopeList = VT5App.json.decodeFromString(
                ListSerializer(ServerTellingEnvelope.serializer()),
                savedEnvelopeJson
            )
            
            if (envelopeList.isEmpty()) return -1L

            // The begintijd field is a string containing epoch seconds
            envelopeList[0].begintijd.toLongOrNull() ?: -1L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get telling begintijd: ${e.message}")
            -1L
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Check if this is triggered by the hourly alarm
        if (intent.getBooleanExtra(EXTRA_SHOW_HUIDIGE_STAND, false)) {
            showHuidigeStandScherm()
        }
        updateAlarmToggleUi()
    }
    
    /**
     * Show the HuidigeStandScherm with the current tile data.
     * Called by hourly alarm (via onCreate or onNewIntent).
     */
    private fun showHuidigeStandScherm() {
        if (::tegelBeheer.isInitialized) {
            val tiles = tegelBeheer.getTiles()
            val rows = tiles.map { tile ->
                SoortRow(
                    soortId = tile.soortId,
                    naam = tile.naam,
                    countMain = tile.countMain,
                    countReturn = tile.countReturn
                )
            }
            handleSaveClose(rows)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (::initializer.isInitialized) {
            initializer.onPermissionResult(requestCode, grantResults)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Start alarm monitoring when the screen is visible
        if (::alarmHandler.isInitialized) {
            alarmHandler.startMonitoring()
        }

        updateAlarmToggleUi()
        ensurePendingTellingPromptOnRestore()

        // Refresh log text size/color from settings
        val partialsSize = InstellingenScherm.getPartialsTextSizeSp(this)
        val finalsSize = InstellingenScherm.getFinalsTextSizeSp(this)
        val partialsColor = InstellingenScherm.getPartialsTextColor(this)
        val finalsColor = InstellingenScherm.getFinalsTextColor(this)
        partialsAdapter.updatePartialsTextSize(partialsSize)
        finalsAdapter.updateFinalsTextSize(finalsSize)
        partialsAdapter.updatePartialsTextColor(partialsColor)
        finalsAdapter.updateFinalsTextColor(finalsColor)

        val tilesSize = InstellingenScherm.getLettergroottTegelsSp(this)
        tilesAdapter.updateTextSize(tilesSize)
        tilesAdapter.notifyDataSetChanged()

        uiManager.updatePartials(uiManager.getCurrentPartials())
        uiManager.updateFinals(uiManager.getCurrentFinals())
    }

    private fun ensurePendingTellingPromptOnRestore() {
        if (intent?.getBooleanExtra(EXTRA_RESTORE_PENDING_TELLING, false) == true) return
        if (restoredFromSavedEnvelope) return
        if (pendingRecords.isNotEmpty()) return

        lifecycleScope.launch {
            try {
                if (!envelopePersistence.hasSavedEnvelope()) return@launch
                val savedEnvelope = envelopePersistence.loadSavedEnvelope() ?: return@launch
                if (savedEnvelope.data.isEmpty()) return@launch
                if (TellingUploadFlags.isSent(this@TellingScherm, savedEnvelope.tellingid, savedEnvelope.onlineid)) return@launch

                // Redirect to HoofdActiviteit so the resume prompt can be shown there.
                val intent = Intent(this@TellingScherm, HoofdActiviteit::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Log.w(TAG, "ensurePendingTellingPromptOnRestore failed: ${e.message}", e)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop alarm monitoring when the screen is not visible
        if (::alarmHandler.isInitialized) {
            alarmHandler.stopMonitoring()
        }

        // Always persist the latest envelope snapshot as a safety net
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val records = synchronized(pendingRecords) { pendingRecords.toList() }
                if (records.isNotEmpty()) {
                    envelopePersistence.saveEnvelopeWithRecords(records)
                }
            } catch (ex: Exception) {
                Log.w(TAG, "Envelope persistence failed onPause: ${ex.message}", ex)
            }
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(aliasReloadReceiver)
        } catch (_: Exception) {}
        try {
            if (::speechHandler.isInitialized) {
                speechHandler.cleanup()
            }
        } catch (_: Exception) {}
        try {
            if (::alarmHandler.isInitialized) {
                alarmHandler.cleanup()
            }
        } catch (_: Exception) {}
        // Master-client cleanup
        try { mcMasterServer?.stop() }        catch (_: Exception) {}
        try { mcClientConnector?.stop() }     catch (_: Exception) {}
        try { mcDiscoveryService?.stop() }    catch (_: Exception) {}
        super.onDestroy()
    }

    /* ---------- UI setup (now delegated to UiManager) ---------- */

    // Ensure availableSpeciesFlat is loaded; onReady is invoked on Main with the flat list.
    private fun ensureAvailableSpeciesFlat(onReady: (List<String>) -> Unit) {
        speciesManager.ensureAvailableSpeciesFlat(onReady)
    }

    /* ---------- TILE click dialog (adds to existing count) ---------- */
    private fun showNumberInputDialog(position: Int) {
        val current = tilesAdapter.currentList
        dialogHelper.showNumberInputDialog(position, current) { soortId, mainDelta, returnDelta ->
            lifecycleScope.launch {
                // Use tegelBeheer to update tile counts
                val naam = tegelBeheer.findNaamBySoortId(soortId) ?: "Unknown"
                if (mainDelta > 0) {
                    tegelBeheer.verhoogSoortAantal(soortId, mainDelta)
                    // Behave like an ASR final for main direction
                    addFinalLog("$naam -> +$mainDelta")
                    speciesManager.collectFinalAsRecord(soortId, mainDelta)
                }
                if (returnDelta > 0) {
                    tegelBeheer.verhoogSoortAantalReturn(soortId, returnDelta)
                    // Log and collect return as record (amountReturn)
                    addFinalLog("$naam (tegenrichting) -> +$returnDelta")
                    speciesManager.collectFinalAsRecord(soortId, 0, returnDelta)
                }

                RecentSpeciesStore.recordUse(this@TellingScherm, soortId, maxEntries = InstellingenScherm.getMaxFavorieten(this@TellingScherm).let { if (it == InstellingenScherm.MAX_FAVORIETEN_ALL) SpeciesUsageScoreStore.MAX_ALL_CAP else it })
            }
        }
    }





    private fun initializeSpeechRecognition() {
        try {
            speechHandler.initialize()
            updateSelectedSpeciesMap()
            addLog("Spraakherkenning geactiveerd - protocol: 'Soortnaam Aantal'", "systeem")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing speech recognition", e)
            Toast.makeText(this, getString(R.string.telling_speech_init_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Handle speech recognition hypotheses and process match results.
     */
    private fun handleSpeechHypotheses(hypotheses: List<Pair<String, Float>>, partials: List<String>) {
        lifecycleScope.launch(Dispatchers.Default) {
             try {
                 val matchContext = speechHandler.getCachedMatchContext() ?: run {
                     val tiles = tegelBeheer.getTiles().map { it.soortId }.toSet()
                     val mc = initializer.buildMatchContext(tiles)
                     speechHandler.updateCachedMatchContext(mc)
                     mc
                 }

                 val result = speechHandler.parseSpokenWithHypotheses(hypotheses, matchContext, partials, asrWeight = 0.4)

                 withContext(Dispatchers.Main) {
                     try {
                         matchResultHandler.handleMatchResult(result)
                     } catch (ex: Exception) {
                         Log.w(TAG, "Hypotheses handling (UI) failed: ${ex.message}", ex)
                     }
                 }
             } catch (ex: Exception) {
                 Log.w(TAG, "Hypotheses handling (background) failed: ${ex.message}", ex)
             }
         }
    }



    /**
     * Record a species count (add final log, update count, collect record).
     */
    private fun recordSpeciesCount(speciesId: String, displayName: String, count: Int) {
        addFinalLog("$displayName -> +$count")
        lifecycleScope.launch {
            speciesManager.updateSoortCountInternal(speciesId, count)
            speciesManager.collectFinalAsRecord(speciesId, count)
        }
        RecentSpeciesStore.recordUse(this, speciesId, maxEntries = InstellingenScherm.getMaxFavorieten(this).let { if (it == InstellingenScherm.MAX_FAVORIETEN_ALL) SpeciesUsageScoreStore.MAX_ALL_CAP else it })
    }

    /**
     * Show confirmation dialog for adding a new species to tiles.
     */
    private fun showAddSpeciesConfirmationDialog(speciesId: String, displayName: String, count: Int) {
        val msg = "Soort \"$displayName\" herkend met aantal $count.\n\nToevoegen?"
        val dlg = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_species))
            .setMessage(msg)
            .setPositiveButton("Ja") { _, _ ->
                lifecycleScope.launch {
                    speciesManager.addSpeciesToTiles(speciesId, displayName, count)
                    addFinalLog("$displayName -> +$count")
                    speciesManager.collectFinalAsRecord(speciesId, count)
                }
            }
            .setNegativeButton("Nee", null)
            .show()
        DialogStyler.apply(dlg)
    }

    /* ---------- Helper log functions (delegated to logManager) ---------- */

    // Primary addLog implementation: delegate to logManager then update UI
    private fun addLog(msgIn: String, bron: String) {
        val newList = logManager.addLog(msgIn, bron)
        if (newList != null) {
            updateLogsUi(newList, bron)
        }
    }

    // Parse a text using logManager
    private fun parseNameAndCountFromDisplay(text: String): Pair<String, Int> {
        return logManager.parseNameAndCountFromDisplay(text)
    }

    // Update logs UI after changes
    // Routing: "final" → finals adapter, everything else → partials adapter (matching 4e5359e behavior)
    private fun updateLogsUi(newList: List<SpeechLogRow>, bron: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (::viewModel.isInitialized) {
                // UPDATE VIEWMODEL ONLY — observers will update adapters once.
                if (bron == "final") {
                    viewModel.setFinals(newList)
                    // remove 'partial' rows from partials (keep non-partial logs)
                    val preserved = logManager.getPartials().filter { it.bron != "partial" }
                    viewModel.setPartials(preserved)
                } else {
                    // raw, partial, systeem all go to partials
                    viewModel.setPartials(newList)
                }
            } else {
                // Fallback: no ViewModel — update adapter via uiManager
                if (bron == "final") {
                    uiManager.updateFinals(newList)
                    // clear partials from UI
                    val preserved = logManager.getPartials().filter { it.bron != "partial" }
                    uiManager.updatePartials(preserved)
                } else {
                    // raw, partial, systeem all go to partials
                    uiManager.updatePartials(newList)
                }
            }
        }
    }

    // Insert or replace the single partial log entry (bron = "partial")
    // Now ignores blanks and formats partial display to include count when present.
    // Delegate to logManager for upsertPartialLog
    private fun upsertPartialLog(text: String) {
        val newList = logManager.upsertPartialLog(text)
        lifecycleScope.launch(Dispatchers.Main) {
            if (::viewModel.isInitialized) {
                viewModel.setPartials(newList)
            } else {
                uiManager.updatePartials(newList)
            }
        }
    }

    // Delegate to logManager for addFinalLog
    private fun addFinalLog(text: String) {
        val updatedFinals = logManager.addFinalLog(text)
        val updatedPartials = logManager.getPartials().filter { it.bron != "partial" }
        
        lifecycleScope.launch(Dispatchers.Main) {
            if (::viewModel.isInitialized) {
                viewModel.setFinals(updatedFinals)
                viewModel.setPartials(updatedPartials)
            } else {
                uiManager.updateFinals(updatedFinals)
                uiManager.updatePartials(updatedPartials)
            }
        }
    }

    /* ---------- Suggestion / Add / Tiles helpers (unchanged flows) ---------- */
    private fun showSuggestionBottomSheet(candidates: List<Candidate>, count: Int) {
        val items = candidates.map { "${it.displayName} (score: ${"%.2f".format(it.score)})" }.toTypedArray()

        val dlgList = AlertDialog.Builder(this)
            .setTitle("Kies soort")
            .setItems(items) { _, which ->
                val chosen = candidates[which]
                if (chosen.isInTiles) {
                    addFinalLog("${chosen.displayName} -> +$count")
                    lifecycleScope.launch {
                        speciesManager.updateSoortCountInternal(chosen.speciesId, count)
                        speciesManager.collectFinalAsRecord(chosen.speciesId, count)
                    }
                } else {
                    val msg = "Soort \"${chosen.displayName}\" toevoegen en $count noteren?"
                    val dlg = AlertDialog.Builder(this@TellingScherm)
                        .setTitle("Soort toevoegen?")
                        .setMessage(msg)
                        .setPositiveButton("Ja") { _, _ ->
                            lifecycleScope.launch {
                                speciesManager.addSpeciesToTiles(chosen.speciesId, chosen.displayName, count)
                                addFinalLog("${chosen.displayName} -> +$count")
                                speciesManager.collectFinalAsRecord(chosen.speciesId, count)
                            }
                        }
                        .setNegativeButton("Nee", null)
                        .show()
                    DialogStyler.apply(dlg)
                }
                RecentSpeciesStore.recordUse(
                    this,
                    chosen.speciesId,
                    maxEntries = InstellingenScherm.getMaxFavorieten(this).let { if (it == InstellingenScherm.MAX_FAVORIETEN_ALL) SpeciesUsageScoreStore.MAX_ALL_CAP else it }
                )

            }
            .setNegativeButton("Annuleer", null)
            .show()

        // Ensure the list dialog itself is also styled.
        DialogStyler.apply(dlgList)
    }





    // Afronden: build counts_save envelope with saved metadata + pendingRecords, POST and handle response
    /**
     * Handle Afronden (finalize and upload) using afrondHandler.
     * 
     * @param metadataUpdates Optional updates to begintijd, eindtijd, and opmerkingen
     */
    private suspend fun handleAfronden(metadataUpdates: MetadataUpdates? = null) {
        val result = afrondHandler.handleAfronden(
            pendingRecords = synchronized(pendingRecords) { ArrayList(pendingRecords) },
            pendingBackupDocs = pendingBackupDocs,
            pendingBackupInternalPaths = pendingBackupInternalPaths,
            metadataUpdates = metadataUpdates
        )

        withContext(Dispatchers.Main) {
            when (result) {
                is TellingAfrondHandler.AfrondResult.Success -> {
                    // Cleanup local state
                    synchronized(pendingRecords) { pendingRecords.clear() }
                    pendingBackupDocs.clear()
                    pendingBackupInternalPaths.clear()
                    if (::viewModel.isInitialized) viewModel.clearPendingRecords()

                    // Store the eindtijd for potential vervolgtelling
                    // Use ifBlank to handle empty strings and fallback to system time
                    val eindtijdForVervolg = metadataUpdates?.eindtijd?.ifBlank { null }
                        ?: (System.currentTimeMillis() / 1000L).toString()

                    // Show auto-dismissing success toast (like other popups in the app)
                    showAutoDissmissSuccessAndThenVervolg(eindtijdForVervolg)
                }
                is TellingAfrondHandler.AfrondResult.Failure -> {
                    // Show failure dialog
                    val dlg = AlertDialog.Builder(this@TellingScherm)
                        .setTitle(result.title)
                        .setMessage(result.message)
                        .setPositiveButton("OK", null)
                        .show()
                    DialogStyler.apply(dlg)
                }
            }
        }
    }
    
    /**
     * Show auto-dismissing success popup for 2 seconds, then show vervolgtelling dialog.
     * 
     * @param eindtijdEpoch The eindtijd of the finished telling (epoch seconds string)
     */
    private fun showAutoDissmissSuccessAndThenVervolg(eindtijdEpoch: String) {
        // Create and show auto-dismissing success dialog
        val successDialog = AlertDialog.Builder(this@TellingScherm)
            .setTitle(getString(R.string.dialog_finish_success))
            .setMessage(getString(R.string.afrond_upload_success))
            .setCancelable(false)
            .create()
        
        successDialog.show()
        DialogStyler.apply(successDialog)

        // Auto-dismiss after SUCCESS_DIALOG_DELAY_MS and show vervolgtelling dialog
        lifecycleScope.launch {
            kotlinx.coroutines.delay(SUCCESS_DIALOG_DELAY_MS)
            if (successDialog.isShowing) {
                successDialog.dismiss()
            }
            showVervolgtellingDialog(eindtijdEpoch)
        }
    }
    
    /**
     * Show dialog asking if user wants to create a follow-up telling.
     *
     * Als de gebruiker NEEN kiest EN er zijn verbonden clients, stuurt de master
     * eerst een [MasterHandoverMessage] zodat één van hen de masterfunctie kan overnemen.
     * Alle pending records zijn op dit moment al ge-upload (afronden is geslaagd),
     * zodat de overdracht veilig kan plaatsvinden.
     *
     * @param eindtijdEpoch The eindtijd to use as begintijd for the follow-up telling
     */
    private fun showVervolgtellingDialog(eindtijdEpoch: String) {
        val dlg = AlertDialog.Builder(this@TellingScherm)
            .setTitle(getString(R.string.afrond_vervolgtelling_titel))
            .setMessage(getString(R.string.afrond_vervolgtelling_msg))
            .setPositiveButton("OK") { _, _ ->
                // Gebruiker start zelf een vervolgtelling: geen handover nodig
                val intent = Intent(this@TellingScherm, MetadataScherm::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                intent.putExtra(MetadataScherm.EXTRA_VERVOLG_BEGINTIJD_EPOCH, eindtijdEpoch)
                startActivity(intent)
                finish()
            }
            .setNegativeButton(getString(R.string.dlg_cancel)) { _, _ ->
                // Master verlaat de telpost zonder vervolgtelling.
                // Alle pending records zijn al ge-upload (afronden was succesvol).
                // Stuur handover-bericht als er clients verbonden zijn.
                val server = mcMasterServer
                if (server != null && server.connectedClients.value.isNotEmpty()) {
                    server.broadcastMasterHandover(
                        eindtijdEpoch = eindtijdEpoch,
                        reason        = "master verlaat telpost zonder vervolgtelling"
                    )
                }
                // Navigeer naar HoofdActiviteit
                val intent = Intent(this@TellingScherm, com.yvesds.vt5.hoofd.HoofdActiviteit::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                finish()
            }
            .setCancelable(false)
            .show()
        DialogStyler.apply(dlg)
    }

    // Write pretty envelope JSON to SAF as "<timestamp>_count_<onlineid>.json"




    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (::speechHandler.isInitialized && speechHandler.handleKeyDown(keyCode, event)) {
            updateSelectedSpeciesMap()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun updateSelectedSpeciesMap() {
        val speciesMap = tegelBeheer.buildSelectedSpeciesMap()
        if (::speechHandler.isInitialized) {
            speechHandler.buildSelectedSpeciesMap(speciesMap)
        }
    }

    // Launch soort selectie
    private fun openSoortSelectieForAdd() {
        speciesManager.launchSpeciesSelection()
    }

    private suspend fun restorePendingTellingIfAvailable() {
        if (restoredFromSavedEnvelope) return
        try {
            val savedEnvelope = envelopePersistence.loadSavedEnvelope() ?: return
            val savedRecords = savedEnvelope.data

            if (savedRecords.isEmpty()) return

            // Skip restore if this telling was already sent
            if (TellingUploadFlags.isSent(this, savedEnvelope.tellingid, savedEnvelope.onlineid)) {
                Log.i(TAG, "Pending envelope already marked as sent; skipping restore")
                return
            }

            // Restore tiles based on saved records
            val snapshot = withContext(Dispatchers.IO) {
                com.yvesds.vt5.features.serverdata.model.ServerDataCache.getOrLoad(this@TellingScherm)
            }
            val speciesById = snapshot.speciesById
            val countsBySoort = savedRecords.groupBy { it.soortid }

            val restoredTiles = countsBySoort.mapNotNull { (soortId, items) ->
                val soortNaam = speciesById[soortId]?.soortnaam ?: return@mapNotNull null
                val countMain = items.sumOf { it.aantal.toIntOrNull() ?: 0 }
                val countReturn = items.sumOf { it.aantalterug.toIntOrNull() ?: 0 }
                SoortTile(soortId, soortNaam, countMain, countReturn)
            }.sortedBy { it.naam.lowercase() }

            if (restoredTiles.isNotEmpty()) {
                tegelBeheer.setTiles(restoredTiles)
                updateSelectedSpeciesMap()
            }

            // Restore pending records and reflect to ViewModel
            synchronized(pendingRecords) {
                pendingRecords.clear()
                pendingRecords.addAll(savedRecords)
            }
            if (::viewModel.isInitialized) {
                viewModel.setPendingRecords(savedRecords)
            }

            // Restore finals log from records (partials remain empty)
            val restoredFinals = savedRecords.map { rec ->
                val naam = speciesById[rec.soortid]?.soortnaam ?: rec.soortid
                val main = rec.aantal.toIntOrNull() ?: 0
                val ret = rec.aantalterug.toIntOrNull() ?: 0
                val display = buildString {
                    append(naam)
                    append(" -> +")
                    append(main)
                    if (ret > 0) {
                        append(" (tegenrichting) -> +")
                        append(ret)
                    }
                }
                SpeechLogRow(System.currentTimeMillis() / 1000L, display, "final")
            }
            logManager.setFinals(restoredFinals)
            logManager.setPartials(emptyList())
            uiManager.updateFinals(restoredFinals)
            uiManager.updatePartials(emptyList())
            if (::viewModel.isInitialized) {
                viewModel.setFinals(restoredFinals)
                viewModel.setPartials(emptyList())
            }

            restoredFromSavedEnvelope = true
            Toast.makeText(this, getString(R.string.pending_telling_restored), Toast.LENGTH_LONG).show()
        } catch ( e: Exception) {
            Log.w(TAG, "restorePendingTellingIfAvailable failed: ${e.message}", e)
        }
    }

    // Import records from URI
    private suspend fun importRecordsFromUri(uri: Uri) {
        val imported = com.yvesds.vt5.features.masterClient.McShareHelper.importRecordsFromUri(this, uri)
        if (imported.isEmpty()) {
            Toast.makeText(this, getString(R.string.mc_import_failed), Toast.LENGTH_SHORT).show()
            return
        }

        val tellingId = prefs.getString("pref_telling_id", null)
        if (tellingId.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.mc_error_no_telling), Toast.LENGTH_SHORT).show()
            return
        }

        val normalized = mutableListOf<ServerTellingDataItem>()
        withContext(Dispatchers.IO) {
            for (item in imported) {
                val idLocal = DataUploader.getAndIncrementRecordId(this@TellingScherm, tellingId)
                val adjusted = item.copy(
                    idLocal = idLocal,
                    tellingid = tellingId,
                    groupid = idLocal
                )
                normalized.add(adjusted)
                try {
                    backupManager.writeRecordBackupSaf(tellingId, adjusted)
                } catch (_: Exception) {
                }
            }
        }

        applyImportedRecords(normalized)

        Toast.makeText(
            this,
            getString(R.string.mc_import_success, normalized.size),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun applyImportedRecords(items: List<ServerTellingDataItem>) {
        if (items.isEmpty()) return

        for (item in items) {
            val amount = item.aantal.toIntOrNull() ?: 0
            val returnAmount = item.aantalterug.toIntOrNull() ?: 0
            val naam = tegelBeheer.findNaamBySoortId(item.soortid) ?: item.soortid
            val prefix = getString(R.string.mc_log_client_prefix)

            if (amount > 0) {
                speciesManager.updateSoortCountInternal(item.soortid, amount)
            }
            if (returnAmount > 0) {
                tegelBeheer.verhoogSoortAantalReturn(item.soortid, returnAmount)
            }
            if (amount > 0 || returnAmount > 0) {
                val logText = "$prefix $naam -> +$amount" +
                    (if (returnAmount > 0) " / ↩ +$returnAmount" else "")
                addFinalLog(logText)
            }

            synchronized(pendingRecords) { pendingRecords.add(item) }
        }

        if (::viewModel.isInitialized) {
            viewModel.setPendingRecords(synchronized(pendingRecords) { pendingRecords.toList() })
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val records = synchronized(pendingRecords) { pendingRecords.toList() }
                envelopePersistence.saveEnvelopeWithRecords(records)
            } catch (ex: Exception) {
                Log.w(TAG, "Envelope persistence failed after import: ${ex.message}", ex)
            }
        }
    }

    private fun launchOfflineImport() {
        importRecordsLauncher.launch(arrayOf("application/json", "text/plain"))
    }

    private fun openInstellingenScherm() {
        startActivity(Intent(this, InstellingenScherm::class.java))
    }

    private fun toggleHourlyAlarm() {
        val currentlyEnabled = HourlyAlarmManager.isEnabled(this)
        HourlyAlarmManager.setEnabled(this, !currentlyEnabled)
        updateAlarmToggleUi()
    }

    private fun updateAlarmToggleUi() {
        if (!::binding.isInitialized) return
        val enabled = HourlyAlarmManager.isEnabled(this)
        val color = if (enabled) Color.parseColor("#00C853") else Color.parseColor("#D50000")
        binding.btnToggleAlarm.setColorFilter(color)
        binding.btnToggleAlarm.contentDescription = getString(
            if (enabled) R.string.hoofd_alarm_enabled else R.string.hoofd_alarm_disabled
        )
    }

    private fun updateMasterStatusBar(clientCount: Int) {
        val statusBar = binding.root.findViewById<android.view.View?>(R.id.mcStatusBar) ?: return
        val tvStatus  = binding.root.findViewById<android.widget.TextView?>(R.id.tvMcStatus) ?: return
        val tvPin     = binding.root.findViewById<android.widget.TextView?>(R.id.tvMcPin) ?: return
        val btnAction = binding.root.findViewById<android.widget.Button?>(R.id.btnMcAction) ?: return

        if (mcMasterServer == null) return

        statusBar.visibility = android.view.View.VISIBLE
        tvStatus.text = if (clientCount > 0) {
            getString(R.string.mc_status_bar_master, clientCount)
        } else {
            getString(R.string.mc_status_bar_master_no_clients)
        }

        val pm = mcPairingManager
        val pin = pm?.getCurrentPin() ?: pm?.generatePin()
        tvPin.text = if (!pin.isNullOrBlank()) getString(R.string.mc_status_bar_pin, pin) else ""

        btnAction.text = getString(R.string.mc_btn_clients)
        btnAction.setOnClickListener { showMasterPairingDialog() }
    }

    private fun setupClientMode() {
        val statusBar = binding.root.findViewById<android.view.View?>(R.id.mcStatusBar) ?: return
        val tvStatus  = binding.root.findViewById<android.widget.TextView?>(R.id.tvMcStatus) ?: return
        val btnAction = binding.root.findViewById<android.widget.Button?>(R.id.btnMcAction) ?: return

        statusBar.visibility = android.view.View.VISIBLE
        tvStatus.text  = getString(R.string.mc_status_bar_client_connecting)
        btnAction.text = getString(R.string.mc_btn_connect_to_master)
        btnAction.setOnClickListener { showClientPairingDialog() }

        startClientConnector()
    }

    private fun startClientConnector() {
        try {
            val queue     = com.yvesds.vt5.features.masterClient.ClientEventQueue()
            val connector = com.yvesds.vt5.features.masterClient.ClientConnector(
                context    = applicationContext,
                eventQueue = queue
            )
            mcEventQueue      = queue
            mcClientConnector = connector

            connector.onSessionEnded = { _ ->
                runOnUiThread {
                    tilesSyncedFromMaster = false
                    Toast.makeText(this, getString(R.string.mc_status_session_ended), Toast.LENGTH_LONG).show()
                    binding.root.findViewById<android.view.View?>(R.id.mcStatusBar)
                        ?.visibility = android.view.View.GONE
                }
            }

            connector.onMasterHandover = { eindtijd, masterName, _ ->
                runOnUiThread {
                    binding.root.findViewById<android.view.View?>(R.id.mcStatusBar)
                        ?.visibility = android.view.View.GONE
                    showMasterHandoverDialog(eindtijd, masterName)
                }
            }

            connector.onTileSyncReceived = { tiles ->
                runOnUiThread {
                    applyTileSync(tiles)
                }
            }

            lifecycleScope.launch {
                connector.state.collect { state ->
                    runOnUiThread { updateClientStatusBar(state) }
                }
            }

            val discovery = com.yvesds.vt5.features.masterClient.DiscoveryService(applicationContext)
            discovery.startDiscovery()
            mcDiscoveryService = discovery

            Log.i(TAG, "ClientConnector klaar; wacht op pairing-input")
        } catch (e: Exception) {
            Log.e(TAG, "startClientConnector fout: ${e.message}", e)
        }
    }

    private fun updateClientStatusBar(state: com.yvesds.vt5.features.masterClient.ClientConnector.State) {
        val tvStatus  = binding.root.findViewById<android.widget.TextView?>(R.id.tvMcStatus) ?: return
        val btnAction = binding.root.findViewById<android.widget.Button?>(R.id.btnMcAction) ?: return
        when (state) {
            com.yvesds.vt5.features.masterClient.ClientConnector.State.PAIRED -> {
                tvStatus.text  = getString(R.string.mc_status_bar_client_connected)
                btnAction.text = getString(R.string.mc_btn_leave_session)
                btnAction.setOnClickListener { showLeaveSessionDialog() }
                if (pendingOpenSoortSelectieOnPair && !tilesSyncedFromMaster) {
                    pendingOpenSoortSelectieOnPair = false
                    openSoortSelectieForAdd()
                }
            }
            com.yvesds.vt5.features.masterClient.ClientConnector.State.CONNECTING -> {
                tvStatus.text  = getString(R.string.mc_status_bar_client_connecting)
                btnAction.text = getString(R.string.mc_btn_connect_to_master)
                btnAction.setOnClickListener { showClientPairingDialog() }
            }
            com.yvesds.vt5.features.masterClient.ClientConnector.State.ERROR -> {
                tvStatus.text  = getString(R.string.mc_status_bar_client_error)
                btnAction.text = getString(R.string.mc_btn_connect_to_master)
                btnAction.setOnClickListener { showClientPairingDialog() }
            }
            com.yvesds.vt5.features.masterClient.ClientConnector.State.DISCONNECTED -> {
                tvStatus.text  = getString(R.string.mc_status_bar_client_connecting)
                btnAction.text = getString(R.string.mc_btn_connect_to_master)
                btnAction.setOnClickListener { showClientPairingDialog() }
            }
        }
    }

    private fun showLeaveSessionDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.mc_dialog_leave_title)
            .setMessage(R.string.mc_dialog_leave_message)
            .setPositiveButton(R.string.mc_dialog_leave_confirm) { _, _ ->
                tilesSyncedFromMaster = false
                mcClientConnector?.leaveSession("gebruiker verlaat telpost")
                Toast.makeText(this, getString(R.string.mc_toast_left_session), Toast.LENGTH_SHORT).show()
                binding.root.findViewById<android.view.View?>(R.id.mcStatusBar)
                    ?.visibility = android.view.View.GONE
            }
            .setNegativeButton(R.string.mc_dialog_leave_cancel, null)
            .create()
        com.yvesds.vt5.core.ui.DialogStyler.apply(dialog)
        dialog.show()
    }

    private fun showMasterHandoverDialog(eindtijdEpoch: String, masterName: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.mc_handover_title)
            .setMessage(getString(R.string.mc_handover_message, masterName))
            .setPositiveButton(R.string.mc_handover_confirm) { _, _ ->
                mcClientConnector?.stop()
                com.yvesds.vt5.features.masterClient.MasterClientPrefs.clearSession(this)
                com.yvesds.vt5.features.masterClient.MasterClientPrefs.setMode(
                    this,
                    com.yvesds.vt5.features.masterClient.MasterClientPrefs.MODE_MASTER
                )
                startMasterServerOnDemand {
                    val intent = Intent(this, MetadataScherm::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    intent.putExtra(MetadataScherm.EXTRA_VERVOLG_BEGINTIJD_EPOCH, eindtijdEpoch)
                    startActivity(intent)
                    finish()
                }
            }
            .setNegativeButton(R.string.mc_handover_decline) { _, _ ->
                mcClientConnector?.stop()
                com.yvesds.vt5.features.masterClient.MasterClientPrefs.clearSession(this)
                com.yvesds.vt5.features.masterClient.MasterClientPrefs.setMode(
                    this,
                    com.yvesds.vt5.features.masterClient.MasterClientPrefs.MODE_SOLO
                )
                val intent = Intent(this, HoofdActiviteit::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                finish()
            }
            .setCancelable(false)
            .create()
        com.yvesds.vt5.core.ui.DialogStyler.apply(dialog)
        dialog.show()
    }

    private fun showMasterPairingDialog() {
        val pm = mcPairingManager ?: run {
            Toast.makeText(this, getString(R.string.mc_error_no_telling), Toast.LENGTH_SHORT).show()
            return
        }
        val dialogView = layoutInflater.inflate(R.layout.dialog_pairing_master, null)
        val tvPin      = dialogView.findViewById<android.widget.TextView>(R.id.tvMasterPin)
        val btnNewPin  = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnNewPin)
        val btnClose   = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnClosePairing)
        val rvClients  = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvConnectedClients)
        val btnImport  = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnImportOffline)
        val ivQr       = dialogView.findViewById<ImageView>(R.id.ivPairingQr)
        val tvQrInfo   = dialogView.findViewById<android.widget.TextView>(R.id.tvPairingQrInfo)
        val ivWifiQr   = dialogView.findViewById<ImageView>(R.id.ivWifiQr)
        val tvWifiInfo = dialogView.findViewById<android.widget.TextView>(R.id.tvWifiQrInfo)
        val etSsid     = dialogView.findViewById<EditText>(R.id.etHotspotSsid)
        val etPass     = dialogView.findViewById<EditText>(R.id.etHotspotPass)
        val btnSaveHotspot = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveHotspot)

        etSsid.setText(com.yvesds.vt5.features.masterClient.MasterClientPrefs.getHotspotSsid(this))
        etPass.setText(com.yvesds.vt5.features.masterClient.MasterClientPrefs.getHotspotPassword(this))

        val pin = pm.getCurrentPin() ?: pm.generatePin()
        tvPin.text = pin

        val masterPort = mcMasterServer?.port ?: com.yvesds.vt5.features.masterClient.MasterClientPrefs.DEFAULT_PORT
        val sec = com.yvesds.vt5.features.masterClient.MasterClientPrefs.getHotspotSecurity(this)

        fun updateWifiQr() {
            val ssid = etSsid.text.toString().trim()
            val pass = etPass.text.toString().trim()
            if (ssid.isBlank() || pass.isBlank()) {
                ivWifiQr.setImageDrawable(null)
                tvWifiInfo.text = getString(R.string.mc_pairing_wifi_qr_missing)
                return
            }
            val payload = buildWifiQrPayload(ssid, pass, sec)
            val bmp = BarcodeEncoder().encodeBitmap(payload, com.google.zxing.BarcodeFormat.QR_CODE, 360, 360)
            ivWifiQr.setImageBitmap(bmp)
            tvWifiInfo.text = ssid
        }

        fun updatePairingQr() {
            val masterIp = com.yvesds.vt5.features.masterClient.McNetworkUtils.getLocalIpv4()
            if (masterIp.isNullOrBlank()) {
                tvQrInfo.text = getString(R.string.mc_pairing_master_no_ip)
                ivQr.setImageDrawable(null)
                return
            }
            val payload = com.yvesds.vt5.features.masterClient.McQrPayload(
                ip = masterIp,
                port = masterPort,
                pin = tvPin.text.toString(),
                ssid = etSsid.text.toString().trim(),
                pass = etPass.text.toString().trim(),
                sec = sec
            )
            val qrData = com.yvesds.vt5.features.masterClient.McQrPayloadCodec.encode(payload)
            val bitmap = BarcodeEncoder().encodeBitmap(qrData, com.google.zxing.BarcodeFormat.QR_CODE, 360, 360)
            ivQr.setImageBitmap(bitmap)
            tvQrInfo.text = "$masterIp:$masterPort"
        }

        updateWifiQr()
        updatePairingQr()

        val connectedList = mcMasterServer?.connectedClients?.value?.toList() ?: emptyList()
        rvClients.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rvClients.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            val items = connectedList.toMutableList()
            override fun getItemCount() = items.size
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val tv = android.widget.TextView(parent.context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(24, 12, 24, 12)
                    setTextColor(android.graphics.Color.WHITE)
                }
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(tv) {}
            }
            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                (holder.itemView as android.widget.TextView).text = "• ${items[position]}"
            }
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnSaveHotspot.setOnClickListener {
            val ssid = etSsid.text.toString().trim()
            val pass = etPass.text.toString().trim()
            com.yvesds.vt5.features.masterClient.MasterClientPrefs.setHotspotSsid(this, ssid)
            com.yvesds.vt5.features.masterClient.MasterClientPrefs.setHotspotPassword(this, pass)
            Toast.makeText(this, getString(R.string.mc_pairing_hotspot_saved), Toast.LENGTH_SHORT).show()
            updateWifiQr()
            updatePairingQr()
        }

        btnNewPin.setOnClickListener {
            tvPin.text = pm.generatePin()
            updatePairingQr()
            updateMasterStatusBar(mcMasterServer?.connectedClients?.value?.size ?: 0)
        }
        btnClose.setOnClickListener { dialog.dismiss() }

        btnImport.setOnClickListener {
            dialog.dismiss()
            launchOfflineImport()
        }

        val btnEndCollab = dialogView.findViewById<com.google.android.material.button.MaterialButton?>(R.id.btnEndCollab)
        btnEndCollab?.setOnClickListener {
            dialog.dismiss()
            showEndCollabDialog()
        }

        dialog.show()
        com.yvesds.vt5.core.ui.DialogStyler.apply(dialog)
    }

        private fun showEndCollabDialog() {
        val server = mcMasterServer ?: return
        val dlg = AlertDialog.Builder(this)
            .setTitle(R.string.mc_dialog_end_collab_title)
            .setMessage(R.string.mc_dialog_end_collab_message)
            .setPositiveButton(R.string.mc_dialog_end_collab_confirm) { _, _ ->
                server.broadcastSessionEnd("samenwerking beëindigd door master")
                Toast.makeText(this, getString(R.string.mc_toast_session_ended_broadcast), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.mc_dialog_end_collab_cancel, null)
            .create()
        DialogStyler.apply(dlg)
        dlg.show()
    }

    private fun showClientPairingDialog() {
        val connector = mcClientConnector ?: run {
            Toast.makeText(this, getString(R.string.mc_error_no_telling), Toast.LENGTH_SHORT).show()
            return
        }
        val dialogView   = layoutInflater.inflate(R.layout.dialog_pairing_client, null)
        val etIp         = dialogView.findViewById<android.widget.EditText>(R.id.etMasterIp)
        val etPin        = dialogView.findViewById<android.widget.EditText>(R.id.etPin)
        val tvStatus     = dialogView.findViewById<android.widget.TextView>(R.id.tvPairingStatus)
        val btnCancel    = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelPairing)
        val btnConnect   = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConnectToMaster)
        val btnScanQr    = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnScanQr)
        val rvDiscovered = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvDiscoveredMasters)

        etIp.setText(com.yvesds.vt5.features.masterClient.MasterClientPrefs.getMasterIp(this))
        activeClientPairingDialog = ClientPairingDialogRefs(etIp, etPin, tvStatus)

        btnScanQr.setOnClickListener {
            pendingQrScanMode = QrScanMode.WIFI
            val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (hasCamera) {
                launchQrScan(getString(R.string.mc_pairing_scan_wifi_qr))
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        rvDiscovered.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        val discoveredItems = mcDiscoveryService?.discoveredMasters?.value ?: emptyList()
        rvDiscovered.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            val items = discoveredItems
            override fun getItemCount() = items.size
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val tv = android.widget.TextView(parent.context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(24, 12, 24, 12)
                    setTextColor(android.graphics.Color.WHITE)
                    isClickable = true
                }
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(tv) {}
            }
            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val m = items[position]
                val tv = holder.itemView as android.widget.TextView
                tv.text = "${m.name} (${m.host}:${m.port})"
                tv.setOnClickListener { etIp.setText(m.host) }
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener {
            activeClientPairingDialog = null
            dialog.dismiss()
        }
        btnConnect.setOnClickListener {
            val ip  = etIp.text.toString().trim()
            val pin = etPin.text.toString().trim()
            if (ip.isBlank()) { tvStatus.text = getString(R.string.mc_error_no_ip); return@setOnClickListener }
            if (pin.isBlank()) { tvStatus.text = getString(R.string.mc_error_no_pin); return@setOnClickListener }
            tvStatus.text = getString(R.string.mc_pairing_connecting)
            com.yvesds.vt5.features.masterClient.MasterClientPrefs.setMasterIp(this, ip)
            connector.setPendingPin(pin)
            connector.start()
            activeClientPairingDialog = null
            dialog.dismiss()
        }

        dialog.show()
        DialogStyler.apply(dialog)
    }

    private fun buildWifiQrPayload(ssid: String, pass: String, sec: String): String {
        fun esc(input: String) = input
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(":", "\\:")
        val t = sec.ifBlank { "WPA" }
        return "WIFI:T:${esc(t)};S:${esc(ssid)};P:${esc(pass)};;"
    }

    private var tilesSyncedFromMaster = false

    private fun buildTileSyncItems(): List<TileSyncItem> {
        return tegelBeheer.getTiles().map { tile ->
            TileSyncItem(
                soortid = tile.soortId,
                naam = tile.naam,
                countMain = tile.countMain,
                countReturn = tile.countReturn
            )
        }
    }

    private fun applyTileSync(items: List<TileSyncItem>) {
        if (items.isEmpty()) return
        val tiles = items.map { item ->
            SoortTile(item.soortid, item.naam, item.countMain, item.countReturn)
        }
        tegelBeheer.setTiles(tiles)
        updateSelectedSpeciesMap()
        tilesSyncedFromMaster = true
        pendingOpenSoortSelectieOnPair = false
        Toast.makeText(this, getString(R.string.mc_tile_sync_toast), Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val ids = tiles.map { it.soortId }.toSet()
                val mc = initializer.buildMatchContext(ids)
                speechHandler.updateCachedMatchContext(mc)
            } catch (_: Exception) {
            }
        }
    }
}
