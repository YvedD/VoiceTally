package com.yvesds.vt5.core.database.ui

import android.content.Intent
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
            val tvIndex: TextView = view.findViewById(R.id.tvIndex)
            val tvSoortNaam: TextView = view.findViewById(R.id.tvSoortNaam)
            val tvDetails: TextView = view.findViewById(R.id.tvDetails)
            val tvAantal: TextView = view.findViewById(R.id.tvAantal)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_db_waarneming, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvIndex.text = (position + 1).toString()
            holder.tvSoortNaam.text = soortNaam
            
            val readableTime = SpeciesNameResolver.formatTimestamp(item.tijdstip)
            holder.tvDetails.text = "Sessie: ${item.tellingid} | Tijd: $readableTime"
            holder.tvAantal.text = item.aantal
            
            holder.itemView.setOnClickListener {
                val intent = Intent(this@DatabaseSoortOverzichtActiviteit, DatabaseRecordDetailActiviteit::class.java)
                intent.putExtra("recordid", item.idLocal)
                intent.putExtra("tellingid", item.tellingid)
                startActivity(intent)
            }
        }

        override fun getItemCount() = items.size
    }
}
