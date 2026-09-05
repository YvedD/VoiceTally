package com.yvesds.vt5.ai

import android.content.Context

/**
 * Kleine runtime cache voor de AI/BSI artefacten.
 *
 * De prognoses roepen dezelfde modellen meerdere keren op tijdens één scherm-levenscyclus.
 * Door hier te cachen vermijden we herhaald laden van SAF-bestanden en JSON/ProtoBuf parsing.
 */
object AiRuntimeModelCache {
    private val lock = Any()

    @Volatile private var expertKnowledgeLoaded = false
    @Volatile private var expertKnowledgeCache: ExpertKnowledgeBase? = null

    @Volatile private var modelLabelsLoaded = false
    @Volatile private var modelLabelsCache: List<String>? = null

    @Volatile private var tensorLoaded = false
    @Volatile private var tensorCache: BsiResponseTensor? = null

    @Volatile private var neuralEngineLoadedOutputSize = -1
    @Volatile private var neuralEngineCache: LiteNeuralEngine? = null

    @Volatile private var dailyWeightsLoaded = false
    @Volatile private var dailyWeightsCache: Map<String, Float> = emptyMap()

    fun invalidateExpertKnowledge() = synchronized(lock) {
        expertKnowledgeLoaded = false
        expertKnowledgeCache = null
    }

    fun invalidateModelLabels() = synchronized(lock) {
        modelLabelsLoaded = false
        modelLabelsCache = null
    }

    fun invalidateTensor() = synchronized(lock) {
        tensorLoaded = false
        tensorCache = null
    }

    fun invalidateNeuralEngine() = synchronized(lock) {
        neuralEngineLoadedOutputSize = -1
        neuralEngineCache = null
    }

    fun invalidateDailyWeights() = synchronized(lock) {
        dailyWeightsLoaded = false
        dailyWeightsCache = emptyMap()
    }

    fun invalidateAll() {
        invalidateExpertKnowledge()
        invalidateModelLabels()
        invalidateTensor()
        invalidateNeuralEngine()
        invalidateDailyWeights()
    }

    fun getExpertKnowledge(modelStore: ModelStore): ExpertKnowledgeBase? {
        if (expertKnowledgeLoaded) return expertKnowledgeCache
        synchronized(lock) {
            if (!expertKnowledgeLoaded) {
                expertKnowledgeCache = modelStore.loadExpertKnowledge()
                expertKnowledgeLoaded = true
            }
            return expertKnowledgeCache
        }
    }

    fun getModelLabels(modelStore: ModelStore): List<String>? {
        if (modelLabelsLoaded) return modelLabelsCache
        synchronized(lock) {
            if (!modelLabelsLoaded) {
                modelLabelsCache = modelStore.loadModelLabels()
                modelLabelsLoaded = true
            }
            return modelLabelsCache
        }
    }

    fun getResponseTensor(modelStore: ModelStore): BsiResponseTensor? {
        if (tensorLoaded) return tensorCache
        synchronized(lock) {
            if (!tensorLoaded) {
                tensorCache = modelStore.loadBsiResponseTensor()
                tensorLoaded = true
            }
            return tensorCache
        }
    }

    fun getNeuralEngine(modelStore: ModelStore, outputSize: Int): LiteNeuralEngine {
        if (neuralEngineLoadedOutputSize == outputSize && neuralEngineCache != null) {
            return neuralEngineCache!!
        }
        synchronized(lock) {
            if (neuralEngineLoadedOutputSize != outputSize || neuralEngineCache == null) {
                neuralEngineCache = modelStore.loadNeuralEngine(outputSize)
                neuralEngineLoadedOutputSize = outputSize
            }
            return neuralEngineCache!!
        }
    }

    suspend fun getDailyWeights(context: Context): Map<String, Float> {
        if (dailyWeightsLoaded) return dailyWeightsCache

        val computed = try {
            TrainingDataPreparer(context).computeSpeciesWeightsFromDailyAnalysis()
        } catch (_: Exception) {
            emptyMap()
        }

        synchronized(lock) {
            if (!dailyWeightsLoaded) {
                dailyWeightsCache = computed
                dailyWeightsLoaded = true
            }
            return dailyWeightsCache
        }
    }
}

