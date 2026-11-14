import Foundation
import Combine
import AVFoundation
import Network

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
    private let localServer: OfflineLocalHTTPServer
    private var activeTasks: [String: Task<Void, Never>] = [:]
    
    private init() {
        let docs = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        self.baseDirectory = docs.appendingPathComponent("OfflineVideos", isDirectory: true)
        self.metaFileURL = docs.appendingPathComponent("offline_downloads.json")
        self.localServer = OfflineLocalHTTPServer(baseDirectory: self.baseDirectory)
        try? fileManager.createDirectory(at: baseDirectory, withIntermediateDirectories: true)
        self.localServer.start()
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
    
    private func completedItem(for video: VideoItem, episode: PlaybackEpisode? = nil) -> OfflineDownloadItem? {
        let itemId = generateID(video: video, episode: episode)
        if let direct = items.first(where: { $0.id == itemId && $0.status.isCompleted }) {
            return direct
        }
        if episode == nil {
            return items.first(where: { $0.status.isCompleted && $0.pageUrl == video.playbackUrl })
        }
        return nil
    }

    public func playableLocalURL(for video: VideoItem, episode: PlaybackEpisode? = nil) async -> URL? {
        guard let item = completedItem(for: video, episode: episode) else { return nil }
        return await playableLocalURL(for: item)
    }

    public func playableLocalURL(for item: OfflineDownloadItem) async -> URL? {
        guard item.status.isCompleted else { return nil }
        let fullPath = baseDirectory.appendingPathComponent(item.localRelativePath)
        guard fileManager.fileExists(atPath: fullPath.path) else { return nil }
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
        let fullPath = baseDirectory.appendingPathComponent(item.localRelativePath)
        if fileManager.fileExists(atPath: fullPath.path) {
            if item.localRelativePath.lowercased().hasSuffix(".m3u8"),
               let loopbackURL = localServer.urlIfReady(forRelativePath: item.localRelativePath) {
                return loopbackURL
            }
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
    
    private func downloadHLS(itemId: String, streamURL: URL, referer: String?, targetDir: URL) async throws {
        var playlistReq = URLRequest(url: streamURL)
        if let ref = referer, let refURL = URL(string: ref) {
            playlistReq.setValue(refURL.absoluteString, forHTTPHeaderField: "Referer")
        }
        playlistReq.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)", forHTTPHeaderField: "User-Agent")
        
        let (playlistData, playlistResponse) = try await URLSession.permissiveShared.data(for: playlistReq)
        try Self.requireSuccessfulMediaResponse(playlistResponse)
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
                let (varData, variantResponse) = try await URLSession.permissiveShared.data(for: varReq)
                try Self.requireSuccessfulMediaResponse(variantResponse)
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
        
        var downloadedBytes: Int64 = 0
        let targetSegments = segmentOriginalURLs
        
        for (i, segURL) in targetSegments.enumerated() {
            try Task.checkCancellation()
            
            var segReq = URLRequest(url: segURL)
            if let ref = referer { segReq.setValue(ref, forHTTPHeaderField: "Referer") }
            segReq.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)", forHTTPHeaderField: "User-Agent")
            
            let localSegURL = targetDir.appendingPathComponent("segment_\(i).ts")
            if !fileManager.fileExists(atPath: localSegURL.path) {
                let (data, response) = try await URLSession.permissiveShared.data(for: segReq)
                try Task.checkCancellation()
                try Self.requireSuccessfulMediaResponse(response)
                guard !data.isEmpty else {
                    throw NSError(domain: "OfflineDownload", code: -3, userInfo: [NSLocalizedDescriptionKey: "切片为空"])
                }
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
        
        try Task.checkCancellation()
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
        let (tempURL, response) = try await URLSession.permissiveShared.download(for: req)
        try Task.checkCancellation()
        try Self.requireSuccessfulMediaResponse(response)
        
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

    private static func requireSuccessfulMediaResponse(_ response: URLResponse) throws {
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
    
    private func generateID(video: VideoItem, episode: PlaybackEpisode?) -> String {
        let stripped = video.id.replacingOccurrences(of: #"-ep-\d+$"#, with: "", options: .regularExpression)
        let base = stripped.replacingOccurrences(of: "hua-", with: "vod-")
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
        var didRecoverInterrupted = false
        self.items = decoded.map { item in
            var copy = item
            if copy.status.isDownloading {
                copy.status = .failed(message: "下载已中断，点击重试")
                didRecoverInterrupted = true
            }
            return copy
        }
        if didRecoverInterrupted {
            saveToDisk()
        }
    }
    
    private func saveToDisk() {
        guard let data = try? JSONEncoder().encode(items) else { return }
        try? data.write(to: metaFileURL)
    }
}

/// Loopback HTTP server for local HLS playlists (`local_index.m3u8` + `.ts`), since AVURLAsset rejects `file://` M3U8.
private final class OfflineLocalHTTPServer: @unchecked Sendable {
    private let baseDirectory: URL
    private let queue = DispatchQueue(label: "com.aurorashelf.offline-http-server")
    private let lock = NSLock()
    private var listener: NWListener?
    private var port: UInt16?
    private var waiters: [CheckedContinuation<UInt16?, Never>] = []

    init(baseDirectory: URL) {
        self.baseDirectory = baseDirectory
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
        return URL(string: "http://127.0.0.1:\(port)/offline/\(encoded)")
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
        guard decodedPath.hasPrefix("/offline/") else {
            sendSimple(status: "404 Not Found", on: connection)
            return
        }

        let relative = String(decodedPath.dropFirst("/offline/".count))
        let segments = relative.split(separator: "/").map(String.init)
        guard !segments.isEmpty, !segments.contains(".."), !segments.contains(".") else {
            sendSimple(status: "403 Forbidden", on: connection)
            return
        }

        var fileURL = baseDirectory
        for seg in segments {
            fileURL.appendPathComponent(seg)
        }

        guard let attrs = try? FileManager.default.attributesOfItem(atPath: fileURL.path),
              let totalLength = (attrs[.size] as? NSNumber)?.int64Value,
              totalLength >= 0 else {
            sendSimple(status: "404 Not Found", on: connection)
            return
        }

        var byteRange: ClosedRange<Int64>? = nil
        for line in lines.dropFirst() {
            let lower = line.lowercased()
            if lower.hasPrefix("range: bytes=") {
                let spec = lower.dropFirst("range: bytes=".count)
                    .trimmingCharacters(in: .whitespaces)
                    .split(separator: ",").first.map(String.init) ?? ""
                let bounds = spec.split(separator: "-", omittingEmptySubsequences: false).map(String.init)
                if bounds.count == 2 {
                    if let start = Int64(bounds[0]) {
                        let end = Int64(bounds[1]) ?? (totalLength - 1)
                        let clampedEnd = min(max(start, end), max(0, totalLength - 1))
                        if start >= 0 && start < totalLength && start <= clampedEnd {
                            byteRange = start...clampedEnd
                        }
                    }
                }
            }
        }

        let contentType: String = {
            switch fileURL.pathExtension.lowercased() {
            case "m3u8": return "application/vnd.apple.mpegurl"
            case "ts": return "video/mp2t"
            case "mp4": return "video/mp4"
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

        var payload = Data(header.utf8)
        if method == "GET" && responseLength > 0 {
            guard let handle = try? FileHandle(forReadingFrom: fileURL) else {
                sendSimple(status: "500 Internal Server Error", on: connection)
                return
            }
            defer { try? handle.close() }
            try? handle.seek(toOffset: UInt64(startOffset))
            var remaining = Int(responseLength)
            while remaining > 0 {
                let chunkSize = min(remaining, 262_144)
                guard let chunk = try? handle.read(upToCount: chunkSize), !chunk.isEmpty else { break }
                payload.append(chunk)
                remaining -= chunk.count
            }
        }

        connection.send(content: payload, completion: .contentProcessed { _ in
            connection.cancel()
        })
    }

    private func sendSimple(status: String, on connection: NWConnection) {
        let raw = "HTTP/1.1 \(status)\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        connection.send(content: Data(raw.utf8), completion: .contentProcessed { _ in
            connection.cancel()
        })
    }
}
