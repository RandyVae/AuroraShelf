import SwiftUI

/// Apple TV / iOS 设置风格的存储容量多段彩色仪表盘（Storage Breakdown Bar）
public struct StorageUsageBarView: View {
    @ObservedObject private var downloadManager = OfflineDownloadManager.shared
    @ObservedObject private var comicDownloadManager = ComicDownloadManager.shared
    
    @State private var freeBytes: Int64 = 0
    @State private var totalBytes: Int64 = 0
    @State private var cacheBytes: Int64 = 0
    @State private var extraOfflineBytes: Int64 = 0
    @State private var isClearingCache: Bool = false
    
    public init() {}
    
    private var downloadedBytes: Int64 {
        downloadManager.totalDownloadedBytes + comicDownloadManager.totalDownloadedBytes + extraOfflineBytes
    }
    
    public var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("本地存储空间")
                    .font(.subheadline.bold())
                    .foregroundStyle(AuroraColor.textPrimary)
                
                if cacheBytes > 0 {
                    Button {
                        let impact = UIImpactFeedbackGenerator(style: .medium)
                        impact.impactOccurred()
                        isClearingCache = true
                        Task {
                            await CoverImageLoader.shared.clearDiskAndMemoryCache()
                            await ComicPageImageLoader.shared.clearCache()
                            URLCache.shared.removeAllCachedResponses()
                            try? await Task.sleep(nanoseconds: 300_000_000)
                            await MainActor.run {
                                refreshStorageMetrics()
                                isClearingCache = false
                            }
                        }
                    } label: {
                        HStack(spacing: 4) {
                            if isClearingCache {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: AuroraColor.brand))
                                    .scaleEffect(0.6)
                            } else {
                                Image(systemName: "trash")
                                    .font(.caption2)
                                Text("清理缓存")
                                    .font(.caption2.weight(.medium))
                            }
                        }
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(AuroraColor.surfaceHigh, in: Capsule())
                        .foregroundStyle(AuroraColor.brand)
                    }
                    .buttonStyle(.plain)
                    .disabled(isClearingCache)
                }
                
                Spacer()
                
                Text("剩余可用 \(formatBytes(freeBytes))")
                    .font(.caption.bold())
                    .foregroundStyle(AuroraColor.textSecondary)
            }
            
            // 多段彩色容量进度条（基于真实文件系统已用空间）
            GeometryReader { geo in
                let width = max(geo.size.width, 1)
                let validTotal = max(totalBytes, 1)
                let usedBytes = max(totalBytes - freeBytes, downloadedBytes + cacheBytes)
                let systemOtherBytes = max(0, usedBytes - downloadedBytes - cacheBytes)
                
                let downloadRatio = CGFloat(downloadedBytes) / CGFloat(validTotal)
                let displayDownloadRatio = downloadedBytes > 0 ? min(max(downloadRatio, 0.03), 0.75) : 0
                
                let cacheRatio = CGFloat(cacheBytes) / CGFloat(validTotal)
                let displayCacheRatio = cacheBytes > 0 ? min(max(cacheRatio, 0.025), 0.50) : 0
                
                let rawSystemRatio = CGFloat(systemOtherBytes) / CGFloat(validTotal)
                let maxRemaining = max(0, 0.92 - displayDownloadRatio - displayCacheRatio)
                let systemRatio = min(max(rawSystemRatio, 0.08), maxRemaining)
                
                let downloadWidth = width * displayDownloadRatio
                let cacheWidth = width * displayCacheRatio
                let systemWidth = width * systemRatio
                
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(AuroraColor.surfaceHigh)
                        .frame(height: 10)
                    
                    HStack(spacing: 2) {
                        if downloadWidth > 0 {
                            Capsule()
                                .fill(Color(rgb: 0x0A84FF))
                                .frame(width: max(downloadWidth, 6), height: 10)
                        }
                        
                        if cacheWidth > 0 {
                            Capsule()
                                .fill(Color(rgb: 0xFF9F0A))
                                .frame(width: max(cacheWidth, 6), height: 10)
                        }
                        
                        Capsule()
                            .fill(Color(rgb: 0x8E8E93).opacity(0.6))
                            .frame(width: max(systemWidth, 12), height: 10)
                    }
                    .clipShape(Capsule())
                }
            }
            .frame(height: 10)
            
            // 图例说明行
            HStack(spacing: 12) {
                legendItem(color: Color(rgb: 0x0A84FF), label: "离线下载", value: formatBytes(downloadedBytes))
                legendItem(color: Color(rgb: 0xFF9F0A), label: "海报与缓存", value: formatBytes(cacheBytes))
                legendItem(color: Color(rgb: 0x8E8E93), label: "系统与其他", value: nil)
            }
            .font(.caption2)
        }
        .padding(AuroraMetrics.spacingM)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(AuroraColor.surfaceCard)
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(AuroraColor.hairline, lineWidth: 1)
                )
        )
        .onAppear {
            refreshStorageMetrics()
        }
    }
    
    private func legendItem(color: Color, label: String, value: String?) -> some View {
        HStack(spacing: 4) {
            Circle()
                .fill(color)
                .frame(width: 7, height: 7)
            Text(label)
                .foregroundStyle(AuroraColor.textSecondary)
            if let value {
                Text(value)
                    .fontWeight(.bold)
                    .foregroundStyle(AuroraColor.textPrimary)
            }
        }
    }
    
    private func refreshStorageMetrics() {
        Task.detached(priority: .utility) {
            var resolvedTotal: Int64 = 0
            var resolvedFree: Int64 = 0
            var resolvedCache: Int64 = 0
            var resolvedForumSaved: Int64 = 0

            if let attrs = try? FileManager.default.attributesOfFileSystem(forPath: NSHomeDirectory()) {
                if let total = attrs[.systemSize] as? NSNumber {
                    resolvedTotal = total.int64Value
                }
                if let free = attrs[.systemFreeSize] as? NSNumber {
                    resolvedFree = free.int64Value
                }
            }

            if let cachesURL = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first {
                resolvedCache = Self.folderSize(at: cachesURL)
            }
            if let docsURL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first {
                let forumDir = docsURL.appendingPathComponent("ForumSavedImages", isDirectory: true)
                resolvedForumSaved = Self.folderSize(at: forumDir)
            }

            await MainActor.run {
                if resolvedTotal > 0 { totalBytes = resolvedTotal }
                if resolvedFree > 0 { freeBytes = resolvedFree }
                cacheBytes = resolvedCache
                extraOfflineBytes = resolvedForumSaved
            }
        }
    }

    private nonisolated static func folderSize(at url: URL) -> Int64 {
        let fm = FileManager.default
        guard let enumerator = fm.enumerator(
            at: url,
            includingPropertiesForKeys: [.fileSizeKey, .isRegularFileKey],
            options: [.skipsHiddenFiles]
        ) else { return 0 }
        var total: Int64 = 0
        for case let fileURL as URL in enumerator {
            if let values = try? fileURL.resourceValues(forKeys: [.isRegularFileKey, .fileSizeKey]),
               values.isRegularFile == true,
               let size = values.fileSize {
                total += Int64(size)
            }
        }
        return total
    }
    
    private func formatBytes(_ bytes: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file)
    }
}
