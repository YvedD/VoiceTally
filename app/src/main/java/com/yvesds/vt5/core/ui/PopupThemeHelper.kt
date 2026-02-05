package com.yvesds.vt5.core.ui

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/**
 * Central helper to apply the user-selected popup (dialog) colors consistently.
 *
 * User requirement: ONLY popups should react to the chosen colors.
 *
 * This helper:
 * - Applies background color to the dialog window
 * - Applies text color to all TextViews in the dialog view hierarchy
 * - Applies text color to positive/negative/neutral buttons
 *
 * Note: This is a runtime approach. It works for dialogs built with AlertDialog.Builder
 * and for DialogFragments that return an AlertDialog.
 */
@Suppress("unused")
object PopupThemeHelper {

    /**
     * Apply popup colors to an already-shown AlertDialog.
     * Call from: `dialog.setOnShowListener { ... }` or immediately after `show()`.
     */
    fun apply(dialog: AlertDialog, context: Context) {
        val bg = UiColorPrefs.getBackgroundColor(context)
        val fg = UiColorPrefs.getTextColor(context)

        // Background
        @Suppress("UseKtx")
        try {
            dialog.window?.setBackgroundDrawable(ColorDrawable(bg))
        } catch (_: Exception) {
        }

        // Message
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(fg)

        // Custom content view text colors
        dialog.window?.decorView?.let { root ->
            applyTextColorRecursive(root, fg)
        }

        // Buttons
        try {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(fg)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(fg)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(fg)
        } catch (_: Exception) {
        }
    }

    /**
     * Apply popup colors to a plain [android.app.Dialog] window.
     * This is useful for custom Dialog() usages that are not AlertDialogs.
     */
    fun applyDialogWindow(dialog: android.app.Dialog, context: Context) {
        val bg = UiColorPrefs.getBackgroundColor(context)
        val fg = UiColorPrefs.getTextColor(context)

        @Suppress("UseKtx")
        try {
            dialog.window?.setBackgroundDrawable(ColorDrawable(bg))
        } catch (_: Exception) {
        }

        // Custom content view text colors
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
