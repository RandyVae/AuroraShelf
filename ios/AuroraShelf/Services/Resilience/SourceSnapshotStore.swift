import Foundation
import CryptoKit

/// 多槽位 SWR（Stale-While-Revalidate）+ 断网保底（Stale-If-Error）列表快照存储（iOS/iPadOS）。
/// 彻底消除冷启动与切换视频源/漫画源/社区板块时的白屏闪断；即使全部镜像短暂不可用，仍可降级保留 72 小时内有效快照。
public final class SourceSnapshotStore: @unchecked Sendable {
    public static let shared = SourceSnapshotStore()

    public struct Snapshot<T: Sendable>: Sendable {
        public let items: [T]
        public let savedAtEpoch: TimeInterval
        public let isFresh: Bool
    }

    private struct Envelope<T: Codable>: Codable {
        let savedAtEpoch: TimeInterval
        let items: [T]
    }

    private let lock = NSLock()
    private let directory: URL
    private let clock: @Sendable () -> Date
    private var memoryDataCache: [String: (savedAt: TimeInterval, payload: Data)] = [:]

    public static let freshTTL: TimeInterval = 90
    public static let maxStaleAge: TimeInterval = 72 * 3600
    private static let maxSlots = 36
    private static let maxItemsPerSlot = 36

    public init(directory: URL? = nil, clock: @escaping @Sendable () -> Date = Date.init) {
        if let directory {
            self.directory = directory
        } else {
            let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
                ?? URL(fileURLWithPath: NSTemporaryDirectory())
            self.directory = caches.appendingPathComponent("AuroraSourceSnapshots", isDirectory: true)
        }
        self.clock = clock
        try? FileManager.default.createDirectory(at: self.directory, withIntermediateDirectories: true)
    }

    // MARK: - Video Feed

    public func getVideoFeed(sourceId: String, category: String) -> Snapshot<VideoItem>? {
        readSlot(key: "video|\(sourceId)|\(category)", as: VideoItem.self)
    }

    public func putVideoFeed(sourceId: String, category: String, items: [VideoItem]) {
        guard !items.isEmpty else { return }
        var seen = Set<String>()
        let retained = items.filter { seen.insert($0.id).inserted }.prefix(Self.maxItemsPerSlot)
        writeSlot(key: "video|\(sourceId)|\(category)", items: Array(retained))
    }

    // MARK: - Comic Feed

    public func getComicFeed(sourceId: String, category: String) -> Snapshot<ComicItem>? {
        readSlot(key: "comic|\(sourceId)|\(category)", as: ComicItem.self)
    }

    public func putComicFeed(sourceId: String, category: String, items: [ComicItem]) {
        guard !items.isEmpty else { return }
        var seen = Set<String>()
        let retained = items.filter { seen.insert("\($0.sourceId):\($0.id)").inserted }.prefix(Self.maxItemsPerSlot)
        writeSlot(key: "comic|\(sourceId)|\(category)", items: Array(retained))
    }

    // MARK: - Live Feed

    public func getLiveFeed(sourceId: String, gender: String) -> Snapshot<LiveStreamItem>? {
        readSlot(key: "live|\(sourceId)|\(gender)", as: LiveStreamItem.self)
    }

    public func putLiveFeed(sourceId: String, gender: String, items: [LiveStreamItem]) {
        guard !items.isEmpty else { return }
        var seen = Set<String>()
        let retained = items.filter { seen.insert($0.id).inserted }.prefix(Self.maxItemsPerSlot)
        writeSlot(key: "live|\(sourceId)|\(gender)", items: Array(retained))
    }

    // MARK: - Forum Feed

    public func getForumFeed(boardKey: String) -> Snapshot<ForumPostItem>? {
        readSlot(key: "forum|\(boardKey)", as: ForumPostItem.self)
    }

