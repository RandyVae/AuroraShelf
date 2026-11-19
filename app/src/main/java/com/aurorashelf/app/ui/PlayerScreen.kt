@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.aurorashelf.app.ui

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurorashelf.app.cast.CastDevice
import com.aurorashelf.app.cast.CastManager
import com.aurorashelf.app.cast.CastState
import com.aurorashelf.app.data.OfflineCacheManager
import com.aurorashelf.app.data.OfflineCacheState
import com.aurorashelf.app.data.OfflineVideo
import com.aurorashelf.app.data.PREFETCH_LOG_TAG
import com.aurorashelf.app.data.PlaybackEpisode
import com.aurorashelf.app.data.PlaybackResolver
import com.aurorashelf.app.data.PlaybackSource
import com.aurorashelf.app.data.isPrefetchEnabled
import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.VideoDetailMetadata
import com.aurorashelf.app.model.VideoItem
import com.aurorashelf.app.ui.theme.AuroraHaptics
import com.aurorashelf.app.ui.theme.AuroraImmersiveBackground
import com.aurorashelf.app.ui.theme.AuroraMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun PlayerScreen(
    video: VideoItem,
    isFavorite: Boolean,
    isFullscreen: Boolean,
    relatedVideos: List<VideoItem>,
    favoriteIds: Set<String>,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onFullscreenChange: (Boolean) -> Unit,
    onVideoOrientationChange: (Boolean) -> Unit,
    onVideo: (VideoItem) -> Unit,
    onRelatedFavorite: (VideoItem) -> Unit,
    isDismissing: Boolean = false,
) {
    val context = LocalContext.current
    val cacheManager = remember(context) { OfflineCacheManager.get(context) }
    val cachedVideos by cacheManager.entries.collectAsStateWithLifecycle()
    val castManager = remember(context) { CastManager.getInstance(context) }
    val castState by castManager.castState.collectAsStateWithLifecycle()
    var isCastSheetVisible by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isCasting = castState is CastState.Connected

    var playbackUrl by remember(video.id) { mutableStateOf(video.pageUrl) }
    var localDismissing by remember(playbackUrl) { mutableStateOf(false) }
    val suppressPlayback = isDismissing || localDismissing
    var videoAspectRatio by remember(playbackUrl) { mutableStateOf<Float?>(null) }
    var currentPositionMs by rememberSaveable(playbackUrl) { mutableLongStateOf(0L) }
    var currentDurationMs by rememberSaveable(playbackUrl) { mutableLongStateOf(0L) }
    var isPlayerControllerVisible by remember(playbackUrl) { mutableStateOf(true) }
    var isEpisodePickerVisible by remember(video.id) { mutableStateOf(false) }
    // Flow marker for logcat: the gap from this line to "timing ready" is the tap -> READY time.
    LaunchedEffect(video.id) { Log.i(TAG, "timing player screen opened") }
    val episodes by produceState<List<PlaybackEpisode>>(initialValue = emptyList(), key1 = video.pageUrl) {
        val startedAtMs = System.currentTimeMillis()
        value = runCatching { PlaybackResolver().resolveEpisodes(video.pageUrl) }.getOrDefault(emptyList())
        Log.i(TAG, "timing episodes from player screen ms=${System.currentTimeMillis() - startedAtMs} count=${value.size}")
    }
    val detailMetadata by produceState<VideoDetailMetadata?>(initialValue = null, key1 = video.pageUrl) {
        value = runCatching { PlaybackResolver().resolveDetails(video) }.getOrNull()
    }
    val selectedEpisode = episodes.firstOrNull { it.pageUrl == playbackUrl }?.number ?: 1
    val selectedEpisodeLabel = episodes.firstOrNull { it.pageUrl == playbackUrl }?.label
    val cachedVideo = cachedVideos.firstOrNull { it.id == OfflineCacheManager.idFor(playbackUrl) }

    val isInPipMode = com.aurorashelf.app.LocalIsInPipMode.current
    val launchCastForUrl: (CastDevice, String, String?, Long) -> Unit = { device, targetPageUrl, epLabel, resumePos ->
        scope.launch(Dispatchers.IO) {
            val targetCached = cachedVideos.firstOrNull {
                it.id == OfflineCacheManager.idFor(targetPageUrl) && it.isPlayableOffline
            }
            val resolved = runCatching { PlaybackResolver().resolve(targetPageUrl) }.getOrNull()
            val source = if (targetCached != null && targetCached.mediaUrl != null) {
                PlaybackSource(
                    url = targetCached.mediaUrl,
                    referer = targetCached.referer?.ifBlank { resolved?.referer ?: targetPageUrl } ?: (resolved?.referer ?: targetPageUrl),
                    extraHeaders = targetCached.extraHeaders.ifEmpty { resolved?.extraHeaders.orEmpty() },
                )
            } else {
                resolved
            }
            if (source != null) {
                val isLocalFile = source.url.startsWith("file://", ignoreCase = true) ||
                    source.url.startsWith("/")
                castManager.cast(
                    device = device,
                    videoUrl = source.url,
                    title = if (epLabel.isNullOrBlank()) video.title else "${video.title} · $epLabel",
                    referer = source.referer,
                    extraHeaders = source.extraHeaders,
                    localFilePath = if (isLocalFile) source.url else null,
                    cacheKey = targetCached?.id,
                    initialPositionMs = resumePos,
                )
            }
        }
    }
    val nextEpisode = episodes.firstOrNull { it.number > selectedEpisode }
    // Immediate pre-warm for next Huavod episode without waiting for full episode list parsing
    LaunchedEffect(playbackUrl) {
        if (ContentSourceCatalog.isHuavod(playbackUrl)) {
            val epParam = PlaybackResolver().extractHuavodEpisodeParam(playbackUrl)
            if (epParam != null) {
                val parts = epParam.split('-')
                if (parts.size >= 3) {
                    val nextNid = (parts[2].toIntOrNull() ?: 1) + 1
                    val nextEpUrl = "https://huavod.com/vodplay/${parts[0]}-${parts[1]}-$nextNid.html"
                    PlaybackResolver().prewarmHuavod(nextEpUrl)
                }
            }
        }
    }
    // 针对多集短剧开启持续向后滑动窗口预缓存系统（预热后续 1~2 集播放流）
    LaunchedEffect(video.id, selectedEpisode, episodes) {
        val curNum = selectedEpisode
        val appContext = context.applicationContext
        if (!isPrefetchEnabled(appContext)) return@LaunchedEffect
        delay(NEXT_EPISODE_PREFETCH_DELAY_MS)
        // Re-check after the delay: the network may have switched to mobile data meanwhile.
        if (!isPrefetchEnabled(appContext)) return@LaunchedEffect
        Log.i(PREFETCH_LOG_TAG, "sliding window prefetch start for ep=$curNum")
        PlaybackResolver().prefetchSlidingWindow(episodes, curNum, windowSize = 2)
        Log.i(PREFETCH_LOG_TAG, "sliding window prefetch done")
    }
    // task-17 shared warm-up: dedupes, bounds parallelism and applies the same Wi-Fi policy.
    LaunchedEffect(relatedVideos) {
        CoverPrefetcher.prefetchVideos(context, relatedVideos, limit = RECOMMENDATION_COVER_PREFETCH_LIMIT)
    }
    val haptic = LocalHapticFeedback.current
    val flingCollapseSpec = AuroraMotion.fastSpatial<Float>()

    val onCache = {
        AuroraHaptics.tap(haptic)
        when (cachedVideo?.state) {
            OfflineCacheState.RESOLVING,
            OfflineCacheState.QUEUED,
            OfflineCacheState.DOWNLOADING -> cacheManager.remove(cachedVideo.id)
            OfflineCacheState.FAILED -> cacheManager.enqueue(video, playbackUrl, selectedEpisodeLabel)
            OfflineCacheState.COMPLETED,
            OfflineCacheState.REMOVING -> Unit
            null -> cacheManager.enqueue(video, playbackUrl, selectedEpisodeLabel)
        }
    }

    Surface(
        color = if (isFullscreen || isInPipMode) AuroraImmersiveBackground else MaterialTheme.colorScheme.background,
        contentColor = if (isFullscreen || isInPipMode) Color.White else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenWidth = maxWidth
            val screenHeight = maxHeight
            val density = LocalDensity.current
            val listState = rememberLazyListState()
            val isTwoPaneLandscape = shouldUseTwoPaneLandscapeLayout(
                width = screenWidth,
                height = screenHeight,
                isFullscreen = isFullscreen || isInPipMode,
            )
            val leftPaneWidthFraction = if (isTwoPaneLandscape) 0.62f else 1f
            val effectivePaneWidth = screenWidth * leftPaneWidthFraction

            val targetPlayerHeight = remember(videoAspectRatio, effectivePaneWidth, screenHeight) {
                calculateAdaptivePlayerHeight(
                    aspectRatio = videoAspectRatio,
                    containerWidth = effectivePaneWidth,
                    containerHeight = screenHeight,
                )
            }
            val animatedPlayerHeight by animateDpAsState(
                targetValue = targetPlayerHeight,
                animationSpec = AuroraMotion.defaultSpatial(),
                label = "player-height-expansion",
            )

            val collapsedHeight = remember(effectivePaneWidth, animatedPlayerHeight) {
                minOf(animatedPlayerHeight, effectivePaneWidth * (9f / 16f))
            }
            val expandedHeight = animatedPlayerHeight
            val maxCollapsePx = with(density) {
                (expandedHeight - collapsedHeight).coerceAtLeast(0.dp).toPx()
            }

            var collapseOffsetPx by remember(playbackUrl) { mutableFloatStateOf(0f) }

            // Whenever maxCollapsePx changes (e.g. video orientation changes), keep offset within valid bounds
            LaunchedEffect(maxCollapsePx) {
                if (collapseOffsetPx > maxCollapsePx) {
                    collapseOffsetPx = maxCollapsePx
                }
            }

            // When new video loads, reset collapse offset and scroll back to top
            LaunchedEffect(playbackUrl) {
                collapseOffsetPx = 0f
                listState.scrollToItem(0)
            }

            val nestedScrollConnection = remember(maxCollapsePx, flingCollapseSpec) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (available.y < 0f && maxCollapsePx > 0.5f && collapseOffsetPx < maxCollapsePx) {
                            val newOffset = (collapseOffsetPx - available.y).coerceAtMost(maxCollapsePx)
                            val consumedY = -(newOffset - collapseOffsetPx)
                            collapseOffsetPx = newOffset
                            return Offset(0f, consumedY)
                        }
                        return Offset.Zero
                    }

                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        if (available.y > 0f && maxCollapsePx > 0.5f && collapseOffsetPx > 0f) {
                            val newOffset = (collapseOffsetPx - available.y).coerceAtLeast(0f)
                            val consumedY = collapseOffsetPx - newOffset
                            collapseOffsetPx = newOffset
                            return Offset(0f, consumedY)
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (maxCollapsePx > 0.5f && collapseOffsetPx > 0f && collapseOffsetPx < maxCollapsePx) {
                            val target = if (available.y < -300f || collapseOffsetPx > maxCollapsePx * 0.45f) {
                                maxCollapsePx
                            } else {
                                0f
                            }
                            if (available.y < 0f && target == maxCollapsePx) {
                                scope.launch {
                                    animate(
                                        initialValue = collapseOffsetPx,
                                        targetValue = target,
                                        initialVelocity = -available.y,
                                        animationSpec = flingCollapseSpec,
                                    ) { value, _ ->
                                        collapseOffsetPx = value
                                    }
                                }
                                return Velocity.Zero
                            }
                            animate(
                                initialValue = collapseOffsetPx,
                                targetValue = target,
                                initialVelocity = -available.y,
                                animationSpec = flingCollapseSpec,
                            ) { value, _ ->
                                collapseOffsetPx = value
                            }
                            return Velocity(0f, available.y)
                        }
                        return Velocity.Zero
                    }

                    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                        if (maxCollapsePx > 0.5f && available.y > 300f && collapseOffsetPx > 0f) {
                            animate(
                                initialValue = collapseOffsetPx,
                                targetValue = 0f,
                                initialVelocity = -available.y,
                                animationSpec = flingCollapseSpec,
                            ) { value, _ ->
                                collapseOffsetPx = value
                            }
                            return Velocity(0f, available.y)
                        }
                        return Velocity.Zero
                    }
                }
            }

            val currentCollapseDp = with(density) { collapseOffsetPx.toDp() }
            val effectivePlayerHeight = calculateEffectivePlayerHeight(
                expandedHeight = expandedHeight,
                collapsedHeight = collapsedHeight,
                collapseOffsetDp = currentCollapseDp,
            )

            val currentPlayerHeight = effectivePlayerHeight
            val handleEpisodeSelect: (PlaybackEpisode) -> Unit = { episode ->
                AuroraHaptics.segmentTick(haptic)
                playbackUrl = episode.pageUrl
                currentPositionMs = 0L
                currentDurationMs = 0L
                isEpisodePickerVisible = false
                val activeDevice = (castState as? CastState.Connected)?.device
                if (activeDevice != null) {
                    launchCastForUrl(activeDevice, episode.pageUrl, episode.label, 0L)
                }
            }
            val handlePlaybackEnded: () -> Unit = {
                val curNum = selectedEpisode
                val nextEp = episodes.firstOrNull { it.number == curNum + 1 }
                if (nextEp != null) {
                    handleEpisodeSelect(nextEp)
                }
            }
            val topBarBackButton: @Composable () -> Unit = {
                FilledIconButton(
                    onClick = {
                        AuroraHaptics.tap(haptic)
                        localDismissing = true
                        onBack()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.56f),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "返回")
                }
            }
            val topBarRightControls: @Composable () -> Unit = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledIconButton(
                        onClick = {
                            AuroraHaptics.tap(haptic)
                            isCastSheetVisible = true
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isCasting) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.56f),
                            contentColor = if (isCasting) MaterialTheme.colorScheme.onPrimary else Color.White,
                        ),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = if (isCasting) Icons.Filled.CastConnected else Icons.Outlined.Cast,
                            contentDescription = if (isCasting) "投屏中" else "投屏",
                        )
                    }
                    FilledIconButton(
                        onClick = {
                            AuroraHaptics.tap(haptic)
                            onFullscreenChange(true)
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.56f),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Default.Fullscreen,
                            contentDescription = "全屏播放",
                        )
                    }
                }
            }

            if (!isFullscreen && !isInPipMode) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(leftPaneWidthFraction)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .nestedScroll(nestedScrollConnection)
                        .testTag("player-details"),
                ) {
                    item(key = "player-space") {
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .height(currentPlayerHeight),
                        )
                    }
                    item(key = "player-info") {
                        PlayerDetails(
                            video = video,
                            isFavorite = isFavorite,
                            cachedVideo = cachedVideo,
                            episodes = episodes,
                            detailMetadata = detailMetadata,
                            selectedEpisode = selectedEpisode,
                            currentEpisodeProgress = calculateVideoProgressFraction(currentPositionMs, currentDurationMs),
                            isEpisodePickerVisible = isEpisodePickerVisible,
                            isCasting = isCasting,
                            onFavorite = {
                                AuroraHaptics.toggle(haptic, !isFavorite)
                                onFavorite()
                            },
                            onCache = onCache,
                            onCast = {
                                AuroraHaptics.tap(haptic)
                                isCastSheetVisible = true
                            },
                            onToggleEpisodes = {
                                AuroraHaptics.tap(haptic)
                                isEpisodePickerVisible = !isEpisodePickerVisible
                            },
                            onEpisode = handleEpisodeSelect,
                        )
                    }
                    if (!isTwoPaneLandscape && relatedVideos.isNotEmpty()) {
                        item(key = "recommendation-title") {
                            Text(
                                text = "接着观看",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier
                                    .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 10.dp)
                                    .semantics { heading() },
                            )
                        }
                        items(relatedVideos, key = { "related-${it.id}" }) { related ->
                            PlayerRecommendationRow(
                                video = related,
                                isFavorite = related.id in favoriteIds,
                                onClick = { onVideo(related) },
                                onFavorite = { onRelatedFavorite(related) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }

                if (isTwoPaneLandscape) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .fillMaxWidth(0.38f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.background)
                            .statusBarsPadding(),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                        LazyColumn(
                            contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .navigationBarsPadding()
                                .testTag("player-right-rail"),
                        ) {
                            if (relatedVideos.isNotEmpty()) {
                                item(key = "recommendation-title") {
                                    Text(
                                        text = "接着观看",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier
                                            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 10.dp)
                                            .semantics { heading() },
                                    )
                                }
                                items(relatedVideos, key = { "related-${it.id}" }) { related ->
                                    PlayerRecommendationRow(
                                        video = related,
                                        isFavorite = related.id in favoriteIds,
                                        onClick = { onVideo(related) },
                                        onFavorite = { onRelatedFavorite(related) },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(leftPaneWidthFraction)
                        .background(AuroraImmersiveBackground)
                        .statusBarsPadding(),
                )
            }

            // Single persistent player container across portrait, tablet landscape split, PiP, and fullscreen
            // so rotation/PiP never tears down or recreates ExoPlayer.
            NativeSitePlayer(
                pageUrl = playbackUrl,
                isFullscreen = isFullscreen,
                onFullscreenChange = onFullscreenChange,
                onVideoOrientationChange = onVideoOrientationChange,
                onVideoAspectRatioChange = { videoAspectRatio = it },
                onPositionChange = { currentPositionMs = it },
                onDurationChange = { currentDurationMs = it },
                onControllerVisibilityChange = { isPlayerControllerVisible = it },
                onPlaybackEnded = handlePlaybackEnded,
                keepTopBarVisible = !isFullscreen && !isInPipMode && isCasting,
                suppressPlayback = suppressPlayback,
                topBarStart = topBarBackButton,
                topBarEnd = topBarRightControls,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .then(
                        if (isFullscreen || isInPipMode) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .statusBarsPadding()
                                .fillMaxWidth(leftPaneWidthFraction)
                                .height(currentPlayerHeight)
                        },
                    )
                    .testTag("native-player-container"),
            )

            if (!isInPipMode) {
                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(leftPaneWidthFraction)
                        .statusBarsPadding()
                        .padding(
                            top = 60.dp,
                            start = if (isTwoPaneLandscape) 16.dp else 20.dp,
                            end = if (isTwoPaneLandscape) 16.dp else 20.dp,
                        ),
                ) {
                    FloatingCastStatusBanner(
                        visible = isCasting,
                        connected = castState as? CastState.Connected,
                        castManager = castManager,
                        onOpenSheet = { isCastSheetVisible = true },
                    )
                }
            }

            if (isCastSheetVisible && !isInPipMode) {
                CastDeviceSheet(
                    castManager = castManager,
                    onDismissRequest = { isCastSheetVisible = false },
                    onDeviceSelected = { device ->
                        isCastSheetVisible = false
                        launchCastForUrl(device, playbackUrl, selectedEpisodeLabel, currentPositionMs)
                    },
                )
            }
        }
    }
}

