import SwiftUI

/// Apple TV 风格的「接着播放（Up Next）」横向卡片
public struct UpNextCard: View {
    public let record: PlaybackRecord
    public let onPlay: () -> Void
    public let onMarkCompleted: () -> Void
    public let onRemove: () -> Void
    public var onDetail: (() -> Void)? = nil

    @ObservedObject private var locManager = LocalizationManager.shared
    @Environment(\.auroraContainerSize) private var containerSize
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

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

                    VStack {
                        HStack {
                            Spacer()
                            Text(record.sourceName)
                                .font(.caption2.weight(.bold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 3)
                                .background(.black.opacity(0.65), in: Capsule())
                                .padding(6)
                        }
                        Spacer()
                    }

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

                VStack(alignment: .leading, spacing: 2) {
                    Text(record.video.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(AuroraColor.textPrimary)
                        .lineLimit(1)

                    HStack(spacing: 4) {
                        Text("\(Int(record.progressRatio * 100))%")
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(AuroraColor.brandLabel)

                        Text("·")
                            .font(.caption2)
                            .foregroundStyle(AuroraColor.textTertiary)

                        Text(formattedRemainingTime)
                            .font(.caption2)
                            .foregroundStyle(AuroraColor.textSecondary)
                    }
                }
                .frame(width: cardWidth, alignment: .leading)
            }
        }
        .auroraZoomSource("video:\(record.video.id)")
        .buttonStyle(AppleSpringCardButtonStyle())
        .contextMenu {
            if let onDetail = onDetail {
                Button {
                    onDetail()
                } label: {
                    Label("hero.details_a11y".localized, systemImage: "info.circle")
                }
            }

            Button {
                onMarkCompleted()
            } label: {
                Label("card.mark_watched".localized, systemImage: "checkmark.circle")
            }

            Button(role: .destructive) {
                onRemove()
            } label: {
                Label("card.remove_up_next".localized, systemImage: "xmark.circle")
            }

            if let url = URL(string: record.video.playbackUrl), !record.video.playbackUrl.isEmpty {
                ShareLink(item: url) {
                    Label("card.share_video".localized, systemImage: "square.and.arrow.up")
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

    private var cardWidth: CGFloat {
        let form = DeviceLayout.resolveCurrent(containerSize: containerSize, horizontalSizeClass: horizontalSizeClass)
        return DeviceLayout.upNextCardWidth(for: form)
    }

    private var formattedRemainingTime: String {
        let remainingSeconds = max((record.durationMs - record.positionMs) / 1000, 0)
        let minutes = remainingSeconds / 60
        if minutes > 60 {
            return "upnext.remaining_hm".localized(minutes / 60, minutes % 60)
        } else if minutes > 0 {
            return "upnext.remaining_m".localized(minutes)
        } else {
            return "upnext.almost_done".localized
        }
    }
}
