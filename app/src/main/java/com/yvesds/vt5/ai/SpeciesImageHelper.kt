package com.yvesds.vt5.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.entities.SpeciesImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * SpeciesImageHelper - Geoptimaliseerde beeldlader via Wikipedia REST API.
 * Snel, vederlicht en met lokale database-cache.
 */
object SpeciesImageHelper {
    private const val TAG = "SpeciesImageHelper"
    private const val REST_API_URL = "https://en.wikipedia.org/api/rest_v1/page/summary/"

    suspend fun getThumbnail(latinName: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (latinName.isNullOrBlank()) return@withContext null
        
        val cleanLatin = latinName.split("/")[0].split("spec.")[0].trim().replace(" ", "_")
        if (cleanLatin.isEmpty()) return@withContext null
        
        val db = VoiceTallyDatabase.getDatabase(VT5App.instance)
        val dao = db.tellingDao()

        // 1. Check DB Cache
        try {
            val cached = dao.getSpeciesImage(cleanLatin)
            if (cached != null) return@withContext BitmapFactory.decodeByteArray(cached.thumbnailBlob, 0, cached.thumbnailBlob.size)
        } catch (_: Exception) {}

        // 2. Haal van Wikipedia REST API (Razendsnel)
        val url = REST_API_URL + cleanLatin
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "VoiceTally/5.0 (yves@voicetally.be)")
            .build()
        
        try {
            VT5App.http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val thumbUrl = json.optJSONObject("thumbnail")?.optString("source") ?: return@withContext null
                
                // Download de afbeelding
                val imgRequest = Request.Builder().url(thumbUrl).build()
                VT5App.http.newCall(imgRequest).execute().use { imgResponse ->
                    if (!imgResponse.isSuccessful) return@withContext null
                    val bytes = imgResponse.body?.bytes() ?: return@withContext null
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    
                    if (bitmap != null) {
                        saveToCache(dao, cleanLatin, bitmap)
                    }
                    return@withContext bitmap
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fetch failed for $cleanLatin: ${e.message}")
            null
        }
    }

    private suspend fun saveToCache(dao: com.yvesds.vt5.core.database.dao.TellingDao, latin: String, bitmap: Bitmap) {
        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
            val blob = stream.toByteArray()
            dao.insertSpeciesImage(SpeciesImage(latinName = latin, thumbnailBlob = blob))
        } catch (_: Exception) {}
    }
}
