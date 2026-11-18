import SwiftUI

@main
struct AuroraShelfApp: App {
    @UIApplicationDelegateAdaptor(AuroraAppDelegate.self) private var appDelegate

    init() {
        // 确保 VideoRepository 单例按启动参数（如 --video-source）完成一次初始化
        _ = VideoRepository.shared
        // 启动全平台全面预热系统（涵盖视频、论坛与漫画，实现即点即看）
        GlobalStartupPrefetchEngine.shared.startGlobalWarmup()
    }
    
    var body: some Scene {
        WindowGroup {
            MainContainerView()
        }
    }
}
