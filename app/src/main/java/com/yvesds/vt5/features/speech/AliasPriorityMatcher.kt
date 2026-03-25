package com.yvesds.vt5.features.speech

import android.content.Context
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.utils.TextUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * AliasPriorityMatcher (optimized)
 *
 * - Preserves original priority cascade.
 * - Reduces allocations and avoids building/sorting full scored lists in tryFuzzyMatch.
 * - Computes query phonemes once per fuzzy match call and avoids redundant context switches.
 * - Minor housekeeping to reduce overhead inside hot loops.
 *
 * Change: integrates computePrior() into fuzzy scoring as a small contextual prior boost
 * and filters out number-like alias candidates early using NumberPatterns.isNumberCandidate.
 */
object AliasPriorityMatcher {
    private const val SUGGEST_THRESHOLD = 0.40
    private const val POPUP_MAX_CANDIDATES = 6
    private const val FUZZY_AMBIGUITY_GAP = 0.10

    // Scoring weights (sum <= 1)
    private const val W_TEXT = 0.45
    private const val W_COLOGNE = 0.35
    private const val W_PHONEME = 0.20

    // Contextual priors used by computePrior()
    private const val PRIOR_RECENT = 0.25
    private const val PRIOR_TILES = 0.25
    private const val PRIOR_SITE = 0.15
    private const val PRIOR_MAX = 0.6

    // Weight of prior in final hybrid score (tunable)
    private const val PRIOR_WEIGHT = 0.20

    private fun isNumberToken(token: String): Boolean {
        return token.toIntOrNull() != null || NumberPatterns.parseNumberWord(token) != null
    }

    private fun parseAmountToken(token: String): Int? {
        return NumberPatterns.parseNumberPhrase(token)
    }

    suspend fun match(
        hypothesis: String,
        matchContext: MatchContext,
        context: Context,
        saf: SaFStorageHelper
    ): MatchResult = withContext(Dispatchers.Default) {
        val hyp = hypothesis.trim()
        if (hyp.isBlank()) return@withContext MatchResult.NoMatch(hyp, "empty")

        AliasMatcher.ensureLoaded(context, saf)

        val normalized = TextUtils.normalizeLowerNoDiacritics(hyp)
        val tokens = normalized.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return@withContext MatchResult.NoMatch(hyp, "empty-after-norm")

        tryResolveWholePhraseMatch(tokens, hyp, matchContext, context, saf)?.let { resolved ->
            return@withContext resolved
        }

        val matches = mutableListOf<MatchResult.MatchWithAmount>()
        val consumed = BooleanArray(tokens.size)
        var i = 0
        while (i < tokens.size) {
            val maxWindow = minOf(6, tokens.size - i)
            var matched = false

            // exact matching first (canonical/alias in tiles/site)
            for (w in maxWindow downTo 1) {
                val window = tokens.subList(i, i + w)
                if (window.all { isNumberToken(it) }) { i++; matched = true; break }
                if (window.any { NumberPatterns.isNumberWord(it) }) continue
                val phrase = window.joinToString(" ")
                val exact = tryExactMatch(phrase, matchContext, context, saf)
                if (exact != null) {
                    val matchStart = i
                    val nextIndex = i + w
                    var consumedEndExclusive = nextIndex
                    var amount = 1
                    if (nextIndex < tokens.size) {
                        NumberPatterns.parseNumberTokens(tokens, nextIndex)?.let { parsed ->
                            amount = parsed.value
                            consumedEndExclusive = nextIndex + parsed.consumedTokenCount
                            i = nextIndex + parsed.consumedTokenCount
                        } ?: run { i = nextIndex }
                    } else i = nextIndex

                    markConsumed(consumed, matchStart, consumedEndExclusive)

                    matches += MatchResult.MatchWithAmount(exact.first, amount, exact.second)
                    matched = true; break
                }
            }

            if (!matched) {
                // fuzzy matching
                for (w in maxWindow downTo 1) {
                    val window = tokens.subList(i, i + w)
                    if (window.all { isNumberToken(it) }) { i++; matched = true; break }
                    if (window.any { NumberPatterns.isNumberWord(it) }) continue
                    val phrase = window.joinToString(" ")

                    val fuzzy = tryFuzzyMatch(phrase, matchContext, context, saf)
                    if (fuzzy != null) {
                        val matchStart = i
                        val nextIndex = i + w
                        var consumedEndExclusive = nextIndex
                        var amount = 1
                        if (nextIndex < tokens.size) {
                            NumberPatterns.parseNumberTokens(tokens, nextIndex)?.let { parsed ->
                                amount = parsed.value
                                consumedEndExclusive = nextIndex + parsed.consumedTokenCount
                                i = nextIndex + parsed.consumedTokenCount
                            } ?: run { i = nextIndex }
                        } else i = nextIndex

                        markConsumed(consumed, matchStart, consumedEndExclusive)

                        matches += MatchResult.MatchWithAmount(fuzzy.first, amount, fuzzy.second)
                        matched = true; break
                    }
                }
            }

            if (!matched) i++
        }

        val unmatchedFragments = buildUnmatchedFragments(tokens, consumed)

        if (matches.isEmpty()) return@withContext MatchResult.NoMatch(hyp, "no-candidates")
        if (matches.size > 1 || unmatchedFragments.isNotEmpty()) {
            return@withContext MatchResult.MultiMatch(matches, unmatchedFragments, hyp, "multi-species")
        }

        val match = matches.first()
        return@withContext toDirectMatchResult(match.candidate, hyp, match.amount)
    }

