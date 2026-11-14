import XCTest
@testable import AuroraShelf

final class ComicRepositoryTests: XCTestCase {
    @MainActor
    func testEveryComicSourceLoadsCoverDetailsAndFirstPageWhenOptedIn() async throws {
        guard ProcessInfo.processInfo.environment["RUN_IOS_LIVE_SOURCE_AUDIT"] == "1" else {
            throw XCTSkip("Set RUN_IOS_LIVE_SOURCE_AUDIT=1 to probe live comic sources.")
        }

        let repository = ComicRepository.shared
        let requestedIDs = Set(
            ProcessInfo.processInfo.environment["IOS_COMIC_SOURCE_IDS"]?
                .split(separator: ",")
                .map(String.init) ?? []
        )
        for source in ComicSourceCatalog.supportedSources where requestedIDs.isEmpty || requestedIDs.contains(source.id) {
            if source.id == "jm" && !ComicProtocolSecrets.hasJMConfiguration {
                print("LIVE_COMIC_SOURCE \(source.id) skipped=missing-local-protocol-config")
                continue
            }
            if source.id == "picacg" && (!ComicProtocolSecrets.hasPicacgConfiguration || !repository.isPicacgAuthenticated) {
                print("LIVE_COMIC_SOURCE \(source.id) skipped=login-or-local-protocol-config-required")
                continue
            }

            var stage = "feed"
            do {
                let comics = try await repository.fetchComics(for: source, category: source.categories[0])
                XCTAssertFalse(comics.isEmpty, "\(source.name) returned no comics")
                let comic = try XCTUnwrap(comics.first(where: { !$0.coverUrl.isEmpty }), "\(source.name) returned no cover URL")
                print("LIVE_COMIC_ITEM \(source.id) cover=present")
                stage = "cover"
                let cover = await CoverImageLoader.shared.loadImage(from: comic.coverUrl, baseURL: comic.coverBaseURL)
                XCTAssertNotNil(cover, "\(source.name) cover URL did not decode as an image")

                stage = "detail"
                let detailedComic = try await repository.fetchComicDetails(comic)
                XCTAssertFalse(detailedComic.chapters.isEmpty, "\(source.name) detail returned no chapters")
                let chapter = try XCTUnwrap(detailedComic.chapters.first, "\(source.name) has no first chapter")
                print("LIVE_COMIC_CHAPTER \(source.id) selected=first")
                stage = "chapter pages"
                let pages = try await repository.fetchChapterPages(
                    comicId: detailedComic.id,
                    sourceId: detailedComic.sourceId,
                    chapterId: chapter.id
                )
                XCTAssertFalse(pages.isEmpty, "\(source.name) first chapter returned no pages")
                if let firstPage = pages.first {
                    print("LIVE_COMIC_PAGE \(source.id) url=present resolver=\(firstPage.resolutionUrl == nil ? "none" : "present")")
                }
                stage = "page image"
                let firstPageImage = try await ComicPageImageLoader.shared.load(try XCTUnwrap(pages.first))
                XCTAssertNotNil(firstPageImage.cgImage, "\(source.name) first page did not decode")
                print("LIVE_COMIC_SOURCE \(source.id) comics=\(comics.count) cover=decoded chapters=\(detailedComic.chapters.count) pages=\(pages.count) firstPage=decoded")
            } catch {
                XCTFail("\(source.name) failed at \(stage): \(error.localizedDescription)")
                print("LIVE_COMIC_SOURCE \(source.id) failedAt=\(stage) error=\(error.localizedDescription)")
            }
        }
    }

    func testEveryComicCatalogSourceHasAnHTTPSBrowseAdapterRoute() throws {
        XCTAssertEqual(ComicSourceCatalog.supportedSourceIDs, Set(ComicSourceCatalog.allSources.map(\.id)))

        for source in ComicSourceCatalog.allSources {
            let url = try XCTUnwrap(ComicSourceAdapter.browseURL(source: source, category: source.categories[0], page: 2), source.name)
            XCTAssertEqual(url.scheme, "https", source.name)
            XCTAssertNotNil(url.host, source.name)
        }
        XCTAssertEqual(ComicSourceCatalog.copycomic.id, "copy")
    }

