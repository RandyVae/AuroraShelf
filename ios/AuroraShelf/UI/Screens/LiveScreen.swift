import SwiftUI

/// 直播平台视觉规约（对齐 Apple TV「Channels」品牌视觉规范）
public struct LiveVisualSpec: Sendable {
    public let iconName: String
    public let gradientColors: [Color]
    public let accentColor: Color

    public init(iconName: String, gradientColors: [Color], accentColor: Color) {
        self.iconName = iconName
        self.gradientColors = gradientColors
        self.accentColor = accentColor
    }

    public static func forSource(_ id: String) -> LiveVisualSpec {
        switch id {
        case "stripchat":
            // Stripchat：深邃午夜蓝至电光洋红渐变
            return LiveVisualSpec(
                iconName: "video.fill",
                gradientColors: [Color(rgb: 0x0D1B2A), Color(rgb: 0xC2185B)],
                accentColor: Color(rgb: 0xFF4081)
            )
        case "chaturbate":
            // Chaturbate：黑曜石炭黑至耀金渐变
            return LiveVisualSpec(
                iconName: "flame.fill",
                gradientColors: [Color(rgb: 0x1A1A1A), Color(rgb: 0xEF6C00)],
                accentColor: Color(rgb: 0xFFA000)
            )
        default:
            return LiveVisualSpec(
                iconName: "tv.fill",
                gradientColors: [Color(rgb: 0x1C1C1E), Color(rgb: 0x2C2C2E)],
                accentColor: Color(rgb: 0xFF5252)
            )
        }
    }
}

/// Apple TV 风格的「直播平台（Channels）」横向货架
public struct LivePlatformsShelf: View {
    public let selectedSourceID: String
    public let onSelect: (String) -> Void

    public init(
        selectedSourceID: String,
        onSelect: @escaping (String) -> Void
    ) {
        self.selectedSourceID = selectedSourceID
        self.onSelect = onSelect
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
            HStack(spacing: AuroraMetrics.spacingS) {
                Text("直播平台")
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundStyle(AuroraColor.textPrimary)

                Spacer()

                Text("精选全球源")
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundStyle(AuroraColor.brand)
            }
            .padding(.horizontal, AuroraMetrics.contentInset)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: AuroraMetrics.spacingM) {
                    ForEach(Array(LiveSourceCatalog.allSources), id: \.id) { source in
                        let isSelected = selectedSourceID == source.id
                        let spec = LiveVisualSpec.forSource(source.id)

                        Button {
                            onSelect(source.id)
                        } label: {
                            VStack(spacing: 8) {
                                ZStack {
                                    // 1. 底层品牌专属色彩渐变（选中态全量释放；未选态透出微光）
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .fill(
                                            LinearGradient(
                                                colors: spec.gradientColors,
                                                startPoint: .topLeading,
                                                endPoint: .bottomTrailing
                                            )
                                        )
                                        .opacity(isSelected ? 1.0 : 0.35)

                                    if !isSelected {
                                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                                            .fill(AuroraColor.surfaceCard.opacity(0.65))
                                    }

                                    // 2. 苹果官方材质级高光斜切（Apple TV Specular Bevel Highlight）
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .fill(
                                            LinearGradient(
                                                stops: [
                                                    .init(color: .white.opacity(isSelected ? 0.35 : 0.12), location: 0.0),
                                                    .init(color: .white.opacity(isSelected ? 0.08 : 0.02), location: 0.45),
                                                    .init(color: .clear, location: 0.5)
                                                ],
                                                startPoint: .top,
                                                endPoint: .bottom
                                            )
                                        )

                                    // 3. 核心官方 SF Symbol 原生液态玻璃徽章与 LIVE 呼吸小红点
                                    ZStack {
                                        Circle()
                                            .fill(.white.opacity(isSelected ? 0.18 : 0.08))
                                            .overlay(Circle().stroke(.white.opacity(isSelected ? 0.35 : 0.16), lineWidth: 0.8))
                                        
                                        HStack(spacing: 4) {
                                            Image(systemName: spec.iconName)
                                                .font(.system(size: 16, weight: .semibold))
                                                .symbolRenderingMode(.hierarchical)
                                                .foregroundStyle(isSelected ? .white : spec.accentColor)

                                            Circle()
                                                .fill(Color.red)
                                                .frame(width: 5, height: 5)
                                        }
                                    }
                                    .frame(width: 44, height: 36)
                                    .shadow(color: .black.opacity(isSelected ? 0.28 : 0.12), radius: 3, y: 1)

                                    // 4. 外轮廓高亮微光描边
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .stroke(
                                            isSelected
                                                ? LinearGradient(colors: [Color.white.opacity(0.85), spec.accentColor], startPoint: .topLeading, endPoint: .bottomTrailing)
                                                : LinearGradient(colors: [Color.white.opacity(0.12), Color.white.opacity(0.04)], startPoint: .top, endPoint: .bottom),
                                            lineWidth: isSelected ? 2 : 1
                                        )
                                }
                                .frame(width: 100, height: 56)
                                .shadow(
                                    color: isSelected ? spec.accentColor.opacity(0.45) : .clear,
                                    radius: 8,
                                    x: 0,
                                    y: 3
                                )

                                Text(source.name)
                                    .font(.system(size: 11, weight: isSelected ? .bold : .medium, design: .rounded))
                                    .foregroundStyle(isSelected ? AuroraColor.textPrimary : AuroraColor.textSecondary)
                                    .lineLimit(1)
                                    .frame(width: 100)
                            }
                        }
                        .buttonStyle(AppleSpringCardButtonStyle())
                        .accessibilityLabel("直播平台 \(source.name)，\(isSelected ? "当前正在观看" : "轻点切换")")
                    }
                }
                .padding(.horizontal, AuroraMetrics.contentInset)
            }
        }
    }
}

