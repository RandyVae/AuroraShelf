package com.aurorashelf.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
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
    onVideoOrientationChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val cacheManager = remember(context) { OfflineCacheManager.get(context) }
    val offlineEntries by cacheManager.entries.collectAsStateWithLifecycle()
    val offlineVideo = offlineEntries.firstOrNull { it.pageUrl == pageUrl && it.isPlayableOffline }
    val player = remember(pageUrl) {
        // Start faster: playback may begin after 800 ms of buffered media (default 2.5 s) and
        // resumes after a rebuffer once 1.5 s is buffered (default 5 s), while the forward buffer
        // is kept between 15 s and 30 s (default 50 s/50 s). media3 1.11 folds both playback
        // thresholds into setBufferDurationsMs(min, max, forPlayback, afterRebuffer).
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 30_000, 800, 1_500)
            .build()
        ExoPlayer.Builder(context.applicationContext)
            .setLoadControl(loadControl)
            .build().apply {
                // G5: when the (external) media declares text tracks, prefer Chinese then English
                // subtitles and let ExoPlayer auto-select one. Media without subtitle tracks is
                // unaffected: preferred languages only steer track selection.
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredTextLanguages("zh-Hans", "zh", "en")
                    .setSelectUndeterminedTextLanguage(true)
                    .build()
            }
    }
    var position by rememberSaveable(pageUrl) { mutableLongStateOf(0L) }
    var shouldResume by rememberSaveable(pageUrl) { mutableStateOf(true) }
    var attempt by remember(pageUrl) { mutableIntStateOf(0) }
    var error by remember(pageUrl) { mutableStateOf<String?>(null) }
    var isLoading by remember(pageUrl) { mutableStateOf(true) }
    var lastKnownPortrait by remember(pageUrl) { mutableStateOf<Boolean?>(null) }
    var subtitleMenuExpanded by remember(pageUrl) { mutableStateOf(false) }
    var subtitleOptions by remember(pageUrl) { mutableStateOf<List<SubtitleOption>>(emptyList()) }
    val loadStartedAtMs = remember(pageUrl) { mutableLongStateOf(0L) }
    val timingReadyLogged = remember(pageUrl) { mutableStateOf(false) }
    val timingFirstFrameLogged = remember(pageUrl) { mutableStateOf(false) }
    val fullscreenChange by rememberUpdatedState(onFullscreenChange)
    val videoOrientationChange by rememberUpdatedState(onVideoOrientationChange)

    LaunchedEffect(pageUrl, attempt, offlineVideo?.mediaUrl) {
        loadStartedAtMs.longValue = android.os.SystemClock.elapsedRealtime()
        timingReadyLogged.value = false
        timingFirstFrameLogged.value = false
        Log.i(TAG, "load start attempt=$attempt host=${runCatching { java.net.URI(pageUrl).host }.getOrNull()}")
        isLoading = true
        error = null
        try {
            val source = offlineVideo?.mediaUrl?.let { cachedUrl ->
                com.aurorashelf.app.data.PlaybackSource(cachedUrl, pageUrl)
            } ?: PlaybackResolver().resolve(pageUrl)
            Log.i(TAG, "source resolved type=${mediaType(source.url)} host=${runCatching { java.net.URI(source.url).host }.getOrNull()} refererHost=${runCatching { java.net.URI(source.referer).host }.getOrNull()}")
            Log.i(TAG, "timing source resolved ms=${android.os.SystemClock.elapsedRealtime() - loadStartedAtMs.longValue}")
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
            Log.i(TAG, "timing prepared ms=${android.os.SystemClock.elapsedRealtime() - loadStartedAtMs.longValue}")
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
                if (state == Player.STATE_READY && !timingReadyLogged.value) {
                    timingReadyLogged.value = true
                    Log.i(TAG, "timing ready ms=${android.os.SystemClock.elapsedRealtime() - loadStartedAtMs.longValue}")
                }
                isLoading = state == Player.STATE_BUFFERING
            }
            override fun onRenderedFirstFrame() {
                if (!timingFirstFrameLogged.value) {
                    timingFirstFrameLogged.value = true
                    Log.i(TAG, "timing first frame ms=${android.os.SystemClock.elapsedRealtime() - loadStartedAtMs.longValue}")
                }
            }
            override fun onTracksChanged(tracks: Tracks) {
                subtitleOptions = buildSubtitleOptions(tracks)
                Log.i(TAG, "subtitle tracks text=${subtitleOptions.size}")
            }
            @Suppress("DEPRECATION")
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val isPortrait = isPortraitVideo(
                    width = videoSize.width,
                    height = videoSize.height,
                    pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio,
                    unappliedRotationDegrees = videoSize.unappliedRotationDegrees,
                ) ?: return
                if (lastKnownPortrait != isPortrait) {
                    lastKnownPortrait = isPortrait
                    videoOrientationChange(isPortrait)
                }
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
    fun selectSubtitle(option: SubtitleOption) {
        val parameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(option.group, option.trackIndex))
        // Keep the picked language as the fallback preference in case the media re-creates its
        // track groups (adaptive HLS) and the explicit override no longer matches.
        option.language?.let { language -> parameters.setPreferredTextLanguages(language) }
        player.trackSelectionParameters = parameters.build()
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .build()
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
        // Hidden entirely when the media declares no text track, so no empty control is shown.
        if (subtitleOptions.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    // Non-fullscreen: below PlayerScreen's 44dp back button; fullscreen: near edge.
                    .padding(start = 10.dp, top = if (isFullscreen) 10.dp else 58.dp),
            ) {
                IconButton(
                    onClick = { subtitleMenuExpanded = true },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.58f)),
                ) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = "字幕",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                DropdownMenu(
                    expanded = subtitleMenuExpanded,
                    onDismissRequest = { subtitleMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("关闭字幕") },
                        trailingIcon = {
                            if (subtitleOptions.none { it.selected }) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            subtitleMenuExpanded = false
                            setSubtitlesEnabled(false)
                        },
                    )
                    subtitleOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            trailingIcon = {
                                if (option.selected) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                subtitleMenuExpanded = false
                                selectSubtitle(option)
                            },
                        )
                    }
                }
            }
        }
    }
}

