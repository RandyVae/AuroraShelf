import Combine
import Foundation
import UIKit

/// 播放进度记录模型（对齐 Apple TV 待播清单 Up Next 状态机）
public struct PlaybackRecord: Codable, Identifiable, Equatable {
    public var id: String { "\(sourceName)_\(videoId)" }
    public let videoId: String
    public let sourceName: String
    public let video: VideoItem
    public var positionMs: Int64
    public var durationMs: Int64
    public var lastUpdated: Date
    public var isManuallyDismissed: Bool
    public var isManuallyCompleted: Bool

    public init(
        video: VideoItem,
        positionMs: Int64,
        durationMs: Int64,
        lastUpdated: Date = Date(),
        isManuallyDismissed: Bool = false,
        isManuallyCompleted: Bool = false
    ) {
        self.videoId = video.id
        self.sourceName = video.sourceName
        self.video = video
        self.positionMs = positionMs
        self.durationMs = durationMs
        self.lastUpdated = lastUpdated
        self.isManuallyDismissed = isManuallyDismissed
        self.isManuallyCompleted = isManuallyCompleted
    }

    /// 播放进度比例（0.0 ~ 1.0）
    public var progressRatio: Double {
        guard durationMs > 0 else { return 0.0 }
        let ratio = Double(positionMs) / Double(durationMs)
        return min(max(ratio, 0.0), 1.0)
    }

    /// 自动完结判定：播放超过 90% 判定为完成
    public var isAutoCompleted: Bool {
        progressRatio >= 0.90
    }

    /// 是否应出现在 Up Next 待播清单中
    public var shouldAppearInUpNext: Bool {
        guard !isManuallyDismissed && !isManuallyCompleted && !isAutoCompleted else {
            return false
        }
        return positionMs >= 5_000
    }
}

/// 播放进度持久化与 Up Next 状态机管理器
@MainActor
public final class PlaybackProgressStore: ObservableObject {
    public static let shared = PlaybackProgressStore()
    private static let storageKey = "netgrey.playback_progress.v1"
    private static let maxRecordCount = 200

    private struct EmptyDecodable: Decodable {}
    private struct LossyPlaybackRecordArray: Decodable {
        let items: [PlaybackRecord]
        init(from decoder: Decoder) throws {
            var container = try decoder.unkeyedContainer()
            var decoded: [PlaybackRecord] = []
            while !container.isAtEnd {
                if let item = try? container.decode(PlaybackRecord.self) {
                    decoded.append(item)
                } else {
                    _ = try? container.decode(EmptyDecodable.self)
                }
            }
            self.items = decoded
        }
    }

    @Published public private(set) var records: [PlaybackRecord] = [] {
        didSet {
            upNextRecords = Self.computeUpNext(from: records)
        }
    }

    /// 缓存化 Up Next 活跃待播清单（避免 SwiftUI 每次重绘对 200 条记录重复过滤排序）
    @Published public private(set) var upNextRecords: [PlaybackRecord] = []

    private let defaults: UserDefaults
    private var lastDiskPersistTime: Date = .distantPast
    private var pendingPersistTask: Task<Void, Never>?
    nonisolated(unsafe) private var lifecycleObservers: [NSObjectProtocol] = []

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let data = defaults.data(forKey: Self.storageKey) {
            if let decoded = try? JSONDecoder().decode(LossyPlaybackRecordArray.self, from: data) {
                let initial = Self.cappedRecords(decoded.items)
                self.records = initial
                self.upNextRecords = Self.computeUpNext(from: initial)
            } else {
                self.records = []
                self.upNextRecords = []
            }
        }

