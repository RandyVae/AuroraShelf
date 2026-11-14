import UIKit
import CommonCrypto
import CryptoKit

/// 全局封面安全加解密与高性能雙層緩存加載器（1:1 對齊 Android 端 CoverImageLoader）
public actor CoverImageLoader {
    public static let shared = CoverImageLoader()

    private let memoryCache = NSCache<NSString, UIImage>()
    private let fileManager = FileManager.default
    private let cacheDirectory: URL
    private let session: URLSession
    private var inFlightTasks: [String: (id: UUID, task: Task<UIImage?, Never>)] = [:]

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
        config.urlCache = URLCache(memoryCapacity: 64 * 1024 * 1024, diskCapacity: 256 * 1024 * 1024)
        config.requestCachePolicy = .returnCacheDataElseLoad
        self.session = URLSession.permissive(configuration: config)

        // 限制內存緩存最大 120 張封面
        memoryCache.countLimit = 120
        memoryCache.totalCostLimit = 64 * 1024 * 1024
    }

    /// 静默预热图片到磁盘与内存缓存
    nonisolated public func preload(url: String, baseURL: URL? = nil) {
        Task {
            _ = await loadImage(from: url, baseURL: baseURL)
        }
    }

    public func loadImage(from urlString: String, title: String? = nil, baseURL: URL? = nil, requestReferer: String? = nil) async -> UIImage? {
        let rawNormalized = Self.normalizeUrl(urlString, baseURL: baseURL)
        let normalized = DoubanPosterResolver.upgradePosterURL(rawNormalized) ?? rawNormalized
        let isPlaceholderDouban = normalized.lowercased().contains("tv_default_small")
            || normalized.lowercased().contains("movie_default_small")
            || normalized.lowercased().contains("/cuphead/movie-static/pics/")
        guard !isPlaceholderDouban, let url = URL(string: normalized), !normalized.isEmpty else {
            if let title, !title.isEmpty {
                if let fallbackUrl = await DoubanPosterResolver.shared.resolvePosterURL(for: title) {
                    return await loadImage(from: fallbackUrl, title: nil, baseURL: nil, requestReferer: nil)
                }
            }
            return nil
        }

        // 对已确认为无效/死链的 CDN 域名（如 dbzy.tv / dbzy5.com）优先调用豆瓣电影海报引擎重定向
        if let host = url.host?.lowercased(), host == "dbzy.tv" || host == "dbzy5.com" {
            if let title, !title.isEmpty {
                if let fallbackUrl = await DoubanPosterResolver.shared.resolvePosterURL(for: title) {
                    return await loadImage(from: fallbackUrl, title: nil, baseURL: nil, requestReferer: nil)
                }
            }
            return nil
        }

        let cacheKey = Self.cacheKey(for: normalized)

        // 1. 內存緩存命中
        if let cachedImage = memoryCache.object(forKey: cacheKey as NSString) {
            return cachedImage
        }

        // 2. 磁盤緩存命中
        let diskPath = cacheDirectory.appendingPathComponent(cacheKey)
        if let diskData = try? Data(contentsOf: diskPath), let diskImage = Self.decodeForDisplay(diskData) {
            memoryCache.setObject(diskImage, forKey: cacheKey as NSString)
            return diskImage
        }

        // 3. 正在進行的相同下載任務（防擊穿防重入）
        if let ongoing = inFlightTasks[cacheKey] {
            return await ongoing.task.value
        }

        // 4. 網絡拉取並解密（去重任務）
        let taskToken = UUID()
        let session = self.session
        let aesKey = self.aesKey
        let aesIv = self.aesIv
        let task = Task<UIImage?, Never> {
            do {
                let initialReferer = requestReferer ?? baseURL?.absoluteString
                let request = Self.imageRequest(for: url, referer: initialReferer)
                var rawData: Data?

                if let (data, response) = try? await session.data(for: request),
                   let httpResp = response as? HTTPURLResponse,
                   (200...299).contains(httpResp.statusCode),
                   !data.isEmpty {
                    rawData = data
                } else {
                    // 防盗链与 CDN 容灾重试：尝试无 Referer 纯净拉取，或 MissAV 旧域名 fourhoi.com 切换至 fourhoi.mrstcdn.store
                    let noRefererRequest = Self.imageRequest(for: url, referer: nil, forceNoReferer: true)
                    if let (data, response) = try? await session.data(for: noRefererRequest),
                       let httpResp = response as? HTTPURLResponse,
                       (200...299).contains(httpResp.statusCode),
                       !data.isEmpty {
                        rawData = data
                    } else if let fallbackMissAvURL = Self.missAvFallbackURL(for: url) {
                        let fallbackRequest = Self.imageRequest(for: fallbackMissAvURL, referer: "https://missav.media/")
                        if let (data, response) = try? await session.data(for: fallbackRequest),
                           let httpResp = response as? HTTPURLResponse,
                           (200...299).contains(httpResp.statusCode),
                           !data.isEmpty {
                            rawData = data
                        }
                    }
                }

                guard let data = rawData, !data.isEmpty else {
                    return nil
                }

                var finalData = data
                var parsedImage: UIImage? = Self.decodeForDisplay(data)

                // 檢查是否為 AES 加密數據（91動漫/黃果二進制加密封面）
                if parsedImage == nil && (Self.requiresAESDecryption(url: url) || Self.isPossibleEncryptedImage(data)) {
                    if let decrypted = Self.decryptAES128CBC(data: data, key: aesKey, iv: aesIv),
                       let decImg = Self.decodeForDisplay(decrypted) {
                        parsedImage = decImg
                        finalData = decrypted
                    }
                }

                guard let image = parsedImage else {
                    return nil
                }

                try? finalData.write(to: diskPath)
                return image
            } catch {
                return nil
            }
        }

        inFlightTasks[cacheKey] = (id: taskToken, task: task)
        defer {
            if inFlightTasks[cacheKey]?.id == taskToken {
                inFlightTasks[cacheKey] = nil
            }
        }

        if let image = await task.value {
            memoryCache.setObject(image, forKey: cacheKey as NSString, cost: Self.imageCost(image))
            return image
        } else if let title, !title.isEmpty {
            if let fallbackUrl = await DoubanPosterResolver.shared.resolvePosterURL(for: title), fallbackUrl != normalized {
                if let fallbackImage = await loadImage(from: fallbackUrl, title: nil, baseURL: nil, requestReferer: nil) {
                    memoryCache.setObject(fallbackImage, forKey: cacheKey as NSString, cost: Self.imageCost(fallbackImage))
                    return fallbackImage
                }
            }
        }
        return nil
    }

    // MARK: - 輔助方法

    static func normalizeUrl(_ url: String, baseURL: URL? = nil) -> String {
        var clean = url.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return "" }
        // HTML feeds commonly escape URLs as JSON-style strings and some older
        // sources include literal spaces in relative image paths. Normalize those
        // transport artifacts before URL resolution so the image request reaches
        // the actual asset rather than failing URL(string:).
        clean = clean
            .replacingOccurrences(of: "\\/", with: "/")
            .replacingOccurrences(of: "\\u002F", with: "/", options: .caseInsensitive)
            .replacingOccurrences(of: "\\u0026", with: "&", options: .caseInsensitive)
            .replacingOccurrences(of: "&amp;", with: "&")
        if clean.contains(" ") {
            clean = clean.replacingOccurrences(of: " ", with: "%20")
        }
        if clean.hasPrefix("//") { clean = "https:\(clean)" }
        if let schemeRange = clean.range(of: "://") {
            let schemePrefix = String(clean[..<schemeRange.upperBound])
            var rest = String(clean[schemeRange.upperBound...])
            while rest.contains("//") {
                rest = rest.replacingOccurrences(of: "//", with: "/")
            }
            clean = schemePrefix + rest
        }
        if URL(string: clean)?.scheme == nil, let baseURL,
           let resolved = URL(string: clean, relativeTo: baseURL)?.absoluteURL {
            clean = resolved.absoluteString
        }
        if clean.contains("THUMBNUM") {
            clean = clean.replacingOccurrences(of: "THUMBNUM", with: "1")
        }
        if var components = URLComponents(string: clean),
           let host = components.host?.lowercased(),
           (host == "baozimh.com" || host.hasSuffix(".baozimh.com") || host == "webmota.com" || host.hasSuffix(".webmota.com") || host == "ttkan.co" || host.hasSuffix(".ttkan.co")),
           components.path.contains("/cover/") {
            components.host = "s1.bzcdn.net"
            components.query = nil
            clean = components.string ?? clean
        }
        return clean
    }

    static func missAvFallbackURL(for url: URL) -> URL? {
        guard var components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let host = components.host?.lowercased(),
              host == "fourhoi.com" || host.hasSuffix(".fourhoi.com") else {
            return nil
        }
        components.host = "fourhoi.mrstcdn.store"
        return components.url
    }

    static func cacheKey(for url: String) -> String {
        let digest = SHA256.hash(data: Data(url.utf8))
        return "cover_\(digest.map { String(format: "%02x", $0) }.joined()).img"
    }

    private static func decodeForDisplay(_ data: Data, maxPixelSize: Int = 2200) -> UIImage? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixelSize,
            kCGImageSourceShouldCacheImmediately: true,
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
        return UIImage(cgImage: image)
    }

    private static func imageCost(_ image: UIImage) -> Int {
        guard let cgImage = image.cgImage else { return 0 }
        return cgImage.bytesPerRow * cgImage.height
    }

    static func imageRequest(for url: URL, referer suppliedReferer: String? = nil, forceNoReferer: Bool = false) -> URLRequest {
        var request = URLRequest(url: url)
        request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")
        request.setValue("image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8", forHTTPHeaderField: "Accept")
        if forceNoReferer {
            return request
        }
        let host = url.host?.lowercased() ?? ""
        let defaultReferer: String?
        if host == "91dongman.net" || host == "drleao.cn" || host.hasSuffix(".drleao.cn") || host.hasSuffix(".91dongman.net") {
            defaultReferer = "https://91dongman.net/"
        } else if ["bzcdn.net", "webmota.com", "baozimh.com"].contains(where: { host == $0 || host.hasSuffix(".\($0)") }) {
            defaultReferer = "https://cn.webmota.com/"
        } else if ["externulls.com", "beeg.com"].contains(where: { host == $0 || host.hasSuffix(".\($0)") }) {
            defaultReferer = "https://beeg.com/"
        } else if host == "eporner.com" || host.hasSuffix(".eporner.com") {
            defaultReferer = "https://www.eporner.com/"
        } else if host == "komiic.com" || host.hasSuffix(".komiic.com") {
            defaultReferer = "https://komiic.com/"
        } else if host == "e-hentai.org" || host.hasSuffix(".e-hentai.org") {
            defaultReferer = "https://e-hentai.org/"
        } else if host == "huavod.com" || host.hasSuffix(".huavod.com") {
            defaultReferer = "https://huavod.com/"
        } else if host.contains("doubanio.com") || host.contains("douban.com") {
            defaultReferer = "https://movie.douban.com/"
        } else if ["chaturbate.com", "hornystudents.com", "mmcdn.com"].contains(where: { host == $0 || host.hasSuffix(".\($0)") }) {
            defaultReferer = "https://chaturbate.com/"
        } else if host == "ohentai.org" || host.hasSuffix(".ohentai.org") {
            defaultReferer = "https://ohentai.org/"
        } else if host == "pornhub.com" || host.hasSuffix(".pornhub.com") || host.hasSuffix(".phncdn.com") {
            defaultReferer = "https://jp.pornhub.com/"
        } else if ["fourhoi.com", "mrstcdn.store", "missav.media", "missav.ws", "missav.ai", "missav123.com"].contains(where: { host == $0 || host.hasSuffix(".\($0)") }) {
            defaultReferer = "https://missav.media/"
        } else if host == "jav.sb" || host.hasSuffix(".jav.sb") {
            defaultReferer = "https://jav.sb/"
        } else if host == "javnoni.com" || host.hasSuffix(".javnoni.com") {
            defaultReferer = "https://javnoni.com/"
        } else if ["sol140.com", "sol148.com", "23img.com", "cdn77.org"].contains(where: { host == $0 || host.hasSuffix(".\($0)") }) || host.contains("sol") {
            defaultReferer = "https://h1014.sol148.com/"
        } else if host.contains("huarenlivewebsite")
            || host.contains("hitv.com")
            || host.contains("okokserver")
            || host.contains("weserv.nl")
            || host.contains("qpic.cn")
            || host.contains("iqiyipic.com")
            || host.contains("alicdn.com")
            || host.contains("hhmage.com") {
            defaultReferer = "https://huavod.com/"
        } else if host == "t66y.com" || host.hasSuffix(".t66y.com") {
            defaultReferer = "https://t66y.com/"
        } else if host.contains("jmapiproxy") || host.contains("jmapinode") {
            defaultReferer = "https://www.cdnhjk.net/"
        } else if host.contains("xvideos") {
            defaultReferer = "https://www.xvideos.com/"
        } else {
            defaultReferer = nil
        }
        if let referer = suppliedReferer ?? defaultReferer,
           let refererURL = URL(string: referer),
           ["https", "http"].contains(refererURL.scheme?.lowercased() ?? ""), refererURL.host != nil {
            request.setValue(refererURL.absoluteString, forHTTPHeaderField: "Referer")
        }
        return request
    }

    public func clearDiskAndMemoryCache() {
        memoryCache.removeAllObjects()
        URLCache.shared.removeAllCachedResponses()
        session.configuration.urlCache?.removeAllCachedResponses()
        if let files = try? fileManager.contentsOfDirectory(at: cacheDirectory, includingPropertiesForKeys: nil) {
            for file in files {
                try? fileManager.removeItem(at: file)
            }
        }
    }

    private static func requiresAESDecryption(url: URL) -> Bool {
        let host = url.host?.lowercased() ?? ""
        return host.contains("91dongman") ||
            host.hasSuffix(".drleao.cn") || host == "drleao.cn" ||
            host.contains("huangguo") ||
            host.contains("cuinhri.cn") ||
            host.contains("zdmhyg.cn") ||
            host.contains("fisawck.cn")
    }

    private static func isPossibleEncryptedImage(_ data: Data) -> Bool {
        guard data.count >= 16 else { return false }
        if data.count >= 2 && data[0] == 0xFF && data[1] == 0xD8 { return false } // JPEG
        if data.count >= 8 && data.prefix(8) == Data([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]) { return false } // PNG
        if data.count >= 4 && data.prefix(4) == Data([0x47, 0x49, 0x46, 0x38]) { return false } // GIF
        if data.count >= 12 && data.prefix(4) == Data("RIFF".utf8) && data.subdata(in: 8..<12) == Data("WEBP".utf8) { return false } // WEBP
        return data.count % 16 == 0
    }

    private static func decryptAES128CBC(data: Data, key: String, iv: String) -> Data? {
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
