import SwiftUI
import AVKit
import Combine

/// 影视二级详情与内嵌播放一体化界面（Apple HIG 规范：单一播放源驱动、原位无缝全屏展开、控制栏与选集联动）
public struct VideoDetailPlaybackScreen: View {
    public let initialVideo: VideoItem
    public let onDismiss: () -> Void
    
    @State private var currentVideo: VideoItem
    @State private var episodes: [PlaybackEpisode] = []
    @State private var currentEpisode: PlaybackEpisode?
    @State private var isLoadingEpisodes: Bool = false
    @State private var isDescriptionExpanded: Bool = false
    @State private var isShowingEpisodePicker: Bool = false
    
    // 全屏与画幅控制（单一播放源，零重新缓冲）
    @State private var isFullScreen: Bool = false
    @State private var isLandscape: Bool = false
    @State private var isFillMode: Bool = false
    @State private var hudMessage: String?
    @State private var hudIcon: String?
    @State private var hudProgress: Double?
    @State private var hudTask: DispatchWorkItem?
    
    // Apple TV 标志性倒计时显示（轻点可在剩余倒计时与总时长之间切换）与倍速控制
    @State private var showRemainingTime: Bool = true
    @State private var playbackRate: Float = 1.0
    @State private var isShowingInPlayerEpisodes: Bool = false
    
    // 垂直滑动手势模式（左侧亮度、右侧音量）
    private enum PlayerVerticalGestureMode {
        case brightness
        case volume
    }
    @State private var activeGestureMode: PlayerVerticalGestureMode? = nil
    @State private var gestureInitialValue: CGFloat = 0
    
    // 核心播放器状态（全程唯一实例）
    @State private var player: AVPlayer?
    @State private var isPlaying: Bool = true
    @State private var isResolving: Bool = true
    @State private var resolveStatus: String = "正在连接视频源…"
    @State private var resolveError: String? = nil
    @State private var currentTime: Double = 0
    @State private var duration: Double = 0
    @State private var showControls: Bool = true
    @State private var hideControlsTask: DispatchWorkItem?
    @State private var timeObserverToken: Any?
    @State private var itemStatusObserver: NSKeyValueObservation?
    @State private var itemDurationObserver: NSKeyValueObservation?
    @State private var didPlayToEndObserverToken: NSObjectProtocol?
    @State private var isSwitchingNextEpisode: Bool = false
    @State private var activeResolveID = UUID()
    @State private var showDownloadToast: Bool = false
    @State private var downloadToastText: String = ""
    
    @ObservedObject private var watchlist = WatchlistStore.shared
    @ObservedObject private var downloadManager = OfflineDownloadManager.shared
    @ObservedObject private var progressStore = PlaybackProgressStore.shared
    @ObservedObject private var videoRepo = VideoRepository.shared
    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    
    public init(video: VideoItem, onDismiss: @escaping () -> Void) {
        self.initialVideo = video
        self._currentVideo = State(initialValue: video)
        self.onDismiss = onDismiss
    }
    
    private var relatedVideos: [VideoItem] {
        videoRepo.feedVideos.filter { $0.id != currentVideo.id }
    }
    
    public var body: some View {
        GeometryReader { screenGeo in
            ZStack(alignment: .top) {
                AuroraColor.canvas.ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // 1. 顶部播放器视口（支持原位动态展开至全屏，同一个 AVPlayer 实例不中断）
                    topPlayerContainer(in: screenGeo)
                        .zIndex(10)
                    
                    // 2. 下方可滚动详情、选集与推荐内容（全屏时平滑折叠退场）
                    if !isFullScreen {
                        ScrollView(.vertical, showsIndicators: false) {
                            VStack(alignment: .leading, spacing: AuroraMetrics.spacingXL) {
                                // 视频主标题与信息
                                headerInfoSection
                                    .padding(.top, AuroraMetrics.spacingM)
                                
                                // 核心操作工具栏（收藏、缓存、选集、分享）
                                actionToolbarSection
                                
                                // 选集列表（多集剧集）
                                if !episodes.isEmpty || isLoadingEpisodes {
                                    episodesShelfSection
                                }
                                
                                // 剧情简介折叠区
                                synopsisSection
                                
                                // 下方其他视频推荐
                                if !relatedVideos.isEmpty {
                                    relatedVideosSection
                                }
                                
                                Spacer().frame(height: 60)
                            }
                            .padding(.horizontal, AuroraMetrics.contentInset)
                        }
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                    }
                }
                
                // 缓存状态浮动提示
                if showDownloadToast {
                    VStack {
                        Spacer().frame(height: 240)
                        HStack(spacing: 8) {
                            Image(systemName: "arrow.down.circle.fill")
                                .foregroundStyle(AuroraColor.brand)
                            Text(downloadToastText)
                                .font(.subheadline.bold())
                                .foregroundStyle(.white)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(.ultraThinMaterial, in: Capsule())
                        .overlay(Capsule().stroke(AuroraColor.hairline, lineWidth: 1))
                        .shadow(color: .black.opacity(0.3), radius: 10, y: 5)
                        .transition(.move(edge: .top).combined(with: .opacity))
                        Spacer()
                    }
                    .zIndex(20)
                }
                
                // HUD 提示胶囊（画幅模式、旋转模式等）
                if let hud = hudMessage {
                    Text(hud)
                        .font(.callout.bold())
                        .foregroundStyle(.white)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 10)
                        .background(.ultraThinMaterial, in: Capsule())
                        .overlay(Capsule().stroke(Color.white.opacity(0.2), lineWidth: 1))
                        .shadow(color: .black.opacity(0.5), radius: 8, y: 3)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                        .transition(.scale(scale: 0.9).combined(with: .opacity))
                        .zIndex(30)
                }
            }
        }
        .ignoresSafeArea()
        .toolbar(.hidden, for: .tabBar)
        .toolbar(.hidden, for: .navigationBar)
        .navigationBarHidden(true)
        .navigationBarBackButtonHidden(true)
        .preferredColorScheme(.dark)
        .statusBarHidden(isFullScreen ? (!showControls || isLandscape) : false)
        .persistentSystemOverlays(isFullScreen && !showControls ? .hidden : .automatic)
        .task(id: currentVideo.playbackUrl) {
            await startPlayback(for: currentVideo)
        }
        .task(id: currentVideo.id) {
            await loadEpisodes(for: currentVideo)
        }
        .onAppear {
            let args = CommandLine.arguments
            if args.contains("--detail-fullscreen") {
                isFullScreen = true
                scheduleHideControls()
            }
            if args.contains("--detail-landscape") {
                isFullScreen = true
                isLandscape = true
                scheduleHideControls()
            }
            if args.contains("--detail-fill") {
                isFillMode = true
            }
        }
        .onDisappear {
            cleanupPlayer()
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
        .enableSwipeBack(isEnabled: !isFullScreen) {
            cleanupPlayer()
            onDismiss()
            dismiss()
        }
    }
    
