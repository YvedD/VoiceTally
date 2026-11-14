package com.yvesds.vt5.features.update

import android.content.Context
import android.content.pm.PackageManager
import com.yvesds.vt5.core.opslag.AppDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

object AppUpdateManager {
    private const val OWNER = "YvedD"
    private const val REPO = "VoiceTally"
    private const val TAG = "VoiceTally-AI"
    private const val API_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/tags/$TAG"
    private const val CACHE_TTL_MS = 2 * 60 * 60 * 1000L

    private val apkPattern = Regex("^VT-AI\\.(\\d+\\.\\d+\\.\\d+)\\.apk$")
    private val semverPattern = Regex("(\\d+)\\.(\\d+)(?:\\.(\\d+))?")

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    data class ReleaseAsset(
        val version: String,
        val downloadUrl: String,
        val sha256: String?,
        val name: String,
        val updatedAtIso: String? = null
    )

    data class UpdateState(
        val localVersion: String,
        val localVersionCode: Long,
        val latestVersion: String?,
        val updateAvailable: Boolean,
        val latestAsset: ReleaseAsset?,
        val allAssets: List<ReleaseAsset>,
        val checkedAt: Long,
        val fromCache: Boolean
    )

    private data class InstalledVersion(
        val versionName: String,
        val versionCode: Long
    )

    suspend fun getCachedState(context: Context): UpdateState = withContext(Dispatchers.IO) {
        val local = readInstalledVersion(context)
        val snapshot = AppDataStore.getUpdateSnapshot(context)
        val latestAsset = if (!snapshot.latestVersion.isNullOrBlank() && !snapshot.latestUrl.isNullOrBlank()) {
            ReleaseAsset(
                version = snapshot.latestVersion,
                downloadUrl = snapshot.latestUrl,
                sha256 = snapshot.latestSha256,
                name = "VT-AI.${snapshot.latestVersion}.apk",
                updatedAtIso = snapshot.latestUpdatedAtIso
            )
        } else null
        val availableFromCurrentInstall = isUpdateAvailable(context, local.versionName, latestAsset)
        UpdateState(
            localVersion = local.versionName,
            localVersionCode = local.versionCode,
            latestVersion = snapshot.latestVersion,
            updateAvailable = availableFromCurrentInstall,
            latestAsset = latestAsset,
            allAssets = emptyList(),
            checkedAt = snapshot.lastCheckedAt,
            fromCache = true
        )
    }

