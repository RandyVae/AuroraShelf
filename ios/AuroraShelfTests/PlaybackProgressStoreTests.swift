import XCTest
@testable import AuroraShelf

@MainActor
final class PlaybackProgressStoreTests: XCTestCase {
    private var testDefaults: UserDefaults!
    private var suiteName: String!

    override func setUp() {
        super.setUp()
        suiteName = "NetgreyTests.\(UUID().uuidString)"
        testDefaults = UserDefaults(suiteName: suiteName)!
        testDefaults.removePersistentDomain(forName: suiteName)
    }

    override func tearDown() {
        testDefaults.removePersistentDomain(forName: suiteName)
        super.tearDown()
    }

    private func makeSampleVideo(id: String = "v1") -> VideoItem {
        VideoItem(
            id: id,
            title: "测试影视 \(id)",
            subtitle: "4K HDR",
            coverUrl: "https://example.com/cover.jpg",
            playbackUrl: "https://example.com/stream.m3u8",
            sourceName: "华视影院"
        )
    }

    func testProgressUpdateAndUpNextFiltering() {
        let store = PlaybackProgressStore(defaults: testDefaults)
        let video = makeSampleVideo(id: "ep1")

        // 1. 播放小于 5 秒，不应进入 Up Next
        store.updateProgress(video: video, positionMs: 3_000, durationMs: 600_000)
        XCTAssertEqual(store.upNextRecords.count, 0)

        // 2. 播放超过 5 秒且小于 90%，进入 Up Next
        store.updateProgress(video: video, positionMs: 120_000, durationMs: 600_000)
        XCTAssertEqual(store.upNextRecords.count, 1)
        let record = store.upNextRecords.first
        XCTAssertEqual(record?.videoId, "ep1")
        if let ratio = record?.progressRatio {
            XCTAssertEqual(ratio, 0.2, accuracy: 0.001)
        } else {
            XCTFail("progressRatio should not be nil")
        }

        // 3. 播放超过 90%，判定为自动完结，从 Up Next 移出
        store.updateProgress(video: video, positionMs: 550_000, durationMs: 600_000) // 91.6%
        XCTAssertEqual(store.upNextRecords.count, 0)
        XCTAssertTrue(store.record(for: video)?.isAutoCompleted == true)
    }

    func testManualMarkCompletedAndRemoveFromUpNext() {
        let store = PlaybackProgressStore(defaults: testDefaults)
        let video = makeSampleVideo(id: "ep2")

        store.updateProgress(video: video, positionMs: 100_000, durationMs: 500_000)
        XCTAssertEqual(store.upNextRecords.count, 1)

        // 手动从待播移除
        store.removeFromUpNext(for: video)
        XCTAssertEqual(store.upNextRecords.count, 0)
        XCTAssertTrue(store.record(for: video)?.isManuallyDismissed == true)

        // 再次播放更新，自动重新激活待播
        store.updateProgress(video: video, positionMs: 150_000, durationMs: 500_000)
        XCTAssertEqual(store.upNextRecords.count, 1)

        // 手动标记为已观看
        store.markCompleted(for: video)
        XCTAssertEqual(store.upNextRecords.count, 0)
        XCTAssertTrue(store.record(for: video)?.isManuallyCompleted == true)
    }

    func testPersistenceAcrossStoreInstances() {
        let storeA = PlaybackProgressStore(defaults: testDefaults)
        let video = makeSampleVideo(id: "ep3")

        storeA.updateProgress(video: video, positionMs: 80_000, durationMs: 400_000)

        let storeB = PlaybackProgressStore(defaults: testDefaults)
        XCTAssertEqual(storeB.upNextRecords.count, 1)
        XCTAssertEqual(storeB.upNextRecords.first?.videoId, "ep3")
        if let ratio = storeB.upNextRecords.first?.progressRatio {
            XCTAssertEqual(ratio, 0.2, accuracy: 0.001)
        } else {
            XCTFail("progressRatio should not be nil")
        }
    }
}
