import SwiftUI

public struct PersonalScreen: View {
    @ObservedObject public var videoRepo = VideoRepository.shared
    @ObservedObject public var comicRepo = ComicRepository.shared
    @ObservedObject public var liveRepo = LiveRepository.shared
    @ObservedObject private var watchlist = WatchlistStore.shared
    @ObservedObject private var locManager = LocalizationManager.shared
    @ObservedObject private var downloadManager = OfflineDownloadManager.shared
    @ObservedObject private var comicDownloadManager = ComicDownloadManager.shared
    @ObservedObject private var account = AccountService.shared
    @AppStorage("aurora_theme_mode") private var themeModeRaw: String = AuroraThemeMode.system.rawValue
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    public let onPlayVideo: (VideoItem) -> Void

    @State private var showVideoSourcePicker: Bool = false
    @State private var showComicSourcePicker: Bool = false
    @State private var showLanguagePicker: Bool = false
    @State private var showAccount: Bool = false
    @State private var showOpenSourceNotices: Bool = false
    @State private var pushWatchlistFromCommand: Bool = false
    @State private var coordinator = PlayerCoordinator.shared

    public init(onPlayVideo: @escaping (VideoItem) -> Void) {
        self.onPlayVideo = onPlayVideo
    }

    private var activeThemeMode: AuroraThemeMode {
        AuroraThemeMode(rawValue: themeModeRaw) ?? .system
    }

    private var profileDisplayName: String {
        if let profile = account.profile {
            let trimmed = profile.displayName.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty { return trimmed }
            return profile.email.components(separatedBy: "@").first ?? profile.email
        }
        return "account.guest".localized
    }

    private var profileInitial: String {
        let name = profileDisplayName
        guard let first = name.first else { return "G" }
        return String(first).uppercased()
    }

    private var isProActive: Bool {
        guard let status = account.profile?.subscriptionStatus.lowercased() else { return false }
        return status == "pro" || status == "active" || status == "vip"
    }

