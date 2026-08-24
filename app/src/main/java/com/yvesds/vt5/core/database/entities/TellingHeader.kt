package com.yvesds.vt5.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * TellingHeader: Bevat de metadata van een telsessie.
 * Bevat de gevraagde velden plus noodzakelijke velden voor app-functionaliteit.
 */
@Entity(
    tableName = "telling_headers",
    indices = [
        Index(value = ["windrichting"]), 
        Index(value = ["begintijd"]), 
        Index(value = ["onlineid"]),
        Index(value = ["telpostid"]) // NU TOEGEVOEGD
    ]
)
data class TellingHeader(
    @PrimaryKey val tellingid: String, // Numeriek volgnummer (1, 2, 3...)
    val onlineid: String = "",        // id uit Excel / server
    val externid: String = "VT5",
    val bron: String = "4",
    val telpostid: String = "",
    val begintijd: String = "",       // Epoch seconden
    val eindtijd: String = "",         // Epoch seconden
    val tellers: String = "",
    val weer: String = "",
    val windrichting: String = "",
    val windkracht: String = "",
    val temperatuur: String = "",
    val bewolking: String = "",
    val bewolkinghoogte: String = "",
    val neerslag: String = "",
    val duurneerslag: String = "",
    val zicht: String = "",
    val tellersactief: String = "",
    val tellersaanwezig: String = "",
    val typetelling: String = "all",
    val metersnet: String = "",
    val geluid: String = "",
    val opmerkingen: String = "",
    val hydro: String = "",
    val hpa: String = "",
    val equipment: String = "",
    val uuid: String = "",             // Voor server-synchronisatie
    val uploadtijdstip: String = "",
    val nrec: String = "0",           // Berekend: som van records
    val nsoort: String = "0",         // Berekend: totaal unieke soorten
    
    val timezoneid: String = "Europe/Brussels",
    val status: String = "actief"     // actief, geupload, gearchiveerd
)
