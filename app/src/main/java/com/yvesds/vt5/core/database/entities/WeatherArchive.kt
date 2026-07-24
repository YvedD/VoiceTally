package com.yvesds.vt5.core.database.entities

import androidx.room.Entity
import androidx.room.Index

/**
 * WeatherArchive: Slaat historische uurlijkse weersgegevens op voor telposten en referentiepunten.
 * Gebruikt om API credits te sparen en bliksemsnelle AI-training mogelijk te maken.
 */
@Entity(
    tableName = "weather_archive",
    primaryKeys = ["locationId", "timeEpoch"],
    indices = [Index(value = ["timeEpoch"])]
)
data class WeatherArchive(
    val locationId: String, // bijv. "site_74" of "ref_spring_0"
    val timeEpoch: Long,    // UTC epoch seconde (uur-precisie)
    val temp: Double?,
    val windSpeed10m: Double?,
    val windDir10m: Double?,
    val windSpeed100m: Double?,
    val windDir100m: Double?,
    val windGusts10m: Double?,
    val pressureMsl: Double?,
    val cloudCover: Double?,
    val precip: Double?,
    val weatherCode: Int?
)
