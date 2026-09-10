package com.yvesds.vt5.features.telling

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.yvesds.vt5.R
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI Scherm voor het beheren van de Auto-Annotatie instellingen (Krenten-monitor style).
 */
class AutoAnnotatieBeheerScherm : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: SpeciesAdapter

    // The full list of locally known species
    private var allSpecies = listOf<SpeciesItem>()
    // The current selection of species IDs
    private var selectedSpeciesIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auto_annotatie)

        progressBar = findViewById(R.id.progressBar)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = SpeciesAdapter()
        recyclerView.adapter = adapter

        findViewById<MaterialButton>(R.id.btnTerug).setOnClickListener {
            finish()
        }

        findViewById<MaterialButton>(R.id.btnOpslaan).setOnClickListener {
            saveSelection()
        }

        loadData()
    }

    private fun loadData() {
        progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            // Load the current config from the JSON file
            withContext(Dispatchers.IO) {
                AutoAnnotationConfigManager.loadConfig(this@AutoAnnotatieBeheerScherm)
            }
            selectedSpeciesIds.clear()
            selectedSpeciesIds.addAll(AutoAnnotationConfigManager.getAutoAnnotateList())

            // Fetch unique species IDs actually present in the Room Database
            val uniqueLocalSpeciesIds = withContext(Dispatchers.IO) {
                val db = VoiceTallyDatabase.getDatabase(this@AutoAnnotatieBeheerScherm)
                db.tellingDao().getAllUniqueSpeciesIds().toSet()
            }

            // Fetch species metadata from ServerDataCache
            val snapshot = withContext(Dispatchers.IO) {
                ServerDataCache.getOrLoad(this@AutoAnnotatieBeheerScherm)
            }
            
            // Map to items, filter to only include species present in Room, and sort alphabetically
            allSpecies = snapshot.speciesById.values
                .filter { uniqueLocalSpeciesIds.contains(it.soortid) }
                .map { SpeciesItem(it.soortid, if (it.soortnaam.isNotBlank()) it.soortnaam else if (it.latin.isNotBlank()) it.latin else "Onbekend") }
                .sortedBy { it.name }

            adapter.notifyDataSetChanged()
            progressBar.visibility = View.GONE
        }
    }

    private fun saveSelection() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AutoAnnotationConfigManager.saveConfig(this@AutoAnnotatieBeheerScherm, selectedSpeciesIds)
            }
            Toast.makeText(this@AutoAnnotatieBeheerScherm, "Auto-annotatie selectie opgeslagen!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // --- Recycler View Classes ---
    data class SpeciesItem(val id: String, val name: String)

    inner class SpeciesAdapter : RecyclerView.Adapter<SpeciesAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvSpeciesName)
            val cbSelected: MaterialCheckBox = view.findViewById(R.id.cbSelected)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_auto_annotatie_species, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val species = allSpecies[position]
            holder.tvName.text = species.name
            
            // Remove listener before setting checked state to prevent unwanted triggers
            holder.cbSelected.setOnCheckedChangeListener(null)
            holder.cbSelected.isChecked = selectedSpeciesIds.contains(species.id)
            
            // Handle row click
            holder.itemView.setOnClickListener {
                holder.cbSelected.isChecked = !holder.cbSelected.isChecked
            }
            
            // Handle checkbox change
            holder.cbSelected.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedSpeciesIds.add(species.id)
                } else {
                    selectedSpeciesIds.remove(species.id)
                }
            }
        }

        override fun getItemCount(): Int = allSpecies.size
    }
}
