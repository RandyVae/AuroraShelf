import XCTest
import SwiftUI
@testable import AuroraShelf

@MainActor
final class CategoryShelfTests: XCTestCase {
    func testCategoryShelfModelProperties() {
        let dummyVideo = VideoItem(
            id: "test-video-1",
            title: "流浪地球 3",
            subtitle: "2026科幻巨制",
            coverUrl: "https://example.com/cover.jpg",
            playbackUrl: "https://example.com/play.m3u8",
            duration: "128:00",
            rating: "9.2",
            category: "电影",
            sourceName: "华视影院"
        )
        
        let shelf = CategoryShelf(category: "电影", videos: [dummyVideo], isLoading: false)
        XCTAssertEqual(shelf.category, "电影")
        XCTAssertEqual(shelf.id, "电影")
        XCTAssertEqual(shelf.videos.count, 1)
        XCTAssertEqual(shelf.videos.first?.title, "流浪地球 3")
        XCTAssertFalse(shelf.isLoading)
        
        let loadingShelf = CategoryShelf(category: "电视剧", videos: [], isLoading: true)
        XCTAssertEqual(loadingShelf.category, "电视剧")
        XCTAssertTrue(loadingShelf.videos.isEmpty)
        XCTAssertTrue(loadingShelf.isLoading)
    }

    func testVideoRepositoryInitializesCategoryShelvesForHuavod() {
        let repo = VideoRepository.shared
        repo.switchSource(to: VideoSourceCatalog.huavod)
        
        // 华视影院包含分类：全部、电影、电视剧、综艺、动漫、短剧
        let expectedSubcategories = ["电影", "电视剧", "综艺", "动漫", "短剧"]
        let shelfNames = repo.categoryShelves.map(\.category)
        
        // 切换后应立即为全部子分类初始化货架
        XCTAssertEqual(shelfNames, expectedSubcategories, "华视影院应初始化 5 个独立分类货架")
    }

    func testVideoRepositoryPopulatesTop10Hits() {
        let repo = VideoRepository.shared
        XCTAssertNotNil(repo.top10Videos, "Top 10 榜单数组不应为 nil")
    }

    func testParseHuavodSearchHtmlStripsHotSidebarAndExtractsCleanTitles() async {
        let searchHtml = """
        <div class="box-width">
          <div class="search-box">
            <a href="/voddetail/204999.html">
              <img data-src="https://img3.doubanio.com/view/photo/s_ratio_poster/public/p999.jpg" alt="惊蛰无声封面图" />
              <span class="public-list-prb">HD国语</span>
            </a>
            <div class="thumb-txt"><a href="/voddetail/204999.html">惊蛰无声</a></div>
            <div class="thumb-else">2026 / 内地 / 悬疑</div>
          </div>
          <div class="wap-diy-vod-e">
            <h5>热播影片</h5>
            <div class="public-list-box">
              <a href="/voddetail/195322.html" title="玫瑰战争粤语">
                <img data-src="https://img.example/hot.jpg" alt="玫瑰战争粤语封面图" />
              </a>
            </div>
          </div>
        </div>
        """
        let repo = VideoRepository.shared
        let items = repo.parseHuavodHtml(searchHtml, category: "搜索")
        XCTAssertEqual(items.count, 1)
        XCTAssertEqual(items.first?.title, "惊蛰无声")
        XCTAssertEqual(items.first?.playbackUrl, "https://huavod.com/vodplay/204999-1-1.html")
        XCTAssertEqual(items.first?.coverUrl, "https://img3.doubanio.com/view/photo/l_ratio_poster/public/p999.jpg")
        XCTAssertFalse(items.contains(where: { $0.title.contains("玫瑰战争") }))
    }

    func testParseHuavodSuggestJsonBuildsPlayableItems() {
        let json = """
        {"code":1,"msg":"ok","list":[{"id":195322,"name":"玫瑰战争","pic":"https://img.example/mg.jpg"}]}
        """.data(using: .utf8)!
        let items = VideoRepository.parseHuavodSuggestJson(json)
        XCTAssertEqual(items.count, 1)
        XCTAssertEqual(items.first?.title, "玫瑰战争")
        XCTAssertEqual(items.first?.sourceName, "华视影院")
        XCTAssertEqual(items.first?.playbackUrl, "https://huavod.com/vodplay/195322-1-1.html")
    }

