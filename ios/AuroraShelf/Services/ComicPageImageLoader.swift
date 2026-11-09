import Foundation
import UIKit
import CommonCrypto

actor ComicPageImageLoader {
    static let shared = ComicPageImageLoader()

    private let session: URLSession
    private let cache = NSCache<NSString, UIImage>()
    private var inFlightTasks: [String: Task<UIImage, Error>] = [:]

    private init() {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 20
        configuration.timeoutIntervalForResource = 45
        session = URLSession(configuration: configuration)
        cache.countLimit = 32
        cache.totalCostLimit = 80 * 1024 * 1024 // 80 MB 内存上限
        
        let memCache = self.cache
        NotificationCenter.default.addObserver(
            forName: UIApplication.didReceiveMemoryWarningNotification,
            object: nil,
            queue: .main
        ) { _ in
            memCache.removeAllObjects()
        }
    }

    func clearCache() {
        cache.removeAllObjects()
    }

    func load(_ page: ComicPage) async throws -> UIImage {
        if let image = cache.object(forKey: page.id as NSString) { return image }
        if let ongoing = inFlightTasks[page.id] {
            return try await ongoing.value
        }

        let task = Task<UIImage, Error> {
            try await self.performLoad(page)
        }

        inFlightTasks[page.id] = task
        defer { inFlightTasks[page.id] = nil }

        let result = try await task.value
        let cost = result.cgImage.map { $0.bytesPerRow * $0.height } ?? 1024
        cache.setObject(result, forKey: page.id as NSString, cost: cost)
        return result
    }

    private func performLoad(_ page: ComicPage) async throws -> UIImage {
        let imageURL: URL
        let imageReferer: String
        if let resolutionUrl = page.resolutionUrl {
            guard let pageURL = URL(string: resolutionUrl) else { throw ComicImageError.invalidURL }
            let response = try await request(url: pageURL, headers: page.headers, referer: page.referer)
            guard let html = String(data: response.data, encoding: .utf8),
                  let resolvedURL = ComicSourceAdapter.parseEHentaiOriginalImageURL(html, pageURL: pageURL) else {
                throw ComicImageError.resolveFailed
            }
            imageURL = resolvedURL
            imageReferer = pageURL.absoluteString
        } else {
            guard let url = URL(string: page.imageUrl) else { throw ComicImageError.invalidURL }
            imageURL = url
            imageReferer = page.referer ?? ""
        }

        let data: Data
        do {
            let (fetchedData, _) = try await request(url: imageURL, headers: page.headers, referer: imageReferer)
            data = fetchedData
        } catch {
            // 1. Baozi CDN 容灾自动回退：当 bzcdn 节点遭 RST 阻断或返回 404/503 时，自动回退到官方直连节点 static-tw.baozimh.com
            if let host = imageURL.host?.lowercased(), host.hasSuffix("bzcdn.net"),
               var fallbackComponents = URLComponents(url: imageURL, resolvingAgainstBaseURL: true) {
                fallbackComponents.host = "static-tw.baozimh.com"
                if let fallbackURL = fallbackComponents.url {
                    let (fallbackData, _) = try await request(url: fallbackURL, headers: page.headers, referer: "https://cn.twbzmg.com/")
                    data = fallbackData
                } else {
                    throw error
                }
            } else if let host = imageURL.host?.lowercased(), host.contains("jmapiproxy") || host.contains("jmapinode"),
                      var fallbackComponents = URLComponents(url: imageURL, resolvingAgainstBaseURL: true) {
                // 2. JM 禁漫 CDN 容灾自动回退：轮询候选镜像节点
                let backupHosts = ["cdn-msp.jmapiproxy2.cc", "cdn-msp2.jmapiproxy2.cc", "cdn-msp3.jmapiproxy2.cc", "cdn-msp.jmapinodeudzn.net"]
                var fallbackSuccess = false
                var fallbackData: Data?
                for backupHost in backupHosts where backupHost != host {
                    fallbackComponents.host = backupHost
                    if let backupURL = fallbackComponents.url,
                       let (d, _) = try? await request(url: backupURL, headers: page.headers, referer: imageReferer) {
                        fallbackData = d
                        fallbackSuccess = true
                        break
                    }
                }
                if fallbackSuccess, let fallbackData {
                    data = fallbackData
                } else {
                    throw error
                }
            } else {
                throw error
            }
        }
        let image = UIImage(data: data) ?? decryptEncryptedPage(data, url: imageURL)
        guard let image else { throw ComicImageError.decodeFailed }
        return page.verticalSegments > 1 ? unscramble(image, segments: page.verticalSegments) : image
    }

    private func request(url: URL, headers: [String: String], referer: String?) async throws -> (data: Data, response: HTTPURLResponse) {
        var request = URLRequest(url: url)
        request.setValue(ComicSourceAdapter.userAgent, forHTTPHeaderField: "User-Agent")
        headers.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
        if let referer, !referer.isEmpty { request.setValue(referer, forHTTPHeaderField: "Referer") }
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw ComicImageError.invalidResponse }
        guard (200...299).contains(http.statusCode) else { throw ComicImageError.httpStatus(http.statusCode) }
        return (data, http)
    }

    private func unscramble(_ image: UIImage, segments: Int) -> UIImage {
        guard let source = image.cgImage, segments > 1 else { return image }
        let width = source.width
        let height = source.height
        let segmentHeight = height / segments
        let remainder = height % segments
        return autoreleasepool {
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1.0
            let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format)
            return renderer.image { _ in
                for index in 0..<segments {
                    let sourceTop = height - segmentHeight * (index + 1) - remainder
                    let pieceHeight = segmentHeight + (index == 0 ? remainder : 0)
                    let destinationTop = segmentHeight * index + (index == 0 ? 0 : remainder)
                    let sourceRect = CGRect(x: 0, y: sourceTop, width: width, height: pieceHeight)
                    let destinationRect = CGRect(x: 0, y: destinationTop, width: width, height: pieceHeight)
                    if let crop = source.cropping(to: sourceRect) {
                        UIImage(cgImage: crop).draw(in: destinationRect)
                    }
                }
            }
        }
    }

    private func decryptEncryptedPage(_ data: Data, url: URL) -> UIImage? {
        let host = url.host?.lowercased() ?? ""
        guard host.contains("91dongman") || host == "drleao.cn" || host.hasSuffix(".drleao.cn") else { return nil }
        let key = Data("f5d965df75336270".utf8)
        let iv = Data("97b60394abc2fbe1".utf8)
        let options = [CCOptions(0), CCOptions(kCCOptionPKCS7Padding)]
        for option in options {
            let capacity = data.count + kCCBlockSizeAES128
            var output = Data(count: capacity)
            var written = 0
            let status = output.withUnsafeMutableBytes { outputBytes in
                data.withUnsafeBytes { inputBytes in
                    key.withUnsafeBytes { keyBytes in
                        iv.withUnsafeBytes { ivBytes in
                            CCCrypt(CCOperation(kCCDecrypt), CCAlgorithm(kCCAlgorithmAES), option,
                                    keyBytes.baseAddress, kCCKeySizeAES128, ivBytes.baseAddress,
                                    inputBytes.baseAddress, data.count, outputBytes.baseAddress,
                                    capacity, &written)
                        }
                    }
                }
            }
            guard status == kCCSuccess else { continue }
            output.count = written
            if let image = UIImage(data: output) { return image }
        }
        return nil
    }
}

private enum ComicImageError: LocalizedError {
    case invalidURL
    case invalidResponse
    case httpStatus(Int)
    case decodeFailed
    case resolveFailed

    var errorDescription: String? {
        switch self {
        case .invalidURL: return "漫画页地址无效。"
        case .invalidResponse: return "漫画图片服务没有返回有效响应。"
        case .httpStatus(let status): return "漫画图片请求失败（HTTP \(status)）。"
        case .decodeFailed: return "漫画图片格式无法解码。"
        case .resolveFailed: return "未能解析此画廊页面的原图地址。"
        }
    }
}
