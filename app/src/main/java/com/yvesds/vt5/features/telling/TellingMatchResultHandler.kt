package com.yvesds.vt5.features.telling

import android.app.Activity
import com.yvesds.vt5.features.speech.Candidate
import com.yvesds.vt5.features.speech.MatchResult
import com.yvesds.vt5.features.speech.NumberPatterns

/**
 * TellingMatchResultHandler: Processes speech recognition match results.
 * 
 * Responsibilities:
 * - Route match results to appropriate handlers
 * - Handle auto-accept matches
 * - Handle multi-match scenarios
 * - Handle suggestion lists
 */
class TellingMatchResultHandler(
    @Suppress("UNUSED_PARAMETER")
    private val activity: Activity
) {
    // Callbacks for different match types
    var onAutoAccept: ((String?, Candidate, Int) -> Unit)? = null
    var onAutoAcceptWithPopup: ((String?, Candidate, Int) -> Unit)? = null
    var onMultiMatch: ((String?, List<MatchResult.MatchWithAmount>, List<String>) -> Unit)? = null
    var onSuggestionList: ((String?, String, List<Candidate>, Int) -> Unit)? = null
    var onNoMatch: ((String?, String) -> Unit)? = null

    /**
     * Handle different types of match results from speech parsing.
     */
    fun handleMatchResult(result: MatchResult, utteranceId: String? = null) {
        when (result) {
            is MatchResult.AutoAccept -> {
                handleAutoAcceptMatch(utteranceId, result)
            }
            is MatchResult.AutoAcceptAddPopup -> {
                handleAutoAcceptAddPopup(utteranceId, result)
            }
            is MatchResult.MultiMatch -> {
                handleMultiMatch(utteranceId, result)
            }
            is MatchResult.SuggestionList -> {
                onSuggestionList?.invoke(utteranceId, result.hypothesis, result.candidates, extractCountFromHypothesis(result.hypothesis))
            }
            is MatchResult.NoMatch -> {
                onNoMatch?.invoke(utteranceId, result.hypothesis)
            }
        }
    }

    /**
     * Handle auto-accept match (species recognized and in tiles).
     */
    private fun handleAutoAcceptMatch(utteranceId: String?, result: MatchResult.AutoAccept) {
        onAutoAccept?.invoke(utteranceId, result.candidate, result.amount)
    }

    /**
     * Handle auto-accept with popup (species recognized but not in tiles).
     */
    private fun handleAutoAcceptAddPopup(utteranceId: String?, result: MatchResult.AutoAcceptAddPopup) {
        onAutoAcceptWithPopup?.invoke(utteranceId, result.candidate, result.amount)
    }

    /**
     * Handle multi-match scenario (multiple species recognized).
     */
    private fun handleMultiMatch(utteranceId: String?, result: MatchResult.MultiMatch) {
        onMultiMatch?.invoke(utteranceId, result.matches, result.unmatchedFragments)
    }

    /**
     * Extract count from hypothesis text.
     * Simple implementation - can be enhanced with TellingLogManager if needed.
     */
    private fun extractCountFromHypothesis(hypothesis: String): Int {
        return NumberPatterns.extractAmountAndPhrase(hypothesis).amount ?: 1
    }
}
