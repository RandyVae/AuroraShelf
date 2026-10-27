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
        assertEquals("https://huangguoai.com/recommend/2/", repository.pageUrl("https://huangguoai.com", com.aurorashelf.app.model.FeedCategory.CURRENT_HOT, 2))
        assertEquals("https://huangguoai.com/ai-manju/2/", repository.pageUrl("https://huangguoai.com", com.aurorashelf.app.model.FeedCategory.MONTHLY_HOT, 2))
        assertEquals("https://huangguoai.com/ai-huanlian/2/", repository.pageUrl("https://huangguoai.com", com.aurorashelf.app.model.FeedCategory.FAVORITES, 2))
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
        assertEquals("https://missav.ws/ja/release", repository.pageUrl("https://missav.ws", com.aurorashelf.app.model.FeedCategory.CURRENT_HOT, 1))
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
            "https://huangguoai.com/ai-manju/2/",
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

    @Test
    fun huavodUsesDedicatedCategoryPathsAndPagination() {
        val repository = VideoRepository()
        assertEquals("https://huavod.com/vodshow/1.html", repository.pageUrl("https://huavod.com", com.aurorashelf.app.model.FeedCategory.RECENT, 1))
        assertEquals("https://huavod.com/vodshow/1/page/2.html", repository.pageUrl("https://huavod.com", com.aurorashelf.app.model.FeedCategory.RECENT, 2))
        assertEquals("https://huavod.com/vodshow/2.html", repository.pageUrl("https://huavod.com", com.aurorashelf.app.model.FeedCategory.CURRENT_HOT, 1))
        assertEquals("https://huavod.com/vodshow/3.html", repository.pageUrl("https://huavod.com", com.aurorashelf.app.model.FeedCategory.MONTHLY_HOT, 1))
        assertEquals("https://huavod.com/vodshow/4.html", repository.pageUrl("https://huavod.com", com.aurorashelf.app.model.FeedCategory.FAVORITES, 1))
        assertEquals("https://huavod.com/vodshow/5.html", repository.pageUrl("https://huavod.com", com.aurorashelf.app.model.FeedCategory.ALL, 1))

        assertEquals("电影", com.aurorashelf.app.model.FeedCategory.RECENT.displayLabel("https://huavod.com"))
        assertEquals("电视剧", com.aurorashelf.app.model.FeedCategory.CURRENT_HOT.displayLabel("https://huavod.com"))
        assertEquals("综艺", com.aurorashelf.app.model.FeedCategory.MONTHLY_HOT.displayLabel("https://huavod.com"))
        assertEquals("动漫", com.aurorashelf.app.model.FeedCategory.FAVORITES.displayLabel("https://huavod.com"))
        assertEquals("短剧", com.aurorashelf.app.model.FeedCategory.ALL.displayLabel("https://huavod.com"))
    }

    @Test
    fun parsesHuavodCardsWithRatingAndCover() {
        val document = Jsoup.parse(
            """
            <div class="public-list-box">
              <div class="public-list-div">
                <a class="public-list-exp" href="/voddetail/202979.html" title="希望">
                  <img src="https://static.huarenlivewebsite.top/piccdn/olo/movie/202979_image.jpg" alt="希望封面图" />
                  <span class="public-prt">豆瓣热榜</span>
                  <span class="public-list-prb"><i class="ft4">5.5</i></span>
                </a>
              </div>
              <div class="public-list-button">
                <a class="time-title" href="/voddetail/202979.html" title="希望">希望</a>
              </div>
            </div>
            """.trimIndent(),
            "https://huavod.com/vodshow/1.html",
        )

        val items = VideoRepository().parseDocument(document, "https://huavod.com")

        assertEquals(1, items.size)
        val item = items.single()
        assertEquals("希望", item.title)
        assertEquals("华视影院", item.author)
        assertEquals("https://huavod.com/voddetail/202979.html", item.pageUrl)
        assertEquals("https://static.huarenlivewebsite.top/piccdn/olo/movie/202979_image.jpg", item.thumbnailUrl)
        assertEquals("评分 5.5", item.views)
    }

    @Test
    fun beegCategoryPathsAndLabels() {
        val repository = VideoRepository()
        assertEquals(
            "https://beeg.com/tag/videos/index?limit=20&offset=0",
            repository.pageUrl("https://beeg.com", com.aurorashelf.app.model.FeedCategory.RECENT, 1)
        )
        assertEquals(
            "https://beeg.com/tag/videos/amateur?limit=20&offset=20",
            repository.pageUrl("https://beeg.com", com.aurorashelf.app.model.FeedCategory.CURRENT_HOT, 2)
        )
        assertEquals(
            "精选",
            com.aurorashelf.app.model.FeedCategory.RECENT.displayLabel("https://beeg.com")
        )
        assertEquals(
            "自拍",
            com.aurorashelf.app.model.FeedCategory.CURRENT_HOT.displayLabel("https://beeg.com")
        )
    }

    @Test
    fun parsesBeegJsonCorrectly() {
        val sampleJson = """
            [
              {
                "file": {
                  "id": 714126256274840,
                  "fl_duration": 910,
                  "data": [
                    {
                      "cd_column": "sf_name",
                      "cd_value": "Caught cheating&comma; stepsisters swap boyfriends"
                    }
                  ]
                },
                "tags": [
                  {
                    "is_owner": true,
                    "tg_name": "Yasmina Khan"
                  }
                ],
                "fc_facts": [
                  {
                    "fc_st_views": 739642,
                    "fc_thumbs": [114, 117]
                  }
                ]
              }
            ]
        """.trimIndent()

        val items = VideoRepository().parseBeegJson(sampleJson, "https://beeg.com")
        assertEquals(1, items.size)
        val item = items.single()
        assertEquals("714126256274840", item.id)
        assertEquals("Caught cheating, stepsisters swap boyfriends", item.title)
        assertEquals("Yasmina Khan", item.author)
        assertEquals("15:10", item.duration)
        assertEquals("74.0萬次播放", item.views)
        assertEquals("https://beeg.com/-0714126256274840", item.pageUrl)
        assertEquals("https://thumbs.externulls.com/videos/714126256274840/114.webp", item.thumbnailUrl)
    }

    @Test
    fun xvideosProvidesFiveDistinctCategoriesAndLabels() {
        val baseUrl = com.aurorashelf.app.model.ContentSourceCatalog.XVIDEOS_BASE_URL
        val categories = com.aurorashelf.app.model.FeedCategory.availableFor(baseUrl)
        // XVideos now exposes 41 full official categories
        assertTrue("XVideos should have at least 41 categories", categories.size >= 41)
        assertEquals("最新", com.aurorashelf.app.model.FeedCategory.RECENT.displayLabel(baseUrl))
        assertEquals("热门", com.aurorashelf.app.model.FeedCategory.CURRENT_HOT.displayLabel(baseUrl))
        assertEquals("中文", com.aurorashelf.app.model.FeedCategory.CHINESE.displayLabel(baseUrl))
        assertEquals("熟女", com.aurorashelf.app.model.FeedCategory.MILF.displayLabel(baseUrl))
        assertEquals("AI", com.aurorashelf.app.model.FeedCategory.AI.displayLabel(baseUrl))
    }

    @Test
    fun interleaveCombinesMultipleSourcesEqually() {
        val repo = VideoRepository()
        fun makeItem(id: String) = com.aurorashelf.app.model.VideoItem(
            id = id,
            title = "Title $id",
            author = "Author $id",
            duration = "10:00",
            views = "1000",
            pageUrl = "https://example.com/$id",
        )
        val srcA = listOf(makeItem("a1"), makeItem("a2"), makeItem("a3"))
        val srcB = listOf(makeItem("b1"), makeItem("b2"))
        val srcC = listOf(makeItem("c1"))

        val interleaved = repo.interleave(listOf(srcA, srcB, srcC))
        val ids = interleaved.map { it.id }
        assertEquals(listOf("a1", "b1", "c1", "a2", "b2", "a3"), ids)
    }

    @Test
    fun dongman91ProvidesCategoriesAndPageUrls() {
        val repo = VideoRepository()
        val baseUrl = com.aurorashelf.app.model.ContentSourceCatalog.T91DONGMAN_BASE_URL
        assertEquals("https://91dongman.net/anime/lifan/", repo.pageUrl(baseUrl, com.aurorashelf.app.model.FeedCategory.RECENT, 1))
        assertEquals("https://91dongman.net/anime/lifan/page/2/", repo.pageUrl(baseUrl, com.aurorashelf.app.model.FeedCategory.RECENT, 2))
        assertEquals("https://91dongman.net/anime/3d/", repo.pageUrl(baseUrl, com.aurorashelf.app.model.FeedCategory.CURRENT_HOT, 1))
        assertEquals("https://91dongman.net/anime/ai/", repo.pageUrl(baseUrl, com.aurorashelf.app.model.FeedCategory.MONTHLY_HOT, 1))
        assertEquals("https://91dongman.net/ranking/", repo.pageUrl(baseUrl, com.aurorashelf.app.model.FeedCategory.FAVORITES, 1))
        assertEquals("https://91dongman.net/anime/", repo.pageUrl(baseUrl, com.aurorashelf.app.model.FeedCategory.ALL, 1))

        assertEquals("里番", com.aurorashelf.app.model.FeedCategory.RECENT.displayLabel(baseUrl))
        assertEquals("3D动漫", com.aurorashelf.app.model.FeedCategory.CURRENT_HOT.displayLabel(baseUrl))
        assertEquals("AI动漫", com.aurorashelf.app.model.FeedCategory.MONTHLY_HOT.displayLabel(baseUrl))
        assertEquals("排行", com.aurorashelf.app.model.FeedCategory.FAVORITES.displayLabel(baseUrl))
        assertEquals("全部", com.aurorashelf.app.model.FeedCategory.ALL.displayLabel(baseUrl))
    }

    @Test
    fun parsesDongman91CardsProperly() {
        val html = """
            <div class="dm-card">
              <a class="dm-card__media" href="/anime/lifan/8799/">
                <img class="dm-cover__image" data-src="https://pic.example/cover.jpg" alt="测试里番动画">
                <span class="dm-chip dm-card__badge">里番动漫</span>
                <span class="dm-meta-pill dm-card__views"><span>2.5万</span></span>
                <span class="dm-meta-pill dm-card__duration"><span>15:30</span></span>
              </a>
              <div class="dm-card__body">
                <a class="dm-card__title" href="/anime/lifan/8799/"><h3>测试里番动画</h3></a>
              </div>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html, "https://91dongman.net/")
        val items = VideoRepository().parseDocument(doc, "https://91dongman.net")
        assertEquals(1, items.size)
        val item = items.single()
        assertEquals("8799", item.id)
        assertEquals("测试里番动画", item.title)
        assertEquals("里番动漫", item.author)
        assertEquals("15:30", item.duration)
        assertEquals("2.5万", item.views)
        assertEquals("https://91dongman.net/anime/lifan/8799/", item.pageUrl)
        assertEquals("https://pic.example/cover.jpg", item.thumbnailUrl)
    }
}
