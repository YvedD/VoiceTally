package com.yvesds.vt5.utils

import kotlin.math.max

/**
 * Utility for calculating Levenshtein distance between strings.
 * Standardized on the memory-efficient two-row iterative implementation.
 */
object LevenshteinUtils {

    /**
     * Calculate the Levenshtein distance between two strings.
     * Uses an iterative O(min(m,n)) space complexity implementation.
     */
    fun distance(a: String, b: String): Int {
        val la = a.length
        val lb = b.length
        if (la == 0) return lb
        if (lb == 0) return la
        
        // Ensure 'b' is the shorter string to minimize space
        if (la < lb) return distance(b, a)
        
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

    /**
     * Calculate the normalized Levenshtein similarity ratio between 0.0 and 1.0.
     * 1.0 means identical, 0.0 means completely different.
     */
    fun normalizedRatio(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val d = distance(s1, s2)
        val maxLen = max(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        return 1.0 - (d.toDouble() / maxLen.toDouble())
    }
}
