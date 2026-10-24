package com.aurorashelf.app.ui

import android.animation.ValueAnimator
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.aurorashelf.app.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Cinematic launch-intro tokens. A brand moment is an immersive black stage, so it uses its own
 * semantic tokens rather than app surfaces (HIG: media/immersive exemption).
 * Contrast on black: wordmark 21:1, mark grey 8.22:1, icy beam 9.4:1.
 */
internal object IntroTokens {
    val Background = Color(0xFF000000)
    val DeepSpace = Color(0xFF070A0E)
    val Mark = Color(0xFFA9B4C2)
    val MarkBright = Color(0xFFF7FAFF)
    val Beam = Color(0xFF8FE6FF)
    val AuroraCyan = Color(0xFF7FE7FF)
    val AuroraPeriwinkle = Color(0xFF93A9FF)
    val AuroraViolet = Color(0xFFC08CFF)
    val AuroraPink = Color(0xFFFF9AD5)
    val Wordmark = Color(0xFFFFFFFF)
}

/**
 * Pure timeline helper so the choreography can be unit-tested without Compose.
 *
 * HIG: the moment stays skippable and respects Reduce Motion; the visual timeline is 4.2 s
 * (cinematic trailer length requested by the product owner) and is always dismissible.
 */
internal object IntroTiming {
    const val DURATION_MS = 4_200L
    const val SKIP_FADE_MS = 180L
    const val REDUCED_HOLD_MS = 1_200L
    const val REDUCED_STATIC_PROGRESS = 0.90f
    const val FADE_OUT_START = 0.94f

    fun shouldAnimate(animatorsEnabled: Boolean): Boolean = animatorsEnabled

    /** Ribbons travel and weave during the first ~46% of the timeline. */
    fun ribbonProgress(progress: Float): Float = ((progress - 0.02f) / 0.44f).coerceIn(0f, 1f)

    /** Shockwave, bloom flash and chromatic burst. */
    fun burstProgress(progress: Float): Float = ((progress - 0.44f) / 0.14f).coerceIn(0f, 1f)

    /** Letters reveal and expand their tracking. */
    fun wordmarkProgress(progress: Float): Float = ((progress - 0.50f) / 0.28f).coerceIn(0f, 1f)

    /** Final lockup settle before the fade. */
    fun lockupProgress(progress: Float): Float = ((progress - 0.72f) / 0.10f).coerceIn(0f, 1f)

    fun contentAlpha(progress: Float): Float =
        if (progress <= FADE_OUT_START) {
            1f
        } else {
            (1f - (progress - FADE_OUT_START) / (1f - FADE_OUT_START)).coerceIn(0f, 1f)
        }

    fun totalMs(): Long = DURATION_MS
}

private data class Spark(
    val angle: Float,
    val speed: Float,
    val sizeFactor: Float,
    val delay: Float,
)

/**
 * Original cinematic launch sequence: deep-space stage, four luminous ribbons weaving an
 * interlocking emblem, impact flash with chromatic burst and sparks, then the wordmark assembling
 * letter by letter with a final specular sweep.
 *
 * This is an original piece. It deliberately does not recreate any film-studio ident (mark,
 * choreography or audio), while targeting the same production tier: multi-layer bloom, depth,
 * particles, camera push and an original synthesized score.
 */
