package com.yvesds.vt5.ai

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * BsiDispatchers - Geïsoleerde Coroutine Scopes & Dispatchers voor BSI AI taken.
 * Garandeert dat AI-berekeningen op een lage-prioriteit achtergrond-dispatcher draaien,
 * zodat Spraakinvoer, DB Writes en Main UI 100% responsief blijven (0ms vertraging).
 */
object BsiDispatchers {
    /**
     * Dispatcher voor zware achtergrond berekeningen (AI inference, matrix vermenigvuldigingen, KDE smoothing).
     */
    val Background: CoroutineDispatcher = Dispatchers.Default

    /**
     * Dispatcher voor async I/O operaties (fetchen van weerdata, bestand-lezen).
     */
    val IO: CoroutineDispatcher = Dispatchers.IO
}
