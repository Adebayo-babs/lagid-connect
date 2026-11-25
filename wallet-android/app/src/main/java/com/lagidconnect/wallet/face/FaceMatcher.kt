package com.lagidconnect.wallet.face

import kotlin.math.sqrt

data class MatchResult(val score: Float, val threshold: Float) {
    val isMatch: Boolean get() = score >= threshold
}

object FaceMatcher {

    /** Cosine similarity: 1.0 = identical, around 0 = unrelated. */
    fun similarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Embedding sizes differ (${a.size} vs ${b.size}). Was the model changed?" }
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = sqrt(na) * sqrt(nb)
        return if (denom > 0f) dot / denom else 0f
    }

    fun compare(a: FloatArray, b: FloatArray, threshold: Float) = MatchResult(similarity(a, b), threshold)
}
