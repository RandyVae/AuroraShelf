import SwiftUI

public enum OfflineTab: String, CaseIterable, Identifiable {
    case video = "video"
    case comic = "comic"

    public var id: String { rawValue }

    public var displayTitle: String {
        switch self {
        case .video: return "downloads.tab_video".localized
        case .comic: return "downloads.tab_comic".localized
        }
    }
}

public enum OfflineStatusFilter: String, CaseIterable, Identifiable {
    case all = "all"
    case downloading = "downloading"
    case completed = "completed"
    case failed = "failed"

    public var id: String { rawValue }

    public var displayTitle: String {
        switch self {
        case .all: return "home.all".localized
        case .downloading: return "downloads.filter_downloading".localized
        case .completed: return "downloads.filter_completed".localized
        case .failed: return "downloads.filter_failed".localized
        }
    }

    func matches(videoStatus: OfflineDownloadStatus) -> Bool {
        switch self {
        case .all: return true
        case .downloading: return videoStatus.isDownloading
        case .completed: return videoStatus.isCompleted
        case .failed:
            if case .failed = videoStatus { return true }
            return false
        }
    }

    func matches(comicStatus: ComicDownloadStatus) -> Bool {
        switch self {
        case .all: return true
        case .downloading: return comicStatus.isDownloading
        case .completed: return comicStatus.isCompleted
        case .failed:
            if case .failed = comicStatus { return true }
            return false
        }
    }
}

/// 离线缓存与双端下载管理界面（支持视频离线播放、漫画离线阅读、存储占用统计与分项管理）
public struct OfflineDownloadsScreen: View {
    @ObservedObject private var downloadManager = OfflineDownloadManager.shared
    @ObservedObject private var comicDownloadManager = ComicDownloadManager.shared
    @ObservedObject private var locManager = LocalizationManager.shared
    public let showCloseButton: Bool
    public let onPlayVideo: (VideoItem) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var selectedTab: OfflineTab
    @State private var statusFilter: OfflineStatusFilter = .all
    @State private var showingClearAlert = false
    @State private var editMode: EditMode = .inactive
    @State private var selectedItemIDs = Set<String>()

    public init(
        initialTab: OfflineTab = .video,
        showCloseButton: Bool = false,
        onPlayVideo: @escaping (VideoItem) -> Void
    ) {
        self.showCloseButton = showCloseButton
        self.onPlayVideo = onPlayVideo
        self._selectedTab = State(initialValue: initialTab)
    }

    private var totalActiveCount: Int {
        downloadManager.activeItems.count + comicDownloadManager.activeChapters.count
    }

    private var combinedActiveProgress: Double {
        let activeProgresses = downloadManager.activeItems.map(\.status.progressValue)
            + comicDownloadManager.activeChapters.map(\.status.progressValue)
        guard !activeProgresses.isEmpty else {
            let hasCompleted = !downloadManager.completedItems.isEmpty || !comicDownloadManager.completedChapters.isEmpty
            return hasCompleted ? 1.0 : 0.0
        }
        return activeProgresses.reduce(0, +) / Double(activeProgresses.count)
    }

    private var filteredVideoItems: [OfflineDownloadItem] {
        downloadManager.items.filter { statusFilter.matches(videoStatus: $0.status) }
    }

    private var filteredComicChapters: [DownloadedComicChapter] {
        comicDownloadManager.chapters.filter { statusFilter.matches(comicStatus: $0.status) }
    }

    private func filterCount(for filter: OfflineStatusFilter) -> Int {
        switch selectedTab {
        case .video:
            return downloadManager.items.filter { filter.matches(videoStatus: $0.status) }.count
        case .comic:
            return comicDownloadManager.chapters.filter { filter.matches(comicStatus: $0.status) }.count
        }
    }

    private var hasItemsInSelectedTab: Bool {
        selectedTab == .video ? !downloadManager.items.isEmpty : !comicDownloadManager.chapters.isEmpty
    }

