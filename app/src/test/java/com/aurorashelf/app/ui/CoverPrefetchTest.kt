package com.aurorashelf.app.ui

import com.aurorashelf.app.model.ComicSummary
import com.aurorashelf.app.model.LiveItem
import com.aurorashelf.app.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverPrefetchTest {

    @Test
    fun videoTargetsSkipMissingCoversDedupeAndRespectLimit() {
        val videos = listOf(
            video("1", null),
            video("2", "   "),
            video("3", "https://cdn.test/a.jpg"),
            video("4", "https://cdn.test/a.jpg"),
            video("5", "https://cdn.test/b.jpg"),
            video("6", "https://cdn.test/c.jpg"),
        )

        assertEquals(listOf("3", "5"), videoPrefetchTargets(videos, limit = 2).map(VideoItem::id))
        assertEquals(3, videoPrefetchTargets(videos, limit = 8).size)
    }

    @Test
    fun videoTargetsAreEmptyForEmptyListOrNonPositiveLimit() {
        assertTrue(videoPrefetchTargets(emptyList(), limit = 8).isEmpty())
        assertTrue(videoPrefetchTargets(listOf(video("1", "https://cdn.test/a.jpg")), limit = 0).isEmpty())
        assertTrue(videoPrefetchTargets(listOf(video("1", "https://cdn.test/a.jpg")), limit = -3).isEmpty())
    }

    @Test
    fun comicTargetsSkipMissingCoversDedupeAndRespectLimit() {
        val comics = listOf(
            comic("1", null),
            comic("2", ""),
            comic("3", "https://cdn.test/c1.jpg"),
            comic("4", "https://cdn.test/c1.jpg"),
            comic("5", "https://cdn.test/c2.jpg"),
        )

        assertEquals(listOf("3", "5"), comicPrefetchTargets(comics, limit = 10).map(ComicSummary::id))
        assertEquals(listOf("3"), comicPrefetchTargets(comics, limit = 1).map(ComicSummary::id))
    }

    @Test
    fun liveTargetsSkipBlankThumbnailsDedupeAndRespectLimit() {
        val items = listOf(
            live("1", ""),
            live("2", "https://cdn.test/l1.jpg"),
            live("3", "https://cdn.test/l1.jpg"),
            live("4", "https://cdn.test/l2.jpg"),
        )

        assertEquals(listOf("2", "4"), livePrefetchTargets(items, limit = 8).map(LiveItem::id))
        assertEquals(listOf("2"), livePrefetchTargets(items, limit = 1).map(LiveItem::id))
        assertTrue(livePrefetchTargets(emptyList(), limit = 8).isEmpty())
    }

    @Test
    fun coverMemoryCacheKeyIsStableAndTrimmed() {
        assertEquals("https://cdn.test/a.jpg", coverMemoryCacheKey("  https://cdn.test/a.jpg  "))
        assertEquals(coverMemoryCacheKey("https://cdn.test/a.jpg"), coverMemoryCacheKey("https://cdn.test/a.jpg "))
        assertNull(coverMemoryCacheKey(null))
        assertNull(coverMemoryCacheKey(""))
        assertNull(coverMemoryCacheKey("   "))
        assertFalse(coverMemoryCacheKey("https://cdn.test/a.jpg") == coverMemoryCacheKey("https://cdn.test/b.jpg"))
    }

    @Test
    fun deduplicatorSuppressesRepeatsUntilTtlExpires() {
        var now = 1_000L
        val dedupe = PrefetchDeduplicator(ttlMs = 10_000L, clock = { now })

        assertTrue(dedupe.markStartedIfAbsent("https://cdn.test/a.jpg"))
        assertFalse(dedupe.markStartedIfAbsent("https://cdn.test/a.jpg"))
        assertTrue(dedupe.markStartedIfAbsent("https://cdn.test/b.jpg"))
        assertEquals(2, dedupe.trackedKeys())

        now += 9_999L
        assertFalse(dedupe.markStartedIfAbsent("https://cdn.test/a.jpg"))

        now += 1L
        assertTrue(dedupe.markStartedIfAbsent("https://cdn.test/a.jpg"))
        assertEquals(2, dedupe.trackedKeys())
    }

    private fun video(id: String, thumbnailUrl: String?): VideoItem = VideoItem(
        id = id,
        title = "video-$id",
        author = "author",
        duration = "00:30",
        views = "1",
        pageUrl = "https://source.test/$id",
        thumbnailUrl = thumbnailUrl,
    )

    private fun comic(id: String, coverUrl: String?): ComicSummary = ComicSummary(
        sourceId = "ehentai",
        id = id,
        title = "comic-$id",
        coverUrl = coverUrl,
    )

    private fun live(id: String, thumbnailUrl: String): LiveItem = LiveItem(
        id = id,
        username = "live-$id",
        thumbnailUrl = thumbnailUrl,
        viewersCount = 1,
        countryCode = "CN",
        isHd = false,
        playerUrl = "https://live.test/$id",
    )
}
