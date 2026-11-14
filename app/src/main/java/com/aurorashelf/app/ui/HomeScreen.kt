package com.aurorashelf.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurorashelf.app.model.SourceCategory
import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.VideoItem
import com.aurorashelf.app.ui.theme.AuroraCoral
import com.aurorashelf.app.ui.theme.AuroraDimens
import com.aurorashelf.app.ui.theme.AuroraImmersiveBackground
import com.aurorashelf.app.ui.theme.AuroraMediaLabel
import com.aurorashelf.app.ui.theme.AuroraMediaPlaceholder
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

private val HomeBackground = AuroraImmersiveBackground
private val HomeAccent = AuroraCoral
private val HomeMediaLabel = AuroraMediaLabel
private val HomeMediaPlaceholder = AuroraMediaPlaceholder

@Composable
internal fun HomeScreen(
    state: AuroraUiState,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    onSource: (String) -> Unit,
    onCategory: (SourceCategory) -> Unit,
    onVideo: (VideoItem) -> Unit,
    onFavorite: (VideoItem) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    val density = LocalDensity.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val emptyHeaderSpacer = remember(headerHeightPx, density) {
        if (headerHeightPx > 0) with(density) { headerHeightPx.toDp() + 12.dp } else 130.dp
    }
    val listState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 24
        }
    }

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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val gridColumns = when {
            fontScale >= SINGLE_COLUMN_FONT_SCALE -> 1
            maxWidth >= 840.dp -> 4
            maxWidth >= 600.dp -> 3
            else -> 2
        }
        val spotlightCount = if (state.videos.size >= 16) 4 else 1
        val spotlightVideos = remember(state.videos, spotlightCount) {
            state.videos.take(spotlightCount)
        }
        val afterSpotlight = remember(state.videos, spotlightCount) {
            state.videos.drop(spotlightCount)
        }
        val top10Videos = remember(afterSpotlight) { afterSpotlight.take(10) }
        val afterTop10 = remember(afterSpotlight, top10Videos) { afterSpotlight.drop(top10Videos.size) }
        val recommendedVideos = remember(afterTop10) {
            if (afterTop10.size > 10) afterTop10.take(10) else emptyList()
        }
        val afterRecommended = remember(afterTop10, recommendedVideos) {
            afterTop10.drop(recommendedVideos.size)
        }
        val trendingVideos = remember(afterRecommended) {
            if (afterRecommended.size > 14) afterRecommended.take(10) else emptyList()
        }
        val gridVideos = remember(afterRecommended, trendingVideos) {
            afterRecommended.drop(trendingVideos.size)
        }
        val gridRows = remember(gridVideos, gridColumns) {
            gridVideos.chunked(gridColumns)
        }

            LazyColumn(
                state = listState,
                flingBehavior = ScrollableDefaults.flingBehavior(),
                contentPadding = PaddingValues(bottom = LocalDockHeight.current + 32.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("home-feed"),
            ) {
                if (state.videos.isEmpty()) {
                    item(key = "empty-header-space") {
                        Spacer(Modifier.height(emptyHeaderSpacer))
                    }
                    state.message?.let { message ->
                        item(key = "empty-message") {
                            StatusBanner(message = message, onRefresh = onRefresh)
                        }
                    }
                    if (state.isLoading) {
                        item(key = "empty-loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFFE50914),
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    }
                } else {
                    item(key = "hero-billboard") {
                        NetflixHeroBillboard(
                            videos = spotlightVideos,
                            favoriteIds = state.favoriteIds,
                            onPlay = { onVideo(it) },
                            onFavorite = { onFavorite(it) },
                            onDetails = { onVideo(it) },
                        )
                    }

                    item(key = "account-onboarding") {
                        AccountOnboardingCard(onOpenAccount = onOpenAccount)
                    }

                    state.message?.let { message ->
                        item(key = "status-banner") {
                            StatusBanner(message = message, onRefresh = onRefresh)
                        }
                    }

                    if (top10Videos.isNotEmpty()) {
                        item(key = "top-10-heading") {
                            NetflixSectionHeader(
                                title = "今日热播 Top 10",
                                subtitle = "每日精选热门影视排行榜",
                            )
                        }
                        item(key = "top-10-row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                itemsIndexed(
                                    items = top10Videos,
                                    key = { _, v -> "top10-${v.id}" },
                                ) { index, video ->
                                    NetflixTop10Card(
                                        rank = index + 1,
                                        video = video,
                                        onClick = { onVideo(video) },
                                    )
                                }
                            }
                        }
                    }

                    if (recommendedVideos.isNotEmpty()) {
                        item(key = "recommended-heading") {
                            NetflixSectionHeader(
                                title = "为你推荐",
                                subtitle = "猜你喜欢的精彩大片",
                            )
                        }
                        item(key = "recommended-row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                items(
                                    items = recommendedVideos,
                                    key = { "rec-${it.id}" },
                                ) { video ->
                                    NetflixPosterShelfCard(
                                        video = video,
                                        onClick = { onVideo(video) },
                                    )
                                }
                            }
                        }
                    }

                    if (trendingVideos.isNotEmpty()) {
                        item(key = "trending-heading") {
                            NetflixSectionHeader(
                                title = "热播精选",
                                subtitle = "高口碑热门精选内容",
                            )
                        }
                        item(key = "trending-row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                items(
                                    items = trendingVideos,
                                    key = { "trend-${it.id}" },
                                ) { video ->
                                    NetflixPosterShelfCard(
                                        video = video,
                                        onClick = { onVideo(video) },
                                    )
                                }
                            }
                        }
                    }

                    if (gridRows.isNotEmpty()) {
                        item(key = "explore-heading") {
                            NetflixSectionHeader(
                                title = "更多精彩视频",
                                subtitle = "海量全库深度探索",
                            )
                        }

                        items(
                            items = gridRows,
                            key = { row -> row.joinToString(separator = "|") { it.id } },
                        ) { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
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
                                if (gridColumns > 1 && row.size < gridColumns) {
                                    repeat(gridColumns - row.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    if (state.isLoadingMore) {
                        item(key = "loading-more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFFE50914),
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    } else if (!state.canLoadMore && state.videos.isNotEmpty()) {
                        item(key = "end-of-feed") {
                            Text(
                                text = "已加载全部内容",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            val overlayDarkHeader = !isScrolled && state.videos.isNotEmpty()
            val headerModifier = if (isScrolled) {
                Modifier
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                    .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant), RectangleShape)
            } else if (overlayDarkHeader) {
                Modifier.background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.40f),
                            Color.Transparent,
                        ),
                    ),
                )
            } else {
                Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .then(headerModifier)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .onSizeChanged { size ->
                        if (size.height > 0) headerHeightPx = size.height
                    },
            ) {
                HomeHeader(
                    onSearch = onSearch,
                    onSettings = onSettings,
                    forceDark = overlayDarkHeader,
                )
                CategoryBar(
                    selected = state.category,
                    sourceUrl = state.sourceUrl,
                    onCategory = onCategory,
                    isScrolled = isScrolled,
                    forceDark = overlayDarkHeader,
                )
                if (state.isLoading) {
                    LinearProgressIndicator(
                        color = Color(0xFFE50914),
                        trackColor = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                    )
                }
            }
    }
}

