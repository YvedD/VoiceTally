@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.yvesds.vt5.features.telling

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
import com.yvesds.vt5.features.masterClient.ClientConnector
import com.yvesds.vt5.features.masterClient.ClientEventQueue
import com.yvesds.vt5.features.masterClient.MasterClientPrefs
import com.yvesds.vt5.features.masterClient.MasterEventProcessor
import com.yvesds.vt5.features.masterClient.MasterServer
import com.yvesds.vt5.features.masterClient.McNetworkUtils
import com.yvesds.vt5.features.masterClient.McQrPayload
import com.yvesds.vt5.features.masterClient.McQrPayloadCodec
import com.yvesds.vt5.features.masterClient.McRuntimePermissions
import com.yvesds.vt5.features.masterClient.PairingManager
import com.yvesds.vt5.features.masterClient.protocol.TileSyncItem
import com.yvesds.vt5.features.recent.RecentSpeciesStore
import com.yvesds.vt5.features.recent.SpeciesUsageScoreStore
import com.yvesds.vt5.net.ServerTellingDataItem
import com.yvesds.vt5.net.ServerTellingEnvelope
import com.yvesds.vt5.features.metadata.ui.MetadataScherm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlin.coroutines.resume
import kotlin.jvm.Volatile
import com.yvesds.vt5.R
import com.yvesds.vt5.core.ui.DialogStyler
import com.yvesds.vt5.features.speech.Candidate
import com.yvesds.vt5.hoofd.InstellingenScherm
import com.yvesds.vt5.hoofd.HoofdActiviteit

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
        private const val KEY_CLIENT_EVENT_MAP_JSON = "pref_client_event_map_json"

        private const val MAX_LOG_ROWS = 600
        private const val TILE_TAP_GROUP_WINDOW_MS = 5_000L
        
        // Auto-dismiss delay for success dialog (ms)
        private const val SUCCESS_DIALOG_DELAY_MS = 1000L
        
        // Intent extra key for hourly alarm trigger
        const val EXTRA_SHOW_HUIDIGE_STAND = "SHOW_HUIDIGE_STAND"
        const val EXTRA_RESTORE_PENDING_TELLING = "RESTORE_PENDING_TELLING"
        const val EXTRA_CLIENT_QR_PAYLOAD = "EXTRA_CLIENT_QR_PAYLOAD"
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
    private lateinit var tileTapAggregationManager: TileTapAggregationManager
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
    private val parseJobsByUtteranceId = linkedMapOf<String, Job>()

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

    // Master-client state
    private var mcPairingManager: PairingManager? = null
    private var mcEventProcessor: MasterEventProcessor? = null
    private var mcMasterServer: MasterServer? = null
    private var mcEventQueue: ClientEventQueue? = null
    private var mcClientConnector: ClientConnector? = null
    private val mcClientEventIdByRecordId = mutableMapOf<String, String>()
    private val mcClientRecordIdByEvent = mutableMapOf<String, String>()
    private var pendingClientConnectionPayload: McQrPayload? = null
    private var lastObservedClientState: ClientConnector.State? = null
    private var masterPairingDialog: AlertDialog? = null
    private var masterPairingConnectedClientsView: TextView? = null

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        val raw = result.contents?.trim().orEmpty()
        if (raw.isBlank()) return@registerForActivityResult

        val payload = McQrPayloadCodec.decode(raw)
        if (payload == null) {
            Toast.makeText(this, getString(R.string.mc_pairing_qr_invalid), Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        handleClientQrPayload(payload)
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        McRuntimePermissions.cachePermissionResult(this, Manifest.permission.CAMERA, granted)
        if (granted) {
            launchPairingQrScan()
        } else {
            Toast.makeText(this, getString(R.string.mc_pairing_qr_invalid), Toast.LENGTH_SHORT).show()
        }
    }

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
    enum class ObservationDeliveryState {
        NONE,
        PENDING,
        RECEIVED,
        REJECTED
    }

    data class SoortRow(
        val soortId: String, 
        val naam: String, 
        val countMain: Int = 0,
        val countReturn: Int = 0,
        val pendingMainCount: Int = 0
    ) {
        // Backwards compatible total count property
        val count: Int get() = countMain + countReturn
    }
    data class SpeechLogRow(
        val ts: Long,
        val tekst: String,
        val bron: String,
        val isPending: Boolean = false,
        val recordLocalId: String? = null,
        val rowKey: String? = null,
        val isError: Boolean = false,
        val deliveryState: ObservationDeliveryState = ObservationDeliveryState.NONE,
        val isClientOrigin: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SchermTellingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBackPressedCallback()

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        restoreClientEventMappings()

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
                refreshTileRows(list)
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
        setupMasterClientSupport()
        handleClientStartIntent()

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
        tileTapAggregationManager = TileTapAggregationManager(
            scope = lifecycleScope,
            groupWindowMs = TILE_TAP_GROUP_WINDOW_MS,
            onPendingCountChanged = { _, _ -> refreshTileRows() },
            onPendingRowUpsert = { row -> upsertFinalObservationRow(row) },
            onAggregateFinalized = { aggregate, finalizedAtEpochSeconds ->
                commitTileTapAggregate(aggregate, finalizedAtEpochSeconds)
            }
        )
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
        speechHandler.onHypothesesReceived = { utteranceId, hypotheses, partials ->
            handleSpeechHypotheses(utteranceId, hypotheses, partials)
        }
        speechHandler.onPendingMatchResult = { utteranceId, result ->
            lifecycleScope.launch(Dispatchers.Main) {
                try {
                    matchResultHandler.handleMatchResult(result, utteranceId)
                } catch (ex: Exception) {
                    Log.w(TAG, "Pending hypotheses handling failed: ${ex.message}", ex)
                }
            }
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
        matchResultHandler.onAutoAccept = { utteranceId, candidate, amount ->
            handleRecognizedCandidate(utteranceId, candidate, amount)
        }
        matchResultHandler.onAutoAcceptWithPopup = { utteranceId, candidate, amount ->
            handleRecognizedCandidate(utteranceId, candidate, amount)
        }
        matchResultHandler.onMultiMatch = { utteranceId, matches, unmatchedFragments ->
            matches.forEach { match ->
                handleRecognizedCandidate(utteranceId, match.candidate, match.amount)
            }
            unmatchedFragments
                .map(String::trim)
                .filter(String::isNotBlank)
                .takeIf { it.isNotEmpty() }
                ?.let { fragments ->
                    upsertSpeechPartialLog(utteranceId, fragments.joinToString(" | "), isError = true)
                }
        }
        matchResultHandler.onSuggestionList = { utteranceId, hypothesis, candidates, count ->
            showSuggestionBottomSheet(candidates, count, rawHypothesis = hypothesis, utteranceId = utteranceId)
        }
        matchResultHandler.onNoMatch = { utteranceId, hypothesis ->
            val now = System.currentTimeMillis()
            if (now - lastPartialUiUpdateMs >= PARTIAL_UI_DEBOUNCE_MS) {
                upsertSpeechPartialLog(utteranceId, hypothesis, isError = true)
                lastPartialUiUpdateMs = now
            } else {
                upsertSpeechPartialLog(utteranceId, hypothesis, isError = true)
            }
        }

        // Species manager callbacks
        speciesManager.onLogMessage = { msg, source -> addLog(msg, source) }
        speciesManager.onTilesUpdated = { updateSelectedSpeciesMap() }
        speciesManager.onRecordCollected = { item ->
            addOrReplacePendingRecord(item)
            queueClientObservationIfNeeded(item)
            updateClientFinalObservationRow(
                item,
                if (MasterClientPrefs.getMode(this) == MasterClientPrefs.MODE_CLIENT && mcMasterServer == null) {
                    ObservationDeliveryState.PENDING
                } else {
                    ObservationDeliveryState.NONE
                }
            )
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
                    val recordsSnapshot = pendingRecords.toList()
                    ensureTilesExistForRecords(recordsSnapshot)
                    tegelBeheer.recalculateCountsFromRecords(recordsSnapshot)
                }
            }

            queueClientObservationUpdateIfNeeded(updated)
            updateClientFinalObservationRow(updated)

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
        uiManager.onTileSingleTapCallback = { pos: Int -> handleTileTapIncrement(pos, 1) }
        uiManager.onTileDoubleTapCallback = { pos: Int ->
            handleTileTapIncrement(pos, InstellingenScherm.getTileDoubleTapIncrement(this))
        }
        uiManager.onTileLongPressCallback = { pos: Int ->
            lifecycleScope.launch {
                tilesAdapter.currentList.getOrNull(pos)?.let { row ->
                    tileTapAggregationManager.flushSpeciesAndAwait(row.soortId)
                }
                showNumberInputDialog(pos)
            }
        }
        uiManager.onAddSoortenCallback = { openSoortSelectieForAdd() }
        uiManager.onAfrondenCallback = { handleAfrondenWithConfirmation() }
        uiManager.onSaveCloseCallback = { tiles -> handleSaveClose(tiles) }
        uiManager.onOpenSettingsCallback = { openInstellingenScherm() }
        uiManager.onToggleAlarmCallback = { toggleHourlyAlarm() }
        uiManager.onMasterClientConnectionCallback = { handleMasterClientPressed() }
        uiManager.onShowMasterQrCallback = { handleShowMasterQrPressed() }

        // Setup buttons
        uiManager.setupButtons()
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
        if (row.bron == "final" && !row.isPending) {
            annotationHandler.launchAnnotatieScherm(row.tekst, row.ts, pos)
        }
    }

    /**
     * Handle Afronden button with confirmation dialog.
     * Shows a popup to allow editing begintijd, eindtijd, and opmerkingen.
     */
    private fun handleAfrondenWithConfirmation() {
        if (MasterClientPrefs.getMode(this) == MasterClientPrefs.MODE_CLIENT && mcMasterServer == null) {
            updateMasterClientConnectionUi()
            Toast.makeText(this, getString(R.string.mc_client_cannot_afronden), Toast.LENGTH_SHORT).show()
            return
        }

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
            if (savedEnvelopeJson.isNullOrBlank()) {
                // Return defaults with current time
                val nowEpoch = (System.currentTimeMillis() / 1000L).toString()
                return EnvelopeData(nowEpoch, nowEpoch, "")
            }
            
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
        updateMasterClientConnectionUi()
        maybeContinuePendingClientConnection()
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

    private fun maybeContinuePendingClientConnection() {
        val payload = pendingClientConnectionPayload ?: return
        if (!McNetworkUtils.isWifiTransportActive(this)) return
        pendingClientConnectionPayload = null
        connectClientToMaster(payload.ip, payload.session)
    }

    private fun setupBackPressedCallback() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val pendingCount = pendingClientObservationCount()
                if (pendingCount > 0) {
                    showPendingClientObservationsDialog(pendingCount)
                    return
                }

                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    private fun pendingClientObservationCount(): Int {
        return if (MasterClientPrefs.getMode(this) == MasterClientPrefs.MODE_CLIENT && mcMasterServer == null) {
            mcEventQueue?.totalUnacknowledged() ?: 0
        } else {
            0
        }
    }

    private fun showPendingClientObservationsDialog(pendingCount: Int) {
        if (pendingCount > 0) {
            val dialog = AlertDialog.Builder(this)
                .setTitle(R.string.mc_dialog_leave_pending_title)
                .setMessage(getString(R.string.mc_dialog_leave_pending_message, pendingCount))
                .setPositiveButton(android.R.string.ok, null)
                .create()
            DialogStyler.apply(dialog)
            dialog.show()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::alarmHandler.isInitialized) {
            alarmHandler.stopMonitoring()
        }
        lifecycleScope.launch {
            tileTapAggregationManager.flushAllAndAwait()

            // Always persist the latest envelope snapshot as a safety net
            withContext(Dispatchers.IO) {
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
    }

    override fun onDestroy() {
        dismissMasterPairingDialog()
        try {
            unregisterReceiver(aliasReloadReceiver)
        } catch (_: Exception) {}
        try {
            if (::speechHandler.isInitialized) {
                speechHandler.cleanup()
            }
        } catch (_: Exception) {}
        parseJobsByUtteranceId.values.forEach { it.cancel() }
        parseJobsByUtteranceId.clear()
        try {
            if (::alarmHandler.isInitialized) {
                alarmHandler.cleanup()
            }
        } catch (_: Exception) {}
        try {
            mcMasterServer?.stop()
        } catch (_: Exception) {}
        try {
            mcClientConnector?.stop()
        } catch (_: Exception) {}
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

    private fun handleTileTapIncrement(position: Int, delta: Int) {
        val row = tilesAdapter.currentList.getOrNull(position) ?: return
        if (delta <= 0) return

        speciesManager.updateSoortCountInternal(row.soortId, delta)
        tileTapAggregationManager.registerTap(row.soortId, row.naam, delta)

        RecentSpeciesStore.recordUse(
            this,
            row.soortId,
            maxEntries = InstellingenScherm.getMaxFavorieten(this).let {
                if (it == InstellingenScherm.MAX_FAVORIETEN_ALL) SpeciesUsageScoreStore.MAX_ALL_CAP else it
            }
        )
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
    private fun handleSpeechHypotheses(
        utteranceId: String,
        hypotheses: List<Pair<String, Float>>,
        partials: List<String>
    ) {
        val job = lifecycleScope.launch(Dispatchers.Default) {
            try {
                val matchContext = speechHandler.getCachedMatchContext() ?: run {
                    val tiles = tegelBeheer.getTiles().map { it.soortId }.toSet()
                    val mc = initializer.buildMatchContext(tiles)
                    speechHandler.updateCachedMatchContext(mc)
                    mc
                }

                val result = speechHandler.parseSpokenWithHypotheses(utteranceId, hypotheses, matchContext, partials, asrWeight = 0.4)

                withContext(Dispatchers.Main) {
                    try {
                        matchResultHandler.handleMatchResult(result, utteranceId)
                    } catch (ex: Exception) {
                        Log.w(TAG, "Hypotheses handling (UI) failed: ${ex.message}", ex)
                    }
                }
            } catch (ex: Exception) {
                Log.w(TAG, "Hypotheses handling (background) failed: ${ex.message}", ex)
            } finally {
                withContext(Dispatchers.Main) {
                    parseJobsByUtteranceId.remove(utteranceId)
                }
            }
        }

        parseJobsByUtteranceId[utteranceId] = job
    }



    /**
     * Record a species count (add final log, update count, collect record).
     */
    private fun recordSpeciesCount(utteranceId: String?, speciesId: String, displayName: String, count: Int) {
        lifecycleScope.launch {
            tileTapAggregationManager.flushSpeciesAndAwait(speciesId)
            addFinalLog("$displayName -> +$count", utteranceId)
            speciesManager.updateSoortCountInternal(speciesId, count)
            speciesManager.collectFinalAsRecord(speciesId, count)
        }
        RecentSpeciesStore.recordUse(this, speciesId, maxEntries = InstellingenScherm.getMaxFavorieten(this).let { if (it == InstellingenScherm.MAX_FAVORIETEN_ALL) SpeciesUsageScoreStore.MAX_ALL_CAP else it })
    }

    private fun handleRecognizedCandidate(utteranceId: String?, candidate: Candidate, count: Int) {
        clearSpeechPartialLog(utteranceId)
        when {
            candidate.isInTiles -> recordSpeciesCount(utteranceId, candidate.speciesId, candidate.displayName, count)
            candidate.autoAddToTiles -> autoAddRecognizedSpecies(utteranceId, candidate.speciesId, candidate.displayName, count)
            else -> showAddSpeciesConfirmationDialog(utteranceId, candidate.speciesId, candidate.displayName, count)
        }
    }

    private fun autoAddRecognizedSpecies(utteranceId: String?, speciesId: String, displayName: String, count: Int) {
        lifecycleScope.launch {
            tileTapAggregationManager.flushSpeciesAndAwait(speciesId)
            speciesManager.addSpeciesToTiles(speciesId, displayName, count)
            addFinalLog("$displayName -> +$count", utteranceId)
            speciesManager.collectFinalAsRecord(speciesId, count)
        }
        RecentSpeciesStore.recordUse(this, speciesId, maxEntries = InstellingenScherm.getMaxFavorieten(this).let { if (it == InstellingenScherm.MAX_FAVORIETEN_ALL) SpeciesUsageScoreStore.MAX_ALL_CAP else it })
    }

    /**
     * Show confirmation dialog for adding a new species to tiles.
     */
    private fun showAddSpeciesConfirmationDialog(utteranceId: String?, speciesId: String, displayName: String, count: Int) {
        val msg = "Soort \"$displayName\" herkend met aantal $count.\n\nToevoegen?"
        val dlg = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_species))
            .setMessage(msg)
            .setPositiveButton("Ja") { _, _ ->
                lifecycleScope.launch {
                    speciesManager.addSpeciesToTiles(speciesId, displayName, count)
                    addFinalLog("$displayName -> +$count", utteranceId)
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
                    viewModel.setPartials(logManager.getPartials())
                } else {
                    // raw, partial, systeem all go to partials
                    viewModel.setPartials(newList)
                }
            } else {
                // Fallback: no ViewModel — update adapter via uiManager
                if (bron == "final") {
                    uiManager.updateFinals(newList)
                    uiManager.updatePartials(logManager.getPartials())
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

    private fun upsertSpeechPartialLog(utteranceId: String?, text: String, isError: Boolean = false) {
        val newList = if (!utteranceId.isNullOrBlank()) {
            logManager.upsertSpeechPartialLog(utteranceId, text, isError)
        } else {
            logManager.upsertPartialLog(text)
        }

        lifecycleScope.launch(Dispatchers.Main) {
            if (::viewModel.isInitialized) {
                viewModel.setPartials(newList)
            } else {
                uiManager.updatePartials(newList)
            }
        }
    }

    private fun clearSpeechPartialLog(utteranceId: String?) {
        if (utteranceId.isNullOrBlank()) return
        val updatedPartials = logManager.clearSpeechPartialLog(utteranceId)
        lifecycleScope.launch(Dispatchers.Main) {
            if (::viewModel.isInitialized) {
                viewModel.setPartials(updatedPartials)
            } else {
                uiManager.updatePartials(updatedPartials)
            }
        }
    }

    // Delegate to logManager for addFinalLog
    private fun addFinalLog(text: String, speechUtteranceId: String? = null) {
        val updatedFinals = logManager.addFinalLog(text)
        val updatedPartials = if (!speechUtteranceId.isNullOrBlank()) {
            logManager.clearSpeechPartialLog(speechUtteranceId)
        } else {
            logManager.getPartials()
        }
        
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

    private fun upsertFinalObservationRow(row: SpeechLogRow) {
        val updatedFinals = logManager.getFinals().toMutableList()
        val existingIdx = updatedFinals.indexOfFirst { existing ->
            when {
                !row.rowKey.isNullOrBlank() -> existing.rowKey == row.rowKey
                !row.recordLocalId.isNullOrBlank() -> {
                    existing.recordLocalId == row.recordLocalId ||
                        (existing.recordLocalId.isNullOrBlank() && existing.bron == row.bron && existing.tekst == row.tekst)
                }
                else -> existing.ts == row.ts && existing.tekst.substringBefore(" ->") == row.tekst.substringBefore(" ->")
            }
        }
        if (existingIdx >= 0) {
            updatedFinals[existingIdx] = row
        } else {
            updatedFinals.add(row)
        }
        logManager.setFinals(updatedFinals)

        val updatedPartials = logManager.getPartials()
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
    private fun showSuggestionBottomSheet(
        candidates: List<Candidate>,
        count: Int,
        rawHypothesis: String? = null,
        utteranceId: String? = null
    ) {
        val items = candidates.map { "${it.displayName} (score: ${"%.2f".format(it.score)})" }.toTypedArray()

        val dlgList = AlertDialog.Builder(this)
            .setTitle("Kies soort")
            .setItems(items) { _, which ->
                val chosen = candidates[which]
                handleRecognizedCandidate(utteranceId, chosen, count)
                RecentSpeciesStore.recordUse(
                    this,
                    chosen.speciesId,
                    maxEntries = InstellingenScherm.getMaxFavorieten(this).let { if (it == InstellingenScherm.MAX_FAVORIETEN_ALL) SpeciesUsageScoreStore.MAX_ALL_CAP else it }
                )

            }
            .setNegativeButton("Annuleer", null)
            .show()

        if (!rawHypothesis.isNullOrBlank()) {
            upsertSpeechPartialLog(utteranceId, rawHypothesis, isError = false)
        }

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
        tileTapAggregationManager.flushAllAndAwait()
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
     * @param eindtijdEpoch The eindtijd to use as begintijd for the follow-up telling
     */
    private fun showVervolgtellingDialog(eindtijdEpoch: String) {
        val dlg = AlertDialog.Builder(this@TellingScherm)
            .setTitle(getString(R.string.afrond_vervolgtelling_titel))
            .setMessage(getString(R.string.afrond_vervolgtelling_msg))
            .setPositiveButton("OK") { _, _ ->
                // Navigate to MetadataScherm with begintijd preset to eindtijd of the previous telling
                val intent = Intent(this@TellingScherm, MetadataScherm::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                intent.putExtra(MetadataScherm.EXTRA_VERVOLG_BEGINTIJD_EPOCH, eindtijdEpoch)
                startActivity(intent)
                finish()
            }
            .setNegativeButton(getString(R.string.dlg_cancel)) { _, _ ->
                // Navigate to HoofdActiviteit (main screen) so user can close app or edit tellingen
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
                SpeechLogRow(
                    ts = rec.tijdstip.toLongOrNull() ?: (System.currentTimeMillis() / 1000L),
                    tekst = display,
                    bron = "final",
                    isPending = false,
                    recordLocalId = rec.idLocal,
                    rowKey = rec.idLocal
                )
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
        } catch (e: Exception) {
            Log.w(TAG, "restorePendingTellingIfAvailable failed: ${e.message}", e)
        }
    }

    private fun setupMasterClientSupport() {
        updateMasterClientConnectionUi()
    }

    private fun handleMasterClientPressed() {
        val mode = MasterClientPrefs.getMode(this)
        when {
            mcClientConnector != null || mode == MasterClientPrefs.MODE_CLIENT -> beginClientPairingFlow()
            mcMasterServer != null || mode == MasterClientPrefs.MODE_MASTER -> prepareMasterMode()
            else -> showMasterClientRoleChooser()
        }
    }

    private fun handleShowMasterQrPressed() {
        val mode = MasterClientPrefs.getMode(this)
        if (mode != MasterClientPrefs.MODE_MASTER && mcMasterServer == null) {
            Toast.makeText(this, getString(R.string.mc_qr_only_for_master), Toast.LENGTH_SHORT).show()
            return
        }

        startMasterServerOnDemand {
            showMasterPairingDialog()
        }
    }

    private fun showMasterClientRoleChooser() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.mc_dialog_choose_role_title)
            .setMessage(R.string.mc_dialog_choose_role_message)
            .setPositiveButton(R.string.mc_dialog_choose_master) { _, _ ->
                MasterClientPrefs.setMode(this, MasterClientPrefs.MODE_MASTER)
                updateMasterClientConnectionUi()
                prepareMasterMode()
            }
            .setNegativeButton(R.string.mc_dialog_choose_client) { _, _ ->
                beginClientPairingFlow()
            }
            .setNeutralButton(R.string.mc_dialog_choose_cancel, null)
            .create()
        DialogStyler.apply(dialog)
        dialog.show()
    }

    private fun beginClientPairingFlow() {
        ensureClientMode()
        launchPairingQrScan()
    }

    private fun prepareMasterMode() {
        MasterClientPrefs.setMode(this, MasterClientPrefs.MODE_MASTER)
        if (!McNetworkUtils.isWifiTransportActive(this)) {
            Toast.makeText(this, getString(R.string.mc_existing_wifi_required_master), Toast.LENGTH_LONG).show()
            updateMasterClientConnectionUi()
            return
        }

        startMasterServerOnDemand {
            cacheCurrentMasterConnectionDetails()
            Toast.makeText(this, getString(R.string.mc_master_network_ready_existing_wifi), Toast.LENGTH_SHORT).show()
            updateMasterClientConnectionUi()
        }
    }

    private fun ensureClientWifiReady(): Boolean {
        if (McNetworkUtils.isWifiTransportActive(this)) return true
        Toast.makeText(this, getString(R.string.mc_existing_wifi_required_client), Toast.LENGTH_LONG).show()
        updateMasterClientConnectionUi()
        return false
    }

    private fun handleClientStartIntent() {
        val rawPayload = intent?.getStringExtra(EXTRA_CLIENT_QR_PAYLOAD)?.trim().orEmpty()
        if (rawPayload.isBlank()) return

        intent?.removeExtra(EXTRA_CLIENT_QR_PAYLOAD)
        val payload = McQrPayloadCodec.decode(rawPayload)
        if (payload == null) {
            Toast.makeText(this, getString(R.string.mc_pairing_qr_invalid), Toast.LENGTH_SHORT).show()
            return
        }

        handleClientQrPayload(payload)
    }

    private fun handleClientQrPayload(payload: McQrPayload) {
        MasterClientPrefs.setMode(this, MasterClientPrefs.MODE_CLIENT)
        MasterClientPrefs.setMasterIp(this, payload.ip)
        MasterClientPrefs.setMasterPort(this, payload.port)
        MasterClientPrefs.setBootstrapSession(this, payload.session)

        ensureClientMode()
        pendingClientConnectionPayload = payload
        if (!ensureClientWifiReady()) {
            return
        }

        pendingClientConnectionPayload = null
        connectClientToMaster(payload.ip, payload.session)
    }

    private fun ensureClientMode() {
        if (mcClientConnector != null) {
            MasterClientPrefs.setMode(this, MasterClientPrefs.MODE_CLIENT)
            updateMasterClientConnectionUi()
            return
        }

        val queue = ClientEventQueue(applicationContext)
        val connector = ClientConnector(applicationContext, queue)
        mcEventQueue = queue
        mcClientConnector = connector
        MasterClientPrefs.setMode(this, MasterClientPrefs.MODE_CLIENT)

        connector.onTileSyncReceived = { tiles ->
            runOnUiThread { applyTileSync(tiles) }
        }
        connector.onObservationAcknowledged = { clientEventId ->
            runOnUiThread {
                Toast.makeText(this, getString(R.string.mc_observation_received_master), Toast.LENGTH_SHORT).show()
                markClientObservationStateByEventId(clientEventId, ObservationDeliveryState.RECEIVED)
                updateMasterClientConnectionUi()
            }
        }
        connector.onObservationRejected = { clientEventId, _ ->
            runOnUiThread {
                Toast.makeText(this, getString(R.string.mc_observation_retry_master), Toast.LENGTH_SHORT).show()
                markClientObservationStateByEventId(clientEventId, ObservationDeliveryState.REJECTED)
                updateMasterClientConnectionUi()
            }
        }
        connector.onSessionEnded = { _ ->
            runOnUiThread {
                Toast.makeText(this, getString(R.string.mc_status_session_ended), Toast.LENGTH_LONG).show()
                mcClientConnector?.stop()
                mcClientConnector = null
                mcEventQueue = null
                MasterClientPrefs.clearSession(this)
                updateMasterClientConnectionUi()
            }
        }

        lifecycleScope.launch {
            connector.state.collect { state ->
                runOnUiThread {
                    if (state == ClientConnector.State.PAIRED && lastObservedClientState != ClientConnector.State.PAIRED) {
                        Toast.makeText(this@TellingScherm, getString(R.string.mc_client_handshake_success), Toast.LENGTH_SHORT).show()
                    }
                    lastObservedClientState = state
                    updateMasterClientConnectionUi()
                }
            }
        }

        updateMasterClientConnectionUi()
    }

    private fun launchPairingQrScan() {
        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!hasCamera) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setBeepEnabled(false)
            setOrientationLocked(true)
            setPrompt(getString(R.string.mc_pairing_scan_qr))
        }
        qrScanLauncher.launch(options)
    }

    private fun promoteCurrentTellingToMaster() {
        prepareMasterMode()
    }


    private fun startMasterServerOnDemand(onStarted: (() -> Unit)? = null) {
        if (mcMasterServer != null) {
            updateMasterClientConnectionUi()
            onStarted?.invoke()
            return
        }

        try {
            val pairingManager = PairingManager()
            val eventProcessor = MasterEventProcessor()
            eventProcessor.onObservationReceived = { clientId, clientEventId, isUpdate, soortId, amount, aantalterug, tijdstip, geslacht, leeftijd, kleed, opmerkingen, recordPayload ->
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
                    opmerkingen = opmerkingen,
                    recordPayload = recordPayload
                )
            }

            val server = MasterServer(
                context = applicationContext,
                pairingManager = pairingManager,
                eventProcessor = eventProcessor
            )
            server.onPairingRequest = { _, clientName -> requestClientApproval(clientName) }
            server.onClientConnected = { clientName ->
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.mc_master_handshake_success, clientName), Toast.LENGTH_SHORT).show()
                    updateMasterClientConnectionUi()
                    dismissMasterPairingDialog()
                }
            }
            server.onTilesSnapshot = { buildTileSyncItems() }
            server.start()

            mcPairingManager = pairingManager
            mcEventProcessor = eventProcessor
            mcMasterServer = server
            MasterClientPrefs.setMode(this, MasterClientPrefs.MODE_MASTER)
            cacheCurrentMasterConnectionDetails()

            lifecycleScope.launch {
                server.connectedClients.collect {
                    runOnUiThread {
                        updateMasterClientConnectionUi()
                        updateMasterPairingDialogStatus()
                    }
                }
            }

            updateMasterClientConnectionUi()
            onStarted?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "startMasterServerOnDemand fout: ${e.message}", e)
            Toast.makeText(this, getString(R.string.mc_error_server_start, e.message ?: "Onbekende fout"), Toast.LENGTH_LONG).show()
            updateMasterClientConnectionUi()
        }
    }

    private suspend fun requestClientApproval(clientName: String): Boolean =
        suspendCancellableCoroutine { cont ->
            runOnUiThread {
                val dialog = AlertDialog.Builder(this)
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
                DialogStyler.apply(dialog)
                dialog.show()
            }
        }

    private fun updateMasterClientConnectionUi() {
        if (!::binding.isInitialized) return
        val mode = MasterClientPrefs.getMode(this)
        val clientState = mcClientConnector?.state?.value
        val isClient = mode == MasterClientPrefs.MODE_CLIENT || clientState == ClientConnector.State.CONNECTING || clientState == ClientConnector.State.PAIRED
        val isMaster = mode == MasterClientPrefs.MODE_MASTER || mcMasterServer != null
        val active = isMaster || isClient
        val tint = if (active) ContextCompat.getColor(this, R.color.vt5_green) else Color.parseColor("#808080")
        binding.btnMasterClientConnection.setColorFilter(tint)
        binding.btnMasterClientConnection.contentDescription = getString(
            if (active) R.string.mc_master_mode_active else R.string.mc_enable_master_mode
        )
        binding.btnShowMasterQr.visibility = if (isMaster) View.VISIBLE else View.GONE
        binding.btnShowMasterQr.setColorFilter(
            if (mcMasterServer != null) ContextCompat.getColor(this, R.color.vt5_green) else Color.parseColor("#808080")
        )
        val afrondenEnabled = !(mode == MasterClientPrefs.MODE_CLIENT && mcMasterServer == null)
        binding.btnAfronden.isEnabled = afrondenEnabled
        binding.btnAfronden.alpha = if (afrondenEnabled) 1.0f else 0.45f

        val statusText = when {
            isMaster && !McNetworkUtils.isWifiTransportActive(this) -> getString(R.string.mc_existing_wifi_required_master_short)
            isMaster -> {
                val count = mcMasterServer?.connectedClients?.value?.size ?: 0
                if (count > 0) getString(R.string.mc_status_bar_master, count)
                else getString(R.string.mc_status_bar_master_no_clients)
            }
            isClient && pendingClientConnectionPayload != null && !McNetworkUtils.isWifiTransportActive(this) -> getString(R.string.mc_existing_wifi_required_client_short)
            clientState == ClientConnector.State.PAIRED -> {
                val pending = mcEventQueue?.totalUnacknowledged() ?: 0
                if (pending > 0) getString(R.string.mc_status_bar_client_pending, pending)
                else getString(R.string.mc_status_bar_client_connected)
            }
            clientState == ClientConnector.State.CONNECTING -> getString(R.string.mc_status_bar_client_connecting)
            clientState == ClientConnector.State.ERROR -> {
                val detail = mcClientConnector?.lastError?.value?.trim().orEmpty()
                if (detail.isBlank()) getString(R.string.mc_status_bar_client_error)
                else getString(R.string.mc_status_bar_client_error_detail, detail)
            }
            mode == MasterClientPrefs.MODE_CLIENT -> getString(R.string.mc_existing_wifi_required_client_short)
            else -> ""
        }
        binding.tvMasterClientStatus.text = statusText
        binding.tvMasterClientStatus.visibility = if (statusText.isBlank()) View.GONE else View.VISIBLE
        updateMasterPairingDialogStatus()
    }

    private fun updateMasterPairingDialogStatus() {
        val target = masterPairingConnectedClientsView ?: return
        val clients = mcMasterServer?.connectedClients?.value.orEmpty().values.toList().sorted()
        target.text = if (clients.isEmpty()) {
            getString(R.string.mc_pairing_no_clients)
        } else {
            getString(R.string.mc_pairing_connected_clients_names, clients.joinToString(", "))
        }
    }

    private fun dismissMasterPairingDialog() {
        val dialog = masterPairingDialog
        if (dialog != null && dialog.isShowing) dialog.dismiss()
        masterPairingDialog = null
        masterPairingConnectedClientsView = null
    }

    private fun showMasterPairingDialog() {
        val server = mcMasterServer ?: run {
            Toast.makeText(this, getString(R.string.mc_error_no_telling), Toast.LENGTH_SHORT).show()
            return
        }

        if (!McNetworkUtils.isWifiTransportActive(this)) {
            Toast.makeText(this, getString(R.string.mc_existing_wifi_required_master), Toast.LENGTH_LONG).show()
            updateMasterClientConnectionUi()
            return
        }

        cacheCurrentMasterConnectionDetails()
        val masterIp = MasterClientPrefs.getMasterIp(this)
        if (masterIp.isBlank()) {
            Toast.makeText(this, getString(R.string.mc_pairing_master_no_ip), Toast.LENGTH_SHORT).show()
            return
        }

        val pairingSession = mcPairingManager?.openPairingSession().orEmpty()
        if (pairingSession.isBlank()) {
            Toast.makeText(this, getString(R.string.mc_qr_generation_failed), Toast.LENGTH_SHORT).show()
            return
        }
        MasterClientPrefs.setBootstrapSession(this, pairingSession)
        dismissMasterPairingDialog()

        val dialogView = layoutInflater.inflate(R.layout.dialog_master_qr_codes, null)
        val ivPairingQr = dialogView.findViewById<ImageView>(R.id.ivPairingQr)
        val tvPairingInfo = dialogView.findViewById<TextView>(R.id.tvPairingQrInfo)
        val tvConnectedClients = dialogView.findViewById<TextView>(R.id.tvConnectedClients)
        val btnClose = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnClose)

        updatePairingQrView(
            ivPairingQr = ivPairingQr,
            tvPairingInfo = tvPairingInfo,
            masterIp = masterIp,
            masterPort = server.port,
            bootstrapSession = pairingSession
        )

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            if (masterPairingDialog === dialog) {
                masterPairingDialog = null
                masterPairingConnectedClientsView = null
            }
        }
        masterPairingDialog = dialog
        masterPairingConnectedClientsView = tvConnectedClients
        updateMasterPairingDialogStatus()
        dialog.show()
        DialogStyler.apply(dialog)
    }

    private fun updatePairingQrView(
        ivPairingQr: ImageView,
        tvPairingInfo: android.widget.TextView,
        masterIp: String,
        masterPort: Int,
        bootstrapSession: String
    ) {
        when {
            masterIp.isBlank() -> {
                tvPairingInfo.text = getString(R.string.mc_pairing_master_no_ip)
                ivPairingQr.setImageDrawable(null)
            }
            bootstrapSession.isBlank() -> {
                tvPairingInfo.text = getString(R.string.mc_qr_generation_failed)
                ivPairingQr.setImageDrawable(null)
            }
            else -> {
                val payload = McQrPayload(
                    ip = masterIp,
                    port = masterPort,
                    session = bootstrapSession
                )
                val qrData = McQrPayloadCodec.encode(payload)
                val bitmap = BarcodeEncoder().encodeBitmap(qrData, com.google.zxing.BarcodeFormat.QR_CODE, 350, 350)
                ivPairingQr.setImageBitmap(bitmap)
                tvPairingInfo.text = getString(R.string.mc_pairing_qr_info_existing_wifi, masterIp, masterPort)
            }
        }
    }

    private fun showMasterWifiNetworkPicker() {
        prepareMasterMode()
    }

    private fun cacheCurrentMasterConnectionDetails() {
        MasterClientPrefs.setMasterIp(this, McNetworkUtils.getLocalIpv4().orEmpty())
        MasterClientPrefs.setMasterPort(this, mcMasterServer?.port ?: MasterClientPrefs.DEFAULT_PORT)
    }

    private fun connectClientToMaster(ip: String, bootstrapSession: String = "") {
        ensureClientMode()
        MasterClientPrefs.setMode(this, MasterClientPrefs.MODE_CLIENT)
        val connector = mcClientConnector ?: return
        if (ip.isBlank()) {
            Toast.makeText(this, getString(R.string.mc_error_no_ip), Toast.LENGTH_SHORT).show()
            return
        }
        if (!ensureClientWifiReady()) {
            pendingClientConnectionPayload = McQrPayload(ip = ip, port = MasterClientPrefs.getMasterPort(this), session = bootstrapSession)
            return
        }

        MasterClientPrefs.setMasterIp(this, ip)
        MasterClientPrefs.setBootstrapSession(this, bootstrapSession)
        connector.setBootstrapSession(bootstrapSession)
        connector.start()
        updateMasterClientConnectionUi()
    }

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
        val tiles = items.map {
            SoortTile(
                soortId = it.soortid,
                naam = it.naam,
                countMain = it.countMain,
                countReturn = it.countReturn
            )
        }.sortedBy { it.naam.lowercase() }

        tegelBeheer.setTiles(tiles)
        updateSelectedSpeciesMap()
        Toast.makeText(this, getString(R.string.mc_tile_sync_toast), Toast.LENGTH_SHORT).show()
    }

    private fun broadcastTileSyncIfMaster(tiles: List<SoortTile>) {
        mcMasterServer?.broadcastTileSync(
            tiles.map {
                TileSyncItem(
                    soortid = it.soortId,
                    naam = it.naam,
                    countMain = it.countMain,
                    countReturn = it.countReturn
                )
            }
        )
    }

    private fun queueClientObservationIfNeeded(item: ServerTellingDataItem) {
        val connector = mcClientConnector ?: return
        if (mcMasterServer != null) return

        val clientEventId = connector.queueObservation(
            soortid = item.soortid,
            aantal = item.aantal.toIntOrNull() ?: 0,
            aantalterug = item.aantalterug.toIntOrNull() ?: 0,
            tijdstip = item.tijdstip.toLongOrNull() ?: (System.currentTimeMillis() / 1000L),
            geslacht = item.geslacht,
            leeftijd = item.leeftijd,
            kleed = item.kleed,
            opmerkingen = item.opmerkingen,
            recordPayload = encodeObservationRecordPayload(item)
        )

        if (!clientEventId.isNullOrBlank()) {
            mcClientEventIdByRecordId[item.idLocal] = clientEventId
            mcClientRecordIdByEvent[clientEventId] = item.idLocal
            persistClientEventMappings()
            updateClientFinalObservationRow(item, ObservationDeliveryState.PENDING)
            updateMasterClientConnectionUi()
        }
    }

    private fun queueClientObservationUpdateIfNeeded(item: ServerTellingDataItem) {
        val connector = mcClientConnector ?: return
        if (mcMasterServer != null) return

        val clientEventId = mcClientEventIdByRecordId[item.idLocal] ?: return
        connector.queueObservationUpdate(
            clientEventId = clientEventId,
            soortid = item.soortid,
            aantal = item.aantal.toIntOrNull() ?: 0,
            aantalterug = item.aantalterug.toIntOrNull() ?: 0,
            tijdstip = item.tijdstip.toLongOrNull() ?: (System.currentTimeMillis() / 1000L),
            geslacht = item.geslacht,
            leeftijd = item.leeftijd,
            kleed = item.kleed,
            opmerkingen = item.opmerkingen,
            recordPayload = encodeObservationRecordPayload(item)
        )
        updateClientFinalObservationRow(item, ObservationDeliveryState.PENDING)
    }

    private fun restoreClientEventMappings() {
        val raw = prefs.getString(KEY_CLIENT_EVENT_MAP_JSON, null) ?: return
        try {
            val json = org.json.JSONObject(raw)
            val keys = json.keys()
            while (keys.hasNext()) {
                val recordId = keys.next()
                val eventId = json.optString(recordId)
                if (recordId.isNotBlank() && eventId.isNotBlank()) {
                    mcClientEventIdByRecordId[recordId] = eventId
                    mcClientRecordIdByEvent[eventId] = recordId
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Kon client event mapping niet herstellen: ${e.message}", e)
        }
    }

    private fun persistClientEventMappings() {
        try {
            val json = org.json.JSONObject()
            mcClientEventIdByRecordId.forEach { (recordId, eventId) ->
                json.put(recordId, eventId)
            }
            prefs.edit().putString(KEY_CLIENT_EVENT_MAP_JSON, json.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Kon client event mapping niet bewaren: ${e.message}", e)
        }
    }

    private fun encodeObservationRecordPayload(record: ServerTellingDataItem): String {
        return try {
            VT5App.json.encodeToString(ServerTellingDataItem.serializer(), record)
        } catch (ex: Exception) {
            Log.w(TAG, "Kon client record payload niet serialiseren: ${ex.message}", ex)
            ""
        }
    }

    private suspend fun handleLiveClientEvent(
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
        opmerkingen: String,
        recordPayload: String
    ) {
        val eventKey = "$clientId::$clientEventId"
        val existingIdLocal = mcClientRecordIdByEvent[eventKey]
        if (isUpdate && !existingIdLocal.isNullOrBlank()) {
            applyClientRecordUpdate(existingIdLocal, soortId, amount, aantalterug, tijdstip, geslacht, leeftijd, kleed, opmerkingen, recordPayload)
            return
        }

        val tellingId = prefs.getString("pref_telling_id", null) ?: return
        val idLocal = com.yvesds.vt5.features.network.DataUploader.getAndIncrementRecordId(this, tellingId)
        val uploadTimestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", resources.configuration.locales[0]).format(java.util.Date())
        val payloadRecord = runCatching {
            VT5App.json.decodeFromString(ServerTellingDataItem.serializer(), recordPayload)
        }.getOrNull()

        val item = buildMasterClientRecord(
            idLocal = idLocal,
            tellingId = tellingId,
            fallbackSoortId = soortId,
            fallbackAmount = amount,
            fallbackAantalterug = aantalterug,
            fallbackTijdstip = tijdstip,
            fallbackGeslacht = geslacht,
            fallbackLeeftijd = leeftijd,
            fallbackKleed = kleed,
            fallbackOpmerkingen = opmerkingen,
            uploadtijdstip = uploadTimestamp,
            payloadRecord = payloadRecord
        )

        addOrReplacePendingRecord(item)
        mcClientRecordIdByEvent[eventKey] = idLocal

        val displayName = resolveSpeciesName(item.soortid)
        tegelBeheer.voegSoortToeIndienNodig(item.soortid, displayName, 0)
        if ((item.aantal.toIntOrNull() ?: 0) > 0) {
            speciesManager.updateSoortCountInternal(item.soortid, item.aantal.toIntOrNull() ?: 0)
        }
        if ((item.aantalterug.toIntOrNull() ?: 0) > 0) {
            tegelBeheer.verhoogSoortAantalReturn(item.soortid, item.aantalterug.toIntOrNull() ?: 0)
        }
        upsertFinalObservationRow(buildFinalObservationRow(item, isClientOrigin = true))
    }

    private suspend fun applyClientRecordUpdate(
        idLocal: String,
        soortId: String,
        amount: Int,
        aantalterug: Int,
        tijdstip: Long,
        geslacht: String,
        leeftijd: String,
        kleed: String,
        opmerkingen: String,
        recordPayload: String
    ) {
        val idx = synchronized(pendingRecords) { pendingRecords.indexOfFirst { it.idLocal == idLocal } }
        if (idx < 0) return

        val existing = synchronized(pendingRecords) { pendingRecords[idx] }
        val payloadRecord = runCatching {
            VT5App.json.decodeFromString(ServerTellingDataItem.serializer(), recordPayload)
        }.getOrNull()
        val updated = mergeClientRecordUpdate(
            existing = existing,
            fallbackSoortId = soortId,
            fallbackAmount = amount,
            fallbackAantalterug = aantalterug,
            fallbackTijdstip = tijdstip,
            fallbackGeslacht = geslacht,
            fallbackLeeftijd = leeftijd,
            fallbackKleed = kleed,
            fallbackOpmerkingen = opmerkingen,
            payloadRecord = payloadRecord
        )

        synchronized(pendingRecords) { pendingRecords[idx] = updated }
        if (::viewModel.isInitialized) {
            viewModel.setPendingRecords(synchronized(pendingRecords) { pendingRecords.toList() })
        }
        val recordsSnapshot = synchronized(pendingRecords) { pendingRecords.toList() }
        ensureTilesExistForRecords(recordsSnapshot)
        tegelBeheer.recalculateCountsFromRecords(recordsSnapshot)
        persistEnvelopeAsync()

        if (hasClientAnnotationChanges(existing, updated) || existing.aantal != updated.aantal || existing.aantalterug != updated.aantalterug) {
            upsertFinalObservationRow(buildFinalObservationRow(updated, isClientOrigin = true))
        }
    }

    private fun buildFinalObservationRow(
        item: ServerTellingDataItem,
        deliveryState: ObservationDeliveryState = ObservationDeliveryState.NONE,
        isClientOrigin: Boolean = false
    ): SpeechLogRow {
        val name = resolveSpeciesName(item.soortid)
        val main = item.aantal.toIntOrNull() ?: 0
        val ret = item.aantalterug.toIntOrNull() ?: 0
        val prefix = if (isClientOrigin) getString(R.string.mc_log_client_prefix).trim() + " " else ""
        val display = when {
            main > 0 && ret <= 0 -> "$prefix$name -> +$main"
            main <= 0 && ret > 0 -> "$prefix$name (tegenrichting) -> +$ret"
            else -> "$prefix$name -> +$main / +$ret"
        }
        return SpeechLogRow(
            ts = item.tijdstip.toLongOrNull() ?: (System.currentTimeMillis() / 1000L),
            tekst = display,
            bron = "final",
            isPending = deliveryState == ObservationDeliveryState.PENDING,
            recordLocalId = item.idLocal,
            rowKey = item.idLocal,
            deliveryState = deliveryState,
            isClientOrigin = isClientOrigin
        )
    }

    private fun updateClientFinalObservationRow(
        item: ServerTellingDataItem,
        deliveryState: ObservationDeliveryState = existingDeliveryStateForRecord(item.idLocal)
    ) {
        upsertFinalObservationRow(buildFinalObservationRow(item = item, deliveryState = deliveryState, isClientOrigin = false))
    }

    private fun existingDeliveryStateForRecord(recordLocalId: String): ObservationDeliveryState {
        return logManager.getFinals().firstOrNull { it.recordLocalId == recordLocalId }?.deliveryState
            ?: ObservationDeliveryState.NONE
    }

    private fun markClientObservationStateByEventId(eventId: String, state: ObservationDeliveryState) {
        val recordLocalId = mcClientRecordIdByEvent[eventId].orEmpty()
        if (recordLocalId.isBlank()) return
        val currentRecord = synchronized(pendingRecords) { pendingRecords.firstOrNull { it.idLocal == recordLocalId } }
        if (currentRecord != null) {
            updateClientFinalObservationRow(currentRecord, state)
            return
        }
        val existingRow = logManager.getFinals().firstOrNull { it.recordLocalId == recordLocalId } ?: return
        upsertFinalObservationRow(existingRow.copy(isPending = state == ObservationDeliveryState.PENDING, deliveryState = state))
    }

    private fun ensureTilesExistForRecords(records: List<ServerTellingDataItem>) {
        records.asSequence()
            .map { it.soortid }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { soortId ->
                tegelBeheer.voegSoortToeIndienNodig(soortId, resolveSpeciesName(soortId), 0)
            }
    }

    private fun resolveSpeciesName(soortId: String): String {
        tegelBeheer.findNaamBySoortId(soortId)?.let { return it }
        return runCatching {
            com.yvesds.vt5.features.serverdata.model.ServerDataCache.getCachedOrNull()?.speciesById?.get(soortId)?.soortnaam
        }.getOrNull().orEmpty().ifBlank { soortId }
    }

    private fun buildMasterClientRecord(
        idLocal: String,
        tellingId: String,
        fallbackSoortId: String,
        fallbackAmount: Int,
        fallbackAantalterug: Int,
        fallbackTijdstip: Long,
        fallbackGeslacht: String,
        fallbackLeeftijd: String,
        fallbackKleed: String,
        fallbackOpmerkingen: String,
        uploadtijdstip: String,
        payloadRecord: ServerTellingDataItem?
    ): ServerTellingDataItem {
        val source = payloadRecord
        val soortid = source?.soortid?.ifBlank { fallbackSoortId } ?: fallbackSoortId
        val aantal = source?.aantal?.ifBlank { fallbackAmount.toString() } ?: fallbackAmount.toString()
        val aantalterug = source?.aantalterug?.ifBlank { fallbackAantalterug.toString() } ?: fallbackAantalterug.toString()
        val lokaal = source?.lokaal ?: "0"
        return ServerTellingDataItem(
            idLocal = idLocal,
            tellingid = tellingId,
            soortid = soortid,
            aantal = aantal,
            richting = source?.richting ?: "",
            aantalterug = aantalterug,
            richtingterug = source?.richtingterug ?: "",
            sightingdirection = source?.sightingdirection ?: "",
            lokaal = lokaal,
            aantal_plus = source?.aantal_plus ?: "0",
            aantalterug_plus = source?.aantalterug_plus ?: "0",
            lokaal_plus = source?.lokaal_plus ?: "0",
            markeren = source?.markeren ?: "0",
            markerenlokaal = source?.markerenlokaal ?: "0",
            geslacht = source?.geslacht ?: fallbackGeslacht,
            leeftijd = source?.leeftijd ?: fallbackLeeftijd,
            kleed = source?.kleed ?: fallbackKleed,
            opmerkingen = source?.opmerkingen ?: fallbackOpmerkingen,
            trektype = source?.trektype ?: "",
            teltype = source?.teltype ?: "",
            location = source?.location ?: "",
            height = source?.height ?: "",
            tijdstip = source?.tijdstip?.ifBlank { fallbackTijdstip.toString() } ?: fallbackTijdstip.toString(),
            groupid = idLocal,
            uploadtijdstip = source?.uploadtijdstip?.ifBlank { uploadtijdstip } ?: uploadtijdstip,
            totaalaantal = source?.totaalaantal?.ifBlank { calculateObservationTotal(aantal, aantalterug, lokaal) }
                ?: calculateObservationTotal(aantal, aantalterug, lokaal)
        )
    }

    private fun mergeClientRecordUpdate(
        existing: ServerTellingDataItem,
        fallbackSoortId: String,
        fallbackAmount: Int,
        fallbackAantalterug: Int,
        fallbackTijdstip: Long,
        fallbackGeslacht: String,
        fallbackLeeftijd: String,
        fallbackKleed: String,
        fallbackOpmerkingen: String,
        payloadRecord: ServerTellingDataItem?
    ): ServerTellingDataItem {
        val source = payloadRecord
        val soortid = source?.soortid?.ifBlank { fallbackSoortId } ?: fallbackSoortId
        val aantal = source?.aantal?.ifBlank { fallbackAmount.toString() } ?: fallbackAmount.toString()
        val aantalterug = source?.aantalterug?.ifBlank { fallbackAantalterug.toString() } ?: fallbackAantalterug.toString()
        val lokaal = source?.lokaal ?: existing.lokaal
        return existing.copy(
            soortid = soortid,
            aantal = aantal,
            richting = source?.richting ?: existing.richting,
            aantalterug = aantalterug,
            richtingterug = source?.richtingterug ?: existing.richtingterug,
            sightingdirection = source?.sightingdirection ?: existing.sightingdirection,
            lokaal = lokaal,
            aantal_plus = source?.aantal_plus ?: existing.aantal_plus,
            aantalterug_plus = source?.aantalterug_plus ?: existing.aantalterug_plus,
            lokaal_plus = source?.lokaal_plus ?: existing.lokaal_plus,
            markeren = source?.markeren ?: existing.markeren,
            markerenlokaal = source?.markerenlokaal ?: existing.markerenlokaal,
            geslacht = source?.geslacht ?: fallbackGeslacht,
            leeftijd = source?.leeftijd ?: fallbackLeeftijd,
            kleed = source?.kleed ?: fallbackKleed,
            opmerkingen = source?.opmerkingen ?: fallbackOpmerkingen,
            trektype = source?.trektype ?: existing.trektype,
            teltype = source?.teltype ?: existing.teltype,
            location = source?.location ?: existing.location,
            height = source?.height ?: existing.height,
            tijdstip = source?.tijdstip?.ifBlank { fallbackTijdstip.toString() } ?: fallbackTijdstip.toString(),
            uploadtijdstip = source?.uploadtijdstip ?: existing.uploadtijdstip,
            totaalaantal = source?.totaalaantal?.ifBlank { calculateObservationTotal(aantal, aantalterug, lokaal) }
                ?: calculateObservationTotal(aantal, aantalterug, lokaal)
        )
    }

    private fun hasClientAnnotationChanges(existing: ServerTellingDataItem, updated: ServerTellingDataItem): Boolean {
        return existing.geslacht != updated.geslacht ||
            existing.leeftijd != updated.leeftijd ||
            existing.kleed != updated.kleed ||
            existing.opmerkingen != updated.opmerkingen ||
            existing.location != updated.location ||
            existing.height != updated.height ||
            existing.lokaal != updated.lokaal
    }

    private fun calculateObservationTotal(aantal: String, aantalterug: String, lokaal: String): String {
        return ((aantal.toIntOrNull() ?: 0) + (aantalterug.toIntOrNull() ?: 0) + (lokaal.toIntOrNull() ?: 0)).toString()
    }

    private fun refreshTileRows(sourceTiles: List<SoortTile>? = null) {
        if (!::tilesAdapter.isInitialized) return
        val tiles = sourceTiles ?: tegelBeheer.getTiles()
        val rows = tiles.map { tile ->
            val pendingMain = if (::tileTapAggregationManager.isInitialized) {
                tileTapAggregationManager.getPendingMainCount(tile.soortId)
            } else {
                0
            }
            SoortRow(
                soortId = tile.soortId,
                naam = tile.naam,
                countMain = tile.countMain,
                countReturn = tile.countReturn,
                pendingMainCount = pendingMain
            )
        }
        tilesAdapter.submitList(rows)
        if (::viewModel.isInitialized) {
            viewModel.setTiles(rows)
        }
        broadcastTileSyncIfMaster(tiles)
    }

    private fun addOrReplacePendingRecord(item: ServerTellingDataItem) {
        synchronized(pendingRecords) {
            val index = pendingRecords.indexOfFirst { it.idLocal == item.idLocal }
            if (index >= 0) {
                pendingRecords[index] = item
            } else {
                pendingRecords.add(item)
            }
            if (::viewModel.isInitialized) {
                viewModel.setPendingRecords(pendingRecords.toList())
            }
        }
        persistEnvelopeAsync()
    }

    private fun persistEnvelopeAsync() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val records = synchronized(pendingRecords) { pendingRecords.toList() }
                envelopePersistence.saveEnvelopeWithRecords(records)
            } catch (ex: Exception) {
                Log.w(TAG, "Envelope persistence failed: ${ex.message}", ex)
            }
        }
    }

    private suspend fun backupObservationRecord(item: ServerTellingDataItem) {
        withContext(Dispatchers.IO) {
            try {
                backupManager.writeRecordBackupSaf(item.tellingid, item)
                    ?: backupManager.writeRecordBackupInternal(item.tellingid, item)
            } catch (ex: Exception) {
                Log.w(TAG, "Record backup failed during tile tap aggregation: ${ex.message}", ex)
            }
        }
    }

    private suspend fun commitTileTapAggregate(
        aggregate: TileTapAggregationManager.PendingAggregate,
        finalizedAtEpochSeconds: Long
    ) {
        val record = speciesManager.buildObservationRecord(
            soortId = aggregate.speciesId,
            amountMain = aggregate.totalMainCount,
            amountReturn = 0,
            explicitTimestampSeconds = finalizedAtEpochSeconds
        )

        if (record != null) {
            addOrReplacePendingRecord(record)
            backupObservationRecord(record)
            queueClientObservationIfNeeded(record)
            updateClientFinalObservationRow(record, ObservationDeliveryState.PENDING)
        } else {
            addLog("Gegroepeerde tegelwaarneming kon niet als record opgeslagen worden", "systeem")
        }

        if (record == null) {
            upsertFinalObservationRow(
                SpeechLogRow(
                    ts = finalizedAtEpochSeconds,
                    tekst = "${aggregate.displayName} -> +${aggregate.totalMainCount}",
                    bron = "final",
                    isPending = false,
                    rowKey = aggregate.pendingKey
                )
            )
        }
        refreshTileRows()
    }
}
