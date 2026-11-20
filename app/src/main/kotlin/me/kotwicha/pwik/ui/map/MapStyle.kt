package me.kotwicha.pwik.ui.map

import android.graphics.Typeface

class MapPalette(
    val dark: Boolean,
    val land: Int,
    val residential: Int,
    val industrial: Int,
    val green: Int,
    val park: Int,
    val forest: Int,
    val cemetery: Int,
    val pitch: Int,
    val wetland: Int,
    val water: Int,
    val waterLine: Int,
    val building: Int,
    val buildingEdge: Int,
    val rail: Int,
    val railDash: Int,
    val casing: Int,
    val majorCasing: Int,
    val motorway: Int,
    val primary: Int,
    val secondary: Int,
    val minor: Int,
    val path: Int,
    val label: Int,
    val labelHalo: Int,
    val placeLabel: Int,
    val waterLabel: Int,
) {
    companion object {
        val Light = MapPalette(
            dark = false,
            land = 0xFFF5F2EC.toInt(),
            residential = 0xFFEEEAE3.toInt(),
            industrial = 0xFFEDE8F0.toInt(),
            green = 0xFFDDEFD2.toInt(),
            park = 0xFFCDEBBE.toInt(),
            forest = 0xFFC3E2B3.toInt(),
            cemetery = 0xFFD5E6CE.toInt(),
            pitch = 0xFFC6EBD9.toInt(),
            wetland = 0xFFD4E9E2.toInt(),
            water = 0xFFAAD6EC.toInt(),
            waterLine = 0xFF8EC7E4.toInt(),
            building = 0xFFE3DCD2.toInt(),
            buildingEdge = 0xFFD2C9BC.toInt(),
            rail = 0xFFB5AEA4.toInt(),
            railDash = 0xFFF5F2EC.toInt(),
            casing = 0xFFDCD4C8.toInt(),
            majorCasing = 0xFFE6C98A.toInt(),
            motorway = 0xFFFFD27F.toInt(),
            primary = 0xFFFFE3A3.toInt(),
            secondary = 0xFFFFF0C9.toInt(),
            minor = 0xFFFFFFFF.toInt(),
            path = 0xFFC9BFB1.toInt(),
            label = 0xFF4E4A45.toInt(),
            labelHalo = 0xF2FFFFFF.toInt(),
            placeLabel = 0xFF2D2A26.toInt(),
            waterLabel = 0xFF3E7EA6.toInt(),
        )

        val Dark = MapPalette(
            dark = true,
            land = 0xFF15191E.toInt(),
            residential = 0xFF1A1F25.toInt(),
            industrial = 0xFF1D1B24.toInt(),
            green = 0xFF17241C.toInt(),
            park = 0xFF1A2C20.toInt(),
            forest = 0xFF172A1D.toInt(),
            cemetery = 0xFF1A261E.toInt(),
            pitch = 0xFF17302A.toInt(),
            wetland = 0xFF182A2A.toInt(),
            water = 0xFF143349.toInt(),
            waterLine = 0xFF1C4560.toInt(),
            building = 0xFF252B33.toInt(),
            buildingEdge = 0xFF2E3640.toInt(),
            rail = 0xFF4A4F57.toInt(),
            railDash = 0xFF15191E.toInt(),
            casing = 0xFF0E1115.toInt(),
            majorCasing = 0xFF0E1115.toInt(),
            motorway = 0xFF6B5A35.toInt(),
            primary = 0xFF5A4F38.toInt(),
            secondary = 0xFF46433B.toInt(),
            minor = 0xFF343A42.toInt(),
            path = 0xFF3C434C.toInt(),
            label = 0xFFC4C9D0.toInt(),
            labelHalo = 0xE015191E.toInt(),
            placeLabel = 0xFFE6E9ED.toInt(),
            waterLabel = 0xFF7FB3D6.toInt(),
        )
    }
}

class MapTheme(val palette: MapPalette, val density: Float, val typeface: Typeface?, val boldTypeface: Typeface?) {
    val id: Int = (if (palette.dark) 1 else 0) * 1000 + (density * 100).toInt()
}

object RoadWidth {
    private val stops = floatArrayOf(10f, 12f, 14f, 16f, 18f)

    private val major = floatArrayOf(0.8f, 2.2f, 5.0f, 10f, 22f)
    private val secondary = floatArrayOf(0.6f, 1.6f, 4.0f, 8.5f, 19f)
    private val tertiary = floatArrayOf(0.4f, 1.2f, 3.2f, 7.5f, 17f)
    private val minor = floatArrayOf(0f, 0.4f, 1.8f, 5.0f, 12f)
    private val service = floatArrayOf(0f, 0f, 0.9f, 2.8f, 7f)
    private val path = floatArrayOf(0f, 0f, 0.5f, 1.2f, 2.2f)

    private fun interp(values: FloatArray, zoom: Float): Float {
        if (zoom <= stops.first()) return values.first()
        for (i in 1 until stops.size) {
            if (zoom <= stops[i]) {
                val t = (zoom - stops[i - 1]) / (stops[i] - stops[i - 1])
                return values[i - 1] + (values[i] - values[i - 1]) * t
            }
        }
        return values.last()
    }

    fun dp(roadClass: Int, zoom: Float): Float = when (roadClass) {
        0, 1 -> interp(major, zoom)
        2 -> interp(secondary, zoom)
        3 -> interp(tertiary, zoom)
        4, 5 -> interp(minor, zoom)
        6, 7 -> interp(service, zoom)
        else -> interp(path, zoom)
    }
}
