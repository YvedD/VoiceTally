package com.yvesds.vt5.core.database.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.yvesds.vt5.core.database.entities.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class SpeciesWindDatasetRow(
    val begintijd: String,
    val timezoneid: String,
    val windrichting: String,
    val windkracht: String,
    val aantal: Int,
    val aantalterug: Int,
    val telpostid: String = "" // Toegevoegd voor filtering op telpost
)

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
    val aantalterug: Int
)

data class HeaderReturnRow(
    val begintijd: String,
    val timezoneid: String,
    val aantalterug: Int
)

data class WaarnemingTotalsRow(
    val totaal: Int?,
    val totaalterug: Int?
)

@Dao
interface TellingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeader(header: TellingHeader)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeaders(headers: List<TellingHeader>)

    @Query("SELECT * FROM telling_headers WHERE tellingid = :tellingId")
    suspend fun getHeader(tellingId: String): TellingHeader?

    @Query("SELECT * FROM telling_headers ORDER BY begintijd DESC")
    fun getAllHeadersFlow(): Flow<List<TellingHeader>>

    @Query("SELECT * FROM telling_headers ORDER BY begintijd DESC")
    suspend fun getAllHeaders(): List<TellingHeader>

    @Query("DELETE FROM waarnemingen WHERE tellingid = :tellingId")
    suspend fun deleteWaarnemingenVoorTellingById(tellingId: String): Int

    @Query("DELETE FROM telling_headers WHERE tellingid = :tellingId")
    suspend fun deleteHeaderVoorTellingById(tellingId: String): Int

    @Update
    suspend fun updateHeader(header: TellingHeader)

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

    @Query("SELECT * FROM waarnemingen WHERE tellingid = :tellingId ORDER BY tijdstip DESC")
    suspend fun getWaarnemingenList(tellingId: String): List<Waarneming>

    @Query("SELECT * FROM waarnemingen WHERE soortid = :soortId ORDER BY tijdstip DESC")
    suspend fun getWaarnemingenBySoort(soortId: String): List<Waarneming>

    @Query("""
        SELECT * FROM waarnemingen 
        WHERE soortid = :soortId 
        AND (:year IS NULL OR strftime('%Y', datetime(CAST(tijdstip AS INTEGER), 'unixepoch')) = :year)
        ORDER BY tijdstip DESC
    """)
    suspend fun getWaarnemingenBySoortAndYear(soortId: String, year: String?): List<Waarneming>

    @Query("""
        SELECT * FROM waarnemingen 
        WHERE soortid = :soortId 
        AND (:year IS NULL OR strftime('%Y', datetime(CAST(tijdstip AS INTEGER), 'unixepoch')) = :year)
        ORDER BY tijdstip DESC LIMIT :limit OFFSET :offset
    """)
    suspend fun getWaarnemingenBySoortAndYearPaged(soortId: String, year: String?, limit: Int, offset: Int): List<Waarneming>

    @Query("""
        SELECT COUNT(*) FROM waarnemingen 
        WHERE soortid = :soortId 
        AND (:year IS NULL OR strftime('%Y', datetime(CAST(tijdstip AS INTEGER), 'unixepoch')) = :year)
    """)
    suspend fun countWaarnemingenBySoortAndYear(soortId: String, year: String?): Int

    @Query("""
        SELECT * FROM waarnemingen 
        WHERE soortid = :soortId 
        AND (:year IS NULL OR strftime('%Y', datetime(CAST(tijdstip AS INTEGER), 'unixepoch')) = :year)
        ORDER BY tijdstip DESC
    """)
    fun getWaarnemingenPagingSource(soortId: String, year: String?): PagingSource<Int, Waarneming>

    @Query("""
        SELECT SUM(CAST(aantal AS INTEGER)) as totaal, SUM(CAST(aantalterug AS INTEGER)) as totaalterug 
        FROM waarnemingen 
        WHERE soortid = :soortId 
        AND (:year IS NULL OR strftime('%Y', datetime(CAST(tijdstip AS INTEGER), 'unixepoch')) = :year)
    """)
    suspend fun getWaarnemingTotalsForSpecies(soortId: String, year: String?): WaarnemingTotalsRow

    @Query("SELECT * FROM waarnemingen WHERE idLocal = :idLocal AND tellingid = :tellingId LIMIT 1")
    suspend fun getWaarnemingById(idLocal: String, tellingId: String): Waarneming?

    @Query("DELETE FROM telling_headers")
    suspend fun clearAllHeaders()

    @Query("DELETE FROM waarnemingen")
    suspend fun clearAllWaarnemingen()

    @Query("DELETE FROM ai_logs")
    suspend fun clearAllAiLogs()

    @Query("SELECT COUNT(*) FROM ai_logs")
    suspend fun countAiLogs(): Int

    @Query("SELECT COUNT(*) FROM weather_archive")
    suspend fun countWeatherArchive(): Int

    @Query("DELETE FROM weather_archive")
    suspend fun clearWeatherArchive()

    @Query("SELECT COUNT(*) FROM telling_headers")
    suspend fun countHeaders(): Int

    @Query("SELECT tellingid FROM telling_headers")
    suspend fun getAllHeaderIds(): List<String>

    @Query("SELECT COUNT(*) FROM waarnemingen")
    suspend fun countWaarnemingen(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAiLog(log: AiLog)

    @Update
    suspend fun updateAiLog(log: AiLog)

    @Query("SELECT * FROM ai_logs ORDER BY id DESC")
    fun getAllAiLogsFlow(): Flow<List<AiLog>>

    @Query("SELECT * FROM ai_logs WHERE id = :id")
    suspend fun getAiLogById(id: Int): AiLog?

    @Query("SELECT tellingid FROM telling_headers WHERE onlineid = :onlineid LIMIT 1")
    suspend fun getLocalTellingIdForOnlineId(onlineid: String): String?

    @Query("SELECT * FROM telling_headers WHERE onlineid = :onlineid LIMIT 1")
    suspend fun getHeaderByOnlineId(onlineid: String): TellingHeader?

    @Query("SELECT * FROM waarnemingen WHERE onlineid = :onlineid")
    suspend fun getWaarnemingenByOnlineId(onlineid: String): List<Waarneming>

    @Query("SELECT DISTINCT soortid FROM waarnemingen ORDER BY soortid")
    suspend fun getAllUniqueSpeciesIds(): List<String>

    /**
     * Get top species based on hour of the day, filtered by current month for seasonal context.
     */
    @Query("""
        SELECT waarnemingen.soortid, COUNT(*) as count, 0 as percentage, 0 as isZeldzaam, 0 as isPiek
        FROM waarnemingen
        JOIN telling_headers ON waarnemingen.tellingid = telling_headers.tellingid
        WHERE strftime('%H', datetime(CAST(waarnemingen.tijdstip AS INTEGER), 'unixepoch')) = printf('%02d', :hour)
          AND strftime('%m', datetime(
                CASE WHEN CAST(telling_headers.begintijd AS INTEGER) > 9999999999 THEN CAST(telling_headers.begintijd AS INTEGER)/1000 ELSE CAST(telling_headers.begintijd AS INTEGER) END
            , 'unixepoch')) = printf('%02d', :month)
        GROUP BY waarnemingen.soortid
        ORDER BY count DESC
        LIMIT 3
    """)
    suspend fun getTopSpeciesByHour(hour: Int, month: Int): List<com.yvesds.vt5.core.database.dao.AiStatsRow>

    /**
     * Get top species based on wind direction, filtered by current month for seasonal context.
     */
    @Query("""
        SELECT waarnemingen.soortid, COUNT(*) as count, 0 as percentage, 0 as isZeldzaam, 0 as isPiek
        FROM waarnemingen
        JOIN telling_headers ON waarnemingen.tellingid = telling_headers.tellingid
        WHERE telling_headers.windrichting = :wind
          AND strftime('%m', datetime(
                CASE WHEN CAST(telling_headers.begintijd AS INTEGER) > 9999999999 THEN CAST(telling_headers.begintijd AS INTEGER)/1000 ELSE CAST(telling_headers.begintijd AS INTEGER) END
            , 'unixepoch')) = printf('%02d', :month)
        GROUP BY waarnemingen.soortid
        ORDER BY count DESC
        LIMIT 3
    """)
    suspend fun getTopSpeciesByWind(wind: String, month: Int): List<com.yvesds.vt5.core.database.dao.AiStatsRow>

    /**
     * Get top species based on month.
     */
    @Query("""
        SELECT waarnemingen.soortid, COUNT(*) as count, 0 as percentage, 0 as isZeldzaam, 0 as isPiek
        FROM waarnemingen
        JOIN telling_headers ON waarnemingen.tellingid = telling_headers.tellingid
        WHERE strftime('%m', datetime(
            CASE WHEN CAST(telling_headers.begintijd AS INTEGER) > 9999999999 THEN CAST(telling_headers.begintijd AS INTEGER)/1000 ELSE CAST(telling_headers.begintijd AS INTEGER) END
        , 'unixepoch')) = printf('%02d', :month)
        GROUP BY waarnemingen.soortid
        ORDER BY count DESC
        LIMIT 3
    """)
    suspend fun getTopSpeciesByMonth(month: Int): List<com.yvesds.vt5.core.database.dao.AiStatsRow>

    @Query("""
        SELECT begintijd, timezoneid, windrichting, windkracht, aantal, aantalterug, telpostid
        FROM waarnemingen JOIN telling_headers ON waarnemingen.tellingid = telling_headers.tellingid 
        WHERE waarnemingen.soortid = :soortId ORDER BY begintijd DESC
    """)
    suspend fun getWindDatasetForSpecies(soortId: String): List<SpeciesWindDatasetRow>

    @Query("""
        SELECT begintijd, timezoneid, windrichting, windkracht, aantal, aantalterug, telpostid
        FROM waarnemingen JOIN telling_headers ON waarnemingen.tellingid = telling_headers.tellingid 
        WHERE waarnemingen.soortid = :soortId ORDER BY begintijd DESC LIMIT :limit OFFSET :offset
    """)
    suspend fun getWindDatasetForSpeciesPaged(soortId: String, limit: Int, offset: Int): List<SpeciesWindDatasetRow>

    @Query("""
        SELECT COUNT(*) 
        FROM waarnemingen JOIN telling_headers ON waarnemingen.tellingid = telling_headers.tellingid 
        WHERE waarnemingen.soortid = :soortId
    """)
    suspend fun countWindDatasetForSpecies(soortId: String): Int

    @Query("SELECT begintijd, timezoneid, aantalterug FROM waarnemingen JOIN telling_headers ON waarnemingen.tellingid = telling_headers.tellingid WHERE aantalterug > 0")
    suspend fun getAllReturnRows(): List<HeaderReturnRow>

    @Query("""
        SELECT waarnemingen.idLocal, waarnemingen.tellingid, waarnemingen.onlineid as waarnemingOnlineId, 
               telling_headers.onlineid as headerOnlineId, begintijd, timezoneid, windrichting, windkracht, aantal, aantalterug 
        FROM waarnemingen JOIN telling_headers ON waarnemingen.tellingid = telling_headers.tellingid 
        WHERE waarnemingen.soortid = :soortId ORDER BY begintijd DESC
    """)
    suspend fun getWindDebugRowsForSpecies(soortId: String): List<SpeciesWindDebugRow>

    @Query("SELECT DISTINCT strftime('%Y', datetime(CAST(begintijd AS INTEGER), 'unixepoch')) FROM telling_headers ORDER BY begintijd DESC")
    suspend fun getAvailableYears(): List<String>

    @Query("SELECT COUNT(*) FROM waarnemingen WHERE soortid = :speciesId")
    suspend fun countObservationsForSpecies(speciesId: String): Int

    @Query("SELECT SUM(aantal) FROM waarnemingen WHERE CAST(tijdstip AS INTEGER) >= CAST(:start AS INTEGER) AND CAST(tijdstip AS INTEGER) <= CAST(:end AS INTEGER)")
    suspend fun sumCountsInPeriod(start: String, end: String): Int?

    @Query("SELECT soortid, SUM(aantal) as count FROM waarnemingen WHERE CAST(tijdstip AS INTEGER) >= CAST(:start AS INTEGER) AND CAST(tijdstip AS INTEGER) <= CAST(:end AS INTEGER) GROUP BY soortid")
    suspend fun getSpeciesCountsInPeriod(start: String, end: String): List<SpeciesCountRow>

    @Query("SELECT DISTINCT soortid FROM waarnemingen")
    suspend fun getAllSpeciesIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeatherArchive(data: List<WeatherArchive>)

    @Query("SELECT * FROM weather_archive WHERE locationId = :locId AND timeEpoch = :epoch LIMIT 1")
    suspend fun getWeather(locId: String, epoch: Long): WeatherArchive?

    @Query("SELECT COUNT(*) FROM weather_archive WHERE locationId = :locId")
    suspend fun countWeatherForLocation(locId: String): Int

    @Query("SELECT COUNT(*) FROM weather_archive")
    suspend fun countAllWeatherRecords(): Int

    @Query("SELECT DISTINCT CAST(strftime('%Y', datetime(timeEpoch, 'unixepoch')) AS INTEGER) FROM weather_archive ORDER BY timeEpoch DESC")
    suspend fun getWeatherAvailableYears(): List<Int>

    @Query("SELECT DISTINCT CAST(strftime('%m', datetime(timeEpoch, 'unixepoch')) AS INTEGER) FROM weather_archive WHERE strftime('%Y', datetime(timeEpoch, 'unixepoch')) = :year ORDER BY timeEpoch ASC")
    suspend fun getWeatherAvailableMonths(year: String): List<Int>

    @Query("SELECT DISTINCT CAST(strftime('%d', datetime(timeEpoch, 'unixepoch')) AS INTEGER) FROM weather_archive WHERE strftime('%Y', datetime(timeEpoch, 'unixepoch')) = :year AND strftime('%m', datetime(timeEpoch, 'unixepoch')) = :month ORDER BY timeEpoch ASC")
    suspend fun getWeatherAvailableDays(year: String, month: String): List<Int>

    @Query("SELECT DISTINCT locationId FROM weather_archive")
    suspend fun getWeatherAvailableLocations(): List<String>

    @Query("SELECT * FROM weather_archive WHERE locationId = :locId AND timeEpoch >= :start AND timeEpoch <= :end ORDER BY timeEpoch ASC")
    suspend fun getWeatherForDay(locId: String, start: Long, end: Long): List<WeatherArchive>
}

data class SpeciesCountRow(
    val soortid: String,
    val count: Int
)
