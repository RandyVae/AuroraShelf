import SwiftUI

/// 兼容别名层：已收敛转发至单一真源 AuroraColor / AuroraMetrics / AuroraMotion
/// 阶段 1-2 逐文件迁移后将彻底删除此文件
public enum AuroraTheme {
    // 转发至单一真源 AuroraColor
    public static let background      = AuroraColor.canvas
    public static let surface         = AuroraColor.surface
    public static let surfaceCard     = AuroraColor.surfaceCard
    public static let surfaceHigh     = AuroraColor.surfaceHigh
    
    // 品牌强调色
    public static let auroraCoral     = AuroraColor.brand
    public static let auroraBrandAccentDeep = AuroraColor.brandDeep
    public static let crimson         = AuroraColor.accentRed
    public static let crimsonGlow     = AuroraColor.brand
    public static let sky             = Color.blue
    public static let gold            = Color.yellow
    public static let forestGreen     = AuroraColor.forestGreen
    
    // 界面卡片与文字 Token
    public static let auroraMediaSurface = AuroraColor.surface
    public static let auroraMediaBorder  = AuroraColor.hairline
    public static let auroraDarkMuted    = AuroraColor.textSecondary
    
    // 文字色阶（已修正原先硬编码纯白缺陷，改用系统语义色）
    public static let textPrimary     = AuroraColor.textPrimary
    public static let textSecondary   = AuroraColor.textSecondary
    public static let textTertiary    = AuroraColor.textTertiary
    
    // 边框微光
    public static let glassBorder     = AuroraColor.hairline
    public static let glassGlowBorder = AuroraColor.accentRed.opacity(0.35)
    
    // 物理弹性动画 Token
    public static let appleSpring     = AuroraMotion.smooth
    public static let quickSpring     = AuroraMotion.quick
}

public struct ApplePressEffect: ViewModifier {
    @State private var isPressed = false
    public let scale: CGFloat
    
    public init(scale: CGFloat = 0.96) {
        self.scale = scale
    }
    
    public func body(content: Content) -> some View {
        content
            .scaleEffect(isPressed ? scale : 1.0)
            .opacity(isPressed ? 0.88 : 1.0)
            .animation(AuroraTheme.quickSpring, value: isPressed)
            .simultaneousGesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in
                        if !isPressed {
                            isPressed = true
                        }
                    }
                    .onEnded { _ in
                        isPressed = false
                    }
            )
    }
}

public extension View {
    func applePressEffect(scale: CGFloat = 0.96) -> some View {
        self.modifier(ApplePressEffect(scale: scale))
    }
}
