import SwiftUI

public enum OfflineTab: String, CaseIterable, Identifiable {
    case video = "视频"
    case comic = "漫画"
    
    public var id: String { rawValue }
}

/// 离线缓存与双端下载管理界面（支持视频离线播放、漫画离线阅读、存储占用统计与分项管理）
public struct OfflineDownloadsScreen: View {
    @ObservedObject private var downloadManager = OfflineDownloadManager.shared
    @ObservedObject private var comicDownloadManager = ComicDownloadManager.shared
    public let onPlayVideo: (VideoItem) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var selectedTab: OfflineTab = .video
    @State private var showingClearAlert = false
    @State private var editMode: EditMode = .inactive
    @State private var selectedItemIDs = Set<String>()
    @State private var activeComicForReading: ComicItem? = nil
    
    public init(onPlayVideo: @escaping (VideoItem) -> Void) {
        self.onPlayVideo = onPlayVideo
    }
    
    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // 分段选择器（视频 / 漫画）
                Picker("下载类型", selection: $selectedTab) {
                    Text("视频 (\(downloadManager.items.count))").tag(OfflineTab.video)
                    Text("漫画 (\(comicDownloadManager.chapters.count))").tag(OfflineTab.comic)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(AuroraColor.canvas)
                
                Group {
                    switch selectedTab {
                    case .video:
                        if downloadManager.items.isEmpty {
                            emptyStateView(
                                icon: "video.slash",
                                title: "暂无离线视频",
                                description: "在视频二级详情页轻点「缓存」按钮，即可将视频下载至本地，无网络也能随时流畅观看。"
                            )
                        } else {
                            videoDownloadsListView
                        }
                    case .comic:
                        if comicDownloadManager.chapters.isEmpty {
                            emptyStateView(
                                icon: "book.closed",
                                title: "暂无离线漫画",
                                description: "在漫画阅读器顶栏轻点「下载」按钮，即可将单话或全本漫画离线至沙盒，断网也能秒开畅读。"
                            )
                        } else {
                            comicDownloadsListView
                        }
                    }
                }
            }
            .navigationTitle("离线缓存")
            .navigationBarTitleDisplayMode(.inline)
            .environment(\.editMode, $editMode)
            .navigationDestination(item: $activeComicForReading) { comic in
                ComicReaderView(comic: comic) {
                    activeComicForReading = nil
                }
                .toolbar(.hidden, for: .tabBar)
                .navigationBarBackButtonHidden(true)
            }
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    let hasItems = selectedTab == .video ? !downloadManager.items.isEmpty : !comicDownloadManager.chapters.isEmpty
                    if hasItems {
                        HStack(spacing: 12) {
                            if editMode == .active {
                                if !selectedItemIDs.isEmpty {
                                    Button(role: .destructive) {
                                        if selectedTab == .video {
                                            for id in selectedItemIDs {
                                                downloadManager.removeDownload(id: id)
                                            }
                                        } else {
                                            for id in selectedItemIDs {
                                                comicDownloadManager.removeDownload(id: id)
                                            }
                                        }
                                        selectedItemIDs.removeAll()
                                        editMode = .inactive
                                    } label: {
                                        Text("删除 (\(selectedItemIDs.count))")
                                            .font(.body.bold())
                                            .foregroundStyle(AuroraColor.accentRed)
                                    }
                                }
                                Button("完成") {
                                    editMode = .inactive
                                    selectedItemIDs.removeAll()
                                }
                                .font(.body.bold())
                                .foregroundStyle(AuroraColor.brand)
                            } else {
                                Button("编辑") {
                                    editMode = .active
                                }
                                .font(.body)
                                .foregroundStyle(AuroraColor.textPrimary)

                                Button(role: .destructive) {
                                    showingClearAlert = true
                                } label: {
                                    Text("清空")
                                        .font(.body.bold())
                                        .foregroundStyle(AuroraColor.brand)
                                        .frame(minWidth: 44, minHeight: 44)
                                        .contentShape(Rectangle())
                                }
                            }
                        }
                    }
                }
            }
            .alert("清空所有离线缓存？", isPresented: $showingClearAlert) {
                Button("清空", role: .destructive) {
                    if selectedTab == .video {
                        downloadManager.removeAll()
                    } else {
                        comicDownloadManager.removeAll()
                    }
                }
                Button("取消", role: .cancel) {}
            } message: {
                Text(selectedTab == .video ? "此操作将删除所有已下载的视频文件，释放本地存储空间。" : "此操作将删除所有已下载的离线漫画页面，释放本地存储空间。")
            }
        }
    }
    
    private func emptyStateView(icon: String, title: String, description: String) -> some View {
        VStack(spacing: AuroraMetrics.spacingL) {
            Image(systemName: icon)
                .font(.system(size: 64, weight: .light))
                .foregroundStyle(AuroraColor.brand.opacity(0.8))
                .padding(.bottom, AuroraMetrics.spacingS)
            
            Text(title)
                .font(.title3.bold())
                .foregroundStyle(AuroraColor.textPrimary)
            
            Text(description)
                .font(.subheadline)
                .foregroundStyle(AuroraColor.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, AuroraMetrics.spacingXXXL)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(AuroraColor.canvas)
    }
    
    // MARK: - 视频下载列表
    
    private var videoDownloadsListView: some View {
        List(selection: $selectedItemIDs) {
            // 顶部 Apple TV 风格多段彩色容量仪表盘
            Section {
                StorageUsageBarView()
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
            }
            
            Section(header: Text("视频缓存列表 (\(downloadManager.items.count))")) {
                ForEach(downloadManager.items) { item in
                    downloadVideoRow(item: item)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            if editMode == .inactive && item.status.isCompleted {
                                onPlayVideo(item.toVideoItem())
                            }
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) {
                                downloadManager.removeDownload(id: item.id)
                            } label: {
                                Label("删除", systemImage: "trash")
                            }
                        }
                }
            }
        }
        .listStyle(.insetGrouped)
        .background(AuroraColor.canvas)
    }
    
    @ViewBuilder
    private func downloadVideoRow(item: OfflineDownloadItem) -> some View {
        HStack(spacing: AuroraMetrics.spacingM) {
            AuroraCoverImage(
                url: item.coverUrl,
                title: item.displayTitle,
                aspectRatio: 16.0 / 9.0,
                contentMode: .fill,
                cornerRadius: 8
            )
            .frame(width: 96, height: 54)
            
            VStack(alignment: .leading, spacing: 4) {
                Text(item.displayTitle)
                    .font(.subheadline.bold())
                    .foregroundStyle(AuroraColor.textPrimary)
                    .lineLimit(2)
                
                HStack(spacing: 6) {
                    Text(item.sourceName)
                        .font(.caption2.bold())
                        .foregroundStyle(AuroraColor.brandLabel)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(AuroraColor.brand.opacity(0.12), in: Capsule())
                    
                    if item.totalBytes > 0 {
                        Text(ByteCountFormatter.string(fromByteCount: item.totalBytes, countStyle: .file))
                            .font(.caption2)
                            .foregroundStyle(AuroraColor.textSecondary)
                    }
                }
                
                switch item.status {
                case .queued, .resolving:
                    HStack(spacing: 4) {
                        ProgressView().scaleEffect(0.6)
                        Text("正在准备下载…")
                            .font(.caption2)
                            .foregroundStyle(AuroraColor.brand)
                    }
                case .downloading(let progress, _, _):
                    VStack(alignment: .leading, spacing: 2) {
                        ProgressView(value: progress)
                            .tint(AuroraColor.brand)
                        Text("正在缓存 \(Int(progress * 100))%")
                            .font(.caption2)
                            .foregroundStyle(AuroraColor.textSecondary)
                    }
                case .completed:
                    HStack(spacing: 4) {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.caption2)
                            .foregroundStyle(.green)
                        Text("已完成 · 点击播放")
                            .font(.caption2)
                            .foregroundStyle(.green)
                    }
                case .paused:
                    Text("已暂停")
                        .font(.caption2)
                        .foregroundStyle(AuroraColor.warning)
                case .failed(let msg):
                    Text("下载失败: \(msg)")
                        .font(.caption2)
                        .foregroundStyle(AuroraColor.warning)
                        .lineLimit(1)
                }
            }
            
            Spacer()
            
            if item.status.isCompleted {
                Image(systemName: "play.circle.fill")
                    .font(.title2)
                    .foregroundStyle(AuroraColor.brand)
            }
        }
        .padding(.vertical, 4)
    }

    // MARK: - 漫画下载列表
    
    private var comicDownloadsListView: some View {
        List(selection: $selectedItemIDs) {
            Section {
                HStack {
                    Image(systemName: "book.fill")
                        .foregroundStyle(AuroraColor.brand)
                    Text("已下载漫画占用")
                        .font(.subheadline)
                        .foregroundStyle(AuroraColor.textSecondary)
                    Spacer()
                    Text(comicDownloadManager.formattedTotalSpace())
                        .font(.subheadline.bold())
                        .foregroundStyle(AuroraColor.textPrimary)
                }
                .padding(.vertical, 4)
            }

            Section(header: Text("已缓存章节 (\(comicDownloadManager.chapters.count))")) {
                ForEach(comicDownloadManager.chapters) { item in
                    downloadComicRow(item: item)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            if editMode == .inactive && item.status.isCompleted {
                                openOfflineComic(item)
                            }
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) {
                                comicDownloadManager.removeDownload(id: item.id)
                            } label: {
                                Label("删除", systemImage: "trash")
                            }
                        }
                }
            }
        }
        .listStyle(.insetGrouped)
        .background(AuroraColor.canvas)
    }

    @ViewBuilder
    private func downloadComicRow(item: DownloadedComicChapter) -> some View {
        HStack(spacing: AuroraMetrics.spacingM) {
            AuroraCoverImage(
                url: item.coverUrl,
                title: item.displayTitle,
                aspectRatio: 3.0 / 4.0,
                contentMode: .fill,
                cornerRadius: 8
            )
            .frame(width: 54, height: 72)

            VStack(alignment: .leading, spacing: 4) {
                Text(item.comicTitle)
                    .font(.subheadline.bold())
                    .foregroundStyle(AuroraColor.textPrimary)
                    .lineLimit(1)

                Text(item.chapterTitle)
                    .font(.caption)
                    .foregroundStyle(AuroraColor.brandLabel)
                    .lineLimit(1)

                HStack(spacing: 6) {
                    Text("\(item.downloadedPages)/\(item.totalPages) 页")
                        .font(.caption2.bold())
                        .foregroundStyle(AuroraColor.textSecondary)

                    if item.totalBytes > 0 {
                        Text("· \(ByteCountFormatter.string(fromByteCount: item.totalBytes, countStyle: .file))")
                            .font(.caption2)
                            .foregroundStyle(AuroraColor.textSecondary)
                    }
                }

                switch item.status {
                case .queued, .resolving:
                    HStack(spacing: 4) {
                        ProgressView().scaleEffect(0.6)
                        Text("准备下载…")
                            .font(.caption2)
                            .foregroundStyle(AuroraColor.brand)
                    }
                case .downloading(let progress, let done, let total):
                    VStack(alignment: .leading, spacing: 2) {
                        ProgressView(value: progress)
                            .tint(AuroraColor.brand)
                        Text("下载中 \(done)/\(total) (\(Int(progress * 100))%)")
                            .font(.caption2)
                            .foregroundStyle(AuroraColor.textSecondary)
                    }
                case .completed:
                    HStack(spacing: 4) {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.caption2)
                            .foregroundStyle(.green)
                        Text("已完成 · 点击阅读")
                            .font(.caption2)
                            .foregroundStyle(.green)
                    }
                case .paused:
                    Text("已暂停")
                        .font(.caption2)
                        .foregroundStyle(AuroraColor.warning)
                case .failed(let msg):
                    Text("失败: \(msg)")
                        .font(.caption2)
                        .foregroundStyle(AuroraColor.warning)
                        .lineLimit(1)
                }
            }

            Spacer()

            if item.status.isCompleted {
                Image(systemName: "book.circle.fill")
                    .font(.title2)
                    .foregroundStyle(AuroraColor.brand)
            }
        }
        .padding(.vertical, 4)
    }

    private func openOfflineComic(_ item: DownloadedComicChapter) {
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
        activeComicForReading = comic
    }
}
