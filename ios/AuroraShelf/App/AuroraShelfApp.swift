import SwiftUI

@main
struct AuroraShelfApp: App {
    @UIApplicationDelegateAdaptor(AuroraAppDelegate.self) private var appDelegate

    init() {
        let args = ProcessInfo.processInfo.arguments
        if let langIdx = args.firstIndex(of: "--lang"), langIdx + 1 < args.count,
           let parsedLang = AppLanguage(rawValue: args[langIdx + 1]) {
            LocalizationManager.shared.setLanguage(parsedLang)
        }
        if let themeIdx = args.firstIndex(of: "--theme"), themeIdx + 1 < args.count,
           let parsedTheme = AuroraThemeMode(rawValue: args[themeIdx + 1]) {
            UserDefaults.standard.set(parsedTheme.rawValue, forKey: "aurora_theme_mode")
        }
        // 确保 VideoRepository 单例按启动参数（如 --video-source）完成一次初始化
        _ = VideoRepository.shared
        // 启动全平台全面预热系统（涵盖视频、论坛与漫画，实现即点即看）
        GlobalStartupPrefetchEngine.shared.startGlobalWarmup()
    }
    
    var body: some Scene {
        WindowGroup {
            MainContainerView()
        }
        .commands {
            AuroraAppCommands()
        }
    }
}
