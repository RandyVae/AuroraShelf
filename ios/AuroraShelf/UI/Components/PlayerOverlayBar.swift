import SwiftUI

/// 沉浸式浮层与全屏播放器统一顶栏组件（遵循 Apple HIG 与 iOS 26+ 原生 Liquid Glass 规范）
public struct PlayerOverlayBar<TrailingContent: View>: View {
    public let title: String
    public let subtitle: String?
    public let onDismiss: () -> Void
    public let trailingContent: TrailingContent
    
    public init(
        title: String,
        subtitle: String? = nil,
        onDismiss: @escaping () -> Void,
        @ViewBuilder trailingContent: () -> TrailingContent
    ) {
        self.title = title
        self.subtitle = subtitle
        self.onDismiss = onDismiss
        self.trailingContent = trailingContent()
    }
    
    public var body: some View {
        HStack(spacing: AuroraMetrics.spacingM) {
            Button(action: {
                let impact = UIImpactFeedbackGenerator(style: .medium)
                impact.impactOccurred()
                onDismiss()
            }) {
                Image(systemName: "chevron.left")
                    .font(.headline)
                    .foregroundStyle(.white)
                    .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                    .glassEffect(.regular.interactive(), in: .circle)
            }
            .buttonStyle(.plain)
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
            
            Spacer()
            
            trailingContent
        }
        .padding(.horizontal, AuroraMetrics.contentInset)
        .padding(.top, AuroraMetrics.spacingM)
    }
}

public extension PlayerOverlayBar where TrailingContent == EmptyView {
    init(
        title: String,
        subtitle: String? = nil,
        onDismiss: @escaping () -> Void
    ) {
        self.init(title: title, subtitle: subtitle, onDismiss: onDismiss, trailingContent: { EmptyView() })
    }
}
