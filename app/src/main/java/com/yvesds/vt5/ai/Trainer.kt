package com.yvesds.vt5.ai

import android.content.Context
import android.util.Log
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.ui.SpeciesNameResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Trainer - Plan B: Bio-Statistische Intelligentie (BSI) Profiler.
 * In plaats van TensorFlow te trainen, bouwen we razendsnelle statistische profielen 
 * in de Room DB en het geheugen. Dit is 100x sneller en vederlicht.
 */
class Trainer(private val context: Context, private val modelStore: ModelStore) {
    private val TAG = "BsiProfiler"

    suspend fun runOnDeviceTraining(onProgress: (String, Int, Int) -> Unit) = withContext(Dispatchers.Default) {
        try {
            Log.i(TAG, "Start BSI & Neural Training...")
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
                
                if (index % 500 == 0) {
                    count = index
                    val perc = (20 + (index.toFloat() / total * 75)).toInt()
                    onProgress("AI leert van waarnemingen...\n($index van $total)", perc, 100)
                }
            }

            // 5. Sla de nieuwe gewichten op
            onProgress("Nieuwe ervaringen opslaan...", 95, 100)
            modelStore.saveNeuralEngine(engine)

            // 6. Voer Retroactieve Piek-Analyse uit (Knowledge Base)
            onProgress("Meteorologische vingerafdrukken berekenen...", 96, 100)
            val kbManager = ExpertKnowledgeManager(context)
            kbManager.analyzeHistoricalPeaks { msg, curr, total ->
                onProgress(msg, curr, total)
            }

            // 7. Genereer Wetenschappelijke Taxonomie JSON (NIEUW)
            onProgress("Wetenschappelijke taxonomie bijwerken...", 99, 100)
            val taxonomyManager = TaxonomyManager(context)
            taxonomyManager.generateActiveTaxonomyJson()

            onProgress("Training succesvol voltooid!", 100, 100)
            Log.i(TAG, "Neural training voltooid.")
            delay(1000)

        } catch (e: Exception) {
            Log.e(TAG, "Training mislukt: ${e.message}", e)
            onProgress("Fout bij trainen: ${e.message}", 0, 0)
        }
    }

    // Placeholder voor data-modellen die elders gebruikt worden
    data class TrainingSample(val features: FloatArray, val labelIndex: Int)
}
