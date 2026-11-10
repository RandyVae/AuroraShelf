import SwiftUI

public struct PersonalScreen: View {
    @ObservedObject public var videoRepo = VideoRepository.shared
    @ObservedObject public var comicRepo = ComicRepository.shared
    @ObservedObject public var liveRepo = LiveRepository.shared
    @ObservedObject private var watchlist = WatchlistStore.shared
    @ObservedObject private var locManager = LocalizationManager.shared
    @ObservedObject private var downloadManager = OfflineDownloadManager.shared
    public let onPlayVideo: (VideoItem) -> Void
    
    @State private var showGame: Bool = false
    @State private var activePlaybackVideo: VideoItem? = nil
    @State private var showVideoSourcePicker: Bool = false
    @State private var showComicSourcePicker: Bool = false
    @State private var showLiveSourceDialog: Bool = false
    @State private var showAirPlayPicker: Bool = false
    @State private var showLanguagePicker: Bool = false
    
    public init(onPlayVideo: @escaping (VideoItem) -> Void) {
        self.onPlayVideo = onPlayVideo
    }
    
    public var body: some View {
        ZStack {
            AuroraColor.canvas.ignoresSafeArea()
            
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: AuroraMetrics.spacingXL) {
                    // 1. 用户头像与会员卡
                    HStack(spacing: AuroraMetrics.spacingL) {
                        ZStack {
                            Circle()
                                .fill(AuroraColor.brand)
                                .frame(width: 56, height: 56)
                            // 头像单字母（§3.2 白名单允许 .black）
                            Text("R")
                                .font(.system(size: 24, weight: .black, design: .rounded))
                                .foregroundStyle(.white)
                        }
                        
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 6) {
                                Text("Randy")
                                    .font(.title2)
                                    .fontWeight(.bold)
                                    .foregroundStyle(AuroraColor.textPrimary)
                                
                                // PRO 徽章（§3.2 白名单允许 .black）
                                Text("PRO")
                                    .font(.caption2)
                                    .fontWeight(.black)
                                    .foregroundStyle(.black)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Capsule().fill(Color.yellow))
                            }
                            
                            Text("iOS 视频适配 \(VideoSourceCatalog.supportedSources.count)/\(VideoSourceCatalog.allSources.count) · 漫画适配 \(ComicSourceCatalog.supportedSources.count)/\(ComicSourceCatalog.allSources.count)")
                                .font(.footnote)
                                .foregroundStyle(AuroraColor.textSecondary)
                        }
                        
                        Spacer()
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
                    .padding(.horizontal, AuroraMetrics.contentInset)
                    .padding(.top, AuroraMetrics.spacingS)
                    
                    // 资料库核心：接着播放与最近历史（对齐 Apple TV 资料库）
                    UpNextShelf(onPlayVideo: { video in
                        activePlaybackVideo = video
                        onPlayVideo(video)
                    })

                    // 2. 娱乐板块（战术 RPG 离线单机游戏接入）
                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                        Text("settings.game".localized)
                            .font(.headline)
                            .foregroundStyle(AuroraColor.textSecondary)
                            .padding(.horizontal, AuroraMetrics.spacingXS)
                        
