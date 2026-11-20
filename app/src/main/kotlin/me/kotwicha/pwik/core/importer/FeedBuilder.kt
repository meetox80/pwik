package me.kotwicha.pwik.core.importer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import me.kotwicha.pwik.core.calendar.ALL_DAYS
import me.kotwicha.pwik.core.calendar.Service
import me.kotwicha.pwik.core.geo.LatLon
import me.kotwicha.pwik.core.geo.metersBetween
import me.kotwicha.pwik.core.pdf.Legend
import me.kotwicha.pwik.core.pdf.Meaning
import me.kotwicha.pwik.core.pdf.ParsedDocument
import me.kotwicha.pwik.core.pdf.ParsedTable
import me.kotwicha.pwik.core.text.fold
import me.kotwicha.pwik.core.text.groupKey
import me.kotwicha.pwik.core.text.similarity
import me.kotwicha.pwik.core.text.slug
import me.kotwicha.pwik.core.timetable.Platform

fun interface StreetLocator {
    fun locate(name: String, near: LatLon, radiusMeters: Double): LatLon?
}

class FeedBuilder(private val gazetteer: Gazetteer, private val streets: StreetLocator? = null) {

    private val stops = ArrayList<FeedStop>()
    private val stopByKey = HashMap<String, Int>()
    private val aliasToGazetteer = HashMap<String, GazetteerStop>()
    private val services = ArrayList<Service>()
    private val serviceIndex = HashMap<Service, Int>()
    private val warnings = ArrayList<String>()

    init {
        for (g in gazetteer.stops) {
            for (n in g.names + g.name) aliasToGazetteer.putIfAbsent(fold(n), g)
        }
    }

    fun build(doc: ParsedDocument): Feed {
        val lines = ArrayList<FeedLine>()
        val lineIndex = HashMap<String, Int>()
        val patterns = ArrayList<FeedPattern>()
        val patternIndex = HashMap<String, Int>()
        val trips = ArrayList<FeedTrip>()
        val shared = sharedLegend(doc)

        for (table in doc.tables) {
            val li = lineIndex.getOrPut(table.line) {
                lines.add(FeedLine(table.line, table.title)); lines.size - 1
            }
            val resolved = resolveRows(table)
            val served = table.rows.indices.filter { r ->
                resolved[r] != null && table.rows[r].cells.any { it != null }
            }
            if (served.size < 2) {
                warnings.add("strona ${table.page}: za mało przystanków")
                continue
            }
            val stopSeq = IntArray(served.size) { resolved[served[it]]!! }
            val platformSeq = assignPlatforms(stopSeq)
            val key = "$li|${stopSeq.joinToString(",")}|${platformSeq.joinToString(",")}"
            val pi = patternIndex.getOrPut(key) {
                patterns.add(FeedPattern(li, stopSeq, platformSeq)); patterns.size - 1
            }

            val base = Legend.validity(table.validity).and(Legend.notices(table.footnotes))
            for (col in 0 until table.columns) {
                val times = IntArray(served.size) { -1 }
                val conditions = IntArray(served.size) { -1 }
                var prev = -1
                for ((pos, r) in served.withIndex()) {
                    val cell = table.rows[r].cells[col] ?: continue
                    var m = cell.minute
                    if (prev >= 0 && m + 720 < prev) m += 1440
                    if (prev >= 0 && m < prev) {
                        warnings.add("strona ${table.page}, kolumna ${col + 1}: czas ${table.rows[r].name} cofa się")
                        continue
                    }
                    times[pos] = m
                    prev = m
                    if (cell.flags.isNotEmpty()) {
                        val cond = conditionFor(cell.flags, table, shared)
                        if (cond != null) conditions[pos] = serviceId(cond)
                    }
                }
                if (times.count { it >= 0 } < 2) continue
                val (service, note) = columnService(base, table.columnFlags[col], table, shared)
                trips.add(FeedTrip(pi, serviceId(service), times, conditions, note))
            }
        }
        return Feed(stops.toList(), lines, patterns, services.toList(), trips, warnings.toList())
    }

