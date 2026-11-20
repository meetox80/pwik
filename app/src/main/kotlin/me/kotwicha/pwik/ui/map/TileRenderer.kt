package me.kotwicha.pwik.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import me.kotwicha.pwik.core.map.LayerKind
import me.kotwicha.pwik.core.map.MapData
import me.kotwicha.pwik.core.map.MapLayer
import me.kotwicha.pwik.core.map.RoadFlag

const val TILE_PX = 512

fun levelScale(level: Int): Float = 2.0.pow(level).toFloat()

fun styleZoom(level: Int, density: Float): Float = level + 16f - (ln(density.toDouble()) / ln(2.0)).toFloat()

class StreetLabel(val text: String, val xs: FloatArray, val ys: FloatArray, val offset: Float, val size: Float, val bounds: RectF)

class PointLabel(val text: String, val x: Float, val y: Float, val size: Float, val bold: Boolean, val color: Int, val bounds: RectF)

class LabelLayout(val streets: List<StreetLabel>, val points: List<PointLabel>) {

    companion object {
        fun build(map: MapData, theme: MapTheme, level: Int): LabelLayout {
            val zoom = styleZoom(level, theme.density)
            val scale = levelScale(level)
            val d = theme.density
            val placed = ArrayList<RectF>()
            val grid = HashMap<Long, MutableList<RectF>>()
            fun cells(r: RectF, f: (Long) -> Unit) {
                val c0 = (r.left / 256f).toInt() - 1
                val c1 = (r.right / 256f).toInt()
                val r0 = (r.top / 256f).toInt() - 1
                val r1 = (r.bottom / 256f).toInt()
                for (cx in c0..c1) for (cy in r0..r1) f((cx.toLong() shl 32) xor (cy.toLong() and 0xFFFFFFFFL))
            }
            fun free(r: RectF): Boolean {
                var ok = true
                cells(r) { k -> grid[k]?.forEach { if (RectF.intersects(it, r)) ok = false } }
                return ok
            }
            fun take(r: RectF) {
                placed.add(r)
                cells(r) { k -> grid.getOrPut(k) { ArrayList() }.add(r) }
            }
            val measure = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = theme.boldTypeface }

            val points = ArrayList<PointLabel>()
            val places = map.layer(LayerKind.PLACE)
            val placeOrder = (0 until places.count).sortedBy { places.cls[it].toInt() }
            for (f in placeOrder) {
                val cls = places.cls[f].toInt()
                val minZoom = when (cls) { 0 -> 9f; 1 -> 12f; else -> 14.5f }
                if (zoom < minZoom) continue
                if (cls == 0 && zoom > 17.5f) continue
                val text = map.nameOf(places.name[f]) ?: continue
                val size = d * when (cls) { 0 -> if (zoom < 13) 15f else 18f; 1 -> 13f; else -> 11.5f }
                measure.textSize = size
                val w = measure.measureText(text)
                val p = places.firstPoint(f)
                val x = places.x[p] * scale
                val y = places.y[p] * scale
                val r = RectF(x - w / 2 - 6 * d, y - size, x + w / 2 + 6 * d, y + size * 0.5f)
                if (!free(r)) continue
                take(r)
                points.add(PointLabel(text, x, y, size, cls <= 1, if (cls == 2) theme.palette.label else theme.palette.placeLabel, r))
            }

            val streets = ArrayList<StreetLabel>()
            if (zoom >= 15f) {
                val roads = map.layer(LayerKind.ROAD)
                val size = d * if (zoom >= 17f) 12.5f else 11.5f
                measure.textSize = size
                measure.typeface = theme.typeface
                val lastByName = HashMap<Int, MutableList<Pair<Float, Float>>>()
                val order = (0 until roads.count)
                    .filter { roads.name[it] > 0 && roads.cls[it] <= 7 }
                    .sortedWith(compareBy({ roads.cls[it] }, { -(roads.maxX[it] - roads.minX[it] + roads.maxY[it] - roads.minY[it]) }))
                for (f in order) {
                    val text = map.nameOf(roads.name[f]) ?: continue
                    val tw = measure.measureText(text)
                    val run = bestRun(roads, f, scale) ?: continue
                    val (xs, ys, len) = run
                    if (len < tw + 28 * d) continue
                    val cx = xs[xs.size / 2]
                    val cy = ys[ys.size / 2]
                    val spacing = 420f * d
                    val prior = lastByName[roads.name[f]]
                    if (prior != null && prior.any { hypot(it.first - cx, it.second - cy) < spacing }) continue
                    val r = RectF(xs.min() - size * 0.6f, ys.min() - size * 0.6f, xs.max() + size * 0.6f, ys.max() + size * 0.6f)
                    if (!free(r)) continue
                    take(r)
                    lastByName.getOrPut(roads.name[f]) { ArrayList() }.add(cx to cy)
                    val reversed = xs.last() < xs.first()
                    val fx = if (reversed) xs.reversedArray() else xs
                    val fy = if (reversed) ys.reversedArray() else ys
                    streets.add(StreetLabel(text, fx, fy, (len - tw) / 2, size, r))
                }
            }
            return LabelLayout(streets, points)
        }

