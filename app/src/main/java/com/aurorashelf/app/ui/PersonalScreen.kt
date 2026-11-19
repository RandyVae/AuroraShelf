@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.aurorashelf.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurorashelf.app.BuildConfig
import com.aurorashelf.app.R
import com.aurorashelf.app.data.AccountRepository
import com.aurorashelf.app.data.AccountState
import com.aurorashelf.app.data.AppPreferences
import com.aurorashelf.app.data.OfflineCacheManager
import com.aurorashelf.app.data.OfflineCacheState
import com.aurorashelf.app.data.OfflineVideo
import com.aurorashelf.app.data.SourceAddress
import com.aurorashelf.app.data.ThemeMode
import com.aurorashelf.app.data.comic.ComicDownloadStatus
import com.aurorashelf.app.data.comic.DownloadedComicChapter
import com.aurorashelf.app.model.ComicSourceInfo
import com.aurorashelf.app.model.ContentSource
import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.LiveSourceCatalog
import com.aurorashelf.app.model.LiveSourceInfo
import com.aurorashelf.app.model.VideoItem
import com.aurorashelf.app.ui.theme.AuroraHaptics
import com.aurorashelf.app.ui.theme.AuroraMotion
import com.aurorashelf.app.ui.theme.LocalAuroraSemanticColors

private enum class SettingsRoute { OVERVIEW, VIDEO_SOURCES, COMIC_SOURCES, LIVE_SOURCES, OFFLINE_CACHE }

private enum class OfflineMediaTab(val label: String) {
    VIDEO("视频"),
    COMIC("漫画"),
}

