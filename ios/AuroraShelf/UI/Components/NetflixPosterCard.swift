import SwiftUI

public struct NetflixPosterCard: View {
    public let video: VideoItem
    public let width: CGFloat
    public let height: CGFloat
    public let onSelect: () -> Void
    
    public init(
        video: VideoItem,
        width: CGFloat = 120,
        height: CGFloat = 175,
        onSelect: @escaping () -> Void
    ) {
        self.video = video
        self.width = width
        self.height = height
        self.onSelect = onSelect
    }
    
    public var body: some View {
        Button(action: {
            let impact = UIImpactFeedbackGenerator(style: .medium)
            impact.impactOccurred()
            onSelect()
        }) {
            VStack(alignment: .leading, spacing: 6) {
                ZStack(alignment: .topTrailing) {
                    // 海报图片
                    AsyncImage(url: URL(string: video.coverUrl)) { phase in
                        switch phase {
                        case .success(let image):
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .frame(width: width, height: height)
                                .clipped()
                        default:
                            ZStack {
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .fill(AuroraTheme.surfaceCard)
                                Image(systemName: "film")
                                    .font(.system(size: 28))
                                    .foregroundColor(.white.opacity(0.2))
                            }
                            .frame(width: width, height: height)
                        }
                    }
                    .cornerRadius(10)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .stroke(Color.white.opacity(0.1), lineWidth: 0.8)
                    )
                    
                    // 评分微型胶囊
                    HStack(spacing: 2) {
                        Image(systemName: "star.fill")
                            .font(.system(size: 8))
                            .foregroundColor(AuroraTheme.gold)
                        Text(video.rating)
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.white)
                    }
                    .padding(.horizontal, 5)
                    .padding(.vertical, 2.5)
                    .background(
                        Capsule()
                            .fill(Color.black.opacity(0.72))
                    )
                    .padding(6)
                }
                
                // 标题
                Text(video.title)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(AuroraTheme.textPrimary)
                    .lineLimit(1)
                    .frame(width: width, alignment: .leading)
                
                // 来源标签
                HStack(spacing: 4) {
                    Text(video.sourceName)
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(AuroraTheme.crimsonGlow)
                    
                    Spacer()
                    
                    Text(video.duration)
                        .font(.system(size: 10, weight: .regular))
                        .foregroundColor(AuroraTheme.textTertiary)
                }
                .frame(width: width)
            }
        }
        .buttonStyle(PlainButtonStyle())
        .applePressEffect(scale: 0.95)
    }
}

public struct NetflixTop10Card: View {
    public let video: VideoItem
    public let rank: Int
    public let onSelect: () -> Void
    
    public init(
        video: VideoItem,
        rank: Int,
        onSelect: @escaping () -> Void
    ) {
        self.video = video
        self.rank = rank
        self.onSelect = onSelect
    }
    
    public var body: some View {
        Button(action: {
            let impact = UIImpactFeedbackGenerator(style: .medium)
            impact.impactOccurred()
            onSelect()
        }) {
            HStack(spacing: -24) {
                // Netflix 标志性大排名立体数字
                ZStack {
                    // 底层黑色粗描边投影
                    Text("\(rank)")
                        .font(.system(size: 100, weight: .black, design: .rounded))
                        .foregroundColor(.black)
                        .offset(x: 2, y: 2)
                    
                    // 中层描边
                    Text("\(rank)")
                        .font(.system(size: 100, weight: .black, design: .rounded))
                        .foregroundColor(Color(red: 0.22, green: 0.25, blue: 0.35))
                    
                    // 顶层银灰高光渐变
                    Text("\(rank)")
                        .font(.system(size: 96, weight: .black, design: .rounded))
                        .foregroundStyle(
                            LinearGradient(
                                colors: [.white, Color(white: 0.65), Color(white: 0.35)],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                }
                .frame(width: 80, height: 180, alignment: .trailing)
                .zIndex(1)
                
                // 海报卡片
                AsyncImage(url: URL(string: video.coverUrl)) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 125, height: 180)
                            .clipped()
                    default:
                        ZStack {
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(AuroraTheme.surfaceCard)
                            Image(systemName: "film")
                                .font(.system(size: 32))
                                .foregroundColor(.white.opacity(0.2))
                        }
                        .frame(width: 125, height: 180)
                    }
                }
                .cornerRadius(10)
                .overlay(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .stroke(Color.white.opacity(0.12), lineWidth: 0.8)
                )
                .shadow(color: Color.black.opacity(0.4), radius: 10, x: 0, y: 5)
            }
        }
        .buttonStyle(PlainButtonStyle())
        .applePressEffect(scale: 0.95)
    }
}
