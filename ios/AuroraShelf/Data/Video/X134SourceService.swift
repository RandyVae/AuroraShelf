import Foundation

/// Value-type representation of a Seroval wire AST node used by TanStack Start (`https://134x.com/_serverFn`).
public enum X134SerovalValue: Sendable, Equatable {
    case null
    case bool(Bool)
    case number(Double)
    case string(String)
    case array([X134SerovalValue])
    case object([String: X134SerovalValue])

    public var stringValue: String? {
        if case let .string(value) = self { return value }
        return nil
    }

    public var doubleValue: Double? {
        if case let .number(value) = self { return value }
        return nil
    }

    public var intValue: Int? {
        if case let .number(value) = self { return Int(value) }
        return nil
    }

    public var int64Value: Int64? {
        if case let .number(value) = self { return Int64(value) }
        return nil
    }

    public var boolValue: Bool? {
        if case let .bool(value) = self { return value }
        return nil
    }

    public var arrayValue: [X134SerovalValue]? {
        if case let .array(value) = self { return value }
        return nil
    }

    public var objectValue: [String: X134SerovalValue]? {
        if case let .object(value) = self { return value }
        return nil
    }

    public subscript(key: String) -> X134SerovalValue? {
        objectValue?[key]
    }
}

/// Pure Swift Seroval JSON encoder/decoder for `134x.com` `_serverFn` requests & responses.
public enum X134SerovalCodec {
    public static func encodePayload(_ root: X134SerovalValue) throws -> String {
        var nextId = 0
        let encodedRoot = encodeNode(root, nextId: &nextId)
        let envelope: [String: Any] = [
            "t": encodedRoot,
            "f": 127,
            "m": []
        ]
        let jsonData = try JSONSerialization.data(withJSONObject: envelope, options: [])
        guard let jsonString = String(data: jsonData, encoding: .utf8) else {
            throw VideoFeedError.malformedResponse(source: "134X")
        }
        return jsonString
    }

    private static func encodeNode(_ value: X134SerovalValue, nextId: inout Int) -> [String: Any] {
        switch value {
        case .null:
            return ["t": 2, "s": 0]
        case let .bool(flag):
            return ["t": 2, "s": flag ? 2 : 3]
        case let .number(num):
            if num.isFinite && floor(num) == num && abs(num) <= Double(Int64.max / 2) {
                return ["t": 0, "s": Int64(num)]
            }
            return ["t": 0, "s": num]
        case let .string(str):
            return ["t": 1, "s": str]
        case let .array(items):
            let id = nextId
            nextId += 1
            return [
                "t": 9,
                "i": id,
                "a": items.map { encodeNode($0, nextId: &nextId) },
                "o": 0
            ]
        case let .object(dict):
            let id = nextId
            nextId += 1
            let sortedKeys = dict.keys.sorted()
            var values: [[String: Any]] = []
            values.reserveCapacity(sortedKeys.count)
            for key in sortedKeys {
                values.append(encodeNode(dict[key] ?? .null, nextId: &nextId))
            }
            return [
                "t": 10,
                "i": id,
                "p": [
                    "k": sortedKeys,
                    "v": values
                ],
                "o": 0
            ]
        }
    }

    public static func decodeNode(data: Data) throws -> X134SerovalValue {
        guard let rawObject = try JSONSerialization.jsonObject(with: data, options: []) as? [String: Any] else {
            throw VideoFeedError.malformedResponse(source: "134X")
        }
        var refs: [Int: X134SerovalValue] = [:]
        return decode(rawObject, refs: &refs, depth: 0)
    }

