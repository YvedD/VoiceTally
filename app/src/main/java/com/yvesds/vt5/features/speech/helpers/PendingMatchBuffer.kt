package com.yvesds.vt5.features.speech.helpers

import android.content.Context
import android.util.Log
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.features.speech.AliasPriorityMatcher
import com.yvesds.vt5.features.speech.MatchContext
import com.yvesds.vt5.features.speech.MatchResult
import com.yvesds.vt5.utils.TextUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.LinkedHashMap
import java.util.UUID

/**
 * PendingMatchBuffer
 *
 * Manages a bounded set of pending ASR hypotheses that require heavy matching.
 * Each pending item gets its own coroutine so late/heavy parses may complete out-of-order.
 */
class PendingMatchBuffer(
    private val context: Context,
    private val saf: SaFStorageHelper,
    private val logger: SpeechMatchLogger
) {
    companion object {
        private const val TAG = "PendingMatchBuffer"
        private const val BUFFER_CAPACITY = 8
        private const val PER_ITEM_TIMEOUT_MS = 1200L
        private const val MAX_RETRY_ATTEMPTS = 1
    }

    private data class PendingAsr(
        val id: String,
        val utteranceId: String,
        val text: String,
        val confidence: Float,
        val matchContext: MatchContext,
        val partials: List<String>,
        val attempts: Int = 0,
        val timestampMs: Long = System.currentTimeMillis()
    )

    private val bgScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val inFlightLock = Any()
    private val inFlightById = LinkedHashMap<String, PendingAsr>(BUFFER_CAPACITY)

    private var resultListener: ((utteranceId: String, result: MatchResult) -> Unit)? = null

    /**
     * Register a listener to receive results for pending items.
     */
    fun setResultListener(listener: (utteranceId: String, result: MatchResult) -> Unit) {
        resultListener = listener
    }

    /**
     * Start heavy matching for a pending ASR hypothesis.
     * Returns null when the bounded in-flight buffer is full.
     */
    fun enqueuePending(
        utteranceId: String,
        text: String,
        confidence: Float,
        matchContext: MatchContext,
        partials: List<String>
    ): String? {
        val pending = PendingAsr(
            id = UUID.randomUUID().toString(),
            utteranceId = utteranceId,
            text = text,
            confidence = confidence,
            matchContext = matchContext,
            partials = partials
        )

        synchronized(inFlightLock) {
            if (inFlightById.size >= BUFFER_CAPACITY) {
                Log.w(TAG, "In-flight buffer full, dropped pending text='$text'")
                return null
            }
            inFlightById[pending.id] = pending
        }

        launchPendingProcessing(pending)
        return pending.id
    }

    private fun launchPendingProcessing(item: PendingAsr) {
        bgScope.launch {
            try {
                processPendingItem(item)
            } catch (ex: CancellationException) {
                Log.i(TAG, "Pending job cancelled for id=${item.id}")
                throw ex
            } catch (ex: Exception) {
                Log.w(TAG, "Pending job failed for id=${item.id}: ${ex.message}", ex)
            } finally {
                synchronized(inFlightLock) {
                    inFlightById.remove(item.id)
                }
            }
        }
    }

    private suspend fun processPendingItem(item: PendingAsr) {
        try {
            val normalizedText = TextUtils.normalizeLowerNoDiacritics(item.text)
            val maybeResult = withTimeoutOrNull(PER_ITEM_TIMEOUT_MS) {
                AliasPriorityMatcher.match(normalizedText, item.matchContext, context, saf)
            }

            if (maybeResult == null) {
                handleTimeout(item)
            } else {
                handleSuccess(item, maybeResult)
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Log.w(TAG, "Error processing pending id=${item.id}: ${ex.message}", ex)
        }
    }

    private fun handleTimeout(item: PendingAsr) {
        Log.w(TAG, "Pending heavy match timed out for id=${item.id} text='${item.text}'")

        if (item.attempts < MAX_RETRY_ATTEMPTS) {
            val retryItem = item.copy(attempts = item.attempts + 1)
            synchronized(inFlightLock) {
                inFlightById[retryItem.id] = retryItem
            }
            launchPendingProcessing(retryItem)
        } else {
            val result = MatchResult.NoMatch(item.text, "pending_timed_out")
            logger.logMatchResult(
                item.text,
                result,
                item.partials,
                asrHypotheses = listOf(item.text to item.confidence)
            )
            notifyResult(item, result)
        }
    }

    private fun handleSuccess(item: PendingAsr, result: MatchResult) {
        logger.logMatchResult(
            item.text,
            result,
            item.partials,
            asrHypotheses = listOf(item.text to item.confidence)
        )
        notifyResult(item, result)
    }

    private fun notifyResult(item: PendingAsr, result: MatchResult) {
        try {
            resultListener?.invoke(item.utteranceId, result)
        } catch (ex: Exception) {
            Log.w(TAG, "Result listener failed for id=${item.id}: ${ex.message}", ex)
        }
    }
}
