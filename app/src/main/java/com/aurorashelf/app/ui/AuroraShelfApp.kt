@file:OptIn(
    dev.chrisbanes.haze.ExperimentalHazeApi::class,
    dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi::class,
)

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

private val LocalWindowLayout = staticCompositionLocalOf { WindowLayout.calculate(393f, 690f, false) }
private val LocalDockHeight = staticCompositionLocalOf { 112.dp }
private val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

private enum class SettingsRoute { OVERVIEW, VIDEO_SOURCES, OFFLINE_CACHE }
private enum class PersonalRoute { OVERVIEW, FAVORITES, HISTORY, SETTINGS }

@Composable
fun AuroraShelfApp(
    viewModel: AuroraViewModel = viewModel(),
    comicViewModel: ComicViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val comicState by comicViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val hazeState = rememberHazeState()
    val liquidBackdrop = rememberLayerBackdrop()
    var isPlayerFullscreen by rememberSaveable(state.selectedVideo?.id) { mutableStateOf(false) }
    var isCurrentVideoPortrait by remember(state.selectedVideo?.id) { mutableStateOf<Boolean?>(null) }
    var personalRoute by rememberSaveable { mutableStateOf(PersonalRoute.OVERVIEW) }
    val originalOrientation = remember(activity) { activity?.requestedOrientation }

    LaunchedEffect(activity, isPlayerFullscreen, isCurrentVideoPortrait, state.selectedVideo) {
        activity ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (isPlayerFullscreen && state.selectedVideo != null) {
            activity.requestedOrientation = when (isCurrentVideoPortrait) {
                true -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                false -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                null -> originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(activity) {
        onDispose {
            activity ?: return@onDispose
            activity.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler(
        enabled = state.selectedVideo != null || state.isSearchOpen || state.destination != AppDestination.HOME ||
            comicState.reader != null || comicState.details != null,
    ) {
        when {
            state.selectedVideo != null && isPlayerFullscreen -> isPlayerFullscreen = false
            state.selectedVideo != null -> viewModel.closeVideo()
            state.isSearchOpen -> viewModel.setSearchOpen(false)
            state.destination == AppDestination.COMICS && comicState.reader != null -> comicViewModel.closeReader()
            state.destination == AppDestination.COMICS && comicState.details != null -> comicViewModel.closeDetails()
            state.destination == AppDestination.ME && personalRoute != PersonalRoute.OVERVIEW -> {
                personalRoute = PersonalRoute.OVERVIEW
            }
            else -> viewModel.selectDestination(AppDestination.HOME)
        }
    }

    Surface(
        color = if (isPlayerFullscreen) Color.Black else MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize(),
    ) {
      BoxWithConstraints(
          modifier = Modifier
              .fillMaxSize()
              .then(
                  if (isPlayerFullscreen) Modifier
                  else Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
              ),
      ) {
        val density = LocalDensity.current
        val isLandscape = maxWidth > maxHeight
        var dockHeight by remember(isLandscape) { mutableStateOf(if (isLandscape) 0.dp else 112.dp) }
        val statusHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
        val windowLayout = WindowLayout.calculate(
            maxWidth.value, (maxHeight - statusHeight - dockHeight).value, isLandscape,
        )
        CompositionLocalProvider(
            LocalWindowLayout provides windowLayout,
            LocalDockHeight provides dockHeight,
            LocalHazeState provides hazeState,
        ) {
        AnimatedContent(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = if (windowLayout.compactDock) 760.dp else 680.dp)
                .fillMaxSize()
                .layerBackdrop(liquidBackdrop)
                .hazeSource(state = hazeState)
                .then(if (windowLayout.compactDock) Modifier.padding(start = 80.dp) else Modifier)
                .then(
                if (state.isSearchOpen || state.selectedVideo != null) {
                Modifier.clearAndSetSemantics { }
            } else Modifier),
            targetState = state.destination,
            transitionSpec = {
                fadeIn(spring(stiffness = 520f)) togetherWith fadeOut()
            },
            label = "destination",
        ) { destination ->
            when (destination) {
                AppDestination.HOME -> HomeScreen(
                    state = state,
                    onSearch = { viewModel.setSearchOpen(true) },
                    onSettings = {
                        personalRoute = PersonalRoute.SETTINGS
                        viewModel.selectDestination(AppDestination.ME)
                    },
                    onSource = viewModel::saveSourceUrl,
                    onCategory = viewModel::selectCategory,
                    onVideo = viewModel::openVideo,
                    onFavorite = viewModel::toggleFavorite,
                    onRefresh = viewModel::refresh,
                    onLoadMore = viewModel::loadMore,
                )

                AppDestination.COMICS -> ComicScreen(
                    state = comicState,
                    onSource = comicViewModel::selectSource,
                    onCategory = comicViewModel::selectCategory,
                    onQuery = comicViewModel::updateQuery,
                    onSearch = comicViewModel::submitSearch,
                    onRefresh = comicViewModel::refresh,
                    onLoadMore = comicViewModel::loadMore,
                    onComic = comicViewModel::openComic,
                    onCloseDetails = comicViewModel::closeDetails,
                    onChapter = comicViewModel::openChapter,
                    onCloseReader = comicViewModel::closeReader,
                    onPreviousChapter = comicViewModel::previousChapter,
                    onNextChapter = comicViewModel::nextChapter,
                    onRetryChapter = comicViewModel::retryChapter,
                    onAuthenticate = comicViewModel::authenticate,
                    onSignOut = comicViewModel::signOut,
                )

                AppDestination.LIVE -> FeaturePlaceholderScreen(
                    title = "直播",
                    description = "直播内容正在接入",
                    supporting = "后续将在这里提供直播源、分类和播放能力。",
                    icon = { Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(34.dp)) },
                    testTag = "live-placeholder",
                )

                AppDestination.FORUM -> FeaturePlaceholderScreen(
                    title = "论坛",
                    description = "社区功能正在准备",
                    supporting = "后续将在这里提供主题浏览、互动和内容讨论。",
                    icon = { Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(34.dp)) },
                    testTag = "forum-placeholder",
                )

                AppDestination.ME -> PersonalScreen(
                    route = personalRoute,
                    onRoute = { personalRoute = it },
                    favorites = state.allKnownVideos.filter { it.id in state.favoriteIds },
                    history = state.historyIds.mapNotNull { id -> state.allKnownVideos.find { it.id == id } },
                    favoriteIds = state.favoriteIds,
                    currentUrl = state.sourceUrl,
                    onVideo = viewModel::openVideo,
                    onFavorite = viewModel::toggleFavorite,
                    onSave = viewModel::saveSourceUrl,
                    onOpenCached = viewModel::openVideo,
                )
            }
        }

        if (windowLayout.compactDock) {
            AnimatedVisibility(
                visible = state.selectedVideo == null && !state.isSearchOpen && comicState.reader == null,
                enter = fadeIn() + scaleIn(initialScale = 0.94f),
                exit = fadeOut() + scaleOut(targetScale = 0.94f),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .testTag("navigation-rail")
                    .padding(start = 12.dp),
            ) {
                CompactNavigationRail(
                    selected = state.destination,
                    onSelect = { destination ->
                        if (destination == AppDestination.ME) personalRoute = PersonalRoute.OVERVIEW
                        viewModel.selectDestination(destination)
                    },
                )
            }
        } else {
            AnimatedVisibility(
                visible = state.selectedVideo == null && !state.isSearchOpen && comicState.reader == null,
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
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                ExpressiveBottomBar(
                    selected = state.destination,
                    onSelect = { destination ->
                        if (destination == AppDestination.ME) personalRoute = PersonalRoute.OVERVIEW
                        viewModel.selectDestination(destination)
                    },
                    backdrop = liquidBackdrop,
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
                isFullscreen = isPlayerFullscreen,
                relatedVideos = relatedVideos(video, state.videos),
                favoriteIds = state.favoriteIds,
                onBack = viewModel::closeVideo,
                onFavorite = { viewModel.toggleFavorite(video) },
                onFullscreenChange = { isPlayerFullscreen = it },
                onVideoOrientationChange = { isCurrentVideoPortrait = it },
                onVideo = viewModel::openVideo,
                onRelatedFavorite = viewModel::toggleFavorite,
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
                    color = AuroraCoral,
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
                    Text("为你推荐", style = MaterialTheme.typography.titleLarge)
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
            items = homeGridRows(state.videos),
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
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

internal fun homeGridRows(videos: List<VideoItem>): List<List<VideoItem>> =
    videos.drop(1).chunked(2)

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
private fun StatusBanner(message: String, onRefresh: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
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
                Text(video.author, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(video.views, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏视频",
                    tint = if (isFavorite) AuroraCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
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
    backdrop: Backdrop,
) {
    val haptic = LocalHapticFeedback.current
    val navigationDestinations = listOf(
        AppDestination.HOME,
        AppDestination.COMICS,
        AppDestination.LIVE,
        AppDestination.FORUM,
        AppDestination.ME,
    )
    var lastNavigationIndex by remember { mutableStateOf(0) }
    LaunchedEffect(selected) {
        val selectedIndex = navigationDestinations.indexOf(selected)
        if (selectedIndex >= 0) lastNavigationIndex = selectedIndex
    }
    LiquidBottomNavigation(
        selectedTabIndex = lastNavigationIndex,
        onTabSelected = { index -> onSelect(navigationDestinations[index]) },
        onTabPreviewed = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        },
        backdrop = backdrop,
        tabSlots = listOf(0, 1, 2, 3, 4),
        visualItemCount = 5,
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .testTag("liquid-navigation"),
    ) {
        AppDestination.entries.forEach { destination ->
            val icon = when (destination) {
                AppDestination.HOME -> Icons.Default.Home
                AppDestination.COMICS -> Icons.AutoMirrored.Filled.MenuBook
                AppDestination.LIVE -> Icons.Default.LiveTv
                AppDestination.FORUM -> Icons.Default.Forum
                AppDestination.ME -> Icons.Default.Person
            }
            LiquidNavigationTab(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(destination)
                },
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = destination.label,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(21.dp),
                )
                Text(
                    text = destination.label,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        lineHeight = 12.sp,
                    ),
                    maxLines = 1,
                )
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
    GlassSurface(
        shape = RoundedCornerShape(28.dp),
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
                    AppDestination.COMICS -> Icons.AutoMirrored.Filled.MenuBook
                    AppDestination.LIVE -> Icons.Default.LiveTv
                    AppDestination.FORUM -> Icons.Default.Forum
                    AppDestination.ME -> Icons.Default.Person
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
private fun FeaturePlaceholderScreen(
    title: String,
    description: String,
    supporting: String,
    icon: @Composable () -> Unit,
    testTag: String,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            top = 24.dp,
            bottom = LocalDockHeight.current + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag(testTag),
    ) {
        item { Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 52.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(72.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimaryContainer) {
                                icon()
                            }
                        }
                    }
                    Text(description, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        supporting,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text("即将开放", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonalScreen(
    route: PersonalRoute,
    onRoute: (PersonalRoute) -> Unit,
    favorites: List<VideoItem>,
    history: List<VideoItem>,
    favoriteIds: Set<String>,
    currentUrl: String,
    onVideo: (VideoItem) -> Unit,
    onFavorite: (VideoItem) -> Unit,
    onSave: (String) -> Unit,
    onOpenCached: (VideoItem) -> Unit,
) {
    BackHandler(enabled = route != PersonalRoute.OVERVIEW) { onRoute(PersonalRoute.OVERVIEW) }
    AnimatedContent(
        targetState = route,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "personal-route",
    ) { currentRoute ->
        when (currentRoute) {
            PersonalRoute.OVERVIEW -> PersonalOverview(
                favoriteCount = favorites.size,
                historyCount = history.size,
                currentSource = ContentSourceCatalog.find(currentUrl)?.name ?: "自定义视频源",
                onFavorites = { onRoute(PersonalRoute.FAVORITES) },
                onHistory = { onRoute(PersonalRoute.HISTORY) },
                onSettings = { onRoute(PersonalRoute.SETTINGS) },
            )
            PersonalRoute.FAVORITES -> LibraryScreen(
                title = "我的收藏",
                emptyMessage = "收藏的视频会出现在这里",
                videos = favorites,
                onVideo = onVideo,
                onFavorite = onFavorite,
                favoriteIds = favoriteIds,
                onBack = { onRoute(PersonalRoute.OVERVIEW) },
                testTag = "personal-favorites",
                emptyIcon = Icons.Outlined.FavoriteBorder,
            )
            PersonalRoute.HISTORY -> LibraryScreen(
                title = "观看历史",
                emptyMessage = "播放过的视频会保留在这里",
                videos = history,
                onVideo = onVideo,
                onFavorite = onFavorite,
                favoriteIds = favoriteIds,
                onBack = { onRoute(PersonalRoute.OVERVIEW) },
                testTag = "personal-history",
                emptyIcon = Icons.Default.History,
            )
            PersonalRoute.SETTINGS -> SettingsScreen(
                currentUrl = currentUrl,
                onSave = onSave,
                onOpenCached = onOpenCached,
                onBack = { onRoute(PersonalRoute.OVERVIEW) },
            )
        }
    }
}

@Composable
private fun PersonalOverview(
    favoriteCount: Int,
    historyCount: Int,
    currentSource: String,
    onFavorites: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            top = 22.dp,
            bottom = LocalDockHeight.current + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("personal-overview"),
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(32.dp))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                    Text("我的", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "收藏、历史与应用设置",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item { SettingsSectionLabel("你的内容") }
        item {
            SettingsNavigationCard {
                SettingsNavigationRow(
                    icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                    title = "收藏",
                    supporting = "稍后继续观看已保存的内容",
                    value = "$favoriteCount 个",
                    onClick = onFavorites,
                    modifier = Modifier.testTag("personal-favorites-entry"),
                )
                HorizontalDivider(modifier = Modifier.padding(start = 68.dp))
                SettingsNavigationRow(
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    title = "历史",
                    supporting = "查看最近播放过的视频",
                    value = "$historyCount 条",
                    onClick = onHistory,
                    modifier = Modifier.testTag("personal-history-entry"),
                )
            }
        }
        item { SettingsSectionLabel("应用") }
        item {
            SettingsNavigationCard {
                SettingsNavigationRow(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    title = "设置",
                    supporting = "视频源与离线缓存",
                    value = currentSource,
                    onClick = onSettings,
                    modifier = Modifier.testTag("personal-settings-entry"),
                )
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
    onBack: () -> Unit,
    testTag: String,
    emptyIcon: ImageVector,
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
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag(testTag),
    ) {
        item { SettingsPageHeader(title, "保存在这台设备上的内容", onBack, "返回我的") }
        if (videos.isEmpty()) {
            item {
                EmptyState(message = emptyMessage, icon = emptyIcon)
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
private fun EmptyState(message: String, icon: ImageVector = Icons.Outlined.FavoriteBorder) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 96.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(42.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
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
                item {
                    EmptyState(
                        if (query.isBlank()) "输入关键词搜索当前视频源" else "点击搜索按钮开始查找",
                        icon = Icons.Outlined.Search,
                    )
                }
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
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val cacheManager = remember(context) { OfflineCacheManager.get(context) }
    val cachedVideos by cacheManager.entries.collectAsStateWithLifecycle()
    var sourceUrl by remember(currentUrl) { mutableStateOf(currentUrl) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var route by rememberSaveable { mutableStateOf(SettingsRoute.OVERVIEW) }
    val addressError = SourceAddress.error(sourceUrl)
    val selectedSourceId = ContentSourceCatalog.find(currentUrl)?.id
    val downloadedBytes = cachedVideos.sumOf(OfflineVideo::bytesDownloaded)
    val completedCount = cachedVideos.count(OfflineVideo::isPlayableOffline)

    BackHandler(enabled = route != SettingsRoute.OVERVIEW) {
        route = SettingsRoute.OVERVIEW
    }

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
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        AnimatedContent(
            targetState = route,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "settings-route",
        ) { currentRoute ->
            when (currentRoute) {
                SettingsRoute.OVERVIEW -> SettingsOverview(
                    selectedSource = ContentSourceCatalog.find(currentUrl),
                    completedCount = completedCount,
                    downloadedBytes = downloadedBytes,
                    onBack = onBack,
                    onVideoSources = { route = SettingsRoute.VIDEO_SOURCES },
                    onOfflineCache = { route = SettingsRoute.OFFLINE_CACHE },
                )
                SettingsRoute.VIDEO_SOURCES -> VideoSourcesPage(
                    sourceUrl = sourceUrl,
                    selectedSourceId = selectedSourceId,
                    addressError = addressError,
                    onSourceUrl = { sourceUrl = it },
                    onSave = onSave,
                    onBack = { route = SettingsRoute.OVERVIEW },
                )
                SettingsRoute.OFFLINE_CACHE -> OfflineCachePage(
                    cachedVideos = cachedVideos,
                    completedCount = completedCount,
                    downloadedBytes = downloadedBytes,
                    onBack = { route = SettingsRoute.OVERVIEW },
                    onOpenCached = onOpenCached,
                    onClear = { showClearConfirmation = true },
                    onRetry = cacheManager::retry,
                    onRemove = cacheManager::remove,
                )
            }
        }
    }
}

@Composable
private fun SettingsOverview(
    selectedSource: ContentSource?,
    completedCount: Int,
    downloadedBytes: Long,
    onBack: () -> Unit,
    onVideoSources: () -> Unit,
    onOfflineCache: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            top = 22.dp,
            bottom = LocalDockHeight.current + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings-list")
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        item { SettingsPageHeader("设置", "内容源与离线缓存", onBack, "返回我的") }
        item { SettingsSectionLabel("内容") }
        item {
            SettingsNavigationCard {
                SettingsNavigationRow(
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    title = "视频源",
                    supporting = "管理内置来源和自定义站点",
                    value = selectedSource?.name ?: "自定义视频源",
                    onClick = onVideoSources,
                    modifier = Modifier.testTag("settings-video-sources"),
                )
                HorizontalDivider(modifier = Modifier.padding(start = 68.dp))
                SettingsNavigationRow(
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    title = "离线缓存",
                    supporting = "$completedCount 个可离线观看 · ${formatBytes(downloadedBytes)}",
                    onClick = onOfflineCache,
                    modifier = Modifier.testTag("settings-offline-cache"),
                )
            }
        }
        item { SettingsSectionLabel("体验") }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(18.dp),
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("跟随系统外观", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "自动使用 Material You 动态色、日间或夜间主题",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsNavigationCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: @Composable () -> Unit,
    title: String,
    supporting: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center, content = { icon() })
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        value?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 126.dp),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsPageHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    backDescription: String = "返回设置",
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = backDescription)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VideoSourcesPage(
    sourceUrl: String,
    selectedSourceId: String?,
    addressError: String?,
    onSourceUrl: (String) -> Unit,
    onSave: (String) -> Unit,
    onBack: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("") }
    val filteredSources = remember(filter) {
        ContentSourceCatalog.sources.filter {
            filter.isBlank() || it.name.contains(filter, ignoreCase = true) ||
                it.description.contains(filter, ignoreCase = true)
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = LocalDockHeight.current + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).testTag("video-sources-page"),
    ) {
        item { SettingsPageHeader("视频源", "选择后立即刷新首页内容", onBack) }
        item {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("搜索视频源") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (filteredSources.isEmpty()) {
            item {
                Text(
                    "没有匹配的视频源",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                )
            }
        } else {
            items(filteredSources, key = ContentSource::id) { source ->
                SourceOption(
                    source = source,
                    selected = selectedSourceId == source.id,
                    onClick = { onSave(source.baseUrl) },
                )
            }
        }
        item { SettingsSectionLabel("自定义站点") }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(18.dp)) {
                    Text(
                        "仅适用于与默认站点结构兼容的地址。不会在地址中保存账号信息。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = sourceUrl,
                        onValueChange = onSourceUrl,
                        label = { Text("站点地址") },
                        placeholder = { Text("https://example.com") },
                        isError = addressError != null,
                        supportingText = { Text(addressError ?: "请输入完整站点根地址") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { onSave(sourceUrl) },
                        enabled = addressError == null,
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
    }
}

@Composable
private fun OfflineCachePage(
    cachedVideos: List<OfflineVideo>,
    completedCount: Int,
    downloadedBytes: Long,
    onBack: () -> Unit,
    onOpenCached: (VideoItem) -> Unit,
    onClear: () -> Unit,
    onRetry: (OfflineVideo) -> Unit,
    onRemove: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = LocalDockHeight.current + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).testTag("offline-cache-page"),
    ) {
        item {
            SettingsPageHeader(
                title = "离线缓存",
                subtitle = "$completedCount 个可离线观看 · ${formatBytes(downloadedBytes)}",
                onBack = onBack,
            )
        }
        if (cachedVideos.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                        )
                        Text("暂无离线内容", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "播放视频时点击缓存按钮，任务和已下载内容会显示在这里。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        } else {
            item {
                TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("清空全部缓存")
                }
            }
            items(cachedVideos, key = OfflineVideo::id) { cached ->
                OfflineCacheRow(
                    cached = cached,
                    onOpen = { if (cached.isPlayableOffline) onOpenCached(cached.asVideoItem()) },
                    onRetry = { onRetry(cached) },
                    onRemove = { onRemove(cached.id) },
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
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.36f)
            else Color.Transparent,
        ),
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
                tint = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
            Column(Modifier.weight(1f)) {
                Text(source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(source.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Text("使用中", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PlayerScreen(
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun GlassSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val hazeState = LocalHazeState.current
    val materialColor = if (forceDark) Color(0xFF111722) else MaterialTheme.colorScheme.surface
    val surfaceContentColor = if (forceDark) Color.White else MaterialTheme.colorScheme.onSurface
    val outlineColor = if (forceDark) {
        Color.White.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    }
    val glassModifier = if (hazeState != null) {
        Modifier.hazeEffect(
            state = hazeState,
            style = HazeMaterials.thin(materialColor),
        ) {
            blurRadius = 28.dp
            noiseFactor = 0.045f
            inputScale = HazeInputScale.Auto
            blurredEdgeTreatment = BlurredEdgeTreatment(shape)
        }
    } else {
        Modifier.background(materialColor.copy(alpha = 0.88f), shape)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .then(glassModifier)
            .background(materialColor.copy(alpha = 0.18f), shape)
            .border(
                BorderStroke(1.dp, outlineColor),
                shape,
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides surfaceContentColor) {
            content()
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1_024L -> "$bytes B"
    bytes < 1_048_576L -> "%.1f KB".format(bytes / 1_024f)
    bytes < 1_073_741_824L -> "%.1f MB".format(bytes / 1_048_576f)
    else -> "%.2f GB".format(bytes / 1_073_741_824f)
}
