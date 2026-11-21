package com.yvesds.vt5.ai

import android.content.Context
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.core.database.entities.TelpostLocatiesRoot
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * BsiClusterManager - Centrale utility voor het bepalen van 35km telpost clusters
 * en geografische afstandsberekeningen (Haversine formule).
 */
object BsiClusterManager {

    private const val EARTH_RADIUS_KM = 6371.0
    const val DEFAULT_CLUSTER_RADIUS_KM = 35.0

    /**
     * Berekent de afstand in kilometer tussen twee geografische coördinaten.
     */
    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Haalt de telpost-ID's op van alle telposten die binnen de straal (standaard 35km) liggen.
     */
    suspend fun getClusterSiteIds(
        context: Context,
        lat: Double? = null,
        lon: Double? = null,
        radiusKm: Double = DEFAULT_CLUSTER_RADIUS_KM
    ): List<String> = withContext(Dispatchers.IO) {
        val saf = SaFStorageHelper(context)
        try {
            val jsonStr = saf.readServerDataFile("telpost_locaties.json") ?: "{}"
            val root = VT5App.json.decodeFromString<TelpostLocatiesRoot>(jsonStr)
            if (root.locaties.isEmpty()) return@withContext emptyList()

            val anchorLat = lat ?: root.locaties.first().latitude
            val anchorLon = lon ?: root.locaties.first().longitude

            val clusterIds = root.locaties
                .filter { calculateDistanceKm(anchorLat, anchorLon, it.latitude, it.longitude) <= radiusKm }
                .map { it.telpostid }

            if (clusterIds.isEmpty()) listOf(root.locaties.first().telpostid) else clusterIds
        } catch (_: Exception) {
            emptyList()
        }
    }
}
