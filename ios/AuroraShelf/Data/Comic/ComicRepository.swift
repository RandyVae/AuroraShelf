import Foundation

public enum ComicFetchError: LocalizedError {
    case invalidURL
    case invalidResponse
    case httpStatus(Int)
    case malformedResponse
    case networkError(String)
    case notSupported(String)
    case authenticationRequired(String)

    public var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "漫画源地址无效。"
        case .invalidResponse:
            return "漫画源没有返回有效的 HTTP 响应。"
        case .httpStatus(let status):
            return "漫画源请求失败（HTTP \(status)）。"
        case .malformedResponse:
            return "漫画源返回的数据格式无法识别。"
        case .networkError(let msg), .notSupported(let msg), .authenticationRequired(let msg):
            return msg
        }
    }
}

private struct ComicHTTPResponse {
    let data: Data
    let statusCode: Int
    let finalURL: URL

    var body: String { String(data: data, encoding: .utf8) ?? "" }
}

@MainActor
public final class ComicRepository: ObservableObject {
    public static let shared = ComicRepository()

    @Published public var currentSource: ComicSourceDefinition = ComicSourceCatalog.jm
    @Published public var comics: [ComicItem] = []
    @Published public var searchResults: [ComicItem] = []
    @Published public var selectedCategory: String = "全部"
    @Published public var isLoading: Bool = false
    @Published public var isSearching: Bool = false
    @Published public var errorMessage: String?
    @Published public var currentPage: Int = 1
    @Published public var canLoadMore: Bool = true
    @Published public var isLoadingNextPage: Bool = false

    private let session: URLSession
    private var loadTask: Task<Void, Never>?
    private var activeRequestID = UUID()
    private var chapterCache: [String: [ComicPage]] = [:]
    private var inFlightChapterTasks: [String: (id: UUID, task: Task<[ComicPage], Error>)] = [:]

    public func cachedChapter(sourceId: String, comicId: String, chapterId: String) -> [ComicPage]? {
        let key = "\(sourceId):\(comicId):\(chapterId)"
        return chapterCache[key]
    }

