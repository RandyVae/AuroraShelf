import SwiftUI

public struct MainContainerView: View {
    @StateObject private var videoRepo = VideoRepository.shared
    @State private var selectedTab: MainTab = .home
    @State private var activeVideoForPlayback: VideoItem? = nil
    @State private var showSearchSheet: Bool = false
    @State private var showGameDirectly: Bool = false
    
    public init() {}
    
    public var body: some View {
        TabView(selection: $selectedTab) {
            HomeScreen(
                onPlayVideo: { video in
                    activeVideoForPlayback = video
                },
                onOpenSearch: {
                    showSearchSheet = true
                }
            )
            .tabItem {
                Label(MainTab.home.title, systemImage: selectedTab == .home ? MainTab.home.selectedIconName : MainTab.home.iconName)
            }
            .tag(MainTab.home)
            
            ComicsScreen()
                .tabItem {
                    Label(MainTab.comics.title, systemImage: selectedTab == .comics ? MainTab.comics.selectedIconName : MainTab.comics.iconName)
                }
                .tag(MainTab.comics)
            
            LiveScreen()
                .tabItem {
                    Label(MainTab.live.title, systemImage: selectedTab == .live ? MainTab.live.selectedIconName : MainTab.live.iconName)
                }
                .tag(MainTab.live)
            
            ForumScreen()
                .tabItem {
                    Label(MainTab.forum.title, systemImage: selectedTab == .forum ? MainTab.forum.selectedIconName : MainTab.forum.iconName)
                }
                .tag(MainTab.forum)
            
            PersonalScreen()
                .tabItem {
                    Label(MainTab.profile.title, systemImage: selectedTab == .profile ? MainTab.profile.selectedIconName : MainTab.profile.iconName)
                }
                .tag(MainTab.profile)
        }
        .tint(Color(red: 1.0, green: 0.31, blue: 0.48))
        .fullScreenCover(item: $activeVideoForPlayback) { video in
            NativePlayerView(video: video) {
                activeVideoForPlayback = nil
            }
        }
        .sheet(isPresented: $showSearchSheet) {
            SearchSheetView(
                onPlayVideo: { video in
                    activeVideoForPlayback = video
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
                        activeVideoForPlayback = realVideo
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
