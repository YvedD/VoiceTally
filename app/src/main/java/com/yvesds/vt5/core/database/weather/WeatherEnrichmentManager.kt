package com.yvesds.vt5.core.database.weather

import android.content.Context
import android.util.Log
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.entities.TelpostLocatiesRoot
import com.yvesds.vt5.core.database.entities.WeatherArchive
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.utils.weather.WeatherManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.Request
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.*

/**
 * WeatherEnrichmentManager - Beheert het vullen van het weer-archief en het verrijken
 * van telling-headers met historische weergegevens.
 */
class WeatherEnrichmentManager(context: Context) {
    private val TAG = "WeatherEnrichment"
    private val db = VoiceTallyDatabase.getDatabase(context)
    private val saf = SaFStorageHelper(context)

    /**
     * Start het volledige verrijkingsproces.
     */
    suspend fun performEnrichment(onProgress: (String, Int, Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Zoek tellingen met ontbrekend weer
            val missing = db.tellingDao().getMissingWeatherTelpostYears()
            if (missing.isEmpty()) {
                Log.i(TAG, "Geen tellingen gevonden met ontbrekend weer.")
                return@withContext true
            }

            // 2. Laad telpost locaties
            val locatiesJson = saf.readServerDataFile("telpost_locaties.json") ?: "{}"
            val locRoot = try { 
                VT5App.json.decodeFromString<TelpostLocatiesRoot>(locatiesJson) 
            } catch (e: Exception) { 
                TelpostLocatiesRoot() 
            }
            val locMap = locRoot.locaties.associateBy { it.telpostid }

            // 3. Cluster telposten (35km regel)
            val clusters = clusterTelpostYears(missing, locMap)
            
            if (clusters.isEmpty()) {
                Log.w(TAG, "Geen locaties gevonden voor telposten die verrijkt moeten worden.")
                // We kunnen hier niet verder zonder coördinaten
                onProgress("Fout: Geen GPS coördinaten bekend voor telposten in deze import.", 0, 0)
                delay(3000)
                return@withContext false
            }
            
            // 4. Haal weergegevens op per cluster/jaar
            var current = 0
            val total = clusters.size
            
            clusters.forEach { (clusterKey, years) ->
                val (telpostId, lat, lon) = clusterKey
                years.forEach { year ->
                    current++
                    onProgress("Weer ophalen voor $telpostId ($year)...", current, total)
                    fetchAndStoreYearlyWeather(telpostId, lat, lon, year)
                }
            }

            // 5. Vul de headers aan vanuit het archief
            onProgress("Database records aanvullen...", total, total)
            enrichHeadersFromArchive()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Enrichment failed: ${e.message}", e)
            false
        }
    }

    /**
     * Groepeert combinaties van telpost/jaar op basis van geografische nabijheid.
     */
    private fun clusterTelpostYears(
        missing: List<com.yvesds.vt5.core.database.dao.TelpostYear>,
        locMap: Map<String, com.yvesds.vt5.core.database.entities.TelpostLocatie>
    ): Map<Triple<String, Double, Double>, Set<String>> {
        val clusters = mutableMapOf<Triple<String, Double, Double>, MutableSet<String>>()
        
        missing.forEach { (tid, year) ->
            val loc = locMap[tid] ?: return@forEach
            
            // Zoek bestaande cluster binnen 35km
            val existingCluster = clusters.keys.find { (_, clat, clon) ->
                calculateDistance(loc.latitude, loc.longitude, clat, clon) <= 35.0
            }

            if (existingCluster != null) {
                clusters[existingCluster]?.add(year)
            } else {
                clusters[Triple(tid, loc.latitude, loc.longitude)] = mutableSetOf(year)
            }
        }
        return clusters
    }

