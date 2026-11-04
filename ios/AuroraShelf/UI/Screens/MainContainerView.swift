import SwiftUI

public struct MainContainerView: View {
    @StateObject private var videoRepo = VideoRepository.shared
    @State private var selectedTab: MainTab = .home
    private var playerCoordinator = PlayerCoordinator.shared
    @State private var showSearchSheet: Bool = false
    @State private var showGameDirectly: Bool = false
    
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
        
        TabView(selection: $selectedTab) {
            NavigationStack {
                HomeScreen(
                    onPlayVideo: { video in
                        coordinator.play(video)
                    },
                    onOpenSearch: {
                        showSearchSheet = true
                    }
                )
            }
            .tabItem {
                Label(MainTab.home.title, systemImage: selectedTab == .home ? MainTab.home.selectedIconName : MainTab.home.iconName)
            }
            .tag(MainTab.home)
            
            NavigationStack {
                ComicsScreen()
            }
            .tabItem {
                Label(MainTab.comics.title, systemImage: selectedTab == .comics ? MainTab.comics.selectedIconName : MainTab.comics.iconName)
            }
            .tag(MainTab.comics)
            
            NavigationStack {
                LiveScreen()
            }
            .tabItem {
                Label(MainTab.live.title, systemImage: selectedTab == .live ? MainTab.live.selectedIconName : MainTab.live.iconName)
            }
            .tag(MainTab.live)
            
            NavigationStack {
                ForumScreen()
            }
            .tabItem {
                Label(MainTab.forum.title, systemImage: selectedTab == .forum ? MainTab.forum.selectedIconName : MainTab.forum.iconName)
            }
            .tag(MainTab.forum)
            
            NavigationStack {
                PersonalScreen()
            }
            .tabItem {
                Label(MainTab.profile.title, systemImage: selectedTab == .profile ? MainTab.profile.selectedIconName : MainTab.profile.iconName)
            }
            .tag(MainTab.profile)
        }
        .tint(AuroraColor.brand)
        .fullScreenCover(item: $coordinator.activeVideo) { video in
            NativePlayerView(video: video) {
                coordinator.dismiss()
            }
        }
        .sheet(isPresented: $showSearchSheet) {
            SearchSheetView(
                onPlayVideo: { video in
                    coordinator.play(video)
                },
                onDismiss: {
                    showSearchSheet = false
                }
            )
        }
        .fullScreenCover(isPresented: $showGameDirectly) {
            GameContainerView {
                showGameDirectly = false
            }
        }
        .onAppear {
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
                Task {
                    for _ in 0..<25 {
                        try? await Task.sleep(nanoseconds: 200_000_000)
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
            if args.contains("--open-game") {
                showGameDirectly = true
            }
            if args.contains("--open-search") {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    showSearchSheet = true
                }
            }
        }
    }
}
