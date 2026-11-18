import SwiftUI

/// Apple TV 风格的二级分类专区页（Category Detail Screen）
public struct CategoryDetailScreen: View {
    public let source: VideoSourceDefinition
    public let initialCategory: String
    public let onPlayVideo: (VideoItem) -> Void
    public let onOpenDetail: (VideoItem) -> Void

    public enum SortOption: String, CaseIterable, Identifiable {
        case `default` = "default"
        case latest = "latest"
        case rating = "rating"
        public var id: String { rawValue }

        public var displayTitle: String {
            switch self {
            case .default: return "category.sort_default".localized
            case .latest: return "category.sort_latest".localized
            case .rating: return "category.sort_rating".localized
            }
        }
    }

    @ObservedObject private var locManager = LocalizationManager.shared
    @State private var selectedCategory: String
    @State private var sortOption: SortOption = .default
    @State private var videos: [VideoItem] = []
    @State private var isLoading: Bool = true
    @State private var isLoadingNextPage: Bool = false
    @State private var canLoadMore: Bool = true
    @State private var currentPage: Int = 1
    @State private var errorMessage: String? = nil
    @State private var loadMoreError: String? = nil
    @State private var selectedDetailVideo: VideoItem? = nil

    @Environment(\.auroraContainerSize) private var containerSize
    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var availableCategories: [String] {
        source.categories.isEmpty ? [initialCategory] : source.categories
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
        let form = DeviceLayout.resolveCurrent(containerSize: containerSize, horizontalSizeClass: horizontalSizeClass)
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
                    if availableCategories.count > 1 {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: AuroraMetrics.spacingS) {
                                ForEach(availableCategories, id: \.self) { cat in
                                    let isSelected = (cat == selectedCategory)
                                    Button {
                                        if selectedCategory != cat {
                                            AuroraMotion.perform(AuroraMotion.quick, reduceMotion: reduceMotion) {
                                                selectedCategory = cat
                                            }
                                            loadFirstPage()
                                        }
                                    } label: {
                                        Text(cat.localizedCategoryLabel)
                                            .font(.subheadline)
                                            .fontWeight(isSelected ? .bold : .medium)
                                            .padding(.horizontal, 16)
                                            .padding(.vertical, 8)
                                            .frame(minHeight: AuroraMetrics.minTouchTarget)
                                            .background(
                                                isSelected ? AuroraColor.brandSolid : AuroraColor.surfaceCard,
                                                in: Capsule()
                                            )
                                            .foregroundStyle(isSelected ? .white : AuroraColor.textPrimary)
                                    }
                                    .buttonStyle(.plain)
                                    .hoverEffect(.highlight)
                                }
                            }
                            .padding(.horizontal, AuroraMetrics.contentInset)
                            .padding(.top, AuroraMetrics.spacingS)
                        }
                        .sensoryFeedback(.selection, trigger: selectedCategory)
                    }

                    if isLoading && videos.isEmpty {
                        LazyVGrid(columns: gridColumns, spacing: AuroraMetrics.spacingL) {
                            ForEach(0..<6, id: \.self) { _ in
                                VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                                        .fill(AuroraColor.surfaceCard)
                                        .aspectRatio(AuroraMetrics.posterAspect, contentMode: .fit)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                                .stroke(AuroraColor.hairline, lineWidth: 1)
                                        )
                                    RoundedRectangle(cornerRadius: 4)
                                        .fill(AuroraColor.surfaceHigh)
                                        .frame(height: 14)
                                        .frame(maxWidth: .infinity)
                                    RoundedRectangle(cornerRadius: 4)
                                        .fill(AuroraColor.surfaceCard)
                                        .frame(width: 80, height: 10)
                                }
                            }
                        }
                        .padding(.horizontal, AuroraMetrics.contentInset)
                        .padding(.vertical, 8)
                    } else if let error = errorMessage, videos.isEmpty {
                        ContentUnavailableView {
                            Label("category.load_failed".localized, systemImage: "wifi.exclamationmark")
                        } description: {
                            Text(error)
                        } actions: {
                            Button("category.reconnect".localized, systemImage: "arrow.clockwise") {
                                loadFirstPage()
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(AuroraColor.brandSolid)
                        }
                        .padding(.vertical, 60)
                    } else {
                        VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                            LazyVGrid(columns: gridColumns, spacing: AuroraMetrics.spacingL) {
                                ForEach(Array(sortedVideos.enumerated()), id: \.element.id) { idx, video in
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
                                        if idx >= max(0, sortedVideos.count - 4) {
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
                                        .tint(AuroraColor.brandLabel)
                                    Text("home.loading_more".localized)
                                        .font(.footnote)
                                        .foregroundStyle(AuroraColor.textSecondary)
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, AuroraMetrics.spacingM)
                            } else if let loadMoreError {
                                Button {
                                    self.loadMoreError = nil
                                    loadNextPage()
                                } label: {
                                    Label("category.load_more_retry".localized(loadMoreError), systemImage: "arrow.clockwise")
                                        .font(.footnote.weight(.medium))
                                        .foregroundStyle(AuroraColor.brandLabel)
                                        .frame(minHeight: AuroraMetrics.minTouchTarget)
                                }
                                .buttonStyle(.plain)
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

                    Spacer().frame(height: 80)
                }
            }
            .refreshable {
                await reloadAsync()
            }
        }
        .navigationTitle("\(selectedCategory.localizedCategoryLabel) · \(source.name)")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    ForEach(SortOption.allCases) { opt in
                        Button {
                            AuroraMotion.perform(AuroraMotion.subtle, reduceMotion: reduceMotion) {
                                sortOption = opt
                            }
                        } label: {
                            HStack {
                                Text(opt.displayTitle)
                                if sortOption == opt {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                    }
                } label: {
                    Image(systemName: "arrow.up.arrow.down")
                        .font(.subheadline.bold())
                        .foregroundStyle(AuroraColor.textPrimary)
                        .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                        .contentShape(Rectangle())
                }
                .hoverEffect(.highlight)
                .accessibilityLabel("category.sort_a11y".localized(sortOption.displayTitle))
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
        loadMoreError = nil
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
        guard canLoadMore, !isLoading, !isLoadingNextPage, loadMoreError == nil else { return }
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
                    self.loadMoreError = nil
                    self.isLoadingNextPage = false
                }
            } catch {
                await MainActor.run {
                    guard self.selectedCategory == targetCategory else { return }
                    self.loadMoreError = error.localizedDescription
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
