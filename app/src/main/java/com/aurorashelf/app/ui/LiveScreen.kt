@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.aurorashelf.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.aurorashelf.app.data.LiveStreamResolveResult
import com.aurorashelf.app.data.LiveStreamResolver
import com.aurorashelf.app.data.StripchatLiveRepository
import com.aurorashelf.app.data.VideoRepository
import com.aurorashelf.app.model.LiveCategory
import com.aurorashelf.app.model.LiveComment
import com.aurorashelf.app.model.LiveItem
import com.aurorashelf.app.model.LiveSourceCatalog
import com.aurorashelf.app.ui.theme.AuroraBrandAccentDeep
import com.aurorashelf.app.ui.theme.AuroraCoral
import com.aurorashelf.app.ui.theme.AuroraHaptics
import com.aurorashelf.app.ui.theme.AuroraImmersiveBackground
import com.aurorashelf.app.ui.theme.AuroraMediaLabel
import com.aurorashelf.app.ui.theme.AuroraMediaMeta
import com.aurorashelf.app.ui.theme.AuroraMediaMuted
import com.aurorashelf.app.ui.theme.AuroraMediaPlaceholder
import com.aurorashelf.app.ui.theme.AuroraMediaSurface
import com.aurorashelf.app.ui.theme.AuroraMediaSurfaceVariant
import com.aurorashelf.app.ui.theme.AuroraMotion
import com.aurorashelf.app.ui.theme.LocalAuroraSemanticColors
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray

@Composable
internal fun LiveScreen(
    state: LiveUiState,
    bottomPadding: Dp,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    onVideoOrientationChange: (Boolean) -> Unit = {},
    onCategory: (LiveCategory) -> Unit,
    onSelectSource: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onCloseSearch: () -> Unit = {},
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (LiveItem) -> Unit,
    onSelectItem: (LiveItem) -> Unit = onOpen,
    onClose: () -> Unit,
    onSettings: () -> Unit = {},
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            state.selectedItem?.let { item ->
                val relatedItems = remember(state.items, item.id) {
                    state.items.filter { it.id != item.id }
                }
                PredictiveBackContainer(
                    enabled = !isFullscreen,
                    onBack = onClose,
                ) {
                    key(item.id) {
                        LivePlayerScreen(
                            item = item,
                            relatedItems = relatedItems,
                            isFullscreen = isFullscreen,
                            onFullscreenChange = onFullscreenChange,
                            onVideoOrientationChange = onVideoOrientationChange,
                            onSelectRelated = onSelectItem,
                            onBack = onClose,
                        )
                    }
                }
            } ?: LiveFeed(
                state = state,
                bottomPadding = bottomPadding,
                onCategory = onCategory,
                onSelectSource = onSelectSource,
                onSearch = onSearch,
                onSearchQueryChange = onSearchQueryChange,
                onCloseSearch = onCloseSearch,
                onRefresh = onRefresh,
                onLoadMore = onLoadMore,
                onOpen = onOpen,
                onSettings = onSettings,
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose { LiveSessionCache.release() }
    }
}

@Composable
private fun LiveFeed(
    state: LiveUiState,
    bottomPadding: Dp,
    onCategory: (LiveCategory) -> Unit,
    onSelectSource: (String) -> Unit,
    onSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (LiveItem) -> Unit,
    onSettings: () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    val density = LocalDensity.current
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    // C3: with >=150% system font the artwork columns widen with the font scale (capped),
    // so the adaptive grid collapses to one column instead of clipping card text.
    val gridState = rememberLazyGridState()
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val emptyHeaderClearanceDp = if (headerHeightPx > 0) with(density) { headerHeightPx.toDp() + 8.dp } else 96.dp
    val visibleItems = remember(state.items, state.searchQuery) {
        filterLiveItems(state.items, state.searchQuery)
    }
    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 40
        }
    }
    val hasHeroBillboard = visibleItems.isNotEmpty() && state.searchQuery.isBlank() && !state.isSearchOpen
    val overlayDarkHeader = !isScrolled && (hasHeroBillboard || (!isLightTheme && !state.isSearchOpen))
    val prefetchContext = LocalContext.current
    LaunchedEffect(state.items) {
        if (state.items.isNotEmpty()) {
            CoverPrefetcher.prefetchLive(prefetchContext, state.items, limit = LIVE_COVER_PREFETCH_LIMIT)
        }
    }
    LaunchedEffect(gridState, state.items.size, state.canLoadMore, state.isLoading, state.isLoadingMore, state.error) {
        snapshotFlow {
            val layout = gridState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItems = layout.totalItemsCount
            totalItems > 0 && lastVisible >= totalItems - 5 &&
                !state.isLoading && !state.isLoadingMore &&
                state.canLoadMore && state.error == null
        }.distinctUntilChanged().filter { it }.collect {
            onLoadMore()
        }
    }

    PredictiveBackContainer(
        enabled = state.isSearchOpen,
        onBack = onCloseSearch,
    ) {
        AuroraPullToRefreshBox(
            isRefreshing = state.isLoading && state.items.isNotEmpty(),
            onRefresh = onRefresh,
            indicatorPaddingTop = emptyHeaderClearanceDp,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = scaledAdaptiveMinSize(164.dp, fontScale)),
                state = gridState,
                contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = bottomPadding + 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("live-grid"),
            ) {
                if (state.isLoading && state.items.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(emptyHeaderClearanceDp))
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                        ) {
                            AuroraLoadingIndicator(contained = true)
                        }
                    }
                } else if (state.error != null && state.items.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(emptyHeaderClearanceDp))
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        val altSourceId = if (state.selectedSourceId == LiveSourceCatalog.STRIPCHAT.id) {
                            LiveSourceCatalog.HORNYSTUDENTS.id
                        } else {
                            LiveSourceCatalog.STRIPCHAT.id
                        }
                        val altSourceName = LiveSourceCatalog.find(altSourceId).name
                        LiveError(
                            message = state.error,
                            onRetry = onRefresh,
                            onSwitchSource = { onSelectSource(altSourceId) },
                            switchSourceLabel = "切换至 $altSourceName 备用源",
                        )
                    }
                } else if (visibleItems.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(emptyHeaderClearanceDp))
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        if (state.searchQuery.isNotBlank()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                            ) {
                                Text(
                                    "未找到与「${state.searchQuery}」匹配的直播间",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(onClick = { onSearchQueryChange("") }, shape = CircleShape) {
                                    Text("清除筛选")
                                }
                            }
                        } else {
                            val altSourceId = if (state.selectedSourceId == LiveSourceCatalog.STRIPCHAT.id) {
                                LiveSourceCatalog.HORNYSTUDENTS.id
                            } else {
                                LiveSourceCatalog.STRIPCHAT.id
                            }
                            val altSourceName = LiveSourceCatalog.find(altSourceId).name
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                            ) {
                                Text("当前分类没有公开直播", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(onClick = { onSelectSource(altSourceId) }, shape = CircleShape) {
                                    Text("切换至 $altSourceName")
                                }
                            }
                        }
                    }
                } else {
                    if (hasHeroBillboard) {
                        val heroItem = visibleItems.first()
                        item(key = "hero-billboard", span = { GridItemSpan(maxLineSpan) }) {
                            LiveHeroSpotlight(
                                item = heroItem,
                                onOpen = { onOpen(heroItem) },
                            )
                        }
                    } else {
                        item(key = "live-search-spacer", span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(Modifier.height(emptyHeaderClearanceDp))
                        }
                    }

                    item(key = "live-pinned-categories", span = { GridItemSpan(maxLineSpan) }) {
                        CategoryStrip(
                            testTag = "live-category-bar",
                            isScrolled = isScrolled,
                            applyBackground = false,
                        ) {
                            LiveSourceCatalog.sources.forEach { src ->
                                AuroraFilterChip(
                                    selected = state.selectedSourceId == src.id,
                                    onClick = { onSelectSource(src.id) },
                                    label = "源: ${src.name}",
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            LiveCategory.entries.forEach { category ->
                                AuroraFilterChip(
                                    selected = state.selectedCategory == category,
                                    onClick = { onCategory(category) },
                                    label = category.label,
                                )
                            }
                        }
                    }

                    if (hasHeroBillboard && visibleItems.size >= 5) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            AuroraSectionHeader(
                                title = "热门推荐 FEATURED",
                                subtitle = "热门实时直播",
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                val topPerformers = visibleItems.take(10)
                                items(topPerformers, key = { "live-featured-${it.id}" }) { item ->
                                    LiveFeaturedCard(
                                        item = item,
                                        onClick = { onOpen(item) },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            AuroraSectionHeader(
                                title = "正在直播",
                            )
                        }
                    }

                    val gridItems = if (hasHeroBillboard) visibleItems.drop(1) else visibleItems
                    items(gridItems, key = LiveItem::id) { item ->
                        LiveCard(
                            item = item,
                            onClick = { onOpen(item) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                if (state.isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                        ) {
                            AuroraLoadingIndicator(modifier = Modifier.size(30.dp))
                        }
                    }
                } else if (state.error != null && state.items.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                            Text("加载更多失败，点击重试")
                        }
                    }
                }
            }

            val headerModifier = when {
                isScrolled -> Modifier.background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f))
                overlayDarkHeader -> Modifier.background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.40f),
                            Color.Transparent,
                        ),
                    ),
                )
                else -> Modifier.background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                            Color.Transparent,
                        ),
                    ),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { headerHeightPx = it.height }
                    .then(headerModifier)
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                if (state.isSearchOpen) {
                    AuroraSearchBar(
                        query = state.searchQuery,
                        onQueryChange = onSearchQueryChange,
                        placeholder = "搜索主播昵称或国家/地区",
                        onSearch = {},
                        onBack = onCloseSearch,
                        onClear = { onSearchQueryChange("") },
                        isSearching = state.isLoading,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        testTag = "live-search-field",
                    )
                } else {
                    LiveHeader(
                        onSearch = onSearch,
                        onSettings = onSettings,
                        searchLabel = if (state.searchQuery.isNotBlank()) state.searchQuery else "搜索直播",
                        forceDark = overlayDarkHeader,
                    )
                }
                if (state.isLoading) {
                    AuroraWavyProgressBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveHeader(
    onSearch: () -> Unit,
    onSettings: () -> Unit = {},
    searchLabel: String = "搜索直播",
    forceDark: Boolean = false,
) {
    AuroraTopBar(forceDark = forceDark) {
        AuroraSearchCapsule(
            label = searchLabel,
            onClick = onSearch,
            forceDark = forceDark,
        )
    }
}

@Composable
private fun LiveHeroSpotlight(
    item: LiveItem,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val semanticColors = LocalAuroraSemanticColors.current
    val flag = countryFlag(item.countryCode)
    val previewBytes by produceState<ByteArray?>(initialValue = CoverImageLoader.peekMemory(item.thumbnailUrl), key1 = item.thumbnailUrl) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                CoverImageLoader.load(item.thumbnailUrl, liveReferer(item))
            }
        }
    }
    val previewRequest = remember(previewBytes, item.thumbnailUrl, context) {
        previewBytes?.takeIf { it.isNotEmpty() }?.let { bytes ->
            ImageRequest.Builder(context)
                .data(bytes)
                .memoryCacheKey(coverMemoryCacheKey(item.thumbnailUrl))
                .build()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(460.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(role = Role.Button) {
                AuroraHaptics.tap(haptic)
                onOpen()
            },
    ) {
        val isWideStage = maxWidth > 500.dp
        AsyncImage(
            model = previewRequest,
            contentDescription = "${item.username} 直播预览",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (isWideStage) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.56f)),
            )
            Surface(
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                color = AuroraMediaPlaceholder,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .height(220.dp)
                    .aspectRatio(4f / 3f),
            ) {
                AsyncImage(
                    model = previewRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.36f to Color.Transparent,
                            0.65f to Color.Black.copy(alpha = 0.72f),
                            0.84f to Color.Black.copy(alpha = 0.90f),
                            1.0f to Color.Black.copy(alpha = 0.96f),
                        ),
                    ),
                ),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AuroraBadge(
                    text = "● LIVE 直播中",
                    backgroundColor = semanticColors.liveBadgeContainer,
                    textColor = semanticColors.onLiveBadgeContainer,
                )
                if (item.isHd) {
                    AuroraBadge(
                        text = "高清直播",
                        backgroundColor = Color.Black.copy(alpha = 0.65f),
                        textColor = Color.White,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = "$flag ${item.username}",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "${formatViewerCount(item.viewersCount)} 人正在观看",
                style = MaterialTheme.typography.bodySmall,
                color = AuroraMediaLabel,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    AuroraHaptics.confirm(haptic)
                    onOpen()
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "进入直播间",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
private fun LiveFeaturedCard(
    item: LiveItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val semanticColors = LocalAuroraSemanticColors.current
    val previewBytes by produceState<ByteArray?>(initialValue = null, key1 = item.thumbnailUrl) {
        value = withContext(Dispatchers.IO) {
            CoverImageLoader.load(item.thumbnailUrl, liveReferer(item))
        }
    }
    val previewRequest = remember(previewBytes, item.thumbnailUrl, context) {
        previewBytes?.takeIf { it.isNotEmpty() }?.let { bytes ->
            ImageRequest.Builder(context)
                .data(bytes)
                .memoryCacheKey(coverMemoryCacheKey(item.thumbnailUrl))
                .build()
        }
    }

    ElevatedCard(
        onClick = {
            AuroraHaptics.tap(haptic)
            onClick()
        },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = modifier.width(184.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp),
            ) {
                AsyncImage(
                    model = previewRequest,
                    contentDescription = item.username,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.68f), CircleShape)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(semanticColors.liveBadgeContainer, CircleShape),
                    )
                    Text(
                        text = "LIVE",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    )
                    if (item.viewersCount > 0) {
                        Text(
                            text = "· ${formatViewerCount(item.viewersCount)}",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(24.dp),
                ) {
                    AsyncImage(
                        model = previewRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${countryFlag(item.countryCode)} ${item.username}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (item.isHd) "高清实时直播" else "实时公开直播",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveCard(
    item: LiveItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val semanticColors = LocalAuroraSemanticColors.current
    val flag = countryFlag(item.countryCode)
    val context = LocalContext.current
    val previewBytes by produceState<ByteArray?>(initialValue = null, key1 = item.thumbnailUrl) {
        value = withContext(Dispatchers.IO) {
            CoverImageLoader.load(item.thumbnailUrl, liveReferer(item))
        }
    }
    val previewRequest = remember(previewBytes, item.thumbnailUrl, context) {
        previewBytes?.takeIf { it.isNotEmpty() }?.let { bytes ->
            ImageRequest.Builder(context)
                .data(bytes)
                .memoryCacheKey(coverMemoryCacheKey(item.thumbnailUrl))
                .build()
        }
    }
    Card(
        onClick = {
            AuroraHaptics.tap(haptic)
            onClick()
        },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "观看 ${item.username} 的公开直播" }
            .testTag("live-card"),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = previewRequest,
                    contentDescription = "${item.username} 直播预览",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f),
                )
                DarkModeArtworkScrim()
                Surface(
                    color = semanticColors.liveBadgeContainer,
                    contentColor = semanticColors.onLiveBadgeContainer,
                    shape = CircleShape,
                    modifier = Modifier.padding(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(12.dp))
                        Text("LIVE", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold))
                    }
                }
                if (item.isHd) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                    ) {
                        Icon(
                            Icons.Default.Hd,
                            contentDescription = "高清直播",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .size(16.dp),
                        )
                    }
                }
            }
            Text(
                text = "$flag ${item.username}",
                maxLines = 1,
                minLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 10.dp, top = 8.dp, end = 10.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 10.dp, top = 3.dp, end = 10.dp, bottom = 10.dp),
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(
                    text = "${formatViewerCount(item.viewersCount)} 人观看",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LiveError(
    message: String,
    onRetry: () -> Unit,
    onSwitchSource: (() -> Unit)? = null,
    switchSourceLabel: String? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 96.dp, horizontal = 24.dp),
    ) {
        Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(42.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onRetry) { Text("重试") }
            if (onSwitchSource != null && switchSourceLabel != null) {
                OutlinedButton(onClick = onSwitchSource) {
                    Text(switchSourceLabel)
                }
            }
        }
    }
}

