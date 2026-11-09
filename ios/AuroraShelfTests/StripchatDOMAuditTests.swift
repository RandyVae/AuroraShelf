import XCTest
import WebKit
@testable import AuroraShelf

final class StripchatDOMAuditTests: XCTestCase {
    private var webView: WKWebView?
    private var auditWindow: UIWindow?

    @MainActor
    func testAuditStripchatPageDOMAndVideo() async throws {
        guard ProcessInfo.processInfo.environment["RUN_IOS_LIVE_SOURCE_AUDIT"] == "1" else {
            throw XCTSkip("Set RUN_IOS_LIVE_SOURCE_AUDIT=1 to probe live Stripchat WebRTC.")
        }

        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true
        configuration.mediaTypesRequiringUserActionForPlayback = []

        let wv = WKWebView(frame: CGRect(x: 0, y: 0, width: 402, height: 226), configuration: configuration)
        wv.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
        self.webView = wv

        let win = UIWindow(frame: CGRect(x: 0, y: 0, width: 402, height: 874))
        win.rootViewController = UIViewController()
        win.rootViewController?.view.addSubview(wv)
        win.makeKeyAndVisible()
        self.auditWindow = win

        let url = URL(string: "https://zh.stripchat.com/zhutou140")!
        var request = URLRequest(url: url)
        request.setValue("https://zh.stripchat.com/", forHTTPHeaderField: "Referer")
        _ = wv.load(request)

        try await Task.sleep(nanoseconds: 6_000_000_000)

        let jsFindSidebar = """
        (function() {
            var text = document.body ? document.body.innerText : '';
            return text.slice(0, 1000);
        })();
        """

        let rawResult = try await wv.evaluateJavaScript(jsFindSidebar)
        let jsonStr = rawResult as? String ?? ""
        XCTAssertFalse(jsonStr.isEmpty)
    }
}
