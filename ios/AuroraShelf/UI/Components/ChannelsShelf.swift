import SwiftUI

/// 频道视觉规约（对齐 Apple TV「Channels & Apps」品牌徽章规范）
public struct ChannelVisualSpec: Sendable {
    public let iconName: String
    public let gradientColors: [Color]
    public let accentColor: Color

    public init(iconName: String, gradientColors: [Color], accentColor: Color) {
        self.iconName = iconName
        self.gradientColors = gradientColors
        self.accentColor = accentColor
    }

    public static func forSource(_ id: String) -> ChannelVisualSpec {
        switch id {
        case "huavod":
            // 华视影院：经典剧院深红/波尔多红
            return ChannelVisualSpec(
                iconName: "film.stack.fill",
                gradientColors: [Color(rgb: 0x660A10), Color(rgb: 0xB71C1C)],
                accentColor: Color(rgb: 0xFF5252)
            )
        case "huangguo":
            // 黄果短剧：活力金橙/暖柑渐变
            return ChannelVisualSpec(
                iconName: "play.square.stack",
                gradientColors: [Color(rgb: 0xE65100), Color(rgb: 0xFFA000)],
                accentColor: Color(rgb: 0xFFB74D)
            )
        case "91porn":
            // 91视频：Netgrey 标志性珊瑚粉红
            return ChannelVisualSpec(
                iconName: "play.tv",
                gradientColors: [Color(rgb: 0xB20A14), Color(rgb: 0xFF4F7B)],
                accentColor: Color(rgb: 0xFF7597)
            )
        case "91dongman":
            // 91动漫：二次元电光紫罗兰/品红
            return ChannelVisualSpec(
                iconName: "sparkles.tv.fill",
                gradientColors: [Color(rgb: 0x4A148C), Color(rgb: 0x8E24AA)],
                accentColor: Color(rgb: 0xCE93D8)
            )
        case "beeg":
            // Beeg：黑曜石哑光黑 + 电光金黄高光
            return ChannelVisualSpec(
                iconName: "bolt.fill",
                gradientColors: [Color(rgb: 0x1A1A1A), Color(rgb: 0x333336)],
                accentColor: Color(rgb: 0xFFD600)
            )
        case "xvideos":
            // XVideos：烈焰宝石红
            return ChannelVisualSpec(
                iconName: "play.rectangle.on.rectangle.fill",
                gradientColors: [Color(rgb: 0x880E14), Color(rgb: 0xD32F2F)],
                accentColor: Color(rgb: 0xFF5252)
            )
        case "missav":
            // MissAV：极光翡翠碧青
            return ChannelVisualSpec(
                iconName: "play.fill",
                gradientColors: [Color(rgb: 0x004D40), Color(rgb: 0x00796B)],
                accentColor: Color(rgb: 0x4DB6AC)
            )
        case "kissjav":
            // KissJAV：樱花粉洋红
            return ChannelVisualSpec(
                iconName: "heart.fill",
                gradientColors: [Color(rgb: 0x880E4F), Color(rgb: 0xC2185B)],
                accentColor: Color(rgb: 0xF06292)
            )
        case "85po":
            // 85po：午夜深蓝 + 炽热焰火
            return ChannelVisualSpec(
                iconName: "flame.fill",
                gradientColors: [Color(rgb: 0x1A237E), Color(rgb: 0x303F9F)],
                accentColor: Color(rgb: 0xFFAB40)
            )
        case "pornhub":
            // Pornhub：炭黑与经典亮金
            return ChannelVisualSpec(
                iconName: "play.rectangle.fill",
                gradientColors: [Color(rgb: 0x1C1C1E), Color(rgb: 0x2A2A2D)],
                accentColor: Color(rgb: 0xFFA000)
            )
        case "javnoni":
            // JAV-NONI：皇家天青蓝
            return ChannelVisualSpec(
                iconName: "star.fill",
                gradientColors: [Color(rgb: 0x0D47A1), Color(rgb: 0x1976D2)],
                accentColor: Color(rgb: 0x64B5F6)
            )
        case "javsb":
            // JAVSB：深海绿松石/高速蓝
            return ChannelVisualSpec(
                iconName: "bolt.horizontal.fill",
                gradientColors: [Color(rgb: 0x006064), Color(rgb: 0x00838F)],
                accentColor: Color(rgb: 0x4DD0E1)
            )
        case "tokyomotion":
            // TOKYO Motion：涉谷霓虹艳粉
            return ChannelVisualSpec(
                iconName: "location.north",
                gradientColors: [Color(rgb: 0xC2185B), Color(rgb: 0xE91E63)],
                accentColor: Color(rgb: 0xFF4081)
            )
        case "spankbang":
            // SpankBang：4K 科技暗曜紫
            return ChannelVisualSpec(
                iconName: "4k.tv.fill",
                gradientColors: [Color(rgb: 0x311B92), Color(rgb: 0x512DA8)],
                accentColor: Color(rgb: 0xB388FF)
            )
        case "xhamster":
            // xHamster：大地铜橙
            return ChannelVisualSpec(
                iconName: "pawprint.fill",
                gradientColors: [Color(rgb: 0xBF360C), Color(rgb: 0xE64A19)],
                accentColor: Color(rgb: 0xFF7043)
            )
        case "eporner":
            // EPORNER：赛博钴蓝
            return ChannelVisualSpec(
                iconName: "tv.badge.wifi",
                gradientColors: [Color(rgb: 0x01579B), Color(rgb: 0x0288D1)],
                accentColor: Color(rgb: 0x40C4FF)
            )
        case "mrdouga":
            // MRDOUGA：未来科技紫蓝
            return ChannelVisualSpec(
                iconName: "cube.transparent.fill",
                gradientColors: [Color(rgb: 0x283593), Color(rgb: 0x5C6BC0)],
                accentColor: Color(rgb: 0x8C9EFF)
            )
        case "ohentai":
            // OHentai：魔幻薰衣草紫
            return ChannelVisualSpec(
                iconName: "wand.and.stars",
                gradientColors: [Color(rgb: 0x4A148C), Color(rgb: 0x6A1B9A)],
                accentColor: Color(rgb: 0xEA80FC)
            )
        case "xnxx":
            // XNXX：跨国深海靛蓝
            return ChannelVisualSpec(
                iconName: "globe.americas.fill",
                gradientColors: [Color(rgb: 0x002171), Color(rgb: 0x0D47A1)],
                accentColor: Color(rgb: 0x82B1FF)
            )
        default:
            return ChannelVisualSpec(
                iconName: "tv.fill",
                gradientColors: [Color(rgb: 0x2C2C2E), Color(rgb: 0x3A3A3C)],
                accentColor: AuroraColor.brand
            )
        }
    }
}