    public var body: some View {
        VStack(spacing: 0) {
            downloadOverviewCard
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 4)

            Picker("downloads.type_picker".localized, selection: $selectedTab) {
                Text("downloads.video_tab_count".localized(downloadManager.items.count)).tag(OfflineTab.video)
                Text("downloads.comic_tab_count".localized(comicDownloadManager.chapters.count)).tag(OfflineTab.comic)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
            .background(AuroraColor.canvas)

            statusFilterStrip
                .padding(.bottom, 6)
                .background(AuroraColor.canvas)

            Group {
                switch selectedTab {
                case .video:
                    if downloadManager.items.isEmpty {
                        emptyStateView(
                            icon: "video.slash",
                            title: "downloads.empty_video_title".localized,
                            description: "downloads.empty_video_desc".localized
                        )
                    } else if filteredVideoItems.isEmpty {
                        emptyStateView(
                            icon: "line.3.horizontal.decrease.circle",
                            title: "downloads.empty_video_filter_title".localized,
                            description: "downloads.empty_video_filter_desc".localized
                        )
                    } else {
                        videoDownloadsListView
                    }
                case .comic:
                    if comicDownloadManager.chapters.isEmpty {
                        emptyStateView(
                            icon: "book.closed",
                            title: "downloads.empty_comic_title".localized,
                            description: "downloads.empty_comic_desc".localized
                        )
                    } else if filteredComicChapters.isEmpty {
                        emptyStateView(
                            icon: "line.3.horizontal.decrease.circle",
                            title: "downloads.empty_comic_filter_title".localized,
                            description: "downloads.empty_comic_filter_desc".localized
                        )
                    } else {
                        comicDownloadsListView
                    }
                }
            }
        }
        .onChange(of: selectedTab) { _, _ in
            editMode = .inactive
            selectedItemIDs.removeAll()
        }
        .onChange(of: statusFilter) { _, _ in
            editMode = .inactive
            selectedItemIDs.removeAll()
        }
        .navigationTitle("downloads.nav_title".localized)
        .navigationBarTitleDisplayMode(.inline)
        .environment(\.editMode, $editMode)
        .toolbar {
            downloadsToolbarContent
        }
        .alert("downloads.clear_alert_title".localized, isPresented: $showingClearAlert) {
            Button("common.clear".localized, role: .destructive) {
                if selectedTab == .video {
                    downloadManager.removeAll()
                } else {
                    comicDownloadManager.removeAll()
                }
            }
            Button("common.cancel".localized, role: .cancel) {}
        } message: {
            Text(selectedTab == .video ? "downloads.clear_video_desc".localized : "downloads.clear_comic_desc".localized)
        }
    }

