package com.yvesds.vt5.ai

import com.yvesds.vt5.utils.weather.WeatherManager
import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * BsiResponseTensor - precomputed responsoppervlak voor BSI-prognoses.
 *
 * Per soort bewaren we een 365-dagen respons per combinatie van windrichting (16)
 * en windkracht (7). Dat levert 365 * 112 cellen per soort op.
 */
@Serializable
data class BsiResponseTensor(
    val dayCount: Int = DAY_COUNT,
    val windDirectionLabels: List<String> = WIND_DIRECTION_LABELS.toList(),
    val windForceLabels: List<Int> = WIND_FORCE_LABELS.toList(),
    val valuesBySpecies: Map<String, FloatArray> = emptyMap(),
    val statsBySpecies: Map<String, BsiTensorStats> = emptyMap(),
    val createdAtEpochMillis: Long = 0L
) {

    val windBinCount: Int get() = windDirectionLabels.size * windForceLabels.size
    val cellCount: Int get() = dayCount * windBinCount

    fun hasSpecies(speciesId: String): Boolean = valuesBySpecies.containsKey(speciesId)

    fun lookupRawValue(speciesId: String, dayOfYear: Int, windDeg: Double?, windSpeedMs: Double?): Float? {
        val values = valuesBySpecies[speciesId] ?: return null
        if (values.size < cellCount) return null
        val idx = cellIndex(
            dayIndex = dayIndex(dayOfYear),
            windDirectionIndex = windDirectionIndexFromDegrees(windDeg),
            windForceIndex = windForceIndexFromMs(windSpeedMs)
        )
        return values.getOrNull(idx)
    }

    /**
     * Zet de raw tensorwaarde om naar een multiplicatieve factor rond 1.0.
     *
     * 1.0 = neutraal, > 1.0 = gunstiger, < 1.0 = ongunstiger.
     */
    fun lookupFactor(speciesId: String, dayOfYear: Int, windDeg: Double?, windSpeedMs: Double?): Float? {
        val raw = lookupRawValue(speciesId, dayOfYear, windDeg, windSpeedMs) ?: return null
        val stats = statsBySpecies[speciesId] ?: return 1f
        if (stats.std <= 0.0001f) return 1f

        val z = (raw - stats.mean) / stats.std
        return (1f + (z.coerceIn(-1.5f, 3.0f) * 0.35f)).coerceAtLeast(0.2f)
    }

    companion object {
        const val DAY_COUNT = 365
        const val WIND_DIRECTION_COUNT = 16
        const val WIND_FORCE_COUNT = 7
        const val BIN_COUNT = WIND_DIRECTION_COUNT * WIND_FORCE_COUNT

        val WIND_DIRECTION_LABELS = arrayOf(
            "N", "NNO", "NO", "ONO", "O", "OZO", "ZO", "ZZO",
            "Z", "ZZW", "ZW", "WZW", "W", "WNW", "NW", "NNW"
        )
        val WIND_FORCE_LABELS = IntArray(WIND_FORCE_COUNT) { it }

        fun dayIndex(dayOfYear: Int): Int = dayOfYear.coerceIn(1, DAY_COUNT) - 1

        fun windDirectionIndex(label: String?): Int {
            val normalized = label?.trim()?.uppercase().orEmpty()
            if (normalized.isEmpty()) return 0
            val idx = WIND_DIRECTION_LABELS.indexOf(normalized)
            return if (idx >= 0) idx else 0
        }

        fun windDirectionIndexFromDegrees(deg: Double?): Int {
            val value = deg ?: return 0
            val normalized = ((value % 360.0) + 360.0) % 360.0
            val idx = floor(((normalized + 11.25) % 360.0) / 22.5).toInt()
            return idx.coerceIn(0, WIND_DIRECTION_COUNT - 1)
        }

        fun windForceIndex(forceLabel: String?): Int {
            val normalized = forceLabel?.trim()?.replace(',', '.').orEmpty()
            if (normalized.isEmpty()) return 0
            val numeric = Regex("-?\\d+(?:[\\.]\\d+)?").find(normalized)?.value?.toDoubleOrNull()
            if (numeric != null) return numeric.roundToInt().coerceIn(0, WIND_FORCE_COUNT - 1)
            return 0
        }

        fun windForceIndexFromMs(ms: Double?): Int {
            val bft = WeatherManager.msToBeaufort(ms)
            return bft.coerceIn(0, WIND_FORCE_COUNT - 1)
        }

        fun cellIndex(dayIndex: Int, windDirectionIndex: Int, windForceIndex: Int): Int {
            return (dayIndex * BIN_COUNT) + (windDirectionIndex * WIND_FORCE_COUNT) + windForceIndex
        }

        fun gaussianKernel(radius: Int, sigma: Double): FloatArray {
            val size = radius * 2 + 1
            val kernel = FloatArray(size)
            var sum = 0.0
            for (offset in -radius..radius) {
                val weight = exp(-((offset * offset).toDouble()) / (2.0 * sigma * sigma))
                kernel[offset + radius] = weight.toFloat()
                sum += weight
            }
            if (sum <= 0.0) return kernel
            for (i in kernel.indices) {
                kernel[i] = (kernel[i] / sum).toFloat()
            }
            return kernel
        }
    }
}

@Serializable
data class BsiTensorStats(
    val mean: Float = 0f,
    val std: Float = 1f,
    val totalCount: Long = 0L,
    val maxValue: Float = 0f
)


