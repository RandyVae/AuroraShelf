import SwiftUI
import WebKit

// MARK: - 安全本地资源 Scheme 处理器（替代私有 KVC allowFileAccessFromFileURLs，提供稳定 Origin aurora-game://app）

final class AuroraGameSchemeHandler: NSObject, WKURLSchemeHandler, @unchecked Sendable {
    static let scheme = "aurora-game"
    static let host = "app"

    private let lock = NSLock()
    private var activeTaskIDs: Set<ObjectIdentifier> = []

    private func markStarted(_ task: any WKURLSchemeTask) {
        lock.lock()
        activeTaskIDs.insert(ObjectIdentifier(task as AnyObject))
        lock.unlock()
    }

    @discardableResult
    private func markFinished(_ task: any WKURLSchemeTask) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return activeTaskIDs.remove(ObjectIdentifier(task as AnyObject)) != nil
    }

    private func isActive(_ task: any WKURLSchemeTask) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return activeTaskIDs.contains(ObjectIdentifier(task as AnyObject))
    }

    func webView(_ webView: WKWebView, start urlSchemeTask: any WKURLSchemeTask) {
        markStarted(urlSchemeTask)
        let request = urlSchemeTask.request
        guard let requestURL = request.url,
              requestURL.scheme?.lowercased() == Self.scheme,
              requestURL.host?.lowercased() == Self.host else {
            finishWithError(urlSchemeTask, code: NSURLErrorBadURL)
            return
        }

        guard let rawWWWRoot = Bundle.main.url(forResource: "www", withExtension: nil) else {
            finishWithError(urlSchemeTask, code: NSURLErrorFileDoesNotExist)
            return
        }
        let wwwRoot = rawWWWRoot.resolvingSymlinksInPath().standardizedFileURL
        let rawPath = requestURL.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let relativePath = rawPath.isEmpty ? "index.html" : rawPath

        let pathParts = relativePath.split(separator: "/", omittingEmptySubsequences: false).map(String.init)
        guard !pathParts.contains(".."), !pathParts.contains(".") else {
            finishWithError(urlSchemeTask, code: NSURLErrorNoPermissionsToReadFile)
            return
        }

        var candidateURL = wwwRoot
        for part in pathParts where !part.isEmpty {
            candidateURL.appendPathComponent(part)
        }
        let resolvedFileURL = candidateURL.resolvingSymlinksInPath().standardizedFileURL
        let rootPrefix = wwwRoot.path.hasSuffix("/") ? wwwRoot.path : (wwwRoot.path + "/")
        guard resolvedFileURL.path.hasPrefix(rootPrefix) else {
            finishWithError(urlSchemeTask, code: NSURLErrorNoPermissionsToReadFile)
            return
        }

        var isDir: ObjCBool = false
        guard FileManager.default.fileExists(atPath: resolvedFileURL.path, isDirectory: &isDir), !isDir.boolValue,
              let fileData = try? Data(contentsOf: resolvedFileURL, options: [.mappedIfSafe]) else {
            finishWithError(urlSchemeTask, code: NSURLErrorFileDoesNotExist)
            return
        }

        let mimeType = Self.mimeType(forExtension: resolvedFileURL.pathExtension)
        let totalLength = Int64(fileData.count)
        let rangeHeader = request.value(forHTTPHeaderField: "Range")

        var statusCode = 200
        var sliceData = fileData
        var headers: [String: String] = [
            "Content-Type": mimeType,
            "Accept-Ranges": "bytes",
            "Access-Control-Allow-Origin": "\(Self.scheme)://\(Self.host)",
            "Cache-Control": "no-cache"
        ]

        if let rangeHeader,
           let byteRange = OfflineLocalHTTPServer.parseByteRange(headerValue: rangeHeader, totalLength: totalLength) {
            statusCode = 206
            let lower = Int(byteRange.lowerBound)
            let upper = Int(byteRange.upperBound)
            sliceData = fileData.subdata(in: lower..<(upper + 1))
            headers["Content-Range"] = "bytes \(lower)-\(upper)/\(totalLength)"
        }
        headers["Content-Length"] = String(sliceData.count)

        guard isActive(urlSchemeTask) else { return }
        if let response = HTTPURLResponse(
            url: requestURL,
            statusCode: statusCode,
            httpVersion: "HTTP/1.1",
            headerFields: headers
        ) {
            urlSchemeTask.didReceive(response)
            if request.httpMethod?.uppercased() != "HEAD" {
                urlSchemeTask.didReceive(sliceData)
            }
            if markFinished(urlSchemeTask) {
                urlSchemeTask.didFinish()
            }
        } else {
            finishWithError(urlSchemeTask, code: NSURLErrorCannotParseResponse)
        }
    }

    func webView(_ webView: WKWebView, stop urlSchemeTask: any WKURLSchemeTask) {
        _ = markFinished(urlSchemeTask)
    }

    private func finishWithError(_ task: any WKURLSchemeTask, code: Int) {
        guard markFinished(task) else { return }
        task.didFailWithError(NSError(domain: NSURLErrorDomain, code: code))
    }

    static func mimeType(forExtension rawExt: String) -> String {
        switch rawExt.lowercased() {
        case "html", "htm": return "text/html; charset=utf-8"
        case "js", "mjs": return "application/javascript; charset=utf-8"
        case "css": return "text/css; charset=utf-8"
        case "json", "skel": return "application/json; charset=utf-8"
        case "atlas", "txt": return "text/plain; charset=utf-8"
        case "png": return "image/png"
        case "jpg", "jpeg": return "image/jpeg"
        case "webp": return "image/webp"
        case "gif": return "image/gif"
        case "svg": return "image/svg+xml"
        case "avif": return "image/avif"
        case "mp3": return "audio/mpeg"
        case "ogg": return "audio/ogg"
        case "wav": return "audio/wav"
        case "m4a", "aac": return "audio/mp4"
        case "mp4": return "video/mp4"
        case "webm": return "video/webm"
        case "wasm": return "application/wasm"
        case "woff2": return "font/woff2"
        case "woff": return "font/woff"
        case "ttf": return "font/ttf"
        case "otf": return "font/otf"
        default: return "application/octet-stream"
        }
    }
}

