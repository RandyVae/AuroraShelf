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
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.stateDescription
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
import com.aurorashelf.app.model.LiveSourceCatalog
import com.aurorashelf.app.model.LiveSourceInfo
import com.aurorashelf.app.model.VideoItem
import com.aurorashelf.app.data.AppPreferences
import com.aurorashelf.app.data.SourceAddress
import com.aurorashelf.app.data.PlaybackEpisode
import com.aurorashelf.app.data.PlaybackResolver
import com.aurorashelf.app.data.OfflineCacheManager
import com.aurorashelf.app.data.OfflineCacheState
import com.aurorashelf.app.data.OfflineVideo
import com.aurorashelf.app.ui.liquid.LiquidBottomNavigation
import com.aurorashelf.app.ui.liquid.LiquidNavigationTab
import com.aurorashelf.app.ui.theme.AuroraCoral
import com.aurorashelf.app.ui.theme.AuroraImmersiveBackground
import com.aurorashelf.app.ui.theme.AuroraMediaLabel
import com.aurorashelf.app.ui.theme.AuroraMediaMuted
import com.aurorashelf.app.ui.theme.AuroraMediaPlaceholder
import com.aurorashelf.app.ui.theme.AuroraMediaSurface
import com.aurorashelf.app.ui.theme.AuroraProfileAccent
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

private enum class SettingsRoute { OVERVIEW, VIDEO_SOURCES, COMIC_SOURCES, LIVE_SOURCES, OFFLINE_CACHE }

@Composable
internal fun PersonalScreen(
    route: PersonalRoute,
    onRoute: (PersonalRoute) -> Unit,
    favorites: List<VideoItem>,
    history: List<VideoItem>,
    favoriteIds: Set<String>,
    currentUrl: String,
    comicSources: List<ComicSourceInfo>,
    selectedComicSourceId: String,
    selectedLiveSourceId: String = LiveSourceCatalog.STRIPCHAT.id,
    onVideo: (VideoItem) -> Unit,
    onFavorite: (VideoItem) -> Unit,
    onBrowseHome: () -> Unit,
    onSave: (String) -> Unit,
    onComicSource: (String) -> Unit,
    onLiveSource: (String) -> Unit = {},
    onOpenCached: (VideoItem) -> Unit,
) {
    BackHandler(enabled = route != PersonalRoute.OVERVIEW) { onRoute(PersonalRoute.OVERVIEW) }
    Surface(color = AuroraImmersiveBackground, modifier = Modifier.fillMaxSize()) {
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
                    favorites = favorites,
                    history = history,
                    onVideo = onVideo,
                    onFavorites = { onRoute(PersonalRoute.FAVORITES) },
                    onHistory = { onRoute(PersonalRoute.HISTORY) },
                    onSettings = { onRoute(PersonalRoute.SETTINGS) },
                )
                PersonalRoute.FAVORITES -> LibraryScreen(
                    title = "我的收藏",
                    emptyMessage = "收藏的视频会出现在这里",
                    emptyActionLabel = "去首页看看",
                    onEmptyAction = onBrowseHome,
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
                    emptyActionLabel = "去首页看看",
                    onEmptyAction = onBrowseHome,
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
                    comicSources = comicSources,
                    selectedComicSourceId = selectedComicSourceId,
                    selectedLiveSourceId = selectedLiveSourceId,
                    onSave = onSave,
                    onComicSource = onComicSource,
                    onLiveSource = onLiveSource,
                    onOpenCached = onOpenCached,
                    onBrowseHome = onBrowseHome,
                    onBack = { onRoute(PersonalRoute.OVERVIEW) },
                )
            }
        }
    }
}

