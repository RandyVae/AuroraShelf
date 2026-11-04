import SwiftUI

public struct ComicsScreen: View {
    @ObservedObject public var repo = ComicRepository.shared
    @State private var activeComicForReading: ComicItem? = nil
    
    public init() {}
    
    public var body: some View {
        ZStack {
            AuroraColor.canvas.ignoresSafeArea()
            
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: AuroraMetrics.spacingL) {
                    // 漫画源水平切换 Chip 条
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: AuroraMetrics.spacingS) {
                            ForEach(ComicSourceCatalog.allSources) { source in
                                let isSelected = source.id == repo.currentSource.id
                                if isSelected {
                                    Button {
                                        withAnimation(AuroraMotion.quick) {
                                            repo.switchSource(to: source)
                                        }
                                    } label: {
                                        Text(source.name)
                                            .font(.subheadline)
                                            .fontWeight(.semibold)
                                            .lineLimit(1)
                                    }
                                    .buttonStyle(.borderedProminent)
                                    .tint(AuroraColor.brand)
                                    .clipShape(Capsule())
                                    .frame(minHeight: AuroraMetrics.minTouchTarget)
                                } else {
                                    Button {
                                        withAnimation(AuroraMotion.quick) {
                                            repo.switchSource(to: source)
                                        }
                                    } label: {
                                        Text(source.name)
                                            .font(.subheadline)
                                            .fontWeight(.regular)
                                            .lineLimit(1)
                                    }
                                    .buttonStyle(.bordered)
                                    .tint(.secondary)
                                    .clipShape(Capsule())
                                    .frame(minHeight: AuroraMetrics.minTouchTarget)
                                }
                            }
                        }
                        .padding(.horizontal, AuroraMetrics.contentInset)
                        .padding(.vertical, AuroraMetrics.spacingXS)
                    }
                    
                    // 漫画双列推荐网格
                    LazyVGrid(
                        columns: [
                            GridItem(.flexible(), spacing: AuroraMetrics.spacingM),
                            GridItem(.flexible(), spacing: AuroraMetrics.spacingM)
                        ],
                        spacing: AuroraMetrics.spacingL
                    ) {
                        ForEach(repo.comics) { comic in
                            Button {
                                activeComicForReading = comic
                            } label: {
                                VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                                    AuroraCoverImage(
                                        url: comic.coverUrl,
                                        aspectRatio: AuroraMetrics.posterAspect,
                                        contentMode: .fill,
                                        cornerRadius: AuroraMetrics.cardRadius
                                    )
                                    .overlay(
                                        RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                                            .stroke(AuroraColor.hairline, lineWidth: 1)
                                    )
                                    
                                    Text(comic.title)
                                        .font(.subheadline)
                                        .fontWeight(.semibold)
                                        .foregroundStyle(AuroraColor.textPrimary)
                                        .lineLimit(1)
                                    
                                    HStack {
                                        Text(comic.author)
                                            .font(.caption)
                                            .foregroundStyle(AuroraColor.textSecondary)
                                            .lineLimit(1)
                                        Spacer()
                                        Text(comic.latestChapterTitle)
                                            .font(.caption2)
                                            .fontWeight(.medium)
                                            .foregroundStyle(AuroraColor.brand)
                                            .lineLimit(1)
                                    }
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, AuroraMetrics.contentInset)

                    if let errorMessage = repo.errorMessage {
                        ContentUnavailableView {
                            Label("漫画暂不可用", systemImage: "exclamationmark.triangle")
                        } description: {
                            Text(errorMessage)
                        } actions: {
                            Button("重试") { repo.loadComics(for: repo.currentSource) }
                                .buttonStyle(.borderedProminent)
                        }
                        .padding(.horizontal, AuroraMetrics.contentInset)
                    }
                    
                    Spacer().frame(height: AuroraMetrics.spacingXXL)
                }
                .padding(.top, AuroraMetrics.spacingS)
            }
        }
        .navigationTitle("漫画")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                AirPlayRoutePicker()
                    .frame(width: 32, height: 32)
            }
        }
        .fullScreenCover(item: $activeComicForReading) { comic in
            ComicReaderView(comic: comic) {
                activeComicForReading = nil
            }
        }
    }
}
