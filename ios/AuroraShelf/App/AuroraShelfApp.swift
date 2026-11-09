import SwiftUI

@main
struct AuroraShelfApp: App {
    init() {
        // 支持命令行参数指定初始视频源（便于自动化回归与验收截图）
        let args = ProcessInfo.processInfo.arguments
        if let idx = args.firstIndex(of: "--video-source"), idx + 1 < args.count {
            let srcId = args[idx + 1]
            let src = VideoSourceCatalog.find(by: srcId)
            VideoRepository.shared.switchSource(to: src)
        }
        // 启动全平台全面预热系统（涵盖视频、论坛与漫画，实现即点即看）
        GlobalStartupPrefetchEngine.shared.startGlobalWarmup()
    }
    
    var body: some Scene {
        WindowGroup {
            MainContainerView()
        }
    }
}
