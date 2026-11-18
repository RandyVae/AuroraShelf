import SwiftUI

private struct ComicPageImageView: View {
    let sourceId: String
    let comicId: String
    let chapterId: String
    let page: ComicPage
    let pageNumber: Int
    let onAppearPage: (Int) -> Void

    @State private var image: UIImage?
    @State private var errorMessage: String?
    @State private var loadID = UUID()
    @State private var activeTask: Task<Void, Never>?

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(maxWidth: .infinity)
            } else if let errorMessage {
                VStack(spacing: AuroraMetrics.spacingS) {
                    Image(systemName: "photo.badge.exclamationmark")
                        .font(.title2)
                    Text("第 \(pageNumber) 页加载失败")
                    Text(errorMessage)
                        .font(.caption)
                    Button("重试", systemImage: "arrow.clockwise") {
                        startLoad()
                    }
                    .buttonStyle(.bordered)
                }
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, minHeight: 300)
            } else {
                ProgressView("reader.loading_image".localized)
                    .frame(maxWidth: .infinity, minHeight: 300)
            }
        }
        .onAppear {
            onAppearPage(pageNumber)
            if image == nil {
                startLoad()
            }
        }
        .onChange(of: page.id) { _, _ in
            startLoad()
        }
        .onDisappear {
            // Release offscreen decoded UIImage from @State so 100+ page chapters never OOM;
            // re-entering viewport reloads rapidly from ComicPageImageLoader's NSCache or local disk.
            activeTask?.cancel()
            activeTask = nil
            image = nil
        }
    }

    private func startLoad() {
        activeTask?.cancel()
        activeTask = Task {
            await loadPage()
        }
    }

    private func loadPage() async {
        let requestID = UUID()
        loadID = requestID
        image = nil
        errorMessage = nil

        // 1. 优先读取离线已解密缓存图片（100% 离线秒开、零网络消耗）
        if let local = await ComicDownloadManager.shared.localImage(
            comicId: comicId,
            chapterId: chapterId,
            pageIndex: max(0, pageNumber - 1),
            sourceId: sourceId
        ) {
            guard !Task.isCancelled, loadID == requestID else { return }
            image = local
            return
        }

        do {
            let loaded = try await ComicPageImageLoader.shared.load(page)
            guard !Task.isCancelled, loadID == requestID else { return }
            image = loaded
        } catch {
            guard !Task.isCancelled, loadID == requestID else { return }
            errorMessage = error.localizedDescription
        }
    }
}

public enum ComicReaderTheme: String, CaseIterable, Identifiable {
    case oled = "OLED 纯黑"
    case parchment = "暖羊皮纸"
    case charcoal = "典雅炭灰"
    case paper = "原纸暖白"

    public var id: String { rawValue }

    public var backgroundColor: Color {
        switch self {
        case .oled: return Color.black
        case .parchment: return Color(red: 0.96, green: 0.94, blue: 0.89)
        case .charcoal: return Color(red: 0.12, green: 0.12, blue: 0.14)
        case .paper: return Color(red: 0.98, green: 0.97, blue: 0.95)
        }
    }

    public var isDark: Bool {
        switch self {
        case .oled, .charcoal: return true
        case .parchment, .paper: return false
        }
    }
}

public struct ComicReaderView: View {
    public let comic: ComicItem
    @State public var currentChapterIndex: Int = 0
    public let restoreProgress: Bool
    public let onDismiss: () -> Void

    @State private var pages: [ComicPage] = []
    @State private var currentPageNumber: Int = 1
    @State private var pendingScrollPageNumber: Int?
    @State private var didCheckInitialProgress = false
    @State private var showControls: Bool = true
    @State private var showChapterPicker: Bool = false
    @State private var isLoading: Bool = false
    @State private var loadError: String?
    @State private var pageLoadTask: Task<Void, Never>?
    @State private var activePageLoadID = UUID()
    @State private var readerTheme: ComicReaderTheme = .oled
    @State private var isTwoPageSpread: Bool = false
    @State private var zoomScale: CGFloat = 1.0
    @State private var committedZoomScale: CGFloat = 1.0
    @ObservedObject private var downloadManager = ComicDownloadManager.shared
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    public init(
        comic: ComicItem,
        initialChapterIndex: Int = 0,
        restoreProgress: Bool = true,
        onDismiss: @escaping () -> Void
    ) {
        self.comic = comic
        self._currentChapterIndex = State(initialValue: max(0, initialChapterIndex))
        self.restoreProgress = restoreProgress
        self.onDismiss = onDismiss
    }

