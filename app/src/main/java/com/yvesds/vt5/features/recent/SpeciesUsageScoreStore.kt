@file:Suppress("unused")

package com.yvesds.vt5.features.recent

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Score-based "favorites" for species, based on usage across the last N sessions.
 *
 * Requirements / design:
 * - No pinned list; purely score-based.
 * - Keep only species that occurred in the last [MAX_SESSIONS] sessions.
 * - Additional time-based decay is applied (week-scale) so older usage slowly fades.
 * - "ALLE" in settings means: use the *full scored set* (still capped at [MAX_ALL_CAP]).
 */
@Suppress("unused")
object SpeciesUsageScoreStore {
    private const val TAG = "SpeciesUsageScoreStore"

    private const val PREFS = "species_usage_score_prefs"
    private const val KEY_STATE = "state_json"

    /** Keep a rolling window of last sessions (season-ish). */
    private const val MAX_SESSIONS = 10

    /** Hard cap for ALLE (to keep UI manageable). */
    const val MAX_ALL_CAP = 75

    /**
     * Week-ish decay: half-life around 7 days.
     * score(t) = score0 * exp(-lambda * dtDays)
     * where lambda = ln(2)/halfLifeDays
     */
    private const val HALF_LIFE_DAYS = 7.0

    private val memCacheByContext = ConcurrentHashMap<String, State>()

    data class Entry(
        val soortId: String,
        var score: Double,
        var lastUsedMs: Long,
        var lastSessionId: Long
    )

    data class Session(
        val id: Long,
        val startedMs: Long,
        val speciesIds: MutableSet<String>
    )

    data class State(
        var nextSessionId: Long,
        val sessions: MutableList<Session>,
        val entries: MutableMap<String, Entry>
    )

    @Suppress("unused")
    fun startNewSession(context: Context) {
        val st = load(context)
        val now = System.currentTimeMillis()
        val sid = st.nextSessionId++

        st.sessions.add(0, Session(id = sid, startedMs = now, speciesIds = LinkedHashSet()))
        if (st.sessions.size > MAX_SESSIONS) {
            st.sessions.subList(MAX_SESSIONS, st.sessions.size).clear()
        }

        // Drop entries that are no longer present in any of the kept sessions.
        pruneEntriesNotInKeptSessions(st)
        save(context, st)
    }

    @Suppress("unused")
    fun recordUse(context: Context, soortId: String) {
        val st = load(context)
        val now = System.currentTimeMillis()

        val session = st.sessions.firstOrNull() ?: run {
            // Ensure there is a session (defensive). If none, start one.
            val sid = st.nextSessionId++
            val s = Session(id = sid, startedMs = now, speciesIds = LinkedHashSet())
            st.sessions.add(0, s)
            s
        }

        // Update session membership
        session.speciesIds.add(soortId)

        // Decay existing score first
        val entry = st.entries[soortId]
        val decayedScore = entry?.let { decayScore(it.score, it.lastUsedMs, now) } ?: 0.0

        // Boost: 1.0 per use; +0.5 extra if within latest session.
        val boost = 1.0
        val newScore = decayedScore + boost

        if (entry == null) {
            st.entries[soortId] = Entry(
                soortId = soortId,
                score = newScore,
                lastUsedMs = now,
                lastSessionId = session.id
            )
        } else {
            entry.score = newScore
            entry.lastUsedMs = now
            entry.lastSessionId = session.id
        }

        // Prune to last sessions window
        pruneEntriesNotInKeptSessions(st)

        save(context, st)
    }

