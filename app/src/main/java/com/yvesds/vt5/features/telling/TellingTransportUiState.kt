package com.yvesds.vt5.features.telling

import com.yvesds.vt5.features.masterClient.McTransportKind
import com.yvesds.vt5.features.masterClient.McWifiDirectRuntime

/**
 * Pure UI-state mapping voor de transportstatusbalk.
 */
data class TellingTransportUiState(
    val kind: Kind,
    val detail: String = ""
) {
    enum class Kind {
        HIDDEN,
        IDLE,
        DISCOVERING,
        CONNECTING,
        READY,
        RECOVERING,
        FALLBACK,
        ERROR
    }

    companion object {
        fun from(
            runtimeState: McWifiDirectRuntime.State?,
            storedTransportKind: McTransportKind,
            wifiDirectSupported: Boolean,
            recoveryInProgress: Boolean
        ): TellingTransportUiState {
            if (recoveryInProgress) {
                return TellingTransportUiState(Kind.RECOVERING)
            }

            return when (runtimeState) {
                is McWifiDirectRuntime.State.Discovering,
                is McWifiDirectRuntime.State.Starting -> TellingTransportUiState(Kind.DISCOVERING)

                is McWifiDirectRuntime.State.Connecting -> TellingTransportUiState(Kind.CONNECTING)

                is McWifiDirectRuntime.State.Ready -> TellingTransportUiState(
                    kind = Kind.READY,
                    detail = runtimeState.sessionInfo.ownerAddress.ifBlank { "?" }
                )

                is McWifiDirectRuntime.State.Error -> TellingTransportUiState(
                    kind = Kind.ERROR,
                    detail = runtimeState.message
                )

                is McWifiDirectRuntime.State.Lost -> TellingTransportUiState(
                    kind = Kind.ERROR,
                    detail = runtimeState.message
                )

                else -> when (storedTransportKind) {
                    McTransportKind.WIFI_DIRECT -> TellingTransportUiState(Kind.IDLE)
                    McTransportKind.HOTSPOT,
                    McTransportKind.WIFI_LAN -> TellingTransportUiState(Kind.FALLBACK)
                    McTransportKind.UNKNOWN -> {
                        if (wifiDirectSupported) TellingTransportUiState(Kind.IDLE)
                        else TellingTransportUiState(Kind.HIDDEN)
                    }
                }
            }
        }
    }
}

