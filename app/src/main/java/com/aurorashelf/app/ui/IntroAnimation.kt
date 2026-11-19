package com.aurorashelf.app.ui

import android.content.ContentResolver
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.aurorashelf.app.R
import com.aurorashelf.app.ui.theme.AuroraMotion
import com.aurorashelf.app.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay

internal object IntroTiming {
    const val FILM_DURATION_MS = 5_700L
    const val REDUCED_HOLD_MS = 1_500L
    const val SKIP_FADE_MS = 220L
    const val SAFETY_MS = 8_500L

    fun shouldAnimate(animatorsEnabled: Boolean): Boolean = animatorsEnabled
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun BrandIntroOverlay(
    soundEnabled: Boolean,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val appName = stringResource(R.string.app_name)
    val reduceMotion = LocalReduceMotion.current
    val overlayAlpha = remember { Animatable(1f) }
    val exitEffectsSpec = AuroraMotion.fastEffects<Float>()
    var finished by remember { mutableStateOf(false) }

    val player = remember(reduceMotion) {
        if (reduceMotion) {
            null
        } else {
            runCatching {
                ExoPlayer.Builder(context.applicationContext).build().apply {
                    val uri: Uri = Uri.Builder()
                        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                        .path(R.raw.intro_film.toString())
                        .build()
                    setMediaItem(MediaItem.fromUri(uri))
                    repeatMode = Player.REPEAT_MODE_OFF
                    volume = if (soundEnabled && deviceAudible(context)) 1f else 0f
                    prepare()
                    playWhenReady = true
                }
            }.getOrNull()
        }
    }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player?.pause()
                Lifecycle.Event.ON_RESUME -> if (!finished) player?.play()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            runCatching { player?.release() }
        }
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
            overlayAlpha.animateTo(0f, exitEffectsSpec)
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
internal fun PostIntroVeil(appName: String) {
    val reduceMotion = LocalReduceMotion.current
    val alpha = if (reduceMotion) 0.88f else 0.78f
    val accent = MaterialTheme.colorScheme.primary
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
                color = Color.White.copy(alpha = alpha),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.16.em,
                ),
            )
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, accent, Color.Transparent),
                        ),
                    ),
            )
        }
    }
}

@Composable
internal fun OnFirstFrame(onDrawn: () -> Unit) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        onDrawn()
    }
}
