import SwiftUI

/// 原生 Apple TV 风格 Top 10 排行卡片
/// 遵循 Apple TV 原生质感：
/// 1. 封面尺寸与全 App 分类货架严格统一为标准 2:3 海报（140 × 210 pt）；
/// 2. 排行榜数字小巧精致（34 pt），采用透光半透明渐变（Translucent Frosted Gradient），重心完全回归视频封面；
/// 3. 卡片下方具备统一的标题与元数据，保持整齐一致的视觉韵律。
public struct Top10Card: View {
    public let video: VideoItem
    public let rank: Int
    public let onSelect: () -> Void
    public var onDetail: (() -> Void)? = nil

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    private var formFactor: DeviceFormFactor {
        DeviceLayout.resolveCurrent(horizontalSizeClass: horizontalSizeClass)
    }

    private var dynamicRankSize: CGFloat {
        DeviceLayout.top10RankFontSize(for: formFactor)
    }

    private var rankWidthSingle: CGFloat {
        dynamicRankSize * 0.6
    }

    private var rankWidthDouble: CGFloat {
        dynamicRankSize * 0.95
    }

    private var cardWidth: CGFloat {
        let base = DeviceLayout.posterCardWidth(for: formFactor)
        return dynamicTypeSize.isAccessibilitySize ? max(base, 190) : base
    }

    private var cardHeight: CGFloat {
        cardWidth * 1.5
    }

    public init(
        video: VideoItem,
        rank: Int,
        onSelect: @escaping () -> Void,
        onDetail: (() -> Void)? = nil
    ) {
        self.video = video
        self.rank = rank
        self.onSelect = onSelect
        self.onDetail = onDetail
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
            Button(action: onSelect) {
                HStack(alignment: .bottom, spacing: 6) {
                    // Apple TV 风格精致半透明数字（紧凑、半透明透光、小巧克制，自适应日夜间背景）
                    Text("\(rank)")
                        .font(.system(size: dynamicRankSize, weight: .bold, design: .rounded))
                        .foregroundStyle(
                            LinearGradient(
                                colors: [
                                    AuroraColor.textPrimary.opacity(0.55),
                                    AuroraColor.textPrimary.opacity(0.18)
                                ],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                        .shadow(color: Color.black.opacity(0.08), radius: 1, x: 0, y: 1)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .frame(width: rank >= 10 ? rankWidthDouble : rankWidthSingle, alignment: .trailing)
                        .offset(y: -4)

                    // 标准 2:3 纵向海报（与分类货架完全等宽高）
                    ZStack(alignment: .topTrailing) {
                        AuroraCoverImage(
                            url: video.coverUrl,
                            title: video.title,
                            sourceBaseURL: video.coverBaseURL,
                            aspectRatio: AuroraMetrics.posterAspect,
                            contentMode: .fill,
                            cornerRadius: AuroraMetrics.cardRadius
                        )
                        .frame(width: cardWidth, height: cardHeight)
                        .overlay(
                            RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                                .stroke(AuroraColor.hairline, lineWidth: 1)
                        )

                        // 评分胶囊
                        if !video.rating.isEmpty {
                            HStack(spacing: 3) {
                                Image(systemName: "star.fill")
                                    .font(.caption2)
                                    .foregroundStyle(.yellow)
                                Text(video.rating)
                                    .font(.caption2)
                                    .fontWeight(.bold)
                                    .foregroundStyle(.primary)
                            }
                            .padding(.horizontal, 6)
                            .padding(.vertical, 3)
                            .background(.regularMaterial, in: Capsule())
                            .padding(AuroraMetrics.spacingS)
                            .allowsHitTesting(false)
                        }
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(AppleSpringCardButtonStyle())
            .hoverEffect(.lift)
            .accessibilityLabel("排行第 \(rank)，播放 \(video.title)")

            // 标题文本（与分类货架卡片保持完全对齐的排版）
            Button(action: onSelect) {
                Text(video.title)
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundStyle(AuroraColor.textPrimary)
                    .lineLimit(2, reservesSpace: true)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            // 来源与时长信息
            HStack(spacing: 4) {
                Text(video.sourceName)
                    .font(.caption2)
                    .fontWeight(.bold)
                    .foregroundStyle(AuroraColor.brandLabel)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(AuroraColor.brand.opacity(0.12), in: Capsule())

                Spacer()

                if !video.duration.isEmpty {
                    Text(video.duration)
                        .font(.caption2)
                        .foregroundStyle(AuroraColor.textSecondary)
                }
            }
            .lineLimit(1)
            .frame(height: 18)
        }
        .frame(width: cardWidth + (rank >= 10 ? rankWidthDouble : rankWidthSingle) + 6)
        .contextMenu {
            if let onDetail = onDetail {
                Button {
                    onDetail()
                } label: {
                    Label("查看详情", systemImage: "info.circle")
                }
            }
        } preview: {
            AuroraCoverImage(
                url: video.coverUrl,
                title: video.title,
                sourceBaseURL: video.coverBaseURL,
                aspectRatio: AuroraMetrics.posterAspect,
                contentMode: .fill,
                cornerRadius: AuroraMetrics.cardRadius
            )
            .frame(width: 220, height: 330)
        }
    }
}
