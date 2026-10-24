package com.aurorashelf.app.data

import com.aurorashelf.app.model.VideoItem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun decodeSavedVideos_skipsBrokenRecordsAndKeepsValidOnes() {
        val array = JSONArray()
            .put(validRecord("video-1", "https://example.com/video/1"))
            .put("not-an-object")
            .put(JSONObject.NULL)
            .put(JSONObject().put("id", "missing-title").put("pageUrl", "https://example.com/video/2"))
            .put(validRecord("demo-1", "https://example.com/demo"))
            .put(validRecord("bad-url", "not a url"))
            .put(validRecord("video-2", "https://example.com/video/2"))

        val decoded = decodeSavedVideos(array.toString())

        assertEquals(listOf("video-1", "video-2"), decoded.map(VideoItem::id))
        assertEquals("标题 video-1", decoded.first().title)
    }

    @Test
    fun decodeSavedVideos_returnsEmptyForEmptyOrNonJsonPayload() {
        assertTrue(decodeSavedVideos("[]").isEmpty())
        assertTrue(decodeSavedVideos("").isEmpty())
        assertTrue(decodeSavedVideos("not json").isEmpty())
        assertTrue(decodeSavedVideos("null").isEmpty())
    }

    @Test
    fun decodeSavedVideos_keepsAllValidFields() {
        val decoded = decodeSavedVideos(
            JSONArray().put(validRecord("video-9", "https://example.com/video/9")).toString(),
        )

        assertEquals(
            listOf(
                VideoItem(
                    id = "video-9",
                    title = "标题 video-9",
                    author = "作者",
                    duration = "01:00",
                    views = "10",
                    pageUrl = "https://example.com/video/9",
                    thumbnailUrl = null,
                ),
            ),
            decoded,
        )
    }

    private fun validRecord(id: String, pageUrl: String): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", "标题 $id")
        put("author", "作者")
        put("duration", "01:00")
        put("views", "10")
        put("pageUrl", pageUrl)
        put("thumbnailUrl", "")
    }
}
