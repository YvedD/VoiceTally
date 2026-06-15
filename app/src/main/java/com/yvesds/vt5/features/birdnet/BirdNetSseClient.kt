package com.yvesds.vt5.features.birdnet

import android.util.Log
import com.yvesds.vt5.VT5App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Client voor de BirdNET-GO SSE live-stream (`/api/v2/detections/stream`).
 *
 * - Leest de `text/event-stream`-respons regel per regel via OkHttp blocking I/O,
 *   uitgevoerd op Dispatchers.IO via [withContext].
 * - Parsed events: `connected`, `detection`, `heartbeat`, `pending`.
 * - Annuleert automatisch de OkHttp-call via `invokeOnCompletion` zodra de
 *   coroutine gecanceld wordt, waardoor de blokkerende `readUtf8Line()` vrijkomt.
 *
 * Gebruik (in een coroutine):
 * ```kotlin
 * BirdNetSseClient.streamEvents(config) { event ->
 *     when (event) {
 *         is SseEvent.Detection -> handleDetection(event.detection)
 *         is SseEvent.Heartbeat -> { /* keep-alive, negeer */ }
 *         else -> {}
 *     }
 * }
 * ```
 * De functie keert terug wanneer de verbinding wordt verbroken of de coroutine
 * gecanceld wordt. De aanroeper is verantwoordelijk voor reconnect-logica.
 */
object BirdNetSseClient {

    private const val TAG = "BirdNetSseClient"

    // ─── Event types ──────────────────────────────────────────────────────────

    sealed class SseEvent {
        /** Server bevestigt verbinding (eerste event na connect). */
        data object Connected : SseEvent()

        /** Nieuwe vogel gedetecteerd. */
        data class Detection(val detection: BirdNetDetection) : SseEvent()

        /** Snelle pending-detecties, nog vóór definitieve goedkeuring. */
        data class Pending(val detections: List<BirdNetPendingDetection>) : SseEvent()

        /** Keep-alive heartbeat (elke ~30 s). */
        data object Heartbeat : SseEvent()

        /** Verbindingsfout of parse-fout. */
        data class ConnectionError(val message: String) : SseEvent()
    }

    // ─── OkHttpClient ─────────────────────────────────────────────────────────

