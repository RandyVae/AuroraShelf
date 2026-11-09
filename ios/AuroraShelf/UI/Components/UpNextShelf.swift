import SwiftUI

/// Apple TV 风格的「接着播放（Up Next）」横向货架
public struct UpNextShelf: View {
    @ObservedObject private var store = PlaybackProgressStore.shared
    public let onPlayVideo: (VideoItem) -> Void
    public var onDetail: ((VideoItem) -> Void)? = nil

    public init(
        onPlayVideo: @escaping (VideoItem) -> Void,
        onDetail: ((VideoItem) -> Void)? = nil
    ) {
        self.onPlayVideo = onPlayVideo
        self.onDetail = onDetail
    }

    public var body: some View {
        let items = store.upNextRecords
        if !items.isEmpty {
            VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                // 货架标题（Apple TV 原生纯粹排版，无多余副标胶囊）
                HStack(spacing: AuroraMetrics.spacingS) {
                    Text("home.up_next".localized)
                        .font(.title3)
                        .fontWeight(.bold)
                        .foregroundStyle(AuroraColor.textPrimary)

                    Spacer()
                }
                .padding(.horizontal, AuroraMetrics.contentInset)

                // 16:9 横向待播流
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(spacing: AuroraMetrics.spacingL) {
                        ForEach(items) { record in
                            UpNextCard(
                                record: record,
                                onPlay: {
                                    onPlayVideo(record.video)
                                },
                                onMarkCompleted: {
                                    store.markCompleted(for: record.video)
                                },
                                onRemove: {
                                    store.removeFromUpNext(for: record.video)
                                },
                                onDetail: onDetail == nil ? nil : {
                                    onDetail?(record.video)
                                }
                            )
                        }
                    }
                    .padding(.horizontal, AuroraMetrics.contentInset)
                }
            }
        }
    }
}
