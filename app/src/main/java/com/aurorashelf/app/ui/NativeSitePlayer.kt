@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.aurorashelf.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
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
import com.aurorashelf.app.model.ContentSourceCatalog
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.aurorashelf.app.LocalIsInPipMode
import com.aurorashelf.app.MainActivity
import com.aurorashelf.app.R
import com.aurorashelf.app.data.AppPreferences
import com.aurorashelf.app.data.OfflineCacheManager
import com.aurorashelf.app.data.PlaybackResolver
import com.aurorashelf.app.data.VideoRepository
import com.aurorashelf.app.data.playback.ResilientHttpDataSourceFactory
import com.aurorashelf.app.data.playback.ResilientLoadErrorHandlingPolicy
import com.aurorashelf.app.ui.theme.AuroraCoral
import com.aurorashelf.app.ui.theme.AuroraHaptics
import com.aurorashelf.app.ui.theme.AuroraInkSurface
import com.aurorashelf.app.ui.theme.AuroraMediaSurface
import com.aurorashelf.app.ui.theme.AuroraMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import android.util.Log
import kotlin.math.roundToInt

private data class PlayerHudData(
    val icon: ImageVector,
    val text: String,
    val progress: Float? = null,
    val id: Long = System.nanoTime(),
)

/** Native playback of the site's published MP4/HLS source, with no sample-content fallback. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun NativeSitePlayer(
    pageUrl: String,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    onVideoOrientationChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onVideoAspectRatioChange: (Float) -> Unit = {},
    onPositionChange: (Long) -> Unit = {},
    onDurationChange: (Long) -> Unit = {},
    onControllerVisibilityChange: (Boolean) -> Unit = {},
    onPlaybackEnded: (() -> Unit)? = null,
    keepTopBarVisible: Boolean = false,
    suppressPlayback: Boolean = false,
    topBarStart: (@Composable () -> Unit)? = null,
    topBarEnd: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val mainActivity = remember(context) { context.findMainActivity() }
    val isInPipMode = LocalIsInPipMode.current
    val appPreferences = remember(context) { AppPreferences.get(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val cacheManager = remember(context) { OfflineCacheManager.get(context) }
    val offlineEntries by cacheManager.entries.collectAsStateWithLifecycle()
    val offlineVideo = offlineEntries.firstOrNull { it.pageUrl == pageUrl && it.isPlayableOffline }
    val latestOfflineVideo by rememberUpdatedState(offlineVideo)
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
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
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
    var position by rememberSaveable(pageUrl) { mutableLongStateOf(appPreferences.getPlaybackProgress(pageUrl)) }
    var currentPositionMs by rememberSaveable(pageUrl) { mutableLongStateOf(position) }
    var bufferedPositionMs by rememberSaveable(pageUrl) { mutableLongStateOf(0L) }
    var durationMs by rememberSaveable(pageUrl) { mutableLongStateOf(0L) }
    var videoSizePx by remember(pageUrl) { mutableStateOf(16 to 9) }
    var isControllerVisible by remember(pageUrl) { mutableStateOf(true) }
    var isPlaying by remember(pageUrl) { mutableStateOf(true) }
    var isScrubbing by remember(pageUrl) { mutableStateOf(false) }
    var isFillMode by rememberSaveable(pageUrl) { mutableStateOf(false) }
    var showRemainingTime by rememberSaveable { mutableStateOf(true) }
    var playbackSpeed by rememberSaveable(pageUrl) { mutableFloatStateOf(1.0f) }
    var speedMenuExpanded by remember(pageUrl) { mutableStateOf(false) }
    var controlInteractionTick by remember(pageUrl) { mutableIntStateOf(0) }
    var hudState by remember(pageUrl) { mutableStateOf<PlayerHudData?>(null) }
    var shouldResume by rememberSaveable(pageUrl) { mutableStateOf(true) }
    var attempt by remember(pageUrl) { mutableIntStateOf(0) }
    var recoveryStage by remember(pageUrl) { mutableIntStateOf(0) }
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
    val videoAspectRatioChange by rememberUpdatedState(onVideoAspectRatioChange)
    val positionChange by rememberUpdatedState(onPositionChange)
    val durationChange by rememberUpdatedState(onDurationChange)
    val controllerVisibilityChange by rememberUpdatedState(onControllerVisibilityChange)
    val playbackEndedChange by rememberUpdatedState(onPlaybackEnded)

    LaunchedEffect(isControllerVisible) {
        controllerVisibilityChange(isControllerVisible)
    }

    LaunchedEffect(isFullscreen) {
        isControllerVisible = true
        controlInteractionTick++
    }

    LaunchedEffect(isControllerVisible, isPlaying, isScrubbing, subtitleMenuExpanded, speedMenuExpanded, controlInteractionTick) {
        if (isControllerVisible && isPlaying && !isScrubbing && !subtitleMenuExpanded && !speedMenuExpanded) {
            delay(3_500L)
            isControllerVisible = false
        }
    }

    LaunchedEffect(hudState?.id) {
        if (hudState != null) {
            delay(1_200L)
            hudState = null
        }
    }

    val castManager = remember(context) { com.aurorashelf.app.cast.CastManager.getInstance(context) }
    val castState by castManager.castState.collectAsStateWithLifecycle()
    val currentCastState by rememberUpdatedState(castState)
    val haptic = LocalHapticFeedback.current
    var pipSourceRect by remember(pageUrl) { mutableStateOf<Rect?>(null) }

    LaunchedEffect(castState) {
        if (castState is com.aurorashelf.app.cast.CastState.Connected) {
            player.pause()
        }
    }

    LaunchedEffect(mainActivity, isPlaying, error, suppressPlayback, videoSizePx, castState, pipSourceRect) {
        val canAutoPip = isPlaying &&
            error == null &&
            !suppressPlayback &&
            castState !is com.aurorashelf.app.cast.CastState.Connected
        mainActivity?.updatePipPlaybackState(
            isPlayingVideo = canAutoPip,
            videoWidth = videoSizePx.first,
            videoHeight = videoSizePx.second,
            sourceRectHint = pipSourceRect,
            onTogglePlayback = {
                if (player.isPlaying) {
                    player.pause()
                    isPlaying = false
                } else {
                    player.play()
                    isPlaying = true
                }
            },
        )
    }

    DisposableEffect(mainActivity) {
        onDispose {
            mainActivity?.updatePipPlaybackState(isPlayingVideo = false)
        }
    }

    LaunchedEffect(pageUrl, attempt) {
        loadStartedAtMs.longValue = android.os.SystemClock.elapsedRealtime()
        timingReadyLogged.value = false
        timingFirstFrameLogged.value = false
        Log.i(TAG, "load start attempt=$attempt host=${runCatching { java.net.URI(pageUrl).host }.getOrNull()}")
        isLoading = true
        error = null
        try {
            val activeOffline = latestOfflineVideo
            val resolver = PlaybackResolver()
            val forceRefresh = recoveryStage > 0
            val altLineIndex = (recoveryStage - 1).coerceAtLeast(0)
            val source = activeOffline?.mediaUrl?.let { cachedUrl ->
                com.aurorashelf.app.data.PlaybackSource(
                    url = cachedUrl,
                    referer = activeOffline.referer?.ifBlank { pageUrl } ?: pageUrl,
                    extraHeaders = activeOffline.extraHeaders,
                )
            } ?: resolver.resolve(pageUrl, forceRefresh = forceRefresh, alternateLineIndex = altLineIndex)
            Log.i(TAG, "source resolved type=${mediaType(source.url)} host=${runCatching { java.net.URI(source.url).host }.getOrNull()} refererHost=${runCatching { java.net.URI(source.referer).host }.getOrNull()} stage=$recoveryStage")
            Log.i(TAG, "timing source resolved ms=${android.os.SystemClock.elapsedRealtime() - loadStartedAtMs.longValue}")
            val http = ResilientHttpDataSourceFactory(VideoRepository.USER_AGENT)
                .setDefaultRequestProperties(
                    mutableMapOf<String, String>().apply {
                        put("Referer", source.referer)
                        put("Origin", originOf(source.referer))
                        put("Accept", "*/*")
                        putAll(source.extraHeaders)
                    },
                )
            val cachedDataSource = cacheManager.playbackDataSourceFactory(http)
            val loadErrorPolicy = ResilientLoadErrorHandlingPolicy()
            val item = MediaItem.Builder()
                .setUri(source.url)
                .apply {
                    if (activeOffline != null && mediaType(source.url) != "hls") {
                        setCustomCacheKey(activeOffline.id)
                    }
                }
                .build()
            val media: MediaSource = if (mediaType(source.url) == "hls") {
                HlsMediaSource.Factory(cachedDataSource)
                    .setLoadErrorHandlingPolicy(loadErrorPolicy)
                    .createMediaSource(item)
            } else {
                DefaultMediaSourceFactory(cachedDataSource)
                    .setLoadErrorHandlingPolicy(loadErrorPolicy)
                    .createMediaSource(item)
            }
            player.setMediaSource(media, position)
            val isCurrentlyCasting = currentCastState is com.aurorashelf.app.cast.CastState.Connected
            if (suppressPlayback) {
                player.playWhenReady = false
                player.pause()
            } else {
                player.prepare()
                val canAutoStart = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                player.playWhenReady = shouldResume && !isCurrentlyCasting && canAutoStart
            }
            Log.i(TAG, "player prepared playWhenReady=${player.playWhenReady}")
            Log.i(TAG, "timing prepared ms=${android.os.SystemClock.elapsedRealtime() - loadStartedAtMs.longValue}")
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            Log.e(TAG, "load failed ${failure::class.java.simpleName}: ${failure.message}", failure)
            isLoading = false
            error = "无法获取视频地址，请检查网络或站点后重试"
        }
    }
    LaunchedEffect(suppressPlayback, player) {
        if (suppressPlayback) {
            shouldResume = false
            player.playWhenReady = false
            player.pause()
            player.stop()
            player.clearMediaItems()
        }
    }
    LaunchedEffect(player, isPlaying, isScrubbing, suppressPlayback) {
        var lastReportedSecond = -1L
        var lastReportedDuration = -1L
        var lastPersistedAtMs = 0L
        while (true) {
            val state = player.playbackState
            val current = player.currentPosition.coerceAtLeast(0L)
            val dur = player.duration.coerceAtLeast(0L)
            val buf = player.bufferedPosition.coerceAtLeast(0L)
            if (!isScrubbing) {
                currentPositionMs = current
            }
            durationMs = dur
            bufferedPositionMs = buf
            isPlaying = player.isPlaying
            if (!suppressPlayback && (state == Player.STATE_READY || state == Player.STATE_BUFFERING)) {
                position = current
                val currentSec = current / 1_000L
                if (currentSec != lastReportedSecond) {
                    lastReportedSecond = currentSec
                    positionChange(current)
                }
                val nowMs = android.os.SystemClock.elapsedRealtime()
                if (state == Player.STATE_READY && dur > 0L && nowMs - lastPersistedAtMs >= 2_500L) {
                    lastPersistedAtMs = nowMs
                    appPreferences.savePlaybackProgress(pageUrl, current, dur)
                }
            }
            if (dur != lastReportedDuration) {
                lastReportedDuration = dur
                durationChange(dur)
            }
            if (!player.isPlaying && !isScrubbing && state != Player.STATE_BUFFERING) {
                delay(500)
            } else {
                delay(200)
            }
        }
    }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            @Suppress("DEPRECATION")
            override fun onPlaybackStateChanged(state: Int) {
                Log.i(TAG, "playback state=$state position=${player.currentPosition} duration=${player.duration}")
                if (state == Player.STATE_READY && !timingReadyLogged.value) {
                    timingReadyLogged.value = true
                    Log.i(TAG, "timing ready ms=${android.os.SystemClock.elapsedRealtime() - loadStartedAtMs.longValue}")
                    val currentSize = player.videoSize
                    if (currentSize.width > 0 && currentSize.height > 0) {
                        videoSizePx = currentSize.width to currentSize.height
                    }
                    val ratio = calculateVideoAspectRatio(
                        width = currentSize.width,
                        height = currentSize.height,
                        pixelWidthHeightRatio = currentSize.pixelWidthHeightRatio,
                        unappliedRotationDegrees = currentSize.unappliedRotationDegrees,
                    )
                    if (ratio != null) {
                        videoAspectRatioChange(ratio)
                    }
                }
                if (state == Player.STATE_ENDED) {
                    val dur = player.duration.coerceAtLeast(0L)
                    if (dur > 0L) {
                        appPreferences.savePlaybackProgress(pageUrl, dur, dur)
                    }
                    Log.i(TAG, "playback state ended, invoking playbackEnded callback")
                    playbackEndedChange?.invoke()
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
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoSizePx = videoSize.width to videoSize.height
                }
                val ratio = calculateVideoAspectRatio(
                    width = videoSize.width,
                    height = videoSize.height,
                    pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio,
                    unappliedRotationDegrees = videoSize.unappliedRotationDegrees,
                )
                if (ratio != null) {
                    videoAspectRatioChange(ratio)
                }
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
                Log.e(TAG, "player error code=${failure.errorCode} name=${failure.errorCodeName} stage=$recoveryStage cause=${failure.cause?.javaClass?.simpleName}: ${failure.cause?.message}", failure)
                if (latestOfflineVideo == null &&
                    ResilientLoadErrorHandlingPolicy.isRecoverablePlayerErrorCode(failure.errorCode) &&
                    recoveryStage < 2
                ) {
                    recoveryStage += 1
                    isLoading = true
                    error = null
                    shouldResume = true
                    attempt++
                    return
                }
                isLoading = false
                error = if (latestOfflineVideo != null) {
                    "离线缓存读取失败，请在设置中删除后重新缓存"
                } else {
                    "播放失败（${failure.errorCode}），请重新解析后重试"
                }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (mainActivity?.isInPictureInPictureMode == true) return@LifecycleEventObserver
                    if (player.playbackState != Player.STATE_IDLE) {
                        val current = player.currentPosition.coerceAtLeast(0L)
                        val dur = player.duration.coerceAtLeast(0L)
                        position = current
                        positionChange(current)
                        if (dur > 0L) {
                            appPreferences.savePlaybackProgress(pageUrl, current, dur)
                        }
                        shouldResume = player.playWhenReady
                    }
                    player.pause()
                }
                Lifecycle.Event.ON_START -> {
                    if (shouldResume &&
                        !suppressPlayback &&
                        currentCastState !is com.aurorashelf.app.cast.CastState.Connected
                    ) {
                        player.play()
                    }
                }
                else -> Unit
            }
        }
        player.addListener(listener)
        lifecycle.addObserver(observer)
        onDispose {
            if (player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) {
                val current = player.currentPosition.coerceAtLeast(0L)
                val dur = player.duration.coerceAtLeast(0L)
                if (dur > 0L) {
                    appPreferences.savePlaybackProgress(pageUrl, current, dur)
                }
            }
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.playWhenReady = false
            player.pause()
            player.stop()
            player.clearMediaItems()
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

    val activityWindow = (context as? Activity)?.window
    DisposableEffect(activityWindow, pageUrl) {
        val initialBrightness = activityWindow?.attributes?.screenBrightness
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        onDispose {
            activityWindow?.let { win ->
                val lp = win.attributes
                if (lp.screenBrightness != initialBrightness) {
                    lp.screenBrightness = initialBrightness
                    win.attributes = lp
                }
            }
        }
    }

    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    fun showHud(icon: ImageVector, text: String, progress: Float? = null) {
        hudState = PlayerHudData(icon = icon, text = text, progress = progress)
    }

    fun seekRelative(deltaMs: Long) {
        val maxDur = if (durationMs > 0L) durationMs else 3_600_000L
        val target = (player.currentPosition + deltaMs).coerceIn(0L, maxDur)
        player.seekTo(target)
        currentPositionMs = target
        controlInteractionTick++
    }

    fun toggleFillMode() {
        isFillMode = !isFillMode
        showHud(
            icon = Icons.Default.AspectRatio,
            text = if (isFillMode) "填满屏幕" else "适合屏幕",
        )
        controlInteractionTick++
    }

    BackHandler(isFullscreen) { fullscreenChange(false) }
    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInWindow()
                val rect = Rect(
                    bounds.left.roundToInt(),
                    bounds.top.roundToInt(),
                    bounds.right.roundToInt(),
                    bounds.bottom.roundToInt(),
                )
                if (!rect.isEmpty && rect != pipSourceRect) {
                    pipSourceRect = rect
                }
            }
            .background(Color.Black)
            .pointerInput(pageUrl, isFullscreen) {
                val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
                var lastTapMs = 0L
                var lastTapOffset = Offset.Zero
                var visibilityBeforeFirstTap = isControllerVisible
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    if (up.isConsumed) return@awaitEachGesture
                    if ((up.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        return@awaitEachGesture
                    }
                    up.consume()
                    val now = android.os.SystemClock.uptimeMillis()
                    val isDoubleTap = (now - lastTapMs) <= doubleTapTimeout &&
                        (up.position - lastTapOffset).getDistance() <= viewConfiguration.touchSlop * 4f
                    if (isDoubleTap) {
                        lastTapMs = 0L
                        isControllerVisible = visibilityBeforeFirstTap
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        when {
                            up.position.x < w * 0.35f -> {
                                seekRelative(-10_000L)
                                showHud(Icons.Default.Replay10, "快退 10 秒")
                            }
                            up.position.x > w * 0.65f -> {
                                seekRelative(10_000L)
                                showHud(Icons.Default.Forward10, "快进 10 秒")
                            }
                            else -> toggleFillMode()
                        }
                    } else {
                        visibilityBeforeFirstTap = isControllerVisible
                        isControllerVisible = !isControllerVisible
                        controlInteractionTick++
                        lastTapMs = now
                        lastTapOffset = up.position
                    }
                }
            }
            .pointerInput(pageUrl, isFullscreen) {
                if (!isFullscreen) return@pointerInput
                var gestureSide = 0 // 1 = left (brightness), 2 = right (volume)
                var accumDeltaY = 0f
                var initialFraction = 0.5f
                detectVerticalDragGestures(
                    onDragStart = { startOffset ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        accumDeltaY = 0f
                        if (startOffset.x < w * 0.45f) {
                            gestureSide = 1
                            val win = (context as? Activity)?.window
                            val curB = win?.attributes?.screenBrightness ?: -1f
                            initialFraction = if (curB in 0.01f..1.0f) curB else 0.5f
                        } else if (startOffset.x > w * 0.55f) {
                            gestureSide = 2
                            val maxVol = (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
                            val curVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: (maxVol / 2)
                            initialFraction = curVol.toFloat() / maxVol.toFloat()
                        } else {
                            gestureSide = 0
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (gestureSide == 0) return@detectVerticalDragGestures
                        change.consume()
                        accumDeltaY += -dragAmount
                        val h = size.height.toFloat().coerceAtLeast(200f)
                        val nextFraction = (initialFraction + (accumDeltaY / (h * 0.65f))).coerceIn(0f, 1f)
                        if (gestureSide == 1) {
                            val win = (context as? Activity)?.window
                            if (win != null) {
                                val lp = win.attributes
                                lp.screenBrightness = nextFraction.coerceAtLeast(0.02f)
                                win.attributes = lp
                            }
                            showHud(
                                icon = Icons.Default.LightMode,
                                text = "亮度 ${(nextFraction * 100).roundToInt()}%",
                                progress = nextFraction,
                            )
                        } else if (gestureSide == 2) {
                            val maxVol = (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
                            val targetVol = (nextFraction * maxVol).roundToInt().coerceIn(0, maxVol)
                            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                            showHud(
                                icon = if (targetVol == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                text = "音量 ${(nextFraction * 100).roundToInt()}%",
                                progress = nextFraction,
                            )
                        }
                    },
                    onDragEnd = { gestureSide = 0 },
                    onDragCancel = { gestureSide = 0 },
                )
            },
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    id = R.id.native_player
                    this.player = player
                    useController = false
                    keepScreenOn = isPlaying && currentCastState !is com.aurorashelf.app.cast.CastState.Connected
                    resizeMode = if (isFillMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }
            },
            update = { playerView ->
                if (playerView.player !== player) playerView.player = player
                playerView.useController = false
                playerView.keepScreenOn = isPlaying && currentCastState !is com.aurorashelf.app.cast.CastState.Connected
                playerView.resizeMode = if (isFillMode) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.align(Alignment.Center),
            ) {
                AuroraLoadingIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                if (ContentSourceCatalog.isHuavod(pageUrl)) {
                    Text(
                        text = "正在准备超清视频流...",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        error?.let { message ->
            Column(Modifier.align(Alignment.Center).background(Color.Black).padding(24.dp)) {
                Text(message, color = Color.White)
                if (offlineVideo == null) {
                    TextButton(onClick = {
                        AuroraHaptics.tap(haptic)
                        shouldResume = true
                        recoveryStage = 1
                        attempt++
                    }) { Text("重新解析并播放") }
                }
            }
        }

        // Material 3 Expressive 顶部控制栏
        AnimatedVisibility(
            visible = !isInPipMode && (isControllerVisible || keepTopBarVisible || error != null),
            enter = fadeIn(AuroraMotion.fastEffects()),
            exit = fadeOut(AuroraMotion.fastEffects()),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (isControllerVisible && error == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isFullscreen) 96.dp else 72.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.76f), Color.Transparent),
                                ),
                            ),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (isFullscreen) 16.dp else 10.dp,
                            vertical = 8.dp,
                        ),
                ) {
                    if (isFullscreen) {
                        FilledTonalIconButton(
                            onClick = {
                                AuroraHaptics.tap(haptic)
                                fullscreenChange(false)
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = AuroraMediaSurface.copy(alpha = 0.76f),
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "退出全屏",
                            )
                        }
                    } else {
                        topBarStart?.invoke()
                    }

                    Spacer(Modifier.weight(1f))

                    if (isControllerVisible && error == null) {
                        // 倍速选择胶囊（0.5x ~ 2.0x）
                        Box {
                            Surface(
                                onClick = {
                                    AuroraHaptics.tap(haptic)
                                    speedMenuExpanded = true
                                    controlInteractionTick++
                                },
                                shape = CircleShape,
                                color = AuroraMediaSurface.copy(alpha = 0.76f),
                                contentColor = Color.White,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .height(40.dp),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                ) {
                                    Text(
                                        text = if (playbackSpeed == 1.0f) "1.0x" else "${playbackSpeed}x",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = speedMenuExpanded,
                                onDismissRequest = { speedMenuExpanded = false },
                                containerColor = AuroraInkSurface,
                            ) {
                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (speed == 1.0f) "正常 (1.0x)" else "${speed}x",
                                                color = Color.White,
                                            )
                                        },
                                        trailingIcon = {
                                            if (playbackSpeed == speed) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        },
                                        onClick = {
                                            AuroraHaptics.segmentTick(haptic)
                                            speedMenuExpanded = false
                                            playbackSpeed = speed
                                            player.setPlaybackSpeed(speed)
                                            showHud(Icons.Default.Speed, "${speed}x 倍速")
                                            controlInteractionTick++
                                        },
                                    )
                                }
                            }
                        }

                        if (subtitleOptions.isNotEmpty()) {
                            Box {
                                FilledTonalIconButton(
                                    onClick = {
                                        AuroraHaptics.tap(haptic)
                                        subtitleMenuExpanded = true
                                        controlInteractionTick++
                                    },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = AuroraMediaSurface.copy(alpha = 0.76f),
                                        contentColor = Color.White,
                                    ),
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Subtitles,
                                        contentDescription = "字幕",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                DropdownMenu(
                                    expanded = subtitleMenuExpanded,
                                    onDismissRequest = { subtitleMenuExpanded = false },
                                    containerColor = AuroraInkSurface,
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("关闭字幕", color = Color.White) },
                                        trailingIcon = {
                                            if (subtitleOptions.none { it.selected }) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        },
                                        onClick = {
                                            AuroraHaptics.toggle(haptic, false)
                                            subtitleMenuExpanded = false
                                            setSubtitlesEnabled(false)
                                        },
                                    )
                                    subtitleOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label, color = Color.White) },
                                            trailingIcon = {
                                                if (option.selected) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            },
                                            onClick = {
                                                AuroraHaptics.segmentTick(haptic)
                                                subtitleMenuExpanded = false
                                                selectSubtitle(option)
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        if (mainActivity?.supportsPip() == true && !isInPipMode) {
                            FilledTonalIconButton(
                                onClick = {
                                    AuroraHaptics.tap(haptic)
                                    mainActivity.requestEnterPip(
                                        videoWidth = videoSizePx.first,
                                        videoHeight = videoSizePx.second,
                                        sourceRectHint = pipSourceRect,
                                    )
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = AuroraMediaSurface.copy(alpha = 0.76f),
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "画中画",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        if (isFullscreen) {
                            FilledTonalIconButton(
                                onClick = {
                                    AuroraHaptics.toggle(haptic, !isFillMode)
                                    toggleFillMode()
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = AuroraMediaSurface.copy(alpha = 0.76f),
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = if (isFillMode) "适合屏幕" else "填满屏幕",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    if (!isFullscreen) {
                        topBarEnd?.invoke()
                    }
                }
            }
        }

        // Material 3 Expressive 播放中枢控制与弹性时间轴
        AnimatedVisibility(
            visible = !isInPipMode && isControllerVisible && error == null,
            enter = fadeIn(AuroraMotion.fastEffects()),
            exit = fadeOut(AuroraMotion.fastEffects()),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isFullscreen) 116.dp else 84.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.84f)),
                            ),
                        ),
                )

                if (!isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (isFullscreen) 32.dp else 20.dp),
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                AuroraHaptics.segmentTick(haptic)
                                seekRelative(-10_000L)
                                showHud(Icons.Default.Replay10, "快退 10 秒")
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = AuroraMediaSurface.copy(alpha = 0.74f),
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "快退 10 秒",
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        FilledIconButton(
                            onClick = {
                                val nextPlaying = !player.isPlaying
                                AuroraHaptics.toggle(haptic, nextPlaying)
                                if (player.isPlaying) {
                                    player.pause()
                                    isPlaying = false
                                } else {
                                    player.play()
                                    isPlaying = true
                                }
                                controlInteractionTick++
                            },
                            shape = if (isPlaying) RoundedCornerShape(20.dp) else CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier.size(if (isFullscreen) 64.dp else 56.dp),
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                modifier = Modifier.size(if (isFullscreen) 32.dp else 28.dp),
                            )
                        }

                        FilledTonalIconButton(
                            onClick = {
                                AuroraHaptics.segmentTick(haptic)
                                seekRelative(10_000L)
                                showHud(Icons.Default.Forward10, "快进 10 秒")
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = AuroraMediaSurface.copy(alpha = 0.74f),
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "快进 10 秒",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                // 底部 Expressive 时间轴与剩余倒计时控制条
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (isFullscreen) 20.dp else 14.dp,
                            vertical = if (isFullscreen) 16.dp else 8.dp,
                        ),
                ) {
                    Text(
                        text = formatPlayerTimecode(currentPositionMs),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        ),
                    )

                    ExpressiveMediaScrubber(
                        currentPositionMs = currentPositionMs,
                        bufferedPositionMs = bufferedPositionMs,
                        durationMs = durationMs,
                        onScrubMove = { previewMs ->
                            isScrubbing = true
                            currentPositionMs = previewMs
                        },
                        onScrubCommit = { targetMs ->
                            isScrubbing = false
                            currentPositionMs = targetMs
                            player.seekTo(targetMs)
                            controlInteractionTick++
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                    )

                    Text(
                        text = if (showRemainingTime) {
                            formatRemainingPlayerTimecode(currentPositionMs, durationMs)
                        } else {
                            formatPlayerTimecode(durationMs)
                        },
                        color = Color.White.copy(alpha = 0.88f),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = Modifier.clickable {
                            AuroraHaptics.segmentTick(haptic)
                            showRemainingTime = !showRemainingTime
                            controlInteractionTick++
                        },
                    )

                    if (isFullscreen) {
                        FilledTonalIconButton(
                            onClick = {
                                AuroraHaptics.tap(haptic)
                                fullscreenChange(false)
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = AuroraMediaSurface.copy(alpha = 0.76f),
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.FullscreenExit,
                                contentDescription = "收起全屏",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }

        // 顶部偏上 Expressive 动态 HUD 胶囊
        AnimatedVisibility(
            visible = !isInPipMode && hudState != null,
            enter = fadeIn(AuroraMotion.fastEffects()) + scaleIn(
                initialScale = 0.92f,
                animationSpec = AuroraMotion.fastSpatial(),
            ),
            exit = fadeOut(AuroraMotion.fastEffects()) + scaleOut(
                targetScale = 0.92f,
                animationSpec = AuroraMotion.fastEffects(),
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (isFullscreen) 68.dp else 56.dp),
        ) {
            hudState?.let { hud ->
                Surface(
                    shape = CircleShape,
                    color = AuroraInkSurface.copy(alpha = 0.88f),
                    contentColor = Color.White,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = hud.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = hud.text,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        if (hud.progress != null) {
                            Box(
                                modifier = Modifier
                                    .width(68.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.24f)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(hud.progress.coerceIn(0f, 1f))
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!isFullscreen && !isInPipMode && !isControllerVisible) {
            PlayerBottomProgressBar(
                currentPositionMs = currentPositionMs,
                bufferedPositionMs = bufferedPositionMs,
                durationMs = durationMs,
                isControllerVisible = isControllerVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun ExpressiveMediaScrubber(
    currentPositionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    onScrubMove: (Long) -> Unit,
    onScrubCommit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var widthPx by remember { mutableIntStateOf(1) }
    var isDragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    val progressFraction = calculateVideoProgressFraction(currentPositionMs, durationMs)
    val bufferedFraction = calculateVideoBufferedFraction(bufferedPositionMs, durationMs)

    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 10.dp else 4.dp,
        animationSpec = AuroraMotion.fastSpatial(),
        label = "scrubber-track-height",
    )
    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 12.dp,
        animationSpec = AuroraMotion.fastSpatial(),
        label = "scrubber-thumb-width",
    )
    val thumbHeight by animateDpAsState(
        targetValue = if (isDragging) 22.dp else 12.dp,
        animationSpec = AuroraMotion.fastSpatial(),
        label = "scrubber-thumb-height",
    )

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .semantics {
                contentDescription = "播放进度"
                progressBarRangeInfo = ProgressBarRangeInfo(progressFraction, 0f..1f)
                setProgress { target ->
                    if (durationMs <= 0L) {
                        false
                    } else {
                        val clamped = target.coerceIn(0f, 1f)
                        onScrubCommit((clamped * durationMs).toLong())
                        true
                    }
                }
            }
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (durationMs <= 0L) return@detectTapGestures
                    AuroraHaptics.segmentTick(haptic)
                    val fraction = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                    onScrubCommit((fraction * durationMs).toLong())
                }
            }
            .pointerInput(durationMs) {
                var lastTargetMs = currentPositionMs
                var lastTickBucket = -1
                detectHorizontalDragGestures(
                    onDragStart = { startOffset ->
                        if (durationMs <= 0L) return@detectHorizontalDragGestures
                        isDragging = true
                        AuroraHaptics.segmentTick(haptic)
                        val fraction = (startOffset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        lastTargetMs = (fraction * durationMs).toLong()
                        lastTickBucket = (fraction * 24).toInt()
                        onScrubMove(lastTargetMs)
                    },
                    onHorizontalDrag = { change, _ ->
                        if (durationMs <= 0L) return@detectHorizontalDragGestures
                        change.consume()
                        val fraction = (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        val bucket = (fraction * 24).toInt()
                        if (bucket != lastTickBucket) {
                            lastTickBucket = bucket
                            AuroraHaptics.scrubTick(haptic)
                        }
                        lastTargetMs = (fraction * durationMs).toLong()
                        onScrubMove(lastTargetMs)
                    },
                    onDragEnd = {
                        if (isDragging) {
                            isDragging = false
                            AuroraHaptics.confirm(haptic)
                            onScrubCommit(lastTargetMs)
                        }
                    },
                    onDragCancel = {
                        if (isDragging) {
                            isDragging = false
                            onScrubCommit(lastTargetMs)
                        }
                    },
                )
            },
    ) {
        // M3 Expressive rounded capsule track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.24f)),
        ) {
            if (bufferedFraction > 0.001f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(bufferedFraction)
                        .background(Color.White.copy(alpha = 0.36f)),
                )
            }
            if (progressFraction > 0.001f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFraction)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }

        // Material 3 vertical pill handle when scrubbing, dot when resting
        val thumbWpx = with(density) { thumbWidth.toPx() }
        val knobOffsetX = ((widthPx * progressFraction) - thumbWpx / 2f)
            .coerceIn(0f, (widthPx - thumbWpx).coerceAtLeast(0f))
        Box(
            modifier = Modifier
                .offset { IntOffset(knobOffsetX.roundToInt(), 0) }
                .size(width = thumbWidth, height = thumbHeight)
                .background(Color.White, CircleShape),
        )

        if (isDragging) {
            val bubbleWidthPx = with(density) { 68.dp.toPx() }
            val bubbleOffsetX = ((widthPx * progressFraction) - bubbleWidthPx / 2f)
                .coerceIn(0f, (widthPx - bubbleWidthPx).coerceAtLeast(0f))
            val bubbleOffsetY = with(density) { (-30).dp.roundToPx() }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.offset { IntOffset(bubbleOffsetX.roundToInt(), bubbleOffsetY) },
            ) {
                Text(
                    text = formatPlayerTimecode(currentPositionMs),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private fun formatPlayerTimecode(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSecs = ms / 1000L
    val h = totalSecs / 3600L
    val m = (totalSecs % 3600L) / 60L
    val s = totalSecs % 60L
    return if (h > 0L) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }
}

private fun formatRemainingPlayerTimecode(currentMs: Long, durationMs: Long): String {
    if (durationMs <= 0L) return "--:--"
    val remainingMs = (durationMs - currentMs).coerceAtLeast(0L)
    return "-${formatPlayerTimecode(remainingMs)}"
}

@Composable
internal fun PlayerBottomProgressBar(
    currentPositionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    isControllerVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val progressFraction = calculateVideoProgressFraction(currentPositionMs, durationMs)
    val bufferedFraction = calculateVideoBufferedFraction(bufferedPositionMs, durationMs)

    val targetHeight = if (isControllerVisible) 2.dp else 3.dp
    val barHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = AuroraMotion.fastSpatial(),
        label = "bottom-progress-height",
    )

    val progressAlpha by animateFloatAsState(
        targetValue = if (isControllerVisible) 0.5f else 1.0f,
        animationSpec = AuroraMotion.fastEffects(),
        label = "bottom-progress-alpha",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .background(Color.White.copy(alpha = 0.16f))
            .testTag("player-bottom-progress-divider"),
    ) {
        if (bufferedFraction > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(bufferedFraction)
                    .background(Color.White.copy(alpha = 0.34f * progressAlpha)),
            )
        }
        if (progressFraction > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progressFraction)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = progressAlpha),
                        shape = RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp),
                    ),
            )
        }
    }
}

