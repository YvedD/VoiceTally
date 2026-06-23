package com.yvesds.vt5.features.telling

/**
 * SoortRow: UI-model voor een soort-tegel in de telling.
 * Dit is het presentatie-model dat door adapters en ViewModel wordt gebruikt.
 */
data class SoortRow(
    val soortId: String,
    val naam: String,
    val countMain: Int = 0,
    val countReturn: Int = 0,
    val pendingMainCount: Int = 0
) {
    val totalCount: Int get() = countMain + countReturn
}

/**
 * SpeechLogRow: UI-model voor een spraaklog-regel (partial of final).
 */
data class SpeechLogRow(
    val ts: Long,
    val tekst: String,
    val bron: String,
    val isPending: Boolean = false,
    val isError: Boolean = false,
    val rowKey: String? = null,
    val recordLocalId: String? = null,
    val deliveryState: ObservationDeliveryState = ObservationDeliveryState.NONE,
    val isClientOrigin: Boolean = false,
    val isUploadedToServer: Boolean = false
)

/**
 * ObservationDeliveryState: Geeft de leveringsstatus van een observatie aan
 * in de master-client context.
 */
enum class ObservationDeliveryState {
    NONE,
    PENDING,
    RECEIVED,
    REJECTED
}