    /**
     * Dedicated OkHttpClient voor SSE met langere readTimeout.
     * De readTimeout moet > heartbeat-interval (30 s) zijn zodat de verbinding
     * niet vroegtijdig gesloten wordt bij een korte stilte.
     */
    private val sseHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS) // > heartbeat-interval (30 s)
            .retryOnConnectionFailure(false)   // reconnect-logica zit bij de aanroeper
            .build()
    }

    // ─── Publieke API ─────────────────────────────────────────────────────────

    /**
     * Verbind met de SSE-stream en verwerk events totdat de verbinding verbroken
     * wordt of de coroutine gecanceld wordt.
     *
     * @param config   BirdNET-GO hostconfiguratie (host, poort, protocol).
     * @param onEvent  callback die voor elk event geroepen wordt **op de IO-thread**;
     *                 gebruik [android.app.Activity.runOnUiThread] voor UI-updates.
     */
    suspend fun streamEvents(config: BirdNetConfig, onEvent: (SseEvent) -> Unit) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(config.streamUrl)
                .addHeader("Accept", "text/event-stream")
                .addHeader("Cache-Control", "no-cache")
                .addHeader("Connection", "keep-alive")
                .build()

            val call = sseHttp.newCall(request)

            // Annuleer OkHttp-call zodra de coroutine gecanceld wordt.
            // Dit zorgt ervoor dat de blokkerende readUtf8Line() een IOException
            // gooit en de lus eindigt, ook al is de thread bezet.
            coroutineContext.job.invokeOnCompletion {
                call.cancel()
            }

            val response = try {
                call.execute()
            } catch (e: Exception) {
                if (isActive) {
                    Log.w(TAG, "Verbinding mislukt: ${e.message}")
                    onEvent(SseEvent.ConnectionError(e.message ?: "Verbindingsfout"))
                }
                return@withContext
            }

            response.use {
                val body = response.body
                if (body == null) {
                    Log.w(TAG, "Lege respons van server")
                    onEvent(SseEvent.ConnectionError("Lege respons van server"))
                    return@use
                }

                val source = body.source()
                var currentEventType = ""
                val dataBuffer = StringBuilder()

                try {
                    while (isActive) {
                        val line = source.readUtf8Line() ?: break  // EOF / verbinding gesloten

                        when {
                            line.startsWith("event:") -> {
                                currentEventType = line.removePrefix("event:").trim()
                            }
                            line.startsWith("data:") -> {
                                dataBuffer.append(line.removePrefix("data:").trim())
                            }
                            line.isEmpty() -> {
                                // Lege regel = eventgrens
                                if (dataBuffer.isNotEmpty()) {
                                    dispatchEvent(currentEventType, dataBuffer.toString(), onEvent)
                                    dataBuffer.clear()
                                }
                                currentEventType = ""
                            }
                            // `id:` en `retry:` velden worden genegeerd
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        Log.w(TAG, "Stream-fout: ${e.message}")
                        onEvent(SseEvent.ConnectionError(e.message ?: "Stream-fout"))
                    }
                }
            }
        }
    }

    // ─── Interne helpers ──────────────────────────────────────────────────────

    private fun dispatchEvent(eventType: String, data: String, onEvent: (SseEvent) -> Unit) {
        when (eventType) {
            "connected" -> {
                Log.d(TAG, "SSE verbonden: $data")
                onEvent(SseEvent.Connected)
            }
            "detection" -> {
                parseDetection(data)?.let { onEvent(SseEvent.Detection(it)) }
            }
            "heartbeat" -> {
                Log.v(TAG, "SSE heartbeat ontvangen")
                onEvent(SseEvent.Heartbeat)
            }
            "pending" -> {
                onEvent(SseEvent.Pending(parsePendingDetections(data)))
            }
            else -> {
                Log.d(TAG, "Onbekend SSE-eventtype '$eventType': $data")
            }
        }
    }

    /** Parse ruwe JSON van een SSE detection-event naar [BirdNetDetection]. */
    private fun parseDetection(json: String): BirdNetDetection? {
        return try {
            VT5App.json.decodeFromString(BirdNetDetection.serializer(), json)
        } catch (e: Exception) {
            Log.w(TAG, "Kan detectie niet parsen: ${e.message}")
            Log.d(TAG, "Ruwe JSON: $json")
            null
        }
    }

    private fun parsePendingDetections(json: String): List<BirdNetPendingDetection> {
        return try {
            val root = VT5App.json.parseToJsonElement(json)
            root.extractPendingDetections()
        } catch (e: Exception) {
            Log.w(TAG, "Kan pending-detecties niet parsen: ${e.message}")
            Log.d(TAG, "Ruwe pending JSON: $json")
            emptyList()
        }
    }

    private fun JsonElement.extractPendingDetections(): List<BirdNetPendingDetection> {
        return when (this) {
            is JsonArray -> mapNotNull { it.toPendingDetectionOrNull() }
            is JsonObject -> extractPendingFromObject()
            else -> emptyList()
        }
    }

    private fun JsonObject.extractPendingFromObject(): List<BirdNetPendingDetection> {
        listOf("pending", "detections", "items", "data")
            .firstNotNullOfOrNull { key ->
                (this[key] as? JsonArray)?.mapNotNull { it.toPendingDetectionOrNull() }
            }
            ?.let { return it }

        toPendingDetectionOrNull()?.let { return listOf(it) }

        return values.mapNotNull { value ->
            when (value) {
                is JsonObject -> value.toPendingDetectionOrNull()
                else -> null
            }
        }
    }

    private fun JsonElement.toPendingDetectionOrNull(): BirdNetPendingDetection? {
        val obj = this as? JsonObject ?: return null

        val scientificName = obj.stringValue("scientific_name", "scientificName")
        val commonName = obj.stringValue("common_name", "commonName")
        val confidence = obj.doubleValue("confidence")
        val hitCount = obj.intValue("hit_count", "hitCount", "count")
        val maxConfidence = obj.doubleValue("max_confidence", "maxConfidence", "best_confidence")

        if (scientificName.isBlank() && commonName.isBlank()) return null

        return BirdNetPendingDetection(
            scientificName = scientificName,
            commonName = commonName,
            confidence = confidence,
            hitCount = hitCount,
            maxConfidence = maxConfidence
        )
    }

    private fun JsonObject.stringValue(vararg keys: String): String {
        return keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        }.orEmpty()
    }

    private fun JsonObject.doubleValue(vararg keys: String): Double {
        return keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.doubleOrNull
        } ?: 0.0
    }

    private fun JsonObject.intValue(vararg keys: String): Int {
        return keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.intOrNull
        } ?: 0
    }
}

