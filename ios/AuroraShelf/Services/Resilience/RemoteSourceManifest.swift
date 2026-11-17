import Foundation

/// 零单点故障（Zero-SPOF）远端自愈配置清单（https://bananarey.us/source-manifest.json）。
/// 启动期非阻塞 3 秒短超时同步，完全不影响本地独立运行与冷启动速度。
public final class RemoteSourceManifest: @unchecked Sendable {
    public static let shared = RemoteSourceManifest()

    private struct ManifestPayload: Codable {
        let version: String?
        let mirrors: [String: [String]]?
        let cardSelectors: [String: [String]]?
    }

    private let manifestURL = URL(string: "https://bananarey.us/source-manifest.json")!
    private let cacheKey = "aurora_remote_manifest_v1"
    private let lock = NSLock()
    private var cardSelectorsBySource: [String: [String]] = [:]

    private init() {
        if let cachedData = UserDefaults.standard.data(forKey: cacheKey) {
            _ = applyManifestData(cachedData)
        }
    }

    public func syncInBackground() {
        let manifestURL = self.manifestURL
        let cacheKey = self.cacheKey
        Task.detached(priority: .utility) { [weak self] in
            guard let self else { return }
            guard let response = try? await AuroraNetworkEngine.shared.fetch(
                url: manifestURL,
                sourceKey: nil,
                timeout: 3,
                learnRedirectMirror: false
            ) else { return }
            if self.applyManifestData(response.data) {
                UserDefaults.standard.set(response.data, forKey: cacheKey)
                print("[RemoteSourceManifest] synced version in \(response.latencyMs)ms")
            }
        }
    }

    public func cardSelectors(for sourceKey: String) -> [String] {
        lock.lock()
        let result = cardSelectorsBySource[sourceKey] ?? []
        lock.unlock()
        return result
    }

    @discardableResult
    public func applyManifestData(_ data: Data) -> Bool {
        guard let decoded = try? JSONDecoder().decode(ManifestPayload.self, from: data) else {
            return false
        }
        if let mirrors = decoded.mirrors, !mirrors.isEmpty {
            SourceHealthRegistry.shared.applyManifestMirrors(mirrors)
        }
        if let selectors = decoded.cardSelectors {
            lock.lock()
            self.cardSelectorsBySource = selectors
            lock.unlock()
        }
        return true
    }
}
