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
        description: "大陆直连 · 4K 高清电影/连续剧/短剧",
        baseUrl: "https://huavod.com",
        categories: ["全部", "电影", "电视剧", "综艺", "动漫", "短剧"],
        isDirectConnect: true
    )
    
    // 黄果短剧
    public static let huangguo = VideoSourceDefinition(
        id: "huangguo",
        name: "黄果短剧",
        group: .direct,
        description: "快节奏爽剧 · 原生无尽分页",
        baseUrl: "https://huangguoai.com",
        categories: ["全部", "推荐", "最新", "AI漫剧", "AI换脸"],
        isDirectConnect: true
    )
    
    // 91视频
    public static let porn91 = VideoSourceDefinition(
        id: "91porn",
        name: "91视频",
        group: .direct,
        description: "经典原创视界 · 真实视频流",
        baseUrl: "https://h1014.sol148.com",
        categories: ["全部", "当前最热", "非付费", "最近加精", "高清"],
        isDirectConnect: true
    )
    
    // 91动漫视频
    public static let dongman91 = VideoSourceDefinition(
        id: "91dongman",
        name: "91动漫",
        group: .direct,
        description: "高清番剧 · AES 解密封面直连",
        baseUrl: "https://91dongman.net",
        categories: ["全部", "连载番剧", "剧场版", "完结经典", "里番特区"],
        isDirectConnect: true
    )
    
    // Beeg
    public static let beeg = VideoSourceDefinition(
        id: "beeg",
        name: "Beeg",
        group: .overseas,
        description: "国际精选 · REST API 高清流",
        baseUrl: "https://beeg.com",
        categories: ["全部", "精选", "日本", "自拍", "亚洲", "欧美"],
        isDirectConnect: false
    )
    
    // XVideos
    public static let xvideos = VideoSourceDefinition(
        id: "xvideos",
        name: "XVideos",
        group: .overseas,
        description: "全球最大视频源之一 · 41 全量分类",
        baseUrl: "https://www.xvideos.com",
        categories: ["全部", "热播", "最佳", "自拍", "日本", "亚洲", "4K超清"],
        isDirectConnect: false
    )
    
    // MISSPipe / NewPipe 13 个国际扩展源
    public static let missav = VideoSourceDefinition(
        id: "missav",
        name: "MissAV",
        group: .overseas,
        description: "高清中字 · 每日同步最新发售",
        baseUrl: "https://missav.ai",
        categories: ["全部", "最新发布", "热门中字", "无码流出", "VR专区"],
        isDirectConnect: false
    )
    
    public static let kissjav = VideoSourceDefinition(
        id: "kissjav",
        name: "KissJAV",
        group: .overseas,
        description: "超清无码 · 完整版流媒体",
        baseUrl: "https://kissjav.com",
        categories: ["全部", "热门", "新片", "无码", "中文字幕"],
        isDirectConnect: false
    )
    
    public static let po85 = VideoSourceDefinition(
        id: "85po",
        name: "85po",
        group: .overseas,
        description: "轻量高速 · 简洁列表",
        baseUrl: "https://85po.com",
        categories: ["全部", "最近更新", "精选热播", "高清画质"],
        isDirectConnect: false
    )
    
    public static let pornhub = VideoSourceDefinition(
        id: "pornhub",
        name: "Pornhub",
        group: .overseas,
        description: "全球头部平台 · 认证创作者流",
        baseUrl: "https://www.pornhub.com",
        categories: ["全部", "认证精选", "热门榜单", "4K超清", "中文字幕"],
        isDirectConnect: false
    )
    
    public static let javnoni = VideoSourceDefinition(
        id: "javnoni",
        name: "JAV-NONI",
        group: .overseas,
        description: "多线路极速解包 · 线性扫描",
        baseUrl: "https://javnoni.com",
        categories: ["全部", "最新上线", "中文字幕", "素人企划"],
        isDirectConnect: false
    )
    
    public static let javsb = VideoSourceDefinition(
        id: "javsb",
        name: "JAVSB",
        group: .overseas,
        description: "无水印超清在线",
        baseUrl: "https://javsb.net",
        categories: ["全部", "精选", "排行", "最新"],
        isDirectConnect: false
    )
    
    public static let tokyomotion = VideoSourceDefinition(
        id: "tokyomotion",
        name: "TOKYO Motion",
        group: .overseas,
        description: "东京潮流 · 原创短片社区",
        baseUrl: "https://www.tokyomotion.net",
        categories: ["全部", "最新", "最高评分", "最多收藏"],
        isDirectConnect: false
    )
    
    public static let spankbang = VideoSourceDefinition(
        id: "spankbang",
        name: "SpankBang",
        group: .overseas,
        description: "4K 60fps 高帧率流媒体",
        baseUrl: "https://spankbang.com",
        categories: ["全部", "4K 60FPS", "当下热门", "精选特辑"],
        isDirectConnect: false
    )
    
    public static let xhamster = VideoSourceDefinition(
        id: "xhamster",
        name: "xHamster",
        group: .overseas,
        description: "老牌国际巨擘 · 极速分发",
        baseUrl: "https://xhamster.com",
        categories: ["全部", "最佳视频", "最新发布", "本地精选"],
        isDirectConnect: false
    )
    
    public static let eporner = VideoSourceDefinition(
        id: "eporner",
        name: "EPORNER",
        group: .overseas,
        description: "原生真 4K HDR 码率流",
        baseUrl: "https://www.eporner.com",
        categories: ["全部", "4K 超清", "最受喜爱", "本周热门"],
        isDirectConnect: false
    )
    
    public static let mrdouga = VideoSourceDefinition(
        id: "mrdouga",
        name: "MRDOUGA",
        group: .overseas,
        description: "日韩精选影视短视频",
        baseUrl: "https://mrdouga.com",
        categories: ["全部", "推荐", "热门", "新番"],
        isDirectConnect: false
    )
    
    public static let ohentai = VideoSourceDefinition(
        id: "ohentai",
        name: "OHentai",
        group: .overseas,
        description: "精美二次元动漫影视",
        baseUrl: "https://ohentai.org",
        categories: ["全部", "人气推荐", "新番速递", "全集连载"],
        isDirectConnect: false
    )
    
    public static let xnxx = VideoSourceDefinition(
        id: "xnxx",
        name: "XNXX",
        group: .overseas,
        description: "经典高速多码率直链",
        baseUrl: "https://www.xnxx.com",
        categories: ["全部", "热门精选", "最新更新", "高清特区"],
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
    
    public static func find(by id: String) -> VideoSourceDefinition {
        allSources.first(where: { $0.id == id }) ?? huavod
    }
}
