import CommonCrypto
import Foundation
import ImageIO
import UIKit

actor ComicPageImageLoader {
    static let shared = ComicPageImageLoader()

    /// 漫画单页最大目标像素宽度阈值：仅当图片像素宽度 > 1800 时按宽度比例等比降采样，
    /// 彻底修复 800×12000 Webtoon 条漫因高度超限被误压至 160px 宽导致的糊图问题。
    nonisolated static let maxTargetPixelWidth: Int = 1800
    nonisolated static let maxGPUTextureDimension: Int = 16384

    private struct InFlightEntry {
        let id: UUID
        var waiterIds: Set<UUID>
        let task: Task<UIImage, Error>
    }

    private let session: URLSession
    private let cache = NSCache<NSString, UIImage>()
    private var inFlightTasks: [String: InFlightEntry] = [:]

    private init() {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 15
        configuration.timeoutIntervalForResource = 25
        session = URLSession.permissive(configuration: configuration)
        cache.countLimit = 32
        cache.totalCostLimit = 80 * 1024 * 1024 // 80 MB 内存上限

        NotificationCenter.default.addObserver(
            forName: UIApplication.didReceiveMemoryWarningNotification,
            object: nil,
            queue: .main
        ) { _ in
            Task {
                await ComicPageImageLoader.shared.clearCache()
            }
        }
    }

    func clearCache() {
        cache.removeAllObjects()
    }

    func load(_ page: ComicPage) async throws -> UIImage {
        try Task.checkCancellation()
        if let image = cache.object(forKey: page.id as NSString) { return image }
        let waiterId = UUID()
        let taskToken: UUID
        let task: Task<UIImage, Error>
        if var ongoing = inFlightTasks[page.id], !ongoing.task.isCancelled {
            ongoing.waiterIds.insert(waiterId)
            inFlightTasks[page.id] = ongoing
            taskToken = ongoing.id
            task = ongoing.task
        } else {
            let newToken = UUID()
            let created = Task<UIImage, Error> {
                try await self.performLoad(page)
            }
            inFlightTasks[page.id] = InFlightEntry(id: newToken, waiterIds: [waiterId], task: created)
            taskToken = newToken
            task = created
        }

        defer {
            releaseWaiter(pageId: page.id, taskToken: taskToken, waiterId: waiterId, cancelIfLast: false)
        }

        let result = try await withTaskCancellationHandler {
            try await task.value
        } onCancel: {
            Task {
                await self.releaseWaiter(pageId: page.id, taskToken: taskToken, waiterId: waiterId, cancelIfLast: true)
            }
        }
        let cost = result.cgImage.map { $0.bytesPerRow * $0.height } ?? 1024
        cache.setObject(result, forKey: page.id as NSString, cost: cost)
        return result
    }

    private func releaseWaiter(pageId: String, taskToken: UUID, waiterId: UUID, cancelIfLast: Bool) {
        guard var entry = inFlightTasks[pageId], entry.id == taskToken else { return }
        entry.waiterIds.remove(waiterId)
        if entry.waiterIds.isEmpty {
            if cancelIfLast {
                entry.task.cancel()
            }
            inFlightTasks[pageId] = nil
        } else {
            inFlightTasks[pageId] = entry
        }
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
        let host = imageURL.host?.lowercased() ?? ""
        let cdnPoolKey: String? = (host.hasSuffix("bzcdn.net") || host.hasSuffix("baozicdn.com") || host.hasSuffix("baozimh.com"))
            ? "baozi-cdn"
            : ((host.contains("jmapiproxy") || host.contains("jmapinode")) ? "jm-cdn" : nil)

        var targetURL = imageURL
        var targetHost = host
        var targetReferer = imageReferer
        if let cdnPoolKey, SourceHealthRegistry.shared.isCircuitOpen(urlOrHost: host),
           var comps = URLComponents(url: imageURL, resolvingAgainstBaseURL: true),
           let bestHost = SourceHealthRegistry.shared.resolveOrigins(sourceKey: cdnPoolKey)
            .compactMap({ URL(string: $0)?.host?.lowercased() })
            .first(where: { $0 != host }) {
            comps.host = bestHost
            if let rerouted = comps.url {
                targetURL = rerouted
                targetHost = bestHost
                if cdnPoolKey == "baozi-cdn" && bestHost.contains("baozimh") {
                    targetReferer = "https://cn.twbzmg.com/"
                }
            }
        }

        let startedAt = Date()
        do {
            let (fetchedData, _) = try await request(url: targetURL, headers: page.headers, referer: targetReferer)
            if let cdnPoolKey {
                let elapsed = Int(Date().timeIntervalSince(startedAt) * 1000)
                SourceHealthRegistry.shared.recordSuccess(sourceKey: cdnPoolKey, urlOrHost: targetHost, latencyMs: elapsed)
            }
            data = fetchedData
        } catch {
            if let cdnPoolKey {
                SourceHealthRegistry.shared.recordFailure(sourceKey: cdnPoolKey, urlOrHost: targetHost, isHardBlock: true)
            }
            if let cdnPoolKey,
               var fallbackComponents = URLComponents(url: imageURL, resolvingAgainstBaseURL: true) {
                let defaults = cdnPoolKey == "baozi-cdn"
                    ? ["s1.baozicdn.com", "s2.baozicdn.com", "static-tw.baozimh.com", "s1.bzcdn.net", "s2.bzcdn.net"]
                    : ["cdn-msp.jmapiproxy2.cc", "cdn-msp2.jmapiproxy2.cc", "cdn-msp3.jmapiproxy2.cc", "cdn-msp.jmapinodeudzn.net", "cdn-msp.jmapiproxy1.cc"]
                let rankedHosts = SourceHealthRegistry.shared.rankCandidates(sourceKey: cdnPoolKey, candidates: defaults)
                    .filter { $0 != targetHost && $0 != host }
                var fallbackData: Data?
                for backupHost in rankedHosts {
                    fallbackComponents.host = backupHost
                    let backupReferer = (cdnPoolKey == "baozi-cdn")
                        ? (backupHost.contains("baozimh") ? "https://cn.twbzmg.com/" : "https://cn.webmota.com/")
                        : imageReferer
                    let hopStart = Date()
                    if let fallbackURL = fallbackComponents.url,
                       let (candidateData, _) = try? await request(url: fallbackURL, headers: page.headers, referer: backupReferer) {
                        let hopMs = Int(Date().timeIntervalSince(hopStart) * 1000)
                        SourceHealthRegistry.shared.recordSuccess(sourceKey: cdnPoolKey, urlOrHost: backupHost, latencyMs: hopMs)
                        fallbackData = candidateData
                        break
                    } else {
                        SourceHealthRegistry.shared.recordFailure(sourceKey: cdnPoolKey, urlOrHost: backupHost)
                    }
                }
                if let fallbackData {
                    data = fallbackData
                } else {
                    throw error
                }
            } else {
                throw error
            }
        }
        let isGif = imageURL.pathExtension.lowercased() == "gif"
        let effectiveSegments = isGif ? 0 : page.verticalSegments
        let image = Self.decodeImageData(from: data, allowDownsample: effectiveSegments <= 1 && !isGif)
            ?? decryptEncryptedPage(data, url: imageURL)
        guard let image else { throw ComicImageError.decodeFailed }
        return effectiveSegments > 1 ? unscramble(image, segments: effectiveSegments) : image
    }

    nonisolated static func decodeImageData(from data: Data, allowDownsample: Bool) -> UIImage? {
        guard allowDownsample,
              let source = CGImageSourceCreateWithData(data as CFData, nil),
              CGImageSourceGetCount(source) == 1 else {
            return UIImage(data: data)
        }

        let props = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any]
        let pixelWidth = (props?[kCGImagePropertyPixelWidth] as? NSNumber)?.intValue ?? 0
        let pixelHeight = (props?[kCGImagePropertyPixelHeight] as? NSNumber)?.intValue ?? 0

        // Downsample ONLY when pixelWidth > 1800 (unless height exceeds GPU texture limit 16384),
        // keeping 800x12000 Webtoon vertical strips at crisp 800px native width.
        guard pixelWidth > maxTargetPixelWidth || pixelHeight > maxGPUTextureDimension else {
            let decodeOpts: [CFString: Any] = [kCGImageSourceShouldCacheImmediately: true]
            if let cgImage = CGImageSourceCreateImageAtIndex(source, 0, decodeOpts as CFDictionary) {
                return UIImage(cgImage: cgImage)
            }
            return UIImage(data: data)
        }

        let scaleByWidth = pixelWidth > maxTargetPixelWidth
            ? Double(maxTargetPixelWidth) / Double(pixelWidth)
            : 1.0
        let scaleByHeight = pixelHeight > maxGPUTextureDimension
            ? Double(maxGPUTextureDimension) / Double(pixelHeight)
            : 1.0
        let scale = min(scaleByWidth, scaleByHeight)
        let maxDimension = max(pixelWidth, pixelHeight)
        let targetMaxPixelSize = max(1, min(maxGPUTextureDimension, Int((Double(maxDimension) * scale).rounded())))

        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceThumbnailMaxPixelSize: targetMaxPixelSize,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true
        ]
        if let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) {
            return UIImage(cgImage: cgImage)
        }
        return UIImage(data: data)
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
        guard data.count >= 16, data.count % kCCBlockSizeAES128 == 0 else { return nil }
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
            if let image = Self.decodeImageData(from: output, allowDownsample: true) { return image }
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
