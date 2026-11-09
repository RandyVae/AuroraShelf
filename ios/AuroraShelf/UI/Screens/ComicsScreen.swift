import SwiftUI

struct ComicDetailLoadingState {
    private(set) var requestID: UUID?

    var isLoading: Bool { requestID != nil }

    mutating func beginRequest() -> UUID {
        let requestID = UUID()
        self.requestID = requestID
        return requestID
    }

    mutating func cancelRequest() {
        requestID = nil
    }

    mutating func finishRequest(_ requestID: UUID) -> Bool {
        guard self.requestID == requestID else { return false }
        self.requestID = nil
        return true
    }
}

/// 漫画平台视觉规约（对齐 Apple TV「Channels」品牌视觉规范）
public struct ComicVisualSpec: Sendable {
    public let iconName: String
    public let gradientColors: [Color]
    public let accentColor: Color

    public init(iconName: String, gradientColors: [Color], accentColor: Color) {
        self.iconName = iconName
        self.gradientColors = gradientColors
        self.accentColor = accentColor
    }

    public static func forSource(_ id: String) -> ComicVisualSpec {
        switch id {
        case "baozi":
            // 包子漫画：温暖活力暖橙渐变
            return ComicVisualSpec(
                iconName: "doc.richtext.fill",
                gradientColors: [Color(rgb: 0xE65100), Color(rgb: 0xF57C00)],
                accentColor: Color(rgb: 0xFFB74D)
            )
        case "picacg":
            // 哔咔漫画：霓虹玫瑰粉洋红
            return ComicVisualSpec(
                iconName: "heart.text.square.fill",
                gradientColors: [Color(rgb: 0x880E4F), Color(rgb: 0xD81B60)],
                accentColor: Color(rgb: 0xF48FB1)
            )
        case "jm":
            // 禁漫天堂：烈焰炽红渐变
            return ComicVisualSpec(
                iconName: "flame.fill",
                gradientColors: [Color(rgb: 0xB71C1C), Color(rgb: 0xE53935)],
                accentColor: Color(rgb: 0xFF8A80)
            )
        case "komiic":
            // Komiic：蔚蓝天青极光
            return ComicVisualSpec(
                iconName: "bookmark.fill",
                gradientColors: [Color(rgb: 0x01579B), Color(rgb: 0x0288D1)],
                accentColor: Color(rgb: 0x81D4FA)
            )
        case "copycomic":
            // 拷贝漫画：暗曜紫罗兰
            return ComicVisualSpec(
                iconName: "books.vertical.fill",
                gradientColors: [Color(rgb: 0x311B92), Color(rgb: 0x5E35B1)],
                accentColor: Color(rgb: 0xB39DDB)
            )
        case "91dongman":
            // 91动漫：二次元电光紫品红
            return ComicVisualSpec(
                iconName: "sparkles.tv.fill",
                gradientColors: [Color(rgb: 0x4A148C), Color(rgb: 0x8E24AA)],
                accentColor: Color(rgb: 0xCE93D8)
            )
        case "eightMuses":
            // 8Muses：暗夜翡翠森林绿
            return ComicVisualSpec(
                iconName: "photo.on.rectangle.angled",
                gradientColors: [Color(rgb: 0x1B5E20), Color(rgb: 0x388E3C)],
                accentColor: Color(rgb: 0xA5D6A7)
            )
        case "gedeComix":
            // GedeComix：黑曜石现代极简灰
            return ComicVisualSpec(
                iconName: "square.grid.2x2.fill",
                gradientColors: [Color(rgb: 0x212121), Color(rgb: 0x424242)],
                accentColor: Color(rgb: 0xEEEEEE)
            )
        case "ikmmh":
            // 爱看漫：深海孔雀绿
            return ComicVisualSpec(
                iconName: "book.closed.fill",
                gradientColors: [Color(rgb: 0x004D40), Color(rgb: 0x00796B)],
                accentColor: Color(rgb: 0x80CBC4)
            )
        case "ehentai":
            // E-Hentai：复古羊皮铜金
            return ComicVisualSpec(
                iconName: "archivebox.fill",
                gradientColors: [Color(rgb: 0x4E342E), Color(rgb: 0x6D4C41)],
                accentColor: Color(rgb: 0xBCAAA4)
            )
        default:
            return ComicVisualSpec(
                iconName: "book.fill",
                gradientColors: [Color(rgb: 0x263238), Color(rgb: 0x37474F)],
                accentColor: Color(rgb: 0xB0BEC5)
            )
        }
    }
}

