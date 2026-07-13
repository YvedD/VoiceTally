package com.yvesds.vt5.features.alias

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.features.alias.helpers.*
import com.yvesds.vt5.utils.TextUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPOutputStream

/**
 * AliasManager.kt - REFACTORED with helper delegation
 *
 * Responsibilities:
 * - Coordinate alias operations across helpers
 * - Maintain in-memory index state via Flow
 * - Handle user alias additions
 * - Ensure index availability for AliasMatcher
 */

object AliasManager {

    private const val TAG = "AliasManager"

    /* FILE PATHS & CONSTANTS */
    private const val MASTER_FILE = "alias_master.json"
    private const val BINARIES = "binaries"
    private const val ASSETS = "assets"

    /* JSON SERIALIZER */
    private val jsonPretty = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /* INDEX LOAD SYNCHRONIZATION */
    private val indexLoadMutex = Mutex()
    @Volatile private var indexLoaded = false
    private val _indexFlow = MutableStateFlow<AliasIndex?>(null)
    val indexFlow: StateFlow<AliasIndex?> = _indexFlow

    /* MASTER WRITE SYNCHRONIZATION */
    private val masterWriteMutex = Mutex()

    /* WRITE SCOPE */
    private val writeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /* INITIALIZATION: ensure SAF structure and optionally generate initial seed */
    suspend fun initialize(context: Context, saf: SaFStorageHelper): Boolean = withContext(Dispatchers.IO) {
        try {
            val vt5 = saf.getVt5DirIfExists()
            if (vt5 == null) {
                Log.e(TAG, "SAF VT5 root not set")
                return@withContext false
            }

            // Ensure binaries directory exists
            val binaries = vt5.findFile(BINARIES)?.takeIf { it.isDirectory } ?: vt5.createDirectory(BINARIES)
            if (binaries == null) {
                Log.e(TAG, "Cannot create binaries directory")
                return@withContext false
            }

            // Try to load existing master from assets
            var master = AliasMasterIO.readMasterFromAssets(context, vt5)
            if (master != null) {
                Log.i(TAG, "Loaded ${master.species.size} species from assets")
                
                // Ensure direction species is present for existing installations
                val updatedMaster = ensureDirectionSpecies(master)
                if (updatedMaster !== master) {
                    Log.i(TAG, "Migrating master: added direction species")
                    AliasMasterIO.writeMasterAndCbor(context, updatedMaster, vt5, saf)
                    master = updatedMaster
                }
                
                if (!AliasIndexCache.exists(context)) {
                    AliasMasterIO.writeMasterAndCbor(context, master, vt5, saf)
                }
                try { com.yvesds.vt5.features.speech.AliasMatcher.reloadIndex(context, saf) } catch (_: Exception) {}
                return@withContext true
            }

            // Fallback: try legacy location (binaries)
            var legacyMaster = AliasMasterIO.readMasterFromBinaries(context, vt5)
            if (legacyMaster != null) {
                // Ensure direction species is present
                val updatedMaster = ensureDirectionSpecies(legacyMaster)
                if (updatedMaster !== legacyMaster) {
                    Log.i(TAG, "Migrating legacy master: added direction species")
                    AliasMasterIO.writeMasterAndCbor(context, updatedMaster, vt5, saf)
                    legacyMaster = updatedMaster
                }

                if (!AliasIndexCache.exists(context)) {
                    AliasMasterIO.writeMasterAndCbor(context, legacyMaster, vt5, saf)
                }
                try { com.yvesds.vt5.features.speech.AliasMatcher.reloadIndex(context, saf) } catch (_: Exception) {}
                return@withContext true
            }

            // First install: generate seed from species.json
            AliasSeedGenerator.generateSeed(context, saf, vt5)

            return@withContext true

        } catch (ex: Exception) {
            Log.e(TAG, "Initialize failed: ${ex.message}", ex)
            return@withContext false
        }
    }

