package com.yvesds.vt5.core.opslag

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vt5_settings")

/**
 * AppDataStore: Moderne vervanging voor SharedPreferences voor persistente instellingen en tellers.
 */
object AppDataStore {
    private val KEY_NEXT_TELLING_ID = longPreferencesKey("next_telling_id")
    private val KEY_PREFIX_RECORD_ID = "next_record_id_"

    /**
     * Haalt het volgende unieke telling ID op en verhoogt de teller.
     * Returned de waarde VOOR de verhoging.
     */
    suspend fun nextTellingId(context: Context): String {
        var result = 1L
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_NEXT_TELLING_ID] ?: 1L
            result = current
            prefs[KEY_NEXT_TELLING_ID] = current + 1L
        }
        return result.toString()
    }

    /**
     * Reserveer in 1 DataStore-update een blok opeenvolgende telling IDs.
     * Dit is sneller voor bulk-imports dan ID-per-ID ophalen.
     */
    suspend fun reserveTellingIds(context: Context, amount: Int): LongRange {
        require(amount > 0) { "amount must be > 0" }

        var start = 1L
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_NEXT_TELLING_ID] ?: 1L
            start = current
            prefs[KEY_NEXT_TELLING_ID] = current + amount
        }
        return start..(start + amount - 1L)
    }

    /**
     * Zet de telling ID teller terug op 1 (of 0 naar keuze).
     */
    suspend fun resetTellingId(context: Context) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NEXT_TELLING_ID] = 1L
        }
    }

    /**
     * Haalt het volgende record ID op voor een specifieke telling en verhoogt de teller.
     * Returned de waarde VOOR de verhoging.
     */
    suspend fun nextRecordId(context: Context, tellingId: String): String {
        val key = longPreferencesKey(KEY_PREFIX_RECORD_ID + tellingId)
        var result = 1L
        context.dataStore.edit { prefs ->
            val current = prefs[key] ?: 1L
            result = current
            prefs[key] = current + 1L
        }
        return result.toString()
    }
}
