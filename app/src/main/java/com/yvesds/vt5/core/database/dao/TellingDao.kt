package com.yvesds.vt5.core.database.dao

import androidx.room.*
import com.yvesds.vt5.core.database.entities.TellingHeader
import com.yvesds.vt5.core.database.entities.Waarneming
import kotlinx.coroutines.flow.Flow
import androidx.paging.PagingSource

// lightweight projection used for chart aggregation
data class SpeciesWindDatasetRow(
    val begintijd: String,
    val timezoneid: String,
    val windrichting: String,
    val windkracht: String,
    val aantal: Int,
    val aantalterug: Int,
)

// Extended debug row including ids from both waarnemingen and telling_headers for troubleshooting
data class SpeciesWindDebugRow(
    val idLocal: String,
    val tellingid: String,
    val waarnemingOnlineId: String,
    val headerOnlineId: String,
    val begintijd: String,
    val timezoneid: String,
    val windrichting: String,
    val windkracht: String,
    val aantal: Int,
    val aantalterug: Int,
)

// Used by DatabaseBeheerScherm to retrieve aantalterug values together with header timestamp/timezone
data class HeaderReturnRow(
    val begintijd: String,
    val timezoneid: String,
    val aantalterug: Int,
)

// Totals projection for overzicht (sum of aantal and aantalterug)
data class WaarnemingTotalsRow(
    val totaal: Int?,
    val totaalterug: Int?,
)

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

    // Paged variant to avoid loading huge resultsets in a single query
    @Query("""
        SELECT waarnemingen.* FROM waarnemingen
        JOIN telling_headers ON waarnemingen.tellingid = telling_headers.tellingid
        WHERE waarnemingen.soortid = :soortId
          AND (:year IS NULL OR strftime('%Y', datetime(
                CASE WHEN CAST(telling_headers.begintijd AS INTEGER) > 9999999999 THEN CAST(telling_headers.begintijd AS INTEGER)/1000 ELSE CAST(telling_headers.begintijd AS INTEGER) END
              , 'unixepoch')) = :year)
        ORDER BY waarnemingen.tijdstip DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getWaarnemingenBySoortAndYearPaged(soortId: String, year: String?, limit: Int, offset: Int): List<Waarneming>

    @Query("""
        SELECT COUNT(*) FROM waarnemingen
        JOIN telling_headers ON waarnemingen.tellingid = telling_headers.tellingid
        WHERE waarnemingen.soortid = :soortId
          AND (:year IS NULL OR strftime('%Y', datetime(
                CASE WHEN CAST(telling_headers.begintijd AS INTEGER) > 9999999999 THEN CAST(telling_headers.begintijd AS INTEGER)/1000 ELSE CAST(telling_headers.begintijd AS INTEGER) END
              , 'unixepoch')) = :year)
    """)
    suspend fun countWaarnemingenBySoortAndYear(soortId: String, year: String?): Int

    /** PagingSource variant usable by Paging3 */
    @Query("""
        SELECT waarnemingen.* FROM waarnemingen
        JOIN telling_headers ON waarnemingen.tellingid = telling_headers.tellingid
        WHERE waarnemingen.soortid = :soortId
          AND (:year IS NULL OR strftime('%Y', datetime(
                CASE WHEN CAST(telling_headers.begintijd AS INTEGER) > 9999999999 THEN CAST(telling_headers.begintijd AS INTEGER)/1000 ELSE CAST(telling_headers.begintijd AS INTEGER) END
              , 'unixepoch')) = :year)
        ORDER BY waarnemingen.tijdstip DESC
    """)
    fun getWaarnemingenPagingSource(soortId: String, year: String?): PagingSource<Int, Waarneming>

    /** Totals (sum) for aantal and aantalterug for a species/year */
    @Query("""
        SELECT
            CAST(COALESCE(SUM(CAST(NULLIF(waarnemingen.aantal, '') AS INTEGER)),0) AS INTEGER) AS totaal,
            CAST(COALESCE(SUM(CAST(NULLIF(waarnemingen.aantalterug, '') AS INTEGER)),0) AS INTEGER) AS totaalterug
        FROM waarnemingen
        JOIN telling_headers ON waarnemingen.tellingid = telling_headers.tellingid
        WHERE waarnemingen.soortid = :soortId
          AND (:year IS NULL OR strftime('%Y', datetime(
                CASE WHEN CAST(telling_headers.begintijd AS INTEGER) > 9999999999 THEN CAST(telling_headers.begintijd AS INTEGER)/1000 ELSE CAST(telling_headers.begintijd AS INTEGER) END
              , 'unixepoch')) = :year)
    """)
    suspend fun getWaarnemingTotalsForSpecies(soortId: String, year: String?): WaarnemingTotalsRow

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
            CAST(COALESCE(NULLIF(waarnemingen.aantal, ''), '0') AS INTEGER) AS aantal,
            CAST(COALESCE(NULLIF(waarnemingen.aantalterug, ''), '0') AS INTEGER) AS aantalterug
        FROM waarnemingen
        JOIN telling_headers ON waarnemingen.tellingid = telling_headers.tellingid
        WHERE waarnemingen.soortid = :soortId
          AND telling_headers.begintijd IS NOT NULL
          AND telling_headers.begintijd != ''
          AND telling_headers.windrichting IS NOT NULL
          AND telling_headers.windrichting != ''
        ORDER BY CAST(telling_headers.begintijd AS INTEGER) ASC
    """)
    suspend fun getWindDatasetForSpecies(soortId: String): List<SpeciesWindDatasetRow>

    // Paged variant for wind dataset to avoid loading very large datasets at once
    @Query("""
        SELECT
            telling_headers.begintijd AS begintijd,
            telling_headers.timezoneid AS timezoneid,
            telling_headers.windrichting AS windrichting,
            telling_headers.windkracht AS windkracht,
            CAST(COALESCE(NULLIF(waarnemingen.aantal, ''), '0') AS INTEGER) AS aantal,
            CAST(COALESCE(NULLIF(waarnemingen.aantalterug, ''), '0') AS INTEGER) AS aantalterug
        FROM waarnemingen
        JOIN telling_headers ON waarnemingen.tellingid = telling_headers.tellingid
        WHERE waarnemingen.soortid = :soortId
          AND telling_headers.begintijd IS NOT NULL
          AND telling_headers.begintijd != ''
          AND telling_headers.windrichting IS NOT NULL
          AND telling_headers.windrichting != ''
        ORDER BY CAST(telling_headers.begintijd AS INTEGER) ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getWindDatasetForSpeciesPaged(soortId: String, limit: Int, offset: Int): List<SpeciesWindDatasetRow>

    @Query("""
        SELECT COUNT(*) FROM waarnemingen
        JOIN telling_headers ON waarnemingen.tellingid = telling_headers.tellingid
        WHERE waarnemingen.soortid = :soortId
          AND telling_headers.begintijd IS NOT NULL
          AND telling_headers.begintijd != ''
          AND telling_headers.windrichting IS NOT NULL
          AND telling_headers.windrichting != ''
    """)
    suspend fun countWindDatasetForSpecies(soortId: String): Int

    @Query("""
        SELECT
            telling_headers.begintijd AS begintijd,
            telling_headers.timezoneid AS timezoneid,
            CAST(COALESCE(NULLIF(waarnemingen.aantalterug, ''), '0') AS INTEGER) AS aantalterug
        FROM waarnemingen
        JOIN telling_headers ON waarnemingen.tellingid = telling_headers.tellingid
        WHERE telling_headers.begintijd IS NOT NULL
          AND telling_headers.begintijd != ''
        ORDER BY CAST(telling_headers.begintijd AS INTEGER) ASC
    """)
    suspend fun getAllReturnRows(): List<HeaderReturnRow>

    @Query("""
        SELECT
            waarnemingen.idLocal AS idLocal,
            waarnemingen.tellingid AS tellingid,
            waarnemingen.onlineid AS waarnemingOnlineId,
            telling_headers.onlineid AS headerOnlineId,
            telling_headers.begintijd AS begintijd,
            telling_headers.timezoneid AS timezoneid,
            telling_headers.windrichting AS windrichting,
            telling_headers.windkracht AS windkracht,
            CAST(COALESCE(NULLIF(waarnemingen.aantal, ''), '0') AS INTEGER) AS aantal,
            CAST(COALESCE(NULLIF(waarnemingen.aantalterug, ''), '0') AS INTEGER) AS aantalterug
        FROM waarnemingen
        JOIN telling_headers ON waarnemingen.tellingid = telling_headers.tellingid
        WHERE waarnemingen.soortid = :soortId
          AND telling_headers.begintijd IS NOT NULL
          AND telling_headers.begintijd != ''
        ORDER BY CAST(telling_headers.begintijd AS INTEGER) ASC
    """)
    suspend fun getWindDebugRowsForSpecies(soortId: String): List<SpeciesWindDebugRow>

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
