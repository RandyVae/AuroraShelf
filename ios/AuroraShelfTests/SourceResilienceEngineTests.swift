import XCTest
@testable import AuroraShelf

final class SourceResilienceEngineTests: XCTestCase {
    func testHedgedMirrorRacingFailsOverFastAndPromotesHealthyMirror() async throws {
        let nowBox = LockBox(Date(timeIntervalSince1970: 1_000_000))
        let registry = SourceHealthRegistry(defaults: nil, clock: { nowBox.get() })
        let engine = AuroraNetworkEngine(registry: registry)

        let deadURL = URL(string: "https://dead-primary.example/api")!
        let fastURL = URL(string: "https://fast-backup.example/api")!
        let primaryCount = LockBox(0)
        let backupCount = LockBox(0)

        let start1 = Date()
        let first = try await engine.fetchHedged(
            sourceKey: "test-copy",
            candidateURLs: [deadURL, fastURL],
            hedgeDelayMs: 180,
            executor: { url, _ in
                if url.host == "dead-primary.example" {
                    primaryCount.update { $0 += 1 }
                    try await Task.sleep(nanoseconds: 3_000_000_000)
                    throw AuroraNetworkError.invalidResponse
                } else {
                    backupCount.update { $0 += 1 }
                    try await Task.sleep(nanoseconds: 30_000_000)
                    registry.recordSuccess(sourceKey: "test-copy", urlOrHost: url.absoluteString, latencyMs: 30)
                    return AuroraNetworkResponse(data: Data("ok-backup".utf8), statusCode: 200, finalURL: url, latencyMs: 30)
                }
            }
        )
        let elapsed1 = Date().timeIntervalSince(start1)
        XCTAssertEqual(String(data: first.data, encoding: .utf8), "ok-backup")
        XCTAssertLessThan(elapsed1, 1.0)

        // 记录主站阻塞熔断
        registry.recordFailure(sourceKey: "test-copy", urlOrHost: deadURL.absoluteString, statusCode: 502, isHardBlock: true)
        XCTAssertTrue(registry.isCircuitOpen(urlOrHost: "dead-primary.example"))

        // 第二次调用应跳过死节点直接命中 fast-backup
        let start2 = Date()
        let second = try await engine.fetchHedged(
            sourceKey: "test-copy",
            candidateURLs: [deadURL, fastURL],
            hedgeDelayMs: 180,
            executor: { url, _ in
                if url.host == "dead-primary.example" {
                    primaryCount.update { $0 += 1 }
                    throw AuroraNetworkError.invalidResponse
                }
                backupCount.update { $0 += 1 }
                return AuroraNetworkResponse(data: Data("direct-fast".utf8), statusCode: 200, finalURL: url, latencyMs: 15)
            }
        )
        let elapsed2 = Date().timeIntervalSince(start2)
        XCTAssertEqual(String(data: second.data, encoding: .utf8), "direct-fast")
        XCTAssertEqual(primaryCount.get(), 1)
        XCTAssertEqual(backupCount.get(), 2)
        XCTAssertLessThan(elapsed2, 0.15)
    }

    func testCircuitBreakerCooldownAndRecovery() {
        let nowBox = LockBox(Date(timeIntervalSince1970: 5_000_000))
        let registry = SourceHealthRegistry(defaults: nil, clock: { nowBox.get() })

        registry.recordFailure(sourceKey: "copy", urlOrHost: "https://api.2024manga.com", statusCode: 504, isHardBlock: true)
        XCTAssertTrue(registry.isCircuitOpen(urlOrHost: "api.2024manga.com"))

        registry.recordSuccess(sourceKey: "copy", urlOrHost: "https://api.mangacopy.com", latencyMs: 280)
        let ranked = registry.rankCandidates(sourceKey: "copy", candidates: ["https://api.2024manga.com", "https://api.mangacopy.com"])
        XCTAssertEqual(ranked.first, "https://api.mangacopy.com")

        nowBox.update { $0 = $0.addingTimeInterval(31) }
        XCTAssertFalse(registry.isCircuitOpen(urlOrHost: "api.2024manga.com"))
    }