@Composable
internal fun BrandIntroOverlay(
    soundEnabled: Boolean,
    onFinished: () -> Unit,
    onLockupReached: () -> Unit = {},
) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val reduceMotion = remember { !ValueAnimator.areAnimatorsEnabled() }
    val overlayAlpha = remember { Animatable(0f) }
    val timeline = remember { Animatable(0f) }
    var finished by remember { mutableStateOf(false) }
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }

    // Original synthesized sting. Started by the visual timeline (not during composition) so the
    // first beat lands with the first visible frame; skipped for Reduce Motion / silent devices.
    var soundPlayer: MediaPlayer? by remember { mutableStateOf(null) }
    DisposableEffect(Unit) {
        onDispose { runCatching { soundPlayer?.release() } }
    }

    LaunchedEffect(Unit) {
        Log.i("BrandIntro", "start reduceMotion=$reduceMotion sound=$soundEnabled")
        withFrameNanos { }
        val ringerSilent = audioManager?.ringerMode == AudioManager.RINGER_MODE_SILENT
        val mediaAudible = (audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0) > 0
        if (soundEnabled && !reduceMotion && !ringerSilent && mediaAudible) {
            soundPlayer = runCatching {
                MediaPlayer.create(context, R.raw.intro_sting)?.apply {
                    setVolume(0.92f, 0.92f)
                    start()
                    Log.i("BrandIntro", "sound started duration=${duration}ms")
                }
            }.getOrNull()
        }
        overlayAlpha.animateTo(1f, tween(180))
        if (IntroTiming.shouldAnimate(!reduceMotion)) {
            timeline.animateTo(1f, tween(IntroTiming.DURATION_MS.toInt(), easing = LinearEasing))
        } else {
            timeline.snapTo(IntroTiming.REDUCED_STATIC_PROGRESS)
            delay(IntroTiming.REDUCED_HOLD_MS)
        }
        finished = true
    }
    LaunchedEffect(finished) {
        if (finished) {
            Log.i("BrandIntro", "finish")
            overlayAlpha.animateTo(0f, tween(IntroTiming.SKIP_FADE_MS.toInt()))
            onFinished()
        }
    }

    BackHandler { finished = true }

    var lockupReported by remember { mutableStateOf(false) }
    LaunchedEffect(timeline.value) {
        if (!lockupReported && IntroTiming.lockupProgress(timeline.value) >= 1f) {
            lockupReported = true
            onLockupReached()
        }
    }

    val p = timeline.value
    val ribbons = IntroTiming.ribbonProgress(p)
    val burst = IntroTiming.burstProgress(p)
    val wordmark = IntroTiming.wordmarkProgress(p)
    val lockup = IntroTiming.lockupProgress(p)
    val contentAlpha = IntroTiming.contentAlpha(p)
    val cameraScale = 0.94f + 0.09f * easeOutCubic((p / 0.78f).coerceIn(0f, 1f))
    val cameraShift = (1f - easeOutCubic((p / 0.6f).coerceIn(0f, 1f))) * 18f

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
        // Stage: drifting nebula + vignette.
        IntroStage(progress = p, contentAlpha = contentAlpha)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(26.dp),
            modifier = Modifier.graphicsLayer {
                alpha = contentAlpha
                scaleX = cameraScale
                scaleY = cameraScale
                translationY = cameraShift
            },
        ) {
            val emblemTilt = 1f - easeOutCubic((p / 0.7f).coerceIn(0f, 1f))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.graphicsLayer {
                    rotationY = -9f * emblemTilt
                    rotationX = 5f * emblemTilt
                    cameraDistance = 16f * density
                },
            ) {
                // Soft bloom pass (GPU blur where available; harmless fallback below API 31).
                Canvas(modifier = Modifier.size(300.dp).blur(18.dp)) {
                    EmblemLayer(
                        progress = ribbons,
                        burst = burst,
                        sweep = (p / 0.7f).coerceIn(0f, 1f),
                        glowPass = true,
                        reduceMotion = reduceMotion,
                    )
                }
                Canvas(modifier = Modifier.size(300.dp)) {
                    EmblemLayer(
                        progress = ribbons,
                        burst = burst,
                        sweep = (p / 0.7f).coerceIn(0f, 1f),
                        glowPass = false,
                        reduceMotion = reduceMotion,
                    )
                    BurstParticles(progress = burst, emblemSize = size.minDimension)
                    Shockwave(progress = burst, emblemSize = size.minDimension)
                    SpecularSweep(progress = p, emblemSize = size.minDimension)
                }
            }
            WordmarkRow(
                appName = appName,
                reveal = wordmark,
                lockup = lockup,
            )
            Box(
                modifier = Modifier
                    .size(width = 210.dp, height = 1.dp)
                    .graphicsLayer { alpha = easeOutCubic(lockup) * 0.55f }
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, IntroTokens.Beam, Color.Transparent),
                        ),
                    ),
            )
        }

        Text(
            text = "点按跳过",
            color = IntroTokens.Mark.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    alpha = contentAlpha * (if (reduceMotion) 1f else easeOutCubic(ribbons))
                }
                .size(width = 140.dp, height = 48.dp),
        )
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Stage and emblem                                                                               */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun IntroStage(progress: Float, contentAlpha: Float) {
    val drift = (progress * 1.4f).coerceIn(0f, 1f)
    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha }) {
        drawRect(IntroTokens.DeepSpace)
        val w = size.width
        val h = size.height
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(IntroTokens.AuroraCyan.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(w * (0.22f + 0.05f * drift), h * 0.28f),
                radius = w * 0.85f,
            ),
            radius = w * 0.85f,
            center = Offset(w * (0.22f + 0.05f * drift), h * 0.28f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(IntroTokens.AuroraViolet.copy(alpha = 0.08f), Color.Transparent),
                center = Offset(w * (0.82f - 0.04f * drift), h * 0.74f),
                radius = w * 0.7f,
            ),
            radius = w * 0.7f,
            center = Offset(w * (0.82f - 0.04f * drift), h * 0.74f),
        )
        // Vignette keeps attention on the lockup.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                center = Offset(w / 2f, h / 2f),
                radius = w * 0.75f,
            ),
        )
    }
}

