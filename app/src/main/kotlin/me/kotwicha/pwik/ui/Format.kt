package me.kotwicha.pwik.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val polish = Locale.forLanguageTag("pl-PL")

fun hhmm(minute: Int): String {
    val m = ((minute % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val last = n % 10
    val teen = n % 100 in 12..14
    return when {
        n == 1 -> one
        !teen && last in 2..4 -> few
        else -> many
    }
}

fun duration(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    return if (m < 60) "$m min" else if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${m % 60} min"
}

fun countdown(minutes: Int): String = when {
    minutes <= 0 -> "teraz"
    minutes < 60 -> "za $minutes min"
    else -> "za ${duration(minutes)}"
}

fun distance(meters: Int): String =
    if (meters < 1000) "${(meters / 10) * 10} m" else String.format(polish, "%.1f km", meters / 1000.0)

fun stopsCount(n: Int): String = "$n ${plural(n, "przystanek", "przystanki", "przystanków")}"

fun transfers(n: Int): String = when (n) {
    0 -> "bez przesiadek"
    else -> "$n ${plural(n, "przesiadka", "przesiadki", "przesiadek")}"
}

private val shortDay = DateTimeFormatter.ofPattern("EEE, d MMM", polish)
private val longDay = DateTimeFormatter.ofPattern("d MMMM yyyy", polish)

fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Dziś"
    today.plusDays(1) -> "Jutro"
    today.minusDays(1) -> "Wczoraj"
    else -> date.format(shortDay)
}

fun longDate(date: LocalDate): String = date.format(longDay)

fun relativeDays(epochMs: Long, now: Long = System.currentTimeMillis()): String {
    val days = ((now - epochMs) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "dziś"
        days == 1 -> "wczoraj"
        else -> "$days ${plural(days, "dzień", "dni", "dni")} temu"
    }
}
