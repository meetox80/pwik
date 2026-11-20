package me.kotwicha.pwik.data.repo

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import me.kotwicha.pwik.core.calendar.Service
import me.kotwicha.pwik.core.geo.LatLon
import me.kotwicha.pwik.core.importer.Feed
import me.kotwicha.pwik.core.importer.FeedLine
import me.kotwicha.pwik.core.importer.FeedPattern
import me.kotwicha.pwik.core.importer.FeedStop
import me.kotwicha.pwik.core.importer.FeedTrip
import me.kotwicha.pwik.core.timetable.Platform
import me.kotwicha.pwik.data.db.FeedInfoEntity
import me.kotwicha.pwik.data.db.FeedRows
import me.kotwicha.pwik.data.db.LineEntity
import me.kotwicha.pwik.data.db.PatternEntity
import me.kotwicha.pwik.data.db.PatternStopEntity
import me.kotwicha.pwik.data.db.PlatformEntity
import me.kotwicha.pwik.data.db.ServiceEntity
import me.kotwicha.pwik.data.db.StopEntity
import me.kotwicha.pwik.data.db.StopTimeEntity
import me.kotwicha.pwik.data.db.TimetableDao
import me.kotwicha.pwik.data.db.TripEntity

object FeedStore {

    fun toRows(feed: Feed, info: FeedInfoEntity): FeedRows = FeedRows(
        info = info,
        stops = feed.stops.mapIndexed { i, s ->
            StopEntity(i, s.key, s.name, s.lat, s.lon, s.approximate, s.aliases.joinToString("\n"))
        },
        platforms = feed.stops.flatMapIndexed { i, s ->
            s.platforms.mapIndexed { j, p -> PlatformEntity(i, j, p.code, p.lat, p.lon) }
        },
        lines = feed.lines.mapIndexed { i, l -> LineEntity(i, l.name, l.title) },
        patterns = feed.patterns.mapIndexed { i, p -> PatternEntity(i, p.line, encode(p.shape)) },
        patternStops = feed.patterns.flatMapIndexed { i, p ->
            p.stops.indices.map { k ->
                PatternStopEntity(i, k, p.stops[k], p.platforms[k], p.shapeBreaks.getOrElse(k) { -1 })
            }
        },
        services = feed.services.mapIndexed { i, s ->
            ServiceEntity(
                i, s.days, s.schoolDaysOnly, s.notInSummerHolidays, s.onlyInSummerHolidays,
                s.seasonStart, s.seasonEnd, s.closedOn, s.cutoffOn, s.cutoffMinute,
            )
        },
        trips = feed.trips.mapIndexed { i, t -> TripEntity(i, t.pattern, t.service, t.note) },
        stopTimes = feed.trips.flatMapIndexed { i, t ->
            t.times.indices.filter { t.times[it] >= 0 }.map { k -> StopTimeEntity(i, k, t.times[k], t.conditions[k]) }
        },
    )

    suspend fun read(dao: TimetableDao): Feed {
        val platforms = dao.platforms().groupBy { it.stopId }
        val stops = dao.stops().map { s ->
            FeedStop(
                key = s.key,
                name = s.name,
                lat = s.lat,
                lon = s.lon,
                approximate = s.approximate,
                platforms = platforms[s.id].orEmpty().sortedBy { it.idx }.map { Platform(it.code, it.lat, it.lon) },
                aliases = s.aliases.split('\n').filter { it.isNotBlank() },
            )
        }
        val lines = dao.lines().map { FeedLine(it.name, it.title) }
        val patternStops = dao.patternStops().groupBy { it.patternId }
        val patterns = dao.patterns().map { p ->
            val ps = patternStops[p.id].orEmpty().sortedBy { it.seq }
            val breaks = ps.map { it.shapeBreak }.toIntArray()
            FeedPattern(
                line = p.lineId,
                stops = ps.map { it.stopId }.toIntArray(),
                platforms = ps.map { it.platform }.toIntArray(),
                shape = decode(p.shape),
                shapeBreaks = if (breaks.any { it < 0 }) IntArray(0) else breaks,
            )
        }
        val services = dao.services().map {
            Service(
                it.days, it.schoolDaysOnly, it.notInSummerHolidays, it.onlyInSummerHolidays,
                it.seasonStart, it.seasonEnd, it.closedOn, it.cutoffOn, it.cutoffMinute,
            )
        }
        val times = dao.stopTimes().groupBy { it.tripId }
        val trips = dao.trips().map { t ->
            val n = patterns[t.patternId].stops.size
            val arr = IntArray(n) { -1 }
            val cond = IntArray(n) { -1 }
            for (st in times[t.id].orEmpty()) {
                arr[st.seq] = st.minute
                cond[st.seq] = st.conditionId
            }
            FeedTrip(t.patternId, t.serviceId, arr, cond, t.note)
        }
        return Feed(stops, lines, patterns, services, trips, emptyList())
    }

    private fun encode(points: List<LatLon>): ByteArray {
        val buf = ByteBuffer.allocate(points.size * 8).order(ByteOrder.LITTLE_ENDIAN)
        for (p in points) {
            buf.putInt((p.lat * 1e6).roundToInt())
            buf.putInt((p.lon * 1e6).roundToInt())
        }
        return buf.array()
    }

    private fun decode(bytes: ByteArray): List<LatLon> {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return List(bytes.size / 8) { LatLon(buf.int / 1e6, buf.int / 1e6) }
    }
}
