package com.yvesds.vt5.core.ui

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.yvesds.vt5.R

/**
 * VT5GlassTheme: Central Design System Tokens for VT5 Glassmorphic UI.
 * 
 * Edit colors, opacities, corner radii, and stroke widths in this file or in
 * `res/values/colors_vt5_glass.xml` to manually tweak the theme project-wide.
 */
object VT5GlassTheme {

    // --- Glassmorphic Colors & Opacities ---
    const val MASK_OVERLAY_HEX = "#85080E14" // 85% Opacity Dark Background Overlay
    const val CARD_BACKGROUND_HEX = "#CC121A24" // 80% Opacity Dark Navy/Gray (#121A24)
    const val SUB_CARD_BACKGROUND_HEX = "#990E141D" // 60% Opacity Dark Sub-Card (#0E141D)
    const val CYAN_STROKE_HEX = "#227CAF" // Accent Cyan/Light Blue Stroke

    // --- Corner Radii & Elevations ---
    const val DIALOG_CORNER_RADIUS_DP = 16f
    const val CARD_CORNER_RADIUS_DP = 12f
    const val DIALOG_ELEVATION_DP = 12f

    // --- Background Image Slots & Custom Overlay ---
    val DEFAULT_BACKGROUND_DRAWABLE = R.drawable.vt5_btn_glass_primary
    
    // Support custom background image URI or resource id override
    var customBackgroundImageUri: String? = null

    fun getOverlayColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.vt5_glass_overlay_bg)
    }

    fun getCardBackgroundColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.vt5_glass_card_bg)
    }

    fun getSubCardBackgroundColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.vt5_glass_subcard_bg)
    }

    fun getStrokeColor(context: Context): Int {
        return ContextCompat.getColor(context, R.color.vt5_glass_cyan_stroke)
    }
}
