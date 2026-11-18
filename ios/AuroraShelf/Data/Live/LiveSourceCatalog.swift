import Foundation

public struct LiveViewerCount: Hashable, Codable, Sendable, CustomStringConvertible {
    public let value: Int?

    public init(value: Int?) {
        self.value = value
    }

    public var description: String {
        value.map { String($0) } ?? "未提供"
    }
}

public struct LiveStreamItem: Identifiable, Hashable, Codable, Sendable {
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

@MainActor
public final class LiveRepository: ObservableObject {
    public static let shared = LiveRepository()

    @Published public var liveStreams: [LiveStreamItem] = []
    @Published public var selectedGender: String = "全部"
    @Published public private(set) var selectedSourceID: String = LiveSourceCatalog.allSourcesID
    @Published public var isLoading: Bool = false
    @Published public var loadErrorMessage: String?
    @Published public var currentPage: Int = 1
    @Published public var canLoadMore: Bool = true
    @Published public var isLoadingNextPage: Bool = false

    public let categories = ["全部", "女性主播", "情侣互动", "男性主播", "跨性别"]
    private let session: URLSession
    private var currentLoadTask: Task<Void, Never>?
    private var activeRequestID = UUID()
    private var hasTriggeredInitialLoad = false

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 25
        config.timeoutIntervalForResource = 35
        config.httpAdditionalHeaders = [
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            "Accept": "application/xml, text/xml, */*"
        ]
        self.session = URLSession.permissive(configuration: config)
        if let snapshot = SourceSnapshotStore.shared.getLiveFeed(sourceId: selectedSourceID, gender: selectedGender),
           !snapshot.items.isEmpty {
            self.liveStreams = snapshot.items
            print("[AuroraPerf] live_swr_init count=\(snapshot.items.count) fresh=\(snapshot.isFresh)")
        }
    }

    public func loadStreamsIfNeeded() {
        guard !hasTriggeredInitialLoad else { return }
        hasTriggeredInitialLoad = true
        loadStreams()
    }

    public func loadStreams() {
        hasTriggeredInitialLoad = true
        Task.detached(priority: .utility) {
            _ = await StripchatStreamAuthenticator.shared.refreshPkeyIfStale()
        }
        _ = startLoading()
    }

    public func selectSource(_ sourceID: String) {
        guard LiveSourceCatalog.validSourceIDs.contains(sourceID), selectedSourceID != sourceID else { return }
        selectedSourceID = sourceID
        currentPage = 1
        canLoadMore = sourceID != LiveSourceCatalog.hornyStudentsID
        isLoadingNextPage = false
        if let snapshot = SourceSnapshotStore.shared.getLiveFeed(sourceId: sourceID, gender: selectedGender),
           !snapshot.items.isEmpty {
            liveStreams = snapshot.items
            loadErrorMessage = nil
        } else {
            liveStreams = []
        }
        _ = startLoading()
    }

    public func selectGender(_ gender: String) {
        guard categories.contains(gender), selectedGender != gender else { return }
        selectedGender = gender
        currentPage = 1
        canLoadMore = selectedSourceID != LiveSourceCatalog.hornyStudentsID
        isLoadingNextPage = false
        if let snapshot = SourceSnapshotStore.shared.getLiveFeed(sourceId: selectedSourceID, gender: gender),
           !snapshot.items.isEmpty {
            liveStreams = snapshot.items
            loadErrorMessage = nil
        } else {
            liveStreams = []
        }
        _ = startLoading()
    }

    public func refreshStreams() async {
        hasTriggeredInitialLoad = true
        await startLoading().value
    }

