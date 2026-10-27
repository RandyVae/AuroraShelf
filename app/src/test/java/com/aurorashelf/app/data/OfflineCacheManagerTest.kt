package com.aurorashelf.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

    @Test
    fun metadataRoundTripsThroughCodec() {
        val metadata = OfflineMetadata(
            videoId = "video-1",
            title = "缓存标题",
            episodeLabel = "第 2 集",
            pageUrl = "https://example.com/video/1?ep=2",
            thumbnailUrl = "https://example.com/cover.jpg",
        )

        assertEquals(metadata, OfflineMetadataCodec.decode(OfflineMetadataCodec.encode(metadata)))
    }

    @Test
    fun metadataRoundTripsWithOptionalFieldsMissing() {
        val metadata = OfflineMetadata(
            videoId = "video-2",
            title = "缓存标题",
            episodeLabel = null,
            pageUrl = "https://example.com/video/2",
            thumbnailUrl = null,
        )

        assertEquals(metadata, OfflineMetadataCodec.decode(OfflineMetadataCodec.encode(metadata)))
    }

    @Test
    fun metadataDecodeReturnsNullForInvalidJson() {
        assertNull(OfflineMetadataCodec.decode("not json".toByteArray(Charsets.UTF_8)))
        assertNull(OfflineMetadataCodec.decode(ByteArray(0)))
        assertNull(OfflineMetadataCodec.decode("[1, 2]".toByteArray(Charsets.UTF_8)))
        assertNull(OfflineMetadataCodec.decode("42".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun metadataDecodeReturnsNullForTruncatedPayload() {
        val encoded = OfflineMetadataCodec.encode(
            OfflineMetadata(
                videoId = "video-3",
                title = "缓存标题",
                episodeLabel = "第 3 集",
                pageUrl = "https://example.com/video/3",
                thumbnailUrl = "https://example.com/cover3.jpg",
            ),
        )

        assertNull(OfflineMetadataCodec.decode(encoded.copyOf(encoded.size / 2)))
        assertNull(OfflineMetadataCodec.decode(byteArrayOf('{'.code.toByte())))
    }

    @Test
    fun metadataDecodeUsesSafeDefaultsForMissingFields() {
        val decoded = requireNotNull(
            OfflineMetadataCodec.decode("""{"pageUrl":"https://example.com/video/4"}""".toByteArray(Charsets.UTF_8)),
        )

        assertEquals("", decoded.videoId)
        assertEquals(OfflineMetadataCodec.DEFAULT_TITLE, decoded.title)
        assertNull(decoded.episodeLabel)
        assertEquals("https://example.com/video/4", decoded.pageUrl)
        assertNull(decoded.thumbnailUrl)
    }

    @Test
    fun resolvedDownloadIsRejectedAfterThePendingEntryIsRemoved() {
        assertTrue(OfflineCacheManager.shouldAcceptResolvedDownload("offline-a", setOf("offline-a")))
        assertFalse(OfflineCacheManager.shouldAcceptResolvedDownload("offline-a", emptySet()))
        assertFalse(OfflineCacheManager.shouldAcceptResolvedDownload("offline-a", setOf("offline-b")))
    }

    @Test
    fun minUsableDiskSpaceIs500MB() {
        assertEquals(500L * 1024 * 1024, OfflineCacheManager.MIN_USABLE_DISK_SPACE_BYTES)
    }
}