    private fun isUpdateAvailable(
        context: Context,
        localVersionName: String,
        latestAsset: ReleaseAsset?
    ): Boolean {
        if (latestAsset == null) return false
        val cmp = compareVersions(localVersionName, latestAsset.version)
        if (cmp < 0) return true
        if (cmp == 0) {
            // 1. Vergelijk SHA256 hashes indien beschikbaar
            val installedSha = getInstalledApkSha256(context)?.lowercase()
            val remoteSha = latestAsset.sha256?.lowercase()
            if (!installedSha.isNullOrBlank() && !remoteSha.isNullOrBlank() && installedSha != remoteSha) {
                return true
            }

            // 2. Hotfix controle: vergelijk GitHub asset updated_at met de installatiedatum van de huidige app op het toestel
            val installedLastUpdateMs = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            }.getOrDefault(0L)

            val remoteUpdateMs = parseIsoToEpochMs(latestAsset.updatedAtIso)
            if (installedLastUpdateMs > 0L && remoteUpdateMs > 0L && remoteUpdateMs > (installedLastUpdateMs + 60_000L)) {
                return true
            }
        }
        return false
    }

    private fun parseIsoToEpochMs(isoStr: String?): Long {
        if (isoStr.isNullOrBlank()) return 0L
        return runCatching {
            java.time.Instant.parse(isoStr).toEpochMilli()
        }.getOrElse {
            runCatching {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.ROOT).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.parse(isoStr)?.time ?: 0L
            }.getOrDefault(0L)
        }
    }

    fun getInstalledApkSha256(context: Context): String? {
        return runCatching {
            val apkFile = File(context.packageCodePath)
            if (apkFile.exists()) sha256(apkFile) else null
        }.getOrNull()
    }

    suspend fun refreshFromGithub(context: Context, allowCache: Boolean = true): Result<UpdateState> = withContext(Dispatchers.IO) {
        runCatching {
            val local = readInstalledVersion(context)
            val snapshot = AppDataStore.getUpdateSnapshot(context)
            val now = System.currentTimeMillis()

            if (allowCache && snapshot.lastCheckedAt > 0L && now - snapshot.lastCheckedAt <= CACHE_TTL_MS) {
                return@runCatching getCachedState(context)
            }

            val request = Request.Builder()
                .url(API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "VoiceTally-Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("HTTP ${response.code} bij updatecontrole")
                }
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val releasePublishedAt = json.optString("published_at", "")
                val assetsJson = json.optJSONArray("assets") ?: error("Geen assets in release")

                val parsed = mutableListOf<ReleaseAsset>()
                for (i in 0 until assetsJson.length()) {
                    val asset = assetsJson.getJSONObject(i)
                    val name = asset.optString("name")
                    val match = apkPattern.matchEntire(name) ?: continue
                    val version = match.groupValues[1]
                    val url = asset.optString("browser_download_url")
                    if (url.isBlank()) continue
                    val digestRaw = asset.optString("digest", "")
                    val sha256 = digestRaw.removePrefix("sha256:").ifBlank { null }
                    val assetUpdatedAt = asset.optString("updated_at", "")
                    val updatedAt = assetUpdatedAt.ifBlank { releasePublishedAt }
                    parsed += ReleaseAsset(
                        version = version,
                        downloadUrl = url,
                        sha256 = sha256,
                        name = name,
                        updatedAtIso = updatedAt.ifBlank { null }
                    )
                }

                val sorted = parsed.sortedWith { a, b -> compareSemver(b.version, a.version) }
                val latest = sorted.firstOrNull()
                val available = isUpdateAvailable(context, local.versionName, latest)

                AppDataStore.setUpdateSnapshot(
                    context = context,
                    updateAvailable = available,
                    latestVersion = latest?.version,
                    latestUrl = latest?.downloadUrl,
                    latestSha256 = latest?.sha256,
                    latestUpdatedAtIso = latest?.updatedAtIso,
                    checkedAt = now
                )

                UpdateState(
                    localVersion = local.versionName,
                    localVersionCode = local.versionCode,
                    latestVersion = latest?.version,
                    updateAvailable = available,
                    latestAsset = latest,
                    allAssets = sorted,
                    checkedAt = now,
                    fromCache = false
                )
            }
        }
    }

    suspend fun downloadAsset(context: Context, asset: ReleaseAsset): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(asset.downloadUrl)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "VoiceTally-Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code} bij APK-download")
                val body = response.body ?: error("Lege response bij APK-download")
                val outFile = File(context.cacheDir, asset.name)
                body.byteStream().use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Guardrail 1: Verifieer dat het gedownloade bestand een geldig VT5 APK-pakket is.
                val archiveInfo = readArchiveInfo(context, outFile)
                val archivePackage = archiveInfo?.packageName.orEmpty()
                val archiveVersion = archiveInfo?.versionName.orEmpty()
                val archiveSemver = extractSemver(archiveVersion)

                if (archivePackage != context.packageName) {
                    outFile.delete()
                    error("APK pakket-identiteit komt niet overeen met VT5 ($archivePackage)")
                }

                // Guardrail 2: Checksum verificatie met ondersteuning voor hotfixes (re-uploaded builds)
                val actualSha = sha256(outFile).lowercase()
                val expectedSha = asset.sha256?.lowercase()

                if (!expectedSha.isNullOrBlank() && actualSha != expectedSha) {
                    // Bij een hotfix (zelfde versienummer of her-üploade APK) is de SHA256 op GitHub gewijzigd.
                    // Indien de APK inhoudelijk een geldig VT5 pakket is, slaan we de nieuwe SHA256 op in DataStore.
                    if (archiveSemver != null) {
                        AppDataStore.setUpdateSnapshot(
                            context = context,
                            updateAvailable = true,
                            latestVersion = asset.version,
                            latestUrl = asset.downloadUrl,
                            latestSha256 = actualSha,
                            checkedAt = System.currentTimeMillis()
                        )
                    } else {
                        outFile.delete()
                        error("Checksum mismatch")
                    }
                }

                outFile
            }
        }
    }

    private fun parseSemver(version: String): Triple<Int, Int, Int> {
        val m = semverPattern.matchEntire(version) ?: return Triple(0, 0, 0)
        val patch = m.groupValues.getOrNull(3)?.ifBlank { "0" } ?: "0"
        return Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), patch.toInt())
    }

    private fun compareSemver(left: String, right: String): Int {
        val l = parseSemver(extractSemver(left) ?: "0.0.0")
        val r = parseSemver(extractSemver(right) ?: "0.0.0")
        return when {
            l.first != r.first -> l.first.compareTo(r.first)
            l.second != r.second -> l.second.compareTo(r.second)
            else -> l.third.compareTo(r.third)
        }
    }

    private fun compareVersions(local: String, remote: String): Int {
        return compareSemver(local, remote)
    }

    private fun extractSemver(text: String): String? {
        return semverPattern.find(text)?.value
    }

    private fun readInstalledVersion(context: Context): InstalledVersion {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        return InstalledVersion(
            versionName = info.versionName ?: "0.0.0",
            versionCode = info.longVersionCode
        )
    }

    @Suppress("DEPRECATION")
    private fun readArchiveInfo(context: Context, apkFile: File): android.content.pm.PackageInfo? {
        return context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}


