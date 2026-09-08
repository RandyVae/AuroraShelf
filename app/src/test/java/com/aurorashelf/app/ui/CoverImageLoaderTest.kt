package com.aurorashelf.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverImageLoaderTest {
    @Test
    fun recognizesCurrentAndLegacyHuangguoCoverHosts() {
        assertTrue(CoverImageLoader.isHuangguoCover("https://pic.zdmhyg.cn/upload/cover.jpg?auth_key=1"))
        assertTrue(CoverImageLoader.isHuangguoCover("https://pic.cuinhri.cn/upload/cover.jpg"))
        assertFalse(CoverImageLoader.isHuangguoCover("https://example.com/cover.jpg"))
    }

    @Test
    fun derivesRefererAndOriginForCoverRequests() {
        assertEquals(
            "https://example.com/video/1" to "https://example.com",
            CoverImageLoader.requestOrigin("https://example.com/video/1"),
        )
        assertEquals(
            "https://example.com:8443/video/1" to "https://example.com:8443",
            CoverImageLoader.requestOrigin("https://example.com:8443/video/1"),
        )
        assertNull(CoverImageLoader.requestOrigin("javascript:alert(1)"))
    }
}
