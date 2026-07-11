package com.yvesds.vt5.features.telling

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.import.CsvImportPolicy
import com.yvesds.vt5.core.opslag.ServerResponseLogger
import com.yvesds.vt5.hoofd.InstellingenScherm
import com.yvesds.vt5.core.secure.CredentialsStore
import com.yvesds.vt5.net.ServerTellingDataItem
import com.yvesds.vt5.net.ServerTellingEnvelope
import com.yvesds.vt5.net.TrektellenApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centrale uploadkern voor alle telling-uploads.
 *
 * Verantwoordelijkheden:
 * - één gedeelde voorbereiding/sanitisering van envelopes
 * - één gedeelde `counts_save`-aanroep
 * - consistente verwerking van returned onlineId
 * - mode-afhankelijke neveneffecten (prefs/flags), maar géén UI/navigatie/cleanup
 * - alle uploads lopen via één gedeelde mutex, zodat de sessiestatus consistent blijft
 */
class TellingUploadCore(
    private val context: Context
) {
    private val serverResponseLogger by lazy { ServerResponseLogger(context) }

    companion object {
        private const val TAG = "TellingUploadCore"
        private const val PREFS_NAME = "vt5_prefs"
        private const val PREF_ONLINE_ID = "pref_online_id"
        private const val PREF_SAVED_ENVELOPE_JSON = "pref_saved_envelope_json"
        private const val BASE_URL = "https://trektellen.nl"
        private const val LANGUAGE = "dutch"
        private const val VERSIE = "1845"

        private val uploadMutex = Mutex()

        internal fun sanitizeRecordForUpload(record: ServerTellingDataItem): ServerTellingDataItem {
            return record.copy(
                aantal = record.aantal.ifBlank { "0" },
                aantalterug = record.aantalterug.ifBlank { "0" },
                lokaal = record.lokaal.ifBlank { "0" },
                aantal_plus = record.aantal_plus.ifBlank { "0" },
                aantalterug_plus = record.aantalterug_plus.ifBlank { "0" },
                lokaal_plus = record.lokaal_plus.ifBlank { "0" },
                markeren = record.markeren.ifBlank { "0" },
                markerenlokaal = record.markerenlokaal.ifBlank { "0" },
                totaalaantal = record.totaalaantal.ifBlank {
                    ((record.aantal.toIntOrNull() ?: 0) +
                        (record.aantalterug.toIntOrNull() ?: 0) +
                        (record.lokaal.toIntOrNull() ?: 0)).toString()
                },
                richting = record.richting,
                richtingterug = record.richtingterug,
                sightingdirection = record.sightingdirection,
                geslacht = record.geslacht,
                leeftijd = record.leeftijd,
                kleed = record.kleed,
                opmerkingen = record.opmerkingen,
                trektype = record.trektype,
                teltype = record.teltype,
                location = record.location,
                height = record.height,
                tijdstip = record.tijdstip,
                groupid = record.groupid,
                uploadtijdstip = record.uploadtijdstip
            )
        }
    }

    enum class Mode {
        START,
        FINALIZE,
        WORKER_FINALIZE,
        EDITOR_UPLOAD
    }

    data class Credentials(
        val username: String,
        val password: String
    )

    data class UploadRequest(
        val mode: Mode,
        val preparedEnvelope: ServerTellingEnvelope,
        val credentials: Credentials? = null,
        val persistReturnedOnlineId: Boolean = false,
        val persistPreparedEnvelopeToPrefs: Boolean = false,
        val markTellingSent: Boolean = false,
        val keepPreparedOnlineIdOnSuccess: Boolean = false
    )

    data class UploadResult(
        val success: Boolean,
        val skipped: Boolean = false,
        val preparedEnvelope: ServerTellingEnvelope,
        val responseText: String,
        val effectiveOnlineId: String?,
        val errorMessage: String? = null,
        val retryable: Boolean = false
    )

    fun prepareEnvelopeForUpload(
        sourceEnvelope: ServerTellingEnvelope,
        useStoredOnlineIdWhenBlank: Boolean = true,
        now: Date = Date()
    ): ServerTellingEnvelope {
        val envelopeWithOnlineId = if (useStoredOnlineIdWhenBlank) {
            applyStoredOnlineId(sourceEnvelope)
        } else {
            sourceEnvelope
        }

        val sanitizedData = envelopeWithOnlineId.data.map { sanitizeRecordForUpload(it) }
        val uploadtijdstip = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now)

        return envelopeWithOnlineId.copy(
            onlineid = envelopeWithOnlineId.onlineid.trim(),
            uploadtijdstip = uploadtijdstip,
            nrec = sanitizedData.size.toString(),
            nsoort = sanitizedData.map { it.soortid }.filter { it.isNotBlank() }.toSet().size.toString(),
            data = sanitizedData
        )
    }

    suspend fun uploadPrepared(request: UploadRequest): UploadResult {
        // VEILIGHEIDSCHECK: Voorkom upload van gearchiveerde data
        if (request.preparedEnvelope.tellingid.isNotBlank()) {
            val db = VoiceTallyDatabase.getDatabase(context)
            val header = db.tellingDao().getHeader(request.preparedEnvelope.tellingid)
            if (header != null && CsvImportPolicy.isUploadBlocked(header.status, header.bron)) {
                Log.w(
                    TAG,
                    "Upload GEBLOKKEERD: Telling ${header.tellingid} is gemarkeerd als importdata (status=${header.status}, bron=${header.bron})"
                )
                return UploadResult(
                    success = false,
                    skipped = true,
                    preparedEnvelope = request.preparedEnvelope,
                    responseText = "Upload niet toegestaan voor gearchiveerde data.",
                    effectiveOnlineId = request.preparedEnvelope.onlineid,
                    errorMessage = "Deze data is gearchiveerd en mag niet worden geüpload."
                )
            }
        }

        return uploadMutex.withLock {
            doUpload(request)
        }
    }

    fun persistPreparedEnvelopeToPrefs(envelope: ServerTellingEnvelope) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val payload = VT5App.json.encodeToString(
                ListSerializer(ServerTellingEnvelope.serializer()),
                listOf(envelope)
            )
            prefs.edit { putString(PREF_SAVED_ENVELOPE_JSON, payload) }
        } catch (e: Exception) {
            Log.w(TAG, "Kon prepared envelope niet in prefs bewaren: ${e.message}", e)
        }
    }

    fun getStoredOnlineId(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_ONLINE_ID, "") ?: ""
    }

    private suspend fun doUpload(request: UploadRequest): UploadResult = withContext(Dispatchers.IO) {
        val creds = request.credentials ?: loadStoredCredentials()
        if (creds.username.isBlank() || creds.password.isBlank()) {
            return@withContext UploadResult(
                success = false,
                preparedEnvelope = request.preparedEnvelope,
                responseText = "",
                effectiveOnlineId = request.preparedEnvelope.onlineid.ifBlank { null },
                errorMessage = "Geen credentials beschikbaar voor upload.",
                retryable = request.mode == Mode.WORKER_FINALIZE
            )
        }

        val (ok, resp) = try {
            TrektellenApi.postCountsSave(
                BASE_URL,
                LANGUAGE,
                VERSIE,
                creds.username,
                creds.password,
                listOf(request.preparedEnvelope)
            )
        } catch (ex: Exception) {
            Log.w(TAG, "postCountsSave exception (${request.mode}): ${ex.message}", ex)
            false to (ex.message ?: "exception")
        }

        val returnedOnlineId = parseOnlineIdFromResponse(resp)?.takeIf { it.isNotBlank() }
        if (InstellingenScherm.isServerResponseLoggingEnabled(context)) {
            val loggedPath = serverResponseLogger.logCountsSaveResponse(
                mode = request.mode.name,
                tellingId = request.preparedEnvelope.tellingid,
                onlineId = returnedOnlineId ?: request.preparedEnvelope.onlineid.ifBlank { null },
                responseText = resp
            )
            Log.i(TAG, "Server response log saved (${request.mode}): ${loggedPath ?: "unavailable"}")
        }

        if (!ok) {
            return@withContext UploadResult(
                success = false,
                preparedEnvelope = request.preparedEnvelope,
                responseText = resp,
                effectiveOnlineId = request.preparedEnvelope.onlineid.ifBlank { null },
                errorMessage = resp,
                retryable = request.mode == Mode.WORKER_FINALIZE
            )
        }

        val effectiveOnlineId = when {
            request.keepPreparedOnlineIdOnSuccess && request.preparedEnvelope.onlineid.isNotBlank() -> request.preparedEnvelope.onlineid
            !returnedOnlineId.isNullOrBlank() -> returnedOnlineId
            else -> request.preparedEnvelope.onlineid.takeIf { it.isNotBlank() }
        }

        val persistedEnvelope = if (!effectiveOnlineId.isNullOrBlank()) {
            request.preparedEnvelope.copy(onlineid = effectiveOnlineId)
        } else {
            request.preparedEnvelope
        }

        if (
            request.keepPreparedOnlineIdOnSuccess &&
            !returnedOnlineId.isNullOrBlank() &&
            returnedOnlineId != request.preparedEnvelope.onlineid
        ) {
            Log.w(
                TAG,
                "Server returned afwijkend onlineId tijdens ${request.mode}; bestaande sessie-id behouden " +
                    "(server=$returnedOnlineId, session=${request.preparedEnvelope.onlineid})"
            )
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (request.persistReturnedOnlineId && !effectiveOnlineId.isNullOrBlank()) {
            prefs.edit { putString(PREF_ONLINE_ID, effectiveOnlineId) }
        }
        if (request.persistPreparedEnvelopeToPrefs) {
            persistPreparedEnvelopeToPrefs(persistedEnvelope)
        }
        if (request.markTellingSent) {
            TellingUploadFlags.markSent(context, persistedEnvelope.tellingid, effectiveOnlineId ?: persistedEnvelope.onlineid)
        }
        when (request.mode) {
            Mode.START,
            Mode.FINALIZE,
            Mode.WORKER_FINALIZE -> UploadedObservationStateStore.replaceUploadedRecords(
                context = context,
                tellingId = persistedEnvelope.tellingid,
                uploadedRecords = persistedEnvelope.data
            )
            Mode.EDITOR_UPLOAD -> Unit
        }

        Log.i(
            TAG,
            "Upload succesvol (${request.mode}) telling=${persistedEnvelope.tellingid} onlineId=${effectiveOnlineId.orEmpty()} records=${persistedEnvelope.data.size}"
        )

        return@withContext UploadResult(
            success = true,
            preparedEnvelope = persistedEnvelope,
            responseText = resp,
            effectiveOnlineId = effectiveOnlineId
        )
    }

    private fun loadStoredCredentials(): Credentials {
        val creds = CredentialsStore(context)
        return Credentials(
            username = creds.getUsername().orEmpty(),
            password = creds.getPassword().orEmpty()
        )
    }

    private fun applyStoredOnlineId(envelope: ServerTellingEnvelope): ServerTellingEnvelope {
        if (envelope.onlineid.isNotBlank()) return envelope
        val storedOnlineId = getStoredOnlineId()
        return if (storedOnlineId.isBlank()) envelope else envelope.copy(onlineid = storedOnlineId)
    }


    private fun parseOnlineIdFromResponse(resp: String): String? {
        try {
            val el = VT5App.json.parseToJsonElement(resp)
            extractOnlineIdFromJsonElement(el)?.let { return it }
        } catch (_: Exception) {
            // fallback below
        }

        listOf(
            Regex("['\"]?onlineid['\"]?\\s*:\\s*['\"]?([A-Za-z0-9_-]+)['\"]?", RegexOption.IGNORE_CASE),
            Regex("\\bonlineid\\b\\s*:\\s*['\"]?([A-Za-z0-9_-]+)['\"]?", RegexOption.IGNORE_CASE),
            Regex("['\"]?online_id['\"]?\\s*:\\s*['\"]?([A-Za-z0-9_-]+)['\"]?", RegexOption.IGNORE_CASE),
            Regex("['\"]?onlineId['\"]?\\s*:\\s*['\"]?([A-Za-z0-9_-]+)['\"]?", RegexOption.IGNORE_CASE),
            Regex("""onlineid\s*=\s*([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
        ).forEach { regex ->
            regex.find(resp)?.groups?.get(1)?.value?.trim()?.takeIf { it.isNotBlank() }?.let {
                return it
            }
        }

        val primitiveFallback = resp.trim().removeSurrounding("\"")
        return primitiveFallback.takeIf { it.matches(Regex("""[A-Za-z0-9_-]{4,}""")) }
    }

    private fun extractOnlineIdFromJsonElement(element: JsonElement): String? {
        when (element) {
            is JsonObject -> {
                listOf("onlineid", "onlineId", "online_id").forEach { key ->
                    element[key]?.let { candidate ->
                        extractPrimitiveValue(candidate)?.let { return it }
                    }
                }
                element.values.forEach { child ->
                    extractOnlineIdFromJsonElement(child)?.let { return it }
                }
                listOf("result", "id").forEach { key ->
                    element[key]?.let { candidate ->
                        extractPrimitiveValue(candidate)?.let { return it }
                    }
                }
            }
            else -> {
                element.jsonArrayOrNull()?.forEach { child ->
                    extractOnlineIdFromJsonElement(child)?.let { return it }
                }
            }
        }
        return null
    }

    private fun extractPrimitiveValue(element: JsonElement): String? {
        val primitive = element as? JsonPrimitive ?: return null
        return primitive.content.trim().takeIf { it.isNotBlank() }
    }

    private fun JsonElement.jsonArrayOrNull() = runCatching { this.jsonArray }.getOrNull()
}

