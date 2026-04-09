package com.yvesds.vt5.utils

object SessionRemarksMarker {
    const val MARKER = "[we tellen verder]"

    fun append(text: String?): String {
        val trimmed = text.orEmpty().trim()
        if (trimmed.contains(MARKER, ignoreCase = false)) return trimmed
        return if (trimmed.isBlank()) {
            MARKER
        } else {
            "$trimmed $MARKER"
        }
    }

    fun remove(text: String?): String {
        return text.orEmpty()
            .replace(MARKER, "")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}

