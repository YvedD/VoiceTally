package com.yvesds.vt5.ai

import android.util.Log
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object YrProvider {
    private val TAG = "YrProvider"
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetch locationforecast compact from api.met.no and return raw JSON string.
     * The met.no API requires a proper User-Agent header.
     */
    fun fetchLocationForecast(lat: Double, lon: Double): String? {
        val url = "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=$lat&lon=$lon"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "VoiceTally/1.0 (contact: yves@example.com)")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "met.no request failed: ${resp.code}")
                return null
            }
            return resp.body?.string()
        }
    }

    /**
     * Extract a simple aggregated metric (avg wind_speed and avg pressure) from the compact forecast JSON
     * by scanning timeseries entries for the last N days. This is a light heuristic parser.
     */
    fun aggregateRecentFromForecastJson(jsonStr: String?, days: Int = 3): Pair<Double?, Double?> {
        if (jsonStr == null) return Pair(null, null)
        try {
            val now = OffsetDateTime.now()
            val cutoff = now.minusDays(days.toLong())

            // crude substring search for "wind_speed" and "pressure_msl" values near time tags
            // For robustness, a proper JSON parse is recommended; here we use simple heuristics to avoid heavy deps.
            val wsPattern = "\"wind_speed\":"
            val pPattern = "\"pressure_msl\":"
            val wsVals = mutableListOf<Double>()
            val pVals = mutableListOf<Double>()

            var idx = 0
            while (true) {
                val i = jsonStr.indexOf(wsPattern, idx)
                if (i < 0) break
                val sub = jsonStr.substring(i + wsPattern.length)
                val num = sub.trimStart().split(',',':','}','\n')[0].replace("\"","").trim()
                val v = num.toDoubleOrNull()
                if (v != null) wsVals.add(v)
                idx = i + 1
            }

            idx = 0
            while (true) {
                val i = jsonStr.indexOf(pPattern, idx)
                if (i < 0) break
                val sub = jsonStr.substring(i + pPattern.length)
                val num = sub.trimStart().split(',',':','}','\n')[0].replace("\"","").trim()
                val v = num.toDoubleOrNull()
                if (v != null) pVals.add(v)
                idx = i + 1
            }

            val avgWs = if (wsVals.isNotEmpty()) wsVals.average() else null
            val avgP = if (pVals.isNotEmpty()) pVals.average() else null
            return Pair(avgWs, avgP)
        } catch (e: Exception) {
            Log.w(TAG, "aggregateRecentFromForecastJson failed: ${e.message}")
            return Pair(null, null)
        }
    }
}

