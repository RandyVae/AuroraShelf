import SwiftUI

/// Apple TV 风格的影视详情页（Media Detail Page）
public struct VideoDetailSheet: View {
    public let video: VideoItem
    public let onPlay: (VideoItem) -> Void
    public let onDismiss: () -> Void
    public var onSearchKeyword: ((String) -> Void)? = nil

    @ObservedObject private var watchlist = WatchlistStore.shared
    @ObservedObject private var progressStore = PlaybackProgressStore.shared
    @ObservedObject private var videoRepo = VideoRepository.shared
    @ObservedObject private var downloadManager = OfflineDownloadManager.shared

    @State private var isDescriptionExpanded: Bool = false
    @State private var watchlistError: String?
    @State private var episodes: [PlaybackEpisode] = []
    @State private var selectedEpisode: PlaybackEpisode?
    @State private var selectedSeasonIndex: Int = 0
    @State private var isLoadingEpisodes: Bool = false
    @State private var isShowingEpisodePicker: Bool = false
    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    public init(
        video: VideoItem,
        onPlay: @escaping (VideoItem) -> Void,
        onDismiss: @escaping () -> Void,
        onSearchKeyword: ((String) -> Void)? = nil
    ) {
        self.video = video
        self.onPlay = onPlay
        self.onDismiss = onDismiss
        self.onSearchKeyword = onSearchKeyword
    }

    private struct CastMember: Identifiable {
        let id = UUID()
        let name: String
        let role: String
        let icon: String
        let isSearchable: Bool
    }

    private var parsedCastMembers: [CastMember] {
        var list: [CastMember] = []
        var seen = Set<String>()

        // 1. 扫描 tags 中的演职员与导演信息
        for tag in video.tags {
            let trimmed = tag.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else { continue }

            if trimmed.contains("导演") {
                let clean = trimmed
                    .replacingOccurrences(of: "导演:", with: "")
                    .replacingOccurrences(of: "导演：", with: "")
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                for name in clean.split(separator: ",").flatMap({ $0.split(separator: " ") }) {
                    let str = String(name).trimmingCharacters(in: .whitespaces)
                    if !str.isEmpty && !seen.contains(str) {
                        seen.insert(str)
                        list.append(CastMember(name: str, role: "导演", icon: "person.crop.circle.badge.checkmark", isSearchable: true))
                    }
                }
            } else if trimmed.contains("主演") || trimmed.contains("演员") {
                let clean = trimmed
                    .replacingOccurrences(of: "主演:", with: "")
                    .replacingOccurrences(of: "主演：", with: "")
                    .replacingOccurrences(of: "演员:", with: "")
                    .replacingOccurrences(of: "演员：", with: "")
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                for name in clean.split(separator: ",").flatMap({ $0.split(separator: " ") }) {
                    let str = String(name).trimmingCharacters(in: .whitespaces)
                    if !str.isEmpty && !seen.contains(str) {
                        seen.insert(str)
                        list.append(CastMember(name: str, role: "主演", icon: "person.crop.circle.fill", isSearchable: true))
                    }
                }
            } else if trimmed.count >= 2 && trimmed.count <= 6 && !trimmed.contains("4K") && !trimmed.contains("HDR") && !trimmed.contains("国语") && !trimmed.contains("中字") {
                if !seen.contains(trimmed) {
                    seen.insert(trimmed)
                    list.append(CastMember(name: trimmed, role: "演员 / 标签", icon: "person.fill", isSearchable: true))
                }
            }
        }

        // 2. 补足平台与出品音画母带保证 Apple TV 质感
        list.append(CastMember(name: video.sourceName, role: "出品源 · 分发", icon: "tv", isSearchable: false))
        list.append(CastMember(name: "超清母带组", role: "4K 影视工程", icon: "film", isSearchable: false))
        list.append(CastMember(name: "官方原声", role: "杜比全景声", icon: "waveform", isSearchable: false))
        list.append(CastMember(name: "官方译制", role: "双语字幕", icon: "captions.bubble", isSearchable: false))

        return list
    }

