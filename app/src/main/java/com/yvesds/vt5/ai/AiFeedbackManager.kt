package com.yvesds.vt5.ai

import android.content.Context
import android.util.Log
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * AiFeedbackManager - Slaat gebruikerswaarderingen op in een JSON bestand.
 * Nu met ondersteuning voor overschrijven en resetten (0-sterren negeren).
 */
object AiFeedbackManager {
    private const val FILENAME = "user_evaluations.json"
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Serializable
    data class Evaluation(
        val soortid: String,
        val rating: Float, // 0.0 tot 1.0
        val month: Int,
        val wind: String
    )

    @Serializable
    data class FeedbackStore(val evaluations: MutableList<Evaluation> = mutableListOf())

    /**
     * Slaat een nieuwe waardering op. Als stars 0.0 is, wordt de waardering verwijderd.
     */
    fun saveRating(context: Context, soortid: String, stars: Float, wind: String) {
        val saf = SaFStorageHelper(context)
        val month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
        val windUpper = wind.uppercase()
        val rating = stars / 5.0f

        try {
            val store = loadStore(saf)
            
            // Verwijder eerst oude ratings voor dezelfde context (soort + maand + wind)
            store.evaluations.removeAll { 
                it.soortid == soortid && it.month == month && it.wind == windUpper 
            }
            
            // Alleen toevoegen als de rating > 0 is
            if (stars > 0.0f) {
                store.evaluations.add(Evaluation(soortid, rating, month, windUpper))
                Log.d("AiFeedback", "Feedback opgeslagen: $soortid -> $stars sterren")
            } else {
                Log.d("AiFeedback", "Feedback gereset (verwijderd) voor $soortid")
            }
            
            val jsonStr = json.encodeToString(store)
            writeToSaf(saf, jsonStr)
        } catch (e: Exception) {
            Log.e("AiFeedback", "Fout bij opslaan feedback: ${e.message}")
        }
    }

    /**
     * Geeft de gemiddelde waardering voor een soort onder specifieke condities.
     * Alleen scores > 0 worden meegeteld in het gemiddelde.
     */
    fun getCorrectionFactor(context: Context, soortid: String, wind: String): Float {
        val saf = SaFStorageHelper(context)
        val month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
        val store = loadStore(saf)
        
        val relevant = store.evaluations.filter { 
            it.soortid == soortid && it.month == month && it.wind == wind.uppercase() && it.rating > 0.0f
        }
        
        if (relevant.isEmpty()) return 1.0f
        
        // We nemen het gemiddelde. Als dit bijvoorbeeld 0.2 is (1 ster), 
        // dan wordt de kans in de motor flink naar beneden getrokken.
        return relevant.map { it.rating }.average().toFloat()
    }

    /**
     * Zoekt of er een specifieke rating bestaat voor deze soort/wind/maand combo.
     * Geeft null als er geen feedback is.
     */
    fun getExistingRating(context: Context, soortid: String, wind: String): Float? {
        val saf = SaFStorageHelper(context)
        val month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
        val store = loadStore(saf)
        return store.evaluations.find { 
            it.soortid == soortid && it.month == month && it.wind == wind.uppercase() 
        }?.rating
    }

    private fun loadStore(saf: SaFStorageHelper): FeedbackStore {
        return try {
            val content = saf.readFeedbackFile(FILENAME) ?: return FeedbackStore()
            json.decodeFromString<FeedbackStore>(content)
        } catch (_: Exception) { FeedbackStore() }
    }

    private fun writeToSaf(saf: SaFStorageHelper, content: String) {
        saf.writeFeedbackFile(FILENAME, content)
    }
}