    public var body: some View {
        ZStack {
            AuroraColor.canvas.ignoresSafeArea()

            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: AuroraMetrics.spacingXL) {
                    // 1. 用户头像与会员卡（动态绑定 AccountService，点击打开账号管理）
                    Button {
                        showAccount = true
                    } label: {
                        HStack(spacing: AuroraMetrics.spacingL) {
                            ZStack {
                                Circle()
                                    .fill(AuroraColor.brandSolid)
                                    .frame(width: 56, height: 56)
                                Text(profileInitial)
                                    .font(.system(.title2, design: .rounded).weight(.black))
                                    .foregroundStyle(.white)
                            }

                            VStack(alignment: .leading, spacing: 4) {
                                HStack(spacing: 6) {
                                    Text(profileDisplayName)
                                        .font(.title2)
                                        .fontWeight(.bold)
                                        .foregroundStyle(AuroraColor.textPrimary)

                                    if isProActive {
                                        Text("PRO")
                                            .font(.caption2)
                                            .fontWeight(.black)
                                            .foregroundStyle(.black)
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(Capsule().fill(Color.yellow))
                                    } else {
                                        Text(account.profile != nil ? "account.free_plan".localized : "account.unlogged".localized)
                                            .font(.caption2)
                                            .fontWeight(.bold)
                                            .foregroundStyle(AuroraColor.textSecondary)
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(Capsule().fill(AuroraColor.surfaceHigh))
                                    }
                                }

                                Text(
                                    "settings.adaptation_summary".localized(
                                        VideoSourceCatalog.supportedSources.count,
                                        VideoSourceCatalog.allSources.count,
                                        ComicSourceCatalog.supportedSources.count,
                                        ComicSourceCatalog.allSources.count
                                    )
                                )
                                .font(.footnote)
                                .foregroundStyle(AuroraColor.textSecondary)
                            }

                            Spacer()

                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundStyle(AuroraColor.textTertiary)
                        }
                        .padding(AuroraMetrics.spacingL)
                        .background(
                            RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                                .fill(AuroraColor.surface)
                                .overlay(
                                    RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                                        .stroke(AuroraColor.hairline, lineWidth: 1)
                                )
                        )
                    }
                    .buttonStyle(AppleSpringCardButtonStyle())
                    .padding(.horizontal, AuroraMetrics.contentInset)
                    .padding(.top, AuroraMetrics.spacingS)

                    // 资料库核心：接着播放与最近历史（对齐 Apple TV 资料库）
                    UpNextShelf(onPlayVideo: onPlayVideo)

                    // 2. 娱乐板块（战术 RPG 离线单机游戏接入）
                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                        Text("settings.game".localized)
                            .font(.headline)
                            .foregroundStyle(AuroraColor.textSecondary)
                            .padding(.horizontal, AuroraMetrics.spacingXS)
                            .accessibilityAddTraits(.isHeader)

                        Button {
                            PlayerCoordinator.shared.openGame()
                        } label: {
                            HStack(spacing: AuroraMetrics.spacingL) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: AuroraMetrics.controlRadius, style: .continuous)
                                        .fill(AuroraColor.brand.opacity(0.16))
                                        .frame(width: 44, height: 44)
                                    Image(systemName: "gamecontroller.fill")
                                        .font(.title3)
                                        .foregroundStyle(AuroraColor.brandLabel)
                                }

                                VStack(alignment: .leading, spacing: 4) {
                                    Text("settings.game_title".localized)
                                        .font(.headline)
                                        .foregroundStyle(AuroraColor.textPrimary)
                                        .lineLimit(1)
                                        .minimumScaleFactor(0.85)
                                    HStack(spacing: 6) {
                                        Text(shortAppVersion)
                                            .font(.caption2.weight(.bold))
                                            .foregroundStyle(AuroraColor.brandLabel)
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(AuroraColor.brandLabel.opacity(0.14), in: Capsule())
                                        Text("settings.game_desc".localized)
                                            .font(.caption)
                                            .foregroundStyle(AuroraColor.textSecondary)
                                            .fixedSize(horizontal: false, vertical: true)
                                    }
                                }

                                Spacer()

                                HStack(spacing: 4) {
                                    Text("settings.launch".localized)
                                        .font(.subheadline)
                                        .fontWeight(.bold)
                                        .foregroundStyle(AuroraColor.brandLabel)
                                    Image(systemName: "play.circle.fill")
                                        .font(.body)
                                        .foregroundStyle(AuroraColor.brandLabel)
                                }
                            }
                            .padding(AuroraMetrics.spacingM)
                            .background(
                                RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                                    .fill(AuroraColor.surface)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                                            .stroke(AuroraColor.brand.opacity(0.35), lineWidth: 1)
                                    )
                            )
                        }
                        .auroraZoomSource("game:hub")
                        .buttonStyle(AppleSpringCardButtonStyle())
                    }
                    .padding(.horizontal, AuroraMetrics.contentInset)

                    // 3. 源管理与设置列表
                    VStack(spacing: 0) {
                        Button {
                            showAccount = true
                        } label: {
                            settingRow(
                                icon: "person.crop.circle",
                                title: "account.title".localized,
                                value: account.profile?.email ?? "account.login_or_register".localized,
                                isNavigable: true
                            )
                        }
                        .buttonStyle(.plain)
                        .hoverEffect(.highlight)

                        Divider().overlay(AuroraColor.hairline)

                        Button {
                            showVideoSourcePicker = true
                        } label: {
                            settingRow(
                                icon: "server.rack",
                                title: "settings.video_source".localized,
                                value: "\(videoRepo.currentSource.name) · \(VideoSourceCatalog.supportedSources.count)/\(VideoSourceCatalog.allSources.count)",
                                isNavigable: true
                            )
                        }
                        .buttonStyle(.plain)
                        .hoverEffect(.highlight)

                        Divider().overlay(AuroraColor.hairline)

                        Button {
                            showComicSourcePicker = true
                        } label: {
                            settingRow(
                                icon: "books.vertical",
                                title: "settings.comic_source".localized,
                                value: "\(comicRepo.currentSource.name) · \(ComicSourceCatalog.supportedSources.count)/\(ComicSourceCatalog.allSources.count)",
                                isNavigable: true
                            )
                        }
                        .buttonStyle(.plain)
                        .hoverEffect(.highlight)

                        Divider().overlay(AuroraColor.hairline)

                        NavigationLink {
                            WatchlistScreen(onPlayVideo: onPlayVideo)
                        } label: {
                            settingRow(
                                icon: "bookmark",
                                title: "watchlist.title".localized,
                                value: "watchlist.count".localized(watchlist.items.count),
                                isNavigable: true
                            )
                        }
                        .buttonStyle(.plain)
                        .hoverEffect(.highlight)

                        Divider().overlay(AuroraColor.hairline)

                        // 原生 AirPlay 单次轻点直达（覆盖 AirPlayRoutePicker 热区，拔除多余二次中转弹窗）
                        ZStack {
                            settingRow(
                                icon: "tv",
                                title: "settings.airplay_title".localized,
                                value: "settings.airplay_select".localized,
                                isNavigable: true
                            )
                            .allowsHitTesting(false)

                            AirPlayRoutePicker()
                                .frame(maxWidth: .infinity, minHeight: AuroraMetrics.minTouchTarget)
                                .opacity(0.02)
                                .accessibilityLabel("settings.airplay_title".localized)
                        }
                        .hoverEffect(.highlight)

                        Divider().overlay(AuroraColor.hairline)

                        // 原生行内锚定菜单（在 iPad 上直接于行右侧展开，不再浮现无锚点操作弹窗）
                        Menu {
                            ForEach(LiveSourceCatalog.allSources) { source in
                                Button {
                                    liveRepo.selectSource(source.id)
                                } label: {
                                    HStack {
                                        Text(LiveSourceCatalog.name(for: source.id))
                                        if liveRepo.selectedSourceID == source.id {
                                            Image(systemName: "checkmark")
                                        }
                                    }
                                }
                            }
                        } label: {
                            settingRow(
                                icon: "antenna.radiowaves.left.and.right",
                                title: "settings.live_source".localized,
                                value: "\(LiveSourceCatalog.name(for: liveRepo.selectedSourceID)) · \(LiveSourceCatalog.allSources.count)",
                                isNavigable: true,
                                trailingSymbol: "chevron.up.chevron.down"
                            )
                        }
                        .buttonStyle(.plain)
                        .hoverEffect(.highlight)

                        Divider().overlay(AuroraColor.hairline)

                        Menu {
                            ForEach(AuroraThemeMode.allCases) { mode in
                                Button {
                                    themeModeRaw = mode.rawValue
                                } label: {
                                    HStack {
                                        Text(mode.displayName)
                                        if activeThemeMode == mode {
                                            Image(systemName: "checkmark")
                                        }
                                    }
                                }
                            }
                        } label: {
                            settingRow(
                                icon: "circle.lefthalf.filled",
                                title: "settings.appearance".localized,
                                value: activeThemeMode.displayName,
                                isNavigable: true,
                                trailingSymbol: "chevron.up.chevron.down"
                            )
                        }
                        .buttonStyle(.plain)
                        .hoverEffect(.highlight)

                        Divider().overlay(AuroraColor.hairline)

                        Button {
                            showLanguagePicker = true
                        } label: {
                            settingRow(
                                icon: "globe",
                                title: "settings.language".localized,
                                value: locManager.selectedLanguage.displayName,
                                isNavigable: true
                            )
                        }
                        .buttonStyle(.plain)
                        .hoverEffect(.highlight)

                        Divider().overlay(AuroraColor.hairline)

                        NavigationLink {
                            OfflineDownloadsScreen(onPlayVideo: onPlayVideo)
                        } label: {
                            settingRow(
                                icon: "arrow.down.circle",
                                title: "settings.offline_download".localized,
                                value: offlineDownloadSummary,
                                isNavigable: true
                            )
                        }
                        .buttonStyle(.plain)
                        .hoverEffect(.highlight)

                        Divider().overlay(AuroraColor.hairline)

                        Button {
                            showOpenSourceNotices = true
                        } label: {
                            settingRow(
                                icon: "doc.text",
                                title: "settings.open_source".localized,
                                value: "GPLv3 / Apache-2.0 / MIT",
                                isNavigable: true
                            )
                        }
                        .buttonStyle(.plain)
                        .hoverEffect(.highlight)

                        Divider().overlay(AuroraColor.hairline)

                        settingRow(icon: "info.circle", title: "settings.version".localized, value: appVersion)
                    }
                    .background(
                        RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                            .fill(AuroraColor.surface)
                            .overlay(
                                RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                                    .stroke(AuroraColor.hairline, lineWidth: 1)
                            )
                    )
                    .padding(.horizontal, AuroraMetrics.contentInset)

                    // 4. Apple TV 风格本地存储容量多段彩色仪表盘
                    StorageUsageBarView()
                        .padding(.horizontal, AuroraMetrics.contentInset)

                    // 品牌脚注
                    VStack(spacing: 8) {
                        NetgreyMarkView(width: 22, height: 32)

                        Text("Netgrey")
                            .font(.subheadline.weight(.heavy))
                            .foregroundStyle(AuroraColor.brandLabel)
                            .tracking(-0.4)

                        Text(platformFootnote)
                            .font(.caption2)
                            .foregroundStyle(AuroraColor.textSecondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, AuroraMetrics.spacingM)
                }
                .safeAreaPadding(.bottom, 96)
            }
        }
        .navigationTitle("library.title".localized)
        .navigationDestination(isPresented: $pushWatchlistFromCommand) {
            WatchlistScreen(onPlayVideo: onPlayVideo)
        }
        .onChange(of: coordinator.openWatchlistTrigger) { _, _ in
            pushWatchlistFromCommand = true
        }
        .onAppear {
            let args = CommandLine.arguments
            if args.contains("--game") || args.contains("--slots") || args.contains("--game-slots") || args.contains("--game-hub") || args.contains("--game-battle") || args.contains("--battle") {
                PlayerCoordinator.shared.openGame()
            }
        }
        .sheet(isPresented: $showLanguagePicker) {
            LanguagePickerSheet()
        }
        .sheet(isPresented: $showAccount) {
            AccountView()
        }
        .sheet(isPresented: $showOpenSourceNotices) {
            OpenSourceNoticesSheet()
        }
        .sheet(isPresented: $showVideoSourcePicker) {
            VideoSourcePickerSheet(selectedSource: videoRepo.currentSource) { src in
                videoRepo.switchSource(to: src)
                showVideoSourcePicker = false
            }
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $showComicSourcePicker) {
            ComicSourcePickerSheet(selectedSource: comicRepo.currentSource) { src in
                comicRepo.switchSource(to: src)
                showComicSourcePicker = false
            }
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
    }

    private var offlineDownloadSummary: String {
        let activeVideo = downloadManager.activeItems.count
        let activeComic = comicDownloadManager.activeChapters.count
        let totalActive = activeVideo + activeComic
        if totalActive > 0 {
            let activeProgresses = downloadManager.activeItems.map(\.status.progressValue)
                + comicDownloadManager.activeChapters.map(\.status.progressValue)
            let avg = activeProgresses.isEmpty ? 0.0 : activeProgresses.reduce(0, +) / Double(activeProgresses.count)
            return "settings.downloading_summary".localized(totalActive, Int(avg * 100))
        }
        let videoCount = downloadManager.completedItems.count
        let comicCount = comicDownloadManager.completedChapters.count
        if videoCount == 0 && comicCount == 0 {
            return "settings.zero_items".localized
        }
        return "settings.cached_summary".localized(videoCount, comicCount)
    }

    private var shortAppVersion: String {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0.0"
        return "v\(version)"
    }

    private var platformFootnote: String {
        let platformName = UIDevice.current.userInterfaceIdiom == .pad ? "iPadOS" : "iOS"
        let major = ProcessInfo.processInfo.operatingSystemVersion.majorVersion
        return "Netgrey · \(platformName) \(major)"
    }

    private var appVersion: String {
        let unknown = "common.unknown".localized
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? unknown
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? unknown
        return "\(version) (Build \(build))"
    }

    @ViewBuilder
    private func settingRow(
        icon: String,
        title: String,
        value: String,
        isNavigable: Bool = false,
        trailingSymbol: String = "chevron.right"
    ) -> some View {
        if dynamicTypeSize.isAccessibilitySize {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: AuroraMetrics.spacingS) {
                    Image(systemName: icon)
                        .font(.body)
                        .foregroundStyle(AuroraColor.brandLabel)
                    Text(title)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(AuroraColor.textPrimary)
                    Spacer()
                    if isNavigable {
                        Image(systemName: trailingSymbol)
                            .font(.caption)
                            .foregroundStyle(AuroraColor.textTertiary)
                    }
                }
                Text(value)
                    .font(.subheadline)
                    .foregroundStyle(AuroraColor.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, AuroraMetrics.spacingL)
            .padding(.vertical, AuroraMetrics.spacingM)
            .frame(maxWidth: .infinity, minHeight: AuroraMetrics.minTouchTarget, alignment: .leading)
            .contentShape(Rectangle())
        } else {
            HStack(spacing: AuroraMetrics.spacingM) {
                Image(systemName: icon)
                    .font(.body)
                    .foregroundStyle(AuroraColor.brandLabel)
                    .frame(width: 24)

                Text(title)
                    .font(.body)
                    .foregroundStyle(AuroraColor.textPrimary)

                Spacer()

                Text(value)
                    .font(.subheadline)
                    .foregroundStyle(AuroraColor.textSecondary)
                    .multilineTextAlignment(.trailing)
                    .lineLimit(2)

                if isNavigable {
                    Image(systemName: trailingSymbol)
                        .font(.caption2)
                        .foregroundStyle(AuroraColor.textTertiary)
                }
            }
            .padding(.horizontal, AuroraMetrics.spacingL)
            .padding(.vertical, AuroraMetrics.spacingM)
            .frame(minHeight: AuroraMetrics.minTouchTarget)
            .contentShape(Rectangle())
        }
    }
}

