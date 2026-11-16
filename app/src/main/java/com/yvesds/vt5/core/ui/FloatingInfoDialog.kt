package com.yvesds.vt5.core.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.yvesds.vt5.R

/**
 * FloatingInfoDialog - Herbruikbaar zwevend informatiescherm (Black-Box Component).
 *
 * Eigenschappen:
 * - Witte achtergrond met 70% transparantie (@color/floating_info_bg_color)
 * - Warm rode rand (@color/floating_info_border_color)
 * - 80% schermbreedte x 60% schermhoogte, verticaal en horizontaal gecentreerd
 * - Universeel inzetbaar met aangepaste teksten, iconen en optionele achtergrondafbeeldingen.
 */
object FloatingInfoDialog {

    fun show(
        context: Context,
        title: CharSequence,
        bodyText: CharSequence,
        iconResId: Int? = R.drawable.ic_info_circle_exclamation,
        headerDrawableResId: Int? = R.drawable.kraanvogels_bckgrnd,
        bgDrawableResId: Int? = null,
        closeButtonText: CharSequence = "Sluiten",
        onClose: (() -> Unit)? = null
    ): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_floating_info, null)
        dialog.setContentView(view)

        // Bind UI Elementen uit de black-box layout
        val tvTitle = view.findViewById<TextView>(R.id.tvFloatingInfoTitle)
        val tvBody = view.findViewById<TextView>(R.id.tvFloatingInfoBody)
        val ivIcon = view.findViewById<ImageView>(R.id.ivFloatingInfoIcon)
        val ivHeader = view.findViewById<ImageView>(R.id.ivFloatingInfoHeader)
        val cardHeader = view.findViewById<View>(R.id.cardFloatingInfoHeader)
        val ivBg = view.findViewById<ImageView>(R.id.ivFloatingInfoBg)
        val btnClose = view.findViewById<MaterialButton>(R.id.btnFloatingInfoClose)

        tvTitle.text = title
        tvBody.text = bodyText
        btnClose.text = closeButtonText

        if (iconResId != null) {
            ivIcon.setImageResource(iconResId)
            ivIcon.visibility = View.VISIBLE
        } else {
            ivIcon.visibility = View.GONE
        }

        if (headerDrawableResId != null) {
            ivHeader?.setImageResource(headerDrawableResId)
            cardHeader?.visibility = View.VISIBLE
        } else {
            cardHeader?.visibility = View.GONE
        }

        if (bgDrawableResId != null) {
            ivBg.setImageResource(bgDrawableResId)
            ivBg.visibility = View.VISIBLE
        } else {
            ivBg.visibility = View.GONE
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
            onClose?.invoke()
        }

        // Maak venster achtergrond transparant voor de afgeronde glas-kaart
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Stel afmetingen in: 90% breedte x 75% hoogte gecentreerd op het toestelscherm
        dialog.window?.let { window ->
            val displayMetrics = context.resources.displayMetrics
            val width = (displayMetrics.widthPixels * 0.90).toInt()
            val height = (displayMetrics.heightPixels * 0.75).toInt()
            window.setLayout(width, height)
            window.setGravity(android.view.Gravity.CENTER)
        }

        dialog.show()
        return dialog
    }
}
