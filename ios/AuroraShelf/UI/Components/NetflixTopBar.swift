import SwiftUI

/// 1:1 对齐 Android 端 NetflixNLogo（AppComponents.kt #698）
public struct NetflixNLogo: View {
    public var redColor: Color = Color(hex: 0xFF4F7B) // AuroraCoral #FF4F7B
    public var darkRedColor: Color = Color(hex: 0xB20A14) // AuroraBrandAccentDeep #B20A14
    
    public init(
        redColor: Color = Color(hex: 0xFF4F7B),
        darkRedColor: Color = Color(hex: 0xB20A14)
    ) {
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
        .frame(width: 22, height: 34)
    }
}

/// 1:1 对齐 Android 端 NetflixTopBar（AppComponents.kt #976）
public struct NetflixTopBar: View {
    public var title: String
    public var searchPlaceholder: String
    public var sourceName: String?
    public var onSourcePicker: (() -> Void)?
    public var onSearch: (() -> Void)?
    public var onCast: (() -> Void)?
    public var topSafeAreaHeight: CGFloat
    
    public init(
        title: String = "Netgrey",
        searchPlaceholder: String = "搜索",
        sourceName: String? = nil,
        onSourcePicker: (() -> Void)? = nil,
        onSearch: (() -> Void)? = nil,
        onCast: (() -> Void)? = nil,
        topSafeAreaHeight: CGFloat = 0
    ) {
        self.title = title
        self.searchPlaceholder = searchPlaceholder
        self.sourceName = sourceName
        self.onSourcePicker = onSourcePicker
        self.onSearch = onSearch
        self.onCast = onCast
        self.topSafeAreaHeight = topSafeAreaHeight
    }
    
    public var body: some View {
        VStack(spacing: 0) {
            // 系统状态栏与灵动岛顶部安全间隙（彻底消除与 8:20 及电池状态栏图标重叠）
            if topSafeAreaHeight > 0 {
                Color.clear.frame(height: topSafeAreaHeight)
            }
            
            HStack(spacing: 10) {
                // 3D 丝带 N 标
                NetflixNLogo()
                
                // Netgrey 珊瑚红品牌大字
                Text(title)
                    .font(.system(size: 22, weight: .heavy))
                    .foregroundColor(Color(hex: 0xFF4F7B)) // AuroraCoral
                    .tracking(-0.5)
                
                // 可选视频源/切换胶囊
                if let source = sourceName {
                    Button(action: {
                        let impact = UIImpactFeedbackGenerator(style: .light)
                        impact.impactOccurred()
                        onSourcePicker?()
                    }) {
                        HStack(spacing: 4) {
                            Text(source)
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.white)
                            Image(systemName: "chevron.down")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(Color(hex: 0xFF4F7B))
                        }
                        .padding(.horizontal, 9)
                        .padding(.vertical, 5)
                        .background(
                            Capsule()
                                .fill(Color(white: 0.16))
                                .overlay(Capsule().stroke(Color.white.opacity(0.18), lineWidth: 0.8))
                        )
                    }
                    .buttonStyle(PlainButtonStyle())
                }
                
                Spacer()
                
                // AirPlay / Cast 原生系统投屏按钮
                AirPlayRoutePicker()
                    .frame(width: 34, height: 34)
                
                // 胶囊搜索按钮 (NetflixSearchCapsule)
                Button(action: {
                    let impact = UIImpactFeedbackGenerator(style: .light)
                    impact.impactOccurred()
                    onSearch?()
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 13, weight: .bold))
                        Text(searchPlaceholder)
                            .font(.system(size: 12, weight: .medium))
                    }
                    .foregroundColor(.white.opacity(0.88))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(
                        Capsule()
                            .fill(Color(red: 0.12, green: 0.14, blue: 0.20))
                            .overlay(Capsule().stroke(Color.white.opacity(0.18), lineWidth: 0.8))
                    )
                }
                .buttonStyle(PlainButtonStyle())
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 6)
        }
    }
}
