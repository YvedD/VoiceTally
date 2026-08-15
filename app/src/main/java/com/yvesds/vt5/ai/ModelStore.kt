package com.yvesds.vt5.ai

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * ModelStore - manages SAF storage for AI models under VT5/AI-models
 */
class ModelStore(private val context: Context) {
    private val TAG = "ModelStore"
    private val saf = SaFStorageHelper(context)

    fun ensureModelDir(): Boolean {
        try {
            val rootUri = saf.getRootUri() ?: return false
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return false
            
            // 1. Zorg dat de VT5 map zelf bestaat
            val vt5 = saf.findOrCreateDirectory(rootDoc, "VT5") ?: return false
            
            // 2. Zorg dat de AI-models map bestaat
            val aiDir = saf.findOrCreateDirectory(vt5, "AI-models") ?: return false

            // 3. Zorg dat alle submappen bestaan
            val subfolders = listOf("training_exports", "models", "feedback")
            for (name in subfolders) {
                if (saf.findOrCreateDirectory(aiDir, name) == null) {
                    Log.e(TAG, "Kon submap $name niet aanmaken in AI-models")
                    return false
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "ensureModelDir gefaald: ${e.message}")
            return false
        }
    }

    fun getModelDir(): DocumentFile? {
        val vt5 = saf.getVt5DirIfExists() ?: return null
        val aiDir = vt5.findFile("AI-models")?.takeIf { it.isDirectory } ?: return null
        return saf.findOrCreateDirectory(aiDir, "models")
    }

    fun getTrainingExportDir(): DocumentFile? {
        val vt5 = saf.getVt5DirIfExists() ?: return null
        val aiDir = vt5.findFile("AI-models")?.takeIf { it.isDirectory } ?: return null
        return saf.findOrCreateDirectory(aiDir, "training_exports")
    }

    /**
     * Slaat de 'ervaringen' van de AI op naar een JSON bestand in SAF.
     */
    fun saveNeuralEngine(engine: LiteNeuralEngine): Boolean {
        return try {
            val json = Json.encodeToString(engine)
            saveFileToModelDir("personal_lite_model.json", json.toByteArray())
        } catch (e: Exception) {
            Log.e(TAG, "Kon model niet opslaan: ${e.message}")
            false
        }
    }

    /**
     * Laadt het opgeslagen model in, of maakt een nieuwe als het bestand niet bestaat.
     */
    fun loadNeuralEngine(numSpecies: Int): LiteNeuralEngine {
        return try {
            val dir = getModelDir() ?: return LiteNeuralEngine(outputSize = numSpecies)
            val file = dir.findFile("personal_lite_model.json") ?: return LiteNeuralEngine(outputSize = numSpecies)
            
            val jsonStr = context.contentResolver.openInputStream(file.uri)?.use { 
                it.bufferedReader().readText() 
            } ?: ""
            
            if (jsonStr.isBlank()) LiteNeuralEngine(outputSize = numSpecies)
            else Json.decodeFromString<LiteNeuralEngine>(jsonStr)
        } catch (e: Exception) {
            Log.w(TAG, "Kon model niet inladen, start vers: ${e.message}")
            LiteNeuralEngine(outputSize = numSpecies)
        }
    }

    fun saveFileToModelDir(name: String, bytes: ByteArray): Boolean {
        val ai = getModelDir() ?: return false
        val existing = ai.findFile(name)
        try {
            val mimeType = when {
                name.endsWith(".tflite") -> "application/octet-stream"
                name.endsWith(".json") -> "application/json"
                else -> "text/plain"
            }
            val file = existing ?: ai.createFile(mimeType, name) ?: return false
            context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(bytes) }
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save file $name: ${e.message}")
            return false
        }
    }
}

