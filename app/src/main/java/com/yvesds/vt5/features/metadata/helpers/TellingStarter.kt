package com.yvesds.vt5.features.metadata.helpers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.core.database.repository.HybridTellingRepository
import com.yvesds.vt5.databinding.SchermMetadataBinding
import com.yvesds.vt5.features.recent.SpeciesUsageScoreStore
import com.yvesds.vt5.features.serverdata.model.DataSnapshot
import com.yvesds.vt5.features.telling.TellingUploadCore
import com.yvesds.vt5.net.StartTellingApi
import com.yvesds.vt5.features.telling.TellingUploadFlags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TellingStarter: Handles starting a new telling session.
 * 
 * Responsibilities:
 * - Build telling envelope from form data
 * - Send counts_save API request
 * - Parse and store online ID
 * - Initialize session state
 * 
 * This encapsulates the complex logic of starting a telling.
 */
class TellingStarter(
    private val context: Context,
    private val binding: SchermMetadataBinding,
    private val formManager: MetadataFormManager,
    private val prefs: SharedPreferences
) {
    
    companion object {
        private const val TAG = "TellingStarter"
        private const val PREF_TELLING_ID = "pref_telling_id"
        private const val PREF_ONLINE_ID = "pref_online_id"
    }
    
    data class StartResult(
        val success: Boolean,
        val onlineId: String?,
        val errorMessage: String?
    )
    
    private val hybridRepository by lazy { HybridTellingRepository(context) }

    /**
     * Start a new telling session.
     * Returns StartResult with success status and online ID or error message.
     */
    suspend fun startTelling(
        telpostId: String,
        username: String,
        password: String,
        @Suppress("UNUSED_PARAMETER") snapshot: DataSnapshot
    ): StartResult = withContext(Dispatchers.IO) {
        try {
            // Generate telling ID
            val tellingId = VT5App.nextTellingId()

            // Get form values
            val begintijdEpoch = formManager.computeBeginEpochSec()
            val eindtijdEpoch = 0L // Live mode: empty end time
            
            val windrichtingForServer = formManager.gekozenWindrichtingCode?.takeIf { it.isNotBlank() }
                ?: binding.acWindrichting.text?.toString()?.takeIf { it.isNotBlank() } ?: ""
            
            val windkrachtBft = formManager.gekozenWindkracht ?: ""
            val temperatuurC = binding.etTemperatuur.text?.toString()?.trim().orEmpty()
            val bewolkingAchtsten = formManager.gekozenBewolking ?: ""
            val neerslagCode = formManager.gekozenNeerslagCode ?: ""
            val zichtMeters = binding.etZicht.text?.toString()?.trim().orEmpty()
            val typetellingCode = formManager.gekozenTypeTellingCode ?: ""
            val tellersFromUi = formManager.getTellers()
            val weerOpmerking = binding.etWeerOpmerking.text?.toString()?.trim().orEmpty()
            val opmerkingen = formManager.getOpmerkingen()
            val luchtdrukHpaRaw = binding.etLuchtdruk.text?.toString()?.trim().orEmpty()
            
            // Build envelope (live mode = true so eindtijd == "")
            val envelope = StartTellingApi.buildEnvelopeFromUi(
                tellingId = tellingId,
                telpostId = telpostId,
                begintijdEpochSec = begintijdEpoch,
                eindtijdEpochSec = eindtijdEpoch,
                windrichtingLabel = windrichtingForServer,
                windkrachtBftOnly = windkrachtBft,
                temperatuurC = temperatuurC,
                bewolkingAchtstenOnly = bewolkingAchtsten,
                neerslagCode = neerslagCode,
                zichtMeters = zichtMeters,
                typetellingCode = typetellingCode,
                telers = tellersFromUi,
                weerOpmerking = weerOpmerking,
                opmerkingen = opmerkingen,
                luchtdrukHpaRaw = luchtdrukHpaRaw,
                liveMode = true
            )
            
            // NEW: start a new session for score-based favorites windowing (last N sessions)
            SpeciesUsageScoreStore.startNewSession(context)

            val uploadCore = TellingUploadCore(context)
            val preparedEnvelope = uploadCore.prepareEnvelopeForUpload(
                sourceEnvelope = envelope.first(),
                useStoredOnlineIdWhenBlank = false
            )
            val uploadResult = uploadCore.uploadPrepared(
                TellingUploadCore.UploadRequest(
                    mode = TellingUploadCore.Mode.START,
                    preparedEnvelope = preparedEnvelope,
                    credentials = TellingUploadCore.Credentials(username, password),
                    persistReturnedOnlineId = true,
                    persistPreparedEnvelopeToPrefs = true,
                    markTellingSent = false
                )
            )

            if (!uploadResult.success) {
                val reason = uploadResult.errorMessage ?: uploadResult.responseText.ifBlank { "Onbekende uploadfout" }
                Log.w(TAG, "counts_save failed: $reason")
                return@withContext StartResult(false, null, reason)
            }

            val onlineId = uploadResult.effectiveOnlineId
            if (onlineId.isNullOrBlank()) {
                Log.w(TAG, "Could not parse onlineId from response: ${uploadResult.responseText}")
                return@withContext StartResult(false, null, "Could not parse online ID: ${uploadResult.responseText}")
            }
            if (onlineId == tellingId) {
                Log.w(TAG, "Parsed onlineId matches local tellingId; refusing corrupted session start. response=${uploadResult.responseText}")
                return@withContext StartResult(
                    false,
                    null,
                    "Server online ID kon niet betrouwbaar worden gelezen (lokale tellingid werd teruggevonden i.p.v. onlineid)."
                )
            }

            // Store in preferences
            prefs.edit {
                putString(PREF_TELLING_ID, tellingId)
                putString(PREF_ONLINE_ID, onlineId)
            }

            // Mark this telling as not sent yet
            TellingUploadFlags.markNotSent(context, tellingId, onlineId)

            // Room shadow write: Save the header with the returned online ID
            // IMPORTANT: Use uploadResult.preparedEnvelope which contains the onlineId from server
            hybridRepository.saveHeaderToRoom(uploadResult.preparedEnvelope)

            return@withContext StartResult(true, onlineId, null)
        } catch (e: Exception) {
            Log.e(TAG, "startTelling failed: ${e.message}", e)
            return@withContext StartResult(false, null, e.message ?: "Unknown error")
        }
    }
}
