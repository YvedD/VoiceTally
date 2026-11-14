package com.yvesds.vt5.features.update

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * AppUpdateWorker - Periodieke achtergrondtaak (elk uur) om te controleren
 * op nieuwe app-versies en hotfixes op GitHub.
 */
class AppUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.i(TAG, "Uitvoeren periodieke uurlijkse update-controle op GitHub...")
            val result = AppUpdateManager.refreshFromGithub(applicationContext, allowCache = false)
            if (result.isSuccess) {
                val state = result.getOrNull()
                Log.i(TAG, "Update-controle voltooid. Update beschikbaar: ${state?.updateAvailable}")
                Result.success()
            } else {
                Log.w(TAG, "Update-controle mislukt: ${result.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Fout in AppUpdateWorker: ${ex.message}", ex)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "AppUpdateWorker"
        private const val UNIQUE_WORK_NAME = "HourlyAppUpdateCheck"

        fun scheduleHourlyCheck(context: Context) {
            try {
                val constraints = androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(1, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
                Log.i(TAG, "Uurlijkse achtergrond update-controle ingepland bij WorkManager")
            } catch (ex: Exception) {
                Log.w(TAG, "Inplannen AppUpdateWorker mislukt: ${ex.message}")
            }
        }
    }
}
