package com.yvesds.vt5.hoofd

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.NumberPicker
import com.yvesds.vt5.features.serverdata.model.ServerDataRepository
import com.yvesds.vt5.core.secure.CredentialsStore
import com.yvesds.vt5.features.opstart.helpers.ServerAuthenticationManager
import com.yvesds.vt5.features.opstart.helpers.ServerDataDownloadManager
import com.yvesds.vt5.features.opstart.helpers.InstallationSafManager
import com.yvesds.vt5.features.opstart.helpers.AliasIndexManager
import com.yvesds.vt5.features.opstart.helpers.InstallationDialogManager
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.yvesds.vt5.R
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.core.opslag.AppDataStore
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.yvesds.vt5.core.ui.DialogStyler
import com.yvesds.vt5.core.ui.UiColorPrefs
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.yvesds.vt5.features.update.AppUpdateManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * InstellingenScherm - Scherm voor app-instellingen met volledige tablet-synchronisatie.
 */
class InstellingenScherm : AppCompatActivity() {

    private var latestUpdateAsset: AppUpdateManager.ReleaseAsset? = null
    private var updateAssets: List<AppUpdateManager.ReleaseAsset> = emptyList()

    companion object {
        const val PREFS_NAME = "vt5_prefs"
        const val PREF_LETTERGROOTTE_TEGELS_SP = "pref_lettergrootte_tegels_sp"
        const val PREF_PARTIALS_TEXT_COLOR = "pref_partials_text_color"
        const val PREF_UNMATCHED_PARTIALS_TEXT_COLOR = "pref_unmatched_partials_text_color"
        const val PREF_FINALS_TEXT_COLOR = "pref_finals_text_color"
        const val PREF_LOG_TEXT_COLOR = "pref_log_text_color"
        const val PREF_PARTIALS_TEXT_SIZE_SP = "pref_partials_text_size_sp"
        const val PREF_FINALS_TEXT_SIZE_SP = "pref_finals_text_size_sp"
        const val PREF_TILE_DOUBLE_TAP_INCREMENT = "pref_tile_double_tap_increment"
        const val PREF_TILE_TAP_GROUP_WINDOW_SECONDS = "pref_tile_tap_group_window_seconds"
        const val PREF_DYNAMIC_TILE_SORTING_ENABLED = "pref_dynamic_tile_sorting_enabled"
        const val PREF_SERVER_RESPONSE_LOGGING_ENABLED = "pref_server_response_logging_enabled"
        const val PREF_STORAGE_MODE = "pref_storage_mode"
        const val EXTRA_TRIGGER_AI_TRAINING = "extra_trigger_ai_training"

        const val PREF_CHART_LINE_THICKNESS = "pref_chart_line_thickness"
        const val PREF_CHART_COLOR_WIND = "pref_chart_color_wind"
        const val PREF_CHART_COLOR_TREK = "pref_chart_color_trek"
        const val PREF_CHART_COLOR_TERUG = "pref_chart_color_terug"

        const val PREF_PERM_AUDIO_ACK = "pref_perm_audio_ack"
        const val PREF_PERM_SAF_ACK = "pref_perm_saf_ack"
        const val PREF_PERM_LOCATION_ACK = "pref_perm_location_ack"
        const val PREF_PERM_CAMERA_ACK = "pref_perm_camera_ack"
        const val PREF_PERM_BLUETOOTH_ACK = "pref_perm_bluetooth_ack"
        const val PREF_PERM_ALARM_ACK = "pref_perm_alarm_ack"

        const val MIN_LETTERGROOTTE_SP = 10
        const val MAX_LETTERGROOTTE_SP = 30
        const val DEFAULT_LETTERGROOTTE_SP = 17

        const val PREF_MAX_FAVORIETEN = "pref_max_favoriete_soorten"
        const val MAX_FAVORIETEN_ALL = -1
        const val DEFAULT_MAX_FAVORIETEN = 30
        const val DEFAULT_UNMATCHED_PARTIALS_TEXT_COLOR = -256
        const val DEFAULT_TILE_DOUBLE_TAP_INCREMENT = 10
        const val DEFAULT_TILE_TAP_GROUP_WINDOW_SECONDS = 5
        const val DEFAULT_DYNAMIC_TILE_SORTING_ENABLED = true
        const val DEFAULT_SERVER_RESPONSE_LOGGING_ENABLED = false

        const val STORAGE_MODE_JSON = "json"
        const val STORAGE_MODE_ROOM = "room"
        const val STORAGE_MODE_PARALLEL = "parallel"
        const val DEFAULT_STORAGE_MODE = STORAGE_MODE_PARALLEL

        private val TILE_DOUBLE_TAP_OPTIONS = listOf(5, 10, 50, 100)
        private val TILE_TAP_GROUP_WINDOW_OPTIONS = listOf(2, 3, 5, 8, 10, 12, 15)

        fun getPartialsTextSizeSp(context: Context): Int {
            return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(PREF_PARTIALS_TEXT_SIZE_SP, DEFAULT_LETTERGROOTTE_SP)
        }

        fun getFinalsTextSizeSp(context: Context): Int {
            return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(PREF_FINALS_TEXT_SIZE_SP, DEFAULT_LETTERGROOTTE_SP)
        }

        fun getLettergroottTegelsSp(context: Context): Int {
            return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(PREF_LETTERGROOTTE_TEGELS_SP, DEFAULT_LETTERGROOTTE_SP)
        }

        fun getPartialsTextColor(context: Context): Int {
            return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(PREF_PARTIALS_TEXT_COLOR, Color.WHITE)
        }

        fun getUnmatchedPartialsTextColor(context: Context): Int {
            return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(PREF_UNMATCHED_PARTIALS_TEXT_COLOR, DEFAULT_UNMATCHED_PARTIALS_TEXT_COLOR)
        }

        fun getFinalsTextColor(context: Context): Int {
            return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(PREF_FINALS_TEXT_COLOR, Color.WHITE)
        }

        fun getMaxFavorieten(context: Context): Int {
            return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(PREF_MAX_FAVORIETEN, DEFAULT_MAX_FAVORIETEN)
        }

        fun getTileDoubleTapIncrement(context: Context): Int {
            val stored = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(PREF_TILE_DOUBLE_TAP_INCREMENT, DEFAULT_TILE_DOUBLE_TAP_INCREMENT)
            return stored.takeIf { it in TILE_DOUBLE_TAP_OPTIONS } ?: DEFAULT_TILE_DOUBLE_TAP_INCREMENT
        }

        fun getTileTapGroupWindowSeconds(context: Context): Int {
            val stored = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(PREF_TILE_TAP_GROUP_WINDOW_SECONDS, DEFAULT_TILE_TAP_GROUP_WINDOW_SECONDS)
            return stored.takeIf { it in TILE_TAP_GROUP_WINDOW_OPTIONS } ?: DEFAULT_TILE_TAP_GROUP_WINDOW_SECONDS
        }

        fun isDynamicTileSortingEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(PREF_DYNAMIC_TILE_SORTING_ENABLED, DEFAULT_DYNAMIC_TILE_SORTING_ENABLED)
        }

