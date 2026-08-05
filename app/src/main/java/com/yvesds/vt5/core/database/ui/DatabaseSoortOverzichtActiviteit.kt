package com.yvesds.vt5.core.database.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.patrykandpatrick.vico.core.cartesian.CartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.views.cartesian.CartesianChartView
import com.patrykandpatrick.vico.views.cartesian.ScrollHandler
import com.patrykandpatrick.vico.views.cartesian.ZoomHandler
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.dao.SpeciesWindDatasetRow
import com.yvesds.vt5.core.database.entities.Waarneming
import com.yvesds.vt5.core.opslag.FileLogger
import com.yvesds.vt5.core.ui.ChartExportHelper
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.util.*

/**
 * DatabaseSoortOverzichtActiviteit - Toont waarnemingen per soort met wind-grafieken.
 * Laag-volgorde: Wind (achter), Retour (midden), Trek (voor).
 */
class DatabaseSoortOverzichtActiviteit : AppCompatActivity() {

    private lateinit var database: VoiceTallyDatabase
    private lateinit var fileLogger: FileLogger
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvSoortInfo: TextView
    private lateinit var layoutGrafieken: LinearLayout
    private lateinit var tabLayout: TabLayout
    private lateinit var gridWindCharts: GridLayout
    private lateinit var spinnerSiteFilter: Spinner
    private lateinit var spinnerYears: Spinner
    private lateinit var atvSoortZoeken: AutoCompleteTextView

    private var selectedSoortId: String? = null
    private var adapter = SimpleWaarnemingAdapter()
    private val chartBindings = linkedMapOf<String, WindChartBinding>()
    private val windDirections = listOf("N", "NNO", "NO", "ONO", "O", "OZO", "ZO", "ZZO", "Z", "ZZW", "ZW", "WZW", "W", "WNW", "NW", "NNW")

    // --- HIER KUN JE DE KLEUREN TWEAKEN IN res/values/colors.xml ---
    private val colorAantal by lazy { ContextCompat.getColor(this, R.color.grafiek_lijnkleur) }
    private val colorTerug by lazy { ContextCompat.getColor(this, R.color.grafiek_lijnkleur_terug) }
    private val colorWind by lazy { ContextCompat.getColor(this, R.color.grafiek_beaufort) }
    // ---------------------------------------------------------------