    /**
     * Haalt 1 jaar aan uurgegevens op en slaat deze op in Room.
     */
    private suspend fun fetchAndStoreYearlyWeather(telpostId: String, lat: Double, lon: Double, year: String) {
        val startDate = "$year-01-01"
        val endDate = "$year-12-31"
        
        val url = "https://archive-api.open-meteo.com/v1/archive" +
                "?latitude=$lat&longitude=$lon" +
                "&start_date=$startDate&end_date=$endDate" +
                "&hourly=temperature_2m,wind_speed_10m,wind_direction_10m,cloud_cover,pressure_msl,precipitation" +
                "&wind_speed_unit=ms" +
                "&timezone=UTC"

        try {
            val req = Request.Builder().url(url).build()
            VT5App.http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return
                val body = resp.body?.string() ?: return
                val data = VT5App.json.decodeFromString<OpenMeteoArchiveResponse>(body)
                
                val archiveRecords = mutableListOf<WeatherArchive>()
                val hourly = data.hourly
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

                for (i in hourly.time.indices) {
                    val timeStr = hourly.time[i]
                    val epoch = LocalDateTime.parse(timeStr, formatter).toEpochSecond(ZoneOffset.UTC)
                    
                    archiveRecords.add(WeatherArchive(
                        locationId = telpostId, // We slaan op onder de ID van de cluster-head
                        timeEpoch = epoch,
                        temp = hourly.temperature_2m.getOrNull(i),
                        windSpeed10m = hourly.wind_speed_10m.getOrNull(i),
                        windDir10m = hourly.wind_direction_10m.getOrNull(i),
                        cloudCover = hourly.cloud_cover.getOrNull(i),
                        pressureMsl = hourly.pressure_msl.getOrNull(i),
                        precip = hourly.precipitation.getOrNull(i)
                    ))
                    
                    if (archiveRecords.size >= 500) {
                        db.tellingDao().insertWeatherArchiveIgnore(archiveRecords)
                        archiveRecords.clear()
                    }
                }
                if (archiveRecords.isNotEmpty()) {
                    db.tellingDao().insertWeatherArchiveIgnore(archiveRecords)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching weather for $telpostId: ${e.message}")
        }
    }

    /**
     * Doorloopt alle headers met missend weer en vult deze aan vanuit het archief.
     */
    private suspend fun enrichHeadersFromArchive() {
        val headers = db.tellingDao().getHeadersWithMissingWeather()
        val locatiesJson = saf.readServerDataFile("telpost_locaties.json") ?: "{}"
        val locRoot = try { VT5App.json.decodeFromString<TelpostLocatiesRoot>(locatiesJson) } catch (e: Exception) { TelpostLocatiesRoot() }
        val locMap = locRoot.locaties.associateBy { it.telpostid }

        headers.forEach { h ->
            val loc = locMap[h.telpostid] ?: return@forEach
            
            // Vind de juiste cluster (dezelfde 35km logica)
            // In een productie-omgeving zouden we de cluster-id in een cache bewaren,
            // voor nu zoeken we de 'locationId' die in het archief staat.
            val locationIdInArchive = findArchiveLocationId(loc.latitude, loc.longitude) ?: return@forEach
            
            val epoch = h.begintijd.toLongOrNull() ?: return@forEach
            // Afronden naar dichtstbijzijnde hele uur voor matching met archief
            val hourlyEpoch = (epoch / 3600) * 3600
            
            val weather = db.tellingDao().getWeather(locationIdInArchive, hourlyEpoch)
            if (weather != null) {
                val updatedHeader = h.copy(
                    windrichting = if (isFieldMissing(h.windrichting)) WeatherManager.degTo16WindLabel(weather.windDir10m) else h.windrichting,
                    windkracht = if (isFieldMissing(h.windkracht)) WeatherManager.msToBeaufort(weather.windSpeed10m).toString() else h.windkracht,
                    temperatuur = if (isFieldMissing(h.temperatuur)) weather.temp?.roundToInt()?.toString() ?: "" else h.temperatuur,
                    bewolking = if (isFieldMissing(h.bewolking)) WeatherManager.cloudPercentToAchtsten(weather.cloudCover) else h.bewolking,
                    neerslag = if (isFieldMissing(h.neerslag)) WeatherManager.precipitationToCode(weather.precip) else h.neerslag,
                    hpa = if (isFieldMissing(h.hpa)) weather.pressureMsl?.roundToInt()?.toString() ?: "" else h.hpa
                )
                db.tellingDao().updateHeader(updatedHeader)
            }
        }
    }

    private fun isFieldMissing(value: String): Boolean {
        val t = value.trim().lowercase()
        return t == "" || t == "null" || t == "0"
    }

    /**
     * Zoekt welke locationId in het archief gebruikt is voor deze coordinaten (binnen 35km).
     */
    private suspend fun findArchiveLocationId(lat: Double, lon: Double): String? {
        val locations = db.tellingDao().getWeatherAvailableLocations()
        val locatiesJson = saf.readServerDataFile("telpost_locaties.json") ?: "{}"
        val locMap = try { VT5App.json.decodeFromString<TelpostLocatiesRoot>(locatiesJson).locaties.associateBy { it.telpostid } } catch (e: Exception) { emptyMap() }

        return locations.find { id ->
            val loc = locMap[id] ?: return@find false
            calculateDistance(lat, lon, loc.latitude, loc.longitude) <= 35.0
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    @Serializable
    private data class OpenMeteoArchiveResponse(val hourly: OpenMeteoHourlyData)

    @Serializable
    private data class OpenMeteoHourlyData(
        val time: List<String>,
        val temperature_2m: List<Double?>,
        val wind_speed_10m: List<Double?>,
        val wind_direction_10m: List<Double?>,
        val cloud_cover: List<Double?>,
        val pressure_msl: List<Double?>,
        val precipitation: List<Double?>
    )
}
