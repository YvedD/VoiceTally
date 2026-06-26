package com.yvesds.vt5.core.database.ui

import android.content.Context
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import com.yvesds.vt5.features.serverdata.model.SpeciesItem

/**
 * Utility om soortid naar soortnaam te vertalen en andersom.
 * Gebruikt ServerDataCache voor snelle toegang tot de stamgegevens.
 */
object SpeciesNameResolver {

    /**
     * Vertaalt een soortid naar een leesbare naam.
     */
    suspend fun getName(context: Context, soortId: String): String {
        val snap = ServerDataCache.getOrLoad(context)
        return snap.speciesById[soortId]?.soortnaam ?: "Onbekend ($soortId)"
    }

    /**
     * Haalt een lijst van alle beschikbare soorten op (naam + id).
     */
    suspend fun getAllSpecies(context: Context): List<SpeciesItem> {
        val snap = ServerDataCache.getOrLoad(context)
        return snap.speciesById.values.toList().sortedBy { it.soortnaam }
    }
}
