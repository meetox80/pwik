package me.kotwicha.pwik.core.map

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import me.kotwicha.pwik.core.geo.Bounds
import me.kotwicha.pwik.core.geo.LatLon
import me.kotwicha.pwik.core.geo.Mercator

object LayerKind {
    const val RESIDENTIAL = 0
    const val INDUSTRIAL = 1
    const val GREEN = 2
    const val FOREST = 3
    const val CEMETERY = 4
    const val PITCH = 5
    const val WATER = 6
    const val WETLAND = 7
    const val BUILDING = 8
    const val WATERWAY = 9
    const val RAIL = 10
    const val ROAD = 11
    const val PLACE = 12
    const val COUNT = 13
}

object RoadFlag {
    const val WALK = 1
    const val DRIVE = 2
    const val BRIDGE = 4
    const val TUNNEL = 8
    const val ONEWAY = 16
    const val ONEWAY_REVERSE = 32
}

const val MAP_UNITS_PER_WORLD = 16_777_216.0
private const val Q = 1e-6
private const val CELL = 512f

class MapLayer(
    val kind: Int,
    val cls: ByteArray,
    val flags: ByteArray,
    val name: IntArray,
    val ringOffset: IntArray,
    val pointOffset: IntArray,
    val x: FloatArray,
    val y: FloatArray,
    val latQ: IntArray,
    val lonQ: IntArray,
    val minX: FloatArray,
    val minY: FloatArray,
    val maxX: FloatArray,
    val maxY: FloatArray,
) {
    val count: Int get() = cls.size

    fun lat(point: Int): Double = latQ[point] * Q
    fun lon(point: Int): Double = lonQ[point] * Q

    fun firstPoint(feature: Int): Int = pointOffset[ringOffset[feature]]
    fun endPoint(feature: Int): Int = pointOffset[ringOffset[feature + 1]]
}

class MapGrid(
    private val originX: Float,
    private val originY: Float,
    private val cols: Int,
    private val rows: Int,
    private val cells: Array<IntArray>,
) {
    fun query(minX: Float, minY: Float, maxX: Float, maxY: Float, visit: (Int) -> Unit) {
        val c0 = ((minX - originX) / CELL).toInt().coerceIn(0, cols - 1)
        val c1 = ((maxX - originX) / CELL).toInt().coerceIn(0, cols - 1)
        val r0 = ((minY - originY) / CELL).toInt().coerceIn(0, rows - 1)
        val r1 = ((maxY - originY) / CELL).toInt().coerceIn(0, rows - 1)
        for (r in r0..r1) for (c in c0..c1) for (f in cells[r * cols + c]) visit(f)
    }

    companion object {
        fun build(layer: MapLayer, width: Float, height: Float): MapGrid {
            val cols = max(1, (width / CELL).toInt() + 1)
            val rows = max(1, (height / CELL).toInt() + 1)
            val acc = Array(cols * rows) { ArrayList<Int>() }
            for (f in 0 until layer.count) {
                val c0 = (layer.minX[f] / CELL).toInt().coerceIn(0, cols - 1)
                val c1 = (layer.maxX[f] / CELL).toInt().coerceIn(0, cols - 1)
                val r0 = (layer.minY[f] / CELL).toInt().coerceIn(0, rows - 1)
                val r1 = (layer.maxY[f] / CELL).toInt().coerceIn(0, rows - 1)
                for (r in r0..r1) for (c in c0..c1) acc[r * cols + c].add(f)
            }
            return MapGrid(0f, 0f, cols, rows, Array(acc.size) { acc[it].toIntArray() })
        }
    }
}

