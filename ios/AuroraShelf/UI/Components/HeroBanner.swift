import SwiftUI
import Combine

/// 原生 Hero 聚焦横幅：1:1 对齐 Apple TV 聚光灯大卡（大圆角、三态控制组与环境渐变，支持 4 部精选轮播与 Apple TV 膨胀胶囊指示点）
public struct HeroBanner: View {
    public let videos: [VideoItem]
    public var pullOffset: CGFloat = 0
    public let onPlayVideo: (VideoItem) -> Void
    public let onDetailVideo: (VideoItem) -> Void

    public var video: VideoItem {
        videos.first ?? VideoItem(id: "", title: "", subtitle: "", coverUrl: "", playbackUrl: "", duration: "", rating: "", tags: [], category: "", isTop10: false, rankNumber: nil, sourceName: "")
    }

    @ObservedObject private var watchlist = WatchlistStore.shared
    @ObservedObject private var locManager = LocalizationManager.shared
    @State private var currentIndex: Int = 0
    @State private var watchlistError: String?
    @Environment(\.auroraContainerSize) private var containerSize
    @Environment(\.appearsActive) private var appearsActive
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOverEnabled

    private var effectiveSize: CGSize {
        (containerSize.width > 0 && containerSize.height > 0) ? containerSize : DeviceLayout.currentWindowSize
    }

    private var formFactor: DeviceFormFactor {
        DeviceLayout.resolveCurrent(containerSize: containerSize, horizontalSizeClass: horizontalSizeClass)
    }

    private var heroHeight: CGFloat {
        DeviceLayout.heroHeight(for: formFactor, screenHeight: effectiveSize.height)
    }

    private var isWideAspect: Bool {
        formFactor.isWide
    }

    public init(
        videos: [VideoItem],
        pullOffset: CGFloat = 0,
        onPlay: @escaping (VideoItem) -> Void,
        onDetail: @escaping (VideoItem) -> Void
    ) {
        self.videos = videos
        self.pullOffset = pullOffset
        self.onPlayVideo = onPlay
        self.onDetailVideo = onDetail
    }

    public init(
        video: VideoItem,
        pullOffset: CGFloat = 0,
        onPlay: @escaping () -> Void,
        onDetail: @escaping () -> Void
    ) {
        self.videos = [video]
        self.pullOffset = pullOffset
        self.onPlayVideo = { _ in onPlay() }
        self.onDetailVideo = { _ in onDetail() }
    }

