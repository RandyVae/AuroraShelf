package com.aurorashelf.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverImageLoaderTest {
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