/// 原生 Apple TV / 流媒体风格的精选直播卡片
/// 严格采用 4:3 原生直播比例（180 × 135 pt），展示实时 LIVE 状态、在线人数与主播头像，杜绝生硬的 1234 排行大字
public struct LiveFeaturedCard: View {
    public let stream: LiveStreamItem
    public let onSelect: () -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private var cardWidth: CGFloat {
        dynamicTypeSize.isAccessibilitySize ? 210 : AuroraMetrics.liveCardWidth
    }

    private var cardHeight: CGFloat {
        cardWidth / AuroraMetrics.liveAspect
    }

    public init(stream: LiveStreamItem, onSelect: @escaping () -> Void) {
        self.stream = stream
        self.onSelect = onSelect
    }

    public var body: some View {
        Button(action: onSelect) {
            VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                ZStack(alignment: .topTrailing) {
                    AuroraCoverImage(
                        url: stream.snapshotUrl,
                        title: stream.username,
                        aspectRatio: AuroraMetrics.liveAspect,
                        contentMode: .fill,
                        cornerRadius: AuroraMetrics.cardRadius,
                        placeholderStyle: .live
                    )
                    .frame(width: cardWidth, height: cardHeight)
                    .overlay(
                        RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                            .stroke(AuroraColor.hairline, lineWidth: 1)
                    )

                    // LIVE 状态与在线人数微型胶囊
                    HStack(spacing: 4) {
                        Circle()
                            .fill(Color.red)
                            .frame(width: 6, height: 6)
                        Text("LIVE")
                            .font(.system(size: 10, weight: .black))
                            .foregroundStyle(.white)
                        if let viewCount = stream.viewCount.value {
                            Text("\(viewCount)")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundStyle(.white.opacity(0.85))
                        }
                    }
                    .padding(.horizontal, 6)
                    .padding(.vertical, 3)
                    .background(.regularMaterial, in: Capsule())
                    .padding(6)
                }

                HStack(spacing: 8) {
                    AuroraCoverImage(
                        url: stream.avatarUrl,
                        aspectRatio: 1,
                        contentMode: .fill,
                        cornerRadius: 12,
                        placeholderStyle: .live
                    )
                    .frame(width: 24, height: 24)
                    .clipShape(Circle())
                    .overlay(
                        Circle().stroke(AuroraColor.hairline, lineWidth: 0.5)
                    )

                    VStack(alignment: .leading, spacing: 2) {
                        Text(stream.username)
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundStyle(AuroraColor.textPrimary)
                            .lineLimit(1, reservesSpace: true)

                        Text(stream.tags.isEmpty ? "精彩直播" : stream.tags.prefix(2).joined(separator: " · "))
                            .font(.caption2)
                            .foregroundStyle(AuroraColor.textSecondary)
                            .lineLimit(1, reservesSpace: true)
                    }
                }
                .frame(width: cardWidth, alignment: .leading)
            }
        }
        .buttonStyle(AppleSpringCardButtonStyle())
        .contextMenu {
            Button {
                onSelect()
            } label: {
                Label("进入直播间", systemImage: "play.circle.fill")
            }

            if let url = URL(string: stream.roomUrl), !stream.roomUrl.isEmpty {
                ShareLink(item: url) {
                    Label("分享直播间", systemImage: "square.and.arrow.up")
                }
            }
        } preview: {
            VStack(alignment: .leading, spacing: 8) {
                AuroraCoverImage(
                    url: stream.snapshotUrl,
                    title: stream.username,
                    aspectRatio: AuroraMetrics.liveAspect,
                    contentMode: .fill,
                    cornerRadius: 12,
                    placeholderStyle: .live
                )
                .frame(width: 280, height: 210)

                VStack(alignment: .leading, spacing: 2) {
                    Text(stream.username)
                        .font(.headline)
                        .foregroundStyle(Color.white)
                    Text("\(stream.viewCount.value ?? 0) 人在线观看 · 即时直播")
                        .font(.caption)
                        .foregroundStyle(Color.secondary)
                }
                .padding(.horizontal, 10)
                .padding(.bottom, 10)
            }
            .background(AuroraColor.canvas)
        }
        .accessibilityLabel("直播间 \(stream.username)，\(stream.viewCount.value ?? 0) 人在看")
    }
}

