import SwiftUI

public struct MainContainerView: View {
    @StateObject private var videoRepo = VideoRepository.shared
    @State private var selectedTab: MainTab = .home
    @State private var playerCoordinator = PlayerCoordinator.shared
    @State private var showSearchSheet: Bool = false
    @State private var showGameDirectly: Bool = false
    
    public init() {}
    
    public var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack {
                HomeScreen(
                    onPlayVideo: { video in
                        playerCoordinator.play(video)
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
        .fullScreenCover(item: $playerCoordinator.activeVideo) { video in
            NativePlayerView(video: video) {
                playerCoordinator.dismiss()
            }
        }
        .sheet(isPresented: $showSearchSheet) {
            SearchSheetView(
                onPlayVideo: { video in
                    playerCoordinator.play(video)
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
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) {
                    if let realVideo = videoRepo.billboardVideo ?? videoRepo.feedVideos.first {
                        playerCoordinator.play(realVideo)
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
                showSearchSheet = true
            }
        }
    }
}