struct OpenSourceNoticesSheet: View {
    @ObservedObject private var locManager = LocalizationManager.shared
    @Environment(\.dismiss) private var dismiss

    private var items: [(name: String, license: String, summary: String)] {
        [
            ("PipePipeExtractor", "GNU GPLv3", "settings.oss_pipepipe".localized),
            ("Spine WebGL Runtime", "Spine Runtimes License", "settings.oss_spine".localized),
            ("Comic Protocol Reference", "Apache-2.0", "settings.oss_comic".localized),
            ("Apple Native Frameworks", "Apple SDK", "SwiftUI, AVKit, AVFoundation, WebKit, Network, CryptoKit, AppIntents")
        ]
    }

    var body: some View {
        NavigationStack {
            List {
                Section("settings.oss_section".localized) {
                    ForEach(items, id: \.name) { item in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(item.name)
                                    .font(.headline)
                                    .foregroundStyle(AuroraColor.textPrimary)
                                Spacer()
                                Text(item.license)
                                    .font(.caption.bold())
                                    .foregroundStyle(AuroraColor.brandLabel)
                            }
                            Text(item.summary)
                                .font(.caption)
                                .foregroundStyle(AuroraColor.textSecondary)
                        }
                        .padding(.vertical, 4)
                    }
                }
                Section {
                    if let url = URL(string: "https://github.com/RandyVae/AuroraShelf/blob/main/THIRD_PARTY_NOTICES.md") {
                        Link("settings.oss_full_link".localized, destination: url)
                            .foregroundStyle(AuroraColor.brandLabel)
                    }
                }
            }
            .navigationTitle("settings.open_source".localized)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.done".localized) { dismiss() }
                        .frame(minWidth: AuroraMetrics.minTouchTarget, minHeight: AuroraMetrics.minTouchTarget)
                }
            }
        }
    }
}

