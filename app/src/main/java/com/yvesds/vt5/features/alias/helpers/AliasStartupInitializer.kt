package com.yvesds.vt5.features.alias.helpers

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.features.alias.AliasManager
import com.yvesds.vt5.features.alias.AliasRepository
import com.yvesds.vt5.features.speech.AliasMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Rebuilds the SAF alias CBOR at every app startup from the authoritative SAF master,
 * persists metadata to SharedPreferences, and refreshes all in-memory alias consumers.
 */
object AliasStartupInitializer {
    private const val TAG = "AliasStartupInit"
    private const val PREFS_NAME = "vt5_prefs"

    const val PREF_ALIAS_CBOR_SIZE = "pref_alias_cbor_size"
    const val PREF_ALIAS_CBOR_SHA256 = "pref_alias_cbor_sha256"
    const val PREF_ALIAS_CBOR_BUILT_AT = "pref_alias_cbor_built_at"
    const val PREF_ALIAS_CBOR_RECORD_COUNT = "pref_alias_cbor_record_count"
    const val PREF_ALIAS_CBOR_SPECIES_COUNT = "pref_alias_cbor_species_count"
    const val PREF_ALIAS_CBOR_ALIAS_COUNT = "pref_alias_cbor_alias_count"
    const val PREF_ALIAS_CBOR_SOURCE = "pref_alias_cbor_source"

    data class StartupRefreshResult(
        val source: String,
        val gzipSizeBytes: Long,
        val gzipSha256: String,
        val recordCount: Int,
        val speciesCount: Int,
        val aliasCount: Int,
        val builtAtIso: String
    )

    private val startupMutex = Mutex()

    suspend fun rebuildAndWarmup(context: Context): StartupRefreshResult? = withContext(Dispatchers.IO) {
        startupMutex.withLock {
            val appContext = context.applicationContext
            val saf = SaFStorageHelper(appContext)
            val vt5Root = saf.getVt5DirIfExists() ?: run {
                Log.w(TAG, "VT5 root unavailable; skipping startup alias rebuild")
                return@withLock null
            }

            var source = "assets"
            var master = AliasMasterIO.readMasterFromAssets(appContext, vt5Root)

            if (master == null) {
                runCatching {
                    AliasManager.initialize(appContext, saf)
                }.onFailure {
                    Log.w(TAG, "AliasManager.initialize during startup rebuild failed: ${it.message}", it)
                }
                master = AliasMasterIO.readMasterFromAssets(appContext, vt5Root)
            }

            if (master == null) {
                source = "binaries_legacy"
                master = AliasMasterIO.readMasterFromBinaries(appContext, vt5Root)
            }

            if (master == null) {
                Log.w(TAG, "No alias master available for startup rebuild")
                return@withLock null
            }

            val writeResult = AliasMasterIO.writeMasterAndCbor(appContext, master, vt5Root, saf) ?: run {
                Log.w(TAG, "Startup alias rebuild failed while writing master/CBOR")
                return@withLock null
            }

            val builtAt = Instant.now().toString()
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                putLong(PREF_ALIAS_CBOR_SIZE, writeResult.gzipSizeBytes)
                putString(PREF_ALIAS_CBOR_SHA256, writeResult.gzipSha256)
                putString(PREF_ALIAS_CBOR_BUILT_AT, builtAt)
                putInt(PREF_ALIAS_CBOR_RECORD_COUNT, writeResult.recordCount)
                putInt(PREF_ALIAS_CBOR_SPECIES_COUNT, writeResult.speciesCount)
                putInt(PREF_ALIAS_CBOR_ALIAS_COUNT, writeResult.aliasCount)
                putString(PREF_ALIAS_CBOR_SOURCE, source)
            }

            runCatching {
                AliasMatcher.reloadIndex(appContext, saf)
            }.onFailure {
                Log.w(TAG, "AliasMatcher.reloadIndex failed after startup rebuild: ${it.message}", it)
            }

            runCatching {
                AliasRepository.getInstance(appContext).reloadAliasData()
            }.onFailure {
                Log.w(TAG, "AliasRepository.reloadAliasData failed after startup rebuild: ${it.message}", it)
            }

            runCatching {
                AliasManager.ensureIndexLoadedSuspend(appContext, saf)
            }.onFailure {
                Log.w(TAG, "AliasManager.ensureIndexLoadedSuspend failed after startup rebuild: ${it.message}", it)
            }

            Log.i(
                TAG,
                "Startup alias rebuild completed from $source (records=${writeResult.recordCount}, gzipBytes=${writeResult.gzipSizeBytes})"
            )

            StartupRefreshResult(
                source = source,
                gzipSizeBytes = writeResult.gzipSizeBytes,
                gzipSha256 = writeResult.gzipSha256,
                recordCount = writeResult.recordCount,
                speciesCount = writeResult.speciesCount,
                aliasCount = writeResult.aliasCount,
                builtAtIso = builtAt
            )
        }
    }
}

