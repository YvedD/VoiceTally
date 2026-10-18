package com.yvesds.vt5.features.birdnet

import android.util.Log
import com.yvesds.vt5.VT5App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
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
 * Modern BirdNET-GO SSE Client using Kotlin Flow.
 *
 * - Provides a robust [Flow] of [SseEvent].
 * - Automatically handles cleanup when the flow collection stops.
 * - Dispatches events on [Dispatchers.IO].
 */
object BirdNetSseClient {

    private const val TAG = "BirdNetSseClient"

    sealed class SseEvent {
        data object Connected : SseEvent()
        data class Detection(val detection: BirdNetDetection) : SseEvent()
        data class Pending(val detections: List<BirdNetPendingDetection>) : SseEvent()
        data object Heartbeat : SseEvent()
        data class ConnectionError(val message: String) : SseEvent()
    }

    private val sseHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Creates a Flow of SSE events from the BirdNET-GO server.
     */
    fun streamEvents(config: BirdNetConfig): Flow<SseEvent> = callbackFlow {
        Log.i(TAG, "Start BirdNET-GO SSE stream: ${config.streamUrl}")
        
        val request = Request.Builder()
            .url(config.streamUrl)
            .addHeader("Accept", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .addHeader("Connection", "keep-alive")
            .build()

        val call = sseHttp.newCall(request)

        try {
            val response = call.execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "SSE verbinding mislukt (HTTP ${response.code})")
                trySend(SseEvent.ConnectionError("HTTP ${response.code}"))
                close()
                return@callbackFlow
            }

            val body = response.body
            if (body == null) {
                Log.e(TAG, "SSE verbinding mislukt (Lege respons)")
                trySend(SseEvent.ConnectionError("Lege respons"))
                close()
                return@callbackFlow
            }

            Log.i(TAG, "SSE stream verbonden, luisteren naar events...")
            val source = body.source()
            var currentEventType = ""
            val dataBuffer = StringBuilder()

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break

                when {
                    line.startsWith("event:") -> {
                        currentEventType = line.removePrefix("event:").trim()
                    }
                    line.startsWith("data:") -> {
                        dataBuffer.append(line.removePrefix("data:").trim())
                    }
                    line.isEmpty() -> {
                        if (dataBuffer.isNotEmpty()) {
                            val event = dispatchEvent(currentEventType, dataBuffer.toString())
                            if (event != null) {
                                if (event is SseEvent.Heartbeat) {
                                    Log.v(TAG, "SSE Heartbeat ontvangen")
                                }
                                trySend(event)
                            }
                            dataBuffer.clear()
                        }
                        currentEventType = ""
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SSE stream fout: ${e.message}")
            trySend(SseEvent.ConnectionError(e.message ?: "Stream fout"))
        }

        awaitClose {
            Log.d(TAG, "SSE stream afgesloten door collector")
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private fun dispatchEvent(eventType: String, data: String): SseEvent? {
        return when (eventType) {
            "connected" -> {
                Log.d(TAG, "SSE Connected event ontvangen")
                SseEvent.Connected
            }
            "detection" -> {
                Log.i(TAG, "SSE Nieuwe detectie ontvangen")
                parseDetection(data)?.let { SseEvent.Detection(it) }
            }
            "heartbeat" -> SseEvent.Heartbeat
            "pending" -> {
                // Alleen loggen als debug aanstaat om Logcat niet te overspoelen
                // Log.v(TAG, "SSE Pending detections ontvangen")
                SseEvent.Pending(parsePendingDetections(data))
            }
            else -> {
                Log.d(TAG, "SSE Onbekend event type: $eventType")
                null
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
