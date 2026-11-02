import Foundation

public final class VideoSourceService: ObservableObject, @unchecked Sendable {
    public static let shared = VideoSourceService()
    
    @Published public var billboardVideo: VideoItem
    @Published public var top10Videos: [VideoItem] = []
    @Published public var recommendedVideos: [VideoItem] = []
    @Published public var directLineVideos: [VideoItem] = []
    @Published public var categories: [String] = ["全部", "电影", "电视剧", "动漫", "短剧", "海外线路"]
    @Published public var selectedCategory: String = "全部"
    
    private init() {
        // Spotlight 聚光灯大卡（Netflix Billboard）
        self.billboardVideo = VideoItem(
            id: "hero-101",
            title: "赛博霓虹 · 极光之夜",
            subtitle: "4K 超清杜比全景声 · 极光视界独家首播",
            coverUrl: "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=1200&q=80",
            playbackUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            duration: "09:56",
            rating: "9.9",
            tags: ["4K HDR", "杜比视界", "华视超清", "首播特惠"],
            category: "精选",
            isTop10: true,
            rankNumber: 1,
            sourceName: "华视影院"
        )
        
        loadMockFeed()
    }
    
    public func loadMockFeed() {
        // Top 10 热播榜
        self.top10Videos = [
            VideoItem(
                id: "top-1",
                title: "星际穿越：未尽之途",
                subtitle: "全网热播 Top 1 · 4K 震撼视效",
                coverUrl: "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&q=80",
                playbackUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                duration: "10:53",
                rating: "9.8",
                tags: ["Top 1", "科幻神作"],
                isTop10: true,
                rankNumber: 1,
                sourceName: "华视影院"
            ),
            VideoItem(
                id: "top-2",
                title: "暗夜流火：东京追缉",
                subtitle: "高能动作悬疑大片",
                coverUrl: "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&q=80",
                playbackUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                duration: "00:15",
                rating: "9.6",
                tags: ["动作", "犯罪"],
                isTop10: true,
                rankNumber: 2,
                sourceName: "华视影院"
            ),
            VideoItem(
                id: "top-3",
                title: "极速回旋：勒芒对决",
                subtitle: "真实引擎咆哮 杜比音效",
                coverUrl: "https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?w=600&q=80",
                playbackUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                duration: "00:15",
                rating: "9.5",
                tags: ["竞技", "赛车"],
                isTop10: true,
                rankNumber: 3,
                sourceName: "91精选"
            ),
            VideoItem(
                id: "top-4",
                title: "失落的亚特兰蒂斯",
                subtitle: "深海神秘探险巨制",
                coverUrl: "https://images.unsplash.com/photo-1682687220063-4742bd7fd538?w=600&q=80",
                playbackUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                duration: "01:00",
                rating: "9.4",
                tags: ["冒险", "自然"],
                isTop10: true,
                rankNumber: 4,
                sourceName: "华视影院"
            ),
            VideoItem(
                id: "top-5",
                title: "网络迷踪：赛博幽灵",
                subtitle: "反乌托邦黑客惊悚短剧",
                coverUrl: "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=600&q=80",
                playbackUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyBlazes.mp4",
                duration: "00:15",
                rating: "9.3",
                tags: ["短剧", "悬疑"],
                isTop10: true,
                rankNumber: 5,
                sourceName: "黄果短剧"
            )
        ]
        
        // 为你推荐
        self.recommendedVideos = [
            VideoItem(
                id: "rec-1",
                title: "命运石之门：虚空境界",
                subtitle: "现象级硬核科幻番剧",
                coverUrl: "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&q=80",
                playbackUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                duration: "12:14",
                rating: "9.9",
                tags: ["动漫", "神作"],
                category: "动漫",
                sourceName: "91动漫"
            ),
            VideoItem(
                id: "rec-2",
                title: "繁华落尽：风月俏佳人",
                subtitle: "民国豪门恩怨连续剧",
                coverUrl: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=600&q=80",
                playbackUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
                duration: "45:00",
                rating: "9.2",
                tags: ["连续剧", "剧情"],
                category: "电视剧",
                sourceName: "华视影院"
            ),
            VideoItem(
                id: "rec-3",
                title: "总裁的替身娇妻",
                subtitle: "爆火逆袭爽文短剧 80 集全",
                coverUrl: "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=600&q=80",
                playbackUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WhatCarCanYouGetForAGrand.mp4",
                duration: "01:30",
                rating: "9.1",
                tags: ["都市", "短剧"],
                category: "短剧",
                sourceName: "黄果短剧"
            ),
            VideoItem(
                id: "rec-4",
                title: "巴黎狂想曲：香榭丽舍",
                subtitle: "海外精选 4K 浪漫风情",
                coverUrl: "https://images.unsplash.com/photo-1502602898657-3e91760cbb34?w=600&q=80",
                playbackUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                duration: "09:56",
                rating: "9.4",
                tags: ["海外线路", "4K HDR"],
                category: "海外线路",
                sourceName: "XVideos / Beeg"
            )
        ]
        
        // 大陆直连稳定专区
        self.directLineVideos = [
            VideoItem(
                id: "dir-1",
                title: "极光直连：华视经典剧场",
                subtitle: "低延迟秒开 大陆节点直连",
                coverUrl: "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=600&q=80",
                playbackUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                duration: "10:53",
                rating: "9.7",
                tags: ["直连免翻", "超清 60fps"],
                sourceName: "华视影院"
            ),
            VideoItem(
                id: "dir-2",
                title: "黄果短剧专场：逆转人生",
                subtitle: "快节奏爽剧 1080P",
                coverUrl: "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=600&q=80",
                playbackUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                duration: "02:15",
                rating: "9.3",
                tags: ["黄果短剧", "每日更新"],
                sourceName: "黄果短剧"
            )
        ]
    }
}
