import Combine
import Foundation

public struct ComicReadingProgress: Codable, Equatable, Sendable {
    public let sourceId: String
    public let comicId: String
    public let chapterId: String
    public let chapterIndex: Int
    public let pageNumber: Int
    public let updatedAt: Date

    public init(
        sourceId: String,
        comicId: String,
        chapterId: String,
        chapterIndex: Int,
        pageNumber: Int,
        updatedAt: Date = Date()
    ) {
        self.sourceId = sourceId
        self.comicId = comicId
        self.chapterId = chapterId
        self.chapterIndex = chapterIndex
        self.pageNumber = max(1, pageNumber)
        self.updatedAt = updatedAt
    }
}

@MainActor
public final class ComicReadingProgressStore: ObservableObject {
    public static let shared = ComicReadingProgressStore()

    @Published public private(set) var records: [String: ComicReadingProgress] = [:]

    private let defaults: UserDefaults
    private let storageKey: String
    private let maxEntries = 200

    public init(
        defaults: UserDefaults = .standard,
        storageKey: String = "comic_reading_progress_v1"
    ) {
        self.defaults = defaults
        self.storageKey = storageKey
        loadFromDefaults()
    }

    public func key(sourceId: String, comicId: String) -> String {
        "\(sourceId):\(comicId)"
    }

    public func progress(sourceId: String, comicId: String) -> ComicReadingProgress? {
        records[key(sourceId: sourceId, comicId: comicId)]
    }

    public func record(
        sourceId: String,
        comicId: String,
        chapterId: String,
        chapterIndex: Int,
        pageNumber: Int
    ) {
        guard !comicId.isEmpty, !chapterId.isEmpty else { return }
        let entryKey = key(sourceId: sourceId, comicId: comicId)
        records[entryKey] = ComicReadingProgress(
            sourceId: sourceId,
            comicId: comicId,
            chapterId: chapterId,
            chapterIndex: max(0, chapterIndex),
            pageNumber: max(1, pageNumber)
        )
        trimIfNeeded()
        persistToDefaults()
    }

    public func remove(sourceId: String, comicId: String) {
        records.removeValue(forKey: key(sourceId: sourceId, comicId: comicId))
        persistToDefaults()
    }

    public func clearAll() {
        records.removeAll()
        defaults.removeObject(forKey: storageKey)
    }

    private func trimIfNeeded() {
        guard records.count > maxEntries else { return }
        let sortedKeys = records
            .sorted { $0.value.updatedAt > $1.value.updatedAt }
            .prefix(maxEntries)
            .map(\.key)
        let allowed = Set(sortedKeys)
        records = records.filter { allowed.contains($0.key) }
    }

    private func loadFromDefaults() {
        guard let data = defaults.data(forKey: storageKey),
              let decoded = try? JSONDecoder().decode([String: ComicReadingProgress].self, from: data) else {
            return
        }
        records = decoded
    }

    private func persistToDefaults() {
        guard let data = try? JSONEncoder().encode(records) else { return }
        defaults.set(data, forKey: storageKey)
    }
}
