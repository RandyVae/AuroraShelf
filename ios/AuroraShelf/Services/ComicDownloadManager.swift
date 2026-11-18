import Combine
import CryptoKit
import Foundation
import UIKit

/// 漫画离线下载状态枚举
public enum ComicDownloadStatus: Codable, Sendable, Equatable {
    case queued
    case resolving
    case downloading(progress: Double, completedPages: Int, totalPages: Int)
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

/// 已下载的漫画章节记录
public struct DownloadedComicChapter: Identifiable, Codable, Sendable, Equatable {
    public let id: String
    public let comicId: String
    public let sourceId: String
    public let comicTitle: String
    public let chapterId: String
    public let chapterTitle: String
    public let coverUrl: String
    public var totalPages: Int
    public var downloadedPages: Int
    public var totalBytes: Int64
    public var status: ComicDownloadStatus
    public let createdAt: Date

    public var displayTitle: String {
        "\(comicTitle) · \(chapterTitle)"
    }
}

/// 漫画离线下载中枢（Apple HIG 规范：沙盒落盘解密存储、FIFO 挂起队列、跨源隔离与 iCloud 备份排除）
@MainActor
public final class ComicDownloadManager: ObservableObject {
    public static let shared = ComicDownloadManager()

    @Published public private(set) var chapters: [DownloadedComicChapter] = []

    private let fileManager = FileManager.default
    let baseDir: URL
    private let metaFileURL: URL
    private var inFlightTasks: [String: (id: UUID, task: Task<Void, Never>)] = [:]
    private var activeDownloadCount = 0
    private let maximumConcurrentDownloads = 2
    private var slotWaiters: [(id: UUID, continuation: CheckedContinuation<Void, Error>)] = []

    private convenience init() {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        self.init(baseDirectory: docs.appendingPathComponent("OfflineComics", isDirectory: true))
    }

    init(baseDirectory: URL) {
        self.baseDir = baseDirectory
        self.metaFileURL = baseDir.appendingPathComponent("comic_metadata.json")
        Self.prepareExcludedDirectory(baseDir, fileManager: fileManager)
        loadFromDisk()
    }

    private static func prepareExcludedDirectory(_ directory: URL, fileManager: FileManager) {
        if !fileManager.fileExists(atPath: directory.path) {
            try? fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        }
        var dirURL = directory
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? dirURL.setResourceValues(values)
    }

    /// 统计已下载漫画总磁盘占用（字节数）
    public var totalDownloadedBytes: Int64 {
        chapters.filter { $0.status.isCompleted }.reduce(Int64(0)) { $0 + $1.totalBytes }
    }

    /// 统计已下载漫画总磁盘占用（本地化格式字符串）
    public func formattedTotalSpace() -> String {
        let total = chapters.reduce(Int64(0)) { $0 + $1.totalBytes }
        return ByteCountFormatter.string(fromByteCount: total, countStyle: .file)
    }

    public var activeChapters: [DownloadedComicChapter] {
        chapters.filter { $0.status.isDownloading }
    }

    public var completedChapters: [DownloadedComicChapter] {
        chapters.filter { $0.status.isCompleted }
    }

    public var failedChapters: [DownloadedComicChapter] {
        chapters.filter {
            if case .failed = $0.status { return true }
            return false
        }
    }

    public var overallProgress: Double {
        let active = activeChapters
        if !active.isEmpty {
            let sum = active.reduce(0.0) { $0 + $1.status.progressValue }
            return max(0.0, min(1.0, sum / Double(active.count)))
        }
        return completedChapters.isEmpty ? 0.0 : 1.0
    }

    private func findChapter(sourceId: String, comicId: String, chapterId: String) -> DownloadedComicChapter? {
        if !sourceId.isEmpty {
            let scopedID = generateID(sourceId: sourceId, comicId: comicId, chapterId: chapterId)
            if let hit = chapters.first(where: { $0.id == scopedID }) {
                return hit
            }
            if let hit = chapters.first(where: { $0.sourceId == sourceId && $0.comicId == comicId && $0.chapterId == chapterId }) {
                return hit
            }
        }
        let legacyID = legacyGenerateID(comicId: comicId, chapterId: chapterId)
        if let hit = chapters.first(where: { $0.id == legacyID }) {
            if sourceId.isEmpty || hit.sourceId.isEmpty || hit.sourceId == sourceId {
                return hit
            }
        }
        if sourceId.isEmpty {
            return chapters.first(where: { $0.comicId == comicId && $0.chapterId == chapterId })
        }
        return nil
    }

