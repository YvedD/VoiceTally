package com.yvesds.vt5.core.opslag

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * ErrorLogEmailSender - Verantwoordelijk voor het verzenden van logbestanden via een e-mail Intent.
 * 
 * Gebruikt Android FileProvider of SAF Uri's om het logbestand veilig als bijlage toe te voegen
 * aan een e-mail (Gmail, Outlook, etc.) en markeert het bestand na verzending als *.sent.log.
 */
object ErrorLogEmailSender {
    private const val TAG = "ErrorLogEmailSender"

    /**
     * Start een e-mail intent met het opgegeven logbestand als bijlage.
     * 
     * @param context Context van de activity/application
     * @param targetEmail Het gekozen e-mailadres voor de ontvanger (mag leeg zijn)
     * @param docFile DocumentFile van het logbestand (via SAF)
     * @param rawFile Optioneel rechtstreeks java.io.File (bijv. uit interne opslag fallback)
     * @return true als de intent succesvol is gestart
     */
    fun sendErrorLogByEmail(
        context: Context,
        targetEmail: String?,
        docFile: DocumentFile? = null,
        rawFile: File? = null
    ): Boolean {
        try {
            val fileUri: Uri? = when {
                docFile != null -> docFile.uri
                rawFile != null && rawFile.exists() -> {
                    val authority = "${context.packageName}.fileprovider"
                    FileProvider.getUriForFile(context, authority, rawFile)
                }
                else -> null
            }

            if (fileUri == null) {
                Log.e(TAG, "Geen geldig bestand of Uri gevonden om te e-mailen")
                return false
            }

            val filename = docFile?.name ?: rawFile?.name ?: "error_log.log"

            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                if (!targetEmail.isNullOrBlank()) {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(targetEmail.trim()))
                }
                putExtra(Intent.EXTRA_SUBJECT, "[VT5 Foutdiagnose] Logbestand: $filename")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "In de bijlage vind je het geëxporteerde foutdiagnose-logbestand van VT5.\n\n" +
                            "Bestand: $filename\n" +
                            "Toestel: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
                            "Android Versie: ${android.os.Build.VERSION.RELEASE}\n"
                )
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(emailIntent, "Verzend foutdiagnose via e-mail...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

            return true
        } catch (ex: Exception) {
            Log.e(TAG, "Fout bij starten van e-mail intent: ${ex.message}", ex)
            return false
        }
    }
}
