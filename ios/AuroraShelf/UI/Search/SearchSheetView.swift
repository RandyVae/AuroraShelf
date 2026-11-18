import SwiftUI

public struct SearchSheetView: View {
    public enum SearchScope: String, CaseIterable, Identifiable, Sendable {
        case video = "视频"
        case comic = "漫画"
        case live = "直播"
        case forum = "社区"

        public var id: String { rawValue }
    }

    public let onPlayVideo: (VideoItem) -> Void
    public let onSelectComic: ((ComicItem) -> Void)?
    public let onSelectLive: ((LiveStreamItem) -> Void)?
    public let onSelectForumPost: ((ForumPostItem) -> Void)?
    public let onDismiss: () -> Void
    
    @ObservedObject private var repo = VideoRepository.shared
    @ObservedObject private var comicRepo = ComicRepository.shared
    @ObservedObject private var liveRepo = LiveRepository.shared
    @ObservedObject private var forumRepo = ForumRepository.shared
    @ObservedObject private var watchlist = WatchlistStore.shared
    @State private var query: String = ""
    @State private var activeScope: SearchScope = .video
    @State private var comicSearchResults: [ComicItem] = []
    @State private var isSearchingComics: Bool = false
    @State private var comicSearchError: String? = nil
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

    private func sourceDisplayRank(_ sourceName: String) -> Int {
        if sourceName == repo.currentSource.name { return 0 }
        if sourceName == "华视影院" { return 1 }
        return 2
    }

    private var availableSources: [String] {
        var sources = ["全部"]
        let uniqueSources = Set(repo.searchResults.map { $0.sourceName }).sorted { lhs, rhs in
            let lRank = sourceDisplayRank(lhs)
            let rRank = sourceDisplayRank(rhs)
            if lRank != rRank { return lRank < rRank }
            return lhs < rhs
        }
        sources.append(contentsOf: uniqueSources)
        return sources
    }

    private var filteredResults: [VideoItem] {
        if selectedSourceFilter == "全部" {
            return repo.searchResults
        }
        return repo.searchResults.filter { $0.sourceName == selectedSourceFilter }
    }

    private var filteredLiveStreams: [LiveStreamItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !trimmed.isEmpty else { return [] }
        return liveRepo.liveStreams.filter {
            $0.username.lowercased().contains(trimmed)
                || $0.title.lowercased().contains(trimmed)
                || $0.tags.contains(where: { $0.lowercased().contains(trimmed) })
        }
    }

    private var filteredForumPosts: [ForumPostItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !trimmed.isEmpty else { return [] }
        return forumRepo.posts.filter {
            $0.title.lowercased().contains(trimmed)
                || ($0.author ?? "").lowercased().contains(trimmed)
                || $0.boardName.lowercased().contains(trimmed)
        }
    }

    private var resultShelves: [(title: String, items: [VideoItem])] {
        let items = filteredResults
        guard !items.isEmpty else { return [] }

        var shelves: [(title: String, items: [VideoItem])] = []
        
        let topCount = min(4, items.count)
        let topResults = Array(items.prefix(topCount))
        shelves.append((title: "热门匹配", items: topResults))

        let sourceGroups = Dictionary(grouping: items) { $0.sourceName }
        if sourceGroups.count > 1 {
            let sortedSources = sourceGroups.sorted { lhs, rhs in
                let lRank = sourceDisplayRank(lhs.key)
                let rRank = sourceDisplayRank(rhs.key)
                if lRank != rRank { return lRank < rRank }
                if lhs.value.count != rhs.value.count { return lhs.value.count > rhs.value.count }
                return lhs.key < rhs.key
            }
            for (source, groupItems) in sortedSources {
                shelves.append((title: "\(source) 结果", items: groupItems))
            }
        } else {
            let categoryGroups = Dictionary(grouping: items) { item -> String in
                let cat = item.category.trimmingCharacters(in: .whitespacesAndNewlines)
                return cat.isEmpty ? "精选推荐" : cat
            }
            if categoryGroups.count > 1 {
                for (cat, groupItems) in categoryGroups.sorted(by: { $0.value.count > $1.value.count }) {
                    shelves.append((title: cat, items: groupItems))
                }
            } else {
                shelves.append((title: "全部精选", items: items))
            }
        }

        return shelves
    }
    
