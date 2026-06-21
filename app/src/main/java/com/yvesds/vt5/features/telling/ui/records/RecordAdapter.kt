package com.yvesds.vt5.features.telling.ui.records

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.vt5.core.database.entity.ObservationEntity
import com.yvesds.vt5.databinding.ItemRecordRoomBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter voor de lijst van waarnemingen met ondersteuning voor selectie-modus.
 */
class RecordAdapter(
    private val onEdit: (ObservationEntity) -> Unit,
    private val onDelete: (ObservationEntity) -> Unit,
    private val onSelectionChanged: (ObservationEntity, Boolean) -> Unit
) : ListAdapter<ObservationEntity, RecordAdapter.ViewHolder>(DiffCallback()) {

    private val selectedIds = mutableSetOf<String>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun setSelectedIds(ids: Set<String>) {
        selectedIds.clear()
        selectedIds.addAll(ids)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecordRoomBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemRecordRoomBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ObservationEntity) {
            binding.apply {
                tvSoortNaam.text = item.speciesId
                tvAantalInfo.text = "Aantal: ${item.count}"
                
                val timeStr = timeFormat.format(Date(item.timestamp * 1000))
                val dirStr = item.direction ?: ""
                tvTijdRichting.text = if (dirStr.isNotBlank()) "$timeStr • $dirStr" else timeStr

                // Selectie state
                cbSelect.setOnCheckedChangeListener(null)
                cbSelect.isChecked = selectedIds.contains(item.id)
                cbSelect.setOnCheckedChangeListener { _, isChecked ->
                    onSelectionChanged(item, isChecked)
                }

                btnEdit.setOnClickListener { onEdit(item) }
                btnDelete.setOnClickListener { onDelete(item) }

                root.setOnClickListener {
                    cbSelect.isChecked = !cbSelect.isChecked
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ObservationEntity>() {
        override fun areItemsTheSame(oldItem: ObservationEntity, newItem: ObservationEntity) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ObservationEntity, newItem: ObservationEntity) =
            oldItem == newItem
    }
}
