package com.yvesds.vt5.ai

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Utility for BSI Model management.
 * (TFLite functionality is not used in this project).
 */
object ModelManager {
    private const val TAG = "ModelManager"

    data class LoadedModel(val labels: List<String>)

    @Volatile
    private var loadedModel: LoadedModel? = null

    /**
     * Stub for ModelManager compatibility.
     */
    fun loadAndSet(context: Context): String? {
        Log.i(TAG, "BSI Model Manager initialized")
        return null
    }

    fun getLoadedModel(): LoadedModel? = loadedModel

    fun clearLoadedModel() { loadedModel = null }
}
