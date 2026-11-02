import SwiftUI

public enum AuroraTheme {
    // 沉浸流媒体配色（Apple HIG / Netflix 深色体系）
    public static let background = Color(red: 0.03, green: 0.04, blue: 0.06) // #080A0F
    public static let surface = Color(red: 0.08, green: 0.09, blue: 0.14) // #141724
    public static let surfaceCard = Color(red: 0.11, green: 0.13, blue: 0.19) // #1C2130
    
    // 品牌强调色（1:1 对齐 Android Theme.kt）
    public static let auroraCoral = Color(hex: 0xFFFF4F7B) // #FF4F7B 官方品牌珊瑚红
    public static let auroraBrandAccentDeep = Color(hex: 0xFFB20A14)
    public static let crimson = Color(hex: 0xFFE50914) // #E50914 (Netflix Red)
    public static let crimsonGlow = Color(red: 1.00, green: 0.18, blue: 0.28)
    public static let sky = Color(red: 0.04, green: 0.52, blue: 1.00) // #0A84FF
    public static let gold = Color(red: 1.00, green: 0.84, blue: 0.00)
    public static let forestGreen = Color(hex: 0xFF2E7D32) // 草榴社区标识绿
    
    // 界面卡片与文字 Token（1:1 对齐 Android）
    public static let auroraMediaSurface = Color(hex: 0xFF161922) // #161922 极暗卡片底色
    public static let auroraMediaBorder = Color(hex: 0xFF262626) // #262626 极细边框
    public static let auroraDarkMuted = Color(hex: 0xFFB6BFCC) // #B6BFCC 次级文字
    
    // 文字色阶
    public static let textPrimary = Color.white
    public static let textSecondary = Color(white: 0.72)
    public static let textTertiary = Color(white: 0.45)
    
    // 边框微光
    public static let glassBorder = Color.white.opacity(0.12)
    public static let glassGlowBorder = crimson.opacity(0.35)
    
    // 物理弹性动画 Token
    public static let appleSpring = Animation.spring(response: 0.36, dampingFraction: 0.78, blendDuration: 0)
    public static let quickSpring = Animation.spring(response: 0.24, dampingFraction: 0.82, blendDuration: 0)
}

public extension Color {
    init(hex: UInt32, alpha: Double = 1.0) {
        let r = Double((hex >> 16) & 0xff) / 255.0
        let g = Double((hex >> 8) & 0xff) / 255.0
        let b = Double(hex & 0xff) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: alpha)
    }
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
