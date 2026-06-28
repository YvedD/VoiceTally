package com.yvesds.vt5.core.database.dao

import androidx.room.*
import com.yvesds.vt5.core.database.entities.TellingHeader
import com.yvesds.vt5.core.database.entities.Waarneming
import kotlinx.coroutines.flow.Flow

@Dao
interface TellingDao {
    // TellingHeader operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeader(header: TellingHeader)

    @Query("SELECT * FROM telling_headers WHERE tellingid = :tellingId")
    suspend fun getHeader(tellingId: String): TellingHeader?

    @Query("SELECT * FROM telling_headers ORDER BY begintijd DESC")
    fun getAllHeadersFlow(): Flow<List<TellingHeader>>

    @Query("SELECT * FROM telling_headers ORDER BY begintijd DESC")
    suspend fun getAllHeaders(): List<TellingHeader>

    @Update
    suspend fun updateHeader(header: TellingHeader)

    // Waarneming operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaarneming(waarneming: Waarneming)

    @Update
    suspend fun updateWaarneming(waarneming: Waarneming)

    @Delete
    suspend fun deleteWaarneming(waarneming: Waarneming)

    @Query("SELECT * FROM waarnemingen WHERE tellingid = :tellingId ORDER BY tijdstip DESC")
    fun getWaarnemingenFlow(tellingId: String): Flow<List<Waarneming>>

    @Query("SELECT * FROM waarnemingen WHERE tellingid = :tellingId")
    suspend fun getWaarnemingenList(tellingId: String): List<Waarneming>

    @Query("SELECT * FROM waarnemingen WHERE soortid = :soortId ORDER BY tijdstip DESC")
    suspend fun getWaarnemingenBySoort(soortId: String): List<Waarneming>

    @Query("SELECT * FROM waarnemingen WHERE idLocal = :idLocal AND tellingid = :tellingId")
    suspend fun getWaarnemingById(idLocal: String, tellingId: String): Waarneming?

    @Query("DELETE FROM waarnemingen WHERE tellingid = :tellingId")
    suspend fun deleteWaarnemingenVoorTelling(tellingId: String)

    @Query("DELETE FROM telling_headers")
    suspend fun clearAllHeaders()

    @Query("DELETE FROM waarnemingen")
    suspend fun clearAllWaarnemingen()

    @Query("DELETE FROM sync_logs")
    suspend fun clearAllSyncLogs()

    @Query("SELECT COUNT(*) FROM telling_headers")
    suspend fun countHeaders(): Int

    @Query("SELECT COUNT(*) FROM waarnemingen")
    suspend fun countWaarnemingen(): Int

    @Query("SELECT COUNT(*) FROM sync_logs")
    suspend fun countSyncLogs(): Int
}
