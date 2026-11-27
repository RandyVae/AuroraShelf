import SwiftUI
import UIKit

public struct MainContainerView: View {
    @ObservedObject private var locManager = LocalizationManager.shared
    @AppStorage("aurora_theme_mode") private var themeModeRaw: String = AuroraThemeMode.system.rawValue
    @Environment(\.appearsActive) private var appearsActive
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var selectedTab: MainTab = .home
    @State private var playerCoordinator = PlayerCoordinator.shared
    @State private var showSearchSheet: Bool = false
    @State private var searchInitialQuery: String = ""
    @State private var searchInitialScope: SearchSheetView.SearchScope = .video
    @State private var measuredWindowSize: CGSize = DeviceLayout.currentWindowSize
    @State private var autoPlayTask: Task<Void, Never>?

    public init() {
        let args = ProcessInfo.processInfo.arguments
        if let langIdx = args.firstIndex(of: "--lang"), langIdx + 1 < args.count,
           let parsedLang = AppLanguage(rawValue: args[langIdx + 1]) {
            LocalizationManager.shared.setLanguage(parsedLang)
        }
        if let themeIdx = args.firstIndex(of: "--theme"), themeIdx + 1 < args.count,
           let parsedTheme = AuroraThemeMode(rawValue: args[themeIdx + 1]) {
            UserDefaults.standard.set(parsedTheme.rawValue, forKey: "aurora_theme_mode")
        }
        if let idx = args.firstIndex(of: "--tab"), idx + 1 < args.count {
            let tabArg = args[idx + 1].lowercased()
            switch tabArg {
            case "comics", "comic", "manhua":
                _selectedTab = State(initialValue: .comics)
            case "live", "zhibo":
                _selectedTab = State(initialValue: .live)
            case "forum", "luntan":
                _selectedTab = State(initialValue: .forum)
            case "personal", "my", "wode", "profile":
                _selectedTab = State(initialValue: .profile)
            case "search":
                if UIDevice.current.userInterfaceIdiom == .pad {
                    _selectedTab = State(initialValue: .search)
                } else {
                    _selectedTab = State(initialValue: .home)
                    _showSearchSheet = State(initialValue: true)
                }
            default:
                _selectedTab = State(initialValue: .home)
            }
        }
    }

    private var activePreferredColorScheme: ColorScheme? {
        AuroraThemeMode(rawValue: themeModeRaw)?.preferredColorScheme
    }

    private var activeTelemetrySection: (section: String, subSection: String) {
        if showSearchSheet || selectedTab == .search {
            return ("search", "search")
        }
        if let route = playerCoordinator.activeRoute {
            switch route {
            case .video: return ("video", "video_play")
            case .comic: return ("comics", "comic_read")
            case .live: return ("live", "live_watch")
            case .forumPost: return ("forum", "forum_read")
            case .game: return ("game", "game_play")
            }
        }
        switch selectedTab {
        case .home: return ("video", "video_browse")
        case .comics: return ("comics", "comic_browse")
        case .live: return ("live", "live_browse")
        case .forum: return ("forum", "forum_browse")
        case .profile: return ("library", "library")
        case .search: return ("search", "search")
        }
    }

