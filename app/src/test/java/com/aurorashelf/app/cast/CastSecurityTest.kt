package com.aurorashelf.app.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastSecurityTest {

    @Test
    fun sanitizesSessionTokenInLogUrls() {
        val raw = "http://192.168.1.100:8888/stream?token=abc123xyz789&url=https://example.com/video.m3u8"
        val sanitized = CastManager.sanitizeStreamUrl(raw)
        assertEquals("http://192.168.1.100:8888/stream?token=***&url=https://example.com/video.m3u8", sanitized)
        assertFalse(sanitized.contains("abc123xyz789"))
    }

    @Test
    fun preservesUrlsWithoutToken() {
        val raw = "https://example.com/video.mp4"
        assertEquals(raw, CastManager.sanitizeStreamUrl(raw))
    }
}
