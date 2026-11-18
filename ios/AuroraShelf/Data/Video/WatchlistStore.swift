import Foundation

@MainActor
public final class WatchlistStore: ObservableObject {
    public static let shared = WatchlistStore()
    private static let storageKey = "aurora.watchlist.v1"

    private struct EmptyDecodable: Decodable {}
    private struct LossyVideoItemArray: Decodable {
        let items: [VideoItem]
        let droppedCount: Int

        init(from decoder: Decoder) throws {
            var container = try decoder.unkeyedContainer()
            var decoded: [VideoItem] = []
            var dropped = 0
            while !container.isAtEnd {
                if let item = try? container.decode(VideoItem.self) {
                    decoded.append(item)
                } else {
                    dropped += 1
                    if (try? container.decode(EmptyDecodable.self)) == nil {
                        break
                    }
                }
            }
            self.items = decoded
            self.droppedCount = dropped
        }
    }

    @Published public private(set) var items: [VideoItem] = []
    @Published public private(set) var loadError: String?

    private let defaults: UserDefaults

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        guard let data = defaults.data(forKey: Self.storageKey) else { return }

        do {
            let decoded = try JSONDecoder().decode(LossyVideoItemArray.self, from: data)
            items = decoded.items
            if decoded.droppedCount > 0 {
                // Re-save clean items so corrupted entries do not linger
                if let cleanData = try? JSONEncoder().encode(decoded.items) {
                    defaults.set(cleanData, forKey: Self.storageKey)
                }
            }
        } catch {
            loadError = "片单数据无法读取：\(error.localizedDescription)"
        }
    }

    public func contains(_ video: VideoItem) -> Bool {
        items.contains { $0.id == video.id && $0.sourceName == video.sourceName }
    }

    public func setBookmarked(_ isBookmarked: Bool, for video: VideoItem) throws {
        var updated = items.filter { !($0.id == video.id && $0.sourceName == video.sourceName) }
        if isBookmarked {
            updated.insert(video, at: 0)
        }

        let data = try JSONEncoder().encode(updated)
        defaults.set(data, forKey: Self.storageKey)
        guard defaults.data(forKey: Self.storageKey) == data else {
            throw WatchlistStoreError.persistenceFailed
        }
        items = updated
        loadError = nil
    }
}

private enum WatchlistStoreError: LocalizedError {
    case persistenceFailed

    var errorDescription: String? {
        "无法保存片单，请检查设备储存空间后重试。"
    }
}
