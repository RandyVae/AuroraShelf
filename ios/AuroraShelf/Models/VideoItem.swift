import Foundation

public struct VideoDetailMetadata: Equatable, Hashable, Sendable {
    public let actors: [String]
    public let directors: [String]
    public let year: String
    public let area: String
    public let tags: [String]
    public let synopsis: String

    public init(
        actors: [String] = [],
        directors: [String] = [],
        year: String = "",
        area: String = "",
        tags: [String] = [],
        synopsis: String = ""
    ) {
        self.actors = actors
        self.directors = directors
        self.year = year
        self.area = area
        self.tags = tags
        self.synopsis = synopsis
    }

    public var hasContent: Bool {
        !actors.isEmpty || !directors.isEmpty || !year.isEmpty || !area.isEmpty || !tags.isEmpty || !synopsis.isEmpty
    }
}

public struct VideoItem: Identifiable, Hashable, Codable, Sendable {
    public let id: String
    public let title: String
    public let subtitle: String
    public let coverUrl: String
    public let heroCoverUrl: String?
    public let playbackUrl: String
    public let duration: String
    public let rating: String
    public let tags: [String]
    public let category: String
    public let isTop10: Bool
    public let rankNumber: Int?
    public let sourceName: String

    public var coverBaseURL: URL? {
        VideoSourceCatalog.allSources.first(where: { $0.name == sourceName }).flatMap { URL(string: $0.baseUrl) }
    }
    
    public init(
        id: String,
        title: String,
        subtitle: String,
        coverUrl: String,
        heroCoverUrl: String? = nil,
        playbackUrl: String,
        duration: String = "",
        rating: String = "",
        tags: [String] = [],
        category: String = "精选推荐",
        isTop10: Bool = false,
        rankNumber: Int? = nil,
        sourceName: String = "华视影院"
    ) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.coverUrl = coverUrl
        self.heroCoverUrl = heroCoverUrl
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
