package me.kotwicha.pwik.core.text

import java.text.Normalizer

private val MARKS = Regex("\\p{Mn}+")
private val NOT_ALNUM = Regex("[^a-z0-9]+")
private val ON_REQUEST = Regex("\\bn\\s*/\\s*z\\b")
private val PLATFORM_REF = Regex("^(0\\d|\\d\\d)$")
private val ROMAN = mapOf("i" to "1", "ii" to "2", "iii" to "3", "iv" to "4", "v" to "5", "vi" to "6")

fun stripMarks(s: String): String =
    MARKS.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")
        .replace('ł', 'l')

fun fold(s: String): String =
    NOT_ALNUM.replace(ON_REQUEST.replace(stripMarks(s), "nz"), " ").trim().replace(Regex(" +"), " ")

fun groupKey(s: String): String {
    var tokens = fold(s).split(' ').filter { it.isNotEmpty() }
    if (tokens.size > 1 && PLATFORM_REF.matches(tokens.last())) tokens = tokens.dropLast(1)
    return tokens.joinToString(" ") { ROMAN[it] ?: it }
}

fun slug(s: String): String = fold(s).replace(' ', '-')

fun similarity(a: String, b: String): Double {
    val ratio = sequenceRatio(a, b)
    val ta = a.split(' ').filter { it.isNotEmpty() }.toSet()
    val tb = b.split(' ').filter { it.isNotEmpty() }.toSet()
    val union = ta union tb
    var jaccard = if (union.isEmpty()) 0.0 else (ta intersect tb).size.toDouble() / union.size
    if (ta.isNotEmpty() && tb.isNotEmpty() && (tb.containsAll(ta) || ta.containsAll(tb))) {
        jaccard = maxOf(jaccard, 0.85)
    }
    return 0.5 * ratio + 0.5 * jaccard
}

fun sequenceRatio(a: String, b: String): Double {
    val total = a.length + b.length
    if (total == 0) return 1.0
    return 2.0 * matchingChars(a, 0, a.length, b, 0, b.length) / total
}

private fun matchingChars(a: String, a0: Int, a1: Int, b: String, b0: Int, b1: Int): Int {
    if (a0 >= a1 || b0 >= b1) return 0
    var bestLen = 0
    var bestA = a0
    var bestB = b0
    val row = IntArray(b1 - b0 + 1)
    for (i in a0 until a1) {
        var prevDiag = 0
        for (j in b0 until b1) {
            val k = j - b0 + 1
            val saved = row[k]
            row[k] = if (a[i] == b[j]) prevDiag + 1 else 0
            if (row[k] > bestLen) {
                bestLen = row[k]
                bestA = i - bestLen + 1
                bestB = j - bestLen + 1
            }
            prevDiag = saved
        }
    }
    if (bestLen == 0) return 0
    return bestLen +
        matchingChars(a, a0, bestA, b, b0, bestB) +
        matchingChars(a, bestA + bestLen, a1, b, bestB + bestLen, b1)
}

fun searchRank(query: String, candidate: String): Int? {
    if (query.isEmpty()) return null
    return when {
        candidate == query -> 0
        candidate.startsWith(query) -> 1
        candidate.split(' ').any { it.startsWith(query) } -> 2
        query.split(' ').all { q -> candidate.split(' ').any { it.startsWith(q) } } -> 3
        candidate.contains(query) -> 4
        else -> null
    }
}
