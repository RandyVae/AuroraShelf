import Foundation
import Combine

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
        // 至少有效播放 5 秒以上
        return positionMs >= 5_000
    }
}

/// 播放进度持久化与 Up Next 状态机管理器
@MainActor
public final class PlaybackProgressStore: ObservableObject {
    public static let shared = PlaybackProgressStore()
    private static let storageKey = "netgrey.playback_progress.v1"

    @Published public private(set) var records: [PlaybackRecord] = []

    private let defaults: UserDefaults
    private var lastDiskPersistTime: Date = .distantPast
    private var pendingPersistTask: Task<Void, Never>?

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let data = defaults.data(forKey: Self.storageKey) {
            do {
                self.records = try JSONDecoder().decode([PlaybackRecord].self, from: data)
            } catch {
                self.records = []
            }
        }
    }

    /// Up Next 活跃待播清单（按最后更新时间降序）
    public var upNextRecords: [PlaybackRecord] {
        records
            .filter { $0.shouldAppearInUpNext }
            .sorted { $0.lastUpdated > $1.lastUpdated }
    }

    /// 获取特定视频的播放记录
    public func record(for video: VideoItem) -> PlaybackRecord? {
        records.first { $0.videoId == video.id && $0.sourceName == video.sourceName }
    }

    /// 实时更新播放进度（节流落盘，杜绝播放器高频 I/O 阻塞主线程）
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

        self.records = list
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
        flushPendingPersist(with: list)
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
            pendingPersistTask = Task { @MainActor in
                try? await Task.sleep(nanoseconds: 5_000_000_000) // 5 秒防抖窗口
                guard !Task.isCancelled else { return }
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
