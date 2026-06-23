package com.yvesds.vt5.features.telling.controller

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.yvesds.vt5.R
import com.yvesds.vt5.core.ui.DialogStyler
import com.yvesds.vt5.features.recent.RecentSpeciesStore
import com.yvesds.vt5.features.recent.SpeciesUsageScoreStore
import com.yvesds.vt5.features.speech.Candidate
import com.yvesds.vt5.features.telling.TellingSpeechHandler
import com.yvesds.vt5.features.telling.*
import com.yvesds.vt5.hoofd.InstellingenScherm
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechInputController @Inject constructor(
    private val scope: CoroutineScope,
    private val speciesManager: TellingSpeciesManager,
    private val speechHandler: TellingSpeechHandler,
    private val matchResultHandler: TellingMatchResultHandler
) {
    private val TAG = "SpeechInputController"

    private var tileTapAggregationManager: TileTapAggregationManager? = null

    sealed class SpeechEvent {
        data class HypothesesReceived(
            val utteranceId: String,
            val hypotheses: List<Pair<String, Float>>,
            val partials: List<String>
        ) : SpeechEvent()

        data class SuggestionList(
            val utteranceId: String?,
            val hypothesis: String,
            val candidates: List<com.yvesds.vt5.features.speech.Candidate>,
            val count: Int
        ) : SpeechEvent()

        data class NoMatch(
            val utteranceId: String?,
            val hypothesis: String
        ) : SpeechEvent()

        object ListeningStarted : SpeechEvent()
        data class Error(val message: String, val exception: Exception? = null) : SpeechEvent()
        data class RawResult(val text: String) : SpeechEvent()
        data class ShowAddSpeciesConfirmation(
            val utteranceId: String?,
            val speciesId: String,
            val displayName: String,
            val count: Int
        ) : SpeechEvent()
    }

    private val _speechEvents = MutableSharedFlow<SpeechEvent>(replay = 0)
    val speechEvents = _speechEvents.asSharedFlow()

    private val parseJobsByUtteranceId = linkedMapOf<String, Job>()

    init {
        setupCallbacks()
    }

    fun setTileTapAggregationManager(manager: TileTapAggregationManager) {
        this.tileTapAggregationManager = manager
    }

    private fun setupCallbacks() {
        speechHandler.onHypothesesReceived = { utteranceId, hypotheses, partials ->
            handleHypotheses(utteranceId, hypotheses, partials)
        }

        speechHandler.onPendingMatchResult = { utteranceId, result ->
            scope.launch(Dispatchers.Main) {
                try {
                    matchResultHandler.handleMatchResult(result, utteranceId)
                } catch (ex: Exception) {
                    Log.w(TAG, "Pending hypotheses handling failed: ${ex.message}", ex)
                }
            }
        }

        speechHandler.onListeningStarted = {
            emitEvent(SpeechEvent.ListeningStarted)
        }

        speechHandler.onRawResult = { rawText ->
            emitEvent(SpeechEvent.RawResult(rawText))
        }

        matchResultHandler.onAutoAccept = { utteranceId, candidate, amount ->
            handleRecognizedCandidate(utteranceId, candidate, amount)
        }

        matchResultHandler.onAutoAcceptWithPopup = { utteranceId, candidate, amount ->
            handleRecognizedCandidate(utteranceId, candidate, amount)
        }

        matchResultHandler.onMultiMatch = { utteranceId, matches, unmatchedFragments ->
            matches.forEach { match ->
                handleRecognizedCandidate(utteranceId, match.candidate, match.amount)
            }
        }

        matchResultHandler.onSuggestionList = { utteranceId, hypothesis, candidates, count ->
            emitEvent(SpeechEvent.SuggestionList(utteranceId, hypothesis, candidates, count))
        }

        matchResultHandler.onNoMatch = { utteranceId, hypothesis ->
            emitEvent(SpeechEvent.NoMatch(utteranceId, hypothesis))
        }
    }

    fun handleHypotheses(utteranceId: String, hypotheses: List<Pair<String, Float>>, partials: List<String>) {
        val job = scope.launch(Dispatchers.Default) {
            try {
                val matchContext = speechHandler.getCachedMatchContext()

                if (matchContext == null) {
                    Log.w(TAG, "MatchContext not available during hypothesis processing")
                    return@launch
                }

                val result = speechHandler.parseSpokenWithHypotheses(utteranceId, hypotheses, matchContext, partials, asrWeight = 0.4)

                withContext(Dispatchers.Main) {
                    try {
                        matchResultHandler.handleMatchResult(result, utteranceId)
                    } catch (ex: Exception) {
                        Log.w(TAG, "Hypotheses handling (UI) failed: ${ex.message}", ex)
                    }
                }
            } catch (ex: Exception) {
                Log.w(TAG, "Hypotheses handling (background) failed: ${ex.message}", ex)
            } finally {
                withContext(Dispatchers.Main) {
                    parseJobsByUtteranceId.remove(utteranceId)
                }
            }
        }

        parseJobsByUtteranceId[utteranceId] = job
    }

    fun handleRecognizedCandidate(utteranceId: String?, candidate: Candidate, count: Int) {
        when {
            candidate.isInTiles -> recordSpeciesCount(candidate.speciesId, count)
            candidate.autoAddToTiles -> autoAddRecognizedSpecies(candidate.speciesId, candidate.displayName, count)
            else -> emitEvent(SpeechEvent.ShowAddSpeciesConfirmation(utteranceId, candidate.speciesId, candidate.displayName, count))
        }
    }

    private fun recordSpeciesCount(speciesId: String, count: Int) {
        scope.launch {
            tileTapAggregationManager?.flushSpeciesAndAwait(speciesId)
            speciesManager.updateSoortCountInternal(speciesId, count)
            speciesManager.collectFinalAsRecord(speciesId, count)
            
            withContext(Dispatchers.Main) {
                RecentSpeciesStore.recordUse(
                    com.yvesds.vt5.VT5App.instance, 
                    speciesId, 
                    maxEntries = InstellingenScherm.getMaxFavorieten(com.yvesds.vt5.VT5App.instance).let { 
                        if (it == InstellingenScherm.MAX_FAVORIETEN_ALL) SpeciesUsageScoreStore.MAX_ALL_CAP else it 
                    }
                )
            }
        }
    }

    private fun autoAddRecognizedSpecies(speciesId: String, displayName: String, count: Int) {
        scope.launch {
            tileTapAggregationManager?.flushSpeciesAndAwait(speciesId)
            speciesManager.addSpeciesToTiles(speciesId, displayName, count)
            speciesManager.collectFinalAsRecord(speciesId, count)
            
            withContext(Dispatchers.Main) {
                RecentSpeciesStore.recordUse(
                    com.yvesds.vt5.VT5App.instance, 
                    speciesId, 
                    maxEntries = InstellingenScherm.getMaxFavorieten(com.yvesds.vt5.VT5App.instance).let { 
                        if (it == InstellingenScherm.MAX_FAVORIETEN_ALL) SpeciesUsageScoreStore.MAX_ALL_CAP else it 
                    }
                )
            }
        }
    }

    fun handleAddSpeciesConfirmed(speciesId: String, displayName: String, count: Int) {
        scope.launch {
            speciesManager.addSpeciesToTiles(speciesId, displayName, count)
            speciesManager.collectFinalAsRecord(speciesId, count)
        }
    }

    private fun emitEvent(event: SpeechEvent) {
        scope.launch {
            _speechEvents.emit(event)
        }
    }

    fun onResume(activity: AppCompatActivity) {
        speechHandler.initialize()
        speechHandler.initializeVolumeKeyHandler()
    }

    fun onPause() {
        speechHandler.cleanup()
    }

    fun handleKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        return speechHandler.handleKeyDown(keyCode, event)
    }

    fun updateCachedMatchContext(context: com.yvesds.vt5.features.speech.MatchContext) {
        speechHandler.updateCachedMatchContext(context)
    }

    fun loadAliases(forceReload: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            speechHandler.loadAliases(forceReload)
        }
    }

    fun buildSelectedSpeciesMap(speciesMap: Map<String, String>): HashMap<String, String> {
        return speechHandler.buildSelectedSpeciesMap(speciesMap)
    }

    fun cleanup() {
        parseJobsByUtteranceId.values.forEach { it.cancel() }
        parseJobsByUtteranceId.clear()
        speechHandler.cleanup()
    }
}
