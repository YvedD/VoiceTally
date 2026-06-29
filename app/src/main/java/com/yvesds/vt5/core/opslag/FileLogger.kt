package com.yvesds.vt5.core.opslag

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FileLogger - Schrijft logs naar de /VT5/logs/ map op het toestel.
 * Wordt gebruikt voor on-site debugging en migratie-tracking.
 */
class FileLogger(private val context: Context) {
    companion object {
        private const val TAG = "FileLogger"
        private const val LOG_DIR = "logs"
        private const val VT5_DIR = "VT5"
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

    /**
     * Schrijft een logregel naar het bestand van vandaag.
     * 
     * @param message De te loggen boodschap
     * @param level Het log-niveau (INFO, ERROR, WARN, etc.)
     */
    suspend fun log(message: String, level: String = "INFO") = withContext(Dispatchers.IO) {
        try {
            val timestamp = dateFormat.format(Date())
            val dateStr = fileDateFormat.format(Date())
            val filename = "migration_log_$dateStr.txt"
            
            val saf = SaFStorageHelper(context)
            val vt5Dir = saf.getVt5DirIfExists()
            
            val logLine = "[$timestamp] [$level] $message\n"

            if (vt5Dir != null) {
                // Probeer via SAF
                val logDir = saf.findOrCreateDirectory(vt5Dir, LOG_DIR)
                if (logDir != null) {
                    val logFile = logDir.findFile(filename) ?: logDir.createFile("text/plain", filename)
                    if (logFile != null) {
                        context.contentResolver.openOutputStream(logFile.uri, "wa")?.use { out ->
                            out.write(logLine.toByteArray(Charsets.UTF_8))
                        }
                    }
                }
            } else {
                // Fallback naar internal storage als SAF nog niet is ingesteld
                val root = File(context.filesDir, VT5_DIR)
                val logDir = File(root, LOG_DIR)
                if (!logDir.exists()) logDir.mkdirs()
                
                val file = File(logDir, filename)
                file.appendText(logLine, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            // Als file logging faalt, vallen we terug op Logcat voor debugging
            Log.e(TAG, "Niet in staat om naar logbestand te schrijven: ${e.message}")
        }
    }

    suspend fun info(message: String) = log(message, "INFO")
    suspend fun error(message: String) = log(message, "ERROR")
    suspend fun warn(message: String) = log(message, "WARN")
}
