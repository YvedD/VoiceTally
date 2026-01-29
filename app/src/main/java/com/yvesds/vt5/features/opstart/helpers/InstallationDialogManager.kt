package com.yvesds.vt5.features.opstart.helpers

import android.app.Dialog
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.yvesds.vt5.core.ui.DialogStyler
import com.yvesds.vt5.core.ui.ProgressDialogHelper

/**
 * Helper class voor dialog management tijdens installatie.
 * 
 * Verantwoordelijkheden:
 * - Progress dialogs (via ProgressDialogHelper)
 * - Info dialogs
 * - Error dialogs
 * - Dialog styling voor zonlicht leesbaarheid
 * 
 * Gebruik:
 * ```kotlin
 * val dialogManager = InstallationDialogManager(activity)
 * val progressDialog = dialogManager.showProgress("Bezig...")
 * dialogManager.updateProgress(progressDialog, "Nieuwe status...")
 * dialogManager.dismissProgress(progressDialog)
 * ```
 */
class InstallationDialogManager(
    private val activity: AppCompatActivity
) {
    /**
     * Toon een progress dialog.
     * 
     * @param message Te tonen boodschap
     * @return De gecreëerde dialog
     */
    fun showProgress(message: String): Dialog {
        return ProgressDialogHelper.show(activity, message)
    }
    
    /**
     * Update de boodschap in een progress dialog.
     * 
     * @param dialog De dialog om te updaten
     * @param message Nieuwe boodschap
     */
    fun updateProgress(dialog: Dialog, message: String) {
        ProgressDialogHelper.updateMessage(dialog, message)
    }
    
    /**
     * Toon een info dialog met OK button.
     * 
     * @param title Dialog titel
     * @param message Dialog boodschap
     * @param onDismiss Optionele callback bij sluiten
     */
    fun showInfo(
        title: String,
        message: String,
        onDismiss: (() -> Unit)? = null
    ) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                onDismiss?.invoke()
            }
            .create()
        
        dialog.show()
        DialogStyler.apply(dialog)
    }
    
    /**
     * Toon een error dialog met OK button.
     * 
     * @param title Dialog titel
     * @param message Error boodschap
     * @param onDismiss Optionele callback bij sluiten
     */
    fun showError(
        title: String,
        message: String,
        onDismiss: (() -> Unit)? = null
    ) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                onDismiss?.invoke()
            }
            .create()

        dialog.show()
        DialogStyler.apply(dialog)
    }
}
