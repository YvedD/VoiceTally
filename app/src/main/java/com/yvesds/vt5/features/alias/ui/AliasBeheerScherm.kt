package com.yvesds.vt5.features.alias.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.yvesds.vt5.R
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.core.ui.DialogStyler
import com.yvesds.vt5.features.alias.AliasManager
import com.yvesds.vt5.features.alias.SpeciesEntry
import com.yvesds.vt5.features.alias.helpers.AliasMasterIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AliasBeheerScherm : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var searchInput: EditText
    private lateinit var adapter: SpeciesAliasAdapter

    private val saf by lazy { SaFStorageHelper(this) }
    private var allSpecies: List<SpeciesEntry> = emptyList()
    private var filteredSpecies: List<SpeciesEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alias_beheer)

        progressBar = findViewById(R.id.progressBar)
        recyclerView = findViewById(R.id.recyclerView)
        searchInput = findViewById(R.id.etSearch)

        adapter = SpeciesAliasAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<MaterialButton>(R.id.btnTerug).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnRefresh).setOnClickListener { loadMaster() }
        findViewById<MaterialButton>(R.id.btnSearch).setOnClickListener {
            applyFilter(searchInput.text?.toString().orEmpty())
        }

        loadMaster()
    }

    private fun loadMaster() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val vt5 = saf.getVt5DirIfExists()
            if (vt5 == null) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@AliasBeheerScherm, getString(R.string.alias_beheer_error_no_saf), Toast.LENGTH_LONG).show()
                return@launch
            }

            val master = withContext(Dispatchers.IO) {
                AliasMasterIO.readMasterFromAssets(this@AliasBeheerScherm, vt5)
                    ?: AliasMasterIO.readMasterFromBinaries(this@AliasBeheerScherm, vt5)
            }

            if (master == null) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@AliasBeheerScherm, getString(R.string.alias_beheer_error_no_master), Toast.LENGTH_LONG).show()
                return@launch
            }

            allSpecies = master.species.sortedBy { it.canonical.lowercase() }
            applyFilter(searchInput.text?.toString().orEmpty())
            progressBar.visibility = View.GONE
        }
    }

    private fun applyFilter(queryRaw: String) {
        val query = queryRaw.trim().lowercase()
        filteredSpecies = if (query.isBlank()) {
            allSpecies
        } else {
            allSpecies.filter { entry ->
                entry.canonical.lowercase().contains(query) ||
                    (entry.tilename?.lowercase()?.contains(query) == true) ||
                    entry.speciesId.contains(query)
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun openEditDialog(entry: SpeciesEntry) {
        val view = layoutInflater.inflate(R.layout.dialog_alias_beheer_edit, null)
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvSeedAliases = view.findViewById<TextView>(R.id.tvSeedAliases)
        val etUserAliases = view.findViewById<EditText>(R.id.etUserAliases)

        val seedAliases = entry.aliases
            .filter { it.source != "user_field_training" }
            .map { it.text.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val userAliases = entry.aliases
            .filter { it.source == "user_field_training" }
            .map { it.text.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        tvTitle.text = getString(R.string.alias_beheer_dialog_title_format, entry.canonical, entry.speciesId)
        tvSeedAliases.text = if (seedAliases.isEmpty()) {
            getString(R.string.alias_beheer_dialog_none)
        } else {
            seedAliases.joinToString(separator = "\n")
        }
        etUserAliases.setText(userAliases.joinToString(separator = "\n"))

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setNegativeButton(R.string.annuleer, null)
            .setPositiveButton(R.string.db_beheer_opslaan, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                val newUserAliases = etUserAliases.text?.toString().orEmpty()
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toList()

                lifecycleScope.launch {
                    progressBar.visibility = View.VISIBLE
                    val result = withContext(Dispatchers.IO) {
                        AliasManager.setUserAliasesForSpecies(
                            context = this@AliasBeheerScherm,
                            saf = saf,
                            speciesId = entry.speciesId,
                            userAliases = newUserAliases
                        )
                    }

                    if (!result.success) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@AliasBeheerScherm, getString(R.string.alias_beheer_save_failed), Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    // Force immediate rebuild to guarantee parser state is aligned with the saved master.
                    withContext(Dispatchers.IO) {
                        AliasManager.forceRebuildCborNow(this@AliasBeheerScherm, saf)
                    }

                    progressBar.visibility = View.GONE
                    dialog.dismiss()
                    if (result.changed) {
                        val skippedCount = result.skippedConflicts.size
                        if (skippedCount > 0) {
                            Toast.makeText(
                                this@AliasBeheerScherm,
                                getString(R.string.alias_beheer_saved_with_conflicts, skippedCount),
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(this@AliasBeheerScherm, getString(R.string.alias_beheer_saved), Toast.LENGTH_SHORT).show()
                        }
                        loadMaster()
                    } else {
                        Toast.makeText(this@AliasBeheerScherm, getString(R.string.alias_beheer_no_changes), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.show()
        DialogStyler.apply(dialog)
    }

    private inner class SpeciesAliasAdapter : RecyclerView.Adapter<SpeciesAliasAdapter.Holder>() {

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvSpeciesName)
            val tvMeta: TextView = view.findViewById(R.id.tvSpeciesMeta)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_alias_species, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val entry = filteredSpecies[position]
            val userCount = entry.aliases.count { it.source == "user_field_training" }
            val totalCount = entry.aliases.size
            holder.tvName.text = entry.canonical
            holder.tvMeta.text = getString(R.string.alias_beheer_species_meta, entry.speciesId, userCount, totalCount)
            holder.itemView.setOnClickListener { openEditDialog(entry) }
        }

        override fun getItemCount(): Int = filteredSpecies.size
    }
}

