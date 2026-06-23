package com.yvesds.vt5.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.yvesds.vt5.core.database.dao.TellingDao
import com.yvesds.vt5.core.database.entity.ObservationEntity
import com.yvesds.vt5.core.database.entity.TellingEntity

/**
 * Room Database para VoiceTally.
 * Contiene tablas para tellings (sesiones) y observations (avistamientos).
 */
@Database(
    entities = [
        TellingEntity::class,
        ObservationEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class VT5Database : RoomDatabase() {
    abstract fun tellingDao(): TellingDao
}

