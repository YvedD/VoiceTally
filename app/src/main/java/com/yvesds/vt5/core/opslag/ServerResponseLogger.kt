package com.yvesds.vt5.core.opslag

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bewaart serverresponses van `counts_save` als pretty-printed JSON in `/VT5/logs`.
 *
 * - Probeert eerst de SAF-map `Documents/VT5/logs`
 * - Valt terug op interne opslag `files/VT5/logs`
 * - Als de response geen geldig JSON is, wordt de ruwe payload ingepakt in een JSON-wrapper
 */
class ServerResponseLogger(
    private val context: Context
) {
    companion object {
        private const val TAG = "ServerResponseLogger"
        private const val VT5_DIR = "VT5"
        private const val LOG_DIR = "logs"

        private val PRETTY_JSON = Json {
            prettyPrint = true
            encodeDefaults = true
        }
    }

    suspend fun logCountsSaveResponse(
        mode: String,
        tellingId: String,
        onlineId: String?,
        responseText: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val now = Date()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(now)
            val safeMode = mode.lowercase(Locale.US).replace(Regex("[^a-z0-9_-]"), "_")
            val safeTellingId = tellingId.ifBlank { "unknown-telling" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val safeOnlineId = onlineId?.ifBlank { null }
                ?.replace(Regex("[^A-Za-z0-9_-]"), "_")
                ?: "no-onlineid"
            val fileName = "${timestamp}_counts_save_${safeMode}_${safeTellingId}_${safeOnlineId}.json"
            val prettyPayload = prettyPrintResponse(responseText)

            val saf = SaFStorageHelper(context)
            var vt5Dir: DocumentFile? = saf.getVt5DirIfExists()
            if (vt5Dir == null) {
                try {
                    saf.ensureFolders()
                } catch (_: Exception) {
                }
                vt5Dir = saf.getVt5DirIfExists()
            }

            if (vt5Dir != null) {
                val logsDir = saf.findOrCreateDirectory(vt5Dir, LOG_DIR) ?: vt5Dir
                val created = logsDir.createFile("application/json", fileName) ?: return@withContext null
                context.contentResolver.openOutputStream(created.uri)?.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer?.write(prettyPayload)
                    writer?.flush()
                }
                return@withContext "Documents/VT5/logs/$fileName"
            }

            val root = File(context.filesDir, VT5_DIR)
            if (!root.exists()) root.mkdirs()
            val logsDir = File(root, LOG_DIR)
            if (!logsDir.exists()) logsDir.mkdirs()
            val target = File(logsDir, fileName)
            target.writeText(prettyPayload, Charsets.UTF_8)
            return@withContext "internal:${target.absolutePath}"
        } catch (e: Exception) {
            Log.w(TAG, "Kon serverresponse niet loggen: ${e.message}", e)
            return@withContext null
        }
    }

    private fun prettyPrintResponse(responseText: String): String {
        return try {
            val element = Json.parseToJsonElement(responseText)
            PRETTY_JSON.encodeToString(JsonElement.serializer(), element)
        } catch (_: Exception) {
            PRETTY_JSON.encodeToString(
                JsonElement.serializer(),
                buildJsonObject {
                    put("rawResponse", responseText)
                    put("note", "Response was not valid JSON; payload wrapped for logging.")
                }
            )
        }
    }
}

