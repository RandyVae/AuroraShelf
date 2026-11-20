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
            return LiveVisualSpec(
                iconName: "video.fill",
                gradientColors: [Color(rgb: 0x0D1B2A), Color(rgb: 0xC2185B)],
                accentColor: Color(rgb: 0xFF4081)
            )
        case LiveSourceCatalog.hornyStudentsID, "chaturbate":
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
                Text("live.platforms_title".localized)
                    .font(.title3.bold())
                    .foregroundStyle(AuroraColor.textPrimary)
                    .accessibilityAddTraits(.isHeader)

                Spacer()

                Text("live.platforms_subtitle".localized)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(AuroraColor.brandLabel)
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

                                        HStack(spacing: 4) {
                                            Image(systemName: spec.iconName)
                                                .font(.subheadline.weight(.semibold))
                                                .symbolRenderingMode(.hierarchical)
                                                .foregroundStyle(isSelected ? .white : spec.accentColor)

                                            Circle()
                                                .fill(Color.red)
                                                .frame(width: 5, height: 5)
                                        }
                                    }
                                    .frame(width: 44, height: 36)
                                    .shadow(color: .black.opacity(isSelected ? 0.28 : 0.12), radius: 3, y: 1)

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

                                Text(LiveSourceCatalog.name(for: source.id))
                                    .font(.caption2.weight(isSelected ? .bold : .medium))
                                    .fontDesign(.rounded)
                                    .foregroundStyle(isSelected ? AuroraColor.textPrimary : AuroraColor.textSecondary)
                                    .lineLimit(1)
                                    .frame(width: 100)
                            }
                        }
                        .buttonStyle(AppleSpringCardButtonStyle())
                        .sensoryFeedback(.selection, trigger: isSelected)
                        .accessibilityLabel(
                            String(
                                format: "live.platform_a11y".localized,
                                source.name,
                                isSelected ? "live.status_watching".localized : "channels.status_tap".localized
                            )
                        )
                    }
                }
                .padding(.horizontal, AuroraMetrics.contentInset)
            }
        }
    }
}

