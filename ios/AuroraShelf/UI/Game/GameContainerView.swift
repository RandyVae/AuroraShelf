import SwiftUI
import WebKit

// MARK: - 原生 WKWebView 包装器（支持原生触觉反馈 Bridge 与全屏沉浸渲染）

struct GameWebViewRepresentable: UIViewRepresentable {
    let onExitRequest: () -> Void
    let onGameMessage: (String) -> Void
    @Binding var isMuted: Bool
    let reloadToken: UUID

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []

        let preferences = WKWebpagePreferences()
        preferences.allowsContentJavaScript = true
        config.defaultWebpagePreferences = preferences

        // 允许读取本地 HTML 与跨目录 WebGL/Spine 资源
        config.preferences.setValue(true, forKey: "allowFileAccessFromFileURLs")

        // 注入原生 Bridge 代理，建立双向通信通道
        let contentController = WKUserContentController()
        contentController.add(context.coordinator, name: "NativeBridge")
        config.userContentController = contentController

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.isOpaque = false
        webView.backgroundColor = UIColor(AuroraColor.canvas)
        webView.scrollView.isScrollEnabled = false
        webView.scrollView.bounces = false
        webView.scrollView.maximumZoomScale = 1.0
        webView.scrollView.minimumZoomScale = 1.0
        webView.scrollView.bouncesZoom = false
        webView.scrollView.delegate = context.coordinator
        webView.navigationDelegate = context.coordinator
        context.coordinator.webView = webView

        if let url = Bundle.main.url(forResource: "index", withExtension: "html", subdirectory: "www") {
            let readAccessDir = url.deletingLastPathComponent()
            var finalUrl = url
            let args = CommandLine.arguments
            var queryItems: [URLQueryItem] = []
            if args.contains("--game-slots") {
                queryItems.append(URLQueryItem(name: "slots", value: "1"))
            } else if args.contains("--game-battle") || args.contains("--battle") {
                queryItems.append(URLQueryItem(name: "battle", value: "1"))
            }
            if let heroIdx = args.firstIndex(of: "--hero"), heroIdx + 1 < args.count {
                queryItems.append(URLQueryItem(name: "hero", value: args[heroIdx + 1]))
            }
            if !queryItems.isEmpty, var comps = URLComponents(url: url, resolvingAgainstBaseURL: false) {
                comps.queryItems = queryItems
                if let u = comps.url { finalUrl = u }
            }
            webView.loadFileURL(finalUrl, allowingReadAccessTo: readAccessDir)
        }

