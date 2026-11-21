package com.yvesds.vt5.core.database.entities

import androidx.room.Entity

/**
 * SpeciesPhenologyVault - Wetenschappelijke kluis voor trek-curves en piek-analyses.
 * Slaat 366-daagse reeksen op per soort/cluster voor turbo-weergave.
 */
@Entity(
    tableName = "species_phenology_vault",
    primaryKeys = ["speciesId", "clusterId"]
)
data class SpeciesPhenologyVault(
    val speciesId: String,
    val clusterId: String,
    val dailyBphSeries: String, // String van 366 floats gescheiden door |
    val peakSpring: String,     // Tekstuele datum (bijv. "15 april")
    val peakAutumn: String      // Tekstuele datum (bijv. "20 oktober")
) {
    fun getBphForDay(dayOfYear: Int): Float {
        val series = dailyBphSeries.split('|')
        val idx = (dayOfYear - 1).coerceIn(0, (series.size - 1).coerceAtLeast(0))
        return series.getOrNull(idx)?.toFloatOrNull() ?: 0f
    }
}
