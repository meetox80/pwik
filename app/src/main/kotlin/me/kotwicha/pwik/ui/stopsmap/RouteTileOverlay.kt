package me.kotwicha.pwik.ui.stopsmap

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import me.kotwicha.pwik.ui.map.MapTheme
import me.kotwicha.pwik.ui.map.TILE_PX
import me.kotwicha.pwik.ui.map.TileOverlay
import me.kotwicha.pwik.ui.map.rememberMapTheme

class RouteTileOverlay(private val geometry: NetworkGeometry, theme: MapTheme) : TileOverlay {
    private val land = Color(theme.palette.land)
    private val colors = IntArray(geometry.routes.size) { lerp(land, geometry.routes[it].color, 0.62f).toArgb() }
    private val width = 3f * theme.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override val key: String = "routes:${System.identityHashCode(geometry)}:${theme.id}"

    @Synchronized
    override fun draw(canvas: Canvas, level: Int, left: Float, top: Float, scale: Float) {
        val units = TILE_PX / scale
        val margin = width / scale
        val chunks = geometry.tileChunks(level)
        canvas.save()
        canvas.scale(scale, scale)
        canvas.translate(-left, -top)
        paint.strokeWidth = width / scale
        for (chunk in chunks) {
            if (chunk.maxX < left - margin || chunk.minX > left + units + margin) continue
            if (chunk.maxY < top - margin || chunk.minY > top + units + margin) continue
            paint.color = colors[chunk.route]
            canvas.drawPath(chunk.path, paint)
        }
        canvas.restore()
    }
}

@Composable
fun rememberRouteOverlay(geometry: NetworkGeometry): RouteTileOverlay {
    val theme = rememberMapTheme()
    return remember(geometry, theme) { RouteTileOverlay(geometry, theme) }
}
