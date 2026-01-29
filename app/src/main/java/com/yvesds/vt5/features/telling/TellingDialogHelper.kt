package com.yvesds.vt5.features.telling

import android.app.Activity
import android.graphics.Color
import android.text.InputFilter
import android.text.InputType
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.yvesds.vt5.R
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.features.alias.AliasManager
import com.yvesds.vt5.features.recent.RecentSpeciesStore
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import com.yvesds.vt5.features.speech.Candidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TellingDialogHelper: Manages dialog interactions for TellingScherm.
 * 
 * Responsibilities:
 * - Number input dialogs for tiles
 * - Suggestion bottom sheets
 * - Confirmation dialogs
 * - Dialog text styling for sunlight readability
 */
class TellingDialogHelper(
    private val activity: Activity,
    private val lifecycleOwner: LifecycleOwner,
    private val safHelper: SaFStorageHelper
) {
    companion object {
        private const val TAG = "TellingDialogHelper"
    }

    /**
     * Show number input dialog for a species tile.
     */
    fun showNumberInputDialog(
        position: Int,
        currentTiles: List<TellingScherm.SoortRow>,
        mainLabel: String,
        returnLabel: String,
        onCountUpdated: (String, Int, Int) -> Unit
    ) {
        if (position < 0 || position >= currentTiles.size) return
        val row = currentTiles[position]

        val mainInput = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(4))
            hint = "Aantal ${mainLabel.uppercase()}"
        }
        val returnInput = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(4))
            hint = "Aantal ${returnLabel.uppercase()}"
        }

        val container = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 16, 24, 0)

            fun makeRow(label: String, field: EditText): android.widget.LinearLayout {
                return android.widget.LinearLayout(activity).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    val tv = TextView(activity).apply {
                        text = "$label:"
                        setTextColor(Color.WHITE)
                        setPadding(0, 12, 16, 12)
                    }
                    addView(tv)
                    addView(field, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                }
            }

            addView(makeRow(mainLabel.uppercase(), mainInput))
            addView(makeRow(returnLabel.uppercase(), returnInput))
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.dialog_enter_count, row.naam))
            .setMessage("Huidige stand: ${row.countMain} (${mainLabel.uppercase()}) / ${row.countReturn} (${returnLabel.uppercase()})")
            .setView(container)
            .setPositiveButton("Toevoegen") { _, _ ->
                val deltaMain = mainInput.text.toString().trim().toIntOrNull() ?: 0
                val deltaReturn = returnInput.text.toString().trim().toIntOrNull() ?: 0
                if (deltaMain > 0 || deltaReturn > 0) {
                    onCountUpdated(row.soortId, deltaMain, deltaReturn)
                }
            }
            .setNegativeButton("Annuleren", null)
            .show()

        styleAlertDialogTextToWhite(dialog)
    }

    /**
     * Show suggestion bottom sheet with multiple candidates.
     */
    fun showSuggestionBottomSheet(
        candidates: List<Candidate>,
        count: Int,
        onCandidateSelected: (String, String, Int) -> Unit
    ) {
        if (candidates.isEmpty()) return

        val items = candidates.map { "${it.displayName} (${it.speciesId})" }.toTypedArray()
        
        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.dialog_select_species))
            .setItems(items) { _, which ->
                val selected = candidates.getOrNull(which) ?: return@setItems
                onCandidateSelected(selected.speciesId, selected.displayName, count)
            }
            .setNegativeButton("Annuleren", null)
            .show()

        styleAlertDialogTextToWhite(dialog)
    }

    /**
     * Show confirmation dialog before adding species with popup.
     */
    fun showAddSpeciesConfirmation(
        speciesId: String,
        displayName: String,
        count: Int,
        onConfirmed: (String, String, Int) -> Unit
    ) {
        val msg = "Soort \"$displayName\" herkend met aantal $count.\n\nToevoegen?"
        
        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.dialog_add_species))
            .setMessage(msg)
            .setPositiveButton("Ja") { _, _ ->
                onConfirmed(speciesId, displayName, count)
            }
            .setNegativeButton("Nee", null)
            .show()

        styleAlertDialogTextToWhite(dialog)
    }

    /**
     * Show Add Alias dialog for partial/raw entries.
     */
    fun showAddAliasDialog(
        nameText: String,
        count: Int,
        availableSpecies: List<String>,
        onAliasAdded: (String, String, Int) -> Unit,
        fragmentManager: androidx.fragment.app.FragmentManager
    ) {
        val dialog = AddAliasDialog.newInstance(listOf(nameText), availableSpecies)
        
        dialog.listener = object : AddAliasDialog.AddAliasListener {
            override fun onAliasAssigned(speciesId: String, aliasText: String) {
                lifecycleOwner.lifecycleScope.launch {
                    try {
                        val snapshot = ServerDataCache.getOrLoad(activity)
                        val canonical = snapshot.speciesById[speciesId]?.soortnaam ?: aliasText
                        val tilename = snapshot.speciesById[speciesId]?.soortkey

                        val added = AliasManager.addAlias(
                            context = activity,
                            saf = safHelper,
                            speciesId = speciesId,
                            aliasText = aliasText.trim(),
                            canonical = canonical,
                            tilename = tilename
                        )

                        if (added) {
                            // Notify caller
                            onAliasAdded(speciesId, canonical, count)
                        }
                    } catch (ex: Exception) {
                        Log.w(TAG, "Failed to add alias: ${ex.message}", ex)
                    }
                }
            }
        }
        
        dialog.show(fragmentManager, "addAlias")
    }

    /**
     * Style AlertDialog text to white for sunlight readability.
     * Forces title and message text to white color.
     */
    fun styleAlertDialogTextToWhite(dialog: AlertDialog) {
        try {
            // Title
            val titleId = activity.resources.getIdentifier("alertTitle", "id", "android")
            if (titleId > 0) {
                val titleView = dialog.findViewById<TextView>(titleId)
                titleView?.setTextColor(Color.WHITE)
            }

            // Message
            val messageId = android.R.id.message
            val messageView = dialog.findViewById<TextView>(messageId)
            messageView?.setTextColor(Color.WHITE)

            // Buttons
            val darkGray = activity.getColor(R.color.vt5_dark_gray)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                setTextColor(Color.WHITE)
                setBackgroundColor(darkGray)
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                setTextColor(Color.WHITE)
                setBackgroundColor(darkGray)
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
                setTextColor(Color.WHITE)
                setBackgroundColor(darkGray)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to style dialog text: ${e.message}")
        }
    }
}
