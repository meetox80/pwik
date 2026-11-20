package me.kotwicha.pwik.ui.map

import android.graphics.Canvas

interface TileOverlay {
    val key: String

    fun draw(canvas: Canvas, level: Int, left: Float, top: Float, scale: Float)
}
