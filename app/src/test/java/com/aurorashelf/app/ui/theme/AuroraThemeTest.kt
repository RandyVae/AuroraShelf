package com.aurorashelf.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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

    @Test
    fun normalFallbackSchemesMeetWcagAaThresholds() {
        for (scheme in listOf(auroraFallbackColorScheme(darkTheme = false), auroraFallbackColorScheme(darkTheme = true))) {
            assertTextPairsAtLeast(scheme, minimum = 4.5)
            assertTrue(
                "outline/surface must reach the 3:1 non-text threshold",
                contrastRatio(scheme.outline, scheme.surface) >= 3.0,
            )
        }
    }

    @Test
    fun highContrastSchemesMeetStrictWcagTargets() {
        for (scheme in listOf(AuroraHighContrastLightColors, AuroraHighContrastDarkColors)) {
            assertTextPairsAtLeast(scheme, minimum = 7.0)
            assertTrue(
                "high-contrast outline/surface must reach 4.5:1",
                contrastRatio(scheme.outline, scheme.surface) >= 4.5,
            )
        }
    }

    @Test
    fun highContrastSchemesStrictlyImproveEveryCriticalPair() {
        assertStrictlyImprovesEveryCriticalPair(
            normal = auroraFallbackColorScheme(darkTheme = false),
            highContrast = AuroraHighContrastLightColors,
        )
        assertStrictlyImprovesEveryCriticalPair(
            normal = auroraFallbackColorScheme(darkTheme = true),
            highContrast = AuroraHighContrastDarkColors,
        )
    }

    @Test
    fun contrastRatioMatchesKnownWcagValuesAndIsSymmetric() {
        assertEquals(21.0, contrastRatio(Color.White, Color.Black), 1e-9)
        assertEquals(21.0, contrastRatio(Color.Black, Color.White), 1e-9)
        assertEquals(1.0, contrastRatio(Color.White, Color.White), 1e-9)
        assertEquals(4.542225, contrastRatio(Color(0xFF767676), Color.White), 1e-4)

        val first = Color(0xFF3366CC)
        val second = Color(0xFFEEEEEE)
        assertEquals(contrastRatio(first, second), contrastRatio(second, first), 1e-12)
    }

    @Test
    fun schemeNeedsFallbackAcceptsReadableSchemes() {
        assertFalse(schemeNeedsFallback(auroraFallbackColorScheme(darkTheme = false)))
        assertFalse(schemeNeedsFallback(auroraFallbackColorScheme(darkTheme = true)))
        assertFalse(schemeNeedsFallback(AuroraHighContrastLightColors))
        assertFalse(schemeNeedsFallback(AuroraHighContrastDarkColors))
    }

    @Test
    fun schemeNeedsFallbackRejectsLowContrastSchemes() {
        val lowText = lightColorScheme(
            background = Color(0xFFF8F9FE),
            onBackground = Color(0xFFE6E8EE),
        )
        assertTrue("low-contrast onBackground must trigger the fallback", schemeNeedsFallback(lowText))

        val lowOutline = darkColorScheme(
            surface = Color(0xFF111722),
            outline = Color(0xFF1A1D22),
        )
        assertTrue("low-contrast outline must trigger the fallback", schemeNeedsFallback(lowOutline))

        val lowPrimary = lightColorScheme(
            primary = Color(0xFF6750A4),
            onPrimary = Color(0xFF6750A4),
        )
        assertTrue("onPrimary equal to primary must trigger the fallback", schemeNeedsFallback(lowPrimary))
    }

    @Test
    fun auroraColorSchemeSelectsHighContrastVariants() {
        assertSame(
            auroraFallbackColorScheme(darkTheme = false),
            auroraColorScheme(darkTheme = false, highContrast = false),
        )
        assertSame(
            auroraFallbackColorScheme(darkTheme = true),
            auroraColorScheme(darkTheme = true, highContrast = false),
        )
        assertSame(AuroraHighContrastLightColors, auroraColorScheme(darkTheme = false, highContrast = true))
        assertSame(AuroraHighContrastDarkColors, auroraColorScheme(darkTheme = true, highContrast = true))
        assertTrue(AuroraHighContrastLightColors.primary != auroraFallbackColorScheme(darkTheme = false).primary)
        assertTrue(AuroraHighContrastDarkColors.primary != auroraFallbackColorScheme(darkTheme = true).primary)
    }

    private fun assertTextPairsAtLeast(scheme: ColorScheme, minimum: Double) {
        for ((label, foreground, background) in criticalPairs(scheme)) {
            val ratio = contrastRatio(foreground, background)
            assertTrue("$label expected >= $minimum:1 but was $ratio:1", ratio >= minimum)
        }
    }

    private fun assertStrictlyImprovesEveryCriticalPair(normal: ColorScheme, highContrast: ColorScheme) {
        val normalRatios = criticalPairs(normal).map { (_, foreground, background) -> contrastRatio(foreground, background) }
        val highContrastRatios = criticalPairs(highContrast).map { (_, foreground, background) -> contrastRatio(foreground, background) }
        normalRatios.zip(highContrastRatios).forEachIndexed { index, (normalRatio, highContrastRatio) ->
            assertTrue(
                "critical pair #$index must be strictly stronger in the high-contrast palette",
                highContrastRatio > normalRatio,
            )
        }
        assertTrue(
            "outline/surface must be strictly stronger in the high-contrast palette",
            contrastRatio(highContrast.outline, highContrast.surface) > contrastRatio(normal.outline, normal.surface),
        )
    }

    private fun criticalPairs(scheme: ColorScheme): List<Triple<String, Color, Color>> = listOf(
        Triple("onBackground/background", scheme.onBackground, scheme.background),
        Triple("onSurface/surface", scheme.onSurface, scheme.surface),
        Triple("onSurfaceVariant/surface", scheme.onSurfaceVariant, scheme.surface),
        Triple("onPrimary/primary", scheme.onPrimary, scheme.primary),
        Triple("error/surface", scheme.error, scheme.surface),
    )
}
