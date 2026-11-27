package com.aurorashelf.app.game

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aurorashelf.app.data.TelemetryManager
import kotlin.math.max

/**
 * Immersive standalone Activity for Lust Goddess Tactical RPG.
 * Uses Android 17 edge-to-edge APIs, responsive multi-window orientation support,
 * predictive back preview scaling, hardware-accelerated WebGL/Spine rendering,
 * and durable offline save persistence.
 */
class GameActivity : ComponentActivity() {
    private var webView: WebView? = null
    private var assetServer: GameAssetServer? = null
    private var saveBridge: GameSaveBridge? = null
    private var exitCapsule: View? = null
    private var rootContainer: FrameLayout? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoFadeRunnable = Runnable {
        exitCapsule?.animate()?.alpha(0.44f)?.setDuration(320)?.start()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 17 large-screen compliant: never force-lock orientation.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WebView.setWebContentsDebuggingEnabled((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)

        val wv = WebView(this).also { webView = it }
        wv.setBackgroundColor(Color.rgb(7, 9, 15))

        var activeAssetPort = -1
        var url = "file:///android_asset/www/index.html"
        try {
            val server = GameAssetServer(applicationContext).also { assetServer = it }
            val port = server.start()
            activeAssetPort = port
            url = server.buildEntryUrl()
        } catch (_: Exception) {
            assetServer = null
        }

        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        val usingLocalHttp = activeAssetPort > 0
        s.allowFileAccess = !usingLocalHttp
        s.allowContentAccess = !usingLocalHttp
        s.loadsImagesAutomatically = true
        s.mediaPlaybackRequiresUserGesture = false
        s.cacheMode = WebSettings.LOAD_NO_CACHE
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.displayZoomControls = false
        val fontScale = resources.configuration.fontScale
        s.textZoom = (fontScale * 100).toInt().coerceIn(100, 150)
        wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        wv.isVerticalScrollBarEnabled = false
        wv.isHorizontalScrollBarEnabled = false
        wv.defaultFocusHighlightEnabled = false
        wv.overScrollMode = View.OVER_SCROLL_NEVER

        val bridge = GameSaveBridge(applicationContext).also { saveBridge = it }
        wv.addJavascriptInterface(bridge, "AndroidSave")
        wv.addJavascriptInterface(GameNativeBridge(this), "AndroidNative")
        wv.webViewClient = object : android.webkit.WebViewClient() {
            private fun isAllowedGameTarget(target: android.net.Uri): Boolean {
                val scheme = target.scheme?.lowercase() ?: return false
                if (scheme == "data" || scheme == "blob") return true
                return if (usingLocalHttp) {
                    scheme == "http" && target.host == "127.0.0.1" && target.port == activeAssetPort
                } else {
                    if (scheme != "file") return false
                    val rawPath = target.path ?: return false
                    val canonical = runCatching { java.io.File(rawPath).canonicalPath }.getOrNull() ?: return false
                    canonical.startsWith("/android_asset/www/") && !rawPath.contains("..")
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
            ): Boolean {
                val target = request?.url ?: return true
                return !isAllowedGameTarget(target)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
            ): android.webkit.WebResourceResponse? {
                val target = request?.url ?: return blockedResponse()
                if (!isAllowedGameTarget(target)) {
                    return blockedResponse()
                }
                return super.shouldInterceptRequest(view, request)
            }

            private fun blockedResponse(): android.webkit.WebResourceResponse =
                android.webkit.WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    403,
                    "Forbidden",
                    emptyMap(),
                    java.io.ByteArrayInputStream(ByteArray(0)),
                )
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                val tag = "NetgreyGameWeb"
                val msg = "[${m.messageLevel()}] ${m.message()} (${m.sourceId()}:${m.lineNumber()})"
                when (m.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> Log.e(tag, msg)
                    ConsoleMessage.MessageLevel.WARNING -> Log.w(tag, msg)
                    else -> Log.d(tag, msg)
                }
                return true
            }
        }

        wv.isFocusable = true
        wv.isFocusableInTouchMode = true
        wv.requestFocus()

        val rootLayout = FrameLayout(this).also { rootContainer = it }
        rootLayout.setBackgroundColor(Color.rgb(7, 9, 15))
        rootLayout.addView(
            wv,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val capsule = buildFloatingExitCapsule().also { exitCapsule = it }
        val capsuleLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = dpToPx(38)
            marginEnd = dpToPx(16)
        }
        rootLayout.addView(capsule, capsuleLp)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, windowInsets ->
            val topInset = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout(),
            ).top
            val lp = capsule.layoutParams as? FrameLayout.LayoutParams
            if (lp != null) {
                val targetTop = max(dpToPx(12), topInset + dpToPx(8))
                if (lp.topMargin != targetTop) {
                    lp.topMargin = targetTop
                    capsule.layoutParams = lp
                }
            }
            windowInsets
        }

        setContentView(rootLayout)
        ViewCompat.requestApplyInsets(rootLayout)
        hideSystemBars()
        scheduleAutoFade()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                val p = backEvent.progress.coerceIn(0f, 1f)
                val scale = 1f - 0.08f * p
                val shift = dpToPx(24) * p * if (backEvent.swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                rootContainer?.apply {
                    scaleX = scale
                    scaleY = scale
                    translationX = shift
                }
            }

            override fun handleOnBackCancelled() {
                rootContainer?.animate()
                    ?.scaleX(1f)
                    ?.scaleY(1f)
                    ?.translationX(0f)
                    ?.setDuration(180)
                    ?.start()
            }

            override fun handleOnBackPressed() {
                rootContainer?.apply {
                    scaleX = 1f
                    scaleY = 1f
                    translationX = 0f
                }
                val current = webView
                if (current != null) {
                    current.evaluateJavascript("window.handleAppBack ? window.handleAppBack() : false") { value ->
                        if (value != "true") {
                            isEnabled = false
                            finish()
                        }
                    }
                } else {
                    finish()
                }
            }
        })

        wv.loadUrl(url)
    }

    private var isMuted = false

    private fun buildFloatingExitCapsule(): View {
        val capsule = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            elevation = dpToPx(6).toFloat()

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(24).toFloat()
                setColor(Color.argb(232, 25, 31, 44))
                setStroke(dpToPx(1), Color.argb(72, 184, 192, 206))
            }
            background = bg
        }

        val menuBtn = TextView(this).apply {
            text = "···"
            contentDescription = "游戏功能菜单"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48))
            isClickable = true
            isFocusable = true
            setOnClickListener { v ->
                v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                showMiniProgramMenu(v)
            }
        }
        capsule.addView(menuBtn)

        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(1), dpToPx(18))
            setBackgroundColor(Color.argb(72, 184, 192, 206))
        }
        capsule.addView(divider)

        val closeBtn = TextView(this).apply {
            text = "✕"
            contentDescription = "退出游戏"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48))
            isClickable = true
            isFocusable = true
            setOnClickListener { v ->
                v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                finish()
            }
        }
        capsule.addView(closeBtn)

        return capsule
    }

    private fun showMiniProgramMenu(anchor: View) {
        val popup = android.widget.PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, if (isMuted) "开启音效" else "静音")
        popup.menu.add(0, 2, 1, "重新加载")
        popup.menu.add(0, 3, 2, "退出游戏")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    isMuted = !isMuted
                    webView?.evaluateJavascript("if (window.setAppMuted) { window.setAppMuted($isMuted); }", null)
                    true
                }
                2 -> {
                    webView?.reload()
                    true
                }
                3 -> {
                    finish()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun scheduleAutoFade() {
        handler.removeCallbacks(autoFadeRunnable)
        handler.postDelayed(autoFadeRunnable, 3000L)
    }

    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics,
        ).toInt()

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        TelemetryManager.leaveGameSection()
        webView?.evaluateJavascript("window.APP && APP.pauseGame && APP.pauseGame()", null)
        webView?.onPause()
        saveBridge?.flushSync()
    }

    override fun onResume() {
        super.onResume()
        TelemetryManager.enterGameSection()
        webView?.onResume()
        webView?.evaluateJavascript("window.APP && APP.resumeGame && APP.resumeGame()", null)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView?.evaluateJavascript("window.APP && APP.destroyGame && APP.destroyGame()", null)
        webView?.let { wv ->
            wv.stopLoading()
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.removeAllViews()
            wv.destroy()
        }
        webView = null
        saveBridge?.flushSync()
        saveBridge = null
        assetServer?.stop()
        assetServer = null
        exitCapsule = null
        rootContainer = null
        super.onDestroy()
    }
}
