package com.yvesds.vt5.core.database.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.entities.TellingHeader
import com.yvesds.vt5.core.database.repository.HybridTellingRepository
import com.yvesds.vt5.core.database.toServerEnvelope
import com.yvesds.vt5.core.import.CsvImportPolicy
import com.yvesds.vt5.features.telling.RoomTellingReopenLauncher
import com.yvesds.vt5.features.telling.TellingStatus
import com.yvesds.vt5.features.telling.TellingUploadCore
import com.yvesds.vt5.features.telling.TellingUploadFlags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class DatabaseTellingLijstActiviteit : AppCompatActivity() {

    companion object {
        private const val TAG = "DatabaseTellingLijst"
    }

    private lateinit var database: VoiceTallyDatabase
    private lateinit var hybridRepository: HybridTellingRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnDeleteSelected: MaterialButton
    private lateinit var btnUploadSelected: MaterialButton
    private lateinit var tellingenAdapter: TellingAdapter
    private lateinit var spinnerSortField: Spinner
    private lateinit var spinnerSiteFilter: Spinner
    private lateinit var toggleSortDirection: ToggleButton

    private var allTellingen: List<TellingHeader> = emptyList()
    private var availableSites: List<String> = emptyList()
    private var selectedSite: String? = null
    private var sortField: SortField = SortField.TELLINGID
    private var sortAsc: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_db_telling_lijst)

        database = VoiceTallyDatabase.getDatabase(this)
        hybridRepository = HybridTellingRepository(this)
        recyclerView = findViewById(R.id.rvTellingen)
        recyclerView.layoutManager = LinearLayoutManager(this)
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected)
        btnUploadSelected = findViewById(R.id.btnUploadSelected)

        findViewById<MaterialButton>(R.id.btnTerug).setOnClickListener { finish() }

        btnDeleteSelected.setOnClickListener {
            val selected = tellingenAdapter.getSelectedTellingIds()
            if (selected.isEmpty()) {
                return@setOnClickListener
            }
            showDeleteSelectedDialog(selected)
        }

        btnUploadSelected.setOnClickListener {
            val selected = tellingenAdapter.getSelectedTellingIds()
            if (selected.isEmpty()) {
                Toast.makeText(this, getString(R.string.db_telling_upload_none), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            uploadSelectedTellingen(selected)
        }

        tellingenAdapter = TellingAdapter(
            onClick = { telling ->
                val intent = Intent(this@DatabaseTellingLijstActiviteit, DatabaseTellingDetailActiviteit::class.java)
                intent.putExtra("tellingid", telling.tellingid)
                startActivity(intent)
            },
            onReopen = { telling ->
                showReopenConfirmDialog(telling)
            },
            onSelectionChanged = { updateDeleteButtonState() }
        )
        recyclerView.adapter = tellingenAdapter

        // sort/filter controls
        spinnerSortField = findViewById(R.id.spinnerSortField)
        spinnerSiteFilter = findViewById(R.id.spinnerSiteFilter)
        toggleSortDirection = findViewById(R.id.toggleSortDirection)

        val choices = resources.getStringArray(R.array.db_telling_sort_fields)
        spinnerSortField.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, choices.toList())
        spinnerSortField.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sortField = when (position) {
                    0 -> SortField.TELLINGID
                    1 -> SortField.ONLINEID
                    else -> SortField.DATUM
                }
                applySortAndFilter()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        toggleSortDirection.setOnCheckedChangeListener { _, isChecked ->
            sortAsc = isChecked
            applySortAndFilter()
        }

        loadSitesAndTellingen()
    }

    private fun loadSitesAndTellingen() {
        lifecycleScope.launch {
            val (tellingen, sites) = withContext(Dispatchers.IO) {
                val dao = database.tellingDao()
                dao.getAllHeaders() to dao.getUniqueTelpostIds()
            }
            allTellingen = tellingen
            availableSites = sites
            
            setupSiteSpinner()
            applySortAndFilter()
        }
    }

    private fun setupSiteSpinner() {
        val siteOptions = mutableListOf(getString(R.string.db_telling_filter_site_all))
        siteOptions.addAll(availableSites)
        
        spinnerSiteFilter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, siteOptions)
        spinnerSiteFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSite = if (position == 0) null else availableSites[position - 1]
                applySortAndFilter()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun applySortAndFilter() {
        var list = allTellingen

        // 1. Filter by site
        if (selectedSite != null) {
            list = list.filter { it.telpostid == selectedSite }
        }

        // 2. Sort numerically for IDs, or by string for date (epoch seconds sort fine as strings too)
        list = when (sortField) {
            SortField.TELLINGID -> list.sortedWith { a, b ->
                val idA = a.tellingid.toLongOrNull() ?: 0L
                val idB = b.tellingid.toLongOrNull() ?: 0L
                idA.compareTo(idB)
            }
            SortField.ONLINEID -> list.sortedWith { a, b ->
                val idA = a.onlineid.toLongOrNull() ?: 0L
                val idB = b.onlineid.toLongOrNull() ?: 0L
                idA.compareTo(idB)
            }
            SortField.DATUM -> list.sortedBy { it.begintijd }
        }

        if (!sortAsc) list = list.reversed()

        tellingenAdapter.submitList(list)
        updateDeleteButtonState()
    }

    private fun isUploadable(header: TellingHeader): Boolean {
        if (CsvImportPolicy.isUploadBlocked(header.status, header.bron)) return false
        val status = TellingStatus.normalize(header.status)
        if (status == TellingStatus.UPLOADING) return false
        return !TellingStatus.isUploadCompleted(status)
    }

    private fun statusLabel(status: String?): String {
        return when (TellingStatus.normalize(status)) {
            TellingStatus.GEUPLOAD -> "GEUPLOAD"
            TellingStatus.EDITED -> "EDITED"
            TellingStatus.UPLOADING -> "UPLOADING"
            TellingStatus.RETRY_NEEDED -> "RETRY"
            TellingStatus.GEARCHIVEERD -> "ARCHIEF"
            else -> "ACTIEF"
        }
    }

    private fun statusColor(status: String?): Int {
        return when (TellingStatus.normalize(status)) {
            TellingStatus.GEUPLOAD -> ContextCompat.getColor(this, R.color.vt5_green)
            TellingStatus.EDITED -> ContextCompat.getColor(this, R.color.vt5_light_blue)
            TellingStatus.UPLOADING -> ContextCompat.getColor(this, R.color.vt5_outline)
            TellingStatus.RETRY_NEEDED -> ContextCompat.getColor(this, R.color.vt5_orange)
            TellingStatus.GEARCHIVEERD -> ContextCompat.getColor(this, R.color.vt5_dark_gray)
            else -> ContextCompat.getColor(this, R.color.vt5_light_gray)
        }
    }

    private enum class SortField { TELLINGID, ONLINEID, DATUM }

    private fun updateDeleteButtonState() {
        val selectedCount = tellingenAdapter.getSelectedTellingIds().size
        val uploadableSelectedCount = tellingenAdapter.getSelectedTellingIds()
            .mapNotNull { selectedId -> allTellingen.firstOrNull { it.tellingid == selectedId } }
            .count { isUploadable(it) }

        btnDeleteSelected.isEnabled = selectedCount > 0
        btnDeleteSelected.text = if (selectedCount > 0) {
            getString(R.string.db_telling_delete_selected_with_count, selectedCount)
        } else {
            getString(R.string.db_telling_delete_selected)
        }

        btnUploadSelected.isEnabled = uploadableSelectedCount > 0
        btnUploadSelected.text = if (uploadableSelectedCount > 0) {
            getString(R.string.db_telling_upload_selected_with_count, uploadableSelectedCount)
        } else {
            getString(R.string.db_telling_upload_selected)
        }
    }

    private fun showReopenConfirmDialog(telling: TellingHeader) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.db_telling_reopen_title))
            .setMessage(getString(R.string.db_telling_reopen_message, telling.tellingid))
            .setPositiveButton(getString(R.string.db_telling_reopen_button)) { _, _ ->
                reopenTellingFromRoom(telling)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun reopenTellingFromRoom(telling: TellingHeader) {
        lifecycleScope.launch {
            try {
                val records = withContext(Dispatchers.IO) {
                    database.tellingDao().getWaarnemingenList(telling.tellingid)
                }
                if (records.isEmpty()) {
                    Toast.makeText(
                        this@DatabaseTellingLijstActiviteit,
                        getString(R.string.db_telling_reopen_no_records),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                RoomTellingReopenLauncher.launch(
                    activity = this@DatabaseTellingLijstActiviteit,
                    header = telling,
                    records = records,
                    finishCurrent = true
                )
            } catch (_: CancellationException) {
                Log.i(TAG, "Heropenen geannuleerd")
            } catch (e: Exception) {
                Toast.makeText(
                    this@DatabaseTellingLijstActiviteit,
                    getString(R.string.beheer_fout, e.message ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun uploadSelectedTellingen(selected: List<String>) {
        lifecycleScope.launch {
            btnUploadSelected.isEnabled = false
            val result = withContext(Dispatchers.IO) {
                val dao = database.tellingDao()
                val uploadCore = TellingUploadCore(this@DatabaseTellingLijstActiviteit)
                var successCount = 0
                var failCount = 0
                var skippedCount = 0
                val errors = mutableListOf<String>()

                selected.forEach { tellingId ->
                    try {
                        val header = dao.getHeader(tellingId)
                        if (header == null) {
                            failCount++
                            errors.add("$tellingId: header ontbreekt")
                            return@forEach
                        }
                        if (!isUploadable(header)) {
                            skippedCount++
                            return@forEach
                        }

                        hybridRepository.updateHeaderStatus(tellingId, TellingStatus.UPLOADING, header.onlineid)

                        val records = dao.getWaarnemingenList(tellingId)
                        val prepared = uploadCore.prepareEnvelopeForUpload(
                            sourceEnvelope = header.toServerEnvelope(records),
                            useStoredOnlineIdWhenBlank = false,
                            now = Date()
                        )

                        val uploadResult = uploadCore.uploadPrepared(
                            TellingUploadCore.UploadRequest(
                                mode = TellingUploadCore.Mode.EDITOR_UPLOAD,
                                preparedEnvelope = prepared,
                                persistReturnedOnlineId = true,
                                persistPreparedEnvelopeToPrefs = false,
                                markTellingSent = false
                            )
                        )

                        if (uploadResult.success) {
                            val effectiveOnlineId = uploadResult.effectiveOnlineId ?: prepared.onlineid
                            hybridRepository.saveHeaderToRoom(
                                uploadResult.preparedEnvelope.copy(onlineid = effectiveOnlineId),
                                status = TellingStatus.GEUPLOAD,
                                force = true
                            )
                            TellingUploadFlags.markSent(this@DatabaseTellingLijstActiviteit, tellingId, effectiveOnlineId)
                            successCount++
                        } else {
                            hybridRepository.updateHeaderStatus(tellingId, TellingStatus.RETRY_NEEDED, header.onlineid)
                            TellingUploadFlags.markNotSent(this@DatabaseTellingLijstActiviteit, tellingId, header.onlineid)
                            failCount++
                            errors.add("$tellingId: ${uploadResult.errorMessage ?: "onbekende uploadfout"}")
                        }
                    } catch (e: Exception) {
                        hybridRepository.updateHeaderStatus(tellingId, TellingStatus.RETRY_NEEDED)
                        TellingUploadFlags.markNotSent(this@DatabaseTellingLijstActiviteit, tellingId, null)
                        failCount++
                        errors.add("$tellingId: ${e.message ?: "onbekende fout"}")
                    }
                }
                UploadSummary(successCount, failCount, skippedCount, errors)
            }

            loadSitesAndTellingen()

            val summaryMessage = buildString {
                append(getString(R.string.db_telling_upload_summary, result.successCount, result.failCount, result.skippedCount))
                if (result.errors.isNotEmpty()) {
                    append("\n\n")
                    append(result.errors.take(5).joinToString("\n"))
                }
            }

            AlertDialog.Builder(this@DatabaseTellingLijstActiviteit)
                .setTitle(getString(R.string.db_telling_upload_result_title))
                .setMessage(summaryMessage)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private data class UploadSummary(
        val successCount: Int,
        val failCount: Int,
        val skippedCount: Int,
        val errors: List<String>
    )

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
                Toast.makeText(
                    this@DatabaseTellingLijstActiviteit,
                    getString(R.string.db_telling_delete_done),
                    Toast.LENGTH_SHORT
                ).show()
                loadSitesAndTellingen()
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
        private val onReopen: (TellingHeader) -> Unit,
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
            val btnReopen: MaterialButton = view.findViewById(R.id.btnReopen)
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
            holder.tvStatus.text = statusLabel(item.status)
            holder.tvStatus.setBackgroundColor(statusColor(item.status))

            holder.btnReopen.setOnClickListener { onReopen(item) }
            holder.btnReopen.isEnabled = item.tellingid.isNotBlank()

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
