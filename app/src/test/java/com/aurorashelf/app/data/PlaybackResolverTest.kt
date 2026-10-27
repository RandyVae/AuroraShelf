package com.aurorashelf.app.data

import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.*
import org.junit.Test

class PlaybackResolverTest {
    @Test fun resolvesDirectSourceWithoutSelectingAdScript() {
        val page = Jsoup.parse("""<script>var ad='https://ads.example/a.mp4'</script><video><source src='/media/a.mp4'></video>""", "https://example.com/watch")
        assertEquals("https://example.com/media/a.mp4", PlaybackResolver().parse(page)?.url)
    }
    @Test fun decodesPublishedSourcePreservingSignedQueryCharacters() {
        val html = "<source src='https://cdn.example/a.mp4?token=a+b&amp;e=123' type='video/mp4'>"
        val encoded = html.map { "%%%02x".format(it.code) }.joinToString("")
        val page = Jsoup.parse("<video><script>document.write(strencode2(\"$encoded\"));</script></video>", "https://example.com/watch")
        assertEquals("https://cdn.example/a.mp4?token=a+b&e=123", PlaybackResolver().parse(page)?.url)
    }
    @Test fun missingOrUnsafeSourceIsNotReplacedWithSampleMedia() {
        listOf("<video></video>", "<video src='javascript:alert(1)'></video>", "<script>strencode2('nothing')</script>").forEach {
            assertNull(PlaybackResolver().parse(Jsoup.parse(it, "https://example.com/watch")))
        }
    }

    @Test fun resolvesHuangguoDataPlaySource() {
        val page = Jsoup.parse(
            "<div id='cyberPlayer' data-play-src='https://cdn.example/episode.m3u8?token=abc'></div>",
            "https://huangguoai.com/video/323/",
        )
        assertEquals("https://cdn.example/episode.m3u8?token=abc", PlaybackResolver().parse(page)?.url)
    }

    @Test fun resolvesHuangguoInitialJsonSource() {
        val page = Jsoup.parse(
            "<script id='videoInitialData' type='application/json'>{\"videoSrc\":\"https://cdn.example/episode.m3u8?x=1\",\"epPlaySrcs\":{\"1\":\"https://cdn.example/episode.m3u8?x=1\"}}</script>",
            "https://huangguoai.com/video/323/",
        )
        assertEquals("https://cdn.example/episode.m3u8?x=1", PlaybackResolver().parse(page)?.url)
    }

    @Test fun ignoresJsonLdLogoAndFindsMediaContentUrl() {
        val page = Jsoup.parse(
            """<script type='application/ld+json'>{"contentUrl":"https://huangguoai.com/static/web/images/logo-huangguo.png"}</script>
               <script type='application/ld+json'>{"contentUrl":"https://cdn.example/episode.m3u8?token=abc"}</script>""",
            "https://huangguoai.com/video/323/",
        )
        assertEquals("https://cdn.example/episode.m3u8?token=abc", PlaybackResolver().parse(page)?.url)
    }

    @Test fun parsesHuangguoEpisodeLinksFromDetailPage() {
        val page = Jsoup.parse(
            """
            <div class="hg-web-detail__ep-grid">
              <a class="is-active" href="/video/117/" data-ep-id="1">01</a>
              <a href="/video/117/ep-2/" data-ep-id="2">02</a>
              <a href="/video/117/ep-3/" data-ep-id="3">03</a>
            </div>
            """.trimIndent(),
            "https://huangguoai.com/detail/117/",
        )

        val episodes = PlaybackResolver().parseEpisodes(page, "https://huangguoai.com/detail/117/")

        assertEquals(listOf(1, 2, 3), episodes.map { it.number })
        assertEquals("https://huangguoai.com/video/117/ep-2/", episodes[1].pageUrl)
    }

    @Test fun parsesHuavodEpisodeLinksFromDetailPage() {
        val page = Jsoup.parse(
            """
            <div class="anthology-list-play">
              <a href="/vodplay/196089-1-1.html">20260404(第1期上)</a>
              <a href="/vodplay/196089-1-2.html">20260405(第1期下)</a>
              <a href="/vodplay/196089-1-3.html">20260406(第1期加更)</a>
            </div>
            """.trimIndent(),
            "https://huavod.com/voddetail/196089.html",
        )

        val episodes = PlaybackResolver().parseHuavodEpisodes(page, "https://huavod.com/voddetail/196089.html")

        assertEquals(listOf(1, 2, 3), episodes.map { it.number })
        assertEquals("20260404(第1期上)", episodes[0].label)
        assertEquals("https://huavod.com/vodplay/196089-1-2.html", episodes[1].pageUrl)
    }

