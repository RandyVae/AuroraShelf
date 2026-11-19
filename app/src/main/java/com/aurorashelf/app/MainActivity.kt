package com.aurorashelf.app

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurorashelf.app.data.AppPreferences
import com.aurorashelf.app.data.ThemeMode
import com.aurorashelf.app.ui.AuroraShelfApp
import com.aurorashelf.app.ui.AuroraViewModel
import com.aurorashelf.app.ui.BrandIntroOverlay
import com.aurorashelf.app.ui.OnFirstFrame
import com.aurorashelf.app.ui.PostIntroVeil
import com.aurorashelf.app.ui.theme.AuroraMotion
import com.aurorashelf.app.ui.theme.AuroraTheme
import kotlinx.coroutines.delay

internal val LocalIsInPipMode = compositionLocalOf { false }

class MainActivity : ComponentActivity() {
    private val warmUpViewModel: AuroraViewModel by viewModels()
    private val pipModeState = mutableStateOf(false)

    @Volatile
    private var canAutoEnterPip = false

    @Volatile
    private var currentPipPlaying = false

    @Volatile
    private var currentPipRational = Rational(16, 9)

    @Volatile
    private var currentPipSourceRect: Rect? = null

    @Volatile
    private var pipTogglePlaybackCallback: (() -> Unit)? = null

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_PIP_TOGGLE_PLAYBACK) {
                pipTogglePlaybackCallback?.invoke()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        warmUpViewModel.uiState
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        ContextCompat.registerReceiver(
            this,
            pipActionReceiver,
            IntentFilter(ACTION_PIP_TOGGLE_PLAYBACK),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        pipModeState.value = isInPictureInPictureMode
        setContent {
            val preferences = remember { AppPreferences.get(applicationContext) }
            val themeMode by preferences.themeModeFlow.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val effectiveDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            LaunchedEffect(effectiveDarkTheme) {
                val barStyle = if (effectiveDarkTheme) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle,
                )
            }

            // Only play the launch film automatically on first launch unless the user explicitly toggled it in Settings.
            var introVisible by rememberSaveable {
                val shouldShowIntro = !BuildConfig.DEBUG &&
                    preferences.introAnimationEnabled &&
                    (!preferences.introHasPlayedOnce || preferences.isIntroExplicitlyConfigured)
                if (shouldShowIntro) {
                    preferences.introHasPlayedOnce = true
                }
                mutableStateOf(shouldShowIntro)
            }
            val introSoundEnabled = remember { preferences.introSoundEnabled }
            var veilVisible by rememberSaveable { mutableStateOf(false) }
            var appDrawn by remember { mutableStateOf(!introVisible) }

            if (appDrawn && veilVisible) {
                LaunchedEffect(Unit) {
                    delay(120)
                    veilVisible = false
                }
            }

            var introShownAtLaunch by rememberSaveable { mutableStateOf(introVisible) }
            LaunchedEffect(introVisible) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    if (introVisible) {
                        introShownAtLaunch = true
                        isAppearanceLightStatusBars = false
                        isAppearanceLightNavigationBars = false
                        hide(WindowInsetsCompat.Type.systemBars())
                        systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else if (introShownAtLaunch) {
                        introShownAtLaunch = false
                        show(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }

            val isInPipMode by pipModeState
            CompositionLocalProvider(LocalIsInPipMode provides isInPipMode) {
                AuroraTheme(darkTheme = effectiveDarkTheme) {
                    Box(Modifier.fillMaxSize()) {
                        // Compose AuroraShelfApp immediately underneath so cold start never stalls behind the intro film.
                        AuroraShelfApp(
                            viewModel = warmUpViewModel,
                            introActive = introVisible,
                        )
                        OnFirstFrame { appDrawn = true }

                        if (introVisible && !isInPipMode) {
                            BrandIntroOverlay(
                                soundEnabled = introSoundEnabled,
                                onFinished = {
                                    introVisible = false
                                    if (!appDrawn) veilVisible = true
                                },
                            )
                        }
                        AnimatedVisibility(
                            visible = veilVisible && !isInPipMode,
                            enter = fadeIn(AuroraMotion.fastEffects()),
                            exit = fadeOut(AuroraMotion.defaultEffects()),
                        ) {
                            PostIntroVeil(appName = getString(R.string.app_name))
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipActionReceiver) }
        pipTogglePlaybackCallback = null
        super.onDestroy()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipModeState.value = isInPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && canAutoEnterPip && supportsPip()) {
            runCatching {
                enterPictureInPictureMode(buildPipParams(autoEnter = false))
            }
        }
    }

    internal fun supportsPip(): Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    internal fun updatePipPlaybackState(
        isPlayingVideo: Boolean,
        videoWidth: Int = 16,
        videoHeight: Int = 9,
        sourceRectHint: Rect? = null,
        onTogglePlayback: (() -> Unit)? = null,
    ) {
        if (!supportsPip()) return
        canAutoEnterPip = isPlayingVideo
        currentPipPlaying = isPlayingVideo
        currentPipRational = clampPipAspectRatio(videoWidth, videoHeight)
        if (sourceRectHint != null && !sourceRectHint.isEmpty) {
            currentPipSourceRect = Rect(sourceRectHint)
        } else if (!isPlayingVideo) {
            currentPipSourceRect = null
        }
        pipTogglePlaybackCallback = onTogglePlayback
        runCatching {
            setPictureInPictureParams(buildPipParams(autoEnter = isPlayingVideo))
        }
    }

    internal fun requestEnterPip(
        videoWidth: Int = 16,
        videoHeight: Int = 9,
        sourceRectHint: Rect? = null,
    ): Boolean {
        if (!supportsPip() || isInPictureInPictureMode) return false
        currentPipRational = clampPipAspectRatio(videoWidth, videoHeight)
        if (sourceRectHint != null && !sourceRectHint.isEmpty) {
            currentPipSourceRect = Rect(sourceRectHint)
        }
        return runCatching {
            enterPictureInPictureMode(buildPipParams(autoEnter = canAutoEnterPip))
        }.getOrDefault(false)
    }

    private fun buildPipParams(autoEnter: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(currentPipRational)
        currentPipSourceRect?.takeIf { !it.isEmpty }?.let { rect ->
            builder.setSourceRectHint(rect)
        }
        if (pipTogglePlaybackCallback != null) {
            val isPlaying = currentPipPlaying
            val iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            val title = getString(if (isPlaying) R.string.pip_action_pause else R.string.pip_action_play)
            val intent = Intent(ACTION_PIP_TOGGLE_PLAYBACK).setPackage(packageName)
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                PIP_REQUEST_CODE_TOGGLE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setActions(
                listOf(
                    RemoteAction(
                        Icon.createWithResource(this, iconRes),
                        title,
                        title,
                        pendingIntent,
                    ),
                ),
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    companion object {
        private const val ACTION_PIP_TOGGLE_PLAYBACK = "com.aurorashelf.app.action.PIP_TOGGLE_PLAYBACK"
        private const val PIP_REQUEST_CODE_TOGGLE = 4101

        internal fun clampPipAspectRatio(width: Int, height: Int): Rational {
            if (width <= 0 || height <= 0) return Rational(16, 9)
            val ratio = (width.toFloat() / height.toFloat()).coerceIn(0.42f, 2.38f)
            val scaledWidth = (ratio * 1000f).toInt().coerceAtLeast(420)
            return Rational(scaledWidth, 1000)
        }
    }
}
