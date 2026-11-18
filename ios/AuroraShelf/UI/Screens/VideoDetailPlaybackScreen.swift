import SwiftUI
import AVKit
import Combine
import MediaPlayer

/// 影视二级详情与内嵌播放一体化界面
/// - 流转层对标 YouTube：实时感知 `AVPlayerItem.presentationSize`，横片点全屏自动横屏满铺、竖片点全屏保持竖屏满铺，支持重力感应自动进出全屏与竖片内嵌滚动收折；
/// - 控件与光学层对标 Apple TV + iOS 27 SDK：全量采用 `Glass.clear.interactive()` 高透折射液态玻璃、撤除顶/中黑幕、无旋钮流体膨胀胶囊进度轨与正交二级页胶囊栏。
public struct VideoDetailPlaybackScreen: View {
    public let initialVideo: VideoItem
    public let onDismiss: () -> Void
    public var onOpenSearchKeyword: ((String) -> Void)? = nil

    @State private var currentVideo: VideoItem
    @State private var episodes: [PlaybackEpisode] = []
    @State private var currentEpisode: PlaybackEpisode?
    @State private var isLoadingEpisodes: Bool = false
    @State private var detailMetadata: VideoDetailMetadata? = nil
    @State private var isDescriptionExpanded: Bool = false
    @State private var isShowingEpisodePicker: Bool = false

    // 全屏、方向与画幅自适应状态（单一播放源驱动，零重新缓冲）
    @State private var isFullScreen: Bool = false
    @State private var isLandscape: Bool = false
    @State private var isFillMode: Bool = false
    @State private var videoPresentationSize: CGSize = .zero
    @State private var inlineScrollOffsetY: CGFloat = 0
    @State private var gravityMachine = PlayerGravityRotationStateMachine()
    @State private var deviceOrientationObserverToken: NSObjectProtocol?
    @State private var didBeginGeneratingOrientationNotifications: Bool = false

    // HUD 状态
    @State private var hudMessage: String?
    @State private var hudIcon: String?
    @State private var hudProgress: Double?
    @State private var hudTask: DispatchWorkItem?

    // Apple TV 倒计时、倍速与长按 2.0x 快放控制
    @State private var showRemainingTime: Bool = true
    @State private var playbackRate: Float = 1.0
    @State private var isSpeedBoosting: Bool = false
    @State private var preBoostPlaybackRate: Float = 1.0
    @State private var isShowingInPlayerEpisodes: Bool = false
    @State private var isShowingInSight: Bool = false
    @State private var isScrubbing: Bool = false
    @State private var isSeeking: Bool = false
    @State private var seekGeneration: UInt64 = 0
    @State private var isBuffering: Bool = false
    @State private var autoAdvanceWorkItem: DispatchWorkItem? = nil
    @State private var subtitleSelectionGroup: AVMediaSelectionGroup? = nil
    @State private var audioSelectionGroup: AVMediaSelectionGroup? = nil
    @State private var lastSavedSecond: Int = -1

    // 垂直滑动手势模式（左侧亮度、右侧音量）
    private enum PlayerVerticalGestureMode {
        case brightness
        case volume
    }
    @State private var activeGestureMode: PlayerVerticalGestureMode? = nil
    @State private var gestureInitialValue: CGFloat = 0
    @State private var originalScreenBrightness: CGFloat? = nil
    @State private var pinchBaselineFillMode: Bool? = nil
    @State private var didTriggerEndForCurrentItem: Bool = false
    @State private var remoteCommandCleanups: [() -> Void] = []

    // 核心播放器状态（全程唯一实例）
    @State private var player: AVPlayer?
    @State private var isPlaying: Bool = true
    @State private var isMuted: Bool = false
    @State private var isResolving: Bool = true
    @State private var resolveStatus: String = "正在连接视频源…"
    @State private var resolveError: String? = nil
    @State private var currentTime: Double = 0
    @State private var bufferedTime: Double = 0
    @State private var duration: Double = 0
    @State private var showControls: Bool = true
    @State private var hideControlsTask: DispatchWorkItem?
    @State private var timeObserverToken: Any?
    @State private var itemStatusObserver: NSKeyValueObservation?
    @State private var itemDurationObserver: NSKeyValueObservation?
    @State private var itemPresentationSizeObserver: NSKeyValueObservation?
    @State private var didPlayToEndObserverToken: NSObjectProtocol?
    @State private var failedToPlayObserverToken: NSObjectProtocol?
    @State private var recoveryStage: Int = 0
    @State private var isSwitchingNextEpisode: Bool = false
    @State private var activeResolveID = UUID()
    @State private var isViewDismissed: Bool = false
    @State private var showDownloadToast: Bool = false
    @State private var downloadToastText: String = ""
    @State private var measuredViewportSize: CGSize = .zero
    @State private var measuredSafeInsets: EdgeInsets = {
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        return EdgeInsets(top: isPad ? 24 : 59, leading: 0, bottom: isPad ? 20 : 34, trailing: 0)
    }()

    @Namespace private var glassMorphNamespace

