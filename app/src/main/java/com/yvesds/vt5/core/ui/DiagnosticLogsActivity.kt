package com.yvesds.vt5.core.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.yvesds.vt5.R
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * DiagnosticLogsActivity: Toont en deelt alle opgeslagen systeem- en errorlogboeken,
 * inclusief zowel publieke SAF-bestanden als interne prive-sandbox bestanden.
 */
class DiagnosticLogsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnRefresh: ImageButton
    private lateinit var spinnerLogFiles: Spinner
    private lateinit var tvLogFilePath: TextView
    private lateinit var tvLogContent: TextView
    private lateinit var btnShareLog: MaterialButton

    private val logItems = mutableListOf<LogFileItem>()
    private var selectedItem: LogFileItem? = null

    data class LogFileItem(
        val displayName: String,
        val fullPathOrUri: String,
        val isSaf: Boolean,
        val internalFile: File? = null,
        val documentFile: DocumentFile? = null
    ) {
        override fun toString(): String = displayName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostic_logs)

        btnBack = findViewById(R.id.btnBack)
        btnRefresh = findViewById(R.id.btnRefresh)
        spinnerLogFiles = findViewById(R.id.spinnerLogFiles)
        tvLogFilePath = findViewById(R.id.tvLogFilePath)
        tvLogContent = findViewById(R.id.tvLogContent)
        btnShareLog = findViewById(R.id.btnShareLog)

        btnBack.setOnClickListener { finish() }
        btnRefresh.setOnClickListener { loadLogFiles() }
        btnShareLog.setOnClickListener { shareCurrentLogFile() }

        spinnerLogFiles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in logItems.indices) {
                    selectedItem = logItems[position]
                    displayLogFileContent(logItems[position])
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedItem = null
            }
        }

        loadLogFiles()
    }

    private fun loadLogFiles() {
        lifecycleScope.launch(Dispatchers.IO) {
            val items = mutableListOf<LogFileItem>()
            val saf = SaFStorageHelper(this@DiagnosticLogsActivity)

            // 1. Scan SAF directories
            runCatching {
                val vt5Dir = saf.getVt5DirIfExists()
                if (vt5Dir != null) {
                    saf.findOrCreateDirectory(vt5Dir, "logs")?.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            items.add(LogFileItem("[SAF Log] ${file.name}", file.uri.toString(), isSaf = true, documentFile = file))
                        }
                    }
                    saf.findOrCreateDirectory(vt5Dir, "errorlogs")?.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            items.add(LogFileItem("[SAF Error] ${file.name}", file.uri.toString(), isSaf = true, documentFile = file))
                        }
                    }
                }
            }.onFailure { ex ->
                Log.w(TAG, "Fout bij scannen van SAF logbestanden: ${ex.message}")
            }

            // 2. Scan Internal private files sandbox
            runCatching {
                val root = File(filesDir, "VT5")
                File(root, "logs").listFiles()?.forEach { file ->
                    if (file.isFile) {
                        items.add(LogFileItem("[Internal Log] ${file.name}", file.absolutePath, isSaf = false, internalFile = file))
                    }
                }
                File(root, "errorlogs").listFiles()?.forEach { file ->
                    if (file.isFile) {
                        items.add(LogFileItem("[Internal Error] ${file.name}", file.absolutePath, isSaf = false, internalFile = file))
                    }
                }
            }.onFailure { ex ->
                Log.w(TAG, "Fout bij scannen van interne logbestanden: ${ex.message}")
            }

            withContext(Dispatchers.Main) {
                logItems.clear()
                logItems.addAll(items)

                if (items.isEmpty()) {
                    tvLogFilePath.text = "Geen logbestanden gevonden."
                    tvLogContent.text = "Er zijn op dit moment geen logbestanden aanwezig op het toestel."
                    btnShareLog.isEnabled = false
                    spinnerLogFiles.adapter = ArrayAdapter(this@DiagnosticLogsActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Geen logs beschikbaar"))
                } else {
                    btnShareLog.isEnabled = true
                    val adapter = ArrayAdapter(this@DiagnosticLogsActivity, android.R.layout.simple_spinner_dropdown_item, logItems)
                    spinnerLogFiles.adapter = adapter
                    spinnerLogFiles.setSelection(0)
                }
            }
        }
    }

    private fun displayLogFileContent(item: LogFileItem) {
        tvLogFilePath.text = "Locatie: ${item.fullPathOrUri}"
        lifecycleScope.launch(Dispatchers.IO) {
            val content = runCatching {
                if (item.isSaf && item.documentFile != null) {
                    contentResolver.openInputStream(item.documentFile.uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    } ?: "Kon SAF logbestand niet openen."
                } else if (item.internalFile != null && item.internalFile.exists()) {
                    item.internalFile.readText()
                } else {
                    "Logbestand niet leesbaar."
                }
            }.getOrElse { ex ->
                "Fout bij lezen logbestand: ${ex.message}"
            }

            withContext(Dispatchers.Main) {
                tvLogContent.text = content.ifBlank { "(Logbestand is leeg)" }
            }
        }
    }

    private fun shareCurrentLogFile() {
        val item = selectedItem ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val shareUri: Uri = if (item.isSaf && item.documentFile != null) {
                    // Kopieer SAF bestand naar cache om via FileProvider veilig te delen
                    val cacheFile = File(cacheDir, item.documentFile.name ?: "vt5_log.txt")
                    contentResolver.openInputStream(item.documentFile.uri)?.use { input ->
                        cacheFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    FileProvider.getUriForFile(this@DiagnosticLogsActivity, "$packageName.fileprovider", cacheFile)
                } else if (item.internalFile != null) {
                    FileProvider.getUriForFile(this@DiagnosticLogsActivity, "$packageName.fileprovider", item.internalFile)
                } else {
                    return@launch
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    putExtra(Intent.EXTRA_SUBJECT, "VoiceTally VT5 Logbestand: ${item.displayName}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    startActivity(Intent.createChooser(shareIntent, "Exporteer / Delen Logbestand via"))
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Delen van logbestand mislukt: ${ex.message}", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DiagnosticLogsActivity, "Fout bij delen: ${ex.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        private const val TAG = "DiagnosticLogsActivity"
    }
}
