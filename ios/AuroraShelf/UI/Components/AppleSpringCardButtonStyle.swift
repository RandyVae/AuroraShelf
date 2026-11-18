import SwiftUI

/// Apple TV / iPadOS 27 原生感知弹簧与指针悬停卡片按钮样式
/// - 遵循 `accessibilityReduceMotion`：开启减弱动态效果时仅保留柔和明暗反馈，不做弹性缩放
/// - 消除滚动列表时的按压误震动（不再在 `isPressed` 瞬间触发 Impact Haptic）
/// - 内置 `.hoverEffect(.lift)`：连接妙控键盘或触控板时全站卡片自动支持原生指针磁吸抬升
public struct AppleSpringCardButtonStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public init() {}

    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.96 : 1.0)
            .opacity(configuration.isPressed && reduceMotion ? 0.84 : 1.0)
            .contentShape(RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous))
            .hoverEffect(.lift)
            .animation(AuroraMotion.quick(reduceMotion: reduceMotion), value: configuration.isPressed)
    }
}
