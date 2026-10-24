package com.aurorashelf.app.data

import org.jsoup.Jsoup
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
}
