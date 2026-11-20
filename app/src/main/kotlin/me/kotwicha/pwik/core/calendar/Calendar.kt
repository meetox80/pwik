package me.kotwicha.pwik.core.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class DayKind(val bit: Int) { WORKDAY(1), SATURDAY(2), SUNDAY_HOLIDAY(4) }

const val ALL_DAYS = 7

enum class SpecialDate(val bit: Int) {
    EASTER_SUNDAY(1),
    EASTER_MONDAY(2),
    CHRISTMAS_EVE(4),
    CHRISTMAS(8),
    SECOND_CHRISTMAS(16),
    NEW_YEAR(32),
    NEW_YEARS_EVE(64),
    ALL_SAINTS(128);

    fun matches(date: LocalDate, easter: LocalDate): Boolean = when (this) {
        EASTER_SUNDAY -> date == easter
        EASTER_MONDAY -> date == easter.plusDays(1)
        CHRISTMAS_EVE -> date.monthValue == 12 && date.dayOfMonth == 24
        CHRISTMAS -> date.monthValue == 12 && date.dayOfMonth == 25
        SECOND_CHRISTMAS -> date.monthValue == 12 && date.dayOfMonth == 26
        NEW_YEAR -> date.monthValue == 1 && date.dayOfMonth == 1
        NEW_YEARS_EVE -> date.monthValue == 12 && date.dayOfMonth == 31
        ALL_SAINTS -> date.monthValue == 11 && date.dayOfMonth == 1
    }
}

data class DateSpan(val start: LocalDate, val end: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

object PolishCalendar {
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }

    fun holidays(year: Int): Set<LocalDate> {
        val easter = easter(year)
        val fixed = buildList {
            add(LocalDate.of(year, 1, 1))
            add(LocalDate.of(year, 1, 6))
            add(LocalDate.of(year, 5, 1))
            add(LocalDate.of(year, 5, 3))
            add(LocalDate.of(year, 8, 15))
            add(LocalDate.of(year, 11, 1))
            add(LocalDate.of(year, 11, 11))
            if (year >= 2025) add(LocalDate.of(year, 12, 24))
            add(LocalDate.of(year, 12, 25))
            add(LocalDate.of(year, 12, 26))
        }
        return (fixed + listOf(easter, easter.plusDays(1), easter.plusDays(49), easter.plusDays(60))).toSet()
    }

    fun specialDates(date: LocalDate): Int {
        val easter = easter(date.year)
        return SpecialDate.entries.filter { it.matches(date, easter) }.fold(0) { acc, s -> acc or s.bit }
    }
}

class SchoolCalendar(private val winterBreaks: List<DateSpan>) {

    fun classesEnd(year: Int): LocalDate =
        LocalDate.of(year, 6, 20).with(TemporalAdjusters.next(DayOfWeek.FRIDAY))

    fun classesStart(year: Int): LocalDate {
        val first = LocalDate.of(year, 9, 1)
        return when (first.dayOfWeek) {
            DayOfWeek.FRIDAY, DayOfWeek.SATURDAY -> first.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            DayOfWeek.SUNDAY -> first.plusDays(1)
            else -> first
        }
    }

    fun isSummerHoliday(date: LocalDate): Boolean =
        date.isAfter(classesEnd(date.year)) && !date.isAfter(LocalDate.of(date.year, 8, 31))

    fun winterBreak(date: LocalDate): DateSpan? = winterBreaks.firstOrNull { date in it }

    fun isSchoolDay(date: LocalDate, holidays: Set<LocalDate>): Boolean {
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) return false
        if (date in holidays) return false
        val year = date.year
        if (date.isAfter(classesEnd(year)) && date.isBefore(classesStart(year))) return false
        if (date.monthValue == 12 && date.dayOfMonth >= 23) return false
        val easter = PolishCalendar.easter(year)
        if (date in DateSpan(easter.minusDays(3), easter.plusDays(2))) return false
        if (winterBreak(date) != null) return false
        return true
    }

    companion object {
        val PODKARPACKIE_WINTER_BREAKS = listOf(
            DateSpan(LocalDate.of(2026, 2, 16), LocalDate.of(2026, 3, 1)),
            DateSpan(LocalDate.of(2027, 1, 18), LocalDate.of(2027, 1, 31)),
            DateSpan(LocalDate.of(2028, 1, 31), LocalDate.of(2028, 2, 13)),
        )
    }
}

data class DayInfo(
    val date: LocalDate,
    val kind: DayKind,
    val schoolDay: Boolean,
    val summerHoliday: Boolean,
    val special: Int,
) {
    val monthDay: Int get() = date.monthValue * 100 + date.dayOfMonth
}

class DayResolver(private val school: SchoolCalendar) {
    private val holidayCache = HashMap<Int, Set<LocalDate>>()

    private fun holidays(year: Int) = holidayCache.getOrPut(year) { PolishCalendar.holidays(year) }

    fun info(date: LocalDate): DayInfo {
        val hol = holidays(date.year)
        val kind = when {
            date in hol || date.dayOfWeek == DayOfWeek.SUNDAY -> DayKind.SUNDAY_HOLIDAY
            date.dayOfWeek == DayOfWeek.SATURDAY -> DayKind.SATURDAY
            else -> DayKind.WORKDAY
        }
        return DayInfo(
            date = date,
            kind = kind,
            schoolDay = school.isSchoolDay(date, hol),
            summerHoliday = school.isSummerHoliday(date),
            special = PolishCalendar.specialDates(date),
        )
    }
}

data class Service(
    val days: Int = ALL_DAYS,
    val schoolDaysOnly: Boolean = false,
    val notInSummerHolidays: Boolean = false,
    val onlyInSummerHolidays: Boolean = false,
    val seasonStart: Int = 0,
    val seasonEnd: Int = 0,
    val closedOn: Int = 0,
    val cutoffOn: Int = 0,
    val cutoffMinute: Int = 0,
) {
    fun runsOn(day: DayInfo): Boolean {
        if (days and day.kind.bit == 0) return false
        if (schoolDaysOnly && !day.schoolDay) return false
        if (notInSummerHolidays && day.summerHoliday) return false
        if (onlyInSummerHolidays && !day.summerHoliday) return false
        if (seasonStart != 0 && seasonEnd != 0) {
            val md = day.monthDay
            val inSeason = if (seasonStart <= seasonEnd) md in seasonStart..seasonEnd
            else md >= seasonStart || md <= seasonEnd
            if (!inSeason) return false
        }
        if (closedOn and day.special != 0) return false
        return true
    }

    fun allowsDeparture(day: DayInfo, minute: Int): Boolean =
        cutoffOn and day.special == 0 || minute <= cutoffMinute

    fun and(other: Service): Service = Service(
        days = days and other.days,
        schoolDaysOnly = schoolDaysOnly || other.schoolDaysOnly,
        notInSummerHolidays = notInSummerHolidays || other.notInSummerHolidays,
        onlyInSummerHolidays = onlyInSummerHolidays || other.onlyInSummerHolidays,
        seasonStart = if (other.seasonStart != 0) other.seasonStart else seasonStart,
        seasonEnd = if (other.seasonEnd != 0) other.seasonEnd else seasonEnd,
        closedOn = closedOn or other.closedOn,
        cutoffOn = cutoffOn or other.cutoffOn,
        cutoffMinute = if (other.cutoffOn != 0) other.cutoffMinute else cutoffMinute,
    )
}
