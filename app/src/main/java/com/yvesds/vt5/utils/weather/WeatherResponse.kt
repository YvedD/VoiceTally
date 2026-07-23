package com.yvesds.vt5.utils.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * We houden alle numerieke velden als Double? omdat de API vaak decimalen
 * terugstuurt (bv. 15220.00 voor visibility). In de UI ronden we af.
 */
@Serializable
data class WeatherResponse(
    @SerialName("current") val current: Current? = null,
    @SerialName("hourly") val hourly: Hourly? = null
)

@Serializable
data class Current(
    // temperatuur in °C
    @SerialName("temperature_2m") val temperature2m: Double? = null,

    // windrichting in graden (0..360)
    @SerialName("wind_direction_10m") val windDirection10m: Double? = null,

    // windsnelheid in m/s
    @SerialName("wind_speed_10m") val windSpeed10m: Double? = null,

    // windstoten
    @SerialName("wind_gusts_10m") val windGusts10m: Double? = null,

    // bewolking in % (0..100)
    @SerialName("cloud_cover") val cloudCover: Double? = null,

    // luchtdruk op zeeniveau (hPa)
    @SerialName("pressure_msl") val pressureMsl: Double? = null,

    // zicht (meters)
    @SerialName("visibility") val visibility: Double? = null,

    // neerslag (mm)
    @SerialName("precipitation") val precipitation: Double? = null
)

@Serializable
data class Hourly(
    @SerialName("time") val time: List<String>? = null,
    @SerialName("temperature_2m") val temperature2m: List<Double?>? = null,
    @SerialName("wind_speed_10m") val windSpeed10m: List<Double?>? = null,
    @SerialName("wind_direction_10m") val windDirection10m: List<Double?>? = null
)
