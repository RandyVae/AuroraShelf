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
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                    activeVideoForPlayback = videoRepo.billboardVideo ?? videoRepo.top10Videos.first ?? VideoItem(
                        id: "local-intro",
                        title: "极光视界 4K 演示样片",
                        subtitle: "原生硬件解码 · 0ms 秒播",
                        coverUrl: "",
                        playbackUrl: Bundle.main.url(forResource: "intro_film", withExtension: "mp4")?.absoluteString ?? "",
                        duration: "00:15",
                        rating: "9.9",
                        tags: ["本地原生", "4K HDR"],
                        category: "精选",
                        sourceName: "本地内置"
                    )
                }
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
