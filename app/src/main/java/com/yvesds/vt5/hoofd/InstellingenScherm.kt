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
import android.widget.BaseAdapter
import android.widget.NumberPicker
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
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.yvesds.vt5.core.ui.DialogStyler
import com.yvesds.vt5.core.ui.UiColorPrefs
import kotlinx.coroutines.launch

/**
 * InstellingenScherm - Scherm voor app-instellingen met volledige tablet-synchronisatie.
 */
class InstellingenScherm : AppCompatActivity() {
    
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
            setupColorSpinners()
            setupPartialsTextColorSpinner()
            setupUnmatchedPartialsTextColorSpinner()
            setupFinalsTextColorSpinner()
            setupMaxFavorietenButtons()
            setupPermissionAcknowledgements()
            setupAiSettings()
            setupChartSettings()
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
}
