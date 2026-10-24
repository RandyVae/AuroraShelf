package com.aurorashelf.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroAnimationTest {
    @Test
    fun filmLengthMatchesShippedAsset() {
        // The shipped 6.6 s original film; keep this in sync with res/raw/intro_film.mp4.
        assertEquals(6_600L, IntroTiming.FILM_DURATION_MS)
    }

    @Test
    fun safetyStopLeavesRoomForDecodeAndEndedCallback() {
        assertTrue(IntroTiming.SAFETY_MS > IntroTiming.FILM_DURATION_MS)
        assertTrue(IntroTiming.SAFETY_MS - IntroTiming.FILM_DURATION_MS in 1_000..5_000)
        assertTrue(IntroTiming.SKIP_FADE_MS in 1..400L)
    }

    @Test
    fun reduceMotionUsesAShortPosterHold() {
        assertTrue(IntroTiming.REDUCED_HOLD_MS <= IntroTiming.FILM_DURATION_MS / 2)
        assertTrue(IntroTiming.REDUCED_HOLD_MS >= 800L)
    }

    @Test
    fun reduceMotionSkipsPlayback() {
        assertTrue(IntroTiming.shouldAnimate(animatorsEnabled = true))
        assertFalse(IntroTiming.shouldAnimate(animatorsEnabled = false))
    }
}
