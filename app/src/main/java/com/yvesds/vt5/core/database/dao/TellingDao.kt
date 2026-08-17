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
    val telpostid: String = ""
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

    @Query("SELECT * FROM waarnemingen WHERE soortid = :soortId AND (:year IS NULL OR strftime('%Y', datetime(CAST(tijdstip AS INTEGER), 'unixepoch')) = :year) ORDER BY tijdstip DESC")
    suspend fun getWaarnemingenBySoortAndYear(soortId: String, year: String?): List<Waarneming>

    @Query("SELECT * FROM waarnemingen WHERE soortid = :soortId AND (:year IS NULL OR strftime('%Y', datetime(CAST(tijdstip AS INTEGER), 'unixepoch')) = :year) ORDER BY tijdstip DESC LIMIT :limit OFFSET :offset")
    suspend fun getWaarnemingenBySoortAndYearPaged(soortId: String, year: String?, limit: Int, offset: Int): List<Waarneming>

    @Query("SELECT COUNT(*) FROM waarnemingen WHERE soortid = :soortId AND (:year IS NULL OR strftime('%Y', datetime(CAST(tijdstip AS INTEGER), 'unixepoch')) = :year)")
    suspend fun countWaarnemingenBySoortAndYear(soortId: String, year: String?): Int

    @Query("SELECT * FROM waarnemingen WHERE soortid = :soortId AND (:year IS NULL OR strftime('%Y', datetime(CAST(tijdstip AS INTEGER), 'unixepoch')) = :year) ORDER BY tijdstip DESC")
    fun getWaarnemingenPagingSource(soortId: String, year: String?): PagingSource<Int, Waarneming>

    @Query("SELECT SUM(CAST(aantal AS INTEGER)) as totaal, SUM(CAST(aantalterug AS INTEGER)) as totaalterug FROM waarnemingen WHERE soortid = :soortId AND (:year IS NULL OR strftime('%Y', datetime(CAST(tijdstip AS INTEGER), 'unixepoch')) = :year)")
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

    @Insert
    suspend fun insertAiLog(log: AiLog)

    @Update
    suspend fun updateAiLog(log: AiLog)

    @Query("SELECT * FROM ai_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestAiLog(): AiLog?

    @Query("SELECT * FROM ai_logs ORDER BY timestamp DESC")
    fun getAllAiLogsFlow(): Flow<List<AiLog>>

    @Query("SELECT * FROM ai_logs WHERE id = :id LIMIT 1")
    suspend fun getAiLogById(id: Int): AiLog?

    @Query("SELECT tellingid FROM telling_headers WHERE onlineid = :onlineId LIMIT 1")
    suspend fun getLocalTellingIdForOnlineId(onlineId: String): String?

    @Query("SELECT * FROM telling_headers WHERE onlineid = :onlineId LIMIT 1")
    suspend fun getHeaderByOnlineId(onlineId: String): TellingHeader?

    @Query("SELECT * FROM waarnemingen WHERE onlineid = :onlineId")
    suspend fun getWaarnemingenByOnlineId(onlineId: String): List<Waarneming>

    @Query("SELECT DISTINCT soortid FROM waarnemingen")
    suspend fun getAllUniqueSpeciesIds(): List<String>

    /**
     * Berekent het totaal aantal exemplaren (trek) per soort over de hele database.
     */
    @Query("""
        SELECT soortid, SUM(CAST(aantal AS INTEGER) + CAST(aantalterug AS INTEGER) + CAST(aantal_plus AS INTEGER) + CAST(aantalterug_plus AS INTEGER)) as count
        FROM waarnemingen
        GROUP BY soortid
    """)
    suspend fun getGlobalSpeciesMassa(): List<SpeciesCountRow>

    /**
     * Haalt de totalen op voor één specifieke sessie.
     */
    @Query("""
        SELECT soortid, SUM(CAST(aantal AS INTEGER) + CAST(aantalterug AS INTEGER)) as count
        FROM waarnemingen
        WHERE tellingid = :tellingId
        GROUP BY soortid
    """)
    suspend fun getSessionCounts(tellingId: String): List<SpeciesCountRow>

    /**
     * Haalt de gemiddelde sessie-massa op voor een soort in een bepaald venster.
     */
    @Query("""
        SELECT AVG(sessionTotal) FROM (
            SELECT SUM(CAST(w.aantal AS INTEGER) + CAST(w.aantalterug AS INTEGER)) as sessionTotal
            FROM waarnemingen w
            INNER JOIN telling_headers h ON w.tellingid = h.tellingid
            WHERE w.soortid = :speciesId
            AND (CAST(strftime('%j', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) AS INTEGER) BETWEEN :dayStart AND :dayEnd)
            GROUP BY h.tellingid
        )
    """)
    suspend fun getHistoricalAverageForWindow(speciesId: String, dayStart: Int, dayEnd: Int): Float?

    @Query("SELECT DISTINCT soortid FROM waarnemingen")
    suspend fun getAllSpeciesIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWeatherArchiveIgnore(data: List<WeatherArchive>)

    @Query("""
        SELECT DISTINCT telpostid, strftime('%Y', datetime(CAST(begintijd AS INTEGER), 'unixepoch')) as year 
        FROM telling_headers 
        WHERE TRIM(windrichting) IN ('', 'null', '0') 
           OR TRIM(windkracht) IN ('', 'null', '0') 
           OR TRIM(temperatuur) IN ('', 'null', '0') 
           OR TRIM(bewolking) IN ('', 'null', '0') 
           OR TRIM(hpa) IN ('', 'null', '0')
    """)
    suspend fun getMissingWeatherTelpostYears(): List<TelpostYear>

    @Query("""
        SELECT * FROM telling_headers 
        WHERE TRIM(windrichting) IN ('', 'null', '0') 
           OR TRIM(windkracht) IN ('', 'null', '0') 
           OR TRIM(temperatuur) IN ('', 'null', '0') 
           OR TRIM(bewolking) IN ('', 'null', '0') 
           OR TRIM(hpa) IN ('', 'null', '0')
    """)
    suspend fun getHeadersWithMissingWeather(): List<TellingHeader>

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

    @Query("SELECT DISTINCT telpostid FROM telling_headers ORDER BY telpostid ASC")
    suspend fun getUniqueTelpostIds(): List<String>

    @Query("SELECT MIN(52, (CAST(strftime('%j', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) AS INTEGER) - 1) / 7 + 1) as week, SUM(CAST(w.aantal AS INTEGER) + CAST(w.aantalterug AS INTEGER)) as count FROM waarnemingen w INNER JOIN telling_headers h ON w.tellingid = h.tellingid GROUP BY week ORDER BY week ASC")
    suspend fun getBirdCountsByWeekGlobal(): List<WeekCountRow>

    @Query("SELECT MIN(52, (CAST(strftime('%j', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) AS INTEGER) - 1) / 7 + 1) as week, SUM(CAST(w.aantal AS INTEGER) + CAST(w.aantalterug AS INTEGER)) as count FROM waarnemingen w INNER JOIN telling_headers h ON w.tellingid = h.tellingid WHERE h.telpostid = :siteId GROUP BY week ORDER BY week ASC")
    suspend fun getBirdCountsByWeekForSite(siteId: String): List<WeekCountRow>

    @Query("SELECT MIN(52, (CAST(strftime('%j', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) AS INTEGER) - 1) / 7 + 1) as week, COUNT(*) as count FROM telling_headers h GROUP BY week ORDER BY week ASC")
    suspend fun getSessionCountsByWeekGlobal(): List<WeekCountRow>

    @Query("SELECT MIN(52, (CAST(strftime('%j', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) AS INTEGER) - 1) / 7 + 1) as week, COUNT(*) as count FROM telling_headers h WHERE h.telpostid = :siteId GROUP BY week ORDER BY week ASC")
    suspend fun getSessionCountsByWeekForSite(siteId: String): List<WeekCountRow>

    @Query("SELECT UPPER(TRIM(h.windrichting)) as windrichting, MIN(52, (CAST(strftime('%j', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) AS INTEGER) - 1) / 7 + 1) as week, SUM(CAST(w.aantal AS INTEGER)) as totalAantal, SUM(CAST(w.aantalterug AS INTEGER)) as totalTerug, AVG(CAST(NULLIF(h.windkracht, '') AS FLOAT)) as avgWindForce FROM waarnemingen w INNER JOIN telling_headers h ON w.tellingid = h.tellingid WHERE w.soortid = :speciesId AND (:siteId IS NULL OR h.telpostid = :siteId) AND (:year IS NULL OR strftime('%Y', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) = :year) GROUP BY windrichting, week ORDER BY windrichting, week ASC")
    suspend fun getSpeciesWindStats(speciesId: String, siteId: String?, year: String?): List<SpeciesWindStatsRow>

    @Query("SELECT w.tellingid, w.aantal, w.aantalterug, h.begintijd, h.telpostid FROM waarnemingen w INNER JOIN telling_headers h ON w.tellingid = h.tellingid WHERE w.soortid = :speciesId AND (:siteId IS NULL OR h.telpostid = :siteId) AND (:year IS NULL OR strftime('%Y', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) = :year) ORDER BY h.begintijd DESC LIMIT :limit OFFSET :offset")
    suspend fun getWaarnemingenWithHeaderInfo(speciesId: String, siteId: String?, year: String?, limit: Int, offset: Int): List<WaarnemingWithHeaderInfo>

    @Query("SELECT w.soortid, h.begintijd as sessionStart, w.tijdstip as observationTime, h.windrichting, h.windkracht, h.temperatuur, h.bewolking, h.hpa, h.neerslag, h.telpostid FROM waarnemingen w INNER JOIN telling_headers h ON w.tellingid = h.tellingid WHERE h.status = 'gearchiveerd' OR h.status = 'geupload'")
    suspend fun getRawTrainingData(): List<RawTrainingRow>

    @Query("SELECT SUM(CAST(aantal AS INTEGER) + CAST(aantalterug AS INTEGER)) FROM waarnemingen WHERE CAST(tijdstip AS INTEGER) >= :start AND CAST(tijdstip AS INTEGER) <= :end")
    suspend fun getTotalCountInEpochRange(start: Long, end: Long): Long?

    @Query("SELECT DISTINCT strftime('%Y', datetime(CAST(begintijd AS INTEGER), 'unixepoch')) as year FROM telling_headers ORDER BY year DESC")
    suspend fun getAvailableYears(): List<String?>

    @Query("SELECT SUM(aantal) FROM waarnemingen WHERE CAST(tijdstip AS INTEGER) >= CAST(:start AS INTEGER) AND CAST(tijdstip AS INTEGER) <= CAST(:end AS INTEGER)")
    suspend fun sumCountsInPeriod(start: String, end: String): Int?

    @Query("SELECT soortid, SUM(aantal) as count, 0 as percentage, 0 as isZeldzaam, 0 as isPiek FROM waarnemingen WHERE CAST(tijdstip AS INTEGER) >= CAST(:start AS INTEGER) AND CAST(tijdstip AS INTEGER) <= CAST(:end AS INTEGER) GROUP BY soortid ORDER BY count DESC LIMIT 20")
    suspend fun getTopSpeciesByHour(start: Int, end: Int): List<AiStatsRow>

    @Query("SELECT soortid, SUM(aantal) as count, 0 as percentage, 0 as isZeldzaam, 0 as isPiek FROM waarnemingen w INNER JOIN telling_headers h ON w.tellingid = h.tellingid WHERE h.windrichting = :wind AND CAST(strftime('%m', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) AS INTEGER) = :month GROUP BY soortid ORDER BY count DESC LIMIT 20")
    suspend fun getTopSpeciesByWind(wind: String, month: Int): List<AiStatsRow>

    @Query("SELECT soortid, SUM(aantal) as count, 0 as percentage, 0 as isZeldzaam, 0 as isPiek FROM waarnemingen w INNER JOIN telling_headers h ON w.tellingid = h.tellingid WHERE CAST(strftime('%m', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) AS INTEGER) = :month GROUP BY soortid ORDER BY count DESC LIMIT 20")
    suspend fun getTopSpeciesByMonth(month: Int): List<AiStatsRow>

    @Query("SELECT h.begintijd, h.timezoneid, h.windrichting, h.windkracht, w.aantal, w.aantalterug, h.telpostid FROM waarnemingen w INNER JOIN telling_headers h ON w.tellingid = h.tellingid WHERE w.soortid = :speciesId")
    suspend fun getWindDatasetForSpecies(speciesId: String): List<SpeciesWindDatasetRow>

    @Query("""
        SELECT 
            (CAST(h.begintijd AS INTEGER) / 86400) * 86400 as dayEpoch,
            SUM(CAST(w.aantal AS INTEGER) + CAST(w.aantalterug AS INTEGER) + CAST(w.aantal_plus AS INTEGER) + CAST(w.aantalterug_plus AS INTEGER)) as count
        FROM waarnemingen w
        INNER JOIN telling_headers h ON w.tellingid = h.tellingid
        GROUP BY dayEpoch
    """)
    suspend fun getAllDailyTotals(): List<DayCountRow>

    @Query("""
        SELECT 
            w.soortid, 
            SUM(CAST(w.aantal AS INTEGER) + CAST(w.aantalterug AS INTEGER) + CAST(w.aantal_plus AS INTEGER) + CAST(w.aantalterug_plus AS INTEGER)) as count,
            AVG(CAST(NULLIF(h.temperatuur, '') AS FLOAT)) as avgTemp,
            UPPER(h.windrichting) as mainWind,
            AVG(CAST(NULLIF(h.hpa, '') AS FLOAT)) as avgPressure,
            AVG(CAST(strftime('%H', datetime(CAST(MAX(w.tijdstip, h.begintijd) AS INTEGER), 'unixepoch', 'localtime')) AS INTEGER)) as avgHour,
            MAX(CAST(w.markeren AS INTEGER)) as isRemarkable
        FROM waarnemingen w
        INNER JOIN telling_headers h ON w.tellingid = h.tellingid
        WHERE ((CAST(strftime('%j', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) AS INTEGER) BETWEEN :dayStart AND :dayEnd)
           OR (CAST(strftime('%j', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) AS INTEGER) + 365 BETWEEN :dayStart AND :dayEnd)
           OR (CAST(strftime('%j', datetime(CAST(h.begintijd AS INTEGER), 'unixepoch')) AS INTEGER) - 365 BETWEEN :dayStart AND :dayEnd))
           AND (:useCluster = 0 OR h.telpostid IN (:siteIds))
        GROUP BY w.soortid
        ORDER BY count DESC
        LIMIT 100
    """)
    suspend fun getSpeciesPhenologyProfile(dayStart: Int, dayEnd: Int, siteIds: List<String>, useCluster: Int): List<BsiSpeciesProfile>

    /**
     * Zoekt de top-dagen voor een specifieke set soort-IDs (een gilde).
     * Houdt alleen rekening met migratie (geen lokaal).
     */
    @Query("""
        SELECT 
            (CAST(h.begintijd AS INTEGER) / 86400) * 86400 as dayEpoch,
            SUM(CAST(w.aantal AS INTEGER) + CAST(w.aantalterug AS INTEGER) + CAST(w.aantal_plus AS INTEGER) + CAST(w.aantalterug_plus AS INTEGER)) as totalCount
        FROM waarnemingen w
        INNER JOIN telling_headers h ON w.tellingid = h.tellingid
        WHERE w.soortid IN (:speciesIds)
        GROUP BY dayEpoch
        ORDER BY totalCount DESC
        LIMIT :limit
    """)
    suspend fun getPeakDaysForSpecies(speciesIds: List<String>, limit: Int): List<PeakDayRow>
}

