package com.yvesds.vt5.features.export

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.core.secure.CredentialsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.concurrent.ConcurrentHashMap

class InvalidExcelResponseException(message: String, val responseBody: String) : Exception(message)

/**
 * Resultaat van een individuele export download.
 */
data class TrektellenDownloadResult(
    val filename: String,
    val bytesWritten: Long,
    val exportType: Int,
    val year: Int,
    val telpostNumber: String,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

/**
 * Verantwoordelijk voor het veilig downloaden van binaire Excel (.xlsx) bestanden 
 * via trektellen.nl en opslaan in Documents/VT5/imports/.
 */
class TrektellenExcelDownloader(private val context: Context) {

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()
    private val safStorageHelper = SaFStorageHelper(context)

    // Configureer OkHttp met CookieJar
    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore.compute(url.host) { _, current ->
                    val list = current ?: mutableListOf()
                    cookies.forEach { newCookie ->
                        list.removeAll { it.name == newCookie.name }
                        list.add(newCookie)
                    }
                    list
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        })
        .build()

    /**
     * Voedt de CookieJar handmatig in met een `ci_session` of andere sessie cookie.
     */
    fun injectSessionCookie(
        host: String = "www.trektellen.nl", 
        cookieName: String = "ci_session", 
        cookieValue: String
    ) {
        val cookie = Cookie.Builder()
            .domain(host)
            .path("/")
            .name(cookieName)
            .value(cookieValue)
            .secure()
            .httpOnly()
            .build()
            
        cookieStore.compute(host) { _, current ->
            val list = current ?: mutableListOf()
            list.removeAll { it.name == cookieName }
            list.add(cookie)
            list
        }
    }

    /**
     * Haalt actieve cookies (zoals `ci_session` en `cf_clearance`) op uit de Android System WebView CookieManager.
     */
    fun getCookiesFromWebView(url: String = "https://www.trektellen.nl"): String? {
        return try {
            CookieManager.getInstance().getCookie(url)
        } catch (e: Exception) {
            Log.e("TrektellenExcelExport", "Fout bij ophalen cookies uit WebView CookieManager: ${e.message}")
            null
        }
    }

    /**
     * Downloadt het Excel bestand voor een specifieke telpost, jaar en exporttype.
     * 
     * Slaat verplicht op in: Documents/VT5/imports/
     * 
     * Bestandsnaam formats:
     * - exporttype = 1 (headerdata) -> `Trektellen_headerdata_[telpostnummer]_[jaartal].xlsx`
     * - exporttype = 2 (waarnemingen/data) -> `Trektellen_data_[telpostnummer]_[jaartal].xlsx`
     */
    suspend fun downloadSingleExport(
        telpostNumber: String,
        year: Int,
        exportType: Int, // 1 = headerdata, 2 = data
        customCookieHeader: String? = null,
        validatePkHeader: Boolean = true
    ): Result<TrektellenDownloadResult> = withContext(Dispatchers.IO) {
        runCatching {
            // Bepaal de vereiste bestandsnaam volgens de vastgelegde specificatie
            val prefix = if (exportType == 1) "headerdata" else "data"
            val expectedFilename = "Trektellen_${prefix}_${telpostNumber}_${year}.xlsx"
            val downloadUrl = "https://www.trektellen.nl/download/export/$telpostNumber"

            val userAgent = try {
                WebSettings.getDefaultUserAgent(context)
            } catch (_: Exception) {
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
            }

            val formBody = FormBody.Builder()
                .add("exporttype", exportType.toString())
                .add("jaar", year.toString())
                .add("richting", "0")
                .add("filetype", "xlsx")
                .build()

            val requestBuilder = Request.Builder()
                .url(downloadUrl)
                .post(formBody)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "nl-NL,nl;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Referer", "https://www.trektellen.nl/")
                .header("Origin", "https://www.trektellen.nl")

            val storedCiSession = CredentialsStore(context).getCiSessionCookie()
            val formattedStoredCookie = storedCiSession?.takeIf { it.isNotBlank() }?.let {
                if (it.contains("=")) it else "ci_session=$it"
            }

            val effectiveCookies = customCookieHeader?.takeIf { it.isNotBlank() } 
                ?: getCookiesFromWebView(downloadUrl)?.takeIf { it.isNotBlank() }
                ?: formattedStoredCookie

            if (!effectiveCookies.isNullOrBlank()) {
                requestBuilder.header("Cookie", effectiveCookies)
            }

            val request = requestBuilder.build()
            val response: Response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                Log.e("TrektellenExcelExport", "HTTP ${response.code} voor $expectedFilename: $errorBody")
                try {
                    com.yvesds.vt5.core.opslag.ErrorLogger.logError(context, "TrektellenExcelExport", "HTTP ${response.code} voor $expectedFilename: $errorBody")
                } catch (_: Exception) {}
                throw InvalidExcelResponseException("HTTP ${response.code}", errorBody)
            }

            val body = response.body ?: throw InvalidExcelResponseException("Lege response body ontvangen.", "")
            val bytes = body.bytes()

            if (bytes.isEmpty()) {
                throw InvalidExcelResponseException("Server retourneerde 0 bytes voor jaar $year (geen gegevens beschikbaar).", "(0 bytes)")
            }

            if (validatePkHeader) {
                val inputStream: InputStream = ByteArrayInputStream(bytes)
                val pushbackStream = PushbackInputStream(inputStream, 1024)
                val header = ByteArray(2)
                val headerBytes = pushbackStream.read(header)

                if (headerBytes < 2 || header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) {
                    val fullError = String(bytes, Charsets.UTF_8).take(1000)
                    throw InvalidExcelResponseException(
                        "Geen geldige binaire 'PK' Excel handtekening ontvangen voor jaar $year.",
                        fullError
                    )
                }
            }

            // Sla het bestand op in Documents/VT5/imports/
            val savedSuccessfully = safStorageHelper.writeImportsFileBytesSuspend(expectedFilename, bytes)

            if (!savedSuccessfully) {
                throw Exception("Kon bestand $expectedFilename niet wegschrijven naar Documents/VT5/imports/")
            }

            Log.i("TrektellenExcelExport", "Succesvol opgeslagen: Documents/VT5/imports/$expectedFilename (${bytes.size} bytes)")

            TrektellenDownloadResult(
                filename = expectedFilename,
                bytesWritten = bytes.size.toLong(),
                exportType = exportType,
                year = year,
                telpostNumber = telpostNumber,
                isSuccess = true
            )
        }
    }
}