package com.yvesds.vt5.features.telling

/**
 * Centrale statuslabels voor een telling in Room.
 */
object TellingStatus {
    const val ACTIEF = "actief"
    const val EDITED = "edited"
    const val UPLOADING = "uploading"
    const val GEUPLOAD = "geupload"
    const val RETRY_NEEDED = "retry-needed"
    const val GEARCHIVEERD = "gearchiveerd"

    fun normalize(value: String?): String {
        val normalized = value?.trim()?.lowercase().orEmpty()
        return when (normalized) {
            "active" -> ACTIEF
            "uploaded" -> GEUPLOAD
            "retry", "retry_needed", "retryneeded" -> RETRY_NEEDED
            else -> normalized.ifBlank { ACTIEF }
        }
    }

    fun isUploadCompleted(status: String?): Boolean = normalize(status) == GEUPLOAD

    fun isPendingUpload(status: String?): Boolean {
        return when (normalize(status)) {
            ACTIEF, EDITED, RETRY_NEEDED -> true
            else -> false
        }
    }
}

