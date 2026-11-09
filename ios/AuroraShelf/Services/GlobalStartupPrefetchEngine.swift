import Foundation
import SwiftUI
import Combine

/// 全平台启动全量预热中枢（涵盖视频全货架、论坛热帖图集、漫画推荐与热门首话）
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
    
    /// 从用户启动软件的一刻起触发全量后台静默预热（低调度优先级，严禁争抢首屏渲染线程）
    public func startGlobalWarmup() {
        guard !hasStarted else { return }
        hasStarted = true
        isWarmingUp = true
        
        warmupTask = Task.detached(priority: .utility) { [weak self] in
            // 1. 视频模块全货架预热
            await self?.warmupVideoModule()
            
            // 2. 论坛热帖与图集预热
            await self?.warmupForumModule()
            
            // 3. 漫画推荐与热门前话预热
            await self?.warmupComicModule()
            
            await MainActor.run {
                self?.isWarmingUp = false
            }
        }
    }
    
    // MARK: - 1. 视频模块预热
    
    private func warmupVideoModule() async {
        do {
            let repo = await VideoRepository.shared
            let currentSource = await repo.currentSource
            
            // 预先拉取首屏推荐视频
            let feed = try await repo.fetchVideosForSource(currentSource, category: "全部", page: 1)
            
            // 预热前 8 部视频的海报高清图入磁盘与内存缓存
            for video in feed.prefix(8) {
                if !video.coverUrl.isEmpty {
                    CoverImageLoader.shared.preload(
                        url: video.coverUrl,
                        baseURL: video.coverBaseURL
                    )
                }
            }
            
            // 对排头前 3 部推荐视频进行播放流静默预解析，实现真正即点即开
            for video in feed.prefix(3) {
                await VideoPreloadManager.shared.prewarm(video: video)
            }
            
            await MainActor.run {
                self.warmedVideoCount = feed.count
            }
        } catch {
            // 静默处理预热异常
        }
    }
    
    // MARK: - 2. 论坛热帖预热
    
    private func warmupForumModule() async {
        do {
            let forumRepo = await ForumRepository.shared
            // 预刷新论坛主列表
            await forumRepo.loadPosts()
            let posts = await forumRepo.posts
            
            // 预热前 10 篇热帖的封面图片
            for post in posts.prefix(10) {
                if let cover = post.previewImageUrl, !cover.isEmpty {
                    CoverImageLoader.shared.preload(url: cover, baseURL: nil)
                }
            }
            
            await MainActor.run {
                self.warmedForumCount = posts.count
            }
        } catch {
            // 静默处理预热异常
        }
    }
    
    // MARK: - 3. 漫画模块预热
    
    private func warmupComicModule() async {
        do {
            let comicRepo = await ComicRepository.shared
            let source = await comicRepo.currentSource
            let comics = try await comicRepo.fetchComics(for: source, category: "全部", page: 1)
            
            // 预热前 6 部热门漫画的封面
            for comic in comics.prefix(6) {
                if !comic.coverUrl.isEmpty {
                    CoverImageLoader.shared.preload(url: comic.coverUrl, baseURL: nil)
                }
            }
            
            // 对第一部热门漫画拉取章节列表并静默预热第一话的前 3 页图片
            if let firstComic = comics.first {
                let detail = try await comicRepo.fetchComicDetails(firstComic)
                if let firstChapter = detail.chapters.first {
                    let pages = try await comicRepo.fetchChapterPages(
                        comicId: firstComic.id,
                        sourceId: firstComic.sourceId,
                        chapterId: firstChapter.id
                    )
                    for page in pages.prefix(3) {
                        CoverImageLoader.shared.preload(url: page.imageUrl, baseURL: nil)
                    }
                }
            }
            
            await MainActor.run {
                self.warmedComicCount = comics.count
            }
        } catch {
            // 静默处理预热异常
        }
    }
    
    // MARK: - 4. 剧集滑动窗口持续向后缓存中枢
    
    /// 当播放第 N 集时，预热向后 N+1、N+2 集的播放流与缓存
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
    
    /// 当第 N 集完成离线缓存后，自动向后持续缓存第 N+1 集
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
