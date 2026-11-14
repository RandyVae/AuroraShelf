import SwiftUI
import WebKit

struct LiveChatSheet: View {
    let stream: LiveStreamItem
    @Environment(\.dismiss) private var dismiss
    @State private var reloadID = UUID()
    @State private var chatStatus = "正在连接官方聊天室…"

    var body: some View {
        NavigationStack {
            Group {
                if let roomURL = URL(string: stream.roomUrl), roomURL.scheme == "https" {
                    VStack(spacing: 0) {
                        HStack(spacing: 8) {
                            Circle()
                                .fill(chatStatus.hasPrefix("已连接") ? .green : .secondary)
                                .frame(width: 8, height: 8)
                            Text(chatStatus)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                            Spacer()
                        }
                        .padding(.horizontal)
                        .padding(.vertical, 8)
                        .background(.bar)

                        LiveRoomWebView(roomURL: roomURL, reloadID: reloadID) { status in
                            chatStatus = status
                        }
                    }
                    .ignoresSafeArea(edges: .bottom)
                } else {
                    ContentUnavailableView("聊天室地址无效", systemImage: "bubble.left.and.bubble.right")
                }
            }
            .navigationTitle("\(stream.username) · 实时聊天室")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭", systemImage: "xmark") { dismiss() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("重新加载", systemImage: "arrow.clockwise") { reloadID = UUID() }
                        .accessibilityLabel("重新加载官方聊天室")
                }
            }
        }
    }
}

private struct LiveRoomWebView: UIViewRepresentable {
    let roomURL: URL
    let reloadID: UUID
    let onStatus: (String) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onStatus: onStatus) }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true
        configuration.mediaTypesRequiringUserActionForPlayback = .all

        // 注入年龄免责与访客条款同意 Cookie，穿透年龄门禁
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
            ("hasConfirmedAge", "1", ".chaturbate.com")
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

        // 注入自动移除年龄确认覆盖层与模拟点击同意的轻量脚本
        let autoAcceptSource = """
        (() => {
            const overlay = document.getElementById('entrance_terms_overlay') || document.getElementById('entrance_terms');
            if (overlay) { try { overlay.remove(); } catch(e) { overlay.style.display = 'none'; } }
            const acceptButtons = Array.from(document.querySelectorAll('.btn-visitors-agreement-accept, [class*=visitors-agreement-accept], button, a, [role=button]')).filter(b => {
                if (b.classList && (b.classList.contains('btn-visitors-agreement-accept') || String(b.className).includes('visitors-agreement-accept'))) return true;
                const t = (b.innerText || b.textContent || '').trim();
                return t.includes('年满') || t.includes('18岁') || t.includes('Accept') || t.includes('I am 18') || t.includes('Agree') || t.includes('进入');
            });
            if (acceptButtons.length > 0) {
                try { acceptButtons[0].click(); } catch (e) {}
            }
        })();
        """
        let userScript = WKUserScript(source: autoAcceptSource, injectionTime: .atDocumentEnd, forMainFrameOnly: false)
        configuration.userContentController.addUserScript(userScript)

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = context.coordinator
        webView.allowsBackForwardNavigationGestures = false
        webView.load(URLRequest(url: roomURL))
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        guard context.coordinator.reloadID != reloadID else { return }
        context.coordinator.reloadID = reloadID
        webView.load(URLRequest(url: roomURL))
    }

    static func dismantleUIView(_ uiView: WKWebView, coordinator: Coordinator) {
        coordinator.cleanup()
        uiView.stopLoading()
        uiView.navigationDelegate = nil
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        var reloadID = UUID()
        private let onStatus: (String) -> Void
        nonisolated(unsafe) private var pollTimer: Timer?

        init(onStatus: @escaping (String) -> Void) {
            self.onStatus = onStatus
        }

        func cleanup() {
            pollTimer?.invalidate()
            pollTimer = nil
        }

        deinit {
            pollTimer?.invalidate()
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            onStatus("已连接 · 正在监听消息")
            pollTimer?.invalidate()
            pollTimer = Timer.scheduledTimer(withTimeInterval: 3, repeats: true) { [weak self, weak webView] _ in
                Task { @MainActor [weak self, weak webView] in
                    guard let self, let webView else { return }
                    let script = """
                    (() => {
                      const overlay = document.getElementById('entrance_terms_overlay') || document.getElementById('entrance_terms');
                      if (overlay) { try { overlay.remove(); } catch(e) {} }
                      const accept = document.querySelector('.btn-visitors-agreement-accept, [class*=visitors-agreement-accept]');
                      if (accept) { try { accept.click(); } catch(e) {} }

                      const selectors = '[class*=message],[class*=chat],[data-testid*=message],[data-testid*=chat]';
                      const nodes = document.querySelectorAll(selectors).length;
                      const text = (document.body?.innerText || '').length;
                      return JSON.stringify({nodes, text});
                    })()
                    """
                    webView.evaluateJavaScript(script) { [weak self] result, _ in
                        Task { @MainActor [weak self] in
                            guard let self,
                                  let json = result as? String,
                                  let data = json.data(using: .utf8),
                                  let payload = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                                  let nodes = payload["nodes"] as? Int,
                                  let text = payload["text"] as? Int else { return }
                            self.onStatus("已连接 · 实时消息节点 \(nodes) · 文本 \(text) 字")
                        }
                    }
                }
            }
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            onStatus("聊天室加载失败，可重新加载")
        }

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction
        ) async -> WKNavigationActionPolicy {
            guard let url = navigationAction.request.url, url.scheme == "https" else { return .cancel }
            guard let host = url.host?.lowercased(),
                  ["stripchat.com", "chaturbate.com", "hornystudents.com", "mmcdn.com", "highwebmedia.com", "cb-wl.com"]
                    .contains(where: { host == $0 || host.hasSuffix(".\($0)") }) else {
                return .cancel
            }
            // Keep the room and its known chat/CDN frames, but prevent unrelated top-level browsing.
            if navigationAction.targetFrame?.isMainFrame == true,
               !["stripchat.com", "chaturbate.com", "hornystudents.com"]
                .contains(where: { host == $0 || host.hasSuffix(".\($0)") }) {
                return .cancel
            }
            return .allow
        }
    }
}