        fun isServerResponseLoggingEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(PREF_SERVER_RESPONSE_LOGGING_ENABLED, DEFAULT_SERVER_RESPONSE_LOGGING_ENABLED)
        }

        fun getStorageMode(context: Context): String {
            return STORAGE_MODE_PARALLEL
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_instellingen)

        try {
            ensureLogTextColorDefaults()
            setupTerugKnop()
            setupLettergrootteNumberPickers()
            setupDoubleTapIncrementButtons()
            setupTileTapGroupWindowButtons()
            setupDynamicTileSortingToggle()
            setupServerResponseLoggingToggle()
            setupAutoAnnotationButton()
            setupColorSpinners()
            setupPartialsTextColorSpinner()
            setupUnmatchedPartialsTextColorSpinner()
            setupFinalsTextColorSpinner()
            setupMaxFavorietenButtons()
            setupPermissionAcknowledgements()
            setupAiSettings()
            setupUpdateSection()
            setupAliasBeheerButton()
            setupKrentenFilterSettings()
            setupChartSettings()
            setupDatabaseMirroringButton()
            setupBirdNetConfigButton()
            setupDefaultTelpostAndTypeTelling()
            setupTrektellenAccountSettings()
            setupCollapsibleSections()
        } catch (t: Throwable) {
            android.util.Log.e("InstellingenScherm", "Instellingen init failed: ${t.message}", t)
            Toast.makeText(this, "Fout in instellingen: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun ensureLogTextColorDefaults() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit {
            if (!prefs.contains(PREF_PARTIALS_TEXT_COLOR)) putInt(PREF_PARTIALS_TEXT_COLOR, Color.WHITE)
            if (!prefs.contains(PREF_UNMATCHED_PARTIALS_TEXT_COLOR)) putInt(PREF_UNMATCHED_PARTIALS_TEXT_COLOR, DEFAULT_UNMATCHED_PARTIALS_TEXT_COLOR)
            if (!prefs.contains(PREF_FINALS_TEXT_COLOR)) putInt(PREF_FINALS_TEXT_COLOR, Color.WHITE)
        }
    }

    private fun setupTerugKnop() {
        findViewById<MaterialButton>(R.id.btnTerug)?.setOnClickListener { finish() }
    }

    private fun setupAutoAnnotationButton() {
        findViewById<MaterialButton>(R.id.btnOpenAutoAnnotation)?.setOnClickListener {
            val intent = Intent(this, com.yvesds.vt5.features.telling.AutoAnnotatieBeheerScherm::class.java)
            startActivity(intent)
        }
    }

    private fun setupLettergrootteNumberPickers() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        fun setupNP(id: Int, key: String) {
            findViewById<NumberPicker>(id)?.apply {
                minValue = MIN_LETTERGROOTTE_SP
                maxValue = MAX_LETTERGROOTTE_SP
                wrapSelectorWheel = false
                value = prefs.getInt(key, DEFAULT_LETTERGROOTTE_SP).coerceIn(MIN_LETTERGROOTTE_SP, MAX_LETTERGROOTTE_SP)
                setOnValueChangedListener { _, _, newVal -> prefs.edit { putInt(key, newVal) } }
            }
        }

        setupNP(R.id.npLettergroottePartials, PREF_PARTIALS_TEXT_SIZE_SP)
        setupNP(R.id.npLettergrootteFinals, PREF_FINALS_TEXT_SIZE_SP)
        setupNP(R.id.npLettergrootteTegels, PREF_LETTERGROOTTE_TEGELS_SP)
    }

    private fun setupColorSpinners() {
        val spBg = findViewById<Spinner>(R.id.spBackgroundColor) ?: return
        val spText = findViewById<Spinner>(R.id.spTextColor) ?: return

        val bgOptions = UiColorPrefs.getBackgroundOptions(this)
        val textOptions = UiColorPrefs.getTextOptions(this)

        fun buildAdapter(items: List<UiColorPrefs.ColorOption>, getBg: () -> Int, getText: () -> Int): BaseAdapter {
            return object : BaseAdapter() {
                override fun getCount(): Int = items.size
                override fun getItem(position: Int): Any = items[position]
                override fun getItemId(position: Int): Long = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val tv = (convertView as? TextView) ?: layoutInflater.inflate(R.layout.item_color_option, parent, false) as TextView
                    val opt = items[position]
                    tv.text = opt.label
                    tv.setBackgroundColor(getBg())
                    tv.setTextColor(getText())
                    tv.setPadding(24, 18, 24, 18)
                    return tv
                }
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View = getView(position, convertView, parent)
            }
        }

        var selectedBg = UiColorPrefs.getBackgroundColor(this)
        var selectedText = UiColorPrefs.getTextColor(this)

        val bgAdapter = buildAdapter(bgOptions, { selectedBg }, { selectedText })
        val textAdapter = buildAdapter(textOptions, { selectedBg }, { selectedText })

        spBg.adapter = bgAdapter
        spText.adapter = textAdapter

        spBg.setSelection(bgOptions.indexOfFirst { it.argb == selectedBg }.coerceAtLeast(0))
        spText.setSelection(textOptions.indexOfFirst { it.argb == selectedText }.coerceAtLeast(0))

        spBg.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                selectedBg = bgOptions[position].argb
                UiColorPrefs.setBackgroundColor(this@InstellingenScherm, selectedBg)
                bgAdapter.notifyDataSetChanged(); textAdapter.notifyDataSetChanged()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        spText.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                selectedText = textOptions[position].argb
                UiColorPrefs.setTextColor(this@InstellingenScherm, selectedText)
                bgAdapter.notifyDataSetChanged(); textAdapter.notifyDataSetChanged()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun setupDoubleTapIncrementButtons() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val btnMap = mapOf(
            5 to findViewById<MaterialButton>(R.id.btnDoubleTap5),
            10 to findViewById<MaterialButton>(R.id.btnDoubleTap10),
            50 to findViewById<MaterialButton>(R.id.btnDoubleTap50),
            100 to findViewById<MaterialButton>(R.id.btnDoubleTap100)
        )

        fun applySelection(value: Int) {
            prefs.edit { putInt(PREF_TILE_DOUBLE_TAP_INCREMENT, value) }
            btnMap.forEach { (v, btn) -> btn?.isChecked = (v == value) }
        }

        applySelection(getTileDoubleTapIncrement(this))
        btnMap.forEach { (v, btn) ->
            btn?.setOnClickListener { applySelection(v) }
        }
    }

    private fun setupTileTapGroupWindowButtons() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val btnMap = mapOf(
            2 to findViewById<MaterialButton>(R.id.btnPendingTiles2),
            3 to findViewById<MaterialButton>(R.id.btnPendingTiles3),
            5 to findViewById<MaterialButton>(R.id.btnPendingTiles5),
            8 to findViewById<MaterialButton>(R.id.btnPendingTiles8),
            10 to findViewById<MaterialButton>(R.id.btnPendingTiles10),
            12 to findViewById<MaterialButton>(R.id.btnPendingTiles12),
            15 to findViewById<MaterialButton>(R.id.btnPendingTiles15)
        )

        fun applySelection(value: Int) {
            prefs.edit { putInt(PREF_TILE_TAP_GROUP_WINDOW_SECONDS, value) }
            btnMap.forEach { (v, btn) -> btn?.isChecked = (v == value) }
        }

        applySelection(getTileTapGroupWindowSeconds(this))
        btnMap.forEach { (v, btn) ->
            btn?.setOnClickListener { applySelection(v) }
        }
    }

    private fun setupDynamicTileSortingToggle() {
        findViewById<MaterialCheckBox>(R.id.cbDynamicTileSorting)?.apply {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            isChecked = prefs.getBoolean(PREF_DYNAMIC_TILE_SORTING_ENABLED, DEFAULT_DYNAMIC_TILE_SORTING_ENABLED)
            setOnCheckedChangeListener { _, isChecked -> prefs.edit { putBoolean(PREF_DYNAMIC_TILE_SORTING_ENABLED, isChecked) } }
        }
    }

    private fun setupServerResponseLoggingToggle() {
        findViewById<MaterialCheckBox>(R.id.cbServerResponseLogging)?.apply {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            isChecked = prefs.getBoolean(PREF_SERVER_RESPONSE_LOGGING_ENABLED, DEFAULT_SERVER_RESPONSE_LOGGING_ENABLED)
            setOnCheckedChangeListener { _, isChecked -> prefs.edit { putBoolean(PREF_SERVER_RESPONSE_LOGGING_ENABLED, isChecked) } }
        }
    }

    private fun setupPartialsTextColorSpinner() = setupLogColorSpinner(R.id.spPartialsTextColor, PREF_PARTIALS_TEXT_COLOR, Color.WHITE)
    private fun setupUnmatchedPartialsTextColorSpinner() = setupLogColorSpinner(R.id.spUnmatchedPartialsTextColor, PREF_UNMATCHED_PARTIALS_TEXT_COLOR, DEFAULT_UNMATCHED_PARTIALS_TEXT_COLOR)
    private fun setupFinalsTextColorSpinner() = setupLogColorSpinner(R.id.spFinalsTextColor, PREF_FINALS_TEXT_COLOR, Color.WHITE)

    private fun setupLogColorSpinner(spinnerId: Int, prefKey: String, defaultColor: Int) {
        val spinner = findViewById<Spinner>(spinnerId) ?: return
        val options = UiColorPrefs.getTextOptions(this)
        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = options.size
            override fun getItem(position: Int): Any = options[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = (convertView as? TextView) ?: layoutInflater.inflate(R.layout.item_color_option, parent, false) as TextView
                val opt = options[position]
                tv.text = opt.label
                tv.setTextColor(opt.argb)
                tv.setBackgroundColor(Color.TRANSPARENT)
                tv.setPadding(24, 18, 24, 18)
                return tv
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = getView(position, convertView, parent)
                v.setBackgroundColor(Color.parseColor("#333333"))
                return v
            }
        }
        spinner.adapter = adapter
        val current = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(prefKey, defaultColor)
        spinner.setSelection(options.indexOfFirst { it.argb == current }.coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { putInt(prefKey, options[pos].argb) }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun setupMaxFavorietenButtons() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val btnMap = mapOf(
            15 to findViewById<MaterialButton>(R.id.btnFav15),
            20 to findViewById<MaterialButton>(R.id.btnFav20),
            25 to findViewById<MaterialButton>(R.id.btnFav25),
            30 to findViewById<MaterialButton>(R.id.btnFav30),
            35 to findViewById<MaterialButton>(R.id.btnFav35),
            40 to findViewById<MaterialButton>(R.id.btnFav40),
            75 to findViewById<MaterialButton>(R.id.btnFav75),
            MAX_FAVORIETEN_ALL to findViewById<MaterialButton>(R.id.btnFavAll)
        )

        fun apply(v: Int) {
            prefs.edit { putInt(PREF_MAX_FAVORIETEN, v) }
            btnMap.forEach { (value, btn) -> btn?.isChecked = (value == v) }
        }

        val current = prefs.getInt(PREF_MAX_FAVORIETEN, DEFAULT_MAX_FAVORIETEN)
        apply(current)

        btnMap.forEach { (value, btn) ->
            btn?.setOnClickListener { apply(value) }
        }
    }

    private fun setupPermissionAcknowledgements() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasSaf = SaFStorageHelper(this).getRootUri() != null
        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasBluetooth = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val hasAlarm = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) (getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).canScheduleExactAlarms() else true

        bindPermCheckBox(R.id.cbPermAudio, PREF_PERM_AUDIO_ACK, prefs, hasAudio, R.string.perm_disable_message_audio)
        bindPermCheckBox(R.id.cbPermSaf, PREF_PERM_SAF_ACK, prefs, hasSaf, R.string.perm_disable_message_saf)
        bindPermCheckBox(R.id.cbPermLocation, PREF_PERM_LOCATION_ACK, prefs, hasLocation, R.string.perm_disable_message_location)
        bindPermCheckBox(R.id.cbPermCamera, PREF_PERM_CAMERA_ACK, prefs, hasCamera, 0)
        bindPermCheckBox(R.id.cbPermBluetooth, PREF_PERM_BLUETOOTH_ACK, prefs, hasBluetooth, 0)
        bindPermCheckBox(R.id.cbPermAlarm, PREF_PERM_ALARM_ACK, prefs, hasAlarm, 0)
    }

    private fun bindPermCheckBox(id: Int, key: String, prefs: SharedPreferences, granted: Boolean, msg: Int) {
        findViewById<MaterialCheckBox>(id)?.apply {
            isChecked = prefs.getBoolean(key, false) || granted
            setOnCheckedChangeListener { _, checked ->
                if (!checked) showDisablePermissionDialog(msg) { ok -> if (ok) prefs.edit { putBoolean(key, false) } else isChecked = true }
                else prefs.edit { putBoolean(key, true) }
            }
        }
    }

    private fun setupAiSettings() {
        findViewById<MaterialCheckBox>(R.id.cbAiEnabled)?.apply {
            lifecycleScope.launch {
                isChecked = AppDataStore.isAiEnabled(this@InstellingenScherm)
                setOnCheckedChangeListener { _, checked -> lifecycleScope.launch { AppDataStore.setAiEnabled(this@InstellingenScherm, checked) } }
            }
        }

        // Toggle to enable/disable neural inference (global override)
        findViewById<MaterialCheckBox>(R.id.cbUseNeuralInference)?.apply {
            lifecycleScope.launch {
                try {
                    isChecked = AppDataStore.isUseNeuralInference(this@InstellingenScherm)
                } catch (e: Exception) {
                    // fallback to AiConfig default if datastore unavailable
                    isChecked = com.yvesds.vt5.ai.AiConfig.USE_NEURAL_INFERENCE
                }
                setOnCheckedChangeListener { _, checked -> lifecycleScope.launch { AppDataStore.setUseNeuralInference(this@InstellingenScherm, checked) } }
            }
        }

        // Toggle to enable/disable daily-analysis-based weights
        findViewById<MaterialCheckBox>(R.id.cbUseDailyAnalysisWeights)?.apply {
            lifecycleScope.launch {
                try {
                    isChecked = AppDataStore.isUseDailyAnalysisWeights(this@InstellingenScherm)
                } catch (e: Exception) {
                    isChecked = com.yvesds.vt5.ai.AiConfig.USE_DAILY_ANALYSIS_WEIGHTS
                }
                setOnCheckedChangeListener { _, checked -> lifecycleScope.launch { AppDataStore.setUseDailyAnalysisWeights(this@InstellingenScherm, checked) } }
            }
        }
    }

    private fun setupAliasBeheerButton() {
        findViewById<MaterialButton>(R.id.btnOpenAliasBeheer)?.setOnClickListener {
            startActivity(Intent(this, com.yvesds.vt5.features.alias.ui.AliasBeheerScherm::class.java))
        }
    }

    private fun setupUpdateSection() {
        val tvStatus = findViewById<TextView>(R.id.tvUpdateStatus) ?: return
        val btnCheck = findViewById<MaterialButton>(R.id.btnCheckAppUpdate) ?: return
        val btnInstall = findViewById<MaterialButton>(R.id.btnInstallAppUpdate) ?: return
        val btnRollback = findViewById<MaterialButton>(R.id.btnShowAppRollback) ?: return

        btnCheck.setOnClickListener {
            performUpdateCheck(forceNetwork = true, showToast = true)
        }

        btnInstall.setOnClickListener {
            val asset = latestUpdateAsset
            if (asset == null) {
                Toast.makeText(this, getString(R.string.update_no_versions), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            installAssetFlow(asset)
        }

        btnRollback.setOnClickListener {
            showRollbackDialog()
        }

        lifecycleScope.launch {
            val cached = AppUpdateManager.getCachedState(this@InstellingenScherm)
            applyUpdateStateToUi(cached)
            performUpdateCheck(forceNetwork = false, showToast = false)
        }
    }

    private fun performUpdateCheck(forceNetwork: Boolean, showToast: Boolean) {
        val tvStatus = findViewById<TextView>(R.id.tvUpdateStatus) ?: return
        tvStatus.text = getString(R.string.update_status_checking)

        lifecycleScope.launch {
            val result = AppUpdateManager.refreshFromGithub(this@InstellingenScherm, allowCache = !forceNetwork)
            result.onSuccess { state ->
                applyUpdateStateToUi(state)
                val localLabel = "${state.localVersion} (${state.localVersionCode})"
                if (showToast) {
                    val msg = if (state.updateAvailable && !state.latestVersion.isNullOrBlank()) {
                        getString(R.string.update_status_available, state.latestVersion, localLabel)
                    } else {
                        getString(R.string.update_status_latest, localLabel)
                    }
                    Toast.makeText(this@InstellingenScherm, msg, Toast.LENGTH_LONG).show()
                }
            }.onFailure { err ->
                tvStatus.text = getString(R.string.update_status_error, err.message ?: "onbekende fout")
                findViewById<MaterialButton>(R.id.btnInstallAppUpdate)?.isEnabled = false
                if (showToast) {
                    Toast.makeText(this@InstellingenScherm, getString(R.string.update_status_error, err.message ?: "onbekende fout"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun applyUpdateStateToUi(state: AppUpdateManager.UpdateState) {
        val tvStatus = findViewById<TextView>(R.id.tvUpdateStatus) ?: return
        val btnInstall = findViewById<MaterialButton>(R.id.btnInstallAppUpdate) ?: return
        latestUpdateAsset = state.latestAsset
        if (state.allAssets.isNotEmpty()) {
            updateAssets = state.allAssets
        }
        val localLabel = "${state.localVersion} (${state.localVersionCode})"

        if (state.updateAvailable && !state.latestVersion.isNullOrBlank()) {
            tvStatus.text = getString(R.string.update_status_available, state.latestVersion, localLabel)
            btnInstall.isEnabled = true
        } else {
            tvStatus.text = getString(R.string.update_status_latest, localLabel)
            btnInstall.isEnabled = false
        }
    }

    private fun showRollbackDialog() {
        if (updateAssets.isEmpty()) {
            Toast.makeText(this, getString(R.string.update_no_versions), Toast.LENGTH_SHORT).show()
            performUpdateCheck(forceNetwork = true, showToast = false)
            return
        }

        val labels = updateAssets.map { asset ->
            if (asset == latestUpdateAsset) "${asset.version} (latest)" else asset.version
        }.toTypedArray()

        val dlg = AlertDialog.Builder(this)
            .setTitle(R.string.update_rollback_title)
            .setItems(labels) { _, which ->
                val selected = updateAssets.getOrNull(which) ?: return@setItems
                confirmInstallSelectedVersion(selected)
            }
            .setNegativeButton(R.string.annuleer, null)
            .show()
        DialogStyler.apply(dlg)
    }

    private fun confirmInstallSelectedVersion(asset: AppUpdateManager.ReleaseAsset) {
        val dlg = AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_rollback_confirm_title, asset.version))
            .setMessage(R.string.update_rollback_confirm_message)
            .setPositiveButton(R.string.update_prompt_now) { _, _ ->
                installAssetFlow(asset)
            }
            .setNegativeButton(R.string.annuleer, null)
            .show()
        DialogStyler.apply(dlg)
    }

    private fun installAssetFlow(asset: AppUpdateManager.ReleaseAsset) {
        if (!packageManager.canRequestPackageInstalls()) {
            val dlg = AlertDialog.Builder(this)
                .setTitle(R.string.install_unknown_sources_title)
                .setMessage(R.string.update_install_permission_needed)
                .setPositiveButton(R.string.update_open_install_settings) { _, _ ->
                    openUnknownSourcesSettings()
                }
                .setNegativeButton(R.string.annuleer, null)
                .show()
            DialogStyler.apply(dlg)
            return
        }

        lifecycleScope.launch {
            val progress = AlertDialog.Builder(this@InstellingenScherm)
                .setTitle(R.string.update_title)
                .setMessage(R.string.update_downloading)
                .setCancelable(false)
                .create()
            progress.show()
            DialogStyler.apply(progress)

            val result = AppUpdateManager.downloadAsset(this@InstellingenScherm, asset)
            progress.dismiss()

            result.onSuccess { apkFile ->
                openPackageInstaller(apkFile)
            }.onFailure { err ->
                val msg = when {
                    err.message?.contains("Checksum mismatch", ignoreCase = true) == true -> getString(R.string.update_checksum_failed)
                    err.message?.contains("APK metadata mismatch", ignoreCase = true) == true -> getString(R.string.update_apk_version_mismatch)
                    else -> getString(R.string.update_download_failed, err.message ?: "onbekende fout")
                }
                Toast.makeText(this@InstellingenScherm, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openUnknownSourcesSettings() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:$packageName".toUri())
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Kan installatie-instellingen niet openen.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPackageInstaller(apkFile: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Kon installer niet openen: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupKrentenFilterSettings() {
        val np = findViewById<NumberPicker>(R.id.npKrentenThreshold) ?: return
        np.minValue = 1
        np.maxValue = 500
        np.wrapSelectorWheel = false

        lifecycleScope.launch {
            val currentThreshold = AppDataStore.getKrentenThreshold(this@InstellingenScherm)
            np.value = currentThreshold
        }

        findViewById<MaterialButton>(R.id.btnApplyKrentenThreshold)?.setOnClickListener {
            val newVal = np.value
            lifecycleScope.launch {
                val currentInStore = AppDataStore.getKrentenThreshold(this@InstellingenScherm)
                if (newVal != currentInStore) {
                    showKrentenThresholdWarning(newVal)
                } else {
                    Toast.makeText(this@InstellingenScherm, "Drempel is al ingesteld op $newVal", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        findViewById<MaterialButton>(R.id.btnOpenKrentenMonitor)?.setOnClickListener {
            startActivity(Intent(this, com.yvesds.vt5.ai.KrentenMonitorActiviteit::class.java))
        }
    }

    private fun showKrentenThresholdWarning(newVal: Int) {
        val builder = AlertDialog.Builder(this)
            .setTitle("AI Training Vereist")
            .setMessage("Het wijzigen van de krenten-drempel vereist een nieuwe AI-training om de lijst met 'Birds of Interest' te verversen.\n\nWilt u de drempel aanpassen naar $newVal en de training nu starten?")
            .setPositiveButton("JA, START TRAINING") { dialog, _ ->
                lifecycleScope.launch {
                    AppDataStore.setKrentenThreshold(this@InstellingenScherm, newVal)
                    dialog.dismiss()
                    // Ga terug naar hoofdscherm en trigger training
                    val intent = Intent(this@InstellingenScherm, HoofdActiviteit::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    intent.putExtra(EXTRA_TRIGGER_AI_TRAINING, true)
                    startActivity(intent)
                    finish()
                }
            }
            .setNegativeButton("ANNULEREN") { dialog, _ ->
                dialog.dismiss()
                // Reset de NumberPicker naar de oude waarde
                lifecycleScope.launch {
                    val oldVal = AppDataStore.getKrentenThreshold(this@InstellingenScherm)
                    findViewById<NumberPicker>(R.id.npKrentenThreshold)?.value = oldVal
                }
            }
            .setCancelable(false)
        
        val dialog = builder.create()
        dialog.show()
        DialogStyler.apply(dialog)
    }

    private fun setupChartSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        findViewById<NumberPicker>(R.id.npChartThickness)?.apply {
            minValue = 1; maxValue = 5; value = prefs.getInt(PREF_CHART_LINE_THICKNESS, 1)
            setOnValueChangedListener { _, _, v -> prefs.edit { putInt(PREF_CHART_LINE_THICKNESS, v) } }
        }
        
        // Filter out black/very dark colors for charts to ensure visibility on black background
        val options = UiColorPrefs.getTextOptions(this).filter { colorOpt ->
            val color = colorOpt.argb
            // Simple brightness check: (R+G+B)/3 > some threshold
            (Color.red(color) + Color.green(color) + Color.blue(color)) / 3 > 30
        }
        
        setupChartColorSpinner(R.id.spChartColorWind, PREF_CHART_COLOR_WIND, ContextCompat.getColor(this, R.color.grafiek_beaufort), options)
        setupChartColorSpinner(R.id.spChartColorTrek, PREF_CHART_COLOR_TREK, ContextCompat.getColor(this, R.color.grafiek_lijnkleur), options)
        setupChartColorSpinner(R.id.spChartColorTerug, PREF_CHART_COLOR_TERUG, ContextCompat.getColor(this, R.color.grafiek_lijnkleur_terug), options)
    }

    private fun setupChartColorSpinner(id: Int, key: String, def: Int, opts: List<UiColorPrefs.ColorOption>) {
        val spinner = findViewById<Spinner>(id) ?: return
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val adapter = object : BaseAdapter() {
            override fun getCount() = opts.size
            override fun getItem(p: Int) = opts[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, v: View?, parent: ViewGroup): View {
                val tv = (v as? TextView) ?: layoutInflater.inflate(R.layout.item_color_option, parent, false) as TextView
                tv.text = opts[p].label; tv.setTextColor(opts[p].argb); tv.setBackgroundColor(Color.TRANSPARENT); return tv
            }
            override fun getDropDownView(p: Int, v: View?, parent: ViewGroup): View {
                val tv = getView(p, v, parent) as TextView; tv.setBackgroundColor(Color.parseColor("#333333")); tv.setPadding(24, 16, 24, 16); return tv
            }
        }
        spinner.adapter = adapter
        spinner.setSelection(opts.indexOfFirst { it.argb == prefs.getInt(key, def) }.coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) { prefs.edit { putInt(key, opts[p2].argb) } }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun showDisablePermissionDialog(msg: Int, onResult: (Boolean) -> Unit) {
        if (msg == 0) { onResult(true); return }
        val d = AlertDialog.Builder(this).setTitle(R.string.perm_disable_title).setMessage(msg)
            .setPositiveButton(R.string.perm_disable_confirm) { _, _ -> onResult(true) }
            .setNegativeButton(R.string.perm_disable_cancel) { _, _ -> onResult(false) }.setCancelable(false).create()
        DialogStyler.apply(d); d.show()
    }

    private fun setupDatabaseMirroringButton() {
        findViewById<MaterialButton>(R.id.btnMirrorDb)?.setOnClickListener {
            mirrorDatabaseToSaf()
        }
    }

    private fun setupBirdNetConfigButton() {
        findViewById<MaterialButton>(R.id.btnOpenBirdNetConfig)?.setOnClickListener {
            val intent = Intent(this, com.yvesds.vt5.features.birdnet.BirdNetConfigActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupDefaultTelpostAndTypeTelling() {
        val spTelpost = findViewById<Spinner>(R.id.spinnerDefaultTelpost) ?: return
        val spTypeTelling = findViewById<Spinner>(R.id.spinnerDefaultTypeTelling) ?: return

        lifecycleScope.launch {
            val repo = ServerDataRepository(this@InstellingenScherm)
            val snapshot = repo.loadMinimalData()

            // 1. Telposten
            val sites = snapshot.sitesById.values.sortedBy { it.telpostnaam.lowercase(Locale.getDefault()) }
            val siteLabels = mutableListOf("Geen (altijd handmatig kiezen)").apply {
                addAll(sites.map { it.telpostnaam })
            }
            val siteIds = mutableListOf("").apply {
                addAll(sites.map { it.telpostid })
            }

            val telpostAdapter = ArrayAdapter(this@InstellingenScherm, android.R.layout.simple_spinner_item, siteLabels)
            telpostAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spTelpost.adapter = telpostAdapter

            val currentDefaultTelpost = AppDataStore.getDefaultTelpost(this@InstellingenScherm)
            val initialTelpostIdx = siteIds.indexOf(currentDefaultTelpost).coerceAtLeast(0)
            spTelpost.setSelection(initialTelpostIdx)

            spTelpost.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selectedId = siteIds.getOrNull(position).takeIf { !it.isNullOrBlank() }
                    lifecycleScope.launch {
                        AppDataStore.setDefaultTelpost(this@InstellingenScherm, selectedId)
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            // 2. Type Telling
            val allCodes = snapshot.codesByCategory["typetelling_trek"] ?: emptyList()
            val filteredCodes = allCodes.filterNot { c ->
                val key = c.key ?: ""
                key.contains("_sound") || key.contains("_ringen") || key.startsWith("samplingrate_") || key.startsWith("gain_") || key.startsWith("verstoring_")
            }
            val typeLabels = mutableListOf("Geen (altijd handmatig kiezen)").apply {
                addAll(filteredCodes.map { it.text })
            }
            val typeValues = mutableListOf("").apply {
                addAll(filteredCodes.map { it.value })
            }

            val typeAdapter = ArrayAdapter(this@InstellingenScherm, android.R.layout.simple_spinner_item, typeLabels)
            typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spTypeTelling.adapter = typeAdapter

            val currentDefaultType = AppDataStore.getDefaultTypeTelling(this@InstellingenScherm)
            val initialTypeIdx = typeValues.indexOf(currentDefaultType).coerceAtLeast(0)
            spTypeTelling.setSelection(initialTypeIdx)

            spTypeTelling.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selectedValue = typeValues.getOrNull(position).takeIf { !it.isNullOrBlank() }
                    lifecycleScope.launch {
                        AppDataStore.setDefaultTypeTelling(this@InstellingenScherm, selectedValue)
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }

    private fun setupCollapsibleSections() {
        val sections = listOf(
            Triple(R.id.headerDefault, R.id.contentDefault, true),
            Triple(R.id.headerText, R.id.contentText, false),
            Triple(R.id.headerTiles, R.id.contentTiles, false),
            Triple(R.id.headerColors, R.id.contentColors, false),
            Triple(R.id.headerSorting, R.id.contentSorting, false),
            Triple(R.id.headerAi, R.id.contentAi, false),
            Triple(R.id.headerUpdates, R.id.contentUpdates, false),
            Triple(R.id.headerPermissions, R.id.contentPermissions, false),
            Triple(R.id.headerExtra, R.id.contentExtra, false),
            Triple(R.id.headerTrektellen, R.id.contentTrektellen, false)
        )

        for ((headerId, contentId, startOpen) in sections) {
            val header = findViewById<View>(headerId) ?: continue
            val content = findViewById<View>(contentId) ?: continue
            
            content.visibility = if (startOpen) View.VISIBLE else View.GONE
            
            val arrow = (header as? ViewGroup)?.let { vg ->
                (0 until vg.childCount).map { vg.getChildAt(it) }.filterIsInstance<TextView>().lastOrNull()
            }
            arrow?.text = if (startOpen) "▲" else "▼"

            header.setOnClickListener {
                val isVisible = content.visibility == View.VISIBLE
                content.visibility = if (isVisible) View.GONE else View.VISIBLE
                arrow?.text = if (isVisible) "▼" else "▲"
            }
        }
    }

    private fun setupTrektellenAccountSettings() {
        val creds = CredentialsStore(this)
        val authManager = ServerAuthenticationManager(this)
        val downloadManager = ServerDataDownloadManager(this)
        val saf = SaFStorageHelper(this)
        val safManager = InstallationSafManager(this, saf) {}
        val aliasManager = AliasIndexManager(this, saf)
        val dialogManager = InstallationDialogManager(this)

        val etLogin = findViewById<TextInputEditText>(R.id.etLogin)
        val etPass = findViewById<TextInputEditText>(R.id.etPass)
        val btnWis = findViewById<MaterialButton>(R.id.btnWis)
        val btnBewaar = findViewById<MaterialButton>(R.id.btnBewaar)
        val btnLoginTest = findViewById<MaterialButton>(R.id.btnLoginTest)
        val btnDownloadJsons = findViewById<MaterialButton>(R.id.btnDownloadJsons)

        etLogin?.setText(creds.getUsername().orEmpty())
        etPass?.setText(creds.getPassword().orEmpty())

        btnWis?.setOnClickListener {
            try {
                creds.clear()
                etLogin?.setText("")
                etPass?.setText("")
                Toast.makeText(this, getString(R.string.msg_credentials_gewist), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Fout bij wissen: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnBewaar?.setOnClickListener {
            val username = etLogin?.text?.toString().orEmpty().trim()
            val password = etPass?.text?.toString().orEmpty()
            try {
                creds.save(username, password)
                Toast.makeText(this, getString(R.string.msg_credentials_opgeslagen), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Fout bij opslaan: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        fun getCredsOrWarn(): Pair<String, String>? {
            val u = etLogin?.text?.toString().orEmpty().trim()
            val p = etPass?.text?.toString().orEmpty()
            if (u.isBlank() || p.isBlank()) {
                Toast.makeText(this, getString(R.string.msg_vul_login_eerst), Toast.LENGTH_LONG).show()
                return null
            }
            return u to p
        }

        btnLoginTest?.setOnClickListener {
            val (u, p) = getCredsOrWarn() ?: return@setOnClickListener
            lifecycleScope.launch {
                val pd = dialogManager.showProgress("Login testen...")
                runCatching {
                    val res = authManager.testLogin(u, p)
                    pd.dismiss()
                    if (res is ServerAuthenticationManager.AuthResult.Success) {
                        dialogManager.showInfo(getString(R.string.dlg_titel_result), res.response)
                        creds.save(u, p)
                        authManager.saveFullnameToPreferences(res.response)
                        lifecycleScope.launch(Dispatchers.IO) {
                            runCatching {
                                val sDir = safManager.getSubdirectory("serverdata", createIfMissing = true)
                                if (sDir != null) authManager.saveCheckUserResponse(sDir, res.response)
                            }
                        }
                    } else if (res is ServerAuthenticationManager.AuthResult.Failure) {
                        dialogManager.showError("Login mislukt", res.error)
                    }
                }.onFailure {
                    pd.dismiss()
                    dialogManager.showError("Fout bij login test", it.message ?: "Onbekende fout")
                }
            }
        }

        btnDownloadJsons?.setOnClickListener {
            val (u, p) = getCredsOrWarn() ?: return@setOnClickListener
            val vt5Dir = safManager.getVt5Directory()
            if (vt5Dir == null) {
                Toast.makeText(this, getString(R.string.msg_kies_documents_eerst), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val serverdataDir = safManager.getSubdirectory("serverdata", createIfMissing = true)
            val binariesDir = safManager.getSubdirectory("binaries", createIfMissing = true)

            lifecycleScope.launch {
                val pd = dialogManager.showProgress("JSONs downloaden...")
                runCatching {
                    val result = downloadManager.downloadAllServerData(serverdataDir, binariesDir, u, p) { msg ->
                        dialogManager.updateProgress(pd, msg)
                    }
                    pd.dismiss()
                    if (result is ServerDataDownloadManager.DownloadResult.Success) {
                        if (aliasManager.needsRegeneration(vt5Dir)) {
                            aliasManager.regenerateIndexIfNeeded(vt5Dir, authManager.generateIsoTimestamp()) { _ -> }
                        }
                        dialogManager.showInfo(getString(R.string.dlg_titel_result), result.messages.joinToString("\n"))
                    } else if (result is ServerDataDownloadManager.DownloadResult.Failure) {
                        dialogManager.showError("Fout bij downloaden", result.error)
                    }
                }.onFailure {
                    pd.dismiss()
                    dialogManager.showError("Fout bij downloaden", it.message ?: "Onbekende fout")
                }
            }
        }
    }

    private fun mirrorDatabaseToSaf() {
        val saf = SaFStorageHelper(this)
        val rootUri = saf.getRootUri()
        if (rootUri == null) {
            Toast.makeText(this, "Selecteer eerst een SAF map in de installatie", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Bepaal bronlocatie (gelijk aan VoiceTallyDatabase.getDatabaseFile)
                val externalRoot = getExternalFilesDir(null) ?: filesDir
                val vt5RootLocal = File(externalRoot, "VT5")
                val dbDirLocal = File(vt5RootLocal, "database")
                val dbName = "voicetally.db"
                
                val filesToCopy = listOf(dbName, "$dbName-shm", "$dbName-wal")

                // 2. Zoek/maak doelmap in SAF
                val vt5DirSaf = saf.getVt5DirIfExists() ?: run {
                    withContext(Dispatchers.Main) { Toast.makeText(this@InstellingenScherm, "VT5 map niet gevonden in SAF", Toast.LENGTH_SHORT).show() }
                    return@launch
                }
                
                val targetDirSaf = saf.findOrCreateDirectory(vt5DirSaf, "database") ?: run {
                    withContext(Dispatchers.Main) { Toast.makeText(this@InstellingenScherm, "Kon database map niet maken in SAF", Toast.LENGTH_SHORT).show() }
                    return@launch
                }

                var count = 0
                for (fileName in filesToCopy) {
                    val srcFile = File(dbDirLocal, fileName)
                    if (srcFile.exists()) {
                        val mimeType = "application/octet-stream"
                        val targetFile = targetDirSaf.findFile(fileName) ?: targetDirSaf.createFile(mimeType, fileName)
                        
                        if (targetFile != null) {
                            contentResolver.openOutputStream(targetFile.uri, "wt")?.use { out ->
                                srcFile.inputStream().use { it.copyTo(out) }
                            }
                            count++
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@InstellingenScherm, "$count database bestanden gespiegeld naar SAF (VT5/database)", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@InstellingenScherm, "Fout bij spiegelen: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
