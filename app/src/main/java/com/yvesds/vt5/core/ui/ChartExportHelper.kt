package com.yvesds.vt5.core.ui

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * ChartExportHelper - Exporteert volledige Card Views naar binaire PNG-afbeeldingen 
 * met hoge resolutie (minimaal 1024px breed) in Documents/VT5/exports/grafieken/.
 */
object ChartExportHelper {
    private const val TAG = "ChartExportHelper"

    /**
     * Hulpfunctie om de LifecycleOwner (Activity) te vinden door ContextWrappers te ontleden.
     */
    private fun Context.findLifecycleOwner(): LifecycleOwner? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is LifecycleOwner) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /**
     * Exporteert de volledige Card View (inclusief teksten, vinkjes en grafiek) naar PNG.
     * Controleert en maakt zo nodig de submap Documents/VT5/exports/grafieken/ aan.
     */
    suspend fun exportCardToHighResPng(
        context: Context,
        cardView: View,
        speciesName: String,
        siteName: String,
        chartType: String,
        minWidth: Int = 1024
    ) {
        val saf = SaFStorageHelper(context)

        try {
            // 1. Nazicht & gedetailleerde automatische aanmaak van Documents/VT5/exports/grafieken/
            val (grafiekenDir, detailMsg) = saf.getOrCreateGrafiekenDirDetailed()
            if (grafiekenDir == null) {
                throw Exception(detailMsg)
            }

            // 2. High-res Render van de volledige Card
            val width = cardView.width
            val height = cardView.height

            if (width <= 0 || height <= 0) {
                throw Exception("Kaart-layout is nog niet volledig opgebouwd op het scherm (breedte: $width, hoogte: $height).")
            }

            val scale = if (width < minWidth) minWidth.toFloat() / width.toFloat() else 1.0f
            val targetWidth = (width * scale).toInt().coerceAtLeast(minWidth)
            val targetHeight = (height * scale).toInt()

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Vul achtergrond met donkere thema-kleur (#080E14)
            canvas.drawColor(Color.parseColor("#080E14"))
            canvas.scale(scale, scale)

            withContext(Dispatchers.Main) {
                cardView.draw(canvas)
            }

            // 3. Bestandsnaam samenstellen volgens schema: VT5_Grafiek_[Soort]_[Telpost]_[Type]_[Timestamp].png
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val cleanSpecies = speciesName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val cleanSite = siteName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val cleanType = chartType.replace(Regex("[^a-zA-Z0-9_-]"), "_")

            val fileName = "VT5_Grafiek_${cleanSpecies}_${cleanSite}_${cleanType}_$timeStamp.png"

            // 4. Binaire opslag via SAF
            val createFile = withContext(Dispatchers.IO) {
                val existing = grafiekenDir.findFile(fileName)
                val targetFile = existing ?: grafiekenDir.createFile("image/png", fileName)
                    ?: throw Exception("SAF kon bestand $fileName niet aanmaken in map ${grafiekenDir.uri}")

                context.contentResolver.openOutputStream(targetFile.uri, "wt")?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: throw Exception("Kon output stream niet openen voor ${targetFile.uri}")
                targetFile
            }

            // 5. Scan voor mediagalerij
            try {
                MediaScannerConnection.scanFile(context, arrayOf(createFile.uri.path ?: ""), arrayOf("image/png"), null)
            } catch (_: Exception) {}

            val relativePath = "Documents/VT5/exports/grafieken/$fileName"
            Log.i(TAG, "Grafiek succesvol geëxporteerd naar: $relativePath (${targetWidth}x${targetHeight}px)")

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Grafiek geëxporteerd: $fileName",
                    Toast.LENGTH_LONG
                ).show()

                AlertDialog.Builder(context)
                    .setTitle("Grafiek Opgeslagen! 📷")
                    .setMessage("De kaart is in hoge resolutie (${targetWidth}px breed) geëxporteerd.\n\n" +
                            "📁 Locatie:\n$relativePath\n\n" +
                            "Inclusief alle begeleidende aantallen, seizoenskeuzes en aangevinkte opties.")
                    .setPositiveButton("OK", null)
                    .show()
            }

        } catch (e: Throwable) {
            Log.e(TAG, "Fout bij exporteren grafiek: ${e.message}", e)
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(context)
                    .setTitle("Fout bij Exporteren Grafiek")
                    .setMessage("Er is een fout opgetreden tijdens het exporteren:\n\n${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    /**
     * Synchrone legacy helper voor achterwaartse compatibiliteit.
     */
    fun exportViewToImage(view: View, fileNamePrefix: String) {
        val context = view.context
        val owner = context.findLifecycleOwner()
        val parts = fileNamePrefix.split("_")
        val species = if (parts.size > 1) parts[1] else "Soort"
        val type = if (parts.size > 2) parts[2] else "Grafiek"

        if (owner != null) {
            owner.lifecycleScope.launch(Dispatchers.Default) {
                exportCardToHighResPng(
                    context = context,
                    cardView = view,
                    speciesName = species,
                    siteName = "AlleTelposten",
                    chartType = type
                )
            }
        } else {
            Log.e(TAG, "Kon LifecycleOwner/Activity niet vinden voor view.context: $context")
            AlertDialog.Builder(context)
                .setTitle("Fout bij Exporteren Grafiek")
                .setMessage("Kon de actieve Activity niet vinden voor deze View context.")
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
