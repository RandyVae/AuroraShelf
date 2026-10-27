package com.aurorashelf.app.data.forum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ForumPostCacheTest {

    @Before
    fun setUp() {
        ForumPostCache.clear()
    }

    @Test
    fun rejectsOversizedHtmlToPreventOom() {
        val hugeHtml = "<html>" + "a".repeat(600_000) + "</html>"
        ForumPostCache.put("https://example.com/huge", hugeHtml)
        assertNull(ForumPostCache.get("https://example.com/huge"))
    }

    @Test
    fun cachesReasonablySizedHtml() {
        val normalHtml = "<html><body>" + "test content ".repeat(100) + "</body></html>"
        val url = "https://example.com/thread-123.html"
        ForumPostCache.put(url, normalHtml)
        val cached = ForumPostCache.get(url)
        assertNotNull(cached)
        assertEquals(normalHtml, cached)
        assertTrue(ForumPostCache.sizeBytes() > 0)
    }

    @Test
    fun rejectsTooShortHtml() {
        val tinyHtml = "<html>short</html>"
        ForumPostCache.put("https://example.com/tiny", tinyHtml)
        assertNull(ForumPostCache.get("https://example.com/tiny"))
    }
}
