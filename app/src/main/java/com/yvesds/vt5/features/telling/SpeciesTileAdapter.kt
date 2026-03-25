package com.yvesds.vt5.features.telling

import android.graphics.Color
import android.util.TypedValue
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.vt5.R
import com.yvesds.vt5.databinding.ItemSpeciesTileBinding
import com.yvesds.vt5.hoofd.InstellingenScherm

/**
 * Adapter voor soort-tegels met optimalisaties voor efficiente updates:
 * - Efficiënt DiffUtil met payloads voor alleen aantal-wijzigingen
 * - ViewHolder pattern met bindingadapter pattern
 * - Stabiele IDs voor betere animaties
 * - Lettergrootte is nu configureerbaar via InstellingenScherm (gecached voor performance)
 */
class SpeciesTileAdapter(
    private val onTileSingleTap: (position: Int) -> Unit,
    private val onTileDoubleTap: (position: Int) -> Unit,
    private val onTileLongPress: (position: Int) -> Unit
) : ListAdapter<TellingScherm.SoortRow, SpeciesTileAdapter.VH>(Diff) {

    private data class CountPayload(
        val countMain: Int,
        val countReturn: Int,
        val pendingMainCount: Int
    )

    init {
        setHasStableIds(true)
    }
    
    // Gecachede lettergrootte voor betere performance
    private var cachedTextSizeSp: Float = InstellingenScherm.DEFAULT_LETTERGROOTTE_SP.toFloat()

    object Diff : DiffUtil.ItemCallback<TellingScherm.SoortRow>() {
        override fun areItemsTheSame(
            oldItem: TellingScherm.SoortRow,
            newItem: TellingScherm.SoortRow
        ): Boolean = oldItem.soortId == newItem.soortId

        override fun areContentsTheSame(
            oldItem: TellingScherm.SoortRow,
            newItem: TellingScherm.SoortRow
        ): Boolean = oldItem.soortId == newItem.soortId &&
                oldItem.naam == newItem.naam &&
                oldItem.countMain == newItem.countMain &&
                oldItem.countReturn == newItem.countReturn &&
                oldItem.pendingMainCount == newItem.pendingMainCount

        override fun getChangePayload(
            oldItem: TellingScherm.SoortRow,
            newItem: TellingScherm.SoortRow
        ): Any? {
            // Als alleen de aantallen zijn veranderd, geef dan de nieuwe counts terug
            if (oldItem.soortId == newItem.soortId &&
                oldItem.naam == newItem.naam &&
                (
                    oldItem.countMain != newItem.countMain ||
                        oldItem.countReturn != newItem.countReturn ||
                        oldItem.pendingMainCount != newItem.pendingMainCount
                    )) {
                return CountPayload(newItem.countMain, newItem.countReturn, newItem.pendingMainCount)
            }
            return null
        }
    }

    class VH(val vb: ItemSpeciesTileBinding) : RecyclerView.ViewHolder(vb.root)
    
    /**
     * Update de gecachede lettergrootte. Roep dit aan bij onResume van de Activity.
     */
    fun updateTextSize(textSizeSp: Int) {
        cachedTextSizeSp = textSizeSp.toFloat()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vb = ItemSpeciesTileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // Initialiseer cache bij eerste ViewHolder creatie
        cachedTextSizeSp = InstellingenScherm.getLettergroottTegelsSp(parent.context).toFloat()
        return VH(vb)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = getItem(position)
        bindRow(holder, row)
        holder.vb.tileRoot.setOnClickListener { }

        val gestureDetector = GestureDetector(holder.itemView.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    holder.vb.tileRoot.performClick()
                    onTileSingleTap(pos)
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    holder.vb.tileRoot.performClick()
                    onTileDoubleTap(pos)
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    holder.vb.tileRoot.performClick()
                    onTileLongPress(pos)
                }
            }
        })

        holder.vb.tileRoot.setOnTouchListener { view, event ->
            gestureDetector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                view.performClick()
            }
            true
        }
    }

    private fun bindRow(holder: VH, row: TellingScherm.SoortRow) {
        holder.vb.tvName.text = row.naam
        holder.vb.tvCountMain.text = row.countMain.toString()
        holder.vb.tvCountReturn.text = row.countReturn.toString()
        
        // Pas gecachede lettergrootte toe
        holder.vb.tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, cachedTextSizeSp)
        holder.vb.tvCountMain.setTextSize(TypedValue.COMPLEX_UNIT_SP, cachedTextSizeSp)
        holder.vb.tvCountReturn.setTextSize(TypedValue.COMPLEX_UNIT_SP, cachedTextSizeSp)

        applyPendingState(holder, row.pendingMainCount > 0)
    }

    private fun applyPendingState(holder: VH, hasPending: Boolean) {
        val context = holder.itemView.context
        val pendingColor = context.getColor(R.color.vt5_orange)
        val defaultName = Color.WHITE
        val defaultMain = context.getColor(R.color.vt5_green)
        val defaultReturn = context.getColor(R.color.vt5_light_blue)
        val strokeColor = if (hasPending) pendingColor else defaultReturn

        holder.vb.tvName.setTextColor(if (hasPending) pendingColor else defaultName)
        holder.vb.tvCountMain.setTextColor(if (hasPending) pendingColor else defaultMain)
        holder.vb.tvCountReturn.setTextColor(defaultReturn)
        holder.vb.tileRoot.strokeColor = strokeColor
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            // Als er een payload is, update dan alleen de aantallen
            val payload = payloads[0]
            if (payload is CountPayload) {
                holder.vb.tvCountMain.text = payload.countMain.toString()
                holder.vb.tvCountReturn.text = payload.countReturn.toString()
                holder.vb.tvCountMain.setTextSize(TypedValue.COMPLEX_UNIT_SP, cachedTextSizeSp)
                holder.vb.tvCountReturn.setTextSize(TypedValue.COMPLEX_UNIT_SP, cachedTextSizeSp)
                applyPendingState(holder, payload.pendingMainCount > 0)
                return
            }
        }

        // Anders doe een volledige binding
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun getItemId(position: Int): Long {
        // stabiel ID op basis van soortId
        return getItem(position).soortId.hashCode().toLong()
    }
}