/// 原生 Live Hero 聚焦横幅：1:1 对齐 Apple TV 聚光灯大卡
public struct LiveHeroBanner: View {
    public let stream: LiveStreamItem
    public let sourceName: String
    public let onWatch: () -> Void
    public let onCycle: () -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    public init(
        stream: LiveStreamItem,
        sourceName: String,
        onWatch: @escaping () -> Void,
        onCycle: @escaping () -> Void
    ) {
        self.stream = stream
        self.sourceName = sourceName
        self.onWatch = onWatch
        self.onCycle = onCycle
    }

    private var formFactor: DeviceFormFactor {
        DeviceLayout.resolveCurrent(horizontalSizeClass: horizontalSizeClass)
    }

    private var heroHeight: CGFloat {
        DeviceLayout.heroHeight(for: formFactor, screenHeight: DeviceLayout.currentWindowSize.height)
    }

    private var isWideAspect: Bool {
        formFactor.isWide
    }

    public var body: some View {
        ZStack(alignment: .bottom) {
            Color.clear
                .overlay(
                    AuroraCoverImage(
                        url: stream.snapshotUrl,
                        title: stream.username,
                        aspectRatio: nil,
                        contentMode: .fill,
                        cornerRadius: 0
                    )
                )
                .frame(height: heroHeight)
                .frame(maxWidth: .infinity)
                .clipped()

            VStack(spacing: 0) {
                LinearGradient(
                    colors: [Color.black.opacity(0.7), Color.clear],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: 120)

                Spacer()

                LinearGradient(
                    stops: [
                        .init(color: .clear, location: 0.0),
                        .init(color: AuroraColor.canvas.opacity(0.6), location: 0.35),
                        .init(color: AuroraColor.canvas.opacity(0.95), location: 0.75),
                        .init(color: AuroraColor.canvas, location: 1.0)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: formFactor == .duoFolded ? 200 : (formFactor.isWide ? 320 : 260))
            }
            .frame(maxWidth: .infinity, maxHeight: heroHeight)

            VStack(spacing: AuroraMetrics.spacingM) {
                Text(stream.username)
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundStyle(AuroraColor.textPrimary)
                    .multilineTextAlignment(.center)
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 2)
                    .fixedSize(horizontal: false, vertical: true)
                    .shadow(color: .black.opacity(0.95), radius: 8, x: 0, y: 3)
                    .padding(.horizontal, AuroraMetrics.contentInset)

                metadataView
                    .shadow(color: .black.opacity(0.9), radius: 6, x: 0, y: 2)

                HStack(spacing: AuroraMetrics.spacingM) {
                    watchButton
                        .frame(maxWidth: formFactor.isWide ? 220 : .infinity)
                    cycleButton
                }
                .frame(maxWidth: DeviceLayout.heroActionsMaxWidth(for: formFactor) ?? .infinity)
                .padding(.horizontal, AuroraMetrics.contentInset)
                .padding(.bottom, AuroraMetrics.spacingL)
            }
        }
    }

