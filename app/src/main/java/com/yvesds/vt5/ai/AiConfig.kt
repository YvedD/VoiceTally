package com.yvesds.vt5.ai

/**
 * AiConfig - static configuration for AI module (reference points, rare-species weights)
 */
object AiConfig {
    // Reference coordinates (lat, lon) for south / north reference regions
    val SPRING_SOUTH_REFS = listOf(
        Pair(49.33, -0.46), // Ouistreham
        Pair(49.34, -0.85), // Pointe du Hoc (approx)
        Pair(49.9, 2.3) // Amiens area (approx)
    )

    val AUTUMN_NORTH_REFS = listOf(
        Pair(52.0705, 4.3007), // Den Haag
        Pair(51.8126, 5.8372), // Nijmegen
        Pair(53.0793, 8.8017)  // Bremen
    )

    // Default rare species weights (example mapping soortid -> weight)
    val RARE_SPECIES_WEIGHTS: Map<String, Int> = mapOf(
        // fill with known rare soortids as needed, e.g. "1234" to 250
    )

    // Default weight to apply when a species is explicitly marked rare (if used)
    const val DEFAULT_RARE_WEIGHT: Int = 250

    fun getSampleWeightForSpecies(soortid: String?): Int {
        if (soortid == null) return 1
        return RARE_SPECIES_WEIGHTS[soortid] ?: 1
    }
}

