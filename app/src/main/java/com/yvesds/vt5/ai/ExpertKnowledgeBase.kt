package com.yvesds.vt5.ai

import kotlinx.serialization.Serializable

/**
 * ExpertKnowledgeBase - Slaat de meteorologische vingerafdrukken op van historische piektrek.
 */
@Serializable
data class ExpertKnowledgeBase(
    val signatures: List<GuildPeakSignature> = emptyList(),
    val discoveredKrenten: List<String> = emptyList(), // IDs van soorten met < 100 exemplaren
    val lastUpdated: Long = 0
)

@Serializable
data class GuildPeakSignature(
    val guildName: String,
    val month: Int,
    val averageConditions: List<RegionalWeatherSnapshot>
)

@Serializable
data class RegionalWeatherSnapshot(
    val locationName: String,
    val relativeHour: Int, // -24, -48, -72
    val avgTemp: Float,
    val avgWindDir: Double,
    val avgWindSpeed: Float,
    val avgPressure: Float
)
