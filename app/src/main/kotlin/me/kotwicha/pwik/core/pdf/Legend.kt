package me.kotwicha.pwik.core.pdf

import me.kotwicha.pwik.core.calendar.ALL_DAYS
import me.kotwicha.pwik.core.calendar.DayKind
import me.kotwicha.pwik.core.calendar.Service
import me.kotwicha.pwik.core.calendar.SpecialDate
import me.kotwicha.pwik.core.text.fold

sealed interface Meaning {
    data class Rule(val service: Service) : Meaning
    data class Note(val text: String) : Meaning
}

object Legend {

    private val months = listOf(
        "stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
        "lipca", "sierpnia", "wrzesnia", "pazdziernika", "listopada", "grudnia",
    )
    private val season =
        Regex("\\bod (\\d{1,2}) (${months.joinToString("|")}) do (\\d{1,2}) (${months.joinToString("|")})")
    private val clauseEnd = Regex(",\\s*(?=[A-ZĄĆĘŁŃÓŚŹŻ])|\\.\\s+")
    private val cutoff = Regex("(wigili\\w*|sylwest\\w*)(?: \\w+){0,4}? kursuj\\w* do (?:godz )?(\\d{1,2}) (\\d{2})")

    private const val WORKDAY = 1
    private const val SATURDAY = 2
    private const val SUNDAY = 4

    fun meaning(raw: String): Meaning {
        val clause = raw.split(clauseEnd).first().trim().trimEnd('.', ',', ';')
        val t = fold(clause)
        if (t.startsWith("do ")) return Meaning.Note(clause)
        var service = Service()
        var matched = false

        val days = dayMask(t)
        if (days != null) {
            service = service.copy(days = days)
            matched = true
        }
        if (t.contains("nauki szkoln") || t.contains("dni szkoln") || t.contains("dni nauki")) {
            service = service.copy(schoolDaysOnly = true, days = service.days and WORKDAY)
            matched = true
        }
        if (t.contains("kursuj") && (t.contains("wakacj") || t.contains("ferii letnich"))) {
            service = if (t.contains("nie kursuje") || t.contains("poza")) { //xd
                service.copy(notInSummerHolidays = true)
            } else {
                service.copy(onlyInSummerHolidays = true)
            }
            matched = true
        }
        season.find(t)?.let { m ->
            val (d1, m1, d2, m2) = m.destructured
            val start = (months.indexOf(m1) + 1) * 100 + d1.toInt()
            val end = (months.indexOf(m2) + 1) * 100 + d2.toInt()
            service = service.copy(seasonStart = start, seasonEnd = end)
            matched = true
        }
        return if (matched) Meaning.Rule(service) else Meaning.Note(clause)
    }

    fun validity(raw: String?): Service {
        if (raw == null) return Service()
        val t = fold(raw)
        return Service(days = dayMask(t) ?: ALL_DAYS)
    }

    private fun dayMask(t: String): Int? = when {
        t.contains("dni robocz") || t.contains("dni powszedn") || t.contains("poniedzialku do piatku") -> WORKDAY
        t.contains("dni wolne") || t.contains("dni swiateczne") -> SATURDAY or SUNDAY
        t.contains("sobot") && (t.contains("niedziel") || t.contains("swiet")) -> SATURDAY or SUNDAY
        t.contains("sobot") -> SATURDAY
        t.contains("niedziel") || t.contains("swieta") -> SUNDAY
        else -> null
    }

    fun defaultMeaning(letter: String): Meaning? = when (letter) {
        "R" -> Meaning.Rule(Service(days = DayKind.WORKDAY.bit))
        "S" -> Meaning.Rule(Service(days = DayKind.SATURDAY.bit))
        "N" -> Meaning.Rule(Service(days = DayKind.SUNDAY_HOLIDAY.bit))
        "X" -> Meaning.Rule(Service(days = DayKind.WORKDAY.bit, schoolDaysOnly = true))
        else -> null
    }

    fun notices(footnotes: List<String>): Service {
        var closed = 0
        var cutoffOn = 0
        var cutoffMinute = 0
        val text = fold(footnotes.joinToString(" . "))
        var idx = text.indexOf("nie kursuj")
        while (idx >= 0) {
            val phrase = text.substring(idx, minOf(text.length, idx + 200))
            closed = closed or specialsIn(phrase)
            idx = text.indexOf("nie kursuj", idx + 10)
        }
        cutoff.find(text)?.let { m ->
            cutoffOn = if (m.groupValues[1].startsWith("wigili")) SpecialDate.CHRISTMAS_EVE.bit
            else SpecialDate.NEW_YEARS_EVE.bit
            cutoffMinute = m.groupValues[2].toInt() * 60 + m.groupValues[3].toInt()
        }
        return Service(closedOn = closed, cutoffOn = cutoffOn, cutoffMinute = cutoffMinute)
    }

    private fun specialsIn(p: String): Int {
        var mask = 0
        if (p.contains("wielkanoc")) {
            if (p.contains("niedziel")) mask = mask or SpecialDate.EASTER_SUNDAY.bit
            if (p.contains("poniedzial")) mask = mask or SpecialDate.EASTER_MONDAY.bit
            if (!p.contains("niedziel") && !p.contains("poniedzial")) {
                mask = mask or SpecialDate.EASTER_SUNDAY.bit or SpecialDate.EASTER_MONDAY.bit
            }
        }
        if (p.contains("boze narodzenie") || p.contains("bozego narodzenia")) mask = mask or SpecialDate.CHRISTMAS.bit
        if (p.contains("szczepan") || p.contains("drugi dzien swiat")) mask = mask or SpecialDate.SECOND_CHRISTMAS.bit
        if (p.contains("nowy rok") || p.contains("nowego roku")) mask = mask or SpecialDate.NEW_YEAR.bit
        if (Regex("\\bwigili").containsMatchIn(p) && !Regex("wigili\\w*(?: \\w+){0,4}? kursuj\\w* do").containsMatchIn(p)) {
            mask = mask or SpecialDate.CHRISTMAS_EVE.bit
        }
        if (p.contains("sylwest") && !p.contains("kursuja do")) mask = mask or SpecialDate.NEW_YEARS_EVE.bit
        if (p.contains("wszystkich swietych")) mask = mask or SpecialDate.ALL_SAINTS.bit
        return mask
    }
}
