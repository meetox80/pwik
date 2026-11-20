package me.kotwicha.pwik

import java.time.LocalDate
import me.kotwicha.pwik.core.calendar.DayKind
import me.kotwicha.pwik.core.calendar.DayResolver
import me.kotwicha.pwik.core.calendar.PolishCalendar
import me.kotwicha.pwik.core.calendar.SchoolCalendar
import me.kotwicha.pwik.core.geo.metersBetween
import me.kotwicha.pwik.core.geo.pathLength
import me.kotwicha.pwik.core.routing.Endpoint
import me.kotwicha.pwik.core.routing.Leg
import me.kotwicha.pwik.core.routing.Preference
import me.kotwicha.pwik.core.routing.rankedBy
import me.kotwicha.pwik.core.routing.Router
import me.kotwicha.pwik.core.routing.RouterConfig
import me.kotwicha.pwik.core.timetable.ServiceDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val resolver = DayResolver(SchoolCalendar(SchoolCalendar.PODKARPACKIE_WINTER_BREAKS))

class CalendarTest {
    @Test
    fun easterAndHolidays() {
        assertEquals(LocalDate.of(2026, 4, 5), PolishCalendar.easter(2026))
        assertEquals(LocalDate.of(2027, 3, 28), PolishCalendar.easter(2027))
        val h = PolishCalendar.holidays(2026)
        assertTrue(LocalDate.of(2026, 11, 11) in h)
        assertTrue(LocalDate.of(2026, 6, 4) in h)
        assertTrue(LocalDate.of(2026, 12, 24) in h)
        assertFalse(LocalDate.of(2026, 5, 25) in h)
    }

    @Test
    fun dayKinds() {
        assertEquals(DayKind.WORKDAY, resolver.info(LocalDate.of(2026, 9, 10)).kind)
        assertEquals(DayKind.SATURDAY, resolver.info(LocalDate.of(2026, 9, 12)).kind)
        assertEquals(DayKind.SUNDAY_HOLIDAY, resolver.info(LocalDate.of(2026, 9, 13)).kind)
        assertEquals(DayKind.SUNDAY_HOLIDAY, resolver.info(LocalDate.of(2026, 11, 11)).kind)
    }

    @Test
    fun schoolYearFollowsTheRegulation() {
        val school = SchoolCalendar(SchoolCalendar.PODKARPACKIE_WINTER_BREAKS)
        assertEquals(LocalDate.of(2027, 6, 25), school.classesEnd(2027))
        assertEquals(LocalDate.of(2026, 6, 26), school.classesEnd(2026))
        assertTrue(resolver.info(LocalDate.of(2027, 6, 25)).schoolDay)
        assertFalse(resolver.info(LocalDate.of(2027, 6, 28)).schoolDay)
        assertTrue(resolver.info(LocalDate.of(2027, 6, 28)).summerHoliday)
        assertTrue(resolver.info(LocalDate.of(2026, 9, 1)).schoolDay)
        assertFalse(resolver.info(LocalDate.of(2026, 12, 23)).schoolDay)
        assertFalse(resolver.info(LocalDate.of(2027, 1, 20)).schoolDay)
        assertTrue(resolver.info(LocalDate.of(2027, 2, 2)).schoolDay)
        assertFalse(resolver.info(LocalDate.of(2027, 3, 25)).schoolDay)
        assertFalse(resolver.info(LocalDate.of(2027, 3, 30)).schoolDay)
        assertTrue(resolver.info(LocalDate.of(2027, 3, 31)).schoolDay)
    }
}

class FeedTest {
    private val feed = TestData.feed
    private val net = TestData.network

    @Test
    fun everyRowFindsItsStop() {
        val missing = feed.warnings.filter { it.contains("nie znaleziono") }
        assertTrue(missing.toString(), missing.isEmpty())
        assertTrue(feed.warnings.toString(), feed.warnings.none { it.contains("cofa się") })
        assertEquals(listOf("0", "8", "9", "10", "14", "15", "16"), net.lines.map { it.name })
    }

    @Test
    fun stopVariantsCollapseIntoOneStop() {
        val names = net.stops.map { it.name }
        assertEquals(names.size, names.toSet().size)
        assertTrue("Jana Pawła II Starostwo" in names)
        assertFalse("Jan Pawła II" in names)
        assertTrue(net.stops.single { it.name == "Jana Pawła II Starostwo" }.aliases.contains("Jan Pawła II"))
    }

