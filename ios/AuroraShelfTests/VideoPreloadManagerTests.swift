import XCTest
import AVFoundation
@testable import AuroraShelf

final class VideoPreloadManagerTests: XCTestCase {
    
    @MainActor
    func testPreloadManagerCacheAndClear() {
        let manager = VideoPreloadManager.shared
        manager.clearCache()
        
        let playbackUrl = "https://huavod.com/vodplay/test-1-1.html"
        XCTAssertNil(manager.cachedResult(for: playbackUrl))
        
        let dummyResult = PlaybackSourceResult(
            url: URL(string: "https://huavod.com/link/test.m3u8")!,
            referer: "https://huavod.com",
            title: "测试视频"
        )
        
        manager.cache(result: dummyResult, for: playbackUrl)
        let cached = manager.cachedResult(for: playbackUrl)
        XCTAssertNotNil(cached)
        XCTAssertEqual(cached?.url.absoluteString, "https://huavod.com/link/test.m3u8")
        XCTAssertEqual(cached?.title, "测试视频")
        
        manager.clearCache()
        XCTAssertNil(manager.cachedResult(for: playbackUrl))
    }

    @MainActor
    func testHuangguoEpisodeKeysDoNotAliasToEpisodeOne() {
        let first = Set(VideoPreloadManager.canonicalKeys(for: "https://huangguoai.com/video/123/"))
        let second = Set(VideoPreloadManager.canonicalKeys(for: "https://huangguoai.com/video/123/ep-2/"))
        XCTAssertTrue(first.contains("https://huangguoai.com/video/123/"))
        XCTAssertTrue(second.contains("https://huangguoai.com/video/123/ep-2/"))
        XCTAssertTrue(first.intersection(second).isEmpty)
    }
    
    @MainActor
    func testHuavodParserExtractsRatingAndIndependentCardCover() {
        let mockHtml = """
        <div class="public-list-box public-pic-a swiper-slide">
            <div class="public-list-div public-list-bj">
                <a target="_self" class="public-list-exp" href="/voddetail/9999.html" title="测试电影">
                    <img class="pic-fit lazy1 gen-movie-img mask-1" src="/upload/site/cover9999.jpg" />
                    <span class="public-prt">热映推荐</span>
                    <span class="public-list-prb"><i class="ft4">8.9</i></span>
                </a>
            </div>
            <div class="public-list-button">
                <a class="time-title" href="/voddetail/9999.html" title="测试电影">测试电影</a>
            </div>
        </div>
        """
        
        let items = VideoRepository.shared.parseHuavodHtml(mockHtml, category: "电影")
        XCTAssertEqual(items.count, 1)
        let first = items[0]
        XCTAssertEqual(first.id, "hua-9999")
        XCTAssertEqual(first.title, "测试电影")
        XCTAssertEqual(first.rating, "8.9")
        XCTAssertEqual(first.coverUrl, "https://huavod.com/upload/site/cover9999.jpg")
    }
    
    @MainActor
    func testDifferentiateShelfVideosEliminatesDuplicatesAcrossShelves() {
        let v1 = VideoItem(id: "v1", title: "温柔的妈妈被我弄哭了", subtitle: "", coverUrl: "c1", playbackUrl: "p1", duration: "", rating: "", tags: [], category: "精选", isTop10: true, rankNumber: 1, sourceName: "黄果短剧")
        let v2 = VideoItem(id: "v2", title: "热门短剧2", subtitle: "", coverUrl: "c2", playbackUrl: "p2", duration: "", rating: "", tags: [], category: "精选", isTop10: true, rankNumber: 2, sourceName: "黄果短剧")
        let v3 = VideoItem(id: "v3", title: "专属最新短剧A", subtitle: "", coverUrl: "c3", playbackUrl: "p3", duration: "", rating: "", tags: [], category: "最新", isTop10: false, rankNumber: nil, sourceName: "黄果短剧")
        let v4 = VideoItem(id: "v4", title: "专属最新短剧B", subtitle: "", coverUrl: "c4", playbackUrl: "p4", duration: "", rating: "", tags: [], category: "最新", isTop10: false, rankNumber: nil, sourceName: "黄果短剧")
        
        var allocated = Set<String>()
        // 模拟 Top 10 先占用了 v1 和 v2
        allocated.insert("v1")
        allocated.insert("v2")
        
        let candidates = [v1, v3, v4]
        let result = VideoRepository.differentiateShelfVideos(
            candidateVideos: candidates,
            allocatedIDs: &allocated,
            minShelfCount: 2
        )
        
        XCTAssertEqual(result.count, 2)
        XCTAssertEqual(result[0].id, "v3")
        XCTAssertEqual(result[1].id, "v4")
        XCTAssertTrue(allocated.contains("v3"))
        XCTAssertTrue(allocated.contains("v4"))
    }
    
