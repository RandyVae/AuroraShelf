import Foundation

public struct VideoItem: Identifiable, Hashable, Sendable {
    public let id: String
    public let title: String
    public let subtitle: String
    public let coverUrl: String
    public let playbackUrl: String
    public let duration: String
    public let rating: String
    public let tags: [String]
    public let category: String
    public let isTop10: Bool
    public let rankNumber: Int?
    public let sourceName: String
    
    public init(
        id: String,
        title: String,
        subtitle: String,
        coverUrl: String,
        playbackUrl: String,
        duration: String = "1080P",
        rating: String = "9.8",
        tags: [String] = ["4K HDR", "官方原源"],
        category: String = "精选推荐",
        isTop10: Bool = false,
        rankNumber: Int? = nil,
        sourceName: String = "华视影院"
    ) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.coverUrl = coverUrl
        self.playbackUrl = playbackUrl
        self.duration = duration
        self.rating = rating
        self.tags = tags
        self.category = category
        self.isTop10 = isTop10
        self.rankNumber = rankNumber
        self.sourceName = sourceName
    }
}
