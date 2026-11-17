import Foundation

/// 端侧源站与 CDN 健康度注册中心（EWMA 延迟统计、指数退避熔断与动态镜像池）。
/// 100% 对齐 Android `SourceHealthRegistry`，并在纯单测下支持独立 UserDefaults 与注入时钟。
public final class SourceHealthRegistry: @unchecked Sendable {
    public static let shared = SourceHealthRegistry()

    public struct HostHealth: Codable, Sendable, Equatable {
        public let host: String
        public var ewmaLatencyMs: Double
        public var consecutiveFailures: Int
        public var cooldownUntilEpoch: TimeInterval
        public var totalSuccesses: Int
        public var totalFailures: Int
        public var lastStatusCode: Int
        public var updatedAtEpoch: TimeInterval
    }

    private struct PersistedState: Codable {
        var healthByHost: [String: HostHealth]
        var learnedMirrorsBySource: [String: [String]]
    }

    private let lock = NSLock()
    private let defaults: UserDefaults?
    private let storageKey = "aurora_source_health_v1"
    private let clock: @Sendable () -> Date

    private var healthByHost: [String: HostHealth] = [:]
    private var learnedMirrorsBySource: [String: [String]] = [:]
    private var manifestMirrorsBySource: [String: [String]] = [:]

    private static let ewmaAlpha: Double = 0.30
    private static let defaultUnprobedEwmaMs: Double = 450.0
    private static let circuitFailureThreshold = 2
    private static let baseCooldownSec: TimeInterval = 30
    private static let maxCooldownSec: TimeInterval = 300
    private static let maxPersistedHosts = 64
    private static let maxMirrorsPerSource = 8

    public static let builtInMirrors: [String: [String]] = [
        "copy": ["https://api.mangacopy.com", "https://api.2024manga.com"],
        "91porn": ["https://h1014.sol148.com", "https://91porn.com"],
        "pornhub": ["https://cn.pornhub.com", "https://jp.pornhub.com", "https://www.pornhub.com"],
        "missav": ["https://missav.media", "https://missav123.com", "https://missav.ws", "https://missav.ai"],
        "jm": ["https://www.cdnhjk.net", "https://www.cdngwc.net", "https://www.cdngwc.club", "https://www.cdngwc.cc"],
        "t906": ["https://t924.sol140.com", "https://t925.sol140.com", "https://t906.abc2507.cc"],
        "baozi": ["https://cn.webmota.com", "https://cn.twbzmg.com", "https://www.baozimh.com"],
        "baozi-cdn": ["https://s1.baozicdn.com", "https://s2.baozicdn.com", "https://static-tw.baozimh.com", "https://s1.bzcdn.net", "https://s2.bzcdn.net"],
        "jm-cdn": ["https://cdn-msp.jmapiproxy2.cc", "https://cdn-msp2.jmapiproxy2.cc", "https://cdn-msp3.jmapiproxy2.cc", "https://cdn-msp.jmapinodeudzn.net", "https://cdn-msp.jmapiproxy1.cc"],
    ]

    public init(defaults: UserDefaults? = .standard, clock: @escaping @Sendable () -> Date = Date.init) {
        self.defaults = defaults
        self.clock = clock
        loadPersistedState()
    }

    private func loadPersistedState() {
        guard let defaults,
              let data = defaults.data(forKey: storageKey),
              let state = try? JSONDecoder().decode(PersistedState.self, from: data) else {
            return
        }
        lock.lock()
        self.healthByHost = state.healthByHost
        self.learnedMirrorsBySource = state.learnedMirrorsBySource
        lock.unlock()
    }

    private func persistStateLocked() {
        guard let defaults else { return }
        let sortedHosts = healthByHost.values.sorted { $0.updatedAtEpoch > $1.updatedAtEpoch }.prefix(Self.maxPersistedHosts)
        var trimmedHealth: [String: HostHealth] = [:]
        for entry in sortedHosts {
            trimmedHealth[entry.host] = entry
        }
        let state = PersistedState(
            healthByHost: trimmedHealth,
            learnedMirrorsBySource: learnedMirrorsBySource
        )
        if let data = try? JSONEncoder().encode(state) {
            defaults.set(data, forKey: storageKey)
        }
    }

