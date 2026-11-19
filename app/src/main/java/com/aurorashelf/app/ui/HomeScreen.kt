@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.aurorashelf.app.ui

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurorashelf.app.data.AccountRepository
import com.aurorashelf.app.data.AccountState
import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.SourceCategory
import com.aurorashelf.app.model.VideoItem
import com.aurorashelf.app.ui.theme.AuroraHaptics
import com.aurorashelf.app.ui.theme.AuroraMediaLabel
import com.aurorashelf.app.ui.theme.AuroraMotion
import com.aurorashelf.app.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

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
    val context = LocalContext.current
    val accountState by remember(context) { AccountRepository.get(context).state }.collectAsStateWithLifecycle()
    val fontScale = LocalDensity.current.fontScale
    val density = LocalDensity.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val emptyHeaderSpacer = remember(headerHeightPx, density) {
        if (headerHeightPx > 0) with(density) { headerHeightPx.toDp() + 12.dp } else 132.dp
    }
    val listState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 24
        }
    }

    LaunchedEffect(
        listState,
        state.videos.size,
        state.canLoadMore,
        state.isLoading,
        state.isRefreshing,
        state.isLoadingMore,
        state.loadMoreError,
    ) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItems = layout.totalItemsCount
            totalItems > 0 && lastVisible >= totalItems - 6 &&
                !state.isLoading && !state.isRefreshing && !state.isLoadingMore &&
                state.canLoadMore && state.loadMoreError == null
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
        // Align with iOS HomeScreen: Hero (up to 4) + Top 10 horizontal shelf (when feed has plenty),
        // then feed all remaining videos straight into the unlimited vertical exploration grid so page 1
        // immediately renders the multi-column library instead of hiding 34 items in horizontal rows.
        val top10Videos = remember(afterSpotlight) {
            if (afterSpotlight.size >= 10) afterSpotlight.take(10) else emptyList()
        }
        val gridVideos = remember(afterSpotlight, top10Videos) {
            val remaining = afterSpotlight.drop(top10Videos.size)
            if (remaining.isEmpty()) afterSpotlight else remaining
        }
        val gridRows = remember(gridVideos, gridColumns) {
            gridVideos.chunked(gridColumns)
        }

        AuroraPullToRefreshBox(
            isRefreshing = (state.isLoading || state.isRefreshing) && state.videos.isNotEmpty(),
            onRefresh = onRefresh,
            indicatorPaddingTop = emptyHeaderSpacer,
            modifier = Modifier.fillMaxSize(),
        ) {
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
                                AuroraLoadingIndicator(contained = true)
                            }
                        }
                    }
                } else {
                    item(key = "hero-billboard") {
                        HeroSpotlightBanner(
                            videos = spotlightVideos,
                            favoriteIds = state.favoriteIds,
                            onPlay = { onVideo(it) },
                            onFavorite = { onFavorite(it) },
                            onDetails = { onVideo(it) },
                        )
                    }

                    if (accountState !is AccountState.SignedIn) {
                        item(key = "account-onboarding") {
                            AccountOnboardingCard(onOpenAccount = onOpenAccount)
                        }
                    }

                    state.message?.let { message ->
                        item(key = "status-banner") {
                            StatusBanner(message = message, onRefresh = onRefresh)
                        }
                    }

                    if (top10Videos.isNotEmpty()) {
                        item(key = "top-10-heading") {
                            AuroraSectionHeader(
                                title = "今日热播 Top 10",
                                subtitle = "每日精选热门影视排行榜",
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        item(key = "top-10-row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                itemsIndexed(
                                    items = top10Videos,
                                    key = { _, v -> "top10-${v.id}" },
                                ) { index, video ->
                                    Top10ExpressiveCard(
                                        rank = index + 1,
                                        video = video,
                                        onClick = { onVideo(video) },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                    }

                    if (gridRows.isNotEmpty()) {
                        item(key = "explore-heading") {
                            val sectionTitle = if (state.category.isAll) {
                                "全部作品"
                            } else {
                                "${state.category.label} · 全部作品"
                            }
                            val countBadge = "${state.videos.size}${if (state.canLoadMore) "+" else ""}"
                            AuroraSectionHeader(
                                title = sectionTitle,
                                subtitle = "已加载 $countBadge 部视频 · 上滑持续加载",
                                modifier = Modifier.padding(top = 12.dp),
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
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .animateItem(),
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
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                            ) {
                                Spacer(Modifier.weight(1f))
                                AuroraLoadingIndicator(modifier = Modifier.size(28.dp))
                                Text(
                                    text = "正在加载更多视频…",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    } else if (state.loadMoreError != null && state.videos.isNotEmpty()) {
                        item(key = "load-more-error") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                TextButton(onClick = onLoadMore) {
                                    Text("${state.loadMoreError}（点击重试）")
                                }
                            }
                        }
                    } else if (state.canLoadMore && state.videos.isNotEmpty() && !state.isRefreshing) {
                        item(key = "load-more-trigger") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                TextButton(onClick = onLoadMore) {
                                    Text("继续加载更多")
                                }
                            }
                        }
                    } else if (!state.canLoadMore && state.videos.isNotEmpty()) {
                        item(key = "end-of-feed") {
                            Text(
                                text = "已加载全部 ${state.videos.size} 部内容",
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
        }

        val overlayDarkHeader = !isScrolled && state.videos.isNotEmpty()
        val headerModifier = if (isScrolled) {
            Modifier.background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f))
        } else if (overlayDarkHeader) {
            Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.82f),
                        Color.Black.copy(alpha = 0.36f),
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
            if (isScrolled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            }
            if (state.isLoading) {
                AuroraWavyProgressBar(
                    trackColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AccountOnboardingCard(onOpenAccount: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("home-account-onboarding"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, contentDescription = null)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "注册免费账号",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "同步片单与历史，后续会员权益也会保留在账号中",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = {
                    AuroraHaptics.tap(haptic)
                    onOpenAccount()
                },
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
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
    AuroraTopBar(
        modifier = modifier,
        forceDark = forceDark,
    ) {
        AuroraSearchCapsule(
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
            AuroraFilterChip(
                selected = selected == category,
                onClick = { onCategory(category) },
                label = category.label,
                hasDropdown = category.isAll,
                forceDark = forceDark,
            )
        }
    }
}

internal enum class HeroPresentationStyle {
    FULL_BLEED,
    CINEMA_STAGE,
}

internal fun resolveHeroPresentationStyle(
    imageWidthPx: Int,
    imageHeightPx: Int,
    containerWidthPx: Int,
    containerHeightPx: Int,
): HeroPresentationStyle = HeroPresentationStyle.FULL_BLEED

@Composable
private fun HeroSpotlightBanner(
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
    val reduceMotion = LocalReduceMotion.current

    LaunchedEffect(videos.size, reduceMotion) {
        if (videos.size > 1 && !reduceMotion) {
            while (true) {
                delay(6500)
                currentIndex = (currentIndex + 1) % videos.size
            }
        }
    }

    val density = LocalDensity.current
    val containerHeightPx = LocalWindowInfo.current.containerSize.height
    val screenHeightDp = if (containerHeightPx > 0) with(density) { containerHeightPx.toDp().value } else 720f
    val heroHeight = (screenHeightDp * 0.68f).coerceIn(320f, 480.dp.value).dp
    val heroVideo = remember(video) {
        val heroUrl = video.heroThumbnailUrl
        if (heroUrl.isNullOrBlank()) video else video.copy(thumbnailUrl = heroUrl)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clipToBounds()
            .clickable(role = Role.Button, onClick = {
                AuroraHaptics.tap(haptic)
                onDetails(video)
            }),
    ) {
        MediaImage(
            video = heroVideo,
            contentScale = ContentScale.Crop,
            originalSize = true,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.80f),
                            Color.Black.copy(alpha = 0.36f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.74f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.36f to Color.Transparent,
                            0.64f to Color.Black.copy(alpha = 0.62f),
                            0.84f to Color.Black.copy(alpha = 0.88f),
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
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(Modifier.height(6.dp))

            val meta = buildList {
                add("热门精选")
                add("多线路直连")
                video.duration.takeIf { it.isNotBlank() && it != "--:--" }?.let(::add)
            }.joinToString("  ·  ")
            Text(
                text = meta,
                style = MaterialTheme.typography.labelMedium,
                color = AuroraMediaLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(14.dp))

            // Material 3 Expressive pill action cluster
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilledTonalIconButton(
                    onClick = {
                        AuroraHaptics.toggle(haptic, !isFavorite)
                        onFavorite(video)
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.18f),
                        contentColor = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                    ),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = if (isFavorite) "已在片单" else "我的片单",
                    )
                }

                Button(
                    onClick = {
                        AuroraHaptics.confirm(haptic)
                        onPlay(video)
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 0.dp),
                    modifier = Modifier.height(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "播放",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        AuroraHaptics.tap(haptic)
                        onDetails(video)
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "详情",
                    )
                }
            }

            if (videos.size > 1) {
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    videos.indices.forEach { index ->
                        val isSelected = index == currentIndex
                        val targetWidth by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 6.dp,
                            animationSpec = AuroraMotion.fastSpatial(),
                            label = "hero-indicator-width",
                        )
                        Box(
                            modifier = Modifier
                                .width(targetWidth)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.36f))
                                .clickable {
                                    AuroraHaptics.segmentTick(haptic)
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
private fun Top10ExpressiveCard(
    rank: Int,
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rankWidth = if (rank >= 10) 36.dp else 24.dp
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.width(146.dp + rankWidth + 8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(rankWidth)
                .padding(bottom = 44.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AuroraRankNumber(rank = rank)
        }
        Spacer(Modifier.width(8.dp))
        AuroraPosterCard(
            video = video,
            onClick = onClick,
        )
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
        modifier = modifier.testTag("home-grid-card"),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 10f),
            ) {
                MediaImage(video = video, modifier = Modifier.fillMaxSize())
                if (video.duration.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.74f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                    ) {
                        Text(
                            text = video.duration,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOf(video.author, video.views)
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString(" · ")
                            .ifBlank { " " },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = {
                        AuroraHaptics.toggle(haptic, !isFavorite)
                        onFavorite()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "取消收藏" else "收藏视频",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private const val SINGLE_COLUMN_FONT_SCALE = 1.5f