private const val TAG = "NativeSitePlayer"

internal fun isPortraitVideo(
    width: Int,
    height: Int,
    pixelWidthHeightRatio: Float = 1f,
    unappliedRotationDegrees: Int = 0,
): Boolean? {
    if (width <= 0 || height <= 0 || pixelWidthHeightRatio <= 0f) return null
    val isQuarterTurn = Math.floorMod(unappliedRotationDegrees, 180) == 90
    val displayWidth = if (isQuarterTurn) height.toFloat() else width * pixelWidthHeightRatio
    val displayHeight = if (isQuarterTurn) width * pixelWidthHeightRatio else height.toFloat()
    return displayHeight > displayWidth
}

private fun mediaType(url: String): String = when {
    url.substringBefore('?').lowercase().endsWith(".m3u8") -> "hls"
    url.substringBefore('?').lowercase().endsWith(".mp4") -> "mp4"
    else -> "unknown"
}

private fun originOf(referer: String): String = runCatching {
    val uri = java.net.URI(referer)
    "${uri.scheme}://${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}"
}.getOrDefault(referer)

/** One selectable text track from the player's current tracks. */
private data class SubtitleOption(
    val label: String,
    val group: TrackGroup,
    val trackIndex: Int,
    val selected: Boolean,
    val language: String?,
)

private fun buildSubtitleOptions(tracks: Tracks): List<SubtitleOption> {
    val options = mutableListOf<SubtitleOption>()
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_TEXT) continue
        for (index in 0 until group.length) {
            if (!group.isTrackSupported(index)) continue
            val format = group.getTrackFormat(index)
            options += SubtitleOption(
                label = subtitleTrackLabel(format, index),
                group = group.mediaTrackGroup,
                trackIndex = index,
                selected = group.isTrackSelected(index),
                language = format.language?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }
    val duplicatedLabels = options.groupingBy { it.label }.eachCount().filterValues { it > 1 }.keys
    if (duplicatedLabels.isEmpty()) return options
    return options.mapIndexed { position, option ->
        if (option.label in duplicatedLabels) {
            option.copy(label = "${option.label} ${position + 1}")
        } else {
            option
        }
    }
}

private fun subtitleTrackLabel(format: Format, index: Int): String {
    val label = format.label?.trim().orEmpty()
    if (label.isNotEmpty()) return label
    val language = format.language?.trim().orEmpty()
    return when {
        language.isEmpty() -> "字幕 ${index + 1}"
        language.startsWith("zh", ignoreCase = true) || language.equals("zho", ignoreCase = true) -> "中文"
        language.startsWith("en", ignoreCase = true) || language.equals("eng", ignoreCase = true) -> "English"
        else -> language
    }
}
