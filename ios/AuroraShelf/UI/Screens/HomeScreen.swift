import SwiftUI

public struct HomeScreen: View {
    @ObservedObject public var repo = VideoRepository.shared
    @ObservedObject private var account = AccountService.shared
    @ObservedObject private var locManager = LocalizationManager.shared
    public let onPlayVideo: (VideoItem) -> Void
    public let onOpenSearch: () -> Void
    public let onOpenAccount: () -> Void
    @Environment(\.auroraContainerSize) private var containerSize
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    @State private var showSourcePicker: Bool = false
    @State private var selectedCategory: String = "全部"
    @State private var isScrolled: Bool = false
    @State private var pullOffsetY: CGFloat = 0
    @State private var selectedDetailVideo: VideoItem? = nil
    @State private var selectedCategoryForDetail: String? = nil

    private func playVideo(_ video: VideoItem) {
        onPlayVideo(video)
    }

    public var onOpenSearchWithKeyword: ((String) -> Void)? = nil

    private var spotlightVideos: [VideoItem] {
        guard let billboard = repo.billboardVideo else { return [] }
        var list = [billboard]
        for v in repo.feedVideos where v.id != billboard.id {
            list.append(v)
            if list.count >= 4 { break }
        }
        return list
    }

    private var hasHeroContent: Bool {
        !spotlightVideos.isEmpty || repo.isLoading
    }

    public init(
        onPlayVideo: @escaping (VideoItem) -> Void,
        onOpenSearch: @escaping () -> Void,
        onOpenAccount: @escaping () -> Void = {},
        onOpenSearchWithKeyword: ((String) -> Void)? = nil
    ) {
        self.onPlayVideo = onPlayVideo
        self.onOpenSearch = onOpenSearch
        self.onOpenAccount = onOpenAccount
        self.onOpenSearchWithKeyword = onOpenSearchWithKeyword
    }

    public var body: some View {
        ZStack {
            AuroraColor.canvas.ignoresSafeArea()

            ScrollViewReader { proxy in
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: AuroraMetrics.spacingXL) {
                        let spotlights = spotlightVideos
                        if !spotlights.isEmpty {
                            HeroBanner(
                                videos: spotlights,
                                pullOffset: pullOffsetY,
                                onPlay: { v in playVideo(v) },
                                onDetail: { v in selectedDetailVideo = v }
                            )
                        } else if repo.isLoading {
                            HeroLoadingPlaceholder()
                        } else {
                            Spacer().frame(height: 80)
                        }

                        UpNextShelf(
                            onPlayVideo: playVideo,
                            onDetail: { selectedDetailVideo = $0 }
                        )

                        if account.profile == nil {
                            AccountOnboardingCard(onOpenAccount: onOpenAccount)
                        }

                        ChannelsShelf(
                            onOpenSourcePicker: { showSourcePicker = true }
                        )

                        if repo.currentSource.categories.count > 1 {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: AuroraMetrics.spacingS) {
                                    ForEach(repo.currentSource.categories, id: \.self) { cat in
                                        let isSelected = (repo.currentCategory == cat)
                                        Button {
                                            guard repo.currentCategory != cat else { return }
                                            AuroraMotion.perform(AuroraMotion.quick, reduceMotion: reduceMotion) {
                                                selectedCategory = cat
                                                repo.switchCategory(to: cat)
                                            }
                                        } label: {
                                            Text(cat.localizedCategoryLabel)
                                                .font(.subheadline.weight(isSelected ? .bold : .medium))
                                                .foregroundStyle(isSelected ? Color.white : AuroraColor.textPrimary)
                                                .padding(.horizontal, 16)
                                                .padding(.vertical, 8)
                                                .frame(minHeight: AuroraMetrics.minTouchTarget)
                                                .background(
                                                    isSelected ? AuroraColor.brandSolid : AuroraColor.surfaceCard,
                                                    in: Capsule()
                                                )
                                                .overlay(
                                                    Capsule()
                                                        .stroke(isSelected ? Color.clear : AuroraColor.hairline, lineWidth: 1)
                                                )
                                        }
                                        .buttonStyle(.plain)
                                        .hoverEffect(.highlight)
                                        .accessibilityLabel("home.category_label".localized(cat.localizedCategoryLabel))
                                        .accessibilityAddTraits(isSelected ? .isSelected : [])
                                    }
                                }
                                .padding(.horizontal, AuroraMetrics.contentInset)
                            }
                            .sensoryFeedback(.selection, trigger: repo.currentCategory)
                        }

                        if !repo.top10Videos.isEmpty {
                            VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                                HStack(spacing: AuroraMetrics.spacingS) {
                                    Text("home.today_top_hits".localized)
                                        .font(.title2)
                                        .fontWeight(.bold)
                                        .foregroundStyle(AuroraColor.textPrimary)
                                        .accessibilityAddTraits(.isHeader)

                                    Text("TOP 10")
                                        .font(.caption2)
                                        .fontWeight(.black)
                                        .foregroundStyle(.white)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(AuroraColor.accentRed, in: Capsule())
                                }
                                .padding(.horizontal, AuroraMetrics.contentInset)

                                ScrollView(.horizontal, showsIndicators: false) {
                                    LazyHStack(alignment: .top, spacing: AuroraMetrics.spacingL) {
                                        ForEach(Array(repo.top10Videos.enumerated()), id: \.element.id) { index, video in
                                            Top10Card(
                                                video: video,
                                                rank: index + 1,
                                                onSelect: { playVideo(video) },
                                                onDetail: { selectedDetailVideo = video }
                                            )
                                        }
                                    }
                                    .padding(.horizontal, AuroraMetrics.contentInset)
                                }
                            }
                            .id("top10Section")
                        }

