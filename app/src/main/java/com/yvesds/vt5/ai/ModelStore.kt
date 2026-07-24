package com.yvesds.vt5.ai

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.yvesds.vt5.core.opslag.SaFStorageHelper

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
            val subfolders = listOf("training_exports", "models", "feedback", "weather_archive")
            for (name in subfolders) {
                if (saf.findOrCreateDirectory(aiDir, name) == null) {
                    Log.e(TAG, "Kon submap $name niet aanmaken in AI-models")
                    return false
                }
            }

            // 4. Kopieer base_model.tflite als er nog niets in 'models' staat
            val modelsDir = saf.findOrCreateDirectory(aiDir, "models")
            if (modelsDir != null && modelsDir.findFile("base_model.tflite") == null) {
                copyBaseModelToModelsDir(modelsDir)
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "ensureModelDir gefaald: ${e.message}")
            return false
        }
    }

    private fun copyBaseModelToModelsDir(modelsDir: DocumentFile) {
        try {
            context.assets.open("ai/base_model.tflite").use { input ->
                val file = modelsDir.createFile("application/octet-stream", "base_model.tflite")
                if (file != null) {
                    context.contentResolver.openOutputStream(file.uri)?.use { output ->
                        input.copyTo(output)
                    }
                    Log.i(TAG, "base_model.tflite gekopieerd naar SAF models map")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Kon base_model.tflite niet naar SAF kopiëren: ${e.message}")
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

