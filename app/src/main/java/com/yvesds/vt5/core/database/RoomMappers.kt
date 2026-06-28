package com.yvesds.vt5.core.database

import com.yvesds.vt5.core.database.entities.TellingHeader
import com.yvesds.vt5.core.database.entities.Waarneming
import com.yvesds.vt5.net.ServerTellingDataItem
import com.yvesds.vt5.net.ServerTellingEnvelope

/**
 * Mapper-functies tussen Room entiteiten en de Netwerk/App modellen.
 */
fun TellingHeader.toServerEnvelope(records: List<Waarneming>): ServerTellingEnvelope {
    return ServerTellingEnvelope(
        externid = this.externid,
        timezoneid = this.timezoneid,
        bron = this.bron,
        idLocal = "", // Wordt door server genegeerd bij counts_save
        tellingid = this.tellingid,
        telpostid = this.telpostid,
        begintijd = this.begintijd,
        eindtijd = this.eindtijd,
        tellers = this.tellers,
        weer = this.weer,
        windrichting = this.windrichting,
        windkracht = this.windkracht,
        temperatuur = this.temperatuur,
        bewolking = this.bewolking,
        bewolkinghoogte = this.bewolkinghoogte,
        neerslag = this.neerslag,
        duurneerslag = this.duurneerslag,
        zicht = this.zicht,
        tellersactief = this.tellersactief,
        tellersaanwezig = this.tellersaanwezig,
        typetelling = this.typetelling,
        metersnet = this.metersnet,
        geluid = this.geluid,
        opmerkingen = this.opmerkingen,
        onlineid = this.onlineid,
        hydro = this.hydro,
        hpa = this.hpa,
        equipment = this.equipment,
        uuid = this.uuid,
        uploadtijdstip = this.uploadtijdstip,
        nrec = this.nrec,
        nsoort = this.nsoort,
        data = records.map { it.toServerItem() }
    )
}

fun Waarneming.toServerItem(): ServerTellingDataItem {
    return ServerTellingDataItem(
        idLocal = this.idLocal,
        tellingid = this.tellingid,
        soortid = this.soortid,
        aantal = this.aantal,
        richting = this.richting,
        aantalterug = this.aantalterug,
        richtingterug = this.richtingterug,
        sightingdirection = this.sightingdirection,
        lokaal = this.lokaal,
        aantal_plus = this.aantal_plus,
        aantalterug_plus = this.aantalterug_plus,
        lokaal_plus = this.lokaal_plus,
        markeren = this.markeren,
        markerenlokaal = this.markerenlokaal,
        geslacht = this.geslacht,
        leeftijd = this.leeftijd,
        kleed = this.kleed,
        opmerkingen = this.opmerkingen,
        trektype = this.trektype,
        teltype = this.teltype,
        location = this.location,
        height = this.height,
        tijdstip = this.tijdstip,
        groupid = this.groupid,
        uploadtijdstip = this.uploadtijdstip,
        totaalaantal = this.totaalaantal
    )
}
