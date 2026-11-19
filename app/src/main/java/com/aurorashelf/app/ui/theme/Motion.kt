@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.aurorashelf.app.ui.theme

import android.animation.ValueAnimator
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Live accessibility preference reflecting Android's "Remove animations" setting
 * (`Settings.Global.ANIMATOR_DURATION_SCALE == 0f` or `!ValueAnimator.areAnimatorsEnabled()`).
 */
internal val LocalReduceMotion = compositionLocalOf { false }

internal fun readSystemReduceMotion(context: Context): Boolean {
    val scale = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }.getOrDefault(1f)
    return scale == 0f || !ValueAnimator.areAnimatorsEnabled()
}

@Composable
internal fun rememberSystemReduceMotion(context: Context): Boolean {
    val appContext = remember(context) { context.applicationContext ?: context }
    var reduceMotion by remember(appContext) { mutableStateOf(readSystemReduceMotion(appContext)) }
    DisposableEffect(appContext) {
        val resolver = appContext.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduceMotion = readSystemReduceMotion(appContext)
            }
        }
        runCatching {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer,
            )
        }
        onDispose { runCatching { resolver.unregisterContentObserver(observer) } }
    }
    return reduceMotion
}

/**
 * 100% Native Material 3 Expressive motion tokens driven by [MaterialTheme.motionScheme].
 *
 * Strictly separates:
 * - **Spatial specs** (position, size, bounds, scale, shape morphing) — spring physics with expressive bounce.
 * - **Effects specs** (color, alpha, scrim, elevation tint) — critically damped, zero overshoot.
 *
 * Every spec degrades deterministically to [snap] when system Reduce Motion is active.
 */
internal object AuroraMotion {
    @Composable
    fun <T> fastSpatial(): FiniteAnimationSpec<T> =
        if (LocalReduceMotion.current) snap() else MaterialTheme.motionScheme.fastSpatialSpec()

    @Composable
    fun <T> defaultSpatial(): FiniteAnimationSpec<T> =
        if (LocalReduceMotion.current) snap() else MaterialTheme.motionScheme.defaultSpatialSpec()

    @Composable
    fun <T> slowSpatial(): FiniteAnimationSpec<T> =
        if (LocalReduceMotion.current) snap() else MaterialTheme.motionScheme.slowSpatialSpec()

    @Composable
    fun <T> fastEffects(): FiniteAnimationSpec<T> =
        if (LocalReduceMotion.current) snap() else MaterialTheme.motionScheme.fastEffectsSpec()

    @Composable
    fun <T> defaultEffects(): FiniteAnimationSpec<T> =
        if (LocalReduceMotion.current) snap() else MaterialTheme.motionScheme.defaultEffectsSpec()

    @Composable
    fun <T> slowEffects(): FiniteAnimationSpec<T> =
        if (LocalReduceMotion.current) snap() else MaterialTheme.motionScheme.slowEffectsSpec()

    /** Material 3 Expressive Fade-Through transition for top-level navigation destinations. */
    @Composable
    fun expressiveFadeThrough(): ContentTransform {
        val reduceMotion = LocalReduceMotion.current
        if (reduceMotion) {
            return fadeIn(snap()) togetherWith fadeOut(snap())
        }
        val enterEffects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val enterSpatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val exitEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        return (
            fadeIn(animationSpec = enterEffects) +
                scaleIn(initialScale = 0.94f, animationSpec = enterSpatial)
            ) togetherWith (
            fadeOut(animationSpec = exitEffects) +
                scaleOut(targetScale = 0.98f, animationSpec = exitEffects)
            )
    }

    /** Expressive container reveal for modal / detail overlays (player, search, reader). */
    @Composable
    fun expressiveOverlayEnter(): EnterTransition {
        if (LocalReduceMotion.current) return fadeIn(snap())
        return fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
            scaleIn(
                initialScale = 0.94f,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            )
    }

    @Composable
    fun expressiveOverlayExit(): ExitTransition {
        if (LocalReduceMotion.current) return fadeOut(snap())
        return fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
            scaleOut(
                targetScale = 0.96f,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            )
    }

    @Composable
    fun expressiveBarEnter(): EnterTransition {
        if (LocalReduceMotion.current) return fadeIn(snap())
        return fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
            slideInVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 3 }
    }

    @Composable
    fun expressiveBarExit(): ExitTransition {
        if (LocalReduceMotion.current) return fadeOut(snap())
        return fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
            slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 3 }
    }
}

/**
 * Semantic Android 16/17 Compose haptics replacing legacy [HapticFeedbackType.TextHandleMove] misuse.
 */
internal object AuroraHaptics {
    fun tap(haptic: HapticFeedback) {
        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
    }

    fun confirm(haptic: HapticFeedback) {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    fun reject(haptic: HapticFeedback) {
        haptic.performHapticFeedback(HapticFeedbackType.Reject)
    }

    fun toggle(haptic: HapticFeedback, enabled: Boolean) {
        haptic.performHapticFeedback(
            if (enabled) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
        )
    }

    fun segmentTick(haptic: HapticFeedback) {
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    fun scrubTick(haptic: HapticFeedback) {
        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    fun longPress(haptic: HapticFeedback) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}
