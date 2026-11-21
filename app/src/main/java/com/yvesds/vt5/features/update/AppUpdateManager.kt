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

    // Matched bestandsnamen zoals:
    // VT-AI.4.0.1 build 3.apk
    // VT-AI.4.0.1.3.apk
    // VT-AI.4.0.1-b3.apk
    // VT-AI.4.0.1.apk
    private val apkPattern = Regex(
        "^VT-AI\\.(\\d+\\.\\d+\\.\\d+)(?:[\\s._-]*build[\\s._-]*(\\d+)|[\\s._-]*b(\\d+)|[\\s._-]*(\\d+))?\\.apk$",
        RegexOption.IGNORE_CASE
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    data class ReleaseAsset(
        val version: String,       // Basissverienaam (bijv. "4.0.1")
        val buildNumber: Int,      // Build-nummer (bijv. 3, of 0 indien afwezig)
        val downloadUrl: String,
        val sha256: String?,
        val name: String,
        val updatedAtIso: String? = null
    ) {
        val displayVersion: String
            get() = if (buildNumber > 0) "$version build $buildNumber" else version
    }

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

    data class ParsedVersion(
        val baseVersion: String,  // e.g. "4.0.1"
        val buildNumber: Int,     // e.g. 3
        val major: Int,           // 4
        val minor: Int,           // 0
        val patch: Int            // 1
    ) : Comparable<ParsedVersion> {

        val versionCode: Long
            get() = (major * 10_000_000L) + (minor * 100_000L) + (patch * 1_000L) + buildNumber

        val displayVersion: String
            get() = if (buildNumber > 0) "$baseVersion build $buildNumber" else baseVersion

        override fun compareTo(other: ParsedVersion): Int {
            if (this.major != other.major) return this.major.compareTo(other.major)
            if (this.minor != other.minor) return this.minor.compareTo(other.minor)
            if (this.patch != other.patch) return this.patch.compareTo(other.patch)
            return this.buildNumber.compareTo(other.buildNumber)
        }

        companion object {
            private val versionRegex = Regex(
                "(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[\\s._-]*build[\\s._-]*(\\d+)|[\\s._-]*b(\\d+)|[\\s._-]*(\\d+))?",
                RegexOption.IGNORE_CASE
            )

            fun parse(text: String?): ParsedVersion {
                if (text.isNullOrBlank()) return ParsedVersion("0.0.0", 0, 0, 0, 0)
                val match = versionRegex.find(text) ?: return ParsedVersion("0.0.0", 0, 0, 0, 0)
                val major = match.groupValues[1].toIntOrNull() ?: 0
                val minor = match.groupValues[2].toIntOrNull() ?: 0
                val patch = match.groupValues.getOrNull(3)?.ifBlank { "0" }?.toIntOrNull() ?: 0
                val buildStr = match.groupValues.getOrNull(4)?.ifBlank { null }
                    ?: match.groupValues.getOrNull(5)?.ifBlank { null }
                    ?: match.groupValues.getOrNull(6)?.ifBlank { null }
                val build = buildStr?.toIntOrNull() ?: 0
                val base = "$major.$minor.$patch"
                return ParsedVersion(base, build, major, minor, patch)
            }
        }
    }

    suspend fun getCachedState(context: Context): UpdateState = withContext(Dispatchers.IO) {
        val local = readInstalledVersion(context)
        val snapshot = AppDataStore.getUpdateSnapshot(context)
        val latestAsset = if (!snapshot.latestVersion.isNullOrBlank() && !snapshot.latestUrl.isNullOrBlank()) {
            val parsed = ParsedVersion.parse(snapshot.latestVersion)
            ReleaseAsset(
                version = parsed.baseVersion,
                buildNumber = parsed.buildNumber,
                downloadUrl = snapshot.latestUrl,
                sha256 = snapshot.latestSha256,
                name = "VT-AI.${parsed.displayVersion}.apk",
                updatedAtIso = snapshot.latestUpdatedAtIso
            )
        } else null

        val availableFromCurrentInstall = isUpdateAvailable(context, local.versionName, local.versionCode, latestAsset)
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
        localVersionCode: Long,
        latestAsset: ReleaseAsset?
    ): Boolean {
        if (latestAsset == null) return false
        val localParsed = ParsedVersion.parse(localVersionName)
        val remoteParsed = ParsedVersion.parse(latestAsset.displayVersion)

        // 1. Vergelijk geconsolideerde versie & build nummer
        if (remoteParsed > localParsed) return true
        if (remoteParsed.versionCode > localVersionCode) return true

        // 2. Indien de versie gelijk is aan de geïnstalleerde versie:
        // Controleer uitsluitend of er op GitHub een her-üploade hotfix staat met een recentere uploaddatum
        if (remoteParsed == localParsed || remoteParsed.versionCode == localVersionCode) {
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
                    val baseVersion = match.groupValues[1]
                    val buildStr = match.groupValues.getOrNull(2)?.ifBlank { null }
                        ?: match.groupValues.getOrNull(3)?.ifBlank { null }
                        ?: match.groupValues.getOrNull(4)?.ifBlank { null }
                    val buildNum = buildStr?.toIntOrNull() ?: 0

                    val url = asset.optString("browser_download_url")
                    if (url.isBlank()) continue
                    val digestRaw = asset.optString("digest", "")
                    val sha256 = digestRaw.removePrefix("sha256:").ifBlank { null }
                    val assetUpdatedAt = asset.optString("updated_at", "")
                    val updatedAt = assetUpdatedAt.ifBlank { releasePublishedAt }
                    parsed += ReleaseAsset(
                        version = baseVersion,
                        buildNumber = buildNum,
                        downloadUrl = url,
                        sha256 = sha256,
                        name = name,
                        updatedAtIso = updatedAt.ifBlank { null }
                    )
                }

                val sorted = parsed.sortedWith { a, b ->
                    val parsedA = ParsedVersion.parse(a.displayVersion)
                    val parsedB = ParsedVersion.parse(b.displayVersion)
                    parsedB.compareTo(parsedA)
                }
                val latest = sorted.firstOrNull()
                val available = isUpdateAvailable(context, local.versionName, local.versionCode, latest)

                AppDataStore.setUpdateSnapshot(
                    context = context,
                    updateAvailable = available,
                    latestVersion = latest?.displayVersion,
                    latestUrl = latest?.downloadUrl,
                    latestSha256 = latest?.sha256,
                    latestUpdatedAtIso = latest?.updatedAtIso,
                    checkedAt = now
                )

                UpdateState(
                    localVersion = local.versionName,
                    localVersionCode = local.versionCode,
                    latestVersion = latest?.displayVersion,
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

                if (archivePackage != context.packageName) {
                    outFile.delete()
                    error("APK pakket-identiteit komt niet overeen met VT5 ($archivePackage)")
                }

                // Guardrail 2: Checksum verificatie met ondersteuning voor hotfixes (re-uploaded builds)
                val actualSha = sha256(outFile).lowercase()
                val expectedSha = asset.sha256?.lowercase()

                if (!expectedSha.isNullOrBlank() && actualSha != expectedSha) {
                    AppDataStore.setUpdateSnapshot(
                        context = context,
                        updateAvailable = true,
                        latestVersion = asset.displayVersion,
                        latestUrl = asset.downloadUrl,
                        latestSha256 = actualSha,
                        checkedAt = System.currentTimeMillis()
                    )
                }

                outFile
            }
        }
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
