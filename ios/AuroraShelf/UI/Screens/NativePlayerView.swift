import SwiftUI
import AVKit
import Combine

public struct NativePlayerView: View {
    public let video: VideoItem
    public let onDismiss: () -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityVoiceOverEnabled) private var isVoiceOverEnabled
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var player: AVPlayer?
    @State private var isPlaying: Bool = true
    @State private var showControls: Bool = true
    @State private var currentTime: Double = 0
    @State private var duration: Double = 0
    @State private var isSeeking: Bool = false
    @State private var isScrubbing: Bool = false
    @State private var scrubTime: Double = 0
    @State private var timeObserverToken: Any?
    @State private var itemStatusObserver: NSKeyValueObservation?
    @State private var itemDurationObserver: NSKeyValueObservation?
    @State private var hideControlsTask: DispatchWorkItem?
    @State private var resolveTask: Task<Void, Never>?
    @State private var activeResolveID = UUID()
    @State private var subtitleSelectionGroup: AVMediaSelectionGroup?
    @State private var audioSelectionGroup: AVMediaSelectionGroup?

    // 真实流解析状态
    @State private var isResolving: Bool = true
    @State private var resolveStatus: String = "正在连接视频源…"
    @State private var resolveError: String?
    @State private var resolvedResult: PlaybackSourceResult?

    // 画幅控制与横竖屏
    @State private var isFillMode: Bool = false
    @State private var isLandscape: Bool = false
    @State private var isShowingInSight: Bool = false
    @State private var playbackSpeed: Float = 1.0
    @State private var hudMessage: String?
    @State private var hudIcon: String?
    @State private var hudTask: DispatchWorkItem?

    // 屏幕两侧垂直滑动调节亮度与音量
    private enum PlayerVerticalGestureMode {
        case brightness
        case volume
    }
    @State private var activeGestureMode: PlayerVerticalGestureMode? = nil
    @State private var gestureInitialValue: CGFloat = 0.5

    // 剧集与选集状态
    @State private var activeVideo: VideoItem?
    @State private var episodes: [PlaybackEpisode] = []
    @State private var currentEpisode: PlaybackEpisode?
    @State private var isShowingEpisodesSheet: Bool = false
    @State private var isShowingEpisodesDrawer: Bool = false

    private var currentVideo: VideoItem {
        activeVideo ?? video
    }

    public init(video: VideoItem, onDismiss: @escaping () -> Void) {
        self.video = video
        self.onDismiss = onDismiss
    }

    public var body: some View {
        GeometryReader { geo in
            let isPad = UIDevice.current.userInterfaceIdiom == .pad
            let isRotated = !isPad && isLandscape
            let targetWidth = isRotated ? geo.size.height : geo.size.width
            let targetHeight = isRotated ? geo.size.width : geo.size.height
            let isWide = isPad || targetWidth >= 740

            ZStack {
                // 背景纯黑
                Color.black.ignoresSafeArea()

                // 错误回退提示优先呈现，杜绝因 player 实例未清空而遮蔽错误界面
                if let error = resolveError {
                    VStack(spacing: AuroraMetrics.spacingL) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.system(size: 40))
                            .foregroundStyle(AuroraColor.warning)

                        Text("播放失败")
                            .font(.title3.bold())
                            .foregroundStyle(.white)

                        Text(error)
                            .font(.callout)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, AuroraMetrics.spacingXXXL)

                        Button(action: {
                            startResolveAndPlay()
                        }) {
                            Text("重新连接")
                                .font(.headline)
                                .foregroundStyle(.white)
                                .padding(.horizontal, AuroraMetrics.spacingXL)
                                .padding(.vertical, AuroraMetrics.spacingM)
                                .background(AuroraColor.brand, in: .capsule)
                        }
                        .buttonStyle(.plain)
                        .padding(.top, AuroraMetrics.spacingS)
                    }
                } else if isResolving {
                    // 真实网络流换流加载中状态（Apple HIG 深色质感）
                    VStack(spacing: AuroraMetrics.spacingXL) {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: AuroraColor.brand))
                            .scaleEffect(1.4)

                        VStack(spacing: AuroraMetrics.spacingS) {
                            Text(resolveStatus)
                                .font(.headline)
                                .foregroundStyle(.white)

                            Text("\(currentVideo.sourceName) · \(currentVideo.title)")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                                .padding(.horizontal, AuroraMetrics.spacingXXXL)
                        }
                    }
                } else if let player = player {
                    // 原生 AVPlayerLayer 播放视口，支持满铺与自适应画幅，集成 Apple TV 三区双击手势
                    AuroraPlayerSurface(
                        player: player,
                        videoGravity: isFillMode ? .resizeAspectFill : .resizeAspect
                    )
                    .ignoresSafeArea()
                    .contentShape(Rectangle())
                    .simultaneousGesture(
                        DragGesture(minimumDistance: 15)
                            .onChanged { value in
                                let currentWidth = targetWidth > 0 ? targetWidth : geo.size.width
                                if activeGestureMode == nil {
                                    if value.startLocation.x < currentWidth * 0.5 {
                                        activeGestureMode = .brightness
                                        let activeScreen = (UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first(where: { $0.activationState == .foregroundActive }) ?? UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)?.screen
                                        gestureInitialValue = activeScreen?.brightness ?? 0.5
                                    } else {
                                        activeGestureMode = .volume
                                        gestureInitialValue = CGFloat(player.volume)
                                    }
                                }
                                let delta = -value.translation.height / 220.0
                                let newValue = min(max(gestureInitialValue + delta, 0.0), 1.0)
                                if activeGestureMode == .brightness {
                                    let activeScreen = (UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first(where: { $0.activationState == .foregroundActive }) ?? UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)?.screen
                                    activeScreen?.brightness = newValue
                                    showHud("\(Int(newValue * 100))%", icon: "sun.max.fill")
                                } else if activeGestureMode == .volume {
                                    player.volume = Float(newValue)
                                    let iconName = newValue == 0 ? "speaker.slash.fill" : (newValue < 0.5 ? "speaker.wave.1.fill" : "speaker.wave.3.fill")
                                    showHud("\(Int(newValue * 100))%", icon: iconName)
                                }
                            }
                            .onEnded { _ in
                                activeGestureMode = nil
                            }
                    )
                    .onTapGesture(count: 2, coordinateSpace: .global) { location in
                        let currentWidth = targetWidth > 0 ? targetWidth : geo.size.width
                        if location.x < currentWidth * 0.35 {
                            seekBy(-10)
                            showHud("‹‹ 10 秒")
                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        } else if location.x > currentWidth * 0.65 {
                            seekBy(10)
                            showHud("10 秒 ››")
                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        } else {
                            toggleFillMode()
                        }
                    }
                    .onTapGesture(count: 1) {
                        withAnimation(reduceMotion ? nil : AuroraMotion.quick) {
                            showControls.toggle()
                        }
                        if showControls {
                            scheduleAutoHideControls()
                        }
                    }
                }

                // 屏幕中央画幅与模式切换 HUD 提示（Apple HIG 毛玻璃胶囊）
                if let hud = hudMessage {
                    HStack(spacing: 8) {
                        if let icon = hudIcon {
                            Image(systemName: icon)
                                .font(.headline)
                        }
                        Text(hud)
                            .font(.callout.bold())
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 10)
                    .glassEffect(.regular, in: Capsule())
                    .transition(.scale(scale: 0.9).combined(with: .opacity))
                    .zIndex(100)
                }

                // Apple HIG 播放控制覆盖层
                if showControls && player != nil {
                    ZStack {
                        // 渐变可读性遮罩
                        VStack {
                            LinearGradient(
                                colors: [Color.black.opacity(0.8), Color.clear],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                            .frame(height: 120)

                            Spacer()

                            LinearGradient(
                                colors: [Color.clear, Color.black.opacity(0.8)],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                            .frame(height: 140)
                        }
                        .allowsHitTesting(false)
                        .ignoresSafeArea()

                        // 控件布局
                        VStack {
                            // 统一顶栏（集成画幅切换、选集、旋转与投屏）
                            PlayerOverlayBar(
                                title: currentVideo.title,
                                subtitle: currentVideo.sourceName,
                                onDismiss: {
                                    cleanupPlayer()
                                    onDismiss()
                                    dismiss()
                                }
                            ) {
                                HStack(spacing: isLandscape ? AuroraMetrics.spacingM : 8) {
                                    // 1. 选集入口（多集时显示，宽屏模式采用右侧毛玻璃抽屉，窄屏采用模态弹窗）
                                    if episodes.count > 1 {
                                        Button(action: {
                                            if isWide {
                                                withAnimation(AuroraMotion.smooth) {
                                                    isShowingEpisodesDrawer.toggle()
                                                }
                                            } else {
                                                isShowingEpisodesSheet = true
                                            }
                                        }) {
                                            HStack(spacing: 4) {
                                                Image(systemName: "square.stack.3d.down.right.fill")
                                                    .font(.subheadline.bold())
                                                Text(currentEpisode?.label ?? "player.episodes".localized)
                                                    .font(.caption.bold())
                                            }
                                            .foregroundStyle(.white)
                                            .padding(.horizontal, 10)
                                            .frame(height: AuroraMetrics.minTouchTarget)
                                            .frame(minWidth: AuroraMetrics.minTouchTarget)
                                            .glassEffect(.regular.interactive(), in: Capsule())
                                        }
                                        .buttonStyle(.plain)
                                        .hoverEffect(.highlight)
                                        .accessibilityLabel("选集，当前：\(currentEpisode?.label ?? "第 1 集")")
                                    }

                                    // 2. 画幅切换（Fit / Fill）
                                    Button(action: toggleFillMode) {
                                        Image(systemName: isFillMode ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right")
                                            .font(.subheadline.bold())
                                            .foregroundStyle(.white)
                                            .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                                            .glassEffect(.regular.interactive(), in: .circle)
                                    }
                                    .buttonStyle(.plain)
                                    .hoverEffect(.highlight)
                                    .accessibilityLabel(isFillMode ? "自适应画幅" : "填满全屏")

                                    // 3. 横竖屏旋转（仅限 iPhone，iPad 遵从硬件自由全向旋转，彻底消除大屏上的手机图标）
                                    if !isPad && targetWidth < 740 {
                                        Button(action: toggleOrientation) {
                                            Image(systemName: isLandscape ? "iphone.portrait" : "iphone.landscape")
                                                .font(.subheadline.bold())
                                                .foregroundStyle(.white)
                                                .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                                                .glassEffect(.regular.interactive(), in: .circle)
                                        }
                                        .buttonStyle(.plain)
                                        .hoverEffect(.highlight)
                                        .accessibilityLabel(isLandscape ? "竖屏模式" : "横屏沉浸")
                                    }

                                    if let playerItem = player?.currentItem, canSelectMediaTracks {
                                        PlayerMediaTrackMenu(
                                            playerItem: playerItem,
                                            subtitleGroup: subtitleSelectionGroup,
                                            audioGroup: audioSelectionGroup
                                        )
                                    }

                                    // 4. 倍速切换胶囊（Apple TV 标志性 Speed Picker）
                                    Menu {
                                        ForEach([0.75, 1.0, 1.25, 1.5, 2.0], id: \.self) { speed in
                                            Button {
                                                playbackSpeed = Float(speed)
                                                if isPlaying {
                                                    player?.rate = Float(speed)
                                                }
                                                showHud("\(speed == 1.0 ? "1.0" : String(format: "%.2g", speed))x 倍速")
                                            } label: {
                                                HStack {
                                                    Text(speed == 1.0 ? "正常 (1.0x)" : "\(String(format: "%.2g", speed))x")
                                                    if playbackSpeed == Float(speed) {
                                                        Image(systemName: "checkmark")
                                                    }
                                                }
                                            }
                                        }
                                    } label: {
                                        Text(playbackSpeed == 1.0 ? "1.0x" : "\(String(format: "%.2g", playbackSpeed))x")
                                            .font(.system(size: 11, weight: .bold))
                                            .foregroundStyle(.white)
                                            .padding(.horizontal, 8)
                                            .frame(height: AuroraMetrics.minTouchTarget)
                                            .glassEffect(.regular.interactive(), in: Capsule())
                                    }
                                    .accessibilityLabel("播放倍速，当前：\(playbackSpeed) 倍速")

                                    // 5. InSight 音画感知抽屉
                                    Button(action: { isShowingInSight = true }) {
                                        Image(systemName: "info.circle")
                                            .font(.subheadline.bold())
                                            .foregroundStyle(.white)
                                            .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                                            .glassEffect(.regular.interactive(), in: .circle)
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityLabel("InSight 媒体信息与控制")

                                    // 5. 系统画中画
                                    if PictureInPictureManager.shared.isPossible {
                                        Button(action: { PictureInPictureManager.shared.toggle() }) {
                                            Image(systemName: "rectangle.inset.filled")
                                                .font(.subheadline.bold())
                                                .foregroundStyle(.white)
                                                .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                                                .glassEffect(.regular.interactive(), in: .circle)
                                        }
                                        .buttonStyle(.plain)
                                        .accessibilityLabel("画中画")
                                    }

                                    // 6. AirPlay
                                    AirPlayRoutePicker()
                                        .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                                }
                            }
                            .padding(.top, max(12, geo.safeAreaInsets.top))

                            Spacer()

                            // 中心播放/快进控制组
                            HStack(spacing: AuroraMetrics.spacingXXXL) {
                                Button(action: { seekBy(-15) }) {
                                    Image(systemName: "gobackward.15")
                                        .font(.title2)
                                        .foregroundStyle(.white)
                                        .frame(width: 48, height: 48)
                                        .glassEffect(.regular.interactive(), in: .circle)
                                }
                                .buttonStyle(.plain)
                                .hoverEffect(.highlight)
                                .accessibilityLabel("快退 15 秒")

                                Button(action: { togglePlayPause() }) {
                                    Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                                        .font(.title)
                                        .foregroundStyle(.white)
                                        .frame(width: 68, height: 68)
                                        .background(AuroraColor.brand, in: .circle)
                                }
                                .buttonStyle(.plain)
                                .hoverEffect(.highlight)
                                .accessibilityLabel(isPlaying ? "暂停" : "播放")

                                Button(action: { seekBy(15) }) {
                                    Image(systemName: "goforward.15")
                                        .font(.title2)
                                        .foregroundStyle(.white)
                                        .frame(width: 48, height: 48)
                                        .glassEffect(.regular.interactive(), in: .circle)
                                }
                                .buttonStyle(.plain)
                                .hoverEffect(.highlight)
                                .accessibilityLabel("快进 15 秒")
                            }

                            Spacer()

                            // 底部进度条与时间显示（自研 PlayerScrubberBar，横竖屏与 90 度旋转均可 100% 灵敏拖拽）
                            VStack(spacing: AuroraMetrics.spacingS) {
                                PlayerScrubberBar(
                                    currentTime: $currentTime,
                                    duration: duration,
                                    onSeek: { targetTime in
                                        seekTo(time: targetTime)
                                        scheduleAutoHideControls()
                                    }
                                )

                                HStack {
                                    Text(formatTime(isScrubbing ? scrubTime : currentTime))
                                        .font(.footnote)
                                        .monospacedDigit()
                                        .foregroundStyle(.white.opacity(0.85))

                                    Spacer()

                                    Text(formatDuration(duration))
                                        .font(.footnote)
                                        .monospacedDigit()
                                        .foregroundStyle(.white.opacity(0.85))
                                }
                            }
                            .padding(.horizontal, AuroraMetrics.contentInset)
                            .padding(.bottom, max(16, geo.safeAreaInsets.bottom))
                        }
                        .zIndex(30)
                    }
                    .transition(.opacity)
                }

                if player == nil {
                    VStack {
                        PlayerOverlayBar(
                            title: currentVideo.title,
                            subtitle: currentVideo.sourceName,
                            onDismiss: {
                                cleanupPlayer()
                                onDismiss()
                                dismiss()
                            }
                        )
                        Spacer()
                    }
                    .padding(.top, max(12, geo.safeAreaInsets.top))
                    .zIndex(20)
                }

                // iPad 宽屏右侧半透明毛玻璃选集抽屉（Apple TV Trailing Drawer，不阻断主画面播放）
                if isShowingEpisodesDrawer && isWide {
                    HStack(spacing: 0) {
                        Spacer()

                        VStack(spacing: 0) {
                            EpisodePickerSheet(
                                videoTitle: currentVideo.title,
                                sourceName: currentVideo.sourceName,
                                episodes: episodes,
                                currentEpisodeNumber: currentEpisode?.number ?? 1,
                                onSelectEpisode: { ep in
                                    switchEpisode(ep)
                                    withAnimation(AuroraMotion.quick) {
                                        isShowingEpisodesDrawer = false
                                    }
                                },
                                onDismiss: {
                                    withAnimation(AuroraMotion.smooth) {
                                        isShowingEpisodesDrawer = false
                                    }
                                }
                            )
                        }
                        .frame(width: 360)
                        .background(.ultraThinMaterial)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .stroke(AuroraColor.hairline, lineWidth: 1)
                        )
                        .shadow(color: Color.black.opacity(0.4), radius: 24, x: -8, y: 0)
                        .padding(.vertical, 16)
                        .padding(.trailing, 16)
                        .transition(.move(edge: .trailing).combined(with: .opacity))
                    }
                    .zIndex(80)
                }
            }
            .frame(width: targetWidth, height: targetHeight)
            .rotationEffect(isRotated ? .degrees(90) : .zero)
            .position(x: geo.size.width / 2, y: geo.size.height / 2)
            .ignoresSafeArea()
        }
        .ignoresSafeArea()
        .statusBarHidden(!showControls || isLandscape)
        .persistentSystemOverlays(!showControls ? .hidden : .automatic)
        .simultaneousGesture(DragGesture(minimumDistance: 24).onEnded { value in
            guard value.translation.width > 100, abs(value.translation.width) > abs(value.translation.height) else { return }
            cleanupPlayer()
            onDismiss()
            dismiss()
        })
        .onAppear {
            if CommandLine.arguments.contains("--player-landscape") {
                self.isLandscape = true
            }
            if CommandLine.arguments.contains("--player-fill") {
                self.isFillMode = true
            }
            if CommandLine.arguments.contains("--show-episodes") {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                    self.isShowingEpisodesSheet = true
                }
            }
            startResolveAndPlay()
        }
        .task {
            let eps = await VideoRepository.shared.resolveEpisodes(for: video)
            self.episodes = eps
            if self.currentEpisode == nil {
                self.currentEpisode = eps.first(where: { $0.pageUrl == currentVideo.playbackUrl }) ?? eps.first
            }
            if let cur = self.currentEpisode,
               let idx = eps.firstIndex(where: { $0.number == cur.number }),
               idx + 1 < eps.count {
                let nextEp = eps[idx + 1]
                VideoPreloadManager.shared.prewarmNextEpisode(
                    pageUrl: nextEp.pageUrl,
                    title: "\(currentVideo.title) \(nextEp.label)",
                    sourceName: currentVideo.sourceName
                )
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .AVPlayerItemDidPlayToEndTime)) { notification in
            guard let item = notification.object as? AVPlayerItem, player?.currentItem === item else { return }
            isPlaying = false
            showControls = true
            PlaybackProgressStore.shared.markCompleted(for: currentVideo)

            // 自动续播下一集
            if let current = currentEpisode,
               let currentIndex = episodes.firstIndex(where: { $0.number == current.number }),
               currentIndex + 1 < episodes.count {
                let nextEpisode = episodes[currentIndex + 1]
                showHud("即将播放 \(nextEpisode.label)")
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                    switchEpisode(nextEpisode)
                }
            }
        }
        .onChange(of: isVoiceOverEnabled) { _, enabled in
            if enabled {
                hideControlsTask?.cancel()
                showControls = true
            }
        }
        .onDisappear {
            cleanupPlayer()
        }
        .sheet(isPresented: $isShowingEpisodesSheet) {
            EpisodePickerSheet(
                videoTitle: currentVideo.title,
                sourceName: currentVideo.sourceName,
                episodes: episodes,
                currentEpisodeNumber: currentEpisode?.number ?? 1,
                onSelectEpisode: { ep in
                    switchEpisode(ep)
                },
                onDismiss: {
                    isShowingEpisodesSheet = false
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
                onToggleOrientation: { toggleOrientation() },
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
            seekBy(-15)
            return .handled
        }
        .onKeyPress(.rightArrow) {
            seekBy(15)
            return .handled
        }
        .onKeyPress(characters: CharacterSet(charactersIn: "jJ")) { _ in
            seekBy(-15)
            return .handled
        }
        .onKeyPress(characters: CharacterSet(charactersIn: "kK")) { _ in
            togglePlayPause()
            return .handled
        }
        .onKeyPress(characters: CharacterSet(charactersIn: "lL")) { _ in
            seekBy(15)
            return .handled
        }
        .onKeyPress(characters: CharacterSet(charactersIn: "mM")) { _ in
            toggleMute()
            return .handled
        }
        .onKeyPress(characters: CharacterSet(charactersIn: "fF")) { _ in
            toggleFillMode()
            return .handled
        }
        .onKeyPress(.escape) {
            cleanupPlayer()
            onDismiss()
            dismiss()
            return .handled
        }
    }

    private func scheduleAutoHideControls() {
        hideControlsTask?.cancel()
        guard !isVoiceOverEnabled else {
            showControls = true
            return
        }
        let task = DispatchWorkItem {
            withAnimation(reduceMotion ? nil : AuroraMotion.quick) {
                if self.isPlaying {
                    self.showControls = false
                }
            }
        }
        hideControlsTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 3.0, execute: task)
    }

    private var sourcePlaybackURL: URL? {
        guard let url = URL(string: currentVideo.playbackUrl), url.scheme?.lowercased() == "https" else { return nil }
        return url
    }

    private func startResolveAndPlay(for targetVideo: VideoItem? = nil) {
        let v = targetVideo ?? currentVideo
        resolveTask?.cancel()
        let requestID = UUID()
        activeResolveID = requestID
        self.isResolving = true
        self.resolveError = nil
        self.resolveStatus = "正在解析 \(v.sourceName) 的播放地址…"

        resolveTask = Task { @MainActor in
            do {
                let result = try await VideoRepository.shared.resolvePlayback(video: v)
                guard !Task.isCancelled, activeResolveID == requestID else { return }
                resolveTask = nil
                resolvedResult = result
                startPlayback(with: result)
            } catch {
                guard !Task.isCancelled, activeResolveID == requestID else { return }
                resolveTask = nil
                isResolving = false
                resolveError = error.localizedDescription
            }
        }
    }

    private func switchEpisode(_ episode: PlaybackEpisode) {
        guard episode != currentEpisode else { return }
        self.currentEpisode = episode
        showHud("正在切换至 \(episode.label)")

        let newVideo = VideoItem(
            id: "\(video.id)-ep-\(episode.number)",
            title: "\(video.title) \(episode.label)",
            subtitle: video.subtitle,
            coverUrl: video.coverUrl,
            playbackUrl: episode.pageUrl,
            duration: video.duration,
            rating: video.rating,
            tags: video.tags,
            category: video.category,
            sourceName: video.sourceName
        )
        self.activeVideo = newVideo
        startResolveAndPlay(for: newVideo)

        // 持续向后预热下一集，确保连播或切集 0ms 秒开
        if let idx = episodes.firstIndex(where: { $0.number == episode.number }),
           idx + 1 < episodes.count {
            let nextEp = episodes[idx + 1]
            VideoPreloadManager.shared.prewarmNextEpisode(
                pageUrl: nextEp.pageUrl,
                title: "\(video.title) \(nextEp.label)",
                sourceName: video.sourceName
            )
        }
    }

    private func startPlayback(with result: PlaybackSourceResult) {
        cleanupPlayer(cancelResolveTask: false)

        // 激活音频会话
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback, options: [])
        try? AVAudioSession.sharedInstance().setActive(true)

        var httpHeaders: [String: String] = [
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        ]
        if let referer = result.referer {
            httpHeaders["Referer"] = referer
            let origin = (URL(string: referer)?.scheme.map { "\($0)://" } ?? "") + (URL(string: referer)?.host ?? "")
            if !origin.isEmpty {
                httpHeaders["Origin"] = origin
            }
        }
        for (k, v) in result.headers {
            httpHeaders[k] = v
        }

        let options: [String: Any] = [
            "AVURLAssetHTTPHeaderFieldsKey": httpHeaders
        ]

        let asset = AVURLAsset(url: result.url, options: options)
        let playerItem = AVPlayerItem(asset: asset)
        let avPlayer = AVPlayer(playerItem: playerItem)
        self.player = avPlayer

        // 关键加固：立即触发播放并启用防休眠，不挂起在异步 KVO 回调中
        avPlayer.play()
        self.isPlaying = true
        UIApplication.shared.isIdleTimerDisabled = true

        // 监听准备就绪状态
        itemStatusObserver = playerItem.observe(\.status, options: [.new, .initial]) { item, _ in
            DispatchQueue.main.async {
                guard avPlayer.currentItem === item else { return }
                if item.status == .readyToPlay {
                    self.isResolving = false
                    self.isPlaying = true
                    self.loadMediaSelectionGroups(for: item)
                    avPlayer.play()
                    self.scheduleAutoHideControls()
                } else if item.status == .failed {
                    self.isResolving = false
                    self.isPlaying = false
                    self.player = nil
                    self.resolveError = item.error?.localizedDescription ?? "视频解码失败，请重试"
                    UIApplication.shared.isIdleTimerDisabled = false
                }
            }
        }

        // 监听时长更新（适配 HLS 异步解析）
        itemDurationObserver = playerItem.observe(\.duration, options: [.new, .initial]) { item, _ in
            let d = item.duration.seconds
            if !d.isNaN && d > 1.0 {
                DispatchQueue.main.async {
                    self.duration = d
                }
            }
        }

        let interval = CMTime(seconds: 0.5, preferredTimescale: 600)
        timeObserverToken = avPlayer.addPeriodicTimeObserver(forInterval: interval, queue: .main) { time in
            guard !isSeeking, !isScrubbing else { return }
            self.currentTime = time.seconds
            self.scrubTime = time.seconds
            if let currentItem = avPlayer.currentItem {
                let d = currentItem.duration.seconds
                if !d.isNaN && d > 1.0 && self.duration != d {
                    self.duration = d
                }
            }
            // Apple TV Up Next 状态机：每 3 秒同步一次播放进度
            if Int(self.currentTime) % 3 == 0 && self.duration > 5.0 {
                PlaybackProgressStore.shared.updateProgress(
                    video: self.video,
                    positionMs: Int64(self.currentTime * 1000),
                    durationMs: Int64(self.duration * 1000)
                )
            }
        }
    }

    private func cleanupPlayer(cancelResolveTask: Bool = true) {
        UIApplication.shared.isIdleTimerDisabled = false
        if self.duration > 5.0 && self.currentTime > 2.0 {
            PlaybackProgressStore.shared.updateProgress(
                video: video,
                positionMs: Int64(currentTime * 1000),
                durationMs: Int64(duration * 1000)
            )
        }
        PlaybackProgressStore.shared.flushPendingPersist()
        if cancelResolveTask {
            resolveTask?.cancel()
            resolveTask = nil
            activeResolveID = UUID()
        }
        hideControlsTask?.cancel()
        hideControlsTask = nil
        subtitleSelectionGroup = nil
        audioSelectionGroup = nil
        itemStatusObserver?.invalidate()
        itemStatusObserver = nil
        itemDurationObserver?.invalidate()
        itemDurationObserver = nil
        if let token = timeObserverToken, let player = player {
            player.removeTimeObserver(token)
            timeObserverToken = nil
        }
        player?.pause()
        player = nil
    }

    private var canSelectMediaTracks: Bool {
        subtitleSelectionGroup?.options.isEmpty == false || (audioSelectionGroup?.options.count ?? 0) > 1
    }

    private func loadMediaSelectionGroups(for item: AVPlayerItem) {
        Task { @MainActor in
            let subtitleGroup: AVMediaSelectionGroup?
            do {
                subtitleGroup = try await item.asset.loadMediaSelectionGroup(for: .legible)
            } catch {
                print("Unable to load subtitle tracks: \(error.localizedDescription)")
                subtitleGroup = nil
            }

            let audioGroup: AVMediaSelectionGroup?
            do {
                audioGroup = try await item.asset.loadMediaSelectionGroup(for: .audible)
            } catch {
                print("Unable to load audio tracks: \(error.localizedDescription)")
                audioGroup = nil
            }

            guard player?.currentItem === item else { return }
            subtitleSelectionGroup = subtitleGroup
            audioSelectionGroup = audioGroup
        }
    }

    private func togglePlayPause() {
        guard let player = player else { return }
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        if isPlaying {
            player.pause()
            isPlaying = false
        } else {
            player.rate = playbackSpeed
            isPlaying = true
            scheduleAutoHideControls()
        }
    }

    private func toggleMute() {
        guard let p = player else { return }
        if p.volume > 0 {
            gestureInitialValue = CGFloat(p.volume)
            p.volume = 0
            showHud("静音", icon: "speaker.slash.fill")
        } else {
            p.volume = Float(gestureInitialValue > 0 ? gestureInitialValue : 0.8)
            showHud("恢复音量", icon: "speaker.wave.2.fill")
        }
    }

    private func seekBy(_ seconds: Double) {
        guard let player = player else { return }
        let target = max(0, min(duration > 0 ? duration : 3600, currentTime + seconds))
        seekTo(time: target)
    }

    private func seekTo(time: Double) {
        guard let player = player else { return }
        isSeeking = true
        let cmTime = CMTime(seconds: time, preferredTimescale: 600)
        player.seek(to: cmTime) { _ in
            DispatchQueue.main.async {
                self.isSeeking = false
            }
        }
    }

    private func toggleFillMode() {
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        withAnimation(reduceMotion ? nil : AuroraMotion.smooth) {
            isFillMode.toggle()
        }
        showHud(isFillMode ? "畫幅：滿鋪全屏（無黑邊）" : "畫幅：原片比例（完整畫面）")
    }

    private func toggleOrientation() {
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        withAnimation(reduceMotion ? nil : AuroraMotion.smooth) {
            isLandscape.toggle()
        }
        showHud(isLandscape ? "已進入橫屏沉浸模式" : "已返回豎屏常規模式")
    }

    private func showHud(_ text: String, icon: String? = nil) {
        hudTask?.cancel()
        withAnimation(reduceMotion ? nil : AuroraMotion.quick) {
            self.hudIcon = icon
            self.hudMessage = text
        }
        let task = DispatchWorkItem {
            withAnimation(reduceMotion ? nil : AuroraMotion.smooth) {
                self.hudMessage = nil
                self.hudIcon = nil
            }
        }
        hudTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.4, execute: task)
    }

    private func formatTime(_ seconds: Double) -> String {
        guard !seconds.isNaN && seconds >= 0 else { return "00:00" }
        let total = Int(seconds)
        let m = total / 60
        let s = total % 60
        return String(format: "%02d:%02d", m, s)
    }

    private func formatDuration(_ seconds: Double) -> String {
        guard !seconds.isNaN && seconds > 1.0 else { return "--:--" }
        let total = Int(seconds)
        let m = total / 60
        let s = total % 60
        return String(format: "%02d:%02d", m, s)
    }
}

