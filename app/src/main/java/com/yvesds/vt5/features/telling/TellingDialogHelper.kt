package com.yvesds.vt5.features.telling

import android.app.Activity
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.yvesds.vt5.R
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.core.ui.DialogStyler
import com.yvesds.vt5.features.alias.AliasManager
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import com.yvesds.vt5.utils.TelpostDirectionLabelProvider
import kotlinx.coroutines.launch

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
     * Updated to allow entering two counts: main direction (boven) and return/opposite direction (onder).
     */
    fun showNumberInputDialog(
        position: Int,
        currentTiles: List<TellingScherm.SoortRow>,
        onCountUpdated: (String, Int, Int) -> Unit
    ) {
        if (position < 0 || position >= currentTiles.size) return
        val row = currentTiles[position]

        val inflater = activity.layoutInflater
        val view = inflater.inflate(R.layout.dialog_number_input_dual, null)
        val etMain = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_main_count)
        val etReturn = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_return_count)
        val tvMainLabel = view.findViewById<TextView>(R.id.tv_main_direction_label)
        val tvReturnLabel = view.findViewById<TextView>(R.id.tv_return_direction_label)
        val tvCurrentTotal = view.findViewById<TextView>(R.id.tv_current_total)

        // Direction labels based on telpost + (telling) date/season
        lifecycleOwner.lifecycleScope.launch {
            try {
                val labels = TelpostDirectionLabelProvider.getForCurrentSession(activity)
                tvMainLabel.text = labels.mainText
                tvReturnLabel.text = labels.returnText
            } catch (_: Exception) {
                // fall back to defaults in layout
            }
        }

        // Show current counts as initial values
        etMain.setText(row.countMain.toString())
        etReturn.setText(row.countReturn.toString())
        tvCurrentTotal.text = activity.getString(R.string.dialog_current_total, row.count)

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.dialog_enter_count, row.naam))
            .setView(view)
            .setPositiveButton("Toevoegen") { _, _ ->
                val mainRaw = etMain.text?.toString()?.trim() ?: "0"
                val returnRaw = etReturn.text?.toString()?.trim() ?: "0"
                val mainDelta = mainRaw.toIntOrNull() ?: 0
                val returnDelta = returnRaw.toIntOrNull() ?: 0
                if (mainDelta > 0 || returnDelta > 0) {
                    onCountUpdated(row.soortId, mainDelta, returnDelta)
                }
            }
            .setNegativeButton("Annuleren", null)
            .show()

        // Dialog styling: theme-first, plus a safe code fallback
        DialogStyler.apply(dialog)
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
}
