package me.kotwicha.pwik.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import me.kotwicha.pwik.core.geo.LatLon
import me.kotwicha.pwik.core.map.MapData

class UnitLine(val xs: FloatArray, val ys: FloatArray) {
    companion object {
        fun of(map: MapData, points: List<LatLon>): UnitLine = UnitLine(
            FloatArray(points.size) { map.toUnitsX(points[it].lon).toFloat() },
            FloatArray(points.size) { map.toUnitsY(points[it].lat).toFloat() },
        )
    }
}

class UnitPoint(val x: Float, val y: Float) {
    companion object {
        fun of(map: MapData, p: LatLon) = UnitPoint(map.toUnitsX(p.lon).toFloat(), map.toUnitsY(p.lat).toFloat())
    }
}

class OverlayPainter {
    private val path = Path()

    fun line(
        scope: DrawScope,
        proj: MapProjection,
        line: UnitLine,
        color: Color,
        width: Float,
        casing: Color? = null,
        dashed: Boolean = false,
    ) = with(scope) {
        val n = line.xs.size
        if (n < 2) return@with
        path.reset()
        var lx = proj.x(line.xs[0])
        var ly = proj.y(line.ys[0])
        path.moveTo(lx, ly)
        val minStep = 1.5f
        for (i in 1 until n) {
            val x = proj.x(line.xs[i])
            val y = proj.y(line.ys[i])
            if (i < n - 1 && abs(x - lx) < minStep && abs(y - ly) < minStep) continue
            path.lineTo(x, y)
            lx = x
            ly = y
        }
        if (casing != null) {
            drawPath(path, casing, style = Stroke(width + width * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(0.1f, width * 2.1f), 0f) else null
        drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
    }

    fun stop(scope: DrawScope, at: Offset, color: Color, ring: Color, radius: Float, selected: Boolean = false) = with(scope) {
        val r = if (selected) radius * 1.6f else radius
        if (selected) {
            drawCircle(color.copy(alpha = 0.18f), r * 2.8f, at)
            drawCircle(Color.Black.copy(alpha = 0.16f), r * 1.5f, at + Offset(0f, r * 0.2f))
        }
        drawCircle(ring, r * 1.45f, at)
        drawCircle(color, r, at)
        drawCircle(ring, r * 0.42f, at)
    }

    fun origin(scope: DrawScope, at: Offset, color: Color, ring: Color, radius: Float) = with(scope) {
        drawCircle(Color.Black.copy(alpha = 0.18f), radius * 1.7f, at + Offset(0f, radius * 0.25f))
        drawCircle(ring, radius * 1.55f, at)
        drawCircle(color, radius, at)
        drawCircle(ring, radius * 0.45f, at)
    }

    fun pin(scope: DrawScope, at: Offset, color: Color, ring: Color, size: Float) = with(scope) {
        val r = size * 0.42f
        val cy = at.y - size
        path.reset()
        path.moveTo(at.x, at.y)
        path.cubicTo(at.x - r * 0.35f, at.y - r * 0.9f, at.x - r * 1.1f, cy + r * 0.6f, at.x - r, cy)
        path.arcTo(Rect(at.x - r, cy - r, at.x + r, cy + r), 180f, 180f, false)
        path.cubicTo(at.x + r * 1.1f, cy + r * 0.6f, at.x + r * 0.35f, at.y - r * 0.9f, at.x, at.y)
        path.close()
        drawCircle(Color.Black.copy(alpha = 0.18f), r * 0.55f, Offset(at.x, at.y + r * 0.12f))
        drawPath(path, ring, style = Stroke(r * 0.35f))
        drawPath(path, color)
        drawCircle(ring, r * 0.42f, Offset(at.x, cy))
    }

    fun me(scope: DrawScope, at: Offset, color: Color, radius: Float) = with(scope) {
        drawCircle(color.copy(alpha = 0.16f), radius * 3.2f, at)
        drawCircle(Color.White, radius * 1.35f, at)
        drawCircle(color, radius, at)
    }
}
