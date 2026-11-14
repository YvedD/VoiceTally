package com.yvesds.vt5.features.telling

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yvesds.vt5.core.database.entities.TellingHeader
import com.yvesds.vt5.core.database.entities.Waarneming
import com.yvesds.vt5.core.database.toServerEnvelope
import com.yvesds.vt5.core.opslag.ErrorLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Start een expliciete DB-reconstructie van een bestaande telling in TellingScherm.
 */
object RoomTellingReopenLauncher {
    private const val TAG = "RoomTellingReopenLauncher"

    fun launch(
        activity: AppCompatActivity,
        header: TellingHeader,
        records: List<Waarneming>,
        finishCurrent: Boolean = false
    ) {
        try {
            val uploadCore = TellingUploadCore(activity)
            val preparedBase = uploadCore.prepareEnvelopeForUpload(
                sourceEnvelope = header.toServerEnvelope(records),
                useStoredOnlineIdWhenBlank = false,
                now = Date()
            )

            val restoreTellingId = preparedBase.tellingid.ifBlank { header.tellingid }
            val restoreOnlineId = preparedBase.onlineid.ifBlank { header.onlineid }
            val prepared = preparedBase.copy(
                tellingid = restoreTellingId,
                onlineid = restoreOnlineId,
                data = preparedBase.data.map { item ->
                    item.copy(tellingid = item.tellingid.ifBlank { restoreTellingId })
                }
            )

            uploadCore.persistPreparedEnvelopeToPrefs(prepared)

            val reconstructedSpeciesIds = prepared.data
                .asSequence()
                .map { it.soortid.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()

            prepared.telpostid
                .takeIf { it.isNotBlank() }
                ?.let { TellingSessionManager.setTelpost(it) }
            TellingSessionManager.setPreselectedSoorten(reconstructedSpeciesIds)

            activity.getSharedPreferences("vt5_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("pref_telling_id", restoreTellingId)
                .putString("pref_online_id", restoreOnlineId)
                .apply()

            val intent = Intent(activity, TellingScherm::class.java).apply {
                putExtra(TellingScherm.EXTRA_RESTORE_PENDING_TELLING, true)
                putExtra(TellingScherm.EXTRA_RESTORE_TELLING_ID, restoreTellingId)
                putExtra(TellingScherm.EXTRA_RESTORE_ONLINE_ID, restoreOnlineId)
            }
            activity.startActivity(intent)
            if (finishCurrent) {
                activity.finish()
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Fout bij reconstrueren/heropenen van telling [${header.tellingid}]: ${ex.message}", ex)
            CoroutineScope(Dispatchers.IO).launch {
                ErrorLogger.logError(
                    activity,
                    TAG,
                    "Reconstructie van telling [${header.tellingid}] mislukt: ${ex.message}",
                    ex
                )
            }
            Toast.makeText(activity, "Fout bij reconstrueren van telling: ${ex.message}", Toast.LENGTH_LONG).show()
        }
    }
}
