package com.yvesds.vt5.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        SpeciesImage::class,
        SpeciesPhenologyVault::class,
        SyncLog::class
    ],
    version = 2, 
    exportSchema = false
)
abstract class VoiceTallyDatabase : RoomDatabase() {
    abstract fun tellingDao(): TellingDao

    companion object {
        @Volatile
        private var INSTANCE: VoiceTallyDatabase? = null

        /**
         * Migratie van versie 1 naar 2: Voegt tabellen en ontbrekende indexen toe.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Nieuwe tabellen
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `species_phenology_vault` (
                        `speciesId` TEXT NOT NULL, `clusterId` TEXT NOT NULL, 
                        `dailyBphSeries` TEXT NOT NULL, `peakSpring` TEXT NOT NULL, 
                        `peakAutumn` TEXT NOT NULL, PRIMARY KEY(`speciesId`, `clusterId`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `sync_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tellingid` TEXT NOT NULL, 
                        `onlineid` TEXT NOT NULL, `timestamp` TEXT NOT NULL, 
                        `requestPayload` TEXT NOT NULL, `serverResponse` TEXT NOT NULL, `success` TEXT NOT NULL
                    )
                """.trimIndent())

                // 2. Ontbrekende indexen voor v2 (Volgens Room standaard)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_telling_headers_telpostid` ON `telling_headers` (`telpostid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_waarnemingen_tijdstip` ON `waarnemingen` (`tijdstip`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_waarnemingen_soortid_tijdstip` ON `waarnemingen` (`soortid`, `tijdstip`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_analysis_type` ON `daily_analysis` (`type`)")

                // 3. RUIM OUDE HANDMATIGE INDEXEN OP (Fix voor IllegalStateException)
                // Deze veroorzaken het conflict tussen 'Found' en 'Expected'
                db.execSQL("DROP INDEX IF EXISTS `idx_telling_headers_begintijd`")
                db.execSQL("DROP INDEX IF EXISTS `idx_telling_headers_telpostid`")
                db.execSQL("DROP INDEX IF EXISTS `idx_waarnemingen_soortid_tijdstip`")
                db.execSQL("DROP INDEX IF EXISTS `idx_waarnemingen_tijdstip`")
                db.execSQL("DROP INDEX IF EXISTS `idx_daily_analysis_type`")
                db.execSQL("DROP INDEX IF EXISTS `idx_weather_archive_loc_time`")
            }
        }

        fun getDatabase(context: Context): VoiceTallyDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbFile = getDatabaseFile(context)
                val instance = Room.databaseBuilder(context.applicationContext, VoiceTallyDatabase::class.java, dbFile.absolutePath)
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA foreign_keys = ON")
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
