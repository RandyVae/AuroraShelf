import SwiftUI

public struct HomeScreen: View {
    @ObservedObject public var repo = VideoRepository.shared
    public let onPlayVideo: (VideoItem) -> Void
    public let onOpenSearch: () -> Void
    
    @State private var showSourcePicker: Bool = false
    @State private var selectedCategory: String = "全部"
    
    public init(
        onPlayVideo: @escaping (VideoItem) -> Void,
        onOpenSearch: @escaping () -> Void
    ) {
        self.onPlayVideo = onPlayVideo
        self.onOpenSearch = onOpenSearch
    }
    
    public var body: some View {
        ZStack(alignment: .top) {
            AuroraTheme.background.ignoresSafeArea()
            
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 24) {
                    // 1. 顶部聚光灯大卡（Netflix Billboard）
                    if let billboard = repo.billboardVideo {
                        NetflixBillboard(
                            video: billboard,
                            onPlay: { onPlayVideo(billboard) },
                            onDetail: { onPlayVideo(billboard) }
                        )
                    }
                    
                    // 2. 今日热播 Top 10
                    if !repo.top10Videos.isEmpty {
                        VStack(alignment: .leading, spacing: 14) {
                            HStack(spacing: 6) {
                                Text("今日热播")
                                    .font(.system(size: 19, weight: .bold))
                                    .foregroundColor(AuroraTheme.textPrimary)
                                
                                Text("TOP 10")
                                    .font(.system(size: 15, weight: .black, design: .rounded))
                                    .foregroundColor(AuroraTheme.crimson)
                            }
                            .padding(.horizontal, 16)
                            
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 20) {
                                    ForEach(Array(repo.top10Videos.enumerated()), id: \.element.id) { index, video in
                                        NetflixTop10Card(video: video, rank: index + 1) {
                                            onPlayVideo(video)
                                        }
                                    }
                                }
                                .padding(.horizontal, 16)
                            }
                        }
                    }
                    
                    // 3. 当前视频源主要内容流
                    VStack(alignment: .leading, spacing: 14) {
                        HStack {
                            HStack(spacing: 6) {
                                Text("为你推荐 · \(repo.currentSource.name)")
                                    .font(.system(size: 19, weight: .bold))
                                    .foregroundColor(AuroraTheme.textPrimary)
                                
                                if repo.currentSource.isDirectConnect {
                                    Text("直连")
                                        .font(.system(size: 10, weight: .black))
                                        .foregroundColor(.white)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Capsule().fill(Color.green.opacity(0.8)))
                                }
                            }
                            
                            Spacer()
                            
                            Button(action: { showSourcePicker = true }) {
                                HStack(spacing: 3) {
                                    Text("切换视频源 (\(VideoSourceCatalog.allSources.count)源)")
                                        .font(.system(size: 12, weight: .medium))
                                    Image(systemName: "chevron.up.chevron.down")
                                        .font(.system(size: 10))
                                }
                                .foregroundColor(AuroraTheme.crimsonGlow)
                            }
                        }
                        .padding(.horizontal, 16)
                        
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 14) {
                                ForEach(repo.feedVideos) { video in
                                    NetflixPosterCard(video: video, width: 135, height: 195) {
                                        onPlayVideo(video)
                                    }
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }
                    
                    // 底部优雅呼吸间距（原生 TabView 自动管理安全区与毛玻璃穿透折射）
                    Spacer().frame(height: 32)
                }
            }
            
            // 顶栏浮层（N Logo + 视频源指示 + 搜索 + 吸顶分类，彻底避让系统状态栏）
            VStack(spacing: 0) {
                NetflixTopBar(
                    title: "Netgrey",
                    searchPlaceholder: "搜索",
                    sourceName: repo.currentSource.name,
                    onSourcePicker: { showSourcePicker = true },
                    onSearch: onOpenSearch,
                    onCast: {
                        let impact = UIImpactFeedbackGenerator(style: .light)
                        impact.impactOccurred()
                    },
                    topSafeAreaHeight: 52
                )
                
                // 分类条
                CategoryPillBar(
                    categories: repo.currentSource.categories,
                    selectedCategory: $selectedCategory
                )
                .padding(.bottom, 8)
            }
            .background(
                LinearGradient(
                    colors: [Color.black.opacity(0.96), Color.black.opacity(0.75), Color.clear],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea(edges: .top)
            )
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

// 19 视频源快速切换面板
struct VideoSourcePickerSheet: View {
    let selectedSource: VideoSourceDefinition
    let onSelect: (VideoSourceDefinition) -> Void
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        ZStack {
            AuroraTheme.background.ignoresSafeArea()
            
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text("全网 19 个视频源切换")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                    Spacer()
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 20))
                            .foregroundColor(.white.opacity(0.4))
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 16)
                
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 20) {
                        ForEach(VideoSourceGroup.allCases, id: \.self) { group in
                            VStack(alignment: .leading, spacing: 10) {
                                Text(group.rawValue)
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(AuroraTheme.textSecondary)
                                    .padding(.horizontal, 16)
                                
                                ForEach(VideoSourceCatalog.allSources.filter { $0.group == group }) { src in
                                    let isSelected = src.id == selectedSource.id
                                    Button(action: {
                                        onSelect(src)
                                    }) {
                                        HStack(spacing: 12) {
                                            VStack(alignment: .leading, spacing: 3) {
                                                HStack(spacing: 6) {
                                                    Text(src.name)
                                                        .font(.system(size: 15, weight: .semibold))
                                                        .foregroundColor(isSelected ? AuroraTheme.crimsonGlow : .white)
                                                    if src.isDirectConnect {
                                                        Text("大陆直连")
                                                            .font(.system(size: 9, weight: .bold))
                                                            .foregroundColor(.green)
                                                    }
                                                }
                                                Text(src.description)
                                                    .font(.system(size: 11))
                                                    .foregroundColor(AuroraTheme.textTertiary)
                                            }
                                            
                                            Spacer()
                                            
                                            if isSelected {
                                                Image(systemName: "checkmark")
                                                    .font(.system(size: 14, weight: .bold))
                                                    .foregroundColor(AuroraTheme.crimson)
                                            }
                                        }
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 12)
                                        .background(
                                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                .fill(isSelected ? AuroraTheme.crimson.opacity(0.12) : AuroraTheme.surface)
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                        .stroke(isSelected ? AuroraTheme.crimson.opacity(0.4) : AuroraTheme.glassBorder, lineWidth: 1)
                                                )
                                        )
                                    }
                                    .buttonStyle(PlainButtonStyle())
                                    .padding(.horizontal, 16)
                                }
                            }
                        }
                    }
                    .padding(.bottom, 24)
                }
            }
        }
    }
}