class MapData(
    val bounds: Bounds,
    val names: List<String>,
    val layers: Array<MapLayer>,
    val originX: Double,
    val originY: Double,
    val width: Float,
    val height: Float,
) {
    val grids: Array<MapGrid> = Array(layers.size) { MapGrid.build(layers[it], width, height) }

    fun layer(kind: Int): MapLayer = layers[kind]

    fun nameOf(index: Int): String? = if (index <= 0) null else names[index - 1]

    fun toUnitsX(lon: Double): Double = Mercator.x(lon) * MAP_UNITS_PER_WORLD - originX
    fun toUnitsY(lat: Double): Double = Mercator.y(lat) * MAP_UNITS_PER_WORLD - originY
    fun toLon(x: Double): Double = Mercator.lon((x + originX) / MAP_UNITS_PER_WORLD)
    fun toLat(y: Double): Double = Mercator.lat((y + originY) / MAP_UNITS_PER_WORLD)
    fun toLatLon(x: Double, y: Double): LatLon = LatLon(toLat(y), toLon(x))

    companion object {
        fun decode(bytes: ByteArray): MapData {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = String(bytes, 0, 4, Charsets.US_ASCII)
            require(magic == "PWMP") { "nieznany format mapy" }
            buf.position(4)
            require(buf.get().toInt() == 1) { "nieobsługiwana wersja mapy" }
            val minLon = buf.double
            val minLat = buf.double
            val maxLon = buf.double
            val maxLat = buf.double
            val originX = Mercator.x(minLon) * MAP_UNITS_PER_WORLD
            val originY = Mercator.y(maxLat) * MAP_UNITS_PER_WORLD
            val width = (Mercator.x(maxLon) * MAP_UNITS_PER_WORLD - originX).toFloat()
            val height = (Mercator.y(minLat) * MAP_UNITS_PER_WORLD - originY).toFloat()
            val nNames = buf.short.toInt() and 0xFFFF
            val names = ArrayList<String>(nNames)
            repeat(nNames) {
                val len = buf.get().toInt() and 0xFF
                names.add(String(bytes, buf.position(), len, Charsets.UTF_8))
                buf.position(buf.position() + len)
            }
            val nLayers = buf.get().toInt() and 0xFF
            val layers = arrayOfNulls<MapLayer>(LayerKind.COUNT)
            val ox = Math.round(minLon / Q).toInt()
            val oy = Math.round(minLat / Q).toInt()
            val xCache = HashMap<Int, Float>()
            repeat(nLayers) {
                val kind = buf.get().toInt() and 0xFF
                val count = varint(buf)
                val cls = ByteArray(count)
                val flags = ByteArray(count)
                val name = IntArray(count)
                val ringOffset = IntArray(count + 1)
                val pointStarts = ArrayList<Int>()
                val latQ = IntArrayBuilder()
                val lonQ = IntArrayBuilder()
                for (f in 0 until count) {
                    cls[f] = buf.get()
                    flags[f] = buf.get()
                    name[f] = varint(buf)
                    val rings = varint(buf)
                    ringOffset[f] = pointStarts.size
                    repeat(rings) {
                        pointStarts.add(latQ.size)
                        val n = varint(buf)
                        var x = ox
                        var y = oy
                        repeat(n) {
                            x += zigzag(varint(buf))
                            y += zigzag(varint(buf))
                            lonQ.add(x)
                            latQ.add(y)
                        }
                    }
                }
                ringOffset[count] = pointStarts.size
                pointStarts.add(latQ.size)
                val lat = latQ.toArray()
                val lon = lonQ.toArray()
                val px = FloatArray(lon.size)
                val py = FloatArray(lat.size)
                for (i in lon.indices) {
                    px[i] = xCache.getOrPut(lon[i]) { (Mercator.x(lon[i] * Q) * MAP_UNITS_PER_WORLD - originX).toFloat() }
                    py[i] = (Mercator.y(lat[i] * Q) * MAP_UNITS_PER_WORLD - originY).toFloat()
                }
                val minX = FloatArray(count)
                val minY = FloatArray(count)
                val maxX = FloatArray(count)
                val maxY = FloatArray(count)
                val pointOffset = pointStarts.toIntArray()
                for (f in 0 until count) {
                    var a = Float.MAX_VALUE
                    var b = Float.MAX_VALUE
                    var c = -Float.MAX_VALUE
                    var d = -Float.MAX_VALUE
                    for (p in pointOffset[ringOffset[f]] until pointOffset[ringOffset[f + 1]]) {
                        a = min(a, px[p]); b = min(b, py[p]); c = max(c, px[p]); d = max(d, py[p])
                    }
                    minX[f] = a; minY[f] = b; maxX[f] = c; maxY[f] = d
                }
                if (kind < LayerKind.COUNT) {
                    layers[kind] = MapLayer(kind, cls, flags, name, ringOffset, pointOffset, px, py, lat, lon, minX, minY, maxX, maxY)
                }
            }
            val filled = Array(LayerKind.COUNT) { k ->
                layers[k] ?: MapLayer(k, ByteArray(0), ByteArray(0), IntArray(0), IntArray(1), IntArray(1),
                    FloatArray(0), FloatArray(0), IntArray(0), IntArray(0), FloatArray(0), FloatArray(0), FloatArray(0), FloatArray(0))
            }
            return MapData(Bounds(minLat, minLon, maxLat, maxLon), names, filled, originX, originY, width, height)
        }

        private fun varint(buf: ByteBuffer): Int {
            var result = 0
            var shift = 0
            while (true) {
                val c = buf.get().toInt() and 0xFF
                result = result or ((c and 0x7F) shl shift)
                if (c and 0x80 == 0) return result
                shift += 7
            }
        }

        private fun zigzag(n: Int): Int = (n ushr 1) xor -(n and 1)

        fun floorDiv(v: Float, d: Float): Int = floor(v / d).toInt()
    }
}

private class IntArrayBuilder {
    private var data = IntArray(1024)
    var size = 0
        private set

    fun add(v: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }

    fun toArray(): IntArray = data.copyOf(size)
}