                        VStack(spacing: AuroraMetrics.spacingXL) {
                            if (repo.categoryShelves.isEmpty || repo.categoryShelves.allSatisfy { $0.videos.isEmpty }) && repo.feedVideos.isEmpty, let feedError = repo.feedError {
                                ContentUnavailableView {
                                    Label("home.load_failed".localized, systemImage: "wifi.exclamationmark")
                                } description: {
                                    Text(feedError)
                                } actions: {
                                    Button("common.retry".localized, systemImage: "arrow.clockwise") {
                                        repo.loadFeed(for: repo.currentSource, category: repo.currentCategory)
                                    }
                                    .buttonStyle(.borderedProminent)
                                    .tint(AuroraColor.brandSolid)
                                }
                                .padding(.vertical, 40)
                            } else if (repo.categoryShelves.isEmpty || repo.categoryShelves.allSatisfy { $0.videos.isEmpty }) && repo.feedVideos.isEmpty && repo.isLoading {
                                VStack(spacing: AuroraMetrics.spacingM) {
                                    ProgressView()
                                        .controlSize(.regular)
                                        .tint(AuroraColor.brandLabel)
                                    Text("home.connecting".localized(repo.currentSource.name))
                                        .font(.subheadline)
                                        .foregroundStyle(AuroraColor.textSecondary)
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 40)
                            } else {
                                ForEach(repo.categoryShelves) { shelf in
                                    CategoryShelfView(
                                        shelf: shelf,
                                        onSeeAll: { cat in
                                            selectedCategoryForDetail = cat
                                        },
                                        onSelectVideo: { video in
                                            playVideo(video)
                                        },
                                        onDetailVideo: { video in
                                            selectedDetailVideo = video
                                        }
                                    )
                                }

                                if !repo.feedVideos.isEmpty {
                                    let form = DeviceLayout.resolveCurrent(
                                        containerSize: containerSize,
                                        horizontalSizeClass: horizontalSizeClass
                                    )
                                    let baseMin = DeviceLayout.gridMinimumWidth(for: form)
                                    let minColWidth: CGFloat = dynamicTypeSize.isAccessibilitySize ? max(baseMin, 260) : baseMin
                                    let gridColumns = form.isWide
                                        ? [GridItem(.adaptive(minimum: minColWidth, maximum: 260), spacing: AuroraMetrics.spacingM)]
                                        : [
                                            GridItem(.flexible(), spacing: AuroraMetrics.spacingM),
                                            GridItem(.flexible(), spacing: AuroraMetrics.spacingM)
                                        ]
                                    let excludedIds: Set<String> = repo.top10Videos.isEmpty
                                        ? Set(spotlightVideos.prefix(1).map(\.id))
                                        : Set(repo.top10Videos.map(\.id)).union(spotlightVideos.prefix(1).map(\.id))
                                    let remainingVideos = repo.feedVideos.filter { !excludedIds.contains($0.id) }
                                    let gridVideos = remainingVideos.isEmpty ? repo.feedVideos : remainingVideos

                                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                                        HStack(alignment: .firstTextBaseline) {
                                            Text(
                                                repo.currentCategory == "全部"
                                                    ? "home.all_works".localized
                                                    : "home.category_all_works".localized(repo.currentCategory.localizedCategoryLabel)
                                            )
                                            .font(.title2)
                                            .fontWeight(.bold)
                                            .foregroundStyle(AuroraColor.textPrimary)
                                            .lineLimit(1)
                                            .accessibilityAddTraits(.isHeader)

                                            Spacer()

                                            Button {
                                                selectedCategoryForDetail = repo.currentCategory
                                            } label: {
                                                HStack(spacing: 4) {
                                                    let countLabel = "\(repo.feedVideos.count)\(repo.canLoadMore ? "+" : "")"
                                                    Text("home.videos_count".localized(countLabel))
                                                    Image(systemName: "chevron.right")
                                                        .font(.caption2.weight(.bold))
                                                }
                                                .font(.footnote.weight(.medium))
                                                .foregroundStyle(AuroraColor.brandLabel)
                                                .frame(minHeight: AuroraMetrics.minTouchTarget)
                                                .contentShape(Rectangle())
                                            }
                                            .buttonStyle(.plain)
                                            .hoverEffect(.highlight)
                                        }
                                        .padding(.horizontal, AuroraMetrics.contentInset)

                                        LazyVGrid(
                                            columns: gridColumns,
                                            spacing: AuroraMetrics.spacingL
                                        ) {
                                            ForEach(Array(gridVideos.enumerated()), id: \.element.id) { idx, video in
                                                PosterCard(
                                                    video: video,
                                                    onSelect: { playVideo(video) },
                                                    onDetail: { selectedDetailVideo = video }
                                                )
                                                .onAppear {
                                                    if idx >= max(0, gridVideos.count - 4) {
                                                        Task {
                                                            await repo.loadNextPage()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        .padding(.horizontal, AuroraMetrics.contentInset)

                                        if repo.isLoadingNextPage {
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
                                        } else if !repo.canLoadMore && !gridVideos.isEmpty {
                                            Text("home.all_loaded".localized)
                                                .font(.caption)
                                                .foregroundStyle(AuroraColor.textTertiary)
                                                .frame(maxWidth: .infinity)
                                                .padding(.vertical, AuroraMetrics.spacingM)
                                        }
                                    }
                                    .padding(.top, AuroraMetrics.spacingM)
                                }
                            }
                        }
                        .id("recommendSection")

                        Spacer().frame(height: max(AuroraMetrics.spacingXXL, 96))
                    }
                }
                .refreshable {
                    repo.loadFeed(for: repo.currentSource, category: repo.currentCategory)
                    try? await Task.sleep(nanoseconds: 550_000_000)
                }
                .auroraImmersiveHeroScrollEdge(
                    isScrolled: $isScrolled,
                    hasHeroContent: hasHeroContent,
                    reduceMotion: reduceMotion
                )
                .ignoresSafeArea(edges: .top)
                .onScrollGeometryChange(for: CGFloat.self) { geo in
                    geo.contentOffset.y < 0 ? -geo.contentOffset.y : 0
                } action: { oldValue, newValue in
                    if oldValue != newValue {
                        pullOffsetY = newValue
                    }
                }
                .onAppear {
                    if CommandLine.arguments.contains("--scroll-recommend") {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                            AuroraMotion.perform(AuroraMotion.quick, reduceMotion: reduceMotion) {
                                proxy.scrollTo("recommendSection", anchor: .top)
                            }
                        }
                    } else if CommandLine.arguments.contains("--scroll-top10") {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                            AuroraMotion.perform(AuroraMotion.quick, reduceMotion: reduceMotion) {
                                proxy.scrollTo("top10Section", anchor: UnitPoint(x: 0, y: 0.15))
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .auroraImmersiveHeroNavigationBar(isScrolled: isScrolled, hasHeroContent: hasHeroContent)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                NetgreyMarkView(width: 20, height: 28)
            }
            .sharedBackgroundVisibility(.hidden)

            ToolbarItem(placement: .topBarTrailing) {
                Button(action: onOpenSearch) {
                    Image(systemName: "magnifyingglass")
                        .font(.body)
                        .fontWeight(.semibold)
                        .foregroundStyle(.primary)
                }
                .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                .contentShape(Rectangle())
                .hoverEffect(.highlight)
                .accessibilityLabel("home.search_a11y".localized)
            }
        }
        .onAppear {
            selectedCategory = repo.currentCategory
            if let idx = CommandLine.arguments.firstIndex(of: "--category"), idx + 1 < CommandLine.arguments.count {
                let targetCategory = CommandLine.arguments[idx + 1]
                selectedCategory = targetCategory
                repo.switchCategory(to: targetCategory)
            }
            if CommandLine.arguments.contains("--play-first") {
                Task {
                    for _ in 0..<30 {
                        try? await Task.sleep(nanoseconds: 300_000_000)
                        if let first = repo.feedVideos.first(where: { $0.id != repo.billboardVideo?.id }) ?? repo.feedVideos.first {
                            await MainActor.run {
                                playVideo(first)
                            }
                            break
                        }
                    }
                }
            }
            if CommandLine.arguments.contains("--open-source-picker") {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    showSourcePicker = true
                }
            }
        }
        .onChange(of: selectedCategory) { _, newCat in
            if newCat != repo.currentCategory {
                repo.switchCategory(to: newCat)
            }
        }
        .onChange(of: repo.currentCategory) { _, newCat in
            if selectedCategory != newCat {
                selectedCategory = newCat
            }
        }
        .onChange(of: repo.feedVideos) { _, newVideos in
            if let first = newVideos.first {
                VideoPreloadManager.shared.prewarm(video: first)
            }
        }
        .onReceive(repo.$billboardVideo) { video in
            if let v = video {
                VideoPreloadManager.shared.prewarm(video: v)
            }
            if CommandLine.arguments.contains("--open-detail"), let v = video {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                    selectedDetailVideo = v
                }
            }
        }
        .sheet(isPresented: $showSourcePicker) {
            VideoSourcePickerSheet(
                selectedSource: repo.currentSource,
                onSelect: { source in
                    selectedCategory = source.categories.first ?? "全部"
                    repo.switchSource(to: source)
                    showSourcePicker = false
                }
            )
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
        .sheet(item: $selectedDetailVideo) { video in
            VideoDetailSheet(
                video: video,
                onPlay: playVideo,
                onDismiss: {
                    selectedDetailVideo = nil
                },
                onSearchKeyword: { keyword in
                    selectedDetailVideo = nil
                    if let onKeyword = onOpenSearchWithKeyword {
                        onKeyword(keyword)
                    } else {
                        onOpenSearch()
                    }
                }
            )
        }
        .navigationDestination(isPresented: Binding(
            get: { selectedCategoryForDetail != nil },
            set: { if !$0 { selectedCategoryForDetail = nil } }
        )) {
            if let cat = selectedCategoryForDetail {
                CategoryDetailScreen(
                    source: repo.currentSource,
                    initialCategory: cat,
                    onPlayVideo: playVideo,
                    onOpenDetail: { video in
                        selectedDetailVideo = video
                    }
                )
            }
        }
    }
}