    private suspend fun tryExactMatch(
        phrase: String,
        ctx: MatchContext,
        appContext: Context,
        saf: SaFStorageHelper
    ): Pair<Candidate, String>? {
        val candidates = findExactCandidates(phrase, ctx, appContext, saf)
        val best = rankCandidates(candidates, ctx).firstOrNull() ?: return null
        return best to best.source
    }

    private suspend fun tryResolveWholePhraseMatch(
        tokens: List<String>,
        rawHypothesis: String,
        ctx: MatchContext,
        appContext: Context,
        saf: SaFStorageHelper
    ): MatchResult? {
        val trailingAmount = NumberPatterns.parseTrailingNumberTokens(tokens)
        val amount = trailingAmount?.value ?: 1
        val phraseTokens = if (trailingAmount != null) {
            tokens.dropLast(trailingAmount.consumedTokenCount)
        } else {
            tokens
        }
        if (phraseTokens.any { isNumberToken(it) }) return null
        val phrase = phraseTokens.joinToString(" ").trim()
        if (phrase.isBlank()) return null

        val exactCandidates = rankCandidates(findExactCandidates(phrase, ctx, appContext, saf), ctx)
        if (exactCandidates.isNotEmpty()) {
            return when {
                shouldShowExactSuggestions(exactCandidates) -> MatchResult.SuggestionList(
                    exactCandidates.take(POPUP_MAX_CANDIDATES),
                    rawHypothesis,
                    "exact_ambiguous"
                )
                else -> toDirectMatchResult(exactCandidates.first(), rawHypothesis, amount)
            }
        }

        val fuzzyCandidates = buildFuzzyCandidates(phrase, ctx, appContext, saf)
        if (fuzzyCandidates.isEmpty()) return null

        val top = fuzzyCandidates.first()
        if (top.score < SUGGEST_THRESHOLD) return null

        return if (shouldShowFuzzySuggestions(fuzzyCandidates)) {
            MatchResult.SuggestionList(
                fuzzyCandidates.take(POPUP_MAX_CANDIDATES),
                rawHypothesis,
                "fuzzy_ambiguous"
            )
        } else {
            toDirectMatchResult(top, rawHypothesis, amount)
        }
    }

    private fun toDirectMatchResult(candidate: Candidate, hypothesis: String, amount: Int): MatchResult {
        return if (candidate.isInTiles) {
            MatchResult.AutoAccept(candidate, hypothesis, candidate.source, amount)
        } else {
            MatchResult.AutoAcceptAddPopup(candidate, hypothesis, candidate.source, amount)
        }
    }

    private suspend fun findExactCandidates(
        phrase: String,
        ctx: MatchContext,
        appContext: Context,
        saf: SaFStorageHelper
    ): List<Candidate> {
        val normalized = TextUtils.normalizeLowerNoDiacritics(phrase)
        if (normalized.isBlank()) return emptyList()

        val collected = ArrayList<Candidate>(8)
        collected += findExactCanonicalCandidatesInSet(normalized, ctx.tilesSpeciesIds, "exact_canonical_tiles", ctx)
        collected += findExactCanonicalCandidatesInSet(normalized, ctx.siteAllowedIds, "exact_canonical_site", ctx)
            .filterNot { it.speciesId in ctx.tilesSpeciesIds }
        collected += findExactAliasCandidatesInSet(normalized, ctx.tilesSpeciesIds, "exact_alias_tiles", ctx, appContext, saf)
        collected += findExactAliasCandidatesInSet(normalized, ctx.siteAllowedIds, "exact_alias_site", ctx, appContext, saf)
            .filterNot { it.speciesId in ctx.tilesSpeciesIds }

        return deduplicateCandidates(collected, ctx)
    }

