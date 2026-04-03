package com.yvesds.vt5.features.speech

import com.yvesds.vt5.features.alias.AliasRecord
import com.yvesds.vt5.utils.TextUtils

/**
 * NumberPatterns.kt
 *
 * Hard-coded Dutch number words (0-100) and fast phonetic/cologne filters.
 * Provides robust parsing and tolerant phoneme checks to reduce false positives
 * where ASR outputs a number-word and fuzzy matching could otherwise match a species alias.
 */
object NumberPatterns {

    data class ExtractedAmountPhrase(
        val phrase: String,
        val amount: Int?
    )

    data class NumberTokenParse(
        val value: Int,
        val consumedTokenCount: Int
    )

    // -------------------------
    // Layer 1: text -> integer
    // -------------------------
    private val numberWords: Map<String, Int> = mapOf(
        // 0-20
        "nul" to 0, "zero" to 0,
        "een" to 1, "één" to 1, "eén" to 1, "ene" to 1,
        "twee" to 2,
        "drie" to 3,
        "vier" to 4,
        "vijf" to 5,
        "zes" to 6,
        "zeven" to 7,
        "acht" to 8,
        "negen" to 9,
        "tien" to 10,
        "elf" to 11,
        "twaalf" to 12,
        "dertien" to 13,
        "veertien" to 14,
        "vijftien" to 15,
        "zestien" to 16,
        "zeventien" to 17,
        "achttien" to 18,
        "negentien" to 19,
        "twintig" to 20,
        // tens and some compounds (representative)
        "dertig" to 30, "veertig" to 40, "vijftig" to 50, "zestig" to 60,
        "zeventig" to 70, "tachtig" to 80, "negentig" to 90, "honderd" to 100
    ) + (21..99).associate { i ->
        // include numeric strings as fallback keys ("21", "22", ...)
        i.toString() to i
    }

    private val unitWords: Map<String, Int> = linkedMapOf(
        "een" to 1,
        "twee" to 2,
        "drie" to 3,
        "vier" to 4,
        "vijf" to 5,
        "zes" to 6,
        "zeven" to 7,
        "acht" to 8,
        "negen" to 9
    )

    private val tensWords: Map<String, Int> = linkedMapOf(
        "twintig" to 20,
        "dertig" to 30,
        "veertig" to 40,
        "vijftig" to 50,
        "zestig" to 60,
        "zeventig" to 70,
        "tachtig" to 80,
        "negentig" to 90
    )

    private const val MAX_TRAILING_NUMBER_TOKENS = 4

    // -------------------------------------------------
    // Layer 2: Cologne code fast-match patterns (set)
    // -------------------------------------------------
    private val numberCologneCodes: Set<String> = setOf(
        "65", "07", "06", "2", "27", "37", "35", "08", "086", "042", "064", "26",
        "26424", "47424", "37424", "35424", "08424", "042424", "064424", "06272"
    )

    // -------------------------------------------------
    // Layer 3: IPA phoneme patterns (small snippets)
    // -------------------------------------------------
    private val numberPhonemePatterns: Set<String> = setOf(
        "vɛif", "eːn", "tweː", "driː", "viːr", "zɛs", "zeːvən", "ɑxt", "neːɣən", "tiːn",
        "ɛlf", "twaːlf", "dərtiɣ", "veːrtiɣ", "vɛiftiɣ", "zɛstiɣ", "hɔndərt"
    )

    // PUBLIC API ----------------------------------------------------------------

    fun parseNumberWord(word: String): Int? {
        return parseNumberPhrase(word)
    }

    fun parseNumberPhrase(text: String): Int? {
        val normalized = TextUtils.normalizeLowerNoDiacritics(text)
        if (normalized.isBlank()) return null

        numberWords[normalized]?.let { return it }

        val digitsOnly = normalized.replace("[^0-9]".toRegex(), "")
        if (digitsOnly.isNotBlank() && digitsOnly.length == normalized.replace("\\s+".toRegex(), "").length) {
            digitsOnly.toIntOrNull()?.let { return it }
        }

        val compact = normalized.replace(" ", "")
        return parseCompactNumber(compact)
    }

    fun isNumberWord(text: String): Boolean {
        return parseNumberPhrase(text) != null
    }

    fun parseNumberTokens(tokens: List<String>, startIndex: Int = 0): NumberTokenParse? {
        if (startIndex !in 0..tokens.size) return null
        val available = tokens.size - startIndex
        if (available <= 0) return null

        val maxLen = minOf(MAX_TRAILING_NUMBER_TOKENS, available)
        for (len in maxLen downTo 1) {
            val phrase = tokens.subList(startIndex, startIndex + len).joinToString(" ")
            val parsed = parseNumberPhrase(phrase) ?: continue
            return NumberTokenParse(parsed, len)
        }
        return null
    }

