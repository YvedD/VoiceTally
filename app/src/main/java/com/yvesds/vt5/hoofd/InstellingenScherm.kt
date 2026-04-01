package com.yvesds.vt5.hoofd

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
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
import com.yvesds.vt5.core.ui.DialogStyler
import com.yvesds.vt5.core.ui.UiColorPrefs

/**
 * InstellingenScherm - Scherm voor app-instellingen
 * 
 * Biedt instellingen voor:
 * - Lettergrootte van logregels (partial/final) in TellingScherm
 * - Lettergrootte van tegels (soortnaam + aantallen) in TellingScherm
 * 
 * Instellingen worden opgeslagen via SharedPreferences voor gebruik doorheen de app.
 */
class InstellingenScherm : AppCompatActivity() {
    
    companion object {
        private const val PREFS_NAME = "vt5_prefs"
        const val PREF_LETTERGROOTTE_TEGELS_SP = "pref_lettergrootte_tegels_sp"
        const val PREF_PARTIALS_TEXT_COLOR = "pref_partials_text_color"
        const val PREF_FINALS_TEXT_COLOR = "pref_finals_text_color"
        const val PREF_LOG_TEXT_COLOR = "pref_log_text_color"
        const val PREF_PARTIALS_TEXT_SIZE_SP = "pref_partials_text_size_sp"
        const val PREF_FINALS_TEXT_SIZE_SP = "pref_finals_text_size_sp"
        const val PREF_TILE_DOUBLE_TAP_INCREMENT = "pref_tile_double_tap_increment"
        const val PREF_TILE_TAP_GROUP_WINDOW_SECONDS = "pref_tile_tap_group_window_seconds"

        const val PREF_PERM_AUDIO_ACK = "pref_perm_audio_ack"
        const val PREF_PERM_SAF_ACK = "pref_perm_saf_ack"
        const val PREF_PERM_LOCATION_ACK = "pref_perm_location_ack"

        // Lettergrootte bereik in sp
        const val MIN_LETTERGROOTTE_SP = 10
        const val MAX_LETTERGROOTTE_SP = 30
        const val DEFAULT_LETTERGROOTTE_SP = 17

        const val PREF_MAX_FAVORIETEN = "pref_max_favoriete_soorten"
        const val PREF_SILENT_AUTO_UPLOAD_INTERVAL_MINUTES = "pref_silent_auto_upload_interval_minutes"
        const val MAX_FAVORIETEN_ALL = -1
        const val DEFAULT_MAX_FAVORIETEN = 30
        const val DEFAULT_TILE_DOUBLE_TAP_INCREMENT = 10
        const val DEFAULT_TILE_TAP_GROUP_WINDOW_SECONDS = 5
        const val DEFAULT_SILENT_AUTO_UPLOAD_INTERVAL_MINUTES = 0

        private val TILE_DOUBLE_TAP_OPTIONS = listOf(5, 10, 50, 100)
        private val TILE_TAP_GROUP_WINDOW_OPTIONS = listOf(2, 3, 5, 8, 10, 12, 15)
        private val SILENT_AUTO_UPLOAD_OPTIONS = listOf(0, 5, 10, 15, 30)

        /**
         * Haal de huidige lettergrootte voor partials op uit SharedPreferences.
         */
        fun getPartialsTextSizeSp(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getInt(PREF_PARTIALS_TEXT_SIZE_SP, DEFAULT_LETTERGROOTTE_SP)
        }

        /**
         * Haal de huidige lettergrootte voor finals op uit SharedPreferences.
         */
        fun getFinalsTextSizeSp(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getInt(PREF_FINALS_TEXT_SIZE_SP, DEFAULT_LETTERGROOTTE_SP)
        }

        /**
         * Haal de huidige lettergrootte voor tegels op uit SharedPreferences.
         */
        fun getLettergroottTegelsSp(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getInt(PREF_LETTERGROOTTE_TEGELS_SP, DEFAULT_LETTERGROOTTE_SP)
        }

        /**
         * Haal de huidige tekstkleur voor partials logregels op uit SharedPreferences.
         */
        fun getPartialsTextColor(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getInt(PREF_PARTIALS_TEXT_COLOR, android.graphics.Color.WHITE)
        }

        /**
         * Haal de huidige tekstkleur voor finals logregels op uit SharedPreferences.
         */
        fun getFinalsTextColor(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getInt(PREF_FINALS_TEXT_COLOR, android.graphics.Color.WHITE)
        }

        @Suppress("unused")
        private const val _keepGetMaxFavorieten = 0

        /**
         * Haal het huidige maximum aantal favorieten op uit SharedPreferences.
         */
        fun getMaxFavorieten(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getInt(PREF_MAX_FAVORIETEN, DEFAULT_MAX_FAVORIETEN)
        }

        fun getTileDoubleTapIncrement(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val stored = prefs.getInt(PREF_TILE_DOUBLE_TAP_INCREMENT, DEFAULT_TILE_DOUBLE_TAP_INCREMENT)
            return stored.takeIf { it in TILE_DOUBLE_TAP_OPTIONS } ?: DEFAULT_TILE_DOUBLE_TAP_INCREMENT
        }

        fun getTileTapGroupWindowSeconds(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val stored = prefs.getInt(
                PREF_TILE_TAP_GROUP_WINDOW_SECONDS,
                DEFAULT_TILE_TAP_GROUP_WINDOW_SECONDS
            )
            return stored.takeIf { it in TILE_TAP_GROUP_WINDOW_OPTIONS } ?: DEFAULT_TILE_TAP_GROUP_WINDOW_SECONDS
        }

        fun getSilentAutoUploadIntervalMinutes(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val stored = prefs.getInt(
                PREF_SILENT_AUTO_UPLOAD_INTERVAL_MINUTES,
                DEFAULT_SILENT_AUTO_UPLOAD_INTERVAL_MINUTES
            )
            return stored.takeIf { it in SILENT_AUTO_UPLOAD_OPTIONS } ?: DEFAULT_SILENT_AUTO_UPLOAD_INTERVAL_MINUTES
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
            setupSilentAutoUploadButtons()
            setupColorSpinners()
            setupPartialsTextColorSpinner()
            setupFinalsTextColorSpinner()
            setupMaxFavorietenButtons()
            setupPermissionAcknowledgements()
        } catch (t: Throwable) {
            // Fail-safe: avoid hard crash to background when a view/id mismatch occurs.
            android.util.Log.e("InstellingenScherm", "Instellingen init failed: ${t.message}", t)
            android.widget.Toast.makeText(this, "Fout in instellingen-scherm: ${t.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun ensureLogTextColorDefaults() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (!prefs.contains(PREF_PARTIALS_TEXT_COLOR)) {
            prefs.edit { putInt(PREF_PARTIALS_TEXT_COLOR, android.graphics.Color.WHITE) }
        }
        if (!prefs.contains(PREF_FINALS_TEXT_COLOR)) {
            prefs.edit { putInt(PREF_FINALS_TEXT_COLOR, android.graphics.Color.WHITE) }
        }
    }

    private fun setupTerugKnop() {
        val btnTerug = findViewById<MaterialButton>(R.id.btnTerug)
        btnTerug.setOnClickListener {
            finish()
        }
    }
    
    private fun setupLettergrootteNumberPickers() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // NumberPicker voor partials
        val npPartials = findViewById<NumberPicker>(R.id.npLettergroottePartials)
        npPartials.minValue = MIN_LETTERGROOTTE_SP
        npPartials.maxValue = MAX_LETTERGROOTTE_SP
        npPartials.wrapSelectorWheel = false
        npPartials.value = prefs.getInt(PREF_PARTIALS_TEXT_SIZE_SP, DEFAULT_LETTERGROOTTE_SP)
            .coerceIn(MIN_LETTERGROOTTE_SP, MAX_LETTERGROOTTE_SP)
        npPartials.setOnValueChangedListener { _, _, newVal ->
            prefs.edit { putInt(PREF_PARTIALS_TEXT_SIZE_SP, newVal) }
        }

        // NumberPicker voor finals
        val npFinals = findViewById<NumberPicker>(R.id.npLettergrootteFinals)
        npFinals.minValue = MIN_LETTERGROOTTE_SP
        npFinals.maxValue = MAX_LETTERGROOTTE_SP
        npFinals.wrapSelectorWheel = false
        npFinals.value = prefs.getInt(PREF_FINALS_TEXT_SIZE_SP, DEFAULT_LETTERGROOTTE_SP)
            .coerceIn(MIN_LETTERGROOTTE_SP, MAX_LETTERGROOTTE_SP)
        npFinals.setOnValueChangedListener { _, _, newVal ->
            prefs.edit { putInt(PREF_FINALS_TEXT_SIZE_SP, newVal) }
        }

        // NumberPicker voor tegels
        val npTegels = findViewById<NumberPicker>(R.id.npLettergrootteTegels)
        npTegels.minValue = MIN_LETTERGROOTTE_SP
        npTegels.maxValue = MAX_LETTERGROOTTE_SP
        npTegels.wrapSelectorWheel = false
        npTegels.value = prefs.getInt(PREF_LETTERGROOTTE_TEGELS_SP, DEFAULT_LETTERGROOTTE_SP)
            .coerceIn(MIN_LETTERGROOTTE_SP, MAX_LETTERGROOTTE_SP)
        
        npTegels.setOnValueChangedListener { _, _, newVal ->
            prefs.edit {
                putInt(PREF_LETTERGROOTTE_TEGELS_SP, newVal)
            }
        }
    }

