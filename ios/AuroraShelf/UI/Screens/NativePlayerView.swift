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
    
    public init(video: VideoItem, onDismiss: @escaping () -> Void) {
        self.video = video
        self.onDismiss = onDismiss
    }
    
    public var body: some View {
        ZStack {
            // 背景纯黑
            Color.black.ignoresSafeArea()
            
            // 原生 AVPlayer 硬件解码流视口
            if let player = player {
                VideoPlayer(player: player)
                    .ignoresSafeArea()
                    .onTapGesture {
                        withAnimation(AuroraTheme.quickSpring) {
                            showControls.toggle()
                        }
                        if showControls {
                            scheduleAutoHideControls()
                        }
                    }
            } else if isResolving {
                // 真实网络流换流加载中状态（Apple HIG 深色质感）
                VStack(spacing: 20) {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: AuroraTheme.crimson))
                        .scaleEffect(1.6)
                    
                    VStack(spacing: 8) {
                        Text(resolveStatus)
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.white)
                        
                        Text("\(video.sourceName) · \(video.title)")
                            .font(.system(size: 13, weight: .regular))
                            .foregroundColor(.white.opacity(0.65))
                            .lineLimit(1)
                            .padding(.horizontal, 40)
                    }
                }
            } else if let error = resolveError {
                // 错误回退提示
                VStack(spacing: 16) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 40))
                        .foregroundColor(.yellow)
                    
                    Text("播放失败")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(.white)
                    
                    Text(error)
                        .font(.system(size: 13))
                        .foregroundColor(AuroraTheme.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                    
                    Button(action: {
                        startResolveAndPlay()
                    }) {
                        Text("重新连接")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 24)
                            .padding(.vertical, 10)
                            .background(Capsule().fill(AuroraTheme.crimson))
                    }
                    .buttonStyle(PlainButtonStyle())
                    .padding(.top, 8)
                }
            }
            
            // Apple HIG 播放控制覆盖层
            if showControls && player != nil {
                ZStack {
                    // 顶栏渐变遮罩
                    VStack {
                        LinearGradient(
                            colors: [Color.black.opacity(0.85), Color.clear],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                        .frame(height: 110)
                        
                        Spacer()
                        
                        LinearGradient(
                            colors: [Color.clear, Color.black.opacity(0.85)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                        .frame(height: 130)
                    }
                    .ignoresSafeArea()
                    
                    // 控件布局
                    VStack {
                        // 顶部控制栏（包含 Apple HIG 磨砂返回胶囊）
                        HStack {
                            Button(action: {
                                let impact = UIImpactFeedbackGenerator(style: .medium)
                                impact.impactOccurred()
                                cleanupPlayer()
                                onDismiss()
                            }) {
                                HStack(spacing: 6) {
                                    Image(systemName: "chevron.left")
                                        .font(.system(size: 14, weight: .bold))
                                    Text("返回")
                                        .font(.system(size: 14, weight: .semibold))
                                }
                                .foregroundColor(.white)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(
                                    Capsule()
                                        .fill(.ultraThinMaterial)
                                        .environment(\.colorScheme, .dark)
                                        .overlay(Capsule().stroke(Color.white.opacity(0.2), lineWidth: 1))
                                )
                            }
                            .buttonStyle(PlainButtonStyle())
                            .applePressEffect()
                            
                            Spacer()
                            
                            // 原生 AirPlay 投屏
                            AirPlayRoutePicker()
                                .frame(width: 32, height: 32)
                                .padding(.trailing, 6)
                            
                            // 视频标题与清晰度徽章
                            VStack(alignment: .trailing, spacing: 2) {
                                Text(video.title)
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(.white)
                                    .lineLimit(1)
                                
                                HStack(spacing: 4) {
                                    Text(video.sourceName)
                                        .font(.system(size: 11, weight: .medium))
                                        .foregroundColor(AuroraTheme.crimsonGlow)
                                    Text("·")
                                        .foregroundColor(.white.opacity(0.5))
                                    Text("1080P 超清直连")
                                        .font(.system(size: 11, weight: .regular))
                                        .foregroundColor(.white.opacity(0.7))
                                }
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.top, 16)
                        
                        Spacer()
                        
                        // 中心播放/暂停大按钮
                        HStack(spacing: 40) {
                            Button(action: { seekBy(-15) }) {
                                Image(systemName: "gobackward.15")
                                    .font(.system(size: 26, weight: .medium))
                                    .foregroundColor(.white)
                                    .padding(12)
                                    .background(Circle().fill(Color.black.opacity(0.4)))
                            }
                            .buttonStyle(PlainButtonStyle())
                            .applePressEffect()
                            
                            Button(action: { togglePlayPause() }) {
                                Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                                    .font(.system(size: 34, weight: .bold))
                                    .foregroundColor(.white)
                                    .frame(width: 72, height: 72)
                                    .background(
                                        Circle()
                                            .fill(AuroraTheme.crimson.opacity(0.85))
                                            .shadow(color: AuroraTheme.crimson.opacity(0.5), radius: 16, x: 0, y: 4)
                                    )
                            }
                            .buttonStyle(PlainButtonStyle())
                            .applePressEffect(scale: 0.92)
                            
                            Button(action: { seekBy(15) }) {
                                Image(systemName: "goforward.15")
                                    .font(.system(size: 26, weight: .medium))
                                    .foregroundColor(.white)
                                    .padding(12)
                                    .background(Circle().fill(Color.black.opacity(0.4)))
                            }
                            .buttonStyle(PlainButtonStyle())
                            .applePressEffect()
                        }
                        
                        Spacer()
                        
                        // 底部进度条与时间显示
                        VStack(spacing: 8) {
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
                            .accentColor(AuroraTheme.crimson)
                            
                            HStack {
                                Text(formatTime(currentTime))
                                    .font(.system(size: 12, weight: .medium, design: .monospaced))
                                    .foregroundColor(.white.opacity(0.8))
                                
                                Spacer()
                                
                                Text(formatDuration(duration))
                                    .font(.system(size: 12, weight: .medium, design: .monospaced))
                                    .foregroundColor(.white.opacity(0.8))
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.bottom, 24)
                    }
                }
                .transition(.opacity)
            }
        }
        .onAppear {
            startResolveAndPlay()
        }
        .onDisappear {
            cleanupPlayer()
        }
    }
    
    private func scheduleAutoHideControls() {
        hideControlsTask?.cancel()
        let task = DispatchWorkItem {
            withAnimation(AuroraTheme.quickSpring) {
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
