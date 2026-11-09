import Foundation
import Combine

/// 全局播放流预热与极速秒开管理器（支持全源后台异步预解析与多级内存缓存）
@MainActor
public final class VideoPreloadManager: ObservableObject {
    public static let shared = VideoPreloadManager()
    
    public struct CachedStream: Sendable {
        public let result: PlaybackSourceResult
        public let cachedAt: Date
        public let expiresAt: Date
        
        public var isExpired: Bool {
            Date() > expiresAt
        }
    }
    
    // 内存缓存字典与飞行中任务表
    private var streamCache: [String: CachedStream] = [:]
    private var inFlightTasks: [String: Task<PlaybackSourceResult, Error>] = [:]
    private let defaultTTL: TimeInterval = 7200 // 2小时有效期
    
    private init() {}
    
    /// 计算 URL 的所有等价别名键（例如华视的详情页 /voddetail/123.html 与第 1 集 /vodplay/123-1-1.html 等价）
    public static func canonicalKeys(for url: String) -> [String] {
        var keys = [url]
        guard !url.isEmpty else { return keys }
        if url.contains("huavod.com") {
            if let match = url.range(of: #"/voddetail/(\d+)"#, options: .regularExpression) {
                let vodId = String(url[match]).replacingOccurrences(of: "/voddetail/", with: "")
                let playUrl = "https://huavod.com/vodplay/\(vodId)-1-1.html"
                if !keys.contains(playUrl) { keys.append(playUrl) }
            } else if let match = url.range(of: #"/vodplay/(\d+)-1-1"#, options: .regularExpression) {
                let vodId = String(url[match]).replacingOccurrences(of: "/vodplay/", with: "").components(separatedBy: "-").first ?? ""
                if !vodId.isEmpty {
                    let detailUrl = "https://huavod.com/voddetail/\(vodId).html"
                    if !keys.contains(detailUrl) { keys.append(detailUrl) }
                }
            }
        } else if url.contains("huangguoai.com") {
            if let match = url.range(of: #"/(?:detail|video|play)/(\d+)"#, options: .regularExpression) {
                let raw = String(url[match])
                let id = raw.components(separatedBy: "/").filter({ !$0.isEmpty }).last ?? ""
                if !id.isEmpty {
                    let detailKey = "https://huangguoai.com/detail/\(id)/"
                    let videoKey = "https://huangguoai.com/video/\(id)/"
                    if !keys.contains(detailKey) { keys.append(detailKey) }
                    if !keys.contains(videoKey) { keys.append(videoKey) }
                }
            }
        }
        return keys
    }

    /// 检查指定播放页 URL 是否已在内存缓存中且未过期（支持别名查询）
    public func cachedResult(for playbackUrl: String) -> PlaybackSourceResult? {
        let keys = Self.canonicalKeys(for: playbackUrl)
        for key in keys {
            if let entry = streamCache[key] {
                if entry.isExpired {
                    streamCache.removeValue(forKey: key)
                } else {
                    return entry.result
                }
            }
        }
        return nil
    }
    
    /// 获取正在解析中的任务（防击穿，支持别名查询）
    public func inFlightTask(for playbackUrl: String) -> Task<PlaybackSourceResult, Error>? {
        let keys = Self.canonicalKeys(for: playbackUrl)
        for key in keys {
            if let task = inFlightTasks[key] {
                return task
            }
        }
        return nil
    }
    
    /// 获取或发起播放流解析（Single-Flight 防击穿机制，彻底根除任务自身死锁）
    public func resolve(video: VideoItem) async throws -> PlaybackSourceResult {
        let key = video.playbackUrl
        guard !key.isEmpty else {
            return try await VideoRepository.shared.executeStreamResolution(video: video)
        }
        
        // 1. 命中缓存直接秒开返回
        if let cached = cachedResult(for: key) {
            return cached
        }
        
        // 2. 命中正在进行的在途任务（例如后台预热任务），等待其完成返回（防击穿）
        if let inFlight = inFlightTask(for: key) {
            return try await inFlight.value
        }
        
        // 3. 创建全新单飞任务并注册到 inFlightTasks
        let keys = Self.canonicalKeys(for: key)
        let task = Task<PlaybackSourceResult, Error> {
            let result = try await VideoRepository.shared.executeStreamResolution(video: video)
            return result
        }
        for k in keys {
            inFlightTasks[k] = task
        }
        
        do {
            let resolved = try await task.value
            let entry = CachedStream(
                result: resolved,
                cachedAt: Date(),
                expiresAt: Date().addingTimeInterval(self.defaultTTL)
            )
            for k in keys {
                self.streamCache[k] = entry
                self.inFlightTasks.removeValue(forKey: k)
            }
            return resolved
        } catch {
            for k in keys {
                self.inFlightTasks.removeValue(forKey: k)
            }
            throw error
        }
    }

    /// 智能预热单部视频：后台异步解析播放流并持久缓存在内存中
    public func prewarm(video: VideoItem) {
        let key = video.playbackUrl
        guard !key.isEmpty else { return }
        
        // 已缓存或已有正在进行的任务则无需重复触发
        if cachedResult(for: key) != nil || inFlightTask(for: key) != nil {
            return
        }
        
        Task { [weak self] in
            guard let self = self else { return }
            _ = try? await self.resolve(video: video)
        }
    }
    
    /// 批量预热视频列表（例如首屏推荐前 4~6 部视频）
    public func prewarmBatch(videos: [VideoItem], maxCount: Int = 6) {
        let targets = videos.prefix(maxCount)
        for video in targets {
            prewarm(video: video)
        }
    }
    
    /// 预热下一集剧集
    public func prewarmNextEpisode(pageUrl: String, title: String, sourceName: String) {
        guard !pageUrl.isEmpty else { return }
        let stubVideo = VideoItem(
            id: pageUrl,
            title: title,
            subtitle: "",
            coverUrl: "",
            playbackUrl: pageUrl,
            duration: "",
            rating: "",
            tags: [],
            category: "",
            isTop10: false,
            rankNumber: nil,
            sourceName: sourceName
        )
        prewarm(video: stubVideo)
    }
    
    /// 针对多集剧集的滑动窗口向后预热（预热 currentIndex + 1 到 currentIndex + windowSize 集）
    public func prewarmSlidingWindow(
        video: VideoItem,
        episodes: [PlaybackEpisode],
        currentIndex: Int,
        windowSize: Int = 2
    ) {
        guard !episodes.isEmpty else { return }
        let nextEps = episodes.filter { $0.number > currentIndex && $0.number <= currentIndex + windowSize }
        for ep in nextEps {
            prewarmNextEpisode(
                pageUrl: ep.pageUrl,
                title: "\(video.title) \(ep.label)",
                sourceName: video.sourceName
            )
        }
    }

    /// 手动存入解析结果（支持自动多别名同步存入）
    public func cache(result: PlaybackSourceResult, for playbackUrl: String) {
        let keys = Self.canonicalKeys(for: playbackUrl)
        let entry = CachedStream(
            result: result,
            cachedAt: Date(),
            expiresAt: Date().addingTimeInterval(defaultTTL)
        )
        for k in keys {
            streamCache[k] = entry
        }
    }
    
    /// 清除所有缓存
    public func clearCache() {
        streamCache.removeAll()
        inFlightTasks.values.forEach { $0.cancel() }
        inFlightTasks.removeAll()
    }
}
