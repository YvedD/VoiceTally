package com.yvesds.vt5.core.ui

import android.graphics.Color
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/**
 * Central place to style popup dialogs (AlertDialog) consistently.
 *
 * We still keep a theme-based solution (materialAlertDialogTheme) but this is a safe fallback
 * for dialogs that don't fully pick up the Material3 theme overlay.
 */
object DialogStyler {

    fun apply(dialog: AlertDialog) {
        // Message
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(Color.WHITE)

        // Buttons
        try {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Color.WHITE)
        } catch (_: Exception) {
        }
    }
}

