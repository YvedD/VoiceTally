package com.yvesds.vt5.core.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Waarneming: Bevat de individuele records inclusief alle annotaties.
 * Alle velden zijn Strings voor server-compatibiliteit.
 */
@Entity(
    tableName = "waarnemingen",
    foreignKeys = [
        ForeignKey(
            entity = TellingHeader::class,
            parentColumns = ["tellingid"],
            childColumns = ["tellingid"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tellingid"]), Index(value = ["soortid"])]
)
data class Waarneming(
    @PrimaryKey val idLocal: String,
    val tellingid: String,
    val soortid: String = "",
    val aantal: String = "0",
    val richting: String = "",
    val aantalterug: String = "0",
    val richtingterug: String = "",
    val sightingdirection: String = "",
    val lokaal: String = "0",
    val aantal_plus: String = "0",
    val aantalterug_plus: String = "0",
    val lokaal_plus: String = "0",
    val markeren: String = "0",
    val markerenlokaal: String = "0",
    val geslacht: String = "",
    val leeftijd: String = "",
    val kleed: String = "",
    val opmerkingen: String = "",
    val trektype: String = "",
    val teltype: String = "",
    val location: String = "",
    val height: String = "",
    val tijdstip: String = "",
    val groupid: String = "",
    val uploadtijdstip: String = "",
    val totaalaantal: String = "0"
)
