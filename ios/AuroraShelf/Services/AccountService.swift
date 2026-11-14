import Foundation
import Security

public struct AccountProfile: Codable, Equatable, Sendable {
    public let id: String
    public let email: String
    public let displayName: String
    public let isVerified: Bool
    public let subscriptionStatus: String
}

@MainActor
public final class AccountService: ObservableObject {
    public static let shared = AccountService()

    @Published public private(set) var profile: AccountProfile?
    @Published public private(set) var isLoading = false
    @Published public var message: String?

    private let session: URLSession
    private var token: String?
    private let baseURL = URL(string: "https://bananarey.us/auth-api")!

    private init() {
        session = .permissiveShared
        token = KeychainTokenStore.load()
        if let data = UserDefaults.standard.data(forKey: Self.profileKey) {
            profile = try? JSONDecoder().decode(AccountProfile.self, from: data)
        }
    }

    public func register(email: String, password: String, displayName: String) async {
        guard password.count >= 8 else {
            message = "密码至少需要 8 位"
            return
        }
        await perform(
            path: "/api/collections/users/records",
            body: [
                "email": email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
                "password": password,
                "passwordConfirm": password,
                "name": displayName.trimmingCharacters(in: .whitespacesAndNewlines),
            ],
            successMessage: nil,
            preserveSession: true,
        )
        if profile != nil {
            await signIn(email: email, password: password)
            if token != nil {
                message = "注册成功，请检查邮箱完成验证"
            }
        }
    }

    public func signIn(email: String, password: String) async {
        await perform(
            path: "/api/collections/users/auth-with-password",
            body: ["identity": email, "password": password],
            successMessage: nil,
        )
    }

    public func requestVerification() async {
        guard let email = profile?.email else { return }
        await perform(
            path: "/api/collections/users/request-verification",
            body: ["email": email],
            successMessage: "验证邮件已发送",
            preserveSession: true,
        )
    }

    public func requestPasswordReset(email: String) async {
        await perform(
            path: "/api/collections/users/request-password-reset",
            body: ["email": email],
            successMessage: "如果邮箱已注册，重置邮件将很快送达",
            preserveSession: true,
        )
    }

    public func refresh() async {
        guard token != nil else { return }
        await perform(
            path: "/api/collections/users/auth-refresh",
            body: [:],
            successMessage: nil,
        )
    }

    public func signOut() {
        token = nil
        profile = nil
        message = "已退出登录"
        KeychainTokenStore.remove()
        UserDefaults.standard.removeObject(forKey: Self.profileKey)
    }

    private func perform(
        path: String,
        body: [String: String],
        successMessage: String?,
        preserveSession: Bool = false,
    ) async {
        guard let url = URL(string: baseURL.absoluteString + path) else {
            message = "账号服务地址无效"
            return
        }
        isLoading = true
        defer { isLoading = false }
        do {
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            if let token { request.setValue(token, forHTTPHeaderField: "Authorization") }
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else { throw AccountError.invalidResponse }
            let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
            guard (200..<300).contains(http.statusCode) else {
                throw AccountError.server(json["message"] as? String ?? "账号请求失败（HTTP \(http.statusCode)）")
            }
            if let record = json["record"] as? [String: Any] {
                profile = AccountProfile(
                    id: record["id"] as? String ?? "",
                    email: record["email"] as? String ?? "",
                    displayName: (record["name"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? (record["email"] as? String ?? ""),
                    isVerified: record["verified"] as? Bool ?? false,
                    subscriptionStatus: (record["subscriptionStatus"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "free",
                )
                if let profile, let saved = try? JSONEncoder().encode(profile) {
                    UserDefaults.standard.set(saved, forKey: Self.profileKey)
                }
            }
            if let responseToken = json["token"] as? String, !responseToken.isEmpty {
                token = responseToken
                KeychainTokenStore.save(responseToken)
            } else if !preserveSession && profile != nil {
                throw AccountError.invalidResponse
            }
            message = successMessage
        } catch is CancellationError {
            return
        } catch {
            message = (error as? AccountError)?.localizedDescription ?? "账号服务暂时不可用"
        }
    }

    private static let profileKey = "netgrey.account.profile"
}

private enum AccountError: LocalizedError {
    case invalidResponse
    case server(String)

    var errorDescription: String? {
        switch self {
        case .invalidResponse: return "账号服务返回了无效响应"
        case .server(let message): return message
        }
    }
}

private enum KeychainTokenStore {
    private static let service = "com.aurorashelf.app.account"
    private static let account = "refresh-token"

    @discardableResult
    static func save(_ token: String) -> Bool {
        let data = Data(token.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
        let insertQuery = query.merging([
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]) { _, new in new }
        let status = SecItemAdd(insertQuery as CFDictionary, nil)
        return status == errSecSuccess
    }

    static func load() -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func remove() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
    }
}
