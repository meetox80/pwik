package me.kotwicha.pwik.core.pdf

import kotlin.math.abs
import me.kotwicha.pwik.core.text.fold

data class ParsedCell(val minute: Int, val flags: String)

data class ParsedRow(val name: String, val cells: List<ParsedCell?>)

data class ParsedTable(
    val page: Int,
    val line: String,
    val title: String,
    val validity: String?,
    val columnFlags: List<String>,
    val rows: List<ParsedRow>,
    val legend: Map<String, String>,
    val footnotes: List<String>,
) {
    val columns: Int get() = columnFlags.size
}

data class ParsedDocument(val tables: List<ParsedTable>, val skippedPages: List<Int>)

class TimetablePdfParser {

    private val time = Regex("^([0-2]?\\d)[.:,]([0-5]\\d)([A-Za-z]{0,2})$")
    private val dash = Regex("^[-–—]+$")
    private val malformed = Regex("^\\d{1,2}[.:,]\\d{3,}[A-Za-z]{0,2}$")
    private val flag = Regex("^[A-Z]{1,3}$")
    private val suffix = Regex("^[A-Za-z]{1,2}$")
    private val lineHeader = Regex("linia\\b.*?\\bnr\\.?\\s*([0-9]+[a-z]?)", RegexOption.IGNORE_CASE)
    private val lineHeaderLoose = Regex("linia\\s+(?:miejska\\s+)?(?:nr\\.?\\s*)?([0-9]+[a-z]?)\\b", RegexOption.IGNORE_CASE)
    private val validityPattern = Regex("\\(\\s*obowi[aą]zuje\\s+(.*?)\\s*\\)", RegexOption.IGNORE_CASE)
    private val legendEntry = Regex("^([A-Za-z]{1,3})\\s*[-–—]\\s*(\\S.*)$")
    private val legendSplit = Regex("\\s+(?=[A-Z]{1,3}\\s*[-–—]\\s+\\S)")
    private val labels = setOf("przystanek", "przystanki", "godziny", "godzina", "odjazdu", "odjazdy")

    private class Row(val words: MutableList<PdfWord>) {
        var baseline: Float = words.first().baseline
        val text: String get() = words.joinToString(" ") { it.text }
    }

    private enum class Kind { HEADER, LABEL, FLAGS, DATA, NAME, NOTE, EMPTY }

    fun parse(pages: List<PdfPage>): ParsedDocument {
        val tables = ArrayList<ParsedTable>()
        val skipped = ArrayList<Int>()
        for (page in pages) {
            val t = runCatching { parsePage(page) }.getOrNull()
            if (t == null) skipped.add(page.number) else tables.add(t)
        }
        return ParsedDocument(tables, skipped)
    }

