package com.aurorashelf.app.ui

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.aurorashelf.app.R
import kotlin.math.roundToLong
import kotlinx.coroutines.delay

/**
 * Brand intro tokens. A launch brand moment is intentionally an immersive black stage, so it
 * uses its own semantic tokens instead of the app surface palette (HIG: media/immersive exemption).
 * Contrast on black: wordmark 21:1, mark #9AA3AE 8.22:1 (both above 4.5:1 for text and 3:1 icons).
 */
internal object IntroTokens {
    val Background = Color(0xFF000000)
    val Mark = Color(0xFF9AA3AE)
    val MarkHighlight = Color(0xFFF2F5F8)
    val Wordmark = Color(0xFFFFFFFF)
}

/**
 * Pure timeline helper so the intro choreography can be unit-tested without Compose.
 *
 * HIG: the whole moment is capped well below 2 s, is skippable, and never blocks the app
 * beneath it (the app composes and loads while the overlay plays).
 */
internal object IntroTiming {
    /** Full choreography length in milliseconds. Kept <= 2000 ms (HIG: short, cancellable). */
    const val DURATION_MS = 1_900L

    /** Fade used when the user skips or after the timeline ends. */
    const val SKIP_FADE_MS = 120L

    /** With Reduce Motion the static composition just holds briefly before fading. */
    const val REDUCED_HOLD_MS = 700L

    /** Progress used for the static Reduce Motion frame: fully composed, before fade-out. */
    const val REDUCED_STATIC_PROGRESS = 0.85f

    /** Content starts fading out at this normalized progress. */
    const val FADE_OUT_START = 0.90f

    fun shouldAnimate(animatorsEnabled: Boolean): Boolean = animatorsEnabled

    fun markProgress(progress: Float): Float =
        ((progress - 0.02f) / 0.36f).coerceIn(0f, 1f)

    fun sweepProgress(progress: Float): Float =
        ((progress - 0.38f) / 0.18f).coerceIn(0f, 1f)

    fun wordmarkProgress(progress: Float): Float =
        ((progress - 0.52f) / 0.26f).coerceIn(0f, 1f)

    fun contentAlpha(progress: Float): Float =
        if (progress <= FADE_OUT_START) {
            1f
        } else {
            (1f - (progress - FADE_OUT_START) / (1f - FADE_OUT_START)).coerceIn(0f, 1f)
        }

    /** Milliseconds of the cinematic timeline; used by tests and skip logic documentation. */
    fun totalMs(): Long = DURATION_MS
}

/**
 * Original launch brand moment: three light strokes draw a stylized "N", a highlight sweeps the
 * diagonal, then the wordmark expands into place before the black stage fades away.
 *
 * Not a copy of any film-studio ident: no logo, no audio, no third-party assets. The overlay is
 * mounted above the already-composing app, so first paint is not delayed; tapping anywhere or
 * pressing Back dismisses it immediately. With system animations disabled (Reduce Motion) the
 * mark is shown statically with a plain fade.
 */
@Composable
internal fun BrandIntroOverlay(onFinished: () -> Unit) {
    val appName = stringResource(R.string.app_name)
    val reduceMotion = remember { !ValueAnimator.areAnimatorsEnabled() }
    val overlayAlpha = remember { Animatable(0f) }
    val timeline = remember { Animatable(0f) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        overlayAlpha.animateTo(1f, tween(150))
        if (IntroTiming.shouldAnimate(!reduceMotion)) {
            timeline.animateTo(1f, tween(IntroTiming.DURATION_MS.toInt(), easing = LinearEasing))
            delay(40)
        } else {
            // Reduce Motion: no movement, but still show the complete mark + wordmark
            // statically (0.85 = fully composed, before the fade-out starts).
            timeline.snapTo(IntroTiming.REDUCED_STATIC_PROGRESS)
            delay(IntroTiming.REDUCED_HOLD_MS)
        }
        finished = true
    }
    LaunchedEffect(finished) {
        if (finished) {
            overlayAlpha.animateTo(0f, tween(IntroTiming.SKIP_FADE_MS.toInt()))
            onFinished()
        }
    }

    BackHandler { finished = true }

    val progress = timeline.value
    val markProgress = IntroTiming.markProgress(progress)
    val sweepProgress = IntroTiming.sweepProgress(progress)
    val wordmarkProgress = IntroTiming.wordmarkProgress(progress)
    val contentAlpha = IntroTiming.contentAlpha(progress)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(IntroTokens.Background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = "跳过开场动画",
            ) { finished = true }
            .semantics { contentDescription = "$appName 品牌开场，点按跳过" },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.graphicsLayer { alpha = contentAlpha },
        ) {
            BrandMark(
                markProgress = markProgress,
                sweepProgress = sweepProgress,
                modifier = Modifier.size(176.dp),
            )
            Text(
                text = appName,
                color = IntroTokens.Wordmark,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (0.04f + 0.16f * wordmarkProgress).em,
                ),
                modifier = Modifier.graphicsLayer {
                    alpha = wordmarkProgress
                    translationY = (1f - wordmarkProgress) * 14.dp.toPx()
                },
            )
        }
        Text(
            text = "点按跳过",
            color = IntroTokens.Mark.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = contentAlpha * (if (reduceMotion) 1f else markProgress.coerceIn(0f, 1f)) }
                .size(width = 120.dp, height = 48.dp),
        )
    }
}

