import SwiftUI

public struct SearchSheetView: View {
    public let onPlayVideo: (VideoItem) -> Void
    public let onDismiss: () -> Void
    
    @StateObject private var repo = VideoRepository.shared
    @State private var query: String = ""
    @FocusState private var isSearchFocused: Bool
    
    private var totalSourcesCount: Int {
        VideoSourceCatalog.allSources.count
    }
    
    public init(onPlayVideo: @escaping (VideoItem) -> Void, onDismiss: @escaping () -> Void) {
        self.onPlayVideo = onPlayVideo
        self.onDismiss = onDismiss
    }
    
    public var body: some View {
        NavigationStack {
            Group {
                if repo.isSearching {
                    VStack(spacing: AuroraMetrics.spacingL) {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: AuroraColor.brand))
                            .scaleEffect(1.3)
                        
                        Text("正在向全网 \(totalSourcesCount) 个视频源并发检索...")
                            .font(.subheadline)
                            .foregroundStyle(Color.secondary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if !repo.searchResults.isEmpty {
                    List {
                        ForEach(repo.searchResults) { item in
                            Button(action: {
                                onPlayVideo(item)
                                onDismiss()
                            }) {
                                HStack(spacing: AuroraMetrics.spacingM) {
                                    AuroraCoverImage(
                                        url: item.coverUrl,
                                        aspectRatio: 16.0 / 10.0,
                                        contentMode: .fill,
                                        cornerRadius: AuroraMetrics.controlRadius
                                    )
                                    .frame(width: 120, height: 75)
                                    
                                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingXS) {
                                        Text(item.title)
                                            .font(.headline)
                                            .foregroundStyle(.white)
                                            .lineLimit(1)
                                        
                                        Text(item.subtitle)
                                            .font(.caption)
                                            .foregroundStyle(Color.secondary)
                                            .lineLimit(1)
                                        
                                        HStack(spacing: AuroraMetrics.spacingS) {
                                            Text(item.sourceName)
                                                .font(.caption.bold())
                                                .foregroundStyle(AuroraColor.brand)
                                                .padding(.horizontal, AuroraMetrics.spacingXS)
                                                .padding(.vertical, 2)
                                                .background(AuroraColor.brand.opacity(0.18), in: .capsule)
                                            
                                            Text(item.duration)
                                                .font(.caption2)
                                                .foregroundStyle(AuroraColor.textTertiary)
                                        }
                                    }
                                    
                                    Spacer()
                                }
                                .padding(.vertical, AuroraMetrics.spacingXS)
                            }
                            .listRowBackground(AuroraColor.surfaceCard)
                            .listRowSeparatorTint(AuroraColor.hairline)
                        }
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                } else if !query.isEmpty {
                    ContentUnavailableView.search(text: query)
                } else {
                    ContentUnavailableView(
                        "全网视频检索",
                        systemImage: "sparkle.magnifyingglass",
                        description: Text("支持跨全网 \(totalSourcesCount) 个高可用直连源并发毫秒级检索")
                    )
                }
            }
            .background(AuroraColor.canvas.ignoresSafeArea())
            .navigationTitle("全网搜索")
            .navigationBarTitleDisplayMode(.inline)
            .searchable(
                text: $query,
                placement: .navigationBarDrawer(displayMode: .always),
                prompt: "搜索全网 \(totalSourcesCount) 个视频源..."
            )
            .onSubmit(of: .search) {
                Task {
                    await repo.searchAllSources(query: query)
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("完成") {
                        onDismiss()
                    }
                    .foregroundStyle(AuroraColor.brand)
                }
            }
        }
    }
}
