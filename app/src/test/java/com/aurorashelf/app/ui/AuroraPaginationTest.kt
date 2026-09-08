package com.aurorashelf.app.ui

import com.aurorashelf.app.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Test

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

    private fun video(id: String) = VideoItem(
        id = id,
        title = "title-$id",
        author = "author",
        duration = "01:00",
        views = "1",
        pageUrl = "https://example.com/$id",
    )
}
