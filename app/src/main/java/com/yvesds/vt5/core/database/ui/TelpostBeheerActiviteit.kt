package com.yvesds.vt5.core.database.ui

import android.Manifest
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
import com.yvesds.vt5.VT5App
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
 * TelpostBeheerActiviteit - Robuuste kaart-implementatie voor telpost-locaties.
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
        
        // 1. Dwingende Osmdroid initialisatie
        val ctx = applicationContext
        Configuration.getInstance().load(ctx, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        
        binding = SchermTelpostBeheerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        saf = SaFStorageHelper(this)
        
        // 2. Kaart direct instellen
        setupMap()
        
        // 3. Start data-laden
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
            .setPositiveButton(R.string.beheer_verwijderen) { _, _ ->
                resetAllLocaties()
            }
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
        binding.mapView.controller.setCenter(GeoPoint(51.2, 4.4)) // Midden v/h land
        
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
        // 1. Laad telposten voor de dropdown (Parallel)
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val snapshot = withContext(Dispatchers.IO) { ServerDataCache.getOrLoad(this@TelpostBeheerActiviteit) }
                val sites = snapshot.sitesById.values.sortedBy { it.telpostnaam.lowercase() }
                telpostNames = sites.associate { it.telpostid to it.telpostnaam }
                
                val adapter = ArrayAdapter(this@TelpostBeheerActiviteit, android.R.layout.simple_list_item_1, sites.map { it.telpostnaam })
                binding.acTelpost.setAdapter(adapter)
                binding.acTelpost.setOnItemClickListener { _, _, pos, _ ->
                    selectedTelpostId = sites[pos].telpostid
                    showLocatieForSelected()
                }
                // Update titels van markers die al getekend zijn
                overviewMarkers.forEach { (id, marker) -> marker.title = telpostNames[id] ?: id }
            } catch (e: Exception) { Log.e("TelpostBeheer", "Names load failed: ${e.message}") }
        }

        // 2. Laad opgeslagen locaties (Parallel & Tolerant)
        lifecycleScope.launch(Dispatchers.Main) {
            val json = withContext(Dispatchers.IO) { saf.readServerDataFile("telpost_locaties.json") }
            if (!json.isNullOrBlank()) {
                try {
                    val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }
                    
                    // Reparatie voor het geval van dubbele JSON-blokken
                    val cleanJson = if (json.count { it == '{' } > json.count { it == '}' } || json.contains("} {")) {
                        Log.w("TelpostBeheer", "Corrupt JSON detected, attempting structural repair...")
                        val firstEnd = json.indexOf("} {")
                        if (firstEnd != -1) json.substring(0, firstEnd + 1) else json
                    } else json

                    val root = lenientJson.decodeFromString<TelpostLocatiesRoot>(cleanJson)
                    allLocaties.clear()
                    root.locaties.forEach { allLocaties[it.telpostid] = it }
                    
                    delay(200)
                    drawAllMarkers()
                    Log.i("TelpostBeheer", "Loaded ${allLocaties.size} locations.")
                } catch (e: Exception) {
                    Log.e("TelpostBeheer", "Fatal parse error: ${e.message}")
                    Toast.makeText(this@TelpostBeheerActiviteit, "Fout in locatiebestand. Sla een nieuwe locatie op om te herstellen.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun drawAllMarkers() {
        overviewMarkers.values.forEach { binding.mapView.overlays.remove(it) }
        overviewMarkers.clear()

        allLocaties.forEach { (id, loc) ->
            val marker = Marker(binding.mapView)
            marker.position = GeoPoint(loc.latitude, loc.longitude)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = telpostNames[id] ?: id
            marker.alpha = if (id == selectedTelpostId) 1.0f else 0.6f
            binding.mapView.overlays.add(marker)
            overviewMarkers[id] = marker
        }

        // Center op alle markers als er geen selectie is
        if (allLocaties.isNotEmpty() && selectedTelpostId == null) {
            val avgLat = allLocaties.values.map { it.latitude }.average()
            val avgLon = allLocaties.values.map { it.longitude }.average()
            binding.mapView.controller.setCenter(GeoPoint(avgLat, avgLon))
        }
        binding.mapView.invalidate()
    }

    private fun showLocatieForSelected() {
        val id = selectedTelpostId ?: return
        val loc = allLocaties[id]
        
        // 1. Zoom en vlieg naar de locatie (als die er is)
        if (loc != null) {
            val point = GeoPoint(loc.latitude, loc.longitude)
            binding.mapView.controller.animateTo(point)
            binding.mapView.controller.setZoom(16.0)
            binding.tvCoords.text = String.format(Locale.getDefault(), "Locatie: %.6f, %.6f", loc.latitude, loc.longitude)
        } else {
            binding.tvCoords.text = "Locatie: -"
            Toast.makeText(this, "Geen opgeslagen locatie. Tik op de kaart!", Toast.LENGTH_SHORT).show()
        }

        // 2. Highlight de bijbehorende marker
        overviewMarkers.forEach { (mid, marker) ->
            if (mid == id) {
                marker.alpha = 1.0f
                marker.showInfoWindow()
            } else {
                marker.alpha = 0.6f
                marker.closeInfoWindow()
            }
        }
        binding.mapView.invalidate()
    }

    private fun updateMarkerAtPoint(p: GeoPoint) {
        val id = selectedTelpostId ?: return
        var marker = overviewMarkers[id]
        if (marker == null) {
            marker = Marker(binding.mapView)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = telpostNames[id] ?: id
            binding.mapView.overlays.add(marker)
            overviewMarkers[id] = marker
        }
        marker.position = p
        marker.alpha = 1.0f
        marker.showInfoWindow()
        binding.mapView.invalidate()
        binding.tvCoords.text = String.format(Locale.getDefault(), "Locatie: %.6f, %.6f", p.latitude, p.longitude)
    }

    private fun saveCurrentLocatie() {
        val id = selectedTelpostId ?: return
        val marker = overviewMarkers[id] ?: return
        val p = marker.position
        
        val newLoc = TelpostLocatie(id, p.latitude, p.longitude)
        allLocaties[id] = newLoc
        
        lifecycleScope.launch(Dispatchers.IO) {
            val root = TelpostLocatiesRoot(allLocaties.values.toList().sortedBy { it.telpostid })
            val jsonStr = Json { prettyPrint = true; encodeDefaults = true }.encodeToString(root)
            val ok = saf.writeServerDataFile("telpost_locaties.json", jsonStr)
            withContext(Dispatchers.Main) {
                if (ok) Toast.makeText(this@TelpostBeheerActiviteit, "Locatie opgeslagen", Toast.LENGTH_SHORT).show()
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
