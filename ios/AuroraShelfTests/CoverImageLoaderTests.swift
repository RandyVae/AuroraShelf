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
            "https://img9.doubanio.com/view/photo/s_ratio_poster/public/p2932533736.jpg"
        )
        XCTAssertEqual(
            DoubanPosterResolver.shared.quickLookup(for: "给阿嬷的情书"),
            "https://img3.doubanio.com/view/photo/s_ratio_poster/public/p2931851430.jpg"
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
        XCTAssertEqual(videos[0].coverUrl, "https://img9.doubanio.com/view/photo/s_ratio_poster/public/p2932533736.jpg")
        XCTAssertEqual(videos[1].title, "给阿嬷的情书")
        XCTAssertEqual(videos[1].coverUrl, "https://img3.doubanio.com/view/photo/s_ratio_poster/public/p2931851430.jpg")
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
