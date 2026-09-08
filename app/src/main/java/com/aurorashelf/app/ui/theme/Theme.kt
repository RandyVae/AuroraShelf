package com.aurorashelf.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val AuroraCoral = Color(0xFFFF4F7B)
val AuroraBackground = Color(0xFF070B12)
val AuroraSurface = Color(0xFF111722)
val AuroraText = Color(0xFFF5F7FB)
val AuroraMuted = Color(0xFF9CA5B3)

private val AuroraColors = darkColorScheme(
    primary = AuroraCoral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFB0C2),
    onPrimaryContainer = Color(0xFF3F0015),
    background = AuroraBackground,
    onBackground = AuroraText,
    surface = AuroraSurface,
    onSurface = AuroraText,
    surfaceVariant = Color(0xFF1B2330),
    onSurfaceVariant = AuroraMuted,
    outline = Color(0xFF4E596A),
    error = Color(0xFFFFB4AB),
)

private val AuroraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun AuroraTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        AuroraColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AuroraTypography,
        shapes = AuroraShapes,
        content = content,
    )
}
