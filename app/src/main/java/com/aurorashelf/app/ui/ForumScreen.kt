package com.aurorashelf.app.ui

import android.annotation.SuppressLint
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import android.webkit.CookieManager
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
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
import com.aurorashelf.app.data.forum.shouldForwardCookies
import com.aurorashelf.app.model.ForumPost
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

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
    onSettings: () -> Unit = {},
) {
    // Keep the list state in composition while the reader is open.
    val listState = rememberLazyListState()
    LaunchedEffect(state.posts.size) {
        if (state.posts.isNotEmpty()) {
            ForumPostCache.prefetchAll(state.posts.map { it.url }, limit = POST_PREFETCH_LIMIT)
        }
    }
    state.selectedPost?.let { post ->
        ForumReader(post = post, onClose = onClosePost)
        return
    }

    val visiblePosts = remember(state.posts, state.selectedSourceId) {
        if (state.selectedSourceId == null) state.posts
        else state.posts.filter { it.sourceId == state.selectedSourceId }
    }

    LaunchedEffect(Unit) { onEnsureLoaded() }
    LaunchedEffect(listState, visiblePosts.size, state.canLoadMore) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            visiblePosts.isNotEmpty() && lastVisible >= visiblePosts.lastIndex - 4
        }.distinctUntilChanged().filter { it }.collect { onLoadMore() }
    }

    Surface(color = AuroraImmersiveBackground, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = bottomPadding + 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().statusBarsPadding().testTag("forum-feed"),
        ) {
            item {
                NetflixTopBar() {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !state.isLoading,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("forum-refresh"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新论坛",
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

            item(key = "forum-pinned-sources") {
                CategoryStrip(testTag = "forum-category-bar") {
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
                        selected = state.selectedSourceId == "t906",
                        onClick = { onSelectSource("t906") },
                        label = "91自拍论坛",
                    )
                }
            }

        if (state.unavailableSources.isNotEmpty()) {
            item {
                Surface(
                    color = Color(0xFF1E222B),
                    contentColor = Color(0xFFD6DCE5),
                    border = BorderStroke(1.dp, Color(0xFF333A48)),
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

        if (state.error != null && state.posts.isNotEmpty()) {
            item {
                Surface(
                    color = Color(0xFF2B1417),
                    contentColor = Color(0xFFFFB4AB),
                    border = BorderStroke(1.dp, Color(0xFF5C2026)),
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
            item {
                Box(Modifier.fillParentMaxSize().padding(top = 120.dp), contentAlignment = Alignment.TopCenter) {
                    CircularProgressIndicator(modifier = Modifier.testTag("forum-loading"))
                }
            }
        } else if (state.error != null && state.posts.isEmpty()) {
            item {
                ForumError(message = state.error, onRetry = onRefresh)
            }
        } else if (visiblePosts.isEmpty() && state.posts.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "该板块暂未抓取到更多帖子，请下拉刷新或浏览【全部】",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AuroraMediaMuted,
                    )
                }
            }
        } else {
            items(visiblePosts, key = { "${it.sourceId}:${it.id}" }) { post ->
                ForumPostCard(post = post, onClick = { onPost(post) })
            }
        }

        if (state.isLoadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        } else if (state.loadMoreError != null && state.posts.isNotEmpty()) {
            item {
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
    }
}

@Composable
private fun ForumPostCard(post: ForumPost, onClick: () -> Unit) {
    Surface(
        color = AuroraMediaSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF262626)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .applePressEffect(onClick = onClick)
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
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val badgeColor = when (post.sourceId) {
                    "t906" -> Color(0xFFE50914) // Netflix Red / 91
                    "t66y" -> Color(0xFF2E7D32) // Forest Green / 1024
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
                    color = AuroraMediaMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = AuroraMediaMuted, modifier = Modifier.size(14.dp))
                Text(
                    text = post.replyCount.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    color = AuroraMediaLabel,
                )
                if (post.updatedLabel.isNotBlank()) {
                    Text(
                        text = post.updatedLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = AuroraMediaMuted,
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
    val isDarkTheme = true
    val cachedHtml = remember(post.url) { ForumPostCache.get(post.url) }
    val startedAt = remember(post.url) { System.currentTimeMillis() }
    var cachedRendered by remember(post.url) { mutableStateOf(false) }
    var isLoading by remember(post.url) { mutableStateOf(true) }
    var error by remember(post.url) { mutableStateOf<String?>(null) }
    var canGoBack by remember(post.url) { mutableStateOf(false) }
    var loadGeneration by remember(post.url) { mutableStateOf(0) }
    var pendingImageRequest by remember(post.url) { mutableStateOf<ForumImageRequest?>(null) }
    var permissionImageRequest by remember(post.url) { mutableStateOf<ForumImageRequest?>(null) }
    var isSavingImage by remember(post.url) { mutableStateOf(false) }
    val saveImage: (ForumImageRequest) -> Unit = { request ->
        if (!isSavingImage) {
            isSavingImage = true
            coroutineScope.launch {
                val result = imageSaver.save(request)
                isSavingImage = false
                snackbarHostState.showSnackbar(
                    result.fold(
                        onSuccess = { "图片已保存到系统相册" },
                        onFailure = { failure -> failure.message ?: "图片保存失败，请稍后重试" },
                    ),
                )
            }
        }
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        val request = permissionImageRequest
        permissionImageRequest = null
        if (isGranted && request != null) {
            saveImage(request)
        } else if (!isGranted) {
            coroutineScope.launch { snackbarHostState.showSnackbar("没有存储权限，无法保存图片") }
        }
    }
    val webView = remember(post.url) {
        WebView(context).apply {
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
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    loadGeneration += 1
                    isLoading = true
                    error = null
                    Log.i(TAG, "pageStarted cacheHit=${cachedHtml != null}")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    canGoBack = view?.canGoBack() == true
                    if (cachedRendered) return
                    Log.i(TAG, "pageFinished totalMs=${System.currentTimeMillis() - startedAt} cacheHit=${cachedHtml != null}")
                    val finishedGeneration = loadGeneration
                    view?.applyMobileForumLayout(
                        isDarkTheme = isDarkTheme,
                        onHtml = { html -> ForumPostCache.put(post.url, html) },
                    ) { wasApplied ->
                        if (finishedGeneration != loadGeneration) return@applyMobileForumLayout
                        isLoading = false
                        error = if (wasApplied) null else "帖子排版失败，请重新加载"
                    } ?: run { isLoading = false }
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, failure: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        isLoading = false
                        error = "帖子加载失败，请检查网络后重试"
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                    request?.url?.scheme != "https"
            }
            setOnLongClickListener {
                val hit = hitTestResult
                val isImage = hit.type == WebView.HitTestResult.IMAGE_TYPE ||
                    hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                val imageUrl = hit.extra.orEmpty()
                if (!isImage || imageUrl.isBlank()) return@setOnLongClickListener false
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                val currentPageUrl = url ?: post.url
                pendingImageRequest = ForumImageRequest(
                    imageUrl = imageUrl,
                    pageUrl = currentPageUrl,
                    userAgent = settings.userAgentString,
                    cookies = if (shouldForwardCookies(currentPageUrl, imageUrl)) {
                        CookieManager.getInstance().getCookie(currentPageUrl)
                    } else {
                        null
                    },
                )
                true
            }
            if (cachedHtml != null) {
                val baseUrl = post.url
                if (baseUrl.contains("t66y.com", ignoreCase = true)) {
                    CookieManager.getInstance().setCookie(baseUrl, "ismob=1; Path=/; Secure") {
                        loadDataWithBaseURL(baseUrl, cachedHtml, "text/html", "UTF-8", null)
                    }
                } else {
                    loadDataWithBaseURL(baseUrl, cachedHtml, "text/html", "UTF-8", null)
                }
            } else {
                loadForumPost(post.url)
            }
        }
    }
    LaunchedEffect(webView, cachedHtml) {
        if (cachedHtml == null) return@LaunchedEffect
        delay(250)
        if (isLoading && !cachedRendered) {
            cachedRendered = true
            isLoading = false
            error = null
            Log.i(TAG, "cachedPaint ms=${System.currentTimeMillis() - startedAt}")
            webView.applyMobileForumLayout(
                isDarkTheme = isDarkTheme,
                onHtml = { html -> ForumPostCache.put(post.url, html) },
            ) { }
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
    BackHandler {
        if (canGoBack && webView.canGoBack()) webView.goBack() else onClose()
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

    Column(Modifier.fillMaxSize().background(Color(0xFF141218)).statusBarsPadding().testTag("forum-reader")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回论坛", tint = Color.White)
            }
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(post.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                Text(post.sourceName, style = MaterialTheme.typography.labelMedium, color = AuroraMediaMuted)
            }
            IconButton(onClick = { webView.reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新帖子", tint = Color.White)
            }
        }
        HorizontalDivider(color = Color(0xFF262626))
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
                    color = Color(0xFF211F26),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color(0xFF33303A)),
                    modifier = Modifier.align(Alignment.Center).testTag("forum-reader-loading"),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp, color = AuroraCoral)
                        Text("正在整理帖子排版", style = MaterialTheme.typography.titleSmall, color = Color.White)
                        Text(
                            "内容准备好后会一次显示",
                            style = MaterialTheme.typography.bodySmall,
                            color = AuroraMediaMuted,
                        )
                    }
                }
            }
            error?.let { message ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.Center).background(Color(0xFF141218)).padding(24.dp),
                ) {
                    Text(message, color = Color.White)
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

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.applyMobileForumLayout(
    isDarkTheme: Boolean,
    onHtml: ((String) -> Unit)? = null,
    onComplete: (Boolean) -> Unit,
) {
    settings.javaScriptEnabled = true
    evaluateJavascript(mobileForumScript(isDarkTheme)) { result ->
        if (result == "true" && onHtml != null) {
            evaluateJavascript("document.documentElement.outerHTML") { raw ->
                if (raw != null && raw != "null") {
                    runCatching { JSONArray("[$raw]").getString(0) }.getOrNull()?.let(onHtml)
                }
                settings.javaScriptEnabled = false
                onComplete(true)
            }
        } else {
            settings.javaScriptEnabled = false
            onComplete(result == "true")
        }
    }
}

private fun WebView.loadForumPost(url: String) {
    if (!url.contains("t66y.com", ignoreCase = true)) {
        loadUrl(url)
        return
    }

    // The reference 1024 client asks the server for its mobile layout before
    // navigation. Waiting for the cookie callback avoids loading one desktop
    // frame before the mobile preference reaches WebView.
    CookieManager.getInstance().setCookie(url, "ismob=1; Path=/; Secure") {
        loadUrl(url)
    }
}

private const val READER_LOAD_TIMEOUT_MS = 15_000L
private const val POST_PREFETCH_LIMIT = 20
private const val TAG = "ForumReader"

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
        if (wrap) document.body.replaceChildren(wrap);
      }

      const style = document.createElement('style');
      style.textContent = `
        html, body {
          width: 100% !important;
          min-width: 0 !important;
          max-width: 100% !important;
          margin: 0 !important;
          padding: 0 !important;
          overflow-x: hidden !important;
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
        body.aurora-dark .aurora-post-meta,
        body.aurora-dark .postinfo {
          border-color: rgba(202, 196, 208, 0.18) !important;
          color: #cac4d0 !important;
        }
        body.aurora-dark .aurora-pages a { color: #aac7ff !important; }
      `;
      document.head.appendChild(style);

      const preparePageBars = (container, firstContent) => {
        const pageBars = Array.from(container?.querySelectorAll('.pages') || []);
        pageBars.forEach((pages, index) => {
          const navigation = document.createElement('nav');
          navigation.className = 'aurora-pages';
          navigation.setAttribute('aria-label', index === 0 ? '帖子翻页' : '帖子底部翻页');
          navigation.appendChild(pages);
          if (index === 0 && firstContent) container.insertBefore(navigation, firstContent);
          else container?.appendChild(navigation);
        });
      };

      if (document.body.classList.contains('aurora-t66y')) {
        const main = document.querySelector('#main');
        const firstPost = main?.querySelector(':scope > .t2');
        preparePageBars(main, firstPost);
        document.querySelectorAll('#main > .t2').forEach((post) => {
          const content = post.querySelector('tr.tr1 > th:not([rowspan])');
          const author = post.querySelector('tr.tr1 > th[rowspan="2"] > b')?.textContent?.trim();
          const time = post.querySelector('.tipad [data-timestamp]')?.textContent?.trim();
          if (content && (author || time)) {
            const meta = document.createElement('div');
            meta.className = 'aurora-post-meta';
            meta.textContent = [author, time].filter(Boolean).join(' · ');
            content.prepend(meta);
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
      return true;
      } catch (error) {
        return false;
      }
    })();
    """.trimIndent()
