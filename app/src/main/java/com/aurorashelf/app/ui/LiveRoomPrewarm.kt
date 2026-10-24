package com.aurorashelf.app.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import com.aurorashelf.app.model.LiveItem

/**
 * Pre-loads the most likely live room in a hidden WebView while the live grid is visible, and
 * keeps it playing muted so the video/WebRTC pipeline is already warm.
 *
 * Tapping that card hands the ready WebView to the player screen (no page reload, no JS boot),
 * which is the only way to make the Stripchat live player effectively instant: the public HLS
 * endpoint is a short VOD snapshot (ENDLIST) and cannot be used for live playback.
 *
 * Only one room is kept warm; it is destroyed when leaving the live tab or when a different
 * room is opened. The single static WebView is therefore intentional and released explicitly
 * (hence the StaticFieldLeak suppression).
 */
@SuppressLint("SetJavaScriptEnabled", "StaticFieldLeak")
internal object LiveRoomPrewarm {
    private val handler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var warmedUrl: String? = null
    private val idlePauseTask = Runnable {
        // Keep the page warm for instant re-entry, but stop consuming live bandwidth when the
        // user just browses the grid for a long time.
        webView?.let { view ->
            runCatching { view.evaluateJavascript(PAUSE_SCRIPT, null) }
        }
    }

    private val muteTask = object : Runnable {
        override fun run() {
            webView?.let { view -> runCatching { view.evaluateJavascript(MUTE_SCRIPT, null) } }
            if (webView != null) handler.postDelayed(this, MUTE_INTERVAL_MS)
        }
    }

    /** Starts (or reuses) the hidden warm page for [item]. Must run on the main thread. */
    fun ensure(context: android.content.Context, item: LiveItem): WebView {
        val existing = webView
        if (existing != null && warmedUrl == item.playerUrl) return existing
        release()
        val view = WebView(context).apply {
            setBackgroundColor(Color.BLACK)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // Muted autoplay keeps the room's video pipeline warm; the mute loop below keeps it
                // silent until the player screen takes over.
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportMultipleWindows(false)
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
        webView = view
        warmedUrl = item.playerUrl
        view.loadUrl(item.playerUrl)
        handler.post(muteTask)
        handler.postDelayed(idlePauseTask, IDLE_PAUSE_MS)
        return view
    }

    /** Current warm view for the hidden host (nullable so Compose can key on it). */
    fun current(): WebView? = webView

    /**
     * Takes ownership of the ready WebView if it belongs to [targetUrl]; the caller must attach it
     * to the player surface. Returns null when another room is warm.
     */
    fun take(targetUrl: String): WebView? {
        if (warmedUrl != targetUrl) return null
        val taken = webView ?: return null
        handler.removeCallbacks(muteTask)
        handler.removeCallbacks(idlePauseTask)
        (taken.parent as? ViewGroup)?.removeView(taken)
        webView = null
        warmedUrl = null
        taken.settings.mediaPlaybackRequiresUserGesture = false
        runCatching {
            taken.evaluateJavascript(
                "document.querySelectorAll('video').forEach(v => { v.muted = false; v.play().catch(()=>{}); });",
                null,
            )
        }
        return taken
    }

    /** Destroys any warm page (leaving the live tab / opening an unwarmed room). */
    fun release() {
        handler.removeCallbacks(muteTask)
        handler.removeCallbacks(idlePauseTask)
        webView?.let { view ->
            runCatching { view.evaluateJavascript("document.querySelectorAll('video').forEach(v => v.pause());", null) }
            (view.parent as? ViewGroup)?.removeView(view)
            view.stopLoading()
            view.destroy()
        }
        webView = null
        warmedUrl = null
    }

    private const val MUTE_INTERVAL_MS = 120L
    private const val IDLE_PAUSE_MS = 120_000L
    private const val PAUSE_SCRIPT =
        "document.querySelectorAll('video').forEach(v => { v.muted = true; v.pause(); });"
    private const val MUTE_SCRIPT =
        "document.querySelectorAll('video').forEach(v => { if (!v.muted) v.muted = true; });"
}
