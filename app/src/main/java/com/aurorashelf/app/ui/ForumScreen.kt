package com.aurorashelf.app.ui

import android.annotation.SuppressLint
import android.Manifest
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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurorashelf.app.ui.theme.AuroraCoral
import com.aurorashelf.app.ui.theme.AuroraImmersiveBackground
import com.aurorashelf.app.ui.theme.AuroraMediaLabel
import com.aurorashelf.app.ui.theme.AuroraMediaMuted
import com.aurorashelf.app.ui.theme.AuroraMediaSurface
import org.json.JSONArray
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aurorashelf.app.data.forum.ForumImageRequest
import com.aurorashelf.app.data.forum.ForumImageSaver
import com.aurorashelf.app.data.forum.ForumPostCache
import com.aurorashelf.app.data.forum.deduplicateForumImageUrls
import com.aurorashelf.app.data.forum.shouldForwardCookies
import com.aurorashelf.app.model.ForumPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 40
        }
    }
    LaunchedEffect(state.posts.size) {
        if (state.posts.isNotEmpty()) {
            kotlinx.coroutines.delay(400)
            ForumPostCache.prefetchAll(state.posts.map { it.url }, limit = POST_PREFETCH_LIMIT, concurrency = 2)
        }
    }
    state.selectedPost?.let { post ->
        ForumReader(post = post, onClose = onClosePost)
        return
    }

    BackHandler(enabled = state.isSearchOpen) {
        onCloseSearch()
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

    var headerHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val headerSpacerHeight = remember(headerHeightPx, density) {
        if (headerHeightPx > 0) with(density) { headerHeightPx.toDp() + 8.dp } else 115.dp
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                flingBehavior = ScrollableDefaults.flingBehavior(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = bottomPadding + 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().testTag("forum-feed"),
            ) {
                item(key = "forum-header-spacer") {
                    Spacer(Modifier.height(headerSpacerHeight))
                }

        if (state.unavailableSources.isNotEmpty()) {
            item(key = "forum-unavailable") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(14.dp),
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

        if (state.error != null && state.posts.isEmpty().not()) {
            item(key = "forum-refresh-error") {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("forum-refresh-error-banner"),
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
                Box(Modifier.fillParentMaxSize().padding(top = 120.dp), contentAlignment = Alignment.TopCenter) {
                    CircularProgressIndicator(modifier = Modifier.testTag("forum-loading"))
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
                ForumPostCard(post = post, onClick = { onPost(post) })
            }
        }

        if (state.isLoadingMore) {
            item(key = "forum-loading-more") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        } else if (state.loadMoreError != null && state.posts.isNotEmpty()) {
            item(key = "forum-load-more-error") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
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
        Modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant), RectangleShape)
    } else {
        Modifier.background(
            Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                    MaterialTheme.colorScheme.background.copy(alpha = 0.60f),
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
            CenteredSearchBar(
                query = state.searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = "搜索社区帖子、作者或板块",
                onSearch = {},
                onBack = onCloseSearch,
                onClear = { onSearchQueryChange("") },
                isSearching = state.isLoading,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                testTag = "forum-search-field",
                forceDark = false,
            )
        } else {
            NetflixTopBar {
                NetflixSearchCapsule(
                    label = if (state.searchQuery.isNotBlank()) state.searchQuery else "搜索社区",
                    onClick = onSearch,
                )
            }
            CategoryStrip(
                testTag = "forum-category-bar",
                isScrolled = isScrolled,
                applyBackground = false,
            ) {
                NetflixCategoryChip(
                    selected = state.selectedSourceId == null,
                    onClick = { onSelectSource(null) },
                    label = "全部",
                )
                NetflixCategoryChip(
                    selected = state.selectedSourceId == "t66y",
                    onClick = { onSelectSource("t66y") },
                    label = "草榴社区",
                )
                NetflixCategoryChip(
                    selected = state.selectedSourceId == "t66y_16",
                    onClick = { onSelectSource("t66y_16") },
                    label = "达盖尔的旗帜",
                )
                NetflixCategoryChip(
                    selected = state.selectedSourceId == "t906",
                    onClick = { onSelectSource("t906") },
                    label = "91自拍论坛",
                )
            }
        }
        if (state.isLoading) {
            LinearProgressIndicator(
                color = AuroraCoral,
                trackColor = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
        }
    }
}
}
}

@Composable
private fun ForumPostCard(post: ForumPost, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier
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
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val badgeColor = when (post.sourceId) {
                    "t906" -> Color(0xFFE50914) // Netflix Red / 91
                    "t66y" -> Color(0xFF2E7D32) // Forest Green / 1024
                    "t66y_16" -> Color(0xFF00695C) // Peacock Green / 达盖尔
                    "sis" -> AuroraCoral
                    else -> Color(0xFFE65100)
                }
                Surface(
                    color = badgeColor,
                    shape = RoundedCornerShape(3.dp),
                ) {
                    Text(
                        text = post.sourceName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = post.author,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(
                    text = post.replyCount.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (post.updatedLabel.isNotBlank()) {
                    Text(
                        text = post.updatedLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ForumError(message: String, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(28.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry) { Text("重新加载") }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ForumReader(post: ForumPost, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val imageSaver = remember(context) { ForumImageSaver(context) }
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() <= 0.5f
    val latestIsDarkTheme by rememberUpdatedState(isDarkTheme)
    val cachedHtml = remember(post.url) { ForumPostCache.get(post.url) }
    val startedAt = remember(post.url) { System.currentTimeMillis() }
    var isLoading by remember(post.url) { mutableStateOf(true) }
    var earlyStyledGeneration by remember(post.url) { mutableIntStateOf(-1) }
    var error by remember(post.url) { mutableStateOf<String?>(null) }
    var canGoBack by remember(post.url) { mutableStateOf(false) }
    var loadGeneration by remember(post.url) { mutableIntStateOf(0) }
    var pendingImageRequest by remember(post.url) { mutableStateOf<ForumImageRequest?>(null) }
    var permissionImageRequest by remember(post.url) { mutableStateOf<ForumImageRequest?>(null) }
    var permissionBatchRequests by remember(post.url) { mutableStateOf<List<ForumImageRequest>?>(null) }
    var isSavingImage by remember(post.url) { mutableStateOf(false) }
    var batchSaveProgress by remember(post.url) { mutableStateOf<Pair<Int, Int>?>(null) }
    var detectedImageUrls by remember(post.url) { mutableStateOf<List<String>>(emptyList()) }
    val latestDetectedImages by rememberUpdatedState(detectedImageUrls)
    var readerUserAgent by remember(post.url) { mutableStateOf("Mozilla/5.0") }

    val buildImageRequest: (String, String) -> ForumImageRequest = { imageUrl, currentPageUrl ->
        ForumImageRequest(
            imageUrl = imageUrl,
            pageUrl = currentPageUrl,
            userAgent = readerUserAgent,
            cookies = if (shouldForwardCookies(currentPageUrl, imageUrl)) {
                CookieManager.getInstance().getCookie(currentPageUrl)
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

    val webView = remember(post.url) {
        WebView(context).apply {
            readerUserAgent = settings.userAgentString
            // ForumReader sits directly inside a Box + PredictiveBackContainer without a
            // nested-scrolling parent; disabling nested scroll lets WebView own 100% of
            // vertical touch pans and flings natively without competing with Compose.
            isNestedScrollingEnabled = false
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_ALWAYS
            settings.apply {
                javaScriptEnabled = false
                domStorageEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                useWideViewPort = false
                loadWithOverviewMode = false
                layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                builtInZoomControls = true
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
                        onImages = { imgs -> if (imgs.isNotEmpty()) detectedImageUrls = imgs },
                    ) { wasApplied ->
                        if (currentGen == loadGeneration && wasApplied) {
                            isLoading = false
                            error = null
                        }
                    }
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    loadGeneration += 1
                    earlyStyledGeneration = -1
                    isLoading = true
                    error = null
                    detectedImageUrls = emptyList()
                    Log.i(TAG, "pageStarted cacheHit=${cachedHtml != null}")
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    if (cachedHtml == null) return
                    val visibleGen = loadGeneration
                    if (earlyStyledGeneration == visibleGen) return
                    earlyStyledGeneration = visibleGen
                    view?.applyMobileForumLayout(
                        isDarkTheme = latestIsDarkTheme,
                        onHtml = null,
                        onImages = { imgs -> if (imgs.isNotEmpty()) detectedImageUrls = imgs },
                    ) {
                        if (visibleGen == loadGeneration) {
                            isLoading = false
                            error = null
                        }
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    canGoBack = view?.canGoBack() == true
                    Log.i(TAG, "pageFinished totalMs=${System.currentTimeMillis() - startedAt} cacheHit=${cachedHtml != null}")
                    val finishedGeneration = loadGeneration
                    view?.applyMobileForumLayout(
                        isDarkTheme = latestIsDarkTheme,
                        onHtml = { html -> ForumPostCache.put(post.url, stripForumThemeClass(html)) },
                        onImages = { imgs -> detectedImageUrls = imgs },
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
                            val currentPageUrl = view?.url ?: post.url
                            triggerSaveWithPermission(buildImageRequest(imageUrl, currentPageUrl))
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
                val currentPageUrl = url ?: post.url
                pendingImageRequest = buildImageRequest(imageUrl, currentPageUrl)
                true
            }
            if (cachedHtml != null) {
                val baseUrl = post.url
                if (baseUrl.contains("t66y.com", ignoreCase = true)) {
                    CookieManager.getInstance().setCookie(baseUrl, "ismob=1; Path=/; Secure")
                    CookieManager.getInstance().flush()
                }
                // Load synchronously without a racing cookie callback so cached posts never
                // have onPageStarted arrive after an external timer clears isLoading.
                loadDataWithBaseURL(baseUrl, cachedHtml, "text/html", "UTF-8", null)
            } else {
                loadForumPost(post.url)
            }
        }
    }
    LaunchedEffect(webView, isDarkTheme) {
        if (!isLoading) {
            webView.evaluateForumScript(
                "(() => { if (document.body) { document.body.classList.toggle('aurora-dark', $isDarkTheme); return true; } return false; })()",
            ) {}
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
    BackHandler(enabled = canGoBack && webView.canGoBack()) {
        webView.goBack()
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

    PredictiveBackContainer(
        enabled = !(canGoBack && webView.canGoBack()),
        onBack = onClose,
    ) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().testTag("forum-reader")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回论坛", tint = MaterialTheme.colorScheme.onBackground)
            }
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(post.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                Text(post.sourceName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (detectedImageUrls.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AuroraCoral.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, AuroraCoral.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = !isSavingImage) {
                            val currentPageUrl = webView.url ?: post.url
                            val requests = detectedImageUrls.map { buildImageRequest(it, currentPageUrl) }
                            triggerBatchSaveWithPermission(requests)
                        }
                        .testTag("forum-batch-download-images"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "下载本帖全部图片",
                            tint = AuroraCoral,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = batchSaveProgress?.let { "${it.first}/${it.second}" } ?: "下载 ${detectedImageUrls.size} 图",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AuroraCoral,
                            maxLines = 1,
                        )
                    }
                }
            }
            IconButton(onClick = { webView.reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新帖子", tint = MaterialTheme.colorScheme.onBackground)
            }
        }
        batchSaveProgress?.let { (completed, total) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
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
                        color = AuroraCoral,
                    )
                }
                LinearProgressIndicator(
                    progress = { completed.toFloat() / total.coerceAtLeast(1).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = AuroraCoral,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.fillMaxSize()) {
            val contentAlpha by animateFloatAsState(
                targetValue = if (!isLoading && error == null) 1f else 0f,
                animationSpec = tween(durationMillis = 180),
                label = "forum reader content",
            )
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize().alpha(contentAlpha).testTag("forum-reader-content"),
            )
            if (isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = AuroraCoral)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.align(Alignment.Center).testTag("forum-reader-loading"),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp, color = AuroraCoral)
                        Text("正在整理帖子排版", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "内容准备好后会一次显示",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            error?.let { message ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.Center).background(MaterialTheme.colorScheme.background).padding(24.dp),
                ) {
                    Text(message, color = MaterialTheme.colorScheme.onBackground)
                    TextButton(onClick = { webView.reload() }) { Text("重新加载", color = AuroraCoral) }
                }
            }
            if (isSavingImage) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp).testTag("forum-image-saving"),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
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
                        val needsLegacyPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                            PackageManager.PERMISSION_GRANTED
                        if (needsLegacyPermission) {
                            permissionImageRequest = request
                            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            saveImage(request)
                        }
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

private val activeForumJsCalls = java.util.WeakHashMap<WebView, java.util.concurrent.atomic.AtomicInteger>()

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.evaluateForumScript(
    script: String,
    onResult: (String?) -> Unit,
) {
    val counter = synchronized(activeForumJsCalls) {
        activeForumJsCalls.getOrPut(this) { java.util.concurrent.atomic.AtomicInteger(0) }.also {
            it.incrementAndGet()
        }
    }
    settings.javaScriptEnabled = true
    evaluateJavascript(script) { raw ->
        try {
            onResult(raw)
        } finally {
            if (counter.decrementAndGet() <= 0) {
                counter.set(0)
                settings.javaScriptEnabled = false
            }
        }
    }
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
        html, body {
          width: 100% !important;
          min-width: 0 !important;
          max-width: 100% !important;
          min-height: 100% !important;
          height: auto !important;
          margin: 0 !important;
          padding: 0 !important;
          overflow-x: hidden !important;
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
        if (source && source.startsWith('https://')) image.src = source;
      });
      document.querySelectorAll('img[file]').forEach((image) => {
        const source = image.getAttribute('file');
        if (source && source.startsWith('https://')) image.src = source;
      });
      document.querySelectorAll('img[data-src]').forEach((image) => {
        const source = image.getAttribute('data-src');
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
