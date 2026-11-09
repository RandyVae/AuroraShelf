import SwiftUI

/// Apple TV 风格的横向分类货架（Category Shelf）
/// 遵循流媒体画廊策展逻辑：标题带 › 作为二级专区入口，右侧提供「全部 ›」按钮；
/// 卡片采用 2:3 经典海报画幅，右边缘留有 25% 露出（Peek Effect），强烈暗示横向滑动探索。
public struct CategoryShelfView: View {
    public let shelf: CategoryShelf
    public let onSeeAll: (String) -> Void
    public let onSelectVideo: (VideoItem) -> Void
    public var onDetailVideo: ((VideoItem) -> Void)?
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    private var cardWidth: CGFloat {
        let form = DeviceLayout.resolveCurrent(horizontalSizeClass: horizontalSizeClass)
        let base = DeviceLayout.posterCardWidth(for: form)
        return dynamicTypeSize.isAccessibilitySize ? max(base, 190) : base
    }

    public init(
        shelf: CategoryShelf,
        onSeeAll: @escaping (String) -> Void,
        onSelectVideo: @escaping (VideoItem) -> Void,
        onDetailVideo: ((VideoItem) -> Void)? = nil
    ) {
        self.shelf = shelf
        self.onSeeAll = onSeeAll
        self.onSelectVideo = onSelectVideo
        self.onDetailVideo = onDetailVideo
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
            // Apple TV 货架标头：标题带 ›，右侧「全部 ›」
            HStack(alignment: .firstTextBaseline) {
                Button(action: { onSeeAll(shelf.category) }) {
                    HStack(spacing: 6) {
                        Text(shelf.category)
                            .font(.title2)
                            .fontWeight(.bold)
                            .foregroundStyle(AuroraColor.textPrimary)

                        Image(systemName: "chevron.right")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundStyle(AuroraColor.textTertiary)
                    }
                }
                .buttonStyle(.plain)
                .hoverEffect(.highlight)

                Spacer()

                Button(action: { onSeeAll(shelf.category) }) {
                    HStack(spacing: 3) {
                        Text("home.all".localized)
                            .font(.subheadline)
                            .fontWeight(.medium)
                        Image(systemName: "chevron.right")
                            .font(.caption2)
                            .fontWeight(.bold)
                    }
                    .foregroundStyle(AuroraColor.brand)
                }
                .buttonStyle(.plain)
                .hoverEffect(.highlight)
            }
            .padding(.horizontal, AuroraMetrics.contentInset)

            // 横向滑动卡片流
            if shelf.isLoading && shelf.videos.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: AuroraMetrics.spacingM) {
                        ForEach(0..<4, id: \.self) { _ in
                            VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                                RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                                    .fill(AuroraColor.surfaceCard.opacity(0.6))
                                    .frame(width: cardWidth, height: cardWidth * 1.5)
                                RoundedRectangle(cornerRadius: 4, style: .continuous)
                                    .fill(AuroraColor.surfaceCard.opacity(0.4))
                                    .frame(width: cardWidth * 0.75, height: 14)
                            }
                        }
                    }
                    .padding(.horizontal, AuroraMetrics.contentInset)
                }
            } else if !shelf.videos.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(alignment: .top, spacing: AuroraMetrics.spacingM) {
                        ForEach(shelf.videos) { video in
                            VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                                PosterCard(
                                    video: video,
                                    onSelect: { onSelectVideo(video) },
                                    onDetail: {
                                        if let onDetailVideo = onDetailVideo {
                                            onDetailVideo(video)
                                        } else {
                                            onSelectVideo(video)
                                        }
                                    }
                                )
                                .frame(width: cardWidth)
                            }
                        }
                    }
                    .padding(.horizontal, AuroraMetrics.contentInset)
                }
            }
        }
    }
}
