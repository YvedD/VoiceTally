package com.yvesds.vt5.ai

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Utility for BSI Model management.
 * TFLite functionality has been removed in favor of Plan B (Bio-Statistische Intelligentie).
 */
object ModelManager {
    private const val TAG = "ModelManager"

    data class LoadedModel(val labels: List<String>)

    @Volatile
    private var loadedModel: LoadedModel? = null

    /**
     * Stub for ModelManager compatibility after removing TFLite.
     */
    fun loadAndSet(context: Context): String? {
        // BSI doesn't need an external model file, but we can load labels if needed.
        Log.i(TAG, "BSI Model Manager initialized (No TFLite needed)")
        return null
    }

    fun getLoadedModel(): LoadedModel? = loadedModel

    fun clearLoadedModel() { loadedModel = null }
}
