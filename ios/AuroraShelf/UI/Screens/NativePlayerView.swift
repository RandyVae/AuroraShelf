import SwiftUI
import AVKit

/// 统一委托至 VideoDetailPlaybackScreen，消除双播放器代码分叉，同时对外提供 Apple TV 音轨与字幕选择器
public struct NativePlayerView: View {
    public let video: VideoItem
    public let onDismiss: () -> Void

    public init(video: VideoItem, onDismiss: @escaping () -> Void = {}) {
        self.video = video
        self.onDismiss = onDismiss
    }

    public var body: some View {
        VideoDetailPlaybackScreen(video: video, onDismiss: onDismiss)
    }
}

/// Apple TV 风格字幕与多音轨选择器（iOS 27 Liquid Glass 原生菜单按钮）
public struct PlayerMediaTrackMenu: View {
    public let playerItem: AVPlayerItem
    public let subtitleGroup: AVMediaSelectionGroup?
    public let audioGroup: AVMediaSelectionGroup?

    @State private var selectionRevision = 0

    public init(
        playerItem: AVPlayerItem,
        subtitleGroup: AVMediaSelectionGroup?,
        audioGroup: AVMediaSelectionGroup?
    ) {
        self.playerItem = playerItem
        self.subtitleGroup = subtitleGroup
        self.audioGroup = audioGroup
    }

    public var body: some View {
        let _ = selectionRevision
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        Menu {
            if let subtitleGroup, !subtitleGroup.options.isEmpty {
                Section("字幕") {
                    Button {
                        select(nil, in: subtitleGroup)
                    } label: {
                        trackLabel("关闭", selected: selectedOption(in: subtitleGroup) == nil)
                    }

                    ForEach(Array(subtitleGroup.options.enumerated()), id: \.offset) { entry in
                        let option = entry.element
                        Button {
                            select(option, in: subtitleGroup)
                        } label: {
                            trackLabel(option.displayName, selected: isSelected(option, in: subtitleGroup))
                        }
                    }
                }
            }

            if let audioGroup, audioGroup.options.count > 1 {
                Section("音频") {
                    ForEach(Array(audioGroup.options.enumerated()), id: \.offset) { entry in
                        let option = entry.element
                        Button {
                            select(option, in: audioGroup)
                        } label: {
                            trackLabel(option.displayName, selected: isSelected(option, in: audioGroup))
                        }
                    }
                }
            }
        } label: {
            Image(systemName: selectedSubtitle != nil ? "captions.bubble.fill" : "captions.bubble")
                .font(.system(size: isPad ? 15 : 13, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: isPad ? 32 : 26, height: isPad ? 32 : 26)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
        .frame(minWidth: 44, minHeight: 44)
        .accessibilityLabel("字幕与音轨")
        .accessibilityHint("选择可用的字幕或音频轨道")
    }

    private var selectedSubtitle: AVMediaSelectionOption? {
        guard let subtitleGroup else { return nil }
        return selectedOption(in: subtitleGroup)
    }

    private func selectedOption(in group: AVMediaSelectionGroup) -> AVMediaSelectionOption? {
        playerItem.currentMediaSelection.selectedMediaOption(in: group)
    }

    private func isSelected(_ option: AVMediaSelectionOption, in group: AVMediaSelectionGroup) -> Bool {
        selectedOption(in: group)?.isEqual(option) == true
    }

    private func select(_ option: AVMediaSelectionOption?, in group: AVMediaSelectionGroup) {
        playerItem.select(option, in: group)
        selectionRevision &+= 1
    }

    private func trackLabel(_ title: String, selected: Bool) -> some View {
        HStack {
            Text(title)
            Spacer(minLength: AuroraMetrics.spacingL)
            if selected {
                Image(systemName: "checkmark")
                    .accessibilityHidden(true)
            }
        }
    }
}
