package com.aurorashelf.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.aurorashelf.app.ui.AuroraShelfApp
import com.aurorashelf.app.ui.BrandIntroOverlay
import com.aurorashelf.app.ui.theme.AuroraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            // Brand intro plays once per cold start. Debug builds skip it so instrumented tests
            // are not blocked by a full-screen overlay; release builds show the real moment.
            var introVisible by rememberSaveable { mutableStateOf(!BuildConfig.DEBUG) }
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
                    AuroraShelfApp()
                    if (introVisible) {
                        BrandIntroOverlay(onFinished = { introVisible = false })
                    }
                }
            }
        }
    }
}
