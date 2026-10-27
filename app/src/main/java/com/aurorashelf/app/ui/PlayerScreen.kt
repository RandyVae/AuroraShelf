package com.aurorashelf.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import com.aurorashelf.app.model.AppDestination
import com.aurorashelf.app.model.ContentSource
import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.ComicSourceInfo
import com.aurorashelf.app.model.FeedCategory
import com.aurorashelf.app.model.VideoItem
import com.aurorashelf.app.data.SourceAddress
import com.aurorashelf.app.data.PlaybackEpisode
import com.aurorashelf.app.data.PREFETCH_LOG_TAG
import com.aurorashelf.app.data.PlaybackResolver
import com.aurorashelf.app.data.PlaybackSource
import com.aurorashelf.app.data.isPrefetchEnabled
import com.aurorashelf.app.data.OfflineCacheManager
import com.aurorashelf.app.data.OfflineCacheState
import com.aurorashelf.app.data.OfflineVideo
import com.aurorashelf.app.cast.CastDevice
import com.aurorashelf.app.cast.CastManager
import com.aurorashelf.app.cast.CastState
import com.aurorashelf.app.ui.liquid.LiquidBottomNavigation
import com.aurorashelf.app.ui.liquid.LiquidNavigationTab
import com.aurorashelf.app.ui.theme.AuroraCoral
import com.aurorashelf.app.ui.theme.AuroraImmersiveBackground
import com.aurorashelf.app.ui.theme.AuroraMediaSurfaceVariant
import com.aurorashelf.app.ui.theme.AuroraMediaLabel
import com.aurorashelf.app.ui.theme.AuroraMediaMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

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
    var videoAspectRatio by remember(playbackUrl) { mutableStateOf<Float?>(null) }
    var currentPositionMs by rememberSaveable(playbackUrl) { mutableStateOf(0L) }
    var isEpisodePickerVisible by remember(video.id) { mutableStateOf(false) }
    // Flow marker for logcat: the gap from this line to "timing ready" is the tap -> READY time.
    LaunchedEffect(video.id) { Log.i(TAG, "timing player screen opened") }
    val episodes by produceState<List<PlaybackEpisode>>(initialValue = emptyList(), key1 = video.pageUrl) {
        val startedAtMs = System.currentTimeMillis()
        value = runCatching { PlaybackResolver().resolveEpisodes(video.pageUrl) }.getOrDefault(emptyList())
        Log.i(TAG, "timing episodes from player screen ms=${System.currentTimeMillis() - startedAtMs} count=${value.size}")
    }
    val selectedEpisode = episodes.firstOrNull { it.pageUrl == playbackUrl }?.number ?: 1
    val selectedEpisodeLabel = episodes.firstOrNull { it.pageUrl == playbackUrl }?.label
    val cachedVideo = cachedVideos.firstOrNull { it.id == OfflineCacheManager.idFor(playbackUrl) }

    val launchCastForUrl: (CastDevice, String, String?, Long) -> Unit = { device, targetPageUrl, epLabel, resumePos ->
        scope.launch(Dispatchers.IO) {
            val targetCached = cachedVideos.firstOrNull {
                it.id == OfflineCacheManager.idFor(targetPageUrl) && it.isPlayableOffline
            }
            val resolved = runCatching { PlaybackResolver().resolve(targetPageUrl) }.getOrNull()
            val source = if (targetCached != null && targetCached.mediaUrl != null) {
                PlaybackSource(targetCached.mediaUrl, resolved?.referer ?: targetPageUrl)
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
    // Warm the next episode once per selection: switching episodes changes the key and re-runs it.
    LaunchedEffect(video.id, nextEpisode?.pageUrl) {
        val next = nextEpisode ?: return@LaunchedEffect
        val appContext = context.applicationContext
        if (!isPrefetchEnabled(appContext)) return@LaunchedEffect
        delay(NEXT_EPISODE_PREFETCH_DELAY_MS)
        // Re-check after the delay: the network may have switched to mobile data meanwhile.
        if (!isPrefetchEnabled(appContext)) return@LaunchedEffect
        Log.i(PREFETCH_LOG_TAG, "next episode start")
        PlaybackResolver().prefetchPlayback(next.pageUrl)
        Log.i(PREFETCH_LOG_TAG, "next episode done")
    }
    // task-17 shared warm-up: dedupes, bounds parallelism and applies the same Wi-Fi policy.
    LaunchedEffect(relatedVideos) {
        CoverPrefetcher.prefetchVideos(context, relatedVideos, limit = RECOMMENDATION_COVER_PREFETCH_LIMIT)
    }
    val haptic = LocalHapticFeedback.current

    val onCache = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
        color = if (isFullscreen) Color.Black else AuroraImmersiveBackground,
        contentColor = Color.White,
        modifier = Modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenWidth = maxWidth
            val screenHeight = maxHeight
            val density = LocalDensity.current
            val listState = rememberLazyListState()

            val targetPlayerHeight = remember(videoAspectRatio, screenWidth, screenHeight) {
                calculateAdaptivePlayerHeight(
                    aspectRatio = videoAspectRatio,
                    containerWidth = screenWidth,
                    containerHeight = screenHeight,
                )
            }
            val animatedPlayerHeight by animateDpAsState(
                targetValue = targetPlayerHeight,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = 300f,
                ),
                label = "player-height-expansion",
            )

            val collapsedHeight = remember(screenWidth, animatedPlayerHeight) {
                minOf(animatedPlayerHeight, screenWidth * (9f / 16f))
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

            val nestedScrollConnection = remember(maxCollapsePx) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        // When scrolling UP (available.y < 0, dragging content upwards):
                        // Collapse the player height down to collapsedHeight (16:9) first,
                        // before the LazyColumn scrolls its content!
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
                        // When scrolling DOWN (available.y > 0, dragging content downwards):
                        // After the LazyColumn has scrolled back to its top item (firstVisibleItem == 0),
                        // any unconsumed downward scroll expands the player back towards expandedHeight!
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
                            animate(
                                initialValue = collapseOffsetPx,
                                targetValue = target,
                                initialVelocity = -available.y,
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 320f),
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
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 320f),
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

            if (!isFullscreen) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .nestedScroll(nestedScrollConnection)
                        .testTag("player-details"),
                ) {
                    item(key = "player-space") {
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .height(effectivePlayerHeight)
                        )
                    }
                    item(key = "player-info") {
                        PlayerDetails(
                            video = video,
                            isFavorite = isFavorite,
                            cachedVideo = cachedVideo,
                            episodes = episodes,
                            selectedEpisode = selectedEpisode,
                            isEpisodePickerVisible = isEpisodePickerVisible,
                            isCasting = isCasting,
                            onFavorite = onFavorite,
                            onCache = onCache,
                            onCast = { isCastSheetVisible = true },
                            onToggleEpisodes = { isEpisodePickerVisible = !isEpisodePickerVisible },
                            onEpisode = { episode ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                playbackUrl = episode.pageUrl
                                currentPositionMs = 0L
                                isEpisodePickerVisible = false
                                val activeDevice = (castState as? CastState.Connected)?.device
                                if (activeDevice != null) {
                                    launchCastForUrl(activeDevice, episode.pageUrl, episode.label, 0L)
                                }
                            },
                        )
                    }
                    if (relatedVideos.isNotEmpty()) {
                        item(key = "recommendation-title") {
                            Text(
                                text = "接着观看",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
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
                            )
                        }
                    }
                }
            }

            NativeSitePlayer(
                pageUrl = playbackUrl,
                isFullscreen = isFullscreen,
                onFullscreenChange = onFullscreenChange,
                onVideoOrientationChange = onVideoOrientationChange,
                onVideoAspectRatioChange = { videoAspectRatio = it },
                onPositionChange = { currentPositionMs = it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .then(
                        if (isFullscreen) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier
                                .statusBarsPadding()
                                .fillMaxWidth()
                                .height(effectivePlayerHeight)
                        },
                    )
                    .testTag("native-player-container"),
            )

            if (!isFullscreen) {
                GlassSurface(
                    shape = CircleShape,
                    forceDark = true,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(10.dp)
                        .size(44.dp)
                        .clickable(onClick = onBack),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "返回")
                    }
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GlassSurface(
                        shape = CircleShape,
                        forceDark = true,
                        modifier = Modifier
                            .size(44.dp)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isCastSheetVisible = true
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = if (isCasting) Icons.Filled.CastConnected else Icons.Outlined.Cast,
                                contentDescription = if (isCasting) "投屏中" else "投屏",
                                tint = if (isCasting) AuroraCoral else Color.White,
                            )
                        }
                    }
                    GlassSurface(
                        shape = CircleShape,
                        forceDark = true,
                        modifier = Modifier
                            .size(44.dp)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onFullscreenChange(true)
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "全屏播放")
                        }
                    }
                }
            }

            // Apple HIG / Netflix Floating Cast Banner when casting
            AnimatedVisibility(
                visible = isCasting,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 10.dp, start = 60.dp, end = 60.dp),
            ) {
                val connected = castState as? CastState.Connected
                if (connected != null) {
                    Surface(
                        color = Color(0xDD14171F),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, AuroraCoral.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { isCastSheetVisible = true },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CastConnected,
                                contentDescription = null,
                                tint = AuroraCoral,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "正在投屏至 ${connected.device.name}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    if (connected.isPlaying) castManager.pause() else castManager.play()
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(36.dp),
                            ) {
                                Icon(
                                    imageVector = if (connected.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (connected.isPlaying) "暫停電視" else "播放",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = { castManager.disconnect() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "斷開投屏",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (isCastSheetVisible) {
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
private fun PlayerDetails(
    video: VideoItem,
    isFavorite: Boolean,
    cachedVideo: OfflineVideo?,
    episodes: List<PlaybackEpisode>,
    selectedEpisode: Int,
    isEpisodePickerVisible: Boolean,
    isCasting: Boolean,
    onFavorite: () -> Unit,
    onCache: () -> Unit,
    onCast: () -> Unit,
    onToggleEpisodes: () -> Unit,
    onEpisode: (PlaybackEpisode) -> Unit,
) {
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
            .background(AuroraImmersiveBackground)
            .padding(horizontal = 18.dp, vertical = 18.dp)
            .animateContentSize(),
    ) {
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = listOf(video.author, video.views).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = AuroraMediaMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            PlayerActionButton(
                label = if (isFavorite) "已收藏" else "收藏",
                onClick = onFavorite,
                modifier = Modifier.weight(1f),
            )
            {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isFavorite) AuroraCoral else LocalContentColor.current,
                )
            }
            PlayerActionButton(
                label = cacheActionDescription,
                stateDescriptionText = cacheStateDescription,
                onClick = onCache,
                enabled = cachedVideo?.state != OfflineCacheState.REMOVING,
                modifier = Modifier.weight(1f),
            ) {
                when (cachedVideo?.state) {
                    OfflineCacheState.RESOLVING,
                    OfflineCacheState.QUEUED,
                    OfflineCacheState.DOWNLOADING -> CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OfflineCacheState.COMPLETED -> Icon(
                        Icons.Default.DownloadDone,
                        contentDescription = null,
                        tint = AuroraCoral,
                    )
                    OfflineCacheState.FAILED -> Icon(Icons.Default.Refresh, contentDescription = null)
                    OfflineCacheState.REMOVING -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    null -> Icon(Icons.Default.Download, contentDescription = null)
                }
            }
            PlayerActionButton(
                label = if (isCasting) "投屏中" else "投屏",
                onClick = onCast,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = if (isCasting) Icons.Filled.CastConnected else Icons.Outlined.Cast,
                    contentDescription = null,
                    tint = if (isCasting) AuroraCoral else LocalContentColor.current,
                )
            }
            if (episodes.size > 1) {
                PlayerActionButton(
                    label = "选集 ${selectedEpisode}/${episodes.size}",
                    onClick = onToggleEpisodes,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.FormatListNumbered, contentDescription = null)
                }
            }
        }
        AnimatedVisibility(
            visible = episodes.size > 1 && isEpisodePickerVisible,
            enter = fadeIn() + scaleIn(initialScale = 0.98f),
            exit = fadeOut() + scaleOut(targetScale = 0.98f),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                episodes.forEach { episode ->
                    val selected = episode.number == selectedEpisode
                    Surface(
                        onClick = { onEpisode(episode) },
                        color = if (selected) AuroraCoral else AuroraMediaSurfaceVariant,
                        contentColor = if (selected) Color.White else AuroraMediaLabel,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            text = episode.label,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = Color(0xFF262626))
    }
}

@Composable
private fun PlayerActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stateDescriptionText: String? = null,
    icon: @Composable () -> Unit,
) {
    GlassSurface(
        shape = RoundedCornerShape(16.dp),
        forceDark = true,
        modifier = modifier
            .height(68.dp)
            .applePressEffect(enabled = enabled, pressedScale = 0.94f, onClick = onClick)
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
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    letterSpacing = 0.2.sp,
                ),
                color = if (enabled) LocalContentColor.current.copy(alpha = 0.92f) else LocalContentColor.current.copy(alpha = 0.38f),
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
) {
    val haptic = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag("player-recommendation"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.43f)
                .aspectRatio(16 / 9f)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            MediaImage(video = video, modifier = Modifier.fillMaxSize())
            if (video.duration.isNotBlank()) {
                Text(
                    text = video.duration,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .background(Color.Black.copy(alpha = 0.74f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = video.author,
                style = MaterialTheme.typography.labelMedium,
                color = AuroraMediaMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = video.views,
                style = MaterialTheme.typography.labelMedium,
                color = AuroraMediaMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onFavorite()
            },
            modifier = Modifier.offset(x = 8.dp, y = (-8).dp),
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (isFavorite) "取消收藏" else "收藏视频",
                tint = if (isFavorite) AuroraCoral else AuroraMediaMuted,
            )
        }
    }
}

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
