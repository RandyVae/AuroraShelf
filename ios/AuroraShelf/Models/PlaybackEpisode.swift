import Foundation

/// 影视/剧集/番剧单集条目模型（对齐 Android 端 PlaybackEpisode）
public struct PlaybackEpisode: Identifiable, Hashable, Codable, Sendable {
    public let number: Int
    public let label: String
    public let pageUrl: String

    public var id: String { "\(number)-\(pageUrl)" }

    public init(number: Int, label: String, pageUrl: String) {
        self.number = number
        self.label = label
        self.pageUrl = pageUrl
    }
}