    public init(
        initialQuery: String = "",
        initialScope: SearchScope = .video,
        onPlayVideo: @escaping (VideoItem) -> Void,
        onSelectComic: ((ComicItem) -> Void)? = nil,
        onSelectLive: ((LiveStreamItem) -> Void)? = nil,
        onSelectForumPost: ((ForumPostItem) -> Void)? = nil,
        onDismiss: @escaping () -> Void
    ) {
        self._query = State(initialValue: initialQuery)
        self._activeScope = State(initialValue: initialScope)
        self.onPlayVideo = onPlayVideo
        self.onSelectComic = onSelectComic
        self.onSelectLive = onSelectLive
        self.onSelectForumPost = onSelectForumPost
        self.onDismiss = onDismiss
    }
    
    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Picker("搜索范围", selection: $activeScope) {
                    ForEach(SearchScope.allCases) { scope in
                        Text(scope.rawValue).tag(scope)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, AuroraMetrics.contentInset)
                .padding(.vertical, AuroraMetrics.spacingS)
                .onChange(of: activeScope) { _, _ in
                    if !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        submitSearch()
                    }
                }

                Group {
                    switch activeScope {
                    case .video:
                        videoSearchContent
                    case .comic:
                        comicSearchContent
                    case .live:
                        liveSearchContent
                    case .forum:
                        forumSearchContent
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
            .onChange(of: query) { _, newValue in
                if newValue.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    searchTask?.cancel()
                }
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
    private var videoSearchContent: some View {
        if repo.isSearching && repo.searchResults.isEmpty {
            VStack(spacing: AuroraMetrics.spacingL) {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: AuroraColor.brand))
                    .scaleEffect(1.3)
                
                Text("search.concurrent_hint".localized(searchableSourceCount))
                    .font(.subheadline)
                    .foregroundStyle(Color.secondary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !repo.searchResults.isEmpty {
            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: AuroraMetrics.spacingXL) {
                    if repo.isSearching {
                        HStack(spacing: 8) {
                            ProgressView()
                                .controlSize(.small)
                                .tint(AuroraColor.brand)
                            Text("已优先显示命中结果，正在同步其余视频源…")
                                .font(.caption)
                                .foregroundStyle(AuroraColor.textSecondary)
                        }
                        .padding(.horizontal, AuroraMetrics.contentInset)
                        .padding(.top, AuroraMetrics.spacingS)
                    }
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
            landingExploreContent
        }
    }

    @ViewBuilder
    private var comicSearchContent: some View {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if isSearchingComics && comicSearchResults.isEmpty {
            VStack(spacing: AuroraMetrics.spacingL) {
                ProgressView()
                    .tint(AuroraColor.brand)
                Text("comics.loading".localized)
                    .font(.subheadline)
                    .foregroundStyle(AuroraColor.textSecondary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if trimmed.isEmpty {
            landingExploreContent
        } else if comicSearchResults.isEmpty, let err = comicSearchError {
            ContentUnavailableView {
                Label("搜索失败", systemImage: "wifi.exclamationmark")
            } description: {
                Text(err)
            } actions: {
                Button("重试", systemImage: "arrow.clockwise") {
                    submitSearch()
                }
                .buttonStyle(.borderedProminent)
            }
        } else if comicSearchResults.isEmpty {
            ContentUnavailableView.search(text: trimmed)
        } else {
            ScrollView(.vertical, showsIndicators: false) {
                LazyVGrid(
                    columns: [GridItem(.adaptive(minimum: 110, maximum: 160), spacing: AuroraMetrics.spacingM)],
                    spacing: AuroraMetrics.spacingL
                ) {
                    ForEach(comicSearchResults) { comic in
                        Button {
                            onSelectComic?(comic)
                            onDismiss()
                        } label: {
                            VStack(alignment: .leading, spacing: 6) {
                                AuroraCoverImage(
                                    url: comic.coverUrl,
                                    title: comic.title,
                                    sourceBaseURL: comic.coverBaseURL,
                                    aspectRatio: AuroraMetrics.posterAspect,
                                    contentMode: .fill,
                                    cornerRadius: 10
                                )
                                Text(comic.title)
                                    .font(.caption.bold())
                                    .foregroundStyle(AuroraColor.textPrimary)
                                    .lineLimit(1)
                                Text(comic.latestChapterTitle.isEmpty ? comic.author : comic.latestChapterTitle)
                                    .font(.caption2)
                                    .foregroundStyle(AuroraColor.textSecondary)
                                    .lineLimit(1)
                            }
                        }
                        .buttonStyle(AppleSpringCardButtonStyle())
                    }
                }
                .padding(AuroraMetrics.contentInset)
            }
        }
    }

    @ViewBuilder
    private var liveSearchContent: some View {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let matches = filteredLiveStreams
        if trimmed.isEmpty {
            landingExploreContent
        } else if matches.isEmpty {
            ContentUnavailableView.search(text: trimmed)
        } else {
            ScrollView(.vertical, showsIndicators: false) {
                LazyVGrid(
                    columns: [GridItem(.adaptive(minimum: 150, maximum: 220), spacing: AuroraMetrics.spacingM)],
                    spacing: AuroraMetrics.spacingL
                ) {
                    ForEach(matches) { stream in
                        Button {
                            onSelectLive?(stream)
                            onDismiss()
                        } label: {
                            VStack(alignment: .leading, spacing: 6) {
                                AuroraCoverImage(
                                    url: stream.avatarUrl,
                                    title: stream.username,
                                    aspectRatio: AuroraMetrics.liveAspect,
                                    contentMode: .fill,
                                    cornerRadius: 10
                                )
                                Text(stream.username)
                                    .font(.caption.bold())
                                    .foregroundStyle(AuroraColor.textPrimary)
                                    .lineLimit(1)
                                Text(stream.title.isEmpty ? LiveSourceCatalog.name(for: stream.sourceID) : stream.title)
                                    .font(.caption2)
                                    .foregroundStyle(AuroraColor.textSecondary)
                                    .lineLimit(1)
                            }
                        }
                        .buttonStyle(AppleSpringCardButtonStyle())
                    }
                }
                .padding(AuroraMetrics.contentInset)
            }
        }
    }

    @ViewBuilder
    private var forumSearchContent: some View {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let matches = filteredForumPosts
        if trimmed.isEmpty {
            landingExploreContent
        } else if matches.isEmpty {
            ContentUnavailableView.search(text: trimmed)
        } else {
            List(matches) { post in
                Button {
                    onSelectForumPost?(post)
                    onDismiss()
                } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(post.title)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(AuroraColor.textPrimary)
                            .lineLimit(2)
                        HStack(spacing: 8) {
                            Text(post.boardName)
                                .font(.caption2.bold())
                                .foregroundStyle(AuroraColor.brandLabel)
                            if let author = post.author, !author.isEmpty {
                                Text(author)
                                    .font(.caption2)
                                    .foregroundStyle(AuroraColor.textSecondary)
                            }
                            Spacer()
                            Text("forum.replies".localized(post.replies ?? 0))
                                .font(.caption2)
                                .foregroundStyle(AuroraColor.textTertiary)
                        }
                    }
                    .padding(.vertical, 4)
                }
                .buttonStyle(.plain)
                .listRowBackground(AuroraColor.surface)
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
        }
    }

    @ViewBuilder
    private var landingExploreContent: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: AuroraMetrics.spacingXL) {
                if !recentSearches.isEmpty {
                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                        HStack {
                            Text("search.history".localized)
                                .font(.headline)
                                .foregroundStyle(AuroraColor.textPrimary)
                            Spacer()
                            Button("search.clear_history".localized) {
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

                HStack(spacing: 6) {
                    Image(systemName: "network")
                        .font(.caption)
                        .foregroundStyle(AuroraColor.textTertiary)
                    Text("search.concurrent_hint".localized(searchableSourceCount))
                        .font(.caption)
                        .foregroundStyle(AuroraColor.textTertiary)
                }
                .padding(.top, AuroraMetrics.spacingS)
            }
            .padding(.horizontal, AuroraMetrics.contentInset)
            .padding(.top, AuroraMetrics.spacingL)
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
                                    .foregroundStyle(AuroraColor.textPrimary)
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
        selectedSourceFilter = "全部"
        addRecentSearch(submittedQuery)
        switch activeScope {
        case .video:
            searchTask = Task {
                await repo.searchAllSources(query: submittedQuery)
            }
        case .comic:
            isSearchingComics = true
            comicSearchError = nil
            searchTask = Task {
                do {
                    let results = try await comicRepo.searchComics(query: submittedQuery)
                    guard !Task.isCancelled else { return }
                    await MainActor.run {
                        self.comicSearchResults = results
                        self.isSearchingComics = false
                    }
                } catch {
                    guard !Task.isCancelled else { return }
                    await MainActor.run {
                        self.comicSearchError = error.localizedDescription
                        self.isSearchingComics = false
                    }
                }
            }
        case .live:
            if liveRepo.liveStreams.isEmpty {
                liveRepo.loadStreams()
            }
        case .forum:
            if forumRepo.posts.isEmpty {
                forumRepo.loadPosts()
            }
        }
    }
}