private enum class OfflineStatusFilter(val label: String) {
    ALL("全部"),
    DOWNLOADING("下载中"),
    COMPLETED("已完成"),
    FAILED("失败"),
}

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
    comicDownloadedChapters: List<DownloadedComicChapter> = emptyList(),
    onVideo: (VideoItem) -> Unit,
    onFavorite: (VideoItem) -> Unit,
    onBrowseHome: () -> Unit,
    onSave: (String) -> Unit,
    onComicSource: (String) -> Unit,
    onLiveSource: (String) -> Unit = {},
    onOpenCached: (VideoItem) -> Unit,
    onOpenComicDownload: (DownloadedComicChapter) -> Unit = {},
    onRemoveComicDownload: (String) -> Unit = {},
    onRetryComicDownload: (String) -> Unit = {},
    onClearComicDownloads: () -> Unit = {},
    onOpenGame: () -> Unit = {},
    onAccount: () -> Unit = {},
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        val personalRouteTransition = AuroraMotion.expressiveFadeThrough()
        PredictiveBackContainer(
            enabled = route != PersonalRoute.OVERVIEW,
            onBack = { onRoute(PersonalRoute.OVERVIEW) },
        ) {
            AnimatedContent(
                targetState = route,
                transitionSpec = { personalRouteTransition },
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
                        onOpenGame = onOpenGame,
                        onAccount = onAccount,
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
                        comicDownloadedChapters = comicDownloadedChapters,
                        onSave = onSave,
                        onComicSource = onComicSource,
                        onLiveSource = onLiveSource,
                        onOpenCached = onOpenCached,
                        onOpenComicDownload = onOpenComicDownload,
                        onRemoveComicDownload = onRemoveComicDownload,
                        onRetryComicDownload = onRetryComicDownload,
                        onClearComicDownloads = onClearComicDownloads,
                        onBrowseHome = onBrowseHome,
                        onBack = { onRoute(PersonalRoute.OVERVIEW) },
                    )
                    PersonalRoute.ACCOUNT -> AccountScreen(onBack = { onRoute(PersonalRoute.OVERVIEW) })
                }
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
    onOpenGame: () -> Unit = {},
    onAccount: () -> Unit = {},
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                AuroraProfileAvatar(
                    sizeDp = 56.dp,
                    contentDescription = "站点设置",
                    onClick = onSettings,
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        "我的",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "片单、历史与应用设置",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (favorites.isNotEmpty()) {
            item {
                AuroraSectionHeader(
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
                    items(favorites.take(10), key = { "fav-${it.id}" }) { video ->
                        AuroraPosterCard(
                            video = video,
                            onClick = { onVideo(video) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            item {
                AuroraSectionHeader(
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
                    items(history.take(10), key = { "hist-${it.id}" }) { video ->
                        AuroraPosterCard(
                            video = video,
                            onClick = { onVideo(video) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        item { SettingsSectionLabel("你的内容") }
        item {
            SettingsNavigationCard {
                SettingsNavigationRow(
                    icon = { Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "收藏",
                    supporting = "稍后继续观看已保存的内容",
                    value = "$favoriteCount 个",
                    onClick = onFavorites,
                    modifier = Modifier.testTag("personal-favorites-entry"),
                )
                HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsNavigationRow(
                    icon = { Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                    title = "历史",
                    supporting = "查看最近播放过的视频",
                    value = "$historyCount 条",
                    onClick = onHistory,
                    modifier = Modifier.testTag("personal-history-entry"),
                )
            }
        }
        item { SettingsSectionLabel("娱乐") }
        item {
            SettingsNavigationCard {
                SettingsNavigationRow(
                    icon = { Icon(Icons.Default.SportsEsports, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "游戏",
                    supporting = "欲望女神（Lust Goddess）· 战术 RPG",
                    value = "开始游戏",
                    onClick = onOpenGame,
                    modifier = Modifier.testTag("personal-game-entry"),
                )
            }
        }
        item { SettingsSectionLabel("应用") }
        item {
            val context = LocalContext.current
            val accountState by remember(context) { AccountRepository.get(context) }.state.collectAsStateWithLifecycle()
            val signedInProfile = (accountState as? AccountState.SignedIn)?.profile
            SettingsNavigationCard {
                SettingsNavigationRow(
                    icon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = signedInProfile?.displayName?.ifBlank { "账号" } ?: "账号",
                    supporting = signedInProfile?.email ?: "注册登录并为未来会员服务保留权益接口",
                    value = when {
                        signedInProfile == null -> "免费注册"
                        signedInProfile.isVerified -> "已验证"
                        else -> "待验证"
                    },
                    onClick = onAccount,
                    modifier = Modifier.testTag("personal-account-entry"),
                )
                HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsNavigationRow(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    modifier = Modifier.animateItem(),
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
    comicDownloadedChapters: List<DownloadedComicChapter>,
    onSave: (String) -> Unit,
    onComicSource: (String) -> Unit,
    onLiveSource: (String) -> Unit,
    onOpenCached: (VideoItem) -> Unit,
    onOpenComicDownload: (DownloadedComicChapter) -> Unit,
    onRemoveComicDownload: (String) -> Unit,
    onRetryComicDownload: (String) -> Unit,
    onClearComicDownloads: () -> Unit,
    onBrowseHome: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val cacheManager = remember(context) { OfflineCacheManager.get(context) }
    val cachedVideos by cacheManager.entries.collectAsStateWithLifecycle()
    val preferences = remember(context) { AppPreferences.get(context) }
    val themeMode by preferences.themeModeFlow.collectAsStateWithLifecycle()
    var videoPreloadEnabled by remember { mutableStateOf(preferences.videoPreloadEnabled) }
    var introAnimationEnabled by remember { mutableStateOf(preferences.introAnimationEnabled) }
    var introSoundEnabled by remember { mutableStateOf(preferences.introSoundEnabled) }
    var sourceUrl by remember(currentUrl) { mutableStateOf(currentUrl) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var clearTargetIsComic by remember { mutableStateOf(false) }
    var route by rememberSaveable { mutableStateOf(SettingsRoute.OVERVIEW) }
    val addressError = SourceAddress.error(sourceUrl)
    val selectedSourceId = ContentSourceCatalog.find(currentUrl)?.id
    val downloadedBytes = cachedVideos.sumOf(OfflineVideo::bytesDownloaded)
    val completedCount = cachedVideos.count(OfflineVideo::isPlayableOffline)
    val comicDownloadedBytes = comicDownloadedChapters.sumOf(DownloadedComicChapter::totalBytes)
    val completedComicCount = comicDownloadedChapters.count { it.status == ComicDownloadStatus.COMPLETED }
    val activeVideoItems = cachedVideos.filter {
        it.state == OfflineCacheState.RESOLVING ||
            it.state == OfflineCacheState.QUEUED ||
            it.state == OfflineCacheState.DOWNLOADING
    }
    val activeComicItems = comicDownloadedChapters.filter {
        it.status == ComicDownloadStatus.QUEUED ||
            it.status == ComicDownloadStatus.RESOLVING ||
            it.status == ComicDownloadStatus.DOWNLOADING
    }
    val totalActiveCount = activeVideoItems.size + activeComicItems.size
    val combinedActiveProgress = if (totalActiveCount > 0) {
        val sum = activeVideoItems.sumOf { (it.percentDownloaded / 100f).coerceIn(0f, 1f).toDouble() } +
            activeComicItems.sumOf { it.progress.coerceIn(0f, 1f).toDouble() }
        (sum / totalActiveCount).toFloat().coerceIn(0f, 1f)
    } else if (completedCount > 0 || completedComicCount > 0) {
        1f
    } else {
        0f
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(if (clearTargetIsComic) "清空漫画离线缓存？" else "清空视频离线缓存？") },
            text = { Text(if (clearTargetIsComic) "所有已下载漫画章节和正在进行的漫画任务都会被移除。" else "所有已缓存视频和正在进行的视频任务都会被移除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (clearTargetIsComic) {
                            onClearComicDownloads()
                        } else {
                            cacheManager.clearAll()
                        }
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

    val renderDetailRoute: @Composable (SettingsRoute, Boolean) -> Unit = { detailRoute, showBack ->
        val effectiveBack: () -> Unit = if (showBack) {
            { route = SettingsRoute.OVERVIEW }
        } else {
            onBack
        }
        when (detailRoute) {
            SettingsRoute.OVERVIEW,
            SettingsRoute.VIDEO_SOURCES -> VideoSourcesPage(
                sourceUrl = sourceUrl,
                selectedSourceId = selectedSourceId,
                addressError = addressError,
                onSourceUrl = { sourceUrl = it },
                onSave = onSave,
                onBack = effectiveBack,
                showBackButton = showBack,
            )
            SettingsRoute.COMIC_SOURCES -> ComicSourcesPage(
                sources = comicSources,
                selectedSourceId = selectedComicSourceId,
                onSource = onComicSource,
                onBack = effectiveBack,
                showBackButton = showBack,
            )
            SettingsRoute.LIVE_SOURCES -> LiveSourcesPage(
                sources = LiveSourceCatalog.sources,
                selectedSourceId = selectedLiveSourceId,
                onSource = onLiveSource,
                onBack = effectiveBack,
                showBackButton = showBack,
            )
            SettingsRoute.OFFLINE_CACHE -> OfflineCachePage(
                cachedVideos = cachedVideos,
                comicChapters = comicDownloadedChapters,
                completedCount = completedCount,
                downloadedBytes = downloadedBytes,
                completedComicCount = completedComicCount,
                comicDownloadedBytes = comicDownloadedBytes,
                totalActiveCount = totalActiveCount,
                combinedActiveProgress = combinedActiveProgress,
                onBack = effectiveBack,
                showBackButton = showBack,
                onOpenCached = onOpenCached,
                onOpenComic = onOpenComicDownload,
                onBrowseHome = onBrowseHome,
                onClear = { isComic ->
                    clearTargetIsComic = isComic
                    showClearConfirmation = true
                },
                onRetry = cacheManager::retry,
                onRemove = cacheManager::remove,
                onRetryComic = onRetryComicDownload,
                onRemoveComic = onRemoveComicDownload,
            )
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isTwoPane = maxWidth >= 840.dp
            if (isTwoPane) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(0.44f).fillMaxHeight()) {
                        SettingsOverview(
                            selectedSource = ContentSourceCatalog.find(currentUrl),
                            selectedComicSource = comicSources.find { it.id == selectedComicSourceId },
                            selectedLiveSource = LiveSourceCatalog.find(selectedLiveSourceId),
                            completedCount = completedCount,
                            downloadedBytes = downloadedBytes,
                            completedComicCount = completedComicCount,
                            comicDownloadedBytes = comicDownloadedBytes,
                            totalActiveCount = totalActiveCount,
                            combinedActiveProgress = combinedActiveProgress,
                            themeMode = themeMode,
                            onThemeModeChange = { nextMode -> preferences.themeMode = nextMode },
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
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    Box(modifier = Modifier.weight(0.56f).fillMaxHeight()) {
                        val settingsDetailTransition = AuroraMotion.expressiveFadeThrough()
                        AnimatedContent(
                            targetState = if (route == SettingsRoute.OVERVIEW) SettingsRoute.VIDEO_SOURCES else route,
                            transitionSpec = { settingsDetailTransition },
                            label = "settings-detail-pane",
                        ) { activeDetail ->
                            renderDetailRoute(activeDetail, false)
                        }
                    }
                }
            } else {
                val settingsRouteTransition = AuroraMotion.expressiveFadeThrough()
                PredictiveBackContainer(
                    enabled = route != SettingsRoute.OVERVIEW,
                    onBack = { route = SettingsRoute.OVERVIEW },
                ) {
                    AnimatedContent(
                        targetState = route,
                        transitionSpec = { settingsRouteTransition },
                        label = "settings-route",
                    ) { currentRoute ->
                        if (currentRoute == SettingsRoute.OVERVIEW) {
                            SettingsOverview(
                                selectedSource = ContentSourceCatalog.find(currentUrl),
                                selectedComicSource = comicSources.find { it.id == selectedComicSourceId },
                                selectedLiveSource = LiveSourceCatalog.find(selectedLiveSourceId),
                                completedCount = completedCount,
                                downloadedBytes = downloadedBytes,
                                completedComicCount = completedComicCount,
                                comicDownloadedBytes = comicDownloadedBytes,
                                totalActiveCount = totalActiveCount,
                                combinedActiveProgress = combinedActiveProgress,
                                themeMode = themeMode,
                                onThemeModeChange = { nextMode -> preferences.themeMode = nextMode },
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
                        } else {
                            renderDetailRoute(currentRoute, true)
                        }
                    }
                }
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
    completedComicCount: Int,
    comicDownloadedBytes: Long,
    totalActiveCount: Int,
    combinedActiveProgress: Float,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
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
                HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsNavigationRow(
                    icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                    title = "漫画源",
                    supporting = "管理漫画内容来源",
                    value = selectedComicSource?.name ?: "禁漫天堂",
                    onClick = onComicSources,
                    modifier = Modifier.testTag("settings-comic-sources"),
                )
                HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsNavigationRow(
                    icon = { Icon(Icons.Default.LiveTv, contentDescription = null) },
                    title = "直播源",
                    supporting = "管理全球真人与校园风情直播",
                    value = selectedLiveSource?.name ?: "Stripchat",
                    onClick = onLiveSources,
                    modifier = Modifier.testTag("settings-live-sources"),
                )
                HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                val totalOfflineBytes = downloadedBytes + comicDownloadedBytes
                val offlineSummary = if (totalActiveCount > 0) {
                    "下载中 $totalActiveCount 项 (${(combinedActiveProgress * 100).toInt()}%) · ${formatBytes(totalOfflineBytes)}"
                } else {
                    "$completedCount 视频 · $completedComicCount 漫章 · ${formatBytes(totalOfflineBytes)}"
                }
                SettingsNavigationRow(
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    title = "离线缓存",
                    supporting = offlineSummary,
                    onClick = onOfflineCache,
                    modifier = Modifier.testTag("settings-offline-cache"),
                )
            }
        }
        item { SettingsSectionLabel("体验") }
        item {
            ThemeModeSelectorCard(
                selectedMode = themeMode,
                onSelectMode = onThemeModeChange,
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
                icon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = "开屏音效",
                supporting = "播放原创电影感音效；静音或媒体音量为 0 时不播放",
                checked = introSoundEnabled,
                onCheckedChange = onIntroSoundChange,
                testTag = "settings-intro-sound",
            )
        }
        item { SettingsSectionLabel("关于") }
        item {
            AboutAndOpenSourceCard()
        }
    }
}

@Composable
private fun ThemeModeSelectorCard(
    selectedMode: ThemeMode,
    onSelectMode: (ThemeMode) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val modes = ThemeMode.entries
    SettingsCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
                .testTag("settings-theme-mode"),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.theme_mode_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "即时切换浅色、深色或跟随系统外观",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                modifier = Modifier.fillMaxWidth(),
            ) {
                modes.forEachIndexed { index, mode ->
                    val isSelected = mode == selectedMode
                    val label = when (mode) {
                        ThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system)
                        ThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
                        ThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
                    }
                    FilledTonalToggleButton(
                        checked = isSelected,
                        onCheckedChange = {
                            if (!isSelected) {
                                AuroraHaptics.segmentTick(haptic)
                                onSelectMode(mode)
                            }
                        },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .semantics { role = Role.RadioButton }
                            .testTag("theme-mode-${mode.storageKey}"),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutAndOpenSourceCard() {
    var showLicensesDialog by rememberSaveable { mutableStateOf(false) }
    val versionSummary = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
    val aboutTitle = stringResource(R.string.about_open_source_title)

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("开源组件与许可声明") },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    item {
                        Text(
                            "Netgrey $versionSummary 使用以下开源软件组件，谨向各上游社区致谢：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val notices = listOf(
                        "AndroidX & Jetpack Compose / Material 3 Expressive & Adaptive" to "Apache License 2.0",
                        "AndroidX Core SplashScreen & Activity Predictive Back" to "Apache License 2.0",
                        "AndroidX Media3 / ExoPlayer" to "Apache License 2.0",
                        "Square OkHttp" to "Apache License 2.0",
                        "Coil Image Loader" to "Apache License 2.0",
                        "Jsoup HTML Parser" to "MIT License",
                        "TeamNewPipe / PipePipe Extractor & nanojson" to "GNU GPL v3.0 / MIT License",
                    )
                    items(notices) { (name, license) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text("关闭")
                }
            },
        )
    }

    SettingsNavigationCard {
        SettingsNavigationRow(
            icon = { Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = aboutTitle,
            supporting = "查看当前构建版本与第三方开源组件许可声明",
            value = versionSummary,
            onClick = { showLicensesDialog = true },
            modifier = Modifier.testTag("settings-about-licenses"),
        )
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
    val haptic = LocalHapticFeedback.current
    SettingsCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = { next ->
                        AuroraHaptics.toggle(haptic, next)
                        onCheckedChange(next)
                    },
                )
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
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
        content = { content() },
    )
}

@Composable
private fun SettingsSectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 6.dp, top = 14.dp, bottom = 4.dp)
            .semantics { heading() },
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
                AuroraHaptics.tap(haptic)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center, content = { icon() })
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
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
    showBackButton: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        if (showBackButton) {
            IconButton(
                onClick = {
                    AuroraHaptics.tap(haptic)
                    onBack()
                },
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = backDescription, tint = MaterialTheme.colorScheme.onBackground)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Pins [SettingsPageHeader] above a scrollable settings subpage so back navigation stays accessible during scroll or IME focus. */
@Composable
private fun SettingsSubpageScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    listTag: String,
    showBackButton: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Box(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
            SettingsPageHeader(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                showBackButton = showBackButton,
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = LocalDockHeight.current + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag(listTag),
            content = content,
        )
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
    showBackButton: Boolean = true,
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
    SettingsSubpageScaffold(
        title = "视频源",
        subtitle = "选择后立即刷新首页内容",
        onBack = onBack,
        listTag = "video-sources-page",
        showBackButton = showBackButton,
    ) {
        item {
            AuroraSearchBar(
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
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
                        modifier = Modifier.animateItem(),
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
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
        item { SettingsSectionLabel("自定义站点") }
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = MaterialTheme.shapes.large,
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
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { onSave(sourceUrl) },
                        enabled = addressError == null,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
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
    showBackButton: Boolean = true,
) {
    var filter by rememberSaveable { mutableStateOf("") }
    val filteredSources = remember(filter, sources) {
        sources.filter { source ->
            filter.isBlank() || source.name.contains(filter, ignoreCase = true) ||
                source.description.contains(filter, ignoreCase = true)
        }
    }
    SettingsSubpageScaffold(
        title = "漫画源",
        subtitle = "选择后立即刷新漫画内容",
        onBack = onBack,
        listTag = "comic-sources-page",
        showBackButton = showBackButton,
    ) {
        item {
            AuroraSearchBar(
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                )
            }
        } else {
            items(filteredSources, key = ComicSourceInfo::id) { source ->
                ComicSourceOption(
                    source = source,
                    selected = selectedSourceId == source.id,
                    onClick = { onSource(source.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun OfflineCachePage(
    cachedVideos: List<OfflineVideo>,
    comicChapters: List<DownloadedComicChapter>,
    completedCount: Int,
    downloadedBytes: Long,
    completedComicCount: Int,
    comicDownloadedBytes: Long,
    totalActiveCount: Int,
    combinedActiveProgress: Float,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
    onOpenCached: (VideoItem) -> Unit,
    onOpenComic: (DownloadedComicChapter) -> Unit,
    onBrowseHome: () -> Unit,
    onClear: (Boolean) -> Unit,
    onRetry: (OfflineVideo) -> Unit,
    onRemove: (String) -> Unit,
    onRetryComic: (String) -> Unit,
    onRemoveComic: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var selectedTab by rememberSaveable { mutableStateOf(OfflineMediaTab.VIDEO) }
    var statusFilter by rememberSaveable { mutableStateOf(OfflineStatusFilter.ALL) }
    val totalStorageBytes = downloadedBytes + comicDownloadedBytes

    val filteredVideos = remember(cachedVideos, statusFilter) {
        when (statusFilter) {
            OfflineStatusFilter.ALL -> cachedVideos
            OfflineStatusFilter.DOWNLOADING -> cachedVideos.filter {
                it.state == OfflineCacheState.RESOLVING ||
                    it.state == OfflineCacheState.QUEUED ||
                    it.state == OfflineCacheState.DOWNLOADING
            }
            OfflineStatusFilter.COMPLETED -> cachedVideos.filter(OfflineVideo::isPlayableOffline)
            OfflineStatusFilter.FAILED -> cachedVideos.filter { it.state == OfflineCacheState.FAILED }
        }
    }

    val filteredComics = remember(comicChapters, statusFilter) {
        when (statusFilter) {
            OfflineStatusFilter.ALL -> comicChapters
            OfflineStatusFilter.DOWNLOADING -> comicChapters.filter {
                it.status == ComicDownloadStatus.QUEUED ||
                    it.status == ComicDownloadStatus.RESOLVING ||
                    it.status == ComicDownloadStatus.DOWNLOADING
            }
            OfflineStatusFilter.COMPLETED -> comicChapters.filter { it.status == ComicDownloadStatus.COMPLETED }
            OfflineStatusFilter.FAILED -> comicChapters.filter { it.status == ComicDownloadStatus.FAILED }
        }
    }

    val subtitleText = if (totalActiveCount > 0) {
        "下载中 $totalActiveCount 项 (${(combinedActiveProgress * 100).toInt()}%) · ${formatBytes(totalStorageBytes)}"
    } else {
        "$completedCount 视频 · $completedComicCount 漫章 · ${formatBytes(totalStorageBytes)}"
    }

    SettingsSubpageScaffold(
        title = "离线缓存",
        subtitle = subtitleText,
        onBack = onBack,
        listTag = "offline-cache-page",
        showBackButton = showBackButton,
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (totalActiveCount > 0) "正在后台下载 $totalActiveCount 项任务" else "本地存储空间",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            if (totalActiveCount > 0) "${(combinedActiveProgress * 100).toInt()}%" else "已占用 ${formatBytes(totalStorageBytes)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (totalActiveCount > 0) {
                        AuroraWavyProgressBar(
                            progress = { combinedActiveProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                        )
                    } else {
                        val videoShare = if (totalStorageBytes > 0L) {
                            (downloadedBytes.toFloat() / totalStorageBytes.toFloat()).coerceIn(0.06f, 0.94f)
                        } else {
                            0.06f
                        }
                        val comicShare = if (totalStorageBytes > 0L && comicDownloadedBytes > 0L) {
                            (comicDownloadedBytes.toFloat() / totalStorageBytes.toFloat()).coerceIn(0.06f, 0.94f)
                        } else {
                            0.04f
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        ) {
                            Row(Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(videoShare)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                                Spacer(Modifier.width(2.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(comicShare)
                                        .background(MaterialTheme.colorScheme.tertiary),
                                )
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            Text(
                                "视频 $completedCount/${cachedVideos.size} (${formatBytes(downloadedBytes)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                            Text(
                                "漫画 $completedComicCount/${comicChapters.size} (${formatBytes(comicDownloadedBytes)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        item {
            val tabs = listOf(
                OfflineMediaTab.VIDEO to "视频 (${cachedVideos.size})",
                OfflineMediaTab.COMIC to "漫画 (${comicChapters.size})",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                tabs.forEachIndexed { index, (tab, label) ->
                    val selected = selectedTab == tab
                    FilledTonalToggleButton(
                        checked = selected,
                        onCheckedChange = {
                            if (!selected) {
                                AuroraHaptics.segmentTick(haptic)
                                selectedTab = tab
                            }
                        },
                        shapes = if (index == 0) {
                            ButtonGroupDefaults.connectedLeadingButtonShapes()
                        } else {
                            ButtonGroupDefaults.connectedTrailingButtonShapes()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .semantics { role = Role.Tab },
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(OfflineStatusFilter.entries) { filter ->
                    val count = when (selectedTab) {
                        OfflineMediaTab.VIDEO -> when (filter) {
                            OfflineStatusFilter.ALL -> cachedVideos.size
                            OfflineStatusFilter.DOWNLOADING -> cachedVideos.count {
                                it.state == OfflineCacheState.RESOLVING ||
                                    it.state == OfflineCacheState.QUEUED ||
                                    it.state == OfflineCacheState.DOWNLOADING
                            }
                            OfflineStatusFilter.COMPLETED -> cachedVideos.count(OfflineVideo::isPlayableOffline)
                            OfflineStatusFilter.FAILED -> cachedVideos.count { it.state == OfflineCacheState.FAILED }
                        }
                        OfflineMediaTab.COMIC -> when (filter) {
                            OfflineStatusFilter.ALL -> comicChapters.size
                            OfflineStatusFilter.DOWNLOADING -> comicChapters.count {
                                it.status == ComicDownloadStatus.QUEUED ||
                                    it.status == ComicDownloadStatus.RESOLVING ||
                                    it.status == ComicDownloadStatus.DOWNLOADING
                            }
                            OfflineStatusFilter.COMPLETED -> comicChapters.count { it.status == ComicDownloadStatus.COMPLETED }
                            OfflineStatusFilter.FAILED -> comicChapters.count { it.status == ComicDownloadStatus.FAILED }
                        }
                    }
                    FilterChip(
                        selected = statusFilter == filter,
                        onClick = {
                            AuroraHaptics.segmentTick(haptic)
                            statusFilter = filter
                        },
                        label = { Text("${filter.label} ($count)") },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
            }
        }
        if (selectedTab == OfflineMediaTab.VIDEO) {
            if (cachedVideos.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 48.dp),
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                            )
                            Text("暂无离线视频", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "播放视频时点击缓存按钮，任务和已下载内容会显示在这里。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                            Button(
                                onClick = onBrowseHome,
                                shape = CircleShape,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text("去首页缓存视频") }
                        }
                    }
                }
            } else if (filteredVideos.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                        ) {
                            Text("当前筛选无视频任务", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            Text("可切换状态筛选查看其他视频下载记录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                item {
                    TextButton(
                        onClick = { onClear(false) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("清空全部视频缓存")
                    }
                }
                items(filteredVideos, key = OfflineVideo::id) { cached ->
                    OfflineCacheRow(
                        cached = cached,
                        onOpen = { if (cached.isPlayableOffline) onOpenCached(cached.asVideoItem()) },
                        onRetry = { onRetry(cached) },
                        onRemove = { onRemove(cached.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        } else {
            if (comicChapters.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 48.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                            )
                            Text("暂无离线漫画", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "在漫画详情页或阅读器内点击下载章节/下载全本，进度与离线章节会显示在这里。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            } else if (filteredComics.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                        ) {
                            Text("当前筛选无漫画任务", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            Text("可切换状态筛选查看其他漫画章节记录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                item {
                    TextButton(
                        onClick = { onClear(true) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("清空全部漫画缓存")
                    }
                }
                items(filteredComics, key = DownloadedComicChapter::id) { chapter ->
                    OfflineComicChapterRow(
                        chapter = chapter,
                        onOpen = {
                            if (chapter.status == ComicDownloadStatus.COMPLETED) {
                                onOpenComic(chapter)
                            }
                        },
                        onRetry = { onRetryComic(chapter.id) },
                        onRemove = { onRemoveComic(chapter.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineComicChapterRow(
    chapter: DownloadedComicChapter,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pct = (chapter.progress.coerceIn(0f, 1f) * 100).toInt()
    val maxPages = maxOf(chapter.totalPages, chapter.downloadedPages)
    val detail = when (chapter.status) {
        ComicDownloadStatus.QUEUED -> "排队等待 · $maxPages 页"
        ComicDownloadStatus.RESOLVING -> "正在解析章节图片…"
        ComicDownloadStatus.DOWNLOADING -> "下载中 ${chapter.downloadedPages}/$maxPages 页 ($pct%)"
        ComicDownloadStatus.COMPLETED -> "已完成 · $maxPages 页 · ${formatBytes(chapter.totalBytes)} · 点击离线阅读"
        ComicDownloadStatus.PAUSED -> "已暂停 · ${chapter.downloadedPages}/$maxPages 页"
        ComicDownloadStatus.FAILED -> chapter.errorMessage?.takeIf { it.isNotBlank() }?.let { "失败: $it" } ?: "下载失败"
    }
    Card(
        onClick = onOpen,
        enabled = chapter.status == ComicDownloadStatus.COMPLETED,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    chapter.comicTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    chapter.chapterTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    detail,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (chapter.status == ComicDownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (chapter.status == ComicDownloadStatus.DOWNLOADING ||
                    chapter.status == ComicDownloadStatus.RESOLVING ||
                    chapter.status == ComicDownloadStatus.QUEUED
                ) {
                    AuroraWavyProgressBar(
                        progress = { chapter.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
            if (chapter.status == ComicDownloadStatus.FAILED) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = "重试漫画下载")
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "删除漫画缓存")
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
    modifier: Modifier = Modifier,
) {
    val cacheStatusDescription = when (cached.state) {
        OfflineCacheState.RESOLVING -> "正在解析缓存地址"
        OfflineCacheState.QUEUED -> "等待缓存"
        OfflineCacheState.DOWNLOADING -> "正在缓存，已完成 ${cached.percentDownloaded.toInt()}%"
        OfflineCacheState.COMPLETED -> "缓存已完成，可离线观看"
        OfflineCacheState.FAILED -> "缓存失败，可重试"
        OfflineCacheState.REMOVING -> "正在删除缓存"
    }
    Card(
        onClick = onOpen,
        enabled = cached.isPlayableOffline,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
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
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val detail = buildList {
                    cached.episodeLabel?.let(::add)
                    add(
                        when (cached.state) {
                            OfflineCacheState.RESOLVING -> "正在解析"
                            OfflineCacheState.QUEUED -> "等待缓存"
                            OfflineCacheState.DOWNLOADING -> {
                                val bytesPart = if (cached.contentLength > 0L) {
                                    " · ${formatBytes(cached.bytesDownloaded)} / ${formatBytes(cached.contentLength)}"
                                } else if (cached.bytesDownloaded > 0L) {
                                    " · ${formatBytes(cached.bytesDownloaded)}"
                                } else {
                                    ""
                                }
                                "下载中 ${cached.percentDownloaded.toInt()}%$bytesPart"
                            }
                            OfflineCacheState.COMPLETED -> formatBytes(cached.bytesDownloaded)
                            OfflineCacheState.FAILED -> "缓存失败"
                            OfflineCacheState.REMOVING -> "正在移除"
                        },
                    )
                }.joinToString(" · ")
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (cached.state == OfflineCacheState.DOWNLOADING) {
                    AuroraWavyProgressBar(
                        progress = { (cached.percentDownloaded / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
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
private fun SourceOption(
    source: ContentSource,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val semanticsColors = LocalAuroraSemanticColors.current
    Card(
        onClick = {
            AuroraHaptics.segmentTick(haptic)
            if (!selected) onClick()
        },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) scheme.primaryContainer else scheme.surfaceContainerLow,
            contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        ),
        shape = MaterialTheme.shapes.large,
        border = if (selected) BorderStroke(1.5.dp, scheme.primary) else null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(
                if (selected) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        source.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val badgeBg = if (source.isDirectDomestic) semanticsColors.directContainer else semanticsColors.overseasContainer
                    val badgeText = if (source.isDirectDomestic) semanticsColors.onDirectContainer else semanticsColors.onOverseasContainer
                    val badgeLabel = if (source.isDirectDomestic) "直连" else "海外"
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = badgeText,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    source.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.82f) else scheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Text(
                    "使用中",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ComicSourceOption(
    source: ComicSourceInfo,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = {
            AuroraHaptics.segmentTick(haptic)
            if (!selected) onClick()
        },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) scheme.primaryContainer else scheme.surfaceContainerLow,
            contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        ),
        shape = MaterialTheme.shapes.large,
        border = if (selected) BorderStroke(1.5.dp, scheme.primary) else null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(
                if (selected) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    source.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    source.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.82f) else scheme.onSurfaceVariant,
                )
            }
            Text(
                when {
                    selected -> "使用中"
                    source.requiresAuthentication -> "需登录"
                    else -> ""
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = scheme.primary,
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
    showBackButton: Boolean = true,
) {
    var filter by rememberSaveable { mutableStateOf("") }
    val filteredSources = remember(filter, sources) {
        sources.filter { source ->
            filter.isBlank() || source.name.contains(filter, ignoreCase = true) ||
                source.description.contains(filter, ignoreCase = true)
        }
    }
    SettingsSubpageScaffold(
        title = "直播源",
        subtitle = "选择后立即刷新直播内容",
        onBack = onBack,
        listTag = "live-sources-page",
        showBackButton = showBackButton,
    ) {
        item {
            AuroraSearchBar(
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                )
            }
        } else {
            items(filteredSources, key = LiveSourceInfo::id) { source ->
                LiveSourceOption(
                    source = source,
                    selected = selectedSourceId == source.id,
                    onClick = { onSource(source.id) },
                    modifier = Modifier.animateItem(),
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
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = {
            AuroraHaptics.segmentTick(haptic)
            if (!selected) onClick()
        },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) scheme.primaryContainer else scheme.surfaceContainerLow,
            contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        ),
        shape = MaterialTheme.shapes.large,
        border = if (selected) BorderStroke(1.5.dp, scheme.primary) else null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(
                if (selected) Icons.Default.CheckCircle else Icons.Default.LiveTv,
                contentDescription = null,
                tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    source.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    source.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.82f) else scheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Text(
                    "使用中",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.primary,
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
