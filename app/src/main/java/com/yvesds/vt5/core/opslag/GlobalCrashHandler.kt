package com.yvesds.vt5.core.opslag

import android.content.Context
import android.os.Build
import android.util.Log
import com.yvesds.vt5.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * GlobalCrashHandler - Vangt onbehandelde fatale crashes op op alle threads.
 * 
 * Eigenschappen:
 * - Schrijft een uitgebreide diagnostische crashlog SYNCHROON weg.
 * - Flusht direct naar schijf om gegevensverlies bij procesbeëindiging te voorkomen.
 * - Slaat op in Documents/VT5/errorlogs/ (SAF) of interne opslag als fallback.
 * - Draagt daarna de controle over aan de standaard Android crash handler.
 */
class GlobalCrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "GlobalCrashHandler"
        private const val ERRORLOGS_DIR = "errorlogs"
        private const val VT5_DIR = "VT5"

        /**
         * Registreert de GlobalCrashHandler als de standaard UncaughtExceptionHandler voor de app.
         */
        fun register(context: Context) {
            val currentDefault = Thread.getDefaultUncaughtExceptionHandler()
            if (currentDefault !is GlobalCrashHandler) {
                val customHandler = GlobalCrashHandler(context.applicationContext, currentDefault)
                Thread.setDefaultUncaughtExceptionHandler(customHandler)
                Log.i(TAG, "GlobalCrashHandler succesvol geregistreerd als UncaughtExceptionHandler")
            }
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // Synchrone wegschrijfopdracht van de crashlog
            writeCrashLogSync(thread, throwable)
        } catch (ex: Exception) {
            Log.e(TAG, "Niet in staat om fatale crashlog synchroon weg te schrijven: ${ex.message}", ex)
        } finally {
            // Overdragen aan de originele Android OS crash handler
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLogSync(thread: Thread, throwable: Throwable) {
        val now = Date()
        val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

        val timeStr = timestampFormat.format(now)
        val fileTimeStr = fileDateFormat.format(now)
        val filename = "fatal_crash_$fileTimeStr.log"

        val runtime = Runtime.getRuntime()
        val freeMemMb = runtime.freeMemory() / (1024 * 1024)
        val totalMemMb = runtime.totalMemory() / (1024 * 1024)
        val maxMemMb = runtime.maxMemory() / (1024 * 1024)

        val sb = StringBuilder()
        sb.append("=================================================================\n")
        sb.append("VT5 FATALE CRASH DIAGNOSTIEK - UNCAUGHT EXCEPTION REPORT\n")
        sb.append("=================================================================\n")
        sb.append("Tijdstempel     : $timeStr\n")
        sb.append("Thread          : ${thread.name} (ID: ${thread.id})\n")
        sb.append("App Versie      : ${BuildConfig.VERSION_NAME} (BuildCode: ${BuildConfig.VERSION_CODE})\n")
        sb.append("Android Versie  : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        sb.append("Merk / Model    : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n")
        sb.append("Geheugen (MB)   : Vrij: $freeMemMb MB | Totaal: $totalMemMb MB | Max: $maxMemMb MB\n")
        sb.append("-----------------------------------------------------------------\n")
        sb.append("EXCEPTION DETAILS:\n")
        sb.append("Klasse          : ${throwable.javaClass.name}\n")
        sb.append("Boodschap       : ${throwable.message}\n")
        sb.append("-----------------------------------------------------------------\n")
        sb.append("STACKTRACE:\n")
        sb.append(throwable.stackTraceToString())
        sb.append("\n=================================================================\n")

        val payload = sb.toString().toByteArray(Charsets.UTF_8)

        var writtenSuccess = false

        // 1. Probeer SAF schrijven via SaFStorageHelper
        try {
            val saf = SaFStorageHelper(context)
            val errorLogsDir = saf.getErrorLogsDir()
            if (errorLogsDir != null) {
                val file = errorLogsDir.createFile("text/plain", filename)
                if (file != null) {
                    context.contentResolver.openOutputStream(file.uri, "w")?.use { out ->
                        out.write(payload)
                        out.flush()
                    }
                    writtenSuccess = true
                    Log.i(TAG, "Fatale crashlog geschreven naar SAF: ${file.uri}")
                }
            }
        } catch (safEx: Exception) {
            Log.e(TAG, "SAF schrijfactie gefaald tijdens crash: ${safEx.message}")
        }

        // 2. Fallback naar interne opslag als SAF mislukte of nog niet geconfigureerd is
        if (!writtenSuccess) {
            try {
                val root = File(context.filesDir, VT5_DIR)
                val dir = File(root, ERRORLOGS_DIR)
                if (!dir.exists()) dir.mkdirs()

                val file = File(dir, filename)
                FileOutputStream(file).use { out ->
                    out.write(payload)
                    out.flush()
                }
                Log.i(TAG, "Fatale crashlog geschreven naar interne opslag fallback: ${file.absolutePath}")
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Interne opslag fallback schrijfactie gefaald: ${fallbackEx.message}")
            }
        }
    }
}
