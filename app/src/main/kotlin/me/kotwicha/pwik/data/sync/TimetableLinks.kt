package me.kotwicha.pwik.data.sync

import java.net.URI
import java.net.URLDecoder
import java.time.LocalDate
import me.kotwicha.pwik.core.importer.SourceInfo
import me.kotwicha.pwik.core.text.fold
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

data class PdfLink(val url: String, val text: String, val score: Int, val date: LocalDate?)

object TimetableLinks {
    const val PAGE_URL = "https://pwik-jaroslaw.pl/rozklad-jazdy/"

    private val viewerParams = listOf("file", "url", "src", "pdf", "doc")
    private val negative = listOf("taryf", "cennik", "regulamin", "przetarg", "ogloszen", "sprawozd", "rodo", "deklarac", "wniosek", "umowa")
    private val contentSelectors = listOf("main", "article", ".entry-content", "#content", ".content", ".post", ".page")

    fun find(html: String, baseUrl: String = PAGE_URL): List<PdfLink> {
        val doc = Jsoup.parse(html, baseUrl)
        val content = contentSelectors.flatMap { doc.select(it) }.toSet()
        val seen = HashMap<String, PdfLink>()
        val sources = buildList {
            doc.select("a[href]").forEach { add(it to it.absUrl("href")) }
            doc.select("iframe[src], embed[src], source[src]").forEach { add(it to it.absUrl("src")) }
            doc.select("object[data]").forEach { add(it to it.absUrl("data")) }
            doc.select("[data-pdf-url], [data-url], [data-src], [data-file]").forEach { e ->
                listOf("data-pdf-url", "data-url", "data-src", "data-file").forEach { a ->
                    if (e.hasAttr(a)) add(e to e.absUrl(a).ifEmpty { e.attr(a) })
                }
            }
        }
        for ((el, raw) in sources) {
            val url = unwrap(raw) ?: continue
            if (!isPdf(url)) continue
            val text = (el.text().ifBlank { el.attr("title") }.ifBlank { el.attr("aria-label") }).trim()
            val score = score(url, text, el, content)
            val date = SourceInfo.dateIn(SourceInfo.fileName(url)) ?: SourceInfo.dateIn(text)
            val prev = seen[url]
            if (prev == null || prev.score < score) seen[url] = PdfLink(url, text, score, date)
        }
        return seen.values.sortedWith(
            compareByDescending<PdfLink> { it.score }.thenByDescending { it.date ?: LocalDate.MIN }
        )
    }

    fun subpages(html: String, baseUrl: String = PAGE_URL): List<String> {
        val doc = Jsoup.parse(html, baseUrl)
        val host = runCatching { URI(baseUrl).host }.getOrNull()
        return doc.select("a[href]").mapNotNull { a ->
            val url = a.absUrl("href")
            val label = fold(a.text() + " " + url)
            val sameHost = runCatching { URI(url).host == host }.getOrDefault(false)
            if (sameHost && url != baseUrl && !isPdf(url) && label.contains("rozk")) url else null
        }.distinct().take(5)
    }

    private fun unwrap(url: String): String? {
        if (url.isBlank()) return null
        val query = url.substringAfter('?', "")
        if (query.isNotEmpty()) {
            for (pair in query.split('&')) {
                val k = pair.substringBefore('=').lowercase()
                val v = pair.substringAfter('=', "")
                if (k in viewerParams && v.isNotEmpty()) {
                    val decoded = runCatching { URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)
                    if (isPdf(decoded)) return decoded
                }
            }
        }
        return url
    }

    private fun isPdf(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return path.endsWith(".pdf") || path.contains(".pdf/")
    }

    private fun score(url: String, text: String, el: Element, content: Set<Element>): Int {
        val hay = fold(SourceInfo.fileName(url) + " " + text)
        var s = 10
        if (hay.contains("rozk")) s += 60
        if (hay.contains("jazd")) s += 15
        if (hay.contains("linia") || hay.contains("linii") || hay.contains("autobus") || hay.contains("komunikac")) s += 10
        if (hay.contains("wszystkich") || hay.contains("aktualn") || hay.contains("zbiorcz")) s += 15
        if (negative.any { hay.contains(it) }) s -= 50
        if (el.parents().any { it in content }) s += 10
        if (url.contains("/wp-content/uploads/")) s += 5
        return s
    }
}
