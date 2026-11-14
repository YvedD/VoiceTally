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
 * ErrorLogger - Verwerkt en bewaart NIET-FATALE uitzonderingen en foutmeldingen.
 * 
 * Eigenschappen:
 * - Filtert uitsluitend ERROR-niveau (negeert WARN, INFO, DEBUG).
 * - Schrijft weg naar Documents/VT5/errorlogs/error_log_YYYYMMDD.log via SAF.
 * - Gebruikt een interne opslag-fallback (files/VT5/errorlogs/) als SAF nog niet beschikbaar is.
 * - Biedt status-tracking (hernoemen naar *.sent.log na e-mailen).
 */
class ErrorLogger(private val context: Context) {
    companion object {
        private const val TAG = "ErrorLogger"
        private const val ERRORLOGS_DIR = "errorlogs"
        private const val VT5_DIR = "VT5"

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        private val fileDateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

        /**
         * Statische utility functie voor snelle aanroep zonder instantie.
         */
        suspend fun logError(context: Context, tag: String, message: String, throwable: Throwable? = null) {
            ErrorLogger(context.applicationContext).error(tag, message, throwable)
        }

        /**
         * Markeer een logbestand als verzonden door de extensie te wijzigen in .sent.log.
         */
        suspend fun markAsSent(context: Context, filename: String): Boolean = withContext(Dispatchers.IO) {
            try {
                if (filename.isBlank() || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                    return@withContext false
                }
                
                val saf = SaFStorageHelper(context)
                val errorLogsDir = saf.getErrorLogsDir()
                
                if (errorLogsDir != null) {
                    val file = errorLogsDir.findFile(filename)
                    if (file != null && file.name?.endsWith(".sent.log") == false) {
                        val newName = file.name!!.replace(".log", ".sent.log")
                        return@withContext file.renameTo(newName)
                    }
                }

                // Fallback: Interne opslag
                val internalDir = File(File(context.filesDir, VT5_DIR), ERRORLOGS_DIR)
                val internalFile = File(internalDir, filename)
                if (internalFile.exists() && !internalFile.name.endsWith(".sent.log")) {
                    val newTarget = File(internalDir, internalFile.name.replace(".log", ".sent.log"))
                    return@withContext internalFile.renameTo(newTarget)
                }

                false
            } catch (ex: Exception) {
                Log.e(TAG, "Kon bestand $filename niet markeren als verzonden: ${ex.message}")
                false
            }
        }
    }

    /**
     * Schrijft een niet-fatale error logregel weg.
     * 
     * @param tag De tag/component waar de fout optrad
     * @param message Foutbeschrijving
     * @param throwable Optionele uitzondering
     */
    suspend fun error(tag: String, message: String, throwable: Throwable? = null) = withContext(Dispatchers.IO) {
        try {
            val timestamp = dateFormat.format(Date())
            val dateStr = fileDateFormat.format(Date())
            val filename = "error_log_$dateStr.log"
            val threadName = Thread.currentThread().name

            val sb = StringBuilder()
            sb.append("[$timestamp] [$threadName] [$tag] $message\n")
            if (throwable != null) {
                sb.append("   Exception: ${throwable.javaClass.name}: ${throwable.message}\n")
                sb.append(throwable.stackTraceToString())
                sb.append("\n")
            }
            val logPayload = sb.toString()

            val saf = SaFStorageHelper(context)
            val errorLogsDir = saf.getErrorLogsDir()

            if (errorLogsDir != null) {
                // Schrijf via SAF
                val logFile = errorLogsDir.findFile(filename) ?: errorLogsDir.createFile("text/plain", filename)
                if (logFile != null) {
                    context.contentResolver.openOutputStream(logFile.uri, "wa")?.use { out ->
                        out.write(logPayload.toByteArray(Charsets.UTF_8))
                    }
                }
            } else {
                // Fallback naar interne opslag als SAF nog niet geconfigureerd is
                val root = File(context.filesDir, VT5_DIR)
                val dir = File(root, ERRORLOGS_DIR)
                if (!dir.exists()) dir.mkdirs()

                val file = File(dir, filename)
                file.appendText(logPayload, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            // Valt terug op Android Logcat als schrijven naar schijf mislukt
            Log.e(TAG, "Niet in staat om niet-fatale error te loggen: ${e.message}", e)
        }
    }
}
