package me.kotwicha.pwik.core.timetable

import me.kotwicha.pwik.core.calendar.DayInfo
import me.kotwicha.pwik.core.calendar.Service
import me.kotwicha.pwik.core.geo.LatLon
import me.kotwicha.pwik.core.text.fold

class Platform(val code: String, val lat: Double, val lon: Double) {
    val point: LatLon get() = LatLon(lat, lon)
}

class Stop(
    val index: Int,
    val key: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val approximate: Boolean,
    val platforms: List<Platform>,
    val aliases: List<String>,
) {
    val point: LatLon get() = LatLon(lat, lon)
    val searchKeys: List<String> = (listOf(name) + aliases).map(::fold).distinct()
}

class Line(val index: Int, val name: String, val title: String)

class Pattern(
    val index: Int,
    val line: Int,
    val stops: IntArray,
    val platforms: IntArray,
    val shape: List<LatLon>,
    val shapeBreaks: IntArray,
) {
    val hasShape: Boolean get() = shape.size >= 2 && shapeBreaks.size == stops.size
}

class Trip(
    val index: Int,
    val pattern: Int,
    val service: Int,
    val times: IntArray,
    val conditions: IntArray,
    val note: String?,
) {
    val firstMinute: Int = times.first { it >= 0 }
}

class Network(
    val stops: List<Stop>,
    val lines: List<Line>,
    val patterns: List<Pattern>,
    val services: List<Service>,
    val trips: List<Trip>,
) {
    val patternsAtStop: Array<IntArray>
    val positionsAtStop: Array<IntArray>
    val tripsOfPattern: Array<IntArray>
    val linesAtStop: Array<List<Line>>
    private val stopsByKey = stops.associateBy { it.key }

    init {
        val pAcc = Array(stops.size) { ArrayList<Int>() }
        val posAcc = Array(stops.size) { ArrayList<Int>() }
        for (p in patterns) {
            for (i in p.stops.indices) {
                pAcc[p.stops[i]].add(p.index)
                posAcc[p.stops[i]].add(i)
            }
        }
        patternsAtStop = Array(stops.size) { pAcc[it].toIntArray() }
        positionsAtStop = Array(stops.size) { posAcc[it].toIntArray() }
        val tAcc = Array(patterns.size) { ArrayList<Int>() }
        for (t in trips) tAcc[t.pattern].add(t.index)
        tripsOfPattern = Array(patterns.size) { pi ->
            tAcc[pi].sortedBy { trips[it].firstMinute }.toIntArray()
        }
        linesAtStop = Array(stops.size) { s ->
            pAcc[s].map { patterns[it].line }.distinct().map { lines[it] }.sortedWith(lineOrder)
        }
    }

    fun stop(key: String): Stop? = stopsByKey[key]

    fun platformPoint(pattern: Int, position: Int): LatLon {
        val p = patterns[pattern]
        val s = stops[p.stops[position]]
        val pl = s.platforms.getOrNull(p.platforms[position])
        return pl?.point ?: s.point
    }

    fun lineOf(pattern: Int): Line = lines[patterns[pattern].line]

    fun shapeBetween(pattern: Int, from: Int, to: Int): List<LatLon> {
        val p = patterns[pattern]
        if (p.hasShape) {
            val a = p.shapeBreaks[from]
            val b = p.shapeBreaks[to]
            if (b > a) return p.shape.subList(a, b + 1)
        }
        return (from..to).map { platformPoint(pattern, it) }
    }

    fun fullShape(pattern: Int): List<LatLon> {
        val p = patterns[pattern]
        return if (p.hasShape) p.shape else p.stops.indices.map { platformPoint(pattern, it) }
    }

    companion object {
        val lineOrder: Comparator<Line> =
            compareBy<Line>({ it.name.toIntOrNull() ?: Int.MAX_VALUE }, { it.name })
    }
}

class ActiveTrip(val trip: Trip, val times: IntArray) {
    val first: Int = times.indexOfFirst { it >= 0 }
    val last: Int = times.indexOfLast { it >= 0 }
}

class ServiceDay(val network: Network, val day: DayInfo) {
    val tripsOfPattern: Array<List<ActiveTrip>> = Array(network.patterns.size) { pi ->
        network.tripsOfPattern[pi].toList().mapNotNull { ti -> activate(network.trips[ti]) }
            .filter { it.first >= 0 && it.last > it.first }
            .sortedBy { it.times[it.first] }
    }

    private fun activate(trip: Trip): ActiveTrip? {
        val service = network.services[trip.service]
        if (!service.runsOn(day)) return null
        if (!service.allowsDeparture(day, trip.firstMinute)) return null
        var times = trip.times
        for (i in trip.conditions.indices) {
            val c = trip.conditions[i]
            if (c < 0 || times[i] < 0) continue
            if (!network.services[c].runsOn(day)) {
                if (times === trip.times) times = trip.times.copyOf()
                times[i] = -1
            }
        }
        return ActiveTrip(trip, times)
    }

    fun headsign(active: ActiveTrip): Stop {
        val pattern = network.patterns[active.trip.pattern]
        return network.stops[pattern.stops[active.last]]
    }

    fun departures(stopIndex: Int, fromMinute: Int, limit: Int): List<Departure> {
        val out = ArrayList<Departure>()
        val ps = network.patternsAtStop[stopIndex]
        val pos = network.positionsAtStop[stopIndex]
        for (i in ps.indices) {
            val pattern = network.patterns[ps[i]]
            for (active in tripsOfPattern[pattern.index]) {
                val t = active.times[pos[i]]
                if (t < 0 || t < fromMinute || pos[i] >= active.last) continue
                out.add(
                    Departure(
                        minute = t,
                        line = network.lines[pattern.line],
                        headsign = headsign(active),
                        pattern = pattern.index,
                        position = pos[i],
                        trip = active,
                    )
                )
            }
        }
        out.sortWith(compareBy({ it.minute }, { it.line.name }))
        return out.take(limit)
    }
}

class Departure(
    val minute: Int,
    val line: Line,
    val headsign: Stop,
    val pattern: Int,
    val position: Int,
    val trip: ActiveTrip,
)
