import SwiftUI
import WebKit

/// Stripchat 纯净无头 WebRTC 视口注入器
/// 彻底根除广告与网页杂质，实现 100% 真实主播推流接入与原生画幅/全屏控制
public struct StripchatLiveWebView: UIViewRepresentable {
    public let roomURL: URL
    public let isFillMode: Bool
    @Binding public var isPlaying: Bool
    @Binding public var isLoading: Bool
    @Binding public var errorMessage: String?
    public var onComment: ((LiveComment) -> Void)? = nil
    public var onVideoDimension: ((CGSize) -> Void)? = nil

    public init(
        roomURL: URL,
        isFillMode: Bool,
        isPlaying: Binding<Bool>,
        isLoading: Binding<Bool>,
        errorMessage: Binding<String?>,
        onComment: ((LiveComment) -> Void)? = nil,
        onVideoDimension: ((CGSize) -> Void)? = nil
    ) {
        self.roomURL = roomURL
        self.isFillMode = isFillMode
        self._isPlaying = isPlaying
        self._isLoading = isLoading
        self._errorMessage = errorMessage
        self.onComment = onComment
        self.onVideoDimension = onVideoDimension
    }

    public func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    public func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true
        configuration.mediaTypesRequiringUserActionForPlayback = []
        configuration.allowsAirPlayForMediaPlayback = true
        configuration.allowsPictureInPictureMediaPlayback = true

        // 注入关键年满 18 岁及协议确认 Cookie
        let cookieStore = configuration.websiteDataStore.httpCookieStore
        let cookies: [(String, String, String)] = [
            ("isAdult", "1", ".stripchat.com"),
            ("agreed_to_terms", "1", ".stripchat.com"),
            ("hasConfirmedAge", "1", ".stripchat.com"),
            ("isVisitorsAgreementAccepted", "1", ".stripchat.com"),
            ("alreadyVisited", "1", ".stripchat.com"),
            ("lang", "zh", ".stripchat.com")
        ]
        for (name, value, domain) in cookies {
            if let cookie = HTTPCookie(properties: [
                .name: name,
                .value: value,
                .domain: domain,
                .path: "/",
                .secure: "TRUE"
            ]) {
                cookieStore.setCookie(cookie)
            }
        }

        // 1. 文档起始预设状态：模拟合法通过年满 18 岁，避免破坏 React Hydration
        let preSetupSource = """
        (function() {
            window.__isVisitorsAgreementAcceptedBeforeClientFetched = true;
            window.__auroraFill = \(isFillMode ? "true" : "false");
            try {
                document.cookie = "isVisitorsAgreementAccepted=1; path=/; domain=.stripchat.com; max-age=31536000";
                document.cookie = "isAdult=1; path=/; domain=.stripchat.com; max-age=31536000";
                document.cookie = "hasConfirmedAge=1; path=/; domain=.stripchat.com; max-age=31536000";
                localStorage.setItem("18PlusGenderSelected", JSON.stringify("auto"));
                localStorage.setItem("18PlusGenderSelected_expires", JSON.stringify(31536000000));
                localStorage.setItem("isVisitorsAgreementAccepted", "1");
            } catch (e) {}
        })();
        """
        let preSetupScript = WKUserScript(source: preSetupSource, injectionTime: .atDocumentStart, forMainFrameOnly: false)
        configuration.userContentController.addUserScript(preSetupScript)

