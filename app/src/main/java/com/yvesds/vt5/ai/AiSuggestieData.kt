package com.yvesds.vt5.ai

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Data-container voor AI Suggesties, klaar voor transport tussen Activities.
 */
@Parcelize
data class AiSuggestieData(
    val guildResults: List<VogelSuggestie>,
    val rareHighlights: List<VogelSuggestie> = emptyList(),
    val weerBeschrijving: String
) : Parcelable

@Parcelize
data class VogelSuggestie(
    val guildName: String,
    val soortnaam: String,
    val kans: Int,
    val soortid: String,
    val debugReasoning: String = "",
    val latinName: String? = null,
    val expectedIndex: Float? = null
) : Parcelable
