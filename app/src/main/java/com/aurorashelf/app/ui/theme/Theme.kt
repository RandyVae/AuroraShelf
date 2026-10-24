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
    // Dark maroon instead of white: white on #FF4F7B is only 3.15:1, below WCAG AA 4.5:1,
    // while #3F0015 on the same coral is 5.45:1 and keeps the brand tone.
    onPrimary = Color(0xFF3F0015),
    primaryContainer = Color(0xFFFFB0C2),
    onPrimaryContainer = Color(0xFF3F0015),
    background = AuroraDarkBackground,
    onBackground = AuroraDarkText,
    surface = AuroraDarkSurface,
    onSurface = AuroraDarkText,
    surfaceVariant = Color(0xFF1B2330),
    onSurfaceVariant = AuroraDarkMuted,
    // Raised from #4E596A (2.53:1) so control outlines reach the 3:1 non-text threshold.
    outline = Color(0xFF5C6879),
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

/**
 * Immersive media surfaces stay black on purpose (HIG allows media apps to keep dark-only
 * chrome). This token keeps that exception explicit instead of scattering raw hex values.
 */
internal val AuroraImmersiveBackground = Color(0xFF000000)

/** Neutral glass container tints for the floating navigation, one per appearance. */
internal val AuroraGlassContainerLight = Color(0xFFFAFAFA)
internal val AuroraGlassContainerDark = Color(0xFF121212)
internal const val AuroraGlassContainerAlpha = 0.4f

/** Opaque ink surface used by forced-dark glass panels (player controls, immersive chrome). */
internal val AuroraInkSurface = Color(0xFF111722)
internal val AuroraInkContent = Color.White

/** Reading surface for the comic reader; immersive media chrome stays dark by design. */
internal val AuroraReaderBackground = Color(0xFF111111)


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
