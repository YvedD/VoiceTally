package com.yvesds.vt5.core.log

import android.content.Context
import android.util.Log
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import kotlinx.coroutines.*
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * FileLogger: Schrijft logs WEG NAAR DE ROOTMAP VAN DE APP (Documents/VT5/logs) via SAF.
 * Gebruikt de SaFStorageHelper voor de juiste directory resolutie.
 */
object FileLogger {
    private val TAG = "FileLogger"
    private val logScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

    /**
     * Logt een bericht naar Logcat en (indien SAF beschikbaar is) naar een tekstbestand.
     */
    @Synchronized
    fun log(message: String, tag: String = "VT5", level: String = "D") {
        val timestamp = dateTimeFormat.format(Date())
        val line = "$timestamp [$level] $tag: $message\n"
        
        // Altijd naar Logcat
        when (level) {
            "E" -> Log.e(tag, message)
            "W" -> Log.w(tag, message)
            "I" -> Log.i(tag, message)
            "V" -> Log.v(tag, message)
            else -> Log.d(tag, message)
        }

        // Schrijf asynchroon naar VT5/logs/ map op het toestel
        logScope.launch {
            try {
                writeToSaf(line)
            } catch (e: Exception) {
                // Voorkom recursie bij fouten
            }
        }
    }

    private fun writeToSaf(text: String) {
        val context = VT5App.instance
        val saf = SaFStorageHelper(context)
        
        // 1. Zoek de VT5 map (Documents/VT5)
        val vt5Dir = saf.getVt5DirIfExists() ?: return
        
        // 2. Zoek of maak de 'logs' submap IN de VT5 map
        val logsDir = saf.findOrCreateDirectory(vt5Dir, "logs") ?: return

        // 3. Bestandsnaam op basis van datum: errorslog_20231027.txt
        val fileName = "errorslog_${fileNameFormat.format(Date())}.txt"
        
        // 4. Zoek bestaand logbestand of maak een nieuwe aan
        var logFile = logsDir.findFile(fileName)
        if (logFile == null) {
            logFile = logsDir.createFile("text/plain", fileName)
        }
        
        logFile?.let { file ->
            try {
                // Open stream in 'append' modus ("wa")
                context.contentResolver.openOutputStream(file.uri, "wa")?.use { os ->
                    OutputStreamWriter(os).use { writer ->
                        writer.append(text)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Kon niet schrijven naar SAF bestand: ${e.message}")
            }
        }
    }

    fun e(tag: String, message: String, tr: Throwable? = null) {
        val fullMessage = if (tr != null) "$message\n${Log.getStackTraceString(tr)}" else message
        log(fullMessage, tag, "E")
    }

    fun w(tag: String, message: String) = log(message, tag, "W")
    fun i(tag: String, message: String) = log(message, tag, "I")
    fun d(tag: String, message: String) = log(message, tag, "D")
    fun v(tag: String, message: String) = log(message, tag, "V")
}