@Composable
private fun PersonalOverview(
    favoriteCount: Int,
    historyCount: Int,
    currentSource: String,
    favorites: List<VideoItem> = emptyList(),
    history: List<VideoItem> = emptyList(),
    onVideo: (VideoItem) -> Unit = {},
    onFavorites: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
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
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                NetflixProfileAvatar(
                    sizeDp = 56.dp,
                    contentDescription = "站点设置",
                    onClick = onSettings,
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        "我的",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        color = Color.White,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "片单、历史与应用设置",
                        color = AuroraMediaMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (favorites.isNotEmpty()) {
            item {
                NetflixSectionHeader(
                    title = "我的片单",
                    actionText = "全部 ($favoriteCount)",
                    onAction = onFavorites,
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(favorites.take(10)) { video ->
                        NetflixPosterShelfCard(
                            video = video,
                            onClick = { onVideo(video) },
                        )
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            item {
                NetflixSectionHeader(
                    title = "继续观看",
                    actionText = "全部 ($historyCount)",
                    onAction = onHistory,
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(history.take(10)) { video ->
                        NetflixPosterShelfCard(
                            video = video,
                            onClick = { onVideo(video) },
                        )
                    }
                }
            }
        }

        item { SettingsSectionLabel("你的内容") }
        item {
            SettingsNavigationCard {
                SettingsNavigationRow(
                    icon = { Icon(Icons.Default.Favorite, contentDescription = null, tint = AuroraCoral) },
                    title = "收藏",
                    supporting = "稍后继续观看已保存的内容",
                    value = "$favoriteCount 个",
                    onClick = onFavorites,
                    modifier = Modifier.testTag("personal-favorites-entry"),
                )
                HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = Color(0xFF262626))
                SettingsNavigationRow(
                    icon = { Icon(Icons.Default.History, contentDescription = null, tint = Color.White) },
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
                    icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White) },
                    title = "设置",
                    supporting = "视频源、漫画源与离线缓存",
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
    emptyActionLabel: String,
    onEmptyAction: () -> Unit,
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
                EmptyState(
                    message = emptyMessage,
                    icon = emptyIcon,
                    actionLabel = emptyActionLabel,
                    onAction = onEmptyAction,
                )
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
private fun SettingsScreen(
    currentUrl: String,
    comicSources: List<ComicSourceInfo>,
    selectedComicSourceId: String,
    selectedLiveSourceId: String,
    onSave: (String) -> Unit,
    onComicSource: (String) -> Unit,
    onLiveSource: (String) -> Unit,
    onOpenCached: (VideoItem) -> Unit,
    onBrowseHome: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val cacheManager = remember(context) { OfflineCacheManager.get(context) }
    val cachedVideos by cacheManager.entries.collectAsStateWithLifecycle()
    val preferences = remember(context) { AppPreferences(context) }
    var videoPreloadEnabled by remember { mutableStateOf(preferences.videoPreloadEnabled) }
    var introAnimationEnabled by remember { mutableStateOf(preferences.introAnimationEnabled) }
    var introSoundEnabled by remember { mutableStateOf(preferences.introSoundEnabled) }
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
                TextButton(
                    onClick = {
                        cacheManager.clearAll()
                        showClearConfirmation = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text("取消") }
            },
        )
    }
    Surface(
        color = AuroraImmersiveBackground,
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
                    selectedComicSource = comicSources.find { it.id == selectedComicSourceId },
                    selectedLiveSource = LiveSourceCatalog.find(selectedLiveSourceId),
                    completedCount = completedCount,
                    downloadedBytes = downloadedBytes,
                    videoPreloadEnabled = videoPreloadEnabled,
                    onVideoPreloadChange = { enabled ->
                        videoPreloadEnabled = enabled
                        preferences.videoPreloadEnabled = enabled
                    },
                    introAnimationEnabled = introAnimationEnabled,
                    onIntroAnimationChange = { enabled ->
                        introAnimationEnabled = enabled
                        preferences.introAnimationEnabled = enabled
                    },
                    introSoundEnabled = introSoundEnabled,
                    onIntroSoundChange = { enabled ->
                        introSoundEnabled = enabled
                        preferences.introSoundEnabled = enabled
                    },
                    onBack = onBack,
                    onVideoSources = { route = SettingsRoute.VIDEO_SOURCES },
                    onComicSources = { route = SettingsRoute.COMIC_SOURCES },
                    onLiveSources = { route = SettingsRoute.LIVE_SOURCES },
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
                SettingsRoute.COMIC_SOURCES -> ComicSourcesPage(
                    sources = comicSources,
                    selectedSourceId = selectedComicSourceId,
                    onSource = onComicSource,
                    onBack = { route = SettingsRoute.OVERVIEW },
                )
                SettingsRoute.LIVE_SOURCES -> LiveSourcesPage(
                    sources = LiveSourceCatalog.sources,
                    selectedSourceId = selectedLiveSourceId,
                    onSource = onLiveSource,
                    onBack = { route = SettingsRoute.OVERVIEW },
                )
                SettingsRoute.OFFLINE_CACHE -> OfflineCachePage(
                    cachedVideos = cachedVideos,
                    completedCount = completedCount,
                    downloadedBytes = downloadedBytes,
                    onBack = { route = SettingsRoute.OVERVIEW },
                    onOpenCached = onOpenCached,
                    onBrowseHome = onBrowseHome,
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
    selectedComicSource: ComicSourceInfo?,
    selectedLiveSource: LiveSourceInfo?,
    completedCount: Int,
    downloadedBytes: Long,
    videoPreloadEnabled: Boolean,
    onVideoPreloadChange: (Boolean) -> Unit,
    introAnimationEnabled: Boolean,
    onIntroAnimationChange: (Boolean) -> Unit,
    introSoundEnabled: Boolean,
    onIntroSoundChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onVideoSources: () -> Unit,
    onComicSources: () -> Unit,
    onLiveSources: () -> Unit,
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
                    icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                    title = "漫画源",
                    supporting = "管理漫画内容来源",
                    value = selectedComicSource?.name ?: "禁漫天堂",
                    onClick = onComicSources,
                    modifier = Modifier.testTag("settings-comic-sources"),
                )
                HorizontalDivider(modifier = Modifier.padding(start = 68.dp))
                SettingsNavigationRow(
                    icon = { Icon(Icons.Default.LiveTv, contentDescription = null) },
                    title = "直播源",
                    supporting = "管理全球真人与校园风情直播",
                    value = selectedLiveSource?.name ?: "Stripchat",
                    onClick = onLiveSources,
                    modifier = Modifier.testTag("settings-live-sources"),
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
            SettingsInfoCard(
                icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                title = "跟随系统外观",
                supporting = "自动使用 Material You 动态色、日间或夜间主题",
            )
        }
        item {
            SettingsSwitchRow(
                icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                title = "视频预加载",
                supporting = "仅 Wi-Fi 下预取首页视频详情与下一集地址",
                checked = videoPreloadEnabled,
                onCheckedChange = onVideoPreloadChange,
                testTag = "settings-video-preload",
            )
        }
        item {
            IntroPreferenceRow(
                icon = { Icon(Icons.Filled.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = "开屏动画",
                supporting = "冷启动时播放电影感品牌开场，点击可跳过",
                checked = introAnimationEnabled,
                onCheckedChange = onIntroAnimationChange,
                testTag = "settings-intro-animation",
            )
        }
        item {
            IntroPreferenceRow(
                icon = { Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = "开屏音效",
                supporting = "播放原创电影感音效；静音或媒体音量为 0 时不播放",
                checked = introSoundEnabled,
                onCheckedChange = onIntroSoundChange,
                testTag = "settings-intro-sound",
            )
        }
    }
}

@Composable
private fun SettingsInfoCard(
    icon: @Composable () -> Unit,
    title: String,
    supporting: String,
) {
    SettingsCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(18.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                icon()
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: @Composable () -> Unit,
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    SettingsCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .testTag(testTag),
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                icon()
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            // The row owns the switch semantics; this visual switch mirrors its state.
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun IntroPreferenceRow(
    icon: @Composable () -> Unit,
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    SettingsSwitchRow(
        icon = icon,
        title = title,
        supporting = supporting,
        checked = checked,
        onCheckedChange = onCheckedChange,
        testTag = testTag,
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        color = AuroraMediaSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF262626)),
        modifier = Modifier.fillMaxWidth(),
        content = content,
    )
}

@Composable
private fun SettingsSectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
        color = AuroraCoral,
        modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsNavigationCard(content: @Composable () -> Unit) {
    SettingsCard {
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
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF262626),
            contentColor = Color.White,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center, content = { icon() })
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                supporting,
                color = AuroraMediaMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        value?.let {
            Text(
                it,
                color = AuroraCoral,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 126.dp),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = AuroraMediaMuted,
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
            Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = backDescription, tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.semantics { heading() },
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AuroraMediaMuted)
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
    val directSources = remember(filteredSources) { filteredSources.filter { it.isDirectDomestic } }
    val overseasSources = remember(filteredSources) { filteredSources.filter { !it.isDirectDomestic } }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = LocalDockHeight.current + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).testTag("video-sources-page"),
    ) {
        item { SettingsPageHeader("视频源", "选择后立即刷新首页内容", onBack) }
        item {
            CenteredSearchBar(
                query = filter,
                onQueryChange = { filter = it },
                placeholder = "搜索视频源",
                onSearch = {},
                onClear = { filter = "" },
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
            if (directSources.isNotEmpty()) {
                item(key = "section-direct-heading") {
                    SettingsSectionLabel("精选推荐（大陆直连稳定）")
                }
                items(directSources, key = ContentSource::id) { source ->
                    SourceOption(
                        source = source,
                        selected = selectedSourceId == source.id,
                        onClick = { onSave(source.baseUrl) },
                    )
                }
            }
            if (overseasSources.isNotEmpty()) {
                item(key = "section-overseas-heading") {
                    SettingsSectionLabel("扩展视频源（海外/国际线路）")
                }
                items(overseasSources, key = ContentSource::id) { source ->
                    SourceOption(
                        source = source,
                        selected = selectedSourceId == source.id,
                        onClick = { onSave(source.baseUrl) },
                    )
                }
            }
        }
        item { SettingsSectionLabel("自定义站点") }
        item {
            Surface(
                color = AuroraMediaSurface,
                contentColor = Color.White,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFF262626)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(18.dp)) {
                    Text(
                        "仅适用于与默认站点结构兼容的地址。不会在地址中保存账号信息。",
                        color = AuroraMediaMuted,
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
private fun ComicSourcesPage(
    sources: List<ComicSourceInfo>,
    selectedSourceId: String,
    onSource: (String) -> Unit,
    onBack: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("") }
    val filteredSources = remember(filter, sources) {
        sources.filter { source ->
            filter.isBlank() || source.name.contains(filter, ignoreCase = true) ||
                source.description.contains(filter, ignoreCase = true)
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 14.dp,
            bottom = LocalDockHeight.current + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("comic-sources-page"),
    ) {
        item { SettingsPageHeader("漫画源", "选择后立即刷新漫画内容", onBack) }
        item {
            CenteredSearchBar(
                query = filter,
                onQueryChange = { filter = it },
                placeholder = "搜索漫画源",
                onSearch = {},
                onClear = { filter = "" },
            )
        }
        if (filteredSources.isEmpty()) {
            item {
                Text(
                    "没有匹配的漫画源",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                )
            }
        } else {
            items(filteredSources, key = ComicSourceInfo::id) { source ->
                ComicSourceOption(
                    source = source,
                    selected = selectedSourceId == source.id,
                    onClick = { onSource(source.id) },
                )
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
    onBrowseHome: () -> Unit,
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
                    color = AuroraMediaSurface,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color(0xFF262626)),
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
                            tint = AuroraCoral,
                            modifier = Modifier.size(36.dp),
                        )
                        Text("暂无离线内容", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            "播放视频时点击缓存按钮，任务和已下载内容会显示在这里。",
                            color = AuroraMediaMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = onBrowseHome,
                            colors = ButtonDefaults.buttonColors(containerColor = AuroraCoral, contentColor = Color.White),
                        ) { Text("去首页缓存视频") }
                    }
                }
            }
        } else {
            item {
                TextButton(
                    onClick = onClear,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
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
    // HIG G3: expose the download state (and progress when downloading) as a readable
    // description so screen readers do not have to infer it from the small detail line.
    val cacheStatusDescription = when (cached.state) {
        OfflineCacheState.RESOLVING -> "正在解析缓存地址"
        OfflineCacheState.QUEUED -> "等待缓存"
        OfflineCacheState.DOWNLOADING -> "正在缓存，已完成 ${cached.percentDownloaded.toInt()}%"
        OfflineCacheState.COMPLETED -> "缓存已完成，可离线观看"
        OfflineCacheState.FAILED -> "缓存失败，可重试"
        OfflineCacheState.REMOVING -> "正在删除缓存"
    }
    Surface(
        onClick = onOpen,
        enabled = cached.isPlayableOffline,
        color = AuroraMediaSurface,
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color(0xFF262626)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { stateDescription = cacheStatusDescription },
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
                    color = Color.White,
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
                Text(detail, color = AuroraMediaMuted, style = MaterialTheme.typography.bodySmall)
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
        color = if (selected) Color(0xFF1E2838) else AuroraMediaSurface,
        contentColor = if (selected) AuroraProfileAccent else Color.White,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            if (selected) AuroraProfileAccent.copy(alpha = 0.55f) else Color(0xFF262626),
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
                tint = if (selected) AuroraProfileAccent else Color.White,
            )
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
                    val badgeBg = if (source.isDirectDomestic) Color(0x1F34C759) else Color(0x1F0A84FF)
                    val badgeBorder = if (source.isDirectDomestic) Color(0x4034C759) else Color(0x400A84FF)
                    val badgeText = if (source.isDirectDomestic) Color(0xFF34C759) else Color(0xFF64D2FF)
                    val badgeLabel = if (source.isDirectDomestic) "直连" else "海外"
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, badgeBorder),
                    ) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = badgeText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(source.description, style = MaterialTheme.typography.bodySmall, color = AuroraMediaMuted)
            }
            if (selected) {
                Text("使用中", style = MaterialTheme.typography.labelMedium, color = AuroraProfileAccent)
            }
        }
    }
}

@Composable
private fun ComicSourceOption(source: ComicSourceInfo, selected: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (!selected) onClick()
        },
        color = if (selected) Color(0xFF1E2838) else AuroraMediaSurface,
        contentColor = if (selected) AuroraProfileAccent else Color.White,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            if (selected) AuroraProfileAccent.copy(alpha = 0.55f) else Color(0xFF262626),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(
                if (selected) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = if (selected) AuroraProfileAccent else Color.White,
            )
            Column(Modifier.weight(1f)) {
                Text(source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(
                    source.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroraMediaMuted,
                )
            }
            Text(
                when {
                    selected -> "使用中"
                    source.requiresAuthentication -> "需登录"
                    else -> ""
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) AuroraProfileAccent else AuroraCoral,
            )
        }
    }
}

@Composable
private fun LiveSourcesPage(
    sources: List<LiveSourceInfo>,
    selectedSourceId: String,
    onSource: (String) -> Unit,
    onBack: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("") }
    val filteredSources = remember(filter, sources) {
        sources.filter { source ->
            filter.isBlank() || source.name.contains(filter, ignoreCase = true) ||
                source.description.contains(filter, ignoreCase = true)
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 14.dp,
            bottom = LocalDockHeight.current + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("live-sources-page"),
    ) {
        item { SettingsPageHeader("直播源", "选择后立即刷新直播内容", onBack) }
        item {
            CenteredSearchBar(
                query = filter,
                onQueryChange = { filter = it },
                placeholder = "搜索直播源",
                onSearch = {},
                onClear = { filter = "" },
            )
        }
        if (filteredSources.isEmpty()) {
            item {
                Text(
                    "没有匹配的直播源",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                )
            }
        } else {
            items(filteredSources, key = LiveSourceInfo::id) { source ->
                LiveSourceOption(
                    source = source,
                    selected = selectedSourceId == source.id,
                    onClick = { onSource(source.id) },
                )
            }
        }
    }
}

@Composable
private fun LiveSourceOption(
    source: LiveSourceInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (selected) AuroraProfileAccent.copy(alpha = 0.85f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(
                if (selected) Icons.Default.CheckCircle else Icons.Default.LiveTv,
                contentDescription = null,
                tint = if (selected) AuroraProfileAccent else Color.White,
            )
            Column(Modifier.weight(1f)) {
                Text(source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(
                    source.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroraMediaMuted,
                )
            }
            if (selected) {
                Text(
                    "使用中",
                    style = MaterialTheme.typography.labelMedium,
                    color = AuroraProfileAccent,
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1_024L -> "$bytes B"
    bytes < 1_048_576L -> "%.1f KB".format(bytes / 1_024f)
    bytes < 1_073_741_824L -> "%.1f MB".format(bytes / 1_048_576f)
    else -> "%.2f GB".format(bytes / 1_073_741_824f)
}
