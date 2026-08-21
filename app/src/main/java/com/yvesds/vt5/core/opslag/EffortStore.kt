package com.yvesds.vt5.core.opslag

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.effortDataStore: DataStore<Preferences> by preferencesDataStore(name = "vt5_effort")

/**
 * EffortStore - Beheert de persistente opslag van de totale tel-inspanning per telpost.
 * Gebruikt om zware database-aggregaties tijdens BSI-berekeningen te voorkomen.
 */
object EffortStore {
    private const val PREFIX_EFFORT = "total_seconds_"
    private val KEY_INITIALIZED = booleanPreferencesKey("effort_initialized")

    /**
     * Slaat de totale seconden op voor een specifieke telpost.
     */
    suspend fun addEffort(context: Context, telpostId: String, seconds: Long) {
        val key = longPreferencesKey(PREFIX_EFFORT + telpostId)
        context.effortDataStore.edit { prefs ->
            val current = prefs[key] ?: 0L
            prefs[key] = current + seconds
        }
    }

    /**
     * Haalt de totale inspanning op voor een lijst met telposten (een cluster).
     */
    suspend fun getTotalSecondsForCluster(context: Context, siteIds: List<String>): Long {
        val prefs = context.effortDataStore.data.first()
        return siteIds.sumOf { id ->
            prefs[longPreferencesKey(PREFIX_EFFORT + id)] ?: 0L
        }
    }

    /**
     * Controleert of de initiële scan van de database al heeft plaatsgevonden.
     */
    suspend fun isInitialized(context: Context): Boolean {
        return context.effortDataStore.data.map { it[KEY_INITIALIZED] ?: false }.first()
    }

    /**
     * Markeert de initiële scan als voltooid.
     */
    suspend fun setInitialized(context: Context) {
        context.effortDataStore.edit { it[KEY_INITIALIZED] = true }
    }

    /**
     * Reset alle tellers (bijv. na een database reset).
     */
    suspend fun resetAll(context: Context) {
        context.effortDataStore.edit { it.clear() }
    }
}