    private fun serviceId(s: Service): Int = serviceIndex.getOrPut(s) { services.add(s); services.size - 1 }

    private fun sharedLegend(doc: ParsedDocument): Map<String, String> {
        val all = HashMap<String, MutableSet<String>>()
        for (t in doc.tables) for ((k, v) in t.legend) all.getOrPut(k) { HashSet() }.add(fold(Legend.meaning(v).toString()))
        val out = HashMap<String, String>()
        for (t in doc.tables) for ((k, v) in t.legend) if (all[k]?.size == 1) out.putIfAbsent(k, v)
        return out
    }

    private fun meaningOf(letter: String, table: ParsedTable, shared: Map<String, String>): Meaning? {
        val text = table.legend[letter] ?: shared[letter]
        return if (text != null) Legend.meaning(text) else Legend.defaultMeaning(letter)
    }

    private fun meaningsOf(flags: String, table: ParsedTable, shared: Map<String, String>): List<Meaning> {
        val whole = flags.uppercase()
        if (table.legend.containsKey(whole)) return listOfNotNull(meaningOf(whole, table, shared))
        return whole.map { c ->
            meaningOf(c.toString(), table, shared).also {
                if (it == null) warnings.add("strona ${table.page}: nieznane oznaczenie $c")
            }
        }.filterNotNull()
    }

