package com.yvesds.vt5.ai

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.ui.SpeciesNameResolver
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * KrentenMonitorActiviteit - Professioneel dashboard voor het beheren van 'Birds of Interest'.
 * Met afbeeldingen, gilde-kleurcodering en statistieken.
 */
class KrentenMonitorActiviteit : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var adapter: KrentenAdapter
    private val items = mutableListOf<KrentItem>()
    private lateinit var modelStore: ModelStore
    private var currentKb: ExpertKnowledgeBase? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_krenten_monitor)

        modelStore = ModelStore(this)
        rv = findViewById(R.id.rvKrenten)
        
        // Tablet optimalisatie: 2 kolommen voor meer overzicht
        val isTablet = resources.configuration.smallestScreenWidthDp >= 600
        rv.layoutManager = if (isTablet) GridLayoutManager(this, 2) else LinearLayoutManager(this)
        
        adapter = KrentenAdapter(items)
        rv.adapter = adapter

        findViewById<View>(R.id.btnTerug).setOnClickListener { finish() }
        findViewById<View>(R.id.btnSave).setOnClickListener { saveAndExit() }

        loadKrenten()
    }

    private fun loadKrenten() {
        lifecycleScope.launch {
            val kb = withContext(Dispatchers.IO) { modelStore.loadExpertKnowledge() }
            if (kb == null) {
                Toast.makeText(this@KrentenMonitorActiviteit, "Geen AI-voorspellingen gevonden.", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            currentKb = kb
            val krentenIds = kb.discoveredKrenten
            
            val db = VoiceTallyDatabase.getDatabase(this@KrentenMonitorActiviteit)
            val snapshot = withContext(Dispatchers.IO) { try { ServerDataCache.getOrLoad(this@KrentenMonitorActiviteit) } catch(_: Exception) { null } }
            
            // Haal de gedetailleerde massa-statistieken op (observations + total quantity)
            val stats = withContext(Dispatchers.IO) { db.tellingDao().getSpeciesMassaList(krentenIds) }.associateBy { it.soortid }

            val loadedItems = withContext(Dispatchers.IO) {
                krentenIds.map { id ->
                    val speciesData = snapshot?.speciesById?.get(id)
                    val guild = SpeciesGuildMapper.getGuildByLatin(speciesData?.latin)
                    
                    KrentItem(
                        id = id,
                        name = SpeciesNameResolver.getName(this@KrentenMonitorActiviteit, id),
                        latin = speciesData?.latin,
                        guildName = guild.displayName,
                        guildColor = getGuildColor(guild.displayName),
                        obsCount = stats[id]?.observationCount ?: 0,
                        totalQty = stats[id]?.totalQuantity ?: 0L,
                        isSelected = true 
                    )
                }.sortedBy { it.name }
            }

            items.clear()
            items.addAll(loadedItems)
            adapter.notifyDataSetChanged()
        }
    }

    private fun getGuildColor(guildName: String): Int {
        return when {
            guildName.contains("Zang") -> Color.CYAN
            guildName.contains("Roof") -> Color.YELLOW
            guildName.contains("Reiger") -> Color.GREEN
            guildName.contains("Zee") -> Color.MAGENTA
            guildName.contains("Stelt") -> Color.parseColor("#FF9800")
            guildName.contains("Water") -> Color.parseColor("#4FC3F7")
            guildName.contains("Kust") -> Color.parseColor("#009688")
            else -> Color.parseColor("#333333")
        }
    }

    private fun saveAndExit() {
        val filteredIds = items.filter { it.isSelected }.map { it.id }
        val updatedKb = currentKb?.copy(discoveredKrenten = filteredIds)
        if (updatedKb != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                modelStore.saveExpertKnowledge(updatedKb)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@KrentenMonitorActiviteit, "Configuratie opgeslagen", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private data class KrentItem(
        val id: String, 
        val name: String, 
        val latin: String?,
        val guildName: String,
        val guildColor: Int,
        val obsCount: Int,
        val totalQty: Long,
        var isSelected: Boolean
    )

    private inner class KrentenAdapter(private val items: List<KrentItem>) : RecyclerView.Adapter<KrentenAdapter.ViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_krent_monitor, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvName.text = item.name
            holder.tvGuild.text = item.guildName
            holder.tvStats.text = "${item.obsCount} waarnemingen | ${item.totalQty} ex."
            
            // Card styling met gilde kleur
            holder.card.strokeColor = item.guildColor
            
            holder.cb.setOnCheckedChangeListener(null)
            holder.cb.isChecked = item.isSelected
            holder.cb.setOnCheckedChangeListener { _, isChecked -> item.isSelected = isChecked }
            
            holder.itemView.setOnClickListener { holder.cb.toggle() }

            // Foto laden
            holder.ivSpecies.setImageResource(android.R.drawable.ic_menu_gallery)
            holder.ivSpecies.tag = item.latin
            if (!item.latin.isNullOrBlank()) {
                val latin = item.latin
                android.util.Log.d("KrentenMonitor", "Loading image for ${item.name} ($latin)")
                lifecycleScope.launch {
                    val bitmap = withContext(Dispatchers.IO) { SpeciesImageHelper.getThumbnail(latin) }
                    // Dubbel-check tag om recycling fouten te voorkomen
                    if (bitmap != null && holder.ivSpecies.tag == latin) {
                        holder.ivSpecies.setImageBitmap(bitmap)
                    } else if (bitmap == null && holder.ivSpecies.tag == latin) {
                        holder.ivSpecies.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                }
            } else {
                holder.ivSpecies.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val card: MaterialCardView = v.findViewById(R.id.cardKrent)
            val ivSpecies: ImageView = v.findViewById(R.id.ivSpecies)
            val tvGuild: TextView = v.findViewById(R.id.tvGuildName)
            val tvName: TextView = v.findViewById(R.id.tvSpeciesName)
            val tvStats: TextView = v.findViewById(R.id.tvSpeciesStats)
            val cb: CheckBox = v.findViewById(R.id.cbKrentSelected)
        }
    }
}
