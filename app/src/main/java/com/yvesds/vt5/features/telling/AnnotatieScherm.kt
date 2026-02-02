package com.yvesds.vt5.features.telling

import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.Window
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatToggleButton
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.yvesds.vt5.R
import com.yvesds.vt5.core.ui.CompassNeedleView
import com.yvesds.vt5.core.ui.ProgressDialogHelper
import com.yvesds.vt5.features.annotation.AnnotationOption
import com.yvesds.vt5.features.annotation.AnnotationsManager
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import com.yvesds.vt5.features.serverdata.model.SiteItem
import com.yvesds.vt5.utils.SeizoenUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * AnnotatieScherm
 *
 * - Laadt annotations.json in geheugen via AnnotationsManager.loadCache(...)
 * - Vult vooraf getekende ToggleButtons in activity_annotatie.xml met de "tekst" uit annotations.json,
 *   plaatst het corresponderende AnnotationOption object in btn.tag, en handhaaft single-select per groep.
 * - Als er minder opties zijn dan knoppen, worden overtollige buttons verborgen.
 * - OK retourneert een JSON-map { storeKey -> waarde } via EXTRA_ANNOTATIONS_JSON.
 * - Voor compatibiliteit met oudere callers (bv. TellingScherm) vult het resultaat ook:
 *     EXTRA_TEXT -> een korte samenvattende tekst (labels, komma-gescheiden)
 *     EXTRA_TS   -> timestamp in seconden (Long)
 */
class AnnotatieScherm : AppCompatActivity() {

    companion object {
        const val EXTRA_ANNOTATIONS_JSON = "extra_annotations_json"

        // Legacy keys expected by older code paths (keeps TellingScherm compile+runtime compatible)
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_TS = "extra_ts"

        // Keys for prefilling count fields from existing record values
        // These are the raw record values, which need season-based mapping to UI fields
        const val EXTRA_RECORD_AANTAL = "extra_record_aantal"           // record.aantal (main direction)
        const val EXTRA_RECORD_AANTALTERUG = "extra_record_aantalterug" // record.aantalterug (opposite direction)
        const val EXTRA_LOKAAL = "extra_lokaal"
        
        // Key for telpostid to get site-specific direction labels
        const val EXTRA_TELPOSTID = "extra_telpostid"

        private const val PREF_ANNOT_DENSITY = "pref_annot_density"
    }

    private enum class DensityMode(val key: String, val factor: Float) {
        STANDARD("standard", 1.0f),
        COMPACT("compact", 0.85f),
        SUPERCOMPACT("supercompact", 0.72f);

        companion object {
            fun fromKey(key: String?): DensityMode {
                return values().firstOrNull { it.key == key } ?: STANDARD
            }
        }
    }

    private data class DensityBaseline(
        val textSizePx: Float?,
        val paddingLeft: Int,
        val paddingTop: Int,
        val paddingRight: Int,
        val paddingBottom: Int,
        val minWidth: Int,
        val minHeight: Int,
        val marginLeft: Int,
        val marginTop: Int,
        val marginRight: Int,
        val marginBottom: Int
    )

    private val json = Json { prettyPrint = false }

    // Map groupName -> list of ToggleButtons
    private val groupButtons = mutableMapOf<String, MutableList<AppCompatToggleButton>>()

    // Reference to remarks EditText for location/height auto-tagging
    private lateinit var etOpmerkingen: EditText

    // Selected sighting direction (code from windoms, e.g., "N", "NNE", etc.)
    private var selectedSightingDirection: String? = null
    
    // Current site information for direction labels
    private var currentSite: SiteItem? = null

    // Track active compass dialog and view for proper sensor cleanup
    private var activeCompassDialog: Dialog? = null
    private var activeCompassNeedleView: CompassNeedleView? = null

