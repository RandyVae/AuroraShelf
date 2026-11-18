import SwiftUI

/// 漫画阅读器缩放几何引擎（真实布局宽高放大，彻底杜绝 `.scaleEffect` 造成的左右边缘不可平移与下半章截断）
public enum ComicZoomGeometry {
    public static let minScale: CGFloat = 1.0
    public static let maxScale: CGFloat = 3.0

    public static func clampedScale(_ proposed: CGFloat) -> CGFloat {
        min(max(proposed, minScale), maxScale)
    }

    public static func snappedScale(_ proposed: CGFloat) -> CGFloat {
        let clamped = clampedScale(proposed)
        return clamped < 1.08 ? 1.0 : clamped
    }

    public static func contentWidth(viewportWidth: CGFloat, zoomScale: CGFloat) -> CGFloat {
        max(1, viewportWidth) * clampedScale(zoomScale)
    }

    /// 计算双击位置对应的目标滚动偏移（Zoom-to-Point），确保放大后左右与上下全范围可平移触达
    public static func targetOffsetForDoubleTap(
        tapLocationInViewport: CGPoint,
        currentOffset: CGPoint,
        viewportSize: CGSize,
        oldScale: CGFloat,
        newScale: CGFloat
    ) -> CGPoint {
        guard newScale > 1.01, oldScale > 0 else {
            return CGPoint(x: 0, y: max(0, currentOffset.y / max(oldScale, 1)))
        }
        let ratio = newScale / oldScale
        let contentPointX = currentOffset.x + tapLocationInViewport.x
        let contentPointY = currentOffset.y + tapLocationInViewport.y
        let maxOffsetX = max(0, viewportSize.width * newScale - viewportSize.width)
        let targetX = min(max(0, contentPointX * ratio - viewportSize.width * 0.5), maxOffsetX)
        let targetY = max(0, contentPointY * ratio - viewportSize.height * 0.5)
        return CGPoint(x: targetX, y: targetY)
    }
}

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
                    Text("reader.page_load_failed".localized(pageNumber))
                    Text(errorMessage)
                        .font(.caption)
                    Button("common.retry".localized, systemImage: "arrow.clockwise") {
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
    case oled = "oled"
    case parchment = "parchment"
    case charcoal = "charcoal"
    case paper = "paper"

    public var id: String { rawValue }

    public var displayTitle: String {
        switch self {
        case .oled: return "reader.theme_oled".localized
        case .parchment: return "reader.theme_parchment".localized
        case .charcoal: return "reader.theme_charcoal".localized
        case .paper: return "reader.theme_paper".localized
        }
    }

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
    @State private var scrollPosition = ScrollPosition()
    @State private var currentScrollOffset: CGPoint = .zero
    @ObservedObject private var downloadManager = ComicDownloadManager.shared
    @ObservedObject private var locManager = LocalizationManager.shared
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
            readerTheme.backgroundColor.ignoresSafeArea()

            GeometryReader { viewportGeo in
                let viewportSize = viewportGeo.size
                let contentWidth = ComicZoomGeometry.contentWidth(
                    viewportWidth: viewportSize.width,
                    zoomScale: zoomScale
                )

                ScrollViewReader { proxy in
                    ScrollView(zoomScale > 1.01 ? [.vertical, .horizontal] : .vertical, showsIndicators: false) {
                        LazyVStack(spacing: 0) {
                            Color.clear.frame(height: 1).id("top_anchor")

                            if let loadError {
                                ContentUnavailableView {
                                    Label("reader.chapter_unavailable".localized, systemImage: "wifi.exclamationmark")
                                } description: {
                                    Text(loadError)
                                } actions: {
                                    HStack(spacing: AuroraMetrics.spacingM) {
                                        Button("common.retry".localized, systemImage: "arrow.clockwise") {
                                            loadPages()
                                        }
                                        .buttonStyle(.borderedProminent)

                                        Button("reader.back_to_list".localized, systemImage: "chevron.backward") {
                                            dismissReader()
                                        }
                                        .buttonStyle(.bordered)
                                    }
                                }
                                .tint(AuroraColor.brandSolid)
                                .frame(width: viewportSize.width)
                                .frame(minHeight: 300)
                            } else if isLoading {
                                VStack(spacing: AuroraMetrics.spacingM) {
                                    ProgressView("comics.loading_chapters".localized)
                                    Button("reader.cancel_and_back".localized) {
                                        dismissReader()
                                    }
                                    .buttonStyle(.bordered)
                                    .tint(AuroraColor.brandLabel)
                                }
                                .frame(width: viewportSize.width)
                                .frame(minHeight: 300)
                            } else if pages.isEmpty {
                                ContentUnavailableView {
                                    Label("reader.empty_chapter".localized, systemImage: "photo")
                                } description: {
                                    Text("reader.empty_chapter_desc".localized)
                                } actions: {
                                    HStack(spacing: AuroraMetrics.spacingM) {
                                        Button("common.reload".localized, systemImage: "arrow.clockwise") {
                                            loadPages()
                                        }
                                        .buttonStyle(.borderedProminent)

                                        Button("reader.back_to_list".localized, systemImage: "chevron.backward") {
                                            dismissReader()
                                        }
                                        .buttonStyle(.bordered)
                                    }
                                }
                                .tint(AuroraColor.brandSolid)
                                .frame(width: viewportSize.width)
                                .frame(minHeight: 300)
                            } else {
                                if isTwoPageSpread && horizontalSizeClass == .regular {
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
                                        .frame(maxWidth: max(1080, contentWidth))
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

                            // 换章操作栏（始终居中在视口或放大内容底部，保证放大态亦可滑到底部点击下一话）
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
                                    .hoverEffect(.highlight)
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
                                            .background(AuroraColor.brandSolid, in: .capsule)
                                    }
                                    .buttonStyle(.plain)
                                    .hoverEffect(.highlight)
                                    .disabled(currentChapterIndex >= comic.chapters.count - 1)
                                }

                                Text("reader.end_of_chapter".localized)
                                    .font(.footnote)
                                    .foregroundStyle(readerTheme.isDark ? Color.white.opacity(0.6) : Color.black.opacity(0.55))
                            }
                            .padding(.vertical, AuroraMetrics.spacingXXL)
                            .frame(maxWidth: .infinity)
                        }
                        .frame(width: contentWidth)
                    }
                    .scrollPosition($scrollPosition)
                    .scrollBounceBehavior(.basedOnSize, axes: [.vertical, .horizontal])
                    .onScrollGeometryChange(for: CGPoint.self) { geo in
                        geo.contentOffset
                    } action: { _, newOffset in
                        currentScrollOffset = newOffset
                    }
                    .simultaneousGesture(
                        MagnifyGesture()
                            .onChanged { value in
                                zoomScale = ComicZoomGeometry.clampedScale(committedZoomScale * value.magnification)
                            }
                            .onEnded { value in
                                let snapped = ComicZoomGeometry.snappedScale(committedZoomScale * value.magnification)
                                AuroraMotion.perform(AuroraMotion.quick, reduceMotion: reduceMotion) {
                                    zoomScale = snapped
                                    if snapped == 1.0 {
                                        scrollPosition.scrollTo(x: 0)
                                    }
                                }
                                committedZoomScale = snapped
                            }
                    )
                    .simultaneousGesture(
                        SpatialTapGesture(count: 2)
                            .onEnded { event in
                                let oldScale = zoomScale
                                let nextScale: CGFloat = oldScale > 1.05 ? 1.0 : 2.0
                                let targetOffset = ComicZoomGeometry.targetOffsetForDoubleTap(
                                    tapLocationInViewport: event.location,
                                    currentOffset: currentScrollOffset,
                                    viewportSize: viewportSize,
                                    oldScale: oldScale,
                                    newScale: nextScale
                                )
                                AuroraMotion.perform(AuroraMotion.quick, reduceMotion: reduceMotion) {
                                    zoomScale = nextScale
                                    committedZoomScale = nextScale
                                    scrollPosition.scrollTo(point: targetOffset)
                                }
                            }
                    )
                    .onTapGesture(count: 1) {
                        guard !shouldKeepControlsVisible else { return }
                        AuroraMotion.perform(AuroraMotion.quick, reduceMotion: reduceMotion) {
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
            }

            VStack {
                if showControls || shouldKeepControlsVisible {
                    PlayerOverlayBar(
                        title: comic.title,
                        subtitle: currentChapter.map {
                            pages.isEmpty
                                ? "reader.webtoon_flow".localized($0.title)
                                : "\($0.title) · \("reader.page".localized(currentPageNumber, pages.count))"
                        } ?? "reader.chapter_unavailable".localized,
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
                                        .glassEffect(.clear.interactive(), in: .circle)
                                }
                                .buttonStyle(.plain)
                                .hoverEffect(.highlight)
                                .accessibilityLabel("reader.toc_a11y".localized)
                            }

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
                                            Label("reader.delete_chapter_cache".localized, systemImage: "trash")
                                        }
                                    } else {
                                        Button {
                                            downloadManager.startDownload(comic: comic, chapter: chapter)
                                        } label: {
                                            Label("reader.download_chapter".localized, systemImage: "arrow.down.circle")
                                        }
                                    }

                                    Button {
                                        downloadManager.startDownloadAll(comic: comic)
                                    } label: {
                                        Label("reader.download_all_chapters".localized(comic.chapters.count), systemImage: "arrow.down.circle.fill")
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
                                    .glassEffect(.clear.interactive(), in: .circle)
                                }
                                .accessibilityLabel("reader.offline_a11y".localized)
                            }

                            if horizontalSizeClass == .regular {
                                Button {
                                    AuroraMotion.perform(AuroraMotion.quick, reduceMotion: reduceMotion) {
                                        isTwoPageSpread.toggle()
                                    }
                                } label: {
                                    Image(systemName: isTwoPageSpread ? "book.closed" : "book")
                                        .font(.subheadline.bold())
                                        .foregroundStyle(.white)
                                        .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                                        .glassEffect(.clear.interactive(), in: .circle)
                                }
                                .buttonStyle(.plain)
                                .hoverEffect(.highlight)
                                .accessibilityLabel(isTwoPageSpread ? "reader.single_page_mode".localized : "reader.spread_mode".localized)
                            }

                            Menu {
                                ForEach(ComicReaderTheme.allCases) { theme in
                                    Button {
                                        readerTheme = theme
                                    } label: {
                                        HStack {
                                            Text(theme.displayTitle)
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
                                    .glassEffect(.clear.interactive(), in: .circle)
                            }
                            .accessibilityLabel("reader.theme_a11y".localized(readerTheme.displayTitle))
                        }
                    }
                    .transition(.opacity)
                }

                Spacer()

                if showControls && !shouldKeepControlsVisible {
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
                            } ?? "reader.chapter_unavailable".localized)
                                .font(.subheadline)
                            if zoomScale > 1.05 {
                                Text(String(format: "%.1fx", zoomScale))
                                    .font(.caption2.monospacedDigit().bold())
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(AuroraColor.brandSolid, in: Capsule())
                            }
                        }
                        .foregroundStyle(.white)
                        .padding(.horizontal, AuroraMetrics.spacingL)
                        .frame(height: AuroraMetrics.minTouchTarget)
                        .glassEffect(.clear.interactive(), in: .capsule)
                    }
                    .buttonStyle(.plain)
                    .hoverEffect(.highlight)
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
        .enableSwipeBack(isEnabled: zoomScale <= 1.02, allowsContentSwipe: false) {
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
                                .foregroundStyle(isCurrent ? AuroraColor.brandLabel : AuroraColor.textTertiary)
                                .frame(width: 32, alignment: .leading)

                            Text(chapter.title)
                                .font(.body.weight(isCurrent ? .bold : .regular))
                                .foregroundStyle(isCurrent ? AuroraColor.brandLabel : AuroraColor.textPrimary)
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
                                    .foregroundStyle(AuroraColor.brandLabel)
                            }
                        }
                        .frame(minHeight: AuroraMetrics.minTouchTarget)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("reader.toc_nav_title".localized(comic.chapters.count))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.done".localized) {
                        showChapterPicker = false
                    }
                    .foregroundStyle(AuroraColor.brandLabel)
                    .frame(minWidth: AuroraMetrics.minTouchTarget, minHeight: AuroraMetrics.minTouchTarget)
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
        AuroraHaptics.medium()
        zoomScale = 1.0
        committedZoomScale = 1.0
        currentPageNumber = 1
        pendingScrollPageNumber = nil
        self.currentChapterIndex = newIndex

        AuroraMotion.perform(AuroraMotion.quick, reduceMotion: reduceMotion) {
            scrollPosition.scrollTo(point: .zero)
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
            loadError = "reader.no_chapters_source".localized
            return
        }

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
                    loadError = "reader.empty_chapter_desc".localized
                } else {
                    prewarmNextChapterIfNeeded(after: chapter)
                }
            } catch {
                guard !Task.isCancelled, activePageLoadID == requestID else { return }
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