    private var seasons: [(name: String, episodes: [PlaybackEpisode])] {
        guard !episodes.isEmpty else { return [] }
        if episodes.count <= 12 {
            return [("全 \(episodes.count) 集", episodes)]
        }
        var result: [(name: String, episodes: [PlaybackEpisode])] = []
        let chunkSize = 12
        for (i, chunk) in stride(from: 0, to: episodes.count, by: chunkSize).enumerated() {
            let end = min(chunk + chunkSize, episodes.count)
            let seasonEpisodes = Array(episodes[chunk..<end])
            let name = "第 \(i + 1) 季 (\(chunk + 1)-\(end)集)"
            result.append((name: name, episodes: seasonEpisodes))
        }
        return result
    }

    private var activeSeasonEpisodes: [PlaybackEpisode] {
        let allSeasons = seasons
        guard !allSeasons.isEmpty else { return episodes }
        let clampedIndex = min(max(0, selectedSeasonIndex), allSeasons.count - 1)
        return allSeasons[clampedIndex].episodes
    }

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    private var formFactor: DeviceFormFactor {
        DeviceLayout.resolveCurrent(horizontalSizeClass: horizontalSizeClass)
    }

    private var backdropHeight: CGFloat {
        switch formFactor {
        case .pad: return 480
        case .duoUnfolded: return 360
        case .duoFolded: return 220
        case .standardPhone: return 280
        }
    }

    public init(
        video: VideoItem,
        onPlay: @escaping (VideoItem) -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self.video = video
        self.onPlay = onPlay
        self.onDismiss = onDismiss
    }

