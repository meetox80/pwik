package me.kotwicha.pwik.core.importer

import java.net.URLDecoder
import java.time.LocalDate

object SourceInfo {
    private val dmy = Regex("(\\d{1,2})[._-](\\d{1,2})[._-](20\\d{2})")
    private val ymd = Regex("(20\\d{2})[._-](\\d{1,2})[._-](\\d{1,2})")

    fun fileName(url: String): String {
        val path = url.substringBefore('?').substringBefore('#').trimEnd('/')
        return runCatching { URLDecoder.decode(path.substringAfterLast('/'), "UTF-8") }
            .getOrDefault(path.substringAfterLast('/'))
    }

    fun dateIn(text: String): LocalDate? {
        dmy.findAll(text).forEach { m ->
            val (d, mo, y) = m.destructured
            runCatching { return LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }
        }
        ymd.findAll(text).forEach { m ->
            val (y, mo, d) = m.destructured
            runCatching { return LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }
        }
        return null
    }
}
