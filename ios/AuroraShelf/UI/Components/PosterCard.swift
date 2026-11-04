import SwiftUI

/// 原生自适应海报卡片：严格遵守 2:3 纵横比，语义字体与分层表面色
public struct PosterCard: View {
    public let video: VideoItem
    public let onSelect: () -> Void
    
    public init(video: VideoItem, onSelect: @escaping () -> Void) {
        self.video = video
        self.onSelect = onSelect
    }
    
    public var body: some View {
        Button(action: onSelect) {
            VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                // 封面图片（固定 2:3 纵横比自适应宽度，自动解密与双层缓存）
                ZStack(alignment: .topTrailing) {
                    AuroraCoverImage(
                        url: video.coverUrl,
                        aspectRatio: AuroraMetrics.posterAspect,
                        contentMode: .fill,
                        cornerRadius: AuroraMetrics.cardRadius
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                            .stroke(AuroraColor.hairline, lineWidth: 1)
                    )
                    
                    // 评分微型胶囊
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
                    }
                }
                
                // 标题（语义样式 .subheadline，最多 1 行）
                Text(video.title)
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundStyle(AuroraColor.textPrimary)
                    .lineLimit(1)
                
                // 来源与时长行
                HStack {
                    Text(video.sourceName)
                        .font(.caption)
                        .foregroundStyle(AuroraColor.brand)
                    
                    Spacer()
                    
                    if !video.duration.isEmpty {
                        Text(video.duration)
                            .font(.caption2)
                            .foregroundStyle(AuroraColor.textSecondary)
                    }
                }
                .lineLimit(1)
            }
        }
        .buttonStyle(.plain)
    }
}
