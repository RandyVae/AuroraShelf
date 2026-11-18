package com.aurorashelf.app

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
import com.aurorashelf.app.ui.theme.AuroraTheme
import kotlinx.coroutines.delay

internal val LocalIsInPipMode = compositionLocalOf { false }

class MainActivity : ComponentActivity() {
    private val warmUpViewModel: AuroraViewModel by viewModels()
    private val pipModeState = mutableStateOf(false)

    @Volatile
    private var canAutoEnterPip = false

    @Volatile
    private var currentPipRational = Rational(16, 9)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        warmUpViewModel.uiState
        enableEdgeToEdge()
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

            // Only play the 5.7 s launch film automatically on first launch unless the user explicitly toggled it in Settings.
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
                            enter = fadeIn(tween(120)),
                            exit = fadeOut(tween(240)),
                        ) {
                            PostIntroVeil(appName = getString(R.string.app_name))
                        }
                    }
                }
            }
        }
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
    ) {
        if (!supportsPip()) return
        canAutoEnterPip = isPlayingVideo
        currentPipRational = clampPipAspectRatio(videoWidth, videoHeight)
        runCatching {
            setPictureInPictureParams(buildPipParams(autoEnter = isPlayingVideo))
        }
    }

    internal fun requestEnterPip(videoWidth: Int = 16, videoHeight: Int = 9): Boolean {
        if (!supportsPip() || isInPictureInPictureMode) return false
        currentPipRational = clampPipAspectRatio(videoWidth, videoHeight)
        return runCatching {
            enterPictureInPictureMode(buildPipParams(autoEnter = canAutoEnterPip))
        }.getOrDefault(false)
    }

    private fun buildPipParams(autoEnter: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(currentPipRational)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    companion object {
        internal fun clampPipAspectRatio(width: Int, height: Int): Rational {
            if (width <= 0 || height <= 0) return Rational(16, 9)
            val ratio = (width.toFloat() / height.toFloat()).coerceIn(0.42f, 2.38f)
            val scaledWidth = (ratio * 1000f).toInt().coerceAtLeast(420)
            return Rational(scaledWidth, 1000)
        }
    }
}