        // 2. 纯净视口 CSS 注入：锁定视口禁止页面滚动，隐藏网页一切杂质、浮动微缩与外部推荐
        let injectionCSS = """
        html, body {
            margin: 0 !important;
            padding: 0 !important;
            width: 100% !important;
            height: 100% !important;
            overflow: hidden !important;
            position: fixed !important;
            inset: 0 !important;
            top: 0 !important;
            left: 0 !important;
            background: #000000 !important;
            -webkit-user-select: none !important;
            user-select: none !important;
        }
        header, footer, nav, aside,
        #header, #footer,
        #portal-root,
        #legacy-browser-notification-root,
        #portal-container-under-header,
        #header-sub-portal-container,
        #personal-notifications-portal-container,
        #footer-portal-container,
        #bs-actions-menu-portal-container,
        .CookieReminderContentV2, .cookies-banner,
        .adblocker-notification,
        [data-testid="model-search"],
        [data-testid="king-wrapper-is-king"],
        [data-testid="ScrollBarContainer"],
        [data-testid="CookiesReminder"],
        [data-testid="view-cam-info-king-content"],
        [data-testid*="banner"], [data-testid*="chat"],
        [data-testid*="tip"], [data-testid*="king"],
        .view-cam-info-king-content, .king-wrapper,
        .scroll-bar-container, .nav-scrollable-container,
        .model-chat-nav-item, #chat-private-btn,
        #private-tab-tooltip, #public-chat-tab,
        .user-fan-club-status-btn, #join-fc-btn,
        .tooltip-v2-portal-container,
        .ModelSearch, .bs-actions-menu,
        [class*="contest"], [class*="ticker"], [class*="lottery"],
        [class*="announcement"], [class*="promo"],
        [class*="related"], [class*="Related"],
        [class*="recommend"], [class*="Recommend"],
        [class*="modelList"], [class*="ModelList"],
        .related-models, .models-list, .gallery-section, .profile-section,
        [class*="ViewCamShutterWrapper"],
        [class*="ViewCamPublic"],
        [class*="ShutterWrapper"],
        [class*="ViewCamWrapper__bottom"],
        [class*="poster"], [class*="thumbnail"], [class*="thumb"],
        [class*="shutter"], [class*="preview"], [class*="backdrop"],
        .view-cam-controls, .view-cam-controls-wrapper,
        .view-cam-info, [class*="view-cam-info"],
        .ViewCamPlayer__floatingAnchor, [class*="floatingAnchor"],
        [class*="ViewCamWrapper__left"],
        [class*="ViewCamWrapper__sidebar"],
        [class*="ViewCamWrapper__details"],
        [class*="ViewCamWrapper__info"],
        [class*="ViewCamWrapper__tags"],
        [class*="view-cam-sidebar"],
        [class*="model-details"],
        [class*="tagsContainer"],
        [class*="view-cam-model-tags"],
        [class*="promoBanner"],
        [class*="promo-banner"],
        [class*="header-sub"],
        .details-section,
        [class*="sidebar"],
        .main-layout-main-left {
            display: none !important;
            opacity: 0 !important;
            visibility: hidden !important;
            pointer-events: none !important;
            height: 0 !important;
            width: 0 !important;
            overflow: hidden !important;
        }
        #app, .wrapper, .main-layout, .main-layout-main, .view-cam-container, .view-cam-page {
            background: #000000 !important;
        }
        .model-chat, .chat-column {
            position: fixed !important;
            top: -9999px !important;
            left: -9999px !important;
            width: 400px !important;
            height: 600px !important;
            opacity: 0 !important;
            pointer-events: none !important;
            visibility: hidden !important;
            z-index: -100 !important;
            overflow: hidden !important;
        }
        body.visitor-agreement-hidden #agreement-root,
        .visitor-agreement-hidden #agreement-root,
        #agreement-root {
            display: none !important;
            opacity: 0 !important;
            pointer-events: none !important;
        }
        #aurora-main-video,
        video#aurora-main-video,
        video.video-element {
            position: fixed !important;
            inset: 0 !important;
            top: 0 !important;
            left: 0 !important;
            width: 100% !important;
            height: 100% !important;
            max-width: 100% !important;
            max-height: 100% !important;
            min-width: 100% !important;
            min-height: 100% !important;
            z-index: 2147483647 !important;
            background: #000000 !important;
            object-fit: contain !important;
            object-position: center center !important;
            transform: none !important;
            margin: 0 !important;
            padding: 0 !important;
            visibility: visible !important;
            opacity: 1 !important;
            box-sizing: border-box !important;
        }
        body.aurora-fill-mode #aurora-main-video,
        body.aurora-fill-mode video#aurora-main-video,
        body.aurora-fill-mode video.video-element {
            object-fit: cover !important;
        }
        """

