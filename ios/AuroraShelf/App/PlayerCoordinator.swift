import SwiftUI
import Observation

public enum AppImmersiveRoute: Hashable {
    case video(VideoItem)
    case live(stream: LiveStreamItem, relatedStreams: [LiveStreamItem])
    case comic(ComicItem)
    case forumPost(ForumPostItem)
    case game
}

/// 全局播放与沉浸式详情路由协调器：唯一持有并管理超越 TabView/Sidebar 的全屏播放与阅读页
@Observable
@MainActor
public final class PlayerCoordinator {
    public static let shared = PlayerCoordinator()
    
    public var activeRoute: AppImmersiveRoute? = nil

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

    public func openComic(_ comic: ComicItem) {
        activeRoute = .comic(comic)
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
}