    private static func decode(_ raw: Any?, refs: inout [Int: X134SerovalValue], depth: Int) -> X134SerovalValue {
        guard depth < 64 else { return .null }
        guard let dict = raw as? [String: Any], let nodeType = dict["t"] as? Int else {
            return .null
        }
        switch nodeType {
        case 0:
            if let num = dict["s"] as? NSNumber {
                return .number(num.doubleValue)
            }
            return .null
        case 1:
            return .string(unescapeSerovalString((dict["s"] as? String) ?? ""))
        case 2:
            let flag = (dict["s"] as? Int) ?? -1
            switch flag {
            case 2: return .bool(true)
            case 3: return .bool(false)
            default: return .null
            }
        case 4:
            if let refID = dict["i"] as? Int, let referenced = refs[refID] {
                return referenced
            }
            return .null
        case 9:
            let arrRaw = (dict["a"] as? [Any]) ?? []
            let items = arrRaw.map { decode($0, refs: &refs, depth: depth + 1) }
            let val = X134SerovalValue.array(items)
            if let id = dict["i"] as? Int {
                refs[id] = val
            }
            return val
        case 10, 11:
            var out: [String: X134SerovalValue] = [:]
            if let p = dict["p"] as? [String: Any],
               let keys = p["k"] as? [String],
               let values = p["v"] as? [Any] {
                let count = min(keys.count, values.count)
                out.reserveCapacity(count)
                for idx in 0..<count {
                    out[unescapeSerovalString(keys[idx])] = decode(values[idx], refs: &refs, depth: depth + 1)
                }
            }
            let val = X134SerovalValue.object(out)
            if let id = dict["i"] as? Int {
                refs[id] = val
            }
            return val
        default:
            if let str = dict["s"] as? String {
                return .string(unescapeSerovalString(str))
            }
            if let num = dict["s"] as? NSNumber {
                return .number(num.doubleValue)
            }
            return .null
        }
    }

    public static func unescapeSerovalString(_ raw: String) -> String {
        guard raw.contains("\\") else { return raw }
        var out = ""
        out.reserveCapacity(raw.count)
        let scalars = Array(raw.unicodeScalars)
        var i = 0
        while i < scalars.count {
            let current = scalars[i]
            if current == "\\", i + 1 < scalars.count {
                let next = scalars[i + 1]
                switch next {
                case "n":
                    out.append("\n")
                    i += 2
                case "r":
                    out.append("\r")
                    i += 2
                case "t":
                    out.append("\t")
                    i += 2
                case "b":
                    out.append("\u{0008}")
                    i += 2
                case "f":
                    out.append("\u{000C}")
                    i += 2
                case "\\":
                    out.append("\\")
                    i += 2
                case "\"":
                    out.append("\"")
                    i += 2
                case "'":
                    out.append("'")
                    i += 2
                case "u" where i + 5 < scalars.count:
                    let hexStr = String(scalars[(i + 2)...(i + 5)].map { Character($0) })
                    if let code = UInt32(hexStr, radix: 16), let scalar = UnicodeScalar(code) {
                        out.unicodeScalars.append(scalar)
                        i += 6
                    } else {
                        out.unicodeScalars.append(next)
                        i += 2
                    }
                case "x" where i + 3 < scalars.count:
                    let hexStr = String(scalars[(i + 2)...(i + 3)].map { Character($0) })
                    if let code = UInt32(hexStr, radix: 16), let scalar = UnicodeScalar(code) {
                        out.unicodeScalars.append(scalar)
                        i += 4
                    } else {
                        out.unicodeScalars.append(next)
                        i += 2
                    }
                default:
                    out.unicodeScalars.append(next)
                    i += 2
                }
            } else {
                out.unicodeScalars.append(current)
                i += 1
            }
        }
        return out
    }
}

