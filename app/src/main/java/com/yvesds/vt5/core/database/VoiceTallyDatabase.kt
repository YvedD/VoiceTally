package com.yvesds.vt5.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yvesds.vt5.core.database.dao.TellingDao
import com.yvesds.vt5.core.database.entities.*
import java.io.File

@Database(
    entities = [
        TellingHeader::class, 
        Waarneming::class, 
        AiLog::class, 
        WeatherArchive::class,
        DailyAnalysis::class,
        SpeciesImage::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VoiceTallyDatabase : RoomDatabase() {
    abstract fun tellingDao(): TellingDao

    companion object {
        @Volatile
        private var INSTANCE: VoiceTallyDatabase? = null

        fun getDatabase(context: Context): VoiceTallyDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbFile = getDatabaseFile(context)
                
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VoiceTallyDatabase::class.java,
                    dbFile.absolutePath
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA foreign_keys = ON")
                        // Optimalisaties voor massa-data
                        try {
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_waarnemingen_soortid_tijdstip ON waarnemingen(soortid, tijdstip)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_waarnemingen_tijdstip ON waarnemingen(tijdstip)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_telling_headers_begintijd ON telling_headers(begintijd)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_telling_headers_telpostid ON telling_headers(telpostid)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_weather_archive_loc_time ON weather_archive(locationId, timeEpoch)")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_daily_analysis_type ON daily_analysis(type)")
                        } catch (_: Exception) {}
                    }
                })
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        private fun getDatabaseFile(context: Context): File {
            val externalRoot = context.getExternalFilesDir(null) ?: context.filesDir
            val vt5Root = File(externalRoot, "VT5")
            val dbDir = File(vt5Root, "database")
            if (!dbDir.exists()) dbDir.mkdirs()
            return File(dbDir, "voicetally.db")
        }
    }
}