                        Button {
                            showGame = true
                        } label: {
                            HStack(spacing: AuroraMetrics.spacingL) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: AuroraMetrics.controlRadius, style: .continuous)
                                        .fill(AuroraColor.brand.opacity(0.18))
                                        .frame(width: 44, height: 44)
                                    Image(systemName: "gamecontroller.fill")
                                        .font(.title3)
                                        .foregroundStyle(AuroraColor.brand)
                                }
                                
                                VStack(alignment: .leading, spacing: 3) {
                                    HStack(spacing: 6) {
                                        Text("settings.game_title".localized)
                                            .font(.headline)
                                            .foregroundStyle(AuroraColor.textPrimary)
                                        Text("v2.16.9")
                                            .font(.caption2)
                                            .fontWeight(.bold)
                                            .foregroundStyle(AuroraColor.brand)
                                    }
                                    Text("settings.game_desc".localized)
                                        .font(.caption)
                                        .foregroundStyle(AuroraColor.textSecondary)
                                }
                                
                                Spacer()
                                
                                HStack(spacing: 4) {
                                    Text("settings.launch".localized)
                                        .font(.subheadline)
                                        .fontWeight(.bold)
                                        .foregroundStyle(AuroraColor.brand)
                                    Image(systemName: "play.circle.fill")
                                        .font(.body)
                                        .foregroundStyle(AuroraColor.brand)
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
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, AuroraMetrics.contentInset)
                    
                    // 3. 源管理与设置列表
                    VStack(spacing: 1) {
                        Button {
                            showVideoSourcePicker = true
                        } label: {
                            settingRow(
                                icon: "server.rack",
                                title: "settings.video_source".localized,
                                value: "\(videoRepo.currentSource.name) · \(VideoSourceCatalog.supportedSources.count)/\(VideoSourceCatalog.allSources.count)"
                            )
                        }
                        .buttonStyle(.plain)
                        
                        Button {
                            showComicSourcePicker = true
                        } label: {
                            settingRow(
                                icon: "books.vertical",
                                title: "settings.comic_source".localized,
                                value: "\(comicRepo.currentSource.name) · \(ComicSourceCatalog.supportedSources.count)/\(ComicSourceCatalog.allSources.count)"
                            )
                        }
                        .buttonStyle(.plain)
                        
                        NavigationLink {
                            WatchlistScreen(onPlayVideo: { video in
                                activePlaybackVideo = video
                                onPlayVideo(video)
                            })
                        } label: {
                            settingRow(
                                icon: "bookmark",
                                title: "watchlist.title".localized,
                                value: "watchlist.count".localized(watchlist.items.count),
                                isNavigable: true
                            )
                        }
                        .buttonStyle(.plain)

                        Button {
                            showAirPlayPicker = true
                        } label: {
                            settingRow(icon: "tv", title: "settings.airplay_title".localized, value: "settings.airplay_select".localized, isNavigable: true)
                        }
                        .buttonStyle(.plain)

                        Button {
                            showLiveSourceDialog = true
                        } label: {
                            settingRow(
                                icon: "antenna.radiowaves.left.and.right",
                                title: "settings.live_source".localized,
                                value: "\(LiveSourceCatalog.name(for: liveRepo.selectedSourceID)) · \(LiveSourceCatalog.allSources.count)",
                                isNavigable: true
                            )
                        }
                        .buttonStyle(.plain)

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

                        NavigationLink {
                            OfflineDownloadsScreen(onPlayVideo: { video in
                                activePlaybackVideo = video
                                onPlayVideo(video)
                            })
                        } label: {
                            settingRow(
                                icon: "arrow.down.circle",
                                title: "settings.offline_download".localized,
                                value: downloadManager.completedItems.isEmpty ? "0 部" : "\(downloadManager.completedItems.count) 部 · \(downloadManager.formattedTotalSpace())",
                                isNavigable: true
                            )
                        }
                        .buttonStyle(.plain)
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
                            .font(.system(size: 15, weight: .heavy))
                            .foregroundStyle(Color(red: 1.0, green: 0.31, blue: 0.48))
                            .tracking(-0.5)

                        Text("Netgrey · iOS 27")
                            .font(.caption2)
                            .foregroundStyle(AuroraColor.textSecondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, AuroraMetrics.spacingM)

                    Spacer().frame(height: AuroraMetrics.spacingXXL)
                }
            }
        }
        .navigationTitle("library.title".localized)
        .navigationDestination(isPresented: $showGame) {
            GameContainerView {
                showGame = false
            }
            .toolbar(.hidden, for: .tabBar)
            .navigationBarBackButtonHidden(true)
        }
        .navigationDestination(item: $activePlaybackVideo) { video in
            VideoDetailPlaybackScreen(video: video) {
                activePlaybackVideo = nil
            }
            .toolbar(.hidden, for: .tabBar)
            .navigationBarBackButtonHidden(true)
        }
        .onAppear {
            let args = CommandLine.arguments
            if args.contains("--game") || args.contains("--slots") || args.contains("--game-slots") || args.contains("--game-hub") || args.contains("--game-battle") || args.contains("--battle") {
                showGame = true
            }
        }
        .sheet(isPresented: $showLanguagePicker) {
            LanguagePickerSheet()
        }
        .sheet(isPresented: $showVideoSourcePicker) {
            VideoSourcePickerSheet(selectedSource: videoRepo.currentSource) { src in
                videoRepo.switchSource(to: src)
                showVideoSourcePicker = false
            }
        }
        .sheet(isPresented: $showComicSourcePicker) {
            ComicSourcePickerSheet(selectedSource: comicRepo.currentSource) { src in
                comicRepo.switchSource(to: src)
                showComicSourcePicker = false
            }
        }
        .sheet(isPresented: $showAirPlayPicker) {
            NavigationStack {
                VStack(spacing: AuroraMetrics.spacingL) {
                    Text("选择 AirPlay 设备")
                        .font(.headline)
                    AirPlayRoutePicker()
                        .frame(width: 60, height: 60)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(AuroraColor.canvas)
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("完成") { showAirPlayPicker = false }
                    }
                }
            }
            .presentationDetents([.medium])
            .presentationDragIndicator(.visible)
        }
        .confirmationDialog("选择直播来源", isPresented: $showLiveSourceDialog, titleVisibility: .visible) {
            ForEach(LiveSourceCatalog.allSources) { source in
                Button(source.name) {
                    liveRepo.selectSource(source.id)
                }
            }
            Button("取消", role: .cancel) {}
        }
    }

    private var appVersion: String {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "未知"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "未知"
        return "\(version) (Build \(build))"
    }

    private func settingRow(icon: String, title: String, value: String, isNavigable: Bool = false) -> some View {
        HStack(spacing: AuroraMetrics.spacingM) {
            Image(systemName: icon)
                .font(.body)
                .foregroundStyle(AuroraColor.brand)
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
                Image(systemName: "chevron.right")
                    .font(.caption2)
                    .foregroundStyle(AuroraColor.textTertiary)
            }
        }
        .padding(.horizontal, AuroraMetrics.spacingL)
        .padding(.vertical, AuroraMetrics.spacingM)
        .frame(minHeight: AuroraMetrics.minTouchTarget)
    }
}