    @Test fun resolvesMissAvEscapedHlsMetadata() {
        val page = Jsoup.parse(
            "<script>const source='https:\\/\\/media.example\\/abc\\/playlist.m3u8?token=123';</script>",
            "https://missav.ws/ja/abc-123",
        )

        assertEquals(
            "https://media.example/abc/playlist.m3u8?token=123",
            PlaybackResolver().parse(page)?.url,
        )
    }

    @Test fun rejectsMissAvImageUrlsAsPlaybackSources() {
        val page = Jsoup.parse(
            "<script>const cover='https://media.example/abc/cover.jpg';</script>",
            "https://missav.ws/ja/abc-123",
        )
        assertNull(PlaybackResolver().parse(page))
    }

    @Test fun cachedValueExpiresAfterTtlUsingInjectedClock() {
        var now = 1_000L
        val cache = CachedValue<String>(ttlMs = 10_000L, maxEntries = 8, clock = { now })
        cache.put("key", "value")

        assertEquals("value", cache.get("key"))
        now += 9_999L
        assertEquals("value", cache.get("key"))
        now += 2L
        assertNull("expired entry must be dropped", cache.get("key"))
        assertEquals(0, cache.size())
    }

    @Test fun cachedValueEvictsLeastRecentlyUsedEntries() {
        val cache = CachedValue<String>(ttlMs = 60_000L, maxEntries = 2, clock = { 0L })
        cache.put("a", "1")
        cache.put("b", "2")
        assertEquals("1", cache.get("a")) // touch A so B is least recently used
        cache.put("c", "3")

        assertNull(cache.get("b"))
        assertEquals("1", cache.get("a"))
        assertEquals("3", cache.get("c"))
        assertEquals(2, cache.size())
    }

    @Test fun cachedValueUsesPlaybackCacheParameters() {
        val cache = CachedValue<List<PlaybackEpisode>>(
            ttlMs = PlaybackResolver.CACHE_TTL_MS,
            maxEntries = PlaybackResolver.CACHE_MAX_ENTRIES,
            clock = { 0L },
        )
        val episodes = listOf(PlaybackEpisode(1, "01", "https://example.com/video/1"))
        cache.put("page", episodes)

        assertEquals(episodes, cache.get("page"))
        assertNull(cache.get("other"))
        assertEquals(PlaybackResolver.CACHE_TTL_MS, 10 * 60 * 1000L)
    }

    @Test fun pageFetchCacheReusesDocumentWithinTtlAndExpiresAfterward() {
        var now = 1_000L
        var loads = 0
        val cache = PageFetchCache(ttlMs = 60_000L, maxEntries = 4, clock = { now })
        val load: () -> Document = {
            loads += 1
            Jsoup.parse("<p>load-$loads</p>", "https://example.com/page")
        }

        val first = cache.get("https://example.com/page") { load() }
        val second = cache.get("https://example.com/page") { load() }
        assertSame("same URL within TTL must share the document", first, second)
        assertEquals(1, loads)

        now += 59_999L
        assertSame(first, cache.get("https://example.com/page") { load() })
        assertEquals(1, loads)

        now += 2L
        assertNotSame(first, cache.get("https://example.com/page") { load() })
        assertEquals(2, loads)
        assertEquals(1, cache.size())
    }

    @Test fun pageFetchCacheDoesNotCacheFailures() {
        val cache = PageFetchCache(ttlMs = 60_000L, maxEntries = 4, clock = { 0L })
        var loads = 0
        val failure = IOException("network down")

        val thrown = assertThrows(IOException::class.java) {
            cache.get("https://example.com/page") {
                loads += 1
                throw failure
            }
        }
        assertSame("cached load must surface the original failure", failure, thrown)
        assertEquals(1, loads)
        assertEquals("failed loads must not be cached", 0, cache.size())

        val recovered = cache.get("https://example.com/page") {
            loads += 1
            Jsoup.parse("<p>ok</p>")
        }
        assertEquals("next caller must retry instead of reusing a failure", 2, loads)
        assertEquals(1, cache.size())
        assertEquals("ok", recovered.select("p").text())
    }

