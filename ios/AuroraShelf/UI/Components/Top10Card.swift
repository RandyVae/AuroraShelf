import SwiftUI

/// 原生 Top 10 排行卡片：立体大数字（白名单），自适应海报
public struct Top10Card: View {
    public let video: VideoItem
    public let rank: Int
    public let onSelect: () -> Void
    
    @ScaledMetric(relativeTo: .largeTitle) private var rankSize: CGFloat = 88
    
    public init(video: VideoItem, rank: Int, onSelect: @escaping () -> Void) {
        self.video = video
        self.rank = rank
        self.onSelect = onSelect
    }
    
    public var body: some View {
        Button(action: onSelect) {
            HStack(alignment: .bottom, spacing: -14) {
                // 排行榜大数字：底部基准线对齐海报下沿
                Text("\(rank)")
                    .font(.system(size: rankSize, weight: .black, design: .rounded))
                    .foregroundStyle(
                        LinearGradient(
                            colors: [.white, Color(white: 0.65)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .shadow(color: .black.opacity(0.8), radius: 6, x: 2, y: 3)
                    .frame(width: rank >= 10 ? 76 : 56, alignment: .trailing)
                    .offset(y: 6)
                    .zIndex(1)
                
                // 海报（自适应宽度 120，高度按 2:3 纵横比，自动解密与双层缓存）
                AuroraCoverImage(
                    url: video.coverUrl,
                    aspectRatio: AuroraMetrics.posterAspect,
                    contentMode: .fill,
                    cornerRadius: AuroraMetrics.cardRadius
                )
                .frame(width: 120, height: 180)
                .overlay(
                    RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                        .stroke(AuroraColor.hairline, lineWidth: 1)
                )
            }
        }
        .buttonStyle(.plain)
    }
}
