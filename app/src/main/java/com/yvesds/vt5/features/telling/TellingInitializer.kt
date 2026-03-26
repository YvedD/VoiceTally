package com.yvesds.vt5.features.telling

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.yvesds.vt5.R
import com.yvesds.vt5.core.ui.ProgressDialogHelper
import com.yvesds.vt5.features.masterClient.MasterClientPrefs
import com.yvesds.vt5.features.masterClient.McRuntimePermissions
import com.yvesds.vt5.features.recent.RecentSpeciesStore
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import com.yvesds.vt5.features.speech.MatchContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * TellingInitializer: Handles initialization tasks for TellingScherm.
 * 
 * Responsibilities:
 * - Load preselected species tiles
 * - Check and request necessary permissions
 * - Build initial match context
 * - Initialize speech recognition components
 */
class TellingInitializer(
    private val activity: AppCompatActivity
) {
    companion object {
        private const val TAG = "TellingInitializer"
        private const val PERMISSION_REQUEST_STARTUP = 101
    }

    // Callbacks
    var onTilesLoaded: ((List<SoortTile>) -> Unit)? = null
    var onMatchContextBuilt: ((MatchContext) -> Unit)? = null
    var onPermissionsGranted: (() -> Unit)? = null
    var onInitializationFailed: ((String) -> Unit)? = null
    var onLogMessage: ((String, String) -> Unit)? = null

    /**
     * Load preselected species tiles from session state.
     */
    fun loadPreselection() {
        activity.lifecycleScope.launch {
            try {
                val dialog = ProgressDialogHelper.show(activity, "Soorten laden...")
                try {
                    val initial = withContext(Dispatchers.IO) {
                        val snapshot = ServerDataCache.getOrLoad(activity)
                        val pre = TellingSessionManager.preselectState.value
                        val ids = pre.selectedSoortIds

                        val speciesById = snapshot.speciesById
                        ids.mapNotNull { sid ->
                            val naam = speciesById[sid]?.soortnaam ?: return@mapNotNull null
                            SoortTile(sid, naam, 0)
                        }.sortedBy { it.naam.lowercase(Locale.getDefault()) }
                    }

                    if (initial.isEmpty()) {
                        if (MasterClientPrefs.getMode(activity) == MasterClientPrefs.MODE_CLIENT) {
                            onTilesLoaded?.invoke(emptyList())
                            onLogMessage?.invoke("Clientmodus gestart - wachten op tegelsync van master.", "systeem")
                            dialog.dismiss()
                            checkAndRequestPermissions()
                            return@launch
                        }

                        dialog.dismiss()
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.soort_no_preselection),
                            Toast.LENGTH_LONG
                        ).show()
                        activity.finish()
                        return@launch
                    }

                    // Notify callback with loaded tiles
                    onTilesLoaded?.invoke(initial)
                    onLogMessage?.invoke("Telling gestart met ${initial.size} soorten.", "systeem")
                    dialog.dismiss()

                    // Build initial match context in background
                    activity.lifecycleScope.launch(Dispatchers.Default) {
                        try {
                            val mc = buildMatchContext(initial.map { it.soortId }.toSet())
                            onMatchContextBuilt?.invoke(mc)
                        } catch (ex: Exception) {
                            Log.w(TAG, "Failed to build initial cached MatchContext: ${ex.message}", ex)
                        }
                    }

                    checkAndRequestPermissions()
                } finally {
                    dialog.dismiss()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading species: ${e.message}")
                Toast.makeText(
                    activity,
                    activity.getString(R.string.soort_error_loading_species),
                    Toast.LENGTH_SHORT
                ).show()
                onInitializationFailed?.invoke(e.message ?: "Unknown error")
                activity.finish()
            }
        }
    }

    /**
     * Check for required permissions and request if needed.
     */
    fun checkAndRequestPermissions() {
        McRuntimePermissions.refreshCachedPermissionStates(activity)
        val missingPermissions = McRuntimePermissions.missingStartupPermissions(activity)
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                missingPermissions,
                PERMISSION_REQUEST_STARTUP
            )
        } else {
            onPermissionsGranted?.invoke()
        }
    }

    /**
     * Build match context for speech recognition.
     */
    suspend fun buildMatchContext(tilesIds: Set<String>? = null): MatchContext = withContext(Dispatchers.IO) {
        val snapshot = ServerDataCache.getOrLoad(activity)

        val tiles = tilesIds ?: emptySet()

        val telpostId = TellingSessionManager.preselectState.value.telpostId
        val siteAllowed = telpostId?.let { id ->
            snapshot.siteSpeciesBySite[id]?.map { it.soortid }?.toSet() ?: emptySet()
        } ?: snapshot.speciesById.keys

        val recents = RecentSpeciesStore.getRecents(activity).map { it.first }.toSet()

        val speciesById = snapshot.speciesById.mapValues { (_, sp) ->
            sp.soortnaam to sp.soortkey
        }

        MatchContext(
            tilesSpeciesIds = tiles,
            siteAllowedIds = siteAllowed,
            recentIds = recents,
            speciesById = speciesById
        )
    }

    /**
     * Handle permission request result.
     */
    fun onPermissionResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_STARTUP) {
            val requested = McRuntimePermissions.requiredStartupPermissions()
            McRuntimePermissions.cachePermissionResults(
                activity,
                requested.mapIndexedNotNull { index, permission ->
                    val grant = grantResults.getOrNull(index) ?: return@mapIndexedNotNull null
                    permission to (grant == PackageManager.PERMISSION_GRANTED)
                }.toMap()
            )
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted || McRuntimePermissions.hasAllStartupPermissions(activity)) {
                McRuntimePermissions.refreshCachedPermissionStates(activity)
                onPermissionsGranted?.invoke()
            } else {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.mc_permissions_startup_required),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