// 漫画源切换面板（统一升级为原生 NavigationStack + List(.insetGrouped)，与视频源选择器 1:1 对齐）
struct ComicSourcePickerSheet: View {
    let selectedSource: ComicSourceDefinition
    let onSelect: (ComicSourceDefinition) -> Void
    @ObservedObject private var locManager = LocalizationManager.shared
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section(
                    "settings.comic_source_header".localized(
                        ComicSourceCatalog.supportedSources.count,
                        ComicSourceCatalog.allSources.count
                    )
                ) {
                    ForEach(ComicSourceCatalog.allSources) { src in
                        let isSupported = ComicSourceCatalog.isSupported(src)
                        let isSelected = src.id == selectedSource.id
                        Button {
                            guard isSupported else { return }
                            AuroraHaptics.medium()
                            onSelect(src)
                        } label: {
                            HStack(spacing: AuroraMetrics.spacingM) {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(src.name)
                                        .font(.body.weight(.semibold))
                                        .foregroundStyle(isSelected ? AuroraColor.brandLabel : AuroraColor.textPrimary)
                                    Text(isSupported ? src.description : "home.parser_pending".localized)
                                        .font(.caption)
                                        .foregroundStyle(AuroraColor.textSecondary)
                                }
                                Spacer()
                                if isSelected {
                                    Image(systemName: "checkmark")
                                        .font(.body.weight(.bold))
                                        .foregroundStyle(AuroraColor.brandLabel)
                                } else if !isSupported {
                                    Text("settings.not_available".localized)
                                        .font(.caption2.weight(.semibold))
                                        .foregroundStyle(AuroraColor.textSecondary)
                                }
                            }
                            .frame(minHeight: AuroraMetrics.minTouchTarget)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .hoverEffect(.highlight)
                        .disabled(!isSupported)
                        .listRowBackground(AuroraColor.surface)
                    }
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(AuroraColor.canvas.ignoresSafeArea())
            .navigationTitle("comics.select_source".localized)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.done".localized) { dismiss() }
                        .foregroundStyle(AuroraColor.brandLabel)
                        .frame(minWidth: AuroraMetrics.minTouchTarget, minHeight: AuroraMetrics.minTouchTarget)
                }
            }
        }
    }
}

