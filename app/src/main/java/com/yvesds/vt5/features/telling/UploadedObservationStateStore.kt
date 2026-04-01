package com.yvesds.vt5.features.telling

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.net.ServerTellingDataItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

object UploadedObservationStateStore {
    private const val TAG = "UploadedObservationState"
    private const val PREFS_NAME = "vt5_prefs"
    private const val PREF_PREFIX = "pref_uploaded_observation_state_"

    private val updatesFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)

    @Serializable
    private data class UploadedObservationState(
        val fingerprintsByRecordId: Map<String, String> = emptyMap()
    )

    fun updates(): SharedFlow<String> = updatesFlow

    fun replaceUploadedRecords(
        context: Context,
        tellingId: String,
        uploadedRecords: List<ServerTellingDataItem>
    ) {
        if (tellingId.isBlank()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val state = UploadedObservationState(
            fingerprintsByRecordId = uploadedRecords
                .asSequence()
                .filter { it.idLocal.isNotBlank() }
                .associate { it.idLocal to fingerprintFor(it) }
        )

        runCatching {
            val encoded = VT5App.json.encodeToString(state)
            prefs.edit { putString(prefKey(tellingId), encoded) }
            updatesFlow.tryEmit(tellingId)
        }.onFailure { ex ->
            Log.w(TAG, "Kon uploaded-observation-state niet bewaren: ${ex.message}", ex)
        }
    }

    fun isCurrentRecordUploaded(
        context: Context,
        tellingId: String,
        record: ServerTellingDataItem
    ): Boolean {
        if (tellingId.isBlank() || record.idLocal.isBlank()) return false
        val state = loadState(context, tellingId) ?: return false
        val uploadedFingerprint = state.fingerprintsByRecordId[record.idLocal] ?: return false
        return uploadedFingerprint == fingerprintFor(record)
    }

    private fun loadState(context: Context, tellingId: String): UploadedObservationState? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encoded = prefs.getString(prefKey(tellingId), null).orEmpty()
        if (encoded.isBlank()) return null

        return runCatching {
            VT5App.json.decodeFromString<UploadedObservationState>(encoded)
        }.onFailure { ex ->
            Log.w(TAG, "Kon uploaded-observation-state niet laden: ${ex.message}", ex)
        }.getOrNull()
    }

    private fun prefKey(tellingId: String): String = "$PREF_PREFIX$tellingId"

    private fun fingerprintFor(record: ServerTellingDataItem): String {
        val normalized = TellingUploadCore.sanitizeRecordForUpload(record)
        return VT5App.json.encodeToString(ServerTellingDataItem.serializer(), normalized)
    }
}