private struct AccountOnboardingCard: View {
    let onOpenAccount: () -> Void
    @ObservedObject private var locManager = LocalizationManager.shared

    var body: some View {
        HStack(spacing: AuroraMetrics.spacingM) {
            Image(systemName: "person.crop.circle.badge.plus")
                .font(.title2)
                .foregroundStyle(AuroraColor.brandLabel)
            VStack(alignment: .leading, spacing: 4) {
                Text("home.register_banner_title".localized)
                    .font(.headline)
                    .foregroundStyle(AuroraColor.textPrimary)
                Text("home.register_banner_desc".localized)
                    .font(.caption)
                    .foregroundStyle(AuroraColor.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 8)
            Button("home.register_banner_btn".localized, action: onOpenAccount)
                .buttonStyle(.borderedProminent)
                .tint(AuroraColor.brandSolid)
                .frame(minHeight: AuroraMetrics.minTouchTarget)
        }
        .padding(AuroraMetrics.spacingL)
        .background(
            RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                .fill(AuroraColor.surface)
                .overlay(
                    RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                        .stroke(AuroraColor.brand.opacity(0.35), lineWidth: 1)
                )
        )
        .padding(.horizontal, AuroraMetrics.contentInset)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("home.register_banner_a11y".localized)
    }
}

// 视频源快速切换面板
struct VideoSourcePickerSheet: View {
    let selectedSource: VideoSourceDefinition
    let onSelect: (VideoSourceDefinition) -> Void
    @ObservedObject private var locManager = LocalizationManager.shared
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                ForEach(VideoSourceGroup.allCases, id: \.self) { group in
                    Section(group.rawValue) {
                        ForEach(VideoSourceCatalog.allSources.filter { $0.group == group }) { source in
                            sourceRow(source)
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(AuroraColor.canvas)
            .navigationTitle("home.source_sheet_title".localized)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text("home.source_adapted_count".localized(VideoSourceCatalog.supportedSources.count, VideoSourceCatalog.allSources.count))
                        .font(.headline)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.done".localized) { dismiss() }
                        .frame(minWidth: AuroraMetrics.minTouchTarget, minHeight: AuroraMetrics.minTouchTarget)
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                Text("home.source_availability_note".localized)
                    .font(.footnote)
                    .foregroundStyle(AuroraColor.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, AuroraMetrics.contentInset)
                    .padding(.vertical, AuroraMetrics.spacingS)
                    .background(AuroraColor.surface)
            }
        }
        .tint(AuroraColor.brandLabel)
    }