    public var body: some View {
        @Bindable var coordinator = playerCoordinator

        ImmersiveNavigationHost(
            activeRoute: $coordinator.activeRoute,
            showsNavigationBar: { route in
                if case .forumPost = route { return true }
                return false
            },
            rootContent: {
                mainTabHierarchy(coordinator: coordinator)
            },
            destinationContent: { route in
                immersiveDestinationView(for: route, coordinator: coordinator)
                    .id(route)
                    .tint(AuroraColor.brand)
                    .environment(\.locale, locManager.activeLocale)
                    .environment(\.auroraContainerSize, measuredWindowSize)
            }
        )
        .onGeometryChange(for: CGSize.self) { proxy in
            proxy.size
        } action: { _, newSize in
            if newSize.width > 0 && newSize.height > 0 {
                measuredWindowSize = newSize
            }
        }
        .onChange(of: coordinator.requestedTab) { _, target in
            guard let target else { return }
            selectedTab = target
            coordinator.requestedTab = nil
        }
        .onChange(of: coordinator.openSearchTrigger) { _, _ in
            let incomingQuery = coordinator.requestedSearchQuery ?? ""
            if let rawScope = coordinator.requestedSearchScope,
               let parsedScope = SearchSheetView.SearchScope(rawValue: rawScope) {
                searchInitialScope = parsedScope
            } else {
                switch selectedTab {
                case .comics: searchInitialScope = .comic
                case .live: searchInitialScope = .live
                case .forum: searchInitialScope = .forum
                default: searchInitialScope = .video
                }
            }
            if incomingQuery.isEmpty, horizontalSizeClass == .regular {
                selectedTab = .search
            } else {
                searchInitialQuery = incomingQuery
                showSearchSheet = true
            }
            coordinator.requestedSearchQuery = nil
            coordinator.requestedSearchScope = nil
        }
        .onChange(of: coordinator.refreshActiveTabTrigger) { _, _ in
            switch selectedTab {
            case .home:
                VideoRepository.shared.loadFeed(for: VideoRepository.shared.currentSource, category: VideoRepository.shared.currentCategory)
            case .comics:
                ComicRepository.shared.loadComics(for: ComicRepository.shared.currentSource)
            case .live:
                LiveRepository.shared.loadStreams()
            case .forum:
                ForumRepository.shared.loadPosts()
            case .profile, .search:
                break
            }
        }
        .preferredColorScheme(activePreferredColorScheme)
        .ignoresSafeArea()
        .onAppear {
            let sec = activeTelemetrySection
            TelemetryManager.shared.setSection(sec.section, subSection: sec.subSection)
        }
        .onChange(of: "\(activeTelemetrySection.section):\(activeTelemetrySection.subSection)") { _, _ in
            let sec = activeTelemetrySection
            TelemetryManager.shared.setSection(sec.section, subSection: sec.subSection)
        }
    }

    private func triggerSearch(scope: SearchSheetView.SearchScope) {
        searchInitialQuery = ""
        searchInitialScope = scope
        if horizontalSizeClass == .regular {
            selectedTab = .search
        } else {
            showSearchSheet = true
        }
    }

