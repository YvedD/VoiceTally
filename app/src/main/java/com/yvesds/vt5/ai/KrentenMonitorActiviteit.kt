package com.yvesds.vt5.ai

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import kotlinx.coroutines.*
import java.util.Locale

/**
 * KrentenMonitorActiviteit - Professioneel dashboard voor het beheren van 'Birds of Interest'.
 * Nu met Include/Exclude/Pin logica en zoekfunctionaliteit.
 */
class KrentenMonitorActiviteit : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var adapter: KrentenAdapter
    private val allItems = mutableListOf<KrentItem>()
    private val filteredItems = mutableListOf<KrentItem>()
    private lateinit var modelStore: ModelStore
    private var currentKb: ExpertKnowledgeBase? = null
    
    private lateinit var etZoek: TextInputEditText
    private lateinit var switchShowAll: SwitchMaterial
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_krenten_monitor)

        modelStore = ModelStore(this)
        rv = findViewById(R.id.rvKrenten)
        etZoek = findViewById(R.id.etZoek)
        switchShowAll = findViewById(R.id.switchShowAll)
        
        // Tablet optimalisatie: 2 kolommen voor meer overzicht
        val isTablet = resources.configuration.smallestScreenWidthDp >= 600
        rv.layoutManager = if (isTablet) GridLayoutManager(this, 2) else LinearLayoutManager(this)
        
        adapter = KrentenAdapter(filteredItems)
        rv.adapter = adapter

        findViewById<View>(R.id.btnTerug).setOnClickListener { finish() }
        findViewById<View>(R.id.btnSave).setOnClickListener { saveAndExit() }

        setupListeners()
        loadKrenten()
    }

    private fun setupListeners() {
        etZoek.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300)
                    applyFilters()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        switchShowAll.setOnCheckedChangeListener { _, _ -> applyFilters() }
    }

    private fun loadKrenten() {
        lifecycleScope.launch {
            val kb = withContext(Dispatchers.IO) { modelStore.loadExpertKnowledge() } ?: ExpertKnowledgeBase()
            currentKb = kb
            
            // Indien dit de eerste keer is (geen pinned/excluded), toon de volledige lijst
            if (kb.pinnedSpecies.isEmpty() && kb.excludedSpecies.isEmpty()) {
                switchShowAll.isChecked = true
            }

            val db = VoiceTallyDatabase.getDatabase(this@KrentenMonitorActiviteit)
            val snapshot = withContext(Dispatchers.IO) { try { ServerDataCache.getOrLoad(this@KrentenMonitorActiviteit) } catch(_: Exception) { null } }
            
            val stats = withContext(Dispatchers.IO) { db.tellingDao().getGlobalSpeciesMassa() }.associateBy { it.soortid }
            
            // Filter 1: Enkel soorten die in de database voorkomen OF die handmatig gepind zijn
            val observedOrPinnedIds = stats.keys + kb.pinnedSpecies.toSet()
            val relevantSpecies = snapshot?.speciesById?.values?.filter { it.soortid in observedOrPinnedIds } ?: emptyList()

            val loadedItems = withContext(Dispatchers.IO) {
                relevantSpecies.map { species ->
                    val id = species.soortid
                    val guild = SpeciesGuildMapper.getGuildByLatin(species.latin)
                    val isSuggested = kb.discoveredKrenten.contains(id)
                    val isPinned = kb.pinnedSpecies.contains(id)
                    val isExcluded = kb.excludedSpecies.contains(id)
                    
                    KrentItem(
                        id = id,
                        name = species.soortnaam,
                        latin = species.latin,
                        guildName = guild.displayName,
                        guildColor = getGuildColor(guild.displayName),
                        obsCount = stats[id]?.observationCount ?: 0,
                        totalQty = stats[id]?.totalQuantity ?: 0L,
                        isSuggested = isSuggested,
                        isPinned = isPinned,
                        isExcluded = isExcluded,
                        isSelected = (isSuggested || isPinned) && !isExcluded
                    )
                }.sortedBy { it.name }
            }

            allItems.clear()
            allItems.addAll(loadedItems)
            applyFilters()
        }
    }

    private fun applyFilters() {
        val query = etZoek.text?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val showAll = switchShowAll.isChecked

        filteredItems.clear()
        val results = allItems.filter { item ->
            val matchesSearch = query.isEmpty() || item.name.lowercase(Locale.getDefault()).contains(query) || 
                               item.latin?.lowercase(Locale.getDefault())?.contains(query) == true
            
            // Logica: Toon als 'showAll' aan staat, OF als het een geselecteerde krent is, OF een AI suggestie
            val matchesList = showAll || item.isSelected || item.isSuggested || item.isPinned
            
            matchesSearch && matchesList
        }
        
        filteredItems.addAll(results)
        adapter.notifyDataSetChanged()
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
        val pinned = allItems.filter { it.isPinned }.map { it.id }
        val excluded = allItems.filter { it.isExcluded }.map { it.id }
        
        val updatedKb = currentKb?.copy(
            pinnedSpecies = pinned,
            excludedSpecies = excluded
        )
        
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
        val isSuggested: Boolean,
        var isPinned: Boolean,
        var isExcluded: Boolean,
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
            
            holder.card.strokeColor = item.guildColor
            holder.ivPinned.visibility = if (item.isPinned) View.VISIBLE else View.GONE
            
            holder.cb.setOnCheckedChangeListener(null)
            holder.cb.isChecked = item.isSelected
            
            holder.cb.setOnCheckedChangeListener { _, isChecked -> 
                item.isSelected = isChecked
                if (isChecked) {
                    if (!item.isSuggested) item.isPinned = true
                    item.isExcluded = false
                } else {
                    if (item.isSuggested) item.isExcluded = true
                    item.isPinned = false
                }
                holder.ivPinned.visibility = if (item.isPinned) View.VISIBLE else View.GONE
            }
            
            holder.itemView.setOnClickListener { holder.cb.toggle() }

            holder.ivSpecies.setImageResource(android.R.drawable.ic_menu_gallery)
            holder.ivSpecies.tag = item.latin
            if (!item.latin.isNullOrBlank()) {
                val latin = item.latin
                lifecycleScope.launch {
                    val bitmap = withContext(Dispatchers.IO) { SpeciesImageHelper.getThumbnail(latin) }
                    if (bitmap != null && holder.ivSpecies.tag == latin) {
                        holder.ivSpecies.setImageBitmap(bitmap)
                    }
                }
            }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val card: MaterialCardView = v.findViewById(R.id.cardKrent)
            val ivSpecies: ImageView = v.findViewById(R.id.ivSpecies)
            val ivPinned: ImageView = v.findViewById(R.id.ivPinned)
            val tvGuild: TextView = v.findViewById(R.id.tvGuildName)
            val tvName: TextView = v.findViewById(R.id.tvSpeciesName)
            val tvStats: TextView = v.findViewById(R.id.tvSpeciesStats)
            val cb: CheckBox = v.findViewById(R.id.cbKrentSelected)
        }
    }
}
