import Foundation

public struct LiveViewerCount: Hashable, Sendable, CustomStringConvertible {
    public let value: Int?

    public init(value: Int?) {
        self.value = value
    }

    public var description: String {
        value.map { String($0) } ?? "未提供"
    }
}

public struct LiveStreamItem: Identifiable, Hashable, Sendable {
    public let id: String
    public let sourceID: String
    public let username: String
    public let title: String
    public let roomUrl: String
    public let avatarUrl: String
    public let snapshotUrl: String
    public let viewCount: LiveViewerCount
    public let tags: [String]
    public let gender: String
    public let isPrivateShow: Bool
    public let hlsStreamUrl: String?
    
    public init(
        id: String,
        sourceID: String = LiveSourceCatalog.hornyStudentsID,
        username: String,
        title: String,
        roomUrl: String,
        avatarUrl: String,
        snapshotUrl: String,
        viewCount: Int?,
        tags: [String] = ["LIVE"],
        gender: String = "",
        isPrivateShow: Bool = false,
        hlsStreamUrl: String? = nil
    ) {
        self.id = id
        self.sourceID = sourceID
        self.username = username
        self.title = title
        self.roomUrl = roomUrl
        self.avatarUrl = avatarUrl
        self.snapshotUrl = snapshotUrl
        self.viewCount = LiveViewerCount(value: viewCount)
        self.tags = tags
        self.gender = gender
        self.isPrivateShow = isPrivateShow
        self.hlsStreamUrl = hlsStreamUrl
    }
}

public final class LiveRepository: ObservableObject, @unchecked Sendable {
    public static let shared = LiveRepository()
    
    @Published public var liveStreams: [LiveStreamItem] = []
    @Published public var selectedGender: String = "全部"
    @Published public private(set) var selectedSourceID: String = LiveSourceCatalog.allSourcesID
    @Published public var isLoading: Bool = false
    @Published public var loadErrorMessage: String? = nil
    
