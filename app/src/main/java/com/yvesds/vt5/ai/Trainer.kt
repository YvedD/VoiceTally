package com.yvesds.vt5.ai

import android.content.Context
import android.util.Log
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.core.database.ui.SpeciesNameResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.*

/**
 * Trainer - Plan B: Bio-Statistische Intelligentie (BSI) Profiler.
 * In plaats van TensorFlow te trainen, bouwen we razendsnelle statistische profielen 
 * in de Room DB en het geheugen. Dit is 100x sneller en vederlicht.
 */
class Trainer(private val context: Context, private val modelStore: ModelStore) {
    private val TAG = "BsiProfiler"

    suspend fun runOnDeviceTraining(onProgress: (String, Int, Int) -> Unit) = withContext(Dispatchers.Default) {
        try {
            Log.i(TAG, "Start BSI Training...")
            onProgress("Historische data analyseren...", 5, 100)
            
            val db = VoiceTallyDatabase.getDatabase(context)
            val dao = db.tellingDao()
            val preparer = TrainingDataPreparer(context)

            // 1. Haal alle unieke soorten op (dit bepaalt de output van ons netwerk)
            val allSpecies = dao.getAllSpeciesIds().sorted()
            val numSpecies = allSpecies.size.coerceAtLeast(1)
            
            // 2. Laad bestaand model of start vers
            onProgress("Bestaande hersencellen laden...", 10, 100)
            val engine = modelStore.loadNeuralEngine(numSpecies)

            // 3. Haal data op uit Room
            onProgress("Data voorbereiden vanuit Room DB...", 20, 100)
            val trainingSamples = preparer.getTrainingDataFromRoom { msg, curr, total ->
                onProgress(msg, curr, total)
            }

            if (trainingSamples.isEmpty()) {
                onProgress("Geen data om van te leren.", 0, 0)
                return@withContext
            }

            // 4. De Trainings-lus (De AI leert nu echt!)
            val total = trainingSamples.size
            var count = 0
            
            trainingSamples.forEachIndexed { index, sample ->
                engine.train(sample.features, sample.labelIndex)
                
                // Minder frequente updates om de Main thread te ontlasten (was 500)
                if (index % 2500 == 0 || index == total - 1) {
                    count = index
                    val perc = (20 + (index.toFloat() / total * 75)).toInt()
                    onProgress("AI leert van waarnemingen...\n($index van $total)", perc, 100)
                }
            }

            // 5. Sla de nieuwe gewichten op
            onProgress("Nieuwe ervaringen opslaan...", 95, 100)
            modelStore.saveNeuralEngine(engine)
            // Persistente labels zodat label-index mapping reproduceerbaar is
            try {
                modelStore.saveModelLabels(allSpecies)
            } catch (_: Exception) { /* Best effort - niet kritisch */ }

            // 6. Voer Retroactieve Piek-Analyse uit (Knowledge Base)
            onProgress("Meteorologische vingerafdrukken berekenen...", 96, 100)
            val kbManager = ExpertKnowledgeManager(context)
            kbManager.analyzeHistoricalPeaks { msg, curr, total ->
                onProgress(msg, curr, total)
            }

            // BSI 4.0: Bouw uurs-distributie profielen (Gouden Mal)
            onProgress("Uurs-distributie profielen (Gouden Mal) berekenen...", 97, 100)
            buildHourlyProfiles(allSpecies)

            // 7. Genereer Wetenschappelijke Taxonomie JSON
            onProgress("Wetenschappelijke taxonomie bijwerken...", 98, 100)
            val taxonomyManager = TaxonomyManager(context)
            taxonomyManager.generateActiveTaxonomyJson()

            // 8. Vogelbeelden archiveren (PROACTIEF & GEOPTIMALISEERD)
            val snapshot = try { com.yvesds.vt5.features.serverdata.model.ServerDataCache.getOrLoad(context) } catch(_: Exception) { null }
            if (snapshot != null) {
                val cachedLatinNames = dao.getAllCachedLatinNames().toSet()
                val uniqueSids = trainingSamples.map { allSpecies[it.labelIndex] }.distinct()
                
                // Bepaal welke beelden we echt nog missen
                val toFetch = uniqueSids.mapNotNull { sid ->
                    val latin = snapshot.speciesById[sid]?.latin ?: return@mapNotNull null
                    val clean = SpeciesImageHelper.cleanLatinName(latin)
                    if (clean.isNotEmpty() && !cachedLatinNames.contains(clean)) {
                        Pair(sid, latin)
                    } else null
                }

                val totalToFetch = toFetch.size
                if (totalToFetch > 0) {
                    toFetch.forEachIndexed { i, (sid, latin) ->
                        val name = snapshot.speciesById[sid]?.soortnaam ?: sid
                        if (i % 2 == 0 || i == totalToFetch - 1) { // Iets vaker updates voor nieuwe beelden
                            onProgress("Nieuwe beelden archiveren: $name (${i+1}/$totalToFetch)...", 99, 100)
                        }
                        SpeciesImageHelper.getThumbnail(latin)
                        delay(60) // Iets meer adempauze voor nieuwe fetches
                    }
                } else {
                    Log.i(TAG, "Alle vogelbeelden (${uniqueSids.size}) zijn reeds aanwezig in de cache.")
                    onProgress("Vogelbeelden cache is up-to-date.", 99, 100)
                }
            }

            // 8. VUL DE SCIENTIFIC VAULT (HD-Curves & Pieken)
            onProgress("Wetenschappelijke kluis verzegelen...", 99, 100)
            fillScientificVault(onProgress)

            onProgress("Training succesvol voltooid!", 100, 100)
            Log.i(TAG, "Neural training voltooid.")
            delay(1000)

        } catch (e: Exception) {
            Log.e(TAG, "Training mislukt: ${e.message}", e)
            onProgress("Fout bij trainen: ${e.message}", 0, 0)
        }
    }