        private fun bestRun(layer: MapLayer, f: Int, scale: Float): Triple<FloatArray, FloatArray, Float>? {
            val s = layer.firstPoint(f)
            val e = layer.endPoint(f)
            if (e - s < 2) return null
            var bestStart = s
            var bestEnd = s
            var bestLen = 0f
            var runStart = s
            var runLen = 0f
            var prevAngle = Float.NaN
            for (p in s + 1 until e) {
                val dx = (layer.x[p] - layer.x[p - 1]) * scale
                val dy = (layer.y[p] - layer.y[p - 1]) * scale
                val segLen = hypot(dx, dy)
                if (segLen < 0.01f) continue
                val angle = atan2(dy, dx)
                if (!prevAngle.isNaN()) {
                    var diff = abs(angle - prevAngle)
                    if (diff > Math.PI) diff = (2 * Math.PI - diff).toFloat()
                    if (diff > 0.45f) {
                        runStart = p - 1
                        runLen = 0f
                    }
                }
                prevAngle = angle
                runLen += segLen
                if (runLen > bestLen) {
                    bestLen = runLen
                    bestStart = runStart
                    bestEnd = p
                }
            }
            if (bestEnd <= bestStart) return null
            val n = bestEnd - bestStart + 1
            val xs = FloatArray(n) { layer.x[bestStart + it] * scale }
            val ys = FloatArray(n) { layer.y[bestStart + it] * scale }
            return Triple(xs, ys, bestLen)
        }
    }
}