    // MARK: - 1. 顶部播放器视口（内嵌 / 全屏通用）
    
    @ViewBuilder
    private func topPlayerContainer(in screenGeo: GeometryProxy) -> some View {
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        let isPhysicallyLandscape = screenGeo.size.width > screenGeo.size.height
        // iPad 端依靠机身感应全向自然旋转，严禁施加 90 度强制逆向视图旋转
        let isRotated = !isPad && isFullScreen && isLandscape && !isPhysicallyLandscape
        let targetWidth = isRotated ? screenGeo.size.height : screenGeo.size.width
        let safeTop = DeviceLayout.safeAreaTop(in: screenGeo.size)
        let needsTopOffset = !isFullScreen && !isLandscape && !isPad
        let targetHeight: CGFloat = {
            if isFullScreen {
                return isRotated ? screenGeo.size.width : screenGeo.size.height
            }
            if isPad {
                // iPad 宽屏内嵌态：严格限制播放器黄金视口高度，留出 >= 50% 竖向空间展现选集与简介，根治下半部被挤压问题
                return min(screenGeo.size.height * 0.48, 440)
            }
            return (screenGeo.size.width * 9.0 / 16.0) + safeTop
        }()
        
        ZStack {
            Color.black
            
            VStack(spacing: 0) {
                // 非全屏竖屏嵌入态：顶部垫入 safeTop 高度的纯黑安全区隔离底衬，使视频物理起始于灵动岛/状态栏下方，彻底杜绝向上位移
                if needsTopOffset {
                    Color.black.frame(height: safeTop)
                }

                ZStack {
                    // 底层海报背景（加载或失败时垫底）
                    if player == nil {
                        AuroraCoverImage(
                            url: currentVideo.coverUrl,
                            title: currentVideo.title,
                            sourceBaseURL: currentVideo.coverBaseURL,
                            aspectRatio: 16.0 / 9.0,
                            contentMode: .fill,
                            cornerRadius: 0
                        )
                        .overlay(Color.black.opacity(0.45))
                    }
                    
                    // 原生 AVPlayer 渲染视口（支持 Aspect Fit 与 Aspect Fill 满铺）
                    if let activePlayer = player {
                        AuroraPlayerSurface(
                            player: activePlayer,
                            videoGravity: isFillMode ? .resizeAspectFill : .resizeAspect
                        )
                        .onTapGesture(count: 2) {
                            toggleFillMode()
                        }
                        .onTapGesture(count: 1) {
                            withAnimation(.easeInOut(duration: 0.2)) {
                                showControls.toggle()
                            }
                            if showControls {
                                scheduleHideControls()
                            } else {
                                hideControlsTask?.cancel()
                            }
                        }
                    }
                    
                    // 解析失败提示层
                    if let error = resolveError {
                        VStack(spacing: 12) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.system(size: 32))
                                .foregroundStyle(AuroraColor.warning)
                            Text("播放失败")
                                .font(.headline)
                                .foregroundStyle(.white)
                            Text(error)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 24)
                            
                            HStack(spacing: 12) {
                                Button("重试") {
                                    Task { await startPlayback(for: currentVideo) }
                                }
                                .buttonStyle(.borderedProminent)
                                .tint(AuroraColor.brand)
                            }
                        }
                        .padding()
                    } else if isResolving {
                        // 正在解析/预热加载层
                        VStack(spacing: 10) {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: AuroraColor.brand))
                                .scaleEffect(1.1)
                            Text(resolveStatus)
                                .font(.caption.bold())
                                .foregroundStyle(.white)
                                .shadow(radius: 2)
                        }
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            
            // 播放器控制覆盖层（Apple HIG 悬浮胶囊与进度条）
            if showControls && resolveError == nil {
                playerControlsOverlay(in: screenGeo, isRotated: isRotated)
                    .transition(.opacity)
            }
        }
        .frame(width: targetWidth, height: targetHeight)
        .rotationEffect(isRotated ? .degrees(90) : .zero)
        .frame(
            width: isFullScreen ? screenGeo.size.width : screenGeo.size.width,
            height: isFullScreen ? screenGeo.size.height : targetHeight,
            alignment: .top
        )
        .clipped()
        .ignoresSafeArea()
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: isFullScreen)
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: isLandscape)
    }
    
    // MARK: - 播放器控制栏（内嵌 / 全屏自适应）
    
    @ViewBuilder
    private func playerControlsOverlay(in screenGeo: GeometryProxy, isRotated: Bool) -> some View {
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        let safeTop = DeviceLayout.safeAreaTop(in: screenGeo.size)
        // 顶栏无论内嵌还是全屏，均必须刚性避让 safeTop（灵动岛/状态栏 59pt），杜绝按钮飞出屏幕或陷入灵动岛
        let topBarPadding: CGFloat = isRotated ? 16 : (isPad ? (isFullScreen ? 24 : max(safeTop, 24) + 8) : (safeTop + 8))
        let topGradientHeight: CGFloat = isRotated ? 100 : max(topBarPadding + 54, 115)
        let bottomGradientHeight: CGFloat = isRotated ? 90 : (isFullScreen ? max(110, DeviceLayout.safeAreaBottom + 70) : (isPad ? 85 : 75))

        ZStack {
            // 背景全屏轻触与 Apple TV 双侧滑动手势交互层
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture(count: 2) {
                    toggleFillMode()
                }
                .onTapGesture(count: 1) {
                    withAnimation(.easeInOut(duration: 0.25)) {
                        showControls = false
                        isShowingInPlayerEpisodes = false
                    }
                    hideControlsTask?.cancel()
                }
                .simultaneousGesture(
                    DragGesture(minimumDistance: 15)
                        .onChanged { value in
                            guard isFullScreen else { return }
                            let screenWidth = screenGeo.size.width
                            let startX = value.startLocation.x
                            if activeGestureMode == nil {
                                if startX < screenWidth * 0.40 {
                                    activeGestureMode = .brightness
                                    let activeScreen = (UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first(where: { $0.activationState == .foregroundActive }) ?? UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)?.screen
                                    gestureInitialValue = activeScreen?.brightness ?? 0.5
                                } else if startX > screenWidth * 0.60 {
                                    activeGestureMode = .volume
                                    gestureInitialValue = CGFloat(player?.volume ?? 1.0)
                                }
                            }
                            let deltaY = -value.translation.height / (isPad ? 280.0 : 200.0)
                            if activeGestureMode == .brightness {
                                let newBrightness = min(max(gestureInitialValue + deltaY, 0.0), 1.0)
                                let activeScreen = (UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first(where: { $0.activationState == .foregroundActive }) ?? UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)?.screen
                                activeScreen?.brightness = newBrightness
                                showHud(icon: "sun.max.fill", text: "亮度", progress: Double(newBrightness))
                            } else if activeGestureMode == .volume {
                                let newVolume = min(max(gestureInitialValue + deltaY, 0.0), 1.0)
                                player?.volume = Float(newVolume)
                                let icon = newVolume > 0.5 ? "speaker.wave.3.fill" : (newVolume > 0 ? "speaker.wave.1.fill" : "speaker.slash.fill")
                                showHud(icon: icon, text: "音量", progress: Double(newVolume))
                            }
                        }
                        .onEnded { value in
                            if activeGestureMode != nil {
                                activeGestureMode = nil
                                return
                            }
                            if isFullScreen {
                                let isSwipeDown = value.translation.height > 60 && abs(value.translation.height) > abs(value.translation.width) * 1.2
                                let isSwipeRight = value.translation.width > 80 && abs(value.translation.width) > abs(value.translation.height) * 1.2
                                if isSwipeDown || isSwipeRight {
                                    let impact = UIImpactFeedbackGenerator(style: .medium)
                                    impact.impactOccurred()
                                    withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                                        isFullScreen = false
                                        isLandscape = false
                                        isShowingInPlayerEpisodes = false
                                    }
                                    scheduleHideControls()
                                }
                            }
                        }
                )

            // 渐变可读性遮罩（顶部吸顶、底部吸底，中间完全通透，彻底根除画面中央割裂横线与色块）
            VStack(spacing: 0) {
                LinearGradient(
                    colors: [Color.black.opacity(0.85), Color.black.opacity(0.35), Color.clear],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: topGradientHeight)

                Spacer()

                LinearGradient(
                    colors: [Color.clear, Color.black.opacity(0.35), Color.black.opacity(0.85)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: bottomGradientHeight)
            }
            .allowsHitTesting(false)
            .ignoresSafeArea()
            
            VStack {
                // 顶部工具栏
                topControlBar(isRotated: isRotated)
                
                Spacer()
                
                // 中央控制区（快退 15s + 播放/暂停 + 快进 15s）
                if let _ = player, !isResolving {
                    centerControlGroup
                }
                
                Spacer()
                
                // 全屏快速选集横向浮动抽屉
                if isFullScreen && isShowingInPlayerEpisodes && episodes.count > 1 {
                    inPlayerEpisodesDrawer
                }
                
                // 底部进度条与时间控制
                bottomControlBar
            }
            .padding(.horizontal, isPad ? 24 : 14)
            .padding(.top, topBarPadding)
            .padding(.bottom, isFullScreen ? max(16, DeviceLayout.safeAreaBottom) : (isPad ? 14 : 8))
            
            // Apple TV 悬浮动态 HUD
            if let msg = hudMessage {
                VStack(spacing: 8) {
                    if let icon = hudIcon {
                        Image(systemName: icon)
                            .font(.system(size: 26, weight: .bold))
                            .foregroundStyle(.white)
                    }
                    Text(msg)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(.white)
                    if let prog = hudProgress {
                        GeometryReader { pGeo in
                            ZStack(alignment: .leading) {
                                Capsule()
                                    .fill(Color.white.opacity(0.25))
                                Capsule()
                                    .fill(Color.white)
                                    .frame(width: max(0, pGeo.size.width * CGFloat(prog)))
                            }
                        }
                        .frame(width: 110, height: 4)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 14)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(Color.white.opacity(0.2), lineWidth: 1)
                )
                .shadow(color: .black.opacity(0.4), radius: 14, y: 5)
                .transition(.scale.combined(with: .opacity))
            }
        }
    }
    
    // 顶部控制条（Apple TV 标准布局）
    private func topControlBar(isRotated: Bool) -> some View {
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        return HStack(spacing: isPad ? 16 : 12) {
            if isFullScreen {
                // 全屏退出折叠按钮（Apple TV 标准向下折叠箭头，留出充足间距防文字重叠）
                Button {
                    let impact = UIImpactFeedbackGenerator(style: .medium)
                    impact.impactOccurred()
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                        isFullScreen = false
                        isLandscape = false
                        isShowingInPlayerEpisodes = false
                    }
                    scheduleHideControls()
                } label: {
                    Image(systemName: "chevron.down")
                        .font(.system(size: isPad ? 18 : 16, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: isPad ? 44 : 38, height: isPad ? 44 : 38)
                        .background(.ultraThinMaterial, in: Circle())
                        .glassEffect(.regular.interactive(), in: .circle)
                        .contentShape(Rectangle())
                        .frame(minWidth: 44, minHeight: 44)
                }
                .buttonStyle(.plain)
                .contentShape(Rectangle())
                .accessibilityLabel("退出全屏")
                
                VStack(alignment: .leading, spacing: 3) {
                    Text(currentVideo.title)
                        .font(isPad ? .title3.bold() : .headline.bold())
                        .foregroundStyle(.white)
                        .lineLimit(1)
                    
                    HStack(spacing: 8) {
                        if let ep = currentEpisode {
                            Text(ep.label)
                                .font(.caption2.bold())
                                .foregroundStyle(.white)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(AuroraColor.brand, in: Capsule())
                        }
                        
                        Text(currentVideo.sourceName)
                            .font(.caption2.bold())
                            .foregroundStyle(AuroraColor.brandLabel)
                        
                        Text("4K HDR")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(.white.opacity(0.85))
                            .padding(.horizontal, 5)
                            .padding(.vertical, 1)
                            .background(Color.white.opacity(0.16), in: RoundedRectangle(cornerRadius: 3))
                    }
                }
                .padding(.leading, 4)
            } else {
                // 内嵌模式：返回上一层页面按钮（iPad 端显示醒目胶囊微按钮）
                Button {
                    let impact = UIImpactFeedbackGenerator(style: .medium)
                    impact.impactOccurred()
                    cleanupPlayer()
                    onDismiss()
                    dismiss()
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "chevron.backward")
                            .font(.system(size: isPad ? 17 : 16, weight: .bold))
                        if isPad {
                            Text("返回")
                                .font(.subheadline.bold())
                        }
                    }
                    .foregroundStyle(.white)
                    .frame(height: isPad ? 42 : 38)
                    .padding(.horizontal, isPad ? 16 : 0)
                    .frame(width: isPad ? nil : 38)
                    .background(.ultraThinMaterial, in: Capsule())
                    .glassEffect(.regular.interactive(), in: .capsule)
                    .contentShape(Rectangle())
                    .frame(minWidth: isPad ? 54 : 44, minHeight: 44)
                }
                .buttonStyle(.plain)
                .contentShape(Rectangle())
                .accessibilityLabel("返回")
            }
            
            Spacer()
            
            // 全屏倍速控制菜单（0.5x ~ 2.0x）
            if isFullScreen {
                Menu {
                    ForEach([0.5, 0.75, 1.0, 1.25, 1.5, 2.0], id: \.self) { speed in
                        Button {
                            let impact = UIImpactFeedbackGenerator(style: .light)
                            impact.impactOccurred()
                            playbackRate = Float(speed)
                            player?.rate = Float(speed)
                            showHud(
                                icon: "gauge.with.dots.needle.bottom.50percent",
                                text: "\(speed == 1.0 ? "1.0" : String(format: "%.2g", speed))x 倍速"
                            )
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
                        .font(.system(size: isPad ? 14 : 12, weight: .bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, isPad ? 12 : 10)
                        .padding(.vertical, isPad ? 8 : 6)
                        .background(.ultraThinMaterial, in: Capsule())
                        .overlay(Capsule().stroke(Color.white.opacity(0.18), lineWidth: 1))
                        .frame(minWidth: 44, minHeight: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("播放倍速: \(playbackRate)x")
            }
            
            // AirPlay 原生路由选择器
            AirPlayRoutePicker()
                .frame(width: isPad ? 44 : 38, height: isPad ? 44 : 38)
                .background(.ultraThinMaterial, in: Circle())
                .frame(minWidth: 44, minHeight: 44)
            
            if isFullScreen {
                // 画幅切换（Aspect Fit / Aspect Fill 满铺）
                Button {
                    toggleFillMode()
                } label: {
                    Image(systemName: isFillMode ? "arrow.down.forward.and.arrow.up.backward" : "arrow.up.left.and.arrow.down.right")
                        .font(.system(size: isPad ? 15 : 13, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: isPad ? 44 : 38, height: isPad ? 44 : 38)
                        .background(.ultraThinMaterial, in: Circle())
                        .contentShape(Rectangle())
                        .frame(minWidth: 44, minHeight: 44)
                }
                .buttonStyle(.plain)
                .contentShape(Rectangle())
                .accessibilityLabel(isFillMode ? "原片比例" : "满铺全屏")
                
                // 旋转切换（仅 iPhone 端提供锁定横竖屏按钮；iPad 端由机身物理重力全向感应）
                if !isPad {
                    Button {
                        toggleOrientation()
                    } label: {
                        Image(systemName: isLandscape ? "iphone.portrait" : "iphone.landscape")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 38, height: 38)
                            .background(.ultraThinMaterial, in: Circle())
                            .contentShape(Rectangle())
                            .frame(minWidth: 44, minHeight: 44)
                    }
                    .buttonStyle(.plain)
                    .contentShape(Rectangle())
                    .accessibilityLabel(isLandscape ? "竖屏模式" : "横屏沉浸")
                }
            } else {
                // 内嵌模式：展开为全屏大画幅
                Button {
                    let impact = UIImpactFeedbackGenerator(style: .medium)
                    impact.impactOccurred()
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                        isFullScreen = true
                    }
                    scheduleHideControls()
                } label: {
                    Image(systemName: "arrow.up.left.and.arrow.down.right")
                        .font(.system(size: isPad ? 16 : 14, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: isPad ? 44 : 38, height: isPad ? 44 : 38)
                        .background(.ultraThinMaterial, in: Circle())
                        .contentShape(Rectangle())
                        .frame(minWidth: isPad ? 48 : 44, minHeight: 44)
                }
                .buttonStyle(.plain)
                .contentShape(Rectangle())
                .accessibilityLabel("全屏播放")
            }
        }
    }
    
    // 中央控制区（Apple TV 影院级大尺寸控制器）
    private var centerControlGroup: some View {
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        let rewindForwardSize: CGFloat = isPad ? 60 : (isFullScreen ? 48 : 40)
        let playPauseSize: CGFloat = isPad ? 80 : (isFullScreen ? 64 : 54)
        let groupSpacing: CGFloat = isPad ? 60 : (isFullScreen ? 40 : 28)

        return HStack(spacing: groupSpacing) {
            // 快退 15 秒
            Button {
                seekRelative(-15)
            } label: {
                Image(systemName: "gobackward.15")
                    .font(.system(size: isPad ? 28 : (isFullScreen ? 26 : 20), weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: rewindForwardSize, height: rewindForwardSize)
                    .background(.ultraThinMaterial, in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("快退 15 秒")
            
            // 播放/暂停主按钮
            Button {
                togglePlayPause()
            } label: {
                Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: isPad ? 36 : (isFullScreen ? 32 : 26), weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: playPauseSize, height: playPauseSize)
                    .background(AuroraColor.brand.opacity(0.92), in: Circle())
                    .shadow(color: .black.opacity(0.5), radius: 10, y: 4)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isPlaying ? "暂停" : "播放")
            
            // 快进 15 秒
            Button {
                seekRelative(15)
            } label: {
                Image(systemName: "goforward.15")
                    .font(.system(size: isPad ? 28 : (isFullScreen ? 26 : 20), weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: rewindForwardSize, height: rewindForwardSize)
                    .background(.ultraThinMaterial, in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("快进 15 秒")
        }
    }
    
    // 全屏快速选集横向浮动抽屉
    @ViewBuilder
    private var inPlayerEpisodesDrawer: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("快速跳集")
                    .font(.subheadline.bold())
                    .foregroundStyle(.white)
                Spacer()
                Button {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                        isShowingInPlayerEpisodes = false
                    }
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 18))
                        .foregroundStyle(.white.opacity(0.6))
                }
            }
            .padding(.horizontal, 16)
            
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 12) {
                    ForEach(episodes) { ep in
                        let isSelected = (currentEpisode?.number == ep.number)
                        Button {
                            switchEpisode(ep)
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                                isShowingInPlayerEpisodes = false
                            }
                        } label: {
                            HStack(spacing: 6) {
                                if isSelected {
                                    Image(systemName: "play.circle.fill")
                                        .font(.system(size: 13))
                                }
                                Text(ep.label)
                                    .font(.system(size: 13, weight: isSelected ? .bold : .medium))
                            }
                            .foregroundStyle(isSelected ? .black : .white)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(isSelected ? Color.white : Color.black.opacity(0.4), in: Capsule())
                            .overlay(Capsule().stroke(isSelected ? Color.white : Color.white.opacity(0.2), lineWidth: 1))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
            }
        }
        .padding(.vertical, 12)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Color.white.opacity(0.15), lineWidth: 1))
        .shadow(color: .black.opacity(0.4), radius: 16, y: 6)
        .padding(.horizontal, 20)
        .padding(.bottom, 8)
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }
    
    // 底部控制条（Apple TV 倒计时时间与快捷抽屉）
    private var bottomControlBar: some View {
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        return HStack(spacing: isPad ? 16 : 10) {
            Text(formatTime(currentTime))
                .font(.system(size: isPad ? 14 : 11, weight: .medium, design: .monospaced))
                .foregroundStyle(.white)
            
            // 自研 Apple HIG 进度条：使用局部手势坐标，解决旋转后的坐标错位问题
            PlayerScrubberBar(
                currentTime: $currentTime,
                duration: duration,
                onSeek: { targetTime in
                    seekTo(time: targetTime)
                    scheduleHideControls()
                }
            )
            .frame(height: isPad ? 44 : 36)
            
            // Apple TV 标志性倒计时显示：点击切换剩余倒计时与总时长
            Button {
                showRemainingTime.toggle()
            } label: {
                Text(showRemainingTime ? formatRemainingTime(currentTime, duration: duration) : formatTime(duration))
                    .font(.system(size: isPad ? 14 : 11, weight: .medium, design: .monospaced))
                    .foregroundStyle(.white.opacity(0.85))
                    .frame(minWidth: isPad ? 50 : 42, alignment: .trailing)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(showRemainingTime ? "切换至总时长" : "切换至倒计时")
            
            // 下一集快捷跳转按钮
            if isFullScreen, let currentEp = currentEpisode, let nextEp = episodes.first(where: { $0.number == currentEp.number + 1 }) {
                Button {
                    let impact = UIImpactFeedbackGenerator(style: .medium)
                    impact.impactOccurred()
                    switchEpisode(nextEp)
                } label: {
                    Image(systemName: "forward.end.fill")
                        .font(.system(size: isPad ? 15 : 13, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: isPad ? 38 : 32, height: isPad ? 38 : 32)
                        .background(.ultraThinMaterial, in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("播放下一集")
            }
            
            // 全屏状态下的快捷选集按钮
            if isFullScreen && episodes.count > 1 {
                Button {
                    let impact = UIImpactFeedbackGenerator(style: .medium)
                    impact.impactOccurred()
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                        isShowingInPlayerEpisodes.toggle()
                    }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "square.stack.3d.down.right")
                            .font(.system(size: isPad ? 14 : 13, weight: .bold))
                        if isPad {
                            Text("选集")
                                .font(.caption.bold())
                        }
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, isPad ? 14 : 10)
                    .padding(.vertical, isPad ? 8 : 6)
                    .background(isShowingInPlayerEpisodes ? AuroraColor.brand : Color.white.opacity(0.18), in: Capsule())
                    .overlay(Capsule().stroke(Color.white.opacity(0.2), lineWidth: 1))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("切换集数")
            }
        }
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
                
                Text("4K HDR")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.white.opacity(0.9))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.white.opacity(0.14), in: RoundedRectangle(cornerRadius: 4))
                
                Text("杜比视界")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.white.opacity(0.8))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.white.opacity(0.10), in: RoundedRectangle(cornerRadius: 4))
                
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
        }
    }
    
    // MARK: - 3. 核心操作工具栏（iPad 端 Apple TV 标志性白底主播放大胶囊 + 毛玻璃微晶组）
    
    @ViewBuilder
    private var actionToolbarSection: some View {
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        let isBookmarked = watchlist.contains(currentVideo)
        let downloadStatus = downloadManager.status(for: currentVideo, episode: currentEpisode)
        let isDownloaded = downloadManager.isDownloaded(video: currentVideo, episode: currentEpisode)
        
        if isPad {
            HStack(spacing: 16) {
                // Apple TV 标志性白底黑字主播放大胶囊
                Button {
                    let impact = UIImpactFeedbackGenerator(style: .medium)
                    impact.impactOccurred()
                    if isPlaying {
                        togglePlayPause()
                    } else {
                        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                            isFullScreen = true
                        }
                    }
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                            .font(.system(size: 16, weight: .bold))
                        Text(isPlaying ? "正在播放" : (currentEpisode != nil ? "播放 \(currentEpisode!.label)" : "立即播放"))
                            .font(.system(size: 16, weight: .bold))
                    }
                    .foregroundStyle(.black)
                    .frame(height: 48)
                    .padding(.horizontal, 28)
                    .background(Color.white, in: Capsule())
                    .shadow(color: .white.opacity(0.2), radius: 8, y: 2)
                }
                .buttonStyle(AppleSpringCardButtonStyle())
                
                // 加入片单
                Button {
                    let impact = UIImpactFeedbackGenerator(style: .medium)
                    impact.impactOccurred()
                    try? watchlist.setBookmarked(!isBookmarked, for: currentVideo)
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: isBookmarked ? "bookmark.fill" : "plus")
                            .font(.system(size: 15, weight: .bold))
                        Text(isBookmarked ? "已在片单" : "加入片单")
                            .font(.system(size: 14, weight: .semibold))
                    }
                    .foregroundStyle(isBookmarked ? AuroraColor.brand : .white)
                    .frame(height: 48)
                    .padding(.horizontal, 18)
                    .background(.ultraThinMaterial, in: Capsule())
                    .overlay(Capsule().stroke(Color.white.opacity(0.15), lineWidth: 1))
                }
                .buttonStyle(AppleSpringCardButtonStyle())
                
                // 离线缓存
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
                            Text("缓存")
                        }
                    }
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(height: 48)
                    .padding(.horizontal, 18)
                    .background(.ultraThinMaterial, in: Capsule())
                    .overlay(Capsule().stroke(Color.white.opacity(0.15), lineWidth: 1))
                }
                .buttonStyle(AppleSpringCardButtonStyle())
                
                // 选集入口（多集时可见）
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
                        .foregroundStyle(.white)
                        .frame(height: 48)
                        .padding(.horizontal, 18)
                        .background(.ultraThinMaterial, in: Capsule())
                        .overlay(Capsule().stroke(Color.white.opacity(0.15), lineWidth: 1))
                    }
                    .buttonStyle(AppleSpringCardButtonStyle())
                }
                
                // 分享
                if let shareURL = URL(string: currentVideo.playbackUrl), !currentVideo.playbackUrl.isEmpty {
                    ShareLink(item: shareURL) {
                        HStack(spacing: 6) {
                            Image(systemName: "square.and.arrow.up")
                                .font(.system(size: 14, weight: .bold))
                            Text("分享")
                                .font(.system(size: 14, weight: .semibold))
                        }
                        .foregroundStyle(.white)
                        .frame(height: 48)
                        .padding(.horizontal, 18)
                        .background(.ultraThinMaterial, in: Capsule())
                        .overlay(Capsule().stroke(Color.white.opacity(0.15), lineWidth: 1))
                    }
                    .buttonStyle(AppleSpringCardButtonStyle())
                }
                
                Spacer()
            }
        } else {
            HStack(spacing: 12) {
                // 收藏按钮
                Button {
                    let impact = UIImpactFeedbackGenerator(style: .medium)
                    impact.impactOccurred()
                    try? watchlist.setBookmarked(!isBookmarked, for: currentVideo)
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: isBookmarked ? "bookmark.fill" : "bookmark")
                            .font(.title3)
                            .foregroundStyle(isBookmarked ? AuroraColor.brand : AuroraColor.textPrimary)
                        Text(isBookmarked ? "已收藏" : "收藏")
                            .font(.caption2)
                            .foregroundStyle(isBookmarked ? AuroraColor.brand : AuroraColor.textSecondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
                .buttonStyle(.plain)
                
                // 缓存下载按钮
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
                    VStack(spacing: 4) {
                        if isDownloaded {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.title3)
                                .foregroundStyle(.green)
                            Text("已缓存")
                                .font(.caption2)
                                .foregroundStyle(.green)
                        } else if let status = downloadStatus, status.isDownloading {
                            ProgressView(value: status.progressValue)
                                .frame(width: 24, height: 24)
                                .tint(AuroraColor.brand)
                            Text("\(Int(status.progressValue * 100))%")
                                .font(.caption2)
                                .foregroundStyle(AuroraColor.brand)
                        } else {
                            Image(systemName: "arrow.down.circle")
                                .font(.title3)
                                .foregroundStyle(AuroraColor.textPrimary)
                            Text("缓存")
                                .font(.caption2)
                                .foregroundStyle(AuroraColor.textSecondary)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
                .buttonStyle(.plain)
                
                // 选集入口（多集时可见）
                if episodes.count > 1 {
                    Button {
                        isShowingEpisodePicker = true
                    } label: {
                        VStack(spacing: 4) {
                            Image(systemName: "square.stack.3d.down.right")
                                .font(.title3)
                                .foregroundStyle(AuroraColor.textPrimary)
                            Text("选集(\(episodes.count))")
                                .font(.caption2)
                                .foregroundStyle(AuroraColor.textSecondary)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
                
                // 原生分享
                if let shareURL = URL(string: currentVideo.playbackUrl), !currentVideo.playbackUrl.isEmpty {
                    ShareLink(item: shareURL) {
                        VStack(spacing: 4) {
                            Image(systemName: "square.and.arrow.up")
                                .font(.title3)
                                .foregroundStyle(AuroraColor.textPrimary)
                            Text("分享")
                                .font(.caption2)
                                .foregroundStyle(AuroraColor.textSecondary)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
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
                HStack(spacing: AuroraMetrics.spacingM) {
                    ForEach(0..<4, id: \.self) { _ in
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(AuroraColor.surfaceCard.opacity(0.6))
                            .frame(width: isPad ? 250 : 110, height: isPad ? 140 : 56)
                    }
                }
            } else if isPad {
                // iPad 专属：Apple TV 16:9 横版高清剧照大卡片货架
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(spacing: 16) {
                        ForEach(episodes) { ep in
                            let isSelected = (currentEpisode?.number == ep.number)
                            Button {
                                switchEpisode(ep)
                            } label: {
                                VStack(alignment: .leading, spacing: 8) {
                                    // 16:9 剧照与状态微晶
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
                                        
                                        // 渐变暗角遮罩
                                        LinearGradient(
                                            colors: [Color.clear, Color.black.opacity(0.65)],
                                            startPoint: .center,
                                            endPoint: .bottom
                                        )
                                        
                                        // 左上角集数标
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
                                        
                                        // 选中播放指示（正在播放时在中央浮起高亮动效）
                                        if isSelected {
                                            ZStack {
                                                Color.black.opacity(0.4)
                                                Circle()
                                                    .fill(AuroraColor.brand)
                                                    .frame(width: 44, height: 44)
                                                Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                                                    .font(.system(size: 18, weight: .bold))
                                                    .foregroundStyle(.white)
                                            }
                                        }
                                    }
                                    .frame(width: 250, height: 140)
                                    .cornerRadius(12)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                                            .stroke(isSelected ? Color.white : AuroraColor.hairline, lineWidth: isSelected ? 2 : 1)
                                    )
                                    .shadow(color: isSelected ? Color.white.opacity(0.2) : Color.black.opacity(0.3), radius: isSelected ? 10 : 4, y: 3)
                                    
                                    // 卡片下方标题与描述
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
            } else {
                // iPhone 手机端紧凑按钮
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
            }
        }
    }
    
    // MARK: - 5. 剧情简介折叠区
    
    private var synopsisSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("简介")
                .font(.headline)
                .foregroundStyle(AuroraColor.textPrimary)
            
            let description = currentVideo.subtitle.isEmpty ? "本片由 \(currentVideo.sourceName) 官方源直连分发，支持 4K 超清硬解出画与离线缓存下载。" : currentVideo.subtitle
            Text(description)
                .font(.subheadline)
                .foregroundStyle(AuroraColor.textSecondary)
                .lineLimit(isDescriptionExpanded ? nil : 3)
            
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
    
    // MARK: - 6. 下方其他视频推荐（iPad 端 4 列自适应宽屏网格）
    
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
    
    // MARK: - 核心播放调度引擎（单一实例，零重新缓冲）
    
    private func startPlayback(for video: VideoItem) async {
        cleanupPlayer()
        let resolveID = UUID()
        activeResolveID = resolveID
        isResolving = true
        resolveError = nil
        resolveStatus = "正在极速秒开…"
        
        // 优先检查本地离线缓存
        if let localURL = downloadManager.localPlaybackURL(for: video, episode: currentEpisode) {
            resolveStatus = "从离线缓存中加载…"
            initPlayer(url: localURL, referer: nil)
            isResolving = false
            return
        }
        
        do {
            let result = try await VideoRepository.shared.resolvePlayback(video: video)
            guard activeResolveID == resolveID else { return }
            initPlayer(url: result.url, referer: result.referer, headers: result.headers)
            isResolving = false
        } catch {
            guard activeResolveID == resolveID else { return }
            resolveError = error.localizedDescription
            isResolving = false
        }
    }
    
    private func initPlayer(url: URL, referer: String?, headers: [String: String] = [:]) {
        var assetOptions: [String: Any] = [:]
        var httpHeaders: [String: String] = [
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)"
        ]
        if let ref = referer, let refURL = URL(string: ref) {
            httpHeaders["Referer"] = refURL.absoluteString
            let origin = (refURL.scheme.map { "\($0)://" } ?? "") + (refURL.host ?? "")
            if !origin.isEmpty {
                httpHeaders["Origin"] = origin
            }
        }
        for (k, v) in headers {
            httpHeaders[k] = v
        }
        assetOptions["AVURLAssetHTTPHeaderFieldsKey"] = httpHeaders
        let asset = AVURLAsset(url: url, options: assetOptions)
        let playerItem = AVPlayerItem(asset: asset)
        
        let p = AVPlayer(playerItem: playerItem)
        self.player = p
        
        // 观察时长
        itemDurationObserver = playerItem.observe(\.duration, options: [.new]) { [self] item, _ in
            let seconds = CMTimeGetSeconds(item.duration)
            if seconds.isFinite && seconds > 0 {
                DispatchQueue.main.async {
                    self.duration = seconds
                }
            }
        }
        
        // 观察状态
        itemStatusObserver = playerItem.observe(\.status, options: [.new]) { [self] item, _ in
            DispatchQueue.main.async {
                if item.status == .readyToPlay {
                    self.isPlaying = true
                    p.play()
                    UIApplication.shared.isIdleTimerDisabled = true
                    self.scheduleHideControls()
                } else if item.status == .failed {
                    self.resolveError = item.error?.localizedDescription ?? "播放器解码失败"
                    UIApplication.shared.isIdleTimerDisabled = false
                }
            }
        }
        
        // 进度观察与末尾连播保底
        let interval = CMTime(seconds: 0.5, preferredTimescale: CMTimeScale(NSEC_PER_SEC))
        timeObserverToken = p.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [self] time in
            let cur = CMTimeGetSeconds(time)
            self.currentTime = cur
            if self.duration > 3.0 && cur >= self.duration - 0.4 {
                self.handleEpisodePlaybackEnded()
            }
        }
        
        // 观察播放自然结束通知
        if let oldToken = didPlayToEndObserverToken {
            NotificationCenter.default.removeObserver(oldToken)
            didPlayToEndObserverToken = nil
        }
        didPlayToEndObserverToken = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: playerItem,
            queue: .main
        ) { [self] _ in
            self.handleEpisodePlaybackEnded()
        }
        
        scheduleHideControls()
    }
    
    private func togglePlayPause() {
        guard let p = player else { return }
        if isPlaying {
            p.pause()
            isPlaying = false
            hideControlsTask?.cancel()
        } else {
            p.play()
            isPlaying = true
            scheduleHideControls()
        }
    }
    
    private func seekTo(time: Double) {
        guard let p = player else { return }
        let cmTime = CMTime(seconds: time, preferredTimescale: 600)
        p.seek(to: cmTime, toleranceBefore: .zero, toleranceAfter: .zero)
    }
    
    private func seekRelative(_ delta: Double) {
        let target = min(max(currentTime + delta, 0), duration)
        seekTo(time: target)
        scheduleHideControls()
    }
    
    private func toggleFillMode() {
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
            isFillMode.toggle()
        }
        showHud(
            icon: isFillMode ? "arrow.down.forward.and.arrow.up.backward" : "arrow.up.left.and.arrow.down.right",
            text: isFillMode ? "填满屏幕" : "适合屏幕"
        )
        scheduleHideControls()
    }
    
    private func toggleOrientation() {
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
            isLandscape.toggle()
        }
        showHud(
            icon: isLandscape ? "iphone.landscape" : "iphone.portrait",
            text: isLandscape ? "横屏沉浸" : "竖屏模式"
        )
        scheduleHideControls()
    }
    
    private func scheduleHideControls() {
        hideControlsTask?.cancel()
        let task = DispatchWorkItem {
            withAnimation(.easeInOut(duration: 0.3)) {
                self.showControls = false
            }
        }
        hideControlsTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 3.5, execute: task)
    }
    
    private func cleanupPlayer() {
        UIApplication.shared.isIdleTimerDisabled = false
        hideControlsTask?.cancel()
        itemStatusObserver?.invalidate()
        itemDurationObserver?.invalidate()
        if let token = didPlayToEndObserverToken {
            NotificationCenter.default.removeObserver(token)
            didPlayToEndObserverToken = nil
        }
        if let token = timeObserverToken, let p = player {
            p.removeTimeObserver(token)
            timeObserverToken = nil
        }
        player?.pause()
        player = nil
    }
    
    private func handleEpisodePlaybackEnded() {
        guard !isSwitchingNextEpisode else { return }
        guard let currentEp = currentEpisode, !episodes.isEmpty else { return }
        if let nextEp = episodes.first(where: { $0.number == currentEp.number + 1 }) {
            isSwitchingNextEpisode = true
            showHud(
                icon: "forward.end.fill",
                text: "本集结束，自动播放 \(nextEp.label)"
            )
            let impact = UINotificationFeedbackGenerator()
            impact.notificationOccurred(.success)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                switchEpisode(nextEp)
                DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                    self.isSwitchingNextEpisode = false
                }
            }
        } else {
            showHud(
                icon: "checkmark.circle.fill",
                text: "全剧播放完毕"
            )
        }
    }
    
    private func switchEpisode(_ ep: PlaybackEpisode) {
        currentEpisode = ep
        let epVideo = VideoItem(
            id: "\(currentVideo.id)-ep-\(ep.number)",
            title: "\(currentVideo.title) \(ep.label)",
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
        
        // 针对后续集数进行滑动窗口持续预热（向后预热2集）
        GlobalStartupPrefetchEngine.shared.handleEpisodePlaying(
            video: currentVideo,
            currentEpisode: ep,
            allEpisodes: episodes
        )
    }
    
    private func switchToVideo(_ newVideo: VideoItem) {
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        currentVideo = newVideo
        currentEpisode = nil
        episodes = []
    }
    
    private func loadEpisodes(for video: VideoItem) async {
        isLoadingEpisodes = true
        let eps = await videoRepo.resolveEpisodes(for: video)
        episodes = eps
        if currentEpisode == nil {
            currentEpisode = eps.first(where: { $0.pageUrl == video.playbackUrl }) ?? eps.first
        }
        isLoadingEpisodes = false
        
        // 若有多集，自动为后续集数开启向后滑动窗口预热
        if let currentEp = currentEpisode {
            GlobalStartupPrefetchEngine.shared.handleEpisodePlaying(
                video: video,
                currentEpisode: currentEp,
                allEpisodes: eps
            )
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
        let m = secs / 60
        let s = secs % 60
        return String(format: "%02d:%02d", m, s)
    }

    /// Apple TV 标志性倒计时剩余时间格式（-MM:SS）
    private func formatRemainingTime(_ current: Double, duration: Double) -> String {
        guard duration.isFinite, duration > 0, current.isFinite else { return "--:--" }
        let remaining = max(0, duration - current)
        let secs = Int(remaining)
        let m = secs / 60
        let s = secs % 60
        return String(format: "-%02d:%02d", m, s)
    }

    /// Apple TV 风格动态 HUD（音量、亮度、画幅缩放、倍速切换）
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
