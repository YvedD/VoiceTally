package com.yvesds.vt5.features.telling

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.net.ServerTellingEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "AfrondWorker"

/**
 * Worker responsible for uploading a pending telling.
 * - It uses RecordsBeheer to obtain pending records (restores index first).
 * - Reads saved envelope from prefs (same as Activity).
 * - Builds final envelope, posts to TrektellenApi.
 * - Writes audit file and pretty envelope JSON (SAF or internal).
 * - On success: clears pending records/backups using RecordsBeheer.
 *
 * Input: none (reads prefs/index)
 */
class AfrondWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            val context = applicationContext
            val recordsBeheer = RecordsBeheer(context)

            // Restore any index persisted to disk, so repository holds latest
            recordsBeheer.restorePendingIndex()

            // Read saved envelope JSON
            val prefs = context.getSharedPreferences("vt5_prefs", Context.MODE_PRIVATE)
            val savedEnvelopeJson = prefs.getString("pref_saved_envelope_json", null)
            if (savedEnvelopeJson.isNullOrBlank()) {
                Log.w(TAG, "No saved envelope JSON available (abort)")
                return Result.failure()
            }

            val envelopeList = try {
                VT5App.json.decodeFromString(ListSerializer(ServerTellingEnvelope.serializer()), savedEnvelopeJson)
            } catch (ex: Exception) {
                Log.w(TAG, "Failed to decode saved envelope JSON: ${ex.message}", ex)
                return Result.failure()
            }
            if (envelopeList.isEmpty()) {
                Log.w(TAG, "Saved envelope empty")
                return Result.failure()
            }

            // Build final envelope with pending records
            val nowEpoch = (System.currentTimeMillis() / 1000L).toString()
            val baseEnv = envelopeList[0]
            val envWithTimes = baseEnv.copy(eindtijd = nowEpoch)

            // Ensure repository snapshot is fresh
            val recordsSnapshot = recordsBeheer.getPendingRecordsSnapshot()
            val nrec = recordsSnapshot.size
            val nsoort = recordsSnapshot.map { it.soortid }.toSet().size

            val uploadCore = TellingUploadCore(context)
            val finalEnv = uploadCore.prepareEnvelopeForUpload(
                sourceEnvelope = envWithTimes.copy(
                    nrec = nrec.toString(),
                    nsoort = nsoort.toString(),
                    data = recordsSnapshot
                ),
                useStoredOnlineIdWhenBlank = true,
                now = Date()
            )
            val envelopeToSend = listOf(finalEnv)

            // Pretty JSON to save
            val prettyJson = try {
                VT5App.json.encodeToString(ListSerializer(ServerTellingEnvelope.serializer()), envelopeToSend)
            } catch (ex: Exception) {
                Log.w(TAG, "Pretty encode failed: ${ex.message}", ex)
                null
            }

            val uploadResult = uploadCore.uploadPrepared(
                TellingUploadCore.UploadRequest(
                    mode = TellingUploadCore.Mode.WORKER_FINALIZE,
                    preparedEnvelope = finalEnv,
                    persistReturnedOnlineId = true,
                    persistPreparedEnvelopeToPrefs = true,
                    markTellingSent = false
                )
            )
            val resp = uploadResult.responseText

            // Write audit file (attempt best-effort)
            writeEnvelopeResponseToSaf(context, finalEnv.tellingid, prettyJson ?: "{}", resp)

            if (!uploadResult.success) {
                Log.w(TAG, "Upload failed (will retry): ${uploadResult.errorMessage ?: resp}")
                // On failure, keep pending records intact and retry later
                return Result.retry()
            }

            // Success: clear pending records + backups
            recordsBeheer.clearPendingRecordsAndBackups()
            
            // Archive the active_telling.json (rename to timestamped file in counts folder)
            try {
                val saf = SaFStorageHelper(context)
                val envelopePersistence = TellingEnvelopePersistence(context, saf)
                val tellingId = finalEnv.tellingid
                val archiveOnlineId = uploadResult.effectiveOnlineId ?: prefs.getString("pref_online_id", null) ?: finalEnv.onlineid
                envelopePersistence.archiveSavedEnvelope(tellingId, archiveOnlineId)
            } catch (ex: Exception) {
                Log.w(TAG, "Failed to archive active_telling.json: ${ex.message}", ex)
            }

            // Save pretty envelope to SAF/internal for audit
            if (prettyJson != null) {
                // prefer SAF when possible
                try {
                    writePrettyEnvelopeToSaf(context, prefs.getString("pref_online_id", "unknown") ?: "unknown", prettyJson)
                } catch (ex: Exception) {
                    Log.w(TAG, "writePrettyEnvelopeToSaf (worker) failed: ${ex.message}", ex)
                }
            }

            // Worker succeeded
            return Result.success()
        } catch (ex: Exception) {
            Log.w(TAG, "AfrondWorker failed: ${ex.message}", ex)
            // Let WorkManager handle retry/backoff
            return Result.retry()
        }
    }

    // Helper: write pretty envelope JSON to SAF or internal (copied logic)
    private suspend fun writePrettyEnvelopeToSaf(context: Context, onlineId: String, prettyJson: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val saf = SaFStorageHelper(context)
                var vt5Dir: DocumentFile? = saf.getVt5DirIfExists()
                if (vt5Dir == null) {
                    try { saf.ensureFolders() } catch (_: Exception) {}
                    vt5Dir = saf.getVt5DirIfExists()
                }
                if (vt5Dir != null) {
                    val exportsDir = saf.findOrCreateDirectory(vt5Dir, "exports") ?: vt5Dir
                    val nowStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val safeName = "${nowStr}_count_${onlineId}.json"
                    val created = exportsDir.createFile("application/json", safeName) ?: return@withContext null
                    context.contentResolver.openOutputStream(created.uri)?.bufferedWriter(Charsets.UTF_8).use { w ->
                        w?.write(prettyJson)
                        w?.flush()
                    }
                    return@withContext "Documents/VT5/exports/$safeName"
                } else {
                    val root = File(context.filesDir, "VT5")
                    if (!root.exists()) root.mkdirs()
                    val exports = File(root, "exports"); if (!exports.exists()) exports.mkdirs()
                    val nowStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val filename = "${nowStr}_count_${onlineId}.json"
                    val f = File(exports, filename)
                    f.writeText(prettyJson, Charsets.UTF_8)
                    return@withContext "internal:${f.absolutePath}"
                }
            } catch (e: Exception) {
                Log.w(TAG, "writePrettyEnvelopeToSaf failed: ${e.message}", e)
                return@withContext null
            }
        }
    }

    // Helper: write envelope + response audit
    private suspend fun writeEnvelopeResponseToSaf(context: Context, tellingId: String, envelopeJson: String, responseText: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val saf = SaFStorageHelper(context)
                var vt5Dir: DocumentFile? = saf.getVt5DirIfExists()
                if (vt5Dir == null) {
                    try { saf.ensureFolders() } catch (_: Exception) {}
                    vt5Dir = saf.getVt5DirIfExists()
                }
                if (vt5Dir != null) {
                    val exportsDir = saf.findOrCreateDirectory(vt5Dir, "exports") ?: vt5Dir
                    val nowStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val safeName = "counts_save_response_${tellingId}_$nowStr.txt"
                    val created = exportsDir.createFile("text/plain", safeName) ?: return@withContext null
                    context.contentResolver.openOutputStream(created.uri)?.bufferedWriter(Charsets.UTF_8).use { w ->
                        w?.write("=== Envelope ===\n")
                        w?.write(envelopeJson)
                        w?.write("\n\n=== Server response ===\n")
                        w?.write(responseText)
                        w?.flush()
                    }
                    return@withContext "Documents/VT5/exports/$safeName"
                } else {
                    val root = File(context.filesDir, "VT5")
                    if (!root.exists()) root.mkdirs()
                    val exports = File(root, "exports"); if (!exports.exists()) exports.mkdirs()
                    val nowStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val filename = "counts_save_response_${tellingId}_$nowStr.txt"
                    val f = File(exports, filename)
                    f.bufferedWriter(Charsets.UTF_8).use { w ->
                        w.write("=== Envelope ===\n")
                        w.write(envelopeJson)
                        w.write("\n\n=== Server response ===\n")
                        w.write(responseText)
                        w.flush()
                    }
                    return@withContext "internal:${f.absolutePath}"
                }
            } catch (e: Exception) {
                Log.w(TAG, "writeEnvelopeResponseToSaf failed: ${e.message}", e)
                return@withContext null
            }
        }
    }
}