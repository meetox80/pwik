package me.kotwicha.pwik.core.map

import java.util.PriorityQueue
import me.kotwicha.pwik.core.geo.LatLon
import me.kotwicha.pwik.core.geo.metersBetween

private const val Q = 1e-6
private const val GRID = 0.002

class StreetGraph private constructor(
    private val lat: DoubleArray,
    private val lon: DoubleArray,
    private val head: IntArray,
    private val next: IntArray,
    private val target: IntArray,
    private val weight: FloatArray,
    private val length: FloatArray,
    private val grid: HashMap<Long, IntArray>,
) {
    private val dist = DoubleArray(lat.size)
    private val stamp = IntArray(lat.size)
    private val prev = IntArray(lat.size)
    private var generation = 0

    fun point(node: Int): LatLon = LatLon(lat[node], lon[node])

    fun nearestNode(qLat: Double, qLon: Double, maxRings: Int = 6): Int {
        val cx = Math.floorDiv((qLon / GRID).toLong(), 1L).toInt()
        val cy = Math.floorDiv((qLat / GRID).toLong(), 1L).toInt()
        var best = -1
        var bestD = Double.MAX_VALUE
        for (ring in 0..maxRings) {
            for (gx in (cx - ring)..(cx + ring)) {
                for (gy in (cy - ring)..(cy + ring)) {
                    if (ring > 0 && gx != cx - ring && gx != cx + ring && gy != cy - ring && gy != cy + ring) continue
                    val bucket = grid[key(gx, gy)] ?: continue
                    for (n in bucket) {
                        val d = metersBetween(qLat, qLon, lat[n], lon[n])
                        if (d < bestD) { bestD = d; best = n }
                    }
                }
            }
            if (best >= 0 && ring >= 1) break
        }
        return best
    }

    class Reach internal constructor(
        private val graph: StreetGraph,
        private val meters: HashMap<Int, Float>,
        private val parent: HashMap<Int, Int>,
    ) {
        fun metersTo(node: Int): Double? = meters[node]?.toDouble()

        fun pathTo(node: Int): List<LatLon> {
            if (!meters.containsKey(node)) return emptyList()
            val out = ArrayList<LatLon>()
            var cur = node
            while (cur >= 0) {
                out.add(graph.point(cur))
                cur = parent[cur] ?: -1
            }
            out.reverse()
            return out
        }
    }

    @Synchronized
    fun explore(source: Int, maxMeters: Double): Reach {
        val gen = ++generation
        val touched = ArrayList<Int>()
        val pq = PriorityQueue<Long>()
        fun push(d: Double, n: Int) = pq.add((d.toFloat().toRawBits().toLong() shl 32) or n.toLong())
        stamp[source] = gen
        dist[source] = 0.0
        prev[source] = -1
        touched.add(source)
        push(0.0, source)
        while (pq.isNotEmpty()) {
            val top = pq.remove()
            val u = (top and 0xFFFFFFFFL).toInt()
            val d = Float.fromBits((top ushr 32).toInt())
            if (d > dist[u].toFloat()) continue
            var e = head[u]
            while (e != -1) {
                val v = target[e]
                val nd = dist[u] + length[e]
                if (nd <= maxMeters && (stamp[v] != gen || nd < dist[v])) {
                    if (stamp[v] != gen) { stamp[v] = gen; touched.add(v) }
                    dist[v] = nd
                    prev[v] = u
                    push(nd, v)
                }
                e = next[e]
            }
        }
        val meters = HashMap<Int, Float>(touched.size * 2)
        val parent = HashMap<Int, Int>(touched.size * 2)
        for (n in touched) {
            meters[n] = dist[n].toFloat()
            parent[n] = prev[n]
        }
        return Reach(this, meters, parent)
    }

    @Synchronized
    fun route(from: Int, to: Int, maxFactor: Double = 4.0): List<LatLon>? {
        if (from < 0 || to < 0) return null
        if (from == to) return listOf(point(from))
        val gen = ++generation
        val straight = metersBetween(lat[from], lon[from], lat[to], lon[to])
        val limit = straight * maxFactor + 1500.0
        val pq = PriorityQueue<Long>()
        fun push(f: Double, n: Int) = pq.add((f.toFloat().toRawBits().toLong() shl 32) or n.toLong())
        stamp[from] = gen
        dist[from] = 0.0
        prev[from] = -1
        push(straight, from)
        while (pq.isNotEmpty()) {
            val top = pq.remove()
            val u = (top and 0xFFFFFFFFL).toInt()
            if (u == to) break
            val f = Float.fromBits((top ushr 32).toInt())
            val h = metersBetween(lat[u], lon[u], lat[to], lon[to])
            if (f > (dist[u] + h).toFloat()) continue
            var e = head[u]
            while (e != -1) {
                val v = target[e]
                val nd = dist[u] + weight[e]
                if (nd <= limit && (stamp[v] != gen || nd < dist[v])) {
                    stamp[v] = gen
                    dist[v] = nd
                    prev[v] = u
                    push(nd + metersBetween(lat[v], lon[v], lat[to], lon[to]), v)
                }
                e = next[e]
            }
        }
        if (stamp[to] != gen) return null
        val out = ArrayList<LatLon>()
        var cur = to
        while (cur >= 0) {
            out.add(point(cur))
            cur = prev[cur]
        }
        out.reverse()
        return out
    }

    companion object {
        private fun key(gx: Int, gy: Int): Long = (gx.toLong() shl 32) xor (gy.toLong() and 0xFFFFFFFFL)

        private fun penalty(cls: Int): Float = when (cls) {
            0, 1, 2 -> 1.0f
            3 -> 1.05f
            4 -> 1.2f
            5 -> 1.6f
            6 -> 2.2f
            else -> 1.0f
        }

        fun build(map: MapData, walking: Boolean): StreetGraph {
            val roads = map.layer(LayerKind.ROAD)
            val index = HashMap<Long, Int>(roads.latQ.size)
            val latAcc = ArrayList<Double>()
            val lonAcc = ArrayList<Double>()
            fun node(p: Int): Int {
                val k = (roads.lonQ[p].toLong() shl 32) xor (roads.latQ[p].toLong() and 0xFFFFFFFFL)
                return index.getOrPut(k) {
                    latAcc.add(roads.latQ[p] * Q); lonAcc.add(roads.lonQ[p] * Q); latAcc.size - 1
                }
            }
            val from = IntArrayList()
            val to = IntArrayList()
            val w = FloatArrayList()
            val len = FloatArrayList()
            for (f in 0 until roads.count) {
                val flags = roads.flags[f].toInt()
                if (walking && flags and RoadFlag.WALK == 0) continue
                if (!walking && flags and RoadFlag.DRIVE == 0) continue
                val forward = walking || flags and RoadFlag.ONEWAY_REVERSE == 0
                val backward = walking || flags and RoadFlag.ONEWAY == 0
                val pen = if (walking) 1f else penalty(roads.cls[f].toInt())
                val start = roads.firstPoint(f)
                val end = roads.endPoint(f)
                var a = node(start)
                for (p in start + 1 until end) {
                    val b = node(p)
                    if (a == b) continue
                    val m = metersBetween(roads.lat(p - 1), roads.lon(p - 1), roads.lat(p), roads.lon(p)).toFloat()
                    if (forward) { from.add(a); to.add(b); w.add(m * pen); len.add(m) }
                    if (backward) { from.add(b); to.add(a); w.add(m * pen); len.add(m) }
                    a = b
                }
            }
            val n = latAcc.size
            val head = IntArray(n) { -1 }
            val next = IntArray(from.size)
            val target = IntArray(from.size)
            val weight = FloatArray(from.size)
            val length = FloatArray(from.size)
            for (e in 0 until from.size) {
                target[e] = to[e]
                weight[e] = w[e]
                length[e] = len[e]
                next[e] = head[from[e]]
                head[from[e]] = e
            }
            val latArr = latAcc.toDoubleArray()
            val lonArr = lonAcc.toDoubleArray()
            val buckets = HashMap<Long, ArrayList<Int>>()
            for (i in 0 until n) {
                if (head[i] == -1) continue
                val k = key(Math.floorDiv((lonArr[i] / GRID).toLong(), 1L).toInt(), Math.floorDiv((latArr[i] / GRID).toLong(), 1L).toInt())
                buckets.getOrPut(k) { ArrayList() }.add(i)
            }
            val grid = HashMap<Long, IntArray>(buckets.size * 2)
            for ((k, v) in buckets) grid[k] = v.toIntArray()
            return StreetGraph(latArr, lonArr, head, next, target, weight, length, grid)
        }
    }
}

private class IntArrayList {
    private var data = IntArray(4096)
    var size = 0
        private set

    fun add(v: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }

    operator fun get(i: Int): Int = data[i]
}

private class FloatArrayList {
    private var data = FloatArray(4096)
    var size = 0
        private set

    fun add(v: Float) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }

    operator fun get(i: Int): Float = data[i]
}
