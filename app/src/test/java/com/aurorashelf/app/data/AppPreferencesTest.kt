package com.aurorashelf.app.data

import com.aurorashelf.app.model.VideoItem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun feedCacheRoundTripsSourceCategoryAndVideos() {
        val feed = CachedFeed(
            sourceUrl = "https://example.com",
            categoryPath = "v.php?category=mf",
            videos = listOf(video("video-1"), video("video-2")),
            savedAtMillis = 1_700_000_000_000L,
        )

        val decoded = requireNotNull(decodeFeedCache(encodeFeedCache(feed)))

        assertEquals(feed.sourceUrl, decoded.sourceUrl)
        assertEquals(feed.categoryPath, decoded.categoryPath)
        assertEquals(feed.savedAtMillis, decoded.savedAtMillis)
        assertEquals(listOf("video-1", "video-2"), decoded.videos.map(VideoItem::id))
        assertEquals("标题 video-2", decoded.videos[1].title)
    }

    @Test
    fun feedCacheDropsBrokenEntriesAndKeepsValidOnes() {
        val payload = JSONObject()
            .put("sourceUrl", "https://example.com")
            .put("categoryPath", "v.php?category=hot")
            .put("savedAt", 123L)
            .put(
                "videos",
                JSONArray()
                    .put(validRecord("ok-1", "https://example.com/video/1"))
                    .put("not-an-object")
                    .put(JSONObject.NULL)
                    .put(JSONObject().put("id", "missing-title").put("pageUrl", "https://example.com/video/2"))
                    .put(validRecord("demo-1", "https://example.com/demo"))
                    .put(validRecord("bad-url", "not a url"))
                    .put(validRecord("ok-2", "https://example.com/video/2")),
            )

        val decoded = requireNotNull(decodeFeedCache(payload.toString()))

        assertEquals(listOf("ok-1", "ok-2"), decoded.videos.map(VideoItem::id))
    }

    @Test
    fun feedCacheRejectsInvalidOrEmptyPayloads() {
        assertNull(decodeFeedCache(""))
        assertNull(decodeFeedCache("not json"))
        assertNull(decodeFeedCache(JSONObject().put("sourceUrl", "https://example.com").toString()))
        assertNull(
            decodeFeedCache(
                JSONObject()
                    .put("sourceUrl", "https://example.com")
                    .put("categoryPath", "v.php?category=hot")
                    .put("videos", JSONArray())
                    .toString(),
            ),
        )
        assertNull(
            decodeFeedCache(
                JSONObject()
                    .put("sourceUrl", "")
                    .put("categoryPath", "v.php?category=hot")
                    .put("videos", JSONArray().put(validRecord("x", "https://example.com/x")))
                    .toString(),
            ),
        )
    }

    @Test
    fun feedCacheCapsStoredItems() {
        val videos = (1..AppPreferences.MAX_FEED_CACHE_ITEMS + 10).map { video("video-$it") }
        val feed = CachedFeed("https://example.com", "v.php?category=hot", videos, 1L)

        val decoded = requireNotNull(decodeFeedCache(encodeFeedCache(feed)))

        assertEquals(AppPreferences.MAX_FEED_CACHE_ITEMS, decoded.videos.size)
    }

    private fun video(id: String) = VideoItem(
        id = id,
        title = "标题 $id",
        author = "作者",
        duration = "01:00",
        views = "10",
        pageUrl = "https://example.com/video/$id",
    )

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
