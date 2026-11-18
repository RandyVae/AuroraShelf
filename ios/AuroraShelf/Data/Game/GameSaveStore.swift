import Foundation

/// 原生游戏存档持久化仓库（双写 Application Support/GameSave/lg_save.json 与 UserDefaults 镜像，彻底免疫 WKWebsiteDataStore 缓存清理导致的丢档）
public final class GameSaveStore: @unchecked Sendable {
    public static let shared = GameSaveStore()

    public static let primarySaveKey = "lg_save"
    private static let defaultsMirrorKey = "aurora.game.save_store.v1"

    private let lock = NSLock()
    private let fileManager = FileManager.default
    let fileURL: URL
    private let defaults: UserDefaults
    private var entries: [String: String] = [:]

    private convenience init() {
        let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let gameSaveDir = appSupport.appendingPathComponent("GameSave", isDirectory: true)
        self.init(
            fileURL: gameSaveDir.appendingPathComponent("lg_save.json"),
            defaults: .standard
        )
    }

    init(fileURL: URL, defaults: UserDefaults) {
        self.fileURL = fileURL
        self.defaults = defaults
        Self.prepareExcludedDirectory(fileURL.deletingLastPathComponent(), fileManager: fileManager)
        self.entries = Self.loadInitialEntries(fileURL: fileURL, defaults: defaults, fileManager: fileManager)
    }

    private static func prepareExcludedDirectory(_ directory: URL, fileManager: FileManager) {
        if !fileManager.fileExists(atPath: directory.path) {
            try? fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        }
        var mutableDir = directory
        var resourceValues = URLResourceValues()
        resourceValues.isExcludedFromBackup = true
        try? mutableDir.setResourceValues(resourceValues)
    }

    private static func loadInitialEntries(
        fileURL: URL,
        defaults: UserDefaults,
        fileManager: FileManager
    ) -> [String: String] {
        if fileManager.fileExists(atPath: fileURL.path),
           let data = try? Data(contentsOf: fileURL),
           let decoded = try? JSONDecoder().decode([String: String].self, from: data) {
            return decoded
        }
        if let mirrorData = defaults.data(forKey: defaultsMirrorKey),
           let decoded = try? JSONDecoder().decode([String: String].self, from: mirrorData) {
            if let reencoded = try? JSONEncoder().encode(decoded) {
                prepareExcludedDirectory(fileURL.deletingLastPathComponent(), fileManager: fileManager)
                try? reencoded.write(to: fileURL, options: .atomic)
            }
            return decoded
        }
        return [:]
    }

    public func get() -> String? {
        getKey(Self.primarySaveKey)
    }

    public func set(_ raw: String) {
        setKey(Self.primarySaveKey, raw: raw)
    }

    public func clear() {
        clearKey(Self.primarySaveKey)
    }

    public func getKey(_ key: String) -> String? {
        guard !key.isEmpty else { return nil }
        lock.lock()
        defer { lock.unlock() }
        return entries[key]
    }

    public func setKey(_ key: String, raw: String) {
        guard !key.isEmpty else { return }
        lock.lock()
        entries[key] = raw
        let snapshot = entries
        lock.unlock()
        persistSnapshot(snapshot)
    }

    public func clearKey(_ key: String) {
        guard !key.isEmpty else { return }
        lock.lock()
        entries.removeValue(forKey: key)
        let snapshot = entries
        lock.unlock()
        persistSnapshot(snapshot)
    }

    public func clearAll() {
        lock.lock()
        entries.removeAll()
        lock.unlock()
        persistSnapshot([:])
    }

    public func snapshotDictionary() -> [String: String] {
        lock.lock()
        defer { lock.unlock() }
        return entries
    }

    /// 序列化为可安全内联到 `.atDocumentStart` WKUserScript 的 JSON 字面量
    public func snapshotJSONLiteral() -> String {
        let dict = snapshotDictionary()
        guard let data = try? JSONSerialization.data(withJSONObject: dict, options: []),
              var json = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        json = json
            .replacingOccurrences(of: "\u{2028}", with: "\\u2028")
            .replacingOccurrences(of: "\u{2029}", with: "\\u2029")
            .replacingOccurrences(of: "</script>", with: "<\\/script>", options: .caseInsensitive)
        return json
    }

    private func persistSnapshot(_ snapshot: [String: String]) {
        guard let data = try? JSONEncoder().encode(snapshot) else { return }
        Self.prepareExcludedDirectory(fileURL.deletingLastPathComponent(), fileManager: fileManager)
        try? data.write(to: fileURL, options: .atomic)
        defaults.set(data, forKey: Self.defaultsMirrorKey)
    }
}
