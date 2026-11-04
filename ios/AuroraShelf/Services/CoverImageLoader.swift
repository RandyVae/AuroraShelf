import UIKit
import CommonCrypto

/// 全局封面安全加解密与高性能雙層緩存加載器（1:1 對齊 Android 端 CoverImageLoader）
public final class CoverImageLoader: @unchecked Sendable {
    public static let shared = CoverImageLoader()
    
    private let memoryCache = NSCache<NSString, UIImage>()
    private let fileManager = FileManager.default
    private let cacheDirectory: URL
    private let session: URLSession
    
    // 91動漫 / 黃果 AES-128-CBC 解密密鑰與 IV
    private let aesKey = "f5d965df75336270"
    private let aesIv = "97b60394abc2fbe1"
    
    private init() {
        let paths = fileManager.urls(for: .cachesDirectory, in: .userDomainMask)
        self.cacheDirectory = paths[0].appendingPathComponent("AuroraCovers", isDirectory: true)
        try? fileManager.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)
        
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.timeoutIntervalForResource = 30
        self.session = URLSession(configuration: config)
        
        // 限制內存緩存最大 120 張封面
        memoryCache.countLimit = 120
    }
    
    public func loadImage(from urlString: String) async -> UIImage? {
        let normalized = normalizeUrl(urlString)
        guard let url = URL(string: normalized), !normalized.isEmpty else { return nil }
        
        let cacheKey = cacheKey(for: normalized)
        
        // 1. 內存緩存命中
        if let cachedImage = memoryCache.object(forKey: cacheKey as NSString) {
            return cachedImage
        }
        
        // 2. 磁盤緩存命中
        let diskPath = cacheDirectory.appendingPathComponent(cacheKey)
        if let diskData = try? Data(contentsOf: diskPath), let diskImage = UIImage(data: diskData) {
            memoryCache.setObject(diskImage, forKey: cacheKey as NSString)
            return diskImage
        }
        
        // 3. 網絡拉取並解密
        do {
            var request = URLRequest(url: url)
            request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15", forHTTPHeaderField: "User-Agent")
            
            // 防盜鏈 Header 注入
            if normalized.contains("91dongman") || normalized.contains("drleao.cn") {
                request.setValue("https://91dongman.net/", forHTTPHeaderField: "Referer")
                request.setValue("https://91dongman.net", forHTTPHeaderField: "Origin")
            } else if normalized.contains("bzcdn.net") || normalized.contains("webmota") || normalized.contains("baozimh") {
                request.setValue("https://cn.webmota.com/", forHTTPHeaderField: "Referer")
            } else if normalized.contains("huavod") {
                request.setValue("https://huavod.com/", forHTTPHeaderField: "Referer")
            } else if normalized.contains("mmcdn.com") || normalized.contains("chaturbate") || normalized.contains("hornystudents") {
                request.setValue("https://chaturbate.com/", forHTTPHeaderField: "Referer")
            } else if normalized.contains("externulls.com") || normalized.contains("beeg.com") {
                request.setValue("https://beeg.com/", forHTTPHeaderField: "Referer")
            }
            
            let (data, response) = try await session.data(for: request)
            guard let httpResp = response as? HTTPURLResponse, (200...299).contains(httpResp.statusCode) else {
                return nil
            }
            
            var finalData = data
            var parsedImage: UIImage? = UIImage(data: data)
            
            // 檢查是否為 AES 加密數據（91動漫/黃果二進制加密封面）
            if parsedImage == nil && (normalized.contains("91dongman") || normalized.contains("drleao.cn") || normalized.contains("huangguo")) {
                if let decrypted = decryptAES128CBC(data: data, key: aesKey, iv: aesIv),
                   let decImg = UIImage(data: decrypted) {
                    parsedImage = decImg
                    finalData = decrypted
                }
            }
            
            guard let image = parsedImage else {
                return nil
            }
            
            // 寫入雙層緩存
            memoryCache.setObject(image, forKey: cacheKey as NSString)
            try? finalData.write(to: diskPath)
            
            return image
        } catch {
            return nil
        }
    }
    
    // MARK: - 輔助方法
    
    private func normalizeUrl(_ url: String) -> String {
        var clean = url.trimmingCharacters(in: .whitespacesAndNewlines)
        if clean.contains("THUMBNUM") {
            clean = clean.replacingOccurrences(of: "THUMBNUM", with: "1")
        }
        // 包子漫畫官方反代封面域名替換
        if clean.contains("baozimh.com/cover/") || clean.contains("webmota.com/cover/") || clean.contains("ttkan.co/cover/") {
            clean = clean.replacingOccurrences(of: "https://static-tw.baozimh.com/cover/", with: "https://s1.bzcdn.net/cover/")
            clean = clean.replacingOccurrences(of: "https://cn.webmota.com/cover/", with: "https://s1.bzcdn.net/cover/")
            clean = clean.replacingOccurrences(of: "https://static.ttkan.co/cover/", with: "https://s1.bzcdn.net/cover/")
            if let qIdx = clean.firstIndex(of: "?") {
                clean = String(clean[..<qIdx])
            }
        }
        return clean
    }
    
    private func cacheKey(for url: String) -> String {
        let hash = url.utf8.reduce(5381) { ($0 << 5) &+ $0 &+ Int($1) }
        return "cover_\(abs(hash)).img"
    }
    
    private func isEncryptedData(_ data: Data, urlString: String) -> Bool {
        // 判斷常見未加密圖片魔數
        if data.count >= 2 && data[0] == 0xFF && data[1] == 0xD8 { return false } // JPEG
        if data.count >= 8 && data.prefix(8) == Data([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]) { return false } // PNG
        if data.count >= 4 && data.prefix(4) == Data([0x47, 0x49, 0x46, 0x38]) { return false } // GIF
        if data.count >= 12 && data.prefix(4) == Data("RIFF".utf8) && data.subdata(in: 8..<12) == Data("WEBP".utf8) { return false } // WEBP
        
        return true
    }
    
    private func decryptAES128CBC(data: Data, key: String, iv: String) -> Data? {
        guard let keyData = key.data(using: .utf8),
              let ivData = iv.data(using: .utf8) else { return nil }
        
        let bufferSize = data.count + kCCBlockSizeAES128
        var decryptedData = Data(count: bufferSize)
        var numBytesDecrypted: size_t = 0
        
        let cryptStatus = keyData.withUnsafeBytes { keyBytes in
            ivData.withUnsafeBytes { ivBytes in
                data.withUnsafeBytes { dataBytes in
                    decryptedData.withUnsafeMutableBytes { decryptedBytes in
                        CCCrypt(
                            CCOperation(kCCDecrypt),
                            CCAlgorithm(kCCAlgorithmAES),
                            0, // 无 Padding 或手工 Padding
                            keyBytes.baseAddress, kCCKeySizeAES128,
                            ivBytes.baseAddress,
                            dataBytes.baseAddress, data.count,
                            decryptedBytes.baseAddress, bufferSize,
                            &numBytesDecrypted
                        )
                    }
                }
            }
        }
        
        guard cryptStatus == kCCSuccess else { return nil }
        decryptedData.count = numBytesDecrypted
        return decryptedData
    }
}
