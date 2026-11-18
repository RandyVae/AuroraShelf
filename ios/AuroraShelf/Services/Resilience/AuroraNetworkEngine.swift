import Foundation

public struct AuroraNetworkResponse: Sendable {
    public let data: Data
    public let statusCode: Int
    public let finalURL: URL
    public let latencyMs: Int

    public init(data: Data, statusCode: Int, finalURL: URL, latencyMs: Int) {
        self.data = data
        self.statusCode = statusCode
        self.finalURL = finalURL
        self.latencyMs = latencyMs
    }
}

public enum AuroraNetworkError: LocalizedError, Sendable {
    case invalidResponse
    case httpStatus(Int, URL)
    case cloudflareChallenge(URL)
    case validationFailed(URL)
    case allCandidatesFailed(String)

    public var errorDescription: String? {
        switch self {
        case .invalidResponse:
            return "源站未返回有效的 HTTP 响应。"
        case .httpStatus(let code, let url):
            return "源站请求失败（HTTP \(code) · \(url.host ?? "")）。"
        case .cloudflareChallenge(let url):
            return "源站触发 Cloudflare 人机验证保护（\(url.host ?? "")）。"
        case .validationFailed(let url):
            return "源站返回数据校验失败（\(url.host ?? "")）。"
        case .allCandidatesFailed(let detail):
            return detail.isEmpty ? "所有候选镜像站均无法连接。" : detail
        }
    }
}

/// 全端统一连接池与阶梯对冲竞速网络引擎（iOS/iPadOS）。
/// 1. 共享 `PermissiveURLSessionDelegate`（最大并发 12/host，HTTP/2 多路复用，TLS 自愈）；
/// 2. 针对 `NSURLErrorNetworkConnectionLost(-1005)` / 超时等瞬时断流自动无缝重试；
/// 3. 阶梯对冲竞速（`hedgeDelayMs = 1100`）：正常节点零多余请求，慢/坏节点 1.1 秒内触发镜像竞速并取消慢者！
public final class AuroraNetworkEngine: @unchecked Sendable {
    public static let shared = AuroraNetworkEngine()

    public let session: URLSession
    private let registry: SourceHealthRegistry

    public init(registry: SourceHealthRegistry = .shared) {
        self.registry = registry
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 10
        config.timeoutIntervalForResource = 18
        config.httpMaximumConnectionsPerHost = 12
        config.waitsForConnectivity = false
        config.urlCache = URLCache(memoryCapacity: 32 * 1024 * 1024, diskCapacity: 128 * 1024 * 1024)
        config.httpAdditionalHeaders = [
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        ]
        self.session = URLSession.permissive(configuration: config)
    }