    @ObservedObject private var watchlist = WatchlistStore.shared
    @ObservedObject private var downloadManager = OfflineDownloadManager.shared
    @ObservedObject private var progressStore = PlaybackProgressStore.shared
    @ObservedObject private var videoRepo = VideoRepository.shared
    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityVoiceOverEnabled) private var isVoiceOverEnabled
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public init(
        video: VideoItem,
        onDismiss: @escaping () -> Void,
        onOpenSearchKeyword: ((String) -> Void)? = nil
    ) {
        self.initialVideo = video
        self._currentVideo = State(initialValue: video)
        self.onDismiss = onDismiss
        self.onOpenSearchKeyword = onOpenSearchKeyword
    }

    private var relatedVideos: [VideoItem] {
        videoRepo.feedVideos.filter { $0.id != currentVideo.id }
    }

    private var effectiveAspectRatio: CGFloat {
        VideoAspectProfile.resolvedAspectRatio(
            presentationSize: videoPresentationSize,
            sourceName: currentVideo.sourceName,
            category: currentVideo.category
        )
    }

    private var videoOrientationKind: VideoOrientationKind {
        VideoAspectProfile.kind(forAspect: effectiveAspectRatio)
    }

    private func resolvedSafeTop(in size: CGSize) -> CGFloat {
        if measuredSafeInsets.top > 0 {
            return measuredSafeInsets.top
        }
        if size.width > size.height, UIDevice.current.userInterfaceIdiom != .pad {
            return 12
        }
        return UIDevice.current.userInterfaceIdiom == .pad ? 24 : 59
    }

    private var resolvedSafeBottom: CGFloat {
        if measuredSafeInsets.bottom > 0 {
            return measuredSafeInsets.bottom
        }
        return UIDevice.current.userInterfaceIdiom == .pad ? 20 : 34
    }

    public var body: some View {
        ZStack {
            Color.clear
                .onGeometryChange(for: EdgeInsets.self) { proxy in
                    proxy.safeAreaInsets
                } action: { insets in
                    if insets != measuredSafeInsets {
                        measuredSafeInsets = insets
                    }
                }
                .allowsHitTesting(false)

            GeometryReader { screenGeo in
                let rawSize = screenGeo.size
                let screenSize = (rawSize.width > 0 && rawSize.height > 0) ? rawSize : measuredViewportSize
                let idiom = UIDevice.current.userInterfaceIdiom
                let isPad = idiom == .pad
                let layoutMode = VideoDetailLayout.mode(size: screenSize, idiom: idiom)
                let isSplitMode = (layoutMode == .padLandscapeSplit && !isFullScreen)
                let columns = VideoDetailLayout.columns(for: screenSize, idiom: idiom)
                let playerWidth = columns.playerWidth(for: screenSize.width)
                let recommendationWidth = max(0, columns.recommendationWidth(for: screenSize.width) - 1)
                let isPhysicallyLandscape = screenSize.width > screenSize.height
                // 正常情况下由 PlayerOrientationCoordinator 驱动真实 UIWindow 旋转；仅在无窗口几何响应环境回退视图级旋转
                let isRotated = !isPad && isFullScreen && isLandscape && !isPhysicallyLandscape
                    && PlayerOrientationCoordinator.shared.needsViewRotationFallback
                let safeTop = resolvedSafeTop(in: screenSize)
                let collapseProgress = min(max(inlineScrollOffsetY / 180.0, 0), 1)
                let inlineHeight = VideoAspectProfile.inlineContainerHeight(
                    screenSize: screenSize,
                    playerColumnWidth: isSplitMode ? playerWidth : screenSize.width,
                    safeTop: safeTop,
                    aspectRatio: effectiveAspectRatio,
                    collapseProgress: isFullScreen ? 0 : collapseProgress,
                    idiom: idiom,
                    isSplitMode: isSplitMode
                )
                let containerWidth = isFullScreen ? screenSize.width : (isSplitMode ? playerWidth : screenSize.width)
                let targetWidth = isRotated ? screenSize.height : containerWidth
                let targetHeight: CGFloat = {
                    if isFullScreen {
                        return isRotated ? screenSize.width : screenSize.height
                    }
                    return inlineHeight
                }()

                ZStack(alignment: .topLeading) {
                    AuroraColor.canvas
                        .ignoresSafeArea()
                        .onGeometryChange(for: CGSize.self) { proxy in
                            proxy.size
                        } action: { newSize in
                            guard newSize.width > 0, newSize.height > 0 else { return }
                            if abs(newSize.width - measuredViewportSize.width) > 0.5 ||
                                abs(newSize.height - measuredViewportSize.height) > 0.5 {
                                measuredViewportSize = newSize
                            }
                        }

                    // 详情、选集与推荐列表（仅当非全屏时渲染；播放器始终在 ZStack 顶层保持唯一实例，横竖屏与全屏切换零销毁）
                    if !isFullScreen {
                        if isSplitMode {
                            HStack(alignment: .top, spacing: 0) {
                                ScrollViewReader { scrollProxy in
                                    ScrollView(.vertical, showsIndicators: false) {
                                        detailScrollContent(includeRecommendations: false, columnWidth: playerWidth)
                                    }
                                    .onScrollGeometryChange(for: CGFloat.self) { geo in
                                        max(0, geo.contentOffset.y + geo.contentInsets.top)
                                    } action: { _, newOffset in
                                        if abs(newOffset - inlineScrollOffsetY) > 1.0 {
                                            inlineScrollOffsetY = newOffset
                                        }
                                    }
                                    .onChange(of: currentVideo.id) { _, _ in
                                        inlineScrollOffsetY = 0
                                        withAnimation(.easeOut(duration: 0.2)) {
                                            scrollProxy.scrollTo("video-detail-top-anchor", anchor: .top)
                                        }
                                    }
                                }
                                .padding(.top, targetHeight)
                                .frame(width: playerWidth, height: screenSize.height)

                                Rectangle()
                                    .fill(AuroraColor.hairline)
                                    .frame(width: 1, height: screenSize.height)

                                ScrollView(.vertical, showsIndicators: false) {
                                    landscapeRecommendationRail(width: recommendationWidth, safeTop: safeTop)
                                }
                                .frame(width: recommendationWidth, height: screenSize.height)
                                .accessibilityIdentifier("video-detail-right-rail")
                            }
                            .accessibilityIdentifier("video-detail-split-layout")
                        } else {
                            ScrollViewReader { scrollProxy in
                                ScrollView(.vertical, showsIndicators: false) {
                                    detailScrollContent(includeRecommendations: true, columnWidth: screenSize.width)
                                }
                                .onScrollGeometryChange(for: CGFloat.self) { geo in
                                    max(0, geo.contentOffset.y + geo.contentInsets.top)
                                } action: { _, newOffset in
                                    if abs(newOffset - inlineScrollOffsetY) > 1.0 {
                                        inlineScrollOffsetY = newOffset
                                    }
                                }
                                .onChange(of: currentVideo.id) { _, _ in
                                    inlineScrollOffsetY = 0
                                    withAnimation(.easeOut(duration: 0.2)) {
                                        scrollProxy.scrollTo("video-detail-top-anchor", anchor: .top)
                                    }
                                }
                            }
                            .padding(.top, targetHeight)
                            .frame(width: screenSize.width, height: screenSize.height)
                            .accessibilityIdentifier("video-detail-stacked-layout")
                        }
                    }

                    // 顶部播放器视口（单一常驻实例：Portrait <-> LandscapeSplit <-> Fullscreen 全程不销毁/重建 AuroraPlayerSurface）
                    topPlayerContainer(
                        screenSize: screenSize,
                        containerWidth: containerWidth,
                        targetWidth: targetWidth,
                        targetHeight: targetHeight,
                        safeTop: safeTop,
                        isRotated: isRotated
                    )
                    .zIndex(10)

                    // 缓存状态浮动提示（iOS 27 Glass.clear 胶囊）
                    if showDownloadToast {
                        VStack {
                            Spacer().frame(height: min(targetHeight + 16, screenSize.height * 0.45))
                            HStack(spacing: 8) {
                                Image(systemName: "arrow.down.circle.fill")
                                    .foregroundStyle(AuroraColor.brand)
                                Text(downloadToastText)
                                    .font(.subheadline.bold())
                                    .foregroundStyle(.white)
                            }
                            .padding(.horizontal, 18)
                            .padding(.vertical, 10)
                            .glassEffect(.clear, in: .capsule)
                            .shadow(color: .black.opacity(0.28), radius: 10, y: 5)
                            .environment(\.colorScheme, .dark)
                            .transition(.move(edge: .top).combined(with: .opacity))
                            Spacer()
                        }
                        .frame(maxWidth: .infinity)
                        .allowsHitTesting(false)
                        .zIndex(20)
                    }
                }
                .frame(width: screenSize.width, height: screenSize.height)
            }
            .ignoresSafeArea()
        }
        .ignoresSafeArea()
        .toolbar(.hidden, for: .tabBar)
        .toolbar(.hidden, for: .navigationBar)
        .navigationBarHidden(true)
        .navigationBarBackButtonHidden(true)
        .statusBarHidden(isFullScreen ? (!showControls || isLandscape) : false)
        .persistentSystemOverlays(isFullScreen && !showControls ? .hidden : .automatic)
        .task(id: currentVideo.playbackUrl) {
            await startPlayback(for: currentVideo)
        }
        .task(id: currentVideo.id) {
            detailMetadata = nil
            async let episodesTask: Void = loadEpisodes(for: currentVideo)
            async let metaTask = videoRepo.fetchDetailMetadata(for: currentVideo)
            let (_, fetchedMeta) = await (episodesTask, metaTask)
            if !Task.isCancelled && !isViewDismissed {
                detailMetadata = fetchedMeta
            }
        }
        .onChange(of: isVoiceOverEnabled) { _, enabled in
            if enabled {
                hideControlsTask?.cancel()
                showControls = true
            }
        }
        .onAppear {
            isViewDismissed = false
            setupDeviceOrientationTrackingIfNeeded()
            let args = CommandLine.arguments
            if args.contains("--detail-landscape") {
                enterAdaptiveFullscreen(forceLandscapeOverride: true)
            } else if args.contains("--detail-fullscreen") {
                enterAdaptiveFullscreen()
            }
            if args.contains("--detail-fill") {
                isFillMode = true
            }
        }
        .onDisappear {
            isViewDismissed = true
            tearDownDeviceOrientationTracking()
            restoreBrightnessAndOrientationIfNeeded()
            cleanupPlayer(forceStopPlayer: !PictureInPictureManager.shared.isPiPActiveOrStarting)
        }
        .sheet(isPresented: $isShowingEpisodePicker) {
            EpisodePickerSheet(
                videoTitle: currentVideo.title,
                sourceName: currentVideo.sourceName,
                episodes: episodes,
                currentEpisodeNumber: currentEpisode?.number ?? 1,
                onSelectEpisode: { ep in
                    switchEpisode(ep)
                    isShowingEpisodePicker = false
                },
                onDismiss: {
                    isShowingEpisodePicker = false
                }
            )
        }
        .sheet(isPresented: $isShowingInSight) {
            InSightSheetView(
                video: currentVideo,
                currentTime: currentTime,
                duration: duration,
                isFillMode: isFillMode,
                isLandscape: isLandscape,
                onToggleFillMode: { toggleFillMode() },
                onToggleOrientation: { toggleManualOrientation() },
                onDismiss: { isShowingInSight = false }
            )
            .presentationDetents([.fraction(0.55), .large])
            .presentationDragIndicator(.visible)
        }
        .focusable()
        .focusEffectDisabled()
        .onKeyPress(.space) {
            togglePlayPause()
            return .handled
        }
        .onKeyPress(.leftArrow) {
            seekRelative(-10)
            showHud(icon: "gobackward.10", text: "快退 10 秒")
            return .handled
        }
        .onKeyPress(.rightArrow) {
            seekRelative(10)
            showHud(icon: "goforward.10", text: "快进 10 秒")
            return .handled
        }
        .onKeyPress(characters: CharacterSet(charactersIn: "jJ")) { _ in
            seekRelative(-10)
            showHud(icon: "gobackward.10", text: "快退 10 秒")
            return .handled
        }
        .onKeyPress(characters: CharacterSet(charactersIn: "kK")) { _ in
            togglePlayPause()
            return .handled
        }
        .onKeyPress(characters: CharacterSet(charactersIn: "lL")) { _ in
            seekRelative(10)
            showHud(icon: "goforward.10", text: "快进 10 秒")
            return .handled
        }
        .onKeyPress(characters: CharacterSet(charactersIn: "mM")) { _ in
            toggleMute()
            return .handled
        }
        .onKeyPress(characters: CharacterSet(charactersIn: "fF")) { _ in
            if isFullScreen {
                exitAdaptiveFullscreen()
            } else {
                enterAdaptiveFullscreen()
            }
            return .handled
        }
        .onKeyPress(.escape) {
            if isFullScreen {
                exitAdaptiveFullscreen()
            } else {
                dismissView()
            }
            return .handled
        }
        .enableSwipeBack(isEnabled: !isFullScreen, allowsContentSwipe: false) {
            dismissView()
        }
    }

    @ViewBuilder
    private func detailScrollContent(includeRecommendations: Bool, columnWidth: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: AuroraMetrics.spacingXL) {
            headerInfoSection
                .id("video-detail-top-anchor")
                .padding(.top, AuroraMetrics.spacingM)

            actionToolbarSection

            if !episodes.isEmpty || isLoadingEpisodes {
                episodesShelfSection
            }

            if let meta = detailMetadata, !meta.directors.isEmpty || !meta.actors.isEmpty {
                castCrewSection(metadata: meta)
            }

            synopsisSection

            if includeRecommendations && !relatedVideos.isEmpty {
                relatedVideosSection
            }

            Spacer().frame(height: 60)
        }
        .padding(.horizontal, AuroraMetrics.contentInset)
        .frame(width: max(0, columnWidth), alignment: .leading)
        .clipped()
    }

    private func landscapeRecommendationRail(width: CGFloat, safeTop: CGFloat) -> some View {
        let horizontalPadding: CGFloat = 14
        let thumbWidth = min(152, max(118, (width - horizontalPadding * 2 - 10) * 0.43))
        let thumbHeight = (thumbWidth * 9.0 / 16.0).rounded()
        return VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
            Text("更多推荐")
                .font(.title3.bold())
                .foregroundStyle(AuroraColor.textPrimary)

            ForEach(relatedVideos.prefix(20)) { related in
                Button {
                    switchToVideo(related)
                } label: {
                    HStack(alignment: .top, spacing: 10) {
                        AuroraCoverImage(
                            url: related.coverUrl,
                            title: related.title,
                            sourceBaseURL: related.coverBaseURL,
                            aspectRatio: 16.0 / 9.0,
                            contentMode: .fill,
                            cornerRadius: 10
                        )
                        .frame(width: thumbWidth, height: thumbHeight)
                        .clipped()

                        VStack(alignment: .leading, spacing: 6) {
                            Text(related.title)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(AuroraColor.textPrimary)
                                .lineLimit(3)
                                .multilineTextAlignment(.leading)

                            Text(related.sourceName)
                                .font(.caption)
                                .foregroundStyle(AuroraColor.textSecondary)

                            if !related.duration.isEmpty {
                                Text(related.duration)
                                    .font(.caption2)
                                    .foregroundStyle(AuroraColor.textTertiary)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .hoverEffect(.highlight)
                .frame(minHeight: thumbHeight)
            }
        }
        .padding(.horizontal, horizontalPadding)
        .padding(.top, max(safeTop + 8, 24))
        .padding(.bottom, 48)
    }

    // MARK: - 1. 顶部播放器视口（内嵌 / 全屏 / 横屏分栏单一常驻实例）

    @ViewBuilder
    private func topPlayerContainer(
        screenSize: CGSize,
        containerWidth: CGFloat,
        targetWidth: CGFloat,
        targetHeight: CGFloat,
        safeTop: CGFloat,
        isRotated: Bool
    ) -> some View {
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        let isPhysicallyLandscape = screenSize.width > screenSize.height
        let needsTopOffset = !isFullScreen && (!isPhysicallyLandscape || isPad)

        ZStack {
            Color.black

            VStack(spacing: 0) {
                // 非全屏竖屏嵌入态：顶部垫入 safeTop 高度的纯黑安全区隔离底衬，使视频物理起始于灵动岛/状态栏下方
                if needsTopOffset {
                    Color.black.frame(height: safeTop)
                }

                ZStack(alignment: .bottom) {
                    // 底层海报背景（加载、缓冲或失败时垫底，消除缓冲期间黑屏）
                    if player == nil || isBuffering {
                        AuroraCoverImage(
                            url: currentVideo.coverUrl,
                            title: currentVideo.title,
                            sourceBaseURL: currentVideo.coverBaseURL,
                            aspectRatio: nil,
                            contentMode: .fill,
                            cornerRadius: 0
                        )
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .clipped()
                        .overlay(Color.black.opacity(0.38))
                    }

                    // 原生 AVPlayer 渲染视口（单一持久化实例 + 零延迟长按 2.0x 快放识别）
                    if let activePlayer = player {
                        AuroraPlayerSurface(
                            player: activePlayer,
                            videoGravity: isFillMode ? .resizeAspectFill : .resizeAspect,
                            onLongPressBoostChanged: { boosting in
                                handleSpeedBoostChanged(boosting)
                            }
                        )
                        .opacity(isBuffering ? 0.01 : 1)
                        .modifier(
                            PlayerSurfaceGestureModifier(
                                isFullScreen: isFullScreen,
                                viewportWidth: targetWidth,
                                isPad: isPad,
                                onSingleTap: {
                                    withAnimation(.easeInOut(duration: 0.22)) {
                                        showControls.toggle()
                                        if !showControls {
                                            isShowingInPlayerEpisodes = false
                                        }
                                    }
                                    if showControls {
                                        scheduleHideControls()
                                    } else {
                                        hideControlsTask?.cancel()
                                    }
                                },
                                onDoubleTapAtX: { tapX in
                                    handleDoubleTap(at: tapX, in: targetWidth)
                                },
                                onVerticalSideDrag: { startX, deltaY in
                                    handleVerticalSideDrag(startX: startX, translationHeight: deltaY, width: targetWidth, isPad: isPad)
                                },
                                onVerticalSideDragEnd: {
                                    activeGestureMode = nil
                                },
                                onSwipeUpInline: {
                                    enterAdaptiveFullscreen()
                                },
                                onSwipeDownFullscreen: {
                                    exitAdaptiveFullscreen()
                                },
                                onPinchScale: { scale in
                                    handlePinchZoom(scale: scale)
                                },
                                onPinchEnd: {
                                    pinchBaselineFillMode = nil
                                }
                            )
                        )
                    }

                    // 解析失败提示层（始终提供返回与重试出口）
                    if let error = resolveError {
                        VStack(spacing: 12) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.system(size: 30))
                                .foregroundStyle(AuroraColor.warning)
                            Text("播放失败")
                                .font(.headline)
                                .foregroundStyle(.white)
                            Text(error)
                                .font(.caption)
                                .foregroundStyle(.white.opacity(0.8))
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 24)

                            GlassEffectContainer(spacing: 12) {
                                HStack(spacing: 12) {
                                    PlayerGlassPillButton(systemName: "chevron.backward", title: "返回") {
                                        dismissView()
                                    }

                                    PlayerGlassPillButton(
                                        systemName: "arrow.clockwise",
                                        title: "重试",
                                        tint: AuroraColor.brand
                                    ) {
                                        let stage = max(recoveryStage, 1)
                                        recoveryStage = stage
                                        Task {
                                            await startPlayback(
                                                for: currentVideo,
                                                forceFresh: true,
                                                alternateLineIndex: max(stage - 1, 0),
                                                useRelay: true
                                            )
                                        }
                                    }
                                }
                            }
                            .environment(\.colorScheme, .dark)
                        }
                        .padding()
                    } else if isResolving || isBuffering {
                        VStack(spacing: 10) {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                .scaleEffect(1.15)
                            Text(isResolving ? resolveStatus : "正在缓冲视频…")
                                .font(.caption.bold())
                                .foregroundStyle(.white)
                                .shadow(color: .black.opacity(0.5), radius: 3)
                        }
                        .padding(.horizontal, 18)
                        .padding(.vertical, 12)
                        .glassEffect(.clear, in: .capsule)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                        .allowsHitTesting(false)
                    }

                    // 内嵌态控制栏隐去时的底缘微光进度细线（零遮挡感知播放进度）
                    if !isFullScreen && !showControls && resolveError == nil && duration > 0 {
                        GeometryReader { barGeo in
                            let ratio = min(max(CGFloat(currentTime / duration), 0), 1)
                            ZStack(alignment: .leading) {
                                Rectangle()
                                    .fill(Color.white.opacity(0.16))
                                Rectangle()
                                    .fill(Color.white.opacity(0.86))
                                    .frame(width: barGeo.size.width * ratio)
                            }
                        }
                        .frame(height: 2)
                        .allowsHitTesting(false)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }

            // 播放器控制覆盖层（Apple TV 拓扑 + iOS 27 `Glass.clear` 原生高透液态玻璃）
            if (showControls || resolveError != nil) && resolveError == nil {
                playerControlsOverlay(
                    screenSize: screenSize,
                    playerWidth: targetWidth,
                    safeTop: safeTop,
                    isRotated: isRotated
                )
                .transition(.opacity)
            } else if resolveError != nil {
                // 解析失败时仍保留左上角返回热区
                VStack {
                    HStack {
                        PlayerGlassIconButton(
                            systemName: "chevron.backward",
                            accessibilityTitle: "返回",
                            visualDiameter: 40,
                            symbolSize: 16,
                            symbolWeight: .bold
                        ) {
                            dismissView()
                        }
                        Spacer()
                    }
                    .padding(.horizontal, 14)
                    .padding(.top, needsTopOffset ? safeTop + 8 : 16)
                    Spacer()
                }
                .environment(\.colorScheme, .dark)
            }

            // 长按 2.0x 倍速快放顶部悬浮水滴胶囊（控制栏隐藏时同样可见）
            if isSpeedBoosting {
                VStack {
                    HStack(spacing: 6) {
                        Image(systemName: "forward.fill")
                            .font(.system(size: 12, weight: .bold))
                        Text("2.0x 倍速快放")
                            .font(.system(size: 13, weight: .bold))
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .glassEffect(.clear, in: .capsule)
                    .environment(\.colorScheme, .dark)
                    .padding(.top, isFullScreen ? max(18, safeTop) : safeTop + 10)
                    Spacer()
                }
                .transition(.scale(scale: 0.9).combined(with: .opacity))
                .allowsHitTesting(false)
                .zIndex(45)
            }

            // Apple TV 悬浮高透液态玻璃动态 HUD
            if let msg = hudMessage {
                PlayerLiquidGlassHUD(icon: hudIcon, message: msg, progress: hudProgress)
                    .environment(\.colorScheme, .dark)
                    .transition(.scale(scale: 0.88).combined(with: .opacity))
                    .zIndex(50)
            }
        }
        .frame(width: targetWidth, height: targetHeight)
        .rotationEffect(isRotated ? .degrees(90) : .zero)
        .position(
            x: (isFullScreen ? screenSize.width : containerWidth) / 2,
            y: (isFullScreen ? screenSize.height : targetHeight) / 2
        )
        .frame(
            width: isFullScreen ? screenSize.width : containerWidth,
            height: isFullScreen ? screenSize.height : targetHeight,
            alignment: .center
        )
        .clipped()
        .ignoresSafeArea()
        .animation(reduceMotion ? nil : .spring(response: 0.34, dampingFraction: 0.86), value: isFullScreen)
        .animation(reduceMotion ? nil : .spring(response: 0.34, dampingFraction: 0.86), value: isLandscape)
        .animation(reduceMotion ? nil : .spring(response: 0.32, dampingFraction: 0.88), value: effectiveAspectRatio)
    }

    // MARK: - 播放器控制层（撤除浓黑遮罩，释放 Glass.clear 动态视频折射）

    @ViewBuilder
    private func playerControlsOverlay(
        screenSize: CGSize,
        playerWidth: CGFloat,
        safeTop: CGFloat,
        isRotated: Bool
    ) -> some View {
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        let isPhysicallyLandscape = screenSize.width > screenSize.height
        let topBarPadding: CGFloat = {
            if isRotated { return 16 }
            if isPad { return isFullScreen ? max(20, safeTop) : max(safeTop, 24) + 6 }
            if isFullScreen && isPhysicallyLandscape { return max(14, measuredSafeInsets.top + 6) }
            return safeTop + 6
        }()
        let horizontalLeadingInset: CGFloat = {
            if isPad { return 24 }
            if isFullScreen && isPhysicallyLandscape { return max(20, measuredSafeInsets.leading + 6) }
            if isRotated { return max(18, safeTop) }
            return 14
        }()
        let horizontalTrailingInset: CGFloat = {
            if isPad { return 24 }
            if isFullScreen && isPhysicallyLandscape { return max(20, measuredSafeInsets.trailing + 6) }
            if isRotated { return max(18, resolvedSafeBottom) }
            return 14
        }()
        let bottomInset: CGFloat = {
            if isFullScreen {
                return isPhysicallyLandscape ? max(14, resolvedSafeBottom) : max(16, resolvedSafeBottom + 4)
            }
            return isPad ? 12 : 8
        }()
        let bottomVignetteHeight: CGFloat = isFullScreen ? (isPad ? 180 : 156) : 68

        ZStack {
            // 仅在极底部文字与进度轨背后保留轻柔光学暗角（0.0 -> 0.32），顶部与中区 0% 黑幕，让 Glass.clear 100% 折射真实视频色彩！
            VStack(spacing: 0) {
                Spacer()
                LinearGradient(
                    colors: [Color.clear, Color.black.opacity(0.14), Color.black.opacity(0.34)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: bottomVignetteHeight)
            }
            .allowsHitTesting(false)
            .ignoresSafeArea()

            VStack(spacing: 0) {
                // 1. 顶部悬浮液态玻璃栏（极简克制：左返回/收起+静音，右 PiP+AirPlay，绝不堆叠拥挤）
                topControlBar

                Spacer(minLength: 4)

                // 2. 中央无染色大水滴三联传输控制器（iOS 27 Glass.clear 透镜；缓冲/解析中自动让位给缓冲胶囊，杜绝重叠）
                if player != nil && !isResolving && !isBuffering {
                    centerControlGroup
                }

                Spacer(minLength: 4)

                // 3. 底部控制区：内嵌极简条 vs 全屏 Apple TV 三层影院控制台
                if isFullScreen {
                    fullscreenCinemaBottomDeck(availableWidth: max(0, playerWidth - horizontalLeadingInset - horizontalTrailingInset))
                } else {
                    inlineBottomBar
                }
            }
            .environment(\.colorScheme, .dark)
            .padding(.leading, horizontalLeadingInset)
            .padding(.trailing, horizontalTrailingInset)
            .padding(.top, topBarPadding)
            .padding(.bottom, bottomInset)
        }
    }

    // 顶部控制栏：内嵌与全屏职责清晰、零拥挤
    private var topControlBar: some View {
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        return HStack(spacing: 12) {
            GlassEffectContainer(spacing: 10) {
                HStack(spacing: 10) {
                    if isFullScreen {
                        // 全屏左上角：收起回二级详情页 + 静音快捷开关（对齐 Apple TV / AVKit）
                        PlayerGlassIconButton(
                            systemName: "chevron.down",
                            accessibilityTitle: "退出全屏",
                            visualDiameter: isPad ? 44 : 40,
                            symbolSize: isPad ? 17 : 15,
                            symbolWeight: .bold
                        ) {
                            let impact = UIImpactFeedbackGenerator(style: .medium)
                            impact.impactOccurred()
                            exitAdaptiveFullscreen()
                            scheduleHideControls()
                        }
                        .glassEffectID("nav-primary", in: glassMorphNamespace)

                        PlayerGlassIconButton(
                            systemName: isMuted ? "speaker.slash.fill" : "speaker.wave.2.fill",
                            accessibilityTitle: isMuted ? "取消静音" : "静音",
                            visualDiameter: isPad ? 44 : 40,
                            symbolSize: isPad ? 16 : 14,
                            symbolWeight: .semibold
                        ) {
                            toggleMute()
                            scheduleHideControls()
                        }

                        // iPhone 全屏顶栏附带横竖屏自由切换圆钮（释放窄屏底栏空间，确保底栏全按钮零裁切）
                        if !isPad {
                            PlayerGlassIconButton(
                                systemName: "rotate.right",
                                accessibilityTitle: isLandscape ? "切换竖屏显示" : "切换横屏显示",
                                visualDiameter: 40,
                                symbolSize: 14,
                                symbolWeight: .semibold
                            ) {
                                toggleManualOrientation()
                            }
                        }
                    } else {
                        // 二级页内嵌左上角：返回上一级
                        if isPad {
                            PlayerGlassPillButton(systemName: "chevron.backward", title: "返回") {
                                let impact = UIImpactFeedbackGenerator(style: .medium)
                                impact.impactOccurred()
                                dismissView()
                            }
                            .glassEffectID("nav-primary", in: glassMorphNamespace)
                        } else {
                            PlayerGlassIconButton(
                                systemName: "chevron.backward",
                                accessibilityTitle: "返回",
                                visualDiameter: 38,
                                symbolSize: 15,
                                symbolWeight: .bold
                            ) {
                                let impact = UIImpactFeedbackGenerator(style: .medium)
                                impact.impactOccurred()
                                dismissView()
                            }
                            .glassEffectID("nav-primary", in: glassMorphNamespace)
                        }
                    }
                }
            }

            Spacer(minLength: 8)

            // 右上角：仅保留系统级投射双键（PiP 画中画 + AirPlay），彻底告别 6 按钮拥挤
            GlassEffectContainer(spacing: 8) {
                HStack(spacing: 8) {
                    if AVPictureInPictureController.isPictureInPictureSupported() {
                        PlayerGlassIconButton(
                            systemName: PictureInPictureManager.shared.isActive ? "pip.exit" : "pip.enter",
                            accessibilityTitle: "画中画",
                            visualDiameter: isPad ? 42 : 38,
                            symbolSize: isPad ? 15 : 13,
                            symbolWeight: .semibold
                        ) {
                            let impact = UIImpactFeedbackGenerator(style: .medium)
                            impact.impactOccurred()
                            PictureInPictureManager.shared.toggle()
                            scheduleHideControls()
                        }
                    }

                    AirPlayRoutePicker(tint: .white)
                        .frame(width: isPad ? 42 : 38, height: isPad ? 42 : 38)
                        .glassEffect(.clear.interactive(), in: .circle)
                        .frame(minWidth: AuroraMetrics.minTouchTarget, minHeight: AuroraMetrics.minTouchTarget)
                }
            }
        }
    }

    // 中央控制区：iOS 27 原生无染色大号水滴透镜三联组（移除旧版实心粉红圆饼）
    private var centerControlGroup: some View {
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        let sideSize: CGFloat = isPad ? 56 : (isFullScreen ? 48 : 40)
        let centerSize: CGFloat = isPad ? 76 : (isFullScreen ? 68 : 54)
        let groupSpacing: CGFloat = isPad ? 48 : (isFullScreen ? 38 : 26)

        return GlassEffectContainer(spacing: groupSpacing) {
            HStack(spacing: groupSpacing) {
                PlayerGlassIconButton(
                    systemName: "gobackward.10",
                    accessibilityTitle: "快退 10 秒",
                    visualDiameter: sideSize,
                    symbolSize: isPad ? 23 : (isFullScreen ? 20 : 17),
                    symbolWeight: .semibold
                ) {
                    seekRelative(-10)
                }

                PlayerGlassIconButton(
                    systemName: isPlaying ? "pause.fill" : "play.fill",
                    accessibilityTitle: isPlaying ? "暂停" : "播放",
                    visualDiameter: centerSize,
                    symbolSize: isPad ? 32 : (isFullScreen ? 28 : 22),
                    symbolWeight: .bold,
                    symbolLeadingOffset: isPlaying ? 0 : 1.5
                ) {
                    togglePlayPause()
                }

                PlayerGlassIconButton(
                    systemName: "goforward.10",
                    accessibilityTitle: "快进 10 秒",
                    visualDiameter: sideSize,
                    symbolSize: isPad ? 23 : (isFullScreen ? 20 : 17),
                    symbolWeight: .semibold
                ) {
                    seekRelative(10)
                }
            }
        }
    }

    // 二级页内嵌底部栏：时间 + 流体胶囊进度轨 + 右下角黄金「全屏」按钮
    private var inlineBottomBar: some View {
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        return HStack(spacing: isPad ? 12 : 8) {
            Button {
                showRemainingTime.toggle()
            } label: {
                HStack(spacing: 3) {
                    Text(formatTime(currentTime))
                        .foregroundStyle(.white)
                    Text("/")
                        .foregroundStyle(.white.opacity(0.55))
                    Text(showRemainingTime ? formatRemainingTime(currentTime, duration: duration) : formatTime(duration))
                        .foregroundStyle(.white.opacity(0.82))
                }
                .font(.system(size: isPad ? 12 : 11, weight: .semibold, design: .monospaced))
                .shadow(color: .black.opacity(0.5), radius: 2, y: 1)
                .fixedSize(horizontal: true, vertical: false)
            }
            .buttonStyle(.plain)
            .frame(minHeight: 36)
            .accessibilityLabel("切换剩余时间与总时长")

            PlayerScrubberBar(
                currentTime: $currentTime,
                bufferedTime: bufferedTime,
                duration: duration,
                onSeek: { targetTime in
                    seekTo(time: targetTime)
                    scheduleHideControls()
                },
                onEditingChanged: { editing in
                    isScrubbing = editing
                    if editing {
                        hideControlsTask?.cancel()
                    } else {
                        scheduleHideControls()
                    }
                }
            )
            .frame(height: isPad ? 38 : 34)

            // 右下角固定全屏展开按钮（点击后按视频真实比例智能进入横屏全屏或竖屏全屏！）
            PlayerGlassIconButton(
                systemName: "arrow.up.left.and.arrow.down.right",
                accessibilityTitle: "全屏播放",
                visualDiameter: isPad ? 40 : 36,
                symbolSize: isPad ? 15 : 13,
                symbolWeight: .bold
            ) {
                let impact = UIImpactFeedbackGenerator(style: .medium)
                impact.impactOccurred()
                enterAdaptiveFullscreen()
                scheduleHideControls()
            }
            .glassEffectID("fullscreen-anchor", in: glassMorphNamespace)
        }
    }

    // 全屏 Apple TV 三层影院底部控制台（片名层 + 流体膨胀胶囊进度轨 + 响应式功能胶囊栏）
    @ViewBuilder
    private func fullscreenCinemaBottomDeck(availableWidth: CGFloat) -> some View {
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        // 窄屏竖向全屏（如 393pt 播放竖屏短剧）自动收敛为紧凑图标圆钮，彻底杜绝文字截断与横向溢出
        let useCompactIcons = availableWidth < 540

        VStack(alignment: .leading, spacing: isPad ? 10 : 6) {
            if isShowingInPlayerEpisodes && episodes.count > 1 {
                inPlayerEpisodesDrawer
            }

            // 第一层：Apple TV 左下角片名与剧集上下文（完整舒展，绝不被顶栏挤压）
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(currentVideo.sourceName)
                        .font(.caption2.bold())
                        .foregroundStyle(.white.opacity(0.9))
                        .padding(.horizontal, 7)
                        .padding(.vertical, 2)
                        .glassEffect(.clear, in: .capsule)

                    if let ep = currentEpisode {
                        Text(ep.label)
                            .font(.caption2.bold())
                            .foregroundStyle(.white)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 2)
                            .glassEffect(.clear.tint(AuroraColor.brand), in: .capsule)
                    }
                }

                Text(currentVideo.title)
                    .font(isPad ? .title3.bold() : .headline.bold())
                    .foregroundStyle(.white)
                    .shadow(color: .black.opacity(0.55), radius: 4, y: 1)
                    .lineLimit(1)
            }

            // 第二层：已播时间 + 6pt->12pt 流体膨胀液态胶囊进度条 + 倒计时剩余时间
            HStack(spacing: isPad ? 14 : 10) {
                Text(formatTime(currentTime))
                    .font(.system(size: isPad ? 13 : 12, weight: .semibold, design: .monospaced))
                    .foregroundStyle(.white)
                    .shadow(color: .black.opacity(0.5), radius: 2, y: 1)
                    .fixedSize(horizontal: true, vertical: false)

                PlayerScrubberBar(
                    currentTime: $currentTime,
                    bufferedTime: bufferedTime,
                    duration: duration,
                    onSeek: { targetTime in
                        seekTo(time: targetTime)
                        scheduleHideControls()
                    },
                    onEditingChanged: { editing in
                        isScrubbing = editing
                        if editing {
                            hideControlsTask?.cancel()
                        } else {
                            scheduleHideControls()
                        }
                    }
                )

                Button {
                    showRemainingTime.toggle()
                } label: {
                    Text(showRemainingTime ? formatRemainingTime(currentTime, duration: duration) : formatTime(duration))
                        .font(.system(size: isPad ? 13 : 12, weight: .semibold, design: .monospaced))
                        .foregroundStyle(.white.opacity(0.88))
                        .shadow(color: .black.opacity(0.5), radius: 2, y: 1)
                        .frame(minWidth: isPad ? 52 : 46, alignment: .trailing)
                        .fixedSize(horizontal: true, vertical: false)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(showRemainingTime ? "切换至总时长" : "切换至倒计时")
            }

            // 第三层：Apple TV 底部功能胶囊栏（右侧固定退出全屏锚点，左侧/中部支持窄屏自适应零溢出）
            HStack(spacing: 8) {
                ScrollView(.horizontal, showsIndicators: false) {
                    GlassEffectContainer(spacing: 8) {
                        HStack(spacing: 8) {
                            PlayerGlassPillButton(
                                systemName: "info.circle",
                                title: "详情",
                                isCompactIconOnly: useCompactIcons
                            ) {
                                let impact = UIImpactFeedbackGenerator(style: .light)
                                impact.impactOccurred()
                                isShowingInSight = true
                                scheduleHideControls()
                            }

                            if episodes.count > 1 {
                                PlayerGlassPillButton(
                                    systemName: "square.stack.3d.down.right",
                                    title: "选集",
                                    isCompactIconOnly: useCompactIcons,
                                    tint: isShowingInPlayerEpisodes ? AuroraColor.brand : nil
                                ) {
                                    let impact = UIImpactFeedbackGenerator(style: .medium)
                                    impact.impactOccurred()
                                    withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                                        isShowingInPlayerEpisodes.toggle()
                                    }
                                    scheduleHideControls()
                                }
                            }

                            if let currentEp = currentEpisode,
                               let nextEp = episodes.first(where: { $0.number == currentEp.number + 1 }) {
                                PlayerGlassPillButton(
                                    systemName: "forward.end.fill",
                                    title: "下一集",
                                    isCompactIconOnly: useCompactIcons
                                ) {
                                    let impact = UIImpactFeedbackGenerator(style: .medium)
                                    impact.impactOccurred()
                                    switchEpisode(nextEp)
                                }
                            }

                            if canSelectMediaTracks, let item = player?.currentItem {
                                PlayerMediaTrackMenu(
                                    playerItem: item,
                                    subtitleGroup: subtitleSelectionGroup,
                                    audioGroup: audioSelectionGroup,
                                    showLabelText: !useCompactIcons && isPad
                                )
                            }

                            // 倍速原生菜单（Glass.clear 胶囊）
                            Menu {
                                ForEach([0.5, 0.75, 1.0, 1.25, 1.5, 2.0], id: \.self) { speed in
                                    Button {
                                        let impact = UIImpactFeedbackGenerator(style: .light)
                                        impact.impactOccurred()
                                        playbackRate = Float(speed)
                                        if isPlaying && !isSpeedBoosting {
                                            player?.rate = Float(speed)
                                        }
                                        showHud(
                                            icon: "gauge.with.dots.needle.bottom.50percent",
                                            text: "\(speed == 1.0 ? "1.0" : String(format: "%.2g", speed))x 倍速"
                                        )
                                        scheduleHideControls()
                                    } label: {
                                        HStack {
                                            Text(speed == 1.0 ? "正常 (1.0x)" : "\(String(format: "%.2g", speed))x")
                                            if playbackRate == Float(speed) {
                                                Image(systemName: "checkmark")
                                            }
                                        }
                                    }
                                }
                            } label: {
                                Text(playbackRate == 1.0 ? "1.0x" : "\(String(format: "%.2g", playbackRate))x")
                                    .font(.system(size: 13, weight: .bold, design: .rounded))
                                    .foregroundStyle(.white)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 5)
                                    .fixedSize(horizontal: true, vertical: false)
                            }
                            .playerClearGlassCapsuleButton(tint: playbackRate != 1.0 ? AuroraColor.brand : nil)
                            .frame(minHeight: AuroraMetrics.minTouchTarget)
                            .accessibilityLabel("播放倍速 \(playbackRate)x")

                            // 专属画幅切换键（使用外扩/内收画幅框图标，彻底区分全屏对角箭头图标！）
                            PlayerGlassPillButton(
                                systemName: isFillMode ? "rectangle.arrowtriangle.2.inward" : "rectangle.arrowtriangle.2.outward",
                                title: isFillMode ? "原比" : "满铺",
                                isCompactIconOnly: useCompactIcons,
                                tint: isFillMode ? AuroraColor.brand : nil
                            ) {
                                toggleFillMode()
                            }
                        }
                    }
                }
                .scrollClipDisabled()

                Spacer(minLength: 4)

                // 右下角固定「退出全屏」锚点（与内嵌态右下角「进入全屏」肌肉记忆 1:1 对齐）
                PlayerGlassIconButton(
                    systemName: "arrow.down.right.and.arrow.up.left",
                    accessibilityTitle: "退出全屏",
                    visualDiameter: isPad ? 42 : 38,
                    symbolSize: isPad ? 15 : 13,
                    symbolWeight: .bold
                ) {
                    let impact = UIImpactFeedbackGenerator(style: .medium)
                    impact.impactOccurred()
                    exitAdaptiveFullscreen()
                    scheduleHideControls()
                }
                .glassEffectID("fullscreen-anchor", in: glassMorphNamespace)
            }
        }
    }

    // 全屏快速选集横向浮动抽屉（升级为 iOS 27 Glass.clear 透光浮层）
    @ViewBuilder
    private var inPlayerEpisodesDrawer: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("快速选集")
                    .font(.subheadline.bold())
                    .foregroundStyle(.white)
                Spacer()
                Button {
                    withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                        isShowingInPlayerEpisodes = false
                    }
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 18))
                        .foregroundStyle(.white.opacity(0.8))
                        .frame(minWidth: AuroraMetrics.minTouchTarget, minHeight: AuroraMetrics.minTouchTarget)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("关闭选集")
            }
            .padding(.horizontal, 14)

            ScrollView(.horizontal, showsIndicators: false) {
                GlassEffectContainer(spacing: 10) {
                    LazyHStack(spacing: 10) {
                        ForEach(episodes) { ep in
                            let isSelected = (currentEpisode?.number == ep.number)
                            PlayerGlassPillButton(
                                systemName: isSelected ? "play.circle.fill" : nil,
                                title: ep.label,
                                tint: isSelected ? AuroraColor.brand : nil
                            ) {
                                switchEpisode(ep)
                                withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                                    isShowingInPlayerEpisodes = false
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 14)
                }
            }
        }
        .padding(.vertical, 10)
        .glassEffect(.clear, in: .rect(cornerRadius: 18))
        .padding(.bottom, 4)
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }

    // MARK: - 2. 视频主标题与信息

    private var headerInfoSection: some View {
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        return VStack(alignment: .leading, spacing: isPad ? 8 : 6) {
            Text(currentVideo.title)
                .font(isPad ? .title2.bold() : .title3.bold())
                .foregroundStyle(AuroraColor.textPrimary)
                .lineLimit(2)

            HStack(spacing: 8) {
                Text(currentVideo.sourceName)
                    .font(.caption.bold())
                    .foregroundStyle(AuroraColor.brandLabel)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(AuroraColor.brand.opacity(0.16), in: Capsule())

                if !currentVideo.duration.isEmpty {
                    Text(currentVideo.duration)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(AuroraColor.textSecondary)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: 4))
                }

                if !currentVideo.rating.isEmpty {
                    HStack(spacing: 3) {
                        Image(systemName: "star.fill")
                            .font(.system(size: 10))
                            .foregroundStyle(.yellow)
                        Text(currentVideo.rating)
                            .font(.caption.bold())
                            .foregroundStyle(.primary)
                    }
                    .padding(.horizontal, 7)
                    .padding(.vertical, 2)
                    .background(.regularMaterial, in: Capsule())
                }

                if let ep = currentEpisode {
                    Text(ep.label)
                        .font(.caption.bold())
                        .foregroundStyle(AuroraColor.brand)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 2)
                        .background(AuroraColor.brand.opacity(0.12), in: Capsule())
                }
            }

            if let meta = detailMetadata {
                let pills = [meta.year, meta.area].filter { !$0.isEmpty } + meta.tags
                if !pills.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(Array(pills.prefix(10).enumerated()), id: \.offset) { _, pill in
                                Text(pill)
                                    .font(.caption2.weight(.medium))
                                    .foregroundStyle(AuroraColor.textSecondary)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 3)
                                    .background(AuroraColor.surfaceCard, in: Capsule())
                                    .overlay(Capsule().stroke(AuroraColor.hairline, lineWidth: 0.75))
                            }
                        }
                    }
                    .contentMargins(.horizontal, AuroraMetrics.contentInset, for: .scrollContent)
                    .padding(.horizontal, -AuroraMetrics.contentInset)
                }
            }
        }
    }

    // MARK: - 3. 二级详情页单行正交胶囊工具栏（统一 iPhone / Duo / iPad，根除双播放键冲突）

    @ViewBuilder
    private var actionToolbarSection: some View {
        let isBookmarked = watchlist.contains(currentVideo)
        let downloadStatus = downloadManager.status(for: currentVideo, episode: currentEpisode)
        let isDownloaded = downloadManager.isDownloaded(video: currentVideo, episode: currentEpisode)

        ScrollView(.horizontal, showsIndicators: false) {
            GlassEffectContainer(spacing: 10) {
                HStack(spacing: 10) {
                    // 1. Apple TV 标志性高对比主胶囊：一键最佳方向全屏沉浸观影（若暂停则同步恢复播放）
                    Button {
                        let impact = UIImpactFeedbackGenerator(style: .medium)
                        impact.impactOccurred()
                        if let p = player, !isPlaying {
                            p.play()
                            if playbackRate != 1.0 {
                                p.rate = playbackRate
                            }
                            isPlaying = true
                            UIApplication.shared.isIdleTimerDisabled = true
                            updateNowPlayingMetadata()
                        }
                        enterAdaptiveFullscreen()
                        scheduleHideControls()
                    } label: {
                        HStack(spacing: 7) {
                            Image(systemName: "arrow.up.left.and.arrow.down.right")
                                .font(.system(size: 14, weight: .bold))
                            Text("全屏观影")
                                .font(.system(size: 15, weight: .bold))
                        }
                        .padding(.horizontal, 18)
                        .frame(minHeight: AuroraMetrics.minTouchTarget)
                        .background(AuroraColor.textPrimary, in: Capsule())
                        .foregroundStyle(AuroraColor.canvas)
                        .fixedSize(horizontal: true, vertical: false)
                    }
                    .buttonStyle(AppleSpringCardButtonStyle())
                    .hoverEffect(.highlight)
                    .accessibilityLabel("全屏观影")

                    // 2. 片单收藏胶囊
                    let bookmarkPill = Button {
                        let impact = UIImpactFeedbackGenerator(style: .medium)
                        impact.impactOccurred()
                        try? watchlist.setBookmarked(!isBookmarked, for: currentVideo)
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: isBookmarked ? "bookmark.fill" : "plus")
                                .font(.system(size: 14, weight: .bold))
                            Text(isBookmarked ? "已在片单" : "加入片单")
                                .font(.system(size: 14, weight: .semibold))
                        }
                        .padding(.horizontal, 10)
                        .frame(minHeight: 36)
                        .fixedSize(horizontal: true, vertical: false)
                    }
                    .buttonBorderShape(.capsule)
                    .frame(minHeight: AuroraMetrics.minTouchTarget)

                    if isBookmarked {
                        bookmarkPill
                            .buttonStyle(.glassProminent)
                            .tint(AuroraColor.brand)
                    } else {
                        bookmarkPill
                            .buttonStyle(.glass)
                    }

                    // 3. 离线缓存胶囊
                    Button {
                        let impact = UIImpactFeedbackGenerator(style: .medium)
                        impact.impactOccurred()
                        if !isDownloaded {
                            downloadManager.startDownload(video: currentVideo, episode: currentEpisode)
                            showToast(text: "已加入缓存队列")
                        } else {
                            showToast(text: "该视频已完成离线缓存")
                        }
                    } label: {
                        HStack(spacing: 6) {
                            if isDownloaded {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(.green)
                                Text("已缓存")
                            } else if let status = downloadStatus, status.isDownloading {
                                ProgressView(value: status.progressValue)
                                    .frame(width: 16, height: 16)
                                    .tint(AuroraColor.brand)
                                Text("\(Int(status.progressValue * 100))%")
                            } else {
                                Image(systemName: "arrow.down.circle")
                                Text("离线缓存")
                            }
                        }
                        .font(.system(size: 14, weight: .semibold))
                        .padding(.horizontal, 10)
                        .frame(minHeight: 36)
                        .fixedSize(horizontal: true, vertical: false)
                    }
                    .buttonStyle(.glass)
                    .buttonBorderShape(.capsule)
                    .frame(minHeight: AuroraMetrics.minTouchTarget)

                    // 4. 选集胶囊（多集时可见）
                    if episodes.count > 1 {
                        Button {
                            isShowingEpisodePicker = true
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "square.stack.3d.down.right")
                                    .font(.system(size: 14, weight: .bold))
                                Text("选集 (\(episodes.count))")
                                    .font(.system(size: 14, weight: .semibold))
                            }
                            .padding(.horizontal, 10)
                            .frame(minHeight: 36)
                            .fixedSize(horizontal: true, vertical: false)
                        }
                        .buttonStyle(.glass)
                        .buttonBorderShape(.capsule)
                        .frame(minHeight: AuroraMetrics.minTouchTarget)
                    }

                    // 5. 原生分享胶囊
                    if let shareURL = URL(string: currentVideo.playbackUrl), !currentVideo.playbackUrl.isEmpty {
                        ShareLink(item: shareURL) {
                            HStack(spacing: 6) {
                                Image(systemName: "square.and.arrow.up")
                                    .font(.system(size: 14, weight: .bold))
                                Text("分享")
                                    .font(.system(size: 14, weight: .semibold))
                            }
                            .padding(.horizontal, 10)
                            .frame(minHeight: 36)
                            .fixedSize(horizontal: true, vertical: false)
                        }
                        .buttonStyle(.glass)
                        .buttonBorderShape(.capsule)
                        .frame(minHeight: AuroraMetrics.minTouchTarget)
                    }
                }
                .padding(.vertical, 2)
            }
        }
        .contentMargins(.horizontal, AuroraMetrics.contentInset, for: .scrollContent)
        .padding(.horizontal, -AuroraMetrics.contentInset)
    }

    // MARK: - 4. 选集货架（iPad 端呈现 Apple TV 16:9 横版剧照大卡片货架）

    private var episodesShelfSection: some View {
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        return VStack(alignment: .leading, spacing: isPad ? 14 : AuroraMetrics.spacingM) {
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "square.stack.3d.down.right.fill")
                        .font(.subheadline)
                        .foregroundStyle(AuroraColor.brand)
                    Text("选集播放")
                        .font(isPad ? .title3.bold() : .headline)
                        .foregroundStyle(AuroraColor.textPrimary)
                }
                Spacer()
                if episodes.count > 1 {
                    Button {
                        isShowingEpisodePicker = true
                    } label: {
                        HStack(spacing: 4) {
                            Text("全部 \(episodes.count) 集")
                                .font(.caption.bold())
                            Image(systemName: "chevron.right")
                                .font(.caption2.bold())
                        }
                        .foregroundStyle(AuroraColor.brand)
                    }
                }
            }

            if isLoadingEpisodes {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: AuroraMetrics.spacingM) {
                        ForEach(0..<4, id: \.self) { _ in
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(AuroraColor.surfaceCard.opacity(0.6))
                                .frame(width: isPad ? 250 : 110, height: isPad ? 140 : 56)
                        }
                    }
                }
            } else if isPad {
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(spacing: 16) {
                        ForEach(episodes) { ep in
                            let isSelected = (currentEpisode?.number == ep.number)
                            Button {
                                switchEpisode(ep)
                            } label: {
                                VStack(alignment: .leading, spacing: 8) {
                                    ZStack(alignment: .bottomLeading) {
                                        AuroraCoverImage(
                                            url: currentVideo.coverUrl,
                                            title: "\(currentVideo.title) \(ep.label)",
                                            sourceBaseURL: currentVideo.coverBaseURL,
                                            aspectRatio: 16.0 / 9.0,
                                            contentMode: .fill,
                                            cornerRadius: 12
                                        )
                                        .frame(width: 250, height: 140)
                                        .clipped()

                                        LinearGradient(
                                            colors: [Color.clear, Color.black.opacity(0.65)],
                                            startPoint: .center,
                                            endPoint: .bottom
                                        )

                                        VStack {
                                            HStack {
                                                Text(ep.label)
                                                    .font(.caption.bold())
                                                    .foregroundStyle(.white)
                                                    .padding(.horizontal, 8)
                                                    .padding(.vertical, 3)
                                                    .background(Color.black.opacity(0.7), in: Capsule())
                                                Spacer()
                                            }
                                            Spacer()
                                        }
                                        .padding(8)

                                        if isSelected {
                                            ZStack {
                                                Color.black.opacity(0.35)
                                                Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                                                    .font(.system(size: 18, weight: .bold))
                                                    .foregroundStyle(.white)
                                                    .frame(width: 44, height: 44)
                                                    .glassEffect(.clear, in: .circle)

                                                if duration > 0 {
                                                    let progress = min(max(currentTime / duration, 0.04), 1.0)
                                                    VStack {
                                                        Spacer()
                                                        ZStack(alignment: .leading) {
                                                            Rectangle()
                                                                .fill(Color.white.opacity(0.28))
                                                                .frame(height: 3.5)
                                                            Rectangle()
                                                                .fill(Color.white)
                                                                .frame(width: 250 * CGFloat(progress), height: 3.5)
                                                        }
                                                    }
                                                }
                                            }
                                            .environment(\.colorScheme, .dark)
                                        }
                                    }
                                    .frame(width: 250, height: 140)
                                    .cornerRadius(12)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                                            .stroke(isSelected ? Color.white : AuroraColor.hairline, lineWidth: isSelected ? 2 : 1)
                                    )
                                    .shadow(color: isSelected ? Color.white.opacity(0.2) : Color.black.opacity(0.3), radius: isSelected ? 10 : 4, y: 3)

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(ep.label)
                                            .font(.system(size: 14, weight: isSelected ? .bold : .medium))
                                            .foregroundStyle(isSelected ? AuroraColor.brand : AuroraColor.textPrimary)
                                        Text(currentVideo.sourceName)
                                            .font(.system(size: 12))
                                            .foregroundStyle(AuroraColor.textTertiary)
                                    }
                                    .frame(width: 250, alignment: .leading)
                                }
                            }
                            .buttonStyle(AppleSpringCardButtonStyle())
                        }
                    }
                    .padding(.vertical, 4)
                }
                .contentMargins(.horizontal, AuroraMetrics.contentInset, for: .scrollContent)
                .padding(.horizontal, -AuroraMetrics.contentInset)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(spacing: 10) {
                        ForEach(episodes) { ep in
                            let isSelected = (currentEpisode?.number == ep.number)
                            Button {
                                switchEpisode(ep)
                            } label: {
                                VStack(alignment: .leading, spacing: 4) {
                                    HStack {
                                        Text(ep.label)
                                            .font(.system(size: 13, weight: isSelected ? .bold : .medium))
                                            .foregroundStyle(isSelected ? .white : AuroraColor.textPrimary)
                                            .lineLimit(1)
                                        Spacer()
                                        if isSelected {
                                            Image(systemName: "play.circle.fill")
                                                .font(.system(size: 12))
                                                .foregroundStyle(.white)
                                        }
                                    }
                                    Text(currentVideo.sourceName)
                                        .font(.system(size: 10))
                                        .foregroundStyle(isSelected ? .white.opacity(0.8) : AuroraColor.textTertiary)
                                        .lineLimit(1)
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .frame(width: 116, height: 54)
                                .background(
                                    isSelected ? AuroraColor.brand : AuroraColor.surfaceCard,
                                    in: RoundedRectangle(cornerRadius: 10, style: .continuous)
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                                        .stroke(isSelected ? Color.white.opacity(0.6) : AuroraColor.hairline, lineWidth: 1)
                                )
                            }
                            .buttonStyle(AppleSpringCardButtonStyle())
                        }
                    }
                }
                .contentMargins(.horizontal, AuroraMetrics.contentInset, for: .scrollContent)
                .padding(.horizontal, -AuroraMetrics.contentInset)
            }
        }
    }

    // MARK: - 5. 演职员阵容与基本介绍

    @ViewBuilder
    private func castCrewSection(metadata: VideoDetailMetadata) -> some View {
        let crewItems: [(name: String, role: String, icon: String)] =
            metadata.directors.map { ($0, "导演", "megaphone.fill") } +
            metadata.actors.map { ($0, "主演", "person.crop.circle.fill") }

        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("演员表")
                    .font(.headline)
                    .foregroundStyle(AuroraColor.textPrimary)
                Spacer()
                if onOpenSearchKeyword != nil {
                    Text("轻点检索相关作品")
                        .font(.caption2)
                        .foregroundStyle(AuroraColor.textTertiary)
                }
            }

            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 10) {
                    ForEach(Array(crewItems.enumerated()), id: \.offset) { _, member in
                        let chipBody = HStack(spacing: 7) {
                            Image(systemName: member.icon)
                                .font(.caption)
                                .foregroundStyle(member.role == "导演" ? AuroraColor.brand : AuroraColor.textSecondary)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(member.name)
                                    .font(.caption.bold())
                                    .foregroundStyle(AuroraColor.textPrimary)
                                    .lineLimit(1)
                                Text(member.role)
                                    .font(.system(size: 10, weight: .medium))
                                    .foregroundStyle(AuroraColor.textSecondary)
                                    .lineLimit(1)
                            }
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 7)
                        .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .stroke(AuroraColor.hairline, lineWidth: 1)
                        )
                        .frame(minHeight: 44)

                        if let onSearch = onOpenSearchKeyword {
                            Button {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                isViewDismissed = true
                                tearDownDeviceOrientationTracking()
                                restoreBrightnessAndOrientationIfNeeded()
                                cleanupPlayer(forceStopPlayer: true)
                                onSearch(member.name)
                            } label: {
                                chipBody
                            }
                            .buttonStyle(.plain)
                        } else {
                            chipBody
                        }
                    }
                }
            }
            .contentMargins(.horizontal, AuroraMetrics.contentInset, for: .scrollContent)
            .padding(.horizontal, -AuroraMetrics.contentInset)
        }
    }

    private var synopsisSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("基本介绍")
                .font(.headline)
                .foregroundStyle(AuroraColor.textPrimary)

            let metaSynopsis = detailMetadata?.synopsis.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let fallbackSubtitle = currentVideo.subtitle.trimmingCharacters(in: .whitespacesAndNewlines)
            let description: String = {
                if !metaSynopsis.isEmpty { return metaSynopsis }
                if !fallbackSubtitle.isEmpty { return fallbackSubtitle }
                return "本片由 \(currentVideo.sourceName) 源站直连分发。"
            }()

            VStack(alignment: .leading, spacing: 6) {
                Text(description)
                    .font(.subheadline)
                    .foregroundStyle(AuroraColor.textSecondary)
                    .lineSpacing(3)
                    .lineLimit(isDescriptionExpanded ? nil : 4)

                if description.count > 75 {
                    Button {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            isDescriptionExpanded.toggle()
                        }
                    } label: {
                        Text(isDescriptionExpanded ? "收起" : "展开全部")
                            .font(.caption.bold())
                            .foregroundStyle(AuroraColor.brand)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(AuroraColor.hairline, lineWidth: 0.75)
            )
        }
    }

    // MARK: - 6. 下方其他视频推荐

    private var relatedVideosSection: some View {
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        return VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
            Text("更多推荐")
                .font(isPad ? .title3.bold() : .headline)
                .foregroundStyle(AuroraColor.textPrimary)

            if isPad {
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 16), count: 4), spacing: 20) {
                    ForEach(relatedVideos.prefix(16)) { related in
                        PosterCard(
                            video: related,
                            onSelect: {
                                switchToVideo(related)
                            }
                        )
                    }
                }
            } else {
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 16) {
                    ForEach(relatedVideos.prefix(12)) { related in
                        PosterCard(
                            video: related,
                            onSelect: {
                                switchToVideo(related)
                            }
                        )
                    }
                }
            }
        }
    }

    // MARK: - YouTube 智能全屏与物理方向联动引擎

    private func enterAdaptiveFullscreen(
        triggeredByGravity: Bool = false,
        forceLandscapeOverride: Bool? = nil
    ) {
        let idiom = UIDevice.current.userInterfaceIdiom
        let preferLandscape = forceLandscapeOverride
            ?? VideoAspectProfile.prefersLandscapeFullscreen(aspectRatio: effectiveAspectRatio, idiom: idiom)
        gravityMachine.enterFullscreen(preferLandscape: preferLandscape, triggeredByGravity: triggeredByGravity)
        PlayerOrientationCoordinator.shared.applyPhoneOrientation(landscape: preferLandscape)
        withAnimation(reduceMotion ? nil : .spring(response: 0.34, dampingFraction: 0.86)) {
            isFullScreen = true
            isLandscape = preferLandscape
        }
        scheduleHideControls()
    }

    private func exitAdaptiveFullscreen() {
        gravityMachine.exitFullscreen()
        PlayerOrientationCoordinator.shared.applyPhoneOrientation(landscape: false)
        withAnimation(reduceMotion ? nil : .spring(response: 0.34, dampingFraction: 0.86)) {
            isFullScreen = false
            isLandscape = false
            isShowingInPlayerEpisodes = false
        }
        scheduleHideControls()
    }

    private func toggleManualOrientation() {
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        let nextLandscape = !isLandscape
        if !isFullScreen {
            enterAdaptiveFullscreen(forceLandscapeOverride: nextLandscape)
        } else {
            let devOri = UIDevice.current.orientation
            let physicallyLandscape = (devOri == .landscapeLeft || devOri == .landscapeRight)
            gravityMachine.setLandscapeInFullscreen(nextLandscape, currentlyPhysicallyLandscape: physicallyLandscape)
            PlayerOrientationCoordinator.shared.applyPhoneOrientation(landscape: nextLandscape)
            withAnimation(reduceMotion ? nil : .spring(response: 0.34, dampingFraction: 0.86)) {
                isLandscape = nextLandscape
            }
        }
        showHud(
            icon: nextLandscape ? "iphone.landscape" : "iphone",
            text: nextLandscape ? "横屏沉浸" : "竖屏模式"
        )
        scheduleHideControls()
    }

    private func setupDeviceOrientationTrackingIfNeeded() {
        guard UIDevice.current.userInterfaceIdiom != .pad else { return }
        if !didBeginGeneratingOrientationNotifications {
            UIDevice.current.beginGeneratingDeviceOrientationNotifications()
            didBeginGeneratingOrientationNotifications = true
        }
        if let existing = deviceOrientationObserverToken {
            NotificationCenter.default.removeObserver(existing)
        }
        deviceOrientationObserverToken = NotificationCenter.default.addObserver(
            forName: UIDevice.orientationDidChangeNotification,
            object: nil,
            queue: .main
        ) { _ in
            Task { @MainActor in
                guard !self.isViewDismissed,
                      !self.isShowingEpisodePicker,
                      !self.isShowingInSight else { return }
                let devOri = UIDevice.current.orientation
                let action = self.gravityMachine.handleDeviceOrientation(
                    devOri,
                    videoKind: self.videoOrientationKind,
                    idiom: UIDevice.current.userInterfaceIdiom
                )
                switch action {
                case .none:
                    break
                case .enterLandscapeFullscreen:
                    PlayerOrientationCoordinator.shared.applyPhoneOrientation(
                        landscape: true,
                        preferredDeviceOrientation: devOri
                    )
                    withAnimation(self.reduceMotion ? nil : .spring(response: 0.34, dampingFraction: 0.86)) {
                        self.isFullScreen = true
                        self.isLandscape = true
                    }
                    self.scheduleHideControls()
                case .exitToInlinePortrait:
                    PlayerOrientationCoordinator.shared.applyPhoneOrientation(landscape: false)
                    withAnimation(self.reduceMotion ? nil : .spring(response: 0.34, dampingFraction: 0.86)) {
                        self.isFullScreen = false
                        self.isLandscape = false
                        self.isShowingInPlayerEpisodes = false
                    }
                    self.scheduleHideControls()
                }
            }
        }
    }

    private func tearDownDeviceOrientationTracking() {
        if let token = deviceOrientationObserverToken {
            NotificationCenter.default.removeObserver(token)
            deviceOrientationObserverToken = nil
        }
        if didBeginGeneratingOrientationNotifications {
            UIDevice.current.endGeneratingDeviceOrientationNotifications()
            didBeginGeneratingOrientationNotifications = false
        }
    }

    // MARK: - 核心播放调度引擎（单一实例，零重新缓冲）

    private func startPlayback(
        for video: VideoItem,
        forceFresh: Bool = false,
        alternateLineIndex: Int = 0,
        useRelay: Bool = false
    ) async {
        guard !isViewDismissed, !Task.isCancelled else { return }
        cleanupPlayer(forceStopPlayer: false)
        let resolveID = UUID()
        activeResolveID = resolveID
        isResolving = true
        resolveError = nil
        resolveStatus = alternateLineIndex > 0 ? "正在切换备用线路…" : (useRelay ? "正在切换防限流通道…" : "正在极速秒开…")

        if !forceFresh, let retainedPlayer = PictureInPictureManager.shared.consumeRetainedPlayer(for: video) {
            if retainedPlayer.error == nil,
               let item = retainedPlayer.currentItem,
               item.status != .failed,
               !isViewDismissed,
               !Task.isCancelled,
               activeResolveID == resolveID {
                try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback, options: [])
                try? AVAudioSession.sharedInstance().setActive(true)
                let wasPlayingInPiP = retainedPlayer.timeControlStatus == .playing || retainedPlayer.rate > 0
                if wasPlayingInPiP, retainedPlayer.rate > 0 {
                    self.playbackRate = retainedPlayer.rate
                }
                attachObservers(to: retainedPlayer, preservePlaybackState: true, wasPlaying: wasPlayingInPiP)
                self.player = retainedPlayer
                self.isPlaying = wasPlayingInPiP
                self.isMuted = retainedPlayer.isMuted || retainedPlayer.volume == 0
                UIApplication.shared.isIdleTimerDisabled = wasPlayingInPiP
                self.isResolving = false
                return
            } else {
                retainedPlayer.pause()
                retainedPlayer.replaceCurrentItem(with: nil)
            }
        }

        if let localURL = await downloadManager.playableLocalURL(for: video, episode: currentEpisode) {
            guard activeResolveID == resolveID, !isViewDismissed, !Task.isCancelled else { return }
            resolveStatus = "从离线缓存中加载…"
            initPlayer(url: localURL, referer: nil)
            isResolving = false
            return
        }

        do {
            let result: PlaybackSourceResult
            if forceFresh {
                result = try await VideoPreloadManager.shared.resolveFresh(video: video, alternateLineIndex: alternateLineIndex)
            } else {
                result = try await VideoRepository.shared.resolvePlayback(video: video)
            }
            guard activeResolveID == resolveID, !isViewDismissed, !Task.isCancelled else { return }
            let isHLS = result.url.path.lowercased().hasSuffix(".m3u8") || result.url.absoluteString.lowercased().contains(".m3u8")
            if useRelay, isHLS,
               let relayURL = await HLSLoopbackRelayServer.shared.relayURL(for: result.url, referer: result.referer, headers: result.headers) {
                guard activeResolveID == resolveID, !isViewDismissed, !Task.isCancelled else { return }
                initPlayer(url: relayURL, referer: nil, headers: [:])
            } else {
                initPlayer(url: result.url, referer: result.referer, headers: result.headers)
            }
            isResolving = false
        } catch {
            guard activeResolveID == resolveID, !isViewDismissed, !Task.isCancelled else { return }
            resolveError = error.localizedDescription
            isResolving = false
        }
    }

    private func initPlayer(url: URL, referer: String?, headers: [String: String] = [:]) {
        guard !isViewDismissed else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback, options: [])
        try? AVAudioSession.sharedInstance().setActive(true)

        var assetOptions: [String: Any] = [:]
        var httpHeaders: [String: String] = [
            "User-Agent": PlaybackSourceResult.defaultUserAgent
        ]
        if let ref = referer, let refURL = URL(string: ref) {
            httpHeaders["Referer"] = refURL.absoluteString
            if refURL.host?.lowercased() == url.host?.lowercased() {
                let origin = (refURL.scheme.map { "\($0)://" } ?? "") + (refURL.host ?? "")
                if !origin.isEmpty {
                    httpHeaders["Origin"] = origin
                }
            }
        }
        for (k, v) in headers {
            if k.caseInsensitiveCompare("Origin") == .orderedSame,
               let originHost = URL(string: v)?.host?.lowercased(),
               let targetHost = url.host?.lowercased(),
               originHost != targetHost {
                continue
            }
            httpHeaders[k] = v
        }
        assetOptions["AVURLAssetHTTPHeaderFieldsKey"] = httpHeaders
        let asset = AVURLAsset(url: url, options: assetOptions)
        let playerItem = AVPlayerItem(asset: asset)

        let p = AVPlayer(playerItem: playerItem)
        self.isBuffering = true
        self.player = p
        self.isMuted = false
        self.lastSavedSecond = -1
        attachObservers(to: p, applyResumeSeek: true)
    }

    private func attachObservers(
        to p: AVPlayer,
        applyResumeSeek: Bool = false,
        preservePlaybackState: Bool = false,
        wasPlaying: Bool = true
    ) {
        guard let playerItem = p.currentItem else { return }
        self.player = p
        self.lastSavedSecond = -1
        self.didTriggerEndForCurrentItem = false
        let itemDur = CMTimeGetSeconds(playerItem.duration)
        if itemDur.isFinite && itemDur > 0 {
            self.duration = itemDur
        }
        if playerItem.presentationSize.width > 1 && playerItem.presentationSize.height > 1 {
            self.videoPresentationSize = playerItem.presentationSize
        }
        self.currentTime = max(0, CMTimeGetSeconds(p.currentTime()))
        self.loadMediaSelectionGroups(for: playerItem)
        self.configureRemoteTransportControls()
        self.updateNowPlayingMetadata()

        // 实时观察解码画幅尺寸（驱动竖屏短剧展开与全屏横竖方向自适应）
        itemPresentationSizeObserver = playerItem.observe(\.presentationSize, options: [.new, .initial]) { [self] item, _ in
            let size = item.presentationSize
            guard size.width > 1, size.height > 1 else { return }
            Task { @MainActor in
                guard !self.isViewDismissed, self.player === p, self.player?.currentItem === item else { return }
                let previousKind = self.videoOrientationKind
                self.videoPresentationSize = size
                let updatedKind = self.videoOrientationKind
                // 若用户在首帧尺寸就绪前已点击进入全屏，首帧揭示为竖屏片时自动回正竖屏铺满，揭示为横屏片时自动转横屏铺满
                if self.isFullScreen, UIDevice.current.userInterfaceIdiom != .pad, previousKind != updatedKind {
                    let shouldLandscape = (updatedKind == .landscape)
                    if self.isLandscape != shouldLandscape {
                        self.gravityMachine.setLandscapeInFullscreen(shouldLandscape)
                        PlayerOrientationCoordinator.shared.applyPhoneOrientation(landscape: shouldLandscape)
                        withAnimation(self.reduceMotion ? nil : .spring(response: 0.34, dampingFraction: 0.86)) {
                            self.isLandscape = shouldLandscape
                        }
                    }
                }
            }
        }

        // 观察时长
        itemDurationObserver = playerItem.observe(\.duration, options: [.new, .initial]) { [self] item, _ in
            let seconds = CMTimeGetSeconds(item.duration)
            if seconds.isFinite && seconds > 0 {
                Task { @MainActor in
                    self.duration = seconds
                    self.updateNowPlayingMetadata()
                }
            }
        }

        // 观察状态
        itemStatusObserver = playerItem.observe(\.status, options: [.new, .initial]) { [self] item, _ in
            let status = item.status
            let errorDesc = item.error?.localizedDescription
            Task { @MainActor in
                guard !self.isViewDismissed, self.player === p, self.player?.currentItem === item else {
                    p.pause()
                    return
                }
                if status == .readyToPlay {
                    self.isBuffering = false
                    if item.presentationSize.width > 1 && item.presentationSize.height > 1 {
                        self.videoPresentationSize = item.presentationSize
                    }
                    if preservePlaybackState {
                        self.isPlaying = wasPlaying
                        if wasPlaying {
                            if p.rate == 0 {
                                p.play()
                                if self.playbackRate != 1.0 {
                                    p.rate = self.playbackRate
                                }
                            }
                            UIApplication.shared.isIdleTimerDisabled = true
                        } else {
                            p.pause()
                            UIApplication.shared.isIdleTimerDisabled = false
                        }
                        self.loadMediaSelectionGroups(for: item)
                        self.updateNowPlayingMetadata()
                        return
                    }
                    self.isPlaying = true
                    self.loadMediaSelectionGroups(for: item)
                    if applyResumeSeek,
                       let record = self.progressStore.record(for: self.currentVideo),
                       !record.isManuallyCompleted,
                       record.positionMs > 3_000,
                       record.progressRatio < 0.95 {
                        let resumeSec = Double(record.positionMs) / 1000.0
                        self.currentTime = resumeSec
                        let cmTime = CMTime(seconds: resumeSec, preferredTimescale: 600)
                        p.seek(to: cmTime, toleranceBefore: .zero, toleranceAfter: .zero)
                    }
                    p.play()
                    if self.playbackRate != 1.0 {
                        p.rate = self.playbackRate
                    }
                    UIApplication.shared.isIdleTimerDisabled = true
                    self.scheduleHideControls()
                } else if status == .failed {
                    self.handlePlaybackFailure(errorDescription: errorDesc)
                }
            }
        }

        if let oldFailedToken = failedToPlayObserverToken {
            NotificationCenter.default.removeObserver(oldFailedToken)
            failedToPlayObserverToken = nil
        }
        failedToPlayObserverToken = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemFailedToPlayToEndTime,
            object: playerItem,
            queue: .main
        ) { [self] notification in
            let errDesc = (notification.userInfo?[AVPlayerItemFailedToPlayToEndTimeErrorKey] as? Error)?.localizedDescription
            Task { @MainActor in
                guard !self.isViewDismissed, self.player === p, self.player?.currentItem === playerItem else { return }
                self.handlePlaybackFailure(errorDescription: errDesc)
            }
        }

        let interval = CMTime(seconds: 0.4, preferredTimescale: CMTimeScale(NSEC_PER_SEC))
        timeObserverToken = p.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [self] time in
            let cur = CMTimeGetSeconds(time)
            let bufEnd: Double = {
                guard let firstRange = p.currentItem?.loadedTimeRanges.first?.timeRangeValue else { return 0 }
                let end = CMTimeGetSeconds(CMTimeRangeGetEnd(firstRange))
                return end.isFinite ? end : 0
            }()
            Task { @MainActor in
                if bufEnd > 0 {
                    self.bufferedTime = max(bufEnd, self.currentTime)
                }
                guard !self.isScrubbing, !self.isSeeking else { return }
                guard cur.isFinite && cur >= 0 else { return }
                self.currentTime = cur
                let sec = Int(cur)
                if sec != self.lastSavedSecond {
                    self.updateNowPlayingElapsedTime(cur)
                }
                if sec > 2 && sec % 3 == 0 && sec != self.lastSavedSecond && self.duration > 5.0 {
                    self.lastSavedSecond = sec
                    self.progressStore.updateProgress(
                        video: self.currentVideo,
                        positionMs: Int64(cur * 1000),
                        durationMs: Int64(self.duration * 1000)
                    )
                }
            }
        }

        if let oldToken = didPlayToEndObserverToken {
            NotificationCenter.default.removeObserver(oldToken)
            didPlayToEndObserverToken = nil
        }
        didPlayToEndObserverToken = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: playerItem,
            queue: .main
        ) { [self] _ in
            Task { @MainActor in
                guard !self.didTriggerEndForCurrentItem else { return }
                self.didTriggerEndForCurrentItem = true
                self.handleEpisodePlaybackEnded()
            }
        }

        scheduleHideControls()
    }

    private var canSelectMediaTracks: Bool {
        subtitleSelectionGroup?.options.isEmpty == false || (audioSelectionGroup?.options.count ?? 0) > 1
    }

    private func loadMediaSelectionGroups(for item: AVPlayerItem) {
        Task { @MainActor in
            let subtitleGroup = try? await item.asset.loadMediaSelectionGroup(for: .legible)
            let audioGroup = try? await item.asset.loadMediaSelectionGroup(for: .audible)
            guard self.player?.currentItem === item else { return }
            self.subtitleSelectionGroup = subtitleGroup
            self.audioSelectionGroup = audioGroup
        }
    }

    // MARK: - 手势与传输交互动作

    private func handleDoubleTap(at x: CGFloat, in width: CGFloat) {
        let effectiveWidth = max(width, 1)
        if x < effectiveWidth * 0.35 {
            let impact = UIImpactFeedbackGenerator(style: .medium)
            impact.impactOccurred()
            seekRelative(-10)
            showHud(icon: "gobackward.10", text: "快退 10 秒")
        } else if x > effectiveWidth * 0.65 {
            let impact = UIImpactFeedbackGenerator(style: .medium)
            impact.impactOccurred()
            seekRelative(10)
            showHud(icon: "goforward.10", text: "快进 10 秒")
        } else {
            toggleFillMode()
        }
    }

    private func handleVerticalSideDrag(startX: CGFloat, translationHeight: CGFloat, width: CGFloat, isPad: Bool) {
        guard isFullScreen else { return }
        if activeGestureMode == nil {
            if startX < width * 0.35 {
                activeGestureMode = .brightness
                let currentBrightness = PlayerOrientationCoordinator.activeWindowScene()?.screen.brightness ?? 0.5
                if originalScreenBrightness == nil {
                    originalScreenBrightness = currentBrightness
                }
                gestureInitialValue = currentBrightness
            } else if startX > width * 0.65 {
                activeGestureMode = .volume
                gestureInitialValue = CGFloat(player?.volume ?? 1.0)
            }
        }
        guard let mode = activeGestureMode else { return }
        let deltaY = -translationHeight / (isPad ? 280.0 : 200.0)
        switch mode {
        case .brightness:
            let newBrightness = min(max(gestureInitialValue + deltaY, 0.0), 1.0)
            PlayerOrientationCoordinator.activeWindowScene()?.screen.brightness = newBrightness
            showHud(icon: "sun.max.fill", text: "亮度", progress: Double(newBrightness))
        case .volume:
            let newVolume = min(max(gestureInitialValue + deltaY, 0.0), 1.0)
            player?.isMuted = (newVolume == 0)
            player?.volume = Float(newVolume)
            isMuted = (newVolume == 0)
            let icon = newVolume > 0.5 ? "speaker.wave.3.fill" : (newVolume > 0 ? "speaker.wave.1.fill" : "speaker.slash.fill")
            showHud(icon: icon, text: "音量", progress: Double(newVolume))
        }
    }

    private func handlePinchZoom(scale: CGFloat) {
        if pinchBaselineFillMode == nil {
            pinchBaselineFillMode = isFillMode
        }
        guard let base = pinchBaselineFillMode else { return }
        if !base && scale > 1.16 {
            let impact = UIImpactFeedbackGenerator(style: .medium)
            impact.impactOccurred()
            withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                isFillMode = true
            }
            pinchBaselineFillMode = true
            showHud(icon: "rectangle.arrowtriangle.2.inward", text: "填满屏幕")
        } else if base && scale < 0.86 {
            let impact = UIImpactFeedbackGenerator(style: .medium)
            impact.impactOccurred()
            withAnimation(.spring(response: 0.28, dampingFraction: 0.82)) {
                isFillMode = false
            }
            pinchBaselineFillMode = false
            showHud(icon: "rectangle.arrowtriangle.2.outward", text: "原片比例")
        }
    }

    private func handleSpeedBoostChanged(_ boosting: Bool) {
        guard let p = player else { return }
        if boosting {
            guard isPlaying, !isScrubbing else { return }
            preBoostPlaybackRate = playbackRate
            isSpeedBoosting = true
            p.rate = 2.0
            UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
        } else if isSpeedBoosting {
            isSpeedBoosting = false
            if isPlaying {
                p.rate = preBoostPlaybackRate
            }
        }
    }

    private func togglePlayPause() {
        guard let p = player else { return }
        let impact = UIImpactFeedbackGenerator(style: .light)
        impact.impactOccurred()
        if isPlaying {
            isSpeedBoosting = false
            p.pause()
            isPlaying = false
            UIApplication.shared.isIdleTimerDisabled = false
            hideControlsTask?.cancel()
        } else {
            p.play()
            if playbackRate != 1.0 {
                p.rate = playbackRate
            }
            isPlaying = true
            UIApplication.shared.isIdleTimerDisabled = true
            scheduleHideControls()
        }
        updateNowPlayingMetadata()
    }

    private func toggleMute() {
        guard let p = player else { return }
        let impact = UIImpactFeedbackGenerator(style: .light)
        impact.impactOccurred()
        if !isMuted && p.volume > 0 {
            gestureInitialValue = CGFloat(p.volume)
            p.isMuted = true
            p.volume = 0
            isMuted = true
            showHud(icon: "speaker.slash.fill", text: "已静音", progress: 0)
        } else {
            let restored = Float(gestureInitialValue > 0.05 ? gestureInitialValue : 0.85)
            p.isMuted = false
            p.volume = restored
            isMuted = false
            showHud(icon: "speaker.wave.2.fill", text: "已恢复声音", progress: Double(restored))
        }
    }

    private func seekTo(time: Double) {
        guard let p = player else { return }
        seekGeneration &+= 1
        let gen = seekGeneration
        isSeeking = true
        currentTime = time
        p.currentItem?.cancelPendingSeeks()
        let cmTime = CMTime(seconds: time, preferredTimescale: 600)
        p.seek(to: cmTime, toleranceBefore: .zero, toleranceAfter: .zero) { _ in
            DispatchQueue.main.async {
                if self.seekGeneration == gen {
                    self.isSeeking = false
                }
            }
        }
    }

    private func seekRelative(_ delta: Double) {
        let maxDur = duration > 0 ? duration : 3600
        let target = min(max(currentTime + delta, 0), maxDur)
        seekTo(time: target)
        scheduleHideControls()
    }

    private func toggleFillMode() {
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        withAnimation(.spring(response: 0.3, dampingFraction: 0.82)) {
            isFillMode.toggle()
        }
        showHud(
            icon: isFillMode ? "rectangle.arrowtriangle.2.inward" : "rectangle.arrowtriangle.2.outward",
            text: isFillMode ? "填满屏幕" : "原片比例"
        )
        scheduleHideControls()
    }

    private func restoreBrightnessAndOrientationIfNeeded() {
        if let savedBrightness = originalScreenBrightness {
            PlayerOrientationCoordinator.activeWindowScene()?.screen.brightness = savedBrightness
            originalScreenBrightness = nil
        }
        gravityMachine.exitFullscreen()
        PlayerOrientationCoordinator.shared.resetToPortraitIfNeeded()
    }

    private func configureRemoteTransportControls() {
        tearDownRemoteTransportControls(clearMetadata: false)
        let commandCenter = MPRemoteCommandCenter.shared()

        commandCenter.playCommand.isEnabled = true
        let playTarget = commandCenter.playCommand.addTarget { _ in
            Task { @MainActor in
                if !self.isPlaying {
                    self.togglePlayPause()
                }
            }
            return .success
        }

        commandCenter.pauseCommand.isEnabled = true
        let pauseTarget = commandCenter.pauseCommand.addTarget { _ in
            Task { @MainActor in
                if self.isPlaying {
                    self.togglePlayPause()
                }
            }
            return .success
        }

        commandCenter.togglePlayPauseCommand.isEnabled = true
        let toggleTarget = commandCenter.togglePlayPauseCommand.addTarget { _ in
            Task { @MainActor in
                self.togglePlayPause()
            }
            return .success
        }

        commandCenter.skipForwardCommand.isEnabled = true
        commandCenter.skipForwardCommand.preferredIntervals = [10]
        let forwardTarget = commandCenter.skipForwardCommand.addTarget { event in
            let delta = (event as? MPSkipIntervalCommandEvent)?.interval ?? 10
            Task { @MainActor in
                self.seekRelative(delta)
            }
            return .success
        }

        commandCenter.skipBackwardCommand.isEnabled = true
        commandCenter.skipBackwardCommand.preferredIntervals = [10]
        let backwardTarget = commandCenter.skipBackwardCommand.addTarget { event in
            let delta = (event as? MPSkipIntervalCommandEvent)?.interval ?? 10
            Task { @MainActor in
                self.seekRelative(-delta)
            }
            return .success
        }

        commandCenter.changePlaybackPositionCommand.isEnabled = true
        let scrubTarget = commandCenter.changePlaybackPositionCommand.addTarget { event in
            guard let posEvent = event as? MPChangePlaybackPositionCommandEvent else {
                return .commandFailed
            }
            let targetSec = posEvent.positionTime
            Task { @MainActor in
                self.seekTo(time: targetSec)
                self.updateNowPlayingElapsedTime(targetSec)
            }
            return .success
        }

        remoteCommandCleanups = [
            { commandCenter.playCommand.removeTarget(playTarget) },
            { commandCenter.pauseCommand.removeTarget(pauseTarget) },
            { commandCenter.togglePlayPauseCommand.removeTarget(toggleTarget) },
            { commandCenter.skipForwardCommand.removeTarget(forwardTarget) },
            { commandCenter.skipBackwardCommand.removeTarget(backwardTarget) },
            { commandCenter.changePlaybackPositionCommand.removeTarget(scrubTarget) }
        ]
    }

    private func updateNowPlayingMetadata() {
        var info = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
        info[MPMediaItemPropertyTitle] = currentVideo.title
        info[MPMediaItemPropertyArtist] = currentVideo.sourceName
        if duration.isFinite && duration > 0 {
            info[MPMediaItemPropertyPlaybackDuration] = duration
        }
        if currentTime.isFinite && currentTime >= 0 {
            info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentTime
        }
        info[MPNowPlayingInfoPropertyPlaybackRate] = isPlaying ? playbackRate : Float(0.0)
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    private func updateNowPlayingElapsedTime(_ elapsed: Double) {
        guard var info = MPNowPlayingInfoCenter.default().nowPlayingInfo else {
            updateNowPlayingMetadata()
            return
        }
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = elapsed
        if duration.isFinite && duration > 0 {
            info[MPMediaItemPropertyPlaybackDuration] = duration
        }
        info[MPNowPlayingInfoPropertyPlaybackRate] = isPlaying ? playbackRate : Float(0.0)
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    private func tearDownRemoteTransportControls(clearMetadata: Bool) {
        for cleanup in remoteCommandCleanups {
            cleanup()
        }
        remoteCommandCleanups.removeAll()
        if clearMetadata {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        }
    }

    private func scheduleHideControls() {
        hideControlsTask?.cancel()
        guard !isVoiceOverEnabled else {
            showControls = true
            return
        }
        let task = DispatchWorkItem {
            withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.28)) {
                if self.isPlaying && !self.isScrubbing {
                    self.showControls = false
                }
            }
        }
        hideControlsTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 3.5, execute: task)
    }

    private func dismissView() {
        isViewDismissed = true
        tearDownDeviceOrientationTracking()
        restoreBrightnessAndOrientationIfNeeded()
        cleanupPlayer(forceStopPlayer: !PictureInPictureManager.shared.isPiPActiveOrStarting)
        onDismiss()
    }

    private func cleanupPlayer(forceStopPlayer: Bool = false) {
        UIApplication.shared.isIdleTimerDisabled = false
        activeResolveID = UUID()
        isResolving = false
        isBuffering = false
        isSpeedBoosting = false
        autoAdvanceWorkItem?.cancel()
        autoAdvanceWorkItem = nil
        if player != nil, duration > 5.0, currentTime > 2.0, currentTime < duration - 1.0 {
            progressStore.updateProgress(
                video: currentVideo,
                positionMs: Int64(currentTime * 1000),
                durationMs: Int64(duration * 1000)
            )
            progressStore.flushPendingPersist()
        }
        hideControlsTask?.cancel()
        subtitleSelectionGroup = nil
        audioSelectionGroup = nil
        itemStatusObserver?.invalidate()
        itemStatusObserver = nil
        itemDurationObserver?.invalidate()
        itemDurationObserver = nil
        itemPresentationSizeObserver?.invalidate()
        itemPresentationSizeObserver = nil
        if let token = didPlayToEndObserverToken {
            NotificationCenter.default.removeObserver(token)
            didPlayToEndObserverToken = nil
        }
        if let token = failedToPlayObserverToken {
            NotificationCenter.default.removeObserver(token)
            failedToPlayObserverToken = nil
        }
        if let token = timeObserverToken, let p = player {
            p.removeTimeObserver(token)
            timeObserverToken = nil
        }
        if !forceStopPlayer, PictureInPictureManager.shared.isPiPActiveOrStarting, let activePlayer = player {
            tearDownRemoteTransportControls(clearMetadata: false)
            PictureInPictureManager.shared.retainPlayerForPiP(
                player: activePlayer,
                video: currentVideo,
                duration: duration
            )
        } else {
            tearDownRemoteTransportControls(clearMetadata: isViewDismissed || forceStopPlayer)
            if forceStopPlayer || isViewDismissed {
                PictureInPictureManager.shared.stopAndReleasePiPIfNeeded()
                PictureInPictureManager.shared.detach()
            }
            if let existingPlayer = player {
                existingPlayer.pause()
                existingPlayer.replaceCurrentItem(with: nil)
            }
            if isViewDismissed {
                Task.detached(priority: .utility) {
                    try? AVAudioSession.sharedInstance().setActive(false, options: [.notifyOthersOnDeactivation])
                }
            }
        }
        player = nil
        currentTime = 0
        bufferedTime = 0
        duration = 0
        lastSavedSecond = -1
        didTriggerEndForCurrentItem = false
        isPlaying = false
    }

    private func handleEpisodePlaybackEnded() {
        guard !isSwitchingNextEpisode, !isViewDismissed else { return }
        progressStore.markCompleted(for: currentVideo)
        guard let currentEp = currentEpisode, !episodes.isEmpty else { return }
        if let nextEp = episodes.first(where: { $0.number == currentEp.number + 1 }) {
            isSwitchingNextEpisode = true
            showHud(
                icon: "forward.end.fill",
                text: "本集结束，自动播放 \(nextEp.label)"
            )
            let impact = UINotificationFeedbackGenerator()
            impact.notificationOccurred(.success)
            autoAdvanceWorkItem?.cancel()
            let item = DispatchWorkItem {
                guard !self.isViewDismissed else {
                    self.isSwitchingNextEpisode = false
                    return
                }
                self.switchEpisode(nextEp)
                DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                    self.isSwitchingNextEpisode = false
                }
            }
            autoAdvanceWorkItem = item
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6, execute: item)
        } else {
            showHud(
                icon: "checkmark.circle.fill",
                text: "全剧播放完毕"
            )
        }
    }

    private func handlePlaybackFailure(errorDescription: String?) {
        self.isBuffering = false
        VideoPreloadManager.shared.invalidateCache(for: self.currentVideo.playbackUrl)
        guard !self.isViewDismissed else { return }
        if self.recoveryStage < 2 {
            self.recoveryStage += 1
            let nextStage = self.recoveryStage
            let targetVideo = self.currentVideo
            Task { @MainActor in
                await self.startPlayback(
                    for: targetVideo,
                    forceFresh: true,
                    alternateLineIndex: max(nextStage - 1, 0),
                    useRelay: true
                )
            }
            return
        }
        self.player = nil
        self.resolveError = errorDescription ?? "播放器解码失败"
        UIApplication.shared.isIdleTimerDisabled = false
    }

    private func switchEpisode(_ ep: PlaybackEpisode) {
        cleanupPlayer(forceStopPlayer: true)
        recoveryStage = 0
        currentEpisode = ep
        let baseId = currentVideo.id.replacingOccurrences(of: #"-ep-\d+$"#, with: "", options: .regularExpression)
        let baseTitle = initialVideo.id.replacingOccurrences(of: #"-ep-\d+$"#, with: "", options: .regularExpression) == baseId
            ? initialVideo.title
            : currentVideo.title
        let epVideo = VideoItem(
            id: "\(baseId)-ep-\(ep.number)",
            title: "\(baseTitle) \(ep.label)",
            subtitle: currentVideo.subtitle,
            coverUrl: currentVideo.coverUrl,
            playbackUrl: ep.pageUrl,
            duration: currentVideo.duration,
            rating: currentVideo.rating,
            tags: currentVideo.tags,
            category: currentVideo.category,
            isTop10: currentVideo.isTop10,
            rankNumber: nil,
            sourceName: currentVideo.sourceName
        )
        currentVideo = epVideo

        GlobalStartupPrefetchEngine.shared.handleEpisodePlaying(
            video: currentVideo,
            currentEpisode: ep,
            allEpisodes: episodes
        )
    }

    private func switchToVideo(_ newVideo: VideoItem) {
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        cleanupPlayer(forceStopPlayer: true)
        resolveError = nil
        recoveryStage = 0
        videoPresentationSize = .zero
        inlineScrollOffsetY = 0
        currentVideo = newVideo
        currentEpisode = nil
        episodes = []
    }

    private func loadEpisodes(for video: VideoItem) async {
        isLoadingEpisodes = true
        let eps = await videoRepo.resolveEpisodes(for: video)
        guard !Task.isCancelled, !isViewDismissed else { return }
        episodes = eps
        if currentEpisode == nil {
            let parsedEpNum = OfflineDownloadManager.extractEpisodeNumber(fromVideoID: video.id)
            currentEpisode = eps.first(where: { $0.pageUrl == video.playbackUrl })
                ?? parsedEpNum.flatMap { num in eps.first(where: { $0.number == num }) }
                ?? eps.first
        }
        isLoadingEpisodes = false

        if let currentEp = currentEpisode {
            let videoSnapshot = video
            let epsSnapshot = eps
            Task.detached(priority: .utility) {
                try? await Task.sleep(for: .milliseconds(500))
                guard !Task.isCancelled else { return }
                await MainActor.run {
                    GlobalStartupPrefetchEngine.shared.handleEpisodePlaying(
                        video: videoSnapshot,
                        currentEpisode: currentEp,
                        allEpisodes: epsSnapshot
                    )
                }
            }
        }
    }

    private func showToast(text: String) {
        downloadToastText = text
        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
            showDownloadToast = true
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            withAnimation(.easeInOut(duration: 0.2)) {
                self.showDownloadToast = false
            }
        }
    }

    private func formatTime(_ seconds: Double) -> String {
        guard seconds.isFinite && seconds >= 0 else { return "00:00" }
        let secs = Int(seconds)
        let h = secs / 3600
        let m = (secs % 3600) / 60
        let s = secs % 60
        if h > 0 {
            return String(format: "%d:%02d:%02d", h, m, s)
        }
        return String(format: "%02d:%02d", m, s)
    }

    private func formatRemainingTime(_ current: Double, duration: Double) -> String {
        guard duration.isFinite, duration > 0, current.isFinite else { return "--:--" }
        let remaining = max(0, duration - current)
        let secs = Int(remaining)
        let h = secs / 3600
        let m = (secs % 3600) / 60
        let s = secs % 60
        if h > 0 {
            return String(format: "-%d:%02d:%02d", h, m, s)
        }
        return String(format: "-%02d:%02d", m, s)
    }

    private func showHud(icon: String, text: String, progress: Double? = nil) {
        hudTask?.cancel()
        withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
            self.hudIcon = icon
            self.hudMessage = text
            self.hudProgress = progress
        }
        let task = DispatchWorkItem {
            withAnimation(.easeInOut(duration: 0.25)) {
                self.hudMessage = nil
                self.hudIcon = nil
                self.hudProgress = nil
            }
        }
        hudTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2, execute: task)
    }
}