internal enum class LiveRoomStatus {
    CONNECTING,
    READY,
    TICKET_SHOW,
    PRIVATE_SHOW,
    OFFLINE,
    ERROR,
}

@Composable
private fun LiveRoomStatusOverlay(
    status: LiveRoomStatus,
    errorMessage: String?,
    onRetry: () -> Unit,
    onOpenExternal: () -> Unit,
    onBack: () -> Unit,
    onFallbackToWeb: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val (icon, title, subtitle) = when (status) {
        LiveRoomStatus.TICKET_SHOW -> Triple(
            Icons.Default.ConfirmationNumber,
            "主播正在进行购票表演",
            "当前直播间为门票专场，公开直播流暂未开放",
        )
        LiveRoomStatus.PRIVATE_SHOW -> Triple(
            Icons.Default.Lock,
            "主播正在进行私密表演",
            "当前直播间处于 1对1 私密秀状态，公开直播流暂未开放",
        )
        LiveRoomStatus.OFFLINE -> Triple(
            Icons.Default.LiveTv,
            "主播目前已离线",
            "主播已下播或离开直播间，请稍后刷新查看",
        )
        LiveRoomStatus.ERROR -> Triple(
            Icons.Default.Warning,
            "直播播放器加载失败",
            errorMessage ?: "无法获取当前直播流，可能受到网络或源站限制",
        )
        LiveRoomStatus.CONNECTING, LiveRoomStatus.READY -> return
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxSize()
            .background(AuroraImmersiveBackground.copy(alpha = 0.94f))
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AuroraCoral,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Color.LightGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (status == LiveRoomStatus.OFFLINE) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuroraBrandAccentDeep,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("返回列表")
                }
            } else {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuroraBrandAccentDeep,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("刷新状态")
                }
            }
            if (onFallbackToWeb != null) {
                OutlinedButton(
                    onClick = onFallbackToWeb,
                    border = BorderStroke(1.dp, AuroraCoral.copy(alpha = 0.8f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp), tint = AuroraCoral)
                    Spacer(Modifier.width(6.dp))
                    Text("备用视口")
                }
            }
            OutlinedButton(
                onClick = onOpenExternal,
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("网页打开")
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun StreamerInfoBar(
    item: LiveItem,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant), RectangleShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = item.username.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                )
                if (item.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = livePosterRequest(context, item.thumbnailUrl, liveReferer(item)),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.username,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Text(countryFlag(item.countryCode), fontSize = 12.sp)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    color = AuroraBrandAccentDeep,
                    shape = RoundedCornerShape(3.dp),
                ) {
                    Text(
                        "LIVE",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                Text(
                    text = "${formatViewerCount(item.viewersCount)} 人观看",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                if (item.isHd) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(3.dp),
                    ) {
                        Text(
                            "HD",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
        IconButton(onClick = onOpenExternal, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "在系统浏览器中打开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LivePlayerScreen(
    item: LiveItem,
    relatedItems: List<LiveItem> = emptyList(),
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    onVideoOrientationChange: (Boolean) -> Unit,
    onSelectRelated: (LiveItem) -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var comments by remember(item.id) { mutableStateOf<List<LiveComment>>(emptyList()) }
    var hasCheckedComments by remember(item.id) { mutableStateOf(false) }
    var isCommentsExpanded by remember(item.id) { mutableStateOf(true) }
    var videoDimensions by remember(item.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    val videoAspectRatio = remember(videoDimensions, item.playerUrl) {
        videoDimensions?.let { (w, h) ->
            if (w > 0 && h > 0) w.toFloat() / h.toFloat() else null
        } ?: if (item.playerUrl.contains("hornystudents.com")) (4f / 3f) else (16f / 9f)
    }
    val openInBrowser: () -> Unit = remember(item.playerUrl, context) {
        {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, item.playerUrl.toUri()).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    val resolver = remember { LiveStreamResolver(verifyConstructedStream = true) }
    var resolveAttempt by remember(item.id) { mutableIntStateOf(0) }
    var forceEmbedded by remember(item.id) { mutableStateOf(false) }
    val resolveResult by produceState<LiveStreamResolveResult?>(initialValue = null, item.id, resolveAttempt) {
        value = resolver.resolve(item)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isFullscreen) AuroraImmersiveBackground else MaterialTheme.colorScheme.background)
            .testTag("live-player"),
    ) {
        val isTwoPaneLandscape = shouldUseTwoPaneLandscapeLayout(
            width = maxWidth,
            height = maxHeight,
            isFullscreen = isFullscreen,
        )
        val leftPaneWidthFraction = if (isTwoPaneLandscape) 0.62f else 1f
        val leftPaneWidth = maxWidth * leftPaneWidthFraction
        val targetStageHeight = remember(videoAspectRatio, leftPaneWidth, maxHeight, isTwoPaneLandscape) {
            calculateLiveStageHeight(
                aspectRatio = videoAspectRatio,
                containerWidth = leftPaneWidth,
                containerHeight = maxHeight,
                isTwoPaneLandscape = isTwoPaneLandscape,
            )
        }
        val animatedStageHeight by animateDpAsState(
            targetValue = targetStageHeight,
            animationSpec = AuroraMotion.defaultSpatial(),
            label = "live-stage-height",
        )
        val playerModifier = if (isFullscreen) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxWidth()
                .height(animatedStageHeight)
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(leftPaneWidthFraction)
                .fillMaxHeight()
                .then(
                    if (!isFullscreen && isTwoPaneLandscape) {
                        Modifier.statusBarsPadding()
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (!isFullscreen && !isTwoPaneLandscape) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AuroraImmersiveBackground)
                        .windowInsetsTopHeight(WindowInsets.statusBars),
                )
            }
            if (forceEmbedded) {
                EmbeddedLivePlayer(
                    url = item.playerUrl,
                    isFullscreen = isFullscreen,
                    onFullscreenChange = onFullscreenChange,
                    onVideoOrientationChange = onVideoOrientationChange,
                    onVideoSizeChange = { w: Int, h: Int -> videoDimensions = Pair(w, h) },
                    onCommentsChange = { comments = it },
                    onCommentsLoadingChange = { hasCheckedComments = !it },
                    onOpenExternal = openInBrowser,
                    onBack = onBack,
                    modifier = playerModifier,
                    posterUrl = item.thumbnailUrl,
                    comments = comments,
                    item = item,
                )
            } else {
                when (val res = resolveResult) {
                    is LiveStreamResolveResult.Success -> {
                        NativeLivePlayer(
                            hlsUrl = res.hlsUrl,
                            referer = res.referer,
                            item = item,
                            isFullscreen = isFullscreen,
                            onFullscreenChange = onFullscreenChange,
                            onVideoOrientationChange = onVideoOrientationChange,
                            onVideoSizeChange = { w: Int, h: Int -> videoDimensions = Pair(w, h) },
                            onOpenExternal = openInBrowser,
                            onBack = onBack,
                            onFallbackToWeb = { forceEmbedded = true },
                            modifier = playerModifier,
                            posterUrl = item.thumbnailUrl,
                            comments = comments,
                        )
                    }
                    is LiveStreamResolveResult.StatusError -> {
                        Box(playerModifier.background(AuroraImmersiveBackground)) {
                            LiveRoomStatusOverlay(
                                status = LiveRoomStatus.OFFLINE,
                                errorMessage = res.message,
                                onRetry = { resolveAttempt++ },
                                onOpenExternal = openInBrowser,
                                onBack = onBack,
                                onFallbackToWeb = { forceEmbedded = true },
                            )
                        }
                    }
                    is LiveStreamResolveResult.Failure -> {
                        EmbeddedLivePlayer(
                            url = item.playerUrl,
                            isFullscreen = isFullscreen,
                            onFullscreenChange = onFullscreenChange,
                            onVideoOrientationChange = onVideoOrientationChange,
                            onVideoSizeChange = { w: Int, h: Int -> videoDimensions = Pair(w, h) },
                            onCommentsChange = { comments = it },
                            onCommentsLoadingChange = { hasCheckedComments = !it },
                            onOpenExternal = openInBrowser,
                            onBack = onBack,
                            modifier = playerModifier,
                            posterUrl = item.thumbnailUrl,
                            comments = comments,
                            item = item,
                        )
                    }
                    null -> {
                        Box(playerModifier.background(AuroraImmersiveBackground)) {
                            item.thumbnailUrl.takeIf { it.isNotBlank() }?.let { poster ->
                                AsyncImage(
                                    model = livePosterRequest(context, poster, liveReferer(item)),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Box(Modifier.fillMaxSize().background(AuroraImmersiveBackground.copy(alpha = 0.55f)))
                            }
                            AuroraLoadingIndicator(color = AuroraCoral, modifier = Modifier.align(Alignment.Center))
                            FilledTonalIconButton(
                                onClick = onBack,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.56f),
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                                    .padding(12.dp)
                                    .size(48.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            if (!isFullscreen) {
                StreamerInfoBar(
                    item = item,
                    onOpenExternal = openInBrowser,
                )
                if (isTwoPaneLandscape) {
                    LiveLeftDetailPanel(
                        item = item,
                        onOpenExternal = openInBrowser,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                } else {
                    val hasRelated = relatedItems.isNotEmpty()
                    val isCommentsLoading = !hasCheckedComments && resolveResult == null
                    LiveComments(
                        comments = comments,
                        isLoading = isCommentsLoading,
                        isExpanded = isCommentsExpanded,
                        applyBottomInset = !hasRelated,
                        compactWhenEmpty = hasRelated,
                        onToggleExpand = { isCommentsExpanded = !isCommentsExpanded },
                        modifier = when {
                            !hasRelated -> Modifier.fillMaxWidth().weight(1f)
                            !isCommentsExpanded -> Modifier.fillMaxWidth()
                            comments.isEmpty() && !isCommentsLoading -> Modifier.fillMaxWidth()
                            else -> Modifier.fillMaxWidth().heightIn(max = 196.dp)
                        },
                    )
                    if (hasRelated) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        )
                        LiveRelatedStreamsList(
                            relatedItems = relatedItems,
                            onSelectRelated = onSelectRelated,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                }
            }
        }

        if (isTwoPaneLandscape) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(0.38f)
                    .fillMaxHeight()
                    .statusBarsPadding(),
            ) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                )
                LiveRightRail(
                    comments = comments,
                    isLoadingComments = !hasCheckedComments && resolveResult == null,
                    isCommentsExpanded = isCommentsExpanded,
                    onToggleComments = { isCommentsExpanded = !isCommentsExpanded },
                    relatedItems = relatedItems,
                    onSelectRelated = onSelectRelated,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun NativeLivePlayer(
    hlsUrl: String,
    referer: String,
    item: LiveItem,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    onVideoOrientationChange: (Boolean) -> Unit,
    onVideoSizeChange: ((width: Int, height: Int) -> Unit)?,
    onOpenExternal: () -> Unit,
    onBack: () -> Unit,
    onFallbackToWeb: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    posterUrl: String? = null,
    comments: List<LiveComment> = emptyList(),
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var isLoading by remember(hlsUrl) { mutableStateOf(true) }
    var isPlaying by remember(hlsUrl) { mutableStateOf(false) }
    var errorMessage by remember(hlsUrl) { mutableStateOf<String?>(null) }
    var isPaused by remember(hlsUrl) { mutableStateOf(false) }
    var isMuted by remember(hlsUrl) { mutableStateOf(false) }
    var isDanmakuEnabled by rememberSaveable { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var reloadAttempt by remember(hlsUrl) { mutableIntStateOf(0) }
    var readySinceEpochMs by remember(hlsUrl, reloadAttempt) { mutableStateOf<Long?>(null) }

    val player = remember(hlsUrl, reloadAttempt) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(8_000, 20_000, 500, 1_000)
            .build()
        ExoPlayer.Builder(context.applicationContext)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .build()
    }

    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_START -> if (!isPaused) player.play()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        if (readySinceEpochMs == null) {
                            readySinceEpochMs = System.currentTimeMillis()
                        }
                        isLoading = false
                        isPlaying = true
                        errorMessage = null
                    }
                    Player.STATE_BUFFERING -> {
                        isLoading = true
                    }
                    Player.STATE_ENDED -> {
                        isLoading = false
                        isPlaying = false
                        val playedMs = readySinceEpochMs?.let { System.currentTimeMillis() - it } ?: 0L
                        if (playedMs < 25_000L && onFallbackToWeb != null) {
                            onFallbackToWeb.invoke()
                        } else {
                            errorMessage = "主播当前已离线或暂未开播"
                        }
                    }
                    Player.STATE_IDLE -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isLoading = false
                isPlaying = false
                errorMessage = "直播流播放异常，请检查网络后点击重试"
                onFallbackToWeb?.invoke()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val isPortrait = videoSize.height > videoSize.width
                    onVideoOrientationChange(isPortrait)
                    onVideoSizeChange?.invoke(videoSize.width, videoSize.height)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(player, hlsUrl, referer, reloadAttempt) {
        isLoading = true
        errorMessage = null
        val http = com.aurorashelf.app.data.playback.ResilientHttpDataSourceFactory(VideoRepository.USER_AGENT)
            .setDefaultRequestProperties(
                mutableMapOf(
                    "Referer" to referer,
                    "Origin" to referer.trimEnd('/'),
                    "Accept" to "*/*",
                ),
            )
        val mediaItem = MediaItem.Builder()
            .setUri(hlsUrl)
            .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
            .build()
        val mediaSource = DefaultMediaSourceFactory(http)
            .setLoadErrorHandlingPolicy(com.aurorashelf.app.data.playback.ResilientLoadErrorHandlingPolicy())
            .createMediaSource(mediaItem)
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = !isPaused && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    LaunchedEffect(controlsVisible, isFullscreen, isPaused) {
        if (controlsVisible && !isPaused) {
            delay(3_500L)
            controlsVisible = false
        }
    }

    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            delay(2000L)
            feedbackMessage = null
        }
    }

    BackHandler(enabled = isFullscreen) {
        onFullscreenChange(false)
    }

    Box(modifier = modifier.background(AuroraImmersiveBackground)) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = false
                    keepScreenOn = !isPaused
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { playerView ->
                if (playerView.player !== player) playerView.player = player
                playerView.keepScreenOn = !isPaused && errorMessage == null
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Gesture detector to toggle controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    controlsVisible = !controlsVisible
                },
        )

        // Poster and buffer indicator
        if (isLoading) {
            Box(Modifier.fillMaxSize().background(AuroraImmersiveBackground)) {
                posterUrl?.let { poster ->
                    AsyncImage(
                        model = livePosterRequest(context, poster, referer),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(Modifier.fillMaxSize().background(AuroraImmersiveBackground.copy(alpha = 0.55f)))
                }
                AuroraLoadingIndicator(color = AuroraCoral, modifier = Modifier.align(Alignment.Center))
            }
        }

        // Error overlay with Retry
        if (errorMessage != null && !isLoading) {
            LiveRoomStatusOverlay(
                status = LiveRoomStatus.ERROR,
                errorMessage = errorMessage,
                onRetry = {
                    isLoading = true
                    errorMessage = null
                    reloadAttempt++
                },
                onOpenExternal = onOpenExternal,
                onBack = onBack,
                onFallbackToWeb = onFallbackToWeb,
            )
        }

        // Danmaku Layer
        DanmakuOverlay(
            comments = comments,
            enabled = isFullscreen && isDanmakuEnabled,
            modifier = Modifier.fillMaxSize(),
        )

        // Floating Toast for Danmaku mode switch
        AnimatedVisibility(
            visible = feedbackMessage != null,
            enter = fadeIn(AuroraMotion.fastEffects()),
            exit = fadeOut(AuroraMotion.fastEffects()),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                .padding(top = 56.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.86f),
                border = BorderStroke(1.dp, if (isDanmakuEnabled) AuroraCoral else Color.White.copy(alpha = 0.34f)),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = if (isDanmakuEnabled) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                        contentDescription = null,
                        tint = if (isDanmakuEnabled) AuroraCoral else Color.LightGray,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = feedbackMessage ?: "",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Top bar
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(AuroraMotion.fastEffects()),
            exit = fadeOut(AuroraMotion.fastEffects()),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            val haptic = LocalHapticFeedback.current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent),
                        ),
                    )
                    .then(
                        if (isFullscreen) {
                            Modifier.statusBarsPadding().displayCutoutPadding()
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                FilledTonalIconButton(
                    onClick = {
                        AuroraHaptics.tap(haptic)
                        if (isFullscreen) {
                            onFullscreenChange(false)
                        } else {
                            onBack()
                        }
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.56f),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (isFullscreen) "退出全屏" else "返回直播列表",
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.username,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = AuroraCoral,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        "LIVE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }

        // Bottom control bar
        if (!isLoading && errorMessage == null) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(AuroraMotion.fastEffects()),
                exit = fadeOut(AuroraMotion.fastEffects()),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                val haptic = LocalHapticFeedback.current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.80f)),
                            ),
                        )
                        .then(
                            if (isFullscreen) {
                                Modifier.navigationBarsPadding().displayCutoutPadding()
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    IconButton(onClick = {
                        AuroraHaptics.toggle(haptic, isPaused)
                        if (isPaused) {
                            player.play()
                            isPaused = false
                        } else {
                            player.pause()
                            isPaused = true
                        }
                    }) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "继续播放" else "暂停直播",
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = {
                        AuroraHaptics.toggle(haptic, isMuted)
                        if (isMuted) {
                            player.volume = 1f
                            isMuted = false
                        } else {
                            player.volume = 0f
                            isMuted = true
                        }
                    }) {
                        Icon(
                            if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isMuted) "开启声音" else "静音",
                            tint = Color.White,
                        )
                    }
                    if (isFullscreen) {
                        Surface(
                            onClick = {
                                val next = !isDanmakuEnabled
                                AuroraHaptics.toggle(haptic, next)
                                isDanmakuEnabled = next
                                feedbackMessage = if (next) "已开启弹幕浮层" else "已开启纯净观看模式（弹幕已关闭）"
                            },
                            shape = CircleShape,
                            color = if (isDanmakuEnabled) AuroraCoral.copy(alpha = 0.88f) else AuroraMediaSurface.copy(alpha = 0.78f),
                            contentColor = Color.White,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .height(36.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.padding(horizontal = 12.dp),
                            ) {
                                Icon(
                                    imageVector = if (isDanmakuEnabled) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                                    contentDescription = if (isDanmakuEnabled) "关闭弹幕" else "开启弹幕",
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = if (isDanmakuEnabled) "弹幕 开" else "纯净模式",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        AuroraHaptics.tap(haptic)
                        onFullscreenChange(!isFullscreen)
                    }) {
                        Icon(
                            if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isFullscreen) "退出全屏" else "全屏播放",
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveComments(
    comments: List<LiveComment>,
    isLoading: Boolean,
    isExpanded: Boolean = true,
    applyBottomInset: Boolean = true,
    compactWhenEmpty: Boolean = false,
    onToggleExpand: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) {
            listState.animateScrollToItem(comments.size - 1)
        }
    }

    Column(
        modifier
            .background(MaterialTheme.colorScheme.background)
            .then(if (applyBottomInset) Modifier.navigationBarsPadding() else Modifier)
            .padding(horizontal = 16.dp)
            .testTag("live-comments"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onToggleExpand != null) {
                        Modifier.clickable(role = Role.Button, onClick = onToggleExpand)
                    } else {
                        Modifier
                    },
                )
                .padding(top = 10.dp, bottom = 8.dp),
        ) {
            Text(
                text = "实时互动",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = "${comments.size} 条",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text("公开聊天室 · 只读", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            if (onToggleExpand != null) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "收起实时互动" else "展开实时互动",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            if (comments.isEmpty()) {
                if (compactWhenEmpty && !isLoading) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                    ) {
                        Text(
                            text = "原生低延迟直连播放中 · 当前暂无新弹幕",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        )
                    }
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = if (compactWhenEmpty) {
                            Modifier.fillMaxWidth().height(72.dp)
                        } else {
                            Modifier.fillMaxSize()
                        },
                    ) {
                        Text(
                            if (isLoading) "正在连接直播间实时评论…" else "当前暂无公开评论",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = if (applyBottomInset) 24.dp else 10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(comments, key = LiveComment::id) { comment ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.size(28.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        comment.username.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = comment.username,
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = comment.message,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveLeftDetailPanel(
    item: LiveItem,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = if (item.isHd) "高清直播" else "标清直播",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            if (item.countryCode.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = item.countryCode.uppercase(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = "${item.viewersCount} 人在线",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        OutlinedButton(
            onClick = onOpenExternal,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("在浏览器中打开直播间")
        }
    }
}

@Composable
private fun LiveRightRail(
    comments: List<LiveComment>,
    isLoadingComments: Boolean,
    isCommentsExpanded: Boolean,
    onToggleComments: () -> Unit,
    relatedItems: List<LiveItem>,
    onSelectRelated: (LiveItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        val hasRelated = relatedItems.isNotEmpty()
        LiveComments(
            comments = comments,
            isLoading = isLoadingComments,
            isExpanded = isCommentsExpanded,
            applyBottomInset = !hasRelated,
            compactWhenEmpty = hasRelated,
            onToggleExpand = onToggleComments,
            modifier = when {
                !hasRelated -> Modifier.fillMaxWidth().weight(1f)
                !isCommentsExpanded -> Modifier.fillMaxWidth()
                comments.isEmpty() && !isLoadingComments -> Modifier.fillMaxWidth()
                else -> Modifier.fillMaxWidth().weight(0.42f)
            },
        )
        if (relatedItems.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            )
            LiveRelatedStreamsList(
                relatedItems = relatedItems,
                onSelectRelated = onSelectRelated,
                modifier = if (isCommentsExpanded) {
                    Modifier.fillMaxWidth().weight(0.55f)
                } else {
                    Modifier.fillMaxWidth().weight(1f)
                },
            )
        }
    }
}

@Composable
private fun LiveRelatedStreamsList(
    relatedItems: List<LiveItem>,
    onSelectRelated: (LiveItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
        Text(
            text = "更多直播",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(top = 12.dp, bottom = 8.dp)
                .semantics { heading() },
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(relatedItems.take(20), key = LiveItem::id) { related ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(10.dp))
                        .clickable { onSelectRelated(related) }
                        .padding(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(126.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (related.thumbnailUrl.isNotBlank()) {
                            AsyncImage(
                                model = livePosterRequest(context, related.thumbnailUrl, liveReferer(related)),
                                contentDescription = related.username,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Surface(
                            color = AuroraBrandAccentDeep.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                        ) {
                            Text(
                                text = "LIVE",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = related.username,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${related.viewersCount} 观看",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (related.countryCode.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = related.countryCode.uppercase(),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal class LiveOrientationBridge(
    private val onOrientationDetected: (Boolean) -> Unit,
    private val onVideoSizeDetected: ((width: Int, height: Int) -> Unit)? = null,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onVideoSize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            val isPortrait = height > width
            mainHandler.post {
                onOrientationDetected(isPortrait)
                onVideoSizeDetected?.invoke(width, height)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun EmbeddedLivePlayer(
    url: String,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    onVideoOrientationChange: (Boolean) -> Unit = {},
    onVideoSizeChange: ((width: Int, height: Int) -> Unit)? = null,
    onCommentsChange: (List<LiveComment>) -> Unit,
    onCommentsLoadingChange: (Boolean) -> Unit,
    onOpenExternal: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    posterUrl: String? = null,
    comments: List<LiveComment> = emptyList(),
    item: LiveItem? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val orientationBridge = remember(onVideoOrientationChange, onVideoSizeChange) {
        LiveOrientationBridge(onVideoOrientationChange, onVideoSizeChange)
    }
    var roomStatus by remember(url) {
        mutableStateOf(LiveRoomStatus.CONNECTING)
    }
    var isPaused by remember(url) { mutableStateOf(false) }
    var isMuted by remember(url) { mutableStateOf(false) }
    var errorMessage by remember(url) { mutableStateOf<String?>(null) }
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var pageLoadToken by remember(url) { mutableIntStateOf(0) }
    var isDanmakuEnabled by rememberSaveable { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    val isPlayerReady = roomStatus == LiveRoomStatus.READY

    LaunchedEffect(controlsVisible, isFullscreen, isPaused) {
        if (controlsVisible && !isPaused) {
            delay(3_500L)
            controlsVisible = false
        }
    }

    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            delay(2000L)
            feedbackMessage = null
        }
    }

    val webView = remember(url) {
        WebView(context).apply webView@{
            setBackgroundColor(android.graphics.Color.BLACK)
            addJavascriptInterface(orientationBridge, "AuroraLiveBridge")
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportMultipleWindows(false)
                val defaultUa = userAgentString
                if (defaultUa != null) {
                    userAgentString = defaultUa.replace("; wv", "").replace("Version/4.0 ", "")
                }
            }
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(this@webView, true)
                listOf("https://zh.stripchat.com", "https://stripchat.com").forEach { origin ->
                    setCookie(origin, "isAdult=1; path=/; domain=.stripchat.com; Secure; SameSite=Lax")
                    setCookie(origin, "agreed_to_terms=1; path=/; domain=.stripchat.com; Secure; SameSite=Lax")
                    setCookie(origin, "hasConfirmedAge=1; path=/; domain=.stripchat.com; Secure; SameSite=Lax")
                    setCookie(origin, "isVisitorsAgreementAccepted=1; path=/; domain=.stripchat.com; Secure; SameSite=Lax")
                    setCookie(origin, "alreadyVisited=1; path=/; domain=.stripchat.com; Secure; SameSite=Lax")
                    setCookie(origin, "lang=zh; path=/; domain=.stripchat.com; Secure; SameSite=Lax")
                }
                listOf("https://www.hornystudents.com", "https://hornystudents.com", "https://chaturbate.com", "https://www.chaturbate.com").forEach { origin ->
                    setCookie(origin, "agreeterms=1; path=/; domain=.hornystudents.com; Secure; SameSite=Lax")
                    setCookie(origin, "agreeterms=1; path=/; domain=.chaturbate.com; Secure; SameSite=Lax")
                    setCookie(origin, "isAdult=1; path=/; domain=.hornystudents.com; Secure; SameSite=Lax")
                    setCookie(origin, "isAdult=1; path=/; domain=.chaturbate.com; Secure; SameSite=Lax")
                    setCookie(origin, "hasConfirmedAge=1; path=/; domain=.hornystudents.com; Secure; SameSite=Lax")
                    setCookie(origin, "hasConfirmedAge=1; path=/; domain=.chaturbate.com; Secure; SameSite=Lax")
                }
                flush()
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, location: String?, favicon: Bitmap?) {
                    pageLoadToken += 1
                    roomStatus = LiveRoomStatus.CONNECTING
                    errorMessage = null
                    onCommentsChange(emptyList())
                    onCommentsLoadingChange(true)
                }

                override fun onPageFinished(view: WebView?, location: String?) {
                    // Discovery loop determines room readiness
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    failure: WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) {
                        errorMessage = "直播页面连接失败，请检查网络后重试"
                        roomStatus = LiveRoomStatus.ERROR
                        onCommentsLoadingChange(false)
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val requestUrl = request?.url?.toString() ?: return null
                    if (shouldBlockLiveResource(requestUrl)) {
                        val isScript = requestUrl.substringBefore('?').endsWith(".js", ignoreCase = true)
                        return WebResourceResponse(
                            if (isScript) "application/javascript" else "text/plain",
                            "UTF-8",
                            ByteArrayInputStream(EMPTY_INTERCEPT_BYTES),
                        )
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return true
                    // Embedded player, captcha, and CDN frames navigate independently of the room
                    // document. Let HTTPS subframes proceed; only the main frame uses the room allowlist.
                    if (!request.isForMainFrame) {
                        return uri.scheme !in setOf("http", "https")
                    }
                    return !isAllowedLiveUrl(uri.toString())
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view == null || fullscreenView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }
                    fullscreenView = view
                    fullscreenCallback = callback
                    onFullscreenChange(true)
                }

                override fun onHideCustomView() {
                    fullscreenView = null
                    fullscreenCallback?.onCustomViewHidden()
                    fullscreenCallback = null
                    onFullscreenChange(false)
                }
            }
            loadUrl(url)
        }
    }

    LaunchedEffect(webView, url, lifecycle, pageLoadToken) {
        val playerModeScript = livePlayerModeScript(url)
        var discoveryFinished = false
        val startTime = System.currentTimeMillis()
        val timeoutMs = 35_000L
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (discoveryFinished || roomStatus != LiveRoomStatus.CONNECTING) return@repeatOnLifecycle
            while (!discoveryFinished && roomStatus == LiveRoomStatus.CONNECTING) {
                val elapsed = System.currentTimeMillis() - startTime
                val interval = if (elapsed < 3_000L) 250L else 500L
                delay(interval)
                if (discoveryFinished || roomStatus != LiveRoomStatus.CONNECTING) break
                webView.evaluateJavascript(playerModeScript) { result ->
                    if (discoveryFinished || roomStatus != LiveRoomStatus.CONNECTING) return@evaluateJavascript
                    val status = result?.trim()?.trim('"')
                    android.util.Log.d("LivePlayer", "evaluate result: $result, status: $status, elapsed: $elapsed")
                    when {
                        status?.startsWith("ready") == true -> {
                            val orient = parseLiveVideoOrientation(status)
                            if (orient != null) onVideoOrientationChange(orient)
                            roomStatus = LiveRoomStatus.READY
                            discoveryFinished = true
                        }

                        status == "ticket_show" -> {
                            if (elapsed >= MISMATCH_GRACE_MS) {
                                roomStatus = LiveRoomStatus.TICKET_SHOW
                                onCommentsLoadingChange(false)
                                discoveryFinished = true
                            }
                        }

                        status == "private_show" -> {
                            if (elapsed >= MISMATCH_GRACE_MS) {
                                roomStatus = LiveRoomStatus.PRIVATE_SHOW
                                onCommentsLoadingChange(false)
                                discoveryFinished = true
                            }
                        }

                        status == "offline" -> {
                            if (elapsed >= MISMATCH_GRACE_MS) {
                                roomStatus = LiveRoomStatus.OFFLINE
                                onCommentsLoadingChange(false)
                                discoveryFinished = true
                            }
                        }

                        status == "mismatch" -> {
                            // A WebView can briefly expose an intermediate path while the room
                            // redirects. Do not turn one transient mismatch into a dead player.
                            if (elapsed >= MISMATCH_GRACE_MS) {
                                errorMessage = "主播状态已变化，请返回列表刷新后重试"
                                roomStatus = LiveRoomStatus.ERROR
                                onCommentsLoadingChange(false)
                                discoveryFinished = true
                            }
                        }

                        else -> {
                            if (System.currentTimeMillis() - startTime >= timeoutMs) {
                                errorMessage = "未检测到可播放的视频流，主播可能已下播或受网络限制"
                                roomStatus = LiveRoomStatus.ERROR
                                onCommentsLoadingChange(false)
                                discoveryFinished = true
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(webView, url, isPlayerReady, isFullscreen, isDanmakuEnabled, lifecycle) {
        if (!isPlayerReady || (isFullscreen && !isDanmakuEnabled)) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                webView.evaluateJavascript(LIVE_COMMENTS_SCRIPT) { result ->
                    val updatedComments = parseLiveComments(result)
                    onCommentsChange(updatedComments)
                    onCommentsLoadingChange(false)
                }
                delay(COMMENTS_REFRESH_INTERVAL_MS)
            }
        }
    }

    fun exitFullscreen() {
        fullscreenView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        onFullscreenChange(false)
    }

    fun setPlaybackPaused(shouldPause: Boolean) {
        val command = if (shouldPause) {
            "window.__auroraPlaybackPaused = true; (document.querySelector('$LIVE_VIDEO_SELECTOR'))?.pause();"
        } else {
            "window.__auroraPlaybackPaused = false; (document.querySelector('$LIVE_VIDEO_SELECTOR'))?.play();"
        }
        webView.evaluateJavascript(command, null)
        isPaused = shouldPause
    }

    fun setPlaybackMuted(shouldMute: Boolean) {
        webView.evaluateJavascript(
            "const video = document.querySelector('$LIVE_VIDEO_SELECTOR'); if (video) video.muted = $shouldMute;",
            null,
        )
        isMuted = shouldMute
    }

    BackHandler(enabled = isFullscreen || fullscreenView != null) {
        if (fullscreenView != null) {
            exitFullscreen()
        } else {
            onFullscreenChange(false)
        }
    }
    DisposableEffect(webView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> LiveSessionCache.pauseAndMute(webView)
                Lifecycle.Event.ON_START -> LiveSessionCache.resumeView(
                    webView = webView,
                    shouldPlay = !isPaused,
                    shouldMute = isMuted,
                )
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            fullscreenCallback?.onCustomViewHidden()
            onFullscreenChange(false)
            LiveSessionCache.destroyView(webView)
        }
    }

    LaunchedEffect(webView, isPlayerReady) {
        if (isPlayerReady && !isMuted) {
            webView.evaluateJavascript(
                "document.querySelectorAll('video').forEach(v => { v.muted = false; v.play().catch(()=>{}); });",
                null,
            )
        }
    }

    Box(modifier.background(AuroraImmersiveBackground)) {
        AndroidView(
            factory = { webView },
            update = { view -> view.keepScreenOn = isPlayerReady && !isPaused },
            modifier = Modifier.fillMaxSize(),
        )
        if (roomStatus == LiveRoomStatus.CONNECTING) {
            Box(Modifier.fillMaxSize().background(AuroraImmersiveBackground)) {
                posterUrl?.let { poster ->
                    val fallbackReferer = item?.let(::liveReferer) ?: "${StripchatLiveRepository.SITE_BASE}/"
                    AsyncImage(
                        model = livePosterRequest(context, poster, fallbackReferer),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(Modifier.fillMaxSize().background(AuroraImmersiveBackground.copy(alpha = 0.55f)))
                }
                AuroraLoadingIndicator(color = AuroraCoral, modifier = Modifier.align(Alignment.Center))
            }
        }
        if (roomStatus != LiveRoomStatus.CONNECTING && roomStatus != LiveRoomStatus.READY) {
            LiveRoomStatusOverlay(
                status = roomStatus,
                errorMessage = errorMessage,
                onRetry = {
                    roomStatus = LiveRoomStatus.CONNECTING
                    errorMessage = null
                    pageLoadToken += 1
                    webView.reload()
                },
                onOpenExternal = onOpenExternal,
                onBack = onBack,
            )
        }
        fullscreenView?.let { view ->
            AndroidView(factory = { view }, modifier = Modifier.fillMaxSize().background(AuroraImmersiveBackground))
        }
        if (isPlayerReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        role = Role.Button
                        contentDescription = if (controlsVisible) "隐藏控制栏" else "显示控制栏"
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        controlsVisible = !controlsVisible
                    },
            )
        }

        // Danmaku Layer
        DanmakuOverlay(
            comments = comments,
            enabled = isFullscreen && isDanmakuEnabled,
            modifier = Modifier.fillMaxSize(),
        )

        // Floating Toast for Danmaku mode switch
        AnimatedVisibility(
            visible = feedbackMessage != null,
            enter = fadeIn(AuroraMotion.fastEffects()),
            exit = fadeOut(AuroraMotion.fastEffects()),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                .padding(top = 56.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.86f),
                border = BorderStroke(1.dp, if (isDanmakuEnabled) AuroraCoral else Color.White.copy(alpha = 0.34f)),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = if (isDanmakuEnabled) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                        contentDescription = null,
                        tint = if (isDanmakuEnabled) AuroraCoral else Color.LightGray,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = feedbackMessage ?: "",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Top Bar in Inline and Fullscreen
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(AuroraMotion.fastEffects()),
            exit = fadeOut(AuroraMotion.fastEffects()),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            val haptic = LocalHapticFeedback.current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent),
                        ),
                    )
                    .then(
                        if (isFullscreen) {
                            Modifier.statusBarsPadding().displayCutoutPadding()
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                FilledTonalIconButton(
                    onClick = {
                        AuroraHaptics.tap(haptic)
                        if (fullscreenView != null) {
                            exitFullscreen()
                        } else if (isFullscreen) {
                            onFullscreenChange(false)
                        } else {
                            onBack()
                        }
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.56f),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (isFullscreen) "退出全屏" else "返回直播列表",
                        modifier = Modifier.size(20.dp),
                    )
                }
                item?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = it.username,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = AuroraCoral,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            "LIVE",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${formatViewerCount(it.viewersCount)} 人",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        // Bottom control bar
        if (isPlayerReady) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(AuroraMotion.fastEffects()),
                exit = fadeOut(AuroraMotion.fastEffects()),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                val haptic = LocalHapticFeedback.current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.80f)),
                            ),
                        )
                        .then(
                            if (isFullscreen) {
                                Modifier.navigationBarsPadding().displayCutoutPadding()
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    IconButton(onClick = {
                        AuroraHaptics.toggle(haptic, isPaused)
                        setPlaybackPaused(!isPaused)
                    }) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "继续播放" else "暂停直播",
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = {
                        AuroraHaptics.toggle(haptic, isMuted)
                        setPlaybackMuted(!isMuted)
                    }) {
                        Icon(
                            if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isMuted) "开启声音" else "静音",
                            tint = Color.White,
                        )
                    }
                    if (isFullscreen) {
                        Surface(
                            onClick = {
                                val next = !isDanmakuEnabled
                                AuroraHaptics.toggle(haptic, next)
                                isDanmakuEnabled = next
                                feedbackMessage = if (next) "已开启弹幕浮层" else "已开启纯净观看模式（弹幕已关闭）"
                            },
                            shape = CircleShape,
                            color = if (isDanmakuEnabled) AuroraCoral.copy(alpha = 0.88f) else AuroraMediaSurface.copy(alpha = 0.78f),
                            contentColor = Color.White,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .height(36.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.padding(horizontal = 12.dp),
                            ) {
                                Icon(
                                    imageVector = if (isDanmakuEnabled) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                                    contentDescription = if (isDanmakuEnabled) "关闭弹幕" else "开启弹幕",
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = if (isDanmakuEnabled) "弹幕 开" else "纯净模式",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        AuroraHaptics.tap(haptic)
                        onFullscreenChange(!isFullscreen)
                    }) {
                        Icon(
                            if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isFullscreen) "退出全屏" else "全屏播放",
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

internal data class DanmakuItem(
    val id: String,
    val username: String,
    val message: String,
    val lane: Int,
    val durationMs: Int = 7500,
    val delayMs: Int = 0,
)

@Composable
internal fun DanmakuOverlay(
    comments: List<LiveComment>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return

    var activeDanmaku by remember { mutableStateOf<List<DanmakuItem>>(emptyList()) }
    val seenCommentIds = remember { mutableSetOf<String>() }
    var nextLane by remember { mutableIntStateOf(0) }

    LaunchedEffect(comments, enabled) {
        if (!enabled) {
            activeDanmaku = emptyList()
            return@LaunchedEffect
        }
        if (activeDanmaku.isEmpty() && comments.isNotEmpty()) {
            val initialBatch = comments.takeLast(6)
            initialBatch.forEach { seenCommentIds.add(it.id) }
            activeDanmaku = initialBatch.mapIndexed { index, comment ->
                DanmakuItem(
                    id = "${comment.id}_seed_$index",
                    username = comment.username,
                    message = comment.message,
                    lane = index % 4,
                    durationMs = 7000 + ((index % 4) * 350),
                    delayMs = (index / 4) * 1800 + (index % 4) * 200,
                )
            }
        } else {
            val newComments = comments.filter { seenCommentIds.add(it.id) }
            if (newComments.isNotEmpty()) {
                val newItems = newComments.take(4).mapIndexed { index, comment ->
                    val lane = (nextLane + index) % 4
                    DanmakuItem(
                        id = "${comment.id}_${System.currentTimeMillis()}_$index",
                        username = comment.username,
                        message = comment.message,
                        lane = lane,
                        durationMs = 6500 + (lane * 400),
                        delayMs = index * 350,
                    )
                }
                nextLane = (nextLane + newComments.size) % 4
                activeDanmaku = (activeDanmaku + newItems).takeLast(16)
            }
        }
    }

    Box(modifier = modifier) {
        activeDanmaku.forEach { item ->
            key(item.id) {
                FlyingDanmakuChip(
                    item = item,
                    onComplete = {
                        activeDanmaku = activeDanmaku.filter { it.id != item.id }
                    },
                )
            }
        }
    }
}

@Composable
private fun FlyingDanmakuChip(
    item: DanmakuItem,
    onComplete: () -> Unit,
) {
    val windowInfo = LocalWindowInfo.current
    val screenWidthPx = windowInfo.containerSize.width.toFloat().coerceAtLeast(360f)
    val offsetX = remember { Animatable(screenWidthPx) }

    LaunchedEffect(item.id) {
        if (item.delayMs > 0) {
            delay(item.delayMs.toLong())
        }
        offsetX.animateTo(
            targetValue = -1200f,
            animationSpec = tween(durationMillis = item.durationMs, easing = LinearEasing),
        )
        onComplete()
    }

    val laneY = 56.dp + (item.lane * 36).dp

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.value.roundToInt(), with(density) { laneY.roundToPx() }) }
            .widthIn(max = 320.dp)
            .background(Color.Black.copy(alpha = 0.60f), CircleShape)
            .border(BorderStroke(0.75.dp, Color.White.copy(alpha = 0.22f)), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = item.username,
                color = AuroraCoral,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    shadow = Shadow(color = Color.Black, blurRadius = 4f, offset = Offset(1f, 1f)),
                ),
            )
            Text(
                text = item.message.replace("\n", " ").trim(),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    shadow = Shadow(color = Color.Black, blurRadius = 4f, offset = Offset(1f, 1f)),
                ),
            )
        }
    }
}

internal fun parseLiveVideoOrientation(status: String?): Boolean? {
    if (status?.startsWith("ready") != true) return null
    val orient = status.substringAfter(":", "")
    return when (orient) {
        "portrait" -> true
        "landscape" -> false
        else -> null
    }
}

private val EMPTY_INTERCEPT_BYTES = ByteArray(0)

private val BLOCKED_HOST_KEYWORDS = listOf(
    "google-analytics.com",
    "googletagmanager.com",
    "yandex.ru",
    "mc.yandex",
    "metrika",
    "scorecardresearch.com",
    "hotjar.com",
    "sentry.io",
    "sentry-cdn.com",
    "adnxs.com",
    "doubleclick.net",
    "adroll.com",
    "criteo.com",
    "taboola.com",
    "outbrain.com",
    "exoclick.com",
    "juicyads.com",
    "trafficjunky.com",
)

private val BLOCKED_PATH_KEYWORDS = listOf(
    "wheel-of-fortune",
    "plugins/reactions",
    "canvas-performance-test",
    "fps-meter",
)

private fun liveReferer(item: LiveItem): String =
    if (item.playerUrl.contains("hornystudents.com")) "https://www.hornystudents.com/" else "${StripchatLiveRepository.SITE_BASE}/"

private fun livePosterRequest(
    context: android.content.Context,
    posterUrl: String,
    referer: String,
): ImageRequest = ImageRequest.Builder(context)
    .data(posterUrl)
    .httpHeaders(
        NetworkHeaders.Builder()
            .set("Referer", referer)
            .set("User-Agent", VideoRepository.USER_AGENT)
            .build(),
    )
    .memoryCacheKey(posterUrl)
    .build()

internal fun filterLiveItems(items: List<LiveItem>, searchQuery: String): List<LiveItem> {
    val needle = searchQuery.trim()
    if (needle.isEmpty()) return items
    return items.filter { item ->
        item.username.contains(needle, ignoreCase = true) ||
            item.countryCode.contains(needle, ignoreCase = true) ||
            liveRoomSlug(item.playerUrl).contains(needle, ignoreCase = true)
    }
}

internal fun shouldBlockLiveResource(url: String): Boolean {
    val lower = url.lowercase()
    if (BLOCKED_HOST_KEYWORDS.any { lower.contains(it) }) return true
    if (BLOCKED_PATH_KEYWORDS.any { lower.contains(it) }) return true
    return false
}

internal fun isAllowedLiveUrl(url: String): Boolean = runCatching {
    val uri = java.net.URI(url)
    val host = uri.host?.lowercase() ?: return@runCatching false
    uri.scheme.equals("https", ignoreCase = true) &&
        (host == "stripchat.com" || host.endsWith(".stripchat.com") ||
         host == "hornystudents.com" || host.endsWith(".hornystudents.com") ||
         host == "chaturbate.com" || host.endsWith(".chaturbate.com") ||
         host == "highwebmedia.com" || host.endsWith(".highwebmedia.com") ||
         host == "cb-wl.com" || host.endsWith(".cb-wl.com") ||
         host == "agego.com" || host.endsWith(".agego.com") ||
         host == "mmcdn.com" || host.endsWith(".mmcdn.com"))
}.getOrDefault(false)

internal fun formatViewerCount(value: Int): String = when {
    value >= 10_000 -> String.format(java.util.Locale.US, "%.1f万", value / 10_000f)
    value >= 1_000 -> String.format(java.util.Locale.US, "%.1f千", value / 1_000f)
    else -> value.coerceAtLeast(0).toString()
}

internal fun calculateLiveStageHeight(
    aspectRatio: Float,
    containerWidth: Dp,
    containerHeight: Dp,
    isTwoPaneLandscape: Boolean,
): Dp {
    val safeWidth = containerWidth.coerceAtLeast(160.dp)
    val safeHeight = containerHeight.coerceAtLeast(320.dp)
    val safeRatio = aspectRatio.takeIf { it.isFinite() && it > 0f }?.coerceIn(0.45f, 2.4f) ?: (16f / 9f)

    if (isTwoPaneLandscape) {
        return (safeWidth / safeRatio).coerceIn(220.dp, safeHeight * 0.64f)
    }

    return if (safeRatio < 1.0f) {
        val maxPortraitStage = (safeHeight * 0.46f).coerceIn(280.dp, 440.dp)
        (safeWidth / safeRatio).coerceIn(260.dp, maxPortraitStage)
    } else {
        val naturalHeight = safeWidth / safeRatio
        val balancedMinStage = (safeHeight * 0.30f).coerceIn(232.dp, 276.dp)
        val maxLandscapeStage = (safeHeight * 0.38f).coerceAtLeast(balancedMinStage)
        maxOf(naturalHeight, balancedMinStage).coerceAtMost(maxLandscapeStage)
    }
}

internal fun parseLiveComments(value: String): List<LiveComment> = runCatching {
    val messages = JSONArray(value)
    buildList {
        for (index in 0 until messages.length()) {
            val message = messages.optJSONObject(index) ?: continue
            val id = message.optString("id").take(64)
            val username = message.optString("username").trim().take(48)
            val text = message.optString("message").trim().take(300)
            if (id.isNotBlank() && username.isNotBlank() && text.isNotBlank()) {
                add(LiveComment(id = id, username = username, message = text))
            }
        }
    }.asReversed()
        .distinctBy(LiveComment::id)
        .distinctBy { it.username to it.message }
        .asReversed()
        .takeLast(MAX_VISIBLE_COMMENTS)
}.getOrDefault(emptyList())

private fun countryFlag(code: String): String {
    if (code.length != 2 || code.any { !it.isLetter() }) return ""
    return code.uppercase().map { character ->
        String(Character.toChars(character.code - 'A'.code + 0x1F1E6))
}.joinToString("") + " "
}

private const val LIVE_COVER_PREFETCH_LIMIT = 8
private const val COMMENTS_REFRESH_INTERVAL_MS = 1_500L
private const val MISMATCH_GRACE_MS = 3_000L
private const val MAX_VISIBLE_COMMENTS = 40
private const val LIVE_VIDEO_SELECTOR = ".web-rtc-player-wrapper video, .player-wrapper video, #mainVideo video, video"

private const val LIVE_COMMENTS_SCRIPT = """
(() => {
  const isNoise = (text) => {
    if (!text) return true;
    const lower = text.toLowerCase();
    return lower.includes('featured chat rooms') ||
           lower.includes('chat color') ||
           lower.includes('fontdefault') ||
           lower.includes('rules: no spamming') ||
           lower.includes('viewing chaturbate') ||
           lower.includes('chat settings') ||
           lower.includes('private conversation with') ||
           lower.includes('to send a tip') ||
           lower.includes('send tip') ||
           lower.includes('never contact you') ||
           lower.includes('agreeterms') ||
           lower.includes('terms of service');
  };

  const stripchatNodes = Array.from(document.querySelectorAll('.message.message-base'));
  const chaturbateNodes = Array.from(document.querySelectorAll('.chat-list li, .msg-text, [data-testid="chat-message"]'));

  if (chaturbateNodes.length > 0 && stripchatNodes.length === 0) {
    return chaturbateNodes.slice(-60).map((node, index) => {
      const userEl = node.querySelector('.username, [class*="username"], [class*="user-name"]');
      const username = (userEl?.textContent || '').replace(/[:\s]+${'$'}/, '').trim();
      const content = node.cloneNode(true);
      content.querySelectorAll('.username, [class*="user"], svg, script, style').forEach(el => el.remove());
      const message = (content.textContent || '').replace(/\s+/g, ' ').trim();
      return {
        id: `${'$'}{index}-${'$'}{username}-${'$'}{message}`,
        username,
        message,
      };
    }).filter(item => item.username && item.message && !isNoise(item.message) && !isNoise(item.username));
  }

  return stripchatNodes
    .filter(node => !String(node.className).includes('WelcomeBotMessage'))
    .slice(-60).map((node, index) => {
    const usernameNode = node.querySelector('.message-username .user-levels-username-text') ||
      node.querySelector('.message-username');
    const username = (usernameNode?.textContent || '').replace(/\s+/g, ' ').trim();
    const content = node.cloneNode(true);
    content.querySelectorAll('.message-username, svg, script, style, [class*="showMoreBtn"]').forEach(element => element.remove());
    const message = (content.textContent || '').replace(/\s+/g, ' ').trim();
    return {
      id: node.getAttribute('data-message-id') || `${'$'}{index}-${'$'}{username}-${'$'}{message}`,
      username,
      message,
    };
  }).filter(item => item.username && item.message && !isNoise(item.message) && !isNoise(item.username));
})()
"""

/** Return the room slug independent of an optional trailing slash or query string. */
internal fun liveRoomSlug(roomUrl: String): String = runCatching {
    val raw = java.net.URI(roomUrl).path.orEmpty()
        .split('/')
        .asReversed()
        .firstOrNull(String::isNotBlank)
        .orEmpty()
        .lowercase()
    raw.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
}.getOrDefault("")

/** Keep the source-owned video node and its stream while removing webpage chrome around it. */
private fun livePlayerModeScript(roomUrl: String): String {
    val expectedUsername = liveRoomSlug(roomUrl)
    return """
(() => {
  const expectedUsername = '$expectedUsername';
  const href = String(location.href || '');
  if (!/^https:\/\//i.test(href) || location.pathname === 'blank' || document.readyState === 'loading') return 'pending';
  const currentUsername = decodeURIComponent(location.pathname.split('/').filter(Boolean).pop() || '').toLowerCase();
  if (expectedUsername && currentUsername && currentUsername !== expectedUsername) return 'mismatch';

  // 1. 自动处理 Cookie 政策 / 进入确认弹层 / 年龄确认
  const closeEntrance = document.getElementById('close_entrance_terms') || document.querySelector('.close_entrance_terms, #close_entrance_terms, [id*="close_entrance"]');
  if (closeEntrance) {
    try { closeEntrance.click(); } catch(e) {}
  }
  const overlay = document.getElementById('entrance_terms_overlay') || document.getElementById('entrance_terms');
  if (overlay) {
    try { overlay.remove(); } catch(e) { overlay.style.display = 'none'; }
  }

  const acceptButtons = Array.from(document.querySelectorAll('.btn-visitors-agreement-accept, [class*=visitors-agreement-accept], button, a, [role=button]')).filter(b => {
    if (b.classList && (b.classList.contains('btn-visitors-agreement-accept') || String(b.className).includes('visitors-agreement-accept'))) return true;
    const t = (b.innerText || b.textContent || '').trim();
    return t.includes('年满') || t.includes('18岁') || t.includes('全部接受') || t.includes('接受') ||
           t.includes('Accept') || t.includes('I am 18') || t.includes('Agree') || t.includes('进入');
  });
  if (acceptButtons.length > 0) {
    try { acceptButtons[0].click(); } catch (e) {}
  }

  const playButtons = document.querySelectorAll('.vjs-big-play-button, .play-button, [class*="big-play-button"], [class*="playButton"]');
  playButtons.forEach(btn => {
    try { btn.click(); } catch (e) {}
  });

  const videoSelector = '$LIVE_VIDEO_SELECTOR';
  const reportVideoSize = (v) => {
    if (!v) return null;
    const w = v.videoWidth || 0;
    const h = v.videoHeight || 0;
    if (w > 0 && h > 0) {
      if (window.AuroraLiveBridge && window.AuroraLiveBridge.onVideoSize) {
        try { window.AuroraLiveBridge.onVideoSize(w, h); } catch (e) {}
      }
      return h > w ? 'portrait' : 'landscape';
    }
    return null;
  };

  const applyPlayerStage = (activeVideo) => {
    if (!activeVideo) activeVideo = document.querySelector(videoSelector);
    if (!activeVideo) return false;
    const player = activeVideo.closest('.player-wrapper') ||
                   activeVideo.closest('.web-rtc-player-wrapper') ||
                   activeVideo.closest('.player-holder') ||
                   activeVideo.closest('.video-js') ||
                   activeVideo.closest('#player') ||
                   activeVideo.parentElement;
    if (!player) return false;

    // Short-circuit: avoid redundant style mutations if stage is already configured for current dimensions
    const vpW = Math.round(window.visualViewport?.width || window.innerWidth || 0);
    const vpH = Math.round(window.visualViewport?.height || window.innerHeight || 0);
    const stageSig = String(vpW) + 'x' + String(vpH) + ':' + String(activeVideo.videoWidth) + 'x' + String(activeVideo.videoHeight) + ':' + String(activeVideo.currentSrc || activeVideo.src);
    if (activeVideo.__auroraStageSig === stageSig && activeVideo.parentElement === player) {
      reportVideoSize(activeVideo);
      return true;
    }
    activeVideo.__auroraStageSig = stageSig;

    const styleId = 'aurora-live-player-style';
    let style = document.getElementById(styleId);
    if (!style) {
      style = document.createElement('style');
      style.id = styleId;
      style.textContent = `
        html, body {
          width: 100% !important;
          height: 100% !important;
          margin: 0 !important;
          padding: 0 !important;
          overflow: hidden !important;
          background: #000 !important;
        }
        header, #static-header, .header, nav, footer, .room-header, .evolve-header, #room-bottom-banner,
        .language-mismatch-notice, [class*="language-mismatch"], #language-suggestion {
          display: none !important;
          height: 0 !important;
          min-height: 0 !important;
          max-height: 0 !important;
          opacity: 0 !important;
          pointer-events: none !important;
          visibility: hidden !important;
        }
        #mobile-room-root {
          top: 0px !important;
          height: 100% !important;
          min-height: 100% !important;
          max-height: 100% !important;
          width: 100% !important;
          overflow: visible !important;
          position: static !important;
        }
        #basePlayer, .videoPlayerDiv {
          top: 0px !important;
          left: 0px !important;
          height: 100% !important;
          min-height: 100% !important;
          max-height: 100% !important;
          width: 100% !important;
          min-width: 100% !important;
          overflow: visible !important;
          position: static !important;
        }
        .vjs-control-bar, .vjs-text-track-display, .vjs-loading-spinner, .vjs-big-play-button, .vjs-modal-dialog, canvas[style*="filter"] {
          display: none !important;
          opacity: 0 !important;
          pointer-events: none !important;
        }
        video, .vjs-tech {
          clip-path: none !important;
          -webkit-clip-path: none !important;
        }
        body * { visibility: hidden !important; }
      `;
      document.head.appendChild(style);
    }

    const viewportWidth = window.visualViewport?.width || window.innerWidth || document.documentElement.clientWidth || 393;
    const viewportHeight = window.visualViewport?.height || window.innerHeight || document.documentElement.clientHeight || 222;
    const stageWidth = `${'$'}{viewportWidth}px`;
    const stageHeight = `${'$'}{viewportHeight}px`;

    // 1. Move player directly to body if it is inside Chaturbate containers
    if (player.parentElement !== document.body && (player.id === 'chat-player' || document.getElementById('mobile-room-root'))) {
      document.body.appendChild(player);
    }

    // 2. Hide headers and reset roots
    const header = document.querySelector('header, .header, #static-header');
    if (header) {
      try { header.remove(); } catch(e) { header.style.display = 'none'; }
    }
    const mobileRoot = document.getElementById('mobile-room-root');
    if (mobileRoot) {
      mobileRoot.style.setProperty('top', '0px', 'important');
      mobileRoot.style.setProperty('height', '100%', 'important');
      mobileRoot.style.setProperty('overflow', 'visible', 'important');
    }
    const bp = document.getElementById('basePlayer');
    if (bp) {
      bp.style.setProperty('top', '0px', 'important');
      bp.style.setProperty('height', '100%', 'important');
      bp.style.setProperty('overflow', 'visible', 'important');
    }
    const vpd = document.querySelector('.videoPlayerDiv');
    if (vpd) {
      vpd.style.setProperty('top', '0px', 'important');
      vpd.style.setProperty('height', '100%', 'important');
      vpd.style.setProperty('width', '100%', 'important');
      vpd.style.setProperty('overflow', 'visible', 'important');
    }

    // 3. Keep player and its parents visible and sized with explicit pixels
    let current = activeVideo;
    while (current) {
      current.style.setProperty('visibility', 'visible', 'important');
      if (current === player) break;
      current.style.setProperty('width', stageWidth, 'important');
      current.style.setProperty('height', stageHeight, 'important');
      current.style.setProperty('inline-size', stageWidth, 'important');
      current.style.setProperty('block-size', stageHeight, 'important');
      current.style.setProperty('min-width', '0', 'important');
      current.style.setProperty('min-height', '0', 'important');
      current.style.setProperty('max-width', 'none', 'important');
      current.style.setProperty('max-height', 'none', 'important');
      current.style.setProperty('aspect-ratio', 'auto', 'important');
      current = current.parentElement;
    }
    current = player.parentElement;
    while (current) {
      current.style.setProperty('visibility', 'visible', 'important');
      current = current.parentElement;
    }

    // 4. Style player container to fill full viewport with black background
    player.style.setProperty('position', 'fixed', 'important');
    player.style.setProperty('inset', '0px', 'important');
    player.style.setProperty('top', '0px', 'important');
    player.style.setProperty('left', '0px', 'important');
    player.style.setProperty('width', stageWidth, 'important');
    player.style.setProperty('height', stageHeight, 'important');
    player.style.setProperty('inline-size', stageWidth, 'important');
    player.style.setProperty('block-size', stageHeight, 'important');
    player.style.setProperty('min-width', '0', 'important');
    player.style.setProperty('min-height', '0', 'important');
    player.style.setProperty('max-width', 'none', 'important');
    player.style.setProperty('max-height', 'none', 'important');
    player.style.setProperty('aspect-ratio', 'auto', 'important');
    player.style.setProperty('z-index', '2147483647', 'important');
    player.style.setProperty('margin', '0px', 'important');
    player.style.setProperty('padding', '0px', 'important');
    player.style.setProperty('border-radius', '0', 'important');
    player.style.setProperty('background', '#000', 'important');
    player.style.setProperty('overflow', 'hidden', 'important');
    player.style.setProperty('visibility', 'visible', 'important');
    player.style.setProperty('display', 'block', 'important');

    // 5. Native video: 100% full view, perfectly contained, zero crop
    activeVideo.controls = false;
    activeVideo.playsInline = true;
    activeVideo.style.setProperty('position', 'absolute', 'important');
    activeVideo.style.setProperty('inset', '0px', 'important');
    activeVideo.style.setProperty('top', '0px', 'important');
    activeVideo.style.setProperty('left', '0px', 'important');
    activeVideo.style.setProperty('visibility', 'visible', 'important');
    activeVideo.style.setProperty('width', '100%', 'important');
    activeVideo.style.setProperty('height', '100%', 'important');
    activeVideo.style.setProperty('inline-size', '100%', 'important');
    activeVideo.style.setProperty('block-size', '100%', 'important');
    activeVideo.style.setProperty('object-fit', 'contain', 'important');
    activeVideo.style.setProperty('object-position', 'center center', 'important');
    activeVideo.style.setProperty('clip-path', 'none', 'important');
    activeVideo.style.setProperty('-webkit-clip-path', 'none', 'important');
    activeVideo.style.setProperty('margin', '0px', 'important');
    activeVideo.style.setProperty('padding', '0px', 'important');
    activeVideo.style.setProperty('border', 'none', 'important');
    activeVideo.style.setProperty('background', '#000', 'important');
    activeVideo.style.setProperty('display', 'block', 'important');

    // 6. Hide Video.js sibling overlays
    if (player.children) {
      Array.from(player.children).forEach(c => {
        if (c !== activeVideo && !c.contains(activeVideo)) {
          c.style.setProperty('display', 'none', 'important');
        }
      });
    }

    if (!activeVideo.__auroraOrientationListeners) {
      activeVideo.__auroraOrientationListeners = true;
      activeVideo.addEventListener('loadedmetadata', () => reportVideoSize(activeVideo));
      activeVideo.addEventListener('resize', () => reportVideoSize(activeVideo));
      activeVideo.addEventListener('playing', () => reportVideoSize(activeVideo));
      activeVideo.addEventListener('timeupdate', () => {
        if (!activeVideo.__auroraReportedOnce && activeVideo.videoWidth > 0) {
          activeVideo.__auroraReportedOnce = true;
          reportVideoSize(activeVideo);
        }
      });
    }
    reportVideoSize(activeVideo);
    if (!activeVideo.__auroraPauseGuard) {
      activeVideo.__auroraPauseGuard = true;
      activeVideo.addEventListener('play', () => {
        if (window.__auroraPlaybackPaused) activeVideo.pause();
      });
    }
    if (window.__auroraPlaybackPaused) {
      activeVideo.pause();
    } else {
      activeVideo.play().catch(() => {});
    }
    return true;
  };

  // 2. 检查是否有公开视频
  const activeVideo = document.querySelector(videoSelector);
  if (activeVideo) {
    applyPlayerStage(activeVideo);

    if (!window.__auroraLivePlayerObserver) {
      let pending = false;
      window.__auroraLivePlayerObserver = new MutationObserver(() => {
        if (pending) return;
        pending = true;
        requestAnimationFrame(() => {
          pending = false;
          const v = document.querySelector(videoSelector);
          if (v) applyPlayerStage(v);
        });
      });
      // Scope observation to player container hierarchy to avoid firing on chatroom stream insertions
      const playerContainer = activeVideo.closest('#chat-player, #basePlayer, .player-wrapper, #mobile-room-root') || activeVideo.parentElement || document.body;
      window.__auroraLivePlayerObserver.observe(playerContainer, { childList: true, subtree: playerContainer !== document.body });
      window.addEventListener('resize', () => {
        const v = document.querySelector(videoSelector);
        if (v) {
          if (v.__auroraStageSig) v.__auroraStageSig = '';
          applyPlayerStage(v);
        }
      });
    }
    const orient = reportVideoSize(activeVideo);
    if (!orient || activeVideo.readyState < 2) return 'pending';
    return 'ready:' + orient;
  }

  // 3. 智能检测主播特殊状态（排除常规导航按钮与请求私密按钮）
  const text = (document.body?.innerText || document.body?.textContent || '').slice(0, 3000);
  if (text.includes('购票表演') || text.includes('加入购票') || text.includes('Ticket Show') || text.includes('购买代币门票') || text.includes('代币门票') ||
      document.querySelector('[class*="groupShow"], [class*="ticket-show-overlay"], .group-show-container, [data-testid="group-show-overlay"]')) {
    return 'ticket_show';
  }
  const isPrivateShow = (
    text.includes('is in a Private Show') ||
    text.includes('in a private show') ||
    text.includes('正在进行私密秀') ||
    text.includes('处于私密秀') ||
    Boolean(document.querySelector('.private-show-overlay, [data-testid="private-show-overlay"], .exclusive-show-overlay, #player .private-message, .privateOverlay:not(:empty)'))
  );
  if (isPrivateShow) {
    return 'private_show';
  }
  const isOffline = (
    text.includes('is currently offline') ||
    text.includes('currently offline') ||
    text.includes('目前离线') ||
    text.includes('主播已下播') ||
    text.includes('目前不在线') ||
    Boolean(document.querySelector('.offline-container, [data-testid="offline-banner"], .room_offline'))
  );
  if (isOffline) {
    return 'offline';
  }

  return 'missing';
})()
"""
}
