package com.yvesds.vt5.features.telling

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manager voor het lezen en schrijven van auto_annotatie.json.
 * Zorgt ervoor dat handmatig ingevoerde JSON veilig wordt gelezen en de app niet laat crashen bij typfouten.
 */
object AutoAnnotationConfigManager {
    private const val TAG = "AutoAnnotConfig"
    
    // In-memory opslag voor razendsnelle checks tijdens het tellen
    private val autoAnnotateSpecies = mutableSetOf<String>()

    /**
     * Genereert het pad naar [InterneOpslag]/VT5/assets/auto_annotatie.json
     */
    private fun getConfigFile(context: Context): File {
        val externalRoot = context.getExternalFilesDir(null) ?: context.filesDir
        val vt5Root = File(externalRoot, "VT5")
        val assetsDir = File(vt5Root, "assets")
        if (!assetsDir.exists()) {
            assetsDir.mkdirs()
        }
        return File(assetsDir, "auto_annotatie.json")
    }

    /**
     * Laad de configuratie eenmalig in het geheugen.
     * Te roepen bij het opstarten van het tellingscherm of het instellingenscherm.
     */
    fun loadConfig(context: Context) {
        val file = getConfigFile(context)
        if (!file.exists()) {
            // Maak een leeg standaardbestand aan als het nog niet bestaat
            saveConfig(context, emptySet())
            return
        }

        try {
            val jsonString = file.readText()
            val jsonObject = JSONObject(jsonString)
            val soortenArray = jsonObject.optJSONArray("soorten")
            
            autoAnnotateSpecies.clear()
            if (soortenArray != null) {
                for (i in 0 until soortenArray.length()) {
                    val soortId = soortenArray.optString(i)
                    if (soortId.isNotBlank()) {
                        autoAnnotateSpecies.add(soortId)
                    }
                }
            }
            Log.i(TAG, "Geladen auto-annotatie configuratie: ${autoAnnotateSpecies.size} soorten.")
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij inlezen auto_annotatie.json, mogelijk ongeldige JSON", e)
            // We crashen niet! Bij corrupte JSON maken we simpelweg de actieve lijst leeg 
            // zodat er geen onvoorspelbaar gedrag optreedt.
            autoAnnotateSpecies.clear()
        }
    }

    /**
     * Slaat de huidige selectie op naar de JSON file met een nette formattering,
     * zodat de gebruiker het nadien makkelijk handmatig in Kladblok kan lezen/aanpassen.
     */
    fun saveConfig(context: Context, speciesIds: Set<String>) {
        val file = getConfigFile(context)
        try {
            val jsonObject = JSONObject()
            val jsonArray = JSONArray()
            speciesIds.forEach { jsonArray.put(it) }
            jsonObject.put("soorten", jsonArray)

            // 4 spaties indentatie voor leesbaarheid als een mens het opent
            file.writeText(jsonObject.toString(4)) 
            
            // Update in-memory lijst
            autoAnnotateSpecies.clear()
            autoAnnotateSpecies.addAll(speciesIds)
            Log.i(TAG, "Auto-annotatie configuratie succesvol opgeslagen.")
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij opslaan auto_annotatie.json", e)
        }
    }

    /**
     * Controleert razendsnel of een soortId automatisch de annotatie popup moet krijgen.
     */
    fun isAutoAnnotate(soortId: String): Boolean {
        return autoAnnotateSpecies.contains(soortId)
    }

    /**
     * Geeft een kopie van de in-memory lijst, bijv. voor het instellingenscherm.
     */
    fun getAutoAnnotateList(): Set<String> {
        return autoAnnotateSpecies.toSet()
    }
}
