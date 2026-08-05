package com.yvesds.vt5.core.database

import android.content.Context
import android.content.SharedPreferences

/**
 * SessionIdManager - Beheert de numerieke teller voor teldocumenten.
 * Garandeert dat elke nieuwe telling een opvolgend nummer krijgt (1, 2, 3...).
 */
class SessionIdManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("session_ids", Context.MODE_PRIVATE)

    /**
     * Haalt het volgende beschikbare numerieke ID op en verhoogt de teller.
     */
    @Synchronized
    fun getNextId(): String {
        val current = prefs.getLong("next_telling_id", 1L)
        prefs.edit().putLong("next_telling_id", current + 1).apply()
        return current.toString()
    }

    /**
     * Reset de teller naar 1 (bijv. na database reset).
     */
    fun reset() {
        prefs.edit().putLong("next_telling_id", 1L).apply()
    }

    /**
     * Initialiseert de teller op basis van de hoogste waarde in de database indien nodig.
     * Wordt gebruikt bij migratie of herstel.
     */
    fun initializeWithMax(maxId: Long) {
        val current = prefs.getLong("next_telling_id", 1L)
        if (maxId >= current) {
            prefs.edit().putLong("next_telling_id", maxId + 1).apply()
        }
    }
}