    public func applyManifestMirrors(_ mirrorsBySource: [String: [String]]) {
        lock.lock()
        for (key, list) in mirrorsBySource {
            var deduped: [String] = []
            var seen = Set<String>()
            for raw in list {
                if let clean = Self.normalizeOrigin(raw), seen.insert(clean).inserted {
                    deduped.append(clean)
                }
            }
            if !deduped.isEmpty {
                manifestMirrorsBySource[key] = deduped
            }
        }
        lock.unlock()
    }

    public func registerLearnedMirror(sourceKey: String, originURL: String) {
        guard let clean = Self.normalizeOrigin(originURL) else { return }
        lock.lock()
        var current = learnedMirrorsBySource[sourceKey] ?? []
        if current.first != clean {
            current.removeAll { $0 == clean }
            current.insert(clean, at: 0)
            if current.count > Self.maxMirrorsPerSource {
                current = Array(current.prefix(Self.maxMirrorsPerSource))
            }
            learnedMirrorsBySource[sourceKey] = current
            persistStateLocked()
            print("[AuroraHealth] learned mirror [\(sourceKey)] -> \(clean)")
        }
        lock.unlock()
    }

    public func knownHosts(for sourceKey: String) -> Set<String> {
        let origins = resolveOrigins(sourceKey: sourceKey)
        return Set(origins.compactMap(Self.extractHost))
    }

    public func resolveOrigins(sourceKey: String, defaults defaultOrigins: [String] = []) -> [String] {
        lock.lock()
        let learned = learnedMirrorsBySource[sourceKey] ?? []
        let manifest = manifestMirrorsBySource[sourceKey] ?? []
        lock.unlock()

        var merged: [String] = []
        var seen = Set<String>()
        for candidate in (learned + defaultOrigins + (Self.builtInMirrors[sourceKey] ?? []) + manifest) {
            if let norm = Self.normalizeOrigin(candidate), seen.insert(norm).inserted {
                merged.append(norm)
            }
        }
        return rankCandidates(sourceKey: sourceKey, candidates: merged)
    }

    public func expandCandidateURLs(sourceKey: String?, requestURL: URL) -> [URL] {
        guard let sourceKey, !sourceKey.isEmpty,
              let requestOrigin = Self.normalizeOrigin(requestURL.absoluteString) else {
            return [requestURL]
        }
        let origins = resolveOrigins(sourceKey: sourceKey, defaults: [requestOrigin])
        guard !origins.isEmpty else { return [requestURL] }

        var urls: [URL] = []
        var seen = Set<String>()
        let originalComponents = URLComponents(url: requestURL, resolvingAgainstBaseURL: true)

        for origin in origins {
            if let originURL = URL(string: origin),
               var comps = originalComponents {
                comps.scheme = originURL.scheme ?? "https"
                comps.host = originURL.host
                comps.port = originURL.port
                if let built = comps.url, seen.insert(built.absoluteString).inserted {
                    urls.append(built)
                }
            }
        }
        if seen.insert(requestURL.absoluteString).inserted {
            urls.append(requestURL)
        }
        return urls
    }

    public func rankCandidates(sourceKey: String?, candidates: [String]) -> [String] {
        let rawStrings = candidates
        var unique: [String] = []
        var seen = Set<String>()
        for s in rawStrings where seen.insert(s).inserted {
            unique.append(s)
        }
        guard unique.count > 1 else { return unique }

        let nowEpoch = clock().timeIntervalSince1970
        lock.lock()
        let healthSnapshot = self.healthByHost
        lock.unlock()

        let ranked: [(value: String, index: Int, coolingDown: Bool, score: Double, cooldownUntil: TimeInterval)] = unique.enumerated().map { idx, item in
            let host = Self.extractHost(item)
            let health = host.flatMap { healthSnapshot[$0] }
            let cooling = (health?.cooldownUntilEpoch ?? 0) > nowEpoch && (health?.consecutiveFailures ?? 0) > 0
            let score: Double
            if let h = health, h.totalSuccesses > 0 {
                score = h.ewmaLatencyMs + Double(h.consecutiveFailures) * 350.0
            } else {
                score = 320.0 + Double(idx) * 25.0
            }
            return (value: item, index: idx, coolingDown: cooling, score: score, cooldownUntil: health?.cooldownUntilEpoch ?? 0)
        }

        let healthy = ranked.filter { !$0.coolingDown }.sorted {
            $0.score == $1.score ? $0.index < $1.index : $0.score < $1.score
        }
        let broken = ranked.filter { $0.coolingDown }.sorted {
            $0.cooldownUntil == $1.cooldownUntil ? $0.score < $1.score : $0.cooldownUntil < $1.cooldownUntil
        }
        return (healthy + broken).map(\.value)
    }