        return webView
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(onExitRequest: onExitRequest, onGameMessage: onGameMessage)
    }

    class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler, UIScrollViewDelegate {
        weak var webView: WKWebView?
        var lastReloadToken: UUID?
        let onExitRequest: () -> Void
        let onGameMessage: (String) -> Void

        func viewForZooming(in scrollView: UIScrollView) -> UIView? {
            return nil
        }

        private func disableDoubleTapZoom(in view: UIView) {
            for gesture in view.gestureRecognizers ?? [] {
                if let tap = gesture as? UITapGestureRecognizer, tap.numberOfTapsRequired == 2 {
                    tap.isEnabled = false
                }
            }
            for subview in view.subviews {
                disableDoubleTapZoom(in: subview)
            }
        }

        init(onExitRequest: @escaping () -> Void, onGameMessage: @escaping () -> Void) {
            self.onExitRequest = onExitRequest
            self.onGameMessage = { _ in }
        }

        init(onExitRequest: @escaping () -> Void, onGameMessage: @escaping (String) -> Void) {
            self.onExitRequest = onExitRequest
            self.onGameMessage = onGameMessage
        }

        // 处理来自前端的 JS 原生消息
        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard message.name == "NativeBridge", let dict = message.body as? [String: Any] else { return }
            let type = dict["type"] as? String ?? ""

            switch type {
            case "haptic":
                let style = dict["style"] as? String ?? "medium"
                DispatchQueue.main.async {
                    switch style {
                    case "light":
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    case "medium":
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    case "heavy":
                        UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
                    case "selection":
                        UISelectionFeedbackGenerator().selectionChanged()
                    case "success", "bigWin":
                        UINotificationFeedbackGenerator().notificationOccurred(.success)
                    case "warning", "nearMiss":
                        UINotificationFeedbackGenerator().notificationOccurred(.warning)
                    case "error", "defeat":
                        UINotificationFeedbackGenerator().notificationOccurred(.error)
                    default:
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    }
                }
            case "exitGame":
                DispatchQueue.main.async {
                    self.onExitRequest()
                }
            case "toast":
                if let msg = dict["message"] as? String {
                    DispatchQueue.main.async {
                        self.onGameMessage(msg)
                    }
                }
            case "log":
                if let msg = dict["message"] as? String {
                    let lvl = dict["level"] as? String ?? "info"
                    #if DEBUG
                    print("[NetgreyGameWeb:\(lvl)] \(msg)")
                    #endif
                }
            default:
                break
            }
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            disableDoubleTapZoom(in: webView)
            let args = CommandLine.arguments
            if args.contains("--game-battle") || args.contains("--battle") {
                webView.evaluateJavaScript("""
                    function tryBattle() {
                        var b = document.getElementById('btn-start');
                        if (b) b.click();
                        setTimeout(function() {
                            if (window.runBattleDirect && window.APP && window.APP.data && window.APP.data.chapters && window.APP.data.chapters[0]) {
                                var chap = window.APP.data.chapters[0];
                                if (chap.stages && chap.stages[0]) {
                                    window.runBattleDirect(chap, chap.stages[0]);
                                }
                            }
                        }, 500);
                    }
                    setTimeout(tryBattle, 600);
                """)
            } else if args.contains("--game-slots") || args.contains("--slots") {
                webView.evaluateJavaScript("""
                    window.__AUTO_SLOTS__ = true;
                    function ensureSlots() {
                        var b = document.getElementById('btn-start');
                        if (b && b.offsetParent !== null) b.click();
                        if (window.APP && window.APP.openSlots) {
                            APP.openSlots();
                        } else {
                            var s = document.getElementById('nav-slots');
                            if (s) s.click();
                        }
                    }
                    setInterval(function() {
                        var sl = document.getElementById('screen-slots');
                        if (sl && sl.classList.contains('active')) return;
                        ensureSlots();
                    }, 350);
                """)
            } else if args.contains("--game-hub") {
                webView.evaluateJavaScript("""
                    setInterval(function() {
                        if (window.APP && window.APP.screen === 'hub') return;
                        var b = document.getElementById('btn-start');
                        if (b && b.offsetParent !== null) b.click();
                    }, 400);
                """)
            }
            if let heroIdx = args.firstIndex(of: "--hero"), heroIdx + 1 < args.count {
                let heroKey = args[heroIdx + 1]
                webView.evaluateJavaScript("""
                    var heroApplied = false;
                    setInterval(function() {
                        if (heroApplied) return;
                        if (window.APP && window.APP.state && window.UI && window.APP.data && window.APP.screen === 'hub') {
                            APP.state.squad = ['\(heroKey)'];
                            UI.renderHub(APP.state, APP.data);
                            heroApplied = true;
                        }
                    }, 300);
                """)
            }
        }
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        if context.coordinator.lastReloadToken != reloadToken {
            context.coordinator.lastReloadToken = reloadToken
            uiView.reload()
        }
        // 同步静音状态到前端
        let jsMute = isMuted ? "true" : "false"
        uiView.evaluateJavaScript("if (window.setAppMuted) { window.setAppMuted(\(jsMute)); }")
    }
}

// MARK: - 主游戏容器视图（Apple HIG 悬浮一体化内嵌小程序胶囊）

