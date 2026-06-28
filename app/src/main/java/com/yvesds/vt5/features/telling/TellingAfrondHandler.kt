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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Date

/**
 * TellingAfrondHandler: Handles the complete "Afronden" (finalize) flow for TellingScherm.
 * 
 * Responsibilities:
 * - Building the final envelope with counts from Room (Hybrid System)
 * - Uploading to server
 * - Handling server response
 * - Cleanup of temporary files and preferences
 * - Cleanup of active_telling.json (historical)
 * - Error handling and user feedback
 */
class TellingAfrondHandler(
    private val context: Context,
    private val backupManager: TellingBackupManager,
    private val dataProcessor: TellingDataProcessor,
    private val envelopePersistence: TellingEnvelopePersistence? = null
) {
    private val database by lazy { VoiceTallyDatabase.getDatabase(context) }
    private val hybridRepository by lazy { HybridTellingRepository(context) }

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
     * Uses Room as the primary source of truth for records and metadata.
     * 
     * @param pendingRecordsSnapshot Snaphot of records from memory (as backup/sync source)
     * @param pendingBackupDocs List of backup document files to clean up on success
     * @param pendingBackupInternalPaths List of internal backup paths to clean up on success
     * @param metadataUpdates Optional metadata updates (begintijd, eindtijd, opmerkingen)
     */
    suspend fun handleAfronden(
        pendingRecordsSnapshot: List<ServerTellingDataItem>,
        pendingBackupDocs: List<DocumentFile>,
        pendingBackupInternalPaths: List<String>,
        metadataUpdates: MetadataUpdates? = null
    ): AfrondResult = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val tellingId = prefs.getString(PREF_TELLING_ID, null)

        // 1. Fetch metadata and records from Room (Hybride Systeem source of truth)
        val header = if (!tellingId.isNullOrBlank()) {
            database.tellingDao().getHeader(tellingId)
        } else null

        val roomRecords = if (!tellingId.isNullOrBlank()) {
            database.tellingDao().getWaarnemingenList(tellingId)
        } else emptyList()

        val baseEnv = if (header != null) {
            // Build envelope from database state
            header.toServerEnvelope(roomRecords)
        } else {
            // LEGACY FALLBACK: Use saved JSON if Room header is missing
            val savedEnvelopeJson = prefs.getString(PREF_SAVED_ENVELOPE_JSON, null)
            if (savedEnvelopeJson.isNullOrBlank()) {
                return@withContext AfrondResult.Failure(
                    title = "Geen metadata",
                    message = "Er is geen opgeslagen metadata gevonden in Room of JSON. Keer terug naar metadata en start een telling."
                )
            }

            val envelopeList = try {
                VT5App.json.decodeFromString(
                    ListSerializer(ServerTellingEnvelope.serializer()), 
                    savedEnvelopeJson
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed decoding saved envelope JSON fallback: ${e.message}", e)
                null
            }
            
            if (envelopeList.isNullOrEmpty()) {
                return@withContext AfrondResult.Failure(
                    title = "Ongeldige metadata",
                    message = "Metadata in database ontbreekt en JSON fallback is ongeldig."
                )
            }
            envelopeList[0]
        }

        val savedOnlineId = prefs.getString(PREF_ONLINE_ID, baseEnv.onlineid)

        // 2. Build final envelope with times and records
        val nowEpoch = (System.currentTimeMillis() / 1000L)
        val nowEpochStr = nowEpoch.toString()

        // Apply metadata updates if provided
        val effectiveBegintijd = metadataUpdates?.begintijd?.ifBlank { null } ?: baseEnv.begintijd
        val effectiveEindtijd = metadataUpdates?.eindtijd?.ifBlank { null } ?: nowEpochStr
        val effectiveOpmerkingen = SessionRemarksMarker.remove(metadataUpdates?.opmerkingen ?: baseEnv.opmerkingen)
        
        // Combine current Room records (primary) with base metadata
        val finalRecords = if (roomRecords.isNotEmpty()) {
            roomRecords.map { it.toServerItem() }
        } else {
            // Fallback to memory snapshot if Room is somehow empty but memory has data
            pendingRecordsSnapshot
        }

        val nrec = finalRecords.size
        val nsoort = finalRecords.map { it.soortid }.filter { it.isNotBlank() }.toSet().size

        val finalEnvDraft = baseEnv.copy(
            begintijd = effectiveBegintijd,
            eindtijd = effectiveEindtijd,
            opmerkingen = effectiveOpmerkingen,
            nrec = nrec.toString(),
            nsoort = nsoort.toString(), 
            data = finalRecords
        )

        val uploadCore = TellingUploadCore(context)
        val finalEnv = uploadCore.prepareEnvelopeForUpload(
            sourceEnvelope = finalEnvDraft,
            useStoredOnlineIdWhenBlank = true,
            now = Date()
        )
        val envelopeToSend = listOf(finalEnv)

        // 3. Pretty print and save envelope (historical JSON archive)
        val prettyJson = try {
            PRETTY_JSON.encodeToString(
                ListSerializer(ServerTellingEnvelope.serializer()), 
                envelopeToSend
            )
        } catch (e: Exception) {
            Log.w(TAG, "pretty encode failed: ${e.message}", e)
            null
        }

        val onlineIdForFilename = finalEnv.onlineid.ifBlank { 
            savedOnlineId?.ifBlank { "unknown" } ?: "unknown" 
        }
        var savedPrettyPath: String? = null
        if (prettyJson != null) {
            savedPrettyPath = backupManager.writePrettyEnvelopeToSaf(
                onlineIdForFilename, 
                prettyJson
            ) ?: backupManager.writePrettyEnvelopeInternal(
                onlineIdForFilename, 
                prettyJson
            )
        }

        // 4. Upload to server via centrale uploadkern
        val uploadResult = uploadCore.uploadPrepared(
            TellingUploadCore.UploadRequest(
                mode = TellingUploadCore.Mode.FINALIZE,
                preparedEnvelope = finalEnv,
                persistReturnedOnlineId = true,
                persistPreparedEnvelopeToPrefs = true,
                markTellingSent = true
            )
        )
        val resp = uploadResult.responseText

        // 5. Write audit file
        val auditPath = try {
            backupManager.writeEnvelopeResponseToSaf(
                finalEnv.tellingid, 
                prettyJson ?: "{}", 
                resp
            ) ?: backupManager.writeEnvelopeResponseInternal(
                finalEnv.tellingid, 
                prettyJson ?: "{}", 
                resp
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write audit: ${e.message}", e)
            null
        }

        // 6. Handle result
        if (!uploadResult.success) {
            return@withContext AfrondResult.Failure(
                title = "Upload mislukt",
                message = "Kon telling niet uploaden:\n${uploadResult.errorMessage ?: resp}\n\n" +
                         "Envelope opgeslagen: ${savedPrettyPath ?: "niet beschikbaar"}\n" +
                         "Auditbestand: ${auditPath ?: "niet beschikbaar"}",
                savedPrettyPath = savedPrettyPath,
                auditPath = auditPath
            )
        }
        val effectiveOnlineId = uploadResult.effectiveOnlineId ?: savedOnlineId ?: finalEnv.onlineid

        // 7. Cleanup: remove backups and clear preferences
        try {
            pendingBackupDocs.forEach { doc -> 
                try { doc.delete() } catch (_: Exception) {} 
            }
            
            pendingBackupInternalPaths.forEach { path -> 
                try { java.io.File(path).delete() } catch (_: Exception) {} 
            }

            // Remove telling-related preferences
            prefs.edit {
                remove(PREF_ONLINE_ID)
                remove(PREF_TELLING_ID)
                remove(PREF_SAVED_ENVELOPE_JSON)
            }
            
            // Hybride System final actions:
            try {
                val currentTellingId = finalEnv.tellingid
                val archiveOnlineId = effectiveOnlineId.ifBlank { finalEnv.onlineid }
                
                // Room shadow update: Update the header with final times and counts
                hybridRepository.saveHeaderToRoom(finalEnv, status = "geupload")

                // Archive the JSON to counts folder (historical reference)
                if (prettyJson != null) {
                    envelopePersistence?.saveFinalEnvelopeToCountsDir(currentTellingId, archiveOnlineId, prettyJson)
                }
                
                // Final export of the database to SAF for the user
                hybridRepository.forceDatabaseBackup()
                
            } catch (ex: Exception) {
                Log.w(TAG, "Hybride cleanup actions failed: ${ex.message}", ex)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup after successful Afronden failed: ${e.message}", e)
        }

        return@withContext AfrondResult.Success(
            savedPrettyPath = savedPrettyPath,
            auditPath = auditPath
        )
    }



    /**
     * Build envelope summary for display.
     */
    fun buildEnvelopeSummary(envelope: ServerTellingEnvelope): String {
        return dataProcessor.buildEnvelopeSummary(envelope)
    }
}