    @ViewBuilder
    private func mainTabHierarchy(coordinator: PlayerCoordinator) -> some View {
        let baseTabs = TabView(selection: $selectedTab) {
            Tab(value: MainTab.home) {
                NavigationStack {
                    HomeScreen(
                        onPlayVideo: { video in
                            coordinator.play(video)
                        },
                        onOpenSearch: {
                            triggerSearch(scope: .video)
                        },
                        onOpenAccount: {
                            selectedTab = .profile
                        },
                        onOpenSearchWithKeyword: { keyword in
                            searchInitialQuery = keyword
                            searchInitialScope = .video
                            showSearchSheet = true
                        }
                    )
                }
            } label: {
                Label {
                    Text(MainTab.home.title)
                } icon: {
                    #if canImport(UIKit)
                    Image(uiImage: NetgreyTabIconRenderer.renderImage(isSelected: selectedTab == .home))
                    #else
                    Image(systemName: selectedTab == .home ? MainTab.home.selectedIconName : MainTab.home.iconName)
                    #endif
                }
            }

            Tab(
                MainTab.comics.title,
                systemImage: selectedTab == .comics ? MainTab.comics.selectedIconName : MainTab.comics.iconName,
                value: MainTab.comics
            ) {
                NavigationStack {
                    ComicsScreen(onOpenSearch: {
                        triggerSearch(scope: .comic)
                    })
                }
            }

            Tab(
                MainTab.live.title,
                systemImage: selectedTab == .live ? MainTab.live.selectedIconName : MainTab.live.iconName,
                value: MainTab.live
            ) {
                NavigationStack {
                    LiveScreen(onOpenSearch: {
                        triggerSearch(scope: .live)
                    })
                }
            }

            Tab(
                MainTab.forum.title,
                systemImage: selectedTab == .forum ? MainTab.forum.selectedIconName : MainTab.forum.iconName,
                value: MainTab.forum
            ) {
                NavigationStack {
                    ForumScreen(onOpenSearch: {
                        triggerSearch(scope: .forum)
                    })
                }
            }

            Tab(
                MainTab.profile.title,
                systemImage: selectedTab == .profile ? MainTab.profile.selectedIconName : MainTab.profile.iconName,
                value: MainTab.profile
            ) {
                NavigationStack {
                    PersonalScreen(onPlayVideo: { video in
                        coordinator.play(video)
                    })
                }
            }

            if horizontalSizeClass == .regular {
                Tab(value: MainTab.search, role: .search) {
                    SearchSheetView(
                        initialQuery: searchInitialQuery,
                        initialScope: searchInitialScope,
                        isEmbeddedInTab: true,
                        onPlayVideo: { video in
                            coordinator.play(video)
                        },
                        onSelectComic: { comic in
                            coordinator.openComic(comic)
                        },
                        onSelectLive: { stream in
                            let related = LiveRepository.shared.liveStreams.filter { $0.id != stream.id }
                            coordinator.openLive(stream, relatedStreams: related)
                        },
                        onSelectForumPost: { post in
                            coordinator.openForumPost(post)
                        },
                        onDismiss: {
                            selectedTab = .home
                        }
                    )
                }
            }
        }
        .tabViewStyle(.sidebarAdaptable)
        .tabBarMinimizeBehavior(.onScrollDown)
        .tabViewSidebarHeader {
            HStack {
                NetgreyLogoView()
                    .opacity(appearsActive ? 1.0 : 0.62)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
        .tint(AuroraColor.brand)
        .environment(\.locale, locManager.activeLocale)
        .environment(\.auroraContainerSize, measuredWindowSize)
        .sheet(isPresented: $showSearchSheet) {
            SearchSheetView(
                initialQuery: searchInitialQuery,
                initialScope: searchInitialScope,
                isEmbeddedInTab: false,
                onPlayVideo: { video in
                    showSearchSheet = false
                    searchInitialQuery = ""
                    selectedTab = .home
                    coordinator.play(video)
                },
                onSelectComic: { comic in
                    showSearchSheet = false
                    searchInitialQuery = ""
                    selectedTab = .comics
                    coordinator.openComic(comic)
                },
                onSelectLive: { stream in
                    showSearchSheet = false
                    searchInitialQuery = ""
                    selectedTab = .live
                    let related = LiveRepository.shared.liveStreams.filter { $0.id != stream.id }
                    coordinator.openLive(stream, relatedStreams: related)
                },
                onSelectForumPost: { post in
                    showSearchSheet = false
                    searchInitialQuery = ""
                    selectedTab = .forum
                    coordinator.openForumPost(post)
                },
                onDismiss: {
                    showSearchSheet = false
                    searchInitialQuery = ""
                }
            )
            .preferredColorScheme(activePreferredColorScheme)
        }
        .onAppear {
            PictureInPictureManager.shared.onRestoreUserInterface = { completion in
                if let pipVideo = PictureInPictureManager.shared.activePiPVideo {
                    selectedTab = .home
                    coordinator.play(pipVideo)
                }
                completion(true)
            }
            let args = CommandLine.arguments
            if args.contains("--tab-comics") {
                selectedTab = .comics
            } else if args.contains("--tab-live") {
                selectedTab = .live
            } else if args.contains("--tab-forum") {
                selectedTab = .forum
            } else if args.contains("--tab-profile") {
                selectedTab = .profile
            } else if args.contains("--tab-search") {
                triggerSearch(scope: .video)
            }

            if args.contains("--auto-play") {
                selectedTab = .home
                autoPlayTask?.cancel()
                autoPlayTask = Task {
                    for _ in 0..<25 {
                        try? await Task.sleep(nanoseconds: 200_000_000)
                        guard !Task.isCancelled else { return }
                        if let realVideo = VideoRepository.shared.billboardVideo ?? VideoRepository.shared.feedVideos.first {
                            await MainActor.run {
                                coordinator.play(realVideo)
                            }
                            break
                        }
                    }
                }
            }
            if args.contains("--auto-live") {
                selectedTab = .live
            }
            if args.contains("--open-game") || args.contains("--game") || args.contains("--slots") || args.contains("--game-slots") || args.contains("--game-hub") {
                selectedTab = .profile
            }
            if args.contains("--open-search") {
                triggerSearch(scope: .video)
            }
        }
        .onDisappear {
            autoPlayTask?.cancel()
            autoPlayTask = nil
        }

        baseTabs
    }

    @ViewBuilder
    private func immersiveDestinationView(
        for route: AppImmersiveRoute,
        coordinator: PlayerCoordinator
    ) -> some View {
        switch route {
        case .video(let video):
            VideoDetailPlaybackScreen(
                video: video,
                onDismiss: {
                    coordinator.dismiss()
                },
                onOpenSearchKeyword: { keyword in
                    coordinator.dismiss()
                    searchInitialQuery = keyword
                    searchInitialScope = .video
                    showSearchSheet = true
                }
            )
            .toolbar(.hidden, for: .tabBar)
            .toolbar(.hidden, for: .navigationBar)
            .navigationBarHidden(true)
            .navigationBarBackButtonHidden(true)
        case .live(let stream, let relatedStreams):
            LiveDetailView(
                stream: stream,
                relatedStreams: relatedStreams,
                onSelectStream: { next in
                    let updatedRelated = LiveRepository.shared.liveStreams.filter { $0.id != next.id }
                    coordinator.openLive(next, relatedStreams: updatedRelated.isEmpty ? relatedStreams : updatedRelated)
                },
                onDismiss: {
                    coordinator.dismiss()
                }
            )
            .id(stream.id)
            .toolbar(.hidden, for: .tabBar)
            .toolbar(.hidden, for: .navigationBar)
            .navigationBarBackButtonHidden(true)
        case .comic(let comic, let initialChapterIndex, let restoreProgress):
            ComicReaderView(comic: comic, initialChapterIndex: initialChapterIndex, restoreProgress: restoreProgress) {
                coordinator.dismiss()
            }
            .toolbar(.hidden, for: .tabBar)
            .toolbar(.hidden, for: .navigationBar)
            .navigationBarBackButtonHidden(true)
        case .forumPost(let post):
            ForumPostDetailView(post: post) {
                coordinator.dismiss()
            }
            .toolbar(.hidden, for: .tabBar)
        case .game:
            GameContainerView {
                coordinator.dismiss()
            }
            .toolbar(.hidden, for: .tabBar)
            .toolbar(.hidden, for: .navigationBar)
            .navigationBarBackButtonHidden(true)
        }
    }
}

final class ImmersiveNavigationController: UINavigationController {
    override var childForStatusBarHidden: UIViewController? { topViewController }
    override var childForStatusBarStyle: UIViewController? { topViewController }
    override var childForHomeIndicatorAutoHidden: UIViewController? { topViewController }
    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        PlayerOrientationCoordinator.shared.supportedInterfaceOrientations(
            for: UIDevice.current.userInterfaceIdiom
        )
    }

    override func viewWillTransition(
        to size: CGSize,
        with coordinator: UIViewControllerTransitionCoordinator
    ) {
        super.viewWillTransition(to: size, with: coordinator)
        coordinator.animate(alongsideTransition: { _ in
            let bounds = CGRect(origin: .zero, size: size)
            self.view.frame = bounds
            for vc in self.viewControllers {
                vc.view.frame = bounds
                vc.view.setNeedsLayout()
                vc.view.layoutIfNeeded()
            }
        }, completion: { _ in
            let bounds = CGRect(origin: .zero, size: self.view.bounds.size)
            for vc in self.viewControllers {
                vc.view.frame = bounds
                vc.view.setNeedsLayout()
                vc.view.layoutIfNeeded()
            }
        })
    }
}

private struct ImmersiveNavigationHost<RootContent: View, DestinationContent: View>: UIViewControllerRepresentable {
    @Binding var activeRoute: AppImmersiveRoute?
    let showsNavigationBar: (AppImmersiveRoute) -> Bool
    @ViewBuilder let rootContent: () -> RootContent
    @ViewBuilder let destinationContent: (AppImmersiveRoute) -> DestinationContent

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIViewController(context: Context) -> UINavigationController {
        let rootVC = UIHostingController(rootView: rootContent())
        rootVC.view.backgroundColor = .clear
        let nav = ImmersiveNavigationController(rootViewController: rootVC)
        nav.setNavigationBarHidden(true, animated: false)
        nav.delegate = context.coordinator
        context.coordinator.rootController = rootVC
        context.coordinator.navigationController = nav
        return nav
    }

