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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar
import kotlin.math.*

/**
 * WeatherEnrichmentManager - Deskundige weermotor voor het archief.
 * Garandeert uur-voor-uur data voor de volledige duur van elke telsessie.
 * Nu met geoptimaliseerde batch-reconstructie en agressieve gatenvulling.
 */
class WeatherEnrichmentManager(context: Context) {
    private val TAG = "WeatherEnrichment"
    private val db = VoiceTallyDatabase.getDatabase(context)
    private val saf = SaFStorageHelper(context)

    suspend fun performEnrichment(onProgress: (String, Int, Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Scan de database op telpost+jaar combinaties
            val allActive = db.tellingDao().getAllTelpostYears()
            if (allActive.isEmpty()) return@withContext true

            val locatiesJson = saf.readServerDataFile("telpost_locaties.json") ?: "{}"
            val locRoot = try { VT5App.json.decodeFromString<TelpostLocatiesRoot>(locatiesJson) } catch (_: Exception) { TelpostLocatiesRoot() }
            val locMap = locRoot.locaties.associateBy { it.telpostid }

            // 2. Cluster telposten (35km)
            val clusters = clusterTelpostYears(allActive, locMap)
            
            // 3. Vul het weer-archief
            var current = 0
            val activeYears = allActive.map { it.year }.distinct()
            val totalSteps = clusters.size * activeYears.size
            
            clusters.forEach { (clusterKey, years) ->
                val (telpostId, lat, lon) = clusterKey
                years.forEach { year ->
                    current++
                    fetchAndStoreYearlyWeather(telpostId, lat, lon, year, onProgress, current, totalSteps)
                }
            }

            // 4. Haal corridor-data op
            val corridorPoints = com.yvesds.vt5.ai.AiConfig.REFERENCE_POINTS
            var corrStep = 0
            val totalCorrSteps = activeYears.size * corridorPoints.size
            
            activeYears.forEach { year ->
                corridorPoints.forEach { point ->
                    corrStep++
                    onProgress("Corridor-data indexeren: ${point.name} ($year)...", corrStep, totalCorrSteps)
                    fetchAndStoreYearlyWeather(point.name, point.lat, point.lon, year, onProgress, corrStep, totalCorrSteps)
                }
            }

            // 5. De Agressieve Gatenvuller: vul alle ontbrekende velden in de headers aan
            onProgress("Database records aanvullen met meteorologische context...", 0, 100)
            enrichHeadersFromArchive(onProgress)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Enrichment failed: ${e.message}")
            false
        }
    }

