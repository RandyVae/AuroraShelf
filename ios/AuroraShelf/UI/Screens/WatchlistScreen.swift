import SwiftUI

public struct WatchlistScreen: View {
    @ObservedObject private var store = WatchlistStore.shared
    @ObservedObject private var locManager = LocalizationManager.shared
    @State private var storeError: String?
    @Environment(\.auroraContainerSize) private var containerSize
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    public let onPlayVideo: (VideoItem) -> Void

    public init(onPlayVideo: @escaping (VideoItem) -> Void) {
        self.onPlayVideo = onPlayVideo
    }

    private var gridColumns: [GridItem] {
        let form = DeviceLayout.resolveCurrent(containerSize: containerSize, horizontalSizeClass: horizontalSizeClass)
        let baseMin = DeviceLayout.gridMinimumWidth(for: form)
        let minimumWidth: CGFloat = dynamicTypeSize.isAccessibilitySize ? max(baseMin, 260) : baseMin
        return [GridItem(.adaptive(minimum: minimumWidth, maximum: 340), spacing: AuroraMetrics.spacingM)]
    }

    public var body: some View {
        Group {
            if store.items.isEmpty {
                ContentUnavailableView(
                    "watchlist.empty".localized,
                    systemImage: "bookmark",
                    description: Text(store.loadError ?? "watchlist.empty_desc".localized)
                )
            } else {
                ScrollView {
                    LazyVGrid(
                        columns: gridColumns,
                        spacing: AuroraMetrics.spacingL
                    ) {
                        ForEach(store.items) { video in
                            PosterCard(video: video) {
                                onPlayVideo(video)
                            }
                            .contextMenu {
                                Button(role: .destructive) {
                                    do {
                                        try store.setBookmarked(false, for: video)
                                    } catch {
                                        storeError = error.localizedDescription
                                    }
                                } label: {
                                    Label("watchlist.remove".localized, systemImage: "bookmark.slash")
                                }
                            }
                        }
                    }
                    .padding(AuroraMetrics.contentInset)
                    .safeAreaPadding(.bottom, AuroraMetrics.spacingXXL)
                }
            }
        }
        .background(AuroraColor.canvas.ignoresSafeArea())
        .navigationTitle("watchlist.title".localized)
        .navigationBarTitleDisplayMode(.inline)
        .alert("detail.watchlist_update_failed".localized, isPresented: Binding(
            get: { storeError != nil },
            set: { if !$0 { storeError = nil } }
        )) {
            Button("common.ok".localized, role: .cancel) { storeError = nil }
        } message: {
            Text(storeError ?? "hero.try_again_later".localized)
        }
    }
}