@MainActor
final class GameWebViewSessionRef {
    weak var webView: WKWebView?

    func pauseGame() {
        webView?.evaluateJavaScript("if (window.APP && typeof window.APP.pauseGame === 'function') { window.APP.pauseGame(); }", completionHandler: nil)
    }

    func resumeGame() {
        webView?.evaluateJavaScript("if (window.APP && typeof window.APP.resumeGame === 'function') { window.APP.resumeGame(); }", completionHandler: nil)
    }

    func tryHandleBack(orExit fallbackExit: @escaping @MainActor () -> Void) {
        guard let webView else {
            fallbackExit()
            return
        }
        webView.evaluateJavaScript("(typeof window.handleAppBack === 'function') ? Boolean(window.handleAppBack()) : false") { result, error in
            let handled = (error == nil) && ((result as? Bool) == true)
            if !handled {
                Task { @MainActor in
                    fallbackExit()
                }
            }
        }
    }
}

// MARK: - 原生 WKWebView 包装器（支持 AuroraSaveBridge 持久化、触觉反馈与全屏沉浸渲染）

struct GameWebViewRepresentable: UIViewRepresentable {
    let sessionRef: GameWebViewSessionRef
    let onExitRequest: () -> Void
    let onGameMessage: (String) -> Void
    @Binding var isMuted: Bool
    let reloadToken: UUID

