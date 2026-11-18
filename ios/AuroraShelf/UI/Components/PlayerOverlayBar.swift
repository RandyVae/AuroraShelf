import SwiftUI

/// 沉浸式浮层与全屏阅读器统一顶栏组件（遵循 Apple HIG 与 iOS 27 原生 `Glass.clear` 高透液态玻璃规范）
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
            // 极轻柔可读性暗角（避免厚重黑幕吞噬 Glass.clear 边缘折射高光）
            LinearGradient(
                colors: [Color.black.opacity(0.38), Color.black.opacity(0.14), Color.clear],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: max(topPadding + 52, 108))
            .allowsHitTesting(false)

            GlassEffectContainer(spacing: AuroraMetrics.spacingM) {
                HStack(spacing: AuroraMetrics.spacingM) {
                    PlayerGlassIconButton(
                        systemName: "chevron.left",
                        accessibilityTitle: "返回",
                        visualDiameter: 40,
                        symbolSize: 16,
                        symbolWeight: .bold
                    ) {
                        let impact = UIImpactFeedbackGenerator(style: .medium)
                        impact.impactOccurred()
                        onDismiss()
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text(title)
                            .font(.headline)
                            .foregroundStyle(.white)
                            .shadow(color: .black.opacity(0.45), radius: 3, y: 1)
                            .lineLimit(1)

                        if let subtitle, !subtitle.isEmpty {
                            Text(subtitle)
                                .font(.caption)
                                .foregroundStyle(Color.white.opacity(0.82))
                                .shadow(color: .black.opacity(0.45), radius: 2, y: 1)
                                .lineLimit(1)
                        }
                    }
                    .layoutPriority(1)

                    Spacer()

                    trailingContent
                }
            }
            .environment(\.colorScheme, .dark)
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
