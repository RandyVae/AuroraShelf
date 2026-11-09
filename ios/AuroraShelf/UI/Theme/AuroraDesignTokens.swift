import SwiftUI
import UIKit

// MARK: - Aurora Design Tokens (Single Source of Truth)

public enum AuroraColor {
    // 品牌
    public static let brand        = Color(rgb: 0xFF4F7B)   // AuroraCoral，1:1 对齐 Android
    public static let brandDeep    = Color(rgb: 0xB20A14)
    public static let brandLabel = adaptive(
        light: 0xB20A14,
        dark: 0xFF4F7B,
        lightHigh: 0x8B0000,
        darkHigh: 0xFF91AC
    )
    public static let accentRed    = Color(rgb: 0xE50914)   // 仅用于排行/徽章等标记

    // 表面（层级由低到高）
    public static let canvas = adaptive(
        light: 0xF5F6F8,
        dark: 0x080A0F,
        lightHigh: 0xFFFFFF,
        darkHigh: 0x000000
    )
    public static let surface = adaptive(
        light: 0xFFFFFF,
        dark: 0x141724,
        lightHigh: 0xFFFFFF,
        darkHigh: 0x0A0C12
    )
    public static let surfaceCard = adaptive(
        light: 0xF0F1F4,
        dark: 0x1C2130,
        lightHigh: 0xF0F0F0,
        darkHigh: 0x171A22
    )
    public static let surfaceHigh = adaptive(
        light: 0xE5E7EB,
        dark: 0x232838,
        lightHigh: 0xE1E1E1,
        darkHigh: 0x282C37
    )

    // 文字（层级，遵循系统语义）
    public static let textPrimary = Color(uiColor: UIColor { traits in
        if traits.accessibilityContrast == .high {
            return traits.userInterfaceStyle == .dark ? UIColor.white : UIColor.black
        }
        return UIColor.label.resolvedColor(with: traits)
    })
    public static let textSecondary = Color(uiColor: UIColor { traits in
        if traits.accessibilityContrast == .high {
            return traits.userInterfaceStyle == .dark
                ? UIColor(white: 0.82, alpha: 1)
                : UIColor(white: 0.28, alpha: 1)
        }
        return UIColor.secondaryLabel.resolvedColor(with: traits)
    })
    public static let textTertiary = Color(uiColor: UIColor { traits in
        let isDark = traits.userInterfaceStyle == .dark
        let isHighContrast = traits.accessibilityContrast == .high
        let whiteLevel: CGFloat = isHighContrast ? (isDark ? 0.84 : 0.20) : (isDark ? 0.62 : 0.30)
        return UIColor(white: whiteLevel, alpha: 1)
    })
    public static let textOnBrand = Color(uiColor: UIColor { traits in
        if traits.accessibilityContrast == .high || traits.userInterfaceStyle == .light {
            return UIColor.black
        }
        return UIColor.white
    })

    // 语义状态
    public static let success     = Color.green
    public static let warning     = Color.orange
    public static let destructive = Color.red

    // 来源标识色（与 Android Theme.kt 对齐）
    public static let forestGreen  = Color(rgb: 0x2E7D32)   // 草榴社区标识绿

    // 描边 / 发丝线
    public static let hairline = Color(uiColor: UIColor { traits in
        let isDark = traits.userInterfaceStyle == .dark
        let isHighContrast = traits.accessibilityContrast == .high
        let baseColor = isDark ? UIColor.white : UIColor.black
        return baseColor.withAlphaComponent(isHighContrast ? 0.32 : 0.12)
    })

    private static func adaptive(
        light: UInt32,
        dark: UInt32,
        lightHigh: UInt32,
        darkHigh: UInt32
    ) -> Color {
        Color(uiColor: UIColor { traits in
            let isDark = traits.userInterfaceStyle == .dark
            let isHighContrast = traits.accessibilityContrast == .high
            let value = isDark
                ? (isHighContrast ? darkHigh : dark)
                : (isHighContrast ? lightHigh : light)
            return UIColor(
                red: CGFloat((value >> 16) & 0xFF) / 255,
                green: CGFloat((value >> 8) & 0xFF) / 255,
                blue: CGFloat(value & 0xFF) / 255,
                alpha: 1
            )
        })
    }
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
    public static let liveAspect:   CGFloat = 4.0 / 3.0   // 真实直播流画幅
    public static let wideAspect:   CGFloat = 16.0 / 9.0  // 宽幅视频/接着播放

    // 全 App 统一的卡片尺寸基线（Apple HIG 规范）
    public static let posterCardWidth: CGFloat = 140
    public static let posterCardHeight: CGFloat = 210     // 140 * 1.5 (2:3)
    public static let liveCardWidth: CGFloat = 180
    public static let liveCardHeight: CGFloat = 135       // 180 / (4/3) (4:3)
    public static let wideCardWidth: CGFloat = 260
    public static let wideCardHeight: CGFloat = 146       // 16:9

    public static let contentInset: CGFloat = 16
}

public enum AuroraMotion {
    public static let quick  = Animation.spring(response: 0.24, dampingFraction: 0.82)
    public static let smooth = Animation.spring(response: 0.36, dampingFraction: 0.78)

    public static func selection(reduceMotion: Bool) -> Animation? {
        reduceMotion ? nil : quick
    }
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
