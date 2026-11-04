import SwiftUI

public struct LiveScreen: View {
    @ObservedObject public var repo = LiveRepository.shared
    @State private var activeStream: LiveStreamItem? = nil
    
    public init() {}
    
    public var body: some View {
        ZStack {
            AuroraColor.canvas.ignoresSafeArea()
            
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: AuroraMetrics.spacingL) {
                    // 分类 Chip 条
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: AuroraMetrics.spacingS) {
                            ForEach(repo.categories, id: \.self) { cat in
                                let isSelected = cat == repo.selectedGender
                                if isSelected {
                                    Button {
                                        withAnimation(AuroraMotion.quick) {
                                            repo.selectedGender = cat
                                        }
                                    } label: {
                                        Text(cat)
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
                                            repo.selectedGender = cat
                                        }
                                    } label: {
                                        Text(cat)
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
                    
                    // 主播双列网格（严格防重叠与边界裁切）
                    LazyVGrid(
                        columns: [
                            GridItem(.flexible(), spacing: AuroraMetrics.spacingM),
                            GridItem(.flexible(), spacing: AuroraMetrics.spacingM)
                        ],
                        spacing: AuroraMetrics.spacingL
                    ) {
                        ForEach(repo.liveStreams) { item in
                            Button {
                                activeStream = item
                            } label: {
                                VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
                                    ZStack(alignment: .topTrailing) {
                                        AuroraCoverImage(
                                            url: item.snapshotUrl,
                                            aspectRatio: 4.0 / 3.0,
                                            contentMode: .fill,
                                            cornerRadius: AuroraMetrics.cardRadius
                                        )
                                        .frame(minWidth: 0, maxWidth: .infinity)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                                                .stroke(AuroraColor.hairline, lineWidth: 1)
                                        )
                                        
                                        // LIVE 浮动指示标（系统 Material 材质胶囊）
                                        HStack(spacing: 4) {
                                            Circle()
                                                .fill(Color.red)
                                                .frame(width: 6, height: 6)
                                            Text("LIVE")
                                                .font(.caption2)
                                                .fontWeight(.black)
                                                .foregroundStyle(.primary)
                                            Text("\(item.viewCount)")
                                                .font(.caption2)
                                                .fontWeight(.bold)
                                                .foregroundStyle(.secondary)
                                        }
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(.regularMaterial, in: Capsule())
                                        .padding(AuroraMetrics.spacingS)
                                    }
                                    .frame(minWidth: 0, maxWidth: .infinity)
                                    .clipped()
                                    
                                    HStack(spacing: AuroraMetrics.spacingS) {
                                        AsyncImage(url: URL(string: item.avatarUrl)) { phase in
                                            if let img = phase.image {
                                                img.resizable().aspectRatio(contentMode: .fill)
                                            } else {
                                                Circle().fill(AuroraColor.surfaceCard)
                                            }
                                        }
                                        .frame(width: 24, height: 24)
                                        .clipShape(Circle())
                                        .clipped()
                                        
                                        Text(item.username)
                                            .font(.subheadline)
                                            .fontWeight(.semibold)
                                            .foregroundStyle(AuroraColor.textPrimary)
                                            .lineLimit(1)
                                    }
                                    
                                    Text(item.title)
                                        .font(.caption)
                                        .foregroundStyle(AuroraColor.textSecondary)
                                        .lineLimit(1)
                                }
                                .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, AuroraMetrics.contentInset)
                    
                    Spacer().frame(height: AuroraMetrics.spacingXXL)
                }
                .padding(.top, AuroraMetrics.spacingS)
            }
        }
        .navigationTitle("直播")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                AirPlayRoutePicker()
                    .frame(width: 32, height: 32)
            }
        }
        .fullScreenCover(item: $activeStream) { stream in
            LivePlayerView(stream: stream) {
                activeStream = nil
            }
        }
        .onReceive(repo.$liveStreams) { streams in
            if CommandLine.arguments.contains("--auto-live") && activeStream == nil, let first = streams.first {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                    activeStream = first
                }
            }
        }
    }
}
