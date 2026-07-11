package com.yvesds.vt5.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yvesds.vt5.core.database.dao.TellingDao
import com.yvesds.vt5.core.database.entities.SyncLog
import com.yvesds.vt5.core.database.entities.TellingHeader
import com.yvesds.vt5.core.database.entities.Waarneming
import java.io.File

@Database(
    entities = [TellingHeader::class, Waarneming::class, SyncLog::class],
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
                    }
                })
                .fallbackToDestructiveMigration() // Voor ontwikkeling, later wijzigen naar echte migraties
                .build()
                INSTANCE = instance
                instance
            }
        }

        private fun getDatabaseFile(context: Context): File {
            // 1. Definieer locaties
            val externalRoot = context.getExternalFilesDir(null) ?: context.filesDir
            val vt5Root = File(externalRoot, "VT5")
            val dbDir = File(vt5Root, "database")
            if (!dbDir.exists()) dbDir.mkdirs()
            
            val activeDbFile = File(dbDir, "voicetally.db")
            
            // 2. Oude migratie vanaf interne (onzichtbare) filesDir (legacy)
            val oldRoot = File(context.filesDir, "VT5")
            val oldDbFile = File(oldRoot, "database/voicetally.db")
            
            if (oldDbFile.exists() && !activeDbFile.exists()) {
                try {
                    oldDbFile.copyTo(activeDbFile, overwrite = true)
                    // Ook hulpbestanden verhuizen
                    migrateAuxFiles(oldDbFile, activeDbFile)
                    
                    // Oude bestanden verwijderen na succesvolle kopie
                    deleteDbFiles(oldDbFile)
                } catch (e: Exception) {
                    android.util.Log.e("VoiceTallyDatabase", "Migratie vanaf filesDir mislukt: ${e.message}")
                }
            }
            
            return activeDbFile
        }

        private fun migrateAuxFiles(source: File, target: File) {
            File(source.path + "-journal").let { if(it.exists()) it.copyTo(File(target.path + "-journal")) }
            File(source.path + "-shm").let { if(it.exists()) it.copyTo(File(target.path + "-shm")) }
            File(source.path + "-wal").let { if(it.exists()) it.copyTo(File(target.path + "-wal")) }
        }

        private fun deleteDbFiles(baseFile: File) {
            baseFile.delete()
            File(baseFile.path + "-journal").delete()
            File(baseFile.path + "-shm").delete()
            File(baseFile.path + "-wal").delete()
        }
    }
}
