package com.yvesds.vt5.ai

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

/**
 * ModelStore - Beheert SAF opslag voor AI modellen.
 * Ondersteunt Dual-Storage (JSON + Binair) met automatische synchronisatie.
 */
class ModelStore(private val context: Context) {
    private val TAG = "ModelStore"
    private val saf = SaFStorageHelper(context)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun ensureModelDir(): Boolean {
        try {
            val rootUri = saf.getRootUri() ?: return false
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return false
            val vt5 = saf.findOrCreateDirectory(rootDoc, "VT5") ?: return false
            val aiDir = saf.findOrCreateDirectory(vt5, "AI-models") ?: return false
            val subfolders = listOf("training_exports", "models", "feedback")
            for (name in subfolders) {
                if (saf.findOrCreateDirectory(aiDir, name) == null) return false
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
     * Slaat de Neurale Motor op in beide formaten.
     */
    fun saveNeuralEngine(engine: LiteNeuralEngine): Boolean {
        return try {
            val jsonStr = json.encodeToString(engine)
            saveFileToModelDir("personal_lite_model.json", jsonStr.toByteArray())
            val binaryData = ProtoBuf.encodeToByteArray(engine)
            saveFileToModelDir("personal_lite_model.bin", binaryData)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Kon model niet opslaan: ${e.message}")
            false
        }
    }

    /**
     * Laadt de Neurale Motor met Sync-check.
     */
    fun loadNeuralEngine(numSpecies: Int): LiteNeuralEngine {
        val dir = getModelDir() ?: return LiteNeuralEngine(outputSize = numSpecies)
        val jsonFile = dir.findFile("personal_lite_model.json")
        val binFile = dir.findFile("personal_lite_model.bin")

        try {
            if (jsonFile != null && (binFile == null || jsonFile.lastModified() > binFile.lastModified())) {
                val jsonStr = context.contentResolver.openInputStream(jsonFile.uri)?.use { it.bufferedReader().readText() }
                if (!jsonStr.isNullOrBlank()) {
                    val engine = json.decodeFromString<LiteNeuralEngine>(jsonStr)
                    saveNeuralEngine(engine)
                    return engine
                }
            }
            if (binFile != null) {
                val bytes = context.contentResolver.openInputStream(binFile.uri)?.use { it.readBytes() }
                if (bytes != null) return ProtoBuf.decodeFromByteArray<LiteNeuralEngine>(bytes)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Load Neural failed: ${e.message}")
        }
        return LiteNeuralEngine(outputSize = numSpecies)
    }

    /**
     * Slaat de Expert Knowledge Base op in beide formaten.
     */
    fun saveExpertKnowledge(kb: ExpertKnowledgeBase): Boolean {
        return try {
            saveFileToModelDir("expert_knowledge.json", json.encodeToString(kb).toByteArray())
            saveFileToModelDir("expert_knowledge.bin", ProtoBuf.encodeToByteArray(kb))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Kon ExpertKB niet opslaan: ${e.message}")
            false
        }
    }

    /**
     * Laadt de Expert Knowledge Base met Sync-check.
     */
    fun loadExpertKnowledge(): ExpertKnowledgeBase? {
        val dir = getModelDir() ?: return null
        val jsonFile = dir.findFile("expert_knowledge.json")
        val binFile = dir.findFile("expert_knowledge.bin")

        return try {
            if (jsonFile != null && (binFile == null || jsonFile.lastModified() > jsonFile.lastModified())) {
                val jsonStr = context.contentResolver.openInputStream(jsonFile.uri)?.use { it.bufferedReader().readText() }
                if (!jsonStr.isNullOrBlank()) {
                    val kb = json.decodeFromString<ExpertKnowledgeBase>(jsonStr)
                    saveExpertKnowledge(kb)
                    return kb
                }
            }
            if (binFile != null) {
                val bytes = context.contentResolver.openInputStream(binFile.uri)?.use { it.readBytes() }
                if (bytes != null) return ProtoBuf.decodeFromByteArray<ExpertKnowledgeBase>(bytes)
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Load ExpertKB failed: ${e.message}")
            null
        }
    }

    fun saveFileToModelDir(name: String, bytes: ByteArray): Boolean {
        val ai = getModelDir() ?: return false
        val existing = ai.findFile(name)
        return try {
            val mimeType = if (name.endsWith(".json")) "application/json" else "application/octet-stream"
            val file = existing ?: ai.createFile(mimeType, name) ?: return false
            context.contentResolver.openOutputStream(file.uri, "wt")?.use { out ->
                out.write(bytes)
                out.flush()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save file $name: ${e.message}")
            false
        }
    }
}