    @ToolbarContentBuilder
    private var downloadsToolbarContent: some ToolbarContent {
        if showCloseButton {
            ToolbarItem(placement: .topBarLeading) {
                Button("common.close".localized) { dismiss() }
                    .font(.body)
                    .frame(minWidth: AuroraMetrics.minTouchTarget, minHeight: AuroraMetrics.minTouchTarget)
            }
        }

        if hasItemsInSelectedTab {
            if #available(iOS 27.0, *) {
                ToolbarItem(placement: .topBarPinnedTrailing) {
                    primaryEditToolbarButton
                }
                ToolbarOverflowMenu {
                    if editMode == .active, !selectedItemIDs.isEmpty {
                        Button(role: .destructive, action: deleteSelectedItems) {
                            Label("downloads.delete_selected".localized(selectedItemIDs.count), systemImage: "trash")
                        }
                    }
                    Button(role: .destructive) {
                        showingClearAlert = true
                    } label: {
                        Label("common.clear".localized, systemImage: "trash.slash")
                    }
                }
            } else {
                ToolbarItem(placement: .topBarTrailing) {
                    HStack(spacing: 12) {
                        if editMode == .active, !selectedItemIDs.isEmpty {
                            Button(role: .destructive, action: deleteSelectedItems) {
                                Text("downloads.delete_selected".localized(selectedItemIDs.count))
                                    .font(.body.bold())
                                    .foregroundStyle(AuroraColor.accentRed)
                            }
                        }
                        primaryEditToolbarButton
                        if editMode != .active {
                            Button(role: .destructive) {
                                showingClearAlert = true
                            } label: {
                                Text("common.clear".localized)
                                    .font(.body.bold())
                                    .foregroundStyle(AuroraColor.brandLabel)
                                    .frame(minWidth: AuroraMetrics.minTouchTarget, minHeight: AuroraMetrics.minTouchTarget)
                                    .contentShape(Rectangle())
                            }
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var primaryEditToolbarButton: some View {
        if editMode == .active {
            Button("common.done".localized) {
                editMode = .inactive
                selectedItemIDs.removeAll()
            }
            .font(.body.bold())
            .foregroundStyle(AuroraColor.brandLabel)
            .frame(minWidth: AuroraMetrics.minTouchTarget, minHeight: AuroraMetrics.minTouchTarget)
        } else {
            Button("common.edit".localized) {
                editMode = .active
            }
            .font(.body)
            .foregroundStyle(AuroraColor.textPrimary)
            .frame(minWidth: AuroraMetrics.minTouchTarget, minHeight: AuroraMetrics.minTouchTarget)
        }
    }

    private func deleteSelectedItems() {
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
    }

    private var downloadOverviewCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                Image(systemName: totalActiveCount > 0 ? "arrow.down.circle.fill" : "internaldrive.fill")
                    .font(.title3)
                    .foregroundStyle(AuroraColor.brandLabel)
                VStack(alignment: .leading, spacing: 2) {
                    Text(
                        totalActiveCount > 0
                            ? "downloads.active_header".localized(totalActiveCount)
                            : "downloads.overview_header".localized
                    )
                    .font(.subheadline.bold())
                    .foregroundStyle(AuroraColor.textPrimary)
                    Text(
                        "downloads.summary_stats".localized(
                            downloadManager.completedItems.count,
                            downloadManager.items.count,
                            downloadManager.formattedTotalSpace(),
                            comicDownloadManager.completedChapters.count,
                            comicDownloadManager.chapters.count,
                            comicDownloadManager.formattedTotalSpace()
                        )
                    )
                    .font(.caption)
                    .foregroundStyle(AuroraColor.textSecondary)
                    .lineLimit(1)
                }
                Spacer(minLength: 4)
                if totalActiveCount > 0 {
                    Text("\(Int(combinedActiveProgress * 100))%")
                        .font(.caption.monospacedDigit().bold())
                        .foregroundStyle(AuroraColor.brandLabel)
                }
            }

            if totalActiveCount > 0 {
                ProgressView(value: combinedActiveProgress)
                    .tint(AuroraColor.brandSolid)
            }
        }
        .padding(12)
        .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Color.primary.opacity(0.08), lineWidth: 1)
        )
    }

