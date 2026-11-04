import SwiftUI

// MARK: - Aurora Design Tokens (Single Source of Truth)

public enum AuroraColor {
    // 品牌
    public static let brand        = Color(rgb: 0xFF4F7B)   // AuroraCoral，1:1 对齐 Android
    public static let brandDeep    = Color(rgb: 0xB20A14)
    public static let accentRed    = Color(rgb: 0xE50914)   // 仅用于排行/徽章等标记

    // 表面（层级由低到高）
    public static let canvas       = Color(rgb: 0x080A0F)   // 页面底，全 App 唯一一处
    public static let surface      = Color(rgb: 0x141724)
    public static let surfaceCard  = Color(rgb: 0x1C2130)
    public static let surfaceHigh  = Color(rgb: 0x232838)

    // 文字（层级，遵循系统语义）
    public static let textPrimary   = Color.primary
    public static let textSecondary = Color.secondary
    public static let textTertiary  = Color(white: 0.45)

    // 语义状态
    public static let success     = Color.green
    public static let warning     = Color.orange
    public static let destructive = Color.red

    // 来源标识色（与 Android Theme.kt 对齐）
    public static let forestGreen  = Color(rgb: 0x2E7D32)   // 草榴社区标识绿

    // 描边 / 发丝线
    public static let hairline = Color.primary.opacity(0.12)
}

public enum AuroraMetrics {
    public static let spacingXS: CGFloat = 4
    public static let spacingS:  CGFloat = 8
    public static let spacingM:  CGFloat = 12
    public static let spacingL:  CGFloat = 16
    public static let spacingXL: CGFloat = 24
    public static let spacingXXL: CGFloat = 32
    public static let spacingXXXL: CGFloat = 48

    public static let cardRadius: CGFloat = 12
    public static let controlRadius: CGFloat = 10
    public static let minTouchTarget: CGFloat = 44   // HIG 最小触控

    public static let posterAspect: CGFloat = 2.0 / 3.0
    public static let heroAspect:   CGFloat = 4.0 / 5.0   // 竖版 Hero，见 §4.1

    public static let contentInset: CGFloat = 16
}

public enum AuroraMotion {
    public static let quick  = Animation.spring(response: 0.24, dampingFraction: 0.82)
    public static let smooth = Animation.spring(response: 0.36, dampingFraction: 0.78)
}

// MARK: - Color Initializer Extensions

public extension Color {
    /// 24 位 RGB 整数初始化（无 Alpha 通道，不透明）
    init(rgb: UInt32) {
        let r = Double((rgb >> 16) & 0xff) / 255.0
        let g = Double((rgb >> 8) & 0xff) / 255.0
        let b = Double(rgb & 0xff) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: 1.0)
    }

    /// 32 位 RGBA 整数初始化
    init(rgba: UInt32) {
        let r = Double((rgba >> 24) & 0xff) / 255.0
        let g = Double((rgba >> 16) & 0xff) / 255.0
        let b = Double((rgba >> 8) & 0xff) / 255.0
        let a = Double(rgba & 0xff) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: a)
    }

    /// 弃用的十六进制初始化（请迁移至 init(rgb:) 或 init(rgba:)）
    @available(*, deprecated, message: "使用 init(rgb:) 或 init(rgba:)")
    init(hex: UInt32, alpha: Double = 1.0) {
        let r = Double((hex >> 16) & 0xff) / 255.0
        let g = Double((hex >> 8) & 0xff) / 255.0
        let b = Double(hex & 0xff) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: alpha)
    }
}
