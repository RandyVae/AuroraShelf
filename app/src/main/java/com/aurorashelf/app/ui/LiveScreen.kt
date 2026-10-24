package com.aurorashelf.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.aurorashelf.app.data.StripchatLiveRepository
import com.aurorashelf.app.model.LiveCategory
import com.aurorashelf.app.model.LiveComment
import com.aurorashelf.app.model.LiveItem
import com.aurorashelf.app.ui.theme.AuroraCoral
import com.aurorashelf.app.ui.theme.AuroraImmersiveBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray

@Composable
internal fun LiveScreen(
    state: LiveUiState,
    bottomPadding: Dp,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    onCategory: (LiveCategory) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (LiveItem) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(enabled = state.selectedItem != null && !isFullscreen) { onClose() }
    Box(Modifier.fillMaxSize()) {
        state.selectedItem?.let { item ->
            LivePlayerScreen(
                item = item,
                isFullscreen = isFullscreen,
                onFullscreenChange = onFullscreenChange,
                onBack = onClose,
            )
        } ?: LiveFeed(
            state = state,
            bottomPadding = bottomPadding,
            onCategory = onCategory,
            onRefresh = onRefresh,
            onLoadMore = onLoadMore,
            onOpen = onOpen,
        )
        // Keep the last opened room's WebView alive so re-entering it is instant.
        val retained = LiveSessionCache.retained
        if (state.selectedItem == null && !isFullscreen && retained != null) {
            key(retained) {
                AndroidView(factory = { retained }, modifier = Modifier.size(1.dp))
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { LiveSessionCache.release() }
    }
}

@Composable
private fun LiveFeed(
    state: LiveUiState,
    bottomPadding: Dp,
    onCategory: (LiveCategory) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (LiveItem) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    // C3: with >=150% system font the artwork columns widen with the font scale (capped),
    // so the adaptive grid collapses to one column instead of clipping card text.
    val gridState = rememberLazyGridState()
    val prefetchContext = LocalContext.current
    // task-17: warm the first screen's previews (bytes + decode) without touching card layout.
    LaunchedEffect(state.items) {
        if (state.items.isNotEmpty()) {
            CoverPrefetcher.prefetchLive(prefetchContext, state.items, limit = LIVE_COVER_PREFETCH_LIMIT)
        }
    }
    LaunchedEffect(gridState, state.items.size, state.canLoadMore) {
        snapshotFlow {
            val layout = gridState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to layout.totalItemsCount
        }.map { (lastVisible, totalItems) ->
            totalItems > 0 && lastVisible >= totalItems - 5
        }.distinctUntilChanged().filter { it }.collect {
            onLoadMore()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = scaledAdaptiveMinSize(164.dp, fontScale)),
        state = gridState,
        contentPadding = PaddingValues(start = 18.dp, top = 20.dp, end = 18.dp, bottom = bottomPadding + 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding().testTag("live-grid"),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            LiveHeader(onRefresh = onRefresh)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                LiveCategory.entries.forEach { category ->
                    FilterChip(
                        selected = state.selectedCategory == category,
                        onClick = { onCategory(category) },
                        label = { Text(category.label) },
                    )
                }
            }
        }

        if (state.isLoading && state.items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(320.dp)) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        } else if (state.error != null && state.items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LiveError(message = state.error, onRetry = onRefresh)
            }
        } else if (state.items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(260.dp)) {
                    Text("当前分类没有公开直播", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        items(state.items, key = LiveItem::id) { item ->
            LiveCard(item = item, onClick = { onOpen(item) })
        }

        if (state.isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }
        } else if (state.error != null && state.items.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                    Text("加载更多失败，点击重试")
                }
            }
        }
    }
}

@Composable
private fun LiveHeader(onRefresh: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "直播",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Stripchat · 公开直播 · 18+",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = "刷新直播列表")
        }
    }
}

