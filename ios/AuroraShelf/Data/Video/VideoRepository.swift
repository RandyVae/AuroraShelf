import Foundation

public struct PlaybackSourceResult: Sendable {
    public let url: URL
    public let headers: [String: String]
    public let referer: String?
    public let title: String

    public init(url: URL, headers: [String: String] = [:], referer: String? = nil, title: String) {
        self.url = url
        var h = headers
        h["User-Agent"] = h["User-Agent"] ?? "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile/15E148 Safari/604.1"
        if let referer = referer {
            h["Referer"] = referer
            if let components = URLComponents(string: referer),
               let scheme = components.scheme, let host = components.host {
                h["Origin"] = "\(scheme)://\(host)" + (components.port.map { ":\($0)" } ?? "")
            }
        }
        self.headers = h
        self.referer = referer
        self.title = title
    }
}

private actor StreamCache {
    private var streamMap: [String: URL] = [:]

    func get(key: String) -> URL? {
        streamMap[key]
    }

    func set(key: String, url: URL) {
        streamMap[key] = url
    }
}

/// Apple TV 风格分类货架实体
public struct CategoryShelf: Identifiable, Sendable, Equatable {
    public var id: String { category }
    public let category: String
    public var videos: [VideoItem]
    public var isLoading: Bool

    public init(category: String, videos: [VideoItem], isLoading: Bool = false) {
        self.category = category
        self.videos = videos
        self.isLoading = isLoading
    }
}

@MainActor
public final class VideoRepository: ObservableObject {
    public static let shared = VideoRepository()
    private static let USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

    // 默认使用 华视影院（大陆直连推荐）
    @Published public var currentSource: VideoSourceDefinition = VideoSourceCatalog.huavod
    @Published public var currentCategory: String = "全部"
    @Published public var feedVideos: [VideoItem] = []
    @Published public var billboardVideo: VideoItem?
    @Published public var top10Videos: [VideoItem] = []
    @Published public var categoryShelves: [CategoryShelf] = []
    @Published public var isLoading: Bool = false
    @Published public var feedError: String?
    @Published public var searchResults: [VideoItem] = []
    @Published public var isSearching: Bool = false
    @Published public var searchError: String?
    @Published public var currentPage: Int = 1
    @Published public var canLoadMore: Bool = true
    @Published public var isLoadingNextPage: Bool = false

    private let session: URLSession
    private let cache = StreamCache()
    private var currentLoadTask: Task<Void, Never>?
    private var currentSearchToken = UUID()

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.httpAdditionalHeaders = [
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        ]
        self.session = URLSession.permissive(configuration: config)

