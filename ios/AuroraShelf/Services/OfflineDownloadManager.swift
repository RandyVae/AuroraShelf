import Foundation
import Combine
import AVFoundation

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
    public let sourceName: String
    public let pageUrl: String
    public let coverUrl: String
    public var streamUrl: String
    public var localRelativePath: String
    public var totalBytes: Int64
    public var downloadedBytes: Int64
    public var status: OfflineDownloadStatus
    public let createdAt: Date
    
    public var displayTitle: String {
        if let ep = episodeLabel, !ep.isEmpty {
            return "\(title) · \(ep)"
        }
        return title
    }
    
    public func toVideoItem() -> VideoItem {
        VideoItem(
            id: videoId,
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

/// 全局离线视频缓存下载中枢（支持 HLS .m3u8 切片完整本地化下载与直链 MP4 离线硬解）
@MainActor
public final class OfflineDownloadManager: ObservableObject {
    public static let shared = OfflineDownloadManager()
    
    @Published public private(set) var items: [OfflineDownloadItem] = []
    
    private let fileManager = FileManager.default
    private let baseDirectory: URL
    private let metaFileURL: URL
    private var activeTasks: [String: Task<Void, Never>] = [:]
    
    private init() {
        let docs = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        self.baseDirectory = docs.appendingPathComponent("OfflineVideos", isDirectory: true)
        self.metaFileURL = docs.appendingPathComponent("offline_downloads.json")
        try? fileManager.createDirectory(at: baseDirectory, withIntermediateDirectories: true)
        loadFromDisk()
    }
    
    public var completedItems: [OfflineDownloadItem] {
        items.filter { $0.status.isCompleted }
    }
    
    public var totalDownloadedBytes: Int64 {
        items.filter { $0.status.isCompleted }.reduce(0) { $0 + $1.downloadedBytes }
    }
    
    public func formattedTotalSpace() -> String {
        ByteCountFormatter.string(fromByteCount: totalDownloadedBytes, countStyle: .file)
    }
    
    public func status(for video: VideoItem, episode: PlaybackEpisode? = nil) -> OfflineDownloadStatus? {
        let itemId = generateID(video: video, episode: episode)
        return items.first(where: { $0.id == itemId })?.status
    }
    
    public func isDownloaded(video: VideoItem, episode: PlaybackEpisode? = nil) -> Bool {
        let itemId = generateID(video: video, episode: episode)
        return items.first(where: { $0.id == itemId })?.status.isCompleted ?? false
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
    
    public func localPlaybackURL(for video: VideoItem, episode: PlaybackEpisode? = nil) -> URL? {
        let itemId = generateID(video: video, episode: episode)
        guard let item = items.first(where: { $0.id == itemId }), item.status.isCompleted else {
            return nil
        }
        let fullPath = baseDirectory.appendingPathComponent(item.localRelativePath)
        if fileManager.fileExists(atPath: fullPath.path) {
            return fullPath
        }
        return nil
    }
    
    public func localPlaybackURL(for item: OfflineDownloadItem) -> URL? {
        guard item.status.isCompleted else { return nil }
        let fullPath = baseDirectory.appendingPathComponent(item.localRelativePath)
        if fileManager.fileExists(atPath: fullPath.path) {
            return fullPath
        }
        return nil
    }
    
    public func startDownload(video: VideoItem, episode: PlaybackEpisode? = nil) {
        let itemId = generateID(video: video, episode: episode)
        let pageUrl = episode?.pageUrl ?? video.playbackUrl
        let epLabel = episode?.label
        
        // 如果已经在下载或已完成，则不重复加入
        if let existing = items.first(where: { $0.id == itemId }) {
            if existing.status.isCompleted || existing.status.isDownloading {
                return
            }
        }
        
        let newItem = OfflineDownloadItem(
            id: itemId,
            videoId: video.id,
            title: video.title,
            episodeLabel: epLabel,
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
        
        // 启动后台下载任务
        let task = Task { [weak self] in
            guard let self = self else { return }
            await self.executeDownload(itemId: itemId, video: video, pageUrl: pageUrl)
        }
        activeTasks[itemId] = task
    }
    
    public func removeDownload(id: String) {
        activeTasks[id]?.cancel()
        activeTasks.removeValue(forKey: id)
        
        if let idx = items.firstIndex(where: { $0.id == id }) {
            let item = items[idx]
            let itemDir = baseDirectory.appendingPathComponent(id)
            try? fileManager.removeItem(at: itemDir)
            items.remove(at: idx)
            saveToDisk()
        }
    }
    
    public func removeAll() {
        activeTasks.values.forEach { $0.cancel() }
        activeTasks.removeAll()
        try? fileManager.removeItem(at: baseDirectory)
        try? fileManager.createDirectory(at: baseDirectory, withIntermediateDirectories: true)
        items.removeAll()
        saveToDisk()
    }
    
    // MARK: - 内部下载执行流程
    
    private func executeDownload(itemId: String, video: VideoItem, pageUrl: String) async {
        let itemDir = baseDirectory.appendingPathComponent(itemId, isDirectory: true)
        try? fileManager.createDirectory(at: itemDir, withIntermediateDirectories: true)
        
        do {
            // 1. 解析真实流
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
            
            // 2. 根据媒体类型执行分片或直链下载
            if streamURL.pathExtension.lowercased() == "m3u8" || streamURL.absoluteString.contains(".m3u8") {
                try await downloadHLS(itemId: itemId, streamURL: streamURL, referer: sourceResult.referer, targetDir: itemDir)
            } else {
                try await downloadDirectFile(itemId: itemId, streamURL: streamURL, referer: sourceResult.referer, targetDir: itemDir)
            }
            
            activeTasks.removeValue(forKey: itemId)
        } catch {
            updateItem(id: itemId) { item in
                item.status = .failed(message: error.localizedDescription)
            }
            activeTasks.removeValue(forKey: itemId)
        }
    }
    
    private func downloadHLS(itemId: String, streamURL: URL, referer: String?, targetDir: URL) async throws {
        var playlistReq = URLRequest(url: streamURL)
        if let ref = referer, let refURL = URL(string: ref) {
            playlistReq.setValue(refURL.absoluteString, forHTTPHeaderField: "Referer")
        }
        playlistReq.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)", forHTTPHeaderField: "User-Agent")
        
        let (playlistData, _) = try await URLSession.permissive().data(for: playlistReq)
        guard let playlistText = String(data: playlistData, encoding: .utf8) else {
            throw NSError(domain: "OfflineDownload", code: -1, userInfo: [NSLocalizedDescriptionKey: "无法解析播放列表"])
        }
        
        // 检查是否为 Master Playlist（多码率变种列表）
        var finalMediaPlaylistURL = streamURL
        var mediaLines = playlistText.components(separatedBy: .newlines)
        
        if playlistText.contains("#EXT-X-STREAM-INF") {
            var candidateVariant: String?
            for (idx, line) in mediaLines.enumerated() {
                if line.hasPrefix("#EXT-X-STREAM-INF") && idx + 1 < mediaLines.count {
                    let nextLine = mediaLines[idx + 1].trimmingCharacters(in: .whitespacesAndNewlines)
                    if !nextLine.isEmpty && !nextLine.hasPrefix("#") {
                        candidateVariant = nextLine
                        break
                    }
                }
            }
            if let variant = candidateVariant, let resolvedURL = URL(string: variant, relativeTo: streamURL) {
                finalMediaPlaylistURL = resolvedURL
                var varReq = URLRequest(url: resolvedURL)
                if let ref = referer { varReq.setValue(ref, forHTTPHeaderField: "Referer") }
                let (varData, _) = try await URLSession.permissive().data(for: varReq)
                if let varText = String(data: varData, encoding: .utf8) {
                    mediaLines = varText.components(separatedBy: .newlines)
                }
            }
        }
        
        // 提取所有切片 URL
        var segmentOriginalURLs: [URL] = []
        var rewrittenLines: [String] = []
        var segmentIndex = 0
        
        for line in mediaLines {
            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.isEmpty { continue }
            if trimmed.hasPrefix("#") {
                rewrittenLines.append(trimmed)
            } else {
                if let segURL = URL(string: trimmed, relativeTo: finalMediaPlaylistURL) {
                    segmentOriginalURLs.append(segURL)
                    let localFileName = "segment_\(segmentIndex).ts"
                    rewrittenLines.append(localFileName)
                    segmentIndex += 1
                }
            }
        }
        
        guard !segmentOriginalURLs.isEmpty else {
            throw NSError(domain: "OfflineDownload", code: -2, userInfo: [NSLocalizedDescriptionKey: "未找到切片内容"])
        }
        
        let totalCount = segmentOriginalURLs.count
        var downloadedBytes: Int64 = 0
        
        // 限制最大缓存切片数量以防无限直播流（点播视频通常为 100~300 片）
        let targetSegments = Array(segmentOriginalURLs.prefix(360))
        
        for (i, segURL) in targetSegments.enumerated() {
            if Task.isCancelled { break }
            
            var segReq = URLRequest(url: segURL)
            if let ref = referer { segReq.setValue(ref, forHTTPHeaderField: "Referer") }
            segReq.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)", forHTTPHeaderField: "User-Agent")
            
            let localSegURL = targetDir.appendingPathComponent("segment_\(i).ts")
            if !fileManager.fileExists(atPath: localSegURL.path) {
                let (data, _) = try await URLSession.permissive().data(for: segReq)
                try data.write(to: localSegURL)
                downloadedBytes += Int64(data.count)
            } else {
                let attrs = try? fileManager.attributesOfItem(atPath: localSegURL.path)
                downloadedBytes += (attrs?[.size] as? Int64) ?? 0
            }
            
            let progress = Double(i + 1) / Double(targetSegments.count)
            updateItem(id: itemId) { item in
                item.downloadedBytes = downloadedBytes
                item.totalBytes = max(downloadedBytes, Int64(Double(downloadedBytes) / max(0.01, progress)))
                item.status = .downloading(progress: progress, bytesWritten: downloadedBytes, totalBytes: item.totalBytes)
            }
        }
        
        // 写出本地播放列表 local_index.m3u8
        let localPlaylistURL = targetDir.appendingPathComponent("local_index.m3u8")
        let localPlaylistText = rewrittenLines.joined(separator: "\n")
        try localPlaylistText.data(using: .utf8)?.write(to: localPlaylistURL)
        
        updateItem(id: itemId) { item in
            item.localRelativePath = "\(itemId)/local_index.m3u8"
            item.status = .completed
            item.downloadedBytes = downloadedBytes
        }
        saveToDisk()
    }
    
    private func downloadDirectFile(itemId: String, streamURL: URL, referer: String?, targetDir: URL) async throws {
        var req = URLRequest(url: streamURL)
        if let ref = referer { req.setValue(ref, forHTTPHeaderField: "Referer") }
        req.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)", forHTTPHeaderField: "User-Agent")
        
        let localFileURL = targetDir.appendingPathComponent("video.mp4")
        let (tempURL, response) = try await URLSession.permissive().download(for: req)
        
        if fileManager.fileExists(atPath: localFileURL.path) {
            try? fileManager.removeItem(at: localFileURL)
        }
        try fileManager.moveItem(at: tempURL, to: localFileURL)
        
        let size = (try? fileManager.attributesOfItem(atPath: localFileURL.path)[.size] as? Int64) ?? response.expectedContentLength
        
        updateItem(id: itemId) { item in
            item.localRelativePath = "\(itemId)/video.mp4"
            item.status = .completed
            item.downloadedBytes = size
            item.totalBytes = size
        }
        saveToDisk()
    }
    
    private func updateItem(id: String, mutate: (inout OfflineDownloadItem) -> Void) {
        if let idx = items.firstIndex(where: { $0.id == id }) {
            var item = items[idx]
            mutate(&item)
            items[idx] = item
        }
    }
    
    private func generateID(video: VideoItem, episode: PlaybackEpisode?) -> String {
        let base = video.id.replacingOccurrences(of: "hua-", with: "vod-")
        if let ep = episode {
            return "\(base)_ep\(ep.number)"
        }
        return base
    }
    
    private func loadFromDisk() {
        guard fileManager.fileExists(atPath: metaFileURL.path),
              let data = try? Data(contentsOf: metaFileURL),
              let decoded = try? JSONDecoder().decode([OfflineDownloadItem].self, from: data) else {
            return
        }
        self.items = decoded
    }
    
    private func saveToDisk() {
        guard let data = try? JSONEncoder().encode(items) else { return }
        try? data.write(to: metaFileURL)
    }
}