    fun parsePage(page: PdfPage): ParsedTable? {
        if (page.words.isEmpty()) return null
        val size = page.words.map { it.size }.sorted().let { it[it.size / 2] }
        val rows = buildRows(page.words, size)
        val lineIndex = rows.indexOfFirst { fold(it.text).split(' ').contains("linia") }
        if (lineIndex < 0) return null
        val labelIndex = rows.indexOfFirst { r -> r.words.any { isLabel(it) } }
        val firstTimeRow = rows.indexOfFirst { r -> r.words.count { time.matches(it.text) } >= 2 }
        if (firstTimeRow < 0) return null
        val bodyStart = if (labelIndex in (lineIndex + 1)..firstTimeRow) labelIndex else firstTimeRow

        val headerRows = rows.subList(lineIndex, bodyStart)
        val headerText = headerRows.joinToString(" ") { it.text }
        val line = (lineHeader.find(headerText) ?: lineHeaderLoose.find(headerText))?.groupValues?.get(1)
            ?: return null
        val validityMatch = validityPattern.find(headerText)
        val validity = validityMatch?.groupValues?.get(1)?.trim()
        val title = headerRows.drop(1)
            .filterNot { r -> validityPattern.containsMatchIn(r.text) || r.words.all { isLabel(it) } }
            .joinToString(" ") { it.text }
            .let { validityPattern.replace(it, "") }
            .replace(Regex("\\s+"), " ")
            .trim()

        val body = rows.subList(bodyStart, rows.size)
        val candidates = body.filter { r ->
            val times = r.words.filter { time.matches(it.text) }
            if (times.size < 2) return@filter false
            val firstTimeX = times.minOf { it.x0 }
            r.words.all { w -> time.matches(w.text) || dash.matches(w.text) || w.x1 <= firstTimeX || suffix.matches(w.text) }
        }
        if (candidates.isEmpty()) return null
        val nameEdge = candidates.minOf { r ->
            r.words.filter { time.matches(it.text) }.minOf { it.x0 }
        } - size * 0.3f

        val kinds = body.map { classify(it, nameEdge) }.toMutableList()
        val lastData = kinds.indexOfLast { it == Kind.DATA }
        if (lastData < 0) return null
        val firstNote = kinds.withIndex().firstOrNull { (i, k) -> k == Kind.NOTE && i > lastData }?.index
        if (firstNote != null) {
            for (i in firstNote until kinds.size) if (kinds[i] != Kind.EMPTY) kinds[i] = Kind.NOTE
        }

        val timeTokens = body.withIndex().filter { kinds[it.index] == Kind.DATA }
            .flatMap { (_, r) -> r.words.filter { it.x0 >= nameEdge && time.matches(it.text) } }
        val dataRows = body.withIndex().filter { kinds[it.index] == Kind.DATA }.map { it.value }
        val centers = columnCenters(dataRows, nameEdge, timeTokens)
        fun columnOf(x: Float): Int = centers.indices.minBy { abs(centers[it] - x) }

        val columnFlags = Array(centers.size) { StringBuilder() }
        val firstData = kinds.indexOf(Kind.DATA)
        for (i in 0 until firstData) {
            if (kinds[i] != Kind.FLAGS && kinds[i] != Kind.LABEL) continue
            for (w in body[i].words) {
                if (isLabel(w) || !flag.matches(w.text) || w.x0 < nameEdge - size) continue
                val col = columnOf(w.cx)
                if (abs(centers[col] - w.cx) <= size * 2.5f) columnFlags[col].append(w.text)
            }
        }

        val used = HashSet<Int>()
        val parsedRows = ArrayList<ParsedRow>()
        for ((i, r) in body.withIndex()) {
            if (kinds[i] != Kind.DATA) continue
            var name = nameOf(r, nameEdge)
            if (name.isEmpty()) {
                val parts = ArrayList<String>()
                val above = i - 1
                val below = i + 1
                if (above >= 0 && kinds[above] == Kind.NAME && above !in used &&
                    r.baseline - body[above].baseline < size * 2.8f
                ) {
                    parts.add(nameOf(body[above], nameEdge)); used.add(above)
                }
                if (below < body.size && kinds[below] == Kind.NAME && below !in used &&
                    body[below].baseline - r.baseline < size * 2.8f
                ) {
                    parts.add(nameOf(body[below], nameEdge)); used.add(below)
                }
                name = parts.joinToString(" ")
            }
            if (name.isEmpty()) continue
            val cells = arrayOfNulls<ParsedCell>(centers.size)
            var lastCol = -1
            var lastX1 = 0f
            for (w in r.words.filter { it.x0 >= nameEdge }.sortedBy { it.x0 }) {
                val m = time.matchEntire(w.text)
                if (m != null) {
                    val col = columnOf(w.cx)
                    val minute = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
                    if (cells[col] == null) cells[col] = ParsedCell(minute, m.groupValues[3])
                    lastCol = col
                    lastX1 = w.x1
                } else if (suffix.matches(w.text) && lastCol >= 0 && w.x0 - lastX1 < size * 1.6f) {
                    val c = cells[lastCol]!!
                    cells[lastCol] = c.copy(flags = c.flags + w.text)
                    lastCol = -1
                } else {
                    lastCol = -1
                }
            }
            parsedRows.add(ParsedRow(name, cells.toList()))
        }

        val footnotes = body.withIndex()
            .filter { kinds[it.index] == Kind.NOTE }
            .flatMap { segments(it.value, size) }
        val legend = LinkedHashMap<String, String>()
        for (segment in footnotes) {
            for (part in segment.split(legendSplit)) {
                val m = legendEntry.matchEntire(part.trim()) ?: continue
                val letter = m.groupValues[1].uppercase()
                if (letter !in legend) legend[letter] = m.groupValues[2].trim()
            }
        }

        return ParsedTable(
            page = page.number,
            line = line,
            title = title,
            validity = validity,
            columnFlags = columnFlags.map { it.toString() },
            rows = parsedRows,
            legend = legend,
            footnotes = footnotes,
        )
    }

