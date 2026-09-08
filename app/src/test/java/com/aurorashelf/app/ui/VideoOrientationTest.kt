package com.aurorashelf.app.ui

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
}
