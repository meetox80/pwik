package me.kotwicha.pwik.core.map

import me.kotwicha.pwik.core.geo.LatLon
import me.kotwicha.pwik.core.geo.metersBetween
import me.kotwicha.pwik.core.geo.nearestPointOnSegment
import me.kotwicha.pwik.core.importer.StreetLocator
import me.kotwicha.pwik.core.text.fold
import me.kotwicha.pwik.core.text.groupKey
import me.kotwicha.pwik.core.text.searchRank
import me.kotwicha.pwik.core.text.similarity

enum class PlaceKind { STREET, TOWN, VILLAGE, NEIGHBOURHOOD }

data class NamedPlace(val name: String, val kind: PlaceKind, val point: LatLon)

class Streets(private val map: MapData) : StreetLocator {
    private val roads = map.layer(LayerKind.ROAD)
    private val places = map.layer(LayerKind.PLACE)
    private val roadsByName: Map<Int, IntArray>
    private val entries: List<Entry>

    private class Entry(val name: String, val folded: String, val kind: PlaceKind, val nameIndex: Int, val feature: Int)

    init {
        val acc = HashMap<Int, MutableList<Int>>()
        for (f in 0 until roads.count) {
            val n = roads.name[f]
            if (n > 0) acc.getOrPut(n) { ArrayList() }.add(f)
        }
        roadsByName = acc.mapValues { it.value.toIntArray() }
        val list = ArrayList<Entry>()
        for (n in roadsByName.keys) {
            val name = map.nameOf(n) ?: continue
            list.add(Entry(name, fold(name), PlaceKind.STREET, n, -1))
        }
        for (f in 0 until places.count) {
            val name = map.nameOf(places.name[f]) ?: continue
            val kind = when (places.cls[f].toInt()) {
                0 -> PlaceKind.TOWN
                1 -> PlaceKind.VILLAGE
                else -> PlaceKind.NEIGHBOURHOOD
            }
            list.add(Entry(name, fold(name), kind, places.name[f], f))
        }
        entries = list
    }

    fun search(query: String, limit: Int): List<NamedPlace> {
        val q = fold(query)
        if (q.isEmpty()) return emptyList()
        return entries.mapNotNull { e -> searchRank(q, e.folded)?.let { e to it } }
            .sortedWith(compareBy({ it.second }, { it.first.kind != PlaceKind.STREET }, { it.first.folded.length }))
            .distinctBy { it.first.folded to it.first.kind }
            .take(limit)
            .mapNotNull { (e, _) -> placeOf(e) }
    }

    private fun placeOf(e: Entry): NamedPlace? {
        if (e.kind != PlaceKind.STREET) {
            val p = places.firstPoint(e.feature)
            return NamedPlace(e.name, e.kind, LatLon(places.lat(p), places.lon(p)))
        }
        val ways = roadsByName[e.nameIndex] ?: return null
        var lat = 0.0
        var lon = 0.0
        var n = 0
        for (f in ways) for (p in roads.firstPoint(f) until roads.endPoint(f)) {
            lat += roads.lat(p); lon += roads.lon(p); n++
        }
        if (n == 0) return null
        val centroid = LatLon(lat / n, lon / n)
        return NamedPlace(e.name, e.kind, nearestOnWays(ways, centroid) ?: centroid)
    }

    fun nearestPointOnStreet(name: String, near: LatLon): LatLon? {
        val idx = map.names.indexOf(name)
        if (idx < 0) return null
        val ways = roadsByName[idx + 1] ?: return null
        return nearestOnWays(ways, near)
    }

    private fun nearestOnWays(ways: IntArray, near: LatLon): LatLon? {
        var best: LatLon? = null
        var bestD = Double.MAX_VALUE
        for (f in ways) {
            val s = roads.firstPoint(f)
            val e = roads.endPoint(f)
            for (p in s until e - 1) {
                val c = nearestPointOnSegment(near.lat, near.lon, roads.lat(p), roads.lon(p), roads.lat(p + 1), roads.lon(p + 1))
                val d = metersBetween(near, c)
                if (d < bestD) { bestD = d; best = c }
            }
        }
        return best
    }

    fun streetNameNear(point: LatLon, maxMeters: Double = 80.0): String? {
        var best: String? = null
        var bestD = maxMeters
        val x = map.toUnitsX(point.lon).toFloat()
        val y = map.toUnitsY(point.lat).toFloat()
        val r = 120f
        map.grids[LayerKind.ROAD].query(x - r, y - r, x + r, y + r) { f ->
            val n = roads.name[f]
            if (n <= 0) return@query
            for (p in roads.firstPoint(f) until roads.endPoint(f) - 1) {
                val c = nearestPointOnSegment(point.lat, point.lon, roads.lat(p), roads.lon(p), roads.lat(p + 1), roads.lon(p + 1))
                val d = metersBetween(point, c)
                if (d < bestD) { bestD = d; best = map.nameOf(n) }
            }
        }
        return best
    }

    override fun locate(name: String, near: LatLon, radiusMeters: Double): LatLon? {
        val key = groupKey(name)
        var best: LatLon? = null
        var bestScore = 0.0
        for (e in entries) {
            val score = similarity(key, groupKey(e.name.removePrefix("ulica ")))
            if (score < 0.72) continue
            val p = if (e.kind == PlaceKind.STREET) {
                roadsByName[e.nameIndex]?.let { nearestOnWays(it, near) }
            } else placeOf(e)?.point
            if (p == null || metersBetween(p, near) > radiusMeters) continue
            if (score > bestScore) { bestScore = score; best = p }
        }
        return best
    }
}