/// Apple TV 风格的「漫画平台（Channels）」横向货架
public struct ComicPlatformsShelf: View {
    public let currentSource: ComicSourceDefinition
    public let onSelect: (ComicSourceDefinition) -> Void

    public init(
        currentSource: ComicSourceDefinition,
        onSelect: @escaping (ComicSourceDefinition) -> Void
    ) {
        self.currentSource = currentSource
        self.onSelect = onSelect
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
            HStack(spacing: AuroraMetrics.spacingS) {
                Text("漫画平台")
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundStyle(AuroraColor.textPrimary)

                Spacer()

                Text("精选 \(ComicSourceCatalog.allSources.count) 平台")
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundStyle(AuroraColor.brand)
            }
            .padding(.horizontal, AuroraMetrics.contentInset)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: AuroraMetrics.spacingM) {
                    ForEach(ComicSourceCatalog.allSources) { source in
                        let isSelected = currentSource.id == source.id
                        let isSupported = ComicSourceCatalog.isSupported(source)
                        let spec = ComicVisualSpec.forSource(source.id)

                        Button {
                            if isSupported {
                                onSelect(source)
                            }
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

                                    // 3. 核心官方 SF Symbol 原生液态玻璃徽章
                                    ZStack {
                                        Circle()
                                            .fill(.white.opacity(isSelected ? 0.18 : 0.08))
                                            .overlay(Circle().stroke(.white.opacity(isSelected ? 0.35 : 0.16), lineWidth: 0.8))
                                        Image(systemName: spec.iconName)
                                            .font(.system(size: 19, weight: .semibold))
                                            .symbolRenderingMode(.hierarchical)
                                            .foregroundStyle(isSelected ? .white : spec.accentColor)
                                    }
                                    .frame(width: 36, height: 36)
                                    .shadow(color: .black.opacity(isSelected ? 0.28 : 0.12), radius: 3, y: 1)

                                    // 4. 外轮廓高亮微光描边
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .stroke(
                                            isSelected
                                                ? LinearGradient(colors: [Color.white.opacity(0.85), spec.accentColor], startPoint: .topLeading, endPoint: .bottomTrailing)
                                                : LinearGradient(colors: [Color.white.opacity(0.12), Color.white.opacity(0.04)], startPoint: .top, endPoint: .bottom),
                                            lineWidth: isSelected ? 2 : 1
                                        )

                                    if !isSupported {
                                        Text("即将推出")
                                            .font(.system(size: 9, weight: .bold))
                                            .foregroundStyle(.white.opacity(0.8))
                                            .padding(.horizontal, 4)
                                            .padding(.vertical, 2)
                                            .background(Color.black.opacity(0.6), in: Capsule())
                                            .offset(y: 18)
                                    }
                                }
                                .frame(width: 88, height: 56)
                                .shadow(
                                    color: isSelected ? spec.accentColor.opacity(0.45) : .clear,
                                    radius: 8,
                                    x: 0,
                                    y: 3
                                )

                                Text(source.name)
                                    .font(isSelected ? .caption.weight(.bold) : .caption.weight(.medium))
                                    .foregroundStyle(isSelected ? AuroraColor.textPrimary : AuroraColor.textSecondary)
                                    .lineLimit(1)
                                    .frame(width: 88)
                            }
                        }
                        .buttonStyle(AppleSpringCardButtonStyle())
                        .disabled(!isSupported)
                        .accessibilityLabel("漫画平台 \(source.name)，\(isSelected ? "当前正在浏览" : (isSupported ? "轻点切换" : "即将支持"))")
                    }
                }
                .padding(.horizontal, AuroraMetrics.contentInset)
            }
        }
    }
}

