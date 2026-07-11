package com.yvesds.vt5.core.database.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.entities.TellingHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DatabaseTellingLijstActiviteit : AppCompatActivity() {

    private lateinit var database: VoiceTallyDatabase
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnDeleteSelected: MaterialButton
    private lateinit var tellingenAdapter: TellingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_db_telling_lijst)

        database = VoiceTallyDatabase.getDatabase(this)
        recyclerView = findViewById(R.id.rvTellingen)
        recyclerView.layoutManager = LinearLayoutManager(this)
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected)

        findViewById<MaterialButton>(R.id.btnTerug).setOnClickListener { finish() }

        btnDeleteSelected.setOnClickListener {
            val selected = tellingenAdapter.getSelectedTellingIds()
            if (selected.isEmpty()) {
                return@setOnClickListener
            }
            showDeleteSelectedDialog(selected)
        }

        tellingenAdapter = TellingAdapter(
            onClick = { telling ->
                val intent = Intent(this@DatabaseTellingLijstActiviteit, DatabaseTellingDetailActiviteit::class.java)
                intent.putExtra("tellingid", telling.tellingid)
                startActivity(intent)
            },
            onSelectionChanged = { updateDeleteButtonState() }
        )
        recyclerView.adapter = tellingenAdapter

        loadTellingen()
    }

    private fun loadTellingen() {
        lifecycleScope.launch {
            val tellingen = withContext(Dispatchers.IO) { database.tellingDao().getAllHeaders() }
            tellingenAdapter.submitList(tellingen)
        }
    }

    private fun updateDeleteButtonState() {
        val selectedCount = tellingenAdapter.getSelectedTellingIds().size
        btnDeleteSelected.isEnabled = selectedCount > 0
        btnDeleteSelected.text = if (selectedCount > 0) {
            getString(R.string.db_telling_delete_selected_with_count, selectedCount)
        } else {
            getString(R.string.db_telling_delete_selected)
        }
    }

    private fun showDeleteSelectedDialog(selected: List<String>) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.db_telling_delete_confirm_title))
            .setMessage(getString(R.string.db_telling_delete_confirm_msg, selected.size))
            .setPositiveButton(getString(R.string.db_telling_delete_selected)) { _, _ ->
                deleteSelectedTellingen(selected)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteSelectedTellingen(selected: List<String>) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    database.withTransaction {
                        val dao = database.tellingDao()
                        selected.forEach { tellingId ->
                            dao.deleteWaarnemingenVoorTellingById(tellingId)
                            dao.deleteHeaderVoorTellingById(tellingId)
                        }
                    }
                }
                tellingenAdapter.clearSelection()
                android.widget.Toast.makeText(
                    this@DatabaseTellingLijstActiviteit,
                    getString(R.string.db_telling_delete_done),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                loadTellingen()
            } catch (e: Exception) {
                AlertDialog.Builder(this@DatabaseTellingLijstActiviteit)
                    .setTitle(getString(R.string.db_telling_delete_confirm_title))
                    .setMessage(getString(R.string.db_beheer_actie_fout, e.message ?: ""))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    inner class TellingAdapter(
        private val onClick: (TellingHeader) -> Unit,
        private val onSelectionChanged: () -> Unit
    ) : RecyclerView.Adapter<TellingAdapter.ViewHolder>() {

        private var items: List<TellingHeader> = emptyList()
        private val selectedTellingIds = mutableSetOf<String>()

        fun submitList(newItems: List<TellingHeader>) {
            items = newItems
            selectedTellingIds.retainAll(items.map { it.tellingid }.toSet())
            notifyDataSetChanged()
            onSelectionChanged()
        }

        fun getSelectedTellingIds(): List<String> = selectedTellingIds.toList()

        fun clearSelection() {
            selectedTellingIds.clear()
            notifyDataSetChanged()
            onSelectionChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cbSelect: CheckBox = view.findViewById(R.id.cbSelect)
            val tvTellingId: TextView = view.findViewById(R.id.tvTellingId)
            val tvOnlineId: TextView = view.findViewById(R.id.tvOnlineId)
            val tvTijd: TextView = view.findViewById(R.id.tvTijd)
            val tvStats: TextView = view.findViewById(R.id.tvStats)
            val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_db_telling_sessie, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.cbSelect.setOnCheckedChangeListener(null)
            holder.cbSelect.isChecked = selectedTellingIds.contains(item.tellingid)
            holder.cbSelect.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedTellingIds.add(item.tellingid)
                } else {
                    selectedTellingIds.remove(item.tellingid)
                }
                onSelectionChanged()
            }

            holder.tvTellingId.text = item.tellingid
            holder.tvOnlineId.text = holder.itemView.context.getString(
                R.string.db_telling_online_id,
                item.onlineid.ifBlank { holder.itemView.context.getString(R.string.db_telling_online_id_na) }
            )

            val start = SpeciesNameResolver.formatTimestamp(item.begintijd)
            val eind = if (item.eindtijd.isNotBlank()) SpeciesNameResolver.formatTimestamp(item.eindtijd) else "..."
            holder.tvTijd.text = holder.itemView.context.getString(R.string.db_telling_tijd_range, start, eind)

            holder.tvStats.text = holder.itemView.context.getString(
                R.string.db_telling_stats,
                item.nrec.toIntOrNull() ?: 0,
                item.nsoort.toIntOrNull() ?: 0
            )
            holder.tvStatus.text = item.status.uppercase()

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
