import SwiftUI

public struct HomeScreen: View {
    @ObservedObject public var repo = VideoRepository.shared
    public let onPlayVideo: (VideoItem) -> Void
    public let onOpenSearch: () -> Void
    public let onOpenAccount: () -> Void
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    
    @State private var showSourcePicker: Bool = false
    @State private var selectedCategory: String = "全部"
    @State private var isScrolled: Bool = false
    @State private var pullOffsetY: CGFloat = 0
    @State private var selectedDetailVideo: VideoItem? = nil
    @State private var selectedCategoryForDetail: String? = nil
    @State private var activePlaybackVideo: VideoItem? = nil
    @State private var playerCoordinator = PlayerCoordinator.shared
    
    private func playVideo(_ video: VideoItem) {
        activePlaybackVideo = video
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
                        // 1. 顶部 Hero 聚焦大卡（Apple TV 4部聚光灯热门作品轮播画廊，长条微光展开胶囊指示点）
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
                        
                        // Apple TV 核心层级：接着播放（Up Next 货架）
                        UpNextShelf(
                            onPlayVideo: playVideo,
                            onDetail: { selectedDetailVideo = $0 }
                        )

                        AccountOnboardingCard(onOpenAccount: onOpenAccount)

                        // Apple TV 核心层级：流媒体频道与 19 视频源（Channels）
                        ChannelsShelf(
                            onOpenSourcePicker: { showSourcePicker = true }
                        )

                        // 2. 今日热播 Top 10（Apple TV 风格精致半透明数字，重心回归海报）
                        if !repo.top10Videos.isEmpty {
                            VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                                HStack(spacing: AuroraMetrics.spacingS) {
                                    Text("home.today_top_hits".localized)
                                        .font(.title2)
                                        .fontWeight(.bold)
                                        .foregroundStyle(AuroraColor.textPrimary)

                                    Text("TOP 10")
                                        .font(.caption2)
                                        .fontWeight(.black)
                                        .foregroundStyle(AuroraColor.accentRed)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(AuroraColor.accentRed.opacity(0.12), in: Capsule())
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
                        
                        // 3. Apple TV 核心层级：横向分类货架流（一个板块一个标题，向左滑动浏览更多，点击标题进入二级专区）
                        VStack(spacing: AuroraMetrics.spacingXL) {
                            if (repo.categoryShelves.isEmpty || repo.categoryShelves.allSatisfy { $0.videos.isEmpty }) && repo.feedVideos.isEmpty, let feedError = repo.feedError {
                                ContentUnavailableView {
                                    Label("无法加载视频", systemImage: "wifi.exclamationmark")
                                } description: {
                                    Text(feedError)
                                } actions: {
                                    Button("重试", systemImage: "arrow.clockwise") {
                                        repo.loadFeed(for: repo.currentSource, category: repo.currentCategory)
                                    }
                                    .buttonStyle(.borderedProminent)
                                    .tint(AuroraColor.brand)
                                }
                                .padding(.vertical, 40)
                            } else if (repo.categoryShelves.isEmpty || repo.categoryShelves.allSatisfy { $0.videos.isEmpty }) && repo.feedVideos.isEmpty && repo.isLoading {
                                VStack(spacing: AuroraMetrics.spacingM) {
                                    ProgressView()
                                        .controlSize(.regular)
                                        .tint(AuroraColor.brand)
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

                                // 4. 全量双列海报探索网格（确保所有视频作品完整呈现）
                                if !repo.feedVideos.isEmpty {
                                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                                        HStack {
                                            Text("全部作品")
                                                .font(.title2)
                                                .fontWeight(.bold)
                                                .foregroundStyle(AuroraColor.textPrimary)
                                            Spacer()
                                            Text("\(repo.feedVideos.count) 部影片")
                                                .font(.footnote)
                                                .foregroundStyle(AuroraColor.textSecondary)
                                        }
                                        .padding(.horizontal, AuroraMetrics.contentInset)

                                        let gridColumns = horizontalSizeClass == .regular
                                            ? [GridItem(.adaptive(minimum: 240, maximum: 340), spacing: AuroraMetrics.spacingM)]
                                            : [
                                                GridItem(.flexible(), spacing: AuroraMetrics.spacingM),
                                                GridItem(.flexible(), spacing: AuroraMetrics.spacingM)
                                            ]
                                        let dropOffset = repo.top10Videos.isEmpty ? 1 : 11
                                        let remainingVideos = Array(repo.feedVideos.dropFirst(dropOffset))
                                        let gridVideos = remainingVideos.isEmpty ? repo.feedVideos : remainingVideos

                                        LazyVGrid(
                                            columns: gridColumns,
                                            spacing: AuroraMetrics.spacingL
                                        ) {
                                            ForEach(gridVideos) { video in
                                                PosterCard(
                                                    video: video,
                                                    onSelect: { playVideo(video) },
                                                    onDetail: { selectedDetailVideo = video }
                                                )
                                            }
                                        }
                                        .padding(.horizontal, AuroraMetrics.contentInset)
                                    }
                                    .padding(.top, AuroraMetrics.spacingM)
                                }
                            }
                        }
                        .id("recommendSection")
                        
                        // Keep the final recommendation row clear of the floating tab bar.
                        Spacer().frame(height: max(AuroraMetrics.spacingXXL, 96))
                    }
                }
                .ignoresSafeArea(edges: .top)
                .onScrollGeometryChange(for: Bool.self) { geo in
                    geo.contentOffset.y > 60
                } action: { _, newValue in
                    if isScrolled != newValue {
                        isScrolled = newValue
                    }
                }
                .onScrollGeometryChange(for: CGFloat.self) { geo in
                    // 仅在真实下拉橡皮筋时返回正向位移；正常向上滑动时恒返回 0，杜绝每像素触发全屏重组
                    geo.contentOffset.y < 0 ? -geo.contentOffset.y : 0
                } action: { oldValue, newValue in
                    if oldValue != newValue {
                        pullOffsetY = newValue
                    }
                }
                .onAppear {
                    if CommandLine.arguments.contains("--scroll-recommend") {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                            withAnimation(AuroraMotion.selection(reduceMotion: reduceMotion)) {
                                proxy.scrollTo("recommendSection", anchor: .top)
                            }
                        }
                    } else if CommandLine.arguments.contains("--scroll-top10") {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                            withAnimation(AuroraMotion.selection(reduceMotion: reduceMotion)) {
                                proxy.scrollTo("top10Section", anchor: UnitPoint(x: 0, y: 0.15))
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground((isScrolled || repo.billboardVideo == nil) ? .visible : .hidden, for: .navigationBar)
        .toolbarBackground(.regularMaterial, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                // 纯粹单个 3D 折叠丝带 N 标（彻底拔除文字，杜绝「大 N 紧贴小 N」的双 N 现象）
                NetgreyMarkView(width: 20, height: 28)
            }
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
                .accessibilityLabel("搜索视频")
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
            // 仅对首部精选视频做轻量后台预热，杜绝全量并发雪崩与死锁
            if let first = newVideos.first {
                VideoPreloadManager.shared.prewarm(video: first)
            }
        }
        .onReceive(repo.$billboardVideo) { video in
            if let v = video {
                VideoPreloadManager.shared.prewarm(video: v)
            }
            if CommandLine.arguments.contains("--auto-play"), let v = video {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                    playVideo(v)
                }
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
        .navigationDestination(item: $activePlaybackVideo) { video in
            VideoDetailPlaybackScreen(
                video: video,
                onDismiss: {
                    activePlaybackVideo = nil
                    playerCoordinator.dismiss()
                },
                onOpenSearchKeyword: { keyword in
                    activePlaybackVideo = nil
                    playerCoordinator.dismiss()
                    if let onKeyword = onOpenSearchWithKeyword {
                        onKeyword(keyword)
                    } else {
                        onOpenSearch()
                    }
                }
            )
            .toolbar(.hidden, for: .tabBar)
            .toolbar(.hidden, for: .navigationBar)
            .navigationBarHidden(true)
            .navigationBarBackButtonHidden(true)
        }
        .onChange(of: playerCoordinator.activeVideo) { _, newVideo in
            if let v = newVideo, activePlaybackVideo != v {
                activePlaybackVideo = v
            }
        }
        .onChange(of: activePlaybackVideo) { _, newValue in
            if newValue == nil && playerCoordinator.activeVideo != nil {
                playerCoordinator.dismiss()
            }
        }
    }
}

private struct AccountOnboardingCard: View {
    let onOpenAccount: () -> Void

    var body: some View {
        HStack(spacing: AuroraMetrics.spacingM) {
            Image(systemName: "person.crop.circle.badge.plus")
                .font(.title2)
                .foregroundStyle(AuroraColor.brand)
            VStack(alignment: .leading, spacing: 4) {
                Text("注册免费账号")
                    .font(.headline)
                    .foregroundStyle(AuroraColor.textPrimary)
                Text("同步片单与历史，后续会员权益也会保留在账号中")
                    .font(.caption)
                    .foregroundStyle(AuroraColor.textSecondary)
                    .lineLimit(2)
            }
            Spacer(minLength: 8)
            Button("注册 / 登录", action: onOpenAccount)
                .buttonStyle(.borderedProminent)
                .tint(AuroraColor.brand)
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
        .accessibilityLabel("注册免费账号，点击进入注册或登录")
    }
}

// 视频源快速切换面板
struct VideoSourcePickerSheet: View {
    let selectedSource: VideoSourceDefinition
    let onSelect: (VideoSourceDefinition) -> Void
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
            .navigationTitle("视频源")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text("已配置适配 \(VideoSourceCatalog.supportedSources.count)/\(VideoSourceCatalog.allSources.count)")
                        .font(.headline)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button("完成") { dismiss() }
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                Text("站点可用性受网络、地区和站点策略影响。")
                    .font(.footnote)
                    .foregroundStyle(AuroraColor.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, AuroraMetrics.contentInset)
                    .padding(.vertical, AuroraMetrics.spacingS)
                    .background(.regularMaterial)
            }
        }
        .tint(AuroraColor.brand)
    }

