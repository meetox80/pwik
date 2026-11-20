package me.kotwicha.pwik.ui.stopsmap

import android.graphics.Path
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.pow
import me.kotwicha.pwik.core.geo.Bounds
import me.kotwicha.pwik.core.map.MapData
import me.kotwicha.pwik.core.timetable.Network
import me.kotwicha.pwik.ui.map.UnitLine
import me.kotwicha.pwik.ui.map.UnitPoint
import me.kotwicha.pwik.ui.theme.LineColors

class RouteShape(val line: String, val path: UnitLine, val color: Color)

class RouteChunk(val route: Int, val path: Path, val minX: Float, val minY: Float, val maxX: Float, val maxY: Float)

private const val CHUNK_POINTS = 48

class NetworkGeometry private constructor(val map: MapData, val network: Network) {
    val stops: List<UnitPoint> = network.stops.map { UnitPoint.of(map, it.point) }
    val routes: List<RouteShape> = network.patterns.map { p ->
        val name = network.lines[p.line].name
        RouteShape(name, UnitLine.of(map, network.fullShape(p.index)), LineColors.of(name))
    }
    val servedBy: List<Set<String>> = network.linesAtStop.map { lines -> lines.mapTo(HashSet()) { it.name } }
    val bounds: Bounds? = Bounds.of(network.stops.map { it.point })
    private val levels = HashMap<Int, List<RouteChunk>>()
    private val tileLevels = HashMap<Int, List<RouteChunk>>()

    @Synchronized
    fun routeChunks(level: Int): List<RouteChunk> = levels.getOrPut(level) { buildChunks(level) }

    @Synchronized
    fun tileChunks(level: Int): List<RouteChunk> = tileLevels.getOrPut(level) { buildChunks(level) }

    private fun buildChunks(level: Int): List<RouteChunk> {
        val step = 0.8f / 2f.pow(level)
        return routes.flatMapIndexed { index, route -> chunks(index, route.path, step) }
    }

    private fun chunks(route: Int, line: UnitLine, step: Float): List<RouteChunk> {
        val n = line.xs.size
        if (n < 2) return emptyList()
        val xs = ArrayList<Float>(n)
        val ys = ArrayList<Float>(n)
        xs.add(line.xs[0])
        ys.add(line.ys[0])
        for (i in 1 until n) {
            val x = line.xs[i]
            val y = line.ys[i]
            if (i < n - 1 && abs(x - xs.last()) < step && abs(y - ys.last()) < step) continue
            xs.add(x)
            ys.add(y)
        }
        val out = ArrayList<RouteChunk>()
        var start = 0
        while (start < xs.size - 1) {
            val end = minOf(start + CHUNK_POINTS, xs.size - 1)
            val path = Path()
            path.moveTo(xs[start], ys[start])
            var minX = xs[start]
            var maxX = xs[start]
            var minY = ys[start]
            var maxY = ys[start]
            for (i in start + 1..end) {
                path.lineTo(xs[i], ys[i])
                minX = minOf(minX, xs[i])
                maxX = maxOf(maxX, xs[i])
                minY = minOf(minY, ys[i])
                maxY = maxOf(maxY, ys[i])
            }
            out.add(RouteChunk(route, path, minX, minY, maxX, maxY))
            start = end
        }
        return out
    }

    companion object {
        private var cached: NetworkGeometry? = null

        @Synchronized
        fun of(map: MapData, network: Network): NetworkGeometry {
            val current = cached
            if (current != null && current.map === map && current.network === network) return current
            return NetworkGeometry(map, network).also { cached = it }
        }
    }
}
