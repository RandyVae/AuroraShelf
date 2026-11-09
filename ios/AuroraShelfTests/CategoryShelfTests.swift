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
}