/**
 * Emblem = four interlocking luminous ribbons (two counter-rotating arcs + two S-beams).
 * Reveal is a trimmed-path draw-in with a trailing head; on impact the strokes brighten and a
 * core of light ignites. Original geometry, intentionally not a studio "N".
 */
private fun DrawScope.EmblemLayer(
    progress: Float,
    burst: Float,
    sweep: Float,
    glowPass: Boolean,
    reduceMotion: Boolean,
) {
    val side = size.minDimension
    val cx = size.width / 2f
    val cy = size.height / 2f
    val topY = cy - side * 0.295f
    val bottomY = cy + side * 0.295f
    val leftX = cx - side * 0.245f
    val rightX = cx + side * 0.245f
    val halfW = side * 0.058f
    val flash = if (burst in 0.01f..0.99f) sin(burst * PI.toFloat()) else 0f
    val shift = sweep * side * 0.05f

    fun quad(a: Offset, b: Offset, c: Offset, d: Offset): Path = Path().apply {
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
    }

    // Three flat ribbons: left stem, diagonal, right stem. No round strokes: everything is a
    // filled plane so the mark reads as folded paper/foil instead of neon tubes.
    val leftStem = quad(
        Offset(leftX - halfW, bottomY), Offset(leftX + halfW, bottomY),
        Offset(leftX + halfW, topY), Offset(leftX - halfW, topY),
    )
    val rightStem = quad(
        Offset(rightX - halfW, bottomY), Offset(rightX + halfW, bottomY),
        Offset(rightX + halfW, topY), Offset(rightX - halfW, topY),
    )
    // The diagonal's top/bottom faces are flush with the stems' end faces, so the three flat
    // ribbons join cleanly (no protruding corners at the weave).
    val diagonal = quad(
        Offset(leftX - halfW, topY),
        Offset(leftX + halfW, topY),
        Offset(rightX + halfW, bottomY),
        Offset(rightX - halfW, bottomY),
    )

    val aurora = Brush.linearGradient(
        colors = listOf(
            IntroTokens.AuroraCyan,
            IntroTokens.AuroraPeriwinkle,
            IntroTokens.AuroraViolet,
            IntroTokens.AuroraPink,
        ),
        start = Offset(leftX - side * 0.18f + shift, bottomY + side * 0.10f),
        end = Offset(rightX + side * 0.18f + shift, topY - side * 0.10f),
    )
    val auroraSoft = Brush.linearGradient(
        colors = listOf(
            IntroTokens.AuroraCyan.copy(alpha = 0.55f),
            IntroTokens.AuroraViolet.copy(alpha = 0.55f),
            IntroTokens.AuroraPink.copy(alpha = 0.55f),
        ),
        start = Offset(leftX - side * 0.2f, bottomY),
        end = Offset(rightX + side * 0.2f, topY),
    )

    fun line(from: Offset, to: Offset, color: Color, widthPx: Float) {
        drawLine(color = color, start = from, end = to, strokeWidth = widthPx, cap = StrokeCap.Butt)
    }

    // Ambient aurora halo behind the mark, then a soft drop shadow so the N sits above the stage.
    if (!glowPass) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    IntroTokens.AuroraViolet.copy(alpha = 0.16f),
                    IntroTokens.AuroraCyan.copy(alpha = 0.10f),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = side * 0.52f,
            ),
            radius = side * 0.52f,
            center = Offset(cx, cy),
        )
    }

    fun drawSegment(
        shape: Path,
        clip: Path,
        alpha: Float,
        edgeTop: Pair<Offset, Offset>,
        edgeBottom: Pair<Offset, Offset>,
    ) {
        clipPath(clip) {
            if (glowPass) {
                drawPath(shape, brush = auroraSoft, alpha = 0.42f + 0.18f * flash)
                return@clipPath
            }
            drawPath(shape, brush = aurora, alpha = alpha)
            // Foil sheen: brighter toward the top, falling off to the base.
            drawPath(
                shape,
                brush = Brush.verticalGradient(
                    listOf(
                        IntroTokens.MarkBright.copy(alpha = 0.16f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.12f),
                    ),
                ),
            )
            // Fold lighting: one lit face, one shaded face, plus a hairline crease.
            line(edgeTop.first, edgeTop.second, IntroTokens.MarkBright.copy(alpha = 0.48f), side * 0.006f)
            line(edgeBottom.first, edgeBottom.second, Color.Black.copy(alpha = 0.42f), side * 0.008f)
            val midTop = Offset((edgeTop.first.x + edgeBottom.first.x) / 2f, (edgeTop.first.y + edgeBottom.first.y) / 2f)
            val midBottom = Offset((edgeTop.second.x + edgeBottom.second.x) / 2f, (edgeTop.second.y + edgeBottom.second.y) / 2f)
            line(midTop, midBottom, IntroTokens.MarkBright.copy(alpha = 0.10f), side * 0.0014f)
            if (flash > 0.02f) {
                line(edgeTop.first + Offset(side * 0.006f, 0f), edgeTop.second + Offset(side * 0.006f, 0f),
                    IntroTokens.AuroraCyan.copy(alpha = 0.18f * flash), side * 0.0026f)
                line(edgeBottom.first - Offset(side * 0.006f, 0f), edgeBottom.second - Offset(side * 0.006f, 0f),
                    IntroTokens.AuroraPink.copy(alpha = 0.18f * flash), side * 0.0026f)
            }
        }
    }

    val leftLocal = ((progress - 0.00f) / 0.34f).coerceIn(0f, 1f)
    val diagLocal = ((progress - 0.14f) / 0.40f).coerceIn(0f, 1f)
    val rightLocal = ((progress - 0.28f) / 0.36f).coerceIn(0f, 1f)

    // Axis-aligned unfolding: stems grow vertically, diagonal wipes left→right.
    val leftClip = Path().apply {
        addRect(Rect(leftX - halfW, bottomY - (bottomY - topY) * leftLocal, leftX + halfW, bottomY + 1f))
    }
    val diagClip = Path().apply {
        addRect(Rect(leftX - halfW - 1f, topY - 1f, leftX - halfW + (rightX - leftX + halfW * 2f) * diagLocal, bottomY + 1f))
    }
    val rightClip = Path().apply {
        addRect(Rect(rightX - halfW, topY - (bottomY - topY) * (1f - rightLocal) - 1f, rightX + halfW, bottomY + 1f))
    }

    // Drop shadow silhouette (offset down), gives physical depth under the foil.
    val shadowSilhouette = Path().apply {
        addPath(leftStem); addPath(diagonal); addPath(rightStem)
    }
    withTransform({ translate(0f, side * 0.022f) }) {
        drawPath(shadowSilhouette, color = Color.Black.copy(alpha = 0.38f))
    }

    drawSegment(
        leftStem, leftClip, 1f,
        edgeTop = Offset(leftX - halfW, topY) to Offset(leftX + halfW, topY),
        edgeBottom = Offset(leftX - halfW, bottomY) to Offset(leftX + halfW, bottomY),
    )
    drawSegment(
        diagonal, diagClip, 1f,
        edgeTop = Offset(leftX - halfW, topY) to Offset(leftX + halfW, topY),
        edgeBottom = Offset(rightX - halfW, bottomY) to Offset(rightX + halfW, bottomY),
    )
    if (!glowPass) {
        // Cast shadow of the right stem falling on the diagonal, then the right stem itself.
        clipPath(rightClip) {
            val shadow = Path().apply {
                addPath(rightStem)
                translate(Offset(-side * 0.02f, 0f))
            }
            drawPath(shadow, color = Color.Black.copy(alpha = 0.28f))
        }
    }
    drawSegment(
        rightStem, rightClip, 1f,
        edgeTop = Offset(rightX - halfW, topY) to Offset(rightX + halfW, topY),
        edgeBottom = Offset(rightX - halfW, bottomY) to Offset(rightX + halfW, bottomY),
    )

    // Specular pass: a soft white band travels across the folded foil.
    if (!glowPass && sweep in 0.55f..1f) {
        val t = ((sweep - 0.55f) / 0.45f).coerceIn(0f, 1f)
        val bandX = size.width * (-0.25f + 1.5f * t)
        val band = Path().apply {
            moveTo(bandX, topY - side * 0.2f)
            lineTo(bandX + side * 0.16f, topY - side * 0.2f)
            lineTo(bandX + side * 0.02f, bottomY + side * 0.2f)
            lineTo(bandX - side * 0.14f, bottomY + side * 0.2f)
            close()
        }
        clipPath(leftStem) { drawPath(band, brush = Brush.linearGradient(
            listOf(Color.Transparent, IntroTokens.MarkBright.copy(alpha = 0.30f), Color.Transparent),
            start = Offset(bandX, 0f), end = Offset(bandX + side * 0.16f, 0f),
        )) }
        clipPath(diagonal) { drawPath(band, brush = Brush.linearGradient(
            listOf(Color.Transparent, IntroTokens.MarkBright.copy(alpha = 0.30f), Color.Transparent),
            start = Offset(bandX, 0f), end = Offset(bandX + side * 0.16f, 0f),
        )) }
        clipPath(rightStem) { drawPath(band, brush = Brush.linearGradient(
            listOf(Color.Transparent, IntroTokens.MarkBright.copy(alpha = 0.30f), Color.Transparent),
            start = Offset(bandX, 0f), end = Offset(bandX + side * 0.16f, 0f),
        )) }
    }

    // Light core at the weave during the impact.
    val coreRadius = side * (0.012f + 0.05f * sin(burst * PI.toFloat())) * if (glowPass) 1.3f else 1f
    if (coreRadius > 0.1f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    IntroTokens.MarkBright.copy(alpha = if (glowPass) 0.30f else 0.55f),
                    IntroTokens.AuroraCyan.copy(alpha = if (glowPass) 0.22f else 0.14f),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = coreRadius * 2.2f,
            ),
            radius = coreRadius * 2.2f,
            center = Offset(cx, cy),
        )
    }
}