    private fun isLabelText(t: String): Boolean = fold(t) in labels

    private fun isLabel(w: PdfWord): Boolean = isLabelText(w.text)

    private fun buildRows(words: List<PdfWord>, size: Float): List<Row> {
        val tol = size * 0.45f
        val rows = ArrayList<Row>()
        for (w in words.sortedWith(compareBy({ it.baseline }, { it.x0 }))) {
            val r = rows.lastOrNull()
            if (r != null && abs(w.baseline - r.baseline) <= tol) {
                r.words.add(w)
                r.baseline = r.words.map { it.baseline }.average().toFloat()
            } else {
                rows.add(Row(mutableListOf(w)))
            }
        }
        rows.forEach { r -> r.words.sortBy { it.x0 } }
        return rows
    }

    private fun classify(r: Row, nameEdge: Float): Kind {
        val words = r.words.filterNot { isLabel(it) }
        if (words.isEmpty()) return if (r.words.isEmpty()) Kind.EMPTY else Kind.LABEL
        val right = words.filter { it.x1 > nameEdge }
        val left = words.filter { it.x1 <= nameEdge }
        val leftText = left.joinToString(" ") { it.text }
        val folded = fold(leftText)
        if (right.isNotEmpty() && right.all { flag.matches(it.text) } && left.isEmpty()) return Kind.FLAGS
        val rightOk = right.all {
            time.matches(it.text) || dash.matches(it.text) || suffix.matches(it.text) || malformed.matches(it.text)
        }
        val hasTime = right.any { time.matches(it.text) && it.x0 >= nameEdge }
        if (hasTime && rightOk) return Kind.DATA
        if (right.isEmpty() || right.all { dash.matches(it.text) }) {
            if (legendEntry.matches(leftText.trim()) || folded.startsWith("legenda") ||
                folded.contains("kursuj") || folded.contains("obowiazuj")
            ) return Kind.NOTE
            return if (right.isEmpty()) Kind.NAME else Kind.EMPTY
        }
        return Kind.NOTE
    }

    private fun nameOf(r: Row, nameEdge: Float): String =
        r.words.filter { it.x1 <= nameEdge && !isLabel(it) }.joinToString(" ") { it.text }.trim()

    private fun columnCenters(rows: List<Row>, nameEdge: Float, tokens: List<PdfWord>): List<Float> {
        val widths = tokens.map { it.x1 - it.x0 }.sorted()
        val w = widths[widths.size / 2]
        val clusters = ArrayList<MutableList<Float>>()
        for (x in tokens.map { it.cx }.sorted()) {
            val c = clusters.lastOrNull()
            if (c != null && x - c.last() <= w * 0.45f) c.add(x) else clusters.add(mutableListOf(x))
        }
        val centers = clusters.map { it.average().toFloat() }.toMutableList()
        val rowXs = rows.map { r -> r.words.filter { it.x0 >= nameEdge && time.matches(it.text) }.map { it.cx } }
        fun nearest(x: Float) = centers.indices.minBy { abs(centers[it] - x) }
        var merged = true
        while (merged && centers.size > 1) {
            merged = false
            for (i in 0 until centers.size - 1) {
                if (centers[i + 1] - centers[i] > w * 0.9f) continue
                val clash = rowXs.any { xs ->
                    val cols = xs.map(::nearest)
                    i in cols && (i + 1) in cols
                }
                if (!clash) {
                    centers[i] = (centers[i] + centers[i + 1]) / 2
                    centers.removeAt(i + 1)
                    merged = true
                    break
                }
            }
        }
        return centers
    }

    private fun segments(r: Row, size: Float): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var lastX1 = Float.NaN
        for (w in r.words) {
            if (sb.isNotEmpty() && w.x0 - lastX1 > size * 2.5f) {
                out.add(sb.toString().trim()); sb.setLength(0)
            }
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(w.text)
            lastX1 = w.x1
        }
        if (sb.isNotEmpty()) out.add(sb.toString().trim())
        return out.filter { it.isNotEmpty() }
    }
}
