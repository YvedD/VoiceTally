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
import java.net.URLEncoder

/**
 * SpeciesImageHelper - Geoptimaliseerde beeldlader via Wikipedia REST API.
 * Snel, vederlicht en met lokale database-cache.
 */
object SpeciesImageHelper {
    private const val TAG = "SpeciesImageHelper"
    private const val REST_API_URL = "https://en.wikipedia.org/api/rest_v1/page/summary/"
    private const val SEARCH_API_URL = "https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=1&srsearch="

    suspend fun getThumbnail(latinName: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (latinName.isNullOrBlank()) return@withContext null
        
        // Opschonen Latijnse naam
        val cleanLatin = latinName.split("/")[0].split("spec.")[0].trim().replace(" ", "_")
        if (cleanLatin.isEmpty()) return@withContext null
        
        val db = VoiceTallyDatabase.getDatabase(VT5App.instance)
        val dao = db.tellingDao()

        // 1. Check DB Cache
        try {
            val cached = dao.getSpeciesImage(cleanLatin)
            if (cached != null) {
                Log.d(TAG, "Cache hit for $cleanLatin")
                return@withContext BitmapFactory.decodeByteArray(cached.thumbnailBlob, 0, cached.thumbnailBlob.size)
            }
        } catch (_: Exception) {}

        // 2. Haal van Wikipedia REST API
        var bitmap = fetchFromRestApi(cleanLatin)
        
        // 3. Fallback: Gebruik Search API als direct niet lukt
        if (bitmap == null) {
            Log.d(TAG, "Direct REST fetch failed for $cleanLatin, trying search...")
            val searchTitle = fetchTitleFromSearch(cleanLatin)
            if (searchTitle != null) {
                bitmap = fetchFromRestApi(searchTitle.replace(" ", "_"))
            }
        }
        
        // 4. Sla op in cache
        if (bitmap != null) {
            saveToCache(dao, cleanLatin, bitmap)
        }
        
        return@withContext bitmap
    }

    private fun fetchFromRestApi(title: String): Bitmap? {
        try {
            val url = REST_API_URL + URLEncoder.encode(title, "UTF-8")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "VoiceTally/5.0 (yves@voicetally.be)")
                .build()
            
            VT5App.http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val thumbUrl = json.optJSONObject("thumbnail")?.optString("source") ?: return null
                
                val imgRequest = Request.Builder().url(thumbUrl).header("User-Agent", "VoiceTally/5.0").build()
                VT5App.http.newCall(imgRequest).execute().use { imgResponse ->
                    if (!imgResponse.isSuccessful) return null
                    val bytes = imgResponse.body?.bytes() ?: return null
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }
        } catch (e: Exception) {
            return null
        }
    }

    private fun fetchTitleFromSearch(query: String): String? {
        try {
            val url = SEARCH_API_URL + URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "VoiceTally/5.0")
                .build()
            
            VT5App.http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val searchResults = json.getJSONObject("query").getJSONArray("search")
                if (searchResults.length() > 0) {
                    return searchResults.getJSONObject(0).getString("title")
                }
            }
        } catch (_: Exception) {}
        return null
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
