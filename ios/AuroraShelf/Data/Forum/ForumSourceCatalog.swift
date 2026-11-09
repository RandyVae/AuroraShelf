import Foundation

public struct ForumBoard: Identifiable, Hashable, Sendable {
    public let id: String
    public let fid: Int
    public let name: String
    public let description: String

    public init(id: String, fid: Int, name: String, description: String) {
        self.id = id
        self.fid = fid
        self.name = name
        self.description = description
    }
}

public struct ForumPostItem: Identifiable, Hashable, Sendable {
    public let id: String
    public let fid: Int
    public var compositeID: String { "\(fid):\(id)" }
    public let boardName: String
    public let title: String
    public let author: String?
    public let postTime: String?
    public let replies: Int?
    public let views: Int?
    public let previewImageUrl: String?
    public let contentImageUrls: [String]
    public let contentText: String
    public let threadUrl: String

    public init(
        id: String,
        fid: Int,
        boardName: String,
        title: String,
        author: String? = nil,
        postTime: String? = nil,
        replies: Int? = nil,
        views: Int? = nil,
        previewImageUrl: String? = nil,
        contentImageUrls: [String] = [],
        contentText: String = "",
        threadUrl: String = ""
    ) {
        self.id = id
        self.fid = fid
        self.boardName = boardName
        self.title = title
        self.author = author
        self.postTime = postTime
        self.replies = replies
        self.views = views
        self.previewImageUrl = previewImageUrl
        self.contentImageUrls = contentImageUrls
        self.contentText = contentText
        self.threadUrl = threadUrl
    }
}

public enum ForumSourceCatalog {
    public static let fid4 = ForumBoard(id: "fid_4", fid: 4, name: "原创自拍区", description: "91 核心主力原创区")
    public static let fid21 = ForumBoard(id: "fid_21", fid: 21, name: "我爱我妻区", description: "热门夫妻自拍专区")
    public static let fid33 = ForumBoard(id: "fid_33", fid: 33, name: "性趣闲谈", description: "论坛话题与经验交流")
    public static let fid34 = ForumBoard(id: "fid_34", fid: 34, name: "两性健康", description: "两性健康与知识交流")
    public static let fid36 = ForumBoard(id: "fid_36", fid: 36, name: "海外专区", description: "海外用户交流")
    public static let t66y = ForumBoard(id: "t66y", fid: 100, name: "草榴技术区", description: "经典社区原创精华")
    public static let t66yDaguerre = ForumBoard(id: "t66y_16", fid: 16, name: "达盖尔的旗帜", description: "唯美摄影与图片分享")

    public static let allBoards: [ForumBoard] = [
        fid4, fid21, fid33, fid34, fid36, t66y, t66yDaguerre
    ]

    public static func isT66Y(fid: Int) -> Bool {
        fid == t66y.fid || fid == t66yDaguerre.fid
    }
}

enum ForumBoardSelection {
    static func fids(for selectedFid: Int?) -> [Int] {
        selectedFid.map { [$0] } ?? ForumSourceCatalog.allBoards.map(\.fid)
    }
}

enum ForumSourceAdapter {
    static func pageURL(fid: Int, page: Int) -> URL? {
        guard page >= 1 else { return nil }
        if fid == ForumSourceCatalog.t66y.fid {
            return URL(string: "https://t66y.com/thread0806.php?fid=7&page=\(page)")
        }
        if fid == ForumSourceCatalog.t66yDaguerre.fid {
            return URL(string: "https://t66y.com/thread0806.php?fid=16&page=\(page)")
        }
        return URL(string: "https://t924.sol140.com/forumdisplay.php?fid=\(fid)&page=\(page)")
    }
}

typealias ForumPostFetcher = @Sendable (Int, Int) async throws -> [ForumPostItem]

private enum ForumFetchError: LocalizedError, Sendable {
    case invalidURL
    case invalidResponse
    case httpStatus(Int)
    case transport(String)

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "论坛地址无效，请稍后重试。"
        case .invalidResponse:
            return "论坛返回了无法识别的数据，请稍后重试。"
        case .httpStatus(let status):
            return "论坛请求失败（HTTP \(status)），请稍后重试。"
        case .transport(let message):
            return "无法连接论坛：\(message)"
        }
    }
}

// MARK: - 社区垃圾内容与官方无用公告强力过滤管道

