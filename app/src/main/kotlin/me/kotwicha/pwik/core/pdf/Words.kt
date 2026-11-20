package me.kotwicha.pwik.core.pdf

import kotlin.math.abs

data class Glyph(val text: String, val x: Float, val baseline: Float, val width: Float, val size: Float)

data class PdfWord(val text: String, val x0: Float, val x1: Float, val baseline: Float, val size: Float) {
    val cx: Float get() = (x0 + x1) / 2
}

data class PdfPage(val number: Int, val words: List<PdfWord>)

object WordAssembler {

    fun assemble(glyphs: List<Glyph>): List<PdfWord> {
        val visible = glyphs.filter { it.size > 0f }
        if (visible.isEmpty()) return emptyList()
        val sizes = visible.map { it.size }.sorted()
        val typical = sizes[sizes.size / 2]
        val lineTol = typical * 0.35f
        val lines = ArrayList<MutableList<Glyph>>()
        for (g in visible.sortedWith(compareBy({ it.baseline }, { it.x }))) {
            val line = lines.lastOrNull()
            if (line != null && abs(line.first().baseline - g.baseline) <= lineTol) line.add(g)
            else lines.add(mutableListOf(g))
        }
        val words = ArrayList<PdfWord>()
        for (line in lines) {
            line.sortBy { it.x }
            val sb = StringBuilder()
            var x0 = 0f
            var x1 = 0f
            var base = 0f
            var size = 0f
            fun flush() {
                val t = sb.toString().trim()
                if (t.isNotEmpty()) words.add(PdfWord(t, x0, x1, base, size))
                sb.setLength(0)
            }
            for (g in line) {
                val blank = g.text.isBlank()
                val gap = g.x - x1
                if (sb.isNotEmpty() && (blank || gap > g.size * 0.2f)) flush()
                if (blank) continue
                if (sb.isEmpty()) {
                    x0 = g.x
                    base = g.baseline
                    size = g.size
                }
                sb.append(g.text)
                x1 = g.x + g.width
                size = maxOf(size, g.size)
            }
            flush()
        }
        return words
    }
}
