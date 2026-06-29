package com.yvesds.vt5.core.database.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.button.MaterialButton
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.entities.TellingHeader
import com.yvesds.vt5.core.database.entities.Waarneming
import com.yvesds.vt5.core.database.toServerEnvelope
import com.yvesds.vt5.core.opslag.FileLogger
import com.yvesds.vt5.features.telling.TellingUploadCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class DatabaseTellingDetailActiviteit : AppCompatActivity() {

    private lateinit var database: VoiceTallyDatabase
    private lateinit var fileLogger: FileLogger
    private lateinit var containerMetadata: FlexboxLayout
    private lateinit var containerRecords: FlexboxLayout
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
        findViewById<MaterialButton>(R.id.btnUploadServer).setOnClickListener { uploadToServer() }

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
        addEditField("tellersactief", header.tellersactief)
        addEditField("tellersaanwezig", header.tellersaanwezig)
        addEditField("weer", header.weer)
        addEditField("windrichting", header.windrichting)
        addEditField("windkracht", header.windkracht)
        addEditField("temperatuur", header.temperatuur)
        addEditField("bewolking", header.bewolking)
        addEditField("bewolkinghoogte", header.bewolkinghoogte)
        addEditField("neerslag", header.neerslag)
        addEditField("duurneerslag", header.duurneerslag)
        addEditField("zicht", header.zicht)
        addEditField("typetelling", header.typetelling)
        addEditField("metersnet", header.metersnet)
        addEditField("geluid", header.geluid)
        addEditField("hydro", header.hydro)
        addEditField("hpa", header.hpa)
        addEditField("equipment", header.equipment)
        addEditField("uuid", header.uuid, enabled = false, fullWidth = true)
        addEditField("tellingid", header.tellingid, enabled = false)
    }

    private fun addEditField(label: String, value: String, enabled: Boolean = true, fullWidth: Boolean = false) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_db_veld_edit, containerMetadata, false)
        
        val displayLabel = if (label.contains("tijd", ignoreCase = true) || label == "tijdstip" || label == "uploadtijdstip") {
            "$label (${SpeciesNameResolver.formatTimestamp(value)})"
        } else {
            label
        }
        
        val tvLabel = view.findViewById<TextView>(R.id.tvLabel)
        tvLabel.text = displayLabel
        tvLabel.tag = label // Bewaar de originele labelnaam voor saveChanges

        val et = view.findViewById<EditText>(R.id.etValue)
        et.setText(value)
        et.isEnabled = enabled
        if (!enabled) et.alpha = 0.5f
        
        val params = view.layoutParams as FlexboxLayout.LayoutParams
        params.flexBasisPercent = if (fullWidth) 1.0f else 0.48f
        params.flexGrow = 1.0f
        view.layoutParams = params
        
        containerMetadata.addView(view)
    }

    private suspend fun renderRecords(records: List<Waarneming>) {
        containerRecords.removeAllViews()
        
        if (records.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "Geen records gevonden voor deze telling."
                setTextColor(ContextCompat.getColor(this@DatabaseTellingDetailActiviteit, R.color.vt5_light_gray))
                setPadding(16, 16, 16, 16)
            }
            containerRecords.addView(emptyView)
            return
        }

        for ((index, record) in records.withIndex()) {
            try {
                val view = LayoutInflater.from(this).inflate(R.layout.item_db_waarneming, containerRecords, false)
                val soortNaam = SpeciesNameResolver.getName(this, record.soortid)
                
                view.findViewById<TextView>(R.id.tvIndex).text = (index + 1).toString()
                view.findViewById<TextView>(R.id.tvSoortNaam).text = soortNaam
                
                val readableTime = SpeciesNameResolver.formatTimestamp(record.tijdstip)
                view.findViewById<TextView>(R.id.tvDetails).text = "Tijd: $readableTime"
                view.findViewById<TextView>(R.id.tvAantal).text = record.aantal
                
                view.setOnClickListener {
                    val intent = Intent(this@DatabaseTellingDetailActiviteit, DatabaseRecordDetailActiviteit::class.java)
                    intent.putExtra("recordid", record.idLocal)
                    intent.putExtra("tellingid", record.tellingid)
                    startActivity(intent)
                }
                
                val params = view.layoutParams as FlexboxLayout.LayoutParams
                params.flexBasisPercent = 0.48f
                params.flexGrow = 1.0f
                view.layoutParams = params

                containerRecords.addView(view)
            } catch (e: Exception) {
                Log.w("DetailRoom", "Fout bij renderen record $index: ${e.message}")
            }
        }
        containerRecords.requestLayout()
    }

    private fun saveChanges() {
        val header = currentHeader ?: return
        val updatedMap = mutableMapOf<String, String>()
        for (i in 0 until containerMetadata.childCount) {
            val row = containerMetadata.getChildAt(i)
            val tvLabel = row.findViewById<TextView>(R.id.tvLabel)
            val label = tvLabel.tag?.toString() ?: tvLabel.text.toString()
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
                    tellersactief = updatedMap["tellersactief"] ?: header.tellersactief,
                    tellersaanwezig = updatedMap["tellersaanwezig"] ?: header.tellersaanwezig,
                    weer = updatedMap["weer"] ?: header.weer,
                    windrichting = updatedMap["windrichting"] ?: header.windrichting,
                    windkracht = updatedMap["windkracht"] ?: header.windkracht,
                    temperatuur = updatedMap["temperatuur"] ?: header.temperatuur,
                    bewolking = updatedMap["bewolking"] ?: header.bewolking,
                    bewolkinghoogte = updatedMap["bewolkinghoogte"] ?: header.bewolkinghoogte,
                    neerslag = updatedMap["neerslag"] ?: header.neerslag,
                    duurneerslag = updatedMap["duurneerslag"] ?: header.duurneerslag,
                    zicht = updatedMap["zicht"] ?: header.zicht,
                    typetelling = updatedMap["typetelling"] ?: header.typetelling,
                    metersnet = updatedMap["metersnet"] ?: header.metersnet,
                    geluid = updatedMap["geluid"] ?: header.geluid,
                    hydro = updatedMap["hydro"] ?: header.hydro,
                    hpa = updatedMap["hpa"] ?: header.hpa,
                    equipment = updatedMap["equipment"] ?: header.equipment,
                    bron = "4" // Geforceerd op "4" voor Trektellen API consistentie
                )
                database.tellingDao().updateHeader(updatedHeader)
                fileLogger.info("GEBRUIKER: Metadata van telling [${header.tellingid}] volledig bijgewerkt in database")
                
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

    private fun uploadToServer() {
        val header = currentHeader ?: return
        val id = currentTellingId ?: return
        
        lifecycleScope.launch(Dispatchers.Main) {
            val btn = findViewById<MaterialButton>(R.id.btnUploadServer)
            btn.isEnabled = false
            btn.text = "Bezig met uploaden..."
            
            try {
                val records = withContext(Dispatchers.IO) {
                    database.tellingDao().getWaarnemingenList(id)
                }
                
                val envelope = header.toServerEnvelope(records)
                val uploadCore = TellingUploadCore(this@DatabaseTellingDetailActiviteit)
                
                // Voorbereiden voor upload (sanitisering + meta-updates)
                val finalEnvelope = uploadCore.prepareEnvelopeForUpload(
                    sourceEnvelope = envelope,
                    useStoredOnlineIdWhenBlank = false,
                    now = Date()
                )
                
                val result = withContext(Dispatchers.IO) {
                    uploadCore.uploadPrepared(
                        TellingUploadCore.UploadRequest(
                            mode = TellingUploadCore.Mode.EDITOR_UPLOAD,
                            preparedEnvelope = finalEnvelope,
                            persistReturnedOnlineId = true,
                            persistPreparedEnvelopeToPrefs = false,
                            markTellingSent = true
                        )
                    )
                }
                
                if (result.success) {
                    Toast.makeText(this@DatabaseTellingDetailActiviteit, "Sync succesvol: Data op server bijgewerkt", Toast.LENGTH_LONG).show()
                    fileLogger.info("DATABASE SYNC: Telling [${header.tellingid}] succesvol gepushed naar server")
                    loadData() // Refresh voor eventuele nieuwe onlineid
                } else {
                    Toast.makeText(this@DatabaseTellingDetailActiviteit, "Sync mislukt: ${result.errorMessage}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DatabaseTellingDetailActiviteit, "Fout bij sync: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                btn.isEnabled = true
                btn.text = "Sync: Alle wijzigingen naar Server pushen"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }
}
