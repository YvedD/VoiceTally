package com.yvesds.vt5.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * TellingHeader: Bevat de metadata van een telsessie.
 * Alle velden zijn Strings voor server-compatibiliteit.
 */
@Entity(tableName = "telling_headers")
data class TellingHeader(
    @PrimaryKey val tellingid: String,
    val onlineid: String = "",
    val externid: String = "",
    val timezoneid: String = "Europe/Brussels",
    val bron: String = "4",
    val telpostid: String = "",
    val begintijd: String = "",
    val eindtijd: String = "",
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
    val typetelling: String = "",
    val metersnet: String = "",
    val geluid: String = "",
    val opmerkingen: String = "",
    val hydro: String = "",
    val hpa: String = "",
    val equipment: String = "",
    val uuid: String = "",
    val uploadtijdstip: String = "",
    val nrec: String = "0",
    val nsoort: String = "0",
    val status: String = "actief" // actief, geupload, gearchiveerd
)
