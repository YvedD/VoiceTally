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
abstract class TellingDao {

    // ═══════════════════════════════════════════════════════
    // Telling Operations
    // ═══════════════════════════════════════════════════════

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTelling(telling: TellingEntity)

    @Update
    abstract suspend fun updateTelling(telling: TellingEntity)

    @Delete
    abstract suspend fun deleteTelling(telling: TellingEntity)

    @Query("SELECT * FROM tellings WHERE id = :id")
    abstract suspend fun getTelling(id: String): TellingEntity?

    @Query("SELECT * FROM tellings WHERE id = :id")
    abstract fun getTellingFlow(id: String): Flow<TellingEntity?>

    @Query("SELECT * FROM tellings WHERE isUploaded = 0 ORDER BY createdAt DESC")
    abstract fun getPendingTellings(): Flow<List<TellingEntity>>

    @Query("SELECT * FROM tellings ORDER BY createdAt DESC LIMIT :limit")
    abstract fun getRecentTellings(limit: Int): Flow<List<TellingEntity>>

    // ═══════════════════════════════════════════════════════
    // Observation Operations
    // ═══════════════════════════════════════════════════════

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertObservation(observation: ObservationEntity)

    @Update
    abstract suspend fun updateObservation(observation: ObservationEntity)

    @Delete
    abstract suspend fun deleteObservation(observation: ObservationEntity)

    @Query("SELECT * FROM observations WHERE tellingId = :tellingId")
    abstract suspend fun getObservations(tellingId: String): List<ObservationEntity>

    @Query("SELECT * FROM observations WHERE tellingId = :tellingId")
    abstract fun getObservationsFlow(tellingId: String): Flow<List<ObservationEntity>>

    @Query("SELECT * FROM observations WHERE speciesId = :speciesId")
    abstract fun getObservationsBySpecies(speciesId: String): Flow<List<ObservationEntity>>

    @Query("""
        SELECT * FROM observations 
        WHERE tellingId = :tellingId 
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    abstract suspend fun getObservationsPaged(tellingId: String, limit: Int): List<ObservationEntity>

    // ═══════════════════════════════════════════════════════
    // Transaction Operations
    // ═══════════════════════════════════════════════════════

    @Transaction
    open suspend fun insertTellingWithObservations(
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
    abstract suspend fun getObservationStats(tellingId: String): ObservationStats?

    @Query("DELETE FROM observations WHERE tellingId = :tellingId")
    abstract suspend fun deleteObservationsForTelling(tellingId: String)

    @Query("DELETE FROM observations WHERE id IN (:ids)")
    abstract suspend fun deleteObservationsByIds(ids: List<String>)

    @Query("""
        SELECT * FROM observations 
        WHERE tellingId = :tellingId 
        AND (speciesId LIKE '%' || :query || '%' OR count LIKE '%' || :query || '%')
        ORDER BY timestamp DESC
    """)
    abstract fun getFilteredObservationsFlow(tellingId: String, query: String): Flow<List<ObservationEntity>>
}

data class ObservationStats(
    val count: Int,
    val total: Int
)