    // Direction button data: Dutch label -> uppercase English code for sightingdirection field (matches codes.json sightingdirection)
    private val directionLabelToCode = mapOf(
        "N" to "N", "NNO" to "NNE", "NO" to "NE", "ONO" to "ENE",
        "O" to "E", "OZO" to "ESE", "ZO" to "SE", "ZZO" to "SSE",
        "Z" to "S", "ZZW" to "SSW", "ZW" to "SW", "WZW" to "WSW",
        "W" to "W", "WNW" to "WNW", "NW" to "NW", "NNW" to "NNW"
    )

    // Direction button IDs for compass dialog
    private val directionButtonIds = listOf(
        R.id.btn_dir_n, R.id.btn_dir_nno, R.id.btn_dir_no, R.id.btn_dir_ono,
        R.id.btn_dir_o, R.id.btn_dir_ozo, R.id.btn_dir_zo, R.id.btn_dir_zzo,
        R.id.btn_dir_z, R.id.btn_dir_zzw, R.id.btn_dir_zw, R.id.btn_dir_wzw,
        R.id.btn_dir_w, R.id.btn_dir_wnw, R.id.btn_dir_nw, R.id.btn_dir_nnw
    )

    // Pre-drawn button IDs per column (layout contains these)
    private val leeftijdBtnIds = listOf(
        R.id.btn_leeftijd_1, R.id.btn_leeftijd_2, R.id.btn_leeftijd_3, R.id.btn_leeftijd_4,
        R.id.btn_leeftijd_5, R.id.btn_leeftijd_6, R.id.btn_leeftijd_7, R.id.btn_leeftijd_8
    )
    private val geslachtBtnIds = listOf(
        R.id.btn_geslacht_1, R.id.btn_geslacht_2, R.id.btn_geslacht_3, R.id.btn_geslacht_4
    )
    private val kleedBtnIds = listOf(
        R.id.btn_kleed_1, R.id.btn_kleed_2, R.id.btn_kleed_3, R.id.btn_kleed_4,
        R.id.btn_kleed_5, R.id.btn_kleed_6, R.id.btn_kleed_7, R.id.btn_kleed_8
    )
    private val locationBtnIds = listOf(
        R.id.btn_location_1, R.id.btn_location_2, R.id.btn_location_3,
        R.id.btn_location_4, R.id.btn_location_5, R.id.btn_location_6,
        R.id.btn_location_7, R.id.btn_location_8
    )
    private val heightBtnIds = listOf(
        R.id.btn_height_1, R.id.btn_height_2, R.id.btn_height_3, R.id.btn_height_4,
        R.id.btn_height_5, R.id.btn_height_6, R.id.btn_height_7, R.id.btn_height_8
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_annotatie)

        setupDensityControls()

        // Initialize remarks EditText for location/height auto-tagging
        etOpmerkingen = findViewById(R.id.et_opmerkingen)

        // DEBUG: Log incoming Intent extras
        val rowPosition = intent.getIntExtra("extra_row_pos", -1)

        // Show progress while loading cache and populating UI
        lifecycleScope.launch {
            val progress = ProgressDialogHelper.show(this@AnnotatieScherm, getString(R.string.msg_loading_annotations))
            try {
                withContext(Dispatchers.IO) {
                    // load annotations into memory (SAF -> assets fallback)
                    AnnotationsManager.loadCache(this@AnnotatieScherm)
                    
                    // Load site data for direction labels
                    val telpostId = intent.getStringExtra(EXTRA_TELPOSTID)
                    if (!telpostId.isNullOrBlank()) {
                        val snapshot = ServerDataCache.getOrLoad(this@AnnotatieScherm)
                        currentSite = snapshot.sitesById[telpostId]
                    }
                }
                // populate the pre-drawn buttons
                populateAllColumnsFromCache()

                // Update count field labels based on current season and site
                updateCountFieldLabels()

                // Prefill count fields with existing record values if provided
                prefillCountFields()
            } finally {
                progress.dismiss()
            }
        }

