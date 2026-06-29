package com.yvesds.vt5.core.secure

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * Opslag voor Trektellen credentials.
 * - Probeert EncryptedSharedPreferences te gebruiken voor veilige opslag.
 * - Valt terug op gewone SharedPreferences als encryptie niet werkt.
 * - save() overschrijft bestaande waarden (bewuste keus: gebruiker kan altijd nieuwe login invullen).
 * 
 * BELANGRIJK: Gebruikt een gecachte SharedPreferences instantie in de companion object
 * om te garanderen dat dezelfde prefs instantie wordt gebruikt tijdens de hele levenscyclus.
 * Dit voorkomt inconsistenties tussen EncryptedSharedPreferences en fallback prefs.
 */
class CredentialsStore(context: Context) {

    companion object {
        private const val TAG = "CredentialsStore"
        private const val PREFS_FILE_NAME = "vt5_secure_prefs"
        private const val FALLBACK_PREFS_FILE_NAME = "vt5_credentials"
        private const val KEY_USER = "trektellen_user"
        private const val KEY_PASS = "trektellen_pass"
        
        // Cached SharedPreferences instantie - garandeert consistentie
        @Volatile
        private var cachedPrefs: SharedPreferences? = null
        private val lock = Any()
        
        /**
         * Haal of maak de SharedPreferences-instantie aan.
         * gebruik 'double-checked locking' voor thread bescherming.
         */
        private fun getOrCreatePrefs(context: Context): SharedPreferences {
            cachedPrefs?.let { return it }
            
            synchronized(lock) {
                cachedPrefs?.let { return it }
                
                val appContext = context.applicationContext
                val prefs = createPreferencesInternal(appContext)
                cachedPrefs = prefs
                return prefs
            }
        }
        
        /**
         * Maakt voorkeuren aan, probeert eerst versleuteld en valt dan terug naar de gewone SharedPreferences.
         */
        private fun createPreferencesInternal(appContext: Context): SharedPreferences {
            Log.i(TAG, "Voorkeur creërens (singleton)...")
            
            // Probeer eerst EncryptedSharedPreferences
            val encryptedPrefs = tryCreateEncryptedPrefsInternal(appContext)
            if (encryptedPrefs != null) {
                return encryptedPrefs
            }
            
            // Val terug naar de gewone SharedPreferences (inloggegevens nog steeds opgeslagen, alleen niet versleuteld)
            Log.w(TAG, "Gebruik van de fallback-reguliere SharedPreferences (onversleuteld maar persistent)")
            return appContext.getSharedPreferences(FALLBACK_PREFS_FILE_NAME, Context.MODE_PRIVATE)
        }
        
        /**
         * Pogingen om EncryptedSharedPreferences aan te maken.
         * Geeft nul terug als versleuteling niet beschikbaar is op dit apparaat.
         */
        private fun tryCreateEncryptedPrefsInternal(appContext: Context): SharedPreferences? {
            // Probeer eerst MasterKey te maken
            val masterKey = try {
                MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            } catch (e: Exception) {
                Log.w(TAG, "De creatie van MasterKey mislukte: ${e.javaClass.simpleName} - ${e.message}")
                return null
            }
            
            // Probeer EncryptedSharedPreferences aan te maken
            return try {
                val prefs = EncryptedSharedPreferences.create(
                    appContext,
                    PREFS_FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                Log.i(TAG, "EncryptedSharedPreferences met success aangemaakt")
                prefs
            } catch (e: Exception) {
                Log.w(TAG, "EncryptedSharedPreferences niet geslaagd: ${e.javaClass.simpleName} - ${e.message}")
                
                // Probeer corrupte data te wissen en probeer het één keer opnieuw
                clearCorruptedPrefsFileInternal(appContext)
                
                try {
                    val prefs = EncryptedSharedPreferences.create(
                        appContext,
                        PREFS_FILE_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                    Log.i(TAG, "EncryptedSharedPreferences aangemaakt na herstel")
                    prefs
                } catch (e2: Exception) {
                    Log.w(TAG, "EncryptedSharedPreferences herstel mislukt: ${e2.javaClass.simpleName} - ${e2.message}")
                    null
                }
            }
        }
        
        /**
         * Verwijdert het corrupte SharedPreferences-bestand van de schijf.
         */
        private fun clearCorruptedPrefsFileInternal(appContext: Context) {
            try {
                val prefsDir = File(appContext.filesDir.parent, "shared_prefs")
                val prefsFile = File(prefsDir, "$PREFS_FILE_NAME.xml")
                if (prefsFile.exists()) {
                    prefsFile.delete()
                    Log.i(TAG, "Beschadigd prefs-bestand verwijderd: ${prefsFile.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Mislukt om het corrupte prefs-bestand te verwijderen: ${e.message}")
            }
        }
    }

    private val prefs: SharedPreferences = getOrCreatePrefs(context)

    fun save(username: String, password: String) {
        Log.i(TAG, "Inloggegevens opslaan - gebruikersnaam: ${if (username.isNotBlank()) "present" else "blank"}, password: ${if (password.isNotBlank()) "present" else "blank"}")
        val result = prefs.edit()
            .putString(KEY_USER, username)
            .putString(KEY_PASS, password)
            .commit()  // Use commit() for synchronous save
        Log.i(TAG, "Inloggegevens opgeslagen: ${if (result) "success" else "failed"}")
        
        // Verify by immediately reading back
        val savedUsername = prefs.getString(KEY_USER, null)
        val savedPassword = prefs.getString(KEY_PASS, null)
        Log.i(TAG, "Verificatie - gebruikersnaam opgeslagen: ${if (savedUsername == username) "OK" else "MISMATCH"}, password saved: ${if (savedPassword == password) "OK" else "MISMATCH"}")
    }

    fun getUsername(): String? {
        return prefs.getString(KEY_USER, null)
    }
    
    fun getPassword(): String? {
        return prefs.getString(KEY_PASS, null)
    }

    fun hasCredentials(): Boolean = !getUsername().isNullOrEmpty() && !getPassword().isNullOrEmpty()

    fun clear() {
        prefs.edit().remove(KEY_USER).remove(KEY_PASS).commit()
        Log.i(TAG, "Inloggegevens opgeschoond")
    }
}