    /// 查询某章节当前下载状态（支持传入 sourceId，兼容旧版无 sourceId 索引）
    public func status(for comicId: String, chapterId: String, sourceId: String = "") -> ComicDownloadStatus? {
        findChapter(sourceId: sourceId, comicId: comicId, chapterId: chapterId)?.status
    }

    /// 判断某章节是否已完成离线下载
    public func isDownloaded(comicId: String, chapterId: String, sourceId: String = "") -> Bool {
        status(for: comicId, chapterId: chapterId, sourceId: sourceId)?.isCompleted ?? false
    }

    /// 获取离线页面本地缓存文件 URL（优先读取包含 sourceId 的隔离路径，回退旧版路径）
    public func localPageURL(comicId: String, chapterId: String, pageIndex: Int, sourceId: String = "") -> URL? {
        let resolvedSourceId = !sourceId.isEmpty
            ? sourceId
            : (findChapter(sourceId: "", comicId: comicId, chapterId: chapterId)?.sourceId ?? "")

        if !resolvedSourceId.isEmpty {
            let scopedURL = baseDir
                .appendingPathComponent(sanitize(resolvedSourceId))
                .appendingPathComponent(sanitize(comicId))
                .appendingPathComponent(sanitize(chapterId))
                .appendingPathComponent("page_\(pageIndex).jpg")
            if fileManager.fileExists(atPath: scopedURL.path) {
                return scopedURL
            }
        }

        let legacyPrimaryURL = baseDir
            .appendingPathComponent(sanitize(comicId))
            .appendingPathComponent(sanitize(chapterId))
            .appendingPathComponent("page_\(pageIndex).jpg")
        if fileManager.fileExists(atPath: legacyPrimaryURL.path) {
            return legacyPrimaryURL
        }

        let legacyEncodedURL = baseDir
            .appendingPathComponent(legacySanitize(comicId))
            .appendingPathComponent(legacySanitize(chapterId))
            .appendingPathComponent("page_\(pageIndex).jpg")
        return fileManager.fileExists(atPath: legacyEncodedURL.path) ? legacyEncodedURL : nil
    }

    /// 从本地磁盘直接加载已解密的离线页面图片
    public func localImage(comicId: String, chapterId: String, pageIndex: Int, sourceId: String = "") async -> UIImage? {
        guard let url = localPageURL(comicId: comicId, chapterId: chapterId, pageIndex: pageIndex, sourceId: sourceId) else {
            return nil
        }
        return await Task.detached(priority: .utility) {
            guard let data = try? Data(contentsOf: url) else { return nil }
            return UIImage(data: data)
        }.value
    }

    /// 启动单章节下载
    public func startDownload(comic: ComicItem, chapter: ComicChapter) {
        let itemId = generateID(sourceId: comic.sourceId, comicId: comic.id, chapterId: chapter.id)

        if let existing = findChapter(sourceId: comic.sourceId, comicId: comic.id, chapterId: chapter.id),
           existing.status.isCompleted || existing.status.isDownloading {
            return
        }

        if let idx = chapters.firstIndex(where: { $0.id == itemId }) {
            chapters[idx].status = .queued
        } else {
            let record = DownloadedComicChapter(
                id: itemId,
                comicId: comic.id,
                sourceId: comic.sourceId,
                comicTitle: comic.title,
                chapterId: chapter.id,
                chapterTitle: chapter.title,
                coverUrl: comic.coverUrl,
                totalPages: chapter.pageCount,
                downloadedPages: 0,
                totalBytes: 0,
                status: .queued,
                createdAt: Date()
            )
            chapters.insert(record, at: 0)
        }
        saveToDisk()

        inFlightTasks[itemId]?.task.cancel()
        let taskToken = UUID()
        let task = Task<Void, Never> { [weak self] in
            guard let self else { return }
            await self.executeDownload(itemId: itemId, taskToken: taskToken, comic: comic, chapter: chapter)
        }
        inFlightTasks[itemId] = (id: taskToken, task: task)
    }

    /// 启动整本漫画批量下载
    public func startDownloadAll(comic: ComicItem) {
        for chapter in comic.chapters {
            startDownload(comic: comic, chapter: chapter)
        }
    }

