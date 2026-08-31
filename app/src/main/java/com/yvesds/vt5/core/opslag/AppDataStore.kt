package com.yvesds.vt5.core.opslag

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Extensie property voor DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vt5_settings")

/**
 * AppDataStore: Moderne vervanging voor SharedPreferences voor persistente instellingen en tellers.
 * Gebruikt Jetpack DataStore Preferences.
 */
object AppDataStore {
    private val KEY_NEXT_TELLING_ID = longPreferencesKey("next_telling_id")
    private val KEY_AI_ENABLED = booleanPreferencesKey("ai_enabled")
    private val KEY_AI_MODEL_DIR_URI = stringPreferencesKey("ai_model_dir_uri")
    private val KEY_USE_NEURAL_INFERENCE = booleanPreferencesKey("use_neural_inference")
    private val KEY_USE_DAILY_ANALYSIS_WEIGHTS = booleanPreferencesKey("use_daily_analysis_weights")
    private val KEY_KRENTEN_THRESHOLD = intPreferencesKey("ai_krenten_threshold")
    private const val PREFIX_RECORD_ID = "next_record_id_"

    /**
     * Haalt het volgende unieke telling ID op en verhoogt de teller.
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
     * Reserveer een blok telling IDs voor bulk imports.
     */
    suspend fun reserveTellingIds(context: Context, amount: Int): LongRange {
        var start = 1L
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_NEXT_TELLING_ID] ?: 1L
            start = current
            prefs[KEY_NEXT_TELLING_ID] = current + amount.toLong()
        }
        return start until (start + amount)
    }

    /**
     * Zet de telling ID teller terug naar 1.
     */
    suspend fun resetTellingId(context: Context) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NEXT_TELLING_ID] = 1L
        }
    }

    /**
     * Haalt het volgende record ID op voor een specifieke telling.
     */
    suspend fun nextRecordId(context: Context, tellingId: String): String {
        val key = longPreferencesKey(PREFIX_RECORD_ID + tellingId)
        var result = 1L
        context.dataStore.edit { prefs ->
            val current = prefs[key] ?: 1L
            result = current
            prefs[key] = current + 1L
        }
        return result.toString()
    }

    /**
     * AI-gerelateerde instellingen.
     */
    suspend fun setAiEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AI_ENABLED] = enabled
        }
    }

    suspend fun isAiEnabled(context: Context): Boolean {
        return context.dataStore.data.map { it[KEY_AI_ENABLED] ?: false }.first()
    }

    suspend fun setAiModelDirUri(context: Context, uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(KEY_AI_MODEL_DIR_URI)
            else prefs[KEY_AI_MODEL_DIR_URI] = uri
        }
    }

    suspend fun getAiModelDirUri(context: Context): String? {
        return context.dataStore.data.map { it[KEY_AI_MODEL_DIR_URI] }.first()
    }

    suspend fun setKrentenThreshold(context: Context, threshold: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_KRENTEN_THRESHOLD] = threshold
        }
    }

    suspend fun getKrentenThreshold(context: Context): Int {
        return context.dataStore.data.map { it[KEY_KRENTEN_THRESHOLD] ?: 100 }.first()
    }

    /**
     * Enable/disable use of the on-device neural inference (global override).
     * Default is TRUE for backwards-on behavior requested by product.
     */
    suspend fun setUseNeuralInference(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USE_NEURAL_INFERENCE] = enabled
        }
    }

    suspend fun isUseNeuralInference(context: Context): Boolean {
        return context.dataStore.data.map { it[KEY_USE_NEURAL_INFERENCE] ?: true }.first()
    }

    /**
     * Enable/disable usage of daily-analysis-based sample weights (prototype experiment).
     * Default is TRUE so that effective teldag-verslagen meetellen in training & inference.
     */
    suspend fun setUseDailyAnalysisWeights(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USE_DAILY_ANALYSIS_WEIGHTS] = enabled
        }
    }

    suspend fun isUseDailyAnalysisWeights(context: Context): Boolean {
        return context.dataStore.data.map { it[KEY_USE_DAILY_ANALYSIS_WEIGHTS] ?: true }.first()
    }
}