// 语言切换选择面板
struct LanguagePickerSheet: View {
    @ObservedObject private var locManager = LocalizationManager.shared
    @Environment(\.dismiss) private var dismiss

    private func iconData(for lang: AppLanguage) -> (icon: String, gradient: [Color]) {
        switch lang {
        case .system:
            return ("gearshape.fill", [Color(rgb: 0x455A64), Color(rgb: 0x607D8B)])
        case .zhHans:
            return ("character.bubble.fill", [Color(rgb: 0xB71C1C), Color(rgb: 0xE53935)])
        case .zhHant:
            return ("text.bubble.fill", [Color(rgb: 0x0D47A1), Color(rgb: 0x1E88E5)])
        case .en:
            return ("globe.americas.fill", [Color(rgb: 0x1B5E20), Color(rgb: 0x43A047)])
        case .ja:
            return ("sun.max.fill", [Color(rgb: 0xC2185B), Color(rgb: 0xE91E63)])
        case .ko:
            return ("moon.stars.fill", [Color(rgb: 0x4A148C), Color(rgb: 0x8E24AA)])
        case .es:
            return ("globe.europe.africa.fill", [Color(rgb: 0xE65100), Color(rgb: 0xFB8C00)])
        case .fr:
            return ("flag.fill", [Color(rgb: 0x1A237E), Color(rgb: 0x3949AB)])
        case .de:
            return ("building.columns.fill", [Color(rgb: 0x3E2723), Color(rgb: 0x6D4C41)])
        case .vi:
            return ("star.fill", [Color(rgb: 0xC62828), Color(rgb: 0xF44336)])
        case .th:
            return ("sparkles", [Color(rgb: 0x006064), Color(rgb: 0x00ACC1)])
        case .id:
            return ("leaf.fill", [Color(rgb: 0x2E7D32), Color(rgb: 0x66BB6A)])
        case .ms:
            return ("shield.fill", [Color(rgb: 0x283593), Color(rgb: 0x5C6BC0)])
        }
    }