    @ViewBuilder
    private var metadataView: some View {
        HStack(spacing: AuroraMetrics.spacingS) {
            HStack(spacing: 4) {
                Circle()
                    .fill(Color.red)
                    .frame(width: 6, height: 6)
                Text("LIVE 直播中")
                    .font(.caption2.bold())
                    .foregroundStyle(.white)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(Color.red.opacity(0.85), in: Capsule())

            if let viewCount = stream.viewCount.value {
                Text("\(viewCount) 人在线")
                    .font(.caption.bold())
                    .foregroundStyle(.white)
                Text("•")
                    .foregroundStyle(.white.opacity(0.6))
            }

            Text(sourceName)
                .font(.caption.bold())
                .foregroundStyle(AuroraColor.brandLabel)

            if !stream.title.isEmpty {
                Text("•")
                    .foregroundStyle(.white.opacity(0.6))
                Text(stream.title)
                    .font(.caption2)
                    .foregroundStyle(.white.opacity(0.85))
                    .lineLimit(1)
            }
        }
        .lineLimit(1)
        .fixedSize(horizontal: false, vertical: true)
    }

    private var watchButton: some View {
        Button(action: onWatch) {
            HStack(spacing: 6) {
                Image(systemName: "play.fill")
                    .font(.headline)
                Text("立即观看")
                    .font(.headline.weight(.bold))
            }
            .frame(maxWidth: .infinity, minHeight: 46)
            .background(Color.white, in: Capsule())
            .foregroundStyle(Color.black)
        }
        .buttonStyle(AppleSpringCardButtonStyle())
    }

    private var cycleButton: some View {
        Button(action: onCycle) {
            HStack(spacing: 6) {
                Image(systemName: "shuffle")
                    .font(.headline)
                Text("换个主播")
                    .font(.headline.weight(.semibold))
            }
            .frame(minHeight: 46)
            .padding(.horizontal, 16)
            .background(.ultraThinMaterial, in: Capsule())
            .background(Color.black.opacity(0.35), in: Capsule())
            .overlay(
                Capsule()
                    .stroke(Color.white.opacity(0.25), lineWidth: 1)
            )
            .foregroundStyle(.white)
        }
        .buttonStyle(AppleSpringCardButtonStyle())
        .accessibilityHint("切换另一位推荐主播")
    }
}

public struct LiveScreen: View {
    @ObservedObject public var repo = LiveRepository.shared
    public let onOpenSearch: () -> Void
    @State private var activeStream: LiveStreamItem? = nil
    @State private var spotlightIndex: Int = 0
    @State private var isScrolled: Bool = false
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    
    public init(onOpenSearch: @escaping () -> Void = {}) {
        self.onOpenSearch = onOpenSearch
    }

    private var spotlightStream: LiveStreamItem? {
        guard !filteredStreams.isEmpty else { return nil }
        let validIndex = spotlightIndex % filteredStreams.count
        return filteredStreams[validIndex]
    }

    private var top10Streams: [LiveStreamItem] {
        Array(filteredStreams.prefix(10))
    }

    private var currentSourceName: String {
        LiveSourceCatalog.allSources.first(where: { $0.id == repo.selectedSourceID })?.name ?? "全球直播"
    }
    