    public var body: some View {
        let activePull = max(0, pullOffset)
        ZStack(alignment: .bottom) {
            if videos.count <= 1, let firstVideo = videos.first {
                heroSlide(for: firstVideo, pull: activePull)
            } else if !videos.isEmpty {
                TabView(selection: $currentIndex) {
                    ForEach(Array(videos.enumerated()), id: \.element.id) { index, item in
                        heroSlide(for: item, pull: activePull)
                            .tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .frame(height: heroHeight + activePull)
                .offset(y: -activePull)

                // Apple TV 标志性长条微光展开胶囊指示点
                HStack(spacing: 6) {
                    ForEach(0..<videos.count, id: \.self) { idx in
                        Capsule()
                            .fill(currentIndex == idx ? AuroraColor.brandSolid : AuroraColor.textPrimary.opacity(0.35))
                            .frame(width: currentIndex == idx ? 22 : 6, height: 6)
                            .shadow(color: currentIndex == idx ? AuroraColor.brand.opacity(0.45) : Color.clear, radius: 4)
                            .animation(AuroraMotion.smooth(reduceMotion: reduceMotion), value: currentIndex)
                            .onTapGesture {
                                AuroraMotion.perform(AuroraMotion.smooth, reduceMotion: reduceMotion) {
                                    currentIndex = idx
                                }
                            }
                    }
                }
                .padding(.bottom, AuroraMetrics.spacingM)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: heroHeight + activePull)
        .onReceive(Timer.publish(every: 6.5, on: .main, in: .common).autoconnect()) { _ in
            guard videos.count > 1, appearsActive, !reduceMotion, !voiceOverEnabled else { return }
            AuroraMotion.perform(AuroraMotion.smooth, reduceMotion: reduceMotion) {
                currentIndex = (currentIndex + 1) % videos.count
            }
        }
        .alert("hero.watchlist_error_title".localized, isPresented: Binding(
            get: { watchlistError != nil },
            set: { if !$0 { watchlistError = nil } }
        )) {
            Button("common.ok".localized, role: .cancel) { watchlistError = nil }
        } message: {
            Text(watchlistError ?? "hero.try_again_later".localized)
        }
    }

    public enum PresentationStyle: Equatable, Sendable {
        case fullBleed
        case cinemaStage
    }

    public static func presentationStyle(imagePixelSize: CGSize, containerSize: CGSize) -> PresentationStyle {
        return .fullBleed
    }

    @ViewBuilder
    private func heroSlide(for videoItem: VideoItem, pull: CGFloat) -> some View {
        let targetUrl = videoItem.heroCoverUrl ?? videoItem.coverUrl
        ZStack(alignment: .bottom) {
            Color.clear
                .overlay(
                    AuroraCoverImage(
                        url: targetUrl,
                        title: videoItem.title,
                        sourceBaseURL: videoItem.coverBaseURL,
                        aspectRatio: nil,
                        contentMode: .fill,
                        cornerRadius: 0
                    )
                )
                .frame(height: heroHeight + pull)
                .frame(maxWidth: .infinity)
                .scaleEffect(max(1.0, 1.0 + (pull / max(heroHeight, 1)) * 0.5), anchor: .top)
                .offset(y: -pull)
                .clipped()

            VStack(spacing: 0) {
                LinearGradient(
                    colors: [Color.black.opacity(0.65), Color.clear],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: 120)

                Spacer()

                LinearGradient(
                    stops: [
                        .init(color: .clear, location: 0.0),
                        .init(color: AuroraColor.canvas.opacity(0.18), location: 0.40),
                        .init(color: AuroraColor.canvas.opacity(0.62), location: 0.68),
                        .init(color: AuroraColor.canvas.opacity(0.90), location: 0.86),
                        .init(color: AuroraColor.canvas, location: 1.0)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: formFactor == .duoFolded ? 200 : (formFactor.isWide ? 320 : 260))
            }
            .frame(maxWidth: .infinity, maxHeight: heroHeight + pull)
            .offset(y: -pull)

            VStack(spacing: AuroraMetrics.spacingM) {
                Text(videoItem.title)
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundStyle(AuroraColor.textPrimary)
                    .multilineTextAlignment(.center)
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 2)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, AuroraMetrics.contentInset)

                metadataView(for: videoItem)

                GlassEffectContainer(spacing: AuroraMetrics.spacingM) {
                    HStack(spacing: AuroraMetrics.spacingM) {
                        playButton(for: videoItem)
                            .frame(maxWidth: formFactor.isWide ? 220 : .infinity)
                        watchlistButton(for: videoItem)
                        detailButton(for: videoItem)
                    }
                }
                .frame(maxWidth: DeviceLayout.heroActionsMaxWidth(for: formFactor) ?? .infinity)
                .padding(.horizontal, AuroraMetrics.contentInset)
                .padding(.bottom, videos.count > 1 ? (AuroraMetrics.spacingM + 18) : AuroraMetrics.spacingL)
            }
        }
        .frame(height: heroHeight + pull)
        .frame(maxWidth: .infinity)
        .clipped()
    }

    @ViewBuilder
    private func metadataView(for targetVideo: VideoItem) -> some View {
        HStack(spacing: AuroraMetrics.spacingS) {
            if !targetVideo.duration.isEmpty {
                Text(targetVideo.duration)
                    .font(.caption2.bold())
                    .foregroundStyle(AuroraColor.textPrimary)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(AuroraColor.surfaceCard.opacity(0.88), in: Capsule())
            }

            if !targetVideo.rating.isEmpty {
                HStack(spacing: 3) {
                    Image(systemName: "star.fill")
                        .font(.caption2)
                        .foregroundStyle(.yellow)
                    Text(targetVideo.rating)
                        .font(.caption.bold())
                        .foregroundStyle(AuroraColor.textPrimary)
                }
            }

            Text("•")
                .foregroundStyle(AuroraColor.textSecondary)

            Text(targetVideo.sourceName)
                .font(.caption.bold())
                .foregroundStyle(AuroraColor.brandLabel)
        }
        .lineLimit(1)
        .fixedSize(horizontal: false, vertical: true)
    }

    // Apple TV 核心：深浅色自适应高对比主播放胶囊（深色白底黑字 21:1，浅色黑底白字 17.2:1）
    private func playButton(for targetVideo: VideoItem) -> some View {
        Button {
            onPlayVideo(targetVideo)
        } label: {
            HStack(spacing: 6) {
                Image(systemName: "play.fill")
                    .font(.headline)
                Text("home.play".localized)
                    .font(.headline.weight(.bold))
            }
            .frame(maxWidth: .infinity, minHeight: 46)
            .background(AuroraColor.textPrimary, in: Capsule())
            .foregroundStyle(AuroraColor.canvas)
        }
        .auroraZoomSource("video:\(targetVideo.id)")
        .buttonStyle(AppleSpringCardButtonStyle())
    }

    // Apple TV 次要操作：iOS 27 原生 Liquid Glass 片单胶囊（消除手写黑灰叠加）
    private func watchlistButton(for targetVideo: VideoItem) -> some View {
        let isSaved = watchlist.contains(targetVideo)
        return Button {
            do {
                try watchlist.setBookmarked(!isSaved, for: targetVideo)
            } catch {
                watchlistError = error.localizedDescription
            }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: isSaved ? "checkmark" : "plus")
                    .font(.headline)
                Text(isSaved ? "watchlist.in_list".localized : "home.watchlist".localized)
                    .font(.headline.weight(.semibold))
                    .lineLimit(1)
            }
            .frame(minHeight: 34)
            .padding(.horizontal, 6)
            .fixedSize(horizontal: true, vertical: false)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.capsule)
        .tint(isSaved ? AuroraColor.brandLabel : AuroraColor.textPrimary)
        .hoverEffect(.highlight)
        .accessibilityHint(isSaved ? "hero.remove_hint".localized : "hero.add_hint".localized)
    }

    // Apple TV 详情圆形原生 Liquid Glass 胶囊
    private func detailButton(for targetVideo: VideoItem) -> some View {
        Button {
            onDetailVideo(targetVideo)
        } label: {
            Image(systemName: "info.circle")
                .font(.title3)
                .frame(width: 46, height: 46)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
        .tint(AuroraColor.textPrimary)
        .hoverEffect(.highlight)
        .accessibilityLabel("hero.details_a11y".localized)
    }
}