/// 原生 Apple TV / 流媒体风格的精选直播卡片
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
                    .auroraZoomSource("live:\(stream.id)")

                    HStack(spacing: 4) {
                        Circle()
                            .fill(Color.red)
                            .frame(width: 6, height: 6)
                        Text("live.badge_live".localized)
                            .font(.caption2.weight(.black))
                            .foregroundStyle(.white)
                        if let viewCount = stream.viewCount.value {
                            Text("\(viewCount)")
                                .font(.caption2.weight(.bold).monospacedDigit())
                                .foregroundStyle(.white.opacity(0.88))
                        }
                    }
                    .padding(.horizontal, 7)
                    .padding(.vertical, 3)
                    .background(Color.black.opacity(0.72), in: Capsule())
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
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(AuroraColor.textPrimary)
                            .lineLimit(1, reservesSpace: true)

                        Text(stream.tags.isEmpty ? "card.fallback_live".localized : stream.tags.prefix(2).joined(separator: " · "))
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
                Label("live.enter_room".localized, systemImage: "play.circle.fill")
            }

            if let url = URL(string: stream.roomUrl), !stream.roomUrl.isEmpty {
                ShareLink(item: url) {
                    Label("live.share_room".localized, systemImage: "square.and.arrow.up")
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
                        .foregroundStyle(AuroraColor.textPrimary)
                    Text(String(format: "live.watching_now".localized, stream.viewCount.value ?? 0))
                        .font(.caption)
                        .foregroundStyle(AuroraColor.textSecondary)
                }
                .padding(.horizontal, 10)
                .padding(.bottom, 10)
            }
            .background(AuroraColor.canvas)
        }
        .accessibilityLabel(
            String(format: "live.room_a11y".localized, stream.username, stream.viewCount.value ?? 0)
        )
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
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.auroraContainerSize) private var containerSize

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
        DeviceLayout.resolve(size: containerSize, horizontalSizeClass: horizontalSizeClass)
    }

    private var heroHeight: CGFloat {
        DeviceLayout.heroHeight(for: formFactor, screenHeight: containerSize.height)
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
                .auroraZoomSource("live:\(stream.id)")

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
                Text(stream.username)
                    .font(.title2.bold())
                    .foregroundStyle(AuroraColor.textPrimary)
                    .multilineTextAlignment(.center)
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 2)
                    .fixedSize(horizontal: false, vertical: true)
                    .shadow(color: colorScheme == .dark ? .black.opacity(0.85) : .clear, radius: 8, x: 0, y: 3)
                    .padding(.horizontal, AuroraMetrics.contentInset)

                metadataView
                    .shadow(color: colorScheme == .dark ? .black.opacity(0.75) : .clear, radius: 6, x: 0, y: 2)

                GlassEffectContainer(spacing: AuroraMetrics.spacingM) {
                    HStack(spacing: AuroraMetrics.spacingM) {
                        watchButton
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
            HStack(spacing: 4) {
                Circle()
                    .fill(Color.white)
                    .frame(width: 6, height: 6)
                Text("live.badge_live".localized)
                    .font(.caption2.bold())
                    .foregroundStyle(.white)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(Color.red.opacity(0.90), in: Capsule())

            if let viewCount = stream.viewCount.value {
                Text(String(format: "live.online_count".localized, "\(viewCount)"))
                    .font(.caption.bold())
                    .foregroundStyle(AuroraColor.textPrimary)
                Text("•")
                    .foregroundStyle(AuroraColor.textTertiary)
            }

            Text(sourceName)
                .font(.caption.bold())
                .foregroundStyle(AuroraColor.brandLabel)

            if !stream.title.isEmpty {
                Text("•")
                    .foregroundStyle(AuroraColor.textTertiary)
                Text(stream.title)
                    .font(.caption2)
                    .foregroundStyle(AuroraColor.textSecondary)
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
                Text("live.watch_now".localized)
                    .font(.headline.weight(.bold))
            }
            .frame(maxWidth: .infinity, minHeight: 46)
            .background(AuroraColor.textPrimary, in: Capsule())
            .foregroundStyle(AuroraColor.canvas)
        }
        .buttonStyle(AppleSpringCardButtonStyle())
    }

    private var cycleButton: some View {
        Button(action: onCycle) {
            HStack(spacing: 6) {
                Image(systemName: "shuffle")
                    .font(.headline)
                Text("live.shuffle_streamer".localized)
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
        .accessibilityHint("live.shuffle_hint".localized)
    }
}

public struct LiveScreen: View {
    @ObservedObject public var repo = LiveRepository.shared
    public let onOpenSearch: () -> Void
    @State private var spotlightIndex: Int = 0
    @State private var isScrolled: Bool = false
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.auroraContainerSize) private var containerSize

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
        LiveSourceCatalog.name(for: repo.selectedSourceID)
    }

    public var body: some View {
        ZStack {
            AuroraColor.canvas.ignoresSafeArea()

            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: AuroraMetrics.spacingXL) {
                    if let spotlight = spotlightStream {
                        LiveHeroBanner(
                            stream: spotlight,
                            sourceName: currentSourceName,
                            onWatch: { openLiveStream(spotlight) },
                            onCycle: {
                                AuroraHaptics.selection()
                                AuroraMotion.perform(AuroraMotion.smooth, reduceMotion: reduceMotion) {
                                    spotlightIndex = (spotlightIndex + 1) % max(1, filteredStreams.count)
                                }
                            }
                        )
                    } else if repo.isLoading {
                        HeroLoadingPlaceholder()
                    } else {
                        Spacer().frame(height: 60)
                    }

                    LivePlatformsShelf(
                        selectedSourceID: repo.selectedSourceID,
                        onSelect: { sourceID in
                            AuroraMotion.perform(AuroraMotion.snappy, reduceMotion: reduceMotion) {
                                repo.selectSource(sourceID)
                            }
                        }
                    )

                    if !top10Streams.isEmpty {
                        VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                            HStack(spacing: AuroraMetrics.spacingS) {
                                Text("live.hot_featured".localized)
                                    .font(.title2.bold())
                                    .foregroundStyle(AuroraColor.textPrimary)
                                    .accessibilityAddTraits(.isHeader)

                                HStack(spacing: 4) {
                                    Circle()
                                        .fill(AuroraColor.brandLabel)
                                        .frame(width: 6, height: 6)
                                    Text("live.featured".localized.uppercased())
                                        .font(.caption2.weight(.black))
                                        .foregroundStyle(AuroraColor.brandLabel)
                                }
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(AuroraColor.brandLabel.opacity(0.12), in: Capsule())
                            }
                            .padding(.horizontal, AuroraMetrics.contentInset)

                            ScrollView(.horizontal, showsIndicators: false) {
                                LazyHStack(alignment: .top, spacing: AuroraMetrics.spacingM) {
                                    ForEach(top10Streams) { item in
                                        LiveFeaturedCard(stream: item) {
                                            openLiveStream(item)
                                        }
                                    }
                                }
                                .padding(.horizontal, AuroraMetrics.contentInset)
                            }
                        }
                    }

                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                        HStack(spacing: AuroraMetrics.spacingS) {
                            Text("live.hot_streams".localized)
                                .font(.title3.bold())
                                .foregroundStyle(AuroraColor.textPrimary)
                                .accessibilityAddTraits(.isHeader)

                            Spacer()

                            Text(String(format: "live.streamers_online".localized, filteredStreams.count))
                                .font(.subheadline)
                                .foregroundStyle(AuroraColor.textSecondary)
                        }
                        .padding(.horizontal, AuroraMetrics.contentInset)

                        SourceFilterBar(
                            categories: repo.categories,
                            selectedCategory: Binding(
                                get: { repo.selectedGender },
                                set: { repo.selectGender($0) }
                            )
                        )

                        feedState

                        LazyVGrid(columns: liveGridColumns, spacing: AuroraMetrics.spacingL) {
                            ForEach(filteredStreams) { item in
                                Button {
                                    openLiveStream(item)
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
                                            .auroraZoomSource("live:\(item.id)")

                                            HStack(spacing: 4) {
                                                Circle()
                                                    .fill(Color.red)
                                                    .frame(width: 6, height: 6)
                                                Text("live.badge_live".localized)
                                                    .font(.caption2.weight(.black))
                                                    .foregroundStyle(.white)
                                                if let viewCount = item.viewCount.value {
                                                    Text("\(viewCount)")
                                                        .font(.caption2.weight(.bold).monospacedDigit())
                                                        .foregroundStyle(.white.opacity(0.88))
                                                }
                                            }
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 4)
                                            .background(Color.black.opacity(0.72), in: Capsule())
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
                                                .font(.subheadline.weight(.semibold))
                                                .foregroundStyle(AuroraColor.textPrimary)
                                                .lineLimit(1, reservesSpace: true)
                                        }

                                        Text(item.localizedSubtitle.isEmpty ? (item.tags.first ?? "live.streaming_now".localized) : item.localizedSubtitle)
                                            .font(.caption)
                                            .foregroundStyle(AuroraColor.textSecondary)
                                            .lineLimit(1, reservesSpace: true)
                                    }
                                    .frame(maxWidth: .infinity, alignment: .topLeading)
                                }
                                .buttonStyle(AppleSpringCardButtonStyle())
                                .accessibilityElement(children: .combine)
                                .accessibilityLabel(liveCardAccessibilityLabel(for: item))
                                .accessibilityHint("live.open_room_hint".localized)
                                .onAppear {
                                    if item.id == filteredStreams.last?.id, repo.canLoadMore, !repo.isLoadingNextPage {
                                        Task { await repo.loadNextPage() }
                                    }
                                }
                            }
                        }
                        .padding(.horizontal, AuroraMetrics.contentInset)
                        .frame(maxWidth: 1080)
                        .frame(maxWidth: .infinity)

                        if repo.isLoadingNextPage {
                            HStack(spacing: AuroraMetrics.spacingS) {
                                ProgressView().tint(AuroraColor.brandLabel)
                                Text("home.loading_more".localized)
                                    .font(.caption)
                                    .foregroundStyle(AuroraColor.textSecondary)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, AuroraMetrics.spacingM)
                        }
                    }

                    Spacer().frame(height: max(AuroraMetrics.spacingXXL, 96))
                }
            }
            .auroraImmersiveHeroScrollEdge(
                isScrolled: $isScrolled,
                hasHeroContent: spotlightStream != nil || repo.isLoading,
                reduceMotion: reduceMotion
            )
            .ignoresSafeArea(edges: .top)
            .refreshable {
                repo.loadStreams()
                try? await Task.sleep(for: .milliseconds(150))
                while repo.isLoading {
                    try? await Task.sleep(for: .milliseconds(80))
                }
            }
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .auroraImmersiveHeroNavigationBar(
            isScrolled: isScrolled,
            hasHeroContent: spotlightStream != nil || repo.isLoading
        )
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                NetgreyMarkView(width: 20, height: 28)
            }
            .sharedBackgroundVisibility(.hidden)

            ToolbarItem(placement: .topBarTrailing) {
                HStack(spacing: AuroraMetrics.spacingM) {
                    AirPlayRoutePicker()
                        .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)

                    Button(action: onOpenSearch) {
                        Image(systemName: "magnifyingglass")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.primary)
                    }
                    .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                    .contentShape(Rectangle())
                    .accessibilityLabel("live.search_a11y".localized)
                }
            }
        }
        .onAppear {
            repo.loadStreamsIfNeeded()
            if CommandLine.arguments.contains("--select-stripchat") {
                repo.selectSource(LiveSourceCatalog.stripchatID)
            }
        }
        .onReceive(repo.$liveStreams) { newStreams in
            if CommandLine.arguments.contains("--auto-open-live-detail"),
               let first = newStreams.first,
               PlayerCoordinator.shared.activeRoute == nil {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                    openLiveStream(first)
                }
            }
        }
    }

    private func openLiveStream(_ stream: LiveStreamItem) {
        let related = repo.liveStreams.filter { $0.id != stream.id }
        PlayerCoordinator.shared.openLive(stream, relatedStreams: related)
    }

    @ViewBuilder
    private var feedState: some View {
        Group {
            if repo.liveStreams.isEmpty && repo.isLoading {
                VStack(spacing: AuroraMetrics.spacingM) {
                    ProgressView()
                        .tint(AuroraColor.brandLabel)
                    Text("live.loading".localized)
                        .font(.subheadline)
                        .foregroundStyle(AuroraColor.textSecondary)
                }
                .frame(maxWidth: .infinity, minHeight: 300)
            } else if repo.liveStreams.isEmpty, let error = repo.loadErrorMessage {
                ContentUnavailableView {
                    Label("live.load_failed".localized, systemImage: "wifi.exclamationmark")
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
                    Text("live.empty_desc".localized)
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
                        Button("common.retry".localized, systemImage: "arrow.clockwise") {
                            repo.loadStreams()
                        }
                        .labelStyle(.iconOnly)
                        .frame(minWidth: AuroraMetrics.minTouchTarget, minHeight: AuroraMetrics.minTouchTarget)
                    }
                    .padding(.horizontal, AuroraMetrics.contentInset)
                }

                if filteredStreams.isEmpty {
                    ContentUnavailableView {
                        Label("live.category_empty_title".localized, systemImage: "person.slash")
                    } description: {
                        Text("live.category_empty_desc".localized)
                    } actions: {
                        Button("live.show_all".localized, systemImage: "arrow.uturn.backward") {
                            AuroraMotion.perform(AuroraMotion.quick, reduceMotion: reduceMotion) {
                                repo.selectGender("全部")
                            }
                        }
                        .buttonStyle(.bordered)
                    }
                    .frame(maxWidth: .infinity, minHeight: 300)
                }
            }
        }
    }

    private func liveCardAccessibilityLabel(for item: LiveStreamItem) -> String {
        let titleSegment = item.title.isEmpty ? "" : ", \(item.title)"
        let viewerSegment = item.viewCount.value.map { String(format: "live.viewer_count_segment".localized, $0) } ?? ""
        return String(format: "live.streamer_a11y".localized, item.username, titleSegment, viewerSegment)
    }

    private var filteredStreams: [LiveStreamItem] {
        repo.liveStreams.filter { LiveRepository.matchesSelectedGender(itemGender: $0.gender, selectedGender: repo.selectedGender) }
    }

    private var liveGridColumns: [GridItem] {
        let minimumWidth: CGFloat = dynamicTypeSize.isAccessibilitySize ? 300 : 160
        return [GridItem(.adaptive(minimum: minimumWidth, maximum: 420), spacing: AuroraMetrics.spacingM)]
    }

    private var retryButton: some View {
        Button("common.retry".localized, systemImage: "arrow.clockwise") {
            repo.loadStreams()
        }
        .buttonStyle(.borderedProminent)
        .tint(AuroraColor.brandSolid)
    }
}