    func updateUIViewController(_ nav: UINavigationController, context: Context) {
        let coord = context.coordinator
        coord.parent = self
        coord.rootController?.rootView = rootContent()

        if let route = activeRoute {
            if let destVC = coord.destinationController, nav.viewControllers.contains(destVC) {
                if coord.displayedRoute != route {
                    coord.displayedRoute = route
                    destVC.rootView = destinationContent(route)
                    let showBar = showsNavigationBar(route)
                    if nav.isNavigationBarHidden == showBar {
                        nav.setNavigationBarHidden(!showBar, animated: !UIAccessibility.isReduceMotionEnabled)
                    }
                }
            } else {
                let destVC = UIHostingController(rootView: destinationContent(route))
                destVC.view.backgroundColor = UIColor(AuroraColor.canvas)
                destVC.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
                if !UIAccessibility.isReduceMotionEnabled,
                   ZoomTransitionSourceRegistry.shared.sourceView(for: route) != nil {
                    destVC.preferredTransition = .zoom { _ in
                        ZoomTransitionSourceRegistry.shared.sourceView(for: route)
                    }
                }
                coord.destinationController = destVC
                coord.displayedRoute = route
                let showBar = showsNavigationBar(route)
                nav.setNavigationBarHidden(!showBar, animated: false)
                nav.pushViewController(destVC, animated: !UIAccessibility.isReduceMotionEnabled)
            }
        } else if nav.viewControllers.count > 1 {
            coord.displayedRoute = nil
            coord.destinationController = nil
            nav.popToRootViewController(animated: !UIAccessibility.isReduceMotionEnabled)
        } else {
            coord.displayedRoute = nil
            coord.destinationController = nil
        }
    }