        let cssScriptSource = """
        (function() {
            function addStyle() {
                if (document.getElementById('aurora-pure-viewport-style')) return;
                const style = document.createElement('style');
                style.id = 'aurora-pure-viewport-style';
                style.textContent = `\(injectionCSS)`;
                (document.head || document.documentElement).appendChild(style);
            }
            addStyle();
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', addStyle);
            }
        })();
        """
        let cssUserScript = WKUserScript(source: cssScriptSource, injectionTime: .atDocumentStart, forMainFrameOnly: false)
        configuration.userContentController.addUserScript(cssUserScript)

        // 3. 真实推流侦测、自动接受协议、提权挂载 body、全视口拉伸与状态桥接 JS 脚本
        let jsSource = """
        (function() {
            function notifySwift(type, payload) {
                if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.stripchatBridge) {
                    window.webkit.messageHandlers.stripchatBridge.postMessage(Object.assign({ type: type }, payload || {}));
                }
            }

            function findMainVideo() {
                var vids = Array.from(document.querySelectorAll('video'));
                if (!vids.length) return null;
                // 优先选取带有 blob 流或 MediaStream 且已解析出分辨率的真实推流
                var candidates = vids.filter(function(v) {
                    var isBlob = (v.src && v.src.indexOf('blob:') === 0) || (v.currentSrc && v.currentSrc.indexOf('blob:') === 0) || !!v.srcObject;
                    return isBlob || (v.videoWidth > 0 && v.videoHeight > 0);
                });
                var pool = candidates.length > 0 ? candidates : vids;
                pool.sort(function(a, b) {
                    return ((b.videoWidth || 0) * (b.videoHeight || 0)) - ((a.videoWidth || 0) * (a.videoHeight || 0));
                });
                return pool[0];
            }

            function checkAndHook() {
                // 0. 强制复位滚动位置，严防网页产生滚动从而触发次级浮动微缩播放器
                if (window.scrollY !== 0 || window.scrollX !== 0) {
                    window.scrollTo(0, 0);
                }
                if (document.documentElement && document.documentElement.scrollTop !== 0) {
                    document.documentElement.scrollTop = 0;
                }
                if (document.body && document.body.scrollTop !== 0) {
                    document.body.scrollTop = 0;
                }

                // 1. 触发官方原生的条款同意处理器，使 React 正常 Hydrate
                if (typeof window.__visitorsAgreementAcceptHandler === 'function') {
                    try { window.__visitorsAgreementAcceptHandler(); } catch (e) {}
                }
                const acceptButton = document.querySelector('.btn-visitors-agreement-accept');
                if (acceptButton) {
                    try { acceptButton.click(); } catch (e) {}
                }

                // 2. 侦测主播离线状态
                if (document.querySelector('.model-offline, .king-offline, [data-testid="offline-badge"], [class*="ModelOffline"], [class*="offline-banner"]')) {
                    notifySwift('error', { message: '主播当前离线或处于休息中' });
                    return;
                }

                // 3. 侦测私密或专属表演状态
                if (document.querySelector('.private-show, [data-testid*="private"], .group-show, [class*="groupShow"], .ticket-show, [class*="spy-show"], [class*="SpyShow"], [class*="password-protect"]')) {
                    notifySwift('error', { message: '主播正在进行私密或群组表演，需付费观看' });
                    return;
                }

                if (window.location.pathname.indexOf('/offline') !== -1) {
                    notifySwift('error', { message: '主播当前离线或已关播' });
                    return;
                }

                // 4. 自动点击起播按钮
                const playBtns = document.querySelectorAll('.view-cam-play-btn, [class*="PlayButton"], [class*="play-btn"], [aria-label*="Play"], [aria-label*="播放"]');
                playBtns.forEach(function(b) {
                    try { b.click(); } catch(e) {}
                });

                // 5. 确保主推流全屏自适应，不移动 DOM 节点，杜绝 WebKit 解码断流与黑屏
                const mainVideo = findMainVideo();
                if (mainVideo) {
                    mainVideo.id = 'aurora-main-video';
                    mainVideo.style.setProperty('position', 'fixed', 'important');
                    mainVideo.style.setProperty('inset', '0px', 'important');
                    mainVideo.style.setProperty('top', '0px', 'important');
                    mainVideo.style.setProperty('left', '0px', 'important');
                    mainVideo.style.setProperty('width', '100%', 'important');
                    mainVideo.style.setProperty('height', '100%', 'important');
                    mainVideo.style.setProperty('max-width', '100%', 'important');
                    mainVideo.style.setProperty('max-height', '100%', 'important');
                    mainVideo.style.setProperty('z-index', '2147483647', 'important');
                    mainVideo.style.setProperty('background', '#000000', 'important');
                    mainVideo.style.setProperty('object-fit', window.__auroraFill ? 'cover' : 'contain', 'important');
                    mainVideo.style.setProperty('object-position', 'center center', 'important');
                    mainVideo.style.setProperty('margin', '0px', 'important');
                    mainVideo.style.setProperty('padding', '0px', 'important');
                    mainVideo.style.setProperty('visibility', 'visible', 'important');
                    mainVideo.style.setProperty('opacity', '1', 'important');

                    // 确保祖先容器不设置裁剪与包含块
                    var cur = mainVideo.parentElement;
                    while (cur && cur !== document.body) {
                        cur.style.setProperty('overflow', 'visible', 'important');
                        cur.style.setProperty('transform', 'none', 'important');
                        cur.style.setProperty('filter', 'none', 'important');
                        cur.style.setProperty('contain', 'none', 'important');
                        cur = cur.parentElement;
                    }

                    // 彻底隐藏任何快门/封面与次级多余缩略标签与悬浮画中画容器
                    document.querySelectorAll('[class*="ViewCamPublic"], [class*="ViewCamShutterWrapper"], [class*="ShutterWrapper"]').forEach(function(s) {
                        s.style.setProperty('display', 'none', 'important');
                        s.style.setProperty('visibility', 'hidden', 'important');
                    });
                    document.querySelectorAll('video').forEach(function(v) {
                        if (v !== mainVideo) {
                            v.style.setProperty('display', 'none', 'important');
                        }
                    });

                    if (!mainVideo.dataset.auroraHooked) {
                        mainVideo.dataset.auroraHooked = 'true';
                        mainVideo.playsInline = true;
                        mainVideo.setAttribute('playsinline', '');
                        mainVideo.setAttribute('webkit-playsinline', '');

                        mainVideo.addEventListener('playing', function() {
                            notifySwift('playing');
                        });
                        mainVideo.addEventListener('loadeddata', function() {
                            notifySwift('playing');
                        });
                        mainVideo.addEventListener('canplay', function() {
                            notifySwift('playing');
                        });
                        mainVideo.addEventListener('timeupdate', function() {
                            if (mainVideo.currentTime > 0 && !mainVideo.paused) {
                                notifySwift('playing');
                            }
                        });
                        mainVideo.addEventListener('waiting', function() {
                            notifySwift('buffering');
                        });
                        mainVideo.addEventListener('error', function() {
                            notifySwift('error', { message: '视频解码或网络异常' });
                        });
                    }

                    // 如果 video 已经在播放或已解析出帧
                    if ((mainVideo.readyState >= 2 || mainVideo.currentTime > 0 || mainVideo.videoWidth > 0) && !mainVideo.paused) {
                        notifySwift('playing', { width: mainVideo.videoWidth, height: mainVideo.videoHeight });
                    }
                    if (mainVideo.videoWidth > 0 && mainVideo.videoHeight > 0) {
                        notifySwift('dimension', { width: mainVideo.videoWidth, height: mainVideo.videoHeight });
                    }

                    // 尝试自动播放并有声推流，若受限则降级静音即刻起播
                    if (mainVideo.paused) {
                        mainVideo.play().then(function() {
                            notifySwift('playing', { width: mainVideo.videoWidth, height: mainVideo.videoHeight });
                        }).catch(function() {
                            mainVideo.muted = true;
                            mainVideo.play().then(function() {
                                notifySwift('playing', { width: mainVideo.videoWidth, height: mainVideo.videoHeight });
                            }).catch(function() {});
                        });
                    }
                }

                // 6. 提取实时聊天室评论消息并桥接至 SwiftUI
                var msgNodes = document.querySelectorAll('.message, [class*="ChatMessage"], [class*="TipMessage"], [data-testid*="chat-message"]');
                msgNodes.forEach(function(node) {
                    if (node.dataset.auroraSent) return;
                    node.dataset.auroraSent = 'true';
                    var authorNode = node.querySelector('[class*="username"], [class*="author"], .message-username');
                    var author = (authorNode ? authorNode.innerText : '观众').trim().slice(0, 30);
                    var textNode = node.querySelector('[class*="text"], [class*="message-text"], [class*="content"]') || node;
                    var text = (textNode.innerText || textNode.textContent || '').replace(author, '').trim().replace(/\\s+/g, ' ');
                    if (!text || text.length > 200) return;
                    notifySwift('comment', {
                        id: Math.random().toString(36).slice(2),
                        author: author || '观众',
                        text: text
                    });
                });
            }

            if (window.__auroraHookInterval) clearInterval(window.__auroraHookInterval);
            window.__auroraHookInterval = setInterval(checkAndHook, 250);
            checkAndHook();
            window.addEventListener('load', checkAndHook);
        })();
        """
        let hookUserScript = WKUserScript(source: jsSource, injectionTime: .atDocumentEnd, forMainFrameOnly: false)
        configuration.userContentController.addUserScript(hookUserScript)

