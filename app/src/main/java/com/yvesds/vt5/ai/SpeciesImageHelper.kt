package com.yvesds.vt5.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import com.yvesds.vt5.VT5App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * SpeciesImageHelper - Haalt on-the-fly vogelthumbnails op van Wikipedia/Wikimedia.
 */
object SpeciesImageHelper {
    private const val TAG = "SpeciesImage"
    private val cache = LruCache<String, Bitmap>(100) 

    suspend fun getThumbnail(latinName: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (latinName.isNullOrBlank()) return@withContext null
        
        // Alleen Genus + species (Wikipedia voorkeur)
        val cleanName = latinName.split(" ").take(2).joinToString(" ")
        
        cache.get(cleanName)?.let { return@withContext it }

        try {
            // Wikipedia API met redirects en User-Agent
            val encodedName = URLEncoder.encode(cleanName, "UTF-8").replace("+", "%20")
            val apiUrl = "https://en.wikipedia.org/w/api.php?action=query&titles=$encodedName&prop=pageimages&format=json&pithumbsize=250&redirects=1"
            
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "VoiceTally/5.0 (Android; Contact: yvesds@example.com)")
                .build()
                
            val response = VT5App.http.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "API Error: ${response.code} voor $cleanName")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val query = json.optJSONObject("query") ?: return@withContext null
            val pages = query.optJSONObject("pages") ?: return@withContext null
            val keys = pages.keys()
            
            if (!keys.hasNext()) return@withContext null
            val pageId = keys.next()
            if (pageId == "-1") {
                Log.d(TAG, "Geen pagina gevonden voor: $cleanName")
                return@withContext null
            }

            val page = pages.getJSONObject(pageId)
            if (!page.has("thumbnail")) {
                Log.d(TAG, "Geen thumbnail beschikbaar voor: $cleanName")
                return@withContext null
            }
            
            val imageUrl = page.getJSONObject("thumbnail").getString("source")

            // Download de foto zelf
            val imgRequest = Request.Builder()
                .url(imageUrl)
                .header("User-Agent", "VoiceTally/5.0")
                .build()
                
            val imgResponse = VT5App.http.newCall(imgRequest).execute()
            val bytes = imgResponse.body?.bytes() ?: return@withContext null
            
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                cache.put(cleanName, bitmap)
                Log.i(TAG, "Succesvol geladen: $cleanName")
            }
            return@withContext bitmap
            
        } catch (e: Exception) {
            Log.w(TAG, "Fout bij ophalen $cleanName: ${e.message}")
            null
        }
    }
}