    @Test
    fun tripsAreTimeOrdered() {
        for (t in net.trips) {
            val served = t.times.filter { it >= 0 }
            assertEquals(served.sorted(), served)
            assertTrue(served.size >= 2)
        }
        assertTrue(net.trips.size >= 233)
    }

    @Test
    fun knownDepartureExists() {
        val s = net.stops.single { it.name == "Piłsudskiego" }
        val day = ServiceDay(net, resolver.info(LocalDate.of(2026, 9, 10)))
        val deps = day.departures(s.index, 4 * 60, 3)
        assertEquals(4 * 60 + 35, deps.first().minute)
        assertEquals("0", deps.first().line.name)
    }

    @Test
    fun opposingDirectionsUseOppositePlatforms() {
        val komb = net.stops.single { it.name == "Kombatantów" }
        val used = net.patterns.mapNotNull { p ->
            val i = p.stops.indexOf(komb.index)
            if (i < 0) null else p.platforms[i]
        }.toSet()
        assertEquals(setOf(0, 1), used)
    }

    @Test
    fun conditionalStopsDependOnTheSeason() {
        val osada = net.stops.single { it.name == "Osada Świetlica" }
        val schoolDay = ServiceDay(net, resolver.info(LocalDate.of(2026, 10, 7)))
        val summer = ServiceDay(net, resolver.info(LocalDate.of(2027, 7, 7)))
        assertTrue(schoolDay.departures(osada.index, 0, 20).isNotEmpty())
        assertTrue(summer.departures(osada.index, 0, 20).isEmpty())
        val kostkow = net.stops.single { it.name == "Kostków Pętla" }
        assertTrue(summer.departures(kostkow.index, 0, 20).isNotEmpty())
    }

    @Test
    fun line14SummerExtensionRunsOnlyInSeason() {
        val huta = net.stops.single { it.name == "Huta" }
        fun line14(date: LocalDate) = ServiceDay(net, resolver.info(date)).departures(huta.index, 0, 200)
            .filter { it.line.name == "14" && it.minute == 9 * 60 + 30 }
        assertTrue(line14(LocalDate.of(2027, 6, 2)).isNotEmpty())
        assertTrue(line14(LocalDate.of(2026, 10, 7)).isEmpty())
    }

    @Test
    fun christmasAndEasterRules() {
        val centrum = net.stops.single { it.name == "Centrum Przesiadkowe" }
        val christmas = ServiceDay(net, resolver.info(LocalDate.of(2026, 12, 25)))
        assertTrue(christmas.departures(centrum.index, 0, 50).none { it.line.name == "0" })
        val eve = ServiceDay(net, resolver.info(LocalDate.of(2026, 12, 24)))
        val line0 = eve.departures(centrum.index, 0, 200).filter { it.line.name == "0" }
        assertTrue(line0.isNotEmpty())
        assertTrue(line0.all { it.trip.times[it.trip.first] <= 16 * 60 + 30 })
    }

    @Test
    fun shapesFollowStreets() {
        var routed = 0
        for (p in net.patterns) {
            assertTrue(p.hasShape)
            val stopsOnly = pathLength(p.stops.indices.map { net.platformPoint(p.index, it) })
            if (pathLength(p.shape) > stopsOnly * 1.02) routed++
        }
        assertTrue("trasy po ulicach: $routed z ${net.patterns.size}", routed >= net.patterns.size * 0.8)
    }
}

class RouterTest {
    private val net = TestData.network
    private val router = Router(net, RouterConfig(), TestData.walkGraph)
    private val weekday = ServiceDay(net, resolver.info(LocalDate.of(2026, 9, 10)))

    private fun stop(name: String) = Endpoint.AtStop(net.stops.single { it.name == name }.index)

    @Test
    fun directRideOnLineZero() {
        val js = router.plan(stop("Piłsudskiego"), stop("P.Z.Z"), weekday, 4 * 60)
        val best = js.first { !it.walkOnly }
        assertEquals(listOf("0"), best.rides.map { it.line.name })
        assertEquals(4 * 60 + 59, best.arriveMinute)
        assertEquals(4 * 60 + 35, best.departMinute)
        assertTrue(js.count { !it.walkOnly } >= 3)
    }

    @Test
    fun legsAreContiguous() {
        val js = router.plan(stop("Piłsudskiego"), stop("Kostków Pętla"), weekday, 6 * 60)
        assertTrue(js.any { !it.walkOnly })
        for (j in js) {
            for (i in 1 until j.legs.size) {
                assertTrue("$j", j.legs[i].startMinute >= j.legs[i - 1].endMinute)
            }
        }
    }

