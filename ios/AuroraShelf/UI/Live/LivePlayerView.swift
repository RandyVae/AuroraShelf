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
    @State private var isDanmakuEnabled: Bool = true
    @State private var danmakus: [DanmakuMessage] = []
    @State private var inputMessage: String = ""
    @State private var showControls: Bool = true
    
    public init(stream: LiveStreamItem, onDismiss: @escaping () -> Void) {
        self.stream = stream
        self.onDismiss = onDismiss
    }
    
    public var body: some View {
        ZStack {
            // 背景纯黑
            Color.black.ignoresSafeArea()
            
            // 播放器视口
            if let player = player {
                VideoPlayer(player: player)
                    .ignoresSafeArea()
                    .onTapGesture {
                        withAnimation(AuroraTheme.quickSpring) {
                            showControls.toggle()
                        }
                    }
            } else {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: AuroraTheme.crimson))
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
                                    .background(
                                        Capsule()
                                            .fill(Color.black.opacity(0.65))
                                    )
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
            if showControls {
                VStack {
                    // 顶栏：主播信息与返回
                    HStack(spacing: 12) {
                        Button(action: {
                            let impact = UIImpactFeedbackGenerator(style: .medium)
                            impact.impactOccurred()
                            player?.pause()
                            player = nil
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
            setupLivePlayer()
            startDanmakuEmitter()
        }
    }
    
    private func setupLivePlayer() {
        let playUrl: URL?
        if let localUrl = Bundle.main.url(forResource: "intro_film", withExtension: "mp4") {
            playUrl = localUrl
        } else if let remote = URL(string: stream.roomUrl) {
            playUrl = remote
        } else {
            playUrl = nil
        }
        guard let url = playUrl else { return }
        let player = AVPlayer(url: url)
        self.player = player
        player.play()
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
        // 初始种子弹幕
        let seeds = [
            "主播真好看！666",
            "打卡打卡，声音太甜了",
            "这是在哪里呀？",
            "极光视界画质太顶了！",
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