    public func loadNextPage() async {
        guard canLoadMore, !isLoading, !isLoadingNextPage else { return }
        guard selectedSourceID == LiveSourceCatalog.allSourcesID || selectedSourceID == LiveSourceCatalog.stripchatID else {
            canLoadMore = false
            return
        }
        let capturedRequestID = activeRequestID
        let capturedSourceID = selectedSourceID
        let capturedGender = selectedGender
        let nextPage = currentPage + 1
        isLoadingNextPage = true
        defer {
            if activeRequestID == capturedRequestID {
                isLoadingNextPage = false
            }
        }

        do {
            let nextItems = try await fetchRealStripchatFeed(gender: capturedGender, page: nextPage)
            guard !Task.isCancelled,
                  activeRequestID == capturedRequestID,
                  selectedSourceID == capturedSourceID,
                  selectedGender == capturedGender else { return }
            let existingIDs = Set(liveStreams.map(\.id))
            let uniqueNew = nextItems.filter { !existingIDs.contains($0.id) }
            if uniqueNew.isEmpty {
                canLoadMore = false
            } else {
                liveStreams.append(contentsOf: uniqueNew)
                currentPage = nextPage
                canLoadMore = !nextItems.isEmpty
            }
        } catch {
            // Keep canLoadMore = true on transient error so user can scroll to retry
        }
    }

    private func startLoading() -> Task<Void, Never> {
        currentLoadTask?.cancel()
        let requestID = UUID()
        activeRequestID = requestID
        let selectedSourceID = self.selectedSourceID
        let selectedGender = self.selectedGender
        self.currentPage = 1
        self.canLoadMore = selectedSourceID != LiveSourceCatalog.hornyStudentsID
        self.isLoadingNextPage = false

        if self.liveStreams.isEmpty,
           let snapshot = SourceSnapshotStore.shared.getLiveFeed(sourceId: selectedSourceID, gender: selectedGender),
           !snapshot.items.isEmpty {
            self.liveStreams = snapshot.items
        }
        self.isLoading = self.liveStreams.isEmpty
        self.loadErrorMessage = nil

        let task = Task { [weak self] in
            guard let self else { return }

            let outcome = await self.fetchSelectedSources(selectedSourceID: selectedSourceID, selectedGender: selectedGender)
            guard !Task.isCancelled, self.activeRequestID == requestID else { return }

            switch outcome {
            case .loaded(let streams, let warnings):
                if !streams.isEmpty {
                    SourceSnapshotStore.shared.putLiveFeed(sourceId: selectedSourceID, gender: selectedGender, items: streams)
                }
                self.liveStreams = streams
                self.canLoadMore = !streams.isEmpty && selectedSourceID != LiveSourceCatalog.hornyStudentsID
                self.loadErrorMessage = warnings.isEmpty ? nil : warnings.joined(separator: "\n")
            case .failed(let message):
                if self.liveStreams.isEmpty {
                    self.loadErrorMessage = message
                } else {
                    self.loadErrorMessage = nil
                }
            }
            self.isLoading = false
            self.currentLoadTask = nil
        }
        currentLoadTask = task
        return task
    }

    nonisolated static func matchesSelectedGender(itemGender: String, selectedGender: String) -> Bool {
        if selectedGender == "全部" || selectedGender.isEmpty {
            return true
        }
        let g = itemGender.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        switch selectedGender {
        case "女性主播":
            return g == "f" || g == "female" || g == "girls" || g == "girl"
        case "情侣互动":
            return g == "c" || g == "couple" || g == "couples"
        case "男性主播":
            return g == "m" || g == "male" || g == "men" || g == "man"
        case "跨性别":
            return g == "s" || g == "t" || g == "trans" || g == "shemale"
        default:
            return true
        }
    }

