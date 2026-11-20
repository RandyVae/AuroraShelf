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
            return ComicVisualSpec(
                iconName: "doc.richtext.fill",
                gradientColors: [Color(rgb: 0xE65100), Color(rgb: 0xF57C00)],
                accentColor: Color(rgb: 0xFFB74D)
            )
        case "picacg":
            return ComicVisualSpec(
                iconName: "heart.text.square.fill",
                gradientColors: [Color(rgb: 0x880E4F), Color(rgb: 0xD81B60)],
                accentColor: Color(rgb: 0xF48FB1)
            )
        case "jm":
            return ComicVisualSpec(
                iconName: "flame.fill",
                gradientColors: [Color(rgb: 0xB71C1C), Color(rgb: 0xE53935)],
                accentColor: Color(rgb: 0xFF8A80)
            )
        case "komiic":
            return ComicVisualSpec(
                iconName: "bookmark.fill",
                gradientColors: [Color(rgb: 0x01579B), Color(rgb: 0x0288D1)],
                accentColor: Color(rgb: 0x81D4FA)
            )
        case "copy", "copycomic":
            return ComicVisualSpec(
                iconName: "books.vertical.fill",
                gradientColors: [Color(rgb: 0x311B92), Color(rgb: 0x5E35B1)],
                accentColor: Color(rgb: 0xB39DDB)
            )
        case "dongman91", "91dongman":
            return ComicVisualSpec(
                iconName: "sparkles.tv.fill",
                gradientColors: [Color(rgb: 0x4A148C), Color(rgb: 0x8E24AA)],
                accentColor: Color(rgb: 0xCE93D8)
            )
        case "8muses", "eightMuses":
            return ComicVisualSpec(
                iconName: "photo.on.rectangle.angled",
                gradientColors: [Color(rgb: 0x1B5E20), Color(rgb: 0x388E3C)],
                accentColor: Color(rgb: 0xA5D6A7)
            )
        case "gedecomix", "gedeComix":
            return ComicVisualSpec(
                iconName: "square.grid.2x2.fill",
                gradientColors: [Color(rgb: 0x212121), Color(rgb: 0x424242)],
                accentColor: Color(rgb: 0xEEEEEE)
            )
        case "ikmmh":
            return ComicVisualSpec(
                iconName: "book.closed.fill",
                gradientColors: [Color(rgb: 0x004D40), Color(rgb: 0x00796B)],
                accentColor: Color(rgb: 0x80CBC4)
            )
        case "ehentai":
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
    @ObservedObject private var locManager = LocalizationManager.shared

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
                Text("comics.platforms_title".localized)
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundStyle(AuroraColor.textPrimary)
                    .accessibilityAddTraits(.isHeader)

                Spacer()

                Text("comics.platforms_count".localized(ComicSourceCatalog.allSources.count))
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundStyle(AuroraColor.brandLabel)
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

                                    ZStack {
                                        Circle()
                                            .fill(.white.opacity(isSelected ? 0.18 : 0.08))
                                            .overlay(Circle().stroke(.white.opacity(isSelected ? 0.35 : 0.16), lineWidth: 0.8))
                                        Image(systemName: spec.iconName)
                                            .font(.body.weight(.semibold))
                                            .symbolRenderingMode(.hierarchical)
                                            .foregroundStyle(isSelected ? .white : spec.accentColor)
                                    }
                                    .frame(width: 36, height: 36)
                                    .shadow(color: .black.opacity(isSelected ? 0.28 : 0.12), radius: 3, y: 1)

                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .stroke(
                                            isSelected
                                                ? LinearGradient(colors: [Color.white.opacity(0.85), spec.accentColor], startPoint: .topLeading, endPoint: .bottomTrailing)
                                                : LinearGradient(colors: [Color.white.opacity(0.12), Color.white.opacity(0.04)], startPoint: .top, endPoint: .bottom),
                                            lineWidth: isSelected ? 2 : 1
                                        )

                                    if !isSupported {
                                        Text("comics.coming_soon".localized)
                                            .font(.caption2.weight(.bold))
                                            .foregroundStyle(.white.opacity(0.88))
                                            .padding(.horizontal, 4)
                                            .padding(.vertical, 2)
                                            .background(Color.black.opacity(0.62), in: Capsule())
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
                        .accessibilityLabel(
                            "comics.platform_a11y".localized(
                                source.name,
                                isSelected
                                    ? "comics.status_browsing".localized
                                    : (isSupported ? "channels.status_tap".localized : "comics.coming_soon".localized)
                            )
                        )
                    }
                }
                .padding(.horizontal, AuroraMetrics.contentInset)
            }
        }
    }
}

