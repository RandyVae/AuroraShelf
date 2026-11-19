@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.aurorashelf.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Precision
import coil3.size.Size
import com.aurorashelf.app.data.comic.ComicDownloadStatus
import com.aurorashelf.app.data.comic.ComicPageResolver
import com.aurorashelf.app.data.comic.DownloadedComicChapter
import com.aurorashelf.app.model.ComicChapter
import com.aurorashelf.app.model.ComicDetails
import com.aurorashelf.app.model.ComicPage
import com.aurorashelf.app.model.ComicSummary
import com.aurorashelf.app.ui.theme.AuroraCoral
import com.aurorashelf.app.ui.theme.AuroraHaptics
import com.aurorashelf.app.ui.theme.AuroraImmersiveBackground
import com.aurorashelf.app.ui.theme.AuroraMediaLabel
import com.aurorashelf.app.ui.theme.AuroraMediaPlaceholder
import com.aurorashelf.app.ui.theme.AuroraMotion
import com.aurorashelf.app.ui.theme.AuroraReaderBackground
import com.aurorashelf.app.ui.theme.LocalAuroraSemanticColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext

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
    onDownloadChapter: ((ComicChapter) -> Unit)? = null,
    onDownloadAll: (() -> Unit)? = null,
    downloadedChapters: List<DownloadedComicChapter> = emptyList(),
    onRetryComicDownload: ((String) -> Unit)? = null,
    onRemoveComicDownload: ((String) -> Unit)? = null,
    onCancelOpening: () -> Unit = {},
    onDismissDetailsError: () -> Unit = {},
    onRetryOpening: () -> Unit = {},
) {
    var showDownloadSheet by rememberSaveable { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        val comicScreenTransition = AuroraMotion.expressiveFadeThrough()
        AnimatedContent(
            targetState = when {
                state.reader != null -> "reader"
                state.details != null -> "details"
                else -> "library"
            },
            transitionSpec = { comicScreenTransition },
            label = "comic-screen",
        ) { screen ->
            when (screen) {
                "reader" -> state.reader?.let {
                    PredictiveBackContainer(enabled = true, onBack = onCloseReader) {
                        ComicReader(
                            state = state,
                            onBack = onCloseReader,
                            onPreviousChapter = onPreviousChapter,
                            onNextChapter = onNextChapter,
                            onRetry = onRetryChapter,
                            onDownloadChapter = onDownloadChapter,
                            downloadedChapters = downloadedChapters,
                        )
                    }
                }
                "details" -> state.details?.let {
                    PredictiveBackContainer(enabled = true, onBack = onCloseDetails) {
                        ComicDetailsScreen(
                            details = it,
                            onBack = onCloseDetails,
                            onChapter = onChapter,
                            onDownloadChapter = onDownloadChapter,
                            onDownloadAll = onDownloadAll,
                            downloadedChapters = downloadedChapters,
                            onOpenDownloadProgress = { showDownloadSheet = true },
                            onRetryComicDownload = onRetryComicDownload,
                        )
                    }
                }
                else -> ComicLibrary(
                    state = state,
                    downloadedChapters = downloadedChapters,
                    onCategory = onCategory,
                    onQuery = onQuery,
                    onSearch = onSearch,
                    onRefresh = onRefresh,
                    onLoadMore = onLoadMore,
                    onComic = onComic,
                    onAuthenticate = onAuthenticate,
                    onSignOut = onSignOut,
                    onSettings = onSettings,
                    onOpenDownloadProgress = { showDownloadSheet = true },
                )
            }
        }
    }

    if (showDownloadSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDownloadSheet = false },
            sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.PartiallyExpanded)),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            ComicDownloadProgressSheetContent(
                chapters = downloadedChapters,
                onRetry = { id -> onRetryComicDownload?.invoke(id) },
                onRemove = { id -> onRemoveComicDownload?.invoke(id) },
            )
        }
    }

    if (state.isDetailsLoading) {
        val blockerInteraction = remember { MutableInteractionSource() }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = blockerInteraction,
                    indication = null,
                    onClick = {},
                ),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                ) {
                    AuroraLoadingIndicator(contained = true)
                    Text(
                        text = "正在加载章节…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    state.pendingComicForRetry?.title?.takeIf { it.isNotBlank() }?.let { comicTitle ->
                        Text(
                            text = comicTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                    TextButton(onClick = onCancelOpening) {
                        Text("取消")
                    }
                }
            }
        }
    }

    if (state.detailsError != null) {
        AlertDialog(
            onDismissRequest = onDismissDetailsError,
            title = { Text("无法打开漫画") },
            text = { Text(state.detailsError) },
            confirmButton = {
                if (state.pendingComicForRetry != null) {
                    TextButton(onClick = onRetryOpening) {
                        Text("重试")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDetailsError) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ComicLibrary(
    state: ComicUiState,
    downloadedChapters: List<DownloadedComicChapter>,
    onCategory: (String) -> Unit,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onComic: (ComicSummary) -> Unit,
    onAuthenticate: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onSettings: () -> Unit,
    onOpenDownloadProgress: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val fontScale = LocalDensity.current.fontScale
    val prefetchContext = LocalContext.current
    LaunchedEffect(state.comics) {
        if (state.comics.isNotEmpty()) {
            CoverPrefetcher.prefetchComicCovers(prefetchContext, state.comics, limit = COMIC_COVER_PREFETCH_LIMIT)
        }
    }
    val selectedSource = state.sources.find { it.id == state.selectedSourceId }
    LaunchedEffect(
        gridState,
        state.selectedSourceId,
        state.selectedCategoryId,
        state.activeQuery,
        state.canLoadMore,
        state.isLoading,
        state.isLoadingMore,
        state.loadMoreError,
    ) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 5 &&
                !state.isLoading && !state.isLoadingMore &&
                state.canLoadMore && state.loadMoreError == null
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }

    var isSearchOpen by rememberSaveable { mutableStateOf(false) }

    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 40
        }
    }

    val bottomInset = LocalDockHeight.current + 24.dp
    PredictiveBackContainer(
        enabled = isSearchOpen,
        onBack = { isSearchOpen = false },
    ) {
        AuroraPullToRefreshBox(
            isRefreshing = state.isLoading && state.comics.isNotEmpty(),
            onRefresh = onRefresh,
            indicatorPaddingTop = 132.dp,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = scaledAdaptiveMinSize(154.dp, fontScale)),
                state = gridState,
                contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = bottomInset),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("comic-library-grid"),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(130.dp))
                }

                if (selectedSource?.requiresAuthentication == true && !state.isSourceAuthenticated) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ComicSourceAuthCard(
                            isLoading = state.isAuthorizing,
                            error = state.authError,
                            onAuthenticate = onAuthenticate,
                        )
                    }
                } else if (state.isLoading && state.comics.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AuroraLoadingIndicator(contained = true)
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
                    if (state.activeQuery.isBlank()) {
                        val heroComic = state.comics.first()
                        item(key = "hero-comic-billboard", span = { GridItemSpan(maxLineSpan) }) {
                            ComicHeroSpotlight(
                                comic = heroComic,
                                onRead = { onComic(heroComic) },
                            )
                        }
                    }

                    if (state.comics.size >= 5 && state.activeQuery.isBlank()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            AuroraSectionHeader(
                                title = "人气精选 TRENDING",
                                subtitle = "近期读者最爱作品",
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                val topComics = state.comics.take(10)
                                itemsIndexed(topComics, key = { _, c -> "trend-${c.sourceId}:${c.id}" }) { _, comic ->
                                    ComicFeaturedCard(
                                        comic = comic,
                                        onClick = { onComic(comic) },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            AuroraSectionHeader(
                                title = "为你推荐",
                            )
                        }
                    }

                    val feedComics = if (state.activeQuery.isBlank()) state.comics.drop(1) else state.comics
                    items(feedComics, key = { "${it.sourceId}:${it.id}" }) { comic ->
                        ComicCard(
                            comic = comic,
                            onClick = { onComic(comic) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                if (state.isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(72.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AuroraLoadingIndicator(modifier = Modifier.size(30.dp))
                        }
                    }
                }
                (state.loadMoreError ?: state.error)?.let { message ->
                    if (state.comics.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            TextButton(
                                onClick = {
                                    if (state.loadMoreError != null && state.page > 0) {
                                        onLoadMore()
                                    } else {
                                        onRefresh()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("$message（点击重试）")
                            }
                        }
                    }
                }
            }

            // Native Material 3 Expressive Top App Bar & category strip
            val overlayDarkHeader = !isScrolled && !isSearchOpen && state.comics.isNotEmpty() && state.activeQuery.isBlank()
            val headerModifier = if (isScrolled) {
                Modifier.background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f))
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
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                if (isSearchOpen) {
                    AuroraSearchBar(
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
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        testTag = "comic-search-field",
                    )
                } else {
                    AuroraTopBar(
                        titleTestTag = "comic-library-title",
                        modifier = Modifier.padding(bottom = 6.dp),
                        forceDark = overlayDarkHeader,
                    ) {
                        ComicDownloadProgressIcon(
                            downloadedChapters = downloadedChapters,
                            forceDark = overlayDarkHeader,
                            onClick = onOpenDownloadProgress,
                        )
                        AuroraSearchCapsule(
                            label = if (state.activeQuery.isNotBlank()) state.activeQuery else "搜索漫画",
                            onClick = { isSearchOpen = true },
                            forceDark = overlayDarkHeader,
                        )
                    }
                    val categories = selectedSource?.categories.orEmpty()
                    if (categories.isNotEmpty()) {
                        CategoryStrip(
                            testTag = "comic-category-bar",
                            isScrolled = isScrolled,
                            applyBackground = false,
                            forceDark = overlayDarkHeader,
                        ) {
                            categories.forEach { category ->
                                AuroraFilterChip(
                                    selected = category.id == state.selectedCategoryId,
                                    onClick = { onCategory(category.id) },
                                    label = category.label,
                                    forceDark = overlayDarkHeader,
                                )
                            }
                        }
                    }
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
private fun ComicSourceAuthCard(
    isLoading: Boolean,
    error: String?,
    onAuthenticate: (String, String) -> Unit,
) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("授权哔咔漫画", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "账号仅用于换取访问令牌，密码不会保存。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = account,
                onValueChange = { account = it },
                label = { Text("邮箱账号") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                shape = MaterialTheme.shapes.medium,
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
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = { onAuthenticate(account, password) },
                enabled = !isLoading && account.isNotBlank() && password.isNotBlank(),
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                if (isLoading) {
                    AuroraLoadingIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(9.dp))
                }
                Text(if (isLoading) "正在授权" else "登录并连接", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ComicHeroSpotlight(
    comic: ComicSummary,
    onRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(460.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(role = Role.Button) {
                AuroraHaptics.tap(haptic)
                onRead()
            },
    ) {
        val isWideStage = maxWidth > 500.dp
        ComicCoverImage(
            comic = comic,
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
                    .padding(top = 68.dp)
                    .height(236.dp)
                    .aspectRatio(0.72f),
            ) {
                ComicCoverImage(
                    comic = comic,
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
                            0.65f to Color.Black.copy(alpha = 0.68f),
                            0.84f to Color.Black.copy(alpha = 0.90f),
                            1.0f to Color.Black.copy(alpha = 0.95f),
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
            Text(
                text = "MANGA SPOTLIGHT",
                color = AuroraCoral,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                ),
            )

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
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroraMediaLabel,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    AuroraHaptics.confirm(haptic)
                    onRead()
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
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "开始阅读",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
private fun ComicFeaturedCard(
    comic: ComicSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
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
        modifier = modifier.width(142.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(206.dp),
            ) {
                ComicCoverImage(
                    comic = comic,
                    modifier = Modifier.fillMaxSize(),
                )
                if (comic.subtitle.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                    ) {
                        Text(
                            text = comic.subtitle,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = comic.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = comic.subtitle.ifBlank { "精选漫画" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ComicCard(
    comic: ComicSummary,
    onClick: () -> Unit,
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
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f),
            ) {
                ComicCoverImage(comic = comic, modifier = Modifier.fillMaxSize())
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = comic.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = comic.subtitle.ifBlank { " " },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ComicDownloadProgressIcon(
    downloadedChapters: List<DownloadedComicChapter>,
    forceDark: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val activeCount = downloadedChapters.count {
        it.status == ComicDownloadStatus.QUEUED ||
            it.status == ComicDownloadStatus.RESOLVING ||
            it.status == ComicDownloadStatus.DOWNLOADING
    }
    val tint = if (activeCount > 0) {
        MaterialTheme.colorScheme.primary
    } else if (forceDark) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Box(contentAlignment = Alignment.TopEnd) {
        IconButton(
            onClick = {
                AuroraHaptics.tap(haptic)
                onClick()
            },
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("comic-download-progress-button"),
        ) {
            Icon(
                Icons.Default.Download,
                contentDescription = "漫画下载进度",
                tint = tint,
            )
        }
        if (activeCount > 0) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(top = 6.dp, end = 6.dp)
                    .size(16.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = activeCount.coerceAtMost(99).toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

@Composable
private fun ComicDownloadProgressSheetContent(
    chapters: List<DownloadedComicChapter>,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val activeChapters = chapters.filter {
        it.status == ComicDownloadStatus.QUEUED ||
            it.status == ComicDownloadStatus.RESOLVING ||
            it.status == ComicDownloadStatus.DOWNLOADING
    }
    val completedCount = chapters.count { it.status == ComicDownloadStatus.COMPLETED }
    val avgProgress = if (activeChapters.isNotEmpty()) {
        activeChapters.map { it.progress.coerceIn(0f, 1f) }.average().toFloat()
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "漫画下载进度",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "进行中 ${activeChapters.size} 话 · 已完成 $completedCount 话",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (activeChapters.isNotEmpty()) {
                Text(
                    "${(avgProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (activeChapters.isNotEmpty()) {
            AuroraWavyProgressBar(
                progress = { avgProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )
        }
        if (chapters.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
            ) {
                Text(
                    "暂无漫画下载任务，可在漫画详情或阅读器中点击下载按钮。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                items(chapters.size, key = { chapters[it].id }) { idx ->
                    val record = chapters[idx]
                    val maxPages = maxOf(record.totalPages, record.downloadedPages)
                    val pct = (record.progress.coerceIn(0f, 1f) * 100).toInt()
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    record.comicTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    record.chapterTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                val statusLine = when (record.status) {
                                    ComicDownloadStatus.QUEUED -> "排队等待 · $maxPages 页"
                                    ComicDownloadStatus.RESOLVING -> "正在解析章节…"
                                    ComicDownloadStatus.DOWNLOADING -> "下载中 ${record.downloadedPages}/$maxPages 页 ($pct%)"
                                    ComicDownloadStatus.COMPLETED -> "已完成 · $maxPages 页"
                                    ComicDownloadStatus.PAUSED -> "已暂停 · ${record.downloadedPages}/$maxPages 页"
                                    ComicDownloadStatus.FAILED -> record.errorMessage?.takeIf { it.isNotBlank() }?.let { "失败: $it" } ?: "下载失败"
                                }
                                Text(
                                    statusLine,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (record.status == ComicDownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (record.status == ComicDownloadStatus.DOWNLOADING ||
                                    record.status == ComicDownloadStatus.RESOLVING ||
                                    record.status == ComicDownloadStatus.QUEUED
                                ) {
                                    AuroraWavyProgressBar(
                                        progress = { record.progress.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                    )
                                }
                            }
                            if (record.status == ComicDownloadStatus.FAILED) {
                                IconButton(onClick = { onRetry(record.id) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "重试下载")
                                }
                            }
                            IconButton(onClick = { onRemove(record.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除任务")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComicDetailsScreen(
    details: ComicDetails,
    onBack: () -> Unit,
    onChapter: (Int) -> Unit,
    onDownloadChapter: ((ComicChapter) -> Unit)? = null,
    onDownloadAll: (() -> Unit)? = null,
    downloadedChapters: List<DownloadedComicChapter> = emptyList(),
    onOpenDownloadProgress: () -> Unit = {},
    onRetryComicDownload: ((String) -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val semanticColors = LocalAuroraSemanticColors.current
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 126.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            stickyHeader {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        IconButton(
                            onClick = {
                                AuroraHaptics.tap(haptic)
                                onBack()
                            },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回漫画列表", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(
                            text = "漫画详情",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() },
                        )
                        Spacer(Modifier.weight(1f))
                        ComicDownloadProgressIcon(
                            downloadedChapters = downloadedChapters,
                            forceDark = false,
                            onClick = onOpenDownloadProgress,
                        )
                    }
                }
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .width(120.dp)
                                .aspectRatio(0.72f),
                        ) {
                            ComicCoverImage(comic = details.comic, modifier = Modifier.fillMaxSize())
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            Text(details.comic.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold))
                            if (details.comic.subtitle.isNotBlank()) {
                                Text(details.comic.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                details.comic.tags.take(6).forEach { tag ->
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                        Text(tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                }
                            }
                            if (details.chapters.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = {
                                            AuroraHaptics.confirm(haptic)
                                            onChapter(0)
                                        },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = if (onDownloadAll != null) Modifier.weight(1f).heightIn(min = 48.dp) else Modifier.heightIn(min = 48.dp),
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("开始阅读", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                    }

                                    if (onDownloadAll != null) {
                                        OutlinedButton(
                                            onClick = {
                                                AuroraHaptics.tap(haptic)
                                                onDownloadAll()
                                            },
                                            shape = CircleShape,
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp),
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "下载全本",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (details.description.isNotBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                            Text("简介", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(details.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Text(
                    "章节 (${details.chapters.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 4.dp)
                        .semantics { heading() },
                )
            }
            itemsIndexed(details.chapters, key = { _, ch -> ch.id }) { index, chapter ->
                val downloadRecord = downloadedChapters.find { it.comicId == details.comic.id && it.chapterId == chapter.id }
                Card(
                    onClick = {
                        AuroraHaptics.tap(haptic)
                        onChapter(index)
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .animateItem(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(
                            chapter.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )

                        if (downloadRecord?.status == ComicDownloadStatus.COMPLETED) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "已离线",
                                tint = semanticColors.onDirectContainer,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(20.dp),
                            )
                        } else if (downloadRecord?.status == ComicDownloadStatus.DOWNLOADING ||
                            downloadRecord?.status == ComicDownloadStatus.RESOLVING ||
                            downloadRecord?.status == ComicDownloadStatus.QUEUED
                        ) {
                            val pct = (downloadRecord.progress.coerceIn(0f, 1f) * 100).toInt()
                            Text(
                                text = "${downloadRecord.downloadedPages}/${maxOf(downloadRecord.totalPages, downloadRecord.downloadedPages)}页 · $pct%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                            CircularProgressIndicator(
                                progress = { downloadRecord.progress },
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else if (downloadRecord?.status == ComicDownloadStatus.FAILED) {
                            IconButton(
                                onClick = {
                                    AuroraHaptics.tap(haptic)
                                    if (onRetryComicDownload != null) {
                                        onRetryComicDownload(downloadRecord.id)
                                    } else {
                                        onDownloadChapter?.invoke(chapter)
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(32.dp)
                                    .padding(end = 6.dp),
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "重试下载章节",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else if (onDownloadChapter != null) {
                            IconButton(
                                onClick = {
                                    AuroraHaptics.tap(haptic)
                                    onDownloadChapter(chapter)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(32.dp)
                                    .padding(end = 6.dp),
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "离线下载此章节",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onDownloadChapter: ((ComicChapter) -> Unit)? = null,
    downloadedChapters: List<DownloadedComicChapter> = emptyList(),
) {
    val reader = requireNotNull(state.reader)
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val semanticColors = LocalAuroraSemanticColors.current
    val targetPageWidthPx = LocalWindowInfo.current.containerSize.width.coerceIn(720, JmUnscrambleTransformation.MAX_TEXTURE_WIDTH)
    val listState = rememberLazyListState()

    LaunchedEffect(reader.chapterIndex) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(listState, reader.pages, targetPageWidthPx) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { currentIndex ->
                val ahead = (currentIndex + 1..currentIndex + 3).filter { it in reader.pages.indices }
                withContext(Dispatchers.IO) {
                    ahead.forEach { idx ->
                        val p = reader.pages[idx]
                        if (p.imageUrl.startsWith("file:/")) return@forEach
                        runCatching {
                            val resolved = ComicPageResolver.resolve(p)
                            val req = ImageRequest.Builder(context)
                                .data(resolved.imageUrl)
                                .httpHeaders(
                                    NetworkHeaders.Builder()
                                        .set("Referer", resolved.referer)
                                        .set("User-Agent", "Mozilla/5.0 (Linux; Android 17) AppleWebKit/537.36 Chrome/142 Mobile Safari/537.36")
                                        .build(),
                                )
                                .memoryCacheKey("${resolved.imageUrl}#segments=${resolved.verticalSegments}")
                                .diskCacheKey(resolved.imageUrl)
                                .apply {
                                    if (resolved.verticalSegments > 1) {
                                        size(Size.ORIGINAL)
                                        transformations(JmUnscrambleTransformation(resolved.verticalSegments))
                                    } else {
                                        size(Size(targetPageWidthPx, JmUnscrambleTransformation.MAX_TEXTURE_HEIGHT))
                                        precision(Precision.INEXACT)
                                    }
                                }
                                .build()
                            context.imageLoader.enqueue(req)
                        }
                    }
                }
            }
    }

    val currentRecord = downloadedChapters.find { it.comicId == reader.details.comic.id && it.chapterId == reader.chapter.id }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuroraImmersiveBackground),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 72.dp, bottom = 124.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.isReaderLoading) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 140.dp),
                    ) {
                        AuroraLoadingIndicator(color = Color.White)
                        Text("正在加载章节…", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            onClick = onBack,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        ) {
                            Text("取消并返回")
                        }
                    }
                }
            } else if (state.readerError != null) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 120.dp),
                    ) {
                        Icon(Icons.Default.BrokenImage, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
                        Text(
                            state.readerError,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onRetry, shape = CircleShape) { Text("重试") }
                            OutlinedButton(
                                onClick = onBack,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            ) {
                                Text("返回漫画列表")
                            }
                        }
                    }
                }
            } else if (reader.pages.isEmpty()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 120.dp),
                    ) {
                        Icon(Icons.Default.BrokenImage, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
                        Text("此章节暂时没有可显示的图片。", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onRetry, shape = CircleShape) { Text("重新加载") }
                            OutlinedButton(
                                onClick = onBack,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            ) {
                                Text("返回漫画列表")
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(reader.pages, key = { index, page -> "$index:${page.imageUrl}" }) { index, page ->
                    ComicPageImage(page = page, pageNumber = index + 1)
                }
            }
        }

        // Top reader chrome
        Surface(
            color = Color.Black.copy(alpha = 0.84f),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
            ) {
                IconButton(
                    onClick = {
                        AuroraHaptics.tap(haptic)
                        onBack()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
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

                if (currentRecord?.status == ComicDownloadStatus.COMPLETED) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "已离线缓存",
                        tint = semanticColors.onDirectContainer,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(22.dp),
                    )
                } else if (currentRecord?.status == ComicDownloadStatus.DOWNLOADING ||
                    currentRecord?.status == ComicDownloadStatus.RESOLVING
                ) {
                    CircularProgressIndicator(
                        progress = { currentRecord.progress },
                        color = Color.White,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else if (onDownloadChapter != null) {
                    IconButton(
                        onClick = {
                            AuroraHaptics.tap(haptic)
                            onDownloadChapter(reader.chapter)
                        },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "离线缓存本话", tint = Color.White)
                    }
                }
            }
        }

        // Native Material 3 Expressive Floating Toolbar for chapter navigation
        if (!state.isReaderLoading && state.readerError == null && reader.pages.isNotEmpty()) {
            HorizontalFloatingToolbar(
                expanded = true,
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                IconButton(
                    onClick = {
                        AuroraHaptics.segmentTick(haptic)
                        onPreviousChapter()
                    },
                    enabled = reader.chapterIndex > 0,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一章")
                }
                Text(
                    text = "${reader.chapterIndex + 1} / ${reader.details.chapters.size}",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(horizontal = 10.dp),
                )
                IconButton(
                    onClick = {
                        AuroraHaptics.segmentTick(haptic)
                        onNextChapter()
                    },
                    enabled = reader.chapterIndex < reader.details.chapters.lastIndex,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一章")
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
    val targetPageWidthPx = LocalWindowInfo.current.containerSize.width.coerceIn(720, JmUnscrambleTransformation.MAX_TEXTURE_WIDTH)
    val isLocalFile = page.imageUrl.startsWith("file:/")
    val resolvedResult by produceState<Result<ComicPage>?>(
        initialValue = if (isLocalFile || page.resolutionUrl == null) Result.success(page) else null,
        key1 = page,
        key2 = retryKey,
    ) {
        if (!isLocalFile && page.resolutionUrl != null) {
            value = runCatching { ComicPageResolver.resolve(page) }
        }
    }
    val resolvedPage = resolvedResult?.getOrNull()
    val request = remember(resolvedPage, retryKey, context, targetPageWidthPx) {
        resolvedPage?.let { resolved ->
            if (isLocalFile) {
                val file = java.io.File(java.net.URI.create(resolved.imageUrl).path)
                ImageRequest.Builder(context)
                    .data(file)
                    .memoryCacheKey(resolved.imageUrl)
                    .size(Size(targetPageWidthPx, JmUnscrambleTransformation.MAX_TEXTURE_HEIGHT))
                    .precision(Precision.INEXACT)
                    .build()
            } else {
                ImageRequest.Builder(context)
                    .data(resolved.imageUrl)
                    .httpHeaders(
                        NetworkHeaders.Builder()
                            .set("Referer", resolved.referer)
                            .set("User-Agent", "Mozilla/5.0 (Linux; Android 17) AppleWebKit/537.36 Chrome/142 Mobile Safari/537.36")
                            .build(),
                    )
                    .memoryCacheKey("${resolved.imageUrl}#segments=${resolved.verticalSegments}")
                    .diskCacheKey(resolved.imageUrl)
                    .apply {
                        if (retryKey > 0) {
                            memoryCachePolicy(CachePolicy.WRITE_ONLY)
                            diskCachePolicy(CachePolicy.WRITE_ONLY)
                        }
                        if (resolved.verticalSegments > 1) {
                            size(Size.ORIGINAL)
                            transformations(JmUnscrambleTransformation(resolved.verticalSegments))
                        } else {
                            size(Size(targetPageWidthPx, JmUnscrambleTransformation.MAX_TEXTURE_HEIGHT))
                            precision(Precision.INEXACT)
                        }
                    }
                    .build()
            }
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(AuroraReaderBackground),
    ) {
        request?.let {
            AsyncImage(
                model = it,
                contentDescription = "第 $pageNumber 页",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
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
                AuroraLoadingIndicator(color = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(28.dp))
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
private fun ComicCoverImage(
    comic: ComicSummary,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
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
                contentScale = contentScale,
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