    private suspend fun fillScientificVault(onProgress: (String, Int, Int) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val db = VoiceTallyDatabase.getDatabase(context)
            val dao = db.tellingDao()
            val saf = SaFStorageHelper(context)
            
            val jsonStr = saf.readServerDataFile("telpost_locaties.json") ?: "{}"
            val root = com.yvesds.vt5.VT5App.json.decodeFromString<com.yvesds.vt5.core.database.entities.TelpostLocatiesRoot>(jsonStr)
            
            // BEPAAL HOOFDTELPOST (De allereerste in het JSON bestand)
            val primarySite = root.locaties.firstOrNull() ?: return@withContext
            val clusterIds = getLocalSiteClusterIds(saf, primarySite.latitude, primarySite.longitude) ?: listOf(primarySite.telpostid)
            val primaryClusterId = primarySite.telpostid
            
            val speciesIds = dao.getAllSpeciesIds()
            val total = speciesIds.size

            speciesIds.forEachIndexed { index, sid ->
                if (index % 10 == 0) {
                    onProgress("Vault verzegelen: Soort ${index+1}/$total...", 99, 100)
                }

                // Gebruik de volledige cluster voor de distributie
                val distribution = dao.getSpeciesDailyDistribution(sid, clusterIds)
                val rawCurve = FloatArray(366)
                distribution.forEach { if (it.day in 1..366) rawCurve[it.day - 1] = it.count.toFloat() }
                
                // GEEN SMOOTHING: Sla ruwe data op voor maximale eerlijkheid
                val curveString = rawCurve.joinToString("|") { String.format(Locale.US, "%.4f", it) }

                // Piek berekening
                val peakSpring = findPeakInPeriod(rawCurve, 1, 166)
                val peakAutumn = findPeakInPeriod(rawCurve, 167, 366)

                dao.insertDailyAnalysisVault(com.yvesds.vt5.core.database.entities.SpeciesPhenologyVault(
                    speciesId = sid,
                    clusterId = primaryClusterId, // Altijd gekoppeld aan de hoofdtelpost
                    dailyBphSeries = curveString,
                    peakSpring = peakSpring,
                    peakAutumn = peakAutumn
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vault failure: ${e.message}")
        }
    }

    private fun findPeakInPeriod(curve: FloatArray, startDay: Int, endDay: Int): String {
        var maxVal = -1f; var peakDay = -1
        for (i in (startDay - 1) until kotlin.math.min(endDay, curve.size)) {
            if (curve[i] > maxVal) { maxVal = curve[i]; peakDay = i + 1 }
        }
        if (peakDay == -1 || maxVal <= 0f) return "[onbekend]"
        val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_YEAR, peakDay) }
        return java.text.SimpleDateFormat("d MMMM", Locale("nl", "BE")).format(cal.time)
    }

    private fun getLocalSiteClusterIds(saf: SaFStorageHelper, lat: Double, lon: Double): List<String>? {
        return try {
            val jsonStr = saf.readServerDataFile("telpost_locaties.json") ?: return null
            val root = com.yvesds.vt5.VT5App.json.decodeFromString<com.yvesds.vt5.core.database.entities.TelpostLocatiesRoot>(jsonStr)
            root.locaties.filter { 
                val r = 6371.0
                val dLat = Math.toRadians(it.latitude - lat); val dLon = Math.toRadians(it.longitude - lon)
                val a = kotlin.math.sin(dLat/2).pow(2) + kotlin.math.cos(Math.toRadians(lat)) * kotlin.math.cos(Math.toRadians(it.latitude)) * kotlin.math.sin(dLon/2).pow(2)
                r * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a)) <= 35.0 
            }.map { it.telpostid }
        } catch (_: Exception) { null }
    }

    /**
     * BSI 4.0: Berekent de uurs-distributie profielen op basis van de "Anchor Sites".
     */
    private suspend fun buildHourlyProfiles(allSpecies: List<String>) = withContext(Dispatchers.IO) {
        val db = VoiceTallyDatabase.getDatabase(context)
        val dao = db.tellingDao()
        val anchorSites = AiConfig.ANCHOR_SITE_IDS
        
        val currentKb = modelStore.loadExpertKnowledge() ?: ExpertKnowledgeBase()
        val newProfiles = mutableMapOf<String, List<Float>>()
        
        allSpecies.forEach { sid ->
            val dist = dao.getSpeciesHourlyDistribution(anchorSites, sid)
            if (dist.isNotEmpty()) {
                val hourlyArray = FloatArray(24)
                var total = 0L
                dist.forEach { row ->
                    val h = row.hour.toIntOrNull() ?: return@forEach
                    if (h in 0..23) {
                        hourlyArray[h] = row.count.toFloat()
                        total += row.count
                    }
                }
                if (total > 0) {
                    newProfiles[sid] = hourlyArray.map { it / total }
                }
            }
        }
        
        modelStore.saveExpertKnowledge(currentKb.copy(hourlyProfiles = newProfiles))
    }

    // Placeholder voor data-modellen die elders gebruikt worden
    data class TrainingSample(val features: FloatArray, val labelIndex: Int)
}
