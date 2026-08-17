package com.yvesds.vt5.ai

import android.content.Context
import android.util.Log
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * TaxonomyManager - Genereert een wetenschappelijk overzicht van de gebruikte soorten.
 * Kruisverwijst de database met de wereldwijde species.json.
 */
class TaxonomyManager(private val context: Context) {
    private val TAG = "TaxonomyManager"
    private val db = VoiceTallyDatabase.getDatabase(context)
    private val saf = SaFStorageHelper(context)
    private val modelStore = ModelStore(context)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    data class TaxonomyExport(
        val info: String,
        val generatedAt: String,
        val groups: List<TaxonomicGroup>
    )

    @Serializable
    data class TaxonomicGroup(
        val guild: String,
        val strategy: String,
        val scientificOrder: String,
        val species: List<SpeciesInfo>
    )

    @Serializable
    data class SpeciesInfo(
        val id: String,
        val name: String,
        val latin: String? = ""
    )

    @Serializable
    private data class SpeciesJsonRoot(val json: List<RawSpecies>)

    @Serializable
    private data class RawSpecies(
        val soortid: String,
        val soortnaam: String,
        val latin: String? = ""
    )

    /**
     * Genereert een pretty-print JSON van alle soorten in de DB met hun gilde.
     */
    suspend fun generateActiveTaxonomyJson(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Start genereren actieve wetenschappelijke taxonomie...")
            
            val dbSpeciesIds = db.tellingDao().getAllUniqueSpeciesIds().toSet()
            if (dbSpeciesIds.isEmpty()) {
                Log.w(TAG, "Geen soorten gevonden in database.")
                return@withContext false
            }

            val worldJsonStr = saf.readServerDataFile("species.json") ?: return@withContext false
            val worldSpecies = json.decodeFromString<SpeciesJsonRoot>(worldJsonStr).json

            // Filter en Map naar gilden
            val taxonomyGroups = worldSpecies
                .filter { it.soortid in dbSpeciesIds }
                .filter { !it.soortnaam.lowercase().contains("spec.") }
                .filter { !it.soortnaam.lowercase().contains("onbekend") }
                .filter { !it.soortnaam.contains("/") }
                .mapNotNull { s ->
                    val guild = SpeciesGuildMapper.getGuildByLatin(s.latin)
                    if (guild == SpeciesGuildMapper.Guild.OTHER) null
                    else s to guild
                }
                .groupBy { it.second } 
                .map { (guild, list) ->
                    TaxonomicGroup(
                        guild = guild.displayName,
                        strategy = guild.strategy.name,
                        scientificOrder = getOrderForGuild(guild),
                        species = list.map { (s, _) -> 
                            SpeciesInfo(s.soortid, s.soortnaam, s.latin)
                        }.sortedBy { it.name }
                    )
                }.sortedBy { it.guild }

            val export = TaxonomyExport(
                info = "VT5 Wetenschappelijke Taxonomie - Gebaseerd op effectieve waarnemingen.",
                generatedAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                groups = taxonomyGroups
            )

            val resultJson = json.encodeToString(export)
            val filename = "active_scientific_taxonomy.json"
            
            Log.i(TAG, "JSON gegenereerd, start schrijven naar SAF (${resultJson.length} tekens)...")
            
            // Gebruik directe byte-schrijving om afbreken te voorkomen
            val success = modelStore.saveFileToModelDir(filename, resultJson.toByteArray(Charsets.UTF_8))
            
            if (success) {
                Log.i(TAG, "Wetenschappelijke taxonomie succesvol opgeslagen: $filename (${resultJson.length} tekens)")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij genereren taxonomie: ${e.message}", e)
            false
        }
    }

    private fun getOrderForGuild(guild: SpeciesGuildMapper.Guild): String {
        return when(guild) {
            SpeciesGuildMapper.Guild.WATERFOWL -> "Anseriformes"
            SpeciesGuildMapper.Guild.COASTAL_BIRDS -> "Anseriformes / Gaviiformes / Podicipediformes"
            SpeciesGuildMapper.Guild.RAPTORS_THERMAL, SpeciesGuildMapper.Guild.RAPTORS_ACTIVE -> "Accipitriformes / Falconiformes"
            SpeciesGuildMapper.Guild.HERONS -> "Pelecaniformes (Ardeidae)"
            SpeciesGuildMapper.Guild.STORKS -> "Ciconiiformes (Ciconiidae)"
            SpeciesGuildMapper.Guild.SHOREBIRDS -> "Charadriiformes (Scolopaci/Charadrii)"
            SpeciesGuildMapper.Guild.GULLS_TERNS -> "Charadriiformes (Lari)"
            SpeciesGuildMapper.Guild.PELAGICS -> "Procellariiformes / Suliformes / Gaviiformes / Podicipediformes"
            SpeciesGuildMapper.Guild.LANDBIRDS_SPECIAL -> "Coraciiformes / Upupiformes / Piciformes / Cuculiformes / Oriolidae / Laniidae"
            SpeciesGuildMapper.Guild.LANDBIRDS_REG -> "Columbiformes / Apodiformes / Hirundinidae"
            SpeciesGuildMapper.Guild.PASSERINES -> "Passeriformes"
            else -> "Overig / Niet-geclassificeerd"
        }
    }
}
