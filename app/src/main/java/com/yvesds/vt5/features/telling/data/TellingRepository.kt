package com.yvesds.vt5.features.telling.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yvesds.vt5.core.database.dao.TellingDao
import com.yvesds.vt5.core.database.entity.ObservationEntity
import com.yvesds.vt5.core.database.entity.TellingEntity
import com.yvesds.vt5.net.ServerTellingDataItem
import com.yvesds.vt5.net.ServerTellingEnvelope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "telling_prefs")

/**
 * Repository para Telling (sesiones de conteo).
 * Proporciona acceso unificado a Room (persistencia local) y DataStore (preferencias).
 */
@Singleton
class TellingRepository @Inject constructor(
    private val tellingDao: TellingDao,
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore
    private val savedEnvelopeKey = stringPreferencesKey("saved_envelope")
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }

    // ═══════════════════════════════════════════════════════
    // Telling Operations
    // ═══════════════════════════════════════════════════════

    suspend fun saveTelling(envelope: ServerTellingEnvelope) {
        val telling = TellingEntity(
            id = envelope.tellingid,
            telpostId = envelope.telpostid,
            begintijd = envelope.begintijd.toLongOrNull() ?: (System.currentTimeMillis() / 1000),
            eindtijd = envelope.eindtijd.toLongOrNull() ?: 0L,
            createdAt = System.currentTimeMillis(),
            opmerkingen = envelope.opmerkingen
        )
        tellingDao.insertTelling(telling)

        envelope.data.forEach { record ->
            val observation = ObservationEntity(
                id = record.idLocal.takeIf { it.isNotBlank() } ?: "${telling.id}_${java.util.UUID.randomUUID()}",
                tellingId = envelope.tellingid,
                speciesId = record.soortid,
                count = record.aantal,
                direction = record.richting,
                timestamp = record.tijdstip.toLongOrNull() ?: (System.currentTimeMillis() / 1000),
                countReturn = record.aantalterug,
                directionReturn = record.richtingterug,
                sightingDirection = record.sightingdirection,
                local = record.lokaal,
                countPlus = record.aantal_plus,
                countReturnPlus = record.aantalterug_plus,
                localPlus = record.lokaal_plus,
                mark = record.markeren,
                markLocal = record.markerenlokaal,
                geslacht = record.geslacht,
                leeftijd = record.leeftijd,
                kleed = record.kleed,
                notes = record.opmerkingen,
                trektype = record.trektype,
                teltype = record.teltype,
                location = record.location,
                height = record.height,
                groupId = record.groupid,
                uploadTimestamp = record.uploadtijdstip,
                totalCount = record.totaalaantal
            )
            tellingDao.insertObservation(observation)
        }
    }

    fun getPendingTellings(): Flow<List<TellingEntity>> = tellingDao.getPendingTellings()

    fun getRecentTellings(limit: Int = 50): Flow<List<TellingEntity>> =
        tellingDao.getRecentTellings(limit)

    fun getTellingFlow(id: String): Flow<TellingEntity?> = tellingDao.getTellingFlow(id)

    suspend fun getTelling(id: String): TellingEntity? = tellingDao.getTelling(id)

    suspend fun markTellingAsUploaded(tellingId: String) {
        val telling = tellingDao.getTelling(tellingId) ?: return
        tellingDao.updateTelling(telling.copy(isUploaded = true, uploadedAt = System.currentTimeMillis()))
    }

    // ═══════════════════════════════════════════════════════
    // Observation Operations
    // ═══════════════════════════════════════════════════════

    fun getObservationsFlow(tellingId: String): Flow<List<ObservationEntity>> =
        tellingDao.getObservationsFlow(tellingId)

    suspend fun getObservations(tellingId: String): List<ObservationEntity> =
        tellingDao.getObservations(tellingId)

    fun ObservationEntity.toDomain(): ServerTellingDataItem {
        return ServerTellingDataItem(
            idLocal = id,
            tellingid = tellingId,
            soortid = speciesId,
            aantal = count,
            richting = direction ?: "",
            aantalterug = countReturn,
            richtingterug = directionReturn ?: "",
            sightingdirection = sightingDirection ?: "",
            lokaal = local,
            aantal_plus = countPlus,
            aantalterug_plus = countReturnPlus,
            lokaal_plus = localPlus,
            markeren = mark,
            markerenlokaal = markLocal,
            geslacht = geslacht ?: "",
            leeftijd = leeftijd ?: "",
            kleed = kleed ?: "",
            opmerkingen = notes ?: "",
            trektype = trektype ?: "",
            teltype = teltype ?: "",
            location = location ?: "",
            height = height ?: "",
            tijdstip = timestamp.toString(),
            groupid = groupId ?: id,
            uploadtijdstip = uploadTimestamp ?: "",
            totaalaantal = totalCount
        )
    }

    suspend fun addObservation(tellingId: String, record: ServerTellingDataItem) {
        val observation = ObservationEntity(
            id = record.idLocal.takeIf { it.isNotBlank() } ?: "${tellingId}_${java.util.UUID.randomUUID()}",
            tellingId = tellingId,
            speciesId = record.soortid,
            count = record.aantal,
            direction = record.richting,
            timestamp = record.tijdstip.toLongOrNull() ?: (System.currentTimeMillis() / 1000),
            countReturn = record.aantalterug,
            directionReturn = record.richtingterug,
            sightingDirection = record.sightingdirection,
            local = record.lokaal,
            countPlus = record.aantal_plus,
            countReturnPlus = record.aantalterug_plus,
            localPlus = record.lokaal_plus,
            mark = record.markeren,
            markLocal = record.markerenlokaal,
            geslacht = record.geslacht,
            leeftijd = record.leeftijd,
            kleed = record.kleed,
            notes = record.opmerkingen,
            trektype = record.trektype,
            teltype = record.teltype,
            location = record.location,
            height = record.height,
            groupId = record.groupid,
            uploadTimestamp = record.uploadtijdstip,
            totalCount = record.totaalaantal
        )
        tellingDao.insertObservation(observation)
    }

    suspend fun updateObservation(observation: ObservationEntity) {
        tellingDao.updateObservation(observation)
    }

    suspend fun deleteObservation(observation: ObservationEntity) {
        tellingDao.deleteObservation(observation)
    }

    suspend fun deleteObservationsByIds(ids: List<String>) {
        tellingDao.deleteObservationsByIds(ids)
    }

    fun getFilteredObservationsFlow(tellingId: String, query: String): Flow<List<ObservationEntity>> =
        tellingDao.getFilteredObservationsFlow(tellingId, query)

    suspend fun deleteObservationsForTelling(tellingId: String) {
        tellingDao.deleteObservationsForTelling(tellingId)
    }

    // ═══════════════════════════════════════════════════════
    // DataStore Operations (preferences)
    // ═══════════════════════════════════════════════════════

    fun getSavedEnvelopeFlow(): Flow<ServerTellingEnvelope?> =
        dataStore.data.map { prefs ->
            val json = prefs[savedEnvelopeKey] ?: return@map null
            try {
                val envelopes = Json.decodeFromString<List<ServerTellingEnvelope>>(json)
                envelopes.firstOrNull()
            } catch (e: Exception) {
                null
            }
        }

    suspend fun saveEnvelope(envelope: ServerTellingEnvelope) {
        dataStore.edit { prefs ->
            val json = Json.encodeToString(ListSerializer(ServerTellingEnvelope.serializer()), listOf(envelope))
            prefs[savedEnvelopeKey] = json
        }
    }

    suspend fun clearSavedEnvelope() {
        dataStore.edit { prefs ->
            prefs.remove(savedEnvelopeKey)
        }
    }
}

