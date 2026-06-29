package com.yvesds.vt5.core.ui

import androidx.appcompat.app.AlertDialog

/**
 * Centrale plek om pop-updialogen (AlertDialog) consistent te stylen.
 * Gebruikersvereiste: ALLEEN popups mogen reageren op de gekozen kleuren.
 * Deze class blijft bestaan als compatibiliteitslaag voor bestaande callsites.
 * Het delegeert aan [PopupThemeHelper] die achtergrond + tekstkleuren toepast.
 */
object DialogStyler {

    fun apply(dialog: AlertDialog) {
        PopupThemeHelper.apply(dialog, dialog.context)
    }
}
