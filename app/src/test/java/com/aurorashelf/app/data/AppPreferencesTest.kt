package com.aurorashelf.app.data

import com.aurorashelf.app.model.ForumPost
import com.aurorashelf.app.model.VideoItem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun forumCacheRoundTripsPostsAndSavedAt() {
        val cache = CachedForum(
            posts = listOf(forumPost("t66y", "1", 100), forumPost("t906", "2", 200)),
            savedAtMillis = 1_700_000_000_000L,
        )

        val decoded = requireNotNull(decodeForumCache(encodeForumCache(cache)))

        assertEquals(1_700_000_000_000L, decoded.savedAtMillis)
        assertEquals(cache.posts, decoded.posts)
    }

    @Test
    fun forumCacheDropsBrokenRecordsAndKeepsValidOnes() {
        val payload = JSONObject()
            .put("savedAt", 42L)
            .put(
                "posts",
                JSONArray()
                    .put(forumRecord("ok-1", "https://t66y.com/read.php?tid=1"))
                    .put("not-an-object")
                    .put(JSONObject.NULL)
                    .put(JSONObject().put("sourceId", "x").put("id", "missing-title").put("url", "https://example.com/x"))
                    .put(forumRecord("ok-2", "https://t906.abc2507.cc/viewthread.php?tid=2"))
                    .put(forumRecord("bad-url", "not a url"))
                    .put(forumRecord("ok-1", "https://t66y.com/read.php?tid=1")),
            )

        val decoded = requireNotNull(decodeForumCache(payload.toString()))

        assertEquals(listOf("ok-1", "ok-2"), decoded.posts.map(ForumPost::id))
        assertEquals(42L, decoded.savedAtMillis)
    }

    @Test
    fun forumCacheRejectsInvalidPayloads() {
        assertNull(decodeForumCache(""))
        assertNull(decodeForumCache("not json"))
        assertNull(decodeForumCache(JSONObject().put("savedAt", 1L).toString()))
        assertNull(decodeForumCache(JSONObject().put("posts", JSONArray()).toString()))
        assertNull(
            decodeForumCache(
                JSONObject().put("posts", JSONArray().put(forumRecord("bad", "not a url"))).toString(),
            ),
        )
    }

    @Test
    fun forumCacheCapsStoredPostsAtForty() {
        val posts = (1..AppPreferences.MAX_FORUM_CACHE_ITEMS + 10)
            .map { forumPost("t66y", "post-$it", it.toLong()) }

        val decoded = requireNotNull(decodeForumCache(encodeForumCache(CachedForum(posts, 1L))))

        assertEquals(AppPreferences.MAX_FORUM_CACHE_ITEMS, decoded.posts.size)
    }

    @Test
    fun forumCacheFreshnessHonoursTwelveHourTtl() {
        val base = 1_700_000_000_000L

        assertTrue(isForumCacheFresh(base, base))
        assertTrue(isForumCacheFresh(base, base + AppPreferences.FORUM_CACHE_TTL_MS))
        assertFalse(isForumCacheFresh(base, base + AppPreferences.FORUM_CACHE_TTL_MS + 1))
        assertFalse(isForumCacheFresh(0L, base))
        assertFalse("future timestamps are not trusted", isForumCacheFresh(base + 1, base))
    }

    private fun forumPost(sourceId: String, id: String, updatedEpochSeconds: Long) = ForumPost(
        sourceId = sourceId,
        sourceName = sourceId,
        id = id,
        title = "标题 $id",
        author = "作者",
        replyCount = 3,
        updatedLabel = "刚刚",
        updatedEpochSeconds = updatedEpochSeconds,
        url = "https://example.com/$id",
    )

    private fun forumRecord(id: String, url: String): JSONObject = JSONObject().apply {
        put("sourceId", "t66y")
        put("sourceName", "草榴社区")
        put("id", id)
        put("title", "标题 $id")
        put("author", "作者")
        put("replyCount", 1)
        put("updatedLabel", "刚刚")
        put("updatedEpochSeconds", 100L)
        put("url", url)
    }
}
