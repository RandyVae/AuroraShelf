package com.aurorashelf.app.ui

import com.aurorashelf.app.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Test

class AuroraViewModelTest {
    @Test
    fun localSearchMatchesTitleAndAuthorAndDropsDuplicates() {
        val videos = listOf(
            video("1", "海边旅行", "阿岚"),
            video("2", "山间徒步", "旅行频道"),
            video("3", "无关内容", "别人"),
            video("2", "山间徒步", "旅行频道"),
        )

        val matches = AuroraViewModel.localSearchMatches("旅行", videos)

        assertEquals(listOf("1", "2"), matches.map(VideoItem::id))
    }

    @Test
    fun localSearchMatchesIsCaseInsensitive() {
        val videos = listOf(video("1", "Beach Vlog", "ALAN"))

        assertEquals(listOf("1"), AuroraViewModel.localSearchMatches("beach", videos).map(VideoItem::id))
        assertEquals(listOf("1"), AuroraViewModel.localSearchMatches("alan", videos).map(VideoItem::id))
    }

    @Test
    fun localSearchMatchesReturnsEmptyWhenNothingMatches() {
        val videos = listOf(video("1", "海边旅行", "阿岚"))

        assertEquals(emptyList<VideoItem>(), AuroraViewModel.localSearchMatches("雪山", videos))
    }

    private fun video(id: String, title: String, author: String) = VideoItem(
        id = id,
        title = title,
        author = author,
        duration = "",
        views = "",
        pageUrl = "https://example.com/video/$id",
    )
}
