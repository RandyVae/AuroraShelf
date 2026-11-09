import SwiftUI

/// 沉浸式浮层与全屏播放器统一顶栏组件（遵循 Apple HIG 与 iOS 26+ 原生 Liquid Glass 规范）
public struct PlayerOverlayBar<TrailingContent: View>: View {
    public let title: String
    public let subtitle: String?
    public let customTopPadding: CGFloat?
    public let onDismiss: () -> Void
    public let trailingContent: TrailingContent
    
    public init(
        title: String,
        subtitle: String? = nil,
        customTopPadding: CGFloat? = nil,
        onDismiss: @escaping () -> Void,
        @ViewBuilder trailingContent: () -> TrailingContent
    ) {
        self.title = title
        self.subtitle = subtitle
        self.customTopPadding = customTopPadding
        self.onDismiss = onDismiss
        self.trailingContent = trailingContent()
    }
    
    public var body: some View {
        let topPadding = customTopPadding ?? (DeviceLayout.safeAreaTop + 6)

        ZStack(alignment: .top) {
            // 暗调渐变底衬，确保文字与按钮在任何背景（如漫画白底）上均清晰可读
            LinearGradient(
                colors: [Color.black.opacity(0.85), Color.black.opacity(0.4), Color.clear],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: max(topPadding + 54, 120))
            .allowsHitTesting(false)

            HStack(spacing: AuroraMetrics.spacingM) {
                Button(action: {
                    let impact = UIImpactFeedbackGenerator(style: .medium)
                    impact.impactOccurred()
                    onDismiss()
                }) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 38, height: 38)
                        .background(Color.black.opacity(0.45), in: Circle())
                        .glassEffect(.regular.interactive(), in: .circle)
                        .contentShape(Rectangle())
                        .frame(minWidth: 44, minHeight: 44)
                }
                .buttonStyle(.plain)
                .contentShape(Rectangle())
                .accessibilityLabel("返回")
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.headline)
                        .foregroundStyle(.white)
                        .lineLimit(1)

                    if let subtitle, !subtitle.isEmpty {
                        Text(subtitle)
                            .font(.caption)
                            .foregroundStyle(Color.white.opacity(0.75))
                            .lineLimit(1)
                    }
                }
                .layoutPriority(1)

                Spacer()

                trailingContent
            }
            .padding(.horizontal, AuroraMetrics.contentInset)
            .padding(.top, topPadding)
        }
    }
}

public extension PlayerOverlayBar where TrailingContent == EmptyView {
    init(
        title: String,
        subtitle: String? = nil,
        customTopPadding: CGFloat? = nil,
        onDismiss: @escaping () -> Void
    ) {
        self.init(title: title, subtitle: subtitle, customTopPadding: customTopPadding, onDismiss: onDismiss, trailingContent: { EmptyView() })
    }
}
