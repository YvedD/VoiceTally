package com.yvesds.vt5.ai

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.yvesds.vt5.R
import com.yvesds.vt5.core.ui.DialogStyler
import android.graphics.Color

/**
 * Dialoog voor het tonen van AI-suggesties in het MetadataScherm.
 */
object AiInformatieDialoog {
    private var isSuppressedForSession = false

    fun show(context: Context, suggesties: AiSuggesties) {
        if (isSuppressedForSession) return
        
        val builder = AlertDialog.Builder(context)
        builder.setTitle("AI Informatie!")

        val message = SpannableStringBuilder()
        
        message.append("In het verleden werden bij de huidige omstandigheden:\n\n")

        // Tijdstip
        message.append("Tijdstip van de dag:\n")
        suggesties.tijdstipSuggesties.forEach { 
            appendColoredLine(message, "${it.soortnaam} ${it.kans}% kans", Color.CYAN)
        }
        message.append("\n")

        // Weer
        message.append("Weersomstandigheden:\n")
        message.append("${suggesties.weerBeschrijving}:\n")
        suggesties.weerSuggesties.forEach { 
            val suffix = if (it.isZeldzaam) " (zeldzame soort)" else if (it.isPiek) " (piekperiode)" else ""
            appendColoredLine(message, "${it.soortnaam} ${it.kans}% kans$suffix", Color.YELLOW)
        }
        message.append("\n")

        // Periode
        message.append("Periode van het jaar:\n")
        suggesties.periodeSuggesties.forEach { 
            val suffix = if (it.isHogeAantallen) " (hogere aantallen)" else if (it.isPiek) " (piekperiode)" else ""
            appendColoredLine(message, "${it.soortnaam} ${it.kans}% kans$suffix", Color.GREEN)
        }

        builder.setMessage(message)
        
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }
        
        builder.setNeutralButton("Niet meer tonen") { dialog, _ ->
            isSuppressedForSession = true
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
        DialogStyler.apply(dialog)
    }

    private fun appendColoredLine(builder: SpannableStringBuilder, text: String, color: Int) {
        val start = builder.length
        builder.append("  • $text\n")
        builder.setSpan(ForegroundColorSpan(color), start, builder.length, 0)
    }

    data class AiSuggesties(
        val tijdstipSuggesties: List<Suggestie>,
        val weerBeschrijving: String,
        val weerSuggesties: List<Suggestie>,
        val periodeSuggesties: List<Suggestie>
    )

    data class Suggestie(
        val soortnaam: String,
        val kans: Int,
        val isZeldzaam: Boolean = false,
        val isPiek: Boolean = false,
        val isHogeAantallen: Boolean = false
    )
}