    private suspend fun fetchAndStoreYearlyWeather(
        telpostId: String, lat: Double, lon: Double, year: String,
        onProgress: (String, Int, Int) -> Unit, currentStep: Int, totalSteps: Int
    ) {
        val existingCount = db.tellingDao().countWeatherForLocationInYear(telpostId, year)
        // Voor het huidige jaar halen we altijd de laatste data op (beperkt tot gisteren)
        if (existingCount >= 8700 && year != LocalDate.now().year.toString()) {
            onProgress("Reeds in weer-archief: $year ($telpostId)", currentStep, totalSteps)
            return
        }

        val startDate = "$year-01-01"
        val endDate = if (year == LocalDate.now().year.toString()) LocalDate.now().minusDays(2).toString() else "$year-12-31"
        
        onProgress("Downloaden weer-gegevens $year ($telpostId)...", currentStep, totalSteps)
        val url = "https://archive-api.open-meteo.com/v1/archive?latitude=$lat&longitude=$lon&start_date=$startDate&end_date=$endDate" +
                  "&hourly=temperature_2m,wind_speed_10m,wind_direction_10m,cloud_cover,pressure_msl,precipitation&wind_speed_unit=ms&timezone=UTC"

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
                    val epoch = LocalDateTime.parse(hourly.time[i], formatter).toEpochSecond(ZoneOffset.UTC)
                    archiveRecords.add(WeatherArchive(
                        locationId = telpostId, timeEpoch = epoch,
                        temp = hourly.temperature_2m.getOrNull(i), windSpeed10m = hourly.wind_speed_10m.getOrNull(i),
                        windDir10m = hourly.wind_direction_10m.getOrNull(i), cloudCover = hourly.cloud_cover.getOrNull(i),
                        pressureMsl = hourly.pressure_msl.getOrNull(i), precip = hourly.precipitation.getOrNull(i)
                    ))
                    if (archiveRecords.size >= 1000) {
                        db.tellingDao().insertWeatherArchiveIgnore(archiveRecords)
                        archiveRecords.clear()
                    }
                }
                if (archiveRecords.isNotEmpty()) db.tellingDao().insertWeatherArchiveIgnore(archiveRecords)
            }
        } catch (e: Exception) { Log.e(TAG, "Error: ${e.message}") }
    }

    private suspend fun enrichHeadersFromArchive(onProgress: (String, Int, Int) -> Unit) {
        // Optimalisatie: Haal alleen de headers met gaten op
        val headers = db.tellingDao().getHeadersWithGaps()
        val total = headers.size
        if (total == 0) return

        val locatiesJson = saf.readServerDataFile("telpost_locaties.json") ?: "{}"
        val locRoot = try { VT5App.json.decodeFromString<TelpostLocatiesRoot>(locatiesJson) } catch (_: Exception) { TelpostLocatiesRoot() }
        val locMap = locRoot.locaties.associateBy { it.telpostid }
        val updates = mutableListOf<com.yvesds.vt5.core.database.entities.TellingHeader>()

        headers.forEachIndexed { index, h ->
            if (index % 50 == 0) {
                onProgress("Weergegevens aanvullen: ${index+1}/$total...", index + 1, total)
                kotlinx.coroutines.yield()
            }
            
            val loc = locMap[h.telpostid] ?: return@forEachIndexed
            val archiveLocationId = findArchiveLocationId(loc.latitude, loc.longitude) ?: return@forEachIndexed
            val startTimeSec = h.begintijd.toLongOrNull() ?: return@forEachIndexed
            
            // AFRONDING NAAR DICHTSTBIJZIJNDE UUR: 
            // We tellen 1800 seconden (30 min) op voor de deling door 3600
            val epoch = ((startTimeSec + 1800) / 3600) * 3600
            
            val w = db.tellingDao().getWeather(archiveLocationId, epoch)
            if (w != null) {
                // Agressieve aanvulling met LOWERCASE windrichting
                val updated = h.copy(
                    windrichting = if (isFieldMissing(h.windrichting)) WeatherManager.degTo16WindLabel(w.windDir10m).lowercase() else h.windrichting,
                    windkracht = if (isFieldMissing(h.windkracht)) WeatherManager.msToBeaufort(w.windSpeed10m).toString() else h.windkracht,
                    temperatuur = if (isFieldMissing(h.temperatuur)) w.temp?.roundToInt()?.toString() ?: "" else h.temperatuur,
                    bewolking = if (isFieldMissing(h.bewolking)) WeatherManager.cloudPercentToAchtsten(w.cloudCover) else h.bewolking,
                    neerslag = if (isFieldMissing(h.neerslag)) WeatherManager.precipitationToCode(w.precip) else h.neerslag,
                    hpa = if (isFieldMissing(h.hpa)) w.pressureMsl?.roundToInt()?.toString() ?: "" else h.hpa
                )
                if (updated != h) updates.add(updated)
            }
            
            if (updates.size >= 100) { 
                db.tellingDao().updateHeaders(updates); updates.clear(); delay(1) 
            }
        }
        if (updates.isNotEmpty()) db.tellingDao().updateHeaders(updates)
    }

    private fun isFieldMissing(v: String?): Boolean = v.isNullOrBlank() || v.trim().lowercase() in listOf("null", "0", "nan", "onbekend", "-", "?", "nan")

    private fun clusterTelpostYears(all: List<com.yvesds.vt5.core.database.dao.TelpostYear>, map: Map<String, com.yvesds.vt5.core.database.entities.TelpostLocatie>): Map<Triple<String, Double, Double>, Set<String>> {
        val res = mutableMapOf<Triple<String, Double, Double>, MutableSet<String>>()
        all.forEach { (tid, year) ->
            val loc = map[tid] ?: return@forEach
            val ex = res.keys.find { calculateDistance(loc.latitude, loc.longitude, it.second, it.third) <= 35.0 }
            if (ex != null) res[ex]?.add(year) else res[Triple(tid, loc.latitude, loc.longitude)] = mutableSetOf(year)
        }
        return res
    }

    private suspend fun findArchiveLocationId(lat: Double, lon: Double): String? {
        val locations = db.tellingDao().getWeatherAvailableLocations()
        val locatiesJson = saf.readServerDataFile("telpost_locaties.json") ?: "{}"
        val locMap = try { VT5App.json.decodeFromString<TelpostLocatiesRoot>(locatiesJson).locaties.associateBy { it.telpostid } } catch (_: Exception) { emptyMap() }
        return locations.find { id ->
            val loc = locMap[id] ?: return@find false
            calculateDistance(lat, lon, loc.latitude, lon) <= 35.0
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val a = sin(Math.toRadians(lat2 - lat1) / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(Math.toRadians(lon2 - lon1) / 2).pow(2)
        return 6371.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    @Serializable
    private data class OpenMeteoArchiveResponse(val hourly: OpenMeteoHourlyData)
    @Serializable
    private data class OpenMeteoHourlyData(val time: List<String>, val temperature_2m: List<Double?>, val wind_speed_10m: List<Double?>, val wind_direction_10m: List<Double?>, val cloud_cover: List<Double?>, val pressure_msl: List<Double?>, val precipitation: List<Double?>)
}
