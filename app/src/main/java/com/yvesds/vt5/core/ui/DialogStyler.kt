package com.yvesds.vt5.core.ui

import androidx.appcompat.app.AlertDialog

/**
 * Central place to style popup dialogs (AlertDialog) consistently.
 *
 * User requirement: ONLY popups should react to the chosen colors.
 *
 * This class remains as a compatibility layer for existing callsites.
 * It delegates to [PopupThemeHelper] which applies background + text colors.
 */
object DialogStyler {

    fun apply(dialog: AlertDialog) {
        PopupThemeHelper.apply(dialog, dialog.context)
    }
}
