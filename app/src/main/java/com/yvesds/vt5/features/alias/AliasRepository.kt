@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)
package com.yvesds.vt5.features.alias

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.features.speech.AliasMatcher
import com.yvesds.vt5.utils.LevenshteinUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.cbor.Cbor
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.time.Instant
import java.util.zip.GZIPInputStream

/**
 * AliasRepository.kt (CSV dependency removed)
 *
 * Responsibilities:
 *  - Load aliases from SAF (preferred: binaries/aliases_optimized.cbor.gz then binaries/alias_master.json)
 *  - Fallback to serverdata/aliases.json (legacy wrapper)
 *  - Maintain an in-memory map speciesId -> SpeciesEntry (AliasModels)
 *  - Maintain reverse map normalized alias -> speciesId
 *  - Provide hot-patch addAliasInMemory()
 *
 * Notes on changes in this patch:
 *  - Replaced GlobalScope usage with a private repoScope (structured concurrency).
 *  - Background persistence scheduled by addAliasInMemory now uses repoScope and is documented as best-effort.
 *  - getAliasesForSpecies / getAllAliases are public API helpers; marked with @Suppress("unused")
 *    (they may look unused in the codebase but are part of the repository API).
 *  - Minor safety and coroutine dispatcher improvements.
 */

class AliasRepository(private val context: Context) {

    companion object {
        private const val TAG = "AliasRepository"

        const val ACTION_ALIAS_RELOAD_STARTED = "com.yvesds.vt5.ALIAS_RELOAD_STARTED"
        const val ACTION_ALIAS_RELOAD_COMPLETED = "com.yvesds.vt5.ALIAS_RELOAD_COMPLETED"
        const val EXTRA_RELOAD_SUCCESS = "com.yvesds.vt5.EXTRA_RELOAD_SUCCESS"

        @SuppressLint("StaticFieldLeak")
        @Volatile private var INSTANCE: AliasRepository? = null

        fun getInstance(context: Context): AliasRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AliasRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val appContext: Context = context.applicationContext

    // In-memory canonical cache: speciesId -> SpeciesEntry
    private val aliasCache = ConcurrentHashMap<String, SpeciesEntry>()

    // Reverse map: normalized alias -> speciesId
    private val aliasToSpeciesIdMap = ConcurrentHashMap<String, String>()

    var isDataLoaded = false
        private set

    // Structured scope for repository background tasks (not tied to UI lifecycle).
    // Uses SupervisorJob so failures in one child do not cancel others.
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Observe AliasManager's indexFlow to keep repository cache in sync
        repoScope.launch {
            AliasManager.indexFlow.collectLatest { index ->
                if (index != null) {
                    loadFromAliasIndex(index)
                    buildReverseMapping()
                    isDataLoaded = true
                    Log.i(TAG, "Repository cache updated from AliasManager flow")
                }
            }
        }
    }

    /**
     * Load all alias data (async).
     * Now primarily ensures AliasManager has loaded the index.
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun loadAliasData(): Boolean {
        if (isDataLoaded) return true
        val saf = SaFStorageHelper(appContext)
        AliasManager.ensureIndexLoadedSuspend(appContext, saf)
        return isDataLoaded
    }

    suspend fun reloadAliasData(): Boolean {
        isDataLoaded = false
        val saf = SaFStorageHelper(appContext)
        AliasManager.ensureIndexLoadedSuspend(appContext, saf)
        return isDataLoaded
    }

    /**
     * Find speciesId for an alias (normalized lookup)
     */
    fun findSpeciesIdByAlias(text: String): String? {
        if (!isDataLoaded) return null
        val normalized = normalizeForKey(text)
        return aliasToSpeciesIdMap[normalized]
    }

    /**
     * Get all aliases for a specific species.
     */
    fun getAliasesForSpecies(speciesId: String): List<AliasData> {
        return aliasCache[speciesId]?.aliases ?: emptyList()
    }

    // ---------- Loaders ----------

    /**
     * Load alias index from an in-memory AliasIndex object (CBOR decoded).
     * Converts AliasIndex -> SpeciesEntry map.
     *
     * This conversion is cheap enough to run here (called from dispatcher IO),
     * but keep it simple and thread-safe (synchronized on aliasCache).
     */
    private fun loadFromAliasIndex(index: AliasIndex) {
        try {
            val grouped = index.json.groupBy { it.speciesid }
            val localMap = mutableMapOf<String, SpeciesEntry>()
            for ((sid, recs) in grouped) {
                val canonical = recs.firstOrNull()?.canonical ?: sid
                val tilename = recs.firstOrNull()?.tilename
                val aliasesList = recs.map { r ->
                    AliasData(
                        text = r.alias,
                        norm = r.norm,
                        cologne = r.cologne ?: "",
                        phonemes = r.phonemes ?: "",
                        source = r.source,
                        timestamp = null
                    )
                }
                localMap[sid] = SpeciesEntry(speciesId = sid, canonical = canonical, tilename = tilename, aliases = aliasesList)
            }
            // Swap in atomically
            synchronized(aliasCache) {
                aliasCache.clear()
                aliasCache.putAll(localMap)
            }
        } catch (ex: Exception) {
            Log.w(TAG, "loadFromAliasIndex failed: ${ex.message}", ex)
        }
    }

    /**
     * Build reverse mapping alias -> speciesId using canonical, tilename and all aliases
     */
    private fun buildReverseMapping() {
        val localMap = mutableMapOf<String, String>()
        for ((soortId, entry) in aliasCache) {
            // canonical (normalize)
            val canon = entry.canonical.takeIf { it.isNotBlank() } ?: ""
            if (canon.isNotBlank()) localMap[normalizeForKey(canon)] = soortId

            // tilename
            entry.tilename?.takeIf { it.isNotBlank() }?.let { localMap[normalizeForKey(it)] = soortId }

            // aliases list (AliasData)
            entry.aliases.forEach { a ->
                val key = normalizeForKey(a.text)
                if (key.isNotBlank()) localMap[key] = soortId
            }
        }
        aliasToSpeciesIdMap.clear()
        aliasToSpeciesIdMap.putAll(localMap)
    }

    private fun normalizeForKey(input: String): String {
        val lower = input.lowercase(Locale.getDefault())
        val decomposed = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
        val noDiacritics = decomposed.replace("\\p{Mn}+".toRegex(), "")
        val cleaned = noDiacritics.replace("[^\\p{L}\\p{Nd}]+".toRegex(), " ").trim()
        return cleaned.replace("\\s+".toRegex(), " ")
    }

    /**
     * Trigger AliasMatcher reload (low-priority helper).
     */
    suspend fun reloadMatcherIfNeeded(context: Context, saf: SaFStorageHelper): Boolean = withContext(Dispatchers.IO) {
        try {
            AliasMatcher.reloadIndex(context, saf)
            Log.i(TAG, "AliasMatcher.reloadIndex completed")
            return@withContext true
        } catch (ex: Exception) {
            Log.w(TAG, "AliasMatcher.reloadIndex failed: ${ex.message}", ex)
            return@withContext false
        }
    }

}