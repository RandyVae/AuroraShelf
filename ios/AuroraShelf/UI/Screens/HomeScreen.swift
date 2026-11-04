import SwiftUI

public struct HomeScreen: View {
    @ObservedObject public var repo = VideoRepository.shared
    public let onPlayVideo: (VideoItem) -> Void
    public let onOpenSearch: () -> Void
    
    @State private var showSourcePicker: Bool = false
    @State private var selectedCategory: String = "全部"
    @State private var isScrolled: Bool = false
    
    public init(
        onPlayVideo: @escaping (VideoItem) -> Void,
        onOpenSearch: @escaping () -> Void
    ) {
        self.onPlayVideo = onPlayVideo
        self.onOpenSearch = onOpenSearch
    }
    
    public var body: some View {
        ZStack {
            AuroraColor.canvas.ignoresSafeArea()
            
            ScrollViewReader { proxy in
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: AuroraMetrics.spacingXL) {
                        // 1. 顶部 Hero 聚焦大卡（黄金画幅，呼吸感强，首屏自然延展）
                        if let billboard = repo.billboardVideo {
                            HeroBanner(
                                video: billboard,
                                onPlay: { onPlayVideo(billboard) },
                                onDetail: { onPlayVideo(billboard) }
                            )
                        } else if repo.isLoading {
                            HeroLoadingPlaceholder()
                        } else {
                            Spacer().frame(height: 80)
                        }
                        
                        // 2. 今日热播 Top 10（大数字紧贴海报左下角）
                        if !repo.top10Videos.isEmpty {
                            VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                                HStack(spacing: AuroraMetrics.spacingS) {
                                    Text("今日热播")
                                        .font(.title2)
                                        .fontWeight(.bold)
                                        .foregroundStyle(AuroraColor.textPrimary)
                                    
                                    Text("TOP 10")
                                        .font(.headline)
                                        .fontWeight(.black)
                                        .foregroundStyle(AuroraColor.accentRed)
                                }
                                .padding(.horizontal, AuroraMetrics.contentInset)
                                
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: AuroraMetrics.spacingL) {
                                        ForEach(Array(repo.top10Videos.enumerated()), id: \.element.id) { index, video in
                                            Top10Card(video: video, rank: index + 1) {
                                                onPlayVideo(video)
                                            }
                                        }
                                    }
                                    .padding(.horizontal, AuroraMetrics.contentInset)
                                }
                            }
                        }
                        
                        // 3. 当前视频源推荐流（标题 -> 分类胶囊筛选 -> 双列海报网格）
                        VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                            HStack {
                                HStack(spacing: AuroraMetrics.spacingS) {
                                    Text("为你推荐 · \(repo.currentSource.name)")
                                        .font(.title2)
                                        .fontWeight(.bold)
                                        .foregroundStyle(AuroraColor.textPrimary)
                                    
                                    if repo.currentSource.isDirectConnect {
                                        Text("直连")
                                            .font(.caption2)
                                            .fontWeight(.black)
                                            .foregroundStyle(.white)
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(Capsule().fill(AuroraColor.forestGreen))
                                    }
                                }
                                
                                Spacer()
                                
                                Button(action: { showSourcePicker = true }) {
                                    HStack(spacing: 5) {
                                        Text("全量 \(VideoSourceCatalog.allSources.count) 源")
                                            .font(.footnote)
                                            .fontWeight(.semibold)
                                        Image(systemName: "slider.horizontal.3")
                                            .font(.caption)
                                    }
                                    .foregroundStyle(AuroraColor.brand)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 5)
                                    .background(AuroraColor.brand.opacity(0.12), in: Capsule())
                                    .overlay(Capsule().stroke(AuroraColor.brand.opacity(0.3), lineWidth: 1))
                                }
                            }
                            .padding(.horizontal, AuroraMetrics.contentInset)
                            
                            // 原生分类筛选 Chip 条：直接贴合在推荐网格上方，联动响应极佳
                            SourceFilterBar(
                                categories: repo.currentSource.categories,
                                selectedCategory: $selectedCategory
                            )
                            
                            if repo.isLoading {
                                VStack(spacing: AuroraMetrics.spacingM) {
                                    ProgressView()
                                        .controlSize(.regular)
                                        .tint(AuroraColor.brand)
                                    Text("正在连接 \(repo.currentSource.name)...")
                                        .font(.subheadline)
                                        .foregroundStyle(AuroraColor.textSecondary)
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 60)
                            } else if repo.feedVideos.isEmpty {
                                ContentUnavailableView {
                                    Label("暂无视频内容", systemImage: "film.stack")
                                } description: {
                                    Text("「\(repo.currentSource.name)」当前分类暂未检索到内容。")
                                } actions: {
                                    Button("切换至 91动漫") {
                                        repo.switchSource(to: VideoSourceCatalog.dongman91)
                                    }
                                    .buttonStyle(.borderedProminent)
                                    .tint(AuroraColor.brand)
                                }
                                .padding(.vertical, 40)
                            } else {
                                LazyVGrid(
                                    columns: [
                                        GridItem(.flexible(), spacing: AuroraMetrics.spacingM),
                                        GridItem(.flexible(), spacing: AuroraMetrics.spacingM)
                                    ],
                                    spacing: AuroraMetrics.spacingL
                                ) {
                                    ForEach(repo.feedVideos) { video in
                                        PosterCard(video: video) {
                                            onPlayVideo(video)
                                        }
                                    }
                                }
                                .padding(.horizontal, AuroraMetrics.contentInset)
                            }
                        }
                        .id("recommendSection")
                        
                        Spacer().frame(height: AuroraMetrics.spacingXXL)
                    }
                }
                .ignoresSafeArea(edges: .top)
                .onScrollGeometryChange(for: Bool.self) { geo in
                    geo.contentOffset.y > 60
                } action: { _, newValue in
                    isScrolled = newValue
                }
                .onAppear {
                    if CommandLine.arguments.contains("--scroll-recommend") {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                            withAnimation(AuroraMotion.smooth) {
                                proxy.scrollTo("recommendSection", anchor: .top)
                            }
                        }
                    }
                }
            }
        }
        .toolbarBackground((isScrolled || repo.billboardVideo == nil) ? .visible : .hidden, for: .navigationBar)
        .toolbarBackground(.regularMaterial, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                HStack(spacing: 6) {
                    Image(systemName: "sparkles.tv.fill")
                        .font(.headline)
                        .foregroundStyle(AuroraColor.brand)
                    Text("极光视界")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundStyle(AuroraColor.textPrimary)
                }
            }
            
            ToolbarItem(placement: .topBarTrailing) {
                HStack(spacing: AuroraMetrics.spacingM) {
                    AirPlayRoutePicker()
                        .frame(width: 32, height: 32)
                    
                    Button(action: onOpenSearch) {
                        Image(systemName: "magnifyingglass")
                            .font(.body)
                            .fontWeight(.semibold)
                            .foregroundStyle(.primary)
                    }
                }
            }
        }
        .onAppear {
            selectedCategory = repo.currentCategory
        }
        .onChange(of: selectedCategory) { _, newCat in
            if newCat != repo.currentCategory {
                repo.switchCategory(to: newCat)
            }
        }
        .onChange(of: repo.currentCategory) { _, newCat in
            selectedCategory = newCat
        }
        .onReceive(repo.$billboardVideo) { video in
            if CommandLine.arguments.contains("--auto-play"), let v = video {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                    onPlayVideo(v)
                }
            }
        }
        .sheet(isPresented: $showSourcePicker) {
            VideoSourcePickerSheet(
                selectedSource: repo.currentSource,
                onSelect: { source in
                    repo.switchSource(to: source)
                    selectedCategory = repo.currentCategory
                    showSourcePicker = false
                }
            )
        }
    }
}

