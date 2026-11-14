import XCTest
@testable import AuroraShelf

final class CoverImageLoaderTests: XCTestCase {
    func testCoverURLNormalizationResolvesProtocolRelativeAndSourceRelativeURLs() throws {
        let baseURL = try XCTUnwrap(URL(string: "https://example.com/catalog/"))
        XCTAssertEqual(CoverImageLoader.normalizeUrl("//cdn.example/image.jpg"), "https://cdn.example/image.jpg")
        XCTAssertEqual(CoverImageLoader.normalizeUrl("../covers/image.jpg", baseURL: baseURL), "https://example.com/covers/image.jpg")
    }

    func testBaoziLegacyCoverHostsAreRewrittenAndTrackingQueryIsRemoved() {
        XCTAssertEqual(
            CoverImageLoader.normalizeUrl("https://static-tw.baozimh.com/cover/book.jpg?token=old"),
            "https://s1.bzcdn.net/cover/book.jpg"
        )
    }

    func testCoverURLNormalizationUnescapesFeedURLsAndQuotesSpaces() throws {
        let baseURL = try XCTUnwrap(URL(string: "https://ohentai.org/"))
        XCTAssertEqual(
            CoverImageLoader.normalizeUrl("video_data\\/Episode%202\\/cover image.png", baseURL: baseURL),
            "https://ohentai.org/video_data/Episode%202/cover%20image.png"
        )
    }

    func testJMImageHostInjectsJMReferer() throws {
        let url = try XCTUnwrap(URL(string: "https://cdn-msp.jmapiproxy1.cc/media/albums/1488548_3x4.jpg"))
        let request = CoverImageLoader.imageRequest(for: url)
        XCTAssertEqual(request.value(forHTTPHeaderField: "Referer"), "https://www.cdnhjk.net/")
    }

    func testDoubanImageHostInjectsDoubanReferer() throws {
        let url = try XCTUnwrap(URL(string: "https://img9.doubanio.com/view/photo/s_ratio_poster/public/p2932533736.jpg"))
        let request = CoverImageLoader.imageRequest(for: url)
        XCTAssertEqual(request.value(forHTTPHeaderField: "Referer"), "https://movie.douban.com/")
    }

    func testDoubanPosterResolverMapsKnownDeadHuavodTitles() {
        XCTAssertEqual(
            DoubanPosterResolver.shared.quickLookup(for: "眩晕"),
            "https://img9.doubanio.com/view/photo/l_ratio_poster/public/p2932533736.jpg"
        )
        XCTAssertEqual(
            DoubanPosterResolver.shared.quickLookup(for: "给阿嬷的情书"),
            "https://img3.doubanio.com/view/photo/l_ratio_poster/public/p2931851430.jpg"
        )
    }

    func testDoubanPosterResolverUpgradesSmallPostersAndRejectsPlaceholders() {
        XCTAssertEqual(
            DoubanPosterResolver.upgradePosterURL("https://img3.doubanio.com/view/photo/s_ratio_poster/public/p2931851430.jpg"),
            "https://img3.doubanio.com/view/photo/l_ratio_poster/public/p2931851430.jpg"
        )
        XCTAssertEqual(
            DoubanPosterResolver.upgradePosterURL("https://img9.doubanio.com/view/photo/m_ratio_poster/public/p2932533736.jpg"),
            "https://img9.doubanio.com/view/photo/l_ratio_poster/public/p2932533736.jpg"
        )
        XCTAssertNil(
            DoubanPosterResolver.upgradePosterURL("https://img1.doubanio.com/cuphead/movie-static/pics/tv_default_small.png")
        )
    }

    @MainActor
    func testHuavodHtmlParserReplacesDeadDbzyCoverWithDoubanPoster() {
        let mockHtml = """
        <div class="public-list-box"><div class="public-list-div"><a class="public-list-exp" href="/voddetail/204158.html" title="眩晕"><img src="https://dbzy.tv/upload/vod/20261016-1/78acd20e94236c8e1e7da746295d5ba4.jpg" /><span class="ft4">6.8</span></a></div></div>
        <div class="public-list-box"><div class="public-list-div"><a class="public-list-exp" href="/voddetail/197690.html" title="给阿嬷的情书"><img src="https://dbzy5.com/upload/vod/20260503-1/ff748615f1f31dc34fc270e3b1f53c99.jpg" /><span class="ft4">9.3</span></a></div></div>
        """
        let videos = VideoRepository.shared.parseHuavodHtml(mockHtml, category: "电影")
        XCTAssertEqual(videos.count, 2)
        XCTAssertEqual(videos[0].title, "眩晕")
        XCTAssertEqual(videos[0].coverUrl, "https://img9.doubanio.com/view/photo/l_ratio_poster/public/p2932533736.jpg")
        XCTAssertEqual(videos[1].title, "给阿嬷的情书")
        XCTAssertEqual(videos[1].coverUrl, "https://img3.doubanio.com/view/photo/l_ratio_poster/public/p2931851430.jpg")
    }

