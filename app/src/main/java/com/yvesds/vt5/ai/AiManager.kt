package com.yvesds.vt5.ai

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import com.yvesds.vt5.core.opslag.AppDataStore

/**
 * AiManager - Facade for the AI subsystem.
 * Handles initialization, update scheduling, and initial setup.
 */
object AiManager {
    private const val TAG = "AiManager"
    private const val UNIQUE_WORK_NAME = "vt5_ai_update_work"
    private val aiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Initialize AI (called from VT5App.onCreate) */
    fun init(context: Context) {
        aiScope.launch {
            // We schakelen automatische achtergrond-updates volledig uit.
            // Verrijking mag enkel handmatig gebeuren via de knop in de UI.
            Log.i(TAG, "AI/Enrichment initialized (manual mode only)")
            cancelNightlyUpdate(context)
        }
    }

    /** Trigger a manual AI optimization session. */
    fun requestManualUpdate(context: Context, isInitial: Boolean = false) {
        aiScope.launch {
            if (!AppDataStore.isAiEnabled(context)) {
                Log.d(TAG, "requestManualUpdate ignored: AI disabled")
                return@launch
            }

            Log.d(TAG, "Requesting manual AI update (initial=$isInitial)")

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED) // Explicit Wi-Fi requirement
                .build()

            val req = OneTimeWorkRequestBuilder<AiUpdateWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(if (isInitial) "initial_setup" else "manual_update")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME + (if (isInitial) "_initial" else "_manual"),
                ExistingWorkPolicy.REPLACE,
                req
            )
        }
    }

    private fun checkInitialExport(context: Context) {
        val modelStore = ModelStore(context)
        val exportDir = modelStore.getTrainingExportDir() ?: return
        
        val currentExport = exportDir.findFile("training_data_current.csv")
        if (currentExport == null) {
            Log.i(TAG, "No training data found, starting initial database export...")
            requestManualUpdate(context, isInitial = true)
        }
    }

    fun scheduleNightlyUpdate(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()

        val periodic = PeriodicWorkRequest.Builder(AiUpdateWorker::class.java, 24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )
    }

    fun cancelNightlyUpdate(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(UNIQUE_WORK_NAME)
        wm.cancelUniqueWork(UNIQUE_WORK_NAME + "_initial")
        wm.cancelUniqueWork(UNIQUE_WORK_NAME + "_manual")
    }
}
