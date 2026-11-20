package me.kotwicha.pwik.ui.map

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.kotwicha.pwik.core.geo.LatLon
import me.kotwicha.pwik.core.map.MapData
import me.kotwicha.pwik.ui.theme.AppFont

private const val LEVEL_BIAS = 0.3f

internal val MIN_TILE_SCALE = 2f.pow(LEVEL_BIAS - 1f)

private fun levelFor(zoom: Float): Int = ceil(zoom - LEVEL_BIAS).toInt().coerceIn(-6, 4)

@Composable
fun rememberMapTheme(): MapTheme {
    val density = LocalDensity.current.density
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val resolver = LocalFontFamilyResolver.current
    val regular = remember(resolver) { resolver.resolve(AppFont, FontWeight.Medium).value as? Typeface }
    val bold = remember(resolver) { resolver.resolve(AppFont, FontWeight.Bold).value as? Typeface }
    return remember(dark, density, regular, bold) {
        MapTheme(if (dark) MapPalette.Dark else MapPalette.Light, density, regular, bold)
    }
}

fun tilesCovering(map: MapData, cx: Double, cy: Double, zoom: Float, width: Int, height: Int): List<Long> {
    val scale = 2.0.pow(zoom.toDouble())
    val level = levelFor(zoom)
    val tileUnits = TILE_PX / levelScale(level)
    val maxTx = floor(map.width / tileUnits).toInt()
    val maxTy = floor(map.height / tileUnits).toInt()
    val tx0 = floor((cx - width / 2 / scale) / tileUnits).toInt().coerceAtLeast(0)
    val ty0 = floor((cy - height / 2 / scale) / tileUnits).toInt().coerceAtLeast(0)
    val tx1 = floor((cx + width / 2 / scale) / tileUnits).toInt().coerceAtMost(maxTx)
    val ty1 = floor((cy + height / 2 / scale) / tileUnits).toInt().coerceAtMost(maxTy)
    return buildList {
        for (ty in ty0..ty1) for (tx in tx0..tx1) add(tileKey(level, tx, ty))
    }
}

