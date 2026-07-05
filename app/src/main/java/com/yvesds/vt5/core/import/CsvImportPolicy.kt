package com.yvesds.vt5.core.import

/**
 * Centrale policy voor CSV-importen.
 * Geimporteerde tellingen zijn altijd upload-geblokkeerd.
 */
object CsvImportPolicy {
    const val IMPORT_STATUS = "gearchiveerd"
    const val IMPORT_SOURCE = "2"

    private val BLOCKED_STATUSES = setOf(
        "gearchiveerd",
        "archief",
        "geimporteerd",
        "imported",
    )

    fun isUploadBlocked(status: String?, bron: String?): Boolean {
        val normalizedStatus = status?.trim()?.lowercase().orEmpty()
        val normalizedBron = bron?.trim()?.lowercase().orEmpty()
        // Backward compatible: oudere imports gebruikten bron="import-csv".
        return normalizedStatus in BLOCKED_STATUSES || normalizedBron == "import-csv"
    }
}

