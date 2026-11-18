import XCTest
@testable import AuroraShelf

@MainActor
final class OfflineDownloadManagerTests: XCTestCase {
    private var tempRoot: URL!
    private var manager: OfflineDownloadManager!

    override func setUp() {
        super.setUp()
        tempRoot = FileManager.default.temporaryDirectory
            .appendingPathComponent("AuroraOfflineTests_\(UUID().uuidString)", isDirectory: true)
        try? FileManager.default.createDirectory(at: tempRoot, withIntermediateDirectories: true)
        manager = OfflineDownloadManager(
            baseDirectory: tempRoot.appendingPathComponent("OfflineVideos", isDirectory: true),
            metaFileURL: tempRoot.appendingPathComponent("offline_downloads.json"),
            legacyBaseDirectory: tempRoot.appendingPathComponent("LegacyOfflineVideos", isDirectory: true),
            legacyMetaFileURL: tempRoot.appendingPathComponent("legacy_offline_downloads.json")
        )
    }

    override func tearDown() {
        manager.removeAll()
        if let tempRoot {
            try? FileManager.default.removeItem(at: tempRoot)
        }
        manager = nil
        tempRoot = nil
        super.tearDown()
    }

    func testOfflineDownloadItemToVideoItemMappingAndEpisodePersistence() {
        let item = OfflineDownloadItem(
            id: "dl_0123456789abcdef",
            videoId: "hua-202979",
            title: "希望",
            episodeLabel: "第3集",
            episodeNumber: 3,
            sourceName: "华视影院",
            pageUrl: "https://huavod.com/vodplay/202979-1-3.html",
            coverUrl: "https://static.huarenlivewebsite.top/piccdn/olo/movie/202979_image.jpg",
            streamUrl: "https://huavod.com/link/penny-test.m3u8",
            localRelativePath: "dl_0123456789abcdef/local_index.m3u8",
            totalBytes: 52428800,
            downloadedBytes: 52428800,
            status: .completed,
            createdAt: Date()
        )

        let video = item.toVideoItem()
        XCTAssertEqual(video.id, "hua-202979-ep-3")
        XCTAssertEqual(video.title, "希望 · 第3集")
        XCTAssertEqual(video.sourceName, "华视影院")
        XCTAssertTrue(item.status.isCompleted)
        XCTAssertFalse(item.status.isDownloading)
        XCTAssertEqual(item.status.progressValue, 1.0)
    }

    func testGenerateIDUsesSafeSHA256PrefixWithoutSlashesOrQueries() {
        let video = VideoItem(
            id: "https://example.com/watch?v=abc/def&token=123-ep-2",
            title: "测试",
            subtitle: "",
            coverUrl: "",
            playbackUrl: "https://example.com/watch?v=abc/def&token=123",
            duration: "",
            rating: "",
            tags: [],
            category: "",
            isTop10: false,
            rankNumber: nil,
            sourceName: "测试源"
        )
        let id = OfflineDownloadManager.generateID(video: video, episode: nil)
        XCTAssertTrue(id.hasPrefix("dl_"))
        XCTAssertEqual(id.count, 19)
        XCTAssertFalse(id.contains("/"))
        XCTAssertFalse(id.contains("?"))
    }

    func testOfflineHLSVariantSelectionAndKeyMapRewriting() throws {
        let master = """
        #EXTM3U
        #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
        low/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
        high/index.m3u8
        """
        let baseURL = try XCTUnwrap(URL(string: "https://cdn.example.com/vod/master.m3u8"))
        let best = OfflineDownloadManager.selectBestVariantURL(masterPlaylist: master, baseURL: baseURL)
        XCTAssertEqual(best?.absoluteString, "https://cdn.example.com/vod/high/index.m3u8")

        let media = """
        #EXTM3U
        #EXT-X-KEY:METHOD=AES-128,URI="keys/enc.key"
        #EXT-X-MAP:URI="init.mp4"
        #EXTINF:4.0,
        chunk_0.m4s?sig=1
        #EXTINF:4.0,
        chunk_1.ts
        """
        let mediaBase = try XCTUnwrap(URL(string: "https://cdn.example.com/vod/high/index.m3u8"))
        let plan = OfflineDownloadManager.buildLocalHLSPlan(mediaPlaylist: media, baseURL: mediaBase)
        XCTAssertEqual(plan.auxiliaryAssets.count, 2)
        XCTAssertEqual(plan.segments.count, 2)
        XCTAssertEqual(plan.segments[0].localFileName, "segment_0.m4s")
        XCTAssertEqual(plan.segments[1].localFileName, "segment_1.ts")
        XCTAssertTrue(plan.rewrittenPlaylist.contains("URI=\"asset_key_0.key\""))
        XCTAssertTrue(plan.rewrittenPlaylist.contains("URI=\"asset_map_1.mp4\""))
    }

    func testOfflineLocalHTTPServerSuffixAndStandardByteRangeParsing() {
        XCTAssertEqual(OfflineLocalHTTPServer.parseByteRange(headerValue: "Range: bytes=0-499", totalLength: 1000), 0...499)
        XCTAssertEqual(OfflineLocalHTTPServer.parseByteRange(headerValue: "Range: bytes=-200", totalLength: 1000), 800...999)
        XCTAssertEqual(OfflineLocalHTTPServer.parseByteRange(headerValue: "Range: bytes=-2000", totalLength: 500), 0...499)
        XCTAssertNil(OfflineLocalHTTPServer.parseByteRange(headerValue: "Range: bytes=1200-1300", totalLength: 1000))
    }

    func testOfflineDownloadStatusProgressCalculations() {
        let queued = OfflineDownloadStatus.queued
        XCTAssertFalse(queued.isCompleted)
        XCTAssertTrue(queued.isDownloading)
        XCTAssertEqual(queued.progressValue, 0.0)

        let downloading = OfflineDownloadStatus.downloading(progress: 0.45, bytesWritten: 450, totalBytes: 1000)
        XCTAssertFalse(downloading.isCompleted)
        XCTAssertTrue(downloading.isDownloading)
        XCTAssertEqual(downloading.progressValue, 0.45, accuracy: 0.001)

        let failed = OfflineDownloadStatus.failed(message: "网络超时")
        XCTAssertFalse(failed.isCompleted)
        XCTAssertFalse(failed.isDownloading)
        XCTAssertEqual(failed.progressValue, 0.0)
    }

    func testOfflineDownloadManagerIsolatedDirectoryAndBackupExclusion() throws {
        XCTAssertTrue(manager.items.isEmpty)
        XCTAssertEqual(manager.totalDownloadedBytes, 0)
        XCTAssertEqual(manager.completedItems.count, 0)
        let values = try manager.baseDirectory.resourceValues(forKeys: [.isExcludedFromBackupKey])
        XCTAssertEqual(values.isExcludedFromBackup, true)
    }
}
