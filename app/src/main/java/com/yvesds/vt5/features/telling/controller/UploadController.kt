package com.yvesds.vt5.features.telling.controller

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.yvesds.vt5.R
import com.yvesds.vt5.core.ui.DialogStyler
import com.yvesds.vt5.features.masterClient.MasterClientRuntimeStore
import com.yvesds.vt5.features.metadata.ui.MetadataScherm
import com.yvesds.vt5.features.telling.AfrondConfirmDialog
import com.yvesds.vt5.features.telling.MetadataUpdates
import com.yvesds.vt5.features.telling.TellingAfrondHandler
import com.yvesds.vt5.features.telling.TellingUploadCore
import com.yvesds.vt5.features.telling.data.TellingRepository
import com.yvesds.vt5.hoofd.HoofdActiviteit
import com.yvesds.vt5.net.ServerTellingDataItem
import com.yvesds.vt5.net.ServerTellingEnvelope
import com.yvesds.vt5.net.TrektellenApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UploadController: Beheert de upload flow voor tellingen.
 *
 * Verantwoordelijkheden:
 * - UI-state management voor uploads (Idle, Uploading, Success, Error)
 * - Afronden met bevestigingsdialog (handleAfrondenWithConfirmation)
 * - Volledige afrond-flow via TellingAfrondHandler (handleAfronden)
 * - Directe upload via TellingUploadCore (upload)
 * - Navigatie na succesvolle upload (vervolgtelling dialog)
 *
 * De upload() methode gebruikt TellingUploadCore.uploadPrepared() voor consistente
 * afhandeling van credentials, sanitizing, onlineId persistence en mutex.
 */