    public func recordSuccess(
        sourceKey: String?,
        urlOrHost: String,
        latencyMs: Int,
        finalRedirectURL: String? = nil
    ) {
        let nowEpoch = clock().timeIntervalSince1970
        let clamped = Double(max(5, min(25_000, latencyMs)))
        let host = Self.extractHost(urlOrHost)

        lock.lock()
        if let host {
            let prev = healthByHost[host]
            let newEwma: Double
            if let prev, prev.totalSuccesses > 0 {
                newEwma = Self.ewmaAlpha * clamped + (1.0 - Self.ewmaAlpha) * prev.ewmaLatencyMs
            } else {
                newEwma = clamped
            }
            healthByHost[host] = HostHealth(
                host: host,
                ewmaLatencyMs: newEwma,
                consecutiveFailures: 0,
                cooldownUntilEpoch: 0,
                totalSuccesses: (prev?.totalSuccesses ?? 0) + 1,
                totalFailures: prev?.totalFailures ?? 0,
                lastStatusCode: 200,
                updatedAtEpoch: nowEpoch
            )
        }
        lock.unlock()

        if let sourceKey, !sourceKey.isEmpty,
           let finalRedirectURL,
           let origHost = host,
           let finalHost = Self.extractHost(finalRedirectURL),
           origHost != finalHost,
           let finalOrigin = Self.normalizeOrigin(finalRedirectURL),
           Self.isPlausibleSourceMirror(sourceKey: sourceKey, origHost: origHost, finalHost: finalHost) {
            registerLearnedMirror(sourceKey: sourceKey, originURL: finalOrigin)
        } else {
            lock.lock()
            persistStateLocked()
            lock.unlock()
        }
    }

    public func recordFailure(
        sourceKey: String?,
        urlOrHost: String,
        statusCode: Int = 0,
        isHardBlock: Bool = false
    ) {
        guard let host = Self.extractHost(urlOrHost) else { return }
        let nowEpoch = clock().timeIntervalSince1970

        lock.lock()
        let prev = healthByHost[host]
        let failures = (prev?.consecutiveFailures ?? 0) + 1
        let shouldOpen = failures >= Self.circuitFailureThreshold || isHardBlock || [403, 502, 503, 504].contains(statusCode)
        let cooldownSec: TimeInterval
        if shouldOpen {
            let shift = min(3, max(0, failures - 1))
            cooldownSec = min(Self.maxCooldownSec, Self.baseCooldownSec * Double(1 << shift))
        } else {
            cooldownSec = max(0, (prev?.cooldownUntilEpoch ?? 0) - nowEpoch)
        }
        healthByHost[host] = HostHealth(
            host: host,
            ewmaLatencyMs: (prev?.ewmaLatencyMs ?? Self.defaultUnprobedEwmaMs) + 400.0,
            consecutiveFailures: failures,
            cooldownUntilEpoch: cooldownSec > 0 ? nowEpoch + cooldownSec : 0,
            totalSuccesses: prev?.totalSuccesses ?? 0,
            totalFailures: (prev?.totalFailures ?? 0) + 1,
            lastStatusCode: statusCode,
            updatedAtEpoch: nowEpoch
        )
        persistStateLocked()
        lock.unlock()

        if cooldownSec > 0 {
            print("[AuroraHealth] circuit open [\(sourceKey ?? "-")] host=\(host) status=\(statusCode) cooldown=\(Int(cooldownSec))s")
        }
    }