        let args = ProcessInfo.processInfo.arguments
        if let idx = args.firstIndex(of: "--video-source"), idx + 1 < args.count {
            let srcId = args[idx + 1]
            let src = VideoSourceCatalog.find(by: srcId)
            self.switchSource(to: src)
        } else {
            self.switchSource(to: VideoSourceCatalog.dongman91)
        }
    }

    public func switchCategory(to category: String) {
        self.currentCategory = category
        self.feedVideos = []
        self.billboardVideo = nil
        self.top10Videos = []
        self.categoryShelves = []
        self.feedError = nil
        self.currentPage = 1
        self.canLoadMore = true
        self.isLoadingNextPage = false
        self.isLoading = true
        self.loadFeed(for: self.currentSource, category: category)
    }

    public func switchSource(to source: VideoSourceDefinition) {
        self.currentSource = source
        self.currentCategory = source.categories.first ?? "全部"
        self.feedVideos = []
        self.billboardVideo = nil
        self.top10Videos = []
        let shelfCategories = source.categories.filter { $0 != "全部" }
        self.categoryShelves = shelfCategories.isEmpty ? [CategoryShelf(category: "精选推荐", videos: [], isLoading: true)] : shelfCategories.map { CategoryShelf(category: $0, videos: [], isLoading: true) }
        self.feedError = nil
        self.currentPage = 1
        self.canLoadMore = true
        self.isLoadingNextPage = false
        self.isLoading = true
        self.loadFeed(for: source, category: self.currentCategory)
    }

    public func loadFeed(for source: VideoSourceDefinition, category: String = "全部") {
        currentLoadTask?.cancel()
        self.isLoading = true
        self.feedError = nil
        self.currentPage = 1
        self.canLoadMore = true
        self.isLoadingNextPage = false
        let targetSource = source
        currentLoadTask = Task {
            do {
                let videos = try await fetchVideosForSource(targetSource, category: category, page: 1)
                guard !Task.isCancelled else { return }
                guard self.currentSource.id == targetSource.id && self.currentCategory == category else { return }
                var seenIDs = Set<String>()
                var uniqueVideos: [VideoItem] = []
                for v in videos {
                    if !seenIDs.contains(v.id) {
                        seenIDs.insert(v.id)
                        uniqueVideos.append(v)
                    }
                }
                self.feedVideos = uniqueVideos
                self.billboardVideo = uniqueVideos.first
                self.top10Videos = uniqueVideos.count > 1 ? Array(uniqueVideos.dropFirst().prefix(10)) : uniqueVideos
                self.isLoading = false
                self.canLoadMore = !uniqueVideos.isEmpty
                self.currentPage = 1

                // 构建 Apple TV 风格的横向分类货架流（优先从首屏优质列表中智能分流，零网络阻塞秒开）
                let shelfCategories = targetSource.categories.filter { $0 != "全部" }
                if shelfCategories.isEmpty {
                    self.categoryShelves = [CategoryShelf(category: "精选推荐", videos: Array(uniqueVideos.prefix(10)), isLoading: false)]
                } else {
                    var remainingVideos = uniqueVideos
                    var shelves: [CategoryShelf] = []
                    for cat in shelfCategories.prefix(4) {
                        let matching = remainingVideos.filter { v in
                            v.tags.contains(where: { $0.localizedCaseInsensitiveContains(cat) }) ||
                            v.category.localizedCaseInsensitiveContains(cat) ||
                            v.title.localizedCaseInsensitiveContains(cat)
                        }
                        let shelfVids: [VideoItem]
                        if matching.count >= 3 {
                            shelfVids = Array(matching.prefix(10))
                            remainingVideos.removeAll { v in shelfVids.contains(where: { $0.id == v.id }) }
                        } else {
                            shelfVids = Array(remainingVideos.prefix(8))
                            remainingVideos = Array(remainingVideos.dropFirst(shelfVids.count))
                        }
                        if !shelfVids.isEmpty {
                            shelves.append(CategoryShelf(category: cat, videos: shelfVids, isLoading: false))
                        }
                    }
                    if shelves.isEmpty {
                        shelves = [CategoryShelf(category: "精选推荐", videos: Array(uniqueVideos.prefix(10)), isLoading: false)]
                    }
                    self.categoryShelves = shelves
                }

                // 重点源（如华视影院 22s 广告倒计时锁）前置异步静默预热前 3 部视频播放流
                if targetSource.id == VideoSourceCatalog.huavod.id {
                    for video in uniqueVideos.prefix(3) {
                        VideoPreloadManager.shared.prewarm(video: video)
                    }
                }
            } catch is CancellationError {
                return
            } catch {
                guard !Task.isCancelled else { return }
                guard self.currentSource.id == targetSource.id && self.currentCategory == category else { return }
                self.feedError = error.localizedDescription
                self.categoryShelves = []
                self.isLoading = false
            }
        }
    }

    /// 跨栏目差异化推荐筛选器：彻底杜绝首页多栏目封面与内容同质化
    public static func differentiateShelfVideos(
        candidateVideos: [VideoItem],
        allocatedIDs: inout Set<String>,
        minShelfCount: Int = 10
    ) -> [VideoItem] {
        // 1. 独占新内容（从未在上方货架或 Top 10 展现过的）
        let exclusive = candidateVideos.filter { !allocatedIDs.contains($0.id) }

        // 如果独占视频充足，直接取前 minShelfCount 部
        let result: [VideoItem]
        if exclusive.count >= minShelfCount {
            result = Array(exclusive.prefix(minShelfCount))
        } else {
            // 独占视频排在最前，重复视频沉底填充
            let duplicates = candidateVideos.filter { allocatedIDs.contains($0.id) }
            result = exclusive + duplicates.prefix(minShelfCount - exclusive.count)
        }

        for v in result {
            allocatedIDs.insert(v.id)
        }
        return result
    }

    public func fetchCategoryVideos(source: VideoSourceDefinition, category: String, page: Int = 1) async throws -> [VideoItem] {
        try await fetchVideosForSource(source, category: category, page: page)
    }

    public func loadNextPage() async {
        guard canLoadMore, !isLoading, !isLoadingNextPage else { return }
        self.isLoadingNextPage = true
        defer { self.isLoadingNextPage = false }

        let targetSource = currentSource
        let targetCategory = currentCategory
        let nextPage = currentPage + 1

        do {
            let newVideos = try await fetchVideosForSource(targetSource, category: targetCategory, page: nextPage)
            guard !Task.isCancelled, self.currentSource.id == targetSource.id, self.currentCategory == targetCategory else { return }
            let existingIDs = Set(self.feedVideos.map(\.id))
            let uniqueNewVideos = newVideos.filter { !existingIDs.contains($0.id) }

            if uniqueNewVideos.isEmpty {
                self.canLoadMore = false
            } else {
                self.feedVideos.append(contentsOf: uniqueNewVideos)
                self.currentPage = nextPage
                self.canLoadMore = !newVideos.isEmpty
            }
        } catch {
            guard !Task.isCancelled, self.currentSource.id == targetSource.id, self.currentCategory == targetCategory else { return }
            // 网络抖动或临时异常不应永久禁用滚动分页，保留 canLoadMore 允许重试
            self.feedError = error.localizedDescription
        }
    }

    func fetchVideosForSource(_ source: VideoSourceDefinition, category: String = "全部", page: Int = 1) async throws -> [VideoItem] {
        let page = max(page, 1)
        switch source.id {
        case VideoSourceCatalog.dongman91.id:
            return try await fetch91DongmanFeed(category: category, page: page)
        case VideoSourceCatalog.huangguo.id:
            return try await fetchHuangguoFeed(category: category, page: page)
        case VideoSourceCatalog.porn91.id:
            return try await fetch91PornFeed(category: category, page: page)
        case VideoSourceCatalog.huavod.id:
            return try await fetchRealHuavodFeed(category: category, page: page)
        case VideoSourceCatalog.beeg.id:
            return try await fetchBeegFeed(category: category, page: page)
        case VideoSourceCatalog.xvideos.id:
            return try await UniversalVideoEngine.fetchXVideosFeed(category: category, session: session, page: page)
        case VideoSourceCatalog.eporner.id:
            return try await UniversalVideoEngine.fetchEPORNERFeed(category: category, session: session, page: page)
        default:
            guard VideoSourceCatalog.htmlAdapterSourceIDs.contains(source.id) else {
                throw VideoFeedError.unsupportedSource(source: source.name)
            }
            return try await fetchHTMLSourceFeed(source: source, category: category, page: page)
        }
    }

    private func fetchHTMLSourceFeed(source: VideoSourceDefinition, category: String, page: Int = 1) async throws -> [VideoItem] {
        guard let url = HTMLVideoSourceAdapter.feedURL(for: source, category: category, page: page) else {
            throw VideoFeedError.unsupportedSource(source: source.name)
        }
        var request = URLRequest(url: url)
        request.setValue(HTMLVideoSourceAdapter.baseURL(for: source) + "/", forHTTPHeaderField: "Referer")
        request.setValue("text/html,application/json;q=0.9,*/*;q=0.8", forHTTPHeaderField: "Accept")
        if source.id == "spankbang" {
            request.setValue("age_pass=1; pg_interstitial_v5=1; pg_pop_v5=1; player_quality=1080; preroll_skip=1; backend_version=main; videos_layout=four-col", forHTTPHeaderField: "Cookie")
            request.setValue("en-US,en;q=0.9", forHTTPHeaderField: "Accept-Language")
        }
        if source.id == "pornhub" || url.host?.contains("pornhub") == true {
            request.setValue("age_verified=1; has_access=1; accessAgeDisclaimerPH=1; platform=mobile; bs=000000000000000022522ffa8ac94dcd; cookieConsent=3;", forHTTPHeaderField: "Cookie")
            request.setValue("ja,en-US;q=0.8,en;q=0.6", forHTTPHeaderField: "Accept-Language")
        }
        let data = try await requestFeedData(for: request, source: source.name)

        if source.id == VideoSourceCatalog.eporner.id {
            return try UniversalVideoEngine.decodeEPORNERFeed(data: data, statusCode: 200, category: category)
        }
        guard let html = String(data: data, encoding: .utf8) else {
            throw VideoFeedError.malformedResponse(source: source.name)
        }
        let videos = HTMLVideoSourceAdapter.parseHTMLFeed(html, source: source, category: category, baseURL: HTMLVideoSourceAdapter.baseURL(for: source))
        guard !videos.isEmpty else {
            throw VideoFeedError.malformedResponse(source: source.name)
        }
        return videos
    }

    private func requestFeedData(for request: URLRequest, source: String) async throws -> Data {
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw VideoFeedError.invalidResponse(source: source)
        }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw VideoFeedError.httpStatus(source: source, statusCode: httpResponse.statusCode)
        }
        return data
    }

    // MARK: - 真实请求 91动漫 列表（实测 918ms 秒开）

    private func fetch91DongmanFeed(category: String = "全部", page: Int = 1) async throws -> [VideoItem] {
        let path: String
        switch category {
        case "连载番剧": path = "/anime/lifan/"
        case "剧场版", "3D动漫": path = "/anime/3d/"
        case "完结经典", "AI动漫": path = "/anime/ai/"
        case "里番特区": path = "/anime/lifan/"
        default: path = "/anime/"
        }

        let p = max(page, 1)
        let fullPath = p == 1 ? path : "\(path)page/\(p)/"
        guard let url = URL(string: "https://91dongman.net\(fullPath)") else {
            throw VideoFeedError.invalidURL(source: "91动漫")
        }
        var request = URLRequest(url: url)
        request.setValue("https://91dongman.net/", forHTTPHeaderField: "Referer")
        let data = try await requestFeedData(for: request, source: "91动漫")
        guard let html = String(data: data, encoding: .utf8) else {
            throw VideoFeedError.malformedResponse(source: "91动漫")
        }
        return parse91DongmanHtml(html, category: category)
    }

    nonisolated func parse91DongmanHtml(_ html: String, category: String) -> [VideoItem] {
        var videos: [VideoItem] = []

        // 匹配卡片: href="/anime/.../\d+/" 及内嵌的 img data-src / alt
        let pattern = #"<a[^>]+class="[^"]*dm-card__media[^"]*"[^>]+href="(/anime/[^/]+/(\d+)/)"[^>]*>([\s\S]*?)</a>"#
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return [] }
        let nsHtml = html as NSString
        let matches = regex.matches(in: html, options: [], range: NSRange(location: 0, length: nsHtml.length))

        var index = 0
        for m in matches {
            let relHref = nsHtml.substring(with: m.range(at: 1))
            let animeId = nsHtml.substring(with: m.range(at: 2))
            let innerHtml = nsHtml.substring(with: m.range(at: 3))

            // 提取封面图
            var coverUrl = ""
            if let srcMatch = innerHtml.range(of: #"data-src="([^"]+)""#, options: .regularExpression) {
                let raw = String(innerHtml[srcMatch])
                let clean = raw.replacingOccurrences(of: "data-src=\"", with: "").replacingOccurrences(of: "\"", with: "")
                if !clean.isEmpty { coverUrl = clean }
            }

            // 提取标题
            var title = ""
            if let altMatch = innerHtml.range(of: #"alt="([^"]+)""#, options: .regularExpression) {
                let raw = String(innerHtml[altMatch])
                let clean = raw.replacingOccurrences(of: "alt=\"", with: "").replacingOccurrences(of: "\"", with: "")
                if !clean.isEmpty { title = clean }
            }
            guard !title.isEmpty else { continue }

            let fullPageUrl = "https://91dongman.net\(relHref)"

            videos.append(VideoItem(
                id: "dm-\(animeId)",
                title: title,
                subtitle: "91动漫 · \(category)",
                coverUrl: coverUrl,
                playbackUrl: fullPageUrl,
                duration: "",
                rating: "",
                tags: ["91动漫", category],
                category: category,
                isTop10: false,
                rankNumber: nil,
                sourceName: "91动漫"
            ))

            index += 1
        }

        return videos
    }

    // MARK: - 91动漫 真实播放流解析（实测 400ms 获取真实 M3U8）

    private func cleanHlsUrl(_ raw: String) -> String {
        return raw
            .replacingOccurrences(of: "\\/", with: "/")
            .replacingOccurrences(of: "\\u002F", with: "/", options: .caseInsensitive)
            .replacingOccurrences(of: "\\u0026", with: "&", options: .caseInsensitive)
            .replacingOccurrences(of: "&amp;", with: "&")
    }

    public func resolve91DongmanStream(pageUrl: String, title: String) async throws -> PlaybackSourceResult {
        guard let url = URL(string: pageUrl) else {
            throw NSError(domain: "VideoRepository", code: 400, userInfo: [NSLocalizedDescriptionKey: "无效 91动漫 链接"])
        }

        var request = URLRequest(url: url)
        request.setValue("https://91dongman.net/", forHTTPHeaderField: "Referer")

        let (data, response) = try await session.data(for: request)
        guard let httpResp = response as? HTTPURLResponse, (200...299).contains(httpResp.statusCode),
              let html = String(data: data, encoding: .utf8) else {
            throw NSError(domain: "VideoRepository", code: 502, userInfo: [NSLocalizedDescriptionKey: "无法加载 91动漫 播放页"])
        }

        // 正则提取 data-video-url="..." 或 var url = "..."
        let pattern = #"data-video-url=['"]([^'"]+)['"]|var\s+url\s*=\s*['"]([^'"]+\.m3u8[^'"]*)['"]"#
        guard let regex = try? NSRegularExpression(pattern: pattern) else {
            throw NSError(domain: "VideoRepository", code: 500, userInfo: [NSLocalizedDescriptionKey: "解析引擎初始化失败"])
        }

        let nsHtml = html as NSString
        guard let match = regex.firstMatch(in: html, options: [], range: NSRange(location: 0, length: nsHtml.length)) else {
            throw NSError(domain: "VideoRepository", code: 404, userInfo: [NSLocalizedDescriptionKey: "未找到可播放的 91动漫 视频流"])
        }

        var rawM3u8 = ""
        if match.range(at: 1).location != NSNotFound {
            rawM3u8 = nsHtml.substring(with: match.range(at: 1))
        } else if match.range(at: 2).location != NSNotFound {
            rawM3u8 = nsHtml.substring(with: match.range(at: 2))
        }

        let cleanM3u8 = cleanHlsUrl(rawM3u8)
        guard let streamUrl = URL(string: cleanM3u8) else {
            throw NSError(domain: "VideoRepository", code: 500, userInfo: [NSLocalizedDescriptionKey: "解析到的直链 URL 无效"])
        }

        // 提取 CDN 鉴权头（x-cdn-auth 与 x-cdn-app）
        var headers: [String: String] = [
            "Referer": "https://91dongman.net/",
            "Origin": "https://91dongman.net"
        ]

        let authKeyPattern = #"data-cdn-auth-key=['"]([^'"]+)['"]"#
        let authValPattern = #"data-cdn-auth-value=['"]([^'"]+)['"]"#
        let appPattern = #"data-cdn-app=['"]([^'"]+)['"]"#

        let keyRegex = try? NSRegularExpression(pattern: authKeyPattern)
        let valRegex = try? NSRegularExpression(pattern: authValPattern)
        let appRegex = try? NSRegularExpression(pattern: appPattern)

        var authHeaderName = "x-cdn-auth"
        if let km = keyRegex?.firstMatch(in: html, range: NSRange(location: 0, length: nsHtml.length)) {
            authHeaderName = nsHtml.substring(with: km.range(at: 1))
        }
        if let vm = valRegex?.firstMatch(in: html, range: NSRange(location: 0, length: nsHtml.length)) {
            let authVal = nsHtml.substring(with: vm.range(at: 1))
            headers[authHeaderName] = authVal
        }
        if let am = appRegex?.firstMatch(in: html, range: NSRange(location: 0, length: nsHtml.length)) {
            let appVal = nsHtml.substring(with: am.range(at: 1))
            headers["x-cdn-app"] = appVal
        }

        return PlaybackSourceResult(url: streamUrl, headers: headers, referer: "https://91dongman.net/", title: title)
    }

    // MARK: - 真实请求 Beeg REST API 数据流（实测 974ms 秒开）

    private func fetchBeegFeed(category: String = "全部", page: Int = 1) async throws -> [VideoItem] {
        let tag: String
        switch category {
        case "精选": tag = "index"
        case "自拍": tag = "amateur"
        case "日本": tag = "japanese"
        case "亚洲": tag = "asian"
        case "欧美": tag = "milf"
        default: tag = "index"
        }

        let p = max(page, 1)
        let offset = (p - 1) * 20
        guard let url = URL(string: "https://store.externulls.com/tag/videos/\(tag)?limit=20&offset=\(offset)") else {
            throw VideoFeedError.invalidURL(source: "Beeg")
        }
        var request = URLRequest(url: url)
        request.setValue("https://beeg.com/", forHTTPHeaderField: "Referer")
        request.setValue("https://beeg.com", forHTTPHeaderField: "Origin")
        request.setValue("application/json, text/plain, */*", forHTTPHeaderField: "Accept")
        let data = try await requestFeedData(for: request, source: "Beeg")
        return try parseBeegFeed(data: data, category: category)
    }

    nonisolated func parseBeegFeed(data: Data, category: String) throws -> [VideoItem] {
        guard let jsonArray = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            throw VideoFeedError.malformedResponse(source: "Beeg")
        }

        return jsonArray.enumerated().compactMap { _, item in
            guard
                let file = item["file"] as? [String: Any],
                let fileIdNumber = file["id"] as? NSNumber,
                let dataList = file["data"] as? [[String: Any]],
                let title = dataList.first(where: { $0["cd_column"] as? String == "sf_name" })?["cd_value"] as? String,
                !title.isEmpty
            else {
                return nil
            }
            let fileId = fileIdNumber.stringValue

            let thumbOffset = (item["fc_facts"] as? [[String: Any]])?.first?["fc_thumbs"] as? [Int]
            let coverUrl = thumbOffset?.first.map { "https://thumbs.externulls.com/videos/\(fileId)/\($0).webp" } ?? ""
            let durationSeconds = file["fl_duration"] as? Int ?? 0
            let duration = durationSeconds > 0 ? String(format: "%02d:%02d", durationSeconds / 60, durationSeconds % 60) : ""

            return VideoItem(
                id: "beeg-\(fileId)",
                title: title,
                subtitle: "Beeg · \(category)",
                coverUrl: coverUrl,
                playbackUrl: "https://beeg.com/-0\(fileId)",
                duration: duration,
                rating: "",
                tags: ["Beeg", category],
                category: category,
                isTop10: false,
                rankNumber: nil,
                sourceName: "Beeg"
            )
        }
    }

    public func resolveBeegStream(fileId: String) async throws -> URL {
        let cacheKey = "beeg-\(fileId)"
        if let cached = await cache.get(key: cacheKey) {
            return cached
        }

        guard let url = URL(string: "https://store.externulls.com/video/play_url/\(fileId)") else {
            throw NSError(domain: "VideoRepository", code: 400, userInfo: [NSLocalizedDescriptionKey: "无效 Beeg 视频 ID"])
        }

        var req = URLRequest(url: url)
        req.setValue("https://beeg.com/", forHTTPHeaderField: "Referer")
        req.setValue("https://beeg.com", forHTTPHeaderField: "Origin")
        req.setValue("*/*", forHTTPHeaderField: "Accept")

        let (data, resp) = try await session.data(for: req)
        guard let httpResp = resp as? HTTPURLResponse, (200...299).contains(httpResp.statusCode),
              let playPath = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !playPath.isEmpty, !playPath.contains("error") else {
            throw NSError(domain: "VideoRepository", code: 502, userInfo: [NSLocalizedDescriptionKey: "Beeg 暂未返回有效播放流"])
        }

        let fullPlayUrlStr = "https://video.beeg.com/" + (playPath.starts(with: "/") ? String(playPath.dropFirst()) : playPath)
        guard let fullPlayUrl = URL(string: fullPlayUrlStr) else {
            throw NSError(domain: "VideoRepository", code: 500, userInfo: [NSLocalizedDescriptionKey: "无法构建 Beeg 播放流地址"])
        }

        // 关键增强：Beeg Master M3U8 包含 AV1 (av01) 与 HEVC，iOS VideoToolbox 硬件解码器无法解码 AV1 会触发 -11833 / -12906。
        // 解析 Master M3U8 并优先提取 100% 硬件兼容的 Apple H.264 (avc1) 720p/1080p 变体流。
        var masterReq = URLRequest(url: fullPlayUrl)
        masterReq.setValue("https://beeg.com/", forHTTPHeaderField: "Referer")
        masterReq.setValue("https://beeg.com", forHTTPHeaderField: "Origin")
        if let (masterData, masterResp) = try? await session.data(for: masterReq),
           let masterHttp = masterResp as? HTTPURLResponse, (200...299).contains(masterHttp.statusCode),
           let masterM3u8 = String(data: masterData, encoding: .utf8) {
            let lines = masterM3u8.split(separator: "\n").map(String.init)
            var chosenVariant: String?
            // 优先级：720p avc1 -> 1080p avc1 -> 480p avc1 -> 任一 avc1
            for target in ["720p", "1080p", "480p"] {
                for (idx, line) in lines.enumerated() {
                    if line.contains("avc1") && line.contains(target) && idx + 1 < lines.count {
                        chosenVariant = lines[idx + 1].trimmingCharacters(in: .whitespacesAndNewlines)
                        break
                    }
                }
                if chosenVariant != nil { break }
            }
            if chosenVariant == nil {
                for (idx, line) in lines.enumerated() {
                    if line.contains("avc1") && idx + 1 < lines.count {
                        chosenVariant = lines[idx + 1].trimmingCharacters(in: .whitespacesAndNewlines)
                        break
                    }
                }
            }
            if let variant = chosenVariant, !variant.isEmpty {
                let variantURLStr = variant.starts(with: "http") ? variant : ("https://video.beeg.com" + (variant.starts(with: "/") ? variant : "/\(variant)"))
                if let variantURL = URL(string: variantURLStr) {
                    await cache.set(key: cacheKey, url: variantURL)
                    return variantURL
                }
            }
        }

        await cache.set(key: cacheKey, url: fullPlayUrl)
        return fullPlayUrl
    }

    // MARK: - 黄果短剧 Edge 统一信封网关客户端（大陆直连、高速爽剧）

    private func executeHuangguoEdgeRequest(
        origin: String = "https://huangguoai.com",
        method: String,
        path: String,
        body: String = "",
        contentType: String = ""
    ) async throws -> (Data, HTTPURLResponse) {
        let cleanOrigin = origin.hasSuffix("/") ? String(origin.dropLast()) : origin
        guard let url = URL(string: "\(cleanOrigin)/api/edge/request") else {
            throw NSError(domain: "VideoRepository", code: 400, userInfo: [NSLocalizedDescriptionKey: "无效 Edge 网关地址"])
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("\(cleanOrigin)/", forHTTPHeaderField: "Referer")
        request.setValue(cleanOrigin, forHTTPHeaderField: "Origin")
        request.setValue(Self.USER_AGENT, forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 8

        let payload: [String: Any] = [
            "method": method,
            "path": path,
            "body": body,
            "content_type": contentType
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)

        let (data, response) = try await session.data(for: request)
        guard let httpResp = response as? HTTPURLResponse, (200...299).contains(httpResp.statusCode) else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? 500
            throw NSError(domain: "VideoRepository", code: code, userInfo: [NSLocalizedDescriptionKey: "黄果 Edge 请求失败 HTTP \(code)"])
        }
        return (data, httpResp)
    }

    private func fetchHuangguoFeed(category: String = "全部", page: Int = 1) async throws -> [VideoItem] {
        let p = max(page, 1)
        let edgePath: String
        switch category {
        case "推荐": edgePath = "/api/ranks/hot"
        case "最新": edgePath = "/api/videos?page=\(p)&size=20"
        case "AI漫剧": edgePath = "/api/videos/category/ai-manju?page=\(p)&size=20"
        case "AI换脸": edgePath = "/api/videos/category/ai-huanlian?page=\(p)&size=20"
        default: edgePath = "/api/videos?page=\(p)&size=20"
        }

        // 1. 优先走 Edge 网关（纯净 JSON，耗时 300ms，无臃肿 HTML）
        if let (data, _) = try? await executeHuangguoEdgeRequest(origin: "https://huangguoai.com", method: "GET", path: edgePath),
           let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let dataDict = root["data"] as? [String: Any],
           let itemsArray = dataDict["items"] as? [[String: Any]], !itemsArray.isEmpty {

            var videos: [VideoItem] = []
            for item in itemsArray {
                let idNum = (item["video_id"] as? Int) ?? (item["id"] as? Int) ?? 0
                guard idNum > 0 else { continue }
                let dramaId = String(idNum)
                let title = (item["title"] as? String) ?? "黄果短剧"
                let cover = (item["cover"] as? String) ?? ""
                let desc = (item["description"] as? String) ?? ""
                let totalEp = (item["episode_count"] as? Int) ?? 0
                let epText = totalEp > 0 ? "全 \(totalEp) 集" : "短剧"
                let duration = (item["duration"] as? String) ?? ""

                videos.append(VideoItem(
                    id: "hg-\(dramaId)",
                    title: title,
                    subtitle: desc.isEmpty ? "黄果短剧 · \(epText)" : desc,
                    coverUrl: cover,
                    playbackUrl: "https://huangguoai.com/video/\(dramaId)/",
                    duration: duration,
                    rating: String(format: "%.1f", (item["score"] as? Double) ?? 9.5),
                    tags: (item["tags"] as? [String]) ?? ["短剧", category],
                    category: category,
                    isTop10: false,
                    rankNumber: item["rank"] as? Int,
                    sourceName: "黄果短剧"
                ))
            }
            if !videos.isEmpty {
                return videos
            }
        }

        // 2. 兜底回退：HTML 页面解析
        let path: String
        switch category {
        case "推荐": path = "/recommend"
        case "最新": path = "/newest"
        case "AI漫剧": path = "/ai-manju/"
        case "AI换脸": path = "/ai-huanlian/"
        default: path = "/recommend"
        }

        let fullPath: String
        if p == 1 {
            fullPath = path
        } else {
            fullPath = path.hasSuffix("/") ? "\(path)\(p)/" : "\(path)/\(p)/"
        }
        guard let url = URL(string: "https://huangguoai.com\(fullPath)") else {
            throw VideoFeedError.invalidURL(source: "黄果短剧")
        }
        var request = URLRequest(url: url)
        request.setValue("https://huangguoai.com/", forHTTPHeaderField: "Referer")
        let data = try await requestFeedData(for: request, source: "黄果短剧")
        guard let html = String(data: data, encoding: .utf8) else {
            throw VideoFeedError.malformedResponse(source: "黄果短剧")
        }
        return parseHuangguoHtml(html, category: category)
    }

    nonisolated func parseHuangguoHtml(_ html: String, category: String) -> [VideoItem] {
        var videos: [VideoItem] = []
        let cards = html.components(separatedBy: "class=\"hg-drama-card\"").dropFirst()

        var index = 0
        for card in cards {
            // 提取播放页链接 href="/video/xxx/"
            var href = ""
            if let m = card.range(of: #"href="(/video/\d+/|/detail/\d+/)"#, options: .regularExpression) {
                let raw = String(card[m])
                href = raw.replacingOccurrences(of: "href=\"", with: "")
            }
            guard !href.isEmpty else { continue }

            // 提取短剧 ID
            let dramaIdDigits = href.components(separatedBy: CharacterSet.decimalDigits.inverted).joined()
            let dramaId = dramaIdDigits.isEmpty ? "\(index)" : dramaIdDigits

            // 提取真实封面图：优先 data-src，其次 src
            var coverUrl = ""
            if let dataSrcRange = card.range(of: #"data-src="([^"]+)""#, options: .regularExpression) {
                let raw = String(card[dataSrcRange])
                let clean = raw.replacingOccurrences(of: "data-src=\"", with: "").replacingOccurrences(of: "\"", with: "")
                if !clean.isEmpty && !clean.contains("placeholder") {
                    coverUrl = clean
                }
            } else if let srcRange = card.range(of: #"src="([^"]+)""#, options: .regularExpression) {
                let raw = String(card[srcRange])
                let clean = raw.replacingOccurrences(of: "src=\"", with: "").replacingOccurrences(of: "\"", with: "")
                if !clean.isEmpty && !clean.contains("placeholder") {
                    coverUrl = clean
                }
            }

            // 提取标题：优先 alt 或 title
            var title = ""
            if let altRange = card.range(of: #"alt="([^"]+)""#, options: .regularExpression) {
                let raw = String(card[altRange])
                let clean = raw.replacingOccurrences(of: "alt=\"", with: "").replacingOccurrences(of: "\"", with: "")
                if !clean.isEmpty { title = clean }
            } else if let titleRange = card.range(of: #"title="([^"]+)""#, options: .regularExpression) {
                let raw = String(card[titleRange])
                let clean = raw.replacingOccurrences(of: "title=\"", with: "").replacingOccurrences(of: "\"", with: "")
                if !clean.isEmpty { title = clean }
            }
            guard !title.isEmpty else { continue }

            let fullPlayUrl = "https://huangguoai.com/video/\(dramaId)/"

            videos.append(VideoItem(
                id: "hg-\(dramaId)",
                title: title,
                subtitle: "黄果短剧 · \(category) · 连载完结",
                coverUrl: coverUrl,
                playbackUrl: fullPlayUrl,
                duration: "",
                rating: "",
                tags: ["短剧", "黄果", category],
                category: category,
                isTop10: false,
                rankNumber: nil,
                sourceName: "黄果短剧"
            ))

            index += 1
        }

        if category == "AI漫剧" {
            let matched = videos.filter { $0.title.contains("漫") || $0.title.contains("动漫") || $0.title.contains("二次元") || $0.title.contains("画") || $0.title.contains("AI漫") }
            let unmatched = videos.filter { !matched.contains($0) }
            return matched.isEmpty ? videos : matched + unmatched
        } else if category == "AI换脸" {
            let matched = videos.filter { $0.title.contains("换脸") || $0.title.contains("明星") || $0.title.contains("AI换脸") || $0.title.contains("脸") }
            let unmatched = videos.filter { !matched.contains($0) }
            return matched.isEmpty ? videos : matched + unmatched
        }

        return videos
    }

    private func huangguoOrigin(for pageUrl: URL) -> (origin: String, host: String) {
        let host = pageUrl.host?.lowercased() ?? "huangguoai.com"
        let scheme = pageUrl.scheme ?? "https"
        return ("\(scheme)://\(host)", host)
    }

    public func resolveHuangguoStream(pageUrl: String, title: String) async throws -> PlaybackSourceResult {
        guard let url = URL(string: pageUrl) else {
            throw NSError(domain: "VideoRepository", code: 400, userInfo: [NSLocalizedDescriptionKey: "无效黄果短剧链接"])
        }
        // 以实际播放页的 origin 为准，兼容站点迁移到镜像域名后的旧链接。
        let originInfo = huangguoOrigin(for: url)
        let origin = originInfo.origin

        // 1. 提取短剧 ID 与集数（兼容 /video/10586/、/video/10586/ep-2/、/detail/10586/、?ep=2）
        var dramaId: String? = nil
        var episodeNum: Int = 1

        if let epMatch = pageUrl.range(of: #"/ep-(\d+)"#, options: .regularExpression) {
            let epStr = String(pageUrl[epMatch]).replacingOccurrences(of: "/ep-", with: "").replacingOccurrences(of: "/", with: "")
            if let parsedEp = Int(epStr) {
                episodeNum = parsedEp
            }
        } else if let queryMatch = pageUrl.range(of: #"[?&]ep=(\d+)"#, options: .regularExpression) {
            let queryStr = String(pageUrl[queryMatch])
            if let val = queryStr.components(separatedBy: "=").last, let parsedEp = Int(val) {
                episodeNum = parsedEp
            }
        }

        if let match = pageUrl.range(of: #"/(?:video|detail|play)/(\d+)"#, options: .regularExpression) {
            let segment = String(pageUrl[match])
            dramaId = segment.components(separatedBy: "/").filter { !$0.isEmpty }.last
        }

        // 2. 优先请求黄果 Edge 信封网关（实测 330ms 秒级纯净直链）
        if let id = dramaId {
            let edgePath = "/api/videos/\(id)/play?ep=\(episodeNum)&page_texts=0"
            if let (data, _) = try? await executeHuangguoEdgeRequest(origin: origin, method: "GET", path: edgePath),
               let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let dataDict = root["data"] as? [String: Any] {

                let streamUrlStr = ((dataDict["video_url"] as? String)?.isEmpty == false ? dataDict["video_url"] as? String : nil)
                    ?? ((dataDict["video_url_hevc"] as? String)?.isEmpty == false ? dataDict["video_url_hevc"] as? String : nil)

                if let rawStream = streamUrlStr, let streamUrl = URL(string: cleanHlsUrl(rawStream)) {
                    let headers: [String: String] = [
                        "Referer": "\(origin)/",
                        "Origin": origin,
                        "User-Agent": Self.USER_AGENT
                    ]
                    return PlaybackSourceResult(url: streamUrl, headers: headers, referer: "\(origin)/", title: title)
                }
            }

            // 备选接口：POST /api/videos/{id}/playback 或 /api/videos/{id}/episodes/{ep}/playback
            let apiPath = episodeNum > 1
                ? "\(origin)/api/videos/\(id)/episodes/\(episodeNum)/playback"
                : "\(origin)/api/videos/\(id)/playback"

            if let apiUrl = URL(string: apiPath) {
                var apiReq = URLRequest(url: apiUrl)
                apiReq.httpMethod = "POST"
                apiReq.setValue("application/json", forHTTPHeaderField: "Content-Type")
                apiReq.setValue(pageUrl, forHTTPHeaderField: "Referer")
                apiReq.setValue(origin, forHTTPHeaderField: "Origin")
                apiReq.setValue(Self.USER_AGENT, forHTTPHeaderField: "User-Agent")
                apiReq.httpBody = "{}".data(using: .utf8)
                apiReq.timeoutInterval = 10

                if let (data, response) = try? await session.data(for: apiReq),
                   let httpResp = response as? HTTPURLResponse, (200...299).contains(httpResp.statusCode),
                   let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let dataDict = json["data"] as? [String: Any] {

                    var streamUrlStr: String? = nil
                    if let streams = dataDict["streams"] as? [String: Any] {
                        if let main = streams["main"] as? String, !main.isEmpty {
                            streamUrlStr = main
                        } else if let preview = streams["preview"] as? String, !preview.isEmpty {
                            streamUrlStr = preview
                        } else if let hevc = streams["hevc"] as? String, !hevc.isEmpty {
                            streamUrlStr = hevc
                        }
                    }

                    if let rawStream = streamUrlStr {
                        let cleanUrlStr = cleanHlsUrl(rawStream)
                        if let streamUrl = URL(string: cleanUrlStr) {
                            var headers: [String: String] = [
                                "Referer": "\(origin)/",
                                "Origin": origin,
                                "User-Agent": Self.USER_AGENT
                            ]
                            if let cdn = dataDict["cdn"] as? [String: Any] {
                                if let authKey = cdn["authKey"] as? String, !authKey.isEmpty,
                                   let authValue = cdn["authValue"] as? String, !authValue.isEmpty {
                                    headers[authKey] = authValue
                                }
                                if let app = cdn["app"] as? String, !app.isEmpty {
                                    headers["x-cdn-app"] = app
                                }
                            }
                            return PlaybackSourceResult(url: streamUrl, headers: headers, referer: "\(origin)/", title: title)
                        }
                    }
                }
            }
        }

        // 3. 兜底策略：请求 HTML 页面进行解析（兼容静态/旧版页面及测试桩）
        var request = URLRequest(url: url)
        request.setValue("\(origin)/", forHTTPHeaderField: "Referer")
        request.setValue(origin, forHTTPHeaderField: "Origin")
        request.setValue(Self.USER_AGENT, forHTTPHeaderField: "User-Agent")

        let (data, response) = try await session.data(for: request)
        guard let httpResp = response as? HTTPURLResponse, (200...299).contains(httpResp.statusCode),
              let html = String(data: data, encoding: .utf8) else {
            throw NSError(domain: "VideoRepository", code: 502, userInfo: [NSLocalizedDescriptionKey: "无法加载黄果短剧播放页"])
        }

        // 3.1 尝试解析 script#videoInitialData JSON
        if let initRange = html.range(of: #"id=["']videoInitialData["'][^>]*>(.*?)</script>"#, options: .regularExpression) {
            let scriptContent = String(html[initRange])
            if let jsonStart = scriptContent.firstIndex(of: ">"),
               let jsonEnd = scriptContent.lastIndex(of: "<") {
                let jsonStr = String(scriptContent[scriptContent.index(after: jsonStart)..<jsonEnd])
                if let jsonData = jsonStr.data(using: .utf8),
                   let jsonObj = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any] {
                    var candidate: String? = nil
                    if let epPlaySrcs = jsonObj["epPlaySrcs"] as? [String: String],
                       let epUrl = epPlaySrcs[String(episodeNum)], !epUrl.isEmpty {
                        candidate = epUrl
                    } else if let vSrc = jsonObj["videoSrc"] as? String, !vSrc.isEmpty {
                        candidate = vSrc
                    } else if let pSrc = jsonObj["previewSrc"] as? String, !pSrc.isEmpty {
                        candidate = pSrc
                    }
                    if let cand = candidate, let sUrl = URL(string: cleanHlsUrl(cand)) {
                        let headers: [String: String] = [
                            "Referer": "\(origin)/",
                            "Origin": origin
                        ]
                        return PlaybackSourceResult(url: sUrl, headers: headers, referer: "\(origin)/", title: title)
                    }
                }
            }
        }

        // 3.2 正则匹配 M3U8 流地址
        let pattern = #"https?://[^"\s<>]+?\.m3u8[^"\s<>]*"#
        guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive) else {
            throw NSError(domain: "VideoRepository", code: 500, userInfo: [NSLocalizedDescriptionKey: "解析引擎初始化失败"])
        }

        let nsHtml = html as NSString
        guard let match = regex.firstMatch(in: html, options: [], range: NSRange(location: 0, length: nsHtml.length)) else {
            throw NSError(domain: "VideoRepository", code: 404, userInfo: [NSLocalizedDescriptionKey: "未找到黄果短剧播放流"])
        }

        let rawM3u8 = nsHtml.substring(with: match.range)
        let cleanUrlStr = cleanHlsUrl(rawM3u8)
        guard let streamUrl = URL(string: cleanUrlStr) else {
            throw NSError(domain: "VideoRepository", code: 500, userInfo: [NSLocalizedDescriptionKey: "解析出的 M3U8 地址无效"])
        }

        let headers: [String: String] = [
            "Referer": "\(origin)/",
            "Origin": origin
        ]
        return PlaybackSourceResult(url: streamUrl, headers: headers, referer: "\(origin)/", title: title)
    }

    // MARK: - 真实请求 91视频（经典原创视界）

    private func fetch91PornFeed(category: String = "全部", page: Int = 1) async throws -> [VideoItem] {
        let catParam: String
        switch category {
        case "当前最热": catParam = "hot"
        case "非付费": catParam = "nonpaid"
        case "最近加精": catParam = "rf"
        case "高清": catParam = "hd"
        default: catParam = "hot"
        }

        let p = max(page, 1)
        guard let url = URL(string: "https://h1014.sol148.com/v.php?category=\(catParam)&viewtype=basic&page=\(p)") else {
            throw VideoFeedError.invalidURL(source: "91视频")
        }
        var request = URLRequest(url: url)
        request.setValue("https://h1014.sol148.com/", forHTTPHeaderField: "Referer")
        let data = try await requestFeedData(for: request, source: "91视频")
        guard let html = String(data: data, encoding: .utf8) else {
            throw VideoFeedError.malformedResponse(source: "91视频")
        }
        return parse91PornHtml(html, category: category)
    }

    nonisolated func parse91PornHtml(_ html: String, category: String) -> [VideoItem] {
        var videos: [VideoItem] = []
        let wells = html.components(separatedBy: "class=\"well well-sm").dropFirst()

        var index = 0
        for well in wells {
            // 提取 viewkey
            var viewkey = ""
            if let vkRange = well.range(of: #"viewkey=([0-9a-fA-F]+)"#, options: .regularExpression) {
                let raw = String(well[vkRange])
                viewkey = raw.replacingOccurrences(of: "viewkey=", with: "")
            }
            guard !viewkey.isEmpty else { continue }

            // 提取封面缩略图
            var coverUrl = ""
            if let srcRange = well.range(of: #"src="([^"]+thumb/[^"]+)""#, options: .regularExpression) {
                let raw = String(well[srcRange])
                let clean = raw.replacingOccurrences(of: "src=\"", with: "").replacingOccurrences(of: "\"", with: "")
                if !clean.isEmpty { coverUrl = clean }
            } else if let srcRange = well.range(of: #"src="([^"]+)""#, options: .regularExpression) {
                let raw = String(well[srcRange])
                let clean = raw.replacingOccurrences(of: "src=\"", with: "").replacingOccurrences(of: "\"", with: "")
                if !clean.isEmpty && !clean.contains("icon") && !clean.contains("logo") { coverUrl = clean }
            }

            // 提取时长
            var duration = ""
            if let durRange = well.range(of: #"class="duration">([^<]+)</span>"#, options: .regularExpression) {
                let raw = String(well[durRange])
                duration = raw.replacingOccurrences(of: #"class="duration">"#, with: "", options: .regularExpression)
                              .replacingOccurrences(of: "</span>", with: "")
                              .trimmingCharacters(in: .whitespacesAndNewlines)
            }

            // 提取标题
            var title = ""
            if let titleRange = well.range(of: #"class="video-title[^"]*">([^<]+)</span>"#, options: .regularExpression) {
                let raw = String(well[titleRange])
                let clean = raw.replacingOccurrences(of: #"class="video-title[^"]*">"#, with: "", options: .regularExpression)
                               .replacingOccurrences(of: "</span>", with: "")
                               .trimmingCharacters(in: .whitespacesAndNewlines)
                if !clean.isEmpty { title = clean }
            }
            guard !title.isEmpty else { continue }

            let fullPageUrl = "https://h1014.sol148.com/view_video.php?viewkey=\(viewkey)"

            videos.append(VideoItem(
                id: "91p-\(viewkey)",
                title: title,
                subtitle: "91视频 · \(category) · 热门经典",
                coverUrl: coverUrl,
                playbackUrl: fullPageUrl,
                duration: duration,
                rating: "",
                tags: ["91原创", "经典", category],
                category: category,
                isTop10: false,
                rankNumber: nil,
                sourceName: "91视频"
            ))

            index += 1
        }

        if category == "AI漫剧" {
            let matched = videos.filter { $0.title.contains("漫") || $0.title.contains("动漫") || $0.title.contains("二次元") || $0.title.contains("画") || $0.title.contains("AI漫") }
            let unmatched = videos.filter { !matched.contains($0) }
            return matched.isEmpty ? videos : matched + unmatched
        } else if category == "AI换脸" {
            let matched = videos.filter { $0.title.contains("换脸") || $0.title.contains("明星") || $0.title.contains("AI换脸") || $0.title.contains("脸") }
            let unmatched = videos.filter { !matched.contains($0) }
            return matched.isEmpty ? videos : matched + unmatched
        }

        return videos
    }

    public static func unescapeJavaScriptString(_ value: String) -> String {
        guard let regex = try? NSRegularExpression(pattern: #"%u([0-9a-fA-F]{4})|%([0-9a-fA-F]{2})"#) else {
            return value.removingPercentEncoding ?? value
        }
        let nsValue = value as NSString
        var result = ""
        var lastIndex = 0
        let matches = regex.matches(in: value, range: NSRange(location: 0, length: nsValue.length))
        for match in matches {
            if match.range.location > lastIndex {
                result += nsValue.substring(with: NSRange(location: lastIndex, length: match.range.location - lastIndex))
            }
            if match.range(at: 1).location != NSNotFound {
                let hex = nsValue.substring(with: match.range(at: 1))
                if let code = UInt32(hex, radix: 16), let scalar = UnicodeScalar(code) {
                    result.append(Character(scalar))
                }
            } else if match.range(at: 2).location != NSNotFound {
                let hex = nsValue.substring(with: match.range(at: 2))
                if let code = UInt32(hex, radix: 16), let scalar = UnicodeScalar(code) {
                    result.append(Character(scalar))
                }
            }
            lastIndex = match.range.location + match.range.length
        }
        if lastIndex < nsValue.length {
            result += nsValue.substring(with: NSRange(location: lastIndex, length: nsValue.length - lastIndex))
        }
        return result
    }

    public func resolve91PornStream(pageUrl: String, title: String) async throws -> PlaybackSourceResult {
        guard let url = URL(string: pageUrl) else {
            throw NSError(domain: "VideoRepository", code: 400, userInfo: [NSLocalizedDescriptionKey: "无效 91视频 链接"])
        }

        var request = URLRequest(url: url)
        request.setValue("https://h1014.sol148.com/", forHTTPHeaderField: "Referer")
        request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")

        let (data, response) = try await session.data(for: request)
        guard let httpResp = response as? HTTPURLResponse, (200...299).contains(httpResp.statusCode),
              let html = String(data: data, encoding: .utf8) else {
            throw NSError(domain: "VideoRepository", code: 502, userInfo: [NSLocalizedDescriptionKey: "无法加载 91视频 播放页"])
        }

        let headers: [String: String] = [
            "Referer": "https://h1014.sol148.com/",
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile/15E148 Safari/604.1"
        ]

        let nsHtml = html as NSString
        let fullRange = NSRange(location: 0, length: nsHtml.length)

        // 1. 优先尝试从 strencode2(...) 中解密官方真实 CDN77 直链
        if let strencodeRegex = try? NSRegularExpression(pattern: #"strencode2\(\s*["']([^"']+)["']\s*\)"#),
           let strencodeMatch = strencodeRegex.firstMatch(in: html, options: [], range: fullRange),
           strencodeMatch.numberOfRanges > 1 {
            let encodedPayload = nsHtml.substring(with: strencodeMatch.range(at: 1))
            let decodedSnippet = Self.unescapeJavaScriptString(encodedPayload)
            let sourcePattern = #"src=['"]([^'"]+)['"]"#
            if let srcRegex = try? NSRegularExpression(pattern: sourcePattern),
               let srcMatch = srcRegex.firstMatch(in: decodedSnippet, options: [], range: NSRange(location: 0, length: (decodedSnippet as NSString).length)),
               srcMatch.numberOfRanges > 1 {
                let streamCandidate = (decodedSnippet as NSString).substring(with: srcMatch.range(at: 1))
                if let streamURL = URL(string: cleanHlsUrl(streamCandidate)),
                   ["http", "https"].contains(streamURL.scheme?.lowercased() ?? ""),
                   let host = streamURL.host?.lowercased(), !host.contains("91p52.com") {
                    return PlaybackSourceResult(url: streamURL, headers: headers, referer: "https://h1014.sol148.com/", title: title)
                }
            }
        }

        // 2. 兜底尝试正则提取静态 <source src="..." 或 data-video-url（排除阻断主机 91p52.com）
        let pattern = #"<source\s+src="([^"]+)""#
        if let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive) {
            let matches = regex.matches(in: html, options: [], range: fullRange)
            for match in matches where match.numberOfRanges > 1 {
                let rawStream = nsHtml.substring(with: match.range(at: 1))
                let cleanStream = cleanHlsUrl(rawStream)
                if let streamUrl = URL(string: cleanStream),
                   let host = streamUrl.host?.lowercased(),
                   !host.contains("91p52.com") {
                    return PlaybackSourceResult(url: streamUrl, headers: headers, referer: "https://h1014.sol148.com/", title: title)
                }
            }
        }

        throw NSError(domain: "VideoRepository", code: 404, userInfo: [NSLocalizedDescriptionKey: "未找到有效的 91视频 播放流"])
    }

    // MARK: - 真实请求华视影院

    private func fetchRealHuavodFeed(category: String = "全部", page: Int = 1) async throws -> [VideoItem] {
        let typeId: String
        switch category {
        case "电视剧": typeId = "2"
        case "综艺": typeId = "3"
        case "动漫": typeId = "4"
        case "短剧": typeId = "20"
        default: typeId = "1"
        }
        let p = max(page, 1)
        guard let url = URL(string: "https://huavod.com/vodshow/\(typeId)/page/\(p).html") else {
            throw VideoFeedError.invalidURL(source: "华视影院")
        }
        var request = URLRequest(url: url)
        request.setValue("https://huavod.com", forHTTPHeaderField: "Referer")
        let data = try await requestFeedData(for: request, source: "华视影院")
        guard let html = String(data: data, encoding: .utf8) else {
            throw VideoFeedError.malformedResponse(source: "华视影院")
        }
        return parseHuavodHtml(html, category: category)
    }

    nonisolated func parseHuavodHtml(_ html: String, category: String) -> [VideoItem] {
        var videos: [VideoItem] = []
        let rawCards: [String]
        if html.contains("public-list-box") {
            rawCards = Array(html.components(separatedBy: "public-list-box").dropFirst())
        } else if html.contains("public-list-exp") {
            rawCards = Array(html.components(separatedBy: "public-list-exp").dropFirst().map { "class=\"public-list-exp\"" + $0 })
        } else {
            rawCards = []
        }
        guard !rawCards.isEmpty else { return [] }

        for card in rawCards {
            guard let idMatch = card.range(of: #"href=["'][^"']*/voddetail/(\d+)\.html["']"#, options: .regularExpression) else {
                continue
            }
            let hrefString = String(card[idMatch])
            guard let digits = hrefString.components(separatedBy: CharacterSet.decimalDigits.inverted).filter({ !$0.isEmpty }).last else {
                continue
            }
            let vodId = digits

            var title = "视频 \(vodId)"
            if let titleRange = card.range(of: #"title=["']([^"']+)["']"#, options: .regularExpression) {
                let rawTitleAttr = String(card[titleRange])
                if let cleaned = rawTitleAttr.split(separator: "\"").dropFirst().first ?? rawTitleAttr.split(separator: "'").dropFirst().first {
                    title = String(cleaned).trimmingCharacters(in: .whitespacesAndNewlines)
                }
            } else if let timeTitleRange = card.range(of: #"class=["'][^"']*time-title[^"']*["'][^>]*>([^<]+)<"#, options: .regularExpression) {
                let tagStr = String(card[timeTitleRange])
                if let inside = tagStr.split(separator: ">").last?.split(separator: "<").first {
                    title = String(inside).trimmingCharacters(in: .whitespacesAndNewlines)
                }
            }

            var coverUrl = ""
            if let imgTagRange = card.range(of: #"<img[^>]+>"#, options: .regularExpression) {
                let imgTag = String(card[imgTagRange])
                let srcPatterns = [
                    #"data-src=["']([^"']+)["']"#,
                    #"data-original=["']([^"']+)["']"#,
                    #"src=["']([^"']+)["']"#
                ]
                for pattern in srcPatterns {
                    if let srcRange = imgTag.range(of: pattern, options: .regularExpression) {
                        let matched = String(imgTag[srcRange])
                        if let urlPart = matched.split(separator: "\"").dropFirst().first ?? matched.split(separator: "'").dropFirst().first {
                            let s = String(urlPart).trimmingCharacters(in: .whitespacesAndNewlines)
                            if !s.hasPrefix("data:image") && !s.isEmpty {
                                if s.hasPrefix("//") {
                                    coverUrl = "https:" + s
                                } else if s.hasPrefix("http") {
                                    coverUrl = s
                                } else if s.hasPrefix("/") {
                                    coverUrl = "https://huavod.com" + s
                                } else {
                                    coverUrl = s
                                }
                                break
                            }
                        }
                    }
                }
            }

            // 华视死链与缺失封面智能替换为官方高清豆瓣海报
            if coverUrl.isEmpty || coverUrl.contains("dbzy.tv") || coverUrl.contains("dbzy5.com") {
                if let direct = DoubanPosterResolver.shared.quickLookup(for: title) {
                    coverUrl = direct
                }
            }

            var rating = ""
            if let rateRange = card.range(of: #"class=["'][^"']*ft4[^"']*["'][^>]*>([^<]+)<"#, options: .regularExpression) {
                let tagStr = String(card[rateRange])
                if let inside = tagStr.split(separator: ">").last?.split(separator: "<").first {
                    rating = String(inside).trimmingCharacters(in: .whitespacesAndNewlines)
                }
            }

            let playbackPageUrl = "https://huavod.com/vodplay/\(vodId)-1-1.html"

            videos.append(VideoItem(
                id: "hua-\(vodId)",
                title: title,
                subtitle: "华视影院 · \(category)",
                coverUrl: coverUrl,
                playbackUrl: playbackPageUrl,
                duration: "",
                rating: rating,
                tags: ["华视影院", category],
                category: category,
                isTop10: false,
                rankNumber: nil,
                sourceName: "华视影院"
            ))
        }
        return videos
    }

    // MARK: - 核心播放流分发引擎（杜绝一切假片，返回真实 M3U8 流与 Referer）

    public func resolvePlayback(video: VideoItem) async throws -> PlaybackSourceResult {
        try await VideoPreloadManager.shared.resolve(video: video)
    }

    /// 底层真实流媒体直链与鉴权头解析引擎（绝不自我 await inFlight，杜绝自身死锁）
    public func executeStreamResolution(video: VideoItem) async throws -> PlaybackSourceResult {
        let sourceID = Self.playbackSourceID(for: video)
        let result: PlaybackSourceResult

        // 1. 91动漫（实测 400ms 获取真实直链与 CDN 鉴权头）
        if sourceID == VideoSourceCatalog.dongman91.id {
            result = try await resolve91DongmanStream(pageUrl: video.playbackUrl, title: video.title)
        } else if sourceID == VideoSourceCatalog.huangguo.id {
            // 2. 黄果短剧（实测提取真实 M3U8 直播流与鉴权参数）
            result = try await resolveHuangguoStream(pageUrl: video.playbackUrl, title: video.title)
        } else if sourceID == VideoSourceCatalog.porn91.id {
            // 3. 91视频（提取真实原画 MP4 直链）
            result = try await resolve91PornStream(pageUrl: video.playbackUrl, title: video.title)
        } else if sourceID == VideoSourceCatalog.huavod.id {
            result = try await resolveHuavodStream(pageUrl: video.playbackUrl, title: video.title)
        } else {
            result = try await resolveOtherVideoStreams(sourceID: sourceID, video: video)
        }

        return result
    }

    private func resolveOtherVideoStreams(sourceID: String?, video: VideoItem) async throws -> PlaybackSourceResult {

        // 4. Beeg 官方极简流媒体（支持多格式 ID 提取与真实 Master M3U8 解析）
        if sourceID == VideoSourceCatalog.beeg.id {
            let numericID: String
            if let regex = try? NSRegularExpression(pattern: #"(?:beeg\.com/(?:-0?|video/)?|/|^)(\d+)"#),
               let match = regex.firstMatch(in: video.playbackUrl, range: NSRange(location: 0, length: (video.playbackUrl as NSString).length)),
               match.numberOfRanges > 1,
               match.range(at: 1).location != NSNotFound {
                numericID = (video.playbackUrl as NSString).substring(with: match.range(at: 1))
            } else if let id = video.id.split(separator: "-").last.map(String.init), id.allSatisfy(\.isNumber) {
                numericID = id
            } else {
                throw VideoFeedError.invalidURL(source: "Beeg")
            }
            guard !numericID.isEmpty else {
                throw VideoFeedError.invalidURL(source: "Beeg")
            }
            let streamURL = try await resolveBeegStream(fileId: numericID)
            return PlaybackSourceResult(url: streamURL, referer: "https://beeg.com/", title: video.title)
        }

        // 5. XVideos 真实 HLS / MP4 播放流
        if sourceID == VideoSourceCatalog.xvideos.id {
            return try await resolveXVideosStream(pageUrl: video.playbackUrl, title: video.title)
        }

        if sourceID == VideoSourceCatalog.eporner.id {
            return try await resolveHTMLSourceStream(pageUrl: video.playbackUrl, source: VideoSourceCatalog.eporner, title: video.title)
        }

        if sourceID == VideoSourceCatalog.javsb.id {
            return try await resolveJavSbStream(pageUrl: video.playbackUrl, title: video.title)
        }

        if let source = VideoSourceCatalog.allSources.first(where: { $0.id == sourceID }),
           VideoSourceCatalog.htmlAdapterSourceIDs.contains(source.id) {
            return try await resolveHTMLSourceStream(pageUrl: video.playbackUrl, source: source, title: video.title)
        }

        // 6. 直接流媒体地址
        if let directUrl = URL(string: video.playbackUrl),
           ["http", "https"].contains(directUrl.scheme?.lowercased() ?? "") {
            let path = directUrl.path.lowercased()
            if path.hasSuffix(".m3u8") || path.hasSuffix(".mp4") {
                return PlaybackSourceResult(url: directUrl, referer: nil, title: video.title)
            }
        }

        throw VideoFeedError.unsupportedSource(source: video.sourceName)
    }

    static func playbackSourceID(for video: VideoItem) -> String? {
        if let namedSource = VideoSourceCatalog.allSources.first(where: { $0.name == video.sourceName }) {
            return namedSource.id
        }
        guard let host = URL(string: video.playbackUrl)?.host?.lowercased() else { return nil }
        return VideoSourceCatalog.allSources.first { source in
            guard let sourceHost = URL(string: source.baseUrl)?.host?.lowercased() else { return false }
            let candidate = host.hasPrefix("www.") ? String(host.dropFirst(4)) : host
            let expected = sourceHost.hasPrefix("www.") ? String(sourceHost.dropFirst(4)) : sourceHost
            return candidate == expected || (source.id == "pornhub" && candidate.hasSuffix(".pornhub.com"))
        }?.id
    }

    private func resolveHTMLSourceStream(
        pageUrl: String,
        source: VideoSourceDefinition,
        title: String,
        referer: String? = nil,
        depth: Int = 0
    ) async throws -> PlaybackSourceResult {
        guard let pageURL = URL(string: pageUrl) else {
            throw VideoFeedError.invalidURL(source: source.name)
        }
        var request = URLRequest(url: pageURL)
        request.setValue(referer ?? HTMLVideoSourceAdapter.baseURL(for: source) + "/", forHTTPHeaderField: "Referer")
        request.setValue("text/html,application/xhtml+xml;q=0.9,*/*;q=0.8", forHTTPHeaderField: "Accept")
        if source.id == "spankbang" {
            request.setValue("age_pass=1; pg_interstitial_v5=1; pg_pop_v5=1; player_quality=1080; preroll_skip=1; backend_version=main; videos_layout=four-col", forHTTPHeaderField: "Cookie")
            request.setValue("en-US,en;q=0.9", forHTTPHeaderField: "Accept-Language")
        }
        if source.id == "pornhub" || pageURL.host?.contains("pornhub") == true {
            request.setValue("age_verified=1; has_access=1; accessAgeDisclaimerPH=1; platform=mobile; bs=000000000000000022522ffa8ac94dcd; cookieConsent=3;", forHTTPHeaderField: "Cookie")
            request.setValue("ja,en-US;q=0.8,en;q=0.6", forHTTPHeaderField: "Accept-Language")
        }
        let data = try await requestFeedData(for: request, source: source.name)
        guard let html = decodeHTML(data) else {
            throw VideoFeedError.malformedResponse(source: source.name)
        }
        if let mediaURL = HTMLVideoSourceAdapter.extractMediaURL(from: html, pageURL: pageURL) {
            return PlaybackSourceResult(url: mediaURL, referer: pageURL.absoluteString, title: title)
        }
        if depth < 2 {
            let playerPages = HTMLVideoSourceAdapter.playerFrameURLs(from: html, pageURL: pageURL, source: source)
            var lastPlayerError: Error?
            for playerPage in playerPages {
                do {
                    return try await resolveHTMLSourceStream(
                        pageUrl: playerPage.absoluteString,
                        source: source,
                        title: title,
                        referer: pageURL.absoluteString,
                        depth: depth + 1
                    )
                } catch {
                    lastPlayerError = error
                }
            }
            if let lastPlayerError { throw lastPlayerError }
        }
        throw VideoFeedError.malformedResponse(source: source.name)
    }

    private func decodeHTML(_ data: Data) -> String? {
        String(data: data, encoding: .utf8)
            ?? String(data: data, encoding: .shiftJIS)
            ?? String(data: data, encoding: .windowsCP1252)
            ?? String(data: data, encoding: .isoLatin1)
    }

    private func resolveJavSbStream(pageUrl: String, title: String) async throws -> PlaybackSourceResult {
        guard let pageURL = URL(string: pageUrl) else {
            throw VideoFeedError.invalidURL(source: "JAVSB")
        }
        var pageRequest = URLRequest(url: pageURL)
        pageRequest.setValue("https://jav.sb/", forHTTPHeaderField: "Referer")
        pageRequest.setValue("text/html,application/xhtml+xml;q=0.9,*/*;q=0.8", forHTTPHeaderField: "Accept")
        let data = try await requestFeedData(for: pageRequest, source: "JAVSB")
        guard let html = decodeHTML(data) else {
            throw VideoFeedError.malformedResponse(source: "JAVSB")
        }

        if let mediaURL = HTMLVideoSourceAdapter.extractMediaURL(from: html, pageURL: pageURL) {
            return PlaybackSourceResult(url: mediaURL, referer: pageURL.absoluteString, title: title)
        }

        guard let srcRegex = try? NSRegularExpression(pattern: #"[?&]src=([A-Za-z0-9]{16,})"#) else {
            throw VideoFeedError.malformedResponse(source: "JAVSB")
        }
        let nsHTML = html as NSString
        guard let match = srcRegex.firstMatch(in: html, range: NSRange(location: 0, length: nsHTML.length)),
              match.numberOfRanges > 1 else {
            throw VideoFeedError.malformedResponse(source: "JAVSB")
        }
        let fileCode = nsHTML.substring(with: match.range(at: 1))

        guard let tokenURL = URL(string: "https://jav.sb/token.php?file=\(fileCode)") else {
            throw VideoFeedError.invalidURL(source: "JAVSB")
        }
        var tokenRequest = URLRequest(url: tokenURL)
        tokenRequest.setValue("https://jav.sb/ja/static/player/videojs.html?src=\(fileCode)", forHTTPHeaderField: "Referer")
        tokenRequest.setValue("application/json, text/plain, */*", forHTTPHeaderField: "Accept")
        let tokenData = try await requestFeedData(for: tokenRequest, source: "JAVSB")

        guard let tokenJson = try? JSONSerialization.jsonObject(with: tokenData) as? [String: Any],
              let token = tokenJson["token"] as? String,
              let ts = tokenJson["ts"] else {
            throw VideoFeedError.malformedResponse(source: "JAVSB")
        }
        let tsString = "\(ts)"

        let currentSecond = Int(Date().timeIntervalSince1970)
        let candidates = [currentSecond, Int(tsString) ?? currentSecond, currentSecond - 1, currentSecond + 1]

        for sec in candidates {
            let signature = JavSbSignature.createSignature(for: "\(fileCode)@\(sec)")
            guard let cacheURL = URL(string: "https://jav.sb/save_m3u8_cache.php?file=\(fileCode)&token=\(token)&ts=\(tsString)&sign=\(signature)") else {
                continue
            }
            var cacheRequest = URLRequest(url: cacheURL)
            cacheRequest.setValue("https://jav.sb/ja/static/player/videojs.html?src=\(fileCode)", forHTTPHeaderField: "Referer")
            cacheRequest.setValue("application/json, text/plain, */*", forHTTPHeaderField: "Accept")
            guard let cacheData = try? await requestFeedData(for: cacheRequest, source: "JAVSB"),
                  let cacheJson = try? JSONSerialization.jsonObject(with: cacheData) as? [String: Any] else {
                continue
            }
            if let sourceStr = cacheJson["source"] as? String, let sourceURL = URL(string: sourceStr) {
                return PlaybackSourceResult(url: sourceURL, referer: "https://jav.sb/", title: title)
            }
            if let m3u8File = cacheJson["m3u8_file"] as? String {
                let fullPath = m3u8File.hasPrefix("http") ? m3u8File : "https://jav.sb\(m3u8File.hasPrefix("/") ? "" : "/")\(m3u8File)"
                let separator = fullPath.contains("?") ? "&" : "?"
                if let streamURL = URL(string: "\(fullPath)\(separator)sign=\(signature)") {
                    return PlaybackSourceResult(url: streamURL, referer: "https://jav.sb/", title: title)
                }
            }
        }

        throw VideoFeedError.malformedResponse(source: "JAVSB")
    }

    private func resolveHuavodStream(pageUrl: String, title: String) async throws -> PlaybackSourceResult {
        guard let episode = Self.huavodEpisodeParam(from: pageUrl) else {
            throw VideoFeedError.invalidURL(source: "华视影院")
        }

        guard let nextURL = URL(string: "https://huavod.com/Player/next?episode=\(episode)"),
              let resolveURL = URL(string: "https://huavod.com/Player/resolveUrl") else {
            throw VideoFeedError.invalidURL(source: "华视影院")
        }

        let playerURL = "https://huavod.com/Player/ec?episode=\(episode)"
        var lastError: Error?

        for attempt in 1...2 {
            do {
                var nextRequest = URLRequest(url: nextURL)
                nextRequest.setValue(playerURL, forHTTPHeaderField: "Referer")
                nextRequest.setValue("application/json", forHTTPHeaderField: "Accept")
                let nextData = try await requestFeedData(for: nextRequest, source: "华视影院")
                let token = try Self.huavodResolveToken(from: nextData)
                if let root = try? JSONSerialization.jsonObject(with: nextData) as? [String: Any],
                   let payload = root["data"] as? [String: Any] {
                    // Fast path: 若是非 BunnyCDN 的直接 m3u8，直接秒开
                    if let channelKey = payload["channel_key"] as? String,
                       !channelKey.contains("b-cdn.net"),
                       channelKey.contains(".m3u8"),
                       let channelURL = URL(string: channelKey.replacingOccurrences(of: "\\/", with: "/")) {
                        return PlaybackSourceResult(url: channelURL, referer: playerURL, title: title)
                    }
                    if let adDuration = (payload["ad_duration_ms"] as? NSNumber)?.intValue {
                        // 关键修复：严禁提前发起请求！华视服务端以 Redis 签发时间为准，提前哪怕 1 毫秒就会判定 too early 并不恢复地销毁 token。
                        // 必须增加 1200ms 的安全网络与时钟误差缓冲，保证服务端绝对已过广告时长。
                        let safeWait = max(0, adDuration) + 1_200
                        if safeWait > 0 {
                            try await Task.sleep(for: .milliseconds(safeWait))
                        }
                    }
                }

                var resolveRequest = URLRequest(url: resolveURL)
                resolveRequest.httpMethod = "POST"
                resolveRequest.setValue(playerURL, forHTTPHeaderField: "Referer")
                resolveRequest.setValue("https://huavod.com", forHTTPHeaderField: "Origin")
                resolveRequest.setValue("XMLHttpRequest", forHTTPHeaderField: "X-Requested-With")
                resolveRequest.setValue("application/x-www-form-urlencoded; charset=UTF-8", forHTTPHeaderField: "Content-Type")
                var form = URLComponents()
                form.queryItems = [URLQueryItem(name: "token", value: token)]
                resolveRequest.httpBody = form.percentEncodedQuery?.data(using: .utf8)
                let resolvedData = try await requestFeedData(for: resolveRequest, source: "华视影院")
                let streamURL = try Self.huavodResolvedStreamURL(from: resolvedData)
                return PlaybackSourceResult(url: streamURL, referer: playerURL, title: title)
            } catch {
                lastError = error
                if attempt < 2 {
                    try? await Task.sleep(for: .milliseconds(800))
                }
            }
        }

        throw lastError ?? VideoFeedError.malformedResponse(source: "华视影院")
    }

    static func huavodResolveToken(from data: Data) throws -> String {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              (root["code"] as? Int) == 1,
              let payload = root["data"] as? [String: Any],
              let token = payload["mt"] as? String ?? payload["token"] as? String,
              !token.isEmpty else {
            throw VideoFeedError.malformedResponse(source: "华视影院")
        }
        return token
    }

    static func huavodResolvedStreamURL(from data: Data) throws -> URL {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              (root["code"] as? Int) == 1,
              let payload = root["data"] as? [String: Any],
              let rawURL = payload["url"] as? String,
              let streamURL = URL(string: rawURL.replacingOccurrences(of: "\\/", with: "/"), relativeTo: URL(string: "https://huavod.com"))?.absoluteURL,
              streamURL.scheme == "https",
              streamURL.pathExtension.lowercased() == "m3u8" else {
            throw VideoFeedError.malformedResponse(source: "华视影院")
        }
        return streamURL
    }

    static func huavodEpisodeParam(from pageUrl: String) -> String? {
        if let match = pageUrl.range(of: #"/vodplay/(\d+-\d+-\d+)"#, options: .regularExpression) {
            return String(pageUrl[match]).replacingOccurrences(of: "/vodplay/", with: "")
        }
        if let match = pageUrl.range(of: #"/voddetail/(\d+)"#, options: .regularExpression) {
            return String(pageUrl[match]).replacingOccurrences(of: "/voddetail/", with: "") + "-1-1"
        }
        return nil
    }

    public func resolveXVideosStream(pageUrl: String, title: String) async throws -> PlaybackSourceResult {
        // 净化 XVideos 链接中的模板占位符（如 /THUMBNUM/），防止 404
        var cleanUrlStr = pageUrl.replacingOccurrences(of: "/THUMBNUM/", with: "/0/")
        if let normRegex = try? NSRegularExpression(pattern: #"/video\.([a-zA-Z0-9_-]+)/\d+/(?:THUMBNUM|\d+)/"#) {
            cleanUrlStr = normRegex.stringByReplacingMatches(in: cleanUrlStr, range: NSRange(location: 0, length: cleanUrlStr.utf16.count), withTemplate: "/video.$1/")
        }

        guard let url = URL(string: cleanUrlStr) else {
            throw NSError(domain: "VideoRepository", code: 400, userInfo: [NSLocalizedDescriptionKey: "无效 XVideos 链接"])
        }

        var req = URLRequest(url: url)
        req.setValue("https://www.xvideos.com/", forHTTPHeaderField: "Referer")
        req.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")

        let (data, resp) = try await session.data(for: req)
        guard let httpResp = resp as? HTTPURLResponse, (200...299).contains(httpResp.statusCode),
              let html = String(data: data, encoding: .utf8) else {
            throw NSError(domain: "VideoRepository", code: 502, userInfo: [NSLocalizedDescriptionKey: "无法加载 XVideos 播放页"])
        }

        // 优先解析 MP4 原画直链（无 HLS 切片防盗链与相对路径解析风险，AVPlayer 硬件秒开）
        let highPattern = #"html5player\.setVideoUrlHigh\('([^']+)'\)"#
        if let highRegex = try? NSRegularExpression(pattern: highPattern),
           let match = highRegex.firstMatch(in: html, range: NSRange(location: 0, length: (html as NSString).length)) {
            let stream = (html as NSString).substring(with: match.range(at: 1))
            if let streamUrl = URL(string: stream) {
                return PlaybackSourceResult(url: streamUrl, referer: "https://www.xvideos.com/", title: title)
            }
        }

        // 次选 MP4 标清直链
        let lowPattern = #"html5player\.setVideoUrlLow\('([^']+)'\)"#
        if let lowRegex = try? NSRegularExpression(pattern: lowPattern),
           let match = lowRegex.firstMatch(in: html, range: NSRange(location: 0, length: (html as NSString).length)) {
            let stream = (html as NSString).substring(with: match.range(at: 1))
            if let streamUrl = URL(string: stream) {
                return PlaybackSourceResult(url: streamUrl, referer: "https://www.xvideos.com/", title: title)
            }
        }

        // 兜底提取静态 HTML 无脚本降级链接中的 mp4
        let staticMp4Pattern = #"<div id="html5video_base"[^>]*>.*?<a\s+href="([^"]+\.mp4[^"]*)""#
        if let staticRegex = try? NSRegularExpression(pattern: staticMp4Pattern, options: .dotMatchesLineSeparators),
           let match = staticRegex.firstMatch(in: html, range: NSRange(location: 0, length: (html as NSString).length)) {
            let stream = (html as NSString).substring(with: match.range(at: 1))
            if let streamUrl = URL(string: stream) {
                return PlaybackSourceResult(url: streamUrl, referer: "https://www.xvideos.com/", title: title)
            }
        }

        // 最后备用 HLS 自适应流
        let hlsPattern = #"html5player\.setVideoHLS\('([^']+)'\)"#
        if let hlsRegex = try? NSRegularExpression(pattern: hlsPattern),
           let match = hlsRegex.firstMatch(in: html, range: NSRange(location: 0, length: (html as NSString).length)) {
            let stream = (html as NSString).substring(with: match.range(at: 1))
            if let streamUrl = URL(string: stream) {
                return PlaybackSourceResult(url: streamUrl, referer: "https://www.xvideos.com/", title: title)
            }
        }

        throw NSError(domain: "VideoRepository", code: 404, userInfo: [NSLocalizedDescriptionKey: "未找到 XVideos 播放流"])
    }

    // 全网搜索
    @MainActor
    public func searchAllSources(query: String) async {
        let token = UUID()
        currentSearchToken = token
        isSearching = true
        searchResults = []
        searchError = nil
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            isSearching = false
            return
        }

        let searchableSources = VideoSourceCatalog.searchableSources
        let results = await withTaskGroup(of: [VideoItem]?.self, returning: [[VideoItem]?].self) { group in
            for source in searchableSources {
                group.addTask {
                    do {
                        return try await self.searchVideos(for: source, query: trimmed)
                    } catch {
                        return nil
                    }
                }
            }
            var collected: [[VideoItem]?] = []
            for await result in group { collected.append(result) }
            return collected
        }
        guard currentSearchToken == token else { return }
        guard !Task.isCancelled else {
            isSearching = false
            return
        }

        let allVideos = results.compactMap { $0 }.flatMap { $0 }
        searchResults = allVideos
        let failedFeedCount = results.filter { $0 == nil }.count
        if failedFeedCount == results.count {
            searchError = "搜索暂时失败，请检查网络后重试。"
        } else if failedFeedCount > 0 {
            searchError = "部分来源暂时无法访问，已显示其余搜索结果。"
        }
        isSearching = false
    }

    private func searchVideos(for source: VideoSourceDefinition, query: String) async throws -> [VideoItem] {
        guard VideoSourceCatalog.isSupported(source),
              let url = HTMLVideoSourceAdapter.searchURL(for: source, query: query) else {
            throw VideoFeedError.unsupportedSource(source: source.name)
        }

        var request = URLRequest(url: url)
        request.setValue(HTMLVideoSourceAdapter.baseURL(for: source) + "/", forHTTPHeaderField: "Referer")
        request.setValue("text/html,application/json;q=0.9,*/*;q=0.8", forHTTPHeaderField: "Accept")
        if source.id == "spankbang" {
            request.setValue("age_pass=1; pg_interstitial_v5=1; pg_pop_v5=1; player_quality=1080; preroll_skip=1; backend_version=main; videos_layout=four-col", forHTTPHeaderField: "Cookie")
            request.setValue("en-US,en;q=0.9", forHTTPHeaderField: "Accept-Language")
        }
        if source.id == "pornhub" || url.host?.contains("pornhub") == true {
            request.setValue("age_verified=1; has_access=1; accessAgeDisclaimerPH=1; platform=mobile; bs=000000000000000022522ffa8ac94dcd; cookieConsent=3;", forHTTPHeaderField: "Cookie")
            request.setValue("ja,en-US;q=0.8,en;q=0.6", forHTTPHeaderField: "Accept-Language")
        }
        if source.id == VideoSourceCatalog.beeg.id {
            request.setValue("https://beeg.com", forHTTPHeaderField: "Origin")
        }
        let data = try await requestFeedData(for: request, source: source.name)

        if source.id == VideoSourceCatalog.eporner.id {
            return try UniversalVideoEngine.decodeEPORNERFeed(data: data, statusCode: 200, category: "搜索")
        }
        if source.id == VideoSourceCatalog.beeg.id {
            return try parseBeegFeed(data: data, category: "搜索")
        }
        if source.id == VideoSourceCatalog.xvideos.id {
            return try UniversalVideoEngine.decodeXVideosFeed(data: data, statusCode: 200, category: "搜索")
        }

        guard let html = String(data: data, encoding: .utf8) else {
            throw VideoFeedError.malformedResponse(source: source.name)
        }
        switch source.id {
        case "huavod": return parseHuavodHtml(html, category: "搜索")
        case "huangguo": return parseHuangguoHtml(html, category: "搜索")
        case "91porn": return parse91PornHtml(html, category: "搜索")
        case "91dongman": return parse91DongmanHtml(html, category: "搜索")
        default:
            guard VideoSourceCatalog.htmlAdapterSourceIDs.contains(source.id) else {
                throw VideoFeedError.unsupportedSource(source: source.name)
            }
            let videos = HTMLVideoSourceAdapter.parseHTMLFeed(html, source: source, category: "搜索", baseURL: HTMLVideoSourceAdapter.baseURL(for: source))
            guard !videos.isEmpty else { return [] }
            return videos
        }
    }

    // MARK: - 选集与多集剧集解析（Episode Resolver）
    private var episodeCache: [String: [PlaybackEpisode]] = [:]

    /// 获取视频对应的完整剧集列表（电视剧、动漫、短剧支持多集选集，单片电影默认为正片）
    public func resolveEpisodes(for video: VideoItem) async -> [PlaybackEpisode] {
        if let cached = episodeCache[video.playbackUrl], !cached.isEmpty {
            return cached
        }

        let episodes: [PlaybackEpisode]
        if video.playbackUrl.contains("huangguoai.com") {
            episodes = await resolveHuangguoEpisodes(pageUrl: video.playbackUrl)
        } else if video.playbackUrl.contains("huavod.com") {
            episodes = await resolveHuavodEpisodes(pageUrl: video.playbackUrl)
        } else if video.playbackUrl.contains("91dongman.net") {
            episodes = await resolve91DongmanEpisodes(pageUrl: video.playbackUrl)
        } else {
            episodes = [PlaybackEpisode(number: 1, label: "正片", pageUrl: video.playbackUrl)]
        }

        let finalEpisodes = episodes.isEmpty ? [PlaybackEpisode(number: 1, label: "第 1 集", pageUrl: video.playbackUrl)] : episodes
        episodeCache[video.playbackUrl] = finalEpisodes
        return finalEpisodes
    }

    private func resolveHuangguoEpisodes(pageUrl: String) async -> [PlaybackEpisode] {
        let dramaId: String? = {
            if let match = pageUrl.range(of: #"/detail/(\d+)"#, options: .regularExpression) {
                return String(pageUrl[match]).components(separatedBy: "/").filter({ !$0.isEmpty }).last
            }
            if let match = pageUrl.range(of: #"/video/(\d+)"#, options: .regularExpression) {
                return String(pageUrl[match]).components(separatedBy: "/").filter({ !$0.isEmpty }).last
            }
            if let match = pageUrl.range(of: #"/play/(\d+)"#, options: .regularExpression) {
                return String(pageUrl[match]).components(separatedBy: "/").filter({ !$0.isEmpty }).last
            }
            return nil
        }()

        guard let id = dramaId else {
            return [PlaybackEpisode(number: 1, label: "第 1 集", pageUrl: pageUrl)]
        }

        let originInfo = huangguoOrigin(for: URL(string: pageUrl) ?? URL(string: "https://huangguoai.com")!)
        let origin = originInfo.origin

        // 1. 优先调用黄果 Edge 网关 GET /api/videos/{id}（秒级返回结构化全集目录 + 首集流）
        if let (data, _) = try? await executeHuangguoEdgeRequest(origin: origin, method: "GET", path: "/api/videos/\(id)"),
           let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let dataDict = root["data"] as? [String: Any] {

            var episodesList: [PlaybackEpisode] = []
            if let epsArray = dataDict["episodes"] as? [[String: Any]] {
                for (idx, epDict) in epsArray.enumerated() {
                    let epNum = epDict["ep_num"] as? Int ?? (idx + 1)
                    let epUrl = epNum == 1 ? "\(origin)/video/\(id)/" : "\(origin)/video/\(id)/ep-\(epNum)/"
                    episodesList.append(PlaybackEpisode(number: epNum, label: "第 \(epNum) 集", pageUrl: epUrl))
                }
            }
            if episodesList.isEmpty {
                let totalEp = dataDict["episode_count"] as? Int ?? 0
                if totalEp > 0 {
                    for epNum in 1...totalEp {
                        let epUrl = epNum == 1 ? "\(origin)/video/\(id)/" : "\(origin)/video/\(id)/ep-\(epNum)/"
                        episodesList.append(PlaybackEpisode(number: epNum, label: "第 \(epNum) 集", pageUrl: epUrl))
                    }
                }
            }

            // 预存第一集播放流（如果返回了 video_url），实现 0ms 秒开！
            let firstVideoUrl = (dataDict["video_url"] as? String) ?? (dataDict["video_url_hevc"] as? String)
            if let stream = firstVideoUrl, !stream.isEmpty, let sUrl = URL(string: cleanHlsUrl(stream)) {
                let ep1Key = "\(origin)/video/\(id)/"
                let result = PlaybackSourceResult(
                    url: sUrl,
                    headers: [
                        "Referer": "\(origin)/",
                        "Origin": origin,
                        "User-Agent": Self.USER_AGENT
                    ],
                    referer: "\(origin)/",
                    title: "第 1 集"
                )
                Task { @MainActor in
                    VideoPreloadManager.shared.cache(result: result, for: ep1Key)
                    VideoPreloadManager.shared.cache(result: result, for: pageUrl)
                }
            }

            if !episodesList.isEmpty {
                return episodesList
            }
        }

        let videoUrl = "https://huangguoai.com/video/\(id)/"
        guard let url = URL(string: videoUrl) else {
            return [PlaybackEpisode(number: 1, label: "第 1 集", pageUrl: pageUrl)]
        }

        var request = URLRequest(url: url)
        request.setValue(Self.USER_AGENT, forHTTPHeaderField: "User-Agent")
        request.setValue("https://huangguoai.com/", forHTTPHeaderField: "Referer")
        request.timeoutInterval = 10

        guard let (data, response) = try? await session.data(for: request),
              let http = response as? HTTPURLResponse, (200...399).contains(http.statusCode),
              let html = String(data: data, encoding: .utf8) else {
            return [PlaybackEpisode(number: 1, label: "第 1 集", pageUrl: pageUrl)]
        }

        var results: [PlaybackEpisode] = []
        var seen = Set<Int>()

        results.append(PlaybackEpisode(number: 1, label: "第 1 集", pageUrl: "https://huangguoai.com/video/\(id)/"))
        seen.insert(1)

        // 1. 尝试从 videoInitialData JSON 中提取 trackingEpisodes 或 total_episode
        if let initRange = html.range(of: #"id=["']videoInitialData["'][^>]*>(.*?)</script>"#, options: .regularExpression) {
            let scriptContent = String(html[initRange])
            if let jsonStart = scriptContent.firstIndex(of: ">"),
               let jsonEnd = scriptContent.lastIndex(of: "<") {
                let jsonStr = String(scriptContent[scriptContent.index(after: jsonStart)..<jsonEnd])
                if let jsonData = jsonStr.data(using: .utf8),
                   let jsonObj = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any] {
                    if let trackingEpisodes = jsonObj["trackingEpisodes"] as? [String: [String: Any]] {
                        for (epKey, _) in trackingEpisodes {
                            if let epNum = Int(epKey), epNum > 0, seen.insert(epNum).inserted {
                                let epUrl = epNum == 1 ? "https://huangguoai.com/video/\(id)/" : "https://huangguoai.com/video/\(id)/ep-\(epNum)/"
                                results.append(PlaybackEpisode(number: epNum, label: "第 \(epNum) 集", pageUrl: epUrl))
                            }
                        }
                    }
                    if let tracking = jsonObj["tracking"] as? [String: Any],
                       let total = tracking["total_episode"] as? Int, total > 1 {
                        for i in 1...total {
                            if seen.insert(i).inserted {
                                let epUrl = i == 1 ? "https://huangguoai.com/video/\(id)/" : "https://huangguoai.com/video/\(id)/ep-\(i)/"
                                results.append(PlaybackEpisode(number: i, label: "第 \(i) 集", pageUrl: epUrl))
                            }
                        }
                    }
                }
            }
        }

        // 2. 正则匹配 HTML 中的集数链接
        let epPattern = #"href="(/video/\#(id)/ep-(\d+)/?)""#
        if let regex = try? NSRegularExpression(pattern: epPattern) {
            let nsHtml = html as NSString
            let matches = regex.matches(in: html, range: NSRange(location: 0, length: nsHtml.length))
            for m in matches {
                let href = nsHtml.substring(with: m.range(at: 1))
                if let epNum = Int(nsHtml.substring(with: m.range(at: 2))), seen.insert(epNum).inserted {
                    let fullUrl = href.starts(with: "http") ? href : "https://huangguoai.com" + href
                    results.append(PlaybackEpisode(number: epNum, label: "第 \(epNum) 集", pageUrl: fullUrl))
                }
            }
        }

        let legacyPattern = #"data-ep-id="(\d+)"[^>]*href="([^"]+)""#
        if let regex = try? NSRegularExpression(pattern: legacyPattern) {
            let nsHtml = html as NSString
            let matches = regex.matches(in: html, range: NSRange(location: 0, length: nsHtml.length))
            for m in matches {
                if let epNum = Int(nsHtml.substring(with: m.range(at: 1))), seen.insert(epNum).inserted {
                    let href = nsHtml.substring(with: m.range(at: 2))
                    let fullUrl = href.starts(with: "http") ? href : "https://huangguoai.com" + href
                    results.append(PlaybackEpisode(number: epNum, label: "第 \(epNum) 集", pageUrl: fullUrl))
                }
            }
        }

        return results.sorted(by: { $0.number < $1.number })
    }

    public nonisolated func parseHuavodEpisodesFromHtml(_ html: String) -> [PlaybackEpisode] {
        var results: [PlaybackEpisode] = []
        var seenNids = Set<Int>()
        var primarySid: String? = nil
        // 华视播放页与详情页兼容正则：支持 data-url 与 href，支持内嵌 <span> 标题与 <em> 状态
        let pattern = #"(?:href|data-url)=["\x27](/vodplay/(\d+)-(\d+)-(\d+)\.html)["\x27][^>]*>(?:<span[^>]*>)?([^<]+)"#
        if let regex = try? NSRegularExpression(pattern: pattern) {
            let nsHtml = html as NSString
            let matches = regex.matches(in: html, range: NSRange(location: 0, length: nsHtml.length))
            var fallback = 1
            for m in matches {
                let href = nsHtml.substring(with: m.range(at: 1))
                let sid = nsHtml.substring(with: m.range(at: 3))
                let nid = Int(nsHtml.substring(with: m.range(at: 4))) ?? fallback
                let label = nsHtml.substring(with: m.range(at: 5)).trimmingCharacters(in: .whitespacesAndNewlines)
                if primarySid == nil {
                    primarySid = sid
                }
                // 仅选用首个主线路 sid，杜绝多线路导致同一集重复展示
                if sid == primarySid && seenNids.insert(nid).inserted {
                    let fullUrl = href.starts(with: "http") ? href : "https://huavod.com" + href
                    results.append(PlaybackEpisode(number: nid, label: label.isEmpty ? "第 \(nid) 集" : label, pageUrl: fullUrl))
                    fallback += 1
                }
            }
        }
        return results.sorted(by: { $0.number < $1.number })
    }

    private func resolveHuavodEpisodes(pageUrl: String) async -> [PlaybackEpisode] {
        guard let url = URL(string: pageUrl) else {
            return [PlaybackEpisode(number: 1, label: "第 1 集", pageUrl: pageUrl)]
        }

        var request = URLRequest(url: url)
        request.setValue(Self.USER_AGENT, forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 10

        guard let (data, response) = try? await session.data(for: request),
              let http = response as? HTTPURLResponse, (200...399).contains(http.statusCode),
              let html = String(data: data, encoding: .utf8) else {
            return [PlaybackEpisode(number: 1, label: "第 1 集", pageUrl: pageUrl)]
        }

        var results = parseHuavodEpisodesFromHtml(html)

        // 容灾兜底：若播放页解析结果小于等于 1 集，且为 vodplay 链接，尝试读取详情页补齐选集
        if results.count <= 1, let vodIdMatch = pageUrl.range(of: #"/vodplay/(\d+)-"#, options: .regularExpression) {
            let vodId = String(pageUrl[vodIdMatch]).replacingOccurrences(of: "/vodplay/", with: "").replacingOccurrences(of: "-", with: "")
            let detailUrlStr = "https://huavod.com/voddetail/\(vodId).html"
            if let detailUrl = URL(string: detailUrlStr), detailUrlStr != pageUrl {
                var detailReq = URLRequest(url: detailUrl)
                detailReq.setValue(Self.USER_AGENT, forHTTPHeaderField: "User-Agent")
                detailReq.timeoutInterval = 8
                if let (detailData, detailResp) = try? await session.data(for: detailReq),
                   let detailHttp = detailResp as? HTTPURLResponse, (200...399).contains(detailHttp.statusCode),
                   let detailHtml = String(data: detailData, encoding: .utf8) {
                    let detailEpisodes = parseHuavodEpisodesFromHtml(detailHtml)
                    if detailEpisodes.count > results.count {
                        results = detailEpisodes
                    }
                }
            }
        }

        let finalEpisodes = results.isEmpty ? [PlaybackEpisode(number: 1, label: "第 1 集", pageUrl: pageUrl)] : results
        if let firstEp = finalEpisodes.first {
            await MainActor.run {
                VideoPreloadManager.shared.prewarmNextEpisode(
                    pageUrl: firstEp.pageUrl,
                    title: firstEp.label,
                    sourceName: "华视影院"
                )
            }
        }
        return finalEpisodes
    }

    private func resolve91DongmanEpisodes(pageUrl: String) async -> [PlaybackEpisode] {
        guard let url = URL(string: pageUrl) else {
            return [PlaybackEpisode(number: 1, label: "第 1 集", pageUrl: pageUrl)]
        }

        var request = URLRequest(url: url)
        request.setValue(Self.USER_AGENT, forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 10

        guard let (data, response) = try? await session.data(for: request),
              let http = response as? HTTPURLResponse, (200...399).contains(http.statusCode),
              let html = String(data: data, encoding: .utf8) else {
            return [PlaybackEpisode(number: 1, label: "第 1 集", pageUrl: pageUrl)]
        }

        var results: [PlaybackEpisode] = []
        var seen = Set<Int>()
        let pattern = #"href="(/dongmanplay/(\d+)-(\d+)-(\d+)\.html)"[^>]*>([^<]+)</a>"#
        if let regex = try? NSRegularExpression(pattern: pattern) {
            let nsHtml = html as NSString
            let matches = regex.matches(in: html, range: NSRange(location: 0, length: nsHtml.length))
            for m in matches {
                let href = nsHtml.substring(with: m.range(at: 1))
                if let nid = Int(nsHtml.substring(with: m.range(at: 4))), seen.insert(nid).inserted {
                    let label = nsHtml.substring(with: m.range(at: 5)).trimmingCharacters(in: .whitespacesAndNewlines)
                    let fullUrl = href.starts(with: "http") ? href : "https://91dongman.net" + href
                    results.append(PlaybackEpisode(number: nid, label: label.isEmpty ? "第 \(nid) 话" : label, pageUrl: fullUrl))
                }
            }
        }

        let sorted = results.isEmpty ? [PlaybackEpisode(number: 1, label: "第 1 话", pageUrl: pageUrl)] : results.sorted(by: { $0.number < $1.number })
        if let first = sorted.first {
            let epVideo = VideoItem(
                id: first.pageUrl,
                title: first.label,
                subtitle: "第 1 话",
                coverUrl: "",
                playbackUrl: first.pageUrl,
                sourceName: VideoSourceCatalog.dongman91.name
            )
            Task { @MainActor in
                VideoPreloadManager.shared.prewarm(video: epVideo)
            }
        }
        return sorted
    }
}

public enum JavSbSignature {
    private static let alphabet = Array("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
    private static let xorKey: UInt8 = 0x7b

    public static func createSignature(for value: String) -> String {
        let bytes = Array(value.utf8).map { $0 ^ xorKey }
        var number = Array(bytes)
        while number.first == 0 { number.removeFirst() }
        if number.isEmpty { return String(alphabet[0]) }

        var result: [Character] = []
        while !number.isEmpty {
            var remainder = 0
            var newDigits: [UInt8] = []
            for byte in number {
                let current = remainder * 256 + Int(byte)
                let quotient = current / 62
                remainder = current % 62
                if !newDigits.isEmpty || quotient > 0 { newDigits.append(UInt8(quotient)) }
            }
            result.append(alphabet[remainder])
            number = newDigits
        }
        return String(result.reversed())
    }
}