    public let categories = ["全部", "女性主播", "情侣互动", "男性主播", "跨性别"]
    private let session: URLSession
    private var currentLoadTask: Task<Void, Never>?
    private var activeRequestID = UUID()
    
    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.httpAdditionalHeaders = [
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            "Accept": "application/xml, text/xml, */*"
        ]
        self.session = URLSession(configuration: config)
        self.loadStreams()
    }
    
    public func loadStreams() {
        _ = startLoading()
    }

    public func selectSource(_ sourceID: String) {
        guard LiveSourceCatalog.validSourceIDs.contains(sourceID), selectedSourceID != sourceID else { return }
        selectedSourceID = sourceID
        liveStreams = []
        _ = startLoading()
    }

    public func refreshStreams() async {
        await startLoading().value
    }

    private func startLoading() -> Task<Void, Never> {
        currentLoadTask?.cancel()
        let requestID = UUID()
        activeRequestID = requestID
        let selectedSourceID = self.selectedSourceID
        let selectedGender = self.selectedGender
        self.isLoading = true
        self.loadErrorMessage = nil

        let task = Task { [weak self] in
            guard let self else { return }

            let outcome = await self.fetchSelectedSources(selectedSourceID: selectedSourceID, selectedGender: selectedGender)
            guard !Task.isCancelled else { return }

            await MainActor.run {
                guard self.activeRequestID == requestID else { return }
                switch outcome {
                case .loaded(let streams, let warnings):
                    self.liveStreams = streams
                    self.loadErrorMessage = warnings.isEmpty ? nil : warnings.joined(separator: "\n")
                case .failed(let message):
                    self.loadErrorMessage = message
                }
                self.isLoading = false
                self.currentLoadTask = nil
            }
        }
        currentLoadTask = task
        return task
    }

    private func fetchSelectedSources(selectedSourceID: String, selectedGender: String) async -> LiveFeedOutcome {
        let sourceIDs = selectedSourceID == LiveSourceCatalog.allSourcesID
            ? [LiveSourceCatalog.hornyStudentsID, LiveSourceCatalog.stripchatID]
            : [selectedSourceID]

        var streams: [LiveStreamItem] = []
        var failures: [String] = []
        for sourceID in sourceIDs {
            do {
                let loaded = sourceID == LiveSourceCatalog.stripchatID
                    ? try await fetchRealStripchatFeed(gender: selectedGender)
                    : try await fetchRealHornyStudentsFeed()
                streams.append(contentsOf: loaded)
            } catch {
                guard !Task.isCancelled else { return .failed("直播列表加载已取消。") }
                failures.append("\(LiveSourceCatalog.name(for: sourceID))：\((error as? LocalizedError)?.errorDescription ?? error.localizedDescription)")
            }
        }

        guard !streams.isEmpty || failures.isEmpty else {
            return .failed(failures.joined(separator: "\n"))
        }
        return .loaded(streams, failures)
    }
    
    // MARK: - 真实请求 HornyStudents RSS 2.0 在线主播流
    
    private func fetchRealHornyStudentsFeed() async throws -> [LiveStreamItem] {
        guard let url = URL(string: "https://www.hornystudents.com/feed/latest/") else {
            throw LiveFeedError.invalidURL
        }

        let (data, response) = try await session.data(from: url)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw LiveFeedError.invalidHTTPResponse
        }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw LiveFeedError.httpStatus(httpResponse.statusCode)
        }
        guard let xmlString = String(data: data, encoding: .utf8) else {
            throw LiveFeedError.invalidEncoding
        }

        return try parseRssXml(xmlString)
    }

    private func fetchRealStripchatFeed(gender: String) async throws -> [LiveStreamItem] {
        let primaryTags: [String]
        switch gender {
        case "女性主播": primaryTags = ["girls"]
        case "情侣互动": primaryTags = ["couples"]
        case "男性主播": primaryTags = ["men"]
        case "跨性别": primaryTags = ["trans"]
        default: primaryTags = ["girls", "couples", "men", "trans"]
        }

        var streams: [LiveStreamItem] = []
        var errors: [Error] = []
        for tag in primaryTags {
            do {
                streams.append(contentsOf: try await fetchStripchatFeed(primaryTag: tag))
            } catch {
                errors.append(error)
            }
        }
        if streams.isEmpty, let firstErr = errors.first {
            throw firstErr
        }
        return streams.uniqued(by: \.id)
    }

    private func fetchStripchatFeed(primaryTag: String) async throws -> [LiveStreamItem] {
        let hosts = ["https://zh.stripchat.com", "https://stripchat.com"]
        var lastError: Error = LiveFeedError.invalidURL
        for host in hosts {
            do {
                let url = LiveSourceAdapter.stripchatFeedURL(page: 1, primaryTag: primaryTag, base: host)
                var request = URLRequest(url: url)
                request.setValue("application/json", forHTTPHeaderField: "Accept")
                request.setValue("zh-CN,zh;q=0.9,en;q=0.8", forHTTPHeaderField: "Accept-Language")
                request.setValue("\(host)/", forHTTPHeaderField: "Origin")
                request.setValue("\(host)/", forHTTPHeaderField: "Referer")
                let (data, response) = try await session.data(for: request)
                guard let httpResponse = response as? HTTPURLResponse else { throw LiveFeedError.invalidHTTPResponse }
                guard (200...299).contains(httpResponse.statusCode) else { throw LiveFeedError.httpStatus(httpResponse.statusCode) }
                return try LiveSourceAdapter.parseStripchat(data: data)
            } catch {
                lastError = error
            }
        }
        throw lastError
    }
    
    private func parseRssXml(_ xml: String) throws -> [LiveStreamItem] {
        guard xml.range(of: #"<rss(?:\s|>)"#, options: [.regularExpression, .caseInsensitive]) != nil else {
            throw LiveFeedError.malformedXML
        }
        guard let xmlData = xml.data(using: .utf8) else {
            throw LiveFeedError.invalidEncoding
        }
        let xmlParser = XMLParser(data: xmlData)
        xmlParser.shouldResolveExternalEntities = false
        guard xmlParser.parse() else {
            throw LiveFeedError.malformedXML
        }

        var items: [LiveStreamItem] = []
        
        // 正则逐个匹配 <item> 节点
        let itemPattern = #"<item>([\s\S]*?)</item>"#
        guard let itemRegex = try? NSRegularExpression(pattern: itemPattern) else {
            throw LiveFeedError.malformedXML
        }
        let nsXml = xml as NSString
        let matches = itemRegex.matches(in: xml, options: [], range: NSRange(location: 0, length: nsXml.length))
        let hasIdentifiableItem = matches.contains { match in
            let block = nsXml.substring(with: match.range)
            return extractTag(from: block, tag: "cb:username") != nil
                || extractTag(from: block, tag: "title") != nil
        }
        guard matches.isEmpty || hasIdentifiableItem else {
            throw LiveFeedError.malformedXML
        }
        
        for m in matches {
            let block = nsXml.substring(with: m.range)
            
            let username = extractTag(from: block, tag: "cb:username")
                ?? extractTag(from: block, tag: "title")
                ?? ""
            guard !username.isEmpty else { continue }
            
            let viewers = extractTag(from: block, tag: "cb:number_of_users_watching")
                .flatMap { Int($0) }
                .flatMap { $0 >= 0 ? $0 : nil }
            
            let gender = extractTag(from: block, tag: "cb:gender") ?? ""
            let location = extractTag(from: block, tag: "cb:location") ?? "Online"
            
            let currentShow = extractTag(from: block, tag: "cb:current_show") ?? "public"
            let isPrivate = currentShow != "public"
            
            let imgUrl = extractTag(from: block, tag: "cb:image_url")
                ?? "https://thumb.live.mmcdn.com/ri/\(username).jpg"
            
            items.append(LiveStreamItem(
                id: "hs-\(username)",
                sourceID: LiveSourceCatalog.hornyStudentsID,
                username: username,
                title: "\(location) · 正在直播",
                roomUrl: "https://chaturbate.com/\(username)/",
                avatarUrl: imgUrl,
                snapshotUrl: imgUrl,
                viewCount: viewers,
                tags: ["LIVE", location],
                gender: gender,
                isPrivateShow: isPrivate
            ))
        }
        
        return items
    }
    
    private func extractTag(from text: String, tag: String) -> String? {
        let pattern = "<\(tag)>([^<]*?)</\(tag)>"
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
        let ns = text as NSString
        if let match = regex.firstMatch(in: text, options: [], range: NSRange(location: 0, length: ns.length)) {
            return ns.substring(with: match.range(at: 1)).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return nil
    }
}

