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
            Log.w(TAG, "MatchLogWriter niet kunnen starten in Application.onCreate: ${ex.message}", ex)
        }
        instance = this

        try {
            McRuntimePermissions.refreshCachedPermissionStates(this)
        } catch (ex: Exception) {
            Log.w(TAG, "Het is niet gelukt om de machtigingscache bij het opstarten te verversen: ${ex.message}", ex)
        }
        
        // Ruim eventueel nog aanwezige legacy AlarmManager-registraties op.
        try {
            com.yvesds.vt5.core.app.HourlyAlarmManager.cancelScheduledAlarm(this)
        } catch (ex: Exception) {
            Log.w(TAG, "Is er niet in geslaagd het oude uuralarm op te ruimen: ${ex.message}", ex)
        }

        // Preload data in de achtergrond - verhoogt app responsiviteit
        preloadDataAsync()

        startupAliasRefreshJob = appScope.async {
            try {
                AliasStartupInitializer.rebuildAndWarmup(applicationContext)
            } catch (ex: Exception) {
                Log.w(TAG, "Startup-alias herbouw mislukt: ${ex.message}", ex)
                null
            }
        }
        startupAliasRefreshDeferred = startupAliasRefreshJob

        /** Beste inspanning: voorlaad aliasindexen zodat eerste herkenning niet blokkeren
        * bij de belasting van CBORSAN.
        * We splitsen bewust IO-checks en CPU-gebonden werk:
        * - kleine SAF-bestaanscontroles draaien op IO
        * - zwaar CPU-bouwwerk draait op standaard
        */
        appScope.launch {
            try {
                val saf = SaFStorageHelper(applicationContext)
                // Snelle IO-controle: raak de VT5-directorystatus één keer aan op IO voordat je CPU-zware preloads plaatst.
                withContext(Dispatchers.IO) { saf.getVt5DirIfExists() }

                // Voer index-preloads uit op Default zodat CPU-gebonden werk Default threads gebruikt.
                // Opmerking: ensureLoaded ensureIndexLoadedSuspend intern gebruik de juiste dispatchers
                // voor hun IO- versus CPU-fasen; Oproepen vanuit Default zorgt ervoor dat CPU-fasen op Default draaien.
                withContext(Dispatchers.Default) {
                    try {
                        AliasMatcher.ensureLoaded(applicationContext, saf)
                    } catch (ex: Exception) {
                        Log.w(TAG, "AliasMatcher.ensureLoaded mislukt (achtergrond): ${ex.message}", ex)
                    }

                    try {
                        // AliasManager.ensureIndexLoadedSuspend does IO & CPU; calling from Default lets its CPU parts run there.
                        AliasManager.ensureIndexLoadedSuspend(applicationContext, saf)
                    } catch (ex: Exception) {
                        Log.w(TAG, "AliasManager.ensureIndexLoadedSuspend mislukt (achtergrond): ${ex.message}", ex)
                    }
                }

                // Niets anders om de opstart te blokkeren hier; De preload is alleen best-effort.
            } catch (ex: Exception) {
                Log.w(TAG, "Achtergrondalias-index préloading overgeslagen: ${ex.message}", ex)
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
                Log.e(TAG, "Fout tijdens het préloaden van data: ${e.message}", e)
            }
        }
    }

    override fun onTerminate() {
        try {
            // Annuleer alle achtergrondcoroutines
            appScope.cancel()
            
            // Opruimen match log writer
            try {
                MatchLogWriter.stop()
            } catch (ex: Exception) {
                Log.w(TAG, "Niet gestopt MatchLogWriter: ${ex.message}", ex)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fout tijdens app-beëindiging: ${e.message}", e)
        }
        super.onTerminate()
    }

    override fun onLowMemory() {
        Log.w(TAG, "VT5App onLowMemory - Caches wissen")
        super.onLowMemory()
        // Mogelijkheid om caches te wissen indien nodig
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Extra geheugenopruiming op basis van niveau indien nodig
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

        // Toggle voor ontwikkelings/debug logging van wind-grafiekdata.
        // Kan tijdelijk ingeschakeld worden tijdens debugging sessions.
        @Volatile
        var ENABLE_WIND_DEBUG_LOGGING: Boolean = false

        suspend fun awaitStartupAliasRefresh(): AliasStartupInitializer.StartupRefreshResult? {
            return startupAliasRefreshDeferred?.await()
        }

        /**
         * Geef volgende telling-id terug als String en verhoog de teller via DataStore.
         */
        suspend fun nextTellingId(): String {
            return com.yvesds.vt5.core.opslag.AppDataStore.nextTellingId(instance)
        }

        // ====== Shared singletons ======
        /** Lenient JSON decoder (ignoreUnknownKeys/explicitNulls=false/encodeDefaults=true) */
        val json: Json by lazy {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true  // Sluit altijd alle velden op, zelfs lege strings,
                coerceInputValues = true  // Zet null om naar standaardwaarden bij het decoderen
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
