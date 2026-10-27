package com.aurorashelf.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.rememberLazyListState
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.transformations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.aurorashelf.app.data.VideoRepository
import com.aurorashelf.app.data.comic.ComicPageResolver
import com.aurorashelf.app.model.ComicDetails
import com.aurorashelf.app.model.ComicPage
import com.aurorashelf.app.model.ComicSummary
import com.aurorashelf.app.ui.theme.AuroraCoral
import com.aurorashelf.app.ui.theme.AuroraImmersiveBackground
import com.aurorashelf.app.ui.theme.AuroraMediaLabel
import com.aurorashelf.app.ui.theme.AuroraMediaMuted
import com.aurorashelf.app.ui.theme.AuroraMediaPlaceholder
import com.aurorashelf.app.ui.theme.AuroraMediaRankFill
import com.aurorashelf.app.ui.theme.AuroraMediaRankOutline
import com.aurorashelf.app.ui.theme.AuroraMediaSurface
import com.aurorashelf.app.ui.theme.AuroraMediaSurfaceVariant
import com.aurorashelf.app.ui.theme.AuroraReaderBackground
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
internal fun ComicScreen(
    state: ComicUiState,
    onCategory: (String) -> Unit,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onComic: (ComicSummary) -> Unit,
    onCloseDetails: () -> Unit,
    onChapter: (Int) -> Unit,
    onCloseReader: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onRetryChapter: () -> Unit,
    onAuthenticate: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onSettings: () -> Unit = {},
) {
    BackHandler(enabled = state.reader != null || state.details != null) {
        if (state.reader != null) onCloseReader() else onCloseDetails()
    }

    Surface(
        color = AuroraImmersiveBackground,
        modifier = Modifier.fillMaxSize(),
    ) {
        AnimatedContent(
            targetState = when {
                state.reader != null -> "reader"
                state.details != null -> "details"
                else -> "library"
            },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "comic-screen",
        ) { screen ->
            when (screen) {
                "reader" -> state.reader?.let {
                    ComicReader(
                        state = state,
                        onBack = onCloseReader,
                        onPreviousChapter = onPreviousChapter,
                        onNextChapter = onNextChapter,
                        onRetry = onRetryChapter,
                    )
                }
                "details" -> state.details?.let {
                    ComicDetailsScreen(
                        details = it,
                        onBack = onCloseDetails,
                        onChapter = onChapter,
                    )
                }
                else -> ComicLibrary(
                    state = state,
                    onCategory = onCategory,
                    onQuery = onQuery,
                    onSearch = onSearch,
                    onRefresh = onRefresh,
                    onLoadMore = onLoadMore,
                    onComic = onComic,
                    onAuthenticate = onAuthenticate,
                    onSignOut = onSignOut,
                    onSettings = onSettings,
                )
            }
        }
    }

    if (state.isDetailsLoading) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = AuroraMediaSurface,
                tonalElevation = 6.dp,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(24.dp).size(30.dp),
                    strokeWidth = 3.dp,
                    color = AuroraCoral,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ComicLibrary(
    state: ComicUiState,
    onCategory: (String) -> Unit,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onComic: (ComicSummary) -> Unit,
    onAuthenticate: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onSettings: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val fontScale = LocalDensity.current.fontScale
    val prefetchContext = LocalContext.current
    // task-17: warm the first screen's comic covers with the same requests the grid uses.
    LaunchedEffect(state.comics) {
        if (state.comics.isNotEmpty()) {
            CoverPrefetcher.prefetchComicCovers(prefetchContext, state.comics, limit = COMIC_COVER_PREFETCH_LIMIT)
        }
    }
    val selectedSource = state.sources.find { it.id == state.selectedSourceId }
    // snapshotFlow keeps firing when the next page grows the list, unlike a derivedStateOf
    // boolean that can stay latched at true and never request the following page.
    LaunchedEffect(gridState, state.selectedSourceId, state.selectedCategoryId, state.activeQuery) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 5
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }

    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = isSearchOpen) { isSearchOpen = false }

    LazyVerticalGrid(
        // C3: widen artwork columns with the system font scale (capped) so >=150% font
        // collapses the covers to a single column instead of squeezing the text.
        columns = GridCells.Adaptive(minSize = scaledAdaptiveMinSize(154.dp, fontScale)),
        state = gridState,
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 126.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding().testTag("comic-library-grid"),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            if (isSearchOpen) {
                CenteredSearchBar(
                    query = state.query,
                    onQueryChange = onQuery,
                    placeholder = "搜索漫画",
                    onSearch = onSearch,
                    onBack = {
                        isSearchOpen = false
                        if (state.query.isNotEmpty()) {
                            onQuery("")
                            onSearch()
                        }
                    },
                    onClear = {
                        onQuery("")
                        onSearch()
                    },
                    isSearching = state.isLoading,
                    modifier = Modifier.padding(bottom = 6.dp),
                    testTag = "comic-search-field",
                )
            } else {
                NetflixTopBar(
                    titleTestTag = "comic-library-title",
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    NetflixSearchCapsule(
                        label = if (state.activeQuery.isNotBlank()) state.activeQuery else "搜索漫画",
                        onClick = { isSearchOpen = true },
                    )
                    if (selectedSource?.requiresAuthentication == true && state.isSourceAuthenticated) {
                        TextButton(onClick = onSignOut) { Text("退出", color = AuroraCoral) }
                    }
                    IconButton(
                        onClick = onRefresh,
                        enabled = state.isSourceAuthenticated,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新漫画",
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
        }
        if (selectedSource?.requiresAuthentication == true && !state.isSourceAuthenticated) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ComicSourceAuthCard(
                    isLoading = state.isAuthorizing,
                    error = state.authError,
                    onAuthenticate = onAuthenticate,
                )
            }
        } else {
            stickyHeader(key = "comic-pinned-categories") {
                val categories = selectedSource?.categories.orEmpty()
                CategoryStrip(testTag = "comic-category-bar") {
                    categories.forEach { category ->
                        NetflixCategoryChip(
                            selected = category.id == state.selectedCategoryId,
                            onClick = { onCategory(category.id) },
                            label = category.label,
                        )
                    }
                }
            }

            if (state.isLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AuroraCoral)
                    }
                }
            } else if (state.error != null && state.comics.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ComicError(message = state.error, onRetry = onRefresh)
                }
            } else if (state.comics.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ComicEmpty(message = if (state.activeQuery.isBlank()) "当前漫画源暂无内容" else "没有找到相关漫画")
                }
            } else {
                // Netflix Hero Spotlight for the leading title
                if (state.activeQuery.isBlank()) {
                    val heroComic = state.comics.first()
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        NetflixComicHeroBillboard(
                            comic = heroComic,
                            onRead = { onComic(heroComic) },
                        )
                    }
                }

                // Top 10 Manga Shelf
                if (state.comics.size >= 5 && state.activeQuery.isBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        NetflixSectionHeader(
                            title = "今日热门漫画 TOP 10",
                            badgeText = "TOP 10",
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val topComics = state.comics.take(10)
                            itemsIndexed(topComics) { index, comic ->
                                NetflixComicTop10Card(
                                    rank = index + 1,
                                    comic = comic,
                                    onClick = { onComic(comic) },
                                )
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        NetflixSectionHeader(
                            title = "为你推荐",
                        )
                    }
                }

                items(state.comics, key = { "${it.sourceId}:${it.id}" }) { comic ->
                    ComicCard(comic = comic, onClick = { onComic(comic) })
                }
                if (state.isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp, color = AuroraCoral)
                        }
                    }
                }
                state.error?.let { message ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComicSourceAuthCard(
    isLoading: Boolean,
    error: String?,
    onAuthenticate: (String, String) -> Unit,
) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Surface(
        color = AuroraMediaSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF333333)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("授权哔咔漫画", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "账号仅用于换取访问令牌，密码不会保存。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AuroraMediaMuted,
                )
            }
            OutlinedTextField(
                value = account,
                onValueChange = { account = it },
                label = { Text("邮箱账号") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AuroraCoral,
                    unfocusedBorderColor = Color(0xFF444444),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAuthenticate(account, password) }),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AuroraCoral,
                    unfocusedBorderColor = Color(0xFF444444),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = { onAuthenticate(account, password) },
                enabled = !isLoading && account.isNotBlank() && password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuroraCoral,
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(9.dp))
                }
                Text(if (isLoading) "正在授权" else "登录并连接", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NetflixComicHeroBillboard(
    comic: ComicSummary,
    onRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(410.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onRead),
    ) {
        ComicCoverImage(
            comic = comic,
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
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NetflixNLogo(modifier = Modifier.size(width = 12.dp, height = 18.dp))
                Text(
                    text = "MANGA SPOTLIGHT",
                    color = AuroraCoral,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = comic.title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            if (comic.subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = comic.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = AuroraMediaLabel,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onRead()
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
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "开始阅读",
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
private fun NetflixComicTop10Card(
    rank: Int,
    comic: ComicSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rankOffset = if (rank >= 10) 56.dp else 42.dp
    val totalWidth = rankOffset + 104.dp

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
                .width(104.dp)
                .height(158.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                ComicCoverImage(
                    comic = comic,
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
                        letterSpacing = (-0.2).sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComicCard(comic: ComicSummary, onClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .applePressEffect(onClick = onClick)
            .padding(bottom = 6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = AuroraMediaSurface,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
        ) {
            Box(Modifier.fillMaxSize()) {
                ComicCoverImage(comic = comic, modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp),
                ) {
                    NetflixNLogo(modifier = Modifier.size(width = 10.dp, height = 15.dp))
                }
            }
        }
        Text(
            text = comic.title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = Color.White,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        if (comic.subtitle.isNotBlank()) {
            Text(
                text = comic.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = AuroraMediaMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun ComicDetailsScreen(
    details: ComicDetails,
    onBack: () -> Unit,
    onChapter: (Int) -> Unit,
) {
    Surface(color = AuroraImmersiveBackground, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 126.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            stickyHeader {
                Surface(color = AuroraImmersiveBackground.copy(alpha = 0.96f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回漫画列表", tint = Color.White)
                        }
                        Text(
                            text = "漫画详情",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                }
            }
            item {
                Surface(
                    color = AuroraMediaSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF262626)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AuroraMediaPlaceholder,
                            modifier = Modifier.width(120.dp).aspectRatio(0.72f),
                        ) {
                            ComicCoverImage(comic = details.comic, modifier = Modifier.fillMaxSize())
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            Text(details.comic.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold), color = Color.White)
                            if (details.comic.subtitle.isNotBlank()) {
                                Text(details.comic.subtitle, style = MaterialTheme.typography.bodySmall, color = AuroraMediaMuted)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                details.comic.tags.take(6).forEach { tag ->
                                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF262626)) {
                                        Text(tag, style = MaterialTheme.typography.labelSmall, color = AuroraMediaLabel, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                                    }
                                }
                            }
                            if (details.chapters.isNotEmpty()) {
                                Button(
                                    onClick = { onChapter(0) },
                                    shape = RoundedCornerShape(4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                                    modifier = Modifier.height(38.dp),
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("开始阅读", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            if (details.description.isNotBlank()) {
                item {
                    Surface(
                        color = AuroraMediaSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFF262626)),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                            Text("简介", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(details.description, style = MaterialTheme.typography.bodyMedium, color = AuroraMediaLabel)
                        }
                    }
                }
            }
            item {
                Text(
                    "章节 (${details.chapters.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                )
            }
            itemsIndexed(details.chapters, key = { _, ch -> ch.id }) { index, chapter ->
                Surface(
                    color = AuroraMediaSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF222222)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { onChapter(index) },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(
                            chapter.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = AuroraMediaMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComicReader(
    state: ComicUiState,
    onBack: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onRetry: () -> Unit,
) {
    val reader = requireNotNull(state.reader)
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Sliding window page prefetch: pre-resolves and warms ahead 3 pages off the main thread
    LaunchedEffect(listState, reader.pages) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { currentIndex ->
                val ahead = (currentIndex + 1..currentIndex + 3).filter { it in reader.pages.indices }
                withContext(Dispatchers.IO) {
                    ahead.forEach { idx ->
                        val p = reader.pages[idx]
                        runCatching {
                            val resolved = ComicPageResolver.resolve(p)
                            val req = ImageRequest.Builder(context)
                                .data(resolved.imageUrl)
                                .httpHeaders(
                                    NetworkHeaders.Builder()
                                        .set("Referer", resolved.referer)
                                        .set("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
                                        .build(),
                                )
                                .memoryCacheKey("${resolved.imageUrl}#segments=${resolved.verticalSegments}")
                                .diskCacheKey(resolved.imageUrl)
                                .apply {
                                    if (resolved.verticalSegments > 1) {
                                        transformations(JmUnscrambleTransformation(resolved.verticalSegments))
                                    }
                                }
                                .build()
                            context.imageLoader.enqueue(req)
                        }
                    }
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 72.dp, bottom = 110.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.isReaderLoading) {
                item {
                    Box(Modifier.fillMaxWidth().height(420.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            } else if (state.readerError != null) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 120.dp),
                    ) {
                        Icon(Icons.Default.BrokenImage, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
                        Text(state.readerError, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = onRetry) { Text("重试") }
                    }
                }
            } else {
                itemsIndexed(reader.pages, key = { index, page -> "$index:${page.imageUrl}" }) { index, page ->
                    ComicPageImage(page = page, pageNumber = index + 1)
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                    ) {
                        TextButton(
                            onClick = onPreviousChapter,
                            enabled = reader.chapterIndex > 0,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                            Text("上一章")
                        }
                        Text(
                            "${reader.chapterIndex + 1} / ${reader.details.chapters.size}",
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        TextButton(
                            onClick = onNextChapter,
                            enabled = reader.chapterIndex < reader.details.chapters.lastIndex,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        ) {
                            Text("下一章")
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
            }
        }

        Surface(
            color = Color.Black.copy(alpha = 0.82f),
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.statusBarsPadding().height(64.dp).padding(horizontal = 8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出阅读")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reader.details.comic.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(reader.chapter.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.72f))
                }
            }
        }
    }
}

@Composable
private fun ComicPageImage(page: ComicPage, pageNumber: Int) {
    var retryKey by remember { mutableIntStateOf(0) }
    var loadState by remember(page, retryKey) { mutableIntStateOf(0) }
    val context = LocalContext.current
    val resolvedResult by produceState<Result<ComicPage>?>(
        initialValue = page.takeIf { it.resolutionUrl == null }?.let(Result.Companion::success),
        key1 = page,
        key2 = retryKey,
    ) {
        value = runCatching { ComicPageResolver.resolve(page) }
    }
    val resolvedPage = resolvedResult?.getOrNull()
    val request = remember(resolvedPage, retryKey, context) {
        resolvedPage?.let { resolved ->
            ImageRequest.Builder(context)
                .data(resolved.imageUrl)
                .httpHeaders(
                    NetworkHeaders.Builder()
                        .set("Referer", resolved.referer)
                        .set("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
                        .build(),
                )
                .memoryCacheKey("${resolved.imageUrl}#segments=${resolved.verticalSegments}")
                .diskCacheKey(resolved.imageUrl)
                .apply {
                    if (resolved.verticalSegments > 1) {
                        transformations(JmUnscrambleTransformation(resolved.verticalSegments))
                    }
                }
                .build()
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().background(AuroraReaderBackground),
    ) {
        request?.let {
            AsyncImage(
                model = it,
                contentDescription = "第 $pageNumber 页",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                onLoading = { loadState = 0 },
                onSuccess = { loadState = 1 },
                onError = { loadState = 2 },
            )
        }
        when {
            resolvedResult?.isFailure == true -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                TextButton(onClick = { retryKey++ }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("第 $pageNumber 页解析失败", color = Color.White)
                }
            }
            resolvedResult == null || loadState == 0 -> Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
            }
            loadState == 2 -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                TextButton(onClick = { retryKey++ }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("第 $pageNumber 页加载失败", color = Color.White)
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun ComicCoverImage(comic: ComicSummary, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Shared with CoverPrefetcher so warm-up and the visible list use identical data/cache keys.
    val request = remember(comic.coverUrl, comic.coverReferer, comic.sourceId, context) {
        comicCoverRequest(context, comic)
    }
    if (request == null) {
        ComicCoverPlaceholder()
    } else {
        Box(modifier = modifier) {
            AsyncImage(
                model = request,
                contentDescription = "${comic.title}封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            DarkModeArtworkScrim()
        }
    }
}

@Composable
private fun ComicError(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 72.dp),
    ) {
        Icon(Icons.Default.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(42.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun ComicEmpty(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 72.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(42.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ComicCoverPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(38.dp),
        )
    }
}

/** First-screen comic covers warmed through the shared task-17 CoverPrefetcher. */
private const val COMIC_COVER_PREFETCH_LIMIT = 10
