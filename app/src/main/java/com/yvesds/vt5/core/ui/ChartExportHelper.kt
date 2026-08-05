package com.yvesds.vt5.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * ChartExportHelper - Exporteert View-componenten (zoals Vico grafieken) naar Bitmaps op de opslag.
 */
object ChartExportHelper {
    private const val TAG = "ChartExportHelper"

    /**
     * Maakt een screenshot van een specifieke view en slaat deze op in de Pictures map.
     */
    fun exportViewToImage(view: View, fileNamePrefix: String) {
        try {
            val context = view.context
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${fileNamePrefix}_$timeStamp.png"
            
            val storageDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "VT5_Charts")
            if (!storageDir.exists()) storageDir.mkdirs()

            val imageFile = File(storageDir, fileName)
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // Maak zichtbaar in de galerij
            MediaScannerConnection.scanFile(context, arrayOf(imageFile.absolutePath), null, null)
            
            Toast.makeText(context, "Grafiek opgeslagen: ${imageFile.name}", Toast.LENGTH_SHORT).show()
            Log.i(TAG, "Grafiek succesvol geëxporteerd naar: ${imageFile.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Fout bij exporteren grafiek: ${e.message}")
            Toast.makeText(view.context, "Export mislukt: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