enum LiveSourceAdapter {
    static func stripchatFeedURL(page: Int, primaryTag: String? = nil, base: String = "https://zh.stripchat.com") -> URL {
        let offset = (max(page, 1) - 1) * 30
        let endpoint = "\(base)/api/front/models"
        var components = URLComponents(string: endpoint)!
        var queryItems = [
            URLQueryItem(name: "improveTs", value: "false"),
            URLQueryItem(name: "removeShows", value: "true"),
            URLQueryItem(name: "limit", value: "30"),
            URLQueryItem(name: "offset", value: String(offset)),
            URLQueryItem(name: "sortBy", value: "stripRanking"),
            URLQueryItem(name: "rcmGrp", value: "A"),
            URLQueryItem(name: "rbCnGr", value: "true"),
            URLQueryItem(name: "prxCnGr", value: "false"),
            URLQueryItem(name: "nic", value: "false")
        ]
        let tag = (primaryTag?.isEmpty == false) ? primaryTag! : "girls"
        queryItems.insert(URLQueryItem(name: "primaryTag", value: tag), at: 2)
        components.queryItems = queryItems
        return components.url!
    }

    static func parseStripchat(data: Data) throws -> [LiveStreamItem] {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let models = root["models"] as? [[String: Any]] else {
            throw LiveFeedError.malformedResponse
        }
        return models.compactMap { model in
            guard (model["isLive"] as? Bool) == true,
                  (model["status"] as? String) == "public",
                  let username = model["username"] as? String,
                  username.range(of: #"^[A-Za-z0-9_-]{1,80}$"#, options: .regularExpression) != nil else { return nil }
            let thumbnail = [model["previewUrlThumbSmall"] as? String, model["avatarUrl"] as? String]
                .compactMap { $0 }
                .first { URL(string: $0)?.scheme == "https" && URL(string: $0)?.host != nil }
            guard let thumbnail else { return nil }
            let viewers = (model["viewersCount"] as? Int).flatMap { $0 >= 0 ? $0 : nil }
            let gender = model["gender"] as? String ?? ""
            let title = model["country"] as? String ?? "Stripchat · 正在直播"
            let rawHls = model["hlsPlaylist"] as? String
            let numericId = (model["id"] as? Int).map { String($0) } ?? (model["id"] as? String)
            let fallbackHls = numericId.map { "https://edge-hls.doppiocdn.media/hls/\($0)/master/\($0).m3u8" }
            let hlsUrl = rawHls ?? fallbackHls

            return LiveStreamItem(
                id: "sc-\(numericId ?? username)",
                sourceID: LiveSourceCatalog.stripchatID,
                username: username,
                title: title,
                roomUrl: "https://zh.stripchat.com/\(username)",
                avatarUrl: (model["avatarUrl"] as? String).flatMap { URL(string: $0)?.scheme == "https" ? $0 : nil } ?? thumbnail,
                snapshotUrl: thumbnail,
                viewCount: viewers,
                tags: ["LIVE", title],
                gender: gender,
                isPrivateShow: false,
                hlsStreamUrl: hlsUrl
            )
        }.uniqued(by: \.id)
    }
}

private extension Sequence where Element: Hashable {
    func uniqued<T: Hashable>(by keyPath: KeyPath<Element, T>) -> [Element] {
        var seen = Set<T>()
        return filter { seen.insert($0[keyPath: keyPath]).inserted }
    }
}

private enum LiveFeedOutcome: Sendable {
    case loaded([LiveStreamItem], [String])
    case failed(String)
}

public struct LiveSourceDefinition: Identifiable, Hashable, Sendable {
    public let id: String
    public let name: String

