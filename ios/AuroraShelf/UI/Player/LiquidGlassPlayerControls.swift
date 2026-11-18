import SwiftUI
import UIKit

// MARK: - iOS 26 / 27 SDK 原生高透折射液态玻璃（Glass.clear）播放器组件库
//
// 设计准则：
// 1. 视频浮层一律采用 `Glass.clear.interactive()`（底层对应 `UIGlassEffectStyleClear`），
//    取代发灰雾化的 `Glass.regular`，让动态视频像素在按钮边缘产生强折射透镜高光（Specular Rim & Lensing）。
// 2. 优先使用 iOS 26.1+ / 27 SDK 原生 `.buttonStyle(.glass(.clear.interactive()))`，并在 iOS 26.0 上平滑回退至
//    `.glassEffect(.clear.interactive(), in: ...)`，确保零自定义模糊伪造且 100% 兼容部署基线。

public extension View {
    /// 为视频浮层圆形按钮赋予 iOS 27 SDK 高透液态玻璃样式（`Glass.clear.interactive()`）
    @ViewBuilder
    func playerClearGlassCircleButton(tint: Color? = nil) -> some View {
        if #available(iOS 26.1, *) {
            let glass: Glass = tint.map { Glass.clear.tint($0).interactive() } ?? Glass.clear.interactive()
            self
                .buttonStyle(.glass(glass))
                .buttonBorderShape(.circle)
        } else {
            let glass: Glass = tint.map { Glass.clear.tint($0).interactive() } ?? Glass.clear.interactive()
            self
                .buttonStyle(.plain)
                .glassEffect(glass, in: .circle)
        }
    }

    /// 为视频浮层胶囊按钮赋予 iOS 27 SDK 高透液态玻璃样式（`Glass.clear.interactive()`）
    @ViewBuilder
    func playerClearGlassCapsuleButton(tint: Color? = nil) -> some View {
        if #available(iOS 26.1, *) {
            let glass: Glass = tint.map { Glass.clear.tint($0).interactive() } ?? Glass.clear.interactive()
            self
                .buttonStyle(.glass(glass))
                .buttonBorderShape(.capsule)
        } else {
            let glass: Glass = tint.map { Glass.clear.tint($0).interactive() } ?? Glass.clear.interactive()
            self
                .buttonStyle(.plain)
                .glassEffect(glass, in: .capsule)
        }
    }
}

/// 播放器圆形高透液态玻璃图标按钮（符合 Apple HIG 44×44pt 最小触控热区）
public struct PlayerGlassIconButton: View {
    public let systemName: String
    public let accessibilityTitle: String
    public var visualDiameter: CGFloat = 38
    public var symbolSize: CGFloat = 15
    public var symbolWeight: Font.Weight = .semibold
    public var symbolLeadingOffset: CGFloat = 0
    public var tint: Color? = nil
    public let action: () -> Void

    public init(
        systemName: String,
        accessibilityTitle: String,
        visualDiameter: CGFloat = 38,
        symbolSize: CGFloat = 15,
        symbolWeight: Font.Weight = .semibold,
        symbolLeadingOffset: CGFloat = 0,
        tint: Color? = nil,
        action: @escaping () -> Void
    ) {
        self.systemName = systemName
        self.accessibilityTitle = accessibilityTitle
        self.visualDiameter = visualDiameter
        self.symbolSize = symbolSize
        self.symbolWeight = symbolWeight
        self.symbolLeadingOffset = symbolLeadingOffset
        self.tint = tint
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: symbolSize, weight: symbolWeight))
                .foregroundStyle(.white)
                .offset(x: symbolLeadingOffset)
                .frame(width: max(visualDiameter - 12, 22), height: max(visualDiameter - 12, 22))
        }
        .playerClearGlassCircleButton(tint: tint)
        .frame(minWidth: max(visualDiameter, AuroraMetrics.minTouchTarget), minHeight: max(visualDiameter, AuroraMetrics.minTouchTarget))
        .contentShape(Rectangle())
        .accessibilityLabel(accessibilityTitle)
    }
}

/// 播放器胶囊型高透液态玻璃动作按钮（用于 Apple TV 全屏底部功能栏）
public struct PlayerGlassPillButton: View {
    public let systemName: String?
    public let title: String
    public var isCompactIconOnly: Bool = false
    public var tint: Color? = nil
    public let action: () -> Void

    public init(
        systemName: String? = nil,
        title: String,
        isCompactIconOnly: Bool = false,
        tint: Color? = nil,
        action: @escaping () -> Void
    ) {
        self.systemName = systemName
        self.title = title
        self.isCompactIconOnly = isCompactIconOnly
        self.tint = tint
        self.action = action
    }

    public var body: some View {
        if isCompactIconOnly, let systemName {
            PlayerGlassIconButton(
                systemName: systemName,
                accessibilityTitle: title,
                visualDiameter: 38,
                symbolSize: 14,
                symbolWeight: .semibold,
                tint: tint,
                action: action
            )
        } else {
            Button(action: action) {
                HStack(spacing: 5) {
                    if let systemName {
                        Image(systemName: systemName)
                            .font(.system(size: 13, weight: .semibold))
                    }
                    Text(title)
                        .font(.system(size: 13, weight: .semibold))
                        .lineLimit(1)
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .fixedSize(horizontal: true, vertical: false)
            }
            .playerClearGlassCapsuleButton(tint: tint)
            .frame(minHeight: AuroraMetrics.minTouchTarget)
            .accessibilityLabel(title)
        }
    }
}

/// Apple TV 风格高透液态玻璃动态 HUD（跳秒、音量、亮度、画幅切换、2.0x 倍速提示）
public struct PlayerLiquidGlassHUD: View {
    public let icon: String?
    public let message: String
    public let progress: Double?

    public init(icon: String?, message: String, progress: Double? = nil) {
        self.icon = icon
        self.message = message
        self.progress = progress
    }

    public var body: some View {
        VStack(spacing: 8) {
            if let icon {
                Image(systemName: icon)
                    .font(.system(size: 26, weight: .bold))
                    .foregroundStyle(.white)
                    .symbolEffect(.bounce, value: message)
            }
            Text(message)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(.white)
                .lineLimit(1)

            if let prog = progress {
                GeometryReader { pGeo in
                    let clamped = min(max(CGFloat(prog), 0), 1)
                    ZStack(alignment: .leading) {
                        Capsule()
                            .fill(Color.white.opacity(0.26))
                        Capsule()
                            .fill(Color.white)
                            .frame(width: max(0, pGeo.size.width * clamped))
                    }
                }
                .frame(width: 116, height: 5)
            }
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 15)
        .glassEffect(.clear, in: .rect(cornerRadius: 20))
        .shadow(color: .black.opacity(0.28), radius: 14, y: 6)
        .allowsHitTesting(false)
    }
}
