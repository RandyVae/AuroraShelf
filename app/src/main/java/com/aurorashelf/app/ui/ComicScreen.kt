package com.aurorashelf.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.aurorashelf.app.model.ComicDetails
import com.aurorashelf.app.model.ComicPage
import com.aurorashelf.app.model.ComicSummary

@Composable
internal fun ComicScreen(
    state: ComicUiState,
    onSource: (String) -> Unit,
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
) {
    BackHandler(enabled = state.reader != null || state.details != null) {
        if (state.reader != null) onCloseReader() else onCloseDetails()
    }

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
                onSource = onSource,
                onCategory = onCategory,
                onQuery = onQuery,
                onSearch = onSearch,
                onRefresh = onRefresh,
                onLoadMore = onLoadMore,
                onComic = onComic,
            )
        }
    }

    if (state.isDetailsLoading) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.2f)),
        ) {
            Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp) {
                CircularProgressIndicator(modifier = Modifier.padding(24.dp).size(30.dp), strokeWidth = 3.dp)
            }
        }
    }
}

@Composable
private fun ComicLibrary(
    state: ComicUiState,
    onSource: (String) -> Unit,
    onCategory: (String) -> Unit,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onComic: (ComicSummary) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 154.dp),
        state = gridState,
        contentPadding = PaddingValues(start = 18.dp, top = 20.dp, end = 18.dp, bottom = 126.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "漫画",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("comic-library-title"),
                    )
                    Text(
                        state.sources.find { it.id == state.selectedSourceId }?.description.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新漫画")
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                state.sources.forEach { source ->
                    FilterChip(
                        selected = source.id == state.selectedSourceId,
                        onClick = { onSource(source.id) },
                        label = { Text(source.name) },
                    )
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            val categories = state.sources.find { it.id == state.selectedSourceId }?.categories.orEmpty()
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                categories.forEach { category ->
                    FilterChip(
                        selected = category.id == state.selectedCategoryId,
                        onClick = { onCategory(category.id) },
                        label = { Text(category.label) },
                    )
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                singleLine = true,
                label = { Text("搜索漫画") },
                trailingIcon = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.isLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
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
            items(state.comics, key = { "${it.sourceId}:${it.id}" }) { comic ->
                ComicCard(comic = comic, onClick = { onComic(comic) })
            }
            if (state.isLoadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
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

@Composable
private fun ComicCard(comic: ComicSummary, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(
        verticalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(bottom = 6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.74f),
        ) {
            if (comic.coverUrl != null) {
                AsyncImage(
                    model = comic.coverUrl,
                    contentDescription = "${comic.title}封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ComicCoverPlaceholder()
            }
        }
        Text(
            comic.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        if (comic.subtitle.isNotBlank()) {
            Text(
                comic.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    LazyColumn(
        contentPadding = PaddingValues(bottom = 126.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回漫画列表")
                }
                Text("漫画详情", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.width(132.dp).aspectRatio(0.74f),
                ) {
                    details.comic.coverUrl?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = "${details.comic.title}封面",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } ?: ComicCoverPlaceholder()
                }
                Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.weight(1f)) {
                    Text(details.comic.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (details.comic.subtitle.isNotBlank()) {
                        Text(details.comic.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        details.comic.tags.take(6).forEach { tag ->
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(tag, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                            }
                        }
                    }
                    if (details.chapters.isNotEmpty()) {
                        Button(onClick = { onChapter(0) }, contentPadding = PaddingValues(horizontal = 18.dp)) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("开始阅读")
                        }
                    }
                }
            }
        }
        if (details.description.isNotBlank()) {
            item {
                Text(
                    details.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
        item {
            Text(
                "章节 · ${details.chapters.size}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
            )
        }
        if (details.chapters.isEmpty()) {
            item { ComicEmpty("该漫画暂时没有可读章节") }
        } else {
            itemsIndexed(details.chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onChapter(index) }
                        .padding(horizontal = 20.dp, vertical = 15.dp),
                ) {
                    Text(chapter.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
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
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        LazyColumn(
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
                    Text(reader.details.comic.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
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
    val request = remember(page, retryKey, context) {
        ImageRequest.Builder(context)
            .data(page.imageUrl)
            .httpHeaders(
                NetworkHeaders.Builder()
                    .set("Referer", page.referer)
                    .set("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
                    .build(),
            )
            .memoryCacheKey(page.imageUrl)
            .diskCacheKey(page.imageUrl)
            .build()
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().background(Color(0xFF111111)),
    ) {
        AsyncImage(
            model = request,
            contentDescription = "第 $pageNumber 页",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            onLoading = { loadState = 0 },
            onSuccess = { loadState = 1 },
            onError = { loadState = 2 },
        )
        when (loadState) {
            0 -> Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
            }
            2 -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
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
