package com.yvesds.vt5.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.yvesds.vt5.core.database.entity.ObservationEntity
import com.yvesds.vt5.core.database.entity.TellingEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para operaciones de base de datos en Tellings y Observations.
 */
@Dao
interface TellingDao {

    // ═══════════════════════════════════════════════════════
    // Telling Operations
    // ═══════════════════════════════════════════════════════

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTelling(telling: TellingEntity)

    @Update
    suspend fun updateTelling(telling: TellingEntity)

    @Delete
    suspend fun deleteTelling(telling: TellingEntity)

    @Query("SELECT * FROM tellings WHERE id = :id")
    suspend fun getTelling(id: String): TellingEntity?

    @Query("SELECT * FROM tellings WHERE id = :id")
    fun getTellingFlow(id: String): Flow<TellingEntity?>

    @Query("SELECT * FROM tellings WHERE isUploaded = 0 ORDER BY createdAt DESC")
    fun getPendingTellings(): Flow<List<TellingEntity>>

    @Query("SELECT * FROM tellings ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentTellings(limit: Int = 50): Flow<List<TellingEntity>>

    // ═══════════════════════════════════════════════════════
    // Observation Operations
    // ═══════════════════════════════════════════════════════

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObservation(observation: ObservationEntity)

    @Update
    suspend fun updateObservation(observation: ObservationEntity)

    @Delete
    suspend fun deleteObservation(observation: ObservationEntity)

    @Query("SELECT * FROM observations WHERE tellingId = :tellingId")
    suspend fun getObservations(tellingId: String): List<ObservationEntity>

    @Query("SELECT * FROM observations WHERE tellingId = :tellingId")
    fun getObservationsFlow(tellingId: String): Flow<List<ObservationEntity>>

    @Query("SELECT * FROM observations WHERE speciesId = :speciesId")
    fun getObservationsBySpecies(speciesId: String): Flow<List<ObservationEntity>>

    @Query("""
        SELECT * FROM observations 
        WHERE tellingId = :tellingId 
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    suspend fun getObservationsPaged(tellingId: String, limit: Int = 50): List<ObservationEntity>

    // ═══════════════════════════════════════════════════════
    // Transaction Operations
    // ═══════════════════════════════════════════════════════

    @Transaction
    suspend fun insertTellingWithObservations(
        telling: TellingEntity,
        observations: List<ObservationEntity>
    ) {
        insertTelling(telling)
        observations.forEach { obs ->
            insertObservation(obs)
        }
    }

    @Query("""
        SELECT COUNT(*) as count, SUM(count) as total 
        FROM observations 
        WHERE tellingId = :tellingId
    """)
    suspend fun getObservationStats(tellingId: String): ObservationStats?

    @Query("DELETE FROM observations WHERE tellingId = :tellingId")
    suspend fun deleteObservationsForTelling(tellingId: String)

    @Query("DELETE FROM observations WHERE id IN (:ids)")
    suspend fun deleteObservationsByIds(ids: List<String>)

    @Query("""
        SELECT * FROM observations 
        WHERE tellingId = :tellingId 
        AND (speciesId LIKE '%' || :query || '%' OR count LIKE '%' || :query || '%')
        ORDER BY timestamp DESC
    """)
    fun getFilteredObservationsFlow(tellingId: String, query: String): Flow<List<ObservationEntity>>
}

data class ObservationStats(
    val count: Int,
    val total: Int
)

