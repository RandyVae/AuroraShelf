package com.aurorashelf.app.ui

import android.animation.ValueAnimator
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.aurorashelf.app.R
import kotlinx.coroutines.delay

/**
 * Launch brand film timing. The visual asset is an original pre-rendered 6.6 s film
 * (brushed-platinum Didot "N", carved out of darkness by a light blade, with an original score).
 */
internal object IntroTiming {
    /** Length of the shipped film; used for the safety timeout and skip fade. */
    const val FILM_DURATION_MS = 5_700L

    /** Reduce Motion: hold the poster lockup instead of playing motion. */
    const val REDUCED_HOLD_MS = 1_500L

    /** Fade used when the user skips or the film ends. */
    const val SKIP_FADE_MS = 220L

    /** Hard safety stop in case the player never reports ENDED. */
    const val SAFETY_MS = 8_500L

    fun shouldAnimate(animatorsEnabled: Boolean): Boolean = animatorsEnabled
}

/**
 * Plays the original launch film above the (still composing) app.
 *
 * - Tap anywhere / Back: skip immediately.
 * - Reduce Motion: show the film's lockup poster statically for 1.5 s, no audio.
 * - Silent mode / zero media volume / user setting: audio muted; the film itself is unchanged.
 * - The app is composed behind this overlay and its first frame is covered by [PostIntroVeil].
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun BrandIntroOverlay(
    soundEnabled: Boolean,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val appName = stringResourceAppName()
    val reduceMotion = remember { !ValueAnimator.areAnimatorsEnabled() }
    val overlayAlpha = remember { Animatable(1f) }
    var finished by remember { mutableStateOf(false) }

    val player = remember(reduceMotion) {
        if (reduceMotion) {
            null
        } else {
            runCatching {
                ExoPlayer.Builder(context.applicationContext).build().apply {
                    val uri: Uri = RawResourceDataSource.buildRawResourceUri(R.raw.intro_film)
                    setMediaItem(MediaItem.fromUri(uri))
                    repeatMode = Player.REPEAT_MODE_OFF
                    volume = if (soundEnabled && deviceAudible(context)) 1f else 0f
                    prepare()
                    playWhenReady = true
                }
            }.getOrNull()
        }
    }
    DisposableEffect(player) {
        onDispose { runCatching { player?.release() } }
    }

    LaunchedEffect(player, reduceMotion) {
        if (reduceMotion || player == null) {
            delay(IntroTiming.REDUCED_HOLD_MS)
            finished = true
            return@LaunchedEffect
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) finished = true
            }
        }
        player.addListener(listener)
        delay(IntroTiming.SAFETY_MS)
        if (!finished) finished = true
        player.removeListener(listener)
    }
    LaunchedEffect(finished) {
        if (finished) {
            overlayAlpha.animateTo(0f, tween(IntroTiming.SKIP_FADE_MS.toInt()))
            onFinished()
        }
    }

    BackHandler { finished = true }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = "跳过开场动画",
            ) { finished = true }
            .semantics { contentDescription = "$appName 品牌开场，点按跳过" },
    ) {
        // Underlay: a near-black first frame hides the player's start gap; Reduce Motion shows
        // the finished lockup as a poster instead.
        Image(
            painter = painterResource(
                if (reduceMotion) R.drawable.intro_poster else R.drawable.intro_film_start,
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setShutterBackgroundColor(AndroidColor.BLACK)
                        this.player = player
                    }
                },
                update = { view -> if (view.player !== player) view.player = player },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun deviceAudible(context: android.content.Context): Boolean {
    val audio = context.getSystemService(AudioManager::class.java) ?: return true
    return audio.ringerMode != AudioManager.RINGER_MODE_SILENT &&
        audio.getStreamVolume(AudioManager.STREAM_MUSIC) > 0
}

@Composable
private fun stringResourceAppName(): String = androidx.compose.ui.res.stringResource(R.string.app_name)

/**
 * Short brand bridge shown while the heavy app UI composes after the film (or after a skip).
 * It prevents a flash of empty desktop and keeps the transition intentional.
 */
@Composable
internal fun PostIntroVeil(appName: String) {
    val reduceMotion = remember { !ValueAnimator.areAnimatorsEnabled() }
    val alpha = if (reduceMotion) 0.85f else 0.75f
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(Color.Black),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = appName,
                color = Color(0xFFE7E2D8).copy(alpha = alpha),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.16.em,
                ),
            )
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 1.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(Color.Transparent, Color(0xFF8E2F3C), Color.Transparent),
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
