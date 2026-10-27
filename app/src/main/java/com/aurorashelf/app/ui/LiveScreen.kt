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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
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
import kotlin.math.roundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.aurorashelf.app.data.StripchatLiveRepository
import com.aurorashelf.app.model.LiveCategory
import com.aurorashelf.app.model.LiveComment
import com.aurorashelf.app.model.LiveItem
import com.aurorashelf.app.model.LiveSourceCatalog
import com.aurorashelf.app.ui.theme.AuroraCoral
import com.aurorashelf.app.ui.theme.AuroraImmersiveBackground
import com.aurorashelf.app.ui.theme.AuroraMediaLabel
import com.aurorashelf.app.ui.theme.AuroraMediaMuted
import com.aurorashelf.app.ui.theme.AuroraMediaPlaceholder
import com.aurorashelf.app.ui.theme.AuroraMediaRankFill
import com.aurorashelf.app.ui.theme.AuroraMediaRankOutline
import com.aurorashelf.app.ui.theme.AuroraMediaSurface
import java.io.ByteArrayInputStream
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
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (LiveItem) -> Unit,
    onClose: () -> Unit,
    onSettings: () -> Unit = {},
) {
    BackHandler(enabled = state.selectedItem != null && !isFullscreen) { onClose() }
    Surface(color = AuroraImmersiveBackground, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            state.selectedItem?.let { item ->
                LivePlayerScreen(
                    item = item,
                    isFullscreen = isFullscreen,
                    onFullscreenChange = onFullscreenChange,
                    onVideoOrientationChange = onVideoOrientationChange,
                    onBack = onClose,
                )
            } ?: LiveFeed(
                state = state,
                bottomPadding = bottomPadding,
                onCategory = onCategory,
                onSelectSource = onSelectSource,
                onRefresh = onRefresh,
                onLoadMore = onLoadMore,
                onOpen = onOpen,
                onSettings = onSettings,
            )
            // Keep the last opened room's WebView alive so re-entering it is instant.
            val retained = LiveSessionCache.retained
            if (state.selectedItem == null && !isFullscreen && retained != null) {
                key(retained) {
                    AndroidView(factory = { retained }, modifier = Modifier.size(1.dp))
                }
            }
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
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (LiveItem) -> Unit,
    onSettings: () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    // C3: with >=150% system font the artwork columns widen with the font scale (capped),
    // so the adaptive grid collapses to one column instead of clipping card text.
    val gridState = rememberLazyGridState()
    val prefetchContext = LocalContext.current
    // task-17: warm the first screen's previews (bytes + decode) without touching card layout.
    LaunchedEffect(state.items) {
        if (state.items.isNotEmpty()) {
            CoverPrefetcher.prefetchLive(prefetchContext, state.items, limit = LIVE_COVER_PREFETCH_LIMIT)
        }
    }
    LaunchedEffect(gridState, state.items.size, state.canLoadMore) {
        snapshotFlow {
            val layout = gridState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to layout.totalItemsCount
        }.map { (lastVisible, totalItems) ->
            totalItems > 0 && lastVisible >= totalItems - 5
        }.distinctUntilChanged().filter { it }.collect {
            onLoadMore()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = scaledAdaptiveMinSize(164.dp, fontScale)),
        state = gridState,
        contentPadding = PaddingValues(start = 18.dp, top = 20.dp, end = 18.dp, bottom = bottomPadding + 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding().testTag("live-grid"),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            LiveHeader(onRefresh = onRefresh, onSettings = onSettings)
        }
        stickyHeader(key = "live-pinned-categories") {
            CategoryStrip(testTag = "live-category-bar") {
                LiveCategory.entries.forEach { category ->
                    NetflixCategoryChip(
                        selected = state.selectedCategory == category,
                        onClick = { onCategory(category) },
                        label = category.label,
                    )
                }
            }
        }

        if (state.isLoading && state.items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(320.dp)) {
                    CircularProgressIndicator(color = AuroraCoral)
                }
            }
        } else if (state.error != null && state.items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LiveError(message = state.error, onRetry = onRefresh)
            }
        } else if (state.items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(260.dp)) {
                    Text("当前分类没有公开直播", color = AuroraMediaMuted)
                }
            }
        } else {
            // Netflix Live Hero Billboard
            val heroItem = state.items.first()
            item(span = { GridItemSpan(maxLineSpan) }) {
                NetflixLiveHeroBillboard(
                    item = heroItem,
                    onOpen = { onOpen(heroItem) },
                )
            }

            // Top 10 Live Performers Shelf
            if (state.items.size >= 5) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    NetflixSectionHeader(
                        title = "人气主播 TOP 10",
                        badgeText = "TOP 10",
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val topPerformers = state.items.take(10)
                        itemsIndexed(topPerformers) { index, item ->
                            NetflixLiveTop10Card(
                                rank = index + 1,
                                item = item,
                                onClick = { onOpen(item) },
                            )
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    NetflixSectionHeader(
                        title = "正在直播",
                    )
                }
            }

            items(state.items, key = LiveItem::id) { item ->
                LiveCard(item = item, onClick = { onOpen(item) })
            }
        }

        if (state.isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
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
}