    private var statusFilterStrip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(OfflineStatusFilter.allCases, id: \.self) { filter in
                    let count = filterCount(for: filter)
                    let isSelected = statusFilter == filter
                    Button {
                        AuroraMotion.perform(AuroraMotion.quick, reduceMotion: reduceMotion) {
                            statusFilter = filter
                        }
                    } label: {
                        Text("\(filter.displayTitle) (\(count))")
                            .font(.caption.weight(isSelected ? .bold : .medium))
                            .foregroundStyle(isSelected ? Color.white : AuroraColor.textSecondary)
                            .padding(.horizontal, 14)
                            .frame(minHeight: AuroraMetrics.minTouchTarget - 6)
                            .background(
                                isSelected ? AuroraColor.brandSolid : AuroraColor.surfaceCard,
                                in: Capsule()
                            )
                    }
                    .buttonStyle(.plain)
                    .frame(minHeight: AuroraMetrics.minTouchTarget)
                    .contentShape(Rectangle())
                    .hoverEffect(.highlight)
                }
            }
            .padding(.horizontal, 16)
        }
        .sensoryFeedback(.selection, trigger: statusFilter)
    }

    private func emptyStateView(icon: String, title: String, description: String) -> some View {
        VStack(spacing: AuroraMetrics.spacingL) {
            Image(systemName: icon)
                .font(.largeTitle.weight(.light))
                .imageScale(.large)
                .foregroundStyle(AuroraColor.brandLabel.opacity(0.85))
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
            Section {
                StorageUsageBarView()
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
            }

            Section(header: Text("downloads.video_section".localized(filteredVideoItems.count)).accessibilityAddTraits(.isHeader)) {
                ForEach(filteredVideoItems) { item in
                    downloadVideoRow(item: item)
                        .contentShape(Rectangle())
                        .accessibilityAddTraits(.isButton)
                        .onTapGesture {
                            guard editMode == .inactive else { return }
                            if item.status.isCompleted {
                                let videoItem = item.toVideoItem()
                                if showCloseButton {
                                    dismiss()
                                }
                                onPlayVideo(videoItem)
                            } else if case .failed = item.status {
                                downloadManager.retryDownload(id: item.id)
                            }
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) {
                                downloadManager.removeDownload(id: item.id)
                            } label: {
                                Label("common.delete".localized, systemImage: "trash")
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
                        Text("downloads.preparing".localized)
                            .font(.caption2)
                            .foregroundStyle(AuroraColor.brandLabel)
                    }
                case .downloading(let progress, let written, let total):
                    VStack(alignment: .leading, spacing: 2) {
                        ProgressView(value: progress)
                            .tint(AuroraColor.brandSolid)
                        let writtenStr = ByteCountFormatter.string(fromByteCount: written, countStyle: .file)
                        let totalStr = total > 0 ? " / \(ByteCountFormatter.string(fromByteCount: total, countStyle: .file))" : ""
                        Text("downloads.downloading_progress".localized(Int(progress * 100), writtenStr, totalStr))
                            .font(.caption2)
                            .foregroundStyle(AuroraColor.textSecondary)
                    }
                case .completed:
                    HStack(spacing: 4) {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.caption2)
                            .foregroundStyle(.green)
                        Text("downloads.completed_play".localized)
                            .font(.caption2)
                            .foregroundStyle(.green)
                    }
                case .paused:
                    Text("downloads.paused".localized)
                        .font(.caption2)
                        .foregroundStyle(AuroraColor.warning)
                case .failed(let msg):
                    Text("downloads.failed_msg".localized(msg))
                        .font(.caption2)
                        .foregroundStyle(AuroraColor.warning)
                        .lineLimit(1)
                }
            }

            Spacer()

            if item.status.isCompleted {
                Image(systemName: "play.circle.fill")
                    .font(.title2)
                    .foregroundStyle(AuroraColor.brandLabel)
            } else if case .failed = item.status {
                Button {
                    downloadManager.retryDownload(id: item.id)
                } label: {
                    Image(systemName: "arrow.clockwise.circle.fill")
                        .font(.title2)
                        .foregroundStyle(AuroraColor.brandLabel)
                        .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("downloads.retry_video_a11y".localized)
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
                        .foregroundStyle(AuroraColor.brandLabel)
                    Text("downloads.comic_space_used".localized)
                        .font(.subheadline)
                        .foregroundStyle(AuroraColor.textSecondary)
                    Spacer()
                    Text(comicDownloadManager.formattedTotalSpace())
                        .font(.subheadline.bold())
                        .foregroundStyle(AuroraColor.textPrimary)
                }
                .padding(.vertical, 4)
            }

            Section(header: Text("downloads.comic_section".localized(filteredComicChapters.count)).accessibilityAddTraits(.isHeader)) {
                ForEach(filteredComicChapters) { item in
                    downloadComicRow(item: item)
                        .contentShape(Rectangle())
                        .accessibilityAddTraits(.isButton)
                        .onTapGesture {
                            guard editMode == .inactive else { return }
                            if item.status.isCompleted {
                                openOfflineComic(item)
                            } else if case .failed = item.status {
                                comicDownloadManager.retryDownload(id: item.id)
                            }
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) {
                                comicDownloadManager.removeDownload(id: item.id)
                            } label: {
                                Label("common.delete".localized, systemImage: "trash")
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
                    Text("downloads.pages_progress".localized(item.downloadedPages, max(item.totalPages, item.downloadedPages)))
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
                        Text("downloads.preparing".localized)
                            .font(.caption2)
                            .foregroundStyle(AuroraColor.brandLabel)
                    }
                case .downloading(let progress, let done, let total):
                    VStack(alignment: .leading, spacing: 2) {
                        ProgressView(value: progress)
                            .tint(AuroraColor.brandSolid)
                        Text("downloads.comic_downloading".localized(done, max(total, done), Int(progress * 100)))
                            .font(.caption2)
                            .foregroundStyle(AuroraColor.textSecondary)
                    }
                case .completed:
                    HStack(spacing: 4) {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.caption2)
                            .foregroundStyle(.green)
                        Text("downloads.completed_read".localized)
                            .font(.caption2)
                            .foregroundStyle(.green)
                    }
                case .paused:
                    Text("downloads.paused".localized)
                        .font(.caption2)
                        .foregroundStyle(AuroraColor.warning)
                case .failed(let msg):
                    Text("downloads.comic_failed_msg".localized(msg))
                        .font(.caption2)
                        .foregroundStyle(AuroraColor.warning)
                        .lineLimit(1)
                }
            }

            Spacer()

            if item.status.isCompleted {
                Image(systemName: "book.circle.fill")
                    .font(.title2)
                    .foregroundStyle(AuroraColor.brandLabel)
            } else if case .failed = item.status {
                Button {
                    comicDownloadManager.retryDownload(id: item.id)
                } label: {
                    Image(systemName: "arrow.clockwise.circle.fill")
                        .font(.title2)
                        .foregroundStyle(AuroraColor.brandLabel)
                        .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("downloads.retry_comic_a11y".localized)
            }
        }
        .padding(.vertical, 4)
    }

    private func openOfflineComic(_ item: DownloadedComicChapter) {
        let siblingDownloads = comicDownloadManager.completedChapters.filter { candidate in
            candidate.comicId == item.comicId &&
                (candidate.sourceId == item.sourceId || candidate.sourceId.isEmpty || item.sourceId.isEmpty)
        }
        let sortedSiblings = siblingDownloads.sorted { lhs, rhs in
            let cmp = lhs.chapterTitle.localizedStandardCompare(rhs.chapterTitle)
            if cmp != .orderedSame {
                return cmp == .orderedAscending
            }
            return lhs.createdAt < rhs.createdAt
        }
        var seenChapterIDs = Set<String>()
        var bundledChapters: [ComicChapter] = []
        for entry in sortedSiblings where seenChapterIDs.insert(entry.chapterId).inserted {
            bundledChapters.append(
                ComicChapter(
                    id: entry.chapterId,
                    title: entry.chapterTitle,
                    pageCount: max(entry.totalPages, entry.downloadedPages)
                )
            )
        }
        if bundledChapters.isEmpty {
            bundledChapters = [
                ComicChapter(
                    id: item.chapterId,
                    title: item.chapterTitle,
                    pageCount: max(item.totalPages, item.downloadedPages)
                )
            ]
        }
        let initialIdx = bundledChapters.firstIndex(where: { $0.id == item.chapterId }) ?? 0
        let comic = ComicItem(
            id: item.comicId,
            sourceId: item.sourceId,
            title: item.comicTitle,
            author: "",
            coverUrl: item.coverUrl,
            chapters: bundledChapters
        )
        if showCloseButton {
            dismiss()
        }
        PlayerCoordinator.shared.openComic(comic, initialChapterIndex: initialIdx, restoreProgress: false)
    }
}
