package com.aurorashelf.app

import com.aurorashelf.app.cast.LocalNetworkPermission
import com.aurorashelf.app.ui.theme.AuroraTypography
import com.aurorashelf.app.ui.theme.auroraColorScheme
import com.aurorashelf.app.ui.theme.contrastRatio
import com.aurorashelf.app.ui.theme.schemeNeedsFallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Android17ExpressiveComplianceTest {

    @Test
    fun localNetworkPermissionGatingMatchesAndroid17Api37() {
        assertEquals("android.permission.ACCESS_LOCAL_NETWORK", LocalNetworkPermission.PERMISSION)
        assertFalse(LocalNetworkPermission.requiresRuntimeGrant(34))
        assertFalse(LocalNetworkPermission.requiresRuntimeGrant(35))
        assertFalse(LocalNetworkPermission.requiresRuntimeGrant(36))
        assertTrue(LocalNetworkPermission.requiresRuntimeGrant(37))
        assertTrue(LocalNetworkPermission.requiresRuntimeGrant(38))
    }

    @Test
    fun expressiveColorSchemesDefineFiveTonalSurfaceTiersAndMeetContrast() {
        val variants = listOf(
            Triple("Light", false, false),
            Triple("Dark", true, false),
            Triple("HighContrastLight", false, true),
            Triple("HighContrastDark", true, true),
        )
        variants.forEach { (name, dark, highContrast) ->
            val scheme = auroraColorScheme(darkTheme = dark, highContrast = highContrast)
            assertFalse("$name scheme must pass WCAG AA guard", schemeNeedsFallback(scheme))

            val surfaceTiers = setOf(
                scheme.surfaceContainerLowest,
                scheme.surfaceContainerLow,
                scheme.surfaceContainer,
                scheme.surfaceContainerHigh,
                scheme.surfaceContainerHighest,
            )
            assertEquals("$name scheme must define 5 distinct tonal surfaceContainer tiers", 5, surfaceTiers.size)

            val minText = if (highContrast) 7.0 else 4.5
            assertTrue(
                "$name onPrimary/primary contrast must be >= $minText",
                contrastRatio(scheme.onPrimary, scheme.primary) >= minText,
            )
            assertTrue(
                "$name onPrimaryContainer/primaryContainer contrast must be >= 4.5",
                contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5,
            )
            assertTrue(
                "$name onSecondaryContainer/secondaryContainer contrast must be >= 4.5",
                contrastRatio(scheme.onSecondaryContainer, scheme.secondaryContainer) >= 4.5,
            )
            assertTrue(
                "$name onSurface/surfaceContainerLow contrast must be >= $minText",
                contrastRatio(scheme.onSurface, scheme.surfaceContainerLow) >= minText,
            )
        }
    }

    @Test
    fun expressiveTypographyScaleIsStrictlyMonotonic() {
        assertTrue(AuroraTypography.displayLarge.fontSize > AuroraTypography.headlineLarge.fontSize)
        assertTrue(AuroraTypography.headlineLarge.fontSize > AuroraTypography.titleLarge.fontSize)
        assertTrue(AuroraTypography.titleLarge.fontSize > AuroraTypography.titleMedium.fontSize)
        assertTrue(AuroraTypography.titleMedium.fontSize >= AuroraTypography.bodyLarge.fontSize)
        assertTrue(AuroraTypography.bodyLarge.fontSize > AuroraTypography.bodySmall.fontSize)
        assertTrue(AuroraTypography.labelLarge.fontSize > AuroraTypography.labelSmall.fontSize)
    }
}
