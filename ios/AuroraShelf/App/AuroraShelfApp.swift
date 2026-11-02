import SwiftUI

@main
struct AuroraShelfApp: App {
    init() {
        // 强制暗色流媒体风格
        UIView.appearance().overrideUserInterfaceStyle = .dark
    }
    
    var body: some Scene {
        WindowGroup {
            MainContainerView()
                .preferredColorScheme(.dark)
        }
    }
}
