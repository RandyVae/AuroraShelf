import SwiftUI

/// 论坛板块视觉规约（对齐 Apple TV「Channels」品牌视觉规范）
public struct ForumBoardVisualSpec: Sendable {
    public let iconName: String
    public let gradientColors: [Color]
    public let accentColor: Color

    public init(iconName: String, gradientColors: [Color], accentColor: Color) {
        self.iconName = iconName
        self.gradientColors = gradientColors
        self.accentColor = accentColor
    }

    public static func forBoard(_ fid: Int?) -> ForumBoardVisualSpec {
        guard let fid else {
            // 全部板块：黑曜石现代极简灰
            return ForumBoardVisualSpec(
                iconName: "tray.2.fill",
                gradientColors: [Color(rgb: 0x263238), Color(rgb: 0x37474F)],
                accentColor: Color(rgb: 0x90A4AE)
            )
        }

        switch fid {
        case 100:
            // 草榴技术区：经典翡翠墨绿
            return ForumBoardVisualSpec(
                iconName: "wrench.and.screwdriver.fill",
                gradientColors: [Color(rgb: 0x1B5E20), Color(rgb: 0x2E7D32)],
                accentColor: Color(rgb: 0x81C784)
            )
        case 16, 33:
            // 达盖尔的旗帜 / 唯美摄影：深海孔雀绿
            return ForumBoardVisualSpec(
                iconName: "photo.artframe",
                gradientColors: [Color(rgb: 0x004D40), Color(rgb: 0x00695C)],
                accentColor: Color(rgb: 0x80CBC4)
            )
        case 4:
            // 91 原创自拍：烈焰宝石红
            return ForumBoardVisualSpec(
                iconName: "camera.fill",
                gradientColors: [Color(rgb: 0xB71C1C), Color(rgb: 0xD32F2F)],
                accentColor: Color(rgb: 0xFF8A80)
            )
        case 21:
            // 我爱我妻：柔美洋红玫瑰
            return ForumBoardVisualSpec(
                iconName: "heart.circle.fill",
                gradientColors: [Color(rgb: 0x880E4F), Color(rgb: 0xAD1457)],
                accentColor: Color(rgb: 0xF48FB1)
            )
        case 33:
            // 草榴达盖尔：深海孔雀绿
            return ForumBoardVisualSpec(
                iconName: "photo.artframe",
                gradientColors: [Color(rgb: 0x004D40), Color(rgb: 0x00695C)],
                accentColor: Color(rgb: 0x80CBC4)
            )
        case 34:
            // 新时代征文：活力暖金柑橙
            return ForumBoardVisualSpec(
                iconName: "text.book.closed.fill",
                gradientColors: [Color(rgb: 0xE65100), Color(rgb: 0xEF6C00)],
                accentColor: Color(rgb: 0xFFB74D)
            )
        case 36:
            // 东方文学：暗曜紫罗兰
            return ForumBoardVisualSpec(
                iconName: "books.vertical.fill",
                gradientColors: [Color(rgb: 0x311B92), Color(rgb: 0x4527A0)],
                accentColor: Color(rgb: 0xB39DDB)
            )
        case 37:
            // 经典影视：经典剧院深红
            return ForumBoardVisualSpec(
                iconName: "film.stack.fill",
                gradientColors: [Color(rgb: 0x660A10), Color(rgb: 0xB71C1C)],
                accentColor: Color(rgb: 0xFF5252)
            )
        case 38:
            // 网友自拍：珊瑚晚霞橙
            return ForumBoardVisualSpec(
                iconName: "person.crop.rectangle.fill",
                gradientColors: [Color(rgb: 0xBF360C), Color(rgb: 0xD84315)],
                accentColor: Color(rgb: 0xFF8A65)
            )
        default:
            return ForumBoardVisualSpec(
                iconName: "bubble.left.and.bubble.right.fill",
                gradientColors: [Color(rgb: 0x1A237E), Color(rgb: 0x283593)],
                accentColor: Color(rgb: 0x9FA8DA)
            )
        }
    }
}

/// Apple TV 风格的「社区板块（Channels）」横向货架
public struct ForumBoardsShelf: View {
    public let selectedBoardFid: Int?
    public let onSelect: (Int?) -> Void

