package com.yvesds.vt5.ai

import android.content.Context
import android.util.Log
import com.yvesds.vt5.core.ui.ProgressDialogHelper
import com.yvesds.vt5.utils.weather.Current
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AiSuggestieFetcher - Bridges between the UI and InferenceEngine.
 * 
 * Logic:
 * - Shows a progress dialog during analysis to avoid user confusion.
 * - Triggers the inference calculation (DB stats or TFLite).
 * - Displays the results in the styled AiInformatieDialoog.
 */
object AiSuggestieFetcher {
    private const val TAG = "AiSuggestieFetcher"

    suspend fun fetchAndShow(context: Context, cur: Current) {
        try {
            // Show analysis progress since DB query (154k rows) can take ~1-2 seconds
            val progress = withContext(Dispatchers.Main) {
                ProgressDialogHelper.show(context, "AI analyseert database...")
            }
            
            // Perform the actual calculation
            val suggesties = AiInferenceEngine.getSuggesties(context, cur)
            
            withContext(Dispatchers.Main) {
                progress.dismiss()
                
                // Only show dialog if we actually found useful suggestions
                if (suggesties.tijdstipSuggesties.isNotEmpty() || 
                    suggesties.weerSuggesties.isNotEmpty() || 
                    suggesties.periodeSuggesties.isNotEmpty()) {
                    AiInformatieDialoog.show(context, suggesties)
                } else {
                    Log.i(TAG, "No significant AI suggestions found for current conditions")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI suggesties fetch failed: ${e.message}")
        }
    }
}
