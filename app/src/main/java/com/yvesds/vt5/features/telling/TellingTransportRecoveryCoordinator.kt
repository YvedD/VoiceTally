package com.yvesds.vt5.features.telling
import com.yvesds.vt5.features.masterClient.McPairingTransport
import com.yvesds.vt5.features.masterClient.McQrPayload
import com.yvesds.vt5.features.masterClient.McTransportKind
import com.yvesds.vt5.features.masterClient.McWifiDirectJoinResolver
import com.yvesds.vt5.features.masterClient.McWifiDirectRuntime
class TellingTransportRecoveryCoordinator {
    companion object {
        private const val RECOVERY_COOLDOWN_MS = 8_000L
    }
    private var recoveryInProgress = false
    private var lastRecoveryAttemptAt = 0L
    fun isRecoveryInProgress(): Boolean = recoveryInProgress
    fun buildJoinTarget(payload: McQrPayload): McWifiDirectJoinResolver.JoinTarget {
        return McWifiDirectJoinResolver.JoinTarget(
            sessionId = payload.sessionId,
            serviceTag = payload.serviceTag,
            ownerDeviceAddress = payload.ownerDeviceAddress.trim(),
            ownerDeviceName = payload.ownerDeviceName.trim(),
            networkName = payload.networkName.ifBlank { payload.ssid },
            ownerAddress = payload.ownerAddress.ifBlank { payload.ip }
        )
    }
    fun buildJoinTarget(transport: McPairingTransport): McWifiDirectJoinResolver.JoinTarget? {
        if (transport.kind != McTransportKind.WIFI_DIRECT) return null
        val target = McWifiDirectJoinResolver.JoinTarget(
            sessionId = transport.sessionId,
            serviceTag = transport.serviceTag,
            ownerDeviceAddress = transport.ownerDeviceAddress,
            ownerDeviceName = transport.ownerDeviceName,
            networkName = transport.networkName,
            ownerAddress = transport.ownerAddress
        )
        return if (
            target.ownerDeviceAddress.isBlank() &&
            target.ownerDeviceName.isBlank() &&
            target.serviceTag.isBlank() &&
            target.sessionId.isBlank()
        ) null else target
    }
    fun canAttemptRecovery(transport: McPairingTransport): Boolean {
        val enoughTimeElapsed = System.currentTimeMillis() - lastRecoveryAttemptAt >= RECOVERY_COOLDOWN_MS
        return !recoveryInProgress && enoughTimeElapsed && buildJoinTarget(transport) != null
    }
    fun attemptRecovery(
        transport: McPairingTransport,
        runtime: McWifiDirectRuntime,
        onStatus: (String) -> Unit,
        onSuccess: (McWifiDirectRuntime.SessionInfo, McWifiDirectJoinResolver.JoinTarget) -> Unit,
        onFailure: (String) -> Unit
    ): Boolean {
        val joinTarget = buildJoinTarget(transport) ?: return false
        if (!canAttemptRecovery(transport)) return false
        recoveryInProgress = true
        lastRecoveryAttemptAt = System.currentTimeMillis()
        onStatus(joinTarget.summary())
        runtime.reconnect(joinTarget) { result ->
            recoveryInProgress = false
            result.onSuccess { session ->
                onSuccess(session, joinTarget)
            }.onFailure { ex ->
                onFailure(ex.message ?: "onbekende fout")
            }
        }
        return true
    }
    fun markRecoveryFinished() {
        recoveryInProgress = false
    }
}