    suspend fun ensureIndexLoadedSuspend(context: Context, saf: SaFStorageHelper) = withContext(Dispatchers.IO) {
        if (indexLoaded && _indexFlow.value != null) return@withContext

        indexLoadMutex.withLock {
            if (indexLoaded && _indexFlow.value != null) return@withLock

            val index = AliasIndexLoader.loadIndex(context, saf)
            if (index != null) {
                _indexFlow.value = index
                indexLoaded = true
                Log.i(TAG, "AliasIndex loaded")
            } else {
                _indexFlow.value = null
                indexLoaded = false
            }
        }
    }

    /**
     * Get all unique species from the loaded alias index.
     */
    suspend fun getAllSpeciesFromIndex(context: Context, saf: SaFStorageHelper): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            ensureIndexLoadedSuspend(context, saf)
            val index = _indexFlow.value ?: return@withContext emptyMap()
            
            val speciesMap = mutableMapOf<String, String>()
            for (record in index.json) {
                if (!speciesMap.containsKey(record.speciesid)) {
                    speciesMap[record.speciesid] = record.canonical
                }
            }
            speciesMap
        } catch (ex: Exception) {
            Log.e(TAG, "getAllSpeciesFromIndex failed: ${ex.message}")
            emptyMap()
        }
    }

    /* ADD ALIAS (HOT-RELOAD) */
    suspend fun addAlias(
        context: Context,
        saf: SaFStorageHelper,
        speciesId: String,
        aliasText: String,
        canonical: String,
        tilename: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val normalizedText = aliasText.trim()
            if (normalizedText.isBlank()) return@withContext false

            val found = com.yvesds.vt5.features.speech.AliasMatcher.findExact(TextUtils.normalizeLowerNoDiacritics(normalizedText), context, saf)
            if (found.isNotEmpty()) return@withContext false

            com.yvesds.vt5.features.speech.AliasMatcher.addAliasHotpatch(speciesId, normalizedText, canonical, tilename)

            val timestamp = Instant.now().toString()
            writeScope.launch {
                try {
                    val persisted = writeSingleAliasToMasterImmediate(context, saf, speciesId, normalizedText, canonical, tilename, timestamp)
                    if (!persisted) return@launch

                    val vt5Local = saf.getVt5DirIfExists() ?: return@launch
                    val masterObj = AliasMasterIO.readMasterFromAssets(context, vt5Local) ?: return@launch
                    val writeResult = AliasMasterIO.writeMasterAndCbor(context, masterObj, vt5Local, saf)
                    
                    _indexFlow.value = masterObj.toAliasIndex()
                    indexLoaded = true

                    if (writeResult == null || !writeResult.wroteCborSaf) {
                        AliasCborRebuilder.scheduleRebuild(context, saf)
                    }

                    try {
                        com.yvesds.vt5.features.speech.AliasMatcher.reloadIndex(context, saf)
                    } catch (_: Exception) {}
                } catch (ex: Exception) {
                    AliasCborRebuilder.scheduleRebuild(context, saf)
                }
            }
            return@withContext true
        } catch (ex: Exception) {
            Log.e(TAG, "addAlias failed: ${ex.message}", ex)
            return@withContext false
        }
    }

    suspend fun forceRebuildCborNow(context: Context, saf: SaFStorageHelper) {
        AliasCborRebuilder.forceRebuild(context, saf)
    }

    /**
     * Ensures that the reserved direction species (_DIR_RETURN_) is present in the master.
     * Returns the updated master if changes were made, otherwise the original.
     */
    fun ensureDirectionSpecies(master: AliasMaster): AliasMaster {
        if (master.species.any { it.speciesId == SPECIES_ID_DIR_RETURN }) {
            return master
        }

        Log.i(TAG, "Injecting missing direction species into master")
        val seedAliases = listOf("terug", "omgekeerd", "back", "reverse")
        val aliases = seedAliases.map { generateAliasData(text = it, source = "seed_canonical") }
        
        val newSpecies = SpeciesEntry(
            speciesId = SPECIES_ID_DIR_RETURN,
            canonical = SPECIES_NAME_DIR_RETURN,
            tilename = "Terug",
            aliases = aliases
        )

        return master.copy(
            timestamp = Instant.now().toString(),
            species = (master.species + newSpecies).sortedBy { it.speciesId }
        )
    }

    private fun generateAliasData(text: String, source: String = "seed_canonical"): AliasData {
        val cleaned = TextUtils.normalizeLowerNoDiacritics(text)
        val col = runCatching { com.yvesds.vt5.features.speech.ColognePhonetic.encode(cleaned) }.getOrNull() ?: ""
        val phon = runCatching { com.yvesds.vt5.features.speech.DutchPhonemizer.phonemize(cleaned) }.getOrNull() ?: ""

        return AliasData(
            text = text.trim().lowercase(),
            norm = cleaned,
            cologne = col,
            phonemes = phon,
            source = source,
            timestamp = if (source == "user_field_training") Instant.now().toString() else null
        )
    }

    private suspend fun writeSingleAliasToMasterImmediate(
        context: Context,
        saf: SaFStorageHelper,
        speciesId: String,
        aliasText: String,
        canonical: String,
        tilename: String?,
        timestamp: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            masterWriteMutex.withLock {
                val vt5 = saf.getVt5DirIfExists() ?: return@withContext false
                val assetsDir = vt5.findFile(ASSETS)?.takeIf { it.isDirectory } ?: vt5.createDirectory(ASSETS) ?: return@withContext false
                val masterDoc = assetsDir.findFile(MASTER_FILE)?.takeIf { it.isFile } ?: assetsDir.createFile("application/json", MASTER_FILE) ?: return@withContext false

                val masterJson = runCatching { context.contentResolver.openInputStream(masterDoc.uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
                val master: AliasMaster = if (masterJson.isNullOrBlank()) {
                    AliasMaster("2.1", Instant.now().toString(), emptyList())
                } else {
                    runCatching { jsonPretty.decodeFromString<AliasMaster>(AliasMaster.serializer(), masterJson) }.getOrElse {
                        AliasMaster("2.1", Instant.now().toString(), emptyList())
                    }
                }

                val speciesMap = master.species.associateBy { it.speciesId }.toMutableMap()
                val speciesEntry = speciesMap.getOrElse(speciesId) {
                    SpeciesEntry(speciesId, canonical, tilename, emptyList()).also { speciesMap[speciesId] = it }
                }

                val newAlias = generateAliasData(aliasText, "user_field_training").copy(timestamp = timestamp)
                if (speciesEntry.aliases.any { it.norm == newAlias.norm }) {
                    val updatedMaster = master.copy(timestamp = Instant.now().toString(), species = speciesMap.values.sortedBy { it.speciesId })
                    AliasSafWriter.safeWriteTextToDocument(context, masterDoc, jsonPretty.encodeToString(AliasMaster.serializer(), updatedMaster))
                    return@withContext true
                } else {
                    val updatedSpeciesEntry = speciesEntry.copy(aliases = speciesEntry.aliases + newAlias)
                    speciesMap[speciesId] = updatedSpeciesEntry
                    val updatedMaster = master.copy(timestamp = Instant.now().toString(), species = speciesMap.values.sortedBy { it.speciesId })
                    val prettyJson = jsonPretty.encodeToString(AliasMaster.serializer(), updatedMaster)
                    val wrote = AliasSafWriter.safeWriteTextToDocument(context, masterDoc, prettyJson)
                    if (!wrote) AliasIndexCache.write(context, updatedMaster.toAliasIndex())
                    return@withContext true
                }
            }
        } catch (ex: Exception) {
            Log.w(TAG, "writeSingleAliasToMasterImmediate failed: ${ex.message}")
            false
        }
    }
}