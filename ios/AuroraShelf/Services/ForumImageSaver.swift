import CryptoKit
import Foundation
import Photos

public enum ForumImageSaveError: LocalizedError {
    case invalidURL
    case downloadFailed
    case writeFailed(String)

    public var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "无效的图片地址"
        case .downloadFailed:
            return "图片下载失败，请检查网络连接"
        case .writeFailed(let detail):
            return "图片保存失败：\(detail)"
        }
    }
}

public struct ForumImageSaveOutcome: Sendable {
    public let localURL: URL
    public let savedToPhotoLibrary: Bool
}

@MainActor
public final class ForumImageSaver: ObservableObject {
    public static let shared = ForumImageSaver()

    @Published public private(set) var savedLocalURLs: [String: URL] = [:]
    @Published public private(set) var activeSavingURLs: Set<String> = []
    @Published public private(set) var batchProgress: (completed: Int, total: Int)?

    private let fileManager = FileManager.default
    private let saveDirectory: URL

    private init() {
        let docs = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        self.saveDirectory = docs.appendingPathComponent("ForumSavedImages", isDirectory: true)
        try? fileManager.createDirectory(at: saveDirectory, withIntermediateDirectories: true)
        restoreExistingFiles()
    }

    private func restoreExistingFiles() {
        guard let mapData = try? Data(contentsOf: indexFileURL),
              let map = try? JSONDecoder().decode([String: String].self, from: mapData) else {
            return
        }
        var restored: [String: URL] = [:]
        for (remote, filename) in map {
            let local = saveDirectory.appendingPathComponent(filename)
            if fileManager.fileExists(atPath: local.path) {
                restored[remote] = local
            }
        }
        savedLocalURLs = restored
    }

    private var indexFileURL: URL {
        saveDirectory.appendingPathComponent("saved_index.json")
    }

    private func persistIndex() {
        let map = savedLocalURLs.mapValues { $0.lastPathComponent }
        if let data = try? JSONEncoder().encode(map) {
            try? data.write(to: indexFileURL)
        }
    }

    public func isSaved(urlString: String) -> Bool {
        guard let local = savedLocalURLs[urlString] else { return false }
        return fileManager.fileExists(atPath: local.path)
    }

    public func saveImage(urlString: String, referer: String) async throws -> ForumImageSaveOutcome {
        guard let remoteURL = URL(string: urlString), !urlString.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw ForumImageSaveError.invalidURL
        }
        activeSavingURLs.insert(urlString)
        defer { activeSavingURLs.remove(urlString) }

        guard let data = await CoverImageLoader.shared.loadRawData(from: urlString, requestReferer: referer),
              !data.isEmpty else {
            throw ForumImageSaveError.downloadFailed
        }

        let ext = Self.fileExtension(for: data, fallbackURL: remoteURL)
        let localURL = Self.localDestinationURL(in: saveDirectory, urlString: urlString, fileExtension: ext)
        do {
            try data.write(to: localURL, options: .atomic)
        } catch {
            throw ForumImageSaveError.writeFailed(error.localizedDescription)
        }
        savedLocalURLs[urlString] = localURL
        persistIndex()

        let photoSaved = await Self.saveToPhotoLibraryIfAuthorized(data: data)
        return ForumImageSaveOutcome(localURL: localURL, savedToPhotoLibrary: photoSaved)
    }

    public func saveAllImages(
        _ urls: [String],
        referer: String
    ) async -> (succeeded: Int, total: Int, savedToPhotoLibraryCount: Int) {
        var uniqueUrls: [String] = []
        var seen = Set<String>()
        for url in urls {
            let trimmed = url.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty, seen.insert(trimmed).inserted {
                uniqueUrls.append(trimmed)
            }
        }
        guard !uniqueUrls.isEmpty else { return (0, 0, 0) }

        batchProgress = (completed: 0, total: uniqueUrls.count)
        defer { batchProgress = nil }

        var succeeded = 0
        var photoSavedCount = 0
        for (idx, url) in uniqueUrls.enumerated() {
            if let outcome = try? await saveImage(urlString: url, referer: referer) {
                succeeded += 1
                if outcome.savedToPhotoLibrary {
                    photoSavedCount += 1
                }
            }
            batchProgress = (completed: idx + 1, total: uniqueUrls.count)
        }
        return (succeeded, uniqueUrls.count, photoSavedCount)
    }

    nonisolated static func fileExtension(for data: Data, fallbackURL: URL) -> String {
        if data.count >= 4, data.prefix(4) == Data([0x47, 0x49, 0x46, 0x38]) {
            return "gif"
        }
        if data.count >= 8, data.prefix(8) == Data([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]) {
            return "png"
        }
        if data.count >= 12,
           data.prefix(4) == Data("RIFF".utf8),
           data.subdata(in: 8..<12) == Data("WEBP".utf8) {
            return "webp"
        }
        if data.count >= 2, data[0] == 0xFF, data[1] == 0xD8 {
            return "jpg"
        }
        let pathExt = fallbackURL.pathExtension.lowercased()
        if ["jpg", "jpeg", "png", "gif", "webp", "heic"].contains(pathExt) {
            return pathExt == "jpeg" ? "jpg" : pathExt
        }
        return "jpg"
    }

    nonisolated static func localDestinationURL(in directory: URL, urlString: String, fileExtension: String) -> URL {
        let digest = SHA256.hash(data: Data(urlString.utf8))
        let hash = digest.map { String(format: "%02x", $0) }.joined()
        return directory.appendingPathComponent("forum_\(hash).\(fileExtension)")
    }

    private static func saveToPhotoLibraryIfAuthorized(data: Data) async -> Bool {
        let currentStatus = PHPhotoLibrary.authorizationStatus(for: .addOnly)
        let status: PHAuthorizationStatus
        if currentStatus == .notDetermined {
            status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
        } else {
            status = currentStatus
        }
        guard status == .authorized || status == .limited else {
            return false
        }
        return await withCheckedContinuation { continuation in
            PHPhotoLibrary.shared().performChanges {
                let request = PHAssetCreationRequest.forAsset()
                request.addResource(with: .photo, data: data, options: nil)
            } completionHandler: { success, _ in
                continuation.resume(returning: success)
            }
        }
    }
}
