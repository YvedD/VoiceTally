@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
package com.yvesds.vt5.features.serverdata.model

import android.content.Context
import android.util.Log
import com.yvesds.vt5.core.opslag.FileLogger
import kotlinx.coroutines.*

/**
 * Improved in-memory cache for DataSnapshot with safe, single-loader semantics.
 *
 * Key changes:
 * - Uses FileLogger for on-device debugging.
 * - Two-phase loading: minimal essentials first, full dataset in background.
 */
object ServerDataCache {
    private const val TAG = "ServerDataCache"

    @Volatile
    private var cached: DataSnapshot? = null

    @Volatile
    private var lastLoadTimeMs: Long = 0

    @Volatile
    private var loadingDeferred: Deferred<DataSnapshot>? = null

    private val loaderScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun getLogger(context: Context) = FileLogger(context)

    fun invalidate(context: Context? = null) {
        cached = null
        loadingDeferred = null
        if (context != null) {
            ServerDataRepository(context).clearFileCache()
            loaderScope.launch {
                getLogger(context).info("ServerDataCache geïnvalideerd")
            }
        }
    }

    fun getCachedOrNull(): DataSnapshot? = cached

    /**
     * Twee-fase opstart:
     * Fase 1: Laad minimale metadata (telposten + codes)
     * Fase 2: Laad de rest in de achtergrond
     */
    fun preload(context: Context) {
        if (cached != null && cached?.hasFullDataset() == true) return

        if (loadingDeferred != null && loadingDeferred?.isActive == true) return

        synchronized(this) {
            if (loadingDeferred == null || loadingDeferred?.isCompleted == true) {
                loadingDeferred = loaderScope.async {
                    loadMinimalSnapshot(context)
                }
                
                loaderScope.launch {
                    delay(500)
                    try {
                        val fullSnapshot = loadFromSaf(context)
                        cached = fullSnapshot
                        getLogger(context).info("Fase 2 voltooid: alle serverdata geladen in achtergrond")
                    } catch (ex: Exception) {
                        getLogger(context).warn("Fase 2 achtergrond-load mislukt: ${ex.message}")
                    }
                }
            }
        }
    }
    
    private suspend fun loadMinimalSnapshot(context: Context): DataSnapshot = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val repo = ServerDataRepository(context.applicationContext)
            val snap = repo.loadMinimalData()
            if (!snap.hasMetadataEssentials()) {
                throw IllegalStateException("Minimale serverdata onvolledig: telposten of codes ontbreken")
            }
            if (cached == null || cached?.hasFullDataset() == false) {
                cached = snap
            }
            val elapsed = System.currentTimeMillis() - start
            getLogger(context).info("loadMinimalSnapshot: minimale snapshot geladen in ${elapsed}ms")
            return@withContext snap
        } catch (ex: Exception) {
            getLogger(context).error("loadMinimalSnapshot mislukt: ${ex.message}")
            throw ex
        } finally {
            synchronized(this@ServerDataCache) {
                if (loadingDeferred?.isCompleted == true) loadingDeferred = null
            }
        }
    }

    suspend fun getOrLoad(context: Context): DataSnapshot = coroutineScope {
        cached?.let {
            if (it.hasFullDataset()) return@coroutineScope it
        }

        val existing = loadingDeferred
        if (existing != null) {
            try {
                val snap = existing.await()
                if (snap.hasFullDataset()) return@coroutineScope snap
            } catch (ex: Exception) {
                if (ex is CancellationException) throw ex
                getLogger(context).warn("getOrLoad - achtergrondlader mislukt: ${ex.message}; probeer directe load")
            }
        }

        val loader = synchronized(this@ServerDataCache) {
            val cur = loadingDeferred
            if (cur != null && cur.isActive) {
                cur
            } else {
                loaderScope.async {
                    loadFromSaf(context)
                }.also { loadingDeferred = it }
            }
        }

        try {
            return@coroutineScope loader.await()
        } catch (ex: Exception) {
            if (ex is CancellationException) throw ex
            synchronized(this@ServerDataCache) {
                if (loadingDeferred === loader) loadingDeferred = null
            }
            getLogger(context).error("getOrLoad mislukt bij laden data: ${ex.message}")
            throw ex
        }
    }

    private suspend fun loadFromSaf(context: Context): DataSnapshot = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val repo = ServerDataRepository(context.applicationContext)
            val snap = repo.loadAllFromSaf()
            
            if (!snap.hasFullDataset()) {
                val missing = mutableListOf<String>()
                if (!snap.hasMetadataEssentials()) missing.add("Telposten/Codes")
                if (!snap.hasSpeciesData()) missing.add("Soortenlijst")
                if (snap.siteSpeciesBySite.isEmpty()) missing.add("Telpost-soorten mapping (site_species)")
                
                val errorMsg = "Volledige serverdata onvolledig. Ontbrekend: ${missing.joinToString(", ")}"
                getLogger(context).warn(errorMsg)
                throw IllegalStateException(errorMsg)
            }
            
            cached = snap
            lastLoadTimeMs = System.currentTimeMillis() - start
            getLogger(context).info("loadFromSaf: volledige snapshot geladen in ${lastLoadTimeMs}ms")
            return@withContext snap
        } catch (ex: Exception) {
            getLogger(context).error("loadFromSaf mislukt: ${ex.message}")
            throw ex
        } finally {
            synchronized(this@ServerDataCache) {
                if (loadingDeferred?.isCompleted == true) loadingDeferred = null
            }
        }
    }
}