/**
 * Draws the original three-stroke "N" mark with a trimmed-path draw-in and a highlight sweep.
 * Purely decorative: the overlay carries the accessibility label.
 */
@Composable
private fun BrandMark(
    markProgress: Float,
    sweepProgress: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val side = size.minDimension
        val stroke = side * 0.075f
        val inset = side * 0.16f
        val left = inset
        val right = side - inset
        val top = inset
        val bottom = side - inset

        val leftBar = Path().apply {
            moveTo(left, bottom)
            lineTo(left, top)
        }
        val diagonal = Path().apply {
            moveTo(left, top)
            lineTo(right, bottom)
        }
        val rightBar = Path().apply {
            moveTo(right, bottom)
            lineTo(right, top)
        }

        val measure = PathMeasure()
        fun drawTrimmed(path: Path, localProgress: Float, color: Color, width: Float) {
            if (localProgress <= 0f) return
            measure.setPath(path, false)
            val length = measure.length
            if (length <= 0f) return
            val segment = Path()
            measure.getSegment(0f, length * localProgress.coerceIn(0f, 1f), segment, true)
            drawPath(
                path = segment,
                color = color,
                style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        // Staggered draw-in: left bar, diagonal, right bar.
        val p1 = IntroTiming.markProgress(markProgress)
        val p2 = ((markProgress - 0.18f) / 0.82f).coerceIn(0f, 1f)
        val p3 = ((markProgress - 0.40f) / 0.60f).coerceIn(0f, 1f)

        // Two very soft glow layers instead of a hard outline; decorative only.
        drawTrimmed(leftBar, p1, IntroTokens.Mark.copy(alpha = 0.05f), stroke * 3.4f)
        drawTrimmed(diagonal, p2, IntroTokens.Mark.copy(alpha = 0.05f), stroke * 3.4f)
        drawTrimmed(rightBar, p3, IntroTokens.Mark.copy(alpha = 0.05f), stroke * 3.4f)
        drawTrimmed(leftBar, p1, IntroTokens.Mark.copy(alpha = 0.10f), stroke * 2.1f)
        drawTrimmed(diagonal, p2, IntroTokens.Mark.copy(alpha = 0.10f), stroke * 2.1f)
        drawTrimmed(rightBar, p3, IntroTokens.Mark.copy(alpha = 0.10f), stroke * 2.1f)

        // The sweep briefly brightens the strokes, then the mark settles back to brand cold grey.
        val sweepPulse = (sweepProgress * (1f - sweepProgress) * 4f).coerceIn(0f, 1f)
        val tint = androidx.compose.ui.graphics.lerp(IntroTokens.Mark, IntroTokens.MarkHighlight, sweepPulse * 0.9f)
        drawTrimmed(leftBar, p1, tint, stroke)
        drawTrimmed(diagonal, p2, tint, stroke)
        drawTrimmed(rightBar, p3, tint, stroke)

        if (sweepProgress > 0f && sweepProgress < 1f) {
            measure.setPath(diagonal, false)
            val length = measure.length
            val head = length * sweepProgress.coerceIn(0f, 1f)
            val tail = (head - length * 0.22f).coerceAtLeast(0f)
            val sweepPath = Path()
            measure.getSegment(tail, head, sweepPath, true)
            drawPath(
                path = sweepPath,
                color = IntroTokens.MarkHighlight.copy(alpha = 0.9f),
                style = Stroke(width = stroke * 0.55f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawCircle(
                color = IntroTokens.MarkHighlight.copy(alpha = 0.55f * (1f - sweepProgress)),
                radius = stroke * 0.9f,
                center = Offset(
                    left + (right - left) * sweepProgress,
                    top + (bottom - top) * sweepProgress,
                ),
            )
        }
    }
}

/** Converts the timeline progress to whole milliseconds (used only for diagnostics/tests). */
internal fun IntroTimingElapsedMs(progress: Float): Long =
    (progress.coerceIn(0f, 1f) * IntroTiming.totalMs()).roundToLong()
