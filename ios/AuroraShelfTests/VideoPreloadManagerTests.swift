import XCTest
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
    }
}
