import SwiftUI

/// Apple TV 风格的「接着播放（Up Next）」横向卡片
public struct UpNextCard: View {
    public let record: PlaybackRecord
    public let onPlay: () -> Void
    public let onMarkCompleted: () -> Void
    public let onRemove: () -> Void
    public var onDetail: (() -> Void)? = nil

    public init(
        record: PlaybackRecord,
        onPlay: @escaping () -> Void,
        onMarkCompleted: @escaping () -> Void,
        onRemove: @escaping () -> Void,
        onDetail: (() -> Void)? = nil
    ) {
        self.record = record
        self.onPlay = onPlay
        self.onMarkCompleted = onMarkCompleted
        self.onRemove = onRemove
        self.onDetail = onDetail
    }

    public var body: some View {
        Button(action: onPlay) {
            VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                // 1. 16:9 黄金画幅封面与进度指示器
                ZStack(alignment: .bottomLeading) {
                    AuroraCoverImage(
                        url: record.video.coverUrl,
                        title: record.video.title,
                        sourceBaseURL: record.video.coverBaseURL,
                        aspectRatio: 16.0 / 9.0,
                        contentMode: .fill,
                        cornerRadius: AuroraMetrics.controlRadius
                    )
                    .clipShape(RoundedRectangle(cornerRadius: AuroraMetrics.controlRadius, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: AuroraMetrics.controlRadius, style: .continuous)
                            .stroke(AuroraColor.hairline, lineWidth: 1)
                    )

                    // 右上角来源角标胶囊
                    VStack {
                        HStack {
                            Spacer()
                            Text(record.sourceName)
                                .font(.system(size: 10, weight: .bold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 3)
                                .background(.black.opacity(0.65), in: Capsule())
                                .padding(6)
                        }
                        Spacer()
                    }

                    // 底部 3pt 线性播放进度条
                    if record.progressRatio > 0.01 {
                        GeometryReader { proxy in
                            ZStack(alignment: .leading) {
                                Capsule()
                                    .fill(Color.white.opacity(0.25))

                                Capsule()
                                    .fill(AuroraColor.brand)
                                    .frame(width: max(proxy.size.width * CGFloat(record.progressRatio), 6))
                            }
                        }
                        .frame(height: 3)
                        .padding(.horizontal, 6)
                        .padding(.bottom, 6)
                    }
                }
                .frame(width: cardWidth, height: cardWidth * (9.0 / 16.0))

                // 2. 影视标题与进度信息
                VStack(alignment: .leading, spacing: 2) {
                    Text(record.video.title)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(AuroraColor.textPrimary)
                        .lineLimit(1)

                    HStack(spacing: 4) {
                        Text("\(Int(record.progressRatio * 100))%")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(AuroraColor.brand)

                        Text("·")
                            .font(.system(size: 11))
                            .foregroundStyle(AuroraColor.textTertiary)

                        Text(formattedRemainingTime)
                            .font(.system(size: 11))
                            .foregroundStyle(AuroraColor.textSecondary)
                    }
                }
                .frame(width: cardWidth, alignment: .leading)
            }
        }
        .buttonStyle(AppleSpringCardButtonStyle())
        .hoverEffect(.lift)
        .contextMenu {
            if let onDetail = onDetail {
                Button {
                    onDetail()
                } label: {
                    Label("查看详情", systemImage: "info.circle")
                }
            }

            Button {
                onMarkCompleted()
            } label: {
                Label("标记为已观看", systemImage: "checkmark.circle")
            }

            Button(role: .destructive) {
                onRemove()
            } label: {
                Label("从待播清单移除", systemImage: "xmark.circle")
            }

            if let url = URL(string: record.video.playbackUrl), !record.video.playbackUrl.isEmpty {
                ShareLink(item: url) {
                    Label("分享视频", systemImage: "square.and.arrow.up")
                }
            }
        } preview: {
            AuroraCoverImage(
                url: record.video.coverUrl,
                title: record.video.title,
                sourceBaseURL: record.video.coverBaseURL,
                aspectRatio: AuroraMetrics.wideAspect,
                contentMode: .fill,
                cornerRadius: AuroraMetrics.cardRadius
            )
            .frame(width: 320, height: 180)
        }
    }

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    private var cardWidth: CGFloat {
        let form = DeviceLayout.resolveCurrent(horizontalSizeClass: horizontalSizeClass)
        return DeviceLayout.upNextCardWidth(for: form)
    }

    private var formattedRemainingTime: String {
        let remainingSeconds = max((record.durationMs - record.positionMs) / 1000, 0)
        let minutes = remainingSeconds / 60
        if minutes > 60 {
            return "剩余 \(minutes / 60)小时\(minutes % 60)分"
        } else if minutes > 0 {
            return "剩余 \(minutes)分钟"
        } else {
            return "即将播完"
        }
    }
}
