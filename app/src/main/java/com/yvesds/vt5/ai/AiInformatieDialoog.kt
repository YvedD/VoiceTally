package com.yvesds.vt5.ai

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.appcompat.app.AlertDialog
import com.yvesds.vt5.core.ui.DialogStyler
import android.graphics.Color
import android.graphics.Typeface
import com.yvesds.vt5.R

/**
 * Dialoog voor het tonen van AI-suggesties per Gilde.
 */
object AiInformatieDialoog {
    private var isSuppressedForSession = false

    fun show(context: Context, suggesties: AiSuggesties) {
        if (isSuppressedForSession) return
        
        val builder = AlertDialog.Builder(context)
        builder.setTitle("AI Migratie Prognose")

        val message = SpannableStringBuilder()
        message.append("Verwachting op basis van historiek en weer:\n")
        message.append("Condities: ${suggesties.weerBeschrijving}\n\n")

        if (suggesties.guildResults.isEmpty()) {
            message.append("Geen specifieke trekpieken verwacht voor de huidige omstandigheden.")
        } else {
            suggesties.guildResults.forEach { res ->
                appendGuildLine(message, res)
            }
        }

        builder.setMessage(message)
        builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        builder.setNeutralButton("Niet meer tonen") { dialog, _ ->
            isSuppressedForSession = true
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
        DialogStyler.apply(dialog)
    }

    /**
     * Toont een uurlijks overzicht van de top-prognoses.
     */
    fun showHourly(context: Context, hourlyData: List<Pair<String, AiSuggesties>>) {
        if (isSuppressedForSession) return
        
        val builder = AlertDialog.Builder(context)
        builder.setTitle(context.getString(R.string.meta_ai_hourly_title))

        val message = SpannableStringBuilder()
        
        hourlyData.forEach { (time, suggesties) ->
            // Tijdstip header
            val timeStart = message.length
            message.append("🕒 $time: ")
            message.setSpan(StyleSpan(Typeface.BOLD), timeStart, message.length, 0)
            message.setSpan(ForegroundColorSpan(Color.LTGRAY), timeStart, message.length, 0)
            
            if (suggesties.guildResults.isEmpty()) {
                message.append("Geen pieken verwacht\n")
            } else {
                // Toon de top 2 gilde-winnaars voor dit uur om het compact te houden
                val top2 = suggesties.guildResults.sortedByDescending { it.kans }.take(2)
                top2.forEachIndexed { idx, res ->
                    val nameStart = message.length
                    message.append("${res.soortnaam} (${res.kans}%)")
                    message.setSpan(ForegroundColorSpan(getGuildColor(res.guildName)), nameStart, message.length, 0)
                    if (idx < top2.size - 1) message.append(", ")
                }
                message.append("\n")
            }
            message.append("\n")
        }

        builder.setMessage(message)
        builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        builder.setNeutralButton("Niet meer tonen") { dialog, _ ->
            isSuppressedForSession = true
            dialog.dismiss()
        }
        
        val dialog = builder.create()
        dialog.show()
        DialogStyler.apply(dialog)
    }

    private fun appendGuildLine(builder: SpannableStringBuilder, res: GuildSuggestie) {
        val start = builder.length
        
        // Gilde naam in het wit/vet
        val guildStart = builder.length
        builder.append("${res.guildName}: ")
        builder.setSpan(StyleSpan(Typeface.BOLD), guildStart, builder.length, 0)
        
        // Soortnaam in kleur op basis van gilde
        val nameStart = builder.length
        builder.append("${res.soortnaam} ")
        val color = getGuildColor(res.guildName)
        builder.setSpan(ForegroundColorSpan(color), nameStart, builder.length, 0)
        
        // Kans
        builder.append("${res.kans}%\n")
        builder.append("\n")
    }

    private fun getGuildColor(guildName: String): Int {
        return when {
            guildName.contains("Zang") -> Color.CYAN
            guildName.contains("Roof") -> Color.YELLOW
            guildName.contains("Reiger") -> Color.GREEN
            guildName.contains("Zee") -> Color.MAGENTA
            guildName.contains("Stelt") -> Color.parseColor("#FF9800") // Oranje
            guildName.contains("Water") -> Color.parseColor("#4FC3F7") // Lichtblauw
            else -> Color.LTGRAY
        }
    }

    data class AiSuggesties(
        val guildResults: List<GuildSuggestie>,
        val weerBeschrijving: String
    )

    data class GuildSuggestie(
        val guildName: String,
        val soortnaam: String,
        val kans: Int
    )
}
