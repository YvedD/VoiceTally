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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeaders(headers: List<TellingHeader>)

    @Query("SELECT * FROM telling_headers WHERE tellingid = :tellingId")
    suspend fun getHeader(tellingId: String): TellingHeader?

    @Query("SELECT * FROM telling_headers ORDER BY CAST(COALESCE(NULLIF(begintijd, ''), '0') AS INTEGER) DESC, tellingid DESC")
    fun getAllHeadersFlow(): Flow<List<TellingHeader>>

    @Query("SELECT * FROM telling_headers ORDER BY CAST(COALESCE(NULLIF(begintijd, ''), '0') AS INTEGER) DESC, tellingid DESC")
    suspend fun getAllHeaders(): List<TellingHeader>

    @Query("DELETE FROM waarnemingen WHERE tellingid = :tellingId")
    suspend fun deleteWaarnemingenVoorTellingById(tellingId: String): Int

    @Query("DELETE FROM telling_headers WHERE tellingid = :tellingId")
    suspend fun deleteHeaderVoorTellingById(tellingId: String): Int

    @Update
    suspend fun updateHeader(header: TellingHeader)

    // Waarneming operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaarneming(waarneming: Waarneming)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaarnemingen(waarnemingen: List<Waarneming>)

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

    // Variant that allows optional year filtering. Pass NULL for all years.
    @Query("""
        SELECT waarnemingen.* FROM waarnemingen
        JOIN telling_headers ON waarnemingen.tellingid = telling_headers.tellingid
        WHERE waarnemingen.soortid = :soortId
          AND (:year IS NULL OR strftime('%Y', datetime(
                CASE WHEN CAST(telling_headers.begintijd AS INTEGER) > 9999999999 THEN CAST(telling_headers.begintijd AS INTEGER)/1000 ELSE CAST(telling_headers.begintijd AS INTEGER) END
            , 'unixepoch')) = :year)
        ORDER BY waarnemingen.tijdstip DESC
    """)
    suspend fun getWaarnemingenBySoortAndYear(soortId: String, year: String?): List<Waarneming>

    @Query("SELECT * FROM waarnemingen WHERE idLocal = :idLocal AND tellingid = :tellingId")
    suspend fun getWaarnemingById(idLocal: String, tellingId: String): Waarneming?


    @Query("DELETE FROM telling_headers")
    suspend fun clearAllHeaders()

    @Query("DELETE FROM waarnemingen")
    suspend fun clearAllWaarnemingen()

    @Query("DELETE FROM sync_logs")
    suspend fun clearAllSyncLogs()

    @Query("SELECT COUNT(*) FROM telling_headers")
    suspend fun countHeaders(): Int

    @Query("SELECT tellingid FROM telling_headers")
    suspend fun getAllHeaderIds(): List<String>

    @Query("SELECT COUNT(*) FROM waarnemingen")
    suspend fun countWaarnemingen(): Int

    // Lookup helpers by server online id
    @Query("SELECT tellingid FROM telling_headers WHERE onlineid = :onlineid LIMIT 1")
    suspend fun getLocalTellingIdForOnlineId(onlineid: String): String?

    @Query("SELECT * FROM telling_headers WHERE onlineid = :onlineid LIMIT 1")
    suspend fun getHeaderByOnlineId(onlineid: String): TellingHeader?

    @Query("SELECT * FROM waarnemingen WHERE onlineid = :onlineid")
    suspend fun getWaarnemingenByOnlineId(onlineid: String): List<Waarneming>

    @Query("SELECT COUNT(*) FROM sync_logs")
    suspend fun countSyncLogs(): Int

    @Query("""
        SELECT
            telling_headers.begintijd AS begintijd,
            telling_headers.timezoneid AS timezoneid,
            telling_headers.windrichting AS windrichting,
            telling_headers.windkracht AS windkracht,
            CAST(COALESCE(NULLIF(waarnemingen.aantal, ''), '0') AS INTEGER) AS aantal
        FROM waarnemingen
        JOIN telling_headers ON waarnemingen.tellingid = telling_headers.tellingid
        WHERE waarnemingen.soortid = :soortId
          AND telling_headers.begintijd IS NOT NULL
          AND telling_headers.begintijd != ''
          AND telling_headers.windrichting IS NOT NULL
          AND telling_headers.windrichting != ''
          AND (:year IS NULL OR strftime('%Y', datetime(
                CASE WHEN CAST(telling_headers.begintijd AS INTEGER) > 9999999999 THEN CAST(telling_headers.begintijd AS INTEGER)/1000 ELSE CAST(telling_headers.begintijd AS INTEGER) END
            , 'unixepoch')) = :year)
        ORDER BY CAST(telling_headers.begintijd AS INTEGER) ASC
    """)
    suspend fun getWindDatasetForSpecies(soortId: String, year: String?): List<SpeciesWindDatasetRow>

    // Retrieve distinct years available from telling_headers (derived from begintijd)
    @Query("""
        SELECT DISTINCT strftime('%Y', datetime(
            CASE WHEN CAST(begintijd AS INTEGER) > 9999999999 THEN CAST(begintijd AS INTEGER)/1000 ELSE CAST(begintijd AS INTEGER) END
        , 'unixepoch')) as year
        FROM telling_headers
        WHERE begintijd IS NOT NULL AND begintijd != ''
        ORDER BY year DESC
    """)
    suspend fun getAvailableYears(): List<String>
}

data class SpeciesWindDatasetRow(
    val begintijd: String,
    val timezoneid: String,
    val windrichting: String,
    val windkracht: String,
    val aantal: Int,
)
