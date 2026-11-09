import SwiftUI

/// 用户原生经典 3D 折叠丝带 N 标（1:1 对齐 Android 端 AppComponents.kt #698 与历史基线 NetflixNLogo）
public struct NetgreyMarkView: View {
    public var width: CGFloat
    public var height: CGFloat
    public var redColor: Color
    public var darkRedColor: Color

    public init(
        width: CGFloat = 18,
        height: CGFloat = 26,
        redColor: Color = Color(red: 1.0, green: 0.31, blue: 0.48), // AuroraCoral #FF4F7B
        darkRedColor: Color = Color(red: 0.70, green: 0.04, blue: 0.08) // AuroraBrandAccentDeep #B20A14
    ) {
        self.width = width
        self.height = height
        self.redColor = redColor
        self.darkRedColor = darkRedColor
    }

    public var body: some View {
        Canvas { context, size in
            let w = size.width
            let h = size.height
            let barW = w * 0.32

            // 左侧垂直立柱
            context.fill(
                Path(CGRect(x: 0, y: 0, width: barW, height: h)),
                with: .color(darkRedColor)
            )

            // 右侧垂直立柱
            context.fill(
                Path(CGRect(x: w - barW, y: 0, width: barW, height: h)),
                with: .color(darkRedColor)
            )

            // 倾斜折叠丝带
            var diagonal = Path()
            diagonal.move(to: CGPoint(x: 0, y: 0))
            diagonal.addLine(to: CGPoint(x: barW, y: 0))
            diagonal.addLine(to: CGPoint(x: w, y: h))
            diagonal.addLine(to: CGPoint(x: w - barW, y: h))
            diagonal.closeSubpath()
            context.fill(diagonal, with: .color(redColor))
        }
        .frame(width: width, height: height)
        .accessibilityLabel("Netgrey")
    }
}

/// 兼容别名
public typealias NetflixNLogo = NetgreyMarkView

/// 用户原生经典 Netgrey 品牌 Logotype（对齐 AppComponents.kt #976 原生排版）
public struct NetgreyLogoView: View {
    public var markWidth: CGFloat
    public var markHeight: CGFloat
    public var fontSize: CGFloat

    public init(
        markWidth: CGFloat = 18,
        markHeight: CGFloat = 26,
        fontSize: CGFloat = 21
    ) {
        self.markWidth = markWidth
        self.markHeight = markHeight
        self.fontSize = fontSize
    }

    public var body: some View {
        HStack(spacing: 8) {
            // 3D 丝带 N 标
            NetgreyMarkView(width: markWidth, height: markHeight)

            // Netgrey 珊瑚红经典品牌大字
            Text("Netgrey")
                .font(.system(size: fontSize, weight: .heavy))
                .foregroundStyle(Color(red: 1.0, green: 0.31, blue: 0.48)) // AuroraCoral #FF4F7B
                .tracking(-0.5)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Netgrey")
    }
}

#if canImport(UIKit)
import UIKit

public enum NetgreyTabIconRenderer {
    /// 生成适配 iOS / iPadOS 原生 TabBar / Sidebar 胶囊的高清 3D 折叠丝带 N 标图片
    public static func renderImage(
        width: CGFloat = 20,
        height: CGFloat = 26,
        scale: CGFloat = 3.0,
        isSelected: Bool = true
    ) -> UIImage {
        let size = CGSize(width: width, height: height)
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        let alpha: CGFloat = isSelected ? 1.0 : 0.65
        let image = renderer.image { ctx in
            let cg = ctx.cgContext
            let barW = width * 0.32

            // 左立柱与右立柱（#B20A14 深红暗部）
            cg.setFillColor(UIColor(red: 0.70, green: 0.04, blue: 0.08, alpha: alpha).cgColor)
            cg.fill(CGRect(x: 0, y: 0, width: barW, height: height))
            cg.fill(CGRect(x: width - barW, y: 0, width: barW, height: height))

            // 倾斜折叠丝带（#FF4F7B 亮珊瑚红）
            cg.setFillColor(UIColor(red: 1.0, green: 0.31, blue: 0.48, alpha: alpha).cgColor)
            let path = CGMutablePath()
            path.move(to: CGPoint(x: 0, y: 0))
            path.addLine(to: CGPoint(x: barW, y: 0))
            path.addLine(to: CGPoint(x: width, y: height))
            path.addLine(to: CGPoint(x: width - barW, y: height))
            path.closeSubpath()
            cg.addPath(path)
            cg.fillPath()
        }
        return image.withRenderingMode(.alwaysOriginal)
    }
}
#endif