/// 原生 Apple Books / 流媒体风格的精选漫画卡片
/// 尺寸与全 App 影视分类货架严格统一为标准 2:3 海报（140 × 210 pt），具备作品标题与更新信息，拔除粗糙的 1234 排行大字
public struct ComicFeaturedCard: View {
    public let comic: ComicItem
    public let onSelect: () -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private var cardWidth: CGFloat {
        dynamicTypeSize.isAccessibilitySize ? 180 : AuroraMetrics.posterCardWidth
    }

    private var cardHeight: CGFloat {
        cardWidth * 1.5
    }

    public init(comic: ComicItem, onSelect: @escaping () -> Void) {
        self.comic = comic
        self.onSelect = onSelect
    }

    public var body: some View {
        Button(action: onSelect) {
            VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                // 标准 2:3 纵向海报封面（140 × 210 pt，与主页完全一致）
                ZStack(alignment: .topTrailing) {
                    AuroraCoverImage(
                        url: comic.coverUrl,
                        title: comic.title,
                        sourceBaseURL: comic.coverBaseURL,
                        aspectRatio: AuroraMetrics.posterAspect,
                        contentMode: .fill,
                        cornerRadius: AuroraMetrics.cardRadius
                    )
                    .frame(width: cardWidth, height: cardHeight)
                    .overlay(
                        RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                            .stroke(AuroraColor.hairline, lineWidth: 1)
                    )

                    if !comic.latestChapterTitle.isEmpty {
                        Text(comic.latestChapterTitle)
                            .font(.caption2)
                            .fontWeight(.bold)
                            .foregroundStyle(AuroraColor.textOnBrand)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 3)
                            .background(AuroraColor.brand, in: Capsule())
                            .padding(AuroraMetrics.spacingS)
                    }
                }

                // 作品书名（固定锁定两行空间，消除上下高低错位）
                Text(comic.title)
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundStyle(AuroraColor.textPrimary)
                    .lineLimit(2, reservesSpace: true)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)

                // 作者与更新状态（统一高度）
                HStack(spacing: 4) {
                    Text(comic.author.isEmpty ? comic.sourceName : comic.author)
                        .font(.caption2)
                        .foregroundStyle(AuroraColor.textSecondary)
                        .lineLimit(1, reservesSpace: true)

                    Spacer()

                    Text(comic.sourceName)
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundStyle(AuroraColor.brandLabel)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(AuroraColor.brand.opacity(0.12), in: Capsule())
                        .lineLimit(1)
                }
                .frame(height: 18)
            }
            .frame(width: cardWidth)
            .contentShape(Rectangle())
        }
        .buttonStyle(AppleSpringCardButtonStyle())
        .accessibilityLabel("漫画 \(comic.title)，\(comic.author)")
    }
}

