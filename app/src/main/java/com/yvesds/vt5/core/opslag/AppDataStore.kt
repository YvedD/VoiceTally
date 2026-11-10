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
    private val KEY_UPDATE_AVAILABLE = booleanPreferencesKey("app_update_available")
    private val KEY_UPDATE_LATEST_VERSION = stringPreferencesKey("app_update_latest_version")
    private val KEY_UPDATE_LATEST_URL = stringPreferencesKey("app_update_latest_url")
    private val KEY_UPDATE_LATEST_SHA256 = stringPreferencesKey("app_update_latest_sha256")
    private val KEY_UPDATE_LAST_CHECK_AT = longPreferencesKey("app_update_last_check_at")
    private val KEY_UPDATE_LAST_PROMPTED_VERSION = stringPreferencesKey("app_update_last_prompted_version")
    private val KEY_DEFAULT_TELPOST_ID = stringPreferencesKey("default_telpost_id")
    private val KEY_DEFAULT_TYPE_TELLING_CODE = stringPreferencesKey("default_type_telling_code")
    private val KEY_SKIP_SPECIES_SELECTION = booleanPreferencesKey("skip_species_selection")
    private const val PREFIX_RECORD_ID = "next_record_id_"

    data class UpdateSnapshot(
        val updateAvailable: Boolean,
        val latestVersion: String?,
        val latestUrl: String?,
        val latestSha256: String?,
        val lastCheckedAt: Long
    )

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

    suspend fun setUpdateSnapshot(
        context: Context,
        updateAvailable: Boolean,
        latestVersion: String?,
        latestUrl: String?,
        latestSha256: String?,
        checkedAt: Long
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_UPDATE_AVAILABLE] = updateAvailable
            if (latestVersion.isNullOrBlank()) prefs.remove(KEY_UPDATE_LATEST_VERSION) else prefs[KEY_UPDATE_LATEST_VERSION] = latestVersion
            if (latestUrl.isNullOrBlank()) prefs.remove(KEY_UPDATE_LATEST_URL) else prefs[KEY_UPDATE_LATEST_URL] = latestUrl
            if (latestSha256.isNullOrBlank()) prefs.remove(KEY_UPDATE_LATEST_SHA256) else prefs[KEY_UPDATE_LATEST_SHA256] = latestSha256
            prefs[KEY_UPDATE_LAST_CHECK_AT] = checkedAt
        }
    }

    suspend fun getUpdateSnapshot(context: Context): UpdateSnapshot {
        return context.dataStore.data.map { prefs ->
            UpdateSnapshot(
                updateAvailable = prefs[KEY_UPDATE_AVAILABLE] ?: false,
                latestVersion = prefs[KEY_UPDATE_LATEST_VERSION],
                latestUrl = prefs[KEY_UPDATE_LATEST_URL],
                latestSha256 = prefs[KEY_UPDATE_LATEST_SHA256],
                lastCheckedAt = prefs[KEY_UPDATE_LAST_CHECK_AT] ?: 0L
            )
        }.first()
    }

    suspend fun setLastPromptedUpdateVersion(context: Context, version: String?) {
        context.dataStore.edit { prefs ->
            if (version.isNullOrBlank()) prefs.remove(KEY_UPDATE_LAST_PROMPTED_VERSION)
            else prefs[KEY_UPDATE_LAST_PROMPTED_VERSION] = version
        }
    }

    suspend fun getLastPromptedUpdateVersion(context: Context): String? {
        return context.dataStore.data.map { it[KEY_UPDATE_LAST_PROMPTED_VERSION] }.first()
    }

    suspend fun setDefaultTelpost(context: Context, telpostId: String?) {
        context.dataStore.edit { prefs ->
            if (telpostId.isNullOrBlank()) prefs.remove(KEY_DEFAULT_TELPOST_ID)
            else prefs[KEY_DEFAULT_TELPOST_ID] = telpostId
        }
    }

    suspend fun getDefaultTelpost(context: Context): String? {
        return context.dataStore.data.map { it[KEY_DEFAULT_TELPOST_ID] }.first()
    }

    suspend fun setDefaultTypeTelling(context: Context, typeCode: String?) {
        context.dataStore.edit { prefs ->
            if (typeCode.isNullOrBlank()) prefs.remove(KEY_DEFAULT_TYPE_TELLING_CODE)
            else prefs[KEY_DEFAULT_TYPE_TELLING_CODE] = typeCode
        }
    }

    suspend fun getDefaultTypeTelling(context: Context): String? {
        return context.dataStore.data.map { it[KEY_DEFAULT_TYPE_TELLING_CODE] }.first()
    }

    suspend fun setSkipSpeciesSelection(context: Context, value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SKIP_SPECIES_SELECTION] = value
        }
    }

    suspend fun getSkipSpeciesSelection(context: Context): Boolean {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_SKIP_SPECIES_SELECTION] ?: false
        }.first()
    }
}
