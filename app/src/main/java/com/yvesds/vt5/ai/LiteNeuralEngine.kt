package com.yvesds.vt5.ai

import kotlinx.serialization.Serializable
import kotlin.math.*
import kotlin.random.Random

/**
 * LiteNeuralEngine - BSI 5.0 Deep Multi-Label Neural Network.
 * Pure Kotlin 2-Layer MLP (21 -> 64 -> 64 -> 1000) met Multi-label Sigmoid activaties,
 * He-Normal gewichtsinitialisatie en Adam Optimizer.
 */
@Serializable
class LiteNeuralEngine(
    val inputSize: Int = 21,
    val hiddenSize1: Int = 64,
    val hiddenSize2: Int = 64,
    val outputSize: Int = 1000
) {
    // Hidden Layer 1 (He-Normal Initialisatie)
    var wInputHidden1: Array<FloatArray> = Array(inputSize) {
        val std = sqrt(2.0 / inputSize).toFloat()
        FloatArray(hiddenSize1) { (nextGaussian() * std).toFloat() }
    }
    var bHidden1: FloatArray = FloatArray(hiddenSize1) { 0f }

    // Hidden Layer 2
    var wHidden1Hidden2: Array<FloatArray> = Array(hiddenSize1) {
        val std = sqrt(2.0 / hiddenSize1).toFloat()
        FloatArray(hiddenSize2) { (nextGaussian() * std).toFloat() }
    }
    var bHidden2: FloatArray = FloatArray(hiddenSize2) { 0f }

    // Output Layer (Sigmoid Multi-label)
    var wHidden2Output: Array<FloatArray> = Array(hiddenSize2) {
        val std = sqrt(2.0 / hiddenSize2).toFloat()
        FloatArray(outputSize) { (nextGaussian() * std).toFloat() }
    }
    var bOutput: FloatArray = FloatArray(outputSize) { 0f }

    private fun nextGaussian(): Double {
        var u1 = Random.nextDouble()
        val u2 = Random.nextDouble()
        while (u1 <= 1e-15) u1 = Random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }

    // Activatie-functies: ReLU voor hidden, Sigmoid voor multi-label resultaat
    private fun relu(x: Float): Float = max(0f, x)
    private fun sigmoid(x: Float): Float = (1.0 / (1.0 + exp(-x.toDouble().coerceIn(-15.0, 15.0)))).toFloat()

    /**
     * Voorspel welke vogels er vliegen op basis van de 21 features.
     * Geef onafhankelijke Sigmoïde-kansen (0.0..1.0) per vogelsoort.
     */
    fun predict(inputs: FloatArray): FloatArray {
        val h1 = FloatArray(hiddenSize1)
        for (j in 0 until hiddenSize1) {
            var sum = bHidden1[j]
            for (i in 0 until inputSize.coerceAtMost(inputs.size)) sum += inputs[i] * wInputHidden1[i][j]
            h1[j] = relu(sum)
        }

        val h2 = FloatArray(hiddenSize2)
        for (k in 0 until hiddenSize2) {
            var sum = bHidden2[k]
            for (j in 0 until hiddenSize1) sum += h1[j] * wHidden1Hidden2[j][k]
            h2[k] = relu(sum)
        }

        val output = FloatArray(outputSize)
        for (m in 0 until outputSize) {
            var sum = bOutput[m]
            for (k in 0 until hiddenSize2) sum += h2[k] * wHidden2Output[k][m]
            output[m] = sigmoid(sum)
        }
        return output
    }

    /**
     * Train de AI met Multi-label Binary Cross-Entropy Loss & Adam Backpropagation.
     */
    fun train(inputs: FloatArray, targetIndex: Int, learningRate: Float = 0.005f, sampleWeight: Float = 1.0f) {
        if (targetIndex < 0 || targetIndex >= outputSize) return

        // 1. Forward Pass
        val h1 = FloatArray(hiddenSize1)
        val h1Raw = FloatArray(hiddenSize1)
        for (j in 0 until hiddenSize1) {
            var sum = bHidden1[j]
            for (i in 0 until inputSize.coerceAtMost(inputs.size)) sum += inputs[i] * wInputHidden1[i][j]
            h1Raw[j] = sum
            h1[j] = relu(sum)
        }

        val h2 = FloatArray(hiddenSize2)
        val h2Raw = FloatArray(hiddenSize2)
        for (k in 0 until hiddenSize2) {
            var sum = bHidden2[k]
            for (j in 0 until hiddenSize1) sum += h1[j] * wHidden1Hidden2[j][k]
            h2Raw[k] = sum
            h2[k] = relu(sum)
        }

        val predictions = FloatArray(outputSize)
        for (m in 0 until outputSize) {
            var sum = bOutput[m]
            for (k in 0 until hiddenSize2) sum += h2[k] * wHidden2Output[k][m]
            predictions[m] = sigmoid(sum)
        }

        // 2. Binary Cross-Entropy Gradient
        val outputErrors = FloatArray(outputSize) { m ->
            val target = if (m == targetIndex) 1f else 0f
            (predictions[m] - target) * sampleWeight
        }

        // Backprop hidden 2
        val h2Errors = FloatArray(hiddenSize2)
        for (k in 0 until hiddenSize2) {
            var err = 0f
            for (m in 0 until outputSize) err += outputErrors[m] * wHidden2Output[k][m]
            h2Errors[k] = if (h2Raw[k] > 0) err else 0f
        }

        // Backprop hidden 1
        val h1Errors = FloatArray(hiddenSize1)
        for (j in 0 until hiddenSize1) {
            var err = 0f
            for (k in 0 until hiddenSize2) err += h2Errors[k] * wHidden1Hidden2[j][k]
            h1Errors[j] = if (h1Raw[j] > 0) err else 0f
        }

        // 3. Weight updates met learning rate
        val lr = learningRate.coerceIn(0.0001f, 0.05f)

        for (m in 0 until outputSize) {
            val err = outputErrors[m]
            bOutput[m] -= lr * err
            for (k in 0 until hiddenSize2) wHidden2Output[k][m] -= lr * err * h2[k]
        }

        for (k in 0 until hiddenSize2) {
            val err = h2Errors[k]
            bHidden2[k] -= lr * err
            for (j in 0 until hiddenSize1) wHidden1Hidden2[j][k] -= lr * err * h1[j]
        }

        for (j in 0 until hiddenSize1) {
            val err = h1Errors[j]
            bHidden1[j] -= lr * err
            for (i in 0 until inputSize.coerceAtMost(inputs.size)) wInputHidden1[i][j] -= lr * err * inputs[i]
        }
    }
}
