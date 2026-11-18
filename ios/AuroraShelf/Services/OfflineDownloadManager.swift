import AVFoundation
import Combine
import CryptoKit
import Foundation
import Network
import UIKit

public enum OfflineDownloadStatus: Codable, Sendable, Equatable {
    case queued
    case resolving
    case downloading(progress: Double, bytesWritten: Int64, totalBytes: Int64)
    case completed
    case paused
    case failed(message: String)

    public var isCompleted: Bool {
        if case .completed = self { return true }
        return false
    }

    public var isDownloading: Bool {
        switch self {
        case .queued, .resolving, .downloading:
            return true
        default:
            return false
        }
    }

    public var progressValue: Double {
        switch self {
        case .completed:
            return 1.0
        case .downloading(let progress, _, _):
            return max(0.0, min(1.0, progress))
        default:
            return 0.0
        }
    }
}

public struct OfflineDownloadItem: Identifiable, Codable, Sendable, Equatable {
    public let id: String
    public let videoId: String
    public let title: String
    public let episodeLabel: String?
    public var episodeNumber: Int?
    public let sourceName: String
    public let pageUrl: String
    public let coverUrl: String
    public var streamUrl: String
    public var localRelativePath: String
    public var totalBytes: Int64
    public var downloadedBytes: Int64
    public var status: OfflineDownloadStatus
    public let createdAt: Date

    public init(
        id: String,
        videoId: String,
        title: String,
        episodeLabel: String?,
        episodeNumber: Int? = nil,
        sourceName: String,
        pageUrl: String,
        coverUrl: String,
        streamUrl: String,
        localRelativePath: String,
        totalBytes: Int64,
        downloadedBytes: Int64,
        status: OfflineDownloadStatus,
        createdAt: Date
    ) {
        self.id = id
        self.videoId = videoId
        self.title = title
        self.episodeLabel = episodeLabel
        self.episodeNumber = episodeNumber
        self.sourceName = sourceName
        self.pageUrl = pageUrl
        self.coverUrl = coverUrl
        self.streamUrl = streamUrl
        self.localRelativePath = localRelativePath
        self.totalBytes = totalBytes
        self.downloadedBytes = downloadedBytes
        self.status = status
        self.createdAt = createdAt
    }

    public var displayTitle: String {
        if let ep = episodeLabel, !ep.isEmpty {
            return "\(title) · \(ep)"
        }
        return title
    }

    public func toVideoItem() -> VideoItem {
        let resolvedId: String
        if let epNum = episodeNumber, epNum > 0 {
            let baseId = videoId.replacingOccurrences(of: #"-ep-\d+$"#, with: "", options: .regularExpression)
            resolvedId = "\(baseId)-ep-\(epNum)"
        } else {
            resolvedId = videoId
        }
        return VideoItem(
            id: resolvedId,
            title: displayTitle,
            subtitle: "离线缓存 · \(sourceName)",
            coverUrl: coverUrl,
            playbackUrl: pageUrl,
            duration: "",
            rating: "",
            tags: ["离线缓存", sourceName],
            category: "离线缓存",
            isTop10: false,
            rankNumber: nil,
            sourceName: sourceName
        )
    }
}

/// 全局离线视频缓存下载中枢（支持 HLS .m3u8 Master/KEY/MAP/fMP4 4路并发本地化下载与直链 MP4 离线硬解）
@MainActor
public final class OfflineDownloadManager: ObservableObject {
    public static let shared = OfflineDownloadManager()

    private static let minimumFreeDiskBytes: Int64 = 300 * 1024 * 1024
    private static let maxSegmentConcurrency = 4

    @Published public private(set) var items: [OfflineDownloadItem] = []

    private let fileManager = FileManager.default
    let baseDirectory: URL
    private let legacyBaseDirectory: URL?
    let metaFileURL: URL
    private let legacyMetaFileURL: URL?
    private let localServer: OfflineLocalHTTPServer
    private var activeTasks: [String: Task<Void, Never>] = [:]

    private convenience init() {
        let fm = FileManager.default
        let appSupport = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let docs = fm.urls(for: .documentDirectory, in: .userDomainMask)[0]
        self.init(
            baseDirectory: appSupport.appendingPathComponent("OfflineVideos", isDirectory: true),
            metaFileURL: appSupport.appendingPathComponent("offline_downloads.json"),
            legacyBaseDirectory: docs.appendingPathComponent("OfflineVideos", isDirectory: true),
            legacyMetaFileURL: docs.appendingPathComponent("offline_downloads.json")
        )
    }

    init(
        baseDirectory: URL,
        metaFileURL: URL? = nil,
        legacyBaseDirectory: URL? = nil,
        legacyMetaFileURL: URL? = nil
    ) {
        self.baseDirectory = baseDirectory
        self.legacyBaseDirectory = legacyBaseDirectory
        self.metaFileURL = metaFileURL ?? baseDirectory.deletingLastPathComponent().appendingPathComponent("offline_downloads.json")
        self.legacyMetaFileURL = legacyMetaFileURL
        let roots = [baseDirectory, legacyBaseDirectory].compactMap { $0 }
        self.localServer = OfflineLocalHTTPServer(searchRoots: roots)
        Self.prepareExcludedDirectory(baseDirectory, fileManager: fileManager)
        // Lazy-start localServer only when local HLS playback is actually requested.
        loadFromDisk()
    }

    private static func prepareExcludedDirectory(_ directory: URL, fileManager: FileManager) {
        let parent = directory.deletingLastPathComponent()
        if !fileManager.fileExists(atPath: parent.path) {
            try? fileManager.createDirectory(at: parent, withIntermediateDirectories: true)
        }
        if !fileManager.fileExists(atPath: directory.path) {
            try? fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        }
        var dirURL = directory
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? dirURL.setResourceValues(values)
    }

    public var completedItems: [OfflineDownloadItem] {
        items.filter { $0.status.isCompleted }
    }

    public var activeItems: [OfflineDownloadItem] {
        items.filter { $0.status.isDownloading }
    }

    public var failedItems: [OfflineDownloadItem] {
        items.filter {
            if case .failed = $0.status { return true }
            return false
        }
    }

    public var overallProgress: Double {
        let active = activeItems
        if !active.isEmpty {
            let total = active.reduce(0.0) { $0 + $1.status.progressValue }
            return max(0.0, min(1.0, total / Double(active.count)))
        }
        return completedItems.isEmpty ? 0.0 : 1.0
    }

    public var totalDownloadedBytes: Int64 {
        items.filter { $0.status.isCompleted }.reduce(0) { $0 + $1.downloadedBytes }
    }