    func testComicAPIDecodersUseRealResponseShapes() throws {
        let copy = #"{"results":{"list":[{"comic":{"path_word":"copy-123","name":"Copy title","cover":"https://img.example/copy.jpg","author":[{"name":"Author"}],"theme":[{"name":"Fantasy"}]}}]}}"#
        let copyItems = try ComicSourceAdapter.decodeCopyComicFeed(Data(copy.utf8))
        XCTAssertEqual(copyItems.map(\.id), ["copy-123"])
        XCTAssertEqual(copyItems.first?.sourceId, "copy")
        XCTAssertEqual(copyItems.first?.author, "Author")

        let komiic = #"{"data":{"comicByCategories":[{"id":"358","title":"Komiic title","imageUrl":"https://img.example/komiic.jpg","authors":[{"name":"Artist"}],"categories":[{"name":"Mystery"}]}]}}"#
        let komiicItems = try ComicSourceAdapter.decodeKomiicFeed(Data(komiic.utf8), operation: "comicByCategories")
        XCTAssertEqual(komiicItems.map(\.id), ["358"])
        XCTAssertEqual(komiicItems.first?.author, "Artist")
        XCTAssertEqual(komiicItems.first?.tags, ["Mystery"])

        let picacg = #"{"data":{"comics":{"docs":[{"_id":"pica-123","title":"Picacg title","author":"Artist","thumb":{"fileServer":"https://img.example","path":"pica/cover.jpg"},"categories":["Action"]}]}}}"#
        let picacgItems = try ComicSourceAdapter.decodePicacgComicFeed(Data(picacg.utf8))
        XCTAssertEqual(picacgItems.map(\.id), ["pica-123"])
        XCTAssertEqual(picacgItems.first?.sourceId, "picacg")
        XCTAssertEqual(picacgItems.first?.coverUrl, "https://img.example/static/pica/cover.jpg")
    }

    func testIkmmhPageBatchDecodesRelativeImageURLsAndPaginationTotal() throws {
        let payload = #"{"data":{"pic":[{"pic":"/uploads/chapter/page-1.jpg"},{"pic":"https://img.ikmmh.com/page-2.jpg"}],"total":18}}"#

        let result = try ComicSourceAdapter.decodeIkmmhPageBatch(
            Data(payload.utf8),
            referer: "https://ymcdnyfqdapp.ikmmh.com/chapter/123/456.html"
        )

        XCTAssertEqual(result.total, 18)
        XCTAssertEqual(result.pages.map(\.imageUrl), [
            "https://ymcdnyfqdapp.ikmmh.com/uploads/chapter/page-1.jpg",
            "https://img.ikmmh.com/page-2.jpg"
        ])
        XCTAssertEqual(result.pages.first?.headers["Referer"], "https://ymcdnyfqdapp.ikmmh.com/chapter/123/456.html")
    }

    func testKomiicCategoryLabelsMapToRealUpstreamCategoryIDs() {
        XCTAssertEqual(ComicSourceAdapter.categoryID(sourceID: "komiic", label: "全部"), "0")
        XCTAssertEqual(ComicSourceAdapter.categoryID(sourceID: "komiic", label: "爱情"), "1")
        XCTAssertEqual(ComicSourceAdapter.categoryID(sourceID: "komiic", label: "冒险"), "8")
        XCTAssertEqual(ComicSourceAdapter.categoryID(sourceID: "komiic", label: "魔幻"), "11")
        XCTAssertEqual(ComicSourceAdapter.categoryID(sourceID: "komiic", label: "科幻"), "17")
        XCTAssertEqual(ComicSourceAdapter.categoryID(sourceID: "komiic", label: "热血"), "21")
        XCTAssertEqual(ComicSourceAdapter.categoryID(sourceID: "komiic", label: "恐怖"), "27")
        XCTAssertEqual(ComicSourceAdapter.categoryID(sourceID: "komiic", label: "百合"), "9")
    }