    private fun columnService(
        base: Service,
        flags: String,
        table: ParsedTable,
        shared: Map<String, String>,
    ): Pair<Service, String?> {
        if (flags.isEmpty()) return base to null
        val meanings = meaningsOf(flags, table, shared)
        val rules = meanings.filterIsInstance<Meaning.Rule>().map { it.service }
        val notes = meanings.filterIsInstance<Meaning.Note>().map { it.text }
        val dayRules = rules.filter { it.days != ALL_DAYS }
        var service = if (dayRules.isNotEmpty()) base.copy(days = dayRules.fold(0) { a, r -> a or r.days }) else base
        for (r in rules) service = service.and(r.copy(days = ALL_DAYS))
        return service to notes.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    private fun conditionFor(flags: String, table: ParsedTable, shared: Map<String, String>): Service? {
        val rules = meaningsOf(flags, table, shared).filterIsInstance<Meaning.Rule>()
        if (rules.isEmpty()) return null
        return rules.map { it.service }.reduce { a, b -> a.and(b) }
    }

    private fun resolveRows(table: ParsedTable): Array<Int?> {
        val out = arrayOfNulls<Int>(table.rows.size)
        for ((i, row) in table.rows.withIndex()) out[i] = resolve(row.name)
        for ((i, row) in table.rows.withIndex()) {
            if (out[i] != null || row.cells.all { it == null }) continue
            out[i] = place(row.name, i, out)
            if (out[i] == null) warnings.add("strona ${table.page}: nie znaleziono przystanku ${row.name}")
        }
        return out
    }

    private fun resolve(raw: String): Int? {
        aliasToGazetteer[fold(raw)]?.let { return use(it) }
        val key = groupKey(raw)
        if (key.isEmpty()) return null
        var bestScore = 0.0
        var bestStop: GazetteerStop? = null
        var bestOsm: GazetteerOsmGroup? = null
        for (g in gazetteer.stops) {
            for (n in g.names + g.name + g.osm) {
                val s = similarity(key, groupKey(n))
                if (s > bestScore) { bestScore = s; bestStop = g; bestOsm = null }
            }
        }
        for (o in gazetteer.osm) {
            val s = similarity(key, groupKey(o.name))
            if (s > bestScore) { bestScore = s; bestOsm = o; bestStop = null }
        }
        if (bestScore < 0.72) return null
        bestStop?.let { return use(it, raw) }
        val osm = bestOsm ?: return null
        gazetteer.stops.firstOrNull { osm.name in it.osm || it.name == osm.name }?.let { return use(it, raw) }
        return add(
            FeedStop(
                key = slug(osm.name),
                name = displayName(raw),
                lat = osm.platforms.map { it.lat }.average(),
                lon = osm.platforms.map { it.lon }.average(),
                approximate = false,
                platforms = osm.platforms.map { Platform(it.code, it.lat, it.lon) },
                aliases = listOf(raw, osm.name).distinct(),
            )
        )
    }

    private fun use(g: GazetteerStop, alias: String? = null): Int {
        val existing = stopByKey[g.key]
        if (existing != null) {
            if (alias != null && alias !in stops[existing].aliases) {
                stops[existing] = stops[existing].copy(aliases = stops[existing].aliases + alias)
            }
            return existing
        }
        return add(
            FeedStop(
                key = g.key,
                name = g.name,
                lat = g.platforms.map { it.lat }.average(),
                lon = g.platforms.map { it.lon }.average(),
                approximate = g.placed,
                platforms = g.platforms.map { Platform(it.code, it.lat, it.lon) },
                aliases = (g.names + g.osm + listOfNotNull(alias)).filter { it != g.name }.distinct(),
            )
        )
    }

    private fun add(stop: FeedStop): Int {
        stopByKey[stop.key]?.let { return it }
        stops.add(stop)
        stopByKey[stop.key] = stops.size - 1
        return stops.size - 1
    }

    private fun place(raw: String, row: Int, resolved: Array<Int?>): Int? {
        val key = slug(displayName(raw))
        stopByKey[key]?.let { return it }
        val before = (row - 1 downTo 0).firstOrNull { resolved[it] != null && !stops[resolved[it]!!].approximate }
        val after = (row + 1 until resolved.size).firstOrNull { resolved[it] != null && !stops[resolved[it]!!].approximate }
        if (before == null && after == null) return null
        val a = stops[resolved[before ?: after!!]!!]
        val b = stops[resolved[after ?: before!!]!!]
        val f = if (before != null && after != null) (row - before).toDouble() / (after - before) else 0.0
        val guess = LatLon(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
        val span = metersBetween(a.lat, a.lon, b.lat, b.lon)
        val point = streets?.locate(raw, guess, (span * 0.75).coerceIn(300.0, 2500.0)) ?: guess
        warnings.add("przystanek $raw umieszczony w przybliżeniu")
        return add(
            FeedStop(
                key = key,
                name = displayName(raw),
                lat = point.lat,
                lon = point.lon,
                approximate = true,
                platforms = listOf(Platform("", point.lat, point.lon)),
                aliases = listOf(raw),
            )
        )
    }

    private fun assignPlatforms(seq: IntArray): IntArray = IntArray(seq.size) { i ->
        val stop = stops[seq[i]]
        if (stop.platforms.size < 2) return@IntArray 0
        val prev = (i - 1 downTo 0).map { stops[seq[it]] }.firstOrNull { it.key != stop.key }
        val next = (i + 1 until seq.size).map { stops[seq[it]] }.firstOrNull { it.key != stop.key }
        val a = prev ?: stop
        val b = next ?: stop
        if (a === b) return@IntArray 0
        val k = cos(stop.lat * PI / 180.0)
        val hx = (b.lon - a.lon) * k
        val hy = b.lat - a.lat
        val n = hypot(hx, hy).takeIf { it > 0 } ?: return@IntArray 0
        stop.platforms.indices.maxBy { p ->
            val ox = (stop.platforms[p].lon - stop.lon) * k
            val oy = stop.platforms[p].lat - stop.lat
            (ox * hy - oy * hx) / n
        }
    }

    companion object {
        private val lowerKeep = setOf("do", "n/ż", "k-c", "i", "w", "na", "nad")

        fun displayName(raw: String): String {
            val s = raw
                .replace(Regex("\\(\\s*do miasta\\s*\\)", RegexOption.IGNORE_CASE), "")
                .replace(Regex("-\\s*słupek", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+-\\s+"), " ")
                .replace(Regex("(\\D)(\\d)$"), "$1 $2")
                .trim()
            return s.split(Regex("\\s+")).joinToString(" ") { w ->
                if (w.lowercase() in lowerKeep || w.length < 3 || w.matches(Regex("[IVX]+"))) w
                else w.replaceFirstChar { it.uppercaseChar() }
            }
        }
    }
}