/// 频道专属几何字标：统一使用 SF Pro Rounded 的短字标，
/// 避免把不同来源的系统图标拼在一起造成视觉噪音。
private struct ChannelGlyph: View {
    let sourceID: String
    let iconName: String
    let accent: Color
    let isSelected: Bool

    private var monogram: String {
        switch sourceID {
        case "huavod": return "HU"
        case "huangguo": return "HG"
        case "91porn": return "91"
        case "91dongman": return "漫"
        case "beeg": return "B"
        case "xvideos": return "XV"
        case "missav": return "M"
        case "kissjav": return "K"
        case "85po": return "85"
        case "pornhub": return "PH"
        case "javnoni": return "JN"
        case "javsb": return "JS"
        case "tokyomotion": return "TM"
        case "spankbang": return "SB"
        case "xhamster": return "XH"
        case "eporner": return "EP"
        case "mrdouga": return "MD"
        case "ohentai": return "OH"
        case "xnxx": return "XX"
        default: return "TV"
        }
    }

    var body: some View {
        ZStack {
            Circle()
                .fill(.white.opacity(isSelected ? 0.16 : 0.08))
                .overlay(Circle().stroke(.white.opacity(isSelected ? 0.34 : 0.16), lineWidth: 0.8))
            Image(systemName: iconName)
                .font(.system(size: 20, weight: .semibold))
                .symbolRenderingMode(.hierarchical)
                .foregroundStyle(isSelected ? .white : accent)
        }
        .frame(width: 38, height: 38)
        .shadow(color: .black.opacity(isSelected ? 0.28 : 0.12), radius: 3, y: 1)
        .accessibilityLabel("频道图标")
    }
}

/// Apple TV 风格的单个频道品牌卡片（Channel Brand Badge）
public struct ChannelBrandCard: View {
    public let source: VideoSourceDefinition
    public let isSelected: Bool

    public init(source: VideoSourceDefinition, isSelected: Bool) {
        self.source = source
        self.isSelected = isSelected
    }

    private var spec: ChannelVisualSpec {
        ChannelVisualSpec.forSource(source.id)
    }

