package com.yvesds.vt5.features.alias.helpers

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.features.alias.AliasData
import com.yvesds.vt5.features.alias.AliasMaster
import com.yvesds.vt5.features.alias.SpeciesEntry
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import com.yvesds.vt5.features.speech.ColognePhonetic
import com.yvesds.vt5.features.speech.DutchPhonemizer
import com.yvesds.vt5.utils.TextUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/**
 * AliasSeedGenerator: Generates initial alias master directly from species.json (Single Source of Truth).
 * 
 * Responsibilities:
 * - Read all master species from species.json (or ServerDataCache)
 * - Generate phonetic encodings for every species
 * - Build initial AliasMaster seed containing all global species
 * - Write master and CBOR to SAF
 */
object AliasSeedGenerator {
    
    private const val TAG = "AliasSeedGenerator"
    
    /**
     * Generate seed directly from species.json in serverdata directory.
     * Writes the resulting master to SAF assets and CBOR to binaries.
     */
    suspend fun generateSeed(
        context: Context,
        saf: SaFStorageHelper,
        vt5RootDir: DocumentFile
    ) = withContext(Dispatchers.IO) {
        try {
            // Locate serverdata
            val serverDir = vt5RootDir.findFile("serverdata")?.takeIf { it.isDirectory }
            if (serverDir == null) {
                Log.w(TAG, "serverdata not available, aborting seed generation")
                return@withContext
            }
            
            // Load all species from species.json (Single Source of Truth)
            val speciesMap = loadSpeciesMap(context, serverDir)
            if (speciesMap.isEmpty()) {
                Log.w(TAG, "No species found in species.json / ServerDataCache — aborting seed generation")
                return@withContext
            }
            
            // Build species list from all species in species.json
            val speciesList = buildSpeciesListFromMap(speciesMap).toMutableList()
            
            // Add reserved direction species
            val seedAliases = listOf("terug", "omgekeerd", "back", "reverse")
            val aliases = seedAliases.map { generateAliasData(text = it, source = "seed_canonical") }
            
            speciesList.add(
                SpeciesEntry(
                    speciesId = com.yvesds.vt5.features.alias.SPECIES_ID_DIR_RETURN,
                    canonical = com.yvesds.vt5.features.alias.SPECIES_NAME_DIR_RETURN,
                    tilename = "Terug",
                    aliases = aliases
                )
            )
            
            // Create master
            val newMaster = AliasMaster(
                version = "2.2",
                timestamp = Instant.now().toString(),
                species = speciesList
            )
            
            // Write master and CBOR to SAF
            AliasMasterIO.writeMasterAndCbor(context, newMaster, vt5RootDir, saf)
            
            // Hot-load CBOR into matcher
            try {
                com.yvesds.vt5.features.speech.AliasMatcher.reloadIndex(context, saf)
            } catch (_: Exception) {}
            
            Log.i(TAG, "Seed generated from species.json: ${newMaster.species.size} species, ${newMaster.species.sumOf { it.aliases.size }} total aliases")
        } catch (ex: Exception) {
            Log.e(TAG, "Seed generation failed: ${ex.message}", ex)
        }
    }
    
    /**
     * Load species map directly from ServerDataCache or species.json.
     */
    private suspend fun loadSpeciesMap(
        context: Context,
        serverDir: DocumentFile
    ): Map<String, Pair<String, String?>> = withContext(Dispatchers.IO) {
        val speciesMap = mutableMapOf<String, Pair<String, String?>>()
        
        // Try ServerDataCache first
        val snapshot = kotlin.runCatching { ServerDataCache.getOrLoad(context) }.getOrNull()
        if (snapshot != null && snapshot.speciesById.isNotEmpty()) {
            snapshot.speciesById.forEach { (k, v) ->
                val sid = k.lowercase().trim()
                speciesMap[sid] = Pair(
                    v.soortnaam ?: k,
                    v.soortkey?.takeIf { it.isNotBlank() }
                )
            }
            return@withContext speciesMap
        }
        
        // Fallback to reading species.json directly from serverdata
        val speciesFile = serverDir.findFile("species.json")?.takeIf { it.isFile }
        val speciesBytes = speciesFile?.let { doc ->
            kotlin.runCatching {
                context.contentResolver.openInputStream(doc.uri)?.use { it.readBytes() }
            }.getOrNull()
        }
        
        if (speciesBytes != null) {
            kotlin.runCatching {
                val root = Json.parseToJsonElement(speciesBytes.toString(Charsets.UTF_8))
                var arr = root.jsonArrayOrNull()
                if (arr == null && root is JsonObject) {
                    arr = root["json"]?.jsonArray 
                        ?: root["data"]?.jsonArray 
                        ?: root["items"]?.jsonArray
                }
                
                arr?.forEach { el ->
                    if (el is JsonObject) {
                        val sid = el["soortid"]?.jsonPrimitive?.contentOrNull
                            ?: el["id"]?.jsonPrimitive?.contentOrNull
                        val sidClean = sid?.lowercase()?.trim() ?: return@forEach
                        val naam = el["soortnaam"]?.jsonPrimitive?.contentOrNull 
                            ?: el["naam"]?.jsonPrimitive?.contentOrNull 
                            ?: sidClean
                        val key = el["soortkey"]?.jsonPrimitive?.contentOrNull
                        speciesMap[sidClean] = Pair(naam, key?.takeIf { it.isNotBlank() })
                    }
                }
            }.onFailure {
                Log.w(TAG, "Failed to parse species.json: ${it.message}")
            }
        }
        
        speciesMap
    }
    
    /**
     * Build sorted species list for all species in species.json.
     */
    private fun buildSpeciesListFromMap(
        speciesMap: Map<String, Pair<String, String?>>
    ): List<SpeciesEntry> {
        // Sort species IDs deterministically
        val sidList = speciesMap.keys.toList().sortedWith(Comparator { a, b ->
            val ai = a.toIntOrNull()
            val bi = b.toIntOrNull()
            when {
                ai != null && bi != null -> ai.compareTo(bi)
                ai != null && bi == null -> -1
                ai == null && bi != null -> 1
                else -> a.compareTo(b)
            }
        })
        
        return sidList.map { sid ->
            val (naamRaw, keyRaw) = speciesMap[sid] ?: Pair(sid, null)
            val canonical = naamRaw.ifBlank { sid }
            val tilename = keyRaw
            
            val canonicalAlias = generateAliasData(text = canonical, source = "seed_canonical")
            val tilenameAlias = if (!tilename.isNullOrBlank() && !tilename.equals(canonical, ignoreCase = true)) {
                generateAliasData(text = tilename, source = "seed_tilename")
            } else null
            
            SpeciesEntry(
                speciesId = sid,
                canonical = canonical,
                tilename = tilename,
                aliases = listOfNotNull(canonicalAlias, tilenameAlias)
            )
        }
    }
    
    /**
     * Generate AliasData with Cologne and Dutch phonemes.
     */
    private fun generateAliasData(text: String, source: String): AliasData {
        val cleaned = TextUtils.normalizeLowerNoDiacritics(text)
        val col = runCatching { ColognePhonetic.encode(cleaned) }.getOrNull() ?: ""
        val phon = runCatching { DutchPhonemizer.phonemize(cleaned) }.getOrNull() ?: ""
        
        return AliasData(
            text = text.trim().lowercase(),
            norm = cleaned,
            cologne = col,
            phonemes = phon,
            source = source,
            timestamp = if (source == "user_field_training") Instant.now().toString() else null
        )
    }
    
    private fun JsonElement.jsonArrayOrNull() = try { this.jsonArray } catch (_: Throwable) { null }
}
