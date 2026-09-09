package com.aurorashelf.app.data

import com.aurorashelf.app.data.comic.JmImageScrambler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JmImageScramblerTest {
    @Test
    fun imageBeforeScrambleThresholdIsNotSegmented() {
        assertEquals(0, JmImageScrambler.segmentCount(220980, "220000", "00001.webp"))
    }

    @Test
    fun legacyScrambledImageUsesTenSegments() {
        assertEquals(10, JmImageScrambler.segmentCount(220980, "250000", "00001.webp"))
    }

    @Test
    fun currentScrambledImageUsesAnEvenBoundedSegmentCount() {
        val count = JmImageScrambler.segmentCount(220980, "500000", "00001.webp")
        assertTrue(count in 2..16)
        assertEquals(0, count % 2)
    }

    @Test
    fun currentProtocolHashesFilenameWithoutItsExtension() {
        assertEquals(12, JmImageScrambler.segmentCount(220980, "500000", "00001.webp"))
        assertEquals(12, JmImageScrambler.segmentCount(220980, "500000", "00001.jpg?cache=1"))
        assertEquals(12, JmImageScrambler.segmentCount(220980, "500000", "00001"))
    }
}
