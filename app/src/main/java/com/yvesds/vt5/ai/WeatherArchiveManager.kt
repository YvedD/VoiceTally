package com.yvesds.vt5.ai

import android.content.Context
import android.util.Log
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.entities.WeatherArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * WeatherArchiveManager - Beheert de historische weersgegevens in de Room database.
 * Gebruikt de Open-Meteo Archive API voor bulk-downloads.
 */
class WeatherArchiveManager(private val context: Context) {
    private val TAG = "WeatherArchiveManager"
    private val client = OkHttpClient()
    private val db = VoiceTallyDatabase.getDatabase(context)

    /**
     * Zorgt dat de weersgegevens voor een specifieke telpost en jaar aanwezig zijn in de DB.
     * Downloadt per jaar indien ontbrekend.
     */
    suspend fun ensureYearArchive(year: Int, lat: Double, lon: Double, locationId: String, onProgress: (String) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        val uniqueLocationYear = "${locationId}_$year"
        
        // "Slimme" check: We gebruiken SharedPreferences om bij te houden welke blokken 100% voltooid zijn.
        val progressPrefs = context.getSharedPreferences("weather_archive_progress", Context.MODE_PRIVATE)
        if (progressPrefs.getBoolean("done_$uniqueLocationYear", false)) {
            onProgress("Overslaan: $locationId ($year) reeds gedownload")
            return@withContext true
        }

        // Dubbele check in DB voor de zekerheid
        val countInDb = db.tellingDao().countWeatherForLocation(uniqueLocationYear)
        if (countInDb >= 8000) {
            onProgress("Herstellen vlag: $locationId ($year) aanwezig in database")
            progressPrefs.edit().putBoolean("done_$uniqueLocationYear", true).apply()
            return@withContext true
        }

        onProgress("Downloaden weer: $locationId ($year)...")
        Log.i(TAG, "Downloading bulk weather for $uniqueLocationYear ($lat, $lon)...")
        
        // De geoptimaliseerde URL van de gebruiker (met CERRA model en best_match)
        val url = "https://archive-api.open-meteo.com/v1/archive" +
                "?latitude=$lat&longitude=$lon" +
                "&start_date=$year-01-01&end_date=$year-12-31" +
                "&hourly=temperature_2m,rain,weather_code,pressure_msl,cloud_cover,wind_speed_10m,wind_direction_10m,wind_speed_100m,wind_direction_100m,wind_gusts_10m" +
                "&models=cerra,best_match" +
                "&wind_speed_unit=ms" +
                "&timezone=UTC"

        try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(TAG, "Open-Meteo API Error: ${resp.code}")
                    return@withContext false
                }
                val body = resp.body?.string() ?: return@withContext false
                val root = JSONObject(body)
                val hourly = root.getJSONObject("hourly")
                val times = hourly.getJSONArray("time")
                
                val temps = hourly.getJSONArray("temperature_2m")
                val rains = hourly.getJSONArray("rain")
                val codes = hourly.getJSONArray("weather_code")
                val pressures = hourly.getJSONArray("pressure_msl")
                val clouds = hourly.getJSONArray("cloud_cover")
                val winds10 = hourly.getJSONArray("wind_speed_10m")
                val dirs10 = hourly.getJSONArray("wind_direction_10m")
                val winds100 = hourly.getJSONArray("wind_speed_100m")
                val dirs100 = hourly.getJSONArray("wind_direction_100m")
                val gusts = hourly.getJSONArray("wind_gusts_10m")

                val entities = mutableListOf<WeatherArchive>()
                for (i in 0 until times.length()) {
                    val timeStr = times.getString(i)
                    val epoch = Instant.parse("${timeStr}:00Z").epochSecond
                    
                    entities.add(WeatherArchive(
                        locationId = uniqueLocationYear,
                        timeEpoch = epoch,
                        temp = temps.optDouble(i),
                        windSpeed10m = winds10.optDouble(i),
                        windDir10m = dirs10.optDouble(i),
                        windSpeed100m = winds100.optDouble(i),
                        windDir100m = dirs100.optDouble(i),
                        windGusts10m = gusts.optDouble(i),
                        pressureMsl = pressures.optDouble(i),
                        cloudCover = clouds.optDouble(i),
                        precip = rains.optDouble(i),
                        weatherCode = codes.optInt(i)
                    ))
                    
                    // Batch insert per 500 om geheugen te sparen
                    if (entities.size >= 500) {
                        db.tellingDao().insertWeatherArchive(entities)
                        entities.clear()
                    }
                }
                
                if (entities.isNotEmpty()) {
                    db.tellingDao().insertWeatherArchive(entities)
                }
                
                // Markeer als voltooid in SharedPreferences
                progressPrefs.edit().putBoolean("done_$uniqueLocationYear", true).apply()
                
                Log.i(TAG, "Imported ${times.length()} weather hours for $uniqueLocationYear")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed bulk weather download: ${e.message}")
            false
        }
    }

    /**
     * Haalt weersgegevens op voor een specifiek moment uit de Room database.
     */
    suspend fun getWeatherFromDb(epoch: Long, locationId: String): WeatherArchive? = withContext(Dispatchers.IO) {
        val zdt = Instant.ofEpochSecond(epoch).atZone(ZoneId.of("UTC"))
        val year = zdt.year
        val hourEpoch = (epoch / 3600) * 3600 // Afronden naar heel uur
        
        val uniqueLocationYear = "${locationId}_$year"
        db.tellingDao().getWeather(uniqueLocationYear, hourEpoch)
    }
}
