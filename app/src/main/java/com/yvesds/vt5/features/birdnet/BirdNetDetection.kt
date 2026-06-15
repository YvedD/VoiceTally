@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.yvesds.vt5.features.birdnet

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * Eén detectie-event van de BirdNET-GO SSE-stream (`/api/v2/detections/stream`).
 *
 * Veldnamen komen 1-op-1 overeen met de JSON-sleutels van BirdNET-GO API v2.
 * Alle velden hebben standaardwaarden zodat `ignoreUnknownKeys` + `coerceInputValues`
 * nooit een crash veroorzaken bij ontbrekende of null-velden.
 *
 * Voorbeeld:
 * ```json
 * {
 *   "id": 12345,
 *   "date": "2024-01-15",
 *   "time": "08:30:45",
 *   "source": {
 *     "id": "usb0",
 *     "type": "audio",
 *     "displayName": "USB Audio Device"
 *   },
 *   "scientificName": "Merops apiaster",
 *   "commonName": "European Bee-eater",
 *   "confidence": 0.87,
 *   "clipName": "merops_apiaster_87p_20240115T083045Z.wav",
 *   "timestamp": "2024-01-15T08:30:45.123Z",
 *   "eventType": "new_detection"
 * }
 * ```
 */
@Serializable
data class BirdNetSourceInfo(
    val id: String = "",
    val type: String = "",
    val displayName: String = ""
)

@Serializable
data class BirdNetDetection(
    val id: Long = 0L,
    val date: String = "",
    val time: String = "",
    val source: BirdNetSourceInfo? = null,
    val scientificName: String = "",
    val commonName: String = "",
    val confidence: Double = 0.0,
    val clipName: String? = null,
    val timestamp: String = "",
    val eventType: String = ""
) {
    /**
     * Deduplicatiesleutel: bij voorkeur het numerieke ID van de server,
     * anders timestamp + wetenschappelijke naam + confidence.
     *
     * Garandeert dat twee identieke events (bijv. bij reconnect) niet dubbel getoond worden,
     * maar twee echte waarnemingen van dezelfde soort op verschillende tijdstippen
     * wél als aparte regels verschijnen.
     */
    val deduplicationKey: String
        get() = if (id > 0L) "id:$id"
                else "ts:$timestamp:sn:$scientificName:cf:$confidence"

    /** Weergavepercentage (0–100). */
    val displayConfidencePct: Int
        get() = (confidence * 100).roundToInt()
}

/**
 * Genormaliseerde pending-detectie voor de snelle BirdNET-GO ticker.
 */
data class BirdNetPendingDetection(
    val scientificName: String = "",
    val commonName: String = "",
    val confidence: Double = 0.0,
    val hitCount: Int = 0,
    val maxConfidence: Double = 0.0
) {
    val displayName: String
        get() = commonName.ifBlank { scientificName }

    val displayConfidencePct: Int
        get() = ((if (maxConfidence > 0.0) maxConfidence else confidence) * 100).roundToInt()
}

