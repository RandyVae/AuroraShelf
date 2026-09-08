package com.aurorashelf.app.data

import org.junit.Assert.*
import org.junit.Test

class SourceAddressTest {
    @Test fun acceptsValidSourceButRejectsBlankAddress() {
        assertNull(SourceAddress.error("https://example.com"))
        assertNotNull(SourceAddress.error(""))
    }
    @Test fun rejectsMalformedAndCredentialBearingAddresses() {
        listOf("javascript:alert(1)", "file:///tmp/x", "example.com", "https://u:p@example.com", "https://example.com/?token=123")
            .forEach { assertNotNull(it, SourceAddress.error(it)) }
    }
    @Test fun resolvesRelativeAndRejectsActiveContent() {
        assertEquals("https://example.com/thumb.jpg", SourceAddress.resolve("https://example.com/v.php?category=mf", "thumb.jpg"))
        assertNull(SourceAddress.resolve("https://example.com/", "javascript:alert(1)"))
        assertNull(SourceAddress.resolve("https://example.com/", ""))
    }
}
