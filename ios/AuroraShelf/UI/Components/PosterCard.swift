import SwiftUI

/// 原生自适应海报卡片：严格遵守 2:3 纵横比，语义字体与分层表面色
public struct PosterCard: View {
    public let video: VideoItem
    public let onSelect: () -> Void
    public var onDetail: (() -> Void)? = nil
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    
    public init(
        video: VideoItem,
        onSelect: @escaping () -> Void,
        onDetail: (() -> Void)? = nil
    ) {
        self.video = video
        self.onSelect = onSelect
        self.onDetail = onDetail
    }
    
    public var body: some View {
        VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
            Button(action: onSelect) {
                // 封面独立作为播放入口，图片异步加载和叠加评分不会缩小按钮命中区域。
                ZStack(alignment: .topTrailing) {
                    AuroraCoverImage(
                        url: video.coverUrl,
                        title: video.title,
                        sourceBaseURL: video.coverBaseURL,
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
                                .foregroundStyle(.white)
                        }
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background(Color.black.opacity(0.65), in: Capsule())
                        .overlay(Capsule().stroke(Color.white.opacity(0.15), lineWidth: 0.5))
                        .padding(AuroraMetrics.spacingS)
                        .allowsHitTesting(false)
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(AppleSpringCardButtonStyle())
            .hoverEffect(.lift)
            .accessibilityLabel("播放\(video.title)")

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

            // 来源与时长行
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
        .contextMenu {
            if let onDetail = onDetail {
                Button {
                    onDetail()
                } label: {
                    Label("查看详情", systemImage: "info.circle")
                }
            }
            Button {
                do {
                    let isBookmarked = WatchlistStore.shared.contains(video)
                    try WatchlistStore.shared.setBookmarked(!isBookmarked, for: video)
                } catch {
                    #if DEBUG
                    print("[Watchlist] failed to update bookmark: \(error.localizedDescription)")
                    #endif
                }
            } label: {
                Label(
                    WatchlistStore.shared.contains(video) ? "从片单移除" : "加入片单",
                    systemImage: WatchlistStore.shared.contains(video) ? "bookmark.fill" : "bookmark"
                )
            }
            if let url = URL(string: video.playbackUrl), !video.playbackUrl.isEmpty {
                ShareLink(item: url) {
                    Label("分享视频", systemImage: "square.and.arrow.up")
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