    public var body: some View {
        ZStack {
            AuroraColor.canvas.ignoresSafeArea()
            
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: AuroraMetrics.spacingXL) {
                    // 1. 顶部 Live Hero 聚焦大卡（Apple TV 影院级画幅）
                    if let spotlight = spotlightStream {
                        LiveHeroBanner(
                            stream: spotlight,
                            sourceName: currentSourceName,
                            onWatch: { activeStream = spotlight },
                            onCycle: {
                                withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                                    spotlightIndex = (spotlightIndex + 1) % max(1, filteredStreams.count)
                                }
                            }
                        )
                    } else if repo.isLoading {
                        HeroLoadingPlaceholder()
                    } else {
                        Spacer().frame(height: 60)
                    }

                    // 2. Apple TV 核心层级：精选直播平台（Channels 货架）
                    LivePlatformsShelf(
                        selectedSourceID: repo.selectedSourceID,
                        onSelect: { sourceID in
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                                repo.selectSource(sourceID)
                            }
                        }
                    )

                    // 3. 热门精选直播（标准 4:3 原生画幅，实时状态与在线人数，拔除生硬的 1234 排行大字）
                    if !top10Streams.isEmpty {
                        VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                            HStack(spacing: AuroraMetrics.spacingS) {
                                Text("热门推荐")
                                    .font(.title2)
                                    .fontWeight(.bold)
                                    .foregroundStyle(AuroraColor.textPrimary)

                                HStack(spacing: 4) {
                                    Circle()
                                        .fill(Color.red)
                                        .frame(width: 6, height: 6)
                                    Text("FEATURED")
                                        .font(.caption2)
                                        .fontWeight(.black)
                                        .foregroundStyle(AuroraColor.accentRed)
                                }
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(AuroraColor.accentRed.opacity(0.12), in: Capsule())
                            }
                            .padding(.horizontal, AuroraMetrics.contentInset)

                            ScrollView(.horizontal, showsIndicators: false) {
                                LazyHStack(alignment: .top, spacing: AuroraMetrics.spacingM) {
                                    ForEach(top10Streams) { item in
                                        LiveFeaturedCard(stream: item) {
                                            activeStream = item
                                        }
                                    }
                                }
                                .padding(.horizontal, AuroraMetrics.contentInset)
                            }
                        }
                    }

