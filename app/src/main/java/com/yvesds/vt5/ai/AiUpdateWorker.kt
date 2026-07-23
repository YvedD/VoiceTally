package com.yvesds.vt5.ai

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Worker that performs AI model update: prepares training data, optionally runs a light trainer,
 * and stores resulting model artifacts in SAF via ModelStore.
 * Current implementation is a skeleton that exports training CSV and writes metadata files.
 */
class AiUpdateWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val TAG = "AiUpdateWorker"

    override suspend fun doWork(): Result {
        Log.i(TAG, "AI update worker started")
        try {
            val modelStore = com.yvesds.vt5.ai.ModelStore(applicationContext)
            val preparer = com.yvesds.vt5.ai.TrainingDataPreparer(applicationContext)

            // ensure AI-models dir exists
            modelStore.ensureModelDir()

            // export training CSV (Room -> features)
            val exported = preparer.exportTrainingCsv(modelStore.getTrainingExportDir())
            if (exported.isEmpty()) {
                Log.w(TAG, "No data to export, AI update skipped")
                return Result.success()
            }
            Log.i(TAG, "Training export written: $exported")

            // generate labels.json
            val labels = preparer.generateLabelsJson(modelStore.getTrainingExportDir())
            Log.i(TAG, "Labels generated: ${labels.size} species")

            // call Trainer to perform on-device update
            val trainer = com.yvesds.vt5.ai.Trainer(applicationContext, modelStore)
            trainer.runOnDeviceTraining(exported, labels)

            Log.i(TAG, "AI update worker finished successfully")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "AI update worker failed: ${e.message}", e)
            return Result.failure()
        }
    }
}

