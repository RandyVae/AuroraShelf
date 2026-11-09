import XCTest
@testable import AuroraShelf

@MainActor
final class UniversalVideoEngineTests: XCTestCase {
    func testXVideosHTTPFailurePropagatesInsteadOfReturningCuratedItems() {
        XCTAssertThrowsError(
            try UniversalVideoEngine.decodeXVideosFeed(
                data: Data("upstream unavailable".utf8),
                statusCode: 503,
                category: "热播"
            )
        ) { error in
            guard let feedError = error as? VideoFeedError,
                  case .httpStatus(let source, let statusCode) = feedError else {
                return XCTFail("Expected an XVideos HTTP status error, got: \(error)")
            }

            XCTAssertEqual(source, "XVideos")
            XCTAssertEqual(statusCode, 503)
        }
    }

    func testXVideosEmptyMarkupDoesNotCreateSyntheticFeedItems() throws {
        let videos = try UniversalVideoEngine.decodeXVideosFeed(
            data: Data("<html><body>No videos</body></html>".utf8),
            statusCode: 200,
            category: "全部"
        )

        XCTAssertTrue(videos.isEmpty)
    }

    func testXVideosFeedUsesOnlyParsedUpstreamMetadata() throws {
        let markup = #"<div class="thumb-block"><a href="/video.ab123/example/" title="Upstream title"><img data-src="https://cdn.example/cover.jpg"><span class="duration">12:34</span></a></div>"#
        let videos = try UniversalVideoEngine.decodeXVideosFeed(
            data: Data(markup.utf8),
            statusCode: 200,
            category: "自拍"
        )

        XCTAssertEqual(videos.count, 1)
        let video = try XCTUnwrap(videos.first)
        XCTAssertEqual(video.title, "Upstream title")
        XCTAssertEqual(video.coverUrl, "https://cdn.example/cover.jpg")
        XCTAssertEqual(video.playbackUrl, "https://www.xvideos.com/video.ab123/example/")
        XCTAssertEqual(video.duration, "12:34")
        XCTAssertTrue(video.rating.isEmpty)
        XCTAssertFalse(video.isTop10)
        XCTAssertNil(video.rankNumber)
    }