    /**
     * Return scored species IDs (best first), restricted to last sessions window.
     *
     * @param limit requested limit; if <=0 treated as "ALL" (still capped).
     */
    @Suppress("unused")
    fun getTopSpeciesIds(context: Context, limit: Int): List<String> {
        val st = load(context)
        val now = System.currentTimeMillis()
        val allowed = buildAllowedIdsFromSessions(st)

        val requested = if (limit <= 0) MAX_ALL_CAP else limit
        val effectiveLimit = requested.coerceAtMost(MAX_ALL_CAP)

        return st.entries.values
            .asSequence()
            .filter { it.soortId in allowed }
            .map { e ->
                val scoreNow = decayScore(e.score, e.lastUsedMs, now)
                e.soortId to scoreNow
            }
            .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })
            .take(effectiveLimit)
            .map { it.first }
            .toList()
    }

    /**
     * Similar to recents: returns pairs (soortId, lastUsedMs) but still limited by window.
     */
    @Suppress("unused")
    fun getRecents(context: Context, limit: Int): List<Pair<String, Long>> {
        val st = load(context)
        val allowed = buildAllowedIdsFromSessions(st)
        val requested = if (limit <= 0) MAX_ALL_CAP else limit
        val effectiveLimit = requested.coerceAtMost(MAX_ALL_CAP)

        return st.entries.values
            .asSequence()
            .filter { it.soortId in allowed }
            .sortedByDescending { it.lastUsedMs }
            .take(effectiveLimit)
            .map { it.soortId to it.lastUsedMs }
            .toList()
    }

    private fun decayScore(score: Double, lastUsedMs: Long, nowMs: Long): Double {
        if (score <= 0.0) return 0.0
        val dtDays = (nowMs - lastUsedMs).coerceAtLeast(0L).toDouble() / (1000.0 * 60.0 * 60.0 * 24.0)
        val lambda = kotlin.math.ln(2.0) / HALF_LIFE_DAYS
        return score * kotlin.math.exp(-lambda * dtDays)
    }

    private fun buildAllowedIdsFromSessions(st: State): Set<String> {
        val set = LinkedHashSet<String>()
        st.sessions.forEach { set.addAll(it.speciesIds) }
        return set
    }

    private fun pruneEntriesNotInKeptSessions(st: State) {
        val allowed = buildAllowedIdsFromSessions(st)
        val it = st.entries.keys.iterator()
        while (it.hasNext()) {
            val id = it.next()
            if (id !in allowed) it.remove()
        }
    }

    private fun cacheKey(context: Context): String = context.applicationContext.packageName

    private fun load(context: Context): State {
        val ck = cacheKey(context)
        memCacheByContext[ck]?.let { return it }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_STATE, null)
        val state = try {
            if (raw.isNullOrBlank()) {
                State(nextSessionId = 1L, sessions = mutableListOf(), entries = mutableMapOf())
            } else {
                decodeState(raw)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode state; resetting. ${e.message}", e)
            State(nextSessionId = 1L, sessions = mutableListOf(), entries = mutableMapOf())
        }

        memCacheByContext[ck] = state
        return state
    }

    private fun save(context: Context, state: State) {
        memCacheByContext[cacheKey(context)] = state
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_STATE, encodeState(state))
        }
    }

    private fun encodeState(st: State): String {
        val root = JSONObject()
        root.put("nextSessionId", st.nextSessionId)

        val sessArr = JSONArray()
        st.sessions.forEach { s ->
            val so = JSONObject()
            so.put("id", s.id)
            so.put("startedMs", s.startedMs)
            val ids = JSONArray()
            s.speciesIds.forEach { ids.put(it) }
            so.put("speciesIds", ids)
            sessArr.put(so)
        }
        root.put("sessions", sessArr)

        val entArr = JSONArray()
        st.entries.values.forEach { e ->
            val eo = JSONObject()
            eo.put("soortId", e.soortId)
            eo.put("score", e.score)
            eo.put("lastUsedMs", e.lastUsedMs)
            eo.put("lastSessionId", e.lastSessionId)
            entArr.put(eo)
        }
        root.put("entries", entArr)

        return root.toString()
    }

    private fun decodeState(raw: String): State {
        val root = JSONObject(raw)
        val nextSessionId = root.optLong("nextSessionId", 1L).coerceAtLeast(1L)

        val sessions = mutableListOf<Session>()
        val sessArr = root.optJSONArray("sessions") ?: JSONArray()
        for (i in 0 until sessArr.length()) {
            val so = sessArr.optJSONObject(i) ?: continue
            val id = so.optLong("id", 0L)
            val startedMs = so.optLong("startedMs", 0L)
            if (id <= 0L || startedMs <= 0L) continue

            val ids = LinkedHashSet<String>()
            val idsArr = so.optJSONArray("speciesIds") ?: JSONArray()
            for (j in 0 until idsArr.length()) {
                val sid = idsArr.optString(j, "")
                if (sid.isNotBlank()) ids.add(sid)
            }
            sessions.add(Session(id = id, startedMs = startedMs, speciesIds = ids))
        }

        val entries = mutableMapOf<String, Entry>()
        val entArr = root.optJSONArray("entries") ?: JSONArray()
        for (i in 0 until entArr.length()) {
            val eo = entArr.optJSONObject(i) ?: continue
            val soortId = eo.optString("soortId", "")
            if (soortId.isBlank()) continue
            val score = eo.optDouble("score", 0.0)
            val lastUsedMs = eo.optLong("lastUsedMs", 0L)
            val lastSessionId = eo.optLong("lastSessionId", 0L)
            if (lastUsedMs <= 0L) continue
            entries[soortId] = Entry(soortId, score, lastUsedMs, lastSessionId)
        }

        // Ensure in-memory invariants: keep only last MAX_SESSIONS
        sessions.sortByDescending { it.startedMs }
        if (sessions.size > MAX_SESSIONS) {
            sessions.subList(MAX_SESSIONS, sessions.size).clear()
        }

        val st = State(nextSessionId = nextSessionId, sessions = sessions, entries = entries)
        pruneEntriesNotInKeptSessions(st)
        return st
    }

    @Suppress("unused")
    fun invalidateCache() {
        memCacheByContext.clear()
    }
}