class TileRenderer(private val map: MapData, private val theme: MapTheme) {
    private val pal = theme.palette
    private val d = theme.density
    private val stamps = Array(LayerKind.COUNT) { IntArray(map.layers[it].count) }
    private var stamp = 0
    private val path = Path()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
    private val textHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }

    fun render(level: Int, tx: Int, ty: Int, labels: LabelLayout?, overlay: TileOverlay?): Bitmap {
        val bitmap = Bitmap.createBitmap(TILE_PX, TILE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(pal.land)
        val scale = levelScale(level)
        val units = TILE_PX / scale
        val ox = tx * units
        val oy = ty * units
        val zoom = styleZoom(level, d)
        val margin = 24f / scale
        val view = Viewport(ox - margin, oy - margin, ox + units + margin, oy + units + margin, ox, oy, scale)

        if (zoom >= 11f) areas(canvas, view, LayerKind.RESIDENTIAL, pal.residential, minPx = 3f)
        if (zoom >= 11f) areas(canvas, view, LayerKind.INDUSTRIAL, pal.industrial, minPx = 3f)
        areas(canvas, view, LayerKind.FOREST, pal.forest, minPx = 2f)
        areas(canvas, view, LayerKind.GREEN, pal.green, minPx = 2f) { f -> if (map.layer(LayerKind.GREEN).cls[f].toInt() == 1) pal.park else pal.green }
        if (zoom >= 12f) areas(canvas, view, LayerKind.CEMETERY, pal.cemetery, minPx = 2f)
        if (zoom >= 13f) areas(canvas, view, LayerKind.PITCH, pal.pitch, minPx = 2f)
        if (zoom >= 12f) areas(canvas, view, LayerKind.WETLAND, pal.wetland, minPx = 2f)
        waterways(canvas, view, zoom)
        areas(canvas, view, LayerKind.WATER, pal.water, minPx = 1.5f)
        if (zoom >= 14.5f) buildings(canvas, view, zoom)
        if (zoom >= 11.5f) rail(canvas, view, zoom)
        roads(canvas, view, zoom)
        overlay?.draw(canvas, level, ox, oy, scale)
        if (labels != null) labels(canvas, view, labels)
        return bitmap
    }

    private class Viewport(
        val minX: Float, val minY: Float, val maxX: Float, val maxY: Float,
        val ox: Float, val oy: Float, val scale: Float,
    ) {
        fun px(x: Float) = (x - ox) * scale
        fun py(y: Float) = (y - oy) * scale
    }

    private inline fun visible(layerKind: Int, v: Viewport, crossinline f: (Int) -> Unit) {
        val layer = map.layers[layerKind]
        val marks = stamps[layerKind]
        stamp++
        val s = stamp
        map.grids[layerKind].query(v.minX, v.minY, v.maxX, v.maxY) { i ->
            if (marks[i] == s) return@query
            marks[i] = s
            if (layer.maxX[i] < v.minX || layer.minX[i] > v.maxX || layer.maxY[i] < v.minY || layer.minY[i] > v.maxY) return@query
            f(i)
        }
    }

    private fun buildPath(layer: MapLayer, f: Int, v: Viewport, closed: Boolean): Path {
        path.rewind()
        path.fillType = Path.FillType.EVEN_ODD
        for (r in layer.ringOffset[f] until layer.ringOffset[f + 1]) {
            val s = layer.pointOffset[r]
            val e = layer.pointOffset[r + 1]
            if (e - s < 2) continue
            path.moveTo(v.px(layer.x[s]), v.py(layer.y[s]))
            var lx = layer.x[s]
            var ly = layer.y[s]
            for (p in s + 1 until e) {
                val x = layer.x[p]
                val y = layer.y[p]
                if (p != e - 1 && abs(x - lx) * v.scale < 0.6f && abs(y - ly) * v.scale < 0.6f) continue
                path.lineTo(v.px(x), v.py(y))
                lx = x; ly = y
            }
            if (closed) path.close()
        }
        return path
    }

    private fun areas(canvas: Canvas, v: Viewport, kind: Int, color: Int, minPx: Float, colorOf: ((Int) -> Int)? = null) {
        val layer = map.layers[kind]
        visible(kind, v) { f ->
            if ((layer.maxX[f] - layer.minX[f]) * v.scale < minPx && (layer.maxY[f] - layer.minY[f]) * v.scale < minPx) return@visible
            fill.color = colorOf?.invoke(f) ?: color
            canvas.drawPath(buildPath(layer, f, v, closed = true), fill)
        }
    }

    private fun buildings(canvas: Canvas, v: Viewport, zoom: Float) {
        val layer = map.layers[LayerKind.BUILDING]
        val edges = zoom >= 16f
        fill.color = pal.building
        stroke.color = pal.buildingEdge
        stroke.strokeWidth = 0.7f * d
        stroke.pathEffect = null
        val fade = ((zoom - 14.5f) / 1.0f).coerceIn(0f, 1f)
        fill.alpha = (255 * fade).toInt()
        visible(LayerKind.BUILDING, v) { f ->
            val p = buildPath(layer, f, v, closed = true)
            canvas.drawPath(p, fill)
            if (edges) canvas.drawPath(p, stroke)
        }
        fill.alpha = 255
    }

    private fun waterways(canvas: Canvas, v: Viewport, zoom: Float) {
        val layer = map.layers[LayerKind.WATERWAY]
        stroke.color = pal.waterLine
        stroke.pathEffect = null
        visible(LayerKind.WATERWAY, v) { f ->
            val w = when (layer.cls[f].toInt()) {
                0 -> RoadWidth.dp(2, zoom) * 0.9f + 0.6f
                1 -> if (zoom < 13) 0f else RoadWidth.dp(4, zoom) * 0.6f
                else -> if (zoom < 13.5f) 0f else RoadWidth.dp(7, zoom) * 0.9f
            }
            if (w <= 0.05f) return@visible
            stroke.strokeWidth = w * d
            canvas.drawPath(buildPath(layer, f, v, closed = false), stroke)
        }
    }

    private fun rail(canvas: Canvas, v: Viewport, zoom: Float) {
        val layer = map.layers[LayerKind.RAIL]
        val width = (if (zoom < 14) 1.2f else 2.6f) * d
        visible(LayerKind.RAIL, v) { f ->
            val p = buildPath(layer, f, v, closed = false)
            stroke.pathEffect = null
            stroke.color = pal.rail
            stroke.strokeWidth = width
            canvas.drawPath(p, stroke)
            if (zoom >= 14f) {
                stroke.color = pal.railDash
                stroke.strokeWidth = width * 0.45f
                stroke.pathEffect = DashPathEffect(floatArrayOf(6f * d, 6f * d), 0f)
                canvas.drawPath(p, stroke)
                stroke.pathEffect = null
            }
        }
    }

    private fun roadFill(cls: Int): Int = when (cls) {
        0 -> pal.motorway
        1 -> pal.primary
        2 -> pal.secondary
        8, 9, 10 -> pal.path
        else -> pal.minor
    }

    private fun roads(canvas: Canvas, v: Viewport, zoom: Float) {
        val layer = map.layers[LayerKind.ROAD]
        val buckets = Array(11) { ArrayList<Int>() }
        visible(LayerKind.ROAD, v) { f -> buckets[layer.cls[f].toInt().coerceIn(0, 10)].add(f) }
        val drawOrder = intArrayOf(10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0)
        val pathDash = DashPathEffect(floatArrayOf(3f * d, 3f * d), 0f)

        if (zoom >= 15.5f) {
            for (cls in intArrayOf(10, 9, 8)) {
                val w = RoadWidth.dp(cls, zoom) * d
                if (w < 0.3f) continue
                stroke.color = pal.path
                stroke.strokeWidth = w
                stroke.pathEffect = pathDash
                stroke.strokeCap = Paint.Cap.BUTT
                for (f in buckets[cls]) canvas.drawPath(buildPath(layer, f, v, closed = false), stroke)
            }
            stroke.strokeCap = Paint.Cap.ROUND
            stroke.pathEffect = null
        }

        val casingFrom = 13f
        for (pass in 0..1) {
            for (cls in drawOrder) {
                if (cls >= 8) continue
                val w = RoadWidth.dp(cls, zoom) * d
                if (w < 0.25f) continue
                if (pass == 0) {
                    if (zoom < casingFrom) continue
                    stroke.color = if (cls <= 2) pal.majorCasing else pal.casing
                    stroke.strokeWidth = w + max(1.2f * d, w * 0.18f)
                } else {
                    stroke.color = roadFill(cls)
                    stroke.strokeWidth = w
                }
                for (f in buckets[cls]) {
                    val tunnel = layer.flags[f].toInt() and RoadFlag.TUNNEL != 0
                    stroke.alpha = if (tunnel) 110 else 255
                    canvas.drawPath(buildPath(layer, f, v, closed = false), stroke)
                }
                stroke.alpha = 255
            }
        }
    }

    private fun labels(canvas: Canvas, v: Viewport, labels: LabelLayout) {
        val left = v.ox * v.scale
        val top = v.oy * v.scale
        val tileRect = RectF(left - 4, top - 4, left + TILE_PX + 4, top + TILE_PX + 4)
        textHalo.color = pal.labelHalo
        for (s in labels.streets) {
            if (!RectF.intersects(s.bounds, tileRect)) continue
            path.rewind()
            path.fillType = Path.FillType.WINDING
            path.moveTo(s.xs[0] - left, s.ys[0] - top)
            for (i in 1 until s.xs.size) path.lineTo(s.xs[i] - left, s.ys[i] - top)
            textFill.typeface = theme.typeface
            textHalo.typeface = theme.typeface
            textFill.textSize = s.size
            textHalo.textSize = s.size
            textHalo.strokeWidth = 3f * d
            textFill.color = pal.label
            val vOffset = s.size * 0.35f
            canvas.drawTextOnPath(s.text, path, s.offset, vOffset, textHalo)
            canvas.drawTextOnPath(s.text, path, s.offset, vOffset, textFill)
        }
        textFill.textAlign = Paint.Align.CENTER
        textHalo.textAlign = Paint.Align.CENTER
        for (p in labels.points) {
            if (!RectF.intersects(p.bounds, tileRect)) continue
            val tf = if (p.bold) theme.boldTypeface else theme.typeface
            textFill.typeface = tf
            textHalo.typeface = tf
            textFill.textSize = p.size
            textHalo.textSize = p.size
            textHalo.strokeWidth = 3.5f * d
            textFill.color = p.color
            canvas.drawText(p.text, p.x - left, p.y - top, textHalo)
            canvas.drawText(p.text, p.x - left, p.y - top, textFill)
        }
        textFill.textAlign = Paint.Align.LEFT
        textHalo.textAlign = Paint.Align.LEFT
    }
}
