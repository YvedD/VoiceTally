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
        Log.i(TAG, "AI background worker disabled (manual enrichment mode only)")
        return Result.success()
    }
}

