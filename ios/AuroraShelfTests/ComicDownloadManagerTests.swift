import XCTest
@testable import AuroraShelf

@MainActor
final class ComicDownloadManagerTests: XCTestCase {
    private var manager: ComicDownloadManager!

    override func setUp() {
        super.setUp()
        manager = ComicDownloadManager.shared
        manager.removeAll()
    }

    override func tearDown() {
        manager.removeAll()
        super.tearDown()
    }

    func testInitialStateIsEmpty() {
        XCTAssertTrue(manager.chapters.isEmpty)
        XCTAssertEqual(manager.formattedTotalSpace(), "Zero KB")
    }

    func testIsDownloadedWhenNoData() {
        XCTAssertFalse(manager.isDownloaded(comicId: "test_c1", chapterId: "ch_1"))
        XCTAssertNil(manager.status(for: "test_c1", chapterId: "ch_1"))
    }

    func testRemoveDownload() {
        let ch = ComicChapter(id: "ch_1", title: "第1话", pageCount: 10)
        let comic = ComicItem(id: "c_1", sourceId: "baozi", title: "测试漫画", author: "测试作者", coverUrl: "https://example.com/cover.jpg", chapters: [ch])

        manager.startDownload(comic: comic, chapter: ch)
        XCTAssertEqual(manager.chapters.count, 1)

        manager.removeDownload(id: "c_1_ch_1")
        XCTAssertTrue(manager.chapters.isEmpty)
    }

    func testFormattedTotalSpaceWithZeroBytes() {
        XCTAssertTrue(manager.formattedTotalSpace().contains("0") || manager.formattedTotalSpace().contains("Zero"))
    }
}
