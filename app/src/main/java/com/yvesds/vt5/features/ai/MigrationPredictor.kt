package com.yvesds.vt5.features.ai

import android.content.Context
import android.util.Log
import com.yvesds.vt5.core.opslag.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * MigrationPredictor: Handles TFLite model inference for bird migration.
 * Updated to handle multi-input models where each species is predicted individually.
 */
class MigrationPredictor(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var scalers: JsonObject? = null
    private var speciesLabels: Map<Int, String>? = null
    private var phenologyMapping: Map<String, Double>? = null
    private var groupPhenologyMapping: Map<String, Double>? = null

    private val json = Json { ignoreUnknownKeys = true }
    private val fileLogger = FileLogger(context)
    private val logScope = CoroutineScope(Dispatchers.IO)

    init {
        try {
            loadModel()
            loadMetadata()
            logModelDiagnostics()
        } catch (e: Exception) {
            Log.e("MigrationPredictor", "Failed to initialize: ${e.message}")
            logScope.launch { fileLogger.error("AI Init Error: ${e.message}") }
        }
    }

    private fun logModelDiagnostics() {
        val interp = interpreter ?: return
        logScope.launch {
            val sb = StringBuilder("AI Model Diagnostics:\n")
            sb.append("Input Tensors: ${interp.inputTensorCount}\n")
            for (i in 0 until interp.inputTensorCount) {
                val t = interp.getInputTensor(i)
                sb.append("  In $i: name=${t.name()}, shape=${Arrays.toString(t.shape())}, type=${t.dataType()}\n")
            }
            sb.append("Output Tensors: ${interp.outputTensorCount}\n")
            for (i in 0 until interp.outputTensorCount) {
                val t = interp.getOutputTensor(i)
                sb.append("  Out $i: name=${t.name()}, shape=${Arrays.toString(t.shape())}, type=${t.dataType()}\n")
            }
            fileLogger.info(sb.toString())
        }
    }

    private fun loadModel() {
        val modelFileDescriptor = context.assets.openFd("voicetally_migration_model.tflite")
        val inputStream = FileInputStream(modelFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = modelFileDescriptor.startOffset
        val declaredLength = modelFileDescriptor.declaredLength
        val buffer: MappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        interpreter = Interpreter(buffer)
    }

    private fun loadMetadata() {
        val assets = context.assets
        
        scalers = assets.open("scalers.json").bufferedReader().use { it.readText() }
            .let { json.parseToJsonElement(it).jsonObject }

        speciesLabels = assets.open("species_labels.json").bufferedReader().use { it.readText() }
            .let { 
                val obj = json.parseToJsonElement(it).jsonObject
                obj.map { (k, v) -> k.toInt() to v.jsonPrimitive.content }.toMap()
            }

        phenologyMapping = assets.open("phenology_mapping.json").bufferedReader().use { it.readText() }
            .let { 
                val obj = json.parseToJsonElement(it).jsonObject
                obj.map { (k, v) -> k to v.jsonPrimitive.double }.toMap()
            }

        groupPhenologyMapping = assets.open("group_phenology_mapping.json").bufferedReader().use { it.readText() }
            .let { 
                val obj = json.parseToJsonElement(it).jsonObject
                obj.map { (k, v) -> k to v.jsonPrimitive.double }.toMap()
            }
    }

    data class PredictionInput(
        val temp: Double,
        val windSpeed10m: Double,
        val windDirectionDeg: Double,
        val pressureMsl: Double,
        val cloudCover: Double,
        val tempUpstream: Double,
        val pressureUpstream: Double,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun predict(input: PredictionInput): List<PredictionResult> {
        val interp = interpreter ?: return emptyList()
        val labels = speciesLabels ?: return emptyList()

        val cal = Calendar.getInstance().apply { timeInMillis = input.timestamp }
        val dagVanJaar = cal.get(Calendar.DAY_OF_YEAR).toDouble()
        val maand = (cal.get(Calendar.MONTH) + 1).toDouble()

        // Prepare weather features (In 0: [1, 12])
        val weatherFeatures = FloatArray(12)
        weatherFeatures[0] = scale(input.temp, "temp")
        weatherFeatures[1] = scale(input.windSpeed10m, "windSpeed10m")
        weatherFeatures[2] = scale(sin(Math.toRadians(input.windDirectionDeg)), "wind_sin")
        weatherFeatures[3] = scale(cos(Math.toRadians(input.windDirectionDeg)), "wind_cos")
        weatherFeatures[4] = scale(input.pressureMsl, "pressureMsl")
        weatherFeatures[5] = scale(input.cloudCover, "cloudCover")
        weatherFeatures[6] = scale(input.tempUpstream, "temp_upstream")
        weatherFeatures[7] = scale(input.pressureUpstream, "pressure_upstream")
        weatherFeatures[8] = scale(dagVanJaar, "dagvanjaar")
        weatherFeatures[9] = scale(maand, "maand")
        
        // FIX: Ensure no NaN values by using fallbacks
        val phenos = phenologyMapping?.filter { it.key.endsWith("_$maand") }?.values
        val avgPhenology = if (phenos.isNullOrEmpty()) 0.5 else phenos.average()
        
        val groupPhenos = groupPhenologyMapping?.filter { it.key.endsWith("_$maand") }?.values
        val avgGroupPhenology = if (groupPhenos.isNullOrEmpty()) 0.5 else groupPhenos.average()
        
        weatherFeatures[10] = scale(avgPhenology, "phenology_score")
        weatherFeatures[11] = scale(avgGroupPhenology, "group_phenology_score")

        val results = mutableListOf<PredictionResult>()
        
        try {
            // Log technical info once per session
            val logData = weatherFeatures.joinToString(", ") { "%.4f".format(it) }
            logScope.launch { fileLogger.info("Weather Input: [$logData]") }

            // Since the model has 2 inputs [1,12] and [1,1] and 1 output [1,1],
            // it predicts for ONE species at a time. We iterate over all species.
            val speciesInput = arrayOf(intArrayOf(0)) // [1, 1] INT32
            val outputData = arrayOf(floatArrayOf(0f)) // [1, 1] FLOAT32
            
            val inputs = arrayOf<Any>(arrayOf(weatherFeatures), speciesInput)
            val outputs = mutableMapOf<Int, Any>(0 to outputData)

            for ((idx, name) in labels) {
                speciesInput[0][0] = idx
                interp.runForMultipleInputsOutputs(inputs, outputs)
                
                val score = outputData[0][0].toDouble()
                if (score > 0.005) {
                    results.add(PredictionResult(name, score))
                }
            }
        } catch (e: Exception) {
            Log.e("MigrationPredictor", "Multi-input prediction failed: ${e.message}")
            logScope.launch { fileLogger.error("AI Prediction Failed: ${e.message}\n${Log.getStackTraceString(e)}") }
            throw e
        }

        return results.sortedByDescending { it.confidence }
    }

    private fun scale(value: Double, feature: String): Float {
        val s = scalers?.get(feature)?.jsonObject ?: return value.toFloat()
        val min = s["min"]?.jsonPrimitive?.double ?: 0.0
        val max = s["max"]?.jsonPrimitive?.double ?: 1.0
        if (max == min) return 0f
        return ((value - min) / (max - min)).toFloat()
    }

    data class PredictionResult(val speciesName: String, val confidence: Double)
}
