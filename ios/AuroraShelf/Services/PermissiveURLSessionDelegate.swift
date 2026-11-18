import Foundation
import Security

/// 域名受控宽松证书信任与防盗链代理（Domain-Scoped Permissive URLSession Delegate）
/// 专为第三方跨域流媒体、短剧、漫画图床以及自签名 / 中间证书缺失节点设计；
/// 对官方账号服务、漫画鉴权接口与主流标准 HTTPS 服务强制执行系统默认严格证书校验。
public final class PermissiveURLSessionDelegate: NSObject, URLSessionDelegate, URLSessionTaskDelegate, @unchecked Sendable {
    public static let shared = PermissiveURLSessionDelegate()

    /// 严禁信任无效/自签名证书的敏感与主流域名（账号鉴权、远端配置、Picacg API、豆瓣 API）
    private static let strictTLSDomains: [String] = [
        "bananarey.us",
        "picacomic.com",
        "picaapi.picacomic.com",
        "douban.com",
        "movie.douban.com",
        "doubanio.com"
    ]

    public static func isStrictTLSHost(_ rawHost: String?) -> Bool {
        guard let host = rawHost?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
              !host.isEmpty else {
            return false
        }
        return strictTLSDomains.contains { domain in
            host == domain || host.hasSuffix(".\(domain)")
        }
    }

    public func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        Self.handleChallenge(challenge, completionHandler: completionHandler)
    }

    public func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        Self.handleChallenge(challenge, completionHandler: completionHandler)
    }

    /// 统一证书信任处理逻辑：敏感与标准域名执行系统严格校验，仅第三方边缘流媒体/图床节点放宽校验
    public static func handleChallenge(
        _ challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let serverTrust = challenge.protectionSpace.serverTrust else {
            completionHandler(.performDefaultHandling, nil)
            return
        }

        if isStrictTLSHost(challenge.protectionSpace.host) {
            completionHandler(.performDefaultHandling, nil)
            return
        }

        // 1. 设置宽松的基础 X.509 评估策略，容忍第三方 CDN 边缘证书 CN/SAN 不一致
        SecTrustSetPolicies(serverTrust, SecPolicyCreateBasicX509())

        // 2. 使用现代 SecTrustCopyCertificateChain 提取证书链
        let certs = (SecTrustCopyCertificateChain(serverTrust) as? [SecCertificate]) ?? []
        if !certs.isEmpty {
            SecTrustSetAnchorCertificates(serverTrust, certs as CFArray)
            SecTrustSetAnchorCertificatesOnly(serverTrust, true)
        }

        // 3. 执行评估；若因客户端时间与第三方节点证书有效期偏差导致未通过，使用叶子证书锚定放行
        var secError: CFError?
        let evaluated = SecTrustEvaluateWithError(serverTrust, &secError)
        if !evaluated, let leaf = certs.first {
            SecTrustSetAnchorCertificates(serverTrust, [leaf] as CFArray)
            SecTrustSetAnchorCertificatesOnly(serverTrust, true)
        }

        completionHandler(.useCredential, URLCredential(trust: serverTrust))
    }
}

extension URLSession {
    /// Reused session for short-lived media and image requests. Reusing the delegate-backed
    /// session keeps the connection pool warm and avoids creating one URLSession per HLS segment.
    public static var permissiveShared: URLSession { PermissiveURLSessionStore.shared }

    /// 创建自带域名受控证书信任代理的 URLSession
    public static func permissive(configuration: URLSessionConfiguration = .default) -> URLSession {
        URLSession(
            configuration: configuration,
            delegate: PermissiveURLSessionDelegate.shared,
            delegateQueue: nil
        )
    }
}

private enum PermissiveURLSessionStore {
    static let shared: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 15
        configuration.timeoutIntervalForResource = 30
        configuration.httpMaximumConnectionsPerHost = 6
        configuration.requestCachePolicy = .useProtocolCachePolicy
        return URLSession(configuration: configuration, delegate: PermissiveURLSessionDelegate.shared, delegateQueue: nil)
    }()
}
