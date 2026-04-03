package com.yvesds.vt5

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import com.yvesds.vt5.features.speech.MatchLogWriter
import com.yvesds.vt5.features.speech.AliasMatcher
import com.yvesds.vt5.features.alias.AliasManager
import com.yvesds.vt5.features.alias.helpers.AliasStartupInitializer
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.features.masterClient.McRuntimePermissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

/**
 * VT5 – App singleton
 *
 * - Houdt een veilige Application.instance bij
 * - Biedt centrale Json/OkHttp singletons
 * - Biedt nextTellingId() (als String), oplopend en persistent via SharedPreferences
 * - Proactieve data preloading voor betere app performance
 *
 * Note: Zware dataverwerking gebeurt in een background scope, onzichtbaar voor de gebruiker.
 */
class VT5App : Application() {
    // Speciale scope die blijft bestaan gedurende de hele app-lifecycle
    private val appScope = CoroutineScope(Job() + Dispatchers.IO)
    private val TAG = "VT5App"
    private var startupAliasRefreshJob: Deferred<AliasStartupInitializer.StartupRefreshResult?>? = null

    override fun onCreate() {
        super.onCreate()
        try {
            MatchLogWriter.start(this)
        } catch (ex: Exception) {
            Log.w(TAG, "Failed to start MatchLogWriter in Application.onCreate: ${ex.message}", ex)
        }
        instance = this

        try {
            McRuntimePermissions.refreshCachedPermissionStates(this)
        } catch (ex: Exception) {
            Log.w(TAG, "Failed to refresh permission cache at startup: ${ex.message}", ex)
        }
        
        // Ruim eventueel nog aanwezige legacy AlarmManager-registraties op.
        try {
            com.yvesds.vt5.core.app.HourlyAlarmManager.cancelScheduledAlarm(this)
        } catch (ex: Exception) {
            Log.w(TAG, "Failed to clean up legacy hourly alarm: ${ex.message}", ex)
        }

        // Preload data in de achtergrond - verhoogt app responsiviteit
        preloadDataAsync()

        startupAliasRefreshJob = appScope.async {
            try {
                AliasStartupInitializer.rebuildAndWarmup(applicationContext)
            } catch (ex: Exception) {
                Log.w(TAG, "Startup alias rebuild failed: ${ex.message}", ex)
                null
            }
        }
        startupAliasRefreshDeferred = startupAliasRefreshJob

        // Best-effort: preload alias indexes so first recognitions don't block on CBOR/SAN load.
        // We deliberately split IO checks and CPU-bound work:
        //  - small SAF existence checks run on IO
        //  - heavy index building / CPU work runs on Default
        appScope.launch {
            try {
                val saf = SaFStorageHelper(applicationContext)
                // Quick IO check: touch the VT5 directory state once on IO before CPU-heavy preloads.
                withContext(Dispatchers.IO) { saf.getVt5DirIfExists() }

                // Run index preloads on Default so CPU-bound work uses Default threads.
                // Note: ensureLoaded / ensureIndexLoadedSuspend internally use appropriate dispatchers
                // for their IO vs CPU phases; calling from Default ensures CPU phases run on Default.
                withContext(Dispatchers.Default) {
                    try {
                        AliasMatcher.ensureLoaded(applicationContext, saf)
                    } catch (ex: Exception) {
                        Log.w(TAG, "AliasMatcher.ensureLoaded failed (background): ${ex.message}", ex)
                    }

                    try {
                        // AliasManager.ensureIndexLoadedSuspend does IO & CPU; calling from Default lets its CPU parts run there.
                        AliasManager.ensureIndexLoadedSuspend(applicationContext, saf)
                    } catch (ex: Exception) {
                        Log.w(TAG, "AliasManager.ensureIndexLoadedSuspend failed (background): ${ex.message}", ex)
                    }
                }

                // Nothing else to block startup on here; the preload is best-effort only.
            } catch (ex: Exception) {
                Log.w(TAG, "Background alias index preload skipped: ${ex.message}", ex)
            }
        }
    }

    /**
     * Start een achtergrond taak om server data te preloaden
     * Dit maakt scherm-transities sneller wanneer de data nodig is
     */
    private fun preloadDataAsync() {
        appScope.launch {
            try {
                ServerDataCache.preload(applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Error during data preloading: ${e.message}", e)
            }
        }
    }

    override fun onTerminate() {
        try {
            // Cancel all background coroutines
            appScope.cancel()
            
            // Clean up match log writer
            try {
                MatchLogWriter.stop()
            } catch (ex: Exception) {
                Log.w(TAG, "Failed to stop MatchLogWriter: ${ex.message}", ex)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during app termination: ${e.message}", e)
        }
        super.onTerminate()
    }

    override fun onLowMemory() {
        Log.w(TAG, "VT5App onLowMemory - clearing caches")
        super.onLowMemory()
        // Opportunity to clear caches if needed
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Additional memory cleanup based on level if needed
    }

    companion object {
        // ====== App instance ======
        lateinit var instance: VT5App
            private set

        // ====== Prefs ======
        private const val PREFS = "vt5_prefs"
        private const val KEY_TELLING_ID = "telling_id"
        @Volatile private var startupAliasRefreshDeferred: Deferred<AliasStartupInitializer.StartupRefreshResult?>? = null

        /** Toegang tot app-prefs. */
        fun prefs(): SharedPreferences =
            instance.run { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

        suspend fun awaitStartupAliasRefresh(): AliasStartupInitializer.StartupRefreshResult? {
            return startupAliasRefreshDeferred?.await()
        }

        /**
         * Geef volgende telling-id terug als String en verhoog de teller.
         * Thread-safe via @Synchronized.
         */
        @Synchronized
        fun nextTellingId(): String {
            val p = prefs()
            val current = p.getLong(KEY_TELLING_ID, 1L)
            p.edit { putLong(KEY_TELLING_ID, current + 1L) }
            return current.toString()
        }

        // ====== Shared singletons ======
        /** Lenient JSON decoder (ignoreUnknownKeys/explicitNulls=false/encodeDefaults=true) */
        val json: Json by lazy {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true  // Always include all fields, even empty strings
                coerceInputValues = true  // Convert null to default values when decoding
            }
        }

        /** OkHttp client met bescheiden timeouts. */
        val http: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}