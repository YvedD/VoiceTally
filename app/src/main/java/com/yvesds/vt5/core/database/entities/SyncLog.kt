package com.yvesds.vt5.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SyncLog: Houdt de status en antwoorden van server-uploads bij.
 */
@Entity(tableName = "sync_logs")
data class SyncLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tellingid: String,
    val onlineid: String = "",
    val timestamp: String = "",
    val requestPayload: String = "",
    val serverResponse: String = "",
    val success: String = "0" // "1" voor succes, "0" voor falen
)
