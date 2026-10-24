package com.aurorashelf.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroAnimationTest {
    @Test
    fun introStaysDismissibleAndWithinCinematicBudget() {
        assertTrue("开场应有电影感长度但不超过 5s", IntroTiming.DURATION_MS in 3_000..5_000)
        assertTrue(IntroTiming.SKIP_FADE_MS in 1..400L)
        assertTrue(IntroTiming.REDUCED_HOLD_MS < IntroTiming.DURATION_MS)
    }

    @Test
    fun ribbonsWeaveThenImpactBursts() {
        assertEquals(0f, IntroTiming.ribbonProgress(0f))
        assertTrue(IntroTiming.ribbonProgress(0.20f) in 0.1f..0.7f)
        assertEquals(1f, IntroTiming.ribbonProgress(0.50f))
        assertEquals(0f, IntroTiming.burstProgress(0.30f))
        assertTrue(IntroTiming.burstProgress(0.50f) in 0.2f..0.7f)
        assertEquals(1f, IntroTiming.burstProgress(0.60f))
    }

    @Test
    fun wordmarkAssemblesAfterTheBurst() {
        assertEquals(0f, IntroTiming.wordmarkProgress(0.30f))
        assertTrue(IntroTiming.wordmarkProgress(0.62f) in 0.1f..0.6f)
        assertEquals(1f, IntroTiming.wordmarkProgress(0.80f))
        assertEquals(0f, IntroTiming.lockupProgress(0.70f))
        assertEquals(1f, IntroTiming.lockupProgress(0.85f))
    }

    @Test
    fun contentFadesOnlyAtTheEnd() {
        assertEquals(1f, IntroTiming.contentAlpha(0f))
        assertEquals(1f, IntroTiming.contentAlpha(IntroTiming.FADE_OUT_START))
        assertTrue(IntroTiming.contentAlpha(0.97f) in 0.3f..0.7f)
        assertEquals(0f, IntroTiming.contentAlpha(1f))
    }

    @Test
    fun reduceMotionSkipsCinematicTimeline() {
        assertTrue(IntroTiming.shouldAnimate(animatorsEnabled = true))
        assertFalse(IntroTiming.shouldAnimate(animatorsEnabled = false))
        assertTrue(IntroTiming.REDUCED_STATIC_PROGRESS in 0.7f..0.95f)
    }

    @Test
    fun timelineConvertsToMilliseconds() {
        assertEquals(0L, IntroTimingElapsedMs(0f))
        assertEquals(IntroTiming.DURATION_MS, IntroTimingElapsedMs(1f))
        assertEquals(IntroTiming.DURATION_MS / 2, IntroTimingElapsedMs(0.5f))
    }
}