/// 原生 Apple Books / 流媒体风格的精选漫画卡片
public struct ComicFeaturedCard: View {
    public let comic: ComicItem
    public let onSelect: () -> Void

    @Environment(\.auroraContainerSize) private var containerSize
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private var cardWidth: CGFloat {
        let form = DeviceLayout.resolveCurrent(containerSize: containerSize, horizontalSizeClass: horizontalSizeClass)
        let base = DeviceLayout.posterCardWidth(for: form)
        return dynamicTypeSize.isAccessibilitySize ? max(base, 180) : base
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
                            .foregroundStyle(.white)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 3)
                            .background(AuroraColor.brandSolid, in: Capsule())
                            .padding(AuroraMetrics.spacingS)
                    }
                }

                Text(comic.title)
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundStyle(AuroraColor.textPrimary)
                    .lineLimit(2, reservesSpace: true)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)

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
                .frame(minHeight: 18)
            }
            .frame(width: cardWidth)
            .contentShape(Rectangle())
        }
        .auroraZoomSource("comic:\(comic.id)")
        .buttonStyle(AppleSpringCardButtonStyle())
        .accessibilityLabel("comics.card_a11y".localized(comic.title, comic.author))
    }
}

/// 原生 Comic Hero 聚焦横幅：1:1 对齐 Apple TV 聚光灯大卡
public struct ComicHeroBanner: View {
    public let comic: ComicItem
    public let sourceName: String
    public let onRead: () -> Void
    public let onCycle: () -> Void

