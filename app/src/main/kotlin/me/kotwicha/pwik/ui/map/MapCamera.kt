package me.kotwicha.pwik.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import me.kotwicha.pwik.core.geo.Bounds
import me.kotwicha.pwik.core.geo.LatLon
import me.kotwicha.pwik.core.map.MapData

class MapProjection(
    val map: MapData,
    private val cx: Double,
    private val cy: Double,
    val scale: Float,
    private val width: Float,
    private val height: Float,
) {
    fun x(ux: Float): Float = ((ux - cx) * scale + width / 2).toFloat()
    fun y(uy: Float): Float = ((uy - cy) * scale + height / 2).toFloat()
    fun toScreen(p: LatLon): Offset = Offset(x(map.toUnitsX(p.lon).toFloat()), y(map.toUnitsY(p.lat).toFloat()))
    fun toLatLon(o: Offset): LatLon = map.toLatLon(cx + (o.x - width / 2) / scale, cy + (o.y - height / 2) / scale)
}

@Stable
class MapCameraState(centerX: Double = 0.0, centerY: Double = 0.0, zoom: Float = -1f) {
    var cx by mutableDoubleStateOf(centerX)
        private set
    var cy by mutableDoubleStateOf(centerY)
        private set
    var zoom by mutableFloatStateOf(zoom)
        private set
    var viewport by mutableStateOf(IntSize.Zero)
    var initialized by mutableStateOf(false)
        private set

    var minZoom = -6f
    var maxZoom = 3.6f
    private var extentW = Float.MAX_VALUE
    private var extentH = Float.MAX_VALUE

    val scale: Float get() = 2.0.pow(zoom.toDouble()).toFloat()

    fun projection(map: MapData): MapProjection =
        MapProjection(map, cx, cy, scale, viewport.width.toFloat(), viewport.height.toFloat())

    fun attach(map: MapData) {
        extentW = map.width
        extentH = map.height
    }

    fun set(x: Double, y: Double, z: Float) {
        zoom = z.coerceIn(minZoom, maxZoom)
        cx = x.coerceIn(0.0, extentW.toDouble())
        cy = y.coerceIn(0.0, extentH.toDouble())
        initialized = true
    }

    fun panBy(dx: Float, dy: Float) {
        val s = scale
        set(cx - dx / s, cy - dy / s, zoom)
    }

    fun zoomBy(factor: Float, focus: Offset) {
        if (factor == 1f || factor <= 0f) return
        val w = viewport.width / 2f
        val h = viewport.height / 2f
        val before = scale
        val newZoom = (zoom + (ln(factor.toDouble()) / ln(2.0)).toFloat()).coerceIn(minZoom, maxZoom)
        val after = 2.0.pow(newZoom.toDouble()).toFloat()
        val fx = cx + (focus.x - w) / before
        val fy = cy + (focus.y - h) / before
        set(fx - (focus.x - w) / after, fy - (focus.y - h) / after, newZoom)
    }

    fun zoomForBounds(map: MapData, bounds: Bounds, paddingPx: Float, topPx: Float = 0f, bottomPx: Float = 0f): Triple<Double, Double, Float> {
        val x0 = map.toUnitsX(bounds.minLon)
        val x1 = map.toUnitsX(bounds.maxLon)
        val y0 = map.toUnitsY(bounds.maxLat)
        val y1 = map.toUnitsY(bounds.minLat)
        val w = max(1f, viewport.width - 2 * paddingPx)
        val h = max(1f, viewport.height - 2 * paddingPx - topPx - bottomPx)
        val sx = w / max(1e-3, x1 - x0)
        val sy = h / max(1e-3, y1 - y0)
        val z = (ln(min(sx, sy)) / ln(2.0)).toFloat().coerceIn(minZoom, maxZoom - 0.6f)
        val shift = (topPx - bottomPx) / 2.0 / 2.0.pow(z.toDouble())
        return Triple((x0 + x1) / 2, (y0 + y1) / 2 - shift, z)
    }

    fun fit(map: MapData, bounds: Bounds, paddingPx: Float, topPx: Float = 0f, bottomPx: Float = 0f) {
        val (x, y, z) = zoomForBounds(map, bounds, paddingPx, topPx, bottomPx)
        set(x, y, z)
    }

    suspend fun animateTo(x: Double, y: Double, z: Float, durationMs: Int = 520) {
        val sx = cx
        val sy = cy
        val sz = zoom
        val t = Animatable(0f)
        t.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) {
            val f = value.toDouble()
            set(sx + (x - sx) * f, sy + (y - sy) * f, sz + (z - sz) * value)
        }
    }

    suspend fun animateToBounds(map: MapData, bounds: Bounds, paddingPx: Float, topPx: Float = 0f, bottomPx: Float = 0f) {
        val (x, y, z) = zoomForBounds(map, bounds, paddingPx, topPx, bottomPx)
        animateTo(x, y, z)
    }

    suspend fun animateToPoint(map: MapData, p: LatLon, z: Float = zoom) =
        animateTo(map.toUnitsX(p.lon), map.toUnitsY(p.lat), z)
}
