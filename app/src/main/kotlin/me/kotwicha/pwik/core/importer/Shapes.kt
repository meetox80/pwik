package me.kotwicha.pwik.core.importer

import me.kotwicha.pwik.core.geo.LatLon
import me.kotwicha.pwik.core.geo.metersBetween
import me.kotwicha.pwik.core.geo.pathLength
import me.kotwicha.pwik.core.map.StreetGraph

class ShapeBuilder(private val drive: StreetGraph) {

    fun build(points: List<LatLon>): Pair<List<LatLon>, IntArray> {
        val out = ArrayList<LatLon>()
        val breaks = IntArray(points.size)
        if (points.isEmpty()) return out to breaks
        out.add(points[0])
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val segment = segment(a, b)
            for (k in 1 until segment.size) out.add(segment[k])
            breaks[i] = out.size - 1
        }
        return out to breaks
    }

    private fun segment(a: LatLon, b: LatLon): List<LatLon> {
        val direct = metersBetween(a, b)
        if (direct < 15) return listOf(a, b)
        val na = drive.nearestNode(a.lat, a.lon)
        val nb = drive.nearestNode(b.lat, b.lon)
        if (na < 0 || nb < 0) return listOf(a, b)
        val snapA = drive.point(na)
        val snapB = drive.point(nb)
        if (metersBetween(a, snapA) > 120 || metersBetween(b, snapB) > 120) return listOf(a, b)
        val path = drive.route(na, nb) ?: return listOf(a, b)
        if (pathLength(path) > direct * 3.5 + 600) return listOf(a, b)
        return buildList {
            add(a)
            addAll(path)
            add(b)
        }
    }
}
