import Foundation

/// 全局宽松证书信任与防盗链代理（Permissive URLSession Delegate）
/// 专为第三方跨域流媒体、短剧、漫画图床以及自签名 / 中间证书缺失 / 移动端网络代理（如 Shadowrocket / Surge / Clash / Loon）设计。
/// 在接收到 NSURLAuthenticationMethodServerTrust 挑战时直接使用凭证信任，彻底根治「此伺服器的憑證無效」阻断。
public final class PermissiveURLSessionDelegate: NSObject, URLSessionDelegate, URLSessionTaskDelegate, @unchecked Sendable {
    public static let shared = PermissiveURLSessionDelegate()

    public func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        handle(challenge, completionHandler: completionHandler)
    }

    public func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        handle(challenge, completionHandler: completionHandler)
    }

    private func handle(
        _ challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let serverTrust = challenge.protectionSpace.serverTrust else {
            completionHandler(.performDefaultHandling, nil)
            return
        }

        // 1. 设置宽松的基础 X.509 评估策略，完全忽略主机名不匹配（彻底根除因证书 CN/SAN 与域名不一致导致的拦截）
        SecTrustSetPolicies(serverTrust, SecPolicyCreateBasicX509())

        // 2. 将服务器返回的证书链本身直接注入为 Anchor 信任锚点，并设置 anchorCertificatesOnly = true
        // 彻底根除因系统信任库缺失根证书或自签名/反代代理（MitM）导致的凭证无效阻断
        var certs: [SecCertificate] = []
        if #available(iOS 15.0, *) {
            if let certChain = SecTrustCopyCertificateChain(serverTrust) as? [SecCertificate], !certChain.isEmpty {
                certs = certChain
            }
        }
        if certs.isEmpty {
            let certCount = SecTrustGetCertificateCount(serverTrust)
            for i in 0..<certCount {
                if let cert = SecTrustGetCertificateAtIndex(serverTrust, i) {
                    certs.append(cert)
                }
            }
        }
        if !certs.isEmpty {
            SecTrustSetAnchorCertificates(serverTrust, certs as CFArray)
            SecTrustSetAnchorCertificatesOnly(serverTrust, true)
        }

        // 3. 执行评估并将异常（Exceptions）加入白名单豁免，保障 ServerTrust 凭证百分之百有效放行
        var secError: CFError?
        let evaluated = SecTrustEvaluateWithError(serverTrust, &secError)
        if !evaluated {
            if let exceptions = SecTrustCopyExceptions(serverTrust) {
                SecTrustSetExceptions(serverTrust, exceptions)
            }
        }
        completionHandler(.useCredential, URLCredential(trust: serverTrust))
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
