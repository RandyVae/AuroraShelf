import XCTest
@testable import AuroraShelf

@MainActor
final class WatchlistStoreTests: XCTestCase {
    func testBookmarkPersistsAcrossStoreInstancesAndCanBeRemoved() throws {
        let suiteName = "AuroraShelfTests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        defaults.removePersistentDomain(forName: suiteName)
        defer { defaults.removePersistentDomain(forName: suiteName) }

        let video = VideoItem(
            id: "video-1",
            title: "真实片目",
            subtitle: "",
            coverUrl: "https://example.com/cover.jpg",
            playbackUrl: "https://example.com/video.m3u8",
            sourceName: "测试源"
        )

        let firstStore = WatchlistStore(defaults: defaults)
        try firstStore.setBookmarked(true, for: video)

        let reloadedStore = WatchlistStore(defaults: defaults)
        XCTAssertTrue(reloadedStore.contains(video))
        XCTAssertEqual(reloadedStore.items, [video])

        try reloadedStore.setBookmarked(false, for: video)
        XCTAssertFalse(WatchlistStore(defaults: defaults).contains(video))
    }
}