    public init(
        selectedBoardFid: Int?,
        onSelect: @escaping (Int?) -> Void
    ) {
        self.selectedBoardFid = selectedBoardFid
        self.onSelect = onSelect
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
            HStack(spacing: AuroraMetrics.spacingS) {
                Text("社区板块")
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundStyle(AuroraColor.textPrimary)

                Spacer()

                Text("精选讨论区")
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundStyle(AuroraColor.brand)
            }
            .padding(.horizontal, AuroraMetrics.contentInset)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: AuroraMetrics.spacingM) {
                    boardCard(nil, title: "全部")

                    ForEach(ForumSourceCatalog.allBoards) { board in
                        boardCard(board.fid, title: board.name)
                    }
                }
                .padding(.horizontal, AuroraMetrics.contentInset)
            }
        }
    }

    @ViewBuilder
    private func boardCard(_ fid: Int?, title: String) -> some View {
        let isSelected = selectedBoardFid == fid
        let spec = ForumBoardVisualSpec.forBoard(fid)

        Button {
            onSelect(fid)
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
                        .frame(width: 96, height: 56)
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(
                                    isSelected ? spec.accentColor : Color.white.opacity(0.18),
                                    lineWidth: isSelected ? 2.5 : 1
                                )
                        )

                    Image(systemName: spec.iconName)
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(.white)
                        .shadow(color: .black.opacity(0.5), radius: 4, x: 0, y: 2)
                }
                .shadow(
                    color: isSelected ? spec.accentColor.opacity(0.45) : .clear,
                    radius: 8,
                    x: 0,
                    y: 3
                )

                Text(title)
                    .font(isSelected ? .caption.weight(.bold) : .caption.weight(.medium))
                    .foregroundStyle(isSelected ? AuroraColor.textPrimary : AuroraColor.textSecondary)
                    .lineLimit(1)
                    .frame(width: 96)
            }
        }
        .buttonStyle(AppleSpringCardButtonStyle())
        .accessibilityLabel("社区板块 \(title)，\(isSelected ? "当前正在查看" : "轻点切换")")
    }
}

/// 原生社区精选帖子卡片：严格统一 16:9 封面画幅与卡片高宽，拔除高低参差不齐缺陷
public struct ForumFeaturedCard: View {
    public let post: ForumPostItem
    public let cardWidth: CGFloat

    public init(post: ForumPostItem, cardWidth: CGFloat = 200) {
        self.post = post
        self.cardWidth = cardWidth
    }

    private var coverHeight: CGFloat {
        cardWidth * (9.0 / 16.0)
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
            // 1. 严格统一尺寸的 16:9 封面容器
            ZStack(alignment: .bottomLeading) {
                if let preview = post.previewImageUrl, !preview.isEmpty {
                    AuroraCoverImage(
                        url: preview,
                        title: post.title,
                        sourceBaseURL: URL(string: post.threadUrl),
                        aspectRatio: nil,
                        contentMode: .fill,
                        cornerRadius: AuroraMetrics.cardRadius,
                        placeholderStyle: .forum
                    )
                    .frame(width: cardWidth, height: coverHeight)
                    .clipped()
                } else {
                    let spec = ForumBoardVisualSpec.forBoard(post.fid)
                    ZStack {
                        RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                            .fill(
                                LinearGradient(
                                    colors: spec.gradientColors,
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )

                        Image(systemName: spec.iconName)
                            .font(.system(size: 32, weight: .light))
                            .foregroundStyle(.white.opacity(0.2))

                        Text(post.title)
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(.white.opacity(0.95))
                            .padding(12)
                            .lineLimit(3)
                            .multilineTextAlignment(.leading)
                    }
                    .frame(width: cardWidth, height: coverHeight)
                }

                // 板块标识胶囊
                Text(post.boardName)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.black.opacity(0.65), in: Capsule())
                    .padding(8)
            }
            .frame(width: cardWidth, height: coverHeight)
            .clipShape(RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                    .stroke(AuroraColor.hairline, lineWidth: 1)
            )

            // 2. 标题固定高度插槽（38pt），无论单行双行均顶部对齐
            Text(post.title)
                .font(.subheadline)
                .fontWeight(.semibold)
                .foregroundStyle(AuroraColor.textPrimary)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
                .frame(width: cardWidth, height: 38, alignment: .topLeading)

            // 3. 元数据固定高度插槽（16pt）
            HStack(spacing: 6) {
                if let author = post.author, !author.isEmpty {
                    Text(author)
                        .font(.caption2)
                        .foregroundStyle(AuroraColor.textSecondary)
                        .lineLimit(1)
                } else {
                    Text(post.boardName)
                        .font(.caption2)
                        .foregroundStyle(AuroraColor.textSecondary)
                        .lineLimit(1)
                }

                Spacer()

                if let replies = post.replies {
                    Label("\(replies)", systemImage: "bubble.left")
                        .font(.caption2)
                        .foregroundStyle(AuroraColor.textSecondary)
                }
            }
            .frame(width: cardWidth, height: 16)
        }
        .frame(width: cardWidth, alignment: .topLeading)
    }
}

