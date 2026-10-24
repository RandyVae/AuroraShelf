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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.core.view.WindowCompat
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
            // The intro owns the first frames; the app is composed once the lockup is reached so its
            // heavy first composition cannot stall the opening (or when the user skips).
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
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    // The intro stage is pure black, so keep system bar icons light while it plays.
                    isAppearanceLightStatusBars = if (introVisible) false else !darkTheme
                    isAppearanceLightNavigationBars = if (introVisible) false else !darkTheme
                }
            }
            AuroraTheme(darkTheme = darkTheme) {
                Box(Modifier.fillMaxSize()) {
                    // The app composes and starts loading underneath the intro, so the brand
                    // moment never delays first paint.
                    if (appVisible) {
                        AuroraShelfApp()
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
