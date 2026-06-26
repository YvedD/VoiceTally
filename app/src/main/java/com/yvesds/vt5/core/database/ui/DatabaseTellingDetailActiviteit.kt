package com.yvesds.vt5.core.database.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.entities.TellingHeader
import com.yvesds.vt5.core.database.entities.Waarneming
import com.yvesds.vt5.core.opslag.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DatabaseTellingDetailActiviteit : AppCompatActivity() {

    private lateinit var database: VoiceTallyDatabase
    private lateinit var fileLogger: FileLogger
    private lateinit var containerMetadata: LinearLayout
    private lateinit var containerRecords: LinearLayout
    private var currentTellingId: String? = null
    private var currentHeader: TellingHeader? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_db_telling_detail)

        database = VoiceTallyDatabase.getDatabase(this)
        fileLogger = FileLogger(this)
        containerMetadata = findViewById(R.id.containerMetadata)
        containerRecords = findViewById(R.id.containerRecords)
        currentTellingId = intent.getStringExtra("tellingid")

        findViewById<MaterialButton>(R.id.btnTerug).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnOpslaan).setOnClickListener { saveChanges() }

        loadData()
    }

    private fun loadData() {
        val id = currentTellingId ?: return
        lifecycleScope.launch {
            val header = database.tellingDao().getHeader(id) ?: return@launch
            currentHeader = header
            renderMetadata(header)
            
            val records = database.tellingDao().getWaarnemingenList(id)
            renderRecords(records)
        }
    }

    private fun renderMetadata(header: TellingHeader) {
        containerMetadata.removeAllViews()
        // Alle relevante velden voor metadata bewerking
        addEditField("onlineid", header.onlineid)
        addEditField("telpostid", header.telpostid)
        addEditField("begintijd", header.begintijd)
        addEditField("eindtijd", header.eindtijd)
        addEditField("opmerkingen", header.opmerkingen)
        addEditField("status", header.status)
        addEditField("nrec", header.nrec)
        addEditField("nsoort", header.nsoort)
        addEditField("tellers", header.tellers)
        addEditField("weer", header.weer)
        addEditField("windrichting", header.windrichting)
        addEditField("windkracht", header.windkracht)
        addEditField("temperatuur", header.temperatuur)
        addEditField("bewolking", header.bewolking)
        addEditField("zicht", header.zicht)
        addEditField("typetelling", header.typetelling)
        addEditField("uuid", header.uuid, enabled = false)
        addEditField("tellingid", header.tellingid, enabled = false)
    }

    private fun addEditField(label: String, value: String, enabled: Boolean = true) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_db_veld_edit, containerMetadata, false)
        view.findViewById<TextView>(R.id.tvLabel).text = label
        val et = view.findViewById<EditText>(R.id.etValue)
        et.setText(value)
        et.isEnabled = enabled
        if (!enabled) et.alpha = 0.5f
        containerMetadata.addView(view)
    }

    private suspend fun renderRecords(records: List<Waarneming>) {
        containerRecords.removeAllViews()
        val textColor = ContextCompat.getColor(this, R.color.vt5_on_surface)
        
        for (record in records) {
            val view = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_2, containerRecords, false)
            val soortNaam = SpeciesNameResolver.getName(this, record.soortid)
            
            view.findViewById<TextView>(android.R.id.text1).apply {
                text = "$soortNaam (${record.aantal} ex)"
                setTextColor(textColor)
            }
            view.findViewById<TextView>(android.R.id.text2).apply {
                text = "ID: ${record.idLocal} | Tijd: ${record.tijdstip}"
                setTextColor(textColor)
                alpha = 0.7f
            }
            view.setOnClickListener {
                val intent = Intent(this@DatabaseTellingDetailActiviteit, DatabaseRecordDetailActiviteit::class.java)
                intent.putExtra("recordid", record.idLocal)
                startActivity(intent)
            }
            containerRecords.addView(view)
        }
    }

    private fun saveChanges() {
        val header = currentHeader ?: return
        val updatedMap = mutableMapOf<String, String>()
        for (i in 0 until containerMetadata.childCount) {
            val row = containerMetadata.getChildAt(i)
            val label = row.findViewById<TextView>(R.id.tvLabel).text.toString()
            val value = row.findViewById<EditText>(R.id.etValue).text.toString()
            updatedMap[label] = value
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val updatedHeader = header.copy(
                    onlineid = updatedMap["onlineid"] ?: header.onlineid,
                    telpostid = updatedMap["telpostid"] ?: header.telpostid,
                    begintijd = updatedMap["begintijd"] ?: header.begintijd,
                    eindtijd = updatedMap["eindtijd"] ?: header.eindtijd,
                    opmerkingen = updatedMap["opmerkingen"] ?: header.opmerkingen,
                    status = updatedMap["status"] ?: header.status,
                    nrec = updatedMap["nrec"] ?: header.nrec,
                    nsoort = updatedMap["nsoort"] ?: header.nsoort,
                    tellers = updatedMap["tellers"] ?: header.tellers,
                    weer = updatedMap["weer"] ?: header.weer
                )
                database.tellingDao().updateHeader(updatedHeader)
                fileLogger.info("GEBRUIKER: Metadata van telling [${header.tellingid}] aangepast")
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DatabaseTellingDetailActiviteit, "Opgeslagen", Toast.LENGTH_SHORT).show()
                    loadData()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DatabaseTellingDetailActiviteit, "Fout: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }
}
