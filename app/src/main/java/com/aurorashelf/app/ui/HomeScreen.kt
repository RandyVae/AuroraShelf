package com.aurorashelf.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import com.aurorashelf.app.model.FeedCategory
import com.aurorashelf.app.model.VideoItem
import com.aurorashelf.app.ui.theme.AuroraBrandAccentDeep
import com.aurorashelf.app.ui.theme.AuroraCoral
import com.aurorashelf.app.ui.theme.AuroraDimens
import com.aurorashelf.app.ui.theme.AuroraImmersiveBackground
import com.aurorashelf.app.ui.theme.AuroraMediaInk
import com.aurorashelf.app.ui.theme.AuroraMediaLabel
import com.aurorashelf.app.ui.theme.AuroraMediaMeta
import com.aurorashelf.app.ui.theme.AuroraMediaMuted
import com.aurorashelf.app.ui.theme.AuroraMediaOnImage
import com.aurorashelf.app.ui.theme.AuroraMediaOutline
import com.aurorashelf.app.ui.theme.AuroraMediaPlaceholder
import com.aurorashelf.app.ui.theme.AuroraMediaRankFill
import com.aurorashelf.app.ui.theme.AuroraMediaRankOutline
import com.aurorashelf.app.ui.theme.AuroraMediaSurfaceVariant
import com.aurorashelf.app.ui.theme.AuroraProfileAccent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

private val HomeBackground = AuroraImmersiveBackground
private val HomeAccent = AuroraCoral
private val HomeAccentDeep = AuroraBrandAccentDeep
private val HomeMediaInk = AuroraMediaInk
private val HomeMediaLabel = AuroraMediaLabel
private val HomeMediaMeta = AuroraMediaMeta
private val HomeMuted = AuroraMediaMuted
private val HomeMediaOnImage = AuroraMediaOnImage
private val HomeMediaOutline = AuroraMediaOutline
private val HomeMediaPlaceholder = AuroraMediaPlaceholder
private val HomeMediaRankFill = AuroraMediaRankFill
private val HomeMediaRankOutline = AuroraMediaRankOutline
private val HomeMediaSurfaceVariant = AuroraMediaSurfaceVariant
private val HomeProfileAccent = AuroraProfileAccent

