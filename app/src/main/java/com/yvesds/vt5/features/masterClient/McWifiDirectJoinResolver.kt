package com.yvesds.vt5.features.masterClient

/**
 * Pure resolver voor Wi‑Fi Direct join-candidates.
 *
 * Fase 3 gebruikt meerdere hints tegelijk:
 *  - owner device address
 *  - owner device name
 *  - serviceTag
 *  - sessionId
 *  - networkName
 */
object McWifiDirectJoinResolver {

    data class JoinTarget(
        val sessionId: String = "",
        val serviceTag: String = "",
        val ownerDeviceAddress: String = "",
        val ownerDeviceName: String = "",
        val networkName: String = "",
        val ownerAddress: String = ""
    ) {
        fun summary(): String {
            return when {
                ownerDeviceName.isNotBlank() -> ownerDeviceName
                networkName.isNotBlank() -> networkName
                ownerDeviceAddress.isNotBlank() -> ownerDeviceAddress
                serviceTag.isNotBlank() -> serviceTag
                sessionId.isNotBlank() -> sessionId
                else -> "doelapparaat"
            }
        }
    }

    data class Candidate(
        val deviceAddress: String,
        val deviceName: String,
        val networkName: String = "",
        val ownerAddress: String = "",
        val advertisedSessionId: String = "",
        val advertisedServiceTag: String = "",
        val advertisedOwnerDeviceName: String = ""
    )

    fun resolve(target: JoinTarget, candidates: List<Candidate>): Candidate? {
        if (candidates.isEmpty()) return null
        return candidates
            .map { candidate -> candidate to score(target, candidate) }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<Candidate, Int>> { it.second }
                    .thenByDescending { it.first.deviceAddress.equals(target.ownerDeviceAddress, ignoreCase = true) }
            )
            .firstOrNull()
            ?.first
    }

    fun buildServiceTag(sessionId: String): String {
        val normalized = sessionId.trim()
        if (normalized.isBlank()) return "vt5mc"
        val hash = normalized.hashCode().toUInt().toString(16).padStart(8, '0')
        return "vt5-${hash.takeLast(8)}"
    }

    private fun score(target: JoinTarget, candidate: Candidate): Int {
        var score = 0
        if (target.ownerDeviceAddress.isNotBlank() &&
            candidate.deviceAddress.equals(target.ownerDeviceAddress, ignoreCase = true)
        ) {
            score += 1000
        }
        if (target.serviceTag.isNotBlank() &&
            candidate.advertisedServiceTag.equals(target.serviceTag, ignoreCase = true)
        ) {
            score += 700
        }
        if (target.sessionId.isNotBlank() &&
            candidate.advertisedSessionId.equals(target.sessionId, ignoreCase = true)
        ) {
            score += 650
        }
        if (target.ownerDeviceName.isNotBlank()) {
            if (candidate.deviceName.equals(target.ownerDeviceName, ignoreCase = true)) {
                score += 300
            }
            if (candidate.advertisedOwnerDeviceName.equals(target.ownerDeviceName, ignoreCase = true)) {
                score += 250
            }
        }
        if (target.networkName.isNotBlank() &&
            candidate.networkName.equals(target.networkName, ignoreCase = true)
        ) {
            score += 200
        }
        if (target.ownerAddress.isNotBlank() &&
            candidate.ownerAddress.equals(target.ownerAddress, ignoreCase = true)
        ) {
            score += 150
        }
        return score
    }
}