        // Wire compass button
        findViewById<Button>(R.id.btn_compass).setOnClickListener {
            showCompassDialog()
        }

        // Wire Tally checkbox to add/remove [Handteller] tag in remarks
        findViewById<CheckBox>(R.id.cb_tally)?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                addTagToRemarks("Handteller")
            } else {
                removeTagFromRemarks("Handteller")
            }
        }

        // Wire action buttons
        findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
        findViewById<Button>(R.id.btn_ok).setOnClickListener {
            val resultMap = mutableMapOf<String, String?>()
            val selectedLabels = mutableListOf<String>()


            // For each group collect the selected option and label for summary
            for ((group, btns) in groupButtons) {
                val selectedOpt = btns.firstOrNull { it.isChecked }?.tag as? AnnotationOption
                if (selectedOpt != null) {
                    val storeKey = if (selectedOpt.veld.isNotBlank()) selectedOpt.veld else group
                    resultMap[storeKey] = selectedOpt.waarde
                    selectedLabels.add(selectedOpt.tekst)

                    // DEBUG: Log each selected option
                }
            }
            // Checkboxes
            findViewById<CheckBox>(R.id.cb_markeren)?.takeIf { it.isChecked }?.let {
                resultMap["markeren"] = "1"
                selectedLabels.add("Markeren")
            }
            findViewById<CheckBox>(R.id.cb_markeren_lokaal)?.takeIf { it.isChecked }?.let {
                resultMap["markerenlokaal"] = "1"
                selectedLabels.add("Markeren Lokaal")
            }
            findViewById<CheckBox>(R.id.cb_tally)?.takeIf { it.isChecked }?.let {
                resultMap["teltype_C"] = "C"
                selectedLabels.add("Handteller")
            }

            // Manual count inputs - direct mapping (labels are adjusted based on season and site)
            // et_aantal always maps to record.aantal (main direction)
            // et_aantalterug always maps to record.aantalterug (opposite direction)
            val isZwSeizoen = isZwSeizoen()
            
            // Get direction labels from site data
            val isNocMigSite = currentSite?.typetelpost == "5"
            val r1 = currentSite?.r1?.takeIf { it.isNotBlank() && it != "nvt" }
            val r2 = currentSite?.r2?.takeIf { it.isNotBlank() && it != "nvt" }
            
            // Determine labels to use in summary
            val mainLabel = when {
                isNocMigSite || r1 == null || r2 == null -> "Aantal"
                isZwSeizoen -> r1.uppercase()
                else -> r2.uppercase()
            }
            val returnLabel = when {
                isNocMigSite || r1 == null || r2 == null -> "Aantal terug"
                isZwSeizoen -> r2.uppercase()
                else -> r1.uppercase()
            }

            findViewById<EditText>(R.id.et_aantal)?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                resultMap["aantal"] = it
                selectedLabels.add("$mainLabel: $it")
            }
            findViewById<EditText>(R.id.et_aantalterug)?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                resultMap["aantalterug"] = it
                selectedLabels.add("$returnLabel: $it")
            }
            findViewById<EditText>(R.id.et_aantal_lokaal)?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                resultMap["lokaal"] = it
                selectedLabels.add("Lokaal: $it")
            }

            // Remarks/Comments
            findViewById<EditText>(R.id.et_opmerkingen)?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                resultMap["opmerkingen"] = it
                selectedLabels.add("Opm: $it")
            }

            // Sighting direction from compass
            selectedSightingDirection?.let { direction ->
                resultMap["sightingdirection"] = direction
                // Find the Dutch label for display (reverse lookup in directionLabelToCode)
                val label = directionLabelToCode.entries.find { it.value == direction }?.key ?: direction
                selectedLabels.add("Richting: $label")
            }

            val payload = json.encodeToString(resultMap)

            // Build legacy summary text and timestamp for backward compatibility
            val summaryText = if (selectedLabels.isEmpty()) "" else selectedLabels.joinToString(", ")
            val tsSeconds = System.currentTimeMillis() / 1000L

            val out = Intent().apply {
                putExtra(EXTRA_ANNOTATIONS_JSON, payload)
                putExtra(EXTRA_TEXT, summaryText)
                putExtra(EXTRA_TS, tsSeconds)
                // CRITICAL FIX: Preserve row position so handler can match the correct record
                putExtra("extra_row_pos", rowPosition)
            }

            setResult(RESULT_OK, out)
            finish()
        }
    }

    private fun populateAllColumnsFromCache() {
        val cache = AnnotationsManager.getCached()

        // mapping group -> preIds + container id
        applyOptionsToPreDrawn("leeftijd", cache["leeftijd"].orEmpty(), leeftijdBtnIds)
        applyOptionsToPreDrawn("geslacht", cache["geslacht"].orEmpty(), geslachtBtnIds)
        applyOptionsToPreDrawn("kleed", cache["kleed"].orEmpty(), kleedBtnIds)
        applyOptionsToPreDrawn("location", cache["location"].orEmpty(), locationBtnIds)
        applyOptionsToPreDrawn("height", cache["height"].orEmpty(), heightBtnIds)
    }

    /**
     * Fill pre-drawn buttons with the provided options.
     * - If options.size <= preIds.size: fill first N buttons, hide rest.
     * - All buttons are pre-drawn in the layout; no dynamic button creation.
     */
    private fun applyOptionsToPreDrawn(group: String, options: List<AnnotationOption>, preIds: List<Int>) {
        val btnList = mutableListOf<AppCompatToggleButton>()

        // Fill pre-drawn buttons
        for ((idx, resId) in preIds.withIndex()) {
            val btn = findViewById<AppCompatToggleButton?>(resId)
            if (btn == null) continue
            if (idx < options.size) {
                val opt = options[idx]
                btn.text = opt.tekst
                btn.textOn = opt.tekst
                btn.textOff = opt.tekst
                btn.tag = opt
                btn.visibility = View.VISIBLE
                btn.isChecked = false
                btn.setOnClickListener { v -> onGroupButtonClicked(group, v as AppCompatToggleButton) }
            } else {
                // hide unused pre-drawn button
                btn.visibility = View.GONE
                btn.tag = null
                btn.setOnClickListener(null)
            }
            setToggleColor(btn)
            btnList.add(btn)
        }

        groupButtons[group] = btnList
    }

    /**
     * Called when any toggle in a group is clicked.
     * Enforces single-select within the group and updates colouring.
     * For location/height groups, also manages tags in remarks field.
     */
    private fun onGroupButtonClicked(group: String, clicked: AppCompatToggleButton) {
        val list = groupButtons[group] ?: return
        if (clicked.isChecked) {
            // DEBUG: Log button click with value from tag
            val selectedOpt = clicked.tag as? AnnotationOption
            if (selectedOpt != null) {

                // For location and height groups, add tag to remarks
                if (group == "location" || group == "height") {
                    addTagToRemarks(selectedOpt.tekst)
                }
            } else {
                Log.w("AnnotatieScherm", "Button $group clicked but tag is null or not AnnotationOption!")
            }

            // Single-select: uncheck other buttons in the group
            for (btn in list) {
                if (btn === clicked) {
                    setToggleColor(btn)
                } else {
                    if (btn.isChecked) {
                        // Remove tag from remarks if this was a location/height button
                        if (group == "location" || group == "height") {
                            val oldOpt = btn.tag as? AnnotationOption
                            if (oldOpt != null) {
                                removeTagFromRemarks(oldOpt.tekst)
                            }
                        }
                        btn.isChecked = false
                    }
                    setToggleColor(btn)
                }
            }
        } else {
            // toggled off

            // Remove tag from remarks if this is a location/height button
            if (group == "location" || group == "height") {
                val opt = clicked.tag as? AnnotationOption
                if (opt != null) {
                    removeTagFromRemarks(opt.tekst)
                }
            }

            setToggleColor(clicked)
        }
    }

    /**
     * Add a tag to the remarks field in format "[text]"
     */
    private fun addTagToRemarks(tag: String) {
        val current = etOpmerkingen.text.toString()
        val formattedTag = "[$tag]"

        // Check if tag already exists
        if (current.contains(formattedTag)) {
            return
        }

        // Add tag without separator (tags are adjacent)
        val newText = if (current.isBlank()) {
            formattedTag
        } else {
            "$current$formattedTag"
        }

        etOpmerkingen.setText(newText)
    }

    /**
     * Remove a tag from the remarks field
     */
    private fun removeTagFromRemarks(tag: String) {
        val current = etOpmerkingen.text.toString()
        val formattedTag = "[$tag]"

        // Remove the tag
        val newText = current.replace(formattedTag, "")

        etOpmerkingen.setText(newText)
    }

    private fun setToggleColor(btn: AppCompatToggleButton?) {
        if (btn == null) return
        // Ensure the button uses the selector background (preserve the blue border)
        // The style already sets this, but we refresh it to ensure it's not lost
        btn.setBackgroundResource(R.drawable.vt5_btn_selector)
        // Set text color to white for readability
        btn.setTextColor(Color.WHITE)
        // Refresh drawable state to apply the selector based on isChecked
        btn.refreshDrawableState()
    }

    /**
     * Prefill count fields with existing record values if provided via Intent extras.
     * This allows the user to see the current counts from the speech input and modify them.
     *
     * The mapping from record fields to UI fields depends on the season:
     * - In ZW seizoen (Jul-Dec): record.aantal → et_aantal_zw, record.aantalterug → et_aantal_no
     * - In NO seizoen (Jan-Jun): record.aantal → et_aantal_no, record.aantalterug → et_aantal_zw
     */
    private fun prefillCountFields() {
        // Get existing count values from intent extras (these are raw record values)
        val recordAantal = intent.getStringExtra(EXTRA_RECORD_AANTAL)
        val recordAantalterug = intent.getStringExtra(EXTRA_RECORD_AANTALTERUG)
        val lokaal = intent.getStringExtra(EXTRA_LOKAAL)

        // Direct mapping - labels are already adjusted based on season
        // et_aantal always corresponds to record.aantal
        // et_aantalterug always corresponds to record.aantalterug
        recordAantal?.takeIf { it.isNotBlank() && it != "0" }?.let { value ->
            findViewById<EditText>(R.id.et_aantal)?.setText(value)
        }
        recordAantalterug?.takeIf { it.isNotBlank() && it != "0" }?.let { value ->
            findViewById<EditText>(R.id.et_aantalterug)?.setText(value)
        }

        // Prefill lokaal count field (this is direction-independent)
        lokaal?.takeIf { it.isNotBlank() && it != "0" }?.let { value ->
            findViewById<EditText>(R.id.et_aantal_lokaal)?.setText(value)
        }
    }

    /**
     * Update the count field labels based on the current season and site-specific directions.
     * Uses r1 (autumn direction, Jul-Dec) and r2 (spring direction, Jan-Jun) from sites.json.
     * For sites with typetelpost="5" (NocMig sites), directions are not applicable and labels remain simple.
     */
    private fun updateCountFieldLabels() {
        val labelAantal = findViewById<TextView>(R.id.tv_label_aantal)
        val labelAantalterug = findViewById<TextView>(R.id.tv_label_aantalterug)
        
        // Check if this is a NocMig site (typetelpost=5) where directions don't apply
        val isNocMigSite = currentSite?.typetelpost == "5"
        
        if (isNocMigSite || currentSite == null) {
            // NocMig sites or unknown sites: use simple labels without direction
            labelAantal?.text = "Aantal :"
            labelAantalterug?.text = "Aantal terug :"
            return
        }
        
        // Get r1 (autumn) and r2 (spring) from site data
        val r1 = currentSite?.r1?.takeIf { it.isNotBlank() && it != "nvt" }
        val r2 = currentSite?.r2?.takeIf { it.isNotBlank() && it != "nvt" }
        
        // If either direction is null/nvt, fall back to simple labels
        if (r1 == null || r2 == null) {
            labelAantal?.text = "Aantal :"
            labelAantalterug?.text = "Aantal terug :"
            return
        }
        
        val isZwSeizoen = isZwSeizoen()
        
        if (isZwSeizoen) {
            // Autumn season (Jul-Dec): hoofdrichting is r1, terug is r2
            labelAantal?.text = "Aantal ${r1.uppercase()} :"
            labelAantalterug?.text = "Aantal ${r2.uppercase()} :"
        } else {
            // Spring season (Jan-Jun): hoofdrichting is r2, terug is r1
            labelAantal?.text = "Aantal ${r2.uppercase()} :"
            labelAantalterug?.text = "Aantal ${r1.uppercase()} :"
        }
    }

    /**
     * Helper to get the current season status.
     * Delegates to SeizoenUtils for consistent behavior across the app.
     */
    private fun isZwSeizoen(): Boolean = SeizoenUtils.isZwSeizoen()

    /**
     * Shows the compass dialog for selecting sighting direction.
     * The compass uses device sensors to show a real moving needle.
     * User can tap on any of the 16 wind direction buttons to select it.
     */
    private fun showCompassDialog() {
        // Store original direction to restore on cancel
        val originalDirection = selectedSightingDirection

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_compass)
        dialog.setCancelable(true)

        val compassNeedleView = dialog.findViewById<CompassNeedleView>(R.id.compass_needle_view)
        val tvSelectedDirection = dialog.findViewById<TextView>(R.id.tv_selected_direction)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_compass_cancel)
        val btnClear = dialog.findViewById<Button>(R.id.btn_compass_clear)
        val btnOk = dialog.findViewById<Button>(R.id.btn_compass_ok)

        // Track active dialog and compass view for cleanup on activity destroy
        activeCompassDialog = dialog
        activeCompassNeedleView = compassNeedleView

        // Get all direction buttons using predefined ID list
        val directionButtons = directionButtonIds.map { id ->
            dialog.findViewById<MaterialButton>(id)
        }

        // Colors for button states
        val normalColor = getColor(R.color.vt5_dark_gray)
        val selectedColor = getColor(R.color.vt5_light_blue)

        // Set initial selection if already selected
        val initialLabel = selectedSightingDirection?.let { code ->
            directionLabelToCode.entries.find { it.value == code }?.key
        }
        updateDirectionButtonColors(directionButtons, initialLabel, normalColor, selectedColor)
        updateCompassSelectionText(tvSelectedDirection, selectedSightingDirection)

        // Track previous label for remarks tag management
        var previousLabel: String? = initialLabel

        // Set up click handlers for all direction buttons
        directionButtons.forEach { btn ->
            btn.setOnClickListener {
                val label = btn.text.toString()
                val code = directionLabelToCode[label] ?: label

                // Toggle: if same button tapped again, deselect
                if (selectedSightingDirection == code) {
                    // Remove tag from remarks when deselecting
                    removeTagFromRemarks(label)

                    selectedSightingDirection = null
                    updateDirectionButtonColors(directionButtons, null, normalColor, selectedColor)
                    updateCompassSelectionText(tvSelectedDirection, null)
                    previousLabel = null
                } else {
                    // Remove previous direction tag from remarks if any
                    previousLabel?.let { removeTagFromRemarks(it) }

                    // Add new direction tag to remarks
                    addTagToRemarks(label)

                    selectedSightingDirection = code
                    updateDirectionButtonColors(directionButtons, label, normalColor, selectedColor)
                    updateCompassSelectionText(tvSelectedDirection, code)
                    previousLabel = label
                }
                // Update the main view display as well
                updateSightingDirectionDisplay()
            }
        }

        // Start sensors when dialog is shown
        compassNeedleView.startSensors()

        btnCancel.setOnClickListener {
            // Cancel reverts to the original selection (before opening dialog)
            // Remove current tag from remarks if different from original
            previousLabel?.let { currentLabel ->
                if (currentLabel != initialLabel) {
                    removeTagFromRemarks(currentLabel)
                }
            }
            // Restore original tag if there was one
            if (initialLabel != null && previousLabel != initialLabel) {
                addTagToRemarks(initialLabel)
            }

            selectedSightingDirection = originalDirection
            updateSightingDirectionDisplay()
            cleanupCompassDialog()
            dialog.dismiss()
        }

        btnClear.setOnClickListener {
            // Remove current tag from remarks
            previousLabel?.let { removeTagFromRemarks(it) }

            selectedSightingDirection = null
            updateDirectionButtonColors(directionButtons, null, normalColor, selectedColor)
            updateCompassSelectionText(tvSelectedDirection, null)
            updateSightingDirectionDisplay()
            previousLabel = null
        }

        btnOk.setOnClickListener {
            // Selection is already stored, just close the dialog
            cleanupCompassDialog()
            dialog.dismiss()
        }

        // Stop sensors when dialog is dismissed (by back button, etc.)
        dialog.setOnDismissListener {
            cleanupCompassDialog()
        }

        dialog.show()

        // Set dialog width to match parent with some margin
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.95).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /**
     * Updates the background colors of direction buttons based on selection.
     */
    private fun updateDirectionButtonColors(
        buttons: List<MaterialButton>,
        selectedLabel: String?,
        normalColor: Int,
        selectedColor: Int
    ) {
        buttons.forEach { btn ->
            val isSelected = btn.text.toString() == selectedLabel
            btn.backgroundTintList = ColorStateList.valueOf(
                if (isSelected) selectedColor else normalColor
            )
        }
    }

    /**
     * Cleans up the compass dialog resources, stopping sensors.
     */
    private fun cleanupCompassDialog() {
        activeCompassNeedleView?.stopSensors()
        activeCompassNeedleView = null
        activeCompassDialog = null
    }

    override fun onDestroy() {
        // Ensure compass sensors are stopped if activity is destroyed while dialog is showing
        cleanupCompassDialog()
        super.onDestroy()
    }

    /**
     * Updates the text showing the selected direction in the compass dialog.
     */
    private fun updateCompassSelectionText(textView: TextView, directionCode: String?) {
        if (directionCode == null) {
            textView.text = getString(R.string.compass_no_selection)
        } else {
            // Find the Dutch label for the English code
            val label = directionLabelToCode.entries.find { it.value == directionCode }?.key ?: directionCode
            textView.text = getString(R.string.compass_selected, label)
        }
    }

    /**
     * Updates the sighting direction display in the main annotation screen.
     */
    private fun updateSightingDirectionDisplay() {
        val tvSelectedDirection = findViewById<TextView>(R.id.tv_selected_sighting_direction)

        if (selectedSightingDirection != null) {
            // Find the Dutch label for the English code
            val label = directionLabelToCode.entries.find { it.value == selectedSightingDirection }?.key
                ?: selectedSightingDirection
            tvSelectedDirection?.text = label
        } else {
            tvSelectedDirection?.text = ""
        }
    }

    private fun setupDensityControls() {
        val prefs = getSharedPreferences("vt5_prefs", MODE_PRIVATE)
        val savedMode = DensityMode.fromKey(prefs.getString(PREF_ANNOT_DENSITY, null))

        val btnStandard = findViewById<ImageButton>(R.id.btn_density_standard)
        val btnCompact = findViewById<ImageButton>(R.id.btn_density_compact)
        val btnSupercompact = findViewById<ImageButton>(R.id.btn_density_supercompact)

        fun applyMode(mode: DensityMode) {
            prefs.edit().putString(PREF_ANNOT_DENSITY, mode.key).apply()
            applyDensityMode(mode)
            updateDensityButtonState(btnStandard, btnCompact, btnSupercompact, mode)
        }

        btnStandard.setOnClickListener { applyMode(DensityMode.STANDARD) }
        btnCompact.setOnClickListener { applyMode(DensityMode.COMPACT) }
        btnSupercompact.setOnClickListener { applyMode(DensityMode.SUPERCOMPACT) }

        applyMode(savedMode)
    }

    private fun updateDensityButtonState(
        btnStandard: ImageButton,
        btnCompact: ImageButton,
        btnSupercompact: ImageButton,
        mode: DensityMode
    ) {
        val activeAlpha = 1.0f
        val inactiveAlpha = 0.5f
        btnStandard.alpha = if (mode == DensityMode.STANDARD) activeAlpha else inactiveAlpha
        btnCompact.alpha = if (mode == DensityMode.COMPACT) activeAlpha else inactiveAlpha
        btnSupercompact.alpha = if (mode == DensityMode.SUPERCOMPACT) activeAlpha else inactiveAlpha
    }

    private fun applyDensityMode(mode: DensityMode) {
        val root = findViewById<ViewGroup>(R.id.annot_content_root) ?: return
        val factor = mode.factor
        val skipIds = setOf(R.id.btn_density_standard, R.id.btn_density_compact, R.id.btn_density_supercompact)

        traverseViews(root) { view ->
            if (skipIds.contains(view.id)) return@traverseViews
            val baseline = (view.getTag(R.id.tag_density_baseline) as? DensityBaseline)
                ?: captureBaseline(view).also { view.setTag(R.id.tag_density_baseline, it) }
            applyBaseline(view, baseline, factor)
        }
        root.requestLayout()
        root.invalidate()
    }

    private fun traverseViews(root: View, block: (View) -> Unit) {
        block(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                traverseViews(root.getChildAt(i), block)
            }
        }
    }

    private fun captureBaseline(view: View): DensityBaseline {
        val textSizePx = (view as? TextView)?.textSize
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams
        return DensityBaseline(
            textSizePx = textSizePx,
            paddingLeft = view.paddingLeft,
            paddingTop = view.paddingTop,
            paddingRight = view.paddingRight,
            paddingBottom = view.paddingBottom,
            minWidth = view.minimumWidth,
            minHeight = view.minimumHeight,
            marginLeft = params?.leftMargin ?: 0,
            marginTop = params?.topMargin ?: 0,
            marginRight = params?.rightMargin ?: 0,
            marginBottom = params?.bottomMargin ?: 0
        )
    }

    private fun applyBaseline(view: View, baseline: DensityBaseline, factor: Float) {
        baseline.textSizePx?.let { sizePx ->
            if (view is TextView) {
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx * factor)
            }
        }

        val left = (baseline.paddingLeft * factor).roundToInt()
        val top = (baseline.paddingTop * factor).roundToInt()
        val right = (baseline.paddingRight * factor).roundToInt()
        val bottom = (baseline.paddingBottom * factor).roundToInt()
        view.setPadding(left, top, right, bottom)

        view.minimumWidth = (baseline.minWidth * factor).roundToInt()
        view.minimumHeight = (baseline.minHeight * factor).roundToInt()

        val params = view.layoutParams
        if (params is ViewGroup.MarginLayoutParams) {
            params.leftMargin = (baseline.marginLeft * factor).roundToInt()
            params.topMargin = (baseline.marginTop * factor).roundToInt()
            params.rightMargin = (baseline.marginRight * factor).roundToInt()
            params.bottomMargin = (baseline.marginBottom * factor).roundToInt()
            view.layoutParams = params
        }
    }
}
