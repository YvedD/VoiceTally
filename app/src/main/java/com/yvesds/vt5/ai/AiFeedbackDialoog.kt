package com.yvesds.vt5.ai

import android.content.Context
import android.view.LayoutInflater
import android.widget.RatingBar
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.yvesds.vt5.R
import com.yvesds.vt5.core.ui.DialogStyler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dialoog voor het verzamelen van gebruikersfeedback over de AI-prognoses.
 */
object AiFeedbackDialoog {

    fun show(context: Context, tellingId: String, logId: Int? = null, onComplete: () -> Unit) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_ai_feedback, null)
        val rbAccuracy = view.findViewById<RatingBar>(R.id.rbAiAccuracy)
        val etFeedback = view.findViewById<EditText>(R.id.etAiFeedbackText)

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setPositiveButton("Versturen") { _, _ ->
                val rating = rbAccuracy.rating.toInt()
                val text = etFeedback.text.toString()
                saveFeedback(context, tellingId, rating, text)
                
                // Also update Room DB if logId provided
                if (logId != null) {
                    updateDatabaseRecord(context, logId, rating, text)
                }
                
                onComplete()
            }
            .setNegativeButton("Overslaan") { _, _ ->
                onComplete()
            }
            .create()

        dialog.show()
        DialogStyler.apply(dialog)
    }

    private fun saveFeedback(context: Context, tellingId: String, rating: Int, text: String) {
        try {
            val feedback = JSONObject()
            feedback.put("tellingId", tellingId)
            feedback.put("rating", rating)
            feedback.put("comment", text)
            feedback.put("timestamp", System.currentTimeMillis())

            val filename = "feedback_${tellingId}_${System.currentTimeMillis()}.json"
            
            // We gebruiken de 'feedback' submap in de AI-structuur
            val vt5 = com.yvesds.vt5.core.opslag.SaFStorageHelper(context).getVt5DirIfExists()
            val aiDir = vt5?.findFile("AI-models")
            val feedbackDir = aiDir?.findFile("feedback")

            if (feedbackDir != null) {
                val file = feedbackDir.createFile("application/json", filename)
                if (file != null) {
                    context.contentResolver.openOutputStream(file.uri)?.use { out ->
                        out.write(feedback.toString(2).toByteArray(Charsets.UTF_8))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AiFeedbackDialoog", "Failed to save feedback: ${e.message}")
        }
    }

    private fun updateDatabaseRecord(context: Context, logId: Int, rating: Int, text: String) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val db = com.yvesds.vt5.core.database.VoiceTallyDatabase.getDatabase(context)
                val log = db.tellingDao().getAiLogById(logId)
                if (log != null) {
                    db.tellingDao().updateAiLog(log.copy(rating = rating, feedback = text))
                }
            } catch (e: Exception) {
                android.util.Log.e("AiFeedbackDialoog", "Failed to update DB record: ${e.message}")
            }
        }
    }
}
