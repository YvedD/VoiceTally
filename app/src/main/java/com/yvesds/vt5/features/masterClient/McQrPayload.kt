package com.yvesds.vt5.features.masterClient

import com.yvesds.vt5.VT5App
import kotlinx.serialization.Serializable

private const val DEFAULT_QR_TRANSPORT = "wifi_lan"

@Serializable
data class McQrPayload(
    val ip: String,
    val port: Int,
    val pin: String,
    val ssid: String = "",
    val pass: String = "",
    val sec: String = "WPA",
    val transport: String = DEFAULT_QR_TRANSPORT,
    val networkName: String = "",
    val sessionId: String = "",
    val ownerAddress: String = "",
    val ownerDeviceAddress: String = "",
    val ownerDeviceName: String = "",
    val serviceTag: String = "",
    val v: Int = 2
)

object McQrPayloadCodec {
    private const val PREFIX = "VT5MC:"

    fun encode(payload: McQrPayload): String {
        val json = VT5App.json.encodeToString(McQrPayload.serializer(), payload.normalized())
        return "$PREFIX$json"
    }

    fun decode(raw: String): McQrPayload? {
        if (!raw.startsWith(PREFIX)) return null
        val json = raw.removePrefix(PREFIX).trim()
        return try {
            VT5App.json.decodeFromString(McQrPayload.serializer(), json).normalized()
        } catch (_: Exception) {
            null
        }
    }

    private fun McQrPayload.normalized(): McQrPayload {
        val resolvedTransport = when {
            transport.isNotBlank() -> McTransportKind.fromWireValue(transport)
            ssid.isNotBlank() -> McTransportKind.HOTSPOT
            else -> McTransportKind.WIFI_LAN
        }
        val normalizedSecurity = sec.ifBlank {
            if (pass.isBlank()) "NOPASS" else "WPA"
        }
        val normalizedServiceTag = serviceTag.ifBlank {
            if (sessionId.isBlank()) "" else McWifiDirectJoinResolver.buildServiceTag(sessionId)
        }
        return copy(
            transport = resolvedTransport.wireValue,
            networkName = if (networkName.isNotBlank()) networkName else ssid,
            sec = normalizedSecurity,
            serviceTag = normalizedServiceTag,
            v = if (v <= 0) 1 else v
        )
    }
}
