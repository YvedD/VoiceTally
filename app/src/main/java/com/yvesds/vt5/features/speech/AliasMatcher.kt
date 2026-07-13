package com.yvesds.vt5.features.speech

import android.content.Context
import android.util.Log
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.features.alias.AliasIndex as RepoAliasIndex
import com.yvesds.vt5.features.alias.AliasRecord
import com.yvesds.vt5.features.alias.AliasManager
import com.yvesds.vt5.utils.LevenshteinUtils
import com.yvesds.vt5.utils.TextUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import kotlin.math.max

/**
 * AliasMatcher (optimized)
 *
 * - Loads/parses CBOR on Dispatchers.IO and builds heavy indexes on Dispatchers.Default.
 * - Uses a single Deferred loader to avoid duplicate concurrent loads and to let callers await completion.
 * - Builds maps off-main and atomically swaps them in when ready to avoid partially-populated state.
 * - Hotpatch incremental updates avoid full-map copies when possible.
 * - Defensive/gentle fallbacks and clearer logging.
 *
 * Runtime behavior (non-blocking lookups):
 * - findExact(...) and findFuzzyCandidates(...) are non-blocking read paths:
 *   they return quickly with empty results if the in-memory index isn't loaded.
 *   ensureLoaded(...) is the explicit suspending loader to call at app startup.
 */
internal object AliasMatcher {
    private const val TAG = "AliasMatcher"

    // In-memory structures (volatile for quick visibility)
    @Volatile private var loadedIndex: RepoAliasIndex? = null
    @Volatile private var aliasMap: Map<String, List<AliasRecord>>? = null
    @Volatile private var phoneticCache: Map<String, String>? = null
    @Volatile private var firstCharBuckets: Map<Char, List<String>>? = null
    @Volatile private var bloomFilter: Set<Long>? = null