/// 原生 Comic Hero 聚焦横幅：1:1 对齐 Apple TV 聚光灯大卡
public struct ComicHeroBanner: View {
    public let comic: ComicItem
    public let sourceName: String
    public let onRead: () -> Void
    public let onCycle: () -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    public init(
        comic: ComicItem,
        sourceName: String,
        onRead: @escaping () -> Void,
        onCycle: @escaping () -> Void
    ) {
        self.comic = comic
        self.sourceName = sourceName
        self.onRead = onRead
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
            AuroraCoverImage(
                url: comic.coverUrl,
                title: comic.title,
                sourceBaseURL: comic.coverBaseURL,
                aspectRatio: isWideAspect ? 16 / 9 : 4 / 5,
                contentMode: .fill,
                cornerRadius: 0
            )
            .frame(maxWidth: .infinity, maxHeight: heroHeight)
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
                Text(comic.title)
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
                    readButton
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
            Text("精品漫画")
                .font(.caption2.bold())
                .foregroundStyle(.white)
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(.purple.opacity(0.4), in: Capsule())

            if !comic.author.isEmpty {
                Text(comic.author)
                    .font(.caption.bold())
                    .foregroundStyle(.white)
                Text("•")
                    .foregroundStyle(.white.opacity(0.6))
            }

            Text(sourceName)
                .font(.caption.bold())
                .foregroundStyle(AuroraColor.brandLabel)

            if !comic.latestChapterTitle.isEmpty {
                Text("•")
                    .foregroundStyle(.white.opacity(0.6))
                Text(comic.latestChapterTitle)
                    .font(.caption2)
                    .foregroundStyle(.white.opacity(0.85))
                    .lineLimit(1)
            }
        }
        .lineLimit(1)
        .fixedSize(horizontal: false, vertical: true)
    }

    private var readButton: some View {
        Button(action: onRead) {
            HStack(spacing: 6) {
                Image(systemName: "book.fill")
                    .font(.headline)
                Text("开始阅读")
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
                Text("换一部")
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
        .accessibilityHint("切换另一部推荐漫画")
    }
}

public struct ComicsScreen: View {
    @ObservedObject public var repo = ComicRepository.shared
    public let onOpenSearch: () -> Void
    @State private var activeComicForReading: ComicItem? = nil
    @State private var detailTask: Task<Void, Never>?
    @State private var detailLoadingState = ComicDetailLoadingState()
    @State private var detailError: String?
    @State private var showPicacgLogin = false
    @State private var picacgAccount = ""
    @State private var picacgPassword = ""
    @State private var isPicacgLoginInProgress = false
    @State private var picacgLoginError: String?
    @State private var spotlightIndex: Int = 0
    @State private var selectedComicForRetry: ComicItem?
    @State private var isScrolled: Bool = false

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    
    public init(onOpenSearch: @escaping () -> Void = {}) {
        self.onOpenSearch = onOpenSearch
    }

    private var spotlightComic: ComicItem? {
        guard !repo.comics.isEmpty else { return nil }
        let validIndex = spotlightIndex % repo.comics.count
        return repo.comics[validIndex]
    }

    private var top10Comics: [ComicItem] {
        Array(repo.comics.prefix(10))
    }
    
    public var body: some View {
        ZStack {
            AuroraColor.canvas.ignoresSafeArea()
            
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: AuroraMetrics.spacingXL) {
                    // 1. 顶部 Comic Hero 聚焦大卡（Apple TV 影院级画幅）
                    if let spotlight = spotlightComic {
                        ComicHeroBanner(
                            comic: spotlight,
                            sourceName: repo.currentSource.name,
                            onRead: { openComic(spotlight) },
                            onCycle: {
                                withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                                    spotlightIndex = (spotlightIndex + 1) % max(1, repo.comics.count)
                                }
                            }
                        )
                    } else if repo.isLoading {
                        HeroLoadingPlaceholder()
                    } else {
                        Spacer().frame(height: 60)
                    }

                    // 2. Apple TV 核心层级：精选漫画平台（Channels 货架）
                    ComicPlatformsShelf(
                        currentSource: repo.currentSource,
                        onSelect: { source in
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                                repo.switchSource(to: source)
                            }
                        }
                    )

                    // 3. 人气精选漫画（标准 2:3 海报画幅，与主页完全统一，拔除粗糙的 1234 排行大字）
                    if !top10Comics.isEmpty {
                        VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                            HStack(spacing: AuroraMetrics.spacingS) {
                                Text("人气精选")
                                    .font(.title2)
                                    .fontWeight(.bold)
                                    .foregroundStyle(AuroraColor.textPrimary)

                                Text("TRENDING")
                                    .font(.caption2)
                                    .fontWeight(.black)
                                    .foregroundStyle(AuroraColor.brand)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(AuroraColor.brand.opacity(0.12), in: Capsule())
                            }
                            .padding(.horizontal, AuroraMetrics.contentInset)

                            ScrollView(.horizontal, showsIndicators: false) {
                                LazyHStack(alignment: .top, spacing: AuroraMetrics.spacingM) {
                                    ForEach(top10Comics) { comic in
                                        ComicFeaturedCard(comic: comic) {
                                            openComic(comic)
                                        }
                                    }
                                }
                                .padding(.horizontal, AuroraMetrics.contentInset)
                            }
                        }
                    }

                    // 4. 平台推荐漫画流
                    comicsContent

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
            .refreshable {
                repo.loadComics(for: repo.currentSource)
            }
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground((isScrolled || spotlightComic == nil) ? .visible : .hidden, for: .navigationBar)
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
                    .accessibilityLabel("搜索漫画")
                }
            }
        }
        .navigationDestination(item: $activeComicForReading) { comic in
            ComicReaderView(comic: comic) {
                activeComicForReading = nil
            }
            .toolbar(.hidden, for: .tabBar)
            .navigationBarBackButtonHidden(true)
        }
        .alert("无法打开漫画", isPresented: Binding(
            get: { detailError != nil },
            set: { if !$0 { detailError = nil } }
        )) {
            Button("取消", role: .cancel) { detailError = nil }
            Button("重试") {
                if let comic = selectedComicForRetry {
                    openComic(comic)
                }
            }
        } message: {
            Text(detailError ?? "请稍后重试。")
        }
        .overlay {
            if detailLoadingState.isLoading {
                ProgressView("正在加载章节…")
                    .padding(AuroraMetrics.spacingXL)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius))
            }
        }
        .sheet(isPresented: $showPicacgLogin) {
            picacgLoginSheet
        }
        .onAppear {
            let args = CommandLine.arguments
            if let idx = args.firstIndex(of: "--comic-source"), idx + 1 < args.count {
                let srcId = args[idx + 1]
                if let source = ComicSourceCatalog.supportedSources.first(where: { $0.id == srcId }) {
                    repo.switchSource(to: source)
                }
            }
            if args.contains("--open-first-comic") {
                Task {
                    for _ in 0..<20 {
                        try? await Task.sleep(nanoseconds: 300_000_000)
                        if let first = repo.comics.first {
                            await MainActor.run {
                                openComic(first)
                            }
                            break
                        }
                    }
                }
            }
        }
        .onDisappear {
            cancelComicDetailsLoad()
        }
    }

    @ViewBuilder
    private var comicsContent: some View {
        if repo.currentSource.id == ComicSourceCatalog.picacg.id && !ComicProtocolSecrets.hasPicacgConfiguration {
            ContentUnavailableView {
                Label("未配置哔咔协议", systemImage: "key.slash")
            } description: {
                Text("此构建未配置哔咔协议参数（API Key 与签名密钥）。请在 SourceSecrets.plist 中配置后使用。")
            }
            .frame(maxWidth: .infinity, minHeight: 300)
        } else if repo.currentSource.id == ComicSourceCatalog.picacg.id && !repo.isPicacgAuthenticated {
            ContentUnavailableView {
                Label("需要哔咔账号", systemImage: "person.badge.key")
            } description: {
                Text(repo.errorMessage ?? "登录后使用哔咔官方接口浏览漫画。登录令牌仅保存在本机钥匙串中。")
            } actions: {
                Button("登录哔咔", systemImage: "person.crop.circle.badge.checkmark") {
                    showPicacgLogin = true
                }
                .buttonStyle(.borderedProminent)
                .tint(AuroraColor.brand)
            }
            .frame(maxWidth: .infinity, minHeight: 300)
        } else if repo.isLoading && repo.comics.isEmpty {
            ProgressView("正在加载漫画…")
                .frame(maxWidth: .infinity, minHeight: 240)
        } else if repo.comics.isEmpty, let errorMessage = repo.errorMessage {
            ContentUnavailableView {
                Label("漫画暂不可用", systemImage: "wifi.exclamationmark")
            } description: {
                Text(errorMessage)
            } actions: {
                Button("重试", systemImage: "arrow.clockwise") {
                    repo.loadComics(for: repo.currentSource)
                }
                .buttonStyle(.borderedProminent)
            }
            .tint(AuroraColor.brand)
            .frame(maxWidth: .infinity, minHeight: 300)
        } else if repo.comics.isEmpty {
            ContentUnavailableView(
                "暂无漫画",
                systemImage: "books.vertical",
                description: Text("当前来源暂时没有可显示的内容。")
            )
            .frame(maxWidth: .infinity, minHeight: 300)
        } else {
            VStack(spacing: AuroraMetrics.spacingM) {
                HStack(spacing: AuroraMetrics.spacingS) {
                    Text("\(repo.currentSource.name) 热门连载")
                        .font(.title3)
                        .fontWeight(.bold)
                        .foregroundStyle(AuroraColor.textPrimary)

                    Spacer()

                    Text("\(repo.comics.count) 部作品")
                        .font(.subheadline)
                        .foregroundStyle(AuroraColor.textSecondary)
                }
                .padding(.horizontal, AuroraMetrics.contentInset)

                if repo.isLoading {
                    ProgressView("正在刷新…")
                        .font(.caption)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, AuroraMetrics.contentInset)
                }

                if let errorMessage = repo.errorMessage {
                    Label {
                        Text(errorMessage)
                            .font(.caption)
                    } icon: {
                        Image(systemName: "exclamationmark.triangle")
                    }
                    .foregroundStyle(AuroraColor.warning)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .overlay(alignment: .trailing) {
                        Button("重试") { repo.loadComics(for: repo.currentSource) }
                            .font(.caption.weight(.semibold))
                    }
                    .padding(.horizontal, AuroraMetrics.contentInset)
                }

                LazyVGrid(columns: comicGridColumns, spacing: AuroraMetrics.spacingL) {
                    ForEach(repo.comics) { comic in
                        Button {
                            openComic(comic)
                        } label: {
                            VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                                AuroraCoverImage(
                                    url: comic.coverUrl,
                                    title: comic.title,
                                    sourceBaseURL: comic.coverBaseURL,
                                    aspectRatio: AuroraMetrics.posterAspect,
                                    contentMode: .fill,
                                    cornerRadius: AuroraMetrics.cardRadius,
                                    placeholderStyle: .comic
                                )
                                .overlay {
                                    RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                                        .stroke(AuroraColor.hairline, lineWidth: 1)
                                }

                                Text(comic.title)
                                    .font(.subheadline)
                                    .fontWeight(.semibold)
                                    .foregroundStyle(AuroraColor.textPrimary)
                                    .lineLimit(2, reservesSpace: true)
                                    .multilineTextAlignment(.leading)
                                    .frame(maxWidth: .infinity, alignment: .leading)

                                VStack(alignment: .leading, spacing: AuroraMetrics.spacingXS) {
                                    Text(comic.author.isEmpty ? comic.sourceName : comic.author)
                                        .font(.caption)
                                        .foregroundStyle(AuroraColor.textSecondary)
                                        .lineLimit(1, reservesSpace: true)

                                    Text(comic.latestChapterTitle.isEmpty ? "最新章节" : comic.latestChapterTitle)
                                        .font(.caption2)
                                        .fontWeight(.medium)
                                        .foregroundStyle(comic.latestChapterTitle.isEmpty ? AuroraColor.textTertiary : AuroraColor.brandLabel)
                                        .lineLimit(1, reservesSpace: true)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .topLeading)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(AppleSpringCardButtonStyle())
                        .onAppear {
                            if comic.id == repo.comics.last?.id {
                                Task {
                                    await repo.loadNextPage()
                                }
                            }
                        }
                        .accessibilityElement(children: .combine)
                        .accessibilityLabel("\(comic.title), \(comic.author.isEmpty ? "" : "作者 " + comic.author + ", ")\(comic.latestChapterTitle.isEmpty ? "" : "最新 " + comic.latestChapterTitle)")
                        .accessibilityHint("打开漫画阅读器")
                    }
                }
                .padding(.horizontal, AuroraMetrics.contentInset)
                .frame(maxWidth: 1000)
                .frame(maxWidth: .infinity)

                if repo.isLoadingNextPage {
                    HStack(spacing: AuroraMetrics.spacingM) {
                        ProgressView()
                            .controlSize(.regular)
                            .tint(AuroraColor.brand)
                        Text("comics.loading_more".localized)
                            .font(.footnote)
                            .foregroundStyle(AuroraColor.textSecondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, AuroraMetrics.spacingM)
                } else if !repo.canLoadMore && !repo.comics.isEmpty {
                    Text("comics.all_loaded".localized)
                        .font(.caption)
                        .foregroundStyle(AuroraColor.textTertiary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, AuroraMetrics.spacingM)
                }
            }
        }
    }

    private var picacgLoginSheet: some View {
        NavigationStack {
            Form {
                Section("哔咔账号") {
                    TextField("邮箱或账号", text: $picacgAccount)
                        .textContentType(.username)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()

                    SecureField("密码", text: $picacgPassword)
                        .textContentType(.password)
                }

                if let picacgLoginError {
                    Section {
                        Text(picacgLoginError)
                            .foregroundStyle(.red)
                    }
                }

                Section {
                    Button {
                        Task { await loginPicacg() }
                    } label: {
                        HStack {
                            Spacer()
                            if isPicacgLoginInProgress { ProgressView() }
                            Text(isPicacgLoginInProgress ? "正在登录…" : "登录")
                            Spacer()
                        }
                    }
                    .disabled(isPicacgLoginInProgress || picacgAccount.isEmpty || picacgPassword.isEmpty)
                } footer: {
                    Text("账号密码只用于此次认证请求；认证成功后仅保存服务令牌。")
                }
            }
            .navigationTitle("哔咔登录")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { showPicacgLogin = false }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    @MainActor
    private func loginPicacg() async {
        isPicacgLoginInProgress = true
        picacgLoginError = nil
        defer { isPicacgLoginInProgress = false }
        do {
            try await repo.loginPicacg(account: picacgAccount, password: picacgPassword)
            picacgPassword = ""
            showPicacgLogin = false
            repo.loadComics(for: repo.currentSource)
        } catch {
            picacgPassword = ""
            picacgLoginError = error.localizedDescription
        }
    }

    private var comicGridColumns: [GridItem] {
        let minimumWidth: CGFloat = dynamicTypeSize.isAccessibilitySize ? 300 : 152
        return [GridItem(.adaptive(minimum: minimumWidth, maximum: 340), spacing: AuroraMetrics.spacingM)]
    }

    private func openComic(_ comic: ComicItem) {
        cancelComicDetailsLoad()
        if !comic.chapters.isEmpty {
            selectedComicForRetry = nil
            detailError = nil
            activeComicForReading = comic
            return
        }
        selectedComicForRetry = comic
        detailError = nil
        let requestID = detailLoadingState.beginRequest()
        detailTask = Task {
            do {
                let detailedComic = try await repo.fetchComicDetails(comic)
                guard !Task.isCancelled, detailLoadingState.finishRequest(requestID) else { return }
                detailTask = nil
                if detailedComic.chapters.isEmpty {
                    detailError = "此漫画来源没有提供章节列表。"
                } else {
                    activeComicForReading = detailedComic
                }
            } catch {
                guard !Task.isCancelled, detailLoadingState.finishRequest(requestID) else { return }
                detailTask = nil
                detailError = error.localizedDescription
            }
        }
    }

    private func cancelComicDetailsLoad() {
        detailTask?.cancel()
        detailTask = nil
        detailLoadingState.cancelRequest()
    }
}
