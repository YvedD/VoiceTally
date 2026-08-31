package com.yvesds.vt5.ai

import kotlinx.serialization.Serializable
import kotlin.math.*
import kotlin.random.Random

/**
 * LiteNeuralEngine - Pure Kotlin Neural Network (MLP).
 * Vederlicht, zelflerend en specifiek gebouwd voor VT5 vogel-prognoses.
 */
@Serializable
class LiteNeuralEngine(
    val inputSize: Int = 21,
    val hiddenSize: Int = 32,
    val outputSize: Int = 1000
) {
    // Hersencellen (Gewichten en Biases)
    var wInputHidden: Array<FloatArray> = Array(inputSize) { FloatArray(hiddenSize) { Random.nextFloat() * 0.1f - 0.05f } }
    var bHidden: FloatArray = FloatArray(hiddenSize) { 0f }
    
    var wHiddenOutput: Array<FloatArray> = Array(hiddenSize) { FloatArray(outputSize) { Random.nextFloat() * 0.1f - 0.05f } }
    var bOutput: FloatArray = FloatArray(outputSize) { 0f }

    // Activatie-functies: ReLU voor intern, Softmax voor resultaat
    private fun relu(x: Float): Float = max(0f, x)
    
    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0f
        val expValues = FloatArray(logits.size) { i -> exp(logits[i] - maxLogit) }
        val sumExp = expValues.sum()
        return FloatArray(logits.size) { i -> expValues[i] / sumExp }
    }

    /**
     * Voorspel welke vogels er vliegen op basis van de 21 features.
     */
    fun predict(inputs: FloatArray): FloatArray {
        val hiddenLayer = FloatArray(hiddenSize)
        for (j in 0 until hiddenSize) {
            var sum = bHidden[j]
            for (i in 0 until inputSize) sum += inputs[i] * wInputHidden[i][j]
            hiddenLayer[j] = relu(sum)
        }

        val outputLayer = FloatArray(outputSize)
        for (k in 0 until outputSize) {
            var sum = bOutput[k]
            for (j in 0 until hiddenSize) sum += hiddenLayer[j] * wHiddenOutput[j][k]
            outputLayer[k] = sum
        }
        return softmax(outputLayer)
    }

    /**
     * De AI leert van een waarneming (Backpropagation).
     */
    fun train(inputs: FloatArray, targetIndex: Int, learningRate: Float = 0.01f, sampleWeight: Float = 1.0f) {
        if (targetIndex < 0 || targetIndex >= outputSize) return

        // 1. Voorwaartse pass (onthouden voor backprop)
        val hiddenLayer = FloatArray(hiddenSize)
        val hiddenSums = FloatArray(hiddenSize)
        for (j in 0 until hiddenSize) {
            var sum = bHidden[j]
            for (i in 0 until inputSize) sum += inputs[i] * wInputHidden[i][j]
            hiddenSums[j] = sum
            hiddenLayer[j] = relu(sum)
        }

        val outputLayer = FloatArray(outputSize)
        for (k in 0 until outputSize) {
            var sum = bOutput[k]
            for (j in 0 until hiddenSize) sum += hiddenLayer[j] * wHiddenOutput[j][k]
            outputLayer[k] = sum
        }
        val predictions = softmax(outputLayer)

        // 2. Fout berekenen
        // Schaal de output-error met sampleWeight zodat sommige voorbeelden zwaarder doorwegen
        val outputErrors = FloatArray(outputSize) { k -> 
            (predictions[k] - (if (k == targetIndex) 1f else 0f)) * sampleWeight
        }

        val hiddenErrors = FloatArray(hiddenSize)
        for (j in 0 until hiddenSize) {
            var error = 0f
            for (k in 0 until outputSize) error += outputErrors[k] * wHiddenOutput[j][k]
            hiddenErrors[j] = if (hiddenSums[j] > 0) error else 0f
        }

        // 3. Hersencellen aanpassen (Gewichten bijwerken)
        for (k in 0 until outputSize) {
            bOutput[k] -= learningRate * outputErrors[k]
            for (j in 0 until hiddenSize) wHiddenOutput[j][k] -= learningRate * outputErrors[k] * hiddenLayer[j]
        }

        for (j in 0 until hiddenSize) {
            bHidden[j] -= learningRate * hiddenErrors[j]
            for (i in 0 until inputSize) wInputHidden[i][j] -= learningRate * hiddenErrors[j] * inputs[i]
        }
    }
}