    fun parseTrailingNumberPhrase(text: String): Pair<String, Int?> {
        val normalized = TextUtils.normalizeLowerNoDiacritics(text)
        if (normalized.isBlank()) return "" to null

        val tokens = normalized.split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return normalized to null

        val trailing = parseTrailingNumberTokens(tokens) ?: return normalized to null
        val nameTokens = tokens.dropLast(trailing.consumedTokenCount)
        if (nameTokens.isEmpty()) return normalized to null
        return nameTokens.joinToString(" ") to trailing.value
    }

    fun extractAmountAndPhrase(text: String): ExtractedAmountPhrase {
        val normalized = TextUtils.normalizeLowerNoDiacritics(text)
        if (normalized.isBlank()) return ExtractedAmountPhrase("", null)

        val (trailingPhrase, trailingAmount) = parseTrailingNumberPhrase(normalized)
        if (trailingAmount != null && trailingPhrase.isNotBlank()) {
            return ExtractedAmountPhrase(trailingPhrase, trailingAmount)
        }


        return ExtractedAmountPhrase(normalized, null)
    }

    fun parseTrailingNumberTokens(tokens: List<String>): NumberTokenParse? {
        if (tokens.isEmpty()) return null
        val maxLen = minOf(MAX_TRAILING_NUMBER_TOKENS, tokens.size - 1)
        if (maxLen <= 0) return null

        for (len in maxLen downTo 1) {
            val phrase = tokens.takeLast(len).joinToString(" ")
            val parsed = parseNumberPhrase(phrase) ?: continue
            return NumberTokenParse(parsed, len)
        }
        return null
    }

    fun isNumberCologne(cologne: String?): Boolean {
        if (cologne == null) return false
        return numberCologneCodes.contains(cologne)
    }

    fun isNumberPhoneme(phonemes: String?): Boolean {
        if (phonemes.isNullOrBlank()) return false
        val norm = phonemes.replace("\\s+".toRegex(), "")
        for (p in numberPhonemePatterns) {
            if (norm.contains(p)) return true
            // tolerant match: allow small edit distance
            if (levenshteinDistance(norm, p) <= 1) return true
        }
        return false
    }

    /**
     * Predicate: is this AliasRecord likely a number (so we should ignore it for species matching)?
     * Lightweight, avoids allocating lists; used in tight loops.
     */
    fun isNumberCandidate(rec: AliasRecord): Boolean {
        // Check textual alias first (alias field is raw alias string)
        if (isNumberWord(rec.alias)) return true
        // cologne code
        if (!rec.cologne.isNullOrBlank() && isNumberCologne(rec.cologne)) return true
        // phoneme patterns
        if (!rec.phonemes.isNullOrBlank() && isNumberPhoneme(rec.phonemes)) return true
        return false
    }

    /**
     * Filter out candidate alias records that appear to be number words.
     * Kept for compatibility; internally uses isNumberCandidate.
     */
    fun filterNumberCandidates(candidates: List<AliasRecord>): List<AliasRecord> {
        return candidates.filter { rec -> !isNumberCandidate(rec) }
    }

    // Utility: Levenshtein (simple implementation)
    private fun levenshteinDistance(a: String, b: String): Int {
        val la = a.length
        val lb = b.length
        if (la == 0) return lb
        if (lb == 0) return la
        val prev = IntArray(lb + 1) { it }
        val cur = IntArray(lb + 1)
        for (i in 1..la) {
            cur[0] = i
            for (j in 1..lb) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(cur, 0, prev, 0, lb + 1)
        }
        return prev[lb]
    }

    private fun parseCompactNumber(compactInput: String): Int? {
        val compact = compactInput.trim()
        if (compact.isBlank()) return null

        numberWords[compact]?.let { return it }
        if (compact.all { it.isDigit() }) return compact.toIntOrNull()

        parseHundredsCompact(compact)?.let { return it }
        parseTensCompact(compact)?.let { return it }
        return null
    }

    private fun parseHundredsCompact(compact: String): Int? {
        val idx = compact.indexOf("honderd")
        if (idx < 0 || idx != compact.lastIndexOf("honderd")) return null

        val prefix = compact.substring(0, idx)
        val suffix = compact.substring(idx + "honderd".length)

        val multiplier = when {
            prefix.isBlank() -> 1
            else -> unitWords[prefix] ?: return null
        }

        val normalizedSuffix = suffix.removePrefix("en")
        val remainder = if (normalizedSuffix.isBlank()) 0 else parseCompactNumber(normalizedSuffix) ?: return null
        return multiplier * 100 + remainder
    }

    private fun parseTensCompact(compact: String): Int? {
        tensWords[compact]?.let { return it }

        for ((unitWord, unitValue) in unitWords) {
            for ((tensWord, tensValue) in tensWords) {
                if (compact == unitWord + "en" + tensWord) {
                    return unitValue + tensValue
                }
            }
        }
        return null
    }
}