                    // 4. 原生分类胶囊筛选条（女性主播/情侣/男性/跨性别）
                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                        HStack(spacing: AuroraMetrics.spacingS) {
                            Text("热门直播")
                                .font(.title3)
                                .fontWeight(.bold)
                                .foregroundStyle(AuroraColor.textPrimary)

                            Spacer()

                            Text("\(filteredStreams.count) 位在线")
                                .font(.subheadline)
                                .foregroundStyle(AuroraColor.textSecondary)
                        }
                        .padding(.horizontal, AuroraMetrics.contentInset)

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: AuroraMetrics.spacingS) {
                                ForEach(repo.categories, id: \.self) { cat in
                                    let isSelected = cat == repo.selectedGender
                                    categoryButton(cat, isSelected: isSelected)
                                }
                            }
                            .padding(.horizontal, AuroraMetrics.contentInset)
                            .padding(.vertical, AuroraMetrics.spacingXS)
                        }

                        feedState

                        // 主播网格
                        LazyVGrid(columns: liveGridColumns, spacing: AuroraMetrics.spacingL) {
                            ForEach(filteredStreams) { item in
                                Button {
                                    activeStream = item
                                } label: {
                                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                                        ZStack(alignment: .topTrailing) {
                                            AuroraCoverImage(
                                                url: item.snapshotUrl,
                                                title: item.username,
                                                aspectRatio: AuroraMetrics.liveAspect,
                                                contentMode: .fill,
                                                cornerRadius: AuroraMetrics.cardRadius,
                                                placeholderStyle: .live
                                            )
                                            .frame(minWidth: 0, maxWidth: .infinity)
                                            .overlay(
                                                RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                                                    .stroke(AuroraColor.hairline, lineWidth: 1)
                                            )

                                            HStack(spacing: 4) {
                                                Circle()
                                                    .fill(Color.red)
                                                    .frame(width: 6, height: 6)
                                                Text("LIVE")
                                                    .font(.caption2)
                                                    .fontWeight(.black)
                                                    .foregroundStyle(.primary)
                                                if let viewCount = item.viewCount.value {
                                                    Text("\(viewCount)")
                                                        .font(.caption2)
                                                        .fontWeight(.bold)
                                                        .foregroundStyle(.secondary)
                                                }
                                            }
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 4)
                                            .background(.regularMaterial, in: Capsule())
                                            .padding(AuroraMetrics.spacingS)
                                        }
                                        .frame(minWidth: 0, maxWidth: .infinity)
                                        .clipped()

                                        HStack(spacing: AuroraMetrics.spacingS) {
                                            AuroraCoverImage(
                                                url: item.avatarUrl,
                                                aspectRatio: 1,
                                                contentMode: .fill,
                                                cornerRadius: AuroraMetrics.minTouchTarget / 2,
                                                placeholderStyle: .live
                                            )
                                            .frame(width: 24, height: 24)
                                            .clipShape(Circle())
                                            .clipped()

                                            Text(item.username)
                                                .font(.subheadline)
                                                .fontWeight(.semibold)
                                                .foregroundStyle(AuroraColor.textPrimary)
                                                .lineLimit(1, reservesSpace: true)
                                        }

                                        Text(item.title.isEmpty ? (item.tags.first ?? "精彩直播中") : item.title)
                                            .font(.caption)
                                            .foregroundStyle(AuroraColor.textSecondary)
                                            .lineLimit(1, reservesSpace: true)
                                    }
                                    .frame(maxWidth: .infinity, alignment: .topLeading)
                                }
                                .buttonStyle(AppleSpringCardButtonStyle())
                                .accessibilityElement(children: .combine)
                                .accessibilityLabel("主播 \(item.username), \(item.title.isEmpty ? "" : item.title + ", ")在线人数 \(item.viewCount)")
                                .accessibilityHint("打开直播间播放并查看实时聊天")
                            }
                        }
                        .padding(.horizontal, AuroraMetrics.contentInset)
                        .frame(maxWidth: 1080)
                        .frame(maxWidth: .infinity)
                    }

                    // Keep clear of floating tab bar
                    Spacer().frame(height: max(AuroraMetrics.spacingXXL, 96))
                }
            }
            .ignoresSafeArea(edges: .top)
            .onScrollGeometryChange(for: Bool.self) { geo in
                geo.contentOffset.y > 60
            } action: { _, newValue in
                isScrolled = newValue
            }
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground((isScrolled || spotlightStream == nil) ? .visible : .hidden, for: .navigationBar)
        .toolbarBackground(.regularMaterial, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                NetgreyMarkView(width: 20, height: 28)
            }
            ToolbarItem(placement: .topBarTrailing) {
                HStack(spacing: AuroraMetrics.spacingM) {
                    AirPlayRoutePicker()
                        .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)

                    Button(action: onOpenSearch) {
                        Image(systemName: "magnifyingglass")
                            .font(.body)
                            .fontWeight(.semibold)
                            .foregroundStyle(.primary)
                    }
                    .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                    .contentShape(Rectangle())
                    .accessibilityLabel("搜索直播")
                }
            }
        }
        .navigationDestination(item: $activeStream) { stream in
            LiveDetailView(
                stream: stream,
                relatedStreams: repo.liveStreams.filter { $0.id != stream.id },
                onSelectStream: { next in
                    activeStream = next
                },
                onDismiss: {
                    activeStream = nil
                }
            )
            .id(stream.id)
            .toolbar(.hidden, for: .tabBar)
            .toolbar(.hidden, for: .navigationBar)
            .navigationBarBackButtonHidden(true)
        }
        .onAppear {
            if CommandLine.arguments.contains("--select-stripchat") {
                repo.selectSource(LiveSourceCatalog.stripchatID)
            }
        }
        .onReceive(repo.$liveStreams) { newStreams in
            if CommandLine.arguments.contains("--auto-open-live-detail"), let first = newStreams.first, activeStream == nil {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                    activeStream = first
                }
            }
        }
    }

    @ViewBuilder
    private var feedState: some View {
        Group {
            if repo.liveStreams.isEmpty && repo.isLoading {
                VStack(spacing: AuroraMetrics.spacingM) {
                    ProgressView()
                        .tint(AuroraColor.brand)
                    Text("live.loading".localized)
                        .font(.subheadline)
                        .foregroundStyle(AuroraColor.textSecondary)
                }
                .frame(maxWidth: .infinity, minHeight: 300)
            } else if repo.liveStreams.isEmpty, let error = repo.loadErrorMessage {
                ContentUnavailableView {
                    Label("无法加载直播", systemImage: "wifi.exclamationmark")
                } description: {
                    Text(error)
                } actions: {
                    retryButton
                }
                .frame(maxWidth: .infinity, minHeight: 300)
            } else if repo.liveStreams.isEmpty {
                ContentUnavailableView {
                    Label("live.empty".localized, systemImage: "antenna.radiowaves.left.and.right.slash")
                } description: {
                    Text("当前直播源没有正在进行的公开直播。")
                } actions: {
                    retryButton
                }
                .frame(maxWidth: .infinity, minHeight: 300)
            } else {
                if let error = repo.loadErrorMessage {
                    HStack(spacing: AuroraMetrics.spacingS) {
                        Label(error, systemImage: "exclamationmark.triangle")
                            .font(.caption)
                            .foregroundStyle(AuroraColor.warning)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Button("重试", systemImage: "arrow.clockwise") {
                            repo.loadStreams()
                        }
                        .labelStyle(.iconOnly)
                    }
                    .padding(.horizontal, AuroraMetrics.contentInset)
                }

                if filteredStreams.isEmpty {
                    ContentUnavailableView {
                        Label("该分类暂无直播", systemImage: "person.slash")
                    } description: {
                        Text("当前直播列表中没有符合所选分类的主播。")
                    } actions: {
                        Button("显示全部", systemImage: "arrow.uturn.backward") {
                            withAnimation(AuroraMotion.selection(reduceMotion: reduceMotion)) {
                                repo.selectedGender = "全部"
                            }
                        }
                        .buttonStyle(.bordered)
                    }
                    .frame(maxWidth: .infinity, minHeight: 300)
                }
            }
        }
    }

    private var filteredStreams: [LiveStreamItem] {
        guard let selectedCode = Self.genderCodes[repo.selectedGender] else {
            return repo.liveStreams
        }
        return repo.liveStreams.filter { $0.gender.lowercased() == selectedCode }
    }

    @ViewBuilder
    private func categoryButton(_ category: String, isSelected: Bool) -> some View {
        if isSelected {
            Button {
                withAnimation(AuroraMotion.selection(reduceMotion: reduceMotion)) {
                    repo.selectedGender = category
                }
            } label: {
                Text(category).font(.subheadline.weight(.semibold)).lineLimit(1)
            }
            .buttonStyle(.borderedProminent)
            .tint(AuroraColor.brand)
            .clipShape(Capsule())
            .frame(minHeight: AuroraMetrics.minTouchTarget)
        } else {
            Button {
                withAnimation(AuroraMotion.selection(reduceMotion: reduceMotion)) {
                    repo.selectedGender = category
                }
            } label: {
                Text(category).font(.subheadline).lineLimit(1)
            }
            .buttonStyle(.bordered)
            .tint(.secondary)
            .clipShape(Capsule())
            .frame(minHeight: AuroraMetrics.minTouchTarget)
        }
    }

    private var liveGridColumns: [GridItem] {
        let minimumWidth: CGFloat = dynamicTypeSize.isAccessibilitySize ? 300 : 160
        return [GridItem(.adaptive(minimum: minimumWidth, maximum: 420), spacing: AuroraMetrics.spacingM)]
    }

    private static let genderCodes: [String: String] = [
        "女性主播": "f",
        "情侣互动": "c",
        "男性主播": "m",
        "跨性别": "s"
    ]

    private var retryButton: some View {
        Button("重试", systemImage: "arrow.clockwise") {
            repo.loadStreams()
        }
        .buttonStyle(.borderedProminent)
        .tint(AuroraColor.brand)
    }
}