/// Native adapter for https://134x.com (`最新`, `热门`, `精选`, site search, HD poster hydration, and HLS/MP4 playback).
public actor X134SourceService {
    public static let shared = X134SourceService()
    public static let baseURL = "https://134x.com"
    private static let userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
    private static let metaTTL: TimeInterval = 15 * 60
    private static let maxMetaEntries = 512
    private static let maxCursorEntries = 128

    public struct ServerFnHashes: Sendable, Equatable {
        public let latest: String
        public let ranking: String
        public let search: String
        public let detail: String

        public init(latest: String, ranking: String, search: String, detail: String) {
            self.latest = latest
            self.ranking = ranking
            self.search = search
            self.detail = detail
        }
    }

    public struct TweetMetadata: Sendable {
        public let posterUrl: String?
        public let hlsUrl: String?
        public let mp4Url: String?
        public let updatedAt: Date
    }

    public static let defaultHashes = ServerFnHashes(
        latest: "7cd2fdc202217221aea24ce98c8911133744658e2b912656f645ea53849d6f17",
        ranking: "25c25b734ef2228ea3f8f49f4067988ab39ff3e50799a02452f080931c889002",
        search: "51bd2bec8cfd50e218d50fb96a0f5a94bbdeb6e6835dec38fab33818af78324d",
        detail: "9c9bc9391d885f2f66683866157820d7de7d05082825aa01d1999a181d3c1e99"
    )

    private var activeHashes = defaultHashes
    private var metaCache: [String: TweetMetadata] = [:]
    private var metaInsertionOrder: [String] = []
    private var nextCursorCache: [String: String] = [:]
    private let session: URLSession

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.timeoutIntervalForResource = 30
        config.httpMaximumConnectionsPerHost = 8
        config.httpAdditionalHeaders = ["User-Agent": Self.userAgent]
        self.session = URLSession.permissive(configuration: config)
    }

    public func fetchFeed(category: String, page: Int = 1) async throws -> [VideoItem] {
        let safePage = max(page, 1)
        let ranking: String?
        if category.contains("热") || category.contains("排行") {
            ranking = "popular"
        } else if category.contains("精") || category.contains("推") {
            ranking = "featured"
        } else {
            ranking = nil
        }

        let modeKey = ranking ?? "latest"
        var dataFields: [String: X134SerovalValue] = [
            "page": .number(Double(safePage))
        ]
        if let ranking {
            dataFields["ranking"] = .string(ranking)
        }
        if safePage > 1, let cursor = nextCursorCache["\(modeKey):\(safePage - 1)"] {
            dataFields["cursor"] = .string(cursor)
            dataFields["direction"] = .string("next")
        }

        let result = try await callServerFnWithRefresh(
            fnSelector: { ranking == nil ? $0.latest : $0.ranking },
            data: dataFields
        )

        if let nextCursor = result["nextCursor"]?.stringValue, !nextCursor.isEmpty {
            recordCursor(key: "\(modeKey):\(safePage)", cursor: nextCursor)
        }
        let rawTweets = result["tweets"]?.arrayValue ?? []
        let hydrated = await hydrateMetadata(for: rawTweets)
        let items = Self.parseFeedItems(rawTweets: rawTweets, metadata: hydrated, category: category)
        if safePage == 1 && items.isEmpty {
            throw VideoFeedError.malformedResponse(source: "134X")
        }
        return items
    }

    public func search(query: String, page: Int = 1) async throws -> [VideoItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        let safePage = max(page, 1)
        let result = try await callServerFnWithRefresh(
            fnSelector: { $0.search },
            data: [
                "query": .string(trimmed),
                "page": .number(Double(safePage))
            ]
        )
        let rawTweets = result["tweets"]?.arrayValue ?? []
        let hydrated = await hydrateMetadata(for: rawTweets)
        return Self.parseFeedItems(rawTweets: rawTweets, metadata: hydrated, category: "搜索")
    }

    public func resolvePlayback(pageUrl: String, title: String) async throws -> PlaybackSourceResult {
        guard let tweetId = Self.extractTweetID(from: pageUrl) else {
            throw VideoFeedError.invalidURL(source: "134X")
        }

        if let cached = freshMetadata(for: tweetId),
           let streamString = cached.hlsUrl?.nonEmptyOrNil ?? cached.mp4Url?.nonEmptyOrNil,
           let streamURL = URL(string: streamString) {
            return PlaybackSourceResult(url: streamURL, referer: "\(Self.baseURL)/", title: title)
        }

        if let fetched = try? await fetchTweetMetadata(tweetId: tweetId, fallbackMp4: nil),
           let streamString = fetched.hlsUrl?.nonEmptyOrNil ?? fetched.mp4Url?.nonEmptyOrNil,
           let streamURL = URL(string: streamString) {
            return PlaybackSourceResult(url: streamURL, referer: "\(Self.baseURL)/", title: title)
        }

        if let htmlStream = await resolveStreamFromDetailHTML(tweetId: tweetId),
           let streamURL = URL(string: htmlStream) {
            return PlaybackSourceResult(url: streamURL, referer: "\(Self.baseURL)/", title: title)
        }

        throw VideoFeedError.malformedResponse(source: "134X")
    }

    private func hydrateMetadata(for rawTweets: [X134SerovalValue]) async -> [String: TweetMetadata] {
        struct PendingItem: Sendable {
            let tweetId: String
            let listMp4: String?
        }

        var output: [String: TweetMetadata] = [:]
        var pending: [PendingItem] = []

        for node in rawTweets {
            guard let tweetId = node["tweetId"]?.stringValue?.trimmingCharacters(in: .whitespacesAndNewlines),
                  !tweetId.isEmpty else { continue }
            let listMp4 = node["media"]?.stringValue?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmptyOrNil
            let existingPoster = node["posterUrl"]?.stringValue?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmptyOrNil
            let existingHls = Self.firstNonEmptyStringValue(in: node["hlsUrls"])
            let cached = freshMetadata(for: tweetId)

            if let existingPoster {
                let merged = TweetMetadata(
                    posterUrl: existingPoster,
                    hlsUrl: existingHls ?? cached?.hlsUrl,
                    mp4Url: listMp4 ?? cached?.mp4Url,
                    updatedAt: Date()
                )
                storeMetadata(merged, for: tweetId)
                output[tweetId] = merged
            } else if let cached, cached.posterUrl?.nonEmptyOrNil != nil {
                let merged = TweetMetadata(
                    posterUrl: cached.posterUrl,
                    hlsUrl: cached.hlsUrl,
                    mp4Url: listMp4 ?? cached.mp4Url,
                    updatedAt: cached.updatedAt
                )
                storeMetadata(merged, for: tweetId)
                output[tweetId] = merged
            } else {
                pending.append(PendingItem(tweetId: tweetId, listMp4: listMp4))
            }
        }

        guard !pending.isEmpty else { return output }

        let maxConcurrent = 6
        await withTaskGroup(of: (String, TweetMetadata?).self) { group in
            var index = 0
            while index < min(maxConcurrent, pending.count) {
                let item = pending[index]
                index += 1
                group.addTask {
                    let fetched = await self.fetchTweetMetadataWithTimeout(tweetId: item.tweetId, fallbackMp4: item.listMp4)
                    return (item.tweetId, fetched)
                }
            }
            while let (tweetId, meta) = await group.next() {
                if let meta {
                    storeMetadata(meta, for: tweetId)
                    output[tweetId] = meta
                }
                if index < pending.count {
                    let nextItem = pending[index]
                    index += 1
                    group.addTask {
                        let fetched = await self.fetchTweetMetadataWithTimeout(tweetId: nextItem.tweetId, fallbackMp4: nextItem.listMp4)
                        return (nextItem.tweetId, fetched)
                    }
                }
            }
        }
        return output
    }

    private func fetchTweetMetadataWithTimeout(tweetId: String, fallbackMp4: String?) async -> TweetMetadata? {
        await withTaskGroup(of: TweetMetadata?.self) { group in
            group.addTask {
                (try? await self.fetchTweetMetadata(tweetId: tweetId, fallbackMp4: fallbackMp4))
                    ?? TweetMetadata(posterUrl: nil, hlsUrl: nil, mp4Url: fallbackMp4, updatedAt: Date())
            }
            group.addTask {
                try? await Task.sleep(nanoseconds: 4_500_000_000)
                return TweetMetadata(posterUrl: nil, hlsUrl: nil, mp4Url: fallbackMp4, updatedAt: Date())
            }
            let first = await group.next() ?? nil
            group.cancelAll()
            return first
        }
    }

    private func fetchTweetMetadata(tweetId: String, fallbackMp4: String?) async throws -> TweetMetadata {
        let result = try await callServerFnWithRefresh(
            fnSelector: { $0.detail },
            data: ["tweetId": .string(tweetId)]
        )
        let posterUrl = result["posterUrl"]?.stringValue?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmptyOrNil
        let hlsDict = result["hlsUrls"]?.objectValue
        let hlsUrl = hlsDict?.values.compactMap { $0.stringValue?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmptyOrNil }.first
        let hlsKeyMp4 = hlsDict?.keys.first(where: { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty })
        let bestVariantMp4 = Self.extractBestVariantMp4(from: result["videoVariants"])
        let prev = freshMetadata(for: tweetId)
        let merged = TweetMetadata(
            posterUrl: posterUrl ?? prev?.posterUrl,
            hlsUrl: hlsUrl ?? prev?.hlsUrl,
            mp4Url: hlsKeyMp4 ?? bestVariantMp4 ?? fallbackMp4 ?? prev?.mp4Url,
            updatedAt: Date()
        )
        storeMetadata(merged, for: tweetId)
        return merged
    }

    private func callServerFnWithRefresh(
        fnSelector: @Sendable (ServerFnHashes) -> String,
        data: [String: X134SerovalValue]
    ) async throws -> X134SerovalValue {
        let currentHash = fnSelector(activeHashes)
        do {
            return try await executeServerFn(fnID: currentHash, data: data)
        } catch let error as VideoFeedError {
            guard case let .httpStatus(_, code) = error, [400, 404, 500].contains(code) else {
                throw error
            }
            if let refreshed = await refreshHashesFromLiveBundle() {
                let nextHash = fnSelector(refreshed)
                if nextHash != currentHash {
                    return try await executeServerFn(fnID: nextHash, data: data)
                }
            }
            throw error
        }
    }

    private func executeServerFn(fnID: String, data: [String: X134SerovalValue]) async throws -> X134SerovalValue {
        let payloadString = try X134SerovalCodec.encodePayload(.object(["data": .object(data)]))
        guard var components = URLComponents(string: "\(Self.baseURL)/_serverFn/\(fnID)") else {
            throw VideoFeedError.invalidURL(source: "134X")
        }
        components.queryItems = [URLQueryItem(name: "payload", value: payloadString)]
        guard let url = components.url else {
            throw VideoFeedError.invalidURL(source: "134X")
        }

        var request = URLRequest(url: url)
        request.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("\(Self.baseURL)/", forHTTPHeaderField: "Referer")
        request.setValue("same-origin", forHTTPHeaderField: "Sec-Fetch-Site")
        request.setValue("cors", forHTTPHeaderField: "Sec-Fetch-Mode")
        request.setValue("empty", forHTTPHeaderField: "Sec-Fetch-Dest")
        request.setValue("true", forHTTPHeaderField: "x-tsr-serverFn")

        let (responseData, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw VideoFeedError.invalidResponse(source: "134X")
        }
        guard (200...299).contains(http.statusCode) else {
            throw VideoFeedError.httpStatus(source: "134X", statusCode: http.statusCode)
        }
        let decoded = try X134SerovalCodec.decodeNode(data: responseData)
        if let err = decoded["error"], err != .null {
            throw VideoFeedError.malformedResponse(source: "134X")
        }
        guard let result = decoded["result"], result != .null else {
            throw VideoFeedError.malformedResponse(source: "134X")
        }
        return result
    }

    private func refreshHashesFromLiveBundle() async -> ServerFnHashes? {
        guard let homeURL = URL(string: "\(Self.baseURL)/") else { return nil }
        var homeReq = URLRequest(url: homeURL)
        homeReq.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        homeReq.setValue("text/html,application/xhtml+xml;q=0.9,*/*;q=0.8", forHTTPHeaderField: "Accept")

        guard let (homeData, homeResp) = try? await session.data(for: homeReq),
              let homeHTTP = homeResp as? HTTPURLResponse,
              (200...299).contains(homeHTTP.statusCode),
              let homeHTML = String(data: homeData, encoding: .utf8),
              let bundlePath = Self.extractAppShellPath(fromHTML: homeHTML),
              let bundleURL = URL(string: "\(Self.baseURL)/\(bundlePath.trimmingCharacters(in: CharacterSet(charactersIn: "/")))") else {
            return nil
        }

        var jsReq = URLRequest(url: bundleURL)
        jsReq.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        jsReq.setValue("\(Self.baseURL)/", forHTTPHeaderField: "Referer")
        guard let (jsData, jsResp) = try? await session.data(for: jsReq),
              let jsHTTP = jsResp as? HTTPURLResponse,
              (200...299).contains(jsHTTP.statusCode),
              let jsText = String(data: jsData, encoding: .utf8),
              let parsed = Self.extractHashes(fromAppShellJS: jsText) else {
            return nil
        }
        activeHashes = parsed
        return parsed
    }

    private func resolveStreamFromDetailHTML(tweetId: String) async -> String? {
        guard let url = URL(string: "\(Self.baseURL)/video/\(tweetId)") else { return nil }
        var request = URLRequest(url: url)
        request.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("\(Self.baseURL)/", forHTTPHeaderField: "Referer")
        guard let (data, resp) = try? await session.data(for: request),
              let http = resp as? HTTPURLResponse,
              (200...299).contains(http.statusCode),
              let html = String(data: data, encoding: .utf8) else {
            return nil
        }
        return Self.extractMediaURLFromHTML(html)
    }

    // MARK: - Helpers & Pure Parsing

    private func freshMetadata(for tweetId: String) -> TweetMetadata? {
        guard let item = metaCache[tweetId] else { return nil }
        if Date().timeIntervalSince(item.updatedAt) > Self.metaTTL {
            metaCache.removeValue(forKey: tweetId)
            return nil
        }
        return item
    }

    private func storeMetadata(_ meta: TweetMetadata, for tweetId: String) {
        if metaCache[tweetId] == nil {
            metaInsertionOrder.append(tweetId)
            if metaInsertionOrder.count > Self.maxMetaEntries {
                let evicted = metaInsertionOrder.removeFirst()
                metaCache.removeValue(forKey: evicted)
            }
        }
        metaCache[tweetId] = meta
    }

    private func recordCursor(key: String, cursor: String) {
        if nextCursorCache.count >= Self.maxCursorEntries, let firstKey = nextCursorCache.keys.first {
            nextCursorCache.removeValue(forKey: firstKey)
        }
        nextCursorCache[key] = cursor
    }

    public static func extractAppShellPath(fromHTML html: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: #"["']/?(assets/AppShell-[A-Za-z0-9_-]+\.js)["']"#),
              let match = regex.firstMatch(in: html, range: NSRange(html.startIndex..., in: html)),
              match.numberOfRanges > 1,
              let range = Range(match.range(at: 1), in: html) else {
            return nil
        }
        return String(html[range])
    }

    public static func extractHashes(fromAppShellJS jsText: String) -> ServerFnHashes? {
        guard let regex = try? NSRegularExpression(pattern: #"handler\([A-Za-z0-9_$]+\([`"']([0-9a-f]{64})[`"']\)\)"#) else {
            return nil
        }
        let matches = regex.matches(in: jsText, range: NSRange(jsText.startIndex..., in: jsText))
        let hashes: [String] = matches.compactMap { match in
            guard match.numberOfRanges > 1, let range = Range(match.range(at: 1), in: jsText) else { return nil }
            return String(jsText[range])
        }
        guard hashes.count >= 8 else { return nil }
        return ServerFnHashes(
            latest: hashes[1],
            ranking: hashes[3],
            search: hashes[4],
            detail: hashes[7]
        )
    }

    public static func extractMediaURLFromHTML(_ html: String) -> String? {
        for ext in ["m3u8", "mp4"] {
            let pattern = #"https://video\.twimg\.com/[^"'\s<>\\]+\."# + ext + #"[^"'\s<>\\]*"#
            if let regex = try? NSRegularExpression(pattern: pattern),
               let match = regex.firstMatch(in: html, range: NSRange(html.startIndex..., in: html)),
               let range = Range(match.range, in: html) {
                return String(html[range])
            }
        }
        return nil
    }

    public static func parseFeedItems(
        rawTweets: [X134SerovalValue],
        metadata: [String: TweetMetadata] = [:],
        category: String = "最新"
    ) -> [VideoItem] {
        var seen = Set<String>()
        var items: [VideoItem] = []

        for tweet in rawTweets {
            guard let tweetId = tweet["tweetId"]?.stringValue?.trimmingCharacters(in: .whitespacesAndNewlines),
                  !tweetId.isEmpty else { continue }
            let pageUrl = "\(baseURL)/video/\(tweetId)"
            guard !seen.contains(pageUrl) else { continue }
            seen.insert(pageUrl)

            let name = tweet["name"]?.stringValue?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let screenName = tweet["screenName"]?.stringValue?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let author: String
            if !name.isEmpty {
                author = name
            } else if !screenName.isEmpty {
                author = "@\(screenName)"
            } else {
                author = "134X"
            }

            let rawText = tweet["text"]?.stringValue ?? ""
            let title = cleanTweetTitle(rawText, fallbackAuthor: author)
            let durationMillis = tweet["durationMillis"]?.int64Value ?? 0
            let playTimes = tweet["playTimes"]?.int64Value ?? 0
            let meta = metadata[tweetId]
            let avatarFallback = tweet["profileImage"]?.stringValue?
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .replacingOccurrences(of: "_normal.", with: "_400x400.")
                .nonEmptyOrNil
            let coverUrl = meta?.posterUrl?.nonEmptyOrNil
                ?? tweet["posterUrl"]?.stringValue?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmptyOrNil
                ?? avatarFallback
                ?? ""

            let durationText = formatDurationMillis(durationMillis)
            let viewsText = formatPlayTimes(playTimes)
            let isLong = durationMillis >= 3_600_000

            items.append(
                VideoItem(
                    id: "134x-\(tweetId)",
                    title: title,
                    subtitle: "134X · \(author)",
                    coverUrl: coverUrl,
                    playbackUrl: pageUrl,
                    duration: durationText,
                    rating: viewsText,
                    tags: isLong ? ["134X", "长视频", category] : ["134X", category],
                    category: category,
                    isTop10: false,
                    rankNumber: nil,
                    sourceName: "134X"
                )
            )
        }
        return items
    }

    public static func extractTweetID(from pageUrl: String) -> String? {
        for pattern in [#"(?:134x\.com/video/|tweetId=)(\d+)"#, #"/(\d{10,})(?:[/?#]|$)"#] {
            if let regex = try? NSRegularExpression(pattern: pattern),
               let match = regex.firstMatch(in: pageUrl, range: NSRange(pageUrl.startIndex..., in: pageUrl)),
               match.numberOfRanges > 1,
               let range = Range(match.range(at: 1), in: pageUrl) {
                return String(pageUrl[range])
            }
        }
        return nil
    }

    public static func cleanTweetTitle(_ rawText: String, fallbackAuthor: String) -> String {
        let stripped = rawText
            .replacingOccurrences(of: #"\\r\\n|\\n|\\r|\\t"#, with: " ", options: .regularExpression)
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "&apos;", with: "'")
            .replacingOccurrences(of: #"https?://t\.co/\S+"#, with: " ", options: .regularExpression)
            .replacingOccurrences(of: #"[\r\n\t]+"#, with: " ", options: .regularExpression)
            .replacingOccurrences(of: #"\s{2,}"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if !stripped.isEmpty { return stripped }
        return fallbackAuthor.isEmpty ? "134X 视频" : fallbackAuthor
    }

    public static func formatDurationMillis(_ durationMillis: Int64) -> String {
        guard durationMillis > 0 else { return "--:--" }
        let totalSeconds = durationMillis / 1000
        let hours = totalSeconds / 3600
        let minutes = (totalSeconds % 3600) / 60
        let seconds = totalSeconds % 60
        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, seconds)
        }
        return String(format: "%02d:%02d", minutes, seconds)
    }

    public static func formatPlayTimes(_ playTimes: Int64) -> String {
        if playTimes <= 0 { return "长视频" }
        if playTimes >= 10_000 {
            return String(format: "%.1f万", Double(playTimes) / 10_000.0)
        }
        return "\(playTimes)"
    }

    private static func firstNonEmptyStringValue(in node: X134SerovalValue?) -> String? {
        guard let dict = node?.objectValue else { return nil }
        return dict.values.compactMap { $0.stringValue?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmptyOrNil }.first
    }

    private static func extractBestVariantMp4(from node: X134SerovalValue?) -> String? {
        guard let dict = node?.objectValue else { return nil }
        var bestURL: String?
        var bestScore: Int64 = -1
        for value in dict.values {
            guard let items = value.arrayValue else { continue }
            for entry in items {
                guard let url = entry["url"]?.stringValue?.trimmingCharacters(in: .whitespacesAndNewlines),
                      url.hasPrefix("http") else { continue }
                let width = entry["width"]?.int64Value ?? 0
                let bitrate = entry["bitrate"]?.int64Value ?? 0
                let score = width * 1_000_000 + bitrate
                if score > bestScore {
                    bestScore = score
                    bestURL = url
                }
            }
        }
        return bestURL
    }
}

private extension String {
    var nonEmptyOrNil: String? {
        isEmpty ? nil : self
    }
}
