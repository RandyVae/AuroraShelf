package com.aurorashelf.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import com.aurorashelf.app.R
import com.aurorashelf.app.data.PlaybackResolver
import com.aurorashelf.app.data.OfflineCacheManager
import com.aurorashelf.app.data.VideoRepository
import com.aurorashelf.app.ui.theme.AuroraCoral
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import android.util.Log

/** Native playback of the site's published MP4/HLS source, with no sample-content fallback. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun NativeSitePlayer(
    pageUrl: String,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val cacheManager = remember(context) { OfflineCacheManager.get(context) }
    val offlineEntries by cacheManager.entries.collectAsStateWithLifecycle()
    val offlineVideo = offlineEntries.firstOrNull { it.pageUrl == pageUrl && it.isPlayableOffline }
    val player = remember(pageUrl) { ExoPlayer.Builder(context.applicationContext).build() }
    var position by rememberSaveable(pageUrl) { mutableLongStateOf(0L) }
    var shouldResume by rememberSaveable(pageUrl) { mutableStateOf(true) }
    var attempt by remember(pageUrl) { mutableIntStateOf(0) }
    var error by remember(pageUrl) { mutableStateOf<String?>(null) }
    var isLoading by remember(pageUrl) { mutableStateOf(true) }
    val fullscreenChange by rememberUpdatedState(onFullscreenChange)

    LaunchedEffect(pageUrl, attempt, offlineVideo?.mediaUrl) {
        Log.i(TAG, "load start attempt=$attempt host=${runCatching { java.net.URI(pageUrl).host }.getOrNull()}")
        isLoading = true
        error = null
        try {
            val source = offlineVideo?.mediaUrl?.let { cachedUrl ->
                com.aurorashelf.app.data.PlaybackSource(cachedUrl, pageUrl)
            } ?: PlaybackResolver().resolve(pageUrl)
            Log.i(TAG, "source resolved type=${mediaType(source.url)} host=${runCatching { java.net.URI(source.url).host }.getOrNull()} refererHost=${runCatching { java.net.URI(source.referer).host }.getOrNull()}")
            val http = DefaultHttpDataSource.Factory().setUserAgent(VideoRepository.USER_AGENT)
                .setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000)
                .setDefaultRequestProperties(
                    mapOf(
                        "Referer" to source.referer,
                        "Origin" to originOf(source.referer),
                        "Accept" to "*/*",
                    ),
                )
            val cachedDataSource = cacheManager.playbackDataSourceFactory(http)
            val item = MediaItem.Builder()
                .setUri(source.url)
                .apply {
                    if (offlineVideo != null && mediaType(source.url) != "hls") {
                        setCustomCacheKey(offlineVideo.id)
                    }
                }
                .build()
            val media: MediaSource = if (mediaType(source.url) == "hls") {
                HlsMediaSource.Factory(cachedDataSource).createMediaSource(item)
            } else {
                DefaultMediaSourceFactory(cachedDataSource).createMediaSource(item)
            }
            player.setMediaSource(media, position)
            player.prepare()
            player.playWhenReady = shouldResume
            Log.i(TAG, "player prepared playWhenReady=$shouldResume")
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            Log.e(TAG, "load failed ${failure::class.java.simpleName}: ${failure.message}", failure)
            isLoading = false
            error = "无法获取视频地址，请检查网络或站点后重试"
        }
    }
    LaunchedEffect(player) {
        while (true) { delay(500); position = player.currentPosition }
    }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                Log.i(TAG, "playback state=$state position=${player.currentPosition} duration=${player.duration}")
                isLoading = state == Player.STATE_BUFFERING
            }
            override fun onPlayerError(failure: PlaybackException) {
                Log.e(TAG, "player error code=${failure.errorCode} name=${failure.errorCodeName} cause=${failure.cause?.javaClass?.simpleName}: ${failure.cause?.message}", failure)
                isLoading = false
                error = if (offlineVideo != null) {
                    "离线缓存读取失败，请在设置中删除后重新缓存"
                } else {
                    "播放失败（${failure.errorCode}），请重新解析后重试"
                }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    position = player.currentPosition
                    shouldResume = player.playWhenReady
                    player.pause()
                }
                Lifecycle.Event.ON_RESUME -> if (shouldResume) player.play()
                else -> Unit
            }
        }
        player.addListener(listener)
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    BackHandler(isFullscreen) { fullscreenChange(false) }
    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { viewContext -> PlayerView(viewContext).apply {
                id = R.id.native_player
                this.player = player
                keepScreenOn = true
                setShowNextButton(false)
                setShowPreviousButton(false)
                setFullscreenButtonClickListener { fullscreenChange(it) }
            } },
            update = { playerView ->
                // AndroidView is retained when an episode changes. Rebind its surface
                // to the new ExoPlayer or the replacement player will be audio-only.
                if (playerView.player !== player) playerView.player = player
                playerView.setFullscreenButtonState(isFullscreen)
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (isLoading) CircularProgressIndicator(color = AuroraCoral, modifier = Modifier.align(Alignment.Center))
        error?.let { message ->
            Column(Modifier.align(Alignment.Center).background(Color.Black).padding(24.dp)) {
                Text(message, color = Color.White)
                if (offlineVideo == null) {
                    TextButton(onClick = { shouldResume = true; attempt++ }) { Text("重新解析并播放") }
                }
            }
        }
    }
}

private const val TAG = "NativeSitePlayer"

private fun mediaType(url: String): String = when {
    url.substringBefore('?').lowercase().endsWith(".m3u8") -> "hls"
    url.substringBefore('?').lowercase().endsWith(".mp4") -> "mp4"
    else -> "unknown"
}

private fun originOf(referer: String): String = runCatching {
    val uri = java.net.URI(referer)
    "${uri.scheme}://${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}"
}.getOrDefault(referer)
