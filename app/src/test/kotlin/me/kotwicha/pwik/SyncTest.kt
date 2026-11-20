package me.kotwicha.pwik

import java.time.LocalDate
import me.kotwicha.pwik.core.importer.SourceInfo
import me.kotwicha.pwik.data.sync.TimetableLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTest {
    private val page = javaClass.getResource("/rozklad-jazdy.html")!!.readText()

    @Test
    fun picksTheCombinedTimetableFromTheRealPage() {
        val links = TimetableLinks.find(page, TimetableLinks.PAGE_URL)
        val best = links.first()
        assertEquals("https://pwik-jaroslaw.pl/wp-content/uploads/2026/10/Rozklad-01.11.2026.pdf", best.url)
        assertEquals(LocalDate.of(2026, 11, 1), best.date)
        assertTrue(links.size > 10)
        assertTrue(links.drop(1).all { it.score < best.score })
    }

    @Test
    fun unwrapsEmbeddedViewers() {
        val html = """
            <div class="entry-content">
              <iframe src="https://docs.google.com/viewer?url=https%3A%2F%2Fexample.org%2Fwp-content%2Fuploads%2FRozklad-02.02.2027.pdf&embedded=true"></iframe>
              <a href="/wp-content/uploads/cennik.pdf">Cennik biletów</a>
            </div>
        """.trimIndent()
        val links = TimetableLinks.find(html, "https://example.org/rozklad-jazdy/")
        assertEquals("https://example.org/wp-content/uploads/Rozklad-02.02.2027.pdf", links.first().url)
        assertEquals("https://example.org/wp-content/uploads/cennik.pdf", links.last().url)
    }

    @Test
    fun readsValidityFromFileNames() {
        assertEquals(LocalDate.of(2026, 6, 27), SourceInfo.dateIn("Rozklad-27.06.2026.pdf"))
        assertEquals(LocalDate.of(2027, 1, 4), SourceInfo.dateIn("rozklad_2027-01-04.pdf"))
        assertEquals("Rozklad-01.11.2026.pdf", SourceInfo.fileName("https://pwik-jaroslaw.pl/wp-content/uploads/2026/10/Rozklad-01.11.2026.pdf?x=1"))
    }
}
