package com.yvesds.vt5.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yvesds.vt5.core.database.dao.TellingDao
import com.yvesds.vt5.core.database.entities.SyncLog
import com.yvesds.vt5.core.database.entities.TellingHeader
import com.yvesds.vt5.core.database.entities.Waarneming
import com.yvesds.vt5.core.opslag.SaFStorageHelper
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
                val saf = SaFStorageHelper(context)
                val dbFile = getDatabaseFile(context, saf)
                
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VoiceTallyDatabase::class.java,
                    dbFile.absolutePath
                )
                .fallbackToDestructiveMigration() // Voor ontwikkeling, later wijzigen naar echte migraties
                .build()
                INSTANCE = instance
                instance
            }
        }

        private fun getDatabaseFile(context: Context, saf: SaFStorageHelper): File {
            // 1. Definieer locaties
            val externalRoot = context.getExternalFilesDir(null) ?: context.filesDir
            val vt5Root = File(externalRoot, "VT5")
            val dbDir = File(vt5Root, "database")
            if (!dbDir.exists()) dbDir.mkdirs()
            
            val activeDbFile = File(dbDir, "voicetally.db")
            
            // 2. Probeer herstel vanaf SAF als het actieve bestand ontbreekt
            if (!activeDbFile.exists()) {
                restoreFromSaf(context, saf, activeDbFile)
            }

            // 3. Oude migratie vanaf interne (onzichtbare) filesDir (legacy)
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

        private fun restoreFromSaf(context: Context, saf: SaFStorageHelper, targetFile: File) {
            try {
                val vt5Dir = saf.getVt5DirIfExists() ?: return
                val dbDirSaf = vt5Dir.findFile("database") ?: return
                val safDbFile = dbDirSaf.findFile("voicetally.db") ?: return
                
                if (safDbFile.exists() && safDbFile.length() > 0) {
                    context.contentResolver.openInputStream(safDbFile.uri)?.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    // Ook proberen hulpbestanden te herstellen
                    val safWal = dbDirSaf.findFile("voicetally.db-wal")
                    if (safWal != null) {
                        context.contentResolver.openInputStream(safWal.uri)?.use { it.copyTo(File(targetFile.path + "-wal").outputStream()) }
                    }
                    val safShm = dbDirSaf.findFile("voicetally.db-shm")
                    if (safShm != null) {
                        context.contentResolver.openInputStream(safShm.uri)?.use { it.copyTo(File(targetFile.path + "-shm").outputStream()) }
                    }
                    android.util.Log.i("VoiceTallyDatabase", "Database succesvol hersteld vanaf SAF")
                }
            } catch (e: Exception) {
                android.util.Log.e("VoiceTallyDatabase", "Herstel vanaf SAF mislukt: ${e.message}")
            }
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
