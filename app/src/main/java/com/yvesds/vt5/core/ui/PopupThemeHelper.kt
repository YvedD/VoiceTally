package com.yvesds.vt5.core.ui

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/**
 * Centrale hulp om de door de gebruiker geselecteerde popupkleuren (dialoog) consistent toe te passen.
 * Gebruikersvereiste: ALLEEN popups mogen reageren op de gekozen kleuren.
 * Deze hulp:
 * - Past achtergrondkleur toe aan het dialoogvenster
 * - Past tekstkleur toe op alle TextViews in de dialoogweergavehiërarchie
 * - Past tekstkleur toe op positief-negatiefneutrale knoppen
 * Opmerking: Dit is een runtime-benadering.
 * Het werkt voor dialogen die zijn gebouwd met AlertDialog.Builder
 * en voor DialogFragments die een AlertDialog teruggeven.
 */
@Suppress("unused")
object PopupThemeHelper {

    /**
     * Pas pop-upkleuren toe op een reeds weergegeven AlertDialog.
     * Oproep van: 'dialog.setOnShowListener { ... }' of direct na 'show()'.
     */
    fun apply(dialog: AlertDialog, context: Context) {
        val bg = UiColorPrefs.getBackgroundColor(context)
        val fg = UiColorPrefs.getTextColor(context)

        // Achtergrond
        @Suppress("UseKtx")
        try {
            dialog.window?.setBackgroundDrawable(ColorDrawable(bg))
        } catch (_: Exception) {
        }

        // Melding
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(fg)

        // Aangepaste inhoud weergave tekstkleuren
        dialog.window?.decorView?.let { root ->
            applyTextColorRecursive(root, fg)
        }

        // Knoppen
        try {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(fg)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(fg)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(fg)
        } catch (_: Exception) {
        }
    }

    /**
    * Pas pop-upkleuren toe op een eenvoudig [android.app.Dialog]-venster.
    * Dit is handig voor aangepaste Dialog()-toepassingen die geen AlertDialogs zijn.
    * */
    fun applyDialogWindow(dialog: android.app.Dialog, context: Context) {
        val bg = UiColorPrefs.getBackgroundColor(context)
        val fg = UiColorPrefs.getTextColor(context)

        @Suppress("UseKtx")
        try {
            dialog.window?.setBackgroundDrawable(ColorDrawable(bg))
        } catch (_: Exception) {
        }

        // Aangepaste inhoud weergave tekstkleuren
        try {
            dialog.window?.decorView?.let { root ->
                applyTextColorRecursive(root, fg)
            }
        } catch (_: Exception) {
        }
    }

    private fun applyTextColorRecursive(view: View, textColor: Int) {
        when (view) {
            is TextView -> view.setTextColor(textColor)
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    applyTextColorRecursive(view.getChildAt(i), textColor)
                }
            }
        }
    }
}
