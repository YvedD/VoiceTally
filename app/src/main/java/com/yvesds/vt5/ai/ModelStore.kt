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
        val vt5 = saf.getVt5DirIfExists() ?: return false
        val aiDir = saf.findOrCreateDirectory(vt5, "AI-models") ?: return false

        // Ensure common subfolders exist
        val subfolders = listOf("training_exports", "models", "feedback")
        for (name in subfolders) {
            if (saf.findOrCreateDirectory(aiDir, name) == null) return false
        }
        return true
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
            val file = existing ?: ai.createFile("text/plain", name) ?: return false
            context.contentResolver.openOutputStream(file.uri)?.use { it.write(bytes) }
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save file $name: ${e.message}")
            return false
        }
    }
}