@Composable
private fun LiveHeader(
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    NetflixTopBar(
        modifier = Modifier.padding(bottom = 6.dp),
    ) {
        IconButton(
            onClick = onRefresh,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "刷新直播列表",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        NetflixProfileAvatar(
            onClick = onSettings,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

@Composable
private fun NetflixLiveHeroBillboard(
    item: LiveItem,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val flag = countryFlag(item.countryCode)
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(410.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onOpen),
    ) {
        AsyncImage(
            model = previewRequest,
            contentDescription = "${item.username} 直播预览",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Top dark vignette gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            AuroraImmersiveBackground.copy(alpha = 0.85f),
                            AuroraImmersiveBackground.copy(alpha = 0.4f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        // Bottom dark dissolve gradient
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
                            0.65f to AuroraImmersiveBackground.copy(alpha = 0.72f),
                            0.84f to AuroraImmersiveBackground.copy(alpha = 0.95f),
                            1.0f to AuroraImmersiveBackground,
                        ),
                    ),
                ),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NetflixNLogo(modifier = Modifier.size(width = 12.dp, height = 18.dp))
                NetflixBadge(text = "● LIVE 直播中", backgroundColor = AuroraCoral)
                if (item.isHd) {
                    NetflixBadge(text = "1080P 高清", backgroundColor = Color(0xFF262626))
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
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = AuroraMediaLabel,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOpen()
                },
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 0.dp),
                modifier = Modifier.height(38.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "进入直播间",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.Black,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun NetflixLiveTop10Card(
    rank: Int,
    item: LiveItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val rankOffset = if (rank >= 10) 56.dp else 42.dp
    val totalWidth = rankOffset + 120.dp

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

    Box(
        modifier = modifier
            .width(totalWidth)
            .height(168.dp)
            .applePressEffect(onClick = onClick),
    ) {
        NetflixRankNumber(
            rank = rank,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-4).dp, y = 8.dp),
        )

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = AuroraMediaPlaceholder,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .width(120.dp)
                .height(158.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = previewRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(AuroraCoral, RoundedCornerShape(bottomEnd = 4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "TOP 10",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Text(
                    text = "${formatViewerCount(item.viewersCount)} 人",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(AuroraImmersiveBackground.copy(alpha = 0.78f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun LiveCard(item: LiveItem, onClick: () -> Unit) {
    val flag = countryFlag(item.countryCode)
    val context = LocalContext.current
    val previewBytes by produceState<ByteArray?>(initialValue = null, key1 = item.thumbnailUrl) {
        value = withContext(Dispatchers.IO) {
            CoverImageLoader.load(item.thumbnailUrl, liveReferer(item))
        }
    }
    // URL-keyed memory cache so scrolling back does not decode the same ByteArray again.
    val previewRequest = remember(previewBytes, item.thumbnailUrl, context) {
        previewBytes?.takeIf { it.isNotEmpty() }?.let { bytes ->
            ImageRequest.Builder(context)
                .data(bytes)
                .memoryCacheKey(coverMemoryCacheKey(item.thumbnailUrl))
                .build()
        }
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = AuroraMediaSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .applePressEffect(onClick = onClick)
            .semantics { contentDescription = "观看 ${item.username} 的公开直播" }
            .testTag("live-card"),
    ) {
        Column {
            Box {
                val isHs = item.playerUrl.contains("hornystudents.com")
                AsyncImage(
                    model = previewRequest,
                    contentDescription = "${item.username} 直播预览",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (isHs) 4f / 3f else 16 / 10f),
                )
                DarkModeArtworkScrim()
                Surface(
                    color = AuroraCoral,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
                        Text("LIVE", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold))
                    }
                }
                if (item.isHd) {
                    // A bare white glyph can drop below 3:1 on bright thumbnails; the scrim
                    // keeps the HD affordance readable on any cover image.
                    Surface(
                        color = AuroraImmersiveBackground.copy(alpha = 0.65f),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    ) {
                        Icon(
                            Icons.Default.Hd,
                            contentDescription = "高清直播",
                            tint = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp).size(16.dp),
                        )
                    }
                }
            }
            Text(
                text = "$flag ${item.username}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 8.dp, top = 3.dp, end = 8.dp, bottom = 8.dp),
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, tint = AuroraMediaMuted, modifier = Modifier.size(14.dp))
                Text(
                    text = "${formatViewerCount(item.viewersCount)} 人观看",
                    color = AuroraMediaMuted,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LiveError(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 96.dp, horizontal = 24.dp),
    ) {
        Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(42.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRetry) { Text("重试") }
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
                        containerColor = AuroraCoral,
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
                        containerColor = AuroraCoral,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("刷新状态")
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
    onFullscreenToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF262626),
            modifier = Modifier.size(38.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = item.username.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.username,
                    color = Color.White,
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
                    color = AuroraCoral,
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
                    color = AuroraMediaMuted,
                    fontSize = 11.sp,
                )
                if (item.isHd) {
                    Surface(
                        color = Color(0xFF2E333D),
                        shape = RoundedCornerShape(3.dp),
                    ) {
                        Text(
                            "HD",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
        IconButton(onClick = onFullscreenToggle, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Fullscreen,
                contentDescription = "全屏播放",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onOpenExternal, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "在系统浏览器中打开",
                tint = AuroraMediaMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun LivePlayerScreen(
    item: LiveItem,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    onVideoOrientationChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var comments by remember(item.id) { mutableStateOf<List<LiveComment>>(emptyList()) }
    var hasCheckedComments by remember(item.id) { mutableStateOf(false) }
    var videoDimensions by remember(item.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    val videoAspectRatio = remember(videoDimensions, item.playerUrl) {
        videoDimensions?.let { (w, h) ->
            if (w > 0 && h > 0) w.toFloat() / h.toFloat() else null
        } ?: if (item.playerUrl.contains("hornystudents.com")) (4f / 3f) else (16f / 9f)
    }
    val cachedSession = remember(item.playerUrl) { LiveSessionCache.take(item.playerUrl) }
    if (cachedSession == null) {
        LaunchedEffect(item.playerUrl) { LiveSessionCache.releaseIfNot(item.playerUrl) }
    }
    val openInBrowser: () -> Unit = remember(item.playerUrl, context) {
        {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.playerUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize().background(AuroraImmersiveBackground).testTag("live-player")) {
        if (!isFullscreen) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回直播列表",
                        tint = Color.White,
                    )
                }
                Text(
                    item.username,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = openInBrowser) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "在系统浏览器中打开",
                        tint = Color.LightGray,
                        modifier = Modifier.size(18.dp),
                    )
                }
                val siteName = if (item.playerUrl.contains("hornystudents.com")) "HornyStudents" else "Stripchat"
                Text(siteName, color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
            }
        }
        val playerModifier = if (isFullscreen) {
            Modifier.fillMaxSize()
        } else {
            val ratio = videoAspectRatio
            if (ratio < 1.0f) {
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .aspectRatio(ratio)
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
            }
        }
        EmbeddedLivePlayer(
            url = item.playerUrl,
            isFullscreen = isFullscreen,
            onFullscreenChange = onFullscreenChange,
            onVideoOrientationChange = onVideoOrientationChange,
            onVideoSizeChange = { w, h -> videoDimensions = Pair(w, h) },
            onCommentsChange = { comments = it },
            onCommentsLoadingChange = { hasCheckedComments = !it },
            onOpenExternal = openInBrowser,
            onBack = onBack,
            modifier = playerModifier,
            posterUrl = item.thumbnailUrl,
            reused = cachedSession,
            comments = comments,
            item = item,
        )
        if (!isFullscreen) {
            StreamerInfoBar(
                item = item,
                onOpenExternal = openInBrowser,
                onFullscreenToggle = { onFullscreenChange(true) },
            )
            LiveComments(
                comments = comments,
                isLoading = !hasCheckedComments,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

@Composable
private fun LiveComments(
    comments: List<LiveComment>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) {
            listState.animateScrollToItem(comments.size - 1)
        }
    }

    Column(modifier.background(AuroraImmersiveBackground).padding(horizontal = 16.dp).testTag("live-comments")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 8.dp),
        ) {
            Text(
                text = "实时互动",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                color = Color(0xFF262626),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = "${comments.size} 条",
                    color = AuroraCoral,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text("公开聊天室 · 只读", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }
        if (comments.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    if (isLoading) "正在连接直播间实时评论…" else "当前暂无公开评论",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(comments, key = LiveComment::id) { comment ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF141414), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF242730),
                            contentColor = Color.White,
                            border = BorderStroke(1.dp, Color(0xFF383E4C)),
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
                                color = AuroraCoral,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = comment.message,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
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
    reused: CachedLiveSession? = null,
    comments: List<LiveComment> = emptyList(),
    item: LiveItem? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val orientationBridge = remember(onVideoOrientationChange, onVideoSizeChange) {
        LiveOrientationBridge(onVideoOrientationChange, onVideoSizeChange)
    }
    var roomStatus by remember(url) {
        mutableStateOf(if (reused?.ready == true) LiveRoomStatus.READY else LiveRoomStatus.CONNECTING)
    }
    var isPaused by remember(url) { mutableStateOf(false) }
    var isMuted by remember(url) { mutableStateOf(false) }
    var errorMessage by remember(url) { mutableStateOf<String?>(null) }
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var pageLoadToken by remember(url) { mutableStateOf(0) }
    var isDanmakuEnabled by rememberSaveable { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    val isPlayerReady = roomStatus == LiveRoomStatus.READY

    LaunchedEffect(controlsVisible, isFullscreen) {
        if (isFullscreen && controlsVisible) {
            delay(4000L)
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
        (reused?.webView ?: WebView(context)).apply webView@{
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
                        return WebResourceResponse(
                            "text/plain",
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
            if (reused == null) {
                loadUrl(url)
            } else {
                settings.mediaPlaybackRequiresUserGesture = false
            }
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

    LaunchedEffect(webView, url, isPlayerReady, lifecycle) {
        if (!isPlayerReady) return@LaunchedEffect
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
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            fullscreenCallback?.onCustomViewHidden()
            onFullscreenChange(false)
            (webView.parent as? ViewGroup)?.removeView(webView)
            if (isPlayerReady && roomStatus == LiveRoomStatus.READY) {
                // Pause + mute and keep it for an instant re-entry; released when leaving the tab.
                LiveSessionCache.retain(webView, url, isPlayerReady)
            } else {
                webView.stopLoading()
                webView.destroy()
            }
        }
    }

    LaunchedEffect(webView, isPlayerReady) {
        if (isPlayerReady && !isMuted) {
            webView.evaluateJavascript(
                "document.querySelectorAll('video').forEach(v => { v.muted = false; v.play().catch(()=>{}); });",
                null,
            )
        }
        if (isPlayerReady) {
            for (i in 0 until 8) {
                delay(1000L)
                webView.evaluateJavascript(
                    """
                    (function() {
                      const v = document.querySelector('$LIVE_VIDEO_SELECTOR');
                      if (v && v.videoWidth > 0 && v.videoHeight > 0) {
                        if (window.AuroraLiveBridge && window.AuroraLiveBridge.onVideoSize) {
                          try { window.AuroraLiveBridge.onVideoSize(v.videoWidth, v.videoHeight); } catch(e) {}
                        }
                        return v.videoHeight > v.videoWidth ? 'portrait' : 'landscape';
                      }
                      return null;
                    })()
                    """.trimIndent(),
                ) { res ->
                    val orient = res?.trim()?.trim('"')
                    if (orient == "portrait") onVideoOrientationChange(true)
                    else if (orient == "landscape") onVideoOrientationChange(false)
                }
            }
        }
    }

    Box(modifier.background(AuroraImmersiveBackground)) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        if (roomStatus == LiveRoomStatus.CONNECTING) {
            Box(Modifier.fillMaxSize().background(AuroraImmersiveBackground)) {
                posterUrl?.let { poster ->
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(poster).memoryCacheKey(poster).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(Modifier.fillMaxSize().background(AuroraImmersiveBackground.copy(alpha = 0.55f)))
                }
                CircularProgressIndicator(color = AuroraCoral, modifier = Modifier.align(Alignment.Center))
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
        if (isFullscreen && isPlayerReady) {
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
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 56.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xDD000000),
                border = BorderStroke(1.dp, if (isDanmakuEnabled) AuroraCoral else Color(0x55FFFFFF)),
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

        // Top Bar in Fullscreen
        if (isFullscreen) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent),
                            ),
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "退出全屏",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    item?.let {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = it.username,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = AuroraCoral,
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
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${formatViewerCount(it.viewersCount)} 人",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // Danmaku mode switch pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isDanmakuEnabled) AuroraCoral.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, if (isDanmakuEnabled) AuroraCoral else Color.White.copy(alpha = 0.35f)),
                        onClick = {
                            isDanmakuEnabled = !isDanmakuEnabled
                            feedbackMessage = if (isDanmakuEnabled) "已开启弹幕浮层" else "已开启纯净观看模式（评论已关闭）"
                        },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = if (isDanmakuEnabled) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = if (isDanmakuEnabled) "弹幕 开" else "纯净模式",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        // Bottom control bar
        if (isPlayerReady) {
            AnimatedVisibility(
                visible = !isFullscreen || controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                            ),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    IconButton(onClick = { setPlaybackPaused(!isPaused) }) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "继续播放" else "暂停直播",
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = { setPlaybackMuted(!isMuted) }) {
                        Icon(
                            if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isMuted) "开启声音" else "静音",
                            tint = Color.White,
                        )
                    }
                    if (isFullscreen) {
                        IconButton(onClick = {
                            isDanmakuEnabled = !isDanmakuEnabled
                            feedbackMessage = if (isDanmakuEnabled) "已开启弹幕浮层" else "已开启纯净观看模式（评论已关闭）"
                        }) {
                            Icon(
                                if (isDanmakuEnabled) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                                contentDescription = if (isDanmakuEnabled) "关闭弹幕 (开启纯净模式)" else "开启弹幕",
                                tint = if (isDanmakuEnabled) AuroraCoral else Color.LightGray,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { onFullscreenChange(!isFullscreen) }) {
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
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
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
            .background(Color(0x99000000), RoundedCornerShape(14.dp))
            .border(BorderStroke(0.75.dp, Color(0x33FFFFFF)), RoundedCornerShape(14.dp))
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
  const stripchatNodes = Array.from(document.querySelectorAll('.message.message-base'));
  const chaturbateNodes = Array.from(document.querySelectorAll('.chat-list li, .msg-text, [data-testid="chat-message"]'));

  if (chaturbateNodes.length > 0 && stripchatNodes.length === 0) {
    return chaturbateNodes.slice(-60).map((node, index) => {
      const userEl = node.querySelector('.username, [class*="user"], a');
      const username = (userEl?.textContent || '').replace(/[:\s]+${'$'}/, '').trim();
      const content = node.cloneNode(true);
      content.querySelectorAll('.username, [class*="user"], svg, script, style').forEach(el => el.remove());
      const message = (content.textContent || '').replace(/\s+/g, ' ').trim();
      return {
        id: `${'$'}{index}-${'$'}{username}-${'$'}{message}`,
        username,
        message,
      };
    }).filter(item => item.username && item.message);
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
  }).filter(item => item.username && item.message);
})()
"""

/** Return the room slug independent of an optional trailing slash or query string. */
internal fun liveRoomSlug(roomUrl: String): String = runCatching {
    java.net.URI(roomUrl).path.orEmpty()
        .split('/')
        .asReversed()
        .firstOrNull(String::isNotBlank)
        .orEmpty()
        .lowercase()
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
        header, #static-header, .header, nav, footer, .room-header, .evolve-header, #room-bottom-banner {
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
      window.__auroraLivePlayerObserver.observe(document.body, { childList: true, subtree: true });
      window.addEventListener('resize', () => {
        const v = document.querySelector(videoSelector);
        if (v) applyPlayerStage(v);
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
