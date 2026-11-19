@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.aurorashelf.app.ui.theme

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.pow

val AuroraCoral = Color(0xFFFF4F7B)
private val AuroraDarkBackground = Color(0xFF0A0D14)
private val AuroraDarkSurface = Color(0xFF10141D)
private val AuroraDarkText = Color(0xFFF3F5FA)
private val AuroraDarkMuted = Color(0xFFB8C0CE)

/**
 * Material 3 Expressive tonal dark fallback scheme with full 5-tier `surfaceContainer*` elevation roles.
 */
private val AuroraDarkColors = darkColorScheme(
    primary = AuroraCoral,
    // Dark maroon on coral reaches 5.45:1 (WCAG AA >= 4.5:1), whereas white on #FF4F7B is only 3.15:1.
    onPrimary = Color(0xFF3F0015),
    primaryContainer = Color(0xFF7B1637),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFE5BDC7),
    onSecondary = Color(0xFF432931),
    secondaryContainer = Color(0xFF5B3F47),
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFFEFBD94),
    onTertiary = Color(0xFF48290C),
    tertiaryContainer = Color(0xFF613F20),
    onTertiaryContainer = Color(0xFFFFDCC2),
    background = AuroraDarkBackground,
    onBackground = AuroraDarkText,
    surface = AuroraDarkSurface,
    onSurface = AuroraDarkText,
    surfaceDim = Color(0xFF0B0E15),
    surfaceBright = Color(0xFF313641),
    surfaceContainerLowest = Color(0xFF06080D),
    surfaceContainerLow = Color(0xFF151A25),
    surfaceContainer = Color(0xFF191F2C),
    surfaceContainerHigh = Color(0xFF232A38),
    surfaceContainerHighest = Color(0xFF2E3545),
    surfaceVariant = Color(0xFF232A38),
    onSurfaceVariant = AuroraDarkMuted,
    outline = Color(0xFF6D7788),
    outlineVariant = Color(0xFF363F4E),
    inverseSurface = Color(0xFFE6E8F0),
    inverseOnSurface = Color(0xFF1C2029),
    inversePrimary = Color(0xFFB42352),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
)

/**
 * Material 3 Expressive tonal light fallback scheme with full 5-tier `surfaceContainer*` elevation roles.
 */
private val AuroraLightColors = lightColorScheme(
    primary = Color(0xFFB42352),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3F0017),
    secondary = Color(0xFF75565F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF2B151C),
    tertiary = Color(0xFF7D5635),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC2),
    onTertiaryContainer = Color(0xFF2F1500),
    background = Color(0xFFFBF8FA),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFBF8FA),
    onSurface = Color(0xFF1B1B1F),
    surfaceDim = Color(0xFFDBD9DD),
    surfaceBright = Color(0xFFFBF8FA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2F6),
    surfaceContainer = Color(0xFFF0ECF2),
    surfaceContainerHigh = Color(0xFFE9E6EC),
    surfaceContainerHighest = Color(0xFFE4E1E7),
    surfaceVariant = Color(0xFFECE6EE),
    onSurfaceVariant = Color(0xFF4F4B54),
    outline = Color(0xFF726C77),
    outlineVariant = Color(0xFFC9C3CE),
    inverseSurface = Color(0xFF303034),
    inverseOnSurface = Color(0xFFF3F0F4),
    inversePrimary = Color(0xFFFFB1C4),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color.Black,
)

/**
 * Increase Contrast (WCAG 1.4.6 AAA >= 7:1 text, >= 4.5:1 non-text) light palette.
 */
internal val AuroraHighContrastLightColors = lightColorScheme(
    primary = Color(0xFF8B0034),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF2A000E),
    secondary = Color(0xFF4E2D37),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF210B12),
    tertiary = Color(0xFF523214),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC2),
    onTertiaryContainer = Color(0xFF200E00),
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceDim = Color(0xFFDADADA),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainer = Color(0xFFEDEDED),
    surfaceContainerHigh = Color(0xFFE5E5E5),
    surfaceContainerHighest = Color(0xFFDDDDDD),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF3D3D3D),
    outline = Color(0xFF525252),
    outlineVariant = Color(0xFF7A7A7A),
    inverseSurface = Color(0xFF141414),
    inverseOnSurface = Color.White,
    inversePrimary = Color(0xFFFFB3C6),
    error = Color(0xFF990000),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color.Black,
)

/**
 * Increase Contrast (WCAG 1.4.6 AAA >= 7:1 text, >= 4.5:1 non-text) dark palette.
 */