    final class Coordinator: NSObject, UINavigationControllerDelegate {
        var parent: ImmersiveNavigationHost
        weak var navigationController: UINavigationController?
        weak var rootController: UIHostingController<RootContent>?
        weak var destinationController: UIHostingController<DestinationContent>?
        var displayedRoute: AppImmersiveRoute?

        init(parent: ImmersiveNavigationHost) {
            self.parent = parent
        }

        func navigationController(
            _ navigationController: UINavigationController,
            willShow viewController: UIViewController,
            animated: Bool
        ) {
            if viewController === rootController {
                if !navigationController.isNavigationBarHidden {
                    navigationController.setNavigationBarHidden(true, animated: animated)
                }
            } else if let route = parent.activeRoute {
                let showBar = parent.showsNavigationBar(route)
                if navigationController.isNavigationBarHidden == showBar {
                    navigationController.setNavigationBarHidden(!showBar, animated: animated)
                }
            }
        }

        func navigationController(
            _ navigationController: UINavigationController,
            didShow viewController: UIViewController,
            animated: Bool
        ) {
            if viewController === rootController, displayedRoute != nil {
                displayedRoute = nil
                destinationController = nil
                if parent.activeRoute != nil {
                    DispatchQueue.main.async {
                        self.parent.activeRoute = nil
                    }
                }
            }
        }
    }
}