@Composable
internal fun HomeScreen(
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
    val fontScale = LocalDensity.current.fontScale
    val gridColumns = if (fontScale >= SINGLE_COLUMN_FONT_SCALE) 1 else 2
    val gridRows = remember(state.videos, gridColumns) {
        homeGridRows(state.videos, gridColumns)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
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
                        Spacer(Modifier.height(130.dp))
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
                    val hero = state.videos.first()
                    item(key = "hero-billboard") {
                        NetflixHeroBillboard(
                            video = hero,
                            isFavorite = hero.id in state.favoriteIds,
                            onPlay = { onVideo(hero) },
                            onFavorite = { onFavorite(hero) },
                            onDetails = { onVideo(hero) },
                        )
                    }

                    state.message?.let { message ->
                        item(key = "status-banner") {
                            StatusBanner(message = message, onRefresh = onRefresh)
                        }
                    }

                    if (state.videos.size > 1) {
                        item(key = "top-10-heading") {
                            Text(
                                text = "今日热播 Top 10",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 19.sp,
                                ),
                                color = Color.White,
                                modifier = Modifier
                                    .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp)
                                    .semantics { heading() },
                            )
                        }
                        item(key = "top-10-row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                itemsIndexed(
                                    items = state.videos.drop(1).take(10),
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

                    if (state.videos.size > 11) {
                        item(key = "recommended-heading") {
                            Text(
                                text = "为你推荐",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 19.sp,
                                ),
                                color = Color.White,
                                modifier = Modifier
                                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 10.dp)
                                    .semantics { heading() },
                            )
                        }
                        item(key = "recommended-row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                items(
                                    items = state.videos.drop(11).take(10),
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

                    if (state.videos.size > 21) {
                        item(key = "trending-heading") {
                            Text(
                                text = "热播精选",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 19.sp,
                                ),
                                color = Color.White,
                                modifier = Modifier
                                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 10.dp)
                                    .semantics { heading() },
                            )
                        }
                        item(key = "trending-row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                items(
                                    items = state.videos.drop(21).take(10),
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

                    item(key = "explore-heading") {
                        Text(
                            text = "更多精彩视频",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 19.sp,
                            ),
                            color = Color.White,
                            modifier = Modifier
                                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp)
                                .semantics { heading() },
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
                            if (gridColumns > 1 && row.size == 1) Spacer(Modifier.weight(1f))
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
                                color = HomeMuted,
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

            val headerModifier = if (isScrolled) {
                Modifier
                    .background(Color(0xFF000000).copy(alpha = 0.96f))
                    .border(BorderStroke(0.5.dp, Color(0xFF222222)), RectangleShape)
            } else {
                Modifier.background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.40f),
                            Color.Transparent,
                        ),
                    ),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .then(headerModifier)
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                HomeHeader(
                    onSearch = onSearch,
                    onSettings = onSettings,
                )
                CategoryBar(
                    selected = state.category,
                    sourceUrl = state.sourceUrl,
                    onCategory = onCategory,
                    isScrolled = isScrolled,
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

internal fun homeGridRows(videos: List<VideoItem>, columns: Int = 2): List<List<VideoItem>> =
    videos.drop(1).chunked(columns)

@Composable
private fun HomeHeader(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NetflixTopBar(
        modifier = modifier,
    ) {
        NetflixSearchCapsule(
            label = "搜索视频",
            onClick = onSearch,
        )
        NetflixProfileAvatar(
            onClick = onSettings,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

@Composable
private fun CategoryBar(
    selected: FeedCategory,
    sourceUrl: String,
    onCategory: (FeedCategory) -> Unit,
    modifier: Modifier = Modifier,
    isScrolled: Boolean = false,
) {
    CategoryStrip(
        modifier = modifier,
        testTag = "home-category-bar",
        isScrolled = isScrolled,
        applyBackground = false,
    ) {
        FeedCategory.availableFor(sourceUrl).forEach { category ->
            NetflixCategoryChip(
                selected = selected == category,
                onClick = { onCategory(category) },
                label = category.displayLabel(sourceUrl),
                hasDropdown = category == FeedCategory.ALL,
            )
        }
    }
}

@Composable
private fun NetflixHeroBillboard(
    video: VideoItem,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(460.dp)
            .clickable(role = Role.Button, onClick = onDetails),
    ) {
        MediaImage(
            video = video,
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
                            HomeBackground.copy(alpha = 0.88f),
                            HomeBackground.copy(alpha = 0.45f),
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
                            0.62f to HomeBackground.copy(alpha = 0.65f),
                            0.82f to HomeBackground.copy(alpha = 0.94f),
                            1.0f to HomeBackground,
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
                    text = "高清 1080P",
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
                                onFavorite()
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
                        onPlay()
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
                                onDetails()
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
    val numberOffset = if (rank >= 10) 64.dp else 44.dp
    val totalWidth = numberOffset + 112.dp

    Box(
        modifier = modifier
            .width(totalWidth)
            .height(168.dp)
            .clickable(role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
    ) {
        // LAYER 0: The huge stylized rank number (behind poster)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-4).dp, y = 10.dp),
        ) {
            Text(
                text = rankText,
                fontSize = 96.sp,
                lineHeight = 96.sp,
                fontWeight = FontWeight.Black,
                style = TextStyle(
                    drawStyle = Stroke(width = 8f, join = StrokeJoin.Round),
                ),
                color = Color(0xFF6B6B6B),
            )
            Text(
                text = rankText,
                fontSize = 96.sp,
                lineHeight = 96.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF141414),
            )
        }

        // LAYER 1: The Poster Card (overlapping the right side of the number)
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF1E1E1E),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .width(112.dp)
                .height(160.dp),
            shadowElevation = 6.dp,
        ) {
            Box(Modifier.fillMaxSize()) {
                MediaImage(
                    video = video,
                    modifier = Modifier.fillMaxSize(),
                )
                // Netflix red "TOP 10" badge in top-right corner
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(
                            Color(0xFFE50914),
                            RoundedCornerShape(bottomStart = 4.dp),
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "TOP 10",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                    )
                }
                if (video.duration.isNotBlank() && video.duration != "--:--") {
                    Text(
                        text = video.duration,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
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
                .background(HomeMediaPlaceholder),
        ) {
            MediaImage(video = video, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            ) {
                NetflixNLogo(modifier = Modifier.size(width = 11.dp, height = 17.dp))
            }
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
                    color = Color.White,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${video.author} · ${video.views}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = HomeMuted,
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
                    tint = if (isFavorite) AuroraCoral else Color(0xFF888888),
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
