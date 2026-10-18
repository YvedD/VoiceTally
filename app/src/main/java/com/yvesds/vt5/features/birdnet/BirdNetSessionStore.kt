package com.yvesds.vt5.features.birdnet

import android.util.Log

/**
 * Persistente in-memory sessie-store voor BirdNET-GO detecties tijdens een tellingssessie.
 *
 * Zorgt ervoor dat gedetecteerde vogels behouden blijven wanneer de gebruiker
 * tijdelijk naar een ander scherm navigeert (bijv. InstellingenScherm of HuidigeStandScherm/Totalen).
 *
 * Items worden chronologisch toegevoegd (nieuwste onderaan).
 */
object BirdNetSessionStore {

    private const val TAG = "BirdNetSessionStore"
    private const val MAX_ITEMS = 30

    private val items = mutableListOf<BirdNetDetection>()
    private val knownKeys = mutableSetOf<String>()
    private val addedKeys = mutableSetOf<String>()

    /** Haalt een kopie van de huidige lijst met detecties op (oudste bovenaan, nieuwste onderaan). */
    fun getDetections(): List<BirdNetDetection> = synchronized(this) {
        items.toList()
    }

    /** Haalt de set van deduplicatiesleutels op van detecties die al aan de telling zijn toegevoegd. */
    fun getAddedKeys(): Set<String> = synchronized(this) {
        addedKeys.toSet()
    }

    /** Voegt een nieuwe detectie onderaan toe. Returns true als het nieuw was. */
    fun addDetection(detection: BirdNetDetection): Boolean = synchronized(this) {
        val key = detection.deduplicationKey
        if (!knownKeys.add(key)) return false

        items.add(detection)

        while (items.size > MAX_ITEMS) {
            val removed = items.removeAt(0)
            knownKeys.remove(removed.deduplicationKey)
            addedKeys.remove(removed.deduplicationKey)
        }
        return true
    }

    /** Markeert een detectie als toegevoegd aan de telling. */
    fun markAddedToCount(key: String) = synchronized(this) {
        addedKeys.add(key)
    }

    /** Controleert of een detectie al aan de telling is toegevoegd. */
    fun isAddedToCount(key: String): Boolean = synchronized(this) {
        addedKeys.contains(key)
    }

    /** Wis alle opgeslagen detecties (bijv. bij een nieuwe tellingssessie). */
    fun clear() = synchronized(this) {
        Log.i(TAG, "BirdNetSessionStore geleegd voor nieuwe sessie.")
        items.clear()
        knownKeys.clear()
        addedKeys.clear()
    }
}
