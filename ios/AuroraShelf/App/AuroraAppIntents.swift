import AppIntents
import Foundation

/// Spotlight / Siri / 快捷指令：继续播放最近观看视频
public struct ContinueWatchingIntent: AppIntent {
    public static let title: LocalizedStringResource = "Continue Watching"
    public static let description = IntentDescription("Resume your most recent video in Netgrey.")
    public static let openAppWhenRun: Bool = true

    public init() {}

    @MainActor
    public func perform() async throws -> some IntentResult {
        if let recent = PlaybackProgressStore.shared.upNextRecords.first {
            PlayerCoordinator.shared.play(recent.video)
        } else {
            PlayerCoordinator.shared.switchTab(.home)
        }
        return .result()
    }
}

/// Spotlight / Siri / 快捷指令：打开我的片单
public struct OpenWatchlistIntent: AppIntent {
    public static let title: LocalizedStringResource = "Open Watchlist"
    public static let description = IntentDescription("Open your saved watchlist in Netgrey.")
    public static let openAppWhenRun: Bool = true

    public init() {}

    @MainActor
    public func perform() async throws -> some IntentResult {
        PlayerCoordinator.shared.openWatchlist()
        return .result()
    }
}

/// Spotlight / Siri / 快捷指令：在 Netgrey 中搜索内容
public struct SearchAuroraIntent: AppIntent {
    public static let title: LocalizedStringResource = "Search Netgrey"
    public static let description = IntentDescription("Search videos, comics, live streams, and community posts.")
    public static let openAppWhenRun: Bool = true

    @Parameter(title: "Query")
    public var query: String?

    public init() {}

    public init(query: String?) {
        self.query = query
    }

    @MainActor
    public func perform() async throws -> some IntentResult {
        PlayerCoordinator.shared.openSearch(query: query ?? "")
        return .result()
    }
}

/// 系统级快捷指令暴露（零网络请求、纯本地路由交接）
public struct AuroraShortcutsProvider: AppShortcutsProvider {
    public static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: ContinueWatchingIntent(),
            phrases: [
                "Continue watching in \(.applicationName)",
                "Resume playback in \(.applicationName)"
            ],
            shortTitle: "Continue Watching",
            systemImageName: "play.tv.fill"
        )
        AppShortcut(
            intent: OpenWatchlistIntent(),
            phrases: [
                "Open watchlist in \(.applicationName)",
                "Show my list in \(.applicationName)"
            ],
            shortTitle: "Watchlist",
            systemImageName: "bookmark.fill"
        )
        AppShortcut(
            intent: SearchAuroraIntent(),
            phrases: [
                "Search in \(.applicationName)"
            ],
            shortTitle: "Search",
            systemImageName: "magnifyingglass"
        )
    }
}