    // Return Candidate? if phrase exactly matches canonical name in the allowed set
    // Non-suspending: simple synchronous scan of ctx.speciesById
    private fun findExactCanonicalCandidatesInSet(
        normalized: String,
        allowed: Set<String>,
        sourceTag: String,
        ctx: MatchContext
    ): List<Candidate> {
        val nameMap = ctx.speciesById
        val results = ArrayList<Candidate>()
        for (sid in allowed) {
            val info = nameMap[sid] ?: continue
            val canon = info.first
            if (TextUtils.normalizeLowerNoDiacritics(canon) == normalized) {
                results += Candidate(
                    speciesId = sid,
                    displayName = canon,
                    score = 1.0,
                    isInTiles = sid in ctx.tilesSpeciesIds,
                    source = sourceTag,
                    autoAddToTiles = sourceTag == "exact_canonical_site" && sid !in ctx.tilesSpeciesIds
                )
            }
        }
        return results
    }

    // Return Candidate? if phrase exactly matches any alias in allowed set (consult AliasMatcher)
    // Suspend because AliasMatcher.findExact is suspend.
    private suspend fun findExactAliasCandidatesInSet(
        normalized: String,
        allowed: Set<String>,
        sourceTag: String,
        ctx: MatchContext,
        appContext: Context,
        saf: SaFStorageHelper
    ): List<Candidate> {
        val records = AliasMatcher.findExact(normalized, appContext, saf)
        if (records.isEmpty()) return emptyList()
        val results = ArrayList<Candidate>()
        for (r in records) {
            if (r.speciesid in allowed) {
                val display = r.canonical
                results += Candidate(
                    speciesId = r.speciesid,
                    displayName = display,
                    score = 1.0,
                    isInTiles = r.speciesid in ctx.tilesSpeciesIds,
                    source = sourceTag
                )
            }
        }
        return results
    }

    private suspend fun tryFuzzyMatch(
        phrase: String,
        ctx: MatchContext,
        appContext: Context,
        saf: SaFStorageHelper
    ): Pair<Candidate, String>? {
        val candidate = buildFuzzyCandidates(phrase, ctx, appContext, saf).firstOrNull() ?: return null
        return if (candidate.score >= SUGGEST_THRESHOLD) candidate to candidate.source else null
    }

    private suspend fun buildFuzzyCandidates(
        phrase: String,
        ctx: MatchContext,
        appContext: Context,
        saf: SaFStorageHelper
    ): List<Candidate> {
        val normalized = TextUtils.normalizeLowerNoDiacritics(phrase)

        val shortlist = AliasMatcher.findFuzzyCandidates(normalized, appContext, saf, topN = 50, threshold = 0.0)
        if (shortlist.isEmpty()) return emptyList()

        val filteredShortlist = shortlist.filter { pair ->
            !NumberPatterns.isNumberCandidate(pair.first)
        }
        if (filteredShortlist.isEmpty()) return emptyList()

        var needPhonemes = false
        for (p in filteredShortlist) {
            if (!p.first.phonemes.isNullOrBlank()) {
                needPhonemes = true
                break
            }
        }

        var qPh: String? = null
        if (needPhonemes) {
            qPh = runCatching { DutchPhonemizer.phonemize(normalized) }.getOrDefault("")
        }

        val bestBySpecies = LinkedHashMap<String, Candidate>()

        for (pair in filteredShortlist) {
            val rec = pair.first
            val recNorm = if (rec.norm.isNotBlank()) rec.norm else rec.alias.ifBlank { rec.canonical }
            if (recNorm.isBlank()) continue

            val textSim = normalizedLevenshteinRatio(normalized, recNorm)
            val cologneSim = runCatching { ColognePhonetic.similarity(normalized, recNorm) }.getOrDefault(0.0)
            val phonemeSim = if (!rec.phonemes.isNullOrBlank() && qPh != null) {
                runCatching { DutchPhonemizer.phonemeSimilarity(qPh, rec.phonemes) }.getOrDefault(0.0)
            } else 0.0

            val baseScore = (W_TEXT * textSim + W_COLOGNE * cologneSim + W_PHONEME * phonemeSim).coerceIn(0.0, 1.0)
            val prior = computePrior(rec.speciesid, ctx)
            val priorScaled = (prior / PRIOR_MAX).coerceIn(0.0, 1.0)
            val score = ((1.0 - PRIOR_WEIGHT) * baseScore + PRIOR_WEIGHT * priorScaled).coerceIn(0.0, 1.0)

            val candidate = Candidate(
                speciesId = rec.speciesid,
                displayName = ctx.speciesById[rec.speciesid]?.first ?: rec.canonical,
                score = score,
                isInTiles = rec.speciesid in ctx.tilesSpeciesIds,
                source = if (rec.speciesid in ctx.tilesSpeciesIds) "fuzzy_tiles" else "fuzzy_site"
            )

            val existing = bestBySpecies[rec.speciesid]
            if (existing == null || isCandidateBetter(candidate, existing, ctx)) {
                bestBySpecies[rec.speciesid] = candidate
            }
        }

        return rankCandidates(bestBySpecies.values.toList(), ctx)
    }

