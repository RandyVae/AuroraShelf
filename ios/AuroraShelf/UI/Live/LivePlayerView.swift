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
                        withAnimation(AuroraTheme.quickSpring) {
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
                                .opacity(0.4)
                                .blur(radius: 12)
                        }
                    }
                    
                    VStack(spacing: 16) {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: AuroraTheme.crimson))
                            .scaleEffect(1.6)
                        
                        Text("正在直连 \(stream.username) 官方高清直播流...")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.white)
                    }
                }
            } else if let error = liveError {
                // 离线或异常提示
                VStack(spacing: 16) {
                    Image(systemName: "tv.slash.fill")
                        .font(.system(size: 40))
                        .foregroundColor(AuroraTheme.crimson)
                    
                    Text("主播暂时离开或正在私密表演")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                    
                    Text(error)
                        .font(.system(size: 13, weight: .regular))
                        .foregroundColor(.white.opacity(0.7))
                        .padding(.horizontal, 32)
                    
                    Button(action: {
                        fetchAndStartLiveStream()
                    }) {
                        Text("重新连接")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 24)
                            .padding(.vertical, 10)
                            .background(Capsule().fill(AuroraTheme.crimson))
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
            
            // 4 泳道防碰撞错峰弹幕浮层
            if isDanmakuEnabled {
                VStack(spacing: 12) {
                    ForEach(0..<4, id: \.self) { laneIndex in
                        HStack {
                            let laneDanmakus = danmakus.filter { $0.lane == laneIndex }
                            ForEach(laneDanmakus) { d in
                                Text(d.text)
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(d.color)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 4)
                                    .background(Capsule().fill(Color.black.opacity(0.65)))
                                    .shadow(color: .black, radius: 2)
                                    .transition(.asymmetric(insertion: .move(edge: .trailing), removal: .opacity))
                            }
                            Spacer()
                        }
                        .frame(height: 28)
                    }
                    Spacer()
                }
                .padding(.top, 90)
                .padding(.horizontal, 16)
                .allowsHitTesting(false)
            }
            
            // 浮动控制栏
            if showControls || isLoadingLive || liveError != nil {
                VStack {
                    // 顶栏：主播信息与返回
                    HStack(spacing: 12) {
                        Button(action: {
                            let impact = UIImpactFeedbackGenerator(style: .medium)
                            impact.impactOccurred()
                            cleanupLivePlayer()
                            onDismiss()
                        }) {
                            HStack(spacing: 4) {
                                Image(systemName: "chevron.left")
                                    .font(.system(size: 14, weight: .bold))
                                Text("返回")
                                    .font(.system(size: 13, weight: .semibold))
                            }
                            .foregroundColor(.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(
                                Capsule()
                                    .fill(.ultraThinMaterial)
                                    .environment(\.colorScheme, .dark)
                                    .overlay(Capsule().stroke(AuroraTheme.glassBorder, lineWidth: 1))
                            )
                        }
                        .buttonStyle(PlainButtonStyle())
                        
                        // 主播头像与名称
                        HStack(spacing: 8) {
                            AsyncImage(url: URL(string: stream.avatarUrl)) { phase in
                                if let img = phase.image {
                                    img.resizable().aspectRatio(contentMode: .fill)
                                } else {
                                    Circle().fill(AuroraTheme.surfaceCard)
                                }
                            }
                            .frame(width: 32, height: 32)
                            .clipShape(Circle())
                            
                            VStack(alignment: .leading, spacing: 1) {
                                Text(stream.username)
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(.white)
                                Text("在线人数 \(stream.viewCount)")
                                    .font(.system(size: 10))
                                    .foregroundColor(.white.opacity(0.7))
                            }
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(Capsule().fill(Color.black.opacity(0.6)))
                        
                        Spacer()
                        
                        // 原生 AirPlay
                        AirPlayRoutePicker()
                            .frame(width: 32, height: 32)
                            .padding(.trailing, 4)
                        
                        // 弹幕开关胶囊
                        Button(action: {
                            let impact = UIImpactFeedbackGenerator(style: .light)
                            impact.impactOccurred()
                            withAnimation(AuroraTheme.quickSpring) {
                                isDanmakuEnabled.toggle()
                            }
                        }) {
                            HStack(spacing: 4) {
                                Image(systemName: isDanmakuEnabled ? "bubble.left.and.bubble.right.fill" : "bubble.left.and.bubble.right")
                                    .font(.system(size: 12))
                                Text(isDanmakuEnabled ? "弹幕开" : "纯净模式")
                                    .font(.system(size: 11, weight: .semibold))
                            }
                            .foregroundColor(isDanmakuEnabled ? AuroraTheme.crimsonGlow : .white.opacity(0.6))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(
                                Capsule()
                                    .fill(.ultraThinMaterial)
                                    .environment(\.colorScheme, .dark)
                                    .overlay(Capsule().stroke(AuroraTheme.glassBorder, lineWidth: 0.8))
                            )
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                    
                    Spacer()
                    
                    // 底部弹幕发送栏
                    HStack(spacing: 10) {
                        HStack {
                            Image(systemName: "pencil")
                                .font(.system(size: 12))
                                .foregroundColor(.white.opacity(0.5))
                            TextField("跟主播说点什么吧...", text: $inputMessage)
                                .font(.system(size: 13))
                                .foregroundColor(.white)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(
                            Capsule()
                                .fill(.ultraThinMaterial)
                                .environment(\.colorScheme, .dark)
                                .overlay(Capsule().stroke(AuroraTheme.glassBorder, lineWidth: 0.8))
                        )
                        
                        Button(action: {
                            sendDanmaku()
                        }) {
                            Image(systemName: "paperplane.fill")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.white)
                                .padding(10)
                                .background(Circle().fill(AuroraTheme.crimson))
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
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
        guard !inputMessage.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        let msg = DanmakuMessage(text: inputMessage, lane: Int.random(in: 0..<4), color: AuroraTheme.crimsonGlow)
        withAnimation(AuroraTheme.appleSpring) {
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
                let msg = DanmakuMessage(text: text, lane: i % 4, color: i == 0 ? AuroraTheme.gold : .white)
                withAnimation(AuroraTheme.appleSpring) {
                    self.danmakus.append(msg)
                }
            }
        }
    }
}