private fun DrawScope.BurstParticles(progress: Float, emblemSize: Float) {
    if (progress <= 0f) return
    val rnd = Random(42)
    repeat(56) {
        val angle = rnd.nextFloat() * 2f * PI.toFloat()
        val speed = 0.20f + rnd.nextFloat() * 0.55f
        val sizePx = emblemSize * (0.0035f + rnd.nextFloat() * 0.006f)
        val delay = rnd.nextFloat() * 0.45f
        val life = 0.35f + rnd.nextFloat() * 0.5f
        val t = ((progress - delay) / life).coerceIn(0f, 1f)
        if (t <= 0f) return@repeat
        val dist = emblemSize * speed * easeOutCubic(t) * 1.15f
        val center = Offset(size.width / 2f, size.height / 2f)
        val pos = Offset(center.x + cos(angle) * dist, center.y + sin(angle) * dist)
        val alpha = (1f - t) * 0.9f
        val warm = it % 3 == 0
        drawCircle(
            color = (if (warm) IntroTokens.MarkBright else IntroTokens.Beam).copy(alpha = alpha),
            radius = sizePx * (1f - 0.45f * t),
            center = pos,
        )
    }
}

private fun DrawScope.Shockwave(progress: Float, emblemSize: Float) {
    if (progress <= 0f || progress >= 1f) return
    val center = Offset(size.width / 2f, size.height / 2f)
    val base = emblemSize * 0.26f
    for (i in 0..1) {
        val t = (progress - i * 0.16f).coerceIn(0f, 1f)
        if (t <= 0f) continue
        val radius = base + emblemSize * 0.55f * easeOutCubic(t)
        drawCircle(
            color = IntroTokens.MarkBright.copy(alpha = (1f - t) * 0.42f),
            radius = radius,
            center = center,
            style = Stroke(width = emblemSize * 0.012f * (1f - t) + 1f),
        )
    }
}

