package com.yvesds.vt5.ai

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.yvesds.vt5.core.ui.ProgressDialogHelper
import com.yvesds.vt5.utils.weather.Current
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AiSuggestieFetcher - Bridges between the UI and InferenceEngine.
 * 
 * Logic:
 * - Shows a progress dialog during analysis to avoid user confusion.
 * - Triggers the inference calculation (BSI DB stats).
 * - Displays the results in the styled AiInformatieDialoog.
 */
object AiSuggestieFetcher {
    private const val TAG = "AiSuggestieFetcher"

    suspend fun fetchAndShow(context: Context, cur: Current, hour: Int? = null) {
        Log.i(TAG, "Starting AI fetch sequence...")
        try {
            val progress = withContext(Dispatchers.Main) {
                ProgressDialogHelper.show(context, "AI analyseert database...")
            }
            
            try {
                Log.d(TAG, "Calling AiInferenceEngine.getSuggesties...")
                // Perform the actual calculation with optional hour override
                val suggesties = AiInferenceEngine.getSuggesties(context, cur, hourOverride = hour)
                
                withContext(Dispatchers.Main) {
                    // Start de nieuwe Prognose Activity
                    if (suggesties.guildResults.isNotEmpty() || suggesties.rareHighlights.isNotEmpty()) {
                        val intent = android.content.Intent(context, AiPrognoseActiviteit::class.java)
                        intent.putExtra("prognose_data", suggesties)
                        context.startActivity(intent)
                    } else {
                        Toast.makeText(context, "AI: Geen specifieke trekpieken gevonden.", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    progress.dismiss()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI suggesties fetch failed: ${e.message}", e)
        }
    }
}