public struct GameContainerView: View {
    public let onDismiss: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var isMuted: Bool = false
    @State private var reloadToken: UUID = UUID()
    @State private var toastMessage: String? = nil

    public init(onDismiss: @escaping () -> Void) {
        self.onDismiss = onDismiss
    }

    public var body: some View {
        GeometryReader { proxy in
            let safeTop = DeviceLayout.safeAreaTop(in: proxy.size)
            let isPad = UIDevice.current.userInterfaceIdiom == .pad

            ZStack(alignment: .top) {
                // 背景纯黑沉浸
                Color.black.ignoresSafeArea()

                // 真实 WebGL / HTML5 游戏视口（内嵌小程序）
                GameWebViewRepresentable(
                    onExitRequest: {
                        exitGracefully()
                    },
                    onGameMessage: { msg in
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                            toastMessage = msg
                        }
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                            withAnimation(.easeOut(duration: 0.2)) {
                                if toastMessage == msg {
                                    toastMessage = nil
                                }
                            }
                        }
                    },
                    isMuted: $isMuted,
                    reloadToken: reloadToken
                )
                .ignoresSafeArea()

                // Apple HIG / 微信小程序规范右上角浮动胶囊按键（[ ··· | ✕ ]）
                HStack {
                    Spacer()
                    HStack(spacing: 0) {
                        // 左半部：··· 快捷功能菜单
                        Menu {
                            Button {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                isMuted.toggle()
                            } label: {
                                Label(isMuted ? "开启音效" : "静音", systemImage: isMuted ? "speaker.wave.2" : "speaker.slash")
                            }

                            Button {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                reloadToken = UUID()
                            } label: {
                                Label("重新加载", systemImage: "arrow.clockwise")
                            }

                            Divider()

                            Button(role: .destructive) {
                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                exitGracefully()
                            } label: {
                                Label("退出小程序", systemImage: "xmark.circle")
                            }
                        } label: {
                            Image(systemName: "ellipsis")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(.white)
                                .frame(width: 42, height: 32)
                                .contentShape(Rectangle())
                        }
                        .accessibilityLabel("小程序功能选项")

                        // 中间垂直半透明分割线
                        Rectangle()
                            .fill(Color.white.opacity(0.2))
                            .frame(width: 0.8, height: 16)

                        // 右半部：✕ 关闭按键
                        Button {
                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                            exitGracefully()
                        } label: {
                            Image(systemName: "xmark")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(.white)
                                .frame(width: 42, height: 32)
                                .contentShape(Rectangle())
                        }
                        .accessibilityLabel("关闭并退出小程序")
                    }
                    .background(
                        Capsule()
                            .fill(Color.black.opacity(0.45))
                            .background(.ultraThinMaterial, in: Capsule())
                    )
                    .overlay(
                        Capsule()
                            .stroke(Color.white.opacity(0.22), lineWidth: 0.8)
                    )
                    .shadow(color: .black.opacity(0.35), radius: 6, y: 2)
                }
                .padding(.horizontal, isPad ? 24 : 16)
                .padding(.top, isPad ? 20 : max(safeTop, 20) + 4)

                // 原生 Toast 消息提示
                if let msg = toastMessage {
                    Text(msg)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 10)
                        .background(.ultraThinMaterial, in: Capsule())
                        .overlay(
                            Capsule()
                                .stroke(AuroraColor.brand.opacity(0.6), lineWidth: 1)
                        )
                        .shadow(color: .black.opacity(0.5), radius: 10, y: 4)
                        .padding(.top, safeTop + 60)
                        .transition(.scale.combined(with: .opacity))
                }
            }
        }
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
        // 遵循软件整体交互规范：支持 iOS 原生流体边缘右滑返回
        .enableSwipeBack {
            exitGracefully()
        }
    }

    private func exitGracefully() {
        onDismiss()
        dismiss()
    }
}
