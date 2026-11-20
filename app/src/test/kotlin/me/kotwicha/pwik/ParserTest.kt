package me.kotwicha.pwik

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.kotwicha.pwik.core.calendar.SpecialDate
import me.kotwicha.pwik.core.pdf.Legend
import me.kotwicha.pwik.core.pdf.Meaning
import me.kotwicha.pwik.core.text.fold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserTest {
    private val doc = TestData.legacyDocument

    private fun page(n: Int) = doc.tables.first { it.page == n }

    @Test
    fun everyTimetablePageIsParsed() {
        assertEquals((1..21).toList(), doc.tables.map { it.page })
        assertEquals(listOf("0", "8", "9", "10", "14", "15", "16"), doc.tables.map { it.line }.distinct())
    }

    @Test
    fun matchesTheReferenceParseCellByCell() {
        val reference = Json.parseToJsonElement(File("../data/timetable/timetable-raw.json").readText()).jsonArray
        for (ref in reference) {
            val o = ref.jsonObject
            val p = page(o["page"]!!.jsonPrimitive.int)
            assertEquals("kolumny na stronie ${p.page}", o["cols"]!!.jsonPrimitive.int, p.columns)
            val ours = p.rows.map { fold(it.name) to it }
            var cursor = 0
            for (s in o["stops"]!!.jsonArray) {
                val so = s.jsonObject
                val name = so["norm"]!!.jsonPrimitive.content
                val idx = (cursor until ours.size).firstOrNull { ours[it].first == name }
                    ?: error("strona ${p.page}: brak wiersza $name")
                cursor = idx + 1
                val row = ours[idx].second
                for ((col, dep) in so["dep"]!!.jsonObject) {
                    val t = dep.jsonObject["t"]!!.jsonPrimitive.content.split(":")
                    val minute = t[0].toInt() * 60 + t[1].toInt()
                    assertEquals("strona ${p.page} $name kolumna $col", minute, row.cells[col.toInt()]?.minute)
                }
                val refCount = so["dep"]!!.jsonObject.size
                assertEquals("strona ${p.page} $name liczba odjazdów", refCount, row.cells.count { it != null })
            }
        }
    }

    @Test
    fun rowsDroppedByTheOldParserAreRecovered() {
        val p11 = page(11)
        val osada = p11.rows.single { it.name == "Osada świetlica" }
        assertEquals(3, osada.cells.count { it != null })
        assertTrue(osada.cells.filterNotNull().all { it.flags == "w" })
        val oczyszczalnia = p11.rows.single { it.name == "Kostków oczyszczalnia" }
        assertEquals("w", oczyszczalnia.cells[1]?.flags)
    }

    @Test
    fun columnFlagsAreReadAboveTheColumns() {
        assertEquals(listOf("R", "R", "RX", "R", "R", "R", "R", "R", "R", "R", "R", "R"), page(5).columnFlags)
        assertEquals(listOf("S", "S", "N", "N"), page(15).columnFlags)
        assertEquals("XW", page(11).columnFlags[9])
        assertEquals("O", page(11).columnFlags[1])
    }

    @Test
    fun headersAreRead() {
        assertEquals("w dni robocze", page(1).validity)
        assertEquals("w dni wolne od pracy", page(3).validity)
        assertEquals("w soboty i niedziele", page(19).validity)
        assertTrue(page(1).title.contains("Piłsudskiego"))
        assertTrue(page(1).title.contains("Zbożowa"))
    }

    @Test
    fun legendsAreUnderstood() {
        val l11 = page(11).legend
        assertEquals(setOf("X", "W", "O"), l11.keys)
        val w11 = Legend.meaning(l11.getValue("W")) as Meaning.Rule
        assertTrue(w11.service.notInSummerHolidays)
        val x11 = Legend.meaning(l11.getValue("X")) as Meaning.Rule
        assertTrue(x11.service.schoolDaysOnly)
        assertTrue(Legend.meaning(l11.getValue("O")) is Meaning.Note)
        val w13 = Legend.meaning(page(13).legend.getValue("W")) as Meaning.Rule
        assertEquals(601, w13.service.seasonStart)
        assertEquals(930, w13.service.seasonEnd)
        val s15 = Legend.meaning(page(15).legend.getValue("S")) as Meaning.Rule
        assertEquals(2, s15.service.days)
    }

    @Test
    fun noticesBecomeServiceExceptions() {
        val n = Legend.notices(page(3).footnotes)
        val expected = SpecialDate.EASTER_SUNDAY.bit or SpecialDate.EASTER_MONDAY.bit or
            SpecialDate.CHRISTMAS.bit or SpecialDate.SECOND_CHRISTMAS.bit or SpecialDate.NEW_YEAR.bit
        assertEquals(expected, n.closedOn)
        assertEquals(SpecialDate.CHRISTMAS_EVE.bit, n.cutoffOn)
        assertEquals(16 * 60 + 30, n.cutoffMinute)
        assertEquals(0, Legend.notices(page(5).footnotes).closedOn)
    }
}
