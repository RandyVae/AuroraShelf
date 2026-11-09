import SwiftUI

public struct SearchSheetView: View {
    public let onPlayVideo: (VideoItem) -> Void
    public let onDismiss: () -> Void
    
    @ObservedObject private var repo = VideoRepository.shared
    @ObservedObject private var watchlist = WatchlistStore.shared
    @State private var query: String = ""
    @State private var searchTask: Task<Void, Never>?
    @FocusState private var isSearchFocused: Bool
    @AppStorage("recent_video_searches") private var recentSearchesRaw: String = ""
    @State private var selectedSourceFilter: String = "全部"
    @State private var isGridView: Bool = false
    @State private var selectedDetailVideo: VideoItem? = nil

    private let trendingKeywords = [
        "4K 超清", "最新电影", "热门连续剧", "经典港片",
        "热血动漫", "高分综艺", "悬疑惊悚", "科幻大片"
    ]

    private var recentSearches: [String] {
        recentSearchesRaw
            .split(separator: "||")
            .map { String($0).trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }

    private var searchableSourceCount: Int {
        VideoSourceCatalog.searchableSources.count
    }

    private var availableSources: [String] {
        var sources = ["全部"]
        let uniqueSources = Set(repo.searchResults.map { $0.sourceName }).sorted()
        sources.append(contentsOf: uniqueSources)
        return sources
    }

    private var filteredResults: [VideoItem] {
        if selectedSourceFilter == "全部" {
            return repo.searchResults
        }
        return repo.searchResults.filter { $0.sourceName == selectedSourceFilter }
    }

    private var resultShelves: [(title: String, items: [VideoItem])] {
        let items = filteredResults
        guard !items.isEmpty else { return [] }

        var shelves: [(title: String, items: [VideoItem])] = []
        
        // 1. 热门最佳匹配（Top Results - 前 4 部）
        let topCount = min(4, items.count)
        let topResults = Array(items.prefix(topCount))
        shelves.append((title: "热门匹配", items: topResults))

        // 2. 按分类归集横向货架
        let categoryGroups = Dictionary(grouping: items) { item -> String in
            let cat = item.category.trimmingCharacters(in: .whitespacesAndNewlines)
            return cat.isEmpty ? "精选推荐" : cat
        }

        if categoryGroups.count > 1 {
            for (cat, groupItems) in categoryGroups.sorted(by: { $0.value.count > $1.value.count }) {
                shelves.append((title: cat, items: groupItems))
            }
        } else {
            // 若为同分类，按视频源聚合货架
            let sourceGroups = Dictionary(grouping: items) { $0.sourceName }
            if sourceGroups.count > 1 {
                for (source, groupItems) in sourceGroups.sorted(by: { $0.value.count > $1.value.count }) {
                    shelves.append((title: "\(source) 结果", items: groupItems))
                }
            } else {
                shelves.append((title: "全部精选", items: items))
            }
        }

        return shelves
    }
    
    public init(
        initialQuery: String = "",
        onPlayVideo: @escaping (VideoItem) -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self._query = State(initialValue: initialQuery)
        self.onPlayVideo = onPlayVideo
        self.onDismiss = onDismiss
    }
    
    public var body: some View {
        NavigationStack {
            Group {
                if repo.isSearching {
                    VStack(spacing: AuroraMetrics.spacingL) {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: AuroraColor.brand))
                            .scaleEffect(1.3)
                        
                        Text("正在搜索 \(searchableSourceCount) 个已接入视频源…")
                            .font(.subheadline)
                            .foregroundStyle(Color.secondary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if !repo.searchResults.isEmpty {
                    // Apple TV 标志性分类货架流搜索结果呈现
                    ScrollView(.vertical, showsIndicators: false) {
                        VStack(alignment: .leading, spacing: AuroraMetrics.spacingXL) {
                            // 1. 顶部分源过滤栏与布局切换胶囊
                            HStack {
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: AuroraMetrics.spacingS) {
                                        ForEach(availableSources, id: \.self) { src in
                                            let isSelected = (selectedSourceFilter == src)
                                            Button {
                                                selectedSourceFilter = src
                                            } label: {
                                                HStack(spacing: 4) {
                                                    Text(src)
                                                        .font(.caption.bold())
                                                    let count = src == "全部" ? repo.searchResults.count : repo.searchResults.filter { $0.sourceName == src }.count
                                                    Text("\(count)")
                                                        .font(.system(size: 10, weight: .bold))
                                                        .padding(.horizontal, 4)
                                                        .padding(.vertical, 1)
                                                        .background(isSelected ? Color.white.opacity(0.25) : Color.black.opacity(0.15), in: Capsule())
                                                }
                                                .padding(.horizontal, 10)
                                                .padding(.vertical, 6)
                                                .background(
                                                    isSelected ? AuroraColor.brand : AuroraColor.surfaceCard,
                                                    in: Capsule()
                                                )
                                                .foregroundStyle(isSelected ? .white : AuroraColor.textPrimary)
                                            }
                                            .buttonStyle(.plain)
                                        }
                                    }
                                }
                                
                                Spacer()
                                
                                // 布局切换胶囊（货架流 vs 网格）
                                Button {
                                    withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                                        isGridView.toggle()
                                    }
                                } label: {
                                    Image(systemName: isGridView ? "rectangle.grid.2x2" : "rectangle.stack")
                                        .font(.caption.bold())
                                        .foregroundStyle(AuroraColor.textPrimary)
                                        .frame(width: 32, height: 32)
                                        .background(AuroraColor.surfaceCard, in: Circle())
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(isGridView ? "切换为货架视图" : "切换为网格视图")
                            }
                            .padding(.horizontal, AuroraMetrics.contentInset)
                            .padding(.top, AuroraMetrics.spacingS)

                            if let searchWarning = repo.searchError {
                                HStack(spacing: 6) {
                                    Image(systemName: "exclamationmark.triangle.fill")
                                        .font(.caption)
                                        .foregroundStyle(AuroraColor.warning)
                                    Text(searchWarning)
                                        .font(.caption)
                                        .foregroundStyle(AuroraColor.textSecondary)
                                }
                                .padding(.horizontal, AuroraMetrics.contentInset)
                            }

                            if isGridView {
                                // 网格视图
                                LazyVGrid(
                                    columns: [GridItem(.adaptive(minimum: 110, maximum: 160), spacing: AuroraMetrics.spacingM)],
                                    spacing: AuroraMetrics.spacingL
                                ) {
                                    ForEach(filteredResults) { item in
                                        searchCard(item: item, width: nil)
                                    }
                                }
                                .padding(.horizontal, AuroraMetrics.contentInset)
                            } else {
                                // Apple TV 分类货架流（横滑货架）
                                ForEach(resultShelves, id: \.title) { shelf in
                                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                                        HStack {
                                            Text(shelf.title)
                                                .font(.title3)
                                                .fontWeight(.bold)
                                                .foregroundStyle(AuroraColor.textPrimary)
                                            
                                            Spacer()
                                            
                                            Text("共 \(shelf.items.count) 部")
                                                .font(.caption.bold())
                                                .foregroundStyle(AuroraColor.brand)
                                        }
                                        .padding(.horizontal, AuroraMetrics.contentInset)

                                        ScrollView(.horizontal, showsIndicators: false) {
                                            HStack(spacing: AuroraMetrics.spacingM) {
                                                ForEach(shelf.items) { item in
                                                    searchCard(item: item, width: 130)
                                                }
                                            }
                                            .padding(.horizontal, AuroraMetrics.contentInset)
                                        }
                                    }
                                }
                            }

                            Spacer().frame(height: 60)
                        }
                    }
                } else if !query.isEmpty, let searchError = repo.searchError {
                    ContentUnavailableView {
                        Label("搜索失败", systemImage: "wifi.exclamationmark")
                    } description: {
                        Text(searchError)
                    } actions: {
                        Button("重试", systemImage: "arrow.clockwise") {
                            submitSearch()
                        }
                        .buttonStyle(.borderedProminent)
                    }
                } else if !query.isEmpty {
                    ContentUnavailableView.search(text: query)
                } else {
                    // Apple TV 标志性搜索落地页：热门推荐探索 + 最近搜索足迹
                    ScrollView(.vertical, showsIndicators: false) {
                        VStack(alignment: .leading, spacing: AuroraMetrics.spacingXL) {
                            // 1. 最近搜索足迹
                            if !recentSearches.isEmpty {
                                VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                                    HStack {
                                        Text("最近搜索")
                                            .font(.headline)
                                            .foregroundStyle(AuroraColor.textPrimary)
                                        Spacer()
                                        Button("清除") {
                                            clearRecentSearches()
                                        }
                                        .font(.subheadline)
                                        .foregroundStyle(AuroraColor.brand)
                                    }

                                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 100), spacing: 8)], spacing: 8) {
                                        ForEach(recentSearches, id: \.self) { item in
                                            Button {
                                                query = item
                                                submitSearch()
                                            } label: {
                                                HStack(spacing: 5) {
                                                    Image(systemName: "clock.arrow.circlepath")
                                                        .font(.caption2)
                                                    Text(item)
                                                        .font(.subheadline)
                                                        .lineLimit(1)
                                                }
                                                .padding(.horizontal, 12)
                                                .padding(.vertical, 8)
                                                .frame(maxWidth: .infinity)
                                                .background(AuroraColor.surfaceCard, in: Capsule())
                                                .overlay(Capsule().stroke(AuroraColor.hairline, lineWidth: 1))
                                                .foregroundStyle(AuroraColor.textSecondary)
                                            }
                                            .buttonStyle(.plain)
                                        }
                                    }
                                }
                            }

