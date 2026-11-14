package com.aurorashelf.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.aurorashelf.app.data.AppPreferences
import com.aurorashelf.app.ui.AuroraShelfApp
import com.aurorashelf.app.ui.AuroraViewModel
import com.aurorashelf.app.ui.OnFirstFrame
import com.aurorashelf.app.ui.PostIntroVeil
import com.aurorashelf.app.ui.BrandIntroOverlay
import com.aurorashelf.app.ui.theme.AuroraTheme

class MainActivity : ComponentActivity() {
    // Created before the UI so the feed/cover warm-up runs during the cinematic intro.
    private val warmUpViewModel: AuroraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Touch the lazy delegate immediately so AuroraViewModel.init warms the feed/covers
        // while the 5.7 s brand intro film plays.
        warmUpViewModel.uiState
        enableEdgeToEdge()
        setContent {
            val preferences = remember { AppPreferences(applicationContext) }
            val darkTheme = isSystemInDarkTheme()
            // Cinematic intro: release builds only (debug skips so instrumented tests are not
            // blocked by a full-screen overlay), once per cold start, user-switchable in Settings.
            var introVisible by rememberSaveable {
                mutableStateOf(!BuildConfig.DEBUG && preferences.introAnimationEnabled)
            }
            var introSoundEnabled by remember { mutableStateOf(preferences.introSoundEnabled) }
            // The intro owns the first frames so its H.264 decoder and RenderThread never
            // contend with Home's initial composition or ViewModel initialization; PostIntroVeil
            // bridges the brief frame gap when the app reveals after the intro or a tap-skip.
            var appVisible by rememberSaveable { mutableStateOf(!introVisible) }
            // The veil covers the app's first composition; it only exists after the intro.
            var veilVisible by rememberSaveable { mutableStateOf(false) }
            var appDrawn by remember { mutableStateOf(!introVisible) }
            if (appDrawn && veilVisible) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(140)
                    veilVisible = false
                }
            }
            var introShownAtLaunch by rememberSaveable { mutableStateOf(introVisible) }
            LaunchedEffect(introVisible) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    // Full-bleed cinema: hide system bars during the film and restore visibility
                    // once it finishes; AuroraShelfApp owns per-destination light/dark bar styling.
                    if (introVisible) {
                        introShownAtLaunch = true
                        isAppearanceLightStatusBars = false
                        isAppearanceLightNavigationBars = false
                        hide(WindowInsetsCompat.Type.systemBars())
                        systemBarsBehavior =
                            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else if (introShownAtLaunch) {
                        introShownAtLaunch = false
                        show(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
            AuroraTheme(darkTheme = darkTheme) {
                Box(Modifier.fillMaxSize()) {
                    // Reveal and compose the main app once the intro finishes or is skipped.
                    if (appVisible) {
                        AuroraShelfApp(
                            viewModel = warmUpViewModel,
                            introActive = introVisible,
                        )
                        OnFirstFrame { appDrawn = true }
                    }
                    if (introVisible) {
                        BrandIntroOverlay(
                            soundEnabled = introSoundEnabled,
                            onFinished = {
                                introVisible = false
                                appVisible = true
                                veilVisible = true
                            },
                        )
                    }
                    AnimatedVisibility(
                        visible = veilVisible,
                        enter = fadeIn(tween(120)),
                        exit = fadeOut(tween(280)),
                    ) {
                        PostIntroVeil(appName = getString(R.string.app_name))
                    }
                }
            }
        }
    }
}