internal fun calculateVideoProgressFraction(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L || positionMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

internal fun calculateVideoBufferedFraction(bufferedMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L || bufferedMs <= 0L) return 0f
    return (bufferedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

private const val TAG = "NativeSitePlayer"

internal fun calculateVideoAspectRatio(
    width: Int,
    height: Int,
    pixelWidthHeightRatio: Float = 1f,
    unappliedRotationDegrees: Int = 0,
): Float? {
    if (width <= 0 || height <= 0 || pixelWidthHeightRatio <= 0f) return null
    val isQuarterTurn = Math.floorMod(unappliedRotationDegrees, 180) == 90
    val displayWidth = if (isQuarterTurn) height.toFloat() else width * pixelWidthHeightRatio
    val displayHeight = if (isQuarterTurn) width * pixelWidthHeightRatio else height.toFloat()
    if (displayWidth <= 0f || displayHeight <= 0f) return null
    return displayWidth / displayHeight
}

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
    url.substringBefore('?').lowercase().endsWith(".m3u8") ||
        url.contains(".m3u8", ignoreCase = true) ||
        url.contains("%2Em3u8", ignoreCase = true) ||
        url.contains("/api/hls", ignoreCase = true) -> "hls"
    url.substringBefore('?').lowercase().endsWith(".mp4") -> "mp4"
    else -> "unknown"
}

private fun originOf(referer: String): String = runCatching {
    val uri = java.net.URI(referer)
    "${uri.scheme}://${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}"
}.getOrDefault(referer)

private tailrec fun Context.findMainActivity(): MainActivity? = when (this) {
    is MainActivity -> this
    is ContextWrapper -> baseContext.findMainActivity()
    else -> null
}

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