    private static func makeSaveBridgeBootstrapScript() -> String {
        let initialJSON = GameSaveStore.shared.snapshotJSONLiteral()
        return """
        (function() {
            var store = \(initialJSON);
            function postBridge(payload) {
                try {
                    if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.NativeBridge) {
                        window.webkit.messageHandlers.NativeBridge.postMessage(payload);
                    }
                } catch (e) {}
            }
            try {
                if (Object.keys(store).length === 0 && window.localStorage) {
                    for (var i = 0; i < window.localStorage.length; i++) {
                        var lk = window.localStorage.key(i);
                        if (lk) {
                            var lv = window.localStorage.getItem(lk);
                            if (typeof lv === 'string') {
                                store[lk] = lv;
                                postBridge({ type: 'saveSetKey', key: lk, value: lv });
                            }
                        }
                    }
                }
            } catch (e) {}

            window.AuroraSaveBridge = {
                get: function() {
                    return Object.prototype.hasOwnProperty.call(store, 'lg_save') ? store['lg_save'] : null;
                },
                set: function(raw) {
                    var str = String(raw == null ? '' : raw);
                    store['lg_save'] = str;
                    try { if (window.localStorage) window.localStorage.setItem('lg_save', str); } catch (e) {}
                    postBridge({ type: 'saveSet', value: str });
                },
                clear: function() {
                    delete store['lg_save'];
                    try { if (window.localStorage) window.localStorage.removeItem('lg_save'); } catch (e) {}
                    postBridge({ type: 'saveClear' });
                },
                getKey: function(k) {
                    var key = String(k || '');
                    return Object.prototype.hasOwnProperty.call(store, key) ? store[key] : null;
                },
                setKey: function(k, raw) {
                    var key = String(k || '');
                    if (!key) return;
                    var str = String(raw == null ? '' : raw);
                    store[key] = str;
                    try { if (window.localStorage) window.localStorage.setItem(key, str); } catch (e) {}
                    postBridge({ type: 'saveSetKey', key: key, value: str });
                },
                clearKey: function(k) {
                    var key = String(k || '');
                    if (!key) return;
                    delete store[key];
                    try { if (window.localStorage) window.localStorage.removeItem(key); } catch (e) {}
                    postBridge({ type: 'saveClearKey', key: key });
                }
            };
        })();
        """
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.allowsPictureInPictureMediaPlayback = false
        config.mediaTypesRequiringUserActionForPlayback = []
        config.setURLSchemeHandler(AuroraGameSchemeHandler(), forURLScheme: AuroraGameSchemeHandler.scheme)

        let preferences = WKWebpagePreferences()
        preferences.allowsContentJavaScript = true
        config.defaultWebpagePreferences = preferences

        let contentController = WKUserContentController()
        let bootstrapScript = WKUserScript(
            source: Self.makeSaveBridgeBootstrapScript(),
            injectionTime: .atDocumentStart,
            forMainFrameOnly: true
        )
        contentController.addUserScript(bootstrapScript)
        contentController.add(WeakGameScriptMessageHandler(delegate: context.coordinator), name: "NativeBridge")
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
        sessionRef.webView = webView

        if let entryURL = Self.buildEntryURL() {
            webView.load(URLRequest(url: entryURL))
        }

        return webView
    }

    private static func buildEntryURL() -> URL? {
        var components = URLComponents()
        components.scheme = AuroraGameSchemeHandler.scheme
        components.host = AuroraGameSchemeHandler.host
        components.path = "/index.html"

        #if DEBUG
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
        if !queryItems.isEmpty {
            components.queryItems = queryItems
        }
        #endif

        return components.url
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(onExitRequest: onExitRequest, onGameMessage: onGameMessage)
    }

    @MainActor
    final class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler, UIScrollViewDelegate {
        weak var webView: WKWebView?
        var lastReloadToken: UUID?
        var lastMuted: Bool?
        let onExitRequest: () -> Void
        let onGameMessage: (String) -> Void

        init(onExitRequest: @escaping () -> Void, onGameMessage: @escaping (String) -> Void) {
            self.onExitRequest = onExitRequest
            self.onGameMessage = onGameMessage
        }

