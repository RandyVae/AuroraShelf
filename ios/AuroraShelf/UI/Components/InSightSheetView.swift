import AVKit
import SwiftUI

/// Apple TV 风格的 InSight 实时音画感知与控制面板
public struct InSightSheetView: View {
    public let video: VideoItem
    public let currentTime: Double
    public let duration: Double
    public let isFillMode: Bool
    public let isLandscape: Bool
    public let onToggleFillMode: () -> Void
    public let onToggleOrientation: () -> Void
    public let onDismiss: () -> Void

    @ObservedObject private var watchlistStore = WatchlistStore.shared
    @ObservedObject private var progressStore = PlaybackProgressStore.shared
    @ObservedObject private var locManager = LocalizationManager.shared

    public init(
        video: VideoItem,
        currentTime: Double,
        duration: Double,
        isFillMode: Bool,
        isLandscape: Bool,
        onToggleFillMode: @escaping () -> Void,
        onToggleOrientation: @escaping () -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self.video = video
        self.currentTime = currentTime
        self.duration = duration
        self.isFillMode = isFillMode
        self.isLandscape = isLandscape
        self.onToggleFillMode = onToggleFillMode
        self.onToggleOrientation = onToggleOrientation
        self.onDismiss = onDismiss
    }

    public var body: some View {
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        NavigationStack {
            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: AuroraMetrics.spacingXL) {
                    HStack(spacing: AuroraMetrics.spacingM) {
                        AuroraCoverImage(
                            url: video.coverUrl,
                            sourceBaseURL: video.coverBaseURL,
                            aspectRatio: 16.0 / 9.0,
                            contentMode: .fill,
                            cornerRadius: 8
                        )
                        .frame(width: 120, height: 67.5)
                        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .stroke(AuroraColor.hairline, lineWidth: 1)
                        )

                        VStack(alignment: .leading, spacing: 4) {
                            Text(video.title)
                                .font(.headline)
                                .fontWeight(.bold)
                                .foregroundStyle(AuroraColor.textPrimary)
                                .lineLimit(2)

                            HStack(spacing: 6) {
                                Text(video.sourceName)
                                    .font(.caption2)
                                    .fontWeight(.bold)
                                    .foregroundStyle(AuroraColor.brandLabel)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(AuroraColor.brand.opacity(0.12), in: Capsule())

                                if duration > 0 {
                                    Text("\(Int(min(currentTime / duration, 1.0) * 100))%")
                                        .font(.caption2)
                                        .foregroundStyle(AuroraColor.textSecondary)
                                }
                            }
                        }
                    }
                    .padding(.horizontal, AuroraMetrics.contentInset)

                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                        Text("insight.media_specs".localized)
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundStyle(AuroraColor.textTertiary)
                            .padding(.horizontal, AuroraMetrics.contentInset)
                            .accessibilityAddTraits(.isHeader)

                        HStack(spacing: AuroraMetrics.spacingM) {
                            specBadge(
                                title: "insight.aspect_ratio".localized,
                                value: isFillMode ? "player.fill_screen".localized : "player.original_ratio".localized
                            )
                            specBadge(
                                title: "insight.protocol".localized,
                                value: video.playbackUrl.contains(".m3u8") ? "HLS" : "MP4"
                            )
                            specBadge(
                                title: "insight.video_source".localized,
                                value: video.sourceName
                            )
                        }
                        .padding(.horizontal, AuroraMetrics.contentInset)
                    }

                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                        Text("insight.display_airplay".localized)
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundStyle(AuroraColor.textTertiary)
                            .padding(.horizontal, AuroraMetrics.contentInset)
                            .accessibilityAddTraits(.isHeader)

