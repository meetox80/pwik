package me.kotwicha.pwik.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kotwicha.pwik.R
import me.kotwicha.pwik.data.repo.ThemeMode

private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val AppFont = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E6A86),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC6E9F8),
    onPrimaryContainer = Color(0xFF00283A),
    secondary = Color(0xFF4C616B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD4E6F0),
    onSecondaryContainer = Color(0xFF0B1E27),
    tertiary = Color(0xFFB4501B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC9),
    onTertiaryContainer = Color(0xFF3A1200),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF151B1F),
    surface = Color(0xFFF6F8FA),
    onSurface = Color(0xFF151B1F),
    surfaceVariant = Color(0xFFDCE3E8),
    onSurfaceVariant = Color(0xFF40484E),
    outline = Color(0xFF70797F),
    outlineVariant = Color(0xFFC6CED3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F3F6),
    surfaceContainer = Color(0xFFEAEEF2),
    surfaceContainerHigh = Color(0xFFE4E9ED),
    surfaceContainerHighest = Color(0xFFDEE4E8),
    surfaceBright = Color(0xFFFBFCFE),
    surfaceDim = Color(0xFFD7DCE0),
    inverseSurface = Color(0xFF2A3135),
    inverseOnSurface = Color(0xFFEDF1F4),
    inversePrimary = Color(0xFF8AD2EF),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AD2EF),
    onPrimary = Color(0xFF003548),
    primaryContainer = Color(0xFF0A4D64),
    onPrimaryContainer = Color(0xFFC6E9F8),
    secondary = Color(0xFFB4C9D4),
    onSecondary = Color(0xFF1F333C),
    secondaryContainer = Color(0xFF354953),
    onSecondaryContainer = Color(0xFFD4E6F0),
    tertiary = Color(0xFFFFB68F),
    onTertiary = Color(0xFF572000),
    tertiaryContainer = Color(0xFF7C3510),
    onTertiaryContainer = Color(0xFFFFDCC9),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1316),
    onBackground = Color(0xFFDDE3E7),
    surface = Color(0xFF0E1316),
    onSurface = Color(0xFFDDE3E7),
    surfaceVariant = Color(0xFF40484E),
    onSurfaceVariant = Color(0xFFBFC8CE),
    outline = Color(0xFF8A9298),
    outlineVariant = Color(0xFF40484E),
    surfaceContainerLowest = Color(0xFF090D10),
    surfaceContainerLow = Color(0xFF151B1E),
    surfaceContainer = Color(0xFF192024),
    surfaceContainerHigh = Color(0xFF242A2E),
    surfaceContainerHighest = Color(0xFF2E3539),
    surfaceBright = Color(0xFF343A3E),
    surfaceDim = Color(0xFF0E1316),
    inverseSurface = Color(0xFFDDE3E7),
    inverseOnSurface = Color(0xFF2A3135),
    inversePrimary = Color(0xFF0E6A86),
    scrim = Color(0xFF000000),
)

private val base = Typography()

private fun TextStyle.jakarta(weight: Int, size: Int, line: Int, tracking: Double = 0.0) = copy(
    fontFamily = AppFont,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

val AppTypography = Typography(
    displayLarge = base.displayLarge.jakarta(800, 52, 58, -1.0),
    displayMedium = base.displayMedium.jakarta(800, 42, 48, -0.8),
    displaySmall = base.displaySmall.jakarta(800, 34, 40, -0.6),
    headlineLarge = base.headlineLarge.jakarta(800, 30, 36, -0.5),
    headlineMedium = base.headlineMedium.jakarta(700, 26, 32, -0.4),
    headlineSmall = base.headlineSmall.jakarta(700, 22, 28, -0.2),
    titleLarge = base.titleLarge.jakarta(700, 20, 26, -0.1),
    titleMedium = base.titleMedium.jakarta(700, 16, 22),
    titleSmall = base.titleSmall.jakarta(600, 14, 20),
    bodyLarge = base.bodyLarge.jakarta(500, 16, 24),
    bodyMedium = base.bodyMedium.jakarta(500, 14, 20),
    bodySmall = base.bodySmall.jakarta(500, 12, 16, 0.1),
    labelLarge = base.labelLarge.jakarta(700, 14, 20),
    labelMedium = base.labelMedium.jakarta(600, 12, 16, 0.2),
    labelSmall = base.labelSmall.jakarta(600, 11, 14, 0.3),
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Immutable
data class ExtraColors(
    val walk: Color,
    val origin: Color,
    val destination: Color,
    val live: Color,
    val heroTop: Color,
    val heroBottom: Color,
    val onHero: Color,
)

private val LightExtra = ExtraColors(
    walk = Color(0xFF6B7780),
    origin = Color(0xFF0E6A86),
    destination = Color(0xFFD93F3F),
    live = Color(0xFF0B8457),
    heroTop = Color(0xFF0B5A73),
    heroBottom = Color(0xFF1A8FAF),
    onHero = Color(0xFFFFFFFF),
)

private val DarkExtra = ExtraColors(
    walk = Color(0xFF9AA6AE),
    origin = Color(0xFF8AD2EF),
    destination = Color(0xFFFF8A80),
    live = Color(0xFF5FD4A0),
    heroTop = Color(0xFF07303E),
    heroBottom = Color(0xFF0D4C61),
    onHero = Color(0xFFEAF6FB),
)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

object LineColors {
    private val known = mapOf(
        "0" to Color(0xFFD93F3F),
        "8" to Color(0xFFE8710A),
        "9" to Color(0xFF1A73E8),
        "10" to Color(0xFF188038),
        "14" to Color(0xFF8E44AD),
        "15" to Color(0xFFC2185B),
        "16" to Color(0xFF00897B),
    )
    private val spare = listOf(
        Color(0xFF5E35B1), Color(0xFF6D4C41), Color(0xFF3949AB),
        Color(0xFF00838F), Color(0xFFAD1457), Color(0xFF558B2F),
    )

    fun of(line: String): Color = known[line] ?: spare[(line.hashCode() and 0x7FFFFFFF) % spare.size]
}

@Composable
fun PwikTheme(mode: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val extra = if (dark) DarkExtra else LightExtra
    androidx.compose.runtime.CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

val tabular = TextStyle(fontFeatureSettings = "tnum")