internal val AuroraHighContrastDarkColors = darkColorScheme(
    primary = Color(0xFFFFB3C6),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFFFFB0C2),
    onPrimaryContainer = Color(0xFF29000D),
    secondary = Color(0xFFFFD0DC),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF7A5660),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFFFD3AE),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF7D5635),
    onTertiaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0A0A0A),
    onSurface = Color.White,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF333333),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF222222),
    surfaceContainerHighest = Color(0xFF2C2C2C),
    surfaceVariant = Color(0xFF181818),
    onSurfaceVariant = Color(0xFFD8D8D8),
    outline = Color(0xFFB5B5B5),
    outlineVariant = Color(0xFF6B6B6B),
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
    inversePrimary = Color(0xFF8B0034),
    error = Color(0xFFFFB4AB),
    onError = Color.Black,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color.White,
    scrim = Color.Black,
)

/**
 * Semantic domain badges (direct domestic vs overseas route, forum board tags, live status).
 * Always theme-aware so UI screens never hardcode raw `Color(0x...)` literals.
 */
@Immutable
internal data class AuroraSemanticColors(
    val directContainer: Color,
    val onDirectContainer: Color,
    val overseasContainer: Color,
    val onOverseasContainer: Color,
    val liveBadgeContainer: Color,
    val onLiveBadgeContainer: Color,
    val forumPrimaryBadge: Color,
    val forumSecondaryBadge: Color,
    val forumTertiaryBadge: Color,
    val forumFallbackBadge: Color,
    val onForumBadge: Color,
)

private val AuroraSemanticLight = AuroraSemanticColors(
    directContainer = Color(0xFFD7F3DF),
    onDirectContainer = Color(0xFF0E5A27),
    overseasContainer = Color(0xFFD7E8FF),
    onOverseasContainer = Color(0xFF00458F),
    liveBadgeContainer = Color(0xFFC61A3E),
    onLiveBadgeContainer = Color.White,
    forumPrimaryBadge = Color(0xFFB42352),
    forumSecondaryBadge = Color(0xFF1F6E3D),
    forumTertiaryBadge = Color(0xFF00645D),
    forumFallbackBadge = Color(0xFF9A4D00),
    onForumBadge = Color.White,
)

private val AuroraSemanticDark = AuroraSemanticColors(
    directContainer = Color(0xFF143C24),
    onDirectContainer = Color(0xFF86E8A6),
    overseasContainer = Color(0xFF123254),
    onOverseasContainer = Color(0xFF99CCFF),
    liveBadgeContainer = Color(0xFFFF4F7B),
    onLiveBadgeContainer = Color(0xFF3F0015),
    forumPrimaryBadge = Color(0xFF8C1D40),
    forumSecondaryBadge = Color(0xFF1E5E36),
    forumTertiaryBadge = Color(0xFF0F5752),
    forumFallbackBadge = Color(0xFF7D4310),
    onForumBadge = Color.White,
)

internal val LocalAuroraSemanticColors = staticCompositionLocalOf { AuroraSemanticDark }

/**
 * Picks the fallback palette for the requested state: normal light/dark or the increased
 * contrast variants. Dynamic colors are handled by [AuroraTheme] and never take part here.
 */
internal fun auroraColorScheme(darkTheme: Boolean, highContrast: Boolean): ColorScheme = when {
    darkTheme && highContrast -> AuroraHighContrastDarkColors
    darkTheme -> AuroraDarkColors
    highContrast -> AuroraHighContrastLightColors
    else -> AuroraLightColors
}

internal fun auroraFallbackColorScheme(darkTheme: Boolean): ColorScheme =
    auroraColorScheme(darkTheme, highContrast = false)

/**
 * Immersive media surfaces stay dark by design (video player, fullscreen live, comic canvas).
 */
internal val AuroraImmersiveBackground = Color(0xFF000000)
internal val AuroraInkSurface = Color(0xFF141822)
internal val AuroraInkContent = Color.White
internal val AuroraReaderBackground = Color(0xFF101218)

internal val AuroraMediaScrim = Color.Black
internal val AuroraMediaOnImage = Color.White
internal val AuroraMediaInk = Color.Black
internal val AuroraMediaSurface = Color(0xFF161A22)
internal val AuroraMediaSurfaceVariant = Color(0xFF222834)
internal val AuroraMediaMuted = Color(0xFFA8B0BE)
internal val AuroraMediaOutline = Color(0xFF3E4656)
internal val AuroraMediaLabel = Color(0xFFDCE1EA)
internal val AuroraMediaPlaceholder = Color(0xFF1A1E28)
internal val AuroraMediaMeta = Color(0xFF8E96A4)
internal val AuroraMediaRankOutline = Color(0xFF788090)
internal val AuroraMediaRankFill = Color(0xFF141822)
internal val AuroraProfileAccent = Color(0xFF2962FF)
internal val AuroraBrandAccentDeep = Color(0xFFB42352)