// MARK: - 播放器视口多模态自然手势修饰符（单击、三区双击、上滑进全屏/下滑退全屏、双侧调光调音、双指缩放）

private struct PlayerSurfaceGestureModifier: ViewModifier {
    let isFullScreen: Bool
    let viewportWidth: CGFloat
    let isPad: Bool
    let onSingleTap: () -> Void
    let onDoubleTapAtX: (CGFloat) -> Void
    let onVerticalSideDrag: (CGFloat, CGFloat) -> Void
    let onVerticalSideDragEnd: () -> Void
    let onSwipeUpInline: () -> Void
    let onSwipeDownFullscreen: () -> Void
    let onPinchScale: (CGFloat) -> Void
    let onPinchEnd: () -> Void

    @State private var isSideDragging: Bool = false

    func body(content: Content) -> some View {
        content
            .contentShape(Rectangle())
            .onTapGesture(count: 2) { location in
                onDoubleTapAtX(location.x)
            }
            .onTapGesture(count: 1) {
                onSingleTap()
            }
            .simultaneousGesture(
                MagnificationGesture()
                    .onChanged { scale in
                        onPinchScale(scale)
                    }
                    .onEnded { _ in
                        onPinchEnd()
                    }
            )
            .simultaneousGesture(
                DragGesture(minimumDistance: 18)
                    .onChanged { value in
                        let dx = value.translation.width
                        let dy = value.translation.height
                        guard abs(dy) > abs(dx) * 1.15 else { return }
                        if isFullScreen {
                            let startX = value.startLocation.x
                            if startX < viewportWidth * 0.35 || startX > viewportWidth * 0.65 {
                                isSideDragging = true
                                onVerticalSideDrag(startX, dy)
                            }
                        }
                    }
                    .onEnded { value in
                        if isSideDragging {
                            isSideDragging = false
                            onVerticalSideDragEnd()
                            return
                        }
                        let dx = value.translation.width
                        let dy = value.translation.height
                        let isVerticalDominant = abs(dy) > abs(dx) * 1.25
                        guard isVerticalDominant else { return }

                        if isFullScreen {
                            // 全屏中央区域向下轻扫 -> YouTube / Apple TV 式跟手退回二级页
                            if dy > 56 {
                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                onSwipeDownFullscreen()
                            }
                        } else {
                            // 二级页内嵌播放器向上轻扫 -> 直接展开自适应全屏
                            if dy < -48 {
                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                onSwipeUpInline()
                            }
                        }
                    }
            )
    }
}