    public func prewarmChapter(sourceId: String, comicId: String, chapterId: String) {
        let key = "\(sourceId):\(comicId):\(chapterId)"
        guard chapterCache[key] == nil, inFlightChapterTasks[key] == nil else { return }

        Task { [weak self] in
            guard let self else { return }
            let pages = (try? await self.fetchChapterPages(comicId: comicId, sourceId: sourceId, chapterId: chapterId)) ?? []
            // 管道预热前 3 张图片
            for page in pages.prefix(3) {
                _ = try? await ComicPageImageLoader.shared.load(page)
            }
        }
    }

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.timeoutIntervalForResource = 25
        config.httpAdditionalHeaders = [
            "User-Agent": ComicSourceAdapter.userAgent
        ]
        self.session = URLSession.permissive(configuration: config)
        self.loadComics(for: ComicSourceCatalog.jm)
    }

    public func switchSource(to source: ComicSourceDefinition) {
        guard currentSource.id != source.id else { return }
        self.currentSource = source
        self.selectedCategory = source.categories.first ?? "全部"
        self.comics = []
        self.currentPage = 1
        self.canLoadMore = true
        self.isLoadingNextPage = false
        self.loadComics(for: source)
    }

    public func switchCategory(to category: String) {
        guard selectedCategory != category else { return }
        self.selectedCategory = category
        self.comics = []
        self.currentPage = 1
        self.canLoadMore = true
        self.isLoadingNextPage = false
        self.loadComics(for: currentSource)
    }

    public func loadComics(for source: ComicSourceDefinition) {
        loadTask?.cancel()
        let requestID = UUID()
        activeRequestID = requestID
        self.isLoading = true
        self.errorMessage = nil
        self.currentPage = 1
        self.canLoadMore = true
        self.isLoadingNextPage = false
        loadTask = Task { [weak self] in
            guard let self else { return }
            do {
                let loaded = try await self.fetchRealComics(source: source, category: self.selectedCategory, page: 1)
                guard !Task.isCancelled, self.activeRequestID == requestID else { return }
                var seen = Set<String>()
                var uniqueList: [ComicItem] = []
                for item in loaded {
                    if !seen.contains(item.id) {
                        seen.insert(item.id)
                        uniqueList.append(item)
                    }
                }
                self.comics = uniqueList
                self.canLoadMore = !uniqueList.isEmpty
                self.currentPage = 1
                self.errorMessage = nil
            } catch {
                guard !Task.isCancelled, self.activeRequestID == requestID else { return }
                self.errorMessage = error.localizedDescription
            }
            self.isLoading = false
            self.loadTask = nil
        }
    }

    public func loadNextPage() async {
        guard canLoadMore, !isLoading, !isLoadingNextPage else { return }
        self.isLoadingNextPage = true
        defer { self.isLoadingNextPage = false }

        let targetSource = currentSource
        let targetCategory = selectedCategory
        let nextPage = currentPage + 1

        do {
            let loaded = try await fetchRealComics(source: targetSource, category: targetCategory, page: nextPage)
            guard !Task.isCancelled, currentSource.id == targetSource.id, selectedCategory == targetCategory else { return }

            let existingIDs = Set(comics.map(\.id))
            let uniqueNewComics = loaded.filter { !existingIDs.contains($0.id) }

            if uniqueNewComics.isEmpty {
                self.canLoadMore = false
            } else {
                self.comics.append(contentsOf: uniqueNewComics)
                self.currentPage = nextPage
                self.canLoadMore = !loaded.isEmpty
            }
        } catch {
            guard !Task.isCancelled, currentSource.id == targetSource.id, selectedCategory == targetCategory else { return }
            // 临时网络错误保留 canLoadMore 允许后续重试加载，不永久断开分页
            self.errorMessage = error.localizedDescription
        }
    }

    public var isPicacgAuthenticated: Bool { !PicacgAuthStore.shared.token.isEmpty }

    public func loginPicacg(account: String, password: String) async throws {
        guard ComicProtocolSecrets.hasPicacgConfiguration else {
            throw ComicFetchError.authenticationRequired("此构建未配置哔咔协议参数。")
        }
        guard let url = URL(string: "https://picaapi.picacomic.com/auth/sign-in") else { throw ComicFetchError.invalidURL }
        let body = try JSONSerialization.data(withJSONObject: ["email": account, "password": password])
        let response = try await request(url, method: "POST", body: body, headers: picacgHeaders(method: "POST", path: "auth/sign-in", token: ""))
        guard let root = try? JSONSerialization.jsonObject(with: response.data) as? [String: Any],
              let data = root["data"] as? [String: Any], let token = data["token"] as? String, !token.isEmpty else {
            throw ComicFetchError.authenticationRequired("哔咔授权失败。")
        }
        PicacgAuthStore.shared.token = token
    }

    public func signOutPicacg() {
        PicacgAuthStore.shared.token = ""
        if currentSource.id == ComicSourceCatalog.picacg.id { loadComics(for: currentSource) }
    }

    // MARK: - 真实包子漫画与多源网络抓取

    func fetchComics(for source: ComicSourceDefinition, category: String = "全部", page: Int = 1) async throws -> [ComicItem] {
        try await fetchRealComics(source: source, category: category, page: page)
    }

    public func searchComics(query: String, source: ComicSourceDefinition? = nil) async throws -> [ComicItem] {
        let activeSource = source ?? currentSource
        guard let url = ComicSourceAdapter.searchURL(source: activeSource, query: query, page: 1) else {
            throw ComicFetchError.notSupported("\(activeSource.name) 暂不支持搜索。")
        }
        switch activeSource.id {
        case "copy":
            let response = try await request(url, headers: ComicSourceAdapter.requestHeaders(sourceID: "copy", referer: "https://www.copymanga.site/"))
            return try ComicSourceAdapter.decodeCopyComicFeed(response.data)
        case "komiic":
            let queryGQL = "query searchComics($keyword: String!, $pagination: Pagination!) { searchComics(keyword: $keyword, pagination: $pagination) { id title status year imageUrl authors { id name } categories { id name } dateUpdated } }"
            let variables: [String: Any] = [
                "keyword": query,
                "pagination": ["limit": 24, "offset": 0, "orderBy": "DATE_UPDATED", "status": "", "asc": true]
            ]
            let result = try await komiicRequest(operation: "searchComics", variables: variables, query: queryGQL)
            let data = try JSONSerialization.data(withJSONObject: result)
            return try ComicSourceAdapter.decodeKomiicFeed(data, operation: "searchComics")
        case "jm":
            guard ComicProtocolSecrets.hasJMConfiguration else {
                throw ComicFetchError.authenticationRequired("此构建未配置禁漫协议参数。")
            }
            let encodedQ = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query
            let json = try await jmRequest(path: "search?search_query=\(encodedQ)&o=mr&page=1", secretKey: "JM_TOKEN_SECRET")
            let items = json["content"] as? [[String: Any]] ?? []
            return items.compactMap { raw in
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
        case "picacg":
            guard ComicProtocolSecrets.hasPicacgConfiguration else {
                throw ComicFetchError.authenticationRequired("此构建未配置哔咔协议参数。")
            }
            let token = PicacgAuthStore.shared.token
            guard !token.isEmpty else { throw ComicFetchError.authenticationRequired("哔咔需要先登录。") }
            let postBody = try JSONSerialization.data(withJSONObject: ["keyword": query, "sort": "dd"])
            guard let searchURL = URL(string: "https://picaapi.picacomic.com/comics/advanced-search?page=1") else { throw ComicFetchError.invalidURL }
            let response = try await request(searchURL, method: "POST", body: postBody, headers: picacgHeaders(method: "POST", path: "comics/advanced-search?page=1", token: token))
            return try ComicSourceAdapter.decodePicacgComicFeed(response.data)
        default:
            let response = try await request(url, headers: ComicSourceAdapter.requestHeaders(sourceID: activeSource.id, referer: activeSource.baseUrl))
            return ComicSourceAdapter.parseHTMLList(response.body, source: activeSource, finalURL: response.finalURL)
        }
    }

    private func fetchRealComics(source: ComicSourceDefinition, category: String, page: Int = 1) async throws -> [ComicItem] {
        let p = max(page, 1)
        switch source.id {
        case "baozi":
            return try await fetchBaoziList(category: category, page: p)
        case "copy":
            return try await fetchCopyList(category: category, page: p)
        case "komiic":
            return try await fetchKomiicList(category: category, page: p)
        case "jm":
            return try await fetchJMList(category: category, page: p)
        case "picacg":
            return try await fetchPicacgList(category: category, page: p)
        default:
            guard let url = ComicSourceAdapter.browseURL(source: source, category: category, page: p) else {
                throw ComicFetchError.notSupported("\(source.name) 的浏览路由暂不可用。")
            }
            let response = try await request(url, headers: ComicSourceAdapter.requestHeaders(sourceID: source.id, referer: source.baseUrl))
            return ComicSourceAdapter.parseHTMLList(response.body, source: source, finalURL: response.finalURL)
        }
    }

    private func fetchBaoziList(category: String, page: Int = 1) async throws -> [ComicItem] {
        guard let url = ComicSourceAdapter.browseURL(source: ComicSourceCatalog.baozi, category: category, page: page) else {
            throw ComicFetchError.invalidURL
        }
        let response = try await request(url, headers: ComicSourceAdapter.requestHeaders(sourceID: "baozi", referer: "https://cn.webmota.com/"))
        return try Self.decodeBaoziFeed(data: response.data, statusCode: response.statusCode)
    }

    private func fetchCopyList(category: String, page: Int = 1) async throws -> [ComicItem] {
        guard let url = ComicSourceAdapter.browseURL(source: ComicSourceCatalog.copycomic, category: category, page: page) else {
            throw ComicFetchError.invalidURL
        }
        let response = try await request(url, headers: ComicSourceAdapter.requestHeaders(sourceID: "copy", referer: "https://www.copymanga.site/"))
        return try ComicSourceAdapter.decodeCopyComicFeed(response.data)
    }

    private func fetchKomiicList(category: String, page: Int = 1) async throws -> [ComicItem] {
        let categoryID = ComicSourceAdapter.categoryID(sourceID: "komiic", label: category)
        let operation = categoryID == "0" ? "recentUpdate" : "comicByCategories"
        let fields = "id title status year imageUrl authors { id name } categories { id name } dateUpdated"
        let query = categoryID == "0"
            ? "query recentUpdate($pagination: Pagination!) { recentUpdate(pagination: $pagination) { \(fields) } }"
            : "query comicByCategories($categoryId: [ID!]!, $pagination: Pagination!) { comicByCategories(categoryId: $categoryId, pagination: $pagination) { \(fields) } }"
        let offset = max(page - 1, 0) * 24
        var variables: [String: Any] = ["pagination": ["limit": 24, "offset": offset, "orderBy": "DATE_UPDATED", "status": "", "asc": true]]
        if categoryID != "0" { variables["categoryId"] = [categoryID] }
        let result = try await komiicRequest(operation: operation, variables: variables, query: query)
        let data = try JSONSerialization.data(withJSONObject: result)
        return try ComicSourceAdapter.decodeKomiicFeed(data, operation: operation)
    }

    private func fetchJMList(category: String, page: Int = 1) async throws -> [ComicItem] {
        guard ComicProtocolSecrets.hasJMConfiguration else {
            throw ComicFetchError.authenticationRequired("此构建未配置禁漫协议参数。")
        }
        let id = ComicSourceAdapter.categoryID(sourceID: "jm", label: category)
        let p = max(page, 1)
        let json = try await jmRequest(path: "categories/filter?o=mr&c=\(id)&page=\(p)", secretKey: "JM_TOKEN_SECRET")
        let items = json["content"] as? [[String: Any]] ?? []
        return items.compactMap { raw in
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
    }

    private func fetchPicacgList(category: String, page: Int = 1) async throws -> [ComicItem] {
        guard ComicProtocolSecrets.hasPicacgConfiguration else {
            throw ComicFetchError.authenticationRequired("此构建未配置哔咔协议参数。")
        }
        let token = PicacgAuthStore.shared.token
        guard !token.isEmpty else { throw ComicFetchError.authenticationRequired("哔咔需要先登录。") }
        let sort = ["官方推荐": "dd", "生肉": "ld", "Cosplay": "vd", "重口": "vd", "纯爱": "dd"][category] ?? "dd"
        let p = max(page, 1)
        let path = "comics?page=\(p)&s=\(sort)"
        guard let url = URL(string: "https://picaapi.picacomic.com/\(path)") else { throw ComicFetchError.invalidURL }
        let response = try await request(url, headers: picacgHeaders(method: "GET", path: path, token: token))
        return try ComicSourceAdapter.decodePicacgComicFeed(response.data)
    }

    private func fetchCopyDetails(_ comic: ComicItem) async throws -> ComicItem {
        let api = "https://api.2024manga.com/api/v3"
        guard let detailURL = URL(string: "\(api)/comic2/\(comic.id)?platform=3") else { throw ComicFetchError.invalidURL }
        let detail = try await request(detailURL, headers: ComicSourceAdapter.requestHeaders(sourceID: "copy", referer: "https://www.copymanga.site/"))
        guard let root = try JSONSerialization.jsonObject(with: detail.data) as? [String: Any],
              let results = root["results"] as? [String: Any], let item = results["comic"] as? [String: Any] else {
            throw ComicFetchError.malformedResponse
        }
        guard let chapterURL = URL(string: "\(api)/comic/\(comic.id)/group/default/chapters?limit=500&offset=0&platform=3") else { throw ComicFetchError.invalidURL }
        let chapterResult = try await request(chapterURL, headers: ComicSourceAdapter.requestHeaders(sourceID: "copy", referer: "https://www.copymanga.site/"))
        let chapterRoot = try JSONSerialization.jsonObject(with: chapterResult.data) as? [String: Any]
        let chapterList = ((chapterRoot?["results"] as? [String: Any])?["list"] as? [[String: Any]]) ?? []
        let version = item["reclass"] == nil || item["reclass"] is NSNull ? 2 : 1
        let chapters = chapterList.enumerated().compactMap { index, row -> ComicChapter? in
            guard let uuid = row["uuid"] as? String else { return nil }
            return ComicChapter(id: "\(version):\(uuid)", title: row["name"] as? String ?? "第 \(index + 1) 话")
        }
        let author = ((item["author"] as? [[String: Any]])?.first?["name"] as? String) ?? comic.author
        let tags = (item["theme"] as? [[String: Any]] ?? []).compactMap { $0["name"] as? String }
        let updated = ComicItem(id: comic.id, sourceId: comic.sourceId, title: item["name"] as? String ?? comic.title, author: author, coverUrl: item["cover"] as? String ?? comic.coverUrl, category: comic.category, tags: tags, chapters: chapters, latestChapterTitle: chapters.first?.title ?? "")
        return updated
    }

    private func fetchKomiicDetails(_ comic: ComicItem) async throws -> ComicItem {
        let response = try await komiicRequest(
            operation: "chapterByComicId",
            variables: ["comicId": comic.id],
            query: "query chapterByComicId($comicId: ID!) { chaptersByComicId(comicId: $comicId) { id serial type } }"
        )
        let items = ((response["data"] as? [String: Any])?["chaptersByComicId"] as? [[String: Any]]) ?? []
        let chapters = items.compactMap { item -> ComicChapter? in
            guard let id = item["id"] as? String else { return nil }
            let prefix = item["type"] as? String == "book" ? "卷" : "第"
            return ComicChapter(id: id, title: "\(prefix)\(item["serial"] ?? "")")
        }
        return comicWithChapters(comic, chapters: chapters)
    }

    private func fetchJMDetails(_ comic: ComicItem) async throws -> ComicItem {
        let item = try await jmRequest(path: "album?id=\(comic.id)", secretKey: "JM_TOKEN_SECRET")
        let series = item["series"] as? [[String: Any]] ?? []
        let chapters = series.enumerated().compactMap { index, row -> ComicChapter? in
            let idString: String
            if let str = row["id"] as? String {
                idString = str
            } else if let num = row["id"] as? NSNumber {
                idString = num.stringValue
            } else {
                idString = "\(comic.id)-\(index + 1)"
            }
            let sortString: String
            if let sortStr = row["sort"] as? String {
                sortString = sortStr
            } else if let sortNum = row["sort"] as? NSNumber {
                sortString = sortNum.stringValue
            } else {
                sortString = String(index + 1)
            }
            let title = row["name"] as? String ?? "第 \(sortString) 话"
            return ComicChapter(id: idString, title: title)
        }
        let author: String
        if let authorList = item["author"] as? [String] {
            author = authorList.joined(separator: " / ")
        } else if let authorStr = item["author"] as? String {
            author = authorStr
        } else {
            author = comic.author
        }
        let updated = ComicItem(id: comic.id, sourceId: comic.sourceId, title: item["name"] as? String ?? comic.title, author: author.isEmpty ? comic.author : author, coverUrl: "https://cdn-msp.jmapiproxy1.cc/media/albums/\(comic.id)_3x4.jpg", category: comic.category, tags: item["tags"] as? [String] ?? comic.tags, chapters: chapters.isEmpty ? [ComicChapter(id: comic.id, title: "完整内容")] : chapters, latestChapterTitle: chapters.first?.title ?? "完整内容")
        return updated
    }

    private func fetchPicacgDetails(_ comic: ComicItem) async throws -> ComicItem {
        guard !PicacgAuthStore.shared.token.isEmpty else { throw ComicFetchError.authenticationRequired("哔咔需要先登录。") }
        let token = PicacgAuthStore.shared.token
        let detailPath = "comics/\(comic.id)"
        guard let detailURL = URL(string: "https://picaapi.picacomic.com/\(detailPath)") else { throw ComicFetchError.invalidURL }
        let detail = try await request(detailURL, headers: picacgHeaders(method: "GET", path: detailPath, token: token))
        let detailRoot = try JSONSerialization.jsonObject(with: detail.data) as? [String: Any]
        let item = ((detailRoot?["data"] as? [String: Any])?["comic"] as? [String: Any]) ?? [:]
        var chapters: [ComicChapter] = []
        var page = 1
        var pageCount = 1
        while page <= pageCount && page <= 50 {
            let epsPath = "comics/\(comic.id)/eps?page=\(page)"
            guard let epsURL = URL(string: "https://picaapi.picacomic.com/\(epsPath)") else { throw ComicFetchError.invalidURL }
            let eps = try await request(epsURL, headers: picacgHeaders(method: "GET", path: epsPath, token: token))
            let epsRoot = try JSONSerialization.jsonObject(with: eps.data) as? [String: Any]
            let payload = ((epsRoot?["data"] as? [String: Any])?["eps"] as? [String: Any]) ?? [:]
            pageCount = min(max(payload["pages"] as? Int ?? 1, 1), 50)
            let docs = payload["docs"] as? [[String: Any]] ?? []
            if docs.isEmpty { break }
            chapters.append(contentsOf: docs.map { row in
                ComicChapter(id: String(row["order"] as? Int ?? chapters.count + 1), title: row["title"] as? String ?? "章节")
            })
            page += 1
        }
        return ComicItem(id: comic.id, sourceId: comic.sourceId, title: item["title"] as? String ?? comic.title, author: item["author"] as? String ?? comic.author, coverUrl: comic.coverUrl, category: comic.category, tags: comic.tags, chapters: chapters, latestChapterTitle: chapters.first?.title ?? "")
    }

    private func fetchCopyPages(comicId: String, chapterId: String) async throws -> [ComicPage] {
        let parts = chapterId.split(separator: ":", maxSplits: 1).map(String.init)
        let version = parts.first.flatMap(Int.init) ?? 2
        let uuid = parts.count > 1 ? parts[1] : chapterId
        let suffix = version >= 2 ? String(version) : ""
        let path = "comic/\(comicId)/chapter\(suffix)/\(uuid)?platform=3"
        let referer = "https://www.copymanga.site/comic/\(comicId)/chapter/\(uuid)"
        guard let url = URL(string: "https://api.2024manga.com/api/v3/\(path)") else { throw ComicFetchError.invalidURL }
        let response = try await request(url, headers: ComicSourceAdapter.requestHeaders(sourceID: "copy", referer: referer))
        let root = try JSONSerialization.jsonObject(with: response.data) as? [String: Any]
        let chapter = (((root?["results"] as? [String: Any])?["chapter"] as? [String: Any]) ?? [:])
        let contents = chapter["contents"] as? [[String: Any]] ?? []
        let order = chapter["words"] as? [Int] ?? []
        return contents.enumerated()
            .sorted { (order.indices.contains($0.offset) ? order[$0.offset] : $0.offset) < (order.indices.contains($1.offset) ? order[$1.offset] : $1.offset) }
            .compactMap { $0.element["url"] as? String }
            .map { ComicPage(imageUrl: $0, referer: referer, headers: ComicSourceAdapter.requestHeaders(sourceID: "copy", referer: referer)) }
    }

    private func fetchKomiicPages(comicId: String, chapterId: String) async throws -> [ComicPage] {
        let response = try await komiicRequest(
            operation: "imagesByChapterId",
            variables: ["chapterId": chapterId],
            query: "query imagesByChapterId($chapterId: ID!) { imagesByChapterId(chapterId: $chapterId) { kid } }"
        )
        let items = ((response["data"] as? [String: Any])?["imagesByChapterId"] as? [[String: Any]]) ?? []
        let referer = "https://komiic.com/comic/\(comicId)/chapter/\(chapterId)/images/all"
        return items.compactMap { $0["kid"] as? String }.map {
            ComicPage(imageUrl: "https://komiic.com/api/image/\($0)", referer: referer, headers: ComicSourceAdapter.requestHeaders(sourceID: "komiic", referer: referer))
        }
    }

    private func fetchJMPages(chapterId: String) async throws -> [ComicPage] {
        let item = try await jmRequest(path: "chapter?id=\(chapterId)", secretKey: "JM_TOKEN_SECRET")
        let photoID: String
        if let str = item["id"] as? String {
            photoID = str
        } else if let num = item["id"] as? NSNumber {
            photoID = num.stringValue
        } else {
            photoID = chapterId
        }
        let files = item["images"] as? [String] ?? []
        let scrambleID = await requestJMScrambleID(photoID: photoID)
        let hosts = ["cdn-msp.jmapiproxy1.cc", "cdn-msp.jmapiproxy2.cc", "cdn-msp2.jmapiproxy2.cc", "cdn-msp3.jmapiproxy2.cc", "cdn-msp.jmapinodeudzn.net"]
        return files.enumerated().map { index, filename in
            let imageName = filename.split(separator: "?").first.map(String.init) ?? filename
            let basename = (imageName as NSString).deletingPathExtension
            let segments = ComicSourceAdapter.jmSegmentCount(scrambleID: scrambleID, photoID: photoID, imageName: basename)
            let referer = "https://www.cdnhjk.net/"
            return ComicPage(imageUrl: "https://\(hosts[index % hosts.count])/media/photos/\(photoID)/\(filename)", referer: referer, headers: ComicSourceAdapter.requestHeaders(sourceID: "jm", referer: referer), verticalSegments: segments)
        }
    }

    private func fetchPicacgPages(comicId: String, chapterId: String) async throws -> [ComicPage] {
        let token = PicacgAuthStore.shared.token
        guard !token.isEmpty else { throw ComicFetchError.authenticationRequired("哔咔需要先登录。") }
        var pages: [ComicPage] = []
        var page = 1
        var pageCount = 1
        while page <= pageCount && page <= 100 {
            let path = "comics/\(comicId)/order/\(chapterId)/pages?page=\(page)"
            guard let url = URL(string: "https://picaapi.picacomic.com/\(path)") else { throw ComicFetchError.invalidURL }
            let response = try await request(url, headers: picacgHeaders(method: "GET", path: path, token: token))
            let root = try JSONSerialization.jsonObject(with: response.data) as? [String: Any]
            let payload = ((root?["data"] as? [String: Any])?["pages"] as? [String: Any]) ?? [:]
            pageCount = min(max(payload["pages"] as? Int ?? 1, 1), 100)
            let docs = payload["docs"] as? [[String: Any]] ?? []
            if docs.isEmpty { break }
            pages.append(contentsOf: docs.compactMap { row in
                let media = row["media"] as? [String: Any] ?? [:]
                guard let server = media["fileServer"] as? String, let path = media["path"] as? String else { return nil }
                return ComicPage(imageUrl: "\(server.trimmingCharacters(in: CharacterSet(charactersIn: "/")))/static/\(path.trimmingCharacters(in: CharacterSet(charactersIn: "/")))", referer: "https://picaapi.picacomic.com/", headers: ["api-key": ComicProtocolSecrets.value("PICACG_API_KEY"), "authorization": token, "image-quality": "original"])
            })
            page += 1
        }
        return pages
    }

    private func fetchEHentaiPages(comicId: String) async throws -> [ComicPage] {
        guard let galleryURL = URL(string: "https://e-hentai.org/g/\(comicId.trimmingCharacters(in: CharacterSet(charactersIn: "/")))/") else {
            throw ComicFetchError.invalidURL
        }
        let headers = ComicSourceAdapter.requestHeaders(sourceID: "ehentai", referer: "https://e-hentai.org/")
        let firstPage = try await request(galleryURL, headers: headers)
        let pageCount = ComicSourceAdapter.parseEHentaiPageCount(firstPage.body) ?? 0
        let previewPageCount = min(max((pageCount + 19) / 20, 1), 50)
        var imagePageURLs = ComicSourceAdapter.parseEHentaiImagePageLinks(firstPage.body, baseURL: firstPage.finalURL)
        var seenImagePageURLs = Set(imagePageURLs)

        if previewPageCount > 1 {
            for previewPage in 1..<previewPageCount {
                guard var components = URLComponents(url: galleryURL, resolvingAgainstBaseURL: false) else { continue }
                components.queryItems = [URLQueryItem(name: "p", value: String(previewPage))]
                guard let url = components.url else { continue }
                let response = try await request(url, headers: headers)
                for imagePageURL in ComicSourceAdapter.parseEHentaiImagePageLinks(response.body, baseURL: response.finalURL)
                    where seenImagePageURLs.insert(imagePageURL).inserted {
                    imagePageURLs.append(imagePageURL)
                }
            }
        }

        return imagePageURLs.prefix(1_000).map { pageURL in
            ComicPage(imageUrl: "", referer: galleryURL.absoluteString, headers: headers, resolutionUrl: pageURL)
        }
    }

    private func requestJMScrambleID(photoID: String) async -> Int {
        let timestamp = String(Int(Date().timeIntervalSince1970))
        let token = ComicProtocolSecrets.md5Hex(timestamp + ComicProtocolSecrets.value("JM_TOKEN_SECRET_2"))
        let path = "chapter_view_template?id=\(photoID)&mode=vertical&page=0&app_img_shunt=1&express=off"
        let domains = ["www.cdnhjk.net", "www.cdngwc.cc", "www.cdngwc.net", "www.cdngwc.club"]
        for domain in domains {
            guard let url = URL(string: "https://\(domain)/\(path)") else { continue }
            do {
                let response = try await request(url, headers: ["token": token, "tokenparam": "\(timestamp),2.1.2", "Cookie": "AVS=1"])
                if let scrambleID = ComicSourceAdapter.parseJMScrambleID(response.body) {
                    return scrambleID
                }
            } catch {
                continue
            }
        }
        return 220_980
    }

    private func fetchEHentaiListDetail(_ comic: ComicItem) async throws -> ComicItem {
        guard let url = URL(string: "https://e-hentai.org/g/\(comic.id)/") else {
            throw ComicFetchError.invalidURL
        }
        let response = try await request(url, headers: ComicSourceAdapter.requestHeaders(sourceID: "ehentai", referer: "https://e-hentai.org/"))
        let count = ComicSourceAdapter.parseEHentaiPageCount(response.body)
        let title = count.map { "完整画廊（\($0) 页）" } ?? "完整画廊"
        let chapters = [ComicChapter(id: comic.id, title: title, pageCount: count ?? 0)]
        return comicWithChapters(comic, chapters: chapters)
    }

    private func fetchEightMusesDetails(_ comic: ComicItem) async throws -> ComicItem {
        guard let url = URL(string: "https://8muses.io/g/\(comic.id)") else {
            throw ComicFetchError.invalidURL
        }
        let response = try await request(url, headers: ComicSourceAdapter.requestHeaders(sourceID: "8muses", referer: "https://8muses.io/"))
        let count = ComicSourceAdapter.parseEightMusesPageCount(response.body) ?? 1
        let chapters = [ComicChapter(id: String(min(max(count, 1), 1_000)), title: "全本（\(count) 页）", pageCount: count)]
        return comicWithChapters(comic, chapters: chapters)
    }

    private func fetchIkmmhDetails(_ comic: ComicItem) async throws -> ComicItem {
        guard let url = URL(string: comic.id.hasPrefix("http") ? comic.id : "https://ymcdnyfqdapp.ikmmh.com/book/\(comic.id)/") else {
            throw ComicFetchError.invalidURL
        }
        let response = try await request(url, headers: ComicSourceAdapter.requestHeaders(sourceID: "ikmmh", referer: "https://ymcdnyfqdapp.ikmmh.com/"))
        let path = url.lastPathComponent
        guard let chaptersURL = URL(string: "https://ymcdnyfqdapp.ikmmh.com/api/comic/zyz/chapters?ph=1&tempid=3&zpid=\(path)&page=0&line=200&orderby=desc") else {
            throw ComicFetchError.invalidURL
        }
        let chapterResponse = try await request(chaptersURL, headers: ComicSourceAdapter.requestHeaders(sourceID: "ikmmh", referer: url.absoluteString))
        guard let root = try JSONSerialization.jsonObject(with: chapterResponse.data) as? [String: Any] else { throw ComicFetchError.malformedResponse }
        let items = root["length"] as? [[String: Any]] ?? []
        let chapters = items.compactMap { row -> ComicChapter? in
            guard let chapterURL = row["url"] as? String else { return nil }
            return ComicChapter(id: chapterURL, title: row["name"] as? String ?? "章节")
        }
        let title = extractText(in: response.body, tag: "h1") ?? comic.title
        return comicWithChapters(ComicItem(id: comic.id, sourceId: comic.sourceId, title: title, author: comic.author, coverUrl: comic.coverUrl, tags: comic.tags), chapters: chapters)
    }

    private func detailURL(for comic: ComicItem, source: ComicSourceDefinition) -> URL? {
        switch source.id {
        case "dongman91": return URL(string: "\(source.baseUrl)/comic/\(comic.id)/")
        case "8muses": return URL(string: "\(source.baseUrl)/g/\(comic.id)")
        case "gedecomix": return URL(string: "\(source.baseUrl)/porncomic/\(comic.id)/")
        case "ikmmh": return URL(string: comic.id.hasPrefix("http") ? comic.id : "\(source.baseUrl)/book/\(comic.id).html")
        case "ehentai": return URL(string: "\(source.baseUrl)/g/\(comic.id)/")
        default: return nil
        }
    }

    private func comicWithChapters(_ comic: ComicItem, chapters: [ComicChapter]) -> ComicItem {
        ComicItem(id: comic.id, sourceId: comic.sourceId, title: comic.title, author: comic.author, coverUrl: comic.coverUrl, category: comic.category, tags: comic.tags, chapters: chapters, latestChapterTitle: chapters.first?.title ?? "")
    }

    private func extractImageURL(_ html: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: #"(?i)<img[^>]+src=['"]([^'"]+)['"]"#) else { return nil }
        let ns = html as NSString
        guard let match = regex.firstMatch(in: html, range: NSRange(location: 0, length: ns.length)) else { return nil }
        return ns.substring(with: match.range(at: 1))
    }

    private func extractText(in html: String, tag: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: "(?is)<\(tag)\\b[^>]*>(.*?)</\(tag)>") else { return nil }
        let ns = html as NSString
        guard let match = regex.firstMatch(in: html, range: NSRange(location: 0, length: ns.length)) else { return nil }
        return ns.substring(with: match.range(at: 1)).replacingOccurrences(of: "<[^>]+>", with: " ", options: .regularExpression).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func parseBaoziPages(html: String, referer: String) -> [ComicPage] {
        var candidates: [String] = []
        var seen = Set<String>()

        // 1. 优先提取 amp-img 与 img 的 data-src 或 src 属性
        let imgTagPattern = #"(?i)<(?:amp-img|img)\b[^>]+?(?:data-src|src)\s*=\s*['"]([^'"]+)['"]"#
        if let regex = try? NSRegularExpression(pattern: imgTagPattern) {
            let ns = html as NSString
            let matches = regex.matches(in: html, range: NSRange(location: 0, length: ns.length))
            for match in matches {
                let urlStr = ns.substring(with: match.range(at: 1)).trimmingCharacters(in: .whitespacesAndNewlines)
                candidates.append(urlStr)
            }
        }

        // 2. 提取 AMP 状态中的 json 图片数据 {"url": "..."}
        let jsonPattern = #"\{"url":\s*"([^"]+)"\}"#
        if let regex = try? NSRegularExpression(pattern: jsonPattern) {
            let ns = html as NSString
            let matches = regex.matches(in: html, range: NSRange(location: 0, length: ns.length))
            for match in matches {
                let urlStr = ns.substring(with: match.range(at: 1)).trimmingCharacters(in: .whitespacesAndNewlines)
                candidates.append(urlStr)
            }
        }

        // 3. 通用正则匹配 bzcdn 图床 URL
        let cdnPattern = #"https://[a-zA-Z0-9.-]+\.bzcdn\.net/scomic/[^"'\s<>]+\.(?:jpg|jpeg|png|webp)"#
        if let regex = try? NSRegularExpression(pattern: cdnPattern) {
            let ns = html as NSString
            let matches = regex.matches(in: html, range: NSRange(location: 0, length: ns.length))
            for match in matches {
                let urlStr = ns.substring(with: match.range).trimmingCharacters(in: .whitespacesAndNewlines)
                candidates.append(urlStr)
            }
        }

        let filtered = candidates.compactMap { rawURL -> String? in
            var cleanURL = rawURL.replacingOccurrences(of: "&amp;", with: "&")
            if cleanURL.hasPrefix("//") {
                cleanURL = "https:" + cleanURL
            }
            guard cleanURL.hasPrefix("http://") || cleanURL.hasPrefix("https://") else { return nil }
            let lower = cleanURL.lowercased()
            guard !lower.contains("icon") &&
                  !lower.contains("favicon") &&
                  !lower.contains("logo") &&
                  !lower.contains("default_cover") &&
                  !lower.contains("social-share") &&
                  !lower.contains("bg_detail") &&
                  !lower.contains("/cover/") else {
                return nil
            }
            guard seen.insert(cleanURL).inserted else { return nil }
            return cleanURL
        }

        return filtered.map { imageURL in
            ComicPage(
                imageUrl: imageURL,
                referer: referer,
                headers: ComicSourceAdapter.requestHeaders(sourceID: "baozi", referer: referer)
            )
        }
    }

    private func request(_ url: URL, method: String = "GET", body: Data? = nil, headers: [String: String]) async throws -> ComicHTTPResponse {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.httpBody = body
        headers.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else { throw ComicFetchError.invalidResponse }
            guard (200...299).contains(http.statusCode) else { throw ComicFetchError.httpStatus(http.statusCode) }
            return ComicHTTPResponse(data: data, statusCode: http.statusCode, finalURL: http.url ?? url)
        } catch let error as ComicFetchError {
            throw error
        } catch {
            throw ComicFetchError.networkError(error.localizedDescription)
        }
    }

    private func komiicRequest(operation: String, variables: [String: Any], query: String) async throws -> [String: Any] {
        let body = try JSONSerialization.data(withJSONObject: ["operationName": operation, "variables": variables, "query": query])
        guard let url = URL(string: "https://komiic.com/api/query") else { throw ComicFetchError.invalidURL }
        let response = try await request(url, method: "POST", body: body, headers: ["Referer": "https://komiic.com/", "Content-Type": "application/json"])
        guard let json = try? JSONSerialization.jsonObject(with: response.data) as? [String: Any] else { throw ComicFetchError.malformedResponse }
        if let errors = json["errors"] as? [[String: Any]], let message = errors.first?["message"] as? String {
            throw ComicFetchError.networkError(message)
        }
        return json
    }

    private func jmRequest(path: String, secretKey: String) async throws -> [String: Any] {
        var lastError: Error?
        for domain in ["www.cdnhjk.net", "www.cdngwc.cc", "www.cdngwc.net", "www.cdngwc.club"] {
            do {
                let timestamp = String(Int(Date().timeIntervalSince1970))
                let token = ComicProtocolSecrets.md5Hex(timestamp + ComicProtocolSecrets.value(secretKey))
                let headers = ["token": token, "tokenparam": "\(timestamp),2.1.2", "Cookie": "AVS=1", "X-Requested-With": "com.JMComic3.app"]
                guard let url = URL(string: "https://\(domain)/\(path)") else { throw ComicFetchError.invalidURL }
                let response = try await request(url, headers: headers)
                guard let outer = try JSONSerialization.jsonObject(with: response.data) as? [String: Any],
                      outer["code"] as? Int == 200, let encoded = outer["data"] as? String,
                      let decoded = ComicProtocolSecrets.decryptJMPayload(encoded, timestamp: timestamp),
                      let result = try JSONSerialization.jsonObject(with: decoded) as? [String: Any] else {
                    throw ComicFetchError.malformedResponse
                }
                return result
            } catch { lastError = error }
        }
        throw lastError ?? ComicFetchError.networkError("禁漫接口连接失败。")
    }

    private func picacgHeaders(method: String, path: String, token: String) -> [String: String] {
        let timestamp = String(Int(Date().timeIntervalSince1970))
        let nonce = UUID().uuidString.replacingOccurrences(of: "-", with: "")
        let apiKey = ComicProtocolSecrets.value("PICACG_API_KEY")
        let payload = (path + timestamp + nonce + method.uppercased() + apiKey).lowercased()
        let signature = ComicProtocolSecrets.hmacSHA256(payload, secret: ComicProtocolSecrets.value("PICACG_SIGNATURE_SECRET"))
        return [
            "api-key": apiKey, "accept": "application/vnd.picacomic.com.v1+json", "app-channel": "3",
            "authorization": token, "time": timestamp, "nonce": nonce, "app-version": "2.2.1.3.3.4",
            "app-uuid": "defaultUuid", "image-quality": "original", "app-platform": "ios",
            "app-build-version": "45", "version": "v1.4.1", "signature": signature
        ]
    }

    static func decodeBaoziFeed(data: Data, statusCode: Int) throws -> [ComicItem] {
        guard (200...299).contains(statusCode) else {
            throw ComicFetchError.httpStatus(statusCode)
        }
        guard
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let items = object["items"] as? [[String: Any]]
        else {
            throw ComicFetchError.malformedResponse
        }

        return items.compactMap { item in
            let comicID = (item["comic_id"] as? String)
                ?? (item["comic_id"] as? NSNumber)?.stringValue
                ?? ""
            let title = (item["name"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !comicID.isEmpty, !title.isEmpty else { return nil }

            let author = item["author"] as? String ?? ""
            let topicImage = item["topic_img"] as? String ?? ""
            let tags = (item["type_names"] as? [String] ?? []).filter { !$0.isEmpty }
            let coverURL = topicImage.isEmpty ? "" : "https://s1.bzcdn.net/cover/\(topicImage)"

            return ComicItem(
                id: comicID,
                sourceId: ComicSourceCatalog.baozi.id,
                title: title,
                author: author,
                coverUrl: coverURL,
                category: tags.first ?? "",
                tags: tags,
                chapters: [],
                latestChapterTitle: ""
            )
        }
    }

    public func fetchChapterPages(comicId: String, sourceId: String, chapterId: String) async throws -> [ComicPage] {
        let key = "\(sourceId):\(comicId):\(chapterId)"
        if let cached = chapterCache[key], !cached.isEmpty {
            return cached
        }
        let taskToken: UUID
        let task: Task<[ComicPage], Error>
        if let ongoing = inFlightChapterTasks[key], !ongoing.task.isCancelled {
            taskToken = ongoing.id
            task = ongoing.task
        } else {
            let newToken = UUID()
            let created = Task<[ComicPage], Error> { [weak self] in
                guard let self else { throw CancellationError() }
                defer {
                    if self.inFlightChapterTasks[key]?.id == newToken {
                        self.inFlightChapterTasks[key] = nil
                    }
                }
                return try await self.performWithTimeout(
                    seconds: 18,
                    timeoutMessage: "加载章节图片超时，请检查网络或切换漫画源后重试。"
                ) {
                    try await self.loadChapterPagesUncached(comicId: comicId, sourceId: sourceId, chapterId: chapterId)
                }
            }
            inFlightChapterTasks[key] = (id: newToken, task: created)
            taskToken = newToken
            task = created
        }

        do {
            let pages = try await awaitTask(
                task,
                seconds: 19,
                timeoutMessage: "加载章节图片超时，请检查网络或切换漫画源后重试。",
                cancelWorkOnCancel: true
            )
            if !pages.isEmpty {
                chapterCache[key] = pages
            }
            return pages
        } catch {
            if inFlightChapterTasks[key]?.id == taskToken {
                inFlightChapterTasks[key] = nil
            }
            throw error
        }
    }

    private func loadChapterPagesUncached(comicId: String, sourceId: String, chapterId: String) async throws -> [ComicPage] {
        switch sourceId {
        case "baozi":
            return try await fetchChapterPages(comicId: comicId, chapterId: chapterId)
        case "copy":
            return try await fetchCopyPages(comicId: comicId, chapterId: chapterId)
        case "komiic":
            return try await fetchKomiicPages(comicId: comicId, chapterId: chapterId)
        case "jm":
            return try await fetchJMPages(chapterId: chapterId)
        case "picacg":
            return try await fetchPicacgPages(comicId: comicId, chapterId: chapterId)
        case "8muses":
            let count = min(max(Int(chapterId) ?? 1, 1), 1_000)
            let base = "https://8muses.io/g/\(comicId)"
            return ComicSourceAdapter.eightMusesPageURLs(comicID: comicId, count: count).map { url in
                ComicPage(imageUrl: url.absoluteString, referer: base, headers: ComicSourceAdapter.requestHeaders(sourceID: sourceId, referer: base))
            }
        case "ehentai":
            return try await fetchEHentaiPages(comicId: comicId)
        case "ikmmh":
            return try await fetchIkmmhPages(chapterId: chapterId)
        default:
            guard let source = ComicSourceCatalog.allSources.first(where: { $0.id == sourceId }),
                  let sourceURL = URL(string: source.baseUrl),
                  let url = URL(string: chapterId, relativeTo: sourceURL)?.absoluteURL else { throw ComicFetchError.invalidURL }
            let response = try await request(url, headers: ComicSourceAdapter.requestHeaders(sourceID: sourceId, referer: source.baseUrl))
            return ComicSourceAdapter.parseImagePages(response.body, baseURL: response.finalURL, referer: response.finalURL.absoluteString, sourceID: sourceId)
        }
    }

    private func fetchIkmmhPages(chapterId: String) async throws -> [ComicPage] {
        guard let baseURL = URL(string: ComicSourceCatalog.ikmmh.baseUrl),
              let chapterURL = URL(string: chapterId, relativeTo: baseURL)?.absoluteURL,
              chapterURL.host == baseURL.host else { throw ComicFetchError.invalidURL }
        let parts = chapterURL.path.split(separator: "/").map(String.init)
        guard parts.count >= 3, parts[0] == "chapter" else { throw ComicFetchError.invalidURL }
        let chapterNumber = parts[2].split(separator: ".").first.map(String.init) ?? parts[2]

        var pages: [ComicPage] = []
        var offset = 0
        var total = Int.max
        while offset < total && pages.count < 1_000 {
            var form = URLComponents()
            form.queryItems = [
                URLQueryItem(name: "id", value: chapterNumber),
                URLQueryItem(name: "aid", value: parts[1]),
                URLQueryItem(name: "offset", value: String(offset)),
                URLQueryItem(name: "limit", value: "10")
            ]
            guard let body = form.percentEncodedQuery?.data(using: .utf8) else { throw ComicFetchError.malformedResponse }
            let apiURL = baseURL.appending(path: "api/comic/read/pics")
            let response = try await request(apiURL, method: "POST", body: body, headers: [
                "User-Agent": ComicSourceAdapter.userAgent,
                "Referer": chapterURL.absoluteString,
                "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
                "X-Requested-With": "XMLHttpRequest"
            ])
            let batch = try ComicSourceAdapter.decodeIkmmhPageBatch(response.data, referer: chapterURL.absoluteString)
            guard !batch.pages.isEmpty else { break }
            pages.append(contentsOf: batch.pages)
            offset += batch.pages.count
            if batch.total > 0 { total = batch.total }
            else if batch.pages.count < 10 { break }
        }
        return pages
    }

    // 真实抓取章节正文全部图片
    public func fetchChapterPages(comicId: String, chapterId: String) async throws -> [ComicPage] {
        guard let baseURL = URL(string: "https://cn.webmota.com") else {
            throw ComicFetchError.invalidURL
        }
        let initialURL: URL
        if chapterId.hasPrefix("http://") || chapterId.hasPrefix("https://") {
            guard let parsed = URL(string: chapterId) else { throw ComicFetchError.invalidURL }
            initialURL = parsed
        } else if chapterId.hasPrefix("/") {
            guard let resolved = URL(string: chapterId, relativeTo: baseURL)?.absoluteURL else { throw ComicFetchError.invalidURL }
            initialURL = resolved
        } else {
            guard let resolved = URL(string: "/comic/chapter/\(comicId)/0_\(chapterId).html", relativeTo: baseURL)?.absoluteURL else { throw ComicFetchError.invalidURL }
            initialURL = resolved
        }

        var request = URLRequest(url: initialURL)
        request.setValue("https://cn.webmota.com/comic/\(comicId)", forHTTPHeaderField: "Referer")
        request.setValue(ComicSourceAdapter.userAgent, forHTTPHeaderField: "User-Agent")
        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw ComicFetchError.networkError(error.localizedDescription)
        }
        guard let httpResponse = response as? HTTPURLResponse else { throw ComicFetchError.invalidResponse }

        // 404 回退机制：完整保留 Query 与 Path 切换至 cn.twbzmg.com
        if httpResponse.statusCode == 404,
           var fallbackComponents = URLComponents(url: initialURL, resolvingAgainstBaseURL: true) {
            fallbackComponents.scheme = "https"
            fallbackComponents.host = "cn.twbzmg.com"
            if let fallbackURL = fallbackComponents.url {
                var fallbackRequest = URLRequest(url: fallbackURL)
                fallbackRequest.setValue("https://cn.twbzmg.com/", forHTTPHeaderField: "Referer")
                fallbackRequest.setValue(ComicSourceAdapter.userAgent, forHTTPHeaderField: "User-Agent")
                let (fallbackData, fallbackResponse) = try await session.data(for: fallbackRequest)
                guard let fallbackHTTP = fallbackResponse as? HTTPURLResponse,
                      (200...299).contains(fallbackHTTP.statusCode) else {
                    throw ComicFetchError.httpStatus((fallbackResponse as? HTTPURLResponse)?.statusCode ?? 404)
                }
                guard let html = String(data: fallbackData, encoding: .utf8) else { throw ComicFetchError.malformedResponse }
                return parseBaoziPages(html: html, referer: fallbackHTTP.url?.absoluteString ?? fallbackURL.absoluteString)
            }
        }
        guard (200...299).contains(httpResponse.statusCode) else { throw ComicFetchError.httpStatus(httpResponse.statusCode) }
        guard let html = String(data: data, encoding: .utf8) else { throw ComicFetchError.malformedResponse }
        return parseBaoziPages(html: html, referer: httpResponse.url?.absoluteString ?? initialURL.absoluteString)
    }

    public func fetchChapters(comicId: String) async throws -> [ComicChapter] {
        guard let url = URL(string: "https://cn.webmota.com/comic/\(comicId)") else {
            throw ComicFetchError.invalidURL
        }

        var request = URLRequest(url: url)
        request.setValue("https://cn.webmota.com", forHTTPHeaderField: "Referer")
        request.setValue(ComicSourceAdapter.userAgent, forHTTPHeaderField: "User-Agent")
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw ComicFetchError.networkError(error.localizedDescription)
        }
        guard let httpResponse = response as? HTTPURLResponse else {
            throw ComicFetchError.invalidResponse
        }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw ComicFetchError.httpStatus(httpResponse.statusCode)
        }
        guard let html = String(data: data, encoding: .utf8) else {
            throw ComicFetchError.malformedResponse
        }
        return Self.parseBaoziChapters(html: html)
    }

    static func parseBaoziChapters(html: String) -> [ComicChapter] {
        // 优先锁定 id="chapter-items"（正序完整章节目录），避开顶部最新的 VIP/预告占位章节
        var scopeHTML = html
        if let chapterItemsRange = html.range(of: "id=\"chapter-items\"") {
            scopeHTML = String(html[chapterItemsRange.lowerBound...])
        }

        guard let anchorRegex = try? NSRegularExpression(
            pattern: #"(?is)<a\b([^>]*)>(.*?)</a>"#
        ),
        let hrefRegex = try? NSRegularExpression(pattern: #"(?i)\bhref\s*=\s*['\"]([^'\"]+)['\"]"#),
        let classRegex = try? NSRegularExpression(pattern: #"(?i)\bclass\s*=\s*['\"]([^'\"]*)['\"]"#),
        let spanRegex = try? NSRegularExpression(pattern: #"(?is)<span\b[^>]*>(.*?)</span>"#) else {
            return []
        }

        let nsHTML = scopeHTML as NSString
        let anchors = anchorRegex.matches(in: scopeHTML, range: NSRange(location: 0, length: nsHTML.length))
        var chapters: [ComicChapter] = []
        var seenURLs = Set<String>()

        for anchor in anchors {
            let attributes = nsHTML.substring(with: anchor.range(at: 1)) as NSString
            let body = nsHTML.substring(with: anchor.range(at: 2))
            let attributeRange = NSRange(location: 0, length: attributes.length)
            guard let classMatch = classRegex.firstMatch(in: attributes as String, range: attributeRange),
              attributes.substring(with: classMatch.range(at: 1)).contains("comics-chapters__item"),
              let hrefMatch = hrefRegex.firstMatch(in: attributes as String, range: attributeRange) else {
                continue
            }

            let href = Self.decodeHTML(attributes.substring(with: hrefMatch.range(at: 1))).trimmingCharacters(in: .whitespacesAndNewlines)
            guard !href.isEmpty, seenURLs.insert(href).inserted else { continue }

            let title: String
            if let spanMatch = spanRegex.firstMatch(in: body, range: NSRange(location: 0, length: (body as NSString).length)),
               let titleRange = Range(spanMatch.range(at: 1), in: body) {
                title = Self.stripHTML(String(body[titleRange])).trimmingCharacters(in: .whitespacesAndNewlines)
            } else {
                title = Self.stripHTML(body).trimmingCharacters(in: .whitespacesAndNewlines)
            }
            guard !title.isEmpty else { continue }
            chapters.append(ComicChapter(id: href, title: title))
        }
        return chapters
    }

    private static func stripHTML(_ value: String) -> String {
        guard let regex = try? NSRegularExpression(pattern: #"<[^>]+>"#) else { return value }
        let range = NSRange(location: 0, length: (value as NSString).length)
        return decodeHTML(regex.stringByReplacingMatches(in: value, range: range, withTemplate: " "))
    }

    private static func decodeHTML(_ value: String) -> String {
        value
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
    }

    public func fetchComicDetails(_ comic: ComicItem) async throws -> ComicItem {
        let updatedComic = try await performWithTimeout(
            seconds: 16,
            timeoutMessage: "加载漫画章节超时，请检查网络或切换漫画源后重试。"
        ) {
            try await self.attachChapters(to: comic)
        }
        try Task.checkCancellation()
        if let index = comics.firstIndex(where: { $0.id == comic.id && $0.sourceId == comic.sourceId }) {
            comics[index] = updatedComic
        }
        // 静默预热第 1 话，用户点击「开始阅读」时 0ms 瞬间进入
        if let firstChapter = updatedComic.chapters.first {
            prewarmChapter(sourceId: updatedComic.sourceId, comicId: updatedComic.id, chapterId: firstChapter.id)
        }
        return updatedComic
    }

    private func performWithTimeout<T: Sendable>(
        seconds: TimeInterval,
        timeoutMessage: String,
        operation: @escaping @MainActor () async throws -> T
    ) async throws -> T {
        let workTask = Task<T, Error> {
            try await operation()
        }
        return try await awaitTask(
            workTask,
            seconds: seconds,
            timeoutMessage: timeoutMessage,
            cancelWorkOnCancel: true
        )
    }

    private func awaitTask<T: Sendable>(
        _ workTask: Task<T, Error>,
        seconds: TimeInterval,
        timeoutMessage: String,
        cancelWorkOnCancel: Bool
    ) async throws -> T {
        try Task.checkCancellation()
        let box = AsyncTimeoutBox<T>()
        let waiterTask = Task<Void, Never> {
            do {
                let value = try await workTask.value
                box.finish(.success(value), cancelWork: false)
            } catch {
                box.finish(.failure(error), cancelWork: false)
            }
        }
        let timeoutTask = Task<Void, Never> {
            do {
                try await Task.sleep(for: .seconds(seconds))
                waiterTask.cancel()
                box.finish(.failure(ComicFetchError.networkError(timeoutMessage)), cancelWork: true)
            } catch {
                // Sleep cancelled on completion or external cancellation
            }
        }
        box.attach(workTask: workTask, timeoutTask: timeoutTask)

        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                box.configure(continuation: continuation)
            }
        } onCancel: {
            waiterTask.cancel()
            box.finish(.failure(CancellationError()), cancelWork: cancelWorkOnCancel)
        }
    }

    private final class AsyncTimeoutBox<T: Sendable>: @unchecked Sendable {
        private let lock = NSLock()
        private var continuation: CheckedContinuation<T, Error>?
        private var pendingResult: Result<T, Error>?
        private var isCompleted = false
        private var workTask: Task<T, Error>?
        private var timeoutTask: Task<Void, Never>?

        func configure(continuation: CheckedContinuation<T, Error>) {
            lock.lock()
            if let result = pendingResult {
                pendingResult = nil
                lock.unlock()
                continuation.resume(with: result)
                return
            }
            self.continuation = continuation
            lock.unlock()
        }

        func attach(workTask: Task<T, Error>, timeoutTask: Task<Void, Never>) {
            lock.lock()
            if isCompleted {
                lock.unlock()
                timeoutTask.cancel()
                return
            }
            self.workTask = workTask
            self.timeoutTask = timeoutTask
            lock.unlock()
        }

        func finish(_ result: Result<T, Error>, cancelWork: Bool) {
            lock.lock()
            guard !isCompleted else {
                lock.unlock()
                return
            }
            isCompleted = true
            let cont = continuation
            continuation = nil
            let tTask = timeoutTask
            let wTask = workTask
            timeoutTask = nil
            workTask = nil
            if cont == nil {
                pendingResult = result
            }
            lock.unlock()

            tTask?.cancel()
            if cancelWork {
                wTask?.cancel()
            }
            cont?.resume(with: result)
        }
    }

    private func attachChapters(to comic: ComicItem) async throws -> ComicItem {
        switch comic.sourceId {
        case "baozi":
            return comicWithChapters(comic, chapters: try await fetchChapters(comicId: comic.id))
        case "copy":
            return try await fetchCopyDetails(comic)
        case "komiic":
            return try await fetchKomiicDetails(comic)
        case "jm":
            return try await fetchJMDetails(comic)
        case "picacg":
            return try await fetchPicacgDetails(comic)
        default:
            guard let source = ComicSourceCatalog.allSources.first(where: { $0.id == comic.sourceId }) else { return comic }
            if source.id == "8muses" { return try await fetchEightMusesDetails(comic) }
            if source.id == "ehentai" { return try await fetchEHentaiListDetail(comic) }
            if source.id == "ikmmh" { return try await fetchIkmmhDetails(comic) }
            guard let url = detailURL(for: comic, source: source) else { return comic }
            let response = try await request(url, headers: ComicSourceAdapter.requestHeaders(sourceID: source.id, referer: source.baseUrl))
            let chapters = ComicSourceAdapter.parseChapters(response.body, sourceID: source.id, finalURL: response.finalURL)
            return comicWithChapters(comic, chapters: chapters)
        }
    }
}
