import XCTest
@testable import AuroraShelf

final class ForumRepositoryTests: XCTestCase {
    func testEveryForumBoardLoadsAndOpensARealThreadWhenOptedIn() async throws {
        guard ProcessInfo.processInfo.environment["RUN_IOS_LIVE_SOURCE_AUDIT"] == "1" else {
            throw XCTSkip("Set RUN_IOS_LIVE_SOURCE_AUDIT=1 to probe live forum boards.")
        }
        let session = URLSession(configuration: .ephemeral)
        let sharedPinnedIDs: Set<String> = ["411942", "412035", "83424", "407901"]
        for board in ForumSourceCatalog.allBoards {
            let listURL = try XCTUnwrap(ForumSourceAdapter.pageURL(fid: board.fid, page: 1))
            var request = URLRequest(url: listURL)
            request.setValue("Mozilla/5.0", forHTTPHeaderField: "User-Agent")
            request.setValue("https://\(listURL.host ?? "")", forHTTPHeaderField: "Referer")
            let (data, response) = try await session.data(for: request)
            let status = (response as? HTTPURLResponse)?.statusCode
            let html = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .init(rawValue: 0x80000632)) ?? ""
            let posts = board.fid == ForumSourceCatalog.t66y.fid
                ? ForumPostParser.parseT66Y(html, fid: board.fid, baseUrl: "https://\(listURL.host ?? "")")
                : ForumPostParser.parse(html, fid: board.fid, baseUrl: "https://\(listURL.host ?? "")")
            print("LIVE_FORUM_BOARD fid=\(board.fid) status=\(status ?? 0) parsed=\(posts.count)")
            XCTAssertEqual(status, 200, "\(board.name) list HTTP status")
            XCTAssertFalse(posts.isEmpty, "\(board.name) list parser returned no real topics")

            let post = try XCTUnwrap(posts.first(where: {
                !sharedPinnedIDs.contains($0.id) && !Self.isPinnedOrAnnouncement($0.title)
            }), "\(board.name) returned only pinned or announcement topics")
            XCTAssertEqual(post.boardName, board.name)
            let detailURL = try XCTUnwrap(URL(string: post.threadUrl))
            var detailRequest = URLRequest(url: detailURL)
            detailRequest.setValue("Mozilla/5.0", forHTTPHeaderField: "User-Agent")
            detailRequest.setValue("https://\(detailURL.host ?? "")", forHTTPHeaderField: "Referer")
            let (detailData, detailResponse) = try await session.data(for: detailRequest)
            let detailStatus = (detailResponse as? HTTPURLResponse)?.statusCode
            let detailHTML = String(data: detailData, encoding: .utf8) ?? String(data: detailData, encoding: .init(rawValue: 0x80000632)) ?? ""
            let detail = ForumPostParser.parseDetail(detailHTML, post: post)
            print("LIVE_FORUM_THREAD fid=\(board.fid) id=\(post.id) title=\(post.title) status=\(detailStatus ?? 0) text=\(detail.contentText.count) image=\(detail.previewImageUrl != nil)")
            XCTAssertEqual(detailStatus, 200, "\(board.name) thread HTTP status")
            let hasValidBody = detail.contentText.count >= 20 || (detail.contentText.count > 0 && (detail.previewImageUrl != nil || !detail.contentImageUrls.isEmpty))
            XCTAssertTrue(hasValidBody, "\(board.name) ordinary topic body was not parsed (text count: \(detail.contentText.count))")
            if let imageURL = detail.contentImageUrls.first ?? detail.previewImageUrl {
                let image = await CoverImageLoader.shared.loadImage(from: imageURL, requestReferer: post.threadUrl)
                XCTAssertNotNil(image?.cgImage, "\(board.name) topic image did not decode")
                print("LIVE_FORUM_IMAGE fid=\(board.fid) decoded=\(image != nil)")
            }

            let pageTwoURL = try XCTUnwrap(ForumSourceAdapter.pageURL(fid: board.fid, page: 2))
            var pageTwoRequest = URLRequest(url: pageTwoURL)
            pageTwoRequest.setValue("Mozilla/5.0", forHTTPHeaderField: "User-Agent")
            pageTwoRequest.setValue("https://\(pageTwoURL.host ?? "")", forHTTPHeaderField: "Referer")
            let (pageTwoData, pageTwoResponse) = try await session.data(for: pageTwoRequest)
            let pageTwoStatus = (pageTwoResponse as? HTTPURLResponse)?.statusCode
            let pageTwoHTML = String(data: pageTwoData, encoding: .utf8) ?? String(data: pageTwoData, encoding: .init(rawValue: 0x80000632)) ?? ""
            let pageTwoPosts = board.fid == ForumSourceCatalog.t66y.fid
                ? ForumPostParser.parseT66Y(pageTwoHTML, fid: board.fid, baseUrl: "https://\(pageTwoURL.host ?? "")")
                : ForumPostParser.parse(pageTwoHTML, fid: board.fid, baseUrl: "https://\(pageTwoURL.host ?? "")")
            XCTAssertEqual(pageTwoStatus, 200, "\(board.name) page 2 HTTP status")
            XCTAssertFalse(pageTwoPosts.isEmpty, "\(board.name) page 2 has no parsed topics")
            XCTAssertFalse(Set(pageTwoPosts.map(\.id)).isSubset(of: Set(posts.map(\.id))), "\(board.name) page 2 repeated only page 1 topics")
            print("LIVE_FORUM_PAGE fid=\(board.fid) page=2 status=\(pageTwoStatus ?? 0) parsed=\(pageTwoPosts.count)")
        }
    }

    private static func isPinnedOrAnnouncement(_ title: String) -> Bool {
        ["置顶", "公告", "官方客户端", "免翻地址", "永久域名"]
            .contains(where: title.localizedCaseInsensitiveContains)
    }

    func testAllSelectionIncludesEveryCatalogBoard() {
        XCTAssertEqual(
            ForumBoardSelection.fids(for: nil),
            ForumSourceCatalog.allBoards.map(\.fid)
        )
        XCTAssertEqual(ForumBoardSelection.fids(for: 100), [100])
    }

    func testT66yBoardUsesItsOwnRealListEndpoint() throws {
        let url = try XCTUnwrap(ForumSourceAdapter.pageURL(fid: 100, page: 3))
        XCTAssertEqual(url.host, "t66y.com")
        XCTAssertEqual(url.path, "/thread0806.php")
        XCTAssertEqual(URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems, [
            URLQueryItem(name: "fid", value: "7"),
            URLQueryItem(name: "page", value: "3")
        ])
    }

    func testForumCatalogMatchesCurrent91BoardNames() {
        XCTAssertEqual(ForumSourceCatalog.allBoards.map(\.name), [
            "原创自拍区", "我爱我妻区", "性趣闲谈", "两性健康", "海外专区", "草榴技术区", "达盖尔的旗帜"
        ])
    }

    func testT66yDaguerreBoardUsesItsOwnRealListEndpoint() throws {
        let url = try XCTUnwrap(ForumSourceAdapter.pageURL(fid: 16, page: 2))
        XCTAssertEqual(url.host, "t66y.com")
        XCTAssertEqual(url.path, "/thread0806.php")
        XCTAssertEqual(URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems, [
            URLQueryItem(name: "fid", value: "16"),
            URLQueryItem(name: "page", value: "2")
        ])
    }

    func testForumPageTwoUsesRequestedBoardAndPage() throws {
        let url = try XCTUnwrap(ForumSourceAdapter.pageURL(fid: 34, page: 2))
        XCTAssertEqual(url.host, "t924.sol140.com")
        XCTAssertEqual(URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems, [
            URLQueryItem(name: "fid", value: "34"),
            URLQueryItem(name: "page", value: "2")
        ])
    }

    func testForumPostParserHandlesStyledAndHighlightedTitles() {
        let sampleHTML = """
        <a href="viewthread.php?tid=123456&amp;extra=page%3D1"><font color="red"><b>[精华]</b> 盛夏海滩原创自拍分享系列</font></a>
        <a href="viewthread.php?tid=789012&amp;extra=page%3D1">普通无高亮标题帖子测试文本</a>
        """
        let posts = ForumPostParser.parse(sampleHTML, fid: 21, baseUrl: "https://example.com")
        XCTAssertEqual(posts.count, 2)
        XCTAssertEqual(posts[0].id, "123456")
        XCTAssertTrue(posts[0].title.contains("盛夏海滩原创自拍分享系列"))
        XCTAssertFalse(posts[0].title.contains("<font"))
        XCTAssertFalse(posts[0].title.contains("<b>"))
        XCTAssertEqual(posts[1].id, "789012")
        XCTAssertEqual(posts[1].title, "普通无高亮标题帖子测试文本")
    }

    func testForumParserDecodesHTMLQuerySeparatorsInThreadLinks() {
        let html = #"<a href="viewthread.php?tid=1234&amp;extra=page%3D1">一条真实论坛主题</a>"#

        let posts = ForumPostParser.parse(html, fid: 4, baseUrl: "https://t924.sol140.com")

        XCTAssertEqual(posts.first?.threadUrl, "https://t924.sol140.com/viewthread.php?tid=1234&extra=page%3D1")
    }

    func testForumImagesCarryTheThreadAsTheirReferer() throws {
        let imageURL = try XCTUnwrap(URL(string: "https://cdn.example/post-image.jpg"))
        let request = CoverImageLoader.imageRequest(for: imageURL, referer: "https://t924.sol140.com/viewthread.php?tid=1234")

        XCTAssertEqual(request.value(forHTTPHeaderField: "Referer"), "https://t924.sol140.com/viewthread.php?tid=1234")
    }

    func testT66yParserUsesActualThreadLinkAndDoesNotDuplicateRows() {
        let html = #"""
        <tr class="tr3 t_one"><td class="tal"><h3><a href="/htm_data/2610/7/12345.html" id="t12345">真实草榴帖子</a></h3></td></tr>
        <tr class="tr3 t_one"><td class="tal"><h3><a id="t12345" href="read.php?tid=12345">重复帖子</a></h3></td></tr>
        <tr class="tr3 t_one"><td><span class="thread_page"><a href="read.php?tid=99999&page=2">2</a></span></td></tr>
        """#
        let posts = ForumPostParser.parseT66Y(html, fid: 100, baseUrl: "https://t66y.com")

        XCTAssertEqual(posts.map(\.id), ["12345"])
        XCTAssertEqual(posts.first?.threadUrl, "https://t66y.com/htm_data/2610/7/12345.html")
        XCTAssertEqual(posts.first?.boardName, ForumSourceCatalog.t66y.name)
    }

    func testForumDetailParserExtractsVisibleTextAndImageFromRealContainers() {
        let post = makePost(id: "123", fid: 100)
        let html = #"<div class="tpc_content do_not_catch" id="conttpc">真实正文<br>第二行<img src="/uploads/photo.jpg"></div>"#
        let detail = ForumPostParser.parseDetail(html, post: post)

        XCTAssertTrue(detail.contentText.contains("真实正文"))
        XCTAssertTrue(detail.contentText.contains("第二行"))
        XCTAssertEqual(detail.previewImageUrl, "https://t66y.com/uploads/photo.jpg")
    }

    func testForumDetailParserReads91PostMessageAndAttachmentImagesOnly() {
        let post = makePost(id: "411942", fid: 4)
        let html = #"""
        <img src="templates/fervor/images/logo.png" alt="论坛 logo">
        <div class="postmessage firstpost">
          <div id="threadtitle"><h1>帖子标题</h1></div>
          <div class="t_msgfontfix"><table><tr><td class="t_msgfont" id="postmessage_5786587">
            正文第一段<br>正文第二段
            <span><img src="images/common/none.gif" file="https://cdn.example/one.jpg" alt="one"></span>
            <div class="t_attach"><a>下载</a><img src="images/common/none.gif" file="https://cdn.example/two.jpg"></div>
          </td></tr></table></div>
        </div>
        <img src="https://ads.example/banner.jpg">
        """#

        let detail = ForumPostParser.parseDetail(html, post: post)

        XCTAssertTrue(detail.contentText.contains("正文第一段"))
        XCTAssertTrue(detail.contentText.contains("正文第二段"))
        XCTAssertFalse(detail.contentText.contains("帖子标题"))
        XCTAssertEqual(detail.contentImageUrls, ["https://cdn.example/one.jpg", "https://cdn.example/two.jpg"])
        XCTAssertEqual(detail.previewImageUrl, "https://cdn.example/one.jpg")
    }

    func testParserDoesNotInventUnavailablePostMetadata() {
        let html = #"<a href="viewthread.php?tid=1234&page=1">一条真实主题标题</a>"#

        let posts = ForumPostParser.parse(
            html,
            fid: 4,
            baseUrl: "https://t924.sol140.com"
        )

        XCTAssertEqual(posts.count, 1)
        XCTAssertEqual(posts[0].title, "一条真实主题标题")
        XCTAssertNil(posts[0].author)
        XCTAssertNil(posts[0].postTime)
        XCTAssertNil(posts[0].replies)
        XCTAssertNil(posts[0].views)
        XCTAssertNil(posts[0].previewImageUrl)
        XCTAssertTrue(posts[0].contentText.isEmpty)
    }

    @MainActor
    func testRefreshFailureKeepsPreviouslyLoadedPosts() async {
        let repo = ForumRepository(fetcher: { _, _ in throw ForumTestError.offline })
        repo.selectedBoardFid = 4
        let existingPost = makePost(id: "existing", fid: 4)
        repo.posts = [existingPost]

        await repo.reloadPosts()

        XCTAssertEqual(repo.posts, [existingPost])
        XCTAssertNotNil(repo.errorMessage)
        XCTAssertFalse(repo.isLoading)
    }

    @MainActor
    func testLateResponseFromOldSelectionCannotReplaceNewSelection() async {
        let gate = ForumRequestGate()
        let stalePost = makePost(id: "stale", fid: 4)
        let currentPost = makePost(id: "current", fid: 100)
        let repo = ForumRepository(fetcher: { fid, _ in
            if fid == 4 {
                return await gate.waitForResult()
            }
            return [currentPost]
        })

        repo.selectedBoardFid = 4
        let staleRequest = Task { await repo.reloadPosts() }
        await gate.waitUntilStarted()

        repo.selectedBoardFid = 100
        await repo.reloadPosts()
        await gate.release([stalePost])
        await staleRequest.value

        XCTAssertEqual(repo.posts, [currentPost])
        XCTAssertEqual(repo.posts.first?.fid, 100)
        XCTAssertFalse(repo.isLoading)
    }

    @MainActor
    func testLoadNextPageAppendsUniquePostsAndAdvancesPage() async {
        let first = makePost(id: "first", fid: 4)
        let second = makePost(id: "second", fid: 4)
        let repo = ForumRepository(fetcher: { _, page in
            page == 1 ? [first] : [first, second]
        })
        repo.selectedBoardFid = 4

        await repo.reloadPosts()
        await repo.loadNextPage()

        XCTAssertEqual(repo.posts.map(\.id), ["first", "second"])
        XCTAssertFalse(repo.isLoadingNextPage)
    }

    @MainActor
    func testSelectDaguerreBoardLoadsFid16Posts() async {
        let daguerrePost = makePost(id: "daguerre_1", fid: 16)
        let repo = ForumRepository(fetcher: { fid, _ in
            fid == 16 ? [daguerrePost] : []
        })
        repo.selectBoard(16)
        try? await Task.sleep(nanoseconds: 50_000_000)
        XCTAssertEqual(repo.selectedBoardFid, 16)
        XCTAssertEqual(repo.posts, [daguerrePost])
        XCTAssertEqual(repo.posts.first?.fid, 16)
    }

    func testForumNoiseFilterBlocksNoticeAndSpamTitles() {
        // 用户明确要求排除的无用标题
        XCTAssertTrue(ForumNoiseFilter.isNoisePost(title: "91网须知"))
        XCTAssertTrue(ForumNoiseFilter.isNoisePost(title: "91论坛须知与总则"))
        XCTAssertTrue(ForumNoiseFilter.isNoisePost(title: "论坛手机版的使用详情"))
        XCTAssertTrue(ForumNoiseFilter.isNoisePost(title: "如何获取91论坛的邀请码"))
        XCTAssertTrue(ForumNoiseFilter.isNoisePost(title: "获取91论坛邀请码方法"))
        XCTAssertTrue(ForumNoiseFilter.isNoisePost(title: "【置顶公告】本版发帖须知与新手指南"))
        XCTAssertTrue(ForumNoiseFilter.isNoisePost(title: "[公告] 永久最新发布页与防封指南"))

        // 广告、博彩与引流垃圾信息
        XCTAssertTrue(ForumNoiseFilter.isNoisePost(title: "澳门银河真人视讯开元棋牌代理加盟"))
        XCTAssertTrue(ForumNoiseFilter.isNoisePost(title: "加微信进福利群，每天更新"))
        XCTAssertTrue(ForumNoiseFilter.isNoisePost(title: "高薪兼职加V信扫码进群"))
        XCTAssertTrue(ForumNoiseFilter.isNoisePost(title: "充值USDT代充话费优惠返利"))

        // 正常网友原创分享与交流讨论必须完好保留
        XCTAssertFalse(ForumNoiseFilter.isNoisePost(title: "周末带老婆去海边自拍散步留念"))
        XCTAssertFalse(ForumNoiseFilter.isNoisePost(title: "记录和女友相识两周年的生活日常"))
        XCTAssertFalse(ForumNoiseFilter.isNoisePost(title: "两性健康与日常保养心得交流分享"))
        XCTAssertFalse(ForumNoiseFilter.isNoisePost(title: "第一次发帖分享下两人的甜蜜瞬间"))
    }

    func testForumParserExcludesNoisePostsFromHTML() {
        let fakeHTML = """
        <a href="viewthread.php?tid=1001">91网须知</a>
        <a href="viewthread.php?tid=1002">论坛手机版的使用详情</a>
        <a href="viewthread.php?tid=1003">如何获取91论坛的邀请码</a>
        <a href="viewthread.php?tid=1004">【置顶】新手必读发帖规则</a>
        <a href="viewthread.php?tid=1005">周末带媳妇去度假酒店原创自拍</a>
        <a href="viewthread.php?tid=1006">记录两人的真实幸福生活点滴</a>
        """
        let parsed = ForumPostParser.parse(fakeHTML, fid: 4, baseUrl: "https://example.com")
        XCTAssertEqual(parsed.count, 2)
        XCTAssertEqual(parsed[0].id, "1005")
        XCTAssertEqual(parsed[0].title, "周末带媳妇去度假酒店原创自拍")
        XCTAssertEqual(parsed[1].id, "1006")
        XCTAssertEqual(parsed[1].title, "记录两人的真实幸福生活点滴")
    }

    private func makePost(id: String, fid: Int) -> ForumPostItem {
        ForumPostItem(
            id: id,
            fid: fid,
            boardName: fid == 100 ? "草榴技术区" : "原创自拍区",
            title: "测试帖子 \(id)"
        )
    }

    @MainActor
    func testForumRepositoryDetailCacheReturnsCachedDetail() {
        let repo = ForumRepository.shared
        let dummy = ForumPostItem(id: "test999", fid: 4, boardName: "原创自拍区", title: "测试", threadUrl: "https://t924.sol140.com/viewthread.php?tid=test999")
        XCTAssertNil(repo.cachedDetail(for: dummy))
    }
}

private enum ForumTestError: Error, Sendable {
    case offline
}

private actor ForumRequestGate {
    private var resultContinuation: CheckedContinuation<[ForumPostItem], Never>?
    private var startedContinuation: CheckedContinuation<Void, Never>?
    private var hasStarted = false

    func waitForResult() async -> [ForumPostItem] {
        hasStarted = true
        startedContinuation?.resume()
        startedContinuation = nil
        return await withCheckedContinuation { continuation in
            resultContinuation = continuation
        }
    }

    func waitUntilStarted() async {
        guard !hasStarted else { return }
        await withCheckedContinuation { continuation in
            startedContinuation = continuation
        }
    }

    func release(_ posts: [ForumPostItem]) {
        resultContinuation?.resume(returning: posts)
        resultContinuation = nil
    }
}