@Composable
private fun AccountOnboardingCard(onOpenAccount: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, HomeAccent.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("home-account-onboarding"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = HomeAccent, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("注册免费账号", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("同步片单与历史，后续会员权益也会保留在账号中", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onOpenAccount, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                Text("注册 / 登录")
            }
        }
    }
}

internal fun homeGridRows(videos: List<VideoItem>, columns: Int = 2): List<List<VideoItem>> =
    videos.drop(1).chunked(columns)

@Composable
private fun HomeHeader(
    onSearch: () -> Unit,
    onSettings: () -> Unit = {},
    forceDark: Boolean = false,
    modifier: Modifier = Modifier,
) {
    NetflixTopBar(
        modifier = modifier,
        forceDark = forceDark,
    ) {
        NetflixSearchCapsule(
            label = "搜索视频",
            onClick = onSearch,
            forceDark = forceDark,
        )
    }
}

@Composable
private fun CategoryBar(
    selected: SourceCategory,
    sourceUrl: String,
    onCategory: (SourceCategory) -> Unit,
    modifier: Modifier = Modifier,
    isScrolled: Boolean = false,
    forceDark: Boolean = false,
) {
    CategoryStrip(
        modifier = modifier,
        testTag = "home-category-bar",
        isScrolled = isScrolled,
        applyBackground = false,
        forceDark = forceDark,
    ) {
        ContentSourceCatalog.sourceCategories(sourceUrl).forEach { category ->
            NetflixCategoryChip(
                selected = selected == category,
                onClick = { onCategory(category) },
                label = category.label,
                hasDropdown = category.isAll,
                forceDark = forceDark,
            )
        }
    }
}