    public func putForumFeed(boardKey: String, items: [ForumPostItem]) {
        guard !items.isEmpty else { return }
        var seen = Set<String>()
        let retained = items.filter { seen.insert($0.compositeID).inserted }.prefix(Self.maxItemsPerSlot)
        writeSlot(key: "forum|\(boardKey)", items: Array(retained))
    }

    public func clearAll() {
        lock.lock()
        memoryDataCache.removeAll()
        lock.unlock()
        if let files = try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil) {
            files.forEach { try? FileManager.default.removeItem(at: $0) }
        }
    }

    // MARK: - Internal Engine

    private func readSlot<T: Codable & Sendable>(key: String, as type: T.Type) -> Snapshot<T>? {
        let nowEpoch = clock().timeIntervalSince1970
        let fileURL = fileURL(for: key)

        lock.lock()
        let memEntry = memoryDataCache[key]
        lock.unlock()

        let data: Data
        if let memEntry {
            data = memEntry.payload
        } else if let diskData = try? Data(contentsOf: fileURL), !diskData.isEmpty {
            data = diskData
        } else {
            return nil
        }

        guard let envelope = try? JSONDecoder().decode(Envelope<T>.self, from: data) else {
            return nil
        }

        let age = nowEpoch - envelope.savedAtEpoch
        guard envelope.savedAtEpoch > 0, age >= 0, age <= Self.maxStaleAge, !envelope.items.isEmpty else {
            lock.lock()
            memoryDataCache.removeValue(forKey: key)
            lock.unlock()
            try? FileManager.default.removeItem(at: fileURL)
            return nil
        }

        lock.lock()
        memoryDataCache[key] = (savedAt: envelope.savedAtEpoch, payload: data)
        lock.unlock()

        return Snapshot(
            items: envelope.items,
            savedAtEpoch: envelope.savedAtEpoch,
            isFresh: age <= Self.freshTTL
        )
    }

    private func writeSlot<T: Codable>(key: String, items: [T]) {
        let nowEpoch = clock().timeIntervalSince1970
        let envelope = Envelope(savedAtEpoch: nowEpoch, items: items)
        guard let encoded = try? JSONEncoder().encode(envelope) else { return }

        lock.lock()
        memoryDataCache[key] = (savedAt: nowEpoch, payload: encoded)
        if memoryDataCache.count > Self.maxSlots {
            let overflow = memoryDataCache.count - Self.maxSlots
            let victims = memoryDataCache.sorted { $0.value.savedAt < $1.value.savedAt }.prefix(overflow)
            victims.forEach { memoryDataCache.removeValue(forKey: $0.key) }
        }
        lock.unlock()

        let targetURL = fileURL(for: key)
        let tmpURL = directory.appendingPathComponent("\(targetURL.lastPathComponent).tmp")
        do {
            try encoded.write(to: tmpURL, options: .atomic)
            if FileManager.default.fileExists(atPath: targetURL.path) {
                try FileManager.default.removeItem(at: targetURL)
            }
            try FileManager.default.moveItem(at: tmpURL, to: targetURL)
            trimDiskIfNeeded()
        } catch {
            try? FileManager.default.removeItem(at: tmpURL)
        }
    }

    private func trimDiskIfNeeded() {
        guard let urls = try? FileManager.default.contentsOfDirectory(
            at: directory,
            includingPropertiesForKeys: [.contentModificationDateKey]
        ) else { return }
        let jsonFiles = urls.filter { $0.pathExtension == "json" }
        guard jsonFiles.count > Self.maxSlots else { return }
        let sorted = jsonFiles.sorted { a, b in
            let dA = (try? a.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
            let dB = (try? b.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
            return dA < dB
        }
        sorted.prefix(jsonFiles.count - Self.maxSlots).forEach { try? FileManager.default.removeItem(at: $0) }
    }

    private func fileURL(for key: String) -> URL {
        let digest = SHA256.hash(data: Data(key.utf8)).map { String(format: "%02x", $0) }.joined()
        return directory.appendingPathComponent("\(digest).json")
    }
}
