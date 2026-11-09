import SwiftUI

/// Apple TV 风格的二级分类专区页（Category Detail Screen）
/// 承接用户从主页货架点击「分类标题 ›」或「全部 ›」之后的深层浏览需求；
/// 提供细分品类切换 Chip、自适应海报网格与无限上拉分页流。
public struct CategoryDetailScreen: View {
    public let source: VideoSourceDefinition
    public let initialCategory: String
    public let onPlayVideo: (VideoItem) -> Void
    public let onOpenDetail: (VideoItem) -> Void

    public enum SortOption: String, CaseIterable, Identifiable {
        case `default` = "推荐排序"
        case latest = "最新上架"
        case rating = "高分优选"
        public var id: String { rawValue }
    }

    @State private var selectedCategory: String
    @State private var sortOption: SortOption = .default
    @State private var videos: [VideoItem] = []
    @State private var isLoading: Bool = true
    @State private var isLoadingNextPage: Bool = false
    @State private var canLoadMore: Bool = true
    @State private var currentPage: Int = 1
    @State private var errorMessage: String? = nil
    @State private var selectedDetailVideo: VideoItem? = nil

    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    private var availableCategories: [String] {
        let subs = source.categories.filter { $0 != "全部" }
        return subs.isEmpty ? [initialCategory] : subs
    }

    private var sortedVideos: [VideoItem] {
        switch sortOption {
        case .default:
            return videos
        case .latest:
            return videos.reversed()
        case .rating:
            return videos.sorted { (Double($0.rating) ?? 0) > (Double($1.rating) ?? 0) }
        }
    }

    private var gridColumns: [GridItem] {
        let form = DeviceLayout.resolveCurrent(horizontalSizeClass: horizontalSizeClass)
        let baseMin = DeviceLayout.gridMinimumWidth(for: form)
        let minimumWidth: CGFloat = dynamicTypeSize.isAccessibilitySize ? max(baseMin, 260) : baseMin
        return [GridItem(.adaptive(minimum: minimumWidth, maximum: 340), spacing: AuroraMetrics.spacingM)]
    }

    public init(
        source: VideoSourceDefinition,
        initialCategory: String,
        onPlayVideo: @escaping (VideoItem) -> Void,
        onOpenDetail: @escaping (VideoItem) -> Void
    ) {
        self.source = source
        self.initialCategory = initialCategory
        self.onPlayVideo = onPlayVideo
        self.onOpenDetail = onOpenDetail
        _selectedCategory = State(initialValue: initialCategory)
    }

    public var body: some View {
        ZStack {
            AuroraColor.canvas.ignoresSafeArea()

            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: AuroraMetrics.spacingL) {
                    // 1. 分类细分子标签切换栏（支持在专区内快捷跳转其他板块）
                    if availableCategories.count > 1 {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: AuroraMetrics.spacingS) {
                                ForEach(availableCategories, id: \.self) { cat in
                                    let isSelected = (cat == selectedCategory)
                                    Button {
                                        if selectedCategory != cat {
                                            selectedCategory = cat
                                            loadFirstPage()
                                        }
                                    } label: {
                                        Text(cat)
                                            .font(.subheadline)
                                            .fontWeight(isSelected ? .bold : .medium)
                                            .padding(.horizontal, 16)
                                            .padding(.vertical, 8)
                                            .background(
                                                isSelected ? AuroraColor.brand : AuroraColor.surfaceCard,
                                                in: Capsule()
                                            )
                                            .foregroundStyle(isSelected ? .white : AuroraColor.textPrimary)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(.horizontal, AuroraMetrics.contentInset)
                            .padding(.top, AuroraMetrics.spacingS)
                        }
                    }

                    // 2. 视频内容网格
                    if isLoading && videos.isEmpty {
                        // Apple TV 原生骨架屏（Poster Shimmer Skeleton）
                        LazyVGrid(columns: gridColumns, spacing: AuroraMetrics.spacingL) {
                            ForEach(0..<6, id: \.self) { _ in
                                VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                                        .fill(Color.white.opacity(0.08))
                                        .aspectRatio(AuroraMetrics.posterAspect, contentMode: .fit)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                                .stroke(Color.white.opacity(0.06), lineWidth: 1)
                                        )
                                    RoundedRectangle(cornerRadius: 4)
                                        .fill(Color.white.opacity(0.12))
                                        .frame(height: 14)
                                        .frame(maxWidth: .infinity)
                                    RoundedRectangle(cornerRadius: 4)
                                        .fill(Color.white.opacity(0.08))
                                        .frame(width: 80, height: 10)
                                }
                            }
                        }
                        .padding(.horizontal, AuroraMetrics.contentInset)
                        .padding(.vertical, 8)
                    } else if let error = errorMessage, videos.isEmpty {
                        ContentUnavailableView {
                            Label("无法加载内容", systemImage: "wifi.exclamationmark")
                        } description: {
                            Text(error)
                        } actions: {
                            Button("重新连接", systemImage: "arrow.clockwise") {
                                loadFirstPage()
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(AuroraColor.brand)
                        }
                        .padding(.vertical, 60)
                    } else {
                        VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                            LazyVGrid(columns: gridColumns, spacing: AuroraMetrics.spacingL) {
                                ForEach(sortedVideos) { video in
                                    PosterCard(
                                        video: video,
                                        onSelect: {
                                            onPlayVideo(video)
                                        },
                                        onDetail: {
                                            selectedDetailVideo = video
                                        }
                                    )
                                    .onAppear {
                                        if video.id == videos.last?.id {
                                            loadNextPage()
                                        }
                                    }
                                }
                            }
                            .padding(.horizontal, AuroraMetrics.contentInset)

                            if isLoadingNextPage {
                                HStack(spacing: AuroraMetrics.spacingM) {
                                    ProgressView()
                                        .controlSize(.regular)
                                        .tint(AuroraColor.brand)
                                    Text("home.loading_more".localized)
                                        .font(.footnote)
                                        .foregroundStyle(AuroraColor.textSecondary)
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, AuroraMetrics.spacingM)
                            } else if !canLoadMore && !videos.isEmpty {
                                Text("home.all_loaded".localized)
                                    .font(.caption)
                                    .foregroundStyle(AuroraColor.textTertiary)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, AuroraMetrics.spacingM)
                            }
                        }
                        .frame(maxWidth: 1080)
                        .frame(maxWidth: .infinity)
                    }

