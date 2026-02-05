package com.yvesds.vt5.features.recent

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Verbeterde 'recent gebruikt' opslag met in-memory caching.
 * - Bewaart max [DEFAULT_MAX_RECENTS] soortIDs met laatste gebruikstijd, meest recent eerst.
 * - recordUse() promoot of voegt toe; trimt lijst.
 * - Optimalisaties: in-memory caching voor snellere toegang zonder IO
 *
 * Let op:
 * - Deze store is globaal (app-breed), niet telpost-specifiek.
 * - Filter recents zelf tegen de actuele lijst voor de gekozen telpost.
 */
object RecentSpeciesStore {
    private const val TAG = "RecentSpeciesStore"
    private const val PREFS = "recent_species_prefs"
    private const val KEY = "recent_species_list"
    private const val DEFAULT_MAX_RECENTS = 30 // maximum aantal recente soorten

    // In-memory cache van recente soorten
    private var cachedRecents: List<Pair<String, Long>>? = null
    // Memory-efficient lookup voor membership checks
    private var cachedRecentIdsSet = ConcurrentHashMap<String, Boolean>()

    /**
     * Registreer het gebruik van een soort-id.
     * Houdt legacy "recents" bij én voedt het nieuw score/decay systeem.
     */
    fun recordUse(context: Context, soortId: String, maxEntries: Int = DEFAULT_MAX_RECENTS) {
        // NEW score-based favorites model
        SpeciesUsageScoreStore.recordUse(context, soortId)

        // Legacy recents list (kept for backwards compatibility)
        val list = load(context).toMutableList()
        val now = System.currentTimeMillis()
        val without = list.filterNot { it.first == soortId }
        val updated = listOf(soortId to now) + without
        save(context, updated.take(maxEntries))

        // Update cache
        cachedRecents = updated.take(maxEntries)
        cachedRecentIdsSet.clear()
        cachedRecents?.forEach { (id, _) -> cachedRecentIdsSet[id] = true }
    }

    /**
     * Retourneert lijst van (soortId, lastUsedMillis), meest recent eerst.
     * NEW: score-store bepaalt de window (laatste sessies) + cap voor ALLE.
     */
    fun getRecents(context: Context): List<Pair<String, Long>> {
        // Delegate to score-based recents window.
        val loaded = SpeciesUsageScoreStore.getRecents(context, limit = SpeciesUsageScoreStore.MAX_ALL_CAP)
        cachedRecents = loaded
        cachedRecentIdsSet.clear()
        loaded.forEach { (id, _) -> cachedRecentIdsSet[id] = true }
        return loaded
    }

    /**
     * Snel controleren of een soortId recent is gebruikt
     */
    @Suppress("unused")
    fun isRecent(context: Context, soortId: String): Boolean {
        if (cachedRecentIdsSet.containsKey(soortId)) {
            return cachedRecentIdsSet[soortId] == true
        }

        // Cache vullen als die nog niet bestaat
        if (cachedRecents == null) {
            getRecents(context)
        }

        return cachedRecentIdsSet[soortId] == true
    }

    private fun load(context: Context): List<Pair<String, Long>> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id", "")
                val ts = o.optLong("ts", 0L)
                if (id.isNotBlank() && ts > 0L) id to ts else null
            }
        }.getOrElse { e ->
            Log.e(TAG, "Error loading recents: ${e.message}", e)
            emptyList()
        }
    }

    private fun save(context: Context, items: List<Pair<String, Long>>) {
        try {
            val arr = JSONArray()
            items.forEach { (id, ts) ->
                arr.put(JSONObject().put("id", id).put("ts", ts))
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit {
                    putString(KEY, arr.toString())
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving recents: ${e.message}", e)
        }
    }

    /**
     * Handmatig de cache leegmaken (gebruikt in testsituaties)
     */
    @Suppress("unused")
    fun invalidateCache() {
        cachedRecents = null
        cachedRecentIdsSet.clear()
    }
}