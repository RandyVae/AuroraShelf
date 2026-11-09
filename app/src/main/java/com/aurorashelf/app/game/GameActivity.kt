package com.aurorashelf.app.game

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.abs
import kotlin.math.max

/**
 * Immersive standalone Activity for Lust Goddess Tactical RPG.
 * Follows the host window orientation with zero-distraction fullscreen cutout handling,
 * hardware-accelerated WebGL/Spine rendering, durable offline save persistence,
 * and an Apple HIG floating exit capsule to seamlessly return to AuroraShelf video feeds.
 */
class GameActivity : ComponentActivity() {
    private var webView: WebView? = null
    private var assetServer: GameAssetServer? = null
    private var exitCapsule: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoFadeRunnable = Runnable {
        exitCapsule?.animate()?.alpha(0.40f)?.setDuration(350)?.start()
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        val window = window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        WebView.setWebContentsDebuggingEnabled((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)

        val wv = WebView(this).also { webView = it }
        wv.setBackgroundColor(Color.rgb(7, 9, 15))

        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.loadsImagesAutomatically = true
        s.mediaPlaybackRequiresUserGesture = false
        s.cacheMode = WebSettings.LOAD_NO_CACHE
        val fontScale = resources.configuration.fontScale
        s.textZoom = (fontScale * 100).toInt().coerceIn(100, 150)
        wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        wv.isVerticalScrollBarEnabled = false
        wv.isHorizontalScrollBarEnabled = false
        if (Build.VERSION.SDK_INT >= 26) {
            wv.defaultFocusHighlightEnabled = false
        }
        wv.overScrollMode = View.OVER_SCROLL_NEVER

        wv.addJavascriptInterface(GameSaveBridge(applicationContext), "AndroidSave")
        wv.addJavascriptInterface(GameNativeBridge(this), "AndroidNative")
        wv.webViewClient = WebViewClient()
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

        var url = "file:///android_asset/www/index.html"
        try {
            val server = GameAssetServer(applicationContext).also { assetServer = it }
            val port = server.start()
            url = "http://127.0.0.1:$port/index.html"
        } catch (e: Exception) {
            assetServer = null
        }

        wv.isFocusable = true
        wv.isFocusableInTouchMode = true
        wv.requestFocus()

        val rootLayout = FrameLayout(this)
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

        setContentView(rootLayout)
        hideSystemBars()
        scheduleAutoFade()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
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
            elevation = dpToPx(8).toFloat()

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.argb(220, 20, 24, 38))
                setStroke(dpToPx(1), Color.argb(60, 255, 255, 255))
            }
            background = bg
        }

        // 左半部：··· 功能菜单
        val menuBtn = TextView(this).apply {
            text = "···"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dpToPx(42), dpToPx(32))
            isClickable = true
            isFocusable = true
            setOnClickListener { v ->
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                showMiniProgramMenu(v)
            }
        }
        capsule.addView(menuBtn)

        // 中间半透明垂直分割线
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(1), dpToPx(14))
            setBackgroundColor(Color.argb(60, 255, 255, 255))
        }
        capsule.addView(divider)

        // 右半部：✕ 关闭退出按键
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dpToPx(42), dpToPx(32))
            isClickable = true
            isFocusable = true
            setOnClickListener { v ->
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
        popup.menu.add(0, 3, 2, "退出小程序")
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
        webView?.evaluateJavascript("window.APP && APP.pauseGame && APP.pauseGame()", null)
        webView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
        webView?.evaluateJavascript("window.APP && APP.resumeGame && APP.resumeGame()", null)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView?.evaluateJavascript("window.APP && APP.destroyGame && APP.destroyGame()", null)
        assetServer?.stop()
        assetServer = null
        webView?.let { wv ->
            wv.stopLoading()
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.removeAllViews()
            wv.destroy()
        }
        webView = null
        exitCapsule = null
        super.onDestroy()
    }

    private class ChevronLeftDrawable(private val color: Int, private val strokeWidthPx: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@ChevronLeftDrawable.color
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val path = Path()

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            path.reset()
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            val startX = bounds.left + w * 0.70f
            val midX = bounds.left + w * 0.30f
            val topY = bounds.top + h * 0.20f
            val midY = bounds.top + h * 0.50f
            val botY = bounds.top + h * 0.80f
            path.moveTo(startX, topY)
            path.lineTo(midX, midY)
            path.lineTo(startX, botY)
        }

        override fun draw(canvas: Canvas) {
            canvas.drawPath(path, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