        let center = NotificationCenter.default
        let resignToken = center.addObserver(
            forName: UIApplication.willResignActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self, self.pendingPersistTask != nil else { return }
                self.flushPendingPersist()
            }
        }
        let terminateToken = center.addObserver(
            forName: UIApplication.willTerminateNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.flushPendingPersist()
            }
        }
        self.lifecycleObservers = [resignToken, terminateToken]
    }

    deinit {
        for observer in lifecycleObservers {
            NotificationCenter.default.removeObserver(observer)
        }
    }

    private static func computeUpNext(from items: [PlaybackRecord]) -> [PlaybackRecord] {
        items
            .filter { $0.shouldAppearInUpNext }
            .sorted { $0.lastUpdated > $1.lastUpdated }
    }

    /// 获取特定视频的播放记录
    public func record(for video: VideoItem) -> PlaybackRecord? {
        records.first { $0.videoId == video.id && $0.sourceName == video.sourceName }
    }

    /// 实时更新播放进度（节流落盘，切后台/终止时即刻冲刷）
    public func updateProgress(video: VideoItem, positionMs: Int64, durationMs: Int64) {
        guard durationMs > 0 else { return }

        var list = records
        if let index = list.firstIndex(where: { $0.videoId == video.id && $0.sourceName == video.sourceName }) {
            var existing = list[index]
            existing.positionMs = positionMs
            existing.durationMs = durationMs
            existing.lastUpdated = Date()
            existing.isManuallyDismissed = false
            existing.isManuallyCompleted = false
            list[index] = existing
        } else {
            let newRecord = PlaybackRecord(
                video: video,
                positionMs: positionMs,
                durationMs: durationMs
            )
            list.insert(newRecord, at: 0)
        }

        self.records = Self.cappedRecords(list)
        scheduleThrottledPersist()
    }

    /// 标记为已观看（即刻同步写盘）
    public func markCompleted(for video: VideoItem) {
        var list = records
        if let index = list.firstIndex(where: { $0.videoId == video.id && $0.sourceName == video.sourceName }) {
            list[index].isManuallyCompleted = true
            list[index].lastUpdated = Date()
        } else {
            let record = PlaybackRecord(
                video: video,
                positionMs: 0,
                durationMs: 0,
                isManuallyCompleted: true
            )
            list.insert(record, at: 0)
        }
        flushPendingPersist(with: Self.cappedRecords(list))
    }

    private static func cappedRecords(_ items: [PlaybackRecord]) -> [PlaybackRecord] {
        guard items.count > maxRecordCount else { return items }
        return Array(items.sorted { $0.lastUpdated > $1.lastUpdated }.prefix(maxRecordCount))
    }

    /// 从 Up Next 待播清单移除（即刻同步写盘）
    public func removeFromUpNext(for video: VideoItem) {
        var list = records
        if let index = list.firstIndex(where: { $0.videoId == video.id && $0.sourceName == video.sourceName }) {
            list[index].isManuallyDismissed = true
            list[index].lastUpdated = Date()
            flushPendingPersist(with: list)
        }
    }

    /// 清空所有播放历史
    public func clearAll() {
        flushPendingPersist(with: [])
    }

    /// 强制将内存中最新播放进度即刻落盘
    public func flushPendingPersist(with specificRecords: [PlaybackRecord]? = nil) {
        pendingPersistTask?.cancel()
        pendingPersistTask = nil
        let target = specificRecords ?? self.records
        self.records = target
        persistToDisk(target)
    }

    private func scheduleThrottledPersist() {
        let now = Date()
        if now.timeIntervalSince(lastDiskPersistTime) >= 10.0 {
            pendingPersistTask?.cancel()
            pendingPersistTask = nil
            persistToDisk(records)
        } else if pendingPersistTask == nil {
            pendingPersistTask = Task { @MainActor [weak self] in
                try? await Task.sleep(nanoseconds: 5_000_000_000)
                guard !Task.isCancelled, let self else { return }
                self.persistToDisk(self.records)
                self.pendingPersistTask = nil
            }
        }
    }

    private func persistToDisk(_ newRecords: [PlaybackRecord]) {
        lastDiskPersistTime = Date()
        if let data = try? JSONEncoder().encode(newRecords) {
            defaults.set(data, forKey: Self.storageKey)
        }
    }
}