@Composable
fun MapView(
    map: MapData,
    camera: MapCameraState,
    modifier: Modifier = Modifier,
    onTap: ((LatLon, Offset, MapProjection) -> Unit)? = null,
    tileOverlay: TileOverlay? = null,
    overlay: DrawScope.(MapProjection) -> Unit = {},
    content: @Composable BoxScope.() -> Unit = {},
) {
    val theme = rememberMapTheme()
    var tick by remember { mutableIntStateOf(0) }
    val cache = remember(map, theme, tileOverlay?.key) { TileCache.obtain(map, theme, tileOverlay) }
    DisposableEffect(cache) {
        val listener: () -> Unit = { tick++ }
        cache.addListener(listener)
        onDispose { cache.removeListener(listener) }
    }
    SideEffect { camera.attach(map) }

    val scope = rememberCoroutineScope()
    val tapHandler by rememberUpdatedState(onTap)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val doubleTapTimeout = LocalViewConfiguration.current.doubleTapTimeoutMillis
    val src = remember { Rect() }
    val dst = remember { Rect() }
    val bitmapPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
    val landColor = theme.palette.land

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { camera.viewport = it }
                .pointerInput(camera, map) {
                    var fling: Job? = null
                    var lastTapAt = 0L
                    var lastTapPos = Offset.Zero
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fling?.cancel()
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var moved = false
                        var travel = 0f
                        var multi = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size > 1) multi = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            if (!moved) {
                                travel += hypot(pan.x, pan.y)
                                if (travel > touchSlop || abs(zoom - 1f) > 0.02f) moved = true
                            }
                            if (moved) {
                                if (zoom != 1f) camera.zoomBy(zoom, centroid)
                                if (pan != Offset.Zero) camera.panBy(pan.x, pan.y)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                            if (centroid != Offset.Unspecified) {
                                tracker.addPosition(event.changes.first().uptimeMillis, centroid)
                            }
                        }
                        if (!moved) {
                            val now = System.currentTimeMillis()
                            val pos = down.position
                            if (now - lastTapAt < doubleTapTimeout && (pos - lastTapPos).getDistance() < touchSlop * 3) {
                                lastTapAt = 0L
                                scope.launch {
                                    val target = camera.zoom + 1f
                                    val proj = camera.projection(map)
                                    val ll = proj.toLatLon(pos)
                                    val ux = map.toUnitsX(ll.lon)
                                    val uy = map.toUnitsY(ll.lat)
                                    val w = camera.viewport.width / 2f
                                    val h = camera.viewport.height / 2f
                                    val s = Math.pow(2.0, target.toDouble())
                                    camera.animateTo(ux - (pos.x - w) / s, uy - (pos.y - h) / s, target, 300)
                                }
                            } else {
                                lastTapAt = now
                                lastTapPos = pos
                                tapHandler?.let { h ->
                                    val proj = camera.projection(map)
                                    h(proj.toLatLon(pos), pos, proj)
                                }
                            }
                        } else if (!multi) {
                            val v = tracker.calculateVelocity()
                            val speed = hypot(v.x, v.y)
                            if (speed > 300f) {
                                val dx = v.x / speed
                                val dy = v.y / speed
                                fling = scope.launch {
                                    var prev = 0f
                                    AnimationState(0f, speed).animateDecay(exponentialDecay(frictionMultiplier = 1.6f)) {
                                        val delta = value - prev
                                        prev = value
                                        camera.panBy(dx * delta, dy * delta)
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            if (tick < 0) return@Canvas
            drawRect(androidx.compose.ui.graphics.Color(landColor))
            if (!camera.initialized || size.width <= 0f) return@Canvas
            val zoom = camera.zoom
            val scale = camera.scale
            val level = levelFor(zoom)
            val tileUnits = TILE_PX / levelScale(level)
            val w = size.width
            val h = size.height
            val left = camera.cx - w / 2 / scale
            val top = camera.cy - h / 2 / scale
            val right = camera.cx + w / 2 / scale
            val bottom = camera.cy + h / 2 / scale
            val maxTx = floor(map.width / tileUnits).toInt()
            val maxTy = floor(map.height / tileUnits).toInt()
            val tx0 = floor(left / tileUnits).toInt().coerceAtLeast(0)
            val ty0 = floor(top / tileUnits).toInt().coerceAtLeast(0)
            val tx1 = floor(right / tileUnits).toInt().coerceAtMost(maxTx)
            val ty1 = floor(bottom / tileUnits).toInt().coerceAtMost(maxTy)
            val wanted = ArrayList<Pair<Long, Double>>()
            val ccx = camera.cx
            val ccy = camera.cy
            drawIntoCanvas { c ->
                val canvas = c.nativeCanvas
                fun screenX(ux: Double) = floor((ux - ccx) * scale + w / 2).toInt()
                fun screenY(uy: Double) = floor((uy - ccy) * scale + h / 2).toInt()
                for (ty in ty0..ty1) for (tx in tx0..tx1) {
                    val x0 = screenX(tx * tileUnits.toDouble())
                    val y0 = screenY(ty * tileUnits.toDouble())
                    val x1 = screenX((tx + 1) * tileUnits.toDouble())
                    val y1 = screenY((ty + 1) * tileUnits.toDouble())
                    dst.set(x0, y0, x1, y1)
                    val bmp = cache.get(level, tx, ty)
                    if (bmp != null) {
                        canvas.drawBitmap(bmp, null, dst, bitmapPaint)
                        continue
                    }
                    val cxT = (tx + 0.5) * tileUnits - ccx
                    val cyT = (ty + 0.5) * tileUnits - ccy
                    wanted.add(tileKey(level, tx, ty) to (cxT * cxT + cyT * cyT))
                    var drawn = false
                    for (up in 1..4) {
                        val pl = level - up
                        val div = 1 shl up
                        val px = Math.floorDiv(tx, div)
                        val py = Math.floorDiv(ty, div)
                        val parent = cache.get(pl, px, py) ?: continue
                        val sub = TILE_PX / div
                        val sx = (tx - px * div) * sub
                        val sy = (ty - py * div) * sub
                        src.set(sx, sy, sx + sub, sy + sub)
                        canvas.drawBitmap(parent, src, dst, bitmapPaint)
                        drawn = true
                        break
                    }
                    if (!drawn) {
                        val cl = level + 1
                        for (cyI in 0..1) for (cxI in 0..1) {
                            val child = cache.get(cl, tx * 2 + cxI, ty * 2 + cyI) ?: continue
                            val mx = (x0 + x1) / 2
                            val my = (y0 + y1) / 2
                            dst.set(if (cxI == 0) x0 else mx, if (cyI == 0) y0 else my, if (cxI == 0) mx else x1, if (cyI == 0) my else y1)
                            canvas.drawBitmap(child, null, dst, bitmapPaint)
                        }
                    }
                }
            }
            cache.request(wanted.sortedBy { it.second }.map { it.first })
            overlay(camera.projection(map))
        }
        content()
        Text(
            "© OpenStreetMap",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 3.dp),
        )
    }
}
