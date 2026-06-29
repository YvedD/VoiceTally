@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.yvesds.vt5.features.serverdata.model

import android.content.Context
import com.yvesds.vt5.core.opslag.FileLogger
import com.yvesds.vt5.features.serverdata.helpers.ServerDataDecoder
import com.yvesds.vt5.features.serverdata.helpers.ServerDataFileReader
import com.yvesds.vt5.features.serverdata.helpers.ServerDataTransformer
import com.yvesds.vt5.features.serverdata.helpers.VT5Bin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * Optimized ServerDataRepository with helper delegation.
 * Uses FileLogger for on-device debugging.
 */
class ServerDataRepository(
    private val context: Context,
    private val json: Json = ServerDataDecoder.defaultJson,
    private val cbor: Cbor = ServerDataDecoder.defaultCbor
) {
    private val fileLogger by lazy { FileLogger(context) }
    private val snapshotState = MutableStateFlow(DataSnapshot())
    val snapshot: StateFlow<DataSnapshot> = snapshotState

    private val fileReader = ServerDataFileReader(context)
    private val decoder = ServerDataDecoder(context, json, cbor)
    
    private val loaderScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun hasRequiredFiles(): Boolean = fileReader.hasRequiredFiles()

    fun clearFileCache() {
        fileReader.clearCache()
    }

    /**
     * Load only minimal data required for startup (sites and codes).
     */
    suspend fun loadMinimalData(): DataSnapshot = withContext(Dispatchers.IO) {
        val serverdata = fileReader.getServerdataDir() ?: return@withContext DataSnapshot()

        coroutineScope {
            val sitesJob = async { readList<SiteItem>(serverdata, "sites", VT5Bin.Kind.SITES) }
            val codesJob = async {
                runCatching {
                    readList<CodeItem>(serverdata, "codes", VT5Bin.Kind.CODES)
                }.getOrElse { emptyList() }
            }

            val sites = sitesJob.await()
            val codes = codesJob.await()

            val (sitesById, codesByCategory) = ServerDataTransformer.processMinimalData(sites, codes)

            DataSnapshot(
                sitesById = sitesById,
                codesByCategory = codesByCategory
            )
        }
    }

    /**
     * Full data load with parallel processing for better performance.
     */
    suspend fun loadAllFromSaf(): DataSnapshot = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        val serverdata = fileReader.getServerdataDir() ?: run {
            fileLogger.warn("Serverdata directory niet gevonden")
            return@withContext DataSnapshot()
        }

        if (!hasRequiredFiles()) {
            fileLogger.warn("Essentiële databestanden ontbreken in ${serverdata.uri}")
            return@withContext DataSnapshot()
        }

        return@withContext coroutineScope {
            val userObjDef = async { readOne<CheckUserItem>(serverdata, "checkuser", VT5Bin.Kind.CHECK_USER) }
            val speciesListDef = async { readList<SpeciesItem>(serverdata, "species", VT5Bin.Kind.SPECIES) }
            val protocolInfoDef = async { readList<ProtocolInfoItem>(serverdata, "protocolinfo", VT5Bin.Kind.PROTOCOL_INFO) }
            val protocolSpeciesDef = async { readList<ProtocolSpeciesItem>(serverdata, "protocolspecies", VT5Bin.Kind.PROTOCOL_SPECIES) }
            val sitesDef = async { readList<SiteItem>(serverdata, "sites", VT5Bin.Kind.SITES) }
            val siteLocationsDef = async { readList<SiteValueItem>(serverdata, "site_locations", VT5Bin.Kind.SITE_LOCATIONS) }
            val siteHeightsDef = async { readList<SiteValueItem>(serverdata, "site_heights", VT5Bin.Kind.SITE_HEIGHTS) }
            val siteSpeciesDef = async { readList<SiteSpeciesItem>(serverdata, "site_species", VT5Bin.Kind.SITE_SPECIES) }
            val codesDef = async { runCatching { readList<CodeItem>(serverdata, "codes", VT5Bin.Kind.CODES) }.getOrElse { emptyList() } }

            val userObj = userObjDef.await()
            val speciesList = speciesListDef.await()
            val protocolInfo = protocolInfoDef.await()
            val protocolSpecies = protocolSpeciesDef.await()
            val sites = sitesDef.await()
            val siteLocations = siteLocationsDef.await()
            val siteHeights = siteHeightsDef.await()
            val siteSpecies = siteSpeciesDef.await()
            val codes = codesDef.await()

            if (speciesList.isEmpty()) fileLogger.warn("Bestand 'species' is leeg of onleesbaar")
            if (sites.isEmpty()) fileLogger.warn("Bestand 'sites' is leeg of onleesbaar")
            if (siteSpecies.isEmpty()) fileLogger.warn("Bestand 'site_species' is leeg of onleesbaar")
            if (codes.isEmpty()) fileLogger.warn("Bestand 'codes' is leeg of onleesbaar")

            val (speciesById, speciesByCanonical) = ServerDataTransformer.transformSpecies(speciesList)
            val sitesById = ServerDataTransformer.transformSites(sites)
            val (siteLocationsBySite, siteHeightsBySite) = ServerDataTransformer.transformSiteValues(siteLocations, siteHeights)
            val siteSpeciesBySite = ServerDataTransformer.transformSiteSpecies(siteSpecies)
            val protocolSpeciesByProtocol = ServerDataTransformer.transformProtocolSpecies(protocolSpecies)
            val codesByCategory = ServerDataTransformer.transformCodes(codes)

            val snap = DataSnapshot(
                currentUser = userObj,
                speciesById = speciesById,
                speciesByCanonical = speciesByCanonical,
                sitesById = sitesById,
                assignedSites = emptyList(),
                siteLocationsBySite = siteLocationsBySite,
                siteHeightsBySite = siteHeightsBySite,
                siteSpeciesBySite = siteSpeciesBySite,
                protocolsInfo = protocolInfo,
                protocolSpeciesByProtocol = protocolSpeciesByProtocol,
                codesByCategory = codesByCategory
            )

            val elapsed = System.currentTimeMillis() - startTime
            fileLogger.info("loadAllFromSaf voltooid in ${elapsed}ms")

            snapshotState.value = snap
            snap
        }
    }

    private inline fun <reified T> readList(
        dir: androidx.documentfile.provider.DocumentFile,
        baseName: String,
        expectedKind: UShort
    ): List<T> {
        val found = fileReader.findFile(dir, baseName)
        if (found == null) {
            loaderScope.launch { fileLogger.warn("Bestand niet gevonden: $baseName") }
            return emptyList()
        }
        val (file, isBinary) = found

        return if (isBinary) {
            decoder.decodeListFromBinary<T>(file, expectedKind) ?: emptyList()
        } else {
            decoder.decodeListFromJson<T>(file) ?: emptyList()
        }
    }

    private inline fun <reified T> readOne(
        dir: androidx.documentfile.provider.DocumentFile,
        baseName: String,
        expectedKind: UShort
    ): T? {
        val found = fileReader.findFile(dir, baseName)
        if (found == null) {
            loaderScope.launch { fileLogger.warn("Bestand niet gevonden: $baseName") }
            return null
        }
        val (file, isBinary) = found

        return if (isBinary) {
            decoder.decodeOneFromBinary<T>(file, expectedKind)
        } else {
            decoder.decodeOneFromJson<T>(file)
        }
    }
}
