package com.yvesds.vt5.core.database.entities

import kotlinx.serialization.Serializable

@Serializable
data class TelpostLocatie(
    val telpostid: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class TelpostLocatiesRoot(
    val locaties: List<TelpostLocatie> = emptyList()
)