    public init(id: String, name: String) {
        self.id = id
        self.name = name
    }
}

public enum LiveSourceCatalog {
    public static let allSourcesID = "all"
    public static let hornyStudentsID = "hornystudents"
    public static let stripchatID = "stripchat"
    public static let allSources = [
        LiveSourceDefinition(id: allSourcesID, name: "全部来源"),
        LiveSourceDefinition(id: hornyStudentsID, name: "HornyStudents"),
        LiveSourceDefinition(id: stripchatID, name: "Stripchat")
    ]
    public static let validSourceIDs = Set(allSources.map(\.id))

    public static func name(for id: String) -> String {
        allSources.first(where: { $0.id == id })?.name ?? id
    }
}

private enum LiveFeedError: LocalizedError, Sendable {
    case invalidURL
    case invalidHTTPResponse
    case httpStatus(Int)
    case invalidEncoding
    case malformedXML
    case malformedResponse

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "直播列表网址无效。"
        case .invalidHTTPResponse:
            return "直播列表没有返回有效的 HTTP 响应。"
        case .httpStatus(let status):
            return "直播列表请求失败（HTTP \(status)）。"
        case .invalidEncoding:
            return "直播列表编码无法读取。"
        case .malformedXML:
            return "直播列表格式无效。"
        case .malformedResponse:
            return "直播列表数据格式无法识别。"
        }
    }
}
