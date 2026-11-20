package me.kotwicha.pwik.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.ceil

class LabelSprites private constructor(
    private val typeface: Typeface?,
    private val textSize: Float,
    private val textColor: Int,
    private val haloColor: Int,
) {
    private val sprites = HashMap<String, Bitmap>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        this.typeface = this@LabelSprites.typeface
        this.textSize = this@LabelSprites.textSize
        strokeJoin = Paint.Join.ROUND
    }

    fun get(text: String): Bitmap = sprites.getOrPut(text) { render(text) }

    private fun render(text: String): Bitmap {
        val halo = textSize * 0.16f
        val metrics = paint.fontMetrics
        val width = ceil(paint.measureText(text) + halo * 2).toInt().coerceAtLeast(1)
        val height = ceil(metrics.descent - metrics.ascent + halo * 2).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val baseline = halo - metrics.ascent
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = halo * 2
        paint.color = haloColor
        canvas.drawText(text, halo, baseline, paint)
        paint.style = Paint.Style.FILL
        paint.color = textColor
        canvas.drawText(text, halo, baseline, paint)
        val gpu = bitmap.copy(Bitmap.Config.HARDWARE, false) ?: return bitmap
        bitmap.recycle()
        return gpu
    }

    companion object {
        private var shared: LabelSprites? = null

        @Synchronized
        fun obtain(typeface: Typeface?, textSize: Float, textColor: Int, haloColor: Int): LabelSprites {
            val current = shared
            if (current != null && current.typeface == typeface && current.textSize == textSize &&
                current.textColor == textColor && current.haloColor == haloColor
            ) return current
            return LabelSprites(typeface, textSize, textColor, haloColor).also { shared = it }
        }
    }
}