    @MainActor
    func testPrewarmSlidingWindowQueuesNextEpisodes() {
        let manager = VideoPreloadManager.shared
        manager.clearCache()
        
        let video = VideoItem(id: "v_series", title: "剧集", subtitle: "", coverUrl: "", playbackUrl: "https://site.com/ep1", duration: "", rating: "", tags: [], category: "", isTop10: false, rankNumber: nil, sourceName: "测试源")
        let eps = [
            PlaybackEpisode(number: 1, label: "第 1 集", pageUrl: "https://site.com/ep1"),
            PlaybackEpisode(number: 2, label: "第 2 集", pageUrl: "https://site.com/ep2"),
            PlaybackEpisode(number: 3, label: "第 3 集", pageUrl: "https://site.com/ep3"),
            PlaybackEpisode(number: 4, label: "第 4 集", pageUrl: "https://site.com/ep4")
        ]
        
        manager.prewarmSlidingWindow(video: video, episodes: eps, currentIndex: 1, windowSize: 2)
        XCTAssertNil(manager.cachedResult(for: "https://site.com/ep1"))
    }

    @MainActor
    func testHuavodBidirectionalAliasCache() {
        let manager = VideoPreloadManager.shared
        manager.clearCache()

        let playUrl = "https://huavod.com/vodplay/204772-1-1.html"
        let detailUrl = "https://huavod.com/voddetail/204772.html"

        let dummyResult = PlaybackSourceResult(
            url: URL(string: "https://huavod.com/link/penny-204772.m3u8")!,
            referer: "https://huavod.com/Player/ec?episode=204772-1-1",
            title: "测试华视电影"
        )

        // 存入 playUrl，通过 detailUrl 应能同时命中
        manager.cache(result: dummyResult, for: playUrl)
        XCTAssertNotNil(manager.cachedResult(for: playUrl))
        XCTAssertNotNil(manager.cachedResult(for: detailUrl))
        XCTAssertEqual(manager.cachedResult(for: detailUrl)?.url.absoluteString, "https://huavod.com/link/penny-204772.m3u8")

        manager.clearCache()

        // 存入 detailUrl，通过 playUrl 也应能同时命中
        manager.cache(result: dummyResult, for: detailUrl)
        XCTAssertNotNil(manager.cachedResult(for: playUrl))
        XCTAssertNotNil(manager.cachedResult(for: detailUrl))
        XCTAssertEqual(manager.cachedResult(for: playUrl)?.url.absoluteString, "https://huavod.com/link/penny-204772.m3u8")

        manager.invalidateCache(for: playUrl)
        XCTAssertNil(manager.cachedResult(for: playUrl))
        XCTAssertNil(manager.cachedResult(for: detailUrl))
    }

    @MainActor
    func testParseHuavodHeroSlidesMatchesMultiClassSlideTags() {
        let html = """
        <div class="swiper-wrapper">
          <div class="slide-time-bj swiper-slide">
            <a href="/advertiser" target="_blank">
              <div class="slide-time-img3 slide-time-img3--ad" style="background-image: url('https://ad.example/ad.jpg')"></div>
            </a>
          </div>
          <div class="slide-time-bj swiper-slide">
            <a href="/voddetail/205123.html" title="你好星期六">
              <div class="slide-time-img3 mask-1" style="background-image: url('https://puui.qpic.cn/banner.jpg')"></div>
              <div class="slide-info">
                <div class="slide-info-type"><span>综艺</span></div>
                <h3 class="slide-info-title">你好星期六</h3>
              </div>
            </a>
          </div>
        </div>
        """
        let slides = VideoRepository.parseHuavodHeroSlides(from: html)
        XCTAssertEqual(slides.count, 1)
        XCTAssertEqual(slides.first?.vodId, "205123")
        XCTAssertEqual(slides.first?.bannerUrl, "https://puui.qpic.cn/banner.jpg")
        XCTAssertEqual(slides.first?.categoryLabel, "综艺")
    }

    @MainActor
    func testPictureInPictureConsumeRetainedPlayerClearsActivePiPVideo() {
        let pip = PictureInPictureManager.shared
        pip.stopAndReleasePiPIfNeeded()
        let video = VideoItem(
            id: "pip-test",
            title: "PiP Test",
            subtitle: "",
            coverUrl: "",
            playbackUrl: "https://example.com/pip-test.m3u8",
            duration: "",
            rating: "",
            tags: [],
            category: "",
            isTop10: false,
            rankNumber: nil,
            sourceName: "Test"
        )
        let dummyPlayer = AVPlayer()
        pip.retainPlayerForPiP(player: dummyPlayer, video: video)
        XCTAssertEqual(pip.activePiPVideo?.playbackUrl, video.playbackUrl)

        let consumed = pip.consumeRetainedPlayer(for: video)
        XCTAssertTrue(consumed === dummyPlayer)
        XCTAssertNil(pip.activePiPVideo, "恢复播放器后必须清空 activePiPVideo，防止再次退出残余错乱状态")
    }