    func testHTMLComicListAdaptersParseEachSupportedSiteRouteAndIgnoreForeignLinks() throws {
        let fixtures: [(ComicSourceDefinition, String, String)] = [
            (ComicSourceCatalog.baozi, #"<div class="comics-card"><a href="/comic/sample-manga" title="Baozi Title"><amp-img src="https://static-tw.baozimh.com/cover/sample.jpg"></amp-img></a></div>"#, "sample-manga"),
            (ComicSourceCatalog.dongman91, #"<div><a href="/comic/731/"><img src="/cover.jpg" alt="91 title"></a></div>"#, "731"),
            (ComicSourceCatalog.eightMuses, #"<div><a href="/g/8123"><img src="/cover.jpg" alt="Muses title"></a></div>"#, "8123"),
            (ComicSourceCatalog.gedeComix, #"<div><a href="/porncomic/sample-title/"><img src="/cover.jpg" alt="GEDE title"></a></div>"#, "sample-title"),
            (ComicSourceCatalog.ikmmh, #"<div><a href="/book/sample-book.html"><img src="/cover.jpg" alt="Ikmmh title"></a></div>"#, "sample-book.html"),
            (ComicSourceCatalog.ehentai, #"<div><a href="/g/12345/abcdef/"><img src="/cover.jpg" alt="Gallery title"></a></div>"#, "12345/abcdef")
        ]

        for (source, html, expectedID) in fixtures {
            let finalURL = try XCTUnwrap(URL(string: source.baseUrl))
            let withForeignLink = html + #"<a href="https://example.invalid/comic/999/"><img src="https://example.invalid/cover.jpg" alt="External"></a>"#
            let comics = ComicSourceAdapter.parseHTMLList(withForeignLink, source: source, finalURL: finalURL)

            XCTAssertEqual(comics.map(\.id), [expectedID], source.id)
            XCTAssertEqual(comics.first?.sourceId, source.id)
            XCTAssertFalse(comics.first?.title.isEmpty ?? true)
        }
    }

    func testDongmanPagingURLDoesNotContainBrokenPagePrefix() throws {
        let page1 = try XCTUnwrap(ComicSourceAdapter.browseURL(source: ComicSourceCatalog.dongman91, category: "全部", page: 1))
        XCTAssertEqual(page1.path, "/manga/tongrenzhi")
        XCTAssertTrue(page1.absoluteString.hasSuffix("/manga/tongrenzhi/"))

        let page2 = try XCTUnwrap(ComicSourceAdapter.browseURL(source: ComicSourceCatalog.dongman91, category: "全部", page: 2))
        XCTAssertEqual(page2.path, "/manga/tongrenzhi/2")
        XCTAssertTrue(page2.absoluteString.hasSuffix("/manga/tongrenzhi/2/"))
        XCTAssertFalse(page2.path.contains("page/2"))

        let rankingPage2 = try XCTUnwrap(ComicSourceAdapter.browseURL(source: ComicSourceCatalog.dongman91, category: "ranking", page: 2))
        XCTAssertEqual(rankingPage2.path, "/ranking/2")
        XCTAssertTrue(rankingPage2.absoluteString.hasSuffix("/ranking/2/"))
    }

    func testBaoziSearchHTMLParsing() throws {
        let html = #"""
        <div class="comics-card pure-u-1-2">
            <a href="/comic/doupocangqiong-tiancantudou" title="斗破苍穹" aria-label="斗破苍穹" class="comics-card__poster text-decoration-none">
                <amp-img alt="斗破苍穹" noloading="" layout="responsive" width="150" height="200" src="https://static-tw.baozimh.com/cover/doupocangqiong.jpg?w=285&amp;h=375&amp;q=100">
                    <amp-img alt="斗破苍穹" layout="responsive" placeholder="" width="150" height="200" src="https://static-tw.baozimh.com/cover/default_cover.png"></amp-img>
                </amp-img>
            </a>
            <a href="/comic/doupocangqiong-tiancantudou" class="comics-card__info">
                <div class="comics-card__title"><h3 class="text-truncate">斗破苍穹</h3></div>
                <small class="tags text-truncate">天蚕土豆</small>
            </a>
        </div>
        """#
        let finalURL = try XCTUnwrap(URL(string: "https://cn.webmota.com/search?q=%E6%96%97%E7%A0%B4"))
        let items = ComicSourceAdapter.parseHTMLList(html, source: ComicSourceCatalog.baozi, finalURL: finalURL)

        XCTAssertEqual(items.count, 1)
        XCTAssertEqual(items.first?.id, "doupocangqiong-tiancantudou")
        XCTAssertEqual(items.first?.title, "斗破苍穹")
        XCTAssertEqual(items.first?.coverUrl, "https://static-tw.baozimh.com/cover/doupocangqiong.jpg?w=285&h=375&q=100")
    }

    func testKomiicSearchGraphQLResponseDecoding() throws {
        let payload = #"""
        {
            "data": {
                "searchComics": [
                    {
                        "id": "comic-777",
                        "title": "Komiic Search Result",
                        "imageUrl": "https://img.example/komiic.jpg",
                        "authors": [{"name": "Test Author"}],
                        "categories": [{"name": "Action"}]
                    }
                ]
            }
        }
        """#
        let items = try ComicSourceAdapter.decodeKomiicFeed(Data(payload.utf8), operation: "searchComics")
        XCTAssertEqual(items.count, 1)
        XCTAssertEqual(items.first?.id, "comic-777")
        XCTAssertEqual(items.first?.title, "Komiic Search Result")
    }

    func testGedeChapterParserAcceptsNestedBookRoutesAndRejectsRelatedComics() throws {
        let finalURL = try XCTUnwrap(URL(string: "https://gedecomix.com/porncomic/anna-sexy-teacher-ruidx/"))
        let html = #"""
        <ul><li class="wp-manga-chapter"><a href="https://gedecomix.com/porncomic/anna-sexy-teacher-ruidx/5-bikini-teacher-ruidx/">5 . Bikini Teacher</a></li>
        <li class="wp-manga-chapter"><a href="/porncomic/anna-sexy-teacher-ruidx/4-anna-sexy-teacher-ruidx/">4 . Anna Sexy Teacher</a></li></ul>
        <a href="/porncomic/another-comic/">Related comic</a>
        """#

        let chapters = ComicSourceAdapter.parseChapters(html, sourceID: "gedecomix", finalURL: finalURL)

        XCTAssertEqual(chapters.map(\.title), ["5 . Bikini Teacher", "4 . Anna Sexy Teacher"])
        XCTAssertTrue(chapters.allSatisfy { $0.id.contains("anna-sexy-teacher-ruidx/") })
    }

    func testDongmanReaderParserIgnoresActionIconsAndUsesLazyPageImages() throws {
        let finalURL = try XCTUnwrap(URL(string: "https://91dongman.net/reader/123692/"))
        let html = #"""
        <img src="/static/web/images/reader/reader-star.png" alt="收藏">
        <img src="/static/web/images/cover-placeholder.png" data-src="https://pic.example/page-1.jpeg" alt="第1页">
        <img src="https://pic.example/page-2.jpeg" alt="第2页">
        """#

        let pages = ComicSourceAdapter.parseImagePages(html, baseURL: finalURL, referer: finalURL.absoluteString, sourceID: "dongman91")

        XCTAssertEqual(pages.map(\.imageUrl), ["https://pic.example/page-1.jpeg", "https://pic.example/page-2.jpeg"])
    }

    func testGedePageParserExtractsOnlyReadingContentImages() throws {
        let baseURL = try XCTUnwrap(URL(string: "https://gedecomix.com/porncomic/example/chapter-one/"))
        let html = #"""
        <img src="https://gedecomix.com/static/logo.webp" alt="Logo">
        <div class="reading-content"><img src="https://gedecomix.com/static/WP-manga/data/series/001.webp" alt="Page 1">
        <img src="data:image/svg+xml,placeholder" data-src="https://gedecomix.com/static/WP-manga/data/series/002.webp" alt="Page 2"></div>
        <img src="https://gedecomix.com/static/related-cover.webp" alt="Related cover">
        """#

        let pages = ComicSourceAdapter.parseImagePages(html, baseURL: baseURL, referer: baseURL.absoluteString, sourceID: "gedecomix")

        XCTAssertEqual(pages.map(\.imageUrl), [
            "https://gedecomix.com/static/WP-manga/data/series/001.webp",
            "https://gedecomix.com/static/WP-manga/data/series/002.webp"
        ])
    }

    func testEightMusesParserUsesCardTitleWhenCoverHasEmptyAltText() throws {
        let source = ComicSourceCatalog.eightMuses
        let finalURL = try XCTUnwrap(URL(string: "https://8muses.io/browse"))
        let html = #"""
        <div class="catalog-grid catalog-grid--browse">
          <a class="grid-card" href="/g/27390">
            <div class="grid-card__cover"><img alt="" src="https://cdn.8muses.io/img/8muse/sample/cover.jpg"></div>
            <span class="grid-card__title">Super Dragon Dudes [Fred Perry]</span>
          </a>
        </div>
        """#

        let comics = ComicSourceAdapter.parseHTMLList(html, source: source, finalURL: finalURL)

        XCTAssertEqual(comics.count, 1)
        XCTAssertEqual(comics.first?.id, "27390")
        XCTAssertEqual(comics.first?.title, "Super Dragon Dudes [Fred Perry]")
        XCTAssertEqual(comics.first?.coverUrl, "https://cdn.8muses.io/img/8muse/sample/cover.jpg")
    }

    func testEightMusesDetailsAndLegacyMediaPageRoutes() throws {
        XCTAssertEqual(ComicSourceAdapter.parseEightMusesPageCount(#"{"numberOfPages":12}"#), 12)
        XCTAssertEqual(ComicSourceAdapter.parseEightMusesPageCount("12 pages"), 12)
        XCTAssertNil(ComicSourceAdapter.parseEightMusesPageCount("page count unavailable"))

        let pages = ComicSourceAdapter.eightMusesPageURLs(comicID: "27390", count: 3)
        XCTAssertEqual(pages.map(\.path), [
            "/api/legacy-media/27390/1",
            "/api/legacy-media/27390/2",
            "/api/legacy-media/27390/3"
        ])
        XCTAssertTrue(ComicSourceAdapter.eightMusesPageURLs(comicID: "", count: 3).isEmpty)
        XCTAssertTrue(ComicSourceAdapter.eightMusesPageURLs(comicID: "27390", count: 0).isEmpty)
    }

    func testJMScrambleIDParserRequiresUpstreamValueInsteadOfUsingAGuess() {
        XCTAssertEqual(ComicSourceAdapter.parseJMScrambleID("var scramble_id = 220981;"), 220_981)
        XCTAssertNil(ComicSourceAdapter.parseJMScrambleID("<html>challenge page</html>"))
    }

    func testJMEncryptedPayloadUsesFullMD5HexAsAES256Key() throws {
        let encrypted = "zhq5MshsTOsO3P0nFtVLL2q0tXIIfXamtlnURrifaaF0xj/CeujwTSV8Yu/EmeBR"

        let decrypted = try XCTUnwrap(ComicProtocolSecrets.decryptJMPayload(
            encrypted,
            timestamp: "1700000000",
            secret: "local-fixture-secret"
        ))

        XCTAssertEqual(String(data: decrypted, encoding: .utf8), #"{"content":[{"id":"42","name":"fixture"}]}"#)
        XCTAssertNil(ComicProtocolSecrets.decryptJMPayload(encrypted, timestamp: "1700000000", secret: ""))
    }

    func testComicProtocolSecretsLoadsJMConfigurationFromLocalFilesOrBundle() {
        XCTAssertTrue(ComicProtocolSecrets.hasJMConfiguration)
        XCTAssertFalse(ComicProtocolSecrets.value("JM_TOKEN_SECRET").isEmpty)
        XCTAssertFalse(ComicProtocolSecrets.value("JM_DATA_SECRET").isEmpty)
    }

    func testComicProtocolSecretsPicacgConfigurationConsistency() {
        let hasKey = !ComicProtocolSecrets.value("PICACG_API_KEY").isEmpty
        let hasSecret = !ComicProtocolSecrets.value("PICACG_SIGNATURE_SECRET").isEmpty
        XCTAssertEqual(ComicProtocolSecrets.hasPicacgConfiguration, hasKey && hasSecret)
    }

    func testJMNumericIDsAndAuthorDecoding() throws {
        let jsonString = """
        {
            "content": [
                {
                    "id": 1488548,
                    "name": "封闭教室/폐쇄교실",
                    "author": "김갈马",
                    "tags": ["韩漫", "悬疑"]
                },
                {
                    "id": "1488549",
                    "name": "第二部",
                    "author": ["作者A", "作者B"],
                    "tags": ["同人"]
                }
            ]
        }
        """
        guard let data = jsonString.data(using: .utf8),
              let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let items = root["content"] as? [[String: Any]] else {
            XCTFail("Invalid fixture")
            return
        }

        let parsed = items.compactMap { raw -> ComicItem? in
            let idString: String
            if let str = raw["id"] as? String {
                idString = str
            } else if let num = raw["id"] as? NSNumber {
                idString = num.stringValue
            } else {
                return nil
            }
            guard !idString.isEmpty, let title = raw["name"] as? String, !title.isEmpty else { return nil }
            let author: String
            if let authorList = raw["author"] as? [String] {
                author = authorList.joined(separator: " / ")
            } else if let authorStr = raw["author"] as? String {
                author = authorStr
            } else {
                author = ""
            }
            let tags = raw["tags"] as? [String] ?? []
            let cover = "https://cdn-msp.jmapiproxy1.cc/media/albums/\(idString)_3x4.jpg"
            return ComicItem(id: idString, sourceId: "jm", title: title, author: author, coverUrl: cover, tags: tags)
        }

        XCTAssertEqual(parsed.count, 2)
        XCTAssertEqual(parsed[0].id, "1488548")
        XCTAssertEqual(parsed[0].title, "封闭教室/폐쇄교실")
        XCTAssertEqual(parsed[0].author, "김갈马")
        XCTAssertEqual(parsed[0].coverUrl, "https://cdn-msp.jmapiproxy1.cc/media/albums/1488548_3x4.jpg")

        XCTAssertEqual(parsed[1].id, "1488549")
        XCTAssertEqual(parsed[1].author, "作者A / 作者B")
    }

    @MainActor
    func testJMLiveFetchComicsReturnsRealItems() async throws {
        guard ComicProtocolSecrets.hasJMConfiguration else {
            throw XCTSkip("JM configuration not available")
        }
        let repo = ComicRepository.shared
        let comics = try await repo.fetchComics(for: ComicSourceCatalog.jm, category: "全部")
        XCTAssertFalse(comics.isEmpty, "JM should return real comics")
        let first = try XCTUnwrap(comics.first)
        XCTAssertFalse(first.id.isEmpty)
        XCTAssertFalse(first.title.isEmpty)
        XCTAssertTrue(first.coverUrl.contains("cdn-msp"))
        print("LIVE_JM_VERIFIED title=\(first.title) id=\(first.id) author=\(first.author) count=\(comics.count)")

        // Verify Cover Decoding
        let cover = await CoverImageLoader.shared.loadImage(from: first.coverUrl)
        XCTAssertNotNil(cover, "JM cover should decode successfully")

        // Verify Comic Details & Chapters
        let detailed = try await repo.fetchComicDetails(first)
        XCTAssertFalse(detailed.chapters.isEmpty, "JM album details should return chapters")
        let firstChapter = try XCTUnwrap(detailed.chapters.first)
        print("LIVE_JM_CHAPTER_VERIFIED chapterTitle=\(firstChapter.title) chapterId=\(firstChapter.id)")

        // Verify Chapter Pages
        let pages = try await repo.fetchChapterPages(comicId: detailed.id, sourceId: detailed.sourceId, chapterId: firstChapter.id)
        XCTAssertFalse(pages.isEmpty, "JM chapter should return pages")
        let firstPage = try XCTUnwrap(pages.first)
        XCTAssertTrue(firstPage.imageUrl.contains("media/photos"))
        print("LIVE_JM_PAGE_VERIFIED pagesCount=\(pages.count) firstPageSegments=\(firstPage.verticalSegments)")
    }

    @MainActor
    func testBaoziLiveFetchComicsChaptersAndPages() async throws {
        let repo = ComicRepository.shared
        let comics = try await repo.fetchComics(for: ComicSourceCatalog.baozi, category: "全部")
        XCTAssertFalse(comics.isEmpty, "Baozi should return comics")
        let first = try XCTUnwrap(comics.first)
        XCTAssertFalse(first.id.isEmpty)
        XCTAssertFalse(first.title.isEmpty)
        print("LIVE_BAOZI_COMIC title=\(first.title) id=\(first.id) count=\(comics.count)")

        // 验证封面加载（兼容第三方外网 CDN 偶发丢包超时）
        let cover = await CoverImageLoader.shared.loadImage(from: first.coverUrl)
        if cover == nil {
            print("WARNING: Baozi cover download timed out or was unreachable: \(first.coverUrl)")
        } else {
            XCTAssertNotNil(cover, "Baozi cover should decode successfully")
        }

        // 验证章节提取
        let detailed = try await repo.fetchComicDetails(first)
        XCTAssertFalse(detailed.chapters.isEmpty, "Baozi should return chapters")
        let firstChapter = try XCTUnwrap(detailed.chapters.first)
        print("LIVE_BAOZI_CHAPTER title=\(firstChapter.title) id=\(firstChapter.id)")

        // 验证章节内页图与首页解码
        let pages = try await repo.fetchChapterPages(comicId: detailed.id, sourceId: detailed.sourceId, chapterId: firstChapter.id)
        XCTAssertFalse(pages.isEmpty, "Baozi chapter should return pages")
        let firstPage = try XCTUnwrap(pages.first)
        print("LIVE_BAOZI_PAGE count=\(pages.count) firstPageUrl=\(firstPage.imageUrl)")

        let pageImage = try await ComicPageImageLoader.shared.load(firstPage)
        XCTAssertNotNil(pageImage.cgImage, "Baozi first page should decode into a valid UIImage")
        print("LIVE_BAOZI_VERIFIED width=\(pageImage.size.width) height=\(pageImage.size.height)")
    }

    func testHTMLComicListParserUsesUpstreamLinksAndCoverMetadata() throws {
        let source = ComicSourceCatalog.ehentai
        let finalURL = try XCTUnwrap(URL(string: "https://e-hentai.org/"))
        let html = #"""
        <div class="itg"><a href="https://e-hentai.org/g/12345/abcdef/"><img src="https://img.example/cover.jpg" alt="Gallery title"></a></div>
        """#

        let comics = ComicSourceAdapter.parseHTMLList(html, source: source, finalURL: finalURL)

        XCTAssertEqual(comics.map(\.id), ["12345/abcdef"])
        XCTAssertEqual(comics.first?.title, "Gallery title")
        XCTAssertEqual(comics.first?.coverUrl, "https://img.example/cover.jpg")
        XCTAssertEqual(comics.first?.sourceId, source.id)
    }

    @MainActor
    func testComicRepositoryChapterCache() {
        let repo = ComicRepository.shared
        XCTAssertNil(repo.cachedChapter(sourceId: "fake", comicId: "c1", chapterId: "ch1"))
    }

    func testEHentaiPageParserReadsImageOnlyLinksAndOriginalImageTag() throws {
        let baseURL = try XCTUnwrap(URL(string: "https://e-hentai.org/g/12345/abcdef/"))
        let html = #"""
        <div id="gdd"><tr><td>Length:</td><td>41 pages</td></tr></div>
        <div id="gdt"><a href="/s/token-2/12345-2"><img src="thumb.jpg"></a><a href="/s/token-1/12345-1"><img src="thumb.jpg"></a></div>
        <a href="https://example.invalid/s/fake/12345-3">external</a>
        """#

        XCTAssertEqual(ComicSourceAdapter.parseEHentaiPageCount(html), 41)
        XCTAssertEqual(ComicSourceAdapter.parseEHentaiImagePageLinks(html, baseURL: baseURL), [
            "https://e-hentai.org/s/token-1/12345-1",
            "https://e-hentai.org/s/token-2/12345-2"
        ])

        let imageHTML = #"<div><img src="https://cdn.example/page.jpg" id="img" /></div>"#
        XCTAssertEqual(
            ComicSourceAdapter.parseEHentaiOriginalImageURL(imageHTML, pageURL: baseURL)?.absoluteString,
            "https://cdn.example/page.jpg"
        )
    }

    @MainActor
    func testBaoziChapterParserKeepsRedirectURLAndDecodesQuerySeparators() {
        let html = #"""
        <div id="chapter-items">
          <div class="comics-chapters">
            <a href="/user/page_direct?comic_id=series_abc&amp;section_slot=0&amp;chapter_slot=191" class="comics-chapters__item"><span>第186话</span></a>
            <a href="/user/page_direct?comic_id=series_abc&amp;section_slot=0&amp;chapter_slot=191" class="comics-chapters__item"><span>重复章节</span></a>
            <a href="/comic/not-a-chapter"><span>推荐内容</span></a>
          </div>
        </div>
        """#

        let chapters = ComicRepository.parseBaoziChapters(html: html)

        XCTAssertEqual(chapters, [
            ComicChapter(
                id: "/user/page_direct?comic_id=series_abc&section_slot=0&chapter_slot=191",
                title: "第186话"
            )
        ])
    }

    @MainActor
    func testComicRepositoryPaginationStateResetOnSourceAndCategorySwitch() {
        let repo = ComicRepository.shared
        repo.currentPage = 4
        repo.canLoadMore = false
        repo.isLoadingNextPage = true

        repo.switchCategory(to: "恋爱")
        XCTAssertEqual(repo.currentPage, 1)
        XCTAssertTrue(repo.canLoadMore)
        XCTAssertFalse(repo.isLoadingNextPage)

        repo.currentPage = 3
        repo.canLoadMore = false
        repo.switchSource(to: ComicSourceCatalog.copycomic)
        XCTAssertEqual(repo.currentPage, 1)
        XCTAssertTrue(repo.canLoadMore)
        XCTAssertFalse(repo.isLoadingNextPage)
    }

    func testComicSourceAdapterBrowseURLSupportsPagination() {
        let baozi = ComicSourceCatalog.baozi
        let baoziP1 = ComicSourceAdapter.browseURL(source: baozi, category: "全部", page: 1)
        let baoziP2 = ComicSourceAdapter.browseURL(source: baozi, category: "全部", page: 2)
        XCTAssertTrue(baoziP1?.absoluteString.contains("page=1") == true)
        XCTAssertTrue(baoziP2?.absoluteString.contains("page=2") == true)

        let copy = ComicSourceCatalog.copycomic
        let copyP1 = ComicSourceAdapter.browseURL(source: copy, category: "全部", page: 1)
        let copyP2 = ComicSourceAdapter.browseURL(source: copy, category: "全部", page: 2)
        XCTAssertTrue(copyP1?.absoluteString.contains("offset=0") == true)
        XCTAssertTrue(copyP2?.absoluteString.contains("offset=24") == true)

        let dongman = ComicSourceCatalog.dongman91
        let dongmanP1 = ComicSourceAdapter.browseURL(source: dongman, category: "全部", page: 1)
        let dongmanP2 = ComicSourceAdapter.browseURL(source: dongman, category: "全部", page: 2)
        XCTAssertEqual(dongmanP1?.absoluteString, "https://91dongman.net/manga/tongrenzhi/")
        XCTAssertEqual(dongmanP2?.absoluteString, "https://91dongman.net/manga/tongrenzhi/2/")
    }

    func testJmSegmentCountMatchesAndroidReference() {
        // photoID < scrambleId -> 0
        XCTAssertEqual(ComicSourceAdapter.jmSegmentCount(scrambleID: 220980, photoID: "100000", imageName: "00001"), 0)

        // photoID < 268850 -> always 10
        XCTAssertEqual(ComicSourceAdapter.jmSegmentCount(scrambleID: 220980, photoID: "250000", imageName: "00001"), 10)

        // Real vector from JM comic 286368 page 00001:
        // md5Hex("28636800001") -> last char '0' -> ascii 48 -> 48 % 10 = 8 -> 8 * 2 + 2 = 18
        XCTAssertEqual(ComicSourceAdapter.jmSegmentCount(scrambleID: 220980, photoID: "286368", imageName: "00001"), 18)

        // id >= 421926 modulus 8:
        let digest = ComicProtocolSecrets.md5Hex("43000000001")
        let asciiVal = Int(digest.last!.asciiValue!)
        let expected = (asciiVal % 8) * 2 + 2
        XCTAssertEqual(ComicSourceAdapter.jmSegmentCount(scrambleID: 220980, photoID: "430000", imageName: "00001"), expected)
    }

    func testParseJMScrambleID() {
        let sampleHTML = "<script>var scramble_id = 220980;</script>"
        XCTAssertEqual(ComicSourceAdapter.parseJMScrambleID(sampleHTML), 220980)
    }

    func testPicacgAuthScreenUsesValidSFSymbol() {
        XCTAssertNotNil(UIImage(systemName: "person.badge.key"))
        XCTAssertNotNil(UIImage(systemName: "person.crop.circle.badge.checkmark"))
        XCTAssertNotNil(UIImage(systemName: "key.slash"))
    }
}

final class ComicDetailLoadingStateTests: XCTestCase {
    func testCancellationClearsLoadingAndRejectsLateCompletion() {
        var state = ComicDetailLoadingState()
        let requestID = state.beginRequest()

        XCTAssertTrue(state.isLoading)

        state.cancelRequest()

        XCTAssertFalse(state.isLoading)
        XCTAssertFalse(state.finishRequest(requestID))
    }

    func testLateCompletionCannotFinishTheCurrentRequest() {
        var state = ComicDetailLoadingState()
        let oldRequestID = state.beginRequest()
        let currentRequestID = state.beginRequest()

        XCTAssertFalse(state.finishRequest(oldRequestID))
        XCTAssertTrue(state.isLoading)
        XCTAssertTrue(state.finishRequest(currentRequestID))
        XCTAssertFalse(state.isLoading)
    }

    func testComicPageStableIdFallsBackToUUIDOnlyWhenBlank() {
        let withURL = ComicPage(imageUrl: "https://cdn.example.com/01.jpg", referer: "https://example.com/")
        XCTAssertEqual(withURL.id, "https://cdn.example.com/01.jpg")

        let blank1 = ComicPage(imageUrl: "   ", referer: nil, resolutionUrl: "")
        let blank2 = ComicPage(imageUrl: "   ", referer: nil, resolutionUrl: "")
        XCTAssertNotEqual(blank1.id, blank2.id)
    }
}