// 漫画源切换面板
struct ComicSourcePickerSheet: View {
    let selectedSource: ComicSourceDefinition
    let onSelect: (ComicSourceDefinition) -> Void
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        ZStack {
            AuroraColor.canvas.ignoresSafeArea()
            
            VStack(alignment: .leading, spacing: AuroraMetrics.spacingL) {
                HStack {
                    Text("iOS 漫画源 · \(ComicSourceCatalog.supportedSources.count)/\(ComicSourceCatalog.allSources.count) 个已配置适配")
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
                    VStack(spacing: AuroraMetrics.spacingM) {
                        ForEach(ComicSourceCatalog.allSources) { src in
                            let isSelected = src.id == selectedSource.id
                            Button(action: { onSelect(src) }) {
                                HStack(spacing: AuroraMetrics.spacingM) {
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(src.name)
                                            .font(.body)
                                            .fontWeight(.semibold)
                                            .foregroundStyle(isSelected ? AuroraColor.brand : AuroraColor.textPrimary)
                                        Text(ComicSourceCatalog.isSupported(src) ? src.description : "iOS 解析器尚未接入")
                                            .font(.caption)
                                            .foregroundStyle(AuroraColor.textSecondary)
                                    }
                                    Spacer()
                                    if isSelected {
                                        Image(systemName: "checkmark")
                                            .font(.headline)
                                            .fontWeight(.bold)
                                            .foregroundStyle(AuroraColor.brand)
                                    } else if !ComicSourceCatalog.isSupported(src) {
                                        Text("暂不可用")
                                            .font(.caption2.weight(.semibold))
                                            .foregroundStyle(AuroraColor.textSecondary)
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
                            .disabled(!ComicSourceCatalog.isSupported(src))
                        }
                    }
                    .padding(.horizontal, AuroraMetrics.contentInset)
                    .padding(.bottom, AuroraMetrics.spacingXXL)
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
                            // 原生液态玻璃语言图标（Apple HIG 设计规范）
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
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundStyle(.white)
                            }
                            .overlay(Circle().stroke(Color.white.opacity(0.2), lineWidth: 1))
                            .shadow(color: meta.gradient.first?.opacity(0.35) ?? .clear, radius: 4, y: 1)

                            VStack(alignment: .leading, spacing: 3) {
                                Text(lang.displayName)
                                    .font(.body)
                                    .foregroundStyle(AuroraColor.textPrimary)
                                if lang == .system {
                                    Text("跟随当前系统语言设置")
                                        .font(.caption)
                                        .foregroundStyle(AuroraColor.textSecondary)
                                }
                            }
                            Spacer()
                            if locManager.selectedLanguage == lang {
                                Image(systemName: "checkmark")
                                    .font(.body.weight(.bold))
                                    .foregroundStyle(AuroraColor.brand)
                            }
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
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
                    .foregroundStyle(AuroraColor.brand)
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}