        func viewForZooming(in scrollView: UIScrollView) -> UIView? {
            nil
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

        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard message.name == "NativeBridge", let dict = message.body as? [String: Any] else { return }
            let type = dict["type"] as? String ?? ""

            switch type {
            case "saveSet":
                if let raw = dict["value"] as? String {
                    GameSaveStore.shared.set(raw)
                }
            case "saveClear":
                GameSaveStore.shared.clear()
            case "saveSetKey":
                if let key = dict["key"] as? String, let raw = dict["value"] as? String {
                    GameSaveStore.shared.setKey(key, raw: raw)
                }
            case "saveClearKey":
                if let key = dict["key"] as? String {
                    GameSaveStore.shared.clearKey(key)
                }
            case "haptic":
                let style = dict["style"] as? String ?? "medium"
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
            case "exitGame":
                onExitRequest()
            case "toast":
                if let msg = dict["message"] as? String {
                    onGameMessage(msg)
                }
            case "log":
                #if DEBUG
                if let msg = dict["message"] as? String {
                    let lvl = dict["level"] as? String ?? "info"
                    print("[NetgreyGameWeb:\(lvl)] \(msg)")
                }
                #endif
            default:
                break
            }
        }

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping @MainActor (WKNavigationActionPolicy) -> Void
        ) {
            guard let url = navigationAction.request.url else {
                decisionHandler(.cancel)
                return
            }
            if url.absoluteString == "about:blank" {
                decisionHandler(.allow)
                return
            }
            if url.scheme?.lowercased() == AuroraGameSchemeHandler.scheme,
               url.host?.lowercased() == AuroraGameSchemeHandler.host {
                decisionHandler(.allow)
                return
            }
            decisionHandler(.cancel)
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            disableDoubleTapZoom(in: webView)
            #if DEBUG
            let args = CommandLine.arguments
            if args.contains("--game-battle") || args.contains("--battle") {
                webView.evaluateJavaScript("""
                    (function() {
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
                    })();
                """)
            } else if args.contains("--game-slots") || args.contains("--slots") {
                webView.evaluateJavaScript("""
                    (function() {
                        window.__AUTO_SLOTS__ = true;
                        var attempts = 0;
                        var timerId = setInterval(function() {
                            attempts++;
                            var sl = document.getElementById('screen-slots');
                            if ((sl && sl.classList.contains('active')) || attempts >= 25) {
                                clearInterval(timerId);
                                return;
                            }
                            var b = document.getElementById('btn-start');
                            if (b && b.offsetParent !== null) b.click();
                            if (window.APP && window.APP.openSlots) {
                                APP.openSlots();
                            } else {
                                var s = document.getElementById('nav-slots');
                                if (s) s.click();
                            }
                        }, 350);
                    })();
                """)
            } else if args.contains("--game-hub") {
                webView.evaluateJavaScript("""
                    (function() {
                        var attempts = 0;
                        var timerId = setInterval(function() {
                            attempts++;
                            if ((window.APP && window.APP.screen === 'hub') || attempts >= 25) {
                                clearInterval(timerId);
                                return;
                            }
                            var b = document.getElementById('btn-start');
                            if (b && b.offsetParent !== null) b.click();
                        }, 400);
                    })();
                """)
            }
            if let heroIdx = args.firstIndex(of: "--hero"), heroIdx + 1 < args.count {
                let rawHeroKey = args[heroIdx + 1].filter { $0.isLetter || $0.isNumber || $0 == "_" || $0 == "-" }
                webView.evaluateJavaScript("""
                    (function() {
                        var attempts = 0;
                        var timerId = setInterval(function() {
                            attempts++;
                            if (attempts >= 30) { clearInterval(timerId); return; }
                            if (window.APP && window.APP.state && window.UI && window.APP.data && window.APP.screen === 'hub') {
                                APP.state.squad = ['\(rawHeroKey)'];
                                UI.renderHub(APP.state, APP.data);
                                clearInterval(timerId);
                            }
                        }, 300);
                    })();
                """)
            }
            #endif
        }
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        sessionRef.webView = uiView
        if context.coordinator.lastReloadToken != reloadToken {
            context.coordinator.lastReloadToken = reloadToken
            context.coordinator.lastMuted = nil
            // Refresh bootstrap save snapshot before reloading
            uiView.configuration.userContentController.removeAllUserScripts()
            let freshBootstrap = WKUserScript(
                source: Self.makeSaveBridgeBootstrapScript(),
                injectionTime: .atDocumentStart,
                forMainFrameOnly: true
            )
            uiView.configuration.userContentController.addUserScript(freshBootstrap)
            uiView.reload()
        }
        if context.coordinator.lastMuted != isMuted {
            context.coordinator.lastMuted = isMuted
            let jsMute = isMuted ? "true" : "false"
            uiView.evaluateJavaScript("if (window.setAppMuted) { window.setAppMuted(\(jsMute)); }")
        }
    }

    static func dismantleUIView(_ uiView: WKWebView, coordinator: Coordinator) {
        uiView.evaluateJavaScript("if (window.APP && typeof window.APP.destroyGame === 'function') { try { window.APP.destroyGame(); } catch (e) {} }", completionHandler: nil)
        uiView.pauseAllMediaPlayback()
        uiView.closeAllMediaPresentations(completionHandler: {})
        uiView.stopLoading()
        uiView.navigationDelegate = nil
        uiView.scrollView.delegate = nil
        uiView.configuration.userContentController.removeAllUserScripts()
        uiView.configuration.userContentController.removeScriptMessageHandler(forName: "NativeBridge")
        uiView.loadHTMLString("", baseURL: nil)
    }
}

private final class WeakGameScriptMessageHandler: NSObject, WKScriptMessageHandler {
    private weak var delegate: WKScriptMessageHandler?

    init(delegate: WKScriptMessageHandler) {
        self.delegate = delegate
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        delegate?.userContentController(userContentController, didReceive: message)
    }
}

// MARK: - 主游戏容器视图（Apple HIG 悬浮一体化内嵌小程序胶囊，支持安全区避让与前后台音频/空闲锁生命周期）

