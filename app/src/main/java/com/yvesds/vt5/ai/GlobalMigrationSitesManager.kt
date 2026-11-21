package com.yvesds.vt5.ai

import android.content.Context
import com.yvesds.vt5.VT5App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class SeasonalVector(
    val primaryHeadingDeg: Double = 0.0,
    val directionLabel: String = "",
    val favorableWindSectors: List<String> = emptyList()
)

@Serializable
data class MigrationVectors(
    val spring: SeasonalVector? = null,
    val autumn: SeasonalVector? = null
)

@Serializable
data class GlobalMigrationSite(
    val siteId: String,
    val trektellenId: Int = 0,
    val name: String,
    val countryCode: String = "",
    val region: String? = null,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Int = 0,
    val biotopeType: String = "INLAND_LOWLAND",
    val hasSeaView: Boolean = false,
    val coastalDistanceKm: Double = 10.0,
    val migrationVectors: MigrationVectors? = null,
    val dominantGuilds: List<String> = emptyList()
)

@Serializable
data class GlobalMigrationSitesRoot(
    val schemaVersion: Int = 1,
    val totalSites: Int = 0,
    val sites: List<GlobalMigrationSite> = emptyList()
)

/**
 * GlobalMigrationSitesManager - Beheert het uitlezen van assets/global_migration_sites.json
 * voor internationale BSI-prognoses zonder vereiste van lokale accreditatie.
 */
object GlobalMigrationSitesManager {

    private var cachedRoot: GlobalMigrationSitesRoot? = null

    /**
     * Haalt alle internationale referentie-telposten op uit assets/global_migration_sites.json.
     */
    suspend fun getAllGlobalSites(context: Context): List<GlobalMigrationSite> = withContext(Dispatchers.IO) {
        cachedRoot?.sites?.let { return@withContext it }
        try {
            val jsonStr = context.assets.open("global_migration_sites.json").bufferedReader().use { it.readText() }
            val root = VT5App.json.decodeFromString<GlobalMigrationSitesRoot>(jsonStr)
            cachedRoot = root
            root.sites
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Zoekt een specifieke internationale referentie-telpost op basis van siteId.
     */
    suspend fun getGlobalSiteById(context: Context, siteId: String): GlobalMigrationSite? {
        return getAllGlobalSites(context).find { it.siteId == siteId || it.name == siteId }
    }

    /**
     * Vindt de dichtstbijzijnde internationale referentie-telpost via GPS-coördinaten.
     */
    suspend fun findClosestGlobalSite(context: Context, lat: Double, lon: Double): GlobalMigrationSite? {
        val sites = getAllGlobalSites(context)
        return sites.minByOrNull { BsiClusterManager.calculateDistanceKm(lat, lon, it.latitude, it.longitude) }
    }
}