public enum ForumNoiseFilter {
    /// 官方须知、版规与新手引导类黑名单关键词
    public static let noticeKeywords: [String] = [
        "91网须知", "91论坛须知", "论坛须知", "本版须知", "发帖须知", "版面须知",
        "版规", "总版规", "新手必读", "新手须知", "置顶公告", "新手指南", "站务公告", "本站须知",
        "论坛手机版", "手机版使用", "手机版教程", "手机客户端", "手机浏览",
        "如何获取", "邀请码", "获取91", "论坛邀请", "注册邀请", "购买邀请", "邀请码发放", "求邀请",
        "新手入门", "防封指南", "最新地址", "永久地址", "地址发布", "发布页", "网址发布",
        "回家地址", "防丢失", "请收藏", "防屏蔽", "永久域名", "官方域名", "防封教程"
    ]

    /// 广告推广、博彩招募与引流黑名单关键词
    public static let spamKeywords: [String] = [
        "广告", "赞助商", "特约", "官方合作", "商务合作", "招租",
        "博彩", "彩票", "棋牌", "真人视讯", "澳门银河", "开元棋牌", "太阳城", "威尼斯人",
        "代理加盟", "高薪兼职", "代充", "话费充值", "币充值", "usdt", "USDT", "虚拟币",
        "加微信", "加V", "加微", "微信号", "薇信", "进群", "tg群", "电报群",
        "telegram", "Telegram", "扫码进群", "福利群", "约炮群", "修车群", "外围女", "品茶",
        "充值优惠", "返利", "开奖", "彩种", "投注", "包赢"
    ]

    /// 标题前缀标签黑名单
    public static let blockedPrefixes: [String] = [
        "【公告】", "[公告]", "【置顶】", "[置顶]", "【版务】", "[版务]",
        "【注意】", "[注意]", "【通知】", "[通知]", "【必读】", "[必读]",
        "【版规】", "[版规]", "【提醒】", "[提醒]"
    ]

    /// 判定一个帖子是否属于噪音/广告/官方无用帖子
    public static func isNoisePost(title: String) -> Bool {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 4 else { return true }

        // 纯数字或纯标点拦截
        let alphanumericOrChinese = trimmed.filter { $0.isLetter || $0.isNumber }
        guard alphanumericOrChinese.count >= 2 else { return true }

        // 前缀标签拦截
        for prefix in blockedPrefixes {
            if trimmed.hasPrefix(prefix) { return true }
        }

        // 统一小写并剔除常见标点与空格进行模糊匹配
        let normalized = trimmed.lowercased()
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "\t", with: "")
            .replacingOccurrences(of: "【", with: "")
            .replacingOccurrences(of: "】", with: "")
            .replacingOccurrences(of: "[", with: "")
            .replacingOccurrences(of: "]", with: "")
            .replacingOccurrences(of: "-", with: "")
            .replacingOccurrences(of: "_", with: "")

        // 官方须知与指南拦截
        for keyword in noticeKeywords {
            let cleanKeyword = keyword.lowercased().replacingOccurrences(of: " ", with: "")
            if normalized.contains(cleanKeyword) {
                return true
            }
        }

        // 广告与灰产垃圾信息拦截
        for keyword in spamKeywords {
            let cleanKeyword = keyword.lowercased().replacingOccurrences(of: " ", with: "")
            if normalized.contains(cleanKeyword) {
                return true
            }
        }

        return false
    }
}

enum ForumPostParser {
    static func parse(_ html: String, fid: Int, baseUrl: String) -> [ForumPostItem] {
        var items: [ForumPostItem] = []
        let pattern = #"(?is)<a\s+href="(viewthread\.php\?tid=(\d+)[^"]*)"[^>]*>(.*?)</a>"#
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return [] }
        let nsHtml = html as NSString
        let matches = regex.matches(in: html, options: [], range: NSRange(location: 0, length: nsHtml.length))

        var seenTids = Set<String>()
        for match in matches {
            let threadRel = nsHtml.substring(with: match.range(at: 1)).replacingOccurrences(of: "&amp;", with: "&")
            let tid = nsHtml.substring(with: match.range(at: 2))
            let rawTitle = nsHtml.substring(with: match.range(at: 3))
                .replacingOccurrences(of: #"(?is)<[^>]+>"#, with: " ", options: .regularExpression)
            let title = cleanText(rawTitle)
            guard title.count >= 4 && !title.contains("最新") && !title.contains("首页") else { continue }
            guard !ForumNoiseFilter.isNoisePost(title: title) else { continue }
            guard seenTids.insert(tid).inserted else { continue }

            let board = ForumSourceCatalog.allBoards.first(where: { $0.fid == fid })?.name ?? "原创自拍区"
            items.append(ForumPostItem(
                id: tid,
                fid: fid,
                boardName: board,
                title: title,
                threadUrl: URL(string: threadRel, relativeTo: URL(string: baseUrl + "/"))?.absoluteURL.absoluteString ?? "\(baseUrl)/\(threadRel)"
            ))
        }

        return items
    }

