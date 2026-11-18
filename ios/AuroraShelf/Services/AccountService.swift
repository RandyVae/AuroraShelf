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

    public var hasToken: Bool {
        guard let token else { return false }
        return !token.isEmpty
    }

    private init() {
        session = .shared
        token = KeychainTokenStore.load()
        if let data = UserDefaults.standard.data(forKey: Self.profileKey) {
            profile = try? JSONDecoder().decode(AccountProfile.self, from: data)
        }
    }

    static func normalizedEmail(_ raw: String) -> String {
        raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    static func isValidEmailFormat(_ normalized: String) -> Bool {
        guard !normalized.isEmpty else { return false }
        let parts = normalized.split(separator: "@", omittingEmptySubsequences: false)
        guard parts.count == 2, !parts[0].isEmpty, parts[1].contains(".") else { return false }
        return !normalized.contains(" ")
    }

    static func extractErrorMessage(from json: [String: Any], statusCode: Int) -> String {
        if let dataDict = json["data"] as? [String: Any], !dataDict.isEmpty {
            var details: [String] = []
            for key in dataDict.keys.sorted() {
                if let fieldObj = dataDict[key] as? [String: Any],
                   let msg = fieldObj["message"] as? String,
                   !msg.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    let fieldLabel: String
                    switch key {
                    case "email", "identity": fieldLabel = "邮箱"
                    case "password": fieldLabel = "密码"
                    case "passwordConfirm": fieldLabel = "确认密码"
                    case "name": fieldLabel = "昵称"
                    default: fieldLabel = key
                    }
                    details.append("\(fieldLabel)：\(msg)")
                }
            }
            if !details.isEmpty {
                return details.joined(separator: "；")
            }
        }
        if let topMessage = json["message"] as? String,
           !topMessage.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return topMessage
        }
        return "账号请求失败（HTTP \(statusCode)）"
    }

    public func register(email: String, password: String, displayName: String) async {
        let cleanEmail = Self.normalizedEmail(email)
        guard Self.isValidEmailFormat(cleanEmail) else {
            message = "请输入格式正确的邮箱地址"
            return
        }
        guard password.count >= 8 else {
            message = "密码至少需要 8 位"
            return
        }
        let cleanName = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        let created = await perform(
            path: "/api/collections/users/records",
            body: [
                "email": cleanEmail,
                "password": password,
                "passwordConfirm": password,
                "name": cleanName,
            ],
            successMessage: nil,
            requiresAuth: false,
            preserveSession: true
        )
        if created {
            await signIn(email: cleanEmail, password: password)
            if token != nil {
                message = "注册成功，请检查邮箱完成验证"
            }
        }
    }

    public func signIn(email: String, password: String) async {
        let cleanEmail = Self.normalizedEmail(email)
        guard Self.isValidEmailFormat(cleanEmail) else {
            message = "请输入格式正确的邮箱地址"
            return
        }
        guard !password.isEmpty else {
            message = "请输入登录密码"
            return
        }
        _ = await perform(
            path: "/api/collections/users/auth-with-password",
            body: ["identity": cleanEmail, "password": password],
            successMessage: "登录成功",
            requiresAuth: false
        )
    }

    public func requestVerification() async {
        guard let rawEmail = profile?.email else {
            message = "当前没有可验证的账号邮箱"
            return
        }
        let cleanEmail = Self.normalizedEmail(rawEmail)
        guard Self.isValidEmailFormat(cleanEmail) else {
            message = "当前账号邮箱格式无效"
            return
        }
        _ = await perform(
            path: "/api/collections/users/request-verification",
            body: ["email": cleanEmail],
            successMessage: "验证邮件已发送",
            requiresAuth: false,
            preserveSession: true
        )
    }

    public func requestPasswordReset(email: String) async {
        let cleanEmail = Self.normalizedEmail(email)
        guard Self.isValidEmailFormat(cleanEmail) else {
            message = "请输入注册时使用的有效邮箱地址"
            return
        }
        _ = await perform(
            path: "/api/collections/users/request-password-reset",
            body: ["email": cleanEmail],
            successMessage: "如果邮箱已注册，重置邮件将很快送达",
            requiresAuth: false,
            preserveSession: true
        )
    }

    public func refresh() async {
        guard hasToken else { return }
        _ = await perform(
            path: "/api/collections/users/auth-refresh",
            body: [:],
            successMessage: nil,
            requiresAuth: true
        )
    }

    public func signOut() {
        token = nil
        profile = nil
        message = "已退出登录"
        KeychainTokenStore.remove()
        UserDefaults.standard.removeObject(forKey: Self.profileKey)
    }

    @discardableResult
    private func perform(
        path: String,
        body: [String: String],
        successMessage: String?,
        requiresAuth: Bool = false,
        preserveSession: Bool = false
    ) async -> Bool {
        guard let url = URL(string: baseURL.absoluteString + path) else {
            message = "账号服务地址无效"
            return false
        }
        isLoading = true
        defer { isLoading = false }
        do {
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            if requiresAuth, let token, !token.isEmpty {
                let bearer = token.lowercased().hasPrefix("bearer ") ? token : "Bearer \(token)"
                request.setValue(bearer, forHTTPHeaderField: "Authorization")
            }
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else { throw AccountError.invalidResponse }
            let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
            guard (200..<300).contains(http.statusCode) else {
                if !preserveSession && (http.statusCode == 401 || http.statusCode == 403) {
                    token = nil
                    profile = nil
                    KeychainTokenStore.remove()
                    UserDefaults.standard.removeObject(forKey: Self.profileKey)
                }
                throw AccountError.server(Self.extractErrorMessage(from: json, statusCode: http.statusCode))
            }

            var nextProfile: AccountProfile?
            if let record = json["record"] as? [String: Any] {
                nextProfile = AccountProfile(
                    id: record["id"] as? String ?? "",
                    email: record["email"] as? String ?? "",
                    displayName: (record["name"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? (record["email"] as? String ?? ""),
                    isVerified: record["verified"] as? Bool ?? false,
                    subscriptionStatus: (record["subscriptionStatus"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "free"
                )
            }

            let responseToken = json["token"] as? String
            if !preserveSession && nextProfile != nil && (responseToken == nil || responseToken?.isEmpty == true) {
                throw AccountError.invalidResponse
            }

            if let responseToken, !responseToken.isEmpty {
                guard KeychainTokenStore.save(responseToken) else {
                    throw AccountError.server("未能安全保存登录会话")
                }
                token = responseToken
            }
            if let nextProfile {
                profile = nextProfile
                if let saved = try? JSONEncoder().encode(nextProfile) {
                    UserDefaults.standard.set(saved, forKey: Self.profileKey)
                }
            }
            if let successMessage {
                message = successMessage
            } else {
                message = nil
            }
            return true
        } catch is CancellationError {
            return false
        } catch {
            message = (error as? AccountError)?.localizedDescription ?? "账号服务暂时不可用"
            return false
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