    public var body: some View {
        VStack(spacing: 8) {
            ZStack {
                // 1. 底层品牌专属色彩渐变（选中态全量释放；未选态透出微光）
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: spec.gradientColors,
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .opacity(isSelected ? 1.0 : 0.35)

                if !isSelected {
                    // 未选态覆盖轻微毛玻璃底色，与 Apple TV 频道卡片层级一致
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(AuroraColor.surfaceCard.opacity(0.65))
                }

                // 2. 苹果官方材质级高光斜切（Apple TV Specular Bevel Highlight）
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(
                        LinearGradient(
                            stops: [
                                .init(color: .white.opacity(isSelected ? 0.35 : 0.12), location: 0.0),
                                .init(color: .white.opacity(isSelected ? 0.08 : 0.02), location: 0.45),
                                .init(color: .clear, location: 0.5)
                            ],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )

                // 3. 核心官方 SF Symbol / 品牌标识（91视频作为 Netgrey 核心源专属呈现 3D 丝带 N 标，彻底拔除老旧红电视图标）
                if source.id == "91porn" {
                    NetgreyMarkView(width: 20, height: 26)
                        .shadow(color: .black.opacity(isSelected ? 0.45 : 0.20), radius: 4, x: 0, y: 1)
                } else {
                    ChannelGlyph(sourceID: source.id, iconName: spec.iconName, accent: spec.accentColor, isSelected: isSelected)
                }

                // 4. 外轮廓高亮微光描边
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(
                        isSelected
                            ? LinearGradient(colors: [Color.white.opacity(0.85), spec.accentColor], startPoint: .topLeading, endPoint: .bottomTrailing)
                            : LinearGradient(colors: [Color.white.opacity(0.12), Color.white.opacity(0.04)], startPoint: .top, endPoint: .bottom),
                        lineWidth: isSelected ? 2 : 1
                    )
            }
            .frame(width: 88, height: 56)
            .shadow(
                color: isSelected ? spec.accentColor.opacity(0.45) : .clear,
                radius: 8,
                x: 0,
                y: 3
            )

            // 频道标题（选态加粗加亮）
            Text(source.name)
                .font(isSelected ? .caption.weight(.bold) : .caption.weight(.medium))
                .foregroundStyle(isSelected ? AuroraColor.textPrimary : AuroraColor.textSecondary)
                .lineLimit(1)
                .frame(width: 88)
        }
    }
}

/// Apple TV 风格的「频道与流媒体来源（Channels & Sources）」横向货架
public struct ChannelsShelf: View {
    @ObservedObject private var repo = VideoRepository.shared
    public let onOpenSourcePicker: () -> Void

    public init(onOpenSourcePicker: @escaping () -> Void) {
        self.onOpenSourcePicker = onOpenSourcePicker
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
            // 货架标题（Apple TV 原生纯粹排版，无多余副标胶囊）
            HStack(spacing: AuroraMetrics.spacingS) {
                Text("home.channels".localized)
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundStyle(AuroraColor.textPrimary)

                Spacer()

                Button(action: onOpenSourcePicker) {
                    HStack(spacing: 2) {
                        Text("全量 \(VideoSourceCatalog.allSources.count) 源")
                            .font(.subheadline)
                            .fontWeight(.medium)
                            .foregroundStyle(AuroraColor.brand)
                        Image(systemName: "chevron.right")
                            .font(.caption2)
                            .foregroundStyle(AuroraColor.brand)
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel("查看全量 \(VideoSourceCatalog.allSources.count) 个频道来源")
            }
            .padding(.horizontal, AuroraMetrics.contentInset)

            // 频道卡片横向滚动流
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: AuroraMetrics.spacingM) {
                    ForEach(VideoSourceCatalog.supportedSources) { source in
                        let isSelected = repo.currentSource.id == source.id
                        Button {
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                                repo.switchSource(to: source)
                            }
                        } label: {
                            ChannelBrandCard(source: source, isSelected: isSelected)
                        }
                        .buttonStyle(AppleSpringCardButtonStyle())
                        .accessibilityLabel("频道 \(source.name)，\(isSelected ? "当前正在播放" : "轻点切换")")
                    }

                    // 探索更多频道入口
                    Button(action: onOpenSourcePicker) {
                        VStack(spacing: 8) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .fill(
                                        LinearGradient(
                                            colors: [Color(rgb: 0x1C1C1E), Color(rgb: 0x2C2C2E)],
                                            startPoint: .topLeading,
                                            endPoint: .bottomTrailing
                                        )
                                    )
                                    .frame(width: 88, height: 56)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                                            .stroke(AuroraColor.hairline, lineWidth: 1)
                                    )

                                Image(systemName: "ellipsis.circle.fill")
                                    .font(.system(size: 22, weight: .bold))
                                    .foregroundStyle(AuroraColor.brand)
                            }

                            Text("更多频道")
                                .font(.system(size: 11, weight: .medium, design: .rounded))
                                .foregroundStyle(AuroraColor.textSecondary)
                                .lineLimit(1)
                                .frame(width: 88)
                        }
                    }
                    .buttonStyle(AppleSpringCardButtonStyle())
                    .accessibilityLabel("探索更多流媒体来源")
                }
                .padding(.horizontal, AuroraMetrics.contentInset)
            }
        }
    }
}