    @ObservedObject private var locManager = LocalizationManager.shared
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.auroraContainerSize) private var containerSize

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
        DeviceLayout.resolveCurrent(containerSize: containerSize, horizontalSizeClass: horizontalSizeClass)
    }

    private var heroHeight: CGFloat {
        let effectiveHeight = containerSize.height > 0 ? containerSize.height : DeviceLayout.currentWindowSize.height
        return DeviceLayout.heroHeight(for: formFactor, screenHeight: effectiveHeight)
    }

    public var body: some View {
        ZStack(alignment: .bottom) {
            Color.clear
                .overlay(
                    AuroraCoverImage(
                        url: comic.coverUrl,
                        title: comic.title,
                        sourceBaseURL: comic.coverBaseURL,
                        aspectRatio: nil,
                        contentMode: .fill,
                        cornerRadius: 0
                    )
                )
                .frame(maxWidth: .infinity)
                .frame(height: heroHeight)
                .clipped()

            VStack(spacing: 0) {
                LinearGradient(
                    colors: [
                        (colorScheme == .light ? Color.white.opacity(0.28) : Color.black.opacity(0.28)),
                        Color.clear
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: colorScheme == .light ? 72 : 88)

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
                    .padding(.horizontal, AuroraMetrics.contentInset)

                metadataView

                GlassEffectContainer(spacing: AuroraMetrics.spacingM) {
                    HStack(spacing: AuroraMetrics.spacingM) {
                        readButton
                            .frame(maxWidth: formFactor.isWide ? 220 : .infinity)
                        cycleButton
                    }
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
            Text("comics.featured_badge".localized)
                .font(.caption2.bold())
                .foregroundStyle(.white)
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(Color.purple.opacity(0.85), in: Capsule())

            if !comic.author.isEmpty {
                Text(comic.author)
                    .font(.caption.bold())
                    .foregroundStyle(AuroraColor.textPrimary)
                Text("•")
                    .foregroundStyle(AuroraColor.textTertiary)
            }

            Text(sourceName)
                .font(.caption.bold())
                .foregroundStyle(AuroraColor.brandLabel)

            if !comic.latestChapterTitle.isEmpty {
                Text("•")
                    .foregroundStyle(AuroraColor.textTertiary)
                Text(comic.latestChapterTitle)
                    .font(.caption2)
                    .foregroundStyle(AuroraColor.textSecondary)
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
                Text("comics.start_reading".localized)
                    .font(.headline.weight(.bold))
            }
            .frame(maxWidth: .infinity, minHeight: 46)
            .background(AuroraColor.textPrimary, in: Capsule())
            .foregroundStyle(AuroraColor.canvas)
        }
        .auroraZoomSource("comic:\(comic.id)")
        .buttonStyle(AppleSpringCardButtonStyle())
    }

    private var cycleButton: some View {
        Button(action: onCycle) {
            HStack(spacing: 6) {
                Image(systemName: "shuffle")
                    .font(.headline)
                Text("comics.shuffle".localized)
                    .font(.headline.weight(.semibold))
                    .lineLimit(1)
            }
            .frame(minHeight: 34)
            .padding(.horizontal, 6)
            .fixedSize(horizontal: true, vertical: false)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.capsule)
        .tint(AuroraColor.textPrimary)
        .hoverEffect(.highlight)
        .accessibilityHint("comics.shuffle_hint".localized)
    }
}

public struct ComicsScreen: View {
    @ObservedObject public var repo = ComicRepository.shared
    @ObservedObject private var comicDownloadManager = ComicDownloadManager.shared
    @ObservedObject private var locManager = LocalizationManager.shared
    public let onOpenSearch: () -> Void
    public let onPlayVideo: (VideoItem) -> Void
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
    @State private var showDownloadCenter = false

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.auroraContainerSize) private var containerSize

    public init(
        onOpenSearch: @escaping () -> Void = {},
        onPlayVideo: @escaping (VideoItem) -> Void = { _ in }
    ) {
        self.onOpenSearch = onOpenSearch
        self.onPlayVideo = onPlayVideo
    }

    private var spotlightComic: ComicItem? {
        guard !repo.comics.isEmpty else { return nil }
        let validIndex = spotlightIndex % repo.comics.count
        return repo.comics[validIndex]
    }

    private var top10Comics: [ComicItem] {
        Array(repo.comics.prefix(10))
    }

    private var hasPicacgDraft: Bool {
        !picacgAccount.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !picacgPassword.isEmpty
    }

    public var body: some View {
        ZStack {
            AuroraColor.canvas.ignoresSafeArea()

            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: AuroraMetrics.spacingXL) {
                    if let spotlight = spotlightComic {
                        ComicHeroBanner(
                            comic: spotlight,
                            sourceName: repo.currentSource.name,
                            onRead: { openComic(spotlight) },
                            onCycle: {
                                AuroraMotion.perform(AuroraMotion.smooth, reduceMotion: reduceMotion) {
                                    spotlightIndex = (spotlightIndex + 1) % max(1, repo.comics.count)
                                }
                            }
                        )
                    } else if repo.isLoading {
                        HeroLoadingPlaceholder()
                    } else {
                        Spacer().frame(height: 60)
                    }

                    ComicPlatformsShelf(
                        currentSource: repo.currentSource,
                        onSelect: { source in
                            cancelComicDetailsLoad()
                            AuroraMotion.perform(AuroraMotion.smooth, reduceMotion: reduceMotion) {
                                repo.switchSource(to: source)
                            }
                        }
                    )

                    if repo.currentSource.categories.count > 1 {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: AuroraMetrics.spacingS) {
                                ForEach(repo.currentSource.categories, id: \.self) { cat in
                                    let isSelected = (repo.selectedCategory == cat)
                                    Button {
                                        guard repo.selectedCategory != cat else { return }
                                        AuroraMotion.perform(AuroraMotion.quick, reduceMotion: reduceMotion) {
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
                                    .accessibilityLabel("comics.category_a11y".localized(cat.localizedCategoryLabel))
                                    .accessibilityAddTraits(isSelected ? .isSelected : [])
                                }
                            }
                            .padding(.horizontal, AuroraMetrics.contentInset)
                        }
                        .sensoryFeedback(.selection, trigger: repo.selectedCategory)
                    }

                    if !top10Comics.isEmpty {
                        VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                            HStack(spacing: AuroraMetrics.spacingS) {
                                Text("comics.popular".localized)
                                    .font(.title2)
                                    .fontWeight(.bold)
                                    .foregroundStyle(AuroraColor.textPrimary)
                                    .accessibilityAddTraits(.isHeader)

                                Text("TRENDING")
                                    .font(.caption2)
                                    .fontWeight(.black)
                                    .foregroundStyle(AuroraColor.brandLabel)
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

                    comicsContent

                    Spacer().frame(height: max(AuroraMetrics.spacingXXL, 96))
                }
            }
            .auroraImmersiveHeroScrollEdge(
                isScrolled: $isScrolled,
                hasHeroContent: spotlightComic != nil || repo.isLoading,
                reduceMotion: reduceMotion
            )
            .ignoresSafeArea(edges: .top)
            .refreshable {
                repo.loadComics(for: repo.currentSource)
                try? await Task.sleep(nanoseconds: 500_000_000)
            }
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .auroraImmersiveHeroNavigationBar(
            isScrolled: isScrolled,
            hasHeroContent: spotlightComic != nil || repo.isLoading
        )
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                NetgreyMarkView(width: 20, height: 28)
            }
            .sharedBackgroundVisibility(.hidden)

            ToolbarItem(placement: .topBarTrailing) {
                HStack(spacing: AuroraMetrics.spacingM) {
                    Button {
                        showDownloadCenter = true
                    } label: {
                        ZStack(alignment: .topTrailing) {
                            Image(systemName: comicDownloadManager.activeChapters.isEmpty ? "arrow.down.circle" : "arrow.down.circle.fill")
                                .font(.body)
                                .fontWeight(.semibold)
                                .foregroundStyle(comicDownloadManager.activeChapters.isEmpty ? Color.primary : AuroraColor.brandLabel)

                            if !comicDownloadManager.activeChapters.isEmpty {
                                Text("\(comicDownloadManager.activeChapters.count)")
                                    .font(.caption2.weight(.bold))
                                    .foregroundStyle(.white)
                                    .padding(.horizontal, 4)
                                    .padding(.vertical, 1)
                                    .background(AuroraColor.brandSolid, in: Capsule())
                                    .offset(x: 6, y: -5)
                            }
                        }
                    }
                    .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                    .contentShape(Rectangle())
                    .hoverEffect(.highlight)
                    .accessibilityLabel("comics.downloads_a11y".localized)

                    Button(action: onOpenSearch) {
                        Image(systemName: "magnifyingglass")
                            .font(.body)
                            .fontWeight(.semibold)
                            .foregroundStyle(.primary)
                    }
                    .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                    .contentShape(Rectangle())
                    .hoverEffect(.highlight)
                    .accessibilityLabel("comics.search_a11y".localized)
                }
            }
        }
        .alert("comics.open_error_title".localized, isPresented: Binding(
            get: { detailError != nil },
            set: { if !$0 { detailError = nil } }
        )) {
            Button("common.cancel".localized, role: .cancel) { detailError = nil }
            Button("common.retry".localized) {
                if let comic = selectedComicForRetry {
                    openComic(comic)
                }
            }
        } message: {
            Text(detailError ?? "hero.try_again_later".localized)
        }
        .overlay {
            if detailLoadingState.isLoading {
                ZStack {
                    Color.black.opacity(0.32)
                        .ignoresSafeArea()
                        .contentShape(Rectangle())
                        .onTapGesture { }

                    VStack(spacing: AuroraMetrics.spacingM) {
                        ProgressView("comics.loading_chapters".localized)
                        if let title = selectedComicForRetry?.title, !title.isEmpty {
                            Text(title)
                                .font(.caption)
                                .foregroundStyle(AuroraColor.textSecondary)
                                .lineLimit(2)
                                .multilineTextAlignment(.center)
                        }
                        Button("common.cancel".localized, role: .cancel) {
                            cancelComicDetailsLoad()
                        }
                        .buttonStyle(.bordered)
                    }
                    .frame(maxWidth: 260)
                    .padding(AuroraMetrics.spacingXL)
                    .glassEffect(.regular, in: .rect(cornerRadius: AuroraMetrics.cardRadius))
                }
            }
        }
        .onChange(of: repo.currentSource.id) { _, _ in
            cancelComicDetailsLoad()
            detailError = nil
            selectedComicForRetry = nil
        }
        .onChange(of: repo.selectedCategory) { _, _ in
            cancelComicDetailsLoad()
            detailError = nil
            selectedComicForRetry = nil
        }
        .sheet(isPresented: $showPicacgLogin) {
            picacgLoginSheet
        }
        .sheet(isPresented: $showDownloadCenter) {
            NavigationStack {
                OfflineDownloadsScreen(
                    initialTab: .comic,
                    showCloseButton: true,
                    onPlayVideo: { video in
                        showDownloadCenter = false
                        onPlayVideo(video)
                    }
                )
            }
        }
        .onAppear {
            repo.loadComicsIfNeeded()
            #if DEBUG
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
            #endif
        }
        .onDisappear {
            cancelComicDetailsLoad()
        }
    }

    @ViewBuilder
    private var comicsContent: some View {
        if repo.currentSource.id == ComicSourceCatalog.picacg.id && !ComicProtocolSecrets.hasPicacgConfiguration {
            ContentUnavailableView {
                Label("comics.picacg_unconfigured_title".localized, systemImage: "key.slash")
            } description: {
                Text("comics.picacg_unconfigured_desc".localized)
            }
            .frame(maxWidth: .infinity, minHeight: 300)
        } else if repo.currentSource.id == ComicSourceCatalog.picacg.id && !repo.isPicacgAuthenticated {
            ContentUnavailableView {
                Label("comics.picacg_account_needed".localized, systemImage: "person.badge.key")
            } description: {
                Text(repo.errorMessage ?? "comics.picacg_account_desc".localized)
            } actions: {
                Button("comics.picacg_login_btn".localized, systemImage: "person.crop.circle.badge.checkmark") {
                    showPicacgLogin = true
                }
                .buttonStyle(.borderedProminent)
                .tint(AuroraColor.brandSolid)
            }
            .frame(maxWidth: .infinity, minHeight: 300)
        } else if repo.isLoading && repo.comics.isEmpty {
            ProgressView("comics.loading".localized)
                .frame(maxWidth: .infinity, minHeight: 240)
        } else if repo.comics.isEmpty, let errorMessage = repo.errorMessage {
            ContentUnavailableView {
                Label("comics.unavailable".localized, systemImage: "wifi.exclamationmark")
            } description: {
                Text(errorMessage)
            } actions: {
                Button("common.retry".localized, systemImage: "arrow.clockwise") {
                    repo.loadComics(for: repo.currentSource)
                }
                .buttonStyle(.borderedProminent)
            }
            .tint(AuroraColor.brandSolid)
            .frame(maxWidth: .infinity, minHeight: 300)
        } else if repo.comics.isEmpty {
            ContentUnavailableView(
                "comics.empty_title".localized,
                systemImage: "books.vertical",
                description: Text("comics.empty_desc".localized)
            )
            .frame(maxWidth: .infinity, minHeight: 300)
        } else {
            VStack(spacing: AuroraMetrics.spacingM) {
                HStack(spacing: AuroraMetrics.spacingS) {
                    Text("comics.hot_serials".localized(repo.currentSource.name))
                        .font(.title3)
                        .fontWeight(.bold)
                        .foregroundStyle(AuroraColor.textPrimary)
                        .accessibilityAddTraits(.isHeader)

                    Spacer()

                    Text("comics.works_count".localized(repo.comics.count))
                        .font(.subheadline)
                        .foregroundStyle(AuroraColor.textSecondary)
                }
                .padding(.horizontal, AuroraMetrics.contentInset)

                if repo.isLoading {
                    ProgressView("comics.refreshing".localized)
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
                        Button("common.retry".localized) { repo.loadComics(for: repo.currentSource) }
                            .font(.caption.weight(.semibold))
                            .frame(minHeight: AuroraMetrics.minTouchTarget)
                    }
                    .padding(.horizontal, AuroraMetrics.contentInset)
                }

                LazyVGrid(columns: comicGridColumns, spacing: AuroraMetrics.spacingL) {
                    ForEach(repo.comics) { comic in
                        comicGridCard(comic)
                    }
                }
                .padding(.horizontal, AuroraMetrics.contentInset)
                .frame(maxWidth: 1000)
                .frame(maxWidth: .infinity)

                if repo.isLoadingNextPage {
                    HStack(spacing: AuroraMetrics.spacingM) {
                        ProgressView()
                            .controlSize(.regular)
                            .tint(AuroraColor.brandLabel)
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
                Section("comics.picacg_section".localized) {
                    TextField("comics.picacg_account_placeholder".localized, text: $picacgAccount)
                        .textContentType(.username)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()

                    SecureField("comics.picacg_password_placeholder".localized, text: $picacgPassword)
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
                            Text(isPicacgLoginInProgress ? "comics.logging_in".localized : "account.login".localized)
                            Spacer()
                        }
                    }
                    .disabled(isPicacgLoginInProgress || picacgAccount.isEmpty || picacgPassword.isEmpty)
                } footer: {
                    Text("comics.picacg_privacy_note".localized)
                }
            }
            .navigationTitle("comics.picacg_nav_title".localized)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.cancel".localized) {
                        picacgAccount = ""
                        picacgPassword = ""
                        showPicacgLogin = false
                    }
                    .frame(minWidth: AuroraMetrics.minTouchTarget, minHeight: AuroraMetrics.minTouchTarget)
                }
            }
            .modifier(PicacgDraftDismissalGuard(hasDraft: hasPicacgDraft) {
                picacgAccount = ""
                picacgPassword = ""
                showPicacgLogin = false
            })
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
        let form = DeviceLayout.resolveCurrent(containerSize: containerSize, horizontalSizeClass: horizontalSizeClass)
        let baseMin = max(152, DeviceLayout.gridMinimumWidth(for: form))
        let minimumWidth: CGFloat = dynamicTypeSize.isAccessibilitySize ? 300 : baseMin
        return [GridItem(.adaptive(minimum: minimumWidth, maximum: 340), spacing: AuroraMetrics.spacingM)]
    }

    private func openComic(_ comic: ComicItem) {
        cancelComicDetailsLoad()
        if !comic.chapters.isEmpty {
            selectedComicForRetry = nil
            detailError = nil
            PlayerCoordinator.shared.openComic(comic)
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
                    detailError = "comics.no_chapters_error".localized
                } else {
                    PlayerCoordinator.shared.openComic(detailedComic)
                }
            } catch {
                guard !Task.isCancelled, detailLoadingState.finishRequest(requestID) else { return }
                detailTask = nil
                detailError = error.localizedDescription
            }
        }
    }

    @ViewBuilder
    private func comicGridCard(_ comic: ComicItem) -> some View {
        let authorSuffix = comic.author.isEmpty ? "" : (", " + "comics.author_prefix".localized(comic.author))
        let latestSuffix = comic.latestChapterTitle.isEmpty ? "" : (", " + "comics.latest_prefix".localized(comic.latestChapterTitle))
        let a11yLabel = comic.title + authorSuffix + latestSuffix

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
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(AuroraColor.textPrimary)
                    .lineLimit(2, reservesSpace: true)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)

                VStack(alignment: .leading, spacing: AuroraMetrics.spacingXS) {
                    Text(comic.author.isEmpty ? comic.sourceName : comic.author)
                        .font(.caption)
                        .foregroundStyle(AuroraColor.textSecondary)
                        .lineLimit(1, reservesSpace: true)

                    Text(comic.latestChapterTitle.isEmpty ? "comics.latest_chapter".localized : comic.latestChapterTitle)
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(comic.latestChapterTitle.isEmpty ? AuroraColor.textTertiary : AuroraColor.brandLabel)
                        .lineLimit(1, reservesSpace: true)
                }
            }
            .frame(maxWidth: .infinity, alignment: .topLeading)
            .contentShape(Rectangle())
        }
        .auroraZoomSource("comic:\(comic.id)")
        .buttonStyle(AppleSpringCardButtonStyle())
        .onAppear {
            if comic.id == repo.comics.last?.id {
                Task {
                    await repo.loadNextPage()
                }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(a11yLabel)
        .accessibilityHint("comics.open_reader_hint".localized)
    }

    private func cancelComicDetailsLoad() {
        detailTask?.cancel()
        detailTask = nil
        detailLoadingState.cancelRequest()
    }
}

private struct PicacgDraftDismissalGuard: ViewModifier {
    let hasDraft: Bool
    let onDiscard: () -> Void

    func body(content: Content) -> some View {
        if #available(iOS 27.0, *) {
            content
                .interactiveDismissDisabled(hasDraft)
                .dismissalConfirmationDialog(
                    Text("dialog.discard_title".localized),
                    shouldPresent: hasDraft
                ) {
                    Button("dialog.discard_confirm".localized, role: .destructive) {
                        onDiscard()
                    }
                    Button("dialog.keep_editing".localized, role: .cancel) {}
                }
        } else {
            content
                .interactiveDismissDisabled(hasDraft)
        }
    }
}
