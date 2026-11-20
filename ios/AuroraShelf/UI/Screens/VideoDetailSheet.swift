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
    @State private var detailMetadata: VideoDetailMetadata? = nil
    @State private var watchlistError: String?
    @State private var episodes: [PlaybackEpisode] = []
    @State private var selectedEpisode: PlaybackEpisode?
    @State private var selectedSeasonIndex: Int = 0
    @State private var isLoadingEpisodes: Bool = false
    @State private var isShowingEpisodePicker: Bool = false
    @State private var isScrolled: Bool = false
    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.auroraContainerSize) private var containerSize

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

    private enum CastRole {
        case director
        case actor
        case tag

        var localizedTitle: String {
            switch self {
            case .director: return "detail.role_director".localized
            case .actor: return "detail.role_actor".localized
            case .tag: return "detail.role_tag".localized
            }
        }

        var icon: String {
            switch self {
            case .director: return "megaphone.fill"
            case .actor: return "person.crop.circle.fill"
            case .tag: return "tag.fill"
            }
        }
    }

    private struct CastMember: Identifiable {
        let id = UUID()
        let name: String
        let role: CastRole
        let isSearchable: Bool
    }

    private var parsedCastMembers: [CastMember] {
        var list: [CastMember] = []
        var seen = Set<String>()

        if let meta = detailMetadata {
            for director in meta.directors where !director.isEmpty && seen.insert(director).inserted {
                list.append(CastMember(name: director, role: .director, isSearchable: true))
            }
            for actor in meta.actors where !actor.isEmpty && seen.insert(actor).inserted {
                list.append(CastMember(name: actor, role: .actor, isSearchable: true))
            }
        }

        for tag in video.tags {
            let trimmed = tag.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty, trimmed != video.sourceName, trimmed != video.category else { continue }

            if trimmed.contains("导演") {
                let clean = trimmed
                    .replacingOccurrences(of: "导演:", with: "")
                    .replacingOccurrences(of: "导演：", with: "")
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                for name in clean.split(separator: ",").flatMap({ $0.split(separator: " ") }) {
                    let str = String(name).trimmingCharacters(in: .whitespaces)
                    if !str.isEmpty && !seen.contains(str) {
                        seen.insert(str)
                        list.append(CastMember(name: str, role: .director, isSearchable: true))
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
                        list.append(CastMember(name: str, role: .actor, isSearchable: true))
                    }
                }
            } else if trimmed.count >= 2 && trimmed.count <= 6 && !trimmed.contains("4K") && !trimmed.contains("HDR") && !trimmed.contains("国语") && !trimmed.contains("中字") {
                if !seen.contains(trimmed) {
                    seen.insert(trimmed)
                    list.append(CastMember(name: trimmed, role: .tag, isSearchable: true))
                }
            }
        }

        return list
    }

    private var seasons: [(name: String, episodes: [PlaybackEpisode])] {
        guard !episodes.isEmpty else { return [] }
        if episodes.count <= 12 {
            return [(String(format: "player.all_episodes_count".localized, episodes.count), episodes)]
        }
        var result: [(name: String, episodes: [PlaybackEpisode])] = []
        let chunkSize = 12
        for (i, chunk) in stride(from: 0, to: episodes.count, by: chunkSize).enumerated() {
            let end = min(chunk + chunkSize, episodes.count)
            let seasonEpisodes = Array(episodes[chunk..<end])
            let name = String(format: "detail.season_range".localized, i + 1, chunk + 1, end)
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

    private var formFactor: DeviceFormFactor {
        DeviceLayout.resolve(size: containerSize, horizontalSizeClass: horizontalSizeClass)
    }

    private var backdropHeight: CGFloat {
        switch formFactor {
        case .pad: return 460
        case .duoUnfolded: return 360
        case .duoFolded: return 220
        case .standardPhone: return 280
        }
    }

    public var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: AuroraMetrics.spacingXL) {
                        ZStack(alignment: .bottomLeading) {
                            AuroraCoverImage(
                                url: video.heroCoverUrl ?? video.coverUrl,
                                title: video.title,
                                sourceBaseURL: video.coverBaseURL,
                                aspectRatio: nil,
                                contentMode: .fill,
                                cornerRadius: 0
                            )
                            .frame(maxWidth: .infinity)
                            .frame(height: backdropHeight)
                            .clipped()

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

                            VStack(alignment: .leading, spacing: 6) {
                                Text(video.title)
                                    .font(.title2.bold())
                                    .foregroundStyle(AuroraColor.textPrimary)

                                HStack(spacing: AuroraMetrics.spacingS) {
                                    Text(video.sourceName)
                                        .font(.caption.bold())
                                        .foregroundStyle(AuroraColor.brandLabel)
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 3)
                                        .background(AuroraColor.brandLabel.opacity(0.16), in: Capsule())

                                    if !video.rating.isEmpty {
                                        HStack(spacing: 3) {
                                            Image(systemName: "star.fill")
                                                .font(.caption2)
                                                .foregroundStyle(.yellow)
                                            Text(video.rating)
                                                .font(.caption.bold())
                                                .foregroundStyle(.white)
                                        }
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 3)
                                        .background(.black.opacity(0.65), in: Capsule())
                                    }

                                    if !video.duration.isEmpty {
                                        Text(video.duration)
                                            .font(.caption)
                                            .foregroundStyle(AuroraColor.textSecondary)
                                    }

                                    if let year = detailMetadata?.year, !year.isEmpty {
                                        Text(year)
                                            .font(.caption.bold())
                                            .foregroundStyle(AuroraColor.textPrimary)
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(AuroraColor.surfaceCard, in: Capsule())
                                    }

                                    if let area = detailMetadata?.area, !area.isEmpty {
                                        Text(area)
                                            .font(.caption.bold())
                                            .foregroundStyle(AuroraColor.textPrimary)
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(AuroraColor.surfaceCard, in: Capsule())
                                    }
                                }
                            }
                            .padding(.horizontal, AuroraMetrics.contentInset)
                            .padding(.bottom, AuroraMetrics.spacingM)
                        }

                        HStack(spacing: AuroraMetrics.spacingM) {
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
                                        .lineLimit(1)
                                }
                                .frame(maxWidth: .infinity, minHeight: 48)
                                .background(AuroraColor.brandSolid, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                                .foregroundStyle(Color.white)
                            }
                            .buttonStyle(AppleSpringCardButtonStyle())
                            .hoverEffect(.highlight)

                            let isBookmarked = watchlist.contains(video)
                            Button {
                                AuroraHaptics.impact(.light)
                                do {
                                    try watchlist.setBookmarked(!isBookmarked, for: video)
                                } catch {
                                    watchlistError = error.localizedDescription
                                }
                            } label: {
                                Image(systemName: isBookmarked ? "bookmark.fill" : "bookmark")
                                    .font(.title3)
                                    .frame(width: 48, height: 48)
                                    .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                                            .stroke(AuroraColor.hairline, lineWidth: 1)
                                    )
                                    .foregroundStyle(isBookmarked ? AuroraColor.brandLabel : AuroraColor.textPrimary)
                            }
                            .buttonStyle(AppleSpringCardButtonStyle())
                            .hoverEffect(.highlight)
                            .accessibilityLabel(isBookmarked ? "watchlist.remove".localized : "watchlist.add".localized)

                            let targetVideo = selectedEpisode != nil ? videoForEpisode(selectedEpisode!) : video
                            let isDownloaded = downloadManager.isDownloaded(video: targetVideo, episode: selectedEpisode)
                            let isDownloading = downloadManager.status(for: targetVideo, episode: selectedEpisode)?.isDownloading ?? false
                            Button {
                                AuroraHaptics.impact(.medium)
                                if !isDownloaded && !isDownloading {
                                    downloadManager.startDownload(video: targetVideo, episode: selectedEpisode)
                                }
                            } label: {
                                ZStack {
                                    if isDownloading {
                                        ProgressView()
                                            .progressViewStyle(CircularProgressViewStyle(tint: AuroraColor.brandLabel))
                                            .scaleEffect(0.85)
                                    } else if isDownloaded {
                                        Image(systemName: "checkmark.circle.fill")
                                            .font(.title3)
                                            .foregroundStyle(AuroraColor.brandLabel)
                                    } else {
                                        Image(systemName: "arrow.down.circle")
                                            .font(.title3)
                                            .foregroundStyle(AuroraColor.textPrimary)
                                    }
                                }
                                .frame(width: 48, height: 48)
                                .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .stroke(AuroraColor.hairline, lineWidth: 1)
                                )
                            }
                            .buttonStyle(AppleSpringCardButtonStyle())
                            .hoverEffect(.highlight)
                            .accessibilityLabel(
                                isDownloaded
                                    ? "player.cached".localized
                                    : (isDownloading ? "downloads.filter_downloading".localized : "player.offline_cache".localized)
                            )

                            if let shareURL = URL(string: video.playbackUrl), !video.playbackUrl.isEmpty {
                                ShareLink(item: shareURL) {
                                    Image(systemName: "square.and.arrow.up")
                                        .font(.title3)
                                        .frame(width: 48, height: 48)
                                        .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                .stroke(AuroraColor.hairline, lineWidth: 1)
                                        )
                                        .foregroundStyle(AuroraColor.textPrimary)
                                }
                                .buttonStyle(AppleSpringCardButtonStyle())
                                .hoverEffect(.highlight)
                                .accessibilityLabel("common.share".localized)
                            }
                        }
                        .frame(maxWidth: formFactor.isWide ? 440 : .infinity, alignment: .leading)
                        .padding(.horizontal, AuroraMetrics.contentInset)

                        if !episodes.isEmpty || isLoadingEpisodes {
                            VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                                HStack {
                                    HStack(spacing: 8) {
                                        Image(systemName: "square.stack.3d.down.right.fill")
                                            .font(.subheadline)
                                            .foregroundStyle(AuroraColor.brandLabel)
                                        Text("player.episodes".localized)
                                            .font(.headline)
                                            .foregroundStyle(AuroraColor.textPrimary)
                                            .accessibilityAddTraits(.isHeader)

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
                                                        .font(.caption2.bold())
                                                        .foregroundStyle(AuroraColor.brandLabel)
                                                }
                                                .padding(.horizontal, 10)
                                                .frame(minHeight: AuroraMetrics.minTouchTarget)
                                                .background(AuroraColor.surfaceCard, in: Capsule())
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
                                                Text(String(format: "detail.total_episodes".localized, episodes.count))
                                                    .font(.caption.bold())
                                                Image(systemName: "chevron.right")
                                                    .font(.caption2.bold())
                                            }
                                            .foregroundStyle(AuroraColor.brandLabel)
                                            .frame(minHeight: AuroraMetrics.minTouchTarget)
                                        }
                                    }
                                }
                                .padding(.horizontal, AuroraMetrics.contentInset)

                                if isLoadingEpisodes {
                                    ScrollView(.horizontal, showsIndicators: false) {
                                        HStack(spacing: AuroraMetrics.spacingM) {
                                            ForEach(0..<4, id: \.self) { _ in
                                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                                    .fill(AuroraColor.surfaceCard.opacity(0.6))
                                                    .frame(width: 120, height: 60)
                                            }
                                        }
                                        .padding(.horizontal, AuroraMetrics.contentInset)
                                    }
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
                                                                        isSelected ? AuroraColor.brandLabel : AuroraColor.hairline,
                                                                        lineWidth: isSelected ? 2 : 1
                                                                    )
                                                            )

                                                            HStack(spacing: 3) {
                                                                Text("EP \(ep.number)")
                                                                    .font(.caption2.weight(.black))
                                                                    .foregroundStyle(.white)
                                                            }
                                                            .padding(.horizontal, 6)
                                                            .padding(.vertical, 3)
                                                            .background(.black.opacity(0.65), in: Capsule())
                                                            .padding(6)
                                                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)

                                                            if isSelected {
                                                                Image(systemName: "play.circle.fill")
                                                                    .font(.title2)
                                                                    .foregroundStyle(AuroraColor.brandSolid)
                                                                    .background(Circle().fill(Color.white).padding(2))
                                                                    .padding(6)
                                                                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                                                            }

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

                                                        VStack(alignment: .leading, spacing: 2) {
                                                            Text(ep.label)
                                                                .font(.subheadline.weight(isSelected ? .bold : .semibold))
                                                                .foregroundStyle(isSelected ? AuroraColor.brandLabel : AuroraColor.textPrimary)
                                                                .lineLimit(1)

                                                            Text(video.sourceName)
                                                                .font(.caption2)
                                                                .foregroundStyle(AuroraColor.textTertiary)
                                                                .lineLimit(1)
                                                        }
                                                        .frame(width: cardW, alignment: .leading)
                                                    }
                                                }
                                                .buttonStyle(AppleSpringCardButtonStyle())
                                                .accessibilityLabel(
                                                    String(
                                                        format: "episode.item_a11y".localized,
                                                        ep.number,
                                                        isSelected ? "episode.selected_status".localized : "home.play".localized
                                                    )
                                                )
                                            }
                                        }
                                        .padding(.horizontal, AuroraMetrics.contentInset)
                                    }
                                }
                            }
                        }

                        VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                            Text("detail.intro_title".localized)
                                .font(.headline)
                                .foregroundStyle(AuroraColor.textPrimary)
                                .accessibilityAddTraits(.isHeader)

                            if let metaTags = detailMetadata?.tags, !metaTags.isEmpty {
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 6) {
                                        ForEach(metaTags.prefix(8), id: \.self) { tag in
                                            Text(tag)
                                                .font(.caption2.weight(.medium))
                                                .foregroundStyle(AuroraColor.textSecondary)
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 3)
                                                .background(AuroraColor.surfaceCard, in: Capsule())
                                                .overlay(Capsule().stroke(AuroraColor.hairline, lineWidth: 0.75))
                                        }
                                    }
                                }
                            }

                            let metaDesc = detailMetadata?.synopsis.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                            let description = !metaDesc.isEmpty
                                ? metaDesc
                                : (video.subtitle.isEmpty
                                    ? String(format: "detail.source_direct_desc".localized, video.sourceName)
                                    : video.subtitle)
                            Text(description)
                                .font(.subheadline)
                                .foregroundStyle(AuroraColor.textSecondary)
                                .lineSpacing(3)
                                .lineLimit(isDescriptionExpanded ? nil : 4)

                            if description.count > 75 {
                                Button {
                                    AuroraMotion.perform(AuroraMotion.quick, reduceMotion: reduceMotion) {
                                        isDescriptionExpanded.toggle()
                                    }
                                } label: {
                                    Text(isDescriptionExpanded ? "detail.collapse".localized : "detail.expand_all".localized)
                                        .font(.caption.bold())
                                        .foregroundStyle(AuroraColor.brandLabel)
                                        .frame(minHeight: AuroraMetrics.minTouchTarget)
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, AuroraMetrics.contentInset)

                        VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                            Text("insight.media_specs".localized)
                                .font(.headline)
                                .foregroundStyle(AuroraColor.textPrimary)
                                .accessibilityAddTraits(.isHeader)
                                .padding(.horizontal, AuroraMetrics.contentInset)

                            HStack(spacing: AuroraMetrics.spacingM) {
                                specItem(title: "detail.video_codec".localized, value: video.playbackUrl.contains(".m3u8") ? "HLS" : "MP4")
                                specItem(title: "detail.source_site".localized, value: video.sourceName)
                                specItem(title: "detail.airplay_support".localized, value: "AirPlay")
                            }
                            .padding(.horizontal, AuroraMetrics.contentInset)
                        }

                        if !parsedCastMembers.isEmpty {
                            VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                                HStack {
                                    Text("detail.cast_title".localized)
                                        .font(.headline)
                                        .foregroundStyle(AuroraColor.textPrimary)
                                        .accessibilityAddTraits(.isHeader)

                                    Spacer()

                                    if onSearchKeyword != nil {
                                        Text("detail.cast_tap_hint".localized)
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
                                                    AuroraHaptics.impact(.light)
                                                    onDismiss()
                                                    dismiss()
                                                    onSearchKeyword?(member.name)
                                                } label: {
                                                    castMemberView(name: member.name, role: member.role)
                                                }
                                                .buttonStyle(AppleSpringCardButtonStyle())
                                            } else {
                                                castMemberView(name: member.name, role: member.role)
                                            }
                                        }
                                    }
                                    .padding(.horizontal, AuroraMetrics.contentInset)
                                }
                            }
                            .id("castCrewSection")
                        }

                        let relatedVideos = videoRepo.feedVideos.filter { $0.id != video.id }.prefix(6)
                        if !relatedVideos.isEmpty {
                            VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                                Text("detail.more_like_this".localized)
                                    .font(.headline)
                                    .foregroundStyle(AuroraColor.textPrimary)
                                    .accessibilityAddTraits(.isHeader)
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
                                AuroraMotion.perform(AuroraMotion.quick, reduceMotion: reduceMotion) {
                                    proxy.scrollTo("castCrewSection", anchor: .top)
                                }
                            }
                        }
                    }
                }
                .auroraImmersiveHeroScrollEdge(
                    isScrolled: $isScrolled,
                    hasHeroContent: true,
                    reduceMotion: reduceMotion
                )
                .ignoresSafeArea(edges: .top)
                .background(AuroraColor.canvas.ignoresSafeArea())
                .navigationBarTitleDisplayMode(.inline)
                .auroraImmersiveHeroNavigationBar(isScrolled: isScrolled, hasHeroContent: true)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("common.close".localized, systemImage: "xmark") {
                            AuroraHaptics.impact(.light)
                            onDismiss()
                            dismiss()
                        }
                    }
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .task {
            VideoPreloadManager.shared.prewarm(video: video)
            isLoadingEpisodes = true
            async let epsTask = videoRepo.resolveEpisodes(for: video)
            async let metaTask = videoRepo.fetchDetailMetadata(for: video)
            let (eps, fetchedMeta) = await (epsTask, metaTask)
            episodes = eps
            detailMetadata = fetchedMeta
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
        .alert("detail.watchlist_update_failed".localized, isPresented: Binding(
            get: { watchlistError != nil },
            set: { if !$0 { watchlistError = nil } }
        )) {
            Button("common.ok".localized, role: .cancel) { watchlistError = nil }
        } message: {
            Text(watchlistError ?? "hero.try_again_later".localized)
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
                .font(.caption2)
                .foregroundStyle(AuroraColor.textTertiary)
            Text(value)
                .font(.caption.bold())
                .foregroundStyle(AuroraColor.textPrimary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
    }

    private func castMemberView(name: String, role: CastRole) -> some View {
        VStack(spacing: 6) {
            ZStack {
                Circle()
                    .fill(AuroraColor.surfaceCard)
                    .frame(width: 56, height: 56)

                Image(systemName: role.icon)
                    .font(.title3)
                    .foregroundStyle(AuroraColor.brandLabel)
            }
            .overlay(
                Circle().stroke(AuroraColor.hairline, lineWidth: 1)
            )

            Text(name)
                .font(.caption.bold())
                .foregroundStyle(AuroraColor.textPrimary)
                .lineLimit(1)

            Text(role.localizedTitle)
                .font(.caption2)
                .foregroundStyle(AuroraColor.textSecondary)
                .lineLimit(1)
        }
        .frame(width: 76)
    }
}
