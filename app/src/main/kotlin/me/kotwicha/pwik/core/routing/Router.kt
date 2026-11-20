package me.kotwicha.pwik.core.routing

import kotlin.math.ceil
import kotlin.math.roundToInt
import me.kotwicha.pwik.core.geo.LatLon
import me.kotwicha.pwik.core.geo.metersBetween
import me.kotwicha.pwik.core.map.StreetGraph
import me.kotwicha.pwik.core.timetable.ActiveTrip
import me.kotwicha.pwik.core.timetable.Line
import me.kotwicha.pwik.core.timetable.Network
import me.kotwicha.pwik.core.timetable.ServiceDay
import me.kotwicha.pwik.core.timetable.Stop

data class RouterConfig(
    val maxAccessWalkMeters: Double = 1200.0,
    val maxTransferWalkMeters: Double = 500.0,
    val walkSpeedKmh: Double = 4.6,
    val minTransferMinutes: Int = 2,
    val maxTransfers: Int = 2,
    val detourFactor: Double = 1.3,
)

sealed interface Endpoint {
    data class AtStop(val stop: Int) : Endpoint
    data class AtPoint(val point: LatLon, val label: String) : Endpoint
}

data class StopVisit(val stop: Stop, val minute: Int, val point: LatLon)

sealed interface Leg {
    val startMinute: Int
    val endMinute: Int

    data class Walk(
        val from: String,
        val to: String,
        val meters: Int,
        val minutes: Int,
        val path: List<LatLon>,
        override val startMinute: Int,
        override val endMinute: Int,
    ) : Leg

    data class Ride(
        val line: Line,
        val pattern: Int,
        val headsign: Stop,
        val visits: List<StopVisit>,
        val shape: List<LatLon>,
        val note: String?,
    ) : Leg {
        val from: StopVisit get() = visits.first()
        val to: StopVisit get() = visits.last()
        override val startMinute: Int get() = from.minute
        override val endMinute: Int get() = to.minute
        val intermediate: Int get() = visits.size - 2
    }
}

data class Journey(
    val legs: List<Leg>,
    val departMinute: Int,
    val arriveMinute: Int,
    val walkMeters: Int,
) {
    val rides: List<Leg.Ride> get() = legs.filterIsInstance<Leg.Ride>()
    val transfers: Int get() = (rides.size - 1).coerceAtLeast(0)
    val totalMinutes: Int get() = arriveMinute - departMinute
    val walkMinutes: Int get() = legs.filterIsInstance<Leg.Walk>().sumOf { it.minutes }
    val walkOnly: Boolean get() = rides.isEmpty()
    val signature: String
        get() = legs.joinToString("|") {
            when (it) {
                is Leg.Ride -> "${it.pattern}@${it.from.minute}>${it.to.stop.index}"
                is Leg.Walk -> "w${it.meters}"
            }
        }
}

enum class Preference { FASTEST, LEAST_WALKING, FEWEST_TRANSFERS }

