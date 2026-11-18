import Observation
import SwiftUI

public enum AppImmersiveRoute: Hashable {
    case video(VideoItem)
    case live(stream: LiveStreamItem, relatedStreams: [LiveStreamItem])
    case comic(ComicItem, initialChapterIndex: Int, restoreProgress: Bool = true)
    case forumPost(ForumPostItem)
    case game
}

/// 全局播放与沉浸式详情路由协调器：唯一持有并管理超越 TabView/Sidebar 的全屏播放与阅读页
@Observable
@MainActor
public final class PlayerCoordinator {
    public static let shared = PlayerCoordinator()

    public var activeRoute: AppImmersiveRoute?
    public var requestedTab: MainTab?
    public var requestedSearchQuery: String?
    public var requestedSearchScope: String?
    public var openSearchTrigger: Int = 0
    public var openWatchlistTrigger: Int = 0
    public var refreshActiveTabTrigger: Int = 0
    public var togglePlayPauseTrigger: Int = 0
    public var toggleFullScreenTrigger: Int = 0

    public var activeVideo: VideoItem? {
        get {
            if case .video(let video) = activeRoute {
                return video
            }
            return nil
        }
        set {
            activeRoute = newValue.map { .video($0) }
        }
    }

    public init() {}

    public func play(_ video: VideoItem) {
        activeRoute = .video(video)
    }

    public func openLive(_ stream: LiveStreamItem, relatedStreams: [LiveStreamItem] = []) {
        activeRoute = .live(stream: stream, relatedStreams: relatedStreams)
    }

    public func openComic(_ comic: ComicItem, initialChapterIndex: Int = 0, restoreProgress: Bool = true) {
        activeRoute = .comic(comic, initialChapterIndex: max(0, initialChapterIndex), restoreProgress: restoreProgress)
    }

    public func openForumPost(_ post: ForumPostItem) {
        activeRoute = .forumPost(post)
    }

    public func openGame() {
        activeRoute = .game
    }

    public func dismiss() {
        activeRoute = nil
    }

    public func switchTab(_ tab: MainTab) {
        activeRoute = nil
        requestedTab = tab
    }

    public func openSearch(query: String = "", scopeRawValue: String? = nil) {
        activeRoute = nil
        requestedSearchQuery = query
        requestedSearchScope = scopeRawValue
        openSearchTrigger &+= 1
    }

    public func openWatchlist() {
        activeRoute = nil
        requestedTab = .profile
        openWatchlistTrigger &+= 1
    }

    public func triggerRefreshActiveTab() {
        refreshActiveTabTrigger &+= 1
    }

    public func triggerPlayPause() {
        togglePlayPauseTrigger &+= 1
    }

    public func triggerToggleFullScreen() {
        toggleFullScreenTrigger &+= 1
    }
}
