package com.aurorashelf.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
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
import com.aurorashelf.app.data.PlaybackResolver
import com.aurorashelf.app.data.OfflineCacheManager
import com.aurorashelf.app.data.OfflineCacheState
import com.aurorashelf.app.data.OfflineVideo
import com.aurorashelf.app.ui.liquid.LiquidBottomNavigation
import com.aurorashelf.app.ui.liquid.LiquidNavigationTab
import com.aurorashelf.app.ui.theme.AuroraCoral
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    var playbackUrl by remember(video.id) { mutableStateOf(video.pageUrl) }
    var isEpisodePickerVisible by remember(video.id) { mutableStateOf(false) }
    val episodes by produceState<List<PlaybackEpisode>>(initialValue = emptyList(), key1 = video.pageUrl) {
        value = runCatching { PlaybackResolver().resolveEpisodes(video.pageUrl) }.getOrDefault(emptyList())
    }
    val selectedEpisode = episodes.firstOrNull { it.pageUrl == playbackUrl }?.number ?: 1
    val selectedEpisodeLabel = episodes.firstOrNull { it.pageUrl == playbackUrl }?.label
    val cachedVideo = cachedVideos.firstOrNull { it.id == OfflineCacheManager.idFor(playbackUrl) }
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
        color = if (isFullscreen) Color.Black else MaterialTheme.colorScheme.background,
        contentColor = if (isFullscreen) Color.White else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (!isFullscreen) {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .testTag("player-details"),
                ) {
                    item(key = "player-space") {
                        Spacer(Modifier.fillMaxWidth().aspectRatio(16 / 9f))
                    }
                    item(key = "player-info") {
                        PlayerDetails(
                            video = video,
                            isFavorite = isFavorite,
                            cachedVideo = cachedVideo,
                            episodes = episodes,
                            selectedEpisode = selectedEpisode,
                            isEpisodePickerVisible = isEpisodePickerVisible,
                            onFavorite = onFavorite,
                            onCache = onCache,
                            onToggleEpisodes = { isEpisodePickerVisible = !isEpisodePickerVisible },
                            onEpisode = { episode ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                playbackUrl = episode.pageUrl
                                isEpisodePickerVisible = false
                            },
                        )
                    }
                    if (relatedVideos.isNotEmpty()) {
                        item(key = "recommendation-title") {
                            Text(
                                text = "接着观看",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 10.dp),
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
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .then(
                        if (isFullscreen) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier.statusBarsPadding().fillMaxWidth().aspectRatio(16 / 9f)
                        },
                    )
                    .testTag("native-player-container"),
            )

            if (!isFullscreen) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.58f),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(10.dp)
                        .size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "返回")
                    }
                }
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onFullscreenChange(true)
                    },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.58f),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(10.dp)
                        .size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "全屏播放")
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
    selectedEpisode: Int,
    isEpisodePickerVisible: Boolean,
    onFavorite: () -> Unit,
    onCache: () -> Unit,
    onToggleEpisodes: () -> Unit,
    onEpisode: (PlaybackEpisode) -> Unit,
) {
    val cacheActionDescription = when (cachedVideo?.state) {
        OfflineCacheState.RESOLVING,
        OfflineCacheState.QUEUED,
        OfflineCacheState.DOWNLOADING -> "取消缓存"
        OfflineCacheState.COMPLETED -> "已离线缓存"
        OfflineCacheState.FAILED -> "重试缓存"
        OfflineCacheState.REMOVING -> "正在删除缓存"
        null -> "缓存当前视频"
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 18.dp, vertical = 18.dp)
            .animateContentSize(),
    ) {
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOf(video.author, video.views).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
}

@Composable
private fun PlayerActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.heightIn(min = 64.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = video.author,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = video.views,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                tint = if (isFavorite) AuroraCoral else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun relatedVideos(current: VideoItem, videos: List<VideoItem>): List<VideoItem> =
    videos.distinctBy(VideoItem::id).filterNot { it.id == current.id }