class Router(
    private val network: Network,
    private val cfg: RouterConfig,
    private val walk: StreetGraph?,
) {
    private val stopNode = IntArray(network.stops.size) { i ->
        walk?.nearestNode(network.stops[i].lat, network.stops[i].lon) ?: -1
    }
    private val transferCache = HashMap<Int, List<Transfer>>()

    private class Transfer(val to: Int, val meters: Int, val path: List<LatLon>)

    private class Access(val meters: Map<Int, Int>, val paths: Map<Int, List<LatLon>>)

    private sealed interface Step {
        class Ride(val pattern: Int, val trip: ActiveTrip, val board: Int, val alight: Int) : Step
        class Walk(val from: String, val to: String, val meters: Int, val path: List<LatLon>) : Step
    }

    private class Label(
        val arr: Int,
        val walk: Int,
        val stop: Int,
        val byRide: Boolean,
        val parent: Label?,
        val step: Step?,
    )

    fun walkMinutes(meters: Int): Int = ceil(meters / (cfg.walkSpeedKmh * 1000.0 / 60.0)).toInt()

    private fun straight(a: LatLon, b: LatLon): Int = (metersBetween(a, b) * cfg.detourFactor).roundToInt()

    @Synchronized
    private fun transfersFrom(stop: Int): List<Transfer> = transferCache.getOrPut(stop) {
        val src = network.stops[stop]
        val g = walk
        val node = stopNode[stop]
        if (g == null || node < 0) {
            network.stops.filter { it.index != stop && metersBetween(src.point, it.point) <= cfg.maxTransferWalkMeters }
                .map { Transfer(it.index, straight(src.point, it.point), listOf(src.point, it.point)) }
        } else {
            val reach = g.explore(node, cfg.maxTransferWalkMeters)
            network.stops.mapNotNull { s ->
                if (s.index == stop) return@mapNotNull null
                val m = reach.metersTo(stopNode[s.index].takeIf { it >= 0 } ?: return@mapNotNull null)
                    ?: return@mapNotNull null
                Transfer(s.index, m.roundToInt(), listOf(src.point) + reach.pathTo(stopNode[s.index]) + s.point)
            }
        }
    }

    private fun access(e: Endpoint, maxMeters: Double): Access = when (e) {
        is Endpoint.AtStop -> {
            val meters = hashMapOf(e.stop to 0)
            val paths = HashMap<Int, List<LatLon>>()
            for (t in transfersFrom(e.stop)) {
                if (t.meters <= maxMeters) { meters[t.to] = t.meters; paths[t.to] = t.path }
            }
            Access(meters, paths)
        }
        is Endpoint.AtPoint -> {
            val g = walk
            val src = g?.nearestNode(e.point.lat, e.point.lon) ?: -1
            if (g == null || src < 0) {
                val meters = HashMap<Int, Int>()
                val paths = HashMap<Int, List<LatLon>>()
                for (s in network.stops) {
                    val m = straight(e.point, s.point)
                    if (m <= maxMeters) { meters[s.index] = m; paths[s.index] = listOf(e.point, s.point) }
                }
                Access(meters, paths)
            } else {
                val lead = metersBetween(e.point, g.point(src))
                val reach = g.explore(src, maxMeters)
                val meters = HashMap<Int, Int>()
                val paths = HashMap<Int, List<LatLon>>()
                for (s in network.stops) {
                    val node = stopNode[s.index]
                    if (node < 0) continue
                    val m = reach.metersTo(node) ?: continue
                    val total = (m + lead + metersBetween(g.point(node), s.point)).roundToInt()
                    if (total > maxMeters) continue
                    meters[s.index] = total
                    paths[s.index] = listOf(e.point) + reach.pathTo(node) + s.point
                }
                Access(meters, paths)
            }
        }
    }

    fun nearestStop(point: LatLon): Pair<Stop, Int>? {
        val g = walk
        if (g != null) {
            val src = g.nearestNode(point.lat, point.lon)
            if (src >= 0) {
                val reach = g.explore(src, 8000.0)
                val best = network.stops.mapNotNull { s ->
                    val node = stopNode[s.index]
                    if (node < 0) null else reach.metersTo(node)?.let { s to it.roundToInt() }
                }.minByOrNull { it.second }
                if (best != null) return best
            }
        }
        return network.stops.minByOrNull { metersBetween(point, it.point) }?.let { it to straight(point, it.point) }
    }

    fun walkBetween(a: LatLon, b: LatLon): Pair<Int, List<LatLon>> {
        val g = walk
        if (g != null) {
            val na = g.nearestNode(a.lat, a.lon)
            val nb = g.nearestNode(b.lat, b.lon)
            if (na >= 0 && nb >= 0) {
                val reach = g.explore(na, metersBetween(a, b) * 2.5 + 400)
                val m = reach.metersTo(nb)
                if (m != null) {
                    val total = m + metersBetween(a, g.point(na)) + metersBetween(g.point(nb), b)
                    return total.roundToInt() to (listOf(a) + reach.pathTo(nb) + b)
                }
            }
        }
        return straight(a, b) to listOf(a, b)
    }

    private fun pointOf(e: Endpoint): LatLon = when (e) {
        is Endpoint.AtStop -> network.stops[e.stop].point
        is Endpoint.AtPoint -> e.point
    }

    private fun labelOf(e: Endpoint): String = when (e) {
        is Endpoint.AtStop -> network.stops[e.stop].name
        is Endpoint.AtPoint -> e.label
    }

    fun plan(from: Endpoint, to: Endpoint, day: ServiceDay, departMinute: Int, wanted: Int = 6): List<Journey> {
        if (from is Endpoint.AtStop && to is Endpoint.AtStop && from.stop == to.stop) return emptyList()
        val accessFrom = access(from, cfg.maxAccessWalkMeters)
        val accessTo = access(to, cfg.maxAccessWalkMeters)
        val found = LinkedHashMap<String, Journey>()
        if (accessFrom.meters.isNotEmpty() && accessTo.meters.isNotEmpty()) {
            var t = departMinute
            for (iteration in 0 until wanted + 2) {
                val transit = raptor(from, to, accessFrom, accessTo, day, t).filter { it.rides.isNotEmpty() }
                if (transit.isEmpty()) break
                transit.forEach { found.putIfAbsent(it.signature, it) }
                t = maxOf(t + 1, transit.minOf { it.departMinute } + 1)
                if (found.size >= wanted * 2) break
            }
        }
        val a = pointOf(from)
        val b = pointOf(to)
        val (meters, path) = walkBetween(a, b)
        if (meters <= maxOf(cfg.maxAccessWalkMeters, 1500.0)) {
            val minutes = walkMinutes(meters)
            val w = Leg.Walk(labelOf(from), labelOf(to), meters, minutes, path, departMinute, departMinute + minutes)
            found.putIfAbsent("walk", Journey(listOf(w), departMinute, departMinute + minutes, meters))
        }
        val all = found.values.toList()
        return all.filter { j -> all.none { o -> o !== j && dominates(o, j) } }
            .sortedBy { it.departMinute }
            .take(wanted + 1)
    }

    private fun dominates(a: Journey, b: Journey): Boolean {
        val better = a.departMinute >= b.departMinute && a.arriveMinute <= b.arriveMinute &&
            a.walkMeters <= b.walkMeters && a.transfers <= b.transfers
        val strictly = a.departMinute > b.departMinute || a.arriveMinute < b.arriveMinute ||
            a.walkMeters < b.walkMeters || a.transfers < b.transfers
        return better && strictly
    }

    private fun dominates(a: Label, b: Label) =
        a.arr <= b.arr && a.walk <= b.walk && (a.arr < b.arr || a.walk < b.walk)

    private fun addLabel(bag: MutableList<Label>, cand: Label): Boolean {
        for (l in bag) if (dominates(l, cand) || (l.arr == cand.arr && l.walk == cand.walk)) return false
        bag.removeAll { dominates(cand, it) }
        bag.add(cand)
        return true
    }

    private fun raptor(
        from: Endpoint,
        to: Endpoint,
        accessFrom: Access,
        accessTo: Access,
        day: ServiceDay,
        departMinute: Int,
    ): List<Journey> {
        val n = network.stops.size
        val rounds = cfg.maxTransfers + 1
        val bags = Array(rounds + 1) { Array(n) { ArrayList<Label>(2) } }
        val fromLabel = labelOf(from)
        val toLabel = labelOf(to)
        var marked = HashSet<Int>()
        for ((stop, meters) in accessFrom.meters) {
            val step = if (meters > 0) {
                Step.Walk(fromLabel, network.stops[stop].name, meters, accessFrom.paths[stop] ?: emptyList())
            } else null
            if (addLabel(bags[0][stop], Label(departMinute + walkMinutes(meters), meters, stop, false, null, step))) {
                marked.add(stop)
            }
        }
        for (round in 1..rounds) {
            if (marked.isEmpty()) break
            val next = HashSet<Int>()
            val scan = HashMap<Int, Int>()
            for (stop in marked) {
                val ps = network.patternsAtStop[stop]
                val pos = network.positionsAtStop[stop]
                for (i in ps.indices) {
                    val prev = scan[ps[i]]
                    if (prev == null || pos[i] < prev) scan[ps[i]] = pos[i]
                }
            }
            for ((pattern, start) in scan) scanPattern(pattern, start, bags, round, day, next)
            relaxTransfers(bags[round], next)
            marked = next
        }
        val results = ArrayList<Label>()
        for (round in 1..rounds) {
            for ((stop, meters) in accessTo.meters) {
                for (l in bags[round][stop]) {
                    if (!l.byRide) continue
                    val step = if (meters > 0) {
                        Step.Walk(network.stops[stop].name, toLabel, meters, (accessTo.paths[stop] ?: emptyList()).reversed())
                    } else null
                    results.add(Label(l.arr + walkMinutes(meters), l.walk + meters, stop, false, l, step))
                }
            }
        }
        val front = ArrayList<Label>()
        for (r in results.sortedWith(compareBy({ it.arr }, { it.walk }))) addLabel(front, r)
        return front.map { toJourney(it, departMinute) }
    }

    private fun relaxTransfers(bag: Array<ArrayList<Label>>, marked: HashSet<Int>) {
        for (stop in marked.toList()) {
            val sources = bag[stop].filter { it.byRide }
            if (sources.isEmpty()) continue
            for (t in transfersFrom(stop)) {
                val mins = walkMinutes(t.meters)
                for (l in sources) {
                    val step = Step.Walk(network.stops[stop].name, network.stops[t.to].name, t.meters, t.path)
                    if (addLabel(bag[t.to], Label(l.arr + mins, l.walk + t.meters, t.to, false, l, step))) {
                        marked.add(t.to)
                    }
                }
            }
        }
    }

    private fun scanPattern(
        pattern: Int,
        start: Int,
        bags: Array<Array<ArrayList<Label>>>,
        round: Int,
        day: ServiceDay,
        next: HashSet<Int>,
    ) {
        val p = network.patterns[pattern]
        val trips = day.tripsOfPattern[pattern]
        if (trips.isEmpty()) return
        val riding = ArrayList<Triple<ActiveTrip, Int, Label>>()
        for (pos in start until p.stops.size) {
            val stop = p.stops[pos]
            for ((trip, board, label) in riding) {
                val t = trip.times[pos]
                if (t < 0) continue
                val cand = Label(t, label.walk, stop, true, label, Step.Ride(pattern, trip, board, pos))
                if (addLabel(bags[round][stop], cand)) next.add(stop)
            }
            for (l in bags[round - 1][stop]) {
                val ready = l.arr + if (l.byRide) cfg.minTransferMinutes else 0
                var best: ActiveTrip? = null
                for (trip in trips) {
                    val t = trip.times[pos]
                    if (t < ready || pos >= trip.last) continue
                    if (best == null || t < best.times[pos]) best = trip
                }
                val chosen = best ?: continue
                if (riding.none { it.first === chosen && it.third.walk <= l.walk }) {
                    riding.add(Triple(chosen, pos, l))
                }
            }
        }
    }

    private fun toJourney(end: Label, requested: Int): Journey {
        val steps = ArrayList<Step>()
        var cur: Label? = end
        while (cur != null) {
            cur.step?.let { steps.add(it) }
            cur = cur.parent
        }
        steps.reverse()
        val merged = ArrayList<Step>()
        for (s in steps) {
            val last = merged.lastOrNull()
            if (s is Step.Walk && last is Step.Walk) {
                merged[merged.size - 1] = Step.Walk(last.from, s.to, last.meters + s.meters, last.path + s.path)
            } else merged.add(s)
        }
        val legs = ArrayList<Leg>()
        var clock = requested
        for ((i, s) in merged.withIndex()) {
            when (s) {
                is Step.Ride -> {
                    val visits = (s.board..s.alight).mapNotNull { pos ->
                        val t = s.trip.times[pos]
                        if (t < 0) null else StopVisit(
                            network.stops[network.patterns[s.pattern].stops[pos]], t, network.platformPoint(s.pattern, pos)
                        )
                    }
                    legs.add(
                        Leg.Ride(
                            line = network.lineOf(s.pattern),
                            pattern = s.pattern,
                            headsign = network.stops[network.patterns[s.pattern].stops[s.trip.last]],
                            visits = visits,
                            shape = network.shapeBetween(s.pattern, s.board, s.alight),
                            note = s.trip.trip.note,
                        )
                    )
                    clock = visits.last().minute
                }
                is Step.Walk -> {
                    val mins = walkMinutes(s.meters)
                    val nextRide = merged.getOrNull(i + 1) as? Step.Ride
                    val startAt = if (i == 0 && nextRide != null) nextRide.trip.times[nextRide.board] - mins else clock
                    legs.add(Leg.Walk(s.from, s.to, s.meters, mins, s.path, startAt, startAt + mins))
                    clock = startAt + mins
                }
            }
        }
        val depart = legs.firstOrNull()?.startMinute ?: requested
        return Journey(legs, depart, end.arr, end.walk)
    }
}

fun List<Journey>.rankedBy(pref: Preference): List<Journey> = when (pref) {
    Preference.FASTEST -> sortedWith(compareBy({ it.arriveMinute }, { -it.departMinute }, { it.walkMeters }))
    Preference.LEAST_WALKING -> sortedWith(compareBy({ it.walkMeters / 100 }, { it.arriveMinute }))
    Preference.FEWEST_TRANSFERS -> sortedWith(compareBy({ it.transfers }, { it.arriveMinute }))
}
