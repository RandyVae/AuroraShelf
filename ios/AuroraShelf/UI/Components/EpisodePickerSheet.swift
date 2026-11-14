import SwiftUI

/// Apple TV 风格的选集悬浮抽屉面板（Episode Picker Sheet）
public struct EpisodePickerSheet: View {
    public let videoTitle: String
    public let sourceName: String
    public let episodes: [PlaybackEpisode]
    public let currentEpisodeNumber: Int
    public let onSelectEpisode: (PlaybackEpisode) -> Void
    public let onDismiss: () -> Void

    @Environment(\.dismiss) private var dismiss

    private let columns = [
        GridItem(.adaptive(minimum: 88, maximum: 140), spacing: AuroraMetrics.spacingM)
    ]

    public init(
        videoTitle: String,
        sourceName: String,
        episodes: [PlaybackEpisode],
        currentEpisodeNumber: Int,
        onSelectEpisode: @escaping (PlaybackEpisode) -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self.videoTitle = videoTitle
        self.sourceName = sourceName
        self.episodes = episodes
        self.currentEpisodeNumber = currentEpisodeNumber
        self.onSelectEpisode = onSelectEpisode
        self.onDismiss = onDismiss
    }

    public var body: some View {
        NavigationStack {
            ZStack {
                AuroraColor.canvas.ignoresSafeArea()

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingL) {
                        // 头部视频信息
                        VStack(alignment: .leading, spacing: 4) {
                            Text(videoTitle)
                                .font(.title3)
                                .fontWeight(.bold)
                                .foregroundStyle(AuroraColor.textPrimary)
                                .lineLimit(1)

                            HStack(spacing: 8) {
                                Text(sourceName)
                                    .font(.caption2.bold())
                                    .foregroundStyle(AuroraColor.brandLabel)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(AuroraColor.brand.opacity(0.12), in: Capsule())

                                Text("共 \(episodes.count) 集 · 高清流媒体")
                                    .font(.caption)
                                    .foregroundStyle(AuroraColor.textSecondary)
                            }
                        }
                        .padding(.horizontal, AuroraMetrics.contentInset)
                        .padding(.top, AuroraMetrics.spacingM)

                        // 剧集网格
                        LazyVGrid(columns: columns, spacing: AuroraMetrics.spacingM) {
                            ForEach(episodes) { ep in
                                let isSelected = ep.number == currentEpisodeNumber
                                Button {
                                    onSelectEpisode(ep)
                                    dismiss()
                                } label: {
                                    VStack(spacing: 4) {
                                        Text(ep.label)
                                            .font(.system(size: 14, weight: isSelected ? .bold : .medium))
                                            .foregroundStyle(isSelected ? .white : AuroraColor.textPrimary)
                                            .lineLimit(1)

                                        Text(isSelected ? "正在播放" : "选集")
                                            .font(.caption2)
                                            .foregroundStyle(isSelected ? .white.opacity(0.85) : AuroraColor.textTertiary)
                                    }
                                    .frame(maxWidth: .infinity, minHeight: 52)
                                    .padding(.horizontal, 8)
                                    .background(
                                        isSelected ? AuroraColor.brand : AuroraColor.surfaceCard,
                                        in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    )
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                                            .stroke(
                                                isSelected ? Color.white.opacity(0.6) : AuroraColor.hairline,
                                                lineWidth: 1
                                            )
                                    )
                                }
                                .buttonStyle(AppleSpringCardButtonStyle())
                                .accessibilityLabel("第 \(ep.number) 集，\(isSelected ? "当前播放" : "轻点播放")")
                            }
                        }
                        .padding(.horizontal, AuroraMetrics.contentInset)
                        .padding(.bottom, AuroraMetrics.spacingXL)
                    }
                }
            }
            .navigationTitle("player.episodes".localized)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("common.done".localized) {
                        dismiss()
                        onDismiss()
                    }
                    .font(.body.bold())
                    .foregroundStyle(AuroraColor.brand)
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}