    func testHeroPresentationStyleSelectsCinemaStageForUltrawideAndSmallAssets() {
        let phoneHero = CGSize(width: 1179, height: 1485)
        XCTAssertEqual(
            HeroBanner.presentationStyle(imagePixelSize: CGSize(width: 2560, height: 650), containerSize: phoneHero),
            .cinemaStage
        )
        XCTAssertEqual(
            HeroBanner.presentationStyle(imagePixelSize: CGSize(width: 1280, height: 720), containerSize: phoneHero),
            .cinemaStage
        )
        XCTAssertEqual(
            HeroBanner.presentationStyle(imagePixelSize: CGSize(width: 362, height: 678), containerSize: phoneHero),
            .cinemaStage
        )
        XCTAssertEqual(
            HeroBanner.presentationStyle(imagePixelSize: CGSize(width: 1080, height: 1512), containerSize: phoneHero),
            .fullBleed
        )
    }

    func testHuavodParsePlayLinesExtractsAlternateServerLinesForEpisode() {
        let html = """
        <div class="anthology-list-play">
            <a href="/vodplay/204173-1-1.html">第01集</a>
            <a href="/vodplay/204173-1-2.html">第02集</a>
            <a data-url="/vodplay/204173-2-1.html">第01集</a>
            <a data-url="/vodplay/204173-2-2.html">第02集</a>
            <a href="/vodplay/204173-3-1.html">第01集</a>
            <a href="/vodplay/888888-1-2.html">其他影片</a>
        </div>
        """
        let lines = VideoRepository.parseHuavodPlayLines(html: html, vodId: "204173", targetNid: 2)
        XCTAssertEqual(
            lines,
            [
                "https://huavod.com/vodplay/204173-1-2.html",
                "https://huavod.com/vodplay/204173-2-2.html",
                "https://huavod.com/vodplay/204173-3-1.html"
            ]
        )
    }

    func testHLSLoopbackRelayRewritesSubPlaylistsKeysAndSegments() {
        let playlist = """
        #EXTM3U
        #EXT-X-KEY:METHOD=AES-128,URI="enc.key"
        #EXTINF:5.0,
        seg000.ts?token=abc
        #EXT-X-STREAM-INF:BANDWIDTH=800000
        720p/index.m3u8
        """
        let baseURL = URL(string: "https://cdn11.okhuaren.com/204173/index.m3u8")!
        let rewritten = HLSLoopbackRelayServer.rewriteM3U8(
            playlist,
            baseURL: baseURL,
            originPrefix: "http://127.0.0.1:45678",
            token: "tok1"
        )
        let lines = rewritten.components(separatedBy: "\n")
        func decodeRelayTarget(_ urlString: String) -> String? {
            guard let comps = URLComponents(string: urlString),
                  let uValue = comps.queryItems?.first(where: { $0.name == "u" })?.value else {
                return nil
            }
            var b64 = uValue.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
            let rem = b64.count % 4
            if rem != 0 { b64 += String(repeating: "=", count: 4 - rem) }
            guard let data = Data(base64Encoded: b64) else { return nil }
            return String(data: data, encoding: .utf8)
        }

        XCTAssertTrue(lines[1].contains("URI=\"http://127.0.0.1:45678/segment?"))
        let keyRangeStart = lines[1].range(of: "URI=\"")!.upperBound
        let keyRangeEnd = lines[1][keyRangeStart...].firstIndex(of: "\"")!
        XCTAssertEqual(
            decodeRelayTarget(String(lines[1][keyRangeStart..<keyRangeEnd])),
            "https://cdn11.okhuaren.com/204173/enc.key"
        )
        XCTAssertTrue(lines[3].hasPrefix("http://127.0.0.1:45678/segment?"))
        XCTAssertEqual(
            decodeRelayTarget(lines[3]),
            "https://cdn11.okhuaren.com/204173/seg000.ts?token=abc"
        )
        XCTAssertTrue(lines[5].hasPrefix("http://127.0.0.1:45678/playlist?"))
        XCTAssertEqual(
            decodeRelayTarget(lines[5]),
            "https://cdn11.okhuaren.com/204173/720p/index.m3u8"
        )
    }
}
