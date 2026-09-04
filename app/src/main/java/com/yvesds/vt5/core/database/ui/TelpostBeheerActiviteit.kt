package com.yvesds.vt5.core.database.ui

import android.Manifest
import android.graphics.Color
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.entities.TelpostLocatie
import com.yvesds.vt5.core.database.entities.TelpostLocatiesRoot
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.databinding.SchermTelpostBeheerBinding
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.*

/**
 * TelpostBeheerActiviteit - Herstelde en robuuste kaart-implementatie.
 */
class TelpostBeheerActiviteit : AppCompatActivity() {

    private lateinit var binding: SchermTelpostBeheerBinding
    private lateinit var saf: SaFStorageHelper
    private var selectedTelpostId: String? = null
    private val allLocaties = mutableMapOf<String, TelpostLocatie>()
    private val overviewMarkers = mutableMapOf<String, Marker>()
    private var telpostNames = mapOf<String, String>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) zoomToMyLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val ctx = applicationContext
        Configuration.getInstance().load(ctx, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        
        binding = SchermTelpostBeheerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        saf = SaFStorageHelper(this)
        
        setupMap()
        loadData()
        
        binding.btnTerug.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener { saveCurrentLocatie() }
        binding.btnReset.setOnClickListener { confirmResetAll() }
        binding.fabMyLocation.setOnClickListener { ensurePermissionAndZoom() }
        binding.fabLayers.setOnClickListener { showLayerPopup() }
    }

    private fun confirmResetAll() {
        AlertDialog.Builder(this)
            .setTitle(R.string.telpost_beheer_reset_confirm_title)
            .setMessage(R.string.telpost_beheer_reset_confirm_msg)
            .setPositiveButton(R.string.beheer_verwijderen) { _, _ -> resetAllLocaties() }
            .setNegativeButton(R.string.annuleer, null)
            .show()
    }

    private fun resetAllLocaties() {
        allLocaties.clear()
        lifecycleScope.launch(Dispatchers.IO) {
            val emptyRoot = TelpostLocatiesRoot(emptyList())
            val jsonStr = Json { prettyPrint = true }.encodeToString(emptyRoot)
            val ok = saf.writeServerDataFile("telpost_locaties.json", jsonStr)
            withContext(Dispatchers.Main) {
                if (ok) {
                    drawAllMarkers()
                    binding.tvCoords.text = "Locatie: -"
                    Toast.makeText(this@TelpostBeheerActiviteit, "Alle locaties gewist", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller.setZoom(9.0)
        binding.mapView.controller.setCenter(GeoPoint(51.2, 4.4))
        
        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                if (selectedTelpostId == null) {
                    Toast.makeText(this@TelpostBeheerActiviteit, "Kies eerst een telpost", Toast.LENGTH_SHORT).show()
                    return false
                }
                updateMarkerAtPoint(p)
                return true
            }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        })
        binding.mapView.overlays.add(eventsOverlay)
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.Main) {
            // 1. Namen laden
            val sites = try {
                val snapshot = withContext(Dispatchers.IO) { ServerDataCache.getOrLoad(this@TelpostBeheerActiviteit) }
                snapshot.sitesById.values.sortedBy { it.telpostnaam.lowercase(Locale.getDefault()) }
            } catch (e: Exception) {
                Log.e("TelpostBeheer", "Names load failed: ${e.message}")
                emptyList()
            }

            telpostNames = sites.associate { it.telpostid to it.telpostnaam }
            
            val adapter = ArrayAdapter(this@TelpostBeheerActiviteit, android.R.layout.simple_list_item_1, sites.map { it.telpostnaam })
            binding.acTelpost.setAdapter(adapter)
            binding.acTelpost.setOnItemClickListener { parent, _, pos, _ ->
                val selectedName = parent.getItemAtPosition(pos).toString()
                selectedTelpostId = sites.find { it.telpostnaam == selectedName }?.telpostid
                showLocatieForSelected()
            }

            // 2. Locaties laden
            val json = withContext(Dispatchers.IO) { saf.readServerDataFile("telpost_locaties.json") }
            allLocaties.clear()
            if (!json.isNullOrBlank()) {
                try {
                    val root = Json { ignoreUnknownKeys = true }.decodeFromString<TelpostLocatiesRoot>(json)
                    root.locaties.forEach { allLocaties[it.telpostid] = it }
                } catch (e: Exception) {
                    Log.e("TelpostBeheer", "JSON load failed: ${e.message}")
                }
            }
            
            // 3. ALTIJD tekenen, ook bij lege lijst
            drawAllMarkers()
        }
    }

    private fun drawAllMarkers() {
        binding.mapView.overlays.removeAll { it is Marker || it is org.osmdroid.views.overlay.Polygon }
        overviewMarkers.clear()

        val siteList = allLocaties.values.toList()
        val anchorSite = siteList.firstOrNull()

        // 1. 35km cirkel
        anchorSite?.let { loc ->
            val circle = org.osmdroid.views.overlay.Polygon(binding.mapView)
            circle.points = org.osmdroid.views.overlay.Polygon.pointsAsCircle(GeoPoint(loc.latitude, loc.longitude), 35000.0)
            circle.fillPaint.color = Color.parseColor("#3000BCD4")
            circle.outlinePaint.color = Color.parseColor("#FF00BCD4")
            circle.outlinePaint.strokeWidth = 3f
            binding.mapView.overlays.add(circle)
        }

        // 2. Markers
        siteList.forEach { loc ->
            val id = loc.telpostid
            val marker = Marker(binding.mapView)
            marker.position = GeoPoint(loc.latitude, loc.longitude)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = telpostNames[id] ?: "Telpost $id"
            
            if (id == selectedTelpostId) {
                marker.alpha = 1.0f
                marker.showInfoWindow()
                // Probeer tint, maar negeer fouten (niet kritiek voor werking)
                try { marker.icon?.setTint(ContextCompat.getColor(this, R.color.vt5_green)) } catch (_: Exception) {}
            } else {
                marker.alpha = 0.6f
            }
            
            binding.mapView.overlays.add(marker)
            overviewMarkers[id] = marker
        }

        if (anchorSite != null && selectedTelpostId == null) {
            binding.mapView.controller.setCenter(GeoPoint(anchorSite.latitude, anchorSite.longitude))
        } else if (allLocaties.isNotEmpty() && selectedTelpostId == null) {
            val avgLat = allLocaties.values.map { it.latitude }.average()
            val avgLon = allLocaties.values.map { it.longitude }.average()
            binding.mapView.controller.setCenter(GeoPoint(avgLat, avgLon))
        }
        binding.mapView.invalidate()
    }

    private fun showLocatieForSelected() {
        val id = selectedTelpostId ?: return
        val loc = allLocaties[id]
        if (loc != null) {
            val point = GeoPoint(loc.latitude, loc.longitude)
            binding.mapView.controller.animateTo(point)
            binding.mapView.controller.setZoom(16.0)
            binding.tvCoords.text = String.format(Locale.getDefault(), "Locatie: %.6f, %.6f", loc.latitude, loc.longitude)
        } else {
            binding.tvCoords.text = "Locatie: -"
        }
        drawAllMarkers() // Refresh alle markers en de geselecteerde
    }

    private fun updateMarkerAtPoint(p: GeoPoint) {
        val id = selectedTelpostId ?: return
        var marker = overviewMarkers[id]
        if (marker == null) {
            marker = Marker(binding.mapView)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            binding.mapView.overlays.add(marker)
            overviewMarkers[id] = marker
        }
        marker.position = p
        marker.title = telpostNames[id] ?: "Telpost $id"
        marker.alpha = 1.0f
        marker.showInfoWindow()
        try { marker.icon?.setTint(ContextCompat.getColor(this, R.color.vt5_green)) } catch (_: Exception) {}
        binding.mapView.invalidate()
        binding.tvCoords.text = String.format(Locale.getDefault(), "Locatie: %.6f, %.6f", p.latitude, p.longitude)
    }

    private fun saveCurrentLocatie() {
        val id = selectedTelpostId ?: return
        val marker = overviewMarkers[id] ?: return
        val p = marker.position
        
        allLocaties[id] = TelpostLocatie(id, p.latitude, p.longitude)
        
        lifecycleScope.launch(Dispatchers.IO) {
            val root = TelpostLocatiesRoot(allLocaties.values.toList().sortedBy { it.telpostid })
            val jsonStr = Json { prettyPrint = true; encodeDefaults = true }.encodeToString(root)
            val ok = saf.writeServerDataFile("telpost_locaties.json", jsonStr)
            withContext(Dispatchers.Main) {
                if (ok) Toast.makeText(this@TelpostBeheerActiviteit, "Opgeslagen: ${telpostNames[id]}", Toast.LENGTH_SHORT).show()
                else Toast.makeText(this@TelpostBeheerActiviteit, "Opslaan mislukt!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLayerPopup() {
        val popup = PopupMenu(this, binding.fabLayers)
        popup.menu.add(0, 1, 0, "Standaard")
        popup.menu.add(0, 2, 1, "Satelliet")
        popup.menu.add(0, 3, 2, "Topografisch")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
                2 -> {
                    val esriSat = object : OnlineTileSourceBase("EsriSat", 0, 19, 256, "", arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"), "© Esri") {
                        override fun getTileURLString(p: Long): String = baseUrl + MapTileIndex.getZoom(p) + "/" + MapTileIndex.getY(p) + "/" + MapTileIndex.getX(p)
                    }
                    binding.mapView.setTileSource(esriSat)
                }
                3 -> binding.mapView.setTileSource(TileSourceFactory.OpenTopo)
            }
            true
        }
        popup.show()
    }

    private fun ensurePermissionAndZoom() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) zoomToMyLocation()
        else requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun zoomToMyLocation() {
        val lm = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        try {
            val l = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            l?.let {
                val p = GeoPoint(it.latitude, it.longitude)
                binding.mapView.controller.animateTo(p)
                binding.mapView.controller.setZoom(17.0)
                if (selectedTelpostId != null) updateMarkerAtPoint(p)
            }
        } catch (e: SecurityException) { }
    }

    override fun onResume() { super.onResume(); binding.mapView.onResume() }
    override fun onPause() { super.onPause(); binding.mapView.onPause() }
}
