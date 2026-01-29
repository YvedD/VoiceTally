package com.yvesds.vt5.utils

import android.content.Context
import android.util.Log
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import com.yvesds.vt5.features.serverdata.model.SiteItem
import com.yvesds.vt5.features.telling.TellingSessionManager
import com.yvesds.vt5.net.ServerTellingEnvelope
import kotlinx.serialization.builtins.ListSerializer

/**
 * Central provider for the direction labels (hoofdrichting / tegenrichting) based on:
 * - Telpost data (sites.json): r1/r2 + typetelpost
 * - Telling start time (begintijd) to determine season, so old tellings display correctly.
 *
 * Rules (same as elsewhere in the app):
 * - For typetelpost="5" (NocMig) or missing r1/r2: use generic labels.
 * - For Jul-Dec (ZW season): main = r1, return = r2
 * - For Jan-Jun (NO season): main = r2, return = r1
 */
object TelpostDirectionLabelProvider {

    private const val TAG = "TelpostDirectionLabels"

    data class Labels(
        val mainShort: String?,   // e.g. "ZW" or "NO" or null
        val returnShort: String?, // e.g. "NO" or "ZW" or null
        val mainText: String,     // e.g. "Aantal ZW :" or "Aantal :"
        val returnText: String    // e.g. "Aantal NO :" or "Aantal terug :"
    )

    suspend fun getForCurrentSession(context: Context): Labels {
        val telpostId = TellingSessionManager.preselectState.value.telpostId
        val begintijdEpoch = readBegintijdEpochFromPrefs(context)

        val snapshot = try {
            ServerDataCache.getOrLoad(context)
        } catch (e: Exception) {
            Log.w(TAG, "Could not load ServerDataCache: ${e.message}")
            return fallback()
        }

        val site = telpostId?.let { snapshot.sitesById[it] }
        return compute(site, begintijdEpoch)
    }

    fun compute(site: SiteItem?, begintijdEpochSeconds: Long?): Labels {
        val isNocMigSite = site?.typetelpost == "5"
        val r1 = site?.r1?.takeIf { it.isNotBlank() && it != "nvt" }?.uppercase()
        val r2 = site?.r2?.takeIf { it.isNotBlank() && it != "nvt" }?.uppercase()

        if (isNocMigSite || r1 == null || r2 == null) {
            return Labels(
                mainShort = null,
                returnShort = null,
                mainText = "Aantal :",
                returnText = "Aantal terug :"
            )
        }

        val isZwSeason = when {
            begintijdEpochSeconds != null && begintijdEpochSeconds > 0 -> SeizoenUtils.isZwSeizoen(begintijdEpochSeconds)
            else -> SeizoenUtils.isZwSeizoen()
        }

        val mainDir = if (isZwSeason) r1 else r2
        val returnDir = if (isZwSeason) r2 else r1

        return Labels(
            mainShort = mainDir,
            returnShort = returnDir,
            mainText = "Aantal $mainDir :",
            returnText = "Aantal $returnDir :"
        )
    }

    private fun fallback(): Labels = Labels(null, null, "Aantal :", "Aantal terug :")

    /**
     * Tries to read the current telling's begintijd from the saved envelope in prefs.
     * Returns null if not available.
     */
    private fun readBegintijdEpochFromPrefs(context: Context): Long? {
        return try {
            val prefs = context.getSharedPreferences("vt5_prefs", Context.MODE_PRIVATE)
            val json = prefs.getString("pref_saved_envelope_json", null) ?: return null
            val envelopes = VT5App.json.decodeFromString(ListSerializer(ServerTellingEnvelope.serializer()), json)
            val first = envelopes.firstOrNull() ?: return null
            first.begintijd.toLongOrNull()
        } catch (_: Exception) {
            null
        }
    }
}
