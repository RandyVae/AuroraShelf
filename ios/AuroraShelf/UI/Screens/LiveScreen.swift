import SwiftUI

public struct LiveScreen: View {
    @ObservedObject public var repo = LiveRepository.shared
    @State private var activeStream: LiveStreamItem? = nil
    
    public init() {}
    
    public var body: some View {
        ZStack {
            AuroraTheme.background.ignoresSafeArea()
            
            VStack(spacing: 0) {
                // 1:1 对齐 Android 顶栏 NetflixTopBar
                NetflixTopBar(
                    title: "Netgrey",
                    searchPlaceholder: "搜索主播",
                    onSearch: {
                        // 搜索主播
                    },
                    onCast: {
                        // 投屏
                    }
                )
                
                // 分类条
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(repo.categories, id: \.self) { cat in
                            let isSelected = cat == repo.selectedGender
                            Button(action: {
                                withAnimation(AuroraTheme.appleSpring) {
                                    repo.selectedGender = cat
                                }
                            }) {
                                Text(cat)
                                    .font(.system(size: 12, weight: isSelected ? .bold : .medium))
                                    .foregroundColor(isSelected ? .white : AuroraTheme.textSecondary)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(
                                        Capsule()
                                            .fill(isSelected ? AuroraTheme.crimson : AuroraTheme.surfaceCard)
                                            .overlay(Capsule().stroke(isSelected ? AuroraTheme.crimson : AuroraTheme.glassBorder, lineWidth: 0.8))
                                    )
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 6)
                }
                
                // 直播主播双列网格
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)], spacing: 16) {
                        ForEach(repo.liveStreams) { item in
                            Button(action: {
                                let impact = UIImpactFeedbackGenerator(style: .heavy)
                                impact.impactOccurred()
                                activeStream = item
                            }) {
                                VStack(alignment: .leading, spacing: 6) {
                                    ZStack(alignment: .topTrailing) {
                                        AsyncImage(url: URL(string: item.snapshotUrl)) { phase in
                                            if let img = phase.image {
                                                img.resizable().aspectRatio(contentMode: .fill)
                                            } else {
                                                RoundedRectangle(cornerRadius: 12).fill(AuroraTheme.surfaceCard)
                                            }
                                        }
                                        .frame(height: 180)
                                        .cornerRadius(12)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12)
                                                .stroke(AuroraTheme.glassBorder, lineWidth: 0.8)
                                        )
                                        
                                        // LIVE 浮动指示标
                                        HStack(spacing: 4) {
                                            Circle().fill(Color.red).frame(width: 6, height: 6)
                                            Text("LIVE")
                                                .font(.system(size: 9, weight: .black))
                                                .foregroundColor(.white)
                                            Text("\(item.viewCount)")
                                                .font(.system(size: 9, weight: .bold))
                                                .foregroundColor(.white.opacity(0.8))
                                        }
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 3)
                                        .background(Capsule().fill(Color.black.opacity(0.7)))
                                        .padding(8)
                                    }
                                    
                                    HStack(spacing: 6) {
                                        AsyncImage(url: URL(string: item.avatarUrl)) { phase in
                                            if let img = phase.image {
                                                img.resizable().aspectRatio(contentMode: .fill)
                                            } else {
                                                Circle().fill(AuroraTheme.surfaceCard)
                                            }
                                        }
                                        .frame(width: 24, height: 24)
                                        .clipShape(Circle())
                                        
                                        Text(item.username)
                                            .font(.system(size: 13, weight: .bold))
                                            .foregroundColor(.white)
                                            .lineLimit(1)
                                    }
                                    
                                    Text(item.title)
                                        .font(.system(size: 11))
                                        .foregroundColor(AuroraTheme.textSecondary)
                                        .lineLimit(1)
                                }
                            }
                            .buttonStyle(PlainButtonStyle())
                            .applePressEffect()
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
                    .padding(.bottom, 24) // 底部原生呼吸间距（原生 TabBar 自动接管 SafeArea）
                }
            }
        }
        .fullScreenCover(item: $activeStream) { stream in
            LivePlayerView(stream: stream) {
                activeStream = nil
            }
        }
    }
}
