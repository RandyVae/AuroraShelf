package com.aurorashelf.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesTest {
    @Test
    fun defaultComicSource_isJm() {
        assertEquals("jm", AppPreferences.DEFAULT_COMIC_SOURCE_ID)
    }

    @Test
    fun normalizeBaseUrl_trimsWhitespaceAndTrailingSlash() {
        assertEquals("https://example.com", AppPreferences.normalizeBaseUrl("  https://example.com///  "))
    }
}
