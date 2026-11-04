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
            HStack(spacing: -18) {
                // 排行榜大数字（§3.2 白名单允许使用 .black）
                Text("\(rank)")
                    .font(.system(size: rankSize, weight: .black, design: .rounded))
                    .foregroundStyle(
                        LinearGradient(
                            colors: [.white, Color(white: 0.6)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .shadow(color: .black.opacity(0.6), radius: 4, x: 2, y: 2)
                    .frame(width: 64, alignment: .trailing)
                    .zIndex(1)
                
                // 海报（自适应宽度 120，高度按 2:3 纵横比）
                AsyncImage(url: URL(string: video.coverUrl)) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    default:
                        ZStack {
                            AuroraColor.surfaceCard
                            Image(systemName: "film")
                                .font(.title3)
                                .foregroundStyle(.tertiary)
                        }
                    }
                }
                .frame(width: 120, height: 180)
                .clipShape(RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                        .stroke(AuroraColor.hairline, lineWidth: 1)
                )
            }
        }
        .buttonStyle(.plain)
    }
}
