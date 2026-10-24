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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.pow

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

/**
 * Increase Contrast (HIG "Increase Contrast" / WCAG 1.4.6) light fallback palette.
 *
 * Measured WCAG 2.1 contrast ratios for the critical pairs (normal palette in brackets):
 * - onBackground/background: **21.00:1** (16.26:1)
 * - onSurface/surface: **21.00:1** (17.10:1)
 * - onSurfaceVariant/surface: **9.29:1** (6.74:1)
 * - onPrimary/primary: **9.78:1** (6.36:1)
 * - error/surface: **8.92:1** (6.46:1)
 * - outline/surface (non-text boundary): **7.00:1** (4.37:1)
 *
 * Every text pair reaches the 7:1 custom foreground/background target and the outline
 * reaches 4.5:1; each pair is strictly stronger than the normal light palette.
 */
internal val AuroraHighContrastLightColors = lightColorScheme(
    primary = Color(0xFF8B0034),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3F0017),
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF474747),
    outline = Color(0xFF595959),
    outlineVariant = Color(0xFF8A8A8A),
    error = Color(0xFF990000),
    onError = Color.White,
)

/**
 * Increase Contrast dark fallback palette.
 *
 * Measured WCAG 2.1 contrast ratios for the critical pairs (normal palette in brackets):
 * - onBackground/background: **21.00:1** (18.37:1)
 * - onSurface/surface: **19.80:1** (16.74:1)
 * - onSurfaceVariant/surface: **12.84:1** (9.67:1)
 * - onPrimary/primary: **12.52:1** (5.45:1)
 * - error/surface: **11.66:1** (10.58:1)
 * - outline/surface (non-text boundary): **9.13:1** (3.17:1)
 *
 * Dark chrome keeps the base/elevated distinction: the background is pure black while the
 * surface stays slightly raised at #0A0A0A. Every pair is strictly stronger than the normal
 * dark palette, including the brand coral button pair (black ink on a lighter coral).
 */
internal val AuroraHighContrastDarkColors = darkColorScheme(
    primary = Color(0xFFFFB3C6),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFFFFB0C2),
    onPrimaryContainer = Color(0xFF3F0015),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0A0A0A),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF141414),
    onSurfaceVariant = Color(0xFFD0D0D0),
    outline = Color(0xFFB0B0B0),
    outlineVariant = Color(0xFF5A5A5A),
    error = Color(0xFFFFB4AB),
    onError = Color.Black,
)

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

/** WCAG 2.1 AA text contrast threshold for the dynamic-color guard. */
private const val MIN_TEXT_CONTRAST = 4.5

/** WCAG 2.1 non-text contrast threshold for icons and boundaries. */
private const val MIN_NON_TEXT_CONTRAST = 3.0

/** AOSP high-contrast-text flag; no public `Settings.Secure`/`Settings.Global` constant exists. */
private const val HIGH_TEXT_CONTRAST_SETTING = "high_text_contrast_enabled"

/**
 * Coerces one sRGB channel to linear light, per WCAG 2.1 relative luminance.
 * Values are assumed to be gamma-encoded sRGB components in `0f..1f`.
 */
private fun linearizeChannel(channel: Float): Double {
    val value = channel.toDouble().coerceIn(0.0, 1.0)
    return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
}

/** WCAG 2.1 relative luminance for an opaque sRGB [Color]. */
private fun Color.relativeLuminance(): Double =
    0.2126 * linearizeChannel(red) + 0.7152 * linearizeChannel(green) + 0.0722 * linearizeChannel(blue)

/**
 * WCAG 2.1 contrast ratio `(L1 + 0.05) / (L2 + 0.05)`, symmetric in its arguments.
 *
 * Returns `1.0` for identical colors and `21.0` for black on white. Intended for the
 * opaque sRGB design tokens used by this theme; translucent colors are evaluated as if
 * they were opaque (all current scheme tokens are fully opaque).
 */
internal fun contrastRatio(foreground: Color, background: Color): Double {
    val foregroundLuminance = foreground.relativeLuminance()
    val backgroundLuminance = background.relativeLuminance()
    val lighter = maxOf(foregroundLuminance, backgroundLuminance)
    val darker = minOf(foregroundLuminance, backgroundLuminance)
    return (lighter + 0.05) / (darker + 0.05)
}

/**
 * True when a Material You dynamic scheme should be replaced by an Aurora fallback.
 *
 * Text pairs must reach WCAG AA 4.5:1: onBackground/background, onSurface/surface,
 * onSurfaceVariant/surface and onPrimary/primary. The outline/surface icon and border
 * pair must reach the 3:1 non-text threshold. A wallpaper-derived scheme that fails any
 * of these is discarded in favor of [auroraColorScheme], which is contrast-audited.
 */
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

/**
 * Reads the AOSP high-contrast-text flag from [Settings.Secure], falling back to
 * [Settings.Global] when the secure provider has no value for the key.
 *
 * AOSP stores the feature as the string `"1"`; some builds store `"true"`. Any read
 * failure (missing provider, `SecurityException` on locked-down OEM images) is treated
 * as `false` so the app simply keeps the standard palette.
 */
internal fun readHighTextContrastSetting(contentResolver: ContentResolver): Boolean = runCatching {
    val value = Settings.Secure.getString(contentResolver, HIGH_TEXT_CONTRAST_SETTING)
        ?: Settings.Global.getString(contentResolver, HIGH_TEXT_CONTRAST_SETTING)
    value == "1" || value.equals("true", ignoreCase = true)
}.getOrDefault(false)

/**
 * Best-effort "Increase Contrast" detection.
 *
 * Android 16 (API 36, [Build.VERSION_CODES.BAKLAVA]) exposes the authoritative public
 * query `AccessibilityManager.isHighContrastTextEnabled()`, so it is used when available.
 * Every release additionally probes the AOSP setting key because:
 * - devices below API 36 have no public query API;
 * - some OEM skins expose their own "high contrast fonts/text" switch that does not
 *   reach this API, and a stale/false negative must not disable the legibility upgrade.
 *
 * Known boundary: OEM toggles that never write `high_text_contrast_enabled` remain
 * invisible here, and the app keeps the normal palette on those skins.
 */
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

/**
 * Tracks the system high-contrast flag for the lifetime of the composition.
 *
 * The initial value is read synchronously (a provider query is cheap and avoids a
 * palette flash). A [ContentObserver] on both settings URIs refreshes the state when the
 * user toggles the system setting while the app is running; failures to register or
 * unregister are ignored because this is a non-essential enhancement.
 */
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
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(HIGH_TEXT_CONTRAST_SETTING),
                false,
                observer,
            )
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor(HIGH_TEXT_CONTRAST_SETTING),
                false,
                observer,
            )
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
    val fallbackScheme = auroraColorScheme(darkTheme, highContrast)
    // Increase Contrast cannot be expressed by wallpaper-derived dynamic colors, and a
    // dynamic scheme that fails the WCAG guard is replaced by the audited fallback.
    val colorScheme = if (!highContrast && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamicScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        if (schemeNeedsFallback(dynamicScheme)) fallbackScheme else dynamicScheme
    } else {
        fallbackScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AuroraTypography,
        shapes = AuroraShapes,
        content = content,
    )
}