        // 注册弱引用脚本消息处理器
        let bridgeHandler = WeakScriptMessageHandler(delegate: context.coordinator)
        configuration.userContentController.add(bridgeHandler, name: "stripchatBridge")

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
        webView.navigationDelegate = context.coordinator
        context.coordinator.webView = webView
        context.coordinator.requestedURL = roomURL
        context.coordinator.currentFillMode = isFillMode

        webView.allowsBackForwardNavigationGestures = false
        webView.isOpaque = false
        webView.backgroundColor = .black
        webView.scrollView.backgroundColor = .black
        webView.scrollView.isScrollEnabled = false
        webView.scrollView.bounces = false
        webView.scrollView.contentInsetAdjustmentBehavior = .never

        var request = URLRequest(url: roomURL)
        request.setValue("https://zh.stripchat.com/", forHTTPHeaderField: "Referer")
        request.setValue("zh-CN,zh;q=0.9", forHTTPHeaderField: "Accept-Language")
        webView.load(request)

        return webView
    }

    public func updateUIView(_ webView: WKWebView, context: Context) {
        context.coordinator.parent = self

        // 1. 动态切换画幅（Fit / Fill），无重载无黑屏
        if context.coordinator.currentFillMode != isFillMode {
            context.coordinator.currentFillMode = isFillMode
            let js = """
            window.__auroraFill = \(isFillMode ? "true" : "false");
            var v = document.getElementById('aurora-main-video');
            if (v) {
                v.style.setProperty('object-fit', '\(isFillMode ? "cover" : "contain")', 'important');
            }
            if (\(isFillMode ? "true" : "false")) {
                document.body.classList.add('aurora-fill-mode');
            } else {
                document.body.classList.remove('aurora-fill-mode');
            }
            """
            webView.evaluateJavaScript(js, completionHandler: nil)
        }

        // 2. 房间地址变化时重载
        if context.coordinator.requestedURL != roomURL {
            context.coordinator.requestedURL = roomURL
            var request = URLRequest(url: roomURL)
            request.setValue("https://zh.stripchat.com/", forHTTPHeaderField: "Referer")
            request.setValue("zh-CN,zh;q=0.9", forHTTPHeaderField: "Accept-Language")
            webView.load(request)
        }
    }

    public static func dismantleUIView(_ webView: WKWebView, coordinator: Coordinator) {
        coordinator.playTimeoutTask?.cancel()
        coordinator.playTimeoutTask = nil
        webView.evaluateJavaScript("if (window.__auroraHookInterval) clearInterval(window.__auroraHookInterval);", completionHandler: nil)
        webView.configuration.userContentController.removeScriptMessageHandler(forName: "stripchatBridge")
        webView.stopLoading()
        webView.loadHTMLString("", baseURL: nil)
    }

    public final class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
        var parent: StripchatLiveWebView
        weak var webView: WKWebView?
        var requestedURL: URL?
        var currentFillMode: Bool?
        var playTimeoutTask: DispatchWorkItem?

        init(_ parent: StripchatLiveWebView) {
            self.parent = parent
            super.init()
            scheduleTimeout()
        }

        private func scheduleTimeout() {
            playTimeoutTask?.cancel()
            let task = DispatchWorkItem { [weak self] in
                guard let self = self, self.parent.isLoading else { return }
                self.webView?.evaluateJavaScript("""
                (function() {
                    var v = document.getElementById('aurora-main-video') || document.querySelector('video');
                    if (!v) return 'none';
                    if (!v.paused && (v.currentTime > 0 || v.readyState >= 2)) return 'playing';
                    return 'loading';
                })()
                """) { result, _ in
                    let status = (result as? String) ?? "none"
                    DispatchQueue.main.async {
                        if status == "playing" {
                            self.parent.isLoading = false
                            self.parent.isPlaying = true
                        } else {
                            self.parent.isLoading = false
                            self.parent.isPlaying = false
                            self.parent.errorMessage = "直播连接超时，请检查网络或重新连接"
                        }
                    }
                }
            }
            playTimeoutTask = task
            DispatchQueue.main.asyncAfter(deadline: .now() + 15.0, execute: task)
        }

        public func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard message.name == "stripchatBridge",
                  let dict = message.body as? [String: Any],
                  let type = dict["type"] as? String else { return }

            DispatchQueue.main.async {
                switch type {
                case "playing":
                    self.playTimeoutTask?.cancel()
                    self.parent.isLoading = false
                    self.parent.isPlaying = true
                    self.parent.errorMessage = nil
                    if let w = dict["width"] as? Double, let h = dict["height"] as? Double, w > 0, h > 0 {
                        self.parent.onVideoDimension?(CGSize(width: w, height: h))
                    }
                case "dimension":
                    if let w = dict["width"] as? Double, let h = dict["height"] as? Double, w > 0, h > 0 {
                        self.parent.onVideoDimension?(CGSize(width: w, height: h))
                    }
                case "buffering":
                    break
                case "error":
                    self.playTimeoutTask?.cancel()
                    self.parent.isLoading = false
                    self.parent.isPlaying = false
                    self.parent.errorMessage = dict["message"] as? String ?? "直播播放异常"
                case "comment":
                    if let author = dict["author"] as? String, let text = dict["text"] as? String {
                        let id = dict["id"] as? String ?? UUID().uuidString
                        let comment = LiveComment(id: id, author: author, text: text)
                        self.parent.onComment?(comment)
                    }
                default:
                    break
                }
            }
        }

        @MainActor
        public func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction) async -> WKNavigationActionPolicy {
            guard let url = navigationAction.request.url,
                  url.scheme == "https" else {
                return .cancel
            }
            guard navigationAction.targetFrame?.isMainFrame == true else { return .allow }
            guard let host = url.host?.lowercased(), host == "stripchat.com" || host.hasSuffix(".stripchat.com") else { return .cancel }
            return .allow
        }

        public func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            let js = "window.__auroraFill = \(parent.isFillMode ? "true" : "false"); if (typeof checkAndHook === 'function') { checkAndHook(); }"
            webView.evaluateJavaScript(js, completionHandler: nil)
        }

        public func webView(
            _ webView: WKWebView,
            didReceive challenge: URLAuthenticationChallenge,
            completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
        ) {
            if challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
               let serverTrust = challenge.protectionSpace.serverTrust {
                completionHandler(.useCredential, URLCredential(trust: serverTrust))
            } else {
                completionHandler(.performDefaultHandling, nil)
            }
        }
    }
}

private final class WeakScriptMessageHandler: NSObject, WKScriptMessageHandler {
    private weak var delegate: WKScriptMessageHandler?

    init(delegate: WKScriptMessageHandler) {
        self.delegate = delegate
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        delegate?.userContentController(userContentController, didReceive: message)
    }
}
