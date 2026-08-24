package com.yvesds.vt5.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * DailyAnalysis - Slaat de volledige wetenschappelijke analyse van een teldag op.
 * Dit voorkomt zware herberekeningen en maakt het archief razendsnel.
 */
@Entity(
    tableName = "daily_analysis",
    indices = [Index(value = ["type"])] // NU TOEGEVOEGD
)
data class DailyAnalysis(
    @PrimaryKey val dayEpoch: Long, // Start van de dag in seconden (uniek per dag)
    val type: String, // "LIVE" of "RECONSTRUCTED"
    val weatherJson: String, // Wind, Temp, Druk, Neerslag
    val effortJson: String,  // Inspanning per telpost
    val resultsJson: String, // Soorten, BpH, Sterren, Aantallen
    val corridorJson: String = "", // Ruwe corridor data voor toekomstig gebruik
    val remarks: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
