import Foundation

public struct ComicChapter: Identifiable, Hashable, Sendable {
    public let id: String
    public let title: String
    public let pageCount: Int
    
    public init(id: String, title: String, pageCount: Int = 0) {
        self.id = id
        self.title = title
        self.pageCount = pageCount
    }
}

public struct ComicPage: Identifiable, Hashable, Sendable {
    public let id: String
    public let imageUrl: String
    public let referer: String?
    public let headers: [String: String]
    public let resolutionUrl: String?
    public let verticalSegments: Int

    public init(
        imageUrl: String,
        referer: String? = nil,
        headers: [String: String] = [:],
        resolutionUrl: String? = nil,
        verticalSegments: Int = 0
    ) {
        let trimmedResolution = resolutionUrl?.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedImage = imageUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        if let trimmedResolution, !trimmedResolution.isEmpty {
            self.id = trimmedResolution
        } else if !trimmedImage.isEmpty {
            self.id = trimmedImage
        } else {
            self.id = UUID().uuidString
        }
        self.imageUrl = imageUrl
        self.referer = referer
        self.headers = headers
        self.resolutionUrl = resolutionUrl
        self.verticalSegments = verticalSegments
    }
}

public struct ComicItem: Identifiable, Hashable, Sendable {
    public let id: String
    public let sourceId: String
    public let title: String
    public let author: String
    public let coverUrl: String
    public let category: String
    public let tags: [String]
    public let chapters: [ComicChapter]
    public let latestChapterTitle: String

    public var coverBaseURL: URL? {
        ComicSourceCatalog.allSources.first(where: { $0.id == sourceId }).flatMap { URL(string: $0.baseUrl) }
    }

    public var sourceName: String {
        ComicSourceCatalog.allSources.first(where: { $0.id == sourceId })?.name ?? sourceId
    }
    
    public init(
        id: String,
        sourceId: String,
        title: String,
        author: String,
        coverUrl: String,
        category: String = "",
        tags: [String] = [],
        chapters: [ComicChapter] = [],
        latestChapterTitle: String = ""
    ) {
        self.id = id
        self.sourceId = sourceId
        self.title = title
        self.author = author
        self.coverUrl = coverUrl
        self.category = category
        self.tags = tags
        self.chapters = chapters
        self.latestChapterTitle = latestChapterTitle
    }
}

public struct ComicSourceDefinition: Identifiable, Hashable, Sendable {
    public let id: String
    public let name: String
    public let description: String
    public let baseUrl: String
    public let categories: [String]
    
    public init(id: String, name: String, description: String, baseUrl: String, categories: [String]) {
        self.id = id
        self.name = name
        self.description = description
        self.baseUrl = baseUrl
        self.categories = categories
    }
}

public enum ComicSourceCatalog {
    // 1. 包子漫画（Baozi）
    public static let baozi = ComicSourceDefinition(
        id: "baozi",
        name: "包子漫画",
        description: "简体中文 · 302 自动重定向追踪",
        baseUrl: "https://cn.webmota.com",
        categories: ["全部", "恋爱", "纯爱", "古风", "都市", "热血", "搞笑"]
    )
    
    // 2. 禁漫天堂（JM）
    public static let jm = ComicSourceDefinition(
        id: "jm",
        name: "禁漫天堂",
        description: "匿名浏览 · Base64 跨平台双轨解密",
        baseUrl: "https://www.cdnhjk.net",
        categories: ["全部", "同人", "单本", "短篇", "韩漫", "美漫", "3D"]
    )
    
    // 3. 哔咔漫画（Picacg）
    public static let picacg = ComicSourceDefinition(
        id: "picacg",
        name: "哔咔漫画",
        description: "官方分流 · 硬上限守卫杜绝死循环",
        baseUrl: "https://picaapi.picacomic.com",
        categories: ["全部", "官方推荐", "生肉", "Cosplay", "重口", "纯爱"]
    )
    
    // 4. Komiic 漫画
    public static let komiic = ComicSourceDefinition(
        id: "komiic",
        name: "Komiic 漫画",
        description: "GraphQL Schema 适配 · 高清全彩",
        baseUrl: "https://komiic.com",
        categories: ["全部", "爱情", "冒险", "魔幻", "科幻", "热血", "恐怖", "百合"]
    )
    
    // 5. 91动漫漫画端
    public static let dongman91 = ComicSourceDefinition(
        id: "dongman91",
        name: "91动漫漫画",
        description: "AES-128-CBC 封面解密 · 4 大分类",
        baseUrl: "https://91dongman.net",
        categories: ["全部", "精选漫画", "日韩专区", "欧美连载"]
    )
    
    // 6. 8Muses
    public static let eightMuses = ComicSourceDefinition(
        id: "8muses",
        name: "8Muses",
        description: "Next.js 结构 · 官方媒体路由直连",
        baseUrl: "https://8muses.io",
        categories: ["全部", "Popular", "New", "Classics"]
    )
    
    // 7. GEDE Comix
    public static let gedeComix = ComicSourceDefinition(
        id: "gedecomix",
        name: "GEDE Comix",
        description: "WordPress Madara 协议 · WebP 页面直读",
        baseUrl: "https://gedecomix.com",
        categories: ["全部", "Latest", "Trending", "Manhwa"]
    )
    
    // 8. 拷贝漫画（CopyComic）
    public static let copycomic = ComicSourceDefinition(
        id: "copy",
        name: "拷贝漫画",
        description: "优质汉化 · 热门连载日更",
        baseUrl: "https://api.2024manga.com/api/v3",
        categories: ["全部", "日漫", "韩漫", "美漫"]
    )
    
    // 9. 爱看漫画（Ikmmh）
    public static let ikmmh = ComicSourceDefinition(
        id: "ikmmh",
        name: "爱看漫画",
        description: "极速缓存 · 轻量阅读",
        baseUrl: "https://ymcdnyfqdapp.ikmmh.com",
        categories: ["全部", "都市", "悬疑", "玄幻"]
    )
    
    // 10. E-Hentai
    public static let ehentai = ComicSourceDefinition(
        id: "ehentai",
        name: "E-Hentai",
        description: "全球海量同人本子库",
        baseUrl: "https://e-hentai.org",
        categories: ["全部", "Doujinshi", "Manga", "Artist CG"]
    )
    
    // 全量 10 个漫画源
    public static let allSources: [ComicSourceDefinition] = [
        baozi, jm, picacg, komiic, dongman91,
        eightMuses, gedeComix, copycomic, ikmmh, ehentai
    ]

    /// Explicit adapters exist for all entries; JM and Picacg still require credentials/config.
    public static let supportedSourceIDs: Set<String> = [
        baozi.id, jm.id, picacg.id, komiic.id, dongman91.id,
        eightMuses.id, gedeComix.id, copycomic.id, ikmmh.id, ehentai.id
    ]

    public static var supportedSources: [ComicSourceDefinition] {
        allSources.filter { supportedSourceIDs.contains($0.id) }
    }

    public static func isSupported(_ source: ComicSourceDefinition) -> Bool {
        supportedSourceIDs.contains(source.id)
    }
    
    public static func find(by id: String) -> ComicSourceDefinition {
        allSources.first(where: { $0.id == id }) ?? jm
    }
}