data class SpeciesCountRow(val soortid: String, val count: Int)
data class TelpostYear(val telpostid: String, val year: String)
data class DayCountRow(val dayEpoch: Long, val count: Long)
data class PeakDayRow(val dayEpoch: Long, val totalCount: Long)
data class BsiSpeciesProfile(
    val soortid: String,
    val count: Long,
    val avgTemp: Float?,
    val mainWind: String?,
    val avgBft: Float?,
    val avgPressure: Float?,
    val avgHour: Float?,
    val isRemarkable: Int
)

data class WaarnemingTotalsRow(
    val totaal: Int?,
    val totaalterug: Int?
)

data class WeekCountRow(
    val week: Int,
    val count: Long
)

data class SpeciesWindStatsRow(
    val windrichting: String,
    val week: Int,
    val totalAantal: Long,
    val totalTerug: Long,
    val avgWindForce: Float
)

data class WaarnemingWithHeaderInfo(
    val tellingid: String,
    val aantal: String,
    val aantalterug: String,
    val begintijd: String,
    val telpostid: String
)

data class RawTrainingRow(
    val soortid: String,
    val sessionStart: String,
    val observationTime: String,
    val windrichting: String,
    val windkracht: String,
    val temperatuur: String,
    val bewolking: String,
    val hpa: String,
    val neerslag: String,
    val telpostid: String
)
