package com.yvesds.vt5.features.birdnet

import androidx.core.content.edit
import com.yvesds.vt5.VT5App

/**
 * BirdNET-GO host configuration.
 *
 * Gedocumenteerde URL-opbouw:
 *   http://<host>:<port>/api/v2          (plain HTTP, standaard Pi-setup)
 *   https://<host>:<port>/api/v2         (TLS, aanbevolen voor productie)
 *
 * Relevante endpoints:
 *   /ping                    → connectiecheck, respons {"status":"ok"}
 *   /detections/stream       → SSE live feed (primair)
 *   /detections/recent       → REST fallback
 */
data class BirdNetConfig(
    val protocol: String = "http",
    val host: String = "",
    val port: Int = 8080,
    val apiPath: String = "/api/v2"
) {
    val baseUrl: String    get() = "$protocol://$host:$port$apiPath"
    val pingUrl: String    get() = "$baseUrl/ping"
    val streamUrl: String  get() = "$baseUrl/detections/stream"
    val recentUrl: String  get() = "$baseUrl/detections/recent"
    val isConfigured: Boolean get() = host.isNotBlank()

    /** Leesbare weergave voor UI-statusregel. */
    val displayLabel: String get() = if (isConfigured) "$host:$port" else ""

    companion object {
        private const val KEY_HOST     = "birdnet_host"
        private const val KEY_PORT     = "birdnet_port"
        private const val KEY_PROTOCOL = "birdnet_protocol"

        /** Laad persistente config uit VT5-prefs. */
        fun load(): BirdNetConfig = VT5App.prefs().run {
            BirdNetConfig(
                protocol = getString(KEY_PROTOCOL, "http") ?: "http",
                host     = getString(KEY_HOST, "") ?: "",
                port     = getInt(KEY_PORT, 8080)
            )
        }

        /** Sla config persistent op. */
        fun save(config: BirdNetConfig) {
            VT5App.prefs().edit {
                putString(KEY_HOST, config.host)
                putInt(KEY_PORT, config.port)
                putString(KEY_PROTOCOL, config.protocol)
            }
        }

        /** Wis de opgeslagen host volledig (bv. bij reset). */
        fun clear() {
            VT5App.prefs().edit {
                remove(KEY_HOST)
                remove(KEY_PORT)
                remove(KEY_PROTOCOL)
            }
        }
    }
}

