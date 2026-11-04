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
                        // 1. 顶部 Hero 聚焦大卡（4:5 竖版黄金比例，延伸至屏幕顶端）
                        if let billboard = repo.billboardVideo {
                            HeroBanner(
                                video: billboard,
                                onPlay: { onPlayVideo(billboard) },
                                onDetail: { onPlayVideo(billboard) }
                            )
                        }
                        
                        // 2. 原生分类筛选 Chip 条
                        SourceFilterBar(
                            categories: repo.currentSource.categories,
                            selectedCategory: $selectedCategory
                        )
                        
                        // 3. 今日热播 Top 10
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
                        
                        // 4. 当前视频源推荐流（双列网格排布）
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
                                    HStack(spacing: 4) {
                                        Text("切换视频源 (\(VideoSourceCatalog.allSources.count)源)")
                                            .font(.subheadline)
                                        Image(systemName: "chevron.up.chevron.down")
                                            .font(.caption2)
                                    }
                                    .foregroundStyle(AuroraColor.brand)
                                }
                            }
                            .padding(.horizontal, AuroraMetrics.contentInset)
                            
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
        .toolbarBackground(isScrolled ? .visible : .hidden, for: .navigationBar)
        .toolbarBackground(.regularMaterial, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Text("Netgrey")
                    .font(.title3)
                    .fontWeight(.heavy)
                    .foregroundStyle(AuroraColor.brand)
                    .fixedSize()
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
        .onChange(of: selectedCategory) { _, newCat in
            repo.switchCategory(to: newCat)
        }
        .onChange(of: repo.currentSource.id) { _, _ in
            selectedCategory = repo.currentSource.categories.first ?? "全部"
        }
        .sheet(isPresented: $showSourcePicker) {
            VideoSourcePickerSheet(
                selectedSource: repo.currentSource,
                onSelect: { source in
                    repo.switchSource(to: source)
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
