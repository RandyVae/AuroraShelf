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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.aurorashelf.app.ui.theme.AuroraImmersiveBackground
import com.aurorashelf.app.ui.theme.AuroraMediaLabel
import com.aurorashelf.app.ui.theme.AuroraMediaMuted
import com.aurorashelf.app.ui.theme.AuroraMediaSurfaceVariant
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchScreen(
    query: String,
    videos: List<VideoItem>,
    favoriteIds: Set<String>,
    isSearching: Boolean,
    message: String?,
    filterLocally: Boolean,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    onFavorite: (VideoItem) -> Unit,
) {
    val matches = remember(query, videos, filterLocally) {
        if (!filterLocally || query.isBlank()) {
            videos
        } else {
            videos.filter {
                it.title.contains(query, ignoreCase = true) || it.author.contains(query, ignoreCase = true)
            }
        }
    }
    var selectedSourceFilter by rememberSaveable { mutableStateOf("全部") }
    val availableSources = remember(matches) {
        if (matches.isEmpty()) listOf("全部")
        else listOf("全部") + matches.map { it.author.ifBlank { "华视影院" } }.distinct().sorted()
    }
    val filteredMatches = remember(matches, selectedSourceFilter) {
        if (selectedSourceFilter == "全部") matches
        else matches.filter { (it.author.ifBlank { "华视影院" }) == selectedSourceFilter }
    }
    val prefetchContext = LocalContext.current
    // task-17: warm the first screen of results (bytes + decode) after the list settles.
    LaunchedEffect(matches) {
        if (matches.isNotEmpty()) {
            CoverPrefetcher.prefetchVideos(prefetchContext, matches, limit = SEARCH_COVER_PREFETCH_LIMIT)
        }
    }
    Surface(color = AuroraImmersiveBackground, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            stickyHeader(key = "search-header") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                ) {
                    CenteredSearchBar(
                        query = query,
                        onQueryChange = onQuery,
                        placeholder = "全局搜索所有视频源",
                        onSearch = onSearch,
                        onBack = onClose,
                        onClear = { onQuery("") },
                        isSearching = isSearching,
                        testTag = "search-input-field",
                    )
                }
            }
            message?.let { status -> item { StatusBanner(status, onSearch) } }
            if (matches.isEmpty() && !isSearching && message == null) {
                if (query.isBlank()) {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                        ) {
                            Text(
                                text = "热门探索",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                            )
                            val trending = listOf(
                                "4K 超清", "最新电影", "热门连续剧", "经典港片",
                                "热血动漫", "高分综艺", "悬疑惊悚", "科幻大片"
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            ) {
                                trending.forEach { keyword ->
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = AuroraMediaSurfaceVariant,
                                        border = BorderStroke(1.dp, Color(0xFF333333)),
                                        modifier = Modifier.applePressEffect {
                                            onQuery(keyword)
                                            onSearch()
                                        },
                                    ) {
                                        Text(
                                            text = keyword,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                            color = AuroraMediaLabel,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            EmptyState(
                                message = "输入关键词全局搜索所有视频源",
                                icon = Icons.Outlined.Search,
                                actionLabel = null,
                                onAction = null,
                            )
                        }
                    }
                } else {
                    item {
                        EmptyState(
                            message = "点击搜索按钮开始查找",
                            icon = Icons.Outlined.Search,
                            actionLabel = if (!isSearching) "开始搜索" else null,
                            onAction = onSearch,
                        )
                    }
                }
            } else {
                // 1. 顶部分源过滤栏
                if (availableSources.size > 2) {
                    item(key = "source-filter-bar") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            availableSources.forEach { src ->
                                val isSelected = selectedSourceFilter == src
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) AuroraCoral else AuroraMediaSurfaceVariant,
                                    border = BorderStroke(1.dp, if (isSelected) AuroraCoral else Color(0xFF333333)),
                                    modifier = Modifier.applePressEffect {
                                        selectedSourceFilter = src
                                    },
                                ) {
                                    val count = if (src == "全部") matches.size else matches.count { (it.author.ifBlank { "华视影院" }) == src }
                                    Text(
                                        text = "$src ($count)",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        ),
                                        color = if (isSelected) Color.White else AuroraMediaLabel,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Apple TV 热门最佳匹配横向货架（前 4 部精选）
                val topMatches = filteredMatches.take(4)
                if (topMatches.isNotEmpty()) {
                    item(key = "top-matches-shelf") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "热门匹配",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                )
                                Text(
                                    text = "精选 ${topMatches.size} 部",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = AuroraCoral,
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            ) {
                                topMatches.forEach { video ->
                                    NetflixPosterShelfCard(
                                        video = video,
                                        onClick = { onVideo(video) },
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. 全部结果流
                item(key = "all-results-header") {
                    Text(
                        text = "全部检索结果 (${filteredMatches.size})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                items(filteredMatches, key = VideoItem::id) { video ->
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

/** First-screen search results warmed through the shared task-17 CoverPrefetcher. */
private const val SEARCH_COVER_PREFETCH_LIMIT = 8