                        VStack(spacing: 1) {
                            Button(action: onToggleFillMode) {
                                HStack {
                                    Label(
                                        isFillMode ? "insight.aspect_fill_desc".localized : "insight.aspect_fit_desc".localized,
                                        systemImage: isFillMode ? "rectangle.arrowtriangle.2.inward" : "rectangle.arrowtriangle.2.outward"
                                    )
                                    Spacer()
                                    Text(isFillMode ? "insight.remove_bars".localized : "player.original_ratio".localized)
                                        .font(.subheadline)
                                        .foregroundStyle(AuroraColor.textSecondary)
                                }
                                .padding()
                                .frame(minHeight: AuroraMetrics.minTouchTarget)
                                .background(AuroraColor.surfaceCard)
                            }
                            .buttonStyle(.plain)
                            .hoverEffect(.highlight)

                            if !isPad {
                                Button(action: onToggleOrientation) {
                                    HStack {
                                        Label(
                                            isLandscape ? "insight.orient_landscape".localized : "insight.orient_portrait".localized,
                                            systemImage: isLandscape ? "iphone" : "iphone.landscape"
                                        )
                                        Spacer()
                                        Text(isLandscape ? "insight.landscape_locked".localized : "insight.default".localized)
                                            .font(.subheadline)
                                            .foregroundStyle(AuroraColor.textSecondary)
                                    }
                                    .padding()
                                    .frame(minHeight: AuroraMetrics.minTouchTarget)
                                    .background(AuroraColor.surfaceCard)
                                }
                                .buttonStyle(.plain)
                                .hoverEffect(.highlight)
                            }

                            HStack {
                                Label("insight.airplay_label".localized, systemImage: "airplayvideo")
                                Spacer()
                                AirPlayRoutePicker()
                                    .frame(width: 44, height: 44)
                            }
                            .padding(.horizontal)
                            .padding(.vertical, 4)
                            .frame(minHeight: AuroraMetrics.minTouchTarget)
                            .background(AuroraColor.surfaceCard)
                        }
                        .clipShape(RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous))
                        .padding(.horizontal, AuroraMetrics.contentInset)
                    }

                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                        Text("insight.watchlist_mgmt".localized)
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundStyle(AuroraColor.textTertiary)
                            .padding(.horizontal, AuroraMetrics.contentInset)
                            .accessibilityAddTraits(.isHeader)

                        HStack(spacing: AuroraMetrics.spacingM) {
                            let isBookmarked = watchlistStore.contains(video)
                            Button {
                                try? watchlistStore.setBookmarked(!isBookmarked, for: video)
                            } label: {
                                Label(
                                    isBookmarked ? "watchlist.in_list".localized : "watchlist.add".localized,
                                    systemImage: isBookmarked ? "bookmark.fill" : "bookmark"
                                )
                                .font(.subheadline.bold())
                                .frame(maxWidth: .infinity, minHeight: AuroraMetrics.minTouchTarget)
                                .padding(.vertical, 4)
                                .background(
                                    isBookmarked ? AuroraColor.brand.opacity(0.15) : AuroraColor.surfaceCard,
                                    in: RoundedRectangle(cornerRadius: 10)
                                )
                                .foregroundStyle(isBookmarked ? AuroraColor.brandLabel : AuroraColor.textPrimary)
                            }
                            .buttonStyle(.plain)
                            .hoverEffect(.highlight)

                            Button {
                                progressStore.markCompleted(for: video)
                            } label: {
                                Label("insight.mark_watched".localized, systemImage: "checkmark.circle")
                                    .font(.subheadline.bold())
                                    .frame(maxWidth: .infinity, minHeight: AuroraMetrics.minTouchTarget)
                                    .padding(.vertical, 4)
                                    .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: 10))
                                    .foregroundStyle(AuroraColor.textPrimary)
                            }
                            .buttonStyle(.plain)
                            .hoverEffect(.highlight)
                        }
                        .padding(.horizontal, AuroraMetrics.contentInset)
                    }
                }
                .padding(.vertical, AuroraMetrics.spacingL)
            }
            .background(AuroraColor.canvas.ignoresSafeArea())
            .navigationTitle("InSight")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onDismiss) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.headline)
                            .foregroundStyle(AuroraColor.textTertiary)
                            .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                            .contentShape(Rectangle())
                    }
                    .accessibilityLabel("common.close".localized)
                }
            }
        }
    }

    private func specBadge(title: String, value: String) -> some View {
        VStack(spacing: 2) {
            Text(title)
                .font(.caption2)
                .foregroundStyle(AuroraColor.textTertiary)
            Text(value)
                .font(.caption.weight(.bold))
                .foregroundStyle(AuroraColor.textPrimary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
    }
}