    /// 重试指定章节下载
    public func retryDownload(id: String) {
        guard let item = chapters.first(where: { $0.id == id }),
              !item.status.isCompleted,
              !item.status.isDownloading else { return }
        let chapter = ComicChapter(
            id: item.chapterId,
            title: item.chapterTitle,
            pageCount: item.totalPages
        )
        let comic = ComicItem(
            id: item.comicId,
            sourceId: item.sourceId,
            title: item.comicTitle,
            author: "",
            coverUrl: item.coverUrl,
            chapters: [chapter]
        )
        startDownload(comic: comic, chapter: chapter)
    }

    /// 删除指定章节下载（同时兼容新版含 sourceId 的 ID 与旧版 comicId_chapterId）
    public func removeDownload(id: String) {
        let matched = chapters.first { item in
            item.id == id || legacyGenerateID(comicId: item.comicId, chapterId: item.chapterId) == id
        }
        let targetId = matched?.id ?? id
        inFlightTasks[targetId]?.task.cancel()
        inFlightTasks[targetId] = nil

        if let item = matched {
            var candidateDirs: [URL] = [
                baseDir.appendingPathComponent(sanitize(item.comicId)).appendingPathComponent(sanitize(item.chapterId)),
                baseDir.appendingPathComponent(legacySanitize(item.comicId)).appendingPathComponent(legacySanitize(item.chapterId))
            ]
            if !item.sourceId.isEmpty {
                candidateDirs.insert(
                    baseDir
                        .appendingPathComponent(sanitize(item.sourceId))
                        .appendingPathComponent(sanitize(item.comicId))
                        .appendingPathComponent(sanitize(item.chapterId)),
                    at: 0
                )
            }
            for dir in candidateDirs {
                try? fileManager.removeItem(at: dir)
            }
            chapters.removeAll(where: { $0.id == item.id })
        } else {
            chapters.removeAll(where: { $0.id == id })
        }
        saveToDisk()
    }

    /// 清空所有离线漫画
    public func removeAll() {
        for entry in inFlightTasks.values { entry.task.cancel() }
        inFlightTasks.removeAll()
        let pendingWaiters = slotWaiters
        slotWaiters.removeAll()
        for waiter in pendingWaiters {
            waiter.continuation.resume(throwing: CancellationError())
        }

        try? fileManager.removeItem(at: baseDir)
        Self.prepareExcludedDirectory(baseDir, fileManager: fileManager)
        chapters.removeAll()
        saveToDisk()
    }

    // MARK: - 核心下载任务调度（FIFO Continuation 队列，零轮询休眠）

