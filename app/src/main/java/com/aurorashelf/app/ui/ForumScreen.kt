@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.aurorashelf.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.ScrollableDefaults
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.aurorashelf.app.data.VideoRepository
import com.aurorashelf.app.data.forum.ForumContentBlock
import com.aurorashelf.app.data.forum.ForumFloor
import com.aurorashelf.app.data.forum.ForumHttp
import com.aurorashelf.app.data.forum.ForumImageRequest
import com.aurorashelf.app.data.forum.ForumImageSaver
import com.aurorashelf.app.data.forum.ForumPageLink
import com.aurorashelf.app.data.forum.ForumPostCache
import com.aurorashelf.app.data.forum.ForumThreadDetail
import com.aurorashelf.app.data.forum.ForumThreadParser
import com.aurorashelf.app.data.forum.deduplicateForumImageUrls
import com.aurorashelf.app.data.forum.shouldForwardCookies
import com.aurorashelf.app.model.ForumPost
import com.aurorashelf.app.ui.theme.AuroraHaptics
import com.aurorashelf.app.ui.theme.AuroraMotion
import com.aurorashelf.app.ui.theme.LocalAuroraSemanticColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

@Composable
internal fun ForumScreen(
    state: ForumUiState,
    bottomPadding: Dp,
    onEnsureLoaded: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onPost: (ForumPost) -> Unit,
    onClosePost: () -> Unit,
    onSelectSource: (String?) -> Unit = {},
    onSearch: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onCloseSearch: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    // Keep the list state in composition while the reader is open.
    val listState = rememberLazyListState()
    LaunchedEffect(state.posts.size) {
        if (state.posts.isNotEmpty()) {
            delay(400)
            ForumPostCache.prefetchAll(state.posts.map { it.url }, limit = POST_PREFETCH_LIMIT, concurrency = 2)
        }
    }

    val visiblePosts = remember(state.posts, state.selectedSourceId, state.searchQuery) {
        filterForumPosts(
            posts = state.posts,
            selectedSourceId = state.selectedSourceId,
            searchQuery = state.searchQuery,
        )
    }

    LaunchedEffect(Unit) { onEnsureLoaded() }
    LaunchedEffect(listState, state.canLoadMore, state.isLoading, state.isLoadingMore, state.loadMoreError) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 3 &&
                !state.isLoading && !state.isLoadingMore &&
                state.canLoadMore && state.loadMoreError == null
        }.distinctUntilChanged().filter { it }.collect { onLoadMore() }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isTwoPane = maxWidth >= 840.dp
            val selectedPost = state.selectedPost
            if (selectedPost != null && !isTwoPane) {
                ForumReader(post = selectedPost, onClose = onClosePost)
            } else if (isTwoPane && selectedPost != null) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(0.42f).fillMaxHeight()) {
                        ForumFeedPane(
                            state = state,
                            visiblePosts = visiblePosts,
                            listState = listState,
                            bottomPadding = bottomPadding,
                            onRefresh = onRefresh,
                            onLoadMore = onLoadMore,
                            onPost = onPost,
                            onSelectSource = onSelectSource,
                            onSearch = onSearch,
                            onSearchQueryChange = onSearchQueryChange,
                            onCloseSearch = onCloseSearch,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    Box(modifier = Modifier.weight(0.58f).fillMaxHeight()) {
                        ForumReader(post = selectedPost, onClose = onClosePost)
                    }
                }
            } else {
                ForumFeedPane(
                    state = state,
                    visiblePosts = visiblePosts,
                    listState = listState,
                    bottomPadding = bottomPadding,
                    onRefresh = onRefresh,
                    onLoadMore = onLoadMore,
                    onPost = onPost,
                    onSelectSource = onSelectSource,
                    onSearch = onSearch,
                    onSearchQueryChange = onSearchQueryChange,
                    onCloseSearch = onCloseSearch,
                )
            }
        }
    }
}

