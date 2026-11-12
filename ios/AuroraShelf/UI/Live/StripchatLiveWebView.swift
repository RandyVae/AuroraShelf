import SwiftUI
import WebKit

/// 统一通用纯净无头直播视口注入器
/// 深度适配 Stripchat、HornyStudents 与 Chaturbate 等多平台直播流
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

        // 注入关键年满 18 岁及协议确认 Cookie（覆盖 Stripchat 与 Chaturbate/HornyStudents）
        let cookieStore = configuration.websiteDataStore.httpCookieStore
        let cookies: [(String, String, String)] = [
            ("isAdult", "1", ".stripchat.com"),
            ("agreed_to_terms", "1", ".stripchat.com"),
            ("hasConfirmedAge", "1", ".stripchat.com"),
            ("isVisitorsAgreementAccepted", "1", ".stripchat.com"),
            ("alreadyVisited", "1", ".stripchat.com"),
            ("lang", "zh", ".stripchat.com"),
            ("agreeterms", "1", ".hornystudents.com"),
            ("agreeterms", "1", ".chaturbate.com"),
            ("isAdult", "1", ".hornystudents.com"),
            ("isAdult", "1", ".chaturbate.com"),
            ("hasConfirmedAge", "1", ".hornystudents.com"),
            ("hasConfirmedAge", "1", ".chaturbate.com"),
            ("lang", "en", ".hornystudents.com"),
            ("lang", "en", ".chaturbate.com")
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

        // 1. 文档起始预设状态：模拟合法通过年满 18 岁，注入基础参数
        let preSetupSource = """
        (function() {
            window.__isVisitorsAgreementAcceptedBeforeClientFetched = true;
            window.__auroraFill = \(isFillMode ? "true" : "false");
            try {
                document.cookie = "isVisitorsAgreementAccepted=1; path=/; domain=.stripchat.com; max-age=31536000";
                document.cookie = "isAdult=1; path=/; domain=.stripchat.com; max-age=31536000";
                document.cookie = "hasConfirmedAge=1; path=/; domain=.stripchat.com; max-age=31536000";
                document.cookie = "agreeterms=1; path=/; domain=.hornystudents.com; max-age=31536000";
                document.cookie = "agreeterms=1; path=/; domain=.chaturbate.com; max-age=31536000";
                document.cookie = "lang=en; path=/; domain=.hornystudents.com; max-age=31536000";
                document.cookie = "lang=en; path=/; domain=.chaturbate.com; max-age=31536000";
                localStorage.setItem("18PlusGenderSelected", JSON.stringify("auto"));
                localStorage.setItem("18PlusGenderSelected_expires", JSON.stringify(31536000000));
                localStorage.setItem("isVisitorsAgreementAccepted", "1");
            } catch (e) {}
        })();
        """
        let preSetupScript = WKUserScript(source: preSetupSource, injectionTime: .atDocumentStart, forMainFrameOnly: false)
        configuration.userContentController.addUserScript(preSetupScript)

        // 2. 纯净视口 CSS 注入：隐藏一切顶栏、底栏、杂质，强制全屏纯黑
        let injectionCSS = """
        html, body {
            margin: 0 !important;
            padding: 0 !important;
            width: 100% !important;
            height: 100% !important;
            overflow: hidden !important;
            background: #000000 !important;
            -webkit-user-select: none !important;
            user-select: none !important;
        }
        header, footer, nav, aside,
        #header, #footer, #static-header, .header, .evolve-header, .room-header,
        #mobile-room-root header, #room-bottom-banner,
        #portal-root, #agreement-root,
        .CookieReminderContentV2, .cookies-banner, .adblocker-notification,
        #close_entrance_terms, #entrance_terms, #entrance_terms_overlay,
        .language-mismatch-notice, .language_mismatch_notice, [class*="language-mismatch"], [class*="language_mismatch"],
        [class*="language_mismatch_update"], [class*="language_mismatch_go_back"], [class*="language_mismatch_dismiss"],
        [id*="language"], [class*="language"], #language-suggestion,
        .notification-bar, .site-notice, .lang-bar, #banner-container,
        [class*="chat-settings"], [class*="chat-toolbar"], [class*="font-list"], .c-chat-settings, .chat-settings-holder,
        .user-warning-banner, .site_alert,
        div[style*="background: rgb(255, 255, 204)"], div[style*="background-color: #fff9d7"],
        .vjs-control-bar, .vjs-big-play-button, .vjs-loading-spinner,
        [data-testid*="banner"], [data-testid*="chat"], [data-testid*="tip"],
        .related-models, .models-list, .gallery-section, .profile-section,
        .view-cam-controls, .view-cam-info, [class*="view-cam-info"], .videoPlayerControls {
            display: none !important;
            opacity: 0 !important;
            visibility: hidden !important;
            pointer-events: none !important;
            height: 0 !important;
            width: 0 !important;
            overflow: hidden !important;
        }
        #aurora-main-video {
            position: fixed !important;
            inset: 0 !important;
            top: 0 !important;
            left: 0 !important;
            width: 100% !important;
            height: 100% !important;
            z-index: 2147483647 !important;
            background: #000000 !important;
            object-fit: contain !important;
            object-position: center center !important;
            transform: none !important;
            margin: 0 !important;
            padding: 0 !important;
            visibility: visible !important;
            opacity: 1 !important;
        }
        body.aurora-fill-mode #aurora-main-video {
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

        // 3. 动态探测主视频、拉伸视口、自动同意协议与状态桥接 JS 脚本
        let jsSource = """
        (function() {
            function notifySwift(type, payload) {
                if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.liveBridge) {
                    window.webkit.messageHandlers.liveBridge.postMessage(Object.assign({ type: type }, payload || {}));
                }
            }

            function findMainVideo() {
                var selector = '.web-rtc-player-wrapper video, .player-wrapper video, #chat-player video, #basePlayer video, video';
                var vids = Array.from(document.querySelectorAll(selector));
                if (vids.length === 0) return null;
                var candidates = vids.filter(function(v) {
                    return (v.videoWidth > 0 && v.videoHeight > 0) || (v.readyState >= 1) || !v.paused;
                });
                var pool = candidates.length > 0 ? candidates : vids;
                pool.sort(function(a, b) {
                    return ((b.videoWidth || 0) * (b.videoHeight || 0)) - ((a.videoWidth || 0) * (a.videoHeight || 0));
                });
                return pool[0];
            }

            function checkAndHook() {
                // 1. 自动处理 Cookie 政策 / 进入确认弹层 / 年龄确认 / 语言提示条 / 语言不匹配警告
                const langBars = document.querySelectorAll('#language-suggestion, [id*="language"], [class*="language-choice"], [class*="language-suggest"], .notification-bar, .site-notice, .language-mismatch-notice, .language_mismatch_notice, [class*="language-mismatch"], [class*="language_mismatch"]');
                langBars.forEach(function(b) { try { b.remove(); } catch(e) { b.style.display = 'none'; } });

                const closeEntrance = document.getElementById('close_entrance_terms') || document.querySelector('.close_entrance_terms, #close_entrance_terms, [id*="close_entrance"]');
                if (closeEntrance) {
                    try { closeEntrance.click(); } catch(e) {}
                }
                const overlay = document.getElementById('entrance_terms_overlay') || document.getElementById('entrance_terms');
                if (overlay) {
                    try { overlay.remove(); } catch(e) { overlay.style.display = 'none'; }
                }

                if (typeof window.__visitorsAgreementAcceptHandler === 'function') {
                    try { window.__visitorsAgreementAcceptHandler(); } catch (e) {}
                }
                const acceptButtons = Array.from(document.querySelectorAll('.btn-visitors-agreement-accept, [class*=visitors-agreement-accept], button, a, [role=button]')).filter(b => {
                    if (b.classList && (b.classList.contains('btn-visitors-agreement-accept') || String(b.className).includes('visitors-agreement-accept'))) return true;
                    const t = (b.innerText || b.textContent || '').trim();
                    return t.includes('年满') || t.includes('18岁') || t.includes('全部接受') || t.includes('接受') ||
                           t.includes('Accept') || t.includes('I am 18') || t.includes('Agree') || t.includes('进入');
                });
                if (acceptButtons.length > 0) {
                    try { acceptButtons[0].click(); } catch (e) {}
                }

                // 2. 自动点击起播按钮并驱动 video.play()
                const playBtns = document.querySelectorAll('.view-cam-play-btn, .vjs-big-play-button, .play-button, [class*="PlayButton"], [class*="playButton"], [class*="play-btn"], [aria-label*="Play"], [aria-label*="播放"]');
                playBtns.forEach(function(b) {
                    try { b.click(); } catch(e) {}
                });
                if (window.videojs) {
                    try {
                        const players = window.videojs.getAllPlayers ? window.videojs.getAllPlayers() : [];
                        players.forEach(function(p) {
                            if (p && typeof p.play === 'function' && p.paused && p.paused()) {
                                p.play();
                            }
                        });
                    } catch(e) {}
                }

                // 3. 侦测离线与私密秀状态（结合 window.initialRoomDossier 与真实 DOM）
                if (window.initialRoomDossier) {
                    try {
                        var dossier = typeof window.initialRoomDossier === 'string' ? JSON.parse(window.initialRoomDossier) : window.initialRoomDossier;
                        if (dossier.room_status === 'private') {
                            notifySwift('error', { message: '主播当前正在进行私密秀（需付费观看）' });
                            return;
                        }
                        if (dossier.room_status === 'offline') {
                            notifySwift('error', { message: '主播当前已离线或暂未开播' });
                            return;
                        }
                        if (dossier.room_status === 'away') {
                            notifySwift('error', { message: '主播暂时离开，请稍后重试' });
                            return;
                        }
                    } catch(e) {}
                }
                if (document.querySelector('.model-offline, .king-offline, [data-testid="offline-badge"], [class*="ModelOffline"], [class*="offline-banner"], #room_status_offline, .room_offline, #offline_broadcaster_badge')) {
                    notifySwift('error', { message: '主播当前已离线或暂未开播' });
                    return;
                }
                if (document.querySelector('#private_room, .private-room, #room_status_private, .c-private-show, [class*="private-show"], .private-show-overlay')) {
                    notifySwift('error', { message: '主播当前正在进行私密秀（需付费观看）' });
                    return;
                }
                var docHtml = document.documentElement ? document.documentElement.innerHTML : '';
                if (docHtml.indexOf('\\u0022room_status\\u0022: \\u0022offline\\u0022') !== -1 ||
                    docHtml.indexOf('\\u0022room_status\\u0022:\\u0022offline\\u0022') !== -1 ||
                    docHtml.indexOf('"room_status":"offline"') !== -1 ||
                    docHtml.indexOf('"room_status": "offline"') !== -1) {
                    notifySwift('error', { message: '主播当前已离线或暂未开播' });
                    return;
                }
                if (docHtml.indexOf('\\u0022room_status\\u0022: \\u0022private\\u0022') !== -1 ||
                    docHtml.indexOf('\\u0022room_status\\u0022:\\u0022private\\u0022') !== -1 ||
                    docHtml.indexOf('"room_status":"private"') !== -1 ||
                    docHtml.indexOf('"room_status": "private"') !== -1) {
                    notifySwift('error', { message: '主播当前正在进行私密秀（需付费观看）' });
                    return;
                }

                // 4. 确保主推流全屏自适应，撑开所有祖先容器
                const mainVideo = findMainVideo();
                if (mainVideo) {
                    mainVideo.id = 'aurora-main-video';

                    const vpW = Math.round(window.visualViewport?.width || window.innerWidth || document.documentElement.clientWidth || 393);
                    const vpH = Math.round(window.visualViewport?.height || window.innerHeight || document.documentElement.clientHeight || 852);
                    const stageW = vpW + 'px';
                    const stageH = vpH + 'px';

                    mainVideo.style.setProperty('position', 'fixed', 'important');
                    mainVideo.style.setProperty('inset', '0px', 'important');
                    mainVideo.style.setProperty('top', '0px', 'important');
                    mainVideo.style.setProperty('left', '0px', 'important');
                    mainVideo.style.setProperty('width', stageW, 'important');
                    mainVideo.style.setProperty('height', stageH, 'important');
                    mainVideo.style.setProperty('max-width', 'none', 'important');
                    mainVideo.style.setProperty('max-height', 'none', 'important');
                    mainVideo.style.setProperty('z-index', '2147483647', 'important');
                    mainVideo.style.setProperty('background', '#000000', 'important');
                    mainVideo.style.setProperty('object-fit', window.__auroraFill ? 'cover' : 'contain', 'important');
                    mainVideo.style.setProperty('object-position', 'center center', 'important');
                    mainVideo.style.setProperty('margin', '0px', 'important');
                    mainVideo.style.setProperty('padding', '0px', 'important');
                    mainVideo.style.setProperty('visibility', 'visible', 'important');
                    mainVideo.style.setProperty('opacity', '1', 'important');

                    // 遍历重置所有祖先容器样式，消除 transform、contain 等包含块限制
                    var cur = mainVideo.parentElement;
                    while (cur && cur !== document.body) {
                        cur.style.setProperty('overflow', 'visible', 'important');
                        cur.style.setProperty('transform', 'none', 'important');
                        cur.style.setProperty('filter', 'none', 'important');
                        cur.style.setProperty('contain', 'none', 'important');
                        cur.style.setProperty('width', stageW, 'important');
                        cur.style.setProperty('height', stageH, 'important');
                        cur.style.setProperty('max-width', 'none', 'important');
                        cur.style.setProperty('max-height', 'none', 'important');
                        cur = cur.parentElement;
                    }

                    if (!mainVideo.dataset.auroraHooked) {
                        mainVideo.dataset.auroraHooked = 'true';
                        mainVideo.playsInline = true;
                        mainVideo.setAttribute('playsinline', '');
                        mainVideo.setAttribute('webkit-playsinline', '');

                        var notifyPlay = function() {
                            notifySwift('playing', { width: mainVideo.videoWidth, height: mainVideo.videoHeight });
                        };
                        mainVideo.addEventListener('playing', notifyPlay);
                        mainVideo.addEventListener('loadeddata', notifyPlay);
                        mainVideo.addEventListener('canplay', notifyPlay);
                        mainVideo.addEventListener('timeupdate', function() {
                            if (mainVideo.currentTime > 0 && !mainVideo.paused) {
                                notifyPlay();
                            }
                        });
                        mainVideo.addEventListener('waiting', function() {
                            notifySwift('buffering');
                        });
                        mainVideo.addEventListener('error', function() {
                            notifySwift('error', { message: '视频解码或网络异常' });
                        });
                    }

                    if ((mainVideo.readyState >= 2 || mainVideo.currentTime > 0 || mainVideo.videoWidth > 0) && !mainVideo.paused) {
                        notifySwift('playing', { width: mainVideo.videoWidth, height: mainVideo.videoHeight });
                    }
                    if (mainVideo.videoWidth > 0 && mainVideo.videoHeight > 0) {
                        notifySwift('dimension', { width: mainVideo.videoWidth, height: mainVideo.videoHeight });
                    }

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

                // 5. 提取真实用户实时评论（严禁将界面设置条、规则提示、语言警告、字体选择器等非用户发言当作评论）
                var msgNodes = document.querySelectorAll('.chat-list li, .msg-text, [data-testid="chat-message"], .message.message-base');
                msgNodes.forEach(function(node) {
                    if (node.dataset.auroraSent) return;
                    node.dataset.auroraSent = 'true';

                    // 排除设置面板、字体列表、规则条、提示栏、语言提示栏内的节点
                    if (node.closest('.chat-settings, .font-list, [class*="setting"], [class*="notice"], [class*="rule"], [class*="toolbar"], [class*="warning"], [class*="language"], [id*="language"], header, nav, footer, #header')) return;

                    // 必须存在明确的聊天用户名或发言人节点，严禁使用宽松链接选择器
                    var authorNode = node.querySelector('.username, [class*="username"], .message-username, .user-name, [data-testid="chat-author"]');
                    if (!authorNode) return;
                    var author = (authorNode.innerText || authorNode.textContent || '').replace(/[:\\s]+$/, '').trim().slice(0, 30);
                    if (!author || author === '观众' || author === '用户' || author.length < 2) return;

                    var textNode = node.querySelector('.text, [class*="text"], [class*="message-text"], [class*="content"]');
                    var rawText = textNode ? (textNode.innerText || textNode.textContent) : (node.innerText || node.textContent);
                    var text = (rawText || '').replace(author, '').replace(/[:\\s]+/, ' ').trim().replace(/\\s+/g, ' ');
                    if (!text || text.length > 200) return;

                    // 黑名单过滤（杜绝页面提示词、语言通知、字体面板等系统文本泄露）
                    var lowerText = text.toLowerCase();
                    if (lowerText.includes('featured chat') ||
                        lowerText.includes('chat settings') ||
                        lowerText.includes('viewing chaturbate') ||
                        lowerText.includes('send a message') ||
                        lowerText.includes('send tip') ||
                        lowerText.includes('no spamming') ||
                        lowerText.includes('fontdefault') ||
                        lowerText.includes('caution: the chaturbate') ||
                        lowerText.includes('rules:') ||
                        lowerText.includes('chinese (simplified)') ||
                        lowerText.includes('chinese (traditional)') ||
                        lowerText.includes('continue in english') ||
                        lowerText.includes('chat color')) {
                        return;
                    }

                    notifySwift('comment', {
                        id: Math.random().toString(36).slice(2),
                        author: author,
                        text: text
                    });
                });
            }

            if (window.__auroraHookInterval) clearInterval(window.__auroraHookInterval);
            window.__auroraHookInterval = setInterval(checkAndHook, 300);
            checkAndHook();
            window.addEventListener('load', checkAndHook);
        })();
        """
        let hookUserScript = WKUserScript(source: jsSource, injectionTime: .atDocumentEnd, forMainFrameOnly: false)
        configuration.userContentController.addUserScript(hookUserScript)

        // 注册弱引用脚本消息处理器
        let bridgeHandler = WeakScriptMessageHandler(delegate: context.coordinator)
        configuration.userContentController.add(bridgeHandler, name: "liveBridge")

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

        let host = roomURL.host ?? "zh.stripchat.com"
        let acceptLang = host.contains("stripchat") ? "zh-CN,zh;q=0.9,en;q=0.8" : "en-US,en;q=0.9"
        var request = URLRequest(url: roomURL, cachePolicy: .useProtocolCachePolicy, timeoutInterval: 35.0)
        request.setValue("https://\(host)/", forHTTPHeaderField: "Referer")
        request.setValue(acceptLang, forHTTPHeaderField: "Accept-Language")
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
            let host = roomURL.host ?? "zh.stripchat.com"
            let acceptLang = host.contains("stripchat") ? "zh-CN,zh;q=0.9,en;q=0.8" : "en-US,en;q=0.9"
            var request = URLRequest(url: roomURL, cachePolicy: .useProtocolCachePolicy, timeoutInterval: 35.0)
            request.setValue("https://\(host)/", forHTTPHeaderField: "Referer")
            request.setValue(acceptLang, forHTTPHeaderField: "Accept-Language")
            webView.load(request)
        }
    }

    public static func dismantleUIView(_ webView: WKWebView, coordinator: Coordinator) {
        coordinator.playTimeoutTask?.cancel()
        coordinator.playTimeoutTask = nil
        webView.evaluateJavaScript("if (window.__auroraHookInterval) clearInterval(window.__auroraHookInterval);", completionHandler: nil)
        webView.stopLoading()
        webView.navigationDelegate = nil
        webView.configuration.userContentController.removeAllUserScripts()
        webView.configuration.userContentController.removeScriptMessageHandler(forName: "liveBridge")
    }

    public final class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
        var parent: StripchatLiveWebView
        weak var webView: WKWebView?
        var requestedURL: URL?
        var currentFillMode: Bool = false
        var playTimeoutTask: DispatchWorkItem?

        init(_ parent: StripchatLiveWebView) {
            self.parent = parent
            super.init()
            startPlayTimeoutWatchdog()
        }

        private func startPlayTimeoutWatchdog() {
            playTimeoutTask?.cancel()
            let task = DispatchWorkItem { [weak self] in
                guard let self = self else { return }
                if self.parent.isLoading && self.parent.errorMessage == nil {
                    self.parent.isLoading = false
                    self.parent.errorMessage = "主播当前离线或暂未开播"
                }
            }
            playTimeoutTask = task
            DispatchQueue.main.asyncAfter(deadline: .now() + 25.0, execute: task)
        }

        public func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard message.name == "liveBridge",
                  let body = message.body as? [String: Any],
                  let type = body["type"] as? String else {
                return
            }

            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                switch type {
                case "playing":
                    self.playTimeoutTask?.cancel()
                    self.playTimeoutTask = nil
                    withAnimation(AuroraMotion.smooth) {
                        self.parent.isLoading = false
                        self.parent.isPlaying = true
                        self.parent.errorMessage = nil
                    }
                    if let w = body["width"] as? CGFloat, let h = body["height"] as? CGFloat, w > 0, h > 0 {
                        self.parent.onVideoDimension?(CGSize(width: w, height: h))
                    }
                case "buffering":
                    if !self.parent.isPlaying && self.parent.errorMessage == nil {
                        self.parent.isLoading = true
                    }
                case "dimension":
                    if let w = body["width"] as? CGFloat, let h = body["height"] as? CGFloat, w > 0, h > 0 {
                        self.parent.onVideoDimension?(CGSize(width: w, height: h))
                    }
                case "error":
                    self.playTimeoutTask?.cancel()
                    self.playTimeoutTask = nil
                    let msg = body["message"] as? String ?? "直播流拉取失败"
                    withAnimation(AuroraMotion.smooth) {
                        self.parent.isLoading = false
                        self.parent.isPlaying = false
                        self.parent.errorMessage = msg
                    }
                case "comment":
                    guard let commentText = body["text"] as? String, !commentText.isEmpty else { return }
                    let author = (body["author"] as? String) ?? "观众"
                    let commentId = (body["id"] as? String) ?? UUID().uuidString
                    let liveComment = LiveComment(
                        id: commentId,
                        author: author,
                        text: commentText
                    )
                    self.parent.onComment?(liveComment)
                default:
                    break
                }
            }
        }

        public func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            let nsErr = error as NSError
            if nsErr.code == NSURLErrorCancelled { return }
            DispatchQueue.main.async {
                self.parent.isLoading = false
                self.parent.errorMessage = "网络连接中断，请重试"
            }
        }

        public func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            let nsErr = error as NSError
            if nsErr.code == NSURLErrorCancelled { return }

            // 失败时自动平滑切入备用线路
            if let currentUrl = self.requestedURL {
                let currentHost = currentUrl.host ?? ""
                var fallbackUrl: URL? = nil
                if currentHost.contains("zh.stripchat.com") {
                    fallbackUrl = URL(string: currentUrl.absoluteString.replacingOccurrences(of: "zh.stripchat.com", with: "stripchat.com"))
                } else if currentHost.contains("chaturbate.com") {
                    let uname = self.parent.roomURL.lastPathComponent
                    fallbackUrl = URL(string: "https://www.hornystudents.com/\(uname)/")
                }

                if let nextUrl = fallbackUrl, nextUrl != currentUrl {
                    self.requestedURL = nextUrl
                    var req = URLRequest(url: nextUrl, cachePolicy: .useProtocolCachePolicy, timeoutInterval: 35.0)
                    req.setValue("https://\(nextUrl.host ?? "")/", forHTTPHeaderField: "Referer")
                    webView.load(req)
                    return
                }
            }

            DispatchQueue.main.async {
                self.parent.isLoading = false
                self.parent.errorMessage = "网络连接超时，请检查网络后点击重试"
            }
        }

        public func webView(
            _ webView: WKWebView,
            didReceive challenge: URLAuthenticationChallenge,
            completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
        ) {
            PermissiveURLSessionDelegate.handleChallenge(challenge, completionHandler: completionHandler)
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
