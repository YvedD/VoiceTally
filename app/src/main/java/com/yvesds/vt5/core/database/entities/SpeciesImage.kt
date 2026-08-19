package com.yvesds.vt5.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SpeciesImage - Lokale cache voor vogelthumbnails (BLOB).
 * Voorkomt herhaalde internet-aanvragen en werkt offline.
 */
@Entity(tableName = "species_images")
data class SpeciesImage(
    @PrimaryKey val latinName: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val thumbnailBlob: ByteArray,
    val lastUpdated: Long = System.currentTimeMillis()
)