    private var availableSiteIds = mutableListOf<String?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_db_soort_overzicht)

        database = VoiceTallyDatabase.getDatabase(this)
        fileLogger = FileLogger(this)
        
        recyclerView = findViewById(R.id.rvWaarnemingen)
        tvSoortInfo = findViewById(R.id.tvSoortInfo)
        layoutGrafieken = findViewById(R.id.layoutGrafieken)
        tabLayout = findViewById(R.id.tabSoortOverzicht)
        gridWindCharts = findViewById(R.id.gridWindCharts)
        spinnerSiteFilter = findViewById(R.id.spinnerSiteFilter)
        spinnerYears = findViewById(R.id.spinnerYears)
        atvSoortZoeken = findViewById(R.id.atvSoortZoeken)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        setupSiteFilter()
        setupSearch()
        setupYearSelector()

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 1) {
                    layoutGrafieken.visibility = View.VISIBLE
                    gridWindCharts.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    prepareAndShowCharts()
                } else {
                    layoutGrafieken.visibility = View.GONE
                    gridWindCharts.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    loadWaarnemingen()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        findViewById<View>(R.id.btnTerug).setOnClickListener { finish() }
    }

    private fun setupSiteFilter() {
        lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.IO) { ServerDataCache.getOrLoad(this@DatabaseSoortOverzichtActiviteit) }
            val siteList = mutableListOf("Alle Telposten")
            availableSiteIds.clear()
            availableSiteIds.add(null)
            snapshot?.sitesById?.values?.sortedBy { it.telpostnaam }?.forEach {
                siteList.add("${it.telpostnaam} (${it.telpostid})")
                availableSiteIds.add(it.telpostid)
            }
            val spinnerAdapter = ArrayAdapter(this@DatabaseSoortOverzichtActiviteit, android.R.layout.simple_spinner_item, siteList)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerSiteFilter.adapter = spinnerAdapter
            spinnerSiteFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) { refreshCurrentTab() }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }
    }

    private fun setupSearch() {
        lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.IO) { ServerDataCache.getOrLoad(this@DatabaseSoortOverzichtActiviteit) }
            val speciesList = snapshot?.speciesById?.values?.sortedBy { it.soortnaam } ?: emptyList()
            val names = speciesList.map { it.soortnaam }
            
            val searchAdapter = ArrayAdapter(this@DatabaseSoortOverzichtActiviteit, android.R.layout.simple_dropdown_item_1line, names)
            atvSoortZoeken.setAdapter(searchAdapter)
            atvSoortZoeken.setOnItemClickListener { _, _, _, _ ->
                val selectedName = atvSoortZoeken.text.toString()
                val species = speciesList.find { it.soortnaam == selectedName }
                if (species != null) {
                    selectedSoortId = species.soortid
                    tvSoortInfo.text = "Soort: $selectedName (ID: ${species.soortid})"
                    refreshCurrentTab()
                }
            }
        }
    }

    private fun setupYearSelector() {
        lifecycleScope.launch {
            val years = withContext(Dispatchers.IO) { database.tellingDao().getAvailableYears() }
            val yearList = mutableListOf("Alle jaren")
            yearList.addAll(years.filterNotNull())
            val yearAdapter = ArrayAdapter(this@DatabaseSoortOverzichtActiviteit, android.R.layout.simple_spinner_item, yearList)
            yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerYears.adapter = yearAdapter
            spinnerYears.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) { refreshCurrentTab() }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }
    }

    private fun refreshCurrentTab() {
        if (tabLayout.selectedTabPosition == 1) prepareAndShowCharts()
        else loadWaarnemingen()
    }

    private fun loadWaarnemingen() {
        val soortId = selectedSoortId ?: return
        val year = if (spinnerYears.selectedItemPosition > 0) spinnerYears.selectedItem.toString() else null
        val siteIdx = spinnerSiteFilter.selectedItemPosition
        val siteId = if (siteIdx > 0) availableSiteIds[siteIdx] else null

        lifecycleScope.launch(Dispatchers.IO) {
            val raw = database.tellingDao().getWaarnemingenBySoortAndYear(soortId, year)
            val filtered = if (siteId != null) {
                raw.filter { database.tellingDao().getHeader(it.tellingid)?.telpostid == siteId }
            } else raw
            withContext(Dispatchers.Main) { adapter.submitList(filtered) }
        }
    }

    private fun prepareAndShowCharts() {
        val soortId = selectedSoortId ?: return
        val siteIdx = spinnerSiteFilter.selectedItemPosition
        val filterSiteId = if (siteIdx > 0) availableSiteIds[siteIdx] else null
        val filterYear = if (spinnerYears.selectedItemPosition > 0) spinnerYears.selectedItem.toString() else null

        lifecycleScope.launch(Dispatchers.IO) {
            val rawData = database.tellingDao().getWindDatasetForSpecies(soortId)
            
            withContext(Dispatchers.Main) {
                ensureWindChartsCreated()
                
                windDirections.forEach { dir ->
                    val binding = chartBindings[dir] ?: return@forEach
                    
                    val filtered = rawData.filter { 
                        it.windrichting.trim().uppercase() == dir && 
                        (filterSiteId == null || it.telpostid == filterSiteId) &&
                        (filterYear == null || getYearFromEpoch(it.begintijd) == filterYear)
                    }

                    val weekAantal = FloatArray(54)
                    val weekTerug = FloatArray(54)
                    val weekWindSum = FloatArray(54)
                    val weekWindCount = IntArray(54)
                    var sumAantal = 0
                    var sumTerug = 0

                    filtered.forEach { row ->
                        val week = getWeekIndex(row.begintijd)
                        if (week in 1..53) {
                            weekAantal[week] += row.aantal.toFloat()
                            weekTerug[week] += row.aantalterug.toFloat()
                            sumAantal += row.aantal
                            sumTerug += row.aantalterug
                            val bft = row.windkracht.split(".")[0].toFloatOrNull() ?: 0f
                            weekWindSum[week] += bft
                            weekWindCount[week]++
                        }
                    }

                    // Update header met totalen: Richting (T: Trek, R: Retour)
                    binding.headerView.text = String.format(Locale.getDefault(), "%s (Trek: %d, Terug: %d)", dir, sumAantal, sumTerug)

                    val weekWindAvg = FloatArray(54) { i ->
                        if (weekWindCount[i] > 0) weekWindSum[i] / weekWindCount[i] else 0f
                    }

                    updateChart(binding, weekAantal, weekTerug, weekWindAvg)
                }
            }
        }
    }

    private fun getYearFromEpoch(epochStr: String): String {
        val epoch = epochStr.toLongOrNull() ?: return ""
        val instant = if (epoch > 9999999999L) Instant.ofEpochMilli(epoch) else Instant.ofEpochSecond(epoch)
        return instant.atZone(ZoneId.systemDefault()).toLocalDate().year.toString()
    }

    private fun ensureWindChartsCreated() {
        if (chartBindings.isNotEmpty()) return
        gridWindCharts.removeAllViews()
        windDirections.forEach { dir ->
            val view = LayoutInflater.from(this).inflate(R.layout.item_wind_chart_card, gridWindCharts, false)
            val binding = WindChartBinding(
                view.findViewById(R.id.tvDirectionHeader),
                view.findViewById(R.id.chartDirection),
                CartesianChartModelProducer(),
                view.findViewById(R.id.cbShowReturn),
                view.findViewById(R.id.cbShowTrek)
            )
            binding.headerView.text = dir
            setupSingleChart(binding.chartView, binding.producer)
            
            view.setOnLongClickListener {
                ChartExportHelper.exportViewToImage(binding.chartView, "WindChart_${selectedSoortId}_$dir")
                true
            }

            val listener = { _: View? -> prepareAndShowCharts() }
            binding.cbShowTrek.setOnClickListener(listener)
            binding.cbShowReturn.setOnClickListener(listener)
            
            chartBindings[dir] = binding
            gridWindCharts.addView(view)
        }
    }

    private fun setupSingleChart(chartView: CartesianChartView, producer: CartesianChartModelProducer) {
        chartView.modelProducer = producer
        chartView.setBackgroundColor(Color.parseColor("#739B9B"))
        
        chartView.zoomHandler = ZoomHandler(zoomEnabled = false, initialZoom = Zoom.Content)
        chartView.scrollHandler = ScrollHandler(scrollEnabled = false)

        chartView.chart = CartesianChart(
            layers = arrayOf(
                // 1. Windkracht (Achtergrondlaag) - Max 14.0 Beaufort
                VicoLineChartProducer.createBeaufortLayer(colorWind),
                // 2. Vogel-aantallen (Voorgrondlaag)
                LineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.Line(fill = LineCartesianLayer.LineFill.single(Fill(colorTerug))), // Midden
                        LineCartesianLayer.Line(fill = LineCartesianLayer.LineFill.single(Fill(colorAantal)))  // Helemaal voor
                    ),
                    verticalAxisPosition = Axis.Position.Vertical.Start
                )
            ),
            startAxis = VerticalAxis.start(label = VicoLineChartHelper.whiteAxisLabel),
            endAxis = VerticalAxis.end(
                label = VicoLineChartHelper.whiteAxisLabel,
                itemPlacer = VerticalAxis.ItemPlacer.count(count = { 8 })
            ),
            bottomAxis = VicoLineChartHelper.createMonthLabelAxis()
        )
    }

    private fun updateChart(binding: WindChartBinding, aantal: FloatArray, terug: FloatArray, wind: FloatArray) {
        val seriesAantal = if (binding.cbShowTrek.isChecked) aantal.toList() else List(54) { 0f }
        val seriesTerug = if (binding.cbShowReturn.isChecked) terug.toList() else List(54) { 0f }
        val seriesWind = wind.toList()
        
        lifecycleScope.launch {
            binding.producer.runTransaction {
                // Layer 0: Wind
                lineSeries { series(seriesWind) }
                // Layer 1: Terug & Aantal (volgorde bepaalt stapeling binnen laag)
                lineSeries {
                    series(seriesTerug)
                    series(seriesAantal)
                }
            }
        }
    }

    private fun getWeekIndex(begintijd: String): Int {
        val epoch = begintijd.toLongOrNull() ?: return 0
        val instant = if (epoch > 9999999999L) Instant.ofEpochMilli(epoch) else Instant.ofEpochSecond(epoch)
        val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        return ((date.dayOfYear - 1) / 7) + 1
    }

    private class SimpleWaarnemingAdapter : RecyclerView.Adapter<SimpleWaarnemingAdapter.VH>() {
        private var items = listOf<Waarneming>()
        fun submitList(newItems: List<Waarneming>) { items = newItems; notifyDataSetChanged() }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.t1.text = "Aantal: ${item.aantal} | Terug: ${item.aantalterug}"
            holder.t1.setTextColor(Color.WHITE)
            holder.t2.text = "Telling: ${item.tellingid} | ID: ${item.onlineid}"
            holder.t2.setTextColor(Color.LTGRAY)
        }
        override fun getItemCount() = items.size
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val t1 = v.findViewById<TextView>(android.R.id.text1)
            val t2 = v.findViewById<TextView>(android.R.id.text2)
        }
    }

    private data class WindChartBinding(
        val headerView: TextView,
        val chartView: CartesianChartView,
        val producer: CartesianChartModelProducer,
        val cbShowReturn: CheckBox,
        val cbShowTrek: CheckBox
    )
}

/** Helper om Beaufort laag op de achtergrond te plaatsen zonder de as-schaling van de vogel-aantallen te verstoren */
object VicoLineChartProducer {
    fun createBeaufortLayer(color: Int): LineCartesianLayer {
        return LineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.Line(fill = LineCartesianLayer.LineFill.single(Fill(color)))
            ),
            rangeProvider = com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider.fixed(0.0, 14.0),
            verticalAxisPosition = Axis.Position.Vertical.End
        )
    }
}
