package com.yvesds.vt5.core.database.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.views.cartesian.CartesianChartView
import com.patrykandpatrick.vico.core.cartesian.CartesianChart
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.entities.Waarneming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class DatabaseSoortOverzichtActiviteit : AppCompatActivity() {

    private lateinit var database: VoiceTallyDatabase
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvSoortInfo: TextView
    private lateinit var layoutGrafieken: LinearLayout
    private lateinit var btnToonGrafieken: MaterialButton
    
    private val producerNoord = CartesianChartModelProducer()
    private val producerOost = CartesianChartModelProducer()
    private val producerZuid = CartesianChartModelProducer()
    private val producerWest = CartesianChartModelProducer()

    private var currentWaarnemingen: List<Waarneming> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_db_soort_overzicht)

        database = VoiceTallyDatabase.getDatabase(this)
        recyclerView = findViewById(R.id.rvWaarnemingen)
        recyclerView.layoutManager = LinearLayoutManager(this)
        tvSoortInfo = findViewById(R.id.tvSoortInfo)
        layoutGrafieken = findViewById(R.id.layoutGrafieken)
        btnToonGrafieken = findViewById(R.id.btnToonGrafieken)

        findViewById<MaterialButton>(R.id.btnTerug).setOnClickListener { finish() }

        btnToonGrafieken.setOnClickListener {
            if (layoutGrafieken.visibility == View.VISIBLE) {
                layoutGrafieken.visibility = View.GONE
            } else {
                layoutGrafieken.visibility = View.VISIBLE
                prepareAndShowCharts()
            }
        }

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
            currentWaarnemingen = items
            val totaalEx = items.sumOf { it.aantal.toIntOrNull() ?: 0 }
            
            tvSoortInfo.text = "Totaal: $totaalEx exemplaren in ${items.size} waarnemingen."
            recyclerView.adapter = WaarnemingAdapter(items, soortNaam)
            
            btnToonGrafieken.visibility = if (items.isNotEmpty()) View.VISIBLE else View.GONE
            layoutGrafieken.visibility = View.GONE // Reset bij nieuwe soort
        }
    }

    private val monthLabels = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

    private fun prepareAndShowCharts() {
        lifecycleScope.launch(Dispatchers.Default) {
            val items = currentWaarnemingen
            if (items.isEmpty()) return@launch

            // We hebben ook de headers nodig voor windrichting (die staat in de header, niet per waarneming)
            val tellingIds = items.map { it.tellingid }.distinct()
            val headerMap = withContext(Dispatchers.IO) {
                tellingIds.associateWith { database.tellingDao().getHeader(it) }
            }

            // Maanden 0-11 initialiseren
            val dataNoord = IntArray(12)
            val dataOost = IntArray(12)
            val dataZuid = IntArray(12)
            val dataWest = IntArray(12)

            val cal = Calendar.getInstance()
            
            for (item in items) {
                val header = headerMap[item.tellingid] ?: continue
                val wind = header.windrichting.uppercase().trim()
                val aantal = item.aantal.toIntOrNull() ?: 0
                
                // Tijdstip parsen
                val ts = item.tijdstip.toLongOrNull() ?: 0L
                if (ts == 0L) continue
                cal.timeInMillis = ts * 1000L
                val month = cal.get(Calendar.MONTH) // 0-11

                // Verdeling per windrichting groep
                if (wind in listOf("N", "NW", "NNW", "NO", "NNO")) dataNoord[month] += aantal
                if (wind in listOf("O", "NO", "NNO", "OZO", "ZZO")) dataOost[month] += aantal
                if (wind in listOf("Z", "ZZO", "ZO", "ZW", "ZZW")) dataZuid[month] += aantal
                if (wind in listOf("W", "WZW", "ZW", "NW", "WNW")) dataWest[month] += aantal
            }

            withContext(Dispatchers.Main) {
                updateChart(findViewById(R.id.chartNoord), producerNoord, dataNoord)
                updateChart(findViewById(R.id.chartOost), producerOost, dataOost)
                updateChart(findViewById(R.id.chartZuid), producerZuid, dataZuid)
                updateChart(findViewById(R.id.chartWest), producerWest, dataWest)
            }
        }
    }

    private suspend fun updateChart(chartView: CartesianChartView?, producer: CartesianChartModelProducer, data: IntArray) {
        if (chartView == null) return

        producer.runTransaction {
            columnSeries { series(data.toList()) }
        }
        
        withContext(Dispatchers.Main) {
            chartView.modelProducer = producer
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
