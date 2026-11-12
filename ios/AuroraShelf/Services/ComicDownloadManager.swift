import Foundation
import UIKit
import Combine

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

/// 漫画离线下载中枢（Apple HIG 规范：沙盒落盘解密存储、离线阅读优先拦截与磁盘管理）
@MainActor
public final class ComicDownloadManager: ObservableObject {
    public static let shared = ComicDownloadManager()

    @Published public private(set) var chapters: [DownloadedComicChapter] = []

    private let fileManager = FileManager.default
    private let baseDir: URL
    private let metaFileURL: URL
    private var inFlightTasks: [String: Task<Void, Never>] = [:]
    private var activeDownloadCount = 0
    private let maximumConcurrentDownloads = 2

    private init() {
        let docs = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        self.baseDir = docs.appendingPathComponent("OfflineComics", isDirectory: true)
        self.metaFileURL = baseDir.appendingPathComponent("comic_metadata.json")

        if !fileManager.fileExists(atPath: baseDir.path) {
            try? fileManager.createDirectory(at: baseDir, withIntermediateDirectories: true)
        }

        loadFromDisk()
    }

    /// 统计已下载漫画总磁盘占用
    public func formattedTotalSpace() -> String {
        let total = chapters.reduce(Int64(0)) { $0 + $1.totalBytes }
        return ByteCountFormatter.string(fromByteCount: total, countStyle: .file)
    }

    /// 查询某章节当前下载状态
    public func status(for comicId: String, chapterId: String) -> ComicDownloadStatus? {
        let id = generateID(comicId: comicId, chapterId: chapterId)
        return chapters.first(where: { $0.id == id })?.status
    }

    /// 判断某章节是否已完成离线下载
    public func isDownloaded(comicId: String, chapterId: String) -> Bool {
        status(for: comicId, chapterId: chapterId)?.isCompleted ?? false
    }

    /// 获取离线页面本地缓存文件 URL
    public func localPageURL(comicId: String, chapterId: String, pageIndex: Int) -> URL? {
        let sanitizedComic = sanitize(comicId)
        let sanitizedChapter = sanitize(chapterId)
        let fileURL = baseDir
            .appendingPathComponent(sanitizedComic)
            .appendingPathComponent(sanitizedChapter)
            .appendingPathComponent("page_\(pageIndex).jpg")
        return fileManager.fileExists(atPath: fileURL.path) ? fileURL : nil
    }

    /// 从本地磁盘直接加载已解密的离线页面图片
    public func localImage(comicId: String, chapterId: String, pageIndex: Int) async -> UIImage? {
        guard let url = localPageURL(comicId: comicId, chapterId: chapterId, pageIndex: pageIndex),
              let data = await Task.detached(priority: .utility, operation: {
                  try? Data(contentsOf: url)
              }).value,
              let image = UIImage(data: data) else {
            return nil
        }
        return image
    }

    /// 启动单章节下载
    public func startDownload(comic: ComicItem, chapter: ComicChapter) {
        let itemId = generateID(comicId: comic.id, chapterId: chapter.id)

        if let existing = chapters.first(where: { $0.id == itemId }), existing.status.isCompleted {
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

        inFlightTasks[itemId]?.cancel()
        inFlightTasks[itemId] = Task { [weak self] in
            await self?.executeDownload(itemId: itemId, comic: comic, chapter: chapter)
        }
    }

    /// 启动整本漫画批量下载
    public func startDownloadAll(comic: ComicItem) {
        for chapter in comic.chapters {
            startDownload(comic: comic, chapter: chapter)
        }
    }

    /// 删除指定章节下载
    public func removeDownload(id: String) {
        inFlightTasks[id]?.cancel()
        inFlightTasks[id] = nil

        if let item = chapters.first(where: { $0.id == id }) {
            let chapterDir = baseDir
                .appendingPathComponent(sanitize(item.comicId))
                .appendingPathComponent(sanitize(item.chapterId))
            try? fileManager.removeItem(at: chapterDir)
        }

        chapters.removeAll(where: { $0.id == id })
        saveToDisk()
    }

    /// 清空所有离线漫画
    public func removeAll() {
        for task in inFlightTasks.values { task.cancel() }
        inFlightTasks.removeAll()

        try? fileManager.removeItem(at: baseDir)
        try? fileManager.createDirectory(at: baseDir, withIntermediateDirectories: true)
        chapters.removeAll()
        saveToDisk()
    }

    // MARK: - 核心下载任务调度

    private func executeDownload(itemId: String, comic: ComicItem, chapter: ComicChapter) async {
        await waitForDownloadSlot()
        activeDownloadCount += 1
        defer { activeDownloadCount -= 1 }
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

            let chapterDir = baseDir
                .appendingPathComponent(sanitize(comic.id))
                .appendingPathComponent(sanitize(chapter.id))
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
                    // 使用 ComicPageImageLoader 下载并解密图片（处理 JM 混淆、EHentai 鉴权等）
                    let image = try await ComicPageImageLoader.shared.load(page)
                    guard let jpegData = image.jpegData(compressionQuality: 0.88) else {
                        throw NSError(domain: "ComicDownloadManager", code: -2, userInfo: [NSLocalizedDescriptionKey: "图片转码 JPEG 失败"])
                    }
                    try jpegData.write(to: pageFileURL)
                    totalBytes += Int64(jpegData.count)
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

    private func waitForDownloadSlot() async {
        while activeDownloadCount >= maximumConcurrentDownloads {
            try? await Task.sleep(for: .milliseconds(50))
        }
    }

    private func updateItem(id: String, mutate: (inout DownloadedComicChapter) -> Void) {
        if let idx = chapters.firstIndex(where: { $0.id == id }) {
            var item = chapters[idx]
            mutate(&item)
            chapters[idx] = item
        }
    }

    private func generateID(comicId: String, chapterId: String) -> String {
        "\(comicId)_\(chapterId)"
    }

    private func sanitize(_ str: String) -> String {
        str.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? str
    }

    private func loadFromDisk() {
        guard fileManager.fileExists(atPath: metaFileURL.path),
              let data = try? Data(contentsOf: metaFileURL),
              let decoded = try? JSONDecoder().decode([DownloadedComicChapter].self, from: data) else {
            return
        }
        self.chapters = decoded
    }

    private func saveToDisk() {
        guard let data = try? JSONEncoder().encode(chapters) else { return }
        try? data.write(to: metaFileURL)
    }
}