    public func isCircuitOpen(urlOrHost: String) -> Bool {
        guard let host = Self.extractHost(urlOrHost) else { return false }
        let nowEpoch = clock().timeIntervalSince1970
        lock.lock()
        let entry = healthByHost[host]
        lock.unlock()
        guard let entry else { return false }
        return entry.cooldownUntilEpoch > nowEpoch && entry.consecutiveFailures > 0
    }

    public func snapshotHealth(for urlOrHost: String) -> HostHealth? {
        guard let host = Self.extractHost(urlOrHost) else { return nil }
        lock.lock()
        let entry = healthByHost[host]
        lock.unlock()
        return entry
    }

    public func telemetrySummary() -> String {
        let nowEpoch = clock().timeIntervalSince1970
        lock.lock()
        let values = Array(healthByHost.values)
        lock.unlock()
        let tracked = values.sorted { $0.updatedAtEpoch > $1.updatedAtEpoch }.prefix(12)
        let openCircuits = values.filter { $0.cooldownUntilEpoch > nowEpoch && $0.consecutiveFailures > 0 }.count
        let desc = tracked.map { h in
            let state = h.cooldownUntilEpoch > nowEpoch ? "OPEN" : "OK"
            return "\(h.host):\(Int(h.ewmaLatencyMs))ms(\(state),ok=\(h.totalSuccesses),err=\(h.totalFailures))"
        }.joined(separator: "; ")
        return "tracked=\(values.count) openCircuits=\(openCircuits) [\(desc)]"
    }

    public func clearAllForTesting() {
        lock.lock()
        healthByHost.removeAll()
        learnedMirrorsBySource.removeAll()
        manifestMirrorsBySource.removeAll()
        defaults?.removeObject(forKey: storageKey)
        lock.unlock()
    }

    public static func sourceKey(for url: URL) -> String? {
        guard let host = url.host?.lowercased() else { return nil }
        if host == "h1014.sol148.com" || host == "91porn.com" || host.hasSuffix(".sol148.com") { return "91porn" }
        if host.hasSuffix("pornhub.com") { return "pornhub" }
        if host.contains("missav") { return "missav" }
        if host.contains("2024manga") || host.contains("mangacopy") { return "copy" }
        if host.contains("cdnhjk") || host.contains("cdngwc") { return "jm" }
        if host.contains("sol140.com") || host.contains("abc2507") { return "t906" }
        if host.contains("webmota") || host.contains("twbzmg") || host.contains("baozimh") { return "baozi" }
        return nil
    }

    public static func normalizeOrigin(_ raw: String) -> String? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let withScheme = trimmed.contains("://") ? trimmed : "https://\(trimmed)"
        guard let comps = URLComponents(string: withScheme),
              let host = comps.host?.lowercased(), !host.isEmpty else { return nil }
        let scheme = (comps.scheme?.lowercased() ?? "https")
        guard scheme == "http" || scheme == "https" else { return nil }
        let portPart: String
        if let port = comps.port, port != 80 && port != 443 {
            portPart = ":\(port)"
        } else {
            portPart = ""
        }
        return "\(scheme)://\(host)\(portPart)"
    }

    public static func extractHost(_ urlOrHost: String) -> String? {
        let trimmed = urlOrHost.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        if !trimmed.contains("://") && !trimmed.contains("/") {
            return trimmed.split(separator: ":").first.map { String($0).lowercased() }
        }
        return URL(string: trimmed)?.host?.lowercased()
    }

    private static func isPlausibleSourceMirror(sourceKey: String, origHost: String, finalHost: String) -> Bool {
        if finalHost.contains("cloudflare") || finalHost.contains("challenge") { return false }
        switch sourceKey {
        case "missav": return finalHost.contains("missav")
        case "pornhub": return finalHost.contains("pornhub")
        case "91porn": return finalHost.contains("91") || finalHost.contains("sol")
        case "t906": return finalHost.contains("sol")
        case "copy": return finalHost.contains("manga")
        case "baozi": return finalHost.contains("webmota") || finalHost.contains("baozimh") || finalHost.contains("twbzmg")
        default:
            let stem = origHost.split(separator: ".").first.map(String.init) ?? ""
            return stem.count >= 4 && finalHost.contains(stem)
        }
    }
}
