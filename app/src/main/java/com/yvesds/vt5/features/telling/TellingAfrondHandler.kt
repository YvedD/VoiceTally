package com.yvesds.vt5.features.telling

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.repository.HybridTellingRepository
import com.yvesds.vt5.core.database.toServerEnvelope
import com.yvesds.vt5.core.database.toServerItem
import com.yvesds.vt5.net.ServerTellingDataItem
import com.yvesds.vt5.net.ServerTellingEnvelope
import com.yvesds.vt5.utils.SessionRemarksMarker
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Date

/**
 * TellingAfrondHandler: Handles the complete "Afronden" (finalize) flow for TellingScherm.
 * Refactored to separate the blocking upload from slow background post-processing.
 */
class TellingAfrondHandler(
    private val context: Context,
    private val backupManager: TellingBackupManager,
    private val dataProcessor: TellingDataProcessor,
    private val envelopePersistence: TellingEnvelopePersistence? = null
) {
    private val database by lazy { VoiceTallyDatabase.getDatabase(context) }
    private val hybridRepository by lazy { HybridTellingRepository(context) }

    // Dedicated scope for fire-and-forget background tasks (archiving, DB updates)
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "TellingAfrondHandler"
        private const val PREFS_NAME = "vt5_prefs"
        private const val PREF_SAVED_ENVELOPE_JSON = "pref_saved_envelope_json"
        private const val PREF_ONLINE_ID = "pref_online_id"
        private const val PREF_TELLING_ID = "pref_telling_id"
        
        private val PRETTY_JSON: Json by lazy { Json { prettyPrint = true; encodeDefaults = true } }
    }

    /**
     * Result of handleAfronden operation.
     */
    sealed class AfrondResult {
        data class Success(
            val savedPrettyPath: String?,
            val auditPath: String?
        ) : AfrondResult()
        
        data class Failure(
            val title: String,
            val message: String,
            val savedPrettyPath: String? = null,
            val auditPath: String? = null
        ) : AfrondResult()
    }

    /**
     * Handle the complete Afronden (finalize and upload) flow.
     * 
     * @param pendingRecordsSnapshot Snaphot of records from memory
     * @param pendingBackupDocs List of backup document files to clean up
     * @param pendingBackupInternalPaths List of internal backup paths to clean up
     * @param metadataUpdates Optional metadata updates
     * @param hotEnvelope Optional "warm" envelope from ViewModel for faster start
     */
    suspend fun handleAfronden(
        pendingRecordsSnapshot: List<ServerTellingDataItem>,
        pendingBackupDocs: List<DocumentFile>,
        pendingBackupInternalPaths: List<String>,
        metadataUpdates: MetadataUpdates? = null,
        hotEnvelope: ServerTellingEnvelope? = null
    ): AfrondResult = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val tellingId = prefs.getString(PREF_TELLING_ID, null)

        // 1. Get base envelope (prefer hot envelope from ViewModel)
        val baseEnv = if (hotEnvelope != null) {
            hotEnvelope
        } else {
            val header = if (!tellingId.isNullOrBlank()) {
                database.tellingDao().getHeader(tellingId)
            } else null

            val roomRecords = if (!tellingId.isNullOrBlank()) {
                database.tellingDao().getWaarnemingenList(tellingId)
            } else emptyList()

            if (header != null) {
                header.toServerEnvelope(roomRecords)
            } else {
                // LEGACY FALLBACK
                val savedEnvelopeJson = prefs.getString(PREF_SAVED_ENVELOPE_JSON, null)
                if (savedEnvelopeJson.isNullOrBlank()) {
                    return@withContext AfrondResult.Failure(
                        title = "Geen metadata",
                        message = "Er is geen opgeslagen metadata gevonden in Room of JSON. Keer terug naar metadata en start een telling."
                    )
                }
                val list = try {
                    VT5App.json.decodeFromString(ListSerializer(ServerTellingEnvelope.serializer()), savedEnvelopeJson)
                } catch (e: Exception) { null }
                
                if (list.isNullOrEmpty()) {
                    return@withContext AfrondResult.Failure(title = "Ongeldige metadata", message = "Metadata in database ontbreekt en JSON fallback is ongeldig.")
                }
                list[0]
            }
        }

        val savedOnlineId = prefs.getString(PREF_ONLINE_ID, baseEnv.onlineid)
        val sessionOnlineId = savedOnlineId?.ifBlank { null } ?: baseEnv.onlineid.ifBlank { null }

        if (sessionOnlineId.isNullOrBlank()) {
            return@withContext AfrondResult.Failure(
                title = "Geen online ID",
                message = "Deze telling kan niet worden afgerond omdat het onlineid ontbreekt."
            )
        }

        // 2. Build final envelope for upload
        val nowEpochStr = (System.currentTimeMillis() / 1000L).toString()
        val effectiveBegintijd = metadataUpdates?.begintijd?.ifBlank { null } ?: baseEnv.begintijd
        val effectiveEindtijd = metadataUpdates?.eindtijd?.ifBlank { null } ?: nowEpochStr
        val effectiveOpmerkingen = SessionRemarksMarker.remove(metadataUpdates?.opmerkingen ?: baseEnv.opmerkingen)
        
        val finalRecords = if (hotEnvelope != null) {
            hotEnvelope.data
        } else {
            val roomRecords = if (!tellingId.isNullOrBlank()) database.tellingDao().getWaarnemingenList(tellingId) else emptyList()
            if (roomRecords.isNotEmpty()) roomRecords.map { it.toServerItem() } else pendingRecordsSnapshot
        }

        val finalEnvDraft = baseEnv.copy(
            begintijd = effectiveBegintijd,
            eindtijd = effectiveEindtijd,
            opmerkingen = effectiveOpmerkingen,
            onlineid = sessionOnlineId,
            nrec = finalRecords.size.toString(),
            nsoort = finalRecords.map { it.soortid }.filter { it.isNotBlank() }.toSet().size.toString(),
            data = finalRecords
        )

        val uploadCore = TellingUploadCore(context)
        val finalEnv = uploadCore.prepareEnvelopeForUpload(
            sourceEnvelope = finalEnvDraft,
            useStoredOnlineIdWhenBlank = true,
            now = Date()
        )

        // 3. Upload to server (The only truly blocking part)
        val uploadResult = uploadCore.uploadPrepared(
            TellingUploadCore.UploadRequest(
                mode = TellingUploadCore.Mode.FINALIZE,
                preparedEnvelope = finalEnv,
                persistReturnedOnlineId = true,
                persistPreparedEnvelopeToPrefs = true,
                markTellingSent = true
            )
        )

        // 4. FIRE AND FORGET: Background post-processing
        if (uploadResult.success) {
            val effectiveOnlineId = uploadResult.effectiveOnlineId ?: savedOnlineId ?: finalEnv.onlineid
            performBackgroundCleanup(
                finalEnv = finalEnv,
                uploadResult = uploadResult,
                effectiveOnlineId = effectiveOnlineId,
                pendingBackupDocs = pendingBackupDocs,
                pendingBackupInternalPaths = pendingBackupInternalPaths,
                finalRecords = finalRecords
            )
            
            return@withContext AfrondResult.Success(savedPrettyPath = null, auditPath = null)
        } else {
            return@withContext AfrondResult.Failure(
                title = "Upload mislukt",
                message = "Kon telling niet uploaden:\n${uploadResult.errorMessage ?: uploadResult.responseText}"
            )
        }
    }

    /**
     * Perform slow post-processing tasks in background scope.
     */
    private fun performBackgroundCleanup(
        finalEnv: ServerTellingEnvelope,
        uploadResult: TellingUploadCore.UploadResult,
        effectiveOnlineId: String?,
        pendingBackupDocs: List<DocumentFile>,
        pendingBackupInternalPaths: List<String>,
        finalRecords: List<ServerTellingDataItem>
    ) {
        backgroundScope.launch {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val prettyJson = try {
                    PRETTY_JSON.encodeToString(ListSerializer(ServerTellingEnvelope.serializer()), listOf(uploadResult.preparedEnvelope))
                } catch (e: Exception) { null }

                // A) Archive JSON to SAF and Internal
                if (prettyJson != null) {
                    val onlineIdForFilename = effectiveOnlineId?.ifBlank { "unknown" } ?: "unknown"
                    backupManager.writePrettyEnvelopeToSaf(onlineIdForFilename, prettyJson)
                        ?: backupManager.writePrettyEnvelopeInternal(onlineIdForFilename, prettyJson)
                    
                    envelopePersistence?.saveFinalEnvelopeToCountsDir(finalEnv.tellingid, onlineIdForFilename, prettyJson)
                }

                // B) Write audit log
                if (prettyJson != null) {
                    backupManager.writeEnvelopeResponseToSaf(finalEnv.tellingid, prettyJson, uploadResult.responseText)
                        ?: backupManager.writeEnvelopeResponseInternal(finalEnv.tellingid, prettyJson, uploadResult.responseText)
                }

                // C) Cleanup temp backups and prefs
                pendingBackupDocs.forEach { try { it.delete() } catch (_: Exception) {} }
                pendingBackupInternalPaths.forEach { try { java.io.File(it).delete() } catch (_: Exception) {} }
                prefs.edit {
                    remove(PREF_ONLINE_ID)
                    remove(PREF_TELLING_ID)
                    remove(PREF_SAVED_ENVELOPE_JSON)
                }

                // D) Batch update Room (shadow update)
                hybridRepository.saveHeaderToRoom(uploadResult.preparedEnvelope, status = "geupload")
                
                // For records, we ensure consistency.
                finalRecords.forEach { hybridRepository.saveWaarnemingToRoom(it) }

                Log.i(TAG, "Background cleanup completed for telling ${finalEnv.tellingid}")
            } catch (ex: Exception) {
                Log.w(TAG, "Background cleanup failed: ${ex.message}", ex)
            }
        }
    }



    /**
     * Build envelope summary for display.
     */
    fun buildEnvelopeSummary(envelope: ServerTellingEnvelope): String {
        return dataProcessor.buildEnvelopeSummary(envelope)
    }
}
