package com.yvesds.vt5.ai

/**
 * AiConfig - static configuration for AI module (reference points, rare-species weights)
 */
object AiConfig {
    // Reference coordinates (lat, lon) for south / north reference regions
    val SPRING_SOUTH_REFS = listOf(
        Pair(49.16, -0.15), // Ouistreham
        Pair(49.34, -0.85), // Pointe du Hoc (approx)
        Pair(49.9, 2.3) // Amiens area (approx)
    )

    val AUTUMN_NORTH_REFS = listOf(
        Pair(52.0705, 4.3007), // Den Haag
        Pair(51.8126, 5.8372), // Nijmegen
        Pair(53.0793, 8.8017)  // Bremen
    )

    // Belgian Coast Reference Points (Bredene and North)
    val COAST_REFS = listOf(
        Pair(51.2349, 2.9756), // Bredene (Basis)
        Pair(51.3000, 3.1200), // Blankenberge/Zeebrugge (Noord 1)
        Pair(51.3500, 3.3000)  // Knokke-Heist (Noord 2)
    )

    // Default rare species weights (example mapping soortid -> weight)
    val RARE_SPECIES_WEIGHTS: Map<String, Int> = mapOf(
        // fill with known rare soortids as needed, e.g. "1234" to 250
    )

    // Default weight to apply when a species is explicitly marked rare (if used)
    const val DEFAULT_RARE_WEIGHT: Int = 1000 // Krachtiger signaal voor 'krenten'

    fun getSampleWeightForSpecies(soortid: String?): Int {
        if (soortid == null) return 1
        // Verhoogde weging voor massa-trek/ruitrek soorten om patronen te versterken
        val speciesWeights = mapOf(
            "29" to 50,  // Bergeend
            "123" to 10, // Vink
            "125" to 10  // Spreeuw
        )
        return speciesWeights[soortid] ?: RARE_SPECIES_WEIGHTS[soortid] ?: 1
    }
}

