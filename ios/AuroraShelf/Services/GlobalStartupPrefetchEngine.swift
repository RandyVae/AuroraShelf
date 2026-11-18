import Combine
import Foundation
import SwiftUI

/// 全平台启动静默预热中枢（遵循按需懒加载准则，等待首页稳定后再预热首屏封面与流，绝不在未打开标签页制造冷启动网络风暴）
@MainActor
public final class GlobalStartupPrefetchEngine: ObservableObject {
    public static let shared = GlobalStartupPrefetchEngine()

    @Published public private(set) var isWarmingUp: Bool = false
    @Published public private(set) var warmedVideoCount: Int = 0
    @Published public private(set) var warmedForumCount: Int = 0
    @Published public private(set) var warmedComicCount: Int = 0

    private var warmupTask: Task<Void, Never>?
    private var hasStarted = false

    private init() {}

    /// 启动期轻量级非阻塞预热：同步远端自愈配置、刷新登录态、等待首页首个视频源就绪后预热首屏封面与排头流
    public func startGlobalWarmup() {
        guard !hasStarted else { return }
        hasStarted = true
        isWarmingUp = true

        // 1. 远端自愈配置清单异步同步（3 秒短超时）与核心首页源 TCP/TLS 预连接
        RemoteSourceManifest.shared.syncInBackground()
        let warmURLs = [
            "https://huavod.com/",
            "https://huangguoai.com/"
        ].compactMap(URL.init(string:))
        AuroraNetworkEngine.shared.prewarmOrigins(warmURLs)

        // 2. 若本地已持有登录令牌，启动时静默刷新会话
        if AccountService.shared.hasToken {
            Task(priority: .utility) {
                await AccountService.shared.refresh()
            }
        }

        // 3. 等待首页 VideoRepository 首屏数据稳定后再预热首屏封面与前 2 部播放流（不主动请求未打开的论坛/漫画 Tab）
        warmupTask = Task(priority: .utility) { [weak self] in
            guard let self else { return }
            await self.warmupVideoModuleAfterInitialFeed()
            self.warmupCachedSnapshotsOnly()
            self.isWarmingUp = false
        }
    }

    private func warmupVideoModuleAfterInitialFeed() async {
        let repo = VideoRepository.shared
        var attempts = 0
        while repo.isLoading && repo.feedVideos.isEmpty && attempts < 40 && !Task.isCancelled {
            try? await Task.sleep(for: .milliseconds(250))
            attempts += 1
        }
        guard !Task.isCancelled else { return }

        let feed = repo.feedVideos
        for video in feed.prefix(8) where !video.coverUrl.isEmpty {
            CoverImageLoader.shared.preload(
                url: video.coverUrl,
                baseURL: video.coverBaseURL
            )
        }

        let maxStreamPrewarm = repo.currentSource.id == VideoSourceCatalog.huavod.id ? 1 : 2
        for video in feed.prefix(maxStreamPrewarm) {
            VideoPreloadManager.shared.prewarm(video: video)
        }

        self.warmedVideoCount = feed.count
    }

    /// 仅针对本地已落盘的 SWR 缓存快照预热少量封面（零额外列表网络请求）
    private func warmupCachedSnapshotsOnly() {
        let forumPosts = ForumRepository.shared.posts
        for post in forumPosts.prefix(4) {
            if let cover = post.previewImageUrl, !cover.isEmpty {
                CoverImageLoader.shared.preload(url: cover, baseURL: nil)
            }
        }
        self.warmedForumCount = forumPosts.count

        let comics = ComicRepository.shared.comics
        for comic in comics.prefix(4) where !comic.coverUrl.isEmpty {
            CoverImageLoader.shared.preload(url: comic.coverUrl, baseURL: nil)
        }
        self.warmedComicCount = comics.count
    }

    /// 按需预热指定漫画章节的前几页（使用 ComicPageImageLoader，严禁误用 CoverImageLoader 导致混淆/鉴权失效）
    public func prewarmComicPages(_ pages: [ComicPage], limit: Int = 3) {
        let targets = Array(pages.prefix(max(0, limit)))
        guard !targets.isEmpty else { return }
        Task.detached(priority: .utility) {
            for page in targets {
                guard !Task.isCancelled else { break }
                _ = try? await ComicPageImageLoader.shared.load(page)
            }
        }
    }

    // MARK: - 剧集滑动窗口持续向后缓存中枢

    public func handleEpisodePlaying(
        video: VideoItem,
        currentEpisode: PlaybackEpisode,
        allEpisodes: [PlaybackEpisode]
    ) {
        VideoPreloadManager.shared.prewarmSlidingWindow(
            video: video,
            episodes: allEpisodes,
            currentIndex: currentEpisode.number,
            windowSize: 2
        )
    }

    public func handleEpisodeDownloadCompleted(
        video: VideoItem,
        completedEpisode: PlaybackEpisode,
        allEpisodes: [PlaybackEpisode]
    ) {
        OfflineDownloadManager.shared.queueNextEpisodeIfAvailable(
            video: video,
            currentEpisode: completedEpisode,
            allEpisodes: allEpisodes
        )
    }
}