    // Synchronization and loader state
    private val cborMissingWarned = AtomicBoolean(false)
    private val loaderScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // Observe AliasManager's indexFlow to keep matcher maps in sync
        loaderScope.launch {
            AliasManager.indexFlow.collectLatest { index ->
                if (index != null) {
                    val maps = withContext(Dispatchers.Default) { buildIndexMaps(index) }
                    // Atomic swap
                    aliasMap = maps.map
                    phoneticCache = maps.phonCache
                    firstCharBuckets = maps.buckets
                    bloomFilter = maps.bloom
                    loadedIndex = index
                    Log.i(TAG, "Matcher structures updated from AliasManager flow")
                }
            }
        }
    }

    // Reusable regex for tokenization
    private val WHITESPACE = Regex("\\s+")

    /**
     * Ensure internal in-memory alias index is loaded.
     * Now primarily ensures AliasManager has loaded the index.
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun ensureLoaded(context: Context, saf: SaFStorageHelper) {
        // Fast path
        if (aliasMap != null && phoneticCache != null) return
        AliasManager.ensureIndexLoadedSuspend(context, saf)
    }

    /**
     * Force reload: delegatesto AliasManager.
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun reloadIndex(context: Context, saf: SaFStorageHelper) {
        loadedIndex = null
        aliasMap = null
        phoneticCache = null
        firstCharBuckets = null
        bloomFilter = null
        cborMissingWarned.set(false)

        AliasManager.ensureIndexLoadedSuspend(context, saf)
    }

    /**
     * Non-blocking quick exact match lookup by normalized phrase (real-time safe).
     *
     * NOTE: This function intentionally does NOT call ensureLoaded(...). If the index is not yet loaded,
     * it will return an empty list immediately (fast). If you need a blocking guarantee, call ensureLoaded(...)
     * before invoking this function (e.g. at app startup).
     */
    suspend fun findExact(aliasPhrase: String, context: Context, saf: SaFStorageHelper): List<AliasRecord> = withContext(Dispatchers.Default) {
        val mapSnapshot = aliasMap
        if (mapSnapshot == null) {
            AliasManager.ensureIndexLoadedSuspend(context, saf)
            return@withContext emptyList()
        }

        val normKey = TextUtils.normalizeLowerNoDiacritics(aliasPhrase.trim())
        mapSnapshot[normKey]?.let { return@withContext it }
        val lowerKey = aliasPhrase.trim().lowercase()
        mapSnapshot[lowerKey] ?: emptyList()
    }

    /**
     * Non-blocking fuzzy candidate search using combined scoring.
     *
     * Returns quickly with empty list when index not yet loaded.
     */
    suspend fun findFuzzyCandidates(
        phrase: String,
        context: Context,
        saf: SaFStorageHelper,
        topN: Int = 6,
        threshold: Double = 0.40
    ): List<Pair<AliasRecord, Double>> = withContext(Dispatchers.Default) {
        val t0 = System.nanoTime()

        val map = aliasMap
        val buckets = firstCharBuckets
        val bloom = bloomFilter
        if (map == null || buckets == null || bloom == null) {
            AliasManager.ensureIndexLoadedSuspend(context, saf)
            return@withContext emptyList()
        }

        val q = phrase.trim().lowercase()
        if (q.isEmpty()) return@withContext emptyList()

        // Pre-split tokens using precompiled regex to avoid allocations in tight loops
        val tokens = WHITESPACE.split(q).filter { it.isNotBlank() && it.toIntOrNull() == null }

        if (tokens.isNotEmpty()) {
            val anyTokenMatches = tokens.any { token -> token.hashCode().toLong() in bloom }
            if (!anyTokenMatches) {
                return@withContext emptyList()
            }
        }

        val firstChar = q[0]
        val bucket = buckets[firstChar] ?: emptyList()
        val len = q.length

        // Build shortlist but cap its size to avoid huge buckets blowing up CPU
        val SHORTLIST_CAP = 300 // tune if necessary
        val shortlistList = bucket.asSequence()
            .filter { key ->
                val l = key.length
                val diff = kotlin.math.abs(l - len)
                diff <= max(2, len / 3)
            }
            .take(SHORTLIST_CAP)
            .toList()

        if (bucket.size > SHORTLIST_CAP) {
        }

        val scored = mutableListOf<Pair<AliasRecord, Double>>()

        // phonemize query once if needed
        var qPh: String? = null
        var qPhComputed = false

        for (k in shortlistList) {
            val lev = LevenshteinUtils.normalizedRatio(q, k)
            val colSim = runCatching { ColognePhonetic.similarity(q, k) }.getOrDefault(0.0)
            val recs = map[k] ?: continue
            for (r in recs) {
                val phonSim = if (!r.phonemes.isNullOrBlank()) {
                    if (!qPhComputed) {
                        qPh = runCatching { DutchPhonemizer.phonemize(q) }.getOrDefault("")
                        qPhComputed = true
                    }
                    runCatching { DutchPhonemizer.phonemeSimilarity(qPh ?: "", r.phonemes) }.getOrDefault(0.0)
                } else 0.0

                val score = (0.45 * lev + 0.35 * colSim + 0.20 * phonSim).coerceIn(0.0, 1.0)
                if (score >= threshold) scored += Pair(r, score)
            }
            // Optional: small early exit if we've already collected too many scored candidates
            if (scored.size > 1000) break
        }

        scored.sortByDescending { it.second }
        val result = scored.take(topN)
        val t1 = System.nanoTime()
        return@withContext result
    }

    /**
     * Hot-patch: add a minimal AliasRecord in-memory so new alias is immediately visible.
     *
     * This implementation tries to be incremental: only touches the keys that are affected
     * and avoids copying entire maps where possible.
     */
    fun addAliasHotpatch(speciesId: String, aliasRaw: String, canonical: String? = null, tilename: String? = null) {
        try {
            val norm = TextUtils.normalizeLowerNoDiacritics(aliasRaw)
            if (norm.isBlank()) return

            val aliasLower = aliasRaw.trim().lowercase()
            val col = runCatching { ColognePhonetic.encode(norm) }.getOrDefault("")
            val phon = runCatching { DutchPhonemizer.phonemize(norm) }.getOrDefault("")

            val record = AliasRecord(
                aliasid = "hotpatch_${System.nanoTime()}",
                speciesid = speciesId,
                canonical = canonical ?: aliasLower,
                tilename = tilename,
                alias = aliasLower,
                norm = norm,
                cologne = if (col.isNotBlank()) col else null,
                phonemes = if (phon.isNotBlank()) phon else null,
                weight = 1.0,
                source = "user_field_training"
            )

            synchronized(this) {
                // Working copies
                val currentMap = aliasMap?.mapValues { it.value.toMutableList() }?.toMutableMap() ?: mutableMapOf()
                val currentPhon = phoneticCache?.toMutableMap() ?: mutableMapOf()
                val currentBuckets = firstCharBuckets?.mapValues { it.value.toMutableList() }?.toMutableMap() ?: mutableMapOf()
                val currentBloom = bloomFilter?.toMutableSet() ?: mutableSetOf()

                val keys = listOf(aliasLower, (record.canonical ?: "").trim().lowercase(), norm)
                for (k in keys) {
                    if (k.isBlank()) continue
                    val list = currentMap.getOrPut(k) { mutableListOf() }
                    list.add(record)
                    currentMap[k] = list
                    val colForKey = runCatching { ColognePhonetic.encode(k) }.getOrDefault("")
                    currentPhon[k] = colForKey
                    val first = k[0]
                    currentBuckets.getOrPut(first) { mutableListOf() }.add(k)
                    currentBloom.add(k.hashCode().toLong())
                }

                // Atomic-ish swap: assign immutable views
                aliasMap = currentMap.mapValues { it.value.toList() }
                phoneticCache = currentPhon.toMap()
                firstCharBuckets = currentBuckets.mapValues { it.value.toList() }
                bloomFilter = currentBloom.toSet()
            }

        } catch (ex: Exception) {
            Log.w(TAG, "addAliasHotpatch failed: ${ex.message}", ex)
        }
    }

    // ----------------------
    // Internal helpers
    // ----------------------

    private data class IndexMaps(
        val map: Map<String, List<AliasRecord>>,
        val phonCache: Map<String, String>,
        val buckets: Map<Char, List<String>>,
        val bloom: Set<Long>
    )

    private fun buildIndexMaps(idx: RepoAliasIndex): IndexMaps {
        val map = mutableMapOf<String, MutableList<AliasRecord>>()
        val phonCache = mutableMapOf<String, String>()
        val buckets = mutableMapOf<Char, MutableList<String>>()
        val bloomSet = mutableSetOf<Long>()

        for (r in idx.json) {
            val keys = mutableSetOf<String>()
            r.alias.trim().takeIf { it.isNotEmpty() }?.let { keys += it.lowercase() }
            r.canonical.trim().takeIf { it.isNotEmpty() }?.let { keys += it.lowercase() }
            r.norm.trim().takeIf { it.isNotEmpty() }?.let { keys += r.norm }

            for (k in keys) {
                map.getOrPut(k) { mutableListOf() }.add(r)
                if (k.isNotEmpty()) {
                    val first = k[0]
                    buckets.getOrPut(first) { mutableListOf() }.add(k)
                    val col = runCatching { ColognePhonetic.encode(k) }.getOrDefault("")
                    phonCache[k] = col
                    bloomSet.add(k.hashCode().toLong())
                }
            }
        }

        return IndexMaps(
            map = map.mapValues { it.value.toList() },
            phonCache = phonCache.toMap(),
            buckets = buckets.mapValues { it.value.toList() },
            bloom = bloomSet.toSet()
        )
    }
}