@Composable
private fun FloatingCastStatusBanner(
    visible: Boolean,
    connected: CastState.Connected?,
    castManager: CastManager,
    onOpenSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    AnimatedVisibility(
        visible = visible,
        enter = AuroraMotion.expressiveBarEnter(),
        exit = AuroraMotion.expressiveBarExit(),
        modifier = modifier,
    ) {
        if (connected != null) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.94f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = CircleShape,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        AuroraHaptics.tap(haptic)
                        onOpenSheet()
                    },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CastConnected,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.inversePrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "正在投屏至 ${connected.device.name}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            AuroraHaptics.tap(haptic)
                            if (connected.isPlaying) castManager.pause() else castManager.play()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(36.dp),
                    ) {
                        Icon(
                            imageVector = if (connected.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (connected.isPlaying) "暂停电视" else "播放",
                            tint = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            AuroraHaptics.tap(haptic)
                            castManager.disconnect()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "断开投屏",
                            tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.78f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerDetails(
    video: VideoItem,
    isFavorite: Boolean,
    cachedVideo: OfflineVideo?,
    episodes: List<PlaybackEpisode>,
    detailMetadata: VideoDetailMetadata?,
    selectedEpisode: Int,
    currentEpisodeProgress: Float,
    isEpisodePickerVisible: Boolean,
    isCasting: Boolean,
    onFavorite: () -> Unit,
    onCache: () -> Unit,
    onCast: () -> Unit,
    onToggleEpisodes: () -> Unit,
    onEpisode: (PlaybackEpisode) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var isIntroExpanded by remember(video.id) { mutableStateOf(false) }
    val cacheStateDescription = when (cachedVideo?.state) {
        OfflineCacheState.RESOLVING -> "正在解析视频地址"
        OfflineCacheState.QUEUED -> "已加入缓存队列"
        OfflineCacheState.DOWNLOADING -> "正在缓存视频"
        OfflineCacheState.COMPLETED -> "视频已缓存到本地"
        OfflineCacheState.FAILED -> "缓存失败，可点击重试"
        OfflineCacheState.REMOVING -> "正在删除离线缓存"
        null -> null
    }
    val cacheActionDescription = when (cachedVideo?.state) {
        OfflineCacheState.RESOLVING,
        OfflineCacheState.QUEUED,
        OfflineCacheState.DOWNLOADING -> "缓存中"
        OfflineCacheState.COMPLETED -> "已缓存"
        OfflineCacheState.FAILED -> "重试"
        OfflineCacheState.REMOVING -> "删除中"
        null -> "缓存"
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 18.dp, vertical = 18.dp)
            .animateContentSize(animationSpec = AuroraMotion.defaultSpatial()),
    ) {
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = listOf(video.author, video.views)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (detailMetadata != null) {
            val pills = buildList {
                detailMetadata.year.takeIf { it.isNotBlank() }?.let(::add)
                detailMetadata.area.takeIf { it.isNotBlank() }?.let(::add)
                addAll(detailMetadata.tags.filter { it.isNotBlank() })
            }.distinct()
            if (pills.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    pills.take(10).forEach { badge ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            PlayerActionButton(
                label = if (isFavorite) "已收藏" else "收藏",
                selected = isFavorite,
                onClick = onFavorite,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                )
            }
            PlayerActionButton(
                label = cacheActionDescription,
                selected = cachedVideo?.state == OfflineCacheState.COMPLETED,
                stateDescriptionText = cacheStateDescription,
                onClick = onCache,
                enabled = cachedVideo?.state != OfflineCacheState.REMOVING,
                modifier = Modifier.weight(1f),
            ) {
                when (cachedVideo?.state) {
                    OfflineCacheState.RESOLVING,
                    OfflineCacheState.QUEUED,
                    OfflineCacheState.DOWNLOADING,
                    OfflineCacheState.REMOVING -> AuroraLoadingIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OfflineCacheState.COMPLETED -> Icon(
                        Icons.Default.DownloadDone,
                        contentDescription = null,
                    )
                    OfflineCacheState.FAILED -> Icon(Icons.Default.Refresh, contentDescription = null)
                    null -> Icon(Icons.Default.Download, contentDescription = null)
                }
            }
            PlayerActionButton(
                label = if (isCasting) "投屏中" else "投屏",
                selected = isCasting,
                onClick = onCast,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = if (isCasting) Icons.Filled.CastConnected else Icons.Outlined.Cast,
                    contentDescription = null,
                )
            }
            if (episodes.size > 1) {
                PlayerActionButton(
                    label = "选集 ${selectedEpisode}/${episodes.size}",
                    selected = isEpisodePickerVisible,
                    onClick = onToggleEpisodes,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.FormatListNumbered, contentDescription = null)
                }
            }
        }
        AnimatedVisibility(
            visible = episodes.size > 1 && isEpisodePickerVisible,
            enter = AuroraMotion.expressiveOverlayEnter(),
            exit = AuroraMotion.expressiveOverlayExit(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
            ) {
                episodes.forEach { episode ->
                    val selected = episode.number == selectedEpisode
                    Column(
                        modifier = Modifier
                            .width(180.dp)
                            .clickable(role = Role.Button, onClick = { onEpisode(episode) }),
                    ) {
                        Card(
                            shape = MaterialTheme.shapes.small,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .width(180.dp)
                                .height(101.dp),
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                MediaImage(
                                    video = video,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp),
                                ) {
                                    Text(
                                        text = "EP ${episode.number}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                if (selected) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(34.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.PlayArrow,
                                                contentDescription = "正在播放",
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                    AuroraWavyProgressBar(
                                        progress = { currentEpisodeProgress.coerceIn(0.05f, 1f) },
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = Color.White.copy(alpha = 0.30f),
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .height(4.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = episode.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            ),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (selected) "正在播放" else "极速直连",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (detailMetadata != null && (detailMetadata.directors.isNotEmpty() || detailMetadata.actors.isNotEmpty())) {
            val crewEntries = buildList {
                detailMetadata.directors.forEach { add("导演" to it) }
                detailMetadata.actors.forEach { add("主演" to it) }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player-cast-section"),
            ) {
                Text(
                    text = "演员表",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(crewEntries, key = { "${it.first}-${it.second}" }) { (role, name) ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (role == "导演") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = "$name · $role",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        val synopsis = detailMetadata?.synopsis.orEmpty().trim()
        if (synopsis.isNotBlank()) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player-intro-section"),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(14.dp),
                ) {
                    Text(
                        text = "基本介绍",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = synopsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp,
                        maxLines = if (isIntroExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (synopsis.length > 72) {
                        Text(
                            text = if (isIntroExpanded) "收起" else "展开全文",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clickable {
                                    AuroraHaptics.tap(haptic)
                                    isIntroExpanded = !isIntroExpanded
                                },
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun PlayerActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    stateDescriptionText: String? = null,
    icon: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh
    val contentColor = (if (selected) scheme.onPrimaryContainer else scheme.onSurface).let { base ->
        if (enabled) base else base.copy(alpha = 0.38f)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .height(68.dp)
            .then(
                if (stateDescriptionText != null) {
                    Modifier.semantics { stateDescription = stateDescriptionText }
                } else {
                    Modifier
                },
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(24.dp),
            ) {
                icon()
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                ),
                color = LocalContentColor.current,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlayerRecommendationRow(
    video: VideoItem,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                AuroraHaptics.tap(haptic)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag("player-recommendation"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.43f)
                .aspectRatio(16 / 9f)
                .clip(MaterialTheme.shapes.medium),
        ) {
            MediaImage(video = video, modifier = Modifier.fillMaxSize())
            if (video.duration.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.74f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp),
                ) {
                    Text(
                        text = video.duration,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            val cleanAuthor = video.author.trim()
            val cleanViews = video.views.trim().let { viewLabel ->
                val bareSource = viewLabel.removePrefix("来源:").removePrefix("来源：").trim()
                if (cleanAuthor.isNotBlank() && (viewLabel == cleanAuthor || bareSource == cleanAuthor)) {
                    ""
                } else {
                    viewLabel
                }
            }
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (cleanAuthor.isNotBlank()) {
                Text(
                    text = cleanAuthor,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (cleanViews.isNotBlank()) {
                Text(
                    text = cleanViews,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            onClick = {
                AuroraHaptics.toggle(haptic, !isFavorite)
                onFavorite()
            },
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .offset(x = 8.dp, y = (-8).dp),
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (isFavorite) "取消收藏" else "收藏视频",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun shouldUseTwoPaneLandscapeLayout(
    width: Dp,
    height: Dp,
    isFullscreen: Boolean,
): Boolean = !isFullscreen && width >= 700.dp && width > height

internal fun relatedVideos(current: VideoItem, videos: List<VideoItem>): List<VideoItem> =
    videos.distinctBy(VideoItem::id).filterNot { it.id == current.id }

internal fun calculateAdaptivePlayerHeight(
    aspectRatio: Float?,
    containerWidth: Dp,
    containerHeight: Dp,
    maxPortraitHeightRatio: Float = 0.60f,
): Dp {
    if (containerWidth <= 0.dp || containerHeight <= 0.dp) {
        return 220.dp
    }
    val isViewportPortrait = containerHeight >= containerWidth
    val maxAllowedHeight = if (isViewportPortrait) {
        containerHeight * maxPortraitHeightRatio
    } else {
        containerHeight * 0.70f
    }

    val standard16x9Height = containerWidth * (9f / 16f)
    if (aspectRatio == null || aspectRatio <= 0f) {
        return standard16x9Height.coerceAtMost(maxAllowedHeight)
    }

    // Floating-point tolerance around 16:9 to guarantee exact match with standard16x9Height
    if (kotlin.math.abs(aspectRatio - (16f / 9f)) < 0.01f) {
        return standard16x9Height.coerceAtMost(maxAllowedHeight)
    }

    val naturalHeight = containerWidth / aspectRatio
    return naturalHeight.coerceIn(160.dp, maxAllowedHeight)
}

internal fun calculateEffectivePlayerHeight(
    expandedHeight: Dp,
    collapsedHeight: Dp,
    collapseOffsetDp: Dp,
): Dp {
    val minH = minOf(expandedHeight, collapsedHeight)
    val maxH = maxOf(expandedHeight, collapsedHeight)
    if (minH >= maxH) {
        return expandedHeight
    }
    return (expandedHeight - collapseOffsetDp).coerceIn(minH, maxH)
}

private const val TAG = "PlayerScreen"

/** Wait for playback to settle before spending network on the next episode. */
private const val NEXT_EPISODE_PREFETCH_DELAY_MS = 2_000L

/** Recommendation covers warmed through the shared task-17 CoverPrefetcher. */
private const val RECOMMENDATION_COVER_PREFETCH_LIMIT = 6