private fun DrawScope.SpecularSweep(progress: Float, emblemSize: Float) {
    val t = ((progress - 0.52f) / 0.34f).coerceIn(0f, 1f)
    if (t <= 0f || t >= 1f) return
    val band = emblemSize * 0.20f
    val travel = emblemSize * 1.5f
    val x = emblemSize * 0.25f + t * travel
    val path = Path().apply {
        moveTo(x, 0f)
        lineTo(x + band, 0f)
        lineTo(x + band - emblemSize * 0.25f, size.height)
        lineTo(x - emblemSize * 0.25f, size.height)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                IntroTokens.MarkBright.copy(alpha = 0.16f),
                Color.Transparent,
            ),
            start = Offset(x, 0f),
            end = Offset(x + band, 0f),
        ),
    )
}

/* --------------------------------------------------------------------------------------------- */
/* Wordmark                                                                                       */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun WordmarkRow(
    appName: String,
    reveal: Float,
    lockup: Float,
    modifier: Modifier = Modifier,
) {
    val letters = remember(appName) { appName.toCharArray().map(Char::toString) }
    val spacing = (0.6f + 1.8f * easeOutCubic(reveal)) * (1f - 0.15f * lockup)
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        letters.forEachIndexed { index, letter ->
            val start = (index * 0.055f).coerceIn(0f, 0.55f)
            val local = ((reveal - start) / (1f - start).coerceAtLeast(0.35f)).coerceIn(0f, 1f)
            val eased = easeOutCubic(local)
            Text(
                text = letter,
                color = IntroTokens.Wordmark,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (0.01f + 0.05f * eased).em,
                    brush = Brush.linearGradient(
                        listOf(
                            IntroTokens.MarkBright,
                            IntroTokens.AuroraCyan,
                            IntroTokens.AuroraViolet,
                            IntroTokens.AuroraPink,
                        ),
                    ),
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = IntroTokens.AuroraViolet.copy(alpha = 0.40f),
                        blurRadius = 24f,
                    ),
                ),
                modifier = Modifier
                    .graphicsLayer {
                        alpha = eased
                        translationY = (1f - eased) * 26.dp.toPx()
                    }
                    .padding(horizontal = spacing.dp / 2f),
            )
        }
    }
}