// 视频源快速切换面板
struct VideoSourcePickerSheet: View {
    let selectedSource: VideoSourceDefinition
    let onSelect: (VideoSourceDefinition) -> Void
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        ZStack {
            AuroraColor.canvas.ignoresSafeArea()
            
            VStack(alignment: .leading, spacing: AuroraMetrics.spacingL) {
                HStack {
                    Text("全网 \(VideoSourceCatalog.allSources.count) 个视频源切换")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundStyle(AuroraColor.textPrimary)
                    Spacer()
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title3)
                            .foregroundStyle(AuroraColor.textSecondary)
                    }
                }
                .padding(.horizontal, AuroraMetrics.contentInset)
                .padding(.top, AuroraMetrics.spacingL)
                
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingXL) {
                        ForEach(VideoSourceGroup.allCases, id: \.self) { group in
                            VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                                Text(group.rawValue)
                                    .font(.subheadline)
                                    .fontWeight(.semibold)
                                    .foregroundStyle(AuroraColor.textSecondary)
                                    .padding(.horizontal, AuroraMetrics.contentInset)
                                
                                ForEach(VideoSourceCatalog.allSources.filter { $0.group == group }) { src in
                                    let isSelected = src.id == selectedSource.id
                                    Button(action: {
                                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                        onSelect(src)
                                    }) {
                                        HStack(spacing: AuroraMetrics.spacingM) {
                                            VStack(alignment: .leading, spacing: 3) {
                                                HStack(spacing: 6) {
                                                    Text(src.name)
                                                        .font(.body)
                                                        .fontWeight(.semibold)
                                                        .foregroundStyle(isSelected ? AuroraColor.brand : AuroraColor.textPrimary)
                                                    if src.isDirectConnect {
                                                        Text("大陆直连")
                                                            .font(.caption2)
                                                            .fontWeight(.bold)
                                                            .foregroundStyle(AuroraColor.forestGreen)
                                                    }
                                                }
                                                Text(src.description)
                                                    .font(.caption)
                                                    .foregroundStyle(AuroraColor.textSecondary)
                                            }
                                            
                                            Spacer()
                                            
                                            if isSelected {
                                                Image(systemName: "checkmark")
                                                    .font(.headline)
                                                    .fontWeight(.bold)
                                                    .foregroundStyle(AuroraColor.brand)
                                            }
                                        }
                                        .padding(.horizontal, AuroraMetrics.spacingL)
                                        .padding(.vertical, AuroraMetrics.spacingM)
                                        .background(
                                            RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                                                .fill(isSelected ? AuroraColor.brand.opacity(0.12) : AuroraColor.surface)
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                                                        .stroke(isSelected ? AuroraColor.brand.opacity(0.4) : AuroraColor.hairline, lineWidth: 1)
                                                )
                                        )
                                    }
                                    .buttonStyle(.plain)
                                    .padding(.horizontal, AuroraMetrics.contentInset)
                                }
                            }
                        }
                    }
                    .padding(.bottom, AuroraMetrics.spacingXXL)
                }
            }
        }
    }
}

// 顶部 Hero 骨架屏占位（平滑过渡，消除切换时的空白与闪烁）
private struct HeroLoadingPlaceholder: View {
    var body: some View {
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
