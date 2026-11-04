import SwiftUI
import AVKit

public struct DanmakuMessage: Identifiable, Sendable {
    public let id = UUID()
    public let text: String
    public let lane: Int
    public let color: Color
    
    public init(text: String, lane: Int, color: Color = .white) {
        self.text = text
        self.lane = lane
        self.color = color
    }
}

public struct LivePlayerView: View {
    public let stream: LiveStreamItem
    public let onDismiss: () -> Void
    
    @State private var player: AVPlayer?
    @State private var isPlaying: Bool = false
    @State private var isLoadingLive: Bool = true
    @State private var liveError: String?
    @State private var isDanmakuEnabled: Bool = true
    @State private var danmakus: [DanmakuMessage] = []
    @State private var inputMessage: String = ""
    @State private var showControls: Bool = true
    @State private var itemStatusObserver: NSKeyValueObservation?
    
    public init(stream: LiveStreamItem, onDismiss: @escaping () -> Void) {
        self.stream = stream
        self.onDismiss = onDismiss
    }
    
    public var body: some View {
        ZStack {
            // 背景纯黑
            Color.black.ignoresSafeArea()
            
            // 原生 AVPlayer 硬件解码直播视口（秒级出画）
            if let player = player {
                VideoPlayer(player: player)
                    .ignoresSafeArea()
                    .onTapGesture {
                        withAnimation(AuroraMotion.quick) {
                            showControls.toggle()
                        }
                    }
            } else if isLoadingLive {
                // 加载中过渡底图与 Spinner
                ZStack {
                    AsyncImage(url: URL(string: stream.snapshotUrl)) { phase in
                        if let img = phase.image {
                            img.resizable()
                                .aspectRatio(contentMode: .fill)
                                .ignoresSafeArea()
                                .opacity(0.35)
                                .blur(radius: 16)
                        }
                    }
                    
                    VStack(spacing: AuroraMetrics.spacingL) {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: AuroraColor.brand))
                            .scaleEffect(1.4)
                        
                        Text("正在直连 \(stream.username) 官方高清直播流...")
                            .font(.headline)
                            .foregroundStyle(.white)
                    }
                }
            } else if let error = liveError {
                // 离线或异常提示
                VStack(spacing: AuroraMetrics.spacingL) {
                    Image(systemName: "tv.slash.fill")
                        .font(.system(size: 40))
                        .foregroundStyle(AuroraColor.brand)
                    
                    Text("主播暂时离开或正在私密表演")
                        .font(.title3.bold())
                        .foregroundStyle(.white)
                    
                    Text(error)
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, AuroraMetrics.spacingXXXL)
                    
                    Button(action: {
                        fetchAndStartLiveStream()
                    }) {
                        Text("重新连接")
                            .font(.headline)
                            .foregroundStyle(.white)
                            .padding(.horizontal, AuroraMetrics.spacingXL)
                            .padding(.vertical, AuroraMetrics.spacingM)
                            .background(AuroraColor.brand, in: .capsule)
                    }
                    .buttonStyle(.plain)
                }
            }
            
            // 4 泳道防碰撞错峰弹幕浮层（严格单行、宽度上限 320pt 约束）
            if isDanmakuEnabled {
                VStack(spacing: AuroraMetrics.spacingM) {
                    ForEach(0..<4, id: \.self) { laneIndex in
                        HStack {
                            let laneDanmakus = danmakus.filter { $0.lane == laneIndex }
                            ForEach(laneDanmakus) { d in
                                Text(d.text)
                                    .font(.callout.bold())
                                    .lineLimit(1)
                                    .frame(maxWidth: 320, alignment: .leading)
                                    .foregroundStyle(d.color)
                                    .padding(.horizontal, AuroraMetrics.spacingM)
                                    .padding(.vertical, AuroraMetrics.spacingXS)
                                    .background(.regularMaterial, in: .capsule)
                                    .shadow(color: .black.opacity(0.3), radius: 2)
                                    .transition(.asymmetric(insertion: .move(edge: .trailing), removal: .opacity))
                            }
                            Spacer()
                        }
                        .frame(height: 28)
                    }
                    Spacer()
                }
                .padding(.top, 96)
                .padding(.horizontal, AuroraMetrics.contentInset)
                .allowsHitTesting(false)
            }
            
            // 浮动控制栏
            if showControls || isLoadingLive || liveError != nil {
                VStack {
                    // 统一顶栏
                    PlayerOverlayBar(
                        title: stream.username,
                        subtitle: "在线人数 \(stream.viewCount)",
                        onDismiss: {
                            cleanupLivePlayer()
                            onDismiss()
                        }
                    ) {
                        HStack(spacing: AuroraMetrics.spacingS) {
                            AirPlayRoutePicker()
                                .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                            
                            Button(action: {
                                let impact = UIImpactFeedbackGenerator(style: .light)
                                impact.impactOccurred()
                                withAnimation(AuroraMotion.quick) {
                                    isDanmakuEnabled.toggle()
                                }
                            }) {
                                HStack(spacing: AuroraMetrics.spacingXS) {
                                    Image(systemName: isDanmakuEnabled ? "bubble.left.and.bubble.right.fill" : "bubble.left.and.bubble.right")
                                        .font(.caption)
                                    Text(isDanmakuEnabled ? "弹幕" : "纯净")
                                        .font(.caption.bold())
                                }
                                .foregroundStyle(isDanmakuEnabled ? AuroraColor.brand : Color.white.opacity(0.75))
                                .padding(.horizontal, AuroraMetrics.spacingM)
                                .frame(height: AuroraMetrics.minTouchTarget)
                                .glassEffect(.regular.interactive(), in: .capsule)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(isDanmakuEnabled ? "关闭弹幕" : "开启弹幕")
                        }
                    }
                    
                    Spacer()
                    
                    // 底部弹幕发送栏
                    HStack(spacing: AuroraMetrics.spacingM) {
                        HStack(spacing: AuroraMetrics.spacingS) {
                            Image(systemName: "pencil")
                                .font(.callout)
                                .foregroundStyle(.secondary)
                            TextField("跟主播说点什么吧...", text: $inputMessage)
                                .font(.subheadline)
                                .foregroundStyle(.white)
                                .submitLabel(.send)
                                .onSubmit {
                                    sendDanmaku()
                                }
                        }
                        .padding(.horizontal, AuroraMetrics.spacingL)
                        .frame(height: AuroraMetrics.minTouchTarget)
                        .glassEffect(.regular.interactive(), in: .capsule)
                        
                        Button(action: {
                            sendDanmaku()
                        }) {
                            Image(systemName: "paperplane.fill")
                                .font(.headline)
                                .foregroundStyle(.white)
                                .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                                .background(AuroraColor.brand, in: .circle)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("发送弹幕")
                    }
                    .padding(.horizontal, AuroraMetrics.contentInset)
                    .padding(.bottom, AuroraMetrics.spacingXL)
                }
                .transition(.opacity)
            }
        }
        .onAppear {
            fetchAndStartLiveStream()
            startDanmakuEmitter()
        }
        .onDisappear {
            cleanupLivePlayer()
        }
    }
    
    // MARK: - 真实获取官方 LL-HLS 直播直链并硬件秒播
    
    private func fetchAndStartLiveStream() {
        self.isLoadingLive = true
        self.liveError = nil
        cleanupLivePlayer()
        
        let username = stream.username
        guard !username.isEmpty,
              let apiUrl = URL(string: "https://chaturbate.com/api/chatvideocontext/\(username)/") else {
            self.isLoadingLive = false
            self.liveError = "无效的主播用户名"
            return
        }
        
        Task {
            do {
                var request = URLRequest(url: apiUrl)
                request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15", forHTTPHeaderField: "User-Agent")
                request.setValue("https://chaturbate.com/", forHTTPHeaderField: "Referer")
                
                let (data, response) = try await URLSession.shared.data(for: request)
                guard let httpResp = response as? HTTPURLResponse, (200...299).contains(httpResp.statusCode),
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                    throw NSError(domain: "LivePlayer", code: 502, userInfo: [NSLocalizedDescriptionKey: "获取主播直播流状态失败"])
                }
                
                guard let hlsSource = json["hls_source"] as? String, !hlsSource.isEmpty,
                      let hlsUrl = URL(string: hlsSource) else {
                    let roomStatus = json["room_status"] as? String ?? "offline"
                    throw NSError(domain: "LivePlayer", code: 404, userInfo: [NSLocalizedDescriptionKey: "主播当前状态: \(roomStatus)"])
                }
                
                await MainActor.run {
                    self.startNativePlayback(hlsUrl: hlsUrl)
                }
            } catch {
                await MainActor.run {
                    self.isLoadingLive = false
                    self.liveError = error.localizedDescription
                }
            }
        }
    }
    
    private func startNativePlayback(hlsUrl: URL) {
        let playerItem = AVPlayerItem(url: hlsUrl)
        let avPlayer = AVPlayer(playerItem: playerItem)
        self.player = avPlayer
        
        itemStatusObserver = playerItem.observe(\.status, options: [.new, .initial]) { item, _ in
            DispatchQueue.main.async {
                if item.status == .readyToPlay {
                    self.isLoadingLive = false
                    self.isPlaying = true
                    avPlayer.play()
                } else if item.status == .failed {
                    self.isLoadingLive = false
                    self.liveError = item.error?.localizedDescription ?? "直播流解码失败"
                }
            }
        }
    }
    
    private func cleanupLivePlayer() {
        itemStatusObserver?.invalidate()
        itemStatusObserver = nil
        player?.pause()
        player = nil
    }
    
    private func sendDanmaku() {
        let trimmed = inputMessage.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        let msg = DanmakuMessage(text: trimmed, lane: Int.random(in: 0..<4), color: AuroraColor.brand)
        withAnimation(AuroraMotion.smooth) {
            danmakus.append(msg)
        }
        inputMessage = ""
    }
    
    private func startDanmakuEmitter() {
        let seeds = [
            "主播真好看！666",
            "打卡打卡，画质太清晰了",
            "这是哪里呀？",
            "极光视界原生硬解秒出画！",
            "好高清啊，爱了爱了"
        ]
        for (i, text) in seeds.enumerated() {
            DispatchQueue.main.asyncAfter(deadline: .now() + Double(i) * 0.8) {
                let msg = DanmakuMessage(text: text, lane: i % 4, color: i == 0 ? AuroraColor.brand : .white)
                withAnimation(AuroraMotion.smooth) {
                    self.danmakus.append(msg)
                }
            }
        }
    }
}
