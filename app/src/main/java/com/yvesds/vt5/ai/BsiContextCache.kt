package com.yvesds.vt5.ai

import com.yvesds.vt5.core.database.dao.DayCountRowClean
import com.yvesds.vt5.core.database.dao.WeekCountRow
import java.util.concurrent.ConcurrentHashMap

/**
 * BsiContextCache - In-Memory RAM Cache voor BSI 5.0.
 * Voorkomt herhaalde SQL I/O queries tijdens live telsessies en bij het openen van de prognose-schermen.
 * Gegarandeerd thread-safe via ConcurrentHashMap.
 */
object BsiContextCache {

    private val weeklyDistCache = ConcurrentHashMap<String, List<WeekCountRow>>()
    private val dailyDistCache = ConcurrentHashMap<String, List<DayCountRowClean>>()
    private val yesterdayCountCache = ConcurrentHashMap<String, Long>()
    private val pressureGradientCache = ConcurrentHashMap<String, Double>()

    fun getWeeklyDist(speciesId: String): List<WeekCountRow>? = weeklyDistCache[speciesId]
    fun putWeeklyDist(speciesId: String, data: List<WeekCountRow>) {
        weeklyDistCache[speciesId] = data
    }

    fun getDailyDist(speciesId: String): List<DayCountRowClean>? = dailyDistCache[speciesId]
    fun putDailyDist(speciesId: String, data: List<DayCountRowClean>) {
        dailyDistCache[speciesId] = data
    }

    fun getYesterdayCount(siteId: String): Long? = yesterdayCountCache[siteId]
    fun putYesterdayCount(siteId: String, count: Long) {
        yesterdayCountCache[siteId] = count
    }

    fun getPressureGradient(refPoint: String): Double? = pressureGradientCache[refPoint]
    fun putPressureGradient(refPoint: String, gradient: Double) {
        pressureGradientCache[refPoint] = gradient
    }

    /**
     * Wist alle in-memory caches bij een profiel/telpost wissel of bij handmatig vernieuwen.
     */
    fun clearAll() {
        weeklyDistCache.clear()
        dailyDistCache.clear()
        yesterdayCountCache.clear()
        pressureGradientCache.clear()
    }
}
