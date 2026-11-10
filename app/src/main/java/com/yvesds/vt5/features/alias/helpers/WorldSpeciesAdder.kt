package com.yvesds.vt5.features.alias.helpers

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import com.yvesds.vt5.features.serverdata.model.SpeciesItem
import com.yvesds.vt5.utils.TextUtils
import com.yvesds.vt5.features.alias.AliasData
import com.yvesds.vt5.features.alias.AliasMaster
import com.yvesds.vt5.features.alias.SpeciesEntry
import com.yvesds.vt5.features.speech.ColognePhonetic
import com.yvesds.vt5.features.speech.DutchPhonemizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import java.time.Instant
import java.util.Locale

/**
 * WorldSpeciesAdder: Adds species from the world list (species.json) to local alias list.
 *
 * All species in species.json are globally accessible for matching and search.
 * When a custom alias is needed, it is persisted to alias_master.json.
 */
@OptIn(ExperimentalSerializationApi::class)
object WorldSpeciesAdder {

    private const val TAG = "WorldSpeciesAdder"

    /**
     * Add a species from the world list to local lists.
     *
     * @param context Android context
     * @param saf SAF storage helper
     * @param speciesId The species ID to add
     * @param speciesItem The species data from species.json (optional, will be looked up if null)
     * @param telpostId The telpost ID to associate the species with (optional)
     * @return true if species was added successfully, false otherwise
     */
    suspend fun addWorldSpeciesToLocalLists(
        context: Context,
        saf: SaFStorageHelper,
        speciesId: String,
        speciesItem: SpeciesItem?,
        telpostId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val vt5 = saf.getVt5DirIfExists()
            if (vt5 == null) {
                Log.w(TAG, "VT5 directory not found")
                return@withContext false
            }

            val species = speciesItem ?: run {
                Log.w(TAG, "Species item not provided for $speciesId")
                return@withContext false
            }

            // All species in species.json are globally available.
            // Add to alias_master.json if needed.
            val addedToAliasMaster = addToAliasMaster(
                context, vt5, saf, speciesId, species.soortnaam, species.soortkey
            )
            if (!addedToAliasMaster) {
                Log.w(TAG, "Failed to add species $speciesId to alias_master")
                return@withContext false
            }

            ServerDataCache.invalidate(context)
            Log.i(TAG, "Successfully prepared world species $speciesId (${species.soortnaam})")
            return@withContext true
        } catch (ex: Exception) {
            Log.e(TAG, "addWorldSpeciesToLocalLists failed: ${ex.message}", ex)
            return@withContext false
        }
    }

    /**
     * Add a species to alias_master.json with phonemes/cologne computed.
     */
    private suspend fun addToAliasMaster(
        context: Context,
        vt5RootDir: DocumentFile,
        saf: SaFStorageHelper,
        speciesId: String,
        canonical: String,
        tilename: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Read existing master
            val existingMaster = AliasMasterIO.readMasterFromAssets(context, vt5RootDir)
                ?: AliasMasterIO.readMasterFromBinaries(context, vt5RootDir)

            // Check if species already exists
            val speciesMap = existingMaster?.species?.associateBy { it.speciesId }?.toMutableMap()
                ?: mutableMapOf()

            if (speciesMap.containsKey(speciesId)) {
                Log.i(TAG, "Species $speciesId already in alias_master")
                return@withContext true
            }

            // Generate aliases with phonemes and cologne
            val canonicalAlias = generateAliasData(canonical, "seed_canonical")
            val tilenameAlias = if (!tilename.isNullOrBlank() && !tilename.equals(canonical, ignoreCase = true)) {
                generateAliasData(tilename, "seed_tilename")
            } else null

            val newEntry = SpeciesEntry(
                speciesId = speciesId,
                canonical = canonical,
                tilename = tilename?.takeIf { it.isNotBlank() },
                aliases = listOfNotNull<AliasData>(canonicalAlias, tilenameAlias)
            )

            speciesMap[speciesId] = newEntry

            // Create updated master
            val updatedMaster = AliasMaster(
                version = existingMaster?.version ?: "2.1",
                timestamp = Instant.now().toString(),
                species = speciesMap.values.sortedBy { it.speciesId }
            )

            // Write master and CBOR
            AliasMasterIO.writeMasterAndCbor(context, updatedMaster, vt5RootDir, saf)

            // Reload AliasMatcher
            try {
                com.yvesds.vt5.features.speech.AliasMatcher.reloadIndex(context, saf)
                Log.i(TAG, "AliasMatcher reloaded after adding species $speciesId")
            } catch (ex: Exception) {
                Log.w(TAG, "AliasMatcher reload failed: ${ex.message}")
            }

            Log.i(TAG, "Added species $speciesId ($canonical) to alias_master with ${newEntry.aliases.size} aliases")
            return@withContext true
        } catch (ex: Exception) {
            Log.e(TAG, "addToAliasMaster failed: ${ex.message}", ex)
            return@withContext false
        }
    }

    /**
     * Generate AliasData with phonetic encodings (cologne and phonemes).
     */
    private fun generateAliasData(text: String, source: String): AliasData {
        val cleaned = TextUtils.normalizeLowerNoDiacritics(text)
        val cologne = runCatching { ColognePhonetic.encode(cleaned) }.getOrNull() ?: ""
        val phonemes = runCatching { DutchPhonemizer.phonemize(cleaned) }.getOrNull() ?: ""

        return AliasData(
            text = text.trim().lowercase(Locale.ROOT),
            norm = cleaned,
            cologne = cologne,
            phonemes = phonemes,
            source = source,
            timestamp = null
        )
    }
}
