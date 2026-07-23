package com.yvesds.vt5.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AiLog: Houdt de geschiedenis van AI-prognoses en de bijbehorende gebruikersbeoordelingen bij.
 * Dit vervangt de ongebruikte sync_logs tabel.
 */
@Entity(tableName = "ai_logs")
data class AiLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tellingid: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String, // "metadata" of "forecast"
    val requestContext: String = "", // JSON met wind, temp, etc.
    val suggestions: String = "", // JSON met getoonde soorten/kansen
    var rating: Int = 0, // 0-5 sterren
    var feedback: String = "" // Tekstuele feedback
)