    public var currentChapter: ComicChapter? {
        if currentChapterIndex >= 0 && currentChapterIndex < comic.chapters.count {
            return comic.chapters[currentChapterIndex]
        }
        return nil
    }

    public var body: some View {
        ZStack {
            // 环境光阅读底衬（支持 Apple Books 经典 4 态自适应切换）
            readerTheme.backgroundColor.ignoresSafeArea()

            // 漫画页面瀑布流（支持双指缩放与双击还原）
            ScrollViewReader { proxy in
                ScrollView(zoomScale > 1.01 ? [.vertical, .horizontal] : .vertical, showsIndicators: false) {
                    LazyVStack(spacing: 0) {
                        // 顶部定位锚点（换章置顶核心锚点，杜绝滚动越界）
                        Color.clear.frame(height: 1).id("top_anchor")

                        if let loadError {
                            ContentUnavailableView {
                                Label("章节暂不可用", systemImage: "wifi.exclamationmark")
                            } description: {
                                Text(loadError)
                            } actions: {
                                HStack(spacing: AuroraMetrics.spacingM) {
                                    Button("重试", systemImage: "arrow.clockwise") {
                                        loadPages()
                                    }
                                    .buttonStyle(.borderedProminent)

                                    Button("返回漫画列表", systemImage: "chevron.backward") {
                                        dismissReader()
                                    }
                                    .buttonStyle(.bordered)
                                }
                            }
                            .tint(AuroraColor.brand)
                            .frame(maxWidth: .infinity, minHeight: 300)
                        } else if isLoading {
                            VStack(spacing: AuroraMetrics.spacingM) {
                                ProgressView("正在加载章节…")
                                Button("取消并返回") {
                                    dismissReader()
                                }
                                .buttonStyle(.bordered)
                                .tint(AuroraColor.brand)
                            }
                            .frame(maxWidth: .infinity, minHeight: 300)
                        } else if pages.isEmpty {
                            ContentUnavailableView {
                                Label("本章没有页面", systemImage: "photo")
                            } description: {
                                Text("此章节暂时没有可显示的图片。")
                            } actions: {
                                HStack(spacing: AuroraMetrics.spacingM) {
                                    Button("重新加载", systemImage: "arrow.clockwise") {
                                        loadPages()
                                    }
                                    .buttonStyle(.borderedProminent)

                                    Button("返回漫画列表", systemImage: "chevron.backward") {
                                        dismissReader()
                                    }
                                    .buttonStyle(.bordered)
                                }
                            }
                            .tint(AuroraColor.brand)
                            .frame(maxWidth: .infinity, minHeight: 300)
                        } else {
                            if isTwoPageSpread && horizontalSizeClass == .regular {
                                // iPad 宽屏双页展开模式（Apple Books 拟物阅读画廊）
                                let pairs = stride(from: 0, to: pages.count, by: 2).map {
                                    Array(pages[$0..<min($0 + 2, pages.count)])
                                }
                                ForEach(Array(pairs.enumerated()), id: \.offset) { pairIndex, pair in
                                    HStack(spacing: 8) {
                                        ForEach(Array(pair.enumerated()), id: \.element.id) { subIndex, page in
                                            let pageNum = pairIndex * 2 + subIndex + 1
                                            ComicPageImageView(
                                                sourceId: comic.sourceId,
                                                comicId: comic.id,
                                                chapterId: currentChapter?.id ?? "",
                                                page: page,
                                                pageNumber: pageNum,
                                                onAppearPage: { visiblePage in
                                                    recordReadingProgress(pageNumber: visiblePage)
                                                }
                                            )
                                        }
                                    }
                                    .id("page_\(pairIndex * 2 + 1)")
                                    .frame(maxWidth: 1080)
                                    .padding(.horizontal, 16)
                                }
                            } else {
                                ForEach(Array(pages.enumerated()), id: \.element.id) { index, page in
                                    let pageNum = index + 1
                                    ComicPageImageView(
                                        sourceId: comic.sourceId,
                                        comicId: comic.id,
                                        chapterId: currentChapter?.id ?? "",
                                        page: page,
                                        pageNumber: pageNum,
                                        onAppearPage: { visiblePage in
                                            recordReadingProgress(pageNumber: visiblePage)
                                        }
                                    )
                                    .id("page_\(pageNum)")
                                }
                            }
                        }

                        // 换章操作栏
                        VStack(spacing: AuroraMetrics.spacingL) {
                            HStack(spacing: AuroraMetrics.spacingXL) {
                                Button(action: {
                                    if currentChapterIndex > 0 {
                                        switchChapter(to: currentChapterIndex - 1, proxy: proxy)
                                    }
                                }) {
                                    Text("reader.prev_chapter".localized)
                                        .font(.headline)
                                        .foregroundStyle(
                                            currentChapterIndex > 0
                                                ? (readerTheme.isDark ? Color.white : Color.black.opacity(0.88))
                                                : Color.secondary
                                        )
                                        .padding(.horizontal, AuroraMetrics.spacingXL)
                                        .frame(height: AuroraMetrics.minTouchTarget)
                                        .background(
                                            readerTheme.isDark ? AuroraColor.surfaceCard : Color.black.opacity(0.08),
                                            in: .capsule
                                        )
                                }
                                .buttonStyle(.plain)
                                .disabled(currentChapterIndex <= 0)

                                Button(action: {
                                    if currentChapterIndex < comic.chapters.count - 1 {
                                        switchChapter(to: currentChapterIndex + 1, proxy: proxy)
                                    }
                                }) {
                                    Text("reader.next_chapter".localized)
                                        .font(.headline)
                                        .foregroundStyle(currentChapterIndex < comic.chapters.count - 1 ? .white : .secondary)
                                        .padding(.horizontal, AuroraMetrics.spacingXL)
                                        .frame(height: AuroraMetrics.minTouchTarget)
                                        .background(AuroraColor.brand, in: .capsule)
                                }
                                .buttonStyle(.plain)
                                .disabled(currentChapterIndex >= comic.chapters.count - 1)
                            }

                            Text("已阅至本话末尾")
                                .font(.footnote)
                                .foregroundStyle(readerTheme.isDark ? Color.white.opacity(0.6) : Color.black.opacity(0.55))
                        }
                        .padding(.vertical, AuroraMetrics.spacingXXL)
                    }
                    .scaleEffect(zoomScale, anchor: .top)
                }
                .simultaneousGesture(
                    MagnificationGesture()
                        .onChanged { value in
                            let proposed = committedZoomScale * value
                            zoomScale = min(max(proposed, 1.0), 3.0)
                        }
                        .onEnded { value in
                            let finalScale = min(max(committedZoomScale * value, 1.0), 3.0)
                            let snapped = finalScale < 1.08 ? 1.0 : finalScale
                            withAnimation(.spring(response: 0.25, dampingFraction: 0.85)) {
                                zoomScale = snapped
                            }
                            committedZoomScale = snapped
                        }
                )
                .onTapGesture(count: 2) {
                    withAnimation(.spring(response: 0.25, dampingFraction: 0.85)) {
                        if zoomScale > 1.05 {
                            zoomScale = 1.0
                            committedZoomScale = 1.0
                        } else {
                            zoomScale = 2.0
                            committedZoomScale = 2.0
                        }
                    }
                }
                .onTapGesture(count: 1) {
                    guard !shouldKeepControlsVisible else { return }
                    withAnimation(AuroraMotion.selection(reduceMotion: reduceMotion)) {
                        showControls.toggle()
                    }
                }
                .onChange(of: pages.count) { _, newCount in
                    guard newCount > 0, let targetPage = pendingScrollPageNumber else { return }
                    pendingScrollPageNumber = nil
                    let clampedPage = min(max(1, targetPage), newCount)
                    currentPageNumber = clampedPage
                    if clampedPage > 1 {
                        let anchorID: String
                        if isTwoPageSpread && horizontalSizeClass == .regular {
                            let oddPage = ((clampedPage - 1) / 2) * 2 + 1
                            anchorID = "page_\(oddPage)"
                        } else {
                            anchorID = "page_\(clampedPage)"
                        }
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.08) {
                            proxy.scrollTo(anchorID, anchor: .top)
                        }
                    }
                }
                .sheet(isPresented: $showChapterPicker) {
                    chapterPickerSheet(proxy: proxy)
                }
            }

            VStack {
                if showControls || shouldKeepControlsVisible {
                    PlayerOverlayBar(
                        title: comic.title,
                        subtitle: currentChapter.map {
                            pages.isEmpty
                                ? "\($0.title) · 连载瀑布流"
                                : "\($0.title) · \("reader.page".localized(currentPageNumber, pages.count))"
                        } ?? "章节不可用",
                        onDismiss: {
                            dismissReader()
                        }
                    ) {
                        HStack(spacing: 8) {
                            if comic.chapters.count > 1 {
                                Button {
                                    showChapterPicker = true
                                } label: {
                                    Image(systemName: "list.bullet")
                                        .font(.subheadline.bold())
                                        .foregroundStyle(.white)
                                        .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                                        .glassEffect(.regular.interactive(), in: .circle)
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel("章节目录")
                            }

                            // 离线下载当前话与整本漫画
                            if let chapter = currentChapter {
                                let downloadStatus = downloadManager.status(
                                    for: comic.id,
                                    chapterId: chapter.id,
                                    sourceId: comic.sourceId
                                )
                                Menu {
                                    if downloadStatus?.isCompleted == true {
                                        Button(role: .destructive) {
                                            let itemId = downloadManager.generateID(
                                                sourceId: comic.sourceId,
                                                comicId: comic.id,
                                                chapterId: chapter.id
                                            )
                                            downloadManager.removeDownload(id: itemId)
                                        } label: {
                                            Label("删除本话离线缓存", systemImage: "trash")
                                        }
                                    } else {
                                        Button {
                                            downloadManager.startDownload(comic: comic, chapter: chapter)
                                        } label: {
                                            Label("下载本话", systemImage: "arrow.down.circle")
                                        }
                                    }

                                    Button {
                                        downloadManager.startDownloadAll(comic: comic)
                                    } label: {
                                        Label("下载全部章节 (\(comic.chapters.count)话)", systemImage: "arrow.down.circle.fill")
                                    }
                                } label: {
                                    Group {
                                        if let status = downloadStatus, status.isCompleted {
                                            Image(systemName: "checkmark.circle.fill")
                                                .foregroundStyle(AuroraColor.success)
                                        } else if let status = downloadStatus, status.isDownloading {
                                            ProgressView(value: status.progressValue)
                                                .progressViewStyle(CircularProgressViewStyle(tint: AuroraColor.brand))
                                                .frame(width: 18, height: 18)
                                        } else {
                                            Image(systemName: "arrow.down.circle")
                                                .foregroundStyle(.white)
                                        }
                                    }
                                    .font(.subheadline.bold())
                                    .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                                    .glassEffect(.regular.interactive(), in: .circle)
                                }
                                .accessibilityLabel("离线下载")
                            }

                            // iPad 双页展开切换
                            if horizontalSizeClass == .regular {
                                Button {
                                    withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                                        isTwoPageSpread.toggle()
                                    }
                                } label: {
                                    Image(systemName: isTwoPageSpread ? "book.closed" : "book")
                                        .font(.subheadline.bold())
                                        .foregroundStyle(.white)
                                        .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                                        .glassEffect(.regular.interactive(), in: .circle)
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(isTwoPageSpread ? "单页阅读模式" : "双页画册模式")
                            }

                            // Apple Books 风格阅读环境底色切换菜单
                            Menu {
                                ForEach(ComicReaderTheme.allCases) { theme in
                                    Button {
                                        readerTheme = theme
                                    } label: {
                                        HStack {
                                            Text(theme.rawValue)
                                            if readerTheme == theme {
                                                Image(systemName: "checkmark")
                                            }
                                        }
                                    }
                                }
                            } label: {
                                Image(systemName: "textformat.size")
                                    .font(.subheadline.bold())
                                    .foregroundStyle(.white)
                                    .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                                    .glassEffect(.regular.interactive(), in: .circle)
                            }
                            .accessibilityLabel("阅读背景底色，当前：\(readerTheme.rawValue)")
                        }
                    }
                    .transition(.move(edge: .top).combined(with: .opacity))
                }

                Spacer()

                if showControls && !shouldKeepControlsVisible {
                    // 底部当前章节指示与选章胶囊（采用原生 Liquid Glass）
                    Button {
                        if comic.chapters.count > 1 {
                            showChapterPicker = true
                        }
                    } label: {
                        HStack(spacing: 6) {
                            if comic.chapters.count > 1 {
                                Image(systemName: "list.bullet")
                                    .font(.caption.bold())
                            }
                            Text(currentChapter.map {
                                pages.isEmpty
                                    ? $0.title
                                    : "\($0.title) · \("reader.page".localized(currentPageNumber, pages.count))"
                            } ?? "章节不可用")
                                .font(.subheadline)
                            if zoomScale > 1.05 {
                                Text(String(format: "%.1fx", zoomScale))
                                    .font(.caption2.monospacedDigit().bold())
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(AuroraColor.brand, in: Capsule())
                            }
                        }
                        .foregroundStyle(.white)
                        .padding(.horizontal, AuroraMetrics.spacingL)
                        .frame(height: AuroraMetrics.minTouchTarget)
                        .glassEffect(.regular.interactive(), in: .capsule)
                    }
                    .buttonStyle(.plain)
                    .environment(\.colorScheme, .dark)
                    .padding(.bottom, max(DeviceLayout.safeAreaBottom, AuroraMetrics.spacingXL))
                }
            }
            .zIndex(10)
        }
        .ignoresSafeArea()
        .toolbar(.hidden, for: .navigationBar)
        .preferredColorScheme(readerTheme.isDark ? .dark : .light)
        .onAppear {
            restoreInitialProgressIfNeeded()
            loadPages()
        }
        .onDisappear {
            flushCurrentProgress()
            pageLoadTask?.cancel()
        }
        .enableSwipeBack {
            dismissReader()
        }
    }

