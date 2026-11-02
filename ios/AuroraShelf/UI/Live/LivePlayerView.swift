import SwiftUI
import WebKit

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

public struct LiveStreamWebViewRepresentable: UIViewRepresentable {
    public let embedUrl: URL
    @Binding public var isLoading: Bool
    
    public init(embedUrl: URL, isLoading: Binding<Bool>) {
        self.embedUrl = embedUrl
        self._isLoading = isLoading
    }
    
    public func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        
        // 注入确认条款与全屏自适应配置
        let scriptSource = """
        document.cookie = "agreeterms=1; domain=.chaturbate.com; path=/; max-age=31536000";
        document.cookie = "isAdult=1; domain=.chaturbate.com; path=/; max-age=31536000";
        document.cookie = "hasConfirmedAge=1; domain=.chaturbate.com; path=/; max-age=31536000";
        var meta = document.createElement('meta');
        meta.name = 'viewport';
        meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
        document.getElementsByTagName('head')[0].appendChild(meta);
        var style = document.createElement('style');
        style.innerHTML = 'body, html { margin:0 !important; padding:0 !important; background:#000 !important; overflow:hidden !important; } .player-holder { width:100% !important; height:100% !important; } .chat-holder, .sidebar, #header, #footer, .top-section, .room-details { display:none !important; } video { width:100vw !important; height:auto !important; max-height:100vh !important; }';
        document.head.appendChild(style);
        setTimeout(function() {
            var btns = document.querySelectorAll('button, a');
            for (var i = 0; i < btns.length; i++) {
                var t = (btns[i].innerText || '').toLowerCase();
                if (t.indexOf('agree') !== -1 || t.indexOf('enter') !== -1 || t.indexOf('confirm') !== -1) {
                    btns[i].click();
                }
            }
        }, 300);
        """
        let userScript = WKUserScript(source: scriptSource, injectionTime: .atDocumentStart, forMainFrameOnly: false)
        config.userContentController.addUserScript(userScript)
        
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.isOpaque = false
        webView.backgroundColor = .black
        webView.scrollView.backgroundColor = .black
        webView.scrollView.bounces = false
        webView.scrollView.showsVerticalScrollIndicator = false
        webView.scrollView.showsHorizontalScrollIndicator = false
        
        var request = URLRequest(url: embedUrl)
        request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")
        request.setValue("https://chaturbate.com/", forHTTPHeaderField: "Referer")
        webView.load(request)
        
        return webView
    }
    
    public func updateUIView(_ uiView: WKWebView, context: Context) {
        context.coordinator.parent = self
    }
    
    public func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    public class Coordinator: NSObject, WKNavigationDelegate {
        var parent: LiveStreamWebViewRepresentable
        
        init(_ parent: LiveStreamWebViewRepresentable) {
            self.parent = parent
        }
        
        public func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                withAnimation {
                    self.parent.isLoading = false
                }
            }
        }
        
        public func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            DispatchQueue.main.async {
                self.parent.isLoading = false
            }
        }
    }
}

public struct LivePlayerView: View {
    public let stream: LiveStreamItem
    public let onDismiss: () -> Void
    
    @State private var isDanmakuEnabled: Bool = true
    @State private var danmakus: [DanmakuMessage] = []
    @State private var inputMessage: String = ""
    @State private var showControls: Bool = true
    @State private var isWebLoading: Bool = true
    
    public init(stream: LiveStreamItem, onDismiss: @escaping () -> Void) {
        self.stream = stream
        self.onDismiss = onDismiss
    }
    
    private var embedUrl: URL {
        if !stream.username.isEmpty,
           let url = URL(string: "https://chaturbate.com/embed/\(stream.username)/?bgcolor=black") {
            return url
        }
        if let direct = URL(string: stream.roomUrl) {
            return direct
        }
        return URL(string: "https://chaturbate.com/")!
    }
    
    public var body: some View {
        ZStack {
            // 背景纯黑
            Color.black.ignoresSafeArea()
            
            // 原生 WKWebView 全屏无广告嵌入播放器
            LiveStreamWebViewRepresentable(embedUrl: embedUrl, isLoading: $isWebLoading)
                .ignoresSafeArea()
                .onTapGesture {
                    withAnimation(AuroraTheme.quickSpring) {
                        showControls.toggle()
                    }
                }
            
            // 网页加载中过渡底图与 Spinner
            if isWebLoading {
                ZStack {
                    Color.black.ignoresSafeArea()
                    
                    AsyncImage(url: URL(string: stream.snapshotUrl)) { phase in
                        if let img = phase.image {
                            img.resizable()
                                .aspectRatio(contentMode: .fill)
                                .ignoresSafeArea()
                                .opacity(0.35)
                                .blur(radius: 10)
                        }
                    }
                    
                    VStack(spacing: 16) {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: AuroraTheme.crimson))
                            .scaleEffect(1.6)
                        
                        Text("正在直连 \(stream.username) 真实直播间...")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.white)
                    }
                }
                .transition(.opacity)
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
            startDanmakuEmitter()
            // 2.5 秒兜底淡出加载蒙层，确保画面必定出画
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                withAnimation {
                    self.isWebLoading = false
                }
            }
        }
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