private fun easeOutCubic(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return 1f - (1f - x) * (1f - x) * (1f - x)
}

/** Converts the timeline progress to whole milliseconds (diagnostics/tests only). */
internal fun IntroTimingElapsedMs(progress: Float): Long =
    (progress.coerceIn(0f, 1f) * IntroTiming.totalMs()).toLong()

/**
 * Short brand bridge shown for the one or two frames where the heavy app UI is being composed
 * after the intro (or after a skip). It prevents a flash of empty/black desktop and keeps the
 * transition feeling intentional.
 */
@Composable
internal fun PostIntroVeil(appName: String) {
    val reduceMotion = remember { !ValueAnimator.areAnimatorsEnabled() }
    val pulse = if (reduceMotion) {
        0.8f
    } else {
        val transition = rememberInfiniteTransition(label = "veil")
        transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(700),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "veil-pulse",
        ).value
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(IntroTokens.Background),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = appName,
                color = IntroTokens.Wordmark,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.08.em,
                ),
                modifier = Modifier.graphicsLayer { alpha = pulse },
            )
            Box(
                modifier = Modifier
                    .size(width = 150.dp, height = 1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, IntroTokens.Beam.copy(alpha = pulse), Color.Transparent),
                        ),
                    ),
            )
        }
    }
}

/** Fires after the next frame has been drawn (used to retire the post-intro veil). */
@Composable
internal fun OnFirstFrame(onDrawn: () -> Unit) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        onDrawn()
    }
}
