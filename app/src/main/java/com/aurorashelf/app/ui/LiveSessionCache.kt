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
    private var preparedUrl: String? = null
    private var preparedSession: CachedLiveSession? = null

    fun retain(webView: WebView, url: String, ready: Boolean) {
        release()
        (webView.parent as? ViewGroup)?.removeView(webView)
        runCatching {
            webView.evaluateJavascript(PAUSE_AND_MUTE, null)
            webView.onPause()
        }
        retained = webView
        retainedUrl = url
        retainedReady = ready
    }

    /** Moves any matching retained WebView out of snapshot state before entering player composition. */
    fun prepareFor(expectedUrl: String) {
        val view = retained
        if (view != null && retainedUrl == expectedUrl) {
            retained = null
            retainedUrl = null
            val ready = retainedReady
            retainedReady = false
            (view.parent as? ViewGroup)?.removeView(view)
            runCatching { view.onResume() }
            preparedUrl = expectedUrl
            preparedSession = CachedLiveSession(view, ready)
        } else {
            release()
        }
    }

    /** Pure non-snapshot read during composition; falls back only if already prepared. */
    fun consumePrepared(expectedUrl: String): CachedLiveSession? {
        val session = preparedSession ?: return null
        if (preparedUrl != expectedUrl) return null
        preparedSession = null
        preparedUrl = null
        return session
    }

    fun releaseIfNot(url: String) {
        if (retainedUrl != url) {
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
        if (preparedUrl != url) {
            preparedSession?.webView?.let { view ->
                runCatching { view.evaluateJavascript(PAUSE_AND_MUTE, null) }
                (view.parent as? ViewGroup)?.removeView(view)
                view.stopLoading()
                view.destroy()
            }
            preparedSession = null
            preparedUrl = null
        }
    }

    fun release() {
        releaseIfNot("")
    }

    private const val PAUSE_AND_MUTE =
        "document.querySelectorAll('video').forEach(v => { v.muted = true; v.pause(); });"
}