    @ViewBuilder
    private func sourceRow(_ source: VideoSourceDefinition) -> some View {
        let isSupported = VideoSourceCatalog.isSupported(source)
        let isSelected = source.id == selectedSource.id
        Button {
            guard isSupported else { return }
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            onSelect(source)
        } label: {
            HStack(spacing: AuroraMetrics.spacingM) {
                VStack(alignment: .leading, spacing: AuroraMetrics.spacingXS) {
                    HStack(spacing: AuroraMetrics.spacingS) {
                        Text(source.name)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(isSelected ? AuroraColor.brand : AuroraColor.textPrimary)
                        if source.isDirectConnect {
                            Text("大陆直连")
                                .font(.caption2.weight(.bold))
                                .foregroundStyle(AuroraColor.forestGreen)
                        }
                    }
                    Text(isSupported ? source.description : "iOS 解析器尚未接入")
                        .font(.caption)
                        .foregroundStyle(AuroraColor.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer(minLength: AuroraMetrics.spacingS)
                if isSelected {
                    Image(systemName: "checkmark")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(AuroraColor.brand)
                        .accessibilityHidden(true)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!isSupported)
        .accessibilityLabel("\(source.name), \(isSupported ? source.description : "尚未接入")")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

// 顶部 Hero 骨架屏占位（平滑过渡，消除切换时的空白与闪烁）
public struct HeroLoadingPlaceholder: View {
    public init() {}

    public var body: some View {
        ZStack(alignment: .bottom) {
            Rectangle()
                .fill(
                    LinearGradient(
                        colors: [Color.white.opacity(0.04), Color.white.opacity(0.08), Color.white.opacity(0.02)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .frame(maxWidth: .infinity)
                .frame(height: 380)
            
            VStack(spacing: AuroraMetrics.spacingM) {
                RoundedRectangle(cornerRadius: 6)
                    .fill(Color.white.opacity(0.1))
                    .frame(width: 220, height: 28)
                
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color.white.opacity(0.08))
                    .frame(width: 140, height: 16)
                
                HStack(spacing: AuroraMetrics.spacingL) {
                    RoundedRectangle(cornerRadius: 22)
                        .fill(Color.white.opacity(0.12))
                        .frame(height: 44)
                    RoundedRectangle(cornerRadius: 22)
                        .fill(Color.white.opacity(0.08))
                        .frame(width: 120, height: 44)
                }
                .padding(.horizontal, AuroraMetrics.contentInset)
                .padding(.bottom, AuroraMetrics.spacingL)
            }
        }
    }
}