    private fun deduplicateCandidates(candidates: List<Candidate>, ctx: MatchContext): List<Candidate> {
        val bestBySpecies = LinkedHashMap<String, Candidate>()
        for (candidate in candidates) {
            val existing = bestBySpecies[candidate.speciesId]
            if (existing == null || isCandidateBetter(candidate, existing, ctx)) {
                bestBySpecies[candidate.speciesId] = candidate
            }
        }
        return rankCandidates(bestBySpecies.values.toList(), ctx)
    }

    private fun isCandidateBetter(candidate: Candidate, existing: Candidate, ctx: MatchContext): Boolean {
        val candidateRank = candidatePriority(candidate, ctx)
        val existingRank = candidatePriority(existing, ctx)
        return when {
            candidateRank != existingRank -> candidateRank < existingRank
            candidate.score != existing.score -> candidate.score > existing.score
            else -> candidate.displayName.length < existing.displayName.length
        }
    }

    private fun rankCandidates(candidates: List<Candidate>, ctx: MatchContext): List<Candidate> {
        return candidates.sortedWith(
            compareBy<Candidate> { candidatePriority(it, ctx) }
                .thenByDescending { it.score }
                .thenByDescending { it.speciesId in ctx.recentIds }
                .thenBy { it.displayName.lowercase() }
        )
    }

    private fun candidatePriority(candidate: Candidate, ctx: MatchContext): Int {
        return when {
            candidate.isInTiles -> 0
            candidate.source.startsWith("exact_canonical") -> 1
            candidate.source.startsWith("exact_alias") -> 2
            candidate.speciesId in ctx.siteAllowedIds -> 3
            else -> 4
        }
    }

    private fun shouldShowExactSuggestions(candidates: List<Candidate>): Boolean {
        if (candidates.size <= 1) return false
        val tileMatches = candidates.count { it.isInTiles }
        return tileMatches != 1
    }

    private fun shouldShowFuzzySuggestions(candidates: List<Candidate>): Boolean {
        if (candidates.size <= 1) return false
        val top = candidates[0]
        val second = candidates[1]
        if (top.isInTiles && !second.isInTiles) return false
        return (top.score - second.score) < FUZZY_AMBIGUITY_GAP
    }

    private fun markConsumed(consumed: BooleanArray, startInclusive: Int, endExclusive: Int) {
        val start = startInclusive.coerceAtLeast(0)
        for (idx in start until endExclusive.coerceAtMost(consumed.size)) {
            consumed[idx] = true
        }
    }

    private fun buildUnmatchedFragments(tokens: List<String>, consumed: BooleanArray): List<String> {
        val fragments = mutableListOf<String>()
        var idx = 0
        while (idx < tokens.size) {
            if (consumed[idx]) {
                idx++
                continue
            }

            val buffer = mutableListOf<String>()
            var sawWord = false
            while (idx < tokens.size && !consumed[idx]) {
                val token = tokens[idx]
                buffer += token
                if (!isNumberToken(token)) {
                    sawWord = true
                } else if (sawWord) {
                    idx++
                    break
                }
                idx++
            }

            val fragment = buffer.joinToString(" ").trim()
            if (fragment.isNotBlank()) {
                fragments += fragment
            }
        }
        return fragments
    }

    private fun computePrior(speciesId: String, ctx: MatchContext): Double {
        var prior = 0.0
        if (speciesId in ctx.recentIds) prior += PRIOR_RECENT
        if (speciesId in ctx.tilesSpeciesIds) prior += PRIOR_TILES
        if (speciesId in ctx.siteAllowedIds) prior += PRIOR_SITE
        return prior.coerceAtMost(PRIOR_MAX)
    }

    private fun normalizedLevenshteinRatio(s1: String, s2: String): Double {
        val d = levenshteinDistance(s1, s2)
        val maxLen = max(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        return 1.0 - (d.toDouble() / maxLen.toDouble())
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val la = a.length
        val lb = b.length
        if (la == 0) return lb
        if (lb == 0) return la
        val prev = IntArray(lb + 1) { it }
        val cur = IntArray(lb + 1)
        for (i in 1..la) {
            cur[0] = i
            val ai = a[i - 1]
            for (j in 1..lb) {
                val cost = if (ai == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(cur, 0, prev, 0, lb + 1)
        }
        return prev[lb]
    }
}