    @MainActor
    func testHuavodParserExtractsNonAdHeroBannersAndCastDetailMetadata() {
        let homeHtml = """
        <div class="swiper-wrapper">
          <div class="swiper-slide">
            <a href="/advertiser" target="_blank">
              <div class="slide-time-img3 slide-time-img3--ad" style="background-image: url('https://ad.example/banner.jpg')"></div>
            </a>
          </div>
          <div class="swiper-slide">
            <a href="/voddetail/204741.html" title="折月亮">
              <div class="slide-time-img3 mask-1" style="background-image: url('https://puui.qpic.cn/media_img/lena/PICf1e680_1800_800/0')"></div>
              <div class="slide-info">
                <div class="slide-info-type"><span>大陆剧</span></div>
                <h3 class="slide-info-title">折月亮</h3>
              </div>
            </a>
          </div>
        </div>
        <div class="public-list-box"><div class="public-list-div"><a class="public-list-exp" href="/voddetail/204741.html" title="折月亮"><img data-src="https://img3.doubanio.com/view/photo/s_ratio_poster/public/p2931851430.jpg" /><span class="ft4">8.9</span></a></div></div>
        """
        let videos = VideoRepository.shared.parseHuavodHtml(homeHtml, category: "全部")
        XCTAssertEqual(videos.count, 1)
        XCTAssertEqual(videos[0].title, "折月亮")
        XCTAssertEqual(videos[0].heroCoverUrl, "https://puui.qpic.cn/media_img/lena/PICf1e680_1800_800/0")
        XCTAssertEqual(videos[0].coverUrl, "https://img3.doubanio.com/view/photo/l_ratio_poster/public/p2931851430.jpg")

        let detailHtml = """
        <meta name="keywords" content="折月亮,主演:林一 #卢昱晓 #李歌洋 #导演:张博昱" />
        <div class="slide-info-remarks"><span>2026</span> &nbsp; <span>大陆</span> &nbsp; 剧情 爱情</div>
        <div class="detail-info">
          <div class="row">导演 : <a href="/search/1.html">张博昱</a></div>
          <div class="row">演员 : <a href="/search/2.html">林一</a> / <a href="/search/3.html">卢昱晓</a></div>
        </div>
        <div id="height_limit" class="text cor3">社恐少女云厘与天才少年傅识则久别重逢并相互治愈的故事。请记住本站永久域名 huavod.com。</div>
        """
        let meta = VideoRepository.parseHuavodDetailMetadata(from: detailHtml)
        XCTAssertEqual(meta?.directors, ["张博昱"])
        XCTAssertEqual(meta?.actors, ["林一", "卢昱晓"])
        XCTAssertEqual(meta?.year, "2026")
        XCTAssertEqual(meta?.area, "大陆")
        XCTAssertEqual(meta?.tags, ["剧情", "爱情"])
        XCTAssertEqual(meta?.synopsis, "社恐少女云厘与天才少年傅识则久别重逢并相互治愈的故事。")
    }

    @MainActor
    func testHuavodParserHandlesTimeTitleFt4AndSlashSeparatedActors() {
        let cardHtml = """
        <div class="public-list-box">
          <div class="public-list-div">
            <a class="public-list-exp" href="/voddetail/204741.html" title="折月亮">
              <img data-src="https://img3.doubanio.com/view/photo/s_ratio_poster/public/p2931851430.jpg" />
            </a>
          </div>
          <div class="public-list-button">
            <a class="time-title hide ft4" href="/voddetail/204741.html" title="折月亮">折月亮</a>
          </div>
        </div>
        """
        let videos = VideoRepository.shared.parseHuavodHtml(cardHtml, category: "电视剧")
        XCTAssertEqual(videos.count, 1)
        XCTAssertEqual(videos.first?.title, "折月亮")
        XCTAssertEqual(videos.first?.rating, "")

        let detailHtml = """
        <div class="detail-info">
          <div class="slide-info hide"><strong class="r6">导演 :</strong><a href="/d/1">张博昱</a></div>
          <div class="slide-info hide"><strong class="r6">演员 :</strong><a href="/a/1">林一</a><span class="slash">/</span><a href="/a/2">卢昱晓</a><span class="slash">/</span><a href="/a/3">李歌洋</a></div>
        </div>
        <div id="height_limit" class="text cor3">浪漫故事。</div>
        """
        let meta = VideoRepository.parseHuavodDetailMetadata(from: detailHtml)
        XCTAssertEqual(meta?.directors, ["张博昱"])
        XCTAssertEqual(meta?.actors, ["林一", "卢昱晓", "李歌洋"])
    }

    func testLiveCoverImageLoadingForHuavodDeadDbzyUrls() async throws {
        // 验证针对华视死链 URL，CoverImageLoader 能通过 DoubanPosterResolver 正确重定向并拉取真实 UIImage
        let img1 = await CoverImageLoader.shared.loadImage(
            from: "https://dbzy.tv/upload/vod/20261016-1/78acd20e94236c8e1e7da746295d5ba4.jpg",
            title: "眩晕"
        )
        XCTAssertNotNil(img1, "《眩晕》必须成功获取到海报封面")
        if let img1 {
            XCTAssertGreaterThan(img1.size.width, 0)
            XCTAssertGreaterThan(img1.size.height, 0)
        }

        let img2 = await CoverImageLoader.shared.loadImage(
            from: "https://dbzy5.com/upload/vod/20260503-1/ff748615f1f31dc34fc270e3b1f53c99.jpg",
            title: "给阿嬷的情书"
        )
        XCTAssertNotNil(img2, "《给阿嬷的情书》必须成功获取到海报封面")
        if let img2 {
            XCTAssertGreaterThan(img2.size.width, 0)
            XCTAssertGreaterThan(img2.size.height, 0)
        }
    }
}
