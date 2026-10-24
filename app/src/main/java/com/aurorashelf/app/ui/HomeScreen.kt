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
import androidx.compose.ui.semantics.heading
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeScreen(
    state: AuroraUiState,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onSource: (String) -> Unit,
    onCategory: (FeedCategory) -> Unit,
    onVideo: (VideoItem) -> Unit,
    onFavorite: (VideoItem) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    // C7: at 200% (and any >=150%) system font the two-column grid becomes unreadable,
    // so the feed falls back to a single full-width column. Default type stays untouched.
    val gridColumns = if (fontScale >= SINGLE_COLUMN_FONT_SCALE) 1 else 2
    val listState = rememberLazyListState()
    LaunchedEffect(listState, state.videos.size, state.canLoadMore) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to layout.totalItemsCount
        }.map { (lastVisible, totalItems) ->
            totalItems > 0 && lastVisible >= totalItems - 5
        }.distinctUntilChanged().filter { it }.collect {
            onLoadMore()
        }
    }
    LazyColumn(
        state = listState,
        flingBehavior = ScrollableDefaults.flingBehavior(),
        // The feed intentionally continues behind the floating navigation surface.
        // Extra trailing space still lets the final card scroll fully above it.
        contentPadding = PaddingValues(bottom = LocalDockHeight.current + 24.dp),
        verticalArrangement = Arrangement.spacedBy(LocalWindowLayout.current.sectionGap.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("home-feed")
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        item(key = "home-header") {
            HomeHeader(
                sourceUrl = state.sourceUrl,
                onSource = onSource,
                onSearch = onSearch,
                onSettings = onSettings,
            )
        }
        stickyHeader(key = "home-pinned-categories") {
            Surface(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CategoryBar(
                    selected = state.category,
                    sourceUrl = state.sourceUrl,
                    onCategory = onCategory,
                )
            }
        }
        state.message?.let { message ->
            item {
                StatusBanner(message = message, onRefresh = onRefresh)
            }
        }
        if (state.isLoading) {
            item {
                LinearProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(CircleShape),
                )
            }
        }
        if (state.videos.isNotEmpty()) {
            item(key = "feed-heading") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                ) {
                    Text(
                        text = "为你推荐",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.weight(1f))
                    Text("持续更新", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        state.videos.firstOrNull()?.let { hero ->
            item {
                HeroVideoCard(
                    video = hero,
                    isFavorite = hero.id in state.favoriteIds,
                    onClick = { onVideo(hero) },
                    onFavorite = { onFavorite(hero) },
                )
            }
        }
        items(
            items = homeGridRows(state.videos, gridColumns),
            key = { row -> row.joinToString(separator = "|") { it.id } },
        ) { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                row.forEach { video ->
                    VideoGridCard(
                        video = video,
                        isFavorite = video.id in state.favoriteIds,
                        onClick = { onVideo(video) },
                        onFavorite = { onFavorite(video) },
                        modifier = if (gridColumns == 1) Modifier.fillMaxWidth() else Modifier.weight(1f),
                    )
                }
                if (gridColumns > 1 && row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (state.isLoadingMore) {
            item(key = "loading-more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
        } else if (!state.canLoadMore && state.videos.isNotEmpty()) {
            item(key = "end-of-feed") {
                Text(
                    text = "已加载全部内容",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

internal fun homeGridRows(videos: List<VideoItem>, columns: Int = 2): List<List<VideoItem>> =
    videos.drop(1).chunked(columns)

@Composable
private fun HomeHeader(
    sourceUrl: String,
    onSource: (String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "网灰",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                modifier = Modifier.semantics { heading() },
            )
            SourceMenu(sourceUrl = sourceUrl, onSource = onSource)
        }
        GlassSurface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .width(156.dp)
                .height(44.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(role = Role.Button, onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSearch()
                        })
                        .padding(start = 10.dp, end = 4.dp),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(22.dp))
                    Text(
                        "搜索视频",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                    modifier = Modifier
                        .height(24.dp)
                        .width(1.dp),
                )
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSettings()
                }) {
                    Icon(Icons.Default.Settings, contentDescription = "站点设置", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun SourceMenu(sourceUrl: String, onSource: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    expanded = true
                }
                .padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            Text(
                text = ContentSourceCatalog.displayName(sourceUrl),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "选择视频源",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ContentSourceCatalog.sources.forEach { source ->
                val selected = ContentSourceCatalog.find(sourceUrl)?.id == source.id
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(source.name, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            Text(source.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    leadingIcon = {
                        Icon(
                            if (selected) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (selected) AuroraCoral else LocalContentColor.current,
                        )
                    },
                    onClick = {
                        expanded = false
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (!selected) onSource(source.baseUrl)
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryBar(
    selected: FeedCategory,
    sourceUrl: String,
    onCategory: (FeedCategory) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 36.dp),
    ) {
        FeedCategory.availableFor(sourceUrl).forEach { category ->
            val isSelected = selected == category
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = spring(stiffness = 500f, dampingRatio = 0.78f),
                label = "category-color",
            )
            val indicatorWidth by animateDpAsState(
                targetValue = if (isSelected) 22.dp else 0.dp,
                animationSpec = spring(stiffness = 420f, dampingRatio = 0.72f),
                label = "category-indicator",
            )
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCategory(category)
                },
                color = Color.Transparent,
                contentColor = contentColor,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = category.displayLabel(sourceUrl),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(indicatorWidth)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroVideoCard(
    video: VideoItem,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
) {
    val usesCompactNavigation = LocalWindowLayout.current.compactDock
    val fontScale = LocalDensity.current.fontScale
    // At large font sizes the heart button and the metadata line collide; reserve end space then.
    val infoEndPadding = when {
        usesCompactNavigation -> 76.dp
        fontScale >= 1.5f -> 68.dp
        else -> 18.dp
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .aspectRatio(LocalWindowLayout.current.heroAspectRatio)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box {
            MediaImage(video = video, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.65f)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))),
            )
        Surface(
            color = Color.Black.copy(alpha = 0.58f),
                contentColor = Color.White,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .align(if (usesCompactNavigation) Alignment.TopEnd else Alignment.Center)
                    .then(
                        if (usesCompactNavigation) Modifier.padding(14.dp)
                        else Modifier.offset(y = (-24).dp),
                    ),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "播放 ${video.title}",
                    modifier = Modifier.padding(12.dp).size(28.dp),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 18.dp, top = 18.dp, bottom = 18.dp)
                    .padding(end = infoEndPadding),
            ) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${video.duration}  ·  ${video.author}  ·  ${video.views}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 48.dp),
                )
            }
            FavoriteButton(
                isFavorite = isFavorite,
                onClick = onFavorite,
                modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
            )
        }
    }
}

@Composable
private fun VideoGridCard(
    video: VideoItem,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .testTag("home-grid-card"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16 / 10f)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            MediaImage(video = video, modifier = Modifier.fillMaxSize())
            if (video.duration.isNotBlank()) {
                Text(
                    text = video.duration,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.74f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${video.author} · ${video.views}",
                    style = MaterialTheme.typography.labelSmall,
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
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }
}

@Composable
private fun FavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        color = Color.Black.copy(alpha = 0.46f),
        contentColor = if (isFavorite) AuroraCoral else Color.White,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isFavorite) "取消收藏此视频" else "收藏此视频",
            modifier = Modifier.padding(12.dp).size(22.dp),
        )
    }
}

/** Font scale at which the two-column home feed drops to one column (HIG C7). */
private const val SINGLE_COLUMN_FONT_SCALE = 1.5f
