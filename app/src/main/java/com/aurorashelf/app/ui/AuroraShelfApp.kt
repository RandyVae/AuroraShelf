@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
)

package com.aurorashelf.app.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarDefaults
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowSizeClass
import com.aurorashelf.app.LocalIsInPipMode
import com.aurorashelf.app.cast.CastManager
import com.aurorashelf.app.cast.CastState
import com.aurorashelf.app.data.TelemetryManager
import com.aurorashelf.app.game.GameActivity
import com.aurorashelf.app.model.AppDestination
import com.aurorashelf.app.model.VideoItem
import com.aurorashelf.app.ui.theme.AuroraHaptics
import com.aurorashelf.app.ui.theme.AuroraMotion
import kotlinx.coroutines.launch

internal enum class PersonalRoute { OVERVIEW, FAVORITES, HISTORY, SETTINGS, ACCOUNT }

@Composable
fun AuroraShelfApp(
    viewModel: AuroraViewModel = viewModel(),
    comicViewModel: ComicViewModel = viewModel(),
    forumViewModel: ForumViewModel = viewModel(),
    liveViewModel: LiveViewModel = viewModel(),
    introActive: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val comicState by comicViewModel.uiState.collectAsStateWithLifecycle()
    val comicDownloadedChapters by comicViewModel.downloadedChapters.collectAsStateWithLifecycle()
    val forumState by forumViewModel.uiState.collectAsStateWithLifecycle()
    val liveState by liveViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val castManager = remember(context) { CastManager.getInstance(context) }
    val castState by castManager.castState.collectAsStateWithLifecycle()
    val selectedCastDevice by castManager.selectedDevice.collectAsStateWithLifecycle()
    var isCastSheetVisible by rememberSaveable { mutableStateOf(false) }
    val castController = remember(castState, selectedCastDevice) {
        CastController(
            onOpenCastSheet = { isCastSheetVisible = true },
            isCasting = castState is CastState.Connected || selectedCastDevice != null,
        )
    }
    val videosById = remember(state.allKnownVideos) {
        state.allKnownVideos.associateBy(VideoItem::id)
    }
    val favoriteVideos = remember(state.allKnownVideos, state.favoriteIds) {
        state.allKnownVideos.filter { it.id in state.favoriteIds }
    }
    val historyVideos = remember(videosById, state.historyIds) {
        state.historyIds.mapNotNull { id -> videosById[id] }
    }

    var isPlayerFullscreen by rememberSaveable(state.selectedVideo?.id) { mutableStateOf(false) }
    var isLiveFullscreen by rememberSaveable { mutableStateOf(false) }
    var isCurrentVideoPortrait by remember(state.selectedVideo?.id) { mutableStateOf<Boolean?>(null) }
    var isCurrentLivePortrait by remember(liveState.selectedItem?.id) { mutableStateOf<Boolean?>(null) }
    var personalRoute by rememberSaveable { mutableStateOf(PersonalRoute.OVERVIEW) }
    val originalOrientation = remember(activity) { activity?.requestedOrientation }
    val isAnyFullscreen = isPlayerFullscreen || isLiveFullscreen
    val isInPipMode = LocalIsInPipMode.current
    val effectiveDarkTheme = MaterialTheme.colorScheme.background.luminance() <= 0.5f

    // Official Android 17 / Material 3 Adaptive WindowSizeClass
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val isLargeScreenWindow = adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
    )
    val isExpandedScreenWindow = adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
    )

    val telemetrySection = when {
        state.selectedVideo != null -> "video" to "video_play"
        state.isSearchOpen ||
            (state.destination == AppDestination.LIVE && liveState.isSearchOpen) ||
            (state.destination == AppDestination.FORUM && forumState.isSearchOpen) ||
            (state.destination == AppDestination.COMICS && comicState.activeQuery.isNotBlank()) -> "search" to "search"
        state.destination == AppDestination.HOME -> "video" to "video_browse"
        state.destination == AppDestination.COMICS -> {
            if (comicState.reader != null) "comics" to "comic_read" else "comics" to "comic_browse"
        }
        state.destination == AppDestination.LIVE -> {
            if (liveState.selectedItem != null) "live" to "live_watch" else "live" to "live_browse"
        }
        state.destination == AppDestination.FORUM -> {
            if (forumState.selectedPost != null) "forum" to "forum_read" else "forum" to "forum_browse"
        }
        state.destination == AppDestination.ME -> "library" to "library"
        else -> "video" to "video_browse"
    }
    LaunchedEffect(telemetrySection) {
        TelemetryManager.setAppSection(telemetrySection.first, telemetrySection.second)
    }

    LaunchedEffect(
        activity,
        introActive,
        isLargeScreenWindow,
        isPlayerFullscreen,
        isLiveFullscreen,
        isCurrentVideoPortrait,
        isCurrentLivePortrait,
        state.selectedVideo,
        state.destination,
        state.isSearchOpen,
        state.videos.isEmpty(),
        comicState.details,
        comicState.reader,
        comicState.comics.isEmpty(),
        comicState.activeQuery,
        liveState.selectedItem,
        liveState.items.isEmpty(),
        liveState.isSearchOpen,
        liveState.searchQuery,
        effectiveDarkTheme,
    ) {
        activity ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (!introActive) {
            val homeHasDarkHero = state.destination == AppDestination.HOME && state.videos.isNotEmpty()
            val liveHasDarkHero = state.destination == AppDestination.LIVE &&
                liveState.items.isNotEmpty() &&
                !liveState.isSearchOpen &&
                liveState.searchQuery.isBlank()
            val comicsHasDarkHero = state.destination == AppDestination.COMICS &&
                comicState.details == null &&
                comicState.reader == null &&
                comicState.comics.isNotEmpty() &&
                comicState.activeQuery.isBlank()
            val hasDarkStatusBarSurface = effectiveDarkTheme ||
                state.selectedVideo != null ||
                liveState.selectedItem != null ||
                (state.destination == AppDestination.COMICS && comicState.reader != null) ||
                (!state.isSearchOpen && (homeHasDarkHero || liveHasDarkHero || comicsHasDarkHero))
            val hasDarkNavigationBarSurface = effectiveDarkTheme ||
                isAnyFullscreen ||
                (state.destination == AppDestination.COMICS && comicState.reader != null && !state.isSearchOpen)
            insetsController.isAppearanceLightStatusBars = !hasDarkStatusBarSurface
            insetsController.isAppearanceLightNavigationBars = !hasDarkNavigationBarSurface
        }
        // Android 17 (API 37): never request orientation locks on large screens (sw >= 600dp);
        // the platform enforces full resizability and ignores orientation locks on tablets/foldables/desktop.
        if (isPlayerFullscreen && state.selectedVideo != null) {
            if (!isLargeScreenWindow) {
                activity.requestedOrientation = when (isCurrentVideoPortrait) {
                    true -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    false -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    null -> originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else if (isLiveFullscreen) {
            if (!isLargeScreenWindow) {
                activity.requestedOrientation = when (isCurrentLivePortrait) {
                    true -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    false, null -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            if (!isLargeScreenWindow) {
                activity.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
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

    // Top-level fallback back handler for non-predictive states (e.g. non-Home tab -> Home, loading cancel).
    // Child destinations wrapped in PredictiveBackContainer register after this and handle gesture previews first.
    BackHandler(
        enabled = state.selectedVideo != null || state.isSearchOpen || state.destination != AppDestination.HOME ||
            (state.destination == AppDestination.COMICS && (comicState.reader != null || comicState.details != null || comicState.isDetailsLoading || comicState.detailsError != null)) ||
            (state.destination == AppDestination.FORUM && (forumState.selectedPost != null || forumState.isSearchOpen)) ||
            (state.destination == AppDestination.LIVE && (liveState.selectedItem != null || liveState.isSearchOpen)),
    ) {
        when {
            state.selectedVideo != null && isPlayerFullscreen -> isPlayerFullscreen = false
            state.selectedVideo != null -> viewModel.closeVideo()
            state.isSearchOpen -> viewModel.setSearchOpen(false)
            state.destination == AppDestination.COMICS && comicState.reader != null -> comicViewModel.closeReader()
            state.destination == AppDestination.COMICS && comicState.isDetailsLoading -> comicViewModel.cancelOpeningComic()
            state.destination == AppDestination.COMICS && comicState.detailsError != null -> comicViewModel.dismissDetailsError()
            state.destination == AppDestination.COMICS && comicState.details != null -> comicViewModel.closeDetails()
            state.destination == AppDestination.LIVE && isLiveFullscreen -> isLiveFullscreen = false
            state.destination == AppDestination.LIVE && liveState.selectedItem != null -> {
                isLiveFullscreen = false
                liveViewModel.close()
            }
            state.destination == AppDestination.LIVE && liveState.isSearchOpen -> {
                liveViewModel.updateSearchQuery("")
                liveViewModel.setSearchOpen(false)
            }
            state.destination == AppDestination.FORUM && forumState.selectedPost != null -> forumViewModel.closePost()
            state.destination == AppDestination.FORUM && forumState.isSearchOpen -> {
                forumViewModel.updateSearchQuery("")
                forumViewModel.setSearchOpen(false)
            }
            state.destination == AppDestination.ME && personalRoute != PersonalRoute.OVERVIEW -> {
                personalRoute = PersonalRoute.OVERVIEW
            }
            else -> viewModel.selectDestination(AppDestination.HOME)
        }
    }

    // Desktop / hardware keyboard shortcuts (Android 17 tablet & desktop windowing UX)
    val keyboardModifier = Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val modifierHeld = event.isCtrlPressed || event.isMetaPressed
        when {
            modifierHeld && event.key == Key.One -> {
                viewModel.selectDestination(AppDestination.HOME); true
            }
            modifierHeld && event.key == Key.Two -> {
                viewModel.selectDestination(AppDestination.COMICS); true
            }
            modifierHeld && event.key == Key.Three -> {
                viewModel.selectDestination(AppDestination.LIVE); true
            }
            modifierHeld && event.key == Key.Four -> {
                viewModel.selectDestination(AppDestination.FORUM); true
            }
            modifierHeld && event.key == Key.Five -> {
                personalRoute = PersonalRoute.OVERVIEW
                viewModel.selectDestination(AppDestination.ME); true
            }
            modifierHeld && event.key == Key.F -> {
                when (state.destination) {
                    AppDestination.LIVE -> liveViewModel.setSearchOpen(true)
                    AppDestination.FORUM -> forumViewModel.setSearchOpen(true)
                    else -> viewModel.setSearchOpen(true)
                }
                true
            }
            event.key == Key.Escape -> {
                when {
                    isCastSheetVisible -> { isCastSheetVisible = false; true }
                    isPlayerFullscreen -> { isPlayerFullscreen = false; true }
                    state.selectedVideo != null -> { viewModel.closeVideo(); true }
                    state.isSearchOpen -> { viewModel.setSearchOpen(false); true }
                    else -> false
                }
            }
            else -> false
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxSize()
            .then(keyboardModifier),
    ) {
        SharedTransitionLayout {
            val sharedTransitionScope = this
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isAnyFullscreen || isInPipMode) Modifier
                        else Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                    ),
            ) {
                val density = LocalDensity.current
                val isLandscape = maxWidth > maxHeight
                val useNavigationRail = isLandscape || isLargeScreenWindow || maxWidth >= 600.dp
                var dockHeight by remember(useNavigationRail) { mutableStateOf(if (useNavigationRail) 0.dp else 80.dp) }
                val statusHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
                val windowLayout = WindowLayout.calculate(
                    maxWidth.value,
                    (maxHeight - statusHeight - dockHeight).value,
                    isLandscape,
                )

                val showNavigation = !isInPipMode &&
                    state.selectedVideo == null &&
                    !state.isSearchOpen &&
                    comicState.reader == null &&
                    forumState.selectedPost == null &&
                    liveState.selectedItem == null &&
                    !isLiveFullscreen &&
                    !(state.destination == AppDestination.ME && personalRoute == PersonalRoute.ACCOUNT)

                CompositionLocalProvider(
                    LocalWindowLayout provides windowLayout,
                    LocalDockHeight provides dockHeight,
                    LocalCastController provides castController,
                    LocalSharedTransitionScope provides sharedTransitionScope,
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (useNavigationRail && showNavigation) {
                            AuroraWideNavigationRail(
                                selected = state.destination,
                                defaultExpanded = isExpandedScreenWindow,
                                onSelect = { destination ->
                                    if (destination == AppDestination.ME) personalRoute = PersonalRoute.OVERVIEW
                                    viewModel.selectDestination(destination)
                                },
                                modifier = Modifier.testTag("navigation-rail"),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            val navFadeThrough = AuroraMotion.expressiveFadeThrough()
                            AnimatedContent(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (state.isSearchOpen || state.selectedVideo != null) {
                                            Modifier.clearAndSetSemantics { }
                                        } else {
                                            Modifier
                                        },
                                    ),
                                targetState = state.destination,
                                transitionSpec = { navFadeThrough },
                                label = "destination",
                            ) { destination ->
                                CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                                    when (destination) {
                                        AppDestination.HOME -> HomeScreen(
                                            state = state,
                                            onSearch = { viewModel.setSearchOpen(true) },
                                            onSettings = {
                                                personalRoute = PersonalRoute.SETTINGS
                                                viewModel.selectDestination(AppDestination.ME)
                                            },
                                            onOpenAccount = {
                                                personalRoute = PersonalRoute.ACCOUNT
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
                                                onSettings = {
                                                    personalRoute = PersonalRoute.SETTINGS
                                                    viewModel.selectDestination(AppDestination.ME)
                                                },
                                                onDownloadChapter = comicViewModel::downloadChapter,
                                                onDownloadAll = comicViewModel::downloadAllChapters,
                                                downloadedChapters = comicDownloadedChapters,
                                                onRetryComicDownload = comicViewModel::retryComicDownload,
                                                onRemoveComicDownload = comicViewModel::removeComicDownload,
                                                onCancelOpening = comicViewModel::cancelOpeningComic,
                                                onDismissDetailsError = comicViewModel::dismissDetailsError,
                                                onRetryOpening = comicViewModel::retryOpeningComic,
                                            )
                                        }

                                        AppDestination.LIVE -> {
                                            LaunchedEffect(Unit) { liveViewModel.ensureLoaded() }
                                            LiveScreen(
                                                state = liveState,
                                                bottomPadding = LocalDockHeight.current,
                                                isFullscreen = isLiveFullscreen,
                                                onFullscreenChange = { isLiveFullscreen = it },
                                                onVideoOrientationChange = { isCurrentLivePortrait = it },
                                                onCategory = liveViewModel::selectCategory,
                                                onSelectSource = liveViewModel::selectSource,
                                                onSearch = { liveViewModel.setSearchOpen(true) },
                                                onSearchQueryChange = liveViewModel::updateSearchQuery,
                                                onCloseSearch = {
                                                    liveViewModel.updateSearchQuery("")
                                                    liveViewModel.setSearchOpen(false)
                                                },
                                                onRefresh = liveViewModel::refresh,
                                                onLoadMore = liveViewModel::loadMore,
                                                onOpen = liveViewModel::open,
                                                onSelectItem = liveViewModel::open,
                                                onClose = {
                                                    isLiveFullscreen = false
                                                    isCurrentLivePortrait = null
                                                    liveViewModel.close()
                                                },
                                                onSettings = {
                                                    personalRoute = PersonalRoute.SETTINGS
                                                    viewModel.selectDestination(AppDestination.ME)
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
                                            onSelectSource = forumViewModel::selectSource,
                                            onSearch = { forumViewModel.setSearchOpen(true) },
                                            onSearchQueryChange = forumViewModel::updateSearchQuery,
                                            onCloseSearch = {
                                                forumViewModel.updateSearchQuery("")
                                                forumViewModel.setSearchOpen(false)
                                            },
                                            onSettings = {
                                                personalRoute = PersonalRoute.SETTINGS
                                                viewModel.selectDestination(AppDestination.ME)
                                            },
                                        )

                                        AppDestination.ME -> PersonalScreen(
                                            route = personalRoute,
                                            onRoute = { personalRoute = it },
                                            favorites = favoriteVideos,
                                            history = historyVideos,
                                            favoriteIds = state.favoriteIds,
                                            currentUrl = state.sourceUrl,
                                            comicSources = comicState.sources,
                                            selectedComicSourceId = comicState.selectedSourceId,
                                            selectedLiveSourceId = liveState.selectedSourceId,
                                            comicDownloadedChapters = comicDownloadedChapters,
                                            onVideo = viewModel::openVideo,
                                            onFavorite = viewModel::toggleFavorite,
                                            onBrowseHome = { viewModel.selectDestination(AppDestination.HOME) },
                                            onSave = viewModel::saveSourceUrl,
                                            onComicSource = comicViewModel::selectSource,
                                            onLiveSource = liveViewModel::selectSource,
                                            onOpenCached = viewModel::openVideo,
                                            onOpenComicDownload = { chapter ->
                                                comicViewModel.openDownloadedChapter(chapter)
                                                viewModel.selectDestination(AppDestination.COMICS)
                                            },
                                            onRemoveComicDownload = comicViewModel::removeComicDownload,
                                            onRetryComicDownload = comicViewModel::retryComicDownload,
                                            onClearComicDownloads = comicViewModel::clearAllComicDownloads,
                                            onOpenGame = {
                                                context.startActivity(Intent(context, GameActivity::class.java))
                                            },
                                            onAccount = { personalRoute = PersonalRoute.ACCOUNT },
                                        )
                                    }
                                }
                            }

                            if (!useNavigationRail) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = showNavigation,
                                    enter = AuroraMotion.expressiveBarEnter(),
                                    exit = AuroraMotion.expressiveBarExit(),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .testTag("bottom-dock")
                                        .onSizeChanged { size ->
                                            if (size.height > 0) dockHeight = with(density) { size.height.toDp() }
                                        },
                                ) {
                                    AuroraShortNavigationBar(
                                        selected = state.destination,
                                        onSelect = { destination ->
                                            if (destination == AppDestination.ME) personalRoute = PersonalRoute.OVERVIEW
                                            viewModel.selectDestination(destination)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = state.isSearchOpen,
                        modifier = if (state.selectedVideo != null) Modifier.clearAndSetSemantics { } else Modifier,
                        enter = AuroraMotion.expressiveOverlayEnter(),
                        exit = AuroraMotion.expressiveOverlayExit(),
                    ) {
                        PredictiveBackContainer(
                            enabled = state.isSearchOpen && state.selectedVideo == null,
                            onBack = { viewModel.setSearchOpen(false) },
                        ) {
                            SearchScreen(
                                query = state.query,
                                videos = state.searchResults,
                                favoriteIds = state.favoriteIds,
                                isSearching = state.isSearching,
                                message = state.searchMessage,
                                filterLocally = !state.isRemoteSearch,
                                onQuery = viewModel::updateQuery,
                                onSearch = viewModel::search,
                                onClose = { viewModel.setSearchOpen(false) },
                                onVideo = viewModel::openVideo,
                                onFavorite = viewModel::toggleFavorite,
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = state.selectedVideo != null,
                        enter = AuroraMotion.expressiveOverlayEnter(),
                        exit = AuroraMotion.expressiveOverlayExit(),
                    ) {
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            state.selectedVideo?.let { video ->
                                val currentRelatedVideos = remember(video.id, state.videos) {
                                    relatedVideos(video, state.videos)
                                }
                                var isClosing by remember(video.id) { mutableStateOf(false) }
                                val handleCloseVideo: () -> Unit = {
                                    isClosing = true
                                    viewModel.closeVideo()
                                }
                                PredictiveBackContainer(
                                    enabled = !isPlayerFullscreen && !isInPipMode,
                                    onBack = handleCloseVideo,
                                ) {
                                    PlayerScreen(
                                        video = video,
                                        isFavorite = video.id in state.favoriteIds,
                                        isFullscreen = isPlayerFullscreen,
                                        relatedVideos = currentRelatedVideos,
                                        favoriteIds = state.favoriteIds,
                                        onBack = handleCloseVideo,
                                        onFavorite = { viewModel.toggleFavorite(video) },
                                        onFullscreenChange = { isPlayerFullscreen = it },
                                        onVideoOrientationChange = { isCurrentVideoPortrait = it },
                                        onVideo = viewModel::openVideo,
                                        onRelatedFavorite = viewModel::toggleFavorite,
                                        isDismissing = isClosing,
                                    )
                                }
                            }
                        }
                    }

                    if (isCastSheetVisible && !isInPipMode) {
                        CastDeviceSheet(
                            castManager = castManager,
                            onDismissRequest = { isCastSheetVisible = false },
                        )
                    }
                }
            }
        }
    }
}

private fun destinationIcon(destination: AppDestination, selected: Boolean) = when (destination) {
    AppDestination.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    AppDestination.COMICS -> if (selected) Icons.AutoMirrored.Filled.MenuBook else Icons.AutoMirrored.Outlined.MenuBook
    AppDestination.LIVE -> if (selected) Icons.Filled.LiveTv else Icons.Outlined.LiveTv
    AppDestination.FORUM -> if (selected) Icons.Filled.Forum else Icons.Outlined.Forum
    AppDestination.ME -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
}

/**
 * 100% Native Material 3 Expressive [ShortNavigationBar] for compact portrait windows.
 */
@Composable
private fun AuroraShortNavigationBar(
    selected: AppDestination,
    onSelect: (AppDestination) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Box(modifier = Modifier.fillMaxWidth()) {
        // Do not apply fillMaxWidth() directly on ShortNavigationBar: its internal Surface
        // propagates minWidth constraints to EqualWeightContentMeasurePolicy, which would clamp
        // each item to maxWidth. Leaving minWidth unconstrained lets ShortNavigationBar fill
        // maxWidth while splitting all destinations into equal columns.
        ShortNavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            windowInsets = WindowInsets.navigationBars,
            arrangement = ShortNavigationBarDefaults.arrangement,
            modifier = Modifier.testTag("m3-navigation-bar"),
        ) {
            AppDestination.entries.forEach { destination ->
                val isSelected = selected == destination
                ShortNavigationBarItem(
                    selected = isSelected,
                    onClick = {
                        if (!isSelected) {
                            AuroraHaptics.segmentTick(haptic)
                            onSelect(destination)
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = destinationIcon(destination, isSelected),
                            contentDescription = destination.label,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    label = {
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * 100% Native Material 3 Expressive [WideNavigationRail] for landscape phones, tablets, foldables,
 * and Android 17 desktop windows. Collapsible/expandable with smooth Expressive spring physics.
 */
@Composable
private fun AuroraWideNavigationRail(
    selected: AppDestination,
    defaultExpanded: Boolean,
    onSelect: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val railState = rememberWideNavigationRailState(
        initialValue = if (defaultExpanded) WideNavigationRailValue.Expanded else WideNavigationRailValue.Collapsed,
    )
    val isExpanded = railState.targetValue == WideNavigationRailValue.Expanded

    WideNavigationRail(
        state = railState,
        arrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        header = {
            IconButton(
                onClick = {
                    AuroraHaptics.tap(haptic)
                    scope.launch { railState.toggle() }
                },
                modifier = Modifier.padding(start = 12.dp, top = 8.dp),
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Filled.Menu,
                    contentDescription = if (isExpanded) "收起导航栏" else "展开导航栏",
                )
            }
        },
        modifier = modifier,
    ) {
        AppDestination.entries.forEach { destination ->
            val isSelected = selected == destination
            WideNavigationRailItem(
                railExpanded = isExpanded,
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        AuroraHaptics.segmentTick(haptic)
                        onSelect(destination)
                    }
                },
                icon = {
                    Icon(
                        imageVector = destinationIcon(destination, isSelected),
                        contentDescription = destination.label,
                    )
                },
                label = {
                    Text(
                        text = destination.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}
