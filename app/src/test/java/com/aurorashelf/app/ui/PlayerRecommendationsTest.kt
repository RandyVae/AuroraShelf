package com.aurorashelf.app.ui

import com.aurorashelf.app.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerRecommendationsTest {
    @Test
    fun relatedVideosExcludeCurrentAndDuplicateItemsWhileKeepingOrder() {
        val current = video("current")
        val first = video("first")
        val second = video("second")

        val result = relatedVideos(current, listOf(current, first, first.copy(title = "重复"), second))

        assertEquals(listOf(first, second), result)
    }

    private fun video(id: String) = VideoItem(
        id = id,
        title = id,
        author = "作者",
        duration = "01:00",
        views = "1 次观看",
        pageUrl = "https://example.com/$id",
    )
}