private struct PlayerMediaTrackMenu: View {
    let playerItem: AVPlayerItem
    let subtitleGroup: AVMediaSelectionGroup?
    let audioGroup: AVMediaSelectionGroup?

    @State private var selectionRevision = 0

    var body: some View {
        let _ = selectionRevision
        Menu {
            if let subtitleGroup, !subtitleGroup.options.isEmpty {
                Section("字幕") {
                    Button {
                        select(nil, in: subtitleGroup)
                    } label: {
                        trackLabel("关闭", selected: selectedOption(in: subtitleGroup) == nil)
                    }

                    ForEach(Array(subtitleGroup.options.enumerated()), id: \.offset) { entry in
                        let option = entry.element
                        Button {
                            select(option, in: subtitleGroup)
                        } label: {
                            trackLabel(option.displayName, selected: isSelected(option, in: subtitleGroup))
                        }
                    }
                }
            }

            if let audioGroup, audioGroup.options.count > 1 {
                Section("音频") {
                    ForEach(Array(audioGroup.options.enumerated()), id: \.offset) { entry in
                        let option = entry.element
                        Button {
                            select(option, in: audioGroup)
                        } label: {
                            trackLabel(option.displayName, selected: isSelected(option, in: audioGroup))
                        }
                    }
                }
            }
        } label: {
            Image(systemName: selectedSubtitle != nil ? "captions.bubble.fill" : "captions.bubble")
                .font(.subheadline.bold())
                .foregroundStyle(.white)
                .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                .glassEffect(.regular.interactive(), in: .circle)
        }
        .accessibilityLabel("字幕与音轨")
        .accessibilityHint("选择可用的字幕或音频轨道")
    }

    private var selectedSubtitle: AVMediaSelectionOption? {
        guard let subtitleGroup else { return nil }
        return selectedOption(in: subtitleGroup)
    }

    private func selectedOption(in group: AVMediaSelectionGroup) -> AVMediaSelectionOption? {
        playerItem.currentMediaSelection.selectedMediaOption(in: group)
    }

    private func isSelected(_ option: AVMediaSelectionOption, in group: AVMediaSelectionGroup) -> Bool {
        selectedOption(in: group)?.isEqual(option) == true
    }

    private func select(_ option: AVMediaSelectionOption?, in group: AVMediaSelectionGroup) {
        playerItem.select(option, in: group)
        selectionRevision &+= 1
    }

    private func trackLabel(_ title: String, selected: Bool) -> some View {
        HStack {
            Text(title)
            Spacer(minLength: AuroraMetrics.spacingL)
            if selected {
                Image(systemName: "checkmark")
                    .accessibilityHidden(true)
            }
        }
    }
}