    private fun setupColorSpinners() {
        val spBg = findViewById<Spinner>(R.id.spBackgroundColor)
        val spText = findViewById<Spinner>(R.id.spTextColor)

        val bgOptions = UiColorPrefs.getBackgroundOptions(this)
        val textOptions = UiColorPrefs.getTextOptions(this)

        fun buildAdapter(
            items: List<UiColorPrefs.ColorOption>,
            getBg: () -> Int,
            getText: () -> Int
        ): BaseAdapter {
            return object : BaseAdapter() {
                override fun getCount(): Int = items.size
                override fun getItem(position: Int): Any = items[position]
                override fun getItemId(position: Int): Long = position.toLong()

                private fun bind(tv: TextView, position: Int) {
                    val opt = items[position]
                    tv.text = opt.label
                    tv.setBackgroundColor(getBg())
                    tv.setTextColor(getText())
                    tv.setPadding(24, 18, 24, 18)
                }

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val tv = (convertView as? TextView)
                        ?: layoutInflater.inflate(R.layout.item_color_option, parent, false) as TextView
                    bind(tv, position)
                    return tv
                }

                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val tv = (convertView as? TextView)
                        ?: layoutInflater.inflate(R.layout.item_color_option, parent, false) as TextView
                    bind(tv, position)
                    return tv
                }
            }
        }

        // Current selection holders (so each spinner can reflect the combined choice)
        var selectedBg = UiColorPrefs.getBackgroundColor(this)
        var selectedText = UiColorPrefs.getTextColor(this)

        val bgAdapter = buildAdapter(bgOptions, getBg = { selectedBg }, getText = { selectedText })
        val textAdapter = buildAdapter(textOptions, getBg = { selectedBg }, getText = { selectedText })

        spBg.adapter = bgAdapter
        spText.adapter = textAdapter

        // Preselect from prefs
        spBg.setSelection(bgOptions.indexOfFirst { it.argb == selectedBg }.takeIf { it >= 0 } ?: 0)
        spText.setSelection(textOptions.indexOfFirst { it.argb == selectedText }.takeIf { it >= 0 } ?: 0)

        spBg.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedBg = bgOptions[position].argb
                UiColorPrefs.setBackgroundColor(this@InstellingenScherm, selectedBg)

                // Refresh both spinners so each item reflects the combined colors
                bgAdapter.notifyDataSetChanged()
                textAdapter.notifyDataSetChanged()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        spText.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedText = textOptions[position].argb
                UiColorPrefs.setTextColor(this@InstellingenScherm, selectedText)

                // Refresh both spinners so each item reflects the combined colors
                bgAdapter.notifyDataSetChanged()
                textAdapter.notifyDataSetChanged()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupDoubleTapIncrementButtons() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val btn5 = findViewById<MaterialButton>(R.id.btnDoubleTap5)
        val btn10 = findViewById<MaterialButton>(R.id.btnDoubleTap10)
        val btn50 = findViewById<MaterialButton>(R.id.btnDoubleTap50)
        val btn100 = findViewById<MaterialButton>(R.id.btnDoubleTap100)

        val allButtons = listOf(btn5, btn10, btn50, btn100)

        fun applySelection(value: Int) {
            prefs.edit { putInt(PREF_TILE_DOUBLE_TAP_INCREMENT, value) }
            allButtons.forEach { it.isChecked = false }
            when (value) {
                5 -> btn5.isChecked = true
                50 -> btn50.isChecked = true
                100 -> btn100.isChecked = true
                else -> btn10.isChecked = true
            }
        }

        applySelection(getTileDoubleTapIncrement(this))

        btn5.setOnClickListener { applySelection(5) }
        btn10.setOnClickListener { applySelection(10) }
        btn50.setOnClickListener { applySelection(50) }
        btn100.setOnClickListener { applySelection(100) }
    }

    private fun setupTileTapGroupWindowButtons() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val btn2 = findViewById<MaterialButton>(R.id.btnPendingTiles2)
        val btn3 = findViewById<MaterialButton>(R.id.btnPendingTiles3)
        val btn5 = findViewById<MaterialButton>(R.id.btnPendingTiles5)
        val btn8 = findViewById<MaterialButton>(R.id.btnPendingTiles8)
        val btn10 = findViewById<MaterialButton>(R.id.btnPendingTiles10)
        val btn12 = findViewById<MaterialButton>(R.id.btnPendingTiles12)
        val btn15 = findViewById<MaterialButton>(R.id.btnPendingTiles15)

        val allButtons = listOf(btn2, btn3, btn5, btn8, btn10, btn12, btn15)

        fun applySelection(value: Int) {
            prefs.edit { putInt(PREF_TILE_TAP_GROUP_WINDOW_SECONDS, value) }
            allButtons.forEach { it.isChecked = false }
            when (value) {
                2 -> btn2.isChecked = true
                3 -> btn3.isChecked = true
                5 -> btn5.isChecked = true
                8 -> btn8.isChecked = true
                10 -> btn10.isChecked = true
                12 -> btn12.isChecked = true
                15 -> btn15.isChecked = true
                else -> btn5.isChecked = true
            }
        }

        applySelection(getTileTapGroupWindowSeconds(this))

        btn2.setOnClickListener { applySelection(2) }
        btn3.setOnClickListener { applySelection(3) }
        btn5.setOnClickListener { applySelection(5) }
        btn8.setOnClickListener { applySelection(8) }
        btn10.setOnClickListener { applySelection(10) }
        btn12.setOnClickListener { applySelection(12) }
        btn15.setOnClickListener { applySelection(15) }
    }

    private fun setupSilentAutoUploadButtons() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val btnUit = findViewById<MaterialButton?>(R.id.btnSilentUploadUit)
        val btn5 = findViewById<MaterialButton?>(R.id.btnSilentUpload5)
        val btn10 = findViewById<MaterialButton?>(R.id.btnSilentUpload10)
        val btn15 = findViewById<MaterialButton?>(R.id.btnSilentUpload15)
        val btn30 = findViewById<MaterialButton?>(R.id.btnSilentUpload30)

        val allButtons = listOfNotNull(btnUit, btn5, btn10, btn15, btn30)
        if (allButtons.isEmpty()) return

        fun applySelection(value: Int) {
            prefs.edit { putInt(PREF_SILENT_AUTO_UPLOAD_INTERVAL_MINUTES, value) }
            allButtons.forEach { it.isChecked = false }
            when (value) {
                5 -> btn5?.isChecked = true
                10 -> btn10?.isChecked = true
                15 -> btn15?.isChecked = true
                30 -> btn30?.isChecked = true
                else -> btnUit?.isChecked = true
            }
        }

        applySelection(getSilentAutoUploadIntervalMinutes(this))

        btnUit?.setOnClickListener { applySelection(0) }
        btn5?.setOnClickListener { applySelection(5) }
        btn10?.setOnClickListener { applySelection(10) }
        btn15?.setOnClickListener { applySelection(15) }
        btn30?.setOnClickListener { applySelection(30) }
    }

    private fun setupPartialsTextColorSpinner() {
        setupLogColorSpinner(
            spinnerId = R.id.spPartialsTextColor,
            prefKey = PREF_PARTIALS_TEXT_COLOR,
            defaultColor = android.graphics.Color.WHITE
        )
    }

    private fun setupFinalsTextColorSpinner() {
        setupLogColorSpinner(
            spinnerId = R.id.spFinalsTextColor,
            prefKey = PREF_FINALS_TEXT_COLOR,
            defaultColor = android.graphics.Color.WHITE
        )
    }

    private fun setupLogColorSpinner(spinnerId: Int, prefKey: String, defaultColor: Int) {
        val spLogText = findViewById<Spinner>(spinnerId)
        val textOptions = UiColorPrefs.getTextOptions(this)

        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = textOptions.size
            override fun getItem(position: Int): Any = textOptions[position]
            override fun getItemId(position: Int): Long = position.toLong()

            private fun bind(tv: TextView, position: Int) {
                val opt = textOptions[position]
                tv.text = opt.label
                tv.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                tv.setTextColor(opt.argb)
                tv.setPadding(24, 18, 24, 18)
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = (convertView as? TextView)
                    ?: layoutInflater.inflate(R.layout.item_color_option, parent, false) as TextView
                bind(tv, position)
                return tv
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = (convertView as? TextView)
                    ?: layoutInflater.inflate(R.layout.item_color_option, parent, false) as TextView
                bind(tv, position)
                return tv
            }
        }

        spLogText.adapter = adapter

        val current = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getInt(prefKey, defaultColor)
        val initial = textOptions.indexOfFirst { it.argb == current }.takeIf { it >= 0 } ?: 0
        spLogText.setSelection(initial)

        spLogText.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = textOptions[position].argb
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
                    putInt(prefKey, selected)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupMaxFavorietenButtons() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        fun findButton(idName: String): MaterialButton? {
            val id = resources.getIdentifier(idName, "id", packageName)
            if (id == 0) return null
            return findViewById(id)
        }

        val btn15 = findButton("btnFav15")
        val btn20 = findButton("btnFav20")
        val btn25 = findButton("btnFav25")
        val btn30 = findButton("btnFav30")
        val btn35 = findButton("btnFav35")
        val btn40 = findButton("btnFav40")
        val btn75 = findButton("btnFav75")
        val btnAll = findButton("btnFavAll")

        val allButtons = listOfNotNull(btn15, btn20, btn25, btn30, btn35, btn40, btn75, btnAll)
        if (allButtons.isEmpty()) return

        fun applySelection(value: Int) {
            prefs.edit { putInt(PREF_MAX_FAVORIETEN, value) }
            allButtons.forEach { it.isChecked = false }
            when (value) {
                15 -> btn15?.isChecked = true
                20 -> btn20?.isChecked = true
                25 -> btn25?.isChecked = true
                30 -> btn30?.isChecked = true
                35 -> btn35?.isChecked = true
                40 -> btn40?.isChecked = true
                75 -> btn75?.isChecked = true
                else -> btnAll?.isChecked = true
            }
        }

        val current = prefs.getInt(PREF_MAX_FAVORIETEN, DEFAULT_MAX_FAVORIETEN)
        applySelection(current)

        btn15?.setOnClickListener { applySelection(15) }
        btn20?.setOnClickListener { applySelection(20) }
        btn25?.setOnClickListener { applySelection(25) }
        btn30?.setOnClickListener { applySelection(30) }
        btn35?.setOnClickListener { applySelection(35) }
        btn40?.setOnClickListener { applySelection(40) }
        btn75?.setOnClickListener { applySelection(75) }
        btnAll?.setOnClickListener { applySelection(MAX_FAVORIETEN_ALL) }
    }

    private fun setupPermissionAcknowledgements() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val hasSaf = SaFStorageHelper(this).getRootUri() != null

        bindPermCheckBox(R.id.cbPermAudio, PREF_PERM_AUDIO_ACK, prefs, hasAudio, R.string.perm_disable_message_audio)
        bindPermCheckBox(R.id.cbPermSaf, PREF_PERM_SAF_ACK, prefs, hasSaf, R.string.perm_disable_message_saf)
        bindPermCheckBox(R.id.cbPermLocation, PREF_PERM_LOCATION_ACK, prefs, hasLocation, R.string.perm_disable_message_location)
    }

    private fun bindPermCheckBox(
        id: Int,
        key: String,
        prefs: SharedPreferences,
        actualGranted: Boolean,
        disableMessageRes: Int
    ) {
        val cb = findViewById<MaterialCheckBox>(id)
        var suppress = false
        val stored = prefs.getBoolean(key, false)
        val effective = stored || actualGranted
        if (effective && !stored) {
            prefs.edit { putBoolean(key, true) }
        }
        suppress = true
        cb.isChecked = effective
        suppress = false
        cb.setOnCheckedChangeListener { _, isChecked ->
            if (suppress) return@setOnCheckedChangeListener
            if (!isChecked) {
                showDisablePermissionDialog(disableMessageRes) { confirmed ->
                    if (confirmed) {
                        prefs.edit { putBoolean(key, false) }
                    } else {
                        suppress = true
                        cb.isChecked = true
                        suppress = false
                    }
                }
            } else {
                prefs.edit { putBoolean(key, true) }
            }
        }
    }

    private fun showDisablePermissionDialog(messageRes: Int, onResult: (Boolean) -> Unit) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.perm_disable_title)
            .setMessage(messageRes)
            .setPositiveButton(R.string.perm_disable_confirm) { _, _ ->
                onResult(true)
            }
            .setNegativeButton(R.string.perm_disable_cancel) { _, _ ->
                onResult(false)
            }
            .setCancelable(false)
            .show()

        DialogStyler.apply(dialog)
    }
}