@Composable
private fun NetflixHeroBillboard(
    videos: List<VideoItem>,
    favoriteIds: Set<String>,
    onPlay: (VideoItem) -> Unit,
    onFavorite: (VideoItem) -> Unit,
    onDetails: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (videos.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    var currentIndex by remember { mutableIntStateOf(0) }
    val video = videos.getOrNull(currentIndex) ?: videos.first()
    val isFavorite = video.id in favoriteIds

    // 6.5s 自动轮播聚光灯画廊
    LaunchedEffect(videos.size) {
        if (videos.size > 1) {
            while (true) {
                delay(6500)
                currentIndex = (currentIndex + 1) % videos.size
            }
        }
    }

    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val heroHeight = (screenHeightDp * 0.72f).coerceIn(300f, 460f).dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clickable(role = Role.Button, onClick = { onDetails(video) }),
    ) {
        val heroVideo = remember(video) {
            val heroUrl = video.heroThumbnailUrl
            if (heroUrl.isNullOrBlank()) video else video.copy(thumbnailUrl = heroUrl)
        }
        MediaImage(
            video = heroVideo,
            modifier = Modifier.fillMaxSize(),
        )

        // Top dark vignette gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.88f),
                            Color.Black.copy(alpha = 0.45f),
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
                            0.38f to Color.Transparent,
                            0.62f to Color.Black.copy(alpha = 0.65f),
                            0.82f to Color.Black.copy(alpha = 0.90f),
                            1.0f to Color.Black.copy(alpha = 0.96f),
                        ),
                    ),
                ),
        )

        // Overlaid Billboard Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "热门精选",
                    color = HomeMediaLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "  •  ",
                    color = HomeAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "多线路直连",
                    color = HomeMediaLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (video.duration.isNotBlank()) {
                    Text(
                        text = "  •  ",
                        color = HomeAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = video.duration,
                        color = HomeMediaLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            role = Role.Button,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onFavorite(video)
                            },
                        )
                        .padding(vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = if (isFavorite) "已在片单" else "我的片单",
                        tint = if (isFavorite) HomeAccent else Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isFavorite) "已在片单" else "我的片单",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = if (isFavorite) HomeAccent else Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPlay(video)
                    },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .padding(horizontal = 8.dp),
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
                            text = "播放",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.Black,
                            ),
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            role = Role.Button,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDetails(video)
                            },
                        )
                        .padding(vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "详情",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "详情",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            if (videos.size > 1) {
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 2.dp),
                ) {
                    videos.indices.forEach { index ->
                        val isSelected = index == currentIndex
                        val targetWidth by animateDpAsState(
                            targetValue = if (isSelected) 22.dp else 6.dp,
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
                            label = "indicator-width",
                        )
                        Box(
                            modifier = Modifier
                                .width(targetWidth)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.35f))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentIndex = index
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NetflixTop10Card(
    rank: Int,
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val rankText = "$rank"
    val rankWidth = if (rank >= 10) 30.dp else 20.dp
    val totalWidth = 140.dp + rankWidth + 8.dp

    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .width(totalWidth)
            .clickable(role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
    ) {
        // Subtle 34sp Apple TV translucent rank watermark
        Box(
            modifier = Modifier
                .width(rankWidth)
                .padding(bottom = 36.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = rankText,
                fontSize = 34.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
        Spacer(Modifier.width(8.dp))
        // Standard 140x210 poster card with metadata below
        Column(modifier = Modifier.width(140.dp)) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1E1E1E),
                modifier = Modifier
                    .width(140.dp)
                    .height(210.dp),
                shadowElevation = 6.dp,
            ) {
                Box(Modifier.fillMaxSize()) {
                    MediaImage(
                        video = video,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (video.duration.isNotBlank() && video.duration != "--:--") {
                        Text(
                            text = video.duration,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.72f),
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = video.title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 16.sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = listOf(video.views, video.author)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
            .clip(RoundedCornerShape(12.dp))
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
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            MediaImage(video = video, modifier = Modifier.fillMaxSize())
            if (video.duration.isNotBlank()) {
                Text(
                    text = video.duration,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(HomeBackground.copy(alpha = 0.78f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(video.author, video.views)
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
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
                modifier = Modifier
                    .size(36.dp)
                    .offset(x = 6.dp, y = (-4).dp),
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏视频",
                    tint = if (isFavorite) AuroraCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Font scale at which the two-column home feed drops to one column (HIG C7). */
private const val SINGLE_COLUMN_FONT_SCALE = 1.5f

/** Font scale at which the fixed-width search entry becomes an icon-only button (HIG C7/E6). */
private const val LARGE_FONT_SEARCH_SCALE = 1.5f
