import Foundation
import Security

@MainActor
final class PicacgAuthStore {
    static let shared = PicacgAuthStore()

    private let service = "com.aurorashelf.app.comic-auth"
    private let account = "picacg-token"

    private init() {}

    var token: String {
        get {
            let query: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: service,
                kSecAttrAccount as String: account,
                kSecReturnData as String: true,
                kSecMatchLimit as String: kSecMatchLimitOne
            ]
            var result: CFTypeRef?
            guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
                  let data = result as? Data,
                  let token = String(data: data, encoding: .utf8) else { return "" }
            return token
        }
        set {
            let query: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: service,
                kSecAttrAccount as String: account
            ]
            guard !newValue.isEmpty else {
                SecItemDelete(query as CFDictionary)
                return
            }
            let data = Data(newValue.utf8)
            let update: [String: Any] = [kSecValueData as String: data]
            let status = SecItemUpdate(query as CFDictionary, update as CFDictionary)
            if status == errSecItemNotFound {
                var insert = query
                insert[kSecValueData as String] = data
                insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
                SecItemAdd(insert as CFDictionary, nil)
            }
        }
    }
}
