import SwiftUI

public struct MainContainerView: View {
    @StateObject private var videoRepo = VideoRepository.shared
    @ObservedObject private var locManager = LocalizationManager.shared
    @State private var selectedTab: MainTab = .home
    @State private var playerCoordinator = PlayerCoordinator.shared
    @State private var showSearchSheet: Bool = false
    @State private var searchInitialQuery: String = ""
    @State private var autoPlayTask: Task<Void, Never>?
    
    public init() {
        let args = ProcessInfo.processInfo.arguments
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
            default:
                _selectedTab = State(initialValue: .home)
            }
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
                TabView(selection: $selectedTab) {
            NavigationStack {
                HomeScreen(
                    onPlayVideo: { video in
                        coordinator.play(video)
                    },
                    onOpenSearch: {
                        searchInitialQuery = ""
                        showSearchSheet = true
                    },
                    onOpenAccount: {
                        selectedTab = .profile
                    },
                    onOpenSearchWithKeyword: { keyword in
                        searchInitialQuery = keyword
                        showSearchSheet = true
                    }
                )
            }
            .tabItem {
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
            .tag(MainTab.home)
            
            NavigationStack {
                ComicsScreen(onOpenSearch: {
                    showSearchSheet = true
                })
            }
            .tabItem {
                Label(MainTab.comics.title, systemImage: selectedTab == .comics ? MainTab.comics.selectedIconName : MainTab.comics.iconName)
            }
            .tag(MainTab.comics)
            
            NavigationStack {
                LiveScreen(onOpenSearch: {
                    showSearchSheet = true
                })
            }
            .tabItem {
                Label(MainTab.live.title, systemImage: selectedTab == .live ? MainTab.live.selectedIconName : MainTab.live.iconName)
            }
            .tag(MainTab.live)
            
            NavigationStack {
                ForumScreen(onOpenSearch: {
                    showSearchSheet = true
                })
            }
            .tabItem {
                Label(MainTab.forum.title, systemImage: selectedTab == .forum ? MainTab.forum.selectedIconName : MainTab.forum.iconName)
            }
            .tag(MainTab.forum)
            
            NavigationStack {
                PersonalScreen(onPlayVideo: { video in
                    coordinator.play(video)
                })
            }
            .tabItem {
                Label(MainTab.profile.title, systemImage: selectedTab == .profile ? MainTab.profile.selectedIconName : MainTab.profile.iconName)
            }
            .tag(MainTab.profile)
        }
        // iPadOS presents the same top-level navigation as a sidebar while
        // retaining the floating tab bar on iPhone.
        .tabViewStyle(.sidebarAdaptable)
        .tabViewSidebarHeader {
            HStack {
                NetgreyLogoView()
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
        .tint(AuroraColor.brand)
        .environment(\.locale, locManager.activeLocale)
        .sheet(isPresented: $showSearchSheet) {
            SearchSheetView(
                initialQuery: searchInitialQuery,
                onPlayVideo: { video in
                    showSearchSheet = false
                    searchInitialQuery = ""
                    selectedTab = .home
                    coordinator.play(video)
                },
                onDismiss: {
                    showSearchSheet = false
                    searchInitialQuery = ""
                }
            )
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
            }
            
            if args.contains("--auto-play") {
                selectedTab = .home
                autoPlayTask?.cancel()
                autoPlayTask = Task {
                    for _ in 0..<25 {
                        try? await Task.sleep(nanoseconds: 200_000_000)
                        guard !Task.isCancelled else { return }
                        if let realVideo = videoRepo.billboardVideo ?? videoRepo.feedVideos.first {
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
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    showSearchSheet = true
                }
            }
        }
        .onDisappear {
            autoPlayTask?.cancel()
            autoPlayTask = nil
        }
            },
            destinationContent: { route in
                immersiveDestinationView(for: route, coordinator: coordinator)
                    .tint(AuroraColor.brand)
                    .environment(\.locale, locManager.activeLocale)
            }
        )
        .ignoresSafeArea()
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
        case .comic(let comic):
            ComicReaderView(comic: comic) {
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

private final class ImmersiveNavigationController: UINavigationController {
    override var childForStatusBarHidden: UIViewController? { topViewController }
    override var childForStatusBarStyle: UIViewController? { topViewController }
    override var childForHomeIndicatorAutoHidden: UIViewController? { topViewController }
}

private struct ImmersiveNavigationHost<Route: Hashable, RootContent: View, DestinationContent: View>: UIViewControllerRepresentable {
    @Binding var activeRoute: Route?
    let showsNavigationBar: (Route) -> Bool
    @ViewBuilder let rootContent: () -> RootContent
    @ViewBuilder let destinationContent: (Route) -> DestinationContent

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
                        nav.setNavigationBarHidden(!showBar, animated: true)
                    }
                }
            } else {
                let destVC = UIHostingController(rootView: destinationContent(route))
                destVC.view.backgroundColor = .black
                coord.destinationController = destVC
                coord.displayedRoute = route
                let showBar = showsNavigationBar(route)
                nav.setNavigationBarHidden(!showBar, animated: false)
                nav.pushViewController(destVC, animated: true)
            }
        } else if nav.viewControllers.count > 1 {
            coord.displayedRoute = nil
            coord.destinationController = nil
            nav.popToRootViewController(animated: true)
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
        var displayedRoute: Route?

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
            if viewController === rootController {
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
