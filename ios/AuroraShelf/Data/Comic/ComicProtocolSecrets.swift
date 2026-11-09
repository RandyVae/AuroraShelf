import Foundation
import CommonCrypto

enum ComicProtocolSecrets {
    private static let values: [String: String] = {
        if let url = Bundle.main.url(forResource: "SourceSecrets", withExtension: "plist"),
           let values = NSDictionary(contentsOf: url) as? [String: String], !values.isEmpty {
            return values
        }
        for bundle in Bundle.allBundles {
            if let url = bundle.url(forResource: "SourceSecrets", withExtension: "plist"),
               let values = NSDictionary(contentsOf: url) as? [String: String], !values.isEmpty {
                return values
            }
        }
        let env = ProcessInfo.processInfo.environment
        var envDict: [String: String] = [:]
        for k in ["JM_TOKEN_SECRET", "JM_TOKEN_SECRET_2", "JM_DATA_SECRET", "PICACG_API_KEY", "PICACG_SIGNATURE_SECRET"] {
            if let v = env[k], !v.isEmpty { envDict[k] = v }
        }
        if !envDict.isEmpty { return envDict }

        let fm = FileManager.default
        let cwd = fm.currentDirectoryPath
        let candidatePaths = [
            "ios/AuroraShelf/Resources/SourceSecrets.plist",
            "AuroraShelf/Resources/SourceSecrets.plist",
            "../ios/AuroraShelf/Resources/SourceSecrets.plist",
            "SourceSecrets.plist"
        ]
        for relPath in candidatePaths {
            let fullPath = (cwd as NSString).appendingPathComponent(relPath)
            if fm.fileExists(atPath: fullPath),
               let dict = NSDictionary(contentsOfFile: fullPath) as? [String: String],
               !dict.isEmpty {
                return dict
            }
        }
        let propPaths = ["source-secrets.properties", "../source-secrets.properties"]
        for relPath in propPaths {
            let fullPath = (cwd as NSString).appendingPathComponent(relPath)
            if let content = try? String(contentsOfFile: fullPath, encoding: .utf8) {
                var propDict: [String: String] = [:]
                for line in content.components(separatedBy: .newlines) {
                    let trimmed = line.trimmingCharacters(in: .whitespaces)
                    if !trimmed.isEmpty && !trimmed.hasPrefix("#") && trimmed.contains("=") {
                        let parts = trimmed.split(separator: "=", maxSplits: 1).map(String.init)
                        if parts.count == 2 {
                            propDict[parts[0].trimmingCharacters(in: .whitespaces)] = parts[1].trimmingCharacters(in: .whitespaces)
                        }
                    }
                }
                if !propDict.isEmpty { return propDict }
            }
        }
        return [:]
    }()

    static func value(_ key: String) -> String {
        values[key] ?? ""
    }

    static var hasJMConfiguration: Bool {
        ["JM_TOKEN_SECRET", "JM_TOKEN_SECRET_2", "JM_DATA_SECRET"].allSatisfy { !value($0).isEmpty }
    }

    static var hasPicacgConfiguration: Bool {
        ["PICACG_API_KEY", "PICACG_SIGNATURE_SECRET"].allSatisfy { !value($0).isEmpty }
    }

    static func md5Hex(_ value: String) -> String {
        let data = Data(value.utf8)
        var digest = [UInt8](repeating: 0, count: Int(CC_MD5_DIGEST_LENGTH))
        data.withUnsafeBytes { bytes in
            _ = CC_MD5(bytes.baseAddress, CC_LONG(data.count), &digest)
        }
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    static func hmacSHA256(_ value: String, secret: String) -> String {
        let valueData = Data(value.utf8)
        let secretData = Data(secret.utf8)
        var digest = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        valueData.withUnsafeBytes { valueBytes in
            secretData.withUnsafeBytes { secretBytes in
                CCHmac(
                    CCHmacAlgorithm(kCCHmacAlgSHA256),
                    secretBytes.baseAddress,
                    secretData.count,
                    valueBytes.baseAddress,
                    valueData.count,
                    &digest
                )
            }
        }
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    static func decryptJMPayload(_ encoded: String, timestamp: String) -> Data? {
        decryptJMPayload(encoded, timestamp: timestamp, secret: value("JM_DATA_SECRET"))
    }

    static func decryptJMPayload(_ encoded: String, timestamp: String, secret: String) -> Data? {
        guard !secret.isEmpty else { return nil }
        let key = Data(md5Hex(timestamp + secret).utf8)
        let clean = encoded.replacingOccurrences(of: "\n", with: "").replacingOccurrences(of: "\r", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard let encrypted = Data(base64Encoded: clean) else { return nil }
        var output = Data(count: encrypted.count + kCCBlockSizeAES128)
        let outputCapacity = output.count
        var moved = 0
        let status = output.withUnsafeMutableBytes { outputBytes in
            encrypted.withUnsafeBytes { encryptedBytes in
                key.withUnsafeBytes { keyBytes in
                    CCCrypt(
                        CCOperation(kCCDecrypt),
                        CCAlgorithm(kCCAlgorithmAES),
                        CCOptions(kCCOptionECBMode | kCCOptionPKCS7Padding),
                        keyBytes.baseAddress,
                        key.count,
                        nil,
                        encryptedBytes.baseAddress,
                        encrypted.count,
                        outputBytes.baseAddress,
                        outputCapacity,
                        &moved
                    )
                }
            }
        }
        guard status == kCCSuccess else { return nil }
        output.count = moved
        return output
    }
}