    private func chapterPickerSheet(proxy: ScrollViewProxy) -> some View {
        NavigationStack {
            List {
                ForEach(Array(comic.chapters.enumerated()), id: \.offset) { idx, chapter in
                    let isCurrent = idx == currentChapterIndex
                    let isOffline = downloadManager.isDownloaded(
                        comicId: comic.id,
                        chapterId: chapter.id,
                        sourceId: comic.sourceId
                    )
                    Button {
                        showChapterPicker = false
                        if idx != currentChapterIndex {
                            switchChapter(to: idx, proxy: proxy)
                        }
                    } label: {
                        HStack(spacing: AuroraMetrics.spacingM) {
                            Text("\(idx + 1)")
                                .font(.caption.monospacedDigit().bold())
                                .foregroundStyle(isCurrent ? AuroraColor.brand : AuroraColor.textTertiary)
                                .frame(width: 32, alignment: .leading)

                            Text(chapter.title)
                                .font(.body.weight(isCurrent ? .bold : .regular))
                                .foregroundStyle(isCurrent ? AuroraColor.brand : AuroraColor.textPrimary)
                                .lineLimit(2)

                            Spacer()

                            if isOffline {
                                Image(systemName: "arrow.down.circle.fill")
                                    .font(.caption)
                                    .foregroundStyle(AuroraColor.success)
                            }
                            if isCurrent {
                                Image(systemName: "checkmark")
                                    .font(.subheadline.bold())
                                    .foregroundStyle(AuroraColor.brand)
                            }
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("章节目录 (\(comic.chapters.count))")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.done".localized) {
                        showChapterPicker = false
                    }
                    .foregroundStyle(AuroraColor.brand)
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    private var shouldKeepControlsVisible: Bool {
        isLoading || loadError != nil || pages.isEmpty
    }

    private func restoreInitialProgressIfNeeded() {
        guard !didCheckInitialProgress else { return }
        didCheckInitialProgress = true
        guard restoreProgress,
              !comic.chapters.isEmpty,
              let saved = ComicReadingProgressStore.shared.progress(sourceId: comic.sourceId, comicId: comic.id) else {
            return
        }
        if let matchedIdx = comic.chapters.firstIndex(where: { $0.id == saved.chapterId }) {
            currentChapterIndex = matchedIdx
            pendingScrollPageNumber = saved.pageNumber
        } else if saved.chapterIndex >= 0 && saved.chapterIndex < comic.chapters.count {
            currentChapterIndex = saved.chapterIndex
            pendingScrollPageNumber = saved.pageNumber
        }
    }

    private func recordReadingProgress(pageNumber: Int) {
        currentPageNumber = max(1, pageNumber)
        flushCurrentProgress()
    }

    private func flushCurrentProgress() {
        guard let chapter = currentChapter else { return }
        ComicReadingProgressStore.shared.record(
            sourceId: comic.sourceId,
            comicId: comic.id,
            chapterId: chapter.id,
            chapterIndex: currentChapterIndex,
            pageNumber: currentPageNumber
        )
    }

    private func dismissReader() {
        flushCurrentProgress()
        pageLoadTask?.cancel()
        onDismiss()
    }

    private func switchChapter(to newIndex: Int, proxy: ScrollViewProxy) {
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        zoomScale = 1.0
        committedZoomScale = 1.0
        currentPageNumber = 1
        pendingScrollPageNumber = nil
        self.currentChapterIndex = newIndex

        // 关键踩坑经验：换章必须自动回顶，杜绝偏移量越界
        withAnimation(AuroraMotion.selection(reduceMotion: reduceMotion)) {
            proxy.scrollTo("top_anchor", anchor: .top)
        }
        loadPages()
        flushCurrentProgress()
    }

    private func loadPages() {
        pageLoadTask?.cancel()
        let requestID = UUID()
        activePageLoadID = requestID
        guard let chapter = currentChapter else {
            pages = []
            isLoading = false
            loadError = "此漫画来源尚未提供章节列表。"
            return
        }

        // 1. 优先离线优先拦截：若本章节已完整下载，直接从沙盒秒开，无视断网与弱网环境
        if downloadManager.isDownloaded(comicId: comic.id, chapterId: chapter.id, sourceId: comic.sourceId),
           let record = downloadManager.chapters.first(where: {
               $0.comicId == comic.id && $0.chapterId == chapter.id && ($0.sourceId == comic.sourceId || $0.sourceId.isEmpty)
           }),
           record.totalPages > 0 {
            let localPages = (0..<record.totalPages).map { idx in
                ComicPage(
                    imageUrl: downloadManager.localPageURL(
                        comicId: comic.id,
                        chapterId: chapter.id,
                        pageIndex: idx,
                        sourceId: comic.sourceId
                    )?.absoluteString ?? ""
                )
            }
            if !localPages.isEmpty {
                self.pages = localPages
                self.isLoading = false
                self.loadError = nil
                return
            }
        }

        // 2. 内存秒开拦截：若前置预热或历史已解析此章节，0ms 瞬间渲染
        if let cached = ComicRepository.shared.cachedChapter(sourceId: comic.sourceId, comicId: comic.id, chapterId: chapter.id), !cached.isEmpty {
            self.pages = cached
            self.isLoading = false
            self.loadError = nil
            prewarmNextChapterIfNeeded(after: chapter)
            return
        }

        self.pages = []
        self.isLoading = true
        loadError = nil
        pageLoadTask = Task {
            defer {
                if activePageLoadID == requestID {
                    isLoading = false
                    pageLoadTask = nil
                }
            }
            do {
                let loaded = try await ComicRepository.shared.fetchChapterPages(comicId: comic.id, sourceId: comic.sourceId, chapterId: chapter.id)
                guard !Task.isCancelled, activePageLoadID == requestID else { return }
                pages = loaded
                if loaded.isEmpty {
                    loadError = "此章节暂时没有可显示的图片。"
                } else {
                    prewarmNextChapterIfNeeded(after: chapter)
                }
            } catch {
                guard !Task.isCancelled, activePageLoadID == requestID else { return }
                // 网络失败时的本地兜底恢复
                if let record = downloadManager.chapters.first(where: {
                    $0.comicId == comic.id && $0.chapterId == chapter.id && ($0.sourceId == comic.sourceId || $0.sourceId.isEmpty)
                }),
                   record.downloadedPages > 0 {
                    let localPages = (0..<record.downloadedPages).map { idx in
                        ComicPage(
                            imageUrl: downloadManager.localPageURL(
                                comicId: comic.id,
                                chapterId: chapter.id,
                                pageIndex: idx,
                                sourceId: comic.sourceId
                            )?.absoluteString ?? ""
                        )
                    }
                    if !localPages.isEmpty {
                        self.pages = localPages
                        self.loadError = nil
                        return
                    }
                }
                loadError = error.localizedDescription
            }
        }
    }

    private func prewarmNextChapterIfNeeded(after currentChapter: ComicChapter) {
        guard let currentIndex = comic.chapters.firstIndex(where: { $0.id == currentChapter.id }),
              currentIndex + 1 < comic.chapters.count else { return }
        let nextChapter = comic.chapters[currentIndex + 1]
        ComicRepository.shared.prewarmChapter(sourceId: comic.sourceId, comicId: comic.id, chapterId: nextChapter.id)
    }
}
