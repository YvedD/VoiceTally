package com.yvesds.vt5.core.database.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
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
import com.yvesds.vt5.core.database.entities.Waarneming
import com.yvesds.vt5.core.opslag.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DatabaseRecordDetailActiviteit : AppCompatActivity() {

    private lateinit var database: VoiceTallyDatabase
    private lateinit var fileLogger: FileLogger
    private lateinit var containerVelden: LinearLayout
    private var currentRecordId: String? = null
    private var currentTellingId: String? = null
    private var currentRecord: Waarneming? = null
    
    private var selectedSoortId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_db_record_detail)

        database = VoiceTallyDatabase.getDatabase(this)
        fileLogger = FileLogger(this)
        containerVelden = findViewById(R.id.containerVelden)
        currentRecordId = intent.getStringExtra("recordid")
        currentTellingId = intent.getStringExtra("tellingid")

        findViewById<MaterialButton>(R.id.btnTerug).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnOpslaan).setOnClickListener { saveChanges() }

        loadData()
    }

    private fun loadData() {
        val rid = currentRecordId ?: return
        val tid = currentTellingId ?: return
        lifecycleScope.launch {
            val record = database.tellingDao().getWaarnemingById(rid, tid) ?: return@launch
            currentRecord = record
            selectedSoortId = record.soortid
            renderFields(record)
        }
    }

    private suspend fun renderFields(record: Waarneming) {
        containerVelden.removeAllViews()
        
        // 1. Soort (Naam-gebaseerd)
        addSoortSelector(record.soortid)

        // 2. Andere velden (uitgebreide handmatige mapping voor alle relevante velden)
        addEditField("idLocal", record.idLocal, enabled = false)
        addEditField("tellingid", record.tellingid, enabled = false)
        addEditField("aantal", record.aantal)
        addEditField("aantalterug", record.aantalterug)
        addEditField("tijdstip", record.tijdstip)
        addEditField("opmerkingen", record.opmerkingen)
        addEditField("geslacht", record.geslacht)
        addEditField("leeftijd", record.leeftijd)
        addEditField("kleed", record.kleed)
        addEditField("richting", record.richting)
        addEditField("richtingterug", record.richtingterug)
        addEditField("sightingdirection", record.sightingdirection)
        addEditField("lokaal", record.lokaal)
        addEditField("aantal_plus", record.aantal_plus)
        addEditField("aantalterug_plus", record.aantalterug_plus)
        addEditField("lokaal_plus", record.lokaal_plus)
        addEditField("markeren", record.markeren)
        addEditField("markerenlokaal", record.markerenlokaal)
        addEditField("trektype", record.trektype)
        addEditField("teltype", record.teltype)
        addEditField("location", record.location)
        addEditField("height", record.height)
        addEditField("groupid", record.groupid)
        addEditField("uploadtijdstip", record.uploadtijdstip)
        addEditField("totaalaantal", record.totaalaantal)
    }

    private suspend fun addSoortSelector(currentSoortId: String) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_db_veld_edit, containerVelden, false)
        view.findViewById<TextView>(R.id.tvLabel).text = "Soort (Zoek op naam)"
        
        val etValue = view.findViewById<EditText>(R.id.etValue)
        etValue.visibility = View.GONE // Verberg standaard EditText

        val atv = AutoCompleteTextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setTextColor(ContextCompat.getColor(this@DatabaseRecordDetailActiviteit, R.color.vt5_on_surface))
            setHintTextColor(ContextCompat.getColor(this@DatabaseRecordDetailActiviteit, R.color.vt5_on_surface))
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this@DatabaseRecordDetailActiviteit, R.color.vt5_on_surface)
            )
            hint = "Zoek soortnaam..."
        }

        val allSpecies = SpeciesNameResolver.getAllSpecies(this)
        val speciesNames = allSpecies.map { it.soortnaam }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, speciesNames)
        atv.setAdapter(adapter)

        val currentName = SpeciesNameResolver.getName(this, currentSoortId)
        atv.setText(currentName)

        atv.setOnItemClickListener { _, _, position, _ ->
            val selectedName = adapter.getItem(position)
            selectedSoortId = allSpecies.find { it.soortnaam == selectedName }?.soortid
        }

        (view as LinearLayout).addView(atv)
        containerVelden.addView(view)
    }

    private fun addEditField(label: String, value: String, enabled: Boolean = true) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_db_veld_edit, containerVelden, false)
        
        val displayLabel = if (label.contains("tijd", ignoreCase = true) || label == "tijdstip" || label == "uploadtijdstip") {
            "$label (${SpeciesNameResolver.formatTimestamp(value)})"
        } else {
            label
        }
        
        val tvLabel = view.findViewById<TextView>(R.id.tvLabel)
        tvLabel.text = displayLabel
        tvLabel.tag = label // Originele naam bewaren

        val et = view.findViewById<EditText>(R.id.etValue)
        et.setText(value)
        et.isEnabled = enabled
        if (!enabled) et.alpha = 0.5f
        containerVelden.addView(view)
    }

    private fun saveChanges() {
        val record = currentRecord ?: return
        val updatedMap = mutableMapOf<String, String>()
        
        for (i in 0 until containerVelden.childCount) {
            val row = containerVelden.getChildAt(i)
            val labelView = row.findViewById<TextView>(R.id.tvLabel) ?: continue
            val label = labelView.tag?.toString() ?: labelView.text.toString()
            
            if (label != "Soort (Zoek op naam)") {
                val et = row.findViewById<EditText>(R.id.etValue)
                if (et != null && et.isEnabled) {
                    updatedMap[label] = et.text.toString()
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val updatedRecord = record.copy(
                    soortid = selectedSoortId ?: record.soortid,
                    aantal = updatedMap["aantal"] ?: record.aantal,
                    aantalterug = updatedMap["aantalterug"] ?: record.aantalterug,
                    tijdstip = updatedMap["tijdstip"] ?: record.tijdstip,
                    opmerkingen = updatedMap["opmerkingen"] ?: record.opmerkingen,
                    geslacht = updatedMap["geslacht"] ?: record.geslacht,
                    leeftijd = updatedMap["leeftijd"] ?: record.leeftijd,
                    kleed = updatedMap["kleed"] ?: record.kleed,
                    richting = updatedMap["richting"] ?: record.richting,
                    lokaal = updatedMap["lokaal"] ?: record.lokaal,
                    location = updatedMap["location"] ?: record.location,
                    height = updatedMap["height"] ?: record.height
                )
                database.tellingDao().updateWaarneming(updatedRecord)
                
                val speciesName = SpeciesNameResolver.getName(this@DatabaseRecordDetailActiviteit, updatedRecord.soortid)
                fileLogger.info("GEBRUIKER: Bewerking record [${record.idLocal}] (Soort: $speciesName)")
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DatabaseRecordDetailActiviteit, "Opgeslagen", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DatabaseRecordDetailActiviteit, "Fout: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
