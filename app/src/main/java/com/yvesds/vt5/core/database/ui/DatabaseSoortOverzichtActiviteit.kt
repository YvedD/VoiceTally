package com.yvesds.vt5.core.database.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.entities.Waarneming
import kotlinx.coroutines.launch

class DatabaseSoortOverzichtActiviteit : AppCompatActivity() {

    private lateinit var database: VoiceTallyDatabase
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvSoortInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_db_soort_overzicht)

        database = VoiceTallyDatabase.getDatabase(this)
        recyclerView = findViewById(R.id.rvWaarnemingen)
        recyclerView.layoutManager = LinearLayoutManager(this)
        tvSoortInfo = findViewById(R.id.tvSoortInfo)

        findViewById<MaterialButton>(R.id.btnTerug).setOnClickListener { finish() }

        setupSearch()
    }

    private fun setupSearch() {
        val atv = findViewById<AutoCompleteTextView>(R.id.atvSoortZoeken)
        
        lifecycleScope.launch {
            val allSpecies = SpeciesNameResolver.getAllSpecies(this@DatabaseSoortOverzichtActiviteit)
            val speciesNames = allSpecies.map { it.soortnaam }
            val adapter = ArrayAdapter(this@DatabaseSoortOverzichtActiviteit, android.R.layout.simple_dropdown_item_1line, speciesNames)
            atv.setAdapter(adapter)

            atv.setOnItemClickListener { _, _, position, _ ->
                val selectedName = adapter.getItem(position)
                val selectedId = allSpecies.find { it.soortnaam == selectedName }?.soortid
                if (selectedId != null) {
                    loadWaarnemingen(selectedId, selectedName ?: "")
                }
            }
        }
    }

    private fun loadWaarnemingen(soortId: String, soortNaam: String) {
        lifecycleScope.launch {
            val items = database.tellingDao().getWaarnemingenBySoort(soortId)
            val totaalEx = items.sumOf { it.aantal.toIntOrNull() ?: 0 }
            
            tvSoortInfo.text = "Totaal: $totaalEx exemplaren in ${items.size} waarnemingen."
            recyclerView.adapter = WaarnemingAdapter(items, soortNaam)
        }
    }

    inner class WaarnemingAdapter(
        private val items: List<Waarneming>,
        private val soortNaam: String
    ) : RecyclerView.Adapter<WaarnemingAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitel: TextView = view.findViewById(android.R.id.text1)
            val tvSubtitel: TextView = view.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvTitel.text = "$soortNaam (${item.aantal} ex)"
            holder.tvTitel.setTextColor(resources.getColor(R.color.vt5_on_surface))
            holder.tvSubtitel.text = "Telling: ${item.tellingid} | Tijd: ${item.tijdstip} | Opmerking: ${item.opmerkingen}"
            holder.tvSubtitel.setTextColor(resources.getColor(R.color.vt5_on_surface))
            holder.tvSubtitel.alpha = 0.7f
        }

        override fun getItemCount() = items.size
    }
}