    func testSortSearchResultsPrioritizesHuavodAndExactTitleMatches() {
        let otherExact = VideoItem(
            id: "o1",
            title: "折月亮",
            subtitle: "",
            coverUrl: "",
            playbackUrl: "https://other.example/1",
            sourceName: "其他源"
        )
        let huavodExact = VideoItem(
            id: "h1",
            title: "折月亮",
            subtitle: "",
            coverUrl: "",
            playbackUrl: "https://huavod.com/voddetail/204741.html",
            sourceName: "华视影院"
        )
        let huavodPartial = VideoItem(
            id: "h2",
            title: "折月亮 花絮",
            subtitle: "",
            coverUrl: "",
            playbackUrl: "https://huavod.com/voddetail/204742.html",
            sourceName: "华视影院"
        )
        let sorted = VideoRepository.sortSearchResults(
            [huavodPartial, otherExact, huavodExact],
            query: "折月亮",
            preferredSourceName: "华视影院"
        )
        XCTAssertEqual(sorted.map(\.id), ["h1", "o1", "h2"])
    }

    func testForumImageSaverFileExtensionAndDeterministicDestination() {
        let pngData = Data([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])
        let jpgData = Data([0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10])
        let webpData = Data("RIFF\u{00}\u{00}\u{00}\u{00}WEBP".utf8)
        let fallback = URL(string: "https://example.com/photo")!

        XCTAssertEqual(ForumImageSaver.fileExtension(for: pngData, fallbackURL: fallback), "png")
        XCTAssertEqual(ForumImageSaver.fileExtension(for: jpgData, fallbackURL: fallback), "jpg")
        XCTAssertEqual(ForumImageSaver.fileExtension(for: webpData, fallbackURL: fallback), "webp")

        let dir = FileManager.default.temporaryDirectory
        let urlA = ForumImageSaver.localDestinationURL(in: dir, urlString: "https://example.com/a.jpg", fileExtension: "jpg")
        let urlB = ForumImageSaver.localDestinationURL(in: dir, urlString: "https://example.com/a.jpg", fileExtension: "jpg")
        let urlC = ForumImageSaver.localDestinationURL(in: dir, urlString: "https://example.com/b.jpg", fileExtension: "jpg")
        XCTAssertEqual(urlA, urlB)
        XCTAssertNotEqual(urlA, urlC)
        XCTAssertEqual(urlA.pathExtension, "jpg")
    }

    func testOfflineManagersProgressAccessorsBounds() {
        let videoProgress = OfflineDownloadManager.shared.overallProgress
        let comicProgress = ComicDownloadManager.shared.overallProgress
        XCTAssertGreaterThanOrEqual(videoProgress, 0.0)
        XCTAssertLessThanOrEqual(videoProgress, 1.0)
        XCTAssertGreaterThanOrEqual(comicProgress, 0.0)
        XCTAssertLessThanOrEqual(comicProgress, 1.0)
    }

    func testBundleContainsTestFlightComplianceMetadata() throws {
        let bundle = Bundle(for: BundleToken.self)
        let appBundle = Bundle.main
        XCTAssertEqual(appBundle.object(forInfoDictionaryKey: "ITSAppUsesNonExemptEncryption") as? Bool, false)

        let privacyURL = try XCTUnwrap(
            appBundle.url(forResource: "PrivacyInfo", withExtension: "xcprivacy")
                ?? bundle.url(forResource: "PrivacyInfo", withExtension: "xcprivacy"),
            "主应用包必须包含 PrivacyInfo.xcprivacy"
        )
        let data = try Data(contentsOf: privacyURL)
        let plist = try XCTUnwrap(
            try PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any]
        )
        let accessedTypes = try XCTUnwrap(plist["NSPrivacyAccessedAPITypes"] as? [[String: Any]])
        let categories = Set(accessedTypes.compactMap { $0["NSPrivacyAccessedAPIType"] as? String })
        XCTAssertTrue(categories.contains("NSPrivacyAccessedAPICategoryUserDefaults"))
        XCTAssertTrue(categories.contains("NSPrivacyAccessedAPICategorySystemBootTime"))
        XCTAssertTrue(categories.contains("NSPrivacyAccessedAPICategoryFileTimestamp"))
        XCTAssertTrue(categories.contains("NSPrivacyAccessedAPICategoryDiskSpace"))
    }
}

private final class BundleToken {}
