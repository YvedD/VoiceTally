package com.yvesds.vt5.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity voor een "telling" (telsessie).
 * Bevat alle metadata die nodig is voor de server-API (Trektellen).
 */
@Entity(tableName = "tellings")
data class TellingEntity(
    @PrimaryKey val id: String,
    val telpostId: String,
    val begintijd: Long,
    val eindtijd: Long,
    val createdAt: Long,
    val isUploaded: Boolean = false,
    val uploadedAt: Long? = null,
    
    // Metadata velden (strikt als String voor server compatibiliteit)
    val externid: String = "",
    val timezoneid: String = "Europe/Brussels",
    val bron: String = "4",
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
    val onlineid: String = "",
    val hydro: String = "",
    val hpa: String = "",
    val equipment: String = "",
    val uuid: String = "",
    val uploadtijdstip: String = "",
    val nrec: String = "0",
    val nsoort: String = "0"
)

/**
 * Room entity voor een individuele waarneming (soort + aantal).
 * Gekoppeld aan een TellingEntity via tellingId.
 */
@Entity(
    tableName = "observations",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = TellingEntity::class,
            parentColumns = ["id"],
            childColumns = ["tellingId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [
        androidx.room.Index("tellingId"),
        androidx.room.Index("speciesId"),
        androidx.room.Index("timestamp")
    ]
)
data class ObservationEntity(
    @PrimaryKey val id: String,
    val tellingId: String,
    val speciesId: String,
    val count: String = "0",
    val direction: String? = null,
    val timestamp: Long, // Interne timestamp voor sortering
    val countReturn: String = "0",
    val directionReturn: String? = null,
    val sightingDirection: String? = null,
    val local: String = "0",
    val countPlus: String = "0",
    val countReturnPlus: String = "0",
    val localPlus: String = "0",
    val mark: String = "0",
    val markLocal: String = "0",
    val geslacht: String? = null,
    val leeftijd: String? = null,
    val kleed: String? = null,
    val notes: String? = null,
    val trektype: String? = null,
    val teltype: String? = null,
    val location: String? = null,
    val height: String? = null,
    val groupId: String? = null,
    val uploadTimestamp: String? = null,
    val totalCount: String = "0"
)
