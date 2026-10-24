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
import androidx.compose.foundation.isSystemInDarkTheme
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

internal enum class PersonalRoute { OVERVIEW, FAVORITES, HISTORY, SETTINGS }

@Composable
fun AuroraShelfApp(
    viewModel: AuroraViewModel = viewModel(),
    comicViewModel: ComicViewModel = viewModel(),
    forumViewModel: ForumViewModel = viewModel(),
    liveViewModel: LiveViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val comicState by comicViewModel.uiState.collectAsStateWithLifecycle()
    val forumState by forumViewModel.uiState.collectAsStateWithLifecycle()
    val liveState by liveViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val hazeState = rememberHazeState()
    val liquidBackdrop = rememberLayerBackdrop()
    var isPlayerFullscreen by rememberSaveable(state.selectedVideo?.id) { mutableStateOf(false) }
    var isLiveFullscreen by rememberSaveable { mutableStateOf(false) }
    var isCurrentVideoPortrait by remember(state.selectedVideo?.id) { mutableStateOf<Boolean?>(null) }
    var personalRoute by rememberSaveable { mutableStateOf(PersonalRoute.OVERVIEW) }
    val originalOrientation = remember(activity) { activity?.requestedOrientation }
    val isAnyFullscreen = isPlayerFullscreen || isLiveFullscreen
    val systemInDarkTheme = isSystemInDarkTheme()

    LaunchedEffect(
        activity,
        isPlayerFullscreen,
        isLiveFullscreen,
        isCurrentVideoPortrait,
        state.selectedVideo,
        state.destination,
        liveState.selectedItem,
        systemInDarkTheme,
    ) {
        activity ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        // The live player draws a fixed black scrim edge-to-edge; dark status/navigation icons
        // would become invisible in the light theme, so force light icons while it is open.
        val darkScrimVisible = state.destination == AppDestination.LIVE && liveState.selectedItem != null
        insetsController.isAppearanceLightStatusBars = !systemInDarkTheme && !darkScrimVisible
        insetsController.isAppearanceLightNavigationBars = !systemInDarkTheme && !darkScrimVisible
        if (isPlayerFullscreen && state.selectedVideo != null) {
            activity.requestedOrientation = when (isCurrentVideoPortrait) {
                true -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                false -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                null -> originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else if (isLiveFullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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
            comicState.reader != null || comicState.details != null || forumState.selectedPost != null ||
            liveState.selectedItem != null,
    ) {
        when {
            state.selectedVideo != null && isPlayerFullscreen -> isPlayerFullscreen = false
            state.selectedVideo != null -> viewModel.closeVideo()
            state.isSearchOpen -> viewModel.setSearchOpen(false)
            state.destination == AppDestination.COMICS && comicState.reader != null -> comicViewModel.closeReader()
            state.destination == AppDestination.COMICS && comicState.details != null -> comicViewModel.closeDetails()
            state.destination == AppDestination.LIVE && isLiveFullscreen -> isLiveFullscreen = false
            state.destination == AppDestination.LIVE && liveState.selectedItem != null -> {
                isLiveFullscreen = false
                liveViewModel.close()
            }
            state.destination == AppDestination.FORUM && forumState.selectedPost != null -> forumViewModel.closePost()
            state.destination == AppDestination.ME && personalRoute != PersonalRoute.OVERVIEW -> {
                personalRoute = PersonalRoute.OVERVIEW
            }
            else -> viewModel.selectDestination(AppDestination.HOME)
        }
    }

    Surface(
        color = if (isAnyFullscreen) Color.Black else MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize(),
    ) {
      BoxWithConstraints(
          modifier = Modifier
              .fillMaxSize()
              .then(
                  if (isAnyFullscreen) Modifier
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
                .widthIn(max = if (isAnyFullscreen) maxWidth else if (windowLayout.compactDock) 760.dp else 680.dp)
                .fillMaxSize()
                .layerBackdrop(liquidBackdrop)
                .hazeSource(state = hazeState)
                .then(if (windowLayout.compactDock && !isAnyFullscreen) Modifier.padding(start = 80.dp) else Modifier)
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

                AppDestination.COMICS -> {
                    // Create/load the comic feed only after the user opens this tab.
                    LaunchedEffect(Unit) { comicViewModel.ensureLoaded() }
                    ComicScreen(
                        state = comicState,
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
                }

                AppDestination.LIVE -> {
                    // Stripchat is only contacted once the live tab is opened.
                    LaunchedEffect(Unit) { liveViewModel.ensureLoaded() }
                    LiveScreen(
                        state = liveState,
                        bottomPadding = LocalDockHeight.current,
                        isFullscreen = isLiveFullscreen,
                        onFullscreenChange = { isLiveFullscreen = it },
                        onCategory = liveViewModel::selectCategory,
                        onRefresh = liveViewModel::refresh,
                        onLoadMore = liveViewModel::loadMore,
                        onOpen = liveViewModel::open,
                        onClose = {
                            isLiveFullscreen = false
                            liveViewModel.close()
                        },
                    )
                }

                AppDestination.FORUM -> ForumScreen(
                    state = forumState,
                    bottomPadding = LocalDockHeight.current,
                    onEnsureLoaded = forumViewModel::ensureLoaded,
                    onRefresh = forumViewModel::refresh,
                    onLoadMore = forumViewModel::loadMore,
                    onPost = forumViewModel::open,
                    onClosePost = forumViewModel::closePost,
                )

                AppDestination.ME -> PersonalScreen(
                    route = personalRoute,
                    onRoute = { personalRoute = it },
                    favorites = state.allKnownVideos.filter { it.id in state.favoriteIds },
                    history = state.historyIds.mapNotNull { id -> state.allKnownVideos.find { it.id == id } },
                    favoriteIds = state.favoriteIds,
                    currentUrl = state.sourceUrl,
                    comicSources = comicState.sources,
                    selectedComicSourceId = comicState.selectedSourceId,
                    onVideo = viewModel::openVideo,
                    onFavorite = viewModel::toggleFavorite,
                    onBrowseHome = { viewModel.selectDestination(AppDestination.HOME) },
                    onSave = viewModel::saveSourceUrl,
                    onComicSource = comicViewModel::selectSource,
                    onOpenCached = viewModel::openVideo,
                )
            }
        }

        if (windowLayout.compactDock) {
            AnimatedVisibility(
                visible = state.selectedVideo == null && !state.isSearchOpen && comicState.reader == null &&
                    forumState.selectedPost == null && liveState.selectedItem == null && !isLiveFullscreen,
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
                visible = state.selectedVideo == null && !state.isSearchOpen && comicState.reader == null &&
                    forumState.selectedPost == null && liveState.selectedItem == null && !isLiveFullscreen,
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
                // Network search results are already matched by the site; re-filtering them
                // locally drops legitimate tag/alias hits.
                filterLocally = !state.isRemoteSearch,
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
    // The floating dock has a fixed visual height by design; let it grow with the system font so
    // icon + label still fit at 200% instead of drawing outside the glass capsule.
    val fontScale = LocalDensity.current.fontScale
    val extraDockHeight = ((fontScale - 1f).coerceIn(0f, 1f) * 44f).dp
    LiquidBottomNavigation(
        selectedTabIndex = lastNavigationIndex,
        onTabSelected = { index -> onSelect(navigationDestinations[index]) },
        onTabPreviewed = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        },
        backdrop = backdrop,
        tabSlots = listOf(0, 1, 2, 3, 4),
        visualItemCount = 5,
        contentHeight = 64.dp + extraDockHeight,
        lensHeight = 56.dp + extraDockHeight,
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp + extraDockHeight)
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