public struct GameContainerView: View {
    public let onDismiss: () -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @State private var sessionRef = GameWebViewSessionRef()
    @State private var isMuted: Bool = false
    @State private var reloadToken: UUID = UUID()
    @State private var toastMessage: String?
    @State private var measuredSafeInsets: EdgeInsets = EdgeInsets()

    public init(onDismiss: @escaping () -> Void) {
        self.onDismiss = onDismiss
    }

    public var body: some View {
        ZStack {
            Color.clear
                .onGeometryChange(for: EdgeInsets.self) { proxy in
                    proxy.safeAreaInsets
                } action: { insets in
                    if insets != measuredSafeInsets {
                        measuredSafeInsets = insets
                    }
                }
                .allowsHitTesting(false)

            GeometryReader { proxy in
                let isPad = UIDevice.current.userInterfaceIdiom == .pad
                let fallbackTop = DeviceLayout.safeAreaTop(in: proxy.size)
                let topInset = max(measuredSafeInsets.top, isPad ? 16 : fallbackTop)
                let trailingInset = max(measuredSafeInsets.trailing + 12, isPad ? 24 : 16)

                ZStack(alignment: .top) {
                    Color.black.ignoresSafeArea()

                    GameWebViewRepresentable(
                        sessionRef: sessionRef,
                        onExitRequest: {
                            exitImmediately()
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

                    // Apple HIG 右上角浮动胶囊按键（[ ··· | ✕ ]，44×44pt 触控热区，兼容横屏灵动岛/刘海与 iPad 安全区）
                    HStack {
                        Spacer()
                        HStack(spacing: 0) {
                            Menu {
                                Button {
                                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                    isMuted.toggle()
                                } label: {
                                    Label(isMuted ? "开启音效" : "静音", systemImage: isMuted ? "speaker.wave.2" : "speaker.slash")
                                }

                                Button {
                                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                    sessionRef.tryHandleBack {
                                        exitImmediately()
                                    }
                                } label: {
                                    Label("返回上一级", systemImage: "chevron.backward")
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
                                    exitImmediately()
                                } label: {
                                    Label("退出小程序", systemImage: "xmark.circle")
                                }
                            } label: {
                                Image(systemName: "ellipsis")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundStyle(.white)
                                    .frame(width: 46, height: AuroraMetrics.minTouchTarget)
                                    .contentShape(Rectangle())
                            }
                            .accessibilityLabel("小程序功能选项")

                            Rectangle()
                                .fill(Color.white.opacity(0.22))
                                .frame(width: 0.8, height: 18)

                            Button {
                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                exitGracefully()
                            } label: {
                                Image(systemName: "xmark")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundStyle(.white)
                                    .frame(width: 46, height: AuroraMetrics.minTouchTarget)
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
                    .padding(.trailing, trailingInset)
                    .padding(.top, topInset + 4)

                    if let msg = toastMessage {
                        Text(msg)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 18)
                            .padding(.vertical, 10)
                            .background(Color.black.opacity(0.75), in: Capsule())
                            .overlay(
                                Capsule()
                                    .stroke(AuroraColor.brand.opacity(0.6), lineWidth: 1)
                            )
                            .shadow(color: .black.opacity(0.5), radius: 10, y: 4)
                            .padding(.top, topInset + 56)
                            .transition(.scale.combined(with: .opacity))
                    }
                }
            }
            .ignoresSafeArea()
        }
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
        .onAppear {
            PictureInPictureManager.shared.stopAndReleasePiPIfNeeded()
            UIApplication.shared.isIdleTimerDisabled = true
        }
        .onChange(of: scenePhase) { _, newPhase in
            switch newPhase {
            case .active:
                UIApplication.shared.isIdleTimerDisabled = true
                sessionRef.resumeGame()
            case .inactive, .background:
                UIApplication.shared.isIdleTimerDisabled = false
                sessionRef.pauseGame()
            @unknown default:
                break
            }
        }
        .onDisappear {
            UIApplication.shared.isIdleTimerDisabled = false
            sessionRef.pauseGame()
        }
        // 禁用全内容区右滑返回，仅保留最左侧边缘手势，防止游戏内滑动误退
        .enableSwipeBack(isEnabled: true, allowsContentSwipe: false) {
            exitGracefully()
        }
    }

    private func exitGracefully() {
        sessionRef.tryHandleBack {
            exitImmediately()
        }
    }

    private func exitImmediately() {
        UIApplication.shared.isIdleTimerDisabled = false
        onDismiss()
        dismiss()
    }
}
