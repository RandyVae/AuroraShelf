import XCTest
@testable import AuroraShelf

final class OfflineDownloadManagerTests: XCTestCase {
    
    @MainActor
    func testOfflineDownloadItemToVideoItemMapping() {
        let item = OfflineDownloadItem(
            id: "vod-202979_ep1",
            videoId: "hua-202979",
            title: "希望",
            episodeLabel: "第1集",
            sourceName: "华视影院",
            pageUrl: "https://huavod.com/vodplay/202979-1-1.html",
            coverUrl: "https://static.huarenlivewebsite.top/piccdn/olo/movie/202979_image.jpg",
            streamUrl: "https://huavod.com/link/penny-test.m3u8",
            localRelativePath: "vod-202979_ep1/local_index.m3u8",
            totalBytes: 52428800,
            downloadedBytes: 52428800,
            status: .completed,
            createdAt: Date()
        )
        
        let video = item.toVideoItem()
        XCTAssertEqual(video.id, "hua-202979")
        XCTAssertEqual(video.title, "希望 · 第1集")
        XCTAssertEqual(video.sourceName, "华视影院")
        XCTAssertTrue(item.status.isCompleted)
        XCTAssertFalse(item.status.isDownloading)
        XCTAssertEqual(item.status.progressValue, 1.0)
    }
    
    @MainActor
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
    
    @MainActor
    func testOfflineDownloadManagerItemManagement() {
        let manager = OfflineDownloadManager.shared
        manager.removeAll()
        XCTAssertTrue(manager.items.isEmpty)
        XCTAssertEqual(manager.totalDownloadedBytes, 0)
        XCTAssertEqual(manager.completedItems.count, 0)
    }
}
