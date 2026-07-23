package com.yvesds.vt5.core.opslag

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vt5_settings")

/**
 * AppDataStore: Moderne vervanging voor SharedPreferences voor persistente instellingen en tellers.
 */
object AppDataStore {
    private val KEY_NEXT_TELLING_ID = longPreferencesKey("next_telling_id")
    private val KEY_PREFIX_RECORD_ID = "next_record_id_"
    // Persisted URI to the AI model directory chosen by the user via SAF (OpenDocumentTree)
    private val KEY_AI_MODEL_DIR_URI = stringPreferencesKey("ai_model_dir_uri")
    private val KEY_AI_ENABLED = booleanPreferencesKey("ai_enabled")

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

    /**
     * Save the user-selected AI model directory (SAF Uri string) into DataStore.
     * Pass null to clear the stored value.
     */
    suspend fun setAiModelDirUri(context: Context, uriString: String?) {
        context.dataStore.edit { prefs ->
            if (uriString == null) {
                prefs.remove(KEY_AI_MODEL_DIR_URI)
            } else {
                prefs[KEY_AI_MODEL_DIR_URI] = uriString
            }
        }
    }

    /**
     * Retrieve the stored AI model directory Uri string (or null if not set).
     */
    suspend fun getAiModelDirUri(context: Context): String? {
        return context.dataStore.data.first()[KEY_AI_MODEL_DIR_URI]
    }

    /**
     * Set the AI enabled status.
     */
    suspend fun setAiEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AI_ENABLED] = enabled
        }
    }

    /**
     * Check if AI is enabled. Defaults to false.
     */
    suspend fun isAiEnabled(context: Context): Boolean {
        return context.dataStore.data.first()[KEY_AI_ENABLED] ?: false
    }
}
