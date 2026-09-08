package com.aurorashelf.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val AuroraCoral = Color(0xFFFF4F7B)
private val AuroraDarkBackground = Color(0xFF070B12)
private val AuroraDarkSurface = Color(0xFF111722)
private val AuroraDarkText = Color(0xFFF5F7FB)
private val AuroraDarkMuted = Color(0xFFB6BFCC)

private val AuroraDarkColors = darkColorScheme(
    primary = AuroraCoral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFB0C2),
    onPrimaryContainer = Color(0xFF3F0015),
    background = AuroraDarkBackground,
    onBackground = AuroraDarkText,
    surface = AuroraDarkSurface,
    onSurface = AuroraDarkText,
    surfaceVariant = Color(0xFF1B2330),
    onSurfaceVariant = AuroraDarkMuted,
    outline = Color(0xFF4E596A),
    outlineVariant = Color(0xFF303946),
    error = Color(0xFFFFB4AB),
)

private val AuroraLightColors = lightColorScheme(
    primary = Color(0xFFB42352),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3F0017),
    background = Color(0xFFF8F9FE),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE9EDF5),
    onSurfaceVariant = Color(0xFF555C68),
    outline = Color(0xFF737985),
    outlineVariant = Color(0xFFC3C7D0),
    error = Color(0xFFBA1A1A),
)

internal fun auroraFallbackColorScheme(darkTheme: Boolean) =
    if (darkTheme) AuroraDarkColors else AuroraLightColors

private val AuroraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun AuroraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        auroraFallbackColorScheme(darkTheme)
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AuroraTypography,
        shapes = AuroraShapes,
        content = content,
    )
}
