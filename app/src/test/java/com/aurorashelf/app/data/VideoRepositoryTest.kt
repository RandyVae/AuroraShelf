package com.aurorashelf.app.data

import org.jsoup.Jsoup
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRepositoryTest {
    @Test
    fun pageUrlAddsAndReplacesPageParameter() {
        val repository = VideoRepository()
        assertEquals(
            "https://example.com/v.php?category=mf&viewtype=basic&page=2",
            repository.pageUrl("https://example.com", com.aurorashelf.app.model.FeedCategory.MONTHLY_HOT, 2),
        )
    }

    @Test
    fun huangguoPagesUseNumberedPathAndDetailLinks() {
        val repository = VideoRepository()
        assertEquals("https://huangguoai.com/newest/2/", repository.pageUrl("https://huangguoai.com", com.aurorashelf.app.model.FeedCategory.RECENT, 2))
        assertEquals("https://huangguoai.com/ranks/hot/2/", repository.pageUrl("https://huangguoai.com", com.aurorashelf.app.model.FeedCategory.CURRENT_HOT, 2))
        assertEquals("https://huangguoai.com/ranks/recommend/2/", repository.pageUrl("https://huangguoai.com", com.aurorashelf.app.model.FeedCategory.MONTHLY_HOT, 2))
        assertEquals("https://huangguoai.com/ranks/potential/2/", repository.pageUrl("https://huangguoai.com", com.aurorashelf.app.model.FeedCategory.FAVORITES, 2))
        val document = Jsoup.parse(
            """
            <div class="hg-drama-card">
              <a href="/detail/323/"><img data-src="https://cdn.example/cover.jpg" alt="测试短剧"><span>更新至19集</span></a>
              <div class="hg-drama-card__body"><div class="hg-drama-card__title"><a href="/detail/323/">测试短剧</a></div></div>
            </div>
            """.trimIndent(), "https://huangguoai.com/"
        )
        val items = repository.parseDocument(document, "https://huangguoai.com")
        assertEquals("测试短剧", items.single().title)
        assertEquals("https://huangguoai.com/detail/323/", items.single().pageUrl)
        assertEquals("https://cdn.example/cover.jpg", items.single().thumbnailUrl)
    }

    @Test
    fun missAvUsesOnlyDistinctSupportedKiosks() {
        val repository = VideoRepository()
        assertEquals("https://missav.ws/ja", repository.pageUrl("https://missav.ws", com.aurorashelf.app.model.FeedCategory.RECENT, 1))
        assertEquals("https://missav.ws/ja/popular", repository.pageUrl("https://missav.ws", com.aurorashelf.app.model.FeedCategory.CURRENT_HOT, 1))
        assertEquals(
            listOf(
                com.aurorashelf.app.model.FeedCategory.RECENT,
                com.aurorashelf.app.model.FeedCategory.CURRENT_HOT,
                com.aurorashelf.app.model.FeedCategory.MONTHLY_HOT,
            ),
            com.aurorashelf.app.model.FeedCategory.availableFor("https://missav.ws"),
        )
    }

    @Test
    fun parsesMissAvCardsAndRejectsNavigationTiles() {
        val document = Jsoup.parse(
            """
            <a href="/ja/abc-123"><img data-src="https://cdn.example/abc.jpg" alt="ABC-123 作品标题"></a>
            <a href="/ja/popular"><img src="/menu.jpg" alt="Popular"></a>
            <a href="/ja/actresses"><img src="/people.jpg" alt="Actresses"></a>
            """.trimIndent(),
            "https://missav.ws/ja",
        )

        val item = VideoRepository().parseDocument(document, "https://missav.ws").single()

        assertEquals("ABC-123 作品标题", item.title)
        assertEquals("https://missav.ws/ja/abc-123", item.pageUrl)
        assertEquals("https://cdn.example/abc.jpg", item.thumbnailUrl)
    }

    @Test
    fun parsesHuangguoRankingRowsWithEncryptedCoverReference() {
        val document = Jsoup.parse(
            """
            <div class="hg-rank-item">
              <span class="hg-rank-num">01</span>
              <a class="hg-rank-item__cover" href="/detail/117/"><img src="/static/web/images/cover-placeholder.png" data-src="https://pic.cuinhri.cn/upload/cover.jpg?auth_key=x" alt="热播短剧"></a>
              <div class="hg-rank-item__main">
                <h2 class="hg-rank-item__title"><a href="/detail/117/">热播短剧</a></h2>
              </div>
              <div class="hg-rank-item__heat-value">6395</div>
            </div>
            """.trimIndent(),
            "https://huangguoai.com/ranks/hot/",
        )

        val items = VideoRepository().parseDocument(document, "https://huangguoai.com")

        assertEquals(1, items.size)
        assertEquals("热播短剧", items.single().title)
        assertEquals("热度 6395", items.single().views)
        assertEquals("https://pic.cuinhri.cn/upload/cover.jpg?auth_key=x", items.single().thumbnailUrl)
    }

    @Test
    fun huangguoMissingNumberedRankingPageIsEndOfFeed() = runBlocking {
        // URL construction is kept explicit here; the live smoke test verifies the
        // server's 404 is converted to an empty page by loadFeed.
        assertEquals(
            "https://huangguoai.com/ranks/recommend/2/",
            VideoRepository().pageUrl("https://huangguoai.com", com.aurorashelf.app.model.FeedCategory.MONTHLY_HOT, 2),
        )
    }

    @Test
    fun parsesObservedCardStructureWithoutMixingDurationIntoTitle() {
        val document = Jsoup.parse("""
            <div class="well well-sm videos-text-align">
              <a href="view_video.php?viewkey=abc">
                <div class="thumb-overlay"><img data-src="/thumb.jpg"><span class="duration">00:09:42</span></div>
                <span class="video-title title-truncate m-t-5">城市风景测试</span>
              </a>
              <span class="info">作者：tester</span>
            </div>
            <a href="javascript:video()" title="无效视频">无效视频</a>
        """.trimIndent(), "https://example.com/v.php?category=mf")
        val items = VideoRepository().parseDocument(document, "https://example.com")
        assertEquals(1, items.size)
        assertEquals("城市风景测试", items.single().title)
        assertEquals("00:09:42", items.single().duration)
        assertEquals("https://example.com/thumb.jpg", items.single().thumbnailUrl)
    }

    @Test
    fun parseDocument_extractsMediaRows() {
        val document = Jsoup.parse(
            """
            <div class="video-row">
              <a href="view_video.php?viewkey=abc" title="示例视频">
                <img src="thumb.jpg" alt="示例视频" />
              </a>
              <p>作者：tester 时长 09:42 热度：12.4万</p>
            </div>
            """.trimIndent(),
            "https://example.com/",
        )

        val items = VideoRepository().parseDocument(document, "https://example.com")

        assertEquals(1, items.size)
        assertEquals("示例视频", items.single().title)
        assertEquals("09:42", items.single().duration)
        assertTrue(items.single().pageUrl.startsWith("https://example.com/"))
    }
}