    @Test fun pageFetchCacheCoalescesConcurrentLoadsForSameUrl() {
        val cache = PageFetchCache(ttlMs = 60_000L, maxEntries = 4, clock = { 0L })
        val loadStarted = CountDownLatch(1)
        val releaseLoad = CountDownLatch(1)
        val loadCount = AtomicInteger(0)
        val documents = Collections.synchronizedList(mutableListOf<Document>())

        val workers = (1..2).map {
            Thread {
                documents += cache.get("https://example.com/slow") {
                    loadCount.incrementAndGet()
                    loadStarted.countDown()
                    releaseLoad.await(5, TimeUnit.SECONDS)
                    Jsoup.parse("<p>shared</p>")
                }
            }
        }
        workers.forEach { it.start() }
        assertTrue("loader must start", loadStarted.await(5, TimeUnit.SECONDS))
        // Let the second caller join the in-flight fetch before it completes.
        Thread.sleep(100L)
        releaseLoad.countDown()
        workers.forEach { it.join(5_000L) }

        assertEquals("only one fetch may run for a concurrent miss", 1, loadCount.get())
        assertEquals(2, documents.size)
        assertSame("both callers must receive the same document", documents[0], documents[1])
    }

    @Test fun pageFetchCacheEvictsLeastRecentlyUsedEntries() {
        val cache = PageFetchCache(ttlMs = 60_000L, maxEntries = 2, clock = { 0L })
        var loads = 0
        fun load(key: String): Document {
            loads += 1
            return Jsoup.parse("<p>$key</p>")
        }

        cache.get("a") { load("a") }
        cache.get("b") { load("b") }
        cache.get("a") { load("a") } // touch A so B becomes least recently used
        cache.get("c") { load("c") }
        assertEquals(2, cache.size())

        cache.get("b") { load("b") } // B was evicted and must be reloaded (evicts A)
        cache.get("a") { load("a") } // A was evicted by B's insert and must be reloaded
        cache.get("c") { load("c") } // C was evicted by A's insert and must be reloaded
        assertEquals(6, loads)
    }

    @Test fun pageFetchCacheReportsOnlyFreshEntries() {
        var now = 1_000L
        var loads = 0
        val cache = PageFetchCache(ttlMs = 60_000L, maxEntries = 4, clock = { now })

        assertFalse("nothing cached yet", cache.isFresh("https://example.com/page"))
        cache.get("https://example.com/page") { loads += 1; Jsoup.parse("<p>1</p>") }
        assertTrue("cached document is fresh inside the TTL", cache.isFresh("https://example.com/page"))
        assertEquals(1, loads)
        assertFalse(cache.isFresh("https://example.com/other"))

        now += 60_000L
        assertFalse("expired document is not fresh", cache.isFresh("https://example.com/page"))
        assertEquals(1, loads)
    }

    @Test fun pageFetchCacheUsesSharedPageCacheParameters() {
        assertEquals(60_000L, PlaybackResolver.PAGE_CACHE_TTL_MS)
        assertEquals(16, PlaybackResolver.PAGE_CACHE_MAX_ENTRIES)

        var now = 0L
        val cache = PageFetchCache(
            ttlMs = PlaybackResolver.PAGE_CACHE_TTL_MS,
            maxEntries = PlaybackResolver.PAGE_CACHE_MAX_ENTRIES,
            clock = { now },
        )
        var loads = 0
        cache.get("page") { loads += 1; Jsoup.parse("<p>1</p>") }
        now += PlaybackResolver.PAGE_CACHE_TTL_MS - 1
        cache.get("page") { loads += 1; Jsoup.parse("<p>2</p>") }
        assertEquals(1, loads)
        now += 1
        cache.get("page") { loads += 1; Jsoup.parse("<p>3</p>") }
        assertEquals(2, loads)
    }

    @Test fun resolvesBeegEpisodeAsSingleEpisode() = kotlinx.coroutines.runBlocking {
        val episodes = PlaybackResolver().resolveEpisodes("https://beeg.com/-0714126256274840")
        assertEquals(1, episodes.size)
        assertEquals("正片", episodes.single().label)
        assertEquals("https://beeg.com/-0714126256274840", episodes.single().pageUrl)
    }
}