    @Test
    fun fromAStreetPoint() {
        val centrum = net.stops.single { it.name == "Centrum Przesiadkowe" }
        val from = Endpoint.AtPoint(centrum.point.copy(lat = centrum.lat + 0.002, lon = centrum.lon + 0.002), "punkt")
        val js = router.plan(from, stop("Huta"), weekday, 7 * 60)
        val transit = js.first { !it.walkOnly }
        assertTrue(transit.walkMeters > 0)
        assertTrue(transit.legs.first() is Leg.Walk)
    }

    @Test
    fun noLine10OnSunday() {
        val sunday = ServiceDay(net, resolver.info(LocalDate.of(2026, 9, 13)))
        val js = router.plan(stop("Kostków Pętla"), stop("Centrum Przesiadkowe"), sunday, 6 * 60)
        assertTrue(js.none { j -> j.rides.any { it.line.name == "10" } })
    }

    @Test
    fun noDominatedOptions() {
        val js = router.plan(stop("Piłsudskiego"), stop("Huta"), weekday, 7 * 60)
        for (a in js) for (b in js) {
            if (a === b) continue
            val dominated = b.departMinute >= a.departMinute && b.arriveMinute <= a.arriveMinute &&
                b.walkMeters <= a.walkMeters && b.transfers <= a.transfers &&
                (b.departMinute > a.departMinute || b.arriveMinute < a.arriveMinute || b.walkMeters < a.walkMeters || b.transfers < a.transfers)
            assertFalse("$a zdominowana przez $b", dominated)
        }
    }

    @Test
    fun preferenceSorting() {
        val centrum = net.stops.single { it.name == "Centrum Przesiadkowe" }
        val js = router.plan(Endpoint.AtPoint(centrum.point, "centrum"), stop("Stawki"), weekday, 7 * 60)
        val fastest = js.rankedBy(Preference.FASTEST).first()
        val lazy = js.rankedBy(Preference.LEAST_WALKING).first()
        assertTrue(fastest.arriveMinute <= lazy.arriveMinute)
        assertTrue(lazy.walkMeters <= fastest.walkMeters)
    }

    @Test
    fun searchIsFast() {
        val centrum = net.stops.single { it.name == "Centrum Przesiadkowe" }
        val from = Endpoint.AtPoint(centrum.point, "centrum")
        router.plan(from, stop("Kostków Pętla"), weekday, 7 * 60)
        val t0 = System.nanoTime()
        repeat(20) { router.plan(from, stop("Kostków Pętla"), weekday, 7 * 60) }
        val ms = (System.nanoTime() - t0) / 1e6 / 20
        assertTrue("planowanie trwa $ms ms", ms < 300)
    }

    @Test
    fun walkOnlyForShortHops() {
        val a = net.stops.single { it.name == "Centrum Przesiadkowe" }
        val b = net.stops.single { it.name == "Poniatowskiego" }
        val js = router.plan(Endpoint.AtPoint(a.point, "A"), Endpoint.AtPoint(b.point, "B"), weekday, 12 * 60)
        assertTrue(js.any { it.walkOnly })
    }
}

class MapTest {
    @Test
    fun mapDecodesWithAllLayers() {
        val map = TestData.map
        assertTrue(map.layers[11].count > 10_000)
        assertTrue(map.layers[8].count > 10_000)
        assertTrue(map.layers[6].count > 100)
        assertTrue(map.bounds.contains(50.0167, 22.6786))
    }

    @Test
    fun streetSearch() {
        val s = TestData.streets
        assertTrue(s.search("grunwaldzka", 5).any { it.name == "Grunwaldzka" })
        assertTrue(s.search("3 maja", 5).any { it.name.contains("Maja") })
        assertTrue(s.search("slowack", 5).any { it.name.contains("Słowackiego") })
        assertTrue(s.search("zzzz", 5).isEmpty())
        val p = s.nearestPointOnStreet("Grunwaldzka", me.kotwicha.pwik.core.geo.LatLon(50.0167, 22.6786))
        assertNotNull(p)
    }

    @Test
    fun walkingDistanceIsRealistic() {
        val net = TestData.network
        val router = Router(net, RouterConfig(), TestData.walkGraph)
        val a = net.stops.single { it.name == "Centrum Przesiadkowe" }.point
        val b = net.stops.single { it.name == "Słowackiego" }.point
        val (m, path) = router.walkBetween(a, b)
        val crow = metersBetween(a, b)
        assertTrue("$m vs $crow", m >= crow && m < crow * 2.2 + 100)
        assertTrue(path.size > 2)
    }
}
