import SwiftUI
import AVKit

public struct NativePlayerView: View {
    public let video: VideoItem
    public let onDismiss: () -> Void
    
    @State private var player: AVPlayer?
    @State private var isPlaying: Bool = true
    @State private var showControls: Bool = true
    @State private var currentTime: Double = 0
    @State private var duration: Double = 0
    @State private var isSeeking: Bool = false
    @State private var timeObserverToken: Any?
    @State private var itemStatusObserver: NSKeyValueObservation?
    @State private var itemDurationObserver: NSKeyValueObservation?
    @State private var hideControlsTask: DispatchWorkItem?
    
    // 真实流解析状态
    @State private var isResolving: Bool = true
    @State private var resolveStatus: String = "正在连接真实超清直连源..."
    @State private var resolveError: String?
    @State private var resolvedResult: PlaybackSourceResult?
    
    // 画幅控制与横竖屏
    @State private var isFillMode: Bool = false
    @State private var isLandscape: Bool = false
    @State private var hudMessage: String?
    @State private var hudTask: DispatchWorkItem?
    
    public init(video: VideoItem, onDismiss: @escaping () -> Void) {
        self.video = video
        self.onDismiss = onDismiss
    }
    
    public var body: some View {
        GeometryReader { geo in
            let isRotated = isLandscape
            let targetWidth = isRotated ? geo.size.height : geo.size.width
            let targetHeight = isRotated ? geo.size.width : geo.size.height
            
            ZStack {
                // 背景纯黑
                Color.black.ignoresSafeArea()
                
                // 原生 AVPlayerLayer 硬件解码流视口（支持无黑边满铺与自适应）
                if let player = player {
                    AuroraPlayerSurface(
                        player: player,
                        videoGravity: isFillMode ? .resizeAspectFill : .resizeAspect
                    )
                    .ignoresSafeArea()
                    .contentShape(Rectangle())
                    .onTapGesture(count: 2) {
                        toggleFillMode()
                    }
                    .onTapGesture(count: 1) {
                        withAnimation(AuroraMotion.quick) {
                            showControls.toggle()
                        }
                        if showControls {
                            scheduleAutoHideControls()
                        }
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
                            
                            Text("\(video.sourceName) · \(video.title)")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                                .padding(.horizontal, AuroraMetrics.spacingXXXL)
                        }
                    }
                } else if let error = resolveError {
                    // 错误回退提示
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
                }
                
                // 屏幕中央画幅与模式切换 HUD 提示（Apple HIG 毛玻璃胶囊）
                if let hud = hudMessage {
                    Text(hud)
                        .font(.callout.bold())
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
                        .ignoresSafeArea()
                        
                        // 控件布局
                        VStack {
                            // 统一顶栏（集成画幅切换、旋转与投屏）
                            PlayerOverlayBar(
                                title: video.title,
                                subtitle: "\(video.sourceName) · 1080P 超清直连",
                                onDismiss: {
                                    cleanupPlayer()
                                    onDismiss()
                                }
                            ) {
                                HStack(spacing: AuroraMetrics.spacingM) {
                                    // 1. 画幅切换（Fit / Fill）
                                    Button(action: toggleFillMode) {
                                        Image(systemName: isFillMode ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right")
                                            .font(.subheadline.bold())
                                            .foregroundStyle(.white)
                                            .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                                            .glassEffect(.regular.interactive(), in: .circle)
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityLabel(isFillMode ? "自适应画幅" : "填满全屏")
                                    
                                    // 2. 横竖屏旋转
                                    Button(action: toggleOrientation) {
                                        Image(systemName: isLandscape ? "iphone.portrait" : "iphone.landscape")
                                            .font(.subheadline.bold())
                                            .foregroundStyle(.white)
                                            .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                                            .glassEffect(.regular.interactive(), in: .circle)
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityLabel(isLandscape ? "竖屏模式" : "横屏沉浸")
                                    
                                    // 3. AirPlay
                                    AirPlayRoutePicker()
                                        .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                                }
                            }
                            .padding(.top, isLandscape ? 12 : 46)
                            
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
                                .accessibilityLabel("快退 15 秒")
                                
                                Button(action: { togglePlayPause() }) {
                                    Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                                        .font(.title)
                                        .foregroundStyle(.white)
                                        .frame(width: 68, height: 68)
                                        .background(AuroraColor.brand, in: .circle)
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(isPlaying ? "暂停" : "播放")
                                
                                Button(action: { seekBy(15) }) {
                                    Image(systemName: "goforward.15")
                                        .font(.title2)
                                        .foregroundStyle(.white)
                                        .frame(width: 48, height: 48)
                                        .glassEffect(.regular.interactive(), in: .circle)
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel("快进 15 秒")
                            }
                            
                            Spacer()
                            
                            // 底部进度条与时间显示
                            VStack(spacing: AuroraMetrics.spacingS) {
                                Slider(
                                    value: Binding(
                                        get: { currentTime },
                                        set: { newValue in
                                            currentTime = newValue
                                            seekTo(time: newValue)
                                        }
                                    ),
                                    in: 0...max(duration, 1.0)
                                )
                                .tint(AuroraColor.brand)
                                
                                HStack {
                                    Text(formatTime(currentTime))
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
                            .padding(.bottom, isLandscape ? 16 : 42)
                        }
                    }
                    .transition(.opacity)
                }
            }
            .frame(width: targetWidth, height: targetHeight)
            .rotationEffect(isRotated ? .degrees(90) : .zero)
            .position(x: geo.size.width / 2, y: geo.size.height / 2)
            .ignoresSafeArea()
        }
        .ignoresSafeArea()
        .onAppear {
            if CommandLine.arguments.contains("--player-landscape") {
                self.isLandscape = true
            }
            if CommandLine.arguments.contains("--player-fill") {
                self.isFillMode = true
            }
            startResolveAndPlay()
        }
        .onDisappear {
            cleanupPlayer()
        }
    }
    
    private func scheduleAutoHideControls() {
        hideControlsTask?.cancel()
        let task = DispatchWorkItem {
            withAnimation(AuroraMotion.quick) {
                if self.isPlaying {
                    self.showControls = false
                }
            }
        }
        hideControlsTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 3.0, execute: task)
    }
    
    private func startResolveAndPlay() {
        self.isResolving = true
        self.resolveError = nil
        self.resolveStatus = "正在解析 \(video.sourceName) 真实直连流..."
        
        Task {
            do {
                let result = try await VideoRepository.shared.resolvePlayback(video: video)
                await MainActor.run {
                    self.resolvedResult = result
                    self.startPlayback(with: result)
                }
            } catch {
                await MainActor.run {
                    self.isResolving = false
                    self.resolveError = error.localizedDescription
                }
            }
        }
    }
    
    private func startPlayback(with result: PlaybackSourceResult) {
        cleanupPlayer()
        
        // 激活音频会话
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback, options: [])
        try? AVAudioSession.sharedInstance().setActive(true)
        
        var httpHeaders: [String: String] = [
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        ]
        if let referer = result.referer {
            httpHeaders["Referer"] = referer
            httpHeaders["Origin"] = referer
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
        
        // 监听准备就绪状态
        itemStatusObserver = playerItem.observe(\.status, options: [.new, .initial]) { item, _ in
            DispatchQueue.main.async {
                if item.status == .readyToPlay {
                    self.isResolving = false
                    self.isPlaying = true
                    avPlayer.play()
                    self.scheduleAutoHideControls()
                } else if item.status == .failed {
                    self.isResolving = false
                    self.resolveError = item.error?.localizedDescription ?? "视频解码失败，请重试"
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
        
        NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: playerItem,
            queue: .main
        ) { _ in
            self.isPlaying = false
            self.showControls = true
        }
        
        let interval = CMTime(seconds: 0.5, preferredTimescale: 600)
        timeObserverToken = avPlayer.addPeriodicTimeObserver(forInterval: interval, queue: .main) { time in
            guard !isSeeking else { return }
            self.currentTime = time.seconds
            if let currentItem = avPlayer.currentItem {
                let d = currentItem.duration.seconds
                if !d.isNaN && d > 1.0 && self.duration != d {
                    self.duration = d
                }
            }
        }
    }
    
    private func cleanupPlayer() {
        hideControlsTask?.cancel()
        hideControlsTask = nil
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
    
    private func togglePlayPause() {
        guard let player = player else { return }
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        if isPlaying {
            player.pause()
            isPlaying = false
        } else {
            player.play()
            isPlaying = true
            scheduleAutoHideControls()
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
            self.isSeeking = false
        }
    }
    
    private func toggleFillMode() {
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        withAnimation(AuroraMotion.smooth) {
            isFillMode.toggle()
        }
        showHud(isFillMode ? "畫幅：滿鋪全屏（無黑邊）" : "畫幅：原片比例（完整畫面）")
    }
    
    private func toggleOrientation() {
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        withAnimation(AuroraMotion.smooth) {
            isLandscape.toggle()
        }
        showHud(isLandscape ? "已進入橫屏沉浸模式" : "已返回豎屏常規模式")
    }
    
    private func showHud(_ text: String) {
        hudTask?.cancel()
        withAnimation(AuroraMotion.quick) {
            self.hudMessage = text
        }
        let task = DispatchWorkItem {
            withAnimation(AuroraMotion.smooth) {
                self.hudMessage = nil
            }
        }
        hudTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.8, execute: task)
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
