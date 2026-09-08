package com.aurorashelf.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesTest {
    @Test
    fun normalizeBaseUrl_trimsWhitespaceAndTrailingSlash() {
        assertEquals("https://example.com", AppPreferences.normalizeBaseUrl("  https://example.com///  "))
    }
}
