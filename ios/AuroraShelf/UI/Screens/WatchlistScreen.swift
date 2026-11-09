import SwiftUI

public struct WatchlistScreen: View {
    @ObservedObject private var store = WatchlistStore.shared
    @State private var storeError: String?
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    public let onPlayVideo: (VideoItem) -> Void

    public init(onPlayVideo: @escaping (VideoItem) -> Void) {
        self.onPlayVideo = onPlayVideo
    }

    private var gridColumns: [GridItem] {
        let form = DeviceLayout.resolveCurrent(horizontalSizeClass: horizontalSizeClass)
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
                    description: Text(store.loadError ?? "在视频详情中添加内容后，会保存在这里。")
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
                }
            }
        }
        .background(AuroraColor.canvas.ignoresSafeArea())
        .navigationTitle("watchlist.title".localized)
        .navigationBarTitleDisplayMode(.inline)
        .alert("未能更新片单", isPresented: Binding(
            get: { storeError != nil },
            set: { if !$0 { storeError = nil } }
        )) {
            Button("好", role: .cancel) { storeError = nil }
        } message: {
            Text(storeError ?? "请稍后重试。")
        }
    }
}
