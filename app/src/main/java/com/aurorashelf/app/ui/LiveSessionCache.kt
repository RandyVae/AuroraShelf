package com.aurorashelf.app.ui

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class CachedLiveSession(
    val webView: WebView,
    val ready: Boolean,
)

/**
 * Keeps the WebView of the last opened live room alive (paused and muted) when the user returns
 * to the grid, so re-entering the same room is instant instead of reloading the whole site.
 *
 * This deliberately only reuses rooms the user has already opened; the app never pre-loads a room
 * or touches the site's age/consent flow on the user's behalf.
 */
internal object LiveSessionCache {
    /** Observable so the grid can host the retained view in a 1 dp window. */
    var retained by mutableStateOf<WebView?>(null)
        private set

    private var retainedUrl: String? = null
    private var retainedReady = false

    fun retain(webView: WebView, url: String, ready: Boolean) {
        release()
        (webView.parent as? ViewGroup)?.removeView(webView)
        runCatching {
            webView.evaluateJavascript(PAUSE_AND_MUTE, null)
        }
        retained = webView
        retainedUrl = url
        retainedReady = ready
    }

    /** Takes the retained view when it belongs to [expectedUrl]; null means a fresh load is needed. */
    fun take(expectedUrl: String): CachedLiveSession? {
        val view = retained ?: return null
        if (retainedUrl != expectedUrl) return null
        retained = null
        retainedUrl = null
        (view.parent as? ViewGroup)?.removeView(view)
        return CachedLiveSession(view, retainedReady)
    }

    fun releaseIfNot(url: String) {
        if (retainedUrl != url) release()
    }

    fun release() {
        retained?.let { view ->
            runCatching { view.evaluateJavascript(PAUSE_AND_MUTE, null) }
            (view.parent as? ViewGroup)?.removeView(view)
            view.stopLoading()
            view.destroy()
        }
        retained = null
        retainedUrl = null
        retainedReady = false
    }

    private const val PAUSE_AND_MUTE =
        "document.querySelectorAll('video').forEach(v => { v.muted = true; v.pause(); });"
}
