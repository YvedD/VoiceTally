package com.yvesds.vt5.core.database.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.Spinner
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.patrykandpatrick.vico.core.cartesian.CartesianChart
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.views.cartesian.CartesianChartView
import com.patrykandpatrick.vico.views.cartesian.ScrollHandler
import com.patrykandpatrick.vico.views.cartesian.ZoomHandler
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.dao.SpeciesWindDatasetRow
import com.yvesds.vt5.core.database.entities.Waarneming
import com.yvesds.vt5.core.opslag.FileLogger
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.appcompat.app.AlertDialog
import android.widget.ProgressBar
import android.widget.LinearLayout.LayoutParams
import android.view.Gravity
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

class DatabaseSoortOverzichtActiviteit : AppCompatActivity() {

    private lateinit var database: VoiceTallyDatabase
    private lateinit var fileLogger: FileLogger
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvSoortInfo: TextView
    private lateinit var contentOverview: LinearLayout
    private lateinit var layoutGrafieken: LinearLayout
    private lateinit var tabLayout: TabLayout
    private lateinit var gridWindCharts: GridLayout
    private lateinit var spinnerYears: Spinner
    private lateinit var pbOverviewContainer: View

    private var currentWaarnemingen: List<Waarneming> = emptyList()
    private var currentWindDataset: List<SpeciesWindDatasetRow> = emptyList()
    // Job used to cancel any in-flight load when a new species search starts
    private var loadJob: Job? = null
    private lateinit var waarnemingAdapter: WaarnemingAdapter
    private val WAARNEMING_DIFF = object : DiffUtil.ItemCallback<Waarneming>() {
        override fun areItemsTheSame(oldItem: Waarneming, newItem: Waarneming): Boolean {
            return oldItem.idLocal == newItem.idLocal && oldItem.tellingid == newItem.tellingid
        }

        override fun areContentsTheSame(oldItem: Waarneming, newItem: Waarneming): Boolean {
            return oldItem == newItem
        }
    }

    private val windDirections = listOf(
        "N", "NNO", "NO", "ONO", "O", "OZO", "ZO", "ZZO",
        "Z", "ZZW", "ZW", "WZW", "W", "WNW", "NW", "NNW",
    )

    private val chartBindings = linkedMapOf<String, WindChartBinding>()

    private var loadingDialog: AlertDialog? = null

    private val chartLineColor by lazy { ContextCompat.getColor(this, R.color.grafiek_lijnkleur) }
    private val chartBgColor by lazy { ContextCompat.getColor(this, R.color.grafiek_achtergrondkleur) }
    private val beaufortLineColor by lazy { ContextCompat.getColor(this, R.color.grafiek_beaufort) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_db_soort_overzicht)

        database = VoiceTallyDatabase.getDatabase(this)
        fileLogger = FileLogger(this)
        recyclerView = findViewById(R.id.rvWaarnemingen)
        recyclerView.layoutManager = LinearLayoutManager(this)
        waarnemingAdapter = WaarnemingAdapter()
        recyclerView.adapter = waarnemingAdapter
        tvSoortInfo = findViewById(R.id.tvSoortInfo)
        spinnerYears = findViewById(R.id.spinnerYears)
        pbOverviewContainer = findViewById(R.id.pbOverviewContainer)
        contentOverview = findViewById(R.id.contentOverview)
        layoutGrafieken = findViewById(R.id.layoutGrafieken)
        tabLayout = findViewById(R.id.tabSoortOverzicht)
        gridWindCharts = findViewById(R.id.gridWindCharts)

        findViewById<MaterialButton>(R.id.btnTerug).setOnClickListener { finish() }

        lifecycleScope.launch {
            val saf = SaFStorageHelper(this@DatabaseSoortOverzichtActiviteit)
            val dir = withContext(Dispatchers.IO) { saf.getVt5DirIfExists() }
            if (dir == null) {
                Log.e("DatabaseSoortOverzicht", "SAF VT5 directory not found! Logs might be in internal storage.")
            } else {
                Log.d("DatabaseSoortOverzicht", "SAF VT5 directory found: ${dir.uri}")
            }
            fileLogger.info("SCHERM: Soortoverzicht geopend.")
        }

