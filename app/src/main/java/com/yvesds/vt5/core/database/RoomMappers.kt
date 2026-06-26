package com.yvesds.vt5.core.database

import com.yvesds.vt5.core.database.entities.Waarneming
import com.yvesds.vt5.net.ServerTellingDataItem

/**
 * Mapper-functies tussen Room entiteiten en de Netwerk/App modellen.
 */
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
