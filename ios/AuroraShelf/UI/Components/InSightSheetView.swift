import SwiftUI
import AVKit

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
        NavigationStack {
            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: AuroraMetrics.spacingXL) {
                    // 1. 顶部当前影视卡片
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

                    // 2. 媒体流与画质规格
                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                        Text("媒体规格")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundStyle(AuroraColor.textTertiary)
                            .padding(.horizontal, AuroraMetrics.contentInset)

                        HStack(spacing: AuroraMetrics.spacingM) {
                            specBadge(title: "清晰度", value: "4K HDR")
                            specBadge(title: "协议流", value: video.playbackUrl.contains(".m3u8") ? "HLS" : "MP4")
                            specBadge(title: "色彩模式", value: "DCI-P3")
                            specBadge(title: "声道", value: "空间立体声")
                        }
                        .padding(.horizontal, AuroraMetrics.contentInset)
                    }

                    // 3. 画面与投屏控制
                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                        Text("显示与投屏")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundStyle(AuroraColor.textTertiary)
                            .padding(.horizontal, AuroraMetrics.contentInset)

                        VStack(spacing: 1) {
                            // 画幅切换
                            Button(action: onToggleFillMode) {
                                HStack {
                                    Label(
                                        isFillMode ? "画幅：裁切充满（Fill）" : "画幅：等比居中（Fit）",
                                        systemImage: isFillMode ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right"
                                    )
                                    Spacer()
                                    Text(isFillMode ? "消除黑边" : "原始比例")
                                        .font(.subheadline)
                                        .foregroundStyle(AuroraColor.textSecondary)
                                }
                                .padding()
                                .background(AuroraColor.surfaceCard)
                            }
                            .buttonStyle(.plain)

                            // 屏幕旋转
                            Button(action: onToggleOrientation) {
                                HStack {
                                    Label(
                                        isLandscape ? "显示方向：横屏沉浸" : "显示方向：竖屏便携",
                                        systemImage: isLandscape ? "iphone.portrait" : "iphone.landscape"
                                    )
                                    Spacer()
                                    Text(isLandscape ? "90° 旋转" : "默认")
                                        .font(.subheadline)
                                        .foregroundStyle(AuroraColor.textSecondary)
                                }
                                .padding()
                                .background(AuroraColor.surfaceCard)
                            }
                            .buttonStyle(.plain)

                            // 投屏
                            HStack {
                                Label("AirPlay 隔空播放", systemImage: "airplayvideo")
                                Spacer()
                                AirPlayRoutePicker()
                                    .frame(width: 44, height: 44)
                            }
                            .padding(.horizontal)
                            .padding(.vertical, 4)
                            .background(AuroraColor.surfaceCard)
                        }
                        .clipShape(RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous))
                        .padding(.horizontal, AuroraMetrics.contentInset)
                    }

                    // 4. 片单与待播快捷操作
                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                        Text("片单管理")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundStyle(AuroraColor.textTertiary)
                            .padding(.horizontal, AuroraMetrics.contentInset)

                        HStack(spacing: AuroraMetrics.spacingM) {
                            let isBookmarked = watchlistStore.contains(video)
                            Button {
                                try? watchlistStore.setBookmarked(!isBookmarked, for: video)
                            } label: {
                                Label(
                                    isBookmarked ? "移出片单" : "加入片单",
                                    systemImage: isBookmarked ? "bookmark.fill" : "bookmark"
                                )
                                .font(.subheadline.bold())
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(isBookmarked ? AuroraColor.brand.opacity(0.15) : AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: 10))
                                .foregroundStyle(isBookmarked ? AuroraColor.brand : AuroraColor.textPrimary)
                            }
                            .buttonStyle(.plain)

                            Button {
                                progressStore.markCompleted(for: video)
                            } label: {
                                Label("标记已看", systemImage: "checkmark.circle")
                                    .font(.subheadline.bold())
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: 10))
                                    .foregroundStyle(AuroraColor.textPrimary)
                            }
                            .buttonStyle(.plain)
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
                    }
                }
            }
        }
    }

    private func specBadge(title: String, value: String) -> some View {
        VStack(spacing: 2) {
            Text(title)
                .font(.system(size: 10))
                .foregroundStyle(AuroraColor.textTertiary)
            Text(value)
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(AuroraColor.textPrimary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
    }
}