    static func parseT66Y(_ html: String, fid: Int, baseUrl: String) -> [ForumPostItem] {
        guard let regex = try? NSRegularExpression(pattern: #"(?is)<a\b([^>]*)>(.*?)</a>"#) else { return [] }
        let ns = html as NSString
        var seen = Set<String>()
        return regex.matches(in: html, range: NSRange(location: 0, length: ns.length)).compactMap { match in
            let attributes = ns.substring(with: match.range(at: 1))
            guard let href = htmlAttribute("href", in: attributes) else { return nil }
            let tid: String
            if let idMatch = try? NSRegularExpression(pattern: #"tid=(\d+)"#).firstMatch(
                in: href, range: NSRange(href.startIndex..., in: href)
            ) {
                guard href.range(of: #"(?:read|viewthread)\.php\?tid=\d+"#, options: .regularExpression) != nil else { return nil }
                tid = (href as NSString).substring(with: idMatch.range(at: 1))
            } else if href.range(of: #"/htm_data/[^"']+\.html$"#, options: .regularExpression) != nil,
                      let idMatch = try? NSRegularExpression(pattern: #"/([0-9]+)\.html$"#).firstMatch(
                        in: href, range: NSRange(href.startIndex..., in: href)
                      ) {
                tid = (href as NSString).substring(with: idMatch.range(at: 1))
            } else {
                return nil
            }
            guard seen.insert(tid).inserted else { return nil }
            let title = ns.substring(with: match.range(at: 2))
                .replacingOccurrences(of: #"(?is)<[^>]+>"#, with: " ", options: .regularExpression)
                .replacingOccurrences(of: "&nbsp;", with: " ")
                .trimmingCharacters(in: .whitespacesAndNewlines)
            guard !title.isEmpty, !title.allSatisfy(\.isNumber) else { return nil }
            guard !ForumNoiseFilter.isNoisePost(title: title) else { return nil }
            let threadURL: String
            if href.contains("/htm_data/") {
                threadURL = URL(string: href, relativeTo: URL(string: baseUrl + "/"))?.absoluteURL.absoluteString ?? "\(baseUrl)\(href)"
            } else {
                threadURL = "\(baseUrl)/read.php?tid=\(tid)"
            }
            let boardName = ForumSourceCatalog.allBoards.first(where: { $0.fid == fid })?.name ?? "草榴社区"
            return ForumPostItem(
                id: tid,
                fid: fid,
                boardName: boardName,
                title: title,
                threadUrl: threadURL
            )
        }
    }

    static func parseDetail(_ html: String, post: ForumPostItem) -> ForumPostItem {
        let isT66y = ForumSourceCatalog.isT66Y(fid: post.fid)
        let bodyHTML: String?
        if isT66y {
            bodyHTML = firstElementContents(tag: "div", matching: #"\bclass=['\"][^'\"]*\btpc_content\b[^'\"]*['\"]"#, in: html)
        } else {
            bodyHTML = firstElementContents(tag: "td", matching: #"\bid=['\"]postmessage_\d+['\"]"#, in: html)
        }
        let content = bodyHTML.map(cleanText) ?? ""
        var contentImages = bodyHTML.map(extractContentImages) ?? []
        // 容灾兜底：若深度计数因嵌套标签提前闭合，使用正则跨度提取正文内所有图片
        if contentImages.isEmpty {
            if isT66y {
                if let range = html.range(of: #"class=['\"][^'\"]*\btpc_content\b[^'\"]*['\"][^>]*>([\s\S]*?)(?:<div\s+onclick="clickLike|<div\s+class="c"|</td>|</div>\s*</div>)"#, options: .regularExpression) {
                    contentImages = extractContentImages(String(html[range]))
                }
            } else {
                if let range = html.range(of: #"id=['\"]postmessage_\d+['\"][^>]*>([\s\S]*?)</td>"#, options: .regularExpression) {
                    contentImages = extractContentImages(String(html[range]))
                }
            }
        }
        let baseURL = URL(string: post.threadUrl)
            ?? URL(string: isT66y ? "https://t66y.com/" : "https://t924.sol140.com/")
        let resolvedImages = contentImages.compactMap { raw -> String? in
            let unescaped = raw.replacingOccurrences(of: "&amp;", with: "&")
            guard let url = URL(string: unescaped, relativeTo: baseURL)?.absoluteURL,
                  let scheme = url.scheme?.lowercased(),
                  ["https", "http"].contains(scheme),
                  url.host != nil else { return nil }
            return url.absoluteString
        }
        return ForumPostItem(
            id: post.id, fid: post.fid, boardName: post.boardName, title: post.title,
            author: post.author, postTime: post.postTime, replies: post.replies, views: post.views,
            previewImageUrl: resolvedImages.first ?? post.previewImageUrl,
            contentImageUrls: resolvedImages,
            contentText: content,
            threadUrl: post.threadUrl
        )
    }

    private static func firstElementContents(tag: String, matching attributePattern: String, in html: String) -> String? {
        let openingPattern = "(?is)<\(tag)\\b(?=[^>]*\(attributePattern))[^>]*>"
        guard let openingRegex = try? NSRegularExpression(pattern: openingPattern),
              let opening = openingRegex.firstMatch(in: html, range: NSRange(html.startIndex..., in: html)),
              let openingRange = Range(opening.range, in: html),
              let tokenRegex = try? NSRegularExpression(pattern: "(?is)</?\(tag)\\b[^>]*>") else { return nil }
        var depth = 1
        let contentStart = openingRange.upperBound
        for token in tokenRegex.matches(in: html, range: NSRange(contentStart..., in: html)) {
            guard let tokenRange = Range(token.range, in: html) else { continue }
            let tokenText = html[tokenRange]
            if tokenText.hasPrefix("</") {
                depth -= 1
                if depth == 0 { return String(html[contentStart..<tokenRange.lowerBound]) }
            } else if !tokenText.hasSuffix("/>") {
                depth += 1
            }
        }
        return nil
    }

    private static func extractContentImages(_ html: String) -> [String] {
        guard let imageRegex = try? NSRegularExpression(pattern: #"(?is)<img\b([^>]*)>"#) else { return [] }
        let ns = html as NSString
        var seen = Set<String>()
        var results: [String] = []
        for match in imageRegex.matches(in: html, range: NSRange(location: 0, length: ns.length)) {
            let attributes = ns.substring(with: match.range(at: 1))
            let candidates = ["ess-data", "data-link", "file", "zoomfile", "data-src", "data-original", "src"]
            for attr in candidates {
                if let rawVal = htmlAttribute(attr, in: attributes) {
                    let clean = rawVal.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !clean.isEmpty,
                          !clean.hasPrefix("data:"),
                          !clean.contains("none.gif"),
                          !clean.contains("adblo_ck.jpg"),
                          !clean.contains("attachimg.gif"),
                          !clean.contains("ding.png"),
                          !clean.contains("ding_big.png"),
                          !clean.contains("star_level"),
                          !clean.contains("userinfo.gif"),
                          !clean.contains("logo.png"),
                          !clean.contains("desktop.png"),
                          !clean.contains("mobile.png") else {
                        continue
                    }
                    if seen.insert(clean).inserted {
                        results.append(clean)
                        break
                    }
                }
            }
        }
        return results
    }

    private static func firstCapture(_ pattern: String, in value: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: value, range: NSRange(value.startIndex..., in: value)),
              let range = Range(match.range(at: 1), in: value) else { return nil }
        return String(value[range])
    }

    private static func cleanText(_ html: String) -> String {
        html
            .replacingOccurrences(of: #"(?is)<script\b[^>]*>.*?</script>|<style\b[^>]*>.*?</style>"#, with: " ", options: .regularExpression)
            .replacingOccurrences(of: #"(?i)<br\s*/?>|</p>|</div>"#, with: "\n", options: .regularExpression)
            .replacingOccurrences(of: #"(?is)<[^>]+>"#, with: " ", options: .regularExpression)
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&amp;", with: "&")
            .split(whereSeparator: { $0.isWhitespace })
            .joined(separator: " ")
    }

    private static func htmlAttribute(_ name: String, in attributes: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: "(?i)\\b\(NSRegularExpression.escapedPattern(for: name))\\s*=\\s*(['\\\"])(.*?)\\1") else { return nil }
        let ns = attributes as NSString
        guard let match = regex.firstMatch(in: attributes, range: NSRange(location: 0, length: ns.length)) else { return nil }
        return ns.substring(with: match.range(at: 2)).replacingOccurrences(of: "&amp;", with: "&")
    }
}

@MainActor
public final class ForumRepository: ObservableObject {
    public static let shared = ForumRepository()

    @Published public var posts: [ForumPostItem] = []
    /// `nil` means all boards currently exposed by the forum picker.
    @Published public var selectedBoardFid: Int?
    @Published public var isLoading: Bool = false
    @Published public var isLoadingNextPage: Bool = false
    @Published public var errorMessage: String?

    private let session: URLSession
    private let postFetcher: ForumPostFetcher?
    private var currentLoadTask: Task<Void, Never>?
    private var activeRequestID = UUID()
    private var currentPage = 1
    private var hasMorePages = true
    private var detailCache: [String: ForumPostItem] = [:]
    private var inFlightDetailTasks: [String: Task<ForumPostItem, Error>] = [:]

    public func cachedDetail(for post: ForumPostItem) -> ForumPostItem? {
        detailCache[post.compositeID]
    }

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.httpAdditionalHeaders = [
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            "Cookie": "language=cn_CN; watch_times=1"
        ]
        self.session = URLSession.permissive(configuration: config)
        self.postFetcher = nil
        self.loadPosts()
    }

    init(fetcher: @escaping ForumPostFetcher) {
        self.session = .shared
        self.postFetcher = fetcher
    }

    public func loadPosts() {
        _ = startLoad(for: selectedBoardFid)
    }

    public func selectBoard(_ fid: Int?) {
        guard selectedBoardFid != fid else { return }
        selectedBoardFid = fid
        posts = []
        currentPage = 1
        hasMorePages = true
        isLoadingNextPage = false
        errorMessage = nil
        _ = startLoad(for: fid)
    }

    public func loadNextPage() async {
        guard !isLoading, !isLoadingNextPage, hasMorePages else { return }
        let selectedFid = selectedBoardFid
        let requestID = activeRequestID
        let nextPage = currentPage + 1
        isLoadingNextPage = true
        defer { isLoadingNextPage = false }

        let (loaded, failures) = await fetchSelectedBoards(selectedFid, page: nextPage)
        guard activeRequestID == requestID, selectedBoardFid == selectedFid else { return }
        if !loaded.isEmpty {
            let existingIDs = Set(posts.map { "\($0.fid):\($0.id)" })
            let newPosts = loaded.filter { !existingIDs.contains("\($0.fid):\($0.id)") }
            posts.append(contentsOf: newPosts)
            self.prefetchPostImages(for: newPosts)
            hasMorePages = !newPosts.isEmpty
            currentPage = nextPage
        } else if failures.isEmpty {
            hasMorePages = false
        }
        errorMessage = failures.isEmpty ? nil : failures.joined(separator: "\n")
    }

    public func reloadPosts() async {
        let task = startLoad(for: selectedBoardFid)
        await withTaskCancellationHandler {
            await task.value
        } onCancel: {
            task.cancel()
        }
    }

    public func loadDetail(for post: ForumPostItem) async throws -> ForumPostItem {
        if let cached = detailCache[post.compositeID] {
            return cached
        }
        if let ongoing = inFlightDetailTasks[post.compositeID] {
            return try await ongoing.value
        }
        guard let url = URL(string: post.threadUrl), url.scheme == "https",
              ["t924.sol140.com", "t66y.com"].contains(url.host?.lowercased() ?? "") else {
            throw ForumFetchError.invalidURL
        }
        let session = self.session
        let task = Task<ForumPostItem, Error> {
            var request = URLRequest(url: url)
            request.setValue("https://\(url.host ?? "")", forHTTPHeaderField: "Referer")
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else { throw ForumFetchError.invalidResponse }
            guard (200...299).contains(http.statusCode) else { throw ForumFetchError.httpStatus(http.statusCode) }
            guard let html = String(data: data, encoding: .utf8)
                    ?? String(data: data, encoding: .init(rawValue: 0x80000632)) else { throw ForumFetchError.invalidResponse }
            let detail = ForumPostParser.parseDetail(html, post: post)
            guard !detail.contentText.isEmpty || detail.previewImageUrl != nil else { throw ForumFetchError.invalidResponse }
            return detail
        }
        inFlightDetailTasks[post.compositeID] = task
        defer { inFlightDetailTasks[post.compositeID] = nil }

        let detail = try await task.value
        detailCache[post.compositeID] = detail
        return detail
    }

    private func startLoad(for selectedFid: Int?) -> Task<Void, Never> {
        currentLoadTask?.cancel()
        let requestID = UUID()
        activeRequestID = requestID
        self.isLoading = true
        self.errorMessage = nil
        currentPage = 1
        hasMorePages = true
        isLoadingNextPage = false
        let loadTask = Task { [weak self] in
            guard let self else { return }
            let (loaded, failures) = await self.fetchSelectedBoards(selectedFid, page: 1)
            guard self.activeRequestID == requestID else { return }
            guard !Task.isCancelled else {
                self.isLoading = false
                return
            }
            if !loaded.isEmpty || failures.isEmpty {
                self.posts = loaded
                self.prefetchPostImages(for: loaded)
            }
            self.hasMorePages = !loaded.isEmpty
            self.errorMessage = failures.isEmpty ? nil : failures.joined(separator: "\n")
            self.isLoading = false
        }
        currentLoadTask = loadTask
        return loadTask
    }

    private func prefetchPostImages(for candidatePosts: [ForumPostItem]) {
        let targets = Array(candidatePosts.prefix(12))
        Task { [weak self] in
            for target in targets {
                guard let self else { return }
                do {
                    let detail = try await self.loadDetail(for: target)
                    await MainActor.run { [weak self] in
                        guard let self else { return }
                        self.detailCache[target.compositeID] = detail
                        if let idx = self.posts.firstIndex(where: { $0.id == target.id && $0.fid == target.fid }) {
                            self.posts[idx] = detail
                        }
                    }
                    // 管道预热前 2 张重点图片
                    for imgUrl in detail.contentImageUrls.prefix(2) {
                        _ = await CoverImageLoader.shared.loadImage(from: imgUrl, requestReferer: target.threadUrl)
                    }
                } catch {
                    // Silently ignore prefetch errors
                }
            }
        }
    }

    private func fetchSelectedBoards(_ selectedFid: Int?, page: Int) async -> ([ForumPostItem], [String]) {
        let boardIds = ForumBoardSelection.fids(for: selectedFid)
        let postFetcher = self.postFetcher
        let session = self.session
        var postsByBoard: [Int: [ForumPostItem]] = [:]
        var failures: [String] = []

        await withTaskGroup(of: (Int, Result<[ForumPostItem], ForumFetchError>).self) { group in
            for fid in boardIds {
                group.addTask {
                    do {
                        let posts: [ForumPostItem]
                        if let postFetcher {
                            posts = try await postFetcher(fid, page)
                        } else {
                            posts = try await Self.fetchRealForumPosts(fid: fid, page: page, session: session)
                        }
                        return (fid, .success(posts))
                    } catch let error as ForumFetchError {
                        return (fid, .failure(error))
                    } catch {
                        return (fid, .failure(ForumFetchError.transport(error.localizedDescription)))
                    }
                }
            }

            for await (fid, result) in group {
                switch result {
                case .success(let posts):
                    postsByBoard[fid] = posts
                case .failure(let error):
                    let boardName = ForumSourceCatalog.allBoards.first(where: { $0.fid == fid })?.name ?? "论坛板块"
                    failures.append("\(boardName)：\(error.localizedDescription)")
                }
            }
        }

        let orderedPosts = boardIds.flatMap { postsByBoard[$0] ?? [] }
        return (orderedPosts, failures)
    }

    // MARK: - 真实抓取 91 论坛帖子

    nonisolated private static func fetchRealForumPosts(fid: Int, page: Int, session: URLSession) async throws -> [ForumPostItem] {
        guard let url = ForumSourceAdapter.pageURL(fid: fid, page: page),
              let baseUrl = url.host.map({ "https://\($0)" }) else { throw ForumFetchError.invalidURL }

        var request = URLRequest(url: url)
        request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")
        request.setValue("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", forHTTPHeaderField: "Accept")
        request.setValue(baseUrl, forHTTPHeaderField: "Referer")
        let (data, response) = try await session.data(for: request)
        guard let httpResp = response as? HTTPURLResponse else { throw ForumFetchError.invalidResponse }
        guard (200...299).contains(httpResp.statusCode) else { throw ForumFetchError.httpStatus(httpResp.statusCode) }
        guard let html = String(data: data, encoding: .utf8)
                ?? String(data: data, encoding: .init(rawValue: 0x80000632)) /* GBK */ else {
            throw ForumFetchError.invalidResponse
        }

        return ForumSourceCatalog.isT66Y(fid: fid)
            ? ForumPostParser.parseT66Y(html, fid: fid, baseUrl: baseUrl)
            : ForumPostParser.parse(html, fid: fid, baseUrl: baseUrl)
    }
}
