package com.yvesds.vt5.features.telling

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.net.ServerTellingDataItem
import com.yvesds.vt5.net.ServerTellingEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Date

/**
 * TellingAfrondHandler: Handles the complete "Afronden" (finalize) flow for TellingScherm.
 * 
 * Responsibilities:
 * - Building the final envelope with counts
 * - Uploading to server
 * - Handling server response
 * - Cleanup of temporary files and preferences
 * - Cleanup of active_telling.json (continuous backup)
 * - Error handling and user feedback
 */
class TellingAfrondHandler(
    private val context: Context,
    private val backupManager: TellingBackupManager,
    private val dataProcessor: TellingDataProcessor,
    private val envelopePersistence: TellingEnvelopePersistence? = null
) {
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
     * @param pendingRecords List of records to include in the envelope
     * @param pendingBackupDocs List of backup document files to clean up on success
     * @param pendingBackupInternalPaths List of internal backup paths to clean up on success
     * @param metadataUpdates Optional metadata updates (begintijd, eindtijd, opmerkingen)
     */
    suspend fun handleAfronden(
        pendingRecords: List<ServerTellingDataItem>,
        pendingBackupDocs: List<DocumentFile>,
        pendingBackupInternalPaths: List<String>,
        metadataUpdates: MetadataUpdates? = null
    ): AfrondResult = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // 1. Load saved envelope
        val savedEnvelopeJson = prefs.getString(PREF_SAVED_ENVELOPE_JSON, null)
        if (savedEnvelopeJson.isNullOrBlank()) {
            return@withContext AfrondResult.Failure(
                title = "Geen metadata",
                message = "Er is geen opgeslagen metadata (counts_save header). Keer terug naar metadata en start een telling."
            )
        }

        val envelopeList = try {
            VT5App.json.decodeFromString(
                ListSerializer(ServerTellingEnvelope.serializer()), 
                savedEnvelopeJson
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed decoding saved envelope JSON: ${e.message}", e)
            null
        }
        
        if (envelopeList.isNullOrEmpty()) {
            return@withContext AfrondResult.Failure(
                title = "Ongeldige envelope",
                message = "Opgeslagen envelope ongeldig."
            )
        }

        val savedOnlineId = prefs.getString(PREF_ONLINE_ID, "")

        // 2. Build final envelope with times and records
        val nowEpoch = (System.currentTimeMillis() / 1000L)
        val nowEpochStr = nowEpoch.toString()

        val baseEnv = envelopeList[0]

        // Apply metadata updates if provided (begintijd, eindtijd, opmerkingen)
        // Otherwise use defaults: keep original begintijd, set eindtijd to now
        // Note: Use ifBlank to also handle empty string cases, not just null
        val effectiveBegintijd = metadataUpdates?.begintijd?.ifBlank { null } ?: baseEnv.begintijd
        val effectiveEindtijd = metadataUpdates?.eindtijd?.ifBlank { null } ?: nowEpochStr
        val effectiveOpmerkingen = metadataUpdates?.opmerkingen ?: baseEnv.opmerkingen
        
        val envWithTimes = baseEnv.copy(
            begintijd = effectiveBegintijd,
            eindtijd = effectiveEindtijd,
            opmerkingen = effectiveOpmerkingen
        )

        val recordsSnapshot = ArrayList(pendingRecords)
        val nrec = recordsSnapshot.size
        val nsoort = recordsSnapshot.map { it.soortid }.toSet().size

        val finalEnvDraft = envWithTimes.copy(
            nrec = nrec.toString(),
            nsoort = nsoort.toString(), 
            data = recordsSnapshot
        )

        val uploadCore = TellingUploadCore(context)
        val finalEnv = uploadCore.prepareEnvelopeForUpload(
            sourceEnvelope = finalEnvDraft,
            useStoredOnlineIdWhenBlank = true,
            now = Date()
        )
        val envelopeToSend = listOf(finalEnv)

        // 3. Pretty print and save envelope
        val onlineIdPref = savedOnlineId ?: ""
        val prettyJson = try {
            PRETTY_JSON.encodeToString(
                ListSerializer(ServerTellingEnvelope.serializer()), 
                envelopeToSend
            )
        } catch (e: Exception) {
            Log.w(TAG, "pretty encode failed: ${e.message}", e)
            null
        }

        var savedPrettyPath: String? = null
        if (prettyJson != null) {
            savedPrettyPath = backupManager.writePrettyEnvelopeToSaf(
                onlineIdPref.ifBlank { "unknown" }, 
                prettyJson
            ) ?: backupManager.writePrettyEnvelopeInternal(
                onlineIdPref.ifBlank { "unknown" }, 
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
            
            // Save the FINAL envelope (with correct eindtijd) to counts folder
            // This ensures the saved JSON is IDENTICAL to what was sent to the server
            try {
                val tellingId = finalEnv.tellingid
                val archiveOnlineId = effectiveOnlineId.ifBlank { finalEnv.onlineid }
                if (prettyJson != null) {
                    envelopePersistence?.saveFinalEnvelopeToCountsDir(tellingId, archiveOnlineId, prettyJson)
                } else {
                    // Fallback: archive the old way if prettyJson is null
                    envelopePersistence?.archiveSavedEnvelope(tellingId, archiveOnlineId)
                }
            } catch (ex: Exception) {
                Log.w(TAG, "Failed to save final envelope to counts: ${ex.message}", ex)
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
