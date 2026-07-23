package com.yvesds.vt5.core.database.dao

/**
 * Helper class for AI statistics calculation from Room.
 */
data class AiStatsRow(
    val soortid: String,
    val count: Int,
    val percentage: Int = 0,
    val isZeldzaam: Boolean = false,
    val isPiek: Boolean = false
)
