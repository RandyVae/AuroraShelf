package com.aurorashelf.app.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class WindowLayoutTest {
    @Test fun portraitFitsDefaultTextContentAcrossPhoneWindowSizes() {
        // Arrange: representative logical windows; not a claim about Samsung's factory DPI.
        listOf(384f to 832f, 411f to 891f, 448f to 971f).forEach { (width, height) ->
            val usableHeight = height - 32f - 112f
            // Act
            val layout = WindowLayout.calculate(width, usableHeight, false)
            val contentWidth = width - 40f
            val heroHeight = contentWidth / layout.heroAspectRatio
            val rowHeight = contentWidth * layout.rowThumbnailFraction / 1.6f + 8f
            // Assert: two rows fit at default font size, without shrinking text.
            assertTrue(heroHeight + rowHeight * 2 + 70 + 48 + 64 <= usableHeight + 0.1f)
            assertFalse(layout.compactDock)
        }
    }

    @Test fun shortWindowsRetainReadableHeroAndAllowScrolling() {
        val layout = WindowLayout.calculate(384f, 400f, false)
        assertEquals(214f, 344f / layout.heroAspectRatio, 0.1f)
    }

    @Test fun landscapeUsesCompactControlsAndBoundedPosterHeight() {
        val layout = WindowLayout.calculate(891f, 260f, true)
        assertTrue(layout.compactDock)
        assertEquals(128f, 640f / layout.heroAspectRatio, 0.1f)
        assertTrue(layout.rowThumbnailFraction < 0.46f)
    }

    @Test fun tabletTwoPaneLandscapeLayoutThresholdAndRotationTransitions() {
        // Tablet portrait: stacked single pane
        assertFalse(shouldUseTwoPaneLandscapeLayout(width = 600.dp, height = 960.dp, isFullscreen = false))
        assertFalse(shouldUseTwoPaneLandscapeLayout(width = 800.dp, height = 1280.dp, isFullscreen = false))

        // Tablet rotated to landscape (including 700dp-839dp standard tablets and 1280dp large tablets)
        assertTrue(shouldUseTwoPaneLandscapeLayout(width = 768.dp, height = 560.dp, isFullscreen = false))
        assertTrue(shouldUseTwoPaneLandscapeLayout(width = 800.dp, height = 500.dp, isFullscreen = false))
        assertTrue(shouldUseTwoPaneLandscapeLayout(width = 1280.dp, height = 800.dp, isFullscreen = false))

        // Fullscreen never enters split mode
        assertFalse(shouldUseTwoPaneLandscapeLayout(width = 1280.dp, height = 800.dp, isFullscreen = true))

        // Compact phone portrait remains single pane (and <700dp stays single pane)
        assertFalse(shouldUseTwoPaneLandscapeLayout(width = 640.dp, height = 360.dp, isFullscreen = false))
    }
}
