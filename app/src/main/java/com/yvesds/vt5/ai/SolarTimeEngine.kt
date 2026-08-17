package com.yvesds.vt5.ai

import java.util.*
import kotlin.math.*

/**
 * SolarTimeEngine - Berekent biologische dagsegmenten op basis van de zonnestand.
 * Geen API-calls nodig, puur wiskundig op basis van locatie en datum.
 */
object SolarTimeEngine {

    enum class SolarPhase(val displayName: String) {
        NIGHT("Nacht"),
        DAWN("Vroege Ochtend"),
        MORNING("Ochtend"),
        MIDDAY("Middag"),
        LATE_AFTERNOON("Namiddag"),
        EVENING("Avond")
    }

    /**
     * Bepaalt de huidige biologische fase voor een gegeven tijdstip en locatie.
     */
    fun getSolarPhase(lat: Double, lon: Double, calendar: Calendar): SolarPhase {
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val hourFraction = calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60.0

        // 1. Bereken globale zonne-tijden (benadering)
        // Zonne-declinatie
        val decl = 0.409 * sin(2.0 * PI * (dayOfYear - 81) / 365.0)
        
        // Uurhoek bij zonsopkomst/ondergang
        val hourAngle = acos(-tan(Math.toRadians(lat)) * tan(decl))
        val dayLengthHours = (2.0 * Math.toDegrees(hourAngle)) / 15.0
        
        // Solar Noon (lokale tijd benadering op basis van lengtegraad)
        // lon > 0 is Oost, we corrigeren voor de tijdzone offset
        val timezoneOffset = calendar.get(Calendar.ZONE_OFFSET) / 3600000.0
        val solarNoon = 12.0 - (lon / 15.0) + timezoneOffset
        
        val sunrise = solarNoon - (dayLengthHours / 2.0)
        val sunset = solarNoon + (dayLengthHours / 2.0)

        // 2. Map het tijdstip naar een fase
        return when {
            hourFraction < sunrise - 1.0 || hourFraction > sunset + 1.5 -> SolarPhase.NIGHT
            hourFraction < sunrise + 1.0 -> SolarPhase.DAWN
            hourFraction < solarNoon -> SolarPhase.MORNING
            hourFraction < sunset - 3.0 -> SolarPhase.MIDDAY
            hourFraction < sunset -> SolarPhase.LATE_AFTERNOON
            else -> SolarPhase.EVENING
        }
    }
}
