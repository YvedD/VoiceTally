package com.yvesds.vt5.core.database.entities

import androidx.room.Entity
import androidx.room.Index

/**
 * WeatherArchive: Slaat historische uurlijkse weergegevens op voor specifieke locaties.
 * Geoptimaliseerd voor massa-opslag (4.3M records).
 * 
 * Primary Key is een combinatie van locationId en timeEpoch.
 */
@Entity(
    tableName = "weather_archive",
    primaryKeys = ["locationId", "timeEpoch"],
    indices = [
        Index(value = ["locationId"]),
        Index(value = ["timeEpoch"])
    ]
)
data class WeatherArchive(
    val locationId: String,       // Bijv. "grid_A1_universal" of "site_74"
    val timeEpoch: Long,          // Unix timestamp in seconden (Uurbasis)
    
    val temp: Double? = null,
    val windSpeed10m: Double? = null,
    val windDir10m: Double? = null,
    val windSpeed100m: Double? = null,
    val windDir100m: Double? = null,
    val windGusts10m: Double? = null,
    val pressureMsl: Double? = null,
    val cloudCover: Double? = null,
    val precip: Double? = null,
    val weatherCode: Int? = null
)
