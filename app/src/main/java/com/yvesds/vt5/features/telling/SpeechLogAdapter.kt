package com.yvesds.vt5.features.telling

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.vt5.databinding.ItemSpeechLogBinding
import com.yvesds.vt5.hoofd.InstellingenScherm
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SpeechLogAdapter: light-weight RecyclerView adapter for speech logs.
 *
 * Changes:
 * - Default showPartialsInRow = false (TellingScherm composes the partial text; adapter stays cheap).
 * - No regex allocations in onBindViewHolder anymore.
 * - Rows are colorized by row.bron:
 *     - "final" -> bright green
 *     - "partial" -> subtle gray
 *     - "alias"  -> amber (example)
 *     - others  -> default text color
 * - Keeps minimal work in onBindViewHolder and relies on TellingScherm to provide already-processed row.tekst.
 * - Lettergrootte is nu configureerbaar via InstellingenScherm (gecached voor performance)
 */
class SpeechLogAdapter :
    ListAdapter<TellingScherm.SpeechLogRow, SpeechLogAdapter.VH>(Diff) {

    init {
        setHasStableIds(true)
    }
    
    // Gecachede lettergrootte voor betere performance
    private var cachedPartialsTextSizeSp: Float = InstellingenScherm.DEFAULT_LETTERGROOTTE_SP.toFloat()
    private var cachedFinalsTextSizeSp: Float = InstellingenScherm.DEFAULT_LETTERGROOTTE_SP.toFloat()
    private var cachedPartialsTextColor: Int = android.graphics.Color.WHITE
    private var cachedFinalsTextColor: Int = android.graphics.Color.WHITE

    object Diff : DiffUtil.ItemCallback<TellingScherm.SpeechLogRow>() {
        override fun areItemsTheSame(
            oldItem: TellingScherm.SpeechLogRow,
            newItem: TellingScherm.SpeechLogRow
        ): Boolean {
            // Timestamp + text is a reasonable identity for log rows
            return oldItem.ts == newItem.ts && oldItem.tekst == newItem.tekst && oldItem.bron == newItem.bron
        }

        override fun areContentsTheSame(
            oldItem: TellingScherm.SpeechLogRow,
            newItem: TellingScherm.SpeechLogRow
        ): Boolean = oldItem == newItem
    }

    class VH(val vb: ItemSpeechLogBinding) : RecyclerView.ViewHolder(vb.root)

    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /**
     * If TellingScherm already composes the display text, set to false to avoid
     * additional work here. Default false for cheaper binds.
     */
    var showPartialsInRow: Boolean = false
    
    /**
     * Update de gecachede lettergrootte. Roep dit aan bij onResume van de Activity.
     */
    fun updateTextSize(textSizeSp: Int) {
        cachedPartialsTextSizeSp = textSizeSp.toFloat()
        cachedFinalsTextSizeSp = textSizeSp.toFloat()
    }

    fun updatePartialsTextSize(textSizeSp: Int) {
        cachedPartialsTextSizeSp = textSizeSp.toFloat()
    }

    fun updateFinalsTextSize(textSizeSp: Int) {
        cachedFinalsTextSizeSp = textSizeSp.toFloat()
    }

    /**
     * Update de gecachede tekstkleur voor partials logregels.
     */
    fun updatePartialsTextColor(argb: Int) {
        cachedPartialsTextColor = argb
    }

    /**
     * Update de gecachede tekstkleur voor finals logregels.
     */
    fun updateFinalsTextColor(argb: Int) {
        cachedFinalsTextColor = argb
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vb = ItemSpeechLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // Initialiseer cache bij eerste ViewHolder creatie
        cachedPartialsTextSizeSp = InstellingenScherm.getPartialsTextSizeSp(parent.context).toFloat()
        cachedFinalsTextSizeSp = InstellingenScherm.getFinalsTextSizeSp(parent.context).toFloat()
        cachedPartialsTextColor = InstellingenScherm.getPartialsTextColor(parent.context)
        cachedFinalsTextColor = InstellingenScherm.getFinalsTextColor(parent.context)
        return VH(vb)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = getItem(position)
        // time formatting (cheap)
        holder.vb.tvTime.text = fmt.format(Date(row.ts * 1000L))

        // Use the already-prepared text from TellingScherm; keep adapter logic minimal.
        val displayText = row.tekst ?: ""
        holder.vb.tvMsg.text = displayText

        val defaultPartials = cachedPartialsTextColor
        val defaultFinals = cachedFinalsTextColor

        when (row.bron) {
            "final" -> {
                holder.vb.tvMsg.setTextSize(TypedValue.COMPLEX_UNIT_SP, cachedFinalsTextSizeSp)
                holder.vb.tvMsg.setTextColor(defaultFinals)
                holder.vb.tvTime.setTextColor(defaultFinals)
            }
            "partial" -> {
                holder.vb.tvMsg.setTextSize(TypedValue.COMPLEX_UNIT_SP, cachedPartialsTextSizeSp)
                holder.vb.tvMsg.setTextColor(defaultPartials)
                holder.vb.tvTime.setTextColor(defaultPartials)
            }
            "alias", "raw", "systeem", "manueel" -> {
                holder.vb.tvMsg.setTextSize(TypedValue.COMPLEX_UNIT_SP, cachedPartialsTextSizeSp)
                holder.vb.tvMsg.setTextColor(defaultPartials)
                holder.vb.tvTime.setTextColor(defaultPartials)
            }
            else -> {
                holder.vb.tvMsg.setTextSize(TypedValue.COMPLEX_UNIT_SP, cachedPartialsTextSizeSp)
                holder.vb.tvMsg.setTextColor(defaultPartials)
                holder.vb.tvTime.setTextColor(defaultPartials)
            }
        }
    }
    override fun getItemId(position: Int): Long {
        val item = getItem(position)
        // stable id based on ts and tekst hash and bron - avoid collisions as best effort
        return (31L * item.ts + item.tekst.hashCode() + item.bron.hashCode()).toLong()
    }
}