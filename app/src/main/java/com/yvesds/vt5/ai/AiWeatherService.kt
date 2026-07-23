package com.yvesds.vt5.ai

import android.content.Context
import android.util.Log
import com.yvesds.vt5.utils.weather.WeatherManager
import com.yvesds.vt5.utils.weather.WeatherResponse
import com.yvesds.vt5.ai.YrProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WeatherContext(
    val lat: Double,
    val lon: Double,
    val temp: Double?,
    val windSpeed: Double?,
    val windDeg: Double?,
    val cloudPercent: Double?,
    val pressure: Double?,
    val visibility: Int?,
    val pressureTrend: Double? = null // Difference with 6h ago
)

object AiWeatherService {
    private val TAG = "AiWeatherService"

    suspend fun fetchContextualWeather(ctx: Context, lat: Double, lon: Double): WeatherContext? {
        try {
            val current = WeatherManager.fetchCurrent(lat, lon) ?: return null
            val trend = fetchPressureTrend(lat, lon)
            return WeatherContext(
                lat = lat,
                lon = lon,
                temp = current.temperature2m,
                windSpeed = current.windSpeed10m,
                windDeg = current.windDirection10m,
                cloudPercent = current.cloudCover,
                pressure = current.pressureMsl,
                visibility = WeatherManager.toVisibilityMeters(current.visibility),
                pressureTrend = trend
            )
        } catch (e: Exception) {
            Log.w(TAG, "fetchContextualWeather failed: ${e.message}")
            return null
        }
    }

    /** Returns current_pressure - pressure_6h_ago */
    private suspend fun fetchPressureTrend(lat: Double, lon: Double): Double? {
        return try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&hourly=pressure_msl&past_days=1&forecast_days=1"
            val req = okhttp3.Request.Builder().url(url).get().build()
            val client = okhttp3.OkHttpClient()
            val body = withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.newCall(req).execute().use { it.body?.string() }
            } ?: return null
            
            val pMatches = Regex(""""pressure_msl":\s*\[([^\]]+)\]""").find(body)?.groups?.get(1)?.value
            val values = pMatches?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() } ?: return null
            
            if (values.size < 24) return null
            
            // Assume the last element is approx 'now' (since past_days=1, forecast_days=1)
            // Or better, find the index for 'now' if we had timestamps. 
            // For simplicity, let's take the current value from the list and the one 6 indices back.
            val currentVal = values.last()
            val previousVal = values.getOrNull(values.size - 7) ?: values.first()
            currentVal - previousVal
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Try to get recent aggregated metrics (avg wind, avg pressure) using both Open-Meteo and Yr.no as fallback.
     */
    fun fetchRecentAggregates(lat: Double, lon: Double, days: Int = 3): Pair<Double?, Double?> {
        // Try YrProvider first
        val json = YrProvider.fetchLocationForecast(lat, lon)
        val yrAgg = YrProvider.aggregateRecentFromForecastJson(json, days)
        if (yrAgg.first != null || yrAgg.second != null) return yrAgg

        // Fallback: try Open-Meteo via synchronous HTTP call to the Open-Meteo historical endpoint
        try {
            // build URL with hourly params and past_days
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&hourly=temperature_2m,wind_speed_10m,pressure_msl&past_days=$days&timezone=UTC"
            val req = okhttp3.Request.Builder().url(url).get().build()
            okhttp3.OkHttpClient().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return Pair(null, null)
                val body = resp.body?.string() ?: return Pair(null, null)
                // crude parsing for numbers (use raw strings so backslashes aren't treated as Kotlin escapes)
                val wsMatches = Regex(""""wind_speed_10m":\s*\[([^\]]+)\]""").find(body)?.groups?.get(1)?.value
                val pMatches = Regex(""""pressure_msl":\s*\[([^\]]+)\]""").find(body)?.groups?.get(1)?.value
                val wsAvg = wsMatches?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() }?.average()
                val pAvg = pMatches?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() }?.average()
                return Pair(wsAvg, pAvg)
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchRecentAggregates fallback failed: ${e.message}")
        }

        return Pair(null, null)
    }
}

