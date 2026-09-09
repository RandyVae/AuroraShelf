package com.aurorashelf.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aurorashelf.app.model.ForumPost
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
internal fun ForumScreen(
    state: ForumUiState,
    bottomPadding: Dp,
    onEnsureLoaded: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onPost: (ForumPost) -> Unit,
    onClosePost: () -> Unit,
) {
    state.selectedPost?.let { post ->
        ForumReader(post = post, onClose = onClosePost)
        return
    }

    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { onEnsureLoaded() }
    LaunchedEffect(listState, state.posts.size, state.canLoadMore) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.posts.isNotEmpty() && lastVisible >= state.posts.lastIndex - 4
        }.distinctUntilChanged().filter { it }.collect { onLoadMore() }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = bottomPadding + 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding().testTag("forum-feed"),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("论坛", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "两个社区 · 合并更新",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRefresh, enabled = !state.isLoading, modifier = Modifier.testTag("forum-refresh")) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新论坛")
                }
            }
        }

        if (state.unavailableSources.isNotEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = RoundedCornerShape(18.dp),
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
        } else {
            items(state.posts, key = { "${it.sourceId}:${it.id}" }) { post ->
                ForumPostCard(post = post, onClick = { onPost(post) })
            }
        }

        if (state.isLoadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        } else if (state.error != null && state.posts.isNotEmpty()) {
            item {
                TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                    Text("加载失败，点击重试")
                }
            }
        }
    }
}

@Composable
private fun ForumPostCard(post: ForumPost, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag("forum-post"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
            Text(
                post.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = CircleShape,
                ) {
                    Text(post.sourceName, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                }
                Text(
                    post.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                Text(post.replyCount.toString(), style = MaterialTheme.typography.labelMedium)
                if (post.updatedLabel.isNotBlank()) {
                    Text(post.updatedLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var isLoading by remember(post.url) { mutableStateOf(true) }
    var error by remember(post.url) { mutableStateOf<String?>(null) }
    var canGoBack by remember(post.url) { mutableStateOf(false) }
    val webView = remember(post.url) {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = false
                domStorageEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                builtInZoomControls = true
                displayZoomControls = false
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    isLoading = true
                    error = null
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    isLoading = false
                    canGoBack = view?.canGoBack() == true
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, failure: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        isLoading = false
                        error = "帖子加载失败，请检查网络后重试"
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                    request?.url?.scheme !in setOf("https", "http")
            }
            loadUrl(post.url)
        }
    }
    BackHandler {
        if (canGoBack && webView.canGoBack()) webView.goBack() else onClose()
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().testTag("forum-reader")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回论坛")
            }
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(post.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(post.sourceName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { webView.reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新帖子")
            }
        }
        HorizontalDivider()
        Box(Modifier.fillMaxSize()) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            if (isLoading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            error?.let { message ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.Center).background(MaterialTheme.colorScheme.background).padding(24.dp),
                ) {
                    Text(message)
                    TextButton(onClick = { webView.reload() }) { Text("重新加载") }
                }
            }
        }
    }
}