@Singleton
class UploadController @Inject constructor(
    private val scope: CoroutineScope,
    private val api: TrektellenApi,
    private val afrondHandler: TellingAfrondHandler,
    private val uploadCore: TellingUploadCore,
    private val repository: TellingRepository
) {
    private val TAG = "UploadController"
    private val SUCCESS_DIALOG_DELAY_MS = 1000L

    sealed class UploadState {
        object Idle : UploadState()
        object Uploading : UploadState()
        data class Success(val onlineId: String) : UploadState()
        data class Error(val message: String, val exception: Exception? = null) : UploadState()
    }

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState = _uploadState.asStateFlow()

    fun handleAfrondenWithConfirmation(
        activity: AppCompatActivity,
        pendingBackupDocs: List<DocumentFile>,
        pendingBackupInternalPaths: List<String>,
        onSuccess: () -> Unit
    ) {
        val metadata = afrondHandler.getCurrentMetadata()
        if (metadata == null) {
            Log.e(TAG, "Cannot start afronden: metadata is null")
            return
        }

        val dialog = AfrondConfirmDialog.newInstance(
            begintijdEpoch = metadata.begintijd ?: "",
            eindtijdEpoch = metadata.eindtijd ?: "",
            opmerkingen = metadata.opmerkingen ?: ""
        )

        dialog.listener = object : AfrondConfirmDialog.AfrondConfirmListener {
            override fun onAfrondConfirmed(begintijdEpoch: String, eindtijdEpoch: String, opmerkingen: String) {
                handleAfronden(
                    activity = activity,
                    pendingBackupDocs = pendingBackupDocs,
                    pendingBackupInternalPaths = pendingBackupInternalPaths,
                    metadataUpdates = MetadataUpdates(
                        begintijd = begintijdEpoch,
                        eindtijd = eindtijdEpoch,
                        opmerkingen = opmerkingen
                    ),
                    onSuccess = onSuccess
                )
            }

            override fun onAfrondCancelled() {
                // Do nothing
            }
        }

        dialog.show(activity.supportFragmentManager, "afrondConfirm")
    }

    fun handleAfronden(
        activity: AppCompatActivity,
        pendingBackupDocs: List<DocumentFile>,
        pendingBackupInternalPaths: List<String>,
        metadataUpdates: MetadataUpdates? = null,
        onSuccess: () -> Unit
    ) {
        scope.launch {
            _uploadState.value = UploadState.Uploading

            // Haal records op uit repository
            val prefs = activity.getSharedPreferences("vt5_prefs", android.content.Context.MODE_PRIVATE)
            val tellingId = prefs.getString("pref_telling_id", null)

            val pendingRecords = if (tellingId != null) {
                repository.getObservations(tellingId).map { obs ->
                    with(repository) { obs.toDomain() }
                }
            } else {
                emptyList()
            }

            val result = afrondHandler.handleAfronden(
                pendingRecords = pendingRecords,
                pendingBackupDocs = pendingBackupDocs,
                pendingBackupInternalPaths = pendingBackupInternalPaths,
                metadataUpdates = metadataUpdates
            )

            withContext(Dispatchers.Main) {
                when (result) {
                    is TellingAfrondHandler.AfrondResult.Success -> {
                        _uploadState.value = UploadState.Idle
                        onSuccess()

                        val eindtijd = metadataUpdates?.eindtijd?.ifBlank { null }
                            ?: (System.currentTimeMillis() / 1000L).toString()

                        showAutoDismissSuccess(activity) {
                            showVervolgtellingDialog(activity, eindtijd)
                        }
                    }
                    is TellingAfrondHandler.AfrondResult.Failure -> {
                        _uploadState.value = UploadState.Error(result.message)
                        val dlg = AlertDialog.Builder(activity)
                            .setTitle(result.title)
                            .setMessage(result.message)
                            .setPositiveButton("OK", null)
                            .show()
                        DialogStyler.apply(dlg)
                    }
                }
            }
        }
    }

    private fun showAutoDismissSuccess(activity: AppCompatActivity, onDismissed: () -> Unit) {
        val successDialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.dialog_finish_success))
            .setMessage(activity.getString(R.string.afrond_upload_success))
            .setCancelable(false)
            .create()

        successDialog.show()
        DialogStyler.apply(successDialog)

        scope.launch {
            delay(SUCCESS_DIALOG_DELAY_MS)
            withContext(Dispatchers.Main) {
                if (successDialog.isShowing) {
                    successDialog.dismiss()
                }
                onDismissed()
            }
        }
    }

    private fun showVervolgtellingDialog(activity: AppCompatActivity, eindtijdEpoch: String) {
        val dlg = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.afrond_vervolgtelling_titel))
            .setMessage(activity.getString(R.string.afrond_vervolgtelling_msg))
            .setPositiveButton("OK") { _, _ ->
                val intent = Intent(activity, MetadataScherm::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                intent.putExtra(MetadataScherm.EXTRA_VERVOLG_BEGINTIJD_EPOCH, eindtijdEpoch)
                intent.putExtra(MetadataScherm.EXTRA_PRESERVE_MASTER_CLIENT_SESSION, true)
                activity.startActivity(intent)
                activity.finish()
            }
            .setNegativeButton(activity.getString(R.string.dlg_cancel)) { _, _ ->
                MasterClientRuntimeStore.clearAll()
                val intent = Intent(activity, HoofdActiviteit::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                activity.startActivity(intent)
                activity.finish()
            }
            .setCancelable(false)
            .show()
        DialogStyler.apply(dlg)
    }

    /**
     * Upload een envelope naar de server via de centrale TellingUploadCore.
     *
     * Gebruikt TellingUploadCore.uploadPrepared() voor consistente afhandeling van:
     * - Credentials (via CredentialsStore)
     * - Sanitizing van records
     * - Mutex voor thread-veiligheid
     * - OnlineId persistence
     * - TellingUploadFlags.markSent()
     * - UploadedObservationStateStore updates
     *
     * @param envelope De envelope om te uploaden
     * @param username Gebruikersnaam (optioneel, anders uit CredentialsStore)
     * @param password Wachtwoord (optioneel, anders uit CredentialsStore)
     * @param baseUrl  Basis URL (wordt genegeerd, TellingUploadCore gebruikt vaste URL)
     * @return Result met onlineId bij succes, of fout bij mislukken
     */
    suspend fun upload(
        envelope: ServerTellingEnvelope,
        username: String,
        password: String,
        baseUrl: String
    ): Result<String> = try {
        _uploadState.value = UploadState.Uploading

        // Bereid envelope voor met TellingUploadCore (sanitizing, onlineId, timestamp)
        val preparedEnvelope = uploadCore.prepareEnvelopeForUpload(
            sourceEnvelope = envelope,
            useStoredOnlineIdWhenBlank = true
        )

        // Voer upload uit via centrale uploadkern
        val result = uploadCore.uploadPrepared(
            TellingUploadCore.UploadRequest(
                mode = TellingUploadCore.Mode.FINALIZE,
                preparedEnvelope = preparedEnvelope,
                credentials = TellingUploadCore.Credentials(username, password),
                persistReturnedOnlineId = true,
                persistPreparedEnvelopeToPrefs = true,
                markTellingSent = true
            )
        )

        if (result.success) {
            val onlineId = result.effectiveOnlineId ?: preparedEnvelope.onlineid
            _uploadState.value = UploadState.Success(onlineId)
            Result.success(onlineId)
        } else {
            _uploadState.value = UploadState.Error(result.errorMessage ?: result.responseText)
            Result.failure(Exception(result.errorMessage ?: result.responseText))
        }
    } catch (e: Exception) {
        Log.e(TAG, "Upload failed", e)
        _uploadState.value = UploadState.Error(e.message ?: "Unknown error", e)
        Result.failure(e)
    }

    fun resetState() {
        _uploadState.value = UploadState.Idle
    }
}
