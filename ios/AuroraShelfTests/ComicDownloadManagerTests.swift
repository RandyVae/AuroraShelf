import XCTest
@testable import AuroraShelf

@MainActor
final class ComicDownloadManagerTests: XCTestCase {
    private var tempDir: URL!
    private var manager: ComicDownloadManager!

    override func setUp() {
        super.setUp()
        tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("AuroraComicDLTests_\(UUID().uuidString)", isDirectory: true)
        manager = ComicDownloadManager(baseDirectory: tempDir)
    }

    override func tearDown() {
        manager.removeAll()
        if let tempDir {
            try? FileManager.default.removeItem(at: tempDir)
        }
        manager = nil
        tempDir = nil
        super.tearDown()
    }

    func testInitialStateIsEmptyAndExcludedFromBackup() throws {
        XCTAssertTrue(manager.chapters.isEmpty)
        XCTAssertEqual(manager.totalDownloadedBytes, 0)
        XCTAssertFalse(manager.formattedTotalSpace().isEmpty)
        let values = try manager.baseDir.resourceValues(forKeys: [.isExcludedFromBackupKey])
        XCTAssertEqual(values.isExcludedFromBackup, true)
    }

    func testIsDownloadedWhenNoData() {
        XCTAssertFalse(manager.isDownloaded(comicId: "test_c1", chapterId: "ch_1", sourceId: "baozi"))
        XCTAssertNil(manager.status(for: "test_c1", chapterId: "ch_1", sourceId: "baozi"))
    }

    func testSourceScopedDownloadIDAndLegacyRemoveCompatibility() {
        let ch = ComicChapter(id: "ch_1", title: "第1话", pageCount: 10)
        let comicA = ComicItem(id: "100", sourceId: "baozi", title: "漫画A", author: "作者", coverUrl: "https://example.com/a.jpg", chapters: [ch])
        let comicB = ComicItem(id: "100", sourceId: "jm", title: "漫画B", author: "作者", coverUrl: "https://example.com/b.jpg", chapters: [ch])

        manager.startDownload(comic: comicA, chapter: ch)
        manager.startDownload(comic: comicB, chapter: ch)
        XCTAssertEqual(manager.chapters.count, 2)
        XCTAssertNotNil(manager.status(for: "100", chapterId: "ch_1", sourceId: "baozi"))
        XCTAssertNotNil(manager.status(for: "100", chapterId: "ch_1", sourceId: "jm"))

        manager.removeDownload(id: "baozi_100_ch_1")
        XCTAssertEqual(manager.chapters.count, 1)
        XCTAssertNil(manager.status(for: "100", chapterId: "ch_1", sourceId: "baozi"))
        XCTAssertNotNil(manager.status(for: "100", chapterId: "ch_1", sourceId: "jm"))

        // Legacy ID removal compatibility ("comicId_chapterId")
        manager.removeDownload(id: "100_ch_1")
        XCTAssertTrue(manager.chapters.isEmpty)
    }

    func testFormattedTotalSpaceWithZeroBytesIsLocaleIndependent() {
        let formatted = manager.formattedTotalSpace()
        let expected = ByteCountFormatter.string(fromByteCount: 0, countStyle: .file)
        XCTAssertEqual(formatted, expected)
    }
}
