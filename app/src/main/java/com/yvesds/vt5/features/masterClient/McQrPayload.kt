package com.yvesds.vt5.features.masterClient

import com.yvesds.vt5.VT5App
import kotlinx.serialization.Serializable

@Serializable
data class McQrPayload(
    val ip: String,
    val port: Int,
    val session: String = "",
    val v: Int = 1
)

object McQrPayloadCodec {
    private const val PREFIX = "VT5MC:"

    fun encode(payload: McQrPayload): String {
        val json = VT5App.json.encodeToString(McQrPayload.serializer(), payload)
        return "$PREFIX$json"
    }

    fun decode(raw: String): McQrPayload? {
        if (!raw.startsWith(PREFIX)) return null
        val json = raw.removePrefix(PREFIX).trim()
        return try {
            VT5App.json.decodeFromString(McQrPayload.serializer(), json)
        } catch (_: Exception) {
            null
        }
    }
}
