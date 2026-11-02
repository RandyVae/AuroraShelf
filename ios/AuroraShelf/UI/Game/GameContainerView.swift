import SwiftUI
import WebKit

// 原生 WKWebView 包装器，1:1 加载真实 203MB 《欲望女神》WebGL / HTML5 游戏
struct GameWebViewRepresentable: UIViewRepresentable {
    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        
        let preferences = WKWebpagePreferences()
        preferences.allowsContentJavaScript = true
        config.defaultWebpagePreferences = preferences
        
        // 允许读取本地 HTML 与跨目录 WebGL/Spine 资源
        config.preferences.setValue(true, forKey: "allowFileAccessFromFileURLs")
        
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.isOpaque = false
        webView.backgroundColor = UIColor(red: 0.03, green: 0.04, blue: 0.06, alpha: 1.0)
        webView.scrollView.isScrollEnabled = false
        webView.scrollView.bounces = false
        
        if let url = Bundle.main.url(forResource: "index", withExtension: "html", subdirectory: "www") {
            let readAccessDir = url.deletingLastPathComponent()
            webView.loadFileURL(url, allowingReadAccessTo: readAccessDir)
        }
        
        return webView
    }
    
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

public struct GameContainerView: View {
    public let onDismiss: () -> Void
    
    @State private var capsuleOpacity: Double = 1.0
    @State private var capsuleOffset: CGSize = .zero
    @State private var dragPosition: CGSize = .zero
    @State private var fadeTimer: Timer?
    
    public init(onDismiss: @escaping () -> Void) {
        self.onDismiss = onDismiss
    }
    
    public var body: some View {
        ZStack(alignment: .topLeading) {
            // 背景纯黑沉浸
            Color(red: 0.03, green: 0.04, blue: 0.06).ignoresSafeArea()
            
            // 真实 203MB WebGL 游戏视口
            GameWebViewRepresentable()
                .ignoresSafeArea()
            
            // Apple HIG 深色磨砂「< 返回视频」悬浮胶囊
            Button(action: {
                let impact = UIImpactFeedbackGenerator(style: .medium)
                impact.impactOccurred()
                onDismiss()
            }) {
                HStack(spacing: 6) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 13, weight: .bold))
                    Text("返回视频")
                        .font(.system(size: 13, weight: .semibold))
                }
                .foregroundColor(.white)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(
                    Capsule()
                        .fill(Color(red: 0.08, green: 0.09, blue: 0.15).opacity(0.85))
                        .overlay(
                            Capsule().stroke(Color.white.opacity(0.28), lineWidth: 1)
                        )
                )
                .shadow(color: Color.black.opacity(0.4), radius: 10, x: 0, y: 4)
            }
            .buttonStyle(PlainButtonStyle())
            .opacity(capsuleOpacity)
            .offset(x: capsuleOffset.width + dragPosition.width + 16,
                    y: capsuleOffset.height + dragPosition.height + 54)
            .gesture(
                DragGesture()
                    .onChanged { value in
                        wakeCapsule()
                        dragPosition = value.translation
                    }
                    .onEnded { value in
                        capsuleOffset.width += value.translation.width
                        capsuleOffset.height += value.translation.height
                        dragPosition = .zero
                        scheduleAutoFade()
                    }
            )
        }
        .onAppear {
            scheduleAutoFade()
        }
        .onDisappear {
            fadeTimer?.invalidate()
        }
    }
    
    private func scheduleAutoFade() {
        fadeTimer?.invalidate()
        fadeTimer = Timer.scheduledTimer(withTimeInterval: 3.5, repeats: false) { _ in
            withAnimation(.easeOut(duration: 0.35)) {
                capsuleOpacity = 0.40
            }
        }
    }
    
    private func wakeCapsule() {
        fadeTimer?.invalidate()
        withAnimation(.easeIn(duration: 0.2)) {
            capsuleOpacity = 1.0
        }
    }
}
