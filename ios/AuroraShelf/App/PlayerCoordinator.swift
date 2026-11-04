import SwiftUI
import Observation

/// 全局播放协调器：唯一持有并管理当前活跃的全屏视频播放项
@Observable
@MainActor
public final class PlayerCoordinator {
    public static let shared = PlayerCoordinator()
    
    public var activeVideo: VideoItem? = nil
    
    public init() {}
    
    public func play(_ video: VideoItem) {
        activeVideo = video
    }
    
    public func dismiss() {
        activeVideo = nil
    }
}
