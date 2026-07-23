package com.yvesds.vt5.ai

import android.content.Context
import android.os.Environment
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Utility for loading a .tflite model and labels JSON from external storage.
 *
 * Behavior:
 *  - First tries to load from external folder: /storage/emulated/0/VT5/AI-models/training_exports
 *  - If not found, caller can fall back to bundled assets or another source.
 *
 * Note: reading from external storage requires runtime permission READ_EXTERNAL_STORAGE
 * (or use the Storage Access Framework to let user pick files without broad permission).
 */
object ModelManager {
    private const val TAG = "ModelManager"

    // Prefer these locations (in order). Many devices/apps place user documents under
    // /storage/emulated/0/Documents/..., but sometimes the archive is placed directly
    // under the storage root (e.g. /storage/emulated/0/VT5/...). We try both.
    private val EXPORT_DIRS = listOf(
        "VT5/AI-models/training_exports",
        "Documents/VT5/AI-models/training_exports"
    )
    private const val MODEL_FILENAME = "training_model.tflite"
    private const val LABELS_FILENAME = "training_model.labels.json"

    data class LoadedModel(val interpreter: Interpreter, val labels: List<String>)

    // In-memory cached loaded model (if any). Other parts of the app can query
    // ModelManager.getLoadedModel() to check whether a model is available.
    @Volatile
    private var loadedModel: LoadedModel? = null

    /**
     * Try to find the external directory where you placed the files.
     */
    fun findExternalExportDir(): File? {
        // Try candidate locations on primary external storage
        val root = Environment.getExternalStorageDirectory()
        for (rel in EXPORT_DIRS) {
            val dir = File(root, rel)
            Log.d(TAG, "checking external path: ${'$'}{dir.absolutePath}")
            if (dir.exists() && dir.isDirectory) {
                Log.d(TAG, "found model dir: ${'$'}{dir.absolutePath}")
                return dir
            }
        }

        // Also try the public Documents directory if available
        try {
            val docsRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val dir = File(docsRoot, "VT5/AI-models/training_exports")
            Log.d(TAG, "checking public Documents path: ${'$'}{dir.absolutePath}")
            if (dir.exists() && dir.isDirectory) {
                Log.d(TAG, "found model dir in Documents: ${'$'}{dir.absolutePath}")
                return dir
            }
        } catch (_: Exception) {
            // ignore
        }

        return null
    }

    /**
     * Return the expected model file if present, or null.
     */
    fun findModelFile(): File? {
        val dir = findExternalExportDir() ?: return null
        val f = File(dir, MODEL_FILENAME)
        return if (f.exists() && f.isFile) f else null
    }

    fun findLabelsFile(): File? {
        val dir = findExternalExportDir() ?: return null
        val f = File(dir, LABELS_FILENAME)
        return if (f.exists() && f.isFile) f else null
    }

    @Throws(IOException::class)
    private fun loadModelFile(file: File): MappedByteBuffer {
        FileInputStream(file).channel.use { fc ->
            return fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size())
        }
    }

    @Throws(IOException::class)
    private fun loadLabels(file: File): List<String> {
        val text = file.readText(Charsets.UTF_8)
        val obj = JSONObject(text)
        val arr = obj.optJSONArray("classes") ?: return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            list.add(arr.optString(i))
        }
        return list
    }

    /**
     * Try to load the model and labels from the external folder. Returns null on failure.
     * Make sure you already have READ_EXTERNAL_STORAGE granted or use SAF.
     */
    fun loadFromExternal(context: Context): LoadedModel? {
        val modelFile = findModelFile() ?: return null
        val labelsFile = findLabelsFile() ?: return null
        return try {
            val bb = loadModelFile(modelFile)
            val interpreter = Interpreter(bb)
            val labels = loadLabels(labelsFile)
            LoadedModel(interpreter, labels)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Attempt to load the model from external storage and cache it in-memory.
     * Returns null on success, or a short error message on failure.
     */
    fun loadAndSet(context: Context): String? {
        try {
            val foundModel = loadFromExternal(context)
            if (foundModel == null) {
                // Provide helpful message for the user
                val dir = findExternalExportDir()
                val baseMsg = if (dir == null) {
                    "Modelmap niet gevonden. Plaats 'training_model.tflite' en 'training_model.labels.json' in Documents/VT5/AI-models/training_exports"
                } else {
                    "Model of labels niet gevonden in ${dir.absolutePath}. Controleer bestandsnamen en JSON-format"
                }
                return baseMsg
            }
            loadedModel = foundModel
            android.util.Log.i(TAG, "Model geladen en in geheugen gezet")
            return null
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Model load failed: ${e.message}", e)
            return "Fout bij laden model: ${e.message ?: "onbekend"}"
        }
    }

    fun getLoadedModel(): LoadedModel? = loadedModel

    fun clearLoadedModel() { loadedModel = null }
}