        ensureWindChartsCreated()

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                showTab(tab?.position == 1)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit

            override fun onTabReselected(tab: TabLayout.Tab?) {
                if (tab?.position == 1) prepareAndShowCharts()
            }
        })

        tabLayout.getTabAt(0)?.select()

        setupSearch()
        setupYearSelector()
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
                val selectedSpecies = allSpecies.find { it.soortnaam == selectedName }
                val selectedId = selectedSpecies?.soortid
                atv.tag = selectedId
                if (selectedId != null) {
                    loadWaarnemingen(selectedId, selectedName ?: "")
                }
            }
        }
    }

    private fun setupYearSelector() {
        lifecycleScope.launch {
            try {
                val years = withContext(Dispatchers.IO) { database.tellingDao().getAvailableYears() }
                val items = mutableListOf("Alle jaren")
                items.addAll(years)
                val adapter = ArrayAdapter(this@DatabaseSoortOverzichtActiviteit, android.R.layout.simple_spinner_item, items)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerYears.adapter = adapter

                spinnerYears.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        // If a species is already selected, reload with the new year selection
                        val speciesId = (findViewById<AutoCompleteTextView>(R.id.atvSoortZoeken).tag as? String)
                        val speciesName = (findViewById<AutoCompleteTextView>(R.id.atvSoortZoeken).text?.toString() ?: "")
                        if (!speciesId.isNullOrBlank()) {
                            loadWaarnemingen(speciesId, speciesName)
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
            } catch (e: Exception) {
                fileLogger.warn("Kon jaren niet ophalen: ${e.message}")
            }
        }
    }

    private fun getSelectedYear(): String? {
        return try {
            val sel = spinnerYears.selectedItem as? String
            if (sel == null || sel == "Alle jaren") null else sel
        } catch (_: Exception) { null }
    }

    private fun loadWaarnemingen(soortId: String, soortNaam: String) {
        // Cancel any previous load to avoid overlapping heavy UI work which could lead to ANR
        loadJob?.cancel()

        // Clear cached datasets quickly so the UI can release resources before loading
        currentWaarnemingen = emptyList()
        currentWindDataset = emptyList()
        try {
            // clear adapter data using ListAdapter submitList (async diff)
            waarnemingAdapter.submitList(emptyList())
        } catch (e: Exception) {
            // ignore
        }

        // show in-layout progress indicator for overview and ensure overview tab active
        pbOverviewContainer.visibility = View.VISIBLE
        tabLayout.getTabAt(0)?.select()

        loadJob = lifecycleScope.launch {
            try {
                val year = getSelectedYear()
                // fetch heavy data off-main
                val items = withContext(Dispatchers.IO) { database.tellingDao().getWaarnemingenBySoortAndYear(soortId, year) }

                // assign cached results (lightweight) on main thread and submit list via adapter
                currentWaarnemingen = items
                val totaalEx = items.sumOf { it.aantal.toIntOrNull() ?: 0 }

                withContext(Dispatchers.Main) {
                    tvSoortInfo.text = "Totaal: $totaalEx exemplaren in ${items.size} waarnemingen."
                    // update adapter's soortNaam so each item shows correct species name
                    waarnemingAdapter.soortNaam = soortNaam
                    waarnemingAdapter.submitList(items)
                    // after the overview has been updated and RecyclerView had a chance to layout,
                    // initialize charts. Posting to the RecyclerView message queue lets the UI
                    // render the overview first which reduces perceived jank.
                    recyclerView.post {
                        try {
                            prepareAndShowCharts()
                        } catch (e: Exception) {
                            lifecycleScope.launch { fileLogger.warn("Fout bij starten grafieken: ${e.message}") }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    // expected when a newer search started; don't log as an error
                    fileLogger.info("Ophalen waarnemingen gecancelled voor soort $soortId")
                } else {
                    fileLogger.warn("Fout bij ophalen waarnemingen: ${e.message}")
                }
            } finally {
                try {
                    pbOverviewContainer.visibility = View.GONE
                } catch (_: Exception) { }
                loadJob = null
            }
        }
    }

    private fun ensureWindChartsCreated() {
        if (chartBindings.isNotEmpty()) return

        val spacing = (8 * resources.displayMetrics.density).toInt()
        windDirections.forEach { direction ->
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_wind_chart_card, gridWindCharts, false)
            val headerView = itemView.findViewById<TextView>(R.id.tvDirectionHeader)
            val chartView = itemView.findViewById<CartesianChartView>(R.id.chartDirection)
            val producer = CartesianChartModelProducer()

            headerView.text = "$direction - Totaal: 0"
            setupSingleChart(chartView, producer)

            val rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            val columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            itemView.layoutParams = GridLayout.LayoutParams(rowSpec, columnSpec).apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                setMargins(spacing, spacing, spacing, spacing)
            }

            gridWindCharts.addView(itemView)
            chartBindings[direction] = WindChartBinding(headerView, chartView, producer)
        }
    }

    private fun showTab(showGraphs: Boolean) {
        contentOverview.visibility = if (showGraphs) View.GONE else View.VISIBLE
        layoutGrafieken.visibility = if (showGraphs) View.VISIBLE else View.GONE
        if (showGraphs) prepareAndShowCharts()
    }

    private fun prepareAndShowCharts() {
        val speciesId = (findViewById<AutoCompleteTextView>(R.id.atvSoortZoeken).tag as? String)
            ?: currentWaarnemingen.firstOrNull()?.soortid
            ?: return

        showLoading("Ophalen grafiekdata...")
        lifecycleScope.launch(Dispatchers.Default) {
            fileLogger.info("GRAFIEK16: Start ophalen data voor soort $speciesId")
            val dataset = withContext(Dispatchers.IO) {
                val year = getSelectedYear()
                database.tellingDao().getWindDatasetForSpecies(speciesId, year)
            }
            currentWindDataset = dataset

            val weeklyTotalsByDirection = windDirections.associateWith { IntArray(52) }.toMutableMap()
            val weeklyBeaufortWeightedSumByDirection = windDirections.associateWith { DoubleArray(52) }.toMutableMap()
            val weeklyBeaufortWeightByDirection = windDirections.associateWith { IntArray(52) }.toMutableMap()

            dataset.forEach { row ->
                val direction = normalizeWindDirection(row.windrichting) ?: return@forEach
                val weekIndex = getWeekIndex(row.begintijd, row.timezoneid)
                if (weekIndex in 0..51) {
                    weeklyTotalsByDirection[direction]?.let { weekArray ->
                        weekArray[weekIndex] += row.aantal
                    }

                    val beaufort = parseBeaufort(row.windkracht)
                    if (beaufort != null) {
                        val weight = row.aantal.coerceAtLeast(1)
                        weeklyBeaufortWeightedSumByDirection[direction]?.let { sums ->
                            sums[weekIndex] += beaufort * weight
                        }
                        weeklyBeaufortWeightByDirection[direction]?.let { weights ->
                            weights[weekIndex] += weight
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                windDirections.forEach { direction ->
                    val binding = chartBindings[direction] ?: return@forEach
                    val countData = weeklyTotalsByDirection[direction] ?: IntArray(52)
                    val beaufortSums = weeklyBeaufortWeightedSumByDirection[direction] ?: DoubleArray(52)
                    val beaufortWeights = weeklyBeaufortWeightByDirection[direction] ?: IntArray(52)
                    val beaufortData = DoubleArray(52) { index ->
                        val weight = beaufortWeights[index]
                        if (weight > 0) beaufortSums[index] / weight else 0.0
                    }

                    binding.headerView.text = "$direction - Totaal: ${countData.sum()}"
                    updateChart(binding.chartView, binding.producer, countData, beaufortData)
                }
                hideLoading()
            }
        }
    }

    private fun showLoading(message: String) {
        try {
            if (loadingDialog?.isShowing == true) return
            val pb = ProgressBar(this).apply { isIndeterminate = true }
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val pad = (16 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                addView(pb, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER
                })
            }

            loadingDialog = AlertDialog.Builder(this)
                .setTitle(message)
                .setView(container)
                .setCancelable(false)
                .create()
            loadingDialog?.show()
        } catch (e: Exception) {
            Log.w("DatabaseSoortOverzicht", "Kon loading dialog niet tonen: ${e.message}")
        }
    }

    private fun hideLoading() {
        try {
            loadingDialog?.dismiss()
            loadingDialog = null
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun setupSingleChart(chartView: CartesianChartView, producer: CartesianChartModelProducer) {
        // Use the generic createLineLayer helper for count data (single color)
        val countLayer = VicoLineChartHelper.createLineLayer(chartLineColor)
        val beaufortLayer = VicoLineChartHelper.createBeaufortLineLayer(maxBeaufort = 7.0, beaufortColor = beaufortLineColor)

        chartView.chart = CartesianChart(
            countLayer,
            beaufortLayer,
            bottomAxis = VicoLineChartHelper.createMonthLabelAxis(),
            topAxis = VicoLineChartHelper.createWeeklyTickAxis(),
            startAxis = VicoLineChartHelper.createCountAxis(),
            endAxis = VicoLineChartHelper.createBeaufortAxis(),
        )
        chartView.setBackgroundColor(chartBgColor)
        chartView.modelProducer = producer
        chartView.scrollHandler = ScrollHandler(true)
        chartView.zoomHandler = ZoomHandler(zoomEnabled = true, initialZoom = Zoom.Content)
    }

    private suspend fun updateChart(
        chartView: CartesianChartView,
        producer: CartesianChartModelProducer,
        countData: IntArray,
        beaufortData: DoubleArray,
    ) {
        // Clamp beaufort values to a maximum of 7 (Beaufort scale limit for our data)
        val maxBeaufort = 7.0
        val clampedBeaufort = beaufortData.map { v -> (v.coerceAtMost(maxBeaufort)).toFloat() }

        producer.runTransaction {
            // Two separate line models: one per layer (count-left axis, beaufort-right axis).
            lineSeries { series(countData.map { it.toFloat() }) }
            lineSeries { series(clampedBeaufort) }
        }

        withContext(Dispatchers.Main) {
            chartView.invalidate()
        }
    }

    private fun normalizeWindDirection(rawValue: String): String? {
        val normalized = rawValue.trim().uppercase(Locale.ROOT)
        return normalized.takeIf { it in windDirections }
    }

    private fun parseBeaufort(rawValue: String): Double? {
        val normalized = rawValue.trim().replace(',', '.')
        if (normalized.isEmpty()) return null
        val numeric = Regex("""-?\d+(?:\.\d+)?""").find(normalized)?.value ?: return null
        return numeric.toDoubleOrNull()
    }

    private fun getWeekIndex(epochValue: String, timezoneId: String): Int {
        val epoch = epochValue.trim().toLongOrNull() ?: return 0
        val epochSeconds = if (epoch > 9_999_999_999L) epoch / 1000 else epoch
        val zone = runCatching {
            ZoneId.of(timezoneId.ifBlank { "Europe/Brussels" })
        }.getOrDefault(ZoneId.of("Europe/Brussels"))

        val localDate = Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate()
        val weekNumber = localDate.get(WeekFields.of(Locale.getDefault()).weekOfYear())
        return (weekNumber - 1).coerceIn(0, 51)
    }

    private data class WindChartBinding(
        val headerView: TextView,
        val chartView: CartesianChartView,
        val producer: CartesianChartModelProducer,
    )

    inner class WaarnemingAdapter : ListAdapter<Waarneming, WaarnemingAdapter.ViewHolder>(WAARNEMING_DIFF) {
        var soortNaam: String = ""

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
            val item = getItem(position)
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

        // Diff handled by WAARNEMING_DIFF declared on the activity
    }
}