    func testAdaptiveHTMLParserRecoversMutatedSelectors() {
        let mutatedHTML = """
        <html><body>
          <div class="new-2027-card">
            <a href="/view_video.php?viewkey=ios7788">
              <img data-original="/thumb/ios7788.jpg" alt="iOS新类名自愈视频" />
              <span class="badge-new">09:15</span>
            </a>
          </div>
          <a href="/login">登录</a>
        </body></html>
        """
        let recovered = AdaptiveHTMLFallbackParser.extractFallbackVideoItems(
            html: mutatedHTML,
            baseURL: "https://h1014.sol148.com",
            sourceName: "91视频",
            category: "全部"
        )
        XCTAssertEqual(recovered.count, 1)
        XCTAssertEqual(recovered.first?.title, "iOS新类名自愈视频")
        XCTAssertEqual(recovered.first?.playbackUrl, "https://h1014.sol148.com/view_video.php?viewkey=ios7788")
        XCTAssertEqual(recovered.first?.coverUrl, "https://h1014.sol148.com/thumb/ios7788.jpg")
        XCTAssertEqual(recovered.first?.duration, "09:15")
    }

    func testMultiSlotSnapshotStoreSupportsSwrAndStaleIfError() {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("aurora-swr-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let nowBox = LockBox(Date(timeIntervalSince1970: 2_000_000))
        let store = SourceSnapshotStore(directory: tempDir, clock: { nowBox.get() })

        let vA = [VideoItem(id: "a", title: "华视电影A", subtitle: "测试", coverUrl: "https://huavod.com/1.jpg", playbackUrl: "https://huavod.com/voddetail/1.html", sourceName: "华视影院")]
        let vB = [VideoItem(id: "b", title: "91视频B", subtitle: "测试", coverUrl: "https://h1014.sol148.com/2.jpg", playbackUrl: "https://h1014.sol148.com/view_video.php?viewkey=b", sourceName: "91视频")]

        store.putVideoFeed(sourceId: "huavod", category: "全部", items: vA)
        store.putVideoFeed(sourceId: "91porn", category: "全部", items: vB)

        let snapA = store.getVideoFeed(sourceId: "huavod", category: "全部")
        let snapB = store.getVideoFeed(sourceId: "91porn", category: "全部")
        XCTAssertEqual(snapA?.items.first?.title, "华视电影A")
        XCTAssertEqual(snapB?.items.first?.title, "91视频B")
        XCTAssertEqual(snapA?.isFresh, true)

        // 超过 90 秒仍在 72 小时 Stale-If-Error 保护期内
        nowBox.update { $0 = $0.addingTimeInterval(150) }
        let staleA = store.getVideoFeed(sourceId: "huavod", category: "全部")
        XCTAssertEqual(staleA?.isFresh, false)
        XCTAssertEqual(staleA?.items.first?.title, "华视电影A")
    }

    func testForumTrustedHostAndPortalDiscoverySupportsRotatedDomain() {
        let portalHTML = #"<a href="https://t931.sol140.com">自拍论坛</a>"#
        let extracted = ForumSourceAdapter.extractPortalForumBaseURL(from: portalHTML)
        XCTAssertEqual(extracted, "https://t931.sol140.com")
        XCTAssertTrue(ForumSourceAdapter.isTrustedForumHost("t931.sol140.com"))
        XCTAssertTrue(ForumSourceAdapter.isTrustedForumHost("t924.sol140.com"))
        XCTAssertTrue(ForumSourceAdapter.isTrustedForumHost("t66y.com"))
        XCTAssertFalse(ForumSourceAdapter.isTrustedForumHost("evil-phishing.com"))
    }

    @MainActor
    func testLiveComicForumAndLiveSourcesAuditWhenOptedIn() async throws {
        guard ProcessInfo.processInfo.environment["RUN_IOS_LIVE_SOURCE_AUDIT"] == "1" else {
            throw XCTSkip("Set RUN_IOS_LIVE_SOURCE_AUDIT=1 to probe live comic, forum, and live stream sources.")
        }

        // 1. 全部 9 个免登录漫画源：列表 + 详情章节 + 章节正片页 + 封面解码
        for source in ComicSourceCatalog.allSources where source.id != "picacg" {
            let started = Date()
            let category = source.categories.first ?? "全部"
            let items = try await ComicRepository.shared.fetchRealComics(source: source, category: category, page: 1)
            XCTAssertFalse(items.isEmpty, "LIVE_COMIC_SOURCE \(source.id) returned empty items")
            let detailed = try await ComicRepository.shared.fetchComicDetails(items[0])
            XCTAssertFalse(detailed.chapters.isEmpty, "LIVE_COMIC_SOURCE \(source.id) returned empty chapters")
            let pages = try await ComicRepository.shared.fetchChapterPages(
                comicId: detailed.id,
                sourceId: source.id,
                chapterId: detailed.chapters[0].id
            )
            XCTAssertFalse(pages.isEmpty, "LIVE_COMIC_SOURCE \(source.id) returned empty pages")
            var decoded = false
            for candidate in items.prefix(3) where !candidate.coverUrl.isEmpty {
                if await CoverImageLoader.shared.loadImage(from: candidate.coverUrl, baseURL: candidate.coverBaseURL) != nil {
                    decoded = true
                    break
                }
            }
            if !decoded, let firstPage = pages.first {
                decoded = await CoverImageLoader.shared.loadImage(
                    from: firstPage.imageUrl,
                    baseURL: detailed.coverBaseURL,
                    requestReferer: firstPage.referer
                ) != nil
            }
            let elapsedMs = Int(Date().timeIntervalSince(started) * 1000)
            print("LIVE_COMIC_SOURCE \(source.id) items=\(items.count) chapters=\(detailed.chapters.count) pages=\(pages.count) decoded=\(decoded) ms=\(elapsedMs)")
            XCTAssertTrue(decoded, "LIVE_COMIC_SOURCE \(source.id) failed to decode cover/page image")
        }

        // 2. 全部 7 个论坛版块 + 帖子详情抓取
        var sampledDetailPost: ForumPostItem?
        for board in ForumSourceCatalog.allBoards {
            let started = Date()
            let posts = try await ForumRepository.fetchRealForumPosts(fid: board.fid, page: 1, session: AuroraNetworkEngine.shared.session)
            let elapsedMs = Int(Date().timeIntervalSince(started) * 1000)
            print("LIVE_FORUM_BOARD \(board.id) fid=\(board.fid) items=\(posts.count) ms=\(elapsedMs)")
            XCTAssertFalse(posts.isEmpty, "LIVE_FORUM_BOARD \(board.id) returned empty posts")
            if sampledDetailPost == nil {
                sampledDetailPost = posts.first
            }
        }
        if let sample = sampledDetailPost {
            let detailStarted = Date()
            let detail = try await ForumRepository.shared.loadDetail(for: sample)
            let detailMs = Int(Date().timeIntervalSince(detailStarted) * 1000)
            print("LIVE_FORUM_DETAIL id=\(detail.id) textLen=\(detail.contentText.count) images=\(detail.contentImageUrls.count) ms=\(detailMs)")
            XCTAssertTrue(!detail.contentText.isEmpty || !detail.contentImageUrls.isEmpty, "Forum detail returned empty text and images")
        }

        // 3. 直播流源
        let liveStarted = Date()
        await LiveRepository.shared.refreshStreams()
        let liveMs = Int(Date().timeIntervalSince(liveStarted) * 1000)
        print("LIVE_STREAM_AUDIT count=\(LiveRepository.shared.liveStreams.count) ms=\(liveMs)")
        XCTAssertGreaterThan(LiveRepository.shared.liveStreams.count, 0, "Live streams returned empty list")
    }
}

private final class LockBox<Value>: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Value
    init(_ initial: Value) { self.value = initial }
    func get() -> Value {
        lock.lock(); defer { lock.unlock() }; return value
    }
    func update(_ transform: (inout Value) -> Void) {
        lock.lock(); transform(&value); lock.unlock()
    }
}