    @ViewBuilder
    private func sourceRow(_ source: VideoSourceDefinition) -> some View {
        let isSupported = VideoSourceCatalog.isSupported(source)
        let isSelected = source.id == selectedSource.id
        Button {
            guard isSupported else { return }
            AuroraHaptics.medium()
            onSelect(source)
        } label: {
            HStack(spacing: AuroraMetrics.spacingM) {
                VStack(alignment: .leading, spacing: AuroraMetrics.spacingXS) {
                    HStack(spacing: AuroraMetrics.spacingS) {
                        Text(source.name)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(isSelected ? AuroraColor.brandLabel : AuroraColor.textPrimary)
                        if source.isDirectConnect {
                            Text("home.direct_connect".localized)
                                .font(.caption2.weight(.bold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(AuroraColor.forestGreen, in: Capsule())
                        }
                    }
                    Text(isSupported ? source.description : "home.parser_pending".localized)
                        .font(.caption)
                        .foregroundStyle(AuroraColor.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer(minLength: AuroraMetrics.spacingS)
                if isSelected {
                    Image(systemName: "checkmark")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(AuroraColor.brandLabel)
                        .accessibilityHidden(true)
                }
            }
            .frame(minHeight: AuroraMetrics.minTouchTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .hoverEffect(.highlight)
        .disabled(!isSupported)
        .accessibilityLabel("\(source.name), \(isSupported ? source.description : "home.parser_pending_short".localized)")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

public struct HeroLoadingPlaceholder: View {
    public init() {}

    public var body: some View {
        ZStack(alignment: .bottom) {
            Rectangle()
                .fill(
                    LinearGradient(
                        colors: [AuroraColor.surfaceCard, AuroraColor.surfaceHigh, AuroraColor.canvas],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .frame(maxWidth: .infinity)
                .frame(height: 380)

            VStack(spacing: AuroraMetrics.spacingM) {
                RoundedRectangle(cornerRadius: 6)
                    .fill(AuroraColor.surfaceHigh)
                    .frame(width: 220, height: 28)

                RoundedRectangle(cornerRadius: 4)
                    .fill(AuroraColor.surfaceCard)
                    .frame(width: 140, height: 16)

                HStack(spacing: AuroraMetrics.spacingL) {
                    RoundedRectangle(cornerRadius: 22)
                        .fill(AuroraColor.surfaceHigh)
                        .frame(height: 44)
                    RoundedRectangle(cornerRadius: 22)
                        .fill(AuroraColor.surfaceCard)
                        .frame(width: 120, height: 44)
                }
                .padding(.horizontal, AuroraMetrics.contentInset)
                .padding(.bottom, AuroraMetrics.spacingL)
            }
        }
    }
}