    func testXVideosDecodesHtmlEntitiesInTitle() throws {
        let markup = #"<div class="thumb-block"><a href="/video.xyz/test/" title="Bob&#039;s &amp; Alice&#039;s &quot;Hot&quot; Adventure"><img data-src="https://cdn.example/c.jpg"></a></div>"#
        let videos = try UniversalVideoEngine.decodeXVideosFeed(
            data: Data(markup.utf8),
            statusCode: 200,
            category: "全部"
        )
        XCTAssertEqual(videos.count, 1)
        XCTAssertEqual(videos.first?.title, #"Bob's & Alice's "Hot" Adventure"#)
    }

    func testHTMLSourceAdapterParsesKissJavCardWithLazyThumbnail() throws {
        let html = #"""
        <div class="thumb thumb_rel item"><a href="/video/886874/sample/" title="Source title"></a><img src="data:image/gif;base64,AA" data-original="//cdn.example/thumb.jpg" alt="Source title"></div>
        """#

        let source = try XCTUnwrap(VideoSourceCatalog.allSources.first { $0.id == "kissjav" })
        let videos = HTMLVideoSourceAdapter.parseHTMLFeed(html, source: source, category: "新片")

        XCTAssertEqual(videos.count, 1)
        XCTAssertEqual(videos[0].title, "Source title")
        XCTAssertEqual(videos[0].coverUrl, "https://cdn.example/thumb.jpg")
        XCTAssertEqual(videos[0].playbackUrl, "https://kissjav.li/video/886874/sample/")
    }

    func testHTMLSourceAdapterParsesPornhubDetailLinksAndEscapedAmpersands() throws {
        let html = #"<div class="phimage"><a class="linkVideoThumb" href="/view_video.php?viewkey=abc123" title="Upstream video"><img src="https://cdn.example/thumb.jpg" alt="Upstream video"></a></div>"#
        let source = try XCTUnwrap(VideoSourceCatalog.allSources.first { $0.id == "pornhub" })
        let videos = HTMLVideoSourceAdapter.parseHTMLFeed(html, source: source, category: "全部")

        XCTAssertEqual(videos.count, 1)
        XCTAssertEqual(videos[0].playbackUrl, "https://jp.pornhub.com/view_video.php?viewkey=abc123")
        XCTAssertEqual(videos[0].coverUrl, "https://cdn.example/thumb.jpg")
    }

    func testHTMLSourceAdapterNormalizesEscapedRegionalThumbnailURLs() throws {
        let html = #"<div><a href="/view_video.php?viewkey=abc123" title="Escaped"><img src="https:\/\/pix-fl.phncdn.com\/thumbs\/sample.jpg?x=1&amp;y=2"></a></div>"#
        let videos = HTMLVideoSourceAdapter.parseHTMLFeed(html, source: VideoSourceCatalog.pornhub, category: "全部")

        XCTAssertEqual(videos.first?.coverUrl, "https://pix-fl.phncdn.com/thumbs/sample.jpg?x=1&y=2")
    }

    func testEPORNERPrefersSignedDownloadRedirectOverStaleJSONLDCdn() throws {
        let html = #"<script type="application/ld+json">{"contentUrl":"https://gvideo.eporner.com/id/id.mp4"}</script><a href="/dload/id/480/file-480p.mp4">Download</a>"#
        let pageURL = try XCTUnwrap(URL(string: "https://www.eporner.com/video-id/sample/"))
        XCTAssertEqual(
            HTMLVideoSourceAdapter.extractMediaURL(from: html, pageURL: pageURL)?.absoluteString,
            "https://www.eporner.com/dload/id/480/file-480p.mp4"
        )
    }

    func testHTMLSourceAdapterAcceptsPornhubCanonicalHostOnRegionalFeed() throws {
        let html = #"<div><a href="https://www.pornhub.com/view_video.php?viewkey=abc123" title="Canonical host"><img src="https://cdn.example/thumb.jpg"></a></div>"#
        let source = VideoSourceCatalog.pornhub

        let videos = HTMLVideoSourceAdapter.parseHTMLFeed(html, source: source, category: "全部")

        XCTAssertEqual(videos.map(\.playbackUrl), ["https://www.pornhub.com/view_video.php?viewkey=abc123"])
    }

    func testHTMLSourceAdapterResolvesNativeHLSFromPlayerMetadata() throws {
        let html = #"html5player.setVideoHLS('https:\/\/cdn.example\/stream.m3u8?token=one\\u0026quality=high');"#
        let pageURL = try XCTUnwrap(URL(string: "https://kissjav.li/video/123/example/"))

        let stream = try XCTUnwrap(HTMLVideoSourceAdapter.extractMediaURL(from: html, pageURL: pageURL))

        XCTAssertEqual(stream.absoluteString, "https://cdn.example/stream.m3u8?token=one&quality=high")
    }

    func testHTMLSourceAdapterDoesNotMistakeThumbnailVideoForPlayback() throws {
        let html = #"<img src="https://cdn.example/preview.mp4"><script>var trailer="https://cdn.example/trailer.mp4";</script>"#
        let pageURL = try XCTUnwrap(URL(string: "https://jav.sb/ja/jav/title.html"))

        XCTAssertNil(HTMLVideoSourceAdapter.extractMediaURL(from: html, pageURL: pageURL))
    }

    func testTokyoMotionResolvesSignedVideoSourceEndpoint() throws {
        let pageURL = try XCTUnwrap(URL(string: "https://www.tokyomotion.net/embed/example"))
        let html = #"<video><source src="https://www.tokyomotion.net/vsrc/sd/ba027f6b5df0d2756a4f" type="video/mp4"></video>"#

        XCTAssertEqual(
            HTMLVideoSourceAdapter.extractMediaURL(from: html, pageURL: pageURL)?.absoluteString,
            "https://www.tokyomotion.net/vsrc/sd/ba027f6b5df0d2756a4f"
        )
    }

    func testHTMLSourceAdapterReadsRealPlayerMetadataShapes() throws {
        let pageURL = try XCTUnwrap(URL(string: "https://www.85po.com/ja/v/37156.html"))
        let cases: [(String, String)] = [
            (#"var player = {"video_url":"https://www.85po.com/ja/get_file/1/token.mp4?expires=1"};"#, "https://www.85po.com/ja/get_file/1/token.mp4?expires=1"),
            (#"SendPlay.load({"sources":[{"file":"https://videos.ohentai.org/video.mp4?token=one"}]});"#, "https://videos.ohentai.org/video.mp4?token=one"),
            (#"{"contentUrl":"https://gvideo.eporner.com/video.mp4"}"#, "https://gvideo.eporner.com/video.mp4"),
            (#"{"videoUrl":"https://cdn.example/master.m3u8?token=one"}"#, "https://cdn.example/master.m3u8?token=one")
        ]

        for (html, expected) in cases {
            XCTAssertEqual(HTMLVideoSourceAdapter.extractMediaURL(from: html, pageURL: pageURL)?.absoluteString, expected)
        }
    }

    func testHTMLSourceAdapterDecodesLegacyBase64VideoURL() throws {
        let html = #"var flashvars = { video_url: "aHR0cHM6Ly9raXNzamF2LmxpL2dldF9maWxlLzExL3Rva2Vu" };"#
        let pageURL = try XCTUnwrap(URL(string: "https://kissjav.li/video/123/example/"))

        XCTAssertEqual(
            HTMLVideoSourceAdapter.extractMediaURL(from: html, pageURL: pageURL)?.absoluteString,
            "https://kissjav.li/get_file/11/token"
        )
    }

    func testHTMLSourceAdapterUnpacksLegacyPlayerMediaMetadata() throws {
        let html = #"<script>eval(function(p,a,c,k,e,d){while(c--)if(k[c])p=p.replace(new RegExp('\\b'+c.toString(a)+'\\b','g'),k[c]);return p}('"0":"1"',36,2,'videoUrl|https://cdn.example/master.m3u8'.split('|')))</script>"#
        let pageURL = try XCTUnwrap(URL(string: "https://luluvido.com/e/sample"))

        XCTAssertEqual(
            HTMLVideoSourceAdapter.extractMediaURL(from: html, pageURL: pageURL)?.absoluteString,
            "https://cdn.example/master.m3u8"
        )
    }

    func testHTMLSourceAdapterFindsOnlyApprovedPlayerFrames() throws {
        let html = #"<iframe src="/embed/123/signed"></iframe><iframe src="https://ads.example/player"></iframe>"#
        let pageURL = try XCTUnwrap(URL(string: "https://www.tokyomotion.net/video/123/sample"))

        XCTAssertEqual(
            HTMLVideoSourceAdapter.playerFrameURLs(from: html, pageURL: pageURL, source: VideoSourceCatalog.tokyomotion).map(\.absoluteString),
            ["https://www.tokyomotion.net/embed/123/signed"]
        )
    }

    func testJAVSBParserKeepsAllSameSitePlayerFallbacksInOrder() throws {
        let html = #"<iframe src="/ja/static/player/videojs.html?src=first"></iframe><iframe src="/videojs/300.php"></iframe><iframe src="/videojs/strip.php"></iframe><iframe src="https://ads.example/player"></iframe>"#
        let pageURL = try XCTUnwrap(URL(string: "https://jav.sb/ja/jav/sample.html"))

        XCTAssertEqual(
            HTMLVideoSourceAdapter.playerFrameURLs(from: html, pageURL: pageURL, source: VideoSourceCatalog.javsb).map(\.path),
            ["/ja/static/player/videojs.html", "/videojs/300.php", "/videojs/strip.php"]
        )
    }

    func testMissAVVIPNavigationIsNotParsedAsVideo() {
        let html = #"<a href="/ja/vip" title="VIP"></a><a href="/ja/real-video-title" title="Real video"></a>"#

        let videos = HTMLVideoSourceAdapter.parseHTMLFeed(html, source: VideoSourceCatalog.missav, category: "全部")

        XCTAssertEqual(videos.map(\.title), ["Real video"])
    }

    func testHTMLSourceAdapterParsesRealDetailURLShapesAndRejectsForeignCards() throws {
        let fixtures: [(String, String)] = [
            ("missav", #"<a href="/en/sample-title" title="MissAV title"><img src="/cover.jpg"></a>"#),
            ("kissjav", #"<a href="/video/123/sample/" title="KissJAV title"><img src="/cover.jpg"></a>"#),
            ("85po", #"<a href="/ja/v/37156.html" title="85po title"><img src="/cover.jpg"></a>"#),
            ("pornhub", #"<a href="/view_video.php?viewkey=abc123" title="Pornhub title"><img src="/cover.jpg"></a>"#),
            ("javnoni", #"<a href="/archives/sample-title/" title="JAV-NONI title"><img src="/cover.jpg"></a>"#),
            ("javsb", #"<a href="/ja/jav/sample-title.html" title="JAVSB title"><img src="/cover.jpg"></a>"#),
            ("tokyomotion", #"<a href="/video/7168663/sample" title="Tokyo title"><img src="/cover.jpg"></a>"#),
            ("spankbang", #"<a href="/video/123/sample" title="SpankBang title"><img src="/cover.jpg"></a>"#),
            ("xhamster", #"<a href="/videos/sample-xh123" title="xHamster title"><img src="/cover.jpg"></a>"#),
            ("mrdouga", #"<a href="/video/886817/sample" title="MRDOUGA title"><img src="/cover.jpg"></a>"#),
            ("ohentai", #"<a href="/detail.php?vid=NzI4Mg==" title="OHentai title"><img src="/cover.jpg"></a>"#),
            ("xnxx", #"<a href="/video-abc/sample" title="XNXX title"><img src="/cover.jpg"></a>"#)
        ]

        for (sourceID, fixture) in fixtures {
            let source = try XCTUnwrap(VideoSourceCatalog.allSources.first { $0.id == sourceID })
            let baseURL = HTMLVideoSourceAdapter.baseURL(for: source)
            let html = fixture + #"<a href="https://foreign.example/video-xyz/not-this-source" title="Foreign"><img src="https://foreign.example/cover.jpg"></a>"#
            let videos = HTMLVideoSourceAdapter.parseHTMLFeed(html, source: source, category: "全部", baseURL: baseURL)

            XCTAssertEqual(videos.count, 1, sourceID)
            XCTAssertEqual(videos.first?.sourceName, source.name, sourceID)
        }
    }

    func testXNXXCardUsesSourceSafeThumbnailAttribute() {
        let html = #"<div class="thumb-block video"><div class="thumb"><a href="/video-1hy04769/example"><img data-sfwthumb="https://cdn.example/thumbnail.jpg"></a></div><a class="title" href="/video-1hy04769/example" title="Video title">Video title</a></div>"#

        let videos = HTMLVideoSourceAdapter.parseHTMLFeed(html, source: VideoSourceCatalog.xnxx, category: "全部")

        XCTAssertEqual(videos.count, 1)
        XCTAssertEqual(videos.first?.coverUrl, "https://cdn.example/thumbnail.jpg")
    }

    func testJAVSBParserDoesNotTreatCategoryPagesAsVideoDetails() {
        let html = #"<a href="/ja/javtype/Censored.html" title="Censored category">Censored</a><div><a href="/ja/jav/dandy-479-vol-12-1-1.html" title="JAV title"><img src="/cover.jpg"></a></div>"#

        let videos = HTMLVideoSourceAdapter.parseHTMLFeed(html, source: VideoSourceCatalog.javsb, category: "全部")

        XCTAssertEqual(videos.map(\.title), ["JAV title"])
        XCTAssertTrue(videos.first?.playbackUrl.contains("/ja/jav/") == true)
    }

    func testXNXXFeedUsesLiveBestListRouteInsteadOfEmptyHomeOr404LatestPath() throws {
        let source = VideoSourceCatalog.xnxx

        let allURL = try XCTUnwrap(HTMLVideoSourceAdapter.feedURL(for: source, category: "全部"))

        XCTAssertEqual(allURL.path, "/best")
        XCTAssertEqual(source.categories, ["全部"])
    }

    func testHTMLVideoSourceAdapterPageTwoRoutesMatchSiteConventions() throws {
        // JAVSB uses path pagination; query params cause 404 or empty redirect
        let javsbRank = try XCTUnwrap(HTMLVideoSourceAdapter.feedURL(for: VideoSourceCatalog.javsb, category: "排行", page: 2))
        XCTAssertEqual(javsbRank.path, "/ja/label/rank/by/hits_week/page/2.html")
        XCTAssertNil(javsbRank.query)

        let javsbScore = try XCTUnwrap(HTMLVideoSourceAdapter.feedURL(for: VideoSourceCatalog.javsb, category: "精选", page: 2))
        XCTAssertEqual(javsbScore.path, "/ja/label/rank/by/score/page/2.html")
        XCTAssertNil(javsbScore.query)

        let javsbAll = try XCTUnwrap(HTMLVideoSourceAdapter.feedURL(for: VideoSourceCatalog.javsb, category: "全部", page: 2))
        XCTAssertEqual(javsbAll.path, "/ja/label/rank/by/hits_week/page/2.html")
        XCTAssertNil(javsbAll.query)

        // 85po uses trailing slash path pagination
        let po85Latest = try XCTUnwrap(HTMLVideoSourceAdapter.feedURL(for: VideoSourceCatalog.po85, category: "最近更新", page: 2))
        XCTAssertEqual(po85Latest.path, "/ja/latest-updates/2")
        XCTAssertTrue(po85Latest.absoluteString.hasSuffix("/ja/latest-updates/2/"))
        XCTAssertNil(po85Latest.query)

        let po85Hot = try XCTUnwrap(HTMLVideoSourceAdapter.feedURL(for: VideoSourceCatalog.po85, category: "精选热播", page: 2))
        XCTAssertEqual(po85Hot.path, "/ja/most-popular/2")
        XCTAssertTrue(po85Hot.absoluteString.hasSuffix("/ja/most-popular/2/"))
        XCTAssertNil(po85Hot.query)

        // XNXX uses path indexing
        let xnxxPage2 = try XCTUnwrap(HTMLVideoSourceAdapter.feedURL(for: VideoSourceCatalog.xnxx, category: "全部", page: 2))
        XCTAssertEqual(xnxxPage2.path, "/best/2")
        XCTAssertNil(xnxxPage2.query)

        // JAV-NONI uses WordPress /page/2/ path
        let javnoniPage2 = try XCTUnwrap(HTMLVideoSourceAdapter.feedURL(for: VideoSourceCatalog.javnoni, category: "中文字幕", page: 2))
        XCTAssertEqual(javnoniPage2.path, "/archives/category/uncensored/page/2")
        XCTAssertTrue(javnoniPage2.absoluteString.hasSuffix("/archives/category/uncensored/page/2/"))
        XCTAssertNil(javnoniPage2.query)

        // KissJAV uses path before query
        let kissjavPage2 = try XCTUnwrap(HTMLVideoSourceAdapter.feedURL(for: VideoSourceCatalog.kissjav, category: "新片", page: 2))
        XCTAssertEqual(kissjavPage2.path, "/latest-updates/2")
        XCTAssertTrue(kissjavPage2.absoluteString.hasSuffix("/latest-updates/2/"))

        let kissjavHot = try XCTUnwrap(HTMLVideoSourceAdapter.feedURL(for: VideoSourceCatalog.kissjav, category: "热门", page: 2))
        XCTAssertEqual(kissjavHot.path, "/most-popular/2")
        XCTAssertTrue(kissjavHot.absoluteString.hasPrefix("https://kissjav.li/most-popular/2/?sort_by=video_viewed"))
        XCTAssertEqual(kissjavHot.query, "sort_by=video_viewed")

        // SpankBang uses /hot/2/ and /new_videos/2/
        let spankbangHot = try XCTUnwrap(HTMLVideoSourceAdapter.feedURL(for: VideoSourceCatalog.spankbang, category: "热门", page: 2))
        XCTAssertEqual(spankbangHot.path, "/hot/2")
        XCTAssertTrue(spankbangHot.absoluteString.hasSuffix("/hot/2/"))

        // xHamster uses /best/2
        let xhamsterPage2 = try XCTUnwrap(HTMLVideoSourceAdapter.feedURL(for: VideoSourceCatalog.xhamster, category: "全部", page: 2))
        XCTAssertEqual(xhamsterPage2.path, "/best/2")

        // MissAV uses query pagination
        let missavPage2 = try XCTUnwrap(HTMLVideoSourceAdapter.feedURL(for: VideoSourceCatalog.missav, category: "最新发布", page: 2))
        XCTAssertEqual(missavPage2.path, "/cn/new")
        XCTAssertEqual(missavPage2.query, "page=2")
    }

    func testEPORNERFeedDecoderUsesLiveAPIFields() throws {
        let payload = #"{"videos":[{"id":"abc1","title":"API title","url":"https://www.eporner.com/video-abc1/example/","length_sec":125,"rate":"4.38","default_thumb":{"src":"https://cdn.example/cover.jpg"}}]}"#

        let videos = try UniversalVideoEngine.decodeEPORNERFeed(data: Data(payload.utf8), statusCode: 200, category: "4K 超清")

        XCTAssertEqual(videos.count, 1)
        XCTAssertEqual(videos[0].title, "API title")
        XCTAssertEqual(videos[0].duration, "02:05")
        XCTAssertEqual(videos[0].rating, "4.4")
        XCTAssertEqual(videos[0].coverUrl, "https://cdn.example/cover.jpg")
    }

    func testEverySupportedSourceHasAnExplicitFeedAdapterAndSearchRoute() {
        let catalogIDs = Set(VideoSourceCatalog.allSources.map(\.id))
        XCTAssertEqual(VideoSourceCatalog.supportedSources.count, VideoSourceCatalog.allSources.count)
        XCTAssertEqual(VideoSourceCatalog.supportedSourceIDs, catalogIDs)
        XCTAssertTrue(VideoSourceCatalog.dedicatedAdapterSourceIDs.isDisjoint(with: VideoSourceCatalog.htmlAdapterSourceIDs))
        XCTAssertEqual(VideoSourceCatalog.dedicatedAdapterSourceIDs.union(VideoSourceCatalog.htmlAdapterSourceIDs), catalogIDs)

        for source in VideoSourceCatalog.allSources {
            if VideoSourceCatalog.htmlAdapterSourceIDs.contains(source.id) {
                XCTAssertNotNil(HTMLVideoSourceAdapter.feedURL(for: source, category: "全部"), source.id)
            } else {
                XCTAssertTrue(VideoSourceCatalog.dedicatedAdapterSourceIDs.contains(source.id), source.id)
            }
            XCTAssertEqual(
                HTMLVideoSourceAdapter.searchURL(for: source, query: "test") != nil,
                VideoSourceCatalog.searchableSourceIDs.contains(source.id),
                source.id
            )
        }
        XCTAssertEqual(VideoSourceCatalog.searchableSources.count, 18)
        XCTAssertFalse(VideoSourceCatalog.searchableSourceIDs.contains(VideoSourceCatalog.beeg.id))
    }

    func testDedicatedSearchRoutesMatchEachSourcesAdapterProtocol() throws {
        let cases: [(VideoSourceDefinition, String, String, String?)] = [
            (VideoSourceCatalog.huavod, "/vodsearch/-------------.html", "wd", "测试 搜索"),
            (VideoSourceCatalog.huangguo, "/search", "keyword", "测试 搜索"),
            (VideoSourceCatalog.porn91, "/search_result.php", "search_id", "测试 搜索"),
            (VideoSourceCatalog.dongman91, "/search-result/", "q", "测试 搜索"),
            (VideoSourceCatalog.missav, "/ja/search/测试 搜索", "", "测试 搜索"),
            (VideoSourceCatalog.javsb, "/ja/vod/search.html", "wd", "测试 搜索")
        ]

        for (source, path, queryName, queryValue) in cases {
            let url = try XCTUnwrap(HTMLVideoSourceAdapter.searchURL(for: source, query: queryValue ?? ""))
            let components = try XCTUnwrap(URLComponents(url: url, resolvingAgainstBaseURL: false))
            if source.id == VideoSourceCatalog.missav.id {
                XCTAssertEqual(components.path, path, source.id)
                XCTAssertTrue(url.absoluteString.contains("%E6%B5%8B%E8%AF%95%20%E6%90%9C%E7%B4%A2"), source.id)
            } else {
                XCTAssertEqual(components.path, path, source.id)
                XCTAssertEqual(components.queryItems?.first(where: { $0.name == queryName })?.value, queryValue, source.id)
            }
        }

        XCTAssertNil(HTMLVideoSourceAdapter.searchURL(for: VideoSourceCatalog.beeg, query: "sample clip"), "Tag listings are not keyword search")

        let xvideos = try XCTUnwrap(HTMLVideoSourceAdapter.searchURL(for: VideoSourceCatalog.xvideos, query: "sample clip"))
        XCTAssertEqual(xvideos.path, "/")
        XCTAssertEqual(URLComponents(url: xvideos, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "k" })?.value, "sample clip")

        let tokyoMotion = try XCTUnwrap(HTMLVideoSourceAdapter.searchURL(for: VideoSourceCatalog.tokyomotion, query: "test"))
        let tokyoComponents = try XCTUnwrap(URLComponents(url: tokyoMotion, resolvingAgainstBaseURL: false))
        XCTAssertEqual(tokyoComponents.queryItems?.first(where: { $0.name == "search_query" })?.value, "test")
        XCTAssertEqual(tokyoComponents.queryItems?.first(where: { $0.name == "search_type" })?.value, "videos")
    }

    func testBeegSearchPayloadUsesOnlyActualUpstreamItems() throws {
        let payload = #"[{"file":{"id":721,"fl_duration":91,"data":[{"cd_column":"sf_name","cd_value":"Beeg title"}]},"fc_facts":[{"fc_thumbs":[4]}]}]"#
        let videos = try VideoRepository.shared.parseBeegFeed(data: Data(payload.utf8), category: "搜索")

        XCTAssertEqual(videos.count, 1)
        XCTAssertEqual(videos[0].title, "Beeg title")
        XCTAssertEqual(videos[0].playbackUrl, "https://beeg.com/-0721")
        XCTAssertEqual(videos[0].coverUrl, "https://thumbs.externulls.com/videos/721/4.webp")
        XCTAssertTrue(videos[0].rating.isEmpty)
    }

    func testBeegPlaybackNumericIDExtraction() {
        let urls = [
            "https://beeg.com/-0721": "721",
            "https://beeg.com/-12345": "12345",
            "https://beeg.com/video/998877": "998877",
            "https://beeg.com/10001": "10001"
        ]
        let regex = try! NSRegularExpression(pattern: #"(?:beeg\.com/(?:-0?|video/)?|/|^)(\d+)"#)
        for (url, expectedID) in urls {
            let match = regex.firstMatch(in: url, range: NSRange(location: 0, length: (url as NSString).length))
            XCTAssertNotNil(match, "Failed to match: \(url)")
            let extracted = (url as NSString).substring(with: match!.range(at: 1))
            XCTAssertEqual(extracted, expectedID, "Extracted ID mismatch for \(url)")
        }
    }

    func testUnregisteredSourceDoesNotReceiveGenericFeedOrSearchRoutes() {
        let source = VideoSourceDefinition(
            id: "unregistered",
            name: "未注册来源",
            group: .overseas,
            description: "test fixture",
            baseUrl: "https://example.com",
            categories: ["全部"],
            isDirectConnect: false
        )

        XCTAssertNil(HTMLVideoSourceAdapter.feedURL(for: source, category: "全部"))
        XCTAssertNil(HTMLVideoSourceAdapter.searchURL(for: source, query: "test"))
    }

    func testPlaybackAdapterUsesSourceIdentityInsteadOfGenericViewkeyPattern() {
        let pornhubVideo = VideoItem(
            id: "ph-1",
            title: "Pornhub test video",
            subtitle: "",
            coverUrl: "",
            playbackUrl: "https://jp.pornhub.com/view_video.php?viewkey=abc123",
            sourceName: "Pornhub"
        )
        let unknownVideo = VideoItem(
            id: "unknown-1",
            title: "Unknown source",
            subtitle: "",
            coverUrl: "",
            playbackUrl: "https://example.com/view_video.php?viewkey=abc123",
            sourceName: "Unknown"
        )

        XCTAssertEqual(VideoRepository.playbackSourceID(for: pornhubVideo), VideoSourceCatalog.pornhub.id)
        XCTAssertNil(VideoRepository.playbackSourceID(for: unknownVideo))
    }

    func testPlaybackOriginUsesOnlyThePageOrigin() throws {
        let playback = PlaybackSourceResult(
            url: try XCTUnwrap(URL(string: "https://cdn.example/video.m3u8")),
            referer: "https://www.example.com/watch/episode?id=42",
            title: "test"
        )

        XCTAssertEqual(playback.headers["Referer"], "https://www.example.com/watch/episode?id=42")
        XCTAssertEqual(playback.headers["Origin"], "https://www.example.com")
    }

    func testHuavodEpisodeParameterSupportsPlayAndDetailPages() {
        XCTAssertEqual(
            VideoRepository.huavodEpisodeParam(from: "https://huavod.com/vodplay/204156-1-2.html"),
            "204156-1-2"
        )
        XCTAssertEqual(
            VideoRepository.huavodEpisodeParam(from: "https://huavod.com/voddetail/204156.html"),
            "204156-1-1"
        )
        XCTAssertNil(VideoRepository.huavodEpisodeParam(from: "https://example.com/video/204156"))
    }

    func testHuavodPlaybackUsesResolvedProxyInsteadOfDeadChannelKey() throws {
        let nextResponse = #"{"code":1,"data":{"mt":"fresh-token","ad_duration_ms":22000,"channel_key":"https://cdnhr2.b-cdn.net/expired/index.m3u8"}}"#
        let resolvedResponse = #"{"code":1,"msg":"ok","data":{"url":"https:\/\/huavod.com\/link\/penny-905ab584a3ea484dbe0846978a573bfa.m3u8"}}"#

        XCTAssertEqual(try VideoRepository.huavodResolveToken(from: Data(nextResponse.utf8)), "fresh-token")
        XCTAssertEqual(
            try VideoRepository.huavodResolvedStreamURL(from: Data(resolvedResponse.utf8)).absoluteString,
            "https://huavod.com/link/penny-905ab584a3ea484dbe0846978a573bfa.m3u8"
        )
        XCTAssertThrowsError(try VideoRepository.huavodResolvedStreamURL(from: Data(#"{"code":0,"data":{"url":"https://huavod.com/link/failed.m3u8"}}"#.utf8)))
    }

    func testHTMLSourceAdapterParsesPornhubSeparateImageLinkAndTitleLink() throws {
        let html = #"""
        <div class="positionRelative singleVideo">
            <a class="imageLink" href="/view_video.php?viewkey=6aea3470e880b" data-poster="https://pix-cdn77.phncdn.com/poster.jpg">
                <img class="videoThumb" src="https://pix-cdn77.phncdn.com/thumb.jpg">
            </a>
            <div class="title">
                <a href="/view_video.php?viewkey=6aea3470e880b">Pornhub Upstream Title</a>
            </div>
        </div>
        """#
        let source = try XCTUnwrap(VideoSourceCatalog.allSources.first { $0.id == "pornhub" })
        let videos = HTMLVideoSourceAdapter.parseHTMLFeed(html, source: source, category: "全部")

        XCTAssertEqual(videos.count, 1)
        XCTAssertEqual(videos[0].title, "Pornhub Upstream Title")
        XCTAssertEqual(videos[0].coverUrl, "https://pix-cdn77.phncdn.com/poster.jpg")
        XCTAssertEqual(videos[0].playbackUrl, "https://jp.pornhub.com/view_video.php?viewkey=6aea3470e880b")
    }

    func testHTMLSourceAdapterParsesOhentaiSpacesInThumbnailURL() throws {
        let html = #"""
        <div class="videobrick">
            <a href="detail.php?vid=NzI4Mw==" class="interlink">
                <img src="video_data/Axen Collection/Episode-2/Episode-2.png" alt="Axen Collection" />
            </a>
            <div class="videotitle" title="Axen Collection"><a href="detail.php?vid=NzI4Mw==" class="interlink">Axen Collection</a></div>
        </div>
        """#
        let source = try XCTUnwrap(VideoSourceCatalog.allSources.first { $0.id == "ohentai" })
        let videos = HTMLVideoSourceAdapter.parseHTMLFeed(html, source: source, category: "全部")

        XCTAssertEqual(videos.count, 1)
        XCTAssertEqual(videos[0].title, "Axen Collection")
        XCTAssertEqual(videos[0].coverUrl, "https://ohentai.org/video_data/Axen%20Collection/Episode-2/Episode-2.png")
        XCTAssertEqual(videos[0].playbackUrl, "https://ohentai.org/detail.php?vid=NzI4Mw==")
    }

    func test91PornExtractsStrencode2StreamOverBlockedCdn() throws {
        let snippet = "%3c%73%6f%75%72%63%65%20%73%72%63%3d%27%68%74%74%70%73%3a%2f%2f%31%34%37%30%34%32%33%38%33%30%2e%72%73%63%2e%63%64%6e%37%37%2e%6f%72%67%2f%6d%70%34%33%2f%31%32%35%37%32%30%39%2e%6d%70%34%3f%73%65%63%75%72%65%3d%61%62%63%27%20%74%79%70%65%3d%27%76%69%64%65%6f%2f%6d%70%34%27%3e"
        let html = """
        <video id="player_one">
            <source src="https://ccm.91p52.com/358999.mp4?blocked=1" type='video/mp4'>
            document.write(strencode2("\(snippet)"));
        </video>
        """
        guard let strencodeRegex = try? NSRegularExpression(pattern: #"strencode2\(\s*["']([^"']+)["']\s*\)"#),
              let match = strencodeRegex.firstMatch(in: html, range: NSRange(location: 0, length: (html as NSString).length)),
              match.numberOfRanges > 1 else {
            XCTFail("Regex failed")
            return
        }
        let payload = (html as NSString).substring(with: match.range(at: 1))
        let decoded = VideoRepository.unescapeJavaScriptString(payload)
        XCTAssertTrue(decoded.contains("https://1470423830.rsc.cdn77.org/mp43/1257209.mp4?secure=abc"))

        let unicodeTest = "%u4e2d%u6587%20%76%69%64%65%6f"
        XCTAssertEqual(VideoRepository.unescapeJavaScriptString(unicodeTest), "中文 video")
    }

    func testJavSbStreamSignatureMatchesKnownVector() {
        let testVal = "22a7902ff093f509ed4aba25768d4d11@1793888601"
        let signature = JavSbSignature.createSignature(for: testVal)
        XCTAssertEqual(signature, "6ZFINbkpMCtKSeSlybypbVpVYF0CQf1g0KfhV57GIckytOBRCsrFO5RkZc")
    }

    func testMissAvExtractsPackedEvalStream() {
        let html = """
        <script>
        eval(function(p,a,c,k,e,d){e=function(c){return c.toString(36)};if(!''.replace(/^/,String)){while(c--){d[c.toString(a)]=k[c]||c.toString(a)}k=[function(e){return d[e]}];e=function(){return'\\\\w+'};c=1};while(c--){if(k[c]){p=p.replace(new RegExp('\\\\b'+e(c)+'\\\\b','g'),k[c])}}return p}('e=\\'8://7.6/5-4-3-2-1/d.0\\';c=\\'8://7.6/5-4-3-2-1/a/9.0\\';b=\\'8://7.6/5-4-3-2-1/a/9.0\\';',15,15,'m3u8|e1b72ce6424f|8423|4edb|7510|17c87dea|com|surrit|https|video|720p|source1280|source842|playlist|source'.split('|'),0,{}))
        </script>
        """
        let mediaURL = HTMLVideoSourceAdapter.extractMediaURL(from: html, pageURL: URL(string: "https://missav123.com/cn/fns-275")!)
        XCTAssertEqual(mediaURL?.absoluteString, "https://surrit.com/17c87dea-7510-4edb-8423-e1b72ce6424f/playlist.m3u8")
    }

    func testHuavodParserDoesNotTruncateFeedAtTwentyVideos() {
        // Construct mock HTML containing 25 cards
        var cardsHtml = ""
        for i in 1...25 {
            cardsHtml += """
            <div class="card">
                <a class="public-list-exp" href="/voddetail/\(1000 + i).html" title="影片第\(i)部"></a>
                <img class="gen-movie-img" src="https://img.example.com/cover\(i).jpg">
            </div>
            """
        }
        
        let videos = VideoRepository.shared.parseHuavodHtml(cardsHtml, category: "电影")
        XCTAssertEqual(videos.count, 25)
        XCTAssertEqual(videos.first?.title, "影片第1部")
        XCTAssertEqual(videos.last?.title, "影片第25部")
    }

    @MainActor
    func testVideoRepositoryPaginationStateResetOnCategoryAndSourceSwitch() {
        let repo = VideoRepository.shared
        repo.currentPage = 5
        repo.canLoadMore = false
        repo.isLoadingNextPage = true
        
        repo.switchCategory(to: "连载番剧")
        XCTAssertEqual(repo.currentPage, 1)
        XCTAssertTrue(repo.canLoadMore)
        XCTAssertFalse(repo.isLoadingNextPage)
        
        repo.currentPage = 4
        repo.canLoadMore = false
        repo.switchSource(to: VideoSourceCatalog.huavod)
        XCTAssertEqual(repo.currentPage, 1)
        XCTAssertTrue(repo.canLoadMore)
        XCTAssertFalse(repo.isLoadingNextPage)
    }

    func testHTMLVideoSourceAdapterPaginationRoutes() {
        let kissjav = VideoSourceCatalog.allSources.first { $0.id == "kissjav" }!
        let page1 = HTMLVideoSourceAdapter.feedURL(for: kissjav, category: "新片", page: 1)
        let page2 = HTMLVideoSourceAdapter.feedURL(for: kissjav, category: "新片", page: 2)
        XCTAssertEqual(page1?.absoluteString, "https://kissjav.li/latest-updates/")
        XCTAssertEqual(page2?.absoluteString, "https://kissjav.li/latest-updates/2/")

        let javsb = VideoSourceCatalog.allSources.first { $0.id == "javsb" }!
        let javsbPage1 = HTMLVideoSourceAdapter.feedURL(for: javsb, category: "精选", page: 1)
        let javsbPage2 = HTMLVideoSourceAdapter.feedURL(for: javsb, category: "精选", page: 2)
        XCTAssertEqual(javsbPage1?.absoluteString, "https://jav.sb/ja/label/rank/by/score.html")
        XCTAssertEqual(javsbPage2?.absoluteString, "https://jav.sb/ja/label/rank/by/score/page/2.html")

        let missav = VideoSourceCatalog.allSources.first { $0.id == "missav" }!
        let missavPage1 = HTMLVideoSourceAdapter.feedURL(for: missav, category: "热门", page: 1)
        let missavPage2 = HTMLVideoSourceAdapter.feedURL(for: missav, category: "热门", page: 2)
        XCTAssertEqual(missavPage1?.absoluteString, "https://missav.media/cn/release")
        XCTAssertEqual(missavPage2?.absoluteString, "https://missav.media/cn/release?page=2")
    }

    func testResolveHuangguoStreamViaApiEndpoint() async throws {
        let repo = VideoRepository.shared
        do {
            let result = try await repo.resolveHuangguoStream(
                pageUrl: "https://huangguoai.com/video/10586/",
                title: "任意门"
            )
            XCTAssertFalse(result.url.absoluteString.isEmpty)
            XCTAssertTrue(result.url.absoluteString.contains(".m3u8"))
            XCTAssertEqual(result.referer, "https://huangguoai.com/")
            XCTAssertEqual(result.headers["Origin"], "https://huangguoai.com")
            XCTAssertEqual(result.title, "任意门")
        } catch {
            throw XCTSkip("Upstream huangguoai live endpoint unreachable: \(error)")
        }
    }

    func testResolveHuangguoEpisode2StreamViaApiEndpoint() async throws {
        let repo = VideoRepository.shared
        do {
            let result = try await repo.resolveHuangguoStream(
                pageUrl: "https://huangguoai.com/video/10586/ep-2/",
                title: "任意门 第2集"
            )
            XCTAssertFalse(result.url.absoluteString.isEmpty)
            XCTAssertTrue(result.url.absoluteString.contains(".m3u8"))
        } catch {
            throw XCTSkip("Upstream huangguoai live endpoint unreachable: \(error)")
        }
    }

    func testResolveHuangguoEpisodesFromVideoPage() async throws {
        let repo = VideoRepository.shared
        let video = VideoItem(
            id: "hg-10586",
            title: "任意门",
            subtitle: "黄果短剧 · 推荐",
            coverUrl: "https://example.com/cover.jpg",
            playbackUrl: "https://huangguoai.com/video/10586/",
            duration: "",
            rating: "9.5",
            tags: ["短剧"],
            sourceName: "黄果短剧"
        )
        let episodes = await repo.resolveEpisodes(for: video)
        XCTAssertGreaterThanOrEqual(episodes.count, 2)
        XCTAssertEqual(episodes.first?.number, 1)
        XCTAssertEqual(episodes[1].number, 2)
        XCTAssertTrue(episodes[1].pageUrl.contains("ep-2"))
    }

    func testFetchHuangguoFeedViaEdgeGateway() async throws {
        let repo = VideoRepository.shared
        do {
            let videos = try await repo.fetchVideosForSource(VideoSourceCatalog.huangguo, category: "推荐", page: 1)
            XCTAssertFalse(videos.isEmpty)
            XCTAssertTrue(videos.first?.playbackUrl.contains("huangguoai.com") == true)
        } catch {
            throw XCTSkip("Upstream huangguoai live endpoint unreachable: \(error)")
        }
    }
}
