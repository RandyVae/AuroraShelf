package com.aurorashelf.app.ui

import android.view.ViewGroup
import android.webkit.WebView

/**
 * Stateless lifecycle cleaner for embedded live WebViews.
 *
 * Deliberately avoids holding any static [WebView] or [android.content.Context] references across
 * compositions so Activity memory and background WebView audio can never leak (`StaticFieldLeak` / `PLY-06`).
 */
internal object LiveSessionCache {
    private const val PAUSE_AND_MUTE =
        "try{window.__auroraPlaybackPaused=true;document.querySelectorAll('video,audio').forEach(function(m){m.muted=true;m.pause();});}catch(e){}"

    private const val DESTROY_MEDIA =
        "try{window.__auroraPlaybackPaused=true;document.querySelectorAll('video,audio').forEach(function(m){m.muted=true;m.pause();m.removeAttribute('src');m.load();});}catch(e){}"

    fun pauseAndMute(webView: WebView?) {
        val view = webView ?: return
        runCatching {
            view.evaluateJavascript(PAUSE_AND_MUTE, null)
            view.onPause()
        }
    }

    fun resumeView(webView: WebView?, shouldPlay: Boolean, shouldMute: Boolean) {
        val view = webView ?: return
        runCatching {
            view.onResume()
            val script = if (shouldPlay) {
                "try{window.__auroraPlaybackPaused=false;document.querySelectorAll('video,audio').forEach(function(m){m.muted=$shouldMute;m.play().catch(function(){});});}catch(e){}"
            } else {
                "try{document.querySelectorAll('video,audio').forEach(function(m){m.muted=$shouldMute;});}catch(e){}"
            }
            view.evaluateJavascript(script, null)
        }
    }

    fun destroyView(webView: WebView?) {
        val view = webView ?: return
        runCatching {
            view.evaluateJavascript(DESTROY_MEDIA, null)
            view.onPause()
            view.stopLoading()
            view.loadUrl("about:blank")
            (view.parent as? ViewGroup)?.removeView(view)
            view.removeAllViews()
            view.destroy()
        }
    }

    fun release() {
        // No-op: static WebView retention has been eliminated to prevent Context and audio leaks.
    }
}
