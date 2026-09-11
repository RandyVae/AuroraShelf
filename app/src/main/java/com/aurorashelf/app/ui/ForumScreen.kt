package com.aurorashelf.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aurorashelf.app.model.ForumPost
import kotlinx.coroutines.delay
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
    // Keep the list state in composition while the reader is open.
    val listState = rememberLazyListState()
    state.selectedPost?.let { post ->
        ForumReader(post = post, onClose = onClosePost)
        return
    }

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
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var isLoading by remember(post.url) { mutableStateOf(true) }
    var error by remember(post.url) { mutableStateOf<String?>(null) }
    var canGoBack by remember(post.url) { mutableStateOf(false) }
    var loadGeneration by remember(post.url) { mutableStateOf(0) }
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
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    canGoBack = view?.canGoBack() == true
                    val finishedGeneration = loadGeneration
                    view?.applyMobileForumLayout(isDarkTheme) { wasApplied ->
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
            loadForumPost(post.url)
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
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.align(Alignment.Center).testTag("forum-reader-loading"),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                        Text("正在整理帖子排版", style = MaterialTheme.typography.titleSmall)
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
                    Text(message)
                    TextButton(onClick = { webView.reload() }) { Text("重新加载") }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.applyMobileForumLayout(isDarkTheme: Boolean, onComplete: (Boolean) -> Unit) {
    settings.javaScriptEnabled = true
    evaluateJavascript(mobileForumScript(isDarkTheme)) { result ->
        settings.javaScriptEnabled = false
        onComplete(result == "true")
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
      if (host.includes('t906.') || host.includes('91selfie')) {
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