    var body: some View {
        NavigationStack {
            List {
                ForEach(AppLanguage.allCases) { lang in
                    Button {
                        locManager.setLanguage(lang)
                        dismiss()
                    } label: {
                        let meta = iconData(for: lang)
                        HStack(spacing: 12) {
                            ZStack {
                                Circle()
                                    .fill(LinearGradient(colors: meta.gradient, startPoint: .topLeading, endPoint: .bottomTrailing))
                                    .frame(width: 32, height: 32)
                                Circle()
                                    .fill(
                                        LinearGradient(
                                            stops: [
                                                .init(color: .white.opacity(0.35), location: 0.0),
                                                .init(color: .clear, location: 0.5)
                                            ],
                                            startPoint: .top,
                                            endPoint: .bottom
                                        )
                                    )
                                    .frame(width: 32, height: 32)
                                Image(systemName: meta.icon)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(.white)
                            }
                            .overlay(Circle().stroke(Color.white.opacity(0.2), lineWidth: 1))
                            .shadow(color: meta.gradient.first?.opacity(0.35) ?? .clear, radius: 4, y: 1)

                            VStack(alignment: .leading, spacing: 3) {
                                Text(lang.displayName)
                                    .font(.body)
                                    .foregroundStyle(AuroraColor.textPrimary)
                                if lang == .system {
                                    Text("settings.follow_system_lang_desc".localized)
                                        .font(.caption)
                                        .foregroundStyle(AuroraColor.textSecondary)
                                }
                            }
                            Spacer()
                            if locManager.selectedLanguage == lang {
                                Image(systemName: "checkmark")
                                    .font(.body.weight(.bold))
                                    .foregroundStyle(AuroraColor.brandLabel)
                            }
                        }
                        .frame(minHeight: AuroraMetrics.minTouchTarget)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .hoverEffect(.highlight)
                    .listRowBackground(AuroraColor.surface)
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(AuroraColor.canvas.ignoresSafeArea())
            .navigationTitle("settings.language_title".localized)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.done".localized) {
                        dismiss()
                    }
                    .foregroundStyle(AuroraColor.brandLabel)
                    .frame(minWidth: AuroraMetrics.minTouchTarget, minHeight: AuroraMetrics.minTouchTarget)
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}
