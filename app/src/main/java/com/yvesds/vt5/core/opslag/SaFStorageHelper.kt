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
        val expected = setOf("assets", "serverdata", "counts", "exports", "binaries", "logs", "imports", "AI-models")
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

        val subfolders = listOf("assets", "serverdata", "counts", "exports", "binaries", "logs", "imports", "AI-models")
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
     * Synchronous download VT5 DocumentFile als die bestaat.
     * Prefereer getVt5DirIfExistsSuspend() wanneer je vanuit coroutine aanroept.
     */
    fun getVt5DirIfExists(): DocumentFile? {
        val rootTree = getRootUri() ?: return null
        return DocumentFile.fromTreeUri(context, rootTree)?.findFile("VT5")?.takeIf { it.isDirectory }
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
        // Valideer bestandsnaam (geen paddoorloop)
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return false
        }

        val vt5Dir = getVt5DirIfExists() ?: return false
        var importsDir = vt5Dir.findFile(IMPORTS_DIR)

        // Maak een imports-map aan als die niet bestaat
        if (importsDir == null || !importsDir.isDirectory) {
            importsDir = vt5Dir.createDirectory(IMPORTS_DIR) ?: return false
        }

        val mimeType = if (filename.endsWith(".json")) "application/json" else "text/plain"
        val newFile = importsDir.findFile(filename)?.takeIf { it.isFile }
            ?: importsDir.createFile(mimeType, filename)
            ?: return false

        return try {
            context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            try { newFile.delete() } catch (_: Exception) {}
            false
        }
    }

    /**
     * Suspend-variant van writeImportsFile().
     */
    suspend fun writeImportsFileSuspend(filename: String, content: String): Boolean = withContext(Dispatchers.IO) {
        writeImportsFile(filename, content)
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

    companion object {
        private const val COUNTS_DIR = "counts"
        private const val EXPORTS_DIR = "exports"
        private const val IMPORTS_DIR = "imports"
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