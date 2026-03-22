package com.yvesds.vt5.features.masterClient

/**
 * Neutrale transportsoorten voor pairing/bootstrap.
 *
 * Fase 1 houdt de bestaande hotspot/LAN-flow werkend, maar maakt de metadata expliciet
 * zodat een Wi‑Fi Direct transportlaag in latere fases kan inpluggen zonder dat QR/prefs
 * nog hotspot-specifiek hoeven te denken.
 */
enum class McTransportKind(val wireValue: String) {
    WIFI_LAN("wifi_lan"),
    HOTSPOT("hotspot"),
    WIFI_DIRECT("wifi_direct"),
    UNKNOWN("unknown");

    companion object {
        fun fromWireValue(raw: String?): McTransportKind {
            val normalized = raw?.trim().orEmpty()
            return entries.firstOrNull { it.wireValue.equals(normalized, ignoreCase = true) }
                ?: when {
                    normalized.equals("wifi", ignoreCase = true) -> WIFI_LAN
                    normalized.equals("lan", ignoreCase = true) -> WIFI_LAN
                    normalized.equals("local_network", ignoreCase = true) -> WIFI_LAN
                    normalized.equals("wifi-client", ignoreCase = true) -> WIFI_LAN
                    normalized.equals("wifi_client", ignoreCase = true) -> WIFI_LAN
                    normalized.equals("p2p", ignoreCase = true) -> WIFI_DIRECT
                    normalized.equals("wifi-direct", ignoreCase = true) -> WIFI_DIRECT
                    normalized.equals("direct", ignoreCase = true) -> WIFI_DIRECT
                    normalized.equals("local_only_hotspot", ignoreCase = true) -> HOTSPOT
                    normalized.equals("softap", ignoreCase = true) -> HOTSPOT
                    normalized.isBlank() -> UNKNOWN
                    else -> UNKNOWN
                }
        }
    }
}

data class McPairingTransport(
    val kind: McTransportKind,
    val networkName: String = "",
    val passphrase: String = "",
    val security: String = "WPA",
    val sessionId: String = "",
    val ownerAddress: String = "",
    val ownerDeviceAddress: String = "",
    val ownerDeviceName: String = "",
    val serviceTag: String = ""
) {
    val normalizedSecurity: String
        get() = when {
            security.isNotBlank() -> security
            passphrase.isBlank() -> "NOPASS"
            else -> "WPA"
        }
}

