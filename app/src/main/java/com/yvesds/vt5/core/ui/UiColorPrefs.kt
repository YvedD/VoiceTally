package com.yvesds.vt5.core.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.edit

/**
 * Central place for user-configurable UI colors (background + text).
 *
 * This is intentionally simple (SharedPreferences + runtime application) so we don't need to
 * recompile resources or use dynamic theming APIs.
 */
object UiColorPrefs {

    private const val PREFS_NAME = "vt5_prefs"

    const val PREF_BG_COLOR = "pref_ui_bg_color"
    const val PREF_TEXT_COLOR = "pref_ui_text_color"

    // A small palette of high-contrast colors.
    data class ColorOption(val label: String, val argb: Int)

    val backgroundOptions: List<ColorOption> = listOf(
        ColorOption("Zwart", Color.parseColor("#000000")),
        ColorOption("Donkergrijs", Color.parseColor("#222222")),
        ColorOption("Nachtblauw", Color.parseColor("#0B1B3A")),
        ColorOption("Donkergroen", Color.parseColor("#0E2A1A")),
        ColorOption("Sepia", Color.parseColor("#2B1B0E")),
        ColorOption("Donkerpaars", Color.parseColor("#1B0E2B")),
        ColorOption("Wit", Color.parseColor("#FFFFFF")),
        ColorOption("Lichtgeel", Color.parseColor("#FFF8D6"))
    )

    val textOptions: List<ColorOption> = listOf(
        ColorOption("Wit", Color.parseColor("#FFFFFF")),
        ColorOption("Lichtgrijs", Color.parseColor("#E0E0E0")),
        ColorOption("Zwart", Color.parseColor("#000000")),
        ColorOption("Donkergrijs", Color.parseColor("#222222")),
        ColorOption("Geel", Color.parseColor("#FFEB3B")),
        ColorOption("Cyaan", Color.parseColor("#00BCD4")),
        ColorOption("Oranje", Color.parseColor("#FF9800")),
        ColorOption("Lichtgroen", Color.parseColor("#8BC34A"))
    )

    fun getBackgroundColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(PREF_BG_COLOR, Color.parseColor("#000000"))
    }

    fun getTextColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(PREF_TEXT_COLOR, Color.parseColor("#FFFFFF"))
    }

    fun setBackgroundColor(context: Context, argb: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putInt(PREF_BG_COLOR, argb)
        }
    }

    fun setTextColor(context: Context, argb: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putInt(PREF_TEXT_COLOR, argb)
        }
    }

    /** Apply colors to an Activity root view + all nested TextViews. */
    fun applyToActivity(activity: Activity) {
        val root = activity.findViewById<View>(android.R.id.content) ?: return
        val bg = getBackgroundColor(activity)
        val fg = getTextColor(activity)

        root.setBackgroundColor(bg)
        applyTextColorRecursive(root, fg)

        // Window background (helps with dialogs/overdraw)
        activity.window?.decorView?.setBackgroundColor(bg)
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

