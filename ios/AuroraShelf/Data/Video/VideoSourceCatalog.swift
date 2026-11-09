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
        categories: ["全部", "连载番剧", "剧场版", "完结经典", "里番特区"],
        isDirectConnect: true
    )
    
    // Beeg
    public static let beeg = VideoSourceDefinition(
        id: "beeg",
        name: "Beeg",
        group: .overseas,
        description: "REST API 视频列表 · 原站播放信息",
        baseUrl: "https://beeg.com",
        categories: ["全部", "精选", "日本", "自拍", "亚洲", "欧美"],
        isDirectConnect: false
    )
    
    // XVideos
    public static let xvideos = VideoSourceDefinition(
        id: "xvideos",
        name: "XVideos",
        group: .overseas,
        description: "站点分类列表 · 视频详情页解析",
        baseUrl: "https://www.xvideos.com",
        categories: ["全部", "热播", "最佳", "自拍", "日本", "亚洲", "4K超清"],
        isDirectConnect: false
    )
    
    // MISSPipe / NewPipe 13 个国际扩展源
    public static let missav = VideoSourceDefinition(
        id: "missav",
        name: "MissAV",
        group: .overseas,
        description: "站点检索列表 · 原站详情页解析",
        baseUrl: "https://missav.media",
        categories: ["全部", "最新发布", "热门中字", "无码流出", "VR专区"],
        isDirectConnect: false
    )
    
    public static let kissjav = VideoSourceDefinition(
        id: "kissjav",
        name: "KissJAV",
        group: .overseas,
        description: "站点检索列表 · 原站详情页解析",
        baseUrl: "https://kissjav.li",
        categories: ["全部", "热门", "新片", "无码", "中文字幕"],
        isDirectConnect: false
    )
    
    public static let po85 = VideoSourceDefinition(
        id: "85po",
        name: "85po",
        group: .overseas,
        description: "日文列表与搜索 · 原站详情页解析",
        baseUrl: "https://www.85po.com",
        categories: ["全部", "最近更新", "精选热播", "高清画质"],
        isDirectConnect: false
    )
    
    public static let pornhub = VideoSourceDefinition(
        id: "pornhub",
        name: "Pornhub",
        group: .overseas,
        description: "区域站点列表与搜索 · 原站详情页解析",
        baseUrl: "https://jp.pornhub.com",
        categories: ["全部", "认证精选", "热门榜单", "4K超清", "中文字幕"],
        isDirectConnect: false
    )
    
    public static let javnoni = VideoSourceDefinition(
        id: "javnoni",
        name: "JAV-NONI",
        group: .overseas,
        description: "站点分类与搜索列表 · 原站详情页解析",
        baseUrl: "https://javnoni.com",
        categories: ["全部", "最新上线", "中文字幕", "素人企划"],
        isDirectConnect: false
    )
    
    public static let javsb = VideoSourceDefinition(
        id: "javsb",
        name: "JAVSB",
        group: .overseas,
        description: "榜单与搜索列表 · 原站详情页解析",
        baseUrl: "https://jav.sb",
        categories: ["全部", "精选", "排行", "最新"],
        isDirectConnect: false
    )
    
    public static let tokyomotion = VideoSourceDefinition(
        id: "tokyomotion",
        name: "TOKYO Motion",
        group: .overseas,
        description: "视频列表与搜索 · 原站详情页解析",
        baseUrl: "https://www.tokyomotion.net",
        categories: ["全部", "最新", "最高评分", "最多收藏"],
        isDirectConnect: false
    )
    
    public static let spankbang = VideoSourceDefinition(
        id: "spankbang",
        name: "SpankBang",
        group: .overseas,
        description: "站点搜索与分类 · 原站详情页解析",
        baseUrl: "https://www.spankbang.com",
        categories: ["全部", "4K 60FPS", "当下热门", "精选特辑"],
        isDirectConnect: false
    )
    
    public static let xhamster = VideoSourceDefinition(
        id: "xhamster",
        name: "xHamster",
        group: .overseas,
        description: "站点搜索与分类 · 原站详情页解析",
        baseUrl: "https://xhamster.com",
        categories: ["全部", "最佳视频", "最新发布", "本地精选"],
        isDirectConnect: false
    )
    
    public static let eporner = VideoSourceDefinition(
        id: "eporner",
        name: "EPORNER",
        group: .overseas,
        description: "JSON API 视频列表与搜索",
        baseUrl: "https://www.eporner.com",
        categories: ["全部", "4K 超清", "最受喜爱", "本周热门"],
        isDirectConnect: false
    )
    
    public static let mrdouga = VideoSourceDefinition(
        id: "mrdouga",
        name: "MRDOUGA",
        group: .overseas,
        description: "站点搜索与分类 · 原站详情页解析",
        baseUrl: "https://mrdouga.com",
        categories: ["全部", "推荐", "热门", "新番"],
        isDirectConnect: false
    )
    
    public static let ohentai = VideoSourceDefinition(
        id: "ohentai",
        name: "OHentai",
        group: .overseas,
        description: "站点搜索与分类 · 原站详情页解析",
        baseUrl: "https://ohentai.org",
        categories: ["全部", "人气推荐", "新番速递", "全集连载"],
        isDirectConnect: false
    )
    
    public static let xnxx = VideoSourceDefinition(
        id: "xnxx",
        name: "XNXX",
        group: .overseas,
        description: "官方榜单列表 · 原站详情页解析",
        baseUrl: "https://www.xnxx.com",
        categories: ["全部"],
        isDirectConnect: false
    )
    
    // 全量 19 个视频源列表（与 Android 端 100% 对齐）
    public static let allSources: [VideoSourceDefinition] = [
        huavod, huangguo, porn91, dongman91,
        beeg, xvideos, missav, kissjav,
        po85, pornhub, javnoni, javsb,
        tokyomotion, spankbang, xhamster,
        eporner, mrdouga, ohentai, xnxx
    ]

    /// Sources with a dedicated repository flow (including the two structured API adapters).
    public static let dedicatedAdapterSourceIDs: Set<String> = [
        huavod.id, huangguo.id, porn91.id, dongman91.id, beeg.id, xvideos.id, eporner.id
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
    
    public static func find(by id: String) -> VideoSourceDefinition {
        allSources.first(where: { $0.id == id }) ?? huavod
    }
}