    private nonisolated func fetchSelectedSources(selectedSourceID: String, selectedGender: String) async -> LiveFeedOutcome {
        if selectedSourceID == LiveSourceCatalog.allSourcesID {
            async let hsFeed = runCatchingStreams { try await self.fetchRealHornyStudentsFeed(gender: selectedGender) }
            async let scFeed = runCatchingStreams { try await self.fetchRealStripchatFeed(gender: selectedGender, page: 1) }
            let (hsResult, scResult) = await (hsFeed, scFeed)

            var streams: [LiveStreamItem] = []
            var failures: [String] = []

            switch hsResult {
            case .success(let items): streams.append(contentsOf: items)
            case .failure(let err): failures.append("HornyStudents：\(err.localizedDescription)")
            }
            switch scResult {
            case .success(let items): streams.append(contentsOf: items)
            case .failure(let err): failures.append("Stripchat：\(err.localizedDescription)")
            }

            if !streams.isEmpty {
                return .loaded(streams.uniqued(by: \.id), failures)
            } else if !failures.isEmpty {
                return .failed(failures.joined(separator: "\n"))
            } else {
                return .loaded([], [])
            }
        }

        do {
            let loaded = selectedSourceID == LiveSourceCatalog.stripchatID
                ? try await fetchRealStripchatFeed(gender: selectedGender, page: 1)
                : try await fetchRealHornyStudentsFeed(gender: selectedGender)
            return .loaded(loaded, [])
        } catch {
            guard !Task.isCancelled else { return .failed("直播列表加载已取消。") }
            return .failed("\(LiveSourceCatalog.name(for: selectedSourceID))：\((error as? LocalizedError)?.errorDescription ?? error.localizedDescription)")
        }
    }

    private nonisolated func runCatchingStreams(_ block: @Sendable @escaping () async throws -> [LiveStreamItem]) async -> Result<[LiveStreamItem], Error> {
        do {
            let res = try await block()
            return .success(res)
        } catch {
            return .failure(error)
        }
    }

    // MARK: - 真实请求 HornyStudents RSS 2.0 在线主播流

    private nonisolated func fetchRealHornyStudentsFeed(gender: String = "全部") async throws -> [LiveStreamItem] {
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
        let allItems = try Self.parseHornyStudentsRSS(data: data)
        return allItems.filter { Self.matchesSelectedGender(itemGender: $0.gender, selectedGender: gender) }
    }

    private nonisolated func fetchRealStripchatFeed(gender: String, page: Int = 1) async throws -> [LiveStreamItem] {
        let primaryTags: [String]
        switch gender {
        case "女性主播": primaryTags = ["girls"]
        case "情侣互动": primaryTags = ["couples"]
        case "男性主播": primaryTags = ["men"]
        case "跨性别": primaryTags = ["trans"]
        default: primaryTags = ["girls", "couples"]
        }

        let (deduped, lastError): ([LiveStreamItem], Error?) = await withTaskGroup(of: Result<[LiveStreamItem], Error>.self) { group in
            for tag in primaryTags {
                group.addTask {
                    do {
                        return .success(try await self.fetchStripchatFeed(primaryTag: tag, page: page))
                    } catch {
                        return .failure(error)
                    }
                }
            }
            var allStreams: [LiveStreamItem] = []
            var capturedError: Error?
            for await result in group {
                switch result {
                case .success(let streams):
                    allStreams.append(contentsOf: streams)
                case .failure(let error):
                    capturedError = error
                }
            }
            return (allStreams.uniqued(by: \.id), capturedError)
        }
        if !deduped.isEmpty {
            return deduped
        }
        if let lastError {
            throw lastError
        }
        return []
    }