@Composable
private fun ForumFeedPane(
    state: ForumUiState,
    visiblePosts: List<ForumPost>,
    listState: LazyListState,
    bottomPadding: Dp,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onPost: (ForumPost) -> Unit,
    onSelectSource: (String?) -> Unit,
    onSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
) {
    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 40
        }
    }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val headerSpacerHeight = remember(headerHeightPx, density) {
        if (headerHeightPx > 0) with(density) { headerHeightPx.toDp() + 8.dp } else 115.dp
    }

    PredictiveBackContainer(
        enabled = state.isSearchOpen,
        onBack = onCloseSearch,
    ) {
        AuroraPullToRefreshBox(
            isRefreshing = state.isLoading && state.posts.isNotEmpty(),
            onRefresh = onRefresh,
            indicatorPaddingTop = headerSpacerHeight,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                flingBehavior = ScrollableDefaults.flingBehavior(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = bottomPadding + 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("forum-feed"),
            ) {
                item(key = "forum-header-spacer") {
                    Spacer(Modifier.height(headerSpacerHeight))
                }

                if (state.unavailableSources.isNotEmpty()) {
                    item(key = "forum-unavailable") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "${state.unavailableSources.joinToString("、")}暂时不可用，已继续显示其他内容",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                }

                if (state.error != null && state.posts.isNotEmpty()) {
                    item(key = "forum-refresh-error") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("forum-refresh-error-banner"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "刷新失败: ${state.error}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = onRefresh,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error,
                                    ),
                                ) {
                                    Text("重试")
                                }
                            }
                        }
                    }
                }

                if (state.isLoading && state.posts.isEmpty()) {
                    item(key = "forum-loading") {
                        Box(
                            Modifier
                                .fillParentMaxSize()
                                .padding(top = 120.dp),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            AuroraLoadingIndicator(
                                contained = true,
                                modifier = Modifier.testTag("forum-loading"),
                            )
                        }
                    }
                } else if (state.error != null && state.posts.isEmpty()) {
                    item(key = "forum-error") {
                        ForumError(message = state.error, onRetry = onRefresh)
                    }
                } else if (visiblePosts.isEmpty() && state.posts.isNotEmpty()) {
                    item(key = "forum-empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (state.searchQuery.isNotBlank()) {
                                    "未找到与「${state.searchQuery}」匹配的帖子"
                                } else {
                                    "该板块暂未抓取到更多帖子，请下拉刷新或浏览【全部】"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(visiblePosts, key = { "${it.sourceId}:${it.id}" }, contentType = { "forum_post" }) { post ->
                        ForumPostCard(
                            post = post,
                            isSelected = state.selectedPost?.url == post.url,
                            onClick = { onPost(post) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                if (state.isLoadingMore) {
                    item(key = "forum-loading-more") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AuroraLoadingIndicator(modifier = Modifier.size(30.dp))
                        }
                    }
                } else if (state.loadMoreError != null && state.posts.isNotEmpty()) {
                    item(key = "forum-load-more-error") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = state.loadMoreError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = onLoadMore) {
                                Text("加载失败，点击重试")
                            }
                        }
                    }
                }
            }

            val headerModifier = if (isScrolled) {
                Modifier.background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f))
            } else {
                Modifier.background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
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
                    .onSizeChanged { size ->
                        if (size.height > 0) headerHeightPx = size.height
                    }
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                if (state.isSearchOpen) {
                    AuroraSearchBar(
                        query = state.searchQuery,
                        onQueryChange = onSearchQueryChange,
                        placeholder = "搜索社区帖子、作者或板块",
                        onSearch = {},
                        onBack = onCloseSearch,
                        onClear = { onSearchQueryChange("") },
                        isSearching = state.isLoading,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        testTag = "forum-search-field",
                    )
                } else {
                    AuroraTopBar {
                        AuroraSearchCapsule(
                            label = if (state.searchQuery.isNotBlank()) state.searchQuery else "搜索社区",
                            onClick = onSearch,
                        )
                    }
                    CategoryStrip(
                        testTag = "forum-category-bar",
                        isScrolled = isScrolled,
                        applyBackground = false,
                    ) {
                        AuroraFilterChip(
                            selected = state.selectedSourceId == null,
                            onClick = { onSelectSource(null) },
                            label = "全部",
                        )
                        AuroraFilterChip(
                            selected = state.selectedSourceId == "t66y",
                            onClick = { onSelectSource("t66y") },
                            label = "草榴社区",
                        )
                        AuroraFilterChip(
                            selected = state.selectedSourceId == "t66y_16",
                            onClick = { onSelectSource("t66y_16") },
                            label = "达盖尔的旗帜",
                        )
                        AuroraFilterChip(
                            selected = state.selectedSourceId == "t906",
                            onClick = { onSelectSource("t906") },
                            label = "91自拍论坛",
                        )
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
private fun ForumPostCard(
    post: ForumPost,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val semanticColors = LocalAuroraSemanticColors.current
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = {
            AuroraHaptics.tap(haptic)
            onClick()
        },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) scheme.secondaryContainer else scheme.surfaceContainerLow,
            contentColor = if (isSelected) scheme.onSecondaryContainer else scheme.onSurface,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .testTag("forum-post"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 20.sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val badgeColor = when (post.sourceId) {
                    "t906" -> semanticColors.forumPrimaryBadge
                    "t66y" -> semanticColors.forumSecondaryBadge
                    "t66y_16" -> semanticColors.forumTertiaryBadge
                    "sis" -> scheme.primary
                    else -> semanticColors.forumFallbackBadge
                }
                Surface(
                    color = badgeColor,
                    contentColor = semanticColors.onForumBadge,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = post.sourceName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = post.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(
                    text = post.replyCount.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                if (post.updatedLabel.isNotBlank()) {
                    Text(
                        text = post.updatedLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ForumError(message: String, onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(28.dp),
        ) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry) { Text("重新加载") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ForumReader(post: ForumPost, onClose: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val imageSaver = remember(context) { ForumImageSaver(context) }
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() <= 0.5f

    var isWebMode by remember(post.url) { mutableStateOf(false) }
    var activeThreadUrl by remember(post.url) { mutableStateOf(post.url) }
    var threadUrlStack by remember(post.url) { mutableStateOf<List<String>>(emptyList()) }
    var refreshNonce by remember(post.url) { mutableIntStateOf(0) }

    var nativeDetail by remember(post.url) { mutableStateOf<ForumThreadDetail?>(null) }
    var isNativeLoading by remember(post.url) { mutableStateOf(true) }
    var nativeError by remember(post.url) { mutableStateOf<String?>(null) }
    val nativeListState = rememberLazyListState()

    var webDetectedImages by remember(post.url) { mutableStateOf<List<String>>(emptyList()) }
    var webCanGoBack by remember(post.url) { mutableStateOf(false) }
    var webGoBackTrigger by remember(post.url) { mutableIntStateOf(0) }
    var readerUserAgent by remember(post.url) { mutableStateOf(VideoRepository.USER_AGENT) }

    var pendingImageRequest by remember(post.url) { mutableStateOf<ForumImageRequest?>(null) }
    var permissionImageRequest by remember(post.url) { mutableStateOf<ForumImageRequest?>(null) }
    var permissionBatchRequests by remember(post.url) { mutableStateOf<List<ForumImageRequest>?>(null) }
    var isSavingImage by remember(post.url) { mutableStateOf(false) }
    var batchSaveProgress by remember(post.url) { mutableStateOf<Pair<Int, Int>?>(null) }

    val effectiveDetectedImages = if (isWebMode) {
        webDetectedImages
    } else {
        nativeDetail?.allImageUrls.orEmpty()
    }

    val buildImageRequest: (String, String) -> ForumImageRequest = { imageUrl, currentPageUrl ->
        ForumImageRequest(
            imageUrl = imageUrl,
            pageUrl = currentPageUrl,
            userAgent = readerUserAgent,
            cookies = if (shouldForwardCookies(currentPageUrl, imageUrl)) {
                runCatching { CookieManager.getInstance().getCookie(currentPageUrl) }.getOrNull()
            } else {
                null
            },
        )
    }

    val saveImage: (ForumImageRequest) -> Unit = { request ->
        if (!isSavingImage) {
            isSavingImage = true
            coroutineScope.launch {
                val result = imageSaver.save(request)
                isSavingImage = false
                snackbarHostState.showSnackbar(
                    result.fold(
                        onSuccess = { "图片已保存到系统相册 Pictures/AuroraShelf" },
                        onFailure = { failure -> failure.message ?: "图片保存失败，请稍后重试" },
                    ),
                )
            }
        }
    }

    val saveAllImages: (List<ForumImageRequest>) -> Unit = { requests ->
        if (!isSavingImage && requests.isNotEmpty()) {
            isSavingImage = true
            batchSaveProgress = 0 to requests.size
            coroutineScope.launch {
                val result = imageSaver.saveBatch(requests) { completed, total, _ ->
                    coroutineScope.launch(Dispatchers.Main) {
                        batchSaveProgress = completed to total
                    }
                }
                isSavingImage = false
                batchSaveProgress = null
                val summary = if (result.succeeded > 0) {
                    "已保存 ${result.succeeded}/${result.total} 张论坛图片到 Pictures/AuroraShelf"
                } else {
                    "图片批量下载失败，请检查网络连接"
                }
                snackbarHostState.showSnackbar(summary)
            }
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        val singleRequest = permissionImageRequest
        val batchRequests = permissionBatchRequests
        permissionImageRequest = null
        permissionBatchRequests = null
        if (isGranted) {
            when {
                batchRequests != null -> saveAllImages(batchRequests)
                singleRequest != null -> saveImage(singleRequest)
            }
        } else {
            coroutineScope.launch { snackbarHostState.showSnackbar("没有存储权限，无法保存图片") }
        }
    }

    val triggerSaveWithPermission: (ForumImageRequest) -> Unit = { req ->
        val needsLegacyPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsLegacyPermission) {
            permissionImageRequest = req
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveImage(req)
        }
    }

    val triggerBatchSaveWithPermission: (List<ForumImageRequest>) -> Unit = { reqs ->
        val needsLegacyPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsLegacyPermission) {
            permissionBatchRequests = reqs
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveAllImages(reqs)
        }
    }

    // Native Jsoup thread loader (instant <5ms when ForumPostCache hits, shared HTTP/2 pool otherwise)
    LaunchedEffect(activeThreadUrl, refreshNonce, isWebMode) {
        if (isWebMode) return@LaunchedEffect
        val targetUrl = activeThreadUrl
        if (!targetUrl.startsWith("http://", ignoreCase = true) && !targetUrl.startsWith("https://", ignoreCase = true)) {
            isNativeLoading = false
            nativeError = "帖子链接无效"
            return@LaunchedEffect
        }
        isNativeLoading = true
        nativeError = null
        val started = System.currentTimeMillis()
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                val cached = ForumPostCache.get(targetUrl)
                if (cached != null) {
                    ForumThreadParser.parse(cached, targetUrl, post)
                } else {
                    val doc = ForumHttp.fetchDocument(targetUrl, mobileLayout = true)
                    ForumPostCache.put(targetUrl, doc.outerHtml())
                    ForumThreadParser.parse(doc, post)
                }
            }
        }
        outcome.fold(
            onSuccess = { parsed ->
                Log.i(TAG, "nativeParsed ms=${System.currentTimeMillis() - started} floors=${parsed.floors.size} imgs=${parsed.allImageUrls.size}")
                if (parsed.floors.isEmpty()) {
                    // Automatic graceful fallback to cleaned WebView if thread relies on dynamic JS DOM
                    isWebMode = true
                    isNativeLoading = false
                } else {
                    nativeDetail = parsed
                    isNativeLoading = false
                    nativeError = null
                    nativeListState.scrollToItem(0)
                }
            },
            onFailure = { err ->
                if (err is kotlinx.coroutines.CancellationException) throw err
                isNativeLoading = false
                nativeError = err.message ?: "帖子加载失败，请检查网络后重试"
            },
        )
    }

    val navigateToThreadPage: (String) -> Unit = { nextUrl ->
        if (nextUrl.isNotBlank() && nextUrl != activeThreadUrl) {
            threadUrlStack = threadUrlStack + activeThreadUrl
            activeThreadUrl = nextUrl
        }
    }

    val hasInternalBack = if (isWebMode) webCanGoBack else threadUrlStack.isNotEmpty()
    BackHandler(enabled = hasInternalBack) {
        if (isWebMode) {
            webGoBackTrigger += 1
        } else if (threadUrlStack.isNotEmpty()) {
            val prev = threadUrlStack.last()
            threadUrlStack = threadUrlStack.dropLast(1)
            activeThreadUrl = prev
        }
    }

    PredictiveBackContainer(
        enabled = !hasInternalBack,
        onBack = onClose,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("forum-reader"),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .padding(horizontal = 6.dp),
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回论坛",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        post.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = if (isWebMode) "${post.sourceName} · 原网页模式" else post.sourceName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (effectiveDetectedImages.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !isSavingImage) {
                                val requests = effectiveDetectedImages.map { buildImageRequest(it, activeThreadUrl) }
                                triggerBatchSaveWithPermission(requests)
                            }
                            .testTag("forum-batch-download-images"),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "下载本帖全部图片",
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = batchSaveProgress?.let { "${it.first}/${it.second}" } ?: "下载 ${effectiveDetectedImages.size} 图",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }
                IconButton(
                    onClick = { isWebMode = !isWebMode },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = if (isWebMode) Icons.AutoMirrored.Filled.Article else Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = if (isWebMode) "切换为原生极速阅读" else "切换为原网页视图",
                        tint = if (isWebMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = {
                        ForumPostCache.remove(activeThreadUrl)
                        refreshNonce += 1
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "刷新帖子",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            batchSaveProgress?.let { (completed, total) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "正在保存本帖图片 $completed / $total…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${(completed * 100 / total.coerceAtLeast(1))}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    AuroraWavyProgressBar(
                        progress = { completed.toFloat() / total.coerceAtLeast(1).toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Box(Modifier.fillMaxSize()) {
                if (!isWebMode) {
                    val detail = nativeDetail
                    when {
                        isNativeLoading && detail == null -> {
                            AuroraWavyProgressBar(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter),
                            )
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                                shape = MaterialTheme.shapes.extraLarge,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .testTag("forum-reader-loading"),
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                                ) {
                                    AuroraLoadingIndicator(contained = true)
                                    Text("正在加载帖子正文", style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }

                        nativeError != null && detail == null -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(24.dp),
                            ) {
                                Text(nativeError.orEmpty(), color = MaterialTheme.colorScheme.onBackground)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    TextButton(onClick = { refreshNonce += 1 }) {
                                        Text("重新加载")
                                    }
                                    TextButton(onClick = { isWebMode = true }) {
                                        Text("原网页打开")
                                    }
                                }
                            }
                        }

                        detail != null -> {
                            if (isNativeLoading) {
                                AuroraWavyProgressBar(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter),
                                )
                            }
                            NativeForumThreadContent(
                                post = post,
                                detail = detail,
                                pageUrl = activeThreadUrl,
                                listState = nativeListState,
                                isSavingImage = isSavingImage,
                                onSelectPage = navigateToThreadPage,
                                onSaveSingleImage = { imgUrl ->
                                    triggerSaveWithPermission(buildImageRequest(imgUrl, activeThreadUrl))
                                },
                                onConfirmSaveDialog = { imgUrl ->
                                    pendingImageRequest = buildImageRequest(imgUrl, activeThreadUrl)
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("forum-reader-content"),
                            )
                        }
                    }
                } else {
                    ForumWebFallbackContent(
                        url = activeThreadUrl,
                        isDarkTheme = isDarkTheme,
                        refreshNonce = refreshNonce,
                        goBackTrigger = webGoBackTrigger,
                        onCanGoBackChange = { webCanGoBack = it },
                        onDetectedImagesChange = { webDetectedImages = it },
                        onUserAgentCaptured = { readerUserAgent = it },
                        onSaveImage = { imgUrl, pageUrl ->
                            triggerSaveWithPermission(buildImageRequest(imgUrl, pageUrl))
                        },
                        onLongPressImage = { imgUrl, pageUrl ->
                            pendingImageRequest = buildImageRequest(imgUrl, pageUrl)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (isSavingImage) {
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(20.dp)
                            .testTag("forum-image-saving"),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        ) {
                            AuroraLoadingIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.inversePrimary,
                            )
                            Text("正在保存图片…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
        }
    }

    pendingImageRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { pendingImageRequest = null },
            icon = { Icon(Icons.Default.Image, contentDescription = null) },
            title = { Text("保存图片到相册？") },
            text = { Text("图片将保存到系统相册的 Pictures/AuroraShelf 文件夹。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImageRequest = null
                        triggerSaveWithPermission(request)
                    },
                    enabled = !isSavingImage,
                    modifier = Modifier.testTag("forum-image-save-confirm"),
                ) { Text("保存到相册") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImageRequest = null }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NativeForumThreadContent(
    post: ForumPost,
    detail: ForumThreadDetail,
    pageUrl: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isSavingImage: Boolean,
    onSelectPage: (String) -> Unit,
    onSaveSingleImage: (String) -> Unit,
    onConfirmSaveDialog: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val aspectRatioCache = remember(pageUrl) { mutableStateMapOf<String, Float>() }
    val openExternalLink: (String) -> Unit = { targetUrl ->
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, targetUrl.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
    LazyColumn(
        state = listState,
        flingBehavior = ScrollableDefaults.flingBehavior(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        modifier = modifier,
    ) {
        item(key = "native-thread-header", contentType = "header") {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 14.dp),
            ) {
                Text(
                    text = detail.title.ifBlank { post.title },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val semanticColors = LocalAuroraSemanticColors.current
                    val badgeColor = when (post.sourceId) {
                        "t906" -> semanticColors.forumPrimaryBadge
                        "t66y" -> semanticColors.forumSecondaryBadge
                        "t66y_16" -> semanticColors.forumTertiaryBadge
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Surface(
                        color = badgeColor,
                        contentColor = semanticColors.onForumBadge,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = post.sourceName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        text = "${detail.floors.size} 楼 · ${detail.allImageUrls.size} 图",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (post.updatedLabel.isNotBlank()) {
                        Text(
                            text = "· ${post.updatedLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (detail.pageLinks.isNotEmpty()) {
                    ForumPaginationStrip(
                        pageLinks = detail.pageLinks,
                        onSelectPage = onSelectPage,
                    )
                }
            }
        }

        detail.floors.forEach { floor ->
            val hasImages = floor.blocks.any { it is ForumContentBlock.Image }
            if (!hasImages && floor.blocks.size <= 3) {
                item(
                    key = "floor-${floor.floorNumber}-compact",
                    contentType = "floor-compact",
                ) {
                    ForumFloorCard(
                        floor = floor,
                        pageUrl = pageUrl,
                        aspectRatioCache = aspectRatioCache,
                        isSavingImage = isSavingImage,
                        onSaveSingleImage = onSaveSingleImage,
                        onConfirmSaveDialog = onConfirmSaveDialog,
                        onOpenExternalLink = openExternalLink,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            } else {
                item(
                    key = "floor-${floor.floorNumber}-head",
                    contentType = "floor-head",
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (floor.blocks.isEmpty()) Modifier.padding(bottom = 12.dp) else Modifier,
                            ),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
                        ) {
                            ForumFloorHeaderRow(floor = floor)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        }
                    }
                }
                itemsIndexed(
                    items = floor.blocks,
                    key = { idx, _ -> "floor-${floor.floorNumber}-b-$idx" },
                    contentType = { _, block ->
                        when (block) {
                            is ForumContentBlock.Image -> "floor-img"
                            is ForumContentBlock.Paragraph -> "floor-txt"
                            is ForumContentBlock.ExternalLink -> "floor-lnk"
                        }
                    },
                ) { idx, block ->
                    val isLast = idx == floor.blocks.lastIndex
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = if (isLast) {
                            RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                        } else {
                            RectangleShape
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isLast) Modifier.padding(bottom = 12.dp) else Modifier),
                    ) {
                        Box(
                            modifier = Modifier.padding(
                                start = 14.dp,
                                end = 14.dp,
                                top = 2.dp,
                                bottom = if (isLast) 14.dp else 10.dp,
                            ),
                        ) {
                            ForumFloorBlockItem(
                                block = block,
                                pageUrl = pageUrl,
                                aspectRatioCache = aspectRatioCache,
                                isSavingImage = isSavingImage,
                                onSaveSingleImage = onSaveSingleImage,
                                onConfirmSaveDialog = onConfirmSaveDialog,
                                onOpenExternalLink = openExternalLink,
                            )
                        }
                    }
                }
            }
        }

        if (detail.pageLinks.isNotEmpty()) {
            item(key = "native-thread-footer-pages", contentType = "pagination") {
                ForumPaginationStrip(
                    pageLinks = detail.pageLinks,
                    onSelectPage = onSelectPage,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun ForumPaginationStrip(
    pageLinks: List<ForumPageLink>,
    onSelectPage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        pageLinks.forEach { link ->
            Surface(
                onClick = {
                    if (!link.isCurrent) {
                        AuroraHaptics.segmentTick(haptic)
                        onSelectPage(link.url)
                    }
                },
                enabled = !link.isCurrent,
                shape = CircleShape,
                color = if (link.isCurrent) scheme.primaryContainer else scheme.surfaceContainerLow,
                contentColor = if (link.isCurrent) scheme.onPrimaryContainer else scheme.onSurface,
                border = if (link.isCurrent) null else BorderStroke(1.dp, scheme.outlineVariant),
            ) {
                Text(
                    text = link.label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (link.isCurrent) FontWeight.Bold else FontWeight.Medium,
                    ),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ForumFloorHeaderRow(floor: ForumFloor) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = floor.author.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = floor.author,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (floor.isOp) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = "楼主",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            if (floor.timestamp.isNotBlank()) {
                Text(
                    text = floor.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = "#${floor.floorNumber}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ForumFloorBlockItem(
    block: ForumContentBlock,
    pageUrl: String,
    aspectRatioCache: MutableMap<String, Float>,
    isSavingImage: Boolean,
    onSaveSingleImage: (String) -> Unit,
    onConfirmSaveDialog: (String) -> Unit,
    onOpenExternalLink: (String) -> Unit,
) {
    when (block) {
        is ForumContentBlock.Paragraph -> {
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.5.sp,
                    lineHeight = 26.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        is ForumContentBlock.Image -> {
            ForumNativeImageCard(
                imageUrl = block.url,
                pageUrl = pageUrl,
                aspectRatioCache = aspectRatioCache,
                isSavingImage = isSavingImage,
                onSaveClick = { onSaveSingleImage(block.url) },
                onLongPress = { onConfirmSaveDialog(block.url) },
            )
        }

        is ForumContentBlock.ExternalLink -> {
            Surface(
                onClick = { onOpenExternalLink(block.url) },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = block.label,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ForumFloorCard(
    floor: ForumFloor,
    pageUrl: String,
    aspectRatioCache: MutableMap<String, Float>,
    isSavingImage: Boolean,
    onSaveSingleImage: (String) -> Unit,
    onConfirmSaveDialog: (String) -> Unit,
    onOpenExternalLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            ForumFloorHeaderRow(floor = floor)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            floor.blocks.forEach { block ->
                ForumFloorBlockItem(
                    block = block,
                    pageUrl = pageUrl,
                    aspectRatioCache = aspectRatioCache,
                    isSavingImage = isSavingImage,
                    onSaveSingleImage = onSaveSingleImage,
                    onConfirmSaveDialog = onConfirmSaveDialog,
                    onOpenExternalLink = onOpenExternalLink,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ForumNativeImageCard(
    imageUrl: String,
    pageUrl: String,
    aspectRatioCache: MutableMap<String, Float>,
    isSavingImage: Boolean,
    onSaveClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val cachedRatio = aspectRatioCache[imageUrl] ?: 0f
    var decodedRatio by remember(imageUrl) { mutableFloatStateOf(cachedRatio) }
    var isFailed by remember(imageUrl) { mutableStateOf(false) }
    var retryKey by remember(imageUrl) { mutableIntStateOf(0) }
    val activeRatio = if (decodedRatio > 0f) decodedRatio else cachedRatio

    val model = remember(context, imageUrl, pageUrl, retryKey) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .httpHeaders(
                NetworkHeaders.Builder()
                    .set("User-Agent", VideoRepository.USER_AGENT)
                    .set("Referer", pageUrl)
                    .build(),
            )
            .memoryCacheKey("$imageUrl#$retryKey")
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (activeRatio > 0f) {
                    Modifier.aspectRatio(activeRatio)
                } else {
                    Modifier.aspectRatio(0.78f)
                },
            )
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    AuroraHaptics.longPress(haptic)
                    onLongPress()
                },
            ),
    ) {
        AsyncImage(
            model = model,
            contentDescription = "论坛图片",
            contentScale = ContentScale.FillWidth,
            onState = { state ->
                when (state) {
                    is AsyncImagePainter.State.Success -> {
                        isFailed = false
                        val size = state.painter.intrinsicSize
                        if (size.width > 0f && size.height > 0f) {
                            val ratio = (size.width / size.height).coerceIn(0.16f, 4.0f)
                            decodedRatio = ratio
                            aspectRatioCache[imageUrl] = ratio
                        }
                    }
                    is AsyncImagePainter.State.Error -> {
                        isFailed = true
                    }
                    else -> Unit
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (activeRatio <= 0f && !isFailed) {
            AuroraLoadingIndicator(
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.Center),
            )
        }

        if (isFailed) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
            ) {
                Text(
                    text = "外链图片加载失败",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        isFailed = false
                        retryKey += 1
                    },
                ) {
                    Text("点击重试")
                }
            }
        } else {
            Surface(
                onClick = onSaveClick,
                enabled = !isSavingImage,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.78f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            ) {
                Text(
                    text = "保存原图",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ForumWebFallbackContent(
    url: String,
    isDarkTheme: Boolean,
    refreshNonce: Int,
    goBackTrigger: Int,
    onCanGoBackChange: (Boolean) -> Unit,
    onDetectedImagesChange: (List<String>) -> Unit,
    onUserAgentCaptured: (String) -> Unit,
    onSaveImage: (imageUrl: String, pageUrl: String) -> Unit,
    onLongPressImage: (imageUrl: String, pageUrl: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestIsDarkTheme by rememberUpdatedState(isDarkTheme)
    val cachedHtml = remember(url, refreshNonce) { ForumPostCache.get(url) }
    var isLoading by remember(url, refreshNonce) { mutableStateOf(true) }
    var earlyStyledGeneration by remember(url, refreshNonce) { mutableIntStateOf(-1) }
    var error by remember(url, refreshNonce) { mutableStateOf<String?>(null) }
    var loadGeneration by remember(url, refreshNonce) { mutableIntStateOf(0) }
    var detectedImages by remember(url, refreshNonce) { mutableStateOf<List<String>>(emptyList()) }
    val latestDetectedImages by rememberUpdatedState(detectedImages)

    val webView = remember(url, refreshNonce) {
        WebView(context).apply {
            onUserAgentCaptured(settings.userAgentString)
            isNestedScrollingEnabled = false
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_ALWAYS
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                useWideViewPort = false
                loadWithOverviewMode = false
                layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                builtInZoomControls = false
                displayZoomControls = false
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    val currentGen = loadGeneration
                    if (!ForumReaderRevealPolicy.shouldRevealOnProgress(
                            progress = newProgress,
                            generation = currentGen,
                            styledGeneration = earlyStyledGeneration,
                        )
                    ) {
                        return
                    }
                    earlyStyledGeneration = currentGen
                    view?.applyMobileForumLayout(
                        isDarkTheme = latestIsDarkTheme,
                        onHtml = null,
                        onImages = { imgs ->
                            if (imgs.isNotEmpty()) {
                                detectedImages = imgs
                                onDetectedImagesChange(imgs)
                            }
                        },
                    ) { wasApplied ->
                        if (currentGen == loadGeneration && wasApplied) {
                            isLoading = false
                            error = null
                        }
                    }
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                    loadGeneration += 1
                    earlyStyledGeneration = -1
                    isLoading = true
                    error = null
                    detectedImages = emptyList()
                    onDetectedImagesChange(emptyList())
                }

                override fun onPageCommitVisible(view: WebView?, pageUrl: String?) {
                    if (cachedHtml == null) return
                    val visibleGen = loadGeneration
                    if (earlyStyledGeneration == visibleGen) return
                    earlyStyledGeneration = visibleGen
                    view?.applyMobileForumLayout(
                        isDarkTheme = latestIsDarkTheme,
                        onHtml = null,
                        onImages = { imgs ->
                            if (imgs.isNotEmpty()) {
                                detectedImages = imgs
                                onDetectedImagesChange(imgs)
                            }
                        },
                    ) {
                        if (visibleGen == loadGeneration) {
                            isLoading = false
                            error = null
                        }
                    }
                }

                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    onCanGoBackChange(view?.canGoBack() == true)
                    val finishedGeneration = loadGeneration
                    view?.applyMobileForumLayout(
                        isDarkTheme = latestIsDarkTheme,
                        onHtml = { html -> ForumPostCache.put(url, stripForumThemeClass(html)) },
                        onImages = { imgs ->
                            detectedImages = imgs
                            onDetectedImagesChange(imgs)
                        },
                    ) { wasApplied ->
                        if (finishedGeneration != loadGeneration) return@applyMobileForumLayout
                        isLoading = false
                        error = if (wasApplied) null else "帖子排版失败，请重新加载"
                    } ?: run {
                        if (finishedGeneration == loadGeneration) {
                            isLoading = false
                        }
                    }
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, failure: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        isLoading = false
                        error = "帖子加载失败，请检查网络后重试"
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val targetUri = request?.url ?: return true
                    if (targetUri.scheme == "aurorashelf" && targetUri.host == "save-image") {
                        val imageUrl = targetUri.getQueryParameter("url").orEmpty()
                        if (isAllowedForumSaveImageUrl(imageUrl, latestDetectedImages)) {
                            onSaveImage(imageUrl, view?.url ?: url)
                        }
                        return true
                    }
                    return targetUri.scheme != "https"
                }
            }
            setOnLongClickListener {
                val hit = hitTestResult
                val isImage = hit.type == WebView.HitTestResult.IMAGE_TYPE ||
                    hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                val imageUrl = hit.extra.orEmpty()
                if (!isImage || imageUrl.isBlank()) return@setOnLongClickListener false
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onLongPressImage(imageUrl, this.url ?: url)
                true
            }
            if (cachedHtml != null) {
                if (url.contains("t66y.com", ignoreCase = true)) {
                    CookieManager.getInstance().setCookie(url, "ismob=1; Path=/; Secure")
                    CookieManager.getInstance().flush()
                }
                loadDataWithBaseURL(url, cachedHtml, "text/html", "UTF-8", null)
            } else {
                loadForumPost(url)
            }
        }
    }

    LaunchedEffect(goBackTrigger) {
        if (goBackTrigger > 0 && webView.canGoBack()) {
            webView.goBack()
            onCanGoBackChange(webView.canGoBack())
        }
    }

    LaunchedEffect(webView, isDarkTheme) {
        if (!isLoading) {
            webView.evaluateJavascript(
                "(() => { if (document.body) { document.body.classList.toggle('aurora-dark', $isDarkTheme); return true; } return false; })()",
                null,
            )
        }
    }

    LaunchedEffect(loadGeneration) {
        val observedGeneration = loadGeneration
        delay(READER_LOAD_TIMEOUT_MS)
        if (isLoading && observedGeneration == loadGeneration) {
            webView.stopLoading()
            isLoading = false
            error = "帖子加载超时，请检查网络后重试"
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
            webView.stopLoading()
            webView.destroy()
        }
    }

    Box(modifier) {
        val contentAlpha by animateFloatAsState(
            targetValue = if (!isLoading && error == null) 1f else 0f,
            animationSpec = AuroraMotion.fastEffects(),
            label = "forum reader content",
        )
        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxSize()
                .alpha(contentAlpha)
                .testTag("forum-reader-content"),
        )
        if (isLoading) {
            AuroraWavyProgressBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag("forum-reader-loading"),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                ) {
                    AuroraLoadingIndicator(contained = true)
                    Text("正在整理帖子排版", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        error?.let { message ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
            ) {
                Text(message, color = MaterialTheme.colorScheme.onBackground)
                TextButton(onClick = { webView.reload() }) { Text("重新加载") }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.evaluateForumScript(
    script: String,
    onResult: (String?) -> Unit,
) {
    if (!settings.javaScriptEnabled) {
        settings.javaScriptEnabled = true
    }
    evaluateJavascript(script, onResult)
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.applyMobileForumLayout(
    isDarkTheme: Boolean,
    onHtml: ((String) -> Unit)? = null,
    onImages: ((List<String>) -> Unit)? = null,
    onComplete: (Boolean) -> Unit,
) {
    evaluateForumScript(mobileForumScript(isDarkTheme)) { result ->
        val applied = result == "true"
        val finishWithHtml: () -> Unit = {
            if (applied && onHtml != null) {
                evaluateForumScript("document.documentElement.outerHTML") { raw ->
                    if (raw != null && raw != "null") {
                        runCatching { JSONArray("[$raw]").getString(0) }.getOrNull()?.let(onHtml)
                    }
                    onComplete(true)
                }
            } else {
                onComplete(applied)
            }
        }

        if (applied && onImages != null) {
            evaluateForumScript("JSON.stringify(window.__auroraPostImages || [])") { rawImages ->
                val list = runCatching {
                    val jsonString = rawImages?.takeIf { it != "null" }?.let { JSONArray("[$it]").getString(0) } ?: "[]"
                    val arr = JSONArray(jsonString)
                    buildList {
                        for (i in 0 until arr.length()) {
                            arr.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }.let(::deduplicateForumImageUrls)
                }.getOrDefault(emptyList())
                onImages(list)
                finishWithHtml()
            }
        } else {
            finishWithHtml()
        }
    }
}

internal fun filterForumPosts(
    posts: List<ForumPost>,
    selectedSourceId: String?,
    searchQuery: String,
): List<ForumPost> {
    val sourceFiltered = if (selectedSourceId == null) {
        posts
    } else {
        posts.filter { it.sourceId == selectedSourceId }
    }
    val needle = searchQuery.trim()
    if (needle.isEmpty()) return sourceFiltered
    return sourceFiltered.filter { post ->
        post.title.contains(needle, ignoreCase = true) ||
            post.author.contains(needle, ignoreCase = true) ||
            post.sourceName.contains(needle, ignoreCase = true)
    }
}

internal fun isAllowedForumSaveImageUrl(
    imageUrl: String,
    detectedImageUrls: List<String>,
): Boolean {
    val trimmed = imageUrl.trim()
    if (trimmed.isEmpty()) return false
    val validScheme = trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("data:image/", ignoreCase = true)
    return validScheme && trimmed in detectedImageUrls
}

internal fun stripForumThemeClass(html: String): String =
    html.replace("aurora-dark", "").replace("  ", " ")

private fun WebView.loadForumPost(url: String) {
    if (!url.contains("t66y.com", ignoreCase = true)) {
        loadUrl(url)
        return
    }

    // The reference 1024 client asks the server for its mobile layout before
    // navigation. Waiting for the cookie callback avoids loading one desktop
    // frame before the mobile preference reaches WebView.
    CookieManager.getInstance().setCookie(url, "ismob=1; Path=/; Secure")
    CookieManager.getInstance().flush()
    loadUrl(url)
}

private const val READER_LOAD_TIMEOUT_MS = 15_000L
private const val POST_PREFETCH_LIMIT = 10
private const val TAG = "ForumReader"

internal object ForumReaderRevealPolicy {
    fun shouldRevealOnProgress(progress: Int, generation: Int, styledGeneration: Int): Boolean =
        progress >= 45 && styledGeneration != generation
}

internal fun mobileForumScript(isDarkTheme: Boolean): String = MOBILE_FORUM_SCRIPT.replace(
    "const auroraDark = false;",
    "const auroraDark = $isDarkTheme;",
)

internal val MOBILE_FORUM_SCRIPT =
    """
    (() => {
      try {
      const auroraDark = false;
      let viewport = document.querySelector('meta[name="viewport"]');
      if (!viewport) {
        viewport = document.createElement('meta');
        viewport.name = 'viewport';
        document.head.appendChild(viewport);
      }
      viewport.content = 'width=device-width, initial-scale=1, maximum-scale=3, user-scalable=yes';

      const host = location.hostname;
      document.body.classList.toggle('aurora-dark', auroraDark);
      if (host.includes('t66y.com')) document.body.classList.add('aurora-t66y');
      if (host.includes('t906.') || host.includes('t924.') || /^t9\d{2}\./.test(host) || host.includes('91selfie') || document.querySelector('#wrap') !== null) {
        document.body.classList.add('aurora-t906');
        const wrap = document.querySelector('#wrap');
        // The legacy page contains unclosed ad markup, so WebView may nest #wrap
        // under that ad container. Move the forum wrapper to the body before
        // applying direct-child cleanup selectors.
        if (wrap && wrap.parentElement !== document.body) document.body.replaceChildren(wrap);
      }

      const style = document.getElementById('aurora-mobile-style') || document.createElement('style');
      style.id = 'aurora-mobile-style';
      style.textContent = `
        html {
          width: 100% !important;
          min-width: 0 !important;
          max-width: 100% !important;
          margin: 0 !important;
          padding: 0 !important;
          overflow-x: hidden !important;
          overflow-y: auto !important;
        }
        body {
          width: 100% !important;
          min-width: 0 !important;
          max-width: 100% !important;
          height: auto !important;
          margin: 0 !important;
          padding: 0 !important;
          overflow-x: clip !important;
          overflow-y: visible !important;
          touch-action: pan-y pinch-zoom !important;
        }
        *, *::before, *::after { box-sizing: border-box !important; }
        img, video, iframe {
          max-width: 100% !important;
          height: auto !important;
        }
        body {
          background: #faf8ff !important;
          color: #1d1b20 !important;
          font-family: sans-serif !important;
          font-size: 16px !important;
          line-height: 1.6 !important;
        }
        #main, #wrap, #postlist, .mainbox {
          width: 100% !important;
          min-width: 0 !important;
          max-width: 100% !important;
          height: auto !important;
          overflow: visible !important;
          margin-left: 0 !important;
          margin-right: 0 !important;
          background: transparent !important;
        }
        #main, #wrap { padding: 8px 12px !important; }
        body.aurora-t66y > :not(#main),
        body.aurora-t66y #main > :not(.t2):not(.t3):not(.pages):not(.aurora-pages),
        body.aurora-t906 > :not(#wrap),
        body.aurora-t906 #wrap > :not(#postlist):not(.pages):not(.aurora-pages) {
          display: none !important;
        }
        body.aurora-t66y .t2,
        body.aurora-t906 #postlist > div {
          margin: 0 0 12px !important;
          border: 1px solid rgba(73, 69, 79, 0.16) !important;
          border-radius: 18px !important;
          background: #ffffff !important;
          overflow: hidden !important;
        }
        body.aurora-t66y #main > .t3 { display: none !important; }
        .aurora-pages {
          display: flex !important;
          align-items: center !important;
          gap: 6px !important;
          margin: 0 0 12px !important;
          padding: 8px 10px !important;
          border: 1px solid rgba(73, 69, 79, 0.14) !important;
          border-radius: 16px !important;
          background: #ffffff !important;
          overflow-x: auto !important;
          white-space: nowrap !important;
          font-size: 14px !important;
          line-height: 1.35 !important;
        }
        .aurora-pages .pages {
          display: flex !important;
          align-items: center !important;
          gap: 4px !important;
          margin: 0 !important;
          padding: 0 !important;
          font-size: inherit !important;
        }
        .aurora-pages a {
          display: inline-flex !important;
          align-items: center !important;
          min-height: 48px !important;
          padding: 4px 9px !important;
          border-radius: 10px !important;
          color: #2f5d9e !important;
          text-decoration: none !important;
        }
        .aurora-pages input, .aurora-pages select {
          width: auto !important;
          max-width: 72px !important;
          height: 44px !important;
          margin: 0 2px !important;
          padding: 2px 6px !important;
          border: 1px solid rgba(73, 69, 79, 0.25) !important;
          border-radius: 9px !important;
          font-size: 14px !important;
        }
        .t > table > tbody > tr.tr1 > th[rowspan="2"],
        td.postauthor { display: none !important; }
        .t > table, .t > table > tbody,
        .postcontent, .postmessage, .t_msgfontfix, .t_msgfont {
          width: 100% !important;
          min-width: 0 !important;
          max-width: 100% !important;
        }
        .t > table > tbody > tr.tr1 > th:not([rowspan]),
        td.postcontent { padding: 12px !important; }
        .tpc_content, .postmessage, .t_msgfont {
          font-size: 17px !important;
          line-height: 1.65 !important;
          overflow-wrap: anywhere !important;
          word-break: break-word !important;
        }
        body.aurora-t66y .tiptop,
        body.aurora-t66y .tipad,
        body.aurora-t66y .tips,
        body.aurora-t66y h4.f16,
        body.aurora-t906 [id^="ad_thread"],
        body.aurora-t906 .forumcontrol,
        body.aurora-t906 .postbtn,
        body.aurora-t906 .postbottom,
        body.aurora-t906 .threadad {
          display: none !important;
        }
        .aurora-post-meta, .postinfo {
          display: block !important;
          margin: 0 0 12px !important;
          padding: 0 0 8px !important;
          border-bottom: 1px solid rgba(127, 127, 127, 0.22) !important;
          font-size: 13px !important;
          line-height: 1.4 !important;
          color: #666 !important;
        }
        body.aurora-t906 .postinfo .pagecontrol,
        body.aurora-t906 .postinfo .new_tr,
        body.aurora-t906 .postinfo > strong,
        body.aurora-t906 .postinfo .authicon,
        body.aurora-t906 .postinfo .authorinfo > a:not(.posterlink) {
          display: none !important;
        }
        body.aurora-t906 #threadtitle { display: none !important; }
        .tpc_content > img,
        .postmessage img {
          display: block !important;
          margin: 0 auto 8px !important;
        }
        input, textarea, select { max-width: 100% !important; }
        body.aurora-dark { background: #141218 !important; color: #e6e0e9 !important; }
        body.aurora-dark.aurora-t66y .t2,
        body.aurora-dark.aurora-t906 #postlist > div,
        body.aurora-dark .aurora-pages {
          border-color: rgba(202, 196, 208, 0.18) !important;
          background: #211f26 !important;
        }
        body.aurora-dark .tpc_content,
        body.aurora-dark .tpc_content *,
        body.aurora-dark .postmessage,
        body.aurora-dark .postmessage *,
        body.aurora-dark .t_msgfont,
        body.aurora-dark .t_msgfont *,
        body.aurora-dark td.postcontent {
          color: #e6e0e9 !important;
          background-color: transparent !important;
        }
        body.aurora-dark .aurora-post-meta,
        body.aurora-dark .postinfo {
          border-color: rgba(202, 196, 208, 0.18) !important;
          color: #cac4d0 !important;
        }
        body.aurora-dark a:not(.aurora-img-save-btn),
        body.aurora-dark .aurora-pages a { color: #aac7ff !important; }
        .aurora-img-save-btn { color: #ffffff !important; }
      `;
      if (!style.parentNode) document.head.appendChild(style);

      const preparePageBars = (container, firstContent) => {
        if (!container || container.querySelector(':scope > .aurora-pages')) return;
        const pageBars = Array.from(container.querySelectorAll('.pages') || []);
        pageBars.forEach((pages, index) => {
          const navigation = document.createElement('nav');
          navigation.className = 'aurora-pages';
          navigation.setAttribute('aria-label', index === 0 ? '帖子翻页' : '帖子底部翻页');
          navigation.appendChild(pages);
          if (index === 0 && firstContent) container.insertBefore(navigation, firstContent);
          else container.appendChild(navigation);
        });
      };

      if (document.body.classList.contains('aurora-t66y')) {
        const main = document.querySelector('#main');
        const firstPost = main?.querySelector(':scope > .t2');
        preparePageBars(main, firstPost);
        document.querySelectorAll('#main > .t2').forEach((post) => {
          const content = post.querySelector('tr.tr1 > th:not([rowspan])');
          if (content && !content.querySelector('.aurora-post-meta')) {
            const author = post.querySelector('tr.tr1 > th[rowspan="2"] > b')?.textContent?.trim();
            const time = post.querySelector('.tipad [data-timestamp]')?.textContent?.trim();
            if (author || time) {
              const meta = document.createElement('div');
              meta.className = 'aurora-post-meta';
              meta.textContent = [author, time].filter(Boolean).join(' · ');
              content.prepend(meta);
            }
          }
        });
      }
      if (document.body.classList.contains('aurora-t906')) {
        const wrap = document.querySelector('#wrap');
        preparePageBars(wrap, wrap?.querySelector(':scope > #postlist'));
      }

      document.querySelectorAll('img[ess-data]').forEach((image) => {
        const source = image.getAttribute('ess-data');
        image.loading = 'lazy';
        image.decoding = 'async';
        if (source && source.startsWith('https://')) image.src = source;
      });
      document.querySelectorAll('img[file]').forEach((image) => {
        const source = image.getAttribute('file');
        image.loading = 'lazy';
        image.decoding = 'async';
        if (source && source.startsWith('https://')) image.src = source;
      });
      document.querySelectorAll('img[data-src]').forEach((image) => {
        const source = image.getAttribute('data-src');
        image.loading = 'lazy';
        image.decoding = 'async';
        if (source && source.startsWith('https://')) image.src = source;
      });

      const contentImgs = Array.from(document.querySelectorAll('.tpc_content img, .postmessage img, .t_msgfont img, td.postcontent img'));
      const collected = [];
      const seenImg = new Set();
      contentImgs.forEach((img) => {
        const src = (img.currentSrc || img.src || '').trim();
        if (!(src.startsWith('https://') || src.startsWith('data:image/'))) return;
        const lower = src.toLowerCase();
        if (lower.includes('smiley') || lower.includes('/smiles/') || lower.includes('/static/image/common/') || lower.includes('avatar')) return;
        if (img.naturalWidth > 0 && img.naturalWidth < 48 && img.naturalHeight > 0 && img.naturalHeight < 48) return;
        if (!seenImg.has(src)) {
          seenImg.add(src);
          collected.push(src);
        }
        if (!img.parentElement?.classList?.contains('aurora-img-wrap')) {
          const wrap = document.createElement('div');
          wrap.className = 'aurora-img-wrap';
          wrap.style.cssText = 'position:relative;display:block;margin:0 auto 10px;text-align:center;';
          img.parentNode?.insertBefore(wrap, img);
          wrap.appendChild(img);
          const btn = document.createElement('a');
          btn.className = 'aurora-img-save-btn';
          btn.href = 'aurorashelf://save-image?url=' + encodeURIComponent(src);
          btn.textContent = '保存原图';
          btn.style.cssText = 'position:absolute;right:8px;bottom:8px;padding:4px 10px;border-radius:14px;background:rgba(18,18,22,0.78);color:#fff;font-size:12px;font-weight:600;text-decoration:none;border:1px solid rgba(255,255,255,0.22);z-index:5;';
          wrap.appendChild(btn);
        }
      });
      window.__auroraPostImages = collected;
      return true;
      } catch (error) {
        return false;
      }
    })();
    """.trimIndent()
