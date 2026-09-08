package com.aurorashelf.app.ui.theme

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class AuroraThemeTest {
    @Test
    fun fallbackSchemesProvideDistinctReadableBackgrounds() {
        val light = auroraFallbackColorScheme(darkTheme = false)
        val dark = auroraFallbackColorScheme(darkTheme = true)

        assertTrue(light.background.luminance() > 0.8f)
        assertTrue(light.onBackground.luminance() < 0.2f)
        assertTrue(dark.background.luminance() < 0.1f)
        assertTrue(dark.onBackground.luminance() > 0.8f)
    }
}
