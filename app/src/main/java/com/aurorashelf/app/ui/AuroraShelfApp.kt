package com.aurorashelf.app.ui

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.aurorashelf.app.model.AppDestination
import com.aurorashelf.app.model.ContentSource
import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.FeedCategory
import com.aurorashelf.app.model.VideoItem
import com.aurorashelf.app.data.SourceAddress
import com.aurorashelf.app.data.PlaybackEpisode
import com.aurorashelf.app.data.PlaybackResolver
import com.aurorashelf.app.data.OfflineCacheManager
import com.aurorashelf.app.data.OfflineCacheState
import com.aurorashelf.app.data.OfflineVideo
import com.aurorashelf.app.ui.theme.AuroraBackground
import com.aurorashelf.app.ui.theme.AuroraCoral
import com.aurorashelf.app.ui.theme.AuroraMuted
import com.aurorashelf.app.ui.theme.AuroraSurface
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LocalWindowLayout = staticCompositionLocalOf { WindowLayout.calculate(393f, 690f, false) }
private val LocalDockHeight = staticCompositionLocalOf { 112.dp }

@Composable
fun AuroraShelfApp(viewModel: AuroraViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = state.selectedVideo != null || state.isSearchOpen || state.destination != AppDestination.HOME) {
        when {
            state.selectedVideo != null -> viewModel.closeVideo()
            state.isSearchOpen -> viewModel.setSearchOpen(false)
            else -> viewModel.selectDestination(AppDestination.HOME)
        }
    }

    Surface(
        color = AuroraBackground,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize(),
    ) {
      BoxWithConstraints(modifier = Modifier.fillMaxSize()
          .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        val density = LocalDensity.current
        val isLandscape = maxWidth > maxHeight
        var dockHeight by remember(isLandscape) { mutableStateOf(if (isLandscape) 0.dp else 112.dp) }
        val statusHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
        val windowLayout = WindowLayout.calculate(
            maxWidth.value, (maxHeight - statusHeight - dockHeight).value, isLandscape,
        )
        CompositionLocalProvider(LocalWindowLayout provides windowLayout, LocalDockHeight provides dockHeight) {
        AnimatedContent(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = if (windowLayout.compactDock) 760.dp else 680.dp)
                .fillMaxSize()
                .then(if (windowLayout.compactDock) Modifier.padding(start = 80.dp) else Modifier)
                .then(
                if (state.isSearchOpen || state.selectedVideo != null) {
                Modifier.clearAndSetSemantics { }
            } else Modifier),
            targetState = state.destination,
            transitionSpec = {
                (fadeIn(spring(stiffness = 500f)) + scaleIn(initialScale = 0.985f)) togetherWith
                    (fadeOut() + scaleOut(targetScale = 1.01f))
            },
            label = "destination",
        ) { destination ->
            when (destination) {
                AppDestination.HOME -> HomeScreen(
                    state = state,
                    onSearch = { viewModel.setSearchOpen(true) },
                    onSettings = { viewModel.selectDestination(AppDestination.SETTINGS) },
                    onSource = viewModel::saveSourceUrl,
                    onCategory = viewModel::selectCategory,
                    onVideo = viewModel::openVideo,
                    onFavorite = viewModel::toggleFavorite,
                    onRefresh = viewModel::refresh,
                    onLoadMore = viewModel::loadMore,
                )

                AppDestination.DISCOVER -> LibraryScreen(
                    title = "发现",
                    emptyMessage = "暂无已加载内容，请回到首页刷新",
                    videos = state.videos,
                    onVideo = viewModel::openVideo,
                    onFavorite = viewModel::toggleFavorite,
                    favoriteIds = state.favoriteIds,
                )

                AppDestination.FAVORITES -> LibraryScreen(
                    title = "我的收藏",
                    emptyMessage = "收藏的视频会出现在这里",
                    videos = state.allKnownVideos.filter { it.id in state.favoriteIds },
                    onVideo = viewModel::openVideo,
                    onFavorite = viewModel::toggleFavorite,
                    favoriteIds = state.favoriteIds,
                )

                AppDestination.HISTORY -> LibraryScreen(
                    title = "观看历史",
                    emptyMessage = "播放过的视频会保留在这里",
                    videos = state.historyIds.mapNotNull { id -> state.allKnownVideos.find { it.id == id } },
                    onVideo = viewModel::openVideo,
                    onFavorite = viewModel::toggleFavorite,
                    favoriteIds = state.favoriteIds,
                )

                AppDestination.SETTINGS -> SettingsScreen(
                    currentUrl = state.sourceUrl,
                    onSave = viewModel::saveSourceUrl,
                    onOpenCached = viewModel::openVideo,
                )
            }
        }

        if (windowLayout.compactDock) {
            AnimatedVisibility(
                visible = state.selectedVideo == null && !state.isSearchOpen,
                enter = fadeIn() + scaleIn(initialScale = 0.94f),
                exit = fadeOut() + scaleOut(targetScale = 0.94f),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .testTag("navigation-rail")
                    .padding(start = 12.dp),
            ) {
                CompactNavigationRail(
                    selected = state.destination,
                    onSelect = viewModel::selectDestination,
                )
            }
        } else {
            AnimatedVisibility(
                visible = state.selectedVideo == null && !state.isSearchOpen,
                enter = fadeIn() + scaleIn(initialScale = 0.96f),
                exit = fadeOut() + scaleOut(targetScale = 0.96f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 480.dp)
                    .testTag("bottom-dock")
                    .onSizeChanged { size ->
                        if (size.height > 0) dockHeight = with(density) { size.height.toDp() }
                    }
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                ExpressiveBottomBar(
                    selected = state.destination,
                    onSelect = viewModel::selectDestination,
                )
            }
        }

        AnimatedVisibility(
            visible = state.isSearchOpen,
            modifier = if (state.selectedVideo != null) Modifier.clearAndSetSemantics { } else Modifier,
            enter = fadeIn() + scaleIn(initialScale = 0.98f),
            exit = fadeOut() + scaleOut(targetScale = 0.98f),
        ) {
            SearchScreen(
                query = state.query,
                videos = state.searchResults,
                favoriteIds = state.favoriteIds,
                isSearching = state.isSearching,
                message = state.searchMessage,
                onQuery = viewModel::updateQuery,
                onSearch = viewModel::search,
                onClose = { viewModel.setSearchOpen(false) },
                onVideo = viewModel::openVideo,
                onFavorite = viewModel::toggleFavorite,
            )
        }

        state.selectedVideo?.let { video ->
            PlayerScreen(
                video = video,
                isFavorite = video.id in state.favoriteIds,
                onBack = viewModel::closeVideo,
                onFavorite = { viewModel.toggleFavorite(video) },
            )
        }
        }
      }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(
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
        stickyHeader(key = "home-pinned-header") {
            Surface(
                color = AuroraBackground,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 10.dp),
                ) {
                    HomeHeader(
                        sourceUrl = state.sourceUrl,
                        onSource = onSource,
                        onSearch = onSearch,
                        onSettings = onSettings,
                    )
                    CategoryBar(
                        selected = state.category,
                        sourceUrl = state.sourceUrl,
                        onCategory = onCategory,
                    )
                }
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
                    color = AuroraCoral,
                    trackColor = Color.White.copy(alpha = 0.08f),
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
                    Text("为你推荐", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    Text("持续更新", style = MaterialTheme.typography.labelMedium, color = AuroraMuted)
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
        items(state.videos.drop(1), key = VideoItem::id) { video ->
            VideoListRow(
                video = video,
                isFavorite = video.id in state.favoriteIds,
                onClick = { onVideo(video) },
                onFavorite = { onFavorite(video) },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        if (state.isLoadingMore) {
            item(key = "loading-more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = AuroraCoral, modifier = Modifier.size(24.dp))
                }
            }
        } else if (!state.canLoadMore && state.videos.isNotEmpty()) {
            item(key = "end-of-feed") {
                Text(
                    text = "已加载全部内容",
                    color = AuroraMuted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

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
            Text("极光视界", style = MaterialTheme.typography.headlineSmall, maxLines = 1)
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
                    color = Color.White.copy(alpha = 0.12f),
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
                color = AuroraMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "选择视频源",
                tint = AuroraMuted,
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
                            Text(source.description, style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 36.dp),
    ) {
        FeedCategory.availableFor(sourceUrl).forEach { category ->
            val isSelected = selected == category
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                animationSpec = spring(stiffness = 500f, dampingRatio = 0.78f),
                label = "category-color",
            )
            val cornerRadius by animateDpAsState(
                targetValue = if (isSelected) 22.dp else 14.dp,
                animationSpec = spring(stiffness = 420f, dampingRatio = 0.72f),
                label = "category-shape",
            )
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCategory(category)
                },
                color = containerColor,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(cornerRadius),
                tonalElevation = if (isSelected) 2.dp else 0.dp,
                    modifier = Modifier.heightIn(min = 44.dp),
            ) {
                Text(
                    text = category.displayLabel(sourceUrl),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(message: String, onRefresh: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = AuroraMuted)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = AuroraMuted,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = "重新加载")
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
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = AuroraSurface,
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
                    .padding(end = if (usesCompactNavigation) 76.dp else 18.dp),
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
private fun VideoListRow(
    video: VideoItem,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(LocalWindowLayout.current.rowThumbnailFraction)
                    .aspectRatio(16 / 10f)
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                MediaImage(video = video, modifier = Modifier.fillMaxSize())
                Text(
                    text = video.duration,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(topStart = 10.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(video.author, style = MaterialTheme.typography.labelMedium, color = AuroraMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(video.views, style = MaterialTheme.typography.labelMedium, color = AuroraMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏视频",
                    tint = if (isFavorite) AuroraCoral else AuroraMuted,
                )
            }
        }
    }
}

@Composable
private fun MediaImage(video: VideoItem, modifier: Modifier = Modifier) {
    val imageBytes by produceState<ByteArray?>(
        initialValue = null,
        key1 = video.thumbnailUrl,
        key2 = video.pageUrl,
    ) {
        value = video.thumbnailUrl?.let { url ->
            withContext(Dispatchers.IO) { CoverImageLoader.load(url, video.pageUrl) }
        }
    }
    AsyncImage(
        model = imageBytes,
        contentDescription = video.title,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(AuroraSurface),
    )
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

@Composable
private fun ExpressiveBottomBar(
    selected: AppDestination,
    onSelect: (AppDestination) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = Modifier.fillMaxWidth().height(68.dp),
        color = Color(0xFF10151F),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(34.dp),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 4.dp),
        ) {
            AppDestination.entries.forEach { destination ->
                val icon = when (destination) {
                    AppDestination.HOME -> Icons.Default.Home
                    AppDestination.DISCOVER -> Icons.Default.Explore
                    AppDestination.FAVORITES -> Icons.Default.Favorite
                    AppDestination.HISTORY -> Icons.Default.History
                    AppDestination.SETTINGS -> Icons.Default.Settings
                }
                val isSelected = selected == destination
                val indicatorColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    animationSpec = spring(stiffness = 500f, dampingRatio = 0.78f),
                    label = "navigation-indicator",
                )
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(destination)
                    },
                    color = Color.Transparent,
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Surface(
                            color = indicatorColor,
                            contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.width(56.dp).height(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon, contentDescription = destination.label, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelMedium.copy(lineHeight = 14.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactNavigationRail(
    selected: AppDestination,
    onSelect: (AppDestination) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        color = Color(0xFF10151F),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.width(64.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
        ) {
            AppDestination.entries.forEach { destination ->
                val icon = when (destination) {
                    AppDestination.HOME -> Icons.Default.Home
                    AppDestination.DISCOVER -> Icons.Default.Explore
                    AppDestination.FAVORITES -> Icons.Default.Favorite
                    AppDestination.HISTORY -> Icons.Default.History
                    AppDestination.SETTINGS -> Icons.Default.Settings
                }
                val isSelected = selected == destination
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(destination)
                    },
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(if (isSelected) 20.dp else 14.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) {
                        Icon(icon, contentDescription = destination.label, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    title: String,
    emptyMessage: String,
    videos: List<VideoItem>,
    onVideo: (VideoItem) -> Unit,
    onFavorite: (VideoItem) -> Unit,
    favoriteIds: Set<String>,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 28.dp,
            bottom = LocalDockHeight.current + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        item { Text(title, style = MaterialTheme.typography.headlineLarge) }
        if (videos.isEmpty()) {
            item {
                EmptyState(message = emptyMessage)
            }
        } else {
            items(videos, key = VideoItem::id) { video ->
                VideoListRow(
                    video = video,
                    isFavorite = video.id in favoriteIds,
                    onClick = { onVideo(video) },
                    onFavorite = { onFavorite(video) },
                    modifier = Modifier,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 96.dp),
    ) {
        Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = AuroraMuted, modifier = Modifier.size(42.dp))
        Text(message, color = AuroraMuted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(
    query: String,
    videos: List<VideoItem>,
    favoriteIds: Set<String>,
    isSearching: Boolean,
    message: String?,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    onFavorite: (VideoItem) -> Unit,
) {
    val matches = remember(query, videos) {
        if (query.isBlank()) videos else videos.filter {
            it.title.contains(query, ignoreCase = true) || it.author.contains(query, ignoreCase = true)
        }
    }
    Surface(color = AuroraBackground, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "返回")
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        label = { Text("搜索当前视频源") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(onClick = onSearch, enabled = query.isNotBlank()) {
                                    Icon(Icons.Default.Search, contentDescription = "开始搜索")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            message?.let { status -> item { StatusBanner(status, onSearch) } }
            if (matches.isEmpty() && !isSearching && message == null) {
                item { EmptyState(if (query.isBlank()) "输入关键词搜索当前视频源" else "点击搜索按钮开始查找") }
            } else {
                items(matches, key = VideoItem::id) { video ->
                    VideoListRow(
                        video = video,
                        isFavorite = video.id in favoriteIds,
                        onClick = { onVideo(video) },
                        onFavorite = { onFavorite(video) },
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    currentUrl: String,
    onSave: (String) -> Unit,
    onOpenCached: (VideoItem) -> Unit,
) {
    val context = LocalContext.current
    val cacheManager = remember(context) { OfflineCacheManager.get(context) }
    val cachedVideos by cacheManager.entries.collectAsStateWithLifecycle()
    var sourceUrl by remember(currentUrl) { mutableStateOf(currentUrl) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    val addressError = SourceAddress.error(sourceUrl)
    val selectedSourceId = ContentSourceCatalog.find(currentUrl)?.id
    val downloadedBytes = cachedVideos.sumOf(OfflineVideo::bytesDownloaded)
    val completedCount = cachedVideos.count(OfflineVideo::isPlayableOffline)

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("清空离线缓存？") },
            text = { Text("所有已缓存视频和正在进行的任务都会被移除。") },
            confirmButton = {
                TextButton(onClick = {
                    cacheManager.clearAll()
                    showClearConfirmation = false
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text("取消") }
            },
        )
    }
    LazyColumn(
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            top = 28.dp,
            bottom = LocalDockHeight.current + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings-list")
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("设置", style = MaterialTheme.typography.headlineLarge)
                Text("内容源与离线缓存", color = AuroraMuted)
            }
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = AuroraCoral)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("视频源", style = MaterialTheme.typography.titleLarge)
                    Text("选择后立即刷新，无需填写地址", color = AuroraMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(ContentSourceCatalog.sources, key = ContentSource::id) { source ->
            SourceOption(
                source = source,
                selected = selectedSourceId == source.id,
                onClick = { onSave(source.baseUrl) },
            )
        }
        item {
            Surface(
                color = AuroraSurface,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(20.dp),
                ) {
                    Text("高级自定义", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = sourceUrl,
                        onValueChange = { sourceUrl = it },
                        label = { Text("站点地址") },
                        placeholder = { Text("https://example.com") },
                        isError = addressError != null,
                        supportingText = { Text(addressError ?: "仅用于兼容同结构的自定义站点") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { onSave(sourceUrl) },
                        enabled = addressError == null,
                        colors = ButtonDefaults.buttonColors(containerColor = AuroraCoral),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("保存并刷新")
                    }
                }
            }
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp),
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = AuroraCoral)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("离线缓存", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "$completedCount 个可离线观看 · ${formatBytes(downloadedBytes)}",
                        color = AuroraMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (cachedVideos.isNotEmpty()) {
                    IconButton(onClick = { showClearConfirmation = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "清空全部缓存")
                    }
                }
            }
        }
        if (cachedVideos.isEmpty()) {
            item {
                Surface(
                    color = AuroraSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "播放视频时点击顶部的缓存按钮，即可在这里管理。",
                        color = AuroraMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(18.dp),
                    )
                }
            }
        } else {
            items(cachedVideos, key = OfflineVideo::id) { cached ->
                OfflineCacheRow(
                    cached = cached,
                    onOpen = { if (cached.isPlayableOffline) onOpenCached(cached.asVideoItem()) },
                    onRetry = { cacheManager.retry(cached) },
                    onRemove = { cacheManager.remove(cached.id) },
                )
            }
        }
    }
}

@Composable
private fun OfflineCacheRow(
    cached: OfflineVideo,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        onClick = onOpen,
        enabled = cached.isPlayableOffline,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    cached.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                val detail = buildList {
                    cached.episodeLabel?.let(::add)
                    add(
                        when (cached.state) {
                            OfflineCacheState.RESOLVING -> "正在解析"
                            OfflineCacheState.QUEUED -> "等待缓存"
                            OfflineCacheState.DOWNLOADING -> "缓存中 ${cached.percentDownloaded.toInt()}%"
                            OfflineCacheState.COMPLETED -> formatBytes(cached.bytesDownloaded)
                            OfflineCacheState.FAILED -> "缓存失败"
                            OfflineCacheState.REMOVING -> "正在移除"
                        },
                    )
                }.joinToString(" · ")
                Text(detail, color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
                if (cached.state == OfflineCacheState.DOWNLOADING) {
                    LinearProgressIndicator(
                        progress = { (cached.percentDownloaded / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
            if (cached.state == OfflineCacheState.FAILED) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = "重试缓存")
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "删除缓存")
            }
        }
    }
}

@Composable
private fun SourceOption(source: ContentSource, selected: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (!selected) onClick()
        },
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (selected) AuroraCoral.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(
                if (selected) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (selected) AuroraCoral else LocalContentColor.current,
            )
            Column(Modifier.weight(1f)) {
                Text(source.name, style = MaterialTheme.typography.titleMedium)
                Text(source.description, style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
            }
        }
    }
}

@Composable
private fun PlayerScreen(
    video: VideoItem,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
) {
    val context = LocalContext.current
    val cacheManager = remember(context) { OfflineCacheManager.get(context) }
    val cachedVideos by cacheManager.entries.collectAsStateWithLifecycle()
    var isFullscreen by remember(video.id) { mutableStateOf(false) }
    var playbackUrl by remember(video.id) { mutableStateOf(video.pageUrl) }
    var isEpisodePickerVisible by remember(video.id) { mutableStateOf(false) }
    val episodes by produceState<List<PlaybackEpisode>>(initialValue = emptyList(), key1 = video.pageUrl) {
        value = runCatching { PlaybackResolver().resolveEpisodes(video.pageUrl) }.getOrDefault(emptyList())
    }
    val selectedEpisode = episodes.firstOrNull { it.pageUrl == playbackUrl }?.number ?: 1
    val selectedEpisodeLabel = episodes.firstOrNull { it.pageUrl == playbackUrl }?.label
    val cachedVideo = cachedVideos.firstOrNull { it.id == OfflineCacheManager.idFor(playbackUrl) }
    val haptic = LocalHapticFeedback.current
    Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
        Box {
            NativeSitePlayer(pageUrl = playbackUrl, isFullscreen = isFullscreen, onFullscreenChange = { isFullscreen = it })
            if (!isFullscreen) {
            PlayerTopBar(
                title = video.title,
                isFavorite = isFavorite,
                onBack = onBack,
                onFavorite = onFavorite,
                cachedVideo = cachedVideo,
                onCache = {
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
                },
                onEpisodes = if (episodes.size > 1) {
                    { isEpisodePickerVisible = !isEpisodePickerVisible }
                } else null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
            AnimatedVisibility(
                visible = episodes.size > 1 && isEpisodePickerVisible,
                enter = fadeIn() + scaleIn(initialScale = 0.96f),
                exit = fadeOut() + scaleOut(targetScale = 0.96f),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                GlassSurface(
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                        .fillMaxWidth(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text("选集", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.78f))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            episodes.forEach { episode ->
                                val selected = episode.number == selectedEpisode
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        playbackUrl = episode.pageUrl
                                        isEpisodePickerVisible = false
                                    },
                                    color = if (selected) AuroraCoral else Color.White.copy(alpha = 0.12f),
                                    contentColor = Color.White,
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text(
                                        text = episode.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun PlayerTopBar(
    title: String,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    cachedVideo: OfflineVideo?,
    onCache: () -> Unit,
    onEpisodes: (() -> Unit)?,
    modifier: Modifier = Modifier,
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
    GlassSurface(
        shape = RoundedCornerShape(30.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            onEpisodes?.let { showEpisodes ->
                IconButton(onClick = showEpisodes) {
                    Icon(Icons.Default.FormatListNumbered, contentDescription = "选择剧集")
                }
            }
            IconButton(
                onClick = onCache,
                enabled = cachedVideo?.state != OfflineCacheState.REMOVING,
                modifier = Modifier.semantics { contentDescription = cacheActionDescription },
            ) {
                when (cachedVideo?.state) {
                    OfflineCacheState.RESOLVING,
                    OfflineCacheState.QUEUED,
                    OfflineCacheState.DOWNLOADING -> CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = AuroraCoral,
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
            IconButton(onClick = onFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    tint = if (isFavorite) AuroraCoral else LocalContentColor.current,
                )
            }
        }
    }
}

@Composable
private fun GlassSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
        ),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.clip(shape)) { content() }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1_024L -> "$bytes B"
    bytes < 1_048_576L -> "%.1f KB".format(bytes / 1_024f)
    bytes < 1_073_741_824L -> "%.1f MB".format(bytes / 1_048_576f)
    else -> "%.2f GB".format(bytes / 1_073_741_824f)
}