    public func formattedTotalSpace() -> String {
        ByteCountFormatter.string(fromByteCount: totalDownloadedBytes, countStyle: .file)
    }

    public func status(for video: VideoItem, episode: PlaybackEpisode? = nil) -> OfflineDownloadStatus? {
        findMatchingItem(for: video, episode: episode)?.status
    }

    public func isDownloaded(video: VideoItem, episode: PlaybackEpisode? = nil) -> Bool {
        findMatchingItem(for: video, episode: episode)?.status.isCompleted ?? false
    }

    /// 自动向后链式缓存下一集（剧集连续离线缓存）
    public func queueNextEpisodeIfAvailable(video: VideoItem, currentEpisode: PlaybackEpisode?, allEpisodes: [PlaybackEpisode]) {
        guard let currentEp = currentEpisode, !allEpisodes.isEmpty else { return }
        if let nextEp = allEpisodes.first(where: { $0.number == currentEp.number + 1 }) {
            if !isDownloaded(video: video, episode: nextEp) && status(for: video, episode: nextEp) == nil {
                startDownload(video: video, episode: nextEp)
            }
        }
    }

    private func findMatchingItem(for video: VideoItem, episode: PlaybackEpisode?) -> OfflineDownloadItem? {
        let primaryID = Self.generateID(video: video, episode: episode)
        if let direct = items.first(where: { $0.id == primaryID }) {
            return direct
        }
        let legacyID = Self.legacyGenerateID(video: video, episode: episode)
        if let legacy = items.first(where: { $0.id == legacyID }) {
            return legacy
        }
        if let epNum = episode?.number ?? Self.extractEpisodeNumber(fromVideoID: video.id) {
            let stripped = Self.strippedVideoID(video.id)
            return items.first(where: {
                Self.strippedVideoID($0.videoId) == stripped && $0.sourceName == video.sourceName && $0.episodeNumber == epNum
            })
        }
        return nil
    }

    private func completedItem(for video: VideoItem, episode: PlaybackEpisode? = nil) -> OfflineDownloadItem? {
        if let matched = findMatchingItem(for: video, episode: episode), matched.status.isCompleted {
            return matched
        }
        if episode == nil {
            return items.first(where: { $0.status.isCompleted && $0.pageUrl == video.playbackUrl })
        }
        return nil
    }

    private func existingLocalFileURL(forRelativePath relativePath: String) -> URL? {
        let primary = baseDirectory.appendingPathComponent(relativePath)
        if fileManager.fileExists(atPath: primary.path) {
            return primary
        }
        if let legacyBaseDirectory {
            let fallback = legacyBaseDirectory.appendingPathComponent(relativePath)
            if fileManager.fileExists(atPath: fallback.path) {
                return fallback
            }
        }
        return nil
    }

    public func playableLocalURL(for video: VideoItem, episode: PlaybackEpisode? = nil) async -> URL? {
        guard let item = completedItem(for: video, episode: episode) else { return nil }
        return await playableLocalURL(for: item)
    }

    public func playableLocalURL(for item: OfflineDownloadItem) async -> URL? {
        guard item.status.isCompleted else { return nil }
        guard let fullPath = existingLocalFileURL(forRelativePath: item.localRelativePath) else { return nil }
        if item.localRelativePath.lowercased().hasSuffix(".m3u8") {
            return await localServer.playbackURL(forRelativePath: item.localRelativePath) ?? fullPath
        }
        return fullPath
    }

    public func localPlaybackURL(for video: VideoItem, episode: PlaybackEpisode? = nil) -> URL? {
        guard let item = completedItem(for: video, episode: episode) else {
            return nil
        }
        return localPlaybackURL(for: item)
    }

    public func localPlaybackURL(for item: OfflineDownloadItem) -> URL? {
        guard item.status.isCompleted else { return nil }
        guard let fullPath = existingLocalFileURL(forRelativePath: item.localRelativePath) else { return nil }
        if item.localRelativePath.lowercased().hasSuffix(".m3u8"),
           let loopbackURL = localServer.urlIfReady(forRelativePath: item.localRelativePath) {
            return loopbackURL
        }
        return fullPath
    }

    public func startDownload(video: VideoItem, episode: PlaybackEpisode? = nil) {
        let itemId = Self.generateID(video: video, episode: episode)
        let pageUrl = episode?.pageUrl ?? video.playbackUrl
        let epLabel = episode?.label
        let epNumber = episode?.number ?? Self.extractEpisodeNumber(fromVideoID: video.id)
        let cleanVideoId = Self.strippedVideoID(video.id)

        if let existing = findMatchingItem(for: video, episode: episode) {
            if existing.status.isCompleted || existing.status.isDownloading {
                return
            }
        }

        let newItem = OfflineDownloadItem(
            id: itemId,
            videoId: cleanVideoId,
            title: video.title,
            episodeLabel: epLabel,
            episodeNumber: epNumber,
            sourceName: video.sourceName,
            pageUrl: pageUrl,
            coverUrl: video.coverUrl,
            streamUrl: "",
            localRelativePath: "\(itemId)/local_index.m3u8",
            totalBytes: 0,
            downloadedBytes: 0,
            status: .resolving,
            createdAt: Date()
        )

        if let idx = items.firstIndex(where: { $0.id == itemId }) {
            items[idx] = newItem
        } else {
            items.insert(newItem, at: 0)
        }
        saveToDisk()

        let task = Task { [weak self] in
            guard let self = self else { return }
            await self.executeDownload(itemId: itemId, video: video, pageUrl: pageUrl)
        }
        activeTasks[itemId] = task
    }

    public func retryDownload(id: String) {
        guard let existing = items.first(where: { $0.id == id }),
              !existing.status.isCompleted,
              !existing.status.isDownloading else { return }
        activeTasks[id]?.cancel()
        activeTasks.removeValue(forKey: id)
        updateItem(id: id) { item in
            item.status = .resolving
        }
        saveToDisk()
        let stubVideo = existing.toVideoItem()
        let task = Task { [weak self] in
            guard let self = self else { return }
            await self.executeDownload(itemId: id, video: stubVideo, pageUrl: existing.pageUrl)
        }
        activeTasks[id] = task
    }

    public func removeDownload(id: String) {
        activeTasks[id]?.cancel()
        activeTasks.removeValue(forKey: id)

        if let idx = items.firstIndex(where: { $0.id == id }) {
            let item = items[idx]
            let itemDir = baseDirectory.appendingPathComponent(item.id)
            try? fileManager.removeItem(at: itemDir)
            if let legacyBaseDirectory {
                try? fileManager.removeItem(at: legacyBaseDirectory.appendingPathComponent(item.id))
            }
            items.remove(at: idx)
            saveToDisk()
        }
    }

