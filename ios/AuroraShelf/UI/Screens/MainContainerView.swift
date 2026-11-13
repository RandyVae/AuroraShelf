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
    }
}