    private nonisolated func fetchStripchatFeed(primaryTag: String, page: Int = 1) async throws -> [LiveStreamItem] {
        let hosts = ["https://zh.stripchat.com", "https://stripchat.com"]
        var lastError: Error = LiveFeedError.invalidURL
        for host in hosts {
            do {
                let url = LiveSourceAdapter.stripchatFeedURL(page: page, primaryTag: primaryTag, base: host)
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

    // MARK: - Single-Pass XMLParserDelegate for HornyStudents RSS (CDATA + XXE Hardening)

    nonisolated static func parseHornyStudentsRSS(_ xml: String) throws -> [LiveStreamItem] {
        guard let data = xml.data(using: .utf8) else {
            throw LiveFeedError.invalidEncoding
        }
        return try parseHornyStudentsRSS(data: data)
    }

    nonisolated static func parseHornyStudentsRSS(data: Data) throws -> [LiveStreamItem] {
        let delegate = HornyStudentsRSSParserDelegate()
        let parser = XMLParser(data: data)
        parser.shouldResolveExternalEntities = false
        parser.shouldProcessNamespaces = false
        parser.delegate = delegate

        guard parser.parse(), delegate.sawRSSRoot else {
            throw LiveFeedError.malformedXML
        }
        guard delegate.rawItemCount == 0 || delegate.sawIdentifiableItem else {
            throw LiveFeedError.malformedXML
        }
        return delegate.items.uniqued(by: \.id)
    }
}

private final class HornyStudentsRSSParserDelegate: NSObject, XMLParserDelegate {
    private(set) var sawRSSRoot = false
    private(set) var rawItemCount = 0
    private(set) var sawIdentifiableItem = false
    private(set) var items: [LiveStreamItem] = []

    private var insideItem = false
    private var currentElementName = ""
    private var currentTextBuffer = ""
    private var currentFields: [String: String] = [:]

    func parser(
        _ parser: XMLParser,
        didStartElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?,
        attributes attributeDict: [String: String] = [:]
    ) {
        let lower = elementName.lowercased()
        if lower == "rss" {
            sawRSSRoot = true
        } else if lower == "item" {
            insideItem = true
            rawItemCount += 1
            currentFields.removeAll(keepingCapacity: true)
        } else if insideItem {
            currentElementName = lower
            currentTextBuffer = ""
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        guard insideItem, !currentElementName.isEmpty else { return }
        currentTextBuffer.append(string)
    }

    func parser(_ parser: XMLParser, foundCDATA CDATABlock: Data) {
        guard insideItem, !currentElementName.isEmpty,
              let text = String(data: CDATABlock, encoding: .utf8) else { return }
        currentTextBuffer.append(text)
    }

    func parser(
        _ parser: XMLParser,
        didEndElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?
    ) {
        let lower = elementName.lowercased()
        if lower == "item" {
            insideItem = false
            finalizeCurrentItem()
            currentElementName = ""
            currentTextBuffer = ""
        } else if insideItem && lower == currentElementName {
            let trimmed = currentTextBuffer.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty {
                currentFields[lower] = trimmed
            }
            currentElementName = ""
            currentTextBuffer = ""
        }
    }

    private func finalizeCurrentItem() {
        let rawUsername = currentFields["cb:username"] ?? currentFields["title"] ?? ""
        let username = rawUsername.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !username.isEmpty else { return }
        sawIdentifiableItem = true

        let currentShow = (currentFields["cb:current_show"] ?? "public").lowercased()
        guard currentShow == "public" else { return }

        let viewers = currentFields["cb:number_of_users_watching"]
            .flatMap(Int.init)
            .flatMap { $0 >= 0 ? $0 : nil }
        let gender = currentFields["cb:gender"] ?? ""
        let location = currentFields["cb:location"] ?? "Online"
        let imgUrl = currentFields["cb:image_url"] ?? "https://thumb.live.mmcdn.com/ri/\(username).jpg"

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
            isPrivateShow: false
        ))
    }
}

enum LiveSourceAdapter {
    static func stripchatFeedURL(page: Int, primaryTag: String? = nil, base: String = "https://zh.stripchat.com") -> URL {
        let offset = (max(page, 1) - 1) * 30
        let endpoint = "\(base)/api/front/models"
        let fallbackURL = URL(string: "https://zh.stripchat.com/api/front/models")
        guard var components = URLComponents(string: endpoint) else {
            return fallbackURL ?? URL(fileURLWithPath: "/")
        }
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
        let tag = (primaryTag.flatMap { $0.isEmpty ? nil : $0 }) ?? "girls"
        queryItems.insert(URLQueryItem(name: "primaryTag", value: tag), at: 2)
        components.queryItems = queryItems
        return components.url ?? fallbackURL ?? URL(fileURLWithPath: "/")
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
            let streamName = (model["streamName"] as? String).flatMap {
                !$0.isEmpty && $0.allSatisfy(\.isNumber) ? $0 : nil
            }
            let effectiveStreamId = streamName ?? numericId ?? ""
            let hlsUrl = StripchatStreamAuthenticator.normalizeMasterURL(
                rawUrl: rawHls,
                fallbackNumericId: effectiveStreamId
            )

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

public enum StripchatResolveOutcome: Sendable, Equatable {
    case authenticated(URL)
    case offlineOrPrivate
    case webFallback
}

public actor StripchatStreamAuthenticator {
    public static let shared = StripchatStreamAuthenticator()
    public static let defaultPkey = "B0p93vi8Uj6AYyZb"
    private static let defaultMmpOrigin = "https://mmp.doppiocdn.com/player/mmp"
    private static let refreshTTL: TimeInterval = 30 * 60
    private static let retryCooldown: TimeInterval = 15

    private var cachedPkey: String = StripchatStreamAuthenticator.defaultPkey
    private var lastRefreshDate: Date?
    private let probeSession: URLSession

    public init() {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 4.0
        config.timeoutIntervalForResource = 7.0
        self.probeSession = URLSession.permissive(configuration: config)
    }

    public func currentPkey() -> String {
        cachedPkey
    }

    public nonisolated static func isMouflonLockedManifest(_ manifest: String) -> Bool {
        let lower = manifest.lowercased()
        return lower.contains("#ext-x-mouflon-advert") || lower.contains("#ext-x-mouflon:file:")
    }

    public nonisolated static func normalizeMasterURL(rawUrl: String?, fallbackNumericId: String = "") -> String? {
        if let raw = rawUrl?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty {
            let pattern = #"^(https://[^/]*doppiocdn\.[^/]+/hls/([0-9]+)/master/)[0-9]+(?:_[0-9]+p)?\.m3u8(\?.*)?$"#
            if let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
               let match = regex.firstMatch(in: raw, range: NSRange(location: 0, length: (raw as NSString).length)) {
                let ns = raw as NSString
                let prefix = ns.substring(with: match.range(at: 1))
                let streamId = ns.substring(with: match.range(at: 2))
                let query = match.range(at: 3).location != NSNotFound ? ns.substring(with: match.range(at: 3)) : ""
                return "\(prefix)\(streamId)_auto.m3u8\(query)"
            }
            return raw
        }
        let cleanId = fallbackNumericId.filter(\.isNumber)
        guard !cleanId.isEmpty else { return nil }
        return "https://edge-hls.doppiocdn.media/hls/\(cleanId)/master/\(cleanId)_auto.m3u8"
    }

    public nonisolated static func attachPkey(masterUrl: String, pkey: String) -> String {
        guard !pkey.isEmpty, masterUrl.range(of: "doppiocdn.", options: .caseInsensitive) != nil else {
            return masterUrl
        }
        let parts = masterUrl.split(separator: "?", maxSplits: 1, omittingEmptySubsequences: false)
        let base = String(parts[0])
        let existingQuery = parts.count > 1 ? String(parts[1]) : ""
        var params = existingQuery
            .split(separator: "&")
            .map(String.init)
            .filter { !$0.isEmpty && !$0.lowercased().hasPrefix("pkey=") }
        params.append("pkey=\(pkey)")
        return "\(base)?\(params.joined(separator: "&"))"
    }

    public nonisolated static func extractMmpBundleURL(fromHtml rawHtml: String) -> URL? {
        let normalized = rawHtml
            .replacingOccurrences(of: "\\u002F", with: "/")
            .replacingOccurrences(of: "\\/", with: "/")
            .replacingOccurrences(of: "\\u0022", with: "\"")
        let ns = normalized as NSString
        let fullRange = NSRange(location: 0, length: ns.length)
        guard let verRegex = try? NSRegularExpression(pattern: #""mmpVersion"\s*:\s*"(v[0-9]+\.[0-9]+\.[0-9][^"]*)""#),
              let verMatch = verRegex.firstMatch(in: normalized, range: fullRange) else {
            return nil
        }
        let version = ns.substring(with: verMatch.range(at: 1))
        var origin = defaultMmpOrigin
        if let origRegex = try? NSRegularExpression(pattern: #""MMPExternalUnitedSourceOrigin"\s*:\s*"(https://[^"]+)""#),
           let origMatch = origRegex.firstMatch(in: normalized, range: fullRange) {
            origin = ns.substring(with: origMatch.range(at: 1)).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        }
        return URL(string: "\(origin)/\(version)/main.js")
    }

    public nonisolated static func extractPkey(fromMmpJS jsSource: String) -> String? {
        let pattern = #"searchParams\.set\(\s*["']pkey["']\s*,\s*["']([A-Za-z0-9_-]{8,64})["']\s*\)"#
        let ns = jsSource as NSString
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: jsSource, range: NSRange(location: 0, length: ns.length)) else {
            return nil
        }
        let key = ns.substring(with: match.range(at: 1)).trimmingCharacters(in: .whitespacesAndNewlines)
        return key.isEmpty ? nil : key
    }

    public func refreshPkeyIfStale() async -> String {
        if let last = lastRefreshDate, Date().timeIntervalSince(last) < Self.refreshTTL {
            return cachedPkey
        }
        return await refreshPkey(force: false) ?? cachedPkey
    }

    public func refreshPkey(force: Bool) async -> String? {
        let now = Date()
        if !force, let last = lastRefreshDate, now.timeIntervalSince(last) < Self.refreshTTL {
            return cachedPkey
        }
        if force, let last = lastRefreshDate, now.timeIntervalSince(last) < Self.retryCooldown {
            return cachedPkey
        }
        lastRefreshDate = now

        let candidateBases = ["https://zh.stripchat.com", "https://stripchat.com"]
        let ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"
        for siteBase in candidateBases {
            guard let pageUrl = URL(string: "\(siteBase)/") else { continue }
            var pageReq = URLRequest(url: pageUrl, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 6.0)
            pageReq.setValue(ua, forHTTPHeaderField: "User-Agent")
            pageReq.setValue("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", forHTTPHeaderField: "Accept")
            pageReq.setValue("zh-CN,zh;q=0.9,en;q=0.8", forHTTPHeaderField: "Accept-Language")

            guard let (htmlData, htmlResp) = try? await probeSession.data(for: pageReq),
                  let httpResp = htmlResp as? HTTPURLResponse,
                  (200...299).contains(httpResp.statusCode),
                  let rawHtml = String(data: htmlData, encoding: .utf8),
                  let bundleUrl = Self.extractMmpBundleURL(fromHtml: rawHtml) else {
                continue
            }

            var jsReq = URLRequest(url: bundleUrl, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 7.0)
            jsReq.setValue(ua, forHTTPHeaderField: "User-Agent")
            jsReq.setValue(siteBase, forHTTPHeaderField: "Origin")
            jsReq.setValue("\(siteBase)/", forHTTPHeaderField: "Referer")
            jsReq.setValue("*/*", forHTTPHeaderField: "Accept")

            if let (jsData, jsResp) = try? await probeSession.data(for: jsReq),
               let jsHttp = jsResp as? HTTPURLResponse,
               (200...299).contains(jsHttp.statusCode),
               let jsText = String(data: jsData, encoding: .utf8),
               let extracted = Self.extractPkey(fromMmpJS: jsText) {
                cachedPkey = extracted
                return extracted
            }
        }
        return nil
    }

    public func resolvePlayableURL(rawHls: String?, fallbackNumericId: String) async -> StripchatResolveOutcome {
        guard let normalizedMaster = Self.normalizeMasterURL(rawUrl: rawHls, fallbackNumericId: fallbackNumericId) else {
            return .webFallback
        }
        var activeKey = cachedPkey
        var candidateString = Self.attachPkey(masterUrl: normalizedMaster, pkey: activeKey)
        guard var candidateURL = URL(string: candidateString) else {
            return .webFallback
        }

        var probeStatus = await probeMasterAndVariant(candidateURL)
        if probeStatus == .mouflonLocked {
            if let refreshed = await refreshPkey(force: true), refreshed != activeKey {
                activeKey = refreshed
                candidateString = Self.attachPkey(masterUrl: normalizedMaster, pkey: activeKey)
                if let updatedURL = URL(string: candidateString) {
                    candidateURL = updatedURL
                    probeStatus = await probeMasterAndVariant(candidateURL)
                }
            }
        }

        switch probeStatus {
        case .live:
            return .authenticated(candidateURL)
        case .offlineOrPrivate:
            return .offlineOrPrivate
        case .mouflonLocked, .invalid:
            return .webFallback
        case .transientError:
            return .authenticated(candidateURL)
        }
    }

    private enum ProbeState {
        case live
        case mouflonLocked
        case offlineOrPrivate
        case invalid
        case transientError
    }

    private func probeMasterAndVariant(_ masterURL: URL) async -> ProbeState {
        let ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        var req = URLRequest(url: masterURL, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 4.0)
        req.setValue(ua, forHTTPHeaderField: "User-Agent")
        req.setValue("https://zh.stripchat.com/", forHTTPHeaderField: "Referer")
        req.setValue("https://zh.stripchat.com", forHTTPHeaderField: "Origin")
        req.setValue("application/vnd.apple.mpegurl,application/x-mpegURL,*/*", forHTTPHeaderField: "Accept")

        do {
            let (data, resp) = try await probeSession.data(for: req)
            guard let http = resp as? HTTPURLResponse else { return .transientError }
            if [403, 404, 410].contains(http.statusCode) { return .offlineOrPrivate }
            guard (200...299).contains(http.statusCode) else { return .transientError }
            let manifest = String(data: data, encoding: .utf8) ?? ""
            guard manifest.localizedCaseInsensitiveContains("#EXTM3U") else { return .invalid }
            if Self.isMouflonLockedManifest(manifest) { return .mouflonLocked }

            let firstVariant = manifest
                .components(separatedBy: .newlines)
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .first { $0.lowercased().hasPrefix("https://") && $0.lowercased().contains(".m3u8") }

            if let variantStr = firstVariant,
               variantStr.localizedCaseInsensitiveContains("doppiocdn"),
               let variantURL = URL(string: variantStr) {
                var vReq = URLRequest(url: variantURL, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 4.0)
                vReq.setValue(ua, forHTTPHeaderField: "User-Agent")
                vReq.setValue("https://zh.stripchat.com/", forHTTPHeaderField: "Referer")
                vReq.setValue("https://zh.stripchat.com", forHTTPHeaderField: "Origin")
                vReq.setValue("application/vnd.apple.mpegurl,application/x-mpegURL,*/*", forHTTPHeaderField: "Accept")
                let (vData, vResp) = try await probeSession.data(for: vReq)
                guard let vHttp = vResp as? HTTPURLResponse else { return .transientError }
                if [403, 404, 410].contains(vHttp.statusCode) { return .offlineOrPrivate }
                guard (200...299).contains(vHttp.statusCode) else { return .transientError }
                let vText = String(data: vData.prefix(2048), encoding: .utf8) ?? ""
                guard vText.localizedCaseInsensitiveContains("#EXTM3U") else { return .invalid }
                if Self.isMouflonLockedManifest(vText) { return .mouflonLocked }
            }
            return .live
        } catch {
            return .transientError
        }
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