    public func fetch(
        url: URL,
        sourceKey: String? = nil,
        method: String = "GET",
        body: Data? = nil,
        headers: [String: String] = [:],
        timeout: TimeInterval = 12,
        learnRedirectMirror: Bool = true
    ) async throws -> AuroraNetworkResponse {
        let resolvedKey = sourceKey ?? SourceHealthRegistry.sourceKey(for: url)
        var lastError: Error?

        for attempt in 0..<2 {
            var request = URLRequest(url: url, timeoutInterval: timeout)
            request.httpMethod = method
            request.httpBody = body
            headers.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }

            let startedAt = Date()
            do {
                let (data, response) = try await session.data(for: request)
                let elapsedMs = Int(Date().timeIntervalSince(startedAt) * 1000)
                guard let http = response as? HTTPURLResponse else {
                    throw AuroraNetworkError.invalidResponse
                }
                let finalURL = http.url ?? url
                if (200...299).contains(http.statusCode) {
                    registry.recordSuccess(
                        sourceKey: resolvedKey,
                        urlOrHost: url.absoluteString,
                        latencyMs: elapsedMs,
                        finalRedirectURL: learnRedirectMirror ? finalURL.absoluteString : nil
                    )
                    return AuroraNetworkResponse(
                        data: data,
                        statusCode: http.statusCode,
                        finalURL: finalURL,
                        latencyMs: elapsedMs
                    )
                }

                let isHardBlock = [403, 502, 503, 504].contains(http.statusCode)
                registry.recordFailure(sourceKey: resolvedKey, urlOrHost: url.absoluteString, statusCode: http.statusCode, isHardBlock: isHardBlock)
                if attempt == 0 && [502, 503, 504, 429].contains(http.statusCode) {
                    continue
                }
                if http.statusCode == 403,
                   http.value(forHTTPHeaderField: "cf-mitigated")?.lowercased() == "challenge" {
                    throw AuroraNetworkError.cloudflareChallenge(finalURL)
                }
                throw AuroraNetworkError.httpStatus(http.statusCode, finalURL)
            } catch let error as AuroraNetworkError {
                throw error
            } catch let urlErr as URLError where urlErr.code == .cancelled {
                throw CancellationError()
            } catch {
                lastError = error
                if attempt == 0 && Self.isTransientNetworkError(error) {
                    continue
                }
                registry.recordFailure(sourceKey: resolvedKey, urlOrHost: url.absoluteString, statusCode: 0, isHardBlock: true)
                throw error
            }
        }
        throw lastError ?? AuroraNetworkError.invalidResponse
    }

    public typealias HedgedExecutor = @Sendable (URL, [String: String]) async throws -> AuroraNetworkResponse
    public typealias ResponseValidator = @Sendable (AuroraNetworkResponse) -> Bool

    private enum HedgeEvent: Sendable {
        case candidateFinished(Int, Result<AuroraNetworkResponse, Error>)
        case hedgeTimerFired
    }

    public func fetchHedged(
        sourceKey: String?,
        candidateURLs: [URL],
        hedgeDelayMs: UInt64 = 1_100,
        timeout: TimeInterval = 12,
        headersBuilder: @Sendable (URL) -> [String: String] = { _ in [:] },
        validator: ResponseValidator? = nil,
        executor: HedgedExecutor? = nil
    ) async throws -> AuroraNetworkResponse {
        let rankedStrings = registry.rankCandidates(sourceKey: sourceKey, candidates: candidateURLs.map(\.absoluteString))
        let ranked = rankedStrings.compactMap(URL.init(string:))
        guard !ranked.isEmpty else {
            throw AuroraNetworkError.allCandidatesFailed("候选地址列表为空。")
        }

        let performAttempt: HedgedExecutor = executor ?? { [self] targetURL, hdrs in
            try await self.fetch(url: targetURL, sourceKey: sourceKey, headers: hdrs, timeout: timeout)
        }

        if ranked.count == 1 {
            let target = ranked[0]
            let response = try await performAttempt(target, headersBuilder(target))
            if let validator, !validator(response) {
                registry.recordFailure(sourceKey: sourceKey, urlOrHost: target.absoluteString, statusCode: response.statusCode)
                throw AuroraNetworkError.validationFailed(target)
            }
            return response
        }

        return try await withThrowingTaskGroup(of: HedgeEvent.self, returning: AuroraNetworkResponse.self) { group in
            var nextToLaunch = 1
            var activeCandidates = 1
            var lastError: Error?

            let firstURL = ranked[0]
            let firstHeaders = headersBuilder(firstURL)
            group.addTask {
                do {
                    let resp = try await performAttempt(firstURL, firstHeaders)
                    if let validator, !validator(resp) {
                        return .candidateFinished(0, .failure(AuroraNetworkError.validationFailed(firstURL)))
                    }
                    return .candidateFinished(0, .success(resp))
                } catch {
                    return .candidateFinished(0, .failure(error))
                }
            }

            group.addTask {
                try? await Task.sleep(nanoseconds: hedgeDelayMs * 1_000_000)
                return .hedgeTimerFired
            }

            while let event = try await group.next() {
                switch event {
                case .candidateFinished(_, let result):
                    activeCandidates -= 1
                    switch result {
                    case .success(let winner):
                        group.cancelAll()
                        return winner
                    case .failure(let err):
                        if Task.isCancelled {
                            group.cancelAll()
                            throw CancellationError()
                        }
                        lastError = err
                        if nextToLaunch < ranked.count {
                            let idx = nextToLaunch
                            nextToLaunch += 1
                            activeCandidates += 1
                            let nextURL = ranked[idx]
                            let nextHeaders = headersBuilder(nextURL)
                            group.addTask {
                                do {
                                    let resp = try await performAttempt(nextURL, nextHeaders)
                                    if let validator, !validator(resp) {
                                        return .candidateFinished(idx, .failure(AuroraNetworkError.validationFailed(nextURL)))
                                    }
                                    return .candidateFinished(idx, .success(resp))
                                } catch {
                                    return .candidateFinished(idx, .failure(error))
                                }
                            }
                            if nextToLaunch < ranked.count {
                                group.addTask {
                                    try? await Task.sleep(nanoseconds: hedgeDelayMs * 1_000_000)
                                    return .hedgeTimerFired
                                }
                            }
                        } else if activeCandidates == 0 {
                            group.cancelAll()
                            throw lastError ?? AuroraNetworkError.allCandidatesFailed("")
                        }
                    }

                case .hedgeTimerFired:
                    if nextToLaunch < ranked.count {
                        let idx = nextToLaunch
                        nextToLaunch += 1
                        activeCandidates += 1
                        let nextURL = ranked[idx]
                        let nextHeaders = headersBuilder(nextURL)
                        group.addTask {
                            do {
                                let resp = try await performAttempt(nextURL, nextHeaders)
                                if let validator, !validator(resp) {
                                    return .candidateFinished(idx, .failure(AuroraNetworkError.validationFailed(nextURL)))
                                }
                                return .candidateFinished(idx, .success(resp))
                            } catch {
                                return .candidateFinished(idx, .failure(error))
                            }
                        }
                    }
                }
            }

            throw lastError ?? AuroraNetworkError.allCandidatesFailed("")
        }
    }

    public func prewarmOrigins(_ urls: [URL]) {
        for url in urls {
            Task.detached(priority: .utility) { [weak self] in
                guard let self else { return }
                var req = URLRequest(url: url, timeoutInterval: 4)
                req.httpMethod = "HEAD"
                let startedAt = Date()
                if let (_, response) = try? await self.session.data(for: req),
                   let http = response as? HTTPURLResponse,
                   (200...399).contains(http.statusCode) {
                    let ms = Int(Date().timeIntervalSince(startedAt) * 1000)
                    self.registry.recordSuccess(
                        sourceKey: SourceHealthRegistry.sourceKey(for: url),
                        urlOrHost: url.absoluteString,
                        latencyMs: ms
                    )
                }
            }
        }
    }

    private static func isTransientNetworkError(_ error: Error) -> Bool {
        let nsError = error as NSError
        return nsError.domain == NSURLErrorDomain && [
            NSURLErrorNetworkConnectionLost,
            NSURLErrorTimedOut,
            NSURLErrorCannotConnectToHost,
            NSURLErrorDNSLookupFailed
        ].contains(nsError.code)
    }
}
