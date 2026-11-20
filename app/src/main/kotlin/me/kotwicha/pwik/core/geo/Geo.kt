package me.kotwicha.pwik.core.geo

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

const val METERS_PER_DEGREE = 111_320.0

data class LatLon(val lat: Double, val lon: Double)

fun metersBetween(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val k = cos((aLat + bLat) * 0.5 * PI / 180.0)
    return hypot((aLon - bLon) * k, aLat - bLat) * METERS_PER_DEGREE
}

fun metersBetween(a: LatLon, b: LatLon): Double = metersBetween(a.lat, a.lon, b.lat, b.lon)

fun pathLength(points: List<LatLon>): Double {
    var total = 0.0
    for (i in 1 until points.size) total += metersBetween(points[i - 1], points[i])
    return total
}

fun nearestPointOnSegment(
    pLat: Double,
    pLon: Double,
    aLat: Double,
    aLon: Double,
    bLat: Double,
    bLon: Double,
): LatLon {
    val k = cos((aLat + bLat) * 0.5 * PI / 180.0)
    val ax = aLon * k
    val bx = bLon * k
    val px = pLon * k
    val dx = bx - ax
    val dy = bLat - aLat
    val len2 = dx * dx + dy * dy
    if (len2 == 0.0) return LatLon(aLat, aLon)
    val t = min(1.0, max(0.0, ((px - ax) * dx + (pLat - aLat) * dy) / len2))
    return LatLon(aLat + dy * t, (ax + dx * t) / k)
}

object Mercator {
    fun x(lon: Double): Double = (lon + 180.0) / 360.0

    fun y(lat: Double): Double {
        val s = sin(lat * PI / 180.0)
        return 0.5 - ln((1 + s) / (1 - s)) / (4 * PI)
    }

    fun lon(x: Double): Double = x * 360.0 - 180.0

    fun lat(y: Double): Double = (2 * atan(exp((0.5 - y) * 2 * PI)) - PI / 2) * 180.0 / PI
}

data class Bounds(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double) {
    val center: LatLon get() = LatLon((minLat + maxLat) / 2, (minLon + maxLon) / 2)

    fun contains(lat: Double, lon: Double): Boolean =
        lat in minLat..maxLat && lon in minLon..maxLon

    companion object {
        fun of(points: Iterable<LatLon>): Bounds? {
            var minLat = Double.MAX_VALUE
            var minLon = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var maxLon = -Double.MAX_VALUE
            var any = false
            for (p in points) {
                any = true
                minLat = min(minLat, p.lat)
                minLon = min(minLon, p.lon)
                maxLat = max(maxLat, p.lat)
                maxLon = max(maxLon, p.lon)
            }
            return if (any) Bounds(minLat, minLon, maxLat, maxLon) else null
        }
    }
}
