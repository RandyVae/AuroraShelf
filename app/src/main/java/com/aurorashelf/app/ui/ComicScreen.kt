package com.aurorashelf.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.transformations
import com.aurorashelf.app.data.VideoRepository
import com.aurorashelf.app.data.comic.ComicPageResolver
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
    onAuthenticate: (String, String) -> Unit,
    onSignOut: () -> Unit,
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
                onAuthenticate = onAuthenticate,
                onSignOut = onSignOut,
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

@OptIn(ExperimentalMaterial3Api::class)
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
    onAuthenticate: (String, String) -> Unit,
    onSignOut: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    var showSourcePicker by remember { mutableStateOf(false) }
    val selectedSource = state.sources.find { it.id == state.selectedSourceId }
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
                if (selectedSource?.requiresAuthentication == true && state.isSourceAuthenticated) {
                    TextButton(onClick = onSignOut) { Text("退出") }
                }
                IconButton(onClick = onRefresh, enabled = state.isSourceAuthenticated) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新漫画")
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(
                onClick = { showSourcePicker = true },
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().testTag("comic-source-selector"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        Text("漫画源", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            selectedSource?.name.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (selectedSource?.requiresAuthentication == true) {
                        Text(
                            if (state.isSourceAuthenticated) "已登录" else "需登录",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "选择漫画源")
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
        item(span = { GridItemSpan(maxLineSpan) }) {
            val categories = selectedSource?.categories.orEmpty()
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

    if (showSourcePicker) {
        ModalBottomSheet(
            onDismissRequest = { showSourcePicker = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            ComicSourcePicker(
                sources = state.sources,
                selectedSourceId = state.selectedSourceId,
                onSource = { sourceId ->
                    showSourcePicker = false
                    onSource(sourceId)
                },
            )
        }
    }
}

@Composable
private fun ComicSourcePicker(
    sources: List<com.aurorashelf.app.model.ComicSourceInfo>,
    selectedSourceId: String,
    onSource: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            Text("选择漫画源", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "每个来源拥有独立分类、搜索和阅读线路",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        sources.forEach { source ->
            val selected = source.id == selectedSourceId
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSource(source.id)
                },
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (source.requiresAuthentication) {
                                Text("需登录", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text(
                            source.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (selected) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "当前漫画源")
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
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("授权哔咔漫画", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
                shape = RoundedCornerShape(16.dp),
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
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = { onAuthenticate(account, password) },
                enabled = !isLoading && account.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(9.dp))
                }
                Text(if (isLoading) "正在授权" else "登录并连接")
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
            ComicCoverImage(comic = comic, modifier = Modifier.fillMaxSize())
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
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 126.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            stickyHeader {
                Surface(color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f)) {
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
            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.width(126.dp).aspectRatio(0.74f),
                        ) {
                            ComicCoverImage(comic = details.comic, modifier = Modifier.fillMaxSize())
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                            Text(details.comic.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            if (details.comic.subtitle.isNotBlank()) {
                                Text(details.comic.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                details.comic.tags.take(6).forEach { tag ->
                                    Surface(shape = RoundedCornerShape(9.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                        Text(tag, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                                    }
                                }
                            }
                            if (details.chapters.isNotEmpty()) {
                                Button(onClick = { onChapter(0) }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("开始阅读")
                                }
                            }
                        }
                    }
                }
            }
            if (details.description.isNotBlank()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(18.dp)) {
                            Text("简介", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(details.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Text(
                    "章节 · ${details.chapters.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 22.dp, end = 20.dp, top = 18.dp, bottom = 4.dp),
                )
            }
            if (details.chapters.isEmpty()) {
                item { ComicEmpty("该漫画暂时没有可读章节") }
            } else {
                itemsIndexed(details.chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                    Surface(
                        onClick = { onChapter(index) },
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(chapter.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
        modifier = Modifier.fillMaxWidth().background(Color(0xFF111111)),
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
    val request = remember(comic.coverUrl, comic.coverReferer, comic.sourceId, context) {
        comic.coverUrl?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .memoryCacheKey(url)
                .diskCacheKey(url)
                .apply {
                    comic.coverReferer?.let { referer ->
                        httpHeaders(
                            NetworkHeaders.Builder()
                                .set("Referer", referer)
                                .set("User-Agent", VideoRepository.USER_AGENT)
                                .apply { if (comic.sourceId == "ehentai") set("Cookie", "nw=1") }
                                .build(),
                        )
                    }
                }
                .build()
        }
    }
    if (request == null) {
        ComicCoverPlaceholder()
    } else {
        AsyncImage(
            model = request,
            contentDescription = "${comic.title}封面",
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
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
