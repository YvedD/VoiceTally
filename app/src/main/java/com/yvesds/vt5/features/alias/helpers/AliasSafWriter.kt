package com.yvesds.vt5.features.alias.helpers

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.FileNotFoundException

/**
 * AliasSafWriter: Handles safe writing to SAF DocumentFiles.
 * 
 * Responsibilities:
 * - Write binary data to SAF DocumentFile with error handling
 * - Write text data to SAF DocumentFile
 * - Copy data to exports folder for user accessibility
 * 
 * All write operations are safe and never throw - they return success boolean.
 */
object AliasSafWriter {
    
    private const val TAG = "AliasSafWriter"
    
    /**
     * Safely write binary data to a DocumentFile.
     * Returns true if successful, false otherwise.
     * Never throws exceptions.
     */
    fun safeWriteToDocument(context: Context, doc: DocumentFile?, data: ByteArray): Boolean {
        if (doc == null) return false
        
        return try {
            // doc.exists() may throw for some providers; guard it
            runCatching { doc.exists() }.getOrDefault(false)
            
            context.contentResolver.openOutputStream(doc.uri, "w")?.use { os ->
                os.write(data)
                os.flush()
            } ?: run {
                Log.w(TAG, "safeWriteToDocument: openOutputStream returned null for ${doc.uri}")
                return false
            }
            true
        } catch (fnf: FileNotFoundException) {
            Log.w(TAG, "safeWriteToDocument FileNotFound for ${doc.uri}: ${fnf.message}")
            false
        } catch (sec: SecurityException) {
            Log.w(TAG, "safeWriteToDocument SecurityException for ${doc.uri}: ${sec.message}")
            false
        } catch (ex: Exception) {
            Log.w(TAG, "safeWriteToDocument failed for ${doc.uri}: ${ex.message}", ex)
            false
        }
    }
    
    /**
     * Safely write text to a DocumentFile (UTF-8).
     */
    fun safeWriteTextToDocument(context: Context, doc: DocumentFile?, text: String): Boolean {
        return safeWriteToDocument(context, doc, text.toByteArray(Charsets.UTF_8))
    }
}
