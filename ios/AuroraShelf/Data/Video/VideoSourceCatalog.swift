import Foundation

public enum VideoSourceGroup: String, CaseIterable, Sendable {
    case direct = "大陆直连精选"
    case overseas = "海外国际扩展"
}

public struct VideoSourceDefinition: Identifiable, Hashable, Sendable {
    public let id: String
    public let name: String
    public let group: VideoSourceGroup
    public let description: String
    public let baseUrl: String
    public let categories: [String]
    public let isDirectConnect: Bool

    /// Stable source-owned IDs used by tests and future route migrations while the repository
    /// keeps its public category API source-compatible with existing saved state.
    public var categoryDefinitions: [VideoSourceCategory] {
        categories.map { label in
            VideoSourceCategory(
                id: VideoSourceCatalog.categoryID(for: self, label: label),
                label: label
            )
        }
    }
    
    public init(
        id: String,
        name: String,
        group: VideoSourceGroup,
        description: String,
        baseUrl: String,
        categories: [String],
        isDirectConnect: Bool
    ) {
        self.id = id
        self.name = name
        self.group = group
        self.description = description
        self.baseUrl = baseUrl
        self.categories = categories
        self.isDirectConnect = isDirectConnect
    }
}

public struct VideoSourceCategory: Identifiable, Hashable, Sendable {
    public let id: String
    public let label: String

    public init(id: String, label: String) {
        self.id = id
        self.label = label
    }
}

public enum VideoSourceCatalog {
    // 华视影院（大陆直连推荐）
    public static let huavod = VideoSourceDefinition(
        id: "huavod",
        name: "华视影院",
        group: .direct,
        description: "影视分类列表 · 原站详情页解析",
        baseUrl: "https://huavod.com",
        categories: ["全部", "电影", "电视剧", "综艺", "动漫", "短剧"],
        isDirectConnect: true
    )
    
    // 黄果短剧
    public static let huangguo = VideoSourceDefinition(
        id: "huangguo",
        name: "黄果短剧",
        group: .direct,
        description: "短剧分类列表 · 原站详情页解析",
        baseUrl: "https://huangguoai.com",
        categories: ["全部", "推荐", "最新", "AI漫剧", "AI换脸"],
        isDirectConnect: true
    )
    
    // 91视频
    public static let porn91 = VideoSourceDefinition(
        id: "91porn",
        name: "91视频",
        group: .direct,
        description: "视频分类列表 · 原站播放页解析",
        baseUrl: "https://h1014.sol148.com",
        categories: ["全部", "当前最热", "非付费", "最近加精", "高清"],
        isDirectConnect: true
    )
    
    // 91动漫视频
    public static let dongman91 = VideoSourceDefinition(
        id: "91dongman",
        name: "91动漫",
        group: .direct,
        description: "番剧分类列表 · 支持加密封面解码",
        baseUrl: "https://91dongman.net",
        categories: ["全部", "里番", "3D动漫", "AI动漫", "排行"],
        isDirectConnect: true
    )
    
    // Beeg
    public static let beeg = VideoSourceDefinition(
        id: "beeg",
        name: "Beeg",
        group: .overseas,
        description: "REST API 视频列表 · 原站播放信息",
        baseUrl: "https://beeg.com",
        categories: ["精选", "自拍", "日本", "熟女", "亚洲"],
        isDirectConnect: false
    )

    // 134X
    public static let x134 = VideoSourceDefinition(
        id: "134x",
        name: "134X",
        group: .overseas,
        description: "无广告高清长视频 · X 长片直连",
        baseUrl: "https://134x.com",
        categories: ["最新", "热门", "精选"],
        isDirectConnect: false
    )
    
    // XVideos
    public static let xvideos = VideoSourceDefinition(
        id: "xvideos",
        name: "XVideos",
        group: .overseas,
        description: "站点分类列表 · 视频详情页解析",
        baseUrl: "https://www.xvideos.com",
        categories: [
            "最新", "热门", "中文", "自拍", "亚洲", "认证", "AI", "熟女", "少女", "巨乳",
            "美臀", "巨屌", "口交", "肛交", "潮吹", "内射", "颜射", "丝袜", "内衣", "金发",
            "棕发", "红发", "黑人", "拉丁", "印度", "阿拉伯", "跨种族", "女同", "双性", "女王",
            "群交", "家庭", "绿帽", "丰满", "成熟", "独角戏", "油压", "拳交", "扩肛", "网络直播", "ASMR"
        ],
        isDirectConnect: false
    )
    