                            // 2. 热门推荐探索货架
                            VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                                Text("热门探索")
                                    .font(.headline)
                                    .foregroundStyle(AuroraColor.textPrimary)

                                LazyVGrid(columns: [GridItem(.adaptive(minimum: 110), spacing: 8)], spacing: 8) {
                                    ForEach(trendingKeywords, id: \.self) { keyword in
                                        Button {
                                            query = keyword
                                            submitSearch()
                                        } label: {
                                            HStack(spacing: 5) {
                                                Image(systemName: "sparkle")
                                                    .font(.caption2)
                                                    .foregroundStyle(AuroraColor.brand)
                                                Text(keyword)
                                                    .font(.subheadline.weight(.medium))
                                                    .lineLimit(1)
                                            }
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 9)
                                            .frame(maxWidth: .infinity)
                                            .background(AuroraColor.surfaceCard, in: Capsule())
                                            .overlay(Capsule().stroke(AuroraColor.hairline, lineWidth: 1))
                                            .foregroundStyle(AuroraColor.textPrimary)
                                        }
                                        .buttonStyle(AppleSpringCardButtonStyle())
                                    }
                                }
                            }

                            // 3. 全域聚合说明
                            HStack(spacing: 6) {
                                Image(systemName: "network")
                                    .font(.caption)
                                    .foregroundStyle(AuroraColor.textTertiary)
                                Text("全域聚合搜索覆盖 \(searchableSourceCount) 个视频源")
                                    .font(.caption)
                                    .foregroundStyle(AuroraColor.textTertiary)
                            }
                            .padding(.top, AuroraMetrics.spacingS)
                        }
                        .padding(.horizontal, AuroraMetrics.contentInset)
                        .padding(.top, AuroraMetrics.spacingL)
                    }
                }
            }
            .background(AuroraColor.canvas.ignoresSafeArea())
            .navigationTitle("search.placeholder".localized)
            .navigationBarTitleDisplayMode(.inline)
            .searchable(
                text: $query,
                placement: .navigationBarDrawer(displayMode: .always),
                prompt: "search.placeholder".localized
            )
            .onSubmit(of: .search) {
                submitSearch()
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.done".localized) {
                        onDismiss()
                    }
                    .font(.body.bold())
                    .foregroundStyle(AuroraColor.brand)
                    .frame(minWidth: 44, minHeight: 44)
                    .contentShape(Rectangle())
                }
            }
            .sheet(item: $selectedDetailVideo) { video in
                VideoDetailSheet(
                    video: video,
                    onPlay: { v in
                        selectedDetailVideo = nil
                        onPlayVideo(v)
                        onDismiss()
                    },
                    onDismiss: {
                        selectedDetailVideo = nil
                    }
                )
            }
            .onAppear {
                if !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    submitSearch()
                }
            }
            .onDisappear {
                searchTask?.cancel()
            }
        }
    }

    @ViewBuilder
    private func searchCard(item: VideoItem, width: CGFloat?) -> some View {
        Button {
            selectedDetailVideo = item
        } label: {
            VStack(alignment: .leading, spacing: 6) {
                ZStack(alignment: .topTrailing) {
                    AuroraCoverImage(
                        url: item.coverUrl,
                        title: item.title,
                        sourceBaseURL: item.coverBaseURL,
                        aspectRatio: AuroraMetrics.posterAspect,
                        contentMode: .fill,
                        cornerRadius: 10
                    )
                    .frame(width: width, height: width != nil ? (width! * 1.5) : nil)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .stroke(AuroraColor.hairline, lineWidth: 1)
                    )

                    // 视频源微型胶囊
                    Text(item.sourceName)
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(AuroraColor.brandLabel)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.black.opacity(0.65), in: Capsule())
                        .padding(6)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(item.title)
                        .font(.caption.bold())
                        .foregroundStyle(AuroraColor.textPrimary)
                        .lineLimit(1)

                    HStack(spacing: 4) {
                        if !item.rating.isEmpty {
                            HStack(spacing: 2) {
                                Image(systemName: "star.fill")
                                    .font(.system(size: 8))
                                    .foregroundStyle(.yellow)
                                Text(item.rating)
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundStyle(.white)
                            }
                        }

                        if !item.duration.isEmpty {
                            Text(item.duration)
                                .font(.system(size: 9))
                                .foregroundStyle(AuroraColor.textTertiary)
                        }
                    }
                }
                .frame(width: width, alignment: .leading)
            }
        }
        .buttonStyle(AppleSpringCardButtonStyle())
        .contextMenu {
            Button {
                onPlayVideo(item)
                onDismiss()
            } label: {
                Label("立即播放", systemImage: "play.fill")
            }

            let isBookmarked = watchlist.contains(item)
            Button {
                try? watchlist.setBookmarked(!isBookmarked, for: item)
            } label: {
                Label(isBookmarked ? "从片单中移除" : "加入待播片单", systemImage: isBookmarked ? "bookmark.fill" : "bookmark")
            }

            Button {
                selectedDetailVideo = item
            } label: {
                Label("查看影片详情", systemImage: "info.circle")
            }
        }
    }

    private func addRecentSearch(_ keyword: String) {
        let trimmed = keyword.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        var current = recentSearches.filter { $0 != trimmed }
        current.insert(trimmed, at: 0)
        if current.count > 10 {
            current = Array(current.prefix(10))
        }
        recentSearchesRaw = current.joined(separator: "||")
    }

    private func clearRecentSearches() {
        recentSearchesRaw = ""
    }

    private func submitSearch() {
        searchTask?.cancel()
        let submittedQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !submittedQuery.isEmpty else { return }
        addRecentSearch(submittedQuery)
        searchTask = Task {
            await repo.searchAllSources(query: submittedQuery)
        }
    }
}
