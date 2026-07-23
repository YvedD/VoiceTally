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
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
// ...existing imports...
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
import android.widget.CheckBox
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

import com.yvesds.vt5.VT5App
import java.time.format.DateTimeFormatter
import androidx.paging.PagingDataAdapter
// ...existing imports...
import androidx.paging.LoadState
import kotlinx.coroutines.flow.collectLatest
import androidx.paging.PagingData

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
    private lateinit var pagingAdapter: WaarnemingPagingAdapter
    private lateinit var placeholderAdapter: PlaceholderAdapter
    private lateinit var pagingViewModel: DatabaseSoortOverzichtViewModel
    private lateinit var pbOverviewProgressView: ProgressBar
    private var loadStateJob: Job? = null
    private var overviewTotalCount: Int? = null
    private var overviewTotalBlocks: Int = 0
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
    // Cache per-direction aggregated series so toggling visibility doesn't require recomputing the dataset
    private val cachedChartData = mutableMapOf<String, Triple<IntArray, IntArray, DoubleArray>>()

    // allow up to 53 ISO-weeks per year to avoid misplacing weeks in edge cases
    private val WEEKS_PER_YEAR = 53

    private var loadingDialog: AlertDialog? = null

    private val chartLineColor by lazy { ContextCompat.getColor(this, R.color.grafiek_lijnkleur) }
    private val chartReturnLineColor by lazy { ContextCompat.getColor(this, R.color.grafiek_lijnkleur_terug) }
    private val chartBgColor by lazy { ContextCompat.getColor(this, R.color.grafiek_achtergrondkleur) }
    private val beaufortLineColor by lazy { ContextCompat.getColor(this, R.color.grafiek_beaufort) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_db_soort_overzicht)

        database = VoiceTallyDatabase.getDatabase(this)
        fileLogger = FileLogger(this)
        recyclerView = findViewById(R.id.rvWaarnemingen)
        recyclerView.layoutManager = LinearLayoutManager(this)
        // initialize paging adapter and placeholder adapter and viewmodel
        pagingAdapter = WaarnemingPagingAdapter()
        placeholderAdapter = PlaceholderAdapter()
        recyclerView.adapter = ConcatAdapter(pagingAdapter, placeholderAdapter)
        // progress view reference (determinate)
        pbOverviewProgressView = findViewById(R.id.pbOverviewProgress)
        // ViewModel (simple instantiation with DB)
        pagingViewModel = DatabaseSoortOverzichtViewModel(VoiceTallyDatabase.getDatabase(this))
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
                // Retrieve headers and compute available years using the header timezone
                val headers = withContext(Dispatchers.IO) { database.tellingDao().getAllHeaders() }
                val years = headers.mapNotNull { getLocalYearFromEpoch(it.begintijd, it.timezoneid) }
                    .toSortedSet(compareByDescending<String> { it })
                val items = mutableListOf(getString(R.string.spinner_all_years))
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
        loadStateJob?.cancel()

        // Clear cached datasets quickly so the UI can release resources before loading
        currentWaarnemingen = emptyList()
        currentWindDataset = emptyList()
        try {
            // clear paging adapter data
            lifecycleScope.launch(Dispatchers.Main) { pagingAdapter.refresh() }
        } catch (_: Exception) {
            // ignore
        }

        // clear adapter immediately so the previous species' items are not visible
        try {
            lifecycleScope.launch(Dispatchers.Main) { pagingAdapter.submitData(PagingData.empty()) }
        } catch (_: Exception) { }

        // show an initial block of placeholders immediately so the user sees that loading will occur
        try { placeholderAdapter.setCount(PagingConstants.DEFAULT_PAGE_SIZE) } catch (_: Exception) { }

        // show in-layout progress indicator for overview and ensure overview tab active
        pbOverviewContainer.visibility = View.VISIBLE
        tabLayout.getTabAt(0)?.select()

        val year = getSelectedYear()

        // try to get totals (sum) for determinate progress and final info
        lifecycleScope.launch(Dispatchers.IO) {
            val totals = try {
                database.tellingDao().getWaarnemingTotalsForSpecies(soortId, year)
            } catch (e: Exception) {
                fileLogger.warn("Kon totals niet ophalen: ${e.message}")
                null
            }

            withContext(Dispatchers.Main) {
                if (totals != null) {
                    val totalCount = totals.totaal ?: 0
                    overviewTotalCount = totalCount
                    // Use centralized page size constant (keep in sync with ViewModel)
                    val pageSize = PagingConstants.DEFAULT_PAGE_SIZE
                    overviewTotalBlocks = if (totalCount > 0) ((totalCount + pageSize - 1) / pageSize) else 0

                    pbOverviewProgressView.max = if (totalCount > 0) totalCount else 100
                    // show determinate if we have a real total
                    if (totalCount > 0) {
                        findViewById<ProgressBar>(R.id.pbOverviewLoading).visibility = View.GONE
                        pbOverviewProgressView.visibility = View.VISIBLE
                    } else {
                        findViewById<ProgressBar>(R.id.pbOverviewLoading).visibility = View.VISIBLE
                        pbOverviewProgressView.visibility = View.GONE
                    }
                    tvSoortInfo.text = "Totaal verwacht aantal '${totals.totaal ?: 0}', aantal datablokken '${overviewTotalBlocks}'"

                    // Show placeholders for the first upcoming block so the user sees that data will follow.
                    try {
                        val initialPlaceholders = if (totalCount > 0) minOf(totalCount, pageSize) else pageSize
                        placeholderAdapter.setCount(initialPlaceholders)
                    } catch (_: Exception) { }
                } else {
                    findViewById<ProgressBar>(R.id.pbOverviewLoading).visibility = View.VISIBLE
                    pbOverviewProgressView.visibility = View.GONE
                }
            }
        }

        // collect paging flow and submit to adapter
        // Post the start of the heavy paging collection to the UI message queue so the
        // modal loading dialog has a chance to be rendered immediately. Starting the
        // paging collect/submit directly sometimes blocks the main thread rendering
        // before the dialog becomes visible.
        recyclerView.post {
            loadJob = lifecycleScope.launch {
                try {
                    pagingViewModel.getWaarnemingenPager(soortId, getSelectedYear()).collectLatest { pagingData ->
                        pagingAdapter.soortNaam = soortNaam
                        pagingAdapter.submitData(pagingData)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        fileLogger.info("Paging load cancelled for $soortId")
                    } else {
                        fileLogger.warn("Fout bij paged ophalen waarnemingen: ${e.message}")
                    }
                } finally {
                    // nothing here; loadState listener will hide progress when loading finished
                }
            }
        }

        // observe adapter load state to update determinate progress and hide loading when done
        loadStateJob = lifecycleScope.launch {
            pagingAdapter.loadStateFlow.collect { loadStates ->
                // update progress using number of loaded items
                val loaded = pagingAdapter.snapshot().items.size
                withContext(Dispatchers.Main) {
                    try {
                        if (pbOverviewProgressView.visibility == View.VISIBLE) {
                            pbOverviewProgressView.progress = loaded
                        }

                        // Decide whether to keep showing the modal dialog between pages.
                            val pageSize = PagingConstants.DEFAULT_PAGE_SIZE
                        if (overviewTotalBlocks > 0) {
                            val loadedBlocks = ((loaded + pageSize - 1) / pageSize).coerceAtLeast(0)
                                    if (loadedBlocks < overviewTotalBlocks) {
                                        // still more blocks to load: show placeholder block for the next page
                                        pbOverviewContainer.visibility = View.VISIBLE
                                        val remaining = (overviewTotalCount ?: 0) - loaded
                                        val nextPlaceholders = minOf(maxOf(remaining, 0), pageSize)
                                        try { placeholderAdapter.setCount(nextPlaceholders) } catch (_: Exception) { }
                                    } else {
                                        // finished
                                        pbOverviewContainer.visibility = if (loaded > 0) View.GONE else View.VISIBLE
                                        try { placeholderAdapter.setCount(0) } catch (_: Exception) { }
                                    }
                        } else {
                            // fallback to previous behavior when totals unknown
                            val refresh = loadStates.refresh
                            when (refresh) {
                                is LoadState.Loading -> {
                                    pbOverviewContainer.visibility = View.VISIBLE
                                    // show a single block of placeholders when we don't know totals
                                    try { placeholderAdapter.setCount(pageSize) } catch (_: Exception) { }
                                }
                                is LoadState.NotLoading -> {
                                    if (loaded > 0) {
                                        pbOverviewContainer.visibility = View.GONE
                                        try { placeholderAdapter.setCount(0) } catch (_: Exception) { }
                                    } else {
                                        pbOverviewContainer.visibility = View.VISIBLE
                                    }
                                }
                                is LoadState.Error -> {
                                    pbOverviewContainer.visibility = View.VISIBLE
                                    try { placeholderAdapter.setCount(0) } catch (_: Exception) { }
                                }
                            }
                        }
                    } catch (_: Exception) { }
                }
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
            val cbShowReturn = itemView.findViewById<CheckBox>(R.id.cbShowReturn)
            val cbShowTrek = itemView.findViewById<CheckBox>(R.id.cbShowTrek)
            val producer = CartesianChartModelProducer()

            headerView.text = "$direction - Aantal: 0 / Terug: 0"
            // default: show return line
            cbShowReturn.isChecked = true
            cbShowTrek.isChecked = true
            setupSingleChart(chartView, producer)

            val rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            val columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            itemView.layoutParams = GridLayout.LayoutParams(rowSpec, columnSpec).apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                setMargins(spacing, spacing, spacing, spacing)
            }

            gridWindCharts.addView(itemView)
            chartBindings[direction] = WindChartBinding(headerView, chartView, producer, cbShowReturn, cbShowTrek)

            // Toggle listeners: update chart when either Trek or Terug checkbox changes
            val toggleListener = { _: android.widget.CompoundButton, _: Boolean ->
                val cached = cachedChartData[direction]
                if (cached != null) {
                    val (countData, returnData, beaufortData) = cached
                    val showTrek = cbShowTrek.isChecked
                    val showReturn = cbShowReturn.isChecked
                    val effectiveCount = if (showTrek) countData else IntArray(countData.size) { 0 }
                    val effectiveReturn = if (showReturn) returnData else IntArray(returnData.size) { 0 }
                    lifecycleScope.launch {
                        try {
                            updateChart(chartView, producer, effectiveCount, effectiveReturn, beaufortData)
                        } catch (e: Exception) {
                            fileLogger.warn("Kon chart niet updaten voor richting $direction: ${e.message}")
                        }
                    }
                }
            }

            cbShowReturn.setOnCheckedChangeListener(toggleListener)
            cbShowTrek.setOnCheckedChangeListener(toggleListener)
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

        showLoading(getString(R.string.loading_chart))
            lifecycleScope.launch(Dispatchers.Default) {
            fileLogger.info("GRAFIEK16: Start ophalen data voor soort $speciesId")
            // fetch wind dataset in pages to avoid long blocking queries and allow progress updates
            val pageSize = PagingConstants.DEFAULT_PAGE_SIZE
            var offset = 0
            val year = getSelectedYear()

            // If wind debug logging is enabled, schedule writing debug rows (keeps previous behavior)
            if (VT5App.ENABLE_WIND_DEBUG_LOGGING) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val debugRows = database.tellingDao().getWindDebugRowsForSpecies(speciesId)
                        val debugFiltered = if (year == null) debugRows else debugRows.filter { r ->
                            getLocalYearFromEpoch(r.begintijd, r.timezoneid) == year
                        }
                        writeWindDebugFile(speciesId, debugFiltered)
                    } catch (e: Exception) {
                        fileLogger.warn("Kon wind debug rows niet schrijven: ${e.message}")
                    }
                }
            }

            // Aggregation buffers
            val weeklyTotalsByDirection = windDirections.associateWith { IntArray(WEEKS_PER_YEAR) }.toMutableMap()
            val weeklyTotalsByDirectionReturn = windDirections.associateWith { IntArray(WEEKS_PER_YEAR) }.toMutableMap()
            val weeklyBeaufortWeightedSumByDirection = windDirections.associateWith { DoubleArray(WEEKS_PER_YEAR) }.toMutableMap()
            val weeklyBeaufortWeightByDirection = windDirections.associateWith { IntArray(WEEKS_PER_YEAR) }.toMutableMap()

            var processed = 0
                while (true) {
                val page = try {
                    withContext(Dispatchers.IO) { database.tellingDao().getWindDatasetForSpeciesPaged(speciesId, pageSize, offset) }
                } catch (e: Exception) {
                    fileLogger.warn("Fout bij paged ophalen wind dataset: ${e.message}")
                    emptyList<com.yvesds.vt5.core.database.dao.SpeciesWindDatasetRow>()
                }
                if (page.isEmpty()) break

                // apply client-side year filtering and aggregate
                page.forEach { row ->
                    if (year != null && getLocalYearFromEpoch(row.begintijd, row.timezoneid) != year) return@forEach
                    val direction = normalizeWindDirection(row.windrichting) ?: return@forEach
                    val weekIndex = getWeekIndex(row.begintijd, row.timezoneid)
                    if (weekIndex in 0 until WEEKS_PER_YEAR) {
                        weeklyTotalsByDirection[direction]?.let { it[weekIndex] += row.aantal }
                        weeklyTotalsByDirectionReturn[direction]?.let { it[weekIndex] += row.aantalterug }

                        val beaufort = parseBeaufort(row.windkracht)
                        if (beaufort != null) {
                            val weight = row.aantal.coerceAtLeast(1)
                            weeklyBeaufortWeightedSumByDirection[direction]?.let { sums -> sums[weekIndex] += beaufort * weight }
                            weeklyBeaufortWeightByDirection[direction]?.let { weights -> weights[weekIndex] += weight }
                        }
                    }
                }

                processed += page.size
                offset += pageSize
                if (coroutineContext[kotlinx.coroutines.Job]?.isActive != true) break

                // update loading dialog to show progress (gives system a 'heartbeat')
                withContext(Dispatchers.Main) {
                    try {
                        loadingDialog?.setTitle(getString(R.string.loading_chart_progress, processed))
                    } catch (_: Exception) { }
                }
            }

            // store into local variable for subsequent chart building
            currentWindDataset = emptyList()


            withContext(Dispatchers.Main) {
                windDirections.forEach { direction ->
                    val binding = chartBindings[direction] ?: return@forEach
                    val countData = weeklyTotalsByDirection[direction] ?: IntArray(WEEKS_PER_YEAR)
                    val returnData = weeklyTotalsByDirectionReturn[direction] ?: IntArray(WEEKS_PER_YEAR)
                    val beaufortSums = weeklyBeaufortWeightedSumByDirection[direction] ?: DoubleArray(WEEKS_PER_YEAR)
                    val beaufortWeights = weeklyBeaufortWeightByDirection[direction] ?: IntArray(WEEKS_PER_YEAR)
                    val beaufortData = DoubleArray(52) { index ->
                        val weight = beaufortWeights[index]
                        if (weight > 0) beaufortSums[index] / weight else 0.0
                    }

                    binding.headerView.text = "$direction - Aantal: ${countData.sum()} / Terug: ${returnData.sum()}"
                    // cache per-direction series so checkbox toggles don't require full recompute
                    cachedChartData[direction] = Triple(countData, returnData, beaufortData)
                    val showTrek = binding.cbShowTrek.isChecked
                    val showReturn = binding.cbShowReturn.isChecked
                    val effectiveCount = if (showTrek) countData else IntArray(countData.size) { 0 }
                    val effectiveReturn = if (showReturn) returnData else IntArray(returnData.size) { 0 }
                    updateChart(binding.chartView, binding.producer, effectiveCount, effectiveReturn, beaufortData)
                }
                hideLoading()
            }
        }
    }

    private fun showLoading(message: String) {
        try {
            // If dialog already showing, update its title so progress is visible
            if (loadingDialog?.isShowing == true) {
                try { loadingDialog?.setTitle(message) } catch (_: Exception) { }
                return
            }
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
        // Create separate layers: beaufort (right axis), main count (left axis) and return overlay (left axis)
        val countLayer = VicoLineChartHelper.createLineLayer(chartLineColor)
        val returnLayer = VicoLineChartHelper.createLineLayer(chartReturnLineColor)
        val beaufortLayer = VicoLineChartHelper.createBeaufortLineLayer(maxBeaufort = 7.0, beaufortColor = beaufortLineColor, pointSpacingDp = 8f)

        // Layer drawing order: beaufort (background/right axis) -> countLayer (green) -> returnLayer (red on top)
        chartView.chart = CartesianChart(
            beaufortLayer,
            countLayer,
            returnLayer,
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
        returnData: IntArray,
        beaufortData: DoubleArray,
    ) {
        // Clamp beaufort values to a maximum of 7 (Beaufort scale limit for our data)
        val maxBeaufort = 7.0
        val clampedBeaufort = beaufortData.map { v -> (v.coerceAtMost(maxBeaufort)).toFloat() }

        // Log aggregated sums before entering the non-suspending runTransaction lambda
        val countsSum = countData.sum()
        val returnSum = returnData.sum()
        val beaufortSum = beaufortData.sum()
        fileLogger.info("UpdateChart: counts=$countsSum return=$returnSum beaufortAvg=$beaufortSum")

        producer.runTransaction {
            // Layer order: beaufortLayer (background, right axis) then countLayer (foreground, left axis)
            // Produce series in the same order as the layers: first beaufort, then main count, then return count.
            lineSeries { series(clampedBeaufort) }
            lineSeries { series(countData.map { it.toFloat() }) }
            lineSeries { series(returnData.map { it.toFloat() }) }
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
        val epoch = epochValue.trim().toLongOrNull() ?: return -1
        val epochSeconds = if (epoch > 9_999_999_999L) epoch / 1000 else epoch
        val zone = runCatching {
            ZoneId.of(timezoneId.ifBlank { "Europe/Brussels" })
        }.getOrDefault(ZoneId.of("Europe/Brussels"))

        val localDate = Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate()
        val weekNumber = localDate.get(WeekFields.of(Locale.getDefault()).weekOfYear())
        return (weekNumber - 1).coerceIn(0, WEEKS_PER_YEAR - 1)
    }

    private fun getLocalYearFromEpoch(epochValue: String, timezoneId: String): String? {
        val epoch = epochValue.trim().toLongOrNull() ?: return null
        val epochSeconds = if (epoch > 9_999_999_999L) epoch / 1000 else epoch
        val zone = runCatching { ZoneId.of(timezoneId.ifBlank { "Europe/Brussels" }) }
            .getOrDefault(ZoneId.of("Europe/Brussels"))
        val localDate = Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate()
        return localDate.year.toString()
    }

    private suspend fun writeWindDebugFile(speciesId: String, rows: List<com.yvesds.vt5.core.database.dao.SpeciesWindDebugRow>) {
        withContext(Dispatchers.IO) {
            try {
                val saf = SaFStorageHelper(this@DatabaseSoortOverzichtActiviteit)
                val vt5Dir = saf.getVt5DirIfExists() ?: return@withContext

                val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(Instant.now().atZone(ZoneId.systemDefault()))
                val filename = "wind_debug_${speciesId}_$ts.txt"
                val file = vt5Dir.createFile("text/plain", filename) ?: return@withContext

                val sb = StringBuilder()
                sb.append("Wind debug log for speciesId=$speciesId\n")
                sb.append("Generated: $ts\n")
                sb.append("Columns: idLocal, tellingid, waarnemingOnlineId, headerOnlineId, begintijd, timezoneid, windrichting, windkracht, aantal, aantalterug, localYear, weekIndex, normalizedDirection\n")

                rows.forEach { r ->
                    val localYear = getLocalYearFromEpoch(r.begintijd, r.timezoneid) ?: "-"
                    val weekIdx = getWeekIndex(r.begintijd, r.timezoneid)
                    val directionNorm = normalizeWindDirection(r.windrichting) ?: r.windrichting
                    sb.append("${r.idLocal},${r.tellingid},${r.waarnemingOnlineId},${r.headerOnlineId},${r.begintijd},${r.timezoneid},${r.windrichting},${r.windkracht},${r.aantal},${r.aantalterug},${localYear},${weekIdx},${directionNorm}\n")
                }

                // write content
                try {
                    contentResolver.openOutputStream(file.uri)?.use { out ->
                        out.write(sb.toString().toByteArray(Charsets.UTF_8))
                    }
                    fileLogger.info("Wind debug file written: $filename")
                } catch (e: Exception) {
                    try { file.delete() } catch (_: Exception) {}
                    fileLogger.warn("Kon wind debug file niet wegschrijven: ${e.message}")
                }
            } catch (e: Exception) {
                fileLogger.warn("Fout bij schrijven wind debug file: ${e.message}")
            }
        }
    }

    private data class WindChartBinding(
        val headerView: TextView,
        val chartView: CartesianChartView,
        val producer: CartesianChartModelProducer,
        val cbShowReturn: CheckBox,
        val cbShowTrek: CheckBox,
    )

    inner class WaarnemingPagingAdapter : PagingDataAdapter<Waarneming, WaarnemingPagingAdapter.ViewHolder>(WAARNEMING_DIFF) {
        var soortNaam: String = ""

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvIndex: TextView = view.findViewById(R.id.tvIndex)
            val tvSoortNaam: TextView = view.findViewById(R.id.tvSoortNaam)
            val tvDetails: TextView = view.findViewById(R.id.tvDetails)
            val tvAantal: TextView = view.findViewById(R.id.tvAantal)
            val tvAantalTerug: TextView? = view.findViewById(R.id.tvAantalTerug)
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

            if (item != null) {
                val readableTime = SpeciesNameResolver.formatTimestamp(item.tijdstip)
                holder.tvDetails.text = "Sessie: ${item.tellingid} | Tijd: $readableTime"
                holder.tvAantal.text = item.aantal
                holder.tvAantalTerug?.text = item.aantalterug
                holder.itemView.setOnClickListener {
                    val intent = Intent(this@DatabaseSoortOverzichtActiviteit, DatabaseRecordDetailActiviteit::class.java)
                    intent.putExtra("recordid", item.idLocal)
                    intent.putExtra("tellingid", item.tellingid)
                    startActivity(intent)
                }
            } else {
                holder.tvDetails.text = ""
                holder.tvAantal.text = ""
                holder.tvAantalTerug?.text = ""
                holder.itemView.setOnClickListener(null)
            }
        }
    }

    inner class PlaceholderAdapter : RecyclerView.Adapter<PlaceholderAdapter.PlaceholderViewHolder>() {
        private var count: Int = 0

        inner class PlaceholderViewHolder(view: View) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceholderViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_db_waarneming_placeholder, parent, false)
            return PlaceholderViewHolder(view)
        }

        override fun onBindViewHolder(holder: PlaceholderViewHolder, position: Int) {
            // nothing to bind; static placeholder UI
        }

        override fun getItemCount(): Int = count

        fun setCount(newCount: Int) {
            if (newCount == count) return
            count = newCount
            notifyDataSetChanged()
        }
    }
}