    public var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: AuroraMetrics.spacingXL) {
                    // 1. 顶部 16:9 全宽高清背景海报与渐变遮罩
                    ZStack(alignment: .bottomLeading) {
                        AuroraCoverImage(
                            url: video.coverUrl,
                            title: video.title,
                            sourceBaseURL: video.coverBaseURL,
                            aspectRatio: 16.0 / 9.0,
                            contentMode: .fill,
                            cornerRadius: 0
                        )
                        .frame(maxWidth: .infinity, maxHeight: backdropHeight)
                        .clipped()

                        // 渐变过渡遮罩
                        LinearGradient(
                            stops: [
                                .init(color: .clear, location: 0.0),
                                .init(color: AuroraColor.canvas.opacity(0.8), location: 0.6),
                                .init(color: AuroraColor.canvas, location: 1.0)
                            ],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                        .frame(height: 180)

                        // 标题与来源浮层
                        VStack(alignment: .leading, spacing: 6) {
                            Text(video.title)
                                .font(.title2)
                                .fontWeight(.bold)
                                .foregroundStyle(.white)
                                .shadow(color: .black.opacity(0.9), radius: 6, x: 0, y: 2)

                            HStack(spacing: AuroraMetrics.spacingS) {
                                Text(video.sourceName)
                                    .font(.caption)
                                    .fontWeight(.bold)
                                    .foregroundStyle(AuroraColor.brandLabel)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 3)
                                    .background(AuroraColor.brand.opacity(0.18), in: Capsule())

                                if !video.rating.isEmpty {
                                    HStack(spacing: 3) {
                                        Image(systemName: "star.fill")
                                            .font(.caption2)
                                            .foregroundStyle(.yellow)
                                        Text(video.rating)
                                            .font(.caption)
                                            .fontWeight(.bold)
                                            .foregroundStyle(.white)
                                    }
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 3)
                                    .background(.black.opacity(0.5), in: Capsule())
                                }

                                if !video.duration.isEmpty {
                                    Text(video.duration)
                                        .font(.caption)
                                        .foregroundStyle(.white.opacity(0.85))
                                }
                            }
                        }
                        .padding(.horizontal, AuroraMetrics.contentInset)
                        .padding(.bottom, AuroraMetrics.spacingM)
                    }

                    // 2. 核心操作按钮栏（Apple TV 原生标准：高对比纯白播放胶囊 + 磨砂质感辅助圆钮）
                    HStack(spacing: AuroraMetrics.spacingM) {
                        // 主播放按钮（深色下纯白高光大底，提供最高视觉优先级）
                        Button {
                            onDismiss()
                            if let ep = selectedEpisode {
                                onPlay(videoForEpisode(ep))
                            } else {
                                onPlay(video)
                            }
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "play.fill")
                                    .font(.headline)
                                Text(selectedEpisode != nil && episodes.count > 1 ? "\("home.play".localized) \(selectedEpisode!.label)" : "home.play".localized)
                                    .font(.headline.weight(.semibold))
                            }
                            .frame(maxWidth: .infinity, minHeight: 48)
                            .background(Color.white, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .foregroundStyle(Color.black)
                        }
                        .buttonStyle(AppleSpringCardButtonStyle())
                        .hoverEffect(.highlight)

                        // 片单收藏按钮（Apple TV 毛玻璃容器）
                        let isBookmarked = watchlist.contains(video)
                        Button {
                            do {
                                try watchlist.setBookmarked(!isBookmarked, for: video)
                            } catch {
                                watchlistError = error.localizedDescription
                            }
                        } label: {
                            Image(systemName: isBookmarked ? "bookmark.fill" : "bookmark")
                                .font(.title3)
                                .frame(width: 48, height: 48)
                                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .stroke(Color.white.opacity(0.15), lineWidth: 0.5)
                                )
                                .foregroundStyle(isBookmarked ? AuroraColor.brand : .white)
                        }
                        .buttonStyle(AppleSpringCardButtonStyle())
                        .hoverEffect(.highlight)

                        // 离线缓存下载按钮（Apple TV 毛玻璃容器）
                        let targetVideo = selectedEpisode != nil ? videoForEpisode(selectedEpisode!) : video
                        let isDownloaded = downloadManager.isDownloaded(video: targetVideo, episode: selectedEpisode)
                        let isDownloading = downloadManager.status(for: targetVideo, episode: selectedEpisode)?.isDownloading ?? false
                        Button {
                            let impact = UIImpactFeedbackGenerator(style: .medium)
                            impact.impactOccurred()
                            if !isDownloaded && !isDownloading {
                                downloadManager.startDownload(video: targetVideo, episode: selectedEpisode)
                            }
                        } label: {
                            ZStack {
                                if isDownloading {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: AuroraColor.brand))
                                        .scaleEffect(0.85)
                                } else if isDownloaded {
                                    Image(systemName: "checkmark.circle.fill")
                                        .font(.title3)
                                        .foregroundStyle(AuroraColor.brand)
                                } else {
                                    Image(systemName: "arrow.down.circle")
                                        .font(.title3)
                                        .foregroundStyle(.white)
                                }
                            }
                            .frame(width: 48, height: 48)
                            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .stroke(Color.white.opacity(0.15), lineWidth: 0.5)
                            )
                        }
                        .buttonStyle(AppleSpringCardButtonStyle())
                        .hoverEffect(.highlight)
                        .accessibilityLabel(isDownloaded ? "已离线缓存" : (isDownloading ? "正在下载" : "离线缓存"))

                        // 原生分享（Apple TV 毛玻璃容器）
                        if let shareURL = URL(string: video.playbackUrl), !video.playbackUrl.isEmpty {
                            ShareLink(item: shareURL) {
                                Image(systemName: "square.and.arrow.up")
                                    .font(.title3)
                                    .frame(width: 48, height: 48)
                                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                                            .stroke(Color.white.opacity(0.15), lineWidth: 0.5)
                                    )
                                    .foregroundStyle(.white)
                            }
                            .buttonStyle(AppleSpringCardButtonStyle())
                            .hoverEffect(.highlight)
                        }
                    }
                    .frame(maxWidth: formFactor.isWide ? 440 : .infinity, alignment: .leading)
                    .padding(.horizontal, AuroraMetrics.contentInset)

                    // 3. 选集货架（Apple TV 原生多集交互：支持分季下拉选择、横滑单集点播与全集抽屉）
                    if !episodes.isEmpty || isLoadingEpisodes {
                        VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                            HStack {
                                HStack(spacing: 8) {
                                    Image(systemName: "square.stack.3d.down.right.fill")
                                        .font(.subheadline)
                                        .foregroundStyle(AuroraColor.brand)
                                    Text("player.episodes".localized)
                                        .font(.headline)
                                        .foregroundStyle(AuroraColor.textPrimary)

                                    // Apple TV 标志性季列表（Season Picker）快速下拉切换
                                    if seasons.count > 1 {
                                        Menu {
                                            ForEach(Array(seasons.enumerated()), id: \.offset) { idx, season in
                                                Button {
                                                    selectedSeasonIndex = idx
                                                } label: {
                                                    HStack {
                                                        Text(season.name)
                                                        if selectedSeasonIndex == idx {
                                                            Image(systemName: "checkmark")
                                                        }
                                                    }
                                                }
                                            }
                                        } label: {
                                            HStack(spacing: 4) {
                                                Text(seasons[min(max(0, selectedSeasonIndex), seasons.count - 1)].name)
                                                    .font(.caption.bold())
                                                    .foregroundStyle(AuroraColor.textPrimary)
                                                Image(systemName: "chevron.down")
                                                    .font(.system(size: 9, weight: .bold))
                                                    .foregroundStyle(AuroraColor.brand)
                                            }
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 4)
                                            .background(Color.white.opacity(0.12), in: Capsule())
                                        }
                                    }
                                }

                                Spacer()

                                if isLoadingEpisodes {
                                    ProgressView()
                                        .scaleEffect(0.8)
                                } else if episodes.count > 1 {
                                    Button {
                                        isShowingEpisodePicker = true
                                    } label: {
                                        HStack(spacing: 4) {
                                            Text("共 \(episodes.count) 集")
                                                .font(.caption.bold())
                                            Image(systemName: "chevron.right")
                                                .font(.caption2.bold())
                                        }
                                        .foregroundStyle(AuroraColor.brand)
                                    }
                                }
                            }
                            .padding(.horizontal, AuroraMetrics.contentInset)

                            if isLoadingEpisodes {
                                HStack(spacing: AuroraMetrics.spacingM) {
                                    ForEach(0..<4, id: \.self) { _ in
                                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                                            .fill(AuroraColor.surfaceCard.opacity(0.6))
                                            .frame(width: 120, height: 60)
                                    }
                                }
                                .padding(.horizontal, AuroraMetrics.contentInset)
                            } else if !episodes.isEmpty {
                                ScrollView(.horizontal, showsIndicators: false) {
                                    LazyHStack(spacing: AuroraMetrics.spacingM) {
                                        ForEach(activeSeasonEpisodes) { ep in
                                            let isSelected = (selectedEpisode?.number == ep.number)
                                            let epVideo = videoForEpisode(ep)
                                            let cardW = DeviceLayout.episodeCardWidth(for: formFactor)
                                            let cardH = DeviceLayout.episodeCardHeight(for: formFactor)
                                            Button {
                                                selectedEpisode = ep
                                                onDismiss()
                                                onPlay(epVideo)
                                            } label: {
                                                VStack(alignment: .leading, spacing: 6) {
                                                    // 16:9 单集封面图（Apple TV 标志性选集画幅）
                                                    ZStack(alignment: .bottomLeading) {
                                                        AuroraCoverImage(
                                                            url: video.coverUrl,
                                                            title: "\(video.title) \(ep.label)",
                                                            sourceBaseURL: video.coverBaseURL,
                                                            aspectRatio: 16.0 / 9.0,
                                                            contentMode: .fill,
                                                            cornerRadius: 10
                                                        )
                                                        .frame(width: cardW, height: cardH)
                                                        .overlay(
                                                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                                                .stroke(
                                                                    isSelected ? AuroraColor.brand : AuroraColor.hairline,
                                                                    lineWidth: isSelected ? 2 : 1
                                                                )
                                                        )

                                                        // 左上角单集徽标
                                                        HStack(spacing: 3) {
                                                            Text("EP \(ep.number)")
                                                                .font(.system(size: 10, weight: .black))
                                                                .foregroundStyle(.white)
                                                        }
                                                        .padding(.horizontal, 6)
                                                        .padding(.vertical, 3)
                                                        .background(.black.opacity(0.65), in: Capsule())
                                                        .padding(6)
                                                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)

                                                        // 选中态播放光晕
                                                        if isSelected {
                                                            Image(systemName: "play.circle.fill")
                                                                .font(.title2)
                                                                .foregroundStyle(AuroraColor.brand)
                                                                .background(Circle().fill(Color.white).padding(2))
                                                                .padding(6)
                                                                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                                                        }

                                                        // 底部已播放进度微条（Apple TV 标志性红白进度微条）
                                                        if let record = progressStore.record(for: epVideo), record.progressRatio > 0 {
                                                            VStack {
                                                                Spacer()
                                                                ZStack(alignment: .leading) {
                                                                    Rectangle()
                                                                        .fill(Color.white.opacity(0.3))
                                                                        .frame(height: 2.5)
                                                                    Rectangle()
                                                                        .fill(AuroraColor.accentRed)
                                                                        .frame(width: max(0, cardW * CGFloat(record.progressRatio)), height: 2.5)
                                                                }
                                                            }
                                                            .frame(width: cardW, height: cardH)
                                                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                                                        }
                                                    }

                                                    // 单集信息排版
                                                    VStack(alignment: .leading, spacing: 2) {
                                                        Text(ep.label)
                                                            .font(.subheadline)
                                                            .fontWeight(isSelected ? .bold : .semibold)
                                                            .foregroundStyle(isSelected ? AuroraColor.brand : AuroraColor.textPrimary)
                                                            .lineLimit(1)

                                                        HStack(spacing: 4) {
                                                            Text(video.sourceName)
                                                                .font(.caption2)
                                                                .foregroundStyle(AuroraColor.textTertiary)
                                                                .lineLimit(1)
                                                        }
                                                    }
                                                    .frame(width: cardW, alignment: .leading)
                                                }
                                            }
                                            .buttonStyle(AppleSpringCardButtonStyle())
                                            .hoverEffect(.lift)
                                            .accessibilityLabel("第 \(ep.number) 集，\(isSelected ? "当前选中" : "轻点播放")")
                                        }
                                    }
                                    .padding(.horizontal, AuroraMetrics.contentInset)
                                }
                            }
                        }
                    }

                    // 4. 剧情与简介描述（支持折叠/展开）
                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                        Text("detail.synopsis".localized)
                            .font(.headline)
                            .foregroundStyle(AuroraColor.textPrimary)

                        let description = video.subtitle.isEmpty ? "本片由 \(video.sourceName) 官方源直连分发，支持硬件解码出画与多端投屏。" : video.subtitle
                        Text(description)
                            .font(.subheadline)
                            .foregroundStyle(AuroraColor.textSecondary)
                            .lineLimit(isDescriptionExpanded ? nil : 3)

                        Button {
                            withAnimation(.easeInOut(duration: 0.2)) {
                                isDescriptionExpanded.toggle()
                            }
                        } label: {
                            Text(isDescriptionExpanded ? "收起" : "展开全部")
                                .font(.caption.bold())
                                .foregroundStyle(AuroraColor.brand)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, AuroraMetrics.contentInset)

                    // 4. 媒体规格与画质卡片
                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                        Text("媒体规格")
                            .font(.headline)
                            .foregroundStyle(AuroraColor.textPrimary)
                            .padding(.horizontal, AuroraMetrics.contentInset)

                        HStack(spacing: AuroraMetrics.spacingM) {
                            specItem(title: "视频编码", value: video.playbackUrl.contains(".m3u8") ? "HLS" : "MP4")
                            specItem(title: "来源站点", value: video.sourceName)
                            specItem(title: "投屏支持", value: "AirPlay / DLNA")
                        }
                        .padding(.horizontal, AuroraMetrics.contentInset)
                    }

                    // 5. Apple TV 标志性主创与演职员阵容（Cast & Crew - 支持点击演职员发起全域搜索）
                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                        HStack {
                            Text("主创阵容")
                                .font(.headline)
                                .foregroundStyle(AuroraColor.textPrimary)

                            Spacer()

                            if onSearchKeyword != nil {
                                Text("轻点演职员查看相关作品")
                                    .font(.caption2)
                                    .foregroundStyle(AuroraColor.textTertiary)
                            }
                        }
                        .padding(.horizontal, AuroraMetrics.contentInset)

                        ScrollView(.horizontal, showsIndicators: false) {
                            LazyHStack(spacing: AuroraMetrics.spacingL) {
                                ForEach(parsedCastMembers) { member in
                                    if member.isSearchable && onSearchKeyword != nil {
                                        Button {
                                            let impact = UIImpactFeedbackGenerator(style: .light)
                                            impact.impactOccurred()
                                            onDismiss()
                                            dismiss()
                                            onSearchKeyword?(member.name)
                                        } label: {
                                            castMemberView(name: member.name, role: member.role, icon: member.icon)
                                        }
                                        .buttonStyle(AppleSpringCardButtonStyle())
                                    } else {
                                        castMemberView(name: member.name, role: member.role, icon: member.icon)
                                    }
                                }
                            }
                            .padding(.horizontal, AuroraMetrics.contentInset)
                        }
                    }
                    .id("castCrewSection")

                    // 5. 同源推荐或相关推荐（Apple TV 经典货架）
                    let relatedVideos = videoRepo.feedVideos.filter { $0.id != video.id }.prefix(6)
                    if !relatedVideos.isEmpty {
                        VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                            Text("detail.more_like_this".localized)
                                .font(.headline)
                                .foregroundStyle(AuroraColor.textPrimary)
                                .padding(.horizontal, AuroraMetrics.contentInset)

                            ScrollView(.horizontal, showsIndicators: false) {
                                LazyHStack(spacing: AuroraMetrics.spacingM) {
                                    ForEach(Array(relatedVideos)) { related in
                                        Button {
                                            onDismiss()
                                            onPlay(related)
                                        } label: {
                                            VStack(alignment: .leading, spacing: 6) {
                                                AuroraCoverImage(
                                                    url: related.coverUrl,
                                                    title: related.title,
                                                    sourceBaseURL: related.coverBaseURL,
                                                    aspectRatio: AuroraMetrics.posterAspect,
                                                    contentMode: .fill,
                                                    cornerRadius: 8
                                                )
                                                .frame(width: 110, height: 165)
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                                                        .stroke(AuroraColor.hairline, lineWidth: 1)
                                                )

                                                Text(related.title)
                                                    .font(.caption.bold())
                                                    .foregroundStyle(AuroraColor.textPrimary)
                                                    .lineLimit(1)
                                                    .frame(width: 110, alignment: .leading)
                                            }
                                        }
                                        .buttonStyle(AppleSpringCardButtonStyle())
                                    }
                                }
                                .padding(.horizontal, AuroraMetrics.contentInset)
                            }
                        }
                    }

                    Spacer().frame(height: AuroraMetrics.spacingXXL)
                }
                .onAppear {
                    if CommandLine.arguments.contains("--scroll-cast") {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                            withAnimation(.easeInOut(duration: 0.3)) {
                                proxy.scrollTo("castCrewSection", anchor: .top)
                            }
                        }
                    }
                }
            }
            .background(AuroraColor.canvas.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        let impact = UIImpactFeedbackGenerator(style: .medium)
                        impact.impactOccurred()
                        onDismiss()
                        dismiss()
                    } label: {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(AuroraColor.textPrimary)
                            .frame(width: 38, height: 38)
                            .background(Color.black.opacity(0.4), in: Circle())
                            .glassEffect(.regular.interactive(), in: .circle)
                            .contentShape(Rectangle())
                            .frame(minWidth: 44, minHeight: 44)
                    }
                    .buttonStyle(.plain)
                    .contentShape(Rectangle())
                    .accessibilityLabel("返回")
                }

                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        onDismiss()
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.headline)
                            .foregroundStyle(AuroraColor.textTertiary)
                    }
                }
            }
        }
    }
    .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .task {
            // 启动时静默预解析首个可用直链，实现点击立即起播
            VideoPreloadManager.shared.prewarm(video: video)
            isLoadingEpisodes = true
            let eps = await videoRepo.resolveEpisodes(for: video)
            episodes = eps
            selectedEpisode = eps.first(where: { $0.pageUrl == video.playbackUrl }) ?? eps.first
            isLoadingEpisodes = false
            if let firstEp = eps.first {
                VideoPreloadManager.shared.prewarmNextEpisode(
                    pageUrl: firstEp.pageUrl,
                    title: "\(video.title) \(firstEp.label)",
                    sourceName: video.sourceName
                )
            }
        }
        .sheet(isPresented: $isShowingEpisodePicker) {
            EpisodePickerSheet(
                videoTitle: video.title,
                sourceName: video.sourceName,
                episodes: episodes,
                currentEpisodeNumber: selectedEpisode?.number ?? 1,
                onSelectEpisode: { ep in
                    selectedEpisode = ep
                    isShowingEpisodePicker = false
                    onDismiss()
                    onPlay(videoForEpisode(ep))
                },
                onDismiss: {
                    isShowingEpisodePicker = false
                }
            )
        }
        .alert("未能更新片单", isPresented: Binding(
            get: { watchlistError != nil },
            set: { if !$0 { watchlistError = nil } }
        )) {
            Button("好", role: .cancel) { watchlistError = nil }
        } message: {
            Text(watchlistError ?? "请稍后重试。")
        }
    }

    private func videoForEpisode(_ episode: PlaybackEpisode) -> VideoItem {
        VideoItem(
            id: "\(video.id)-ep-\(episode.number)",
            title: "\(video.title) \(episode.label)",
            subtitle: video.subtitle,
            coverUrl: video.coverUrl,
            playbackUrl: episode.pageUrl,
            duration: video.duration,
            rating: video.rating,
            tags: video.tags,
            category: video.category,
            sourceName: video.sourceName
        )
    }

    private func specItem(title: String, value: String) -> some View {
        VStack(spacing: 3) {
            Text(title)
                .font(.system(size: 10))
                .foregroundStyle(AuroraColor.textTertiary)
            Text(value)
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(AuroraColor.textPrimary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
    }

    private func castMemberView(name: String, role: String, icon: String) -> some View {
        VStack(spacing: 6) {
            ZStack {
                Circle()
                    .fill(AuroraColor.surfaceCard)
                    .frame(width: 56, height: 56)

                Image(systemName: icon)
                    .font(.title3)
                    .foregroundStyle(AuroraColor.brand)
            }
            .overlay(
                Circle().stroke(AuroraColor.hairline, lineWidth: 1)
            )

            Text(name)
                .font(.caption.bold())
                .foregroundStyle(AuroraColor.textPrimary)
                .lineLimit(1)

            Text(role)
                .font(.system(size: 10))
                .foregroundStyle(AuroraColor.textSecondary)
                .lineLimit(1)
        }
        .frame(width: 76)
    }
}
