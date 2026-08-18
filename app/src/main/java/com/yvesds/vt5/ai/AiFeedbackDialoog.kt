package com.yvesds.vt5.ai

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.entities.AiLog
import com.yvesds.vt5.core.ui.DialogStyler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * AiFeedbackDialoog - Beheert de evaluatie achteraf per vogelsoort.
 * Nu met 'Auto-Star' pre-fill op basis van de werkelijke telling.
 */
object AiFeedbackDialoog {

    fun show(context: Context, log: AiLog, onComplete: () -> Unit) {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_ai_species_evaluation, null)
        val rv = view.findViewById<RecyclerView>(R.id.rvSpeciesEval)
        val tvContext = view.findViewById<TextView>(R.id.tvEvalContext)

        // Gebruik een IO scope voor de database berekeningen
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val db = VoiceTallyDatabase.getDatabase(context)
                val contextJson = JSONObject(log.requestContext)
                val wind = contextJson.optString("wind", "Onbekend")
                val temp = contextJson.optDouble("temp", 0.0)
                val cal = Calendar.getInstance().apply { timeInMillis = log.timestamp }
                val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
                
                tvContext.text = "Historisch moment: $wind | ${temp.toInt()}°C"

                val suggestionsJson = JSONObject(log.suggestions)
                val items = suggestionsJson.optJSONArray("items") ?: JSONArray() // Fail-safe lege array

                // 1. Haal de werkelijke aantallen van de sessie op
                val sessionCounts = withContext(Dispatchers.IO) {
                    if (log.tellingid.isBlank() || log.tellingid == "manual" || log.tellingid == "auto") emptyMap()
                    else db.tellingDao().getSessionCounts(log.tellingid).associateBy { it.soortid }
                }

                val speciesList = mutableListOf<EvaluationItem>()
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val speciesId = item.optString("id", "")
                    val speciesName = item.optString("name", "Onbekende soort")
                    
                    if (speciesId.isBlank()) continue

                    // 2. Automatische sterren berekening
                    val actualCount = sessionCounts[speciesId]?.count?.toFloat() ?: 0f
                    var autoStars = 0f
                    
                    if (actualCount > 0) {
                        val histAvg = withContext(Dispatchers.IO) {
                            db.tellingDao().getHistoricalAverageForWindow(speciesId, dayOfYear - 10, dayOfYear + 10)
                        } ?: 1f
                        
                        autoStars = when {
                            actualCount <= 2 -> 1f
                            actualCount < histAvg * 0.5f -> 2f
                            actualCount < histAvg * 1.5f -> 3f
                            actualCount < histAvg * 3.0f -> 4f
                            else -> 5f
                        }
                    }

                    val existing = AiFeedbackManager.getExistingRating(context, speciesId, wind)
                    speciesList.add(EvaluationItem(
                        id = speciesId,
                        name = speciesName,
                        currentRating = if (existing != null) existing * 5.0f else autoStars
                    ))
                }

                val isTablet = context.resources.configuration.smallestScreenWidthDp >= 600
                val spanCount = if (isTablet) 2 else 1
                rv.layoutManager = GridLayoutManager(context, spanCount)
                rv.adapter = SpeciesEvalAdapter(speciesList)

            } catch (e: Exception) {
                Log.e("AiFeedbackDialoog", "Fout bij laden log-details: ${e.message}")
            }
        }

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setPositiveButton("Klaar") { _, _ -> onComplete() }
            .create()

        dialog.show()
        DialogStyler.apply(dialog)
    }

    private data class EvaluationItem(
        val id: String, 
        val name: String,
        var currentRating: Float = 0f
    )

    private class SpeciesEvalAdapter(
        private val items: List<EvaluationItem>
    ) : RecyclerView.Adapter<SpeciesEvalAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_ai_species_eval, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvName.text = "${item.name} (${item.currentRating.toInt()} sterren)"
        }

        override fun getItemCount() = items.size

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvName = v.findViewById<TextView>(R.id.tvSpeciesName)
        }
    }
}