@Composable
private fun LiveCard(item: LiveItem, onClick: () -> Unit) {
    val flag = countryFlag(item.countryCode)
    val context = LocalContext.current
    val previewBytes by produceState<ByteArray?>(initialValue = null, key1 = item.thumbnailUrl) {
        value = withContext(Dispatchers.IO) {
            CoverImageLoader.load(item.thumbnailUrl, "${StripchatLiveRepository.SITE_BASE}/")
        }
    }
    // URL-keyed memory cache so scrolling back does not decode the same ByteArray again.
    val previewRequest = remember(previewBytes, item.thumbnailUrl, context) {
        previewBytes?.takeIf { it.isNotEmpty() }?.let { bytes ->
            ImageRequest.Builder(context)
                .data(bytes)
                .memoryCacheKey(coverMemoryCacheKey(item.thumbnailUrl))
                .build()
        }
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "观看 ${item.username} 的公开直播" }
            .testTag("live-card"),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = previewRequest,
                    contentDescription = "${item.username} 直播预览",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
                )
                DarkModeArtworkScrim()
                Surface(
                    color = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text("直播中", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                if (item.isHd) {
                    // A bare white glyph can drop below 3:1 on bright thumbnails; the scrim
                    // keeps the HD affordance readable on any cover image.
                    Surface(
                        color = AuroraImmersiveBackground.copy(alpha = 0.55f),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                    ) {
                        Icon(
                            Icons.Default.Hd,
                            contentDescription = "高清直播",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Text(
                text = "$flag${item.username}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 12.dp),
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    text = "${formatViewerCount(item.viewersCount)} 人观看",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LiveError(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 96.dp, horizontal = 24.dp),
    ) {
        Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(42.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun LivePlayerScreen(
    item: LiveItem,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var comments by remember(item.id) { mutableStateOf<List<LiveComment>>(emptyList()) }
    var hasCheckedComments by remember(item.id) { mutableStateOf(false) }
    val cachedSession = remember(item.playerUrl) { LiveSessionCache.take(item.playerUrl) }
    if (cachedSession == null) {
        LaunchedEffect(item.playerUrl) { LiveSessionCache.releaseIfNot(item.playerUrl) }
    }
    Column(modifier = Modifier.fillMaxSize().background(AuroraImmersiveBackground).testTag("live-player")) {
        if (!isFullscreen) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回直播列表",
                        tint = Color.White,
                    )
                }
                Text(
                    item.username,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                Text("Stripchat", color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
            }
        }
        EmbeddedLivePlayer(
            url = item.playerUrl,
            isFullscreen = isFullscreen,
            onFullscreenChange = onFullscreenChange,
            onCommentsChange = { comments = it },
            onCommentsLoadingChange = { hasCheckedComments = !it },
            modifier = if (isFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            posterUrl = item.thumbnailUrl,
            reused = cachedSession,
        )
        if (!isFullscreen) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            "直播中",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                    Text(
                        text = "${formatViewerCount(item.viewersCount)} 人观看",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Text(
                    text = item.username,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "${countryFlag(item.countryCode)}公开直播 · Stripchat${if (item.isHd) " · HD" else ""}",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "播放控制位于画面底部，横屏或点击全屏可沉浸观看。",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            LiveComments(
                comments = comments,
                isLoading = !hasCheckedComments,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

@Composable
private fun LiveComments(
    comments: List<LiveComment>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(AuroraImmersiveBackground).padding(horizontal = 20.dp).testTag("live-comments")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Text(
                text = "实时评论",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.weight(1f))
            Text("公开聊天室 · 只读", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
        }
        if (comments.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    if (isLoading) "正在获取公开评论…" else "当前暂无公开评论",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(comments.asReversed(), key = LiveComment::id) { comment ->
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(comment.username.take(1).uppercase(), fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(
                                comment.username,
                                // 评论固定在黑色背景上，theme primary 在浅色/动态浅色下对比度不足，改用固定高对比强调色。
                                color = AuroraCoral,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(comment.message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbeddedLivePlayer(
    url: String,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    onCommentsChange: (List<LiveComment>) -> Unit,
    onCommentsLoadingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    posterUrl: String? = null,
    reused: CachedLiveSession? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var isLoading by remember(url) { mutableStateOf(reused?.ready != true) }
    var isPlayerReady by remember(url) { mutableStateOf(reused?.ready == true) }
    var isPaused by remember(url) { mutableStateOf(false) }
    var isMuted by remember(url) { mutableStateOf(false) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var pageLoadToken by remember(url) { mutableStateOf(0) }
    val webView = remember(url) {
        (reused?.webView ?: WebView(context)).apply webView@{
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportMultipleWindows(false)
            }
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(this@webView, true)
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, location: String?, favicon: Bitmap?) {
                    pageLoadToken += 1
                    isLoading = true
                    isPlayerReady = false
                    error = null
                    onCommentsChange(emptyList())
                    onCommentsLoadingChange(true)
                }

                override fun onPageFinished(view: WebView?, location: String?) {
                    isLoading = false
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    failure: WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) {
                        isLoading = false
                        error = "直播播放器加载失败，请检查网络后重试"
                        onCommentsLoadingChange(false)
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                    request?.url?.toString()?.let { !isAllowedLiveUrl(it) } ?: true
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view == null || fullscreenView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }
                    fullscreenView = view
                    fullscreenCallback = callback
                    onFullscreenChange(true)
                }

                override fun onHideCustomView() {
                    fullscreenView = null
                    fullscreenCallback?.onCustomViewHidden()
                    fullscreenCallback = null
                    onFullscreenChange(false)
                }
            }
            if (reused == null) {
                loadUrl(url)
            } else {
                settings.mediaPlaybackRequiresUserGesture = false
            }
        }
    }

    // 每次页面开始加载（含重试 reload）都会递增 token，重新开始探测。
    LaunchedEffect(webView, url, lifecycle, pageLoadToken) {
        val playerModeScript = livePlayerModeScript(url)
        var missingAttempts = 0
        var discoveryFinished = false
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (discoveryFinished || error != null) return@repeatOnLifecycle
            while (!discoveryFinished) {
                delay(PLAYER_DISCOVERY_INTERVAL_MS)
                if (error != null) break
                webView.evaluateJavascript(playerModeScript) { result ->
                    when (result) {
                        "\"ready\"" -> {
                            missingAttempts = 0
                            isPlayerReady = true
                            isLoading = false
                            discoveryFinished = true
                        }

                        "\"mismatch\"" -> {
                            isLoading = false
                            error = "主播状态已变化，请返回列表刷新后重试"
                            onCommentsLoadingChange(false)
                            discoveryFinished = true
                        }

                        else -> {
                            missingAttempts += 1
                            if (missingAttempts >= PLAYER_DISCOVERY_ATTEMPTS && !isPlayerReady) {
                                isLoading = false
                                error = "主播已下播或播放器暂时不可用，请返回列表重试"
                                onCommentsLoadingChange(false)
                                discoveryFinished = true
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(webView, url, isPlayerReady, lifecycle) {
        if (!isPlayerReady) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                webView.evaluateJavascript(LIVE_COMMENTS_SCRIPT) { result ->
                    val updatedComments = parseLiveComments(result)
                    onCommentsChange(updatedComments)
                    onCommentsLoadingChange(false)
                }
                delay(COMMENTS_REFRESH_INTERVAL_MS)
            }
        }
    }

    fun exitFullscreen() {
        fullscreenView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        onFullscreenChange(false)
    }

    fun setPlaybackPaused(shouldPause: Boolean) {
        val command = if (shouldPause) {
            "window.__auroraPlaybackPaused = true; document.querySelector('video')?.pause();"
        } else {
            "window.__auroraPlaybackPaused = false; document.querySelector('video')?.play();"
        }
        webView.evaluateJavascript(command, null)
        isPaused = shouldPause
    }

    fun setPlaybackMuted(shouldMute: Boolean) {
        webView.evaluateJavascript(
            "const video = document.querySelector('video'); if (video) video.muted = $shouldMute;",
            null,
        )
        isMuted = shouldMute
    }

    BackHandler(enabled = fullscreenView != null) { exitFullscreen() }
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
            fullscreenCallback?.onCustomViewHidden()
            onFullscreenChange(false)
            (webView.parent as? ViewGroup)?.removeView(webView)
            if (isPlayerReady && error == null) {
                // Pause + mute and keep it for an instant re-entry; released when leaving the tab.
                LiveSessionCache.retain(webView, url, isPlayerReady)
            } else {
                webView.stopLoading()
                webView.destroy()
            }
        }
    }

    LaunchedEffect(webView, isPlayerReady) {
        if (isPlayerReady && !isMuted) {
            webView.evaluateJavascript(
                "document.querySelectorAll('video').forEach(v => { v.muted = false; v.play().catch(()=>{}); });",
                null,
            )
        }
    }

    Box(modifier.background(AuroraImmersiveBackground)) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        if (isLoading || !isPlayerReady) {
            Box(Modifier.fillMaxSize().background(AuroraImmersiveBackground)) {
                posterUrl?.let { poster ->
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(poster).memoryCacheKey(poster).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(Modifier.fillMaxSize().background(AuroraImmersiveBackground.copy(alpha = 0.55f)))
                }
                CircularProgressIndicator(color = AuroraCoral, modifier = Modifier.align(Alignment.Center))
            }
        }
        error?.let { message ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            ) {
                Text(message, color = Color.White)
                TextButton(
                    onClick = { webView.reload() },
                    // 错误浮层压在固定黑色播放器背景上，沿用固定高对比强调色而不是浅色主题的深色 primary。
                    colors = ButtonDefaults.textButtonColors(contentColor = AuroraCoral),
                ) { Text("重试") }
            }
        }
        fullscreenView?.let { view ->
            AndroidView(factory = { view }, modifier = Modifier.fillMaxSize().background(AuroraImmersiveBackground))
        }
        if (isPlayerReady && error == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(AuroraImmersiveBackground.copy(alpha = 0.62f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = { setPlaybackPaused(!isPaused) }) {
                    Icon(
                        if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "继续播放" else "暂停直播",
                        tint = Color.White,
                    )
                }
                IconButton(onClick = { setPlaybackMuted(!isMuted) }) {
                    Icon(
                        if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "开启声音" else "静音",
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onFullscreenChange(!isFullscreen) }) {
                    Icon(
                        if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "退出全屏" else "全屏播放",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

internal fun isAllowedLiveUrl(url: String): Boolean = runCatching {
    val uri = java.net.URI(url)
    val host = uri.host?.lowercase() ?: return@runCatching false
    uri.scheme.equals("https", ignoreCase = true) &&
        (host == "stripchat.com" || host.endsWith(".stripchat.com"))
}.getOrDefault(false)

internal fun formatViewerCount(value: Int): String = when {
    value >= 10_000 -> String.format(java.util.Locale.US, "%.1f万", value / 10_000f)
    value >= 1_000 -> String.format(java.util.Locale.US, "%.1f千", value / 1_000f)
    else -> value.coerceAtLeast(0).toString()
}

internal fun parseLiveComments(value: String): List<LiveComment> = runCatching {
    val messages = JSONArray(value)
    buildList {
        for (index in 0 until messages.length()) {
            val message = messages.optJSONObject(index) ?: continue
            val id = message.optString("id").take(64)
            val username = message.optString("username").trim().take(48)
            val text = message.optString("message").trim().take(300)
            if (id.isNotBlank() && username.isNotBlank() && text.isNotBlank()) {
                add(LiveComment(id = id, username = username, message = text))
            }
        }
    }.asReversed()
        .distinctBy(LiveComment::id)
        .distinctBy { it.username to it.message }
        .asReversed()
        .takeLast(MAX_VISIBLE_COMMENTS)
}.getOrDefault(emptyList())

private fun countryFlag(code: String): String {
    if (code.length != 2 || code.any { !it.isLetter() }) return ""
    return code.uppercase().map { character ->
        String(Character.toChars(character.code - 'A'.code + 0x1F1E6))
}.joinToString("") + " "
}

private const val LIVE_COVER_PREFETCH_LIMIT = 8
private const val PLAYER_DISCOVERY_ATTEMPTS = 40
private const val PLAYER_DISCOVERY_INTERVAL_MS = 500L
private const val COMMENTS_REFRESH_INTERVAL_MS = 1_500L
private const val MAX_VISIBLE_COMMENTS = 40

private const val LIVE_COMMENTS_SCRIPT = """
(() => Array.from(document.querySelectorAll('.message.message-base'))
  .filter(node => !String(node.className).includes('WelcomeBotMessage'))
  .slice(-60).map((node, index) => {
  const usernameNode = node.querySelector('.message-username .user-levels-username-text') ||
    node.querySelector('.message-username');
  const username = (usernameNode?.textContent || '').replace(/\s+/g, ' ').trim();
  const content = node.cloneNode(true);
  content.querySelectorAll('.message-username, svg, script, style, [class*="showMoreBtn"]').forEach(element => element.remove());
  const message = (content.textContent || '').replace(/\s+/g, ' ').trim();
  return {
    id: node.getAttribute('data-message-id') || `${'$'}{index}-${'$'}{username}-${'$'}{message}`,
    username,
    message,
  };
}).filter(item => item.username && item.message))()
"""

/** Keep the source-owned video node and its stream while removing webpage chrome around it. */
private fun livePlayerModeScript(roomUrl: String): String {
    val expectedUsername = java.net.URI(roomUrl).path.substringAfterLast('/').lowercase()
    return """
(() => {
  const expectedUsername = '$expectedUsername';
  const currentUsername = decodeURIComponent(location.pathname.split('/').filter(Boolean).pop() || '').toLowerCase();
  if (currentUsername !== expectedUsername) return 'mismatch';
  const video = document.querySelector('video');
  if (!video) return 'missing';

  const styleId = 'aurora-live-player-style';
  let style = document.getElementById(styleId);
  if (!style) {
    style = document.createElement('style');
    style.id = styleId;
    style.textContent = `
      html, body {
        width: 100% !important;
        height: 100% !important;
        margin: 0 !important;
        overflow: hidden !important;
        background: #000 !important;
      }
      body * { visibility: hidden !important; }
    `;
    document.head.appendChild(style);
  }

  const applyPlayerStage = () => {
    const activeVideo = document.querySelector('video');
    if (!activeVideo) return false;
    const player = activeVideo.closest('.player-wrapper') || activeVideo.parentElement;
    if (!player) return false;
    const viewportWidth = window.visualViewport?.width || window.innerWidth;
    const viewportHeight = window.visualViewport?.height || window.innerHeight;
    const stageWidth = `${'$'}{viewportWidth}px`;
    const stageHeight = `${'$'}{viewportHeight}px`;

    let current = activeVideo;
    while (current) {
      current.style.setProperty('visibility', 'visible', 'important');
      if (current === player) break;
      current.style.setProperty('width', stageWidth, 'important');
      current.style.setProperty('height', stageHeight, 'important');
      current.style.setProperty('inline-size', stageWidth, 'important');
      current.style.setProperty('block-size', stageHeight, 'important');
      current.style.setProperty('min-width', '0', 'important');
      current.style.setProperty('min-height', '0', 'important');
      current.style.setProperty('max-width', 'none', 'important');
      current.style.setProperty('max-height', 'none', 'important');
      current.style.setProperty('aspect-ratio', 'auto', 'important');
      current = current.parentElement;
    }
    current = player.parentElement;
    while (current) {
      current.style.setProperty('visibility', 'visible', 'important');
      current = current.parentElement;
    }
    player.style.setProperty('position', 'fixed', 'important');
    player.style.setProperty('inset', '0', 'important');
    player.style.setProperty('z-index', '2147483000', 'important');
    player.style.setProperty('width', stageWidth, 'important');
    player.style.setProperty('height', stageHeight, 'important');
    player.style.setProperty('inline-size', stageWidth, 'important');
    player.style.setProperty('block-size', stageHeight, 'important');
    player.style.setProperty('min-width', '0', 'important');
    player.style.setProperty('min-height', '0', 'important');
    player.style.setProperty('max-width', 'none', 'important');
    player.style.setProperty('max-height', 'none', 'important');
    player.style.setProperty('aspect-ratio', 'auto', 'important');
    player.style.setProperty('margin', '0', 'important');
    player.style.setProperty('border-radius', '0', 'important');
    player.style.setProperty('background', '#000', 'important');

    activeVideo.controls = false;
    activeVideo.playsInline = true;
    activeVideo.style.setProperty('visibility', 'visible', 'important');
    activeVideo.style.setProperty('width', stageWidth, 'important');
    activeVideo.style.setProperty('height', stageHeight, 'important');
    activeVideo.style.setProperty('object-fit', 'contain', 'important');
    activeVideo.style.setProperty('background', '#000', 'important');
    if (!activeVideo.__auroraPauseGuard) {
      activeVideo.__auroraPauseGuard = true;
      activeVideo.addEventListener('play', () => {
        if (window.__auroraPlaybackPaused) activeVideo.pause();
      });
    }
    if (window.__auroraPlaybackPaused) {
      activeVideo.pause();
    } else {
      activeVideo.play().catch(() => {});
    }
    return true;
  };

  if (!window.__auroraLivePlayerObserver) {
    let pending = false;
    window.__auroraLivePlayerObserver = new MutationObserver(() => {
      if (pending) return;
      pending = true;
      requestAnimationFrame(() => {
        pending = false;
        applyPlayerStage();
      });
    });
    window.__auroraLivePlayerObserver.observe(document.body, { childList: true, subtree: true });
    window.addEventListener('resize', applyPlayerStage);
    window.visualViewport?.addEventListener('resize', applyPlayerStage);
  }
  return applyPlayerStage() ? 'ready' : 'missing';
})()
"""
}
