import SwiftUI
import WebKit

public struct LiveComment: Identifiable, Equatable {
    public let id: String
    public let author: String
    public let text: String

    public init(id: String, author: String, text: String) {
        self.id = id
        self.author = author
        self.text = text
    }
}

public struct LiveInlineCommentsView: View {
    public let stream: LiveStreamItem
    public var externalComments: [LiveComment] = []
    public var isLivePlaying: Bool = false
    @State private var internalComments: [LiveComment] = []
    @State private var internalStatus = "正在连接"

    public init(
        stream: LiveStreamItem,
        externalComments: [LiveComment] = [],
        isLivePlaying: Bool = false
    ) {
        self.stream = stream
        self.externalComments = externalComments
        self.isLivePlaying = isLivePlaying
    }

    private var comments: [LiveComment] {
        if stream.sourceID == LiveSourceCatalog.stripchatID {
            return externalComments
        }
        return internalComments
    }

    private var status: String {
        if stream.sourceID == LiveSourceCatalog.stripchatID {
            return isLivePlaying || !externalComments.isEmpty ? "已连接" : "正在连接"
        }
        return internalStatus
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
            HStack {
                Label("实时评论", systemImage: "bubble.left.and.bubble.right.fill")
                    .font(.headline)
                Spacer()
                Circle().fill(status == "已连接" ? .green : .secondary).frame(width: 7, height: 7)
                Text(status).font(.caption).foregroundStyle(AuroraColor.textSecondary)
            }
            if comments.isEmpty {
                Text(status == "已连接" ? "等待新评论…" : "正在连接直播聊天室…")
                    .font(.subheadline)
                    .foregroundStyle(AuroraColor.textSecondary)
                    .frame(maxWidth: .infinity, minHeight: 72)
            } else {
                LazyVStack(alignment: .leading, spacing: 8) {
                    ForEach(comments) { comment in
                        HStack(alignment: .top, spacing: 8) {
                            Text(comment.author)
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(AuroraColor.brandLabel)
                                .lineLimit(1)
                            Text(comment.text)
                                .font(.subheadline)
                                .foregroundStyle(AuroraColor.textPrimary)
                                .lineLimit(2)
                        }
                    }
                }
            }
        }
        .padding(AuroraMetrics.spacingL)
        .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous))
        .background {
            if stream.sourceID != LiveSourceCatalog.stripchatID, let url = URL(string: stream.roomUrl) {
                LiveCommentWebView(roomURL: url) { incoming, connection in
                    if let incoming {
                        internalComments = Array((internalComments + [incoming]).suffix(40))
                    }
                    internalStatus = connection
                }
                .frame(width: 1, height: 1)
                .opacity(0.01)
                .allowsHitTesting(false)
            }
        }
    }
}

private struct LiveCommentWebView: UIViewRepresentable {
    let roomURL: URL
    let onEvent: (LiveComment?, String) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onEvent: onEvent) }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        let controller = config.userContentController
        controller.add(context.coordinator, name: "liveComment")
        let script = """
        (() => {
          const sent = new Set();
          const blacklist = ['featured', 'chat color', 'fontdefault', 'rules:', 'to send a tip', 'send a message', 'you are viewing', 'chat settings', 'private conversation', 'never contact you', 'notice:', 'welcome to', 'did you mean to go'];
          const emit = () => {
            const nodes = [...document.querySelectorAll('.chat-message, [class*="ChatMessage"], .text-message, .message-text, [data-testid*="chat-message"]')];
            nodes.slice(-60).forEach((node) => {
              const text = (node.innerText || node.textContent || '').replace(/\\s+/g, ' ').trim();
              if (!text || text.length > 250) return;
              const lower = text.toLowerCase();
              if (blacklist.some(b => lower.includes(b))) return;
              const authorNode = node.querySelector('[class*=author],[class*=username],[class*=user]');
              const author = (authorNode?.innerText || '观众').trim().slice(0, 30);
              const key = author + '|' + text;
              if (sent.has(key)) return;
              sent.add(key);
              window.webkit.messageHandlers.liveComment.postMessage({author, text, key});
            });
          };
          new MutationObserver(emit).observe(document.documentElement, {subtree:true, childList:true, characterData:true});
          setInterval(emit, 2500); emit();
        })();
        """
        controller.addUserScript(WKUserScript(source: script, injectionTime: .atDocumentEnd, forMainFrameOnly: false))
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.load(URLRequest(url: roomURL))
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {}

    final class Coordinator: NSObject, WKScriptMessageHandler, WKNavigationDelegate {
        let onEvent: (LiveComment?, String) -> Void
        init(onEvent: @escaping (LiveComment?, String) -> Void) { self.onEvent = onEvent }
        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard let payload = message.body as? [String: Any], let author = payload["author"] as? String, let text = payload["text"] as? String else { return }
            onEvent(LiveComment(id: payload["key"] as? String ?? UUID().uuidString, author: author, text: text), "已连接")
        }
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) { onEvent(nil, "已连接") }
        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) { onEvent(nil, "连接失败") }
    }
}
