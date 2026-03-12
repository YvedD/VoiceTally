package com.yvesds.vt5.features.masterClient

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.net.ServerTellingDataItem
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * McShareHelper – offline gegevensdeling tussen client en master via een JSON-bestand.
 *
 * Gebruik voor scenario's waarbij geen LAN-verbinding beschikbaar is:
 *  - Client exporteert records naar een JSON-bestand en deelt het via het Android share-sheet.
 *  - Master importeert het JSON-bestand en voegt de records toe via [RecordsBeheer.addExternalRecord].
 *
 * Workflow:
 *  Client: [exportRecordsToFile] → [shareFile]
 *  Master: [importRecordsFromUri]
 */
object McShareHelper {

    private const val TAG              = "McShareHelper"
    private const val EXPORT_DIR       = "mc_export"
    private const val FILE_MIME_TYPE   = "application/json"
    private const val AUTHORITY_SUFFIX = ".fileprovider"

    private val json = VT5App.json

    // ─── Export (client-zijde) ────────────────────────────────────────────────

    /**
     * Schrijf de opgegeven records naar een tijdelijk JSON-bestand in de app-cache.
     * @return het aangemaakte [File], of null bij fout
     */
    fun exportRecordsToFile(context: Context, records: List<ServerTellingDataItem>): File? {
        return try {
            val dir = File(context.cacheDir, EXPORT_DIR).also { it.mkdirs() }
            val ts  = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "vt5_export_$ts.json")
            val payloadJson = json.encodeToString(
                ListSerializer(ServerTellingDataItem.serializer()),
                records
            )
            file.writeText(payloadJson, Charsets.UTF_8)
            Log.i(TAG, "Export-bestand aangemaakt: ${file.absolutePath} (${records.size} records)")
            file
        } catch (e: Exception) {
            Log.e(TAG, "exportRecordsToFile fout: ${e.message}", e)
            null
        }
    }

    /**
     * Open het Android share-sheet voor het gegeven bestand.
     * De ontvanger (master-toestel) kan het bestand openen via [importRecordsFromUri].
     */
    fun shareFile(context: Context, file: File) {
        try {
            val authority = "${context.packageName}$AUTHORITY_SUFFIX"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = FILE_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "VoiceTally export – ${file.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "VT5 export delen via…"))
        } catch (e: Exception) {
            Log.e(TAG, "shareFile fout: ${e.message}", e)
        }
    }

    // ─── Import (master-zijde) ────────────────────────────────────────────────

    /**
     * Lees records uit een JSON-bestand/URI.
     * @return de gedecodeerde lijst van [ServerTellingDataItem], of lege lijst bij fout
     */
    fun importRecordsFromUri(context: Context, uri: Uri): List<ServerTellingDataItem> {
        return try {
            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: return emptyList()
            val items = json.decodeFromString(
                ListSerializer(ServerTellingDataItem.serializer()),
                text
            )
            Log.i(TAG, "Import geslaagd: ${items.size} records geladen")
            items
        } catch (e: Exception) {
            Log.e(TAG, "importRecordsFromUri fout: ${e.message}", e)
            emptyList()
        }
    }

    /** Verwijder alle tijdelijke export-bestanden uit de cache. */
    fun clearExportCache(context: Context) {
        try {
            File(context.cacheDir, EXPORT_DIR).deleteRecursively()
        } catch (e: Exception) {
            Log.w(TAG, "clearExportCache fout: ${e.message}")
        }
    }
}
