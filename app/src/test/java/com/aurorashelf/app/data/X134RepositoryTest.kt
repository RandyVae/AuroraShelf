package com.aurorashelf.app.data

import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.FeedCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class X134RepositoryTest {
    @Test
    fun encodesAndDecodesSerovalPayloadsIncludingBackreferences() {
        val encoded = X134Repository.encodePayload(
            mapOf(
                "data" to mapOf(
                    "page" to 2,
                    "ranking" to "popular",
                    "cursor" to "abc==",
                    "flag" to true,
                    "empty" to null,
                )
            )
        )
        val rawEnvelope = org.json.JSONObject(encoded)
        assertEquals(127, rawEnvelope.getInt("f"))
        val decodedRequest = X134Repository.decodeSeroval(rawEnvelope.getJSONObject("t").toString()) as Map<*, *>
        val innerData = decodedRequest["data"] as Map<*, *>
        assertEquals(2, (innerData["page"] as Number).toInt())
        assertEquals("popular", innerData["ranking"])
        assertEquals(true, innerData["flag"])
        assertNull(innerData["empty"])

        // Simulate realistic serverFn envelope containing t=11 and t=4 backreferences
        val serverResponse = """
            {
              "t": 10,
              "i": 0,
              "p": {
                "k": ["result", "error"],
                "v": [
                  {
                    "t": 11,
                    "i": 1,
                    "p": {
                      "k": ["tweets", "shared", "alias", "flag", "empty"],
                      "v": [
                        {
                          "t": 9,
                          "i": 2,
                          "a": [
                            {"t": 1, "s": "item-1\\nline2 \\\"ok\\\" &amp; \\u4e2d"}
                          ]
                        },
                        {"t": 10, "i": 3, "p": {"k": ["id"], "v": [{"t": 0, "s": 42}]}},
                        {"t": 4, "i": 3},
                        {"t": 2, "s": 2},
                        {"t": 2, "s": 0}
                      ]
                    }
                  },
                  {"t": 2, "s": 1}
                ]
              }
            }
        """.trimIndent()

        val decoded = X134Repository.decodeSeroval(serverResponse) as Map<*, *>
        assertNull(decoded["error"])
        val result = decoded["result"] as Map<*, *>
        assertEquals(listOf("item-1\nline2 \"ok\" &amp; 中"), result["tweets"])
        assertEquals("item-1 line2 \"ok\" & 中", X134Repository.cleanTweetTitle(result["tweets"].let { (it as List<*>)[0] as String }, "fallback"))
        assertEquals(42, ((result["shared"] as Map<*, *>)["id"] as Number).toInt())
        assertEquals(result["shared"], result["alias"])
        assertEquals(true, result["flag"])
        assertNull(result["empty"])
        assertTrue(innerData.isNotEmpty())
    }

    @Test
    fun extractsOrderedServerFnHashesFromAppShellBundle() {
        val h = (0 until 11).map { idx -> idx.toString(16).repeat(64).take(64) }
        val snippet = """
            var Ma=${'$'}({method:`GET`}).handler(Y(`${h[0]}`)),
                Na=${'$'}({method:`GET`}).handler(Y(`${h[1]}`)),
                Pa=${'$'}({method:`GET`}).handler(Y(`${h[2]}`)),
                Fa=${'$'}({method:`GET`}).handler(Y(`${h[3]}`)),
                Ia=${'$'}({method:`GET`}).handler(Y(`${h[4]}`)),
                La=${'$'}({method:`GET`}).handler(Y(`${h[5]}`)),
                Ra=${'$'}({method:`GET`}).handler(Y(`${h[6]}`)),
                za=${'$'}({method:`GET`}).handler(Y(`${h[7]}`));
        """.trimIndent()

        val extracted = X134Repository.extractHashesFromAppShell(snippet)
        assertNotNull(extracted)
        assertEquals(h[1], extracted?.latest)
        assertEquals(h[3], extracted?.ranking)
        assertEquals(h[4], extracted?.search)
        assertEquals(h[7], extracted?.detail)
    }

    @Test
    fun parsesFeedItemsWithCleanTitlesDurationViewsAndPosterMerge() {
        val tweets = listOf(
            mapOf(
                "tweetId" to "2121514973625200843",
                "name" to "中文字幕 AV",
                "screenName" to "CU_JAV",
                "profileImage" to "https://pbs.twimg.com/profile_images/1/avatar_normal.png",
                "text" to "精品长片测试\n第二行 https://t.co/bvz3w78efg",
                "durationMillis" to 4_558_848L,
                "playTimes" to 12_500L,
            ),
            mapOf(
                "tweetId" to "2121514218914808037",
                "name" to "",
                "screenName" to "only_handle",
                "profileImage" to "https://pbs.twimg.com/profile_images/2/avatar_normal.jpg",
                "text" to "https://t.co/onlylink",
                "durationMillis" to 725_000L,
                "playTimes" to 0L,
            ),
        )
        val meta = mapOf(
            "2121514973625200843" to X134Repository.TweetMetadata(
                posterUrl = "https://pbs.twimg.com/amplify_video_thumb/2052979519930441728/img/poster.jpg",
                hlsUrl = "https://video.twimg.com/amplify_video/2052979519930441728/pl/master.m3u8",
                mp4Url = "https://video.twimg.com/amplify_video/2052979519930441728/vid/1280x720/a.mp4",
            )
        )

        val items = X134Repository.parseFeedItems(tweets, meta)
        assertEquals(2, items.size)

        val first = items[0]
        assertEquals("134x-2121514973625200843", first.id)
        assertEquals("精品长片测试 第二行", first.title)
        assertEquals("中文字幕 AV", first.author)
        assertEquals("1:15:58", first.duration)
        assertEquals("1.3万 次播放", first.views)
        assertEquals("https://134x.com/video/2121514973625200843", first.pageUrl)
        assertEquals("https://pbs.twimg.com/amplify_video_thumb/2052979519930441728/img/poster.jpg", first.thumbnailUrl)

        val second = items[1]
        assertEquals("@only_handle", second.title)
        assertEquals("12:05", second.duration)
        assertEquals("长视频 · 134X", second.views)
        assertEquals("https://pbs.twimg.com/profile_images/2/avatar_400x400.jpg", second.thumbnailUrl)
    }

    @Test
    fun registers134xCatalogCategoriesAndSingleEpisode() = runBlocking {
        assertTrue(ContentSourceCatalog.is134x(ContentSourceCatalog.X134_BASE_URL))
        assertEquals("134X", ContentSourceCatalog.inferSourceName("https://134x.com/video/2121514973625200843"))
        assertEquals(
            listOf(FeedCategory.RECENT, FeedCategory.CURRENT_HOT, FeedCategory.MONTHLY_HOT),
            FeedCategory.availableFor(ContentSourceCatalog.X134_BASE_URL),
        )
        assertEquals("最新", FeedCategory.RECENT.displayLabel(ContentSourceCatalog.X134_BASE_URL))
        assertEquals("热门", FeedCategory.CURRENT_HOT.displayLabel(ContentSourceCatalog.X134_BASE_URL))
        assertEquals("精选", FeedCategory.MONTHLY_HOT.displayLabel(ContentSourceCatalog.X134_BASE_URL))
        assertEquals("https://134x.com/popular?page=2", VideoRepository().pageUrl(ContentSourceCatalog.X134_BASE_URL, FeedCategory.CURRENT_HOT, 2))
        assertEquals("https://134x.com/featured?page=3", VideoRepository().pageUrl(ContentSourceCatalog.X134_BASE_URL, FeedCategory.MONTHLY_HOT, 3))

        val episodes = PlaybackResolver().resolveEpisodes("https://134x.com/video/2121514973625200843")
        assertEquals(1, episodes.size)
        assertEquals("正片", episodes.single().label)
    }

    @Test
    fun probesLive134xFeedsSearchAndPlaybackWhenOptedIn() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE_SOURCE_TESTS") == "1")
        val repo = VideoRepository()
        val resolver = PlaybackResolver()

        val latest = repo.loadFeed(ContentSourceCatalog.X134_BASE_URL, FeedCategory.RECENT, 1)
        assertTrue("Expected non-empty latest feed", latest.isNotEmpty())
        assertTrue(
            "Expected Twitter HD posterUrl in latest feed",
            latest.take(3).any { it.thumbnailUrl?.contains("pbs.twimg.com") == true },
        )

        val latestPage2 = repo.loadFeed(ContentSourceCatalog.X134_BASE_URL, FeedCategory.RECENT, 2)
        assertTrue("Expected page 2 items", latestPage2.isNotEmpty())
        assertTrue((latest.map { it.pageUrl }.toSet() intersect latestPage2.map { it.pageUrl }.toSet()).isEmpty())

        val popular = repo.loadFeed(ContentSourceCatalog.X134_BASE_URL, FeedCategory.CURRENT_HOT, 1)
        assertTrue("Expected non-empty popular feed", popular.isNotEmpty())

        val featured = repo.loadFeed(ContentSourceCatalog.X134_BASE_URL, FeedCategory.MONTHLY_HOT, 1)
        assertTrue("Expected non-empty featured feed", featured.isNotEmpty())

        val searched = repo.search(ContentSourceCatalog.X134_BASE_URL, "中文", 1)
        assertTrue("Expected non-empty search results", searched.isNotEmpty())

        val playback = resolver.resolve(latest.first().pageUrl)
        assertTrue(
            "Expected twimg playback URL, got ${playback.url}",
            playback.url.contains("video.twimg.com") || playback.url.contains("134x.com/api/"),
        )
    }
}
