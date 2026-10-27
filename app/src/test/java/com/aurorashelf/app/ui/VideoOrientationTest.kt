package com.aurorashelf.app.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoOrientationTest {
    @Test
    fun detectsLandscapeAndPortraitVideo() {
        assertFalse(isPortraitVideo(width = 1920, height = 1080)!!)
        assertTrue(isPortraitVideo(width = 1080, height = 1920)!!)
    }

    @Test
    fun appliesRotationAndPixelAspectRatio() {
        assertTrue(
            isPortraitVideo(
                width = 1920,
                height = 1080,
                unappliedRotationDegrees = 90,
            )!!,
        )
        assertEquals(false, isPortraitVideo(width = 720, height = 1080, pixelWidthHeightRatio = 2f))
    }

    @Test
    fun ignoresUnknownVideoSize() {
        assertNull(isPortraitVideo(width = 0, height = 0))
    }

    @Test
    fun calculatesVideoAspectRatioCorrectly() {
        // Standard 16:9 landscape
        val landscapeRatio = calculateVideoAspectRatio(width = 1920, height = 1080)!!
        assertEquals(16f / 9f, landscapeRatio, 0.01f)

        // Standard 9:16 portrait
        val portraitRatio = calculateVideoAspectRatio(width = 1080, height = 1920)!!
        assertEquals(9f / 16f, portraitRatio, 0.01f)

        // Rotation 90 degrees flips width and height
        val rotatedRatio = calculateVideoAspectRatio(width = 1920, height = 1080, unappliedRotationDegrees = 90)!!
        assertEquals(9f / 16f, rotatedRatio, 0.01f)

        // Pixel aspect ratio scaling
        val anamorphicRatio = calculateVideoAspectRatio(width = 720, height = 1080, pixelWidthHeightRatio = 2f)!!
        assertEquals(1440f / 1080f, anamorphicRatio, 0.01f)

        // Invalid dimensions return null
        assertNull(calculateVideoAspectRatio(width = 0, height = 0))
        assertNull(calculateVideoAspectRatio(width = -10, height = 100))
    }

    @Test
    fun calculatesAdaptivePlayerHeightForLandscapeAndPortrait() {
        val width = 400.dp
        val height = 840.dp
        val maxPortraitAllowed = height * 0.60f // 504.dp

        // Null aspect ratio defaults to 16:9
        val defaultHeight = calculateAdaptivePlayerHeight(aspectRatio = null, containerWidth = width, containerHeight = height)
        assertEquals(400.dp * (9f / 16f), defaultHeight)

        // Landscape 16:9 video retains 16:9 standard height
        val landscapeHeight = calculateAdaptivePlayerHeight(aspectRatio = 16f / 9f, containerWidth = width, containerHeight = height)
        assertEquals(225.dp.value, landscapeHeight.value, 0.1f)

        // Portrait 9:16 video expands to max allowed viewport height (~60% of viewport)
        // rather than collapsing into 16:9 with huge pillarbox bars
        val portraitHeight = calculateAdaptivePlayerHeight(aspectRatio = 9f / 16f, containerWidth = width, containerHeight = height)
        assertEquals(maxPortraitAllowed.value, portraitHeight.value, 0.1f)

        // 4:5 vertical video fits within the bounds without being excessively clipped
        val ratio4to5 = 4f / 5f
        val height4to5 = calculateAdaptivePlayerHeight(aspectRatio = ratio4to5, containerWidth = width, containerHeight = height)
        assertEquals(500.dp.value, height4to5.value, 0.1f)

        // Square 1:1 video expands to square width (400.dp)
        val squareHeight = calculateAdaptivePlayerHeight(aspectRatio = 1.0f, containerWidth = width, containerHeight = height)
        assertEquals(400.dp.value, squareHeight.value, 0.1f)

        // Landscape phone viewport (e.g. 840.dp wide, 400.dp high) caps height so details are not buried
        val landscapeViewportHeight = calculateAdaptivePlayerHeight(aspectRatio = 16f / 9f, containerWidth = 840.dp, containerHeight = 400.dp)
        assertTrue(landscapeViewportHeight <= 400.dp * 0.70f)
    }

    @Test
    fun calculatesEffectivePlayerHeightCollapsingOnScroll() {
        val expanded = 504.dp
        val collapsed = 225.dp

        // At 0 collapse offset (unscrolled), player stays fully expanded
        val initialHeight = calculateEffectivePlayerHeight(expandedHeight = expanded, collapsedHeight = collapsed, collapseOffsetDp = 0.dp)
        assertEquals(504.dp.value, initialHeight.value, 0.1f)

        // Mid-scroll: player smoothly shrinks proportional to finger drag
        val midHeight = calculateEffectivePlayerHeight(expandedHeight = expanded, collapsedHeight = collapsed, collapseOffsetDp = 100.dp)
        assertEquals(404.dp.value, midHeight.value, 0.1f)

        // Full collapse: player stops exactly at collapsed 16:9 height (225.dp)
        val fullHeight = calculateEffectivePlayerHeight(expandedHeight = expanded, collapsedHeight = collapsed, collapseOffsetDp = 279.dp)
        assertEquals(225.dp.value, fullHeight.value, 0.1f)

        // Over-scroll: collapse offset exceeding range never shrinks below 16:9
        val overHeight = calculateEffectivePlayerHeight(expandedHeight = expanded, collapsedHeight = collapsed, collapseOffsetDp = 500.dp)
        assertEquals(225.dp.value, overHeight.value, 0.1f)

        // Negative offset: never expands beyond initial expanded height
        val negativeHeight = calculateEffectivePlayerHeight(expandedHeight = expanded, collapsedHeight = collapsed, collapseOffsetDp = (-50).dp)
        assertEquals(504.dp.value, negativeHeight.value, 0.1f)

        // Landscape video where expanded == collapsed: height stays invariant
        val landscapeStatic = calculateEffectivePlayerHeight(expandedHeight = 225.dp, collapsedHeight = 225.dp, collapseOffsetDp = 100.dp)
        assertEquals(225.dp.value, landscapeStatic.value, 0.1f)

        // Robustness: floating point variance where expanded is slightly less than collapsed must never throw
        val floatVarianceHeight = calculateEffectivePlayerHeight(expandedHeight = 231.39464.dp, collapsedHeight = 231.42856.dp, collapseOffsetDp = 10.dp)
        assertEquals(231.39464.dp.value, floatVarianceHeight.value, 0.001f)

        // Ultra-wide (21:9) video where expanded height is less than standard 16:9 collapsed height
        val ultrawideHeight = calculateEffectivePlayerHeight(expandedHeight = 175.dp, collapsedHeight = 225.dp, collapseOffsetDp = 20.dp)
        assertEquals(175.dp.value, ultrawideHeight.value, 0.1f)
    }

    @Test
    fun calculatesVideoProgressAndBufferedFractions() {
        val totalDuration = 100_000L // 100 seconds

        // Zero or negative position returns 0f
        assertEquals(0f, calculateVideoProgressFraction(positionMs = 0L, durationMs = totalDuration), 0.001f)
        assertEquals(0f, calculateVideoProgressFraction(positionMs = -500L, durationMs = totalDuration), 0.001f)

        // Midpoint progress
        assertEquals(0.5f, calculateVideoProgressFraction(positionMs = 50_000L, durationMs = totalDuration), 0.001f)

        // Complete progress
        assertEquals(1f, calculateVideoProgressFraction(positionMs = 100_000L, durationMs = totalDuration), 0.001f)

        // Over-duration clamps to 1f
        assertEquals(1f, calculateVideoProgressFraction(positionMs = 150_000L, durationMs = totalDuration), 0.001f)

        // Invalid or zero duration returns 0f safely
        assertEquals(0f, calculateVideoProgressFraction(positionMs = 50_000L, durationMs = 0L), 0.001f)
        assertEquals(0f, calculateVideoProgressFraction(positionMs = 50_000L, durationMs = -1L), 0.001f)

        // Buffered fraction calculation
        assertEquals(0f, calculateVideoBufferedFraction(bufferedMs = 0L, durationMs = totalDuration), 0.001f)
        assertEquals(0.75f, calculateVideoBufferedFraction(bufferedMs = 75_000L, durationMs = totalDuration), 0.001f)
        assertEquals(1f, calculateVideoBufferedFraction(bufferedMs = 120_000L, durationMs = totalDuration), 0.001f)
        assertEquals(0f, calculateVideoBufferedFraction(bufferedMs = 50_000L, durationMs = 0L), 0.001f)
    }
}
