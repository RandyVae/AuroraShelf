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
    @State private var currentIndex: Int = 0
    @State private var watchlistError: String?
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    private var formFactor: DeviceFormFactor {
        DeviceLayout.resolveCurrent(horizontalSizeClass: horizontalSizeClass)
    }

    private var heroHeight: CGFloat {
        DeviceLayout.heroHeight(for: formFactor, screenHeight: DeviceLayout.currentWindowSize.height)
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
                            .fill(currentIndex == idx ? Color.white : Color.white.opacity(0.35))
                            .frame(width: currentIndex == idx ? 22 : 6, height: 6)
                            .shadow(color: currentIndex == idx ? Color.white.opacity(0.6) : Color.clear, radius: 4)
                            .animation(AuroraMotion.smooth, value: currentIndex)
                            .onTapGesture {
                                withAnimation(AuroraMotion.smooth) {
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
            guard videos.count > 1 else { return }
            withAnimation(AuroraMotion.smooth) {
                currentIndex = (currentIndex + 1) % videos.count
            }
        }
        .alert("未能保存片单", isPresented: Binding(
            get: { watchlistError != nil },
            set: { if !$0 { watchlistError = nil } }
        )) {
            Button("好", role: .cancel) { watchlistError = nil }
        } message: {
            Text(watchlistError ?? "请稍后重试。")
        }
    }

    @ViewBuilder
    private func heroSlide(for videoItem: VideoItem, pull: CGFloat) -> some View {
        ZStack(alignment: .bottom) {
            // 背景封面海报：沉浸式充满视口，支持 Apple TV 原生下拉橡皮筋视差缩放（Stretch Parallax）
            Color.clear
                .overlay(
                    AuroraCoverImage(
                        url: videoItem.coverUrl,
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

            // 多层渐变可读性遮罩（Apple TV 自然五阶衰减，无痕融合画布底色）
            VStack(spacing: 0) {
                LinearGradient(
                    colors: [Color.black.opacity(0.7), Color.clear],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: 120)

                Spacer()

                LinearGradient(
                    stops: [
                        .init(color: .clear, location: 0.0),
                        .init(color: AuroraColor.canvas.opacity(0.15), location: 0.40),
                        .init(color: AuroraColor.canvas.opacity(0.55), location: 0.65),
                        .init(color: AuroraColor.canvas.opacity(0.88), location: 0.85),
                        .init(color: AuroraColor.canvas, location: 1.0)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: formFactor == .duoFolded ? 200 : (formFactor.isWide ? 320 : 260))
            }
            .frame(maxWidth: .infinity, maxHeight: heroHeight + pull)
            .offset(y: -pull)

            // 底部内容信息与操作区
            VStack(spacing: AuroraMetrics.spacingM) {
                // 标题（语义样式 .title2 + .bold，带柔和阴影确保阅读舒适度）
                Text(videoItem.title)
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundStyle(AuroraColor.textPrimary)
                    .multilineTextAlignment(.center)
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 2)
                    .fixedSize(horizontal: false, vertical: true)
                    .shadow(color: .black.opacity(0.95), radius: 8, x: 0, y: 3)
                    .padding(.horizontal, AuroraMetrics.contentInset)

                // 元信息行（4K · 评分 · 来源）
                metadataView(for: videoItem)
                    .shadow(color: .black.opacity(0.9), radius: 6, x: 0, y: 2)

                // Apple TV 标志性三元动作按钮栏（[ 播放 ] + [ 片单 ] + [ 详情 ]）
                HStack(spacing: AuroraMetrics.spacingM) {
                    playButton(for: videoItem)
                        .frame(maxWidth: formFactor.isWide ? 220 : .infinity)
                    watchlistButton(for: videoItem)
                    detailButton(for: videoItem)
                }
                .frame(maxWidth: DeviceLayout.heroActionsMaxWidth(for: formFactor) ?? .infinity)
                .padding(.horizontal, AuroraMetrics.contentInset)
                .padding(.bottom, videos.count > 1 ? (AuroraMetrics.spacingM + 18) : AuroraMetrics.spacingL)
            }
        }
    }

    @ViewBuilder
    private func metadataView(for targetVideo: VideoItem) -> some View {
        HStack(spacing: AuroraMetrics.spacingS) {
            if !targetVideo.duration.isEmpty {
                Text(targetVideo.duration)
                    .font(.caption2.bold())
                    .foregroundStyle(.white)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(.white.opacity(0.2), in: Capsule())
            }

            if !targetVideo.rating.isEmpty {
                HStack(spacing: 3) {
                    Image(systemName: "star.fill")
                        .font(.caption2)
                        .foregroundStyle(.yellow)
                    Text(targetVideo.rating)
                        .font(.caption.bold())
                        .foregroundStyle(.white)
                }
            }

            Text("•")
                .foregroundStyle(.white.opacity(0.6))

            Text(targetVideo.sourceName)
                .font(.caption.bold())
                .foregroundStyle(AuroraColor.brandLabel)
        }
        .lineLimit(1)
        .fixedSize(horizontal: false, vertical: true)
    }

    // Apple TV 核心：白底黑字高对比主播放胶囊
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
            .background(Color.white, in: Capsule())
            .foregroundStyle(Color.black)
        }
        .buttonStyle(AppleSpringCardButtonStyle())
        .hoverEffect(.highlight)
    }

    // Apple TV 次要操作：毛玻璃片单胶囊
    private func watchlistButton(for targetVideo: VideoItem) -> some View {
        Button {
            do {
                try watchlist.setBookmarked(!watchlist.contains(targetVideo), for: targetVideo)
            } catch {
                watchlistError = error.localizedDescription
            }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: watchlist.contains(targetVideo) ? "checkmark" : "plus")
                    .font(.headline)
                Text(watchlist.contains(targetVideo) ? "watchlist.in_list".localized : "home.watchlist".localized)
                    .font(.headline.weight(.semibold))
            }
            .frame(minHeight: 46)
            .padding(.horizontal, 16)
            .background(.ultraThinMaterial, in: Capsule())
            .background(Color.black.opacity(0.35), in: Capsule())
            .overlay(
                Capsule()
                    .stroke(Color.white.opacity(0.25), lineWidth: 1)
            )
            .foregroundStyle(.white)
        }
        .buttonStyle(AppleSpringCardButtonStyle())
        .hoverEffect(.highlight)
        .accessibilityHint(watchlist.contains(targetVideo) ? "从我的片单移除" : "添加到我的片单")
    }

    // Apple TV 详情圆形胶囊
    private func detailButton(for targetVideo: VideoItem) -> some View {
        Button {
            onDetailVideo(targetVideo)
        } label: {
            Image(systemName: "info.circle")
                .font(.title3)
                .frame(width: 46, height: 46)
                .background(.ultraThinMaterial, in: .circle)
                .background(Color.black.opacity(0.35), in: .circle)
                .overlay(
                    Circle()
                        .stroke(Color.white.opacity(0.25), lineWidth: 1)
                )
                .foregroundStyle(.white)
        }
        .buttonStyle(AppleSpringCardButtonStyle())
        .hoverEffect(.highlight)
        .accessibilityLabel("查看详情")
    }
}