    private func acquireDownloadSlot() async throws {
        try Task.checkCancellation()
        if activeDownloadCount < maximumConcurrentDownloads {
            activeDownloadCount += 1
            return
        }
        let waiterID = UUID()
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                if Task.isCancelled {
                    continuation.resume(throwing: CancellationError())
                    return
                }
                if activeDownloadCount < maximumConcurrentDownloads {
                    activeDownloadCount += 1
                    continuation.resume()
                    return
                }
                slotWaiters.append((id: waiterID, continuation: continuation))
            }
        } onCancel: {
            Task { @MainActor [weak self] in
                guard let self else { return }
                if let idx = self.slotWaiters.firstIndex(where: { $0.id == waiterID }) {
                    let removed = self.slotWaiters.remove(at: idx)
                    removed.continuation.resume(throwing: CancellationError())
                }
            }
        }
    }

    private func releaseDownloadSlot() {
        while !slotWaiters.isEmpty {
            let next = slotWaiters.removeFirst()
            next.continuation.resume()
            return
        }
        activeDownloadCount = max(0, activeDownloadCount - 1)
    }

    private func executeDownload(itemId: String, taskToken: UUID, comic: ComicItem, chapter: ComicChapter) async {
        do {
            try await acquireDownloadSlot()
        } catch {
            if inFlightTasks[itemId]?.id == taskToken {
                inFlightTasks[itemId] = nil
            }
            return
        }
        defer {
            releaseDownloadSlot()
            if inFlightTasks[itemId]?.id == taskToken {
                inFlightTasks[itemId] = nil
            }
        }
        updateItem(id: itemId) { $0.status = .resolving }

        do {
            let pages = try await ComicRepository.shared.fetchChapterPages(
                comicId: comic.id,
                sourceId: comic.sourceId,
                chapterId: chapter.id
            )

            guard !pages.isEmpty else {
                throw NSError(domain: "ComicDownloadManager", code: -1, userInfo: [NSLocalizedDescriptionKey: "章节页面解析为空"])
            }

            let chapterDir: URL
            if !comic.sourceId.isEmpty {
                chapterDir = baseDir
                    .appendingPathComponent(sanitize(comic.sourceId), isDirectory: true)
                    .appendingPathComponent(sanitize(comic.id), isDirectory: true)
                    .appendingPathComponent(sanitize(chapter.id), isDirectory: true)
            } else {
                chapterDir = baseDir
                    .appendingPathComponent(sanitize(comic.id), isDirectory: true)
                    .appendingPathComponent(sanitize(chapter.id), isDirectory: true)
            }
            try? fileManager.createDirectory(at: chapterDir, withIntermediateDirectories: true)

            updateItem(id: itemId) {
                $0.totalPages = pages.count
                $0.status = .downloading(progress: 0.0, completedPages: 0, totalPages: pages.count)
            }

            var totalBytes: Int64 = 0
            for (idx, page) in pages.enumerated() {
                try Task.checkCancellation()

                let pageFileURL = chapterDir.appendingPathComponent("page_\(idx).jpg")
                if !fileManager.fileExists(atPath: pageFileURL.path) {
                    let image = try await ComicPageImageLoader.shared.load(page)
                    let writtenBytes = try await Task.detached(priority: .utility) { () -> Int64 in
                        try Task.checkCancellation()
                        guard let jpegData = image.jpegData(compressionQuality: 0.88) else {
                            throw NSError(domain: "ComicDownloadManager", code: -2, userInfo: [NSLocalizedDescriptionKey: "图片转码 JPEG 失败"])
                        }
                        try jpegData.write(to: pageFileURL, options: .atomic)
                        return Int64(jpegData.count)
                    }.value
                    totalBytes += writtenBytes
                } else {
                    let attrs = try? fileManager.attributesOfItem(atPath: pageFileURL.path)
                    totalBytes += (attrs?[.size] as? Int64) ?? 0
                }

                let completed = idx + 1
                let progress = Double(completed) / Double(pages.count)
                updateItem(id: itemId) {
                    $0.downloadedPages = completed
                    $0.totalBytes = totalBytes
                    $0.status = .downloading(progress: progress, completedPages: completed, totalPages: pages.count)
                }
            }

            updateItem(id: itemId) {
                $0.downloadedPages = pages.count
                $0.totalBytes = totalBytes
                $0.status = .completed
            }
            saveToDisk()
        } catch {
            if Task.isCancelled { return }
            updateItem(id: itemId) {
                $0.status = .failed(message: error.localizedDescription)
            }
            saveToDisk()
        }
    }

    private func updateItem(id: String, mutate: (inout DownloadedComicChapter) -> Void) {
        if let idx = chapters.firstIndex(where: { $0.id == id }) {
            var item = chapters[idx]
            mutate(&item)
            chapters[idx] = item
        }
    }

    func generateID(sourceId: String, comicId: String, chapterId: String) -> String {
        if sourceId.isEmpty {
            return legacyGenerateID(comicId: comicId, chapterId: chapterId)
        }
        return "\(sourceId)_\(comicId)_\(chapterId)"
    }

    private func legacyGenerateID(comicId: String, chapterId: String) -> String {
        "\(comicId)_\(chapterId)"
    }

    private func legacySanitize(_ str: String) -> String {
        str.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? str
    }

    private func sanitize(_ str: String) -> String {
        let encoded = legacySanitize(str)
        if encoded.count <= 80 {
            return encoded
        }
        let digest = SHA256.hash(data: Data(str.utf8)).prefix(8).map { String(format: "%02x", $0) }.joined()
        return "\(encoded.prefix(48))_\(digest)"
    }

    private func loadFromDisk() {
        guard fileManager.fileExists(atPath: metaFileURL.path),
              let data = try? Data(contentsOf: metaFileURL),
              let decoded = try? JSONDecoder().decode([DownloadedComicChapter].self, from: data) else {
            return
        }
        var needsPersist = false
        self.chapters = decoded.map { item in
            var copy = item
            if copy.status.isDownloading {
                copy.status = .failed(message: "下载已中断，点击重试")
                needsPersist = true
            }
            return copy
        }
        if needsPersist {
            saveToDisk()
        }
    }

    private func saveToDisk() {
        guard let data = try? JSONEncoder().encode(chapters) else { return }
        try? data.write(to: metaFileURL, options: .atomic)
    }
}