/** Shared dimensions for interactive controls and recurring layout primitives. */
internal object AuroraDimens {
    val minTouchTarget = 48.dp
    val chipHeight = 48.dp
    val contentPadding = 16.dp
    val compactGap = 8.dp
    val settingsCardPadding = 18.dp
}

/** Material 3 Expressive rounded shape scale. */
private val AuroraExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private const val MIN_TEXT_CONTRAST = 4.5
private const val MIN_NON_TEXT_CONTRAST = 3.0

private val HIGH_TEXT_CONTRAST_SETTING_KEYS = listOf(
    "high_text_contrast_enabled",
    "accessibility_high_text_contrast_enabled",
    "high_contrast_text",
)

private fun linearizeChannel(channel: Float): Double {
    val value = channel.toDouble().coerceIn(0.0, 1.0)
    return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
}

private fun Color.relativeLuminance(): Double =
    0.2126 * linearizeChannel(red) + 0.7152 * linearizeChannel(green) + 0.0722 * linearizeChannel(blue)

internal fun contrastRatio(foreground: Color, background: Color): Double {
    val foregroundLuminance = foreground.relativeLuminance()
    val backgroundLuminance = background.relativeLuminance()
    val lighter = maxOf(foregroundLuminance, backgroundLuminance)
    val darker = minOf(foregroundLuminance, backgroundLuminance)
    return (lighter + 0.05) / (darker + 0.05)
}

internal fun schemeNeedsFallback(scheme: ColorScheme): Boolean {
    val textPairs = listOf(
        scheme.onBackground to scheme.background,
        scheme.onSurface to scheme.surface,
        scheme.onSurfaceVariant to scheme.surface,
        scheme.onPrimary to scheme.primary,
    )
    if (textPairs.any { (foreground, background) -> contrastRatio(foreground, background) < MIN_TEXT_CONTRAST }) {
        return true
    }
    return contrastRatio(scheme.outline, scheme.surface) < MIN_NON_TEXT_CONTRAST
}

internal fun parseHighContrastValue(raw: String?): Boolean {
    val value = raw?.trim().orEmpty()
    return value == "1" || value.equals("true", ignoreCase = true)
}

internal fun readHighTextContrastSetting(contentResolver: ContentResolver): Boolean = runCatching {
    HIGH_TEXT_CONTRAST_SETTING_KEYS.any { key ->
        val secureValue = Settings.Secure.getString(contentResolver, key)
        val raw = secureValue ?: Settings.Global.getString(contentResolver, key)
        parseHighContrastValue(raw)
    }
}.getOrDefault(false)

internal fun isSystemHighContrastTextEnabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        val official = runCatching {
            (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)
                ?.isHighContrastTextEnabled() == true
        }.getOrDefault(false)
        if (official) return true
    }
    return runCatching { readHighTextContrastSetting(context.contentResolver) }.getOrDefault(false)
}

@Composable
private fun rememberSystemHighContrastText(context: Context): Boolean {
    val appContext = remember(context) { context.applicationContext ?: context }
    var enabled by remember(appContext) { mutableStateOf(isSystemHighContrastTextEnabled(appContext)) }
    DisposableEffect(appContext) {
        val contentResolver = appContext.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = isSystemHighContrastTextEnabled(appContext)
            }
        }
        runCatching {
            HIGH_TEXT_CONTRAST_SETTING_KEYS.forEach { key ->
                contentResolver.registerContentObserver(Settings.Secure.getUriFor(key), false, observer)
                contentResolver.registerContentObserver(Settings.Global.getUriFor(key), false, observer)
            }
        }
        onDispose { runCatching { contentResolver.unregisterContentObserver(observer) } }
    }
    return enabled
}

@Composable
fun AuroraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val highContrast = rememberSystemHighContrastText(context)
    val reduceMotion = rememberSystemReduceMotion(context)
    val fallbackScheme = auroraColorScheme(darkTheme, highContrast)
    // Material You dynamic colors on Android 12+ with WCAG AA contrast guard & high-contrast override.
    val colorScheme = if (!highContrast && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamicScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        if (schemeNeedsFallback(dynamicScheme)) fallbackScheme else dynamicScheme
    } else {
        fallbackScheme
    }
    val semanticColors = if (darkTheme) AuroraSemanticDark else AuroraSemanticLight
    CompositionLocalProvider(
        LocalReduceMotion provides reduceMotion,
        LocalAuroraSemanticColors provides semanticColors,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = AuroraTypography,
            shapes = AuroraExpressiveShapes,
            content = content,
        )
    }
}
