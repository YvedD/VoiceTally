package com.yvesds.vt5.features.masterClient.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Message type discriminators ─────────────────────────────────────────────
const val MC_MSG_PAIRING_REQ  = "pairing_req"
const val MC_MSG_PAIRING_RESP = "pairing_resp"
const val MC_MSG_OBSERVATION  = "observation"
const val MC_MSG_ACK          = "ack"
const val MC_MSG_HEARTBEAT    = "heartbeat"
const val MC_MSG_EXPORT_REQ   = "export_req"
const val MC_MSG_EXPORT_DATA  = "export_data"

/** Client → master: gebruiker verlaat de telling op zijn telpost. */
const val MC_MSG_LEAVE        = "leave"

/** Master → client(s): de master beëindigt de samenwerking (telling afgerond of gestopt). */
const val MC_MSG_SESSION_END  = "session_end"

/**
 * Master → client(s): de master heeft alle pending records ge-upload en verlaat de telpost
 * zonder zelf een vervolgtelling te starten. Eén van de clients kan de masterfunctie overnemen.
 */
const val MC_MSG_MASTER_HANDOVER = "master_handover"

/**
 * Envelope voor alle berichten over de TCP-verbinding.
 * [type] geeft aan welk payload-type [payload] bevat (JSON-gecodeerd).
 */
@Serializable
data class McEnvelope(
    @SerialName("type")    val type: String,
    @SerialName("payload") val payload: String = ""
)

// ─── Pairing ─────────────────────────────────────────────────────────────────

/** Verstuurd door de client om in te koppelen op de master. */
@Serializable
data class PairingRequest(
    @SerialName("pin")        val pin: String,
    @SerialName("clientId")   val clientId: String,
    @SerialName("clientName") val clientName: String
)

/** Antwoord van de master op een PairingRequest. */
@Serializable
data class PairingResponse(
    @SerialName("accepted")      val accepted: Boolean,
    @SerialName("sessionToken")  val sessionToken: String = "",
    @SerialName("masterName")    val masterName: String  = "",
    @SerialName("tellingId")     val tellingId: String   = "",
    @SerialName("error")         val error: String       = ""
)

// ─── Observatie-events ───────────────────────────────────────────────────────

/**
 * Eén waarneming, verstuurd van client naar master.
 * [clientEventId] is uniek per client en wordt gebruikt voor deduplicatie.
 */
@Serializable
data class ObservationEvent(
    @SerialName("clientId")      val clientId: String,
    @SerialName("clientEventId") val clientEventId: String,
    @SerialName("sessionToken")  val sessionToken: String,
    @SerialName("soortid")       val soortid: String,
    @SerialName("aantal")        val aantal: Int,
    @SerialName("aantalterug")   val aantalterug: Int    = 0,
    @SerialName("tijdstip")      val tijdstip: Long,
    @SerialName("geslacht")      val geslacht: String    = "",
    @SerialName("leeftijd")      val leeftijd: String    = "",
    @SerialName("kleed")         val kleed: String       = "",
    @SerialName("opmerkingen")   val opmerkingen: String = "",
    @SerialName("isUpdate")      val isUpdate: Boolean   = false
)

/**
 * ACK verstuurd door master nadat een ObservationEvent verwerkt is.
 */
@Serializable
data class AckMessage(
    @SerialName("clientEventId")   val clientEventId: String,
    @SerialName("assignedIdLocal") val assignedIdLocal: String = "",
    @SerialName("success")         val success: Boolean,
    @SerialName("error")           val error: String = ""
)

// ─── Heartbeat ───────────────────────────────────────────────────────────────

@Serializable
data class HeartbeatMessage(
    @SerialName("ts") val ts: Long = System.currentTimeMillis()
)

// ─── Bulk export (offline/einde telling) ─────────────────────────────────────

/** Client vraagt export-overdracht aan (optioneel). */
@Serializable
data class ExportRequest(
    @SerialName("clientId")     val clientId: String,
    @SerialName("sessionToken") val sessionToken: String
)

/**
 * Bulk-export van client naar master: alle verzamelde records als JSON-strings.
 * Wordt gebruikt aan het einde van de telling of als de LAN-verbinding na een
 * offline periode hersteld wordt.
 */
@Serializable
data class ExportDataMessage(
    @SerialName("clientId")     val clientId: String,
    @SerialName("sessionToken") val sessionToken: String,
    @SerialName("records")      val records: List<String>   // JSON-gecodeerde ServerTellingDataItem-objecten
)

// ─── Verlaten / sessie-einde ──────────────────────────────────────────────────

/**
 * Verstuurd door de **client** wanneer de gebruiker zijn telpost verlaat en
 * actief uit de telling stapt.
 */
@Serializable
data class LeaveMessage(
    @SerialName("clientId")     val clientId: String,
    @SerialName("sessionToken") val sessionToken: String,
    @SerialName("reason")       val reason: String = ""
)

/**
 * Verstuurd door de **master** naar alle verbonden clients wanneer de telling
 * beëindigd wordt of de samenwerking stopgezet wordt.
 * Clients moeten na ontvangst stoppen met tellen en kunnen de verbinding sluiten.
 */
@Serializable
data class SessionEndMessage(
    @SerialName("masterName") val masterName: String = "",
    @SerialName("reason")     val reason: String = ""
)

// ─── Master-overdracht ────────────────────────────────────────────────────────

/**
 * Verstuurd door de **master** naar alle verbonden clients nadat alle pending records
 * succesvol ge-upload zijn en de master de telpost verlaat zonder zelf een vervolgtelling
 * te starten. Eén client kan op basis van deze melding de masterfunctie overnemen en
 * een nieuwe telling starten.
 *
 * Het veld [eindtijdEpoch] bevat de eindtijd van de afgeronde telling en kan als
 * begintijd worden gebruikt voor een vervolgtelling.
 */
@Serializable
data class MasterHandoverMessage(
    @SerialName("masterName")    val masterName: String = "",
    @SerialName("eindtijdEpoch") val eindtijdEpoch: String = "",
    @SerialName("reason")        val reason: String = ""
)
