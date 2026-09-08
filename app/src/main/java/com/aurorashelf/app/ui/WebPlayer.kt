package com.aurorashelf.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aurorashelf.app.ui.theme.AuroraCoral

/** Site-owned playback with HTTPS validation and proper media/view lifecycle. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun SitePlayer(url: String, onFullscreenChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var isLoading by remember(url) { mutableStateOf(true) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var exitFullscreen by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    val fullscreenChanged by rememberUpdatedState(onFullscreenChange)
    val webView = remember(url) {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportMultipleWindows(false)
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, location: String?, favicon: Bitmap?) {
                    isLoading = true
                    error = null
                }
                override fun onPageFinished(view: WebView?, location: String?) { isLoading = false }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, failure: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        isLoading = false
                        error = "页面加载失败，请检查连接后重试"
                    }
                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                    request?.url?.scheme !in setOf("https", "http")
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (fullscreenView != null) { callback?.onCustomViewHidden(); return }
                    fullscreenView = view
                    exitFullscreen = callback
                    fullscreenChanged(view != null)
                }
                override fun onHideCustomView() {
                    fullscreenView = null
                    exitFullscreen?.onCustomViewHidden()
                    exitFullscreen = null
                    fullscreenChanged(false)
                }
            }
            loadUrl(url)
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
            exitFullscreen?.onCustomViewHidden()
            fullscreenChanged(false)
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }
    }
    BackHandler(enabled = fullscreenView != null || webView.canGoBack()) {
        if (fullscreenView != null) {
            fullscreenView = null
            exitFullscreen?.onCustomViewHidden()
            exitFullscreen = null
            fullscreenChanged(false)
        } else webView.goBack()
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize().then(
                if (fullscreenView == null) Modifier.statusBarsPadding().padding(top = 82.dp) else Modifier,
            ),
        )
        if (isLoading) CircularProgressIndicator(color = AuroraCoral, modifier = Modifier.align(Alignment.Center))
        error?.let { message ->
            Column(Modifier.align(Alignment.Center).background(Color.Black).padding(24.dp)) {
                Text(message, color = Color.White)
                TextButton(onClick = { webView.reload() }) { Text("重新加载") }
            }
        }
        fullscreenView?.let { view ->
            AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        }
    }
}
