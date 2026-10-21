package com.myrizz.assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Original MyRizz brand palette: black, deep purple and violet gradients. */
object Brand {
    val Violet = Color(0xFF8B5CF6)
    val VioletSoft = Color(0xFFA78BFA)
    val VioletDeep = Color(0xFF4C1D95)
    val Magenta = Color(0xFFD946EF)

    val gradient: Brush
        get() = Brush.linearGradient(listOf(Color(0xFFA855F7), Color(0xFF7C3AED), Color(0xFF4C1D95)))

    val glow: Brush
        get() = Brush.verticalGradient(listOf(Color(0xFF3B1478), Color(0x00000000)))
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF1B0B3D),
    primaryContainer = Color(0xFF3B1D7A),
    onPrimaryContainer = Color(0xFFEBDDFF),
    secondary = Color(0xFFC4B5FD),
    onSecondary = Color(0xFF1B0B3D),
    background = Color(0xFF0B0714),
    onBackground = Color(0xFFF3EEFF),
    surface = Color(0xFF130E1F),
    onSurface = Color(0xFFF3EEFF),
    surfaceVariant = Color(0xFF1E1730),
    onSurfaceVariant = Color(0xFFC9BEE0),
    outline = Color(0xFF4B3F6B),
    outlineVariant = Color(0xFF2E2547),
    error = Color(0xFFFF8A9B),
    errorContainer = Color(0xFF4A1020),
    onErrorContainer = Color(0xFFFFD9DE),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6D3AE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF24005E),
    secondary = Color(0xFF6B5A9E),
    onSecondary = Color.White,
    background = Color(0xFFF7F4FF),
    onBackground = Color(0xFF1B1530),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1530),
    surfaceVariant = Color(0xFFEDE7FA),
    onSurfaceVariant = Color(0xFF4D4466),
    outline = Color(0xFFB4A8D4),
    outlineVariant = Color(0xFFDCD3F0),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val AppTypography: Typography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun MyRizzTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
