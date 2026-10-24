package com.aurorashelf.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroAnimationTest {
    @Test
    fun introIsShortEnoughForLaunchAndConfigurable() {
        assertTrue("开场必须明显短于 2s", IntroTiming.DURATION_MS <= 2_000L)
        assertTrue(IntroTiming.SKIP_FADE_MS in 1..300L)
        assertTrue(IntroTiming.REDUCED_HOLD_MS <= IntroTiming.DURATION_MS)
    }

    @Test
    fun markDrawsInStagesAndThenSweeps() {
        assertEquals(0f, IntroTiming.markProgress(0f))
        assertTrue(IntroTiming.markProgress(0.20f) > 0f)
        assertTrue(IntroTiming.markProgress(0.20f) < 1f)
        assertEquals(1f, IntroTiming.markProgress(0.50f))
        assertEquals(0f, IntroTiming.sweepProgress(0.30f))
        assertTrue(IntroTiming.sweepProgress(0.47f) in 0.4f..0.6f)
        assertEquals(1f, IntroTiming.sweepProgress(0.60f))
    }

    @Test
    fun wordmarkAppearsAfterTheMarkIsDrawn() {
        assertEquals(0f, IntroTiming.wordmarkProgress(0.30f))
        assertTrue(IntroTiming.wordmarkProgress(0.60f) in 0.1f..0.5f)
        assertEquals(1f, IntroTiming.wordmarkProgress(0.80f))
    }

    @Test
    fun contentFadesOnlyAtTheEnd() {
        assertEquals(1f, IntroTiming.contentAlpha(0f))
        assertEquals(1f, IntroTiming.contentAlpha(IntroTiming.FADE_OUT_START))
        assertTrue(IntroTiming.contentAlpha(0.95f) in 0.4f..0.6f)
        assertEquals(0f, IntroTiming.contentAlpha(1f))
    }

    @Test
    fun reduceMotionSkipsCinematicTimeline() {
        assertTrue(IntroTiming.shouldAnimate(animatorsEnabled = true))
        assertFalse(IntroTiming.shouldAnimate(animatorsEnabled = false))
    }

    @Test
    fun timelineConvertsToMilliseconds() {
        assertEquals(0L, IntroTimingElapsedMs(0f))
        assertEquals(IntroTiming.DURATION_MS, IntroTimingElapsedMs(1f))
        assertEquals(IntroTiming.DURATION_MS / 2, IntroTimingElapsedMs(0.5f))
    }
}
