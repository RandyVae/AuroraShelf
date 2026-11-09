import SwiftUI

/// Apple TV / iOS 设置风格的存储容量多段彩色仪表盘（Storage Breakdown Bar）
public struct StorageUsageBarView: View {
    @ObservedObject private var downloadManager = OfflineDownloadManager.shared
    
    @State private var freeBytes: Int64 = 0
    @State private var totalBytes: Int64 = 0
    @State private var cacheBytes: Int64 = 0
    @State private var isClearingCache: Bool = false
    
    public init() {}
    
    private var downloadedBytes: Int64 {
        downloadManager.totalDownloadedBytes
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
                        .background(Color.white.opacity(0.08), in: Capsule())
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
            
            // 多段彩色容量进度条
            GeometryReader { geo in
                let width = geo.size.width
                let validTotal = max(totalBytes, 1)
                
                // 视觉放大微量下载空间，保证 100MB~1GB 依然可见
                let downloadRatio = CGFloat(downloadedBytes) / CGFloat(validTotal)
                let displayDownloadRatio = downloadedBytes > 0 ? max(downloadRatio, 0.04) : 0
                
                let cacheRatio = CGFloat(cacheBytes) / CGFloat(validTotal)
                let displayCacheRatio = cacheBytes > 0 ? max(cacheRatio, 0.03) : 0
                
                let systemRatio: CGFloat = 0.22 // 系统与其他应用预估占比
                
                let downloadWidth = width * displayDownloadRatio
                let cacheWidth = width * displayCacheRatio
                let systemWidth = width * systemRatio
                
                ZStack(alignment: .leading) {
                    // 底槽：浅灰/暗底
                    Capsule()
                        .fill(Color.white.opacity(0.12))
                        .frame(height: 10)
                    
                    HStack(spacing: 2) {
                        // 1. 离线视频下载（原生天蓝色）
                        if downloadWidth > 0 {
                            Capsule()
                                .fill(Color(rgb: 0x0A84FF))
                                .frame(width: max(downloadWidth, 6), height: 10)
                        }
                        
                        // 2. 媒体缓存（明亮琥珀橙）
                        if cacheWidth > 0 {
                            Capsule()
                                .fill(Color(rgb: 0xFF9F0A))
                                .frame(width: max(cacheWidth, 6), height: 10)
                        }
                        
                        // 3. 系统与其他（中性岩灰）
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
                legendItem(color: Color(rgb: 0x0A84FF), label: "已下载视频", value: formatBytes(downloadedBytes))
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
        // 读取系统真实磁盘容量
        if let attrs = try? FileManager.default.attributesOfFileSystem(forPath: NSHomeDirectory()) {
            if let total = attrs[.systemSize] as? NSNumber {
                totalBytes = total.int64Value
            }
            if let free = attrs[.systemFreeSize] as? NSNumber {
                freeBytes = free.int64Value
            }
        }
        
        // 读取 Caches 目录估算缓存
        if let cachesURL = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first {
            cacheBytes = folderSize(at: cachesURL)
        }
    }
    
    private func folderSize(at url: URL) -> Int64 {
        guard let files = try? FileManager.default.subpathsOfDirectory(atPath: url.path) else { return 0 }
        var total: Int64 = 0
        for file in files {
            let path = url.appendingPathComponent(file).path
            if let attrs = try? FileManager.default.attributesOfItem(atPath: path),
               let size = attrs[.size] as? NSNumber {
                total += size.int64Value
            }
        }
        return total
    }
    
    private func formatBytes(_ bytes: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file)
    }
}