/// 原生 Forum Hero 聚焦横幅：1:1 对齐 Apple TV 聚光灯大卡
public struct ForumHeroBillboard: View {
    public let post: ForumPostItem
    public let onEnter: () -> Void
    public let onCycle: () -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    public init(
        post: ForumPostItem,
        onEnter: @escaping () -> Void,
        onCycle: @escaping () -> Void
    ) {
        self.post = post
        self.onEnter = onEnter
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
            // 背景封面或板块光晕
            if let preview = post.previewImageUrl, !preview.isEmpty {
                AuroraCoverImage(
                    url: preview,
                    title: post.title,
                    aspectRatio: isWideAspect ? 16 / 9 : 4 / 3,
                    contentMode: .fill,
                    cornerRadius: 0
                )
                .frame(maxWidth: .infinity, maxHeight: heroHeight)
                .clipped()
            } else {
                let spec = ForumBoardVisualSpec.forBoard(post.fid)
                ZStack {
                    LinearGradient(
                        colors: spec.gradientColors + [AuroraColor.canvas],
                        startPoint: .top,
                        endPoint: .bottom
                    )

                    Image(systemName: spec.iconName)
                        .font(.system(size: 140, weight: .ultraLight))
                        .foregroundStyle(.white.opacity(0.08))
                        .offset(y: -40)
                }
                .frame(maxWidth: .infinity, maxHeight: heroHeight)
            }

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
                Text(post.title)
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
                    enterButton
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
            Text("热帖聚焦")
                .font(.caption2.bold())
                .foregroundStyle(.white)
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(
                    (post.boardName.contains("草榴") || post.boardName.contains("达盖尔") || post.fid == 100 || post.fid == 16)
                        ? AuroraColor.forestGreen.opacity(0.85) : AuroraColor.accentRed.opacity(0.85),
                    in: Capsule()
                )

            Text(post.boardName)
                .font(.caption.bold())
                .foregroundStyle(AuroraColor.brandLabel)

            if let author = post.author {
                Text("•")
                    .foregroundStyle(.white.opacity(0.6))
                Text(author)
                    .font(.caption.bold())
                    .foregroundStyle(.white)
            }

            if let replies = post.replies {
                Text("•")
                    .foregroundStyle(.white.opacity(0.6))
                Text("\(replies) 回复")
                    .font(.caption2)
                    .foregroundStyle(.white.opacity(0.85))
            }
        }
        .lineLimit(1)
        .fixedSize(horizontal: false, vertical: true)
    }

    private var enterButton: some View {
        Button(action: onEnter) {
            HStack(spacing: 6) {
                Image(systemName: "bubble.left.and.bubble.right.fill")
                    .font(.headline)
                Text("进入讨论")
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
                Text("换篇热帖")
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
        .accessibilityHint("切换另一篇推荐热帖")
    }
}

public struct ForumScreen: View {
    @ObservedObject public var repo = ForumRepository.shared
    public let onOpenSearch: () -> Void
    @State private var spotlightIndex: Int = 0
    @State private var selectedPost: ForumPostItem? = nil
    @State private var isScrolled: Bool = false
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    public init(onOpenSearch: @escaping () -> Void = {}) {
        self.onOpenSearch = onOpenSearch
    }

    private var spotlightPost: ForumPostItem? {
        guard !repo.posts.isEmpty else { return nil }
        let validIndex = spotlightIndex % repo.posts.count
        return repo.posts[validIndex]
    }

    private var top10Posts: [ForumPostItem] {
        Array(repo.posts.prefix(10))
    }

    private var currentBoardTitle: String {
        if let fid = repo.selectedBoardFid {
            return ForumSourceCatalog.allBoards.first(where: { $0.fid == fid })?.name ?? "板块"
        }
        return "全部板块"
    }

    public var body: some View {
        ZStack {
            AuroraColor.canvas.ignoresSafeArea()

            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: AuroraMetrics.spacingXL) {
                    // 1. 顶部 Community Hero 聚焦大卡（Apple TV 影院级画幅）
                    if let spotlight = spotlightPost {
                        ForumHeroBillboard(
                            post: spotlight,
                            onEnter: { selectedPost = spotlight },
                            onCycle: {
                                withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                                    spotlightIndex = (spotlightIndex + 1) % max(1, repo.posts.count)
                                }
                            }
                        )
                    } else if repo.isLoading {
                        HeroLoadingPlaceholder()
                    } else {
                        Spacer().frame(height: 60)
                    }

                    // 2. Apple TV 核心层级：精选社区板块（Channels 货架）
                    ForumBoardsShelf(
                        selectedBoardFid: repo.selectedBoardFid,
                        onSelect: { fid in
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                                repo.selectBoard(fid)
                            }
                        }
                    )

                    // 3. 热门精华讨论（规范宽幅图文卡，拔除生硬的 1234 排行大字）
                    if !top10Posts.isEmpty {
                        VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                            HStack(spacing: AuroraMetrics.spacingS) {
                                Text("热门精华")
                                    .font(.title2)
                                    .fontWeight(.bold)
                                    .foregroundStyle(AuroraColor.textPrimary)

                                Text("FEATURED")
                                    .font(.caption2)
                                    .fontWeight(.black)
                                    .foregroundStyle(AuroraColor.forestGreen)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(AuroraColor.forestGreen.opacity(0.12), in: Capsule())
                            }
                            .padding(.horizontal, AuroraMetrics.contentInset)

                            ScrollView(.horizontal, showsIndicators: false) {
                                LazyHStack(alignment: .top, spacing: AuroraMetrics.spacingM) {
                                    ForEach(top10Posts) { post in
                                        NavigationLink(value: post) {
                                            ForumFeaturedCard(post: post)
                                        }
                                        .buttonStyle(AppleSpringCardButtonStyle())
                                    }
                                }
                                .padding(.horizontal, AuroraMetrics.contentInset)
                            }
                        }
                    }

                    // 4. 板块讨论信息流
                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                        HStack(spacing: AuroraMetrics.spacingS) {
                            Text("\(currentBoardTitle) 讨论")
                                .font(.title3)
                                .fontWeight(.bold)
                                .foregroundStyle(AuroraColor.textPrimary)

                            Spacer()

                            Text("\(repo.posts.count) 篇主题")
                                .font(.subheadline)
                                .foregroundStyle(AuroraColor.textSecondary)
                        }
                        .padding(.horizontal, AuroraMetrics.contentInset)

                        forumContent
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
            .refreshable {
                await repo.reloadPosts()
            }
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground((isScrolled || spotlightPost == nil) ? .visible : .hidden, for: .navigationBar)
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
                    .accessibilityLabel("搜索社区")
                }
            }
        }
        .navigationDestination(for: ForumPostItem.self) { post in
            ForumPostDetailView(post: post)
        }
        .navigationDestination(item: $selectedPost) { post in
            ForumPostDetailView(post: post)
        }
    }

    @ViewBuilder
    private var forumContent: some View {
        if repo.posts.isEmpty && repo.isLoading {
            ProgressView("forum.loading".localized)
                .frame(maxWidth: .infinity, minHeight: 240)
        } else if repo.posts.isEmpty, let errorMessage = repo.errorMessage {
            ContentUnavailableView {
                Label("加载失败", systemImage: "wifi.exclamationmark")
            } description: {
                Text(errorMessage)
            } actions: {
                Button("重试", systemImage: "arrow.clockwise") {
                    repo.loadPosts()
                }
                .buttonStyle(.borderedProminent)
                .tint(AuroraColor.brand)
            }
            .frame(maxWidth: .infinity, minHeight: 280)
        } else if repo.posts.isEmpty {
            ContentUnavailableView {
                Label("暂无帖子", systemImage: "text.bubble")
            } description: {
                Text("当前板块没有可显示的帖子。")
            } actions: {
                Button("刷新", systemImage: "arrow.clockwise") {
                    repo.loadPosts()
                }
                .buttonStyle(.bordered)
            }
            .frame(maxWidth: .infinity, minHeight: 280)
        } else {
            VStack(spacing: AuroraMetrics.spacingM) {
                if let errorMessage = repo.errorMessage {
                    partialFailureBanner(errorMessage)
                }

                LazyVStack(spacing: AuroraMetrics.spacingM) {
                    ForEach(repo.posts, id: \.compositeID) { post in
                        NavigationLink(value: post) {
                            postCard(post)
                        }
                        .buttonStyle(AppleSpringCardButtonStyle())
                        .onAppear {
                            guard post.compositeID == repo.posts.last?.compositeID else { return }
                            Task { await repo.loadNextPage() }
                        }
                    }

                    if repo.isLoadingNextPage {
                        ProgressView("正在加载更多帖子…")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, AuroraMetrics.spacingM)
                    } else if repo.errorMessage != nil {
                        Button("继续加载", systemImage: "arrow.clockwise") {
                            Task { await repo.loadNextPage() }
                        }
                        .buttonStyle(.bordered)
                    }
                }
                .padding(.horizontal, AuroraMetrics.contentInset)
            }
        }
    }

    private func partialFailureBanner(_ message: String) -> some View {
        HStack(alignment: .top, spacing: AuroraMetrics.spacingS) {
            Label("部分板块暂不可用", systemImage: "exclamationmark.triangle")
                .font(.caption.weight(.semibold))
                .foregroundStyle(AuroraColor.textPrimary)
                .labelStyle(.titleAndIcon)

            Text(message)
                .font(.caption)
                .foregroundStyle(AuroraColor.textSecondary)
                .lineLimit(3)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button {
                repo.loadPosts()
            } label: {
                Image(systemName: "arrow.clockwise")
                    .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("重试加载论坛")
        }
        .padding(.horizontal, AuroraMetrics.contentInset)
        .padding(.vertical, AuroraMetrics.spacingS)
        .background(AuroraColor.surface)
    }

    private func postCard(_ post: ForumPostItem) -> some View {
        VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
            Text(post.title)
                .font(.headline)
                .foregroundStyle(AuroraColor.textPrimary)
                .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 2)
                .fixedSize(horizontal: false, vertical: true)
                .multilineTextAlignment(.leading)

            HStack(spacing: AuroraMetrics.spacingS) {
                let isT66y = post.boardName.contains("草榴") || post.boardName.contains("达盖尔") || post.fid == 100 || post.fid == 16
                Text(post.boardName)
                    .font(.caption2.weight(.heavy))
                    .foregroundStyle(AuroraColor.textOnBrand)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 3)
                    .background(
                        RoundedRectangle(cornerRadius: 4, style: .continuous)
                            .fill(isT66y ? AuroraColor.forestGreen : AuroraColor.accentRed)
                    )

                if let author = post.author {
                    Text(author)
                        .font(.caption)
                        .foregroundStyle(AuroraColor.textSecondary)
                        .lineLimit(1)
                }

                Spacer(minLength: AuroraMetrics.spacingS)

                if let replies = post.replies {
                    Label("\(replies)", systemImage: "bubble.left")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AuroraColor.textSecondary)
                        .labelStyle(.titleAndIcon)
                }
            }
        }
        .padding(AuroraMetrics.spacingL)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background {
            RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                .fill(AuroraColor.surface)
                .overlay {
                    RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                        .stroke(AuroraColor.hairline, lineWidth: 1)
                }
        }
        .contentShape(RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous))
    }
}