    public func removeAll() {
        activeTasks.values.forEach { $0.cancel() }
        activeTasks.removeAll()
        try? fileManager.removeItem(at: baseDirectory)
        Self.prepareExcludedDirectory(baseDirectory, fileManager: fileManager)
        if let legacyBaseDirectory {
            try? fileManager.removeItem(at: legacyBaseDirectory)
        }
        items.removeAll()
        saveToDisk()
    }

    // MARK: - 内部下载执行流程

    private func ensureSufficientDiskSpace() throws {
        let probeURL = baseDirectory.deletingLastPathComponent()
        if let values = try? probeURL.resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey]),
           let available = values.volumeAvailableCapacityForImportantUsage,
           available > 0,
           available < Self.minimumFreeDiskBytes {
            throw NSError(
                domain: "OfflineDownload",
                code: -10,
                userInfo: [NSLocalizedDescriptionKey: "设备可用存储空间不足 300 MB，无法开始缓存"]
            )
        }
        if let sysAttrs = try? fileManager.attributesOfFileSystem(forPath: probeURL.path),
           let freeNumber = sysAttrs[.systemFreeSize] as? NSNumber,
           freeNumber.int64Value > 0,
           freeNumber.int64Value < Self.minimumFreeDiskBytes {
            throw NSError(
                domain: "OfflineDownload",
                code: -10,
                userInfo: [NSLocalizedDescriptionKey: "设备可用存储空间不足 300 MB，无法开始缓存"]
            )
        }
    }

    private func beginBackgroundGuard(name: String) -> UIBackgroundTaskIdentifier {
        var bgTask: UIBackgroundTaskIdentifier = .invalid
        bgTask = UIApplication.shared.beginBackgroundTask(withName: name) {
            if bgTask != .invalid {
                UIApplication.shared.endBackgroundTask(bgTask)
                bgTask = .invalid
            }
        }
        return bgTask
    }

    private func endBackgroundGuard(_ bgTask: inout UIBackgroundTaskIdentifier) {
        if bgTask != .invalid {
            UIApplication.shared.endBackgroundTask(bgTask)
            bgTask = .invalid
        }
    }

    private func executeDownload(itemId: String, video: VideoItem, pageUrl: String) async {
        let itemDir = baseDirectory.appendingPathComponent(itemId, isDirectory: true)
        Self.prepareExcludedDirectory(baseDirectory, fileManager: fileManager)
        try? fileManager.createDirectory(at: itemDir, withIntermediateDirectories: true)

        var bgTask = beginBackgroundGuard(name: "AuroraOfflineVideo_\(itemId)")
        defer { endBackgroundGuard(&bgTask) }

        do {
            try ensureSufficientDiskSpace()

            let stubVideo = VideoItem(
                id: video.id,
                title: video.title,
                subtitle: video.subtitle,
                coverUrl: video.coverUrl,
                playbackUrl: pageUrl,
                duration: video.duration,
                rating: video.rating,
                tags: video.tags,
                category: video.category,
                isTop10: video.isTop10,
                rankNumber: video.rankNumber,
                sourceName: video.sourceName
            )

            let sourceResult = try await VideoRepository.shared.resolvePlayback(video: stubVideo)
            let streamURL = sourceResult.url

            updateItem(id: itemId) { item in
                item.streamUrl = streamURL.absoluteString
                item.status = .downloading(progress: 0.05, bytesWritten: 0, totalBytes: 0)
            }

            if Self.looksLikeHLSURL(streamURL) {
                try await downloadHLS(itemId: itemId, streamURL: streamURL, sourceResult: sourceResult, targetDir: itemDir)
            } else {
                try await downloadDirectOrSniffedHLS(itemId: itemId, streamURL: streamURL, sourceResult: sourceResult, targetDir: itemDir)
            }

            activeTasks.removeValue(forKey: itemId)
        } catch is CancellationError {
            activeTasks.removeValue(forKey: itemId)
        } catch {
            updateItem(id: itemId) { item in
                item.status = .failed(message: error.localizedDescription)
            }
            saveToDisk()
            activeTasks.removeValue(forKey: itemId)
        }
    }

    static func looksLikeHLSURL(_ url: URL) -> Bool {
        let ext = url.pathExtension.lowercased()
        if ext == "m3u8" || ext == "m3u" { return true }
        return url.path.lowercased().contains(".m3u8")
    }

    static func isHLSContentTypeOrBody(response: URLResponse, leadingBytes: Data) -> Bool {
        if let mime = response.mimeType?.lowercased(),
           mime.contains("mpegurl") || mime.contains("x-mpegurl") {
            return true
        }
        if let http = response as? HTTPURLResponse,
           let ct = http.value(forHTTPHeaderField: "Content-Type")?.lowercased(),
           ct.contains("mpegurl") {
            return true
        }
        let prefixText = String(data: leadingBytes.prefix(128), encoding: .utf8)?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return prefixText.hasPrefix("#EXTM3U")
    }

    private nonisolated static func buildMediaRequest(
        url: URL,
        sourceResult: PlaybackSourceResult,
        timeout: TimeInterval = 30
    ) -> URLRequest {
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: timeout)
        var hasUserAgent = false
        for (k, v) in sourceResult.headers {
            if k.caseInsensitiveCompare("Origin") == .orderedSame,
               let originHost = URL(string: v)?.host?.lowercased(),
               let targetHost = url.host?.lowercased(),
               originHost != targetHost {
                continue
            }
            if k.caseInsensitiveCompare("User-Agent") == .orderedSame {
                hasUserAgent = true
            }
            req.setValue(v, forHTTPHeaderField: k)
        }
        if !hasUserAgent {
            req.setValue(PlaybackSourceResult.defaultUserAgent, forHTTPHeaderField: "User-Agent")
        }
        if let ref = sourceResult.referer, !ref.isEmpty, let refURL = URL(string: ref) {
            req.setValue(refURL.absoluteString, forHTTPHeaderField: "Referer")
        }
        return req
    }

    // MARK: - HLS Playlist Parsing Helpers (Unit-Testable)

    static func selectBestVariantURL(masterPlaylist: String, baseURL: URL) -> URL? {
        let lines = masterPlaylist.components(separatedBy: .newlines)
        let bwRegex = try? NSRegularExpression(pattern: #"BANDWIDTH=(\d+)"#, options: .caseInsensitive)
        let codecsRegex = try? NSRegularExpression(pattern: #"CODECS="([^"]+)""#, options: .caseInsensitive)

        struct VariantCandidate {
            let url: URL
            let bandwidth: Int64
            let isAV1Only: Bool
            let order: Int
        }

        var candidates: [VariantCandidate] = []
        var orderCounter = 0
        var idx = 0

        while idx < lines.count {
            let trimmed = lines[idx].trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.uppercased().hasPrefix("#EXT-X-STREAM-INF") {
                let nsAttr = trimmed as NSString
                let fullRange = NSRange(location: 0, length: nsAttr.length)
                var bandwidth: Int64 = 0
                if let match = bwRegex?.firstMatch(in: trimmed, range: fullRange),
                   let val = Int64(nsAttr.substring(with: match.range(at: 1))) {
                    bandwidth = val
                }
                var isAV1Only = false
                if let cMatch = codecsRegex?.firstMatch(in: trimmed, range: fullRange) {
                    let codecs = nsAttr.substring(with: cMatch.range(at: 1)).lowercased()
                    isAV1Only = codecs.contains("av01") && !codecs.contains("avc1") && !codecs.contains("hvc1") && !codecs.contains("hev1")
                }

                var nextIdx = idx + 1
                while nextIdx < lines.count {
                    let uriCandidate = lines[nextIdx].trimmingCharacters(in: .whitespacesAndNewlines)
                    if uriCandidate.isEmpty {
                        nextIdx += 1
                        continue
                    }
                    if !uriCandidate.hasPrefix("#"),
                       let resolved = URL(string: uriCandidate, relativeTo: baseURL)?.absoluteURL {
                        candidates.append(VariantCandidate(
                            url: resolved,
                            bandwidth: bandwidth,
                            isAV1Only: isAV1Only,
                            order: orderCounter
                        ))
                        orderCounter += 1
                    }
                    break
                }
                idx = nextIdx + 1
                continue
            }
            idx += 1
        }

        let pool = candidates.filter { !$0.isAV1Only }
        let effectivePool = pool.isEmpty ? candidates : pool
        return effectivePool.max { lhs, rhs in
            if lhs.bandwidth != rhs.bandwidth {
                return lhs.bandwidth < rhs.bandwidth
            }
            return lhs.order > rhs.order
        }?.url
    }

    struct HLSRewritePlan: Sendable {
        struct AssetTask: Sendable {
            let index: Int
            let url: URL
            let localFileName: String
        }
        let rewrittenPlaylist: String
        let auxiliaryAssets: [AssetTask] // #EXT-X-KEY & #EXT-X-MAP
        let segments: [AssetTask]
    }

    static func buildLocalHLSPlan(mediaPlaylist: String, baseURL: URL) -> HLSRewritePlan {
        let lines = mediaPlaylist.components(separatedBy: .newlines)
        let uriAttrRegex = try? NSRegularExpression(pattern: #"URI="([^"]+)""#)

        var rewrittenLines: [String] = []
        rewrittenLines.reserveCapacity(lines.count)

        var auxByURL: [String: String] = [:]
        var auxTasks: [HLSRewritePlan.AssetTask] = []
        var segmentTasks: [HLSRewritePlan.AssetTask] = []

        for rawLine in lines {
            let trimmed = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.isEmpty {
                continue
            }
            if trimmed.hasPrefix("#") {
                let upper = trimmed.uppercased()
                if (upper.hasPrefix("#EXT-X-KEY") || upper.hasPrefix("#EXT-X-MAP")),
                   let regex = uriAttrRegex {
                    let nsLine = trimmed as NSString
                    let matches = regex.matches(in: trimmed, range: NSRange(location: 0, length: nsLine.length))
                    var mutable = trimmed
                    for match in matches.reversed() {
                        let uriRange = match.range(at: 1)
                        let rawURI = nsLine.substring(with: uriRange)
                        guard let resolved = URL(string: rawURI, relativeTo: baseURL)?.absoluteURL,
                              let swiftRange = Range(uriRange, in: mutable) else {
                            continue
                        }
                        let localName: String
                        if let existing = auxByURL[resolved.absoluteString] {
                            localName = existing
                        } else {
                            let idx = auxTasks.count
                            if upper.hasPrefix("#EXT-X-KEY") {
                                localName = "asset_key_\(idx).key"
                            } else {
                                let ext = sanitizedMediaExtension(for: resolved, defaultExtension: "mp4")
                                localName = "asset_map_\(idx).\(ext)"
                            }
                            auxByURL[resolved.absoluteString] = localName
                            auxTasks.append(.init(index: idx, url: resolved, localFileName: localName))
                        }
                        mutable.replaceSubrange(swiftRange, with: localName)
                    }
                    rewrittenLines.append(mutable)
                } else {
                    rewrittenLines.append(trimmed)
                }
            } else if let segURL = URL(string: trimmed, relativeTo: baseURL)?.absoluteURL {
                let segIdx = segmentTasks.count
                let ext = sanitizedMediaExtension(for: segURL, defaultExtension: "ts")
                let localFileName = "segment_\(segIdx).\(ext)"
                segmentTasks.append(.init(index: segIdx, url: segURL, localFileName: localFileName))
                rewrittenLines.append(localFileName)
            }
        }

        return HLSRewritePlan(
            rewrittenPlaylist: rewrittenLines.joined(separator: "\n"),
            auxiliaryAssets: auxTasks,
            segments: segmentTasks
        )
    }

    static func sanitizedMediaExtension(for url: URL, defaultExtension: String) -> String {
        let ext = url.pathExtension.lowercased()
        let allowed: Set<String> = ["ts", "m4s", "mp4", "m4v", "cmfv", "cmfa", "aac", "mp3", "key"]
        return allowed.contains(ext) ? ext : defaultExtension
    }

    private func downloadHLS(
        itemId: String,
        streamURL: URL,
        sourceResult: PlaybackSourceResult,
        targetDir: URL
    ) async throws {
        let playlistReq = Self.buildMediaRequest(url: streamURL, sourceResult: sourceResult)
        let (playlistData, playlistResponse) = try await URLSession.permissiveShared.data(for: playlistReq)
        try Self.requireSuccessfulMediaResponse(playlistResponse)
        try await downloadHLSFromFetchedPlaylist(
            playlistData: playlistData,
            playlistResponse: playlistResponse,
            initialURL: streamURL,
            sourceResult: sourceResult,
            itemId: itemId,
            targetDir: targetDir
        )
    }

    private func downloadHLSFromFetchedPlaylist(
        playlistData: Data,
        playlistResponse: URLResponse,
        initialURL: URL,
        sourceResult: PlaybackSourceResult,
        itemId: String,
        targetDir: URL
    ) async throws {
        guard let playlistText = String(data: playlistData, encoding: .utf8) else {
            throw NSError(domain: "OfflineDownload", code: -1, userInfo: [NSLocalizedDescriptionKey: "无法解析播放列表"])
        }

        var finalMediaPlaylistURL = playlistResponse.url ?? initialURL
        var effectivePlaylistText = playlistText

        if playlistText.contains("#EXT-X-STREAM-INF"),
           let bestVariantURL = Self.selectBestVariantURL(masterPlaylist: playlistText, baseURL: finalMediaPlaylistURL) {
            let varReq = Self.buildMediaRequest(url: bestVariantURL, sourceResult: sourceResult)
            let (varData, variantResponse) = try await URLSession.permissiveShared.data(for: varReq)
            try Self.requireSuccessfulMediaResponse(variantResponse)
            guard let varText = String(data: varData, encoding: .utf8) else {
                throw NSError(domain: "OfflineDownload", code: -1, userInfo: [NSLocalizedDescriptionKey: "无法解析子播放列表"])
            }
            finalMediaPlaylistURL = variantResponse.url ?? bestVariantURL
            effectivePlaylistText = varText
        }

        let plan = Self.buildLocalHLSPlan(mediaPlaylist: effectivePlaylistText, baseURL: finalMediaPlaylistURL)
        guard !plan.segments.isEmpty else {
            throw NSError(domain: "OfflineDownload", code: -2, userInfo: [NSLocalizedDescriptionKey: "未找到切片内容"])
        }

        var downloadedBytes: Int64 = 0

        // 1. 先下载加密密钥（#EXT-X-KEY）与 fMP4 初始化头（#EXT-X-MAP）
        for asset in plan.auxiliaryAssets {
            try Task.checkCancellation()
            let localAssetURL = targetDir.appendingPathComponent(asset.localFileName)
            if !fileManager.fileExists(atPath: localAssetURL.path) {
                let req = Self.buildMediaRequest(url: asset.url, sourceResult: sourceResult)
                let (data, response) = try await URLSession.permissiveShared.data(for: req)
                try Task.checkCancellation()
                try Self.requireSuccessfulMediaResponse(response)
                guard !data.isEmpty else {
                    throw NSError(domain: "OfflineDownload", code: -3, userInfo: [NSLocalizedDescriptionKey: "密钥或初始化分片为空"])
                }
                try data.write(to: localAssetURL, options: .atomic)
                downloadedBytes += Int64(data.count)
            } else {
                let attrs = try? fileManager.attributesOfItem(atPath: localAssetURL.path)
                downloadedBytes += (attrs?[.size] as? Int64) ?? 0
            }
        }

        // 2. 有界 4 路并发下载全部分片
        let totalCount = plan.segments.count
        var completedCount = 0

        try await withThrowingTaskGroup(of: Int64.self) { group in
            var nextIndexToSchedule = 0
            let initialBatch = min(Self.maxSegmentConcurrency, totalCount)

            for _ in 0..<initialBatch {
                let seg = plan.segments[nextIndexToSchedule]
                nextIndexToSchedule += 1
                group.addTask {
                    try await Self.downloadSingleSegment(seg, sourceResult: sourceResult, targetDir: targetDir)
                }
            }

            while let segBytes = try await group.next() {
                try Task.checkCancellation()
                downloadedBytes += segBytes
                completedCount += 1
                let progress = Double(completedCount) / Double(totalCount)
                updateItem(id: itemId) { item in
                    item.downloadedBytes = downloadedBytes
                    item.totalBytes = max(downloadedBytes, Int64(Double(downloadedBytes) / max(0.01, progress)))
                    item.status = .downloading(progress: progress, bytesWritten: downloadedBytes, totalBytes: item.totalBytes)
                }

                if nextIndexToSchedule < totalCount {
                    let seg = plan.segments[nextIndexToSchedule]
                    nextIndexToSchedule += 1
                    group.addTask {
                        try await Self.downloadSingleSegment(seg, sourceResult: sourceResult, targetDir: targetDir)
                    }
                }
            }
        }

        try Task.checkCancellation()
        let localPlaylistURL = targetDir.appendingPathComponent("local_index.m3u8")
        guard let playlistOutData = plan.rewrittenPlaylist.data(using: .utf8) else {
            throw NSError(domain: "OfflineDownload", code: -4, userInfo: [NSLocalizedDescriptionKey: "写出本地播放列表失败"])
        }
        try playlistOutData.write(to: localPlaylistURL, options: .atomic)

        updateItem(id: itemId) { item in
            item.localRelativePath = "\(itemId)/local_index.m3u8"
            item.status = .completed
            item.downloadedBytes = downloadedBytes
            item.totalBytes = downloadedBytes
        }
        saveToDisk()
    }

    private nonisolated static func downloadSingleSegment(
        _ segment: HLSRewritePlan.AssetTask,
        sourceResult: PlaybackSourceResult,
        targetDir: URL
    ) async throws -> Int64 {
        try Task.checkCancellation()
        let fm = FileManager.default
        let localSegURL = targetDir.appendingPathComponent(segment.localFileName)
        if fm.fileExists(atPath: localSegURL.path),
           let existingSize = (try? fm.attributesOfItem(atPath: localSegURL.path)[.size] as? NSNumber)?.int64Value,
           existingSize > 0 {
            return existingSize
        }

        let maxAttempts = 3
        var lastError: Error?
        for attempt in 0..<maxAttempts {
            try Task.checkCancellation()
            do {
                let segReq = buildMediaRequest(url: segment.url, sourceResult: sourceResult)
                let (data, response) = try await URLSession.permissiveShared.data(for: segReq)
                try Task.checkCancellation()
                try requireSuccessfulMediaResponse(response)
                guard !data.isEmpty else {
                    throw NSError(domain: "OfflineDownload", code: -3, userInfo: [NSLocalizedDescriptionKey: "切片为空"])
                }
                try data.write(to: localSegURL, options: .atomic)
                return Int64(data.count)
            } catch is CancellationError {
                throw CancellationError()
            } catch {
                lastError = error
                if attempt < maxAttempts - 1 {
                    try await Task.sleep(for: .milliseconds(350 * (attempt + 1)))
                }
            }
        }
        throw lastError ?? URLError(.cannotLoadFromNetwork)
    }

    private func downloadDirectOrSniffedHLS(
        itemId: String,
        streamURL: URL,
        sourceResult: PlaybackSourceResult,
        targetDir: URL
    ) async throws {
        let req = Self.buildMediaRequest(url: streamURL, sourceResult: sourceResult, timeout: 60)
        let (tempURL, response) = try await performDirectDownload(itemId: itemId, request: req)
        defer {
            if fileManager.fileExists(atPath: tempURL.path) {
                try? fileManager.removeItem(at: tempURL)
            }
        }
        try Task.checkCancellation()
        try Self.requireSuccessfulMediaResponse(response)

        let fileSize = (try? fileManager.attributesOfItem(atPath: tempURL.path)[.size] as? NSNumber)?.int64Value ?? 0
        var leadingBytes = Data()
        if fileSize > 0 && fileSize <= 4 * 1024 * 1024,
           let handle = try? FileHandle(forReadingFrom: tempURL) {
            leadingBytes = (try? handle.read(upToCount: 128)) ?? Data()
            try? handle.close()
        }

        if Self.isHLSContentTypeOrBody(response: response, leadingBytes: leadingBytes),
           let playlistData = try? Data(contentsOf: tempURL) {
            try await downloadHLSFromFetchedPlaylist(
                playlistData: playlistData,
                playlistResponse: response,
                initialURL: streamURL,
                sourceResult: sourceResult,
                itemId: itemId,
                targetDir: targetDir
            )
            return
        }

        let ext = Self.sanitizedMediaExtension(for: response.url ?? streamURL, defaultExtension: "mp4")
        let fileName = ext == "ts" ? "video.ts" : "video.mp4"
        let localFileURL = targetDir.appendingPathComponent(fileName)
        if fileManager.fileExists(atPath: localFileURL.path) {
            try? fileManager.removeItem(at: localFileURL)
        }
        try fileManager.moveItem(at: tempURL, to: localFileURL)

        let size = (try? fileManager.attributesOfItem(atPath: localFileURL.path)[.size] as? Int64) ?? max(0, response.expectedContentLength)

        updateItem(id: itemId) { item in
            item.localRelativePath = "\(itemId)/\(fileName)"
            item.status = .completed
            item.downloadedBytes = size
            item.totalBytes = size
        }
        saveToDisk()
    }

    private func performDirectDownload(itemId: String, request: URLRequest) async throws -> (URL, URLResponse) {
        final class DirectDownloadTaskBox: @unchecked Sendable {
            private let lock = NSLock()
            private var task: URLSessionDownloadTask?
            private var pollTask: Task<Void, Never>?
            private var isCancelled = false

            func attach(_ downloadTask: URLSessionDownloadTask) -> Bool {
                lock.lock()
                defer { lock.unlock() }
                if isCancelled { return false }
                task = downloadTask
                return true
            }

            func setPollTask(_ poll: Task<Void, Never>) {
                lock.lock()
                if isCancelled {
                    lock.unlock()
                    poll.cancel()
                    return
                }
                pollTask = poll
                lock.unlock()
            }

            func cancelPoll() {
                lock.lock()
                let poll = pollTask
                pollTask = nil
                lock.unlock()
                poll?.cancel()
            }

            func cancelAll() {
                lock.lock()
                isCancelled = true
                let current = task
                let poll = pollTask
                task = nil
                pollTask = nil
                lock.unlock()
                poll?.cancel()
                current?.cancel()
            }
        }

        let box = DirectDownloadTaskBox()
        let stableTempURL = fileManager.temporaryDirectory
            .appendingPathComponent("aurorashelf_dl_\(UUID().uuidString).tmp")

        do {
            return try await withTaskCancellationHandler {
                try await withCheckedThrowingContinuation { continuation in
                    let downloadTask = URLSession.permissiveShared.downloadTask(with: request) { tempURL, response, error in
                        box.cancelPoll()
                        if let error = error {
                            try? FileManager.default.removeItem(at: stableTempURL)
                            continuation.resume(throwing: error)
                            return
                        }
                        guard let tempURL = tempURL, let response = response else {
                            try? FileManager.default.removeItem(at: stableTempURL)
                            continuation.resume(throwing: NSError(
                                domain: "OfflineDownload",
                                code: -1,
                                userInfo: [NSLocalizedDescriptionKey: "下载响应为空"]
                            ))
                            return
                        }
                        do {
                            if FileManager.default.fileExists(atPath: stableTempURL.path) {
                                try? FileManager.default.removeItem(at: stableTempURL)
                            }
                            try FileManager.default.moveItem(at: tempURL, to: stableTempURL)
                            continuation.resume(returning: (stableTempURL, response))
                        } catch {
                            try? FileManager.default.removeItem(at: stableTempURL)
                            continuation.resume(throwing: error)
                        }
                    }
                    guard box.attach(downloadTask) else {
                        downloadTask.cancel()
                        continuation.resume(throwing: CancellationError())
                        return
                    }

                    let poll = Task { @MainActor [weak self] in
                        while !Task.isCancelled && (downloadTask.state == .running || downloadTask.state == .suspended) {
                            let received = max(Int64(0), downloadTask.countOfBytesReceived)
                            let expected = max(Int64(0), downloadTask.countOfBytesExpectedToReceive)
                            let fraction: Double = expected > 0
                                ? min(0.99, max(0.05, Double(received) / Double(expected)))
                                : 0.1
                            self?.updateItem(id: itemId) { item in
                                if item.status.isDownloading {
                                    item.status = .downloading(progress: fraction, bytesWritten: received, totalBytes: expected)
                                    item.downloadedBytes = received
                                    if expected > 0 {
                                        item.totalBytes = expected
                                    }
                                }
                            }
                            try? await Task.sleep(nanoseconds: 250_000_000)
                        }
                    }
                    box.setPollTask(poll)
                    downloadTask.resume()
                }
            } onCancel: {
                box.cancelAll()
            }
        } catch {
            try? fileManager.removeItem(at: stableTempURL)
            throw error
        }
    }

    private nonisolated static func requireSuccessfulMediaResponse(_ response: URLResponse) throws {
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            let status = (response as? HTTPURLResponse)?.statusCode ?? -1
            throw NSError(domain: "OfflineDownload", code: status, userInfo: [NSLocalizedDescriptionKey: "媒体服务器返回 HTTP \(status)"])
        }
    }

    private func updateItem(id: String, mutate: (inout OfflineDownloadItem) -> Void) {
        if let idx = items.firstIndex(where: { $0.id == id }) {
            var item = items[idx]
            mutate(&item)
            items[idx] = item
        }
    }

    static func strippedVideoID(_ rawID: String) -> String {
        rawID.replacingOccurrences(of: #"-ep-\d+$"#, with: "", options: .regularExpression)
    }

    static func extractEpisodeNumber(fromVideoID rawID: String) -> Int? {
        guard let range = rawID.range(of: #"-ep-(\d+)$"#, options: .regularExpression) else {
            return nil
        }
        let suffix = rawID[range].replacingOccurrences(of: "-ep-", with: "")
        return Int(suffix)
    }

    static func generateID(video: VideoItem, episode: PlaybackEpisode?) -> String {
        let stripped = strippedVideoID(video.id)
        let epNum = episode?.number ?? extractEpisodeNumber(fromVideoID: video.id) ?? 0
        let rawKey = "\(video.sourceName)|\(stripped)|\(epNum)"
        let digest = SHA256.hash(data: Data(rawKey.utf8))
        let hex16 = digest.prefix(8).map { String(format: "%02x", $0) }.joined()
        return "dl_\(hex16)"
    }

    static func legacyGenerateID(video: VideoItem, episode: PlaybackEpisode?) -> String {
        let stripped = strippedVideoID(video.id)
        let base = stripped.replacingOccurrences(of: "hua-", with: "vod-")
        if let ep = episode {
            return "\(base)_ep\(ep.number)"
        }
        if let epNum = extractEpisodeNumber(fromVideoID: video.id) {
            return "\(base)_ep\(epNum)"
        }
        return base
    }

    private func loadFromDisk() {
        var sourceMetaURL: URL?
        var loadedFromLegacy = false
        if fileManager.fileExists(atPath: metaFileURL.path) {
            sourceMetaURL = metaFileURL
        } else if let legacyMetaFileURL, fileManager.fileExists(atPath: legacyMetaFileURL.path) {
            sourceMetaURL = legacyMetaFileURL
            loadedFromLegacy = true
        }
        guard let activeMetaURL = sourceMetaURL,
              let data = try? Data(contentsOf: activeMetaURL),
              let decoded = try? JSONDecoder().decode([OfflineDownloadItem].self, from: data) else {
            return
        }
        var didMutate = loadedFromLegacy
        self.items = decoded.map { item in
            var copy = item
            if copy.status.isDownloading {
                copy.status = .failed(message: "下载已中断，点击重试")
                didMutate = true
            }
            return copy
        }
        if didMutate {
            saveToDisk()
        }
    }

    private func saveToDisk() {
        guard let data = try? JSONEncoder().encode(items) else { return }
        try? data.write(to: metaFileURL, options: .atomic)
    }
}

/// Loopback HTTP server for local HLS playlists (`local_index.m3u8` + `.ts`/`.m4s`/`.key`), lazy-started with per-process path token.
final class OfflineLocalHTTPServer: @unchecked Sendable {
    private let searchRoots: [URL]
    let secretToken: String
    private let queue = DispatchQueue(label: "com.aurorashelf.offline-http-server")
    private let lock = NSLock()
    private var listener: NWListener?
    private var port: UInt16?
    private var waiters: [CheckedContinuation<UInt16?, Never>] = []

    init(searchRoots: [URL], secretToken: String = UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased()) {
        self.searchRoots = searchRoots
        self.secretToken = secretToken
    }

    func start() {
        queue.async { [weak self] in
            self?.ensureListenerLocked()
        }
    }

    func urlIfReady(forRelativePath relativePath: String) -> URL? {
        lock.lock()
        let activePort = port
        lock.unlock()
        guard let activePort else { return nil }
        return buildURL(port: activePort, relativePath: relativePath)
    }

    func playbackURL(forRelativePath relativePath: String) async -> URL? {
        if let ready = urlIfReady(forRelativePath: relativePath) {
            return ready
        }
        start()
        let boundPort = await withCheckedContinuation { (cont: CheckedContinuation<UInt16?, Never>) in
            lock.lock()
            if let activePort = port {
                lock.unlock()
                cont.resume(returning: activePort)
                return
            }
            waiters.append(cont)
            lock.unlock()

            queue.asyncAfter(deadline: .now() + 2.0) { [weak self] in
                guard let self else { return }
                self.lock.lock()
                if self.port == nil, !self.waiters.isEmpty {
                    let pending = self.waiters
                    self.waiters.removeAll()
                    self.lock.unlock()
                    pending.forEach { $0.resume(returning: nil) }
                } else {
                    self.lock.unlock()
                }
            }
        }
        guard let boundPort else { return nil }
        return buildURL(port: boundPort, relativePath: relativePath)
    }

    private func buildURL(port: UInt16, relativePath: String) -> URL? {
        let clean = relativePath.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let encoded = clean.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? clean
        return URL(string: "http://127.0.0.1:\(port)/offline/\(secretToken)/\(encoded)")
    }

    static func parseByteRange(headerValue: String, totalLength: Int64) -> ClosedRange<Int64>? {
        guard totalLength > 0 else { return nil }
        let lower = headerValue.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let rawSpec: Substring
        if lower.hasPrefix("range: bytes=") {
            rawSpec = lower.dropFirst("range: bytes=".count)
        } else if lower.hasPrefix("bytes=") {
            rawSpec = lower.dropFirst("bytes=".count)
        } else {
            return nil
        }
        let spec = rawSpec.trimmingCharacters(in: .whitespaces).split(separator: ",").first.map(String.init) ?? ""
        let bounds = spec.split(separator: "-", omittingEmptySubsequences: false).map(String.init)
        guard bounds.count == 2 else { return nil }

        if bounds[0].isEmpty {
            // Suffix byte-range-spec: bytes=-N (last N bytes)
            guard let suffixLength = Int64(bounds[1]), suffixLength > 0 else { return nil }
            let start = max(Int64(0), totalLength - suffixLength)
            return start...(totalLength - 1)
        }

        guard let start = Int64(bounds[0]), start >= 0, start < totalLength else {
            return nil
        }
        let end = bounds[1].isEmpty ? (totalLength - 1) : (Int64(bounds[1]) ?? (totalLength - 1))
        let clampedEnd = min(end, totalLength - 1)
        guard start <= clampedEnd else { return nil }
        return start...clampedEnd
    }

    private func ensureListenerLocked() {
        guard listener == nil else { return }
        do {
            let params = NWParameters.tcp
            params.allowLocalEndpointReuse = true
            params.requiredLocalEndpoint = NWEndpoint.hostPort(host: .ipv4(.loopback), port: .any)
            let newListener = try NWListener(using: params)
            newListener.stateUpdateHandler = { [weak self] state in
                guard let self else { return }
                switch state {
                case .ready:
                    if let assignedPort = newListener.port?.rawValue {
                        self.lock.lock()
                        self.port = assignedPort
                        let pending = self.waiters
                        self.waiters.removeAll()
                        self.lock.unlock()
                        pending.forEach { $0.resume(returning: assignedPort) }
                    }
                case .failed, .cancelled:
                    self.lock.lock()
                    self.listener = nil
                    self.port = nil
                    let pending = self.waiters
                    self.waiters.removeAll()
                    self.lock.unlock()
                    pending.forEach { $0.resume(returning: nil) }
                default:
                    break
                }
            }
            newListener.newConnectionHandler = { [weak self] connection in
                self?.handleConnection(connection)
            }
            self.listener = newListener
            newListener.start(queue: queue)
        } catch {
            lock.lock()
            let pending = waiters
            waiters.removeAll()
            lock.unlock()
            pending.forEach { $0.resume(returning: nil) }
        }
    }

    private func handleConnection(_ connection: NWConnection) {
        connection.start(queue: queue)
        receiveRequest(on: connection, buffer: Data())
    }

    private func receiveRequest(on connection: NWConnection, buffer: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 16_384) { [weak self] data, _, isComplete, error in
            guard let self else {
                connection.cancel()
                return
            }
            var nextBuffer = buffer
            if let data { nextBuffer.append(data) }

            if let headerRange = nextBuffer.range(of: Data("\r\n\r\n".utf8)) {
                let headerData = nextBuffer[..<headerRange.lowerBound]
                let headerString = String(data: headerData, encoding: .utf8) ?? ""
                self.respond(to: headerString, on: connection)
                return
            }

            if isComplete || error != nil || nextBuffer.count > 65_536 {
                connection.cancel()
                return
            }
            self.receiveRequest(on: connection, buffer: nextBuffer)
        }
    }

    private func resolveLocalFile(segments: [String]) -> URL? {
        let fm = FileManager.default
        for root in searchRoots {
            var candidate = root
            for seg in segments {
                candidate.appendPathComponent(seg)
            }
            let rootStd = root.standardizedFileURL.path
            let candStd = candidate.standardizedFileURL.path
            guard candStd.hasPrefix(rootStd.hasSuffix("/") ? rootStd : rootStd + "/") else {
                continue
            }
            if fm.fileExists(atPath: candStd) {
                return candidate
            }
        }
        return nil
    }

    private func respond(to rawRequest: String, on connection: NWConnection) {
        let lines = rawRequest.components(separatedBy: "\r\n")
        guard let requestLine = lines.first else {
            sendSimple(status: "400 Bad Request", on: connection)
            return
        }
        let parts = requestLine.split(separator: " ")
        guard parts.count >= 2 else {
            sendSimple(status: "400 Bad Request", on: connection)
            return
        }
        let method = String(parts[0]).uppercased()
        guard method == "GET" || method == "HEAD" else {
            sendSimple(status: "405 Method Not Allowed", on: connection)
            return
        }

        let rawTarget = String(parts[1]).split(separator: "?").first.map(String.init) ?? ""
        let decodedPath = rawTarget.removingPercentEncoding ?? rawTarget
        let expectedPrefix = "/offline/\(secretToken)/"
        guard decodedPath.hasPrefix(expectedPrefix) else {
            sendSimple(status: "403 Forbidden", on: connection)
            return
        }

        let relative = String(decodedPath.dropFirst(expectedPrefix.count))
        let segments = relative.split(separator: "/").map(String.init)
        guard !segments.isEmpty, !segments.contains(".."), !segments.contains(".") else {
            sendSimple(status: "403 Forbidden", on: connection)
            return
        }

        guard let fileURL = resolveLocalFile(segments: segments),
              let attrs = try? FileManager.default.attributesOfItem(atPath: fileURL.path),
              let totalLength = (attrs[.size] as? NSNumber)?.int64Value,
              totalLength >= 0 else {
            sendSimple(status: "404 Not Found", on: connection)
            return
        }

        var byteRange: ClosedRange<Int64>?
        var hadRangeHeader = false
        for line in lines.dropFirst() {
            if line.lowercased().hasPrefix("range:") {
                hadRangeHeader = true
                byteRange = Self.parseByteRange(headerValue: line, totalLength: totalLength)
                break
            }
        }
        if hadRangeHeader && byteRange == nil && totalLength > 0 {
            let raw = "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */\(totalLength)\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
            connection.send(content: Data(raw.utf8), completion: .contentProcessed { _ in
                connection.cancel()
            })
            return
        }

        let contentType: String = {
            switch fileURL.pathExtension.lowercased() {
            case "m3u8", "m3u": return "application/vnd.apple.mpegurl"
            case "ts": return "video/mp2t"
            case "m4s", "cmfv", "cmfa": return "video/iso.segment"
            case "mp4", "m4v": return "video/mp4"
            case "aac": return "audio/aac"
            case "mp3": return "audio/mpeg"
            case "key": return "application/octet-stream"
            default: return "application/octet-stream"
            }
        }()

        let startOffset = byteRange?.lowerBound ?? 0
        let endOffset = byteRange?.upperBound ?? max(0, totalLength - 1)
        let responseLength = totalLength == 0 ? 0 : (endOffset - startOffset + 1)

        var header = byteRange != nil
            ? "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes \(startOffset)-\(endOffset)/\(totalLength)\r\n"
            : "HTTP/1.1 200 OK\r\n"
        header += "Content-Type: \(contentType)\r\n"
        header += "Content-Length: \(responseLength)\r\n"
        header += "Accept-Ranges: bytes\r\n"
        header += "Connection: close\r\n\r\n"

        let headerData = Data(header.utf8)
        guard method == "GET" && responseLength > 0 else {
            connection.send(content: headerData, completion: .contentProcessed { _ in
                connection.cancel()
            })
            return
        }
        guard let handle = try? FileHandle(forReadingFrom: fileURL) else {
            sendSimple(status: "500 Internal Server Error", on: connection)
            return
        }
        try? handle.seek(toOffset: UInt64(startOffset))

        connection.send(content: headerData, completion: .contentProcessed { [weak self] error in
            if error != nil {
                try? handle.close()
                connection.cancel()
            } else if let self {
                self.streamChunks(from: handle, remaining: Int(responseLength), on: connection)
            } else {
                try? handle.close()
                connection.cancel()
            }
        })
    }

    private func streamChunks(from handle: FileHandle, remaining: Int, on connection: NWConnection) {
        if remaining <= 0 {
            try? handle.close()
            connection.cancel()
            return
        }
        let chunkSize = min(remaining, 262_144)
        guard let chunk = try? handle.read(upToCount: chunkSize), !chunk.isEmpty else {
            try? handle.close()
            connection.cancel()
            return
        }
        let nextRemaining = remaining - chunk.count
        connection.send(content: chunk, completion: .contentProcessed { [weak self] error in
            if error != nil || nextRemaining <= 0 {
                try? handle.close()
                connection.cancel()
            } else if let self {
                self.streamChunks(from: handle, remaining: nextRemaining, on: connection)
            } else {
                try? handle.close()
                connection.cancel()
            }
        })
    }

    private func sendSimple(status: String, on connection: NWConnection) {
        let raw = "HTTP/1.1 \(status)\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        connection.send(content: Data(raw.utf8), completion: .contentProcessed { _ in
            connection.cancel()
        })
    }
}