    // MISSPipe / NewPipe 13 个国际扩展源
    public static let missav = VideoSourceDefinition(
        id: "missav",
        name: "MissAV",
        group: .overseas,
        description: "站点检索列表 · 原站详情页解析",
        baseUrl: "https://missav.media",
        categories: ["最新", "热门", "推荐"],
        isDirectConnect: false
    )
    
    public static let kissjav = VideoSourceDefinition(
        id: "kissjav",
        name: "KissJAV",
        group: .overseas,
        description: "站点检索列表 · 原站详情页解析",
        baseUrl: "https://kissjav.li",
        categories: ["最新", "热门", "推荐"],
        isDirectConnect: false
    )
    
    public static let po85 = VideoSourceDefinition(
        id: "85po",
        name: "85po",
        group: .overseas,
        description: "日文列表与搜索 · 原站详情页解析",
        baseUrl: "https://www.85po.com",
        categories: ["最新", "热门", "推荐"],
        isDirectConnect: false
    )
    
    public static let pornhub = VideoSourceDefinition(
        id: "pornhub",
        name: "Pornhub",
        group: .overseas,
        description: "区域站点列表与搜索 · 原站详情页解析",
        baseUrl: "https://jp.pornhub.com",
        categories: ["最新", "热门", "推荐"],
        isDirectConnect: false
    )
    
    public static let javnoni = VideoSourceDefinition(
        id: "javnoni",
        name: "JAV-NONI",
        group: .overseas,
        description: "站点分类与搜索列表 · 原站详情页解析",
        baseUrl: "https://javnoni.com",
        categories: ["最新", "热门", "推荐"],
        isDirectConnect: false
    )
    
    public static let javsb = VideoSourceDefinition(
        id: "javsb",
        name: "JAVSB",
        group: .overseas,
        description: "榜单与搜索列表 · 原站详情页解析",
        baseUrl: "https://jav.sb",
        categories: ["最新", "热门", "推荐"],
        isDirectConnect: false
    )
    
    public static let tokyomotion = VideoSourceDefinition(
        id: "tokyomotion",
        name: "TOKYO Motion",
        group: .overseas,
        description: "视频列表与搜索 · 原站详情页解析",
        baseUrl: "https://www.tokyomotion.net",
        categories: ["最新", "热门", "推荐"],
        isDirectConnect: false
    )
    
    public static let spankbang = VideoSourceDefinition(
        id: "spankbang",
        name: "SpankBang",
        group: .overseas,
        description: "站点搜索与分类 · 原站详情页解析",
        baseUrl: "https://www.spankbang.com",
        categories: ["最新"],
        isDirectConnect: false
    )
    
    public static let xhamster = VideoSourceDefinition(
        id: "xhamster",
        name: "xHamster",
        group: .overseas,
        description: "站点搜索与分类 · 原站详情页解析",
        baseUrl: "https://xhamster.com",
        categories: ["最新"],
        isDirectConnect: false
    )
    
    public static let eporner = VideoSourceDefinition(
        id: "eporner",
        name: "EPORNER",
        group: .overseas,
        description: "JSON API 视频列表与搜索",
        baseUrl: "https://www.eporner.com",
        categories: ["最新"],
        isDirectConnect: false
    )
    
    public static let mrdouga = VideoSourceDefinition(
        id: "mrdouga",
        name: "MRDOUGA",
        group: .overseas,
        description: "站点搜索与分类 · 原站详情页解析",
        baseUrl: "https://mrdouga.com",
        categories: ["最新", "热门"],
        isDirectConnect: false
    )
    
