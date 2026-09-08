package com.aurorashelf.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCacheManagerTest {
    @Test fun stableIdUsesEpisodePageAddress() {
        val first = OfflineCacheManager.idFor("https://huangguoai.com/video/42?ep=1")
        val repeated = OfflineCacheManager.idFor("https://huangguoai.com/video/42?ep=1")
        val second = OfflineCacheManager.idFor("https://huangguoai.com/video/42?ep=2")

        assertEquals(first, repeated)
        assertNotEquals(first, second)
        assertTrue(first.startsWith("offline-"))
    }
}
