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

    func testLossyDecodingPreservesValidWatchlistItemsWhenOneElementIsMalformed() throws {
        let suiteName = "AuroraShelfTests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        defaults.removePersistentDomain(forName: suiteName)
        defer { defaults.removePersistentDomain(forName: suiteName) }

        let rawJSON = """
        [
          {"id":"ok-1","title":"正常影片","subtitle":"","coverUrl":"https://example.com/1.jpg","playbackUrl":"https://example.com/1.m3u8","duration":"","rating":"","tags":[],"category":"","isTop10":false,"sourceName":"华视影院"},
          {"malformed_entry": 12345},
          {"id":"ok-2","title":"第二部影片","subtitle":"","coverUrl":"https://example.com/2.jpg","playbackUrl":"https://example.com/2.m3u8","duration":"","rating":"","tags":[],"category":"","isTop10":false,"sourceName":"华视影院"}
        ]
        """
        defaults.set(Data(rawJSON.utf8), forKey: "aurora.watchlist.v1")

        let store = WatchlistStore(defaults: defaults)
        XCTAssertNil(store.loadError)
        XCTAssertEqual(store.items.map(\.id), ["ok-1", "ok-2"])
    }
}
