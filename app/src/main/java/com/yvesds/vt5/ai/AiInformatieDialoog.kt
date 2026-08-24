package com.yvesds.vt5.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import android.view.LayoutInflater
import java.util.Calendar
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.yvesds.vt5.R
import com.yvesds.vt5.core.ui.DialogStyler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dialoog voor het tonen van AI-suggesties met on-the-fly foto's.
 */
object AiInformatieDialoog {
    private const val TAG = "AiInformatieDialoog"
    private var isSuppressedForSession = false

    fun show(context: Context, suggesties: AiSuggesties) {
        if (isSuppressedForSession) return
        
        val builder = AlertDialog.Builder(context)
        builder.setTitle("AI Migratie Prognose")

        // Custom list view voor de resultaten
        val listView = android.widget.ListView(context)
        listView.divider = null
        
        // Header toevoegen voor de condities
        val header = TextView(context)
        header.text = "Condities: ${suggesties.weerBeschrijving}\n"
        header.setPadding(40, 20, 40, 0)
        header.setTextColor(Color.GRAY)
        listView.addHeaderView(header)

        // Uitkijken voor: Sectie (Krenten Highlights)
        if (suggesties.rareHighlights.isNotEmpty()) {
            val highlightHeader = TextView(context)
            highlightHeader.text = "UITKIJKEN VOOR:"
            highlightHeader.setPadding(40, 30, 40, 10)
            highlightHeader.setTextColor(context.getColor(R.color.vt5_orange))
            highlightHeader.setTypeface(null, Typeface.BOLD)
            listView.addHeaderView(highlightHeader)
            
            suggesties.rareHighlights.forEach { item ->
                val view = LayoutInflater.from(context).inflate(R.layout.item_ai_suggestion, null)
                view.findViewById<TextView>(R.id.tvGuild).text = item.guildName
                val tvName = view.findViewById<TextView>(R.id.tvSpeciesName)
                tvName.text = if (item.expectedIndex != null && item.expectedIndex > 0) {
                    "${item.soortnaam} (BpH index ${"%.1f".format(item.expectedIndex)}ex/h)"
                } else {
                    item.soortnaam
                }
                tvName.setTextColor(getGuildColor(item.guildName))
                view.findViewById<TextView>(R.id.tvProbability).text = "Gunstige condities (${item.kans}%)"
                
                val ivIcon = view.findViewById<ImageView>(R.id.ivSpecies)
                ivIcon.setImageResource(android.R.drawable.ic_menu_gallery)
                ivIcon.tag = item.latinName
                
                if (!item.latinName.isNullOrBlank()) {
                    val currentLatin = item.latinName
                    CoroutineScope(Dispatchers.Main).launch {
                        val bitmap = withContext(Dispatchers.IO) { SpeciesImageHelper.getThumbnail(currentLatin) }
                        if (bitmap != null && ivIcon.tag == currentLatin) ivIcon.setImageBitmap(bitmap)
                    }
                }
                listView.addHeaderView(view)
            }
            
            // Divider na de highlights
            val divider = View(context)
            val params = android.widget.AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2)
            divider.layoutParams = params
            divider.setBackgroundColor(Color.parseColor("#444444"))
            listView.addHeaderView(divider)

            // Wat ademruimte na de divider
            val spacer = View(context)
            spacer.layoutParams = android.widget.AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 20)
            listView.addHeaderView(spacer)
        }

        val adapter = object : ArrayAdapter<GuildSuggestie>(context, 0, suggesties.guildResults) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                // Gebruik onze nieuwe custom layout
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_ai_suggestion, parent, false)
                val item = getItem(position) ?: return view
                
                val tvGuild = view.findViewById<TextView>(R.id.tvGuild)
                val tvName = view.findViewById<TextView>(R.id.tvSpeciesName)
                val tvProb = view.findViewById<TextView>(R.id.tvProbability)
                val ivIcon = view.findViewById<ImageView>(R.id.ivSpecies)
                
                tvGuild.text = item.guildName
                tvName.text = if (item.expectedIndex != null && item.expectedIndex > 0) {
                    "${item.soortnaam} (BpH index ${"%.1f".format(item.expectedIndex)}ex/h)"
                } else {
                    item.soortnaam
                }
                tvName.setTextColor(getGuildColor(item.guildName))
                tvProb.text = "Waarschijnlijkheid: ${item.kans}%"
                
                // Laden van foto op de achtergrond
                ivIcon.setImageResource(android.R.drawable.ic_menu_gallery)
                ivIcon.tag = item.latinName // Gebruik tag om recycling fouten te voorkomen
                
                if (!item.latinName.isNullOrBlank()) {
                    val currentLatin = item.latinName
                    CoroutineScope(Dispatchers.Main).launch {
                        val bitmap = withContext(Dispatchers.IO) {
                            SpeciesImageHelper.getThumbnail(currentLatin)
                        }
                        // Controleer of de view nog steeds dit item toont (tegen flikkeren)
                        if (bitmap != null && ivIcon.tag == currentLatin) {
                            ivIcon.setImageBitmap(bitmap)
                        }
                    }
                }
                
                return view
            }
        }
        
        listView.adapter = adapter
        builder.setView(listView)

        builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        builder.setNeutralButton("Niet meer tonen") { dialog, _ ->
            isSuppressedForSession = true
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
        DialogStyler.apply(dialog)
    }

    private fun getGuildColor(guildName: String): Int {
        return when {
            guildName.contains("Zang") -> Color.CYAN
            guildName.contains("Roof") -> Color.YELLOW
            guildName.contains("Reiger") -> Color.GREEN
            guildName.contains("Zeevogels") -> Color.MAGENTA // Specifieker maken
            guildName.contains("Stelt") -> Color.parseColor("#FF9800")
            guildName.contains("Water") -> Color.parseColor("#4FC3F7")
            guildName.contains("Kust") -> Color.parseColor("#009688")
            else -> Color.LTGRAY
        }
    }

    data class AiSuggesties(
        val guildResults: List<GuildSuggestie>,
        val rareHighlights: List<GuildSuggestie> = emptyList(), // De "Uitkijken voor" lijst
        val weerBeschrijving: String
    )

    data class GuildSuggestie(
        val guildName: String,
        val soortnaam: String,
        val kans: Int,
        val soortid: String,
        val debugReasoning: String = "",
        val latinName: String? = null,
        val expectedIndex: Float? = null
    )
}
