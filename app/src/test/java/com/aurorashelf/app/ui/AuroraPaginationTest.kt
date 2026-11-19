package com.aurorashelf.app.ui

import com.aurorashelf.app.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.utils.HtmlPagination

class AuroraPaginationTest {
    @Test
    fun distinctNewVideosAppendsOnlyUnseenIdsInSourceOrder() {
        val existing = listOf(video("a"), video("b"))
        val incoming = listOf(video("b"), video("c"), video("c"), video("d"))

        val result = AuroraViewModel.distinctNewVideos(existing, incoming)

        assertEquals(listOf("c", "d"), result.map(VideoItem::id))
    }

    @Test
    fun homeGridKeepsFirstVideoAsHeroAndPairsTheRest() {
        val rows = homeGridRows(listOf(video("hero"), video("a"), video("b"), video("c")))

        assertEquals(listOf(listOf("a", "b"), listOf("c")), rows.map { row -> row.map(VideoItem::id) })
    }

    @Test
    fun initialUiStateSeparatesInitialLoadingFromBackgroundRefresh() {
        val state = AuroraUiState()

        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertFalse(state.isLoadingMore)
        assertTrue(state.canLoadMore)
    }

    @Test
    fun htmlPaginationAcceptsDirectCursorJumpsFromBaseUrl() {
        assertTrue(HtmlPagination.isSafeNext("https://www.xvideos.com/", "https://www.xvideos.com/new/1"))
        assertTrue(HtmlPagination.isSafeNext("https://www.xvideos.com/", "https://www.xvideos.com/new/4"))
        assertTrue(HtmlPagination.isSafeNext("https://www.85po.com/latest-updates/", "https://www.85po.com/latest-updates/3/"))
        assertTrue(HtmlPagination.isSafeNext("https://spankbang.com/new_videos", "https://spankbang.com/new_videos/3/"))
        assertFalse(HtmlPagination.isSafeNext("https://www.xvideos.com/", "https://evil.example.com/new/2"))
    }

    @Test
    fun playbackSpeedLabelFormatsStandardPresetsCleanly() {
        assertEquals("0.5x", formatPlaybackSpeedLabel(0.5f))
        assertEquals("0.75x", formatPlaybackSpeedLabel(0.75f))
        assertEquals("1.0x", formatPlaybackSpeedLabel(1.0f))
        assertEquals("1.25x", formatPlaybackSpeedLabel(1.25f))
        assertEquals("1.5x", formatPlaybackSpeedLabel(1.5f))
        assertEquals("2.0x", formatPlaybackSpeedLabel(2.0f))
    }

    private fun video(id: String) = VideoItem(
        id = id,
        title = "title-$id",
        author = "author",
        duration = "01:00",
        views = "1",
        pageUrl = "https://example.com/$id",
    )
}
