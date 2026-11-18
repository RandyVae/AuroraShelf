import AVKit
import SwiftUI

/// Apple TV 风格字幕与多音轨选择器（iOS 27 `Glass.clear` 原生高透液态玻璃菜单按钮）
public struct PlayerMediaTrackMenu: View {
    public let playerItem: AVPlayerItem
    public let subtitleGroup: AVMediaSelectionGroup?
    public let audioGroup: AVMediaSelectionGroup?
    public var showLabelText: Bool = false

    @State private var selectionRevision = 0

    public init(
        playerItem: AVPlayerItem,
        subtitleGroup: AVMediaSelectionGroup?,
        audioGroup: AVMediaSelectionGroup?,
        showLabelText: Bool = false
    ) {
        self.playerItem = playerItem
        self.subtitleGroup = subtitleGroup
        self.audioGroup = audioGroup
        self.showLabelText = showLabelText
    }

    public var body: some View {
        let _ = selectionRevision
        Menu {
            if let subtitleGroup, !subtitleGroup.options.isEmpty {
                Section("player.tracks_subtitles".localized) {
                    Button {
                        select(nil, in: subtitleGroup)
                    } label: {
                        trackLabel("player.tracks_off".localized, selected: selectedOption(in: subtitleGroup) == nil)
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
                Section("player.tracks_audio".localized) {
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
            if showLabelText {
                HStack(spacing: 5) {
                    Image(systemName: selectedSubtitle != nil ? "captions.bubble.fill" : "captions.bubble")
                        .font(.footnote.weight(.semibold))
                        .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                    Text("player.tracks_btn".localized)
                        .font(.footnote.weight(.semibold))
                        .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                        .lineLimit(1)
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .fixedSize(horizontal: true, vertical: false)
            } else {
                Image(systemName: selectedSubtitle != nil ? "captions.bubble.fill" : "captions.bubble")
                    .font(.footnote.weight(.semibold))
                    .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                    .foregroundStyle(.white)
                    .frame(width: 26, height: 26)
            }
        }
        .modifier(MediaTrackGlassModifier(isCapsule: showLabelText))
        .frame(minWidth: AuroraMetrics.minTouchTarget, minHeight: AuroraMetrics.minTouchTarget)
        .accessibilityLabel("player.tracks_a11y".localized)
        .accessibilityHint("player.tracks_hint".localized)
    }

    private struct MediaTrackGlassModifier: ViewModifier {
        let isCapsule: Bool

        func body(content: Content) -> some View {
            if isCapsule {
                content.playerClearGlassCapsuleButton()
            } else {
                content.playerClearGlassCircleButton()
            }
        }
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
