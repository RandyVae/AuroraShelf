import SwiftUI

/// iPadOS 27 系统顶部菜单栏（Menu Bar）与外接硬件键盘快捷键指令集
public struct AuroraAppCommands: Commands {
    @ObservedObject private var locManager = LocalizationManager.shared

    public init() {}

    public var body: some Commands {
        CommandGroup(after: .toolbar) {
            Button("tab.home".localized) {
                PlayerCoordinator.shared.switchTab(.home)
            }
            .keyboardShortcut("1", modifiers: .command)

            Button("tab.comics".localized) {
                PlayerCoordinator.shared.switchTab(.comics)
            }
            .keyboardShortcut("2", modifiers: .command)

            Button("tab.live".localized) {
                PlayerCoordinator.shared.switchTab(.live)
            }
            .keyboardShortcut("3", modifiers: .command)

            Button("tab.forum".localized) {
                PlayerCoordinator.shared.switchTab(.forum)
            }
            .keyboardShortcut("4", modifiers: .command)

            Button("tab.library".localized) {
                PlayerCoordinator.shared.switchTab(.profile)
            }
            .keyboardShortcut("5", modifiers: .command)

            Divider()

            Button("cmd.refresh".localized) {
                PlayerCoordinator.shared.triggerRefreshActiveTab()
            }
            .keyboardShortcut("r", modifiers: .command)
        }

        CommandGroup(replacing: .textEditing) {
            Button("cmd.global_search".localized) {
                PlayerCoordinator.shared.openSearch()
            }
            .keyboardShortcut("f", modifiers: .command)

            Button("watchlist.title".localized) {
                PlayerCoordinator.shared.openWatchlist()
            }
            .keyboardShortcut("b", modifiers: .command)
        }

        CommandMenu("cmd.playback_menu".localized) {
            Button("cmd.play_pause".localized) {
                PlayerCoordinator.shared.triggerPlayPause()
            }
            .keyboardShortcut("p", modifiers: [.command, .shift])

            Button("cmd.toggle_fullscreen".localized) {
                PlayerCoordinator.shared.triggerToggleFullScreen()
            }
            .keyboardShortcut("f", modifiers: [.command, .control])
        }
    }
}