                    Spacer().frame(height: 60)
                }
            }
            .refreshable {
                await reloadAsync()
            }
        }
        .navigationTitle("\(selectedCategory) · \(source.name)")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.regularMaterial, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    ForEach(SortOption.allCases) { opt in
                        Button {
                            withAnimation(.easeInOut(duration: 0.2)) {
                                sortOption = opt
                            }
                        } label: {
                            HStack {
                                Text(opt.rawValue)
                                if sortOption == opt {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                    }
                } label: {
                    Image(systemName: "arrow.up.arrow.down")
                        .font(.caption.bold())
                        .foregroundStyle(AuroraColor.textPrimary)
                        .frame(width: 32, height: 32)
                        .background(AuroraColor.surfaceCard, in: Circle())
                }
                .accessibilityLabel("排序方式，当前：\(sortOption.rawValue)")
            }
        }
        .sheet(item: $selectedDetailVideo) { video in
            VideoDetailSheet(
                video: video,
                onPlay: onPlayVideo,
                onDismiss: { selectedDetailVideo = nil }
            )
        }
        .enableSwipeBack {
            dismiss()
        }
        .onAppear {
            if videos.isEmpty {
                loadFirstPage()
            }
        }
    }

    private func loadFirstPage() {
        isLoading = true
        errorMessage = nil
        currentPage = 1
        canLoadMore = true

        let targetCategory = selectedCategory
        let targetSource = source

        Task {
            do {
                let fetched = try await VideoRepository.shared.fetchCategoryVideos(source: targetSource, category: targetCategory, page: 1)
                await MainActor.run {
                    guard self.selectedCategory == targetCategory else { return }
                    self.videos = fetched
                    self.isLoading = false
                    self.canLoadMore = !fetched.isEmpty
                }
            } catch {
                await MainActor.run {
                    guard self.selectedCategory == targetCategory else { return }
                    self.errorMessage = error.localizedDescription
                    self.isLoading = false
                }
            }
        }
    }

    private func loadNextPage() {
        guard canLoadMore, !isLoading, !isLoadingNextPage else { return }
        isLoadingNextPage = true
        let targetCategory = selectedCategory
        let targetSource = source
        let nextPage = currentPage + 1

        Task {
            do {
                let fetched = try await VideoRepository.shared.fetchCategoryVideos(source: targetSource, category: targetCategory, page: nextPage)
                await MainActor.run {
                    guard self.selectedCategory == targetCategory else { return }
                    let existing = Set(self.videos.map(\.id))
                    let uniqueNew = fetched.filter { !existing.contains($0.id) }
                    if uniqueNew.isEmpty {
                        self.canLoadMore = false
                    } else {
                        self.videos.append(contentsOf: uniqueNew)
                        self.currentPage = nextPage
                        self.canLoadMore = !fetched.isEmpty
                    }
                    self.isLoadingNextPage = false
                }
            } catch {
                await MainActor.run {
                    self.isLoadingNextPage = false
                }
            }
        }
    }

    private func reloadAsync() async {
        let targetCategory = selectedCategory
        let targetSource = source
        do {
            let fetched = try await VideoRepository.shared.fetchCategoryVideos(source: targetSource, category: targetCategory, page: 1)
            await MainActor.run {
                guard self.selectedCategory == targetCategory else { return }
                self.videos = fetched
                self.currentPage = 1
                self.canLoadMore = !fetched.isEmpty
            }
        } catch {
            // retain existing on network failure
        }
    }
}
