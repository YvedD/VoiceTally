package com.yvesds.vt5.ai

import android.content.Context
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.core.database.entities.TelpostLocatiesRoot
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SiteContextResolver - Dynamische analyse van telpost-context (Kustzone vs. Binnenland, Zicht-op-Zee, Anchor Sites).
 */
object SiteContextResolver {

    private const val COASTAL_THRESHOLD_KM = 15.0
    private const val SEA_VIEW_THRESHOLD_KM = 2.0

    // Bekende referentie-kustpunten langs de Belgische en Nederlandse kust
    private val COASTLINE_REFERENCE_POINTS = listOf(
        Pair(51.10, 2.65), // De Panne
        Pair(51.23, 2.92), // Oostende
        Pair(51.35, 3.28), // Knokke / Het Zwin
        Pair(51.50, 3.60), // Walcheren / Domburg
        Pair(51.80, 3.85), // Brouwersdam
        Pair(51.98, 4.10), // Hoek van Holland
        Pair(52.46, 4.56), // IJmuiden Pier
        Pair(52.95, 4.75), // Den Helder
        Pair(53.05, 4.80), // Texel Paal 9
        Pair(53.38, 6.19), // Lauwersmeer
        Pair(53.58, 6.66)  // Eemsmonding
    )

    /**
     * Bepaal de exacte afstand (in km) van de telpost/coördinaten tot de dichtstbijzijnde kustlijn.
     */
    fun getDistanceToCoastlineKm(lat: Double, lon: Double): Double {
        return COASTLINE_REFERENCE_POINTS.minOf { (cLat, cLon) ->
            BsiClusterManager.calculateDistanceKm(lat, lon, cLat, cLon)
        }
    }

    /**
     * BSI 5.4: Bepaal of de telpost echt "Zicht-op-Zee" heeft (<= 2.0 km van de kustlijn).
     * Binnenland/duin-telposten op 3-5 km hebben GEEN zicht op zee voor pelagische zeevogels.
     */
    fun hasSeaView(lat: Double, lon: Double): Boolean {
        return getDistanceToCoastlineKm(lat, lon) <= SEA_VIEW_THRESHOLD_KM
    }

    /**
     * Bepaal dynamisch of een specifieke telpost zich in de bredere kustzone (<= 15 km) bevindt.
     */
    suspend fun isCoastalSite(context: Context, siteId: String? = null, lat: Double? = null, lon: Double? = null): Boolean = withContext(Dispatchers.IO) {
        val saf = SaFStorageHelper(context)
        try {
            val jsonStr = saf.readServerDataFile("telpost_locaties.json") ?: "{}"
            val root = VT5App.json.decodeFromString<TelpostLocatiesRoot>(jsonStr)
            
            val targetSite = if (siteId != null) {
                root.locaties.find { it.telpostid == siteId }
            } else null

            val targetLat = lat ?: targetSite?.latitude ?: root.locaties.firstOrNull()?.latitude ?: 51.2
            val targetLon = lon ?: targetSite?.longitude ?: root.locaties.firstOrNull()?.longitude ?: 3.0

            getDistanceToCoastlineKm(targetLat, targetLon) <= COASTAL_THRESHOLD_KM
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Bepaal dynamisch de Anchor Sites (hoofdtelposten) voor de Gouden Mal uurs-distributie.
     */
    suspend fun getAnchorSiteIds(context: Context): List<String> = withContext(Dispatchers.IO) {
        val saf = SaFStorageHelper(context)
        try {
            val jsonStr = saf.readServerDataFile("telpost_locaties.json") ?: "{}"
            val root = VT5App.json.decodeFromString<TelpostLocatiesRoot>(jsonStr)
            root.locaties.take(2).map { it.telpostid }
        } catch (_: Exception) {
            listOf("site_1", "site_2")
        }
    }
}
