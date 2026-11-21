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
import com.yvesds.vt5.core.opslag.AppDataStore

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

            // 4. Bereken optionele samplegewichten op basis van Teldag Verslagen
            val useDailyWeights = try { AppDataStore.isUseDailyAnalysisWeights(context) } catch (e: Exception) { AiConfig.USE_DAILY_ANALYSIS_WEIGHTS }
            val speciesWeights = if (useDailyWeights) {
                try {
                    preparer.computeSpeciesWeightsFromDailyAnalysis()
                } catch (e: Exception) {
                    Log.w(TAG, "Kon species weights niet berekenen: ${e.message}")
                    emptyMap<String, Float>()
                }
            } else emptyMap()

            // 5. De Trainings-lus (De AI leert nu echt!)
            val total = trainingSamples.size
            var count = 0
            
            trainingSamples.forEachIndexed { index, sample ->
                val sid = allSpecies.getOrNull(sample.labelIndex)
                val rawW = speciesWeights[sid] ?: 1.0f
                // veilig afkappen
                val weight = rawW.coerceAtLeast(0.1f).coerceAtMost(AiConfig.DAILY_ANALYSIS_WEIGHT_MAX)
                engine.train(sample.features, sample.labelIndex, learningRate = 0.01f, sampleWeight = weight)

                // Minder frequente updates om de Main thread te ontlasten (was 500)
                if (index % 2500 == 0 || index == total - 1) {
                    count = index
                    val perc = (20 + (index.toFloat() / total * 75)).toInt()
                    onProgress("AI leert van waarnemingen...\n($index van $total)", perc, 100)
                }
            }

            // 5. Sla de nieuwe gewichten op (Zowel Globaal als per Telpost in DB)
            onProgress("Nieuwe ervaringen opslaan voor alle telposten...", 95, 100)
            modelStore.saveNeuralEngine(engine)
            try {
                modelStore.saveModelLabels(allSpecies)
                val uniqueSiteIds = dao.getUniqueTelpostIds()
                uniqueSiteIds.forEach { siteId ->
                    modelStore.saveNeuralEngineForCluster(siteId, engine)
                }
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
            val root = try { com.yvesds.vt5.VT5App.json.decodeFromString<com.yvesds.vt5.core.database.entities.TelpostLocatiesRoot>(jsonStr) } catch (_: Exception) { null }
            val siteList = root?.locaties ?: emptyList()

            // Haal alle unieke telpost-IDs op uit de database tellingen
            val dbSiteIds = dao.getUniqueTelpostIds().ifEmpty { siteList.map { it.telpostid } }
            val speciesIds = dao.getAllSpeciesIds()
            val totalTasks = dbSiteIds.size * speciesIds.size
            var currentTask = 0

            dbSiteIds.forEach { siteId ->
                val siteLoc = siteList.find { it.telpostid == siteId }
                val clusterIds = BsiClusterManager.getClusterSiteIds(
                    context,
                    siteLoc?.latitude,
                    siteLoc?.longitude
                )

                speciesIds.forEach { sid ->
                    currentTask++
                    if (currentTask % 20 == 0 || currentTask == totalTasks) {
                        val perc = (98 + (currentTask.toFloat() / totalTasks * 2)).toInt()
                        onProgress("Vault verzegelen voor $siteId ($currentTask/$totalTasks)...", perc, 100)
                    }

                    val distribution = dao.getSpeciesDailyDistribution(sid, clusterIds)
                    val rawCurve = FloatArray(366)
                    distribution.forEach { if (it.day in 1..366) rawCurve[it.day - 1] = it.count.toFloat() }

                    val smoothedCurve = applyGaussianKdeSmoothing(rawCurve, bandwidthDays = 5)
                    val curveString = smoothedCurve.joinToString("|") { String.format(Locale.US, "%.4f", it) }

                    val peakSpring = findPeakInPeriod(rawCurve, 1, 166)
                    val peakAutumn = findPeakInPeriod(rawCurve, 167, 366)

                    dao.insertDailyAnalysisVault(com.yvesds.vt5.core.database.entities.SpeciesPhenologyVault(
                        speciesId = sid,
                        clusterId = siteId, // Gekoppeld aan de specifieke telpost ID
                        dailyBphSeries = curveString,
                        peakSpring = peakSpring,
                        peakAutumn = peakAutumn
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vault failure: ${e.message}")
        }
    }

    private fun applyGaussianKdeSmoothing(rawCurve: FloatArray, bandwidthDays: Int = 5): FloatArray {
        val n = rawCurve.size
        val smoothed = FloatArray(n)
        val sigma = bandwidthDays.toDouble()
        val radius = bandwidthDays * 2
        for (i in 0 until n) {
            var sumWeight = 0.0
            var sumVal = 0.0
            for (offset in -radius..radius) {
                val idx = (i + offset + n) % n
                val weight = exp(-((offset * offset).toDouble()) / (2.0 * sigma * sigma))
                sumVal += rawCurve[idx] * weight
                sumWeight += weight
            }
            smoothed[i] = if (sumWeight > 0.0) (sumVal / sumWeight).toFloat() else rawCurve[i]
        }
        return smoothed
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
