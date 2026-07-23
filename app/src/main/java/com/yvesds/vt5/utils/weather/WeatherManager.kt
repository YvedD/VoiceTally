package com.yvesds.vt5.utils.weather

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Weer-helper: last known location, fetchen van huidige weerdata (Open-Meteo),
 * en conversies voor UI (bewolking in achtsten, zicht in meters, enz.).
 */
object WeatherManager {
    private const val TAG = "WeatherManager"
    private val client by lazy { OkHttpClient() }
    private val json by lazy { Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true } }

    /** Haal 72-uurs voorspelling op (uurbasis). */
    suspend fun fetch72HourForecast(lat: Double, lon: Double): List<HourlyForecast>? = withContext(Dispatchers.IO) {
        // Open-Meteo API v1 parameters
        val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&hourly=temperature_2m,wind_speed_10m,wind_direction_10m" +
                "&wind_speed_unit=ms" +
                "&forecast_days=3" +
                "&timezone=auto"
        
        Log.d(TAG, "Fetching 72h forecast: $url")
        val req = Request.Builder().url(url).get().build()
        
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Forecast fetch failed: ${resp.code}")
                    return@use null
                }
                val body = resp.body?.string() ?: return@use null
                val wr = json.decodeFromString(WeatherResponse.serializer(), body)
                val h = wr.hourly ?: return@use null
                val times = h.time ?: return@use null
                
                val list = mutableListOf<HourlyForecast>()
                for (i in times.indices) {
                    list.add(HourlyForecast(
                        time = times[i],
                        temp = h.temperature2m?.getOrNull(i),
                        windSpeed = h.windSpeed10m?.getOrNull(i),
                        windDeg = h.windDirection10m?.getOrNull(i)
                    ))
                }
                list
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching 72h forecast: ${e.message}")
            null
        }
    }

    /** Helper class for hourly forecast entries. */
    data class HourlyForecast(
        val time: String,
        val temp: Double?,
        val windSpeed: Double?,
        val windDeg: Double?
    )

    /** Probeer snel een lastKnownLocation te pakken (NETWORK dan GPS). */
    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(ctx: Context): Location? = withContext(Dispatchers.IO) {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        var best: Location? = null
        for (p in providers) {
            try {
                val loc = lm.getLastKnownLocation(p)
                if (loc != null && (best == null || loc.time > best!!.time)) {
                    best = loc
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get location from $p: ${e.message}")
            }
        }
        if (best == null) {
            Log.w(TAG, "No last known location found from any provider")
        } else {
            Log.d(TAG, "Using last known location from ${best.provider}: ${best.latitude}, ${best.longitude}")
        }
        best
    }

    /** Haal 'current' set op bij Open-Meteo (m/s voor wind). */
    suspend fun fetchCurrent(lat: Double, lon: Double): Current? = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,wind_speed_10m,wind_direction_10m,cloud_cover,pressure_msl,visibility,precipitation" +
                "&wind_speed_unit=ms" +
                "&timezone=auto"
        
        Log.d(TAG, "Fetching current weather: $url")
        val req = Request.Builder().url(url).get().build()
        
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Current weather fetch failed: ${resp.code}")
                    return@use null
                }
                val body = resp.body?.string() ?: return@use null
                val wr = json.decodeFromString(WeatherResponse.serializer(), body)
                wr.current
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching current weather: ${e.message}")
            null
        }
    }

    /** Converteer m/s naar Beaufort 0..12 (klassieke tabel). */
    fun msToBeaufort(ms: Double?): Int {
        val v = ms ?: return 0
        // m/s bovengrenzen per Beaufort
        val thresholds = listOf(0.2, 1.5, 3.3, 5.4, 7.9, 10.7, 13.8, 17.1, 20.7, 24.4, 28.4, 32.6)
        for ((i, t) in thresholds.withIndex()) if (v <= t) return i
        return 12
    }

    /** Converteer graden naar 16-windroos label. */
    fun degTo16WindLabel(deg: Double?): String {
        if (deg == null) return "N"
        val labels = arrayOf("N","NNO","NO","ONO","O","OZO","ZO","ZZO","Z","ZZW","ZW","WZW","W","WNW","NW","NNW")
        val idx = floor(((deg + 11.25) % 360.0) / 22.5).toInt()
        return labels[idx.coerceIn(0, labels.lastIndex)]
    }

    /** Converteer cloud cover (%) → achtsten ("0".."8"). */
    fun cloudPercentToAchtsten(pct: Double?): String {
        val p = ((pct ?: 0.0).coerceIn(0.0, 100.0))
        val achtsten = ((p / 100.0) * 8.0).roundToInt().coerceIn(0, 8)
        return achtsten.toString()
    }

    /**
     * Normaliseer zichtbaarheid naar METERS (Int, geen decimalen).
     */
    fun toVisibilityMeters(value: Double?): Int? {
        value ?: return null
        return if (value < 1000.0) {
            (value * 1000.0).roundToInt()
        } else {
            value.roundToInt()
        }
    }

    /** Simple logic voor neerslag-type o.b.v. intensiteit; fallback "geen". */
    fun precipitationToCode(precipMm: Double?): String {
        val v = (precipMm ?: 0.0)
        return when {
            v < 0.05 -> "geen"
            v < 0.5  -> "motregen"
            else     -> "regen"
        }
    }
}
