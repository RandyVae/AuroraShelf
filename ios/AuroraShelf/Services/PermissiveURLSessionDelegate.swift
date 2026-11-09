import Foundation

/// 全局宽松证书信任与防盗链代理（Permissive URLSession Delegate）
/// 专为第三方跨域流媒体、短剧、漫画图床以及自签名 / 中间证书缺失 / 移动端网络代理（如 Shadowrocket / Surge / Clash / Loon）设计。
/// 在接收到 NSURLAuthenticationMethodServerTrust 挑战时直接使用凭证信任，彻底根治「此伺服器的憑證無效」阻断。
public final class PermissiveURLSessionDelegate: NSObject, URLSessionDelegate, @unchecked Sendable {
    public static let shared = PermissiveURLSessionDelegate()

    public func urlSession(
        _ session: URLSession,
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

extension URLSession {
    /// 创建自带宽松证书信任代理的 URLSession
    public static func permissive(configuration: URLSessionConfiguration = .default) -> URLSession {
        URLSession(
            configuration: configuration,
            delegate: PermissiveURLSessionDelegate.shared,
            delegateQueue: nil
        )
    }
}
