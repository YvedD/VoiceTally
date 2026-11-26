package com.yvesds.vt5.core.opslag

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SaFStorageHelper - Behoudt de originele synchrone helpers voor compatibiliteit.
 * Voegt suspend-wrappers toe die DocumentFilecontentResolver uitvoeren op Dispatchers.IO.
 * Aanroepers in coroutines moeten de suspend-varianten (
 * foldersExistSuspend,
 * ensureFoldersSuspend,
 * getVt5DirIfExistsSuspend,
 * findOrCreateDirectorySuspend)
 * prefereren om te voorkomen dat de UI-thread wordt geblokkeerd.
 * Opmerking: DocumentFile.listFiles() en contentResolver IO kunnen op sommige apparaten traag zijn.
 * Ik geef altijd de voorkeur aan de suspend wrappers in productiecode.
 */
class SaFStorageHelper(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveRootUri(uri: Uri) {
        prefs.edit().putString(KEY_ROOT_URI, uri.toString()).apply()
    }

    fun getRootUri(): Uri? = prefs.getString(KEY_ROOT_URI, null)?.let { Uri.parse(it) }

    fun clearRootUri() {
        prefs.edit().remove(KEY_ROOT_URI).apply()
    }

    /**
     * Zorg ervoor dat we een volhoudbare toestemming hebben voor de geselecteerde boom.
     */
    fun takePersistablePermission(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // swallow; caller should handle UX if permission wasn't granted
        }
    }

    /**
     * Synchrone variant: controleer of VT5 root- en submappen bestaan.
     * Prefereer mappenExistSuspend() wanneer je wordt aangeroepen vanuit een coroutine.
     */
    fun foldersExist(): Boolean {
        val rootTree = getRootUri() ?: return false
        val rootDoc = DocumentFile.fromTreeUri(context, rootTree) ?: return false
        val vt5 = rootDoc.findFile("VT5")?.takeIf { it.isDirectory } ?: return false
        val expected = setOf("assets", "serverdata", "counts", "exports", "binaries", "logs", "imports", "AI-models", "errorlogs")
        val present = vt5.listFiles().filter { it.isDirectory }.mapNotNull { it.name }.toSet()
        return expected.all { it in present }
    }

    /**
     * Suspend variant van foldersExist() (draait op Dispatchers.IO).
     */
    suspend fun foldersExistSuspend(): Boolean = withContext(Dispatchers.IO) {
        foldersExist()
    }

    /**
     * Synchrone ensure (idempotent) van de VT5-boom en submappen.
     * Geef de voorkeur aan ensureFoldersSuspend() in coroutinecontexten.
     */
    fun ensureFolders(): Boolean {
        val rootTree = getRootUri() ?: return false
        val rootDoc = DocumentFile.fromTreeUri(context, rootTree) ?: return false

        val vt5Folder = findOrCreateDirectory(rootDoc, "VT5") ?: return false

        val subfolders = listOf("assets", "serverdata", "counts", "exports", "binaries", "logs", "imports", "AI-models", "errorlogs")
        for (name in subfolders) {
            val dir = findOrCreateDirectory(vt5Folder, name) ?: return false
            if (name == "AI-models") {
                ensureAiSubfolders(dir)
            }
        }
        return true
    }

    private fun ensureAiSubfolders(aiDir: DocumentFile) {
        val subfolders = listOf("training_exports", "models", "feedback")
        for (name in subfolders) {
            findOrCreateDirectory(aiDir, name)
        }
    }

    /**
     * Suspend-variant van ensureFolders (draait op Dispatchers.IO).
     */
    suspend fun ensureFoldersSuspend(): Boolean = withContext(Dispatchers.IO) {
        ensureFolders()
    }

    /**
     * Synchronous retrieval/creation of VT5 DocumentFile.
     * Accurately handles cases where user picked Documents directory OR VT5 directory directly in SAF.
     */
    fun getVt5DirIfExists(): DocumentFile? {
        val rootTree = getRootUri() ?: return null
        val rootDoc = DocumentFile.fromTreeUri(context, rootTree) ?: return null
        if (rootDoc.name?.equals("VT5", ignoreCase = true) == true) {
            return rootDoc
        }
        val childVt5 = rootDoc.findFile("VT5")?.takeIf { it.isDirectory }
        if (childVt5 != null) {
            return childVt5
        }
        return findOrCreateDirectory(rootDoc, "VT5")
    }

    /**
     * Suspend-variant (draait op Dispatchers.IO).
     */
    suspend fun getVt5DirIfExistsSuspend(): DocumentFile? = withContext(Dispatchers.IO) {
        getVt5DirIfExists()
    }

    /**
     * Zoek een bestaande map op exacte naam (hoofdlettergevoelig) of maak deze aan.
     * Opmerking: listFiles() kan traag zijn — geef de voorkeur aan dit aan te roepen via de suspend wrapper.
     */
    fun findOrCreateDirectory(parent: DocumentFile, name: String): DocumentFile? {
        parent.listFiles().firstOrNull { it.isDirectory && it.name == name }?.let { return it }
        return parent.createDirectory(name)
    }

    /**
     * Suspend-safe wrapper voor findOrCreateDirectory.
     */
    suspend fun findOrCreateDirectorySuspend(parent: DocumentFile, name: String): DocumentFile? = withContext(Dispatchers.IO) {
        findOrCreateDirectory(parent, name)
    }
    
    // =id ========================================================================
    // SERVERDATA DIRECTORY HELPERS
    // ========================================================================

    /**
     * Haal de serverdata-map op als die bestaat.
     */
    fun getServerDataDir(): DocumentFile? {
        val vt5Dir = getVt5DirIfExists() ?: return null
        return vt5Dir.findFile(SERVERDATA_DIR)?.takeIf { it.isDirectory }
    }

    /**
     * Suspend variant van getServerDataDir().
     */
    suspend fun getServerDataDirSuspend(): DocumentFile? = withContext(Dispatchers.IO) {
        getServerDataDir()
    }

    /**
     * Lees een bestand uit de serverdata-map.
     */
    fun readServerDataFile(filename: String): String? {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) return null
        val dir = getServerDataDir() ?: return null
        val file = dir.findFile(filename) ?: return null
        return try {
            context.contentResolver.openInputStream(file.uri)?.use { it.bufferedReader().readText() }
        } catch (_: Exception) { null }
    }

    /**
     * Lees een bestand uit de AI-models/models map.
     */
    fun readModelFile(filename: String): String? {
        val vt5 = getVt5DirIfExists() ?: return null
        val ai = vt5.findFile("AI-models") ?: return null
        val models = ai.findFile("models") ?: return null
        val file = models.findFile(filename) ?: return null
        return try {
            context.contentResolver.openInputStream(file.uri)?.use { it.bufferedReader().readText() }
        } catch (_: Exception) { null }
    }

    /**
     * Lees een bestand uit de AI-models/feedback map.
     */
    fun readFeedbackFile(filename: String): String? {
        val vt5 = getVt5DirIfExists() ?: return null
        val ai = vt5.findFile("AI-models") ?: return null
        val feedback = ai.findFile("feedback") ?: return null
        val file = feedback.findFile(filename) ?: return null
        return try {
            context.contentResolver.openInputStream(file.uri)?.use { it.bufferedReader().readText() }
        } catch (_: Exception) { null }
    }

    /**
     * Schrijf een bestand naar de AI-models/feedback map.
     */
    fun writeFeedbackFile(filename: String, content: String): Boolean {
        val vt5 = getVt5DirIfExists() ?: return false
        val ai = vt5.findFile("AI-models") ?: return false
        val dir = findOrCreateDirectory(ai, "feedback") ?: return false
        val file = dir.findFile(filename) ?: dir.createFile("application/json", filename) ?: return false
        return try {
            context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(content.toByteArray()) }
            true
        } catch (_: Exception) { false }
    }

    fun writeServerDataFile(filename: String, content: String): Boolean {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) return false
        val vt5Dir = getVt5DirIfExists() ?: return false
        val dir = findOrCreateDirectory(vt5Dir, SERVERDATA_DIR) ?: return false
        val mimeType = if (filename.endsWith(".json")) "application/json" else "text/plain"
        val file = dir.findFile(filename) ?: dir.createFile(mimeType, filename) ?: return false
        return try {
            // "wt" zorgt ervoor dat het bestand wordt overschreven en niet verlengd (truncate)
            context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(content.toByteArray()) }
            true
        } catch (_: Exception) { false }
    }

    // ========================================================================
    // COUNTS DIRECTORY HELPERS
    // ========================================================================
    
    /**
    * Download de counts-map (DocumentsVT5counts) als die bestaat.
    * @return DocumentFile voor de counts-map, of null als niet beschikbaar
    */
    fun getCountsDir(): DocumentFile? {
        val vt5Dir = getVt5DirIfExists() ?: return null
        return vt5Dir.findFile(COUNTS_DIR)?.takeIf { it.isDirectory }
    }
    
    /**
     * Suspend variant van getCountsDir().
     */
    suspend fun getCountsDirSuspend(): DocumentFile? = withContext(Dispatchers.IO) {
        getCountsDir()
    }
    
    /**
     * Vermeld alle bestanden in de counts-map.
     * @return Lijst van DocumentFile-objecten voor bestanden in counts-directory
     */
    fun listCountsFiles(): List<DocumentFile> {
        val countsDir = getCountsDir() ?: return emptyList()
        return countsDir.listFiles().filter { it.isFile }
    }
    
    /**
     * Pauzeer variant van listCountsFiles().
     */
    suspend fun listCountsFilesSuspend(): List<DocumentFile> = withContext(Dispatchers.IO) {
        listCountsFiles()
    }
    
    /**
     * Verwijder een bestand uit de counts-map.
     * @param filename van het bestand dat verwijderd moet worden (geen pad)
     * @return waar als het bestand is verwijderd, anders onwaar
     */
    fun deleteCountsFile(filename: String): Boolean {
        // Validate filename (no path traversal)
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return false
        }
        
        val countsDir = getCountsDir() ?: return false
        val file = countsDir.findFile(filename) ?: return false
        return file.delete()
    }
    
    /**
     * Suspend variant van deleteCountsFile().
     */
    suspend fun deleteCountsFileSuspend(filename: String): Boolean = withContext(Dispatchers.IO) {
        deleteCountsFile(filename)
    }

    /**
     * Controleert of de submap Documents/VT5/exports/grafieken/ bestaat.
     * Maakt deze zo nodig automatisch aan. Retourneert de map én een diagnose-bericht.
     */
    suspend fun getOrCreateGrafiekenDirDetailed(): Pair<DocumentFile?, String> = withContext(Dispatchers.IO) {
        val rootTree = getRootUri()
            ?: return@withContext Pair(null, "SAF Opslagmap is niet ingesteld in de app. Ga naar Instellingen om de Documents of VT5 map eenmalig te selecteren.")

        val rootDoc = DocumentFile.fromTreeUri(context, rootTree)
            ?: return@withContext Pair(null, "DocumentFile.fromTreeUri retourneerde null voor de opgeslagen URI: $rootTree")

        val vt5Dir = getVt5DirIfExists()
            ?: return@withContext Pair(null, "Kon de VT5 hoofdmap niet vinden of aanmaken in SAF boom: ${rootDoc.uri}")

        val exportsDir = findOrCreateDirectory(vt5Dir, "exports")
            ?: return@withContext Pair(null, "Kon de submap 'exports' niet aanmaken in VT5 map: ${vt5Dir.uri}")

        val grafiekenDir = findOrCreateDirectory(exportsDir, "grafieken")
            ?: return@withContext Pair(null, "Kon de submap 'grafieken' niet aanmaken in exports map: ${exportsDir.uri}")

        Pair(grafiekenDir, "OK")
    }

    suspend fun getOrCreateGrafiekenDirSuspend(): DocumentFile? = withContext(Dispatchers.IO) {
        getOrCreateGrafiekenDirDetailed().first
    }
    
    /**
     * Lees de inhoud van een bestand in de counts-map.
     * @param filename Naam van het bestand dat gelezen moet worden
     * @return File inhoud als String, of null als bestand niet gevonden of gelezen mislukt
     */
    fun readCountsFile(filename: String): String? {
        // Valideer bestandsnaam (geen paddoorloop)
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return null
        }
        
        val countsDir = getCountsDir() ?: return null
        val file = countsDir.findFile(filename) ?: return null
        
        return try {
            context.contentResolver.openInputStream(file.uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Suspend variant van readCountsFile().
     */
    suspend fun readCountsFileSuspend(filename: String): String? = withContext(Dispatchers.IO) {
        readCountsFile(filename)
    }
    
    /**
     * Schrijf inhoud naar een bestand in de counts-map.
     * Als er een bestand bestaat, wordt het overschreven.
     * @param filename Naam van het bestand dat geschreven moet worden
     * @param content Inhoud om te schrijven
     * @return 'waar' als write succesvol was, anders 'onwaar'
     */
    fun writeCountsFile(filename: String, content: String): Boolean {
        // Valideer bestandsnaam (geen paddoorloop)
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return false
        }
        
        val vt5Dir = getVt5DirIfExists() ?: return false
        var countsDir = vt5Dir.findFile(COUNTS_DIR)
        
        // Maak een counts-map aan als die niet bestaat
        if (countsDir == null || !countsDir.isDirectory) {
            countsDir = vt5Dir.createDirectory(COUNTS_DIR) ?: return false
        }
        
        val mimeType = if (filename.endsWith(".json")) "application/json" else "text/plain"
        val newFile = countsDir.findFile(filename)?.takeIf { it.isFile }
            ?: countsDir.createFile(mimeType, filename)
            ?: return false

        return try {
            context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            // Probeer een mislukt bestand op te schonen
            try { newFile.delete() } catch (_: Exception) {}
            false
        }
    }
    
    /**
     * Suspend-variant van writeCountsFile().
     */
    suspend fun writeCountsFileSuspend(filename: String, content: String): Boolean = withContext(Dispatchers.IO) {
        writeCountsFile(filename, content)
    }

    // ========================================================================
    // IMPORTS DIRECTORY HELPERS
    // ========================================================================

    /**
     * Haal de imports-map (Documents/VT5/imports) op als die bestaat.
     */
    fun getImportsDir(): DocumentFile? {
        val vt5Dir = getVt5DirIfExists() ?: return null
        return vt5Dir.findFile(IMPORTS_DIR)?.takeIf { it.isDirectory }
    }

    /**
     * Suspend variant van getImportsDir().
     */
    suspend fun getImportsDirSuspend(): DocumentFile? = withContext(Dispatchers.IO) {
        getImportsDir()
    }

    /**
     * Schrijf inhoud naar een bestand in de imports-map.
     * Als er een bestand bestaat, wordt het overschreven.
     */
    fun writeImportsFile(filename: String, content: String): Boolean {
        return writeImportsFileBytes(filename, content.toByteArray(Charsets.UTF_8))
    }

    /**
     * Schrijf binaire inhoud (zoals .xlsx bestanden) naar een bestand in de imports-map (Documents/VT5/imports).
     */
    fun writeImportsFileBytes(filename: String, bytes: ByteArray): Boolean {
        // Valideer bestandsnaam (geen paddoorloop)
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return false
        }

        val vt5Dir = getVt5DirIfExists()
        if (vt5Dir != null) {
            var importsDir = vt5Dir.findFile(IMPORTS_DIR)
            if (importsDir == null || !importsDir.isDirectory) {
                importsDir = vt5Dir.createDirectory(IMPORTS_DIR) ?: return false
            }

            val mimeType = if (filename.endsWith(".xlsx")) {
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            } else if (filename.endsWith(".json")) {
                "application/json"
            } else {
                "text/plain"
            }

            val newFile = importsDir.findFile(filename)?.takeIf { it.isFile }
                ?: importsDir.createFile(mimeType, filename)
                ?: return false

            return try {
                context.contentResolver.openOutputStream(newFile.uri, "wt")?.use { out ->
                    out.write(bytes)
                }
                true
            } catch (e: Exception) {
                try { newFile.delete() } catch (_: Exception) {}
                false
            }
        }

        // Fallback: Interne opslag files/VT5/imports
        return try {
            val root = java.io.File(context.filesDir, "VT5")
            val importsDir = java.io.File(root, IMPORTS_DIR)
            if (!importsDir.exists()) importsDir.mkdirs()
            val target = java.io.File(importsDir, filename)
            target.writeBytes(bytes)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Suspend-variant van writeImportsFile().
     */
    suspend fun writeImportsFileSuspend(filename: String, content: String): Boolean = withContext(Dispatchers.IO) {
        writeImportsFile(filename, content)
    }

    /**
     * Suspend-variant van writeImportsFileBytes().
     */
    suspend fun writeImportsFileBytesSuspend(filename: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        writeImportsFileBytes(filename, bytes)
    }

    // ========================================================================
    // EXPORTS DIRECTORY HELPERS
    // ========================================================================
    
    /**
     * Haal de exportmap (DocumentsVT5exports) op als die bestaat.
     * @return DocumentFile voor exportsdirectory, of null als niet beschikbaar
     */
    fun getExportsDir(): DocumentFile? {
        val vt5Dir = getVt5DirIfExists() ?: return null
        return vt5Dir.findFile(EXPORTS_DIR)?.takeIf { it.isDirectory }
    }
    
    /**
     * Suspend variant van getExportsDir().
     */
    suspend fun getExportsDirSuspend(): DocumentFile? = withContext(Dispatchers.IO) {
        getExportsDir()
    }
    
    /**
     * Vermeld alle bestanden in de exportmap.
     * @return List of DocumentFile Objecten voor bestanden in de exportmap
     */
    fun listExportsFiles(): List<DocumentFile> {
        val exportsDir = getExportsDir() ?: return emptyList()
        return exportsDir.listFiles().filter { it.isFile }
    }
    
    /**
     * Suspend variant van listExportsFiles().
     */
    suspend fun listExportsFilesSuspend(): List<DocumentFile> = withContext(Dispatchers.IO) {
        listExportsFiles()
    }
    
    /**
     * Verwijder een bestand uit de exportmap.
     * @param filename Naam van het bestand dat verwijderd moet worden (geen pad)
     * @return 'waar' als write succesvol was, anders 'onwaar'
     */
    fun deleteExportsFile(filename: String): Boolean {
        // Validate filename (no path traversal)
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return false
        }
        
        val exportsDir = getExportsDir() ?: return false
        val file = exportsDir.findFile(filename) ?: return false
        return file.delete()
    }
    
    /**
     * Suspend variant van deleteExportsFile().
     */
    suspend fun deleteExportsFileSuspend(filename: String): Boolean = withContext(Dispatchers.IO) {
        deleteExportsFile(filename)
    }
    
    /**
     * Maak de exportmap schoon door alle bestanden te verwijderen behalve de meest recente.
     * 
     * @param keepCount Aantal meest recente bestanden om te behouden (standaard 10)
     * @return Pair<Int, Int> - (aantal verwijderde bestanden, aantal bestanden dat niet kon verwijderen)
     */
    fun cleanupExportsDir(keepCount: Int = 10): Pair<Int, Int> {
        val exportsDir = getExportsDir() ?: return Pair(0, 0)
        val files = exportsDir.listFiles().filter { it.isFile }
        
        if (files.size <= keepCount) {
            return Pair(0, 0)
        }
        
        // Sorteer op laatst gewijzigd (meest recente eerst)
        val sortedFiles = files.sortedByDescending { it.lastModified() }
        
        // Bestanden te verwijderen (behalve de meest recente 'keepCount')
        val filesToDelete = sortedFiles.drop(keepCount)
        
        var deleted = 0
        var failed = 0
        
        for (file in filesToDelete) {
            if (file.delete()) {
                deleted++
            } else {
                failed++
            }
        }
        
        return Pair(deleted, failed)
    }
    
    /**
     * Suspend variant van cleanupExportsDir().
     */
    suspend fun cleanupExportsDirSuspend(keepCount: Int = 10): Pair<Int, Int> = withContext(Dispatchers.IO) {
        cleanupExportsDir(keepCount)
    }

    /**
     * Haal de logs-map (Documents/VT5/logs) op als die bestaat of maak deze aan.
     */
    fun getLogsDir(): DocumentFile? {
        val vt5Dir = getVt5DirIfExists() ?: return null
        return vt5Dir.findFile("logs")?.takeIf { it.isDirectory }
            ?: vt5Dir.createDirectory("logs")
    }

    /**
     * Controleert of een bestandsnaam beschermd is (mag nooit automatisch verwijderd worden).
     */
    private fun isProtectedFile(filename: String?): Boolean {
        if (filename == null) return false
        val lower = filename.lowercase()
        return lower == "active_telling.json" ||
               lower == "alias_master.json" ||
               lower == "aliases_optimized.cbor.gz" ||
               lower == "species.json" ||
               lower == "sites.json" ||
               lower == "site_species.json"
    }

    private val regexDateExtractor = Regex("(20\\d{2})[_-]?(0[1-9]|1[0-2])[_-]?(0[1-9]|[12]\\d|3[01])")
    private val regexJsonDatumExtractor = Regex("\"(?:datum|begintijd|created_at|timestamp)\"\\s*:\\s*\"(20\\d{2})[_-]?(0[1-9]|1[0-2])[_-]?(0[1-9]|[12]\\d|3[01])")
    private val regexJsonIsoDatumExtractor = Regex("\"(?:datum|begintijd|created_at|timestamp)\"\\s*:\\s*\"(20\\d{2})-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])")

    private fun extractDateStringFromFilenameOrContent(
        name: String,
        lastModifiedMs: Long,
        openInputStream: (() -> java.io.InputStream?)? = null
    ): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

        // 1. Zoek datumpatroon (20YYMMDD of 20YY-MM-DD) overal in de bestandsnaam (ongeacht voorvoegsels)
        val match = regexDateExtractor.find(name)
        if (match != null) {
            val y = match.groupValues[1]
            val m = match.groupValues[2]
            val d = match.groupValues[3]
            return "$y-$m-$d"
        }

        // 2. Indien JSON bestand zonder datum in naam: probeer datum uit JSON te lezen
        if (name.endsWith(".json", ignoreCase = true) && openInputStream != null) {
            try {
                val headerSnippet = openInputStream()?.use { stream ->
                    val buffer = ByteArray(2048)
                    val read = stream.read(buffer)
                    if (read > 0) String(buffer, 0, read, Charsets.UTF_8) else ""
                } ?: ""

                val jsonMatch = regexJsonIsoDatumExtractor.find(headerSnippet)
                    ?: regexJsonDatumExtractor.find(headerSnippet)
                if (jsonMatch != null) {
                    val y = jsonMatch.groupValues[1]
                    val m = jsonMatch.groupValues[2]
                    val d = jsonMatch.groupValues[3]
                    return "$y-$m-$d"
                }
            } catch (_: Exception) {}
        }

        // 3. Fallback: lastModified datum
        return dateFormat.format(java.util.Date(lastModifiedMs))
    }

    /**
     * Ruimt een specifieke SAF map op bij het afsluiten door alle bestanden te verwijderen
     * die ouder zijn dan de laatste 'keepDaysCount' teldagen (unieke kalenderdagen).
     */
    fun cleanupDirectoryByCountingDays(dir: DocumentFile?, keepDaysCount: Int = 5): Pair<Int, Int> {
        if (dir == null || !dir.isDirectory) return Pair(0, 0)
        val files = dir.listFiles().filter { it.isFile && !isProtectedFile(it.name) }
        if (files.isEmpty()) return Pair(0, 0)

        // Koppel elk bestand aan zijn teldag (yyyy-MM-dd)
        val fileWithDay = files.map { file ->
            val name = file.name ?: ""
            val dayStr = extractDateStringFromFilenameOrContent(
                name = name,
                lastModifiedMs = file.lastModified(),
                openInputStream = { context.contentResolver.openInputStream(file.uri) }
            )
            file to dayStr
        }

        // Bepaal de unieke teldagen gesorteerd van meest recent naar oudst
        val uniqueDaysSorted = fileWithDay.map { it.second }.distinct().sortedDescending()

        // Behoud de meest recente 'keepDaysCount' teldagen (bijv. 5)
        val allowedDays = uniqueDaysSorted.take(keepDaysCount).toSet()

        var deleted = 0
        var failed = 0

        for ((file, dayStr) in fileWithDay) {
            if (dayStr !in allowedDays) {
                if (file.delete()) {
                    deleted++
                } else {
                    failed++
                }
            }
        }

        return Pair(deleted, failed)
    }

    private fun cleanupInternalDirectoryByCountingDays(dir: java.io.File, keepDaysCount: Int = 5): Pair<Int, Int> {
        if (!dir.exists() || !dir.isDirectory) return Pair(0, 0)
        val files = dir.listFiles()?.filter { it.isFile && !isProtectedFile(it.name) } ?: return Pair(0, 0)
        if (files.isEmpty()) return Pair(0, 0)

        val fileWithDay = files.map { file ->
            val name = file.name ?: ""
            val dayStr = extractDateStringFromFilenameOrContent(
                name = name,
                lastModifiedMs = file.lastModified(),
                openInputStream = { runCatching { java.io.FileInputStream(file) }.getOrNull() }
            )
            file to dayStr
        }

        val uniqueDaysSorted = fileWithDay.map { it.second }.distinct().sortedDescending()
        val allowedDays = uniqueDaysSorted.take(keepDaysCount).toSet()

        var deleted = 0
        var failed = 0

        for ((file, dayStr) in fileWithDay) {
            if (dayStr !in allowedDays) {
                if (file.delete()) {
                    deleted++
                } else {
                    failed++
                }
            }
        }

        return Pair(deleted, failed)
    }

    /**
     * Ruimt alle tijdelijke submappen ('exports', 'logs', 'errorlogs', 'counts') op bij het afsluiten
     * door alle bestanden te verwijderen die ouder zijn dan de laatste 'keepDaysCount' teldagen.
     *
     * @return Pair<Int, Int> - (totaal aantal verwijderde bestanden, totaal aantal mislukte verwijderingen)
     */
    fun cleanupAllTemporaryFoldersOnExit(keepDaysCount: Int = 5): Pair<Int, Int> {
        val safDirsToClean = listOfNotNull(
            getExportsDir(),
            getLogsDir(),
            getErrorLogsDir(),
            getCountsDir()
        )

        var totalDeleted = 0
        var totalFailed = 0

        for (dir in safDirsToClean) {
            val (deleted, failed) = cleanupDirectoryByCountingDays(dir, keepDaysCount)
            totalDeleted += deleted
            totalFailed += failed
        }

        // Ook interne opslag mappen opschonen
        val internalFolders = listOf("exports", "logs", "errorlogs", "counts")
        for (folderName in internalFolders) {
            val internalDir = java.io.File(context.filesDir, "VT5/$folderName")
            val (deleted, failed) = cleanupInternalDirectoryByCountingDays(internalDir, keepDaysCount)
            totalDeleted += deleted
            totalFailed += failed
        }

        return Pair(totalDeleted, totalFailed)
    }

    suspend fun cleanupAllTemporaryFoldersOnExitSuspend(keepDaysCount: Int = 5): Pair<Int, Int> = withContext(Dispatchers.IO) {
        cleanupAllTemporaryFoldersOnExit(keepDaysCount)
    }
    
    /**
     * Krijg het aantal bestanden dat verwijderd zou worden door cleanupExportsDir().
     * 
     * @param keepCount Aantal meest recente bestanden om te behouden (standaard 10)
     * @return Aantal bestanden die verwijderd zouden worden
     */
    fun getExportsCleanupCount(keepCount: Int = 10): Int {
        val exportsDir = getExportsDir() ?: return 0
        val fileCount = exportsDir.listFiles().count { it.isFile }
        return maxOf(0, fileCount - keepCount)
    }
    
    /**
     * Suspend variant van getExportsCleanupCount().
     */
    suspend fun getExportsCleanupCountSuspend(keepCount: Int = 10): Int = withContext(Dispatchers.IO) {
        getExportsCleanupCount(keepCount)
    }

    // ========================================================================
    // ERRORLOGS DIRECTORY HELPERS & AUTOMATED CLEANUP
    // ========================================================================

    /**
     * Haal de errorlogs-map (Documents/VT5/errorlogs) op als die bestaat of maak deze aan.
     */
    fun getErrorLogsDir(): DocumentFile? {
        val vt5Dir = getVt5DirIfExists() ?: return null
        return vt5Dir.findFile(ERRORLOGS_DIR)?.takeIf { it.isDirectory }
            ?: vt5Dir.createDirectory(ERRORLOGS_DIR)
    }

    /**
     * Suspend variant van getErrorLogsDir().
     */
    suspend fun getErrorLogsDirSuspend(): DocumentFile? = withContext(Dispatchers.IO) {
        getErrorLogsDir()
    }

    /**
     * Vermeld alle bestanden in de errorlogs-map.
     */
    fun listErrorLogsFiles(): List<DocumentFile> {
        val errorLogsDir = getErrorLogsDir() ?: return emptyList()
        return errorLogsDir.listFiles().filter { it.isFile }
    }

    /**
     * Suspend variant van listErrorLogsFiles().
     */
    suspend fun listErrorLogsFilesSuspend(): List<DocumentFile> = withContext(Dispatchers.IO) {
        listErrorLogsFiles()
    }

    /**
     * Voer periodieke opruiming uit in Documents/VT5/errorlogs:
     * 1. Verwijder alle bestanden die gemarkeerd staan als verzonden (*.sent.log).
     * 2. Verwijder onverzonden bestanden ouder dan maxAgeDays (standaard 30 dagen).
     * 3. Beperk het aantal onverzonden bestanden tot maxUnsentFiles (standaard 20 via FIFO).
     * 
     * @return Pair<Int, Int> - (aantal verwijderde bestanden, aantal niet-verwijderde bestanden)
     */
    fun cleanupErrorLogsDir(maxAgeDays: Int = 30, maxUnsentFiles: Int = 20): Pair<Int, Int> {
        val errorLogsDir = getErrorLogsDir() ?: return Pair(0, 0)
        val files = errorLogsDir.listFiles().filter { it.isFile }
        if (files.isEmpty()) return Pair(0, 0)

        var deleted = 0
        var failed = 0
        val now = System.currentTimeMillis()
        val maxAgeMs = maxAgeDays.toLong() * 24 * 60 * 60 * 1000

        val unsentFiles = mutableListOf<DocumentFile>()

        for (file in files) {
            val name = file.name ?: ""
            val lastMod = file.lastModified()
            val ageMs = if (lastMod > 0) now - lastMod else 0

            // 1. Verwijder bestanden met status SENT (*.sent.log) direct
            if (name.contains(".sent.")) {
                if (file.delete()) deleted++ else failed++
            }
            // 2. Verwijder bestanden ouder dan maxAgeDays
            else if (maxAgeMs > 0 && ageMs > maxAgeMs) {
                if (file.delete()) deleted++ else failed++
            } else {
                unsentFiles.add(file)
            }
        }

        // 3. Beperk aantal onverzonden bestanden (FIFO op basis van lastModified)
        if (unsentFiles.size > maxUnsentFiles) {
            val sortedByOldest = unsentFiles.sortedBy { it.lastModified() }
            val overflowCount = unsentFiles.size - maxUnsentFiles
            for (i in 0 until overflowCount) {
                if (sortedByOldest[i].delete()) deleted++ else failed++
            }
        }

        return Pair(deleted, failed)
    }

    /**
     * Suspend variant van cleanupErrorLogsDir() (draait op Dispatchers.IO).
     */
    suspend fun cleanupErrorLogsDirSuspend(maxAgeDays: Int = 30, maxUnsentFiles: Int = 20): Pair<Int, Int> = withContext(Dispatchers.IO) {
        cleanupErrorLogsDir(maxAgeDays, maxUnsentFiles)
    }

    companion object {
        private const val SERVERDATA_DIR = "serverdata"
        private const val COUNTS_DIR = "counts"
        private const val EXPORTS_DIR = "exports"
        private const val IMPORTS_DIR = "imports"
        private const val ERRORLOGS_DIR = "errorlogs"
        private const val PREFS_NAME = "saf_storage_prefs"
        private const val KEY_ROOT_URI = "root_tree_uri"

        fun buildOpenDocumentTreeIntent(): Intent {
            return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, null as Uri?)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
    }
}