    public static let ohentai = VideoSourceDefinition(
        id: "ohentai",
        name: "OHentai",
        group: .overseas,
        description: "站点搜索与分类 · 原站详情页解析",
        baseUrl: "https://ohentai.org",
        categories: ["最新"],
        isDirectConnect: false
    )
    
    public static let xnxx = VideoSourceDefinition(
        id: "xnxx",
        name: "XNXX",
        group: .overseas,
        description: "官方榜单列表 · 原站详情页解析",
        baseUrl: "https://www.xnxx.com",
        categories: ["最新"],
        isDirectConnect: false
    )
    
    // 全量 20 个视频源列表（与 Android 端 100% 对齐）
    public static let allSources: [VideoSourceDefinition] = [
        huavod, huangguo, porn91, dongman91,
        beeg, x134, xvideos, missav, kissjav,
        po85, pornhub, javnoni, javsb,
        tokyomotion, spankbang, xhamster,
        eporner, mrdouga, ohentai, xnxx
    ]

    /// Sources with a dedicated repository flow (including structured API adapters).
    public static let dedicatedAdapterSourceIDs: Set<String> = [
        huavod.id, huangguo.id, porn91.id, dongman91.id, beeg.id, x134.id, xvideos.id, eporner.id
    ]

    /// Sources routed through the shared HTML list/detail parser.
    public static let htmlAdapterSourceIDs: Set<String> = [
        missav.id, kissjav.id, po85.id, pornhub.id, javnoni.id, javsb.id,
        tokyomotion.id, spankbang.id, xhamster.id, mrdouga.id, ohentai.id, xnxx.id
    ]

    /// Registration is explicit so adding a catalogue item cannot silently mark it integrated.
    public static let supportedSourceIDs = dedicatedAdapterSourceIDs.union(htmlAdapterSourceIDs)

    /// Full-text search is implemented only when the upstream exposes a search route.
    /// Beeg's current adapter provides tag feeds, not a verified keyword-search API.
    public static let searchableSourceIDs = supportedSourceIDs.subtracting([beeg.id])

    public static var supportedSources: [VideoSourceDefinition] {
        allSources.filter { supportedSourceIDs.contains($0.id) }
    }

    public static var searchableSources: [VideoSourceDefinition] {
        allSources.filter { searchableSourceIDs.contains($0.id) }
    }

    public static func isSupported(_ source: VideoSourceDefinition) -> Bool {
        supportedSourceIDs.contains(source.id)
    }

    public static func categoryID(for source: VideoSourceDefinition, label: String) -> String {
        if source.id == xvideos.id {
            let ids = [
                "最新": "latest", "热门": "best", "中文": "chinese", "自拍": "amateur", "亚洲": "asian",
                "认证": "verified", "AI": "ai", "熟女": "milf", "少女": "teen", "巨乳": "big-tits",
                "美臀": "big-ass", "巨屌": "big-cock", "口交": "blowjob", "肛交": "anal", "潮吹": "squirting",
                "内射": "creampie", "颜射": "cumshot", "丝袜": "stockings", "内衣": "lingerie", "金发": "blonde",
                "棕发": "brunette", "红发": "redhead", "黑人": "black", "拉丁": "latina", "印度": "indian",
                "阿拉伯": "arab", "跨种族": "interracial", "女同": "lesbian", "双性": "bi", "女王": "femdom",
                "群交": "gangbang", "家庭": "family", "绿帽": "cuckold", "丰满": "bbw", "成熟": "mature",
                "独角戏": "solo", "油压": "oiled", "拳交": "fisting", "扩肛": "gapes", "网络直播": "cam", "ASMR": "asmr"
            ]
            return ids[label] ?? label.lowercased()
        }
        switch label {
        case "全部": return "all"
        case "最新", "最新发布", "最近更新", "最新上线": return "latest"
        case "热门", "热门中字", "热门榜单", "当下热门", "精选热播", "排行": return "popular"
        case "推荐", "精选", "认证精选", "最高评分", "最受喜爱", "人气推荐": return "recommended"
        default: return label.lowercased()
        }
    }
    
    public static func find(by id: String) -> VideoSourceDefinition {
        allSources.first(where: { $0.id == id }) ?? huavod
    }
}
